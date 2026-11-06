package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.storage.SavedItem;

/** Events from the bot. Called from background threads: the UI must switch to its own thread. */
public interface BotListener {
    default void onState(BotService.State state, String detail) {}
    default void onSaved(SavedItem item) {}
    default void onPairingChanged() {}
    default void onOwnerChanged() {}
    default void onPausedChanged(boolean paused) {}
    default void onIntrusion(long senderId) {}
}
