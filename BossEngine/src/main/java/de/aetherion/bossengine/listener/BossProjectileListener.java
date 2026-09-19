package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class BossProjectileListener implements Listener {

    private final BossKeys keys;

    public BossProjectileListener(BossKeys keys) {
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (keys.isHearthBolt(event.getEntity())) {
            handleHearth(event, event.getEntity());
            return;
        }
        if (event.getEntity() instanceof Egg egg) {
            handleEgg(event, egg);
            return;
        }
        if (event.getEntity() instanceof Snowball snowball) {
            handleInk(event, snowball);
            return;
        }
        if (event.getEntity() instanceof DragonFireball fireball) {
            handleDragonFireball(event, fireball);
            return;
        }
        if (event.getEntity() instanceof Fireball fireball && keys.isMeteor(fireball)) {
            handleMeteor(event, fireball);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggHatch(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.EGG
                && reason != CreatureSpawnEvent.SpawnReason.DISPENSE_EGG
                && reason != CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }
        org.bukkit.Location at = event.getLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Entity nearby : at.getWorld().getNearbyEntities(at, 48, 24, 48)) {
            if (keys.isBoss(nearby) || keys.isMinion(nearby)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleEgg(ProjectileHitEvent event, Egg egg) {
        Double damage = egg.getPersistentDataContainer().get(keys.eggDamageKey(), PersistentDataType.DOUBLE);
        if (damage == null || damage <= 0) {
            return;
        }

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            Entity shooter = egg.getShooter() instanceof Entity entity ? entity : null;
            if (living instanceof Player player) {
                BossHits.hurt(player, shooter, damage);
                softEggKnockback(player, egg.getLocation(), egg);
            } else {
                living.damage(damage, shooter);
            }
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.9f, 1.15f);
            living.getWorld().spawnParticle(Particle.CLOUD, living.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.02);
        }

        event.setCancelled(true);
        egg.remove();
    }

    /**
     * BossHits.damage() applies vanilla knockback scaled to huge HP chunks.
     * Override next tick with a mild egg shove from config.
     */
    private void softEggKnockback(Player player, Location from, Egg egg) {
        double strength = egg.getPersistentDataContainer()
                .getOrDefault(keys.eggKnockbackKey(), PersistentDataType.DOUBLE, 0.28d);
        if (strength <= 0.0d || from == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("BossEngine");
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && !player.isDead()) {
                        player.setVelocity(new Vector(0, Math.min(0.12d, player.getVelocity().getY()), 0));
                    }
                });
            }
            return;
        }
        Vector away = player.getLocation().toVector().subtract(from.toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0e-4d) {
            Vector look = player.getLocation().getDirection();
            away = new Vector(-look.getX(), 0, -look.getZ());
        }
        if (away.lengthSquared() < 1.0e-4d) {
            away = new Vector(1, 0, 0);
        }
        Vector knock = away.normalize().multiply(strength).setY(0.14d);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (plugin == null) {
            player.setVelocity(knock);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                player.setVelocity(knock);
            }
        });
    }

    private void handleInk(ProjectileHitEvent event, Snowball snowball) {
        Double damage = snowball.getPersistentDataContainer().get(keys.inkDamageKey(), PersistentDataType.DOUBLE);
        if (damage == null || damage <= 0) {
            return;
        }

        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity living && living.isValid() && !living.isDead() && !keys.isBoss(living)) {
            Entity shooter = snowball.getShooter() instanceof Entity entity ? entity : null;
            if (living instanceof Player player) {
                BossHits.hurt(player, shooter, damage);
            } else {
                living.damage(damage, shooter);
            }
            if (keys.isIceShard(snowball)) {
                living.getWorld().playSound(living.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.05f, 0.85f);
                living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 0.7f);
                living.getWorld().spawnParticle(Particle.SNOWFLAKE, living.getEyeLocation(), 22, 0.4, 0.4, 0.4, 0.04);
                living.getWorld().spawnParticle(Particle.SNOWFLAKE, living.getEyeLocation(), 10, 0.25, 0.25, 0.25, 0.02);
            } else {
                living.getWorld().playSound(living.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1.05f, 0.7f);
                living.getWorld().spawnParticle(Particle.SQUID_INK, living.getEyeLocation(), 18, 0.35, 0.35, 0.35, 0.04);
                living.getWorld().spawnParticle(Particle.SMOKE, living.getEyeLocation(), 8, 0.25, 0.25, 0.25, 0.01);
            }

            if (living instanceof Player player) {
                int blind = snowball.getPersistentDataContainer().getOrDefault(keys.inkBlindKey(), PersistentDataType.INTEGER, 0);
                int slow = snowball.getPersistentDataContainer().getOrDefault(keys.inkSlowKey(), PersistentDataType.INTEGER, 0);
                int poison = snowball.getPersistentDataContainer().getOrDefault(keys.inkPoisonKey(), PersistentDataType.INTEGER, 0);
                if (blind > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blind, 0, true, true, true));
                }
                if (slow > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, true, true, true));
                }
                if (poison > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poison, 0, true, true, true));
                }
            }
        } else if (keys.isIceShard(snowball)) {
            snowball.getWorld().spawnParticle(Particle.SNOWFLAKE, snowball.getLocation(), 12, 0.25, 0.2, 0.25, 0.03);
            snowball.getWorld().playSound(snowball.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.3f);
        } else {
            snowball.getWorld().spawnParticle(Particle.SQUID_INK, snowball.getLocation(), 10, 0.2, 0.2, 0.2, 0.02);
            snowball.getWorld().playSound(snowball.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 0.55f, 1.2f);
        }

        event.setCancelled(true);
        snowball.remove();
    }

    private void handleHearth(ProjectileHitEvent event, Projectile projectile) {
        event.setCancelled(true);
        Entity hit = event.getHitEntity();
        de.aetherion.bossengine.BossEngine engine = de.aetherion.bossengine.BossEngine.getInstance();
        if (engine != null && hit != null) {
            engine.getBossManager().getByEntity(hit).ifPresent(instance -> instance.hearthArrived(projectile));
        }
        if (projectile.isValid()) {
            projectile.remove();
        }
    }

    private void handleDragonFireball(ProjectileHitEvent event, DragonFireball fireball) {
        Double damage = fireball.getPersistentDataContainer().get(keys.eggDamageKey(), PersistentDataType.DOUBLE);
        if (damage == null || damage <= 0) {
            return;
        }
        org.bukkit.Location at = fireball.getLocation();
        org.bukkit.World world = at.getWorld();
        if (world == null) {
            return;
        }
        event.setCancelled(true);
        Entity shooter = fireball.getShooter() instanceof Entity entity ? entity : null;
        double radiusSq = 4.6 * 4.6;
        for (Player player : world.getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(at) > radiusSq) {
                continue;
            }
            BossHits.hurt(player, shooter, damage);
        }
        world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.7f, 0.85f);
        world.spawnParticle(Particle.DRAGON_BREATH, at, 24, 0.7, 0.4, 0.7, 0.03);
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        for (Entity nearby : fireball.getNearbyEntities(6, 6, 6)) {
            if (nearby instanceof org.bukkit.entity.AreaEffectCloud) {
                nearby.remove();
            }
        }
        fireball.remove();
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("BossEngine");
        if (plugin != null) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity nearby : world.getNearbyEntities(at, 6, 6, 6)) {
                    if (nearby instanceof org.bukkit.entity.AreaEffectCloud) {
                        nearby.remove();
                    }
                }
            });
        }
    }

    private void handleMeteor(ProjectileHitEvent event, Fireball fireball) {
        double damage = keys.meteorDamage(fireball);
        org.bukkit.Location at = fireball.getLocation();
        event.setCancelled(true);
        Entity shooter = fireball.getShooter() instanceof Entity entity ? entity : null;
        fireball.remove();
        if (at.getWorld() == null) {
            return;
        }
        de.aetherion.bossengine.BossEngine engine = de.aetherion.bossengine.BossEngine.getInstance();
        de.aetherion.bossengine.instance.BossInstance instance = null;
        if (engine != null && shooter instanceof LivingEntity living) {
            instance = engine.getBossManager().getByEntity(living).orElse(null);
        }
        if (instance == null) {
            at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
            at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
            return;
        }
        de.aetherion.bossengine.fx.FakeDestruction.boom(at, instance, damage, 4.4, true);
    }
}
