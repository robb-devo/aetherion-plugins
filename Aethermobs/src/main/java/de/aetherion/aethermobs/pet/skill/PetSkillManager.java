package de.aetherion.aethermobs.pet.skill;

import de.aetherion.core.AetherEntities;
import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.ActivePetManager;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.RarityGlow;
import de.aetherion.items.model.Rarity;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PetSkillManager implements Listener {

    private static final int EFFECT_DURATION_TICKS = 80;
    private static final int NIGHT_VISION_DURATION_TICKS = 400;

    private static final int CREEPER_HIT_THRESHOLD = 5;
    private static final double CREEPER_BURST_RADIUS = 2.4;

    private static final int GUARDIAN_RANGE = 16;
    private static final int GUARDIAN_BEAM_TICKS = 12;
    private static final int HAWK_GLIDE_MAX_TICKS = 160;
    private static final int HAWK_DOUBLE_TAP_TICKS = 16;

    private final AetherMobs plugin;

    private final Map<UUID, Integer> creeperHits =
            new HashMap<>();

    private final Map<UUID, Integer> cowTicks =
            new HashMap<>();

    private final Map<UUID, Long> guardianReadyAt =
            new HashMap<>();

    private final Map<UUID, Long> snowflakeReadyAt =
            new HashMap<>();

    private final Map<UUID, Long> iceDragonReadyAt =
            new HashMap<>();

    private final Map<UUID, Long> chargeReadyAt =
            new HashMap<>();

    private final Set<UUID> guardianFiring =
            new HashSet<>();

    private final Map<UUID, Long> hawkReadyAt =
            new HashMap<>();

    private final Set<UUID> hawkGliding =
            new HashSet<>();

    private final Set<UUID> hawkFlightGranted =
            new HashSet<>();

    private final Map<UUID, Long> hawkNoFallUntil =
            new HashMap<>();

    /** Last airborne downward speed for slime trampoline. */
    private final Map<UUID, Double> slimeFallSpeed =
            new HashMap<>();

    private final Map<UUID, Boolean> slimeWasOnGround =
            new HashMap<>();

    private final Map<UUID, Long> slimeBounceReadyAt =
            new HashMap<>();

    private final Map<UUID, Long> hackerReadyAt =
            new HashMap<>();

    /** Controlled hostile UUID → owner player UUID. */
    private final Map<UUID, UUID> hackerOwners =
            new HashMap<>();

    /** Controlled hostile UUID → expire tick. */
    private final Map<UUID, Long> hackerExpireAt =
            new HashMap<>();

    private final Set<UUID> sensedPets =
            new HashSet<>();

    private final Set<UUID> lookoutMobs =
            new HashSet<>();

    private final NamespacedKey knockbackKey;
    private final NamespacedKey reachBlockKey;
    private final NamespacedKey reachEntityKey;

    private BukkitTask tickTask;

    public PetSkillManager(
            AetherMobs plugin
    ) {

        this.plugin =
                plugin;
        this.knockbackKey =
                new NamespacedKey(
                        plugin,
                        "turtle_shell"
                );
        this.reachBlockKey =
                new NamespacedKey(
                        plugin,
                        "hacker_block_reach"
                );
        this.reachEntityKey =
                new NamespacedKey(
                        plugin,
                        "hacker_entity_reach"
                );
    }

    public void start() {

        stopTick();

        tickTask =
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        tickEquippedPets();
                    }

                }.runTaskTimer(
                        plugin,
                        20L,
                        10L
                );
    }

    public void stop() {

        stopTick();

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {

            creeperHits.clear();
            guardianReadyAt.clear();
            snowflakeReadyAt.clear();
            iceDragonReadyAt.clear();
            chargeReadyAt.clear();
            guardianFiring.clear();
            hawkReadyAt.clear();
            hawkGliding.clear();
            hawkFlightGranted.clear();
            hawkNoFallUntil.clear();
            slimeFallSpeed.clear();
            slimeWasOnGround.clear();
            slimeBounceReadyAt.clear();
            hackerReadyAt.clear();
            releaseAllHijacks();
            clearBeacons();

            return;
        }

        for (Player player :
                plugin.getServer()
                        .getOnlinePlayers()) {

            PetEntity activePet =
                    activePetManager.getActivePet(
                            player
                    );

            if (activePet != null) {

                deactivate(
                        player,
                        activePet.getPetInstance()
                );
            }
        }

        creeperHits.clear();
        guardianReadyAt.clear();
        snowflakeReadyAt.clear();
        iceDragonReadyAt.clear();
        chargeReadyAt.clear();
        guardianFiring.clear();
        hawkReadyAt.clear();
        hawkGliding.clear();
        hawkFlightGranted.clear();
        hawkNoFallUntil.clear();
        slimeFallSpeed.clear();
        slimeWasOnGround.clear();
        slimeBounceReadyAt.clear();
        clearBeacons();
    }

    private void stopTick() {

        if (tickTask != null) {

            tickTask.cancel();

            tickTask = null;
        }
    }

    private long nowTicks() {

        return plugin.getServer()
                .getCurrentTick();
    }

    public void activate(
            Player player,
            PetInstance pet
    ) {

        if (player == null
                || pet == null) {

            return;
        }

        applyPassive(
                player,
                PetSkill.fromPet(
                        pet
                )
        );
    }

    public void deactivate(
            Player player,
            PetInstance pet
    ) {

        if (player == null) {
            return;
        }

        UUID playerId =
                player.getUniqueId();

        creeperHits.remove(
                playerId
        );

        cowTicks.remove(
                playerId
        );

        guardianReadyAt.remove(
                playerId
        );

        snowflakeReadyAt.remove(
                playerId
        );

        iceDragonReadyAt.remove(
                playerId
        );

        chargeReadyAt.remove(
                playerId
        );

        guardianFiring.remove(
                playerId
        );

        hawkReadyAt.remove(
                playerId
        );

        hawkGliding.remove(
                playerId
        );

        hawkNoFallUntil.remove(
                playerId
        );

        slimeFallSpeed.remove(
                playerId
        );

        slimeWasOnGround.remove(
                playerId
        );

        slimeBounceReadyAt.remove(
                playerId
        );

        revokeHawkFlight(
                player
        );

        player.setGliding(
                false
        );

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        PotionEffectType effectType =
                passiveEffect(
                        skill
                );

        if (effectType != null) {

            player.removePotionEffect(
                    effectType
            );
        }

        if (skill == PetSkill.SALMON_RUN) {

            player.removePotionEffect(
                    PotionEffectType.DOLPHINS_GRACE
            );
        }

        if (skill == PetSkill.FOX_POUNCE) {

            player.removePotionEffect(
                    PotionEffectType.SPEED
            );
        }

        if (skill == PetSkill.ARMADILLO_CURL) {

            player.removePotionEffect(
                    PotionEffectType.RESISTANCE
            );
        }

        if (skill == PetSkill.AXOLOTL_MEND) {

            player.removePotionEffect(
                    PotionEffectType.REGENERATION
            );
        }

        applyKnockbackGuard(
                player,
                false
        );

        applyHackerReach(
                player,
                0
        );

        releasePlayerHijacks(
                player.getUniqueId()
        );
    }

    private void tickEquippedPets() {

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {
            return;
        }

        Set<UUID> nextPets =
                new HashSet<>();

        Set<UUID> nextMobs =
                new HashSet<>();

        for (Player player :
                plugin.getServer()
                        .getOnlinePlayers()) {

            if (!player.isOnline()
                    || player.isDead()) {

                continue;
            }

            if (holdingWildSight(player)) {
                collectPetSense(
                        player,
                        nextPets
                );
            }

            PetEntity activePet =
                    activePetManager.getActivePet(
                            player
                    );

            if (activePet == null
                    || !activePet.isSpawned()) {

                continue;
            }

            PetInstance pet =
                    activePet.getPetInstance();

            PetSkill skill =
                    PetSkill.fromPet(
                            pet
                    );

            applyPassive(
                    player,
                    skill
            );

            if (skill == PetSkill.HACKER_BREACH) {
                applyHackerReach(
                        player,
                        PetSkill.hackerReachBlocks(
                                pet.getRarity()
                        )
                );
                tryHackerBreach(
                        player,
                        activePet,
                        pet
                );
            } else {
                applyHackerReach(
                        player,
                        0
                );
            }

            if (skill == PetSkill.GUARDIAN_BEAM) {

                tryGuardianBeam(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.SNOWFLAKE_BOLT) {

                trySnowflakeBolt(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.ICE_DRAGON_FROST) {

                tryIceDragonFrost(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.FIRE_DRAGON_BREATH
                    || skill == PetSkill.BLAZE_FLARE
                    || skill == PetSkill.AETHERION_PULSE) {

                tryFireCloud(
                        player,
                        activePet,
                        pet,
                        skill
                );
            }

            if (skill == PetSkill.WATER_DRAGON_GYRE) {

                tryWaterGyre(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.MINING_DRAGON_VEIN
                    || skill == PetSkill.POISON_DRAGON_MIASMA) {

                tryPoisonMiasma(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.LIGHTNING_DRAGON_CHAIN) {

                tryChainBolt(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.SLIME_BOUNCE
                    || skill == PetSkill.LLAMA_SPIT) {

                trySlimeBounce(
                        player,
                        activePet,
                        pet,
                        skill
                );
            }

            if (skill == PetSkill.GHAST_BOLT) {

                tryGhastBolt(
                        player,
                        activePet,
                        pet
                );
            }

            if (skill == PetSkill.SALMON_RUN) {

                applySalmonRun(
                        player
                );
            }

            if (skill == PetSkill.GLOW_SQUID_SIGHT) {

                collectPetSense(
                        player,
                        nextPets
                );
            }

            if (skill == PetSkill.PARROT_LOOKOUT
                    || skill == PetSkill.PIGEON_SCOUT
                    || skill == PetSkill.OWL_WATCH
                    || skill == PetSkill.DEER_ALERT) {

                collectLookout(
                        player,
                        nextMobs
                );
            }
        }

        syncPetSense(
                nextPets
        );

        syncLookout(
                nextMobs
        );

        tickHijackedMobs();
    }

    private void applyPassive(
            Player player,
            PetSkill skill
    ) {

        applyKnockbackGuard(
                player,
                skill == PetSkill.TURTLE_SHELL
        );

        if (skill == PetSkill.COW_MILK
                || skill == PetSkill.MOOSHROOM_MILK) {
            tryCowMilk(player);
        }

        if (skill == PetSkill.FOX_POUNCE) {
            // Fox already grants SPEED via pet attribute stats — no potion double-dip.
            long time = player.getWorld().getTime();
            if (time < 13000 || time >= 23000) {
                return;
            }
            // Night-only flavor without stacking movement (subtle Night Vision pulse).
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    EFFECT_DURATION_TICKS,
                    0,
                    true,
                    false,
                    true
            ));
            return;
        }

        if (skill == PetSkill.ARMADILLO_CURL) {
            if (!player.isSneaking()) {
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                return;
            }
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,
                    EFFECT_DURATION_TICKS,
                    0,
                    true,
                    false,
                    true
            ));
            return;
        }

        if (skill == PetSkill.AXOLOTL_MEND) {
            if (!player.isInWater() && !player.isInRain()) {
                return;
            }
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    EFFECT_DURATION_TICKS,
                    0,
                    true,
                    false,
                    true
            ));
            return;
        }

        PotionEffectType effectType =
                passiveEffect(
                        skill
                );

        if (effectType == null) {
            return;
        }

        int duration =
                effectType.equals(
                        PotionEffectType.NIGHT_VISION
                )
                        ? NIGHT_VISION_DURATION_TICKS
                        : EFFECT_DURATION_TICKS;

        int amplifier =
                skill == PetSkill.GOAT_JUMP
                        ? 1
                        : 0;

        player.addPotionEffect(
                new PotionEffect(
                        effectType,
                        duration,
                        amplifier,
                        true,
                        false,
                        true
                )
        );
    }

    private PotionEffectType passiveEffect(
            PetSkill skill
    ) {

        if (skill == null) {
            return null;
        }

        return switch (skill) {

            case WOLF_SPEED,
                    SQUIRREL_SCRAMBLE,
                    BUTTERFLY_DRIFT ->
                    PotionEffectType.SPEED;

            // Sand Wraith / Fox already contribute ItemCapability.SPEED via PetStatProvider —
            // potion SPEED on top double-stacks with HealthListener attributes.

            case BAT_NIGHT_VISION,
                    SHROOM_SPORES,
                    MYCELORD_AURA ->
                    PotionEffectType.NIGHT_VISION;

            case DOLPHIN_SWIM ->
                    PotionEffectType.DOLPHINS_GRACE;

            case GOAT_JUMP,
                    RABBIT_HOP,
                    FROG_LEAP ->
                    PotionEffectType.JUMP_BOOST;

            case YETI_HIDE,
                    SHEEP_FLUFF,
                    MUD_COAT,
                    GOLEM_GUARD ->
                    PotionEffectType.RESISTANCE;

            case PANDA_SNOOZE,
                    CAT_NAP,
                    MOSS_MEND,
                    FOREST_BLESSING ->
                    PotionEffectType.REGENERATION;

            case POLAR_GUARD,
                    BOAR_CHARGE ->
                    PotionEffectType.STRENGTH;

            case CAMEL_PACE,
                    SNIFFER_DIG,
                    LUSH_ORACLE ->
                    PotionEffectType.HASTE;

            default ->
                    null;
        };
    }

    private void tryCowMilk(
            Player player
    ) {

        if (player == null) {
            return;
        }

        UUID playerId =
                player.getUniqueId();

        int ticks =
                cowTicks.getOrDefault(
                        playerId,
                        0
                )
                        + 1;

        if (ticks < 16) {

            cowTicks.put(
                    playerId,
                    ticks
            );

            return;
        }

        cowTicks.put(
                playerId,
                0
        );

        PotionEffectType[] negatives = {
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.NAUSEA,
                PotionEffectType.BLINDNESS,
                PotionEffectType.HUNGER,
                PotionEffectType.DARKNESS
        };

        for (PotionEffectType type : negatives) {

            if (type == null
                    || player.getPotionEffect(type) == null) {

                continue;
            }

            player.removePotionEffect(
                    type
            );

            player.getWorld()
                    .playSound(
                            player.getLocation(),
                            Sound.ENTITY_COW_MILK,
                            0.55f,
                            1.15f
                    );

            player.getWorld()
                    .spawnParticle(
                            Particle.CLOUD,
                            player.getLocation()
                                    .add(0, 1.0, 0),
                            8,
                            0.25,
                            0.2,
                            0.25,
                            0.01
                    );

            return;
        }
    }

    private void applyKnockbackGuard(
            Player player,
            boolean enabled
    ) {

        if (player == null) {
            return;
        }

        Attribute attribute =
                knockbackAttribute();

        if (attribute == null) {
            return;
        }

        AttributeInstance instance =
                player.getAttribute(
                        attribute
                );

        if (instance == null) {
            return;
        }

        instance.removeModifier(
                knockbackKey
        );

        if (!enabled) {
            return;
        }

        instance.addModifier(
                new AttributeModifier(
                        knockbackKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY
                )
        );
    }

    private static Attribute knockbackAttribute() {

        try {
            return Attribute.valueOf(
                    "GENERIC_KNOCKBACK_RESISTANCE"
            );
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Attribute.valueOf(
                    "KNOCKBACK_RESISTANCE"
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void applyHackerReach(
            Player player,
            int blocks
    ) {

        if (player == null) {
            return;
        }

        setReachModifier(
                player,
                blockReachAttribute(),
                reachBlockKey,
                blocks
        );
        setReachModifier(
                player,
                entityReachAttribute(),
                reachEntityKey,
                blocks
        );
    }

    private void setReachModifier(
            Player player,
            Attribute attribute,
            NamespacedKey key,
            int blocks
    ) {

        if (attribute == null || key == null) {
            return;
        }

        AttributeInstance instance =
                player.getAttribute(
                        attribute
                );

        if (instance == null) {
            return;
        }

        instance.removeModifier(
                key
        );

        if (blocks <= 0) {
            return;
        }

        instance.addModifier(
                new AttributeModifier(
                        key,
                        blocks,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY
                )
        );
    }

    private static Attribute blockReachAttribute() {

        try {
            return Attribute.valueOf(
                    "PLAYER_BLOCK_INTERACTION_RANGE"
            );
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Attribute.valueOf(
                    "BLOCK_INTERACTION_RANGE"
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Attribute entityReachAttribute() {

        try {
            return Attribute.valueOf(
                    "PLAYER_ENTITY_INTERACTION_RANGE"
            );
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Attribute.valueOf(
                    "ENTITY_INTERACTION_RANGE"
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void tryHackerBreach(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null || pet == null) {
            return;
        }

        long now =
                nowTicks();

        if (now < hackerReadyAt.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }

        Rarity rarity =
                pet.getRarity();
        int wanted =
                PetSkill.hackerHijackCount(
                        rarity
                );

        List<LivingEntity> candidates =
                nearestHijackTargets(
                        player.getLocation(),
                        10.0,
                        wanted
                );

        if (candidates.isEmpty()) {
            return;
        }

        long duration =
                PetSkill.hackerHijackDurationTicks(
                        rarity
                );
        long cooldown =
                PetSkill.hackerHijackCooldownTicks(
                        rarity
                );

        hackerReadyAt.put(
                player.getUniqueId(),
                now + cooldown
        );

        for (LivingEntity target : candidates) {
            hijackMob(
                    player,
                    target,
                    now + duration
            );
        }

        Location fx =
                activePet != null
                        && activePet.isSpawned()
                        && activePet.getEntity() != null
                        ? activePet.getEntity().getLocation()
                        : player.getLocation();

        if (fx != null && fx.getWorld() != null) {
            fx.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    fx.clone().add(0, 0.6, 0),
                    28,
                    0.45,
                    0.45,
                    0.45,
                    0.6
            );
            fx.getWorld().playSound(
                    fx,
                    Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    SoundCategory.PLAYERS,
                    0.55f,
                    1.65f
            );
        }
    }

    private void hijackMob(
            Player owner,
            LivingEntity target,
            long expireAt
    ) {

        if (owner == null || target == null || !target.isValid()) {
            return;
        }

        UUID mobId =
                target.getUniqueId();
        hackerOwners.put(
                mobId,
                owner.getUniqueId()
        );
        hackerExpireAt.put(
                mobId,
                expireAt
        );

        target.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.GLOWING,
                        (int) Math.max(
                                40L,
                                expireAt - nowTicks()
                        ),
                        0,
                        true,
                        false,
                        true
                )
        );

        retargetHijacked(
                owner,
                target
        );
    }

    private void tickHijackedMobs() {

        if (hackerExpireAt.isEmpty()) {
            return;
        }

        long now =
                nowTicks();
        List<UUID> expired =
                new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : hackerExpireAt.entrySet()) {
            UUID mobId =
                    entry.getKey();
            if (now >= entry.getValue()) {
                expired.add(mobId);
                continue;
            }

            UUID ownerId =
                    hackerOwners.get(
                            mobId
                    );
            if (ownerId == null) {
                expired.add(mobId);
                continue;
            }

            Player owner =
                    plugin.getServer()
                            .getPlayer(
                                    ownerId
                            );
            Entity entity =
                    findEntity(
                            mobId
                    );

            if (owner == null
                    || !owner.isOnline()
                    || !(entity instanceof LivingEntity living)
                    || living.isDead()
                    || !living.isValid()) {
                expired.add(mobId);
                continue;
            }

            retargetHijacked(
                    owner,
                    living
            );
        }

        for (UUID mobId : expired) {
            releaseHijack(
                    mobId
            );
        }
    }

    private void retargetHijacked(
            Player owner,
            LivingEntity ally
    ) {

        if (!(ally instanceof Mob mob)) {
            return;
        }

        LivingEntity best =
                null;
        double bestDist =
                14.0 * 14.0;

        for (Entity nearby : ally.getNearbyEntities(14.0, 8.0, 14.0)) {
            if (!(nearby instanceof LivingEntity living)
                    || living.equals(ally)
                    || living.equals(owner)
                    || living instanceof Player
                    || living.isDead()
                    || !living.isValid()
                    || AetherEntities.isPet(living)
                    || AetherEntities.isBoss(living)
                    || living instanceof Boss
                    || hackerOwners.containsKey(living.getUniqueId())) {
                continue;
            }

            if (!(living instanceof Enemy)
                    && !(living instanceof Mob)) {
                continue;
            }

            double dist =
                    living.getLocation()
                            .distanceSquared(
                                    ally.getLocation()
                            );

            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }

        if (best != null) {
            mob.setTarget(
                    best
            );
        } else if (mob.getTarget() instanceof Player) {
            mob.setTarget(
                    null
            );
        }
    }

    private List<LivingEntity> nearestHijackTargets(
            Location origin,
            double range,
            int max
    ) {

        List<LivingEntity> targets =
                new ArrayList<>();

        if (origin == null || origin.getWorld() == null || max <= 0) {
            return targets;
        }

        for (Entity nearby : origin.getWorld().getNearbyEntities(origin, range, range * 0.7, range)) {
            if (!(nearby instanceof LivingEntity living)
                    || living instanceof Player
                    || living.isDead()
                    || !living.isValid()
                    || AetherEntities.isPet(living)
                    || AetherEntities.isBoss(living)
                    || living instanceof Boss
                    || living instanceof org.bukkit.entity.Wither
                    || living instanceof org.bukkit.entity.EnderDragon
                    || hackerOwners.containsKey(living.getUniqueId())) {
                continue;
            }

            if (!(living instanceof Enemy)
                    && !(living instanceof Mob)) {
                continue;
            }

            targets.add(
                    living
            );
        }

        targets.sort(
                Comparator.comparingDouble(
                        living -> living.getLocation()
                                .distanceSquared(
                                        origin
                                )
                )
        );

        if (targets.size() > max) {
            return new ArrayList<>(
                    targets.subList(
                            0,
                            max
                    )
            );
        }

        return targets;
    }

    private Entity findEntity(
            UUID id
    ) {

        if (id == null) {
            return null;
        }

        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            Entity entity =
                    world.getEntity(
                            id
                    );
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private void releaseHijack(
            UUID mobId
    ) {

        if (mobId == null) {
            return;
        }

        hackerOwners.remove(
                mobId
        );
        hackerExpireAt.remove(
                mobId
        );

        Entity entity =
                findEntity(
                        mobId
                );
        if (entity instanceof Mob mob) {
            LivingEntity target =
                    mob.getTarget();
            if (target instanceof Player) {
                mob.setTarget(
                        null
                );
            }
            mob.removePotionEffect(
                    PotionEffectType.GLOWING
            );
        }
    }

    private void releasePlayerHijacks(
            UUID ownerId
    ) {

        if (ownerId == null) {
            return;
        }

        List<UUID> owned =
                new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : hackerOwners.entrySet()) {
            if (ownerId.equals(entry.getValue())) {
                owned.add(
                        entry.getKey()
                );
            }
        }

        for (UUID mobId : owned) {
            releaseHijack(
                    mobId
            );
        }
    }

    private void releaseAllHijacks() {

        List<UUID> all =
                new ArrayList<>(
                        hackerExpireAt.keySet()
                );
        for (UUID mobId : all) {
            releaseHijack(
                    mobId
            );
        }
        hackerOwners.clear();
        hackerExpireAt.clear();
        hackerReadyAt.clear();
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onHackerFriendlyFire(
            EntityDamageByEntityEvent event
    ) {

        Entity damager =
                event.getDamager();
        Entity victim =
                event.getEntity();

        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }

        UUID damagerId =
                damager.getUniqueId();
        UUID victimId =
                victim.getUniqueId();

        UUID damagerOwner =
                hackerOwners.get(
                        damagerId
                );
        UUID victimOwner =
                hackerOwners.get(
                        victimId
                );

        if (damager instanceof Player player
                && victimOwner != null
                && victimOwner.equals(player.getUniqueId())) {
            event.setCancelled(
                    true
            );
            return;
        }

        if (damagerOwner != null
                && victim instanceof Player player
                && damagerOwner.equals(player.getUniqueId())) {
            event.setCancelled(
                    true
            );
            return;
        }

        if (damagerOwner != null
                && victimOwner != null
                && damagerOwner.equals(victimOwner)) {
            event.setCancelled(
                    true
            );
        }
    }

    private boolean turtleGuarding(
            Player player
    ) {

        if (player == null
                || plugin.getActivePetManager() == null) {

            return false;
        }

        PetEntity activePet =
                plugin.getActivePetManager()
                        .getActivePet(
                                player
                        );

        return activePet != null
                && PetSkill.fromPet(
                activePet.getPetInstance()
        )
                == PetSkill.TURTLE_SHELL;
    }

    private boolean slimeTrampoline(
            Player player
    ) {

        if (player == null
                || plugin.getActivePetManager() == null) {

            return false;
        }

        PetEntity activePet =
                plugin.getActivePetManager()
                        .getActivePet(
                                player
                        );

        return activePet != null
                && activePet.isSpawned()
                && PetSkill.fromPet(
                activePet.getPetInstance()
        )
                == PetSkill.SLIME_BOUNCE;
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onSlimeTrampoline(
            PlayerMoveEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!slimeTrampoline(
                player
        )) {

            return;
        }

        UUID playerId =
                player.getUniqueId();

        if (player.isSneaking()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.isInWater()
                || player.getGameMode()
                == GameMode.SPECTATOR) {

            slimeWasOnGround.put(
                    playerId,
                    player.isOnGround()
            );

            slimeFallSpeed.put(
                    playerId,
                    0.0
            );

            return;
        }

        boolean grounded =
                player.isOnGround();

        boolean wasGrounded =
                slimeWasOnGround.getOrDefault(
                        playerId,
                        grounded
                );

        Vector velocity =
                player.getVelocity();

        if (!grounded
                && velocity.getY() < -0.08) {

            slimeFallSpeed.put(
                    playerId,
                    -velocity.getY()
            );
        }

        if (!wasGrounded
                && grounded) {

            long now =
                    nowTicks();

            if (now
                    >= slimeBounceReadyAt.getOrDefault(
                    playerId,
                    0L
            )) {

                double fall =
                        slimeFallSpeed.getOrDefault(
                                playerId,
                                0.0
                        );

                if (fall > 0.16) {

                    double bounce =
                            Math.min(
                                    fall * 0.98,
                                    1.4
                            );

                    Vector launch =
                            velocity.clone();

                    launch.setY(
                            bounce
                    );

                    player.setVelocity(
                            launch
                    );

                    Location at =
                            player.getLocation();

                    at.getWorld()
                            .playSound(
                                    at,
                                    Sound.BLOCK_SLIME_BLOCK_FALL,
                                    0.55f,
                                    1.05f
                            );

                    at.getWorld()
                            .spawnParticle(
                                    Particle.ITEM_SLIME,
                                    at.clone()
                                            .add(
                                                    0,
                                                    0.1,
                                                    0
                                            ),
                                    8,
                                    0.25,
                                    0.05,
                                    0.25,
                                    0.02
                            );

                    slimeBounceReadyAt.put(
                            playerId,
                            now + 3L
                    );
                }
            }

            slimeFallSpeed.put(
                    playerId,
                    0.0
            );
        }

        slimeWasOnGround.put(
                playerId,
                grounded
        );
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onKnockback(
            EntityKnockbackEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player player)) {

            return;
        }

        if (turtleGuarding(
                player
        )) {

            event.setCancelled(
                    true
            );
        }
    }

    /*
     * =========================================================
     * CREEPER
     * =========================================================
     */

    private void applyMeleeDebuff(
            LivingEntity victim,
            PetInstance pet,
            PetSkill skill
    ) {

        if (victim == null
                || pet == null
                || skill == null) {

            return;
        }

        int ticks =
                PetSkill.pufferPoisonTicks(
                        pet.getLevel()
                );

        PotionEffectType type;
        int amplifier = 0;
        int duration = ticks;

        switch (skill) {

            case CAVE_SPIDER_VENOM ->
                    type = PotionEffectType.POISON;

            case ZOMBIE_HUNGER ->
                    type = PotionEffectType.HUNGER;

            case SKELETON_MARK ->
                    type = PotionEffectType.GLOWING;

            case DUNGEON_ROT -> {
                type = PotionEffectType.WITHER;
                duration = Math.max(20, ticks / 2);
            }

            default ->
                    type = null;
        }

        if (type == null) {
            return;
        }

        victim.addPotionEffect(
                new PotionEffect(
                        type,
                        duration,
                        amplifier,
                        true,
                        false,
                        true
                )
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPlayerMeleeHit(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getDamager()
                instanceof Player player)) {

            return;
        }

        if (!(event.getEntity()
                instanceof LivingEntity victim)) {

            return;
        }

        if (victim.equals(player)
                || victim.isDead()) {

            return;
        }

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {
            return;
        }

        PetEntity activePet =
                activePetManager.getActivePet(
                        player
                );

        if (activePet == null
                || !activePet.isSpawned()) {

            return;
        }

        PetInstance pet =
                activePet.getPetInstance();

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        applyMeleeDebuff(
                victim,
                pet,
                skill
        );

        if (skill
                != PetSkill.CREEPER_BURST) {

            return;
        }

        UUID playerId =
                player.getUniqueId();

        int hits =
                creeperHits.getOrDefault(
                        playerId,
                        0
                )
                        + 1;

        if (hits < CREEPER_HIT_THRESHOLD) {

            creeperHits.put(
                    playerId,
                    hits
            );

            return;
        }

        creeperHits.put(
                playerId,
                0
        );

        new BukkitRunnable() {

            @Override
            public void run() {

                if (!player.isOnline()
                        || victim.isDead()
                        || !victim.isValid()) {

                    return;
                }

                triggerCreeperBurst(
                        player,
                        pet,
                        victim
                );
            }

        }.runTaskLater(
                plugin,
                1L
        );
    }

    private void triggerCreeperBurst(
            Player player,
            PetInstance pet,
            LivingEntity victim
    ) {

        Location location =
                victim.getLocation()
                        .clone()
                        .add(
                                0,
                                victim.getHeight() * 0.5,
                                0
                        );

        victim.getWorld()
                .spawnParticle(
                        Particle.EXPLOSION,
                        location,
                        2,
                        0.15,
                        0.15,
                        0.15,
                        0
                );

        victim.getWorld()
                .spawnParticle(
                        Particle.SMOKE,
                        location,
                        14,
                        0.35,
                        0.35,
                        0.35,
                        0.02
                );

        victim.getWorld()
                .playSound(
                        location,
                        Sound.ENTITY_GENERIC_EXPLODE,
                        0.55f,
                        1.45f
                );

        double damage =
                3.0
                        + pet.getLevel()
                        * 0.25;

        dealPetDamage(
                player,
                victim,
                damage
        );

        for (Entity nearby :
                victim.getNearbyEntities(
                        CREEPER_BURST_RADIUS,
                        CREEPER_BURST_RADIUS,
                        CREEPER_BURST_RADIUS
                )) {

            if (!(nearby instanceof Enemy)
                    || !(nearby instanceof LivingEntity living)
                    || living.equals(victim)
                    || living.equals(player)
                    || living.isDead()) {

                continue;
            }

            dealPetDamage(
                    player,
                    living,
                    damage * 0.45
            );
        }
    }

    /*
     * =========================================================
     * GUARDIAN
     * =========================================================
     */

    private void tryGuardianBeam(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        UUID playerId =
                player.getUniqueId();

        if (guardianFiring.contains(
                playerId
        )) {

            return;
        }

        int beams =
                PetSkill.beamCount(
                        pet.getRarity()
                );

        if (beams <= 0) {
            return;
        }

        long now =
                nowTicks();

        long readyAt =
                guardianReadyAt.getOrDefault(
                        playerId,
                        0L
                );

        if (now < readyAt) {
            return;
        }

        List<LivingEntity> targets =
                findGuardianTargets(
                        player,
                        beams
                );

        if (targets.isEmpty()) {
            return;
        }

        while (targets.size() < beams) {

            targets.add(
                    targets.get(0)
            );
        }

        guardianFiring.add(
                playerId
        );

        guardianReadyAt.put(
                playerId,
                now
                        + PetSkill.guardianCooldownTicks(
                        pet.getRarity(),
                        pet.getLevel()
                )
        );

        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> guardianFiring.remove(playerId),
                        GUARDIAN_BEAM_TICKS + 10L
                );

        Location origin =
                activePet.getEntity()
                        .getLocation()
                        .clone()
                        .add(
                                0,
                                0.35,
                                0
                        );

        player.getWorld()
                .playSound(
                        origin,
                        Sound.ENTITY_GUARDIAN_ATTACK,
                        0.8f,
                        1.15f
                );

        for (int index = 0;
             index < beams;
             index++) {

            LivingEntity target =
                    targets.get(
                            index
                    );

            Location start =
                    origin.clone()
                            .add(
                                    (index - (beams - 1) / 2.0)
                                            * 0.22,
                                    0,
                                    0
                            );

            animateGuardianBeam(
                    player,
                    pet,
                    start,
                    target,
                    index == 0
            );
        }
    }

    private List<LivingEntity> findGuardianTargets(
            Player player,
            int limit
    ) {

        List<LivingEntity> hostiles =
                new ArrayList<>();

        for (Entity nearby :
                player.getNearbyEntities(
                        GUARDIAN_RANGE,
                        GUARDIAN_RANGE,
                        GUARDIAN_RANGE
                )) {

            if (!(nearby instanceof Enemy)
                    || !(nearby instanceof LivingEntity living)
                    || living.equals(player)
                    || living.isDead()) {

                continue;
            }

            if (!player.hasLineOfSight(
                    living
            )) {

                continue;
            }

            hostiles.add(
                    living
            );
        }

        hostiles.sort(
                Comparator
                        .comparing(
                                (LivingEntity living) ->
                                        isTargetingPlayer(
                                                living,
                                                player
                                        )
                        )
                        .reversed()
                        .thenComparingDouble(
                                living ->
                                        living.getLocation()
                                                .distanceSquared(
                                                        player.getLocation()
                                                )
                        )
        );

        if (hostiles.size() > limit) {

            return new ArrayList<>(
                    hostiles.subList(
                            0,
                            limit
                    )
            );
        }

        return hostiles;
    }

    private boolean isTargetingPlayer(
            LivingEntity living,
            Player player
    ) {

        if (!(living instanceof Mob mob)) {
            return false;
        }

        return player.equals(
                mob.getTarget()
        );
    }

    private void animateGuardianBeam(
            Player player,
            PetInstance pet,
            Location start,
            LivingEntity target,
            boolean releaseLock
    ) {

        new BukkitRunnable() {

            int tick = 0;

            @Override
            public void run() {

                if (!player.isOnline()
                        || player.isDead()
                        || target.isDead()
                        || !target.isValid()) {

                    finish();

                    return;
                }

                Location end =
                        target.getLocation()
                                .clone()
                                .add(
                                        0,
                                        target.getHeight() * 0.55,
                                        0
                                );

                drawBeam(
                        start,
                        end,
                        (tick + 1)
                                / (double) GUARDIAN_BEAM_TICKS
                );

                tick++;

                if (tick < GUARDIAN_BEAM_TICKS) {
                    return;
                }

                hitGuardianTarget(
                        player,
                        pet,
                        target,
                        end
                );

                finish();
            }

            private void finish() {

                cancel();

                if (releaseLock) {

                    guardianFiring.remove(
                            player.getUniqueId()
                    );
                }
            }

        }.runTaskTimer(
                plugin,
                0L,
                1L
        );
    }

    private void drawBeam(
            Location start,
            Location end,
            double progress
    ) {

        if (start.getWorld() == null
                || end.getWorld() == null
                || !start.getWorld()
                .equals(
                        end.getWorld()
                )) {

            return;
        }

        Vector direction =
                end.toVector()
                        .subtract(
                                start.toVector()
                        );

        double length =
                direction.length();

        if (length < 0.2) {
            return;
        }

        direction.normalize();

        double visibleLength =
                length
                        * Math.max(
                        0.12,
                        Math.min(
                                1.0,
                                progress
                        )
                );

        Particle.DustOptions dust =
                new Particle.DustOptions(
                        Color.fromRGB(
                                40,
                                230,
                                255
                        ),
                        1.05f
                );

        for (double step = 0;
             step <= visibleLength;
             step += 0.28) {

            Location point =
                    start.clone()
                            .add(
                                    direction.clone()
                                            .multiply(
                                                    step
                                            )
                            );

            start.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            point,
                            1,
                            0,
                            0,
                            0,
                            0,
                            dust
                    );

            if (step % 0.84 < 0.28) {

                start.getWorld()
                        .spawnParticle(
                                Particle.BUBBLE_POP,
                                point,
                                1,
                                0.02,
                                0.02,
                                0.02,
                                0
                        );
            }
        }

        Location tip =
                start.clone()
                        .add(
                                direction.clone()
                                        .multiply(
                                                visibleLength
                                        )
                        );

        start.getWorld()
                .spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        tip,
                        3,
                        0.04,
                        0.04,
                        0.04,
                        0.01
                );
    }

    private void hitGuardianTarget(
            Player player,
            PetInstance pet,
            LivingEntity target,
            Location impact
    ) {

        target.getWorld()
                .spawnParticle(
                        Particle.FLASH,
                        impact,
                        1,
                        0,
                        0,
                        0,
                        0
                );

        target.getWorld()
                .spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        impact,
                        18,
                        0.25,
                        0.35,
                        0.25,
                        0.08
                );

        target.getWorld()
                .playSound(
                        impact,
                        Sound.ENTITY_GUARDIAN_HURT,
                        0.7f,
                        1.4f
                );

        double damage =
                PetSkill.guardianDamage(
                        pet.getRarity(),
                        pet.getLevel()
                );

        dealPetDamage(
                player,
                target,
                damage
        );
    }

    private void dealPetDamage(
            Player player,
            LivingEntity target,
            double amount
    ) {

        if (target == null
                || !target.isValid()
                || target.isDead()
                || amount <= 0) {

            return;
        }

        de.aetherion.items.combat.ScriptedHits.run(() -> target.damage(amount, player));
    }

    private void trySnowflakeBolt(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null
                || activePet == null
                || !activePet.isSpawned()
                || pet == null) {

            return;
        }

        long now =
                nowTicks();

        if (now < snowflakeReadyAt.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }

        var display =
                activePet.getEntity();

        if (display == null || !display.isValid()) {
            return;
        }

        Location origin =
                display.getLocation();

        LivingEntity target = null;
        double best = 9.0 * 9.0;
        for (Entity nearby : display.getNearbyEntities(9.0, 6.0, 9.0)) {
            if (!(nearby instanceof LivingEntity living)
                    || living instanceof Player
                    || living instanceof org.bukkit.entity.ArmorStand
                    || living instanceof org.bukkit.entity.Villager
                    || living.isDead()
                    || !living.isValid()) {
                continue;
            }
            if (!(living instanceof Enemy) && !(living instanceof Mob)) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(origin);
            if (dist < best) {
                best = dist;
                target = living;
            }
        }

        if (target == null) {
            return;
        }

        snowflakeReadyAt.put(player.getUniqueId(), now + 50L);

        Location impact = target.getLocation().add(0, 1.0, 0);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, impact, 18, 0.35, 0.45, 0.35, 0.02);
        target.getWorld().playSound(impact, Sound.BLOCK_POWDER_SNOW_BREAK, 0.7f, 1.35f);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, false));
        dealPetDamage(player, target, 5.5 + 0.04 * Math.max(1, pet.getLevel()));
    }

    private void tryIceDragonFrost(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null
                || activePet == null
                || !activePet.isSpawned()
                || pet == null) {

            return;
        }

        long now =
                nowTicks();

        if (now < iceDragonReadyAt.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }

        var display =
                activePet.getEntity();

        if (display == null || !display.isValid()) {
            return;
        }

        Location origin =
                display.getLocation();

        int wanted =
                pet.getRarity() == Rarity.MYTHIC
                        ? 2
                        : 1;

        List<LivingEntity> targets =
                new ArrayList<>();

        for (Entity nearby : display.getNearbyEntities(11.0, 7.0, 11.0)) {
            if (!frostTarget(nearby)) {
                continue;
            }
            targets.add((LivingEntity) nearby);
        }

        targets.sort(Comparator.comparingDouble(living ->
                living.getLocation().distanceSquared(origin)));

        if (targets.isEmpty()) {
            return;
        }

        iceDragonReadyAt.put(player.getUniqueId(), now + PetSkill.dragonCooldownTicks(pet.getLevel()));

        int freezeTicks =
                pet.getRarity() == Rarity.MYTHIC
                        ? 80
                        : 55;

        int slowAmp =
                pet.getRarity() == Rarity.MYTHIC
                        ? 1
                        : 0;

        double damage =
                7.5 + 0.05 * Math.max(1, pet.getLevel());

        int hits =
                Math.min(wanted, targets.size());

        for (int i = 0; i < hits; i++) {
            LivingEntity target =
                    targets.get(i);
            Location impact =
                    target.getEyeLocation();
            target.setFreezeTicks(
                    Math.max(
                            target.getFreezeTicks(),
                            freezeTicks
                    )
            );
            target.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SLOWNESS,
                            freezeTicks,
                            slowAmp,
                            true,
                            false,
                            false
                    )
            );
            target.getWorld().spawnParticle(
                    Particle.SNOWFLAKE,
                    impact,
                    28,
                    0.45,
                    0.55,
                    0.45,
                    0.03
            );
            target.getWorld().spawnParticle(
                    Particle.ITEM_SNOWBALL,
                    impact,
                    10,
                    0.25,
                    0.35,
                    0.25,
                    0.08
            );
            target.getWorld().playSound(
                    impact,
                    Sound.ENTITY_PLAYER_HURT_FREEZE,
                    SoundCategory.PLAYERS,
                    0.16f,
                    0.85f
            );
            dealPetDamage(
                    player,
                    target,
                    damage
            );
        }
    }

    private boolean frostTarget(
            Entity entity
    ) {

        if (!(entity instanceof LivingEntity living)
                || living instanceof Player
                || living instanceof org.bukkit.entity.ArmorStand
                || living instanceof org.bukkit.entity.Villager
                || living.isDead()
                || !living.isValid()) {

            return false;
        }

        if (AetherEntities.isPet(living)) {
            return false;
        }

        return living instanceof Enemy
                || living instanceof Mob
                || living instanceof Boss
                || AetherEntities.isBoss(living);
    }

    private boolean chargeReady(
            Player player,
            PetInstance pet
    ) {

        return chargeReady(
                player,
                pet,
                PetSkill.chargeCooldownTicks(pet.getLevel())
        );
    }

    private boolean chargeReady(
            Player player,
            PetInstance pet,
            long cooldownTicks
    ) {

        long now =
                nowTicks();
        if (now < chargeReadyAt.getOrDefault(player.getUniqueId(), 0L)) {
            return false;
        }
        chargeReadyAt.put(
                player.getUniqueId(),
                now + Math.max(40L, cooldownTicks)
        );
        return true;
    }

    private List<LivingEntity> nearestHostiles(
            Location origin,
            double range,
            int max
    ) {

        List<LivingEntity> targets =
                new ArrayList<>();
        if (origin == null || origin.getWorld() == null) {
            return targets;
        }
        for (Entity nearby : origin.getWorld().getNearbyEntities(origin, range, range * 0.7, range)) {
            if (!frostTarget(nearby)) {
                continue;
            }
            targets.add((LivingEntity) nearby);
        }
        targets.sort(Comparator.comparingDouble(living ->
                living.getLocation().distanceSquared(origin)));
        if (targets.size() > max) {
            return new ArrayList<>(targets.subList(0, max));
        }
        return targets;
    }

    private void tryFireCloud(
            Player player,
            PetEntity activePet,
            PetInstance pet,
            PetSkill skill
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 11.0, 1);
        boolean dragonBreath =
                skill == PetSkill.FIRE_DRAGON_BREATH
                        || skill == PetSkill.AETHERION_PULSE;
        if (targets.isEmpty()
                || !chargeReady(
                        player,
                        pet,
                        dragonBreath
                                ? PetSkill.dragonCooldownTicks(pet.getLevel())
                                : PetSkill.chargeCooldownTicks(pet.getLevel())
                )) {
            return;
        }
        LivingEntity target = targets.get(0);
        Location start = display.getLocation().clone().add(0, 0.3, 0);
        Location end = target.getEyeLocation();
        Vector delta = end.toVector().subtract(start.toVector());
        if (delta.lengthSquared() < 0.01) {
            delta = player.getLocation().getDirection();
        }
        final Vector step = delta.normalize().multiply(0.55);
        double damage =
                PetSkill.chargeDamage(skill, pet.getRarity(), pet.getLevel());
        boolean blaze = skill == PetSkill.BLAZE_FLARE;
        boolean aether = skill == PetSkill.AETHERION_PULSE;
        start.getWorld().playSound(
                start,
                aether
                        ? Sound.ENTITY_ENDERMAN_TELEPORT
                        : blaze ? Sound.ENTITY_BLAZE_SHOOT : Sound.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                aether ? 0.18f : blaze ? 0.35f : 0.18f,
                aether ? 0.85f : blaze ? 1.35f : 1.05f
        );
        new BukkitRunnable() {
            int tick = 0;
            Location cursor = start.clone();

            @Override
            public void run() {
                if (tick++ > (blaze || aether ? 7 : 10) || cursor.getWorld() == null) {
                    cancel();
                    return;
                }
                cursor.add(step);
                cursor.getWorld().spawnParticle(
                        aether ? Particle.PORTAL : Particle.FLAME,
                        cursor,
                        aether ? 12 : blaze ? 4 : 8,
                        0.18,
                        0.16,
                        0.18,
                        aether ? 0.12 : 0.01
                );
                if (!aether) {
                    cursor.getWorld().spawnParticle(
                            Particle.SMOKE,
                            cursor,
                            2,
                            0.12,
                            0.12,
                            0.12,
                            0.0
                    );
                } else {
                    cursor.getWorld().spawnParticle(
                            Particle.WITCH,
                            cursor,
                            3,
                            0.12,
                            0.12,
                            0.12,
                            0.0
                    );
                }
                if (tick % 3 != 0) {
                    return;
                }
                for (LivingEntity living : nearestHostiles(cursor, 1.6, 4)) {
                    if (!aether) {
                        living.setFireTicks(Math.max(living.getFireTicks(), blaze ? 30 : 50));
                    }
                    dealPetDamage(player, living, damage * 0.55);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tryWaterGyre(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 10.0, 1);
        if (targets.isEmpty()
                || !chargeReady(player, pet, PetSkill.dragonCooldownTicks(pet.getLevel()))) {
            return;
        }
        LivingEntity target = targets.get(0);
        Location center = target.getLocation().clone();
        double damage =
                PetSkill.chargeDamage(PetSkill.WATER_DRAGON_GYRE, pet.getRarity(), pet.getLevel());
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 0.18f, 0.85f);
        dealPetDamage(player, target, damage);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || tick++ > 22) {
                    cancel();
                    return;
                }
                double angle = tick * 0.62;
                target.setVelocity(new Vector(
                        -Math.sin(angle) * 0.42,
                        0.05,
                        Math.cos(angle) * 0.42
                ));
                Location at = target.getLocation().add(0, 0.4, 0);
                at.getWorld().spawnParticle(Particle.BUBBLE_POP, at, 6, 0.35, 0.35, 0.35, 0.02);
                at.getWorld().spawnParticle(Particle.SPLASH, at, 4, 0.4, 0.2, 0.4, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tryPoisonMiasma(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 10.0, 1);
        if (targets.isEmpty()
                || !chargeReady(player, pet, PetSkill.dragonCooldownTicks(pet.getLevel()))) {
            return;
        }
        LivingEntity target = targets.get(0);
        Location cloud = target.getLocation().add(0, 0.4, 0);
        double damage =
                PetSkill.chargeDamage(PetSkill.MINING_DRAGON_VEIN, pet.getRarity(), pet.getLevel());
        int poisonTicks = 40 + pet.getLevel() / 2;
        cloud.getWorld().playSound(cloud, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.12f, 1.55f);
        dealPetDamage(player, target, damage);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, poisonTicks, 0, true, true, true));
        cloud.getWorld().spawnParticle(Particle.CRIT, cloud, 16, 0.35, 0.4, 0.35, 0.12);
        cloud.getWorld().spawnParticle(Particle.BLOCK, cloud, 10, 0.3, 0.25, 0.3, 0.02, Material.DEEPSLATE_IRON_ORE.createBlockData());
        cloud.getWorld().spawnParticle(Particle.ASH, cloud, 8, 0.4, 0.3, 0.4, 0.01);
    }

    private void tryChainBolt(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        int hops =
                PetSkill.chainHops(pet.getRarity(), pet.getLevel());
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 12.0, hops);
        if (targets.isEmpty()
                || !chargeReady(player, pet, PetSkill.dragonCooldownTicks(pet.getLevel()))) {
            return;
        }
        double damage =
                PetSkill.chargeDamage(PetSkill.LIGHTNING_DRAGON_CHAIN, pet.getRarity(), pet.getLevel());
        display.getWorld().playSound(
                display.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                SoundCategory.PLAYERS,
                0.12f,
                1.55f
        );
        new BukkitRunnable() {
            int hop = 0;
            Location from = display.getLocation().clone().add(0, 0.3, 0);

            @Override
            public void run() {
                if (hop >= targets.size()) {
                    cancel();
                    return;
                }
                LivingEntity living = targets.get(hop);
                hop++;
                if (!living.isValid() || living.isDead()) {
                    return;
                }
                Location to = living.getEyeLocation();
                Vector delta = to.toVector().subtract(from.toVector());
                int points = Math.max(4, (int) (delta.length() * 2.2));
                Vector step = delta.lengthSquared() < 0.01
                        ? new Vector(0, 0.1, 0)
                        : delta.normalize().multiply(delta.length() / points);
                Location cursor = from.clone();
                for (int i = 0; i < points; i++) {
                    cursor.add(step);
                    cursor.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, cursor, 2, 0.04, 0.04, 0.04, 0.0);
                }
                to.getWorld().spawnParticle(Particle.FLASH, to, 1, 0, 0, 0, 0);
                to.getWorld().playSound(
                        to,
                        Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                        SoundCategory.PLAYERS,
                        0.08f,
                        1.65f
                );
                dealPetDamage(player, living, damage * (1.0 - 0.12 * (hop - 1)));
                from = to.clone();
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void trySlimeBounce(
            Player player,
            PetEntity activePet,
            PetInstance pet,
            PetSkill skill
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 7.0, 3);
        if (targets.isEmpty() || !chargeReady(player, pet)) {
            return;
        }
        boolean llama = skill == PetSkill.LLAMA_SPIT;
        double damage =
                PetSkill.chargeDamage(
                        skill == null ? PetSkill.SLIME_BOUNCE : skill,
                        pet.getRarity(),
                        pet.getLevel()
                );
        Location at = display.getLocation();
        at.getWorld().playSound(
                at,
                llama ? Sound.ENTITY_LLAMA_SPIT : Sound.ENTITY_SLIME_SQUISH,
                0.8f,
                llama ? 1.15f : 0.9f
        );
        at.getWorld().spawnParticle(
                llama ? Particle.SPIT : Particle.ITEM_SLIME,
                at,
                llama ? 14 : 18,
                0.45,
                0.35,
                0.45,
                0.05
        );
        for (LivingEntity living : targets) {
            Vector push = living.getLocation().toVector().subtract(at.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 0.35, 0);
            } else {
                push.normalize().multiply(0.55).setY(0.32);
            }
            living.setVelocity(push);
            if (!llama) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 0, true, false, false));
            }
            dealPetDamage(player, living, damage);
        }
    }

    private void tryGhastBolt(
            Player player,
            PetEntity activePet,
            PetInstance pet
    ) {

        if (player == null || activePet == null || !activePet.isSpawned() || pet == null) {
            return;
        }
        var display = activePet.getEntity();
        if (display == null || !display.isValid()) {
            return;
        }
        List<LivingEntity> targets =
                nearestHostiles(display.getLocation(), 14.0, 1);
        if (targets.isEmpty() || !chargeReady(player, pet)) {
            return;
        }
        LivingEntity target = targets.get(0);
        double damage =
                PetSkill.chargeDamage(PetSkill.GHAST_BOLT, pet.getRarity(), pet.getLevel());
        Location start = display.getLocation().clone().add(0, 0.35, 0);
        start.getWorld().playSound(start, Sound.ENTITY_GHAST_SHOOT, 0.7f, 1.15f);
        new BukkitRunnable() {
            int tick = 0;
            Location cursor = start.clone();

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || tick++ > 10) {
                    if (target.isValid() && !target.isDead()) {
                        Location hit = target.getEyeLocation();
                        hit.getWorld().spawnParticle(Particle.EXPLOSION, hit, 2, 0.15, 0.15, 0.15, 0.0);
                        hit.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 1.35f);
                        target.setFireTicks(Math.max(target.getFireTicks(), 40));
                        dealPetDamage(player, target, damage);
                    }
                    cancel();
                    return;
                }
                Location end = target.getEyeLocation();
                Vector delta = end.toVector().subtract(cursor.toVector());
                if (delta.lengthSquared() > 0.01) {
                    cursor.add(delta.normalize().multiply(1.15));
                }
                cursor.getWorld().spawnParticle(Particle.FLAME, cursor, 3, 0.08, 0.08, 0.08, 0.0);
                cursor.getWorld().spawnParticle(Particle.SMOKE, cursor, 2, 0.08, 0.08, 0.08, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    /*
     * =========================================================
     * HAWK
     * =========================================================
     *
     * First space = normal jump and arms a short double-tap window.
     * Second space in the air (flight toggle) = hop + glide.
     */

    @EventHandler
    public void onHawkJump(
            PlayerJumpEvent event
    ) {

        Player player =
                event.getPlayer();

        PetEntity activePet =
                activeHawk(
                        player
                );

        if (activePet == null) {
            return;
        }

        if (!hawkReady(
                player,
                activePet.getPetInstance(),
                false
        )) {

            showHawkCooldown(
                    player,
                    activePet.getPetInstance()
            );

            return;
        }

        armHawkDoubleTap(
                player
        );
    }

    @EventHandler
    public void onHawkToggleFlight(
            PlayerToggleFlightEvent event
    ) {

        Player player =
                event.getPlayer();

        if (player.getGameMode()
                == GameMode.CREATIVE
                || player.getGameMode()
                == GameMode.SPECTATOR) {

            return;
        }

        if (!hawkFlightGranted.contains(
                player.getUniqueId()
        )) {

            return;
        }

        event.setCancelled(
                true
        );

        player.setFlying(
                false
        );

        revokeHawkFlight(
                player
        );

        PetEntity activePet =
                activeHawk(
                        player
                );

        if (activePet == null) {
            return;
        }

        if (!hawkReady(
                player,
                activePet.getPetInstance(),
                false
        )) {

            showHawkCooldown(
                    player,
                    activePet.getPetInstance()
            );

            return;
        }

        tryHawkLeap(
                player,
                activePet.getPetInstance()
        );
    }

    private PetEntity activeHawk(
            Player player
    ) {

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {
            return null;
        }

        PetEntity activePet =
                activePetManager.getActivePet(
                        player
                );

        if (activePet == null
                || !activePet.isSpawned()) {

            return null;
        }

        if (PetSkill.fromPet(
                activePet.getPetInstance()
        )
                != PetSkill.HAWK_GLIDE) {

            return null;
        }

        return activePet;
    }

    private void armHawkDoubleTap(
            Player player
    ) {

        if (wearingElytra(
                player
        )
                || player.getGameMode()
                == GameMode.CREATIVE
                || player.getGameMode()
                == GameMode.SPECTATOR) {

            return;
        }

        UUID playerId =
                player.getUniqueId();

        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {

                            if (!player.isOnline()
                                    || hawkGliding.contains(
                                    playerId
                            )
                                    || player.getGameMode()
                                    == GameMode.CREATIVE
                                    || player.getGameMode()
                                    == GameMode.SPECTATOR) {

                                return;
                            }

                            if (!player.getAllowFlight()) {

                                player.setAllowFlight(
                                        true
                                );

                                hawkFlightGranted.add(
                                        playerId
                                );
                            }

                            plugin.getServer()
                                    .getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {

                                                if (!hawkGliding.contains(
                                                        playerId
                                                )) {

                                                    revokeHawkFlight(
                                                            player
                                                    );
                                                }
                                            },
                                            HAWK_DOUBLE_TAP_TICKS
                                    );
                        },
                        3L
                );
    }

    private void revokeHawkFlight(
            Player player
    ) {

        if (!hawkFlightGranted.remove(
                player.getUniqueId()
        )) {

            return;
        }

        if (!player.isOnline()) {
            return;
        }

        if (player.getGameMode()
                == GameMode.CREATIVE
                || player.getGameMode()
                == GameMode.SPECTATOR) {

            return;
        }

        player.setFlying(
                false
        );

        player.setAllowFlight(
                false
        );
    }

    private boolean hawkReady(
            Player player,
            PetInstance pet,
            boolean consume
    ) {

        long now =
                nowTicks();

        long readyAt =
                hawkReadyAt.getOrDefault(
                        player.getUniqueId(),
                        0L
                );

        if (now < readyAt) {
            return false;
        }

        if (consume) {

            hawkReadyAt.put(
                    player.getUniqueId(),
                    now
                            + PetSkill.hawkCooldownTicks(
                            pet.getLevel()
                    )
            );
        }

        return true;
    }

    private void showHawkCooldown(
            Player player,
            PetInstance pet
    ) {

        long remaining =
                hawkReadyAt.getOrDefault(
                        player.getUniqueId(),
                        0L
                )
                        - nowTicks();

        if (remaining <= 0) {
            return;
        }

        double seconds =
                remaining
                        / 20.0;

        player.sendActionBar(
                net.kyori.adventure.text.Component.text(
                        "Hawk recharging: "
                                + PetSkill.formatNumber(seconds)
                                + "s"
                )
        );
    }

    private boolean wearingElytra(
            Player player
    ) {

        EntityEquipment equipment =
                player.getEquipment();

        if (equipment == null) {
            return false;
        }

        ItemStack chest =
                equipment.getChestplate();

        return chest != null
                && chest.getType()
                == Material.ELYTRA;
    }

    private void tryHawkLeap(
            Player player,
            PetInstance pet
    ) {

        UUID playerId =
                player.getUniqueId();

        if (hawkGliding.contains(
                playerId
        )) {

            return;
        }

        if (player.getGameMode()
                == GameMode.SPECTATOR
                || player.getGameMode()
                == GameMode.CREATIVE
                || player.isInsideVehicle()
                || player.isSwimming()
                || wearingElytra(
                player
        )) {

            return;
        }

        if (player.getLocation()
                .getBlock()
                .isLiquid()) {

            return;
        }

        if (!hawkReady(
                player,
                pet,
                true
        )) {

            showHawkCooldown(
                    player,
                    pet
            );

            return;
        }

        hawkGliding.add(
                playerId
        );

        Vector direction =
                player.getLocation()
                        .getDirection();

        direction.setY(
                0
        );

        if (direction.lengthSquared()
                < 0.01) {

            direction =
                    new Vector(
                            0,
                            0,
                            1
                    );
        }

        direction.normalize()
                .multiply(
                        0.55
                );

        direction.setY(
                1.15
        );

        player.setVelocity(
                direction
        );

        player.setGliding(
                true
        );

        player.getWorld()
                .playSound(
                        player.getLocation(),
                        Sound.ENTITY_PARROT_FLY,
                        0.9f,
                        0.7f
                );

        player.getWorld()
                .spawnParticle(
                        Particle.CLOUD,
                        player.getLocation()
                                .add(
                                        0,
                                        0.2,
                                        0
                                ),
                        12,
                        0.35,
                        0.1,
                        0.35,
                        0.02
                );

        new BukkitRunnable() {

            int tick = 0;

            @Override
            public void run() {

                if (!hawkGliding.contains(
                        playerId
                )
                        || !player.isOnline()
                        || player.isDead()
                        || player.getLocation()
                        .getBlock()
                        .isLiquid()) {

                    stopHawkGlide(
                            player
                    );

                    cancel();

                    return;
                }

                tick++;

                if (tick > 8
                        && player.isOnGround()) {

                    stopHawkGlide(
                            player
                    );

                    cancel();

                    return;
                }

                if (tick
                        >= HAWK_GLIDE_MAX_TICKS) {

                    stopHawkGlide(
                            player
                    );

                    cancel();

                    return;
                }

                player.setGliding(
                        true
                );

                if (tick % 4 == 0) {

                    player.getWorld()
                            .spawnParticle(
                                    Particle.CLOUD,
                                    player.getLocation(),
                                    2,
                                    0.2,
                                    0.05,
                                    0.2,
                                    0.01
                            );
                }
            }

        }.runTaskTimer(
                plugin,
                1L,
                1L
        );
    }

    private void stopHawkGlide(
            Player player
    ) {

        UUID playerId =
                player.getUniqueId();

        hawkGliding.remove(
                playerId
        );

        hawkNoFallUntil.put(
                playerId,
                nowTicks() + 20L
        );

        revokeHawkFlight(
                player
        );

        if (player.isOnline()) {

            player.setGliding(
                    false
            );
        }
    }

    @EventHandler
    public void onHawkGlideToggle(
            EntityToggleGlideEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player player)) {

            return;
        }

        if (!hawkGliding.contains(
                player.getUniqueId()
        )) {

            return;
        }

        if (!event.isGliding()) {

            event.setCancelled(
                    true
            );
        }
    }

    @EventHandler(
            ignoreCancelled = true
    )
    public void onHawkFallDamage(
            EntityDamageEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player player)) {

            return;
        }

        if (event.getCause()
                != EntityDamageEvent.DamageCause.FALL) {

            return;
        }

        UUID playerId =
                player.getUniqueId();

        if (hawkGliding.contains(
                playerId
        )
                || nowTicks()
                < hawkNoFallUntil.getOrDefault(
                playerId,
                0L
        )
                || slimeTrampoline(
                player
        )) {

            event.setCancelled(
                    true
            );
        }
    }

    /*
     * =========================================================
     * OCELOT
     * =========================================================
     */

    @EventHandler(
            ignoreCancelled = true
    )
    public void onOcelotFish(
            PlayerFishEvent event
    ) {

        if (event.getState()
                != PlayerFishEvent.State.FISHING) {

            return;
        }

        Player player =
                event.getPlayer();

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {
            return;
        }

        PetEntity activePet =
                activePetManager.getActivePet(
                        player
                );

        if (activePet == null
                || !activePet.isSpawned()) {

            return;
        }

        PetInstance pet =
                activePet.getPetInstance();

        if (PetSkill.fromPet(pet)
                != PetSkill.OCELOT_FISHING
                && PetSkill.fromPet(pet)
                != PetSkill.COD_SCHOOL) {

            return;
        }

        org.bukkit.entity.FishHook hook =
                event.getHook();

        if (hook == null) {
            return;
        }

        double factor =
                PetSkill.fromPet(pet)
                        == PetSkill.COD_SCHOOL
                        ? PetSkill.codFishingWaitFactor(
                        pet.getLevel()
                )
                        : PetSkill.fishingWaitFactor(
                        pet.getLevel()
                );

        try {

            int minWait =
                    Math.max(
                            20,
                            (int) Math.round(
                                    hook.getMinWaitTime()
                                            * factor
                            )
                    );

            int maxWait =
                    Math.max(
                            minWait + 20,
                            (int) Math.round(
                                    hook.getMaxWaitTime()
                                            * factor
                            )
                    );

            hook.setMinWaitTime(
                    minWait
            );

            hook.setMaxWaitTime(
                    maxWait
            );

        } catch (Throwable ignored) {
        }
    }

    /*
     * =========================================================
     * SALMON / GLOW SQUID / PARROT / PUFFER
     * =========================================================
     */

    private void applySalmonRun(
            Player player
    ) {

        if (player.isInWater()
                || player.isSwimming()) {

            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.DOLPHINS_GRACE,
                            EFFECT_DURATION_TICKS,
                            0,
                            true,
                            false,
                            true
                    )
            );

            return;
        }

        player.removePotionEffect(
                PotionEffectType.DOLPHINS_GRACE
        );
    }

    private void collectPetSense(
            Player player,
            Set<UUID> nextPets
    ) {

        if (plugin.getPetSpawnManager() == null) {
            return;
        }

        double range =
                PetSkill.petSenseRange();

        double rangeSquared =
                range * range;

        for (PetEntity pet :
                plugin.getPetSpawnManager()
                        .getActivePets()) {

            if (!pet.isSpawned()
                    || !pet.isWild()) {

                continue;
            }

            if (!pet.getEntity()
                    .getWorld()
                    .equals(
                            player.getWorld()
                    )) {

                continue;
            }

            if (pet.getEntity()
                    .getLocation()
                    .distanceSquared(
                            player.getLocation()
                    )
                    > rangeSquared) {

                continue;
            }

            nextPets.add(
                    pet.getEntity()
                            .getUniqueId()
            );
        }
    }

    private void collectLookout(
            Player player,
            Set<UUID> nextMobs
    ) {

        double range =
                PetSkill.lookoutRange();

        for (Entity nearby :
                player.getNearbyEntities(
                        range,
                        range,
                        range
                )) {

            if (!(nearby instanceof Enemy)
                    || !(nearby instanceof LivingEntity living)
                    || living.equals(player)
                    || living.isDead()) {

                continue;
            }

            nextMobs.add(
                    living.getUniqueId()
            );
        }
    }

    private void syncPetSense(
            Set<UUID> nextPets
    ) {

        if (plugin.getPetSpawnManager() == null) {

            sensedPets.clear();
            return;
        }

        for (PetEntity pet :
                plugin.getPetSpawnManager()
                        .getActivePets()) {

            if (!pet.isSpawned()
                    || !pet.isWild()) {

                continue;
            }

            UUID id =
                    pet.getEntity()
                            .getUniqueId();

            boolean sense = nextPets.contains(id);
            pet.setCatchBeacon(sense);
            if (sense) {
                RarityGlow.apply(
                        pet.getEntity(),
                        pet.getPetInstance().getRarity()
                );
            } else {
                RarityGlow.clear(pet.getEntity());
            }
        }

        sensedPets.clear();
        sensedPets.addAll(
                nextPets
        );
    }

    private static boolean holdingWildSight(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(
                new NamespacedKey("aetherion", "item"),
                PersistentDataType.STRING
        );
        return id != null && id.equalsIgnoreCase("wild_sight");
    }

    private void syncLookout(
            Set<UUID> nextMobs
    ) {

        for (UUID id : lookoutMobs) {

            if (nextMobs.contains(id)) {
                continue;
            }

            Entity entity =
                    plugin.getServer()
                            .getEntity(
                                    id
                            );

            if (entity != null) {

                entity.setGlowing(
                        false
                );
            }
        }

        for (UUID id : nextMobs) {

            Entity entity =
                    plugin.getServer()
                            .getEntity(
                                    id
                            );

            if (entity != null) {

                entity.setGlowing(
                        true
                );
            }
        }

        lookoutMobs.clear();
        lookoutMobs.addAll(
                nextMobs
        );
    }

    private void clearBeacons() {

        if (plugin.getPetSpawnManager() != null) {

            for (PetEntity pet :
                    plugin.getPetSpawnManager()
                            .getActivePets()) {

                if (pet.isWild()) {

                    pet.setCatchBeacon(
                            false
                    );
                }
            }
        }

        for (UUID id : lookoutMobs) {

            Entity entity =
                    plugin.getServer()
                            .getEntity(
                                    id
                            );

            if (entity != null) {

                entity.setGlowing(
                        false
                );
            }
        }

        sensedPets.clear();
        lookoutMobs.clear();
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPufferInflate(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player player)) {

            return;
        }

        Entity damager =
                event.getDamager();

        LivingEntity attacker =
                null;

        if (damager instanceof LivingEntity living) {

            attacker =
                    living;

        } else if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter()
                instanceof LivingEntity living) {

            attacker =
                    living;
        }

        if (attacker == null
                || attacker.equals(player)
                || attacker instanceof Player) {

            return;
        }

        ActivePetManager activePetManager =
                plugin.getActivePetManager();

        if (activePetManager == null) {
            return;
        }

        PetEntity activePet =
                activePetManager.getActivePet(
                        player
                );

        if (activePet == null
                || !activePet.isSpawned()) {

            return;
        }

        PetInstance pet =
                activePet.getPetInstance();

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        PotionEffectType type;
        switch (skill) {

            case PUFFER_INFLATE, BEE_STING, WITCH_BREW, SWAMP_HEX ->
                    type = PotionEffectType.POISON;

            case SQUID_INK ->
                    type = PotionEffectType.BLINDNESS;

            case WITHER_AURA ->
                    type = PotionEffectType.WITHER;

            default ->
                    type = null;
        }

        if (type == null) {
            return;
        }

        attacker.addPotionEffect(
                new PotionEffect(
                        type,
                        PetSkill.pufferPoisonTicks(
                                pet.getLevel()
                        ),
                        0,
                        true,
                        false,
                        true
                )
        );
    }
}
