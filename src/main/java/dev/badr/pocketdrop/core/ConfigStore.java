package dev.badr.pocketdrop.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link Config} (atomic writes) and handles the encrypted token. */
public final class ConfigStore {
    private final Path file;
    private final SecretProtector protector;
    private Config config;

    public ConfigStore(Path file, SecretProtector protector) {
        this.file = file;
        this.protector = protector;
        this.config = load();
    }

    public synchronized Config get() { return config; }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Json.pretty(config.toJson()), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("Could not save settings", e);
        }
    }

    /** The decrypted token, or null when there is none (or it cannot be decrypted any more). */
    public synchronized String token() {
        if (config.tokenEnc == null || config.tokenEnc.isEmpty()) return null;
        try {
            return protector.unprotect(config.tokenEnc);
        } catch (IOException e) {
            Log.warn("Stored token could not be decrypted: " + e.getMessage());
            return null;
        }
    }

    public synchronized void setToken(String token) throws IOException {
        config.tokenEnc = protector.protect(token);
        save();
    }

    private Config load() {
        if (!Files.exists(file)) return new Config();
        try {
            Object o = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            return Config.fromJson(Json.obj(o));
        } catch (IOException | RuntimeException e) {
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
            return new Config();
        }
    }
}
