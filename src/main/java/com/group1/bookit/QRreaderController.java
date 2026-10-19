package com.group1.bookit;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.geometry.Rectangle2D;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;

public class QRreaderController {

    @FXML
    private ImageView cameraImage;

    @FXML
    private Label scanResultLabel;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private ExecutorService cameraExecutor;
    private Webcam webcam;

    @FXML
    private void initialize() {
        // Starts the camera immediately after the pop-up appears.
        Platform.runLater(this::startScanner);
    }

    private void startScanner() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }

        scanResultLabel.setText("Opening camera...");

        cameraExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bookit-camera");
            thread.setDaemon(true);
            return thread;
        });

        cameraExecutor.submit(() -> {
            try {
                webcam = Webcam.getDefault();

                if (webcam == null) {
                    throw new IllegalStateException("No camera was detected on this computer.");
                }

                webcam.open();

                Platform.runLater(() ->
                        scanResultLabel.setText("Camera is ready. Point it at a QR code.")
                );

                while (scanning.get() && webcam.isOpen()) {
                    BufferedImage frame = webcam.getImage();

                    if (frame == null) {
                        continue;
                    }

                    Image fxFrame = SwingFXUtils.toFXImage(frame, null);

                    Platform.runLater(() -> {
                        double imageWidth = fxFrame.getWidth();
                        double imageHeight = fxFrame.getHeight();

                        // Uses the smallest dimension, making a square crop.
                        double squareSize = Math.min(imageWidth, imageHeight);

                        // Centres the square crop.
                        double cropX = (imageWidth - squareSize) / 2;
                        double cropY = (imageHeight - squareSize) / 2;

                        cameraImage.setViewport(
                                new Rectangle2D(cropX, cropY, squareSize, squareSize)
                        );

                        cameraImage.setImage(fxFrame);
                    });

                    String CodeValue = scanCode(frame);

                    if (CodeValue != null) {
                        AuthenticatedUser user =
                                authenticationService.authenticateQrToken(CodeValue);

                        if (user != null) {
                            scanning.set(false);

                            Platform.runLater(() ->
                                    onLoginSuccess.accept(user)
                            );
                        } else {
                            // Do not show the token itself—it is a login credential.
                            Platform.runLater(() ->
                                    scanResultLabel.setText("Invalid login QR code.")
                            );
                        }
                    }
                    Thread.sleep(30);
                }

            } catch (Exception exception) {
                Platform.runLater(() ->
                        scanResultLabel.setText(
                                "Camera error: " + exception.getMessage()
                        )
                );
            } finally {
                closeCamera();
            }
        });
    }

    private String scanCode(BufferedImage frame) {
        try {
            BufferedImageLuminanceSource source =
                    new BufferedImageLuminanceSource(frame);

            BinaryBitmap bitmap =
                    new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(bitmap);

            return result.getText();

        } catch (Exception exception) {
            // Expected while no readable QR code is in the current frame.
            return null;
        }
    }

    public void stopScanner() {
        scanning.set(false);
        closeCamera();

        Platform.runLater(() -> {
            if (scanResultLabel != null) {
                scanResultLabel.setText("Camera stopped.");
            }
        });
    }

    private void closeCamera() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }

        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdownNow();
        }
    }
    private final AuthenticationService authenticationService =
        new AuthenticationService();

    private Consumer<AuthenticatedUser> onLoginSuccess = user -> {};

    public void setOnLoginSuccess(
            Consumer<AuthenticatedUser> onLoginSuccess
    ) {
        this.onLoginSuccess = java.util.Objects.requireNonNull(onLoginSuccess);
    }

    private void showTimedPopup(String message, boolean success) {
        Platform.runLater(() -> {
            Popup popup = new Popup();

            Label messageLabel = new Label(message);

            String color = success ? "#55D6BE" : "#E85D75";

            messageLabel.setStyle(
                    "-fx-background-color: #171C2B;" +
                    "-fx-text-fill: white;" +
                    "-fx-border-color: " + color + ";" +
                    "-fx-border-width: 2;" +
                    "-fx-background-radius: 10;" +
                    "-fx-border-radius: 10;" +
                    "-fx-padding: 14;" +
                    "-fx-font-size: 15px;"
            );

            popup.getContent().add(messageLabel);

            Window owner = cameraImage.getScene().getWindow();

            popup.show(
                    owner,
                    owner.getX() + 70,
                    owner.getY() + 80
            );

            PauseTransition delay = new PauseTransition(Duration.seconds(2));

            delay.setOnFinished(event -> popup.hide());

            delay.play();
        });
    }
}