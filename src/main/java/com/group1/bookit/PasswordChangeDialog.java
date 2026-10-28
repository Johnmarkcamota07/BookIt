/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public final class PasswordChangeDialog {
    private final Stage stage = new Stage();
    private final Window owner;
    private final AuthenticatedUser actor;
    private final PasswordChangeService service;
    private final Consumer<Boolean> onBusy;
    private final Runnable onSignOut;
    @FXML private PasswordField current;
    @FXML private PasswordField next;
    @FXML private PasswordField confirmation;
    @FXML private Button submit;
    @FXML private Button cancel;
    @FXML private Label feedback;
    @FXML private Label accountLabel;
    private boolean busy;
    private boolean hostBusy;
    private boolean terminal;
    private boolean disposed;
    private boolean abandoned;
    private boolean signedOut;
    private final EventHandler<WindowEvent> ownerClosing = event -> {
        if (busy || terminal) event.consume();
    };
    private final EventHandler<WindowEvent> ownerHidden = event -> abandon();

    public static void show(Window owner, AuthenticatedUser actor,
                            Consumer<Boolean> onBusy, Runnable onSignOut) {
        requireFxThread();
        try {
            new PasswordChangeDialog(owner, actor, new PasswordChangeService(),
                    onBusy, onSignOut).showAndWait();
        } catch (UncheckedIOException error) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) alert.initOwner(owner);
            alert.setTitle("Change password unavailable");
            alert.setHeaderText("The password screen could not be loaded.");
            alert.setContentText("Check that PasswordChange.fxml and password-change.css are in the bookit resource folder, then Clean and Build.");
            alert.showAndWait();
        }
    }

    /** Package seam: construction does not connect to the database or hash a password. */
    PasswordChangeDialog(Window owner, AuthenticatedUser actor,
                         PasswordChangeService service,
                         Consumer<Boolean> onBusy, Runnable onSignOut) {
        requireFxThread();
        if (actor == null || actor.userId() == null || actor.userId().isBlank()
                || actor.username() == null || actor.username().isBlank()
                || !("USER".equals(actor.role()) || "ADMIN".equals(actor.role()))) {
            throw new IllegalArgumentException("Sign in before changing your password.");
        }
        this.owner = owner;
        this.actor = actor;
        this.service = Objects.requireNonNull(service, "service");
        this.onBusy = Objects.requireNonNull(onBusy, "onBusy");
        this.onSignOut = Objects.requireNonNull(onSignOut, "onSignOut");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("BookIt - Change password");
        stage.setMinWidth(380);
        stage.setMinHeight(380);
        stage.setScene(loadScene());
        // A failed FXML load must not leave a dialog retained by its owner's listeners.
        if (owner != null) {
            owner.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, ownerClosing);
            owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        }
        stage.setOnCloseRequest(event -> { if (busy) event.consume(); });
        stage.setOnHidden(event -> dispose());
        stage.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                requestClose();
            }
        });
        updateControls();
    }

    Stage window() { return stage; }
    void showAndWait() { requireFxThread(); if (!disposed) stage.showAndWait(); }
    void show() { requireFxThread(); if (!disposed) stage.show(); }

    private Scene loadScene() {
        URL location = PasswordChangeDialog.class.getResource("/com/group1/bookit/PasswordChange.fxml");
        if (location == null) {
            throw new UncheckedIOException(new IOException("PasswordChange.fxml is missing from the application resources."));
        }
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(type -> {
            if (type != PasswordChangeDialog.class) {
                throw new IllegalStateException("PasswordChange.fxml must use PasswordChangeDialog.");
            }
            return this;
        });
        try {
            return new Scene(loader.load());
        } catch (IOException error) {
            throw new UncheckedIOException("The password-change layout could not be loaded.", error);
        }
    }

    @FXML
    private void initialize() {
        accountLabel.setText("Account: " + actor.username());
    }

    @FXML
    private void submit() {
        if (busy || terminal || disposed) return;
        char[] oldPassword = current.getText().toCharArray();
        char[] newPassword = next.getText().toCharArray();
        char[] confirmed = confirmation.getText().toCharArray();
        clearFields();
        try {
            PasswordChangeService.validateInput(oldPassword, newPassword, confirmed);
        } catch (IllegalArgumentException invalid) {
            wipe(oldPassword, newPassword, confirmed);
            // Fixed validation messages only; never display exception text.
            feedback.setText("Enter your current password and a different new password of 15 to 1,024 characters. "
                    + "The confirmation must match exactly. Please enter all three fields again.");
            current.requestFocus();
            return;
        }
        final char[] oldWork = oldPassword.clone();
        final char[] newWork = newPassword.clone();
        final char[] confirmedWork = confirmed.clone();
        wipe(oldPassword, newPassword, confirmed);
        busy = true;
        feedback.setText("Updating your password. Please wait; do not close BookIt.");
        updateControls();
        try {
            setHostBusy(true);
        } catch (RuntimeException callbackFailure) {
            wipe(oldWork, newWork, confirmedWork);
            busy = false;
            terminal = true;
            feedback.setText("The account screen could not be secured. No password change was started. Return to sign in.");
            updateControls();
            return;
        }
        Thread worker = new Thread(() -> {
            PasswordChangeService.Outcome outcome = null;
            boolean failed = false;
            try {
                outcome = service.changePassword(actor, oldWork, newWork, confirmedWork);
                if (outcome == null) failed = true;
            } catch (Exception error) {
                failed = true;
            } finally {
                wipe(oldWork, newWork, confirmedWork);
            }
            PasswordChangeService.Outcome result = outcome;
            boolean uncertain = failed;
            try {
                Platform.runLater(() -> complete(result, uncertain));
            } catch (IllegalStateException applicationClosing) {
            }
        }, "bookit-password-change");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException startFailure) {
            wipe(oldWork, newWork, confirmedWork);
            complete(null, true);
        }
    }

    private void complete(PasswordChangeService.Outcome outcome, boolean uncertain) {
        if (disposed || abandoned) return;
        busy = false;
        clearFields();
        if (uncertain) {
            terminal = true;
            feedback.setText("Unable to confirm the password change. Your new password may have been saved "
                    + "and your QR may have been revoked. Return to sign in; try the new password first. "
                    + "No automatic retry was made.");
        } else if (outcome == PasswordChangeService.Outcome.CHANGED) {
            terminal = true;
            feedback.setText("Password changed. Your saved QR is revoked. Sign in again.");
        } else {
            feedback.setText("Password change rejected. Check your current password and whether your account is active or locked. "
                    + "You can enter the fields again or cancel.");
            setHostBusy(false);
        }
        updateControls();
    }

    @FXML
    private void requestClose() { if (!busy && !disposed) stage.close(); }

    private void updateControls() {
        current.setDisable(busy || terminal || disposed);
        next.setDisable(busy || terminal || disposed);
        confirmation.setDisable(busy || terminal || disposed);
        submit.setDisable(busy || terminal || disposed);
        cancel.setDisable(busy || disposed);
        cancel.setText(terminal ? "Return to sign in" : "Cancel");
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        clearFields();
        detachOwner();
        boolean mustSignOut = terminal && !abandoned && (owner == null || owner.isShowing());
        setHostBusy(false);
        if (mustSignOut && !signedOut) {
            signedOut = true;
            onSignOut.run();
        }
    }

    private void abandon() {
        if (disposed) return;
        abandoned = true;
        if (stage.isShowing()) stage.hide();
        else dispose();
    }

    private void detachOwner() {
        if (owner == null) return;
        owner.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, ownerClosing);
        owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
    }

    private void setHostBusy(boolean value) {
        if (hostBusy == value) return;
        hostBusy = value;
        onBusy.accept(value);
    }

    private void clearFields() { current.clear(); next.clear(); confirmation.clear(); }

    private static void wipe(char[]... values) {
        for (char[] value : values) if (value != null) Arrays.fill(value, '\0');
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open password windows on the JavaFX thread.");
    }
}
