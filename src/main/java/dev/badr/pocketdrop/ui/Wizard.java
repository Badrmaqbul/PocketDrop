package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.bot.Gateway;
import dev.badr.pocketdrop.bot.Gateways;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.Log;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import static dev.badr.pocketdrop.core.I18n.t;

/** First-run guide: language, what the app does, bot token, linking, folder. */
public final class Wizard extends StackPane {
    public static final int LANGUAGE = 0, INTRO = 1, TOKEN = 2, LINK = 3, FOLDER = 4;
    private static final int STEPS = 5;

    private final AppContext ctx;
    private final Dialogs dialogs;
    private final Stage stage;
    private final Runnable onFinished;
    private final Consumer<String> onLanguageChosen;

    private final StackPane stepHost = new StackPane();
    private final HBox dots = new HBox(8);
    private final Button back = W.button(t("common.back"), "btn-ghost");
    private final Button next = W.button(t("common.next"), "btn-primary");
    private final HBox nav = new HBox(10);

    private int step = -1;
    private PairingPanel pairingPanel;
    private boolean tokenVerified;
    private boolean linked;

    public Wizard(AppContext ctx, Dialogs dialogs, Stage stage, int startStep,
                  Consumer<String> onLanguageChosen, Runnable onFinished) {
        this.ctx = ctx;
        this.dialogs = dialogs;
        this.stage = stage;
        this.onFinished = onFinished;
        this.onLanguageChosen = onLanguageChosen;
        this.tokenVerified = !ctx.cfg().tokenEnc.isEmpty() && startStep > TOKEN;
        this.linked = ctx.cfg().hasOwner() && startStep > LINK;

        dots.setAlignment(Pos.CENTER);
        nav.setAlignment(Pos.CENTER);
        nav.getChildren().addAll(back, W.spacer(), next);
        nav.setMaxWidth(760);

        VBox frame = new VBox(18, dots, stepHost, nav);
        frame.setAlignment(Pos.TOP_CENTER);
        frame.setPadding(new Insets(26, 40, 28, 40));
        VBox.setVgrow(stepHost, Priority.ALWAYS);
        getChildren().add(frame);

        back.setOnAction(e -> go(step - 1));
        next.setOnAction(e -> go(step + 1));
        go(startStep);
    }

    // ------------------------------------------------------------ navigation

    private void go(int target) {
        if (target < 0 || target >= STEPS) return;
        if (step == LINK && pairingPanel != null && target != LINK) {
            pairingPanel.dispose();
            pairingPanel = null;
        }
        boolean forward = target >= step;
        step = target;

        Node content = switch (step) {
            case LANGUAGE -> languageStep();
            case INTRO -> introStep();
            case TOKEN -> tokenStep();
            case LINK -> linkStep();
            default -> folderStep();
        };
        VBox holder = new VBox(content);
        holder.setAlignment(Pos.TOP_CENTER);
        stepHost.getChildren().setAll(holder);
        double dx = (forward ? 36 : -36) * (I18n.rtl() ? -1 : 1);
        Anim.slideIn(holder, dx, 260);

        dots.getChildren().clear();
        for (int i = 0; i < STEPS; i++) {
            Label d = new Label();
            d.getStyleClass().addAll(i == step ? new String[]{"dot", "dot-active"} : new String[]{"dot"});
            dots.getChildren().add(d);
        }
        nav.setVisible(step != LANGUAGE);
        back.setVisible(step > INTRO);
        next.setText(step == FOLDER ? t("wiz.finish") : step == INTRO ? t("wiz.start") : t("common.next"));
        next.setGraphic(step == FOLDER ? Icons.icon("check", 18, "icon-white") : null);
        updateNext();
        if (step == FOLDER) next.setOnAction(e -> finish());
        else next.setOnAction(e -> go(step + 1));
    }

    private void updateNext() {
        next.setDisable((step == TOKEN && !tokenVerified) || (step == LINK && !linked));
    }

    // ------------------------------------------------------------ step 0: language

