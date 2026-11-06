package dev.badr.pocketdrop.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small formatting helpers (sizes and times). Digits are always Western (0-9). */
public final class Fmt {
    private Fmt() {}

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int u = 0;
        while (v >= 1024 && u < units.length - 1) { v /= 1024; u++; }
        return String.format(Locale.ROOT, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[u]);
    }

    /** "14:32" for today, "10-28 14:32" otherwise. */
    public static String when(long epochMillis) {
        var t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
        if (t.toLocalDate().equals(LocalDate.now())) return HM.format(t);
        return MD_HM.format(t);
    }

    public static String full(long epochMillis) {
        return FULL.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
