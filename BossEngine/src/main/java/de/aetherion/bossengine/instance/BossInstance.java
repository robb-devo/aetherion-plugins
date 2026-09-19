package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.core.AetherKeys;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.event.BossPhaseChangeEvent;
import de.aetherion.bossengine.model.BossAttributes;
import de.aetherion.bossengine.model.BossEquipment;
import de.aetherion.bossengine.model.BossOptions;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.model.BossTemplate;
import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.PhaseTransition;
import de.aetherion.bossengine.model.SpawnCondition;
import de.aetherion.bossengine.model.TransitionShape;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillTrigger;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.SkeletonUtil;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Live encounter. Bound 1:1 to a tagged {@link LivingEntity}.
 * Combat HP is stored here because vanilla max-health is capped at 1024.
 */
public class BossInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final JavaPlugin plugin;
    private final BossKeys keys;
    private final BossTemplate template;
    private final Location spawnLocation;
    private final SpawnCause spawnCause;
    private final String spawnerId;
    private final SpawnCondition conditions;
    private final DamageTracker damageTracker = new DamageTracker();
    private final List<LivingEntity> minions = new ArrayList<>();
    private final List<BoilingZone> boilingZones = new ArrayList<>();
    private final DragonDirector dragonDirector = new DragonDirector(this);
    private final SparkyDirector sparkyDirector = new SparkyDirector(this);
    private final FrostboundDirector frostboundDirector = new FrostboundDirector(this);
    private final PathwardenDirector pathwardenDirector = new PathwardenDirector(this);
    private final SignatureDirector signatureDirector = new SignatureDirector(this);
    private final SandboxDirector sandboxDirector = new SandboxDirector(this);
    private final TransitionSpectacles spectacles = new TransitionSpectacles(this);
    private final Map<AbstractBossSkill, Long> lastCastTick = new IdentityHashMap<>();
    private final Set<String> announced = new HashSet<>();

    private LivingEntity entity;
    private BossPhase currentPhase;
    private BossPhase pendingPhase;
    private BossState state = BossState.SPAWNING;
    private long ticksAlive;
    private int transitionTick = -1;
    private PhaseTransition runningTransition;
    private int phaseArmorTicks;
    private int internalTeleportDepth;
    private Location phaseReturnFrom;
    private Location lastSeenLocation;
    private boolean leashPulling;
    private double combatMaxHealth;
    private double combatHealth;
    private double raidHealthMul = 1.0;
    private double raidDamageMul = 1.0;
    private int raidPlayers = 1;
    private boolean slamArmed;
    private boolean slamAirborne;
    private int slamTicks;
    private double slamRadius;
    private double slamDamage;
    private int overheatTicks;
    private boolean lightningStormActive;
    private double lightningStormRadius = 22.0;
    private double lightningStormDamage = 8.0;
    private boolean blackHoleActive;
    private double blackHoleRadius = 22.0;
    private double blackHoleKillRadius = 2.15;
    private final AtomicBoolean lootPaid = new AtomicBoolean(false);
    private Location hazardFocus;
    private int missingBodyTicks;
    private long suppressBodyRestoreUntilMs;
    private boolean bodyUnloaded;
    private static final ThreadLocal<BossInstance> STORM_BOLT_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Double> STORM_BOLT_DAMAGE = new ThreadLocal<>();

    public BossInstance(
            JavaPlugin plugin,
            BossKeys keys,
            BossTemplate template,
            Location spawnLocation,
            SpawnCause spawnCause,
            String spawnerId,
            SpawnCondition conditions
    ) {
        this.plugin = plugin;
        this.keys = keys;
        this.template = template;
        this.spawnLocation = spawnLocation.clone();
        this.spawnCause = spawnCause;
        this.spawnerId = spawnerId;
        this.conditions = conditions == null ? template.getConditions() : conditions;
        this.combatMaxHealth = Math.max(1, template.getAttributes().getMaxHealth());
        this.combatHealth = combatMaxHealth;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public boolean tryAnnounce(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        return announced.add(key);
    }

    public boolean tryGlobalSpawnAnnounce() {
        if (plugin instanceof de.aetherion.bossengine.BossEngine engine) {
            return engine.getBossManager().tryGlobalSpawnAnnounce(template.getId());
        }
        return tryAnnounce("spawn-dialog");
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public BossKeys getKeys() {
        return keys;
    }

    public BossTemplate getTemplate() {
        return template;
    }

    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    public Location hazardFocus(boolean recapture) {
        if (recapture || hazardFocus == null || hazardFocus.getWorld() == null) {
            if (entity != null && entity.isValid()) {
                hazardFocus = entity.getLocation().clone();
            } else {
                hazardFocus = spawnLocation.clone();
            }
        }
        return hazardFocus.clone();
    }

    public Location hazardFocus() {
        return hazardFocus(false);
    }

    public SpawnCause getSpawnCause() {
        return spawnCause;
    }

    public String getSpawnerId() {
        return spawnerId;
    }

    public SpawnCondition getConditions() {
        return conditions;
    }

    public DamageTracker getDamageTracker() {
        return damageTracker;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public double getCombatHealth() {
        return combatHealth;
    }

    public double getCombatMaxHealth() {
        return combatMaxHealth;
    }

    public int getRaidPlayers() {
        return raidPlayers;
    }

    public void applyRaidScale(int players) {
        int extra = Math.min(8, Math.max(0, players - 1));
        this.raidPlayers = Math.max(1, extra + 1);
        this.raidHealthMul = 1.0 + (0.35d * extra);
        this.raidDamageMul = 1.0 + (0.10d * extra);
        this.combatMaxHealth = Math.max(1.0, template.getAttributes().getMaxHealth() * raidHealthMul);
        this.combatHealth = combatMaxHealth;
    }

    public double scaleDamage(double base) {
        return base * raidDamageMul;
    }

    public double scaleHealth(double base) {
        return base * raidHealthMul;
    }

    public void bindEntity(LivingEntity entity) {
        this.entity = entity;
        this.state = BossState.ALIVE;
        try {
            applyVisuals(template.getDisplayName());
            applySlimeSize();
            applyAttributes(template.getAttributes(), true);
            applyEquipment(template.getEquipment());
            applyOptions(template.getOptions());
            SkeletonUtil.prepareBoss(entity);
            if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
            if (entity instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            }
            if (entity instanceof org.bukkit.entity.Wither wither) {
                wither.setInvulnerabilityTicks(0);
            }
            template.phaseForHealth(100).ifPresent(this::forcePhase);
            syncVanillaHealth();
            dragonDirector.onBind();
            frostboundDirector.onBind();
            pathwardenDirector.onBind();
            sandboxDirector.onBind();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Boss '" + template.getId() + "' failed to apply stats", exception);
        }
    }

    /**
     * Same encounter, new body. Keeps combat HP, phase, and damage. Does not fire spawn skills.
     */
    public void rebindBody(LivingEntity entity) {
        this.entity = entity;
        this.state = BossState.ALIVE;
        try {
            applyVisuals(currentPhase != null && currentPhase.getDisplayName() != null
                    ? currentPhase.getDisplayName()
                    : template.getDisplayName());
            applySlimeSize();
            applyAttributes(activeAttributes(), false);
            applyEquipment(template.getEquipment());
            applyOptions(template.getOptions());
            SkeletonUtil.prepareBoss(entity);
            if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
            if (entity instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            }
            if (entity instanceof org.bukkit.entity.Wither wither) {
                wither.setInvulnerabilityTicks(0);
            }
            if (currentPhase != null) {
                forcePhase(currentPhase);
            }
            syncVanillaHealth();
            dragonDirector.onBind();
            frostboundDirector.onBind();
            pathwardenDirector.onBind();
            sandboxDirector.onBind();
            // Soft-arena bosses stay where they were — never blink home on rebind.
            if (!refusesHardArenaSnap()) {
                snapToArena();
            }
            clearBodyUnloaded();
            clearMissingBodyTicks();
            if (isTransitioning() || phaseArmorTicks > 0) {
                entity.setInvulnerable(true);
                if (runningTransition != null && runningTransition.isFreezeAi() && entity instanceof Mob mob) {
                    mob.setAI(false);
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Boss '" + template.getId() + "' failed to restore its body", exception);
        }
    }

    public void reapplyCombatStats() {
        if (!isAlive()) {
            return;
        }
        applyAttributes(activeAttributes(), false);
        applyVisuals(currentPhase != null && currentPhase.getDisplayName() != null
                ? currentPhase.getDisplayName()
                : template.getDisplayName());
        SkeletonUtil.prepareBoss(entity);
        if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (entity instanceof org.bukkit.entity.Wither wither) {
            wither.setInvulnerabilityTicks(0);
        }
        if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        syncVanillaHealth();
    }

    public BossPhase getCurrentPhase() {
        return currentPhase;
    }

    public BossPhase getPendingPhase() {
        return pendingPhase;
    }

    public BossState getState() {
        return state;
    }

    public void setState(BossState state) {
        this.state = state;
    }

    public boolean isAlive() {
        return state == BossState.ALIVE
                && entity != null
                && entity.isValid()
                && !entity.isDead();
    }

    /**
     * True while this encounter still owns its spawn slot.
     * Unloaded chunks make {@link #isAlive()} false even though the boss is not dead.
     */
    public boolean occupiesSpawnSlot() {
        return state == BossState.ALIVE;
    }

    public boolean hasLivingBody() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public boolean isCinematicDying() {
        return state == BossState.ALIVE && (dragonDirector.isDying() || sparkyDirector.isDying()
                || frostboundDirector.isDying() || pathwardenDirector.isDying()
                || signatureDirector.isDying());
    }

    public void abortCinematic() {
        dragonDirector.abortDeath();
        sparkyDirector.abort();
        frostboundDirector.abort();
        pathwardenDirector.abort();
        signatureDirector.abort();
        sandboxDirector.abort();
    }

    public boolean isEncounterActive() {
        return isAlive() || isCinematicDying();
    }

    public double getAttackDamage() {
        return activeAttributes().getAttackDamage() * raidDamageMul;
    }

    public boolean markLootPaid() {
        return lootPaid.compareAndSet(false, true);
    }

    public void addMinion(LivingEntity minion) {
        minions.add(minion);
    }

    public List<LivingEntity> getMinions() {
        return minions;
    }

    public void cleanupInvalidMinions() {
        minions.removeIf(minion -> minion == null || !minion.isValid() || minion.isDead());
    }

    public void despawnMinions() {
        for (LivingEntity minion : minions) {
            if (minion != null && minion.isValid()) {
                minion.remove();
            }
        }
        minions.clear();
        clearBoilingZones();
        lightningStormActive = false;
        blackHoleActive = false;
        spectacles.finish();
        de.aetherion.bossengine.skill.t2.T2Mechanics.clearInstanceProps(this);
    }

    public void despawnMinions(List<UUID> ids) {
        Iterator<LivingEntity> iterator = minions.iterator();
        while (iterator.hasNext()) {
            LivingEntity minion = iterator.next();
            if (minion != null && ids.contains(minion.getUniqueId())) {
                if (minion.isValid()) {
                    minion.remove();
                }
                iterator.remove();
            }
        }
    }

    public long getTicksAlive() {
        return ticksAlive;
    }

    public boolean isTransitioning() {
        return transitionTick >= 0 && pendingPhase != null;
    }

    public boolean isDamageBlocked() {
        return isTransitioning()
                || phaseArmorTicks > 0
                || dragonDirector.isDying()
                || sparkyDirector.isDying()
                || frostboundDirector.isDying()
                || pathwardenDirector.isDying()
                || signatureDirector.isDying()
                || sandboxDirector.blocksDamage()
                || de.aetherion.bossengine.skill.t2.T2Mechanics.isBurrowing(this);
    }

    public SandboxDirector sandbox() {
        return sandboxDirector;
    }

    public boolean isInternalTeleport() {
        return internalTeleportDepth > 0;
    }

    public void runInternalTeleport(Runnable action) {
        if (action == null) {
            return;
        }
        // Hollow Lurker: hard ban on plugin teleports. Any caller is a no-op.
        if (isHollowLurker()) {
            return;
        }
        internalTeleportDepth++;
        try {
            action.run();
        } finally {
            internalTeleportDepth--;
        }
    }

    /**
     * True when vanilla just deleted the body but the encounter is still in progress.
     */
    public boolean shouldIgnoreVanillaDeath() {
        if (isCinematicDying()) {
            return true;
        }
        return isTransitioning() || phaseArmorTicks > 0 || combatHealth > 1.0;
    }

    public boolean startDeathCinematic() {
        if (isCinematicDying()) {
            return true;
        }
        de.aetherion.bossengine.skill.t2.T2Mechanics.clearInstanceProps(this);
        if (dragonDirector.beginDeath() || sparkyDirector.beginDeath()
                || frostboundDirector.beginDeath() || pathwardenDirector.beginDeath()
                || signatureDirector.beginDeath()) {
            return true;
        }
        return signatureDirector.beginFallbackDeath();
    }

    /**
     * Applies MMO/vanilla damage to the combat HP pool.
     * Caps at the next phase so a oneshot cannot skip the frenzy animation.
     *
     * @return {@code true} if the boss should die from this hit
     */
    public boolean absorbDamage(double amount) {
        if (!isAlive() || amount <= 0) {
            return false;
        }
        if (isDamageBlocked()) {
            return false;
        }

        BossPhase next = nextSequentialPhase();
        if (next != null) {
            double gate = combatMaxHealth * (next.getHealthPercent() / 100.0);
            if (combatHealth - amount <= gate) {
                combatHealth = Math.max(1, gate - Math.max(1.0, combatMaxHealth * 0.002));
                syncVanillaHealth();
                PhaseTransition transition = resolveTransition(next);
                if (transition.isEnabled()) {
                    beginTransition(next);
                } else {
                    completePhase(next);
                }
                return false;
            }
        }

        combatHealth = Math.max(0, combatHealth - amount);
        if (combatHealth <= 0) {
            combatHealth = 1;
            syncVanillaHealth();
            startDeathCinematic();
            return false;
        }
        syncVanillaHealth();
        checkPhase();
        return false;
    }

    public void syncVanillaHealth() {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        if (combatHealth <= 0) {
            return;
        }
        double vanillaMax = Math.max(1, AttributeUtil.vanillaMaxHealth(entity));
        double visual = Math.max(1.0, vanillaMax * (combatHealth / Math.max(1.0, combatMaxHealth)));
        visual = Math.min(vanillaMax, visual);
        try {
            if (Math.abs(entity.getHealth() - visual) > 0.05) {
                entity.setHealth(visual);
            }
        } catch (IllegalArgumentException ignored) {
            // another plugin changed max-health underneath us
        }
    }

    /**
     * @return {@code true} when a death cinematic finished and loot should be paid
     */
    public boolean tick() {
        if (dragonDirector.isDying()) {
            ticksAlive++;
            tickDragon();
            return dragonDirector.tickDeath();
        }
        if (sparkyDirector.isDying()) {
            ticksAlive++;
            return sparkyDirector.tick();
        }
        if (frostboundDirector.isDying()) {
            ticksAlive++;
            return frostboundDirector.tick();
        }
        if (pathwardenDirector.isDying()) {
            ticksAlive++;
            return pathwardenDirector.tick();
        }
        if (signatureDirector.isDying()) {
            ticksAlive++;
            return signatureDirector.tick();
        }
        if (!isAlive()) {
            return false;
        }
        ticksAlive++;
        tickPhaseArmor();
        polishEntityState();
        rememberBodyLocation();
        if (entity != null && entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
        unstickIfNeeded();
        frostboundDirector.tick();
        pathwardenDirector.tick();
        sandboxDirector.tick();
        if (ticksAlive == 20 || ticksAlive == 100) {
            SkeletonUtil.prepareBoss(entity);
            if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
        }
        if (isTransitioning()) {
            if (entity == null || !entity.isValid()) {
                return false;
            }
            tickTransition();
            keepInWater();
            tickLightningStorm();
            tickBlackHole();
            enforceLeash();
            return false;
        }
        if (!frostboundDirector.reflecting()) {
            tickSlam();
        }
        tickWaterChase();
        keepInWater();
        tickBoilingZones();
        tickDragon();
        tickLightningStorm();
        tickBlackHole();
        tickOverheat();
        de.aetherion.bossengine.skill.t2.T2Mechanics.tickBurrow(this);
        checkPhase();
        tickTimerSkills();
        enforceLeash();
        return false;
    }

    public void armSlam(double radius, double damage) {
        if (entity == null) {
            return;
        }
        this.slamArmed = true;
        this.slamAirborne = !entity.isOnGround();
        this.slamTicks = 0;
        this.slamRadius = Math.max(1.5, radius);
        this.slamDamage = Math.max(1.0, scaleDamage(damage));
        drawSlamTelegraph(entity.getLocation());
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.85f, 0.45f);
    }

    public boolean isSlamPending() {
        return slamArmed;
    }

    public void armOverheat(int ticks) {
        this.overheatTicks = Math.max(this.overheatTicks, Math.max(1, ticks));
    }

    public boolean isOverheated() {
        return overheatTicks > 0;
    }

    private void tickOverheat() {
        if (overheatTicks <= 0) {
            return;
        }
        overheatTicks--;
        de.aetherion.bossengine.skill.t2.T2Mechanics.tickFireTrail(this);
    }

    public void checkPhase() {
        if (!isAlive() || isTransitioning()) {
            return;
        }

        double percent = healthPercent();
        BossPhase next = template.phaseForHealth(percent).orElse(null);

        if (next == null || (currentPhase != null && currentPhase.getId().equals(next.getId()))) {
            return;
        }
        if (currentPhase != null && next.getHealthPercent() > currentPhase.getHealthPercent()) {
            return;
        }

        BossPhaseChangeEvent event = new BossPhaseChangeEvent(this, currentPhase, next);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (resolveTransition(next).isEnabled()) {
            beginTransition(next);
            return;
        }

        completePhase(next);
    }

    private BossPhase nextSequentialPhase() {
        List<BossPhase> phases = template.getPhases();
        if (phases == null || phases.isEmpty()) {
            return null;
        }
        double currentThreshold = currentPhase == null ? Double.POSITIVE_INFINITY : currentPhase.getHealthPercent();
        BossPhase next = null;
        for (BossPhase phase : phases) {
            if (phase.getHealthPercent() >= currentThreshold) {
                continue;
            }
            if (next == null || phase.getHealthPercent() > next.getHealthPercent()) {
                next = phase;
            }
        }
        return next;
    }

    private void beginTransition(BossPhase next) {
        if (isTransitioning() && pendingPhase != null && pendingPhase.getId().equals(next.getId())) {
            return;
        }
        pendingPhase = next;
        transitionTick = 0;
        runningTransition = resolveTransition(next);
        PhaseTransition transition = runningTransition;
        capturePhaseReturn();
        if (template != null && "sparky".equalsIgnoreCase(template.getId())) {
            de.aetherion.bossengine.skill.t2.T2Mechanics.clearPropsOfKind(this, "blast_core");
        }
        if (entity != null) {
            if (transition.isInvulnerable()) {
                entity.setInvulnerable(true);
            }
            if (transition.isFreezeAi() && entity instanceof Mob mob) {
                mob.setAI(false);
            }
            if (transition.getShape() == TransitionShape.HOVER_STORM) {
                // Walk phase keeps gravity/AI feel; lift starts once he reaches center.
                entity.setGlowing(true);
                entity.setFallDistance(0);
                if (entity instanceof Mob mob) {
                    mob.setAI(false);
                    mob.setAware(false);
                }
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1.1f, 0.55f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.1f, 0.55f);
            } else if (transition.getShape() == TransitionShape.INK_SPIN) {
                entity.setVelocity(new Vector(0, 0, 0));
                entity.setFallDistance(0);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1.35f, 0.45f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.7f);
                entity.getWorld().spawnParticle(Particle.SQUID_INK, entity.getLocation(), 40, 1.1, 0.8, 1.1, 0.08);
            } else if (transition.getShape() == TransitionShape.FIRE_SPIRAL) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.7f);
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
            } else if (transition.getShape() == TransitionShape.LIGHTNING_STORM) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.85f, 0.5f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.18f, 1.15f);
            } else if (transition.getShape() == TransitionShape.BLACK_HOLE) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.45f);
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.55f);
            } else if (transition.getShape() == TransitionShape.VOID_TORNADO) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.2f, 0.55f);
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.85f, 0.6f);
            } else if (transition.getShape() == TransitionShape.BEAM_SPIN) {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.15f, 0.55f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
            } else {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.85f, 0.55f);
            }
        }
    }

    private void tickTransition() {
        PhaseTransition transition = runningTransition;
        if (transition == null) {
            transition = resolveTransition(pendingPhase);
            runningTransition = transition;
        }
        if (!transition.isEnabled()) {
            completePhase(pendingPhase);
            return;
        }
        transitionTick++;
        double progress = (double) transitionTick / Math.max(1, transition.getDurationTicks());
        if (transition.getShape() == TransitionShape.HOVER_STORM) {
            tickHoverStorm(transition, progress);
        } else if (transition.getShape() == TransitionShape.INK_SPIN) {
            tickInkSpin(transition, progress);
        } else if (transition.getShape() == TransitionShape.FIRE_SPIRAL) {
            tickPhaseReturn(progress);
            dragonDirector.tickFireSpiral(transition, transitionTick, transition.getDurationTicks());
        } else if (transition.getShape() == TransitionShape.LIGHTNING_STORM) {
            tickPhaseReturn(progress);
            dragonDirector.tickLightningCharge(transition, transitionTick, transition.getDurationTicks());
        } else if (transition.getShape() == TransitionShape.BLACK_HOLE) {
            tickPhaseReturn(progress);
            dragonDirector.tickBlackHole(transition, transitionTick, transition.getDurationTicks());
        } else if (transition.getShape() == TransitionShape.VOID_TORNADO) {
            tickPhaseReturn(progress);
            spectacles.tickVoidTornado(transition, transitionTick, transition.getDurationTicks());
        } else if (transition.getShape() == TransitionShape.BEAM_SPIN) {
            tickPhaseReturn(progress);
            spectacles.tickBeamSpin(transition, transitionTick, transition.getDurationTicks());
        } else {
            tickPhaseReturn(progress);
            drawTransition(transition, progress);
        }
        if (transitionTick >= transition.getDurationTicks()) {
            if (transition.getShape() == TransitionShape.HOVER_STORM) {
                finishHoverStorm(transition);
            }
            spectacles.finish();
            explodeTransition(transition);
            completePhase(pendingPhase);
        }
    }

    private void completePhase(BossPhase phase) {
        PhaseTransition finished = runningTransition;
        if (entity != null) {
            entity.setInvulnerable(true);
            if (!(entity instanceof org.bukkit.entity.EnderDragon)) {
                entity.setGravity(true);
            }
            entity.setGlowing(template.getOptions().isGlowing());
            if (entity instanceof Mob mob) {
                mob.setAI(true);
            }
            if (isTransitioning()) {
                if (finished == null || !finished.isExplode()) {
                    if (!(entity instanceof org.bukkit.entity.EnderDragon)) {
                        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.2f, 0.55f);
                    }
                }
            }
        }
        pendingPhase = null;
        transitionTick = -1;
        runningTransition = null;
        phaseReturnFrom = null;
        phaseArmorTicks = 20;
        forcePhase(phase);
        firePhaseSkills();
    }

    private PhaseTransition resolveTransition(BossPhase phase) {
        if (phase == null) {
            return PhaseTransition.none();
        }
        PhaseTransition configured = phase.getTransition();
        if (configured != null && configured.isEnabled()) {
            return configured;
        }
        if (phase.getHealthPercent() < 99.9) {
            return PhaseTransition.guard();
        }
        return PhaseTransition.none();
    }

    private void tickPhaseArmor() {
        if (phaseArmorTicks <= 0) {
            return;
        }
        phaseArmorTicks--;
        if (phaseArmorTicks == 0 && entity != null && entity.isValid() && !isTransitioning()) {
            entity.setInvulnerable(false);
        }
    }

    private void firePhaseSkills() {
        if (currentPhase != null && currentPhase.getHealthPercent() >= 100.0) {
            return;
        }
        if (plugin instanceof de.aetherion.bossengine.BossEngine engine) {
            engine.getBossManager().getSkillManager().execute(this, SkillTrigger.ON_PHASE);
        }
    }

    private void drawTransition(PhaseTransition transition, double progress) {
        if (entity == null) {
            return;
        }
        double radius = transition.getStartRadius()
                + (transition.getEndRadius() - transition.getStartRadius()) * Math.min(1.0, progress);
        if (transition.getShape() == TransitionShape.SPHERE) {
            drawSphere(transition, radius);
            return;
        }
        drawCircle(transition, radius);
    }

    private void drawCircle(PhaseTransition transition, double radius) {
        Location center = entity.getLocation().clone().add(0, transition.getHeight(), 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int points = transition.getPoints();
        Particle particle = transition.getParticle();
        Particle extra = transition.getExtraParticle();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(particle, x, center.getY(), z, 2, 0, 0.05, 0, 0);
            if (extra != null) {
                world.spawnParticle(extra, x, center.getY() + 0.35, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void drawSphere(PhaseTransition transition, double radius) {
        Location center = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int points = transition.getPoints();
        Particle particle = transition.getParticle();
        Particle extra = transition.getExtraParticle();
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));
        int last = Math.max(2, points);
        for (int i = 0; i < last; i++) {
            double y = 1.0 - (i / (double) (last - 1)) * 2.0;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = phi * i;
            double x = center.getX() + Math.cos(theta) * ring * radius;
            double py = center.getY() + y * radius;
            double z = center.getZ() + Math.sin(theta) * ring * radius;
            world.spawnParticle(particle, x, py, z, 2, 0, 0, 0, 0);
            if (extra != null) {
                world.spawnParticle(extra, x, py, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void explodeTransition(PhaseTransition transition) {
        if (entity == null || transition == null || !transition.isExplode()) {
            return;
        }
        Location center = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.35f, 0.7f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.15f, 0.65f);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.55f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, center, 90, 1.1, 1.1, 1.1, 0.08);
        world.spawnParticle(Particle.LAVA, center, 28, 0.9, 0.9, 0.9, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 40, 1.4, 0.4, 1.4, 0.15);

        double radius = transition.getExplodeRadius();
        drawShockwaveRing(center, radius, transition.getParticle());
        double damage = scaleDamage(transition.getExplodeDamage());
        double knockback = transition.getExplodeKnockback();
        double radiusSq = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            if (damage > 0) {
                BossHits.hurt(player, entity, damage);
            }
            if (knockback <= 0) {
                continue;
            }
            Vector away = player.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.0001) {
                away = new Vector(0, 0.45, 0);
            } else {
                away.normalize().multiply(knockback);
                away.setY(Math.max(0.35, away.getY()));
            }
            player.setVelocity(player.getVelocity().add(away));
        }
    }

    private void tickHoverStorm(PhaseTransition transition, double progress) {
        if (entity == null || spawnLocation == null) {
            return;
        }
        World world = spawnLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = standLocation(spawnLocation.clone());
        double hover = Math.max(3.5, transition.getHoverHeight());
        double distSq = horizontalDistanceSquared(entity.getLocation(), center);
        boolean atCenter = distSq <= 2.8 * 2.8;
        boolean walking = progress < 0.38 && !atCenter;

        if (walking) {
            Vector to = center.toVector().subtract(entity.getLocation().toVector());
            to.setY(0);
            if (to.lengthSquared() > 0.01) {
                // Fast charge to mid — readable run, not a blink.
                Vector step = to.normalize().multiply(0.52);
                step.setY(Math.max(-0.1, Math.min(0.28, center.getY() - entity.getLocation().getY())));
                entity.setGravity(true);
                entity.setVelocity(step);
            }
            Location feet = entity.getLocation();
            world.spawnParticle(Particle.CLOUD, feet, 4, 0.25, 0.05, 0.25, 0.01);
            world.spawnParticle(Particle.CRIT, feet.clone().add(0, 0.4, 0), 3, 0.2, 0.15, 0.2, 0.02);
            if (transitionTick % 8 == 0) {
                world.playSound(feet, Sound.ENTITY_PIGLIN_BRUTE_AMBIENT, 0.7f, 0.55f);
            }
            return;
        }

        boolean slamming = progress >= 0.82;
        Location dest = center.clone();
        if (entity != null) {
            dest.setYaw(entity.getLocation().getYaw());
        }

        if (!slamming) {
            if (entity.hasGravity()) {
                entity.setGravity(false);
                world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 0.7f);
            }
            double riseProgress = Math.min(1.0, Math.max(0.0, (progress - 0.38) / 0.12));
            if (atCenter && progress < 0.38) {
                riseProgress = Math.min(1.0, transitionTick / 12.0);
            }
            double bob = Math.sin(progress * Math.PI * 8.0) * 0.65 * riseProgress;
            dest.add(0, hover * riseProgress + bob, 0);
            entity.setVelocity(new Vector(0, 0, 0));
            Location hoverDest = dest.clone();
            runInternalTeleport(() -> entity.teleport(hoverDest));
            entity.setFallDistance(0);

            Location aura = dest.clone().add(0, entity.getHeight() * 0.55, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, aura, 10, 0.55, 0.7, 0.55, 0.08);
            world.spawnParticle(Particle.END_ROD, aura, 3, 0.35, 0.45, 0.35, 0.01);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, dest.clone().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.01);

            int hoverStart = Math.max(1, (int) Math.round(transition.getDurationTicks() * 0.38));
            int hoverEnd = Math.max(hoverStart + 8, (int) Math.round(transition.getDurationTicks() * 0.82));
            int bolts = Math.max(2, transition.getLightningCount());
            for (int i = 1; i <= bolts; i++) {
                int strikeTick = hoverStart + Math.max(4, (hoverEnd - hoverStart) * i / (bolts + 1));
                if (transitionTick == strikeTick) {
                    strikeHoverLightning(dest);
                }
            }
            if (transitionTick % 10 == 0) {
                damagePlayersUnder(transition);
            }
            return;
        }

        double slamProgress = Math.min(1.0, (progress - 0.82) / 0.18);
        double eased = slamProgress * slamProgress;
        dest.add(0, Math.max(0.05, hover * (1.0 - eased)), 0);
        Location slamDest = dest;
        runInternalTeleport(() -> entity.teleport(slamDest));
        entity.setVelocity(new Vector(0, -1.9, 0));
        entity.setFallDistance(0);
        world.spawnParticle(Particle.CLOUD, dest, 8, 0.45, 0.15, 0.45, 0.02);
        world.spawnParticle(Particle.CRIT, dest, 6, 0.3, 0.2, 0.3, 0.05);
        if (slamProgress > 0.7) {
            world.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.55f, 0.45f);
        }
    }

    private Location hoverAnchor() {
        // Storm always resolves on the arena center after the run-in.
        Location base = standLocation(spawnLocation.clone());
        if (entity != null) {
            base.setYaw(entity.getLocation().getYaw());
            base.setPitch(0);
        }
        return base;
    }

    private void strikeHoverLightning(Location dest) {
        World world = dest.getWorld();
        if (world == null) {
            return;
        }
        Location bolt = dest.clone().add(0, entity.getHeight() * 0.2, 0);
        world.strikeLightningEffect(bolt);
        world.playSound(bolt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.15f, 0.85f);
        world.playSound(bolt, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.05f, 0.7f);
        world.spawnParticle(Particle.FLASH, bolt, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, bolt, 28, 0.7, 1.1, 0.7, 0.25);
        entity.setGlowing(true);
        entity.setFireTicks(0);
    }

    private void damagePlayersUnder(PhaseTransition transition) {
        if (entity == null) {
            return;
        }
        World world = entity.getWorld();
        double radius = transition.getUnderRadius();
        double radiusSq = radius * radius;
        double damage = scaleDamage(transition.getUnderDamage());
        if (damage <= 0) {
            return;
        }
        Location origin = entity.getLocation();
        for (Player player : world.getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            Location at = player.getLocation();
            double dx = at.getX() - origin.getX();
            double dz = at.getZ() - origin.getZ();
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }
            if (at.getY() > origin.getY() + 0.4) {
                continue;
            }
            BossHits.hurt(player, entity, damage);
        }
    }

    private void finishHoverStorm(PhaseTransition transition) {
        if (entity == null || spawnLocation == null) {
            return;
        }
        entity.setGravity(true);
        Location land = hoverAnchor();
        runInternalTeleport(() -> entity.teleport(land));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        entity.setGlowing(template.getOptions().isGlowing());
        entity.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.55f);
        entity.getWorld().playSound(land, Sound.BLOCK_ANVIL_LAND, 1.35f, 0.55f);
        if (transition != null) {
            entity.getWorld().spawnParticle(
                    Particle.EXPLOSION_EMITTER,
                    land.clone().add(0, 0.4, 0),
                    1,
                    0,
                    0,
                    0,
                    0
            );
        }
    }

    private void tickSlam() {
        if (!slamArmed || entity == null) {
            return;
        }
        slamTicks++;
        if (slamTicks % 4 == 0) {
            drawSlamTelegraph(entity.getLocation());
        }
        if (!slamAirborne) {
            if (!entity.isOnGround()) {
                slamAirborne = true;
            } else if (slamTicks == 6) {
                Vector nudge = entity.getVelocity().clone();
                nudge.setY(Math.max(0.72, nudge.getY() + 0.55));
                entity.setVelocity(nudge);
                entity.setFallDistance(0);
            } else if (slamTicks >= 24) {
                performSlam();
            }
            return;
        }
        if (entity.isOnGround()) {
            performSlam();
        } else if (slamTicks >= 70) {
            performSlam();
        }
    }

    private void performSlam() {
        slamArmed = false;
        slamAirborne = false;
        slamTicks = 0;
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.setFallDistance(0);
        Location center = entity.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        de.aetherion.bossengine.fx.CombatTheatrics.slam(this, center);
        double radiusSq = slamRadius * slamRadius;
        for (Player player : world.getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            BossHits.hurt(player, entity, slamDamage);
            Vector away = player.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.0001) {
                away = new Vector(0, 0.45, 0);
            } else {
                away.normalize().multiply(0.55);
                away.setY(Math.max(0.32, away.getY()));
            }
            player.setVelocity(player.getVelocity().add(away));
        }
    }

    private void drawSlamTelegraph(Location center) {
        if (center == null || center.getWorld() == null || slamRadius <= 0) {
            return;
        }
        World world = center.getWorld();
        int points = Math.max(20, (int) (slamRadius * 8));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Location rim = center.clone().add(Math.cos(angle) * slamRadius, 0.12, Math.sin(angle) * slamRadius);
            world.spawnParticle(Particle.CRIT, rim, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.SMOKE, rim, 1, 0, 0, 0, 0);
            }
        }
    }

    private void drawShockwaveRing(Location center, double radius, Particle particle) {
        World world = center.getWorld();
        if (world == null || radius <= 0) {
            return;
        }
        Particle ring = particle == null ? Particle.CLOUD : particle;
        int rings = 5;
        for (int r = 1; r <= rings; r++) {
            double rad = radius * (r / (double) rings);
            int points = Math.max(18, (int) (rad * 5.5));
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points;
                double x = center.getX() + Math.cos(angle) * rad;
                double z = center.getZ() + Math.sin(angle) * rad;
                double y = center.getY() + 0.12;
                world.spawnParticle(ring, x, y, z, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, x, y + 0.2, z, 1, 0, 0, 0, 0);
                if (r == rings) {
                    world.spawnParticle(Particle.CLOUD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private boolean isVulnerablePlayer(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    public boolean isCombatTarget(Player player) {
        return isVulnerablePlayer(player);
    }

    public void launchInk(
            Vector direction,
            double speed,
            double damage,
            int blindTicks,
            int slowTicks,
            int poisonTicks
    ) {
        if (entity == null || !entity.isValid() || direction == null || direction.lengthSquared() < 0.0001) {
            return;
        }
        Vector velocity = direction.clone().normalize().multiply(Math.max(0.4, speed));
        Snowball blob = entity.launchProjectile(Snowball.class, velocity);
        blob.setShooter(entity);
        blob.setGravity(true);
        blob.setItem(new ItemStack(Material.INK_SAC));
        blob.getPersistentDataContainer().set(keys.inkDamageKey(), PersistentDataType.DOUBLE, Math.max(1.0, scaleDamage(damage)));
        blob.getPersistentDataContainer().set(keys.inkBlindKey(), PersistentDataType.INTEGER, Math.max(0, blindTicks));
        blob.getPersistentDataContainer().set(keys.inkSlowKey(), PersistentDataType.INTEGER, Math.max(0, slowTicks));
        blob.getPersistentDataContainer().set(keys.inkPoisonKey(), PersistentDataType.INTEGER, Math.max(0, poisonTicks));

        Location from = entity.getEyeLocation();
        World world = from.getWorld();
        if (world != null) {
            world.playSound(from, Sound.ENTITY_SQUID_SQUIRT, 0.85f, 0.85f);
            world.spawnParticle(Particle.SQUID_INK, from, 5, 0.12, 0.12, 0.12, 0.02);
        }
    }

    public void spawnBoilingClusters(int amount, double radius, double spread, double percentPerSecond) {
        boilingZones.clear();
        Location origin = spawnLocation.clone();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(origin, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.4f, 0.55f);
        world.playSound(origin, Sound.ENTITY_GENERIC_SPLASH, 1.1f, 0.6f);
        for (int i = 0; i < amount; i++) {
            Location spot = findBoilingSpot(origin, spread, i, amount);
            boilingZones.add(new BoilingZone(spot, radius, percentPerSecond));
            world.spawnParticle(Particle.SPLASH, spot, 18, 0.6, 0.2, 0.6, 0.05);
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, spot, 12, 0.4, 0.4, 0.4, 0.02);
        }
    }

    public void clearBoilingZones() {
        boilingZones.clear();
    }

    private void tickInkSpin(PhaseTransition transition, double progress) {
        if (entity == null || spawnLocation == null) {
            return;
        }
        Location hold = blendTowardSpawn(progress, 0.35);
        if (hold == null) {
            hold = spawnLocation.clone();
        }
        hold.setYaw((entity.getLocation().getYaw() + 11f) % 360f);
        hold.setPitch(12f);
        Location inkHold = hold;
        runInternalTeleport(() -> entity.teleport(inkHold));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);

        World world = hold.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SQUID_INK, hold.clone().add(0, entity.getHeight() * 0.45, 0), 8, 0.7, 0.45, 0.7, 0.03);
            world.spawnParticle(Particle.BUBBLE, hold, 6, 0.8, 0.4, 0.8, 0.02);
        }

        int interval = Math.max(2, transition.getLightningCount() > 0 ? transition.getLightningCount() : 4);
        if (transitionTick % interval != 0) {
            return;
        }
        int bursts = Math.max(3, transition.getPoints() / 8);
        double damage = transition.getUnderDamage() > 0 ? transition.getUnderDamage() : 14.0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Player nearest = nearestVulnerablePlayer(32);
        for (int i = 0; i < bursts; i++) {
            Vector direction;
            if (i == 0 && nearest != null) {
                direction = nearest.getEyeLocation().toVector().subtract(entity.getEyeLocation().toVector());
            } else {
                direction = new Vector(
                        random.nextDouble(-1.0, 1.0),
                        random.nextDouble(-0.5, 0.12),
                        random.nextDouble(-1.0, 1.0)
                );
            }
            if (direction.lengthSquared() < 0.01) {
                continue;
            }
            launchInk(direction, 1.12, damage, 45, 55, 50);
        }
    }

    private void tickWaterChase() {
        if (!(entity instanceof Squid) || entity instanceof Mob mob && !mob.hasAI()) {
            return;
        }
        Player target = nearestVulnerablePlayer(28);
        if (target == null) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(target);
        }
        Vector to = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        if (to.lengthSquared() < 0.04) {
            return;
        }
        to.normalize().multiply(0.16);
        Location probe = entity.getLocation().clone().add(0, to.getY(), 0);
        if (!isWaterBlock(probe) && !isWaterBlock(probe.clone().add(0, -0.4, 0))) {
            to.setY(0);
            if (to.lengthSquared() < 0.0001) {
                return;
            }
            to.normalize().multiply(0.16);
        } else {
            to.setY(to.getY() * 0.35);
        }
        entity.setVelocity(entity.getVelocity().multiply(0.4).add(to));
        Location look = entity.getLocation();
        look.setDirection(to);
        entity.setRotation(look.getYaw(), look.getPitch());
    }

    private void keepInWater() {
        if (!(entity instanceof Squid) || spawnLocation == null) {
            return;
        }
        Location loc = entity.getLocation();
        if (isWaterBlock(loc) || isWaterBlock(loc.clone().add(0, -0.5, 0))) {
            if (!isWaterBlock(loc.clone().add(0, 0.7, 0)) && entity.getVelocity().getY() > 0.04) {
                entity.setVelocity(entity.getVelocity().setY(Math.min(0.02, entity.getVelocity().getY())));
            }
            return;
        }
        Location water = findWaterNear(loc);
        if (water == null) {
            water = findWaterNear(spawnLocation);
        }
        if (water == null) {
            water = spawnLocation.clone();
        }
        entity.teleport(water);
        entity.setVelocity(new Vector(0, -0.05, 0));
        entity.setFallDistance(0);
    }

    private Location findWaterNear(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = 3; dy >= -4; dy--) {
                        Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                        if (isWaterMaterial(block.getType()) && isWaterMaterial(block.getRelative(0, 1, 0).getType())) {
                            return block.getLocation().add(0.5, 0.4, 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isWaterBlock(Location location) {
        return location != null && location.getWorld() != null && isWaterMaterial(location.getBlock().getType());
    }

    private boolean isWaterMaterial(Material type) {
        return type == Material.WATER
                || type == Material.BUBBLE_COLUMN
                || type == Material.KELP
                || type == Material.KELP_PLANT
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS;
    }

    private void tickDragon() {
        dragonDirector.tick();
    }

    public static boolean stormBoltActive() {
        return STORM_BOLT_SOURCE.get() != null;
    }

    public static BossInstance stormBoltSource() {
        return STORM_BOLT_SOURCE.get();
    }

    public static Double stormBoltDamage() {
        return STORM_BOLT_DAMAGE.get();
    }

    public void strikeStormBolt(Location loc, double damage) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World world = loc.getWorld();
        Location strike = loc.clone();
        double power = Math.max(2.0, scaleDamage(damage));
        STORM_BOLT_SOURCE.set(this);
        STORM_BOLT_DAMAGE.set(power);
        LightningStrike bolt;
        try {
            bolt = world.strikeLightningEffect(strike);
            bolt.setSilent(true);
            keys.tagStormBolt(bolt);
        } finally {
            STORM_BOLT_SOURCE.remove();
            STORM_BOLT_DAMAGE.remove();
        }
        world.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.55f, 1.25f);
        world.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 0.45f, 1.05f);
        double hitSq = 3.5 * 3.5;
        for (Player player : world.getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(strike) > hitSq) {
                continue;
            }
            BossHits.hurt(player, entity, power);
        }
    }

    public void startLightningStorm(double radius, double damage) {
        this.lightningStormActive = true;
        this.lightningStormRadius = Math.max(8.0, radius);
        this.lightningStormDamage = Math.max(2.0, damage);
        if (hazardFocus == null) {
            hazardFocus(true);
        }
    }

    public void startBlackHole(double radius, double killRadius) {
        this.blackHoleActive = true;
        this.blackHoleRadius = Math.max(8.0, radius);
        this.blackHoleKillRadius = Math.max(1.4, killRadius);
        if (hazardFocus == null) {
            hazardFocus(true);
        }
    }

    public void healCombat(double amount) {
        if (!isAlive() || amount <= 0) {
            return;
        }
        combatHealth = Math.min(combatMaxHealth, combatHealth + amount);
        syncVanillaHealth();
    }

    private void tickLightningStorm() {
        Location origin = hazardFocus();
        if (!lightningStormActive || origin == null || origin.getWorld() == null) {
            return;
        }
        if (ticksAlive % 14 != 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int bolts = 2;
        for (int i = 0; i < bolts; i++) {
            double dist = random.nextDouble() * lightningStormRadius;
            double yaw = random.nextDouble() * Math.PI * 2;
            Location bolt = origin.clone().add(Math.cos(yaw) * dist, 0.2, Math.sin(yaw) * dist);
            strikeStormBolt(bolt, lightningStormDamage);
        }
    }

    private void tickBlackHole() {
        Location origin = hazardFocus();
        if (!blackHoleActive || origin == null || origin.getWorld() == null) {
            return;
        }
        World world = origin.getWorld();
        Location core = origin.clone().add(0, 1.15, 0);
        if (ticksAlive % 2 == 0) {
            world.spawnParticle(Particle.SQUID_INK, core, 6, 0.45, 0.45, 0.45, 0.01);
            world.spawnParticle(Particle.PORTAL, core, 10, 0.7, 0.7, 0.7, 0.12);
            world.spawnParticle(Particle.REVERSE_PORTAL, core, 4, 0.25, 0.25, 0.25, 0.03);
        }
        if (ticksAlive % 16 == 0) {
            world.playSound(core, Sound.BLOCK_PORTAL_AMBIENT, 0.28f, 0.4f);
        }
        double radiusSq = blackHoleRadius * blackHoleRadius;
        double killSq = blackHoleKillRadius * blackHoleKillRadius;
        for (Player player : world.getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            double distSq = player.getLocation().distanceSquared(core);
            if (distSq > radiusSq) {
                continue;
            }
            if (distSq <= killSq) {
                double stolen = Math.max(1.0, player.getHealth());
                player.getPersistentDataContainer().set(AetherKeys.NO_SET_SAVE, PersistentDataType.BYTE, (byte) 1);
                player.getPersistentDataContainer().set(AetherKeys.TRUE_DAMAGE, PersistentDataType.BYTE, (byte) 1);
                try {
                    player.setHealth(0);
                } finally {
                    player.getPersistentDataContainer().remove(AetherKeys.NO_SET_SAVE);
                    player.getPersistentDataContainer().remove(AetherKeys.TRUE_DAMAGE);
                }
                healCombat(stolen);
                world.playSound(core, Sound.ENTITY_ENDERMAN_DEATH, 0.7f, 0.5f);
                world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
                continue;
            }
            Vector pull = core.toVector().subtract(player.getLocation().toVector());
            double dist = Math.sqrt(distSq);
            pull.normalize().multiply(0.012 + (1.0 - dist / blackHoleRadius) * 0.042);
            player.setVelocity(player.getVelocity().multiply(0.93).add(pull));
        }
    }

    private void tickBoilingZones() {
        if (boilingZones.isEmpty()) {
            return;
        }
        for (BoilingZone zone : boilingZones) {
            zone.tick(ticksAlive, this::isVulnerablePlayer, entity);
        }
    }

    private Location findBoilingSpot(Location origin, double spread, int index, int total) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = (Math.PI * 2 * index) / Math.max(1, total) + random.nextDouble(-0.35, 0.35);
        double dist = spread * (0.45 + random.nextDouble() * 0.55);
        Location candidate = origin.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        World world = candidate.getWorld();
        if (world == null) {
            return origin.clone();
        }
        for (int dy = 3; dy >= -4; dy--) {
            Block block = world.getBlockAt(candidate.getBlockX(), candidate.getBlockY() + dy, candidate.getBlockZ());
            if (block.isLiquid()) {
                return block.getLocation().add(0.5, 0.35, 0.5);
            }
        }
        return candidate.add(0.5, 0.2, 0.5);
    }

    private Player nearestVulnerablePlayer(double range) {
        if (entity == null) {
            return null;
        }
        Player nearest = null;
        double best = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static final class BoilingZone {
        private final Location center;
        private final double radius;
        private final double percentPerSecond;

        private BoilingZone(Location center, double radius, double percentPerSecond) {
            this.center = center.clone();
            this.radius = radius;
            this.percentPerSecond = percentPerSecond;
        }

        private void tick(long ticksAlive, java.util.function.Predicate<Player> vulnerable, LivingEntity source) {
            World world = center.getWorld();
            if (world == null) {
                return;
            }
            double bob = 0.18 + Math.sin((ticksAlive + center.getBlockX()) * 0.35) * 0.12;
            Location vis = center.clone().add(0, bob, 0);
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, vis, 6, radius * 0.35, 0.45, radius * 0.35, 0.02);
            world.spawnParticle(Particle.BUBBLE, vis, 5, radius * 0.4, 0.25, radius * 0.4, 0.01);
            world.spawnParticle(Particle.SPLASH, vis, 3, radius * 0.45, 0.1, radius * 0.45, 0.02);
            if (ticksAlive % 8 == 0) {
                world.spawnParticle(Particle.CLOUD, vis, 2, radius * 0.3, 0.15, radius * 0.3, 0.01);
            }
            if (ticksAlive % 25 == 0) {
                world.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.55f, 0.85f);
            }
            if (ticksAlive % 10 != 0) {
                return;
            }
            double radiusSq = radius * radius;
            for (Player player : world.getPlayers()) {
                if (!vulnerable.test(player)) {
                    continue;
                }
                if (player.getLocation().distanceSquared(center) > radiusSq) {
                    continue;
                }
                double max = Math.max(1.0, player.getMaxHealth());
                double amount = Math.max(1.0, max * (percentPerSecond / 100.0) * 0.5);
                player.getPersistentDataContainer().set(AetherKeys.TRUE_DAMAGE, PersistentDataType.BYTE, (byte) 1);
                try {
                    player.damage(amount, source);
                } finally {
                    player.getPersistentDataContainer().remove(AetherKeys.TRUE_DAMAGE);
                }
                world.spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    public void forcePhase(BossPhase phase) {
        this.currentPhase = phase;
        if (phase.getDisplayName() != null && !phase.getDisplayName().isBlank()) {
            applyVisuals(phase.getDisplayName());
        }
        if (phase.getAttributes() != null) {
            applyAttributes(template.getAttributes().overlay(phase.getAttributes()), false);
        }
    }

    public double healthPercent() {
        if (combatMaxHealth <= 0) {
            return 0;
        }
        return (combatHealth / combatMaxHealth) * 100.0;
    }

    public boolean canCast(AbstractBossSkill skill) {
        Long last = lastCastTick.get(skill);
        if (last == null) {
            return true;
        }
        int wait = Math.max(skill.getCooldownTicks(), skill.getIntervalTicks());
        return ticksAlive - last >= wait;
    }

    public void markCast(AbstractBossSkill skill) {
        lastCastTick.put(skill, ticksAlive);
    }

    public String replacePlaceholders(String message) {
        if (message == null) {
            return "";
        }
        String health = String.valueOf((int) Math.ceil(combatHealth));
        String max = String.valueOf((int) Math.ceil(combatMaxHealth));
        String phaseName = currentPhase == null ? "-" : TextUtil.plain(currentPhase.getDisplayName());
        return message
                .replace("{boss}", TextUtil.plain(template.getDisplayName()))
                .replace("{health}", health)
                .replace("{max-health}", max)
                .replace("{phase}", phaseName)
                .replace("{percent}", String.valueOf((int) Math.round(healthPercent())));
    }

    public void applyAttributes(BossAttributes attributes) {
        applyAttributes(attributes, false);
    }

    public void applyAttributes(BossAttributes attributes, boolean resetCombatHealth) {
        if (entity == null || attributes == null) {
            return;
        }
        if (attributes.getMaxHealth() > 0) {
            combatMaxHealth = attributes.getMaxHealth() * raidHealthMul;
            if (resetCombatHealth || combatHealth <= 0) {
                combatHealth = combatMaxHealth;
            } else if (combatHealth > combatMaxHealth) {
                combatHealth = combatMaxHealth;
            }
        }
        AttributeUtil.setBase(entity, AttributeUtil.maxHealth(), combatMaxHealth);
        AttributeUtil.setBase(entity, AttributeUtil.movementSpeed(), attributes.getMovementSpeed());
        AttributeUtil.setBase(entity, AttributeUtil.attackDamage(), attributes.getAttackDamage() * raidDamageMul);
        AttributeUtil.setBase(entity, AttributeUtil.followRange(), attributes.getFollowRange());
        AttributeUtil.setBase(entity, AttributeUtil.knockbackResistance(), attributes.getKnockbackResistance());
        applyScale(attributes.getScale());
        syncVanillaHealth();
    }

    private BossAttributes activeAttributes() {
        if (currentPhase != null && currentPhase.getAttributes() != null) {
            return template.getAttributes().overlay(currentPhase.getAttributes());
        }
        return template.getAttributes();
    }

    private void applyVisuals(String displayName) {
        if (entity == null) {
            return;
        }
        entity.customName(TextUtil.component(displayName));
        entity.setCustomNameVisible(template.getOptions().isCustomNameVisible());
    }

    private void applyEquipment(BossEquipment equipment) {
        if (entity == null || equipment == null) {
            return;
        }
        EntityEquipment slots = entity.getEquipment();
        if (slots == null) {
            return;
        }
        setSlot(slots::setHelmet, slots::setHelmetDropChance, equipment.getHelmet(), equipment.getDropChance(), equipment.getLeatherColor());
        setSlot(slots::setChestplate, slots::setChestplateDropChance, equipment.getChestplate(), equipment.getDropChance(), equipment.getLeatherColor());
        setSlot(slots::setLeggings, slots::setLeggingsDropChance, equipment.getLeggings(), equipment.getDropChance(), equipment.getLeatherColor());
        setSlot(slots::setBoots, slots::setBootsDropChance, equipment.getBoots(), equipment.getDropChance(), equipment.getLeatherColor());
        setSlot(slots::setItemInMainHand, slots::setItemInMainHandDropChance, equipment.getMainHand(), equipment.getDropChance(), null);
        setSlot(slots::setItemInOffHand, slots::setItemInOffHandDropChance, equipment.getOffHand(), equipment.getDropChance(), null);
    }

    private void polishEntityState() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (entity instanceof org.bukkit.entity.Wither wither && wither.getInvulnerabilityTicks() > 0) {
            wither.setInvulnerabilityTicks(0);
        }
        if (frostboundDirector.reflecting() && entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            return;
        }
        boolean locked = isTransitioning()
                || de.aetherion.bossengine.skill.t2.T2Mechanics.isBurrowing(this)
                || frostboundDirector.isDying()
                || pathwardenDirector.isDying()
                || sparkyDirector.isDying()
                || signatureDirector.isDying()
                || dragonDirector.isDying();
        if (!locked && phaseArmorTicks <= 0) {
            if (entity.isInvulnerable()) {
                entity.setInvulnerable(false);
            }
            if (entity.isInvisible()) {
                entity.setInvisible(false);
            }
            if (!entity.isCollidable()) {
                entity.setCollidable(true);
            }
            entity.setGlowing(template.getOptions().isGlowing());
            if (entity instanceof Mob mob) {
                mob.setAI(true);
                mob.setAware(true);
                if (ticksAlive % 20 == 0) {
                    retargetNearest(mob);
                }
                if (template != null && "lobby_cleaner".equalsIgnoreCase(template.getId())) {
                    nudgeLobbyCleaner(mob);
                }
            }
        }
    }

    private void nudgeLobbyCleaner(Mob mob) {
        if (mob == null || !mob.isValid() || isTransitioning() || isSlamPending()) {
            return;
        }
        // Scaled Endermen pathfind poorly — keep him drifting toward the fight.
        Player target = mob.getTarget() instanceof Player player && isVulnerablePlayer(player)
                ? player
                : null;
        if (target == null) {
            retargetNearest(mob);
            target = mob.getTarget() instanceof Player player && isVulnerablePlayer(player) ? player : null;
        }
        if (target == null) {
            return;
        }
        double distSq = target.getLocation().distanceSquared(mob.getLocation());
        if (distSq < 3.5 * 3.5 || distSq > 42 * 42) {
            return;
        }
        if (ticksAlive % 6 != 0) {
            return;
        }
        Vector to = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        to.setY(0);
        if (to.lengthSquared() < 0.01) {
            return;
        }
        Vector step = to.normalize().multiply(0.42);
        step.setY(Math.max(0.02, Math.min(0.18, mob.getVelocity().getY())));
        mob.setVelocity(step);
        mob.setFallDistance(0);
    }

    private void retargetNearest(Mob mob) {
        if (mob == null || !mob.isValid()) {
            return;
        }
        if (mob.getTarget() instanceof Player current && isVulnerablePlayer(current)
                && current.getWorld().equals(mob.getWorld())
                && current.getLocation().distanceSquared(mob.getLocation()) <= 48 * 48) {
            return;
        }
        Player best = null;
        double bestDist = 48 * 48;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!isVulnerablePlayer(player)) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(mob.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        if (best != null) {
            mob.setTarget(best);
        }
    }

    private void applyOptions(BossOptions options) {
        if (entity == null) {
            return;
        }
        entity.setGlowing(options.isGlowing());
        entity.setSilent(options.isSilent());
        entity.setPersistent(options.isPersistent());
        entity.setRemoveWhenFarAway(options.isRemoveWhenFarAway());
        entity.setCanPickupItems(!options.isPreventItemPickup());
        if (entity instanceof Mob mob) {
            mob.setAware(true);
        }
        if (options.getInvulnerableSpawnTicks() > 0) {
            entity.setInvulnerable(true);
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        if (entity != null && entity.isValid() && !isTransitioning()) {
                            entity.setInvulnerable(false);
                        }
                    },
                    options.getInvulnerableSpawnTicks()
            );
        }
    }

    private void tickTimerSkills() {
        // SkillManager drives execution; this only tracks lifetime.
    }

    private void enforceLeash() {
        if (entity instanceof org.bukkit.entity.EnderDragon) {
            return;
        }
        if (isHollowLurker()) {
            // Cave boss: zero leash, zero pulls, zero ports.
            return;
        }
        if (isTransitioning()) {
            return;
        }
        double radius = conditions.getLeashRadius();
        if (radius <= 0 || entity == null) {
            return;
        }
        if (!entity.getWorld().equals(spawnLocation.getWorld())) {
            handleLeashBreak();
            return;
        }
        double distSq = horizontalDistanceSquared(entity.getLocation(), spawnLocation);
        double outer = radius * radius;
        double inner = radius * 0.88;
        if (leashPulling) {
            if (distSq <= inner * inner) {
                leashPulling = false;
                return;
            }
            softPullToArena(radius * 0.92);
            return;
        }
        if (distSq > outer) {
            leashPulling = true;
            handleLeashBreak();
        }
    }

    /**
     * These bosses must never blink home (cave walls / bridge falls used to
     * call {@link #snapToArena()} and look identical to a leash warp).
     */
    private boolean refusesHardArenaSnap() {
        if (template == null) {
            return false;
        }
        String id = template.getId();
        return "hollow_lurker".equalsIgnoreCase(id) || "bridge_troll".equalsIgnoreCase(id);
    }

    private boolean isHollowLurker() {
        return template != null && "hollow_lurker".equalsIgnoreCase(template.getId());
    }

    private void handleLeashBreak() {
        if (isHollowLurker()) {
            return;
        }
        if (conditions.getLeashAction() == LeashAction.TELEPORT) {
            // Velocity pull only — hard ring teleports felt like warps.
            softPullToArena(conditions.getLeashRadius() * 0.92);
            return;
        }
        state = BossState.DESPAWNING;
    }

    private void rememberBodyLocation() {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        lastSeenLocation = entity.getLocation().clone();
    }

    public Location getLastSeenLocation() {
        if (lastSeenLocation != null) {
            return lastSeenLocation.clone();
        }
        if (entity != null && entity.isValid()) {
            return entity.getLocation().clone();
        }
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void markBodyUnloaded() {
        bodyUnloaded = true;
        if (entity != null) {
            lastSeenLocation = entity.getLocation().clone();
        }
        suppressBodyRestore(2_500L);
    }

    public boolean isBodyUnloaded() {
        return bodyUnloaded;
    }

    public void clearBodyUnloaded() {
        bodyUnloaded = false;
        missingBodyTicks = 0;
    }

    public void suppressBodyRestore(long millis) {
        suppressBodyRestoreUntilMs = Math.max(suppressBodyRestoreUntilMs, System.currentTimeMillis() + Math.max(0L, millis));
    }

    public boolean isBodyRestoreSuppressed() {
        return System.currentTimeMillis() < suppressBodyRestoreUntilMs;
    }

    public int noteMissingBodyTick() {
        return ++missingBodyTicks;
    }

    public void clearMissingBodyTicks() {
        missingBodyTicks = 0;
    }

    /**
     * Escape suffocation/void without resetting the fight to spawn.
     * Hollow Lurker: never teleport — only a tiny velocity shove.
     */
    public void unstickFromHazard() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.setFallDistance(0);
        if (isHollowLurker()) {
            Vector bump = entity.getVelocity().clone();
            bump.setY(Math.max(0.42, bump.getY()));
            // Slight random horizontal so he does not stay forever in the same wall cell.
            bump.setX(bump.getX() + (Math.random() - 0.5) * 0.2);
            bump.setZ(bump.getZ() + (Math.random() - 0.5) * 0.2);
            entity.setVelocity(bump);
            return;
        }
        entity.setVelocity(new Vector(0, 0.35, 0));
        Location escape = findOpenNear(entity.getLocation(), 5);
        if (escape != null) {
            Location dest = escape;
            runInternalTeleport(() -> entity.teleport(dest));
            return;
        }
        if (refusesHardArenaSnap()) {
            softPullToArena(Math.max(4.0, Math.max(1.0, conditions.getLeashRadius()) * 0.5));
            return;
        }
        // Direct home snap — do not recurse through snapToArena().
        Location dest = standLocation(spawnLocation.clone());
        dest.setYaw(entity.getLocation().getYaw());
        dest.setPitch(0);
        Location home = dest;
        runInternalTeleport(() -> entity.teleport(home));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
    }

    /**
     * No blink — shove the boss back toward the ring with velocity.
     */
    private void softPullToArena(double edgeRadius) {
        if (entity == null || !entity.isValid() || spawnLocation.getWorld() == null) {
            return;
        }
        Location here = entity.getLocation();
        Vector offset = here.toVector().subtract(spawnLocation.toVector());
        offset.setY(0);
        double dist = offset.length();
        if (dist < 0.05) {
            return;
        }
        // Soft leash never snaps — even if far. Walk him back.
        double strength = dist > edgeRadius * 1.35 ? 0.55 : 0.38;
        Vector pull = offset.multiply(-1.0).normalize().multiply(strength);
        pull.setY(Math.max(-0.05, Math.min(0.15, entity.getVelocity().getY())));
        entity.setVelocity(pull);
        entity.setFallDistance(0);
    }

    private void capturePhaseReturn() {
        if (entity == null || !entity.isValid() || entity instanceof org.bukkit.entity.EnderDragon) {
            phaseReturnFrom = null;
            return;
        }
        phaseReturnFrom = entity.getLocation().clone();
    }

    private void tickPhaseReturn(double progress) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        // Dragons settle toward spawn for the spectacle. Everyone else holds
        // the fight position — yanking to center looked like a hard teleport.
        if (entity instanceof org.bukkit.entity.EnderDragon) {
            Location dest = blendTowardSpawn(progress, 1.0);
            if (dest == null) {
                return;
            }
            dest.setYaw(entity.getLocation().getYaw());
            dest.setPitch(0);
            Location move = dest;
            runInternalTeleport(() -> entity.teleport(move));
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setFallDistance(0);
            return;
        }
        if (phaseReturnFrom == null) {
            return;
        }
        // Cave/bridge bosses: freeze in place — never blink-hold.
        if (refusesHardArenaSnap()) {
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setFallDistance(0);
            return;
        }
        Location hold = phaseReturnFrom.clone();
        hold.setYaw(entity.getLocation().getYaw());
        hold.setPitch(0);
        runInternalTeleport(() -> entity.teleport(hold));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
    }

    private Location blendTowardSpawn(double progress, double settleBy) {
        if (entity == null || spawnLocation.getWorld() == null) {
            return null;
        }
        Location target = standLocation(spawnLocation.clone());
        if (phaseReturnFrom == null || settleBy <= 0) {
            return target.clone();
        }
        double t = Math.min(1.0, Math.max(0.0, progress / settleBy));
        t = t * t * (3.0 - 2.0 * t);
        Location from = phaseReturnFrom;
        Location dest = from.clone();
        dest.setX(from.getX() + (target.getX() - from.getX()) * t);
        dest.setY(from.getY() + (target.getY() - from.getY()) * t);
        dest.setZ(from.getZ() + (target.getZ() - from.getZ()) * t);
        dest.setWorld(target.getWorld());
        return dest;
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public void snapToArena() {
        if (entity == null || !entity.isValid() || spawnLocation.getWorld() == null) {
            return;
        }
        if (isHollowLurker()) {
            // Absolute no-port for the cave boss.
            entity.setFallDistance(0);
            return;
        }
        if (refusesHardArenaSnap()) {
            unstickFromHazard();
            return;
        }
        Location dest = standLocation(spawnLocation.clone());
        dest.setYaw(entity.getLocation().getYaw());
        dest.setPitch(0);
        runInternalTeleport(() -> entity.teleport(dest));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
        if (entity instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(null);
        }
    }

    private Location standLocation(Location origin) {
        Location dest = origin.clone();
        if (columnFits(dest)) {
            return dest;
        }
        for (int i = 1; i <= 8; i++) {
            Location up = dest.clone().add(0, i, 0);
            if (columnFits(up)) {
                return up;
            }
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location candidate = dest.clone().add(dx, 0, dz);
                    if (columnFits(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return dest;
    }

    private boolean columnFits(Location feet) {
        if (feet == null || feet.getWorld() == null || entity == null) {
            return false;
        }
        World world = feet.getWorld();
        if (feet.getBlock().getType().isSolid()) {
            return false;
        }
        double height = Math.max(1.8, entity.getHeight());
        int top = (int) Math.floor(feet.getY() + height - 0.05);
        int x = feet.getBlockX();
        int z = feet.getBlockZ();
        for (int y = feet.getBlockY(); y <= top; y++) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void applySlimeSize() {
        if (entity instanceof org.bukkit.entity.MagmaCube cube) {
            // Size drives the real collision box; keep Sparky wide enough to hit from mid-range.
            int size = "sparky".equalsIgnoreCase(template.getId())
                    ? Math.max(7, (int) Math.round(template.getAttributes().getScale() * 2.4))
                    : Math.max(6, (int) Math.round(template.getAttributes().getScale() * 2.8));
            cube.setSize(size);
        } else if (entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(Math.max(5, (int) Math.round(template.getAttributes().getScale() * 2.4)));
        }
    }

    private void applyScale(double wanted) {
        if (entity == null || wanted <= 0) {
            return;
        }
        AttributeUtil.setBase(entity, AttributeUtil.scale(), wanted);
        if (columnFits(entity.getLocation())) {
            return;
        }
        AttributeUtil.setBase(entity, AttributeUtil.scale(), 1.0);
        if (!columnFits(entity.getLocation())) {
            return;
        }
        double best = 1.0;
        double low = 1.0;
        double high = wanted;
        for (int i = 0; i < 8; i++) {
            double mid = (low + high) / 2.0;
            AttributeUtil.setBase(entity, AttributeUtil.scale(), mid);
            if (columnFits(entity.getLocation())) {
                best = mid;
                low = mid;
            } else {
                high = mid;
            }
        }
        AttributeUtil.setBase(entity, AttributeUtil.scale(), best);
    }

    private void unstickIfNeeded() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (isHollowLurker()) {
            // Never teleport out of walls — velocity bump only when packed in solid.
            Block feet = entity.getLocation().getBlock();
            Block head = entity.getEyeLocation().getBlock();
            if (feet.getType().isSolid() || head.getType().isSolid()) {
                unstickFromHazard();
            }
            return;
        }
        Block feet = entity.getLocation().getBlock();
        Block head = entity.getEyeLocation().getBlock();
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            unstickFromHazard();
        }
    }

    private Location findOpenNear(Location from, int radius) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        World world = from.getWorld();
        int r = Math.max(1, radius);
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = 0; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location probe = from.clone().add(dx, dy, dz);
                    Block ground = probe.getBlock();
                    Block above = ground.getRelative(0, 1, 0);
                    Block below = ground.getRelative(0, -1, 0);
                    if (ground.getType().isSolid() || above.getType().isSolid()) {
                        continue;
                    }
                    if (!below.getType().isSolid() && !below.isLiquid()) {
                        continue;
                    }
                    double dist = probe.distanceSquared(from);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = standLocation(probe);
                    }
                }
            }
        }
        return best;
    }

    public void killFromHearth() {
        if (frostboundDirector.isDying()) {
            return;
        }
        combatHealth = 1;
        if (frostboundDirector.beginDeath()) {
            syncVanillaHealth();
            return;
        }
        combatHealth = 0;
        syncVanillaHealth();
    }

    public boolean clickFrostHearth(Player player, org.bukkit.entity.Entity clicked) {
        return frostboundDirector.click(player, clicked);
    }

    public boolean frostReflects() {
        return frostboundDirector.reflecting();
    }

    public void reflectFrost(Player player, double incoming) {
        frostboundDirector.reflect(player, incoming);
    }

    public void hearthArrived(org.bukkit.entity.Entity projectile) {
        frostboundDirector.hearthArrived(projectile);
    }

    private interface ItemSetter {
        void set(ItemStack item);
    }

    private interface ChanceSetter {
        void set(float chance);
    }

    private void setSlot(ItemSetter itemSetter, ChanceSetter chanceSetter, Material material, float dropChance, Color leatherColor) {
        if (material == null || material.isAir()) {
            itemSetter.set(null);
            chanceSetter.set(0);
            return;
        }
        ItemStack stack = new ItemStack(material);
        if (leatherColor != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(leatherColor);
            stack.setItemMeta(meta);
        }
        itemSetter.set(stack);
        chanceSetter.set(dropChance);
    }
}
