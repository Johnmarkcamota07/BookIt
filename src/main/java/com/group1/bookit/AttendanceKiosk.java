/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** Password-gated entrance mode. Fullscreen is not an operating-system kiosk lock. */
public final class AttendanceKiosk {
    private static final String INSTRUCTIONS = "Show your BookIt QR. First scan: Time In. Next scan: Time Out.";
    private final AuthenticatedUser actor;
    private final AttendanceService attendance;
    private final Stage stage = new Stage();
    private final ImageView cameraImage = new ImageView();
    private final Label status = new Label("Opening camera…");
    private final Button retry = new Button("Retry camera");
    private final AtomicBoolean workerActive = new AtomicBoolean();
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final AttendanceScanGate scanGate = new AttendanceScanGate();
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean reviewRequired;
    private Thread cameraThread;
    private boolean exitAllowed;
    private boolean exitGateOpen;
    private final EventHandler<WindowEvent> ownerHidden = event -> {
        exitAllowed = true;
        stopCamera();
        stage.close();
    };

    private AttendanceKiosk(Window owner, AuthenticatedUser actor, AttendanceService attendance) {
        this.actor = actor;
        this.attendance = attendance;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("BookIt · Library entrance");
        stage.setMinWidth(760);
        stage.setMinHeight(650);
        stage.setOnCloseRequest(event -> {
            if (!exitAllowed) { event.consume(); requestExit(); }
        });
        owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        stage.setOnHidden(event -> {
            stopCamera();
            owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        });
        stage.setScene(buildScene());
    }

