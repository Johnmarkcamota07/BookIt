package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import com.group1.bookit.AdminData.Page;
import com.group1.bookit.AdminData.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** JDBC work runs on a worker thread. Each operation rechecks the administrator
 * in MySQL; mutations and their audit records commit together. */
public class AdminService {
    public enum Action {
        ADD_BOOK, EDIT_BOOK, ADD_COPY, UPDATE_COPY, CREATE_MEMBER, SET_MEMBER_STATUS,
        ISSUE_LOAN, RETURN_LOAN, SET_RESERVATION_STATUS, CHARGE_FINE, ADJUST_FINE,
        RECORD_PAYMENT, WAIVE_FINE
    }
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> BOOK_HEADERS = List.of("Book ID", "Title", "Authors", "ISBN", "Category", "Year", "Publisher", "Copies", "Available");
    private static final List<String> LOAN_HEADERS = List.of("Loan ID", "User ID", "Member", "Book", "Barcode", "Borrowed", "Due", "Returned", "Status");
    private static final List<String> MEMBER_HEADERS = List.of("User ID", "Username", "Full name", "Email", "Phone", "Role", "Status", "Open loans");
    private static final List<String> RESERVATION_HEADERS = List.of("Reservation ID", "User ID", "Member", "Book ID", "Book", "Requested", "Status", "Queue");
    private static final List<String> FINE_HEADERS = List.of("Fine ID", "Loan ID", "Member", "Book", "Amount", "Status", "Paid");
    private final LibraryRepository.ConnectionProvider connections;

    public AdminService() { this(DatabaseConnection::getConnection); }
    public AdminService(LibraryRepository.ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public Page load(AuthenticatedUser actor, String page) throws SQLException {
        requireActor(actor);
        if (!List.of("Overview", "Books & copies", "Members", "Borrowing & returns", "Reservations", "Fines").contains(page)) {
            throw new IllegalArgumentException("Unknown administration page.");
        }
        try (Connection c = open()) {
            authorize(c, actor, false);
            Map<String,String> m = new LinkedHashMap<>();
            Table table;
            switch (page) {
                case "Overview" -> {
                    m.put("Book titles", scalar(c, "SELECT COUNT(*) FROM book"));
                    m.put("Active members", scalar(c, "SELECT COUNT(*) FROM app_user WHERE role='USER' AND status='ACTIVE'"));
                    m.put("Open loans", scalar(c, "SELECT COUNT(*) FROM loan WHERE returned_at IS NULL"));
                    m.put("Overdue loans", scalar(c, "SELECT COUNT(*) FROM loan WHERE returned_at IS NULL AND due_at < UTC_TIMESTAMP()"));
                    table = loans(c, true);
                }
                case "Books & copies" -> {
                    table = books(c);
                    m.put("Book titles", Integer.toString(table.rows().size()));
                    m.put("Physical copies", scalar(c, "SELECT COUNT(*) FROM book_copy"));
                    m.put("Available copies", scalar(c, "SELECT COUNT(*) FROM book_copy bc WHERE bc.status='AVAILABLE' AND NOT EXISTS(SELECT 1 FROM loan l WHERE l.copy_id=bc.copy_id AND l.returned_at IS NULL)"));
                }
                case "Members" -> {
                    table = query(c, MEMBER_HEADERS, """
                            SELECT u.user_id,u.username,u.full_name,u.email,COALESCE(u.phone,''),u.role,u.status,
                              (SELECT COUNT(*) FROM loan l WHERE l.user_id=u.user_id AND l.returned_at IS NULL)
                            FROM app_user u ORDER BY u.full_name,u.user_id
                            """);
                    m.put("Accounts", Integer.toString(table.rows().size()));
                    m.put("Active members", scalar(c, "SELECT COUNT(*) FROM app_user WHERE role='USER' AND status='ACTIVE'"));
                    m.put("Disabled members", scalar(c, "SELECT COUNT(*) FROM app_user WHERE role='USER' AND status='DISABLED'"));
                }
                case "Borrowing & returns" -> {
                    table = loans(c, false);
                    m.put("Loan records", Integer.toString(table.rows().size()));
                    m.put("Open loans", scalar(c, "SELECT COUNT(*) FROM loan WHERE returned_at IS NULL"));
                    m.put("Overdue loans", scalar(c, "SELECT COUNT(*) FROM loan WHERE returned_at IS NULL AND due_at<UTC_TIMESTAMP()"));
                }
                case "Reservations" -> {
                    table = reservations(c);
                    m.put("Reservation records", Integer.toString(table.rows().size()));
                    m.put("Waiting", scalar(c, "SELECT COUNT(*) FROM reservation WHERE status='PENDING'"));
                    m.put("Ready", scalar(c, "SELECT COUNT(*) FROM reservation WHERE status='READY'"));
                }
                case "Fines" -> {
                    table = fines(c);
                    m.put("Fine records", Integer.toString(table.rows().size()));
                    m.put("Unpaid total", scalar(c, "SELECT COALESCE(SUM(amount),0) FROM fine WHERE payment_status='UNPAID'"));
                    m.put("Paid total", scalar(c, "SELECT COALESCE(SUM(amount),0) FROM fine WHERE payment_status='PAID'"));
                }
                default -> throw new IllegalArgumentException("Unknown administration page.");
            }
            return new Page(m, table);
        }
    }

    public Table copies(AuthenticatedUser actor, String bookId) throws SQLException {
        requireActor(actor);
        long id = id(bookId, "Book ID");
        try (Connection c = open()) {
            authorize(c, actor, false);
            return query(c, List.of("Copy ID", "Barcode", "Status", "Shelf", "Open loan"), """
                    SELECT bc.copy_id,bc.barcode,bc.status,COALESCE(bc.shelf_location,''),
                      COALESCE(CAST(l.loan_id AS CHAR),'') FROM book_copy bc
                    LEFT JOIN loan l ON l.copy_id=bc.copy_id AND l.returned_at IS NULL
                    WHERE bc.book_id=? ORDER BY bc.copy_id
                    """, id);
        }
    }

    /** Printable data for one physical copy; borrower details are never fetched. */
    public BookCopyLabel copyLabel(AuthenticatedUser actor, String copyId) throws SQLException {
        requireActor(actor);
        long copy = id(copyId, "Copy ID");
        try (Connection c = open()) {
            authorize(c, actor, false);
            try (PreparedStatement s = prepare(c, """
                    SELECT bc.copy_id,bc.book_id,b.title,bc.barcode,COALESCE(bc.shelf_location,'')
                    FROM book_copy bc JOIN book b ON b.book_id=bc.book_id WHERE bc.copy_id=?
                    """, copy); ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Copy not found.");
                return new BookCopyLabel(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5));
            }
        }
    }

