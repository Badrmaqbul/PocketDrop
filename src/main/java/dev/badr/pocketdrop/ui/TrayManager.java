package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.core.AppInfo;
import dev.badr.pocketdrop.core.Log;
import javafx.application.Platform;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

import static dev.badr.pocketdrop.core.I18n.t;

/** Icon next to the clock with a small menu and desktop notifications. */
final class TrayManager {
    private final AppContext ctx;
    private final Runnable showWindow;
    private final Runnable toggleBot;
    private final Runnable togglePause;
    private final Runnable quit;
    private TrayIcon icon;

    TrayManager(AppContext ctx, Runnable showWindow, Runnable toggleBot, Runnable togglePause, Runnable quit) {
        this.ctx = ctx;
        this.showWindow = showWindow;
        this.toggleBot = toggleBot;
        this.togglePause = togglePause;
        this.quit = quit;
    }

    boolean install() {
        try {
            if (!SystemTray.isSupported()) return false;
            SystemTray tray = SystemTray.getSystemTray();
            Dimension d = tray.getTrayIconSize();
            icon = new TrayIcon(Brand.image(Math.max(16, d.width)), AppInfo.NAME, buildMenu());
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> Platform.runLater(showWindow));
            tray.add(icon);
            return true;
        } catch (AWTException | RuntimeException | UnsatisfiedLinkError e) {
            Log.warn("Tray icon not available: " + e);
            icon = null;
            return false;
        }
    }

    private PopupMenu buildMenu() {
        PopupMenu m = new PopupMenu();
        boolean on = ctx.bot.state() == BotService.State.RUNNING || ctx.bot.state() == BotService.State.STARTING
                || ctx.bot.state() == BotService.State.RECONNECTING;
        add(m, t("tray.open"), () -> Platform.runLater(showWindow));
        add(m, on ? t("tray.stop") : t("tray.start"), () -> Platform.runLater(toggleBot));
        add(m, ctx.cfg().paused ? t("tray.resume") : t("tray.pause"), () -> Platform.runLater(togglePause));
        m.addSeparator();
        add(m, t("tray.quit"), () -> Platform.runLater(quit));
        return m;
    }

    private static void add(PopupMenu m, String text, Runnable r) {
        MenuItem mi = new MenuItem(text);
        mi.addActionListener(e -> r.run());
        m.add(mi);
    }

    void refresh() {
        if (icon == null) return;
        icon.setPopupMenu(buildMenu());
    }

    void notify(String title, String text) {
        if (icon != null) icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
    }

    void remove() {
        if (icon != null) {
            try { SystemTray.getSystemTray().remove(icon); } catch (RuntimeException ignored) { }
            icon = null;
        }
    }
}
