package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.HeliosModule;
import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.core.SkyControl;
import de.aetherion.bossengine.helios.encounter.ActScript;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ACT II · HELIOS.
 *
 * <p>The star did not die. It tore itself open and came out as the thing that was always inside: an
 * armillary sphere wearing the Herald's blades as its rings, a porcelain mask with gold tears.
 *
 * <pre>
 *   INTERLUDE (320 t) heartbeat quickens · the star's seams split · the shells drift apart · night falls
 *             per player · four blades come out of the heart and each draws a ring · the mask forms · title
 *   I   KORONA        (100-60 %, 100 BPM) portals, plasma rings, flares, solar wind
 *   II  ZERFALL       (60-25 %, 108 BPM)  the Corona burns away; + meteors, geometry cages, seismic bombs;
 *                                          at 40 % a supernova wave breaks the Course into islands
 *   III SINGULARITÄT  (25-6 %, 80 BPM)    the black hole; gravity, lensing, time dilation, islands torn away
 *   IV  REQUIEM       (6 %)               silence, then the last movement; survive it, then the heart
 *   DEATH (420 t)     slow motion · collapse · swell · SUPERNOVA · afterglow · dawn · the arena rebuilds
 *                     from light, ring by ring · one point of light stays
 * </pre>
 */
public final class HeliosScript extends ActScript {

    private static final int INTERLUDE = 320;
    private static final int DEATH = 440;
    private static final Color WHITE = Color.fromRGB(255, 250, 235);

    private HeliosRig rig;
    private Singularity singularity;
    private Requiem requiem;
    private boolean shattered;
    private boolean exposed;
    private final Vector3f station = new Vector3f(0f, 4f, 0f);
    private int stationAt;
    private float orbit;
    private final List<int[]> molten = new ArrayList<>();
    private int enragePulse;

    /* death */
    private final List<BlockDisplay> nova = new ArrayList<>();
    private final List<Vector3f> novaDir = new ArrayList<>();
    private Shapes.Ring[] shock;
    private HeliosStage.Group deathGroup;
    private List<ArenaLayout.Cell> rebuild;
    private int rebuildCursor;

    public HeliosScript(HeliosModule module, BossInstance instance) {
        super(module, instance);
    }

    /* ================================================================== helpers for attacks */

    HeliosRig rig() {
        return rig;
    }

    double power(String key, double def) {
        return enc.config().power("helios." + key, def);
    }

    Vector3f partyCentroid() {
        Vector3f c = new Vector3f();
        List<Player> f = enc.fighters();
        if (f.isEmpty()) {
            return new Vector3f(0f, 0f, 18f);
        }
        for (Player p : f) {
            c.add(enc.stage().feet(p));
        }
        return c.div(f.size());
    }

    Vector away(Vector3f from, Vector3f to, double strength) {
        Vector3f d = new Vector3f(to).sub(from);
        d.y = 0f;
        if (d.lengthSquared() < 1e-4f) {
            d.set(0f, 0f, 1f);
        }
        d.normalize();
        return new Vector(d.x * strength, 0.4, d.z * strength);
    }

    /** A molten floor cell (real magma) that reverts after {@code ticks}. */
    void moltenAt(int dx, int dz, int ticks) {
        Arena arena = enc.arena();
        if (ArenaLayout.at(dx, -1, dz) == null || !arena.solidAt(dx + 0.5f, dz + 0.5f)) {
            return;
        }
        arena.swap(dx, -1, dz, Material.MAGMA_BLOCK);
        molten.add(new int[]{dx, dz, clock + ticks});
    }

    void stopAttacks() {
        endAttacks();
    }

    void glideTo(Vector3f to, float rate) {
        rig.center.lerp(to, rate);
    }

    void exposeHeart(boolean on) {
        exposed = on;
        if (on) {
            rig.ringScale(1.9f);
            rig.crack(1f);
        } else {
            rig.ringScale(1f);
        }
    }

    /* ================================================================== lifecycle */

