//AdminData() = This method helps lock this specific filing tool so it is never accidentally duplicated.;
//How it works: It acts like a padlock on this blueprint. Because this tool is only meant to hold and organize other containers, it tells the computer to never build an empty, standalone copy of it.
//Why is it used: It keeps the system clean and organized by ensuring this piece of code is strictly used as a secure label or category, rather than cluttering up the computer's memory with unnecessary copies.
//
//Table(List headers, List<List> rows) = This method helps create a digital, tamper-proof spreadsheet grid.;
//How it works: It takes a list of column titles (headers) and the actual data (rows). First, it locks down a permanent copy of the data so no other part of the program can accidentally change it. Then, it counts everything to make absolutely sure that every single row has the exact same number of boxes as the column titles.
//Why is it used: It prevents data from getting scrambled or misaligned on the screen. If a glitch causes a row to be missing a piece of data, this method instantly catches the error before drawing a broken, confusing table for the administrator.
//
//Page(Map<String, String> metrics, Table table) = This method helps bundle up a complete, tamper-proof report page.;
//How it works: It takes the summary numbers (like "Total Books: 50") and the main spreadsheet table, checks to make sure the table isn't missing, and glues them together into one secure package. It also locks the summary numbers so no other part of the program can sneak in and secretly alter the totals after they were calculated.
//Why is it used: It guarantees that when the system hands a report over to be displayed on the administrator's dashboard, the summary numbers and the detailed list arrive safely together exactly as the database created them, without being altered during the transfer.

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable values transferred from a JDBC worker to the JavaFX thread. */
public final class AdminData {
    private AdminData() {}

    public record Table(List<String> headers, List<List<String>> rows) {
        public Table {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
            int width = headers.size();
            if (rows.stream().anyMatch(row -> row.size() != width)) {
                throw new IllegalArgumentException("Every table row must match its headers.");
            }
        }
    }

    public record Page(Map<String, String> metrics, Table table) {
        public Page {
            metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
            Objects.requireNonNull(table, "table");
        }
    }
}
