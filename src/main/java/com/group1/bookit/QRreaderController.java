//QRreaderController() = This method helps prepare the QR login screen for normal use.;
//How it works: It calls the main internal setup tool and connects it automatically to the computer's default webcam and the real library database security checker.
//Why is it used: It acts as the standard starting point when the application runs, linking the visual popup to the real hardware and data.
//
//QRreaderController(FrameSourceFactory sources, Authenticator authentication) = This method helps set up the QR login screen with customized camera or login tools.;
//How it works: It saves the provided camera source and login verifier into the controller.
//Why is it used: It provides a backdoor for programmers to test the QR screen safely without needing a real webcam attached to their computer or messing with the live database.
//
//initialize() = This method helps automatically turn the camera on when the popup window appears.;
//How it works: It sets up invisible tripwires that monitor the visual screen. When the system detects that the popup window has actually been displayed to the user, it immediately starts the camera.
//Why is it used: It automates the hardware so the camera turns on perfectly when needed, without a human clicking a "Start Camera" button.
//
//observeScene(Scene scene) = This method helps connect the camera's on/off switch to the specific graphical box the camera feed lives inside.;
//How it works: It monitors the "Scene" (the graphical box). If the box is placed into a physical window, it starts watching that window. If the box is destroyed, it immediately stops the scanner.
//Why is it used: It ensures the background camera worker does not run if its visual display has been deleted.
//
//observeWindow(Window owner) = This method helps connect the camera's on/off switch to the physical computer window.;
//How it works: It tracks the actual popup window. When the window appears on the user's screen, it starts the camera. When the window is hidden or closed, it instantly shuts the camera off.
//Why is it used: It prevents the camera's green light from staying on and draining the computer's resources after the user has closed the login popup.
//
//visible() = This method helps double-check that the camera feed is actively on the user's screen.;
//How it works: It verifies a strict checklist: the scanner hasn't been stopped, the window exists, the window is showing, and the camera image is securely inside that window.
//Why is it used: It prevents the background thread from trying to draw video frames to a screen that the user just closed, which would cause the application to crash.
//
//startScanner() = This method helps kick off the background camera worker.;
//How it works: It checks if the camera is already running. If it isn't, it changes the screen text to "Opening camera…" and spawns a background thread to start capturing video.
//Why is it used: It safely delegates the heavy processing of opening a webcam to a background worker so the rest of the application doesn't freeze while waiting for the hardware to warm up.
//
//scan() = This method helps continuously watch the webcam for QR codes.;
//How it works: It connects to the camera, grabs a video frame, crops it into a square, displays it, and hunts for a QR code. If it finds a code, it waits 2 seconds (to prevent rapid-fire scanning), checks the code against the database, and either successfully logs the user in or flashes an error message on the screen. It repeats this process endlessly until told to stop.
//Why is it used: It is the main engine of the QR scanner, doing all the heavy lifting of reading images and verifying user identities in real-time.
//
//displayFrame(BufferedImage square) = This method helps draw the live video feed on the screen.;
//How it works: It takes a raw picture from the webcam, converts it into a graphical format that the JavaFX screen understands, and pushes it safely onto the display window.
//Why is it used: It gives the user a live digital mirror so they can clearly see where to hold their phone screen.
//
//decode(BufferedImage frame) = This method helps extract hidden text from a picture of a QR code.;
//How it works: It takes the captured image, converts it into high-contrast black-and-white, and asks the digital barcode reader to find and read the QR pattern.
//Why is it used: It bridges the physical world and the digital world, turning black-and-white pixels into a secret text token the database can verify.
//
//stopScanner() = This method helps safely shut down the camera hardware and background workers.;
//How it works: It flips a "stopped" switch to true, which tells the background video loop to finish its current action and let go of the webcam. It also wipes any lingering error popups from the screen.
//Why is it used: It is the designated off-switch, ensuring the webcam hardware is cleanly disconnected and ready for other programs to use.
//
//setOnLoginSuccess(Consumer<AuthenticatedUser> callback) = This method helps define what should happen after a successful login.;
//How it works: It saves a set of instructions (a callback) provided by the main program. Once a QR code is verified, it executes these instructions, handing over the authenticated user's digital ID badge.
//Why is it used: It allows the popup to simply say "I found a valid user!" and lets the main application decide what to do next (like closing the popup and opening the dashboard).
//
//ui(Runnable action) = This method helps safely update text and graphics on the screen.;
//How it works: It packages a set of screen-updating instructions and sends them to the main JavaFX visual thread, provided the window is still open.
//Why is it used: It prevents fatal software crashes, which happen when background threads (like the camera scanner) try to illegally manipulate graphical buttons or text.
//
//showFeedback(String message) = This method helps display temporary error messages floating over the camera feed.;
//How it works: It creates a tiny popup window, injects the error text (like "QR not accepted"), places it directly on top of the video feed, and sets a 2-second automatic timer to make it disappear.
//Why is it used: It gives the user instant, clear feedback without forcing them to manually click an "OK" button to dismiss the warning.
//
//feedbackContent(String message) = This method helps build the floating error message box.;
//How it works: It loads the visual design file (QrReaderFeedback.fxml), finds the text label inside it, and pastes the provided error message into the label.
//Why is it used: It constructs the physical visual box that gets used by the showFeedback method.
//
//clearFeedback() = This method helps instantly remove floating error messages.;
//How it works: It cancels the 2-second hide timer and immediately deletes the floating popup from the screen.
//Why is it used: It cleans up the display immediately when the camera is turned off or when a new scan happens, preventing old error messages from lingering.
//
//defaultSource() = This method helps connect to the physical camera hardware.;
//How it works: It asks the Sarxos webcam tool to find the default camera, request an optimal resolution, and handle the raw opening and closing of the hardware line.
//Why is it used: It encapsulates the messy hardware driver commands into a clean, reusable tool that the scanner loop can easily turn on and off.

