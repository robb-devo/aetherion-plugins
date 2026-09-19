package de.aetherion.beta;

import de.aetherion.beta.command.BetaCommand;
import de.aetherion.beta.command.ChecklistCommand;
import de.aetherion.beta.data.BetaStore;
import de.aetherion.beta.item.BetaBook;
import de.aetherion.beta.listener.MenuListener;
import de.aetherion.beta.listener.SessionListener;
import de.aetherion.beta.npc.BetaNpcService;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionBeta extends JavaPlugin {

    private BetaStore store;
    private BetaBook book;
    private BetaNpcService npcs;
    private NamespacedKey npcKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        npcKey = new NamespacedKey(this, "beta_guide");
        store = new BetaStore(this);
        book = new BetaBook(this);
        npcs = new BetaNpcService(this, store, book);

        BetaCommand betaCommand = new BetaCommand(this);
        if (getCommand("beta") != null) {
            getCommand("beta").setExecutor(betaCommand);
            getCommand("beta").setTabCompleter(betaCommand);
        }
        if (getCommand("checklist") != null) {
            getCommand("checklist").setExecutor(new ChecklistCommand(this));
        }

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);

        getServer().getScheduler().runTaskTimer(this, store::saveDirty, 20L * 60, 20L * 60);
        getServer().getScheduler().runTaskLater(this, npcs::respawnAll, 40L);

        getLogger().info("AetherionBeta enabled — /checklist for players, /beta for analysis.");
    }

    @Override
    public void onDisable() {
        if (npcs != null) {
            npcs.despawnAll();
        }
        if (store != null) {
            store.saveAll();
        }
    }

    public BetaStore store() {
        return store;
    }

    public BetaBook book() {
        return book;
    }

    public BetaNpcService npcs() {
        return npcs;
    }

    public NamespacedKey npcKey() {
        return npcKey;
    }
}
