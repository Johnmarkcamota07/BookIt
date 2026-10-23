/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Reads a physical-copy label; accepting a loan remains the parent form's job. */
public final class BarcodeScannerDialog {
    private static final String UNAVAILABLE = "Camera unavailable or in use. Close this window and type or scan the copy barcode in the form.";
    private static final Map<DecodeHintType, Object> HINTS = Map.of(
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128),
            DecodeHintType.TRY_HARDER, Boolean.TRUE);
    private final Stage stage = new Stage();
    private final ImageView preview = new ImageView();
    private final Label status = new Label("Opening camera...");
    private final Button cancel = new Button("Cancel");
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final FrameSourceFactory sourceFactory;
    private final Window owner;
    private final EventHandler<WindowEvent> ownerHidden = event -> stage.close();
    private volatile boolean stopped;
    private Thread worker;
    private String result;

    /** Called on the JavaFX thread. Returns the exact label value, or null on cancel. */
    public static String scan(Window owner) {
        requireFxThread();
        return new BarcodeScannerDialog(owner, BarcodeScannerDialog::defaultSource).showAndWait();
    }

    // Injectable image source keeps lifecycle tests independent of camera hardware.
    BarcodeScannerDialog(Window owner, FrameSourceFactory sourceFactory) {
        requireFxThread();
        this.owner = owner;
        this.sourceFactory = Objects.requireNonNull(sourceFactory, "sourceFactory");
        if (owner != null) stage.initOwner(owner);
        stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        stage.setTitle("BookIt - Book copy barcode");
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setScene(buildScene());
        stage.setOnShown(event -> start());
        stage.setOnHidden(event -> {
            stopped = true;
            if (worker != null) worker.interrupt();
            if (owner != null) owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
            // The worker's finally owns native close, including after a delayed open.
        });
        if (owner != null) owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
    }

    String showAndWait() {
        requireFxThread();
        stage.showAndWait();
        return result;
    }

    private Scene buildScene() {
        Label title = new Label("Book copy barcode");
        title.setStyle(
                "-fx-font-size: 24px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: #142c46;"
        );

        Label instruction = new Label(
                "Show the CODE 128 label on the physical book copy. "
                + "Keep the entire barcode and both ends visible."
        );
        instruction.setWrapText(true);
        instruction.setMaxWidth(Double.MAX_VALUE);
        instruction.setStyle(
                "-fx-font-size: 15px; -fx-text-fill: #53677e;"
        );

        preview.setPreserveRatio(true);
        preview.setViewport(null);
        preview.setSmooth(true);

        StackPane frame = new StackPane(preview);
        frame.setMinSize(0, 210);
        frame.setPrefSize(640, 300);
        frame.setStyle(
                "-fx-background-color: #142c46;"
                + "-fx-background-radius: 12;"
        );

        // Fill the rectangle's width without stretching the image.
        preview.fitWidthProperty().unbind();
        preview.fitHeightProperty().unbind();

        preview.fitWidthProperty().bind(frame.widthProperty());
        preview.setFitHeight(0);

        // Clip vertical overflow and keep rounded corners.
        javafx.scene.shape.Rectangle clip =
                new javafx.scene.shape.Rectangle();

        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        frame.setClip(clip);

        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #53677e;");

        cancel.setCancelButton(true);
        cancel.setOnAction(event -> stage.close());

        HBox actions = new HBox(cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(
                14, title, instruction, frame, status, actions
        );

        VBox.setVgrow(frame, javafx.scene.layout.Priority.ALWAYS);

        root.setPadding(new Insets(24));
        root.setStyle(
                "-fx-background-color: #f4f7fb;"
                + "-fx-font-family: 'Segoe UI';"
        );

        return new Scene(root, 680, 510);
    }

    private void start() {
        worker = new Thread(this::readFrames, "BookIt copy barcode camera");
        worker.setDaemon(true);
        worker.start();
    }

    private void readFrames() {
        FrameSource source = null;
        try {
            source = sourceFactory.create();
            if (stopped) return;
            if (source == null || !source.open()) {
                unavailable();
                return;
            }
            if (stopped) return;
            ui(() -> status.setText("Ready. Show the book copy barcode."));
            while (!stopped) {
                BufferedImage frame = source.getImage();
                if (stopped) return;
                if (frame != null) {
                    // Decode and preview the full rectangle; square crops can remove label ends.
                    String value = decode(frame);
                    if (value != null) {
                        stopped = true;
                        Platform.runLater(() -> {
                            if (!stage.isShowing()) return;
                            result = value;
                            stage.close();
                        });
                        return;
                    }
                    displayFrame(frame);
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!stopped) unavailable();
        } catch (Exception | LinkageError unavailable) {
            unavailable();
        } finally {
            if (source != null) {
                try { source.close(); } catch (Exception | LinkageError ignored) { }
            }
        }
    }

    private void unavailable() {
        if (stopped) return;
        stopped = true;
        Platform.runLater(() -> {
            if (!stage.isShowing()) return;
            status.setText(UNAVAILABLE);
            cancel.setText("Close");
        });
    }

    private void displayFrame(BufferedImage frame) throws IOException {
        if (!framePending.compareAndSet(false, true)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(frame, "png", bytes)) throw new IOException("Preview unavailable");
            Image image = new Image(new ByteArrayInputStream(bytes.toByteArray()));
            Platform.runLater(() -> {
                try { if (!stopped && stage.isShowing()) preview.setImage(image); }
                finally { framePending.set(false); }
            });
        } catch (IOException | RuntimeException error) {
            framePending.set(false);
            throw error;
        }
    }

    static String decode(BufferedImage frame) {
        if (frame == null) return null;
        MultiFormatReader reader = new MultiFormatReader();
        try {
            Result decoded = reader.decode(new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(frame))), HINTS);
            if (decoded.getBarcodeFormat() != BarcodeFormat.CODE_128) return null;
            String value = decoded.getText();
            if (value == null || value.isEmpty() || value.length() > 50) return null;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) < 33 || value.charAt(i) > 126) return null;
            }
            return value;
        } catch (NotFoundException unreadable) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private void ui(Runnable action) {
        Platform.runLater(() -> { if (!stopped && stage.isShowing()) action.run(); });
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open the copy barcode scanner on the JavaFX thread.");
    }

    private static FrameSource defaultSource() throws Exception {
        Webcam camera = Webcam.getDefault(8, TimeUnit.SECONDS);
        if (camera == null) return null;
        return new FrameSource() {
            private boolean claimed;
            @Override public boolean open() {
                if (camera.isOpen()) return false;
                Dimension[] sizes = camera.getViewSizes();
                if (sizes.length > 0) camera.setViewSize(Arrays.stream(sizes)
                        .min(Comparator.comparingInt(size -> Math.abs(size.width - 640))).orElse(sizes[0]));
                claimed = true;
                return camera.open();
            }
            @Override public BufferedImage getImage() { return camera.getImage(); }
            @Override public void close() { if (claimed) camera.close(); }
        };
    }

    @FunctionalInterface
    interface FrameSourceFactory {
        FrameSource create() throws Exception;
    }

    interface FrameSource {
        boolean open() throws Exception;
        BufferedImage getImage() throws Exception;
        void close() throws Exception;
    }
}

