//AdminDashboardController(AdminService service, AttendanceService attendanceService, FineAutomationService fineAutomation) = This method helps set up the main control center (the dashboard) for the library administrator.;
//How it works: It brings together all the underlying tools needed for the screen to function, such as the tool that tracks attendance, the tool that calculates late fines, and the general administrative tool.
//Why is it used: It acts as a builder, ensuring that before the screen is even shown to the user, all of its required background helpers are ready to do their jobs.
//
//initialize() = This method helps set up the visual look of the dashboard right after it is opened.;
//How it works: It places the logo on the screen, creates all the buttons for the side menu (like "Overview", "Books & copies", "Members", etc.), and tells the screen to ignore mouse clicks if the system is busy saving data.
//Why is it used: It ensures the dashboard has all its buttons and menus properly built and visible the moment the administrator opens the app.
//
//guardClosing(Window window) = This method helps prevent the application from being closed at a dangerous time.;
//How it works: It places a "guard" on the application window. If the administrator clicks the "X" to close the program while the computer is busy saving data, the guard blocks the close and displays a warning to wait.
//Why is it used: Closing a program exactly when it is writing data can corrupt or break the library records. This protects the system's storage.
//
//setUser(AuthenticatedUser user) = This method helps securely log the administrator into their workspace.;
//How it works: It checks to make sure the person logging in actually has an "ADMIN" role. Once verified, it resets any old data, displays the admin's username on the screen, and loads their starting page.
//Why is it used: It ensures that only authorized staff can see the library records, and it customizes the screen with their specific account details.
//
//dispose() = This method helps safely shut down the dashboard and clean up its memory.;
//How it works: It stops any background tasks (like the automatic late-fee checker), clears all the text and buttons off the screen, and logs the user out visually by changing their name to "Signed out."
//Why is it used: It prevents the computer from wasting memory and ensures that once an admin logs out, the next person using the computer cannot see their sensitive library data.
//
//selectPage(String page) = This method helps switch between different sections of the dashboard, like going from "Members" to "Fines".;
//How it works: It highlights the button of the page you clicked on the menu, changes the title at the top of the screen, clears out the old content, and asks the database to retrieve the information for the new page you selected.
//Why is it used: It allows the administrator to easily navigate the application without having to open a dozen different windows.
//
//renderPage(String page, AdminData.Page data) = This method helps draw the actual tables and buttons for the page you just selected.;
//How it works: It takes the raw data retrieved from the system's storage and turns it into readable tables (like a spreadsheet), summary cards, and action buttons (like "Add book" or "Issue loan") depending on which page you are on.
//Why is it used: It acts as the visual decorator, transforming hidden computer data into a user-friendly interface that the administrator can click and read.
//
//bookForm(Map<String, String> selected) = This method helps open a pop-up window to add a new book or edit an existing one.;
//How it works: It generates a form asking for details like the book's title, ISBN, author, and publisher. If you are editing an existing book, it automatically fills in the blanks with the book's current details.
//Why is it used: It provides a standardized, error-free way for the administrator to type in information about the library's book catalog.
//
//createMember() = This method helps open a pop-up window to register a new library member.;
//How it works: It creates a fill-in-the-blank form asking for a new username, email, full name, phone number, and a starting password for the new member.
//Why is it used: It allows the admin to quickly create new user accounts so people can start borrowing books.
//
//memberStatus(Map<String, String> row) = This method helps turn a member's account on or off.;
//How it works: It looks at the member you selected. If their account is active, it asks if you want to disable it. If it is disabled, it asks if you want to activate it.
//Why is it used: It allows the library to temporarily block a user from borrowing books (for example, if they lost their card or have too many late fees) without deleting their borrowing history.
//
//issueLoan() = This method helps check a book out to a library member.;
//How it works: It opens a form where the admin can scan a book's barcode and type in a member's ID. It also calculates a suggested due date 14 days into the future.
//Why is it used: It is the main tool used at the circulation desk to let people actually take books home.
//
//showBarcodeLabel(String copyId) = This method helps view and print a scannable sticker for a physical book.;
//How it works: It asks the database for the specific barcode image connected to a book copy and opens a window displaying that label so it can be printed.
//Why is it used: It allows the library to create physical stickers to paste on book covers, making them easy to scan with a barcode reader later.
//
//lookupBarcode(boolean returning) = This method helps find a book in the system just by scanning its sticker.;
//How it works: It pops up a scanning window. If the admin is just searching, it shows the book's details. If the admin is returning a book, it finds the open loan attached to that barcode and asks the admin to confirm the return.
//Why is it used: It saves time. Instead of typing out long book titles, the admin can just scan the book to figure out what it is or to quickly process a return.
//
//reservation(Map<String, String> row, String status) = This method helps update the status of a book someone reserved online.;
//How it works: It asks the admin to confirm if a reservation should be changed (for example, marking it as "READY" to be picked up or "CANCELLED").
//Why is it used: It helps the library staff keep track of which books have been pulled from the shelves and are waiting behind the desk for members to collect.
//
//confirmReservationPickup(Map<String, String> row) = This method helps officially hand over a reserved book to a member.;
//How it works: It opens a final confirmation screen where the admin scans the specific physical book they are handing to the user and sets the final due date.
//Why is it used: It transitions a book from being merely "reserved" to being actively "borrowed," starting the official timer for when the book is due back.
//
//saveReservationPickup(AuthenticatedUser identity, Window window, Map<String, String> request) = This method helps save the final handover of a reserved book into the system.;
//How it works: It freezes the screen, sends the pickup details to the database to create a new loan, and then displays a success message with the new Loan ID. If it fails, it warns the admin.
//Why is it used: It ensures the library's digital records exactly match the physical reality of the book leaving the building.
//
//pickupCompletion(AuthenticatedUser identity, Window window) = This method helps double-check that nothing weird happened while saving a pickup.;
//How it works: It checks if the admin accidentally logged out or closed the window while the computer was busy saving the book pickup.
//Why is it used: It acts as a safety net, preventing the system from crashing if the screen it is trying to update no longer exists.
//
//pickupContext(AuthenticatedUser identity, Window window) = This method helps ensure the admin is on the right screen before saving a pickup.;
//How it works: It verifies that the dashboard is currently open, not currently saving something else, and specifically on the "Reservations" page.
//Why is it used: It stops the system from accidentally processing a book pickup if the admin has already navigated away to a completely different task.
//
//unpaid(Map<String, String> row) = This method helps quickly check if a specific fine has not been paid yet.;
//How it works: It looks at a fine record and returns a simple "true" if it says "UNPAID" or "false" if it says something else.
//Why is it used: It is used to decide whether to light up or gray out buttons. (e.g., You cannot click the "Record Payment" button on a fine that is already paid).
//
//fineForm(Map<String, String> row) = This method helps create or change a monetary fine.;
//How it works: If creating a new fine, it asks for the Loan ID, the money amount, and the reason. If adjusting an old fine, it pulls up the existing amount and asks the admin to type the new amount and why they are changing it.
//Why is it used: It allows library staff to manually penalize members for things like damaged books, or to reduce a late fee if the member has a good excuse.
//
//waive(Map<String, String> row) = This method helps forgive a fine so the member does not have to pay it.;
//How it works: It asks the admin to type a reason for forgiving the fine and then updates the record in the system to say "WAIVED" instead of "UNPAID".
//Why is it used: It gives administrators the power to show leniency or fix computer mistakes without messing up the financial accounting.
//
//showCopies(String bookId) = This method helps view all the physical, individual copies of a specific book title.;
//How it works: It changes the screen to show a list of every physical copy the library owns for that one book, showing if they are on the shelf, borrowed, or lost.
//Why is it used: Because a library might own 5 copies of "Harry Potter," this allows the admin to manage each physical book separately.
//
//copyForm(String bookId, Map<String, String> selected) = This method helps register a brand-new physical book or update its status (like marking it "LOST").;
//How it works: It pops up a form asking where the book is located (the shelf) and its physical condition.
//Why is it used: It allows the library to accurately track their physical inventory and automatically generates a unique barcode for every new book purchased.
//
//correctVisit(Map<String, String> row) = This method helps fix a mistake in a member's library attendance record.;
//How it works: It opens a form displaying the time a member walked into the library and the time they left. The admin can correct these times and must provide a reason for the change.
//Why is it used: Sometimes people forget to scan out when they leave the building. This lets the admin manually fix the logbook to keep accurate visitor counts.
//
//localInput(String time) = This method helps slightly reformat a time stamp so it can be typed into a form easily.;
//How it works: It takes a date and time and replaces the empty space between them with a "T", which is the format the computer's calendar box expects.
//Why is it used: It ensures that when an admin edits a time, the computer's digital clock recognizes the text correctly.
//
//reports() = This method helps generate and print detailed spreadsheets of library activity.;
//How it works: It shows a page where the admin can pick a report type (like "Borrowing activity"), choose a start and end date, and click "Generate". It then displays the data and offers buttons to export it to a CSV file or print it as a PDF.
//Why is it used: It is essential for library management to track statistics, calculate revenue from fines, and do inventory audits.
//
//form(String title, String explanation, AdminService.Action action, Field... fields) = This method helps easily create any generic pop-up window that asks the user to type in data.;
//How it works: It takes a list of blanks (like text boxes or drop-downs), displays them on the screen, waits for the admin to fill them out, and then automatically sends the typed answers to the database.
//Why is it used: It saves programmers from writing the same "pop up a window and save data" code dozens of times. They just use this one universal method.
//
//confirmAction(String title, String question, AdminService.Action action, Map<String, String> values) = This method helps ask "Are you sure?" before doing something important.;
//How it works: It pops up a small warning box with an OK and Cancel button. If the admin clicks OK, it proceeds to send the change to the system's storage.
//Why is it used: It prevents accidental clicks from permanently altering or deleting library records.
//
//mutate(String title, Callable write) = This method helps safely apply a permanent change to the system's storage.;
//How it works: It freezes the admin's screen and puts up a "Loading" badge so they can't click anything else. It performs the background database task. If it works, it unfreezes the screen and shows a success message. If it fails, it shows an error message.
//Why is it used: It guarantees that the system doesn't try to process two overlapping actions at the exact same time, which could corrupt the data.
//
//load(String message, Callable query, Consumer render) = This method helps retrieve information from the database without freezing the application.;
//How it works: It shows a loading message on the screen, goes into the background to fetch data from the server, and once it has the data, it draws it onto the screen.
//Why is it used: Fetching data over a network takes time. This method ensures the app doesn't look like it "crashed" while it is thinking and waiting for data.
//
//refreshCurrent() = This method helps reload the page you are currently looking at.;
//How it works: It checks what page you are currently on (for example, the Copies list or the Overview) and triggers a command to fetch the newest data for it from the server.
//Why is it used: If someone else is using the system on another computer, clicking refresh updates your screen so you can see any changes they just made.
//
//refreshButton() = This method helps create the visual "Refresh" button seen on the screen.;
//How it works: It simply creates a clickable gray button labeled "Refresh" and attaches the refreshCurrent() instructions to it.
//Why is it used: It creates the actual button the administrator clicks to update their screen.
//
//setMode(String text, String css) = This method helps change the little status badge at the top of the dashboard.;
//How it works: It updates the text (like "DATABASE" or "NOT CONNECTED") and changes its color (like green for live, red for offline) based on what the system is currently doing.
//Why is it used: It gives the administrator a quick visual indicator of whether the app is working normally, loading data, or experiencing an error.
//
//failureMessage(Throwable error) = This method helps translate complicated computer errors into plain English.;
//How it works: It looks at the technical reason why the program failed. If a database file is missing, it tells the user exactly which file is missing. Otherwise, it gives a generic but polite "check your connection" message.
//Why is it used: Regular users do not understand complex computer error codes. This translates the code into a helpful instruction they can actually read.
//
//logFailure(Throwable failure) = This method helps keep a hidden technical diary of errors.;
//How it works: When an error happens, it quietly prints the exact technical database error codes into a background file, while intentionally hiding passwords or sensitive data.
//Why is it used: It allows the IT team or programmers to fix bugs later by reading the diary, without confusing the librarian with technical jargon on their screen.
//
//grid(AdminData.Table data, String prompt) = This method helps build a searchable spreadsheet table.;
//How it works: It takes raw columns and rows of data, formats them into a neat visual table, and attaches a search bar above it. Typing in the search bar instantly filters the rows to show only what matches.
//Why is it used: It makes finding a specific book, member, or fine incredibly fast, rather than forcing the admin to scroll through thousands of records manually.
//
//selected(String title, Grid grid, Predicate eligible, Runnable action) = This method helps create a button that only turns on when a specific row is clicked.;
//How it works: It makes a button (like "Edit book"). It constantly watches the table. If you click a row, and that row meets the rules (for example, it must be an unpaid fine), the button lights up and becomes clickable.
//Why is it used: It prevents the admin from trying to do impossible things, like trying to edit a book without actually selecting a book first.
//
//toolbar(Node... nodes) = This method helps organize buttons into a neat row.;
//How it works: It takes a list of buttons and groups them horizontally with even spacing between them.
//Why is it used: It keeps the top of the dashboard looking clean and organized, rather than having buttons scattered randomly across the screen.
//
//metrics(Map<String, String> metrics) = This method helps build the colorful summary cards on the Overview page.;
//How it works: It takes summary data (like "Total Members: 50" or "Overdue Books: 12") and puts them into colorful, stylized rectangular cards lined up next to each other.
//Why is it used: It provides a quick, easy-to-read "at-a-glance" summary of the library's health as soon as the admin logs in.
//
//formField(String title, Node input) = This method helps pair a text label with a blank input box.;
//How it works: It simply takes a word (like "Title") and stacks it neatly on top of an empty text box where the user is supposed to type.
//Why is it used: It keeps forms looking perfectly aligned and ensures every blank box clearly explains what you are supposed to type into it.
//
//pageDescription(String page) = This method helps display a helpful hint sentence at the top of each page.;
//How it works: It checks the name of the page you are on (like "Fines") and returns a specific sentence explaining how to use that page.
//Why is it used: It acts as built-in training for new administrators, giving them quick instructions right on the screen so they don't have to read a separate manual.
//
//absent(String value) = This method helps check if a piece of data is missing, blank, or filled with placeholder text.;
//How it works: It looks at a word and returns "true" if the word is entirely blank, says "Not provided", says "None", or is just a dash "-".
//Why is it used: It helps the system accurately recognize when a record truly has no data, preventing it from treating the word "None" as someone's actual name or address.
//
//clean(String value) = This method helps tidy up text before it is put into an editing form.;
//How it works: It runs the text through the absent() check. If the text is a placeholder like "None" or "Uncategorized", it erases it completely so the text box appears empty.
//Why is it used: When an admin goes to edit a form, it is much easier for them to see an empty box rather than having to manually delete the word "Not provided" before typing.
//
//owner() = This method helps figure out which exact window on the computer screen the dashboard is currently living in.;
//How it works: It traces the visual components backward until it finds the master application window that holds them.
//Why is it used: When the program wants to show a pop-up warning, it needs to know exactly which window to lock down and float the pop-up over.
//
//queueAutomationStart() = This method helps turn on the automatic background timer that checks for late fees.;
//How it works: When the dashboard finishes loading, it starts an invisible stopwatch. Every 60 seconds, it tells the system to quickly scan all borrowed books to see if any have become overdue, and then restarts the stopwatch.
//Why is it used: It ensures that late fines are calculated silently and automatically in the background without the librarian having to manually push a button every day.
//
//activeDashboard() = This method helps confirm that the dashboard is open, visible, and actively being used.;
//How it works: It checks a list of conditions: Is the admin logged in? Is the window actually showing on the computer screen? Has it not been closed yet?
//Why is it used: It stops background processes (like checking late fines) from running uselessly if the administrator has minimized the app, logged out, or closed the window.
//
//hasOwnedWindow() = This method helps check if there is currently a pop-up window covering the dashboard.;
//How it works: It scans all the screens currently drawn by the app to see if any of them are floating directly on top of the main dashboard.
//Why is it used: It prevents background tasks from running and forcefully refreshing the screen while the admin is right in the middle of typing into a pop-up form.
//
//runFineCheck(boolean manual) = This method helps execute the task of hunting down overdue books and applying fines.;
//How it works: It freezes the screen briefly, scans the database for any books past their due date, adds a 20 PHP charge if needed, and prints out a summary of how many fines were added.
//Why is it used: It is the engine that actually enforces the library's late-fee rules, ensuring penalties are calculated fairly and accurately based on the calendar.
//
//pauseAfterFineFailure(Throwable error) = This method helps pause the automatic late-fee checker if something breaks.;
//How it works: If the database suddenly disconnects while calculating fines, this stops the 60-second timer, changes the status badge to red, and displays a message telling the admin to check the connection.
//Why is it used: It prevents the system from running in an endless loop of crashing every 60 seconds if the internet or database goes down.
//
//automationPage() = This method helps draw the "Automation" settings page.;
//How it works: It shows the admin whether the automatic late-fee checker is currently running or paused, shows when the last check happened, and provides buttons to manually run the check or pause it.
//Why is it used: It gives the human administrator oversight and manual control over the automated robot tasks running in the background.
//
//accountSecurity() = This method helps draw the page where the administrator manages their own login credentials.;
//How it works: It displays the admin's username and role, and puts a large "Change password" button on the screen.
//Why is it used: It gives administrators a dedicated, safe place to secure their own account without accidentally altering library member accounts.
//
//openPasswordChange() = This method helps launch the pop-up window to change a password.;
//How it works: It verifies that the admin is still logged in securely, freezes the dashboard, and launches a secure pop-up dialog that handles typing the old and new passwords.
//Why is it used: It initiates the high-security process of updating passwords while locking down the rest of the application so nothing else can interfere.
//
//finishPasswordSession(AuthenticatedUser expected, Window window) = This method helps kick the user out of the app after they successfully change their password.;
//How it works: Once a password change is complete, it forces the dashboard to close, clears the session data, and takes the user back to the main login screen to log in with their new password.
//Why is it used: It is a critical security measure. Forcing a log out proves that the new password actually works and invalidates the old secure session immediately.
//
//confirm(String title, String question) = This method helps create a standard Yes/No confirmation pop-up.;
//How it works: It draws a small alert box over the dashboard containing a specific question and OK/Cancel buttons. It waits until the user clicks one, then returns "true" if they clicked OK.
//Why is it used: It is a reusable tool that forces the user to pause and think before confirming a destructive or permanent action.
//
//notice(String title, String message) = This method helps pop up a simple "FYI" message box on the screen.;
//How it works: It draws a small alert box with an "OK" button that displays a message (like "Saved successfully") and disappears when the user clicks OK.
//Why is it used: It is the easiest way to give the administrator direct feedback so they know a task was successfully completed or if they need to be aware of a rule.
//
//handleLogout(ActionEvent event) = This method helps trigger the sign-out process when the user clicks the Logout button.;
//How it works: It checks to make sure the app isn't currently in the middle of saving data. If it is safe, it triggers the application's main navigation system to log out and go back to the login screen.
//Why is it used: It allows the admin to safely end their session and lock the dashboard when they step away from the desk.



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
    private final FineAutomationService fineAutomation;
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

    /** Controller-factory seam for offline tests; construction never opens a connection. */
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
                        () -> confirmAction("Record return", "Record this copy as returned now? Any automatic overdue fine is finalized using the return date in Asia/Manila.",
                                RETURN_LOAN, Map.of("loanId", grid.selected().get("Loan ID")))));
            }
            case "Reservations" -> {
                toolbar.getChildren().add(selected("Mark ready", grid,
                        row -> "PENDING".equalsIgnoreCase(row.get("Status")), () -> reservation(grid.selected(), "READY")));
                Button pickup = selected("Confirm pickup & issue loan", grid,
                        row -> "READY".equalsIgnoreCase(row.get("Status")),
                        () -> confirmReservationPickup(grid.selected()));
                pickup.setId("reservationConfirmPickup");
                toolbar.getChildren().add(pickup);
                toolbar.getChildren().add(selected("Cancel reservation", grid,
                        row -> List.of("PENDING", "READY").contains(row.get("Status").toUpperCase()),
                        () -> reservation(grid.selected(), "CANCELLED")));
            }
            case "Fines" -> {
                toolbar.getChildren().add(button("Charge fine", "primary-button", () -> fineForm(null)));
                toolbar.getChildren().add(selected("Adjust charge", grid, this::unpaid, () -> fineForm(grid.selected())));
                toolbar.getChildren().add(selected("Record payment", grid, this::unpaid,
                        () -> confirmAction("Record payment", "Confirm PHP " + grid.selected().get("Amount")
                                + " was received outside BookIt. Automatic fines require the book to be returned first. If the amount changed, refresh before confirming payment. This does not charge a bank account.",
                                RECORD_PAYMENT, Map.of("fineId", grid.selected().get("Fine ID"),
                                        "expectedAmount", grid.selected().get("Amount")))));
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
                                + "\n\nConfirm the physical book has been received. Any automatic overdue fine is finalized using this return date in Asia/Manila.",
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

    private void confirmReservationPickup(Map<String, String> row) {
        if (!"READY".equalsIgnoreCase(row.get("Status"))) return;
        AuthenticatedUser identity = actor;
        Window window = owner();
        if (!pickupContext(identity, window)) return;
        // Capture the selected request, not a selection that can change inside a
        // dialog's nested event loop. Read-only fields are presentation only;
        // the service rechecks the real reservation/member/title and queue.
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
            // Never accept edited identity fields, even through programmatic
            // changes to a read-only control. Bind this write to this selection.
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
            pageContent.getChildren().setAll(section("Pickup needs review", null,
                    label(explanation, "error-note"), refreshButton()));
            notice("Pickup not confirmed", explanation);
        });
    }

    private boolean pickupCompletion(AuthenticatedUser identity, Window window) {
        if (disposed) return false;
        if (actor != identity || SessionManager.getCurrentUser() != identity || owner() != window
                || !window.isShowing() || root.getScene() == null || root.getScene().getRoot() != root) {
            // A forced owner/session replacement is not a reason to display an
            // old result or re-enable the stale dashboard. Do not log out the
            // replacement session; only discard this old controller.
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

    private void queueAutomationStart() {
        // Navigation supplies the account before it commits SessionManager and mounts
        // the scene. Never write during FXML loading or setUser itself.
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
        // Skip checks during forms, confirmations, password dialogs, camera windows,
        //A popup window shouldn't let the user save or change data if it messes up a change that is already happening in the background.
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
//                Close or terminate old, replaced sessions instead of leaving them active or open.
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
        pageSubtitle.setText("Overdue fines are calculated from saved dates, not repeatedly added each time a check runs.");
        setMode(automationPaused ? "CHECKS PAUSED" : "ADMIN-APP CHECKS", automationPaused ? "offline-badge" : "live-badge");
        Label feedback = label(automationMessage, automationPaused ? "error-note" : "section-note");
        feedback.setId("automationFeedback");
        Button run = button("Run checks now", "primary-button", () -> {
            if (saving.get() || disposed || actor == null) return;
            if (automationPaused && !confirm("Run fresh fine checks",
                    "Review refreshed fine records first. Run a fresh, cumulative assessment from saved database state and resume checks every 60 seconds?")) return;
            runFineCheck(true);
        });
        run.setId("automationRun");
        Button pause = button("Pause periodic checks", "secondary-button", () -> {
            if (saving.get() || disposed) return;
            automationPaused = true;
            automationMessage = "Periodic checks paused in this dashboard. Run checks now resumes them. Returns and payments still finalize eligible automatic fines inside their own transactions.";
            navigation.get("Automation").setText("Automation · paused");
            pageContent.getChildren().clear();
            automationPage();
        });
        pause.setId("automationPause");
        pause.setDisable(automationPaused);
        String checkedAt = lastFineCheck == null ? "No completed cycle in this dashboard session."
                : "Last completed: " + lastFineCheck.completedAt().atZone(java.time.ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.of("Asia/Manila"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", java.util.Locale.ENGLISH))
                        + " (Asia/Manila). Counts may overlap when a newly created charge is also finalized.";
        pageContent.getChildren().addAll(
                section("Automatic overdue fines", "PHP20 per calendar day · Asia/Manila · zero grace days · no maximum",
                        label("Due October 24: October 25 = PHP20, October 26 = PHP40. The return date is the last chargeable date. Returning on the due date costs nothing.", "section-note"),
                        label("Runs on dashboard opening, then every 60 seconds while this signed-in admin dashboard is open and not busy. Missed days are caught up next time. It does not run when all admin apps are closed.", "section-note"),
                        toolbar(run, pause), feedback, label(checkedAt, "muted")),
                section("Safeguards", "Each loan is saved in its own transaction; this is not online payment processing.",
                        label("Manual charges, paid fines, waived fines, and manually overridden automatic charges are never overwritten. An admin adjustment opts that charge out of future automated changes.", "section-note"),
                        label("Automatic fines must be finalized by a return before payment. Payment rechecks the amount you confirmed. A manual charge already attached to a loan takes precedence because the existing schema permits one fine per loan.", "section-note")),
                section("Member renewals", "One renewal per loan, requested by the member; renewals are never automatic.",
                        label("Adds seven days to the current due date. Must be before the deadline, with no positive unpaid fines and no other member waiting for that title. The database rechecks all conditions.", "section-note")));
    }

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