package com.group1.bookit;


import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/** QR login popup. Its worker owns camera open/read/close; hidden views never sign in. */
public final class QRreaderController {
    private static final String READY = "Camera ready. Keep your BookIt QR inside the square.";
    private static final Map<DecodeHintType,Object> HINTS = Map.of(
            DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER,Boolean.TRUE);
    @FXML private ImageView cameraImage;
    @FXML private Label scanResultLabel;
    private final FrameSourceFactory sources;
    private final Authenticator authentication;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final AtomicBoolean delivered = new AtomicBoolean();
    private volatile boolean stopped;
    private Thread worker;
    private Window window;
    private Popup feedback;
    private PauseTransition feedbackDelay;
    private Consumer<AuthenticatedUser> onLoginSuccess = user -> {};
    private final ChangeListener<Boolean> showingListener = (observable,before,showing) -> {
        if (showing) startScanner(); else stopScanner();
    };

    public QRreaderController() {
        this(QRreaderController::defaultSource,new AuthenticationService()::authenticateQrToken);
    }

    /** Package seam for offline lifecycle tests; no connection/camera during construction. */
    QRreaderController(FrameSourceFactory sources, Authenticator authentication) {
        this.sources=Objects.requireNonNull(sources);
        this.authentication=Objects.requireNonNull(authentication);
    }

    @FXML private void initialize() {
        cameraImage.sceneProperty().addListener((observable,before,scene) -> observeScene(scene));
        observeScene(cameraImage.getScene());
        Platform.runLater(this::startScanner);
    }

    private void observeScene(Scene scene) {
        if (scene==null) {
            if (started.get()) stopScanner();
            return;
        }
        scene.windowProperty().addListener((observable,before,owner) -> observeWindow(owner));
        observeWindow(scene.getWindow());
    }

    private void observeWindow(Window owner) {
        if (window==owner) return;
        if (window!=null) {
            window.showingProperty().removeListener(showingListener);
            stopScanner();
        }
        window=owner;
        if (owner!=null) {
            owner.showingProperty().addListener(showingListener);
            if (owner.isShowing()) startScanner();
        }
    }

    private boolean visible() {
        return !stopped && window!=null && window.isShowing() && cameraImage.getScene()!=null
                && cameraImage.getScene().getWindow()==window;
    }

    private void startScanner() {
        if (!visible() || !started.compareAndSet(false,true)) return;
        scanResultLabel.setText("Opening camera…");
        worker=CameraWorker.start(this::scan,"bookit-login-camera");
    }

