package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.io.IOException;
import java.net.URL;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
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
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class PrimaryController {
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass PENDING = PseudoClass.getPseudoClass("pending");
    private final AuthenticationService authenticationService;
    private boolean loginPending;
    private boolean qrWindowOpen;

    public PrimaryController() { this(new AuthenticationService()); }

    /** Allows offline login checks without changing the FXML or opening a database. */
    public PrimaryController(AuthenticationService authenticationService) {
        this.authenticationService = java.util.Objects.requireNonNull(authenticationService);
    }

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label loginMessageLabel;
    @FXML private StackPane loginRoot;
    @FXML private StackPane loginBranding;
    @FXML private Rectangle brandingClip;
    @FXML private ImageView loginLogo;
    @FXML private ImageView compactLogo;
    @FXML private Label loginFooter;

    @FXML
    private void initialize() {
        LogoSupport.install(loginLogo);
        LogoSupport.install(compactLogo);
        loginBranding.prefWidthProperty().bind(loginRoot.widthProperty().multiply(.60));
        loginBranding.minWidthProperty().bind(loginBranding.prefWidthProperty());
        loginBranding.maxWidthProperty().bind(loginBranding.prefWidthProperty());
        brandingClip.widthProperty().bind(loginBranding.widthProperty());
        brandingClip.heightProperty().bind(loginBranding.heightProperty());
        loginRoot.widthProperty().addListener((observable, before, after) -> updateLayout());
        loginRoot.heightProperty().addListener((observable, before, after) -> updateLayout());
        updateLayout();
    }

    private void updateLayout() {
        boolean compact = loginRoot.getWidth() < 900 || loginRoot.getHeight() < 620;
        loginRoot.pseudoClassStateChanged(COMPACT, compact);
        loginBranding.setVisible(!compact);
        loginBranding.setManaged(!compact);
        compactLogo.setVisible(compact);
        compactLogo.setManaged(compact);
        loginFooter.setVisible(!compact);
        loginFooter.setManaged(!compact);
    }

    private void showLoginMessage(String message, boolean error, boolean pending) {
        loginMessageLabel.setText(message);
        loginMessageLabel.pseudoClassStateChanged(ERROR, error);
        loginMessageLabel.pseudoClassStateChanged(PENDING, pending);
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        if (loginPending || qrWindowOpen) return;
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showLoginMessage("Enter your username and password.", true, false);
            return;
        }

        Node button = (Node) event.getSource();
        Scene loginScene = button.getScene();
        Stage loginStage = (Stage) loginScene.getWindow();
        // Block duplicate submissions and opening QR while password login is pending.
        loginScene.getRoot().setDisable(true);
        loginPending = true;
        passwordField.clear();
        showLoginMessage("Signing in...", false, true);
        

        Task<AuthenticatedUser> task = new Task<>() {
            @Override
            protected AuthenticatedUser call() throws Exception {
                return authenticationService.authenticatePassword(username, password);
            }
        };
        task.setOnSucceeded(done -> {
            loginPending = false;
            //prevents error by not processing after user left the log in 
            if (!loginStage.isShowing() || loginStage.getScene() != loginScene) return;
            loginScene.getRoot().setDisable(false);
            AuthenticatedUser user = task.getValue();
            if (user == null) {
                showLoginMessage("Incorrect username or password.", true, false);
            } else {
                completeLogin(user, loginStage);
            }
        });
        task.setOnFailed(done -> {
            loginPending = false;
            if (loginStage.isShowing() && loginStage.getScene() == loginScene) {
                loginScene.getRoot().setDisable(false);
                showLoginMessage("Unable to sign in. Check the database connection.", true, false);
            }
        });

        Thread worker = new Thread(task, "bookit-password-login");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void openQrReader(ActionEvent event) {
        if (loginPending || qrWindowOpen) return;
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
            qrStage.setOnHidden(hidden -> {
                qrWindowOpen = false;
                qrController.stopScanner();
            });
            qrWindowOpen = true;
            qrStage.show();
        } catch (IOException | RuntimeException error) {
            qrWindowOpen = false;
            showError(loginStage, "QR Reader Error",
                    "The QR reader could not open. Check QRreader.fxml and its controller.");
        }
    }

    private void completeLogin(AuthenticatedUser user, Stage loginStage) {
        try {
            Navigation.showDashboard(loginStage, user);
        } catch (IOException | RuntimeException error) {
            // stay on log in screen while loading dashboard
            System.err.println("Dashboard loading failed: " + error.getClass().getSimpleName()
                    + ". Check FXML/controller/resource compatibility; raw exception details were not logged.");
            SessionManager.logout();
            showLoginMessage("The dashboard could not open. Check its FXML and controller.", true, false);
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
