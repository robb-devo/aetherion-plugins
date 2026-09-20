package de.aetherion.foraging.npc;

import de.aetherion.core.entity.DisplayEntities;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.core.npc.FancyNpcSkins;
import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.ForageKeys;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.event.world.ChunkLoadEvent;
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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    public static final String HOLO_TAG = "ae_miss_canopy_holo";
    private static final long BRIEFING_DELAY_TICKS = 28L;

    private final AetherionForaging plugin;
    private final Map<UUID, Long> coolUntil = new ConcurrentHashMap<>();
    private UUID hologramId;
    private volatile boolean helperLogged;
    private volatile boolean skinFixLogged;

    public IsleGuideNpc(AetherionForaging plugin) {
        this.plugin = plugin;
        extractBundledSkin();
        new IsleGuideBriefingGUI(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Patch mhf_oak off the persisted FancyNpc before the 5s full ensure.
        Bukkit.getScheduler().runTaskLater(plugin, this::patchPersistedSkin, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureIfPlaced, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHologram, 40L, 40L);
    }

    public void reload() {
        reensure();
    }

    /** Safe re-ensure after killall / chunk load / player visit. Reuses the tagged hologram. */
    public void reensure() {
        try {
            ensureIfPlaced();
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
            Location at = guideLocation();
            if (at != null) {
                ensureHologram(at);
            }
        }
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

    /** Overwrite a persisted {@code MHF_Oak} identifier as soon as FancyNpcs is loaded. */
    private void patchPersistedSkin() {
        if (!plugin.getConfig().getBoolean("isle-guide.placed", false)) {
            return;
        }
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            if (!FancyNpcFacade.isManagerLoaded(manager)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::patchPersistedSkin, 20L);
                return;
            }
            Object existing = FancyNpcFacade.getNpc(manager, FANCY_NAME);
            if (existing == null) {
                return;
            }
            Object data = FancyNpcFacade.data(existing);
            if (applySkin(data)) {
                FancyNpcFacade.updateForAll(existing);
                FancyNpcFacade.saveNpcs(manager, true);
            }
        } catch (Throwable ignored) {
        }
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

    public void shutdown() {
        removeHologram();
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
        player.sendMessage("§a" + DISPLAY + " §8» §fHey — short tour of the isle?");
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

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("isle-guide.placed", false)) {
            return;
        }
        Location at = guideLocation();
        if (at == null || at.getWorld() == null || !at.getWorld().equals(event.getWorld())) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (chunk.getX() != at.getBlockX() >> 4 || chunk.getZ() != at.getBlockZ() >> 4) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::reensure);
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
                boolean skinChanged = applySkin(data);
                FancyNpcFacade.invokeQuiet(existing, "setSaveToFile", boolean.class, true);
                FancyNpcFacade.moveForAll(existing);
                FancyNpcFacade.updateForAll(existing);
                FancyNpcFacade.spawnForAll(existing);
                if (skinChanged) {
                    FancyNpcFacade.saveNpcs(manager, true);
                }
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
            FancyNpcFacade.saveNpcs(manager, true);
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

    /**
     * Local texture URL / PNG — never {@code MHF_Oak}. FancyNpcs UUIDFetcher
     * 404-loops that name every ~2s and hitch the client at TPS 20.
     */
    private boolean applySkin(Object data) {
        if (data == null) {
            return false;
        }
        String requested = plugin.getConfig().getString(
                "isle-guide.skin", FancyNpcSkins.DEFAULT_TEXTURE_URL);
        String have = FancyNpcSkins.identifier(data);
        String resolved = FancyNpcSkins.resolve(requested, bundledSkinFile());
        if (FancyNpcSkins.isUsernameLookup(have)
                || FancyNpcSkins.isBlocked(have)
                || FancyNpcSkins.isUsernameLookup(requested)
                || FancyNpcSkins.isBlocked(requested)) {
            FancyNpcSkins.rememberFailure(have);
            FancyNpcSkins.rememberFailure(requested);
            if (!skinFixLogged) {
                skinFixLogged = true;
                plugin.getLogger().info(DISPLAY
                        + " skin: using local texture instead of Mojang username '"
                        + (requested == null || requested.isBlank() ? have : requested)
                        + "' (stops FancyNpcs UUIDFetcher loop).");
            }
        } else if (FancyNpcSkins.same(have, resolved)) {
            FancyNpcFacade.invokeQuiet(data, "setMirrorSkin", boolean.class, false);
            return false;
        }
        return FancyNpcSkins.apply(data, resolved);
    }

    private File bundledSkinFile() {
        File file = new File(plugin.getDataFolder(), "skins/" + FancyNpcSkins.DEFAULT_FILE_NAME);
        return file.isFile() ? file : null;
    }

    private void extractBundledSkin() {
        File dir = new File(plugin.getDataFolder(), "skins");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File out = new File(dir, FancyNpcSkins.DEFAULT_FILE_NAME);
        try (InputStream in = plugin.getResource("skins/" + FancyNpcSkins.DEFAULT_FILE_NAME)) {
            if (in == null) {
                return;
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            plugin.getLogger().warning(DISPLAY + " could not extract skin "
                    + FancyNpcSkins.DEFAULT_FILE_NAME + ": " + ex.getMessage());
        }
    }

    private void ensureHologram(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        if (!at.getChunk().isLoaded()) {
            return;
        }
        Location textAt = at.clone().add(0, 2.15, 0);
        TextDisplay holo = livingHologram();
        if (holo == null) {
            holo = findGuideHologram(textAt);
        }
        cullTaggedNear(textAt, holo);
        if (holo != null && holo.isValid() && !holo.isDead()) {
            hologramId = holo.getUniqueId();
            styleHologram(holo);
            if (holo.getLocation().distanceSquared(textAt) > 0.01) {
                holo.teleport(textAt);
            }
            return;
        }
        TextDisplay spawned = at.getWorld().spawn(textAt, TextDisplay.class, this::styleHologram);
        hologramId = spawned.getUniqueId();
    }

    private void styleHologram(TextDisplay text) {
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
        // Plugin respawns on load — persistence stacked copies across restarts.
        text.setPersistent(false);
        text.setGravity(false);
        text.setInvulnerable(true);
    }

    private void tickHologram() {
        try {
            if (!plugin.getConfig().getBoolean("isle-guide.placed", false)) {
                return;
            }
            Location at = guideLocation();
            if (at == null || at.getWorld() == null || !at.getChunk().isLoaded()) {
                return;
            }
            TextDisplay holo = livingHologram();
            if (holo == null || holo.isDead() || !holo.isValid()) {
                ensureHologram(at);
                return;
            }
            Location want = at.clone().add(0, 2.15, 0);
            if (holo.getLocation().distanceSquared(want) > 0.01) {
                holo.teleport(want);
            }
            cullTaggedNear(want, holo);
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
        }
    }

    private TextDisplay livingHologram() {
        if (hologramId != null) {
            Entity entity = Bukkit.getEntity(hologramId);
            if (entity instanceof TextDisplay display && display.isValid() && !display.isDead()) {
                return display;
            }
        }
        Location at = guideLocation();
        if (at == null || at.getWorld() == null || !at.getChunk().isLoaded()) {
            return null;
        }
        TextDisplay found = findGuideHologram(at.clone().add(0, 2.15, 0));
        if (found != null) {
            hologramId = found.getUniqueId();
        }
        return found;
    }

    private void removeHologram() {
        TextDisplay holo = livingHologram();
        if (holo != null) {
            discardGuideHologram(holo);
        }
        hologramId = null;
        Location at = guideLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        Location textAt = at.clone().add(0, 2.15, 0);
        if (!textAt.getChunk().isLoaded()) {
            return;
        }
        for (Entity entity : at.getWorld().getNearbyEntities(textAt, 6, 5, 6)) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(HOLO_TAG)) {
                discardGuideHologram(display);
            }
        }
    }

    private void cullTaggedNear(Location at, TextDisplay keep) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at, 4, 4, 4)) {
            if (!(entity instanceof TextDisplay display) || display == keep) {
                continue;
            }
            if (display.getScoreboardTags().contains(HOLO_TAG)) {
                discardGuideHologram(display);
            }
        }
    }

    private TextDisplay findGuideHologram(Location at) {
        try {
            return DisplayEntities.findTagged(at, HOLO_TAG, 4.0);
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
            return findGuideHologramLocal(at);
        }
    }

    private static TextDisplay findGuideHologramLocal(Location at) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        TextDisplay found = null;
        for (Entity entity : at.getWorld().getNearbyEntities(at, 4.0, 4.0, 4.0)) {
            if (!(entity instanceof TextDisplay display) || !display.isValid() || display.isDead()) {
                continue;
            }
            if (!display.getScoreboardTags().contains(HOLO_TAG)) {
                continue;
            }
            if (found == null) {
                found = display;
            } else {
                try {
                    display.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        return found;
    }

    private void discardGuideHologram(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            DisplayEntities.discard(entity);
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            markHelperMissing(error);
            try {
                entity.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private void markHelperMissing(Throwable error) {
        if (helperLogged) {
            return;
        }
        helperLogged = true;
        plugin.getLogger().severe(
                "AetherionCore is missing de.aetherion.core.entity.DisplayEntities. "
                        + DISPLAY + " hologram uses a local fallback; update AetherionCore. ("
                        + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage())
                        + ")"
        );
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
