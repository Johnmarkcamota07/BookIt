/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

final class CameraWorker {
    private static final Object CAMERA = new Object();

    private CameraWorker() { }

    static Thread start(Runnable scan, String name) {
        Thread worker = new Thread(() -> {
            synchronized (CAMERA) {
                scan.run();
            }
        }, name);
        worker.setDaemon(true);
        worker.start();
        return worker;
    }

    static void release(AutoCloseable camera) {
        boolean interrupted = Thread.interrupted();
        try {
            camera.close();
        } catch (Exception | LinkageError ignored) {
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
