package com.group1.bookit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Locale;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
        VBox box = layout("DashboardPage.fxml").getRoot();
        box.getChildren().addAll(nodes);
        return box;
    }

    public static VBox section(String title, String subtitle, Node... nodes) {
        FXMLLoader view = layout("DashboardSection.fxml");
        VBox box = view.getRoot();
        Label heading = node(view, "sectionTitle", Label.class);
        Label description = node(view, "sectionSubtitle", Label.class);
        if (title == null || title.isBlank()) box.getChildren().remove(heading);
        else heading.setText(title);
        if (subtitle == null || subtitle.isBlank()) box.getChildren().remove(description);
        else description.setText(subtitle);
        box.getChildren().addAll(nodes);
        return box;
    }

    public static HBox row(Node... nodes) {
        HBox row = layout("DashboardRow.fxml").getRoot();
        row.getChildren().addAll(nodes);
        for (Node node : nodes) {
            if (node instanceof Region) HBox.setHgrow(node, Priority.ALWAYS);
        }
        return row;
    }

    public static VBox stat(String title, String value, String detail, String accent) {
        FXMLLoader view = layout("DashboardStat.fxml");
        VBox card = view.getRoot();
        node(view, "statTitle", Label.class).setText(title);
        node(view, "statValue", Label.class).setText(value);
        node(view, "statDetail", Label.class).setText(detail);
        card.getStyleClass().add("accent-" + accent);
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
        TableView<ObservableList<String>> table = layout("DashboardTable.fxml").getRoot();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(Math.min(360, Math.max(220, rows.length * 46 + 54)));
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
        FXMLLoader view = layout("DashboardSearch.fxml");
        VBox box = view.getRoot();
        TextField search = node(view, "searchInput", TextField.class);
        search.setPromptText(prompt);
        search.setAccessibleText(prompt);
        String suffix = sample ? " sample records" : " records";
        Label count = node(view, "searchCount", Label.class);
        count.setText(rows.length + suffix);
        search.textProperty().addListener((observable, before, after) -> {
            String query = after.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(values -> values.stream().anyMatch(value ->
                    value.toLowerCase(Locale.ROOT).contains(query)));
            count.setText(filtered.size() + " of " + rows.length + suffix);
        });
        box.getChildren().add(table);
        return box;
    }

    public static VBox emptyState(String title, String detail) {
        FXMLLoader view = layout("DashboardEmpty.fxml");
        node(view, "emptyTitle", Label.class).setText(title);
        node(view, "emptyDetail", Label.class).setText(detail);
        return view.getRoot();
    }

    public static Button unavailable(String text, String reason) {
        Button button = button(text, "secondary-button", null);
        button.setDisable(true);
        button.setTooltip(new Tooltip(reason));
        button.setAccessibleHelp(reason);
        return button;
    }

    public static void previewDialog(Node owner, String title, Node content) {
        detailDialog(owner, title, content, "Read-only preview - sample data");
    }

    public static void detailDialog(Node owner, String title, Node content, String headerText) {
        FXMLLoader view = layout("DashboardDetail.fxml");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setDialogPane(view.getRoot());
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);
        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        node(view, "detailScroll", ScrollPane.class).setContent(content);
        dialog.showAndWait();
    }

    private static FXMLLoader layout(String filename) {
        URL location = DashboardUi.class.getResource("/com/group1/bookit/" + filename);
        if (location == null) {
            throw new UncheckedIOException(new IOException(filename + " is missing from the bookit resource folder."));
        }
        FXMLLoader loader = new FXMLLoader(location);
        try {
            loader.load();
            return loader;
        } catch (IOException failure) {
            throw new UncheckedIOException("The shared dashboard layout could not be loaded: " + filename, failure);
        }
    }

    private static <T> T node(FXMLLoader loader, String name, Class<T> type) {
        Object value = loader.getNamespace().get(name);
        if (!type.isInstance(value)) throw new IllegalStateException("The shared dashboard layout is missing " + name + ".");
        return type.cast(value);
    }
}
