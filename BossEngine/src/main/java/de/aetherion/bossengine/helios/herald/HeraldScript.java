package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.HeliosGuard;
import de.aetherion.bossengine.helios.HeliosModule;
import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.core.SkyControl;
import de.aetherion.bossengine.helios.encounter.ActScript;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ACT I · THE HERALD.
 *
 * <p>The warden the dying star built to guard its last hours. He is assembled from the star's own debris
 * in front of you, fights with four floating blades and the star's light, and at half health splits
 * into four; only the one whose heart beats in time with the star is real. When he falls, the star
 * reaches down with a gravity beam, stretches him into strands and swallows him whole. Then silence,
 * then a heartbeat: Act II.
 *
 * <pre>
 *   INTRO   (240 t) blackout → the star ignites beat by beat → four blades fall and bite into the Course →
 *           armor flies in from the debris belt, feet first → the sun core ignites (white flash) → title
 *   FIGHT   84 BPM. Blink-Strike, Blade Swarm (spiral / fan / pincer), Laser Cage, Platform Fall, Sun Lance,
 *           Starfall (he leaps into the star's light, hunts one player and dives: crater + shockwave).
 *           Moves chain on the next beat, flurries of 3 (4 below half), a short Opening after each.
 *   MIRROR  (50 %) three clones; the real one's heart is audible on the beat; find it
 *   DEATH   (210 t) he kneels, blades fall; silence; gravity beam; stretched, swallowed; flash; heartbeat
 * </pre>
 */
public final class HeraldScript extends ActScript implements HeliosGuard.PropAware {

    private static final int INTRO = 240;
    private static final int DEATH = 210;
    static final float STATION_R = 12.5f;

    HeraldRig rig;
    private final Vector3f station = new Vector3f(0f, 0f, -12f);
    private int stationAt;
    private int hitReact;
    private boolean fastRender;

    /* mirror */
    private final List<HeraldRig> clones = new ArrayList<>();
    private final List<Interaction> cloneBoxes = new ArrayList<>();
    private final List<Vector3f> mirrorSpots = new ArrayList<>();
    private int realIndex;
    private int mirrorTick = -1;
    private int mirrorRounds;
    private boolean mirrorDone;
    private int stunned;

    /* death */
    private Shapes.Beam gravity;
    private Shapes.Ring[] gravityRings;
    private HeliosStageRef deathRef;

    public HeraldScript(HeliosModule module, BossInstance instance) {
        super(module, instance);
    }

    /* ================================================================== helpers for attacks */

    public HeraldRig rig() {
        return rig;
    }

    double power(String key, double def) {
        return enc.config().power("herald." + key, def);
    }

    int cfgInt(String key, int def) {
        return enc.config().i("herald." + key, def);
    }

    double cfgD(String key, double def) {
        return enc.config().d("herald." + key, def);
    }

    Player pickTarget(boolean farthest) {
        List<Player> f = enc.fighters();
        if (f.isEmpty()) {
            return null;
        }
        if (!farthest) {
            return f.get(ThreadLocalRandom.current().nextInt(f.size()));
        }
        Player best = null;
        float bestD = -1;
        for (Player p : f) {
            float d = enc.stage().feet(p).distanceSquared(rig.root);
            if (d > bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Snappy per-tick rendering while a move needs it. */
    void fast(boolean on) {
        fastRender = on;
    }

    boolean mirrorActive() {
        return mirrorTick >= 0;
    }

    /* ================================================================== lifecycle */

    @Override
    protected void begin() {
        rig = new HeraldRig(enc.stage(), false);
        rig.root.set(0f, 0f, -12f);
        rig.face = 0f;
        rig.snap(HeraldRig.Pose.idle());
        rig.visible(false);
        hitbox.set(rig.root).add(0f, 0.4f, 0f);
        enc.bars().boss("&6✦ &e&lTHE HERALD &6✦", BossBar.Color.YELLOW, 50);
        enc.bars().visible(false);
        enc.tempo().bpm(enc.config().bpm("interlude", 60), 0);
        enc.star().pulse(0.2f);
        enc.star().spin(0.4f);
        mode = Mode.INTRO;
        modeTick = 0;
        enc.cinematic(true);
    }

    @Override
    protected void tickIntro() {
        int t = modeTick;
        DyingStar star = enc.star();
        Score score = enc.score();
        if (t == 1) {
            score.silence(20);
        }
        // The star ignites on four heartbeats, a little bigger each time.
        if (t == 20 || t == 40 || t == 60 || t == 80) {
            int k = t / 20;
            star.scale(0.25f * k, 0.03f);
            score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f + 0.05f * k, true);
            score.play(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f, true);
            if (k == 4) {
                score.play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
                enc.sky().to(SkyControl.DUSK - 600f, 30f);
            }
        }
        if (t == 85) {
            star.pulse(0.35f);
            star.spin(1f);
        }
        // Four blades fall out of the corona and bite into the Course around his landing point.
        if (t == 90) {
            for (int i = 0; i < 4; i++) {
                HeraldRig.Blade b = rig.blade(i);
                b.free = true;
                b.pos.set(DyingStar.CENTER).add(HMath.ring(7f, i * HMath.HALF_PI + 0.4f, 0f));
                b.rot.set(new Quaternionf().rotateX(HMath.PI));
                b.scale = 1f;
            }
            score.play(Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);
        }
        if (t >= 90 && t < 112) {
            float f = HMath.inQuad(HMath.window(t, 90, 110));
            for (int i = 0; i < 4; i++) {
                HeraldRig.Blade b = rig.blade(i);
                Vector3f from = new Vector3f(DyingStar.CENTER).add(HMath.ring(7f, i * HMath.HALF_PI + 0.4f, 0f));
                Vector3f to = new Vector3f(rig.root).add(HMath.ring(3.2f, i * HMath.HALF_PI + HMath.PI / 4f, 2.1f));
                b.pos.set(from).lerp(to, f);
                b.rot.set(new Quaternionf().rotateX(HMath.PI).rotateZ((1f - f) * 3f));
            }
            if (t == 110) {
                for (int i = 0; i < 4; i++) {
                    Vector3f at = new Vector3f(rig.root).add(HMath.ring(3.2f, i * HMath.HALF_PI + HMath.PI / 4f, 0f));
                    score.at(at, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.7f);
                    score.at(at, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
                    enc.arena().crackCell((int) Math.floor(at.x), (int) Math.floor(at.z), 0.6f, 200);
                }
                enc.camera().shakeAll(6, 2);
            }
        }
        // Armor flies in from the debris belt, feet first.
        if (t == 118) {
            rig.visible(true);
            rig.beginAssembly(DyingStar.CENTER, 11f);
            score.sweep(null, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1f, 0.6f, 1.4f, 70, 6);
        }
        if (t >= 118 && t <= 190) {
            float f = HMath.window(t, 118, 190);
            rig.assembly(f);
            if (t % 6 == 0) {
                score.at(rig.chestPoint(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.9f, 0.6f + f * 0.9f);
            }
        }
        if (t == 192) {
            rig.assembly(1f);
            rig.coreHeat(1f);
            rig.flare(15);
            enc.camera().flash(2, 8);
            score.play(Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            score.play(Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            score.chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, Score.semi(-12), Score.semi(-5), Score.semi(0));
            rig.pose(HeraldRig.Pose.raise(), 0.3f);
        }
        // The blades tear out of the floor and settle on his back as wings.
        if (t == 200) {
            for (int i = 0; i < 4; i++) {
                rig.blade(i).free = false;
            }
            score.play(Sound.ITEM_TRIDENT_RETURN, 1f, 0.8f);
            rig.pose(HeraldRig.Pose.guard(), 0.15f);
        }
        if (t == 205) {
            for (Player p : enc.audience()) {
                p.showTitle(net.kyori.adventure.title.Title.title(
                        TextUtil.component("&6&lTHE HERALD"),
                        TextUtil.component("&7Warden of the dying star"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                                java.time.Duration.ofMillis(2600), java.time.Duration.ofMillis(700))));
            }
            enc.bars().visible(true);
            enc.bars().fillOver(40);
            score.chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, Score.semi(0), Score.semi(3), Score.semi(7));
        }
        faceParty(0.05f);
        if (t >= INTRO) {
            rig.pose(HeraldRig.Pose.idle(), 0.15f);
            enc.tempo().bpm(enc.config().bpm("herald", 90), 40);
            enterFight();
            enc.fightStarted();
        }
    }

    @Override
    protected void tickFight() {
        if (stunned > 0) {
            stunned--;
        }
        if (hitReact > 0) {
            hitReact--;
        }
        if (mirrorTick >= 0) {
            tickMirror();
            return;
        }
        // Second mirror at 20 % (once): he has learned the trick.
        if (!mirrorDone && mirrorRounds == 1 && instance.healthPercent() <= 20.0 && !bodyBusy()) {
            startMirror();
            return;
        }
        if (opening()) {
            faceParty(0.03f);
        } else if (!bodyBusy()) {
            idleMove();
        }
        // The star's heartbeat drives the core; the enrage speeds the whole piece up.
        if (enc.tempo().onBeat()) {
            rig.coreHeat(0.6f + 0.4f * enc.star().beat());
            if (enc.tempo().onDownbeat()) {
                enc.score().play(Sound.ENTITY_WARDEN_HEARTBEAT, 0.35f, 0.9f);
            }
        }
        if (enc.enraged() && modeTick % 200 == 0) {
            enc.tempo().bpm(Math.min(140, enc.tempo().bpm() + 6), 40);
            enc.star().pulse(0.6f);
        }
    }

    /** Between moves: face the party, glide to a station on the Crown, breathe. */
    private void idleMove() {
        if (modeTick >= stationAt) {
            Vector3f c = partyCentroid();
            float a = HMath.angleOf(c.x, c.z) + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.4f;
            station.set(HMath.ring(STATION_R, a, 0f));
            stationAt = modeTick + enc.tempo().ticks(6);
        }
        Vector3f to = new Vector3f(station).sub(rig.root);
        float d = to.length();
        if (d > 0.1f) {
            rig.root.add(to.mul(Math.min(1f, 0.09f / d)));
        }
        faceParty(0.12f);
        HeraldRig.Pose p = HeraldRig.Pose.idle();
        p.hover = 0.45f + 0.12f * (float) Math.sin(clock * 0.08f);
        if (hitReact > 0) {
            p.lean = -0.25f;
        }
        rig.pose(p, 0.15f);
    }

    Vector3f partyCentroid() {
        Vector3f c = new Vector3f();
        List<Player> f = enc.fighters();
        if (f.isEmpty()) {
            return new Vector3f(0f, 0f, 20f);
        }
        for (Player p : f) {
            c.add(enc.stage().feet(p));
        }
        return c.div(f.size());
    }

    void faceParty(float step) {
        Player near = null;
        float best = Float.MAX_VALUE;
        for (Player p : enc.audience()) {
            float d = enc.stage().feet(p).distanceSquared(rig.root);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near != null) {
            rig.faceToward(enc.stage().feet(near), step);
        }
    }

    @Override
    protected void tickBody() {
        if (rig == null) {
            return;
        }
        // The Herald is pushed every tick: snappy (interp 1) while a move needs it, otherwise with one
        // tick of overlap so his glide never freezes on a late packet. Clones every second tick.
        boolean fast = fastRender || mode == Mode.DYING || (mode == Mode.INTRO && modeTick > 110);
        if (mirrorTick >= 0 && !fast) {
            // Mirror: the real one moves exactly like the clones (same cadence), only its heart gives it away.
            if (clock % 2 == 0) {
                rig.render(HeliosStage.SMOOTH_2);
            }
        } else {
            rig.render(fast ? 1 : HeliosStage.SMOOTH_1);
        }
        for (int i = 0; i < clones.size(); i++) {
            if (clock % 2 == i % 2) {
                clones.get(i).render(HeliosStage.SMOOTH_2);
            }
        }
        if (mirrorTick >= 0 && realIndex >= 0 && realIndex < mirrorSpots.size()) {
            hitbox.set(realRig().root).add(0f, 0.4f, 0f);
        } else {
            hitbox.set(rig.root).add(0f, 0.35f + rig.current().hover * 0.5f, 0f);
        }
    }

    @Override
    protected void onPhase(String from, String to) {
        if ("mirror".equalsIgnoreCase(to) && mirrorRounds == 0) {
            startMirror();
        }
    }

    @Override
    protected double reshape(Player player, double amount) {
        hitReact = 4;
        rig.flare(15);
        if (mirrorTick >= 0) {
            // The real one is hit: the illusion breaks.
            revealMirror(player);
            return amount * 1.5;
        }
        return stunned > 0 ? amount * 1.5 : amount;
    }

    /** Act I pays nothing: Act II's reliquary pays for both (Herald damage counts 30 % there). */
    @Override
    public boolean paysLoot() {
        return false;
    }

    @Override
    protected boolean paused() {
        return mirrorTick >= 0;
    }

    @Override
    protected boolean shielded() {
        // During the mirror game only the real one can be hurt, and only via its own hitbox.
        return false;
    }

    @Override
    protected int maxConcurrent() {
        double hp = instance.healthPercent();
        if (enc.enraged()) {
            return 3;
        }
        // His own move plus one hazard of another family; below half, two hazards.
        return hp > 50 ? 2 : 3;
    }

    @Override
    protected int flurryLength() {
        return instance.healthPercent() > 50 ? 3 : 4;
    }

    /** He chains: the next move counts in on the very next beat. */
    @Override
    protected int breathBeats() {
        return 0;
    }

    /** He opens as soon as his own move is done; a running cage or swarm keeps going around him. */
    @Override
    protected boolean openingWaitsForHazards() {
        return false;
    }

    /** Short breather, not a nap: the Opening is a bonus-damage window, he is never untouchable. */
    @Override
    protected int openingBeats() {
        return Math.max(2, cfgInt("pacing.opening-beats", 4));
    }

    /** Spent: he sinks to one knee, the blades droop, the core gutters. Hit him. */
    @Override
    protected void onOpening(boolean open) {
        if (open) {
            HeraldRig.Pose p = HeraldRig.Pose.kneel();
            p.crouch = 0.7f;
            p.lean = 0.45f;
            p.hover = 0.05f;
            rig.pose(p, 0.2f);
            rig.coreHeat(0.15f);
            rig.flare(6);
            rig.root.y = 0f;
            enc.score().at(rig.chestPoint(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.6f);
            enc.score().at(rig.chestPoint(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.4f);
        } else {
            rig.pose(HeraldRig.Pose.guard(), 0.15f);
            rig.coreHeat(0.6f);
            rig.flare(15);
            enc.score().at(rig.chestPoint(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        }
    }

    @Override
    protected Vector3f openingSpot() {
        return new Vector3f(rig.root.x, 0f, rig.root.z);
    }

    /* ================================================================== moves */

    @Override
    protected List<Choice> choices() {
        double hp = instance.healthPercent();
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("blink", () -> new BlinkStrike(this, enc.enraged() ? 3 : hp < 50 ? 2 : 1), 5, 8, Attack.Family.BODY));
        out.add(new Choice("swarm", () -> new BladeSwarm(this, null), 4, 12, Attack.Family.SWEEP));
        if (hp < 90) {
            out.add(new Choice("starfall", () -> new Starfall(this, enc.enraged() ? 3 : hp < 50 ? 2 : 1), 3, 22, Attack.Family.BODY));
        }
        if (hp < 90) {
            out.add(new Choice("platform", () -> new PlatformFall(this), 3, 32, Attack.Family.ARENA));
        }
        if (hp < 80) {
            out.add(new Choice("cage", () -> new LaserCage(this), 3, 36, Attack.Family.GROUND));
        }
        if (hp < 70) {
            out.add(new Choice("lance", () -> new SunLance(this), 2, 44, Attack.Family.BODY));
        }
        return out;
    }

    @Override
    protected List<Choice> allChoices() {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("blink", () -> new BlinkStrike(this, 1), 1, 1, Attack.Family.BODY));
        out.add(new Choice("blink3", () -> new BlinkStrike(this, 3), 1, 1, Attack.Family.BODY));
        out.add(new Choice("swarm", () -> new BladeSwarm(this, null), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("spiral", () -> new BladeSwarm(this, BladeSwarm.Formation.SPIRAL), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("fan", () -> new BladeSwarm(this, BladeSwarm.Formation.FAN), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("pincer", () -> new BladeSwarm(this, BladeSwarm.Formation.PINCER), 1, 1, Attack.Family.SWEEP));
        out.add(new Choice("cage", () -> new LaserCage(this), 1, 1, Attack.Family.GROUND));
        out.add(new Choice("platform", () -> new PlatformFall(this), 1, 1, Attack.Family.ARENA));
        out.add(new Choice("lance", () -> new SunLance(this), 1, 1, Attack.Family.BODY));
        out.add(new Choice("starfall", () -> new Starfall(this, 1), 1, 1, Attack.Family.BODY));
        out.add(new Choice("starfall2", () -> new Starfall(this, 2), 1, 1, Attack.Family.BODY));
        return out;
    }

    /* ================================================================== mirror */

    private HeraldRig realRig() {
        return realIndex == 0 ? rig : clones.get(realIndex - 1);
    }

    private void startMirror() {
        endAttacks();
        mirrorRounds++;
        mirrorTick = 0;
        mirrorSpots.clear();
        float base = ThreadLocalRandom.current().nextFloat() * HMath.TAU;
        for (int i = 0; i < 4; i++) {
            mirrorSpots.add(HMath.ring(19.5f, base + i * HMath.HALF_PI, 0f));
        }
        int n = Math.max(1, Math.min(3, cfgInt("mirror.clones", 3)));
        for (int i = 0; i < n; i++) {
            HeraldRig c = new HeraldRig(enc.stage(), true);
            c.root.set(rig.root);
            c.face = rig.face;
            c.snap(HeraldRig.Pose.raise());
            c.visible(false);
            clones.add(c);
        }
        realIndex = ThreadLocalRandom.current().nextInt(n + 1);
        enc.score().play(Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 0.8f);
        enc.score().play(Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        rig.pose(HeraldRig.Pose.raise(), 0.3f);
        for (Player p : enc.audience()) {
            p.sendActionBar(TextUtil.component("&eOnly one of them beats in time with the star."));
        }
    }

    private void tickMirror() {
        int t = mirrorTick++;
        List<HeraldRig> all = allRigs();
        // 0-30: he rises into the star's light and splits.
        if (t < 30) {
            float f = HMath.window(t, 0, 30);
            rig.root.y = HMath.outCubic(f) * 4f;
            return;
        }
        if (t == 30) {
            enc.camera().flash(1, 5);
            enc.score().play(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            placeMirror(all, true);
            spawnCloneBoxes();
        }
        // Every 8 beats the four strike together (blink + slash from each), then shuffle.
        int beatLen = Math.max(1, enc.tempo().ticks(8));
        int local = t - 30;
        if (local > 0 && local % beatLen == 0) {
            for (int i = 0; i < all.size(); i++) {
                Player target = pickTarget(false);
                if (target != null) {
                    MirrorVolley.strike(this, all.get(i), target, i == realIndex);
                }
            }
        }
        if (local > 0 && local % beatLen == beatLen / 2) {
            shuffleMirror(false);
        }
        // The real one's heart is audible, on the beat, from where it stands.
        if (enc.tempo().onBeat()) {
            enc.score().at(new Vector3f(realRig().root).add(0f, 2f, 0f), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1f);
        }
        for (HeraldRig r : all) {
            Player near = null;
            float best = Float.MAX_VALUE;
            for (Player p : enc.audience()) {
                float d = enc.stage().feet(p).distanceSquared(r.root);
                if (d < best) {
                    best = d;
                    near = p;
                }
            }
            if (near != null) {
                r.faceToward(enc.stage().feet(near), 0.12f);
            }
        }
        int window = Math.max(10, cfgInt("mirror.window-seconds", 30)) * 20;
        if (local > window) {
            // Nobody found him: all four detonate, then he gathers himself again.
            for (HeraldRig r : all) {
                MirrorVolley.nova(this, r.root, power("mirror.punish", 60));
            }
            endMirror();
        }
    }

    private List<HeraldRig> allRigs() {
        List<HeraldRig> all = new ArrayList<>();
        all.add(rig);
        all.addAll(clones);
        return all;
    }

    private void placeMirror(List<HeraldRig> all, boolean instant) {
        // Rig i stands on spot order[i]; the real rig is whichever lands on realIndex.
        for (int i = 0; i < all.size(); i++) {
            HeraldRig r = all.get(i);
            Vector3f spot = mirrorSpots.get(i % mirrorSpots.size());
            r.root.set(spot);
            r.visible(true);
            r.snap(HeraldRig.Pose.guard());
            r.cut();
        }
    }

    private void spawnCloneBoxes() {
        removeCloneBoxes();
        List<HeraldRig> all = allRigs();
        for (int i = 0; i < all.size(); i++) {
            if (i == realIndex) {
                cloneBoxes.add(null);
                continue;
            }
            HeraldRig r = all.get(i);
            Interaction box = enc.stage().world().spawn(enc.stage().at(new Vector3f(r.root).add(0f, 0.35f, 0f)), Interaction.class, it -> {
                it.setPersistent(false);
                it.setInteractionWidth(1.0f);
                it.setInteractionHeight(3.0f);
                it.setResponsive(true);
                module.keys().tagBeamFx(it, enc.stage().owner());
            });
            cloneBoxes.add(box);
        }
    }

    private void removeCloneBoxes() {
        for (Interaction i : cloneBoxes) {
            if (i != null && i.isValid()) {
                i.remove();
            }
        }
        cloneBoxes.clear();
    }

    /** All four blink out and come back on shuffled spots. */
    private void shuffleMirror(boolean punish) {
        List<HeraldRig> all = allRigs();
        java.util.Collections.shuffle(mirrorSpots);
        for (HeraldRig r : all) {
            enc.score().at(new Vector3f(r.root).add(0f, 1.5f, 0f), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.6f);
        }
        placeMirror(all, true);
        spawnCloneBoxes();
    }

    @Override
    public boolean onPropHit(Player player, Entity prop) {
        if (mirrorTick < 0) {
            return false;
        }
        for (int i = 0; i < cloneBoxes.size(); i++) {
            Interaction box = cloneBoxes.get(i);
            if (box != null && box.equals(prop)) {
                HeraldRig fake = allRigs().get(i);
                MirrorVolley.shatter(this, fake);
                MirrorVolley.nova(this, fake.root, power("mirror.punish", 60));
                player.sendActionBar(TextUtil.component("&cFalse light."));
                shuffleMirror(true);
                return true;
            }
        }
        return false;
    }

    private void revealMirror(Player by) {
        if (mirrorTick < 0) {
            return;
        }
        for (int i = 0; i < clones.size(); i++) {
            MirrorVolley.shatter(this, clones.get(i));
        }
        HeraldRig real = realRig();
        if (real != rig) {
            // Swap: the body we keep is always "rig", so move it to where the real one stood (a cut, no slide).
            rig.root.set(real.root);
            rig.face = real.face;
            rig.cut();
        }
        enc.score().play(Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        enc.score().chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, Score.semi(0), Score.semi(7));
        enc.camera().shakeAll(6, 2);
        stunned = enc.tempo().ticks(4);
        rig.pose(HeraldRig.Pose.kneel(), 0.25f);
        endMirror();
        holdScheduler(enc.tempo().ticks(4));
        for (Player p : enc.audience()) {
            p.sendActionBar(TextUtil.component("&6" + by.getName() + " &efound the true heart."));
        }
    }

    private void endMirror() {
        mirrorTick = -1;
        removeCloneBoxes();
        for (HeraldRig c : clones) {
            c.clear();
        }
        clones.clear();
        realIndex = 0;
        rig.visible(true);
        if (mirrorRounds >= 2) {
            mirrorDone = true;
        }
    }

    /* ================================================================== death */

    @Override
    protected void onDeathStart() {
        if (mirrorTick >= 0) {
            endMirror();
        }
        enc.cinematic(true);
        enc.bars().visible(false);
        enc.score().silence(30);
        rig.pose(HeraldRig.Pose.kneel(), 0.12f);
        rig.coreHeat(0.2f);
        for (int i = 0; i < 4; i++) {
            HeraldRig.Blade b = rig.blade(i);
            b.free = true;
        }
    }

    @Override
    protected boolean tickDeath() {
        int t = modeTick;
        Score score = enc.score();
        DyingStar star = enc.star();
        Vector3f starC = star.center();
        // 0-30: he sinks to his knees; the blades fall and clatter onto the floor.
        if (t < 30) {
            rig.root.y = Math.max(0f, rig.root.y - 0.1f);
            for (int i = 0; i < 4; i++) {
                HeraldRig.Blade b = rig.blade(i);
                Vector3f rest = new Vector3f(rig.root).add(HMath.ring(1.6f + i * 0.35f, rig.face + i * 1.7f, 0.05f));
                b.pos.lerp(rest, 0.2f);
                b.rot.slerp(new Quaternionf().rotateZ(HMath.HALF_PI).rotateY(i * 0.8f), 0.2f);
            }
            if (t == 16) {
                for (int i = 0; i < 4; i++) {
                    score.at(rig.blade(i).pos, Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f + i * 0.1f);
                }
                score.at(rig.root, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
            }
            return false;
        }
        // 30: the star turns its light on him: a gravity beam with rings running up it.
        if (t == 30) {
            var g = enc.stage().group();
            gravity = new Shapes.Beam(enc.stage(), g, Material.WHITE_CONCRETE, Material.ORANGE_STAINED_GLASS, DyingStar.GOLD);
            gravityRings = new Shapes.Ring[3];
            for (int i = 0; i < 3; i++) {
                gravityRings[i] = new Shapes.Ring(enc.stage(), g, 10, Material.YELLOW_STAINED_GLASS, DyingStar.GOLD, 15, true);
            }
            deathRef = new HeliosStageRef(g);
            score.sweep(null, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.2f, 0.5f, 1.6f, 70, 5);
            score.play(Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.5f);
            star.pulse(0.7f);
        }
        if (t >= 30 && t < 110) {
            float f = HMath.window(t, 30, 108);
            Vector3f chest = rig.chestPoint();
            gravity.set(starC, chest, 0.3f + 1.2f * HMath.smooth(Math.min(1f, f * 2f)), 2);
            Vector3f axis = new Vector3f(starC).sub(chest);
            Quaternionf orient = HMath.alignY(axis);
            for (int i = 0; i < gravityRings.length; i++) {
                float along = ((t * 0.035f) + i / 3f) % 1f;
                Vector3f at = new Vector3f(chest).lerp(starC, along);
                gravityRings[i].pose(at, orient, 1.4f * (1f - along) + 0.3f, 0.08f, 0.08f, t * 0.2f, 0f, 0f, 2);
            }
            // He is lifted, stretched toward the star, and swallowed piece by piece.
            rig.root.y = HMath.inQuad(f) * 5f;
            rig.swallow(starC, HMath.smooth(HMath.window(t, 40, 100)), HMath.inCubic(HMath.window(t, 60, 108)));
            for (int i = 0; i < 4; i++) {
                HeraldRig.Blade b = rig.blade(i);
                float bf = HMath.window(t, 50 + i * 8, 90 + i * 6);
                b.pos.lerp(starC, HMath.inCubic(bf) * 0.2f);
                b.scale = 1f - HMath.inCubic(bf);
            }
            if (t % 10 == 0) {
                enc.camera().shakeAll(10, 2);
            }
        }
        if (t == 108) {
            gravity.hide(2);
            for (Shapes.Ring r : gravityRings) {
                r.hide(starC, 2);
            }
            rig.visible(false);
            star.scale(1.45f, 0.08f);
            enc.camera().flash(4, 12);
            score.play(Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f, true);
            score.play(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.6f, true);
        }
        if (t == 114) {
            // Real silence: nothing plays for two seconds.
            score.silence(46);
            star.scale(1f, 0.02f);
            star.pulse(0.15f);
        }
        // Then a heartbeat, slow, then faster: the star is waking into something else.
        if (t == 160 || t == 178 || t == 192 || t == 202) {
            score.play(Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f, true);
            star.crack(HMath.window(t, 160, 205) * 0.6f + 0.1f);
        }
        if (t >= DEATH) {
            if (deathRef != null) {
                deathRef.group.clear();
            }
            rig.clear();
            enc.heraldFallen();
            return true;
        }
        return false;
    }

    @Override
    protected void clearBody() {
        removeCloneBoxes();
        for (HeraldRig c : clones) {
            c.clear();
        }
        clones.clear();
        if (rig != null) {
            rig.clear();
        }
        if (deathRef != null) {
            deathRef.group.clear();
        }
    }

    /** Holds the death cinematic's display group. */
    private record HeliosStageRef(de.aetherion.bossengine.helios.core.HeliosStage.Group group) {
    }

    /** Knockback away from a point, flat, with a little lift. */
    static Vector away(Vector3f from, Vector3f to, double strength) {
        Vector3f d = new Vector3f(to).sub(from);
        d.y = 0f;
        if (d.lengthSquared() < 1e-4f) {
            d.set(0f, 0f, 1f);
        }
        d.normalize();
        return new Vector(d.x * strength, 0.35, d.z * strength);
    }
}
