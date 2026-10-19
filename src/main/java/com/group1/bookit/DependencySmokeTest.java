/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.util.List;

public class DependencySmokeTest extends Application {

    private final TextArea results = new TextArea();

    @Override
    public void start(Stage stage) {
        results.setEditable(false);
        results.setPrefHeight(400);

        Button runTestsButton = new Button("Run dependency tests");
        runTestsButton.setOnAction(event -> runTests());

        VBox root = new VBox(12, runTestsButton, results);
        root.setStyle("-fx-padding: 20;");

        stage.setScene(new Scene(root, 650, 500));
        stage.setTitle("BookIt Dependency Test");
        stage.show();
    }

    private void runTests() {
        results.clear();

        testJavaFx();
        testDotenv();
        testMySqlDriver();
        testZxing();
        testBoofCv();
        testWebcamCapture();
    }

    private void testJavaFx() {
        pass("JavaFX", "JavaFX window started successfully.");
    }

    private void testDotenv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            String dbUrl = dotenv.get("DB_URL");
            pass("dotenv-java",
                    dbUrl == null
                            ? "Library loaded. No DB_URL was found in .env, which is okay."
                            : "Library loaded. DB_URL was found in .env.");
        } catch (Exception exception) {
            fail("dotenv-java", exception);
        }
    }

    private void testMySqlDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            pass("MySQL Connector/J", "MySQL JDBC driver loaded successfully.");
        } catch (Exception exception) {
            fail("MySQL Connector/J", exception);
        }
    }

    private void testZxing() {
        try {
            BitMatrix qrMatrix = new MultiFormatWriter().encode(
                    "BookIt dependency test",
                    BarcodeFormat.QR_CODE,
                    200,
                    200
            );

            pass("ZXing", "QR-code matrix generated: "
                    + qrMatrix.getWidth() + " x " + qrMatrix.getHeight());
        } catch (Exception exception) {
            fail("ZXing", exception);
        }
    }

    private void testBoofCv() {
        try {
            BufferedImage image = new BufferedImage(
                    200,
                    200,
                    BufferedImage.TYPE_INT_RGB
            );

            GrayU8 boofImage = new GrayU8(200, 200);

            ConvertBufferedImage.convertFrom(image, boofImage, true);

            pass("BoofCV",
                    "BoofCV image conversion succeeded: "
                            + boofImage.width + " x " + boofImage.height);
        } catch (Exception exception) {
            fail("BoofCV", exception);
        }
    }

    private void testWebcamCapture() {
        try {
            List<Webcam> cameras = Webcam.getWebcams();

            pass("webcam-capture",
                    "Library loaded. Cameras detected: " + cameras.size());
        } catch (Exception exception) {
            fail("webcam-capture", exception);
        }
    }

    private void pass(String dependency, String message) {
        results.appendText("PASS — " + dependency + "\n");
        results.appendText("       " + message + "\n\n");
    }

    private void fail(String dependency, Exception exception) {
        results.appendText("FAIL — " + dependency + "\n");
        results.appendText("       " + exception.getClass().getSimpleName()
                + ": " + exception.getMessage() + "\n\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
