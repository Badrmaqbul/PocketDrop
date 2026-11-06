package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotListener;
import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.Fmt;
import dev.badr.pocketdrop.storage.SavedItem;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.function.Consumer;

import static dev.badr.pocketdrop.core.I18n.t;

/** Status ring, start/stop, today's numbers and the latest files. */
final class DashboardPage implements Page {
    private final AppContext ctx;
    private final Dialogs dialogs;
    private final Consumer<Integer> navigate;

    private final VBox root = new VBox(16);
    private final javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane();
    private final VBox banners = new VBox(10);
    private final Circle ring = new Circle(40);
    private final Circle pulseCircle = new Circle(40);
    private final Label botName = W.label("", "h2");
    private final Label statePill = W.pill("", "pill-muted");
    private final Label detail = W.wrap("", "muted", "small");
    private final Button toggle = W.button("play", t("dash.start"), "btn-primary");
    private final Button pause = W.button("pause", t("dash.pause"), "btn-ghost");
    private final Label todayValue = W.label("0", "tile-value");
    private final Label totalValue = W.label("0", "tile-value");
    private final Label sizeValue = W.label("0 B", "tile-value");
    private final VBox recent = new VBox(8);
    private Animation pulse;
    private Node emptyNode;

    private final BotListener listener = new BotListener() {
        @Override public void onState(BotService.State s, String d) { Platform.runLater(DashboardPage.this::refreshState); }
        @Override public void onSaved(SavedItem item) { Platform.runLater(() -> added(item)); }
        @Override public void onOwnerChanged() { Platform.runLater(DashboardPage.this::refreshAll); }
        @Override public void onPausedChanged(boolean p) { Platform.runLater(DashboardPage.this::refreshState); }
    };

    DashboardPage(AppContext ctx, Dialogs dialogs, Consumer<Integer> navigate) {
        this.ctx = ctx;
        this.dialogs = dialogs;
        this.navigate = navigate;
        build();
        ctx.bot.addListener(listener);
        refreshAll();
    }

    @Override public Node view() { return scroll; }

    @Override public void onShow() { refreshAll(); }

    @Override public void dispose() {
        ctx.bot.removeListener(listener);
        if (pulse != null) pulse.stop();
    }

