package com.haramisi98.LingoScape;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded in-memory aggregation. All disk operations belong on the plugin's worker. */
public final class MissingTextCollector {
    public static final int MAX_ENTRIES = 10_000;
    public static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final int limit;
    private final long byteLimit;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long estimatedBytes = 2;
    private boolean full;
    private boolean dirty;

    static final class Entry {
        String language, category, text, reason;
        long count, firstSeen, lastSeen;
        Entry(String language, String category, String text, String reason, long now) {
            this.language = language; this.category = category; this.text = text; this.reason = reason;
            count = 1; firstSeen = now; lastSeen = now;
        }
        Entry copy() {
            Entry copy = new Entry(language, category, text, reason, firstSeen);
            copy.count = count; copy.lastSeen = lastSeen; return copy;
        }
    }

    public MissingTextCollector(Path directory) { this(directory, MAX_ENTRIES, MAX_BYTES); }
    MissingTextCollector(Path directory, int limit, long byteLimit) {
        this.directory = directory; this.limit = limit; this.byteLimit = byteLimit;
    }
    private static String key(Entry e) { return e.language + "\n" + e.category + "\n" + e.text; }
    // Conservative JSON escaping allowance; also leaves room for growing counters/timestamps.
    private static long size(Entry e) { return 512L + e.text.length() * 6L; }

    public synchronized void record(String language, TranslationType type, String text, String reason) {
        if ("en".equals(language) || text == null || text.isBlank() || text.length() > 2048) return;
        Entry incoming = new Entry(language, type.name(), text, reason, System.currentTimeMillis());
        Entry existing = entries.get(key(incoming));
        if (existing != null) {
            if (existing.count < Long.MAX_VALUE) existing.count++;
            existing.lastSeen = incoming.lastSeen; existing.reason = reason; dirty = true; return;
        }
        if (entries.size() >= limit || estimatedBytes + size(incoming) > byteLimit) { full = true; return; }
        estimatedBytes += size(incoming); entries.put(key(incoming), incoming); dirty = true;
    }

    public synchronized String status() {
        return entries.size() + " candidates" + (full ? " — collection full" : "");
    }

    public void load() throws IOException {
        Path path = directory.resolve("missing.json");
        if (!Files.exists(path)) return;
        if (Files.size(path) > byteLimit) throw new IOException("Missing-text file exceeds size limit");
        List<Entry> loaded;
        try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loaded = JSON.fromJson(reader, new TypeToken<List<Entry>>() {}.getType());
        } catch (RuntimeException ex) { throw new IOException("Invalid missing-text file", ex); }
        if (loaded == null) throw new IOException("Invalid missing-text file");
        synchronized (this) {
            for (Entry e : loaded) {
                if (e == null || e.language == null || !e.language.matches("[a-z]{2}") || "en".equals(e.language)
                    || e.category == null || e.text == null || e.text.isBlank() || e.text.length() > 2048
                    || e.reason == null || !Arrays.asList("KNOWN_UNTRANSLATED", "UNKNOWN_TEXT", "POSSIBLE_LOOKUP_MISMATCH").contains(e.reason))
                    throw new IOException("Invalid candidate");
                try { TranslationType.valueOf(e.category); } catch (IllegalArgumentException ex) { throw new IOException(ex); }
                if (entries.containsKey(key(e))) continue;
                if (entries.size() >= limit || estimatedBytes + size(e) > byteLimit) { full = true; break; }
                entries.put(key(e), e); estimatedBytes += size(e);
            }
        }
    }

    public void flush() throws IOException {
        List<Entry> snapshot;
        synchronized (this) {
            if (!dirty) return;
            snapshot = new ArrayList<>();
            entries.values().forEach(e -> snapshot.add(e.copy()));
            dirty = false;
        }
        try { write(directory.resolve("missing.json"), JSON.toJson(snapshot)); }
        catch (IOException ex) { synchronized (this) { dirty = true; } throw ex; }
    }

    public Path export() throws IOException {
        flush();
        Path source = directory.resolve("missing.json");
        Path target = directory.resolve("missing-export.json");
        write(target, Files.exists(source) ? Files.readString(source, StandardCharsets.UTF_8) : "[]");
        return target;
    }

    private void write(Path target, String contents) throws IOException {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > byteLimit) throw new IOException("Candidate output exceeds size limit");
        Files.createDirectories(directory);
        Path temporary = directory.resolve("missing.tmp");
        Files.write(temporary, bytes);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
