/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** A separate USER-only QR window. It does not change the application session. */
public final class UserQrDialog {
    @FunctionalInterface
    interface Issuer {
        String issue(AuthenticatedUser actor, String password) throws Exception;
    }

    private final Stage stage = new Stage();
    private final AuthenticatedUser actor;
    private final Issuer issuer;
    private final PasswordField password = new PasswordField();
    private final CheckBox consent = new CheckBox(
            "I understand generating a new QR invalidates my previous QR immediately.");
    private final Button generate = new Button("Generate / replace QR");
    private final Button save = new Button("Save PNG");
    private final Button close = new Button("Close");
    private final Label status = new Label("No QR generated in this window yet.");
    private final ImageView preview = new ImageView();
    private final Label placeholder = new Label("Your QR will appear here");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "bookit-qr-generation");
        thread.setDaemon(true);
        return thread;
    });
    private byte[] png;
    private boolean busy;
    private boolean saved;
    private boolean uncertain;
    private boolean disposed;

    public static void show(Window owner, AuthenticatedUser actor) {
        requireFxThread();
        AuthenticationService authentication =
                new AuthenticationService(DatabaseConnection::getConnection);
        new UserQrDialog(owner, actor, authentication::issueOrReplaceUserQr).stage.showAndWait();
    }

    /** Package-level seam for offline tests; construction does not access SQL. */
    UserQrDialog(Window owner, AuthenticatedUser actor, Issuer issuer) {
        requireFxThread();
        if (actor == null || actor.userId() == null || actor.userId().isBlank()
                || actor.username() == null || actor.username().isBlank()
                || !"USER".equals(actor.role())) {
            worker.shutdown();
            throw new IllegalArgumentException("Only a signed-in USER can generate a library QR.");
        }
        this.actor = actor;
        this.issuer = java.util.Objects.requireNonNull(issuer, "issuer");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("BookIt - My library QR");
        stage.setMinWidth(460);
        stage.setMinHeight(480);

        password.setId("qrPassword");
        password.setPromptText("Enter your current password");
        password.setAccessibleText("Confirm your current password to generate a QR");
        consent.setId("qrConsent");
        consent.setWrapText(true);
        generate.setId("qrGenerate");
        save.setId("qrSave");
        close.setId("qrClose");
        preview.setId("qrPreview");
        status.setId("qrStatus");
        preview.setFitWidth(280);
        preview.setFitHeight(280);
        preview.setPreserveRatio(true);
        preview.setSmooth(false);
        StackPane imageBox = new StackPane(preview, placeholder);
        imageBox.setMinHeight(296);
        imageBox.setStyle("-fx-background-color: white; -fx-border-color: #ccd5e0; -fx-padding: 8;");
        placeholder.setStyle("-fx-text-fill: #526277;");
        VBox content = new VBox(14,
                text("MY LIBRARY QR", "section-title"),
                text("Account: " + actor.username(), "muted"),
                text("This is a reusable login credential, not your password. Anyone with a copy can use it. Keep the image private.", "section-note"),
                imageBox, password, consent, new HBox(10, generate, save), status,
                text("Save the PNG to keep it for future visits. Reopening this window cannot recover an earlier QR from its database hash.", "section-note"));
        content.setPadding(new Insets(22));
        status.setWrapText(true);
        status.getStyleClass().add("muted");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 22, 16, 22));
        BorderPane frame = new BorderPane(scroll);
        frame.setBottom(footer);
        frame.setStyle("-fx-background-color: #f4f7fb;");
        Scene scene = new Scene(frame, 580, 720);
        var stylesheet = UserQrDialog.class.getResource("/com/group1/bookit/bookit-dashboard.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        content.setStyle("-fx-background-color: #f4f7fb;");
        generate.getStyleClass().add("primary-button");
        save.getStyleClass().add("secondary-button");
        close.getStyleClass().add("secondary-button");
        stage.setScene(scene);
        password.textProperty().addListener((observable, before, after) -> updateControls());
        consent.selectedProperty().addListener((observable, before, after) -> updateControls());
        generate.setOnAction(event -> generate());
        save.setOnAction(event -> chooseSave());
        close.setOnAction(event -> requestClose());
        stage.setOnCloseRequest(event -> { if (!canClose()) event.consume(); });
        stage.setOnHidden(event -> dispose());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                requestClose();
            }
        });
        updateControls();
    }

    Stage window() { return stage; }

    private void generate() {
        if (busy || disposed || uncertain || password.getText().isEmpty() || !consent.isSelected()) return;
        final String enteredPassword = password.getText();
        password.clear();
        consent.setSelected(false);
        clearImage();
        saved = false;
        busy = true;
        status.setText("Verifying your password and replacing the QR. Please wait; do not close BookIt.");
        updateControls();
        run(() -> {
            String token = issuer.issue(actor, enteredPassword);
            return token == null ? null : CodeGenerator.loginQrPng(token);
        }, result -> {
            if (result == null) {
                status.setText("Generation rejected. Check your password and account status. Your QR was not replaced.");
            } else {
                png = result;
                Image image = new Image(new ByteArrayInputStream(png));
                if (image.isError()) {
                    clearImage();
                    uncertain = true;
                    status.setText("The QR was replaced but its preview failed. Reopen this window to generate another QR.");
                } else {
                    preview.setImage(image);
                    placeholder.setVisible(false);
                    status.setText("QR generated. Your previous QR is invalid. Save this PNG before closing.");
                }
            }
        }, error -> {
            uncertain = true;
            status.setText("QR replacement could not be confirmed. Your previous QR may no longer work. Check the database connection, then reopen this window. No automatic retry was made.");
        });
    }

    private void chooseSave() {
        if (busy || disposed || png == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save my private library QR");
        chooser.setInitialFileName("bookit-my-qr.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        var selected = chooser.showSaveDialog(stage);
        if (selected == null) return;
        Path destination = selected.toPath();
        if (!destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            status.setText("Choose a filename ending in .png. Your QR is still available to save.");
            return;
        }
        byte[] toWrite = png.clone();
        busy = true;
        status.setText("Saving your QR image…");
        updateControls();
        run(() -> {
            try { writePng(destination, toWrite); return Boolean.TRUE; }
            finally { Arrays.fill(toWrite, (byte) 0); }
        }, result -> {
            saved = true;
            status.setText("PNG saved successfully. Keep it private; a copy can grant access to your account.");
        }, error -> status.setText("The PNG could not be saved. Choose a writable location and a NEW .png filename; existing files are never overwritten. Your QR is still available here."));
    }

    static void writePng(Path destination, byte[] bytes) throws IOException {
        if (destination == null || destination.getFileName() == null
                || !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("Choose a PNG filename.");
        }
        try (OutputStream stream = Files.newOutputStream(destination,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            stream.write(bytes);
        }
    }

    private <T> void run(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        worker.submit(() -> {
            try {
                T result = operation.call();
                Platform.runLater(() -> {
                    if (disposed) {
                        if (result instanceof byte[] bytes) Arrays.fill(bytes, (byte) 0);
                        return;
                    }
                    busy = false;
                    success.accept(result);
                    updateControls();
                });
            } catch (Exception error) {
                // Never log the password, token, URL, SQL, or driver message.
                logFailure(error);
                try {
                    Platform.runLater(() -> {
                        if (disposed) return;
                        busy = false;
                        failure.accept(error);
                        updateControls();
                    });
                } catch (IllegalStateException ignored) { /* Application is closing. */ }
            }
        });
    }

    private static void logFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof SQLException sql) {
                System.err.println("QR operation failed: SQLState=" + sql.getSQLState()
                        + ", vendorCode=" + sql.getErrorCode());
                return;
            }
        }
        System.err.println("QR operation failed. No credentials were logged.");
    }

    private void updateControls() {
        password.setDisable(busy || uncertain);
        consent.setDisable(busy || uncertain);
        generate.setDisable(busy || uncertain || disposed || password.getText().isEmpty() || !consent.isSelected());
        save.setDisable(busy || disposed || png == null);
        close.setDisable(busy);
    }

    private void requestClose() { if (canClose()) stage.close(); }

    private boolean canClose() {
        if (busy) return false;
        if (png == null || saved) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "This QR has not been saved. Close without saving? You cannot recover it from the database hash; you will need to generate another one.",
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle("Unsaved library QR");
        alert.setHeaderText("Save the PNG if you want to keep using this QR.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearImage() {
        if (png != null) Arrays.fill(png, (byte) 0);
        png = null;
        preview.setImage(null);
        placeholder.setVisible(true);
    }

    private void dispose() {
        disposed = true;
        password.clear();
        clearImage();
        // Normal UI close is blocked while busy; forced app shutdown never retries a write.
        worker.shutdown();
    }

    private static Label text(String text, String style) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open QR windows on the JavaFX thread.");
    }
}

