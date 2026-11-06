package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotListener;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.Fmt;
import dev.badr.pocketdrop.storage.FileNamer;
import dev.badr.pocketdrop.storage.Kind;
import dev.badr.pocketdrop.storage.SavedItem;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.badr.pocketdrop.core.I18n.t;

/** Read-only list of everything the bot saved. The app never edits, moves or deletes these files. */
final class FilesPage implements Page {
    private final AppContext ctx;
    private final Dialogs dialogs;
    private final VBox root = new VBox(14);
    private final ListView<SavedItem> list = new ListView<>();
    private final HBox chips = new HBox(8);
    private final Label count = W.pill("0", "pill-accent");
    private final TextField search = new TextField();
    private Kind filter;      // null = all
    private final Map<String, Image> thumbs = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 300; }
    };
    private final BotListener listener = new BotListener() {
        @Override public void onSaved(SavedItem item) { Platform.runLater(FilesPage.this::reload); }
    };

    FilesPage(AppContext ctx, Dialogs dialogs) {
        this.ctx = ctx;
        this.dialogs = dialogs;
        build();
        ctx.bot.addListener(listener);
        reload();
    }

    @Override public Node view() { return root; }
    @Override public void onShow() { reload(); }
    @Override public void dispose() { ctx.bot.removeListener(listener); }

    private void build() {
        root.setPadding(new Insets(26, 30, 22, 30));
        Button open = W.button("folder", t("files.openfolder"), "btn-ghost");
        open.setOnAction(e -> {
            String dir = ctx.cfg().saveDir;
            if (dir != null && !dir.isBlank()) OpenUtil.folder(Path.of(dir));
        });
        search.setPrefWidth(210);
        HBox head = new HBox(12, W.label(t("nav.files"), "h1"), count, W.spacer(), search, open);
        head.setAlignment(Pos.CENTER_LEFT);

        addChip(null, t("files.all"));
        for (Kind k : Kind.values()) addChip(k, t("kind." + k.id()));
        search.setPromptText(t("files.search"));
        search.setFocusTraversable(false);
        search.textProperty().addListener((o, a, b) -> reload());
        chips.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox bar = new HBox(8, chips);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox empty = new VBox(10, Icons.icon("folder", 34), W.label(t("files.empty"), "muted"));
        empty.setAlignment(Pos.CENTER);
        list.setPlaceholder(empty);
        list.setFocusTraversable(false);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SavedItem it, boolean emptyCell) {
                super.updateItem(it, emptyCell);
                if (emptyCell || it == null) { setGraphic(null); setText(null); return; }
                setText(null);
                setGraphic(rowFor(it));
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().addAll(head, bar, list);
        selectChip(null);
    }

    private void addChip(Kind kind, String text) {
        Button b = W.button(text, "chip");
        b.setUserData(kind);
        b.setOnAction(e -> { filter = kind; selectChip(kind); reload(); });
        chips.getChildren().add(b);
    }

    private void selectChip(Kind kind) {
        for (Node n : chips.getChildren()) W.setSelected(n, n.getUserData() == kind);
    }

    private void reload() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<SavedItem> all = ctx.history.newestFirst().stream()
                .filter(i -> filter == null || i.kind() == filter)
                .filter(i -> q.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(q))
                .limit(1500).collect(Collectors.toList());
        list.setItems(FXCollections.observableArrayList(all));
        count.setText(String.valueOf(all.size()));
    }

    private Node rowFor(SavedItem it) {
        Path p = Path.of(it.path());
        boolean exists = Files.exists(p);

        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("thumb");
        thumb.setMinSize(46, 46);
        thumb.setPrefSize(46, 46);
        thumb.setMaxSize(46, 46);
        if (it.kind() == Kind.PHOTO && exists) {
            Image img = thumbs.computeIfAbsent(it.path(), k -> new Image(p.toUri().toString(), 92, 92, true, true, true));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(46);
            iv.setFitHeight(46);
            iv.setPreserveRatio(true);
            Rectangle clip = new Rectangle(46, 46);
            clip.setArcWidth(16);
            clip.setArcHeight(16);
            thumb.setClip(clip);
            thumb.getChildren().add(iv);
        } else {
            thumb.getChildren().add(Icons.icon(Icons.forKind(it.kind()), 22, "icon-accent"));
        }

        Label name = W.fileName(it.name(), "h3");
        String meta = Fmt.size(it.size()) + "  ·  " + Fmt.full(it.time()).substring(0, 16);
        VBox text = new VBox(1, name, W.label(meta, "muted", "small"));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox h = new HBox(12, thumb, text);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("row");
        if (!exists) {
            h.getChildren().add(W.pill(t("files.missing"), "pill-muted"));
            name.setOpacity(0.5);
        } else {
            Button open = W.iconButton("open", t("files.open"));
            open.setOnAction(e -> openFile(it, p));
            Button reveal = W.iconButton("folder", t("files.reveal"));
            reveal.setOnAction(e -> OpenUtil.reveal(p));
            h.getChildren().addAll(open, reveal);
        }
        return h;
    }

    private void openFile(SavedItem it, Path p) {
        Config cfg = ctx.cfg();
        if (FileNamer.isDangerous(it.name(), cfg.blockedExt)) {
            dialogs.confirm("alert", t("files.danger.title"), t("files.danger.text"), t("files.danger.yes"), true,
                    () -> OpenUtil.file(p));
        } else {
            OpenUtil.file(p);
        }
    }
}
