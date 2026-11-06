package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.core.AppInfo;
import dev.badr.pocketdrop.core.Config;
import dev.badr.pocketdrop.core.Fmt;
import dev.badr.pocketdrop.core.I18n;
import dev.badr.pocketdrop.core.Json;
import dev.badr.pocketdrop.storage.FileNamer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static dev.badr.pocketdrop.core.I18n.t;

/** All settings. Every change is saved at once. */
final class SettingsPage implements Page {
    private static boolean advancedOpen;

    /** Used by the screenshot tool. */
    static void advancedOpenForTests(boolean open) { advancedOpen = open; }

    private final AppContext ctx;
    private final Dialogs dialogs;
    private final Stage stage;
    private final Consumer<String> onLanguage;
    private final Runnable rebuild;
    private final Consumer<Integer> navigate;
    private final Runnable settingsChanged;
    private final ScrollPane scroll = new ScrollPane();

    SettingsPage(AppContext ctx, Dialogs dialogs, Stage stage, Consumer<String> onLanguage, Runnable rebuild,
                 Consumer<Integer> navigate, Runnable settingsChanged) {
        this.ctx = ctx;
        this.dialogs = dialogs;
        this.stage = stage;
        this.onLanguage = onLanguage;
        this.rebuild = rebuild;
        this.navigate = navigate;
        this.settingsChanged = settingsChanged;
        build();
    }

    @Override public Node view() { return scroll; }

    private Config cfg() { return ctx.cfg(); }

    private void saved() {
        ctx.store.save();
        settingsChanged.run();
    }

