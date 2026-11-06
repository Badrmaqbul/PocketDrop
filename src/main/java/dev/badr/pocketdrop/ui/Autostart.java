package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.core.AppInfo;
import dev.badr.pocketdrop.core.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** "Start with Windows" through the current user's Run key (no admin rights, nothing system-wide). */
public final class Autostart {
    private Autostart() {}

    private static final String KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Path of the installed PocketDrop.exe, or null when running from an IDE / jar. */
    public static String installedExe() {
        String cmd = ProcessHandle.current().info().command().orElse(null);
        if (cmd != null && cmd.toLowerCase().endsWith("pocketdrop.exe")) return cmd;
        return null;
    }

    public static boolean available() { return isWindows() && installedExe() != null; }

    public static boolean set(boolean enabled) {
        if (!available()) return false;
        try {
            Process p;
            if (enabled) {
                p = new ProcessBuilder("reg", "add", KEY, "/v", AppInfo.NAME, "/t", "REG_SZ",
                        "/d", "\"" + installedExe() + "\" --minimized", "/f").redirectErrorStream(true).start();
            } else {
                p = new ProcessBuilder("reg", "delete", KEY, "/v", AppInfo.NAME, "/f").redirectErrorStream(true).start();
            }
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && (p.exitValue() == 0 || !enabled);
        } catch (IOException | InterruptedException e) {
            Log.warn("Could not change the start-with-Windows setting: " + e.getMessage());
            return false;
        }
    }
}
