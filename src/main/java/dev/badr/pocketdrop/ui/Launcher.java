package dev.badr.pocketdrop.ui;

import javafx.application.Application;

/** Entry point (a plain class, so the app also starts from a normal jar). */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false"); // needed for the tray icon
        Application.launch(PocketDropApp.class, args);
    }
}
