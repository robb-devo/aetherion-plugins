package de.aetherion.items.world;

import de.aetherion.core.AetherEntities;
import de.aetherion.core.AetherKeys;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.economy.TraderService;
import de.aetherion.items.economy.GearTraderService;
import de.aetherion.items.economy.FenceService;
import de.aetherion.items.economy.MarketService;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.entity.Display;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Habitat-flavoured wildlife, Borderlands T1–T4 hostiles, and a small
 * health label that does not punch through walls.
 */
public final class WildlifeLooks implements Listener {

    /** Borderlands surface / crypt split (Y ≤ this → Crypt T4). */
    public static final int CRYPT_CEILING_Y = 55;

    /** T1 vanilla-feel (untagged or 0). */
    public static final byte TIER_NORMAL = 0;
    /** T2 Sturdy — clearly above vanilla. */
    public static final byte TIER_STURDY = 1;
    /** T3 mini-boss (~750 HP, larger). */
    public static final byte TIER_BRUTE = 2;
    /** T4 Crypt (750–1000 HP, deep names). */
    public static final byte TIER_CRYPT = 3;

    private static final byte STURDY = TIER_STURDY;
    private static final double STURDY_CHANCE = 0.16;

    /** Borderlands compressed / compacted drop chances by tier. */
    private static final double T2_COMPRESSED = 0.04;
    private static final double T3_COMPRESSED = 0.28;
    private static final double T4_COMPRESSED = 0.08;
    private static final double T4_COMPACTED = 0.015;

    /** Surface weights: T1 lots, T2 common, T3 occasional. */
    private static final int SURFACE_T1_WEIGHT = 55;
    private static final int SURFACE_T2_WEIGHT = 30;
    private static final int SURFACE_T3_WEIGHT = 15;

    /** Around Sergeant Vex — surface spawns stay T1 only. */
    public static final double VEX_T1_RADIUS = 60.0;
    private static final double VEX_T1_RADIUS_SQ = VEX_T1_RADIUS * VEX_T1_RADIUS;
    private static volatile String vexWorld = "world";
    private static volatile double vexX = 161.5;
    private static volatile double vexY = 58.0;
    private static volatile double vexZ = 73.5;
    private static volatile long vexCacheMs = 0L;

    private static final Set<EntityType> FARM_ANIMALS = EnumSet.of(
            EntityType.COW,
            EntityType.PIG,
            EntityType.SHEEP,
            EntityType.CHICKEN,
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE,
            EntityType.MOOSHROOM
    );

    private static WildlifeLooks INSTANCE;

    private final JavaPlugin plugin;

    private WildlifeLooks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(JavaPlugin plugin) {
        WildlifeLooks looks = new WildlifeLooks(plugin);
        INSTANCE = looks;
        plugin.getServer().getPluginManager().registerEvents(looks, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, looks::purgeOrphans, 80L);
        plugin.getServer().getScheduler().runTaskLater(plugin, looks::cullManagedAnimals, 100L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, looks::tick, 40L, 40L);
    }

    public static void discard(LivingEntity entity) {
        if (INSTANCE != null) {
            INSTANCE.removeLabel(entity);
        }
    }

    /** Ore troll HP nameplate — same TextDisplay style as wildlife. */
    public static void attachOreTroll(LivingEntity entity) {
        if (INSTANCE == null || entity == null || !entity.isValid()) {
            return;
        }
        INSTANCE.attach(entity, false);
    }

    /** Soft cap so animal anchors + ambient wildlife do not pack a chunk. */
    public static final int MAX_ANIMALS_PER_CHUNK = 3;
    /** Ambient wildlife soft cap in a player's nearby bubble. */
    public static final int AMBIENT_NEARBY_CAP = 4;

