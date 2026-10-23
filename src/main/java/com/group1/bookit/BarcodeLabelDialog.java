/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.google.zxing.WriterException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageRange;
import javafx.print.PrintColor;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private final Button print = new Button("Print label / Save PDF");
    private final Button save = new Button("Save barcode PNG");
    private final Button close = new Button("Close");
    private final Label status = text("Ready. This label uses the registered copy's exact stored barcode.", "muted");
    private final ExecutorService worker;
    private final PngWriter pngWriter;
    private boolean busy;
    private boolean disposed;

    /** Modal preview for a label snapshot already fetched by the administrator. */
    public static void show(Window owner, BookCopyLabel label) {
        requireFxThread();
        try {
            new BarcodeLabelDialog(owner, label).stage.showAndWait();
        } catch (IllegalArgumentException | IOException | WriterException error) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) alert.initOwner(owner);
            alert.setTitle("Copy label unavailable");
            alert.setHeaderText("The stored barcode could not be rendered.");
            alert.setContentText("A label requires an existing copy with a barcode of 1 to 50 non-space printable ASCII characters. Refresh the copy record and check its barcode.");
            alert.showAndWait();
        }
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
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "bookit-copy-label-output");
            thread.setDaemon(true);
            return thread;
        });
        if (owner == null) stage.initModality(Modality.APPLICATION_MODAL);
        else {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle("BookIt - Copy barcode label");
        stage.setMinWidth(640);
        stage.setMinHeight(560);
        print.setId("barcodePrint");
        save.setId("barcodeSave");
        close.setId("barcodeClose");
        status.setId("barcodeStatus");

        Pane previewLabel = labelNode(copy, barcode);
        previewLabel.getTransforms().add(new Scale(2, 2, 0, 0));
        Group enlarged = new Group(previewLabel);
        StackPane preview = new StackPane(enlarged);
        preview.setId("barcodeLabelPreview");
        preview.setPadding(new Insets(12));
        preview.setMinHeight(LABEL_HEIGHT * 2 + 24);
        preview.setStyle("-fx-background-color: #e7edf4; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6;");
        Label title = text(copy.title(), "section-title");
        title.setId("barcodeBookTitle");
        Label identity = text("Book ID " + copy.bookId() + "  ·  Copy ID " + copy.copyId()
                + "  ·  Shelf: " + displayShelf(copy.shelf()), "muted");
        identity.setId("barcodeCopyMetadata");
        VBox content = new VBox(12,
                text("COPY BARCODE LABEL", "stat-label"), title, identity, preview,
                text("Print size: 100 × 50 mm. One label per page; choose the number of copies in the print dialog. Select a printer or your system's Print-to-PDF destination.", "section-note"),
                new HBox(10, print, save),
                text("Save barcode PNG exports the barcode image only (1200 × 240 pixels), without the title or shelf text.", "muted"),
                status);
        content.setPadding(new Insets(22));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 22, 16, 22));
        BorderPane frame = new BorderPane(scroll);
        frame.setBottom(footer);
        frame.setStyle("-fx-background-color: #f4f7fb;");
        Scene scene = new Scene(frame, 690, 700);
        var css = BarcodeLabelDialog.class.getResource("/com/group1/bookit/bookit-dashboard.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        content.setStyle("-fx-background-color: #f4f7fb;");
        print.getStyleClass().add("primary-button");
        save.getStyleClass().add("secondary-button");
        close.getStyleClass().add("secondary-button");
        print.setOnAction(event -> printLabel());
        save.setOnAction(event -> chooseSave());
        close.setOnAction(event -> { if (!busy) stage.close(); });
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

    /** Unscaled, self-contained print node: black on white at exactly 100 × 50 mm. */
    static Pane labelNode(BookCopyLabel copy, Image barcode) {
        requireFxThread();
        requireCopy(copy);
        Objects.requireNonNull(barcode, "barcode image");
        Pane label = new Pane();
        label.setId("copyLabel");
        label.setMinSize(LABEL_WIDTH, LABEL_HEIGHT);
        label.setPrefSize(LABEL_WIDTH, LABEL_HEIGHT);
        label.setMaxSize(LABEL_WIDTH, LABEL_HEIGHT);
        label.resize(LABEL_WIDTH, LABEL_HEIGHT);
        label.setStyle("-fx-background-color: white;");
        Label title = printText(copy.title(), 10, true);
        title.setId("copyLabelTitle");
        title.setWrapText(true);
        title.setAlignment(Pos.TOP_LEFT);
        place(title, 8, 6, LABEL_WIDTH - 16, 25);
        Label identity = printText("Book " + copy.bookId() + "  |  Copy " + copy.copyId(), 7.5, false);
        identity.setId("copyLabelIdentity");
        place(identity, 8, 32, LABEL_WIDTH - 16, 11);
        ImageView bars = new ImageView(barcode);
        bars.setId("copyLabelBars");
        bars.setPreserveRatio(true);
        bars.setSmooth(false);
        bars.setFitWidth(LABEL_WIDTH - 12);
        bars.setFitHeight((LABEL_WIDTH - 12) / 5);
        bars.setX(6);
        bars.setY(45);
        Label readable = printText(copy.barcode(), copy.barcode().length() > 35 ? 8 : 10, false);
        readable.setId("copyLabelCode");
        readable.setStyle(readable.getStyle() + " -fx-font-family: 'Monospaced';");
        readable.setAlignment(Pos.CENTER);
        place(readable, 8, 101, LABEL_WIDTH - 16, 15);
        Label shelf = printText("Shelf: " + displayShelf(copy.shelf()), 8, false);
        shelf.setId("copyLabelShelf");
        place(shelf, 8, 121, LABEL_WIDTH - 16, 12);
        label.getChildren().addAll(title, identity, bars, readable, shelf);
        return label;
    }

    private static void place(Label label, double x, double y, double width, double height) {
        label.relocate(x, y);
        label.setMinSize(width, height);
        label.setPrefSize(width, height);
        label.setMaxSize(width, height);
    }

    /** Printer PageLayout reports printable dimensions in points, not pixels. */
    static boolean fitsPrintableArea(double width, double height) {
        return Double.isFinite(width) && Double.isFinite(height)
                && width >= LABEL_WIDTH && height >= LABEL_HEIGHT;
    }

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

    private static Label printText(String value, double size, boolean bold) {
        Label label = new Label(value);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setStyle("-fx-font-family: 'Arial'; -fx-font-size: " + size + "px; -fx-text-fill: black;"
                + " -fx-font-weight: " + (bold ? "bold" : "normal") + "; -fx-padding: 0;");
        return label;
    }

    private static Label text(String value, String style) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open copy-label windows on the JavaFX thread.");
    }
}

