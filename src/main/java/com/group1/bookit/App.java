//start(Stage stage) = This method helps open the very first window of the application when you start it.;
//How it works: It loads the visual design for the "Log In" screen, places it into the main application window, maximizes the window to take up the full screen, and finally displays it to you.
//Why is it used: It acts as the official starting line for the visual part of the app, ensuring that the login screen is the very first thing you see before anything else happens.
//
//setRoot(String fxml) = This method helps switch the screen you are currently looking at to a completely different page.;
//How it works: It takes the name of a new screen design (like moving from the "Log In" screen to the main dashboard) and smoothly swaps it into your existing application window.
//Why is it used: It allows the application to transition smoothly between different pages without having to annoying pop open and close new windows every time you click a button.
//
//loadFXML(String fxml) = This method helps fetch and read the visual design blueprints for a screen.;
//How it works: It looks inside the application's background files for a specific design blueprint (an FXML file), reads the instructions, and translates them into the actual buttons, text boxes, and pictures the computer needs to draw.
//Why is it used: It acts as a dedicated builder. By handling the heavy lifting of reading layout files, the rest of the program can simply ask for a new screen to be drawn without worrying about the complex details of how to build it.
//
//main(String[] args) = This method helps launch the entire application from the very beginning.;
//How it works: It tells the computer system to fire up the application's visual engine and open the program immediately, intentionally skipping any unnecessary background checks that might slow down the launch.
//Why is it used: It is the absolute master switch for the program. When you double-click the app's icon on your computer, this is the very first instruction the computer looks for to start turning everything on.



package com.group1.bookit;

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
        launch(args);
    }

}