    @Override
    protected void begin() {
        rig = new HeliosRig(enc.stage(), enc.tempo());
        rig.center.set(DyingStar.CENTER);
        rig.visible(false);
        for (int i = 0; i < 4; i++) {
            rig.ringDrawn(i, 0f);
        }
        rig.maskAssembled(0f);
        hitbox.set(rig.center).sub(0f, 1.3f, 0f);
        enc.bars().boss("&c☀ &f&lHELIOS &c☀", BossBar.Color.RED, 60, 25, 6);
        enc.bars().visible(false);
        enc.score().dilation(null);
        mode = Mode.INTRO;
        modeTick = 0;
        enc.cinematic(true);
    }

    @Override
    protected void tickIntro() {
        int t = modeTick;
        DyingStar star = enc.star();
        Score score = enc.score();
        // 0-60: the heartbeat quickens; the seams split wide.
        if (t < 60 && t % Math.max(6, 16 - t / 6) == 0) {
            score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f + t / 150f, true);
            star.crack(0.6f + 0.4f * HMath.window(t, 0, 60));
            enc.camera().shakeAll(3, 2);
        }
        if (t == 10) {
            enc.sky().to(SkyControl.NIGHT, 55f);
            score.sweep(null, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.3f, 0.5f, 1.2f, 90, 5);
        }
        // 60-110: it tears open; the shells drift apart and the naked heart glares.
        if (t == 60) {
            star.open(1f);
            star.pulse(0.8f);
            score.play(Sound.BLOCK_END_PORTAL_SPAWN, 1f, 0.6f);
            score.play(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
            enc.camera().tint(Material.ORANGE_STAINED_GLASS, 3, 10);
            enc.camera().shakeAll(20, 2);
        }
        if (t == 100) {
            enc.camera().flash(2, 10);
            star.heartVisible(false);
            star.coronaVisible(false);
            rig.visible(true);
            rig.heartSize(0.3f);
            score.play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
        }
        if (t >= 100 && t < 140) {
            rig.heartSize(0.3f + 0.7f * HMath.outBack(HMath.window(t, 100, 140)));
        }
        // 120-200: the Herald's blades come out of the heart; each draws its ring.
        for (int i = 0; i < 4; i++) {
            int from = 120 + i * 18;
            if (t == from) {
                score.play(Sound.ITEM_TRIDENT_RETURN, 1f, 0.8f + i * 0.15f);
                score.play(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, Score.semi(new int[]{0, 3, 7, 12}[i]));
            }
            if (t >= from && t <= from + 30) {
                rig.ringDrawn(i, HMath.inOutCubic(HMath.window(t, from, from + 30)));
            }
        }
        // 190-250: the mask forms, crown last; the eyes open.
        if (t >= 190 && t <= 250) {
            rig.maskAssembled(HMath.window(t, 190, 250));
            if (t % 8 == 0) {
                score.play(Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 0.6f + (t - 190) / 90f);
            }
        }
        if (t == 252) {
            score.chord(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, Score.semi(-12), Score.semi(-9), Score.semi(-5), Score.semi(0));
            score.play(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);
            for (Player p : enc.audience()) {
                p.showTitle(net.kyori.adventure.title.Title.title(
                        TextUtil.component("&c&lHELIOS"),
                        TextUtil.component("&7Requiem of a dying star"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400),
                                java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(800))));
            }
            enc.bars().visible(true);
            enc.bars().fillOver(50);
            enc.bars().movement(true);
        }
        // 260-320: the rings spin up; it descends to fighting height.
        if (t >= 260) {
            rig.spin(HMath.lerp(0.3f, 1f, HMath.window(t, 260, 320)));
            glideTo(new Vector3f(0f, 5f, 0f), 0.04f);
        }
        lookAtParty(0.05f);
        if (t >= INTERLUDE) {
            enc.tempo().bpm(enc.config().bpm("corona", 100), 40);
            enc.sky().darken(false);
            enterFight();
            enc.fightStarted();
            station.set(rig.center);
        }
    }

    @Override
    protected void tickFight() {
        String phase = phaseId();
        double hp = instance.healthPercent();
        tickMolten();
        if (singularity != null) {
            singularity.progress((float) ((25.0 - hp) / 19.0));
            singularity.tick();
        }
        if (requiem != null && requiem.tick()) {
            if (requiem.stage() == Requiem.Stage.WINDOW) {
                // The heart comes down onto the Crown, in reach.
                Vector3f c = partyCentroid();
                Vector3f down = HMath.ring(11f, HMath.angleOf(c.x, c.z), 2.6f);
                glideTo(down, 0.08f);
                lookAtParty(0.2f);
            }
            movementBar(phase, hp);
            return;
        }
        if (!shattered && hp <= 40.0 && "zerfall".equalsIgnoreCase(phase)) {
            shattered = true;
            spawnHazard(new ArenaBreak(this, ArenaBreak.Kind.SHATTER));
            holdScheduler(enc.tempo().ticks(8));
        }
        move(phase);
        movementBar(phase, hp);
        if (enc.enraged()) {
            enragePulse++;
            if (enragePulse % 200 == 0) {
                // Past its time: the star starts going nova on its own. Unavoidable, growing.
                enc.camera().flash(1, 6);
                enc.score().play(Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                for (Player p : enc.fighters()) {
                    enc.hit(p, 35, "enrage", 20, null);
                }
            }
        }
    }

    /** Where Helios drifts between moves: low enough to reach, never on top of the party. */
    private void move(String phase) {
        if (opening()) {
            glideTo(openingAt, 0.08f);
            lookAtParty(0.05f);
            return;
        }
        if (singularity != null) {
            orbit += 0.004f;
            Vector3f to = new Vector3f(Singularity.CENTER).add(HMath.ring(13.5f, orbit, 1.2f));
            glideTo(to, 0.05f);
            lookAtParty(0.1f);
            return;
        }
        if (modeTick >= stationAt) {
            Vector3f c = partyCentroid();
            float a = HMath.angleOf(c.x, c.z) + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.2f;
            float r = 6f + ThreadLocalRandom.current().nextFloat() * 8f;
            station.set(HMath.ring(r, a, 3.8f));
            stationAt = modeTick + enc.tempo().ticks(8);
        }
        glideTo(station, 0.025f);
        rig.center.y += 0.02f * (float) Math.sin(clock * 0.07f);
        lookAtParty(0.08f);
    }

    private void lookAtParty(float step) {
        Player near = null;
        float best = Float.MAX_VALUE;
        for (Player p : enc.audience()) {
            float d = enc.stage().feet(p).distanceSquared(rig.center);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near != null) {
            rig.lookAt(enc.stage().feet(near).add(0f, 1.6f, 0f), step);
        }
    }

    private void movementBar(String phase, double hp) {
        if (clock % 10 != 0 || (enc.enraged() || enc.fightClock() > enc.config().heliosEnrageTicks() - 20 * 60)) {
            return;
        }
        switch (phase.toLowerCase(java.util.Locale.ROOT)) {
            case "zerfall" -> enc.bars().movement("&6II &8· &6Decay", (float) ((hp - 25.0) / 35.0), BossBar.Color.YELLOW);
            case "singularitaet" -> enc.bars().movement("&5III &8· &dSingularity", (float) ((hp - 6.0) / 19.0), BossBar.Color.PURPLE);
            case "requiem" -> enc.bars().movement(requiem != null && requiem.exposed() ? "&f&lIV &8· &f&lThe Heart" : "&fIV &8· &fRequiem",
                    requiem != null && requiem.exposed() ? requiem.windowProgress() : 1f, BossBar.Color.WHITE);
            default -> enc.bars().movement("&cI &8· &6Corona", (float) ((hp - 60.0) / 40.0), BossBar.Color.RED);
        }
    }

    private void tickMolten() {
        for (Iterator<int[]> it = molten.iterator(); it.hasNext(); ) {
            int[] m = it.next();
            if (clock >= m[2]) {
                enc.arena().clearTemp(m[0], -1, m[1]);
                it.remove();
            }
        }
    }

    @Override
    protected void tickBody() {
        if (rig == null) {
            return;
        }
        rig.render(2);
        hitbox.set(rig.center).sub(0f, 1.3f, 0f);
    }

    @Override
    protected void onPhase(String from, String to) {
        switch (to.toLowerCase(java.util.Locale.ROOT)) {
            case "zerfall" -> {
                enc.tempo().bpm(enc.config().bpm("decay", 108), 60);
                spawnHazard(new ArenaBreak(this, ArenaBreak.Kind.BURN));
                holdScheduler(enc.tempo().ticks(6));
                rig.crack(0.3f);
            }
            case "singularitaet" -> {
                endAttacks();
                enc.tempo().bpm(enc.config().bpm("singularity", 80), 60);
                enc.score().play(Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.5f);
                enc.score().play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                for (Player p : enc.audience()) {
                    enc.camera().flash(p, Material.BLACK_CONCRETE, Material.PURPLE_STAINED_GLASS, 3, 12);
                }
                enc.camera().darknessAll(40);
                enc.star().shellsVisible(false);
                enc.sky().weather(WeatherType.DOWNFALL);
                enc.sky().fog(true);
                rig.black(true);
                rig.crack(0.6f);
                singularity = new Singularity(this);
                enc.score().dilation(singularity::dilation);
                enc.sky().freeze(singularity::skyRate);
                holdScheduler(enc.tempo().ticks(4));
                for (Player p : enc.audience()) {
                    p.sendActionBar(TextUtil.component("&5The star falls into itself."));
                }
            }
            case "requiem" -> {
                if (singularity != null) {
                    singularity.quiet(true);
                }
                requiem = new Requiem(this);
                requiem.begin();
            }
            default -> {
            }
        }
    }

    @Override
    protected boolean paused() {
        return requiem != null;
    }

    @Override
    protected boolean shielded() {
        return requiem != null && !requiem.exposed();
    }

    @Override
    protected double reshape(Player player, double amount) {
        if (requiem != null && requiem.exposed()) {
            return amount * enc.config().d("helios.requiem.damage-multiplier", 2.5);
        }
        return amount;
    }

    @Override
    protected int maxConcurrent() {
        double hp = instance.healthPercent();
        if (enc.enraged()) {
            return 3;
        }
        return hp > 60 ? 1 : 2;
    }

    @Override
    protected int flurryLength() {
        return instance.healthPercent() > 60 ? 2 : 3;
    }

    /** Spent: the heart sinks to the Crown within sword reach, the rings open wide and slow. */
    @Override
    protected void onOpening(boolean open) {
        if (open) {
            Vector3f c = partyCentroid();
            float a = HMath.angleOf(c.x, c.z);
            Vector3f from = singularity != null ? Singularity.CENTER : new Vector3f();
            openingAt.set(from).add(HMath.ring(singularity != null ? 11.5f : 11f, a, 0f));
            openingAt.y = 2.5f;
            rig.ringScale(1.9f);
            rig.spin(0.3f);
            rig.heartSize(0.8f);
            enc.score().at(rig.center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            enc.score().at(rig.center, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.7f);
        } else {
            rig.ringScale(1f);
            rig.spin(1f);
            rig.heartSize(1f);
            enc.score().at(rig.center, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
            stationAt = 0;
        }
    }

    private final Vector3f openingAt = new Vector3f();

    @Override
    protected Vector3f openingSpot() {
        return new Vector3f(rig.center.x, 0f, rig.center.z);
    }

    /* ================================================================== moves */

    @Override
    protected List<Choice> choices() {
        String phase = phaseId().toLowerCase(java.util.Locale.ROOT);
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("portals", () -> new PortalBeams(this, 0), 4, 20, Attack.Family.SWEEP));
        out.add(new Choice("plasma", () -> new PlasmaRings(this, 2 + ThreadLocalRandom.current().nextInt(2), null), 3, 16, Attack.Family.GROUND));
        out.add(new Choice("flares", () -> new SolarFlares(this, 4), 3, 14, Attack.Family.SKY));
        if (singularity == null) {
            out.add(new Choice("wind", () -> new SolarWind(this, 2), 2, 30, Attack.Family.ARENA));
        }
        if (!"korona".equals(phase)) {
            out.add(new Choice("meteors", () -> new MeteorRain(this, enc.config().i("helios.meteors.count", 6), 1.0), 3, 20, Attack.Family.SKY));
            out.add(new Choice("cage", () -> new GeometryCage(this, null, 10), 3, 24, Attack.Family.SWEEP));
            out.add(new Choice("seismic", () -> new SeismicBomb(this, null, 4), 3, 22, Attack.Family.GROUND));
        }
        return out;
    }

    @Override
    protected List<Choice> allChoices() {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("portals", () -> new PortalBeams(this, 0), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("portals5", () -> new PortalBeams(this, 5), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("plasma", () -> new PlasmaRings(this, 4, null), 1, 1, Attack.Family.GROUND));
        out.add(new Choice("flares", () -> new SolarFlares(this, 3), 1, 1, Attack.Family.SKY));
        out.add(new Choice("wind", () -> new SolarWind(this, 3), 1, 1, Attack.Family.ARENA));
        out.add(new Choice("meteors", () -> new MeteorRain(this, 7, 1.0), 1, 1, Attack.Family.SKY));
        out.add(new Choice("icosa", () -> new GeometryCage(this, GeometryCage.Kind.ICOSA, 8), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("cross", () -> new GeometryCage(this, GeometryCage.Kind.CROSS, 8), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("seismic", () -> new SeismicBomb(this, null, 3), 1, 1, Attack.Family.GROUND));
        out.add(new Choice("burn", () -> new ArenaBreak(this, ArenaBreak.Kind.BURN), 1, 1, Attack.Family.ARENA));
        out.add(new Choice("shatter", () -> new ArenaBreak(this, ArenaBreak.Kind.SHATTER), 1, 1, Attack.Family.ARENA));
        return out;
    }

    /* ================================================================== death */

    @Override
    protected void onDeathStart() {
        enc.cinematic(true);
        enc.bars().visible(false);
        enc.bars().movement(false);
        if (singularity != null) {
            singularity.quiet(true);
            singularity.release();
        }
        enc.score().stopVoices();
        enc.stage().timeScale(0.25f);
        enc.sky().freeze(p -> 0.0);
        rig.spin(0.25f);
        rig.crack(1f);
        exposed = false;
        deathGroup = enc.stage().group();
    }

    @Override
    protected boolean tickDeath() {
        int t = modeTick;
        Score score = enc.score();
        // 0-60: slow motion. The mask cracks through, the rings crawl, the heart stutters.
        if (t < 60) {
            if (t % 20 == 0) {
                score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.5f, true);
            }
            if (t == 5) {
                score.play(Sound.BLOCK_GLASS_BREAK, 1f, 0.5f, true);
            }
            glideTo(new Vector3f(0f, 9f, 0f), 0.03f);
        }
        // 60-120: collapse. Everything is pulled into the heart.
        if (t >= 60 && t < 120) {
            float f = HMath.inCubic(HMath.window(t, 60, 118));
            rig.collapse(f);
            if (singularity != null) {
                singularity.collapse(f);
            }
            enc.star().scale(1f - f, 0.05f);
            if (t == 60) {
                score.sweep(null, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.4f, 2f, 0.5f, 58, 4);
            }
        }
        if (t == 120) {
            score.silence(30);
            if (singularity != null) {
                singularity.clear();
                singularity = null;
            }
            rig.visible(false);
            enc.star().clear();
            enc.stage().timeScale(1f);
            core = deathGroup.block(Material.WHITE_CONCRETE.createBlockData(), WHITE, 15, true);
            coreGlass = deathGroup.block(Material.ORANGE_STAINED_GLASS.createBlockData(), null, 15, true);
            enc.stage().push(core, HeliosStage.cube(rig.center, 0.25f, new Quaternionf()), 0);
            enc.stage().push(coreGlass, HeliosStage.cube(rig.center, 0.01f, new Quaternionf()), 0);
        }
        // 150-200: it swells. The screen edges burn red; the ground shakes harder and harder.
        if (t >= 150 && t < 200) {
            float f = HMath.window(t, 150, 200);
            Quaternionf spin = new Quaternionf().rotateXYZ(t * 0.2f, t * 0.13f, t * 0.07f);
            enc.stage().push(core, HeliosStage.cube(rig.center, 0.25f + 5f * HMath.inExpo(f), spin), 1);
            enc.stage().push(coreGlass, HeliosStage.cube(rig.center, 0.4f + 7.5f * HMath.inExpo(f), new Quaternionf(spin).invert()), 1);
            enc.camera().vignetteAll(0.2f + 0.7f * f);
            if (t % 6 == 0) {
                enc.camera().shakeAll(6, 2);
            }
            if (t == 150) {
                score.play(Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.5f, true);
                score.sweep(null, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.2f, 0.5f, 1.2f, 50, 5);
            }
        }
        // 200: SUPERNOVA.
        if (t == 200) {
            supernova();
        }
        if (t > 200 && t < 300) {
            tickNova(t - 200);
        }
        // 230-420: afterglow and dawn; the arena rebuilds from light, ring by ring.
        if (t == 230) {
            enc.sky().freeze(null);
            enc.sky().weather(null);
            enc.sky().fog(false);
            enc.sky().darken(false);
            enc.sky().to(SkyControl.DAWN, 60f);
            enc.camera().vignetteAll(0f);
            beginRebuild();
        }
        if (t > 230) {
            tickRebuild();
        }
        if (t == 330) {
            enc.sky().to(200f, 25f);
            score.chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, Score.semi(0), Score.semi(4), Score.semi(7), Score.semi(12));
            score.play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
        }
        if (t >= DEATH && rebuild != null && rebuildCursor >= rebuild.size()) {
            finishRestore();
            deathGroup.clear();
            rig.clear();
            enc.heliosFallen();
            return true;
        }
        if (t >= DEATH + 200) {
            // Failsafe: never hang on a slow rebuild.
            finishRestore();
            deathGroup.clear();
            rig.clear();
            enc.heliosFallen();
            return true;
        }
        return false;
    }

    private BlockDisplay core;
    private BlockDisplay coreGlass;

    private void supernova() {
        Score score = enc.score();
        score.unmute();
        enc.camera().flash(6, 24);
        enc.camera().shakeAll(30, 2);
        score.play(Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f, true);
        score.play(Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f, true);
        score.play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f, true);
        score.play(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.5f, true);
        score.play(Sound.ITEM_TOTEM_USE, 1f, 0.6f, true);
        enc.stage().push(core, HeliosStage.gone(rig.center), 4);
        enc.stage().push(coreGlass, HeliosStage.cube(rig.center, 14f, new Quaternionf()), 4);
        shock = new Shapes.Ring[4];
        float[] tilt = {0f, 0.35f, -0.35f, HMath.HALF_PI};
        for (int i = 0; i < shock.length; i++) {
            shock[i] = new Shapes.Ring(enc.stage(), deathGroup, enc.stage().budget().scaled(36, 20),
                    i == 0 ? Material.WHITE_CONCRETE : Material.ORANGE_STAINED_GLASS, i == 0 ? WHITE : null, 15, true);
        }
        int n = enc.stage().budget().scaled(26, 12);
        Material[] mats = {Material.SHROOMLIGHT, Material.MAGMA_BLOCK, Material.OCHRE_FROGLIGHT, Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};
        Vector3f[] dirs = HMath.sphere(n);
        for (int i = 0; i < n; i++) {
            BlockDisplay d = deathGroup.block(mats[i % mats.length].createBlockData(), null, 15, false);
            if (d == null) {
                break;
            }
            nova.add(d);
            novaDir.add(dirs[i]);
        }
        for (Player p : enc.audience()) {
            p.showTitle(net.kyori.adventure.title.Title.title(TextUtil.component(""),
                    TextUtil.component("&f&oSupernova"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(0),
                            java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(1500))));
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.6, 0)));
        }
    }

    private void tickNova(int t) {
        float f = HMath.outExpo(HMath.window(t, 0, 60));
        float[] tilt = {0f, 0.35f, -0.35f, HMath.HALF_PI};
        for (int i = 0; i < shock.length; i++) {
            Quaternionf orient = new Quaternionf().rotateX(tilt[i]).rotateY(i * 0.7f);
            float r = 2f + 60f * f * (1f - i * 0.12f);
            float thick = 0.8f * (1f - f) + 0.05f;
            shock[i].pose(rig.center, orient, r, thick, thick, t * 0.02f, 0f, 0f, 2);
        }
        for (int i = 0; i < nova.size(); i++) {
            Vector3f at = new Vector3f(novaDir.get(i)).mul(4f + 45f * f * (0.6f + HMath.hash(i, 3) * 0.6f)).add(rig.center);
            float size = 1.2f * (1f - f) + 0.05f;
            enc.stage().push(nova.get(i), HeliosStage.cube(at, size, new Quaternionf().rotateXYZ(t * 0.1f + i, t * 0.07f, 0f)), 2);
        }
        if (t == 20) {
            enc.stage().push(coreGlass, HeliosStage.gone(rig.center), 40);
        }
        if (t == 60) {
            for (Shapes.Ring r : shock) {
                r.hide(rig.center, 10);
            }
            for (BlockDisplay d : nova) {
                enc.stage().push(d, HeliosStage.gone(rig.center), 30);
            }
        }
    }

    /* ------------------------------------------------------------------ restore from light */

    private void beginRebuild() {
        Arena arena = enc.arena();
        arena.revertTemps();
        rebuild = new ArrayList<>(arena.damaged());
        rebuild.sort(Comparator.comparingDouble(ArenaLayout.Cell::radius).thenComparingDouble(ArenaLayout.Cell::angle));
        rebuildCursor = 0;
        enc.score().sweep(null, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f, 0.8f, 1.6f, 160, 5);
    }

    private void tickRebuild() {
        if (rebuild == null || rebuildCursor >= rebuild.size()) {
            return;
        }
        int per = Math.max(20, rebuild.size() / 140 + 1);
        int end = Math.min(rebuild.size(), rebuildCursor + per);
        List<ArenaLayout.Cell> batch = rebuild.subList(rebuildCursor, end);
        enc.arena().restore(new ArrayList<>(batch));
        // A thin sheet of light runs ahead of the rebuilt ground.
        if (!batch.isEmpty() && modeTick % 3 == 0) {
            ArenaLayout.Cell c = batch.get(batch.size() - 1);
            BlockDisplay glint = deathGroup.block(Material.WHITE_STAINED_GLASS.createBlockData(), WHITE, 15, false);
            if (glint != null) {
                Vector3f at = new Vector3f(c.dx() + 0.5f, 0.1f, c.dz() + 0.5f);
                enc.stage().push(glint, HeliosStage.cube(at, 1.1f, new Quaternionf()), 0);
                enc.stage().push(glint, HeliosStage.gone(new Vector3f(at).add(0f, 3f, 0f)), 20);
            }
        }
        rebuildCursor = end;
    }

    private void finishRestore() {
        Arena arena = enc.arena();
        List<ArenaLayout.Cell> left = arena.damaged();
        if (!left.isEmpty()) {
            arena.restore(left);
        }
        arena.revertTemps();
        arena.cracks().clearAll();
        arena.forgetDamage();
    }

    @Override
    public boolean handLoot(Map<UUID, List<ItemStack>> bundles) {
        return enc != null && enc.reward(bundles);
    }

    @Override
    protected void clearBody() {
        if (singularity != null) {
            singularity.clear();
            singularity = null;
        }
        for (int[] m : molten) {
            if (enc != null && enc.arena() != null) {
                enc.arena().clearTemp(m[0], -1, m[1]);
            }
        }
        molten.clear();
        if (deathGroup != null) {
            deathGroup.clear();
        }
        if (rig != null) {
            rig.clear();
        }
        if (enc != null && enc.stage() != null) {
            enc.stage().timeScale(1f);
        }
    }
}
