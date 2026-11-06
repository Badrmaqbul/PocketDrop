package dev.badr.pocketdrop.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All user settings. The bot token is only ever stored encrypted in {@link #tokenEnc}. */
public final class Config {
    public static final List<String> DEFAULT_BLOCKED = List.of(
            "exe", "bat", "cmd", "com", "scr", "pif", "msi", "msp", "msix", "appx", "ps1", "psm1", "vbs", "vbe",
            "js", "jse", "wsf", "wsh", "hta", "jar", "jnlp", "lnk", "url", "reg", "cpl", "dll", "sys", "msc",
            "gadget", "scf", "inf", "py", "pyw", "docm", "xlsm", "pptm", "dotm", "xlam");

    public String language;                 // "ar", "en" or null (not chosen yet)
    public boolean setupDone;
    public String tokenEnc = "";
    public long ownerId;                    // 0 = nobody linked
    public String ownerName = "";
    public String ownerUsername = "";
    public String botUsername = "";
    public String saveDir = "";
    public boolean photosSubfolder = true;
    public String photosFolderName = "photos";
    public String layout = "flat";          // flat | type | date
    public String dupStyle = "underscore";  // underscore -> name_2.ext, parens -> name (1).ext
    public int maxFileMb = 20;
    public int minFreeMb = 500;
    public boolean allowDangerous = false;
    public List<String> blockedExt = new ArrayList<>(DEFAULT_BLOCKED);
    public boolean notifications = true;
    public boolean minimizeToTray = true;
    public boolean startWithWindows = false;
    public boolean paused = false;
    public boolean undoEnabled = true;
    public int logRetentionDays = 90;

    public Config copy() {
        return fromJson(toJson());
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("language", language);
        m.put("setupDone", setupDone);
        m.put("tokenEnc", tokenEnc);
        m.put("ownerId", ownerId);
        m.put("ownerName", ownerName);
        m.put("ownerUsername", ownerUsername);
        m.put("botUsername", botUsername);
        m.put("saveDir", saveDir);
        m.put("photosSubfolder", photosSubfolder);
        m.put("photosFolderName", photosFolderName);
        m.put("layout", layout);
        m.put("dupStyle", dupStyle);
        m.put("maxFileMb", maxFileMb);
        m.put("minFreeMb", minFreeMb);
        m.put("allowDangerous", allowDangerous);
        m.put("blockedExt", new ArrayList<>(blockedExt));
        m.put("notifications", notifications);
        m.put("minimizeToTray", minimizeToTray);
        m.put("startWithWindows", startWithWindows);
        m.put("paused", paused);
        m.put("undoEnabled", undoEnabled);
        m.put("logRetentionDays", logRetentionDays);
        return m;
    }

    public static Config fromJson(Map<String, Object> m) {
        Config c = new Config();
        c.language = Json.str(m, "language");
        c.setupDone = Json.bool(m, "setupDone", false);
        c.tokenEnc = Json.str(m, "tokenEnc", "");
        c.ownerId = Json.lng(m, "ownerId", 0);
        c.ownerName = Json.str(m, "ownerName", "");
        c.ownerUsername = Json.str(m, "ownerUsername", "");
        c.botUsername = Json.str(m, "botUsername", "");
        c.saveDir = Json.str(m, "saveDir", "");
        c.photosSubfolder = Json.bool(m, "photosSubfolder", true);
        c.photosFolderName = Json.str(m, "photosFolderName", "photos");
        c.layout = oneOf(Json.str(m, "layout", "flat"), "flat", "type", "date");
        c.dupStyle = oneOf(Json.str(m, "dupStyle", "underscore"), "underscore", "parens");
        c.maxFileMb = (int) clamp(Json.lng(m, "maxFileMb", 20), 1, 20);
        c.minFreeMb = (int) clamp(Json.lng(m, "minFreeMb", 500), 0, 1_000_000);
        c.allowDangerous = Json.bool(m, "allowDangerous", false);
        if (m.get("blockedExt") instanceof List<?> l) {
            List<String> ext = new ArrayList<>();
            for (Object o : l) if (o instanceof String s && !s.isBlank()) ext.add(s.trim().toLowerCase());
            c.blockedExt = ext;
        }
        c.notifications = Json.bool(m, "notifications", true);
        c.minimizeToTray = Json.bool(m, "minimizeToTray", true);
        c.startWithWindows = Json.bool(m, "startWithWindows", false);
        c.paused = Json.bool(m, "paused", false);
        c.undoEnabled = Json.bool(m, "undoEnabled", true);
        c.logRetentionDays = (int) clamp(Json.lng(m, "logRetentionDays", 90), 1, 3650);
        return c;
    }

    public boolean hasOwner() { return ownerId != 0; }

    /** Settings that are safe to export/import (never the token, the linked account or the folder path). */
    public Map<String, Object> portableJson() {
        Map<String, Object> m = toJson();
        for (String k : new String[]{"tokenEnc", "ownerId", "ownerName", "ownerUsername", "botUsername",
                "setupDone", "saveDir", "language"}) m.remove(k);
        return m;
    }

    public void applyPortable(Config o) {
        photosSubfolder = o.photosSubfolder;
        photosFolderName = o.photosFolderName;
        layout = o.layout;
        dupStyle = o.dupStyle;
        maxFileMb = o.maxFileMb;
        minFreeMb = o.minFreeMb;
        blockedExt = new ArrayList<>(o.blockedExt);
        notifications = o.notifications;
        minimizeToTray = o.minimizeToTray;
        undoEnabled = o.undoEnabled;
        logRetentionDays = o.logRetentionDays;
        // allowDangerous is deliberately NOT imported: it must be switched on by hand, with its warning.
    }

    /** Comma separated list for the settings screen. */
    public String blockedExtText() { return String.join(", ", blockedExt); }

    public void setBlockedExtText(String text) {
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(text.split("[,;\\s]+"))) {
            String e = s.trim().toLowerCase();
            while (e.startsWith(".")) e = e.substring(1);
            if (!e.isEmpty() && e.length() <= 12 && !out.contains(e)) out.add(e);
        }
        blockedExt = out;
    }

    private static String oneOf(String v, String... allowed) {
        for (String a : allowed) if (a.equals(v)) return v;
        return allowed[0];
    }

    private static long clamp(long v, long lo, long hi) { return Math.max(lo, Math.min(hi, v)); }
}
