package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.core.Qr;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/** Draws a QR code on a white tile (the quiet zone is part of the tile). */
public final class QrView extends StackPane {
    public QrView(String text, double pixels) {
        getStyleClass().add("qr-frame");
        Qr qr = Qr.encode(text, false);
        int n = qr.size();
        double cell = Math.floor(pixels / n);
        double side = cell * n;
        Canvas c = new Canvas(side, side);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, side, side);
        g.setFill(Color.web("#0F0E17"));
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (qr.get(x, y)) g.fillRect(x * cell, y * cell, cell, cell);
            }
        }
        getChildren().add(c);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
    }
}
