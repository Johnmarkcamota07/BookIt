/*
show(Window owner, BookCopyLabel label) = This method helps open up the preview window to view and print a book's barcode sticker.;
How it works: It verifies that it is running on the main visual thread, builds the barcode label dialog using the provided book details, and pauses to wait until the administrator closes the window. If anything goes wrong, it pops up an error message explaining the problem.
Why is it used: It gives the administrator a quick, convenient way to preview and print physical barcode stickers for books right from their screen.

showError(Window owner, String header, String details) = This method helps display an error pop-up box if the barcode fails to load.;
How it works: It creates a standard error alert box, links it to the main window so it floats properly on top, and displays the specific error title and detailed instructions before waiting for the user to click OK.
Why is it used: It prevents the application from silently crashing if a barcode image is corrupted or missing files, telling the admin clearly what went wrong.

BarcodeLabelDialog(Window owner, BookCopyLabel copy) = This method helps prepare the barcode label dialog using the default image-saving tool.;
How it works: It takes the book's copy details and passes them along to the main builder constructor, telling it to use the standard file-saving tool to handle PNG images.
Why is it used: It acts as a convenient shortcut so programmers don't have to specify the file-saving tool manually every single time they want to open a label preview.

BarcodeLabelDialog(Window owner, BookCopyLabel copy, PngWriter pngWriter) = This method helps build and configure the entire barcode label preview window.;
How it works: It checks that the book copy details are valid, generates the scannable barcode image from the text code, converts it into a digital image, creates a pop-up window, loads the visual design blueprints, and starts a background helper thread to handle printing and saving without freezing the screen.
Why is it used: It acts as the master constructor for the barcode dialog, setting up all the necessary graphics, buttons, and background workers.

window() = This method helps fetch the master window stage for the barcode dialog.;
How it works: It simply hands back the internal JavaFX Stage object that represents the dialog window.
Why is it used: It allows other parts of the program or automated tests to directly access or check the window if needed.

loadScene() = This method helps load the visual layout blueprint for the barcode label window.;
How it works: It reads the "BarcodeLabel.fxml" design file, connects it to this controller class, and turns the text blueprint into actual visual elements like buttons and labels.
Why is it used: It separates the visual design of the window from the computer code, making the app easier to build and maintain.

initialize() = This method helps set up the text and barcode image on the preview screen right after it opens.;
How it works: It pulls the book's title, ID number, and shelf location, displays them on the screen, generates the visual barcode picture, and places it neatly into the preview box.
Why is it used: It ensures that the dialog window displays the correct book information the exact moment the administrator opens it.

handleClose() = This method helps close the barcode dialog when the administrator clicks the close button.;
How it works: It checks to make sure the computer isn't currently busy saving or printing a file. If it's free, it closes the window.
Why is it used: It prevents the window from closing abruptly while an export task is halfway done, protecting the app from glitches.

view(String filename) = This method helps find the visual design files stored inside the application package.;
How it works: It searches the application's resources folder for a specific file name (like "BarcodeLabel.fxml") and returns its digital address (URL).
Why is it used: It helps the program easily locate its design templates regardless of where the app is installed on the computer.

labelNode(BookCopyLabel copy, Image barcode) = This method helps build an exact, unscaled print layout for the physical sticker.;
How it works: It loads a special print template ("CopyBarcodeLabel.fxml"), attaches the book details and barcode image to it, and formats it to the exact physical dimensions of a standard barcode label (100 × 50 mm).
Why is it used: It creates a dedicated, printer-ready version of the label that won't get distorted or messed up by screen zoom levels or preview adjustments.

PrintLabelController(BookCopyLabel copy, Image barcode) = This method helps prepare the helper controller specifically designed for printing labels.;
How it works: It saves the book copy details and the barcode image into temporary storage inside the print controller object.
Why is it used: It supplies the necessary data to the print layout template so it knows what words and barcode lines to print on the paper.

initialize() = This method helps fill in the text and images on the printable label template.;
How it works: It takes the book title, ID, barcode text, shelf location, and barcode picture, places them into their respective slots on the label template, and forces the label to resize to the exact physical printing dimensions.
Why is it used: It ensures the printable sticker is structured correctly before it is sent to the physical printer or PDF generator.

fitsPrintableArea(double width, double height) = This method helps check if the selected piece of paper is big enough to print a barcode label.;
How it works: It compares the width and height of the paper's printable area against the required label size (100 × 50 mm), returning "true" only if the paper is large enough.
Why is it used: It prevents the printer from chopping off the edges of the sticker or failing because someone accidentally tried to print a label on tiny paper.

printLabel() = This method helps send the barcode label to a physical printer or a Save-to-PDF file.;
How it works: It opens the computer's standard print dialog window. Once the admin picks a printer and confirms, it checks if the paper is big enough. If it is, it sets the print color to black-and-white, generates the printable label, and sends it off to the printer in the background without freezing the app.
Why is it used: It is the main bridge between the software and the physical printer, allowing the library to actually produce sticky barcode labels for their books.

chooseSave() = This method helps open a file browser so the administrator can save the barcode as an image file.;
How it works: It opens a "Save File" window, suggests a clean file name based on the book's copy ID, restricts the choice to PNG images, and if the user picks a folder and clicks save, it sends the path off to be saved.
Why is it used: It allows the librarian to save the barcode as a digital picture file if they want to print it later or use it in other documents.

saveTo(Path destination) = This method helps save the barcode picture into a specific file location on the computer.;
How it works: It locks the screen into a "busy" state, updates the status text to say "Saving...", and hands the job over to the background worker to write the PNG image data into the chosen file path.
Why is it used: It safely handles the file-writing process in the background, ensuring the app stays responsive while the computer writes the image data to the hard drive.

writePng(Path destination, byte[] bytes) = This method helps actually write the raw image bytes into a new PNG file.;
How it works: It double-checks that the chosen file ends with ".png". Then, it opens a secure file-writing channel and dumps the raw image data into the new file, ensuring it fails if a file with that name already exists so it never accidentally overwrites anything.
Why is it used: It provides a safe, file-writing tool that prevents accidental data loss by refusing to overwrite existing files.

run(Callable operation, Consumer success, Consumer failure) = This method helps run heavy tasks in the background without freezing the screen.;
How it works: It submits a task (like printing or saving a file) to a background worker thread. When the task finishes successfully, it safely updates the screen on the main JavaFX thread. If it fails, it catches the error and displays a failure message instead.
Why is it used: It prevents the application from locking up or looking like it crashed while waiting for a printer or hard drive to respond.

later(Runnable completion) = This method helps safely schedule a screen update after a background task finishes.;
How it works: It safely passes instructions back to the main visual thread, unlocking the buttons and running the completion code only if the window hasn't been closed in the meantime.
Why is it used: It prevents application crashes that happen when background threads try to mess with visual buttons and labels directly.

updateControls() = This method helps turn buttons on or off depending on whether the computer is busy.;
How it works: If the app is currently busy printing or saving, it disables the print and save buttons so the user can't click them twice. Once it finishes, it turns them back on.
Why is it used: It prevents double-clicking glitches that could confuse the app or create duplicate print jobs.

dispose() = This method helps clean up memory and shut down background tools when the barcode window closes.;
How it works: It marks the window as disposed and shuts down the background worker service so it stops running in the background.
Why is it used: It prevents memory leaks and ensures the computer's processor isn't left running invisible background tasks after the user closes the window.

requireCopy(BookCopyLabel copy) = This method helps verify that a book copy has valid details and an encodable barcode.;
How it works: It checks that the copy ID, book ID, title, and barcode are all present, not blank, and that the barcode consists only of safe, printable characters under 50 letters long.
Why is it used: It acts as a safety gate, throwing an error immediately if someone tries to print a label for a broken or incomplete book record.

displayShelf(String shelf) = This method helps format the shelf location text for the label.;
How it works: It checks if the shelf text is missing or blank. If it is, it returns the words "Not recorded"; otherwise, it returns the actual shelf name.
Why is it used: It keeps the printed sticker looking neat and professional, avoiding weird blank spaces or null words on the physical label.

requireFxThread() = This method helps enforce the rule that graphical windows must only be opened on the main visual thread.;
How it works: It checks if the current code is running on the JavaFX UI thread. If it isn't, it immediately throws an error.
Why is it used: It prevents severe application crashes that happen when background workers try to draw windows or manipulate graphics.

 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.google.zxing.WriterException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.print.PageLayout;
import javafx.print.PageRange;
import javafx.print.PrintColor;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Prints/exports a registered-copy snapshot. No SQL, authentication or record writes. */
public final class BarcodeLabelDialog {
    @FunctionalInterface
    interface PngWriter { void write(Path destination, byte[] image) throws Exception; }
    // JavaFX print dimensions are points (1/72 inch), not screen DPI pixels.
    static final double LABEL_WIDTH = 100.0 * 72.0 / 25.4;
    static final double LABEL_HEIGHT = 50.0 * 72.0 / 25.4;
    private final BookCopyLabel copy;
    private final byte[] png;
    private final Image barcode;
    private final Stage stage;
    @FXML private Button print;
    @FXML private Button save;
    @FXML private Button close;
    @FXML private Label status;
    @FXML private Label bookTitle;
    @FXML private Label copyMetadata;
    @FXML private Group previewGroup;
    @FXML private Scale previewScale;
    private final ExecutorService worker;
    private final PngWriter pngWriter;
    private boolean busy;
    private boolean disposed;

