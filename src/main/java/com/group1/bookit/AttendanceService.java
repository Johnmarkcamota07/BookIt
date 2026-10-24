//accept(String qr, long now) = This method helps prevent the camera from accidentally scanning the same QR code dozens of times in a row.;
//How it works: It acts like a digital toll booth gate. Every fraction of a second, the camera reports what it sees. If it sees a QR code, the gate accepts the code and instantly locks itself. To unlock the gate for the next scan, two things must happen: a two-second cooldown timer must finish, and the camera must clearly see that the QR code has been physically moved out of the frame for almost a full second.
//Why is it used: Because video cameras take dozens of pictures every second, simply holding your phone in front of the lens would normally cause the system to scan your code 30 times in a single second, logging you in and out instantly. This method forces the system to process one code, pause, and wait for the camera's view to clear before it allows the next person to scan.

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Attendance is separate from app login. Invoke JDBC methods off the FX thread. */
public class AttendanceService {
    private static final int TIMEOUT_SECONDS = 12;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> HEADERS = List.of("Visit ID", "User ID", "Member",
            "Time in", "Time out", "Duration", "Status", "Correction reason");
    private final LibraryRepository.ConnectionProvider connections;

    public AttendanceService() { this(DatabaseConnection::getConnection); }

    public AttendanceService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public AdminData.Page load(AuthenticatedUser actor) throws SQLException {
        return transaction(actor, connection -> {
            LocalDate today = LocalDate.now();
            LocalDateTime start = midnightUtc(today);
            LocalDateTime end = midnightUtc(today.plusDays(1));
            Map<String, String> metrics = new LinkedHashMap<>();
            try (PreparedStatement query = statement(connection, """
                    SELECT COUNT(CASE WHEN time_in >= ? AND time_in < ? THEN 1 END) AS visits_today,
                           COUNT(CASE WHEN time_out IS NULL THEN 1 END) AS inside_now,
                           COUNT(CASE WHEN time_out IS NULL AND time_in < ? THEN 1 END) AS missing_timeout
                    FROM library_visit
                    """)) {
                query.setObject(1, start);
                query.setObject(2, end);
                query.setObject(3, start);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    metrics.put("Visits today", rows.getString("visits_today"));
                    metrics.put("Open visits", rows.getString("inside_now"));
                    metrics.put("Missing time-out", rows.getString("missing_timeout"));
                }
            }
            // Bounded interactive view. Reports retrieve the entire selected date range.
            return new AdminData.Page(metrics, visits(connection, null, null, true));
        });
    }

    /** Includes visits whose entrance falls in the inclusive local date range. */
    public AdminData.Table report(AuthenticatedUser actor, LocalDate from, LocalDate to)
            throws SQLException {
        if (from == null || to == null || to.isBefore(from) || to.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("Choose a valid attendance report date range.");
        }
        return transaction(actor, connection -> visits(connection, midnightUtc(from),
                midnightUtc(to.plusDays(1)), false));
    }

    /** Input times are wall-clock times in this PC's time zone, converted to UTC. */
    public void correct(AuthenticatedUser actor, String visitId, LocalDateTime timeIn,
                        LocalDateTime timeOut, String reason) throws SQLException {
        requireId(visitId, "Visit ID");
        if (timeIn == null) throw new IllegalArgumentException("Time in is required.");
        String explanation = reason == null ? "" : reason.trim();
        if (explanation.isEmpty() || explanation.length() > 300) {
            // Leave room in the 500-character immutable audit detail for old/new timestamps.
            throw new IllegalArgumentException("Enter a correction reason of 1 to 300 characters.");
        }
        LocalDateTime inUtc = localToUtc(timeIn);
        LocalDateTime outUtc = timeOut == null ? null : localToUtc(timeOut);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        if (inUtc.isAfter(now) || (outUtc != null && outUtc.isAfter(now))) {
            throw new IllegalArgumentException("Attendance times cannot be in the future.");
        }
        if (outUtc != null && outUtc.isBefore(inUtc)) {
            throw new IllegalArgumentException("Time out cannot be before time in.");
        }
        transaction(actor, connection -> {
            // Discover owner, then lock that user: the same ordering as QR toggles.
            String userId;
            try (PreparedStatement query = statement(connection,
                    "SELECT user_id FROM library_visit WHERE visit_id = ?")) {
                query.setString(1, visitId);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Visit no longer exists.");
                    userId = rows.getString("user_id");
                }
            }
            try (PreparedStatement lock = statement(connection,
                    "SELECT user_id FROM app_user WHERE user_id = ? FOR UPDATE")) {
                lock.setString(1, userId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Member no longer exists.");
                }
            }
            LocalDateTime oldIn;
            LocalDateTime oldOut;
            try (PreparedStatement lock = statement(connection,
                    "SELECT time_in, time_out FROM library_visit WHERE visit_id = ? FOR UPDATE")) {
                lock.setString(1, visitId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Visit no longer exists.");
                    oldIn = rows.getObject("time_in", LocalDateTime.class);
                    oldOut = rows.getObject("time_out", LocalDateTime.class);
                }
            }
            if (outUtc == null) {
                try (PreparedStatement query = statement(connection,
                        "SELECT visit_id FROM library_visit WHERE open_user_id = ? AND visit_id <> ? FOR UPDATE")) {
                    query.setString(1, userId);
                    query.setString(2, visitId);
                    try (ResultSet rows = query.executeQuery()) {
                        if (rows.next()) throw new IllegalArgumentException("This member already has another open visit.");
                    }
                }
            }
            try (PreparedStatement update = statement(connection, """
                    UPDATE library_visit SET time_in = ?, time_out = ?,
                        corrected_by = ?, correction_reason = ? WHERE visit_id = ?
                    """)) {
                update.setObject(1, inUtc);
                update.setObject(2, outUtc);
                update.setString(3, actor.userId());
                update.setString(4, explanation);
                update.setString(5, visitId);
                update.executeUpdate();
            }
            audit(connection, actor, "ATTENDANCE_CORRECT", visitId,
                    "UTC " + oldIn + " / " + oldOut + " -> " + inUtc + " / " + outUtc
                    + "; reason: " + explanation);
            return null;
        });
    }

    /** Reuses LOGIN:<token>, but never creates or changes a BookIt login session. */
    public String toggle(AuthenticatedUser actor, String scannedValue) throws SQLException {
        if (scannedValue == null || !scannedValue.startsWith("LOGIN:")) {
            throw new IllegalArgumentException("This is not a BookIt member QR.");
        }
        String token = scannedValue.substring("LOGIN:".length());
        if (!QrTokens.isValidToken(token)) {
            throw new IllegalArgumentException("This is not a valid BookIt member QR.");
        }
        return transaction(actor, connection -> {
            String userId;
            String member;
            // Locks member + credential until the visit and audit commit together.
            try (PreparedStatement query = statement(connection, """
                    SELECT u.user_id, u.full_name
                    FROM user_qr_credential q JOIN app_user u ON u.user_id = q.user_id
                    WHERE q.token_hash = ? AND q.revoked_at IS NULL
                        AND (q.expires_at IS NULL OR q.expires_at > UTC_TIMESTAMP())
                        AND u.status = 'ACTIVE' AND u.role = 'USER'
                        AND (u.locked_until IS NULL OR u.locked_until <= UTC_TIMESTAMP())
                    FOR UPDATE
                    """)) {
                query.setBytes(1, QrTokens.hash(token));
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException(
                            "QR not recognized, expired, revoked, or account unavailable.");
                    userId = rows.getString("user_id");
                    member = rows.getString("full_name");
                }
            }
            String visitId = null;
            LocalDateTime previousIn = null;
            try (PreparedStatement query = statement(connection,
                    "SELECT visit_id, time_in FROM library_visit WHERE open_user_id = ? FOR UPDATE")) {
                query.setString(1, userId);
                try (ResultSet rows = query.executeQuery()) {
                    if (rows.next()) {
                        visitId = rows.getString("visit_id");
                        previousIn = rows.getObject("time_in", LocalDateTime.class);
                    }
                }
            }
            LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).withNano(0);
            if (visitId == null) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO library_visit(user_id, time_in) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setQueryTimeout(TIMEOUT_SECONDS);
                    insert.setString(1, userId);
                    insert.setObject(2, now);
                    insert.executeUpdate();
                    try (ResultSet key = insert.getGeneratedKeys()) {
                        if (!key.next()) throw new SQLException("The new visit ID was not returned.");
                        visitId = key.getString(1);
                    }
                }
                audit(connection, actor, "ATTENDANCE_IN", visitId,
                        "User " + userId + "; entrance kiosk; UTC " + now);
                return member + " — Time In recorded at " + display(now) + ".";
            }
            if (previousIn.isAfter(now)) throw new IllegalArgumentException(
                    "This visit has a future entrance time. Ask an administrator to correct it.");
            try (PreparedStatement update = statement(connection,
                    "UPDATE library_visit SET time_out = ? WHERE visit_id = ? AND time_out IS NULL")) {
                update.setObject(1, now);
                update.setString(2, visitId);
                if (update.executeUpdate() != 1) throw new SQLException("Visit changed; check the attendance list before scanning again.");
            }
            audit(connection, actor, "ATTENDANCE_OUT", visitId,
                    "User " + userId + "; entrance kiosk; UTC " + now);
            return member + " — Time Out recorded at " + display(now)
                    + ". Duration: " + duration(previousIn, now) + ".";
        });
    }

    private AdminData.Table visits(Connection connection, LocalDateTime from,
                                   LocalDateTime to, boolean limited) throws SQLException {
        List<List<String>> result = new ArrayList<>();
        String sql = """
                SELECT v.visit_id, v.user_id, u.full_name, v.time_in, v.time_out, v.correction_reason
                FROM library_visit v JOIN app_user u ON u.user_id = v.user_id
                """ + (from == null ? "" : " WHERE v.time_in >= ? AND v.time_in < ?")
                + " ORDER BY v.time_in DESC, v.visit_id DESC" + (limited ? " LIMIT 1000" : "");
        try (PreparedStatement query = statement(connection, sql)) {
            if (from != null) { query.setObject(1, from); query.setObject(2, to); }
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    LocalDateTime in = rows.getObject("time_in", LocalDateTime.class);
                    LocalDateTime out = rows.getObject("time_out", LocalDateTime.class);
                    boolean missing = out == null && in.toInstant(ZoneOffset.UTC)
                            .atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now());
                    result.add(List.of(rows.getString("visit_id"), rows.getString("user_id"),
                            rows.getString("full_name"), display(in), out == null ? "—" : display(out),
                            out == null ? "—" : duration(in, out),
                            out != null ? "Completed" : missing ? "Missing time-out" : "Inside",
                            Objects.toString(rows.getString("correction_reason"), "")));
                }
            }
        }
        return new AdminData.Table(HEADERS, result);
    }

    private <T> T transaction(AuthenticatedUser actor, Work<T> work) throws SQLException {
        if (actor == null || !"ADMIN".equals(actor.role())) {
            throw new IllegalArgumentException("An administrator account is required.");
        }
        requireId(actor.userId(), "Administrator ID");
        try (Connection connection = connections.open()) {
            if (connection == null) throw new SQLException("Database connection unavailable.");
            connection.setAutoCommit(false);
            try {
                // Never trust the cached dashboard role alone.
                try (PreparedStatement query = statement(connection, """
                        SELECT user_id FROM app_user WHERE user_id = ? AND role = 'ADMIN'
                            AND status = 'ACTIVE'
                            AND (locked_until IS NULL OR locked_until <= UTC_TIMESTAMP()) FOR UPDATE
                        """)) {
                    query.setString(1, actor.userId());
                    try (ResultSet rows = query.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException(
                                "Administrator access is no longer active. Sign in again.");
                    }
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); }
                catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }

    private static PreparedStatement statement(Connection connection, String sql) throws SQLException {
        PreparedStatement result = connection.prepareStatement(sql);
        result.setQueryTimeout(TIMEOUT_SECONDS);
        return result;
    }

    private static void audit(Connection connection, AuthenticatedUser actor,
                              String action, String visitId, String details) throws SQLException {
        try (PreparedStatement insert = statement(connection, """
                INSERT INTO admin_audit(admin_id, action, target_type, target_id, details, created_at)
                VALUES (?, ?, 'library_visit', ?, ?, UTC_TIMESTAMP())
                """)) {
            insert.setString(1, actor.userId());
            insert.setString(2, action);
            insert.setString(3, visitId);
            insert.setString(4, details.length() > 500 ? details.substring(0, 500) : details);
            insert.executeUpdate();
        }
    }

    private static void requireId(String value, String label) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}"))
            throw new IllegalArgumentException(label + " must be a positive numeric ID.");
    }

    private static LocalDateTime midnightUtc(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneOffset.UTC);
    }

    private static LocalDateTime localToUtc(LocalDateTime value) {
        ZoneId local = ZoneId.systemDefault();
        if (local.getRules().getValidOffsets(value).size() != 1) throw new IllegalArgumentException(
                "The entered local time is ambiguous or does not exist in this computer's time zone.");
        return LocalDateTime.ofInstant(value.atZone(local).toInstant(), ZoneOffset.UTC);
    }

    private static String display(LocalDateTime utc) {
        return DISPLAY.format(utc.toInstant(ZoneOffset.UTC).atZone(ZoneId.systemDefault()));
    }

    private static String duration(LocalDateTime from, LocalDateTime to) {
        long minutes = Math.max(0, Duration.between(from, to).toMinutes());
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }
}

