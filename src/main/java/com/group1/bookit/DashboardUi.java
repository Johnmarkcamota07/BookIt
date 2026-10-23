package com.group1.bookit;

import java.util.Locale;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Small, shared layout helpers. No authentication, SQL, or saved application state. */
public final class DashboardUi {
    private DashboardUi() {}

    public static Label label(String text, String cssClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        if (cssClass != null && !cssClass.isBlank()) label.getStyleClass().add(cssClass);
        return label;
    }

    public static VBox page(Node... nodes) {
        VBox box = new VBox(22, nodes);
        box.getStyleClass().add("page-content");
        box.setFillWidth(true);
        box.setMinWidth(0);
        return box;
    }

    public static VBox section(String title, String subtitle, Node... nodes) {
        VBox box = new VBox(16);
        box.getStyleClass().add("section-card");
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        if (title != null && !title.isBlank()) box.getChildren().add(label(title, "section-title"));
        if (subtitle != null && !subtitle.isBlank()) box.getChildren().add(label(subtitle, "muted"));
        box.getChildren().addAll(nodes);
        return box;
    }

    public static HBox row(Node... nodes) {
        HBox row = new HBox(18, nodes);
        row.setAlignment(Pos.TOP_LEFT);
        row.setFillHeight(true);
        for (Node node : nodes) {
            if (node instanceof Region) HBox.setHgrow(node, Priority.ALWAYS);
        }
        return row;
    }

    public static VBox stat(String title, String value, String detail, String accent) {
        VBox card = new VBox(10,
                label(title, "stat-label"), label(value, "stat-value"), label(detail, "stat-detail"));
        card.getStyleClass().addAll("stat-card", "accent-" + accent);
        card.setMinWidth(0);
        card.setPrefWidth(220);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    public static Button button(String text, String cssClass, Runnable action) {
        Button button = new Button(text);
        button.setMnemonicParsing(false);
        button.getStyleClass().add(cssClass);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        if (action != null) button.setOnAction(event -> action.run());
        return button;
    }

    public static TableView<ObservableList<String>> table(String[] headers, String[][] rows) {
        TableView<ObservableList<String>> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setEditable(false);
        table.setMinWidth(0);
        table.setMinHeight(180);
        table.setPrefHeight(Math.min(360, Math.max(220, rows.length * 46 + 54)));
        table.setPlaceholder(label("No matching records.", "muted"));
        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            final int index = columnIndex;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(headers[index]);
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(index)));
            column.setCellFactory(ignored -> new TableCell<>() {
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    setTooltip(empty || value == null ? null : new Tooltip(value));
                }
            });
            column.setMinWidth(74);
            column.setPrefWidth(index == 0 ? 190 : 130);
            table.getColumns().add(column);
        }
        for (String[] values : rows) {
            if (values.length != headers.length) throw new IllegalArgumentException("Table row width mismatch.");
            table.getItems().add(FXCollections.observableArrayList(values));
        }
        return table;
    }

    /** Search all visible fields; sorting is reapplied after every search change. */
    public static VBox searchableTable(String prompt, String[] headers, String[][] rows) {
        return searchableTable(prompt, headers, rows, true);
    }

    public static VBox searchableTable(String prompt, String[] headers, String[][] rows, boolean sample) {
        TableView<ObservableList<String>> table = table(headers, rows);
        FilteredList<ObservableList<String>> filtered = new FilteredList<>(table.getItems());
        javafx.collections.transformation.SortedList<ObservableList<String>> sorted =
                new javafx.collections.transformation.SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText(prompt);
        search.setAccessibleText(prompt);
        search.getStyleClass().add("search-field");
        search.setPrefWidth(360);
        search.setMaxWidth(500);
        String suffix = sample ? " sample records" : " records";
        Label count = label(rows.length + suffix, "muted");
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(values -> values.stream().anyMatch(value ->
                    value.toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + rows.length + suffix);
        });
        HBox toolbar = new HBox(16, search, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        return new VBox(16, toolbar, table);
    }

    public static VBox emptyState(String title, String detail) {
        VBox box = new VBox(12, label(title, "section-title"), label(detail, "muted"));
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setMinHeight(160);
        return box;
    }

    public static Button unavailable(String text, String reason) {
        Button button = button(text, "secondary-button", null);
        button.setDisable(true);
        button.setTooltip(new Tooltip(reason));
        // Disabled controls cannot always receive mouse events. Keep the explanation
        // visible in the page as well, rather than relying on a tooltip alone.
        button.setAccessibleHelp(reason);
        return button;
    }

    public static void previewDialog(Node owner, String title, Node content) {
        detailDialog(owner, title, content, "Read-only preview - sample data");
    }

    public static void detailDialog(Node owner, String title, Node content, String headerText) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);
        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(380);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        java.net.URL css = DashboardUi.class.getResource("bookit-dashboard.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        dialog.showAndWait();
    }
}
