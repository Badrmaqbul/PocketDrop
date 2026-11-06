package dev.badr.pocketdrop.ui;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.function.Predicate;

/** An on/off switch. An optional guard can veto turning it on (used for the dangerous-files switch). */
public final class Switch extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Region thumb = new Region();
    private Predicate<Boolean> guard;

    public Switch(boolean initial) {
        getStyleClass().add("switch");
        setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        Region track = new Region();
        track.getStyleClass().add("switch-track");
        thumb.getStyleClass().add("switch-thumb");
        thumb.setMouseTransparent(true);
        track.setMouseTransparent(true);
        getChildren().addAll(track, thumb);
        setAlignment(Pos.CENTER_LEFT);
        setPrefSize(46, 26);
        setMinSize(46, 26);
        setMaxSize(46, 26);
        StackPane.setMargin(thumb, new javafx.geometry.Insets(0, 0, 0, 3));

        selected.addListener((o, a, b) -> render(true));
        selected.set(initial);
        render(false);
        setOnMouseClicked(e -> toggle());
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }

    public void setSelected(boolean v) { selected.set(v); }

    /** Called with the wanted new value; return false to refuse the change. */
    public Switch guard(Predicate<Boolean> g) { this.guard = g; return this; }

    public Switch danger() { getStyleClass().add("switch-danger"); return this; }

    private void toggle() {
        boolean next = !selected.get();
        if (guard != null && !guard.test(next)) return;
        selected.set(next);
    }

    private void render(boolean animate) {
        pseudoClassStateChanged(SELECTED, selected.get());
        double x = selected.get() ? 20 : 0;
        if (animate) {
            TranslateTransition t = new TranslateTransition(Duration.millis(140), thumb);
            t.setToX(x);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.play();
        } else {
            thumb.setTranslateX(x);
        }
    }
}
