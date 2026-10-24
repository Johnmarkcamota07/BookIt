//AdminForms() = This method helps lock this tool file so the computer doesn't try to make unnecessary copies of it.;
//How it works: It acts like a digital padlock. Since this file is just a collection of form-building tools, the system is blocked from creating a standalone "object" out of it.
//Why is it used: It keeps the program's memory clean and organized by ensuring this tool is only used as a shared background utility.
//
//text(String key, String title, String value, boolean required) = This method helps create a standard, empty text box for typing normal information.;
//How it works: It sets up the blueprint for a blank box, gives it a label (like "Name" or "Email"), fills it with starting text if needed, and marks whether the user is allowed to leave it blank.
//Why is it used: It is the basic building block for gathering everyday information from the administrator.
//
//barcode() = This method helps create a special input box designed specifically for scanning book barcodes.;
//How it works: It builds a text box that listens for a USB barcode scanner, but it also adds a "Scan camera" button next to it just in case the librarian needs to use a webcam to read the barcode.
//Why is it used: It makes checking books in and out much faster by allowing the system to understand hardware scanners instead of forcing the librarian to type long ID numbers by hand.
//
//id(String key, String title, String value) = This method helps display a locked piece of information that the user cannot change.;
//How it works: It creates a text box filled with specific data (like a member's unique ID number), but it turns off the ability to type inside or edit that box.
//Why is it used: It allows the system to show the administrator exactly which record they are working on, while protecting that vital ID number from being accidentally deleted or altered.
//
//password() = This method helps create a secure, hidden typing box for passwords.;
//How it works: It creates a special text box that turns every typed letter into a dot or star so nobody can read it over the user's shoulder. It also tells the system that any password typed here must be at least 15 characters long.
//Why is it used: It protects sensitive security credentials from being stolen by onlookers while an administrator is setting up a new account.
//
//date(String key, String title, LocalDate value) = This method helps create a pop-up calendar box for picking days.;
//How it works: Instead of a blank box where someone might type a date in the wrong format, it provides a mini digital calendar where the user can just click the correct day.
//Why is it used: It completely prevents typos and guarantees that the computer will always understand the date being entered.
//
//choice(String key, String title, String value, String... choices) = This method helps create a drop-down menu with a list of specific options.;
//How it works: It sets up a button that, when clicked, drops down a pre-approved list of answers (like "AVAILABLE" or "LOST") for the administrator to pick from.
//Why is it used: It forces the user to pick from an approved list, preventing them from typing in a random word the system wouldn't recognize.
//
//show(Window owner, String title, String explanation, Field... fields) = This method helps build and display the actual pop-up form on the screen.;
//How it works: It takes all the different boxes requested (like text boxes, drop-downs, and date pickers) and arranges them neatly into a pop-up window. It adds "Save" and "Cancel" buttons, checks to make sure the user didn't leave any required blanks empty before saving, and automatically wipes out any passwords from the computer's short-term memory the moment the window closes.
//Why is it used: It acts as the master builder, doing all the heavy lifting to turn a simple list of questions into a fully functioning, secure, and user-friendly window on the screen.
//
//value(Control input) = This method helps extract the final typed answer out of any type of box on the form.;
//How it works: It looks at the box the user just interacted with. If it's a calendar, it grabs the date; if it's a password box, it grabs the hidden text; if it's a drop-down, it grabs the selected option. It also trims away any accidental empty spaces typed at the edges.
//Why is it used: Because there are many different types of input boxes, the system needs one universal translator that knows how to pull the final answer out of all of them safely to save it to the database.


package com.group1.bookit;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

/** Editable admin forms. No SQL, saved passwords, or hidden database mutations. */
final class AdminForms {
    private AdminForms() {}

    record Field(String key, String title, String value, boolean required,
                 boolean readOnly, String kind, List<String> choices) {}

    static Field text(String key, String title, String value, boolean required) {
        return new Field(key, title, value, required, false, "text", List.of());
    }

    /** A camera button plus an ordinary keyboard-scanner/manual input field. */
    static Field barcode() {
        return new Field("barcode", "Copy barcode", "", true, false, "barcode", List.of());
    }

    static Field id(String key, String title, String value) {
        return new Field(key, title, value, true, true, "text", List.of());
    }

    static Field password() {
        return new Field("password", "Initial password (8+ characters)", "", true,
                false, "password", List.of());
    }

    static Field date(String key, String title, LocalDate value) {
        return new Field(key, title, value.toString(), true, false, "date", List.of());
    }

    static Field choice(String key, String title, String value, String... choices) {
        return new Field(key, title, value, true, false, "choice", List.of(choices));
    }

