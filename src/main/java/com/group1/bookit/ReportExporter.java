/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
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
        try {
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
        } catch (UncheckedIOException unavailable) {
            job.cancelJob();
            message(owner, "Report layout unavailable", "The printable report layout could not be loaded. Keep ReportPrintPage.fxml, ReportPrintRow.fxml, ReportPrintCell.fxml and report-print.css in the bookit resource folder, then Clean and Build.", true);
        }
    }

    private static VBox printPage(String title, List<String> headers, double width, int number) {
        try {
            FXMLLoader view = printLayout("ReportPrintPage.fxml");
            VBox page = view.getRoot();
            Label heading = printControl(view, "reportPrintHeading", Label.class);
            heading.setText("BookIt - " + title);
            heading.setMaxWidth(width);
            printControl(view, "reportPrintCaption", Label.class).setText("Page " + number + " | "
                    + java.time.LocalDateTime.now().withNano(0) + " | " + java.time.ZoneId.systemDefault());
            page.getChildren().add(printRow(headers, width, true));
            page.setPrefWidth(width); page.setMaxWidth(width);
            return page;
        } catch (IOException unavailable) {
            throw new UncheckedIOException("The printable report page could not be loaded.", unavailable);
        }
    }

    private static HBox printRow(List<String> values, double width, boolean header) {
        try {
            double column = width / Math.max(1, values.size());
            HBox row = printLayout("ReportPrintRow.fxml").getRoot();
            for (String value : values) {
                Label cell = printLayout("ReportPrintCell.fxml").getRoot();
                cell.setText(value == null ? "" : value);
                cell.setMinWidth(column); cell.setPrefWidth(column); cell.setMaxWidth(column);
                if (header) cell.getStyleClass().add("report-print-header");
                row.getChildren().add(cell);
            }
            return row;
        } catch (IOException unavailable) {
            throw new UncheckedIOException("The printable report row could not be loaded.", unavailable);
        }
    }

    private static FXMLLoader printLayout(String filename) throws IOException {
        URL resource = ReportExporter.class.getResource("/com/group1/bookit/" + filename);
        if (resource == null) throw new IOException("Missing report print resource: " + filename);
        FXMLLoader view = new FXMLLoader(resource);
        view.load();
        return view;
    }

    private static <T> T printControl(FXMLLoader view, String name, Class<T> type) throws IOException {
        Object value = view.getNamespace().get(name);
        if (!type.isInstance(value)) throw new IOException("The report print layout is missing " + name + ".");
        return type.cast(value);
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