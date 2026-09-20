package de.aetherion.hub;

import de.aetherion.hub.command.AetherPasteCommand;
import de.aetherion.hub.command.HubAdminCommand;
import de.aetherion.hub.command.SpawnCommand;
import de.aetherion.hub.command.SpawnGotoCommand;
import de.aetherion.hub.data.PlayerHubStorage;
import de.aetherion.hub.item.HomesteadMarker;
import de.aetherion.hub.listener.HomesteadListener;
import de.aetherion.hub.listener.HubListener;
import de.aetherion.hub.listener.SpawnDiscoverListener;
import de.aetherion.hub.menu.SpawnMenu;
import de.aetherion.hub.pad.IslandLaunchPads;
import de.aetherion.hub.service.HubService;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionHub extends JavaPlugin {

    private static AetherionHub instance;

    private HubService hub;
    private SpawnMenu menu;
    private HomesteadMarker homesteadMarker;
    private HubAdminCommand adminCommand;
    private IslandLaunchPads launchPads;
    private de.aetherion.core.api.HubAccess hubAccess;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        hub = new HubService(this, new PlayerHubStorage(this, getDataFolder()));
        hub.ensureDefaultSpawns();
        hub.repairSpawnLayout();
        menu = new SpawnMenu(hub);
        homesteadMarker = new HomesteadMarker(this, hub);
        adminCommand = new HubAdminCommand(this, hub, homesteadMarker);
        SpawnCommand spawnCommand = new SpawnCommand(hub, menu);
        SpawnGotoCommand gotoCommand = new SpawnGotoCommand(hub);
        launchPads = new IslandLaunchPads(this);

        bind("spawn", spawnCommand);
        bind("spawns", spawnCommand);
        for (String label : SpawnGotoCommand.COMMAND_TO_SPAWN.keySet()) {
            // Alias-only labels — plugin.yml aliases bind them to the primary command.
            if ("ore_ridge".equals(label) || "amethystmines".equals(label)) {
                continue;
            }
            bind(label, gotoCommand);
        }

        PluginCommand hubAdmin = getCommand("hubadmin");
        if (hubAdmin != null) {
            hubAdmin.setExecutor(adminCommand);
            hubAdmin.setTabCompleter(adminCommand);
        }

        PluginCommand aetherPaste = getCommand("aetherpaste");
        if (aetherPaste != null) {
            AetherPasteCommand pasteCommand = new AetherPasteCommand(this);
            aetherPaste.setExecutor(pasteCommand);
            aetherPaste.setTabCompleter(pasteCommand);
        }

        getServer().getPluginManager().registerEvents(new HubListener(this, hub, menu, adminCommand), this);
        getServer().getPluginManager().registerEvents(new HomesteadListener(homesteadMarker), this);
        getServer().getPluginManager().registerEvents(launchPads, this);
        SpawnDiscoverListener discover = new SpawnDiscoverListener(this, hub);
        getServer().getPluginManager().registerEvents(discover, this);
        discover.start();
        hubAccess = new de.aetherion.hub.api.HubAccessImpl();
        de.aetherion.core.api.AetherServices.registerHub(hubAccess);
        getLogger().info("AetherionHub enabled. /spawn and /hub teleport, /spawns opens the menu, /aetherpaste pastes Eldervale islands.");
    }

    @Override
    public void onDisable() {
        if (hub != null) {
            hub.saveAll();
        }
        if (hubAccess != null) {
            de.aetherion.core.api.AetherServices.clearHub(hubAccess);
            hubAccess = null;
        }
        instance = null;
    }

    public HubService getHub() {
        return hub;
    }

    public SpawnMenu getMenu() {
        return menu;
    }

    public HomesteadMarker getHomesteadMarker() {
        return homesteadMarker;
    }

    public HubAdminCommand getAdminCommand() {
        return adminCommand;
    }

    public IslandLaunchPads getLaunchPads() {
        return launchPads;
    }

    public int unlockAllSpawns(Player player) {
        if (hub == null || player == null) {
            return 0;
        }
        return hub.unlockAll(player.getUniqueId());
    }

    public static AetherionHub getInstance() {
        return instance;
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }
}