    /** Resolves the exact stored barcode. This read does not issue or return a loan. */
    public ScannedBookCopy findCopyByBarcode(AuthenticatedUser actor, String barcode) throws SQLException {
        requireActor(actor);
        String exact = barcode(barcode, true);
        try (Connection c = open()) {
            authorize(c, actor, false);
            try (PreparedStatement s = prepare(c, """
                    SELECT bc.copy_id,bc.book_id,b.title,bc.barcode,bc.status,COALESCE(bc.shelf_location,''),
                      COALESCE(CAST(l.loan_id AS CHAR),''),COALESCE(u.full_name,'')
                    FROM book_copy bc JOIN book b ON b.book_id=bc.book_id
                    LEFT JOIN loan l ON l.copy_id=bc.copy_id AND l.returned_at IS NULL
                    LEFT JOIN app_user u ON u.user_id=l.user_id WHERE bc.barcode=?
                    """, exact); ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("No physical copy has that barcode.");
                return new ScannedBookCopy(r.getString(1),r.getString(2),r.getString(3),r.getString(4),
                        r.getString(5),r.getString(6),r.getString(7),r.getString(8));
            }
        }
    }

    /** Inventory and fines are CURRENT snapshots, not filtered by dates. The
     * original fine schema has no creation date; do not invent one in reports. */
    public Table report(AuthenticatedUser actor, String type, LocalDate from, LocalDate to) throws SQLException {
        requireActor(actor);
        if (!List.of("Borrowing activity", "Inventory snapshot", "Fine records").contains(type)) {
            throw new IllegalArgumentException("Unknown report type.");
        }
        if ("Borrowing activity".equals(type) && (from == null || to == null || from.isAfter(to))) {
            throw new IllegalArgumentException("Select a valid inclusive date range.");
        }
        if ("Borrowing activity".equals(type) && (from.getYear()<1000 || to.getYear()>9998)) {
            throw new IllegalArgumentException("Report dates must be between years 1000 and 9998.");
        }
        try (Connection c = open()) {
            authorize(c, actor, false);
            if ("Inventory snapshot".equals(type)) return books(c);
            if ("Fine records".equals(type)) return fines(c);
            LocalDateTime start = utc(from.atStartOfDay());
            LocalDateTime end = utc(to.plusDays(1).atStartOfDay());
            Table raw = query(c, List.of("Event", "Time", "Loan ID", "Member", "Book", "Barcode"), """
                    SELECT e.event,e.event_at,e.loan_id,u.full_name,b.title,bc.barcode FROM (
                      SELECT 'BORROWED' event,loaned_at event_at,loan_id,user_id,copy_id FROM loan WHERE loaned_at>=? AND loaned_at<?
                      UNION ALL
                      SELECT 'RETURNED',returned_at,loan_id,user_id,copy_id FROM loan WHERE returned_at>=? AND returned_at<?
                    ) e JOIN app_user u ON u.user_id=e.user_id JOIN book_copy bc ON bc.copy_id=e.copy_id
                    JOIN book b ON b.book_id=bc.book_id ORDER BY e.event_at DESC,e.loan_id DESC
                    """, start,end,start,end);
            return dates(raw, 1);
        }
    }

