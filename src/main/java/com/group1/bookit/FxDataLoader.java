/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;

//background worker po ini syug
public final class FxDataLoader implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private Future<?> pending;
    private long version;
    private boolean closed;

    public FxDataLoader() {
        executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, "bookit-database-reader");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
    }

    public <T> void load(Callable<T> query, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        requireFxThread();
        Objects.requireNonNull(query);
        Objects.requireNonNull(onSuccess);
        Objects.requireNonNull(onFailure);
        if (closed) throw new IllegalStateException("This screen's data loader is closed.");
        cancel();
        long request = version;
        pending = executor.submit(() -> {
            try {
                T result = query.call();
                deliver(request, () -> onSuccess.accept(result));
            } catch (Exception error) {
                deliver(request, () -> onFailure.accept(error));
            }
        });
    }

    public void cancel() {
        requireFxThread();
        version++;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        executor.purge();
    }

    @Override public void close() {
        requireFxThread();
        if (closed) return;
        cancel();
        closed = true;
        executor.shutdownNow();
    }

    private void deliver(long request, Runnable action) {
        try {
            Platform.runLater(() -> {
                if (!closed && request == version) action.run();
            });
        } catch (IllegalStateException ignored) {
            
        }
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Manage screen data loads on the JavaFX thread.");
        }
    }
}
