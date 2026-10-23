/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Exports only the report snapshot currently displayed by the administrator. */
public final class ReportExporter {
    private ReportExporter() {}

    public static void exportCsv(Window owner, AdminData.Table data, String filename) {
        FileChooser picker = new FileChooser();
        picker.setTitle("Save BookIt report");
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV report", "*.csv"));
        picker.setInitialFileName(filename.endsWith(".csv") ? filename : filename + ".csv");
        File selected = picker.showSaveDialog(owner);
        if (selected == null) return;
        String csv = toCsv(data);
        CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(selected.toPath(), csv, StandardCharsets.UTF_8);
                messageLater(owner, "Report saved", "Exported " + data.rows().size() + " record(s) to CSV.", false);
            } catch (Exception error) {
                messageLater(owner, "Export failed", "The report could not be saved. Check the selected location and permissions.", true);
            }
        });
    }

    /** Quoted UTF-8 CSV; neutralizes spreadsheet formulas in text fields. */
    public static String toCsv(AdminData.Table data) {
        StringBuilder csv = new StringBuilder("\ufeff");
        append(csv, data.headers());
        for (List<String> row : data.rows()) append(csv, row);
        return csv.toString();
    }

    private static void append(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.append(',');
            String value = values.get(i) == null ? "" : values.get(i);
            String stripped = value.stripLeading();
            if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                    || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    /** Choose a real printer or the operating system's Print-to-PDF destination. */
    public static void printReport(Window owner, String title, AdminData.Table data) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            message(owner, "No printer available", "Configure a printer or a Print-to-PDF destination, then try again. CSV export is also available.", true);
            return;
        }
        if (!job.showPrintDialog(owner)) return;
        double width = job.getJobSettings().getPageLayout().getPrintableWidth();
        double height = job.getJobSettings().getPageLayout().getPrintableHeight();
        List<VBox> pages = new ArrayList<>();
        VBox page = printPage(title, data.headers(), width, 1);
        int rowCount = 0;
        for (List<String> values : data.rows()) {
            Node row = printRow(values, width, false);
            page.getChildren().add(row);
            measure(page);
            if (page.prefHeight(width) > height) {
                page.getChildren().remove(row);
                if (rowCount == 0) {
                    job.cancelJob();
                    message(owner, "Report cannot fit", "A record is too tall for this paper size. Use larger paper or export the full report as CSV.", true);
                    return;
                }
                pages.add(page);
                page = printPage(title, data.headers(), width, pages.size() + 1);
                page.getChildren().add(row);
                measure(page);
                if (page.prefHeight(width) > height) {
                    job.cancelJob();
                    message(owner, "Report cannot fit", "A record is too tall for this paper size. Export the full report as CSV instead.", true);
                    return;
                }
                rowCount = 0;
            }
            rowCount++;
        }
        pages.add(page);
        job.getJobSettings().setJobName("BookIt - " + title);
        for (VBox part : pages) {
            if (!job.printPage(part)) {
                job.cancelJob();
                message(owner, "Printing not completed", "The print job was cancelled or could not finish.", true);
                return;
            }
        }
        if (!job.endJob()) message(owner, "Printing not completed", "The printer did not accept the complete job.", true);
    }

    private static VBox printPage(String title, List<String> headers, double width, int number) {
        Label heading = new Label("BookIt - " + title);
        heading.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: black;");
        heading.setWrapText(true); heading.setMaxWidth(width);
        Label caption = new Label("Page " + number + " | " + java.time.LocalDateTime.now().withNano(0)
                + " | " + java.time.ZoneId.systemDefault());
        caption.setStyle("-fx-font-size: 9px; -fx-text-fill: black;");
        VBox page = new VBox(8, heading, caption, printRow(headers, width, true));
        page.setPrefWidth(width); page.setMaxWidth(width);
        page.setStyle("-fx-background-color: white;");
        return page;
    }

    private static HBox printRow(List<String> values, double width, boolean header) {
        double column = width / Math.max(1, values.size());
        HBox row = new HBox();
        for (String value : values) {
            Label cell = new Label(value == null ? "" : value);
            cell.setWrapText(true); cell.setPadding(new Insets(4));
            cell.setMinWidth(column); cell.setPrefWidth(column); cell.setMaxWidth(column);
            cell.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 9px; -fx-text-fill: black;"
                    + "-fx-border-color: #d4dce3; -fx-border-width: 0 0 1 0;"
                    + (header ? "-fx-font-weight: bold; -fx-background-color: #eef3f7;" : ""));
            row.getChildren().add(cell);
        }
        return row;
    }

    private static void measure(VBox page) {
        if (page.getScene() == null) new Scene(new Group(page));
        page.applyCss(); page.autosize(); page.layout();
    }

    private static void messageLater(Window owner, String title, String text, boolean error) {
        Platform.runLater(() -> { if (owner == null || owner.isShowing()) message(owner, title, text, error); });
    }
    private static void message(Window owner, String title, String text, boolean error) {
        Alert alert = new Alert(error ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(text); alert.showAndWait();
    }
}

