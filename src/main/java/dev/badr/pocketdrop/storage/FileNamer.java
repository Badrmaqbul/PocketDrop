package dev.badr.pocketdrop.storage;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Makes names that arrive from Telegram safe to use on Windows: removes folders (so "..\..\x" cannot
 * escape), hidden direction-control characters (used to fake extensions), reserved device names and
 * characters Windows does not allow.
 */
public final class FileNamer {
    private FileNamer() {}

    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    public static String sanitize(String raw, String fallback) {
        String s = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        int cut = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (cut >= 0) s = s.substring(cut + 1);

        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.FORMAT || type == Character.CONTROL || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR || type == Character.UNASSIGNED
                    || type == Character.SURROGATE || type == Character.PRIVATE_USE) {
                return;
            }
            if ("<>:\"|?*".indexOf(cp) >= 0) sb.appendCodePoint('_');
            else sb.appendCodePoint(cp);
        });

        String name = trimDotsAndSpaces(sb.toString());
        if (name.isEmpty()) name = fallback == null || fallback.isBlank() ? "file" : fallback;

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        if (ext.length() > 13) { base = name; ext = ""; }
        base = trimDotsAndSpaces(base);
        if (base.isEmpty()) base = fallback == null || fallback.isBlank() ? "file" : fallback;
        if (base.codePointCount(0, base.length()) > 100) {
            base = base.substring(0, base.offsetByCodePoints(0, 100));
        }
        String firstPart = base.contains(".") ? base.substring(0, base.indexOf('.')) : base;
        if (RESERVED.contains(firstPart.toUpperCase(Locale.ROOT))) base = "_" + base;
        return base + ext;
    }

    /** Lower-case extension without the dot, or "" when there is none. */
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isDangerous(String name, Collection<String> blocked) {
        String ext = extension(name);
        return !ext.isEmpty() && blocked.contains(ext);
    }

    /** name.ext -> name_2.ext (underscore style) or name (1).ext (parens style). n starts at 2. */
    public static String withSuffix(String name, int n, String style) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        if ("parens".equals(style)) return base + " (" + (n - 1) + ")" + ext;
        return base + "_" + n + ext;
    }

    private static String trimDotsAndSpaces(String s) {
        int start = 0, end = s.length();
        while (start < end && (s.charAt(start) == '.' || s.charAt(start) == ' ')) start++;
        while (end > start && (s.charAt(end - 1) == '.' || s.charAt(end - 1) == ' ')) end--;
        return s.substring(start, end);
    }
}
