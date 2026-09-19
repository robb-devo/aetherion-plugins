package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.item.FishingRodProgress;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loot pool, rod progress, skill XP, and treasure rolls on {@code CAUGHT_FISH}.
 * Wait / lure / strike minigame is {@link de.aetherion.fishing.FishingController}
 * when AetherionFishing is installed. The NMS wait-clamp below is only a fallback
 * if that plugin is missing.
 */
public final class FishingListener implements Listener {

    private static final int BASE_WAIT_TICKS = 20 * 14;
    private static final int MIN_WAIT_TICKS = 20;
    private static final int LURE_TICKS = 24;
    private static final double BOOSTER_CHANCE = 0.03d;
    private static final double DIVING_CHANCE = 0.02d;
    private static final double COMPACTED_CHANCE = 0.045d;
    private static final double BOSS_DROP_BASE = 0.0004d;
    private static final double BOSS_DROP_CATCH_DIV = 40.0d;
    private static final double BOSS_DROP_CAP = 0.0035d;
    /** Temporary fishing loot — rarer than Water Dragon (~35% of dragon rod chance). */
    private static final double ASCENSION_VIAL_ROD1 = 0.000007d;
    private static final double ASCENSION_VIAL_ROD2 = 0.00005d;
    private static final double ASCENSION_VIAL_ROD3 = 0.00028d;
    private static final double ASCENSION_VIAL_ROD4 = 0.0009d;
    private static final double ASCENSION_VIAL_ROD5 = 0.0022d;

    private static volatile Field waitField;
    private static volatile Field lureField;
    private static volatile Field minWaitField;
    private static volatile Field maxWaitField;
    private static volatile boolean nmsLookupDone;

    private final AetherionItems plugin;
    private final ItemManager items;
    private final ActiveEquipmentStats equipment;
    private final Map<UUID, Cast> casts = new HashMap<>();
    /** When AetherionFishing is loaded it owns wait/strike; Items only resolves loot. */
    private final boolean fishingMinigameOwnsCast;

    public FishingListener(AetherionItems plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.equipment = new ActiveEquipmentStats(items);
        this.fishingMinigameOwnsCast = Bukkit.getPluginManager().getPlugin("AetherionFishing") != null;
        if (!this.fishingMinigameOwnsCast) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (items.isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        UUID id = player.getUniqueId();
        if (fishingMinigameOwnsCast) {
            if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
                onCaught(player, event);
            }
            return;
        }
        switch (event.getState()) {
            case FISHING -> startCast(player, event.getHook());
            case LURED -> {
                // Vanilla lure phase. Keep owning the hook.
            }
            case BITE -> showBite(player, event.getHook());
            case CAUGHT_FISH -> {
                onCaught(player, event);
                clearCast(id);
            }
            case REEL_IN, IN_GROUND -> clearCast(id);
            case FAILED_ATTEMPT -> {
                Cast cast = casts.get(id);
                if (cast == null || !cast.biting) {
                    clearCast(id);
                }
            }
            default -> {
                // Keep the cast while the same hook is still out.
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearCast(event.getPlayer().getUniqueId());
    }

    private void startCast(Player player, FishHook hook) {
        if (hook == null || !hook.isValid()) {
            return;
        }
        clearCast(player.getUniqueId());
        Cast cast = new Cast(hook.getUniqueId());
        casts.put(player.getUniqueId(), cast);
        cast.ownedWait = waitTicks(player);
        applyWait(player, hook, cast.ownedWait, false);
        for (int delay = 1; delay <= 8; delay++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Cast current = casts.get(player.getUniqueId());
                if (current == null || current.biting) {
                    return;
                }
                FishHook live = resolveHook(current.hookId);
                if (live == null || !live.isValid()) {
                    return;
                }
                int cap = waitTicks(player);
                clampVanilla(live, cap);
                if (current.ownedWait > 0) {
                    applyWait(player, live, Math.min(cap, current.ownedWait), false);
                }
            }, delay);
        }
    }