    private void scan() {
        FrameSource source=null;
        String lastCode=null;
        long lastAttempt=Long.MIN_VALUE;
        try {
            if (stopped) return;
            source=sources.create();
            if (stopped) return;
            if (source==null || !source.open()) {
                ui(() -> scanResultLabel.setText("Camera unavailable or already in use. Close this window and use password login."));
                return;
            }
            if (stopped) return;
            ui(() -> scanResultLabel.setText(READY));
            while (!stopped) {
                BufferedImage frame=source.getImage();
                if (stopped) return;
                if (frame!=null) {
                    int side=Math.min(frame.getWidth(),frame.getHeight());
                    BufferedImage square=frame.getSubimage((frame.getWidth()-side)/2,(frame.getHeight()-side)/2,side,side);
                    displayFrame(square);
                    String code=decode(square);
                    if (code==null) lastCode=null;
                    else {
                        long now=System.nanoTime();
                        if (!code.equals(lastCode) && (lastAttempt==Long.MIN_VALUE || now-lastAttempt>=TimeUnit.SECONDS.toNanos(2))) {
                            lastCode=code;
                            lastAttempt=now;
                            if (!code.startsWith("LOGIN:") || !QrTokens.isValidToken(code.substring(6))) {
                                ui(() -> showFeedback("This is not a BookIt login QR."));
                            } else {
                                ui(() -> scanResultLabel.setText("Checking your login QR…"));
                                AuthenticatedUser user=authentication.authenticate(code);
                                if (stopped) return;
                                if (user!=null) {
                                    // Release native hardware before navigating to another screen.
                                    CameraWorker.release(source::close);
                                    source=null;
                                    ui(() -> {
                                        if (delivered.compareAndSet(false,true)) onLoginSuccess.accept(user);
                                    });
                                    return;
                                }
                                ui(() -> showFeedback("QR not accepted. Remove it and try again, or use password login."));
                            }
                        }
                    }
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError failure) {
            // Driver exceptions can contain SQL, credentials or tokens. Do not display/log them.
            ui(() -> {
                clearFeedback();
                scanResultLabel.setText("Unable to open the camera or verify sign-in. Close this window, check the camera/database connection, and try again.");
            });
        } finally {
            if (source!=null) CameraWorker.release(source::close);
        }
    }

    private void displayFrame(BufferedImage square) {
        if (!framePending.compareAndSet(false,true)) return;
        Image image=SwingFXUtils.toFXImage(square,null);
        Platform.runLater(() -> {
            try {
                if (visible()) {
                    cameraImage.setViewport(new Rectangle2D(0,0,image.getWidth(),image.getHeight()));
                    cameraImage.setImage(image);
                }
            } finally { framePending.set(false); }
        });
    }

    static String decode(BufferedImage frame) {
        if (frame==null) return null;
        MultiFormatReader reader=new MultiFormatReader();
        try {
            return reader.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(frame))),HINTS).getText();
        } catch (NotFoundException absent) { return null; }
        finally { reader.reset(); }
    }

    public void stopScanner() {
        stopped=true;
        Runnable cleanup=this::clearFeedback;
        if (Platform.isFxApplicationThread()) cleanup.run(); else Platform.runLater(cleanup);
        // Let the worker leave its loop. Interrupting it can abort native camera close.
        // A new scanner waits in CameraWorker until scan() finally releases this one.
    }

    public void setOnLoginSuccess(Consumer<AuthenticatedUser> callback) {
        onLoginSuccess=Objects.requireNonNull(callback);
    }

    private void ui(Runnable action) { Platform.runLater(() -> { if (visible()) action.run(); }); }

    private void showFeedback(String message) {
        if (!visible()) return;
        clearFeedback();
        scanResultLabel.setText(message);
        Popup notice=new Popup();
        try {
            notice.getContent().add(feedbackContent(message));
            notice.show(window,window.getX()+60,window.getY()+80);
            feedback=notice;
        } catch (IOException | RuntimeException unavailable) {
            // The result remains readable in the main label; a UI error never retries sign-in.
            notice.hide();
        }
        Popup shown=feedback;
        feedbackDelay=new PauseTransition(Duration.seconds(2));
        feedbackDelay.setOnFinished(event -> {
            if (shown!=null) shown.hide();
            if (feedback==shown) feedback=null;
            if (visible()) scanResultLabel.setText(READY);
        });
        feedbackDelay.play();
    }

    private Parent feedbackContent(String message) throws IOException {
        URL location=getClass().getResource("/com/group1/bookit/QrReaderFeedback.fxml");
        if (location==null) throw new IOException("QrReaderFeedback.fxml is missing from resources.");
        FXMLLoader loader=new FXMLLoader(location);
        Parent content=loader.load();
        Object value=loader.getNamespace().get("feedbackMessage");
        if (!(value instanceof Label text)) throw new IOException("The QR feedback message label is missing.");
        text.setText(message);
        return content;
    }

    private void clearFeedback() {
        if (feedbackDelay!=null) { feedbackDelay.stop(); feedbackDelay=null; }
        if (feedback!=null) { feedback.hide(); feedback=null; }
    }

    private static FrameSource defaultSource() throws Exception {
        Webcam camera=Webcam.getDefault(8,TimeUnit.SECONDS);
        if (camera==null) return null;
        return new FrameSource() {
            private boolean claimed;
            @Override public boolean open() {
                if (camera.isOpen()) return false;
                Dimension[] sizes=camera.getViewSizes();
                if (sizes.length>0) camera.setViewSize(Arrays.stream(sizes)
                        .min(Comparator.comparingInt(size -> Math.abs(size.width-640))).orElse(sizes[0]));
                claimed=true;
                return camera.open();
            }
            @Override public BufferedImage getImage() { return camera.getImage(); }
            @Override public void close() { if (claimed) camera.close(); }
        };
    }
    @FunctionalInterface interface Authenticator { AuthenticatedUser authenticate(String code) throws Exception; }
    @FunctionalInterface interface FrameSourceFactory { FrameSource create() throws Exception; }
    interface FrameSource {
        boolean open() throws Exception;
        BufferedImage getImage() throws Exception;
        void close() throws Exception;
    }
}