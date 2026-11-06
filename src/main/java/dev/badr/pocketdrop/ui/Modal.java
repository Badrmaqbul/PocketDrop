package dev.badr.pocketdrop.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** A dim layer with one dialog card on top. Lives inside the window; only one dialog at a time. */
public final class Modal extends StackPane {
    private Runnable onClose;

    public Modal() {
        getStyleClass().add("modal-dim");
        setAlignment(Pos.CENTER);
        setVisible(false);
        setPickOnBounds(true);
    }

    public boolean isOpen() { return isVisible(); }

    public void show(Node card, Runnable onClose) {
        this.onClose = onClose;
        getChildren().setAll(card);
        StackPane.setAlignment(card, Pos.CENTER);
        setVisible(true);
        Anim.fadeIn(this, 160);
        Anim.popIn(card);
    }

    public void close() {
        if (!isVisible()) return;
        setVisible(false);
        getChildren().clear();
        Runnable r = onClose;
        onClose = null;
        if (r != null) r.run();
    }
}
