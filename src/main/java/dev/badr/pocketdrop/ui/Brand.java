package dev.badr.pocketdrop.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** The PocketDrop mark (a drop arrow in a violet rounded square), drawn in code so no image file is needed. */
public final class Brand {
    private Brand() {}

    /** The mark as a JavaFX node of the given size. */
    public static Node mark(double size) {
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.30);
        bg.setArcHeight(size * 0.30);
        bg.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#9B82FF")), new Stop(1, Color.web("#6A45F0"))));
        SVGPath arrow = new SVGPath();
        arrow.setContent("M12 3 V15 M7 10 L12 15 L17 10 M4 19 H20");
        arrow.setFill(Color.TRANSPARENT);
        arrow.setStroke(Color.WHITE);
        arrow.setStrokeWidth(2.4);
        arrow.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        arrow.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        double s = size * 0.58 / 24.0;
        javafx.scene.layout.Pane inner = new javafx.scene.layout.Pane(arrow);
        inner.setPrefSize(24, 24);
        inner.setMinSize(24, 24);
        inner.setMaxSize(24, 24);
        inner.setScaleX(s);
        inner.setScaleY(s);
        StackPane p = new StackPane(bg, new Group(inner));
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        return p;
    }

    /** Same mark as an AWT image (tray icon, window icon, installer icon). */
    public static BufferedImage image(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x9B82FF), px, px, new java.awt.Color(0x6A45F0)));
        g.fill(new RoundRectangle2D.Double(0, 0, px, px, px * 0.30, px * 0.30));
        double s = px * 0.58 / 24.0;
        double off = (px - 24 * s) / 2.0;
        g.translate(off, off);
        g.scale(s, s);
        g.setColor(java.awt.Color.WHITE);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(12, 3); p.lineTo(12, 15);
        p.moveTo(7, 10); p.lineTo(12, 15); p.lineTo(17, 10);
        p.moveTo(4, 19); p.lineTo(20, 19);
        g.draw(p);
        g.dispose();
        return img;
    }

    public static Image fxImage(int px) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image(px), "png", out);
            return new Image(new ByteArrayInputStream(out.toByteArray()));
        } catch (IOException | LinkageError e) {
            return null; // no AWT available: the window simply has no icon
        }
    }
}