    private void build() {
        VBox col = new VBox(16);
        col.setPadding(new Insets(26, 30, 30, 30));
        col.getChildren().addAll(W.label(t("nav.settings"), "h1"), general(), storage(), account(), security(), advanced(), about());
        scroll.setContent(col);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    // ------------------------------------------------------------------ sections

    private VBox section(String title, String icon, Node... rows) {
        HBox head = new HBox(12, W.iconChip(icon, 36, false), W.label(title, "h2"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(14, head);
        for (int i = 0; i < rows.length; i++) {
            v.getChildren().add(W.separator());
            v.getChildren().add(rows[i]);
        }
        v.getStyleClass().add("card");
        return v;
    }

    private Node setting(String title, String desc, Node control) {
        VBox text = new VBox(2, W.label(title, "h3"));
        if (desc != null && !desc.isEmpty()) text.getChildren().add(W.wrap(desc, "muted", "small"));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Switch toggle(boolean value, java.util.function.Consumer<Boolean> apply) {
        Switch s = new Switch(value);
        s.selectedProperty().addListener((o, a, b) -> { apply.accept(b); saved(); });
        return s;
    }

    private TextField number(int value, int min, int max, IntConsumer apply) {
        TextField f = new TextField(String.valueOf(value));
        f.setPrefWidth(110);
        W.ltr(f);
        f.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,7}") ? c : null));
        Runnable commit = () -> {
            int v;
            try { v = Integer.parseInt(f.getText().trim()); } catch (NumberFormatException e) { v = value; }
            v = Math.max(min, Math.min(max, v));
            f.setText(String.valueOf(v));
            apply.accept(v);
            saved();
        };
        f.setOnAction(e -> commit.run());
        f.focusedProperty().addListener((o, a, focused) -> { if (!focused) commit.run(); });
        return f;
    }

    private ComboBox<String> combo(String[] labels, int selected, IntConsumer onPick) {
        ComboBox<String> c = new ComboBox<>();
        c.getItems().addAll(labels);
        c.getSelectionModel().select(selected);
        c.setPrefWidth(210);
        c.setFocusTraversable(false);
        c.setOnAction(e -> {
            int i = c.getSelectionModel().getSelectedIndex();
            if (i >= 0) onPick.accept(i);
        });
        return c;
    }

    // ------------------------------------------------------------------ general

    private Node general() {
        ComboBox<String> lang = combo(new String[]{"العربية", "English"}, "ar".equals(I18n.lang()) ? 0 : 1, i -> {
            String code = i == 0 ? "ar" : "en";
            if (!code.equals(I18n.lang())) onLanguage.accept(code);
        });
        W.ltr(lang);

        Switch auto = new Switch(cfg().startWithWindows);
        boolean canAuto = Autostart.available();
        auto.setDisable(!canAuto);
        auto.selectedProperty().addListener((o, a, b) -> {
            if (!Autostart.set(b)) {
                auto.setSelected(!b);
                dialogs.message("alert", t("set.auto.fail.title"), t("set.auto.fail.text"), true);
                return;
            }
            cfg().startWithWindows = b;
            saved();
        });

        return section(t("set.general"), "settings",
                setting(t("set.lang"), t("set.lang.d"), lang),
                setting(t("set.notif"), t("set.notif.d"), toggle(cfg().notifications, v -> cfg().notifications = v)),
                setting(t("set.tray"), t("set.tray.d"), toggle(cfg().minimizeToTray, v -> cfg().minimizeToTray = v)),
                setting(t("set.auto"), canAuto ? t("set.auto.d") : t("set.auto.na"), auto));
    }

    // ------------------------------------------------------------------ storage

    private Node storage() {
        TextField folder = new TextField(cfg().saveDir);
        folder.setEditable(false);
        folder.setPrefWidth(300);
        W.ltr(folder);
        Button browse = W.button("folder", t("common.browse"), "btn-ghost");
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(t("wiz.folder.pick"));
            File start = new File(cfg().saveDir);
            if (start.isDirectory()) dc.setInitialDirectory(start);
            File chosen = dc.showDialog(stage);
            if (chosen == null) return;
            try {
                Files.createDirectories(chosen.toPath());
            } catch (IOException ex) {
                dialogs.message("alert", t("wiz.folder.err.title"), t("wiz.folder.err.text"), true);
                return;
            }
            cfg().saveDir = chosen.getAbsolutePath();
            folder.setText(cfg().saveDir);
            saved();
        });
        Button open = W.iconButton("open", t("files.openfolder"));
        open.setOnAction(e -> OpenUtil.folder(Path.of(cfg().saveDir)));
        HBox ctl = new HBox(8, folder, browse, open);
        ctl.setAlignment(Pos.CENTER_RIGHT);

        String free = "";
        try {
            Path p = Path.of(cfg().saveDir);
            while (p != null && !Files.exists(p)) p = p.getParent();
            if (p != null) free = Fmt.size(Files.getFileStore(p).getUsableSpace());
        } catch (IOException | RuntimeException ignored) { }

        return section(t("set.storage"), "folder",
                setting(t("set.folder"), t("set.folder.d"), ctl),
                setting(t("set.free"), t("set.free.d"), W.label(free, "h3")));
    }

    // ------------------------------------------------------------------ bot & account

    private Node account() {
        Button token = W.button("lock", t("set.token.change"), "btn-ghost");
        token.setOnAction(e -> dialogs.changeToken(rebuild));
        HBox tokenCtl = new HBox(12, W.label("••••••••••••", "muted", "mono"), token);
        tokenCtl.setAlignment(Pos.CENTER_RIGHT);

        Config c = cfg();
        Node ownerCtl;
        String ownerDesc;
        if (c.hasOwner()) {
            String who = c.ownerName.isBlank() ? "id " + c.ownerId : c.ownerName;
            if (!c.ownerUsername.isBlank()) who += "  @" + c.ownerUsername;
            Button change = W.button("alert", t("set.owner.change"), "btn-danger-soft");
            change.setOnAction(e -> dialogs.changeOwner(rebuild));
            Label name = W.label(who, "h3");
            W.ltr(name);
            ownerCtl = new HBox(12, name, change);
            ownerDesc = t("set.owner.d");
        } else {
            Button link = W.button("link", t("dash.banner.link.btn"), "btn-primary");
            link.setOnAction(e -> dialogs.linkAccount(rebuild));
            ownerCtl = new HBox(12, W.pill(t("set.owner.none"), "pill-warn"), link);
            ownerDesc = t("set.owner.d2");
        }
        ((HBox) ownerCtl).setAlignment(Pos.CENTER_RIGHT);

        Label bot = W.label(c.botUsername.isBlank() ? "-" : "@" + c.botUsername, "h3");
        W.ltr(bot);
        return section(t("set.bot"), "shield",
                setting(t("set.botname"), t("set.botname.d"), bot),
                setting(t("set.token"), t("set.token.d"), tokenCtl),
                setting(t("set.owner"), ownerDesc, ownerCtl));
    }

    // ------------------------------------------------------------------ security

    private Node security() {
        Button log = W.button("list", t("set.sec.openlog"), "btn-ghost");
        log.setOnAction(e -> navigate.accept(2));
        return section(t("set.security"), "lock",
                setting(t("set.sec.strangers"), t("set.sec.strangers.d"), log),
                setting(t("set.sec.blocked"), t("set.sec.blocked.d", cfg().blockedExt.size()),
                        W.pill(cfg().allowDangerous ? t("set.sec.allowed") : t("set.sec.blockedon"),
                                cfg().allowDangerous ? "pill-danger" : "pill-ok")),
                setting(t("set.undo"), t("set.undo.d"), toggle(cfg().undoEnabled, v -> cfg().undoEnabled = v)));
    }

    // ------------------------------------------------------------------ advanced

    private Node advanced() {
        Config c = cfg();
        VBox body = new VBox(14);

        TextField photoName = new TextField(c.photosFolderName);
        photoName.setPrefWidth(160);
        W.ltr(photoName);
        Runnable commitName = () -> {
            c.photosFolderName = FileNamer.sanitize(photoName.getText(), "photos");
            photoName.setText(c.photosFolderName);
            saved();
        };
        photoName.setOnAction(e -> commitName.run());
        photoName.focusedProperty().addListener((o, a, f) -> { if (!f) commitName.run(); });
        HBox photoCtl = new HBox(12, photoName, toggle(c.photosSubfolder, v -> c.photosSubfolder = v));
        photoCtl.setAlignment(Pos.CENTER_RIGHT);

        String[] layouts = {"flat", "type", "date"};
        ComboBox<String> layout = combo(new String[]{t("set.layout.flat"), t("set.layout.type"), t("set.layout.date")},
                java.util.Arrays.asList(layouts).indexOf(c.layout), i -> { c.layout = layouts[i]; saved(); });
        String[] dups = {"underscore", "parens"};
        ComboBox<String> dup = combo(new String[]{"name_2.ext", "name (1).ext"},
                java.util.Arrays.asList(dups).indexOf(c.dupStyle), i -> { c.dupStyle = dups[i]; saved(); });
        W.ltr(dup);

        TextField blocked = new TextField(c.blockedExtText());
        blocked.setPrefWidth(300);
        W.ltr(blocked);
        Runnable commitBlocked = () -> { c.setBlockedExtText(blocked.getText()); blocked.setText(c.blockedExtText()); saved(); };
        blocked.setOnAction(e -> commitBlocked.run());
        blocked.focusedProperty().addListener((o, a, f) -> { if (!f) commitBlocked.run(); });
        Button reset = W.iconButton("refresh", t("set.adv.blocked.reset"));
        reset.setOnAction(e -> {
            c.blockedExt = new java.util.ArrayList<>(Config.DEFAULT_BLOCKED);
            blocked.setText(c.blockedExtText());
            saved();
        });
        HBox blockedCtl = new HBox(8, blocked, reset);
        blockedCtl.setAlignment(Pos.CENTER_RIGHT);

        Switch danger = new Switch(c.allowDangerous);
        danger.danger();
        danger.guard(want -> {
            if (!want) return true;
            dialogs.allowDangerous(() -> { c.allowDangerous = true; ctx.store.save(); danger.setSelected(true); rebuild.run(); },
                    () -> { });
            return false;
        });
        danger.selectedProperty().addListener((o, a, b) -> {
            if (!b) { c.allowDangerous = false; saved(); rebuild.run(); }
        });

        Button export = W.button("upload", t("set.adv.export"), "btn-ghost");
        export.setOnAction(e -> exportSettings());
        Button imp = W.button("download", t("set.adv.import"), "btn-ghost");
        imp.setOnAction(e -> importSettings());
        HBox io = new HBox(8, imp, export);
        io.setAlignment(Pos.CENTER_RIGHT);

        body.getChildren().addAll(
                W.separator(), setting(t("set.adv.photos"), t("set.adv.photos.d"), photoCtl),
                W.separator(), setting(t("set.adv.layout"), t("set.adv.layout.d"), layout),
                W.separator(), setting(t("set.adv.dup"), t("set.adv.dup.d"), dup),
                W.separator(), setting(t("set.adv.maxsize"), t("set.adv.maxsize.d"), number(c.maxFileMb, 1, 20, v -> c.maxFileMb = v)),
                W.separator(), setting(t("set.adv.minfree"), t("set.adv.minfree.d"), number(c.minFreeMb, 0, 1_000_000, v -> c.minFreeMb = v)),
                W.separator(), setting(t("set.adv.blocked"), t("set.adv.blocked.d"), blockedCtl),
                W.separator(), setting(t("set.adv.danger"), t("set.adv.danger.d"), danger),
                W.separator(), setting(t("set.adv.retention"), t("set.adv.retention.d"), number(c.logRetentionDays, 1, 3650, v -> c.logRetentionDays = v)),
                W.separator(), setting(t("set.adv.io"), t("set.adv.io.d"), io));
        body.setManaged(advancedOpen);
        body.setVisible(advancedOpen);

        Node chevron = Icons.icon("chevron", 20, "icon-text");
        chevron.setRotate(advancedOpen ? 90 : 0);
        VBox titles = new VBox(2, W.label(t("set.advanced"), "h2"), W.label(t("set.advanced.d"), "muted", "small"));
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox head = new HBox(12, W.iconChip("settings", 36, false), titles, chevron);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setStyle("-fx-cursor: hand;");
        head.setOnMouseClicked(e -> {
            advancedOpen = !advancedOpen;
            body.setManaged(advancedOpen);
            body.setVisible(advancedOpen);
            chevron.setRotate(advancedOpen ? 90 : 0);
            if (advancedOpen) Anim.fadeIn(body, 180);
        });
        VBox box = new VBox(14, head, body);
        box.getStyleClass().add("card");
        return box;
    }

    private void exportSettings() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("set.adv.export"));
        fc.setInitialFileName("pocketdrop-settings.json");
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            Files.writeString(f.toPath(), Json.pretty(cfg().portableJson()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            dialogs.message("alert", t("log.export.fail"), ex.getMessage(), true);
        }
    }

    private void importSettings() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("set.adv.import"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            Config other = Config.fromJson(Json.obj(Json.parse(Files.readString(f.toPath(), StandardCharsets.UTF_8))));
            cfg().applyPortable(other);
            ctx.store.save();
            settingsChanged.run();
            rebuild.run();
            dialogs.message("check", t("set.adv.imported.title"), t("set.adv.imported.text"), false);
        } catch (IOException | RuntimeException ex) {
            dialogs.message("alert", t("set.adv.import.fail"), t("set.adv.import.fail.d"), true);
        }
    }

    // ------------------------------------------------------------------ about

    private Node about() {
        Label name = W.label(AppInfo.NAME + "  " + AppInfo.VERSION, "h2");
        Label by = W.label(t("set.about.by", AppInfo.AUTHOR), "muted");
        Label net = W.wrap(t("set.about.net"), "muted", "small");
        Region gap = new Region();
        gap.setMinHeight(2);
        VBox v = new VBox(6, name, by, gap, net);
        v.getStyleClass().add("card");
        return v;
    }
}
