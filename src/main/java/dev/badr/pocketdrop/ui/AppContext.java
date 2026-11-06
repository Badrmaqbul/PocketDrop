package dev.badr.pocketdrop.ui;

import dev.badr.pocketdrop.bot.BotService;
import dev.badr.pocketdrop.core.AppDirs;
import dev.badr.pocketdrop.core.ConfigStore;
import dev.badr.pocketdrop.core.SecretProtector;
import dev.badr.pocketdrop.storage.History;

/** The pieces every screen needs. */
public final class AppContext {
    public final ConfigStore store;
    public final History history;
    public final BotService bot;
    public final SecretProtector protector;

    public AppContext(SecretProtector protector) {
        this.protector = protector;
        this.store = new ConfigStore(AppDirs.config(), protector);
        this.history = new History(AppDirs.history());
        this.bot = new BotService(store, history);
    }

    public dev.badr.pocketdrop.core.Config cfg() { return store.get(); }
}
