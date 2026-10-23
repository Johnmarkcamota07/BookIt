/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

/**
 *
 * @author John Mark
 */
import java.time.LocalDateTime;

/** A member's own reservation. Stored timestamps are UTC; queue position is
 * null for completed history and starts at one for active reservations. */
public record LibraryReservation(String reservationId, String bookId,
        String bookTitle, LocalDateTime reservedAt, String status, Long queuePosition) {
    public boolean isActive() {
        return "PENDING".equals(status) || "READY".equals(status);
    }
}
