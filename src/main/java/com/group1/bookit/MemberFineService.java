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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MemberFineService {
    private static final int QUERY_TIMEOUT_SECONDS = 12;
    private static final String MY_FINES_SQL = """
            SELECT u.user_id AS authorized_user_id,
                   own.fine_id,own.loan_id,own.book_title,own.barcode,
                   own.amount,own.payment_status,own.borrowed_at,
                   own.due_at,own.returned_at,own.paid_at
            FROM app_user u
            LEFT JOIN (
                SELECT l.user_id,f.fine_id,f.loan_id,b.title AS book_title,
                       bc.barcode,f.amount,f.payment_status,
                       l.loaned_at AS borrowed_at,l.due_at,l.returned_at,f.paid_at
                FROM loan l
                JOIN fine f ON f.loan_id=l.loan_id
                LEFT JOIN book_copy bc ON bc.copy_id=l.copy_id
                LEFT JOIN book b ON b.book_id=bc.book_id
                WHERE l.user_id=?
            ) own ON own.user_id=u.user_id
            WHERE u.user_id=? AND u.role='USER' AND u.status='ACTIVE'
            ORDER BY CASE own.payment_status
                       WHEN 'UNPAID' THEN 0 WHEN 'PAID' THEN 1 ELSE 2 END,
                     own.fine_id DESC
            """;
    private final LibraryRepository.ConnectionProvider connections;

    public MemberFineService() { this(DatabaseConnection::getConnection); }

    public MemberFineService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public List<LibraryFine> findMyFines(AuthenticatedUser actor) throws SQLException {
        long member = requireActor(actor);
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(MY_FINES_SQL)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setLong(1, member);
            statement.setLong(2, member);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "Your member account is no longer active. Sign out and contact an administrator.");
                }
                List<LibraryFine> fines = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                boolean emptySentinel = false;
                do {
                    long authorized = result.getLong("authorized_user_id");
                    if (result.wasNull() || authorized != member) throw invalidData();
                    String fineId = result.getString("fine_id");
                    if (fineId == null) {
                        if (emptySentinel || !fines.isEmpty()) throw invalidData();

                        for (String column : List.of("loan_id", "book_title", "barcode", "amount",
                                "payment_status", "borrowed_at", "due_at", "returned_at", "paid_at")) {
                            if (result.getObject(column) != null) throw invalidData();
                        }
                        emptySentinel = true;
                    } else {
                        if (emptySentinel || !seen.add(fineId)) throw invalidData();
                        fines.add(readFine(result, fineId));
                    }
                } while (result.next());
                return List.copyOf(fines);
            }
        }
    }

    private Connection open() throws SQLException {
        Connection connection = connections.open();
        if (connection == null) throw new SQLException("No database connection was returned.", "08001");
        return connection;
    }

    private static long requireActor(AuthenticatedUser actor) {
        if (actor == null || !"USER".equals(actor.role())) {
            throw new IllegalArgumentException("An authenticated regular member is required.");
        }
        String value = actor.userId();
        if (value == null || !value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Member ID must be a positive numeric database ID.");
        }
        long parsed = Long.parseLong(value);
        if (parsed < 1 || parsed > 4294967295L) {
            throw new IllegalArgumentException("Member ID is outside the supported range.");
        }
        return parsed;
    }

    private static LibraryFine readFine(ResultSet result, String fineId) throws SQLException {
        try {
            return new LibraryFine(fineId, result.getString("loan_id"),
                    result.getString("book_title"), result.getString("barcode"),
                    result.getBigDecimal("amount"), result.getString("payment_status"),
                    result.getObject("borrowed_at", LocalDateTime.class),
                    result.getObject("due_at", LocalDateTime.class),
                    result.getObject("returned_at", LocalDateTime.class),
                    result.getObject("paid_at", LocalDateTime.class));
        } catch (IllegalArgumentException failure) {
            throw new SQLException("Fine records contain invalid data. Contact an administrator.", "22000", failure);
        }
    }

    private static SQLException invalidData() {
        return new SQLException("Fine records contain invalid data. Contact an administrator.", "22000");
    }
}

