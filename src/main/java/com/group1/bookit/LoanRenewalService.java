/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Own-loan renewals. Call on a worker thread. No credentials/session are cached.
 * Policy: once, seven days from the current due date, before the deadline,
 * no positive unpaid fine for this member, and no other member waiting.
 * Failed/uncertain writes are never retried automatically; reconcile history.
 */
public class LoanRenewalService {
    private static final int TIMEOUT = 12;
    private static final String HISTORY = """
            SELECT u.user_id,u.username,u.role,u.status,r.loan_id,r.request_id,
                   b.title,bc.barcode,r.old_due_at,r.new_due_at,r.renewed_at
            FROM app_user u
            LEFT JOIN loan_renewal r ON r.user_id=u.user_id
            LEFT JOIN loan l ON l.loan_id=r.loan_id AND l.user_id=u.user_id
            LEFT JOIN book_copy bc ON bc.copy_id=l.copy_id
            LEFT JOIN book b ON b.book_id=bc.book_id
            WHERE u.user_id=? ORDER BY r.renewed_at DESC,r.loan_id DESC
            """;
    private final LibraryRepository.ConnectionProvider connections;

    public enum Reason {
        ACCOUNT_UNAVAILABLE, LOAN_UNAVAILABLE, STALE_LOAN, ALREADY_RENEWED,
        OVERDUE, UNPAID_FINE, WAITING_RESERVATION
    }

    /** Fixed safe messages, never SQL or values supplied by another member. */
    public static final class RejectedException extends IllegalArgumentException {
        private final Reason reason;
        public RejectedException(Reason reason) {
            super(switch (reason) {
                case ACCOUNT_UNAVAILABLE -> "Your member account is unavailable. Sign out and contact an administrator.";
                case LOAN_UNAVAILABLE -> "This open loan is unavailable in your account. Refresh your loans.";
                case STALE_LOAN -> "This loan changed after it was loaded. Refresh your loans before trying again.";
                case ALREADY_RENEWED -> "This loan has already been renewed, or this renewal request was already recorded. Refresh your renewal history.";
                case OVERDUE -> "This loan has reached its due date and cannot be renewed.";
                case UNPAID_FINE -> "Settle your outstanding fines with the library before renewing.";
                case WAITING_RESERVATION -> "Another member is waiting for this title, so it cannot be renewed.";
            });
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }

    public LoanRenewalService() { this(DatabaseConnection::getConnection); }
    public LoanRenewalService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public List<LoanRenewal> findMyRenewals(AuthenticatedUser actor) throws SQLException {
        long user = actorId(actor);
        try (Connection c = open(); PreparedStatement s = prepare(c, HISTORY, user);
                ResultSet r = s.executeQuery()) {
            // An anchored LEFT JOIN distinguishes an authorized empty history
            // from a missing/inactive/reassigned account in one read snapshot.
            if (!r.next()) throw reject(Reason.ACCOUNT_UNAVAILABLE);
            List<LoanRenewal> results = new ArrayList<>();
            do {
                authorize(actor, user, r.getString(1), r.getString(2),
                        r.getString(3), r.getString(4));
                String loan = r.getString(5);
                if (loan == null) {
                    if (!results.isEmpty()) throw invalidData();
                    continue;
                }
                String request = r.getString(6), title = r.getString(7), barcode = r.getString(8);
                LocalDateTime oldDue = r.getObject(9, LocalDateTime.class);
                LocalDateTime newDue = r.getObject(10, LocalDateTime.class);
                LocalDateTime renewed = r.getObject(11, LocalDateTime.class);
                if (!validId(loan) || !canonicalUuid(request) || title == null || barcode == null
                        || !validDate(oldDue, false) || !validDate(newDue, false)
                        || !validDate(renewed, true) || !newDue.equals(addWeek(oldDue))) throw invalidData();
                results.add(new LoanRenewal(loan, request, title, barcode, oldDue, newDue, renewed));
            } while (r.next());
            return List.copyOf(results);
        }
    }

