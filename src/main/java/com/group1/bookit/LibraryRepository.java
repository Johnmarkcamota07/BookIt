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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LibraryRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 12;

    private static final String BOOKS_SQL = """
            SELECT b.book_id AS book_id, b.title AS title, b.isbn AS isbn,
                   c.category_name AS category_name,
                   COALESCE(copies.total_copies, 0) AS total_copies,
                   COALESCE(copies.available_copies, 0) AS available_copies
            FROM book b
            LEFT JOIN category c ON c.category_id = b.category_id
            LEFT JOIN (
                SELECT bc.book_id AS book_id, COUNT(*) AS total_copies,
                       SUM(CASE WHEN bc.status = 'AVAILABLE'
                                     AND NOT EXISTS (
                                         SELECT 1 FROM loan open_loan
                                         WHERE open_loan.copy_id = bc.copy_id
                                           AND open_loan.returned_at IS NULL
                                     )
                                THEN 1 ELSE 0 END) AS available_copies
                FROM book_copy bc
                GROUP BY bc.book_id
            ) copies ON copies.book_id = b.book_id
            ORDER BY b.title, b.book_id
            """;

    private static final String AUTHORS_SQL = """
            SELECT ba.book_id AS book_id, a.author_name AS author_name
            FROM book_author ba
            JOIN author a ON a.author_id = ba.author_id
            ORDER BY ba.book_id, a.author_name, a.author_id
            """;

    private static final String MY_LOANS_SQL = """
            SELECT l.loan_id AS loan_id, b.title AS book_title,
                   bc.barcode AS barcode, l.loaned_at AS borrowed_at,
                   l.due_at AS due_at, l.returned_at AS returned_at
            FROM loan l
            JOIN book_copy bc ON bc.copy_id = l.copy_id
            JOIN book b ON b.book_id = bc.book_id
            WHERE l.user_id = ?
            ORDER BY l.loaned_at DESC, l.loan_id DESC
            """;

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private final ConnectionProvider connections;

    public LibraryRepository() {
        this(DatabaseConnection::getConnection);
    }

    public LibraryRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public List<LibraryBook> findBooks(AuthenticatedUser actor) throws SQLException {
        requireActor(actor, false);
        List<LibraryBook> books = new ArrayList<>();
        Map<String, List<String>> authors = new HashMap<>();

        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(BOOKS_SQL)) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        books.add(new LibraryBook(rows.getString("book_id"),
                                rows.getString("title"), "Author not listed",
                                fallback(rows.getString("category_name"), "Uncategorized"),
                                rows.getString("isbn"), rows.getInt("total_copies"),
                                rows.getInt("available_copies")));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(AUTHORS_SQL)) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String name = rows.getString("author_name");
                        if (name != null && !name.isBlank()) {
                            authors.computeIfAbsent(rows.getString("book_id"),
                                    ignored -> new ArrayList<>()).add(name);
                        }
                    }
                }
            }
        }

        List<LibraryBook> result = new ArrayList<>(books.size());
        for (LibraryBook book : books) {
            List<String> names = authors.get(book.bookId());
            result.add(new LibraryBook(book.bookId(), book.title(),
                    names == null ? "Author not listed" : String.join(", ", names),
                    book.category(), book.isbn(), book.totalCopies(), book.availableCopies()));
        }
        return List.copyOf(result);
    }

    public List<LibraryLoan> findMyLoans(AuthenticatedUser actor) throws SQLException {
        requireActor(actor, true);
        List<LibraryLoan> loans = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(MY_LOANS_SQL)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setString(1, actor.userId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {

                    loans.add(new LibraryLoan(rows.getString("loan_id"),
                            rows.getString("book_title"), rows.getString("barcode"),
                            rows.getObject("borrowed_at", LocalDateTime.class),
                            rows.getObject("due_at", LocalDateTime.class),
                            rows.getObject("returned_at", LocalDateTime.class)));
                }
            }
        }
        return List.copyOf(loans);
    }

    private Connection openConnection() throws SQLException {
        Connection connection = connections.open();
        if (connection == null) {
            throw new SQLException("The database connection provider returned no connection.");
        }
        return connection;
    }

    private static void requireActor(AuthenticatedUser actor, boolean memberOnly) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new IllegalArgumentException("A signed-in account with a user ID is required.");
        }
        boolean user = "USER".equals(actor.role());
        boolean admin = "ADMIN".equals(actor.role());
        if ((!user && !admin) || (memberOnly && !user)) {
            throw new IllegalArgumentException(memberOnly
                    ? "My loans requires a USER account."
                    : "Catalog access requires a USER or ADMIN account.");
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

