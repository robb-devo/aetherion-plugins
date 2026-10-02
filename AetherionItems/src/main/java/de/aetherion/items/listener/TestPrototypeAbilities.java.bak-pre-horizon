package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.world.TestArenaGuard;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cinematic ability FX for sandbox prototype weapons (Test Arena only items).
 */
final class TestPrototypeAbilities implements Listener {

    private static final Material[] VOID_DEBRIS = {
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.BLACKSTONE,
            Material.POLISHED_BLACKSTONE,
            Material.END_STONE,
            Material.PURPUR_BLOCK,
            Material.AMETHYST_BLOCK
    };

    private static final Material[] STORM_DEBRIS = {
            Material.WHITE_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.RED_CONCRETE,
            Material.QUARTZ_BLOCK,
            Material.GLASS
    };

    private static final Material[] WIND_DEBRIS = {
            Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL,
            Material.SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.TERRACOTTA
    };

    private final JavaPlugin plugin;
    private final Set<UUID> meteorBusy = new HashSet<>();
    private final Set<UUID> cataclysmBusy = new HashSet<>();
    private final Set<UUID> runeSigilBusy = new HashSet<>();
    private final Set<UUID> worldSplitterBusy = new HashSet<>();
    private final Set<UUID> vesperBusy = new HashSet<>();
    private final Set<UUID> deepsongBusy = new HashSet<>();
    private final Set<UUID> terminusBusy = new HashSet<>();
    private final Set<UUID> gravityBusy = new HashSet<>();
    private final Set<UUID> stormBusy = new HashSet<>();
    private final Set<UUID> beamBusy = new HashSet<>();
    private final Set<UUID> dashBusy = new HashSet<>();
    private final Set<UUID> tornadoBusy = new HashSet<>();
    private final Set<UUID> prismBusy = new HashSet<>();
    private final Set<UUID> cascadeBusy = new HashSet<>();

