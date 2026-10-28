/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template

AttendanceUnlockController(Stage gate, AuthenticatedUser actor, boolean sameAdministrator, Verifier verifier, Runnable authorized, Runnable cancelled) = This method helps prepare the password pop-up window.;
How it works: It saves all the required information to run the password check, including the pop-up window itself, the logged-in administrator's profile, whether the password must belong to that specific admin, the security checking tool, and the instructions for what to do if the password is right (authorized) or wrong (cancelled).
Why is it used: It acts as the master setup tool for the controller, holding all the necessary pieces together so the password gate can operate correctly.

initialize() = This method helps set up the text boxes and labels as soon as the pop-up opens.;
How it works: It takes the pop-up window's title and displays it at the top. It fills the username box with the current admin's username. If the rules say only this specific admin can unlock it, it locks the username box so no one can change it. It also sets up a hidden rule that immediately triggers the "cancelled" instructions if the window is closed before a correct password is typed.
Why is it used: It ensures the pop-up window looks correct and behaves securely the moment it appears on the screen.

verifyAdministrator() = This method helps securely check the password the user typed.;
How it works: It grabs the typed username and password, then immediately wipes the password box empty for safety. If the boxes are empty, it yells at the user. If there is text, it disables the "Verify" button (so they can't click it twice) and sends the credentials to the background worker to check against the database.
If the database says the credentials belong to an active administrator (and it's the right administrator if the rules demand it), it flags the check as a success, closes the window, and runs the "authorized" instructions. If the password is wrong or the database crashes, it turns the button back on, displays a specific error message, and highlights the password box so they can try again.
Why is it used: It securely handles the entire password-checking process without freezing the main application screen while waiting for the database to answer.

cancel() = This method helps safely close the password window without unlocking anything.;
How it works: It simply closes the pop-up window. Because of the rules set up in initialize(), closing the window automatically runs the safety "cancelled" instructions in the background.
Why is it used: It gives the user an easy way to back out if they clicked a restricted button by mistake.

focusPassword() = This method helps automatically highlight the password text box.;
How it works: It forces the computer cursor to blink inside the password box.
Why is it used: It makes the app faster and easier to use, because the administrator can start typing their password immediately without having to use their mouse to click the box first.
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

