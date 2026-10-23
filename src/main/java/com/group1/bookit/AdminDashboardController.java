package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import static com.group1.bookit.DashboardUi.*;
import static com.group1.bookit.AdminForms.*;
import static com.group1.bookit.AdminService.Action.*;

/** Admin workspace: records and actions use services, never fixed sample arrays. */
public final class AdminDashboardController implements DashboardView {
    @FXML private BorderPane root;
    @FXML private ImageView sidebarLogo;
    @FXML private VBox navigationBox;
    @FXML private VBox pageContent;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private Label accountName;
    @FXML private Label accountRole;
    @FXML private Label dataModeLabel;
    @FXML private ScrollPane contentScroll;

    private final AdminService service;
    private final AttendanceService attendanceService;
    private final FxDataLoader reads = new FxDataLoader();
    // Writes are deliberately independent from page-read cancellation.
    private final FxDataLoader writes = new FxDataLoader();
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final Map<String, Button> navigation = new LinkedHashMap<>();
    private AuthenticatedUser actor;
    private String currentPage = "Overview";
    private String copiesBookId;
    private String reportSelection = "Borrowing activity";
    private boolean disposed;
    private Window guardedWindow;
    private final javafx.event.EventHandler<WindowEvent> closingGuard = event -> {
        if (saving.get()) {
            event.consume();
            notice("Saving is in progress", "Wait for the result before closing BookIt. Do not retry a transaction while its result is unknown.");
        }
    };

    public AdminDashboardController() { this(new AdminService(), new AttendanceService()); }

    /** Controller-factory seam for offline tests; construction never opens a connection. */
    public AdminDashboardController(AdminService service, AttendanceService attendanceService) {
        this.service = Objects.requireNonNull(service);
        this.attendanceService = Objects.requireNonNull(attendanceService);
    }

