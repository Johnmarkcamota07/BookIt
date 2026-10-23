/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** A member's own fine, preserving the original charged amount even when paid
 * or waived. Loan and payment timestamps are stored UTC, not invented fine dates. */
public record LibraryFine(String fineId, String loanId, String bookTitle,
        String barcode, BigDecimal amount, String paymentStatus,
        LocalDateTime borrowedAt, LocalDateTime dueAt,
        LocalDateTime returnedAt, LocalDateTime paidAt) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    public LibraryFine {
        requireId(fineId);
        requireId(loanId);
        requireText(bookTitle, 255);
        requireText(barcode, 50);
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Fine amount must be a valid nonnegative DECIMAL(10,2).");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Fine amount must contain exact cents.", failure);
        }
        if (!"UNPAID".equals(paymentStatus) && !"PAID".equals(paymentStatus)
                && !"WAIVED".equals(paymentStatus)) {
            throw new IllegalArgumentException("Fine payment status is invalid.");
        }
        if ("PAID".equals(paymentStatus) != (paidAt != null)) {
            throw new IllegalArgumentException("Fine payment status and payment time do not match.");
        }
        if (borrowedAt == null || dueAt == null || !dueAt.isAfter(borrowedAt)
                || (returnedAt != null && returnedAt.isBefore(borrowedAt))) {
            throw new IllegalArgumentException("Fine loan timestamps are invalid.");
        }
    }

    public boolean isUnpaid() { return "UNPAID".equals(paymentStatus); }
    public boolean isPaid() { return "PAID".equals(paymentStatus); }
    public boolean isWaived() { return "WAIVED".equals(paymentStatus); }
    public BigDecimal outstandingAmount() {
        return isUnpaid() ? amount : new BigDecimal("0.00");
    }

    private static void requireId(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Fine and loan IDs must be positive database IDs.");
        }
        long parsed = Long.parseLong(value);
        if (parsed < 1 || parsed > 4294967295L) {
            throw new IllegalArgumentException("Fine or loan ID is outside the supported range.");
        }
    }

    private static void requireText(String value, int maximum) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("Fine book details are incomplete or invalid.");
        }
    }
}

