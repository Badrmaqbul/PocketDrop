package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotListener;
import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.core.AppDirs;
import dev.badr.pocketdrop.core.AppInfo;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.Log;
import dev.badr.pocketdrop.core.SecretProtector;
import dev.badr.pocketdrop.storage.SavedItem;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static dev.badr.pocketdrop.core.I18n.t;

/** PocketDrop main window. */
public class PocketDropApp extends Application {
    AppContext ctx;
    Stage stage;
    private final StackPane contentHost = new StackPane();
    final Modal modal = new Modal();
    Dialogs dialogs;
    private Shell shell;
    private Wizard wizard;
    private TrayManager tray;
    private FileChannel lockChannel;
    private FileLock lock;
    private boolean trayHintShown;
    private double dragX, dragY;

    @Override
    public void start(Stage primary) {
        stage = primary;
        boolean minimized = getParameters().getRaw().contains("--minimized");
        try {
            AppDirs.ensure();
        } catch (IOException e) {
            System.err.println("Cannot create the PocketDrop data folder: " + e.getMessage());
            Platform.exit();
            return;
        }
        if (!acquireLock()) {
            askRunningCopyToShow();
            Platform.exit();
            System.exit(0);
            return;
        }

        ctx = createContext();
        Config cfg = ctx.cfg();
        Log.init(AppDirs.logs(), cfg.logRetentionDays);
        Log.info(AppInfo.NAME + " " + AppInfo.VERSION + " started");
        I18n.setLanguage(cfg.language != null ? cfg.language : ("ar".equals(I18n.deviceLanguage()) ? "ar" : "en"));
        Platform.setImplicitExit(false);

        buildWindow();
        tray = new TrayManager(ctx, this::showWindow, this::toggleBotFromTray, this::togglePauseFromTray, this::quit);
        boolean trayOk = tray.install();
        if (!trayOk) tray = null;

        ctx.bot.addListener(new BotListener() {
            @Override public void onState(BotService.State s, String d) { Platform.runLater(() -> { if (tray != null) tray.refresh(); }); }
            @Override public void onPausedChanged(boolean p) { Platform.runLater(() -> { if (tray != null) tray.refresh(); }); }
            @Override public void onSaved(SavedItem item) { Platform.runLater(() -> notifySaved(item)); }
        });

        if (cfg.setupDone) {
            showShell(0);
            Thread t = new Thread(ctx.bot::start, "pocketdrop-autostart");
            t.setDaemon(true);
            t.start();
        } else {
            showWizard(cfg.language == null ? Wizard.LANGUAGE : Wizard.INTRO);
        }
        if (!(minimized && cfg.setupDone && tray != null)) stage.show();

        Timeline signal = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            try {
                if (Files.deleteIfExists(showSignal())) showWindow();
            } catch (IOException ignored) { }
        }));
        signal.setCycleCount(Animation.INDEFINITE);
        signal.play();
    }

    /** Overridden by the screenshot tool, which needs a context that does not use Windows encryption. */
    AppContext createContext() {
        return new AppContext(SecretProtector.systemDefault());
    }

    // ------------------------------------------------------------------ window

    private void buildWindow() {
        StackPane windowRoot = new StackPane();
        windowRoot.getStyleClass().add("window");
        VBox frame = new VBox(titleBar(), contentHost);
        VBox.setVgrow(contentHost, Priority.ALWAYS);
        dialogs = new Dialogs(ctx, modal);
        windowRoot.getChildren().addAll(frame, modal);

        Scene scene = new Scene(windowRoot, 1040, 700, Color.web("#0F0E17"));
        Theme.apply(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setTitle(AppInfo.NAME);
        for (int px : new int[]{32, 64, 256}) {
            var img = Brand.fxImage(px);
            if (img != null) stage.getIcons().add(img);
        }
        stage.setOnCloseRequest(e -> { e.consume(); requestClose(); });
        contentHost.setFocusTraversable(true);
        contentHost.setMinHeight(0);
        stage.setOnShown(e -> contentHost.requestFocus());
        applyOrientation();
    }

    private HBox titleBar() {
        Button min = new Button();
        min.getStyleClass().add("win-btn");
        min.setGraphic(Icons.icon("minus", 16, "icon-text"));
        min.setFocusTraversable(false);
        min.setOnAction(e -> stage.setIconified(true));
        Button close = new Button();
        close.getStyleClass().addAll("win-btn", "win-btn-close");
        close.setGraphic(Icons.icon("x", 16, "icon-text"));
        close.setFocusTraversable(false);
        close.setOnAction(e -> requestClose());

        HBox bar = new HBox(10, Brand.mark(20), W.label(AppInfo.NAME, "title-text"), W.spacer(), min, close);
        bar.getStyleClass().add("titlebar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        bar.setOnMousePressed(e -> { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
        bar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); });
        return bar;
    }

    private void applyOrientation() {
        NodeOrientation o = I18n.rtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
        contentHost.setNodeOrientation(o);
        modal.setNodeOrientation(o);
    }

    void showWizard(int step) {
        if (shell != null) { shell.dispose(); shell = null; }
        wizard = new Wizard(ctx, dialogs, stage, step, this::languageChosenInWizard, this::wizardFinished);
        contentHost.getChildren().setAll(wizard);
    }

    void showShell(int page) {
        if (shell != null) shell.dispose();
        wizard = null;
        shell = new Shell(ctx, dialogs, stage, this::changeLanguage, this::rebuildCurrent, this::settingsChanged, page);
        contentHost.getChildren().setAll(shell);
    }

    private void rebuildCurrent() {
        showShell(shell == null ? 0 : shell.currentIndex());
    }

    private void settingsChanged() {
        if (tray != null) tray.refresh();
    }

    // ------------------------------------------------------------------ language

    private void setLanguage(String code) {
        I18n.setLanguage(code);
        ctx.cfg().language = code;
        ctx.store.save();
        applyOrientation();
        if (tray != null) tray.refresh();
    }

    void languageChosenInWizard(String code) {
        setLanguage(code);
        showWizard(Wizard.INTRO);
    }

    private void changeLanguage(String code) {
        setLanguage(code);
        rebuildCurrent();
        Thread t = new Thread(() -> { if (ctx.bot.isRunning()) ctx.bot.restart(); }, "pocketdrop-lang");
        t.setDaemon(true);
        t.start(); // republishes the command descriptions in the new language
    }

    private void wizardFinished() {
        showShell(0);
        Thread t = new Thread(ctx.bot::start, "pocketdrop-start");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ close, tray, notifications

    private void requestClose() {
        Config c = ctx.cfg();
        if (c.setupDone && c.minimizeToTray && tray != null) {
            stage.hide();
            if (!trayHintShown) {
                trayHintShown = true;
                tray.notify(AppInfo.NAME, t("tray.hint"));
            }
        } else {
            quit();
        }
    }

    private void showWindow() {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    private void toggleBotFromTray() {
        BotService.State s = ctx.bot.state();
        boolean on = s == BotService.State.RUNNING || s == BotService.State.STARTING || s == BotService.State.RECONNECTING;
        Thread t = new Thread(() -> { if (on) ctx.bot.stop(); else ctx.bot.start(); }, "pocketdrop-tray");
        t.setDaemon(true);
        t.start();
    }

    private void togglePauseFromTray() {
        ctx.bot.setPaused(!ctx.cfg().paused);
    }

    private void notifySaved(SavedItem item) {
        if (tray == null || !ctx.cfg().notifications) return;
        if (stage.isShowing() && stage.isFocused()) return;
        tray.notify(t("tray.saved"), item.name());
    }

    private void quit() {
        Thread t = new Thread(() -> {
            ctx.bot.stop();
            Platform.runLater(() -> {
                if (tray != null) tray.remove();
                releaseLock();
                Platform.exit();
                System.exit(0);
            });
        }, "pocketdrop-quit");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ one copy only

    private static Path showSignal() { return AppDirs.home().resolve("show.signal"); }

    private boolean acquireLock() {
        try {
            lockChannel = FileChannel.open(AppDirs.lock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            return lock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }

    private void releaseLock() {
        try {
            if (lock != null) lock.release();
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) { }
    }

    private void askRunningCopyToShow() {
        try {
            Files.writeString(showSignal(), "1");
        } catch (IOException ignored) { }
    }

    @Override
    public void stop() {
        if (ctx != null) ctx.bot.stop();
    }
}
