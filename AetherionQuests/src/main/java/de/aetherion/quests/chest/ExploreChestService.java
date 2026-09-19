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
 */
public final class ExploreChestService {

    public static final String SPIN_TAG = "aether_explore_spin";
    public static final String LABEL_TAG = "aether_explore_label";

    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(12);
    private static final double NEAR_BLOCKS = 7.5d;

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
    private BukkitTask proximity;

    public ExploreChestService(AetherionQuests plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "explore_chest");
        this.file = new File(plugin.getDataFolder(), "explore-chests.yml");
        load();
        proximity = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickProximity, 20L, 10L);
    }

    public void shutdown() {
        if (proximity != null) {
            proximity.cancel();
            proximity = null;
        }
        for (String id : new ArrayList<>(idle.keySet())) {
            cancelIdle(id);
        }
        save();
    }

    public NamespacedKey kindKey() {
        return kindKey;
    }

    public ExploreChestKind kindOf(Block block) {
        if (block == null || !(block.getState() instanceof TileState state)) {
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
        target.setType(kind.block(), false);
        var data = target.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            chestData.setFacing(player.getFacing().getOppositeFace());
            target.setBlockData(chestData, false);
        } else if (data instanceof Directional directional) {
            directional.setFacing(player.getFacing().getOppositeFace());
            target.setBlockData(directional, false);
        }
        stamp(target, kind);
        String id = key(target);
        chests.add(id);
        kinds.put(id, kind);
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
            finish(player, block, kind, reward, label);
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
            if (!isExploreChest(block)) {
                if (block.getType().isAir()) {
                    chests.remove(id);
                    kinds.remove(id);
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
        new BukkitRunnable() {
            int step = 0;
            final int total = 22 + ThreadLocalRandom.current().nextInt(6);

            @Override
            public void run() {
                if (!spinner.isValid() || !player.isOnline()) {
                    busy.remove(id);
                    restoreLabel(block, label);
                    cancel();
                    return;
                }
                step++;
                if (step < total) {
                    if (!pool.isEmpty()) {
                        spinner.setItemStack(pool.get(step % pool.size()));
                    }
                    spinner.setTransformation(spinPose(step * 42f, 0.62f));
                    player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.4f + (step % 5) * 0.08f);
                    return;
                }
                spinner.setItemStack(reward.display);
                spinner.setTransformation(spinPose(0f, 0.85f));
                burst(player, block.getLocation().add(0.5, 1.3, 0.5), kind);
                cancel();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    finish(player, block, kind, reward, label);
                    if (spinner.isValid()) {
                        spinner.setTransformation(spinPose(0f, 0.55f));
                    }
                }, 16L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
        World world = block.getWorld();
        Location spinAt = block.getLocation().add(0.5, 1.15, 0.5);
        Location textAt = block.getLocation().add(0.5, 1.65, 0.5);
        ItemDisplay spinner = spinnerAt(block);
        TextDisplay label = labelAt(block);
        List<ItemStack> pool = pool(kind);
        ItemStack first = pool.isEmpty() ? new ItemStack(kind.block()) : pool.get(0);
        if (spinner == null) {
            spinner = world.spawn(spinAt, ItemDisplay.class, display -> {
                display.setItemStack(first);
                display.setPersistent(true);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowRadius(0f);
                display.setTransformation(spinPose(0f, 0.55f));
                display.addScoreboardTag(SPIN_TAG);
            });
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
        } else if (label.isValid() && !busy.contains(key(block))) {
            label.text(labelCopy(kind, null, block));
        }
        startIdle(block, spinner, pool);
    }

    private void startIdle(Block block, ItemDisplay spinner, List<ItemStack> pool) {
        String id = key(block);
        BukkitTask existing = idle.get(id);
        if (existing != null && !existing.isCancelled()) {
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (spinner == null || !spinner.isValid() || !isExploreChest(block)) {
                    cancel();
                    idle.remove(id);
                    return;
                }
                if (busy.contains(id) || pool.isEmpty()) {
                    return;
                }
                step++;
                spinner.setItemStack(pool.get(step % pool.size()));
                spinner.setTransformation(spinPose(step * 18f, 0.55f));
            }
        }.runTaskTimer(plugin, 10L, 8L);
        idle.put(id, task);
    }

    private void clearFx(Block block) {
        cancelIdle(key(block));
        Location at = block.getLocation().add(0.5, 1.4, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(at, 1.6, 2.2, 1.6)) {
            if (entity.getScoreboardTags().contains(SPIN_TAG) || entity.getScoreboardTags().contains(LABEL_TAG)) {
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

    private static Transformation spinPose(float yawDeg, float scale) {
        return new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(yawDeg), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        );
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
