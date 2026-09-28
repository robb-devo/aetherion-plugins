package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.core.SkyControl;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HELIOS DIES. The death of a star, staged in seven beats (ticks from the killing blow):
 *
 * <pre>
 *     0  LAST BREATH   every light in the arena goes out; the stars fade; the heart stutters, slower
 *    70  COLLAPSE      rings, mask, disk and rubble spiral into the heart; light streams in from the rim
 *   150  THE POINT     one white point. Absolute silence. One heartbeat.
 *   190  SWELL         four nested shells turning against each other, sixteen corona rays, the floor
 *                      turning to light ring by ring outward, the rumble climbing
 *   250  DETONATION    night becomes noon in one frame; flat shock rings on three levels and two upright
 *                      ones, a sphere of light plates, ejecta; everyone is lifted and floats
 *   265  NEBULA        coloured veils drift outward; a pulsar sweeps its two beams; dawn
 *   300  GIVING BACK   the broken arena rebuilds from light, the floor's glow draws back inward
 * </pre>
 *
 * Every piece is posed at the same cadence it is pushed (no stretched interpolation), so nothing
 * snaps. Displays are created ahead of the detonation over several ticks to respect the spawn budget.
 */
final class Supernova {

    static final int END = 520;
    private static final Color WHITE = Color.fromRGB(255, 250, 235);
    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final Material[] SHELLS = {
            Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.RED_STAINED_GLASS
    };
    private static final float[] SHELL_R = {2.2f, 4.2f, 6.4f, 8.8f};
    private static final Material[] NEBULA = {
            Material.MAGENTA_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS, Material.PINK_STAINED_GLASS
    };

    private final HeliosScript h;
    private final HeliosEncounter enc;
    private final HeliosStage stage;
    private final HeliosStage.Group g;
    private final Vector3f heart = new Vector3f(0f, 10f, 0f);

    private final List<BlockDisplay> inflow = new ArrayList<>();
    private BlockDisplay point;
    private final BlockDisplay[][] shells = new BlockDisplay[4][3];
    private final List<BlockDisplay> rays = new ArrayList<>();
    private final List<Shapes.Ring> waves = new ArrayList<>();
    private final List<Quaternionf> waveOrient = new ArrayList<>();
    private final List<Float> waveY = new ArrayList<>();
    private final List<BlockDisplay> sphere = new ArrayList<>();
    private final List<Vector3f> sphereDir = new ArrayList<>();
    private final List<BlockDisplay> ejecta = new ArrayList<>();
    private final List<Vector3f> ejectaDir = new ArrayList<>();
    private final List<BlockDisplay> nebula = new ArrayList<>();
    private final List<Vector3f> nebulaAt = new ArrayList<>();
    private final BlockDisplay[] pulsar = new BlockDisplay[3];

    private final List<ArenaLayout.Cell> lights = new ArrayList<>();
    private List<ArenaLayout.Cell> glow;
    private int glowCursor;
    private int glowBack;
    private List<ArenaLayout.Cell> rebuild;
    private int rebuildCursor;
    private float shellScale;
    private float raySpin;

    Supernova(HeliosScript h) {
        this.h = h;
        this.enc = h.encounter();
        this.stage = enc.stage();
        this.g = stage.group();
    }