    private void build() {
        root.setPadding(new Insets(24, 30, 26, 30));
        scroll.setContent(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);

        Label title = W.label(t("nav.dashboard"), "h1");

        // hero card
        pulseCircle.setFill(Color.TRANSPARENT);
        pulseCircle.setStrokeWidth(3);
        pulseCircle.setStyle("-fx-stroke: -pd-accent;");
        pulseCircle.setVisible(false);
        ring.setFill(Color.TRANSPARENT);
        ring.setStrokeWidth(6);
        StackPane ringBox = new StackPane(pulseCircle, ring, Icons.icon("drop", 36, "icon-text"));
        ringBox.setMinSize(116, 116);
        ringBox.setPrefSize(116, 116);

        VBox info = new VBox(8, botName, statePill, detail);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        detail.setMaxWidth(380);

        toggle.setMinWidth(170);
        pause.setMinWidth(170);
        toggle.setOnAction(e -> toggleBot());
        pause.setOnAction(e -> ctx.bot.setPaused(!ctx.cfg().paused));
        VBox actions = new VBox(10, toggle, pause);
        actions.setAlignment(Pos.CENTER);

        HBox hero = new HBox(22, ringBox, info, actions);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("card");

        // stats
        HBox stats = new HBox(14, tile(t("dash.today"), todayValue), tile(t("dash.total"), totalValue),
                tile(t("dash.storage"), sizeValue));
        for (Node n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // recent
        Button all = W.button(t("dash.viewall"), "btn-link");
        all.setOnAction(e -> navigate.accept(1));
        HBox head = new HBox(10, W.label(t("dash.recent"), "h2"), W.spacer(), all);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox recentCard = new VBox(12, head, recent);
        recentCard.getStyleClass().add("card");
        VBox.setVgrow(recentCard, Priority.ALWAYS);

        root.getChildren().addAll(title, banners, hero, stats, recentCard);
    }

    private Node tile(String label, Label value) {
        VBox v = new VBox(4, W.label(label, "muted", "small"), value);
        v.getStyleClass().add("tile");
        return v;
    }

    private void toggleBot() {
        toggle.setDisable(true);
        BotService.State s = ctx.bot.state();
        boolean on = s == BotService.State.RUNNING || s == BotService.State.STARTING || s == BotService.State.RECONNECTING;
        new Thread(() -> {
            if (on) ctx.bot.stop(); else ctx.bot.start();
            Platform.runLater(() -> toggle.setDisable(false));
        }, "pocketdrop-toggle").start();
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        refreshState();
        refreshBanners();
        refreshStats();
        refreshRecent();
    }

    void refreshState() {
        BotService.State s = ctx.bot.state();
        Config cfg = ctx.cfg();
        botName.setText(ctx.bot.botUsername().isEmpty() ? "PocketDrop" : "@" + ctx.bot.botUsername());
        W.ltr(botName);

        String stroke;
        String pill;
        String text;
        switch (s) {
            case RUNNING -> { stroke = "-pd-accent"; pill = "pill-ok"; text = cfg.paused ? t("state.paused") : t("state.running"); }
            case STARTING, RECONNECTING -> { stroke = "-pd-warn"; pill = "pill-warn"; text = s == BotService.State.STARTING ? t("state.starting") : t("state.reconnecting"); }
            case ERROR -> { stroke = "-pd-danger"; pill = "pill-danger"; text = t("state.error"); }
            default -> { stroke = "-pd-border"; pill = "pill-muted"; text = t("state.stopped"); }
        }
        if (s == BotService.State.RUNNING && cfg.paused) pill = "pill-warn";
        ring.setStyle("-fx-stroke: " + stroke + ";");
        statePill.getStyleClass().setAll("label", "pill", pill);
        statePill.setText(text);

        String d = ctx.bot.detail();
        detail.setText(d == null || d.isEmpty() ? (s == BotService.State.STOPPED ? t("state.detail.stopped") : "") : t("state.detail." + d));

        boolean on = s == BotService.State.RUNNING || s == BotService.State.STARTING || s == BotService.State.RECONNECTING;
        toggle.setText(on ? t("dash.stop") : t("dash.start"));
        toggle.setGraphic(Icons.icon(on ? "stop" : "play", 18, "icon-white"));
        toggle.getStyleClass().setAll("button", on ? "btn-ghost" : "btn-primary");
        if (on) toggle.setGraphic(Icons.icon("stop", 18, "icon-text"));
        pause.setText(cfg.paused ? t("dash.resume") : t("dash.pause"));
        pause.setGraphic(Icons.icon(cfg.paused ? "play" : "pause", 18, "icon-text"));
        pause.setDisable(s != BotService.State.RUNNING);

        boolean running = s == BotService.State.RUNNING && !cfg.paused;
        pulseCircle.setVisible(running);
        if (running && pulse == null) pulse = Anim.pulse(pulseCircle);
        if (!running && pulse != null) { pulse.stop(); pulse = null; pulseCircle.setOpacity(0); }
        refreshBanners();
    }

    private void refreshBanners() {
        banners.getChildren().clear();
        Config cfg = ctx.cfg();
        if (!cfg.hasOwner()) {
            Button link = W.button("link", t("dash.banner.link.btn"), "btn-primary");
            link.setOnAction(e -> dialogs.linkAccount(this::refreshAll));
            banners.getChildren().add(banner("banner-warn", "alert", "icon-warn", t("dash.banner.nolink"), link));
        }
        if (ctx.bot.state() == BotService.State.ERROR && "unsafe-client".equals(ctx.bot.detail())) {
            banners.getChildren().add(banner("banner-danger", "alert", "icon-danger", t("dash.banner.unsafe"), null));
        } else if (ctx.bot.state() == BotService.State.ERROR) {
            Button fix = W.button("lock", t("dash.banner.token.btn"), "btn-primary");
            fix.setOnAction(e -> dialogs.changeToken(this::refreshAll));
            banners.getChildren().add(banner("banner-danger", "alert", "icon-danger", t("dash.banner.token"), fix));
        } else if ("conflict".equals(ctx.bot.detail())) {
            banners.getChildren().add(banner("banner-warn", "alert", "icon-warn", t("dash.banner.conflict"), null));
        }
    }

    private Node banner(String style, String icon, String iconClass, String text, Button action) {
        Label l = W.wrap(text, "");
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox h = new HBox(14, Icons.icon(icon, 22, iconClass), l);
        if (action != null) h.getChildren().add(action);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add(style);
        return h;
    }

    private void refreshStats() {
        todayValue.setText(String.valueOf(ctx.history.countToday()));
        totalValue.setText(String.valueOf(ctx.history.count()));
        sizeValue.setText(Fmt.size(ctx.history.totalBytes()));
    }

    private void refreshRecent() {
        recent.getChildren().clear();
        emptyNode = null;
        List<SavedItem> last = ctx.history.last(5);
        if (last.isEmpty()) {
            VBox empty = new VBox(8, Icons.icon("drop", 30), W.label(t("dash.empty"), "muted"));
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(26, 0, 26, 0));
            emptyNode = empty;
            recent.getChildren().add(empty);
            return;
        }
        for (SavedItem it : last) recent.getChildren().add(row(it));
    }

    private void added(SavedItem item) {
        refreshStats();
        if (emptyNode != null) {
            recent.getChildren().remove(emptyNode);
            emptyNode = null;
        }
        StackPane row = (StackPane) row(item);
        recent.getChildren().add(0, row);
        while (recent.getChildren().size() > 5) recent.getChildren().remove(recent.getChildren().size() - 1);
        Anim.dropIn(row);
        Anim.flash((Region) row.getChildren().get(1));
    }

    private Node row(SavedItem it) {
        Label name = W.fileName(it.name(), "h3");
        VBox text = new VBox(1, name, W.label(Fmt.size(it.size()) + "  ·  " + Fmt.when(it.time()), "muted", "small"));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox h = new HBox(12, W.iconChip(Icons.forKind(it.kind()), 40, false), text,
                W.pill(t("kind." + it.kind().id()), "pill-muted"));
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("row");

        Region glow = new Region();
        glow.setStyle("-fx-background-color: rgba(124,92,255,0.30); -fx-background-radius: 12;");
        glow.setMouseTransparent(true);
        glow.setOpacity(0);
        return new StackPane(h, glow);
    }
}
