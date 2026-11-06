package dev.badr.pocketdrop;

import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.bot.BotListener;
import dev.badr.pocketdrop.bot.Pairing;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.ConfigStore;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.Json;
import dev.badr.pocketdrop.core.Log;
import dev.badr.pocketdrop.core.SecretProtector;
import dev.badr.pocketdrop.storage.FileNamer;
import dev.badr.pocketdrop.storage.History;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static dev.badr.pocketdrop.Check.eq;
import static dev.badr.pocketdrop.Check.ok;
import static dev.badr.pocketdrop.Check.section;
import static dev.badr.pocketdrop.Check.waitUntil;

/** Runs every test and exits with code 1 if anything fails. */
public final class AllTests {
    public static void main(String[] args) throws Exception {
        I18n.setLanguage("en");
        fileNamer();
        json();
        pairing();
        apiAddress();
        config();
        i18nKeys();
        bot();
        System.out.println("\n" + Check.passed + " passed, " + Check.failed + " failed");
        System.exit(Check.failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ units

    static void fileNamer() {
        section("File names");
        eq("backslash traversal", "evil.txt", FileNamer.sanitize("..\\..\\evil.txt", "file"));
        eq("slash traversal", "x.txt", FileNamer.sanitize("../../x.txt", "file"));
        eq("absolute path", "boot.ini", FileNamer.sanitize("C:\\Windows\\boot.ini", "file"));
        String rtlo = FileNamer.sanitize("photo\u202Egpj.exe", "file");
        eq("hidden direction character removed", "photogpj.exe", rtlo);
        ok("disguised exe is detected", FileNamer.isDangerous(rtlo, Config.DEFAULT_BLOCKED));
        eq("reserved device name", "_CON.txt", FileNamer.sanitize("CON.txt", "file"));
        eq("reserved name without extension", "_nul", FileNamer.sanitize("nul", "file"));
        eq("alternate data stream colon", "a.txt_evil", FileNamer.sanitize("a.txt:evil", "file"));
        eq("trailing dot and space", "report.pdf", FileNamer.sanitize("report.pdf. ", "file"));
        eq("empty name", "file", FileNamer.sanitize("", "file"));
        eq("blank name", "file", FileNamer.sanitize("   ", "file"));
        eq("null name uses fallback", "x", FileNamer.sanitize(null, "x"));
        eq("arabic name kept", "تقرير.pdf", FileNamer.sanitize("تقرير.pdf", "file"));
        String longName = FileNamer.sanitize("a".repeat(300) + ".pdf", "file");
        ok("long name shortened and keeps extension", longName.length() <= 104 && longName.endsWith(".pdf"));
        ok("exe is dangerous", FileNamer.isDangerous("setup.EXE", Config.DEFAULT_BLOCKED));
        ok("js is dangerous", FileNamer.isDangerous("x.js", Config.DEFAULT_BLOCKED));
        ok("txt is fine", !FileNamer.isDangerous("notes.txt", Config.DEFAULT_BLOCKED));
        ok("tar.gz is fine", !FileNamer.isDangerous("archive.tar.gz", Config.DEFAULT_BLOCKED));
        ok("no extension is fine", !FileNamer.isDangerous("README", Config.DEFAULT_BLOCKED));
        eq("underscore suffix", "a_2.txt", FileNamer.withSuffix("a.txt", 2, "underscore"));
        eq("parens suffix", "a (1).txt", FileNamer.withSuffix("a.txt", 2, "parens"));
        eq("suffix without extension", "a_3", FileNamer.withSuffix("a", 3, "underscore"));
    }

    static void json() {
        section("JSON");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "a \"quote\" \\ and\nnewline and تقرير");
        m.put("n", 5000000001L);
        m.put("b", true);
        m.put("z", null);
        m.put("list", List.of(1, 2, Map.of("k", "v")));
        Object back = Json.parse(Json.pretty(m));
        eq("round trip", Json.stringify(m), Json.stringify(back));
        eq("big number", 5000000001L, Json.lng(Json.obj(back), "n", 0));
        eq("unicode escape", "é", Json.parse("\"\\u00e9\""));
        boolean threw = false;
        try { Json.parse("{\"a\":}"); } catch (IllegalArgumentException e) { threw = true; }
        ok("bad json is rejected", threw);
        threw = false;
        try { Json.parse("{} x"); } catch (IllegalArgumentException e) { threw = true; }
        ok("trailing data is rejected", threw);
        threw = false;
        try { Json.parse("[".repeat(100000)); } catch (IllegalArgumentException e) { threw = true; } catch (StackOverflowError e) { }
        ok("deep nesting is refused without crashing", threw);
        ok("normal nesting still works", Json.parse("[[[[{\"a\":[1]}]]]]") instanceof List);
    }

    static void pairing() {
        section("Pairing");
        Pairing p = new Pairing();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        String code = p.begin(t0);
        eq("code length", 26, code.length());
        ok("code characters", code.matches("[A-Z2-7]{26}"));
        ok("display code grouped", p.displayCode().matches("([A-Z2-7]{4}-){6}[A-Z2-7]{2}"));
        Pairing.Candidate a = new Pairing.Candidate(1, 1, 10, "A", "B", "ab");
        Pairing.Candidate b = new Pairing.Candidate(2, 2, 11, "C", null, null);
        ok("wrong code ignored", !p.offer("NOPE", a, t0.plusSeconds(1)));
        eq("still waiting", Pairing.State.WAITING, p.state());
        ok("first correct code accepted (lower case, dashes)", p.offer(p.displayCode().toLowerCase(), a, t0.plusSeconds(2)));
        eq("now confirming", Pairing.State.CONFIRMING, p.state());
        ok("second sender with the same code is ignored", !p.offer(code, b, t0.plusSeconds(3)));
        eq("candidate is the first sender", 1L, p.candidate().userId());
        Pairing.Candidate confirmed = p.confirm();
        eq("confirm returns the account", 1L, confirmed.userId());
        eq("inactive after confirm", Pairing.State.INACTIVE, p.state());
        ok("code is useless afterwards", !p.offer(code, b, t0.plusSeconds(4)));

        String c2 = p.begin(t0);
        ok("deep link format accepted", p.offer("/start " + c2, a, t0.plusSeconds(5)));
        p.reject();
        eq("reject starts a new round", Pairing.State.WAITING, p.state());
        ok("new code differs", !p.code().equals(c2));
        ok("old code no longer works", !p.offer(c2, a, Instant.now()));

        Pairing q = new Pairing();
        String c3 = q.begin(t0);
        ok("expired code rejected", !q.offer(c3, a, t0.plusSeconds(301)));
        q.tick(t0.plusSeconds(301));
        eq("tick expires the code", Pairing.State.EXPIRED, q.state());

        Pairing r = new Pairing();
        String c4 = r.begin(t0);
        r.offer(c4, a, t0.plusSeconds(1));
        r.tick(t0.plusSeconds(200));
        eq("unanswered confirmation falls back to a new code", Pairing.State.WAITING, r.state());
    }

    static void apiAddress() {
        section("API address");
        String[] good = {"https://api.telegram.org", "https://api.telegram.org/", "http://127.0.0.1:8080", "http://localhost:9", "https://localhost:8443"};
        String[] bad = {"http://api.telegram.org", "https://evil.example", "http://evil.example", "https://api.telegram.org.evil.com",
                "https://api.telegram.org@evil.com", "https://evil.com/https://api.telegram.org", "http://localhost.evil.com",
                "https://api.telegram.org:8443", "ftp://127.0.0.1", "not a url", "https://api.telegram.org/?x=1", "//api.telegram.org"};
        for (String g : good) {
            boolean accepted;
            try { dev.badr.pocketdrop.bot.Gateways.checkBase(g); accepted = true; } catch (IllegalArgumentException e) { accepted = false; }
            ok("accepted: " + g, accepted);
        }
        for (String b : bad) {
            boolean refused;
            try { dev.badr.pocketdrop.bot.Gateways.checkBase(b); refused = false; } catch (IllegalArgumentException e) { refused = true; }
            ok("refused: " + b, refused);
        }
    }

    static void config() {
        section("Settings");
        Config c = new Config();
        c.setBlockedExtText(" .EXE, bat ; ps1 exe ");
        eq("blocked list parsing", List.of("exe", "bat", "ps1"), c.blockedExt);
        c.maxFileMb = 20;
        Config back = Config.fromJson(Json.obj(Json.parse(Json.stringify(c.toJson()))));
        eq("round trip", Json.stringify(c.toJson()), Json.stringify(back.toJson()));
        Map<String, Object> wild = new LinkedHashMap<>();
        wild.put("maxFileMb", 5000);
        wild.put("layout", "../../etc");
        Config w = Config.fromJson(wild);
        eq("max size is clamped to the Telegram limit", 20, w.maxFileMb);
        eq("unknown layout falls back", "flat", w.layout);
    }

    static void i18nKeys() throws IOException {
        section("Texts");
        var ar = I18n.bundle("ar");
        var en = I18n.bundle("en");
        ok("both languages have the same keys", ar.keySet().equals(en.keySet()));
        if (!ar.keySet().equals(en.keySet())) {
            for (Object k : en.keySet()) if (!ar.containsKey(k)) System.out.println("        missing in ar: " + k);
            for (Object k : ar.keySet()) if (!en.containsKey(k)) System.out.println("        missing in en: " + k);
        }
        // every t("...") used in the source must exist
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\\bt\\(\"([a-zA-Z0-9_.]+)\"\\s*[,)]");
        java.util.Set<String> missing = new java.util.TreeSet<>();
        Path src = Path.of("src/main/java");
        if (Files.isDirectory(src)) {
            try (Stream<Path> s = Files.walk(src)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    var m = pat.matcher(Files.readString(f, StandardCharsets.UTF_8));
                    while (m.find()) if (!en.containsKey(m.group(1))) missing.add(m.group(1));
                }
            }
        }
        ok("every text key used in code exists " + (missing.isEmpty() ? "" : missing), missing.isEmpty());
    }

