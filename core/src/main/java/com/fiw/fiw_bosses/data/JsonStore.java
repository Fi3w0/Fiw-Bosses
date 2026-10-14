package com.fiw.fiw_bosses.data;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One JSON state file under config/fiw_bosses/data/. Reads happen on load; every change
 * is serialized on the caller's (server) thread and written by a background thread with
 * an atomic temp-file move, so the tick never waits on disk and a crash mid-write leaves
 * the previous file intact.
 */
public final class JsonStore<T> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // One non-daemon writer that exits when idle: pending writes finish before the JVM
    // exits normally, and an idle server holds no extra thread.
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "FIW-Bosses-Data");
                t.setDaemon(false);
                return t;
            });

    private final String fileName;
    private final Class<T> type;
    private final Supplier<T> fresh;
    private Path file;
    private T data;

    public JsonStore(String fileName, Class<T> type, Supplier<T> fresh) {
        this.fileName = fileName;
        this.type = type;
        this.fresh = fresh;
        this.data = fresh.get();
    }

    public T get() {
        return data;
    }

    /** Loads from {@code dir}/fileName; a missing or broken file starts empty (broken ones are kept as .broken). */
    public void load(Path dir) {
        file = dir.resolve(fileName);
        data = fresh.get();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            if (parsed != null) data = parsed;
        } catch (Exception e) {
            FiwBossesCore.LOGGER.error("Failed to read {}: {} — starting empty, old file kept as {}.broken",
                    file, e.getMessage(), fileName);
            try {
                Files.move(file, file.resolveSibling(fileName + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Leave it; the next save overwrites it.
            }
        }
    }

    /** Snapshots the current data and writes it in the background. */
    public void save() {
        if (file == null) return;
        String json = GSON.toJson(data);
        Path target = file;
        IO.execute(() -> write(target, json));
    }

    /** Waits up to a few seconds for pending writes (tests, shutdown). */
    public static void flush() {
        try {
            IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            FiwBossesCore.LOGGER.warn("Timed out waiting for FIW Bosses data writes: {}", e.getMessage());
        }
    }

    private static void write(Path target, String json) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FiwBossesCore.LOGGER.error("Failed to save {}: {}", target, e.getMessage());
        }
    }
}
