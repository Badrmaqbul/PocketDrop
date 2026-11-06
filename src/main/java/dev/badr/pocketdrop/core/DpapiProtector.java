package dev.badr.pocketdrop.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Windows DPAPI (current user scope) through the built-in PowerShell. The secret is sent through
 * standard input, never through the command line. Data encrypted this way can only be opened by the
 * same Windows user on the same machine.
 */
public final class DpapiProtector implements SecretProtector {
    private static final String HEAD =
            "$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Security;"
            + "$e=[Text.Encoding]::UTF8.GetBytes('PocketDrop.v1');"
            + "$s=[Security.Cryptography.DataProtectionScope]::CurrentUser;"
            + "$in=[Console]::In.ReadToEnd().Trim();";
    private static final String PROTECT = HEAD
            + "$b=[Text.Encoding]::UTF8.GetBytes($in);"
            + "[Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($b,$e,$s)))";
    private static final String UNPROTECT = HEAD
            + "$p=[Convert]::FromBase64String($in);"
            + "[Console]::Out.Write([Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($p,$e,$s)))";

    @Override
    public String protect(String plain) throws IOException {
        return run(PROTECT, plain);
    }

    @Override
    public String unprotect(String stored) throws IOException {
        return run(UNPROTECT, stored);
    }

    private static String run(String script, String input) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder(powershell(), "-NoProfile", "-NonInteractive",
                "-EncodedCommand", encoded);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(input.getBytes(StandardCharsets.UTF_8));
        }
        byte[] out;
        try (InputStream is = p.getInputStream()) {
            out = is.readAllBytes();
        }
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Secure storage timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
        if (p.exitValue() != 0 || out.length == 0) throw new IOException("Secure storage failed");
        return new String(out, StandardCharsets.UTF_8).trim();
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            Path p = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.exists(p)) return p.toString();
        }
        return "powershell.exe";
    }
}
