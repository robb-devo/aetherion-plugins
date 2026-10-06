package de.aetherion.foraging.npc;

import de.aetherion.core.entity.DisplayEntities;
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
    public static final String HOLO_TAG = "ae_miss_canopy_holo";
    private static final long BRIEFING_DELAY_TICKS = 28L;

    private final AetherionForaging plugin;
    private final Map<UUID, Long> coolUntil = new ConcurrentHashMap<>();
    private final TalkDesk desk;
    private UUID hologramId;

    public IsleGuideNpc(AetherionForaging plugin) {
        this.plugin = plugin;
        new IsleGuideBriefingGUI(plugin);
        this.desk = canopyDesk();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureIfPlaced, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHologram, 40L, 80L);
        Bukkit.getScheduler().runTaskTimer(plugin, desk::tick, 40L, 10L);
    }

    public void shutdown() {
        desk.shutdown();
        removeHologram();
        purgeStrayPlates();
    }

    /** Wipe leftover Canopy plates (legacy persistent + PDC) in the guide world only. */
    public void purgeStrayPlates() {
        Location at = guideLocation();
        World world = at != null ? at.getWorld() : null;
        if (world == null) {
            String worldName = plugin.getConfig().getString("isle-guide.world", "world");
            world = Bukkit.getWorld(worldName);
        }
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof TextDisplay)) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(ForageKeys.canopyPlate(), PersistentDataType.BYTE)
                    || entity.getScoreboardTags().contains(HOLO_TAG)) {
                if (hologramId != null && hologramId.equals(entity.getUniqueId())) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    /**
     * Miss Canopy — Ledger-style desk for Foraging Eldervale (Fell Pulse, Crown Finds, cast, bark).
     * Chest briefing stays as "Briefing…" / fallback when the bubble can't run.
     */
    private TalkDesk canopyDesk() {
        return new TalkDesk(plugin, "forage_isle_canopy", DISPLAY, "§a", "canopy_clerk",
                this::guideLocation, this::livingHologram,
                player -> "Hey, {player}. Welcome to the grove. Ask — then go chop.",
                List.of(
                        TalkDesk.Topic.of("The chop?", NamedTextColor.GREEN, "How do I fell trees here?",
                                "Hit a trunk and watch the timing bar — green is a clean cut. Stack clean cuts for a streak.",
                                "Big trunks (Titans) need two clean cuts. Miss and the streak cools. Same axe feel, deeper payoff."),
                        TalkDesk.Topic.of("Crown Finds?", NamedTextColor.GOLD, "What's a Crown Find?",
                                "Sometimes the crown lets go of a glowing find as the tree falls. Catch it mid-air for bonus weight.",
                                "§ePell§f at the Landing buys board orders — wood and Titans. That's the daily money loop."),
                        TalkDesk.Topic.of("Who's who?", NamedTextColor.LIGHT_PURPLE, "Who else is on the isle?",
                                "§ePell§f — Lumber Board. §6Tamsin§f — Grove Marks and bench gear. §dJuniper§f — Forest Ledger and sales.",
                                "Walk the Landing tour once: me → Pell → Tamsin → Juniper. Updrafts carry you between pads."),
                        TalkDesk.Topic.of("Bark & events?", NamedTextColor.RED, "Elderbark? Bark Blight?",
                                "§6Elderbark§f and §8Nightbark§f are rare heartwoods from chops. Two different kinds feed the magic table by the dock.",
                                "§cBark Blight§f is an isle event — beetles nip at your ankles. Squash them for coins and Standing.")
                ),
                "Briefing…", player -> IsleGuideBriefingGUI.open(player, DISPLAY),
                "Off you go. The trees won't fell themselves.");
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
        purgeStrayPlates();
        hologramId = null;
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
        boolean fresh = cool == null || cool <= now;
        if (fresh) {
            coolUntil.put(player.getUniqueId(), now + 3500L);
            de.aetherion.foraging.isle.ForageIsle isle = de.aetherion.foraging.isle.ForageIsle.get();
            if (isle != null) {
                isle.compass().talked(player, "guide");
            }
        }
        // Bubble + reply chips first; the chest briefing is "Briefing…" and the fallback.
        if (desk.click(player)) {
            if (fresh) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.55f, 1.25f);
            }
            return;
        }
        if (!fresh) {
            return;
        }
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
        if (!at.getChunk().isLoaded()) {
            return;
        }
        Location textAt = at.clone().add(0, 2.15, 0);
        TextDisplay holo = livingHologram();
        if (holo == null) {
            holo = DisplayEntities.findTagged(textAt, HOLO_TAG, 4.0);
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
        text.getPersistentDataContainer().set(ForageKeys.canopyPlate(), PersistentDataType.BYTE, (byte) 1);
        // Non-persistent like Farm cast plates — persistent=true survived restarts and stacked.
        text.setPersistent(false);
        text.setGravity(false);
        text.setInvulnerable(true);
    }

    private void tickHologram() {
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
        TextDisplay found = DisplayEntities.findTagged(at.clone().add(0, 2.15, 0), HOLO_TAG, 4.0);
        if (found != null) {
            hologramId = found.getUniqueId();
        }
        return found;
    }

    private void removeHologram() {
        TextDisplay holo = livingHologram();
        if (holo != null) {
            DisplayEntities.discard(holo);
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
                DisplayEntities.discard(display);
            }
        }
    }

    private static void cullTaggedNear(Location at, TextDisplay keep) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at, 4, 4, 4)) {
            if (!(entity instanceof TextDisplay display) || display == keep) {
                continue;
            }
            if (display.getScoreboardTags().contains(HOLO_TAG)) {
                DisplayEntities.discard(display);
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
