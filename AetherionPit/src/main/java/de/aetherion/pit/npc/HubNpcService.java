package de.aetherion.pit.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.menu.PitShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lobby FancyNPCs: Aetherion transfer, Discord invite, Pit shop placeholder.
 */
public final class HubNpcService implements PluginMessageListener {

    public static final String FANCY_AETHERION = "ae_hub_aetherion";
    public static final String FANCY_DISCORD = "ae_hub_discord";
    public static final String FANCY_SHOP = "ae_hub_shop";

    private final AetherionPit plugin;
    private final Map<String, Integer> proxyCounts = new ConcurrentHashMap<>();

    public HubNpcService(AetherionPit plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
    }

    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureAll, 80L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCounts, 100L, 100L);
    }

    public void ensureAll() {
        int vis = plugin.getConfig().getInt("npc-visibility-distance", 64);
        String aetherionSkin = plugin.getConfig().getString("npcs.aetherion.skin",
                "https://textures.minecraft.net/texture/f163d98f20f7b8dec6ecc7a314f82e33f4d4105919dfd287080627979a957933");
        String discordSkin = plugin.getConfig().getString("npcs.discord.skin",
                "https://textures.minecraft.net/texture/ed1e52092b09bc2495d0ed1a2e8e6279548a120dd27e4955963d75e820a181c6");
        // FancyNpcs 2.x prefers MiniMessage — &/§ in nametags often shows as Â on clients.
        ensureOne("aetherion", FANCY_AETHERION, "<light_purple><bold>Aetherion</bold></light_purple>", aetherionSkin, true, vis);
        ensureOne("discord", FANCY_DISCORD, "<blue><bold>Discord</bold></blue>", discordSkin, false, vis);
        ensureOne("shop", FANCY_SHOP, "<gold><bold>Pit Shop</bold></gold>", null, false, vis);
    }

    public void place(String id, Location location) {
        if (location == null || location.getWorld() == null || id == null) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        plugin.getConfig().set("npcs." + key + ".world", location.getWorld().getName());
        plugin.getConfig().set("npcs." + key + ".x", location.getX());
        plugin.getConfig().set("npcs." + key + ".y", location.getY());
        plugin.getConfig().set("npcs." + key + ".z", location.getZ());
        plugin.getConfig().set("npcs." + key + ".yaw", location.getYaw());
        plugin.getConfig().set("npcs." + key + ".pitch", location.getPitch());
        plugin.saveConfig();
        ensureAll();
    }

    public void handleClick(Player player, String fancyName) {
        if (player == null || fancyName == null) {
            return;
        }
        if (FANCY_AETHERION.equalsIgnoreCase(fancyName)) {
            plugin.transfer().toAetherion(player);
            return;
        }
        if (FANCY_DISCORD.equalsIgnoreCase(fancyName)) {
            openDiscord(player);
            return;
        }
        if (FANCY_SHOP.equalsIgnoreCase(fancyName)) {
            de.aetherion.pit.util.Msg.send(player, "&6Pit Shop &8- &7Gear up for the arena outside the red line.");
            PitShopGUI.open(player);
        }
    }

    public static boolean isHubNpc(String fancyName) {
        return FANCY_AETHERION.equalsIgnoreCase(fancyName)
                || FANCY_DISCORD.equalsIgnoreCase(fancyName)
                || FANCY_SHOP.equalsIgnoreCase(fancyName);
    }

    private void openDiscord(Player player) {
        String url = resolveDiscordInvite();
        if (url == null || url.isBlank() || url.contains("changethis")) {
            player.sendMessage(de.aetherion.pit.util.Msg.amp(
                    "&9Discord &8- &7Invite not ready yet - staff can set discord-invite in config."));
            return;
        }
        // Persist discovered invite for next time.
        if (!url.equals(plugin.getConfig().getString("discord-invite", ""))) {
            plugin.getConfig().set("discord-invite", url);
            plugin.saveConfig();
        }
        player.sendMessage(Component.text("Discord", NamedTextColor.BLUE, TextDecoration.BOLD)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Click to join", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)))
                .append(Component.text("  " + url, NamedTextColor.GRAY)));
    }

    /** Config → DiscordSRV invite link → create permanent invite via DiscordSRV bot. */
    private String resolveDiscordInvite() {
        String configured = plugin.getConfig().getString("discord-invite", "").trim();
        if (!configured.isEmpty() && !configured.contains("changethis")) {
            return configured;
        }
        try {
            Plugin dsrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
            if (dsrv != null && dsrv.isEnabled()) {
                Object main = Class.forName("github.scarsz.discordsrv.DiscordSRV").getMethod("getPlugin").invoke(null);
                try {
                    Object link = main.getClass().getMethod("getInviteLink").invoke(main);
                    if (link != null) {
                        String s = String.valueOf(link).trim();
                        if (!s.isEmpty() && !s.contains("changethis") && s.startsWith("http")) {
                            return s;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                }
                // Fallback: read DiscordSRV config.yml
                java.io.File cfg = new java.io.File(dsrv.getDataFolder(), "config.yml");
                if (cfg.isFile()) {
                    org.bukkit.configuration.file.YamlConfiguration yml =
                            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfg);
                    String s = yml.getString("DiscordInviteLink", "").trim();
                    if (!s.isEmpty() && !s.contains("changethis") && s.startsWith("http")) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return configured;
    }

    private void ensureOne(String configKey, String fancyName, String display, String skin,
                           boolean glow, int visibility) {
        Location loc = locationOf(configKey);
        if (loc == null) {
            return;
        }
        if (!spawnFancy(fancyName, display, skin, loc, glow, visibility)) {
            plugin.getLogger().warning("Hub NPC " + fancyName + " could not spawn (FancyNpcs?).");
        }
    }

    private void tickCounts() {
        requestPlayerCount(plugin.getConfig().getString("aetherion-server", "mmo-r"));
        updateAetherionLabel();
    }

    private void requestPlayerCount(String server) {
        try {
            Player messenger = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (messenger == null) {
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("PlayerCount");
            out.writeUTF(server);
            messenger.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (Exception ignored) {
        }
    }

    private void updateAetherionLabel() {
        String server = plugin.getConfig().getString("aetherion-server", "mmo-r");
        int count = proxyCounts.getOrDefault(server.toLowerCase(Locale.ROOT), -1);
        String label = count >= 0
                ? "<light_purple><bold>Aetherion</bold></light_purple> <dark_gray>-</dark_gray> <white>"
                + count + " online</white>"
                : "<light_purple><bold>Aetherion</bold></light_purple> <dark_gray>-</dark_gray> <gray>click to play</gray>";
        setFancyDisplayName(FANCY_AETHERION, label);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"BungeeCord".equals(channel) && !"bungeecord:main".equalsIgnoreCase(channel)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String sub = in.readUTF();
            if (!"PlayerCount".equals(sub)) {
                return;
            }
            String server = in.readUTF();
            int count = in.readInt();
            proxyCounts.put(server.toLowerCase(Locale.ROOT), count);
            updateAetherionLabel();
        } catch (Exception ignored) {
        }
    }

    private Location locationOf(String key) {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("npcs." + key);
        if (sec == null) {
            return null;
        }
        World world = Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw", 0),
                (float) sec.getDouble("pitch", 0)
        );
    }

    private boolean spawnFancy(String fancyName, String display, String skin, Location location,
                               boolean glow, int visibility) {
        if (!FancyNpcFacade.isAvailable()) {
            return false;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> spawnFancy(fancyName, display, skin, location, glow, visibility), 40L);
                return true;
            }
            Object existing = FancyNpcFacade.getNpc(manager, fancyName);
            if (existing != null) {
                Object data = FancyNpcFacade.data(existing);
                FancyNpcFacade.setLocation(data, location.clone());
                FancyNpcFacade.invoke(data, "setDisplayName", String.class, display);
                applyGlowAndVisibility(data, glow, visibility);
                applySkin(data, skin);
                FancyNpcFacade.setSaveToFile(existing, true);
                FancyNpcFacade.moveForAll(existing);
                FancyNpcFacade.updateForAll(existing);
                FancyNpcFacade.spawnForAll(existing);
                FancyNpcFacade.saveNpcs(manager, true);
                return true;
            }
            UUID creator = new UUID(0L, Math.abs(fancyName.hashCode()));
            Object data = FancyNpcFacade.createNpcData(fancyName, creator, location.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, display);
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            applyGlowAndVisibility(data, glow, visibility);
            applySkin(data, skin);
            FancyNpcFacade.invokeQuiet(data, "setSpawnEntity", boolean.class, true);
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(npc, true);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            FancyNpcFacade.saveNpcs(manager, true);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Hub FancyNPC " + fancyName + ": " + ex.getMessage());
            return false;
        }
    }

    private static void applySkin(Object data, String skin) {
        if (data == null || skin == null || skin.isBlank()) {
            return;
        }
        Class<?> dataClass = data.getClass();
        try {
            dataClass.getMethod("setSkin", String.class).invoke(data, skin);
            return;
        } catch (Throwable ignored) {
        }
        try {
            // FancyNpcs Skin variant: setSkin(Skin) via identifier factory
            Class<?> skinClass = Class.forName("de.oliver.fancynpcs.api.utils.Skin");
            Object skinObj = null;
            for (String factory : List.of("byIdentifier", "of", "fromIdentifier", "getSkin")) {
                try {
                    skinObj = skinClass.getMethod(factory, String.class).invoke(null, skin);
                    if (skinObj != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (skinObj != null) {
                dataClass.getMethod("setSkin", skinClass).invoke(data, skinObj);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyGlowAndVisibility(Object data, boolean glow, int visibility) {
        Class<?> dataClass = data.getClass();
        try {
            dataClass.getMethod("setGlowing", boolean.class).invoke(data, glow);
        } catch (Throwable ignored) {
            try {
                dataClass.getMethod("setGlow", boolean.class).invoke(data, glow);
            } catch (Throwable ignored2) {
            }
        }
        if (glow) {
            boolean colored = false;
            // FancyNpcs typically wants NamedTextColor / Adventure color name.
            for (String colorClassName : new String[]{
                    "net.kyori.adventure.text.format.NamedTextColor",
                    "org.bukkit.ChatColor"
            }) {
                if (colored) {
                    break;
                }
                try {
                    Class<?> named = Class.forName(colorClassName);
                    Object lightPurple = named.getField("LIGHT_PURPLE").get(null);
                    try {
                        dataClass.getMethod("setGlowColor", named).invoke(data, lightPurple);
                        colored = true;
                    } catch (NoSuchMethodException ex) {
                        dataClass.getMethod("setGlowingColor", named).invoke(data, lightPurple);
                        colored = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (!colored) {
                try {
                    dataClass.getMethod("setGlowColor", String.class).invoke(data, "light_purple");
                } catch (Throwable ignored) {
                    try {
                        dataClass.getMethod("setGlowingColor", String.class).invoke(data, "light_purple");
                    } catch (Throwable ignored2) {
                    }
                }
            }
        }
        try {
            dataClass.getMethod("setVisibilityDistance", int.class).invoke(data, Math.max(16, visibility));
        } catch (Throwable ignored) {
            try {
                dataClass.getMethod("setShowDistance", int.class).invoke(data, Math.max(16, visibility));
            } catch (Throwable ignored2) {
            }
        }
    }

    private void setFancyDisplayName(String fancyName, String display) {
        try {
            if (!FancyNpcFacade.isAvailable()) {
                return;
            }
            Object manager = FancyNpcFacade.manager();
            Object npc = FancyNpcFacade.getNpc(manager, fancyName);
            if (npc == null) {
                return;
            }
            Object data = FancyNpcFacade.data(npc);
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, display);
            FancyNpcFacade.updateForAll(npc);
        } catch (Throwable ignored) {
        }
    }

}