    public LoanRenewal renew(AuthenticatedUser actor, String loanId,
            LocalDateTime expectedDueAt, String requestId) throws SQLException {
        long user = actorId(actor), loan = numericId(loanId);
        if (!validDate(expectedDueAt, false)) {
            throw new IllegalArgumentException("Refresh your loans to obtain a valid due date.");
        }
        if (!canonicalUuid(requestId)) {
            throw new IllegalArgumentException("A canonical renewal request ID is required.");
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                long book, copy;
                // Non-locking routing only. Every value is rechecked below
                // after book -> member -> copy -> loan locks, matching issue.
                try (PreparedStatement s = prepare(c, """
                        SELECT bc.book_id,l.copy_id FROM loan l
                        JOIN book_copy bc ON bc.copy_id=l.copy_id
                        WHERE l.loan_id=? AND l.user_id=?
                        """, loan, user); ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw reject(Reason.LOAN_UNAVAILABLE);
                    book = databaseId(r, 1); copy = databaseId(r, 2);
                    if (r.next()) throw invalidData();
                }
                String title;
                try (PreparedStatement s = prepare(c,
                        "SELECT book_id,title FROM book WHERE book_id=? FOR UPDATE", book);
                        ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw reject(Reason.LOAN_UNAVAILABLE);
                    if (databaseId(r, 1) != book) throw invalidData();
                    title = r.getString(2);
                    if (title == null || r.next()) throw invalidData();
                }
                try (PreparedStatement s = prepare(c,
                        "SELECT user_id,username,role,status FROM app_user WHERE user_id=? FOR UPDATE", user);
                        ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw reject(Reason.ACCOUNT_UNAVAILABLE);
                    authorize(actor, user, r.getString(1), r.getString(2), r.getString(3), r.getString(4));
                    if (r.next()) throw invalidData();
                }
                String barcode;
                try (PreparedStatement s = prepare(c,
                        "SELECT book_id,barcode FROM book_copy WHERE copy_id=? FOR UPDATE", copy);
                        ResultSet r = s.executeQuery()) {
                    if (!r.next() || databaseId(r, 1) != book) throw reject(Reason.STALE_LOAN);
                    barcode = r.getString(2);
                    if (barcode == null || r.next()) throw invalidData();
                }
                LocalDateTime oldDue;
                try (PreparedStatement s = prepare(c, """
                        SELECT user_id,copy_id,due_at,returned_at FROM loan WHERE loan_id=? FOR UPDATE
                        """, loan); ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw reject(Reason.LOAN_UNAVAILABLE);
                    if (databaseId(r, 1) != user) throw reject(Reason.LOAN_UNAVAILABLE);
                    if (databaseId(r, 2) != copy) throw reject(Reason.STALE_LOAN);
                    oldDue = r.getObject(3, LocalDateTime.class);
                    if (r.getObject(4, LocalDateTime.class) != null) throw reject(Reason.LOAN_UNAVAILABLE);
                    if (!validDate(oldDue, false) || r.next()) throw invalidData();
                }
                if (exists(c, "SELECT loan_id FROM loan_renewal WHERE loan_id=? FOR UPDATE", loan)) {
                    throw reject(Reason.ALREADY_RENEWED);
                }
                if (!oldDue.equals(expectedDueAt)) throw reject(Reason.STALE_LOAN);
                // Admin fine creation locks its loan before inserting a fine.
                // Lock every own loan (including returned loans) before reading
                // fine rows, so a simultaneous charge cannot slip past the check.
                boolean found = false;
                try (PreparedStatement s = prepare(c,
                        "SELECT loan_id FROM loan WHERE user_id=? ORDER BY loan_id FOR UPDATE", user);
                        ResultSet r = s.executeQuery()) {
                    while (r.next()) if (databaseId(r, 1) == loan) found = true;
                }
                if (!found) throw invalidData();
                try (PreparedStatement s = prepare(c, """
                        SELECT f.amount,f.payment_status FROM fine f
                        JOIN loan l ON l.loan_id=f.loan_id
                        WHERE l.user_id=? ORDER BY f.fine_id FOR UPDATE
                        """, user); ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        BigDecimal amount = r.getBigDecimal(1);
                        String status = r.getString(2);
                        if (amount == null || amount.signum() < 0 ||
                                !("UNPAID".equals(status) || "PAID".equals(status) || "WAIVED".equals(status))) throw invalidData();
                        if ("UNPAID".equals(status) && amount.signum() > 0) throw reject(Reason.UNPAID_FINE);
                    }
                }
                if (exists(c, """
                        SELECT reservation_id FROM reservation
                        WHERE book_id=? AND user_id<>? AND status IN ('PENDING','READY')
                        ORDER BY reserved_at,reservation_id FOR UPDATE
                        """, book, user)) throw reject(Reason.WAITING_RESERVATION);
                LocalDateTime now;
                // Read the server clock AFTER all policy reads/lock waits.
                try (PreparedStatement s = prepare(c, "SELECT UTC_TIMESTAMP(6)");
                        ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw invalidData();
                    now = r.getObject(1, LocalDateTime.class);
                    if (!validDate(now, true) || r.next()) throw invalidData();
                }
                if (!oldDue.isAfter(now)) throw reject(Reason.OVERDUE);
                LocalDateTime newDue = addWeek(oldDue);
                if (newDue == null) {
                    throw new IllegalArgumentException("This due date cannot be extended within the supported date range.");
                }
                try (PreparedStatement s = prepare(c, """
                        INSERT INTO loan_renewal(loan_id,user_id,request_id,old_due_at,new_due_at,renewed_at)
                        VALUES(?,?,?,?,?,?)
                        """, loan, user, requestId, oldDue, newDue, now)) {
                    try {
                        if (s.executeUpdate() != 1) throw new SQLException("The renewal receipt was not recorded.", "40001");
                    } catch (SQLException failure) {
                        if (failure.getErrorCode() == 1062) {
                            RejectedException duplicate = reject(Reason.ALREADY_RENEWED);
                            duplicate.initCause(failure);
                            throw duplicate;
                        }
                        throw failure;
                    }
                }
                try (PreparedStatement s = prepare(c, """
                        UPDATE loan SET due_at=?
                        WHERE loan_id=? AND user_id=? AND copy_id=? AND returned_at IS NULL AND due_at=?
                          AND due_at>UTC_TIMESTAMP(6)
                        """, newDue, loan, user, copy, oldDue)) {
                    // Recheck immediately at the write too: receipt insertion
                    // could have waited for a unique-key lock after the clock
                    // read. Any zero-row result rolls back the receipt as well.
                    if (s.executeUpdate() != 1) throw new SQLException("The loan changed or reached its deadline before its renewal could be saved.", "40001");
                }
                LoanRenewal receipt = new LoanRenewal(Long.toString(loan), requestId, title, barcode, oldDue, newDue, now);
                c.commit();
                return receipt;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    private Connection open() throws SQLException {
        Connection c = connections.open();
        if (c == null) throw new SQLException("No database connection was returned.", "08001");
        return c;
    }

    private static long actorId(AuthenticatedUser actor) {
        if (actor == null || !"USER".equals(actor.role()) || actor.username() == null
                || actor.username().isBlank() || !validId(actor.userId())) throw reject(Reason.ACCOUNT_UNAVAILABLE);
        return Long.parseLong(actor.userId());
    }

    private static void authorize(AuthenticatedUser actor, long user, String actualId,
            String username, String role, String status) {
        if (!validId(actualId) || Long.parseLong(actualId) != user
                || !Objects.equals(actor.username(), username)
                || !"USER".equals(role) || !"ACTIVE".equals(status)) throw reject(Reason.ACCOUNT_UNAVAILABLE);
    }

    private static long numericId(String value) {
        if (!validId(value)) throw new IllegalArgumentException("A positive numeric loan ID is required.");
        return Long.parseLong(value);
    }

    private static boolean validId(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) return false;
        long number = Long.parseLong(value);
        return number >= 1 && number <= 4294967295L;
    }