    TestPrototypeAbilities(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean isGravityBusy(UUID id) {
        return gravityBusy.contains(id);
    }

    boolean isStormBusy(UUID id) {
        return stormBusy.contains(id);
    }

    boolean isBeamBusy(UUID id) {
        return beamBusy.contains(id);
    }

    boolean isDashBusy(UUID id) {
        return dashBusy.contains(id);
    }

    boolean isTornadoBusy(UUID id) {
        return tornadoBusy.contains(id);
    }

    boolean isPrismBusy(UUID id) {
        return prismBusy.contains(id);
    }

    boolean isCascadeBusy(UUID id) {
        return cascadeBusy.contains(id);
    }

    boolean isMeteorBusy(UUID id) {
        return meteorBusy.contains(id);
    }


    boolean isCataclysmBusy(UUID id) {
        return cataclysmBusy.contains(id);
    }


    boolean isRuneSigilBusy(UUID id) {
        return runeSigilBusy.contains(id);
    }


    boolean isWorldSplitterBusy(UUID id) {
        return worldSplitterBusy.contains(id);
    }


    boolean isVesperBusy(UUID id) {
        return vesperBusy.contains(id);
    }


    boolean isDeepsongBusy(UUID id) {
        return deepsongBusy.contains(id);
    }


    /** Busy for this caster, or anyone: the world has one edge, so only one Terminus runs at a time. */
    boolean isTerminusBusy(UUID id) {
        return terminusBusy.contains(id) || TerminusEdge.isRunning();
    }


    /** Prototype weapons skip CDs inside the Test Arena. */
    static boolean freeCd(Player player) {
        return player != null && TestArenaGuard.isArena(player.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDebrisLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        if (!falling.getPersistentDataContainer().has(ItemKeys.testDebris(), PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        falling.remove();
    }

    /** 2.5s suck → brutal detonation. Pulls/damages enemies & animals only. */
    public void castGravwell(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!gravityBusy.add(id)) {
            return;
        }
        Location focus = focusAhead(player, 7.5).add(0, 1.1, 0);
        World world = focus.getWorld();
        if (world == null) {
            gravityBusy.remove(id);
            return;
        }

        player.sendMessage("§5✦ Gravwell §7opening…");
        world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.85f, 0.55f);
        world.playSound(focus, Sound.BLOCK_PORTAL_TRIGGER, 0.55f, 0.7f);

        List<FallingBlock> debris = new ArrayList<>();
        Set<UUID> latched = new HashSet<>();
        double boomDamage = Math.max(160.0, weaponDamage * 3.8 + 55.0);

        new BukkitRunnable() {
            int tick = 0;
            final int chargeTicks = 55;

            @Override
            public void run() {
                if (!player.isOnline() || world != focus.getWorld()) {
                    clearDebris(debris);
                    gravityBusy.remove(id);
                    cancel();
                    return;
                }
                tick++;
                double progress = tick / (double) chargeTicks;

                if (tick <= chargeTicks) {
                    drawRing(world, focus, 2.2 + (1.0 - progress) * 7.5, tick);
                    world.spawnParticle(Particle.REVERSE_PORTAL, focus, 18, 0.35, 0.35, 0.35, 0.35);
                    world.spawnParticle(Particle.SQUID_INK, focus, 10, 0.55, 0.55, 0.55, 0.02);
                    world.spawnParticle(Particle.DUST, focus, 8, 0.9, 0.7, 0.9, 0,
                            new Particle.DustOptions(Color.fromRGB(90, 40, 160), 1.35f));
                    if (tick % 7 == 0) {
                        world.playSound(focus, Sound.BLOCK_PORTAL_AMBIENT, 0.45f, 0.5f + (float) progress * 0.6f);
                        spawnDebris(world, focus, debris, VOID_DEBRIS, false);
                    }
                    pullDebris(focus, debris, 0.16 + progress * 0.28);
                    latchAndPull(player, focus, latched, 11.5, 0.14 + progress * 0.26);
                    return;
                }

                clearDebris(debris);
                spitDebris(world, focus, VOID_DEBRIS);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 5, 1.0, 0.55, 1.0, 0);
                world.spawnParticle(Particle.FLASH, focus, 4, 0.5, 0.4, 0.5, 0);
                world.spawnParticle(Particle.SONIC_BOOM, focus, 1, 0, 0, 0, 0);
                world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.55f);
                world.playSound(focus, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.55f, 0.7f);
                world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 0.45f);

                for (Entity entity : world.getNearbyEntities(focus, 12.0, 10.0, 12.0)) {
                    if (!isCombatTarget(entity)) {
                        continue;
                    }
                    LivingEntity living = (LivingEntity) entity;
                    boolean pulled = latched.contains(living.getUniqueId())
                            || living.getLocation().distanceSquared(focus) <= 64.0;
                    if (!pulled) {
                        continue;
                    }
                    abilityDamage(living, boomDamage, player);
                    Vector knock = living.getLocation().toVector().subtract(focus.toVector());
                    if (knock.lengthSquared() > 0.01) {
                        living.setVelocity(knock.normalize().multiply(1.55).setY(0.85));
                    }
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0);
                }
                player.sendMessage("§5✦ Gravwell §cdetonated.");
                gravityBusy.remove(id);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    void castStorm(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!stormBusy.add(id)) {
            return;
        }
        double strikeDamage = Math.max(42.0, weaponDamage * 0.85 + 18.0);
        player.sendMessage("§e✦ Stormcaller §7— thunder rolls for §f6s§7.");
        StormcallerTempest.cast(plugin, player, strikeDamage, () -> stormBusy.remove(id));
    }

    void castSonic(Player player, double weaponDamage) {
        player.sendActionBar(net.kyori.adventure.text.Component.text("§3Resonance Wave"));
        ResonanceScytheWave.cast(plugin, player, weaponDamage);
    }