    public static int animalsInChunk(org.bukkit.Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Animals && entity.isValid() && !(entity instanceof Player)) {
                count++;
            }
        }
        return count;
    }

    public static boolean chunkHasRoom(Location location) {
        return location != null
                && location.getWorld() != null
                && animalsInChunk(location.getChunk()) < MAX_ANIMALS_PER_CHUNK;
    }

    public static boolean isSturdy(Entity entity) {
        return tierOf(entity) == TIER_STURDY;
    }

    public static boolean isBrute(Entity entity) {
        return tierOf(entity) == TIER_BRUTE;
    }

    public static boolean isCrypt(Entity entity) {
        return tierOf(entity) == TIER_CRYPT;
    }

    /**
     * Fraction of player Defense ignored by this mob's hits.
     * Keeps T2 combat gear from trivialising Borderlands melee.
     */
    public static double armorPenetration(LivingEntity entity) {
        return switch (tierOf(entity)) {
            case TIER_STURDY -> 0.30d;
            case TIER_BRUTE -> 0.48d;
            case TIER_CRYPT -> 0.60d;
            default -> 0.0d;
        };
    }

    public static byte tierOf(Entity entity) {
        if (entity == null) {
            return TIER_NORMAL;
        }
        Byte tier = entity.getPersistentDataContainer().get(ItemKeys.wildlifeTier(), PersistentDataType.BYTE);
        return tier == null ? TIER_NORMAL : tier;
    }

    public static boolean hasTier(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ItemKeys.wildlifeTier(), PersistentDataType.BYTE);
    }

    /** Borderlands: Y ≤ 55 → T4 Crypt only; Vex ring → T1 only; else T1/T2/T3 by weight. */
    public static void applyBorderlandsTier(LivingEntity entity, double y) {
        applyBorderlandsTier(entity, y, null);
    }

    public static void applyBorderlandsTier(LivingEntity entity, Location at) {
        if (at == null) {
            return;
        }
        applyBorderlandsTier(entity, at.getY(), at);
    }

    public static void applyBorderlandsTier(LivingEntity entity, double y, Location at) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (y <= CRYPT_CEILING_Y) {
            applyTier(entity, TIER_CRYPT);
            return;
        }
        if (at != null && nearVexStarter(at)) {
            applyTier(entity, TIER_NORMAL);
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(SURFACE_T1_WEIGHT + SURFACE_T2_WEIGHT + SURFACE_T3_WEIGHT);
        if (roll < SURFACE_T1_WEIGHT) {
            applyTier(entity, TIER_NORMAL);
        } else if (roll < SURFACE_T1_WEIGHT + SURFACE_T2_WEIGHT) {
            applyTier(entity, TIER_STURDY);
        } else {
            applyTier(entity, TIER_BRUTE);
        }
    }

    /** Horizontal disk around Vex — soft T1-only combat for the lesson entrance. */
    public static boolean nearVexStarter(Location at) {
        return nearVex(at, VEX_T1_RADIUS_SQ);
    }

    /** Same Vex center, custom radius² (e.g. taboo bubble). */
    public static boolean nearVex(Location at, double radiusSq) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        refreshVexCenter();
        if (!at.getWorld().getName().equalsIgnoreCase(vexWorld)) {
            return false;
        }
        double dx = at.getX() - vexX;
        double dz = at.getZ() - vexZ;
        return dx * dx + dz * dz <= radiusSq;
    }

    /**
     * Soft push: teleport just outside {@code minRadius} from Vex (same Y), clear path/target.
     * Used instead of hard despawn when trash follows a player into the entrance.
     */
    public static boolean ejectFromVexRadius(LivingEntity living, double minRadius) {
        if (living == null || !living.isValid() || living.getWorld() == null) {
            return false;
        }
        refreshVexCenter();
        if (!living.getWorld().getName().equalsIgnoreCase(vexWorld)) {
            return false;
        }
        Location at = living.getLocation();
        double dx = at.getX() - vexX;
        double dz = at.getZ() - vexZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= minRadius) {
            return false;
        }
        double scale = dist < 0.2 ? 1.0 : (minRadius + 1.5) / dist;
        double nx = vexX + dx * scale;
        double nz = vexZ + dz * scale;
        if (dist < 0.2) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            nx = vexX + Math.cos(angle) * (minRadius + 1.5);
            nz = vexZ + Math.sin(angle) * (minRadius + 1.5);
        }
        Location dest = new Location(at.getWorld(), nx, at.getY(), nz, at.getYaw(), at.getPitch());
        // Keep feet on ground if possible.
        int bx = dest.getBlockX();
        int bz = dest.getBlockZ();
        int top = at.getWorld().getHighestBlockYAt(bx, bz);
        dest.setY(Math.max(at.getY(), top + 1.0));
        living.teleport(dest);
        if (living instanceof Mob mob) {
            mob.setTarget(null);
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    private static void refreshVexCenter() {
        long now = System.currentTimeMillis();
        if (now - vexCacheMs < 30_000L) {
            return;
        }
        vexCacheMs = now;
        try {
            org.bukkit.plugin.Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
            if (quests == null || !quests.isEnabled()) {
                return;
            }
            java.io.File file = new java.io.File(quests.getDataFolder(), "npcs.yml");
            if (!file.exists()) {
                return;
            }
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("npcs.vex");
            if (section == null) {
                return;
            }
            vexWorld = section.getString("world", vexWorld);
            vexX = section.getDouble("x", vexX);
            vexY = section.getDouble("y", vexY);
            vexZ = section.getDouble("z", vexZ);
        } catch (Throwable ignored) {
        }
    }

    public static void applyTier(LivingEntity entity, byte tier) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        // Daylight burn applies i-frames — player hits feel like they "miss" while flaming.
        disableSunBurn(entity);
        switch (tier) {
            case TIER_STURDY -> forceSturdy(entity);
            case TIER_BRUTE -> forceBrute(entity);
            case TIER_CRYPT -> forceCrypt(entity);
            default -> {
                entity.getPersistentDataContainer().set(ItemKeys.wildlifeTier(), PersistentDataType.BYTE, TIER_NORMAL);
                stampTitle(entity, "§7" + pretty(entity.getType()));
            }
        }
    }

    /** No sun-fire on Borderlands hostiles (Zombie/Skeleton/etc.). */
    public static void disableSunBurn(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        if (entity instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        if (entity instanceof Phantom phantom) {
            phantom.setShouldBurnInDay(false);
        }
        if (entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        // Zone / tiered Borderlands mobs never sun-burn (fire i-frames steal melee hits).
        if (!living.getPersistentDataContainer().has(ItemKeys.wildlifeTier(), PersistentDataType.BYTE)
                && !living.getPersistentDataContainer().has(ItemKeys.zoneSpawn(), PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        living.setFireTicks(0);
    }

    public static EntityType pickAnimal(World world, Location at) {
        Habitat habitat = habitatAt(world, at);
        List<EntityType> pool = switch (habitat) {
            case SPRUCE -> List.of(EntityType.FOX, EntityType.FOX, EntityType.RABBIT, EntityType.GOAT, EntityType.CHICKEN);
            case FARM -> List.of(EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.HORSE, EntityType.DONKEY);
            case MEADOW -> List.of(EntityType.SHEEP, EntityType.COW, EntityType.HORSE, EntityType.CHICKEN, EntityType.RABBIT);
            case SHORE -> List.of(EntityType.TURTLE, EntityType.RABBIT, EntityType.CHICKEN);
            case ICE -> List.of(
                    EntityType.POLAR_BEAR, EntityType.POLAR_BEAR, EntityType.POLAR_BEAR,
                    EntityType.FOX, EntityType.RABBIT, EntityType.GOAT
            );
            case DESERT -> List.of(EntityType.RABBIT, EntityType.HORSE, EntityType.CAMEL, EntityType.RABBIT);
            default -> List.of(
                    EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
                    EntityType.RABBIT, EntityType.HORSE, EntityType.GOAT
            );
        };
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static EntityType pickMob(World world, Location at) {
        Habitat habitat = habitatAt(world, at);
        List<EntityType> pool = switch (habitat) {
            case SHORE -> List.of(EntityType.DROWNED, EntityType.ZOMBIE, EntityType.SKELETON);
            case ICE -> List.of(EntityType.STRAY, EntityType.SKELETON, EntityType.ZOMBIE);
            case FARM, MEADOW -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);
            case SPRUCE -> List.of(EntityType.SPIDER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER);
            case DESERT -> List.of(EntityType.HUSK, EntityType.HUSK, EntityType.ZOMBIE, EntityType.SPIDER);
            default -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.ENDERMAN);
        };
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmInSnow(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.DEFAULT) {
            return;
        }
        if (!FARM_ANIMALS.contains(event.getEntityType())) {
            return;
        }
        if (snowy(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalCap(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        // Breeding stacks forever next to wildlife — hard no.
        if (reason == CreatureSpawnEvent.SpawnReason.BREEDING
                || reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
            event.setCancelled(true);
            return;
        }
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.DEFAULT
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            if (inEldervale(event.getLocation())) {
                // Mining island: no normal animals (pets stay via pet PDC elsewhere).
                if (!(event.getEntity() instanceof Tameable tameable && tameable.isTamed())
                        && !AetherEntities.isSystemOwned(event.getEntity())
                        && !event.getEntity().getPersistentDataContainer().has(
                                AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
                    event.setCancelled(true);
                    return;
                }
            } else if ((reason == CreatureSpawnEvent.SpawnReason.NATURAL
                    || reason == CreatureSpawnEvent.SpawnReason.DEFAULT
                    || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)
                    && !chunkHasRoom(event.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean inEldervale(Location location) {
        if (location == null) {
            return false;
        }
        try {
            if (plugin instanceof de.aetherion.items.AetherionItems items && items.getAreas() != null) {
                return items.getAreas().isType(location, de.aetherion.items.world.AreaType.ELDERVALE);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> attach(entity, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living) {
                attach(living, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !eligible(living)) {
            return;
        }
        // Health is still pre-damage during the event — paint predicted HP now,
        // then confirm next tick. Slow tick remains fallback only.
        double predicted = Math.max(0.0, living.getHealth() - event.getFinalDamage());
        refreshLabel(living, predicted);
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshLabel(living));
    }

    /**
     * Skeletons / strays ignore GENERIC_ATTACK_DAMAGE for arrows — force tier attack through.
     * Melee peers that somehow stayed on vanilla numbers get the same bump.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTieredHostileHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        LivingEntity attacker = hostileAttacker(event.getDamager());
        if (attacker == null || !hasTier(attacker)) {
            return;
        }
        AttributeInstance attack = attacker.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        double intended = attack.getValue();
        if (intended <= 1.0) {
            return;
        }
        boolean ranged = event.getDamager() instanceof Projectile;
        // Ranged always uses the stamped tier attack (bows ignore the attribute).
        // Melee: only rewrite if the hit looks stuck near vanilla.
        if (ranged || event.getDamage() + 0.01 < intended * 0.45) {
            event.setDamage(intended);
        }
    }

    /** Stamp arrow base damage when a tiered hostile shoots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTieredShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity shooter) || !hasTier(shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        AttributeInstance attack = shooter.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack == null || attack.getValue() <= 1.0) {
            return;
        }
        // Arrow damage is velocity-scaled; keep base modest so final ≈ attack attribute.
        arrow.setDamage(Math.max(2.0, attack.getValue() / 2.5));
    }

    /**
     * Borderlands poison: vanilla witch splash lasts ~45s — replace with ~5s, amplifier by tier.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTieredPoisonSplash(PotionSplashEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof LivingEntity shooter) || !hasTier(shooter)) {
            return;
        }
        if (!potionCarriesPoison(event.getPotion())) {
            return;
        }
        byte tier = tierOf(shooter);
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player player)) {
                continue;
            }
            if (event.getIntensity(affected) <= 0.0) {
                continue;
            }
            // Zero intensity so vanilla long poison does not apply; we re-apply short.
            event.setIntensity(affected, 0.0);
            applyTierPoison(player, tier);
        }
    }

    /** Cave spider bites / other ATTACK poison from tiered hostiles. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTieredPoisonEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        PotionEffect neu = event.getNewEffect();
        if (neu == null || !neu.getType().equals(PotionEffectType.POISON)) {
            return;
        }
        // Only rewrite long vanilla poison (witch ~900 ticks). Short effects we may have just applied stay.
        if (neu.getDuration() <= POISON_TICKS + 20) {
            return;
        }
        LivingEntity source = recentTieredHostileNear(player);
        if (source == null) {
            return;
        }
        event.setCancelled(true);
        applyTierPoison(player, tierOf(source));
    }

    private static final int POISON_TICKS = 100; // 5 seconds

    private static void applyTierPoison(Player player, byte tier) {
        if (player == null || !player.isValid()) {
            return;
        }
        int amplifier = switch (tier) {
            case TIER_STURDY -> 1;
            case TIER_BRUTE -> 2;
            case TIER_CRYPT -> 3;
            default -> 0;
        };
        player.removePotionEffect(PotionEffectType.POISON);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.POISON,
                POISON_TICKS,
                amplifier,
                false,
                true,
                true
        ));
    }

    private static boolean potionCarriesPoison(ThrownPotion potion) {
        if (potion == null) {
            return false;
        }
        for (PotionEffect effect : potion.getEffects()) {
            if (effect != null && effect.getType().equals(PotionEffectType.POISON)) {
                return true;
            }
        }
        if (potion.getItem().getItemMeta() instanceof PotionMeta meta) {
            try {
                PotionType base = meta.getBasePotionType();
                if (base != null) {
                    String name = base.name();
                    return name.contains("POISON");
                }
            } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            }
        }
        return false;
    }

    private static LivingEntity hostileAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** Nearest tiered hostile within 12 blocks — used when effect events lack a shooter. */
    private static LivingEntity recentTieredHostileNear(Player player) {
        LivingEntity best = null;
        double bestDist = 12.0 * 12.0;
        for (Entity nearby : player.getNearbyEntities(12.0, 12.0, 12.0)) {
            if (!(nearby instanceof LivingEntity living) || !hasTier(living)) {
                continue;
            }
            if (!(living instanceof Monster) && living.getType() != EntityType.WITCH) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(player.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refreshLabel(living));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        removeLabel(entity);
        if (entity.getKiller() == null) {
            return;
        }
        if (isBorderlandsHost(entity)) {
            applyBorderlandsLoot(event);
            return;
        }
        if (isAnimalPrey(entity)) {
            applyAnimalLoot(event);
            return;
        }
        if (!isSturdy(entity)) {
            return;
        }
        // Non-animal sturdy leftovers (rare) — light bonus on whatever vanilla dropped.
        List<org.bukkit.inventory.ItemStack> extra = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.55) {
                org.bukkit.inventory.ItemStack copy = drop.clone();
                copy.setAmount(Math.max(1, (int) Math.ceil(copy.getAmount() * 0.5)));
                extra.add(copy);
            }
            if (ThreadLocalRandom.current().nextDouble() < T2_COMPRESSED) {
                CompressedResource resource = CompressedResource.fromDrop(drop.getType());
                if (resource != null) {
                    extra.add(resource.compressed());
                }
            }
        }
        event.getDrops().addAll(extra);
        event.setDroppedExp(Math.max(event.getDroppedExp() + 3, (int) Math.round(event.getDroppedExp() * 1.4)));
    }

    /** Passive animals / wildlife prey — every kill should pay something out. */
    private static boolean isAnimalPrey(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Animals || entity instanceof org.bukkit.entity.Turtle) {
            return true;
        }
        return FARM_ANIMALS.contains(entity.getType())
                || switch (entity.getType()) {
                    case RABBIT, FOX, GOAT, CAMEL, POLAR_BEAR, LLAMA, TRADER_LLAMA, CAT, WOLF, OCELOT,
                            PANDA, SNIFFER, ARMADILLO -> true;
                    default -> false;
                };
    }

    /**
     * Guaranteed vanilla-ish staples; Sturdy pays more (and sometimes compressed leather).
     */
    private void applyAnimalLoot(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        boolean sturdy = isSturdy(entity);
        boolean cooked = entity.getFireTicks() > 0;
        if (entity.getLastDamageCause() != null) {
            var cause = entity.getLastDamageCause().getCause();
            cooked = cooked
                    || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE
                    || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA
                    || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.HOT_FLOOR;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<org.bukkit.inventory.ItemStack> loot = animalLootStacks(entity.getType(), sturdy, cooked, rng);
        if (loot.isEmpty()) {
            // Fallback so nothing feels empty.
            loot.add(new org.bukkit.inventory.ItemStack(Material.LEATHER, sturdy ? 2 : 1));
        }
        event.getDrops().clear();
        event.getDrops().addAll(loot);
        if (sturdy && rng.nextDouble() < T2_COMPRESSED) {
            CompressedResource resource = CompressedResource.fromDrop(Material.LEATHER);
            if (resource != null) {
                event.getDrops().add(resource.compressed());
            }
        }
        int baseExp = Math.max(1, event.getDroppedExp());
        event.setDroppedExp(sturdy
                ? Math.max(baseExp + 4, (int) Math.round(baseExp * 1.6))
                : Math.max(baseExp, 1 + rng.nextInt(3)));
    }

    private static List<org.bukkit.inventory.ItemStack> animalLootStacks(
            EntityType type,
            boolean sturdy,
            boolean cooked,
            ThreadLocalRandom rng
    ) {
        List<org.bukkit.inventory.ItemStack> out = new ArrayList<>();
        int n = sturdy ? 2 : 1;
        switch (type) {
            case COW, MOOSHROOM -> {
                out.add(stack(cooked ? Material.COOKED_BEEF : Material.BEEF, n + rng.nextInt(sturdy ? 3 : 2)));
                out.add(stack(Material.LEATHER, sturdy ? 1 + rng.nextInt(3) : rng.nextInt(2)));
            }
            case PIG -> out.add(stack(cooked ? Material.COOKED_PORKCHOP : Material.PORKCHOP,
                    n + rng.nextInt(sturdy ? 3 : 2)));
            case SHEEP -> {
                out.add(stack(cooked ? Material.COOKED_MUTTON : Material.MUTTON, n + rng.nextInt(2)));
                out.add(stack(Material.WHITE_WOOL, sturdy ? 1 + rng.nextInt(2) : 1));
            }
            case CHICKEN -> {
                out.add(stack(cooked ? Material.COOKED_CHICKEN : Material.CHICKEN, 1));
                out.add(stack(Material.FEATHER, sturdy ? 1 + rng.nextInt(3) : rng.nextInt(3)));
            }
            case HORSE, DONKEY, MULE -> out.add(stack(Material.LEATHER, sturdy ? 2 + rng.nextInt(3) : 1 + rng.nextInt(2)));
            case RABBIT -> {
                out.add(stack(cooked ? Material.COOKED_RABBIT : Material.RABBIT, 1));
                if (sturdy || rng.nextBoolean()) {
                    out.add(stack(Material.RABBIT_HIDE, sturdy ? 1 + rng.nextInt(2) : 1));
                }
            }
            case GOAT -> {
                out.add(stack(cooked ? Material.COOKED_MUTTON : Material.MUTTON, n));
                out.add(stack(Material.LEATHER, sturdy ? 1 + rng.nextInt(2) : rng.nextInt(2)));
            }
            case FOX -> out.add(stack(Material.LEATHER, sturdy ? 1 + rng.nextInt(2) : 1));
            case CAMEL -> out.add(stack(Material.LEATHER, sturdy ? 2 + rng.nextInt(3) : 1 + rng.nextInt(2)));
            case TURTLE -> {
                out.add(stack(Material.SEAGRASS, 1 + rng.nextInt(sturdy ? 3 : 2)));
                if (sturdy || rng.nextDouble() < 0.35) {
                    out.add(stack(Material.TURTLE_SCUTE, 1));
                }
            }
            case POLAR_BEAR -> {
                out.add(stack(Material.COD, n + rng.nextInt(sturdy ? 3 : 2)));
                out.add(stack(Material.LEATHER, sturdy ? 1 + rng.nextInt(2) : rng.nextInt(2)));
            }
            case LLAMA, TRADER_LLAMA -> out.add(stack(Material.LEATHER, sturdy ? 2 + rng.nextInt(2) : 1));
            case CAT, OCELOT -> out.add(stack(Material.STRING, sturdy ? 1 + rng.nextInt(2) : 1));
            case WOLF -> out.add(stack(Material.BONE, sturdy ? 1 + rng.nextInt(2) : 1));
            case PANDA -> out.add(stack(Material.BAMBOO, n + rng.nextInt(sturdy ? 4 : 2)));
            case SNIFFER -> out.add(stack(Material.MOSS_BLOCK, sturdy ? 1 + rng.nextInt(2) : 1));
            case ARMADILLO -> out.add(stack(Material.ARMADILLO_SCUTE, sturdy ? 1 + rng.nextInt(2) : 1));
            default -> {
                if (type != null && type != EntityType.PLAYER) {
                    out.add(stack(Material.LEATHER, sturdy ? 2 : 1));
                }
            }
        }
        out.removeIf(stack -> stack == null || stack.getType().isAir() || stack.getAmount() <= 0);
        return out;
    }

    private static org.bukkit.inventory.ItemStack stack(Material material, int amount) {
        if (material == null || material.isAir() || amount <= 0) {
            return new org.bukkit.inventory.ItemStack(Material.AIR);
        }
        return new org.bukkit.inventory.ItemStack(material, Math.min(64, amount));
    }

    private boolean isBorderlandsHost(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        byte tier = tierOf(entity);
        if (tier == TIER_STURDY || tier == TIER_BRUTE || tier == TIER_CRYPT) {
            Location at = entity.getLocation();
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items != null && items.getMobZones() != null && items.getMobZones().containsBorderlands(at)) {
                return true;
            }
            // Tiered hostiles outside the disk still use Borderlands loot rules.
            return entity.getPersistentDataContainer().has(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
        }
        de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
        return items != null
                && items.getMobZones() != null
                && items.getMobZones().containsBorderlands(entity.getLocation())
                && entity.getPersistentDataContainer().has(ItemKeys.zoneSpawn(), PersistentDataType.STRING);
    }

    /**
     * Bones / rotten flesh / string (etc.) by mob type, scaled by Borderlands tier.
     * Runs at HIGH so {@link de.aetherion.items.listener.PickupDropsListener} can vacuum afterward.
     */
    private void applyBorderlandsLoot(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        byte tier = tierOf(entity);
        Material dropType = primaryLoot(entity.getType());
        if (dropType == null) {
            return;
        }
        int amount = lootAmount(tier);
        event.getDrops().removeIf(stack -> stack != null && isCombatJunk(stack.getType()));
        // Ensure the staple resource is present at the tier amount.
        event.getDrops().removeIf(stack -> stack != null && stack.getType() == dropType);
        event.getDrops().add(new ItemStack(dropType, amount));

        CompressedResource resource = CompressedResource.fromDrop(dropType);
        if (resource == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double compressedChance = switch (tier) {
            case TIER_STURDY -> T2_COMPRESSED;
            case TIER_BRUTE -> T3_COMPRESSED;
            case TIER_CRYPT -> T4_COMPRESSED;
            default -> 0.0d;
        };
        if (compressedChance > 0.0d && rng.nextDouble() < compressedChance) {
            event.getDrops().add(resource.compressed());
        }
        // Compacted only on T4, and never on T3.
        if (tier == TIER_CRYPT && rng.nextDouble() < T4_COMPACTED) {
            event.getDrops().add(resource.compacted());
        }
        int exp = event.getDroppedExp();
        event.setDroppedExp(switch (tier) {
            case TIER_STURDY -> Math.max(exp + 2, (int) Math.round(exp * 1.25));
            case TIER_BRUTE -> Math.max(exp + 8, (int) Math.round(exp * 1.75));
            case TIER_CRYPT -> Math.max(exp + 5, (int) Math.round(exp * 1.4));
            default -> exp;
        });
    }

    private static boolean isCombatJunk(Material material) {
        return material == Material.IRON_SHOVEL
                || material == Material.IRON_SWORD
                || material == Material.IRON_AXE
                || material == Material.BOW
                || material == Material.CROSSBOW
                || material == Material.ARROW
                || material == Material.TIPPED_ARROW
                || material.name().endsWith("_HELMET")
                || material.name().endsWith("_CHESTPLATE")
                || material.name().endsWith("_LEGGINGS")
                || material.name().endsWith("_BOOTS");
    }

    private static Material primaryLoot(EntityType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SKELETON, STRAY, WITHER_SKELETON, BOGGED -> Material.BONE;
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> Material.ROTTEN_FLESH;
            case SPIDER, CAVE_SPIDER -> Material.STRING;
            case CREEPER -> Material.GUNPOWDER;
            case WITCH -> Material.REDSTONE;
            case PILLAGER, VINDICATOR -> Material.BONE;
            default -> Material.ROTTEN_FLESH;
        };
    }

    /** Vanilla-ish amounts, scaled up for higher Borderlands tiers. */
    private static int lootAmount(byte tier) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return switch (tier) {
            case TIER_STURDY -> 2 + rng.nextInt(3);       // 2–4
            case TIER_BRUTE -> 4 + rng.nextInt(4);        // 4–7 solid
            case TIER_CRYPT -> 3 + rng.nextInt(3);        // 3–5 (a bit more than T2)
            default -> 1 + rng.nextInt(2);               // T1: 1–2 vanilla feel
        };
    }

    private void attach(LivingEntity entity, boolean rollSturdy) {
        if (AetherEntities.isBoss(entity) || AetherEntities.isSystemOwned(entity)) {
            return;
        }
        if (de.aetherion.items.mining.OreTrollListener.isOreTroll((org.bukkit.entity.Entity) entity)) {
            stampTitle(entity, "§6" + de.aetherion.items.mining.OreTrollListener.DISPLAY);
            entity.customName(null);
            entity.setCustomNameVisible(false);
            refreshLabel(entity);
            return;
        }
        if (!eligible(entity)) {
            return;
        }
        if (shouldDespawnFarmInSnow(entity)) {
            removeLabel(entity);
            entity.remove();
            return;
        }
        entity.setInvisible(false);
        if (hasTier(entity)
                || entity.getPersistentDataContainer().has(ItemKeys.zoneSpawn(), PersistentDataType.STRING)) {
            disableSunBurn(entity);
        }
        if (rollSturdy && !hasTier(entity) && ThreadLocalRandom.current().nextDouble() < STURDY_CHANCE) {
            applySturdy(entity);
        }
        rememberTitle(entity);
        refreshLabel(entity);
    }

    private boolean eligible(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        if (!(entity instanceof Mob)) {
            return false;
        }
        if (entity instanceof Player
                || entity instanceof ArmorStand
                || entity instanceof Villager
                || entity instanceof WanderingTrader
                || entity instanceof Boss
                || entity instanceof Wither
                || entity instanceof EnderDragon
                || entity instanceof Warden
                || entity instanceof ElderGuardian) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (entity.hasMetadata("NPC")
                || entity.getScoreboardTags().contains("dungeon_keeper")
                || entity.getScoreboardTags().contains("NPC")) {
            return false;
        }
        World world = entity.getWorld();
        if (world != null) {
            String name = world.getName().toLowerCase(Locale.ROOT);
            if (name.equals("aether_guilds") || name.startsWith("aether_guild")
                    || name.equals("aether_islands") || name.startsWith("aether_island")
                    || name.equals("aether_test") || name.startsWith("aether_test_")) {
                return false;
            }
        }
        if (entity.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(AetherKeys.SET_MINION, PersistentDataType.BYTE)) {
            return false;
        }
        if (AetherEntities.isSystemOwned(entity)) {
            return false;
        }
        // Ore trolls use the same HP nameplate once titled in attach().
        if (de.aetherion.items.mining.OreTrollListener.isOreTroll((org.bukkit.entity.Entity) entity)) {
            return entity.getPersistentDataContainer().has(ItemKeys.wildlifeTitle(), PersistentDataType.STRING);
        }
        return !TraderService.isTrader(entity)
                && !GearTraderService.isGearTrader(entity)
                && !FenceService.isFence(entity)
                && !MarketService.isMarketNpc(entity);
    }

    private void applySturdy(LivingEntity entity) {
        forceSturdy(entity);
        dress(entity);
    }

    /** T2 Sturdy — 100–200 HP, hits hard enough that T2 armor still feels it. */
    public static void forceSturdy(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (tierOf(entity) == TIER_STURDY) {
            return;
        }
        entity.getPersistentDataContainer().set(ItemKeys.wildlifeTier(), PersistentDataType.BYTE, TIER_STURDY);
        scaleEntity(entity, 1.12);
        double hp = 100.0 + ThreadLocalRandom.current().nextDouble() * 100.0;
        setHealth(entity, hp);
        setAttack(entity, 29.0 + ThreadLocalRandom.current().nextDouble() * 8.0);
        bumpArmor(entity, 3.0);
        stampTitle(entity, "§6Sturdy " + pretty(entity.getType()));
        if (INSTANCE != null) {
            INSTANCE.dress(entity);
            INSTANCE.refreshLabel(entity);
        }
    }

    /** Eldervale deep miners — Sturdy stats, pickaxe, custom names. */
    public static void applyEldervaleMiner(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        forceSturdy(entity);
        String title = eldervaleMinerName(entity.getType());
        stampTitle(entity, title);
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
            equipment.setItemInMainHandDropChance(0f);
        }
        if (INSTANCE != null) {
            INSTANCE.refreshLabel(entity);
        }
    }

    private static String eldervaleMinerName(EntityType type) {
        return switch (type) {
            case ZOMBIE, DROWNED -> "§6Rotten Miner";
            case SKELETON, STRAY -> "§6Cave Scrapper";
            case HUSK -> "§6Dust Digger";
            default -> "§6Deep Miner";
        };
    }

    /** T3 mini-boss — ~750 HP, larger, not named Sturdy. */
    public static void forceBrute(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.getPersistentDataContainer().set(ItemKeys.wildlifeTier(), PersistentDataType.BYTE, TIER_BRUTE);
        scaleEntity(entity, 1.30);
        setHealth(entity, 750.0);
        setAttack(entity, 52.0 + ThreadLocalRandom.current().nextDouble() * 12.0);
        bumpArmor(entity, 6.0);
        stampTitle(entity, bruteName(entity.getType()));
        if (INSTANCE != null) {
            INSTANCE.dress(entity);
            INSTANCE.refreshLabel(entity);
        }
    }

    /** T4 Crypt — 750–1000 HP, deep names, later-game. */
    public static void forceCrypt(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.getPersistentDataContainer().set(ItemKeys.wildlifeTier(), PersistentDataType.BYTE, TIER_CRYPT);
        scaleEntity(entity, 1.42);
        double hp = 750.0 + ThreadLocalRandom.current().nextDouble() * 250.0;
        setHealth(entity, hp);
        setAttack(entity, 68.0 + ThreadLocalRandom.current().nextDouble() * 14.0);
        bumpArmor(entity, 8.0);
        stampTitle(entity, cryptName(entity.getType()));
        if (INSTANCE != null) {
            INSTANCE.dress(entity);
            INSTANCE.refreshLabel(entity);
        }
    }

    private static void stampTitle(LivingEntity entity, String title) {
        if (entity == null || title == null || title.isBlank()) {
            return;
        }
        entity.getPersistentDataContainer().set(ItemKeys.wildlifeTitle(), PersistentDataType.STRING, title);
    }

    private static void bumpHealth(LivingEntity entity, double multiplier, double floor) {
        AttributeInstance health = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health == null) {
            return;
        }
        double next = Math.max(floor, health.getBaseValue() * multiplier);
        health.setBaseValue(next);
        entity.setHealth(Math.min(next, entity.getMaxHealth()));
    }

    private static void setHealth(LivingEntity entity, double amount) {
        AttributeInstance health = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health == null) {
            return;
        }
        double next = Math.max(1.0, amount);
        health.setBaseValue(next);
        entity.setHealth(Math.min(next, entity.getMaxHealth()));
    }

    private static void bumpAttack(LivingEntity entity, double multiplier, double floor) {
        AttributeInstance attack = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        attack.setBaseValue(Math.max(floor, attack.getBaseValue() * multiplier));
    }

    private static void setAttack(LivingEntity entity, double amount) {
        AttributeInstance attack = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        attack.setBaseValue(Math.max(1.0, amount));
    }

    private static void bumpArmor(LivingEntity entity, double amount) {
        AttributeInstance armor = entity.getAttribute(Attribute.GENERIC_ARMOR);
        if (armor == null) {
            return;
        }
        armor.setBaseValue(Math.max(armor.getBaseValue(), amount));
    }

    private static void scaleEntity(LivingEntity entity, double value) {
        try {
            Attribute attribute = Attribute.valueOf("GENERIC_SCALE");
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static String bruteName(EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED -> "§cAsh Brute";
            case SKELETON, STRAY -> "§cBone Reaver";
            case SPIDER, CAVE_SPIDER -> "§cWidow Titan";
            case CREEPER -> "§cBlast Hulk";
            case WITCH -> "§cCoven Matron";
            case PILLAGER, VINDICATOR -> "§cRaid Champion";
            default -> "§cBorder Brute";
        };
    }

    private static String cryptName(EntityType type) {
        return switch (type) {
            case HUSK -> "§5Crypt Husk";
            case ZOMBIE, DROWNED -> "§5Deep Relic";
            case SKELETON, STRAY -> "§5Vault Bones";
            case SPIDER, CAVE_SPIDER -> "§5Crypt Weaver";
            case CREEPER -> "§5Catacomb Charge";
            case WITCH -> "§5Crypt Crone";
            case PILLAGER -> "§5Deep Raider";
            case VINDICATOR -> "§5Crypt Cleaver";
            default -> "§5Crypt Stalker";
        };
    }

    private void dress(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        EntityType type = entity.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            equipment.setHelmet(leather(Material.LEATHER_HELMET, Color.fromRGB(90, 70, 40)));
            equipment.setHelmetDropChance(0f);
        } else if (type == EntityType.SKELETON || type == EntityType.STRAY) {
            equipment.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.fromRGB(70, 70, 80)));
            equipment.setChestplateDropChance(0f);
        } else if (type == EntityType.SPIDER) {
            entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false, false
            ));
        }
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void scale(LivingEntity entity, double value) {
        scaleEntity(entity, value);
    }

    private void rememberTitle(LivingEntity entity) {
        var data = entity.getPersistentDataContainer();
        String stored = data.get(ItemKeys.wildlifeTitle(), PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            return;
        }
        String name = "";
        if (entity.customName() != null) {
            name = LegacyComponentSerializer.legacySection().serialize(entity.customName());
        } else if (entity.getCustomName() != null) {
            name = entity.getCustomName();
        }
        name = stripHp(name);
        byte tier = tierOf(entity);
        if (name.isBlank()) {
            if (entity.getType() == EntityType.POLAR_BEAR && tier == TIER_STURDY) {
                name = "§6Hungry Polar Bear";
            } else if (tier == TIER_BRUTE) {
                name = bruteName(entity.getType());
            } else if (tier == TIER_CRYPT) {
                name = cryptName(entity.getType());
            } else if (tier == TIER_STURDY) {
                name = "§6Sturdy " + pretty(entity.getType());
            } else {
                name = "§7" + pretty(entity.getType());
            }
        } else if (tier == TIER_STURDY && entity.getType() == EntityType.POLAR_BEAR
                && !name.toLowerCase(Locale.ROOT).contains("hungry")) {
            name = "§6Hungry Polar Bear";
        }
        data.set(ItemKeys.wildlifeTitle(), PersistentDataType.STRING, name);
    }

    private static String stripHp(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("(?i)\\s*§c\\d+§8/§7\\d+\\s*$", "")
                .replaceAll("\\s+\\d+\\s*/\\s*\\d+\\s*$", "")
                .trim();
    }

    private void removeLabel(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().remove(ItemKeys.wildlifeLabel());
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof TextDisplay
                    && passenger.getPersistentDataContainer().has(ItemKeys.wildlifeLabel(), PersistentDataType.STRING)) {
                passenger.remove();
            }
        }
    }

    private void refreshLabel(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        refreshLabel(entity, entity.getHealth());
    }

    private void refreshLabel(LivingEntity entity, double healthNow) {
        if (!eligible(entity)) {
            return;
        }
        rememberTitle(entity);
        String title = entity.getPersistentDataContainer().get(ItemKeys.wildlifeTitle(), PersistentDataType.STRING);
        if (title == null || title.isBlank()) {
            return;
        }
        int current = Math.max(0, (int) Math.ceil(healthNow));
        int max = Math.max(1, (int) Math.ceil(entity.getMaxHealth()));
        String text = title + " §c" + current + "§8/§7" + max;
        entity.customName(null);
        entity.setCustomNameVisible(false);
        TextDisplay label = labelOf(entity);
        if (label == null || !label.isValid()) {
            label = spawnLabel(entity, text);
        } else {
            label.text(LegacyComponentSerializer.legacySection().deserialize(text));
            label.setSeeThrough(false);
            // Passenger attach is already near the head — keep a tiny lift only.
            label.setTransformation(labelTransform(entity));
        }
    }

    private TextDisplay labelOf(LivingEntity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(ItemKeys.wildlifeLabel(), PersistentDataType.STRING)) {
                return display;
            }
        }
        return null;
    }

    private TextDisplay spawnLabel(LivingEntity entity, String text) {
        World world = entity.getWorld();
        if (world == null) {
            return null;
        }
        TextDisplay label = world.spawn(entity.getLocation(), TextDisplay.class, display -> {
            display.text(LegacyComponentSerializer.legacySection().deserialize(text));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBackgroundColor(Color.fromARGB(90, 12, 10, 8));
            display.setPersistent(false);
            display.setGravity(false);
            display.setTransformation(labelTransform(entity));
            display.getPersistentDataContainer().set(
                    ItemKeys.wildlifeLabel(),
                    PersistentDataType.STRING,
                    entity.getUniqueId().toString()
            );
        });
        entity.addPassenger(label);
        return label;
    }

    /**
     * TextDisplay rides as a passenger (attach ≈ head). Extra Y must stay tiny —
     * {@code getHeight()} here stacked a second full body height and floated names sky-high.
     */
    private static Transformation labelTransform(LivingEntity entity) {
        float lift = 0.22f;
        if (entity != null && entity.getHeight() < 1.0) {
            lift = 0.16f;
        }
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf(),
                new Vector3f(0.85f, 0.85f, 0.85f),
                new Quaternionf()
        );
    }

    private boolean shouldDespawnFarmInSnow(LivingEntity entity) {
        if (entity == null || !FARM_ANIMALS.contains(entity.getType())) {
            return false;
        }
        if (entity.isLeashed()) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return snowy(entity.getLocation());
    }

    private void tick() {
        // Player bubbles only — far entities are not visible and cost full-world scans.
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world == null) {
                continue;
            }
            releaseLeakedAmbient(player);
            Location at = player.getLocation();
            for (Entity nearby : world.getNearbyEntities(at, 48, 28, 48)) {
                if (nearby instanceof LivingEntity living && eligible(living)) {
                    refreshLabel(living);
                } else if (nearby instanceof TextDisplay display && shouldDropLabel(display)) {
                    nearby.remove();
                }
            }
        }
        populate();
    }

    /**
     * Ambient wildlife used to stay saved ({@code setPersistent} defaults true on animals)
     * even with remove-when-far-away. Capital is one bubble so the nearby cap held.
     * Mine and Forage are large: each new bubble spawned another herd, and underground
     * players never counted the surface animals above them, so the cap never tripped.
     * Only animals this plugin tagged as runtime ambient are trimmed — zone herds,
     * tames, and untagged vanilla animals stay.
     */
    private void releaseLeakedAmbient(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location at = player.getLocation();
        List<Animals> leaked = new ArrayList<>();
        // Tall box: miners stand far below the surface herd the old spawner left.
        for (Entity nearby : world.getNearbyEntities(at, 48, 96, 48)) {
            if (!(nearby instanceof Animals animal) || !isRuntimeAmbient(animal)) {
                continue;
            }
            animal.setPersistent(false);
            animal.setRemoveWhenFarAway(true);
            leaked.add(animal);
        }
        if (leaked.size() <= AMBIENT_NEARBY_CAP) {
            return;
        }
        leaked.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(at),
                b.getLocation().distanceSquared(at)
        ));
        for (int i = AMBIENT_NEARBY_CAP; i < leaked.size(); i++) {
            Animals extra = leaked.get(i);
            discard(extra);
            extra.remove();
        }
    }

    private static boolean isRuntimeAmbient(Animals animal) {
        if (animal == null || !animal.isValid() || animal instanceof Player) {
            return false;
        }
        if (animal instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (AetherEntities.isSystemOwned(animal) || AetherEntities.isBoss(animal)) {
            return false;
        }
        var data = animal.getPersistentDataContainer();
        if (data.has(ItemKeys.zoneSpawn(), PersistentDataType.STRING)) {
            return false;
        }
        if (data.has(ItemKeys.ambientRuntime(), PersistentDataType.BYTE)) {
            return true;
        }
        // Older ambient spawns: titled by this plugin and flagged to despawn, but
        // persistence was left on so they never actually left.
        return animal.getRemoveWhenFarAway()
                && data.has(ItemKeys.wildlifeTitle(), PersistentDataType.STRING);
    }

    private boolean shouldDropLabel(TextDisplay display) {
        if (display == null || !display.isValid()) {
            return false;
        }
        if (display.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
            return false;
        }
        Entity vehicle = display.getVehicle();
        if (display.getPersistentDataContainer().has(ItemKeys.wildlifeLabel(), PersistentDataType.STRING)) {
            return !(vehicle instanceof LivingEntity living) || !eligible(living);
        }
        return isStrayHpLabel(display) && vehicle == null;
    }

    private int populatePulse;

    private void populate() {
        populatePulse++;
        if (populatePulse % 8 != 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (!isLivingOverworld(world)) {
                continue;
            }
            Location at = player.getLocation();
            if (inEldervale(at)) {
                // No ambient wildlife on Eldervale.
                continue;
            }
            int animals = 0;
            for (Entity nearby : world.getNearbyEntities(at, 42, 18, 42)) {
                if (nearby instanceof Animals) {
                    animals++;
                }
            }
            if (animals >= AMBIENT_NEARBY_CAP) {
                continue;
            }
            spawnAround(player);
        }
    }

    private void spawnAround(Player player) {
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 16 + random.nextDouble() * 18;
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z);
            // Same layer as the player. Underground mine shifts used to fill the
            // surface (outside the nearby Y window) without ever hitting the cap.
            if (Math.abs((y + 1.0) - player.getLocation().getY()) > 12.0) {
                continue;
            }
            Location ground = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            Material floor = world.getBlockAt(x, y, z).getType();
            if (!floor.isSolid() || floor.name().contains("LEAVES") || floor == Material.BARRIER) {
                continue;
            }
            if (!world.getBlockAt(x, y + 1, z).getType().isAir()
                    || !world.getBlockAt(x, y + 2, z).getType().isAir()) {
                continue;
            }
            if (!chunkHasRoom(ground)) {
                continue;
            }
            if (!world.getNearbyEntities(ground, 3.5, 3, 3.5).stream().noneMatch(entity -> entity instanceof Animals)) {
                continue;
            }
            EntityType type = pickAnimal(world, ground);
            LivingEntity spawned = (LivingEntity) world.spawnEntity(ground, type);
            // Temporary. Animals default to persistent, which ignores remove-when-far-away.
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(true);
            spawned.getPersistentDataContainer().set(
                    ItemKeys.ambientRuntime(),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            attach(spawned, true);
            return;
        }
    }

    private static boolean isLivingOverworld(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return !name.startsWith("aedun_")
                && !name.startsWith("ae_dun")
                && !name.equals("aether_guilds")
                && !name.startsWith("aether_guild")
                && !name.equals("aether_islands")
                && !name.startsWith("aether_island")
                && !name.equals("aether_test")
                && !name.startsWith("aether_test_");
    }

    private void purgeOrphans() {
        int labels = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntitiesByClass(TextDisplay.class))) {
                TextDisplay display = (TextDisplay) entity;
                if (display.getPersistentDataContainer().has(ItemKeys.wildlifeLabel(), PersistentDataType.STRING)
                        || isStrayHpLabel(display)) {
                    display.remove();
                    labels++;
                }
            }
        }
        plugin.getLogger().info("Cleared " + labels + " leftover HP labels.");
    }

    /**
     * Kill wildlife / zone animals already piled up. Keeps tamed pets and system NPCs.
     */
    private void cullManagedAnimals() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isLivingOverworld(world)) {
                continue;
            }
            for (Entity entity : List.copyOf(world.getEntitiesByClass(Animals.class))) {
                if (!(entity instanceof Animals living) || !living.isValid()) {
                    continue;
                }
                if (living instanceof Tameable tameable && tameable.isTamed()) {
                    continue;
                }
                if (AetherEntities.isSystemOwned(living) || AetherEntities.isBoss(living) || !eligible(living)) {
                    continue;
                }
                var data = living.getPersistentDataContainer();
                boolean ours = data.has(ItemKeys.wildlifeTitle(), PersistentDataType.STRING)
                        || data.has(ItemKeys.wildlifeTier(), PersistentDataType.BYTE)
                        || data.has(ItemKeys.zoneSpawn(), PersistentDataType.STRING)
                        || living.getRemoveWhenFarAway();
                if (!ours) {
                    // Still nuke overcrowded chunks of plain animals.
                    if (animalsInChunk(living.getLocation().getChunk()) <= MAX_ANIMALS_PER_CHUNK) {
                        continue;
                    }
                }
                discard(living);
                living.remove();
                removed++;
            }
            // Second pass: enforce chunk caps on whatever remains.
            for (Chunk chunk : world.getLoadedChunks()) {
                List<Animals> pack = new ArrayList<>();
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Animals animals
                            && animals.isValid()
                            && !(animals instanceof Tameable tameable && tameable.isTamed())
                            && !AetherEntities.isSystemOwned(animals)
                            && !AetherEntities.isBoss(animals)
                            && eligible(animals)) {
                        pack.add(animals);
                    }
                }
                while (pack.size() > MAX_ANIMALS_PER_CHUNK) {
                    Animals extra = pack.remove(pack.size() - 1);
                    discard(extra);
                    extra.remove();
                    removed++;
                }
            }
        }
        plugin.getLogger().info("Culled " + removed + " surplus animals.");
    }

    private boolean isStrayHpLabel(TextDisplay display) {
        if (display == null || !display.isValid()) {
            return false;
        }
        if (display.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
            return false;
        }
        String text = LegacyComponentSerializer.legacySection().serialize(display.text());
        return text.contains("§c") && text.contains("/") && display.getPassengers().isEmpty();
    }

    private static String pretty(EntityType type) {
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private enum Habitat {
        SPRUCE, FARM, MEADOW, SHORE, ICE, DESERT, DEFAULT
    }

    private static Habitat habitatAt(World world, Location at) {
        if (world == null || at == null) {
            return Habitat.DEFAULT;
        }
        String biome = biomeKey(at);
        if (snowyBiome(biome)) {
            return Habitat.ICE;
        }
        int spruce = 0;
        int farm = 0;
        int meadow = 0;
        int sand = 0;
        int waste = 0;
        int water = 0;
        int ice = 0;
        int ox = at.getBlockX();
        int oy = at.getBlockY();
        int oz = at.getBlockZ();
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                for (int dy = -2; dy <= 4; dy += 2) {
                    Material type = world.getBlockAt(ox + dx, oy + dy, oz + dz).getType();
                    String name = type.name();
                    if (name.contains("SPRUCE")) {
                        spruce++;
                    }
                    if (type == Material.WHEAT
                            || type == Material.FARMLAND
                            || type == Material.HAY_BLOCK
                            || type == Material.CARROTS
                            || type == Material.POTATOES) {
                        farm++;
                    }
                    if (type == Material.GRASS_BLOCK
                            || type == Material.SHORT_GRASS
                            || type == Material.TALL_GRASS
                            || name.endsWith("_TULIP")
                            || type == Material.DANDELION
                            || type == Material.OXEYE_DAISY) {
                        meadow++;
                    }
                    if (type == Material.SAND || type == Material.RED_SAND || type == Material.SUSPICIOUS_SAND) {
                        sand++;
                    }
                    // Origin Borderlands: brown concrete + terracotta waste fields.
                    if (name.contains("TERRACOTTA")
                            || type == Material.BROWN_CONCRETE
                            || type == Material.BROWN_CONCRETE_POWDER
                            || type == Material.ORANGE_CONCRETE
                            || type == Material.ORANGE_CONCRETE_POWDER
                            || type == Material.RED_CONCRETE
                            || type == Material.YELLOW_CONCRETE
                            || type == Material.COARSE_DIRT
                            || type == Material.ROOTED_DIRT
                            || type == Material.PACKED_MUD) {
                        waste++;
                    }
                    if (type == Material.WATER || name.contains("WATER")) {
                        water++;
                    }
                    if (type == Material.SNOW
                            || type == Material.SNOW_BLOCK
                            || type == Material.ICE
                            || type == Material.PACKED_ICE
                            || type == Material.BLUE_ICE
                            || type == Material.POWDER_SNOW) {
                        ice++;
                    }
                }
            }
        }
        boolean desertBiome = biome.contains("desert") || biome.contains("badlands");
        boolean beachBiome = biome.contains("beach") || biome.contains("shore") || biome.contains("ocean");
        int shore = 0;
        int desert = 0;
        if (beachBiome || water > 0) {
            shore = sand + (beachBiome ? 5 : 0) + Math.min(6, water);
        } else if (desertBiome) {
            desert = sand + 8;
        }
        desert += waste;
        if (biome.contains("frozen") || biome.contains("snow") || biome.contains("ice")) {
            ice += 6;
        }
        if (ice >= 3) {
            return Habitat.ICE;
        }
        if (biome.contains("taiga") || biome.contains("grove")) {
            spruce += 5;
        }
        if (biome.contains("plains") || biome.contains("meadow") || biome.contains("sunflower")) {
            meadow += 4;
        }
        farm *= 4;
        int best = 4;
        Habitat habitat = Habitat.DEFAULT;
        if (spruce > best) {
            best = spruce;
            habitat = Habitat.SPRUCE;
        }
        if (farm > best) {
            best = farm;
            habitat = Habitat.FARM;
        }
        if (meadow > best) {
            best = meadow;
            habitat = Habitat.MEADOW;
        }
        if (shore > best) {
            best = shore;
            habitat = Habitat.SHORE;
        }
        if (desert > best) {
            best = desert;
            habitat = Habitat.DESERT;
        }
        if (ice > best) {
            habitat = Habitat.ICE;
        }
        return habitat;
    }

    private static boolean snowy(Location at) {
        return snowyBiome(biomeKey(at));
    }

    private static boolean snowyBiome(String biome) {
        if (biome == null || biome.isBlank()) {
            return false;
        }
        return biome.contains("snow")
                || biome.contains("frozen")
                || biome.contains("ice")
                || biome.equals("grove");
    }

    private static String biomeKey(Location at) {
        if (at == null) {
            return "";
        }
        try {
            Biome biome = at.getBlock().getBiome();
            return biome.getKey().getKey().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
