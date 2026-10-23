/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Shared USER/ADMIN password change. It never logs or displays entered secrets. */
public final class PasswordChangeDialog {
    private final Stage stage = new Stage();
    private final Window owner;
    private final AuthenticatedUser actor;
    private final PasswordChangeService service;
    private final Consumer<Boolean> onBusy;
    private final Runnable onSignOut;
    private final PasswordField current = new PasswordField();
    private final PasswordField next = new PasswordField();
    private final PasswordField confirmation = new PasswordField();
    private final Button submit = new Button("Change password");
    private final Button cancel = new Button("Cancel");
    private final Label feedback = new Label();
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
        new PasswordChangeDialog(owner, actor, new PasswordChangeService(),
                onBusy, onSignOut).showAndWait();
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
            owner.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, ownerClosing);
            owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
        } else stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("BookIt - Change password");
        stage.setMinWidth(380);
        stage.setMinHeight(380);
        stage.setScene(buildScene());
        stage.setOnCloseRequest(event -> { if (busy) event.consume(); });
        stage.setOnHidden(event -> dispose());
        stage.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                requestClose();
            }
        });
        submit.setOnAction(event -> submit());
        cancel.setOnAction(event -> requestClose());
        submit.setDefaultButton(true);
        updateControls();
    }

    Stage window() { return stage; }
    void showAndWait() { requireFxThread(); if (!disposed) stage.showAndWait(); }
    void show() { requireFxThread(); if (!disposed) stage.show(); }

    private Scene buildScene() {
        current.setId("passwordCurrent");
        current.setPromptText("Current password");
        next.setId("passwordNew");
        next.setPromptText("At least 15 characters");
        confirmation.setId("passwordConfirm");
        confirmation.setPromptText("Enter the new password again");
        submit.setId("passwordSubmit");
        cancel.setId("passwordCancel");
        feedback.setId("passwordFeedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("muted");
        feedback.setMinHeight(Label.USE_PREF_SIZE);
        VBox fields = new VBox(8,
                label("Current password", "muted"), current,
                label("New password", "muted"), next,
                label("Confirm new password", "muted"), confirmation);
        fields.getStyleClass().add("section-card");
        VBox body = new VBox(16,
                label("Change password", "page-title"),
                label("Account: " + actor.username(), "muted"),
                label("Use 15 to 1,024 characters. Spaces are allowed and are not trimmed. "
                        + "Choose a password you do not use elsewhere.", "section-note"),
                fields,
                label("A successful change revokes your saved library QR and signs you out of this BookIt window. "
                        + "Sign in with the new password; members can then generate a new QR.", "section-note"),
                feedback);
        body.setPadding(new Insets(22));
        body.setStyle("-fx-background-color: #f4f7fb;");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox actions = new HBox(10, cancel, submit);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 22, 18, 22));
        BorderPane root = new BorderPane(scroll);
        root.setBottom(actions);
        root.setStyle("-fx-background-color: #f4f7fb;");
        submit.getStyleClass().add("primary-button");
        cancel.getStyleClass().add("secondary-button");
        Scene scene = new Scene(root, 570, 650);
        var css = PasswordChangeDialog.class.getResource("/com/group1/bookit/bookit-dashboard.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

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
                // The transaction may have committed: never retry or expose driver messages.
                failed = true;
            } finally {
                wipe(oldWork, newWork, confirmedWork);
            }
            PasswordChangeService.Outcome result = outcome;
            boolean uncertain = failed;
            try {
                Platform.runLater(() -> complete(result, uncertain));
            } catch (IllegalStateException applicationClosing) {
                // Application shutdown: arrays were wiped; no callbacks or retries follow.
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
        // Normal close is blocked while running. Forced hiding must not route a new session.
        boolean mustSignOut = terminal && !abandoned && (owner == null || owner.isShowing());
        setHostBusy(false);
        if (mustSignOut && !signedOut) {
            signedOut = true;
            onSignOut.run();
        }
    }

    /** Owner shutdown is not a user retry and cannot trigger a late route change. */
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

    private static Label label(String text, String css) {
        Label result = new Label(text);
        result.setWrapText(true);
        result.setMinHeight(Label.USE_PREF_SIZE);
        result.getStyleClass().add(css);
        return result;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Open password windows on the JavaFX thread.");
    }
}
