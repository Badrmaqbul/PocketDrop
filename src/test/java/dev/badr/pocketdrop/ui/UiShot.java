package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.FakeTelegram;
import dev.badr.pocketdrop.bot.Pairing;
import dev.badr.pocketdrop.core.AppDirs;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.SecretProtector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Starts the real app against a fake Telegram, walks through every screen and saves screenshots. */
public final class UiShot extends PocketDropApp {
    static final SecretProtector TEST_PROTECTOR = new SecretProtector() {
        public String protect(String p) { return Base64.getEncoder().encodeToString(("x" + p).getBytes(StandardCharsets.UTF_8)); }
        public String unprotect(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8).substring(1); }
    };

    static String lang;
    static Path out;
    static FakeTelegram fake;
    static Path tmp;

    public static void main(String[] args) throws Exception {
        lang = args.length > 0 ? args[0] : "en";
        tmp = Files.createTempDirectory("pdshot");
        out = Path.of("/tmp/shots", lang);
        Files.createDirectories(out);
        System.setProperty("pocketdrop.home", tmp.resolve("app").toString());
        System.setProperty("pocketdrop.pollTimeout", "1");
        System.setProperty("pocketdrop.albumDelayMs", "300");
        System.setProperty("java.awt.headless", "false");
        fake = new FakeTelegram();
        System.setProperty("pocketdrop.apiBase", fake.baseUrl());
        Locale.setDefault(Locale.forLanguageTag(lang));
        Application.launch(UiShot.class, args);
    }

    @Override
    AppContext createContext() {
        return new AppContext(TEST_PROTECTOR);
    }

    @Override
    public void start(javafx.stage.Stage primary) {
        super.start(primary);
        Thread t = new Thread(this::walk, "shots");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ helpers

    void ui(Runnable r) throws InterruptedException {
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { l.countDown(); } });
        l.await();
    }

    void snap(String name) throws Exception {
        Thread.sleep(700);
        BufferedImage[] holder = new BufferedImage[1];
        ui(() -> {
            WritableImage img = stage.getScene().snapshot(null);
            holder[0] = SwingFXUtils.fromFXImage(img, null);
        });
        ImageIO.write(holder[0], "png", out.resolve(name + ".png").toFile());
        System.out.println("shot " + name);
    }

    static byte[] png(int w, int h, int rgb) throws Exception {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int r = (rgb >> 16 & 255) * x / w, g = (rgb >> 8 & 255) * y / h, b = rgb & 255;
            im.setRGB(x, y, (r << 16) | (g << 8) | b);
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(im, "png", o);
        return o.toByteArray();
    }

    boolean waitFor(java.util.function.BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (c.getAsBoolean()) return true; Thread.sleep(40); }
        return c.getAsBoolean();
    }

    // ------------------------------------------------------------------ the walk

    void walk() {
        try {
            final long OWNER = 5001;
            // ---- wizard
            snap("w0-language");
            ui(() -> languageChosenInWizard(lang));
            snap("w1-intro");
            ui(() -> showWizard(Wizard.TOKEN));
            snap("w2-token");

            ctx.store.setToken(FakeTelegram.TOKEN);
            ctx.cfg().botUsername = "PocketDropBot";
            ctx.store.save();
            ctx.bot.start();
            waitFor(() -> ctx.bot.state() == dev.badr.pocketdrop.bot.BotService.State.RUNNING, 5000);

            ui(() -> showWizard(Wizard.LINK));
            snap("w3-link-waiting");
            String code = ctx.bot.pairing().code();
            fake.text(OWNER, "/start " + code);
            waitFor(() -> ctx.bot.pairing().state() == Pairing.State.CONFIRMING, 4000);
            snap("w3b-confirm-dialog");
            ui(() -> { modal.close(); });
            new Thread(ctx.bot::confirmPairing).start();
            waitFor(() -> ctx.cfg().hasOwner(), 4000);
            snap("w3c-linked");

            ui(() -> showWizard(Wizard.FOLDER));
            snap("w4-folder");

            // ---- the running app
            Config cfg = ctx.cfg();
            cfg.saveDir = tmp.resolve("PocketDrop").toString();
            cfg.minFreeMb = 0;
            cfg.setupDone = true;
            ctx.store.save();
            ui(() -> showShell(0));
            snap("s0-dashboard-empty");

            fake.files.put("pdf", "%PDF-1.4 demo".getBytes());
            fake.files.put("img1", png(640, 480, 0x7C5CFF));
            fake.files.put("img2", png(480, 640, 0x22C55E));
            fake.files.put("vid", new byte[3_400_000]);
            fake.files.put("snd", new byte[820_000]);
            fake.files.put("zip", new byte[1_900_000]);
            fake.doc(OWNER, "Quarterly report 2026.pdf", "pdf", 13);
            Thread.sleep(500);
            fake.enqueue(OWNER, "private", Map.of("photo", java.util.List.of(FakeTelegram.photoSize("img1", 640 * 480))));
            fake.enqueue(OWNER, "private", Map.of("photo", java.util.List.of(FakeTelegram.photoSize("img2", 480 * 640))));
            fake.enqueue(OWNER, "private", Map.of("video", Map.of("file_id", "vid", "file_size", 3_400_000, "file_name", "holiday.mp4")));
            fake.enqueue(OWNER, "private", Map.of("voice", Map.of("file_id", "snd", "file_size", 820_000)));
            fake.doc(OWNER, "project-files.zip", "zip", 1_900_000);
            fake.doc(OWNER, "setup.exe", "pdf", 13);
            fake.text(777, "hello bot");
            waitFor(() -> ctx.history.count() >= 6, 8000);
            Thread.sleep(600);
            snap("s0-dashboard");

            ui(() -> showShell(1));
            snap("s1-files");
            ui(() -> showShell(2));
            snap("s2-log");
            ui(() -> showShell(3));
            snap("s3-settings");
            SettingsPage.advancedOpenForTests(true);
            ui(() -> showShell(3));
            Thread.sleep(300);
            ui(() -> { ScrollPane sp = (ScrollPane) stage.getScene().getRoot().lookup(".scroll-pane"); if (sp != null) sp.setVvalue(0.62); });
            snap("s3b-settings-advanced");

            // ---- dialogs
            ui(() -> dialogs.changeOwner(null));
            snap("d1-change-owner");
            ui(modal::close);
            ui(() -> dialogs.allowDangerous(() -> { }, () -> { }));
            snap("d2-allow-dangerous");
            ui(modal::close);
            ui(() -> dialogs.changeToken(null));
            snap("d3-change-token");
            ui(modal::close);

            // ---- the bot stops: dashboard in the stopped state
            ctx.bot.stop();
            ui(() -> showShell(0));
            snap("s4-dashboard-stopped");
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            System.out.println("done");
            System.exit(0);
        }
    }
}
