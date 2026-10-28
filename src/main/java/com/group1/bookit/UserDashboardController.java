package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import static com.group1.bookit.DashboardUi.*;

/** Live member account, catalog, loans, reservations, fines and personal QR. */
public final class UserDashboardController implements DashboardView {
    private static final ZoneId DISPLAY_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    @FXML private BorderPane root;
    @FXML private ImageView sidebarLogo;
    @FXML private VBox navigationBox;
    @FXML private VBox contentBox;
    @FXML private ScrollPane contentScroll;
    @FXML private Label accountNameLabel;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label dataModeLabel;

    private final LibraryRepository repository;
    private final ReservationService reservations;
    private final MemberFineService fines;
    private final MemberProfileService profiles;
    private final LoanRenewalService renewals;
    private final FxDataLoader dataLoader = new FxDataLoader();
    // Page-read cancellation must never cancel a reservation/profile transaction.
    private final FxDataLoader writes = new FxDataLoader();
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final Map<String, Button> navigationButtons = new LinkedHashMap<>();
    private AuthenticatedUser signedInUser;
    private String currentPage = "Overview";
    private boolean disposed;
    private boolean refreshRequired;
    private String unconfirmedExplanation;
    private String successMessage;
    private MemberProfile profileSnapshot;
    private boolean profileDirty;
    private boolean profileRefreshRequired;
    private String profileRecoveryMessage;
    private LoansData loanSnapshot;
    private boolean renewalRefreshRequired;
    private String pendingRenewalRequest;
    private String pendingRenewalLoan;
    private String renewalRecoveryMessage;
    private String renewalMessage;
    private Window guardedWindow;
    private final javafx.event.EventHandler<WindowEvent> closingGuard = event -> {
        if (saving.get() || !discardProfileDraft()) event.consume();
    };

    /** FXMLLoader construction does not open a database connection. */
    public UserDashboardController() {
        this(new LibraryRepository(), new ReservationService());
    }

    /** Allows controller-factory tests to supply a repository without changing the FXML. */
    public UserDashboardController(LibraryRepository repository) {
        this(repository, new ReservationService());
    }

    /** Offline controller-factory seam; neither dependency is called during construction. */
    public UserDashboardController(LibraryRepository repository, ReservationService reservations) {
        this(repository, reservations, new MemberFineService());
    }

    /** Read-only fine queries can be injected without changing the FXML. */
    public UserDashboardController(LibraryRepository repository, ReservationService reservations,
                                   MemberFineService fines) {
        this(repository, reservations, fines, new MemberProfileService());
    }

    /** Test/controller-factory seam; construction never opens a connection. */
    public UserDashboardController(LibraryRepository repository, ReservationService reservations,
                                   MemberFineService fines, MemberProfileService profiles) {
        this(repository, reservations, fines, profiles, new LoanRenewalService());
    }

