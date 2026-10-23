/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Member reservation operations. Call on a worker thread. Every operation
 * checks the account in the database; no mutable global session is consulted. */
public class ReservationService {
    private static final int QUERY_TIMEOUT_SECONDS = 12;
    private static final String MY_RESERVATIONS_SQL = """
            SELECT r.reservation_id,r.book_id,b.title,r.reserved_at,r.status,
              CASE WHEN r.status IN ('PENDING','READY') THEN 1 + (
                SELECT COUNT(*) FROM reservation ahead
                WHERE ahead.book_id=r.book_id AND ahead.status IN ('PENDING','READY')
                  AND (ahead.reserved_at<r.reserved_at OR
                    (ahead.reserved_at=r.reserved_at AND ahead.reservation_id<r.reservation_id))
              ) ELSE NULL END AS queue_position
            FROM reservation r JOIN book b ON b.book_id=r.book_id
            WHERE r.user_id=?
            ORDER BY r.reserved_at DESC,r.reservation_id DESC
            """;
    private final LibraryRepository.ConnectionProvider connections;

    public ReservationService() { this(DatabaseConnection::getConnection); }

    public ReservationService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public List<LibraryReservation> findMyReservations(AuthenticatedUser actor) throws SQLException {
        long user = requireActor(actor);
        try (Connection c = open()) {
            authorize(c, user, false);
            List<LibraryReservation> rows = new ArrayList<>();
            try (PreparedStatement s = prepare(c, MY_RESERVATIONS_SQL, user);
                    ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    long position = r.getLong(6);
                    Long queue = r.wasNull() ? null : position;
                    rows.add(new LibraryReservation(r.getString(1), r.getString(2),
                            r.getString(3), r.getObject(4, LocalDateTime.class),
                            r.getString(5), queue));
                }
            }
            return List.copyOf(rows);
        }
    }

    public String reserve(AuthenticatedUser actor, String bookId) throws SQLException {
        long user = requireActor(actor);
        long book = id(bookId, "Book ID");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                // This preflight is not a lock. The authoritative check follows
                // the book lock, matching AdminService.issue's book/member order.
                authorize(c, user, false);
                lockBook(c, book);
                authorize(c, user, true);
                // AVAILABLE is the copy's physical circulation status. An open
                // loan does not make a title ineligible for the waiting queue.
                if (!exists(c, """
                        SELECT copy_id FROM book_copy WHERE book_id=? AND status='AVAILABLE'
                        ORDER BY copy_id LIMIT 1 FOR UPDATE
                        """, book)) {
                    throw new IllegalArgumentException("This title has no circulatable copies to reserve.");
                }
                // Locking reads see current committed data even if a preflight
                // read created an older REPEATABLE READ snapshot.
                if (exists(c, """
                        SELECT reservation_id FROM reservation
                        WHERE user_id=? AND book_id=? AND status IN ('PENDING','READY')
                        LIMIT 1 FOR UPDATE
                        """, user, book)) {
                    throw duplicate();
                }
                if (exists(c, """
                        SELECT l.loan_id FROM loan l JOIN book_copy bc ON bc.copy_id=l.copy_id
                        WHERE l.user_id=? AND bc.book_id=? AND l.returned_at IS NULL
                        LIMIT 1 FOR UPDATE
                        """, user, book)) {
                    throw new IllegalArgumentException("You already have an open loan for this title.");
                }
                String reservation;
                try {
                    reservation = insert(c, """
                            INSERT INTO reservation(book_id,user_id,reserved_at,status)
                            VALUES(?,?,UTC_TIMESTAMP(),'PENDING')
                            """, book, user);
                } catch (SQLException failure) {
                    // This insert has one unique constraint: the active
                    // member/title guard. Do not retry a write automatically.
                    if (failure.getErrorCode() == 1062) {
                        throw new IllegalArgumentException(duplicate().getMessage(), failure);
                    }
                    throw failure;
                }
                c.commit();
                return reservation;
            } catch (SQLException | RuntimeException failure) {
                rollback(c, failure);
                throw failure;
            }
        }
    }

    public void cancel(AuthenticatedUser actor, String reservationId) throws SQLException {
        long user = requireActor(actor);
        long reservation = id(reservationId, "Reservation ID");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                authorize(c, user, false);
                Long book = null;
                // Resolve only the caller's record. This read takes no row lock;
                // all writes use the same book -> member -> reservation order.
                try (PreparedStatement s = prepare(c,
                        "SELECT book_id FROM reservation WHERE reservation_id=? AND user_id=?",
                        reservation, user); ResultSet r = s.executeQuery()) {
                    if (r.next()) book = r.getLong(1);
                }
                if (book == null) throw missingReservation();
                lockBook(c, book);
                authorize(c, user, true);
                try (PreparedStatement s = prepare(c, """
                        SELECT status FROM reservation
                        WHERE reservation_id=? AND user_id=? AND book_id=? FOR UPDATE
                        """, reservation, user, book); ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw missingReservation();
                    String status = r.getString(1);
                    if (!"PENDING".equals(status) && !"READY".equals(status)) {
                        throw new IllegalArgumentException("Only an active reservation can be cancelled. Refresh your reservations.");
                    }
                }
                try (PreparedStatement s = prepare(c, """
                        UPDATE reservation SET status='CANCELLED'
                        WHERE reservation_id=? AND user_id=? AND book_id=? AND status IN ('PENDING','READY')
                        """, reservation, user, book)) {
                    if (s.executeUpdate() != 1) {
                        throw new SQLException("The reservation changed before it could be cancelled. Refresh your reservations.");
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(c, failure);
                throw failure;
            }
        }
    }

    private Connection open() throws SQLException {
        Connection c = connections.open();
        if (c == null) throw new SQLException("No database connection was returned.");
        return c;
    }

    private static long requireActor(AuthenticatedUser actor) {
        if (actor == null || !"USER".equals(actor.role())) {
            throw new IllegalArgumentException("An authenticated regular member is required.");
        }
        return id(actor.userId(), "Member ID");
    }

    private static long id(String value, String label) {
        if (value == null || !value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException(label + " must be a positive numeric database ID.");
        }
        long number = Long.parseLong(value);
        if (number < 1 || number > 4294967295L) {
            throw new IllegalArgumentException(label + " is outside the supported range.");
        }
        return number;
    }

    private static void authorize(Connection c, long user, boolean lock) throws SQLException {
        try (PreparedStatement s = prepare(c,
                "SELECT role,status FROM app_user WHERE user_id=?" + (lock ? " FOR UPDATE" : ""), user);
                ResultSet r = s.executeQuery()) {
            if (!r.next() || !"USER".equals(r.getString(1)) || !"ACTIVE".equals(r.getString(2))) {
                throw new IllegalArgumentException("Your member account is no longer active. Sign out and contact an administrator.");
            }
        }
    }

    private static void lockBook(Connection c, long book) throws SQLException {
        if (!exists(c, "SELECT book_id FROM book WHERE book_id=? FOR UPDATE", book)) {
            throw new IllegalArgumentException("Book not found. Refresh the catalog.");
        }
    }

    private static boolean exists(Connection c, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement s = prepare(c, sql, parameters); ResultSet r = s.executeQuery()) {
            return r.next();
        }
    }

    private static String insert(Connection c, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(s, parameters);
            if (s.executeUpdate() != 1) throw new SQLException("The reservation was not created.");
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("The database did not return the reservation ID.");
                long key = keys.getLong(1);
                if (keys.wasNull() || key < 1) throw new SQLException("The database returned an invalid reservation ID.");
                return Long.toString(key);
            }
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... parameters) throws SQLException {
        PreparedStatement s = c.prepareStatement(sql);
        try {
            bind(s, parameters);
            return s;
        } catch (SQLException | RuntimeException failure) {
            try { s.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private static void bind(PreparedStatement s, Object... parameters) throws SQLException {
        s.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        for (int i = 0; i < parameters.length; i++) s.setObject(i + 1, parameters[i]);
    }

    private static void rollback(Connection c, Throwable failure) {
        try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
    }

    private static IllegalArgumentException duplicate() {
        return new IllegalArgumentException("You already have an active reservation for this title.");
    }

    private static IllegalArgumentException missingReservation() {
        return new IllegalArgumentException("Reservation not found in your account. Refresh your reservations.");
    }
}

