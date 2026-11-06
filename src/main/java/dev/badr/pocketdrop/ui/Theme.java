package dev.badr.pocketdrop.ui;

import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.io.InputStream;

/** Loads the bundled Tajawal font (OFL license) and the stylesheet. */
public final class Theme {
    private Theme() {}

    private static boolean fontsLoaded;

    public static void apply(Scene scene) {
        loadFonts();
        var css = Theme.class.getResource("/ui/theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }

    private static synchronized void loadFonts() {
        if (fontsLoaded) return;
        fontsLoaded = true;
        for (String f : new String[]{"Tajawal-Regular.ttf", "Tajawal-Bold.ttf"}) {
            try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + f)) {
                if (in != null) Font.loadFont(in, 14);
            } catch (java.io.IOException ignored) { }
        }
    }
}
