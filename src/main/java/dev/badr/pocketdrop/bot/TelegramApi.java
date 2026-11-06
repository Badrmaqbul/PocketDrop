package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Telegram Bot API client over plain HTTPS (java.net.http). No third-party library: the only
 * network traffic of PocketDrop goes to api.telegram.org. Certificates and host names are verified, redirects
 * are never followed (the token is part of every address) and the token never appears in error messages.
 */
public final class TelegramApi {
    public static final class TgException extends Exception {
        private final int code;
        private final int retryAfter;

        public TgException(int code, String description, int retryAfter) {
            super(description);
            this.code = code;
            this.retryAfter = retryAfter;
        }

        public int code() { return code; }
        public int retryAfter() { return retryAfter; }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final String base;
    private final String token;

    public TelegramApi(String token) {
        this(token, System.getProperty("pocketdrop.apiBase", "https://api.telegram.org"));
    }

    /** The address can only be Telegram itself (or this computer, for the automatic tests). */
    public TelegramApi(String token, String base) {
        this.token = token;
        this.base = Gateways.checkBase(base);
    }

    private Map<String, Object> call(String method, Map<String, Object> params, Duration timeout)
            throws TgException, IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/bot" + token + "/" + method))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(params), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> body;
        try {
            body = Json.obj(Json.parse(resp.body()));
        } catch (RuntimeException e) {
            throw new IOException("Unexpected answer from Telegram (HTTP " + resp.statusCode() + ")");
        }
        if (!Json.bool(body, "ok", false)) {
            int code = (int) Json.lng(body, "error_code", resp.statusCode());
            int retry = (int) Json.lng(Json.obj(body.get("parameters")), "retry_after", 0);
            throw new TgException(code, Json.str(body, "description", "error"), retry);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", body.get("result"));
        return out;
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final Duration SHORT = Duration.ofSeconds(30);

    public Map<String, Object> getMe() throws TgException, IOException, InterruptedException {
        return Json.obj(call("getMe", params(), SHORT).get("result"));
    }

    public List<Map<String, Object>> getUpdates(long offset, int timeoutSeconds)
            throws TgException, IOException, InterruptedException {
        Object r = call("getUpdates", params("offset", offset, "timeout", timeoutSeconds,
                "allowed_updates", List.of("message")), Duration.ofSeconds(timeoutSeconds + 20L)).get("result");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.arr(r)) out.add(Json.obj(o));
        return out;
    }

    public void sendMessage(long chatId, String text) throws TgException, IOException, InterruptedException {
        call("sendMessage", params("chat_id", chatId, "text", text, "disable_web_page_preview", true), SHORT);
    }

    public void deleteMessage(long chatId, long messageId) throws TgException, IOException, InterruptedException {
        call("deleteMessage", params("chat_id", chatId, "message_id", messageId), SHORT);
    }

    public Map<String, Object> getFile(String fileId) throws TgException, IOException, InterruptedException {
        return Json.obj(call("getFile", params("file_id", fileId), SHORT).get("result"));
    }

    public void setMyCommands(List<String[]> commands) throws TgException, IOException, InterruptedException {
        List<Object> list = new ArrayList<>();
        for (String[] c : commands) list.add(params("command", c[0], "description", c[1]));
        call("setMyCommands", params("commands", list), SHORT);
    }

    /** Opens the download stream for a file path returned by getFile. The caller must close it. */
    public InputStream download(String filePath) throws IOException, InterruptedException {
        if (!Gateways.safeFilePath(filePath)) throw new IOException("Unexpected file path from Telegram");
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/file/bot" + token + "/" + filePath))
                .timeout(Duration.ofMinutes(10)).GET().build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            resp.body().close();
            throw new IOException("Download failed (HTTP " + resp.statusCode() + ")");
        }
        return resp.body();
    }
}
