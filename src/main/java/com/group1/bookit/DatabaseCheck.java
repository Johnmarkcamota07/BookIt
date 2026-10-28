/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class DatabaseCheck {
    private static final int TIMEOUT = 12;
    private static final Map<String,List<String>> COLUMNS = columns();
    private static final Map<String,List<String>> GENERATED = Map.of(
            "loan",List.of("active_copy_id"), "reservation",List.of("active_book_id"),
            "library_visit",List.of("open_user_id"));
    private static final Map<String,List<String>> UNIQUE = Map.of(
            "loan",List.of("active_copy_id"), "reservation",List.of("user_id","active_book_id"),
            "library_visit",List.of("open_user_id"));
    private record IndexId(String table,String name) {}
    private DatabaseCheck() {}

    public static void main(String[] args) {
        int result = run(DatabaseConnection::getConnection, System.out);
        if (result != 0) System.exit(result);
    }

    public static int run(LibraryRepository.ConnectionProvider provider, PrintStream out) {
        Objects.requireNonNull(out, "output");
        out.println("BookIt database check (read-only; no migrations or library records are changed).");
        int problems;
        try (Connection connection = Objects.requireNonNull(provider, "provider").open()) {
            if (connection == null) throw new SQLException("No connection returned.", "08001");
            String schema = connection.getCatalog();
            if (schema == null || schema.isBlank()) {
                out.println("FAIL: no database is selected. Include your existing BookIt database name in the JDBC URL.");
                return 2;
            }
            out.println("Selected database: " + (schema.matches("[A-Za-z0-9_$-]{1,64}") ? schema : "(name hidden)"));
            if (!supportedVersion(connection, out)) return 2;

            Map<String,String> engines = new LinkedHashMap<>();
            try (PreparedStatement s = query(connection,
                    "SELECT table_name,engine FROM information_schema.tables WHERE table_schema=?",schema);
                 ResultSet rows = s.executeQuery()) {
                while (rows.next()) engines.put(rows.getString(1),rows.getString(2));
            }
            Map<String,Map<String,String>> foundColumns = new LinkedHashMap<>();
            try (PreparedStatement s = query(connection,
                    "SELECT table_name,column_name,extra FROM information_schema.columns WHERE table_schema=?",schema);
                 ResultSet rows = s.executeQuery()) {
                while (rows.next()) {
                    String column = rows.getString(2);
                    if (column == null) continue;
                    foundColumns.computeIfAbsent(rows.getString(1),ignored -> new LinkedHashMap<>())
                            .put(column.toLowerCase(Locale.ROOT),rows.getString(3));
                }
            }
            Map<String,Map<String,List<String>>> indexes = new LinkedHashMap<>();
            Set<IndexId> unsupportedIndexes = new LinkedHashSet<>();
            try (PreparedStatement s = query(connection,
                    "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=? ORDER BY table_name,index_name,seq_in_index",schema);
                 ResultSet rows = s.executeQuery()) {
                while (rows.next()) {
                    String column = rows.getString(5);
                    if (rows.getInt(3) != 0) continue;
                    if (column == null) {
                        unsupportedIndexes.add(new IndexId(rows.getString(1),rows.getString(2)));
                        continue;
                    }
                    indexes.computeIfAbsent(rows.getString(1),ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(rows.getString(2),ignored -> new ArrayList<>())
                            .add(column.toLowerCase(Locale.ROOT));
                }
            }
            for (IndexId index : unsupportedIndexes) {
                Map<String,List<String>> tableIndexes = indexes.get(index.table());
                if (tableIndexes != null) tableIndexes.remove(index.name());
            }

            problems = 0;
            for (Map.Entry<String,List<String>> table : COLUMNS.entrySet()) {
                String name = table.getKey();
                if (!engines.containsKey(name)) {
                    out.println("FAIL: missing table " + name + ". " + migration(name));
                    problems++;
                    continue;
                }
                if (!"InnoDB".equalsIgnoreCase(engines.get(name))) {
                    out.println("FAIL: " + name + " must use transactional InnoDB. Back up and review its schema; this checker does not convert it.");
                    problems++;
                }
                Map<String,String> columns = foundColumns.getOrDefault(name,Map.of());
                Set<String> missing = new LinkedHashSet<>(table.getValue());
                missing.removeAll(columns.keySet());
                if (!missing.isEmpty()) {
                    out.println("FAIL: " + name + " is missing columns: " + String.join(", ",missing) + ". " + migration(name));
                    problems++;
                }
                for (String generated : GENERATED.getOrDefault(name,List.of())) {
                    String extra = columns.get(generated);
                    if (columns.containsKey(generated) && (extra == null || !extra.toUpperCase(Locale.ROOT).contains("GENERATED"))) {
                        out.println("FAIL: " + name + "." + generated + " must be a generated column. Review the original schema; do not recreate populated tables.");
                        problems++;
                    }
                }
                List<String> required = UNIQUE.get(name);
                if (required != null && columns.keySet().containsAll(required)
                        && indexes.getOrDefault(name,Map.of()).values().stream().noneMatch(index ->
                            index.size()==required.size() && new LinkedHashSet<>(index).equals(new LinkedHashSet<>(required)))) {
                    out.println("FAIL: " + name + " needs a UNIQUE index on (" + String.join(", ",required) + ") to prevent duplicate active records.");
                    problems++;
                }
            }
        } catch (SQLException failure) {
            String state = failure.getSQLState();
            String safeState = state != null && state.matches("[A-Z0-9]{5}") ? state : "unknown";
            out.println("FAIL: database check could not finish. SQLState=" + safeState + ", vendorCode=" + failure.getErrorCode() + ".");
            if ("08001".equals(state)) out.println("Check the selected database configuration and connection settings; no settings or secrets are displayed.");
            else out.println("Check the connection, selected schema and permission to inspect metadata. No raw server message or credentials are displayed.");
            return 1;
        } catch (RuntimeException failure) {
            out.println("FAIL: database configuration or metadata could not be checked. No raw error, credentials or settings are displayed.");
            return 1;
        }
        if (problems != 0) {
            out.println("Schema check failed: " + problems + " issue(s). Apply only the required additive migrations after backup; never rerun the full schema over existing data.");
            return 2;
        }
        out.println("PASS: all 15 required tables/columns, InnoDB engines and active-record uniqueness guards are present.");
        out.println("This checks schema metadata only. Test login, data, workflows and devices separately.");
        return 0;
    }

    private static boolean supportedVersion(Connection connection, PrintStream out) throws SQLException {
        try (PreparedStatement s = query(connection,"SELECT VERSION()",null); ResultSet rows=s.executeQuery()) {
            if (!rows.next()) throw new SQLException("Missing server version.");
            String version=rows.getString(1);
            Matcher match=Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})").matcher(version==null?"":version);
            if (!match.find() || version.toLowerCase(Locale.ROOT).contains("mariadb")) {
                out.println("FAIL: this schema targets MySQL 8.0.16 or newer; the server type/version is not supported.");
                return false;
            }
            int major=Integer.parseInt(match.group(1)),minor=Integer.parseInt(match.group(2)),patch=Integer.parseInt(match.group(3));
            out.println("MySQL version: " + major + "." + minor + "." + patch);
            if (major<8 || (major==8 && minor==0 && patch<16)) {
                out.println("FAIL: MySQL 8.0.16 or newer is required for the project's enforced constraints.");
                return false;
            }
            return true;
        }
    }

    private static PreparedStatement query(Connection connection,String sql,String schema) throws SQLException {
        PreparedStatement s=connection.prepareStatement(sql);
        try { s.setQueryTimeout(TIMEOUT); if(schema!=null)s.setString(1,schema); return s; }
        catch(SQLException|RuntimeException failure){try{s.close();}catch(SQLException close){failure.addSuppressed(close);}throw failure;}
    }

    private static String migration(String table) {
        return switch(table) {
            case "admin_audit","library_visit" -> "Apply sql/02-admin-support.sql in the existing database.";
            case "loan_renewal" -> "Apply sql/03-loan-renewals.sql in the existing database.";
            case "automatic_fine" -> "Apply sql/04-automatic-fines.sql in the existing database.";
            default -> "Verify the selected database and base BookIt schema; do not rerun sql/01-schema.sql over existing records.";
        };
    }

    private static Map<String,List<String>> columns() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        result.put("app_user",List.of("user_id","username","email","password_hash","full_name","phone","role","status","failed_login_attempts","locked_until","last_login_at","joined_at","updated_at"));
        result.put("user_qr_credential",List.of("user_id","token_hash","created_at","expires_at","revoked_at"));
        result.put("publisher",List.of("publisher_id","publisher_name","contact_email"));
        result.put("category",List.of("category_id","category_name"));
        result.put("author",List.of("author_id","author_name","bio"));
        result.put("book",List.of("book_id","isbn","title","publication_year","publisher_id","category_id"));
        result.put("book_author",List.of("book_id","author_id"));
        result.put("book_copy",List.of("copy_id","book_id","barcode","status","shelf_location"));
        result.put("loan",List.of("loan_id","copy_id","user_id","loaned_at","due_at","returned_at","active_copy_id"));
        result.put("reservation",List.of("reservation_id","book_id","user_id","reserved_at","status","active_book_id"));
        result.put("fine",List.of("fine_id","loan_id","amount","payment_status","paid_at"));
        result.put("admin_audit",List.of("audit_id","admin_id","action","target_type","target_id","details","created_at"));
        result.put("library_visit",List.of("visit_id","user_id","time_in","time_out","corrected_by","correction_reason","open_user_id"));
        result.put("loan_renewal",List.of("loan_id","user_id","request_id","old_due_at","new_due_at","renewed_at"));
        result.put("automatic_fine",List.of("fine_id","last_amount","charged_days","assessed_due_at","finalized_returned_at","calculated_at","manual_override"));
        return result;
    }
}

