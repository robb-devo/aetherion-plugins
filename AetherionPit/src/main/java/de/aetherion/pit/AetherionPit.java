package de.aetherion.pit;

import de.aetherion.pit.command.PitCommand;
import de.aetherion.pit.data.PitDataStore;
import de.aetherion.pit.hub.HubGuide;
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
    private HubGuide guide;
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
        guide = new HubGuide(this);
        xpHud = new PitXpBarHud(this);
        new de.aetherion.pit.hud.PitHealthHud(this);
        getServer().getPluginManager().registerEvents(new PitZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new HubRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new PitCombatListener(this), this);
        new PitShopGUI(this);
        new HubDevMenu(this);
        HubNpcFancyListener.register(this, hubNpcs);
        hubNpcs.start();
        guide.start();
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
        var modpack = getCommand("modpack");
        if (modpack != null) {
            modpack.setExecutor((sender, c, l, a) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    guide.modpackCard(player);
                }
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
        dirty |= hubDefaults();
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

    /**
     * Hub polish (10-28): the two empty pedestals get the Modpack and Pit stations, the welcome board floats over
     * the plaza, the Pit board stands at the red line west of the hall. Only missing keys are written.
     */
    private boolean hubDefaults() {
        boolean dirty = false;
        dirty |= place("npcs.modpack", -33.5, 65.0, -2.5, -90.0);
        dirty |= place("npcs.pit", -33.5, 65.0, 3.5, -90.0);
        dirty |= place("welcome-board", -9.5, 66.6, 0.5, 90.0);
        dirty |= place("pit-entrance", -74.5, 56.0, 0.5, 90.0);
        dirty |= place("pit-board", -77.5, 58.0, 0.5, 90.0);
        for (String key : new String[]{"modrinth", "curseforge", "shader"}) {
            if (!getConfig().contains("modpack." + key, true)) {
                getConfig().set("modpack." + key, "");
                dirty = true;
            }
        }
        if (!getConfig().contains("modpack.github", true)) {
            getConfig().set("modpack.github", "https://github.com/robb-devo/aetherion-launcher/releases/latest");
            dirty = true;
        }
        return dirty;
    }

    private boolean place(String path, double x, double y, double z, double yaw) {
        if (getConfig().contains(path + ".x", true)) {
            return false;
        }
        getConfig().set(path + ".world", "world");
        getConfig().set(path + ".x", x);
        getConfig().set(path + ".y", y);
        getConfig().set(path + ".z", z);
        getConfig().set(path + ".yaw", yaw);
        getConfig().set(path + ".pitch", 0.0);
        return true;
    }

    @Override
    public void onDisable() {
        if (guide != null) {
            guide.stop();
        }
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

    public HubGuide guide() {
        return guide;
    }

    public PitXpBarHud xpHud() {
        return xpHud;
    }
}
