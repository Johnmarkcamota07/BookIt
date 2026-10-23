package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.net.URL;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.paint.Color;

public class PrimaryController {
    private final AuthenticationService authenticationService = new AuthenticationService();

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label loginMessageLabel;

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            loginMessageLabel.setText("Enter your username and password.");
            loginMessageLabel.setTextFill(Color.WHITE);
            return;
        }

        Node button = (Node) event.getSource();
        Scene loginScene = button.getScene();
        Stage loginStage = (Stage) loginScene.getWindow();
        // Block duplicate submissions and opening QR while password login is pending.
        loginScene.getRoot().setDisable(true);
        passwordField.clear();
        loginMessageLabel.setText("Signing in...");
        loginMessageLabel.setTextFill(Color.GREEN);
        

        Task<AuthenticatedUser> task = new Task<>() {
            @Override
            protected AuthenticatedUser call() throws Exception {
                return authenticationService.authenticatePassword(username, password);
            }
        };
        task.setOnSucceeded(done -> {
            loginScene.getRoot().setDisable(false);
            //prevents error by not processing after user left the log in 
            if (!loginStage.isShowing() || loginStage.getScene() != loginScene) return;
            AuthenticatedUser user = task.getValue();
            if (user == null) {
                loginMessageLabel.setText("Username or Password is Incorrect");
                loginMessageLabel.setTextFill(Color.RED);
            } else {
                completeLogin(user, loginStage);
            }
        });
        task.setOnFailed(done -> {
            loginScene.getRoot().setDisable(false);
            if (loginStage.isShowing() && loginStage.getScene() == loginScene) {
                loginMessageLabel.setText("Unable to sign in. Check the database connection.");
                loginMessageLabel.setTextFill(Color.RED);
            }
        });

        Thread worker = new Thread(task, "bookit-password-login");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void openQrReader(ActionEvent event) {
        Node button = (Node) event.getSource();
        Scene loginScene = button.getScene();
        Stage loginStage = (Stage) loginScene.getWindow();
        try {
            URL location = getClass().getResource("/com/group1/bookit/QRreader.fxml");
            if (location == null) throw new IOException("QRreader.fxml was not found.");
            FXMLLoader loader = new FXMLLoader(location);
            Parent qrRoot = loader.load();
            QRreaderController qrController = loader.getController();

            Stage qrStage = new Stage();
            qrStage.setTitle("BookIt QR Reader");
            qrStage.setScene(new Scene(qrRoot));
            qrStage.setResizable(false);
            qrStage.initOwner(loginStage);
            qrStage.initModality(Modality.WINDOW_MODAL);

            qrController.setOnLoginSuccess(user -> {
                // QRreaderController must call this on the JavaFX thread, only
                // after AuthenticationService has returned a verified account.
                if (!qrStage.isShowing()) return;
                qrStage.close();
                if (loginStage.isShowing() && loginStage.getScene() == loginScene) {
                    completeLogin(user, loginStage);
                }
            });
            qrStage.setOnHidden(hidden -> qrController.stopScanner());
            qrStage.show();
        } catch (IOException | RuntimeException error) {
            showError(loginStage, "QR Reader Error",
                    "The QR reader could not open. Check QRreader.fxml and its controller.");
        }
    }

    private void completeLogin(AuthenticatedUser user, Stage loginStage) {
        try {
            Navigation.showDashboard(loginStage, user);
        } catch (IOException | RuntimeException error) {
            // stay on log in screen while loading dashboard
            error.printStackTrace();
            SessionManager.logout();
            loginMessageLabel.setText("The dashboard could not open. Check its FXML and controller.");
            loginMessageLabel.setTextFill(Color.RED);
            showError(loginStage, "Dashboard Error",
                    "Confirm AdminDashboard.fxml and UserDashboard.fxml are in your resources folder.");
        }
    }

    private void showError(Stage owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