    /** Modal preview for a label snapshot already fetched by the administrator. */
    public static void show(Window owner, BookCopyLabel label) {
        requireFxThread();
        try {
            new BarcodeLabelDialog(owner, label).stage.showAndWait();
        } catch (IOException | UncheckedIOException error) {
            showError(owner, "The barcode label screen could not be loaded.",
                    "Check that BarcodeLabel.fxml, CopyBarcodeLabel.fxml and their CSS files are included in the application resources, then Clean and Build.");
        } catch (IllegalArgumentException | WriterException error) {
            showError(owner, "The stored barcode could not be rendered.",
                    "A label requires an existing copy with a barcode of 1 to 50 non-space printable ASCII characters. Refresh the copy record and check its barcode.");
        }
    }

    private static void showError(Window owner, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Copy label unavailable");
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }

    /** Package-level seam: no file chooser, printer discovery, or database access. */
    BarcodeLabelDialog(Window owner, BookCopyLabel copy) throws IOException, WriterException {
        this(owner, copy, BarcodeLabelDialog::writePng);
    }

    BarcodeLabelDialog(Window owner, BookCopyLabel copy, PngWriter pngWriter) throws IOException, WriterException {
        requireFxThread();
        this.copy = requireCopy(copy);
        this.pngWriter = Objects.requireNonNull(pngWriter, "PNG writer");
        png = CodeGenerator.copyBarcodePng(copy.barcode());
        barcode = new Image(new ByteArrayInputStream(png));
        if (barcode.isError()) throw new IOException("The barcode preview could not be loaded.");
        stage = new Stage();
        if (owner == null) stage.initModality(Modality.APPLICATION_MODAL);
        else {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle("BookIt - Copy barcode label");
        stage.setMinWidth(640);
        stage.setMinHeight(560);
        Scene scene = loadScene();
        // Create output resources only after both FXML templates loaded successfully.
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "bookit-copy-label-output");
            thread.setDaemon(true);
            return thread;
        });
        stage.setOnCloseRequest(event -> { if (busy) event.consume(); });
        stage.setOnHidden(event -> dispose());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                if (!busy) stage.close();
            }
        });
        stage.setScene(scene);
    }

    Stage window() { return stage; }

    private Scene loadScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(view("BarcodeLabel.fxml"));
        // FXML names the class, while this factory preserves its selected-copy snapshot.
        loader.setControllerFactory(type -> {
            if (type != BarcodeLabelDialog.class) {
                throw new IllegalStateException("BarcodeLabel.fxml must use BarcodeLabelDialog.");
            }
            return this;
        });
        return new Scene(loader.load());
    }

    @FXML
    private void initialize() {
        bookTitle.setText(copy.title());
        copyMetadata.setText("Book ID " + copy.bookId() + "  ·  Copy ID " + copy.copyId()
                + "  ·  Shelf: " + displayShelf(copy.shelf()));
        Pane previewLabel = labelNode(copy, barcode);
        previewLabel.getTransforms().add(previewScale);
        previewGroup.getChildren().setAll(previewLabel);
    }

    @FXML
    private void handleClose() {
        if (!busy) stage.close();
    }

    private static URL view(String filename) throws IOException {
        URL location = BarcodeLabelDialog.class.getResource("/com/group1/bookit/" + filename);
        if (location == null) throw new IOException("Missing barcode label resource: " + filename);
        return location;
    }

    /** Unscaled, self-contained print node: black on white at exactly 100 × 50 mm. */
    static Pane labelNode(BookCopyLabel copy, Image barcode) {
        requireFxThread();
        requireCopy(copy);
        Objects.requireNonNull(barcode, "barcode image");
        try {
            FXMLLoader loader = new FXMLLoader(view("CopyBarcodeLabel.fxml"));
            loader.setController(new PrintLabelController(copy, barcode));
            return loader.load();
        } catch (IOException error) {
            throw new UncheckedIOException("The printable barcode label layout could not be loaded.", error);
        }
    }

    /** Supplies only snapshot values to the fixed-size FXML print template. */
    private static final class PrintLabelController {
        private final BookCopyLabel copy;
        private final Image barcode;
        @FXML private Pane copyLabel;
        @FXML private Label copyLabelTitle;
        @FXML private Label copyLabelIdentity;
        @FXML private Label copyLabelCode;
        @FXML private Label copyLabelShelf;
        @FXML private ImageView copyLabelBars;

        PrintLabelController(BookCopyLabel copy, Image barcode) {
            this.copy = copy;
            this.barcode = barcode;
        }

        @FXML private void initialize() {
            copyLabelTitle.setText(copy.title());
            copyLabelIdentity.setText("Book " + copy.bookId() + "  |  Copy " + copy.copyId());
            copyLabelCode.setText(copy.barcode());
            copyLabelShelf.setText("Shelf: " + displayShelf(copy.shelf()));
            copyLabelBars.setImage(barcode);
            if (copy.barcode().length() > 35) copyLabelCode.getStyleClass().add("print-long-code");
            copyLabel.resize(LABEL_WIDTH, LABEL_HEIGHT);
        }
    }

    /** Printer PageLayout reports printable dimensions in points, not pixels. */
    static boolean fitsPrintableArea(double width, double height) {
        return Double.isFinite(width) && Double.isFinite(height)
                && width >= LABEL_WIDTH && height >= LABEL_HEIGHT;
    }

    @FXML
    private void printLabel() {
        if (busy || disposed) return;
        PrinterJob job;
        try { job = PrinterJob.createPrinterJob(); }
        catch (RuntimeException error) {
            status.setText("Printer setup could not be opened. Check your system printer settings; the label remains available here.");
            return;
        }
        if (job == null) {
            status.setText("No printer is available. Configure a printer or Print-to-PDF destination, or save the barcode PNG.");
            return;
        }
        try {
            if (!job.showPrintDialog(stage)) { job.cancelJob(); return; }
            if (disposed) { job.cancelJob(); return; }
            PageLayout layout = job.getJobSettings().getPageLayout();
            if (!fitsPrintableArea(layout.getPrintableWidth(), layout.getPrintableHeight())) {
                job.cancelJob();
                status.setText("This paper's printable area is too small for a 100 × 50 mm label. Select larger paper or adjust the printer margins. The label was not shrunk or cropped.");
                return;
            }
            job.getJobSettings().setJobName("BookIt - copy " + copy.copyId());
            job.getJobSettings().setPrintColor(PrintColor.MONOCHROME);
            job.getJobSettings().setPageRanges(new PageRange(1, 1));
            Pane page = labelNode(copy, barcode);
            // A distinct unscaled node prevents preview zoom or later UI updates
            // from changing the physical printed label.
            new Scene(new Group(page));
            page.applyCss(); page.layout();
            busy = true;
            status.setText("Sending one label to the selected printer. Please wait…");
            updateControls();
            run(() -> {
                try {
                    if (!job.printPage(layout, page)) { job.cancelJob(); return false; }
                    boolean sent = job.endJob();
                    if (!sent) job.cancelJob();
                    return sent;
                } catch (RuntimeException error) { job.cancelJob(); throw error; }
            }, sent -> status.setText(sent
                    ? "The label was sent to the printer queue. For Print-to-PDF, finish saving in the system dialog."
                    : "Printing was cancelled or the printer did not accept the label. Your preview remains available."),
                    error -> status.setText("The print job could not be confirmed. Check the printer queue before printing again. Your preview remains available."));
        } catch (RuntimeException error) {
            job.cancelJob();
            status.setText("The print dialog could not prepare this label. Check the selected printer and paper size; your preview remains available.");
        }
    }

    @FXML
    private void chooseSave() {
        if (busy || disposed) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save copy barcode PNG");
        chooser.setInitialFileName("bookit-copy-" + copy.copyId().replaceAll("[^A-Za-z0-9_-]", "_") + ".png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        var selected = chooser.showSaveDialog(stage);
        if (selected != null) saveTo(selected.toPath());
    }

    /** Testable export entry point; only the chooser runs on the UI thread. */
    void saveTo(Path destination) {
        requireFxThread();
        if (busy || disposed) return;
        busy = true;
        status.setText("Saving the barcode PNG…");
        updateControls();
        run(() -> { pngWriter.write(destination, png); return true; },
                saved -> status.setText("Barcode PNG saved successfully (1200 × 240 pixels)."),
                error -> status.setText("The barcode PNG could not be saved. Choose a writable location and a NEW .png filename; existing files are never overwritten. Your preview remains available."));
    }

    static void writePng(Path destination, byte[] bytes) throws IOException {
        if (destination == null || destination.getFileName() == null
                || !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("Choose a PNG filename.");
        }
        try (OutputStream output = Files.newOutputStream(destination,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            output.write(bytes);
        }
    }

    private <T> void run(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        worker.submit(() -> {
            try {
                T result = operation.call();
                later(() -> { success.accept(result); updateControls(); });
            } catch (Exception error) {
                later(() -> { failure.accept(error); updateControls(); });
            }
        });
    }

    private void later(Runnable completion) {
        try {
            Platform.runLater(() -> {
                if (disposed) return;
                busy = false;
                completion.run();
            });
        } catch (IllegalStateException ignored) { /* Application is closing. */ }
    }

    private void updateControls() {
        print.setDisable(busy || disposed);
        save.setDisable(busy || disposed);
        close.setDisable(busy);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        worker.shutdown();
    }

    private static BookCopyLabel requireCopy(BookCopyLabel copy) {
        if (copy == null || copy.copyId() == null || copy.copyId().isBlank()
                || copy.bookId() == null || copy.bookId().isBlank()
                || copy.title() == null || copy.title().isBlank()
                || copy.barcode() == null || !copy.barcode().matches("[\\x21-\\x7E]{1,50}")) {
            throw new IllegalArgumentException("A registered copy with an encodable stored barcode is required.");
        }
        return copy;
    }

    private static String displayShelf(String shelf) { return shelf == null || shelf.isBlank() ? "Not recorded" : shelf; }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open copy-label windows on the JavaFX thread.");
    }
}