    /** Tracking beacon beam → green→red → nuke (Pathwarden-ish). */
    void castJudgmentBeam(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!beamBusy.add(id)) {
            return;
        }
        double boom = Math.max(140.0, weaponDamage * 3.2 + 40.0);
        player.sendMessage("§a✦ Judgment Beam §7locking…");
        JudgmentVerdict.cast(plugin, player, boom, () -> beamBusy.remove(id));
    }

    /** Real forward dash (~5 blocks), not a teleport. */
    void castDash(Player player) {
        UUID id = player.getUniqueId();
        if (!dashBusy.add(id)) {
            return;
        }
        Vector flat = player.getLocation().getDirection().clone().setY(0);
        if (flat.lengthSquared() < 0.01) {
            flat = player.getLocation().getDirection();
        }
        flat.normalize();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 0.55f, 1.3f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§f✦ Dash"));

        Vector step = flat.clone().multiply(0.95);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 7) {
                    dashBusy.remove(id);
                    cancel();
                    return;
                }
                Vector vel = step.clone();
                vel.setY(tick == 0 ? 0.22 : 0.06);
                player.setVelocity(vel);
                player.setFallDistance(0f);
                Location at = player.getLocation().add(0, 0.4, 0);
                world.spawnParticle(Particle.CLOUD, at, 6, 0.15, 0.1, 0.15, 0.01);
                world.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Spin enemies in a tornado, then fling them out. */
    void castTornado(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!tornadoBusy.add(id)) {
            return;
        }
        double tipDamage = Math.max(55.0, weaponDamage * 1.1 + 20.0);
        player.sendMessage("§f✦ Cyclone §7whips up…");
        CycloneRodTempest.cast(plugin, player, tipDamage, () -> tornadoBusy.remove(id));
    }

    /** Two half-buried colored cubes orbit opposite ways → shrink/spin → big bang. */
    void castPrismBang(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!prismBusy.add(id)) {
            return;
        }
        World world = player.getWorld();
        Location center = player.getLocation().clone();
        center.setY(de.aetherion.items.world.TestArenaService.PLATFORM_Y + 0.02);
        if (!TestArenaGuard.isArena(world)) {
            center.setY(world.getHighestBlockYAt(center) + 0.02);
        }

        org.bukkit.entity.BlockDisplay a = spawnPrism(world, center, Material.LIME_CONCRETE, 2.4f);
        org.bukkit.entity.BlockDisplay b = spawnPrism(world, center, Material.MAGENTA_CONCRETE, 2.4f);
        if (a == null || b == null) {
            if (a != null) {
                a.remove();
            }
            if (b != null) {
                b.remove();
            }
            prismBusy.remove(id);
            return;
        }

        player.sendMessage("§d✦ Twin Prisms §7orbiting…");
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.7f);
        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.55f, 1.4f);
        double boom = Math.max(150.0, weaponDamage * 3.5 + 45.0);

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 70;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !a.isValid() || !b.isValid()) {
                    if (a.isValid()) {
                        a.remove();
                    }
                    if (b.isValid()) {
                        b.remove();
                    }
                    prismBusy.remove(id);
                    cancel();
                    return;
                }
                tick++;
                double progress = tick / (double) duration;
                angle += 0.12 + progress * 0.55;
                double radius = 5.2 - progress * 3.6;
                float scale = (float) (2.4 * (1.0 - progress * 0.72));
                Location pivot = player.isOnline() ? player.getLocation().clone() : center.clone();
                pivot.setY(center.getY());

                placePrism(a, pivot, angle, radius, scale);
                placePrism(b, pivot, angle + Math.PI, radius, scale);

                if (tick % 5 == 0) {
                    world.playSound(pivot, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 0.6f + (float) progress);
                    world.spawnParticle(Particle.END_ROD, pivot.clone().add(0, 0.8, 0), 4, 0.4, 0.3, 0.4, 0.01);
                }

                if (tick < duration) {
                    return;
                }

                Location bang = pivot.clone().add(0, 0.6, 0);
                a.remove();
                b.remove();
                world.spawnParticle(Particle.FLASH, bang, 8, 0.8, 0.5, 0.8, 0);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, bang, 4, 0.9, 0.4, 0.9, 0);
                world.spawnParticle(Particle.END_ROD, bang, 50, 1.2, 0.8, 1.2, 0.08);
                world.spawnParticle(Particle.DUST, bang, 60, 1.5, 0.8, 1.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 2.2f));
                world.playSound(bang, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.55f);
                world.playSound(bang, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.65f);
                world.playSound(bang, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.5f);
                spitDebris(world, bang, STORM_DEBRIS);

                for (Entity entity : world.getNearbyEntities(bang, 8.0, 6.0, 8.0)) {
                    if (!isCombatTarget(entity)) {
                        continue;
                    }
                    LivingEntity living = (LivingEntity) entity;
                    abilityDamage(living, boom, player);
                    Vector away = living.getLocation().toVector().subtract(bang.toVector());
                    if (away.lengthSquared() < 0.01) {
                        away = new Vector(0, 1, 0);
                    } else {
                        away.normalize();
                    }
                    living.setVelocity(away.multiply(2.0).setY(1.1));
                }
                player.sendMessage("§d✦ Big Bang.");
                prismBusy.remove(id);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Big bolt that chains up to 10 enemies. Attack spread ignored. */
    void castCascadeBolt(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!cascadeBusy.add(id)) {
            return;
        }
        double perHit = Math.max(38.0, weaponDamage * 0.95 + 16.0);
        player.sendMessage("§6✦ Cascade Bolt");
        CascadeTorrent.cast(plugin, player, perHit, () -> cascadeBusy.remove(id));
    }

    private static org.bukkit.entity.BlockDisplay spawnPrism(World world, Location at, Material mat, float scale) {
        try {
            return world.spawn(at, org.bukkit.entity.BlockDisplay.class, display -> {
                display.setBlock(mat.createBlockData());
                display.setPersistent(false);
                display.setInterpolationDuration(2);
                display.setTeleportDuration(2);
                applyPrismTransform(display, scale, 0f);
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void placePrism(org.bukkit.entity.BlockDisplay display, Location pivot, double angle, double radius, float scale) {
        Location at = pivot.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        display.teleport(at);
        float yaw = (float) Math.toDegrees(angle) * 2.5f;
        applyPrismTransform(display, scale, yaw);
    }

    private static void applyPrismTransform(org.bukkit.entity.BlockDisplay display, float scale, float yawDeg) {
        // Center cube and sink half underground → pyramid-like silhouette on the pad.
        float s = Math.max(0.35f, scale);
        org.joml.Quaternionf rot = new org.joml.Quaternionf().rotateY((float) Math.toRadians(yawDeg));
        display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(-0.5f * s, -0.5f * s, -0.5f * s),
                rot,
                new org.joml.Vector3f(s, s, s),
                new org.joml.Quaternionf()
        ));
    }

    private static LivingEntity nearestCascade(Player player, Location from, Set<UUID> hit, double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        World world = from.getWorld();
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
            if (!isCombatTarget(entity) || hit.contains(entity.getUniqueId())) {
                continue;
            }
            double d = entity.getLocation().distanceSquared(from);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private static void drawCascadeTrail(World world, Location from, Location to, int hop) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.1) {
            return;
        }
        Vector step = delta.normalize().multiply(0.55);
        Location cursor = from.clone();
        int steps = Math.min(40, (int) (len / 0.55));
        Color color = hop % 2 == 0 ? Color.fromRGB(255, 170, 40) : Color.fromRGB(255, 80, 40);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.8f);
        for (int i = 0; i < steps; i++) {
            cursor.add(step);
            world.spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.FLAME, cursor, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    static boolean isCombatTarget(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        if (entity.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
            return false;
        }
        if (entity.getPersistentDataContainer().has(AetherKeys.QUEST_NPC)
                || entity.getPersistentDataContainer().has(AetherKeys.DUNGEON_NPC)
                || entity.getPersistentDataContainer().has(AetherKeys.SET_MINION, PersistentDataType.BYTE)) {
            return false;
        }
        if (entity.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)
                || entity.getPersistentDataContainer().has(AetherKeys.BOSS_MINION, PersistentDataType.STRING)) {
            return true;
        }
        return entity instanceof Animals || entity instanceof Enemy || entity instanceof Mob;
    }

    private static Location focusAhead(Player player, double dist) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location aim = eye.clone().add(dir.multiply(dist));
        World world = aim.getWorld();
        if (world != null) {
            int y = world.getHighestBlockYAt(aim);
            if (Math.abs(y - aim.getY()) < 8) {
                aim.setY(y + 1.2);
            }
        }
        return aim;
    }

    private static Location rayAim(Player player, double max) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), max);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(world).add(0, 0.2, 0);
        }
        return eye.clone().add(eye.getDirection().normalize().multiply(max));
    }

    private static Location lerp(Location from, Location to, double t) {
        if (from == null) {
            return to.clone();
        }
        if (to == null || from.getWorld() != to.getWorld()) {
            return from.clone();
        }
        return from.clone().add(
                (to.getX() - from.getX()) * t,
                (to.getY() - from.getY()) * t,
                (to.getZ() - from.getZ()) * t
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

    private static void drawBeam(World world, Location from, Location to, Color color, float size) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.1) {
            return;
        }
        Vector step = delta.normalize().multiply(0.45);
        Location cursor = from.clone();
        int steps = Math.min(80, (int) (len / 0.45));
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < steps; i++) {
            cursor.add(step);
            world.spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, cursor, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    private static void drawZone(World world, Location at, Color color, boolean pulse) {
        double r = pulse ? 2.6 : 2.2;
        Particle.DustOptions dust = new Particle.DustOptions(color, pulse ? 1.6f : 1.1f);
        for (int i = 0; i < 18; i++) {
            double a = (Math.PI * 2 * i) / 18.0;
            Location p = at.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
    }

    private static void drawTornado(World world, Location center, double radius, double angle, int tick) {
        for (int layer = 0; layer < 5; layer++) {
            double y = layer * 0.7;
            double r = radius * (0.35 + layer * 0.14);
            for (int i = 0; i < 8; i++) {
                double a = angle + i * (Math.PI / 4) + layer * 0.4;
                Location p = center.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                world.spawnParticle(Particle.CLOUD, p, 1, 0.05, 0.05, 0.05, 0.01);
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.WHITE_SMOKE, p, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }
        if (tick % 5 == 0) {
            world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1.5, 0), 2, 0.8, 0.6, 0.8, 0);
        }
    }

    private void latchAndPull(Player caster, Location focus, Set<UUID> latched, double radius, double strength) {
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(focus, radius, radius, radius)) {
            if (!isCombatTarget(entity) || entity.equals(caster)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            latched.add(living.getUniqueId());
            Vector to = focus.toVector().subtract(living.getLocation().toVector().add(new Vector(0, 0.8, 0)));
            double len = to.length();
            if (len < 0.35) {
                living.setVelocity(new Vector(0, 0.05, 0));
                continue;
            }
            living.setVelocity(to.normalize().multiply(Math.min(1.35, strength + len * 0.035)).setY(
                    Math.max(-0.1, Math.min(0.55, to.getY() * 0.08))
            ));
            living.setFallDistance(0f);
        }
    }

    private void spawnDebris(World world, Location focus, List<FallingBlock> debris, Material[] mats, boolean gravity) {
        if (debris.size() >= 22) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = random.nextDouble() * Math.PI * 2;
        double dist = 5.5 + random.nextDouble() * 5.5;
        Location at = focus.clone().add(Math.cos(yaw) * dist, random.nextDouble(-0.2, 2.8), Math.sin(yaw) * dist);
        Material mat = mats[random.nextInt(mats.length)];
        FallingBlock block = tagDebris(world.spawnFallingBlock(at, mat.createBlockData()));
        block.setGravity(gravity);
        block.setVelocity(new Vector(0, 0, 0));
        debris.add(block);
    }

    private void pullDebris(Location focus, List<FallingBlock> debris, double pull) {
        Iterator<FallingBlock> it = debris.iterator();
        while (it.hasNext()) {
            FallingBlock falling = it.next();
            if (falling == null || !falling.isValid()) {
                it.remove();
                continue;
            }
            Vector to = focus.toVector().subtract(falling.getLocation().toVector());
            double dist = to.length();
            if (dist < 0.65) {
                falling.remove();
                it.remove();
                continue;
            }
            falling.setGravity(false);
            falling.setVelocity(to.normalize().multiply(Math.min(1.2, pull + dist * 0.035)));
        }
    }

    private void spitDebris(World world, Location focus, Material[] mats) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            Material mat = mats[random.nextInt(mats.length)];
            FallingBlock block = tagDebris(world.spawnFallingBlock(focus.clone(), mat.createBlockData()));
            block.setVelocity(new Vector(
                    random.nextDouble(-1.1, 1.1),
                    random.nextDouble(0.55, 1.35),
                    random.nextDouble(-1.1, 1.1)
            ));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.isValid()) {
                        block.remove();
                    }
                }
            }.runTaskLater(plugin, 28L + random.nextInt(12));
        }
    }

    private static FallingBlock tagDebris(FallingBlock block) {
        block.setDropItem(false);
        block.setHurtEntities(false);
        try {
            block.setCancelDrop(true);
        } catch (Throwable ignored) {
        }
        block.setPersistent(false);
        block.getPersistentDataContainer().set(ItemKeys.testDebris(), PersistentDataType.BYTE, (byte) 1);
        return block;
    }

    private static void clearDebris(List<FallingBlock> debris) {
        for (FallingBlock block : debris) {
            if (block != null && block.isValid()) {
                block.remove();
            }
        }
        debris.clear();
    }

    private static void drawRing(World world, Location core, double radius, int tick) {
        int points = 26;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points + tick * 0.14;
            Location p = core.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.08) * 0.35, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SQUID_INK, p, 1, 0, 0, 0, 0);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0, 0, 0);
            }
        }
    }

    static LivingEntity pickStormTarget(Player player, double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        Location origin = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!isCombatTarget(entity)) {
                continue;
            }
            double d = entity.getLocation().distanceSquared(origin);
            Vector to = entity.getLocation().toVector().subtract(player.getEyeLocation().toVector());
            if (to.lengthSquared() < 0.01) {
                continue;
            }
            double dot = to.normalize().dot(player.getEyeLocation().getDirection());
            if (dot < 0.15 && d > 9.0) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private static void strikeQuiet(World world, LivingEntity target, Player caster, double damage) {
        Location at = target.getLocation().add(0, 0.2, 0);
        world.strikeLightningEffect(at);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 28, 0.35, 1.1, 0.35, 0.08);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.4, 0), 1, 0, 0, 0, 0);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.28f, 1.4f);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.18f, 1.65f);
        abilityDamage(target, damage, caster);
        target.setFireTicks(0);
    }

    /** Marks the hit as scripted so BossEngine does not treat it as spam melee. */
    private static void abilityDamage(LivingEntity target, double amount, Player caster) {
        de.aetherion.items.combat.ScriptedHits.run(() -> target.damage(amount, caster));
    }

    // --- restored terminus showcase casts ---

    /** Meteor Mace; the fissure, eruption, meteor, crater and hits live in {@link MeteorMaceCrash}. */
    void castMeteor(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!meteorBusy.add(id)) {
            return;
        }
        double launch = Math.max(24.0, weaponDamage * 0.35 + 8.0);
        double impact = Math.max(175.0, weaponDamage * 4.0 + 60.0);
        double burn = Math.max(10.0, weaponDamage * 0.1);
        player.sendMessage("§c✦ Meteor Mace §7— the ground answers…");
        MeteorMaceCrash.cast(plugin, player, launch, impact, burn, () -> meteorBusy.remove(id));
    }


    /** Cataclysm Rod; bolt flight + Absolute Nova live in {@link CataclysmRodNova}. */
    void castCataclysm(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!cataclysmBusy.add(id)) {
            return;
        }
        double splash = Math.max(80.0, weaponDamage * 2.2 + 30.0);
        CataclysmRodNova.cast(plugin, player, splash, () -> cataclysmBusy.remove(id));
    }


    /** Rune Sigil; azure seal, pillar judgment, heal/damage live in {@link RuneSigilRite}. */
    void castRuneSigil(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!runeSigilBusy.add(id)) {
            return;
        }
        double judgment = Math.max(220.0, weaponDamage * 5.5 + 80.0);
        RuneSigilRite.cast(plugin, player, judgment, () -> runeSigilBusy.remove(id));
    }


    /** World Splitter; reality-rift illusion lives in {@link WorldSplitterRift}. */
    void castWorldSplitter(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!worldSplitterBusy.add(id)) {
            return;
        }
        double splash = Math.max(90.0, weaponDamage * 2.4 + 40.0);
        WorldSplitterRift.cast(plugin, player, splash, () -> worldSplitterBusy.remove(id));
    }


    /** Vesper Bell; basilica, hymn, rapture and shatter live in {@link VesperBellBasilica}. */
    void castVesperBell(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!vesperBusy.add(id)) {
            return;
        }
        double judgment = Math.max(200.0, weaponDamage * 5.0 + 80.0);
        VesperBellBasilica.cast(plugin, player, judgment, () -> vesperBusy.remove(id));
    }


    /** Deepsong Conch; the stone sea, breach and spout live in {@link DeepsongLeviathan}. */
    void castDeepsong(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!deepsongBusy.add(id)) {
            return;
        }
        double impact = Math.max(160.0, weaponDamage * 3.2 + 60.0);
        DeepsongLeviathan.cast(plugin, player, impact, () -> deepsongBusy.remove(id));
    }


    /** Terminus; the edge, the survey, the core sample and the tower live in {@link TerminusEdge}. */
    void castTerminus(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!terminusBusy.add(id)) {
            return;
        }
        double fall = Math.max(320.0, weaponDamage * 6.0 + 100.0);
        TerminusEdge.cast(plugin, player, fall, () -> terminusBusy.remove(id));
    }

}
