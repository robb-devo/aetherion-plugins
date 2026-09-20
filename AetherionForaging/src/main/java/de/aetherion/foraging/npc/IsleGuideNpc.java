package de.aetherion.foraging.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.ForageKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Miss Canopy — forage isle briefing NPC. Place only via DEV anchor (no auto-spawn).
 */
public final class IsleGuideNpc implements Listener {

    public static final String FANCY_NAME = "ae_forage_grove_guide";
    public static final String DISPLAY = "Miss Canopy";
    /** Subtitle under the name — replaces the default Fancy "NPC" line. */
    public static final String TITLE = "Foraging Teacher";

    private static final String HIDE_TEAM = "ae_forage_hide_npc";
    private static final String HOLO_TAG = "ae_miss_canopy_holo";
    private static final long BRIEFING_DELAY_TICKS = 28L;

    private final AetherionForaging plugin;
    private final Map<UUID, Long> coolUntil = new ConcurrentHashMap<>();
    private UUID hologramId;

    public IsleGuideNpc(AetherionForaging plugin) {
        this.plugin = plugin;
        new IsleGuideBriefingGUI(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureIfPlaced, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHologram, 20L, 10L);
    }

    public void reload() {
        ensureIfPlaced();
    }

    public void ensureIfPlaced() {
        if (!plugin.getConfig().getBoolean("isle-guide.placed", false)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("isle-guide.enabled", true)) {
            return;
        }
        Location at = guideLocation();
        if (at == null) {
            return;
        }
        spawnFancy(at);
    }

    public static ItemStack createAnchor() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aNPC Anchor §7(" + DISPLAY + ")");
            meta.setLore(List.of(
                    "§7DEV · forage isle briefing NPC.",
                    "§7Habitats · heartwoods · grove table · TAB weather.",
                    "",
                    "§eRight-click a block to place.",
                    "§eSneak + right-click §7despawns her."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ForageKeys.isleGuideAnchor(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(ForageKeys.isleGuideAnchor(), PersistentDataType.BYTE);
    }

    public void setHere(Player player) {
        if (player == null || player.getLocation().getWorld() == null) {
            return;
        }
        placeAt(player.getLocation(), player);
    }

    public void placeAt(Location at, Player player) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        plugin.getConfig().set("isle-guide.enabled", true);
        plugin.getConfig().set("isle-guide.placed", true);
        plugin.getConfig().set("isle-guide.world", at.getWorld().getName());
        plugin.getConfig().set("isle-guide.x", at.getX());
        plugin.getConfig().set("isle-guide.y", at.getY());
        plugin.getConfig().set("isle-guide.z", at.getZ());
        plugin.getConfig().set("isle-guide.yaw", at.getYaw());
        plugin.getConfig().set("isle-guide.pitch", at.getPitch());
        plugin.saveConfig();
        spawnFancy(at);
        if (player != null) {
            player.sendMessage("§a" + DISPLAY + " §7placed here.");
            player.playSound(at, Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
        }
    }

    public void despawn(Player player) {
        removeFancy();
        removeHologram();
        plugin.getConfig().set("isle-guide.placed", false);
        plugin.saveConfig();
        if (player != null) {
            player.sendMessage("§e" + DISPLAY + " §7despawned. Re-place with DEV anchor.");
        }
    }

    private Location guideLocation() {
        var cfg = plugin.getConfig();
        String worldName = cfg.getString("isle-guide.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null || !cfg.contains("isle-guide.x")) {
            return null;
        }
        return new Location(
                world,
                cfg.getDouble("isle-guide.x"),
                cfg.getDouble("isle-guide.y"),
                cfg.getDouble("isle-guide.z"),
                (float) cfg.getDouble("isle-guide.yaw", 0.0),
                (float) cfg.getDouble("isle-guide.pitch", 0.0)
        );
    }

    public void talk(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = coolUntil.get(player.getUniqueId());
        if (cool != null && cool > now) {
            return;
        }
        coolUntil.put(player.getUniqueId(), now + 3500L);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.55f, 1.25f);
        player.sendMessage("§a" + DISPLAY + " §8» §fHey. Short tour of the isle?");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                IsleGuideBriefingGUI.open(player, DISPLAY);
            }
        }, BRIEFING_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isAnchor(hand)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            despawn(player);
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getRelative(org.bukkit.block.BlockFace.UP).getLocation().add(0.5, 0, 0.5);
        at.setYaw(player.getLocation().getYaw());
        at.setPitch(0);
        placeAt(at, player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent event) {
        String name = event.getRightClicked().getCustomName();
        if (name != null && name.contains(DISPLAY)) {
            event.setCancelled(true);
            talk(event.getPlayer());
        }
    }

