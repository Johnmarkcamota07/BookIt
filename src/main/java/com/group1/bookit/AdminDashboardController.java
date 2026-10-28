package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.net.URL;
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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.util.Duration;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import static com.group1.bookit.DashboardUi.*;
import static com.group1.bookit.AdminForms.*;
import static com.group1.bookit.AdminService.Action.*;

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
    private final FineAutomationService fineAutomation;
    private final FxDataLoader reads = new FxDataLoader();
    private final FxDataLoader writes = new FxDataLoader();
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final Map<String, Button> navigation = new LinkedHashMap<>();
    private AuthenticatedUser actor;
    private String currentPage = "Overview";
    private String copiesBookId;
    private String reportSelection = "Borrowing activity";
    private boolean disposed;
    private Window guardedWindow;
    private Timeline fineTimer;
    private boolean automationPaused;
    private boolean automationRunning;
    private boolean automationStarted;
    private String automationMessage = "Waiting for the signed-in dashboard to open.";
    private FineAutomationService.Result lastFineCheck;
    private final ChangeListener<Boolean> showingListener = (observable, before, showing) -> {
        if (showing) queueAutomationStart();
        else if (fineTimer != null) fineTimer.stop();
    };
    private final javafx.event.EventHandler<WindowEvent> closingGuard = event -> {
        if (saving.get()) {
            event.consume();
            notice("Saving is in progress", "Wait for the result before closing BookIt. Do not retry a transaction while its result is unknown.");
        }
    };

    public AdminDashboardController() { this(new AdminService(), new AttendanceService()); }

    public AdminDashboardController(AdminService service, AttendanceService attendanceService) {
        this(service, attendanceService, new FineAutomationService());
    }

    public AdminDashboardController(AdminService service, AttendanceService attendanceService,
                                    FineAutomationService fineAutomation) {
        this.service = Objects.requireNonNull(service);
        this.attendanceService = Objects.requireNonNull(attendanceService);
        this.fineAutomation = Objects.requireNonNull(fineAutomation);
    }

    @FXML private void initialize() {
        LogoSupport.install(sidebarLogo);
        saving.addListener((observable, before, busy) -> root.setDisable(busy));
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null) {
                scene.windowProperty().addListener((windowObservable, oldWindow, window) -> guardClosing(window));
                guardClosing(scene.getWindow());
            }
        });
        for (String page : List.of("Overview", "Books & copies", "Members", "Borrowing & returns",
                "Reservations", "Fines", "Library attendance", "Reports", "Automation", "Account security")) {
            Button nav = button(page, "nav-button", () -> selectPage(page));
            nav.setMaxWidth(Double.MAX_VALUE);
            navigation.put(page, nav);
            navigationBox.getChildren().add(nav);
        }
        selectPage("Overview");
    }

    private void guardClosing(Window window) {
        if (guardedWindow == window) return;
        if (guardedWindow != null) {
            guardedWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
            guardedWindow.showingProperty().removeListener(showingListener);
        }
        guardedWindow = window;
        if (!disposed && window != null) {
            window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
            window.showingProperty().addListener(showingListener);
            queueAutomationStart();
        }
    }

    @Override public void setUser(AuthenticatedUser user) {
        if (user == null || !"ADMIN".equals(user.role()))
            throw new IllegalArgumentException("The admin dashboard requires an ADMIN account.");
        if (saving.get()) throw new IllegalStateException("Wait for the current transaction before changing accounts.");
        if (disposed) throw new IllegalStateException("This dashboard has already closed.");
        reads.cancel();
        actor = user;
        automationPaused = false;
        automationStarted = false;
        lastFineCheck = null;
        automationMessage = "Waiting for the signed-in dashboard to open.";
        navigation.get("Automation").setText("Automation");
        accountName.setText(user.username());
        accountRole.setText("ADMIN WORKSPACE");
        selectPage(currentPage);
        queueAutomationStart();
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        if (fineTimer != null) fineTimer.stop();
        guardClosing(null);
        reads.close();
        writes.close();
        actor = null;
        pageContent.getChildren().clear();
        accountName.setText("Signed out");
        pageTitle.setText("Signed out");
        pageSubtitle.setText("");
        setMode("NOT CONNECTED", "offline-badge");
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
        } else if ("Automation".equals(page)) automationPage();
        else if ("Account security".equals(page)) accountSecurity();
        else if ("Reports".equals(page)) reports();
        else {
            AuthenticatedUser identity = actor;
            load("Loading " + page.toLowerCase() + "…",
                    () -> "Library attendance".equals(page) ? attendanceService.load(identity) : service.load(identity, page),
                    data -> renderPage(page, data));
        }
    }

    private void renderPage(String page, AdminData.Page data) {
        Grid grid = grid(data.table(), "Search " + page.toLowerCase() + "…");
        String actionsFile = switch (page) {
            case "Overview" -> "AdminOverviewActions.fxml";
            case "Books & copies" -> "AdminBooksActions.fxml";
            case "Members" -> "AdminMembersActions.fxml";
            case "Borrowing & returns" -> "AdminLoansActions.fxml";
            case "Reservations" -> "AdminReservationsActions.fxml";
            case "Fines" -> "AdminFinesActions.fxml";
            case "Library attendance" -> "AdminAttendanceActions.fxml";
            default -> throw new IllegalArgumentException("Unknown admin page.");
        };
        var actions = adminLayout(actionsFile);
        control(actions, "adminRefresh", Button.class).setOnAction(event -> refreshCurrent());
        switch (page) {
            case "Overview" -> {
                control(actions, "adminManageBooks", Button.class).setOnAction(event -> selectPage("Books & copies"));
                control(actions, "adminIssueReturn", Button.class).setOnAction(event -> selectPage("Borrowing & returns"));
                control(actions, "adminViewAttendance", Button.class).setOnAction(event -> selectPage("Library attendance"));
            }
            case "Books & copies" -> {
                control(actions, "adminAddBook", Button.class).setOnAction(event -> bookForm(null));
                selected(control(actions, "adminEditBook", Button.class), grid, ignored -> true, () -> bookForm(grid.selected()));
                selected(control(actions, "adminManageCopies", Button.class), grid, ignored -> true,
                        () -> showCopies(grid.selected().get("Book ID")));
                control(actions, "adminFindCopy", Button.class).setOnAction(event -> lookupBarcode(false));
            }
            case "Members" -> {
                control(actions, "adminCreateMember", Button.class).setOnAction(event -> createMember());
                selected(control(actions, "adminMemberStatus", Button.class), grid,
                        row -> "USER".equals(row.get("Role")) && !actor.userId().equals(row.get("User ID")),
                        () -> memberStatus(grid.selected()));
            }
            case "Borrowing & returns" -> {
                control(actions, "adminIssueLoan", Button.class).setOnAction(event -> issueLoan());
                control(actions, "adminScanReturn", Button.class).setOnAction(event -> lookupBarcode(true));
                selected(control(actions, "adminRecordReturn", Button.class), grid,
                        row -> !"Returned".equalsIgnoreCase(row.get("Status")) && absent(row.get("Returned")),
                        () -> confirmAction("Record return", "Record this copy as returned now? Any automatic overdue fine is finalized using the return date in Asia/Manila.",
                                RETURN_LOAN, Map.of("loanId", grid.selected().get("Loan ID"))));
            }
            case "Reservations" -> {
                selected(control(actions, "adminReservationReady", Button.class), grid,
                        row -> "PENDING".equalsIgnoreCase(row.get("Status")), () -> reservation(grid.selected(), "READY"));
                selected(control(actions, "reservationConfirmPickup", Button.class), grid,
                        row -> "READY".equalsIgnoreCase(row.get("Status")),
                        () -> confirmReservationPickup(grid.selected()));
                selected(control(actions, "adminReservationCancel", Button.class), grid,
                        row -> List.of("PENDING", "READY").contains(row.get("Status").toUpperCase()),
                        () -> reservation(grid.selected(), "CANCELLED"));
            }
            case "Fines" -> {
                control(actions, "adminChargeFine", Button.class).setOnAction(event -> fineForm(null));
                selected(control(actions, "adminAdjustFine", Button.class), grid, this::unpaid, () -> fineForm(grid.selected()));
                selected(control(actions, "adminRecordPayment", Button.class), grid, this::unpaid,
                        () -> confirmAction("Record payment", "Confirm PHP " + grid.selected().get("Amount")
                                + " was received outside BookIt. Automatic fines require the book to be returned first. If the amount changed, refresh before confirming payment. This does not charge a bank account.",
                                RECORD_PAYMENT, Map.of("fineId", grid.selected().get("Fine ID"),
                                        "expectedAmount", grid.selected().get("Amount"))));
                selected(control(actions, "adminWaiveFine", Button.class), grid, this::unpaid, () -> waive(grid.selected()));
            }
            case "Library attendance" -> {
                control(actions, "adminStartEntrance", Button.class).setOnAction(event -> {
                    AttendanceKiosk.show(owner(), actor, attendanceService);
                    if (!disposed) selectPage("Library attendance");
                });
                selected(control(actions, "adminCorrectVisit", Button.class), grid, ignored -> true, () -> correctVisit(grid.selected()));
                control(actions, "adminAttendanceReport", Button.class).setOnAction(event -> {
                    reportSelection = "Library attendance";
                    selectPage("Reports");
                });
            }
            default -> throw new IllegalArgumentException("Unknown admin page.");
        }
        var view = adminLayout("AdminRecords.fxml");
        control(view, "adminRecordTitle", Label.class).setText(page);
        control(view, "adminRecordSubtitle", Label.class).setText(pageDescription(page));
        control(view, "adminRecordActions", VBox.class).getChildren().setAll((FlowPane) actions.getRoot());
        control(view, "adminRecordGrid", VBox.class).getChildren().setAll(grid.container());
        if (!data.metrics().isEmpty()) pageContent.getChildren().add(metrics(data.metrics()));
        pageContent.getChildren().add((VBox) view.getRoot());
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
                                + "\n\nConfirm the physical book has been received. Any automatic overdue fine is finalized using this return date in Asia/Manila.",
                                RETURN_LOAN, Map.of("loanId", copy.openLoanId()));
                    }
                } else {
                    showCopyDetails(copy);
                }
            }, error -> {
                logFailure(error);
                setMode("COPY NOT FOUND / UNAVAILABLE", "offline-badge");
                notice("Copy lookup failed", failureMessage(error));
            });
        });
    }

    private void showCopyDetails(ScannedBookCopy copy) {
        try {
            FXMLLoader view = adminLayout("AdminCopyDetails.fxml");
            control(view, "adminCopyTitle", Label.class).setText(copy.title());
            control(view, "adminCopyIdentity", Label.class).setText("Copy ID: " + copy.copyId() + " · Book ID: " + copy.bookId());
            control(view, "adminCopyBarcode", Label.class).setText("Barcode: " + copy.barcode());
            control(view, "adminCopyInventory", Label.class).setText("Physical status: " + copy.status() + " · Shelf: " + copy.shelf());
            control(view, "adminCopyLoan", Label.class).setText(copy.openLoanId() == null || copy.openLoanId().isBlank()
                    ? "No open loan." : "Open loan: " + copy.openLoanId() + " · " + copy.memberName());
            detailDialog(root, "Registered book copy", view.getRoot(), "Read-only lookup. No copy, loan, or return was changed.");
        } catch (LayoutException unavailable) {
            notice("Copy details unavailable", "Keep AdminCopyDetails.fxml in the bookit resource folder, then Clean and Build.");
        }
    }

    private void reservation(Map<String, String> row, String status) {
        confirmAction("Update reservation", "Set reservation " + row.get("Reservation ID") + " to " + status
                + "? Marking ready does not send an email or hold a specific copy.", SET_RESERVATION_STATUS,
                Map.of("reservationId", row.get("Reservation ID"), "status", status));
    }

    private void confirmReservationPickup(Map<String, String> row) {
        if (!"READY".equalsIgnoreCase(row.get("Status"))) return;
        AuthenticatedUser identity = actor;
        Window window = owner();
        if (!pickupContext(identity, window)) return;
        Map<String, String> selected = Map.copyOf(row);
        String reservationId = selected.get("Reservation ID");
        String memberId = selected.get("User ID");
        String bookId = selected.get("Book ID");
        LocalDate suggestedDue = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(14);
        AdminForms.show(window, "Confirm reservation pickup",
                "Use this only when the member is collecting the physical book. Scan or enter that copy's BookIt barcode. The due date is editable; 14 days is only a suggestion. Save opens a final handover confirmation; scanning by itself never creates a loan.",
                id("reservationId", "Reservation ID", reservationId),
                id("userId", "Member user ID", memberId),
                id("memberName", "Member", selected.get("Member")),
                id("expectedBookId", "Book ID", bookId),
                id("bookTitle", "Reserved title", selected.get("Book")),
                barcode(), date("dueDate", "Due date (Asia/Manila)", suggestedDue)).ifPresent(values -> {
            if (!pickupContext(identity, window)) return;
            Map<String, String> request = Map.of(
                    "reservationId", reservationId, "userId", memberId,
                    "expectedBookId", bookId, "barcode", values.get("barcode"),
                    "dueDate", values.get("dueDate"));
            if (!confirm("Confirm physical handover", "Has " + selected.get("Member")
                    + " received \"" + selected.get("Book") + "\"?"
                    + "\n\nReservation: " + reservationId + "\nCopy barcode: " + request.get("barcode")
                    + "\nDue: " + request.get("dueDate") + " at 23:59:59 (Asia/Manila)"
                    + "\n\nConfirming creates the loan now and marks this reservation FULFILLED together."
                    + " The borrowing period starts at this handover, not when the reservation was made or marked ready."
                    + " The member can then refresh My loans to see it.")) return;
            if (!pickupContext(identity, window)) return;
            saveReservationPickup(identity, window, request);
        });
    }

    private void saveReservationPickup(AuthenticatedUser identity, Window window, Map<String, String> request) {
        if (!pickupContext(identity, window)) return;
        reads.cancel();
        saving.set(true);
        setMode("SAVING PICKUP… DO NOT CLOSE", "loading-badge");
        writes.load(() -> {
            String loanId = service.execute(identity, ISSUE_LOAN, request);
            if (loanId == null || loanId.isBlank()) throw new IllegalStateException("Pickup result not confirmed.");
            return loanId;
        }, loanId -> {
            if (!pickupCompletion(identity, window)) return;
            saving.set(false);
            refreshCurrent();
            notice("Pickup confirmed", "Loan " + loanId + " was created and this reservation is fulfilled. "
                    + "Its borrowing start is the saved pickup time. The member can refresh My loans to see the due date.");
        }, failure -> {
            if (!pickupCompletion(identity, window)) return;
            saving.set(false);
            logFailure(failure);
            setMode("PICKUP NOT CONFIRMED · REFRESH", "offline-badge");
            String explanation = failureMessage(failure)
                    + " Refresh reservations and check Borrowing & returns for the same member and barcode before trying again. "
                    + "A connection failure can happen after the pickup was saved. Do not hand over another copy or repeat the save blindly.";
            showRecovery("Pickup needs review", explanation);
            notice("Pickup not confirmed", explanation);
        });
    }

    private boolean pickupCompletion(AuthenticatedUser identity, Window window) {
        if (disposed) return false;
        if (actor != identity || SessionManager.getCurrentUser() != identity || owner() != window
                || !window.isShowing() || root.getScene() == null || root.getScene().getRoot() != root) {
            dispose();
            root.setDisable(true);
            return false;
        }
        return true;
    }

    private boolean pickupContext(AuthenticatedUser identity, Window window) {
        return !disposed && !saving.get() && identity != null && actor == identity
                && SessionManager.getCurrentUser() == identity && "Reservations".equals(currentPage)
                && window != null && window.isShowing() && owner() == window
                && root.getScene() != null && root.getScene().getRoot() == root;
    }

    private boolean unpaid(Map<String, String> row) {
        return "UNPAID".equalsIgnoreCase(row.get("Status"));
    }

    private void fineForm(Map<String, String> row) {
        if (row == null) form("Charge fine", "Record a manual charge against an existing loan. Manual charges are not changed by the PHP20/day overdue checks. A loan can have only one fine record.",
                CHARGE_FINE, text("loanId", "Loan ID", "", true), text("amount", "Amount (PHP)", "", true),
                text("reason", "Reason", "", true));
        else form("Adjust fine", "Adjust an unpaid charge. The reason is recorded. Adjusting an automatic charge permanently opts it out of future automated amount updates.", ADJUST_FINE,
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
            var view = adminLayout("AdminCopies.fxml");
            control(view, "adminCopiesBack", Button.class).setOnAction(event -> selectPage("Books & copies"));
            control(view, "adminAddCopy", Button.class).setOnAction(event -> copyForm(bookId, null));
            selected(control(view, "adminCopyLabel", Button.class), grid, ignored -> true,
                    () -> showBarcodeLabel(grid.selected().get("Copy ID")));
            selected(control(view, "adminUpdateCopy", Button.class), grid,
                    row -> absent(row.get("Open loan")) || "0".equals(row.get("Open loan")),
                    () -> copyForm(bookId, grid.selected()));
            control(view, "adminRefresh", Button.class).setOnAction(event -> refreshCurrent());
            control(view, "adminCopiesGrid", VBox.class).getChildren().setAll(grid.container());
            pageContent.getChildren().add((VBox) view.getRoot());
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
        try { renderReports(); }
        catch (LayoutException unavailable) { showLayoutFailure(); }
    }

    @SuppressWarnings("unchecked")
    private void renderReports() {
        pageSubtitle.setText("Generate real database reports, export CSV, or print using your system's PDF printer.");
        FXMLLoader view = adminLayout("AdminReports.fxml");
        VBox reportPage = view.getRoot();
        ComboBox<String> type = control(view, "adminReportType", ComboBox.class);
        type.getItems().addAll("Borrowing activity", "Inventory snapshot", "Fine records", "Library attendance");
        type.setValue(reportSelection);
        DatePicker from = control(view, "adminReportFrom", DatePicker.class);
        DatePicker to = control(view, "adminReportTo", DatePicker.class);
        from.setValue(LocalDate.now().minusDays(6));
        to.setValue(LocalDate.now());
        VBox results = control(view, "adminReportResults", VBox.class);
        Label rangeExplanation = control(view, "adminReportRange", Label.class);
        Button generateButton = control(view, "adminReportGenerate", Button.class);
        Button csv = control(view, "adminReportCsv", Button.class);
        Button print = control(view, "adminReportPrint", Button.class);
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
                        try {
                            Grid reportGrid = grid(data, "Search this report…");
                            String description = snapshot ? "Current snapshot · " + data.rows().size() + " records"
                                    : first + " through " + last + " · " + data.rows().size() + " records";
                            results.getChildren().setAll(section(reportType, description, reportGrid.container()));
                            generated.set(data);
                            setMode("DATABASE REPORT", "live-badge");
                        } catch (LayoutException unavailable) {
                            generated.set(null);
                            setMode("SCREEN UNAVAILABLE", "offline-badge");
                            results.getChildren().setAll(emptyState("Report layout unavailable",
                                    "Copy all admin layouts into the bookit resource folder, then Clean and Build."));
                        }
                    }, error -> {
                        logFailure(error);
                        setMode("REPORT FAILED", "offline-badge");
                        results.getChildren().setAll(emptyState("Report unavailable", failureMessage(error)));
                    });
        };
        type.valueProperty().addListener((observable, before, after) -> { reportSelection = after; invalidate.run(); });
        from.valueProperty().addListener((observable, before, after) -> invalidate.run());
        to.valueProperty().addListener((observable, before, after) -> invalidate.run());
        csv.setOnAction(event -> {
            try { ReportExporter.exportCsv(owner(), generated.get(), "bookit-" + type.getValue().toLowerCase().replace(' ', '-') + ".csv"); }
            catch (Exception error) { notice("Export failed", "The report could not be written. Choose a writable location and try again."); }
        });
        print.setOnAction(event -> {
            try { ReportExporter.printReport(owner(), type.getValue(), generated.get()); }
            catch (Exception error) { notice("Print failed", "The report could not be printed. Check your selected printer."); }
        });
        csv.disableProperty().bind(generated.isNull());
        print.disableProperty().bind(generated.isNull());
        generateButton.setOnAction(event -> generate.run());
        pageContent.getChildren().add(reportPage);
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
            showRecovery(title + " not confirmed", explanation);
            notice(title + " not confirmed", explanation);
        });
    }

    private <T> void load(String message, Callable<T> query, Consumer<T> render) {
        setMode("CONNECTING…", "loading-badge");
        pageContent.getChildren().setAll(section("Loading records", null, label(message, "loading-note")));
        reads.load(query, data -> {
            setMode("DATABASE", "live-badge");
            pageContent.getChildren().clear();
            try { render.accept(data); }
            catch (LayoutException unavailable) { showLayoutFailure(); }
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
                return "A required table is missing. Confirm the selected BookIt database and apply the additive admin support, loan renewal, and automatic fine migrations (02, 03, 04). Do not rerun the full schema over existing data.";
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
        var view = adminLayout("AdminGrid.fxml");
        TextField search = control(view, "adminGridSearch", TextField.class);
        search.setPromptText(prompt);
        search.setAccessibleText(prompt);
        Label count = control(view, "adminGridCount", Label.class);
        count.setText(data.rows().size() + " records");
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(values -> values.stream().anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + data.rows().size() + " records");
        });
        control(view, "adminGridTableHost", VBox.class).getChildren().setAll(table);
        return new Grid(data.headers(), table, (VBox) view.getRoot());
    }

    private static Button selected(String title, Grid grid, Predicate<Map<String, String>> eligible, Runnable action) {
        Button button = button(title, "secondary-button", action);
        return selected(button, grid, eligible, action);
    }

    private static Button selected(Button button, Grid grid, Predicate<Map<String, String>> eligible, Runnable action) {
        button.setOnAction(event -> action.run());
        button.disableProperty().bind(Bindings.createBooleanBinding(() ->
                grid.table().getSelectionModel().getSelectedItem() == null || !eligible.test(grid.selected()),
                grid.table().getSelectionModel().selectedItemProperty()));
        return button;
    }

    private static FlowPane toolbar(Node... nodes) {
        FlowPane toolbar = adminLayout("AdminToolbar.fxml").getRoot();
        toolbar.getChildren().setAll(nodes);
        return toolbar;
    }

    private static FlowPane metrics(Map<String, String> metrics) {
        FlowPane cards = adminLayout("AdminMetrics.fxml").getRoot();
        String[] accents = {"blue", "orange", "green"};
        int index = 0;
        for (Map.Entry<String, String> metric : metrics.entrySet()) {
            var view = adminLayout("AdminMetric.fxml");
            VBox card = view.getRoot();
            control(view, "adminMetricTitle", Label.class).setText(metric.getKey().toUpperCase(java.util.Locale.ROOT));
            control(view, "adminMetricValue", Label.class).setText(metric.getValue());
            card.getStyleClass().add("accent-" + accents[index++ % accents.length]);
            cards.getChildren().add(card);
        }
        return cards;
    }

    private static String pageDescription(String page) {
        return switch (page) {
            case "Overview" -> "Live database counts and records requiring attention; attendance has its own page.";
            case "Books & copies" -> "Select a title to edit its metadata or manage physical copies. New records are saved to MySQL.";
            case "Members" -> "USER member accounts and administrator status. Passwords, password hashes, and QR tokens are never shown.";
            case "Borrowing & returns" -> "Issue and return physical copies. BookIt checks the member and copy again inside the transaction.";
            case "Reservations" -> "Mark a request ready, then confirm physical pickup to issue its copy and start the loan. The fulfilled reservation remains in history.";
            case "Fines" -> "Manual and automatic charges, with payment recording only. Return a book before paying its automatic fine. Adjusting an automatic charge opts it out of later automated changes.";
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

    private static final class LayoutException extends IllegalStateException {
        LayoutException(String message, Throwable cause) { super(message, cause); }
    }

    private static FXMLLoader adminLayout(String filename) {
        URL location = AdminDashboardController.class.getResource("/com/group1/bookit/" + filename);
        if (location == null) throw new LayoutException(filename + " is missing.", null);
        try {
            FXMLLoader view = new FXMLLoader(location);
            view.load();
            return view;
        } catch (IOException failure) {
            throw new LayoutException(filename + " could not be loaded.", failure);
        }
    }

    private static <T> T control(FXMLLoader view, String name, Class<T> type) {
        Object value = view.getNamespace().get(name);
        if (!type.isInstance(value)) throw new LayoutException("The admin layout is missing " + name + ".", null);
        return type.cast(value);
    }

    private void showLayoutFailure() {
        setMode("SCREEN UNAVAILABLE", "offline-badge");
        pageContent.getChildren().setAll(section("Admin screen unavailable", null,
                label("An admin layout could not be loaded. Copy all files from the combined update into the bookit resource folder, then Clean and Build.", "error-note"),
                refreshButton()));
    }

    private void showRecovery(String title, String explanation) {
        try {
            FXMLLoader view = adminLayout("AdminRecovery.fxml");
            control(view, "adminRecoveryTitle", Label.class).setText(title);
            control(view, "adminRecoveryMessage", Label.class).setText(explanation);
            control(view, "adminRecoveryRefresh", Button.class).setOnAction(event -> refreshCurrent());
            pageContent.getChildren().setAll((VBox) view.getRoot());
        } catch (LayoutException unavailable) {
            // Keep recovery accessible even if its presentation resource is missing.
            pageContent.getChildren().setAll(section(title, null,
                    label(explanation, "error-note"), refreshButton()));
        }
    }

    private void queueAutomationStart() {
        Platform.runLater(() -> {
            if (!activeDashboard()) return;
            if (fineTimer == null) {
                fineTimer = new Timeline(new KeyFrame(Duration.seconds(60), event -> runFineCheck(false)));
                fineTimer.setCycleCount(Timeline.INDEFINITE);
            }
            fineTimer.play();
            if (!automationStarted) runFineCheck(false);
        });
    }

    private boolean activeDashboard() {
        Window window = owner();
        return !disposed && actor != null && SessionManager.getCurrentUser() == actor
                && root.getScene() != null && root.getScene().getRoot() == root
                && window != null && window.isShowing();
    }

    private boolean hasOwnedWindow() {
        Window dashboard = owner();
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window == dashboard || !(window instanceof Stage stage)) continue;
            for (Window parent = stage.getOwner(); parent != null;
                 parent = parent instanceof Stage ownerStage ? ownerStage.getOwner() : null) {
                if (parent == dashboard) return true;
            }
        }
        return false;
    }

    private void runFineCheck(boolean manual) {
        if (!activeDashboard() || saving.get() || automationRunning || hasOwnedWindow()
                || (!manual && automationPaused)) return;
        AuthenticatedUser identity = actor;
        Window window = owner();
        automationStarted = true;
        automationRunning = true;
        automationPaused = false;
        automationMessage = "Checking saved loans using the database clock…";
        navigation.get("Automation").setText("Automation");
        saving.set(true);
        setMode("CHECKING OVERDUE FINES…", "loading-badge");
        writes.load(() -> fineAutomation.run(identity), result -> {
            if (disposed || actor != identity || owner() != window
                    || SessionManager.getCurrentUser() != identity) {
                automationRunning = false;
                
                if (!disposed) { dispose(); root.setDisable(true); }
                return;
            }
            automationRunning = false;
            saving.set(false);
            if (result == null) {
                pauseAfterFineFailure(new IllegalStateException("Missing reconciliation result."));
                return;
            }
            lastFineCheck = result;
            automationMessage = "Last check completed. " + result.inspected() + " loans checked; "
                    + result.created() + " charges created, " + result.updated() + " updated, "
                    + result.finalized() + " finalized, " + result.skipped() + " unchanged.";
            if ("Automation".equals(currentPage)) {
                pageContent.getChildren().clear();
                automationPage();
            } else if (List.of("Overview", "Fines", "Borrowing & returns").contains(currentPage)
                    && result.created() + result.updated() + result.finalized() > 0) {
                refreshCurrent();
            } else setMode("DATABASE · FINE CHECK COMPLETE", "live-badge");
        }, error -> {
            if (disposed || actor != identity || owner() != window
                    || SessionManager.getCurrentUser() != identity) {
                automationRunning = false;
                if (!disposed) { dispose(); root.setDisable(true); }
                return;
            }
            automationRunning = false;
            saving.set(false);
            pauseAfterFineFailure(error);
        });
    }

    private void pauseAfterFineFailure(Throwable error) {
        logFailure(error);
        automationPaused = true;
        automationMessage = "Checks paused: the latest cycle was not confirmed. Some earlier loans may already "
                + "have committed. Review refreshed fine records, the schema, and the connection. "
                + "Run checks now starts a fresh calculation from saved state; it does not replay a payment or add a second daily fee.";
        navigation.get("Automation").setText("Automation · paused");
        setMode("FINE CHECKS PAUSED", "offline-badge");
        if ("Automation".equals(currentPage)) {
            pageContent.getChildren().clear();
            automationPage();
        }
    }

    private void automationPage() {
        try { renderAutomation(); }
        catch (LayoutException unavailable) { showLayoutFailure(); }
    }

    private void renderAutomation() {
        pageSubtitle.setText("Overdue fines are calculated from saved dates, not repeatedly added each time a check runs.");
        setMode(automationPaused ? "CHECKS PAUSED" : "ADMIN-APP CHECKS", automationPaused ? "offline-badge" : "live-badge");
        FXMLLoader view = adminLayout("AdminAutomation.fxml");
        VBox automationPage = view.getRoot();
        Label feedback = control(view, "automationFeedback", Label.class);
        feedback.setText(automationMessage);
        feedback.getStyleClass().setAll("label", automationPaused ? "error-note" : "section-note");
        Button run = control(view, "automationRun", Button.class);
        Button pause = control(view, "automationPause", Button.class);
        run.setOnAction(event -> {
            if (saving.get() || disposed || actor == null) return;
            if (automationPaused && !confirm("Run fresh fine checks",
                    "Review refreshed fine records first. Run a fresh, cumulative assessment from saved database state and resume checks every 60 seconds?")) return;
            runFineCheck(true);
        });
        pause.setOnAction(event -> {
            if (saving.get() || disposed) return;
            automationPaused = true;
            automationMessage = "Periodic checks paused in this dashboard. Run checks now resumes them. Returns and payments still finalize eligible automatic fines inside their own transactions.";
            navigation.get("Automation").setText("Automation · paused");
            pageContent.getChildren().clear();
            automationPage();
        });
        pause.setDisable(automationPaused);
        String checkedAt = lastFineCheck == null ? "No completed cycle in this dashboard session."
                : "Last completed: " + lastFineCheck.completedAt().atZone(java.time.ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.of("Asia/Manila"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", java.util.Locale.ENGLISH))
                        + " (Asia/Manila). Counts may overlap when a newly created charge is also finalized.";
        control(view, "automationCheckedAt", Label.class).setText(checkedAt);
        pageContent.getChildren().add(automationPage);
    }

    private void accountSecurity() {
        setMode("PASSWORD REQUIRED", "offline-badge");
        pageSubtitle.setText("Protect your administrator account. Password changes require your current password.");
        try {
            FXMLLoader view = adminLayout("AdminSecurity.fxml");
            control(view, "adminSecurityUsername", Label.class).setText("Username: " + actor.username());
            control(view, "adminSecurityIdentity", Label.class).setText("Account ID: " + actor.userId() + " · Role: " + actor.role());
            control(view, "adminChangePassword", Button.class).setOnAction(event -> openPasswordChange());
            pageContent.getChildren().add((VBox) view.getRoot());
        } catch (LayoutException unavailable) { showLayoutFailure(); }
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
