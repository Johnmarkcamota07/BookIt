/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cumulative, idempotent assessments. Each loan and its audit commit atomically.
 * An administrator invokes this service on a worker thread; it never schedules itself. */
public class FineAutomationService {
    public record Result(int inspected, int created, int updated, int finalized,
                         int skipped, LocalDateTime completedAt) {}
    public record Change(boolean created, boolean updated, boolean finalized) {
        public boolean changed() { return created || updated || finalized; }
    }
    private static final Change SKIPPED = new Change(false, false, false);
    private final LibraryRepository.ConnectionProvider connections;
    public FineAutomationService() { this(DatabaseConnection::getConnection); }
    public FineAutomationService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public Result run(AuthenticatedUser actor) throws SQLException {
        actor(actor);
        List<Long> candidates = new ArrayList<>();
        try (Connection c = open()) {
            authorize(c, actor, false);
            // No LIMIT: an old unreturned loan cannot starve later overdue loans.
            try (PreparedStatement s = prepare(c, """
                    SELECT l.loan_id FROM loan l
                    LEFT JOIN fine f ON f.loan_id=l.loan_id
                    LEFT JOIN automatic_fine af ON af.fine_id=f.fine_id
                    WHERE l.due_at < UTC_TIMESTAMP(6)
                      AND (f.fine_id IS NULL OR (f.payment_status='UNPAID'
                        AND af.fine_id IS NOT NULL AND af.manual_override=0
                        AND af.finalized_returned_at IS NULL))
                    ORDER BY l.loan_id
                    """); ResultSet r = s.executeQuery()) {
                while (r.next()) candidates.add(id(r.getString(1), "Loan ID"));
            }
        }
        int inspected=0, created=0, updated=0, finalized=0, skipped=0;
        for (long loan : candidates) {
            if (Thread.currentThread().isInterrupted()) throw new SQLException("Fine reconciliation was interrupted.", "70100");
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    authorize(c, actor, true);
                    List<String> ids = row(c, """
                            SELECT bc.book_id,l.user_id,l.copy_id FROM loan l
                            JOIN book_copy bc ON bc.copy_id=l.copy_id WHERE l.loan_id=?
                            """, loan);
                    Change change = SKIPPED;
                    if (ids != null) {
                        long book=id(ids.get(0),"Book ID"), member=id(ids.get(1),"Member ID"), copy=id(ids.get(2),"Copy ID");
                        required(c,"SELECT book_id FROM book WHERE book_id=? FOR UPDATE",book);
                        // Disabled members still owe fines; this is a locking/existence check only.
                        required(c,"SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE",member);
                        required(c,"SELECT copy_id FROM book_copy WHERE copy_id=? AND book_id=? FOR UPDATE",copy,book);
                        List<String> locked = row(c,"SELECT user_id,copy_id FROM loan WHERE loan_id=? FOR UPDATE",loan);
                        if (locked != null) {
                            if (id(locked.get(0),"Member ID")!=member || id(locked.get(1),"Copy ID")!=copy) {
                                throw new SQLException("The loan changed while being reconciled. Refresh before retrying.");
                            }
                            change=assessLockedLoan(c,actor,loan);
                        }
                    }
                    c.commit();
                    inspected++;
                    if (change.created()) created++;
                    if (change.updated()) updated++;
                    if (change.finalized()) finalized++;
                    if (!change.changed()) skipped++;
                } catch (SQLException | RuntimeException failure) {
                    try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                    // Includes unknown commit outcomes: never replay inside this run.
                    throw failure;
                }
            }
        }
        LocalDateTime completed;
        try (Connection c=open()) {
            authorize(c,actor,false);
            List<String> now=required(c,"SELECT UTC_TIMESTAMP(6)");
            completed=date(now.get(0),"Server time",false);
        }
        return new Result(inspected,created,updated,finalized,skipped,completed);
    }

    /** For an already authorized ADMIN transaction holding the loan row lock.
     * Uses the caller's transaction; it never commits, rolls back, or opens a connection. */
    public static Change assessLockedLoan(Connection c, AuthenticatedUser actor, long loanId) throws SQLException {
        actor(actor);
        requireTransaction(c);
        validId(loanId,"Loan ID");
        List<String> loan=required(c,"SELECT due_at,returned_at,UTC_TIMESTAMP(6),loaned_at FROM loan WHERE loan_id=? FOR UPDATE",loanId);
        LocalDateTime due=date(loan.get(0),"Due date",false), returned=date(loan.get(1),"Return date",true), now=date(loan.get(2),"Server time",false);
        LocalDateTime borrowed=date(loan.get(3),"Borrowed date",false);
        if (due.isBefore(borrowed) || borrowed.isAfter(now) || (returned!=null && returned.isBefore(borrowed))) {
            throw new SQLException("The stored loan dates are inconsistent; review this loan manually.");
        }
        if (returned!=null && returned.isAfter(now)) throw new SQLException("The stored return date is in the future; review this loan manually.");
        AutomaticFineCalculator.Assessment assessed=AutomaticFineCalculator.assess(due,returned,now);
        List<String> fine=row(c,"SELECT fine_id,amount,payment_status FROM fine WHERE loan_id=? FOR UPDATE",loanId);
        if (fine==null) {
            if (assessed.days()==0) return SKIPPED;
            long fineId=insert(c,"INSERT INTO fine(loan_id,amount,payment_status) VALUES(?,?,'UNPAID')",loanId,assessed.amount());
            update(c,"""
                    INSERT INTO automatic_fine(fine_id,last_amount,charged_days,assessed_due_at,
                      finalized_returned_at,calculated_at,manual_override) VALUES(?,?,?,?,?,?,0)
                    """,fineId,assessed.amount(),assessed.days(),due,returned,now);
            audit(c,actor,"AUTO_FINE_CREATE",fineId,assessed,returned!=null);
            return new Change(true,false,returned!=null);
        }
        long fineId=id(fine.get(0),"Fine ID");
        BigDecimal current=amount(fine.get(1));
        if (!"UNPAID".equals(fine.get(2))) {
            if (!List.of("PAID","WAIVED").contains(fine.get(2))) throw new SQLException("Unsupported fine payment status.");
            return SKIPPED;
        }
        List<String> managed=metadata(c,fineId);
        if (managed==null || flag(managed.get(5))) return SKIPPED;
        BigDecimal previous=amount(managed.get(0));
        int days=days(managed.get(1));
        LocalDateTime oldDue=date(managed.get(2),"Assessed due date",false);
        LocalDateTime finalReturn=date(managed.get(3),"Final return date",true);
        date(managed.get(4),"Last assessment time",false);
        if (previous.compareTo(AutomaticFineCalculator.DAILY_RATE.multiply(BigDecimal.valueOf(days)))!=0) {
            throw new SQLException("Automatic-fine provenance has an inconsistent amount; review it before continuing.");
        }
        if (current.compareTo(previous)!=0) {
            update(c,"UPDATE automatic_fine SET manual_override=1,calculated_at=? WHERE fine_id=?",now,fineId);
            audit(c,actor,"AUTO_FINE_MANUAL_OVERRIDE",fineId,assessed,returned!=null,
                    "; observed="+current.toPlainString()+"; tracked="+previous.toPlainString()+"; amount preserved");
            return SKIPPED;
        }
        if (finalReturn!=null) {
            if (!finalReturn.equals(returned)) throw new SQLException("The finalized return date changed; review the fine manually.");
            return SKIPPED;
        }
        boolean amountChanged=current.compareTo(assessed.amount())!=0;
        boolean updated=amountChanged || days!=assessed.days() || !oldDue.equals(due);
        boolean finalized=returned!=null;
        if (!updated && !finalized) return SKIPPED;
        if (amountChanged) update(c,"UPDATE fine SET amount=? WHERE fine_id=? AND payment_status='UNPAID'",assessed.amount(),fineId);
        update(c,"""
                UPDATE automatic_fine SET last_amount=?,charged_days=?,assessed_due_at=?,
                  finalized_returned_at=?,calculated_at=? WHERE fine_id=? AND manual_override=0
                """,assessed.amount(),assessed.days(),due,returned,now,fineId);
        audit(c,actor,finalized?"AUTO_FINE_FINALIZE":"AUTO_FINE_UPDATE",fineId,assessed,finalized);
        return new Change(false,updated,finalized);
    }

    /** Caller must already hold the corresponding loan and fine row locks. */
    public static boolean isAutomaticLocked(Connection c,long fineId) throws SQLException {
        requireTransaction(c);
        validId(fineId,"Fine ID");
        return metadata(c,fineId)!=null;
    }

    /** An intentional administrator adjustment permanently opts this fine out. */
    public static void markManualOverride(Connection c,long fineId) throws SQLException {
        requireTransaction(c);
        validId(fineId,"Fine ID");
        List<String> managed=metadata(c,fineId);
        if (managed!=null && !flag(managed.get(5))) {
            update(c,"UPDATE automatic_fine SET manual_override=1,calculated_at=UTC_TIMESTAMP(6) WHERE fine_id=?",fineId);
        }
    }

    private static List<String> metadata(Connection c,long fine) throws SQLException {
        return row(c,"""
                SELECT last_amount,charged_days,assessed_due_at,finalized_returned_at,calculated_at,manual_override
                FROM automatic_fine WHERE fine_id=? FOR UPDATE
                """,fine);
    }
    private static void requireTransaction(Connection c) throws SQLException {
        if(c==null || c.getAutoCommit()) throw new SQLException("Automatic fine changes require the caller's explicit transaction and loan lock.");
    }
    private static void audit(Connection c,AuthenticatedUser actor,String action,long fine,
                              AutomaticFineCalculator.Assessment assessment,boolean ended) throws SQLException {
        audit(c,actor,action,fine,assessment,ended,"");
    }
    private static void audit(Connection c,AuthenticatedUser actor,String action,long fine,
                              AutomaticFineCalculator.Assessment assessment,boolean ended,String extra) throws SQLException {
        String details="PHP20/calendar day; Asia/Manila; grace=0; no cap; days="+assessment.days()
                +"; amount="+assessment.amount().toPlainString()+"; finalized="+ended+extra;
        update(c,"INSERT INTO admin_audit(admin_id,action,target_type,target_id,details,created_at) VALUES(?,?,'fine',?,?,UTC_TIMESTAMP())",
                id(actor.userId(),"Admin ID"),action,Long.toString(fine),details);
    }
    private static void actor(AuthenticatedUser actor) {
        if (actor==null || !"ADMIN".equals(actor.role()) || actor.username()==null || actor.username().isBlank()) {
            throw new IllegalArgumentException("An authenticated administrator is required.");
        }
        id(actor.userId(),"Admin ID");
    }
    private static void authorize(Connection c,AuthenticatedUser actor,boolean lock) throws SQLException {
        List<String> row=row(c,"SELECT role,status,username FROM app_user WHERE user_id=?"+(lock?" FOR UPDATE":""),id(actor.userId(),"Admin ID"));
        if (row==null || !"ADMIN".equals(row.get(0)) || !"ACTIVE".equals(row.get(1)) || !actor.username().equals(row.get(2))) {
            throw new IllegalArgumentException("Your administrator access is no longer active. Sign out and sign in again.");
        }
    }
    private Connection open() throws SQLException {
        Connection c=connections.open();
        if (c==null) throw new SQLException("No database connection was returned.");
        return c;
    }
    private static long id(String value,String label) {
        if (value==null || !value.matches("[0-9]{1,10}")) throw new IllegalArgumentException(label+" must be a positive numeric database ID.");
        long n=Long.parseLong(value); validId(n,label); return n;
    }
    private static void validId(long n,String label) {
        if(n<1 || n>4294967295L) throw new IllegalArgumentException(label+" is outside the supported range.");
    }
    private static BigDecimal amount(String value) throws SQLException {
        try {
            BigDecimal n=new BigDecimal(value).setScale(2,RoundingMode.UNNECESSARY);
            if(n.signum()<0 || n.compareTo(new BigDecimal("99999999.99"))>0) throw new ArithmeticException();
            return n;
        } catch(RuntimeException bad) { throw new SQLException("The stored fine amount is invalid."); }
    }
    private static int days(String value) throws SQLException {
        try { int n=Integer.parseInt(value); if(n<0) throw new NumberFormatException();return n; }
        catch(RuntimeException bad) { throw new SQLException("The stored charged-day count is invalid."); }
    }
    private static boolean flag(String value) throws SQLException {
        if("0".equals(value))return false;if("1".equals(value))return true;
        throw new SQLException("The stored automatic-fine override flag is invalid.");
    }
    private static LocalDateTime date(String value,String label,boolean nullable) throws SQLException {
        if(value==null || value.isEmpty()) { if(nullable)return null;throw new SQLException(label+" is missing."); }
        try { LocalDateTime d=LocalDateTime.parse(value.replace(' ','T'));AutomaticFineCalculator.valid(d,label);return d; }
        catch(DateTimeParseException | IllegalArgumentException bad) { throw new SQLException(label+" is invalid or out of range."); }
    }
    private static PreparedStatement prepare(Connection c,String sql,Object... params) throws SQLException {
        PreparedStatement s=c.prepareStatement(sql);
        try { bind(s,params);return s; }
        catch(SQLException | RuntimeException failure) { try{s.close();}catch(SQLException close){failure.addSuppressed(close);}throw failure; }
    }
    private static void bind(PreparedStatement s,Object... params) throws SQLException {
        s.setQueryTimeout(12);
        for(int i=0;i<params.length;i++)s.setObject(i+1,params[i]);
    }
    private static int update(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params)) {
            int changed=s.executeUpdate();
            if(changed!=1)throw new SQLException("The automatic-fine write did not affect exactly one record.");
            return changed;
        }
    }
    private static long insert(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(s,params);
            if(s.executeUpdate()!=1)throw new SQLException("The automatic fine could not be created.");
            try(ResultSet r=s.getGeneratedKeys()) {
                if(!r.next())throw new SQLException("The database did not return the fine ID.");
                return id(r.getString(1),"Fine ID");
            }
        }
    }
    private static List<String> required(Connection c,String sql,Object... params) throws SQLException {
        List<String> found=row(c,sql,params);
        if(found==null)throw new SQLException("A required library record was not found.");
        return found;
    }
    private static List<String> row(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params);ResultSet r=s.executeQuery()) {
            if(!r.next())return null;
            List<String> fields=new ArrayList<>();
            for(int i=1;i<=r.getMetaData().getColumnCount();i++)fields.add(r.getString(i));
            return fields;
        }
    }
}