    private void removeFancy() {
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            Object existing = FancyNpcFacade.getNpc(manager, FANCY_NAME);
            if (existing == null) {
                return;
            }
            FancyNpcFacade.removeFromPlayersQuiet(existing);
            try {
                FancyNpcFacade.unregister(manager, existing);
            } catch (ReflectiveOperationException ignored) {
            }
            FancyNpcFacade.saveNpcs(manager, false);
        } catch (Throwable t) {
            plugin.getLogger().warning(DISPLAY + " remove failed: " + t.getMessage());
        }
    }

    private void spawnFancy(Location at) {
        if (!FancyNpcFacade.isAvailable()) {
            plugin.getLogger().warning(DISPLAY + ": FancyNpcs missing.");
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnFancy(at), 40L);
                return;
            }
            Object existing = FancyNpcFacade.getNpc(manager, FANCY_NAME);
            String display = "<empty>";
            if (existing != null) {
                Object data = FancyNpcFacade.data(existing);
                FancyNpcFacade.setLocation(data, at.clone());
                FancyNpcFacade.invoke(data, "setDisplayName", String.class, display);
                FancyNpcFacade.applyVisibility(data, visibilityDistance());
                applySkin(data);
                FancyNpcFacade.moveForAll(existing);
                FancyNpcFacade.updateForAll(existing);
                FancyNpcFacade.spawnForAll(existing);
                hideVanillaNametag(existing);
                ensureHologram(at);
                return;
            }
            UUID creator = new UUID(0L, Math.abs(FANCY_NAME.hashCode()));
            Object data = FancyNpcFacade.createNpcData(FANCY_NAME, creator, at.clone());
            FancyNpcFacade.invoke(data, "setDisplayName", String.class, display);
            FancyNpcFacade.invokeQuiet(data, "setType", org.bukkit.entity.EntityType.class,
                    org.bukkit.entity.EntityType.PLAYER);
            FancyNpcFacade.invokeQuiet(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invokeQuiet(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.applyVisibility(data, visibilityDistance());
            applySkin(data);
            Object npc = FancyNpcFacade.adapt(data);
            FancyNpcFacade.invokeQuiet(npc, "setSaveToFile", boolean.class, true);
            FancyNpcFacade.create(npc);
            FancyNpcFacade.register(manager, npc);
            FancyNpcFacade.spawnForAll(npc);
            FancyNpcFacade.saveNpcs(manager, false);
            hideVanillaNametag(npc);
            ensureHologram(at);
            plugin.getLogger().info(DISPLAY + " spawned at "
                    + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
        } catch (Throwable t) {
            plugin.getLogger().warning(DISPLAY + " FancyNpc spawn failed: " + t.getMessage());
        }
    }

    /** Fancy default is ~10 blocks — raise so the isle teacher reads from the pad. */
    private int visibilityDistance() {
        return Math.max(16, plugin.getConfig().getInt("isle-guide.visibility-distance", 48));
    }

    private void applySkin(Object data) {
        if (data == null) {
            return;
        }
        String skin = plugin.getConfig().getString("isle-guide.skin", "MHF_Oak");
        try {
            data.getClass().getMethod("setSkin", String.class).invoke(data, skin);
        } catch (Throwable ignored) {
        }
    }

    private void ensureHologram(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        removeHologram();
        Location textAt = at.clone().add(0, 2.15, 0);
        TextDisplay holo = at.getWorld().spawn(textAt, TextDisplay.class, text -> {
            text.text(Component.text(DISPLAY, NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(TITLE, NamedTextColor.GRAY)));
            text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setSeeThrough(false);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            text.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            text.addScoreboardTag(HOLO_TAG);
            text.setPersistent(true);
        });
        hologramId = holo.getUniqueId();
    }

    private void tickHologram() {
        if (!plugin.getConfig().getBoolean("isle-guide.placed", false)) {
            return;
        }
        Location at = guideLocation();
        if (at == null) {
            return;
        }
        TextDisplay holo = hologram();
        if (holo == null || holo.isDead()) {
            ensureHologram(at);
            return;
        }
        Location want = at.clone().add(0, 2.15, 0);
        if (holo.getLocation().distanceSquared(want) > 0.01) {
            holo.teleport(want);
        }
    }

    private TextDisplay hologram() {
        if (hologramId == null) {
            Location at = guideLocation();
            if (at == null || at.getWorld() == null) {
                return null;
            }
            for (Entity entity : at.getWorld().getNearbyEntities(at, 3, 4, 3)) {
                if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(HOLO_TAG)) {
                    hologramId = display.getUniqueId();
                    return display;
                }
            }
            return null;
        }
        Entity entity = Bukkit.getEntity(hologramId);
        return entity instanceof TextDisplay display ? display : null;
    }

    private void removeHologram() {
        TextDisplay holo = hologram();
        if (holo != null) {
            holo.remove();
        }
        hologramId = null;
        Location at = guideLocation();
        if (at != null && at.getWorld() != null) {
            for (Entity entity : at.getWorld().getNearbyEntities(at.clone().add(0, 2, 0), 2, 3, 2)) {
                if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(HOLO_TAG)) {
                    display.remove();
                }
            }
        }
    }

    /**
     * Hide vanilla PLAYER nametag (the "❤ NPC" look-at junk). Fancy name is empty; TextDisplay owns the label.
     */
    private void hideVanillaNametag(Object fancyNpc) {
        if (fancyNpc == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Object entity = null;
                for (String method : List.of("getEntity", "getNpcEntity", "getBukkitEntity")) {
                    try {
                        entity = fancyNpc.getClass().getMethod(method).invoke(fancyNpc);
                        if (entity != null) {
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (!(entity instanceof Entity bukkit)) {
                    return;
                }
                if (Bukkit.getScoreboardManager() == null) {
                    return;
                }
                var board = Bukkit.getScoreboardManager().getMainScoreboard();
                var team = board.getTeam(HIDE_TEAM);
                if (team == null) {
                    team = board.registerNewTeam(HIDE_TEAM);
                    team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                            org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                    team.setCanSeeFriendlyInvisibles(false);
                }
                try {
                    team.addEntity(bukkit);
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                }
            } catch (Throwable ignored) {
            }
        }, 5L);
    }

    public static boolean isGuideFancy(String fancyName) {
        return FANCY_NAME.equalsIgnoreCase(fancyName);
    }

    public void handleFancyClick(Player player, String fancyName) {
        if (isGuideFancy(fancyName)) {
            talk(player);
        }
    }
}
