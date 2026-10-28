/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public record LibraryLoan(
        String loanId,
        String bookTitle,
        String barcode,
        LocalDateTime borrowedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt) {

    public LibraryLoan {
        Objects.requireNonNull(borrowedAt, "borrowedAt");
        Objects.requireNonNull(dueAt, "dueAt");
    }

    public boolean isOpen() {
        return returnedAt == null;
    }

    public boolean isOverdue(Instant now) {
        Objects.requireNonNull(now, "now");
        return isOpen() && dueAt.toInstant(ZoneOffset.UTC).isBefore(now);
    }

    public String status(Instant now) {
        Objects.requireNonNull(now, "now");
        return !isOpen() ? "Returned" : isOverdue(now) ? "Overdue" : "Borrowed";
    }
}

