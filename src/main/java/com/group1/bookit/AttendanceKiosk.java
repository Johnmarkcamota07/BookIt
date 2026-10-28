/*
AttendanceKiosk(Window owner, AuthenticatedUser actor, AttendanceService attendance) = This method helps prepare the entrance kiosk window using the default database connection.;
How it works: It takes the owner window, the admin profile, and the attendance service, and forwards them along with a default password verifier to the master builder constructor.
Why is it used: It acts as a convenient shortcut so the rest of the application can launch the kiosk without needing to manually configure security verifiers every time.

AttendanceKiosk(Window owner, AuthenticatedUser actor, AttendanceService attendance, AttendanceUnlockController.Verifier verifier) = This method helps build and configure the entire library entrance kiosk window.;
How it works: It securely stores the admin profile, attendance service, and password verification tool. It initializes a modal pop-up window, sets up its title and minimum size, configures what happens when someone clicks the close button (forcing them through a password gate), loads the visual layout blueprint from an FXML file, and sets up window-hiding event handlers to safely stop the camera if the parent window closes.
Why is it used: It is the core constructor that puts together all the graphical components and safety rules required to run the secure entrance scanner safely.

show(Window owner, AuthenticatedUser actor, AttendanceService service) = This method helps securely launch the entrance kiosk from the main dashboard.;
How it works: It verifies that it is running on the main JavaFX visual thread and checks that the logged-in user actually holds an "ADMIN" role. It then creates the kiosk window, pops up a password gate to confirm the admin's identity, and once verified, displays the kiosk in full screen and turns the webcam on.
Why is it used: It serves as the official launch button for the entrance mode, adding an essential password checkpoint so unauthorized users cannot open the kiosk.

loadScene() = This method helps load and wire up the visual layout blueprint for the kiosk window.;
How it works: It uses an FXMLLoader to read the "AttendanceKiosk.fxml" design file, sets up a custom controller factory to assign this kiosk class as the controller, and wraps the loaded elements into a new Scene.
Why is it used: It separates the visual design of the kiosk screen from the underlying Java code, keeping the project organized and maintainable.

initialize() = This method helps dynamically size the webcam preview frame right after the screen loads.;
How it works: It binds the width and height of the camera container box so that it scales dynamically with the window size while staying perfectly square, and matches the camera image size to fit snugly inside it with a small border.
Why is it used: It ensures the live camera feed looks clean, centered, and correctly proportioned on any screen size or resolution.

loader(String filename) = This method helps locate and load visual design files from the application's resources folder.;
How it works: It searches the application package for a specific FXML filename (like "AttendanceKiosk.fxml") and returns a configured FXMLLoader ready to read it.
Why is it used: It provides a centralized, reusable way to find layout blueprints without hardcoding absolute file paths.

showLayoutError(Window owner, String message) = This method helps display an error pop-up if the kiosk layout files are missing.;
How it works: It creates an error alert box, links it to the parent window, and displays a clear message telling the administrator that attendance FXML files or CSS styles are missing and explaining how to fix it.
Why is it used: It prevents silent application failures and gives developers or admins instant, actionable feedback if a build file is missing.

startCamera() = This method helps power on the webcam and start the background scanning process.;
How it works: It checks if the window is showing and ensures no other camera worker is already running. Then, it flags the system as running, disables the retry button, updates the status text to "Opening camera…", and starts a background thread running the scanner.
Why is it used: It safely turns on the camera hardware in the background without freezing or locking up the user interface.

stopCamera() = This method helps signal the camera worker to shut down.;
How it works: It flips the running flag to false, which tells the background scanning loop to finish its current cycle and release the camera hardware.
Why is it used: It provides a clean, non-blocking way to turn off the camera when closing the kiosk or hiding the window.

scan() = This method helps continuously capture video frames and search them for member QR codes.;
How it works: It connects to the default webcam, requests an optimal view size, opens the camera feed, and enters a continuous loop. In each loop iteration, it grabs a video frame, crops it into a square, displays it on the screen, attempts to decode any QR code present using ZXing, and if found, checks it against the scan gate before recording attendance.
Why is it used: It acts as the core engine for the kiosk, running constantly in the background so visitors can scan their codes instantly.

decode(QRCodeReader reader, BufferedImage image) = This method helps read the hidden text inside a captured QR image.;
How it works: It takes the cropped video frame, processes it through a black-and-white binarizer, and uses the ZXing QR reader to decode its text. If the QR code is damaged or unreadable, it catches the exception and returns a special unreadable code marker instead of crashing.
Why is it used: It translates a visual snapshot of a phone screen into raw data that the application can check against the database.

record(String qr) = This method helps process a scanned QR code and log the member's visit in the database.;
How it works: It temporarily pauses the camera and scanning loop, updates the status text to show it is checking the QR, and calls the attendance service to toggle the member's visit (time in or time out). If it succeeds, it triggers success feedback. If database errors occur, it flags that review is required and displays an error warning.
Why is it used: It handles the actual database logging when someone scans their QR code at the library entrance.

displayFrame(BufferedImage frame) = This method helps paint the live webcam video onto the screen.;
How it works: It uses an atomic boolean lock to drop duplicate frames if the screen is already updating. It converts the captured Java AWT image into a JavaFX image on a background thread, and then pushes it to the JavaFX application thread to update the image view.
Why is it used: It provides a smooth, real-time live video preview for users so they can align their phone screens properly without lagging the app.

feedback(String text, boolean success) = This method helps pop up a temporary notification message after a scan.;
Howit works: It updates the status text, pauses the scanner, and opens a modal pop-up window loaded from "AttendanceFeedback.fxml" containing the result message. It automatically sets a 2-second timer using PauseTransition to close the notification box automatically so the next person can scan.
Why is it used: It gives instant visual feedback to the student about whether their scan was accepted or rejected without requiring anyone to click a button.

requestExit() = This method helps handle the request to close the kiosk mode securely.;
How it works: It checks if an exit process is already open or if the window is hidden. If a record is currently saving to the database, it refuses to exit until it finishes. Otherwise, it opens a password verification gate to confirm an administrator's credentials before allowing the kiosk window to close.
Why is it used: It prevents students or unauthorized individuals from exiting the kiosk and accessing the sensitive admin dashboard.

passwordGate(Window owner, String title, boolean sameAdministrator, Runnable authorized, Runnable cancelled) = This method helps prompt for an admin password before performing a sensitive action.;
How it works: It creates a modal password window, loads the "AttendanceUnlock.fxml" layout, and attaches an AttendanceUnlockController configured with the specified verifier and success/cancel actions. If the FXML fails to load, it safely triggers the cancel action and displays an error. Otherwise, it shows the window and focuses the password field.
Why isIt used: It provides a secure, reusable password checkpoint used whenever someone tries to start or exit the kiosk mode.

ui(Runnable work) = This method helps safely run visual updates on the JavaFX application thread.;
How it works: It wraps the provided code block in a Platform.runLater call, ensuring that it only executes if the kiosk stage is still actively showing on the screen.
Why is it used: It prevents application crashes that occur when background worker threads attempt to modify UI controls directly.

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public final class AttendanceKiosk {
    private final AuthenticatedUser actor;
    private final AttendanceService attendance;
    private final AttendanceUnlockController.Verifier verifier;
    private final Stage stage = new Stage();
    @FXML private ImageView cameraImage;
    @FXML private StackPane cameraFrame;
    @FXML private Label status;
    @FXML private Button retry;
    private final AtomicBoolean workerActive = new AtomicBoolean();
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final AttendanceScanGate scanGate = new AttendanceScanGate();
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean reviewRequired;
    private volatile boolean recordInFlight;
    private Thread cameraThread;
    private boolean exitAllowed;
    private boolean exitGateOpen;
    private final EventHandler<WindowEvent> ownerHidden = event -> {
        exitAllowed = true;
        stopCamera();
        stage.close();
    };

    private AttendanceKiosk(Window owner, AuthenticatedUser actor, AttendanceService attendance) {
        this(owner, actor, attendance,
                new AuthenticationService(DatabaseConnection::getConnection)::authenticatePassword);
    }

    AttendanceKiosk(Window owner, AuthenticatedUser actor, AttendanceService attendance,
                    AttendanceUnlockController.Verifier verifier) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.attendance = Objects.requireNonNull(attendance, "attendance");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("BookIt · Library entrance");
        stage.setMinWidth(760);
        stage.setMinHeight(650);
        stage.setOnCloseRequest(event -> {
            if (!exitAllowed) { event.consume(); requestExit(); }
        });
        stage.setScene(loadScene());
        owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        stage.setOnHidden(event -> {
            stopCamera();
            owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        });
    }

    public static void show(Window owner, AuthenticatedUser actor, AttendanceService service) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open attendance mode on the JavaFX thread.");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(service, "service");
        if (actor == null || !"ADMIN".equals(actor.role())) throw new IllegalArgumentException("Administrator access is required.");
        final AttendanceKiosk kiosk;
        try {
            kiosk = new AttendanceKiosk(owner, actor, service);
        } catch (UncheckedIOException error) {
            showLayoutError(owner, "The entrance screen could not be loaded.");
            return;
        }
        kiosk.passwordGate(owner, "Start entrance attendance", true, () -> {
            kiosk.stage.show();
            kiosk.stage.setFullScreen(true);
            kiosk.startCamera();
        }, () -> owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, kiosk.ownerHidden));
    }

    private Scene loadScene() {
        try {
            FXMLLoader loader = loader("AttendanceKiosk.fxml");
            loader.setControllerFactory(type -> {
                if (type != AttendanceKiosk.class) {
                    throw new IllegalStateException("AttendanceKiosk.fxml must use AttendanceKiosk.");
                }
                return this;
            });
            return new Scene(loader.load());
        } catch (IOException error) {
            throw new UncheckedIOException("The entrance layout could not be loaded.", error);
        }
    }

    @FXML
    private void initialize() {
        cameraFrame.prefWidthProperty().bind(Bindings.min(stage.widthProperty().multiply(.40),
                stage.heightProperty().multiply(.60)));
        cameraFrame.maxWidthProperty().bind(cameraFrame.prefWidthProperty());
        cameraFrame.prefHeightProperty().bind(cameraFrame.prefWidthProperty());
        cameraFrame.minHeightProperty().bind(cameraFrame.prefHeightProperty());
        cameraFrame.maxHeightProperty().bind(cameraFrame.prefHeightProperty());
        cameraImage.fitWidthProperty().bind(cameraFrame.widthProperty().subtract(12));
        cameraImage.fitHeightProperty().bind(cameraFrame.heightProperty().subtract(12));
    }

    private static FXMLLoader loader(String filename) throws IOException {
        URL location = AttendanceKiosk.class.getResource("/com/group1/bookit/" + filename);
        if (location == null) throw new IOException("Missing attendance layout: " + filename);
        return new FXMLLoader(location);
    }

    private static void showLayoutError(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Library entrance unavailable");
        alert.setHeaderText(message);
        alert.setContentText("Check the attendance FXML files and attendance-kiosk.css in the bookit resource folder, then Clean and Build.");
        alert.showAndWait();
    }

    @FXML
    private void startCamera() {
        if (!stage.isShowing() || !workerActive.compareAndSet(false, true)) return;
        running = true;
        retry.setDisable(true);
        status.setText("Opening camera…");
        cameraThread = CameraWorker.start(this::scan, "BookIt entrance camera");
    }

    private void stopCamera() {
        running = false;
    }

    private void scan() {
        Webcam camera = null;
        boolean claimed = false;
        QRCodeReader reader = new QRCodeReader();
        try {
            if (!running) return;
            camera = Webcam.getDefault(8, TimeUnit.SECONDS);
            if (!running) return;
            if (camera == null) throw new IllegalStateException("No camera");
            if (camera.isOpen()) throw new IllegalStateException("Camera already in use");
            Dimension[] sizes = camera.getViewSizes();
            if (sizes.length > 0) camera.setViewSize(Arrays.stream(sizes)
                    .min(Comparator.comparingInt(size -> Math.abs(size.width - 640))).orElse(sizes[0]));
            claimed = true;
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
            if (camera != null && claimed) {
                CameraWorker.release(camera::close);
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
            return "UNREADABLE_QR";
        } finally {
            reader.reset();
        }
    }

    private void record(String qr) {
        paused = true;
        recordInFlight = true;
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
        } finally {
            recordInFlight = false;
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
        try {
            FXMLLoader loader = loader("AttendanceFeedback.fxml");
            notice.setScene(new Scene(loader.load()));
            ((Label) loader.getNamespace().get("feedbackMessage")).setText(text);
        } catch (IOException error) {
            // The attendance operation has already completed. Never retry it for a UI error.
            status.setText(status.getText() + " The feedback window could not be loaded.");
            paused = exitGateOpen || reviewRequired;
            return;
        }
        notice.setResizable(false);
        notice.setOnHidden(event -> { paused = exitGateOpen || reviewRequired; });
        notice.show();
        PauseTransition hide = new PauseTransition(Duration.seconds(2));
        hide.setOnFinished(event -> notice.close());
        hide.play();
    }

    @FXML
    private void requestExit() {
        if (exitGateOpen || !stage.isShowing()) return;
        if (recordInFlight) {
            status.setText("Attendance is still saving. Wait for its result before exiting.");
            return;
        }
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
        AttendanceUnlockController controller = new AttendanceUnlockController(gate, actor,
                sameAdministrator, verifier, authorized, cancelled);
        try {
            FXMLLoader loader = loader("AttendanceUnlock.fxml");
            loader.setControllerFactory(type -> {
                if (type != AttendanceUnlockController.class) {
                    throw new IllegalStateException("AttendanceUnlock.fxml must use AttendanceUnlockController.");
                }
                return controller;
            });
            gate.setScene(new Scene(loader.load()));
        } catch (IOException error) {
            cancelled.run();
            if (stage.isShowing()) {
                status.setText("The administrator verification screen could not be loaded. Check AttendanceUnlock.fxml, then Clean and Build.");
            } else {
                showLayoutError(owner, "The administrator verification screen could not be loaded.");
            }
            return;
        }
        gate.show();
        controller.focusPassword();
    }

    private void ui(Runnable work) {
        Platform.runLater(() -> { if (stage.isShowing()) work.run(); });
    }
}
