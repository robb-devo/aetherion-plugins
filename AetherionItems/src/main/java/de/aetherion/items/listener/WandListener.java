package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.combat.UndeadCombat;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WandListener implements Listener {

    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();

    public WandListener(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!itemManager.isWand(item)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        cast(player, item);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        if (itemManager.isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void cast(Player player, ItemStack item) {
        long now = System.currentTimeMillis();
        String itemId = itemManager.getItemId(item);
        String cdKey = player.getUniqueId() + ":" + (itemId != null ? itemId : "wand");
        Long ready = cooldown.get(cdKey);
        if (ready != null && ready > now) {
            long left = (ready - now + 999L) / 1000L;
            player.sendMessage("§7Still gathering. §f" + left + "s");
            return;
        }
        double range = itemManager.getWandRange(item);
        Location target = aim(player, range);
        if (target == null || target.getWorld() == null) {
            return;
        }
        cooldown.put(cdKey, now + de.aetherion.items.listener.ProgressionEffects.cooldownMs(player, itemManager, itemManager.getWandCooldownMs(item)));

        String type = itemManager.getWandType(item);
        double blast = itemManager.getWandBlast(item);
        int chain = itemManager.getWandChain(item);
        int slowTicks = itemManager.getWandSlowTicks(item);

        World world = target.getWorld();

        if ("heal".equals(type)) {
            castHeal(player, target, world, blast);
            return;
        }

        double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
        if (damage <= 0) {
            damage = 20.0;
        }
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        boolean crit = critChance > 0.0 && Math.random() * 100.0 < critChance;
        if (crit) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            de.aetherion.items.combat.DamageNumbers.markCrit(player);
        }

        playCast(world, target, type);

        List<LivingEntity> hit = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(target, blast, blast + 1.2, blast)) {
            if (entity instanceof LivingEntity living && canHit(player, living)) {
                hit.add(living);
            }
        }
        chainFrom(player, hit, chain, 5.0);

        DamageSource source = DamageSource.builder(damageType(type))
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build();
        int landed = 0;
        Set<UUID> seen = new HashSet<>();
        for (LivingEntity living : hit) {
            if (!seen.add(living.getUniqueId())) {
                continue;
            }
            double dealt = applyUndead(player, living, damage);
            de.aetherion.items.combat.ScriptedHits.run(() -> living.damage(dealt, source));
            if ("ember".equals(type)) {
                living.setFireTicks(60);
            }
            if (slowTicks > 0) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, false, true, true));
            }
            landed++;
        }
        if (landed == 0) {
            player.sendMessage("§7Nothing to hit.");
        }
    }

    private void chainFrom(Player player, List<LivingEntity> hit, int extra, double reach) {
        if (extra <= 0 || hit.isEmpty()) {
            return;
        }
        Set<UUID> known = new HashSet<>();
        for (LivingEntity living : hit) {
            known.add(living.getUniqueId());
        }
        LivingEntity from = hit.get(0);
        World world = from.getWorld();
        for (int hop = 0; hop < extra; hop++) {
            LivingEntity next = nearest(player, from, known, reach);
            if (next == null) {
                return;
            }
            known.add(next.getUniqueId());
            hit.add(next);
            drawSpark(world, from.getLocation().add(0, 1, 0), next.getLocation().add(0, 1, 0));
            world.spawnParticle(Particle.ELECTRIC_SPARK, next.getLocation().add(0, 1, 0), 12, 0.2, 0.4, 0.2, 0.04);
            from = next;
        }
    }

    private LivingEntity nearest(Player player, LivingEntity from, Set<UUID> known, double reach) {
        LivingEntity best = null;
        double bestDist = reach * reach;
        for (Entity entity : from.getWorld().getNearbyEntities(from.getLocation(), reach, reach, reach)) {
            if (!(entity instanceof LivingEntity living) || !canHit(player, living) || known.contains(living.getUniqueId())) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(from.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    private void drawSpark(World world, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            Location point = from.clone().add(delta.clone().multiply(i / (double) steps));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private void castHeal(Player player, Location target, World world, double blast) {
        world.playSound(target, Sound.BLOCK_BELL_USE, 0.9f, 1.4f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 1, 0), 32, 1.2, 0.8, 1.2, 0.02);
        int healed = 0;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), 8, 8, 8)) {
            if (entity instanceof Player nearby && !nearby.isDead()) {
                double max = nearby.getMaxHealth();
                double current = nearby.getHealth();
                if (current < max) {
                    double missing = max - current;
                    double heal = Math.max(4.0, missing * 0.15);
                    nearby.setHealth(Math.min(max, current + heal));
                    world.spawnParticle(Particle.HAPPY_VILLAGER, nearby.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
                    healed++;
                }
            }
        }
        if (healed == 0) {
            player.sendMessage("§aKein Spieler in der Nähe braucht Heilung.");
        } else {
            player.sendMessage("§a" + healed + " Spieler geheilt.");
        }
    }

    private void playCast(World world, Location target, String type) {
        if ("frost".equals(type)) {
            world.playSound(target, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 0.7f);
            world.spawnParticle(Particle.SNOWFLAKE, target.clone().add(0, 1, 0), 28, 0.5, 0.7, 0.5, 0.02);
            world.spawnParticle(Particle.CLOUD, target.clone().add(0, 0.4, 0), 8, 0.3, 0.2, 0.3, 0.01);
            return;
        }
        if ("ember".equals(type)) {
            world.playSound(target, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.8f);
            world.spawnParticle(Particle.FLAME, target.clone().add(0, 1, 0), 36, 0.6, 0.8, 0.6, 0.04);
            world.spawnParticle(Particle.LAVA, target.clone().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        world.strikeLightningEffect(target);
        world.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.85f, "chain".equals(type) ? 1.35f : 1.15f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, 1, 0), 24, 0.4, 0.8, 0.4, 0.05);
    }

    private DamageType damageType(String type) {
        if ("frost".equals(type)) return DamageType.FREEZE;
        if ("ember".equals(type)) return DamageType.ON_FIRE;
        return DamageType.LIGHTNING_BOLT;
    }

    private double applyUndead(Player player, LivingEntity target, double damage) {
        if (!UndeadCombat.isUndead(target)) {
            return damage;
        }
        double bonus = Math.max(0.0, equipmentStats.getStat(player, ItemCapability.UNDEAD_DAMAGE));
        if (bonus <= 0.0) {
            return damage;
        }
        return damage * (1.0 + Math.min(80.0, bonus) / 100.0);
    }

    private Location aim(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, range, 0.35, entity ->
                entity instanceof LivingEntity living && canHit(player, living));
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double entityDist = entityHit == null || entityHit.getHitPosition() == null
                ? Double.MAX_VALUE
                : entityHit.getHitPosition().distanceSquared(eye.toVector());
        double blockDist = blockHit == null || blockHit.getHitPosition() == null
                ? Double.MAX_VALUE
                : blockHit.getHitPosition().distanceSquared(eye.toVector());
        if (entityDist < blockDist && entityHit != null && entityHit.getHitPosition() != null) {
            return entityHit.getHitPosition().toLocation(world);
        }
        if (blockHit != null) {
            Block block = blockHit.getHitBlock();
            if (block != null) {
                return block.getLocation().add(0.5, 1.0, 0.5);
            }
            if (blockHit.getHitPosition() != null) {
                return blockHit.getHitPosition().toLocation(world);
            }
        }
        return eye.add(dir.multiply(range));
    }

    private boolean canHit(Player player, LivingEntity target) {
        if (target.equals(player) || target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !AetherEntities.isPet(target);
    }
}
