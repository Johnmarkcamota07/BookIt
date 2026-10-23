package com.group1.bookit;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
        return new Field("password", "Initial password (15+ characters)", "", true,
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
        dialog.setTitle(title);
        dialog.setHeaderText(explanation);
        // A default Dialog header may size itself to the entire help sentence.
        // Bound and wrap it so scanner instructions fit normal laptop screens.
        Label help = new Label(explanation);
        help.setWrapText(true);
        help.setMinWidth(0);
        help.setPrefWidth(620);
        help.setMaxWidth(620);
        help.setPadding(new Insets(14));
        dialog.getDialogPane().setHeader(help);
        dialog.getDialogPane().setPrefWidth(680);
        dialog.setResizable(true);
        if (owner != null) dialog.initOwner(owner);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        java.net.URL stylesheet = AdminForms.class.getResource("bookit-dashboard.css");
        if (stylesheet != null) dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);
        grid.getStyleClass().add("admin-form");
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
                HBox line = new HBox(8, text, scan);
                HBox.setHgrow(text, Priority.ALWAYS);
                editor = line;
            }
            Label caption = new Label(spec.title() + (spec.required() ? " *" : ""));
            caption.setLabelFor(input);
            caption.setWrapText(true);
            grid.add(caption, 0, row);
            grid.add(editor, 1, row);
            GridPane.setHgrow(editor, Priority.ALWAYS);
        }
        Label error = DashboardUi.label("", "error-note");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        ScrollPane scroll = new ScrollPane(new VBox(18, grid, error));
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(620);
        scroll.setPrefViewportHeight(Math.min(580, fields.length * 58 + 75));
        dialog.getDialogPane().setContent(scroll);
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
                if ("password".equals(field.kind()) && value.codePointCount(0, value.length()) < 15) {
                    error.setText("Use an initial password of at least 15 characters.");
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

    private static String value(Control input) {
        if (input instanceof PasswordField password) return password.getText();
        if (input instanceof TextField text) return text.getText().strip();
        if (input instanceof DatePicker date) return date.getValue() == null ? "" : date.getValue().toString();
        if (input instanceof ComboBox<?> combo) return combo.getValue() == null ? "" : combo.getValue().toString();
        throw new IllegalArgumentException("Unsupported admin field.");
    }
}
