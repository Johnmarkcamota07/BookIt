//accept(String qr, long now) = This method helps prevent the camera from accidentally scanning the same QR code dozens of times in a row.;
//How it works: It acts like a digital toll booth gate. Every fraction of a second, the camera reports what it sees. If it sees a QR code, the gate accepts the code and instantly locks itself. To unlock the gate for the next scan, two things must happen: a two-second cooldown timer must finish, and the camera must clearly see that the QR code has been physically moved out of the frame for almost a full second.
//Why is it used: Because video cameras take dozens of pictures every second, simply holding your phone in front of the lens would normally cause the system to scan your code 30 times in a single second, logging you in and out instantly. This method forces the system to process one code, pause, and wait for the camera's view to clear before it allows the next person to scan.

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