    /** @return true when the whole death has played out */
    boolean tick(int t) {
        Score score = enc.score();
        if (t == 0) {
            lastBreath();
        }
        if (t < 70) {
            if (t % Math.max(8, 20 - t / 6) == 0) {
                score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1.3f, 0.9f - t / 140f, true);
            }
            h.rig().center.lerp(heart, 0.05f);
            if (t == 40) {
                score.silence(30);
            }
        }
        if (t == 70) {
            spawnInflow();
            score.unmute();
            score.sweep(null, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.4f, 2f, 0.5f, 78, 4);
            score.sweep(null, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.4f, 1.1f, 1.4f, 0.5f, 78, 8);
        }
        if (t >= 70 && t < 150) {
            collapse(HMath.window(t, 70, 148), t);
        }
        if (t == 150) {
            thePoint();
        }
        if (t == 172) {
            score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f, true);
            stage.push(point, HeliosStage.cube(heart, 0.7f, new Quaternionf()), 2);
        }
        if (t == 174) {
            stage.push(point, HeliosStage.cube(heart, 0.3f, new Quaternionf()), 4);
        }
        // Spread the detonation's displays over several ticks (spawn budget).
        if (t == 185) {
            spawnShells();
        }
        if (t == 188) {
            spawnRays();
        }
        if (t == 192) {
            spawnWaves();
        }
        if (t == 196) {
            spawnSphere();
        }
        if (t == 200) {
            spawnNebula();
        }
        if (t >= 190 && t < 250) {
            swell(HMath.window(t, 190, 250), t);
        }
        if (t == 250) {
            detonate();
        }
        if (t > 250 && t < 330) {
            expand(t - 250);
        }
        if (t >= 265 && t < END) {
            afterglow(t);
        }
        if (t == 300) {
            beginRebuild();
        }
        if (t > 300) {
            tickRebuild();
        }
        if (t == 420) {
            enc.sky().to(1000f, 30f);
            score.chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, Score.semi(0), Score.semi(4), Score.semi(7), Score.semi(12));
            score.play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
        }
        return t >= END && rebuild != null && rebuildCursor >= rebuild.size() && glowBack >= (glow == null ? 0 : glow.size());
    }

    /* ================================================================== 0: last breath */

    private void lastBreath() {
        enc.sky().to(SkyControl.NIGHT, 120f);
        enc.sky().weather(WeatherType.DOWNFALL);
        enc.sky().darken(true);
        enc.sky().fog(true);
        h.rig().spin(0.15f);
        h.rig().crack(1f);
        // Every light in the arena dies.
        Arena arena = enc.arena();
        for (ArenaLayout.Cell c : ArenaLayout.cells()) {
            if (c.layer() < 0 && c.data().getMaterial() == Material.LIGHT) {
                arena.swap(c.dx(), c.dy(), c.dz(), Material.AIR);
                lights.add(c);
            }
        }
        enc.score().play(Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.5f, true);
        enc.score().play(Sound.BLOCK_GLASS_BREAK, 1f, 0.5f, true);
    }

    /* ================================================================== 70: collapse */

    private void spawnInflow() {
        int n = stage.budget().scaled(16, 8);
        for (int i = 0; i < n; i++) {
            BlockDisplay d = g.block(Material.WHITE_CONCRETE.createBlockData(), WHITE, 15, true);
            stage.push(d, HeliosStage.gone(heart), 0);
            inflow.add(d);
        }
    }

    private void collapse(float f, int t) {
        float e = HMath.inCubic(f);
        h.rig().collapse(e);
        h.collapseWorld(e);
        // Light streams in from the rim: rays that shorten toward the heart.
        if (t % 2 == 0) {
            for (int i = 0; i < inflow.size(); i++) {
                float a = i * HMath.TAU / inflow.size() + f * 1.5f;
                float y = (HMath.hash(i, 5) - 0.5f) * 20f;
                float outer = 45f * (1f - e) + 2f;
                Vector3f from = new Vector3f(heart).add(HMath.ring(outer, a, y * (1f - e)));
                Vector3f to = new Vector3f(from).lerp(heart, 0.35f + 0.6f * e);
                stage.push(inflow.get(i), HeliosStage.beam(from, to, 0.08f + 0.1f * e), 2);
            }
        }
        if (t % 10 == 0) {
            enc.camera().shakeAll(6 + (int) (10 * f), 3);
        }
    }

    /* ================================================================== 150: the point */

    private void thePoint() {
        enc.score().silence(40);
        h.rig().visible(false);
        h.releaseWorld();
        for (BlockDisplay d : inflow) {
            stage.push(d, HeliosStage.gone(heart), 2);
        }
        point = g.block(Material.WHITE_CONCRETE.createBlockData(), WHITE, 15, true);
        stage.push(point, HeliosStage.cube(heart, 0.3f, new Quaternionf()), 0);
    }

    /* ================================================================== 190: swell */

    private void spawnShells() {
        for (int s = 0; s < shells.length; s++) {
            for (int i = 0; i < 3; i++) {
                BlockDisplay d = g.block(SHELLS[s].createBlockData(), s == 0 ? WHITE : null, 15, true);
                stage.push(d, HeliosStage.gone(heart), 0);
                shells[s][i] = d;
            }
        }
    }

    private void spawnRays() {
        int n = stage.budget().scaled(16, 10);
        for (int i = 0; i < n; i++) {
            BlockDisplay d = g.block((i % 2 == 0 ? Material.WHITE_CONCRETE : Material.YELLOW_STAINED_GLASS).createBlockData(),
                    i % 2 == 0 ? GOLD : null, 15, true);
            stage.push(d, HeliosStage.gone(heart), 0);
            rays.add(d);
        }
    }

    private void swell(float f, int t) {
        float e = HMath.inExpo(f);
        shellScale = 0.15f + e;
        raySpin += 0.01f + 0.05f * f;
        if (t % 2 == 0) {
            poseShells(2, t);
            poseRays(2, 2f + 14f * e, 0.12f + 0.25f * e);
            stage.push(point, HeliosStage.cube(heart, 0.3f + 1.2f * e, new Quaternionf().rotateY(t * 0.2f)), 2);
        }
        glowFloor(f);
        enc.camera().vignetteAll(0.15f + 0.6f * f);
        if (t % 6 == 0) {
            enc.camera().shakeAll(6, 2);
        }
        if (t == 190) {
            enc.score().unmute();
            enc.score().play(Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.5f, true);
            enc.score().sweep(null, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.4f, 0.5f, 2f, 60, 5);
            enc.score().sweep(null, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.4f, 0.5f, 2f, 60, 4);
        }
        // A rising chord every eight ticks: the star's last phrase.
        if ((t - 190) % 8 == 0) {
            int step = (t - 190) / 8;
            enc.score().chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, Score.semi(-12 + step), Score.semi(-5 + step), Score.semi(step));
        }
    }

    private void poseShells(int interp, int t) {
        for (int s = 0; s < shells.length; s++) {
            float r = SHELL_R[s] * shellScale;
            for (int i = 0; i < 3; i++) {
                float dir = (s % 2 == 0) ? 1f : -1f;
                Quaternionf rot = new Quaternionf().rotateXYZ(dir * t * 0.05f * (i + 1), dir * t * 0.07f, t * 0.03f * i);
                stage.push(shells[s][i], HeliosStage.cube(heart, r * 1.6f, rot), interp);
            }
        }
    }

    private void poseRays(int interp, float len, float width) {
        for (int i = 0; i < rays.size(); i++) {
            Vector3f dir = new Vector3f((float) Math.cos(i * 2.4f + raySpin), (float) Math.sin(i * 1.3f) * 0.55f,
                    (float) Math.sin(i * 2.4f + raySpin)).normalize();
            Vector3f from = new Vector3f(dir).mul(SHELL_R[3] * shellScale * 0.6f).add(heart);
            Vector3f to = new Vector3f(dir).mul(len * (0.8f + HMath.hash(i, 3) * 0.5f)).add(from);
            stage.push(rays.get(i), HeliosStage.beam(from, to, width), interp);
        }
    }

    /** The floor turns to light ring by ring outward (real blocks, restored afterwards). */
    private void glowFloor(float f) {
        if (glow == null) {
            glow = new ArrayList<>(ArenaLayout.surface());
            glow.sort(Comparator.comparingDouble(ArenaLayout.Cell::radius));
        }
        float reach = (float) enc.config().d("helios.supernova.floor-glow-radius", 30.0) * HMath.outCubic(f);
        Arena arena = enc.arena();
        while (glowCursor < glow.size() && glow.get(glowCursor).radius() <= reach) {
            ArenaLayout.Cell c = glow.get(glowCursor++);
            Material m = c.ring() == 0 ? Material.PEARLESCENT_FROGLIGHT : c.ring() == 1 ? Material.OCHRE_FROGLIGHT : Material.SHROOMLIGHT;
            arena.swap(c.dx(), c.dy(), c.dz(), m);
        }
    }

    /* ================================================================== 250: detonation */

    private void spawnWaves() {
        float[][] orient = {{0f, 0f}, {0f, 0f}, {0f, 0f}, {HMath.HALF_PI, 0f}, {HMath.HALF_PI, HMath.HALF_PI}, {0.5f, 0.8f}};
        float[] ys = {-9.7f, -6f, 0f, 0f, 0f, 0f};
        int segs = stage.budget().scaled(24, 14);
        for (int i = 0; i < orient.length; i++) {
            Shapes.Ring r = new Shapes.Ring(stage, g, segs, i < 3 ? Material.WHITE_CONCRETE : Material.ORANGE_STAINED_GLASS,
                    i < 3 ? WHITE : null, 15, true);
            r.hide(heart, 0);
            waves.add(r);
            waveOrient.add(new Quaternionf().rotateY(orient[i][1]).rotateX(orient[i][0]));
            waveY.add(ys[i]);
        }
    }

    private void spawnSphere() {
        int n = stage.budget().scaled(24, 12);
        Vector3f[] dirs = HMath.sphere(n);
        for (int i = 0; i < n; i++) {
            BlockDisplay d = g.block(Material.WHITE_STAINED_GLASS.createBlockData(), null, 15, true);
            stage.push(d, HeliosStage.gone(heart), 0);
            sphere.add(d);
            sphereDir.add(dirs[i]);
        }
        int m = stage.budget().scaled(28, 12);
        Material[] mats = {Material.SHROOMLIGHT, Material.MAGMA_BLOCK, Material.OCHRE_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.GILDED_BLACKSTONE};
        Vector3f[] edirs = HMath.sphere(m);
        for (int i = 0; i < m; i++) {
            BlockDisplay d = g.block(mats[i % mats.length].createBlockData(), null, 15, true);
            stage.push(d, HeliosStage.gone(heart), 0);
            ejecta.add(d);
            ejectaDir.add(edirs[i]);
        }
    }

    private void spawnNebula() {
        int n = stage.budget().scaled(14, 6);
        for (int i = 0; i < n; i++) {
            BlockDisplay d = g.block(NEBULA[i % NEBULA.length].createBlockData(), null, 15, true);
            stage.push(d, HeliosStage.gone(heart), 0);
            nebula.add(d);
            nebulaAt.add(new Vector3f(heart).add(HMath.ring(8f + HMath.hash(i, 7) * 10f, i * 2.1f, (HMath.hash(i, 9) - 0.5f) * 10f)));
        }
        for (int i = 0; i < pulsar.length; i++) {
            pulsar[i] = g.block((i == 0 ? Material.WHITE_CONCRETE : Material.WHITE_STAINED_GLASS).createBlockData(), i == 0 ? WHITE : null, 15, true);
            stage.push(pulsar[i], HeliosStage.gone(heart), 0);
        }
    }

    private void detonate() {
        Score score = enc.score();
        score.unmute();
        // Night becomes noon in one frame.
        enc.sky().weather(null);
        enc.sky().darken(false);
        enc.sky().fog(false);
        enc.sky().now(SkyControl.NOON);
        enc.camera().flash(12, 40);
        enc.camera().vignetteAll(0f);
        enc.camera().shakeAll(40, 2);
        score.play(Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 0.5f, true);
        score.play(Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f, true);
        score.play(Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.65f, true);
        score.play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 0.5f, true);
        score.play(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, 0.5f, true);
        score.play(Sound.ITEM_TOTEM_USE, 1f, 0.6f, true);
        score.play(Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.7f, true);
        stage.push(point, HeliosStage.gone(heart), 2);
        for (Player p : enc.audience()) {
            p.showTitle(net.kyori.adventure.title.Title.title(TextUtil.component("&f&lSUPERNOVA"),
                    TextUtil.component("&7The star is gone. The light is not."),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(0),
                            java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(1800))));
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.55, 0)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, false));
        }
    }

    private void expand(int t) {
        float f = HMath.outExpo(HMath.window(t, 0, 70));
        if (t % 2 != 0) {
            return;
        }
        // The swollen shells burst outward and thin to nothing.
        shellScale = 1.15f + 1.2f * f;
        for (int s = 0; s < shells.length; s++) {
            for (int i = 0; i < 3; i++) {
                if (t > 16) {
                    stage.push(shells[s][i], HeliosStage.gone(heart), 2);
                } else {
                    Quaternionf rot = new Quaternionf().rotateXYZ(t * 0.2f * (i + 1), t * 0.15f, 0f);
                    stage.push(shells[s][i], HeliosStage.cube(heart, SHELL_R[s] * shellScale * 1.6f * (1f - t / 17f), rot), 2);
                }
            }
        }
        poseRays(2, 20f + 70f * f, Math.max(0.03f, 0.4f * (1f - f)));
        for (int i = 0; i < waves.size(); i++) {
            float r = 2f + 72f * f * (1f - i * 0.06f);
            float thick = 1.1f * (1f - f) + 0.06f;
            Vector3f at = new Vector3f(heart).add(0f, waveY.get(i), 0f);
            waves.get(i).pose(at, waveOrient.get(i), r, thick, thick, t * 0.03f, 0f, 0f, 2);
        }
        for (int i = 0; i < sphere.size(); i++) {
            Vector3f dir = sphereDir.get(i);
            Vector3f at = new Vector3f(dir).mul(3f + 58f * f).add(heart);
            float size = 5f * (1f - f) + 0.2f;
            stage.push(sphere.get(i), HeliosStage.box(at, new Vector3f(size, 0.08f, size), HMath.alignY(dir)), 2);
        }
        for (int i = 0; i < ejecta.size(); i++) {
            Vector3f at = new Vector3f(ejectaDir.get(i)).mul(4f + 50f * f * (0.6f + HMath.hash(i, 3) * 0.6f)).add(heart);
            stage.push(ejecta.get(i), HeliosStage.cube(at, 1.3f * (1f - f) + 0.08f, new Quaternionf().rotateXYZ(t * 0.1f + i, t * 0.07f, 0f)), 2);
        }
        if (t == 68) {
            for (Shapes.Ring r : waves) {
                r.hide(heart, 2);
            }
            for (BlockDisplay d : sphere) {
                stage.push(d, HeliosStage.gone(heart), 2);
            }
            for (BlockDisplay d : ejecta) {
                stage.push(d, HeliosStage.gone(heart), 2);
            }
            for (BlockDisplay d : rays) {
                stage.push(d, HeliosStage.gone(heart), 2);
            }
        }
    }

    /* ================================================================== 265: nebula, pulsar, dawn */

    private void afterglow(int t) {
        if (t == 265) {
            enc.sky().to(SkyControl.DAWN, 90f);
        }
        if (t % 2 != 0) {
            return;
        }
        float life = HMath.window(t, 265, END - 20);
        float fade = 1f - HMath.smooth(HMath.window(t, 420, END - 20));
        for (int i = 0; i < nebula.size(); i++) {
            Vector3f base = nebulaAt.get(i);
            Vector3f out = new Vector3f(base).sub(heart).mul(1f + life * 0.8f).add(heart);
            float size = (6f + HMath.hash(i, 13) * 6f) * fade;
            Quaternionf rot = new Quaternionf().rotateY(i + t * 0.004f).rotateX(0.6f + HMath.hash(i, 17));
            stage.push(nebula.get(i), HeliosStage.box(out, new Vector3f(size, 0.05f, size * 0.6f), rot), 2);
        }
        // The pulsar: a white point sweeping two beams like a lighthouse.
        float spin = t * 0.12f;
        stage.push(pulsar[0], HeliosStage.cube(heart, 0.5f, new Quaternionf().rotateY(spin)), 2);
        Vector3f axis = new Vector3f((float) Math.cos(spin), 0.35f, (float) Math.sin(spin)).normalize();
        float len = 18f * fade;
        stage.push(pulsar[1], HeliosStage.beam(heart, new Vector3f(axis).mul(len).add(heart), 0.12f), 2);
        stage.push(pulsar[2], HeliosStage.beam(heart, new Vector3f(axis).mul(-len).add(heart), 0.12f), 2);
        if (t % 24 == 0 && t < 440) {
            enc.score().at(heart, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.8f);
        }
    }

    /* ================================================================== 300: giving back */

    private void beginRebuild() {
        Arena arena = enc.arena();
        rebuild = new ArrayList<>(arena.damaged());
        rebuild.sort(Comparator.comparingDouble(ArenaLayout.Cell::radius).thenComparingDouble(ArenaLayout.Cell::angle));
        rebuildCursor = 0;
        enc.score().sweep(null, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f, 0.8f, 1.6f, 160, 5);
    }

    private void tickRebuild() {
        Arena arena = enc.arena();
        if (rebuild != null && rebuildCursor < rebuild.size()) {
            int per = Math.max(20, rebuild.size() / 120 + 1);
            int end = Math.min(rebuild.size(), rebuildCursor + per);
            List<ArenaLayout.Cell> batch = new ArrayList<>(rebuild.subList(rebuildCursor, end));
            arena.restore(batch);
            // Rebuilt ground arrives glowing: it joins the light before the light draws back.
            for (ArenaLayout.Cell c : batch) {
                if (c.layer() == 0) {
                    Material m = c.ring() == 0 ? Material.PEARLESCENT_FROGLIGHT : c.ring() == 1 ? Material.OCHRE_FROGLIGHT : Material.SHROOMLIGHT;
                    arena.swap(c.dx(), c.dy(), c.dz(), m);
                }
            }
            rebuildCursor = end;
            return;
        }
        // Then the glow draws back inward, from the rim to the heart; the lights come back last.
        if (glow != null && glowBack < glow.size()) {
            int per = Math.max(30, glow.size() / 80 + 1);
            for (int i = 0; i < per && glowBack < glow.size(); i++, glowBack++) {
                ArenaLayout.Cell c = glow.get(glow.size() - 1 - glowBack);
                arena.clearTemp(c.dx(), c.dy(), c.dz());
            }
            if (glowBack >= glow.size()) {
                // The lights were layout cells turned to air: put them back from the layout itself.
                arena.revertTempsIn(lights);
                arena.restore(lights);
                arena.revertTemps();
            }
        }
    }

    void clear() {
        g.clear();
    }
}
