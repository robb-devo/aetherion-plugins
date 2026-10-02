package de.aetherion.quests.chest;

import de.aetherion.quests.AetherionQuests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * World exploration chests — 12h per player per chest, merchant-style spin.
 * <p>
 * Each chest is an authored {@link ExploreChestProp} (per-rarity model + idle life)
 * standing on an invisible barrier; the barrier keeps the click / break / explosion
 * contract. Opening swings the lid, the loot rises out of it and spins, the reward
 * lands, and the lid shuts again. Chests that are ready for you notice you when you
 * walk up, glint faintly from a distance, and say so once the first time you see them.
 * Older vanilla-block chests are converted in place on load (facing kept).
 */
public final class ExploreChestService {

    public static final String SPIN_TAG = "aether_explore_spin";
    public static final String LABEL_TAG = "aether_explore_label";

    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(12);
    private static final double NEAR_BLOCKS = 7.5d;
    /** Ready chests rattle once when you're this close (per player, per chest, per minute). */
    private static final double NOTICE_BLOCKS = 4.5d;
    private static final long NOTICE_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(60);
    /** Faint glint over ready chests between these distances — an invitation, not a beacon. */
    private static final double GLINT_MIN = 8.0d;
    private static final double GLINT_MAX = 48.0d;
    /** "Something catches the light" — once per session per chest, the first time you come this close. */
    private static final double SIGHT_BLOCKS = 14.0d;
    /** Props animate only while someone is this close. */
    private static final double ANIMATE_BLOCKS = 40.0d;
    private static final int PROP_STEP = 5;
    private static final Set<Material> LEGACY_BLOCKS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.PURPLE_SHULKER_BOX
    );

    private final AetherionQuests plugin;
    private final NamespacedKey kindKey;
    private final File file;
    private final Set<String> chests = new HashSet<>();
    private final Map<String, ExploreChestKind> kinds = new HashMap<>();
    /** playerUuid|chestKey → last open epoch */
    private final Map<String, Long> lastOpen = new HashMap<>();
    private final Set<String> busy = new HashSet<>();
    private final Map<String, BukkitTask> idle = new HashMap<>();
    private final Map<ExploreChestKind, List<ItemStack>> showcase = new EnumMap<>(ExploreChestKind.class);
    private final Map<String, ExploreChestProp> props = new HashMap<>();
    private final Map<String, BlockFace> facings = new HashMap<>();
    /** playerUuid|chestKey → next time the chest may notice this player. */
    private final Map<String, Long> noticed = new HashMap<>();
    /** playerUuid|chestKey seen this session. */
    private final Set<String> sighted = new HashSet<>();
    private BukkitTask proximity;
    private BukkitTask propTicker;
    private int glintCycle;

    public ExploreChestService(AetherionQuests plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "explore_chest");
        this.file = new File(plugin.getDataFolder(), "explore-chests.yml");
        load();
        proximity = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickProximity, 20L, 10L);
        propTicker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickProps, 25L, PROP_STEP);
    }

    public void shutdown() {
        if (proximity != null) {
            proximity.cancel();
            proximity = null;
        }
        if (propTicker != null) {
            propTicker.cancel();
            propTicker = null;
        }
        for (String id : new ArrayList<>(idle.keySet())) {
            cancelIdle(id);
        }
        for (ExploreChestProp prop : props.values()) {
            prop.remove();
        }
        props.clear();
        save();
    }

    public NamespacedKey kindKey() {
        return kindKey;
    }

    public ExploreChestKind kindOf(Block block) {
        if (block == null) {
            return null;
        }
        if (block.getType() == Material.BARRIER) {
            // Prop chests: the barrier is only a hitbox; the yml row carries the kind.
            return kinds.get(key(block));
        }
        if (!(block.getState() instanceof TileState state)) {
            return null;
        }
        ExploreChestKind fromBlock = ExploreChestKind.fromId(
                state.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING));
        if (fromBlock != null) {
            return fromBlock;
        }
        return kinds.get(key(block));
    }

    public boolean isExploreChest(Block block) {
        return kindOf(block) != null;
    }

    public void place(Player player, Block against, BlockFace face, ExploreChestKind kind) {
        if (kind == null) {
            return;
        }
        Block target = against.getRelative(face);
        if (!target.getType().isAir() && target.getType() != Material.WATER && !isExploreChest(target)) {
            player.sendMessage("§cNeed an empty block beside that.");
            return;
        }
        if (isExploreChest(target)) {
            ensureFx(target);
            player.sendMessage("§eThere's already an exploration chest there.");
            return;
        }
        // Invisible hitbox; the chest you see is the prop.
        target.setType(Material.BARRIER, false);
        String id = key(target);
        chests.add(id);
        kinds.put(id, kind);
        facings.put(id, horizontal(player.getFacing().getOppositeFace()));
        save();
        ensureFx(target);
        player.sendMessage(kind.chat() + "Placed " + kind.display() + "§7. Each player, this crate, every §f12h§7.");
        player.playSound(target.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 1.15f);
    }

    public void remove(Player player, Block block) {
        ExploreChestKind kind = kindOf(block);
        if (kind == null) {
            return;
        }
        String id = key(block);
        clearFx(block);
        busy.remove(id);
        chests.remove(id);
        kinds.remove(id);
        facings.remove(id);
        block.setType(Material.AIR, false);
        save();
        player.sendMessage("§eRemoved " + kind.display() + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
    }

    public void tryOpen(Player player, Block block) {
        ExploreChestKind kind = kindOf(block);
        if (player == null || kind == null) {
            return;
        }
        var merchant = plugin.getMerchantChests();
        if (merchant == null || !merchant.isUnlocked(player)) {
            deny(player, "Talk to the merchant at the harbour first — he unlocks these chests.");
            return;
        }
        String id = key(block);
        String stamp = player.getUniqueId() + "|" + id;
        if (lastOpen.containsKey(stamp)) {
            long left = lastOpen.getOrDefault(stamp, 0L) + COOLDOWN_MS - System.currentTimeMillis();
            if (left > 0L) {
                deny(player, "Empty for now. Back in " + formatLeft(left) + ".");
                return;
            }
        }
        if (busy.contains(id)) {
            player.sendMessage("§7Wait for the spin.");
            return;
        }
        ExploreChestLoot.Drop reward = ExploreChestLoot.roll(kind);
        if (reward == null || reward.display == null) {
            player.sendMessage("§cThe crate jammed. Try again in a second.");
            return;
        }
        ItemDisplay spinner = spinnerAt(block);
        TextDisplay label = labelAt(block);
        if (spinner == null) {
            ensureFx(block);
            spinner = spinnerAt(block);
        }
        busy.add(id);
        if (spinner == null) {
            ExploreChestProp prop = props.get(id);
            if (prop != null) {
                prop.open();
            }
            finish(player, block, kind, reward, label);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> settle(block, null), 30L);
            return;
        }
        spin(player, block, kind, spinner, label, reward);
    }

    public void restoreAll() {
        for (String id : new ArrayList<>(chests)) {
            Block block = blockOf(id);
            if (block == null || !block.getChunk().isLoaded()) {
                continue;
            }
            ExploreChestKind kind = kinds.get(id);
            if (kind != null && block.getState() instanceof TileState) {
                stamp(block, kind);
            }
            if (!isExploreChest(block) || block.getType().isAir()) {
                if (block.getType().isAir()) {
                    chests.remove(id);
                    kinds.remove(id);
                    facings.remove(id);
                    ExploreChestProp prop = props.remove(id);
                    if (prop != null) {
                        prop.remove();
                    }
                }
                continue;
            }
            ensureFx(block);
        }
        save();
    }

    public void restoreChunk(World world, int chunkX, int chunkZ) {
        for (String id : chests) {
            Block block = blockOf(id);
            if (block == null || block.getWorld() != world) {
                continue;
            }
            if (block.getX() >> 4 != chunkX || block.getZ() >> 4 != chunkZ) {
                continue;
            }
            ExploreChestKind kind = kinds.get(id);
            if (kind != null && block.getState() instanceof TileState) {
                stamp(block, kind);
            }
            if (isExploreChest(block)) {
                ensureFx(block);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> listed = new ArrayList<>();
        for (String id : chests) {
            ExploreChestKind kind = kinds.get(id);
            listed.add(id + "|" + (kind == null ? "rare" : kind.id()));
        }
        yaml.set("chests", listed);
        List<String> faced = new ArrayList<>();
        for (Map.Entry<String, BlockFace> entry : facings.entrySet()) {
            if (chests.contains(entry.getKey())) {
                faced.add(entry.getKey() + "|" + entry.getValue().name());
            }
        }
        yaml.set("facing", faced);
        List<java.util.Map<String, Object>> opens = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastOpen.entrySet()) {
            String stamp = entry.getKey();
            int cut = stamp.indexOf('|');
            if (cut < 0) {
                continue;
            }
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("player", stamp.substring(0, cut));
            row.put("chest", stamp.substring(cut + 1));
            row.put("at", entry.getValue());
            opens.add(row);
        }
        yaml.set("last-open", opens);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save explore-chests.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("chests")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int cut = raw.lastIndexOf('|');
            String id = raw;
            ExploreChestKind kind = ExploreChestKind.RARE;
            if (cut > 0) {
                ExploreChestKind parsed = ExploreChestKind.fromId(raw.substring(cut + 1));
                if (parsed != null) {
                    kind = parsed;
                    id = raw.substring(0, cut);
                }
            }
            chests.add(id);
            kinds.put(id, kind);
        }
        for (String raw : yaml.getStringList("facing")) {
            int cut = raw == null ? -1 : raw.lastIndexOf('|');
            if (cut <= 0) {
                continue;
            }
            try {
                facings.put(raw.substring(0, cut), horizontal(BlockFace.valueOf(raw.substring(cut + 1))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<?> opens = yaml.getList("last-open");
        if (opens != null) {
            for (Object rawOpen : opens) {
                if (!(rawOpen instanceof Map<?, ?> row)) {
                    continue;
                }
                Object player = row.get("player");
                Object chest = row.get("chest");
                Object at = row.get("at");
                if (player == null || chest == null || at == null) {
                    continue;
                }
                long when = at instanceof Number number ? number.longValue() : 0L;
                lastOpen.put(player + "|" + chest, when);
            }
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("last-open");
        if (root == null) {
            return;
        }
        for (String uuid : root.getKeys(false)) {
            ConfigurationSection player = root.getConfigurationSection(uuid);
            if (player != null) {
                for (String chestId : player.getKeys(false)) {
                    lastOpen.put(uuid + "|" + chestId, player.getLong(chestId, 0L));
                }
            } else {
                lastOpen.put(uuid, root.getLong(uuid, 0L));
            }
        }
    }

    private void stamp(Block block, ExploreChestKind kind) {
        if (block == null || kind == null || !(block.getState() instanceof TileState state)) {
            return;
        }
        String existing = state.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (kind.id().equals(existing)) {
            return;
        }
        state.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.id());
        state.update(true, false);
    }

    private void spin(
            Player player,
            Block block,
            ExploreChestKind kind,
            ItemDisplay spinner,
            TextDisplay label,
            ExploreChestLoot.Drop reward
    ) {
        List<ItemStack> pool = pool(kind);
        if (label != null && label.isValid()) {
            label.text(Component.text("...").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        String id = key(block);
        ExploreChestProp prop = props.get(id);
        if (prop != null) {
            prop.open();
        }
        // The loot climbs out of the open chest before it starts to turn.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (spinner.isValid()) {
                spinner.setInterpolationDelay(0);
                spinner.setInterpolationDuration(6);
                spinner.setTransformation(spinPose(0f, 0.62f, 0f));
            }
        }, 4L);
        new BukkitRunnable() {
            int step = 0;
            final int total = 22 + ThreadLocalRandom.current().nextInt(6);

            @Override
            public void run() {
                if (!spinner.isValid() || !player.isOnline()) {
                    busy.remove(id);
                    restoreLabel(block, label);
                    settle(block, spinner);
                    cancel();
                    return;
                }
                step++;
                if (step == 1) {
                    spinner.setInterpolationDelay(0);
                    spinner.setInterpolationDuration(1);
                }
                if (step < total) {
                    if (!pool.isEmpty()) {
                        spinner.setItemStack(pool.get(step % pool.size()));
                    }
                    spinner.setTransformation(spinPose(step * 42f, 0.62f, 0f));
                    player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.4f + (step % 5) * 0.08f);
                    return;
                }
                spinner.setItemStack(reward.display);
                spinner.setInterpolationDuration(4);
                spinner.setTransformation(spinPose(0f, 0.85f, 0.08f));
                burst(player, block.getLocation().add(0.5, 1.3, 0.5), kind);
                if (prop != null) {
                    prop.reveal();
                }
                cancel();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    finish(player, block, kind, reward, label);
                    if (spinner.isValid()) {
                        spinner.setInterpolationDuration(6);
                        spinner.setTransformation(spinPose(0f, 0.55f, 0f));
                    }
                }, 16L);
                // Hold the reward a moment, then it sinks back and the lid shuts.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> settle(block, spinner), 46L);
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    /** Park the spinner inside the chest and close the lid (unless someone already opened it again). */
    private void settle(Block block, ItemDisplay spinner) {
        if (busy.contains(key(block))) {
            return;
        }
        if (spinner != null && spinner.isValid()) {
            spinner.setInterpolationDelay(0);
            spinner.setInterpolationDuration(8);
            spinner.setTransformation(parkedPose());
        }
        ExploreChestProp prop = props.get(key(block));
        if (prop != null) {
            prop.close();
        }
    }

    private void finish(
            Player player,
            Block block,
            ExploreChestKind kind,
            ExploreChestLoot.Drop reward,
            TextDisplay label
    ) {
        busy.remove(key(block));
        lastOpen.put(player.getUniqueId() + "|" + key(block), System.currentTimeMillis());
        save();
        restoreLabel(block, label);
        if (!player.isOnline()) {
            return;
        }
        ExploreChestLoot.give(player, reward);
        player.sendMessage(kind.chat() + kind.display() + " §7pays out: " + ExploreChestLoot.nameOf(reward));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.35f);
        if (kind == ExploreChestKind.MYTHIC) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.45f, 1.2f);
        }
    }

    private void deny(Player player, String message) {
        player.sendMessage("§7" + message);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.45f, 1.35f);
    }

    private void ensureFx(Block block) {
        ExploreChestKind kind = kindOf(block);
        if (block == null || kind == null) {
            return;
        }
        String id = key(block);
        if (!migrate(block, id, kind)) {
            return;
        }
        World world = block.getWorld();
        Location spinAt = block.getLocation().add(0.5, 1.15, 0.5);
        Location textAt = block.getLocation().add(0.5, 1.65, 0.5);
        ItemDisplay spinner = spinnerAt(block);
        TextDisplay label = labelAt(block);
        List<ItemStack> pool = pool(kind);
        ItemStack first = pool.isEmpty() ? new ItemStack(kind.block()) : pool.get(0);
        if (spinner == null) {
            world.spawn(spinAt, ItemDisplay.class, display -> {
                display.setItemStack(first);
                display.setPersistent(true);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0f);
                display.setTransformation(parkedPose());
                display.addScoreboardTag(SPIN_TAG);
            });
        } else if (!busy.contains(id)) {
            // Older builds left the sample spinning on top — it lives inside the chest now.
            cancelIdle(id);
            spinner.setInterpolationDuration(0);
            spinner.setTransformation(parkedPose());
        }
        if (label == null) {
            world.spawn(textAt, TextDisplay.class, text -> {
                text.text(labelCopy(kind, null, block));
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setSeeThrough(false);
                text.setShadowed(true);
                text.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                text.setPersistent(true);
                text.setGravity(false);
                text.addScoreboardTag(LABEL_TAG);
            });
        } else if (label.isValid() && !busy.contains(id)) {
            label.text(labelCopy(kind, null, block));
        }
        ensureProp(block, id, kind);
    }

    /**
     * Vanilla-block chests from older builds become a barrier (facing kept) so the prop can
     * stand in their place. Anything that isn't ours or a barrier is left alone.
     *
     * @return true if the block is (now) a barrier hitbox
     */
    private boolean migrate(Block block, String id, ExploreChestKind kind) {
        Material type = block.getType();
        if (type == Material.BARRIER) {
            return true;
        }
        if (!LEGACY_BLOCKS.contains(type)) {
            return false;
        }
        BlockFace face = BlockFace.SOUTH;
        if (block.getBlockData() instanceof Directional directional) {
            face = directional.getFacing();
        }
        facings.putIfAbsent(id, horizontal(face));
        // The yml row carries the kind from here on (the tile PDC goes with the old block).
        chests.add(id);
        kinds.put(id, kind);
        block.setType(Material.BARRIER, false);
        save();
        return true;
    }

    private void ensureProp(Block block, String id, ExploreChestKind kind) {
        ExploreChestProp prop = props.get(id);
        if (prop != null && prop.valid() && prop.kind() == kind) {
            return;
        }
        if (prop != null) {
            prop.remove();
        }
        sweepProps(block);
        ExploreChestProp built = new ExploreChestProp(plugin, kind, block.getLocation(),
                yawOf(facings.getOrDefault(id, BlockFace.SOUTH)));
        built.build();
        props.put(id, built);
    }

    /** Stray prop parts (reload, crash) around a chest. */
    private static void sweepProps(Block block) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(at, 1.2, 1.6, 1.2)) {
            if (entity.getScoreboardTags().contains(ExploreChestProp.TAG)) {
                entity.remove();
            }
        }
    }

    private void tickProps() {
        if (props.isEmpty()) {
            return;
        }
        double r2 = ANIMATE_BLOCKS * ANIMATE_BLOCKS;
        for (Map.Entry<String, ExploreChestProp> entry : new ArrayList<>(props.entrySet())) {
            Block block = blockOf(entry.getKey());
            if (block == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            boolean watched = false;
            for (Player player : block.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= r2) {
                    watched = true;
                    break;
                }
            }
            if (!watched) {
                continue;
            }
            ExploreChestProp prop = entry.getValue();
            if (!prop.valid()) {
                // Something took the parts (chunk edge, /kill) — rebuild while someone's looking.
                ExploreChestKind kind = kindOf(block);
                if (kind != null && block.getType() == Material.BARRIER) {
                    ensureProp(block, entry.getKey(), kind);
                }
                continue;
            }
            prop.idle(PROP_STEP);
        }
    }

    private static BlockFace horizontal(BlockFace face) {
        if (face == null) {
            return BlockFace.SOUTH;
        }
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> face;
            default -> BlockFace.SOUTH;
        };
    }

    /** Radians for {@code rotateY}: local +Z (the chest front) turned toward {@code face}. */
    private static float yawOf(BlockFace face) {
        return switch (face) {
            case NORTH -> (float) Math.PI;
            case EAST -> (float) (Math.PI / 2.0);
            case WEST -> (float) (-Math.PI / 2.0);
            default -> 0f;
        };
    }

    private void clearFx(Block block) {
        cancelIdle(key(block));
        ExploreChestProp prop = props.remove(key(block));
        if (prop != null) {
            prop.remove();
        }
        Location at = block.getLocation().add(0.5, 1.4, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(at, 1.6, 2.2, 1.6)) {
            if (entity.getScoreboardTags().contains(SPIN_TAG)
                    || entity.getScoreboardTags().contains(LABEL_TAG)
                    || entity.getScoreboardTags().contains(ExploreChestProp.TAG)) {
                entity.remove();
            }
        }
    }

    private void cancelIdle(String id) {
        BukkitTask task = idle.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private void restoreLabel(Block block, TextDisplay label) {
        if (label == null || !label.isValid()) {
            return;
        }
        ExploreChestKind kind = kindOf(block);
        if (kind != null) {
            label.text(labelCopy(kind, null, block));
        }
    }

    private void tickProximity() {
        if (chests.isEmpty()) {
            return;
        }
        glintCycle++;
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(chests)) {
            if (busy.contains(id)) {
                continue;
            }
            Block block = blockOf(id);
            if (block == null || !block.getChunk().isLoaded() || !isExploreChest(block)) {
                continue;
            }
            ExploreChestKind kind = kindOf(block);
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Player player : block.getWorld().getPlayers()) {
                if (!player.isOnline() || player.getWorld() != block.getWorld()) {
                    continue;
                }
                double dist = player.getLocation().distanceSquared(center);
                if (dist > GLINT_MAX * GLINT_MAX) {
                    continue;
                }
                if (kind != null && readyFor(player, id)) {
                    invite(player, block, id, kind, dist, now);
                }
                if (dist > NEAR_BLOCKS * NEAR_BLOCKS) {
                    continue;
                }
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = player;
                }
            }
            TextDisplay label = labelAt(block);
            if (label != null && label.isValid() && kind != null) {
                label.text(labelCopy(kind, nearest, block));
            }
        }
        if (noticed.size() > 4096) {
            noticed.values().removeIf(until -> until < now);
        }
    }

    /**
     * Exploration invitation for a chest that's ready for {@code player}: a faint glint from
     * a distance, one "something catches the light" the first time this session, and a
     * rattle of the hasp when they walk right up.
     */
    private void invite(Player player, Block block, String id, ExploreChestKind kind, double dist2, long now) {
        String stamp = player.getUniqueId() + "|" + id;
        ExploreChestProp prop = props.get(id);
        Location top = prop != null ? prop.light() : block.getLocation().add(0.5, 0.9, 0.5);

        if (dist2 >= GLINT_MIN * GLINT_MIN && glintCycle % 4 == 0) {
            Color tone = prop != null ? prop.tone() : Color.WHITE;
            player.spawnParticle(Particle.DUST, top.clone().add(0.0, 0.85, 0.0), 2, 0.12, 0.12, 0.12, 0.0,
                    new Particle.DustOptions(tone, 1.3f));
            if ((kind == ExploreChestKind.LEGENDARY || kind == ExploreChestKind.MYTHIC) && glintCycle % 8 == 0) {
                player.spawnParticle(Particle.END_ROD, top.clone().add(0.0, 1.1, 0.0), 1, 0.05, 0.1, 0.05, 0.0);
            }
        }

        if (dist2 <= SIGHT_BLOCKS * SIGHT_BLOCKS && !lastOpen.containsKey(stamp) && sighted.add(stamp)) {
            player.sendActionBar(Component.text("✦ Something catches the light · ", NamedTextColor.GRAY)
                    .append(Component.text(kind.display(), kind.color())));
            player.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35f, 1.6f);
        }

        if (dist2 <= NOTICE_BLOCKS * NOTICE_BLOCKS && noticed.getOrDefault(stamp, 0L) <= now) {
            noticed.put(stamp, now + NOTICE_COOLDOWN_MS);
            if (prop != null) {
                prop.notice();
            }
            switch (kind) {
                case RARE -> player.playSound(top, Sound.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.25f, 1.6f);
                case EPIC -> player.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.45f, 1.2f);
                case LEGENDARY -> player.playSound(top, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 0.3f, 1.6f);
                case MYTHIC -> player.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.5f, 1.0f);
            }
        }
    }

    private boolean readyFor(Player player, String id) {
        Long last = lastOpen.get(player.getUniqueId() + "|" + id);
        return last == null || last + COOLDOWN_MS <= System.currentTimeMillis();
    }

    private Component statusLine(Player player, Block block) {
        if (player == null || block == null) {
            return Component.text("Loot · every 12h").color(NamedTextColor.DARK_GRAY);
        }
        String stamp = player.getUniqueId() + "|" + key(block);
        if (!lastOpen.containsKey(stamp)) {
            return Component.text("Ready · right-click").color(NamedTextColor.GREEN);
        }
        long left = lastOpen.getOrDefault(stamp, 0L) + COOLDOWN_MS - System.currentTimeMillis();
        if (left > 0L) {
            return Component.text("Ready in " + formatLeft(left)).color(NamedTextColor.GRAY);
        }
        return Component.text("Ready · right-click").color(NamedTextColor.GREEN);
    }

    private Component labelCopy(ExploreChestKind kind, Player viewer, Block block) {
        Component subtitle = viewer == null
                ? Component.text("Loot · every 12h").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                : statusLine(viewer, block).decoration(TextDecoration.ITALIC, false);
        return Component.text(kind.display())
                .color(kind.color())
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.newline())
                .append(subtitle);
    }

    private ItemDisplay spinnerAt(Block block) {
        Location at = block.getLocation().add(0.5, 1.15, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(at, 1.5, 2.0, 1.5)) {
            if (entity instanceof ItemDisplay display && display.getScoreboardTags().contains(SPIN_TAG)) {
                return display;
            }
        }
        return null;
    }

    private TextDisplay labelAt(Block block) {
        Location at = block.getLocation().add(0.5, 1.65, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(at, 1.5, 2.0, 1.5)) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(LABEL_TAG)) {
                return display;
            }
        }
        return null;
    }

    private List<ItemStack> pool(ExploreChestKind kind) {
        return showcase.computeIfAbsent(kind, ExploreChestLoot::showcase);
    }

    private void burst(Player player, Location at, ExploreChestKind kind) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Particle spark = switch (kind) {
            case RARE -> Particle.WAX_OFF;
            case EPIC -> Particle.WITCH;
            case LEGENDARY -> Particle.WAX_ON;
            case MYTHIC -> Particle.END_ROD;
        };
        world.spawnParticle(spark, at, 10, 0.28, 0.28, 0.28, 0.02);
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.2, 0.25, 0.2, 0);
        player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.25f);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.35f);
    }

    private static Transformation spinPose(float yawDeg, float scale, float lift) {
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new AxisAngle4f((float) Math.toRadians(yawDeg), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        );
    }

    /** Spinner at rest: tucked inside the chest body. */
    private static Transformation parkedPose() {
        return spinPose(0f, 0.04f, -0.75f);
    }

    private static String formatLeft(long ms) {
        long totalSec = Math.max(1L, (ms + 999L) / 1000L);
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        long seconds = totalSec % 60L;
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static String key(Block block) {
        return block.getWorld().getName() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
    }

    private static Block blockOf(String id) {
        String[] parts = id.split("\\|");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
