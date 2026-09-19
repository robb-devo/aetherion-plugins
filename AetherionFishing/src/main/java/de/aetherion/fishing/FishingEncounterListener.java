package de.aetherion.fishing;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chance to pull a weak fishing-skill encounter out of the water instead of only fish.
 * Encounters stay while the angler is nearby; alone they linger ~2 minutes.
 * Tagged via {@link AetherKeys#FISHING_ENCOUNTER} so NaturalSpawnGuard does not cull them.
 */
public final class FishingEncounterListener implements Listener {

    private static final double OWNER_RANGE = 96.0d;
    private static final double OWNER_RANGE_SQUARED = OWNER_RANGE * OWNER_RANGE;
    private static final double SURFACE_KEEP_DEPTH = 2.4d;
    /** Alone this long before despawn (~2 minutes). */
    private static final long ALONE_DESPAWN_MS = 120_000L;

    private final AetherionFishing plugin;
    private final NamespacedKey encounterKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey surfaceYKey;
    private final double baseChance;
    private final double chanceCap;
    private final Map<UUID, UUID> liveByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> aloneSince = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> liveEncounters = ConcurrentHashMap.newKeySet();
    private ActiveEquipmentStats equipment;

    FishingEncounterListener(AetherionFishing plugin) {
        this.plugin = plugin;
        this.encounterKey = AetherKeys.FISHING_ENCOUNTER;
        this.tierKey = new NamespacedKey(plugin, "encounter_tier");
        this.ownerKey = new NamespacedKey(plugin, "encounter_owner");
        this.surfaceYKey = new NamespacedKey(plugin, "encounter_surface_y");
        this.baseChance = Math.max(0.02d, Math.min(0.25d, plugin.getConfig().getDouble("encounter-chance", 0.12d)));
        this.chanceCap = Math.max(baseChance, Math.min(0.3d, plugin.getConfig().getDouble("encounter-chance-cap", 0.18d)));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickEncounters, 10L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (isDungeonWorld(player.getWorld())) {
            return;
        }
        if (hasLive(player.getUniqueId())) {
            return;
        }
        double catchStat = catchStat(player);
        double chance = Math.min(chanceCap, baseChance * (1.0d + catchStat / 220.0d));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        Location at = event.getHook() != null ? event.getHook().getLocation() : player.getLocation();
        spawn(player, at);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isEncounter(entity)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(Math.max(1, 3 + tierOf(entity)));
        Player killer = entity.getKiller();
        UUID ownerId = ownerOf(entity);
        if (killer == null && ownerId != null) {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                killer = owner;
            }
        }
        liveByPlayer.entrySet().removeIf(e -> entity.getUniqueId().equals(e.getValue()));
        liveEncounters.remove(entity.getUniqueId());
        aloneSince.remove(entity.getUniqueId());
        if (killer == null) {
            return;
        }
        FishingEncounterTier tier = FishingEncounterTier.DOCK_DREDGER;
        int band = tierOf(entity);
        for (FishingEncounterTier candidate : FishingEncounterTier.values()) {
            if (candidate.band() == band) {
                tier = candidate;
                break;
            }
        }
        grantDrops(killer, tier);
        SkillService skills = skills();
        if (skills != null) {
            skills.grantFromFish(killer, 4 + tier.lootQuality() * 2);
        }
        killer.playSound(killer.getLocation(), Sound.ENTITY_DROWNED_DEATH, 0.55f, 1.15f);
        killer.sendActionBar(net.kyori.adventure.text.Component.text(
                tier.displayName() + " sank. Something useful floated up.",
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!isEncounter(event.getEntity())) {
            return;
        }
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId == null) {
            return;
        }
        if (event.getTarget() instanceof Player player && ownerId.equals(player.getUniqueId())) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()
                && owner.getWorld() != null
                && owner.getWorld().equals(event.getEntity().getWorld())) {
            event.setTarget(owner);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Owner leaving does not wipe the mob — tickEncounters despawns after alone timeout.
        liveByPlayer.remove(event.getPlayer().getUniqueId());
    }

    void shutdown() {
        for (UUID mobId : liveEncounters) {
            Entity entity = plugin.getServer().getEntity(mobId);
            if (entity != null) {
                entity.remove();
            }
        }
        liveByPlayer.clear();
        liveEncounters.clear();
        aloneSince.clear();
    }

    private void spawn(Player player, Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        World world = at.getWorld();
        Location spot = findSpot(at);
        if (spot == null) {
            return;
        }
        int level = fishingLevel(player);
        FishingEncounterTier tier = FishingEncounterTier.forFishingLevel(level);
        Class<? extends LivingEntity> type = livingClass(tier);
        if (type == null) {
            return;
        }
        LivingEntity entity = world.spawn(spot, type, CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
            spawned.getPersistentDataContainer().set(encounterKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier.band());
            spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            spawned.getPersistentDataContainer().set(surfaceYKey, PersistentDataType.DOUBLE, spot.getY());
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(true);
            spawned.setCanPickupItems(false);
        });
        tier.dress(entity);
        forceAggro(entity, player);
        // Re-apply next tick — some AI clears target on first tick after spawn.
        plugin.getServer().getScheduler().runTask(plugin, () -> forceAggro(entity, player));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> forceAggro(entity, player), 10L);
        liveByPlayer.put(player.getUniqueId(), entity.getUniqueId());
        liveEncounters.add(entity.getUniqueId());
        aloneSince.remove(entity.getUniqueId());
        player.sendMessage("§bSomething angry took the bait. §f" + tier.displayName() + "§b.");
        player.playSound(player.getLocation(), Sound.ENTITY_DROWNED_AMBIENT, 0.7f, 0.85f);
        world.spawnParticle(Particle.SPLASH, spot.clone().add(0, 0.4, 0), 24, 0.35, 0.2, 0.35, 0.05);
        world.spawnParticle(Particle.BUBBLE, spot, 16, 0.25, 0.2, 0.25, 0.02);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends LivingEntity> livingClass(FishingEncounterTier tier) {
        Class<?> raw = tier.type().getEntityClass();
        if (raw == null || !LivingEntity.class.isAssignableFrom(raw)) {
            return null;
        }
        return (Class<? extends LivingEntity>) raw;
    }

    private static void forceAggro(LivingEntity entity, Player owner) {
        if (entity == null || !entity.isValid() || entity.isDead() || owner == null || !owner.isOnline()) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setAware(true);
            mob.setTarget(owner);
        }
    }

    private void grantDrops(Player player, FishingEncounterTier tier) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int quality = tier.lootQuality();
        give(player, new ItemStack(Material.COD, 1 + rng.nextInt(1 + quality)));
        if (quality >= 1 || rng.nextBoolean()) {
            give(player, new ItemStack(Material.SALMON, 1));
        }
        if (rng.nextDouble() < 0.45d + quality * 0.08d) {
            give(player, new ItemStack(Material.STRING, 1 + rng.nextInt(2)));
        }
        if (quality >= 1 && rng.nextDouble() < 0.55d) {
            give(player, new ItemStack(Material.BONE, 1));
        }
        if (quality >= 2 && rng.nextDouble() < 0.5d) {
            give(player, new ItemStack(Material.PRISMARINE_SHARD, 1 + rng.nextInt(2)));
        }
        if (quality >= 2 && rng.nextDouble() < 0.28d + quality * 0.05d) {
            ItemStack compressed = CompressedResource.COD.compressed();
            if (compressed != null) {
                give(player, compressed);
            }
        }
        if (quality >= 3 && rng.nextDouble() < 0.18d + quality * 0.04d) {
            ItemStack compacted = CompressedResource.SALMON.compacted();
            if (compacted != null) {
                give(player, compacted);
            }
        }
        if (quality >= 3 && rng.nextDouble() < 0.08d + quality * 0.02d) {
            AetherionItems items = AetherionItems.getInstance();
            if (items != null && items.getCustomItem() != null) {
                ItemStack diving = items.getCustomItem().fishing().randomDivingPiece();
                if (diving != null) {
                    give(player, diving);
                    player.sendMessage("§bThe deep coughed up diving gear.");
                }
            }
        }
        if (quality >= 1 && rng.nextDouble() < 0.2d) {
            give(player, new ItemStack(Material.PUFFERFISH, 1));
        }
    }

    private void give(Player player, ItemStack stack) {
        InventoryDrops.give(player, stack);
    }

    private void tickEncounters() {
        Iterator<UUID> it = liveEncounters.iterator();
        while (it.hasNext()) {
            UUID mobId = it.next();
            Entity raw = plugin.getServer().getEntity(mobId);
            if (raw == null) {
                // Chunk unload / brief lookup miss — keep tracking, do not wipe.
                continue;
            }
            if (!(raw instanceof LivingEntity entity) || !entity.isValid() || entity.isDead()) {
                aloneSince.remove(mobId);
                liveByPlayer.entrySet().removeIf(e -> mobId.equals(e.getValue()));
                it.remove();
                continue;
            }
            keepNearSurface(entity);
            Player owner = resolveOwner(entity);
            if (owner != null && inOwnerRange(entity, owner)) {
                aloneSince.remove(mobId);
                forceAggro(entity, owner);
                continue;
            }
            long now = System.currentTimeMillis();
            long since = aloneSince.computeIfAbsent(mobId, ignored -> now);
            if (now - since < ALONE_DESPAWN_MS) {
                continue;
            }
            aloneSince.remove(mobId);
            liveByPlayer.entrySet().removeIf(e -> mobId.equals(e.getValue()));
            it.remove();
            Location at = entity.getLocation();
            if (at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.6, 0), 10, 0.2, 0.3, 0.2, 0.01);
            }
            entity.remove();
        }
    }

    private Player resolveOwner(LivingEntity entity) {
        UUID ownerId = ownerOf(entity);
        if (ownerId == null) {
            return null;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || owner.getGameMode() == GameMode.SPECTATOR) {
            return null;
        }
        if (owner.getWorld() == null || !owner.getWorld().equals(entity.getWorld())) {
            return null;
        }
        return owner;
    }

    private static boolean inOwnerRange(LivingEntity entity, Player owner) {
        return entity.getLocation().distanceSquared(owner.getLocation()) <= OWNER_RANGE_SQUARED;
    }

    private void keepNearSurface(LivingEntity entity) {
        Double surface = entity.getPersistentDataContainer().get(surfaceYKey, PersistentDataType.DOUBLE);
        if (surface == null) {
            return;
        }
        Location at = entity.getLocation();
        if (at.getY() >= surface - SURFACE_KEEP_DEPTH) {
            return;
        }
        Location lift = at.clone();
        // Never lift above the recorded water surface — guardians die on dry land.
        double targetY = Math.min(surface, at.getY() + 1.15d);
        lift.setY(targetY);
        if (!lift.getBlock().isLiquid() && lift.clone().add(0, -1, 0).getBlock().isLiquid()) {
            lift.add(0, -1, 0);
        }
        Vector velocity = entity.getVelocity();
        entity.teleport(lift);
        if (velocity != null) {
            entity.setVelocity(new Vector(velocity.getX(), Math.max(0.12d, velocity.getY()), velocity.getZ()));
        }
    }

    private boolean hasLive(UUID playerId) {
        UUID mobId = liveByPlayer.get(playerId);
        if (mobId == null) {
            return false;
        }
        Entity entity = plugin.getServer().getEntity(mobId);
        if (entity == null) {
            // Likely unloaded — still count as live so we don't stack encounters.
            return liveEncounters.contains(mobId);
        }
        if (!entity.isValid() || entity.isDead()) {
            liveByPlayer.remove(playerId);
            aloneSince.remove(mobId);
            liveEncounters.remove(mobId);
            return false;
        }
        return true;
    }

    private boolean isEncounter(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(encounterKey, PersistentDataType.BYTE);
    }

    private int tierOf(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, Math.min(9, value));
    }

    private UUID ownerOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int fishingLevel(Player player) {
        SkillService skills = skills();
        return skills == null ? 1 : skills.fishingLevel(player);
    }

    private double catchStat(Player player) {
        ActiveEquipmentStats stats = equipment();
        if (stats == null || player == null) {
            return 0.0d;
        }
        return Math.max(0.0d, stats.getStat(player, ItemCapability.FISHING_CATCH));
    }

    private ActiveEquipmentStats equipment() {
        if (equipment != null) {
            return equipment;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            return null;
        }
        equipment = new ActiveEquipmentStats(items.getItemManager());
        return equipment;
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }

    private static Location findSpot(Location hook) {
        World world = hook.getWorld();
        if (world == null) {
            return null;
        }
        Location base = hook.clone();
        // Prefer a nearby water column the player can actually fight — stay IN water.
        for (int attempt = 0; attempt < 10; attempt++) {
            double ox = ThreadLocalRandom.current().nextDouble(-2.2d, 2.2d);
            double oz = ThreadLocalRandom.current().nextDouble(-2.2d, 2.2d);
            Location candidate = base.clone().add(ox, 0.0d, oz);
            candidate.setY(Math.floor(candidate.getY()) + 0.1d);
            if (!(candidate.getBlock().isLiquid()
                    || candidate.clone().add(0, -1, 0).getBlock().isLiquid())) {
                continue;
            }
            // Climb to the top liquid block, then stay inside it (guardians die on dry land).
            while (candidate.getY() < world.getMaxHeight() - 3
                    && candidate.clone().add(0, 1, 0).getBlock().isLiquid()) {
                candidate.add(0, 1, 0);
            }
            if (!candidate.getBlock().isLiquid()) {
                candidate.add(0, -1, 0);
            }
            if (candidate.getBlock().isLiquid()) {
                return candidate;
            }
        }
        Location fallback = base.clone();
        if (!fallback.getBlock().isLiquid() && fallback.clone().add(0, -1, 0).getBlock().isLiquid()) {
            fallback.add(0, -1, 0);
        }
        return fallback;
    }

    private static boolean isDungeonWorld(World world) {
        if (world == null) {
            return true;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.contains("dungeon") || name.startsWith("aether_dungeon");
    }
}
