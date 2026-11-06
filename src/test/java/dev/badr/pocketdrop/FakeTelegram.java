package dev.badr.pocketdrop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.badr.pocketdrop.core.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** A pretend Telegram server running on localhost, so the whole bot can be tested without internet. */
public final class FakeTelegram {
    public static final String TOKEN = "123456789:AAFakeTokenForTestsOnly_0123456789abcdef";

    final HttpServer server;
    final List<Map<String, Object>> pending = new ArrayList<>();
    final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    final List<Long> deleted = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, byte[]> files = new ConcurrentHashMap<>();
    volatile int commandsPublished;
    volatile boolean rejectToken;
    private long nextUpdate = 1;
    private long nextMessage = 100;

    private final boolean secure;

    public FakeTelegram() throws IOException {
        this(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0), false);
    }

    private FakeTelegram(HttpServer s, boolean secure) {
        this.server = s;
        this.secure = secure;
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    /** A pretend Telegram that answers HTTPS with the given certificate (used to test the real addresses). */
    public static FakeTelegram https(java.nio.file.Path keystore, String password, int port) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(keystore)) { ks.load(in, password.toCharArray()); }
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        com.sun.net.httpserver.HttpsServer hs = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        hs.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ctx));
        return new FakeTelegram(hs, true);
    }

    public String baseUrl() { return (secure ? "https://" : "http://") + "127.0.0.1:" + server.getAddress().getPort(); }

    public void stop() { server.stop(0); }

    // ----- building updates

    public synchronized long enqueue(long userId, String chatType, Map<String, Object> extra) {
        Map<String, Object> msg = new LinkedHashMap<>();
        long mid = nextMessage++;
        msg.put("message_id", mid);
        msg.put("date", System.currentTimeMillis() / 1000);
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("id", userId);
        from.put("is_bot", false);
        from.put("first_name", "User" + userId);
        from.put("username", "user" + userId);
        msg.put("from", from);
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", userId);
        chat.put("type", chatType);
        msg.put("chat", chat);
        msg.putAll(extra);
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("update_id", nextUpdate++);
        upd.put("message", msg);
        pending.add(upd);
        notifyAll();
        return mid;
    }

    synchronized void enqueueRaw(Map<String, Object> update) {
        update.put("update_id", nextUpdate++);
        pending.add(update);
        notifyAll();
    }

    public long text(long user, String text) { return enqueue(user, "private", Map.of("text", text)); }

    public long doc(long user, String name, String fileId, long size) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("file_id", fileId);
        if (name != null) d.put("file_name", name);
        d.put("file_size", size);
        return enqueue(user, "private", Map.of("document", d));
    }

    public static Map<String, Object> photoSize(String id, long size) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("file_id", id);
        p.put("file_size", size);
        return p;
    }

    public boolean sentContains(String part) {
        synchronized (sent) {
            for (String s : sent) if (s.contains(part)) return true;
        }
        return false;
    }

    int sentCount() { return sent.size(); }

    // ----- HTTP handling

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (path.startsWith("/file/bot")) {
                String rest = path.substring("/file/bot".length());
                int slash = rest.indexOf('/');
                String tok = rest.substring(0, slash);
                String key = rest.substring(slash + 1).replace("docs/", "");
                byte[] data = files.get(key);
                if (!TOKEN.equals(tok) || data == null) { reply(ex, 404, "nope".getBytes()); return; }
                reply(ex, 200, data);
                return;
            }
            if (!path.startsWith("/bot")) { reply(ex, 404, "x".getBytes()); return; }
            String rest = path.substring(4);
            int slash = rest.indexOf('/');
            String tok = rest.substring(0, slash);
            String method = rest.substring(slash + 1);
            if (rejectToken || !TOKEN.equals(tok)) {
                json(ex, 401, error(401, "Unauthorized"));
                return;
            }
            Map<String, Object> p = body.isBlank() ? new LinkedHashMap<>() : Json.obj(Json.parse(body));
            switch (method.toLowerCase(java.util.Locale.ROOT)) { // Telegram method names are not case sensitive
                case "getme" -> {
                    Map<String, Object> me = new LinkedHashMap<>();
                    me.put("id", 1L);
                    me.put("is_bot", true);
                    me.put("first_name", "PocketDrop Test");
                    me.put("username", "PocketDropTestBot");
                    json(ex, 200, ok(me));
                }
                case "getupdates" -> json(ex, 200, ok(updates(p)));
                case "sendmessage" -> {
                    sent.add(Json.str(p, "text", ""));
                    json(ex, 200, ok(Map.of("message_id", 1)));
                }
                case "deletemessage" -> {
                    deleted.add(Json.lng(p, "message_id", 0));
                    json(ex, 200, ok(true));
                }
                case "setmycommands" -> {
                    commandsPublished++;
                    json(ex, 200, ok(true));
                }
                case "getfile" -> {
                    String id = Json.str(p, "file_id", "");
                    if (id.equals("huge")) {
                        json(ex, 400, error(400, "Bad Request: file is too big"));
                    } else if (!files.containsKey(id)) {
                        json(ex, 400, error(400, "Bad Request: wrong file_id"));
                    } else {
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("file_id", id);
                        f.put("file_size", files.get(id).length);
                        f.put("file_path", "docs/" + id);
                        json(ex, 200, ok(f));
                    }
                }
                default -> json(ex, 404, error(404, "Not Found"));
            }
        } catch (RuntimeException e) {
            json(ex, 500, error(500, "fake server error: " + e));
        }
    }

    private synchronized List<Object> updates(Map<String, Object> p) {
        long offset = Json.lng(p, "offset", 0);
        long timeout = Math.min(Json.lng(p, "timeout", 1), 1);
        pending.removeIf(u -> Json.lng(u, "update_id", 0) < offset);
        long end = System.currentTimeMillis() + timeout * 1000;
        try {
            while (pending.isEmpty() && System.currentTimeMillis() < end) wait(100);
        } catch (InterruptedException ignored) { }
        pending.removeIf(u -> Json.lng(u, "update_id", 0) < offset);
        return new ArrayList<>(pending);
    }

    private static Map<String, Object> ok(Object result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("result", result);
        return m;
    }

    private static Map<String, Object> error(int code, String d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error_code", code);
        m.put("description", d);
        return m;
    }

    private static void json(HttpExchange ex, int status, Object o) throws IOException {
        reply(ex, status, Json.stringify(o).getBytes(StandardCharsets.UTF_8));
    }

    private static void reply(HttpExchange ex, int status, byte[] data) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