    public String execute(AuthenticatedUser actor, Action action, Map<String,String> values) throws SQLException {
        requireActor(actor);
        Objects.requireNonNull(action, "action");
        Map<String,String> v = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));
        validate(action,v);
        // The caller is a worker. Hash before acquiring database locks.
        String hash = null;
        if (action == Action.CREATE_MEMBER) {
            char[] secret = v.remove("password").toCharArray();
            try { hash = PasswordHasher.hash(secret); }
            finally { Arrays.fill(secret, '\0'); }
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                authorize(c,actor,true);
                String result = switch(action) {
                    case ADD_BOOK, EDIT_BOOK -> saveBook(c,v,action==Action.EDIT_BOOK);
                    case ADD_COPY -> addCopy(c,v);
                    case UPDATE_COPY -> updateCopy(c,v);
                    case CREATE_MEMBER -> createMember(c,v,hash);
                    case SET_MEMBER_STATUS -> memberStatus(c,v,actor);
                    case ISSUE_LOAN -> issue(c,v);
                    case RETURN_LOAN -> returnLoan(c,v);
                    case SET_RESERVATION_STATUS -> reservationStatus(c,v);
                    case CHARGE_FINE -> charge(c,v);
                    case ADJUST_FINE, RECORD_PAYMENT, WAIVE_FINE -> changeFine(c,v,action);
                };
                // Migration 02 is required: a missing audit table rolls back the
                // main operation too; unaudited writes are never committed.
                String target = switch(action) {
                    case ADD_BOOK,EDIT_BOOK -> "book";
                    case ADD_COPY,UPDATE_COPY -> "book_copy";
                    case CREATE_MEMBER,SET_MEMBER_STATUS -> "app_user";
                    case ISSUE_LOAN,RETURN_LOAN -> "loan";
                    case SET_RESERVATION_STATUS -> "reservation";
                    default -> "fine";
                };
                String details = "Performed in BookIt admin";
                if (v.containsKey("reason")) details = text(v,"reason",500,true);
                else if (v.containsKey("status")) details = "Status: " + v.get("status");
                else if (v.containsKey("amount")) details = "Amount: " + money(v).toPlainString();
                update(c,"INSERT INTO admin_audit(admin_id,action,target_type,target_id,details,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP())",
                        id(actor.userId(),"Admin ID"),action.name(),target,result,details);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch(SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
    private static Table books(Connection c) throws SQLException {
        Table raw = query(c,BOOK_HEADERS,"""
                SELECT b.book_id,b.title,'' authors,COALESCE(b.isbn,''),COALESCE(cat.category_name,''),
                  COALESCE(CAST(b.publication_year AS CHAR),''),COALESCE(p.publisher_name,''),
                  (SELECT COUNT(*) FROM book_copy bc WHERE bc.book_id=b.book_id),
                  (SELECT COUNT(*) FROM book_copy bc WHERE bc.book_id=b.book_id AND bc.status='AVAILABLE'
                    AND NOT EXISTS(SELECT 1 FROM loan l WHERE l.copy_id=bc.copy_id AND l.returned_at IS NULL))
                FROM book b LEFT JOIN category cat ON cat.category_id=b.category_id
                LEFT JOIN publisher p ON p.publisher_id=b.publisher_id ORDER BY b.title,b.book_id
                """);
        // Fetch authors independently so copy counts are not multiplied and
        // GROUP_CONCAT cannot silently truncate long author lists.
        Map<String,List<String>> authors = new LinkedHashMap<>();
        try (PreparedStatement s = prepare(c,"SELECT ba.book_id,a.author_name FROM book_author ba JOIN author a ON a.author_id=ba.author_id ORDER BY ba.book_id,a.author_name,a.author_id"); ResultSet r = s.executeQuery()) {
            while(r.next()) authors.computeIfAbsent(r.getString(1), ignored -> new ArrayList<>()).add(r.getString(2));
        }
        List<List<String>> rows = new ArrayList<>();
        for(List<String> source:raw.rows()) {
            List<String> row = new ArrayList<>(source);
            row.set(2,String.join("; ",authors.getOrDefault(row.get(0),List.of())));
            rows.add(row);
        }
        return new Table(BOOK_HEADERS,rows);
    }
    private static Table loans(Connection c, boolean overdueOnly) throws SQLException {
        return dates(query(c,LOAN_HEADERS,"""
                SELECT l.loan_id,l.user_id,u.full_name,b.title,bc.barcode,l.loaned_at,l.due_at,l.returned_at,
                  CASE WHEN l.returned_at IS NOT NULL THEN 'Returned' WHEN l.due_at<UTC_TIMESTAMP() THEN 'Overdue' ELSE 'Borrowed' END
                FROM loan l JOIN app_user u ON u.user_id=l.user_id JOIN book_copy bc ON bc.copy_id=l.copy_id
                JOIN book b ON b.book_id=bc.book_id
                """ + (overdueOnly?" WHERE l.returned_at IS NULL AND l.due_at<UTC_TIMESTAMP()":"") + " ORDER BY l.loaned_at DESC,l.loan_id DESC"),5,6,7);
    }
    private static Table reservations(Connection c) throws SQLException {
        return dates(query(c,RESERVATION_HEADERS,"""
                SELECT r.reservation_id,r.user_id,u.full_name,r.book_id,b.title,r.reserved_at,r.status,
                  CASE WHEN r.status IN ('PENDING','READY') THEN CAST((SELECT COUNT(*) FROM reservation q
                    WHERE q.book_id=r.book_id AND q.status IN ('PENDING','READY') AND
                    (q.reserved_at<r.reserved_at OR(q.reserved_at=r.reserved_at AND q.reservation_id<=r.reservation_id))) AS CHAR) ELSE '' END
                FROM reservation r JOIN app_user u ON u.user_id=r.user_id JOIN book b ON b.book_id=r.book_id
                ORDER BY r.reserved_at,r.reservation_id
                """),5);
    }
    private static Table fines(Connection c) throws SQLException {
        return dates(query(c,FINE_HEADERS,"""
                SELECT f.fine_id,f.loan_id,u.full_name,b.title,f.amount,f.payment_status,f.paid_at
                FROM fine f JOIN loan l ON l.loan_id=f.loan_id JOIN app_user u ON u.user_id=l.user_id
                JOIN book_copy bc ON bc.copy_id=l.copy_id JOIN book b ON b.book_id=bc.book_id ORDER BY f.fine_id DESC
                """),6);
    }
    private static String saveBook(Connection c,Map<String,String> v,boolean edit) throws SQLException {
        Long book = edit?id(v.get("bookId"),"Book ID"):null;
        if(edit) lockBook(c,book);
        Long category = named(c,"category","category_id","category_name",text(v,"category",100,false));
        Long publisher = named(c,"publisher","publisher_id","publisher_name",text(v,"publisher",150,false));
        String title=text(v,"title",255,true), isbn=text(v,"isbn",20,false), year=text(v,"year",4,false);
        if(edit) update(c,"UPDATE book SET title=?,isbn=?,publication_year=?,category_id=?,publisher_id=? WHERE book_id=?",
                title,blankNull(isbn),year.isEmpty()?null:Integer.valueOf(year),category,publisher,book);
        else book=insert(c,"INSERT INTO book(title,isbn,publication_year,category_id,publisher_id) VALUES(?,?,?,?,?)",
                title,blankNull(isbn),year.isEmpty()?null:Integer.valueOf(year),category,publisher);
        if(edit) update(c,"DELETE FROM book_author WHERE book_id=?",book);
        var linkedAuthors=new HashSet<Long>();
        for(String author:authorNames(v)) {
            Long authorId=named(c,"author","author_id","author_name",author);
            // Database collation can equate names beyond Java's lowercase rules.
            if(linkedAuthors.add(authorId)) update(c,"INSERT INTO book_author(book_id,author_id) VALUES(?,?)",book,authorId);
        }
        return Long.toString(book);
    }
    private static String addCopy(Connection c,Map<String,String> v) throws SQLException {
        long book=id(v.get("bookId"),"Book ID");
        lockBook(c,book);
        String supplied=barcode(v.get("barcode"),false);
        boolean automatic=supplied.isEmpty();
        // The temporary value is private to this transaction. Uniqueness is
        // enforced by uq_copy_barcode for both provisional and final values.
        String initial=automatic?"NEW-"+UUID.randomUUID():supplied;
        long copy=insert(c,"INSERT INTO book_copy(book_id,barcode,status,shelf_location) VALUES(?,?,?,?)",
                book,initial,v.get("status"),blankNull(text(v,"shelf",50,false)));
        if(automatic) {
            if(copy<1 || copy>4294967295L) throw new SQLException("The database returned an invalid physical copy ID.");
            String assigned=String.format(Locale.ROOT,"BITC-%010d",copy);
            if(update(c,"UPDATE book_copy SET barcode=? WHERE copy_id=?",assigned,copy)!=1) {
                throw new SQLException("The new physical copy barcode could not be assigned.");
            }
        }
        return Long.toString(copy);
    }
    private static String updateCopy(Connection c,Map<String,String> v) throws SQLException {
        long copy=id(v.get("copyId"),"Copy ID");
        long book=singleId(c,"SELECT book_id FROM book_copy WHERE copy_id=?",copy,"Copy not found.");
        lockBook(c,book);
        row(c,"SELECT copy_id FROM book_copy WHERE copy_id=? FOR UPDATE",copy,"Copy not found.");
        if(exists(c,"SELECT loan_id FROM loan WHERE copy_id=? AND returned_at IS NULL FOR UPDATE",copy)) {
            throw new IllegalArgumentException("Return this copy's open loan before changing its physical status or barcode.");
        }
        update(c,"UPDATE book_copy SET barcode=?,status=?,shelf_location=? WHERE copy_id=?",
                barcode(v.get("barcode"),true),v.get("status"),blankNull(text(v,"shelf",50,false)),copy);
        return Long.toString(copy);
    }
    private static String createMember(Connection c,Map<String,String> v,String hash) throws SQLException {
        return Long.toString(insert(c,"INSERT INTO app_user(username,email,password_hash,full_name,phone,role,status,joined_at,updated_at) VALUES(?,?,?,?,?,'USER','ACTIVE',UTC_TIMESTAMP(),UTC_TIMESTAMP())",
                text(v,"username",64,true),text(v,"email",255,true),hash,text(v,"fullName",150,true),blankNull(text(v,"phone",30,false))));
    }
    private static String memberStatus(Connection c,Map<String,String> v,AuthenticatedUser actor) throws SQLException {
        long user=id(v.get("userId"),"User ID");
        if(user==id(actor.userId(),"Admin ID")) throw new IllegalArgumentException("You cannot change your own account here.");
        List<String> record=row(c,"SELECT role,status FROM app_user WHERE user_id=? FOR UPDATE",user,"Member not found.");
        if(!"USER".equals(record.get(0))) throw new IllegalArgumentException("Only regular members can be enabled or disabled here.");
        update(c,"UPDATE app_user SET status=?,updated_at=UTC_TIMESTAMP() WHERE user_id=?",v.get("status"),user);
        return Long.toString(user);
    }
    private static String issue(Connection c,Map<String,String> v) throws SQLException {
        String barcode=text(v,"barcode",50,true);
        long user=id(v.get("userId"),"User ID");
        List<String> info=row(c,"SELECT copy_id,book_id FROM book_copy WHERE barcode=?",barcode,"No copy has this barcode.");
        long copy=id(info.get(0),"Copy ID"), book=id(info.get(1),"Book ID");
        lockBook(c,book);
        List<String> member=row(c,"SELECT role,status FROM app_user WHERE user_id=? FOR UPDATE",user,"Member not found.");
        if(!member.equals(List.of("USER","ACTIVE"))) throw new IllegalArgumentException("Loans require an active regular member.");
        List<String> physical=row(c,"SELECT status FROM book_copy WHERE copy_id=? AND barcode=? FOR UPDATE",List.of(copy,barcode),"The copy changed; refresh and retry.");
        if(!"AVAILABLE".equals(physical.get(0)) || exists(c,"SELECT loan_id FROM loan WHERE copy_id=? AND returned_at IS NULL FOR UPDATE",copy)) {
            throw new IllegalArgumentException("This copy is not available for borrowing.");
        }
        List<String> head=optionalRow(c,"SELECT reservation_id,user_id FROM reservation WHERE book_id=? AND status IN ('PENDING','READY') ORDER BY reserved_at,reservation_id LIMIT 1 FOR UPDATE",book);
        if(head!=null && !head.get(1).equals(Long.toString(user))) throw new IllegalArgumentException("This title is reserved for the member at the front of its queue.");
        LocalDate due=LocalDate.parse(v.get("dueDate"));
        LocalDateTime dueUtc=utc(due.atTime(23,59,59));
        if(!dueUtc.isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw new IllegalArgumentException("The due date must be in the future.");
        long loan=insert(c,"INSERT INTO loan(copy_id,user_id,loaned_at,due_at) VALUES(?,?,UTC_TIMESTAMP(),?)",copy,user,dueUtc);
        if(head!=null) update(c,"UPDATE reservation SET status='FULFILLED' WHERE reservation_id=?",id(head.get(0),"Reservation ID"));
        return Long.toString(loan);
    }
    private static String returnLoan(Connection c,Map<String,String> v) throws SQLException {
        long loan=id(v.get("loanId"),"Loan ID");
        List<String> info=row(c,"SELECT l.copy_id,bc.book_id FROM loan l JOIN book_copy bc ON bc.copy_id=l.copy_id WHERE l.loan_id=?",loan,"Loan not found.");
        lockBook(c,id(info.get(1),"Book ID"));
        row(c,"SELECT copy_id FROM book_copy WHERE copy_id=? FOR UPDATE",id(info.get(0),"Copy ID"),"Copy not found.");
        List<String> current=row(c,"SELECT returned_at FROM loan WHERE loan_id=? FOR UPDATE",loan,"Loan not found.");
        if(!current.get(0).isEmpty()) throw new IllegalArgumentException("This loan has already been returned.");
        update(c,"UPDATE loan SET returned_at=UTC_TIMESTAMP() WHERE loan_id=? AND returned_at IS NULL",loan);
        // Physical status is preserved. ON_LOAN is derived, not stored.
        return Long.toString(loan);
    }
    private static String reservationStatus(Connection c,Map<String,String> v) throws SQLException {
        long reservation=id(v.get("reservationId"),"Reservation ID");
        long book=singleId(c,"SELECT book_id FROM reservation WHERE reservation_id=?",reservation,"Reservation not found.");
        lockBook(c,book);
        List<String> record=row(c,"SELECT status FROM reservation WHERE reservation_id=? FOR UPDATE",reservation,"Reservation not found.");
        if(!List.of("PENDING","READY").contains(record.get(0))) throw new IllegalArgumentException("Only active reservations may be changed.");
        if("READY".equals(v.get("status"))) {
            long first=singleId(c,"SELECT reservation_id FROM reservation WHERE book_id=? AND status IN ('PENDING','READY') ORDER BY reserved_at,reservation_id LIMIT 1 FOR UPDATE",book,"No active reservation.");
            if(first!=reservation) throw new IllegalArgumentException("Only the first reservation in the queue can be marked ready.");
            boolean available=false;
            // Use current locking reads for BOTH copies and open loans. A
            // repeatable-read snapshot taken before the book lock is stale.
            Table candidates=query(c,List.of("Copy ID"),"SELECT copy_id FROM book_copy WHERE book_id=? AND status='AVAILABLE' ORDER BY copy_id FOR UPDATE",book);
            for(List<String> candidate:candidates.rows()) {
                if(!exists(c,"SELECT loan_id FROM loan WHERE copy_id=? AND returned_at IS NULL FOR UPDATE",id(candidate.get(0),"Copy ID"))) {available=true;break;}
            }
            if(!available) {
                throw new IllegalArgumentException("No available physical copy exists for this title.");
            }
        }
        update(c,"UPDATE reservation SET status=? WHERE reservation_id=?",v.get("status"),reservation);
        return Long.toString(reservation);
    }
    private static String charge(Connection c,Map<String,String> v) throws SQLException {
        long loan=id(v.get("loanId"),"Loan ID");
        row(c,"SELECT loan_id FROM loan WHERE loan_id=? FOR UPDATE",loan,"Loan not found.");
        if(exists(c,"SELECT fine_id FROM fine WHERE loan_id=? FOR UPDATE",loan)) throw new IllegalArgumentException("A fine already exists for this loan; adjust its unpaid amount instead.");
        return Long.toString(insert(c,"INSERT INTO fine(loan_id,amount,payment_status) VALUES(?,?,'UNPAID')",loan,money(v)));
    }
    private static String changeFine(Connection c,Map<String,String> v,Action action) throws SQLException {
        long fine=id(v.get("fineId"),"Fine ID");
        List<String> record=row(c,"SELECT payment_status FROM fine WHERE fine_id=? FOR UPDATE",fine,"Fine not found.");
        if(!"UNPAID".equals(record.get(0))) throw new IllegalArgumentException("Only unpaid fines may be changed. Paid and waived records are retained.");
        switch(action) {
            case ADJUST_FINE -> update(c,"UPDATE fine SET amount=? WHERE fine_id=?",money(v),fine);
            case RECORD_PAYMENT -> update(c,"UPDATE fine SET payment_status='PAID',paid_at=UTC_TIMESTAMP() WHERE fine_id=?",fine);
            case WAIVE_FINE -> update(c,"UPDATE fine SET payment_status='WAIVED',paid_at=NULL WHERE fine_id=?",fine);
            default -> throw new IllegalArgumentException("Unknown fine action.");
        }
        return Long.toString(fine);
    }
    private static void validate(Action action,Map<String,String> v) {
        switch(action) {
            case ADD_BOOK,EDIT_BOOK -> {
                if(action==Action.EDIT_BOOK) id(v.get("bookId"),"Book ID");
                text(v,"title",255,true); text(v,"isbn",20,false); text(v,"category",100,false); text(v,"publisher",150,false);
                String year=text(v,"year",4,false);
                if(!year.isEmpty() && !year.matches("[1-9][0-9]{3}")) throw new IllegalArgumentException("Year must be blank or a four-digit year from 1000 to 9999.");
                authorNames(v);
            }
            case ADD_COPY,UPDATE_COPY -> {
                id(v.get(action==Action.ADD_COPY?"bookId":"copyId"),action==Action.ADD_COPY?"Book ID":"Copy ID");
                barcode(v.get("barcode"),action==Action.UPDATE_COPY);
                text(v,"shelf",50,false);
                choice(v,"status","AVAILABLE","MAINTENANCE","LOST","WITHDRAWN");
            }
            case CREATE_MEMBER -> {
                text(v,"username",64,true); String email=text(v,"email",255,true); text(v,"fullName",150,true); text(v,"phone",30,false);
                if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email address.");
                String password=v.get("password");
                if(password==null || password.length()<15 || password.length()>1024) throw new IllegalArgumentException("Use a password of 15 to 1024 characters.");
            }
            case SET_MEMBER_STATUS -> { id(v.get("userId"),"User ID"); choice(v,"status","ACTIVE","DISABLED"); }
            case ISSUE_LOAN -> {
                id(v.get("userId"),"User ID"); text(v,"barcode",50,true);
                try {
                    LocalDate due=LocalDate.parse(v.getOrDefault("dueDate",""));
                    if(due.isBefore(LocalDate.now())) throw new IllegalArgumentException("Due date must be today or later.");
                    if(due.getYear()>9999) throw new IllegalArgumentException("Due date must be within MySQL's supported calendar range.");
                } catch(java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Select a valid due date."); }
            }
            case RETURN_LOAN -> id(v.get("loanId"),"Loan ID");
            case SET_RESERVATION_STATUS -> { id(v.get("reservationId"),"Reservation ID"); choice(v,"status","READY","CANCELLED"); }
            case CHARGE_FINE -> { id(v.get("loanId"),"Loan ID"); money(v); text(v,"reason",500,true); }
            case ADJUST_FINE -> { id(v.get("fineId"),"Fine ID"); money(v); text(v,"reason",500,true); }
            case RECORD_PAYMENT -> id(v.get("fineId"),"Fine ID");
            case WAIVE_FINE -> { id(v.get("fineId"),"Fine ID"); text(v,"reason",500,true); }
        }
    }
    private static BigDecimal money(Map<String,String> v) {
        try {
            BigDecimal amount=new BigDecimal(text(v,"amount",20,true)).setScale(2,RoundingMode.UNNECESSARY);
            if(amount.signum()<0 || amount.compareTo(new BigDecimal("99999999.99"))>0) throw new NumberFormatException();
            return amount;
        } catch(ArithmeticException|NumberFormatException e) { throw new IllegalArgumentException("Amount must be 0 to 99999999.99 with at most two decimal places."); }
    }
    private static List<String> authorNames(Map<String,String> v) {
        String names=text(v,"authors",4000,false);
        Map<String,String> unique=new LinkedHashMap<>();
        for(String part:names.split(";")) {
            String author=part.strip();
            if(author.codePointCount(0,author.length())>150) throw new IllegalArgumentException("Each author name must be at most 150 characters; separate authors with semicolons.");
            if(!author.isEmpty()) unique.putIfAbsent(author.toLowerCase(Locale.ROOT),author);
        }
        return List.copyOf(unique.values());
    }
    private static void choice(Map<String,String> v,String key,String... options) {
        if(!Arrays.asList(options).contains(v.get(key))) throw new IllegalArgumentException("Choose a supported "+key+".");
    }
    private static String text(Map<String,String> v,String key,int max,boolean required) {
        String value=v.getOrDefault(key,"");
        value=value==null?"":value.strip();
        if((required && value.isEmpty()) || value.codePointCount(0,value.length())>max || value.indexOf('\0')>=0) throw new IllegalArgumentException(key+" is required and/or must be at most "+max+" characters.");
        return value;
    }
    private static String blankNull(String v) { return v.isEmpty()?null:v; }
    private static String barcode(String value,boolean required) {
        if(!required && (value==null || value.isBlank())) return "";
        if(value==null || value.length()>50 || !value.matches("[\\x21-\\x7E]+")) {
            throw new IllegalArgumentException("Barcode must be 1 to 50 printable ASCII characters without spaces.");
        }
        return value;
    }
    private static long id(String value,String label) {
        if(value==null || !value.matches("[0-9]{1,10}")) throw new IllegalArgumentException(label+" must be a positive numeric database ID.");
        long id=Long.parseLong(value);
        if(id<1 || id>4294967295L) throw new IllegalArgumentException(label+" is outside the supported range.");
        return id;
    }
    private static void requireActor(AuthenticatedUser actor) {
        if(actor==null || !"ADMIN".equals(actor.role())) throw new IllegalArgumentException("An authenticated administrator is required.");
        id(actor.userId(),"Admin ID");
    }
    private static void authorize(Connection c,AuthenticatedUser actor,boolean lock) throws SQLException {
        try(PreparedStatement s=prepare(c,"SELECT role,status FROM app_user WHERE user_id=?"+(lock?" FOR UPDATE":""),id(actor.userId(),"Admin ID")); ResultSet r=s.executeQuery()) {
            if(!r.next() || !"ADMIN".equals(r.getString(1)) || !"ACTIVE".equals(r.getString(2))) throw new IllegalArgumentException("Your administrator access is no longer active. Sign out and contact an administrator.");
        }
    }
    private Connection open() throws SQLException {
        Connection c=connections.open();
        if(c==null) throw new SQLException("No database connection was returned.");
        return c;
    }
    private static void lockBook(Connection c,long book) throws SQLException {
        row(c,"SELECT book_id FROM book WHERE book_id=? FOR UPDATE",book,"Book not found.");
    }
    // SQL identifier inputs are fixed constants in this class, never form text.
    private static Long named(Connection c,String table,String pk,String nameColumn,String value) throws SQLException {
        if(value.isEmpty()) return null;
        List<String> found=optionalRow(c,"SELECT "+pk+" FROM "+table+" WHERE "+nameColumn+"=? ORDER BY "+pk+" LIMIT 1 FOR UPDATE",value);
        if(found!=null) return id(found.get(0),"Metadata ID");
        if(table.equals("category")) {
            return insert(c,"INSERT INTO category(category_name) VALUES(?) ON DUPLICATE KEY UPDATE category_id=LAST_INSERT_ID(category_id)",value);
        }
        return insert(c,"INSERT INTO "+table+"("+nameColumn+") VALUES(?)",value);
    }
    private static PreparedStatement prepare(Connection c,String sql,Object... params) throws SQLException {
        PreparedStatement s=c.prepareStatement(sql);
        try { bind(s,params); return s; } catch(SQLException|RuntimeException failure) { try{s.close();}catch(SQLException close){failure.addSuppressed(close);} throw failure; }
    }
    private static void bind(PreparedStatement s,Object... params) throws SQLException {
        s.setQueryTimeout(12);
        for(int i=0;i<params.length;i++) s.setObject(i+1,params[i]);
    }
    private static int update(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params)) { return s.executeUpdate(); }
    }
    private static long insert(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(s,params);
            if(s.executeUpdate()<1) throw new SQLException("The operation did not create a record.");
            try(ResultSet keys=s.getGeneratedKeys()) {
                if(!keys.next()) throw new SQLException("The database did not return the new record ID.");
                return keys.getLong(1);
            }
        }
    }
    private static Table query(Connection c,List<String> headers,String sql,Object... params) throws SQLException {
        List<List<String>> rows=new ArrayList<>();
        try(PreparedStatement s=prepare(c,sql,params); ResultSet r=s.executeQuery()) {
            while(r.next()) {
                List<String> row=new ArrayList<>();
                for(int i=1;i<=headers.size();i++) { String value=r.getString(i); row.add(value==null?"":value); }
                rows.add(row);
            }
        }
        return new Table(headers,rows);
    }
    private static Table dates(Table source,int... columns) {
        List<List<String>> rows=new ArrayList<>();
        for(List<String> original:source.rows()) {
            List<String> row=new ArrayList<>(original);
            for(int column:columns) {
                String value=row.get(column);
                if(!value.isEmpty()) {
                    LocalDateTime stored=LocalDateTime.parse(value.replace(' ','T'));
                    row.set(column,DISPLAY.format(stored.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault())));
                }
            }
            rows.add(row);
        }
        return new Table(source.headers(),rows);
    }
    private static LocalDateTime utc(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
    private static String scalar(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params); ResultSet r=s.executeQuery()) { return r.next()?r.getString(1):"0"; }
    }
    private static boolean exists(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params); ResultSet r=s.executeQuery()) { return r.next(); }
    }
    private static long singleId(Connection c,String sql,Object param,String message) throws SQLException {
        return id(row(c,sql,param,message).get(0),"Database ID");
    }
    private static List<String> row(Connection c,String sql,Object param,String message) throws SQLException {
        Object[] params=param instanceof List<?> list?list.toArray():new Object[]{param};
        List<String> row=optionalRow(c,sql,params);
        if(row==null) throw new IllegalArgumentException(message);
        return row;
    }
    private static List<String> optionalRow(Connection c,String sql,Object... params) throws SQLException {
        try(PreparedStatement s=prepare(c,sql,params); ResultSet r=s.executeQuery()) {
            if(!r.next()) return null;
            List<String> row=new ArrayList<>();
            int count=r.getMetaData().getColumnCount();
            for(int i=1;i<=count;i++) {String value=r.getString(i);row.add(value==null?"":value);}
            return row;
        }
    }
}
