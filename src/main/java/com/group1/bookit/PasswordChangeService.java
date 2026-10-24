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
import java.util.Arrays;
import java.util.Objects;

/** Own-account password changes. Invoke on a worker, not the JavaFX thread.
 * Hash changes and existing QR revocation commit together. No raw password is
 * retained or included in any message. This is not an administrator reset API. */
public class PasswordChangeService {
    private static final int TIMEOUT_SECONDS = 12;
    private static final String SELECT_ACCOUNT = """
            SELECT user_id, username, role, password_hash, status,
                   (locked_until IS NOT NULL AND locked_until > UTC_TIMESTAMP()) AS locked,
                   CASE WHEN locked_until IS NOT NULL AND locked_until <= UTC_TIMESTAMP()
                        THEN 0 ELSE failed_login_attempts END AS failures
            FROM app_user WHERE user_id = ? FOR UPDATE
            """;
    private final LibraryRepository.ConnectionProvider connections;

    public enum Outcome { CHANGED, REJECTED }

    public PasswordChangeService() { this(DatabaseConnection::getConnection); }

    public PasswordChangeService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** Rejected password attempts commit shared login counters. SQL/commit failures
     * propagate and must not be automatically retried. Caller arrays are unchanged;
     * private password snapshots are wiped on success, rejection and exceptions. */
    public Outcome changePassword(AuthenticatedUser actor, char[] currentPassword,
            char[] newPassword, char[] confirmation) throws SQLException {
        char[] current = null;
        char[] fresh = null;
        char[] confirmed = null;
        try {
            current = copy(currentPassword);
            fresh = copy(newPassword);
            confirmed = copy(confirmation);
            long userId = requireActor(actor);
            validateInput(current, fresh, confirmed);
            try (Connection connection = open()) {
                try {
                    connection.setAutoCommit(false);
                    Outcome result = change(connection, actor, userId, current, fresh);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException failure) {
                    try { connection.rollback(); }
                    catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                    throw failure;
                }
            }
        } finally {
            wipe(current);
            wipe(fresh);
            wipe(confirmed);
        }
    }

    /** UTF-16 length matches PasswordHasher. Spaces and Unicode are preserved;
     * passwords are never trimmed and no composition rule is imposed. */
    public static void validateInput(char[] current, char[] fresh, char[] confirmed) {
        if (current == null || current.length == 0 || current.length > 1024) {
            throw new IllegalArgumentException("Enter your current password (up to 1024 characters).");
        }
        if (fresh == null || fresh.length < 8 || fresh.length > 1024) {
            throw new IllegalArgumentException("Use a new password of 8 to 1024 characters.");
        }
        if (confirmed == null || !Arrays.equals(fresh, confirmed)) {
            throw new IllegalArgumentException("The new password and confirmation do not match.");
        }
        if (Arrays.equals(current, fresh)) {
            throw new IllegalArgumentException("Choose a new password different from your current password.");
        }
    }

    private Outcome change(Connection connection, AuthenticatedUser actor,
            long userId, char[] current, char[] fresh) throws SQLException {
        String storedHash;
        int failures;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACCOUNT)) {
            statement.setQueryTimeout(TIMEOUT_SECONDS);
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Outcome.REJECTED;
                if (numericId(rows.getString("user_id")) != userId
                        || !Objects.equals(actor.username(), rows.getString("username"))
                        || !Objects.equals(actor.role(), rows.getString("role"))
                        || !"ACTIVE".equals(rows.getString("status"))
                        || rows.getBoolean("locked")) return Outcome.REJECTED;
                storedHash = rows.getString("password_hash");
                failures = rows.getInt("failures");
                if (failures < 0 || failures > 65535 || rows.next()) {
                    throw new SQLException("The account data is invalid. Contact an administrator.", "22000");
                }
            }
        }

        if (!PasswordHasher.verify(current, storedHash)) {
            // Same shared five-failures / fifteen-minute lock as AuthenticationService.
            int attempts = Math.min(failures + 1, 65535);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE app_user SET failed_login_attempts = ?,
                        locked_until = CASE WHEN ? >= 5
                            THEN DATE_ADD(UTC_TIMESTAMP(), INTERVAL 15 MINUTE)
                            ELSE NULL END
                    WHERE user_id = ?
                    """)) {
                statement.setQueryTimeout(TIMEOUT_SECONDS);
                statement.setInt(1, attempts);
                statement.setInt(2, attempts);
                statement.setLong(3, userId);
                requireOne(statement.executeUpdate());
            }
            return Outcome.REJECTED;
        }

        String newHash = PasswordHasher.hash(fresh);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE app_user SET password_hash = ?, failed_login_attempts = 0,
                    locked_until = NULL, updated_at = UTC_TIMESTAMP()
                WHERE user_id = ?
                """)) {
            statement.setQueryTimeout(TIMEOUT_SECONDS);
            statement.setString(1, newHash);
            statement.setLong(2, userId);
            requireOne(statement.executeUpdate());
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE user_qr_credential SET revoked_at = UTC_TIMESTAMP()
                WHERE user_id = ? AND revoked_at IS NULL
                """)) {
            statement.setQueryTimeout(TIMEOUT_SECONDS);
            statement.setLong(1, userId);
            int affected = statement.executeUpdate();
            if (affected < 0 || affected > 1) {
                throw new SQLException("The QR credential update could not be confirmed.", "40001");
            }
        }
        return Outcome.CHANGED;
    }

    private Connection open() throws SQLException {
        Connection connection = connections.open();
        if (connection == null) throw new SQLException("No database connection was returned.", "08001");
        return connection;
    }

    private static long requireActor(AuthenticatedUser actor) {
        if (actor == null || actor.username() == null || actor.username().isBlank()
                || actor.username().codePointCount(0, actor.username().length()) > 64
                || (!"USER".equals(actor.role()) && !"ADMIN".equals(actor.role()))) throw invalidActor();
        return numericId(actor.userId());
    }

    private static long numericId(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) throw invalidActor();
        long parsed = Long.parseLong(value);
        if (parsed < 1 || parsed > 4294967295L) throw invalidActor();
        return parsed;
    }

    private static IllegalArgumentException invalidActor() {
        return new IllegalArgumentException("Your account is unavailable. Sign out and sign in again.");
    }

    private static void requireOne(int affected) throws SQLException {
        if (affected != 1) throw new SQLException("The account update could not be confirmed.", "40001");
    }

    private static char[] copy(char[] value) { return value == null ? null : value.clone(); }
    private static void wipe(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
}