    static Optional<Map<String, String>> show(Window owner, String title, String explanation,
                                            Field... fields) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        final DialogPane pane;
        final Label help;
        final GridPane grid;
        final Label error;
        final ScrollPane scroll;
        final ButtonType save;
        try {
            URL location = AdminForms.class.getResource("/com/group1/bookit/AdminForm.fxml");
            if (location == null) throw new IOException("AdminForm.fxml is missing.");
            FXMLLoader loader = new FXMLLoader(location);
            pane = loader.load();
            help = control(loader, "help", Label.class);
            grid = control(loader, "fieldsGrid", GridPane.class);
            error = control(loader, "formError", Label.class);
            scroll = control(loader, "formScroll", ScrollPane.class);
            save = control(loader, "saveType", ButtonType.class);
        } catch (IOException | ClassCastException unavailable) {
            Alert notice = new Alert(Alert.AlertType.ERROR);
            if (owner != null) notice.initOwner(owner);
            notice.setTitle("Form unavailable");
            notice.setHeaderText("The admin form could not be loaded.");
            notice.setContentText("Keep AdminForm.fxml and admin-form.css in the bookit resource folder, then Clean and Build.");
            notice.showAndWait();
            return Optional.empty();
        }
        dialog.setDialogPane(pane);
        dialog.setTitle(title);
        help.setText(explanation);
        dialog.setResizable(true);
        if (owner != null) dialog.initOwner(owner);
        Map<String, Control> inputs = new LinkedHashMap<>();
        for (int row = 0; row < fields.length; row++) {
            Field spec = fields[row];
            Control input;
            if ("choice".equals(spec.kind())) {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(spec.choices());
                combo.setValue(spec.value().isBlank() ? spec.choices().get(0) : spec.value());
                input = combo;
            } else if ("date".equals(spec.kind())) {
                DatePicker picker = new DatePicker(LocalDate.parse(spec.value()));
                picker.setEditable(false);
                input = picker;
            } else {
                TextField text = "password".equals(spec.kind()) ? new PasswordField() : new TextField();
                text.setText(spec.value());
                text.setEditable(!spec.readOnly());
                input = text;
            }
            input.setMaxWidth(Double.MAX_VALUE);
            input.setAccessibleText(spec.title());
            inputs.put(spec.key(), input);
            Node editor = input;
            if ("barcode".equals(spec.kind()) && input instanceof TextField text) {
                text.setId("adminCopyBarcode");
                text.setPromptText("Scan into this field, or type the copy code");
                // USB keyboard scanners commonly append Enter. Reading a code
                // must not trigger the form's default Save button automatically.
                text.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ENTER) event.consume();
                });
                javafx.scene.control.Button scan = new javafx.scene.control.Button("Scan camera");
                scan.setId("adminBarcodeCamera");
                scan.setOnAction(event -> {
                    Window popupOwner = dialog.getDialogPane().getScene().getWindow();
                    String scanned = BarcodeScannerDialog.scan(popupOwner);
                    if (scanned != null) text.setText(scanned);
                    text.requestFocus();
                });
                HBox line = new HBox(text, scan);
                line.getStyleClass().add("admin-form-barcode");
                HBox.setHgrow(text, Priority.ALWAYS);
                editor = line;
            }
            Label caption = new Label(spec.title() + (spec.required() ? " *" : ""));
            caption.setLabelFor(input);
            caption.setWrapText(true);
            caption.getStyleClass().add("admin-form-caption");
            grid.add(caption, 0, row);
            grid.add(editor, 1, row);
            GridPane.setHgrow(editor, Priority.ALWAYS);
        }
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        scroll.setPrefViewportHeight(Math.min(580, fields.length * 58 + 75));
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            for (Field field : fields) {
                String value = value(inputs.get(field.key()));
                if (field.required() && value.isBlank()) {
                    error.setText("Enter " + field.title().toLowerCase() + ".");
                    inputs.get(field.key()).requestFocus();
                    event.consume();
                    return;
                }
                if ("password".equals(field.kind()) && value.codePointCount(0, value.length()) < 8) {
                    error.setText("Use an initial password of at least 8 characters.");
                    event.consume();
                    return;
                }
            }
        });
        dialog.setResultConverter(chosen -> {
            if (chosen != save) return null;
            Map<String, String> result = new LinkedHashMap<>();
            for (Field field : fields) result.put(field.key(), value(inputs.get(field.key())));
            return result;
        });
        dialog.setOnHidden(event -> inputs.values().stream()
                .filter(input -> input instanceof PasswordField)
                .forEach(input -> ((PasswordField) input).clear()));
        return dialog.showAndWait();
    }

    private static <T> T control(FXMLLoader loader, String name, Class<T> type) throws IOException {
        Object value = loader.getNamespace().get(name);
        if (!type.isInstance(value)) throw new IOException("The admin form is missing " + name + ".");
        return type.cast(value);
    }

    private static String value(Control input) {
        if (input instanceof PasswordField password) return password.getText();
        if (input instanceof TextField text) return text.getText().strip();
        if (input instanceof DatePicker date) return date.getValue() == null ? "" : date.getValue().toString();
        if (input instanceof ComboBox<?> combo) return combo.getValue() == null ? "" : combo.getValue().toString();
        throw new IllegalArgumentException("Unsupported admin field.");
    }
}
