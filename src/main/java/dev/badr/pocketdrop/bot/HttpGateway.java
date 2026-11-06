package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.bot.TelegramApi.TgException;
import dev.badr.pocketdrop.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PocketDrop's own Telegram connection (java.net.http). No third-party code. */
final class HttpGateway implements Gateway {
    private final TelegramApi api;
    private long offset;

    HttpGateway(String token) {
        this.api = new TelegramApi(token);
    }

    private static GatewayException wrap(TgException e) { return new GatewayException(e.code(), e.getMessage(), e.retryAfter()); }

    private static GatewayException wrap(IOException e) { return new GatewayException(0, String.valueOf(e.getMessage()), 0); }

    @Override
    public String getMe() throws GatewayException, InterruptedException {
        try {
            return Json.str(api.getMe(), "username", "");
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public List<Incoming> poll(int timeoutSeconds) throws GatewayException, InterruptedException {
        try {
            List<Incoming> out = new ArrayList<>();
            for (Map<String, Object> u : api.getUpdates(offset, timeoutSeconds)) {
                long id = Json.lng(u, "update_id", 0);
                offset = Math.max(offset, id + 1);
                out.add(fromJson(u));
            }
            return out;
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public void sendMessage(long chatId, String text) throws GatewayException, InterruptedException {
        try {
            api.sendMessage(chatId, text);
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public void deleteMessage(long chatId, long messageId) throws GatewayException, InterruptedException {
        try {
            api.deleteMessage(chatId, messageId);
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public FileRef getFile(String fileId) throws GatewayException, InterruptedException {
        try {
            Map<String, Object> f = api.getFile(fileId);
            String path = Json.str(f, "file_path");
            if (path == null) throw new GatewayException(0, "Telegram did not return a file path", 0);
            return new FileRef(path, Json.lng(f, "file_size", 0));
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public InputStream download(FileRef file) throws GatewayException, InterruptedException {
        try {
            return api.download(file.path());
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public void setCommands(List<String[]> commands) throws GatewayException, InterruptedException {
        try {
            api.setMyCommands(commands);
        } catch (TgException e) {
            throw wrap(e);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ JSON -> Incoming

    static Incoming fromJson(Map<String, Object> update) {
        long updateId = Json.lng(update, "update_id", 0);
        Map<String, Object> msg = Json.obj(update.get("message"));
        if (msg.isEmpty()) return Incoming.other(updateId);
        Map<String, Object> from = Json.obj(msg.get("from"));
        Map<String, Object> chat = Json.obj(msg.get("chat"));
        long date = Json.lng(msg, "date", System.currentTimeMillis() / 1000);
        return new Incoming(updateId, true,
                Json.lng(from, "id", 0), Json.bool(from, "is_bot", false),
                Json.str(from, "first_name"), Json.str(from, "last_name"), Json.str(from, "username"),
                Json.lng(chat, "id", 0), Json.str(chat, "type", ""), Json.lng(msg, "message_id", 0),
                Json.str(msg, "text"), Json.str(msg, "media_group_id"), media(msg, date));
    }

    private static Incoming.Media media(Map<String, Object> msg, long date) {
        if (msg.get("document") instanceof Map) {
            Map<String, Object> d = Json.obj(msg.get("document"));
            return MediaNaming.build(new MediaNaming.Raw("document", Json.str(d, "file_id"), Json.str(d, "file_name"),
                    Json.str(d, "mime_type"), Json.lng(d, "file_size", 0), null, null), date);
        }
        if (msg.get("photo") instanceof List) {
            Map<String, Object> best = null;
            for (Object o : Json.arr(msg.get("photo"))) {
                Map<String, Object> p = Json.obj(o);
                if (best == null || Json.lng(p, "file_size", 0) >= Json.lng(best, "file_size", 0)) best = p;
            }
            if (best != null) {
                return MediaNaming.build(new MediaNaming.Raw("photo", Json.str(best, "file_id"), null, null,
                        Json.lng(best, "file_size", 0), null, null), date);
            }
        }
        if (msg.get("video") instanceof Map) {
            Map<String, Object> v = Json.obj(msg.get("video"));
            return MediaNaming.build(new MediaNaming.Raw("video", Json.str(v, "file_id"), Json.str(v, "file_name"),
                    Json.str(v, "mime_type"), Json.lng(v, "file_size", 0), null, null), date);
        }
        if (msg.get("audio") instanceof Map) {
            Map<String, Object> a = Json.obj(msg.get("audio"));
            return MediaNaming.build(new MediaNaming.Raw("audio", Json.str(a, "file_id"), Json.str(a, "file_name"),
                    Json.str(a, "mime_type"), Json.lng(a, "file_size", 0), Json.str(a, "title"), Json.str(a, "performer")), date);
        }
        if (msg.get("voice") instanceof Map) {
            Map<String, Object> v = Json.obj(msg.get("voice"));
            return MediaNaming.build(new MediaNaming.Raw("voice", Json.str(v, "file_id"), null, null,
                    Json.lng(v, "file_size", 0), null, null), date);
        }
        if (msg.get("video_note") instanceof Map) {
            Map<String, Object> v = Json.obj(msg.get("video_note"));
            return MediaNaming.build(new MediaNaming.Raw("video_note", Json.str(v, "file_id"), null, null,
                    Json.lng(v, "file_size", 0), null, null), date);
        }
        if (msg.get("animation") instanceof Map) {
            Map<String, Object> v = Json.obj(msg.get("animation"));
            return MediaNaming.build(new MediaNaming.Raw("animation", Json.str(v, "file_id"), Json.str(v, "file_name"),
                    null, Json.lng(v, "file_size", 0), null, null), date);
        }
        return null;
    }
}
