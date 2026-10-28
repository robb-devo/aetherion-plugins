package de.aetherion.pit.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.hub.HubHolograms;
import de.aetherion.pit.menu.PitShopGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
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
 * The five lobby stations on the pedestals west of spawn: Aetherion (travel to the MMO), Modpack &amp; Shader,
 * The Pit, Discord and the Pit Shop. FancyNpcs draws the bodies with no name tag; the plates above them are
 * {@link de.aetherion.pit.hub.HubGuide} signs.
 */
public final class HubNpcService implements PluginMessageListener {

    public static final String FANCY_AETHERION = "ae_hub_aetherion";
    public static final String FANCY_DISCORD = "ae_hub_discord";
    public static final String FANCY_SHOP = "ae_hub_shop";
    public static final String FANCY_MODPACK = "ae_hub_modpack";
    public static final String FANCY_PIT = "ae_hub_pit";

    /** {@code profession} is a FancyNpcs villager attribute; {@code glow} a NamedTextColor name or null. */
    public record Station(String key, String fancyName, EntityType type, String profession, String glow) {
    }

    public static final List<Station> STATIONS = List.of(
            new Station("aetherion", FANCY_AETHERION, EntityType.PLAYER, null, "light_purple"),
            new Station("modpack", FANCY_MODPACK, EntityType.VILLAGER, "librarian", null),
            new Station("pit", FANCY_PIT, EntityType.VINDICATOR, null, "red"),
            new Station("discord", FANCY_DISCORD, EntityType.PLAYER, null, null),
            new Station("shop", FANCY_SHOP, EntityType.VILLAGER, "weaponsmith", null)
    );

