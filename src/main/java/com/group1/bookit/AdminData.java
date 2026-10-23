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