    public static void show(Window owner, AuthenticatedUser actor, AttendanceService service) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open attendance mode on the JavaFX thread.");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(service, "service");
        if (actor == null || !"ADMIN".equals(actor.role())) throw new IllegalArgumentException("Administrator access is required.");
        AttendanceKiosk kiosk = new AttendanceKiosk(owner, actor, service);
        kiosk.passwordGate(owner, "Start entrance attendance", true, () -> {
            kiosk.stage.show();
            kiosk.stage.setFullScreen(true);
            kiosk.startCamera();
        }, () -> owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, kiosk.ownerHidden));
    }

    private Scene buildScene() {
        Label title = new Label("Library entrance");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #142c46;");
        Label subtitle = new Label("Attendance only · This does not sign you into BookIt");
        subtitle.setStyle("-fx-text-fill: #53677e;");
        VBox heading = new VBox(6, title, subtitle);
        HBox.setHgrow(heading, Priority.ALWAYS);
        Button exit = new Button("Exit attendance mode");
        exit.setOnAction(event -> requestExit());
        HBox top = new HBox(24, heading, exit);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(22, 28, 14, 28));

        // The actual frame is cropped to a centered square before display AND decoding.
        cameraImage.setPreserveRatio(true);
        StackPane square = new StackPane(cameraImage);
        square.setMinSize(220, 220);
        square.setStyle("-fx-background-color: #142c46; -fx-background-radius: 16; -fx-border-color: #31aff0; -fx-border-width: 3; -fx-border-radius: 16;");
        square.prefWidthProperty().bind(Bindings.min(stage.widthProperty().multiply(.40),
                stage.heightProperty().multiply(.60)));
        square.maxWidthProperty().bind(square.prefWidthProperty());
        square.prefHeightProperty().bind(square.prefWidthProperty());
        square.maxHeightProperty().bind(square.prefHeightProperty());
        cameraImage.fitWidthProperty().bind(square.widthProperty().subtract(12));
        cameraImage.fitHeightProperty().bind(square.heightProperty().subtract(12));
        Label instruction = new Label(INSTRUCTIONS);
        instruction.setWrapText(true);
        instruction.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #142c46;");
        status.setWrapText(true);
        status.setStyle("-fx-font-size: 16px; -fx-text-fill: #53677e;");
        retry.setDisable(true);
        retry.setOnAction(event -> startCamera());
        VBox center = new VBox(18, square, instruction, status, retry);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(16, 32, 24, 32));
        Label reminder = new Label("Remove your QR after scanning. If you forgot to check out, ask the librarian to correct your visit.");
        reminder.setWrapText(true);
        reminder.setStyle("-fx-text-fill: #53677e;");
        BorderPane.setMargin(reminder, new Insets(12, 28, 22, 28));
        BorderPane root = new BorderPane(center, top, null, reminder, null);
        root.setStyle("-fx-background-color: #f4f7fb; -fx-font-family: 'Segoe UI';");
        return new Scene(root, 1100, 820);
    }

    private void startCamera() {
        if (!stage.isShowing() || !workerActive.compareAndSet(false, true)) return;
        running = true;
        retry.setDisable(true);
        status.setText("Opening camera…");
        cameraThread = new Thread(this::scan, "BookIt entrance camera");
        cameraThread.setDaemon(true);
        cameraThread.start();
    }

    private void stopCamera() {
        running = false;
        Thread worker = cameraThread;
        if (worker != null) worker.interrupt();
        // Camera close runs in scan()'s finally, never blocks the FX thread.
    }

    private void scan() {
        Webcam camera = null;
        QRCodeReader reader = new QRCodeReader();
        try {
            camera = Webcam.getDefault(8, TimeUnit.SECONDS);
            if (!running) return;
            if (camera == null) throw new IllegalStateException("No camera");
            Dimension[] sizes = camera.getViewSizes();
            if (sizes.length > 0) camera.setViewSize(Arrays.stream(sizes)
                    .min(Comparator.comparingInt(size -> Math.abs(size.width - 640))).orElse(sizes[0]));
            if (!camera.open()) throw new IllegalStateException("Camera did not open");
            if (!running) return;
            ui(() -> status.setText("Ready. Show your BookIt member QR."));
            while (running) {
                BufferedImage frame = camera.getImage();
                if (frame != null) {
                    int side = Math.min(frame.getWidth(), frame.getHeight());
                    BufferedImage square = frame.getSubimage((frame.getWidth() - side) / 2,
                            (frame.getHeight() - side) / 2, side, side);
                    displayFrame(square);
                    if (!paused) {
                        String qr = decode(reader, square);
                        if (scanGate.accept(qr, System.nanoTime())) record(qr);
                    }
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError error) {
            ui(() -> status.setText("Camera unavailable. Check camera permission, close other camera apps, and check the scanner dependencies. Then choose Retry camera."));
        } finally {
            reader.reset();
            if (camera != null) {
                try { camera.close(); } catch (RuntimeException ignored) { }
            }
            running = false;
            workerActive.set(false);
            ui(() -> retry.setDisable(false));
        }
    }

    private static String decode(QRCodeReader reader, BufferedImage image) {
        try {
            return reader.decode(new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(image)))).getText();
        } catch (NotFoundException absent) {
            return null;
        } catch (ChecksumException | FormatException unreadable) {
            // A QR is present but unreadable: do not treat it as leaving the view.
            return "UNREADABLE_QR";
        } finally {
            reader.reset();
        }
    }

    private void record(String qr) {
        paused = true;
        ui(() -> status.setText("Checking QR and recording attendance…"));
        try {
            String result = attendance.toggle(actor, qr);
            ui(() -> feedback(result, true));
        } catch (IllegalArgumentException rejected) {
            ui(() -> feedback(rejected.getMessage(), false));
        } catch (SQLException database) {
            reviewRequired = true;
            ui(() -> feedback("Database unavailable or attendance tables not ready. No success was confirmed. Ask an administrator to check the visit before rescanning.", false));
        } catch (RuntimeException unexpected) {
            reviewRequired = true;
            ui(() -> feedback("Attendance could not be confirmed. Ask an administrator to check the visit before rescanning.", false));
        }
    }

    private void displayFrame(BufferedImage frame) throws java.io.IOException {
        if (!framePending.compareAndSet(false, true)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(frame, "png", bytes);
            Image image = new Image(new ByteArrayInputStream(bytes.toByteArray()));
            Platform.runLater(() -> {
                try { if (stage.isShowing() && running) cameraImage.setImage(image); }
                finally { framePending.set(false); }
            });
        } catch (RuntimeException | java.io.IOException error) {
            framePending.set(false);
            throw error;
        }
    }

    private void feedback(String text, boolean success) {
        status.setText(reviewRequired ? "Scanning paused. Ask an administrator to exit attendance mode and review the records."
                : success ? "Recorded. Remove your QR before scanning again."
                : "Not accepted. Remove your QR and try again, or ask the librarian.");
        paused = true;
        Stage notice = new Stage();
        notice.initOwner(stage);
        notice.initModality(Modality.WINDOW_MODAL);
        notice.setTitle(success ? "Attendance recorded" : "Attendance not confirmed");
        Label message = new Label(text);
        message.setWrapText(true);
        message.setStyle("-fx-font-size: 18px; -fx-text-fill: #142c46;");
        VBox content = new VBox(message);
        content.setPadding(new Insets(28));
        content.setStyle("-fx-background-color: #f4f7fb;");
        notice.setScene(new Scene(content, 520, 180));
        notice.setResizable(false);
        notice.setOnHidden(event -> { paused = exitGateOpen || reviewRequired; });
        notice.show();
        PauseTransition hide = new PauseTransition(Duration.seconds(2));
        hide.setOnFinished(event -> notice.close());
        hide.play();
    }

    private void requestExit() {
        if (exitGateOpen || !stage.isShowing()) return;
        exitGateOpen = true;
        paused = true;
        passwordGate(stage, "Exit attendance mode", false, () -> {
            exitAllowed = true;
            stage.close();
        }, () -> {
            exitGateOpen = false;
            paused = reviewRequired;
        });
    }

    private void passwordGate(Window owner, String title, boolean sameAdministrator,
                              Runnable authorized, Runnable cancelled) {
        Stage gate = new Stage();
        gate.initOwner(owner);
        gate.initModality(Modality.WINDOW_MODAL);
        gate.setTitle(title);
        gate.setResizable(false);
        TextField username = new TextField(actor.username());
        username.setEditable(!sameAdministrator);
        PasswordField password = new PasswordField();
        password.setPromptText("Administrator password");
        Label error = new Label(sameAdministrator ? "Re-enter the signed-in administrator's password."
                : "Any active administrator may unlock attendance mode.");
        error.setWrapText(true);
        Button verify = new Button("Verify administrator");
        Button cancel = new Button("Cancel");
        HBox buttons = new HBox(10, verify, cancel);
        VBox root = new VBox(12, new Label(title), username, password, error, buttons);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #f4f7fb; -fx-font-family: 'Segoe UI';");
        gate.setScene(new Scene(root, 450, 260));
        boolean[] accepted = {false};
        gate.setOnHidden(event -> { password.clear(); if (!accepted[0]) cancelled.run(); });
        cancel.setOnAction(event -> gate.close());
        verify.setDefaultButton(true);
        verify.setOnAction(event -> {
            String entered = password.getText();
            String enteredUsername = username.getText().trim();
            password.clear();
            if (entered.isEmpty() || enteredUsername.isEmpty()) { error.setText("Enter administrator credentials."); return; }
            verify.setDisable(true);
            error.setText("Checking administrator credentials…");
            Thread check = new Thread(() -> {
                boolean valid = false;
                boolean unavailable = false;
                try {
                    AuthenticatedUser result = new AuthenticationService(DatabaseConnection::getConnection)
                            .authenticatePassword(enteredUsername, entered);
                    valid = result != null && "ADMIN".equals(result.role())
                            && (!sameAdministrator || actor.userId().equals(result.userId()));
                } catch (SQLException | RuntimeException failure) {
                    unavailable = true;
                }
                final boolean success = valid;
                final boolean failedConnection = unavailable;
                Platform.runLater(() -> {
                    if (!gate.isShowing()) return;
                    verify.setDisable(false);
                    if (success) {
                        accepted[0] = true;
                        gate.close();
                        authorized.run();
                    } else {
                        error.setText(failedConnection ? "Unable to verify credentials. Check the database connection."
                                : "Administrator credentials were rejected. Check your password or account status.");
                        password.requestFocus();
                    }
                });
            }, "BookIt attendance administrator verification");
            check.setDaemon(true);
            check.start();
        });
        gate.show();
        password.requestFocus();
    }

    private void ui(Runnable work) {
        Platform.runLater(() -> { if (stage.isShowing()) work.run(); });
    }
}