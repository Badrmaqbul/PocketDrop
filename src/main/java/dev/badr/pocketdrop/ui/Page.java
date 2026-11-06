package dev.badr.pocketdrop.ui;

import javafx.scene.Node;

/** One screen of the main window. */
interface Page {
    Node view();

    /** Called every time the page becomes visible. */
    default void onShow() {}

    /** Called when the window content is rebuilt or closed. */
    default void dispose() {}
}
