package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.bot.Gateway;
import dev.badr.pocketdrop.bot.Pairing;
import dev.badr.pocketdrop.bot.Gateways;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.Log;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Map;

import static dev.badr.pocketdrop.core.I18n.t;

/** All pop-up dialogs of the app. */
public final class Dialogs {
    private final AppContext ctx;
    private final Modal modal;

    public Dialogs(AppContext ctx, Modal modal) {
        this.ctx = ctx;
        this.modal = modal;
    }

    private double cardWidth = 480;

    /** Wrapping paragraph with an explicit width (keeps Arabic text from running past the card). */
    private Label para(String text, String... classes) {
        Label l = W.wrap(text, classes);
        double w = cardWidth - 52;
        l.setPrefWidth(w);
        l.setMaxWidth(w);
        return l;
    }

    private VBox card(double width) {
        cardWidth = width;
        VBox v = new VBox(16);
        v.getStyleClass().add("modal-card");
        v.setMaxSize(width, javafx.scene.layout.Region.USE_PREF_SIZE);
        v.setPrefWidth(width);
        return v;
    }

    private HBox buttons(Node... nodes) {
        HBox h = new HBox(10, nodes);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    // ------------------------------------------------------------ simple message

    public void message(String icon, String title, String text, boolean danger) {
        VBox c = card(460);
        Button ok = W.button(t("common.ok"), danger ? "btn-danger" : "btn-primary");
        ok.setOnAction(e -> modal.close());
        c.getChildren().addAll(W.iconChip(icon, 52, danger), W.label(title, "h2"), para(text, "muted"), buttons(ok));
        modal.show(c, null);
    }

    // ------------------------------------------------------------ "is this you?"

    public void confirmLink(Pairing.Candidate cand) {
        VBox c = card(500);
        String user = cand.username() == null || cand.username().isBlank() ? t("dlg.link.nouser") : "@" + cand.username();
        VBox who = new VBox(4, W.label(cand.displayName(), "h2"),
                W.label(user + "   ·   id " + cand.userId(), "muted", "mono"));
        who.getStyleClass().add("card-flat");
        W.ltr(who);

        Button yes = W.button("check", t("dlg.link.yes"), "btn-primary");
        Button no = W.button(t("dlg.link.no"), "btn-ghost");
        yes.setOnAction(e -> {
            modal.close();
            new Thread(ctx.bot::confirmPairing, "pocketdrop-confirm").start();
        });
        no.setOnAction(e -> {
            modal.close();
            new Thread(ctx.bot::rejectPairing, "pocketdrop-reject").start();
        });
        c.getChildren().addAll(W.iconChip("user", 52, false), W.label(t("dlg.link.title"), "h2"),
                para(t("dlg.link.text"), "muted"), who, para(t("dlg.link.warn"), "warn-text", "small"), buttons(no, yes));
        modal.show(c, null);
    }

    // ------------------------------------------------------------ link a (new) account

    public void linkAccount(Runnable afterLinked) {
        VBox c = card(720);
        PairingPanel[] panel = new PairingPanel[1];
        Button close = W.button(t("common.cancel"), "btn-ghost");
        close.setOnAction(e -> modal.close());
        panel[0] = new PairingPanel(ctx, this::confirmLink, () -> {
            Platform.runLater(() -> {
                Timeline t = new Timeline(new KeyFrame(Duration.millis(1400), x -> {
                    modal.close();
                    if (afterLinked != null) afterLinked.run();
                }));
                t.play();
            });
        });
        c.getChildren().addAll(W.label(t("dlg.linkacc.title"), "h2"), para(t("dlg.linkacc.text"), "muted"),
                panel[0], buttons(close));
        modal.show(c, () -> panel[0].dispose());
    }

    // ------------------------------------------------------------ change token

    public void changeToken(Runnable done) {
        VBox c = card(520);
        PasswordField field = new PasswordField();
        field.setPromptText(t("wiz.token.prompt"));
        W.ltr(field);
        Label status = para("", "small");
        Button apply = W.button("check", t("dlg.token.apply"), "btn-primary");
        Button cancel = W.button(t("common.cancel"), "btn-ghost");
        cancel.setOnAction(e -> modal.close());
        apply.setOnAction(e -> {
            String token = field.getText().trim();
            if (!Gateways.looksLikeToken(token)) {
                status.setText(t("wiz.token.badformat"));
                status.getStyleClass().setAll("label", "small", "danger-text");
                Anim.shake(field);
                return;
            }
            apply.setDisable(true);
            status.setText(t("wiz.token.checking"));
            status.getStyleClass().setAll("label", "small", "muted");
            new Thread(() -> {
                try {
                    String username = BotService.checkToken(token);
                    ctx.bot.stop();
                    try {
                        ctx.store.setToken(token);
                    } catch (IOException secure) {
                        Log.error("Could not store the token securely", secure);
                        fail(status, apply, t("wiz.token.securefail"));
                        return;
                    }
                    Config cfg = ctx.cfg();
                    cfg.botUsername = username;
                    ctx.store.save();
                    ctx.bot.start();
                    Platform.runLater(() -> {
                        modal.close();
                        message("check", t("dlg.token.done.title"), t("dlg.token.done.text"), false);
                        if (done != null) done.run();
                    });
                } catch (Gateway.GatewayException ex) {
                    Log.warn("Token check failed: " + ex.getMessage());
                    fail(status, apply, ex.isUnavailable() ? t("wiz.token.unsafe") : ex.code() == 401 ? t("wiz.token.rejected")
                            : ex.isNetwork() ? t("wiz.token.network") : t("wiz.token.error"));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "pocketdrop-token").start();
        });
        c.getChildren().addAll(W.iconChip("lock", 52, false), W.label(t("dlg.token.title"), "h2"),
                para(t("dlg.token.text"), "muted"), field, status, buttons(cancel, apply));
        modal.show(c, null);
    }

    private void fail(Label status, Button apply, String text) {
        Platform.runLater(() -> {
            status.setText(text);
            status.getStyleClass().setAll("label", "small", "danger-text");
            apply.setDisable(false);
        });
    }

    // ------------------------------------------------------------ change linked account (danger)

    public void changeOwner(Runnable done) {
        VBox c = card(560);
        c.getStyleClass().add("card-danger");
        String word = t("dlg.owner.word");
        TextField typed = new TextField();
        typed.setPromptText(word);
        Button go = W.button(t("dlg.owner.go"), "btn-danger");
        Button cancel = W.button(t("common.cancel"), "btn-ghost");
        Timeline[] tl = new Timeline[1];
        cancel.setOnAction(e -> { if (tl[0] != null) tl[0].stop(); modal.close(); });
        int[] left = {5};
        Runnable refresh = () -> {
            boolean wordOk = typed.getText().trim().equalsIgnoreCase(word);
            go.setDisable(left[0] > 0 || !wordOk);
            go.setText(left[0] > 0 ? t("dlg.owner.go") + "  (" + left[0] + ")" : t("dlg.owner.go"));
        };
        typed.textProperty().addListener((o, a, b) -> refresh.run());
        tl[0] = new Timeline(new KeyFrame(Duration.seconds(1), e -> { left[0]--; refresh.run(); }));
        tl[0].setCycleCount(5);
        tl[0].play();
        refresh.run();
        go.setOnAction(e -> {
            modal.close();
            new Thread(() -> {
                ctx.bot.clearOwner();
                Platform.runLater(() -> {
                    if (done != null) done.run();
                    linkAccount(done);
                });
            }, "pocketdrop-unlink").start();
        });
        c.getChildren().addAll(W.iconChip("alert", 52, true), W.label(t("dlg.owner.title"), "h2", "danger-text"),
                para(t("dlg.owner.warn1"), ""), para(t("dlg.owner.warn2"), "muted"),
                W.label(t("dlg.owner.type", word), "small", "muted"), typed, buttons(cancel, go));
        modal.show(c, () -> tl[0].stop());
    }

    // ------------------------------------------------------------ allow dangerous file types

    public void allowDangerous(Runnable onConfirm, Runnable onCancel) {
        VBox c = card(540);
        c.getStyleClass().add("card-danger");
        Button go = W.button(t("dlg.danger.go"), "btn-danger");
        Button cancel = W.button(t("dlg.danger.cancel"), "btn-ghost");
        int[] left = {5};
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            left[0]--;
            go.setDisable(left[0] > 0);
            go.setText(left[0] > 0 ? t("dlg.danger.go") + "  (" + left[0] + ")" : t("dlg.danger.go"));
        }));
        tl.setCycleCount(5);
        go.setDisable(true);
        go.setText(t("dlg.danger.go") + "  (5)");
        tl.play();
        go.setOnAction(e -> { tl.stop(); modal.close(); onConfirm.run(); });
        cancel.setOnAction(e -> { tl.stop(); modal.close(); onCancel.run(); });
        c.getChildren().addAll(W.iconChip("alert", 52, true), W.label(t("dlg.danger.title"), "h2", "danger-text"),
                para(t("dlg.danger.text"), ""), para(t("dlg.danger.text2"), "muted"), buttons(cancel, go));
        modal.show(c, () -> { tl.stop(); });
    }

    // ------------------------------------------------------------ generic yes / no

    public void confirm(String icon, String title, String text, String yesText, boolean danger, Runnable onYes) {
        VBox c = card(480);
        Button yes = W.button(yesText, danger ? "btn-danger" : "btn-primary");
        Button no = W.button(t("common.cancel"), "btn-ghost");
        yes.setOnAction(e -> { modal.close(); onYes.run(); });
        no.setOnAction(e -> modal.close());
        c.getChildren().addAll(W.iconChip(icon, 52, danger), W.label(title, "h2"), para(text, "muted"), buttons(no, yes));
        modal.show(c, null);
    }
}
