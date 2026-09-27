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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

    private final JavaPlugin plugin;
    private final Set<UUID> gravityBusy = new HashSet<>();
    private final Set<UUID> stormBusy = new HashSet<>();
    private final Set<UUID> beamBusy = new HashSet<>();
    private final Set<UUID> dashBusy = new HashSet<>();
    private final Set<UUID> tornadoBusy = new HashSet<>();
    private final Set<UUID> prismBusy = new HashSet<>();
    private final Set<UUID> cascadeBusy = new HashSet<>();
    private final Set<UUID> meteorBusy = new HashSet<>();
    private final Set<UUID> cataclysmBusy = new HashSet<>();
    private final Set<UUID> runeSigilBusy = new HashSet<>();
    private final Set<UUID> worldSplitterBusy = new HashSet<>();
    private final Set<UUID> vesperBusy = new HashSet<>();
    private final Set<UUID> deepsongBusy = new HashSet<>();
    private final Set<UUID> terminusBusy = new HashSet<>();

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
        HorizonFx horizon = new HorizonFx(world, focus, player.getEyeLocation().getDirection());

        new BukkitRunnable() {
            int tick = 0;
            final int chargeTicks = 55;
            final int collapseTicks = 10;

            @Override
            public void run() {
                if (!player.isOnline() || world != focus.getWorld()) {
                    clearDebris(debris);
                    horizon.remove();
                    gravityBusy.remove(id);
                    cancel();
                    return;
                }
                tick++;
                double progress = tick / (double) chargeTicks;

                if (tick <= chargeTicks) {
                    double collapse = Math.max(0.0, (tick - (chargeTicks - collapseTicks)) / (double) collapseTicks);
                    horizon.pose(tick, progress, collapse);
                    horizon.inflow(tick, progress);
                    if (tick % 2 == 0) {
                        horizon.tethers(player, tick);
                    }
                    if (tick % 7 == 0) {
                        if (collapse <= 0.0) {
                            world.playSound(focus, Sound.BLOCK_PORTAL_AMBIENT, 0.45f, 0.5f + (float) progress * 0.6f);
                        }
                        spawnDebris(world, focus, debris, VOID_DEBRIS, false);
                    }
                    if (tick == chargeTicks - collapseTicks) {
                        world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
                        world.playSound(focus, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 0.5f);
                    }
                    pullDebris(focus, debris, 0.16 + progress * 0.28);
                    latchAndPull(player, focus, latched, 11.5, 0.14 + progress * 0.26);
                    return;
                }

                clearDebris(debris);
                spitDebris(world, focus, VOID_DEBRIS);
                horizon.detonate();
                world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.FLASH, focus, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.SONIC_BOOM, focus, 1, 0, 0, 0, 0);
                world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.55f);
                world.playSound(focus, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.55f, 0.7f);
                world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 0.45f);
                world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.7f);
                new BukkitRunnable() {
                    int after = 0;

                    @Override
                    public void run() {
                        after++;
                        horizon.aftermath(after);
                        if (after >= 14) {
                            horizon.remove();
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L);

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

    /** Stormcaller Maul; the cloud, strokes, hits and scars live in {@link StormcallerTempest}. */
    void castStorm(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!stormBusy.add(id)) {
            return;
        }
        double strikeDamage = Math.max(42.0, weaponDamage * 0.85 + 18.0);
        player.sendMessage("§e✦ Stormcaller §7— thunder rolls for §f6s§7.");
        StormcallerTempest.cast(plugin, player, strikeDamage, () -> stormBusy.remove(id));
    }

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

    /** Resonance Scythe; the wave, its hits and its visuals live in {@link ResonanceScytheWave}. */
    void castSonic(Player player, double weaponDamage) {
        ResonanceScytheWave.cast(plugin, player, weaponDamage);
    }

    /** Judgment Staff; the seal, swords, pillar, hits and visuals live in {@link JudgmentVerdict}. */
    void castJudgmentBeam(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!beamBusy.add(id)) {
            return;
        }
        double boom = Math.max(140.0, weaponDamage * 3.2 + 40.0);
        player.sendMessage("§6✦ Judgment §7— the seal is drawn…");
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

    /** Cyclone Rod; the funnel, orbit, fling and visuals live in {@link CycloneRodTempest}. */
    void castTornado(Player player, double weaponDamage) {
        UUID id = player.getUniqueId();
        if (!tornadoBusy.add(id)) {
            return;
        }
        double tipDamage = Math.max(55.0, weaponDamage * 1.1 + 20.0);
        player.sendMessage("§f✦ Cyclone §7— the air starts to turn…");
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

    /** Big bolt that chains up to 10 enemies. Attack spread ignored. Hops and visuals live in {@link CascadeTorrent}. */
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

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
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

    /** Marks the hit as scripted so BossEngine does not treat it as spam melee. */
    private static void abilityDamage(LivingEntity target, double amount, Player caster) {
        de.aetherion.items.combat.ScriptedHits.run(() -> target.damage(amount, caster));
    }

    /**
     * Event Horizon visuals only; pull and damage stay in {@link #castGravwell}.
     * A black shell core, a fast hot inner disk and a slower glass outer disk tilted toward the caster,
     * a lensed halo, inflow arms and tethers, then collapse, white inversion, shockwave and polar jets.
     */
    private static final class HorizonFx {

        private static final int INNER = 10;
        private static final int OUTER = 18;
        private static final Color STREAM_OUT = Color.fromRGB(70, 20, 130);
        private static final Color STREAM_IN = Color.fromRGB(235, 200, 255);
        private static final Color VOID_EDGE = Color.fromRGB(35, 5, 60);
        private static final Color HALO = Color.fromRGB(245, 225, 255);
        private static final Color JET = Color.fromRGB(215, 190, 255);
        private static final Color WHITE = Color.fromRGB(255, 255, 255);
        private static final Quaternionf[] SHELL = {
                new Quaternionf(),
                new Quaternionf().rotateY((float) Math.toRadians(45.0)).rotateX((float) Math.toRadians(35.26)),
                new Quaternionf().rotateX((float) Math.toRadians(45.0)).rotateZ((float) Math.toRadians(45.0))
        };

        private final World world;
        private final Location core;
        private final Vector normal;
        private final Vector u;
        private final Vector v;
        private final Vector side;
        private final List<BlockDisplay> shell = new ArrayList<>();
        private final List<BlockDisplay> inner = new ArrayList<>();
        private final List<BlockDisplay> outer = new ArrayList<>();
        private double innerSpin;
        private double outerSpin;
        private double coreSpin;
        private float coreScale = 0.35f;
        private double innerRadius = 0.55;
        private double outerRadius = 1.0;

        HorizonFx(World world, Location core, Vector view) {
            this.world = world;
            this.core = core.clone();
            Vector flat = view.clone().setY(0);
            if (flat.lengthSquared() < 0.01) {
                flat = new Vector(0, 0, 1);
            }
            flat.normalize();
            side = new Vector(-flat.getZ(), 0, flat.getX());
            double tilt = Math.toRadians(20.0);
            normal = new Vector(0, 1, 0).multiply(Math.cos(tilt))
                    .subtract(flat.clone().multiply(Math.sin(tilt)))
                    .normalize();
            u = side.clone();
            v = normal.getCrossProduct(u).normalize();
            for (int i = 0; i < SHELL.length; i++) {
                add(shell, Material.BLACK_CONCRETE, 0, true);
            }
            for (int i = 0; i < INNER; i++) {
                add(inner, Material.PEARLESCENT_FROGLIGHT, 15, false);
            }
            for (int i = 0; i < OUTER; i++) {
                add(outer, i % 2 == 0 ? Material.PURPLE_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS, 15, false);
            }
        }

        /** Grows the silhouette with the charge; {@code collapse} 0→1 swallows the disk into the core. */
        void pose(int tick, double progress, double collapse) {
            double grow = progress * progress * (3.0 - 2.0 * progress);
            double squeeze = 1.0 - 0.75 * collapse;
            coreScale = (float) ((0.35 + grow * 1.05) * (1.0 - 0.65 * collapse) * (1.0 + 0.03 * Math.sin(tick * 0.7)));
            float innerWide = (float) ((0.2 + grow * 0.17) * squeeze + 0.04);
            float outerWide = (float) ((0.3 + grow * 0.5) * squeeze + 0.05);
            innerRadius = (0.55 + grow * 0.9) * squeeze;
            outerRadius = innerRadius + innerWide / 2.0 + outerWide / 2.0 + 0.08;
            coreSpin += 0.05;
            innerSpin += 0.07 + progress * 0.07;
            outerSpin += 0.035 + progress * 0.04;
            for (int i = 0; i < shell.size(); i++) {
                apply(shell.get(i), shellTransform(i, coreScale), 2);
            }
            float innerLen = (float) (Math.PI * 2 * innerRadius / INNER * 1.05);
            for (int i = 0; i < inner.size(); i++) {
                apply(inner.get(i), segment(innerSpin + Math.PI * 2 * i / INNER, innerRadius, innerLen, innerWide, 0.05f), 2);
            }
            float outerLen = (float) (Math.PI * 2 * outerRadius / OUTER * 1.02);
            for (int i = 0; i < outer.size(); i++) {
                apply(outer.get(i), segment(outerSpin + Math.PI * 2 * i / OUTER, outerRadius, outerLen, outerWide, 0.04f), 2);
            }
        }

        /** Spiral arms flowing into the disk, the closing infall ring, portal streaks, and the lensed halo. */
        void inflow(int tick, double progress) {
            double reach = outerRadius + 3.0 + progress * 1.5;
            int arms = 3;
            int beads = 7;
            for (int a = 0; a < arms; a++) {
                for (int k = 0; k < beads; k++) {
                    double s = ((k + 0.5) / beads + tick * 0.03) % 1.0;
                    double r = reach - (reach - innerRadius) * s;
                    double ang = outerSpin * 1.4 + a * (Math.PI * 2 / arms) + s * 2.2;
                    world.spawnParticle(Particle.DUST, onDisk(ang, r), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(mix(STREAM_OUT, STREAM_IN, s), (float) (1.25 - s * 0.5)));
                }
            }
            if (tick % 3 == 0) {
                double r = 2.2 + (1.0 - progress) * 7.5;
                Particle.DustOptions edge = new Particle.DustOptions(VOID_EDGE, 1.5f);
                for (int i = 0; i < 28; i++) {
                    world.spawnParticle(Particle.DUST, onDisk(Math.PI * 2 * i / 28 - tick * 0.05, r), 1, 0, 0, 0, 0, edge);
                }
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble(Math.PI * 2);
                double dist = 5.0 + random.nextDouble(4.5);
                Vector off = u.clone().multiply(Math.cos(ang) * dist)
                        .add(v.clone().multiply(Math.sin(ang) * dist))
                        .add(normal.clone().multiply(random.nextDouble(-0.6, 0.6)));
                world.spawnParticle(Particle.PORTAL, core, 0, off.getX(), off.getY(), off.getZ(), 1.0);
            }
            if (tick % 2 == 0) {
                double halo = coreScale * 0.8 + 0.14;
                Particle.DustOptions ring = new Particle.DustOptions(HALO, 0.65f);
                for (int i = 0; i < 22; i++) {
                    double ang = Math.PI * 2 * i / 22;
                    Location at = core.clone()
                            .add(side.clone().multiply(Math.cos(ang) * halo))
                            .add(0, Math.sin(ang) * halo, 0);
                    world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, ring);
                }
            }
        }

        /** Beads flowing from every pulled target into the core. */
        void tethers(Player caster, int tick) {
            for (Entity entity : world.getNearbyEntities(core, 11.5, 11.5, 11.5)) {
                if (!isCombatTarget(entity) || entity.equals(caster)) {
                    continue;
                }
                Location from = entity.getLocation().add(0, entity.getHeight() * 0.55, 0);
                Vector span = core.toVector().subtract(from.toVector());
                if (span.lengthSquared() < 1.0) {
                    continue;
                }
                for (int k = 0; k < 5; k++) {
                    double t = (k / 5.0 + tick * 0.045) % 1.0;
                    world.spawnParticle(Particle.DUST, from.clone().add(span.clone().multiply(t)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(mix(STREAM_OUT, STREAM_IN, t), (float) (1.1 - t * 0.4)));
                }
            }
        }

        /** The inversion beat: the black core flashes white and swells, the disk shatters outward. */
        void detonate() {
            for (int i = 0; i < shell.size(); i++) {
                BlockDisplay display = shell.get(i);
                if (display == null || !display.isValid()) {
                    continue;
                }
                display.setBlock(Material.WHITE_CONCRETE.createBlockData());
                display.setBrightness(new Display.Brightness(15, 15));
                display.setGlowColorOverride(Color.fromRGB(230, 205, 255));
                apply(display, shellTransform(i, 2.6f), 3);
            }
            fling(inner, innerSpin, INNER, innerRadius * 3.5, 0.05f);
            fling(outer, outerSpin, OUTER, outerRadius * 3.0, 0.04f);
        }

        /** Shockwave in the disk plane, a ground ring, and two polar jets. {@code t} counts from 1. */
        void aftermath(int t) {
            double k = Math.min(1.0, t / 12.0);
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            if (t <= 12) {
                double r = 0.8 + ease * 9.5;
                Particle.DustOptions wave = new Particle.DustOptions(mix(WHITE, STREAM_OUT, k), (float) (1.7 - k * 0.7));
                int points = 44;
                for (int i = 0; i < points; i++) {
                    world.spawnParticle(Particle.DUST, onDisk(Math.PI * 2 * i / points, r), 1, 0, 0, 0, 0, wave);
                }
            }
            double ground = world.getHighestBlockYAt(core) + 1.1;
            if (t <= 10 && t % 2 == 0 && core.getY() > ground && core.getY() - ground < 6.0) {
                double r = 0.6 + ease * 8.0;
                Particle.DustOptions dust = new Particle.DustOptions(mix(HALO, STREAM_OUT, k), 1.3f);
                int points = 40;
                for (int i = 0; i < points; i++) {
                    double ang = Math.PI * 2 * i / points;
                    Location at = core.clone();
                    at.setY(ground);
                    world.spawnParticle(Particle.DUST, at.add(Math.cos(ang) * r, 0, Math.sin(ang) * r), 1, 0, 0, 0, 0, dust);
                }
            }
            if (t <= 7) {
                double len = 1.0 + t * 0.9;
                Particle.DustOptions beam = new Particle.DustOptions(JET, 1.2f);
                Particle.DustOptions hot = new Particle.DustOptions(WHITE, 0.6f);
                for (int sign = -1; sign <= 1; sign += 2) {
                    Vector axis = normal.clone().multiply(sign);
                    int step = 0;
                    for (double d = 0.5; d <= len; d += 0.35, step++) {
                        Location at = core.clone().add(axis.clone().multiply(d));
                        world.spawnParticle(Particle.DUST, at, 1, 0.03, 0.03, 0.03, 0, beam);
                        if (step % 2 == 0) {
                            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, hot);
                        }
                    }
                    Location tip = core.clone().add(axis.clone().multiply(len));
                    world.spawnParticle(Particle.END_ROD, tip, 0, axis.getX(), axis.getY(), axis.getZ(), 0.25);
                }
            }
            if (t == 1) {
                for (int i = 0; i < 24; i++) {
                    double ang = Math.PI * 2 * i / 24;
                    Vector out = u.clone().multiply(Math.cos(ang)).add(v.clone().multiply(Math.sin(ang)));
                    world.spawnParticle(Particle.END_ROD, core, 0, out.getX(), out.getY(), out.getZ(), 0.55);
                }
            }
            if (t == 3) {
                for (int i = 0; i < shell.size(); i++) {
                    apply(shell.get(i), shellTransform(i, 0.01f), 3);
                }
            }
            if (t == 6) {
                removeAll(inner);
                removeAll(outer);
            }
        }

        void remove() {
            removeAll(shell);
            removeAll(inner);
            removeAll(outer);
        }

        private void fling(List<BlockDisplay> ring, double spin, int count, double radius, float thick) {
            float length = (float) (Math.PI * 2 * radius / count * 0.35);
            for (int i = 0; i < ring.size(); i++) {
                apply(ring.get(i), segment(spin + Math.PI * 2 * i / count, radius, length, 0.3f, thick), 5);
            }
        }

        private Location onDisk(double angle, double radius) {
            return core.clone()
                    .add(u.clone().multiply(Math.cos(angle) * radius))
                    .add(v.clone().multiply(Math.sin(angle) * radius));
        }

        private Transformation shellTransform(int index, float scale) {
            Quaternionf rot = new Quaternionf()
                    .rotateAxis((float) coreSpin, (float) normal.getX(), (float) normal.getY(), (float) normal.getZ())
                    .mul(SHELL[index]);
            Vector3f half = rot.transform(new Vector3f(scale / 2f, scale / 2f, scale / 2f));
            return new Transformation(half.negate(), rot, new Vector3f(scale, scale, scale), new Quaternionf());
        }

        /** One flat disk tile, centered on the ring at {@code angle}, long side along the orbit. */
        private Transformation segment(double angle, double radius, float length, float width, float thick) {
            Vector radial = u.clone().multiply(Math.cos(angle)).add(v.clone().multiply(Math.sin(angle)));
            Vector tangent = u.clone().multiply(-Math.sin(angle)).add(v.clone().multiply(Math.cos(angle)));
            Vector3f x = new Vector3f((float) tangent.getX(), (float) tangent.getY(), (float) tangent.getZ());
            Vector3f y = new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
            Vector3f z = new Vector3f(x).cross(y);
            Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
            Vector3f half = rot.transform(new Vector3f(length / 2f, thick / 2f, width / 2f));
            Vector3f center = new Vector3f(
                    (float) (radial.getX() * radius),
                    (float) (radial.getY() * radius),
                    (float) (radial.getZ() * radius)
            );
            return new Transformation(center.sub(half), rot, new Vector3f(length, thick, width), new Quaternionf());
        }

        private void add(List<BlockDisplay> into, Material material, int light, boolean horizon) {
            try {
                into.add(world.spawn(core, BlockDisplay.class, spawned -> {
                    spawned.setBlock(material.createBlockData());
                    spawned.setPersistent(false);
                    spawned.setBrightness(new Display.Brightness(light, light));
                    spawned.setInterpolationDuration(2);
                    spawned.setTeleportDuration(2);
                    spawned.setTransformation(new Transformation(
                            new Vector3f(),
                            new Quaternionf(),
                            new Vector3f(0.01f, 0.01f, 0.01f),
                            new Quaternionf()
                    ));
                    if (horizon) {
                        spawned.setGlowing(true);
                        spawned.setGlowColorOverride(Color.fromRGB(80, 10, 140));
                    }
                }));
            } catch (Throwable ignored) {
            }
        }

        private static void apply(BlockDisplay display, Transformation transformation, int ticks) {
            if (display == null || !display.isValid()) {
                return;
            }
            display.setInterpolationDuration(ticks);
            display.setInterpolationDelay(0);
            display.setTransformation(transformation);
        }

        private static void removeAll(List<BlockDisplay> displays) {
            for (BlockDisplay display : displays) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            displays.clear();
        }
    }
}