    @FXML private void initialize() {
        LogoSupport.install(sidebarLogo);
        // Keep this property unbound: Navigation also disables the old root during
        // logout. A binding would make that safety step throw at runtime.
        saving.addListener((observable, before, busy) -> root.setDisable(busy));
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null) {
                scene.windowProperty().addListener((windowObservable, oldWindow, window) -> guardClosing(window));
                guardClosing(scene.getWindow());
            }
        });
        for (String page : List.of("Overview", "Books & copies", "Members", "Borrowing & returns",
                "Reservations", "Fines", "Library attendance", "Reports", "Account security")) {
            Button nav = button(page, "nav-button", () -> selectPage(page));
            nav.setMaxWidth(Double.MAX_VALUE);
            navigation.put(page, nav);
            navigationBox.getChildren().add(nav);
        }
        selectPage("Overview");
    }

    private void guardClosing(Window window) {
        if (guardedWindow == window) return;
        if (guardedWindow != null) guardedWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
        guardedWindow = window;
        if (!disposed && window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
    }

    @Override public void setUser(AuthenticatedUser user) {
        if (user == null || !"ADMIN".equals(user.role()))
            throw new IllegalArgumentException("The admin dashboard requires an ADMIN account.");
        if (saving.get()) throw new IllegalStateException("Wait for the current transaction before changing accounts.");
        if (disposed) throw new IllegalStateException("This dashboard has already closed.");
        reads.cancel();
        actor = user;
        accountName.setText(user.username());
        accountRole.setText("ADMIN WORKSPACE");
        selectPage(currentPage);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        guardClosing(null);
        reads.close();
        writes.close();
        actor = null;
        pageContent.getChildren().clear();
        accountName.setText("Signed out");
        pageTitle.setText("Signed out");
        pageSubtitle.setText("");
        setMode("NOT CONNECTED", "offline-badge");
        // Navigation and window-close controls are blocked while saving. Forced process
        // shutdown still requires checking the persisted state before any retry.
    }

    private void selectPage(String page) {
        if (saving.get() || disposed) return;
        reads.cancel();
        currentPage = page;
        copiesBookId = null;
        navigation.forEach((name, nav) -> {
            nav.getStyleClass().remove("nav-active");
            if (name.equals(page)) nav.getStyleClass().add("nav-active");
        });
        pageTitle.setText(page);
        pageSubtitle.setText("Live library records. Select a record to enable its available actions.");
        pageContent.getChildren().clear();
        contentScroll.setVvalue(0);
        if (actor == null) {
            setMode("NOT CONNECTED", "offline-badge");
            pageContent.getChildren().add(emptyState("Waiting for sign-in",
                    "Records load after the authenticated ADMIN account is supplied."));
        } else if ("Account security".equals(page)) accountSecurity();
        else if ("Reports".equals(page)) reports();
        else {
            AuthenticatedUser identity = actor;
            load("Loading " + page.toLowerCase() + "…",
                    () -> "Library attendance".equals(page) ? attendanceService.load(identity) : service.load(identity, page),
                    data -> renderPage(page, data));
        }
    }

    private void renderPage(String page, AdminData.Page data) {
        if (!data.metrics().isEmpty()) pageContent.getChildren().add(metrics(data.metrics()));
        Grid grid = grid(data.table(), "Search " + page.toLowerCase() + "…");
        FlowPane toolbar = toolbar(refreshButton());
        switch (page) {
            case "Overview" -> toolbar.getChildren().addAll(
                    button("Manage books", "primary-button", () -> selectPage("Books & copies")),
                    button("Issue / return books", "secondary-button", () -> selectPage("Borrowing & returns")),
                    button("View attendance", "secondary-button", () -> selectPage("Library attendance")));
            case "Books & copies" -> {
                toolbar.getChildren().add(button("Add book", "primary-button", () -> bookForm(null)));
                toolbar.getChildren().add(selected("Edit book", grid, ignored -> true, () -> bookForm(grid.selected())));
                toolbar.getChildren().add(selected("Manage copies", grid, ignored -> true,
                        () -> showCopies(grid.selected().get("Book ID"))));
                toolbar.getChildren().add(button("Find / scan copy", "secondary-button", () -> lookupBarcode(false)));
            }
            case "Members" -> {
                toolbar.getChildren().add(button("Create member", "primary-button", this::createMember));
                toolbar.getChildren().add(selected("Enable / disable member", grid,
                        row -> "USER".equals(row.get("Role")) && !actor.userId().equals(row.get("User ID")),
                        () -> memberStatus(grid.selected())));
            }
            case "Borrowing & returns" -> {
                toolbar.getChildren().add(button("Issue a book", "primary-button", this::issueLoan));
                toolbar.getChildren().add(button("Scan / type return", "secondary-button", () -> lookupBarcode(true)));
                toolbar.getChildren().add(selected("Record return", grid,
                        row -> !"Returned".equalsIgnoreCase(row.get("Status")) && absent(row.get("Returned")),
                        () -> confirmAction("Record return", "Record this copy as returned now? No fine is calculated automatically.",
                                RETURN_LOAN, Map.of("loanId", grid.selected().get("Loan ID")))));
            }
            case "Reservations" -> {
                toolbar.getChildren().add(selected("Mark ready", grid,
                        row -> "PENDING".equalsIgnoreCase(row.get("Status")), () -> reservation(grid.selected(), "READY")));
                toolbar.getChildren().add(selected("Cancel reservation", grid,
                        row -> List.of("PENDING", "READY").contains(row.get("Status").toUpperCase()),
                        () -> reservation(grid.selected(), "CANCELLED")));
            }
            case "Fines" -> {
                toolbar.getChildren().add(button("Charge fine", "primary-button", () -> fineForm(null)));
                toolbar.getChildren().add(selected("Adjust charge", grid, this::unpaid, () -> fineForm(grid.selected())));
                toolbar.getChildren().add(selected("Record payment", grid, this::unpaid,
                        () -> confirmAction("Record payment", "Confirm the full selected fine was received outside BookIt. This records payment; it does not charge a bank account.",
                                RECORD_PAYMENT, Map.of("fineId", grid.selected().get("Fine ID")))));
                toolbar.getChildren().add(selected("Waive fine", grid, this::unpaid, () -> waive(grid.selected())));
            }
            case "Library attendance" -> {
                toolbar.getChildren().add(button("Start entrance mode", "primary-button", () -> {
                    AttendanceKiosk.show(owner(), actor, attendanceService);
                    if (!disposed) selectPage("Library attendance");
                }));
                toolbar.getChildren().add(selected("Correct a visit", grid, ignored -> true, () -> correctVisit(grid.selected())));
                toolbar.getChildren().add(button("Attendance report", "secondary-button", () -> {
                    reportSelection = "Library attendance";
                    selectPage("Reports");
                }));
            }
            default -> throw new IllegalArgumentException("Unknown admin page.");
        }
        pageContent.getChildren().add(section(page, pageDescription(page), toolbar, grid.container()));
    }

    private void bookForm(Map<String, String> selected) {
        boolean edit = selected != null;
        Map<String, String> row = edit ? selected : Map.of();
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        if (edit) fields.add(id("bookId", "Book ID", row.get("Book ID")));
        fields.add(text("title", "Title", clean(row.get("Title")), true));
        fields.add(text("isbn", "ISBN (optional)", clean(row.get("ISBN")), false));
        fields.add(text("year", "Publication year (optional)", clean(row.get("Year")), false));
        fields.add(text("category", "Category name (optional)", clean(row.get("Category")), false));
        fields.add(text("authors", "Author names, separated by semicolons", clean(row.get("Authors")), false));
        fields.add(text("publisher", "Publisher name (optional)", clean(row.get("Publisher")), false));
        form(edit ? "Edit book" : "Add book", "Names match existing authors, categories and publishers, or create new ones. Physical copies are managed separately.",
                edit ? EDIT_BOOK : ADD_BOOK, fields.toArray(Field[]::new));
    }

    private void createMember() {
        form("Create member", "Creates a USER account. Give the initial password to the member privately; passwords and QR secrets are never displayed in the member list.",
                CREATE_MEMBER, text("username", "Username", "", true), password(),
                text("email", "Email", "", true), text("fullName", "Full name", "", true),
                text("phone", "Phone (optional)", "", false));
    }

    private void memberStatus(Map<String, String> row) {
        String status = "ACTIVE".equalsIgnoreCase(row.get("Status")) ? "DISABLED" : "ACTIVE";
        confirmAction("Change member access", "Set " + row.get("Username") + " to " + status
                + "? This does not delete their borrowing history.", SET_MEMBER_STATUS,
                Map.of("userId", row.get("User ID"), "status", status));
    }

    private void issueLoan() {
        form("Issue a book", "Enter the member ID. Focus the barcode field for a USB scanner, use Scan camera, or type the registered copy code. Review the form before Save; scanning alone never issues a loan. The due date is explicit; 14 days is only an editable starting value.",
                ISSUE_LOAN, text("userId", "Member user ID", "", true), barcode(),
                date("dueDate", "Due date (local library date)", LocalDate.now().plusDays(14)));
    }

    private void showBarcodeLabel(String copyId) {
        if (saving.get() || disposed || actor == null) return;
        AuthenticatedUser identity = actor;
        setMode("LOADING BARCODE LABEL…", "loading-badge");
        reads.load(() -> service.copyLabel(identity, copyId), data -> {
            setMode("DATABASE", "live-badge");
            BarcodeLabelDialog.show(owner(), data);
        }, error -> {
            logFailure(error);
            setMode("LABEL UNAVAILABLE", "offline-badge");
            notice("Barcode label unavailable", failureMessage(error));
        });
    }

    private void lookupBarcode(boolean returning) {
        if (saving.get() || disposed || actor == null) return;
        AdminForms.show(owner(), returning ? "Return by copy barcode" : "Find a registered copy",
                "Use the BookIt copy label, not the publisher ISBN. Scan camera, focus the input for a USB keyboard scanner, or type the code. Save looks up the copy; it does not change a loan.",
                barcode()).ifPresent(values -> {
            if (saving.get() || disposed || actor == null) return;
            AuthenticatedUser identity = actor;
            String scanned = values.get("barcode");
            setMode("FINDING COPY…", "loading-badge");
            reads.load(() -> service.findCopyByBarcode(identity, scanned), copy -> {
                setMode("DATABASE", "live-badge");
                if (returning) {
                    if (copy.openLoanId() == null || copy.openLoanId().isBlank()) {
                        notice("No open loan", "This registered copy has no unreturned loan. No return was recorded.");
                    } else {
                        confirmAction("Return scanned copy", "Return \"" + copy.title() + "\"?\nCopy: "
                                + copy.barcode() + "\nMember: " + copy.memberName()
                                + "\nLoan: " + copy.openLoanId()
                                + "\n\nConfirm the physical book has been received. No fine is calculated automatically.",
                                RETURN_LOAN, Map.of("loanId", copy.openLoanId()));
                    }
                } else {
                    detailDialog(root, "Registered book copy", page(
                            label(copy.title(), "section-title"),
                            label("Copy ID: " + copy.copyId() + " · Book ID: " + copy.bookId(), "muted"),
                            label("Barcode: " + copy.barcode(), "muted"),
                            label("Physical status: " + copy.status() + " · Shelf: " + copy.shelf(), "muted"),
                            label(copy.openLoanId() == null || copy.openLoanId().isBlank()
                                    ? "No open loan." : "Open loan: " + copy.openLoanId() + " · " + copy.memberName(), "muted")),
                            "Read-only lookup. No copy, loan, or return was changed.");
                }
            }, error -> {
                logFailure(error);
                setMode("COPY NOT FOUND / UNAVAILABLE", "offline-badge");
                notice("Copy lookup failed", failureMessage(error));
            });
        });
    }

    private void reservation(Map<String, String> row, String status) {
        confirmAction("Update reservation", "Set reservation " + row.get("Reservation ID") + " to " + status
                + "? Marking ready does not send an email or hold a specific copy.", SET_RESERVATION_STATUS,
                Map.of("reservationId", row.get("Reservation ID"), "status", status));
    }

    private boolean unpaid(Map<String, String> row) {
        return "UNPAID".equalsIgnoreCase(row.get("Status"));
    }

    private void fineForm(Map<String, String> row) {
        if (row == null) form("Charge fine", "Record a manual charge against an existing loan. BookIt does not calculate an automatic overdue fee.",
                CHARGE_FINE, text("loanId", "Loan ID", "", true), text("amount", "Amount (PHP)", "", true),
                text("reason", "Reason", "", true));
        else form("Adjust fine", "Adjust an unpaid charge. The entered reason is recorded with the change.", ADJUST_FINE,
                id("fineId", "Fine ID", row.get("Fine ID")), text("amount", "New amount (PHP)", clean(row.get("Amount")).replace("PHP", "").replace(",", "").strip(), true),
                text("reason", "Adjustment reason", "", true));
    }

    private void waive(Map<String, String> row) {
        AdminForms.show(owner(), "Waive fine", "The fine will be marked WAIVED and excluded from the unpaid balance. Its original amount and your reason are retained.",
                id("fineId", "Fine ID", row.get("Fine ID")), text("reason", "Waiver reason", "", true))
                .ifPresent(values -> confirmAction("Waive fine", "Confirm this fine should be waived, not paid?", WAIVE_FINE, values));
    }

    private void showCopies(String bookId) {
        if (saving.get() || disposed) return;
        reads.cancel();
        copiesBookId = bookId;
        pageTitle.setText("Copies · book " + bookId);
        pageSubtitle.setText("Register a copy to assign its barcode, then select it to preview, save, or print its label. An open loan prevents inventory changes.");
        AuthenticatedUser identity = actor;
        load("Loading physical copies…", () -> service.copies(identity, bookId), data -> {
            Grid grid = grid(data, "Search copy barcode, shelf, or status…");
            pageContent.getChildren().add(section("Physical copies", "Do not set a copy to borrowed manually: issue or return a loan instead.",
                    toolbar(button("Back to books", "secondary-button", () -> selectPage("Books & copies")),
                            button("Add copy", "primary-button", () -> copyForm(bookId, null)),
                            selected("Barcode label / print", grid, ignored -> true,
                                    () -> showBarcodeLabel(grid.selected().get("Copy ID"))),
                            selected("Update copy", grid, row -> absent(row.get("Open loan")) || "0".equals(row.get("Open loan")),
                                    () -> copyForm(bookId, grid.selected())), refreshButton()), grid.container()));
        });
    }

    private void copyForm(String bookId, Map<String, String> selected) {
        if (selected == null) form("Add physical copy", "BookIt assigns a unique copy barcode when this record is saved. Afterwards select the copy and choose Barcode label / print. You do not need an existing sticker or barcode to register it.",
                ADD_COPY, id("bookId", "Book ID", bookId),
                text("shelf", "Shelf location (optional)", "", false),
                choice("status", "Inventory status", "AVAILABLE", "AVAILABLE", "MAINTENANCE", "LOST", "WITHDRAWN"));
        else AdminForms.show(owner(), "Update physical copy", "The copy's barcode stays the same so existing labels continue to work. Only a copy without an open loan may change inventory status.",
                id("copyId", "Copy ID", selected.get("Copy ID")),
                id("barcode", "Copy barcode (unchanged)", selected.get("Barcode")),
                text("shelf", "Shelf location (optional)", clean(selected.get("Shelf")), false),
                choice("status", "Inventory status", selected.get("Status"), "AVAILABLE", "MAINTENANCE", "LOST", "WITHDRAWN"))
                .ifPresent(values -> {
                    if (List.of("LOST", "WITHDRAWN").contains(values.get("status"))) {
                        confirmAction("Update copy", "Set this copy to " + values.get("status") + "? It will no longer be available for borrowing.", UPDATE_COPY, values);
                    } else {
                        AuthenticatedUser identity = actor;
                        mutate("Update copy", () -> service.execute(identity, UPDATE_COPY, values));
                    }
                });
    }

    private void correctVisit(Map<String, String> row) {
        AdminForms.show(owner(), "Correct attendance visit", "Enter local times in " + ZoneId.systemDefault()
                + " using yyyy-MM-ddTHH:mm. Blank time-out means the visit remains open. Original values and your reason are audited.",
                id("visitId", "Visit ID", row.get("Visit ID")),
                text("timeIn", "Time in", localInput(row.get("Time in")), true),
                text("timeOut", "Time out (optional)", localInput(row.get("Time out")), false),
                text("reason", "Correction reason", "", true)).ifPresent(values -> {
            final LocalDateTime in;
            final LocalDateTime out;
            try {
                in = LocalDateTime.parse(values.get("timeIn"));
                out = values.get("timeOut").isBlank() ? null : LocalDateTime.parse(values.get("timeOut"));
            } catch (java.time.format.DateTimeParseException invalid) {
                notice("Check the times", "Use yyyy-MM-ddTHH:mm, for example 2026-10-23T09:30. No correction was saved.");
                return;
            }
            if (!confirm("Correct visit", "Save this correction with its audit reason?")) return;
            AuthenticatedUser identity = actor;
            mutate("Correct visit", () -> {
                attendanceService.correct(identity, values.get("visitId"), in, out, values.get("reason"));
                return values.get("visitId");
            });
        });
    }

    private static String localInput(String time) {
        return absent(time) ? "" : time.replace(' ', 'T');
    }

    private void reports() {
        pageSubtitle.setText("Generate real database reports, export CSV, or print using your system's PDF printer.");
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("Borrowing activity", "Inventory snapshot", "Fine records", "Library attendance");
        type.setValue(reportSelection);
        type.setMaxWidth(Double.MAX_VALUE);
        type.setAccessibleText("Report type");
        DatePicker from = new DatePicker(LocalDate.now().minusDays(6));
        DatePicker to = new DatePicker(LocalDate.now());
        from.setEditable(false);
        to.setEditable(false);
        from.setAccessibleText("Report start date");
        to.setAccessibleText("Report end date");
        VBox results = new VBox(18);
        Label rangeExplanation = label("", "section-note");
        javafx.beans.property.ObjectProperty<AdminData.Table> generated = new javafx.beans.property.SimpleObjectProperty<>();
        Runnable invalidate = () -> {
            reads.cancel();
            generated.set(null);
            boolean currentSnapshot = List.of("Inventory snapshot", "Fine records").contains(type.getValue());
            from.setDisable(currentSnapshot);
            to.setDisable(currentSnapshot);
            rangeExplanation.setText(currentSnapshot
                    ? "Current database snapshot: date filters do not apply. The fine table has no charge-created timestamp, so this is not a dated revenue report."
                    : "The selected start and end dates are inclusive local library dates (" + ZoneId.systemDefault() + ").");
            results.getChildren().setAll(emptyState("Ready to generate", "Choose filters, then select Generate report. Export uses only a successfully generated result."));
            setMode("REPORT NOT GENERATED", "offline-badge");
        };
        Runnable generate = () -> {
            String reportType = type.getValue();
            LocalDate first = from.getValue();
            LocalDate last = to.getValue();
            boolean snapshot = List.of("Inventory snapshot", "Fine records").contains(reportType);
            generated.set(null);
            if (!snapshot && (first == null || last == null || first.isAfter(last))) {
                results.getChildren().setAll(emptyState("Check the date range", "Choose a start date on or before the end date."));
                return;
            }
            AuthenticatedUser identity = actor;
            setMode("CONNECTING…", "loading-badge");
            results.getChildren().setAll(label("Generating report…", "loading-note"));
            reads.load(() -> "Library attendance".equals(reportType)
                    ? attendanceService.report(identity, first, last) : service.report(identity, reportType, first, last),
                    data -> {
                        generated.set(data);
                        setMode("DATABASE REPORT", "live-badge");
                        String description = snapshot ? "Current snapshot · " + data.rows().size() + " records"
                                : first + " through " + last + " · " + data.rows().size() + " records";
                        results.getChildren().setAll(section(reportType, description, grid(data, "Search this report…").container()));
                    }, error -> {
                        logFailure(error);
                        setMode("REPORT FAILED", "offline-badge");
                        results.getChildren().setAll(emptyState("Report unavailable", failureMessage(error)));
                    });
        };
        type.valueProperty().addListener((observable, before, after) -> { reportSelection = after; invalidate.run(); });
        from.valueProperty().addListener((observable, before, after) -> invalidate.run());
        to.valueProperty().addListener((observable, before, after) -> invalidate.run());
        Button csv = button("Export CSV", "secondary-button", () -> {
            try { ReportExporter.exportCsv(owner(), generated.get(), "bookit-" + type.getValue().toLowerCase().replace(' ', '-') + ".csv"); }
            catch (Exception error) { notice("Export failed", "The report could not be written. Choose a writable location and try again."); }
        });
        Button print = button("Print / Save PDF", "secondary-button", () -> {
            try { ReportExporter.printReport(owner(), type.getValue(), generated.get()); }
            catch (Exception error) { notice("Print failed", "The report could not be printed. Check your selected printer."); }
        });
        csv.disableProperty().bind(generated.isNull());
        print.disableProperty().bind(generated.isNull());
        pageContent.getChildren().addAll(section("Report builder", "CSV exports the complete generated result, not only the rows currently visible after search.",
                row(formField("Report", type), formField("From", from), formField("To", to)), rangeExplanation,
                toolbar(button("Generate report", "primary-button", generate), csv, print)), results);
        invalidate.run();
        generate.run();
    }

    private void form(String title, String explanation, AdminService.Action action, Field... fields) {
        if (saving.get() || disposed) return;
        AdminForms.show(owner(), title, explanation, fields).ifPresent(values -> {
            AuthenticatedUser identity = actor;
            mutate(title, () -> {
                try { return service.execute(identity, action, values); }
                finally { values.clear(); }
            });
        });
    }

    private void confirmAction(String title, String question, AdminService.Action action, Map<String, String> values) {
        if (saving.get() || disposed || !confirm(title, question)) return;
        AuthenticatedUser identity = actor;
        mutate(title, () -> service.execute(identity, action, values));
    }

    private void mutate(String title, Callable<String> write) {
        if (saving.get() || disposed || actor == null) return;
        reads.cancel();
        saving.set(true);
        setMode("SAVING… DO NOT CLOSE", "loading-badge");
        writes.load(write, affectedId -> {
            saving.set(false);
            refreshCurrent();
            notice(title + " completed", "Saved successfully" + (affectedId == null || affectedId.isBlank() ? "." : " · record " + affectedId + "."));
        }, error -> {
            saving.set(false);
            logFailure(error);
            setMode("ACTION FAILED · REFRESH", "offline-badge");
            // Do not retry a write automatically. A lost connection during commit can
            // leave an unknown outcome; refreshing the persisted state is required.
            String explanation = failureMessage(error)
                    + " Refresh records before retrying; a connection failure during commit can have an unknown result.";
            pageContent.getChildren().setAll(section(title + " not confirmed", null,
                    label(explanation, "error-note"), refreshButton()));
            notice(title + " not confirmed", explanation);
        });
    }

    private <T> void load(String message, Callable<T> query, Consumer<T> render) {
        setMode("CONNECTING…", "loading-badge");
        pageContent.getChildren().setAll(section("Loading records", null, label(message, "loading-note")));
        reads.load(query, data -> {
            setMode("DATABASE", "live-badge");
            pageContent.getChildren().clear();
            render.accept(data);
        }, error -> {
            logFailure(error);
            setMode("NOT CONNECTED", "offline-badge");
            pageContent.getChildren().setAll(section("Unable to load records", null,
                    label(failureMessage(error), "error-note"), refreshButton(),
                    label("No demonstration records are substituted for database errors.", "section-note")));
        });
    }

    private void refreshCurrent() {
        if (copiesBookId != null) showCopies(copiesBookId);
        else selectPage(currentPage);
    }

    private Button refreshButton() { return button("Refresh", "secondary-button", this::refreshCurrent); }

    private void setMode(String text, String css) {
        dataModeLabel.setText(text);
        dataModeLabel.getStyleClass().setAll("label", css);
    }

    private static String failureMessage(Throwable error) {
        if (error instanceof IllegalArgumentException) return error.getMessage();
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "42S02".equals(sql.getSQLState()))
                return "A required table is missing. Confirm the selected schema matches your existing BookIt schema; apply sql/02-admin-support.sql for audit/attendance support. Do not rerun the full schema over existing data.";
        }
        return "The database could not complete this operation. Check your connection, schema, and record eligibility.";
    }

    private static void logFailure(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                System.err.println("Admin database operation failed: SQLState=" + sql.getSQLState() + ", vendorCode=" + sql.getErrorCode());
                return;
            }
        }
        System.err.println("Admin operation failed. No raw SQL, passwords, or connection settings were logged.");
    }

    private record Grid(List<String> headers, TableView<ObservableList<String>> table, VBox container) {
        Map<String, String> selected() {
            ObservableList<String> values = table.getSelectionModel().getSelectedItem();
            if (values == null) return Map.of();
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index++) result.put(headers.get(index), values.get(index));
            return result;
        }
    }

    private static Grid grid(AdminData.Table data, String prompt) {
        TableView<ObservableList<String>> table = table(data.headers().toArray(String[]::new),
                data.rows().stream().map(row -> row.toArray(String[]::new)).toArray(String[][]::new));
        table.setPrefHeight(420);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(FXCollections.observableArrayList(table.getItems()));
        SortedList<ObservableList<String>> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText(prompt);
        search.setAccessibleText(prompt);
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        Label count = label(data.rows().size() + " records", "muted");
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(values -> values.stream().anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + data.rows().size() + " records");
        });
        return new Grid(data.headers(), table, new VBox(16, new HBox(16, search, count), table));
    }

    private static Button selected(String title, Grid grid, Predicate<Map<String, String>> eligible, Runnable action) {
        Button button = button(title, "secondary-button", action);
        button.disableProperty().bind(Bindings.createBooleanBinding(() ->
                grid.table().getSelectionModel().getSelectedItem() == null || !eligible.test(grid.selected()),
                grid.table().getSelectionModel().selectedItemProperty()));
        return button;
    }

    private static FlowPane toolbar(Node... nodes) {
        FlowPane toolbar = new FlowPane(12, 12, nodes);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private static FlowPane metrics(Map<String, String> metrics) {
        FlowPane cards = new FlowPane(18, 18);
        String[] accents = {"blue", "orange", "green"};
        int index = 0;
        for (Map.Entry<String, String> metric : metrics.entrySet()) {
            VBox card = stat(metric.getKey().toUpperCase(java.util.Locale.ROOT), metric.getValue(),
                    "Current database snapshot", accents[index++ % accents.length]);
            card.setMinWidth(190);
            card.setPrefWidth(235);
            cards.getChildren().add(card);
        }
        return cards;
    }

    private static VBox formField(String title, Node input) {
        VBox field = new VBox(7, label(title, "stat-label"), input);
        field.setMinWidth(0);
        return field;
    }

    private static String pageDescription(String page) {
        return switch (page) {
            case "Overview" -> "Live database counts and records requiring attention; attendance has its own page.";
            case "Books & copies" -> "Select a title to edit its metadata or manage physical copies. New records are saved to MySQL.";
            case "Members" -> "USER member accounts and administrator status. Passwords, password hashes, and QR tokens are never shown.";
            case "Borrowing & returns" -> "Issue and return physical copies. BookIt checks the member and copy again inside the transaction.";
            case "Reservations" -> "Mark waiting requests ready or cancel active requests. Issuing a copy completes the member's matching reservation.";
            case "Fines" -> "Manual charges and payment recording, not online payment processing. Fine changes require an explanation.";
            case "Library attendance" -> "Latest 1,000 visits; use Reports for a full selected date range. Times shown in " + ZoneId.systemDefault() + ". Open visits have no final duration; admin corrections retain an audit reason.";
            default -> "Current database records.";
        };
    }

    private static boolean absent(String value) {
        return value == null || value.isBlank() || "—".equals(value) || "-".equals(value)
                || "Not provided".equalsIgnoreCase(value) || "None".equalsIgnoreCase(value);
    }

    private static String clean(String value) {
        return absent(value) || "Uncategorized".equals(value) ? "" : value;
    }

    private Window owner() { return root.getScene() == null ? null : root.getScene().getWindow(); }

    private void accountSecurity() {
        setMode("PASSWORD REQUIRED", "offline-badge");
        pageSubtitle.setText("Protect your administrator account. Password changes require your current password.");
        Button change = button("Change password", "primary-button", this::openPasswordChange);
        change.setId("adminChangePassword");
        pageContent.getChildren().addAll(
                section("Administrator account", "Identity supplied by sign-in; the database is rechecked before a password change.",
                        label("Username: " + actor.username(), "profile-value"),
                        label("Account ID: " + actor.userId() + " · Role: " + actor.role(), "muted")),
                section("Change your password", "This changes your own password only. It cannot reset another member's credentials.",
                        change, label("A successful change signs out this app session and revokes any saved QR credential for this account. Administrator QR login remains disabled.", "section-note")));
    }

    private void openPasswordChange() {
        if (saving.get() || disposed || actor == null || !(owner() instanceof Stage)) return;
        reads.cancel();
        AuthenticatedUser identity = actor;
        Window window = owner();
        if (SessionManager.getCurrentUser() != identity) {
            dispose();
            root.setDisable(true);
            return;
        }
        saving.set(true);
        try {
            PasswordChangeDialog.show(window, identity, ignored -> {},
                    () -> finishPasswordSession(identity, window));
        } finally {
            if (!disposed) saving.set(false);
        }
    }

    private void finishPasswordSession(AuthenticatedUser expected, Window window) {
        if (disposed || actor != expected) return;
        if (SessionManager.getCurrentUser() != expected) {
            dispose();
            root.setDisable(true);
            return;
        }
        if (!(window instanceof Stage stage) || root.getScene() == null
                || root.getScene().getWindow() != window) return;
        try {
            Navigation.logout(stage);
        } catch (IOException | RuntimeException failure) {
            SessionManager.logout();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("BookIt - Sign in again");
            alert.setHeaderText("You have been signed out.");
            alert.setContentText("The login screen could not open. Restart BookIt to sign in again.");
            try { alert.showAndWait(); }
            finally { stage.close(); }
        }
    }

    private boolean confirm(String title, String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner() != null) alert.initOwner(owner());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void notice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner() != null) alert.initOwner(owner());
        alert.showAndWait();
    }

    @FXML private void handleLogout(ActionEvent event) {
        if (!saving.get()) Navigation.logoutFrom(event);
    }
}
