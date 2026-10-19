package com.group1.bookit;

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

import java.io.IOException;
import java.net.URL;

public class PrimaryController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginMessageLabel;

    @FXML
    private void openQrReader(ActionEvent event) {
        try {
            URL fxmlLocation = getClass().getResource(
                    "/com/group1/bookit/QRreader.fxml"
            );

            if (fxmlLocation == null) {
                throw new IOException(
                        "Could not find QRreader.fxml in resources."
                );
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent qrRoot = loader.load();

            Stage loginStage = (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            Stage qrStage = new Stage();
            qrStage.setTitle("BookIt QR Reader");
            qrStage.setScene(new Scene(qrRoot));
            qrStage.setResizable(false);

            qrStage.initOwner(loginStage);
            qrStage.initModality(Modality.WINDOW_MODAL);

            QRreaderController qrController = loader.getController();

            qrStage.setOnHidden(windowEvent -> qrController.stopScanner());

            qrStage.show();

        } catch (IOException exception) {
            showError(
                    "QR Reader Error",
                    "The QR reader screen could not be opened.",
                    exception.getMessage()
            );
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        System.out.println("button was pressed");
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            loginMessageLabel.setText("Enter your username and password.");
            loginMessageLabel.setTextFill(Color.WHITE);
            return;
        }

        boolean isAuthenticated = authenticate(username, password);

        if (isAuthenticated) {
            loginMessageLabel.setText("Login successful.");
            loginMessageLabel.setTextFill(Color.GREEN);
            Stage loginStage = (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            openDashboard(loginStage);

        } else {
            loginMessageLabel.setText("Invalid username or password.");
            loginMessageLabel.setTextFill(Color.RED);
            passwordField.clear();
        }
    }

    private boolean authenticate(String username, String password) {
        // SQL/database validation here later.
        return false;
    }

    private void openDashboard(Stage loginStage) {
        // Temporary: replace this with Dashboard.fxml loading later.
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Login Successful");
        alert.setHeaderText(null);
        alert.setContentText("Login passed. Dashboard will open here.");
        alert.showAndWait();
    }

    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }
}