package dev.badr.pocketdrop.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where PocketDrop keeps its own data (never next to the saved files). */
public final class AppDirs {
    private AppDirs() {}

    public static Path home() {
        String override = System.getProperty("pocketdrop.home");
        if (override != null && !override.isBlank()) return Path.of(override);
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Path.of(appData, "PocketDrop");
        return Path.of(System.getProperty("user.home"), ".pocketdrop");
    }

    public static Path config() { return home().resolve("config.json"); }
    public static Path history() { return home().resolve("history.jsonl"); }
    public static Path logs() { return home().resolve("logs"); }
    public static Path lock() { return home().resolve("app.lock"); }

    public static void ensure() throws IOException {
        Files.createDirectories(home());
        Files.createDirectories(logs());
    }
}
