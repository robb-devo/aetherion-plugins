package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.music.FightMusic;
import de.aetherion.bossengine.music.NbsSong;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

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
    private static final double SPIN_RADIUS = 5.8;
    private static final double SMASH_RADIUS = 4.4;
    private static final int SMASH_TELL = 8;
    private static final int BEAM_SEEK = 80;
    private static final int BEAM_LOCK = 45;
    private static final int BEAM_FIRE = 14;
    private static final double BEAM_ZONE = 4.2;
    private static final double BEAM_BLAST = 4.2;
    private static final int DEATH_TICKS = 128;

    private static final double MUSIC_RANGE = 48.0;

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
    private FightMusic music;

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
        startMusic();
    }

    void abort() {
        stopMusic();
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
        stopMusic();
        initEnergyBlades(deathFocus);
        return true;
    }

    private void startMusic() {
        stopMusic();
        JavaPlugin plugin = instance.getPlugin();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("pathwarden-music.enabled", true)) {
            return;
        }
        String track = cfg.getString("pathwarden-music.track", "balkan_bump");
        if (track == null || track.isBlank()) {
            track = "balkan_bump";
        }
        String resource = "melodies/" + track.trim().toLowerCase(Locale.ROOT) + ".nbs";
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("[Pathwarden] Music missing: " + resource);
                return;
            }
            NbsSong song = NbsSong.load(in, plugin.getLogger());
            if (song.isEmpty()) {
                plugin.getLogger().warning("[Pathwarden] Music empty: " + resource);
                return;
            }
            music = new FightMusic(song, this::musicAudience, this::musicOrigin);
            music.volume((float) cfg.getDouble("pathwarden-music.volume", 0.85));
            music.loop(cfg.getBoolean("pathwarden-music.loop", true));
            music.followPlayers(cfg.getBoolean("pathwarden-music.follow-players", true));
            music.play();
            plugin.getLogger().info("[Pathwarden] Music: " + resource + " (" + music.size() + " notes, "
                    + song.ticksPerSecond() + " tps)");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Pathwarden] Music load failed", ex);
        }
    }

    private void stopMusic() {
        if (music != null) {
            music.stop();
            music = null;
        }
    }

    private List<Player> musicAudience() {
        List<Player> out = new ArrayList<>();
        Location origin = musicOrigin();
        if (origin == null || origin.getWorld() == null) {
            return out;
        }
        double rangeSq = MUSIC_RANGE * MUSIC_RANGE;
        for (Player player : origin.getWorld().getPlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin) <= rangeSq) {
                out.add(player);
            }
        }
        return out;
    }

    private Location musicOrigin() {
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid()) {
            return entity.getLocation();
        }
        if (deathFocus != null) {
            return deathFocus;
        }
        return instance.getSpawnLocation();
    }

    boolean tick() {
        if (!isPathwarden()) {
            return false;
        }
        if (music != null) {
            music.tick();
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
        beginBeam(entity, idx);
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
            spinYaw += 1.2f + (float) (t * 2.4);
            at.setYaw(spinYaw);
            entity.teleport(at);
            // Outer ring is the real hit edge; inner ring fills toward it as the clock.
            boolean strobe = SPIN_WINDUP - spinPhase < 6;
            if (strobe) {
                ringParticles(at, SPIN_RADIUS, WHITE, WHITE, 40);
            } else if (spinPhase % 2 == 0) {
                ringParticles(at, SPIN_RADIUS, CRIMSON, CRIMSON, 32);
            }
            ringParticles(at, 1.2 + t * (SPIN_RADIUS - 1.2), AZURE, CRIMSON, 18);
            if (spinPhase % 7 == 0) {
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.5f + (float) t * 1.2f);
            }
            if (spinPhase == SPIN_WINDUP) {
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 0.55f);
                world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.7f);
                if (!de.aetherion.core.api.Vfx.burst(at.clone().add(0, 2.5, 0), WHITE, 3.0, 9)) {
                    de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 2.5, 0), Particle.FLASH, 1, Math.max(0, 0), null);
                }
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
                ringParticles(at, SPIN_RADIUS, CRIMSON, AZURE, 28);
            }
            if (active == SPIN_ACTIVE) {
                world.playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.9f, 0.6f);
                if (!de.aetherion.core.api.Vfx.wave(at.clone().add(0, 0.05, 0), WHITE, 4.0, 14)) {
                    de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.3, 0), Particle.CLOUD, 30, Math.max(2.2, 2.2), null);
                }
            }
            if (active % 5 == 0) {
                hitAround(entity, SPIN_RADIUS, 280);
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

    private void beginBeam(LivingEntity entity, int idx) {
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
        shout(switch (idx) {
            case 0 -> "&4&lPathwarden&7 draws a &bjudgment line&7.";
            case 1 -> "&4&lPathwarden&7: &fNone pass unweighed. &8(&bjudgment line&8)";
            default -> "&4&lPathwarden&7: &cThen I judge you all. &8(&bfinal judgment line&8)";
        });
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
            boolean firing = BEAM_LOCK - beamTick < 10;
            Color zone = firing && beamTick % 2 == 0 ? WHITE : mix;
            drawSeekBeam(from, beamFocus.clone().add(0, 0.2, 0), mix);
            drawDangerZone(beamFocus, zone, true);
            if (firing && beamTick % 4 == 0 && !de.aetherion.core.api.Vfx.pillar(beamFocus, WHITE, 0.6, 4.0, 6)) {
                de.aetherion.bossengine.fx.ShapeFx.puff(beamFocus.clone().add(0, 1.5, 0), Particle.END_ROD, 4, Math.max(0.08, 0.08), null);
            } else if (firing && !de.aetherion.core.api.Vfx.active()) {
                de.aetherion.bossengine.fx.ShapeFx.puff(beamFocus.clone().add(0, 1.5, 0), Particle.END_ROD, 4, Math.max(0.08, 0.08), null);
            }
            if (beamTick % 6 == 0) {
                world.playSound(beamFocus, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, (float) (0.7 + t));
            }
            if (beamTick == BEAM_LOCK - 10) {
                world.playSound(beamFocus, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
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
            double wave = BEAM_BLAST + (beamTick / (double) BEAM_FIRE) * 4.5;
            ringParticles(beamFocus, wave, CRIMSON, WHITE, 36);
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
        if (de.aetherion.core.api.Vfx.burst(beamFocus.clone().add(0, 1.0, 0), WHITE, 5.0, 12)) {
            de.aetherion.core.api.Vfx.wave(beamFocus.clone().add(0, 0.05, 0), CRIMSON, BEAM_BLAST, 15);
            de.aetherion.core.api.Vfx.wave(beamFocus.clone().add(0, 0.07, 0), AZURE, BEAM_BLAST * 0.45, 12);
            de.aetherion.core.api.Vfx.pillar(beamFocus, WHITE, 1.2, 9.0, 16);
        } else {
            de.aetherion.bossengine.fx.ShapeFx.puff(beamFocus.clone().add(0, 0.4, 0), Particle.EXPLOSION_EMITTER, 1, Math.max(0, 0), null);
            ringParticles(beamFocus, BEAM_BLAST, CRIMSON, WHITE, 40);
            ringParticles(beamFocus, BEAM_BLAST * 0.45, AZURE, WHITE, 20);
            de.aetherion.bossengine.fx.ShapeFx.puff(beamFocus.clone().add(0, 1.2, 0), Particle.END_ROD, 6, Math.max(0.08, 0.08), null);
        }
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
            if (!de.aetherion.core.api.Vfx.wave(beamFocus.clone().add(0, 0.05, 0), Color.fromRGB(200, 200, 205), 2.0, 12)) {
                de.aetherion.bossengine.fx.ShapeFx.puff(beamFocus.clone().add(0, 0.4, 0), Particle.CLOUD, 18, Math.max(0.45, 0.45), null);
            }
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
            if (deathTicks == 1 && de.aetherion.core.api.Vfx.burst(deathAzureStart, AZURE, 2.6, 10)) {
                de.aetherion.core.api.Vfx.burst(deathCrimsonStart, CRIMSON, 2.6, 10);
            } else if (!de.aetherion.core.api.Vfx.active()) {
                de.aetherion.bossengine.fx.ShapeFx.puff(deathAzureStart, Particle.FLASH, deathTicks == 1 ? 1 : 0, Math.max(0, 0), null);
                de.aetherion.bossengine.fx.ShapeFx.puff(deathCrimsonStart, Particle.FLASH, deathTicks == 1 ? 1 : 0, Math.max(0, 0), null);
            }
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
            if (de.aetherion.core.api.Vfx.active()) {
                if (deathTicks % 3 == 0) {
                    de.aetherion.core.api.Vfx.bolt(bladeAzure, bladeCrimson, WHITE, 4);
                }
            } else {
                de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.ELECTRIC_SPARK, 10, Math.max(0.5, 0.5), null);
            }
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
            if (deathTicks == DEATH_TICKS - 12) {
                pathOpens(world);
            }
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
            if (smashCd > 0 && smashCd <= SMASH_TELL) {
                smashTell(entity);
            }
            if (smashCd <= 0) {
                smashCd = 28;
                hitAround(entity, SMASH_RADIUS, 420);
                smashImpact(entity);
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

    /** Blades lift: crimson edge of the smash, white on the last beat. */
    private void smashTell(LivingEntity entity) {
        World world = entity.getWorld();
        Location at = entity.getLocation();
        boolean last = smashCd <= 2;
        double t = 1.0 - (smashCd - 1) / (double) SMASH_TELL;
        ringParticles(at, SMASH_RADIUS, last ? WHITE : CRIMSON, last ? WHITE : CRIMSON, 26);
        ringParticles(at, 0.8 + t * (SMASH_RADIUS - 0.8), CRIMSON, AZURE, 14);
        if (smashCd == SMASH_TELL) {
            world.playSound(at, Sound.ITEM_ARMOR_EQUIP_IRON, 1.1f, 0.5f);
            world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, 0.6f, 0.55f);
        }
    }

    private void smashImpact(LivingEntity entity) {
        if (de.aetherion.core.api.Vfx.active()) {
            World w = entity.getWorld();
            Location here = entity.getLocation();
            de.aetherion.core.api.Vfx.wave(here.clone().add(0, 0.05, 0), CRIMSON, SMASH_RADIUS, 13);
            de.aetherion.core.api.Vfx.wave(here.clone().add(0, 0.07, 0), AZURE, SMASH_RADIUS * 0.6, 11);
            de.aetherion.core.api.Vfx.burst(here.clone().add(0, 0.8, 0), WHITE, 2.6, 9);
            de.aetherion.core.api.Vfx.chips(here, Material.IRON_BLOCK.createBlockData(), 10, 1.6);
            w.playSound(here, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.6f);
            return;
        }
        World world = entity.getWorld();
        Location at = entity.getLocation();
        for (int i = 0; i < 36; i++) {
            double ang = Math.PI * 2 * i / 36.0;
            Location p = at.clone().add(Math.cos(ang) * SMASH_RADIUS, 0.15, Math.sin(ang) * SMASH_RADIUS);
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.BLOCK, 2, Math.max(0.1, 0.1), Material.IRON_BLOCK.createBlockData());
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, 1, Math.max(0, 0), null);
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.6, 0), Particle.CRIT, 30, Math.max(1.6, 1.6), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.4, 0), Particle.EXPLOSION, 1, Math.max(0, 0), null);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.6f);
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
        if (de.aetherion.core.api.Vfx.orb(a, null, AZURE, 1.1, 6)) {
            de.aetherion.core.api.Vfx.orb(c, null, CRIMSON, 1.1, 6);
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(a, Particle.DUST, 3, Math.max(0.12, 0.12), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(c, Particle.DUST, 3, Math.max(0.12, 0.12), null);
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
        if (tip != null && tip.getWorld() != null && de.aetherion.core.api.Vfx.active()) {
            Vector bladeDir = tipDir.clone().normalize();
            Location bladeButt = tip.clone().subtract(bladeDir.clone().multiply(3.6 + power * 0.45));
            String id = instance.getInstanceId() + (color.getBlue() > color.getRed() ? ":azure" : ":crimson");
            double edge = (0.32 + power * 0.07) * (charged ? 1.35 : 1.0);
            de.aetherion.core.api.Vfx.line(id, bladeButt, tip, color, edge);
            de.aetherion.core.api.Vfx.line(id + ":core", bladeButt, tip, WHITE, edge * 0.38);
            if (charged && instance.getTicksAlive() % 4 == 0) {
                de.aetherion.core.api.Vfx.spark(tip, color, 1.4);
            }
            return;
        }
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
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, charged ? 3 : 2, Math.max(0.03, 0.03), null);
            if (i % 2 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.END_ROD, 1, Math.max(0.01, 0.01), null);
            }
            if (charged && i % 3 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, 1, Math.max(0.05, 0.05), null);
            }
            if (color.getBlue() > color.getRed()) {
                if (i % 2 == 0) {
                    de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.SOUL_FIRE_FLAME, 1, Math.max(0.02, 0.02), null);
                }
            } else if (i % 2 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.FLAME, 1, Math.max(0.02, 0.02), null);
            }
        }
        // Crossguard flare
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0e-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize().multiply(0.55 + power * 0.12);
        de.aetherion.bossengine.fx.ShapeFx.puff(butt.clone().add(side), Particle.DUST, 4, Math.max(0.05, 0.05), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(butt.clone().subtract(side), Particle.DUST, 4, Math.max(0.05, 0.05), null);
        // Tip star
        de.aetherion.bossengine.fx.ShapeFx.puff(tip, Particle.GLOW, charged ? 8 : 3, Math.max(0.08, 0.08), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(tip, Particle.ELECTRIC_SPARK, charged ? 10 : 3, Math.max(0.1, 0.1), null);
        if (charged) {
            de.aetherion.bossengine.fx.ShapeFx.puff(tip, Particle.FLASH, 1, Math.max(0, 0), null);
        }
    }

    private void trailBurst(Location at, Color color) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        if (de.aetherion.core.api.Vfx.spark(at, color, 0.8)) {
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.DUST, 8, Math.max(0.15, 0.15), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.END_ROD, 2, Math.max(0.08, 0.08), null);
    }

    private void guillotineImpact(World world, Location strike) {
        world.playSound(strike, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.35f);
        world.playSound(strike, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.15f, 0.5f);
        world.playSound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.55f);
        world.playSound(strike, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.75f);
        if (de.aetherion.core.api.Vfx.burst(strike, WHITE, 6.0, 14)) {
            de.aetherion.core.api.Vfx.burst(strike.clone().add(0.6, 0.4, 0), AZURE, 4.0, 12);
            de.aetherion.core.api.Vfx.burst(strike.clone().add(-0.6, -0.4, 0), CRIMSON, 4.0, 12);
            de.aetherion.core.api.Vfx.wave(strike.clone().add(0, -1.2, 0), AZURE, 3.4, 14);
            de.aetherion.core.api.Vfx.wave(strike.clone().add(0, -1.15, 0), CRIMSON, 2.6, 12);
            bloodBurst(world, strike);
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.FLASH, 3, Math.max(0.2, 0.2), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.EXPLOSION_EMITTER, 1, Math.max(0, 0), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.DUST, 80, Math.max(0.7, 0.7), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.DUST, 80, Math.max(0.7, 0.7), null);
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

    /** Final beat: the gate he guarded unlatches. */
    private void pathOpens(World world) {
        Location floor = deathFocus.clone().add(0, 0.2, 0);
        world.playSound(floor, Sound.BLOCK_IRON_DOOR_OPEN, 1.4f, 0.5f);
        world.playSound(floor, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.8f);
        world.playSound(floor, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.3f);
        if (de.aetherion.core.api.Vfx.wave(floor, Color.fromRGB(255, 236, 190), 4.2, 18)) {
            de.aetherion.core.api.Vfx.pillar(floor, Color.fromRGB(150, 225, 255), 0.9, 7.0, 50);
            de.aetherion.core.api.Vfx.motes(floor.clone().add(0, 0.6, 0), Color.fromRGB(150, 225, 255), 6, 0.8, new Vector(0, 2.4, 0), 40);
        } else {
            for (int i = 0; i < 48; i++) {
                double ang = Math.PI * 2 * i / 48.0;
                de.aetherion.bossengine.fx.ShapeFx.puff(floor.clone().add(Math.cos(ang) * 3.2, 0, Math.sin(ang) * 3.2), Particle.END_ROD, 0, Math.max(Math.cos(ang) * 0.25, Math.sin(ang) * 0.25), null);
            }
            de.aetherion.bossengine.fx.ShapeFx.puff(floor.clone().add(0, 1.0, 0), Particle.SOUL, 20, Math.max(0.6, 0.6), null);
        }
        shout("&8&o…the iron gives way. &7The path is &fopen&7.");
    }

    private void aftermathStorm(World world, Location strike, double t) {
        if (de.aetherion.core.api.Vfx.active()) {
            if (deathTicks % 8 == 0) {
                bloodBurst(world, strike.clone().add(0, -0.4 + t * 0.2, 0));
            }
            if (deathTicks % 4 == 0) {
                de.aetherion.core.api.Vfx.motes(strike, AZURE, 3, 1.0, new Vector(0, -2.0, 0), 24);
                de.aetherion.core.api.Vfx.motes(strike, CRIMSON, 3, 1.0, new Vector(0, -2.0, 0), 24);
            }
            if (deathTicks % 5 == 0) {
                world.playSound(strike, Sound.BLOCK_LAVA_POP, 0.5f, 0.4f);
            }
            return;
        }
        bloodBurst(world, strike.clone().add(0, -0.4 + t * 0.2, 0));
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.DUST, 24, Math.max(1.1, 1.1), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.DUST, 24, Math.max(1.1, 1.1), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.SOUL, 6, Math.max(0.7, 0.7), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(strike, Particle.DAMAGE_INDICATOR, 12, Math.max(0.8, 0.8), null);
        if (deathTicks % 5 == 0) {
            world.playSound(strike, Sound.BLOCK_LAVA_POP, 0.5f, 0.4f);
        }
        // Falling energy shreds
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(deathTicks * 18 + i * 60);
            double r = 1.2 + t * 2.5;
            Location p = strike.clone().add(Math.cos(ang) * r, 1.5 - t * 2.2, Math.sin(ang) * r);
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, 2, Math.max(0.05, 0.05), null);
        }
    }

    private void drawDangerZone(Location center, Color color, boolean pulsing) {
        if (de.aetherion.bossengine.fx.ShapeFx.ring(center.clone().add(0, 0.08, 0), color, pulsing ? BEAM_ZONE * (1.0 + 0.06 * Math.sin(beamTick * 0.5)) : BEAM_ZONE)) {
            de.aetherion.bossengine.fx.ShapeFx.ring(center.clone().add(0, 0.06, 0), color, BEAM_ZONE * 0.5);
            return;
        }
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
        if (de.aetherion.core.api.Vfx.line(instance.getInstanceId() + ":seek", from, to, color, 0.32)) {
            de.aetherion.bossengine.fx.ShapeFx.ring(to.clone().add(0, 0.1, 0), color, 0.9);
            return;
        }
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = 26;
        for (int i = 0; i <= steps; i++) {
            Location p = from.clone().add(delta.clone().multiply(i / (double) steps));
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, 2, Math.max(0.04, 0.04), null);
            if (i % 2 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.END_ROD, 1, Math.max(0.01, 0.01), null);
            }
            if (i % 4 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.ELECTRIC_SPARK, 1, Math.max(0.02, 0.02), null);
            }
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(to.clone().add(0, 0.15, 0), Particle.DUST, 14, Math.max(0.35, 0.35), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(to.clone().add(0, 0.25, 0), Particle.GLOW, 6, Math.max(0.2, 0.2), null);
    }

    private void drawFireBeam(Location from, Location to) {
        if (de.aetherion.core.api.Vfx.beam(from, to, CRIMSON, 1.0, 10)) {
            de.aetherion.core.api.Vfx.beam(from, to, Color.fromRGB(255, 200, 120), 0.35, 10);
            de.aetherion.core.api.Vfx.burst(to.clone().add(0, 0.5, 0), Color.fromRGB(255, 140, 60), 3.0, 11);
            de.aetherion.core.api.Vfx.wave(to.clone().add(0, 0.05, 0), CRIMSON, 3.0, 13);
            return;
        }
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= 32; i++) {
            Location p = from.clone().add(delta.clone().multiply(i / 32.0));
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.DUST, 5, Math.max(0.1, 0.1), null);
            de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.FLAME, 2, Math.max(0.06, 0.06), null);
            if (i % 3 == 0) {
                de.aetherion.bossengine.fx.ShapeFx.puff(p, Particle.LAVA, 1, Math.max(0, 0), null);
            }
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(to.clone().add(0, 0.5, 0), Particle.FLASH, 2, Math.max(0, 0), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(to.clone().add(0, 0.3, 0), Particle.EXPLOSION, 2, Math.max(0.2, 0.2), null);
    }

    private void tornado(Location at) {
        if (de.aetherion.core.api.Vfx.active() && at.getWorld() != null) {
            if (instance.getTicksAlive() % 20 == 0) {
                de.aetherion.core.api.Vfx.spiral(at.clone().add(0, 0.2, 0), AZURE, 2.6, 8.4, 24, 2);
                de.aetherion.core.api.Vfx.spiral(at.clone().add(0, 0.2, 0), CRIMSON, 3.8, 8.4, 24, 2);
            }
            de.aetherion.bossengine.fx.ShapeFx.ring(at.clone().add(0, 0.15, 0), WHITE, 1.6);
            return;
        }
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
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.15, 0), Particle.CLOUD, 6, Math.max(1.4, 1.4), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 1.2, 0), Particle.SWEEP_ATTACK, 2, Math.max(1.8, 1.8), null);
    }

    private void ringParticles(Location at, double radius, Color a, Color b, int points) {
        if (de.aetherion.bossengine.fx.ShapeFx.ring(at.clone().add(0, 0.2, 0), a, radius)) {
            if (!a.equals(b)) {
                de.aetherion.bossengine.fx.ShapeFx.ring(at.clone().add(0, 0.18, 0), b, Math.max(0.3, radius - 0.2));
            }
            return;
        }
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
        if (de.aetherion.core.api.Vfx.burst(at, CRIMSON, 2.4, 10)) {
            de.aetherion.core.api.Vfx.chips(at, Material.REDSTONE_BLOCK.createBlockData(), 7, 1.5);
            de.aetherion.core.api.Vfx.chips(at, Material.RED_CONCRETE.createBlockData(), 4, 1.2);
            world.playSound(at, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.5f);
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.DUST, 90, Math.max(0.9, 0.9), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.DAMAGE_INDICATOR, 40, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.BLOCK, 70, Math.max(0.7, 0.7), Material.REDSTONE_BLOCK.createBlockData());
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.BLOCK, 35, Math.max(0.5, 0.5), Material.RED_CONCRETE.createBlockData());
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
