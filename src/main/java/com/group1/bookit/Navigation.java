/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.net.URL;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public final class Navigation {
    private static final String ROOT = "/com/group1/bookit/";

    private Navigation() {}

    public static void showDashboard(Stage stage, AuthenticatedUser user)
            throws IOException {
        requireFxThread();
        if (user == null) throw new IOException("No authenticated account was returned.");

        String filename;
        String title;
        if ("ADMIN".equals(user.role())) {
            filename = "AdminDashboard.fxml";
            title = "BookIt - Admin Dashboard";
        } else if ("USER".equals(user.role())) {
            filename = "UserDashboard.fxml";
            title = "BookIt - User Dashboard";
        } else {
            throw new IOException("The account has an unsupported role.");
        }

        FXMLLoader loader = loaderFor(filename);
        Parent root = loader.load();
        Object loadedController = loader.getController();
        if (!(loadedController instanceof DashboardView controller)) {
            throw new IOException(filename + " must use a controller implementing DashboardView.");
        }

        // Pass the account AFTER FXML injection; no session is needed in initialize().
        controller.setUser(user);
        Scene nextScene = new Scene(root);

        // Save the session only after the dashboard is successfully prepared.
        SessionManager.login(user);
        stage.setScene(nextScene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void logout(Stage stage) throws IOException {
        requireFxThread();
        SessionManager.logout();

        FXMLLoader loader = loaderFor("LogIn.fxml");
        Parent root = loader.load();
        stage.setScene(new Scene(root));
        stage.setTitle("BookIt - Log In");
        stage.setResizable(true);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    // Both controllers share this logout handler and its error handling.
    public static void logoutFrom(ActionEvent event) {
        requireFxThread();
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        source.getScene().getRoot().setDisable(true);

        try {
            logout(stage);
        } catch (IOException | RuntimeException error) {
            // Never leave an actionable dashboard behind after signing out.
            SessionManager.logout();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("BookIt");
            alert.setHeaderText("You have been signed out.");
            alert.setContentText("The login screen could not open. Restart BookIt to sign in again.");
            try {
                alert.showAndWait();
            } finally {
                stage.close();
            }
        }
    }

    private static FXMLLoader loaderFor(String filename) throws IOException {
        URL location = Navigation.class.getResource(ROOT + filename);
        if (location == null) throw new IOException("Missing resource: " + ROOT + filename);
        return new FXMLLoader(location);
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Change screens on the JavaFX application thread.");
        }
    }
}