    private static final String DEFAULT_AETHERION_SKIN =
            "https://textures.minecraft.net/texture/f163d98f20f7b8dec6ecc7a314f82e33f4d4105919dfd287080627979a957933";
    private static final String DEFAULT_DISCORD_SKIN =
            "https://textures.minecraft.net/texture/ed1e52092b09bc2495d0ed1a2e8e6279548a120dd27e4955963d75e820a181c6";

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
        for (Station station : STATIONS) {
            Location loc = locationOf(station.key());
            if (loc == null) {
                continue;
            }
            if (!spawnFancy(station, skinOf(station), loc, vis)) {
                plugin.getLogger().warning("Hub NPC " + station.fancyName() + " could not spawn (FancyNpcs?).");
            }
        }
        plugin.guide().placePlates();
    }

    private String skinOf(Station station) {
        return switch (station.key()) {
            case "aetherion" -> plugin.getConfig().getString("npcs.aetherion.skin", DEFAULT_AETHERION_SKIN);
            case "discord" -> plugin.getConfig().getString("npcs.discord.skin", DEFAULT_DISCORD_SKIN);
            default -> null;
        };
    }

    public static Station station(String key) {
        for (Station station : STATIONS) {
            if (station.key().equalsIgnoreCase(key)) {
                return station;
            }
        }
        return null;
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
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.4f);
            plugin.transfer().toAetherion(player);
        } else if (FANCY_DISCORD.equalsIgnoreCase(fancyName)) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            openDiscord(player);
        } else if (FANCY_SHOP.equalsIgnoreCase(fancyName)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.7f, 1.0f);
            PitShopGUI.open(player);
        } else if (FANCY_MODPACK.equalsIgnoreCase(fancyName)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
            plugin.guide().modpackCard(player);
        } else if (FANCY_PIT.equalsIgnoreCase(fancyName)) {
            plugin.guide().enterPit(player);
        }
    }

    public static boolean isHubNpc(String fancyName) {
        for (Station station : STATIONS) {
            if (station.fancyName().equalsIgnoreCase(fancyName)) {
                return true;
            }
        }
        return false;
    }

    public void openDiscord(Player player) {
        String url = resolveDiscordInvite();
        boolean german = HubHolograms.german(player);
        if (url == null || url.isBlank() || url.contains("changethis")) {
            player.sendMessage(de.aetherion.pit.util.Msg.amp(german
                    ? "&9Discord &8- &7Die Einladung kommt bald."
                    : "&9Discord &8- &7The invite is coming soon."));
            return;
        }
        // Persist discovered invite for next time.
        if (!url.equals(plugin.getConfig().getString("discord-invite", ""))) {
            plugin.getConfig().set("discord-invite", url);
            plugin.saveConfig();
        }
        player.sendMessage(Component.text("Discord", NamedTextColor.BLUE, TextDecoration.BOLD)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(german ? "Klicken zum Beitreten" : "Click to join", NamedTextColor.AQUA,
                                TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)))
                .append(Component.text("  " + url, NamedTextColor.GRAY)));
    }

    /** Config → DiscordSRV invite link → create permanent invite via DiscordSRV bot. */
    public String resolveDiscordInvite() {
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

    /** Players on the MMO backend, or -1 before the proxy answered. */
    public int aetherionOnline() {
        String server = plugin.getConfig().getString("aetherion-server", "mmo-r");
        return proxyCounts.getOrDefault(server.toLowerCase(Locale.ROOT), -1);
    }

    private void tickCounts() {
        requestPlayerCount(plugin.getConfig().getString("aetherion-server", "mmo-r"));
        plugin.guide().refreshAetherionPlate();
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
            plugin.guide().refreshAetherionPlate();
        } catch (Exception ignored) {
        }
    }

    public Location locationOf(String key) {
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

    private boolean spawnFancy(Station station, String skin, Location location, int visibility) {
        if (!FancyNpcFacade.isAvailable()) {
            return false;
        }
        String fancyName = station.fancyName();
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnFancy(station, skin, location, visibility), 40L);
                return true;
            }
            Object existing = FancyNpcFacade.getNpc(manager, fancyName);
            if (existing != null && station.type() != typeOf(FancyNpcFacade.data(existing))) {
                // Body type changed (the shop used to be a skinless player): rebuild it.
                FancyNpcFacade.removeForAll(existing);
                FancyNpcFacade.unregister(manager, existing);
                existing = null;
            }
            if (existing != null) {
                Object data = FancyNpcFacade.data(existing);
                FancyNpcFacade.setLocation(data, location.clone());
                FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
                applyGlowAndVisibility(data, station.glow(), visibility);
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
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, station.type());
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            applyGlowAndVisibility(data, station.glow(), visibility);
            applySkin(data, skin);
            FancyNpcFacade.invokeQuiet(data, "setSpawnEntity", boolean.class, true);
            Object attribute = station.profession() == null ? null : villagerAttribute("profession");
            if (attribute != null) {
                data.getClass().getMethod("addAttribute", Class.forName("de.oliver.fancynpcs.api.NpcAttribute"),
                        String.class).invoke(data, attribute, station.profession());
            }
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(npc, true);
            FancyNpcFacade.create(npc);
            if (attribute != null) {
                attribute.getClass().getMethod("apply", FancyNpcFacade.npcClass(), String.class)
                        .invoke(attribute, npc, station.profession());
            }
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            FancyNpcFacade.saveNpcs(manager, true);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Hub FancyNPC " + fancyName + ": " + ex.getMessage());
            return false;
        }
    }

    private static EntityType typeOf(Object data) {
        try {
            Object type = data.getClass().getMethod("getType").invoke(data);
            return type instanceof EntityType entityType ? entityType : EntityType.PLAYER;
        } catch (ReflectiveOperationException ex) {
            return EntityType.PLAYER;
        }
    }

    private static Object villagerAttribute(String name) {
        try {
            Object api = FancyNpcFacade.plugin();
            Object attributes = api.getClass().getMethod("getAttributeManager").invoke(api);
            return attributes.getClass()
                    .getMethod("getAttributeByName", EntityType.class, String.class)
                    .invoke(attributes, EntityType.VILLAGER, name);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
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

    private static void applyGlowAndVisibility(Object data, String glow, int visibility) {
        Class<?> dataClass = data.getClass();
        boolean glowing = glow != null;
        try {
            dataClass.getMethod("setGlowing", boolean.class).invoke(data, glowing);
        } catch (Throwable ignored) {
            try {
                dataClass.getMethod("setGlow", boolean.class).invoke(data, glowing);
            } catch (Throwable ignored2) {
            }
        }
        if (glowing) {
            String field = glow.toUpperCase(Locale.ROOT);
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
                    Object color = named.getField(field).get(null);
                    try {
                        dataClass.getMethod("setGlowColor", named).invoke(data, color);
                        colored = true;
                    } catch (NoSuchMethodException ex) {
                        dataClass.getMethod("setGlowingColor", named).invoke(data, color);
                        colored = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (!colored) {
                String name = glow.toLowerCase(Locale.ROOT);
                try {
                    dataClass.getMethod("setGlowColor", String.class).invoke(data, name);
                } catch (Throwable ignored) {
                    try {
                        dataClass.getMethod("setGlowingColor", String.class).invoke(data, name);
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
}