    private void applyWait(Player player, FishHook hook, int wait, boolean lure) {
        hook.setSkyInfluenced(false);
        hook.setRainInfluenced(false);
        hook.setApplyLure(false);
        if (!lure) {
            int clamped = Math.max(1, Math.min(BASE_WAIT_TICKS, wait));
            hook.setMinWaitTime(1);
            hook.setMaxWaitTime(clamped);
            try {
                hook.setWaitTime(1, clamped);
            } catch (NoSuchMethodError ignored) {
            }
            hook.setWaitTime(clamped);
            setNmsWait(hook, clamped);
            setNmsMinMax(hook, 1, clamped);
        } else {
            finishWait(hook);
        }
        hook.getPersistentDataContainer().set(ItemKeys.fishingSpeed(), PersistentDataType.BYTE, (byte) 1);
    }

    private int waitTicks(Player player) {
        double speed = Math.max(0.0d, equipment.getStat(player, ItemCapability.FISHING_SPEED));
        double seconds = 14.0d - speed / 10.0d;
        int ticks = (int) Math.round(seconds * 20.0d);
        return Math.max(MIN_WAIT_TICKS, Math.min(BASE_WAIT_TICKS, ticks));
    }

    private void showBite(Player player, FishHook hook) {
        Cast cast = casts.get(player.getUniqueId());
        if (cast != null) {
            cast.biting = true;
        }
        pinHook(hook);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.55f, 1.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.18f, 1.9f);
        if (hook == null || !hook.isValid()) {
            return;
        }
        Location at = hook.getLocation().add(0, 0.12, 0);
        hook.getWorld().spawnParticle(Particle.SPLASH, at, 18, 0.18, 0.08, 0.18, 0.05);
        hook.getWorld().spawnParticle(Particle.BUBBLE, at, 10, 0.12, 0.08, 0.12, 0.02);
        hook.getWorld().playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.5f, 1.15f);
    }

    private void onCaught(Player player, PlayerFishEvent event) {
        SkillService skills = plugin.getSkills();
        if (skills != null) {
            skills.grantFromFish(player, 8);
        }
        double catchStat = Math.max(0.0d, equipment.getStat(player, ItemCapability.FISHING_CATCH));
        int fishingLevel = skills == null ? 1 : skills.fishingLevel(player);
        int extras = FishingLootPool.extraCatches(catchStat, fishingLevel);
        ItemStack caught = null;
        if (event.getCaught() instanceof Item drop) {
            ItemStack stack = drop.getItemStack();
            if (FishingLootPool.shouldReplace(stack, items)) {
                stack = FishingLootPool.rollPrimary(player, skills, catchStat);
            }
            stack = FishingLootPool.maybeUpgrade(player, items, stack, catchStat);
            drop.setItemStack(stack);
            caught = stack.clone();
        }
        if (caught != null && extras > 0) {
            for (int i = 0; i < extras; i++) {
                ItemStack extra = FishingLootPool.rollPrimary(player, skills, catchStat);
                extra = FishingLootPool.maybeUpgrade(player, items, extra, catchStat);
                give(player, extra);
            }
        }
        FishingRodProgress.grantEquipped(player, items, 8 + extras * 4);
        rollTreasure(player, catchStat, fishingLevel);
    }

    private void rollTreasure(Player player, double catchStat, int fishingLevel) {
        if (ThreadLocalRandom.current().nextDouble() < FishingLootPool.boosterChance(BOOSTER_CHANCE, catchStat)) {
            ItemStack booster = plugin.getCustomItem() == null ? null : plugin.getCustomItem().createRandomBooster();
            if (booster != null) {
                de.aetherion.items.storage.BoosterDelivery.giveReward(player, booster);
                player.sendMessage("§6A booster bit. The ocean has a sense of humor.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.4f);
            }
        }
        if (ThreadLocalRandom.current().nextDouble()
                < FishingLootPool.divingChance(DIVING_CHANCE, catchStat, fishingLevel)) {
            ItemStack piece = plugin.getCustomItem() == null ? null : plugin.getCustomItem().fishing().randomDivingPiece();
            if (piece != null) {
                give(player, piece);
                player.sendMessage("§bSomething older than the dock. A diving piece.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.25f, 1.6f);
            }
        }
        if (ThreadLocalRandom.current().nextDouble()
                < FishingLootPool.compactedCrateChance(COMPACTED_CHANCE, catchStat, fishingLevel)) {
            ItemStack crate = compactedCatch();
            if (crate != null) {
                give(player, crate);
                player.sendMessage("§bA crate from the deep. Compacted.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.55f, 0.9f);
            }
        }
        rollAscensionVial(player);
        rollBossDrop(player);
    }

    private void rollAscensionVial(Player player) {
        double chance = ascensionVialChance(player.getInventory().getItemInMainHand());
        if (chance <= 0.0d || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        CustomItem custom = plugin.getCustomItem();
        if (custom == null) {
            return;
        }
        ItemStack vial = custom.createDragonAscensionVial();
        if (vial == null || vial.getType().isAir()) {
            return;
        }
        give(player, vial);
        player.sendMessage("§5✦ Something older than the reef. A §5Dragon Ascension Vial§5.");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.75f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.55f);
    }

    private static double ascensionVialChance(ItemStack rod) {
        if (rod == null || rod.getType().isAir() || !rod.hasItemMeta()) {
            return 0.0d;
        }
        String id = rod.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.item(), PersistentDataType.STRING);
        if (id == null) {
            return 0.0d;
        }
        return switch (id.toLowerCase()) {
            case "fishing_rod" -> ASCENSION_VIAL_ROD1;
            case "fishing_rod_2" -> ASCENSION_VIAL_ROD2;
            case "fishing_rod_3" -> ASCENSION_VIAL_ROD3;
            case "fishing_rod_4" -> ASCENSION_VIAL_ROD4;
            case "fishing_rod_5" -> ASCENSION_VIAL_ROD5;
            default -> 0.0d;
        };
    }

    private void rollBossDrop(Player player) {
        CustomItem custom = plugin.getCustomItem();
        if (custom == null) {
            return;
        }
        double catchStat = Math.max(0.0d, equipment.getStat(player, ItemCapability.FISHING_CATCH));
        double chance = Math.min(BOSS_DROP_CAP, BOSS_DROP_BASE * (1.0d + catchStat / BOSS_DROP_CATCH_DIV));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        ItemStack relic = randomBossDrop(custom);
        if (relic == null) {
            return;
        }
        give(player, relic);
        player.sendMessage("§6Something that should not be in the water. A boss relic.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.15f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.7f);
    }

    private static ItemStack randomBossDrop(CustomItem custom) {
        return switch (ThreadLocalRandom.current().nextInt(10)) {
            case 0 -> custom.createSquidsBoot();
            case 1 -> custom.createWarpedBlade();
            case 2 -> custom.createBridgedAxe();
            case 3 -> custom.createAetherblade();
            case 4 -> custom.createSkuldugeryShortbow();
            case 5 -> custom.createThermalCore();
            case 6 -> custom.createVoidVacuumCharm();
            case 7 -> custom.createInsolventLedger();
            case 8 -> custom.createStaffOfTechnicalDifficulties();
            default -> custom.createPickaxeCoreOfTheBurrower();
        };
    }

    private static ItemStack compactedCatch() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(100);
        CompressedResource resource;
        if (roll < 45) {
            resource = CompressedResource.COD;
        } else if (roll < 65) {
            resource = CompressedResource.STRING;
        } else if (roll < 80) {
            resource = CompressedResource.OAK_LOG;
        } else if (roll < 92) {
            resource = CompressedResource.BONE;
        } else {
            resource = CompressedResource.COBBLESTONE;
        }
        return resource.compacted();
    }

    private void give(Player player, ItemStack item) {
        InventoryDrops.give(player, item);
    }

    private void tick() {
        if (fishingMinigameOwnsCast || casts.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Cast>> it = casts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cast> entry = it.next();
            Cast cast = entry.getValue();
            FishHook hook = resolveHook(cast.hookId);
            if (hook == null || !hook.isValid()) {
                it.remove();
                continue;
            }
            if (cast.biting) {
                pinHook(hook);
                continue;
            }
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null) {
                continue;
            }
            int cap = waitTicks(owner);
            if (cast.ownedWait < 0) {
                cast.ownedWait = cap;
            }
            clampVanilla(hook, cap);
            if (cast.ownedWait > 0) {
                cast.ownedWait--;
                int left = Math.max(0, Math.min(cap, cast.ownedWait));
                if (left > 0) {
                    applyWait(owner, hook, left, false);
                } else {
                    applyWait(owner, hook, LURE_TICKS, true);
                    cast.lureForced = true;
                }
            } else {
                applyWait(owner, hook, LURE_TICKS, true);
            }
        }
    }

    private static void clampVanilla(FishHook hook, int cap) {
        hook.setSkyInfluenced(false);
        hook.setRainInfluenced(false);
        hook.setApplyLure(false);
        int hard = Math.max(1, Math.min(BASE_WAIT_TICKS, cap));
        hook.setMinWaitTime(1);
        hook.setMaxWaitTime(hard);
        try {
            hook.setWaitTime(1, hard);
        } catch (NoSuchMethodError ignored) {
        }
        setNmsMinMax(hook, 1, hard);
        int current = Math.max(0, hook.getWaitTime());
        if (current > hard) {
            bindWaitField(hook, current);
            hook.setWaitTime(hard);
            setNmsWait(hook, hard);
        }
        try {
            int bite = hook.getTimeUntilBite();
            if (bite > LURE_TICKS) {
                hook.setTimeUntilBite(LURE_TICKS);
                setNmsLure(hook, LURE_TICKS);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Vanilla re-rolls 100-600 ticks whenever wait is 0 and lure has not started.
     * Park on 1 so the next entity tick starts lure instead of a 25-30s wait.
     */
    private static void finishWait(FishHook hook) {
        int bite = 0;
        try {
            bite = Math.max(0, hook.getTimeUntilBite());
        } catch (RuntimeException ignored) {
        }
        if (bite > 0) {
            if (bite > LURE_TICKS) {
                try {
                    hook.setTimeUntilBite(LURE_TICKS);
                } catch (RuntimeException ignored) {
                }
                setNmsLure(hook, LURE_TICKS);
            }
            return;
        }
        if (Math.max(0, hook.getWaitTime()) != 1) {
            hook.setWaitTime(1);
            setNmsWait(hook, 1);
        }
        try {
            hook.setMinLureTime(LURE_TICKS);
            hook.setMaxLureTime(LURE_TICKS);
        } catch (RuntimeException ignored) {
        }
    }

    private static void setNmsWait(FishHook hook, int ticks) {
        writeNms(hook, 0, Math.max(0, ticks));
    }

    private static void setNmsLure(FishHook hook, int ticks) {
        writeNms(hook, 1, Math.max(0, ticks));
    }

    private static void setNmsMinMax(FishHook hook, int min, int max) {
        writeNms(hook, 2, Math.max(1, min));
        writeNms(hook, 3, Math.max(1, max));
    }

    private static void writeNms(FishHook hook, int which, int value) {
        try {
            Object handle = hook.getClass().getMethod("getHandle").invoke(hook);
            resolveNms(handle.getClass());
            Field field = switch (which) {
                case 1 -> lureField;
                case 2 -> minWaitField;
                case 3 -> maxWaitField;
                default -> waitField;
            };
            if (field != null) {
                field.setInt(handle, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void bindWaitField(FishHook hook, int observed) {
        if (waitField != null || observed <= BASE_WAIT_TICKS) {
            return;
        }
        try {
            Object handle = hook.getClass().getMethod("getHandle").invoke(hook);
            resolveNms(handle.getClass());
            if (waitField != null) {
                return;
            }
            Class<?> cursor = handle.getClass();
            while (cursor != null && waitField == null) {
                for (Field field : cursor.getDeclaredFields()) {
                    if (field.getType() != int.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    if (field.getInt(handle) == observed) {
                        waitField = field;
                        return;
                    }
                }
                cursor = cursor.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveNms(Class<?> type) {
        if (waitField != null && lureField != null && minWaitField != null && maxWaitField != null) {
            nmsLookupDone = true;
            return;
        }
        if (nmsLookupDone) {
            return;
        }
        Class<?> cursor = type;
        while (cursor != null && (waitField == null || lureField == null || minWaitField == null || maxWaitField == null)) {
            for (Field field : cursor.getDeclaredFields()) {
                if (field.getType() != int.class) {
                    continue;
                }
                String name = field.getName();
                if (waitField == null && (
                        "timeUntilLured".equals(name)
                                || "waitCountdown".equals(name)
                                || "lureTime".equals(name)
                                || "field_7174".equals(name)
                )) {
                    field.setAccessible(true);
                    waitField = field;
                } else if (lureField == null && (
                        "timeUntilHooked".equals(name)
                                || "fishTravelCountdown".equals(name)
                                || "field_7172".equals(name)
                )) {
                    field.setAccessible(true);
                    lureField = field;
                } else if (minWaitField == null && "minWaitTime".equals(name)) {
                    field.setAccessible(true);
                    minWaitField = field;
                } else if (maxWaitField == null && "maxWaitTime".equals(name)) {
                    field.setAccessible(true);
                    maxWaitField = field;
                }
            }
            cursor = cursor.getSuperclass();
        }
        nmsLookupDone = true;
    }

    private static FishHook resolveHook(UUID hookId) {
        if (hookId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(hookId);
        return entity instanceof FishHook hook ? hook : null;
    }

    private static boolean inWater(FishHook hook) {
        if (hook == null || !hook.isValid()) {
            return false;
        }
        try {
            if (hook.isInOpenWater() || hook.isInWater()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
        }
        Location loc = hook.getLocation();
        return liquid(loc)
                || liquid(loc.clone().add(0, -0.35, 0))
                || liquid(loc.clone().add(0, -0.7, 0))
                || liquid(loc.clone().add(0, -1.0, 0));
    }

    private static boolean liquid(Location loc) {
        return loc.getBlock().isLiquid() || loc.getBlock().getType().name().contains("WATER");
    }

    private static void pinHook(FishHook hook) {
        if (hook == null || !hook.isValid() || !inWater(hook)) {
            return;
        }
        hook.setVelocity(new Vector(0, 0, 0));
        Location loc = hook.getLocation();
        int y = loc.getBlockY();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        World world = loc.getWorld();
        while (y < world.getMaxHeight() - 1 && world.getBlockAt(x, y, z).isLiquid()) {
            y++;
        }
        double surface = y - 0.08d;
        // Loose threshold: sub-block bobbing must not teleport every tick (visual thrash).
        if (Math.abs(loc.getY() - surface) > 0.35d) {
            loc.setY(surface);
            hook.teleport(loc);
            hook.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void clearCast(UUID id) {
        casts.remove(id);
    }


    private static final class Cast {
        private final UUID hookId;
        private boolean waitInWater;
        private boolean biting;
        private int ownedWait = -1;
        private boolean lureForced;

        private Cast(UUID hookId) {
            this.hookId = hookId;
        }
    }
}
