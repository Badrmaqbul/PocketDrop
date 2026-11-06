package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.core.AppDirs;
import dev.badr.pocketdrop.core.Fmt;
import dev.badr.pocketdrop.core.Log;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.badr.pocketdrop.core.I18n.t;

/** Activity and security log (messages from strangers are listed here). Tokens are masked. */
final class LogPage implements Page {
    private final Stage stage;
    private final Dialogs dialogs;
    private final VBox root = new VBox(14);
    private final ListView<Log.Entry> list = new ListView<>();
    private final HBox chips = new HBox(8);
    private String filter = "all";
    private final Consumer<Log.Entry> listener = e -> Platform.runLater(this::reload);

    LogPage(Stage stage, Dialogs dialogs) {
        this.stage = stage;
        this.dialogs = dialogs;
        build();
        Log.addListener(listener);
        reload();
    }

    @Override public Node view() { return root; }
    @Override public void onShow() { reload(); }
    @Override public void dispose() { Log.removeListener(listener); }

    private void build() {
        root.setPadding(new Insets(26, 30, 22, 30));
        Button export = W.button("download", t("log.export"), "btn-ghost");
        export.setOnAction(e -> export());
        Button folder = W.button("folder", t("log.folder"), "btn-ghost");
        folder.setOnAction(e -> OpenUtil.folder(AppDirs.logs()));
        HBox head = new HBox(10, W.label(t("nav.log"), "h1"), W.spacer(), folder, export);
        head.setAlignment(Pos.CENTER_LEFT);

        addChip("all", t("log.all"));
        addChip("security", t("log.security"));
        addChip("problems", t("log.problems"));
        addChip("info", t("log.info"));

        Label hint = W.wrap(t("log.hint"), "muted", "small");
        VBox empty = new VBox(10, Icons.icon("shield", 34), W.label(t("log.empty"), "muted"));
        empty.setAlignment(Pos.CENTER);
        list.setPlaceholder(empty);
        list.setFocusTraversable(false);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Log.Entry e, boolean emptyCell) {
                super.updateItem(e, emptyCell);
                if (emptyCell || e == null) { setGraphic(null); setText(null); return; }
                setText(null);
                setGraphic(rowFor(e));
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().addAll(head, chips, hint, list);
        select("all");
    }

    private void addChip(String id, String text) {
        Button b = W.button(text, "chip");
        b.setUserData(id);
        b.setOnAction(e -> { filter = id; select(id); reload(); });
        chips.getChildren().add(b);
    }

    private void select(String id) {
        for (Node n : chips.getChildren()) W.setSelected(n, id.equals(n.getUserData()));
    }

    private void reload() {
        List<Log.Entry> all = new ArrayList<>();
        for (Log.Entry e : Log.recent()) {
            boolean keep = switch (filter) {
                case "security" -> e.level() == Log.Level.SECURITY;
                case "problems" -> e.level() == Log.Level.WARN || e.level() == Log.Level.ERROR;
                case "info" -> e.level() == Log.Level.INFO;
                default -> e.level() != Log.Level.DEBUG;
            };
            if (keep) all.add(e);
        }
        java.util.Collections.reverse(all);
        list.setItems(FXCollections.observableArrayList(all));
    }

    private Node rowFor(Log.Entry e) {
        String color = switch (e.level()) {
            case SECURITY -> "-pd-warn";
            case ERROR -> "-pd-danger";
            case WARN -> "-pd-warn";
            default -> "-pd-accent";
        };
        Circle dot = new Circle(4);
        dot.setStyle("-fx-fill: " + color + ";");
        Label time = W.label(Fmt.full(e.time()), "muted", "small", "mono");
        W.ltr(time);
        time.setMinWidth(Region.USE_PREF_SIZE);
        Label msg = W.wrap(e.message(), "");
        msg.maxWidthProperty().bind(list.widthProperty().subtract(260));
        Label level = W.pill(e.level().name(), e.level() == Log.Level.SECURITY ? "pill-warn"
                : e.level() == Log.Level.ERROR ? "pill-danger" : "pill-muted");
        level.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(12, dot, time, level, msg);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("row");
        return h;
    }

    private void export() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("log.export"));
        fc.setInitialFileName("pocketdrop-log.txt");
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            Log.export(f.toPath());
        } catch (IOException ex) {
            dialogs.message("alert", t("log.export.fail"), ex.getMessage(), true);
        }
    }
}
