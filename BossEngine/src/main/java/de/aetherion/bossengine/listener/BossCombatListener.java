package de.aetherion.bossengine.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.event.BossDamageByPlayerEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.manager.BossManager;
import de.aetherion.bossengine.skill.SkillTrigger;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BossCombatListener implements Listener {

    private final JavaPlugin plugin;
    private final BossManager bossManager;

    public BossCombatListener(JavaPlugin plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMeteorBlast(EntityDamageEvent event) {
        if (!de.aetherion.bossengine.skill.t2.T2Mechanics.meteorBoomActive()) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        Entity damaged = event.getEntity();
        if (plugin instanceof BossEngine engine && engine.getKeys().isBeamFx(damaged)) {
            event.setCancelled(true);
            return;
        }
        if (damaged instanceof org.bukkit.entity.EnderDragonPart part) {
            damaged = part.getParent();
        }
        if (!(damaged instanceof LivingEntity)) {
            return;
        }

        BossInstance instance = resolve(damaged);
        if (instance == null) {
            return;
        }

        if (event.isCancelled() && !shouldUncancelBossHit(instance, event)) {
            return;
        }
        if (event.isCancelled()) {
            event.setCancelled(false);
        }

        if (isWorldHazard(event) && !isPlayerCombatHit(event)) {
            event.setCancelled(true);
            instance.getEntity().setFallDistance(0);
            instance.getEntity().setFireTicks(0);
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                    || event.getCause() == EntityDamageEvent.DamageCause.CRAMMING
                    || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                // Local nudge only — never blink (cave Lurker used to warp from this).
                instance.unstickFromHazard();
            }
            return;
        }

        if (event instanceof EntityDamageByEntityEvent byEntity && plugin instanceof BossEngine engine) {
            Entity source = byEntity.getDamager();
            if (engine.getKeys().isHearthBolt(source) || engine.getKeys().isIceShard(source)) {
                event.setCancelled(true);
                return;
            }
        }

        Player striker = event instanceof EntityDamageByEntityEvent byEntity
                ? damager(byEntity.getDamager())
                : null;

        // Bare-hand / early spam clicks shouldn't chip boss HP.
        // Weapon specials call LivingEntity.damage(player) with low attack cooldown — allow those.
        if (striker != null
                && event instanceof EntityDamageByEntityEvent byEntity
                && !(byEntity.getDamager() instanceof Projectile)
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            ItemStack hand = striker.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                event.setCancelled(true);
                return;
            }
            if (!isAbilityOrScriptedMelee(event) && striker.getAttackCooldown() < 0.85f) {
                event.setCancelled(true);
                return;
            }
        }

        if (instance.frostReflects()) {
            event.setCancelled(true);
            if (striker != null) {
                instance.reflectFrost(striker, Math.max(event.getDamage(), event.getFinalDamage()));
            } else if (instance.getEntity() != null && instance.getEntity().getWorld() != null) {
                instance.getEntity().getWorld().playSound(
                        instance.getEntity().getLocation(),
                        Sound.BLOCK_GLASS_BREAK,
                        0.7f,
                        1.6f
                );
            }
            return;
        }

        if (instance.isDamageBlocked()) {
            event.setCancelled(true);
            return;
        }

        double incoming = Math.max(event.getDamage(), event.getFinalDamage());
        Player player = event instanceof EntityDamageByEntityEvent byEntity
                ? damager(byEntity.getDamager())
                : null;

        if (player != null && instance.sandbox() != null && instance.sandbox().active()) {
            double modified = instance.sandbox().modifyIncoming(player, incoming);
            if (modified < 0) {
                event.setCancelled(true);
                return;
            }
            incoming = modified;
        }

        if (player != null && incoming > 0) {
            instance.getDamageTracker().add(player, incoming);
            BossDamageByPlayerEvent custom = new BossDamageByPlayerEvent(
                    instance,
                    player,
                    incoming,
                    instance.getDamageTracker().getTotalDamage(),
                    instance.getDamageTracker().getShare(player.getUniqueId()) * 100.0
            );
            Bukkit.getPluginManager().callEvent(custom);
            if (custom.isCancelled()) {
                event.setCancelled(true);
                return;
            }
            bossManager.getSkillManager().execute(instance, SkillTrigger.ON_DAMAGE, player, incoming);
        }

        boolean lethal = instance.absorbDamage(incoming);
        bossManager.refreshHud(instance);

        if (lethal) {
            event.setDamage(Math.max(incoming, instance.getEntity().getHealth() + 1));
            return;
        }

        event.setDamage(0.01);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStormLightningSpawn(LightningStrikeEvent event) {
        if (BossInstance.stormBoltActive() && plugin instanceof BossEngine engine) {
            engine.getKeys().tagStormBolt(event.getLightning());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (resolve(event.getEntity()) != null || (plugin instanceof BossEngine engine && engine.getKeys().isBoss(event.getEntity()))) {
            event.setCancelled(true);
            if (event.getEntity() instanceof LivingEntity living) {
                living.setFireTicks(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStormLightningHit(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) {
            return;
        }
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        boolean ours = BossInstance.stormBoltActive()
                || (plugin instanceof BossEngine engine && engine.getKeys().isStormBolt(damager));
        if (!ours) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStormIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.LIGHTNING) {
            return;
        }
        Entity igniter = event.getIgnitingEntity();
        boolean ours = BossInstance.stormBoltActive()
                || (plugin instanceof BossEngine engine && engine.getKeys().isStormBolt(igniter));
        if (ours) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStormCombust(EntityCombustByEntityEvent event) {
        Entity combuster = event.getCombuster();
        boolean ours = combuster instanceof LightningStrike
                && (BossInstance.stormBoltActive()
                || (plugin instanceof BossEngine engine && engine.getKeys().isStormBolt(combuster)));
        if (ours) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSandboxProps(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        Player player = damager(event.getDamager());
        if (player == null) {
            return;
        }
        if (damaged.getPersistentDataContainer().has(
                AetherKeys.namespaced("bossengine", "sandbox_pillar"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            for (BossInstance instance : bossManager.getActive()) {
                if (instance.sandbox() != null && instance.sandbox().active()
                        && instance.sandbox().isPillar(damaged)) {
                    instance.sandbox().onPillarClick(player, damaged);
                    return;
                }
            }
            return;
        }
        String parity = damaged.getPersistentDataContainer().get(
                AetherKeys.namespaced("bossengine", "sandbox_parity"), PersistentDataType.STRING);
        if (parity == null) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
        for (BossInstance instance : bossManager.getActive()) {
            if (instance.getInstanceId() != null
                    && instance.getInstanceId().toString().equals(parity)
                    && instance.sandbox() != null) {
                instance.sandbox().onParityTwinHit(player);
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.END_ROD, damaged.getLocation().add(0, 0.6, 0), 12, 0.3, 0.4, 0.3, 0.02);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSandboxPillarClick(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!clicked.getPersistentDataContainer().has(
                AetherKeys.namespaced("bossengine", "sandbox_pillar"), PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        for (BossInstance instance : bossManager.getActive()) {
            if (instance.sandbox() != null && instance.sandbox().active()
                    && instance.sandbox().isPillar(clicked)) {
                instance.sandbox().onPillarClick(player, clicked);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHitsPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (BossHits.isApplying(player)) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (event.getDamager() instanceof Projectile) {
            return;
        }
        BossInstance instance = resolve(source);
        if (instance == null || !instance.isAlive()) {
            return;
        }
        event.setCancelled(true);
        double power = Math.max(event.getDamage(), instance.getAttackDamage());
        if ("baron_von_wurm".equalsIgnoreCase(instance.getTemplate().getId())
                && de.aetherion.bossengine.skill.t2.T2Mechanics.hasMiningSkills(player)) {
            power *= 1.30;
        }
        BossHits.hurt(player, instance.getEntity(), power);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (!sameBossFaction(victim, source)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDamage(EntityDamageEvent event) {
        Entity damaged = event.getEntity();
        if (damaged instanceof org.bukkit.entity.EnderDragonPart part) {
            damaged = part.getParent();
        }
        BossInstance instance = resolve(damaged);
        if (instance == null || !instance.isAlive()) {
            return;
        }
        instance.syncVanillaHealth();
        bossManager.refreshHud(instance);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossBodyRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        BossInstance instance = resolve(living);
        if (instance == null || !instance.isAlive()) {
            return;
        }
        if (instance.getEntity() != null && !instance.getEntity().getUniqueId().equals(living.getUniqueId())) {
            return;
        }
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.UNLOAD) {
            instance.markBodyUnloaded();
            plugin.getLogger().info(
                    "Boss '" + instance.getTemplate().getId()
                            + "' body unloaded with chunk — will reattach, not replace."
            );
            return;
        }
        if (instance.shouldIgnoreVanillaDeath() && cause != EntityRemoveEvent.Cause.DEATH) {
            instance.suppressBodyRestore(3_000L);
            plugin.getLogger().warning(
                    "Boss '" + instance.getTemplate().getId()
                            + "' body removed mid-fight cause=" + cause
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        BossInstance instance = resolve(event.getEntity());
        if (instance == null) {
            if (plugin instanceof BossEngine engine && engine.getKeys().isBoss(event.getEntity())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (instance.shouldIgnoreVanillaDeath()) {
            event.setCancelled(true);
            LivingEntity body = event.getEntity();
            instance.suppressBodyRestore(5_000L);
            // Paper can still drop the body unless we force HP back immediately.
            try {
                double max = Math.max(1.0, body.getMaxHealth());
                body.setHealth(max);
            } catch (IllegalArgumentException ignored) {
                // max-health race
            }
            instance.syncVanillaHealth();
            instance.getPlugin().getServer().getScheduler().runTask(instance.getPlugin(), () -> {
                if (!instance.isAlive()) {
                    return;
                }
                LivingEntity live = instance.getEntity();
                if (live == null || !live.isValid() || live.isDead()) {
                    return;
                }
                try {
                    live.setHealth(Math.max(1.0, live.getMaxHealth()));
                } catch (IllegalArgumentException ignored) {
                    // ignore
                }
                instance.syncVanillaHealth();
                instance.clearMissingBodyTicks();
            });
            return;
        }

        Player killer = event.getEntity().getKiller();
        bossManager.payoutDeath(instance, killer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onExplode(EntityExplodeEvent event) {
        if (plugin instanceof BossEngine engine && (engine.getKeys().isBeamFx(event.getEntity())
                || engine.getKeys().isMeteor(event.getEntity())
                || engine.getKeys().isHearthBolt(event.getEntity()))) {
            event.setCancelled(true);
            event.blockList().clear();
            event.setYield(0);
            return;
        }
        if (isBossExplosion(event.getEntity())) {
            event.blockList().clear();
            event.setYield(0);
            return;
        }
        if (event.getEntity() instanceof DragonFireball fireball && plugin instanceof BossEngine engine) {
            Double tagged = fireball.getPersistentDataContainer().get(
                    engine.getKeys().eggDamageKey(),
                    PersistentDataType.DOUBLE
            );
            if (tagged != null) {
                event.blockList().clear();
                event.setYield(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(org.bukkit.event.entity.ExplosionPrimeEvent event) {
        if (plugin instanceof BossEngine engine && (engine.getKeys().isBeamFx(event.getEntity())
                || engine.getKeys().isHearthBolt(event.getEntity()))) {
            event.setCancelled(true);
            event.setFire(false);
            event.setRadius(0f);
            return;
        }
        if (isBossExplosion(event.getEntity())) {
            event.setFire(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlimeSplit(org.bukkit.event.entity.SlimeSplitEvent event) {
        if (resolve(event.getEntity()) != null
                || (plugin instanceof BossEngine engine && engine.getKeys().isBoss(event.getEntity()))) {
            event.setCancelled(true);
            event.setCount(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(
                AetherKeys.namespaced("bossengine", "sandbox_debris"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            event.getEntity().remove();
            return;
        }
        if (plugin instanceof BossEngine engine && engine.getKeys().isDebris(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
            return;
        }
        if (isBossExplosion(event.getEntity())) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof org.bukkit.entity.Snowman && resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnowTrail(org.bukkit.event.block.EntityBlockFormEvent event) {
        if (resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(org.bukkit.event.entity.EntityTransformEvent event) {
        if (resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossShoot(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Entity shooter)) {
            return;
        }
        BossInstance instance = resolve(shooter);
        if (instance == null) {
            return;
        }
        if (plugin instanceof BossEngine engine
                && (engine.getKeys().isIceShard(event.getEntity())
                || engine.getKeys().isHearthBolt(event.getEntity()))) {
            return;
        }
        if ("dungeon_frostbound".equalsIgnoreCase(instance.getTemplate().getId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent event) {
        if (resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (resolve(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean shouldUncancelBossHit(BossInstance instance, EntityDamageEvent event) {
        if (instance == null || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return false;
        }
        return instance.getEntity() instanceof org.bukkit.entity.Enderman
                || "lobby_cleaner".equalsIgnoreCase(instance.getTemplate().getId());
    }

    private boolean isBossExplosion(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (resolve(entity) != null) {
            return true;
        }
        if (plugin instanceof BossEngine engine && (engine.getKeys().isBoss(entity)
                || engine.getKeys().isMinion(entity)
                || engine.getKeys().isBeamFx(entity)
                || engine.getKeys().isMeteor(entity)
                || engine.getKeys().isDebris(entity))) {
            return true;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            if (resolve(shooter) != null) {
                return true;
            }
            return plugin instanceof BossEngine engine
                    && (engine.getKeys().isBoss(shooter) || engine.getKeys().isMinion(shooter));
        }
        return false;
    }

    private BossInstance resolve(Entity entity) {
        return bossManager.getByEntity(entity).orElse(null);
    }

    private Player damager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager != null) {
            String owner = damager.getPersistentDataContainer().get(
                    AetherKeys.SET_MINION_OWNER,
                    PersistentDataType.STRING
            );
            if (owner != null && !owner.isBlank()) {
                try {
                    Player player = Bukkit.getPlayer(java.util.UUID.fromString(owner));
                    if (player != null && player.isOnline()) {
                        return player;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private boolean isWorldHazard(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.DRYOUT
                || cause == EntityDamageEvent.DamageCause.DROWNING
                || cause == EntityDamageEvent.DamageCause.LIGHTNING
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CAMPFIRE
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.MELTING
                || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.CRAMMING
                || cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.CONTACT
                || cause == EntityDamageEvent.DamageCause.FREEZE
                || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.MAGIC
                || cause == EntityDamageEvent.DamageCause.DRAGON_BREATH
                || cause == EntityDamageEvent.DamageCause.WITHER
                || cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.SONIC_BOOM
                || cause == EntityDamageEvent.DamageCause.THORNS
                || cause == EntityDamageEvent.DamageCause.FALLING_BLOCK
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER;
    }

    /** Any player-attributed hit counts as combat (wands FREEZE/FIRE/LIGHTNING included). */
    private boolean isPlayerCombatHit(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        return damager(byEntity.getDamager()) != null;
    }

    private static boolean isAbilityOrScriptedMelee(EntityDamageEvent event) {
        if (de.aetherion.core.combat.ScriptedHits.isActive()) {
            return true;
        }
        try {
            var type = event.getDamageSource().getDamageType();
            // Vanilla left-click melee uses PLAYER_ATTACK; scripted/ability sources do not.
            return type != org.bukkit.damage.DamageType.PLAYER_ATTACK
                    && type != org.bukkit.damage.DamageType.MOB_ATTACK
                    && type != org.bukkit.damage.DamageType.MOB_ATTACK_NO_AGGRO;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean sameBossFaction(Entity first, Entity second) {
        return isBossFaction(first) && isBossFaction(second);
    }

    private boolean isBossFaction(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!(plugin instanceof BossEngine engine)) {
            return false;
        }
        return engine.getKeys().isBoss(entity)
                || engine.getKeys().isMinion(entity)
                || bossManager.getByEntity(entity).isPresent();
    }
}
