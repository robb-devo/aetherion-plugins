package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Early pathblocker: sluggish giant, dual held blades,
 * readable spin, 25%-HP tracking beam, energy-guillotine death.
 */
final class PathwardenDirector {

    private static final Color AZURE = Color.fromRGB(80, 180, 255);
    private static final Color CRIMSON = Color.fromRGB(220, 40, 55);
    private static final Color WHITE = Color.fromRGB(255, 245, 240);
    private static final int SPIN_COOLDOWN = 160;
    private static final int SPIN_WINDUP = 35;
    private static final int SPIN_ACTIVE = 70;
    private static final int BEAM_SEEK = 80;
    private static final int BEAM_LOCK = 45;
    private static final int BEAM_FIRE = 14;
    private static final double BEAM_ZONE = 4.2;
    private static final double BEAM_BLAST = 4.2;
    private static final int DEATH_TICKS = 128;

    private final BossInstance instance;
    private final boolean[] beamUsed = new boolean[3];

    private int spinCd;
    private int spinPhase = -1;
    private float spinYaw;
    private int beamPhase = -1;
    private int beamTick;
    private Location beamFocus;
    private UUID beamTarget;
    private int deathTicks = -1;
    private Location deathFocus;
    private Location bladeAzure;
    private Location bladeCrimson;
    private Location deathAzureStart;
    private Location deathCrimsonStart;
    private Vector deathAzureVel;
    private Vector deathCrimsonVel;
    private int smashCd;

    PathwardenDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isPathwarden() {
        return instance.getTemplate() != null
                && "pathwarden".equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    void onBind() {
        if (!isPathwarden()) {
            return;
        }
        clearProps();
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        dressIronGate(entity);
        spinCd = 60;
        smashCd = 40;
        spinPhase = -1;
        beamPhase = -1;
        for (int i = 0; i < beamUsed.length; i++) {
            beamUsed[i] = false;
        }
    }

    void abort() {
        clearProps();
        deathTicks = -1;
        spinPhase = -1;
        beamPhase = -1;
    }

    boolean beginDeath() {
        if (!isPathwarden() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        clearCombatFx();
        deathTicks = 0;
        deathFocus = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : instance.getSpawnLocation().clone();
        if (deathFocus.getWorld() == null) {
            deathTicks = -1;
            return false;
        }
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setGlowing(true);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
            clearHands(entity);
        }
        World world = deathFocus.getWorld();
        world.playSound(deathFocus, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.5f);
        world.playSound(deathFocus, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.55f);
        world.playSound(deathFocus, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 0.65f);
        shout("&4&lPathwarden&7: &fThe gate keeps the toll.");
        initEnergyBlades(deathFocus);
        return true;
    }

    boolean tick() {
        if (!isPathwarden()) {
            return false;
        }
        if (isDying()) {
            return tickDeath();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || instance.isTransitioning()) {
            return false;
        }
        handAura(entity);
        if (beamPhase >= 0) {
            tickBeam(entity);
            return false;
        }
        maybeStartBeam(entity);
        if (beamPhase >= 0) {
            return false;
        }
        if (spinPhase >= 0) {
            tickSpin(entity);
            return false;
        }
        if (spinCd > 0) {
            spinCd--;
        } else if (nearest(entity, 28) != null) {
            beginSpin(entity);
        }
        if (smashCd > 0) {
            smashCd--;
        }
        slowCrawl(entity);
        return false;
    }

    private void maybeStartBeam(LivingEntity entity) {
        double pct = instance.healthPercent();
        int idx = pct <= 25.0 ? 2 : pct <= 50.0 ? 1 : pct <= 75.0 ? 0 : -1;
        if (idx < 0 || beamUsed[idx]) {
            return;
        }
        beamUsed[idx] = true;
        beginBeam(entity);
    }

    private void beginSpin(LivingEntity entity) {
        spinPhase = 0;
        spinYaw = entity.getLocation().getYaw();
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.45f);
        world.playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.55f, 0.7f);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
    }

    private void tickSpin(LivingEntity entity) {
        spinPhase++;
        World world = entity.getWorld();
        Location at = entity.getLocation();
        if (spinPhase <= SPIN_WINDUP) {
            double t = spinPhase / (double) SPIN_WINDUP;
            spinYaw += 1.2f;
            at.setYaw(spinYaw);
            entity.teleport(at);
            ringParticles(at, 2.2 + t * 1.5, AZURE, CRIMSON, 8);
            if (spinPhase == SPIN_WINDUP) {
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 0.55f);
            }
            return;
        }
        int active = spinPhase - SPIN_WINDUP;
        if (active <= SPIN_ACTIVE) {
            spinYaw += 5.1f;
            at.setYaw(spinYaw);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.teleport(at);
            tornado(at);
            if (active % 5 == 0) {
                hitAround(entity, 5.8, 280);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.65f);
            }
            return;
        }
        spinPhase = -1;
        spinCd = SPIN_COOLDOWN;
        if (entity instanceof Mob mob) {
            mob.setAI(true);
        }
    }

    private void beginBeam(LivingEntity entity) {
        beamPhase = 0;
        beamTick = 0;
        beamTarget = null;
        Player first = nearest(entity, 36);
        beamFocus = first != null
                ? ground(first.getLocation())
                : ground(entity.getLocation().clone().add(entity.getLocation().getDirection().multiply(6)));
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.35f);
        world.playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.1f);
        shout("&4&lPathwarden&7 draws a &bjudgment line&7.");
    }

    private void tickBeam(LivingEntity entity) {
        beamTick++;
        World world = entity.getWorld();
        Location from = entity.getLocation().clone().add(0, entity.getHeight() * 0.88, 0);
        if (beamPhase == 0) {
            if (beamTick % 10 == 0 || beamTarget == null) {
                Player pick = pickBeamCandidate(entity, 36);
                if (pick != null && (beamTarget == null || ThreadLocalRandomLike.chance(0.4))) {
                    beamTarget = pick.getUniqueId();
                }
            }
            Player locked = resolveTarget(entity.getWorld());
            Location want = locked != null ? ground(locked.getLocation()) : beamFocus;
            beamFocus = lerp(beamFocus, want, 0.09);
            drawSeekBeam(from, beamFocus.clone().add(0, 0.2, 0), AZURE);
            drawDangerZone(beamFocus, AZURE, false);
            if (beamTick >= BEAM_SEEK) {
                beamPhase = 1;
                beamTick = 0;
                if (locked == null) {
                    locked = nearest(entity, 36);
                }
                if (locked != null) {
                    beamTarget = locked.getUniqueId();
                    beamFocus = ground(locked.getLocation());
                }
                world.playSound(beamFocus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1f, 0.7f);
            }
            return;
        }
        if (beamPhase == 1) {
            // Zone freezes — leave the circle or eat the blast.
            double t = beamTick / (double) BEAM_LOCK;
            Color mix = mix(AZURE, CRIMSON, t);
            drawSeekBeam(from, beamFocus.clone().add(0, 0.2, 0), mix);
            drawDangerZone(beamFocus, mix, true);
            if (beamTick % 6 == 0) {
                world.playSound(beamFocus, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, (float) (0.7 + t));
            }
            if (beamTick >= BEAM_LOCK) {
                beamPhase = 2;
                beamTick = 0;
                world.playSound(beamFocus, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.25f, 0.55f);
                world.playSound(beamFocus, Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 0.55f);
            }
            return;
        }
        if (beamPhase == 2) {
            drawFireBeam(from, beamFocus.clone().add(0, 0.2, 0));
            if (beamTick == 1) {
                fireNuke(entity);
            }
            if (beamTick >= BEAM_FIRE) {
                beamPhase = -1;
                beamTick = 0;
                if (entity instanceof Mob mob) {
                    mob.setAI(true);
                }
            }
        }
    }

    private void fireNuke(LivingEntity entity) {
        World world = entity.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, beamFocus.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, beamFocus.clone().add(0, 0.2, 0), 90, 0.85, 0.2, 0.85, new Particle.DustOptions(CRIMSON, 1.9f));
        world.spawnParticle(Particle.END_ROD, beamFocus.clone().add(0, 0.3, 0), 24, 0.55, 0.15, 0.55, 0.02);
        boolean hitAnyone = false;
        double r2 = BEAM_BLAST * BEAM_BLAST;
        for (Player player : world.getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(beamFocus) > r2) {
                continue;
            }
            hitAnyone = true;
            BossHits.crush(player, entity, 140);
            Vector away = player.getLocation().toVector().subtract(beamFocus.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(0, 1, 0);
            } else {
                away.normalize();
            }
            away.multiply(2.35).setY(1.15);
            player.setVelocity(away);
            player.addPotionEffect(new PotionEffect(slowness(), 55, 2, false, true, true));
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 0.55f);
        }
        if (!hitAnyone) {
            world.playSound(beamFocus, Sound.BLOCK_FIRE_EXTINGUISH, 1.1f, 0.7f);
            world.spawnParticle(Particle.CLOUD, beamFocus.clone().add(0, 0.4, 0), 18, 0.45, 0.2, 0.45, 0.02);
            shout("&7Judgment &fmisses&7 — the path holds.");
        }
    }

    private boolean tickDeath() {
        deathTicks++;
        LivingEntity entity = instance.getEntity();
        World world = deathFocus.getWorld();
        if (world == null) {
            clearProps();
            return true;
        }
        Location strike = strikePoint();

        // 0-14: steel dissolves into twin energy cores at his hands
        if (deathTicks < 15) {
            double form = deathTicks / 14.0;
            bladeAzure = deathAzureStart.clone().add(0, form * 0.6, 0);
            bladeCrimson = deathCrimsonStart.clone().add(0, form * 0.6, 0);
            paintEnergyBlade(bladeAzure, toward(bladeAzure, strike), AZURE, 0.55f + (float) form * 0.7f, false);
            paintEnergyBlade(bladeCrimson, toward(bladeCrimson, strike), CRIMSON, 0.55f + (float) form * 0.7f, false);
            world.spawnParticle(Particle.FLASH, deathAzureStart, deathTicks == 1 ? 1 : 0, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLASH, deathCrimsonStart, deathTicks == 1 ? 1 : 0, 0, 0, 0, 0);
            if (deathTicks == 1 || deathTicks == 8) {
                world.playSound(deathFocus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.55f + deathTicks * 0.04f);
                world.playSound(deathFocus, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.4f);
            }
            return false;
        }

        // 15-40: blades rip free and soar outward
        if (deathTicks < 41) {
            double t = (deathTicks - 15) / 25.0;
            bladeAzure = deathAzureStart.clone().add(deathAzureVel.clone().multiply(3.4 * t));
            bladeCrimson = deathCrimsonStart.clone().add(deathCrimsonVel.clone().multiply(3.4 * t));
            paintEnergyBlade(bladeAzure, toward(bladeAzure, strike), AZURE, 1.15f, false);
            paintEnergyBlade(bladeCrimson, toward(bladeCrimson, strike), CRIMSON, 1.15f, false);
            trailBurst(bladeAzure, AZURE);
            trailBurst(bladeCrimson, CRIMSON);
            if (deathTicks % 6 == 0) {
                world.playSound(bladeAzure, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.4f);
                world.playSound(bladeCrimson, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.55f);
            }
            return false;
        }

        // 41-72: climb above his crown and lock tip-on
        if (deathTicks < 73) {
            double t = (deathTicks - 41) / 31.0;
            Location aHold = strike.clone().add(5.2, 4.6, 0.8);
            Location cHold = strike.clone().add(-5.2, 4.6, -0.8);
            Location aAim = strike.clone().add(1.35, 3.6, 0.2);
            Location cAim = strike.clone().add(-1.35, 3.6, -0.2);
            bladeAzure = lerp(aHold, aAim, easeOut(t));
            bladeCrimson = lerp(cHold, cAim, easeOut(t));
            paintEnergyBlade(bladeAzure, toward(bladeAzure, strike), AZURE, 1.35f + (float) t * 0.35f, t > 0.7);
            paintEnergyBlade(bladeCrimson, toward(bladeCrimson, strike), CRIMSON, 1.35f + (float) t * 0.35f, t > 0.7);
            if (deathTicks % 8 == 0) {
                world.playSound(strike, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.6f + (float) t);
            }
            ringParticles(strike.clone().add(0, -1.5, 0), 2.2 + t * 2.5, AZURE, CRIMSON, 14);
            return false;
        }

        // 73-88: charged tremor — blades scream before the cut
        if (deathTicks < 89) {
            double shake = Math.sin(deathTicks * 1.4) * 0.18;
            bladeAzure = strike.clone().add(1.35 + shake, 3.6, 0.2);
            bladeCrimson = strike.clone().add(-1.35 - shake, 3.6, -0.2);
            float power = 1.8f + (deathTicks - 73) * 0.05f;
            paintEnergyBlade(bladeAzure, toward(bladeAzure, strike), AZURE, power, true);
            paintEnergyBlade(bladeCrimson, toward(bladeCrimson, strike), CRIMSON, power, true);
            world.spawnParticle(Particle.ELECTRIC_SPARK, strike, 10, 0.5, 0.6, 0.5, 0.02);
            if (deathTicks % 3 == 0) {
                world.playSound(strike, Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.6f);
                world.playSound(strike, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.5f, 1.8f);
            }
            if (deathTicks == 88) {
                world.playSound(strike, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.35f, 0.45f);
                world.playSound(strike, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.85f, 0.7f);
            }
            return false;
        }

        // 89-102: guillotine impact
        if (deathTicks < 103) {
            double t = (deathTicks - 89) / 13.0;
            Location aFrom = strike.clone().add(1.35, 3.6, 0.2);
            Location cFrom = strike.clone().add(-1.35, 3.6, -0.2);
            bladeAzure = lerp(aFrom, strike, easeIn(t));
            bladeCrimson = lerp(cFrom, strike, easeIn(t));
            paintEnergyBlade(bladeAzure, toward(bladeAzure, strike), AZURE, 2.2f, true);
            paintEnergyBlade(bladeCrimson, toward(bladeCrimson, strike), CRIMSON, 2.2f, true);
            if (deathTicks == 100) {
                guillotineImpact(world, strike);
            }
            return false;
        }

        // 103-end: aftermath storm
        if (deathTicks < DEATH_TICKS) {
            aftermathStorm(world, strike, (deathTicks - 103) / (double) (DEATH_TICKS - 103));
            if (entity != null && entity.isValid()) {
                entity.teleport(deathFocus.clone().add(0, -0.05, 0));
            }
            return false;
        }
        clearProps();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        return true;
    }

    private void slowCrawl(LivingEntity entity) {
        Player target = nearest(entity, 30);
        if (target == null) {
            return;
        }
        Vector to = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        to.setY(0);
        double distSq = to.lengthSquared();
        if (distSq < 0.01) {
            return;
        }
        to.normalize();
        Location look = entity.getLocation().clone();
        look.setDirection(to);
        // Giants have no pathfinding AI — crawl by teleport so he actually moves.
        if (distSq > 9.0) {
            Location next = entity.getLocation().clone().add(to.clone().multiply(0.11));
            next.setYaw(look.getYaw());
            next.setPitch(0f);
            instance.runInternalTeleport(() -> {
                if (entity.isValid()) {
                    entity.teleport(next);
                }
            });
        } else {
            entity.setRotation(look.getYaw(), 0f);
            if (smashCd <= 0) {
                smashCd = 28;
                hitAround(entity, 4.4, 420);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.45f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.85f, 0.55f);
            }
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(target);
            mob.setAI(false);
            mob.setAware(true);
        }
    }

    private void dressIronGate(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(trimmed(Material.IRON_HELMET, TrimMaterial.LAPIS, TrimPattern.SENTRY));
        equipment.setChestplate(trimmed(Material.IRON_CHESTPLATE, TrimMaterial.REDSTONE, TrimPattern.SILENCE));
        equipment.setLeggings(trimmed(Material.IRON_LEGGINGS, TrimMaterial.LAPIS, TrimPattern.DUNE));
        equipment.setBoots(trimmed(Material.IRON_BOOTS, TrimMaterial.REDSTONE, TrimPattern.SENTRY));
        // Held blades stay vanilla-held (Giant hand sockets are reliable).
        // True blue/red paint needs a resource pack — we hint color with hand aura instead.
        equipment.setItemInMainHand(glinted(Material.DIAMOND_SWORD));
        equipment.setItemInOffHand(glinted(Material.NETHERITE_SWORD));
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
        equipment.setItemInOffHandDropChance(0f);
    }

    private static ItemStack glinted(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack trimmed(Material material, TrimMaterial trimMaterial, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(trimMaterial, pattern));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void clearHands(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItemInMainHand(new ItemStack(Material.AIR));
        equipment.setItemInOffHand(new ItemStack(Material.AIR));
    }

    /** Soft blue/red dust at hand height so the dual-blade theme still reads. */
    private void handAura(LivingEntity entity) {
        if (instance.getTicksAlive() % 4 != 0) {
            return;
        }
        World world = entity.getWorld();
        Location base = entity.getLocation().clone().add(0, Math.max(5.0, entity.getHeight() * 0.45), 0);
        float yaw = base.getYaw();
        double rad = Math.toRadians(yaw);
        Vector right = new Vector(-Math.cos(rad), 0, -Math.sin(rad)).normalize().multiply(3.6);
        Vector left = right.clone().multiply(-1);
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad)).multiply(1.2);
        Location a = base.clone().add(right).add(forward);
        Location c = base.clone().add(left).add(forward);
        world.spawnParticle(Particle.DUST, a, 3, 0.12, 0.22, 0.12, new Particle.DustOptions(AZURE, 1.15f));
        world.spawnParticle(Particle.DUST, c, 3, 0.12, 0.22, 0.12, new Particle.DustOptions(CRIMSON, 1.15f));
    }

    private Location strikePoint() {
        return deathFocus.clone().add(0, 8.8, 0);
    }

    private void initEnergyBlades(Location origin) {
        float yaw = origin.getYaw();
        double rad = Math.toRadians(yaw);
        Vector right = new Vector(-Math.cos(rad), 0, -Math.sin(rad)).normalize().multiply(3.6);
        Vector left = right.clone().multiply(-1);
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad)).multiply(1.2);
        deathAzureStart = origin.clone().add(0, 5.4, 0).add(right).add(forward);
        deathCrimsonStart = origin.clone().add(0, 5.4, 0).add(left).add(forward);
        deathAzureVel = right.clone().normalize().multiply(1.45).add(new Vector(0, 1.35, 0.25));
        deathCrimsonVel = left.clone().normalize().multiply(1.45).add(new Vector(0, 1.3, -0.25));
        bladeAzure = deathAzureStart.clone();
        bladeCrimson = deathCrimsonStart.clone();
    }

    private static Vector toward(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0e-4) {
            return new Vector(0, -1, 0);
        }
        return dir.normalize();
    }

    private static double easeOut(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    private static double easeIn(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t;
    }

    /**
     * Pure light-blade: tip points along tipDir, butt trails behind.
     */
    private void paintEnergyBlade(Location tip, Vector tipDir, Color color, float power, boolean charged) {
        if (tip == null || tip.getWorld() == null) {
            return;
        }
        World world = tip.getWorld();
        Vector dir = tipDir.clone().normalize();
        double length = 3.6 + power * 0.45;
        Location butt = tip.clone().subtract(dir.clone().multiply(length));
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location p = lerp(butt, tip, t);
            float size = (0.85f + (float) t * 1.15f) * Math.max(0.7f, power);
            world.spawnParticle(Particle.DUST, p, charged ? 3 : 2, 0.03, 0.03, 0.03, new Particle.DustOptions(color, size));
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.01, 0.01, 0.01, 0);
            }
            if (charged && i % 3 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, new Particle.DustOptions(WHITE, size * 0.7f));
            }
            if (color.getBlue() > color.getRed()) {
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0.02, 0.02, 0.02, 0.001);
                }
            } else if (i % 2 == 0) {
                world.spawnParticle(Particle.FLAME, p, 1, 0.02, 0.02, 0.02, 0.001);
            }
        }
        // Crossguard flare
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0e-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize().multiply(0.55 + power * 0.12);
        world.spawnParticle(Particle.DUST, butt.clone().add(side), 4, 0.05, 0.05, 0.05, new Particle.DustOptions(color, 1.4f * power));
        world.spawnParticle(Particle.DUST, butt.clone().subtract(side), 4, 0.05, 0.05, 0.05, new Particle.DustOptions(color, 1.4f * power));
        // Tip star
        world.spawnParticle(Particle.GLOW, tip, charged ? 8 : 3, 0.08, 0.08, 0.08, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, tip, charged ? 10 : 3, 0.1, 0.1, 0.1, 0.01);
        if (charged) {
            world.spawnParticle(Particle.FLASH, tip, 1, 0, 0, 0, 0);
        }
    }

    private void trailBurst(Location at, Color color) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 8, 0.15, 0.15, 0.15, new Particle.DustOptions(color, 1.5f));
        world.spawnParticle(Particle.END_ROD, at, 2, 0.08, 0.08, 0.08, 0.01);
    }

    private void guillotineImpact(World world, Location strike) {
        world.playSound(strike, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.35f);
        world.playSound(strike, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.15f, 0.5f);
        world.playSound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.55f);
        world.playSound(strike, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.75f);
        world.spawnParticle(Particle.FLASH, strike, 3, 0.2, 0.3, 0.2, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, strike, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, strike, 80, 0.7, 0.9, 0.7, new Particle.DustOptions(AZURE, 2.0f));
        world.spawnParticle(Particle.DUST, strike, 80, 0.7, 0.9, 0.7, new Particle.DustOptions(CRIMSON, 2.0f));
        bloodBurst(world, strike);
        // Shock ring
        for (int i = 0; i < 36; i++) {
            double ang = Math.toRadians(i * 10);
            double r = 2.8;
            world.spawnParticle(
                    Particle.DUST,
                    strike.getX() + Math.cos(ang) * r,
                    strike.getY() - 1.2,
                    strike.getZ() + Math.sin(ang) * r,
                    2, 0, 0, 0,
                    new Particle.DustOptions(i % 2 == 0 ? AZURE : CRIMSON, 1.6f)
            );
        }
    }

    private void aftermathStorm(World world, Location strike, double t) {
        bloodBurst(world, strike.clone().add(0, -0.4 + t * 0.2, 0));
        world.spawnParticle(Particle.DUST, strike, 24, 1.1, 1.4, 1.1, new Particle.DustOptions(AZURE, 1.6f));
        world.spawnParticle(Particle.DUST, strike, 24, 1.1, 1.4, 1.1, new Particle.DustOptions(CRIMSON, 1.6f));
        world.spawnParticle(Particle.SOUL, strike, 6, 0.7, 1.0, 0.7, 0.01);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, strike, 12, 0.8, 1.0, 0.8, 0);
        if (deathTicks % 5 == 0) {
            world.playSound(strike, Sound.BLOCK_LAVA_POP, 0.5f, 0.4f);
        }
        // Falling energy shreds
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(deathTicks * 18 + i * 60);
            double r = 1.2 + t * 2.5;
            Location p = strike.clone().add(Math.cos(ang) * r, 1.5 - t * 2.2, Math.sin(ang) * r);
            world.spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05,
                    new Particle.DustOptions(i % 2 == 0 ? AZURE : CRIMSON, 1.3f));
        }
    }

    private void drawDangerZone(Location center, Color color, boolean pulsing) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = pulsing ? BEAM_ZONE * (1.0 + 0.06 * Math.sin(beamTick * 0.5)) : BEAM_ZONE;
        int points = pulsing ? 28 : 20;
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2 * i) / points + (pulsing ? beamTick * 0.08 : 0);
            double x = center.getX() + Math.cos(ang) * radius;
            double z = center.getZ() + Math.sin(ang) * radius;
            world.spawnParticle(Particle.DUST, x, center.getY() + 0.08, z, 1, 0, 0, 0, new Particle.DustOptions(color, pulsing ? 1.55f : 1.25f));
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, x, center.getY() + 0.12, z, 1, 0, 0, 0, 0);
            }
        }
        // Soft floor fill so the escape circle reads as a field.
        world.spawnParticle(
                Particle.DUST,
                center.getX(),
                center.getY() + 0.05,
                center.getZ(),
                pulsing ? 18 : 10,
                radius * 0.35,
                0.02,
                radius * 0.35,
                new Particle.DustOptions(color, 1.1f)
        );
    }

    private void drawSeekBeam(Location from, Location to, Color color) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = 26;
        for (int i = 0; i <= steps; i++) {
            Location p = from.clone().add(delta.clone().multiply(i / (double) steps));
            world.spawnParticle(Particle.DUST, p, 2, 0.04, 0.04, 0.04, new Particle.DustOptions(color, 1.45f));
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.01, 0.01, 0.01, 0);
            }
            if (i % 4 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        world.spawnParticle(Particle.DUST, to.clone().add(0, 0.15, 0), 14, 0.35, 0.08, 0.35, new Particle.DustOptions(color, 1.7f));
        world.spawnParticle(Particle.GLOW, to.clone().add(0, 0.25, 0), 6, 0.2, 0.1, 0.2, 0);
    }

    private void drawFireBeam(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= 32; i++) {
            Location p = from.clone().add(delta.clone().multiply(i / 32.0));
            world.spawnParticle(Particle.DUST, p, 5, 0.1, 0.1, 0.1, new Particle.DustOptions(CRIMSON, 2.1f));
            world.spawnParticle(Particle.FLAME, p, 2, 0.06, 0.06, 0.06, 0.015);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.LAVA, p, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticle(Particle.FLASH, to.clone().add(0, 0.5, 0), 2, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, to.clone().add(0, 0.3, 0), 2, 0.2, 0.1, 0.2, 0);
    }

    private void tornado(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        // Fat dual-helix cone: narrow feet, roaring crown.
        double height = 8.4;
        int layers = 12;
        for (int layer = 0; layer < layers; layer++) {
            double t = layer / (double) (layers - 1);
            double y = at.getY() + 0.2 + height * t;
            double radius = 1.15 + t * 5.2;
            int points = 10 + layer * 2;
            for (int i = 0; i < points; i++) {
                double ang = Math.toRadians(spinYaw * 1.85 + i * (360.0 / points) + layer * 18);
                double x = at.getX() + Math.cos(ang) * radius;
                double z = at.getZ() + Math.sin(ang) * radius;
                Color c = (i + layer) % 2 == 0 ? AZURE : CRIMSON;
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, new Particle.DustOptions(c, 1.45f));
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
                if (layer > 7 && i % 4 == 0) {
                    world.spawnParticle(c.getBlue() > c.getRed() ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, x, y, z, 1, 0, 0, 0, 0.001);
                }
            }
            // Counter-helix
            double ang2 = Math.toRadians(-spinYaw * 1.5 + layer * 40);
            world.spawnParticle(
                    Particle.DUST,
                    at.getX() + Math.cos(ang2) * (radius * 0.72),
                    y,
                    at.getZ() + Math.sin(ang2) * (radius * 0.72),
                    2, 0.05, 0.05, 0.05,
                    new Particle.DustOptions(layer % 2 == 0 ? CRIMSON : AZURE, 1.25f)
            );
        }
        // Ground scrape ring
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.15, 0), 6, 1.4, 0.05, 1.4, 0.01);
        world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1.2, 0), 2, 1.8, 0.4, 1.8, 0);
    }

    private void ringParticles(Location at, double radius, Color a, Color b, int points) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            Color c = i % 2 == 0 ? a : b;
            world.spawnParticle(
                    Particle.DUST,
                    at.getX() + Math.cos(ang) * radius,
                    at.getY() + 0.2,
                    at.getZ() + Math.sin(ang) * radius,
                    1, 0, 0, 0,
                    new Particle.DustOptions(c, 1.1f)
            );
        }
    }

    private void hitAround(LivingEntity entity, double radius, double power) {
        World world = entity.getWorld();
        double r2 = radius * radius;
        Location at = entity.getLocation();
        for (Player player : world.getPlayers()) {
            if (!vulnerable(player) || player.getLocation().distanceSquared(at) > r2) {
                continue;
            }
            BossHits.hurt(player, entity, power);
            Vector push = player.getLocation().toVector().subtract(at.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 0.4, 0);
            } else {
                push.normalize().multiply(0.85).setY(0.35);
            }
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    private void bloodBurst(World world, Location at) {
        world.spawnParticle(Particle.DUST, at, 90, 0.9, 1.2, 0.9, new Particle.DustOptions(CRIMSON, 2.0f));
        world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 40, 0.8, 1.0, 0.8, 0);
        world.spawnParticle(Particle.BLOCK, at, 70, 0.7, 0.9, 0.7, 0.08, Material.REDSTONE_BLOCK.createBlockData());
        world.spawnParticle(Particle.BLOCK, at, 35, 0.5, 0.7, 0.5, 0.04, Material.RED_CONCRETE.createBlockData());
        world.playSound(at, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.5f);
    }

    private Player nearest(LivingEntity entity, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(entity.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private Player pickBeamCandidate(LivingEntity entity, double range) {
        List<Player> pool = new ArrayList<>();
        double r2 = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(entity.getLocation()) <= r2) {
                pool.add(player);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private Player resolveTarget(World world) {
        if (beamTarget == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(beamTarget);
        return vulnerable(player) ? player : null;
    }

    private static boolean vulnerable(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static Location ground(Location loc) {
        Location at = loc.clone();
        at.setY(Math.floor(at.getY()));
        return at;
    }

    private static Location lerp(Location from, Location to, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Location(
                to.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t,
                from.getYaw(),
                from.getPitch()
        );
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    private static PotionEffectType slowness() {
        PotionEffectType type = PotionEffectType.getByName("SLOWNESS");
        return type != null ? type : PotionEffectType.getByName("SLOW");
    }

    private void shout(String line) {
        for (Player player : instance.getSpawnLocation().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(instance.getSpawnLocation()) <= 48 * 48) {
                player.sendMessage(TextUtil.component(line));
            }
        }
    }

    private void clearCombatFx() {
        beamPhase = -1;
        spinPhase = -1;
    }

    private void clearProps() {
        bladeAzure = null;
        bladeCrimson = null;
        deathAzureStart = null;
        deathCrimsonStart = null;
        deathAzureVel = null;
        deathCrimsonVel = null;
    }

    /** Tiny helper so we don't import ThreadLocalRandom just for one roll. */
    private static final class ThreadLocalRandomLike {
        static boolean chance(double p) {
            return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < p;
        }
    }
}