    private static long databaseId(ResultSet r, int column) throws SQLException {
        String value = r.getString(column);
        if (!validId(value)) throw invalidData();
        return Long.parseLong(value);
    }

    private static boolean canonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean validDate(LocalDateTime value, boolean microseconds) {
        return value != null && value.getYear() >= 1000 && value.getYear() <= 9999
                && (microseconds ? value.getNano() % 1000 == 0 : value.getNano() == 0);
    }

    private static LocalDateTime addWeek(LocalDateTime oldDue) {
        try {
            LocalDateTime result = oldDue.plusDays(7);
            return validDate(result, false) ? result : null;
        } catch (DateTimeException invalid) { return null; }
    }

    private static boolean exists(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = prepare(c, sql, values); ResultSet r = s.executeQuery()) {
            return r.next();
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... values) throws SQLException {
        PreparedStatement s = c.prepareStatement(sql);
        try {
            s.setQueryTimeout(TIMEOUT);
            for (int index = 0; index < values.length; index++) s.setObject(index + 1, values[index]);
            return s;
        } catch (SQLException | RuntimeException failure) {
            try { s.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private static SQLException invalidData() {
        return new SQLException("The renewal data is incomplete or inconsistent. Contact an administrator.", "22000");
    }

    private static RejectedException reject(Reason reason) { return new RejectedException(reason); }
}