    // ------------------------------------------------------------------ whole bot against the fake server

    static final SecretProtector FAKE_PROTECTOR = new SecretProtector() {
        public String protect(String p) { return Base64.getEncoder().encodeToString(("x" + p).getBytes(StandardCharsets.UTF_8)); }
        public String unprotect(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8).substring(1); }
    };

    static void bot() throws Exception {
        section("Bot against a fake Telegram");
        Path tmp = Files.createTempDirectory("pocketdrop-test");
        System.setProperty("pocketdrop.home", tmp.resolve("app").toString());
        System.setProperty("pocketdrop.pollTimeout", "1");
        System.setProperty("pocketdrop.albumDelayMs", "300");
        // normally the bot talks to a pretend Telegram on this computer through a test-only address.
        // In "real addresses" mode (see ProductionPathCheck) api.telegram.org itself points to this computer.
        String realMode = System.getProperty("pocketdrop.test.realAddresses");
        FakeTelegram fake;
        if (realMode != null) {
            fake = FakeTelegram.https(Path.of(realMode), "changeit", 443);
            System.clearProperty("pocketdrop.apiBase");
            System.out.println("  (using the real address https://api.telegram.org)");
        } else {
            fake = new FakeTelegram();
            System.setProperty("pocketdrop.apiBase", fake.baseUrl());
        }
        Log.init(tmp.resolve("logs"), 90);

        Path saveDir = tmp.resolve("saved");
        ConfigStore store = new ConfigStore(tmp.resolve("config.json"), FAKE_PROTECTOR);
        Config cfg = store.get();
        cfg.saveDir = saveDir.toString();
        cfg.minFreeMb = 0;
        cfg.setupDone = true;
        store.setToken(FakeTelegram.TOKEN);
        ok("token is not stored as plain text", !Files.readString(tmp.resolve("config.json")).contains("AAFake"));

        History history = new History(tmp.resolve("history.jsonl"));
        BotService bot = new BotService(store, history);
        AtomicInteger intrusions = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        bot.addListener(new BotListener() {
            @Override public void onIntrusion(long id) { intrusions.incrementAndGet(); }
            @Override public void onSaved(dev.badr.pocketdrop.storage.SavedItem item) { saved.incrementAndGet(); }
        });
        bot.start();
        ok("bot reaches the running state", waitUntil(() -> bot.state() == BotService.State.RUNNING, 5000));
        eq("bot name read from Telegram", "PocketDropTestBot", bot.botUsername());

        final long OWNER = 999, STRANGER = 111, OTHER = 333;
        fake.files.put("f1", "hello".getBytes());
        fake.files.put("p1", new byte[10]);
        fake.files.put("p2", "BIGGEST".getBytes());
        fake.files.put("p3", new byte[30]);
        fake.files.put("v1", "voice-bytes".getBytes());
        fake.files.put("big", new byte[100]);

        // --- before linking: everybody is ignored
        fake.text(STRANGER, "hello?");
        fake.doc(STRANGER, "x.pdf", "f1", 5);
        ok("stranger is ignored before linking", waitUntil(() -> intrusions.get() >= 2, 3000));
        eq("no reply to strangers", 0, fake.sentCount());

        // --- linking
        String code = bot.beginPairing();
        fake.text(STRANGER, "WRONGCODE");
        long codeMsg = fake.text(OWNER, "/start " + code);
        ok("correct code moves to confirmation", waitUntil(() -> bot.pairing().state() == Pairing.State.CONFIRMING, 3000));
        ok("sender told to confirm in the app", waitUntil(() -> fake.sentContains("Code received"), 3000));
        ok("code message deleted from the chat", waitUntil(() -> fake.deleted.contains(codeMsg), 3000));
        fake.text(OTHER, code);
        Thread.sleep(300);
        eq("second sender cannot take over", 999L, bot.pairing().candidate().userId());
        eq("nobody is linked before confirmation", 0L, store.get().ownerId);
        fake.doc(OWNER, "early.pdf", "f1", 5);
        Thread.sleep(300);
        ok("owner cannot save before confirmation", !Files.exists(saveDir.resolve("early.pdf")));
        bot.confirmPairing();
        eq("owner linked", 999L, store.get().ownerId);
        ok("welcome message sent", waitUntil(() -> fake.sentContains("Linked successfully"), 3000));
        ok("command list published", waitUntil(() -> fake.commandsPublished > 0, 3000));
        fake.text(OTHER, code);
        Thread.sleep(300);
        eq("code does nothing after linking", 999L, store.get().ownerId);

        // --- saving files
        fake.doc(OWNER, "report.pdf", "f1", 5);
        ok("document saved", waitUntil(() -> Files.exists(saveDir.resolve("report.pdf")), 4000));
        eq("content is correct", "hello", Files.readString(saveDir.resolve("report.pdf")));
        ok("saved reply", waitUntil(() -> fake.sentContains("Saved: report.pdf"), 3000));
        fake.doc(OWNER, "report.pdf", "f1", 5);
        ok("duplicate gets _2, original untouched", waitUntil(() -> Files.exists(saveDir.resolve("report_2.pdf")), 4000));
        fake.doc(OWNER, "report.pdf", "f1", 5);
        ok("third copy gets _3", waitUntil(() -> Files.exists(saveDir.resolve("report_3.pdf")), 4000));

        fake.doc(OWNER, "..\\..\\evil.txt", "f1", 5);
        ok("path traversal stays inside the folder", waitUntil(() -> Files.exists(saveDir.resolve("evil.txt")), 4000));
        ok("nothing written outside the folder", !Files.exists(tmp.resolve("evil.txt")) && !Files.exists(tmp.getParent().resolve("evil.txt")));

        fake.doc(OWNER, "setup.exe", "f1", 5);
        ok("dangerous type is explained", waitUntil(() -> fake.sentContains("Blocked for safety"), 3000));
        ok("dangerous file not saved", !Files.exists(saveDir.resolve("setup.exe")));
        cfg.allowDangerous = true;
        fake.doc(OWNER, "setup.exe", "f1", 5);
        ok("dangerous file saved after the owner allows it", waitUntil(() -> Files.exists(saveDir.resolve("setup.exe")), 4000));
        cfg.allowDangerous = false;

        List<Object> sizes = List.of(FakeTelegram.photoSize("p1", 10), FakeTelegram.photoSize("p2", 7), FakeTelegram.photoSize("p3", 30));
        fake.enqueue(OWNER, "private", Map.of("photo", sizes));
        Path photos = saveDir.resolve("photos");
        ok("photo saved in the photos folder", waitUntil(() -> {
            try (Stream<Path> s = Files.isDirectory(photos) ? Files.list(photos) : Stream.empty()) {
                return s.anyMatch(p -> p.getFileName().toString().matches("photo_\\d{8}_\\d{6}\\.jpg"));
            } catch (IOException e) { return false; }
        }, 4000));

        ok("single photo reply arrived", waitUntil(() -> fake.sentContains("Saved: photo_"), 3000));
        int before = fake.sentCount();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("photo", List.of(FakeTelegram.photoSize("p3", 30)));
            x.put("media_group_id", "g1");
            fake.enqueue(OWNER, "private", x);
        }
        ok("album gets one summary", waitUntil(() -> fake.sentContains("All 3 photos were saved"), 4000));
        Thread.sleep(500);
        int singles = 0;
        synchronized (fake.sent) {
            for (int i = before; i < fake.sent.size(); i++) if (fake.sent.get(i).startsWith("✅ Saved")) singles++;
        }
        eq("no single replies for album items", 0, singles);

        fake.doc(OWNER, "huge.bin", "big", 30L * 1024 * 1024);
        ok("declared size above the limit is refused", waitUntil(() -> fake.sentContains("bigger than 20 MB"), 3000));
        ok("too big file not saved", !Files.exists(saveDir.resolve("huge.bin")));
        int c0 = fake.sentCount();
        fake.doc(OWNER, "huge2.bin", "huge", 1000);
        ok("Telegram refusing a big file is explained", waitUntil(() -> fake.sentCount() > c0 && fake.sentContains("bigger than 20 MB"), 3000));

        cfg.minFreeMb = 100_000_000;
        fake.doc(OWNER, "nospace.pdf", "f1", 5);
        ok("low disk space is explained", waitUntil(() -> fake.sentContains("free space"), 3000));
        ok("not saved when space is low", !Files.exists(saveDir.resolve("nospace.pdf")));
        cfg.minFreeMb = 0;

        fake.enqueue(OWNER, "private", Map.of("voice", Map.of("file_id", "v1", "file_size", 11)));
        ok("voice note saved as ogg", waitUntil(() -> {
            try (Stream<Path> s = Files.list(saveDir)) {
                return s.anyMatch(p -> p.getFileName().toString().matches("voice_\\d{8}_\\d{6}\\.ogg"));
            } catch (IOException e) { return false; }
        }, 4000));

        fake.enqueue(OWNER, "private", Map.of("sticker", Map.of("file_id", "s")));
        ok("unsupported content is explained", waitUntil(() -> fake.sentContains("I can only save"), 3000));

        // --- commands
        fake.text(OWNER, "/pause");
        ok("pause works", waitUntil(() -> store.get().paused, 3000));
        fake.doc(OWNER, "paused.pdf", "f1", 5);
        ok("saving paused message", waitUntil(() -> fake.sentContains("Saving is paused"), 3000));
        ok("not saved while paused", !Files.exists(saveDir.resolve("paused.pdf")));
        fake.text(OWNER, "/resume");
        ok("resume works", waitUntil(() -> !store.get().paused, 3000));
        fake.doc(OWNER, "after.pdf", "f1", 5);
        ok("saved after resume", waitUntil(() -> Files.exists(saveDir.resolve("after.pdf")), 4000));

        fake.text(OWNER, "/last");
        ok("/last lists files", waitUntil(() -> fake.sentContains("Last saved files:") && fake.sentContains("after.pdf  ("), 3000));
        fake.text(OWNER, "/stats");
        ok("/stats works", waitUntil(() -> fake.sentContains("Total:"), 3000));
        fake.text(OWNER, "/storage");
        ok("/storage works", waitUntil(() -> fake.sentContains("Free space:"), 3000));
        fake.text(OWNER, "/status@PocketDropTestBot");
        ok("/status with bot name works", waitUntil(() -> fake.sentContains("Saving: active"), 3000));
        fake.text(OWNER, "/ping");
        ok("/ping works", waitUntil(() -> fake.sentContains("pong"), 3000));
        fake.text(OWNER, "/about");
        ok("/about works", waitUntil(() -> fake.sentContains("PocketDrop 1.0.0"), 3000));
        fake.text(OWNER, "/nonsense");
        ok("unknown command handled", waitUntil(() -> fake.sentContains("Unknown command"), 3000));
        fake.text(OWNER, "just chatting");
        ok("plain text gets a hint", waitUntil(() -> fake.sentContains("Send me a file"), 3000));

        int count = history.count();
        fake.text(OWNER, "/undo");
        ok("/undo deletes the last saved file", waitUntil(() -> !Files.exists(saveDir.resolve("after.pdf")), 3000));
        ok("history updated by /undo", waitUntil(() -> history.count() == count - 1, 3000));
        cfg.undoEnabled = false;
        fake.text(OWNER, "/undo");
        ok("undo disabled message", waitUntil(() -> fake.sentContains("/undo is turned off"), 3000));
        cfg.undoEnabled = true;

        // --- after linking, other people and other update types
        int sentBefore = fake.sentCount();
        int filesBefore = fileCount(saveDir);
        fake.doc(STRANGER, "stranger.pdf", "f1", 5);
        fake.enqueue(OTHER, "private", Map.of("text", "/status"));
        fake.enqueue(OWNER, "group", Map.of("text", "/status"));
        Map<String, Object> edited = new LinkedHashMap<>();
        edited.put("edited_message", Map.of("message_id", 5, "text", "edit"));
        fake.enqueueRaw(edited);
        Thread.sleep(800);
        eq("strangers get no replies", sentBefore, fake.sentCount());
        eq("strangers save nothing", filesBefore, fileCount(saveDir));
        ok("bot is still running after odd updates", bot.state() == BotService.State.RUNNING);

        boolean security = false;
        for (Log.Entry e : Log.recent()) if (e.level() == Log.Level.SECURITY && e.message().contains("unlinked")) security = true;
        ok("intrusions are written to the log", security);
        boolean tokenLeak = false;
        for (Log.Entry e : Log.recent()) if (e.message().contains("AAFake")) tokenLeak = true;
        ok("token never appears in the log", !tokenLeak);
        ok("log masks tokens", !Log.mask("url /bot" + FakeTelegram.TOKEN + "/getMe").contains("AAFake"));

        // --- history survives restart
        eq("history is persisted", history.count(), new History(tmp.resolve("history.jsonl")).count());
        ok("saved listener fired", saved.get() >= 8);

        // --- unlinking
        bot.clearOwner();
        eq("owner removed", 0L, store.get().ownerId);
        int filesNow = fileCount(saveDir);
        fake.doc(OWNER, "former-owner.pdf", "f1", 5);
        Thread.sleep(600);
        eq("former owner can no longer save", filesNow, fileCount(saveDir));

        // --- stop and bad token
        bot.stop();
        eq("bot stops", BotService.State.STOPPED, bot.state());
        fake.rejectToken = true;
        BotService bad = new BotService(store, history);
        bad.start();
        ok("invalid token ends in the error state", waitUntil(() -> bad.state() == BotService.State.ERROR, 4000));
        bad.stop();
        fake.stop();
    }

    static int fileCount(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile).count();
        }
    }
}
