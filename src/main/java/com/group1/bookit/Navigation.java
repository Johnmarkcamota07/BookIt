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
import javafx.stage.WindowEvent;

public final class Navigation {
    private static final String ROOT = "/com/group1/bookit/";
    private static final String DASHBOARD = "bookit.currentDashboard";
    private static final String CLEANUP_INSTALLED = "bookit.cleanupInstalled";

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

        final Scene nextScene;
        try {
            controller.setUser(user);
            nextScene = new Scene(root);
        } catch (RuntimeException error) {
            // setUser may already have started a background read.
            try { controller.dispose(); }
            catch (RuntimeException cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }

        // Save the session only after the dashboard is shown. abo ko na yah
        disposeDashboard(stage);
        SessionManager.login(user);
        stage.getProperties().put(DASHBOARD, controller);
        if (!stage.getProperties().containsKey(CLEANUP_INSTALLED)) {
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
                try { disposeDashboard(stage); }
                finally { SessionManager.logout(); }
            });
            stage.getProperties().put(CLEANUP_INSTALLED, Boolean.TRUE);
        }
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setScene(nextScene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
       
        double frameWidth = Math.max(0, stage.getWidth() - nextScene.getWidth());
        double frameHeight = Math.max(0, stage.getHeight() - nextScene.getHeight());
        stage.setMinWidth(1024 + frameWidth);
        stage.setMinHeight(700 + frameHeight);
        stage.setFullScreen(true);
    }

    public static void logout(Stage stage) throws IOException {
        requireFxThread();
        SessionManager.logout();
        disposeDashboard(stage);
        // The login screen must not inherit the dashboard's display mode.
        stage.setFullScreen(false);
        stage.setMaximized(false);

        FXMLLoader loader = loaderFor("LogIn.fxml");
        Parent root = loader.load();
        // The existing login screen may be smaller than the dashboards.
        stage.setMinWidth(0);
        stage.setMinHeight(0);
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

    private static void disposeDashboard(Stage stage) {
        Object controller = stage.getProperties().remove(DASHBOARD);
        if (controller instanceof DashboardView dashboard) dashboard.dispose();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Change screens on the JavaFX application thread.");
        }
    }
}
