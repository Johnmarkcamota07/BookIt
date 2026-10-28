/*
AuthenticationService() = This method helps set up the main security guard for the application.;
How it works: It prepares the application's login system by connecting it to the standard, live database where all the user accounts and passwords are securely stored.
Why is it used: It acts as the default starting point so the application knows exactly where to look to verify someone's identity when they try to log in.

AuthenticationService(ConnectionProvider connections) = This method helps set up the security guard using a custom connection.;
How it works: Instead of connecting to the real library database, it allows the system to use a specialized or temporary connection.
Why is it used: Programmers use this mostly to safely test the login screen without accidentally modifying or exposing real user passwords.

authenticatePassword(String username, String password) = This method helps check if a user's typed username and password are correct.;
How it works: It takes the typed username and password, securely checks them against the digital vault, and if they match, it logs the user in and resets any "failed login" counters. Crucially, it wipes the password from the computer's short-term memory the exact millisecond it finishes checking it.
Why is it used: It is the main front door of the application, ensuring only people with the correct credentials can get inside.

authenticateQrToken(String scannedValue) = This method helps a user log in by scanning a QR code instead of typing on a keyboard.;
How it works: It reads the secret code inside the scanned QR picture, checks to make sure the code hasn't expired or been revoked, and verifies that the member's account is still active and not currently locked out. If everything is valid, it logs them in.
Why is it used: It provides a fast, touch-free way for regular members to sign in at a physical library kiosk using just their smartphones.

issueOrReplaceUserQr(String username, String password) = This method helps a member request a brand-new login QR code.;
How it works: It takes a typed username and password and passes them along to the main QR generator to create a new scannable code.
Why is it used: It acts as a shortcut for generating a new QR code when a member doesn't already have a secure, logged-in session running.

issueOrReplaceUserQr(AuthenticatedUser expectedUser, String password) = This method helps a logged-in member securely reset their QR code.;
How it works: It double-checks that the person asking is a regular member (not a staff administrator). Just to be completely safe, it forces them to type their password one more time to prove they are still sitting at the computer before generating the new code.
Why is it used: It prevents a stranger who finds a computer left logged-in from generating a secret QR code for themselves to steal the account later.

issueOrReplaceUserQr(String username, String password, AuthenticatedUser expectedUser) = This method helps build, save, and hand over a brand-new QR login code.;
How it works: Once the password is confirmed, it generates a random new secret code. It then deletes any old QR codes tied to that account so they can never be used again, saves a scrambled version of the new code into the vault, and hands the raw code back so it can be drawn as a picture on the screen.
Why is it used: It handles the actual secure creation and replacement of the QR code so a member can safely use their phone to log in moving forward.

checkPassword(Connection connection, String username, char[] entered) = This method helps start the process of verifying a password.;
How it works: It simply passes the typed password along to the heavy-duty password checker without adding any extra identity restrictions.
Why is it used: It acts as a convenient, simple shortcut when someone is just trying to log in normally from the front screen.

checkPassword(Connection connection, String username, char[] entered, AuthenticatedUser expectedUser) = This method helps strictly enforce password rules and handles account lockouts.;
How it works: It fetches the user's account. If the password is wrong, it adds a "strike" to their record. If they get 5 strikes, it digitally locks the account for 15 minutes. Cleverly, even if the username typed doesn't exist, it still pretends to check a fake password for the exact same amount of time.
Why is it used: It is the heavy-duty security enforcer. The 15-minute lock stops hackers from using robots to guess millions of passwords. Pretending to check fake accounts prevents hackers from using a stopwatch to figure out which usernames are real and which are fake based on how fast the computer responds.

recordSuccess(Connection connection, String userId) = This method helps clear a user's bad record after a successful login.;
How it works: It tells the database to reset their "failed attempts" counter back to zero, removes any temporary 15-minute account locks, and stamps the current time as their official "last login."
Why is it used: It rewards a correct login by giving the user a clean slate, ensuring that a few innocent typos over the course of a year don't eventually lock them out permanently.

readUser(ResultSet rows) = This method helps package a user's account details into a neat digital ID badge.;
How it works: It takes raw database information (the user's ID number, username, and role) and stamps it onto a secure digital badge that cannot be altered.
Why is it used: It translates messy database data into a clean, locked format that the rest of the application can easily read and trust.

validInput(String username, String password) = This method helps check that the typed username and password aren't dangerously long or completely blank.;
How it works: It looks at the text and ensures the username isn't empty or over 64 characters, and verifies the password isn't over 1024 characters.
Why is it used: It stops bad actors from trying to crash the computer system by pasting a million-word essay into the username box.

transaction(SqlWork work) = This method helps guarantee that login checks and lockouts are saved securely.;
How it works: It opens a secure connection, tries to run the login check, and ensures the result is permanently saved. Even if the login fails and the system needs to record a "strike" against the account, this ensures that strike is saved properly. If the database crashes entirely, it safely cancels the action.
Why is it used: It prevents the database from getting confused, corrupted, or losing data if the computer loses power or connection at the exact millisecond someone clicks the login button.
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

    public String issueOrReplaceUserQr(String username, String password) throws SQLException {
        return issueOrReplaceUserQr(username, password, null);
    }

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
            int attempts = Math.min(65_535, Math.max(0, account.failures()) + 1);
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
        Connection opened = connections.open();
        if (opened == null) throw new SQLException("The database connection was unavailable.", "08001");
        try (Connection connection = opened) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
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