package dev.badr.pocketdrop.bot;

import java.net.URI;

/** Creates the connection to Telegram (PocketDrop's own client; no third-party Telegram library). */
public final class Gateways {
    private Gateways() {}

    /** Fails with code -1 only when the address setting is not safe (nothing is sent in that case). */
    public static Gateway create(String token) throws Gateway.GatewayException {
        try {
            return new HttpGateway(token);
        } catch (IllegalArgumentException e) {
            throw new Gateway.GatewayException(-1, String.valueOf(e.getMessage()), 0);
        }
    }

    public static boolean looksLikeToken(String t) {
        return t != null && t.matches("\\d{6,12}:[A-Za-z0-9_-]{30,}");
    }

    /**
     * The token travels inside every request address, so the address must never point to a stranger.
     * Only Telegram itself is allowed, or this computer (used by the automatic tests).
     */
    public static String checkBase(String base) {
        URI u;
        try {
            u = URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsafe API address");
        }
        String scheme = u.getScheme();
        String host = u.getHost();
        String path = u.getPath() == null ? "" : u.getPath();
        boolean telegram = "https".equals(scheme) && "api.telegram.org".equalsIgnoreCase(host)
                && u.getPort() == -1 && u.getUserInfo() == null && (path.isEmpty() || path.equals("/"))
                && u.getQuery() == null;
        boolean local = ("http".equals(scheme) || "https".equals(scheme)) && host != null
                && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"))
                && u.getUserInfo() == null;
        if (!telegram && !local) throw new IllegalArgumentException("Unsafe API address");
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Telegram file paths look like "documents/file_12.pdf": plain segments, no empty ones, no "." or "..". */
    public static boolean safeFilePath(String p) {
        if (p == null || p.isEmpty() || p.length() > 300 || !p.matches("[A-Za-z0-9_./-]+")) return false;
        for (String seg : p.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return false;
        }
        return true;
    }
}
