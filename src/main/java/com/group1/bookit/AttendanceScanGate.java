/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.util.concurrent.TimeUnit;

/** Pure camera debounce state: an accepted QR must leave view before another attempt. */
final class AttendanceScanGate {
    private static final long COOLDOWN = TimeUnit.SECONDS.toNanos(2);
    private static final long ABSENCE = TimeUnit.MILLISECONDS.toNanos(750);
    private boolean armed = true;
    private boolean attempted;
    private long lastAttempt;
    private long absenceStarted;
    private int absentFrames;

    // Call once per actual image frame; null is a successful frame with no QR found.
    boolean accept(String qr, long now) {
        if (qr == null) {
            if (absentFrames++ == 0) absenceStarted = now;
            if (absentFrames >= 3 && now - absenceStarted >= ABSENCE) armed = true;
            return false;
        }
        absentFrames = 0;
        if (!armed || (attempted && now - lastAttempt < COOLDOWN)) return false;
        armed = false;
        attempted = true;
        lastAttempt = now;
        return true;
    }
}

