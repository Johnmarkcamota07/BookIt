/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public final class AuthenticationService {
    // Kept nested, as discussed. Import this type in controllers and SessionManager.
    public record AuthenticatedUser(String userId, String username, String role) {}

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private final ConnectionProvider connections;

    public AuthenticationService() {
        this(DatabaseConnection::getConnection);
    }

    public AuthenticationService(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections);
    }

    // null = rejected credentials; SQLException = database unavailable/problem.
    // Call off the JavaFX UI thread: SQL and password hashing can take time.
    public AuthenticatedUser authenticatePassword(String username, String password)
            throws SQLException {
        if (!validInput(username, password)) return null;
        char[] entered = password.toCharArray();
        try {
            return transaction(connection -> {
                Account account = checkPassword(connection, username.trim(), entered);
                if (account == null) return null;
                recordSuccess(connection, account.user().userId());
                return account.user();
            });
        } finally {
            Arrays.fill(entered, '\0');
        }
    }

    public AuthenticatedUser authenticateQrToken(String scannedValue) throws SQLException {
        if (scannedValue == null || !scannedValue.startsWith("LOGIN:")) return null;
        String token = scannedValue.substring("LOGIN:".length());
        if (!QrTokens.isValidToken(token)) return null;

        return transaction(connection -> {
            String sql = """
                SELECT u.user_id, u.username, u.role
                FROM user_qr_credential q
                JOIN app_user u ON u.user_id = q.user_id
                WHERE q.token_hash = ?
                  AND q.revoked_at IS NULL
                  AND (q.expires_at IS NULL OR q.expires_at > UTC_TIMESTAMP())
                  AND u.status = 'ACTIVE'
                  AND (u.locked_until IS NULL OR u.locked_until <= UTC_TIMESTAMP())
                  AND u.role = 'USER'
                FOR UPDATE
                """;
            AuthenticatedUser user;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, QrTokens.hash(token));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    user = readUser(rows);
                }
            }
            // Reusable QR: do not consume or revoke the credential after a scan.
            recordSuccess(connection, user.userId());
            return user;
        });
    }

    // Called from an authenticated user's "Generate/replace my QR" flow.
    // Revalidates the password; never accepts just a caller-supplied user ID.
    // Returns a RAW token for CodeGenerator.generateLoginQrCode(token).
    // null means rejected; admin accounts intentionally use password login.
    public String issueOrReplaceUserQr(String username, String password) throws SQLException {
        return issueOrReplaceUserQr(username, password, null);
    }

    /**
     * Creates or replaces the signed-in USER's reusable QR after password confirmation.
     * The immutable session ID and current database role are checked while the account
     * row is locked, so a renamed or reassigned username cannot issue another user's QR.
     * Invalid actors are rejected before connecting. Returns {@code null} on rejection;
     * password-failure counters still commit. Returns the raw token only after the
     * hash-only replacement commits; pass it to CodeGenerator.generateLoginQrCode.
     * SQL failures roll back and propagate. Call this off the JavaFX UI thread.
     */
    public String issueOrReplaceUserQr(AuthenticatedUser expectedUser, String password)
            throws SQLException {
        if (expectedUser == null || expectedUser.userId() == null
                || expectedUser.userId().isBlank() || !"USER".equals(expectedUser.role())
                || !validInput(expectedUser.username(), password)) return null;
        return issueOrReplaceUserQr(expectedUser.username(), password, expectedUser);
    }

    private String issueOrReplaceUserQr(String username, String password,
                                       AuthenticatedUser expectedUser) throws SQLException {
        if (!validInput(username, password)) return null;
        char[] entered = password.toCharArray();
        try {
            return transaction(connection -> {
                Account account = checkPassword(connection, username.trim(), entered, expectedUser);
                if (account == null || !account.user().role().equals("USER")) return null;
                String token = QrTokens.generate();
                try (PreparedStatement remove = connection.prepareStatement(
                        "DELETE FROM user_qr_credential WHERE user_id = ?")) {
                    remove.setString(1, account.user().userId());
                    remove.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO user_qr_credential (user_id, token_hash, created_at)
                        VALUES (?, ?, UTC_TIMESTAMP())
                        """)) {
                    insert.setString(1, account.user().userId());
                    insert.setBytes(2, QrTokens.hash(token));
                    insert.executeUpdate();
                }
                recordSuccess(connection, account.user().userId());
                return token;
            });
        } finally {
            Arrays.fill(entered, '\0');
        }
    }

    private Account checkPassword(Connection connection, String username, char[] entered)
            throws SQLException {
        return checkPassword(connection, username, entered, null);
    }

    private Account checkPassword(Connection connection, String username, char[] entered,
                                  AuthenticatedUser expectedUser) throws SQLException {
        Account account;
        String sql = """
            SELECT user_id, username, role, password_hash, status,
                   (locked_until IS NOT NULL AND locked_until > UTC_TIMESTAMP()) AS locked,
                   CASE WHEN locked_until IS NOT NULL AND locked_until <= UTC_TIMESTAMP()
                        THEN 0 ELSE failed_login_attempts END AS failures
            FROM app_user WHERE username = ? FOR UPDATE
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    // Do comparable password work even for an unknown username.
                    PasswordHasher.verify(entered, DummyHash.VALUE);
                    return null;
                }
                account = new Account(readUser(rows), rows.getString("password_hash"),
                        rows.getString("status").equals("ACTIVE"),
                        rows.getBoolean("locked"), rows.getInt("failures"));
            }
        }
        if (expectedUser != null && (!expectedUser.userId().equals(account.user().userId())
                || !"USER".equals(account.user().role()))) return null;
        if (!account.active() || account.locked()) return null;
        if (!PasswordHasher.verify(entered, account.passwordHash())) {
            int attempts = account.failures() + 1;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE app_user SET failed_login_attempts = ?,
                        locked_until = CASE WHEN ? >= 5
                            THEN DATE_ADD(UTC_TIMESTAMP(), INTERVAL 15 MINUTE)
                            ELSE NULL END
                    WHERE user_id = ?
                    """)) {
                statement.setInt(1, attempts);
                statement.setInt(2, attempts);
                statement.setString(3, account.user().userId());
                statement.executeUpdate();
            }
            return null;
        }
        return account;
    }

    private static void recordSuccess(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL,
                    last_login_at = UTC_TIMESTAMP() WHERE user_id = ?
                """)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private static AuthenticatedUser readUser(ResultSet rows) throws SQLException {
        return new AuthenticatedUser(rows.getString("user_id"),
                rows.getString("username"), rows.getString("role"));
    }

    private static boolean validInput(String username, String password) {
        return username != null && !username.isBlank() && username.trim().length() <= 64
                && password != null && !password.isEmpty() && password.length() <= 1024;
    }

    private record Account(AuthenticatedUser user, String passwordHash,
                           boolean active, boolean locked, int failures) {}

    private static final class DummyHash {
        private static final String VALUE = PasswordHasher.hash(
                "dummy-account-password-not-a-real-login".toCharArray());
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T transaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                // Commit rejected-login counters as well as successful operations.
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }
}
