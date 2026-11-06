package dev.badr.pocketdrop.ui;

import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Small factory methods so screens stay short. */
public final class W {
    private W() {}

    public static Label label(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    public static Label wrap(String text, String... classes) {
        Label l = label(text, classes);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Button button(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        b.setFocusTraversable(false);
        return b;
    }

    public static Button button(String iconName, String text, String styleClass) {
        Button b = button(text, styleClass);
        String iconClass = styleClass.equals("btn-ghost") ? "icon-text" : "icon-white";
        if (styleClass.equals("btn-danger-soft")) iconClass = "icon-danger";
        b.setGraphic(Icons.icon(iconName, 18, iconClass));
        return b;
    }

    public static Button iconButton(String iconName, String tooltip) {
        Button b = new Button();
        b.getStyleClass().add("btn-icon");
        b.setGraphic(Icons.icon(iconName, 18));
        b.setFocusTraversable(false);
        if (tooltip != null) b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return b;
    }

    public static Label pill(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("pill", styleClass);
        return l;
    }

    public static VBox card(Node... children) {
        VBox v = new VBox(12, children);
        v.getStyleClass().add("card");
        return v;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region separator() {
        Region r = new Region();
        r.getStyleClass().add("sep");
        return r;
    }

    /** Icon in a soft rounded square. */
    public static Node iconChip(String icon, double box, boolean danger) {
        javafx.scene.layout.StackPane p = new javafx.scene.layout.StackPane(Icons.icon(icon, box * 0.5, danger ? "icon-danger" : "icon-accent"));
        p.getStyleClass().add(danger ? "icon-chip-danger" : "icon-chip");
        p.setMinSize(box, box);
        p.setPrefSize(box, box);
        p.setMaxSize(box, box);
        return p;
    }

    public static HBox row(double gap, Node... children) {
        HBox h = new HBox(gap, children);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Keep tokens, paths and codes left-to-right even inside Arabic screens. */
    public static <T extends Node> T ltr(T n) {
        n.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return n;
    }

    /** A file name: always left-to-right text, but placed at the start edge in Arabic screens. */
    public static Label fileName(String name, String... classes) {
        Label l = label(name, classes);
        ltr(l);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(dev.badr.pocketdrop.core.I18n.rtl() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        l.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        return l;
    }

    public static void copy(String text) {
        ClipboardContent c = new ClipboardContent();
        c.putString(text);
        Clipboard.getSystemClipboard().setContent(c);
    }

    public static void setSelected(Node n, boolean selected) {
        if (selected) { if (!n.getStyleClass().contains("selected")) n.getStyleClass().add("selected"); }
        else n.getStyleClass().remove("selected");
    }
}
