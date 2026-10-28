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
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Objects;

public class MemberProfileService {
    private static final int TIMEOUT_SECONDS = 12;
    private static final String SELECT_PROFILE = """
            SELECT user_id,username,full_name,email,phone,role,status,
                   joined_at,updated_at,last_login_at
            FROM app_user WHERE user_id=?
            """;
    private static final String UPDATE_PROFILE = """
            UPDATE app_user SET full_name=?,email=?,phone=?,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND role='USER' AND status='ACTIVE'
            """;
    private final LibraryRepository.ConnectionProvider connections;

    public enum Reason { INVALID_DETAILS, EMAIL_IN_USE, STALE_PROFILE, ACCOUNT_UNAVAILABLE }

    public static final class RejectedException extends IllegalArgumentException {
        private final Reason reason;
        public RejectedException(Reason reason) {
            super(switch (reason) {
                case INVALID_DETAILS -> "Enter a full name (up to 150 characters), a valid email (up to 255), and an optional phone (up to 30), without control characters.";
                case EMAIL_IN_USE -> "That email address is already in use. Enter a different email address.";
                case STALE_PROFILE -> "Your profile changed after it was loaded. Refresh your profile before editing again.";
                case ACCOUNT_UNAVAILABLE -> "Your member account is unavailable. Sign out and contact an administrator.";
            });
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }

    public MemberProfileService() { this(DatabaseConnection::getConnection); }

    public MemberProfileService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public MemberProfile findMyProfile(AuthenticatedUser actor) throws SQLException {
        long memberId = requireActor(actor);
        try (Connection connection = open()) {
            return readProfile(connection, actor, memberId, false);
        }
    }

    public MemberProfile updateMyProfile(AuthenticatedUser actor, MemberProfile expected,
            String fullName, String email, String phone) throws SQLException {
        long memberId = requireActor(actor);
        requireExpectedOwner(actor, memberId, expected);
        Details details = normalized(fullName, email, phone);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                MemberProfile current = readProfile(connection, actor, memberId, true);
                if (!Objects.equals(current.username(), expected.username())
                        || !sameEditableFields(current, expected)) {
                    throw reject(Reason.STALE_PROFILE);
                }
                MemberProfile result = current;
                if (!Objects.equals(details.fullName(), current.fullName())
                        || !Objects.equals(details.email(), current.email())
                        || !Objects.equals(details.phone(), current.phone())) {
                    try (PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE)) {
                        statement.setQueryTimeout(TIMEOUT_SECONDS);
                        statement.setString(1, details.fullName());
                        statement.setString(2, details.email());
                        if (details.phone() == null) statement.setNull(3, Types.VARCHAR);
                        else statement.setString(3, details.phone());
                        statement.setLong(4, memberId);
                        try {
                            if (statement.executeUpdate() != 1) {
                                throw new SQLException("The profile update did not affect exactly one member.", "40001");
                            }
                        } catch (SQLException failure) {
                            // yung pwede lang iedit yung unique column app_user.email.
                            if (failure.getErrorCode() == 1062) {
                                RejectedException duplicate = reject(Reason.EMAIL_IN_USE);
                                duplicate.initCause(failure);
                                throw duplicate;
                            }
                            throw failure;
                        }
                    }
                    result = readProfile(connection, actor, memberId, false);
                    if (!Objects.equals(result.fullName(), details.fullName())
                            || !Objects.equals(result.email(), details.email())
                            || !Objects.equals(result.phone(), details.phone())) {
                        throw new SQLException("The saved profile could not be confirmed.", "40001");
                    }
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
    }

    /** The UI may validate first; every write repeats exactly this validation. */
    public static void validateDetails(String fullName, String email, String phone) {
        normalized(fullName, email, phone);
    }

    private static Details normalized(String fullName, String email, String phone) {
        // Check before strip so leading/trailing tabs or line breaks are not silently accepted.
        if (hasControls(fullName) || hasControls(email) || hasControls(phone)) throw reject(Reason.INVALID_DETAILS);
        String name = fullName == null ? "" : fullName.strip();
        String address = email == null ? "" : email.strip();
        String number = phone == null ? "" : phone.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 150
                || address.isEmpty() || address.codePointCount(0, address.length()) > 255
                || address.codePoints().anyMatch(code -> Character.isWhitespace(code) || Character.isSpaceChar(code))
                || !address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || number.codePointCount(0, number.length()) > 30) {
            throw reject(Reason.INVALID_DETAILS);
        }
        return new Details(name, address, number.isEmpty() ? null : number);
    }

    private static boolean hasControls(String value) {
        return value != null && value.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean sameEditableFields(MemberProfile first, MemberProfile second) {
        return Objects.equals(first.fullName(), second.fullName())
                && Objects.equals(first.email(), second.email())
                && Objects.equals(first.phone(), second.phone());
    }

    private static void requireExpectedOwner(AuthenticatedUser actor, long memberId, MemberProfile expected) {
        if (expected == null || numericId(expected.userId()) != memberId
                || !Objects.equals(expected.username(), actor.username())
                || !"USER".equals(expected.role())) {
            throw reject(Reason.ACCOUNT_UNAVAILABLE);
        }
    }

    private MemberProfile readProfile(Connection connection, AuthenticatedUser actor,
            long memberId, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PROFILE + (lock ? " FOR UPDATE" : ""))) {
            statement.setQueryTimeout(TIMEOUT_SECONDS);
            statement.setLong(1, memberId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw reject(Reason.ACCOUNT_UNAVAILABLE);
                MemberProfile profile = new MemberProfile(rows.getString("user_id"), rows.getString("username"),
                        rows.getString("full_name"), rows.getString("email"), rows.getString("phone"),
                        rows.getString("role"), rows.getString("status"),
                        rows.getObject("joined_at", LocalDateTime.class),
                        rows.getObject("updated_at", LocalDateTime.class),
                        rows.getObject("last_login_at", LocalDateTime.class));
                if (numericId(profile.userId()) != memberId || !"USER".equals(profile.role())
                        || !"ACTIVE".equals(profile.status())
                        || !Objects.equals(actor.username(), profile.username())) {
                    throw reject(Reason.ACCOUNT_UNAVAILABLE);
                }
                // Only structural schema requirements are checked on reads.
                // Historical names/email formats must still load so members can correct them.
                if (profile.fullName() == null || profile.email() == null
                        || profile.joinedAt() == null || profile.updatedAt() == null) {
                    throw new SQLException("The member profile contains incomplete data. Contact an administrator.", "22000");
                }
                if (rows.next()) throw new SQLException("More than one profile matched the member ID.", "22000");
                return profile;
            }
        }
    }

    private Connection open() throws SQLException {
        Connection connection = connections.open();
        if (connection == null) throw new SQLException("No database connection was returned.", "08001");
        return connection;
    }

    private static long requireActor(AuthenticatedUser actor) {
        if (actor == null || !"USER".equals(actor.role())
                || actor.username() == null || actor.username().isBlank()) throw reject(Reason.ACCOUNT_UNAVAILABLE);
        return numericId(actor.userId());
    }

    private static long numericId(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) throw reject(Reason.ACCOUNT_UNAVAILABLE);
        long parsed = Long.parseLong(value);
        if (parsed < 1 || parsed > 4294967295L) throw reject(Reason.ACCOUNT_UNAVAILABLE);
        return parsed;
    }

    private static RejectedException reject(Reason reason) { return new RejectedException(reason); }
    private record Details(String fullName, String email, String phone) {}
}

