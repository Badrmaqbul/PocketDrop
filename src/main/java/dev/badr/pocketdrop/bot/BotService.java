package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.core.AppInfo;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.ConfigStore;
import dev.badr.pocketdrop.core.Fmt;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.Json;
import dev.badr.pocketdrop.core.Log;
import dev.badr.pocketdrop.storage.FileNamer;
import dev.badr.pocketdrop.storage.FileStorage;
import dev.badr.pocketdrop.storage.History;
import dev.badr.pocketdrop.storage.Kind;
import dev.badr.pocketdrop.storage.SavedItem;
import dev.badr.pocketdrop.storage.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.badr.pocketdrop.core.I18n.t;

/** Receives Telegram messages and saves what the linked owner sends. Everybody else is ignored silently. */
public final class BotService {
    public enum State { STOPPED, STARTING, RUNNING, RECONNECTING, ERROR }

    private enum Result { SAVED, BLOCKED, TOO_BIG, LOW_DISK, PAUSED, FOLDER, FAILED }

    private static final class Group {
        int saved, failed;
        boolean allPhotos = true;
        ScheduledFuture<?> timer;
    }

    private final ConfigStore store;
    private final History history;
    private final FileStorage storage = new FileStorage();
    private final Pairing pairing = new Pairing();
    private final List<BotListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<Long, Long> lastIntrusionLog = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread th = new Thread(r, "pocketdrop-timer");
        th.setDaemon(true);
        return th;
    });
    private final Object lifecycle = new Object();

    private volatile State state = State.STOPPED;
    private volatile String detail = "";
    private volatile boolean running;
    private volatile Gateway api;
    private volatile String botUsername = "";
    private Thread thread;

    public BotService(ConfigStore store, History history) {
        this.store = store;
        this.history = history;
        this.botUsername = store.get().botUsername;
        pairing.addListener(() -> listeners.forEach(l -> l.onPairingChanged()));
    }

    public void addListener(BotListener l) { listeners.add(l); }
    public void removeListener(BotListener l) { listeners.remove(l); }
    public Pairing pairing() { return pairing; }
    public State state() { return state; }
    public String detail() { return detail; }
    public String botUsername() { return botUsername; }
    public boolean isRunning() { return running; }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        synchronized (lifecycle) {
            if (running) return;
            String token = store.token();
            if (token == null) {
                setState(State.ERROR, "no-token");
                return;
            }
            try {
                api = Gateways.create(token);
            } catch (Gateway.GatewayException e) {
                Log.security("PocketDrop did not start the bot because the Telegram library cannot be used safely: " + e.getMessage());
                setState(State.ERROR, "unsafe-client");
                return;
            }
            running = true;
            setState(State.STARTING, "");
            thread = new Thread(this::loop, "pocketdrop-bot");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        synchronized (lifecycle) {
            running = false;
            Thread t = thread;
            thread = null;
            if (t != null) {
                t.interrupt();
                try { t.join(6000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            Gateway g = api;
            if (g != null) g.close();
            setState(State.STOPPED, "");
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void setState(State s, String d) {
        state = s;
        detail = d;
        for (BotListener l : listeners) {
            try { l.onState(s, d); } catch (RuntimeException ignored) { }
        }
    }

    private void loop() {
        boolean ready = false;
        int backoff = 1;
        while (running) {
            try {
                if (!ready) {
                    botUsername = api.getMe();
                    Config c = store.get();
                    if (!botUsername.equals(c.botUsername)) {
                        c.botUsername = botUsername;
                        store.save();
                    }
                    if (c.hasOwner()) publishCommands();
                    ready = true;
                    Log.info("Bot started as @" + botUsername);
                }
                List<Incoming> updates = api.poll(Integer.getInteger("pocketdrop.pollTimeout", 30));
                if (state != State.RUNNING) setState(State.RUNNING, "");
                backoff = 1;
                for (Incoming m : updates) {
                    try {
                        handle(m);
                    } catch (RuntimeException e) {
                        Log.error("Could not handle a message", e);
                    }
                }
            } catch (Gateway.GatewayException e) {
                if (e.code() == 401) {
                    Log.error("Telegram rejected the bot token (401)");
                    running = false;
                    setState(State.ERROR, "auth");
                    return;
                }
                if (e.code() == 409) {
                    Log.warn("Another copy of this bot is polling Telegram (409)");
                    setState(State.RECONNECTING, "conflict");
                    if (!sleep(5000)) return;
                } else if (e.code() == 429) {
                    Log.warn("Telegram asked us to slow down");
                    if (!sleep(Math.max(1, e.retryAfter()) * 1000L)) return;
                } else {
                    if (e.isNetwork()) Log.warn("Network problem: " + e.getMessage());
                    else Log.warn("Telegram error " + e.code() + ": " + e.getMessage());
                    setState(State.RECONNECTING, e.isNetwork() ? "network" : "telegram");
                    if (!sleep(backoff * 1000L)) return;
                    backoff = Math.min(backoff * 2, 30);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return running;
        } catch (InterruptedException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- pairing (called by the app)

    public String beginPairing() { return pairing.begin(); }

    public void cancelPairing() { pairing.cancel(); }

    public void confirmPairing() {
        Pairing.Candidate c = pairing.confirm();
        if (c == null) return;
        Config cfg = store.get();
        cfg.ownerId = c.userId();
        cfg.ownerName = c.displayName();
        cfg.ownerUsername = c.username() == null ? "" : c.username();
        store.save();
        Log.security("Telegram account linked (id " + c.userId() + ")");
        reply(c.chatId(), t("bot.paired"));
        publishCommands();
        listeners.forEach(l -> l.onOwnerChanged());
    }

    public void rejectPairing() {
        Log.security("A link request was rejected in the app");
        pairing.reject();
    }

    /** Removes the linked account immediately. The bot then ignores everybody until a new link is made. */
    public void clearOwner() {
        Config cfg = store.get();
        cfg.ownerId = 0;
        cfg.ownerName = "";
        cfg.ownerUsername = "";
        store.save();
        Log.security("Linked Telegram account removed");
        listeners.forEach(l -> l.onOwnerChanged());
    }

    public void setPaused(boolean paused) {
        Config cfg = store.get();
        cfg.paused = paused;
        store.save();
        listeners.forEach(l -> l.onPausedChanged(paused));
    }

    // ---------------------------------------------------------------- incoming updates

    private void handle(Incoming m) {
        if (!m.hasMessage()) return; // edits, buttons, channel posts ... are never processed
        if (m.fromId() == 0 || m.chatId() == 0 || m.fromIsBot()) return;

        Config c = store.get();
        if (!c.hasOwner()) {
            handleUnlinked(m);
            return;
        }
        if (m.fromId() != c.ownerId || m.chatId() != c.ownerId || !m.isPrivate()) {
            intrusion(m.fromId());
            return;
        }
        handleOwner(m, c);
    }

    private void handleUnlinked(Incoming m) {
        Pairing.Candidate cand = new Pairing.Candidate(m.fromId(), m.chatId(), m.messageId(),
                m.firstName(), m.lastName(), m.username());
        if (m.isPrivate() && pairing.offer(m.text(), cand, Instant.now())) {
            Log.security("A link code was received from account " + m.fromId() + " (waiting for confirmation)");
            reply(m.chatId(), t("bot.pair.received"));
            deleteQuietly(m.chatId(), cand.messageId());
            return;
        }
        intrusion(m.fromId());
    }

    private void intrusion(long senderId) {
        long now = System.currentTimeMillis();
        Long last = lastIntrusionLog.get(senderId);
        if (last == null || now - last > 60_000) {
            lastIntrusionLog.put(senderId, now);
            if (lastIntrusionLog.size() > 500) lastIntrusionLog.clear();
            Log.security("Ignored a message from an unlinked sender (id " + senderId + ")");
        }
        listeners.forEach(l -> l.onIntrusion(senderId));
    }

    private void handleOwner(Incoming m, Config c) {
        if (m.media() != null) {
            handleMedia(m);
            return;
        }
        String text = m.text();
        if (text == null) {
            reply(m.chatId(), t("bot.unsupported"));
        } else if (text.startsWith("/")) {
            command(m.chatId(), text, c);
        } else {
            reply(m.chatId(), t("bot.hint"));
        }
    }

    // ---------------------------------------------------------------- media

    private void handleMedia(Incoming m) {
        Incoming.Media media = m.media();
        long chatId = m.chatId();
        Config c = store.get();
        String groupId = m.mediaGroupId();
        String safe = FileNamer.sanitize(media.name(), "file");
        Result result;
        String extra = "";

        if (c.paused) {
            result = Result.PAUSED;
        } else if (!c.allowDangerous && FileNamer.isDangerous(safe, c.blockedExt)) {
            result = Result.BLOCKED;
            extra = FileNamer.extension(safe);
            Log.security("Blocked a file of a dangerous type (." + extra + ")");
        } else {
            result = download(c, media, safe);
        }

        if (groupId != null && (result == Result.SAVED || result == Result.FAILED || result == Result.BLOCKED)) {
            aggregate(chatId + ":" + groupId, chatId, media.kind() == Kind.PHOTO, result == Result.SAVED);
            return;
        }
        switch (result) {
            case SAVED -> replySaved(chatId, lastSaved);
            case BLOCKED -> reply(chatId, t("bot.blocked", extra));
            case TOO_BIG -> reply(chatId, t("bot.toobig", c.maxFileMb));
            case LOW_DISK -> reply(chatId, t("bot.lowdisk"));
            case PAUSED -> reply(chatId, t("bot.paused"));
            case FOLDER -> reply(chatId, t("bot.folder"));
            default -> reply(chatId, t("bot.failed"));
        }
    }

    private SavedItem lastSaved;

    private Result download(Config c, Incoming.Media media, String safeName) {
        Gateway g = api;
        long maxBytes = c.maxFileMb * 1024L * 1024L;
        if (media.size() > maxBytes) return Result.TOO_BIG;
        try {
            Path dir = storage.targetDir(c, media.kind(), LocalDate.now());
            long free = storage.usableSpace(dir);
            if (free - media.size() < c.minFreeMb * 1024L * 1024L) {
                Log.warn("Not saving: free space is below the limit");
                return Result.LOW_DISK;
            }
            Gateway.FileRef ref = g.getFile(media.fileId());
            long size = ref.size() > 0 ? ref.size() : media.size();
            if (size > maxBytes) return Result.TOO_BIG;

            FileStorage.Saved saved;
            try (InputStream in = g.download(ref)) {
                saved = storage.save(c, media.kind(), safeName, in, LocalDate.now());
            }
            SavedItem item = new SavedItem(System.currentTimeMillis(), saved.path().getFileName().toString(),
                    media.kind(), saved.size(), saved.path().toString());
            history.add(item);
            lastSaved = item;
            Log.info("Saved " + media.kind().id() + " (" + Fmt.size(saved.size()) + ")");
            listeners.forEach(l -> l.onSaved(item));
            return Result.SAVED;
        } catch (StorageException e) {
            Log.warn("Not saved: " + e.getMessage());
            return switch (e.reason()) {
                case TOO_BIG -> Result.TOO_BIG;
                case LOW_DISK -> Result.LOW_DISK;
                case FOLDER -> Result.FOLDER;
                default -> Result.FAILED;
            };
        } catch (Gateway.GatewayException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("too big")) return Result.TOO_BIG;
            Log.error("Telegram could not provide the file: " + e.getMessage());
            return Result.FAILED;
        } catch (IOException e) {
            Log.error("Could not save a file", e);
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    /** Called for single files after a successful save. */
    private void replySaved(long chatId, SavedItem item) {
        reply(chatId, t("bot.saved", item.name()));
    }

    private void aggregate(String key, long chatId, boolean photo, boolean ok) {
        long delay = Long.getLong("pocketdrop.albumDelayMs", 2000);
        synchronized (groups) {
            Group g = groups.computeIfAbsent(key, k -> new Group());
            if (ok) g.saved++; else g.failed++;
            if (!photo) g.allPhotos = false;
            if (g.timer != null) g.timer.cancel(false);
            g.timer = scheduler.schedule(() -> flush(key, chatId), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void flush(String key, long chatId) {
        Group g;
        synchronized (groups) { g = groups.remove(key); }
        if (g == null) return;
        if (g.failed == 0) reply(chatId, g.allPhotos ? t("bot.album.photos", g.saved) : t("bot.album.files", g.saved));
        else reply(chatId, t("bot.album.partial", g.saved, g.failed));
    }

    // ---------------------------------------------------------------- commands

    private static final String[] COMMANDS = {"start", "help", "status", "last", "stats", "storage",
            "pause", "resume", "undo", "ping", "about"};

    private void publishCommands() {
        List<String[]> list = new ArrayList<>();
        for (String c : COMMANDS) list.add(new String[]{c, t("cmd." + c)});
        try {
            api.setCommands(list);
        } catch (Gateway.GatewayException e) {
            Log.warn("Could not publish the command list: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void command(long chatId, String text, Config c) {
        String word = text.split("\\s+", 2)[0].substring(1);
        int at = word.indexOf('@');
        if (at >= 0) word = word.substring(0, at);
        switch (word.toLowerCase(Locale.ROOT)) {
            case "start" -> reply(chatId, t("bot.start"));
            case "help" -> reply(chatId, t("bot.help"));
            case "status" -> {
                String folder = c.saveDir.isBlank() ? "-" : Path.of(c.saveDir).getFileName() == null
                        ? c.saveDir : Path.of(c.saveDir).getFileName().toString();
                reply(chatId, t("bot.status", c.paused ? t("bot.status.paused") : t("bot.status.active"),
                        history.countToday(), folder));
            }
            case "last" -> {
                List<SavedItem> last = history.last(10);
                if (last.isEmpty()) { reply(chatId, t("bot.last.empty")); break; }
                StringBuilder sb = new StringBuilder(t("bot.last.title"));
                int i = 1;
                for (SavedItem it : last) {
                    sb.append('\n').append(i++).append(". ").append(it.name()).append("  (")
                            .append(Fmt.size(it.size())).append(", ").append(Fmt.when(it.time())).append(')');
                }
                reply(chatId, sb.toString());
            }
            case "stats" -> {
                StringBuilder sb = new StringBuilder(t("bot.stats.title", history.count(), Fmt.size(history.totalBytes())));
                for (Map.Entry<Kind, long[]> e : history.byKind().entrySet()) {
                    if (e.getValue()[0] == 0) continue;
                    sb.append('\n').append(t("kind." + e.getKey().id())).append(": ").append(e.getValue()[0])
                            .append("  (").append(Fmt.size(e.getValue()[1])).append(')');
                }
                reply(chatId, sb.toString());
            }
            case "storage" -> {
                String free = "?";
                try {
                    free = Fmt.size(storage.usableSpace(storage.baseDir(c)));
                } catch (IOException | StorageException ignored) { }
                reply(chatId, t("bot.storage", free, Fmt.size(history.totalBytes()), history.count()));
            }
            case "pause" -> { setPaused(true); reply(chatId, t("bot.pause.on")); }
            case "resume" -> { setPaused(false); reply(chatId, t("bot.pause.off")); }
            case "undo" -> undo(chatId, c);
            case "ping" -> reply(chatId, t("bot.pong"));
            case "about" -> reply(chatId, t("bot.about", AppInfo.NAME, AppInfo.VERSION));
            default -> reply(chatId, t("bot.unknown"));
        }
    }

    private void undo(long chatId, Config c) {
        if (!c.undoEnabled) { reply(chatId, t("bot.undo.disabled")); return; }
        SavedItem last = history.lastOrNull();
        if (last == null) { reply(chatId, t("bot.undo.none")); return; }
        if (System.currentTimeMillis() - last.time() > 60 * 60 * 1000L) { reply(chatId, t("bot.undo.old")); return; }
        try {
            Path base = storage.baseDir(c);
            Path p = Path.of(last.path()).toAbsolutePath().normalize();
            if (!p.startsWith(base)) { reply(chatId, t("bot.failed")); return; }
            Files.deleteIfExists(p);
            history.remove(last);
            Log.info("Undo: removed the last saved file");
            reply(chatId, t("bot.undo.done", last.name()));
        } catch (IOException | StorageException e) {
            Log.warn("Undo failed: " + e.getMessage());
            reply(chatId, t("bot.failed"));
        }
    }

    // ---------------------------------------------------------------- sending

    private void reply(long chatId, String text) {
        Gateway a = api;
        if (a == null) return;
        try {
            a.sendMessage(chatId, text);
        } catch (Gateway.GatewayException e) {
            Log.warn("Could not send a message: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteQuietly(long chatId, long messageId) {
        Gateway a = api;
        if (a == null || messageId == 0) return;
        try {
            a.deleteMessage(chatId, messageId);
        } catch (Gateway.GatewayException e) {
            Log.debug("Could not delete the code message: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Used by the app to confirm that a token works before it is saved. Returns the bot's @username. */
    public static String checkToken(String token) throws Gateway.GatewayException, InterruptedException {
        Gateway g = Gateways.create(token);
        try {
            return g.getMe();
        } finally {
            g.close();
        }
    }

    /** Link that opens the bot and sends the code automatically (Telegram "deep link"). */
    public String deepLink(String code) {
        return "https://t.me/" + botUsername + "?start=" + code;
    }

    public static String describe(State s) { return s.name().toLowerCase(Locale.ROOT); }

    // used by tests
    SavedItem lastSavedForTests() { return lastSaved; }
}
