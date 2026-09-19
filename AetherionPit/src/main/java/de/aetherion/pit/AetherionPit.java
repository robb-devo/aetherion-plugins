package de.aetherion.pit;

import de.aetherion.pit.command.PitCommand;
import de.aetherion.pit.data.PitDataStore;
import de.aetherion.pit.hud.PitXpBarHud;
import de.aetherion.pit.listener.HubRulesListener;
import de.aetherion.pit.listener.PitCombatListener;
import de.aetherion.pit.listener.PitZoneListener;
import de.aetherion.pit.menu.HubDevMenu;
import de.aetherion.pit.menu.PitShopGUI;
import de.aetherion.pit.npc.HubNpcFancyListener;
import de.aetherion.pit.npc.HubNpcService;
import de.aetherion.pit.placeholder.PitPlaceholders;
import de.aetherion.pit.service.PitLevelService;
import de.aetherion.pit.service.SafeZoneService;
import de.aetherion.pit.service.TransferBridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionPit extends JavaPlugin {

    private static AetherionPit instance;
    private SafeZoneService safeZone;
    private PitDataStore data;
    private PitLevelService levels;
    private TransferBridge transfer;
    private HubNpcService hubNpcs;
    private PitXpBarHud xpHud;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeNewConfigDefaults();
        safeZone = new SafeZoneService(this);
        data = new PitDataStore(this);
        levels = new PitLevelService(this, data);
        transfer = new TransferBridge(this);
        hubNpcs = new HubNpcService(this);
        xpHud = new PitXpBarHud(this);
        getServer().getPluginManager().registerEvents(new PitZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new HubRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new PitCombatListener(this), this);
        new PitShopGUI(this);
        new HubDevMenu(this);
        HubNpcFancyListener.register(this, hubNpcs);
        hubNpcs.start();
        PitPlaceholders.tryRegister(this);
        PitCommand cmd = new PitCommand(this);
        var pit = getCommand("pit");
        if (pit != null) {
            pit.setExecutor(cmd);
            pit.setTabCompleter(cmd);
        }
        var ae = getCommand("aetherion");
        if (ae != null) {
            ae.setExecutor((sender, c, l, a) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    transfer.toAetherion(player);
                }
                return true;
            });
        }
        var hub = getCommand("hub");
        if (hub != null) {
            hub.setExecutor((sender, c, l, a) -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                org.bukkit.Location spawn = safeZone.spawn();
                if (spawn == null) {
                    player.sendMessage("§cSpawn not configured.");
                    return true;
                }
                player.teleport(spawn);
                player.sendMessage("§aBack to hub spawn.");
                return true;
            });
        }
        var dev = getCommand("dev");
        if (dev != null) {
            dev.setExecutor((sender, c, l, a) -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!player.hasPermission("aetherion.pit.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                HubDevMenu.open(player);
                return true;
            });
        }
        getLogger().info("AetherionPit enabled — safe box " + safeZone.describe() + ", Pit outside.");
        de.aetherion.pit.world.HubSchemPaster.maybePaste(this);
    }

    /** Keep live hub configs updated when new keys appear in the jar default. */
    private void mergeNewConfigDefaults() {
        boolean dirty = false;
        if (!getConfig().contains("safe-zone.min-x")) {
            getConfig().set("safe-zone.min-x", -78.0);
            getConfig().set("safe-zone.max-x", 42.0);
            getConfig().set("safe-zone.min-z", -60.0);
            getConfig().set("safe-zone.max-z", 60.0);
            dirty = true;
        }
        if (!getConfig().contains("force-spawn-on-join")) {
            getConfig().set("force-spawn-on-join", true);
            dirty = true;
        }
        if (!getConfig().contains("npcs.aetherion")) {
            getConfig().set("npcs.aetherion.world", "world");
            getConfig().set("npcs.aetherion.x", 4.5);
            getConfig().set("npcs.aetherion.y", 64.0);
            getConfig().set("npcs.aetherion.z", 3.5);
            getConfig().set("npcs.aetherion.yaw", 180.0);
            getConfig().set("npcs.discord.world", "world");
            getConfig().set("npcs.discord.x", -4.5);
            getConfig().set("npcs.discord.y", 64.0);
            getConfig().set("npcs.discord.z", 3.5);
            getConfig().set("npcs.discord.yaw", 180.0);
            getConfig().set("npcs.shop.world", "world");
            getConfig().set("npcs.shop.x", 0.5);
            getConfig().set("npcs.shop.y", 64.0);
            getConfig().set("npcs.shop.z", 5.5);
            getConfig().set("npcs.shop.yaw", 180.0);
            dirty = true;
        }
        if (!getConfig().contains("discord-invite")) {
            getConfig().set("discord-invite", "");
            dirty = true;
        }
        if (!getConfig().contains("npc-visibility-distance")) {
            getConfig().set("npc-visibility-distance", 72);
            dirty = true;
        }
        // Always rewrite chat strings to ASCII + &-codes (fixes live Â§ mojibake).
        String enterPit = "&cPit &7- PvP is live out here. Vanilla gear. Have fun.";
        String enterSafe = "&aSafe spawn &7- no PvP inside the red line.";
        String curPit = getConfig().getString("messages.enter-pit", "");
        String curSafe = getConfig().getString("messages.enter-safe", "");
        if (!enterPit.equals(curPit) || curPit.contains("Â") || curPit.contains("§")
                || curPit.contains("â") || curPit.contains("⚔") || curPit.contains("—")) {
            getConfig().set("messages.enter-pit", enterPit);
            dirty = true;
        }
        if (!enterSafe.equals(curSafe) || curSafe.contains("Â") || curSafe.contains("§")
                || curSafe.contains("â") || curSafe.contains("—")) {
            getConfig().set("messages.enter-safe", enterSafe);
            dirty = true;
        }
        getConfig().set("spawn.x", 0.0);
        getConfig().set("spawn.y", 64.0);
        getConfig().set("spawn.z", 0.0);
        getConfig().set("spawn.yaw", 90.0);
        getConfig().set("spawn.pitch", 0.0);
        if (dirty) {
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        if (data != null) {
            data.saveAll();
        }
        instance = null;
    }

    public static AetherionPit getInstance() {
        return instance;
    }

    public SafeZoneService safeZone() {
        return safeZone;
    }

    public PitDataStore data() {
        return data;
    }

    public PitLevelService levels() {
        return levels;
    }

    public TransferBridge transfer() {
        return transfer;
    }

    public HubNpcService hubNpcs() {
        return hubNpcs;
    }

    public PitXpBarHud xpHud() {
        return xpHud;
    }
}
