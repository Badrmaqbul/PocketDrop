package dev.badr.pocketdrop.core;

import java.io.IOException;

/** Encrypts small secrets (the bot token) so they are never stored as plain text. */
public interface SecretProtector {
    /** Returns a text form (Base64) that is safe to store. */
    String protect(String plain) throws IOException;

    String unprotect(String stored) throws IOException;

    /** The protector used by the real app: Windows account-bound encryption (DPAPI). */
    static SecretProtector systemDefault() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return new DpapiProtector();
        return new SecretProtector() {
            @Override public String protect(String plain) throws IOException {
                throw new IOException("Secure storage is only available on Windows");
            }
            @Override public String unprotect(String stored) throws IOException {
                throw new IOException("Secure storage is only available on Windows");
            }
        };
    }
}