    private Node languageStep() {
        VBox v = new VBox(26);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(40, 0, 0, 0));
        Button ar = new Button("العربية");
        Button en = new Button("English");
        for (Button b : new Button[]{ar, en}) {
            b.getStyleClass().add("lang-btn");
            b.setFocusTraversable(false);
            b.setPrefWidth(250);
        }
        ar.setOnAction(e -> onLanguageChosen.accept("ar"));
        en.setOnAction(e -> onLanguageChosen.accept("en"));
        HBox row = new HBox(22, ar, en);
        row.setAlignment(Pos.CENTER);
        W.ltr(row);
        v.getChildren().addAll(Brand.mark(84), W.label(t("wiz.lang.title"), "h1"),
                W.wrap(t("wiz.lang.sub"), "muted"), row);
        return v;
    }

    // ------------------------------------------------------------ step 1: what it does

    private Node introStep() {
        VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);
        v.setMaxWidth(760);
        Label title = W.label(t("wiz.intro.title"), "h1");
        Label sub = W.wrap(t("wiz.intro.sub"), "muted");
        sub.setMaxWidth(620);
        sub.setAlignment(Pos.CENTER);
        sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        HBox cards = new HBox(16,
                introCard("phone", t("wiz.intro.c1.t"), t("wiz.intro.c1.d")),
                introCard("folder", t("wiz.intro.c2.t"), t("wiz.intro.c2.d")),
                introCard("shield", t("wiz.intro.c3.t"), t("wiz.intro.c3.d")));
        cards.setAlignment(Pos.TOP_CENTER);
        VBox.setMargin(cards, new Insets(26, 0, 0, 0));

        v.getChildren().addAll(Brand.mark(64), title, sub, cards);
        v.setSpacing(10);
        VBox.setMargin(title, new Insets(10, 0, 0, 0));
        int i = 0;
        for (Node n : cards.getChildren()) {
            n.setOpacity(0);
            final Node card = n;
            new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(140 + i * 110), e -> Anim.dropIn(card))).play();
            i++;
        }
        return v;
    }

    private Node introCard(String icon, String title, String text) {
        VBox c = new VBox(10, W.iconChip(icon, 48, false), W.label(title, "h3"), W.wrap(text, "muted", "small"));
        c.getStyleClass().add("card");
        c.setPrefWidth(242);
        c.setMinHeight(190);
        return c;
    }

    // ------------------------------------------------------------ step 2: token

    private Node tokenStep() {
        VBox v = new VBox(14);
        v.setMaxWidth(760);
        Label title = W.label(t("wiz.token.title"), "h1");
        Label sub = W.wrap(t("wiz.token.sub"), "muted");

        VBox steps = new VBox(12,
                numbered(1, t("wiz.token.s1")), numbered(2, t("wiz.token.s2")), numbered(3, t("wiz.token.s3")));
        Button botFather = W.button("open", t("wiz.token.open"), "btn-ghost");
        botFather.setOnAction(e -> OpenUtil.link("https://t.me/BotFather"));
        HBox stepsRow = new HBox(20, steps, W.spacer(), botFather);
        stepsRow.setAlignment(Pos.CENTER_LEFT);
        VBox stepsCard = W.card(stepsRow);

        PasswordField field = new PasswordField();
        field.setPromptText(t("wiz.token.prompt"));
        W.ltr(field);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button verify = W.button("shield", t("wiz.token.verify"), "btn-primary");
        HBox fieldRow = new HBox(10, field, verify);
        fieldRow.setAlignment(Pos.CENTER_LEFT);
        Label status = W.wrap(tokenVerified ? t("wiz.token.ok", ctx.cfg().botUsername) : t("wiz.token.note"), "small",
                tokenVerified ? "ok-text" : "muted");

        verify.setOnAction(e -> {
            String token = field.getText().trim();
            if (!Gateways.looksLikeToken(token)) {
                status.setText(t("wiz.token.badformat"));
                status.getStyleClass().setAll("label", "small", "danger-text");
                Anim.shake(field);
                return;
            }
            verify.setDisable(true);
            tokenVerified = false;
            updateNext();
            status.setText(t("wiz.token.checking"));
            status.getStyleClass().setAll("label", "small", "muted");
            new Thread(() -> verifyToken(token, status, verify), "pocketdrop-token").start();
        });
        field.setOnAction(e -> verify.fire());

        v.getChildren().addAll(title, sub, stepsCard, new Label(), W.label(t("wiz.token.label"), "h3"), fieldRow, status);
        return v;
    }

    private void verifyToken(String token, Label status, Button verify) {
        try {
            String username = BotService.checkToken(token);
            ctx.bot.stop();
            try {
                ctx.store.setToken(token);
            } catch (IOException secure) {
                Log.error("Could not store the token securely", secure);
                tokenFail(status, verify, t("wiz.token.securefail"));
                return;
            }
            Config cfg = ctx.cfg();
            cfg.botUsername = username;
            ctx.store.save();
            ctx.bot.start();
            Platform.runLater(() -> {
                tokenVerified = true;
                status.setText(t("wiz.token.ok", username));
                status.getStyleClass().setAll("label", "small", "ok-text");
                verify.setDisable(false);
                updateNext();
            });
        } catch (Gateway.GatewayException ex) {
            Log.warn("Token check failed: " + ex.getMessage());
            tokenFail(status, verify, ex.isUnavailable() ? t("wiz.token.unsafe") : ex.code() == 401 ? t("wiz.token.rejected")
                    : ex.isNetwork() ? t("wiz.token.network") : t("wiz.token.error"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void tokenFail(Label status, Button verify, String text) {
        Platform.runLater(() -> {
            status.setText(text);
            status.getStyleClass().setAll("label", "small", "danger-text");
            verify.setDisable(false);
        });
    }

    private Node numbered(int n, String text) {
        Label num = new Label(String.valueOf(n));
        num.getStyleClass().add("step-num");
        Label l = W.wrap(text, "");
        l.setMaxWidth(470);
        HBox h = new HBox(12, num, l);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ------------------------------------------------------------ step 3: link

    private Node linkStep() {
        VBox v = new VBox(14);
        v.setMaxWidth(760);
        v.getChildren().addAll(W.label(t("wiz.link.title"), "h1"), W.wrap(t("wiz.link.sub"), "muted"));
        if (ctx.bot.state() == BotService.State.STOPPED || ctx.bot.state() == BotService.State.ERROR) ctx.bot.start();
        pairingPanel = new PairingPanel(ctx, dialogs::confirmLink, () -> Platform.runLater(() -> {
            linked = true;
            updateNext();
        }));
        VBox card = W.card(pairingPanel);
        v.getChildren().add(card);
        v.getChildren().add(W.wrap(t("wiz.link.note"), "muted", "small"));
        return v;
    }

    // ------------------------------------------------------------ step 4: folder & quick settings

    private Node folderStep() {
        Config cfg = ctx.cfg();
        if (cfg.saveDir == null || cfg.saveDir.isBlank()) cfg.saveDir = defaultFolder().toString();

        VBox v = new VBox(14);
        v.setMaxWidth(760);
        TextField folder = new TextField(cfg.saveDir);
        folder.setEditable(false);
        W.ltr(folder);
        HBox.setHgrow(folder, Priority.ALWAYS);
        Button browse = W.button("folder", t("common.browse"), "btn-ghost");
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(t("wiz.folder.pick"));
            File start = new File(cfg.saveDir);
            if (start.isDirectory()) dc.setInitialDirectory(start);
            File chosen = dc.showDialog(stage);
            if (chosen != null) {
                cfg.saveDir = chosen.getAbsolutePath();
                folder.setText(cfg.saveDir);
            }
        });
        HBox folderRow = new HBox(10, folder, browse);
        folderRow.setAlignment(Pos.CENTER_LEFT);

        Switch notif = new Switch(cfg.notifications);
        notif.selectedProperty().addListener((o, a, b) -> cfg.notifications = b);
        Switch autostart = new Switch(cfg.startWithWindows);
        boolean canAuto = Autostart.available();
        autostart.setDisable(!canAuto);
        autostart.selectedProperty().addListener((o, a, b) -> cfg.startWithWindows = b);

        VBox quick = W.card(
                toggleRow("bell", t("wiz.folder.notif"), t("wiz.folder.notif.d"), notif),
                W.separator(),
                toggleRow("refresh", t("wiz.folder.auto"), canAuto ? t("wiz.folder.auto.d") : t("wiz.folder.auto.na"), autostart));
        quick.setSpacing(14);

        v.getChildren().addAll(W.label(t("wiz.folder.title"), "h1"), W.wrap(t("wiz.folder.sub"), "muted"),
                W.card(W.label(t("wiz.folder.label"), "h3"), folderRow, W.wrap(t("wiz.folder.hint"), "muted", "small")),
                quick);
        return v;
    }

    static Node toggleRow(String icon, String title, String desc, Switch sw) {
        VBox text = new VBox(2, W.label(title, "h3"), W.wrap(desc, "muted", "small"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(14, W.iconChip(icon, 40, false), text, sw);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static Path defaultFolder() {
        Path home = Path.of(System.getProperty("user.home"));
        Path docs = home.resolve("Documents");
        return (Files.isDirectory(docs) ? docs : home).resolve("PocketDrop");
    }

    private void finish() {
        Config cfg = ctx.cfg();
        try {
            Files.createDirectories(Path.of(cfg.saveDir));
        } catch (IOException | RuntimeException e) {
            dialogs.message("alert", t("wiz.folder.err.title"), t("wiz.folder.err.text"), true);
            return;
        }
        cfg.setupDone = true;
        ctx.store.save();
        if (cfg.startWithWindows) Autostart.set(true);
        if (pairingPanel != null) pairingPanel.dispose();
        onFinished.run();
    }
}