    /** Injectable services keep tests offline; construction does not query MySQL. */
    public UserDashboardController(LibraryRepository repository, ReservationService reservations,
                                   MemberFineService fines, MemberProfileService profiles,
                                   LoanRenewalService renewals) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.fines = Objects.requireNonNull(fines, "fines");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.renewals = Objects.requireNonNull(renewals, "renewals");
    }

    @FXML
    private void initialize() {
        LogoSupport.install(sidebarLogo);
        saving.addListener((observable, before, busy) -> root.setDisable(busy));
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null) {
                scene.windowProperty().addListener((windowObservable, oldWindow, window) -> guardClosing(window));
                guardClosing(scene.getWindow());
            }
        });
        addNavigation("Overview");
        addNavigation("Book catalog");
        addNavigation("My loans");
        addNavigation("Reservations");
        addNavigation("My fines");
        addNavigation("My profile & QR");
        showPage("Overview");
    }

    @Override
    public void setUser(AuthenticatedUser user) {
        if (saving.get()) throw new IllegalStateException("Wait for the pending save before changing accounts.");
        if (disposed) throw new IllegalStateException("This dashboard has already closed.");
        if (user == null || !"USER".equals(user.role())) {
            throw new IllegalArgumentException("The user dashboard requires a USER account.");
        }
        dataLoader.cancel();
        signedInUser = user;
        refreshRequired = false;
        unconfirmedExplanation = null;
        successMessage = null;
        clearProfileState();
        clearRenewalState();
        accountNameLabel.setText(user.username());
        showPage(currentPage);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        guardClosing(null);
        dataLoader.close();
        writes.close();
        signedInUser = null;
        successMessage = null;
        unconfirmedExplanation = null;
        clearProfileState();
        clearRenewalState();
        contentBox.getChildren().clear();
        accountNameLabel.setText("Signed out");
        pageTitleLabel.setText("Signed out");
        pageSubtitleLabel.setText("");
        setDataMode("NOT CONNECTED", "offline-badge");
    }

    private void guardClosing(Window window) {
        if (guardedWindow == window) return;
        if (guardedWindow != null) guardedWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
        guardedWindow = window;
        if (!disposed && window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closingGuard);
    }

    private void addNavigation(String name) {
        Button navigationButton = button(name, "nav-button", () -> showPage(name));
        navigationButton.setMaxWidth(Double.MAX_VALUE);
        navigationButtons.put(name, navigationButton);
        navigationBox.getChildren().add(navigationButton);
    }

    private void showPage(String name) {
        if (saving.get() || disposed) return;
        if (!discardProfileDraft()) return;
        dataLoader.cancel();
        profileSnapshot = null;
        profileDirty = false;
        currentPage = name;
        loanSnapshot = null;
        navigationButtons.forEach((title, navigationButton) -> {
            navigationButton.getStyleClass().remove("nav-active");
            if (title.equals(name)) navigationButton.getStyleClass().add("nav-active");
        });
        pageTitleLabel.setText(name);
        setDataMode("NOT CONNECTED", "offline-badge");
        contentBox.getChildren().clear();
        contentScroll.setVvalue(0);
        if (signedInUser == null) {
            pageSubtitleLabel.setText("Waiting for the authenticated member account.");
            contentBox.getChildren().add(emptyState("Waiting for sign-in",
                    "Library records load only after the authenticated account is supplied."));
            return;
        }
        if (refreshRequired && ("Book catalog".equals(name) || "Reservations".equals(name))) {
            showUnconfirmedAction();
            return;
        }
        if (profileRefreshRequired && "My profile & QR".equals(name)) {
            showProfileRecovery();
            return;
        }
        if (renewalRefreshRequired && "My loans".equals(name)) {
            showRenewalRecovery();
            return;
        }
        switch (name) {
            case "Book catalog" -> loadCatalog();
            case "My loans" -> loadLoans();
            case "Reservations" -> loadReservations();
            case "My fines" -> showFines();
            case "My profile & QR" -> showProfile();
            default -> loadOverview();
        }
    }

    private record OverviewData(List<LibraryBook> books, List<LibraryLoan> loans) {}

    private void loadOverview() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Welcome back, " + actor.username() + ". Your catalog and borrowing account.");
        load("Loading your library overview…", () -> new OverviewData(
                List.copyOf(repository.findBooks(actor)), List.copyOf(repository.findMyLoans(actor))),
                this::renderOverview);
    }

    private void renderOverview(OverviewData data) {
        Instant now = Instant.now();
        List<LibraryLoan> active = data.loans().stream().filter(LibraryLoan::isOpen).toList();
        long overdue = active.stream().filter(loan -> loan.isOverdue(now)).count();
        long availableTitles = data.books().stream().filter(book -> book.availableCopies() > 0).count();
        final VBox overviewPage;
        final VBox loansHost;
        final Label emptyLoans;
        final Button catalog;
        final Button myLoans;
        final Button refresh;
        final Button history;
        try {
            URL location = getClass().getResource("/com/group1/bookit/MemberOverview.fxml");
            if (location == null) throw new IOException("MemberOverview.fxml is missing.");
            FXMLLoader view = new FXMLLoader(location);
            overviewPage = view.load();
            pageControl(view, "overviewActiveLoans", Label.class).setText(String.valueOf(active.size()));
            pageControl(view, "overviewOverdueLoans", Label.class).setText(String.valueOf(overdue));
            pageControl(view, "overviewAvailableTitles", Label.class).setText(String.valueOf(availableTitles));
            pageControl(view, "overviewLoansSubtitle", Label.class).setText(zoneNote() + " · Loaded for your member ID only.");
            loansHost = pageControl(view, "overviewLoansHost", VBox.class);
            emptyLoans = pageControl(view, "overviewEmptyLoansLabel", Label.class);
            catalog = pageControl(view, "overviewExploreCatalog", Button.class);
            myLoans = pageControl(view, "overviewViewLoans", Button.class);
            refresh = pageControl(view, "overviewRefresh", Button.class);
            history = pageControl(view, "overviewHistory", Button.class);
        } catch (IOException | ClassCastException unavailable) {
            setDataMode("OVERVIEW UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Overview screen unavailable", null,
                    label("The overview layout could not be loaded. Keep MemberOverview.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            return;
        }
        TableView<ObservableList<String>> loans = table(
                new String[]{"Book", "Copy barcode", "Due date", "Status"},
                active.stream().map(loan -> new String[]{
                    display(loan.bookTitle()), display(loan.barcode()), format(loan.dueAt()), loan.status(now)
                }).toArray(String[][]::new));
        loans.setPlaceholder(emptyLoans);
        loansHost.getChildren().add(loans);
        catalog.setOnAction(event -> showPage("Book catalog"));
        myLoans.setOnAction(event -> showPage("My loans"));
        refresh.setOnAction(event -> retryCurrentPage());
        history.setOnAction(event -> showPage("My loans"));
        contentBox.getChildren().add(overviewPage);
    }

    private void loadCatalog() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Search the database catalog and check recorded copy availability.");
        load("Loading the book catalog…", () -> List.copyOf(repository.findBooks(actor)), this::renderCatalog);
    }

    @SuppressWarnings("unchecked") 
    private void renderCatalog(List<LibraryBook> books) {
        final VBox catalogPage;
        final VBox tableHost;
        final TextField search;
        final ComboBox<String> category;
        final Label count;
        final Label placeholder;
        final Button details;
        final Button reserve;
        final Button refresh;
        try {
            URL location = getClass().getResource("/com/group1/bookit/MemberCatalog.fxml");
            if (location == null) throw new IOException("MemberCatalog.fxml is missing.");
            FXMLLoader view = new FXMLLoader(location);
            catalogPage = view.load();
            tableHost = pageControl(view, "catalogTableHost", VBox.class);
            search = pageControl(view, "catalogSearch", TextField.class);
            category = pageControl(view, "catalogCategory", ComboBox.class);
            count = pageControl(view, "catalogCount", Label.class);
            placeholder = pageControl(view, books.isEmpty() ? "catalogEmptyLabel" : "catalogNoMatchesLabel", Label.class);
            details = pageControl(view, "catalogDetails", Button.class);
            reserve = pageControl(view, "catalogReserve", Button.class);
            refresh = pageControl(view, "catalogRefresh", Button.class);
        } catch (IOException | ClassCastException unavailable) {
            setDataMode("CATALOG UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Catalog screen unavailable", null,
                    label("The catalog layout could not be loaded. Keep MemberCatalog.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            return;
        }
        TableView<ObservableList<String>> catalog = table(
                new String[]{"Book ID", "Title", "Author(s)", "Category", "ISBN", "Copies available"},
                books.stream().map(book -> new String[]{
                    String.valueOf(book.bookId()), display(book.title()), display(book.authors()),
                    categoryName(book), display(book.isbn()),
                    book.availableCopies() + " / " + book.totalCopies()
                }).toArray(String[][]::new));
        catalog.setPrefHeight(360);
        catalog.setPlaceholder(placeholder);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(
                FXCollections.observableArrayList(catalog.getItems()));
        SortedList<ObservableList<String>> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(catalog.comparatorProperty());
        catalog.setItems(sorted);

        TreeSet<String> categories = new TreeSet<>();
        books.forEach(book -> categories.add(categoryName(book)));
        List<String> categoryItems = new ArrayList<>();
        categoryItems.add("All categories");
        categoryItems.addAll(categories);
        category.setItems(FXCollections.observableArrayList(categoryItems));
        category.getSelectionModel().selectFirst();
        count.setText(books.size() + " books");
        Runnable filter = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            String selectedCategory = category.getValue();
            filtered.setPredicate(book -> ("All categories".equals(selectedCategory)
                    || Objects.equals(book.get(3), selectedCategory))
                    && book.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + books.size() + " books");
        };
        search.textProperty().addListener((observable, oldValue, newValue) -> filter.run());
        category.valueProperty().addListener((observable, oldValue, newValue) -> filter.run());

        details.setOnAction(event -> {
            ObservableList<String> selected = catalog.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            LibraryBook book = books.stream().filter(item -> String.valueOf(item.bookId()).equals(selected.get(0)))
                    .findFirst().orElse(null);
            if (book == null) return;
            showBookDetails(book);
        });
        details.disableProperty().bind(catalog.getSelectionModel().selectedItemProperty().isNull());
        reserve.setOnAction(event -> {
            ObservableList<String> selected = catalog.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            books.stream().filter(book -> book.bookId().equals(selected.get(0)))
                    .findFirst().ifPresent(this::reserveBook);
        });
        reserve.disableProperty().bind(catalog.getSelectionModel().selectedItemProperty().isNull());

        refresh.setOnAction(event -> retryCurrentPage());
        tableHost.getChildren().add(catalog);
        contentBox.getChildren().add(catalogPage);
    }

    private void showBookDetails(LibraryBook book) {
        final VBox detailsPage;
        try {
            URL location = getClass().getResource("/com/group1/bookit/MemberBookDetails.fxml");
            if (location == null) throw new IOException("MemberBookDetails.fxml is missing.");
            FXMLLoader view = new FXMLLoader(location);
            detailsPage = view.load();
            pageControl(view, "catalogBookTitle", Label.class).setText(display(book.title()));
            pageControl(view, "catalogBookAuthors", Label.class).setText("By " + display(book.authors()));
            pageControl(view, "catalogBookId", Label.class).setText(String.valueOf(book.bookId()));
            pageControl(view, "catalogBookCategory", Label.class).setText(categoryName(book));
            pageControl(view, "catalogBookIsbn", Label.class).setText(display(book.isbn()));
            pageControl(view, "catalogBookCopies", Label.class).setText(book.availableCopies() + " / " + book.totalCopies());
        } catch (IOException | ClassCastException unavailable) {
            detailDialog(root, "Book details unavailable",
                    note("The book details layout could not be loaded. Keep MemberBookDetails.fxml in the bookit resource folder, then Clean and Build."),
                    "Book details screen unavailable");
            return;
        }
        detailDialog(root, "Book details", detailsPage, "Read-only catalog record");
    }

    private void reserveBook(LibraryBook book) {
        if (saving.get() || disposed || refreshRequired || signedInUser == null) return;
        AuthenticatedUser actor = signedInUser;
        if (!confirm("Reserve a book", "Reserve \"" + display(book.title()) + "\"?\n\n"
                + "Your request joins this title's queue. The library must confirm availability; "
                + "this does not hold a particular copy or promise a collection deadline.")) return;
        if (disposed || actor != signedInUser) return;
        mutate("Reservation created", () -> reservations.reserve(actor, book.bookId()));
    }

    private record LoansData(List<LibraryLoan> loans, List<LoanRenewal> renewals) {}

    private LoansData readLoans(AuthenticatedUser actor) throws SQLException {
        // Two read snapshots are advisory only. The write rechecks all policy
        // rows and the exact old due date in a locked database transaction.
        return new LoansData(List.copyOf(repository.findMyLoans(actor)),
                List.copyOf(renewals.findMyRenewals(actor)));
    }

    private void loadLoans() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Your current borrowing and returned-book history. " + zoneNote());
        load("Loading your loans and renewal history…", () -> readLoans(actor), this::renderLoans);
    }

    private void renderLoans(LoansData data) {
        List<LibraryLoan> loans = data.loans();
        Instant now = Instant.now();
        List<LibraryLoan> active = loans.stream().filter(LibraryLoan::isOpen).toList();
        List<LibraryLoan> returned = loans.stream().filter(loan -> !loan.isOpen()).toList();
        long overdue = active.stream().filter(loan -> loan.isOverdue(now)).count();
        var renewed = data.renewals().stream().map(LoanRenewal::loanId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        final VBox loansPage;
        final VBox currentHost;
        final VBox returnedHost;
        final VBox renewalHost;
        final TextField search;
        final Button renew;
        final Button refresh;
        final Label placeholder;
        try {
            FXMLLoader view = memberLayout("MemberLoans.fxml");
            loansPage = view.getRoot();
            currentHost = pageControl(view, "memberCurrentLoansHost", VBox.class);
            returnedHost = pageControl(view, "memberReturnedLoansHost", VBox.class);
            renewalHost = pageControl(view, "memberRenewalHistoryHost", VBox.class);
            search = pageControl(view, "memberLoanSearch", TextField.class);
            renew = pageControl(view, "memberLoanRenew", Button.class);
            refresh = pageControl(view, "memberLoanRefresh", Button.class);
            placeholder = pageControl(view, active.isEmpty() ? "memberEmptyCurrentLoans" : "memberNoMatchingLoans", Label.class);
            pageControl(view, "memberActiveLoanCount", Label.class).setText(String.valueOf(active.size()));
            pageControl(view, "memberOverdueLoanCount", Label.class).setText(String.valueOf(overdue));
            pageControl(view, "memberReturnedLoanCount", Label.class).setText(String.valueOf(returned.size()));
            pageControl(view, "memberCurrentLoanSubtitle", Label.class).setText("Only records for your authenticated member ID are loaded. " + zoneNote());
            pageControl(view, "memberReturnedLoanSubtitle", Label.class).setText("Loans that have a recorded return time. " + zoneNote());
            pageControl(view, "memberRenewalHistorySubtitle", Label.class).setText("Saved receipts for your member account. " + zoneNote());
        } catch (IOException | ClassCastException unavailable) {
            loanSnapshot = null;
            setDataMode("LOANS UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Loans screen unavailable", null,
                    label("The loans layout could not be loaded. Keep MemberLoans.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            addRenewalNotice(false);
            return;
        }
        loanSnapshot = data;
        TableView<ObservableList<String>> current = table(
                new String[]{"Loan ID", "Book", "Copy barcode", "Due date", "Status", "Renewal"},
                active.stream().map(loan -> new String[]{
                    loan.loanId(), display(loan.bookTitle()), display(loan.barcode()),
                    format(loan.dueAt()), loan.status(now),
                    renewed.contains(loan.loanId()) ? "Used (1 / 1)" : "Not used"
                }).toArray(String[][]::new));
        current.setId("memberLoanTable");
        current.setPrefHeight(300);
        // Keep the decision-relevant deadline readable at the 1024px minimum
        // window width; long titles retain the shared full-text tooltip.
        double[] loanWidths = {60, 190, 100, 145, 80, 95};
        for (int i = 0; i < loanWidths.length; i++) {
            var column = current.getColumns().get(i);
            column.setPrefWidth(loanWidths[i]);
            column.setMinWidth(i == 1 ? 110 : loanWidths[i]);
            if (i != 1) column.setMaxWidth(loanWidths[i]);
        }
        current.setPlaceholder(placeholder);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(
                FXCollections.observableArrayList(current.getItems()));
        SortedList<ObservableList<String>> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(current.comparatorProperty());
        current.setItems(sorted);
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(loan -> loan.stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query)));
        });
        renew.setOnAction(event -> {
            ObservableList<String> selected = current.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            active.stream().filter(loan -> loan.loanId().equals(selected.get(0)))
                    .findFirst().ifPresent(loan -> renewLoan(data, loan));
        });

        current.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) ->
                renew.setDisable(selected == null || renewed.contains(selected.get(0))));
        refresh.setOnAction(event -> retryCurrentPage());
        currentHost.getChildren().add(current);
        returnedHost.getChildren().add(searchableTable("Search returned loans…",
                new String[]{"Loan ID", "Book", "Copy barcode", "Borrowed", "Due date", "Returned", "Status"},
                returned.stream().map(loan -> new String[]{
                    String.valueOf(loan.loanId()), display(loan.bookTitle()), display(loan.barcode()),
                    format(loan.borrowedAt()), format(loan.dueAt()), format(loan.returnedAt()), loan.status(now)
                }).toArray(String[][]::new), false));
        renewalHost.getChildren().add(table(new String[]{"Loan ID", "Book", "Copy barcode", "Previous due", "New due", "Renewed"},
                data.renewals().stream().map(receipt -> new String[]{
                    receipt.loanId(), display(receipt.bookTitle()), display(receipt.barcode()),
                    format(receipt.previousDueAt()), format(receipt.newDueAt()), format(receipt.renewedAt())
                }).toArray(String[][]::new)));
        contentBox.getChildren().add(loansPage);
        // Insert after the page so an acknowledged receipt remains the first card.
        addRenewalNotice(true);
    }

    private void renewLoan(LoansData expected, LibraryLoan loan) {
        if (!canRenew(expected, loan)) return;
        AuthenticatedUser actor = signedInUser;
        LocalDateTime proposed;
        try { proposed = loan.dueAt().plusDays(7); }
        catch (java.time.DateTimeException invalidDate) { return; }
        if (!confirm("Renew this loan", "Renew \"" + display(loan.bookTitle()) + "\" (loan "
                + loan.loanId() + ")?\n\nCurrent due: " + format(loan.dueAt())
                + "\nNew due: " + format(proposed) + "\n" + zoneNote()
                + "\n\nThis uses the one renewal allowed for this loan. It adds 7 days to the current due date, not today."
                + " The loan must still be before its deadline, you must have no positive unpaid fines,"
                + " and no other member may be waiting for this title. The database rechecks these rules.")) return;
        // A modal runs a nested event loop: a stale detached button must not
        // write after account replacement, navigation, logout, or another save.
        if (actor != signedInUser || !canRenew(expected, loan)) return;
        String request = UUID.randomUUID().toString();
        pendingRenewalRequest = request;
        pendingRenewalLoan = loan.loanId();
        renewalMessage = null;
        dataLoader.cancel();
        saving.set(true);
        setDataMode("SAVING… DO NOT CLOSE", "loading-badge");
        pageSubtitleLabel.setText("Saving your renewal. Wait for confirmation before leaving BookIt.");
        writes.load(() -> Objects.requireNonNull(
                renewals.renew(actor, loan.loanId(), loan.dueAt(), request), "renewal receipt"), receipt -> {
            if (disposed || actor != signedInUser) return;
            saving.set(false);
            renewalRefreshRequired = false;
            pendingRenewalRequest = null;
            pendingRenewalLoan = null;
            renewalRecoveryMessage = null;
            renewalMessage = renewalReceiptMessage(receipt);
            showPage("My loans");
        }, failure -> {
            if (disposed || actor != signedInUser) return;
            saving.set(false);
            logDatabaseFailure(failure);
            loanSnapshot = null;
            renewalRefreshRequired = true;
            renewalRecoveryMessage = failure instanceof LoanRenewalService.RejectedException rejected
                    ? rejected.getMessage()
                    : "The database could not confirm whether this renewal completed. Refresh your loans and renewal history before another renewal.";
            contentBox.getChildren().clear();
            showRenewalRecovery();
        });
    }

    private boolean canRenew(LoansData expected, LibraryLoan loan) {
        return !saving.get() && !disposed && signedInUser != null && !renewalRefreshRequired
                && "My loans".equals(currentPage) && loanSnapshot == expected && loan.isOpen()
                && expected.loans().contains(loan) && expected.renewals().stream()
                        .noneMatch(receipt -> receipt.loanId().equals(loan.loanId()));
    }

    private static String renewalReceiptMessage(LoanRenewal receipt) {
        return "Renewal saved for loan " + receipt.loanId() + ". New due: " + format(receipt.newDueAt())
                + ". Request " + receipt.requestId() + ".";
    }

    private void addRenewalNotice(boolean consume) {
        if (!"My loans".equals(currentPage) || renewalMessage == null) return;
        VBox noticeCard;
        try {
            FXMLLoader view = memberLayout("MemberLoanNotice.fxml");
            noticeCard = view.getRoot();
            pageControl(view, "memberLoanFeedback", Label.class).setText(renewalMessage);
        } catch (IOException | ClassCastException unavailable) {
            // A missing decoration must never hide an acknowledged renewal receipt.
            Label notice = label(renewalMessage, "section-note");
            notice.setId("memberLoanFeedback");
            noticeCard = section("Renewal result", null, notice);
        }
        contentBox.getChildren().add(0, noticeCard);
        if (consume) renewalMessage = null;
    }

    private void showRenewalRecovery() {
        setDataMode("RENEWAL NEEDS REFRESH", "offline-badge");
        pageSubtitleLabel.setText("Review your loans and saved renewal receipts before another renewal.");
        VBox recoveryCard;
        try {
            FXMLLoader view = memberLayout("MemberLoanRecovery.fxml");
            recoveryCard = view.getRoot();
            pageControl(view, "memberLoanRecoveryMessage", Label.class).setText(renewalRecoveryMessage);
            pageControl(view, "memberLoanRefresh", Button.class).setOnAction(event -> refreshRenewalAfterFailure());
        } catch (IOException | ClassCastException unavailable) {
            // Retain the safe read-only recovery route even if its template is missing.
            Button refresh = button("Refresh", "primary-button", this::refreshRenewalAfterFailure);
            refresh.setId("memberLoanRefresh");
            recoveryCard = section("Renewal needs review", null,
                    label(renewalRecoveryMessage, "error-note"),
                    note("No renewal is repeated automatically. Refresh only reads your current loans and renewal history, including the request receipt if the save completed."), refresh);
        }
        contentBox.getChildren().setAll(recoveryCard);
    }

    private void refreshRenewalAfterFailure() {
        if (saving.get() || disposed || signedInUser == null || !renewalRefreshRequired
                || !"My loans".equals(currentPage)) return;
        AuthenticatedUser actor = signedInUser;
        load("Checking your saved loans and renewal receipts…", () -> readLoans(actor), data -> {
            LoanRenewal saved = data.renewals().stream()
                    .filter(receipt -> Objects.equals(receipt.requestId(), pendingRenewalRequest)
                            && Objects.equals(receipt.loanId(), pendingRenewalLoan))
                    .findFirst().orElse(null);
            renewalMessage = saved == null
                    ? "No matching renewal receipt was found in the refreshed history. Review your current due dates before starting a new request."
                    : renewalReceiptMessage(saved);
            renewalRefreshRequired = false;
            pendingRenewalRequest = null;
            pendingRenewalLoan = null;
            renewalRecoveryMessage = null;
            pageSubtitleLabel.setText("Your current borrowing and returned-book history. " + zoneNote());
            renderLoans(data);
        });
    }

    private void clearRenewalState() {
        loanSnapshot = null;
        renewalRefreshRequired = false;
        pendingRenewalRequest = null;
        pendingRenewalLoan = null;
        renewalRecoveryMessage = null;
        renewalMessage = null;
    }

    private <T> void load(String loadingText, Callable<T> query, Consumer<T> render) {
        setDataMode("CONNECTING…", "loading-badge");
        contentBox.getChildren().setAll(section("Loading database records", null,
                label(loadingText, "loading-note")));
        addReservationNotice(false);
        addRenewalNotice(false);
        dataLoader.load(query, result -> {
            contentBox.getChildren().clear();
            setDataMode("DATABASE", "live-badge");
            render.accept(result);
        }, failure -> {
            logDatabaseFailure(failure);
            setDataMode("NOT CONNECTED", "offline-badge");
            contentBox.getChildren().setAll(section("Unable to load records", null,
                    label("The database could not supply this page. Check the connection and schema, then try again.",
                            "error-note"),
                    button("Retry", "primary-button", () -> {
                        retryCurrentPage();
                    }),
                    note("No sample records are substituted. This read did not change database records.")));
            addReservationNotice(false);
            addRenewalNotice(false);
        });
    }

    private static void logDatabaseFailure(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                // Do not print a URL, username, password, SQL text, or raw driver message.
                System.err.println("Library operation failed: SQLState=" + sql.getSQLState()
                        + ", vendorCode=" + sql.getErrorCode());
                return;
            }
        }
        System.err.println("Library operation failed. No unconfirmed records were displayed.");
    }

    private Button refreshButton() {
        return button("Refresh", "secondary-button", this::retryCurrentPage);
    }

    private void retryCurrentPage() {
        if (needsReservationRecovery()) refreshUnconfirmedAction();
        else if (renewalRefreshRequired && "My loans".equals(currentPage)) refreshRenewalAfterFailure();
        else if (profileRefreshRequired && "My profile & QR".equals(currentPage)) refreshProfileAfterSaveFailure();
        else showPage(currentPage);
    }

    private boolean needsReservationRecovery() {
        return refreshRequired && ("Reservations".equals(currentPage)
                || "Book catalog".equals(currentPage));
    }

    private void setDataMode(String text, String cssClass) {
        dataModeLabel.setText(text);
        dataModeLabel.getStyleClass().setAll("label", cssClass);
    }

    private void loadReservations() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Your title requests and reservation history. " + zoneNote());
        load("Loading your reservations…", () -> List.copyOf(reservations.findMyReservations(actor)),
                this::renderReservations);
    }

    private void renderReservations(List<LibraryReservation> records) {
        final VBox reservationPage;
        final TextField search;
        final Label count;
        final Button cancel;
        final Button refresh;
        final Button browse;
        final VBox tableHost;
        final Label placeholder;
        try {
            FXMLLoader view = memberLayout("MemberReservations.fxml");
            reservationPage = view.getRoot();
            pageControl(view, "memberReservationSubtitle", Label.class).setText(
                    "Only requests for your authenticated member ID are loaded. " + zoneNote());
            search = pageControl(view, "memberReservationSearch", TextField.class);
            count = pageControl(view, "memberReservationCount", Label.class);
            cancel = pageControl(view, "memberReservationCancel", Button.class);
            refresh = pageControl(view, "memberReservationRefresh", Button.class);
            browse = pageControl(view, "memberReservationBrowse", Button.class);
            tableHost = pageControl(view, "memberReservationTableHost", VBox.class);
            placeholder = pageControl(view, records.isEmpty() ? "memberReservationEmpty"
                    : "memberReservationNoMatches", Label.class);
        } catch (IOException | ClassCastException unavailable) {
            setDataMode("RESERVATIONS UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Reservations screen unavailable", null,
                    label("The reservation layout could not be loaded. Keep MemberReservations.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            addReservationNotice(false);
            return;
        }
        TableView<ObservableList<String>> requests = table(
                new String[]{"Reservation ID", "Book ID", "Book", "Requested", "Status", "Queue position"},
                records.stream().map(request -> new String[]{
                    request.reservationId(), request.bookId(), display(request.bookTitle()),
                    format(request.reservedAt()), display(request.status()),
                    request.queuePosition() == null ? "—" : String.valueOf(request.queuePosition())
                }).toArray(String[][]::new));
        requests.setPrefHeight(360);
        double[] widths = {110, 75, 210, 145, 95, 105};
        for (int i = 0; i < widths.length; i++) {
            var column = requests.getColumns().get(i);
            column.setPrefWidth(widths[i]);
            column.setMinWidth(i == 2 ? 115 : widths[i]);
            if (i != 2) column.setMaxWidth(widths[i]);
        }
        requests.setId("memberReservationTable");
        requests.setPlaceholder(placeholder);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(
                FXCollections.observableArrayList(requests.getItems()));
        SortedList<ObservableList<String>> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(requests.comparatorProperty());
        requests.setItems(sorted);
        count.setText(records.size() + " reservations");
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(request -> request.stream()
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + records.size() + " reservations");
        });
        cancel.setOnAction(event -> {
            ObservableList<String> selected = requests.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            records.stream().filter(request -> request.reservationId().equals(selected.get(0)))
                    .findFirst().filter(LibraryReservation::isActive).ifPresent(this::cancelReservation);
        });
        cancel.setDisable(true);
        requests.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) ->
                cancel.setDisable(selected == null || records.stream()
                        .filter(request -> request.reservationId().equals(selected.get(0)))
                        .noneMatch(LibraryReservation::isActive)));
        refresh.setOnAction(event -> retryCurrentPage());
        browse.setOnAction(event -> showPage("Book catalog"));
        tableHost.getChildren().setAll(requests);
        contentBox.getChildren().add(reservationPage);
        addReservationNotice(true);
    }

    private void addReservationNotice(boolean consume) {
        if (successMessage == null) return;
        VBox notice;
        try {
            FXMLLoader view = memberLayout("MemberReservationNotice.fxml");
            notice = view.getRoot();
            pageControl(view, "memberReservationFeedback", Label.class).setText(successMessage);
        } catch (IOException | ClassCastException unavailable) {
            notice = section("Saved successfully", null, label(successMessage, "section-note"));
        }
        contentBox.getChildren().add(0, notice);
        if (consume) successMessage = null;
    }

    private void cancelReservation(LibraryReservation request) {
        if (saving.get() || disposed || refreshRequired || signedInUser == null || !request.isActive()) return;
        AuthenticatedUser actor = signedInUser;
        if (!confirm("Cancel reservation", "Cancel your reservation for \"" + display(request.bookTitle())
                + "\" (request " + request.reservationId() + ")?\n\nIt will leave the active queue and remain in your history.")) return;
        if (disposed || actor != signedInUser) return;
        mutate("Reservation cancelled", () -> {
            reservations.cancel(actor, request.reservationId());
            return request.reservationId();
        });
    }

    private boolean confirm(String title, String question) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.CANCEL, ButtonType.OK);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        if (root.getScene() != null && root.getScene().getWindow() != null)
            dialog.initOwner(root.getScene().getWindow());
        return dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void mutate(String completedText, Callable<String> write) {
        if (saving.get() || disposed || refreshRequired || signedInUser == null) return;
        dataLoader.cancel();
        saving.set(true);
        setDataMode("SAVING… DO NOT CLOSE", "loading-badge");
        pageSubtitleLabel.setText("Saving your reservation. Wait for the result before leaving or closing BookIt.");
        writes.load(write, reservationId -> {
            if (disposed) return;
            saving.set(false);
            successMessage = completedText + ". Request " + reservationId + ".";
            showPage("Reservations");
        }, error -> {
            if (disposed) return;
            saving.set(false);
            logDatabaseFailure(error);
            successMessage = null;
            refreshRequired = true;
            unconfirmedExplanation = error instanceof IllegalArgumentException && error.getMessage() != null
                    ? error.getMessage() : "The database could not confirm the reservation action.";
            showPage("Reservations");
        });
    }

    private void showUnconfirmedAction() {
        setDataMode("ACTION NOT CONFIRMED · REFRESH", "offline-badge");
        pageSubtitleLabel.setText("Refresh your saved reservations before taking another reservation action.");
        try {
            FXMLLoader view = memberLayout("MemberReservationRecovery.fxml");
            pageControl(view, "memberReservationRecoveryMessage", Label.class).setText(unconfirmedExplanation);
            pageControl(view, "memberReservationRecoveryRefresh", Button.class)
                    .setOnAction(event -> retryCurrentPage());
            contentBox.getChildren().setAll((VBox) view.getRoot());
        } catch (IOException | ClassCastException unavailable) {
            // Keep explicit read-only recovery available even if a layout is missing.
            contentBox.getChildren().setAll(section("Reservation action not confirmed", null,
                    label(unconfirmedExplanation, "error-note"),
                    note("A connection can fail after a change is saved. Do not submit the action again until you refresh and check your history. No automatic retry is performed."),
                    refreshButton()));
        }
    }

    private void refreshUnconfirmedAction() {
        if (saving.get() || disposed || signedInUser == null) return;
        // Only this explicit refresh can unlock reservation actions after an
        // unknown transaction result. Navigation and failed reads cannot do so.
        showPage("Reservations");
        AuthenticatedUser actor = signedInUser;
        load("Checking your saved reservations before another action…",
                () -> List.copyOf(reservations.findMyReservations(actor)), records -> {
                    refreshRequired = false;
                    unconfirmedExplanation = null;
                    pageSubtitleLabel.setText("Your title requests and reservation history. " + zoneNote());
                    renderReservations(records);
                });
    }

    private void showFines() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Your recorded fines and payment status. " + zoneNote());
        load("Loading your fine records and recorded balances…",
                () -> List.copyOf(fines.findMyFines(actor)), this::renderFines);
    }

    @SuppressWarnings("unchecked")
    private void renderFines(List<LibraryFine> records) {
        final VBox finesPage;
        final FXMLLoader view;
        final TextField search;
        final ComboBox<String> statusFilter;
        final Label count;
        final Button details;
        final Button refresh;
        final VBox tableHost;
        final Label placeholder;
        try {
            view = memberLayout("MemberFines.fxml");
            finesPage = view.getRoot();
            search = pageControl(view, "memberFineSearch", TextField.class);
            statusFilter = pageControl(view, "memberFineStatus", ComboBox.class);
            count = pageControl(view, "memberFineCount", Label.class);
            details = pageControl(view, "memberFineDetails", Button.class);
            refresh = pageControl(view, "memberFineRefresh", Button.class);
            tableHost = pageControl(view, "memberFineTableHost", VBox.class);
            placeholder = pageControl(view, records.isEmpty() ? "memberFineEmpty"
                    : "memberFineNoMatches", Label.class);
            for (String id : List.of("memberFineOutstanding", "memberFinePaid", "memberFineWaived"))
                pageControl(view, id, Label.class);
        } catch (IOException | ClassCastException unavailable) {
            setDataMode("FINES UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Fines screen unavailable", null,
                    label("The fines layout could not be loaded. Keep MemberFines.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            return;
        }
        BigDecimal unpaid = BigDecimal.ZERO.setScale(2);
        BigDecimal paid = BigDecimal.ZERO.setScale(2);
        BigDecimal waived = BigDecimal.ZERO.setScale(2);
        for (LibraryFine fine : records) {
            if (fine.isUnpaid()) unpaid = unpaid.add(fine.amount());
            else if (fine.isPaid()) paid = paid.add(fine.amount());
            else if (fine.isWaived()) waived = waived.add(fine.amount());
        }
        ((Label) view.getNamespace().get("memberFineOutstanding")).setText(unpaid.toPlainString());
        ((Label) view.getNamespace().get("memberFinePaid")).setText(paid.toPlainString());
        ((Label) view.getNamespace().get("memberFineWaived")).setText(waived.toPlainString());

        TableView<ObservableList<String>> history = table(
                new String[]{"Fine ID", "Loan ID", "Book", "Copy barcode", "Amount", "Status", "Paid at"},
                records.stream().map(fine -> new String[]{
                    fine.fineId(), fine.loanId(), display(fine.bookTitle()), display(fine.barcode()),
                    fine.amount().toPlainString(), fine.paymentStatus(), format(fine.paidAt())
                }).toArray(String[][]::new));
        history.setId("memberFineTable");
        history.setPrefHeight(340);
        history.getColumns().get(0).setPrefWidth(85);
        history.getColumns().get(1).setPrefWidth(85);
        history.getColumns().get(2).setPrefWidth(220);
        history.getColumns().get(0).setComparator(Comparator.comparingLong(value -> Long.parseLong(value.toString())));
        history.getColumns().get(1).setComparator(Comparator.comparingLong(value -> Long.parseLong(value.toString())));
        history.getColumns().get(4).setComparator(Comparator.comparing(value -> new BigDecimal(value.toString())));
        history.getColumns().get(6).setComparator(Comparator.comparing(value ->
                "—".equals(value.toString()) ? LocalDateTime.MIN
                        : LocalDateTime.parse(value.toString(), DATE_TIME)));
        history.setPlaceholder(placeholder);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(
                FXCollections.observableArrayList(history.getItems()));
        SortedList<ObservableList<String>> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(history.comparatorProperty());
        history.setItems(sorted);

        statusFilter.setItems(FXCollections.observableArrayList("All statuses", "UNPAID", "PAID", "WAIVED"));
        statusFilter.getSelectionModel().selectFirst();
        count.setText(records.size() + " fine records");
        Runnable filter = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            String selectedStatus = statusFilter.getValue();
            filtered.setPredicate(values -> ("All statuses".equals(selectedStatus)
                    || Objects.equals(values.get(5), selectedStatus))
                    && values.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + records.size() + " fine records");
            history.getSelectionModel().clearSelection();
        };
        search.textProperty().addListener((observable, before, after) -> filter.run());
        statusFilter.valueProperty().addListener((observable, before, after) -> filter.run());

        details.setOnAction(event -> {
            ObservableList<String> selected = history.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            records.stream().filter(fine -> fine.fineId().equals(selected.get(0)))
                    .findFirst().ifPresent(this::showFineDetails);
        });
        details.disableProperty().bind(history.getSelectionModel().selectedItemProperty().isNull());
        refresh.setOnAction(event -> retryCurrentPage());
        tableHost.getChildren().setAll(history);
        contentBox.getChildren().add(finesPage);
    }

    private void showFineDetails(LibraryFine fine) {
        final VBox detailsPage;
        try {
            FXMLLoader view = memberLayout("MemberFineDetails.fxml");
            detailsPage = view.getRoot();
            pageControl(view, "memberFineIdentity", Label.class).setText(fine.fineId() + " / " + fine.loanId());
            pageControl(view, "memberFineBook", Label.class).setText(display(fine.bookTitle()));
            pageControl(view, "memberFineBarcode", Label.class).setText(display(fine.barcode()));
            pageControl(view, "memberFineAmount", Label.class).setText(fine.amount().toPlainString());
            pageControl(view, "memberFinePaymentStatus", Label.class).setText(fine.paymentStatus());
            pageControl(view, "memberFineBorrowed", Label.class).setText(format(fine.borrowedAt()));
            pageControl(view, "memberFineDue", Label.class).setText(format(fine.dueAt()));
            pageControl(view, "memberFineReturned", Label.class).setText(format(fine.returnedAt()));
            pageControl(view, "memberFinePaidAt", Label.class).setText(format(fine.paidAt()));
            pageControl(view, "memberFineZoneNote", Label.class).setText(
                    zoneNote() + ". No fine creation date is stored in the current schema.");
        } catch (IOException | ClassCastException unavailable) {
            detailDialog(root, "Fine details unavailable",
                    note("The fine details layout could not be loaded. Keep MemberFineDetails.fxml in the bookit resource folder, then Clean and Build."),
                    "Fine details screen unavailable");
            return;
        }
        detailDialog(root, "Fine details", detailsPage,
                "Read-only record from your account · Refresh for updates");
    }

    private void showProfile() {
        AuthenticatedUser actor = signedInUser;
        pageSubtitleLabel.setText("Your account, contact details, and personal library QR.");
        load("Loading your member profile…", () -> profiles.findMyProfile(actor),
                profile -> renderProfile(profile, null));
    }

    private void renderProfile(MemberProfile profile, String message) {
        final VBox profilePage;
        final TextField fullName;
        final TextField email;
        final TextField phone;
        final Label feedback;
        final Button save;
        final Button reset;
        final Button refresh;
        final Button changePassword;
        final Button generateQr;
        try {
            URL location = getClass().getResource("/com/group1/bookit/MemberProfile.fxml");
            if (location == null) throw new IOException("MemberProfile.fxml is missing.");
            FXMLLoader view = new FXMLLoader(location);
            profilePage = view.load();
            pageControl(view, "profileUsername", Label.class).setText(display(profile.username()));
            pageControl(view, "profileMemberId", Label.class).setText(display(profile.userId()));
            pageControl(view, "profileRole", Label.class).setText(display(profile.role()));
            pageControl(view, "profileStatus", Label.class).setText(display(profile.status()));
            pageControl(view, "profileJoined", Label.class).setText(format(profile.joinedAt()));
            pageControl(view, "profileUpdated", Label.class).setText(format(profile.updatedAt()));
            pageControl(view, "profileLastLogin", Label.class).setText(format(profile.lastLoginAt()));
            fullName = pageControl(view, "profileFullName", TextField.class);
            email = pageControl(view, "profileEmail", TextField.class);
            phone = pageControl(view, "profilePhone", TextField.class);
            feedback = pageControl(view, "profileFeedback", Label.class);
            save = pageControl(view, "profileSave", Button.class);
            reset = pageControl(view, "profileReset", Button.class);
            refresh = pageControl(view, "profileRefresh", Button.class);
            changePassword = pageControl(view, "memberChangePassword", Button.class);
            generateQr = pageControl(view, "profileGenerateQr", Button.class);
        } catch (IOException | ClassCastException unavailable) {
            profileSnapshot = null;
            profileDirty = false;
            setDataMode("PROFILE UNAVAILABLE", "offline-badge");
            contentBox.getChildren().setAll(section("Profile screen unavailable", null,
                    label("The profile layout could not be loaded. Keep MemberProfile.fxml in the bookit resource folder, then Clean and Build.", "error-note")));
            return;
        }
        profileSnapshot = profile;
        profileDirty = false;
        pageSubtitleLabel.setText("Your account, contact details, and personal library QR. " + zoneNote());
        fullName.setText(profile.fullName() == null ? "" : profile.fullName());
        email.setText(profile.email() == null ? "" : profile.email());
        phone.setText(profile.phone() == null ? "" : profile.phone());
        feedback.setText(message == null ? "" : message);
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        Runnable draftChanged = () -> {
            if (disposed || profileSnapshot != profile || !"My profile & QR".equals(currentPage)) return;
            profileDirty = !sameProfileDetails(profile, fullName.getText(), email.getText(), phone.getText());
            save.setDisable(!profileDirty || profileRefreshRequired);
            reset.setDisable(!profileDirty);
            feedback.setText("");
        };
        fullName.textProperty().addListener((observable, before, after) -> draftChanged.run());
        email.textProperty().addListener((observable, before, after) -> draftChanged.run());
        phone.textProperty().addListener((observable, before, after) -> draftChanged.run());
        reset.setOnAction(event -> {
            fullName.setText(profile.fullName());
            email.setText(profile.email());
            phone.setText(profile.phone() == null ? "" : profile.phone());
            draftChanged.run();
        });
        save.setOnAction(event -> saveProfile(profile, fullName.getText(), email.getText(), phone.getText(), feedback));
        refresh.setOnAction(event -> retryCurrentPage());
        changePassword.setOnAction(event -> openPasswordChange());
        generateQr.setOnAction(event -> UserQrDialog.show(root.getScene().getWindow(), signedInUser));
        contentBox.getChildren().add(profilePage);
    }

    private FXMLLoader memberLayout(String filename) throws IOException {
        URL location = getClass().getResource("/com/group1/bookit/" + filename);
        if (location == null) throw new IOException(filename + " is missing.");
        FXMLLoader view = new FXMLLoader(location);
        view.load();
        return view;
    }

    private static <T> T pageControl(FXMLLoader view, String name, Class<T> type) throws IOException {
        Object value = view.getNamespace().get(name);
        if (!type.isInstance(value)) throw new IOException("The member page layout is missing " + name + ".");
        return type.cast(value);
    }

    private static boolean sameProfileDetails(MemberProfile profile, String name, String email, String phone) {
        return Objects.equals(normalizedProfileValue(profile.fullName()), normalizedProfileValue(name))
                && Objects.equals(normalizedProfileValue(profile.email()), normalizedProfileValue(email))
                && Objects.equals(normalizedProfileValue(profile.phone()), normalizedProfileValue(phone));
    }

    private static String normalizedProfileValue(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }

    private void saveProfile(MemberProfile expected, String name, String email, String phone, Label feedback) {
        if (saving.get() || disposed || signedInUser == null || profileRefreshRequired
                || profileSnapshot != expected || !profileDirty) return;
        try {
            MemberProfileService.validateDetails(name, email, phone);
        } catch (MemberProfileService.RejectedException rejected) {
            feedback.getStyleClass().setAll("label", "error-note");
            feedback.setText(profileRejectionMessage(rejected.reason()));
            return;
        }
        AuthenticatedUser actor = signedInUser;
        dataLoader.cancel();
        saving.set(true);
        feedback.getStyleClass().setAll("label", "section-note");
        feedback.setText("Saving your contact details…");
        setDataMode("SAVING… DO NOT CLOSE", "loading-badge");
        pageSubtitleLabel.setText("Saving your profile. Wait for confirmation before leaving BookIt.");
        writes.load(() -> profiles.updateMyProfile(actor, expected, name, email, phone), saved -> {
            if (disposed) return;
            saving.set(false);
            profileDirty = false;
            profileRefreshRequired = false;
            profileRecoveryMessage = null;
            contentBox.getChildren().clear();
            setDataMode("DATABASE", "live-badge");
            renderProfile(saved, "Profile saved.");
        }, failure -> {
            if (disposed) return;
            saving.set(false);
            logDatabaseFailure(failure);
            if (failure instanceof MemberProfileService.RejectedException rejected
                    && (rejected.reason() == MemberProfileService.Reason.INVALID_DETAILS
                        || rejected.reason() == MemberProfileService.Reason.EMAIL_IN_USE)) {
                setDataMode("NOT SAVED", "offline-badge");
                pageSubtitleLabel.setText("Your changes were not saved. Correct the contact details and try again.");
                feedback.getStyleClass().setAll("label", "error-note");
                feedback.setText(profileRejectionMessage(rejected.reason()));
                return;
            }
            profileRefreshRequired = true;
            profileDirty = false;
            profileSnapshot = null;
            profileRecoveryMessage = failure instanceof MemberProfileService.RejectedException rejected
                    ? profileRejectionMessage(rejected.reason())
                    : "The database could not confirm whether your profile save completed. Refresh and inspect the saved details before editing again.";
            contentBox.getChildren().clear();
            showProfileRecovery();
        });
    }

    private static String profileRejectionMessage(MemberProfileService.Reason reason) {
        return switch (reason) {
            case INVALID_DETAILS -> "Enter a full name (1–150 characters), a valid email (up to 255 characters), and an optional phone (up to 30 characters). Control characters are not allowed.";
            case EMAIL_IN_USE -> "That email address is already in use. Choose another email address.";
            case STALE_PROFILE -> "Your profile changed since this form loaded. Your changes were not saved. Refresh to review the newer details before editing.";
            case ACCOUNT_UNAVAILABLE -> "Your member account is no longer available for this session. Sign out and contact an administrator, or sign in again if your username changed.";
        };
    }

    private void showProfileRecovery() {
        setDataMode("PROFILE NEEDS REFRESH", "offline-badge");
        pageSubtitleLabel.setText("Review your saved profile before another profile change.");
        try {
            FXMLLoader view = memberLayout("MemberProfileRecovery.fxml");
            pageControl(view, "profileRecoveryMessage", Label.class).setText(profileRecoveryMessage);
            pageControl(view, "profileRefresh", Button.class)
                    .setOnAction(event -> refreshProfileAfterSaveFailure());
            contentBox.getChildren().setAll((VBox) view.getRoot());
        } catch (IOException | ClassCastException unavailable) {
            Button refresh = button("Refresh", "primary-button", this::refreshProfileAfterSaveFailure);
            refresh.setId("profileRefresh");
            contentBox.getChildren().setAll(section("Profile save needs review", null,
                    label(profileRecoveryMessage, "error-note"),
                    note("No save is repeated automatically. Refresh only reads the current database record; it does not resubmit your previous changes."), refresh));
        }
    }

    private void refreshProfileAfterSaveFailure() {
        if (saving.get() || disposed || signedInUser == null || !profileRefreshRequired
                || !"My profile & QR".equals(currentPage)) return;
        AuthenticatedUser actor = signedInUser;
        load("Checking your saved profile before another change…", () -> profiles.findMyProfile(actor), profile -> {
            profileRefreshRequired = false;
            profileRecoveryMessage = null;
            renderProfile(profile, "Saved details reloaded. Review them before making another change.");
        });
    }

    private boolean discardProfileDraft() {
        if (!profileDirty || !"My profile & QR".equals(currentPage)) return true;
        AuthenticatedUser identity = signedInUser;
        MemberProfile original = profileSnapshot;
        if (!confirm("Discard unsaved profile changes?", "Your contact details have not been saved. Discard these changes and continue?")) return false;
        if (disposed || signedInUser != identity || profileSnapshot != original) return false;
        profileDirty = false;
        return true;
    }

    private void clearProfileState() {
        profileSnapshot = null;
        profileDirty = false;
        profileRefreshRequired = false;
        profileRecoveryMessage = null;
    }

    private void openPasswordChange() {
        if (saving.get() || disposed || signedInUser == null) return;
        Window window = root.getScene() == null ? null : root.getScene().getWindow();
        if (!(window instanceof Stage)) return;
        AuthenticatedUser identity = signedInUser;
        if (SessionManager.getCurrentUser() != identity) {
            dispose();
            root.setDisable(true);
            return;
        }
        boolean hadDraft = profileDirty;
        if (!discardProfileDraft()) return;
        if (disposed || signedInUser != identity || SessionManager.getCurrentUser() != identity || root.getScene() == null
                || root.getScene().getWindow() != window) return;
        if (hadDraft && profileSnapshot != null) {
            MemberProfile original = profileSnapshot;
            contentBox.getChildren().clear();
            setDataMode("DATABASE", "live-badge");
            renderProfile(original, null);
        }
        dataLoader.cancel();
        saving.set(true);
        try {
            PasswordChangeDialog.show(window, identity, ignored -> {},
                    () -> finishPasswordSession(identity, window));
        } finally {
            if (!disposed) saving.set(false);
        }
    }

    private void finishPasswordSession(AuthenticatedUser expected, Window window) {
        if (disposed || signedInUser != expected) return;
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

    private static String categoryName(LibraryBook book) {
        return book.category() == null || book.category().isBlank() ? "Uncategorized" : book.category();
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private static String format(LocalDateTime utcDateTime) {
        return utcDateTime == null ? "—" : utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME);
    }

    private static String zoneNote() {
        return "Times shown in " + DISPLAY_ZONE.getId() + " (converted from UTC)";
    }

    private Label note(String text) {
        return label(text, "section-note");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        if (saving.get() || disposed) return;
        if (!discardProfileDraft()) return;
        Navigation.logoutFrom(event);
    }
}