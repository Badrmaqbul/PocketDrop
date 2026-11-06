package dev.badr.pocketdrop.storage;

import dev.badr.pocketdrop.core.Json;
import dev.badr.pocketdrop.core.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** List of everything the bot saved (one JSON object per line). Read-only for the user interface. */
public final class History {
    private final Path file;
    private final List<SavedItem> items = new ArrayList<>(); // oldest first

    public History(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    Map<String, Object> m = Json.obj(Json.parse(line));
                    items.add(new SavedItem(Json.lng(m, "t", 0), Json.str(m, "name", ""),
                            Kind.of(Json.str(m, "kind", "document")), Json.lng(m, "size", 0), Json.str(m, "path", "")));
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException e) {
            Log.warn("Could not read history: " + e.getMessage());
        }
    }

    public synchronized void add(SavedItem it) {
        items.add(it);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line(it) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Log.warn("Could not write history: " + e.getMessage());
        }
    }

    public synchronized void remove(SavedItem it) {
        items.remove(it);
        rewrite();
    }

    private void rewrite() {
        StringBuilder sb = new StringBuilder();
        for (SavedItem it : items) sb.append(line(it)).append('\n');
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("Could not rewrite history: " + e.getMessage());
        }
    }

    private static String line(SavedItem it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", it.time());
        m.put("name", it.name());
        m.put("kind", it.kind().id());
        m.put("size", it.size());
        m.put("path", it.path());
        return Json.stringify(m);
    }

    /** Newest first. */
    public synchronized List<SavedItem> newestFirst() {
        List<SavedItem> out = new ArrayList<>(items);
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized List<SavedItem> last(int n) {
        List<SavedItem> all = newestFirst();
        return all.subList(0, Math.min(n, all.size()));
    }

    public synchronized SavedItem lastOrNull() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    public synchronized int countToday() {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (SavedItem it : items) {
            if (java.time.Instant.ofEpochMilli(it.time()).atZone(ZoneId.systemDefault()).toLocalDate().equals(today)) n++;
        }
        return n;
    }

    public synchronized int count() { return items.size(); }

    public synchronized long totalBytes() {
        long s = 0;
        for (SavedItem it : items) s += it.size();
        return s;
    }

    public synchronized Map<Kind, long[]> byKind() { // [count, bytes]
        Map<Kind, long[]> m = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) m.put(k, new long[2]);
        for (SavedItem it : items) {
            long[] a = m.get(it.kind());
            a[0]++;
            a[1] += it.size();
        }
        return m;
    }
}
