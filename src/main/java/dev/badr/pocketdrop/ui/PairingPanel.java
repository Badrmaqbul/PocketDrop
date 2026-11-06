package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.Pairing;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.badr.pocketdrop.core.I18n.t;

/** Shows the link code, the link and the QR, and reacts while somebody sends the code. */
public final class PairingPanel extends VBox {
    private final AppContext ctx;
    private final Consumer<Pairing.Candidate> onCandidate;
    private final Runnable onLinked;
    private final VBox content = new VBox(16);
    private final Label countdown = W.label("", "muted", "mono");
    private final Timeline ticker;
    private final Runnable listener = () -> Platform.runLater(this::refresh);
    private Animation pulse;
    private boolean candidateShown;
    private boolean linkedFired;

    public PairingPanel(AppContext ctx, Consumer<Pairing.Candidate> onCandidate, Runnable onLinked) {
        this.ctx = ctx;
        this.onCandidate = onCandidate;
        this.onLinked = onLinked;
        getChildren().add(content);

        ctx.bot.pairing().addListener(listener);
        if (!ctx.cfg().hasOwner()) ctx.bot.beginPairing();

        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            ctx.bot.pairing().tick(Instant.now());
            updateCountdown();
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        refresh();
    }

    public void dispose() {
        ctx.bot.pairing().removeListener(listener);
        ticker.stop();
        if (pulse != null) pulse.stop();
        if (!ctx.cfg().hasOwner()) ctx.bot.cancelPairing();
    }

    private void refresh() {
        Pairing p = ctx.bot.pairing();
        Pairing.State st = p.state();
        if (pulse != null) { pulse.stop(); pulse = null; }
        content.getChildren().clear();

        switch (st) {
            case WAITING -> content.getChildren().addAll(codeSection(true), statusRow("wait"));
            case EXPIRED -> {
                Button again = W.button("refresh", t("pair.newcode"), "btn-primary");
                again.setOnAction(e -> ctx.bot.beginPairing());
                content.getChildren().addAll(statusRow("expired"), again);
            }
            case CONFIRMING -> {
                content.getChildren().addAll(codeSection(false), statusRow("confirm"));
                if (!candidateShown && p.candidate() != null) {
                    candidateShown = true;
                    onCandidate.accept(p.candidate());
                }
            }
            default -> {
                if (ctx.cfg().hasOwner()) {
                    content.getChildren().add(linkedBox());
                    if (!linkedFired) {
                        linkedFired = true;
                        onLinked.run();
                    }
                }
            }
        }
        if (st != Pairing.State.CONFIRMING) candidateShown = false;
        updateCountdown();
    }

    private Node codeSection(boolean active) {
        Pairing p = ctx.bot.pairing();
        String code = p.displayCode();
        String link = ctx.bot.botUsername().isEmpty() ? "" : ctx.bot.deepLink(p.code());

        Label codeLabel = W.label(code, "code-text");
        W.ltr(codeLabel);
        codeLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Button copyCode = copyButton(() -> p.code());
        HBox codeBox = new HBox(10, codeLabel, copyCode);
        codeBox.setAlignment(Pos.CENTER_LEFT);
        codeBox.getStyleClass().add("code-box");
        W.ltr(codeBox);
        codeBox.setOpacity(active ? 1 : 0.5);

        VBox left = new VBox(8, W.label(t("pair.code"), "h3"), W.wrap(t("pair.code.hint"), "muted", "small"), codeBox);
        if (!link.isEmpty()) {
            TextField linkField = new TextField(link);
            linkField.setEditable(false);
            linkField.setFocusTraversable(false);
            W.ltr(linkField);
            HBox linkRow = new HBox(8, linkField, copyButton(() -> link));
            HBox.setHgrow(linkField, javafx.scene.layout.Priority.ALWAYS);
            linkRow.setAlignment(Pos.CENTER_LEFT);
            left.getChildren().addAll(W.label(t("pair.link"), "h3"), W.wrap(t("pair.link.hint"), "muted", "small"), linkRow);
        }
        left.setMaxWidth(500);
        HBox.setHgrow(left, javafx.scene.layout.Priority.ALWAYS);

        HBox row = new HBox(24, left);
        row.setAlignment(Pos.TOP_LEFT);
        if (!link.isEmpty()) {
            VBox qr = new VBox(8, new QrView(link, 168), W.label(t("pair.qr"), "muted", "small"));
            qr.setAlignment(Pos.TOP_CENTER);
            qr.setOpacity(active ? 1 : 0.4);
            row.getChildren().add(qr);
        }
        return row;
    }

    private Node statusRow(String kind) {
        Circle dot = new Circle(6);
        StackPane mark = new StackPane();
        String text;
        switch (kind) {
            case "wait" -> {
                dot.setStyle("-fx-fill: -pd-accent;");
                Circle ring = new Circle(6);
                ring.setStyle("-fx-fill: -pd-accent;");
                mark.getChildren().addAll(ring, dot);
                pulse = Anim.pulse(ring);
                text = t("pair.waiting");
            }
            case "confirm" -> {
                dot.setStyle("-fx-fill: -pd-warn;");
                mark.getChildren().add(dot);
                text = t("pair.confirming");
            }
            default -> {
                dot.setStyle("-fx-fill: -pd-warn;");
                mark.getChildren().add(dot);
                text = t("pair.expired");
            }
        }
        mark.setMinSize(22, 22);
        HBox row = new HBox(12, mark, W.label(text, "h3"), W.spacer(), countdown);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-flat");
        return row;
    }

    private Node linkedBox() {
        var cfg = ctx.cfg();
        String who = cfg.ownerName.isBlank() ? "" : cfg.ownerName;
        if (!cfg.ownerUsername.isBlank()) who += "  @" + cfg.ownerUsername;
        Node check = Icons.icon("check", 26, "icon-ok");
        StackPane badge = new StackPane(check);
        badge.getStyleClass().add("icon-chip");
        badge.setMinSize(52, 52);
        badge.setPrefSize(52, 52);
        badge.setMaxSize(52, 52);
        badge.setStyle("-fx-background-color: -pd-ok-soft;");
        Anim.popIn(badge);
        VBox text = new VBox(2, W.label(t("pair.linked"), "h2", "ok-text"), W.label(who, "muted"));
        HBox box = new HBox(16, badge, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("banner-ok");
        return box;
    }

    private void updateCountdown() {
        Pairing p = ctx.bot.pairing();
        if (p.state() == Pairing.State.WAITING || p.state() == Pairing.State.CONFIRMING) {
            long secs = Math.max(0, java.time.Duration.between(Instant.now(), p.expiresAt()).getSeconds());
            countdown.setText(String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
        } else {
            countdown.setText("");
        }
    }

    private Button copyButton(Supplier<String> text) {
        Button b = W.iconButton("copy", t("common.copy"));
        b.setOnAction(e -> {
            W.copy(text.get());
            b.setGraphic(Icons.icon("check", 18, "icon-ok"));
            Timeline back = new Timeline(new KeyFrame(Duration.millis(1300), x -> b.setGraphic(Icons.icon("copy", 18))));
            back.play();
        });
        return b;
    }
}
