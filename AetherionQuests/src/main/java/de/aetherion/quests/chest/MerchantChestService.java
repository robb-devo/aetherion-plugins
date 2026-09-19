package de.aetherion.quests.chest;

import de.aetherion.items.AetherionItems;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.model.QuestState;

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
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class MerchantChestService {

    public static final String QUEST_ID = "open_up";
    public static final String OBJECTIVE = "MERCHANT_CHEST";
    public static final String SPIN_TAG = "aether_merchant_spin";
    public static final String LABEL_TAG = "aether_merchant_label";

    /** Personal refresh after Open Up is turned in. */
    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(12);
    private static final double NEAR_BLOCKS = 7.5d;
    /**
     * Secret T1 boss jackpot. Not in the showcase spin — surprise only.
     * ~1 in 10_000 opens.
     */
    private static final double T1_BOSS_CHANCE = 1.0d / 10_000.0d;

    private final AetherionQuests plugin;
    private final NamespacedKey chestKey;
    private final File file;
    /** Last successful open epoch millis per player (quest sample + refreshes). */
    private final Map<UUID, Long> lastOpen = new HashMap<>();
    private final Set<UUID> unlocked = new HashSet<>();
    private final Set<String> chests = new HashSet<>();
    private final Set<String> busy = new HashSet<>();
    private final Map<String, BukkitTask> idle = new HashMap<>();
    private BukkitTask proximity;

    public MerchantChestService(AetherionQuests plugin) {
        this.plugin = plugin;
        this.chestKey = new NamespacedKey(plugin, "merchant_chest");
        this.file = new File(plugin.getDataFolder(), "merchant-chest.yml");
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
    }

    public NamespacedKey chestKey() {
        return chestKey;
    }

    /** Nearest placed sample chest to {@code from}, or null. */
    public Location nearestLocation(Location from) {
        if (from == null || from.getWorld() == null || chests.isEmpty()) {
            return null;
        }
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (String id : chests) {
            Block block = blockOf(id);
            if (block == null || !isMerchantChest(block)) {
                continue;
            }
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            if (!at.getWorld().equals(from.getWorld())) {
                continue;
            }
            double dist = at.distanceSquared(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = at;
            }
        }
        // Other-world fallback: first valid chest.
        if (best == null) {
            for (String id : chests) {
                Block block = blockOf(id);
                if (block == null || !isMerchantChest(block)) {
                    continue;
                }
                return block.getLocation().add(0.5, 0.5, 0.5);
            }
        }
        return best;
    }

    public boolean isMerchantChest(Block block) {
        if (block == null || !(block.getState() instanceof TileState state)) {
            return false;
        }
        Byte flag = state.getPersistentDataContainer().get(chestKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public void place(Player player, Block against, BlockFace face) {
        Block target = against.getRelative(face);
        if (!target.getType().isAir() && target.getType() != Material.WATER && !isMerchantChest(target)) {
            player.sendMessage("§cNeed an empty block beside that.");
            return;
        }
        if (isMerchantChest(target)) {
            ensureFx(target);
            player.sendMessage("§eThat sample chest is already placed.");
            return;
        }
        target.setType(Material.CHEST, false);
        if (target.getBlockData() instanceof Directional directional) {
            directional.setFacing(player.getFacing().getOppositeFace());
            target.setBlockData(directional, false);
        }
        if (target.getState() instanceof Chest chest) {
            chest.getSnapshotInventory().clear();
            chest.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            chest.update(true, false);
        }
        chests.add(key(target));
        save();
        ensureFx(target);
        player.sendMessage("§aPlaced the merchant sample chest. First loot during §fOpen Up§a, then every §f12h§a.");
        player.playSound(target.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 1.15f);
    }

    public void remove(Player player, Block block) {
        if (!isMerchantChest(block)) {
            return;
        }
        String id = key(block);
        clearFx(block);
        busy.remove(id);
        chests.remove(id);
        block.setType(Material.AIR, false);
        save();
        player.sendMessage("§eRemoved the merchant sample chest.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
    }

    /** @return true if this is the first unlock for the player */
    public boolean unlockFor(Player player) {
        if (player == null) {
            return false;
        }
        migrateLegacy(player);
        boolean first = unlocked.add(player.getUniqueId());
        if (first) {
            save();
        }
        return first;
    }

    public boolean isUnlocked(Player player) {
        if (player == null) {
            return false;
        }
        migrateLegacy(player);
        return unlocked.contains(player.getUniqueId());
    }

    public void tryOpen(Player player, Block block) {
        if (player == null || !isMerchantChest(block)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        migrateLegacy(player);
        if (!unlocked.contains(uuid)) {
            deny(player, "Talk to the merchant first — he unlocks these chests.");
            return;
        }
        long readyAt = lastOpen.getOrDefault(uuid, 0L) + COOLDOWN_MS;
        // First open after unlock has no wait (lastOpen missing).
        if (lastOpen.containsKey(uuid)) {
            long left = readyAt - System.currentTimeMillis();
            if (left > 0L) {
                deny(player, "Empty for now. Back in " + formatLeft(left) + ".");
                return;
            }
        }
        boolean firstSample = !lastOpen.containsKey(uuid);

        String id = key(block);
        if (busy.contains(id)) {
            player.sendMessage("§7Wait for the spin.");
            return;
        }
        ItemStack reward = rollReward();
        if (reward == null || reward.getType().isAir()) {
            player.sendMessage("§cThe sample jammed. Try again in a second.");
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
            finish(player, block, reward, label, firstSample);
            return;
        }
        spin(player, block, spinner, label, reward, firstSample);
    }

    public void restoreAll() {
        for (String id : new ArrayList<>(chests)) {
            Block block = blockOf(id);
            if (block == null) {
                continue;
            }
            if (!block.getChunk().isLoaded()) {
                continue;
            }
            if (!isMerchantChest(block)) {
                if (block.getType() != Material.CHEST) {
                    chests.remove(id);
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
            if (isMerchantChest(block)) {
                ensureFx(block);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("claimed", null);
        Map<String, Long> people = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : lastOpen.entrySet()) {
            people.put(entry.getKey().toString(), entry.getValue());
        }
        yaml.set("last-open", people);
        List<String> unlockedIds = new ArrayList<>();
        for (UUID id : unlocked) {
            unlockedIds.add(id.toString());
        }
        yaml.set("unlocked", unlockedIds);
        yaml.set("chests", new ArrayList<>(chests));
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save merchant-chest.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("last-open");
        if (section != null) {
            for (String raw : section.getKeys(false)) {
                try {
                    lastOpen.put(UUID.fromString(raw), section.getLong(raw, 0L));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        // Legacy one-shot list → treat as already opened (ready for 12h after quest turn-in).
        for (String raw : yaml.getStringList("claimed")) {
            try {
                UUID id = UUID.fromString(raw);
                lastOpen.putIfAbsent(id, 0L);
                unlocked.add(id);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String raw : yaml.getStringList("unlocked")) {
            try {
                unlocked.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Anyone who already opened the sample is considered unlocked.
        unlocked.addAll(lastOpen.keySet());
        chests.addAll(yaml.getStringList("chests"));
    }

    private void migrateLegacy(Player player) {
        if (player == null || unlocked.contains(player.getUniqueId())) {
            return;
        }
        var quest = plugin.getQuestManager().getQuest(QUEST_ID);
        boolean legacy = plugin.getQuestManager().getProgress(player, QUEST_ID, OBJECTIVE) > 0
                || plugin.getQuestManager().getProgress(player, QUEST_ID, "CHEST") > 0
                || (quest != null && plugin.getQuestManager().getQuestState(player, quest) == QuestState.COMPLETED);
        if (!legacy) {
            return;
        }
        unlocked.add(player.getUniqueId());
        lastOpen.putIfAbsent(player.getUniqueId(), 0L);
        save();
    }

    private void spin(Player player, Block block, ItemDisplay spinner, TextDisplay label, ItemStack reward, boolean questSample) {
        // Showcase pool is boosters only — jackpots stay off the carousel.
        List<ItemStack> pool = pool();
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
                    restoreLabel(label);
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
                // Jackpots stay off the reel — land on a normal booster, surprise in the payout.
                ItemStack shown = reward;
                if (isT1BossItem(reward) && !pool.isEmpty()) {
                    shown = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                }
                spinner.setItemStack(shown);
                spinner.setTransformation(spinPose(0f, 0.85f));
                burst(player, block.getLocation().add(0.5, 1.3, 0.5));
                cancel();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    finish(player, block, reward, label, questSample);
                    if (spinner.isValid()) {
                        spinner.setTransformation(spinPose(0f, 0.55f));
                    }
                }, 16L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(Player player, Block block, ItemStack reward, TextDisplay label, boolean questSample) {
        busy.remove(key(block));
        lastOpen.put(player.getUniqueId(), System.currentTimeMillis());
        unlocked.add(player.getUniqueId());
        save();
        restoreLabel(label);
        if (!player.isOnline()) {
            return;
        }
        try {
            de.aetherion.items.storage.BoosterDelivery.giveQuestReward(player, reward.clone());
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        if (isT1BossItem(reward)) {
            player.sendMessage("§5✦ The sample hiccups. Something that does not belong there.");
            player.sendMessage("§6" + nameOf(reward));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.15f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.7f);
            return;
        }
        player.sendMessage((questSample ? "§6Sample §7pays out: " : "§6Sample §7refreshes: ") + nameOf(reward));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
    }

    private void deny(Player player, String message) {
        player.sendMessage("§7" + message);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.45f, 1.35f);
    }

    private void ensureFx(Block block) {
        if (block == null || !isMerchantChest(block)) {
            return;
        }
        World world = block.getWorld();
        Location spinAt = block.getLocation().add(0.5, 1.15, 0.5);
        Location textAt = block.getLocation().add(0.5, 1.65, 0.5);
        ItemDisplay spinner = spinnerAt(block);
        TextDisplay label = labelAt(block);
        List<ItemStack> pool = pool();
        ItemStack first = pool.isEmpty() ? new ItemStack(Material.CHEST) : pool.get(0);
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
                text.text(labelCopy(null));
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
            label.text(labelCopy(null));
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
                if (spinner == null || !spinner.isValid() || !isMerchantChest(block)) {
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

    private void restoreLabel(TextDisplay label) {
        if (label == null || !label.isValid()) {
            return;
        }
        label.text(labelCopy(null));
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
            if (block == null || !block.getChunk().isLoaded() || !isMerchantChest(block)) {
                continue;
            }
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
            if (label != null && label.isValid()) {
                label.text(labelCopy(nearest));
            }
        }
    }

    private Component statusLine(Player player) {
        if (player == null) {
            return Component.text("Sample crate").color(NamedTextColor.GOLD);
        }
        migrateLegacy(player);
        if (!unlocked.contains(player.getUniqueId())) {
            return Component.text("Talk to the merchant").color(NamedTextColor.DARK_GRAY);
        }
        if (!lastOpen.containsKey(player.getUniqueId())) {
            return Component.text("Ready · right-click").color(NamedTextColor.GREEN);
        }
        long left = lastOpen.getOrDefault(player.getUniqueId(), 0L) + COOLDOWN_MS - System.currentTimeMillis();
        if (left > 0L) {
            return Component.text("Ready in " + formatLeft(left)).color(NamedTextColor.GRAY);
        }
        return Component.text("Ready · right-click").color(NamedTextColor.GREEN);
    }

    private Component labelCopy(Player viewer) {
        Component subtitle = viewer == null
                ? Component.text("Booster · every 12h").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                : statusLine(viewer).decoration(TextDecoration.ITALIC, false);
        return Component.text("Sample crate")
                .color(NamedTextColor.GOLD)
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

    private ItemStack rollReward() {
        if (ThreadLocalRandom.current().nextDouble() < T1_BOSS_CHANCE) {
            ItemStack jackpot = rollT1Boss();
            if (jackpot != null && !jackpot.getType().isAir()) {
                return jackpot;
            }
        }
        return rollBooster();
    }

    private ItemStack rollBooster() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCustomItem() == null) {
            return null;
        }
        return items.getCustomItem().createRandomBooster();
    }

    /** Hollow Lurker / Squidward uniques — never shown in the idle/spin showcase. */
    private ItemStack rollT1Boss() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCustomItem() == null) {
            return null;
        }
        var custom = items.getCustomItem();
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> custom.createWarpedBlade();
            case 1 -> custom.createHollowLongbow();
            default -> custom.createSquidsBoot();
        };
    }

    private static boolean isT1BossItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(de.aetherion.items.core.ItemKeys.item(), PersistentDataType.STRING);
        if (id == null) {
            return false;
        }
        return switch (id.toLowerCase()) {
            case "warped_blade", "hollow_longbow", "squids_boot" -> true;
            default -> false;
        };
    }

    private List<ItemStack> pool() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getCustomItem() == null) {
            return List.of(new ItemStack(Material.CHEST));
        }
        List<ItemStack> showcase = items.getCustomItem().boosterShowcase();
        return showcase.isEmpty() ? List.of(new ItemStack(Material.CHEST)) : showcase;
    }

    private void burst(Player player, Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.WAX_ON, at, 8, 0.25, 0.25, 0.25, 0);
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

    private static String nameOf(ItemStack item) {
        if (item == null) {
            return "nothing";
        }
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
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
