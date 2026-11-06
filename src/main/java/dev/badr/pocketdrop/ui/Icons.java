package dev.badr.pocketdrop.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/** Simple outline icons drawn from path data on a 24 x 24 grid (no image files needed). */
public final class Icons {
    private Icons() {}

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("home", "M4 11 L12 4 L20 11 V20 H14 V14 H10 V20 H4 Z"),
            Map.entry("folder", "M3 6 H9 L11 8 H21 V19 H3 Z"),
            Map.entry("file", "M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19"),
            Map.entry("image", "M4 5 H20 V19 H4 Z M4 16 L9 11 L13 15 L16 12 L20 16 M8.3 8.5 a1.2 1.2 0 1 0 2.4 0 a1.2 1.2 0 1 0 -2.4 0"),
            Map.entry("video", "M3 6 H15 V18 H3 Z M15 10 L21 6 V18 L15 14"),
            Map.entry("music", "M9 18 V5 L20 3 V16 M9 18 a3 3 0 1 1 -6 0 a3 3 0 0 1 6 0 M20 16 a3 3 0 1 1 -6 0 a3 3 0 0 1 6 0"),
            Map.entry("mic", "M12 3 a3 3 0 0 1 3 3 V12 a3 3 0 0 1 -6 0 V6 a3 3 0 0 1 3 -3 M6 11 a6 6 0 0 0 12 0 M12 17 V21"),
            Map.entry("list", "M8 6 H21 M8 12 H21 M8 18 H21 M3 6 H3.01 M3 12 H3.01 M3 18 H3.01"),
            Map.entry("settings", "M4 7 H20 M4 17 H20 M15 4 V10 M9 14 V20"),
            Map.entry("shield", "M12 3 L19 6 V12 C19 16.5 16 19.5 12 21 C8 19.5 5 16.5 5 12 V6 Z M9 12 L11 14 L15 10"),
            Map.entry("play", "M7 4 L19 12 L7 20 Z"),
            Map.entry("pause", "M8 5 V19 M16 5 V19"),
            Map.entry("stop", "M6 6 H18 V18 H6 Z"),
            Map.entry("copy", "M9 9 H20 V20 H9 Z M5 15 V4 H16"),
            Map.entry("eye", "M2 12 C5 6 19 6 22 12 C19 18 5 18 2 12 Z M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0"),
            Map.entry("check", "M5 12 L10 17 L19 7"),
            Map.entry("x", "M6 6 L18 18 M18 6 L6 18"),
            Map.entry("minus", "M6 12 H18"),
            Map.entry("alert", "M12 3 L22 20 H2 Z M12 10 V14 M12 17 H12.01"),
            Map.entry("link", "M10 14 a4 4 0 0 0 5.7 0 l3 -3 a4 4 0 0 0 -5.7 -5.7 l-1 1 M14 10 a4 4 0 0 0 -5.7 0 l-3 3 a4 4 0 0 0 5.7 5.7 l1 -1"),
            Map.entry("drop", "M12 3 V15 M7 10 L12 15 L17 10 M4 19 H20"),
            Map.entry("open", "M14 4 H20 V10 M20 4 L11 13 M18 14 V19 H5 V6 H10"),
            Map.entry("lock", "M6 11 H18 V20 H6 Z M8 11 V8 a4 4 0 0 1 8 0 V11"),
            Map.entry("user", "M8 8 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0 M4 21 a8 8 0 0 1 16 0"),
            Map.entry("globe", "M3 12 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0 M3 12 H21 M12 3 C8 8 8 16 12 21 M12 3 C16 8 16 16 12 21"),
            Map.entry("bell", "M6 17 V11 a6 6 0 0 1 12 0 V17 L20 19 H4 Z M10 21 H14"),
            Map.entry("chevron", "M9 6 L15 12 L9 18"),
            Map.entry("search", "M4 11 a7 7 0 1 0 14 0 a7 7 0 1 0 -14 0 M16 16 L21 21"),
            Map.entry("download", "M12 4 V15 M7 10 L12 15 L17 10 M5 20 H19"),
            Map.entry("upload", "M12 15 V4 M7 9 L12 4 L17 9 M5 20 H19"),
            Map.entry("phone", "M8 3 H16 a1 1 0 0 1 1 1 V20 a1 1 0 0 1 -1 1 H8 a1 1 0 0 1 -1 -1 V4 a1 1 0 0 1 1 -1 M11 18 H13"),
            Map.entry("computer", "M3 5 H21 V16 H3 Z M8 20 H16 M12 16 V20"),
            Map.entry("refresh", "M20 12 a8 8 0 1 1 -2.5 -5.8 M20 4 V9 H15")
    );

    public static Node icon(String name, double size, String... extraClasses) {
        SVGPath p = new SVGPath();
        p.setContent(PATHS.getOrDefault(name, PATHS.get("file")));
        p.getStyleClass().add("icon");
        for (String c : extraClasses) p.getStyleClass().add(c);

        Pane inner = new Pane(p);
        inner.setPrefSize(24, 24);
        inner.setMinSize(24, 24);
        inner.setMaxSize(24, 24);
        double s = size / 24.0;
        inner.setScaleX(s);
        inner.setScaleY(s);
        // stroke width is scaled too, so keep it readable at small sizes
        if (size < 20) p.setStrokeWidth(2.0);

        StackPane box = new StackPane(new Group(inner));
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    public static String forKind(dev.badr.pocketdrop.storage.Kind k) {
        return switch (k) {
            case PHOTO -> "image";
            case VIDEO -> "video";
            case AUDIO -> "music";
            case VOICE -> "mic";
            default -> "file";
        };
    }
}
