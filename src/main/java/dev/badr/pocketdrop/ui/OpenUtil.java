package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.core.Log;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens files, folders and links with the system defaults (always on a background thread). */
public final class OpenUtil {
    private OpenUtil() {}

    public static void file(Path p) { async(() -> Desktop.getDesktop().open(p.toFile())); }

    public static void folder(Path dir) {
        async(() -> {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        });
    }

    /** Opens the folder and selects the file (Windows); elsewhere just opens the folder. */
    public static void reveal(Path p) {
        async(() -> {
            if (Autostart.isWindows()) {
                new ProcessBuilder("explorer.exe", "/select,", p.toAbsolutePath().toString()).start();
            } else {
                Desktop.getDesktop().open(p.toAbsolutePath().getParent().toFile());
            }
        });
    }

    public static void link(String url) {
        if (!url.startsWith("https://")) return;
        async(() -> Desktop.getDesktop().browse(URI.create(url)));
    }

    private interface Action { void run() throws IOException; }

    private static void async(Action a) {
        Thread t = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported()) return;
                a.run();
            } catch (IOException | RuntimeException e) {
                Log.warn("Could not open: " + e.getMessage());
            }
        }, "pocketdrop-open");
        t.setDaemon(true);
        t.start();
    }

    public static boolean exists(String path) {
        return path != null && Files.exists(Path.of(path));
    }

    public static File asFile(String path) { return new File(path); }
}
