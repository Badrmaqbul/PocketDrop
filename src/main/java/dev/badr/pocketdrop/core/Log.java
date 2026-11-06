package dev.badr.pocketdrop.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Simple file + in-memory logger. Bot tokens are masked before anything is written. */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR, SECURITY }

    public record Entry(long time, Level level, String message) {}

    private static final Pattern TOKEN = Pattern.compile("\\d{6,12}:[A-Za-z0-9_-]{30,}");
    private static final int MAX_MEMORY = 1000;

    private static final Deque<Entry> memory = new ArrayDeque<>();
    private static final List<Consumer<Entry>> listeners = new CopyOnWriteArrayList<>();
    private static Path dir;
    private static Level minLevel = Level.INFO;

    private Log() {}

    public static synchronized void init(Path logDir, int retentionDays) {
        dir = logDir;
        try {
            Files.createDirectories(dir);
            purgeOld(retentionDays);
            loadRecent();
        } catch (IOException e) {
            System.err.println("Log init failed: " + mask(String.valueOf(e)));
        }
    }

    public static void setMinLevel(Level l) { minLevel = l; }

    public static String mask(String s) {
        return s == null ? "" : TOKEN.matcher(s).replaceAll("<token>");
    }

    public static void debug(String m) { add(Level.DEBUG, m); }
    public static void info(String m) { add(Level.INFO, m); }
    public static void warn(String m) { add(Level.WARN, m); }
    public static void security(String m) { add(Level.SECURITY, m); }
    public static void error(String m) { add(Level.ERROR, m); }

    public static void error(String m, Throwable t) {
        StringBuilder sb = new StringBuilder(m).append(": ").append(t);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(4, st.length); i++) sb.append(" | at ").append(st[i]);
        add(Level.ERROR, sb.toString());
    }

    public static void addListener(Consumer<Entry> l) { listeners.add(l); }
    public static void removeListener(Consumer<Entry> l) { listeners.remove(l); }

    public static synchronized List<Entry> recent() {
        return new ArrayList<>(memory);
    }

    private static void add(Level level, String message) {
        if (level.ordinal() < minLevel.ordinal() && level != Level.SECURITY) return;
        Entry e = new Entry(System.currentTimeMillis(), level, mask(message).replace('\n', ' ').replace('\r', ' '));
        synchronized (Log.class) {
            memory.addLast(e);
            while (memory.size() > MAX_MEMORY) memory.removeFirst();
            writeLine(e);
        }
        for (Consumer<Entry> l : listeners) {
            try { l.accept(e); } catch (RuntimeException ignored) { }
        }
    }

    private static void writeLine(Entry e) {
        if (dir == null) return;
        Path f = dir.resolve("pocketdrop-" + LocalDate.now() + ".log");
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(e.time() + "\t" + e.level() + "\t" + e.message());
            w.newLine();
        } catch (IOException ignored) { }
    }

    private static void purgeOld(int retentionDays) throws IOException {
        if (retentionDays <= 0) return;
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith("pocketdrop-") || !n.endsWith(".log") || n.length() < 25) continue;
                try {
                    LocalDate d = LocalDate.parse(n.substring(11, 21));
                    if (d.isBefore(cutoff)) Files.deleteIfExists(p);
                } catch (RuntimeException ignored) { }
            }
        }
    }

    private static void loadRecent() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().startsWith("pocketdrop-")).forEach(files::add);
        }
        Collections.sort(files);
        List<Entry> loaded = new ArrayList<>();
        for (int i = Math.max(0, files.size() - 3); i < files.size(); i++) {
            for (String line : Files.readAllLines(files.get(i), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", 3);
                if (p.length < 3) continue;
                try {
                    loaded.add(new Entry(Long.parseLong(p[0]), Level.valueOf(p[1]), p[2]));
                } catch (RuntimeException ignored) { }
            }
        }
        int from = Math.max(0, loaded.size() - MAX_MEMORY);
        memory.clear();
        memory.addAll(loaded.subList(from, loaded.size()));
    }

    /** Writes the in-memory log to a text file chosen by the user. */
    public static synchronized void export(Path target) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Entry e : memory) {
            lines.add(Fmt.full(e.time()) + "  " + String.format("%-8s", e.level()) + e.message());
        }
        Files.write(target, lines, StandardCharsets.UTF_8);
    }

    public static long now() { return Instant.now().toEpochMilli(); }

    public static String zone() { return ZoneId.systemDefault().getId(); }
}
