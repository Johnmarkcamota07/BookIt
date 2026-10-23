package com.group1.bookit;

import java.sql.Connection;
import java.sql.SQLException;
        
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("LogIn"));
        stage.setScene(scene);
        stage.setTitle("BookIt - Log In");
        stage.setMaximized(true);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        try (Connection db = DatabaseConnection.getConnection()) {
            System.out.println("Database connection successful.");
        } catch (SQLException exception) {
            System.err.println(
                    "Database connection failed. SQLState: "
                    + exception.getSQLState()
            );
        }

        launch();
    }

}