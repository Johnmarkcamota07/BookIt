/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.sql.SQLException;
import java.util.Objects;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Administrator confirmation for starting or leaving entrance attendance mode. */
public final class AttendanceUnlockController {
    @FunctionalInterface
    interface Verifier {
        AuthenticatedUser authenticate(String username, String password) throws SQLException;
    }

    private final Stage gate;
    private final AuthenticatedUser actor;
    private final boolean sameAdministrator;
    private final Verifier verifier;
    private final Runnable authorized;
    private final Runnable cancelled;
    private boolean accepted;

    @FXML private Label heading;
    @FXML private Label error;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Button verify;
    @FXML private Button cancel;

    AttendanceUnlockController(Stage gate, AuthenticatedUser actor,
                               boolean sameAdministrator, Verifier verifier,
                               Runnable authorized, Runnable cancelled) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.sameAdministrator = sameAdministrator;
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
    }

    @FXML
    private void initialize() {
        heading.setText(gate.getTitle());
        username.setText(actor.username());
        username.setEditable(!sameAdministrator);
        error.setText(sameAdministrator
                ? "Re-enter the signed-in administrator's password."
                : "Any active administrator may unlock attendance mode.");
        gate.setOnHidden(event -> {
            password.clear();
            if (!accepted) cancelled.run();
        });
    }

    @FXML
    private void verifyAdministrator() {
        String entered = password.getText();
        String enteredUsername = username.getText().trim();
        password.clear();
        if (entered.isEmpty() || enteredUsername.isEmpty()) {
            error.setText("Enter administrator credentials.");
            return;
        }
        verify.setDisable(true);
        error.setText("Checking administrator credentials…");
        Thread check = new Thread(() -> {
            boolean valid = false;
            boolean unavailable = false;
            try {
                AuthenticatedUser result = verifier.authenticate(enteredUsername, entered);
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
                    accepted = true;
                    gate.close();
                    authorized.run();
                } else {
                    error.setText(failedConnection
                            ? "Unable to verify credentials. Check the database connection."
                            : "Administrator credentials were rejected. Check your password or account status.");
                    password.requestFocus();
                }
            });
        }, "BookIt attendance administrator verification");
        check.setDaemon(true);
        check.start();
    }

    @FXML
    private void cancel() {
        gate.close();
    }

    void focusPassword() {
        password.requestFocus();
    }
}

