package dev.badr.pocketdrop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.bot.TelegramApi;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.ConfigStore;
import dev.badr.pocketdrop.core.Json;
import dev.badr.pocketdrop.core.Log;
import dev.badr.pocketdrop.storage.History;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static dev.badr.pocketdrop.Check.ok;
import static dev.badr.pocketdrop.Check.section;
import static dev.badr.pocketdrop.Check.waitUntil;

/**
 * Security checks for PocketDrop's own Telegram client: certificates and host names are verified,
 * redirects are not followed, the token never leaks, file paths from Telegram are guarded and message
 * text can never change a request. Run without arguments. (With arguments it is a helper process.)
 */
public final class SecurityChecks {
    static final String TOKEN = FakeTelegram.TOKEN;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("tls")) {
            tlsChild(args[1]);
            return;
        }
        tlsDriver();
        redirects();
        tokenInErrors();
        downloadPaths();
        messageBodies();
        System.out.println("\n" + Check.passed + " passed, " + Check.failed + " failed");
        System.exit(Check.failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ certificates (separate JVMs)

    static void tlsDriver() throws Exception {
        section("Certificates and host names");
        Path dir = Files.createTempDirectory("pdtls");
        keytool(dir, "-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost",
                "-ext", "san=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", "good.p12", "-storepass", "changeit", "-keypass", "changeit");
        keytool(dir, "-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=wrong.example",
                "-ext", "san=dns:wrong.example", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", "bad.p12", "-storepass", "changeit", "-keypass", "changeit");
        for (String n : new String[]{"good", "bad"}) {
            keytool(dir, "-exportcert", "-alias", "k", "-keystore", n + ".p12", "-storepass", "changeit", "-file", n + ".cer");
            keytool(dir, "-importcert", "-noprompt", "-alias", "k", "-file", n + ".cer", "-keystore", "trust-" + n + ".p12",
                    "-storetype", "PKCS12", "-storepass", "changeit");
        }
        String r1 = child(dir, "good.p12", null);
        ok("a certificate nobody trusts is refused  [" + brief(r1) + "]", r1.startsWith("FAIL") && r1.contains("SSLHandshakeException"));
        String r2 = child(dir, "bad.p12", "trust-bad.p12");
        ok("a trusted certificate for ANOTHER host name is refused  [" + brief(r2) + "]", r2.startsWith("FAIL"));
        String r3 = child(dir, "good.p12", "trust-good.p12");
        ok("a trusted certificate with the right host name works (control)  [" + brief(r3) + "]", r3.startsWith("OK"));
    }

    static String brief(String s) { return s.length() > 100 ? s.substring(0, 100) + "..." : s; }

    static void keytool(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IllegalStateException("keytool failed: " + String.join(" ", args));
    }

    static String child(Path dir, String keystore, String truststore) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (truststore != null) {
            cmd.add("-Djavax.net.ssl.trustStore=" + dir.resolve(truststore));
            cmd.add("-Djavax.net.ssl.trustStorePassword=changeit");
        }
        cmd.addAll(List.of("-cp", System.getProperty("java.class.path"), "dev.badr.pocketdrop.SecurityChecks", "tls", dir.resolve(keystore).toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(60, TimeUnit.SECONDS);
        for (String line : out.split("\n")) if (line.startsWith("OK") || line.startsWith("FAIL")) return line.trim();
        return "FAIL no-result " + out;
    }

    static void tlsChild(String keystorePath) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(keystorePath))) { ks.load(in, "changeit".toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx));
        server.createContext("/", ex -> reply(ex, 200, "{\"ok\":true,\"result\":{\"username\":\"x\"}}"));
        server.start();
        try {
            new TelegramApi(TOKEN, "https://localhost:" + server.getAddress().getPort()).getMe();
            System.out.println("OK connected");
        } catch (Exception e) {
            System.out.println("FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + (e.getCause() != null ? " | cause: " + e.getCause() : ""));
        }
        server.stop(0);
        System.exit(0);
    }

    // ------------------------------------------------------------------ redirects

    static void redirects() throws Exception {
        section("Redirects");
        AtomicInteger stolen = new AtomicInteger();
        HttpServer evil = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        evil.createContext("/", ex -> { stolen.incrementAndGet(); reply(ex, 200, "{\"ok\":true,\"result\":{}}"); });
        evil.start();
        HttpServer redirector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirector.createContext("/", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + evil.getAddress().getPort() + "/stolen");
            ex.sendResponseHeaders(307, -1);
            ex.close();
        });
        redirector.start();
        TelegramApi api = new TelegramApi(TOKEN, "http://127.0.0.1:" + redirector.getAddress().getPort());
        boolean failed = false;
        try { api.getMe(); } catch (Exception e) { failed = true; }
        ok("a redirect is not followed (the request fails)", failed);
        ok("nothing was sent to the redirect target (token not leaked)", stolen.get() == 0);
        boolean dlFailed = false;
        try { api.download("documents/file_1.pdf").close(); } catch (Exception e) { dlFailed = true; }
        ok("a redirect on a file download is not followed either", dlFailed && stolen.get() == 0);
        redirector.stop(0);
        evil.stop(0);
    }

    // ------------------------------------------------------------------ the token in errors

    static void tokenInErrors() throws Exception {
        section("Token in error messages");
        int closed;
        try (ServerSocket s = new ServerSocket(0)) { closed = s.getLocalPort(); }
        String net = describe(() -> new TelegramApi(TOKEN, "http://127.0.0.1:" + closed).getMe());
        ok("network failure message has no token", !net.contains(TOKEN) && !net.contains("AAFake"));

        HttpServer html = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        html.createContext("/", ex -> reply(ex, 502, "<html>Bad gateway for " + ex.getRequestURI() + "</html>"));
        html.start();
        String bad = describe(() -> new TelegramApi(TOKEN, "http://127.0.0.1:" + html.getAddress().getPort()).getMe());
        ok("a hostile or broken answer that echoes the address does not reach the message", !bad.contains("AAFake"));
        String badDl = describe(() -> new TelegramApi(TOKEN, "http://127.0.0.1:" + html.getAddress().getPort()).download("documents/a.pdf").close());
        ok("download failure message has no token", !badDl.contains("AAFake"));
        html.stop(0);

        // the whole bot against an unreachable Telegram: nothing in the log may contain the token
        Path tmp = Files.createTempDirectory("pdsec");
        System.setProperty("pocketdrop.apiBase", "http://127.0.0.1:" + closed);
        System.setProperty("pocketdrop.pollTimeout", "1");
        ConfigStore store = new ConfigStore(tmp.resolve("config.json"), AllTests.FAKE_PROTECTOR);
        Config cfg = store.get();
        cfg.saveDir = tmp.resolve("saved").toString();
        store.setToken(TOKEN);
        Log.init(tmp.resolve("logs"), 90);
        BotService bot = new BotService(store, new History(tmp.resolve("h.jsonl")));
        bot.start();
        ok("bot keeps retrying while Telegram is unreachable", waitUntil(() -> bot.state() == BotService.State.RECONNECTING, 6000));
        Thread.sleep(1500);
        bot.stop();
        boolean leak = false;
        for (Log.Entry e : Log.recent()) if (e.message().contains("AAFake") || e.message().contains(TOKEN)) leak = true;
        for (Path p : Files.list(tmp.resolve("logs")).toList()) if (Files.readString(p).contains("AAFake")) leak = true;
        ok("nothing the bot wrote to its log contains the token", !leak);
        ok("log masking removes a token inside an address", !Log.mask("GET /bot" + TOKEN + "/getMe failed").contains("AAFake"));
        ok("log masking removes a token inside a file address", !Log.mask("https://api.telegram.org/file/bot" + TOKEN + "/documents/x.pdf").contains("AAFake"));
    }

    interface Call { void run() throws Exception; }

    static String describe(Call c) {
        try {
            c.run();
            return "no error";
        } catch (Throwable t) {
            StringBuilder sb = new StringBuilder();
            for (Throwable x = t; x != null; x = x.getCause()) sb.append(x).append(" | ");
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ file paths from Telegram

    static void downloadPaths() throws Exception {
        section("File paths from Telegram");
        AtomicReference<String> requested = new AtomicReference<>("");
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> { requested.set(ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery())); reply(ex, 200, "file-bytes"); });
        srv.start();
        TelegramApi api = new TelegramApi(TOKEN, "http://127.0.0.1:" + srv.getAddress().getPort());
        String[] bad = {null, "", "../etc/passwd", "a/../b", "documents/..", "a b", "a?x=1", "x#y", "a\\b", "%2e%2e/x",
                "http://evil.example/x", "docs/ü.pdf", "a\nb", "a\u0000b", "//evil.example/x", "docs/a.pdf?x=../y", "/absolute/path", "trailing/"};
        int blocked = 0;
        for (String p : bad) {
            requested.set("");
            try { api.download(p).close(); }
            catch (IOException e) { if (e.getMessage().contains("Unexpected file path") && requested.get().isEmpty()) blocked++; }
        }
        ok("every unsafe path is refused before any request is made (" + blocked + "/" + bad.length + ")", blocked == bad.length);
        requested.set("");
        try (InputStream in = api.download("documents/file_12.pdf")) {
            ok("a normal path works", new String(in.readAllBytes(), StandardCharsets.UTF_8).equals("file-bytes"));
        }
        ok("the request goes to the token address and nowhere else", requested.get().equals("/file/bot" + TOKEN + "/documents/file_12.pdf"));
        srv.stop(0);
    }

    // ------------------------------------------------------------------ user text never changes the request

    static void messageBodies() throws Exception {
        section("Text sent to Telegram");
        AtomicReference<String> body = new AtomicReference<>("");
        AtomicReference<String> path = new AtomicReference<>("");
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            path.set(ex.getRequestURI().toString());
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(b);
            body.set(b.toString(StandardCharsets.UTF_8));
            reply(ex, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
        });
        srv.start();
        TelegramApi api = new TelegramApi(TOKEN, "http://127.0.0.1:" + srv.getAddress().getPort());
        String nasty = "a?b&c=d#e\n</x> \"quote\" \\ ../ %00 \u202E تقرير {\"ok\":false}";
        api.sendMessage(42, nasty);
        Map<String, Object> sent = Json.obj(Json.parse(body.get()));
        ok("text with special characters arrives exactly as written", nasty.equals(Json.str(sent, "text")));
        ok("the address of the request does not depend on the text", path.get().equals("/bot" + TOKEN + "/sendMessage"));
        srv.stop(0);
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
