package dev.badr.pocketdrop.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/** The few animations PocketDrop uses. Motion is used to answer an action or to draw attention, not as decoration. */
public final class Anim {
    private Anim() {}

    public static void fadeIn(Node n, int ms) {
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        f.setInterpolator(Interpolator.EASE_OUT);
        f.play();
    }

    public static void slideIn(Node n, double fromX, int ms) {
        n.setOpacity(0);
        n.setTranslateX(fromX);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(ms), n);
        t.setToX(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(f, t).play();
    }

    public static void dropIn(Node n) {
        n.setOpacity(0);
        n.setTranslateY(-16);
        FadeTransition f = new FadeTransition(Duration.millis(320), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(320), n);
        t.setToY(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(f, t).play();
    }

    public static void popIn(Node n) {
        n.setOpacity(0);
        n.setScaleX(0.92);
        n.setScaleY(0.92);
        FadeTransition f = new FadeTransition(Duration.millis(220), n);
        f.setToValue(1);
        ScaleTransition s = new ScaleTransition(Duration.millis(240), n);
        s.setToX(1);
        s.setToY(1);
        s.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(f, s).play();
    }

    /** An expanding, fading ring (used while waiting for something). Call stop() on the result when done. */
    public static Animation pulse(Node ring) {
        ring.setOpacity(0.55);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ring.scaleXProperty(), 1.0), new KeyValue(ring.scaleYProperty(), 1.0),
                        new KeyValue(ring.opacityProperty(), 0.55)),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(ring.scaleXProperty(), 1.75, Interpolator.EASE_OUT),
                        new KeyValue(ring.scaleYProperty(), 1.75, Interpolator.EASE_OUT),
                        new KeyValue(ring.opacityProperty(), 0.0, Interpolator.EASE_OUT)));
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
        return t;
    }

    /** Brief highlight of a row that just arrived. */
    public static void flash(Region glow) {
        glow.setOpacity(1);
        FadeTransition f = new FadeTransition(Duration.millis(1500), glow);
        f.setToValue(0);
        f.setInterpolator(Interpolator.EASE_IN);
        f.play();
    }

    public static void shake(Node n) {
        TranslateTransition a = new TranslateTransition(Duration.millis(60), n);
        a.setByX(7);
        a.setCycleCount(6);
        a.setAutoReverse(true);
        SequentialTransition s = new SequentialTransition(a);
        s.setOnFinished(e -> n.setTranslateX(0));
        s.play();
    }
}
