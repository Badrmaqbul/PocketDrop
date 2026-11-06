package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotListener;
import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.core.AppInfo;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.badr.pocketdrop.core.I18n.t;

/** Sidebar and the four pages. */
final class Shell extends HBox {
    private final AppContext ctx;
    private final List<Page> pages = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();
    private final StackPane host = new StackPane();
    private final Circle stateDot = new Circle(5);
    private final Label stateText = W.label("", "muted", "small");
    private int current = -1;
    private final BotListener listener = new BotListener() {
        @Override public void onState(BotService.State s, String d) { Platform.runLater(Shell.this::refreshState); }
        @Override public void onPausedChanged(boolean p) { Platform.runLater(Shell.this::refreshState); }
    };

    Shell(AppContext ctx, Dialogs dialogs, Stage stage, Consumer<String> onLanguage, Runnable rebuild,
          Runnable settingsChanged, int initialPage) {
        this.ctx = ctx;
        Consumer<Integer> navigate = this::show;
        pages.add(new DashboardPage(ctx, dialogs, navigate));
        pages.add(new FilesPage(ctx, dialogs));
        pages.add(new LogPage(stage, dialogs));
        pages.add(new SettingsPage(ctx, dialogs, stage, onLanguage, rebuild, navigate, settingsChanged));

        getChildren().addAll(sidebar(), host);
        HBox.setHgrow(host, Priority.ALWAYS);
        ctx.bot.addListener(listener);
        refreshState();
        show(initialPage);
    }

    private Node sidebar() {
        HBox brand = new HBox(10, Brand.mark(34), W.label(AppInfo.NAME, "brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new javafx.geometry.Insets(2, 8, 18, 8));

        VBox nav = new VBox(6);
        String[][] items = {{"home", "nav.dashboard"}, {"folder", "nav.files"}, {"list", "nav.log"}, {"settings", "nav.settings"}};
        for (int i = 0; i < items.length; i++) {
            Button b = new Button(t(items[i][1]));
            b.getStyleClass().add("nav-btn");
            b.setGraphic(Icons.icon(items[i][0], 20));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setFocusTraversable(false);
            final int idx = i;
            b.setOnAction(e -> show(idx));
            navButtons.add(b);
            nav.getChildren().add(b);
        }

        HBox status = new HBox(10, stateDot, stateText);
        status.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("side-status");

        VBox side = new VBox(brand, nav, W.spacer(), status);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(236);
        side.setMinWidth(236);
        side.setMaxWidth(236);
        return side;
    }

    void show(int index) {
        if (index == current) { pages.get(index).onShow(); return; }
        current = index;
        for (int i = 0; i < navButtons.size(); i++) W.setSelected(navButtons.get(i), i == index);
        Node v = pages.get(index).view();
        host.getChildren().setAll(v);
        pages.get(index).onShow();
        Anim.fadeIn(v, 170);
    }

    int currentIndex() { return current; }

    void refreshState() {
        BotService.State s = ctx.bot.state();
        String color;
        String text;
        switch (s) {
            case RUNNING -> { color = ctx.cfg().paused ? "-pd-warn" : "-pd-ok"; text = ctx.cfg().paused ? t("state.paused") : t("state.running"); }
            case STARTING -> { color = "-pd-warn"; text = t("state.starting"); }
            case RECONNECTING -> { color = "-pd-warn"; text = t("state.reconnecting"); }
            case ERROR -> { color = "-pd-danger"; text = t("state.error"); }
            default -> { color = "-pd-muted"; text = t("state.stopped"); }
        }
        stateDot.setStyle("-fx-fill: " + color + ";");
        stateText.setText(text);
    }

    void dispose() {
        ctx.bot.removeListener(listener);
        for (Page p : pages) p.dispose();
    }
}
