package dev.badr.pocketdrop.bot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Links exactly one Telegram account to the app. A long random code is shown in the app; the first
 * person who sends it becomes a candidate and must be confirmed in the app ("is this you?").
 */
public final class Pairing {
    public enum State { INACTIVE, WAITING, CONFIRMING, EXPIRED }

    public record Candidate(long userId, long chatId, long messageId,
                            String firstName, String lastName, String username) {
        public String displayName() {
            String n = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return n.isEmpty() ? "?" : n;
        }
    }

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    public static final Duration CONFIRM_LIFETIME = Duration.ofMinutes(2);

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private State state = State.INACTIVE;
    private String code = "";
    private Instant expiresAt = Instant.EPOCH;
    private Candidate candidate;

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() {
        for (Runnable r : listeners) {
            try { r.run(); } catch (RuntimeException ignored) { }
        }
    }

    /** Starts a new pairing round with a fresh code and returns it. */
    public String begin() { return begin(Instant.now()); }

    public String begin(Instant now) {
        synchronized (this) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 26; i++) sb.append(ALPHABET[RANDOM.nextInt(32)]); // 26 x 5 bits = 130 bits
            code = sb.toString();
            expiresAt = now.plus(CODE_LIFETIME);
            candidate = null;
            state = State.WAITING;
        }
        fire();
        return code();
    }

    public void cancel() {
        synchronized (this) {
            state = State.INACTIVE;
            code = "";
            candidate = null;
        }
        fire();
    }

    public synchronized State state() { return state; }
    public synchronized String code() { return code; }
    public synchronized Instant expiresAt() { return expiresAt; }
    public synchronized Candidate candidate() { return candidate; }

    /** "ABCD-EFGH-..." for showing on screen. */
    public synchronized String displayCode() {
        if (code.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * A message from somebody who is not linked yet. Returns true only for the first correct code;
     * everything else is ignored silently.
     */
    public boolean offer(String text, Candidate from, Instant now) {
        boolean accepted = false;
        synchronized (this) {
            if (state == State.WAITING && now.isBefore(expiresAt) && text != null) {
                String payload = text.trim();
                if (payload.startsWith("/start")) payload = payload.substring(6).trim();
                String n = normalize(payload);
                if (n.length() == code.length()
                        && MessageDigest.isEqual(n.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
                    state = State.CONFIRMING;
                    candidate = from;
                    expiresAt = now.plus(CONFIRM_LIFETIME);
                    accepted = true;
                }
            }
        }
        if (accepted) fire();
        return accepted;
    }

    /** The user pressed "Yes, this is me". Returns the confirmed account, or null. */
    public Candidate confirm() {
        Candidate c;
        synchronized (this) {
            if (state != State.CONFIRMING) return null;
            c = candidate;
            state = State.INACTIVE;
            code = "";
            candidate = null;
        }
        fire();
        return c;
    }

    /** The user pressed "No": the code is burned and a fresh one is generated. */
    public void reject() {
        begin();
    }

    /** Call regularly: expires the code, or auto-rejects a confirmation nobody answered. */
    public void tick(Instant now) {
        boolean changed = false;
        boolean autoReject = false;
        synchronized (this) {
            if (state == State.WAITING && !now.isBefore(expiresAt)) {
                state = State.EXPIRED;
                code = "";
                changed = true;
            } else if (state == State.CONFIRMING && !now.isBefore(expiresAt)) {
                autoReject = true;
            }
        }
        if (autoReject) reject();
        else if (changed) fire();
    }
}
