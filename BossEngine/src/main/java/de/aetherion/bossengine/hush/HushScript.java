package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossScript;
import de.aetherion.bossengine.instance.BossState;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.util.TextUtil;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hush: the dark that grows where the fifth dusk toll should be.
 *
 * <pre>
 *   INTRO     the lamps catch one by one, the dusk toll rings four times, and in the gap it forms
 *   FIGHT     it drifts after the party. In the dark it barely feels a blade; in lamplight it does.
 *             Snuff (it puts a lamp out; break the wind-up to stagger it), Grasp (hands from the floor,
 *             never inside lamplight), Hush (a soundless wave; only lamplight is safe), Veil Lunge (a
 *             parryable rush), Toll (a bell rings by itself; jump the ring)
 *   LULLABY   at 66 % and 33 %: every lamp dies, the bells play the hymn's opening, the party rings it
 *             back in order. The second time the song has five notes and the dark sits on the fifth.
 *   LULLED    the song held: it lies open for a few seconds
 *   DYING     the bells play all seven notes and the dusk toll rings five times. The fifth ends it.
 * </pre>
 *
 * Rule of the fight: everything made of dark (dread, hands, the wave, the echoes' chill) stops at the
 * edge of a burning lamp. Its body and the bells' own sound do not.
 */
final class HushScript implements BossScript {

    enum Mode { NONE, INTRO, FIGHT, LULLABY, LULLED, DYING, DONE }

    /** The seven notes of the old hymn (note-block pitches), lowest first. */
    static final float[] HYMN = {0.749f, 0.841f, 0.944f, 1.122f, 1.26f, 1.498f, 1.682f};

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 208, 110), 1.3f);
    private static final Particle.DustOptions ASHEN = new Particle.DustOptions(Color.fromRGB(70, 66, 78), 1.5f);
    private static final Particle.DustOptions PITCH = new Particle.DustOptions(Color.fromRGB(12, 10, 16), 1.8f);
    private static final double HOVER = 0.4;
    private static final double REACH = HushArena.RADIUS - 2.0;

    private final HushModule module;
    private final BossInstance instance;
    private HushEncounter enc;
    private HushArena arena;
    private HushConfig cfg;
    private World world;
    private HushBody body;

    private Mode mode = Mode.NONE;
    private int modeTick;
    private int clock;
    private boolean orphan;
    private boolean stopped;
    private String phaseId = "";
    /** 0 Vespers, 1 the Long Night, 2 Compline. */
    private int chapter;

    private double x;
    private double y = HOVER;
    private double z;

    private Move bodyMove;
    private final List<Move> hazards = new ArrayList<>();
    private int nextMoveAt;
    private String lastMove = "";
    private int snuffReadyAt;
    private final int[] lampLockUntil = new int[HushArena.LAMPS];
    private int staggerUntil = -1;
    private double windupDamage = -1;
    private int fightTicks;
    private boolean enraged;
    private int touchAt;

    /** Which hymn note each bell plays (shuffled per night) and the inverse. */
    private final int[] noteOfBell = new int[HushArena.BELLS];
    private final int[] bellOfNote = new int[HushArena.BELLS];
    private final int[] bellReadyAt = new int[HushArena.BELLS];
    private int lullaby;
    private int lullabyGoal;
    private int lullabyProgress;
    private boolean answering;
    private int answerStart;
    private int answerDeadline;
    private int replayFrom = -1;
    private boolean saidFifth;
    private final List<Integer> songLamps = new ArrayList<>();

    private final List<Echo> echoes = new ArrayList<>();

    HushScript(HushModule module, BossInstance instance) {
        this.module = module;
        this.instance = instance;
    }

    /* ================================================================== engine hooks */

    @Override
    public void onBind() {
        LivingEntity hitbox = instance.getEntity();
        if (hitbox == null) {
            return;
        }
        prepareHitbox(hitbox);
        if (enc != null) {
            return;
        }
        enc = module.encounterAt(instance.getSpawnLocation());
        if (enc == null || enc.script() != null) {
            // Spawned outside a belfry (/boss spawn) or as a second body: there is nothing to fight in.
            enc = null;
            orphan = true;
            return;
        }
        arena = enc.arena();
        cfg = enc.config();
        world = arena.world();
        body = new HushBody(arena, false);
        enc.attach(this);
        BossPhase p = instance.getCurrentPhase();
        phaseId = p == null ? "" : p.getId();
        begin();
    }

    @Override
    public boolean tick() {
        if (orphan) {
            if (instance.getState() == BossState.ALIVE) {
                instance.setState(BossState.DESPAWNING);
            }
            return false;
        }
        if (enc == null || enc.closed() || stopped) {
            return false;
        }
        try {
            return step();
        } catch (RuntimeException ex) {
            module.plugin().getLogger().log(java.util.logging.Level.SEVERE, "[Hush] Slot " + enc.slot() + " crashed its script; closing it.", ex);
            HushEncounter failed = enc;
            module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> failed.close(false, "error"));
            stopped = true;
            return false;
        }
    }

    private boolean step() {
        clock++;
        modeTick++;
        LivingEntity hitbox = instance.getEntity();
        if (hitbox != null && hitbox.isValid()) {
            maintainHitbox(hitbox);
        }
        boolean finished = false;
        switch (mode) {
            case INTRO -> tickIntro();
            case FIGHT -> tickFight();
            case LULLABY -> tickLullaby();
            case LULLED -> tickLulled();
            case DYING -> finished = tickDeath();
            default -> {
            }
        }
        if (body != null) {
            body.moveTo(x, y, z);
            body.revealed(revealed());
            body.tick(mode == Mode.LULLED || mode == Mode.DYING || staggered() ? 0.7f : 0f);
        }
        tickEchoes();
        placeHitbox();
        if (finished) {
            mode = Mode.DONE;
        }
        return finished;
    }

    @Override
    public boolean beginDeath() {
        if (orphan || enc == null) {
            return false;
        }
        if (mode == Mode.DYING || mode == Mode.DONE) {
            return true;
        }
        endMoves();
        clearEchoes();
        mode = Mode.DYING;
        modeTick = 0;
        enc.cinematic(true);
        LivingEntity hitbox = instance.getEntity();
        if (hitbox != null) {
            hitbox.setInvulnerable(true);
        }
        return true;
    }

    @Override
    public boolean isDying() {
        return mode == Mode.DYING;
    }

    @Override
    public boolean blocksDamage() {
        return mode != Mode.FIGHT && mode != Mode.LULLED;
    }

    @Override
    public void abort() {
        endMoves();
        clearEchoes();
        if (body != null) {
            body.remove();
        }
        if (mode != Mode.DONE) {
            mode = Mode.NONE;
        }
        stopped = true;
    }

    @Override
    public boolean ownsBossBar() {
        return true;
    }

    @Override
    public double incoming(Player player, double amount) {
        if (enc == null || !enc.party().contains(player) || amount <= 0) {
            return -1;
        }
        double shaped = amount;
        if (mode == Mode.LULLED) {
            shaped *= cfg.d("lullaby.lulled-damage-multiplier", 1.6);
        } else if (!revealed()) {
            shaped *= cfg.d("fight.dark-damage-multiplier", 0.25);
            HushParty.Member m = enc.party().get(player);
            if (m != null && enc.clock() - m.lastHintAt > 100) {
                m.lastHintAt = enc.clock();
                player.sendActionBar(TextUtil.component(HushText.SLIPS.of(player)));
            }
        }
        if (windupDamage >= 0) {
            windupDamage += shaped;
        }
        return shaped;
    }

    /* ================================================================== what the encounter asks */

    boolean finished() {
        return mode == Mode.DONE;
    }

    /** A wipe: everything stops where it is until the encounter closes. */
    void stop() {
        endMoves();
        stopped = true;
    }

    boolean revealed() {
        if (mode == Mode.LULLED || mode == Mode.DYING || staggered()) {
            return true;
        }
        return mode == Mode.FIGHT && arena.inLight(x, z);
    }

    private boolean staggered() {
        return clock < staggerUntil;
    }

    boolean lampsCanBeLit() {
        return mode == Mode.FIGHT && !enraged;
    }

    void lampRelit(int lamp) {
        // A lamp lit by hand cannot be snuffed again at once: the party earns a moment of light.
        snuffReadyAt = Math.max(snuffReadyAt, clock + 60);
    }

    double dreadFactor() {
        return switch (mode) {
            case FIGHT -> (chapter >= 2 ? cfg.d("dread.compline-factor", 1.4) : 1.0) * (enraged ? 2.0 : 1.0);
            case LULLABY -> answering ? cfg.d("dread.lullaby-factor", 0.6) : 0.0;
            default -> 0.0;
        };
    }

    double damageFactor() {
        return enraged ? cfg.d("fight.enrage-damage-multiplier", 1.5) : 1.0;
    }

    boolean barsVisible() {
        return mode != Mode.NONE && mode != Mode.DONE && !(mode == Mode.INTRO && modeTick < 215)
                && !(mode == Mode.DYING && modeTick > 100);
    }

    boolean darkenScreen() {
        return mode == Mode.LULLABY || enraged;
    }

    HushText.T barLine() {
        if (mode == Mode.LULLABY) {
            return HushText.BAR_LULLABY.with(lullabyProgress, lullabyGoal);
        }
        int left = (cfg.enrageTicks() - fightTicks) / 20;
        if (!enraged && left <= 60 && left >= 0) {
            return HushText.BAR_ENRAGE.with(left);
        }
        if (enraged) {
            return HushText.ENRAGE;
        }
        return chapter >= 2 ? HushText.BAR_PHASE_COMPLINE : chapter == 1 ? HushText.BAR_PHASE_NIGHT : HushText.BAR_PHASE_VESPERS;
    }

    float barProgress() {
        if (mode == Mode.LULLABY) {
            if (!answering) {
                return 1f;
            }
            return Math.max(0f, (answerDeadline - clock) / (float) Math.max(1, answerDeadline - answerStart));
        }
        if (mode == Mode.LULLED) {
            return Math.max(0f, 1f - modeTick / (float) lulledTicks());
        }
        return (float) Math.max(0.0, Math.min(1.0, arena.litCount() / (double) HushArena.LAMPS));
    }

    BossBar.Color barColor() {
        if (mode == Mode.LULLABY || mode == Mode.LULLED) {
            return BossBar.Color.YELLOW;
        }
        return enraged ? BossBar.Color.RED : BossBar.Color.WHITE;
    }

    String debug() {
        return mode.name().toLowerCase(Locale.ROOT) + " ch" + chapter + " t" + modeTick
                + (bodyMove == null ? "" : " move " + bodyMove.id) + " hazards " + hazards.size()
                + (mode == Mode.LULLABY ? " song " + lullabyProgress + "/" + lullabyGoal : "")
                + (enraged ? " enraged" : "");
    }

    /* ================================================================== intro */

    private void begin() {
        // Which bell holds which note changes every night, so the song has to be listened to.
        List<Integer> notes = new ArrayList<>();
        for (int i = 0; i < HushArena.BELLS; i++) {
            notes.add(i);
        }
        Collections.shuffle(notes);
        for (int bell = 0; bell < HushArena.BELLS; bell++) {
            noteOfBell[bell] = notes.get(bell);
            bellOfNote[notes.get(bell)] = bell;
        }
        for (int i = 0; i < HushArena.LAMPS; i++) {
            arena.setLamp(i, false);
        }
        x = 0;
        z = 0;
        y = HOVER;
        mode = Mode.INTRO;
        modeTick = 0;
        enc.cinematic(true);
    }

    private void tickIntro() {
        int t = modeTick;
        // Old law: the lamps stay lit till dawn. They catch one after another.
        if (t >= 12 && t < 12 + HushArena.LAMPS * 8 && (t - 12) % 8 == 0) {
            enc.relight((t - 12) / 8, null);
        }
        if (t == 50) {
            HushText.say(enc.audience(), HushText.SAY_INTRO);
        }
        // The dusk toll. Four strokes.
        if (t >= 84 && t <= 84 + 3 * 24 && (t - 84) % 24 == 0) {
            toll(1f);
        }
        if (t == 84 + 4 * 24) {
            // ...and where the fifth belongs, it stands.
            for (Player p : enc.audience()) {
                p.stopAllSounds();
            }
            body.spawn(x, y, z);
            world.playSound(arena.at(0, 2, 0), Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 2.2f, 0.5f);
        }
        if (t == 84 + 4 * 24 + 2) {
            body.grow(1f, 46);
        }
        if (t > 84 + 4 * 24 && t < 236 && t % 2 == 0) {
            double a = t * 0.35;
            double r = 3.2 - (t - 180) * 0.04;
            world.spawnParticle(Particle.LARGE_SMOKE, arena.at(Math.cos(a) * r, 0.3 + (t - 180) * 0.06, Math.sin(a) * r), 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticle(Particle.SQUID_INK, arena.at(-Math.cos(a) * r, 0.2, -Math.sin(a) * r), 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (t == 196) {
            HushText.say(enc.audience(), HushText.SAY_FOUR);
        }
        if (t == 216) {
            HushText.title(enc.audience(), HushText.NAME, HushText.TITLE_SUB, 10, 50, 20);
            world.playSound(arena.at(0, 2, 0), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 3f, 0.5f);
        }
        if (t >= 270) {
            HushText.send(enc.audience(), HushText.HINT_LIGHT);
            HushText.send(enc.audience(), HushText.HINT_RELIGHT);
            enterFight(60);
        }
    }

    /** One stroke of the dusk toll, from above the middle of the belfry. */
    private void toll(float pitchScale) {
        Location at = arena.at(0, 9, 0);
        world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.RECORDS, 5f, 0.5f * pitchScale);
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.RECORDS, 4f, 0.6f * pitchScale);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 4f, 0.5f);
        for (double[] p : HushArena.ring(2.0, 16, clock * 0.1)) {
            world.spawnParticle(Particle.DUST, arena.at(p[0], 8.5, p[1]), 1, 0, 0, 0, 0, GOLD);
        }
    }

    /* ================================================================== fight */

    private void enterFight(int breath) {
        mode = Mode.FIGHT;
        modeTick = 0;
        y = HOVER;
        answering = false;
        enc.cinematic(false);
        nextMoveAt = clock + breath;
    }

    private void tickFight() {
        fightTicks++;
        if (checkPhase()) {
            return;
        }
        if (!enraged && fightTicks > cfg.enrageTicks()) {
            enrage();
        }
        if (staggered()) {
            stepHazards();
            return;
        }
        if (bodyMove != null) {
            if (bodyMove.run()) {
                bodyMove.end();
                nextMoveAt = Math.max(nextMoveAt, clock + bodyMove.recovery + gap());
                bodyMove = null;
            }
        } else {
            drift();
            pick();
        }
        stepHazards();
        touch();
    }

    private int gap() {
        int base = chapter >= 2 ? cfg.i("pacing.gap-ticks-compline", 30)
                : chapter == 1 ? cfg.i("pacing.gap-ticks-night", 42) : cfg.i("pacing.gap-ticks-vespers", 55);
        return enraged ? base / 2 : base;
    }

    /** It follows the nearest fighter, slowly; it has all night. */
    private void drift() {
        Player target = nearestFighter(x, z);
        if (target == null) {
            return;
        }
        double tx = arena.localX(target.getLocation());
        double tz = arena.localZ(target.getLocation());
        double dx = tx - x;
        double dz = tz - z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 3.0) {
            double speed = chapter >= 2 ? cfg.d("fight.drift-speed-compline", 0.13) : cfg.d("fight.drift-speed", 0.09);
            moveBy(dx / d * speed, dz / d * speed);
        }
    }

    private void moveBy(double dx, double dz) {
        double[] c = HushArena.clamp(x + dx, z + dz, REACH);
        x = c[0];
        z = c[1];
    }

    /** Its cold: standing against it in the dark hurts a little and frightens a lot. */
    private void touch() {
        if (clock < touchAt) {
            return;
        }
        touchAt = clock + 10;
        for (Player p : enc.fighters()) {
            double px = arena.localX(p.getLocation());
            double pz = arena.localZ(p.getLocation());
            if (sq(px - x, pz - z) <= 2.3 * 2.3 && !arena.inLight(px, pz)) {
                enc.addDread(p, 6.0);
                enc.hit(p, cfg.d("moves.touch.power", 22.0), "touch", 30, null, false);
            }
        }
    }

    private void pick() {
        if (clock < nextMoveAt) {
            return;
        }
        List<Player> fighters = enc.fighters();
        if (fighters.isEmpty()) {
            nextMoveAt = clock + 20;
            return;
        }
        List<String> pool = new ArrayList<>();
        int lit = arena.litCount();
        if (lit > 0 && clock >= snuffReadyAt && !enraged) {
            add(pool, "snuff", 3);
        }
        add(pool, "lunge", 3);
        if (lit > 0) {
            add(pool, "hush", 2);
        }
        if (hazards.size() < (chapter >= 2 ? 2 : 1)) {
            add(pool, "grasp", 3);
            if (chapter >= 1) {
                add(pool, "toll", 3);
            }
        }
        pool.removeIf(id -> id.equals(lastMove));
        if (pool.isEmpty()) {
            nextMoveAt = clock + 20;
            return;
        }
        start(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
    }

    private static void add(List<String> pool, String id, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.add(id);
        }
    }

    /** Starts a move by id (scheduler pick or admin test). @return false for an unknown id */
    boolean start(String id) {
        if (mode != Mode.FIGHT) {
            return false;
        }
        Move move = switch (id.toLowerCase(Locale.ROOT)) {
            case "snuff" -> new Snuff();
            case "lunge" -> new Lunge();
            case "hush" -> new Hush();
            case "grasp" -> new Grasp();
            case "toll" -> new Toll();
            default -> null;
        };
        if (move == null) {
            return false;
        }
        lastMove = move.id;
        if (move.usesBody) {
            if (bodyMove != null) {
                bodyMove.end();
            }
            bodyMove = move;
        } else {
            hazards.add(move);
            nextMoveAt = clock + gap();
        }
        return true;
    }

    static List<String> moveIds() {
        return List.of("snuff", "lunge", "hush", "grasp", "toll");
    }

    private void stepHazards() {
        for (Iterator<Move> it = hazards.iterator(); it.hasNext(); ) {
            Move m = it.next();
            if (m.run()) {
                m.end();
                it.remove();
            }
        }
    }

    private void endMoves() {
        if (bodyMove != null) {
            bodyMove.end();
            bodyMove = null;
        }
        for (Move m : hazards) {
            m.end();
        }
        hazards.clear();
        windupDamage = -1;
    }

    private void enrage() {
        enraged = true;
        HushText.title(enc.audience(), HushText.ENRAGE, null, 5, 50, 20);
        world.playSound(arena.at(0, 2, 0), Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 4f, 0.5f);
        for (int i = 0; i < HushArena.LAMPS; i++) {
            enc.snuff(i, false);
        }
    }

    /** @return true when a gate was crossed and the lullaby took the stage */
    private boolean checkPhase() {
        BossPhase p = instance.getCurrentPhase();
        String now = p == null ? "" : p.getId();
        if (now.equals(phaseId)) {
            return false;
        }
        phaseId = now;
        int which = "compline".equalsIgnoreCase(now) ? 2 : 1;
        if (which <= chapter) {
            return false;
        }
        beginLullaby(which);
        return true;
    }

    /* ================================================================== the lullaby */

    private void beginLullaby(int which) {
        endMoves();
        staggerUntil = -1;
        mode = Mode.LULLABY;
        modeTick = 0;
        lullaby = which;
        lullabyGoal = which == 1 ? 4 : 5;
        lullabyProgress = 0;
        answering = false;
        replayFrom = -1;
        songLamps.clear();
        enc.cinematic(true);
        HushText.say(enc.audience(), HushText.SAY_LULLABY);
        HushText.title(enc.audience(), null, HushText.LULLABY_LISTEN, 5, 60, 10);
        world.playSound(arena.at(0, 3, 0), Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 3f, 0.6f);
    }

    private static final int LISTEN_FROM = 56;
    private static final int LISTEN_STEP = 22;

    private void tickLullaby() {
        int t = modeTick;
        // It goes up into the dark above the middle and takes the lamps with it.
        if (t <= 40) {
            x += (0 - x) * 0.12;
            z += (0 - z) * 0.12;
            y += (6.0 - y) * 0.1;
        }
        if (t >= 6 && t < 6 + HushArena.LAMPS * 4 && (t - 6) % 4 == 0) {
            enc.snuff((t - 6) / 4, false);
        }
        if (!answering) {
            int k = (t - LISTEN_FROM) / LISTEN_STEP;
            if (t >= LISTEN_FROM && (t - LISTEN_FROM) % LISTEN_STEP == 0 && k < lullabyGoal) {
                teach(k);
            }
            if (t >= LISTEN_FROM + lullabyGoal * LISTEN_STEP + 10) {
                answering = true;
                answerStart = clock;
                answerDeadline = clock + cfg.i(lullaby == 1 ? "lullaby.answer-seconds" : "lullaby.answer-seconds-fifth", lullaby == 1 ? 30 : 40) * 20;
                enc.cinematic(false);
                int secs = (answerDeadline - clock) / 20;
                HushText.title(enc.audience(), null, lullaby == 1 ? HushText.LULLABY_ANSWER.with(secs) : HushText.LULLABY_FIFTH, 5, 70, 10);
            }
            return;
        }
        // A wrong note plays the opening again, a little faster.
        if (replayFrom >= 0) {
            int rt = clock - replayFrom;
            if (rt % 14 == 0 && rt / 14 < lullabyGoal) {
                teach(rt / 14);
            } else if (rt / 14 >= lullabyGoal) {
                replayFrom = -1;
            }
        }
        if (lullaby == 2 && clock % 4 == 0) {
            shadowBell(bellOfNote[4]);
        }
        // Hands keep coming while the song is unfinished.
        if ((clock - answerStart) % cfg.i("lullaby.grasp-every-ticks", 110) == 60 && hazards.isEmpty()) {
            hazards.add(new Grasp());
        }
        stepHazards();
        if (clock >= answerDeadline) {
            failLullaby();
        }
    }

    /** Note {@code k} of the opening: its bell shows itself. The fifth note of the second song does not. */
    private void teach(int k) {
        if (k >= 4) {
            Location mouth = arena.bellMouth(bellOfNote[k]);
            world.playSound(mouth, Sound.BLOCK_SCULK_SPREAD, SoundCategory.RECORDS, 3f, 0.5f);
            world.spawnParticle(Particle.SQUID_INK, mouth, 30, 0.5, 0.6, 0.5, 0.02);
            return;
        }
        chime(bellOfNote[k], k, 3.5f);
    }

    private void chime(int bell, int note, float volume) {
        Location mouth = arena.bellMouth(bell);
        world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, volume, HYMN[note]);
        world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, volume, HYMN[note]);
        world.spawnParticle(Particle.NOTE, mouth.clone().add(0, 0.9, 0), 3, 0.3, 0.2, 0.3, 1.0);
        for (int i = 0; i < 14; i++) {
            world.spawnParticle(Particle.END_ROD, mouth.clone().add(0, 0.4 + i * 0.6, 0), 1, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticle(Particle.DUST, mouth, 16, 0.6, 0.6, 0.6, 0, GOLD);
    }

    private void shadowBell(int bell) {
        Location mouth = arena.bellMouth(bell);
        world.spawnParticle(Particle.LARGE_SMOKE, mouth, 3, 0.45, 0.5, 0.45, 0.005);
        world.spawnParticle(Particle.DUST, mouth.clone().add(0, 0.8, 0), 3, 0.4, 0.5, 0.4, 0, PITCH);
    }

    /** A participant struck bell {@code bell}. */
    void ringBell(Player p, int bell) {
        if (clock < bellReadyAt[bell]) {
            return;
        }
        bellReadyAt[bell] = clock + 8;
        int note = noteOfBell[bell];
        if (mode != Mode.LULLABY || !answering) {
            // Outside the song a bell is just a bell: its own note, quietly.
            Location mouth = arena.bellMouth(bell);
            world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 1.2f, HYMN[note]);
            world.spawnParticle(Particle.NOTE, mouth.clone().add(0, 0.9, 0), 1, 0.2, 0.1, 0.2, 1.0);
            return;
        }
        if (note == lullabyProgress) {
            lullabyProgress++;
            chime(bell, note, 3.5f);
            // Every right note brings a lamp back: the one nearest its bell that is still out.
            int lamp = arena.nearestLamp(HushArena.bellX(bell), HushArena.bellZ(bell), false);
            if (lamp >= 0 && enc.relight(lamp, null)) {
                songLamps.add(lamp);
            }
            HushText.bar(enc.audience(), HushText.LULLABY_RIGHT.with(lullabyProgress, lullabyGoal));
            if (lullabyProgress >= lullabyGoal) {
                holdLullaby();
            }
            return;
        }
        // Wrong: the dark takes the note and the lamps the song had lit.
        boolean nearlyThere = lullaby == 2 && lullabyProgress == 4;
        lullabyProgress = 0;
        Location mouth = arena.bellMouth(bell);
        world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 3f, 0.5f);
        world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 3f, 0.53f);
        world.spawnParticle(Particle.SQUID_INK, mouth, 24, 0.5, 0.6, 0.5, 0.03);
        for (int lamp : songLamps) {
            enc.snuff(lamp, false);
        }
        songLamps.clear();
        for (Player f : enc.fighters()) {
            enc.addDread(f, cfg.d("lullaby.wrong-note-dread", 12.0));
        }
        HushText.bar(enc.audience(), HushText.LULLABY_WRONG);
        if (nearlyThere && !saidFifth) {
            saidFifth = true;
            HushText.say(enc.audience(), HushText.SAY_FIFTH);
        }
        replayFrom = clock + 16;
    }

    /** Admin: finish the running song as if every note had been rung. */
    boolean solve() {
        if (mode != Mode.LULLABY) {
            return false;
        }
        answering = true;
        lullabyProgress = lullabyGoal;
        holdLullaby();
        return true;
    }

    private void holdLullaby() {
        endMoves();
        chapter = lullaby;
        mode = Mode.LULLED;
        modeTick = 0;
        answering = false;
        enc.cinematic(false);
        for (int i = 0; i < HushArena.LAMPS; i++) {
            enc.relight(i, null);
        }
        enc.reviveAll();
        for (Player f : enc.fighters()) {
            enc.addDread(f, -100.0);
        }
        HushText.title(enc.audience(), null, HushText.LULLABY_DONE, 5, 60, 15);
        HushText.say(enc.audience(), HushText.SAY_LULLED);
        world.playSound(arena.at(0, 2, 0), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.RECORDS, 3f, 1.4f);
        world.playSound(arena.at(0, 2, 0), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.RECORDS, 3f, 0.8f);
        for (double[] p : HushArena.ring(HushArena.LAMP_RING, 60, 0)) {
            world.spawnParticle(Particle.DUST, arena.at(p[0], 0.6, p[1]), 2, 0.1, 0.3, 0.1, 0, GOLD);
        }
    }

    private void failLullaby() {
        endMoves();
        chapter = lullaby;
        HushText.title(enc.audience(), HushText.LULLABY_FAIL, null, 5, 40, 15);
        HushText.say(enc.audience(), HushText.SAY_FAIL);
        world.playSound(arena.at(0, 2, 0), Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3f, 0.6f);
        for (Player f : enc.fighters()) {
            if (!arena.inLight(f.getLocation())) {
                enc.hit(f, cfg.d("lullaby.fail-power", 70.0), "nightfall", 20, null, false);
                enc.addDread(f, cfg.d("lullaby.fail-dread", 45.0));
            }
        }
        // Two lamps are left to start again from.
        enc.relight(0, null);
        enc.relight(HushArena.LAMPS / 2, null);
        afterLullaby(40);
    }

    private int lulledTicks() {
        return Math.max(40, cfg.i("lullaby.lulled-seconds", 12) * 20);
    }

    private void tickLulled() {
        fightTicks++;
        // Sung down to the floor in the middle, open, shivering.
        x += (0 - x) * 0.15;
        z += (0 - z) * 0.15;
        y += (HOVER - y) * 0.2;
        if (clock % 6 == 0) {
            world.spawnParticle(Particle.NOTE, arena.at(x + rnd(1.5), y + 3 + rnd(1.5), z + rnd(1.5)), 1, 0, 0, 0, 1.0);
        }
        if (modeTick >= lulledTicks() || checkPhase()) {
            if (mode == Mode.LULLED) {
                afterLullaby(30);
            }
        }
    }

    private void afterLullaby(int breath) {
        enterFight(breath);
        if (chapter >= 2 && echoes.isEmpty()) {
            HushText.say(enc.audience(), HushText.SAY_COMPLINE);
            for (int i = 0; i < cfg.i("fight.echoes", 2); i++) {
                echoes.add(new Echo(i));
            }
        }
    }

    /* ================================================================== the fifth toll */

    private static final int HYMN_FROM = 30;
    private static final int TOLLS_FROM = 136;

    private boolean tickDeath() {
        int t = modeTick;
        x += (0 - x) * 0.1;
        z += (0 - z) * 0.1;
        y += (1.2 - y) * 0.08;
        if (t >= 4 && t < 4 + HushArena.LAMPS * 3 && (t - 4) % 3 == 0) {
            enc.relight((t - 4) / 3, null);
        }
        // The bells remember the whole song now: seven notes, in order.
        int k = (t - HYMN_FROM) / 13;
        if (t >= HYMN_FROM && (t - HYMN_FROM) % 13 == 0 && k < HYMN.length) {
            chime(bellOfNote[k], k, 4f);
        }
        // Then the dusk toll. One. Two. Three. Four.
        for (int j = 0; j < 4; j++) {
            if (t == TOLLS_FROM + j * 24) {
                toll(1f);
                lightRing();
                body.grow(1f - (j + 1) * 0.17f, 14);
            }
        }
        // ...and after the gap it grew in: five.
        int fifth = TOLLS_FROM + 4 * 24 + 18;
        if (t == fifth) {
            toll(1.25f);
            lightRing();
            lightRing();
            body.grow(0f, 16);
            Location chest = body.chest();
            world.spawnParticle(Particle.END_ROD, chest, 120, 1.4, 2.2, 1.4, 0.12);
            world.spawnParticle(Particle.WHITE_ASH, chest, 200, 3.0, 3.0, 3.0, 0.02);
            world.spawnParticle(Particle.FLASH, chest, 2, 0.2, 0.2, 0.2, 0.0);
            world.playSound(chest, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.RECORDS, 3f, 1.6f);
            HushText.title(enc.audience(), HushText.FIVE, HushText.FIVE_SUB, 10, 80, 30);
            for (Player p : enc.audience()) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.setPlayerTime(23_000L, false);
            }
        }
        if (t == fifth + 40) {
            arena.nightLight(true);
            world.spawnParticle(Particle.SMALL_FLAME, arena.at(0, 0.5, 0), 6, 0.05, 0.1, 0.05, 0.005);
            world.playSound(arena.at(0, 0.5, 0), Sound.ITEM_FIRECHARGE_USE, SoundCategory.RECORDS, 0.6f, 1.8f);
            HushText.send(enc.audience(), HushText.NIGHT_LIGHT);
        }
        stepHazards();
        if (t >= fifth + 70) {
            endMoves();
            body.remove();
            return true;
        }
        return false;
    }

    private void lightRing() {
        hazards.add(new LightRing());
    }

    /* ================================================================== hitbox */

    private void prepareHitbox(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
        }
        if (entity instanceof org.bukkit.entity.Ageable ageable) {
            // A baby zombie would halve the hitbox.
            ageable.setAdult();
        }
        entity.setGravity(false);
        entity.setInvisible(true);
        entity.setSilent(true);
        entity.setFireTicks(0);
        entity.setVelocity(new Vector());
        entity.setCollidable(false);
        if (entity.getEquipment() != null) {
            entity.getEquipment().clear();
        }
    }

    private void maintainHitbox(LivingEntity entity) {
        if (entity instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
        }
        entity.setFireTicks(0);
        entity.setFallDistance(0f);
        boolean invulnerable = blocksDamage();
        if (entity.isInvulnerable() != invulnerable) {
            entity.setInvulnerable(invulnerable);
        }
    }

    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || arena == null) {
            return;
        }
        Location at = arena.at(x, y, z);
        Location current = entity.getLocation();
        if (current.getWorld() != at.getWorld() || current.distanceSquared(at) > 0.0025) {
            instance.runInternalTeleport(() -> entity.teleport(at));
        }
        entity.setVelocity(new Vector());
    }

    /* ================================================================== helpers */

    private Player nearestFighter(double fx, double fz) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : enc.fighters()) {
            double d = sq(arena.localX(p.getLocation()) - fx, arena.localZ(p.getLocation()) - fz);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static double sq(double a, double b) {
        return a * a + b * b;
    }

    private static double rnd(double spread) {
        return (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * spread;
    }

    /** Is this player standing low enough to be caught by something that runs along the floor? */
    private boolean grounded(Player p) {
        return arena.localY(p.getLocation()) < 0.75;
    }

    /* ================================================================== moves */

    private abstract class Move {
        final String id;
        final boolean usesBody;
        int recovery;
        int t;

        Move(String id, boolean usesBody) {
            this.id = id;
            this.usesBody = usesBody;
        }

        /** @return true when the move is over */
        final boolean run() {
            t++;
            return step();
        }

        abstract boolean step();

        void end() {
        }
    }

    /**
     * It goes to the lamp nearest the party and puts it out. The wind-up is in that lamp's light, so it
     * is the party's best opening; enough damage in it staggers the Hush and saves the lamp.
     */
    private final class Snuff extends Move {
        private final int lamp;
        private final int travel = 90;
        private int windupAt = -1;
        private int waveAt = -1;

        Snuff() {
            super("snuff", true);
            Player near = nearestFighter(x, z);
            double px = near == null ? x : arena.localX(near.getLocation());
            double pz = near == null ? z : arena.localZ(near.getLocation());
            this.lamp = arena.nearestLamp(px, pz, true);
            this.recovery = 20;
        }

        @Override
        boolean step() {
            if (lamp < 0 || (!arena.lit(lamp) && waveAt < 0)) {
                return true;
            }
            double lx = HushArena.lampX(lamp);
            double lz = HushArena.lampZ(lamp);
            if (waveAt >= 0) {
                return wave(lx, lz);
            }
            if (windupAt < 0) {
                double dx = lx - x;
                double dz = lz - z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 2.4 && t < travel) {
                    double speed = cfg.d("moves.snuff.travel-speed", 0.22);
                    moveBy(dx / d * speed, dz / d * speed);
                    return false;
                }
                windupAt = t;
                windupDamage = 0;
                world.playSound(arena.lampFlame(lamp), Sound.ENTITY_WARDEN_LISTENING_ANGRY, SoundCategory.HOSTILE, 2f, 0.6f);
                HushText.bar(enc.fighters(), HushText.REVEALED);
            }
            int w = t - windupAt;
            int windup = cfg.i("moves.snuff.windup-ticks", 46);
            // The flame leans toward it and gutters.
            if (w % 3 == 0) {
                Location flame = arena.lampFlame(lamp);
                Vector pull = arena.at(x, y + 2.6, z).toVector().subtract(flame.toVector()).normalize().multiply(0.12);
                world.spawnParticle(Particle.SMALL_FLAME, flame, 0, pull.getX(), pull.getY(), pull.getZ(), 1.0);
                world.spawnParticle(Particle.SMOKE, flame, 1, 0.05, 0.05, 0.05, 0.01);
            }
            if (w % 10 == 0) {
                world.playSound(arena.lampFlame(lamp), Sound.BLOCK_CANDLE_AMBIENT, SoundCategory.HOSTILE, 2f, 0.5f + w * 0.01f);
            }
            double need = instance.getCombatMaxHealth() * cfg.d("moves.snuff.stagger-percent", 2.0) / 100.0;
            if (windupDamage >= need) {
                windupDamage = -1;
                staggerUntil = clock + cfg.i("moves.snuff.stagger-ticks", 70);
                snuffReadyAt = clock + cfg.i("moves.snuff.cooldown-ticks", 240);
                HushText.bar(enc.fighters(), HushText.STAGGERED);
                world.playSound(body.chest(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 3f, 0.6f);
                world.spawnParticle(Particle.END_ROD, body.chest(), 30, 0.8, 1.4, 0.8, 0.05);
                return true;
            }
            if (w >= windup) {
                windupDamage = -1;
                enc.snuff(lamp, true);
                lampLockUntil[lamp] = clock + cfg.i("lamps.relight-lock-ticks", 100);
                snuffReadyAt = clock + cfg.i("moves.snuff.cooldown-ticks", 240);
                waveAt = t;
            }
            return false;
        }

        /** The dark rolls outward from the dead lamp along the floor. */
        private boolean wave(double lx, double lz) {
            int w = t - waveAt;
            double r = 0.8 + w * 0.55;
            double max = cfg.d("moves.snuff.wave-radius", 9.0);
            for (double[] p : HushArena.ring(r, (int) Math.min(64, 10 + r * 5), w * 0.2)) {
                world.spawnParticle(Particle.SQUID_INK, arena.at(lx + p[0], 0.25, lz + p[1]), 1, 0.05, 0.05, 0.05, 0.0);
            }
            for (Player f : enc.fighters()) {
                double px = arena.localX(f.getLocation());
                double pz = arena.localZ(f.getLocation());
                double d = Math.sqrt(sq(px - lx, pz - lz));
                if (Math.abs(d - r) <= 0.9 && grounded(f) && !arena.inLight(px, pz)) {
                    if (enc.hit(f, cfg.d("moves.snuff.power", 42.0), "snuff", 30, null, false)) {
                        enc.addDread(f, 15.0);
                    }
                }
            }
            return r >= max;
        }

        @Override
        void end() {
            windupDamage = -1;
        }
    }

    /** A rush along a line drawn in smoke. The one strike of the fight that can be parried. */
    private final class Lunge extends Move {
        private double dirX;
        private double dirZ;
        private double length;
        private double travelled;
        private final int telegraph;

        Lunge() {
            super("lunge", true);
            this.telegraph = cfg.i("moves.lunge.telegraph-ticks", chapter >= 2 ? 20 : 26);
            this.recovery = 24;
        }

        @Override
        boolean step() {
            if (t == 1) {
                Player target = pickTarget();
                if (target == null) {
                    return true;
                }
                double dx = arena.localX(target.getLocation()) - x;
                double dz = arena.localZ(target.getLocation()) - z;
                double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                dirX = dx / d;
                dirZ = dz / d;
                length = Math.min(cfg.d("moves.lunge.length", 22.0), d + 7.0);
                world.playSound(body.chest(), Sound.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 3f, 0.5f);
            }
            if (t <= telegraph) {
                if (t % 2 == 0) {
                    for (double s = 1.5; s <= length; s += 1.1) {
                        double[] c = HushArena.clamp(x + dirX * s, z + dirZ * s, REACH + 1);
                        world.spawnParticle(Particle.SMOKE, arena.at(c[0], 0.2, c[1]), 1, 0.25, 0.02, 0.25, 0.0);
                        if (t > telegraph - 8) {
                            world.spawnParticle(Particle.DUST, arena.at(c[0], 0.3, c[1]), 1, 0.2, 0.02, 0.2, 0, PITCH);
                        }
                    }
                }
                return false;
            }
            if (t == telegraph + 1) {
                world.playSound(body.chest(), Sound.ENTITY_VEX_CHARGE, SoundCategory.HOSTILE, 3f, 0.5f);
            }
            double speed = cfg.d("moves.lunge.speed", 1.0);
            double fromX = x;
            double fromZ = z;
            moveBy(dirX * speed, dirZ * speed);
            travelled += speed;
            world.spawnParticle(Particle.LARGE_SMOKE, arena.at(x, y + 1.5, z), 6, 0.8, 1.2, 0.8, 0.01);
            Vector side = new Vector(-dirZ, 0.25, dirX).multiply(0.6);
            for (Player f : enc.fighters()) {
                double px = arena.localX(f.getLocation());
                double pz = arena.localZ(f.getLocation());
                if (sq(px - x, pz - z) <= 2.4 * 2.4 && arena.localY(f.getLocation()) < HushBody.HEIGHT) {
                    // Shove to whichever side of the line the player already is.
                    double cross = (px - x) * dirZ - (pz - z) * dirX;
                    Vector knock = cross > 0 ? side.clone().multiply(-1).setY(0.25) : side.clone();
                    enc.hit(f, cfg.d("moves.lunge.power", 62.0), "lunge", 30, knock, true);
                }
            }
            // Sliding along the rim instead of going anywhere: the rush is spent.
            boolean stuck = sq(x - fromX, z - fromZ) < 0.25 * speed * speed;
            return travelled >= length || stuck;
        }

        private Player pickTarget() {
            List<Player> fighters = enc.fighters();
            if (fighters.isEmpty()) {
                return null;
            }
            // The one furthest away: running from it is what it punishes.
            Player best = null;
            double bestD = -1;
            for (Player p : fighters) {
                double d = sq(arena.localX(p.getLocation()) - x, arena.localZ(p.getLocation()) - z);
                if (d > bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }
    }

    /** Every sound stops. Then a wave with no sound at all. Lamplight is the only safe place. */
    private final class Hush extends Move {
        private final int windup;
        private double baseY;

        Hush() {
            super("hush", true);
            this.windup = cfg.i("moves.hush.windup-ticks", chapter >= 2 ? 50 : 60);
            this.recovery = 30;
        }

        @Override
        boolean step() {
            if (t == 1) {
                baseY = y;
                for (Player p : enc.audience()) {
                    p.stopAllSounds();
                    p.sendActionBar(TextUtil.component(HushText.HUSH_WARN.of(p)));
                }
                HushText.title(enc.audience(), null, HushText.HUSH_WARN, 3, 30, 8);
            }
            if (t <= windup) {
                y = baseY + 1.6 * Math.min(1.0, t / 20.0);
                // The dark is drawn in toward it.
                if (t % 2 == 0) {
                    for (int i = 0; i < 6; i++) {
                        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                        double r = 5 + ThreadLocalRandom.current().nextDouble(9);
                        Location from = arena.at(x + Math.cos(a) * r, 0.4 + ThreadLocalRandom.current().nextDouble(3), z + Math.sin(a) * r);
                        Vector pull = body.chest().toVector().subtract(from.toVector()).normalize().multiply(0.35);
                        world.spawnParticle(Particle.SCULK_SOUL, from, 0, pull.getX(), pull.getY(), pull.getZ(), 1.0);
                    }
                }
                if (t % 20 == 10) {
                    for (Player p : enc.fighters()) {
                        if (!arena.inLight(p.getLocation())) {
                            p.sendActionBar(TextUtil.component(HushText.HUSH_WARN.of(p)));
                        }
                    }
                }
                return false;
            }
            if (t == windup + 1) {
                for (Player p : enc.audience()) {
                    p.stopAllSounds();
                }
                for (double r = 2; r <= HushArena.RADIUS; r += 2.5) {
                    for (double[] p : HushArena.ring(r, (int) (8 + r * 3), r)) {
                        if (!arena.inLight(x + p[0], z + p[1])) {
                            world.spawnParticle(Particle.DUST, arena.at(x + p[0], 0.4, z + p[1]), 1, 0.3, 0.3, 0.3, 0, PITCH);
                        }
                    }
                }
                for (Player f : enc.fighters()) {
                    if (!arena.inLight(f.getLocation())) {
                        if (enc.hit(f, cfg.d("moves.hush.power", 55.0), "hush", 30, null, false)) {
                            enc.addDread(f, cfg.d("moves.hush.dread", 30.0));
                            f.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
                        }
                    }
                }
            }
            y = baseY + 1.6 * Math.max(0.0, 1.0 - (t - windup) / 12.0);
            return t >= windup + 12;
        }

        @Override
        void end() {
            y = HOVER;
        }
    }

    /** Hands come up through the floor under everyone. They cannot rise inside lamplight. */
    private final class Grasp extends Move {
        private final List<double[]> spots = new ArrayList<>();
        private final List<BlockDisplay> fingers = new ArrayList<>();
        private final int telegraph;

        Grasp() {
            super("grasp", false);
            this.telegraph = cfg.i("moves.grasp.telegraph-ticks", 28);
        }

        @Override
        boolean step() {
            if (t == 1) {
                for (Player f : enc.fighters()) {
                    double px = arena.localX(f.getLocation());
                    double pz = arena.localZ(f.getLocation());
                    addSpot(px, pz);
                    for (int i = 0; i < (chapter >= 1 ? 2 : 1); i++) {
                        addSpot(px + rnd(4.5), pz + rnd(4.5));
                    }
                    if (!arena.inLight(px, pz)) {
                        f.sendActionBar(TextUtil.component(HushText.GRASP_WARN.of(f)));
                    }
                }
                if (spots.isEmpty()) {
                    return true;
                }
                for (double[] s : spots) {
                    world.playSound(arena.at(s[0], 0.2, s[1]), Sound.BLOCK_SCULK_CHARGE, SoundCategory.HOSTILE, 1.4f, 0.6f);
                }
            }
            double radius = cfg.d("moves.grasp.radius", 1.9);
            if (t <= telegraph) {
                if (t % 3 == 0) {
                    for (double[] s : spots) {
                        for (double[] p : HushArena.ring(radius, 14, t * 0.15)) {
                            world.spawnParticle(Particle.DUST, arena.at(s[0] + p[0], 0.15, s[1] + p[1]), 1, 0, 0, 0, 0, PITCH);
                        }
                        world.spawnParticle(Particle.SMOKE, arena.at(s[0], 0.15, s[1]), 2, radius * 0.4, 0.02, radius * 0.4, 0.0);
                    }
                }
                return false;
            }
            if (t == telegraph + 1) {
                for (double[] s : spots) {
                    rise(s[0], s[1]);
                    world.playSound(arena.at(s[0], 0.5, s[1]), Sound.BLOCK_SCULK_SHRIEKER_BREAK, SoundCategory.HOSTILE, 1.6f, 0.6f);
                    for (Player f : enc.fighters()) {
                        double px = arena.localX(f.getLocation());
                        double pz = arena.localZ(f.getLocation());
                        if (sq(px - s[0], pz - s[1]) <= radius * radius && arena.localY(f.getLocation()) < 2.2) {
                            if (enc.hit(f, cfg.d("moves.grasp.power", 48.0), "grasp", 20, null, false)) {
                                enc.addDread(f, 12.0);
                                f.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false, false));
                            }
                        }
                    }
                }
            }
            if (t == telegraph + 14) {
                for (BlockDisplay d : fingers) {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(6);
                        d.setTransformation(finger(0.02f));
                    }
                }
            }
            return t >= telegraph + 22;
        }

        private void addSpot(double sx, double sz) {
            double[] c = HushArena.clamp(sx, sz, REACH + 1);
            if (!arena.inLight(c[0], c[1])) {
                spots.add(c);
            }
        }

        private void rise(double sx, double sz) {
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + sx;
                Location at = arena.at(sx + Math.cos(a) * 0.9, 0.0, sz + Math.sin(a) * 0.9);
                BlockDisplay d = world.spawn(at, BlockDisplay.class, e -> {
                    e.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    e.setPersistent(false);
                    e.setBrightness(new Display.Brightness(0, 0));
                    e.setTransformation(finger(0.02f));
                    e.addScoreboardTag(HushModule.TAG);
                });
                fingers.add(d);
            }
            module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> {
                for (BlockDisplay d : fingers) {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(4);
                        d.setTransformation(finger(1.9f + (float) rnd(0.5)));
                    }
                }
            }, 1L);
        }

        private Transformation finger(float height) {
            return new Transformation(new Vector3f(-0.11f, 0f, -0.11f), new Quaternionf(),
                    new Vector3f(0.22f, height, 0.22f), new Quaternionf());
        }

        @Override
        void end() {
            for (BlockDisplay d : fingers) {
                d.remove();
            }
            fingers.clear();
        }
    }

    /** A bell rings by itself and its sound rolls across the floor. Jump it. Light does not stop sound. */
    private final class Toll extends Move {
        private final List<Integer> bells = new ArrayList<>();
        private final int telegraph;

        Toll() {
            super("toll", false);
            this.telegraph = cfg.i("moves.toll.telegraph-ticks", 30);
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < HushArena.BELLS; i++) {
                all.add(i);
            }
            Collections.shuffle(all);
            bells.add(all.get(0));
            if (chapter >= 2) {
                bells.add(all.get(1));
            }
        }

        @Override
        boolean step() {
            double speed = cfg.d("moves.toll.speed", 0.55);
            double max = HushArena.BELL_RING + HushArena.RADIUS + 1;
            if (t <= telegraph) {
                if (t % 4 == 0) {
                    for (int bell : bells) {
                        Location mouth = arena.bellMouth(bell);
                        world.spawnParticle(Particle.DUST, mouth, 8, 0.5, 0.5, 0.5, 0, ASHEN);
                        world.playSound(mouth, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.5f, 0.5f + t * 0.02f);
                    }
                }
                return false;
            }
            if (t == telegraph + 1) {
                for (int bell : bells) {
                    Location mouth = arena.bellMouth(bell);
                    world.playSound(mouth, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 4f, 0.45f);
                    world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 4f, HYMN[noteOfBell[bell]] * 0.53f);
                }
            }
            double r = (t - telegraph) * speed;
            for (int bell : bells) {
                double bx = HushArena.bellX(bell);
                double bz = HushArena.bellZ(bell);
                int points = (int) Math.min(110, 12 + r * 4);
                for (double[] p : HushArena.ring(r, points, t * 0.05)) {
                    double wx = bx + p[0];
                    double wz = bz + p[1];
                    if (wx * wx + wz * wz <= HushArena.RADIUS * HushArena.RADIUS) {
                        world.spawnParticle(Particle.DUST, arena.at(wx, 0.3, wz), 1, 0, 0.05, 0, 0, ASHEN);
                    }
                }
                for (Player f : enc.fighters()) {
                    double d = Math.sqrt(sq(arena.localX(f.getLocation()) - bx, arena.localZ(f.getLocation()) - bz));
                    if (Math.abs(d - r) <= 0.7 && grounded(f)) {
                        Vector knock = new Vector(arena.localX(f.getLocation()) - bx, 0, arena.localZ(f.getLocation()) - bz);
                        if (knock.lengthSquared() > 1e-4) {
                            knock.normalize().multiply(0.45).setY(0.2);
                        }
                        enc.hit(f, cfg.d("moves.toll.power", 44.0), "toll" + bell, 25, knock, false);
                    }
                }
            }
            return r >= max;
        }
    }

    /** The fifth toll's light: a harmless gold ring that runs out over the floor. */
    private final class LightRing extends Move {

        LightRing() {
            super("light", false);
        }

        @Override
        boolean step() {
            double r = t * 0.9;
            for (double[] p : HushArena.ring(r, (int) Math.min(90, 12 + r * 4), t * 0.07)) {
                world.spawnParticle(Particle.DUST, arena.at(p[0], 0.35, p[1]), 1, 0, 0.05, 0, 0, GOLD);
            }
            return r >= HushArena.RADIUS;
        }
    }

    /* ================================================================== echoes (Compline) */

    /**
     * A copy of its shape that drifts through the belfry humming the first notes of the hymn. The real
     * Hush makes no sound. Striking an echo bursts it and frightens whoever struck; it comes back later.
     */
    private final class Echo {
        private final HushBody shell = new HushBody(arena, true);
        private Interaction box;
        private double ex;
        private double ez;
        private double heading;
        private int respawnAt;
        private int hum;
        private final int index;

        Echo(int index) {
            this.index = index;
            this.respawnAt = clock + 20 + index * 30;
        }

        void tick() {
            if (!shell.spawned()) {
                if (mode == Mode.FIGHT && clock >= respawnAt) {
                    form();
                }
                return;
            }
            // A slow wander that keeps to the lamp ring's width.
            heading += rnd(0.12);
            double nx = ex + Math.cos(heading) * 0.07;
            double nz = ez + Math.sin(heading) * 0.07;
            double r = Math.sqrt(nx * nx + nz * nz);
            if (r > 15.5 || r < 5.5) {
                heading = Math.atan2(-nz, -nx) + (r < 5.5 ? Math.PI : 0) + rnd(0.8);
            } else {
                ex = nx;
                ez = nz;
            }
            shell.moveTo(ex, HOVER, ez);
            shell.tick(0f);
            if (box != null && box.isValid() && clock % 2 == 0) {
                box.teleport(arena.at(ex, HOVER, ez));
            }
            if (clock % 26 == index * 9) {
                Location chest = shell.chest();
                world.playSound(chest, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.HOSTILE, 0.9f, HYMN[hum % 4] * 0.5f);
                world.spawnParticle(Particle.NOTE, chest.clone().add(0, 2.2, 0), 1, 0.3, 0.2, 0.3, 1.0);
                hum++;
            }
            if (clock % 10 == 0 && mode == Mode.FIGHT) {
                for (Player f : enc.fighters()) {
                    double px = arena.localX(f.getLocation());
                    double pz = arena.localZ(f.getLocation());
                    if (sq(px - ex, pz - ez) <= 2.4 * 2.4 && !arena.inLight(px, pz)) {
                        enc.addDread(f, cfg.d("fight.echo-chill-dread", 5.0));
                    }
                }
            }
        }

        private void form() {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            ex = Math.cos(a) * 12;
            ez = Math.sin(a) * 12;
            heading = a + Math.PI / 2;
            shell.spawn(ex, HOVER, ez);
            // Two ticks later, so the client has the folded shape first and blends the unfolding.
            module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> {
                if (shell.spawned()) {
                    shell.grow(1f, 30);
                }
            }, 2L);
            box = world.spawn(arena.at(ex, HOVER, ez), Interaction.class, i -> {
                i.setInteractionWidth(2.8f);
                i.setInteractionHeight(HushBody.HEIGHT);
                i.setResponsive(true);
                i.setPersistent(false);
                i.addScoreboardTag(HushModule.TAG);
            });
            world.spawnParticle(Particle.LARGE_SMOKE, shell.chest(), 20, 0.8, 1.5, 0.8, 0.01);
        }

        boolean owns(Entity e) {
            return box != null && box.equals(e);
        }

        void burst(Player by) {
            if (!shell.spawned()) {
                return;
            }
            Location chest = shell.chest();
            world.spawnParticle(Particle.SQUID_INK, chest, 40, 0.9, 1.6, 0.9, 0.05);
            world.playSound(chest, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2f, 0.6f);
            if (by != null) {
                enc.addDread(by, cfg.d("fight.echo-burst-dread", 18.0));
                by.sendActionBar(TextUtil.component(HushText.ECHO_POP.of(by)));
            }
            clear();
            respawnAt = clock + cfg.i("fight.echo-respawn-ticks", 400);
        }

        void clear() {
            shell.remove();
            if (box != null) {
                box.remove();
                box = null;
            }
        }
    }

    private void tickEchoes() {
        for (Echo e : echoes) {
            e.tick();
        }
    }

    private void clearEchoes() {
        for (Echo e : echoes) {
            e.clear();
        }
        echoes.clear();
    }

    /** A participant struck something that is not the boss. @return true if it was one of the echoes */
    boolean propHit(Player p, Entity prop) {
        for (Echo e : echoes) {
            if (e.owns(prop)) {
                e.burst(p);
                return true;
            }
        }
        return false;
    }

    /** May lamp {@code i} be lit by hand right now (a freshly snuffed wick is cold for a moment)? */
    boolean lampLocked(int i) {
        return clock < lampLockUntil[i];
    }

    /** Admin: push HP just under the next gate (or to the end). */
    void skip() {
        if (mode == Mode.LULLABY) {
            solve();
            return;
        }
        double now = instance.healthPercent();
        instance.setCombatHealthPercent(now > 66 ? 65.5 : now > 33 ? 32.5 : 0.5);
    }
}
