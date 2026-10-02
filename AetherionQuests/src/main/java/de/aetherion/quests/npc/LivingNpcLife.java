package de.aetherion.quests.npc;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ForageAccess;
import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.talk.TalkUx;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPC life for living FancyNPC hosts — opt-in per NPC id.
 *
 * <p>Body language is built from <b>beats</b>: one readable action (look down at the ledger,
 * one axe chop, raise the spyglass), a short hold, then the hand and head go back to rest.
 * Each NPC has a {@link Persona} — 3–5 signature beats with weights, its own quiet gap and
 * an optional patrol beat — so the harbour cast reads as people with jobs, not random noise.</p>
 *
 * <ul>
 *   <li><b>Talk is sacred:</b> TalkUx busy, a player within {@link #CLOSE}, or a forager
 *       chop-demo nearby puts the NPC into <i>calm</i>: pending beat steps are cancelled
 *       (generation token), the job prop goes back, pose stands, head faces front.</li>
 *   <li><b>Anti-flail:</b> every swing goes through a per-NPC limiter; multi-swings are rare
 *       and spaced; crouch and snacks have long cooldowns.</li>
 *   <li><b>Heads turn, they don't snap:</b> glances and "look at your work" are eased over a
 *       few 2-tick steps.</li>
 *   <li><b>Patrols:</b> ≤ 3 blocks, ground-checked, eased stroll with a turn-before-walk, a job
 *       beat at the far point, and a quick walk home (not a teleport) when someone approaches.</li>
 * </ul>
 * Config: {@code npc-life.enabled}, {@code npc-life.patrols}, {@code npc-life.pace}
 * (gap multiplier; &gt;1 = calmer).
 */
public final class LivingNpcLife {

    private static LivingNpcLife instance;

    private static final double VIEW = 24.0;
    private static final double CLOSE = 5.5;
    private static final double CHOP_CALM = 12.0;
    /** Motion loop (head turns, patrol steps). Brain (beats/glances) runs every {@link #BRAIN} loops. */
    private static final long PERIOD = 2L;
    private static final int BRAIN = 3;

    private static final int SWING_GAP = 10;          // server ticks between any two swings
    private static final int EXTERNAL_SWING_GAP = 24; // Atmosphere greet/banter swings
    private static final int GESTURE_GAP = 50;        // talk gestures
    private static final int CROUCH_COOLDOWN = 900;
    private static final int SNACK_COOLDOWN = 2400;
    private static final double WALK = 0.085;         // blocks / tick average (eased)
    private static final double HURRY = 0.22;

    private final AetherionQuests plugin;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long now;
    private final boolean patrols;
    private final double pace;

    private static final class State {
        final String id;
        final Location home;
        final Persona persona;
        long nextBeat;
        long nextGlance;
        long busyUntil;
        long lookBackAt = -1;
        long restoreHandAt = -1;
        long standAt = -1;
        long stopUseAt = -1;
        long crouchReadyAt;
        long snackReadyAt;
        int lastSwing = Integer.MIN_VALUE / 2;
        int lastGesture = Integer.MIN_VALUE / 2;
        int gen;
        boolean calm;
        boolean headOff;
        String lastBeat;
        // eased head turn
        float turnFromYaw;
        float turnToYaw;
        float turnFromPitch;
        float turnToPitch;
        int turnStep;
        int turnSteps;
        // patrol
        List<double[]> route;
        int leg = -1;
        Location walkFrom;
        Location walkTo;
        double walkU;
        long patrolPauseUntil;
        long nextPatrol;
        long lastWalkFx;
        boolean patrolBroken;
        boolean needTurn;
        boolean hurry;

        State(String id, Location home) {
            this.id = id;
            this.home = home.clone();
            this.persona = personaFor(id);
        }
    }

    private LivingNpcLife(AetherionQuests plugin) {
        this.plugin = plugin;
        this.patrols = plugin.getConfig().getBoolean("npc-life.patrols", true);
        this.pace = Math.max(0.5, Math.min(3.0, plugin.getConfig().getDouble("npc-life.pace", 1.0)));
    }

    public static LivingNpcLife start(AetherionQuests plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new LivingNpcLife(plugin);
        if (plugin.getConfig().getBoolean("npc-life.enabled", true)) {
            instance.task = plugin.getServer().getScheduler().runTaskTimer(plugin, instance::tick, 40L, PERIOD);
        }
        return instance;
    }

    public static LivingNpcLife get() {
        return instance;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (State s : states.values()) {
            restoreAll(s);
        }
        states.clear();
        if (instance == this) {
            instance = null;
        }
    }

    public static void onSpawned(String npcId, Location at) {
        LivingNpcLife life = instance;
        if (life == null || npcId == null || at == null) {
            return;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        State s = life.states.get(id);
        if (s != null && s.leg >= 0) {
            // Re-placed mid-walk: drop the patrol, adopt the new home.
            life.living().setMovementLocked(id, false);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        State fresh = new State(id, at);
        fresh.nextBeat = life.now + 60 + r.nextInt(160);
        fresh.nextGlance = life.now + 120 + r.nextInt(240);
        fresh.nextPatrol = life.now + 300 + r.nextInt(500);
        fresh.crouchReadyAt = life.now + r.nextInt(400);
        fresh.snackReadyAt = life.now + 1200 + r.nextInt(1200);
        fresh.route = routeFor(id);
        life.states.put(id, fresh);
    }

    public static void onRemoved(String npcId) {
        LivingNpcLife life = instance;
        if (life == null || npcId == null) {
            return;
        }
        State s = life.states.remove(npcId.toLowerCase(Locale.ROOT));
        if (s != null) {
            s.gen++;
            if (s.leg >= 0) {
                life.living().setMovementLocked(s.id, false);
            }
        }
    }

    /**
     * Put an NPC at rest right now: cancel pending beat steps, restore the job prop, stand up,
     * face front. Safe to call every line — it only acts on the transition into calm.
     */
    public void calmForTalk(String npcId) {
        if (npcId == null) {
            return;
        }
        State s = states.get(npcId.toLowerCase(Locale.ROOT));
        LivingNpcService living = living();
        if (s == null || living == null || s.leg >= 0) {
            return;
        }
        calm(living, s);
    }

    private LivingNpcService living() {
        return plugin.getLivingNpcService();
    }

    /* =====================================================================
     * Tick
     * ===================================================================== */

    private void tick() {
        now += PERIOD;
        LivingNpcService living = living();
        if (living == null || !living.available()) {
            return;
        }
        boolean brain = (now / PERIOD) % BRAIN == 0;
        for (State s : states.values()) {
            try {
                tickOne(living, s, brain);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("NPC life tick failed for " + s.id + ": " + ex.getMessage());
            }
        }
    }

    private void tickOne(LivingNpcService living, State s, boolean brain) {
        boolean escort = living.isMovementLocked(s.id) && s.leg < 0;
        if (escort) {
            // A scripted walk owns the NPC (and empties its hand): cancel our steps, never
            // push the job prop back mid-escort.
            s.gen++;
            s.restoreHandAt = -1;
            s.turnSteps = 0;
            s.lookBackAt = -1;
            s.headOff = false;
            if (s.standAt >= 0) {
                s.standAt = -1;
                attribute(s.id, "pose", "standing");
            }
            if (s.stopUseAt >= 0) {
                s.stopUseAt = -1;
                attribute(s.id, "use_item", "none");
            }
            return;
        }

        // Timed restores always run, even if nobody is watching any more.
        if (s.restoreHandAt >= 0 && now >= s.restoreHandAt) {
            s.restoreHandAt = -1;
            restoreHand(s.id);
        }
        if (s.standAt >= 0 && now >= s.standAt) {
            s.standAt = -1;
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0 && now >= s.stopUseAt) {
            s.stopUseAt = -1;
            attribute(s.id, "use_item", "none");
        }

        Location at = living.locationOf(s.id);
        if (at == null || at.getWorld() == null) {
            return;
        }

        if (s.leg >= 0) {
            patrolTick(living, s, at);
            return;
        }
        if (s.turnSteps > 0) {
            stepTurn(living, s, at);
        }
        if (brain) {
            brainTick(living, s, at);
        }
    }

    private void brainTick(LivingNpcService living, State s, Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Player> viewers = viewers(at, VIEW);
        if (viewers.isEmpty()) {
            // Nobody to perform for: settle quietly and wait.
            if (s.headOff && s.turnSteps == 0) {
                s.lookBackAt = -1;
                startTurn(living, s, at, s.home.getYaw(), s.home.getPitch(), 4);
            }
            return;
        }
        boolean talking = busy(s.id);
        boolean close = anyone(at, CLOSE);
        if (talking || close || chopDemoNear(at)) {
            calm(living, s);
            return;
        }
        if (s.calm) {
            // Leaving calm: take a breath before the first beat.
            s.calm = false;
            s.nextBeat = Math.max(s.nextBeat, now + 40 + r.nextInt(60));
            s.nextGlance = Math.max(s.nextGlance, now + 60 + r.nextInt(80));
        }

        if (s.lookBackAt >= 0 && now >= s.lookBackAt && s.turnSteps == 0) {
            s.lookBackAt = -1;
            startTurn(living, s, at, s.home.getYaw(), s.home.getPitch(), 4);
            return;
        }

        boolean free = now >= s.busyUntil && s.turnSteps == 0;
        if (free && now >= s.nextBeat) {
            runBeat(s, at);
            return;
        }
        boolean idle = free && s.lookBackAt < 0 && !s.headOff
                && s.restoreHandAt < 0 && s.standAt < 0 && s.stopUseAt < 0;
        if (idle && now >= s.nextGlance) {
            float offset = (float) r.nextDouble(20.0, 55.0) * (r.nextBoolean() ? 1 : -1);
            startTurn(living, s, at, s.home.getYaw() + offset, (float) r.nextDouble(-6.0, 8.0), 4);
            s.lookBackAt = now + 40 + r.nextInt(60);
            s.nextGlance = now + (long) ((200 + r.nextInt(240)) * pace);
            return;
        }
        if (idle && patrols && s.route != null && !s.patrolBroken && now >= s.nextPatrol
                && now >= s.busyUntil + 10) {
            s.nextPatrol = now + (long) ((600 + r.nextInt(900)) * pace);
            beginPatrol(living, s, at);
        }
    }

    private void calm(LivingNpcService living, State s) {
        if (s.calm) {
            return;
        }
        s.calm = true;
        s.gen++;
        s.busyUntil = now;
        s.lookBackAt = -1;
        s.turnSteps = 0;
        if (s.restoreHandAt >= 0) {
            s.restoreHandAt = -1;
            restoreHand(s.id);
        }
        if (s.standAt >= 0) {
            s.standAt = -1;
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0) {
            s.stopUseAt = -1;
            attribute(s.id, "use_item", "none");
        }
        if (s.headOff) {
            Location at = living.locationOf(s.id);
            if (at != null) {
                at.setYaw(s.home.getYaw());
                at.setPitch(s.home.getPitch());
                living.moveTo(s.id, at);
            }
            s.headOff = false;
        }
    }

    private static boolean busy(String id) {
        TalkUx ux = TalkUx.get();
        return ux != null && ux.isBusy(id);
    }

    private static boolean chopDemoNear(Location at) {
        ForageAccess foraging;
        try {
            foraging = AetherServices.foraging();
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
        if (foraging == null) {
            return false;
        }
        double r2 = CHOP_CALM * CHOP_CALM;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) {
                continue;
            }
            try {
                if (foraging.isChopDemoRunning(p)) {
                    return true;
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return false;
    }

    /* =====================================================================
     * Beats & personas
     * ===================================================================== */

    @FunctionalInterface
    private interface BeatAction {
        /** @return how long the NPC is "busy" in ticks, or 0 if the beat could not run. */
        int run(LivingNpcLife life, State s, Ctx c);
    }

    private record Beat(String key, double weight, BeatAction act) {
    }

    private record Persona(int gapMin, int gapMax, Beat patrolBeat, Beat... beats) {
    }

    /** Beat context: where the NPC stands, where its hands are, and what's in front of it. */
    private record Ctx(Location at, Location hands, World w, float baseYaw) {
        Location front(double dist, double dy) {
            double rad = Math.toRadians(baseYaw);
            return at.clone().add(-Math.sin(rad) * dist, dy, Math.cos(rad) * dist);
        }
    }

    private void runBeat(State s, Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Persona p = s.persona;
        if (p == null || p.beats().length == 0) {
            s.nextBeat = now + 1200;
            return;
        }
        Ctx c = ctx(s, at);
        int dur = 0;
        Beat tried = null;
        for (int attempt = 0; attempt < 3 && dur <= 0; attempt++) {
            Beat b = pick(p, s.lastBeat, tried == null ? null : tried.key(), r);
            tried = b;
            dur = b.act().run(this, s, c);
            if (dur > 0) {
                s.lastBeat = b.key();
            }
        }
        if (dur <= 0) {
            s.nextBeat = now + 40;
            return;
        }
        s.busyUntil = now + dur;
        int gap = p.gapMin() + r.nextInt(Math.max(1, p.gapMax() - p.gapMin()));
        s.nextBeat = s.busyUntil + (long) (gap * pace);
    }

    private Ctx ctx(State s, Location at) {
        float base = s.leg >= 0 ? at.getYaw() : s.home.getYaw();
        return new Ctx(at.clone(), at.clone().add(0, 1.2, 0), at.getWorld(), base);
    }

    /** Weighted pick that avoids repeating the last beat (and a beat that just failed). */
    private static Beat pick(Persona p, String last, String skip, ThreadLocalRandom r) {
        Beat[] beats = p.beats();
        if (beats.length == 1) {
            return beats[0];
        }
        double total = 0;
        for (Beat b : beats) {
            if (!b.key().equals(last) && !b.key().equals(skip)) {
                total += b.weight();
            }
        }
        if (total <= 0) {
            return beats[r.nextInt(beats.length)];
        }
        double roll = r.nextDouble() * total;
        for (Beat b : beats) {
            if (b.key().equals(last) || b.key().equals(skip)) {
                continue;
            }
            roll -= b.weight();
            if (roll <= 0) {
                return b;
            }
        }
        return beats[beats.length - 1];
    }

    private static Beat beat(String key, double weight, BeatAction act) {
        return new Beat(key, weight, act);
    }

    /* ---- reusable beat shapes ---------------------------------------------------------- */

    /** Turn the head a little (relative to the NPC's front) and hold the look. */
    private static BeatAction gaze(double yawSpread, float pitchMin, float pitchMax, int hold) {
        return (l, s, c) -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            float yaw = (float) (yawSpread <= 0 ? 0 : r.nextDouble(yawSpread * 0.5, yawSpread) * (r.nextBoolean() ? 1 : -1));
            float pitch = pitchMin == pitchMax ? pitchMin : (float) r.nextDouble(pitchMin, pitchMax);
            l.look(s, c, yaw, pitch, 4);
            l.lookBack(s, hold);
            return hold + 6;
        };
    }

    /** Look down at the work, one stroke with a job sound, look back up. */
    private static BeatAction stroke(Material prop, float pitch, Sound sound, float vol, float tone,
                                     Material dust, int hold) {
        return (l, s, c) -> {
            if (prop != null) {
                l.hold(s, prop, hold + 4);
            }
            l.look(s, c, 0f, pitch, 3);
            l.step(s, 6, () -> {
                l.swingNow(s);
                l.sound(c.at(), sound, vol, tone + (float) ThreadLocalRandom.current().nextDouble(-0.05, 0.05));
                if (dust != null) {
                    l.dust(c.front(0.9, 0.15), dust, 5);
                }
            });
            l.lookBack(s, hold);
            return hold + 6;
        };
    }

    /** Head down over a book/paper, one pen stroke, a second quiet scribble — no second swing. */
    private static BeatAction write(Material prop, int hold) {
        return (l, s, c) -> {
            if (prop != null) {
                l.hold(s, prop, hold + 4);
            }
            l.look(s, c, 0f, 30f, 3);
            l.step(s, 8, () -> {
                l.swingNow(s);
                l.sound(c.at(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.16f, 1.55f);
            });
            l.step(s, 24, () -> l.sound(c.at(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.12f, 1.7f));
            l.lookBack(s, hold);
            return hold + 6;
        };
    }

    private static BeatAction spyglass(int hold) {
        return (l, s, c) -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            l.hold(s, Material.SPYGLASS, hold + 6);
            l.look(s, c, (float) r.nextDouble(-22, 22), -5f, 4);
            l.step(s, 6, () -> {
                l.use(s, hold - 8);
                l.sound(c.at(), Sound.ITEM_SPYGLASS_USE, 0.3f, 1.0f);
            });
            l.step(s, hold - 2, () -> l.sound(c.at(), Sound.ITEM_SPYGLASS_STOP_USING, 0.25f, 1.0f));
            l.lookBack(s, hold);
            return hold + 8;
        };
    }

    private static BeatAction kneel(Material dust, Sound sound, int hold) {
        return (l, s, c) -> {
            if (!l.crouch(s, hold)) {
                return 0;
            }
            l.look(s, c, 0f, 38f, 3);
            l.step(s, 10, () -> {
                l.sound(c.at(), sound, 0.22f, 1.15f);
                if (dust != null) {
                    l.dust(c.front(0.7, 0.1), dust, 4);
                }
            });
            l.lookBack(s, hold);
            return hold + 8;
        };
    }

    /** One clean arm movement with a sound (a wave, a point, a knock). */
    private static BeatAction once(Sound sound, float vol, float tone, float pitch) {
        return (l, s, c) -> {
            l.look(s, c, 0f, pitch, 2);
            l.step(s, 4, () -> {
                l.swingNow(s);
                if (sound != null) {
                    l.sound(c.at(), sound, vol, tone);
                }
            });
            l.lookBack(s, 18);
            return 24;
        };
    }

    /* ---- the cast ---------------------------------------------------------------------- */

    private static Persona personaFor(String id) {
        return switch (id) {
            // ---------- Harbour spine ----------
            case "egon" -> new Persona(150, 290, null,
                    beat("inspect", 3, (l, s, c) -> {
                        l.look(s, c, 0f, 24f, 3);
                        l.step(s, 12, () -> l.sound(c.at(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.22f, 0.95f));
                        l.lookBack(s, 42);
                        return 48;
                    }),
                    beat("trim", 2, (l, s, c) -> {
                        l.hold(s, Material.SHEARS, 46);
                        l.look(s, c, 0f, 20f, 3);
                        l.step(s, 12, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.ENTITY_SHEEP_SHEAR, 0.18f, 1.35f);
                        });
                        l.lookBack(s, 42);
                        return 50;
                    }),
                    beat("polish", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 16f, 3);
                        l.step(s, 10, () -> {
                            l.sound(c.at(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.2f, 1.25f);
                            c.w().spawnParticle(Particle.WAX_OFF, c.hands(), 3, 0.15, 0.1, 0.15, 0.0);
                        });
                        l.lookBack(s, 36);
                        return 40;
                    }),
                    beat("sizeup", 1.2, gaze(38, -3f, 2f, 40)));

            case "lumberjack" -> new Persona(120, 230, null,
                    beat("chop", 4, stroke(null, 14f, Sound.BLOCK_WOOD_HIT, 0.4f, 0.9f, Material.OAK_LOG, 26)),
                    beat("double", 1.3, (l, s, c) -> {
                        l.look(s, c, 0f, 14f, 3);
                        for (int i = 0; i < 2; i++) {
                            final int k = i;
                            l.step(s, 6 + k * 14, () -> {
                                l.swingNow(s);
                                l.sound(c.at(), k == 0 ? Sound.BLOCK_WOOD_HIT : Sound.ITEM_AXE_STRIP,
                                        0.38f, 0.85f + k * 0.08f);
                                l.dust(c.front(0.9, 0.6), Material.OAK_LOG, 4);
                            });
                        }
                        l.lookBack(s, 34);
                        return 40;
                    }),
                    beat("edge", 1.5, (l, s, c) -> {
                        l.look(s, c, 0f, 32f, 3);
                        l.step(s, 14, () -> c.w().spawnParticle(Particle.CRIT, c.hands(), 2, 0.1, 0.05, 0.1, 0.0));
                        l.lookBack(s, 36);
                        return 40;
                    }),
                    beat("treeline", 1.5, gaze(45, -8f, -2f, 50)),
                    beat("snack", 0.4, (l, s, c) -> {
                        if (!l.snack(s)) {
                            return 0;
                        }
                        l.hold(s, Material.APPLE, 40);
                        l.step(s, 4, () -> l.use(s, 30));
                        l.step(s, 10, () -> l.sound(c.at(), Sound.ENTITY_GENERIC_EAT, 0.28f, 1.0f));
                        l.step(s, 22, () -> l.sound(c.at(), Sound.ENTITY_GENERIC_EAT, 0.24f, 1.05f));
                        return 44;
                    }));

            case "quartermaster" -> new Persona(140, 270, null,
                    beat("spyglass", 3, spyglass(62)),
                    beat("tally", 3, (l, s, c) -> {
                        l.hold(s, Material.WRITABLE_BOOK, 52);
                        l.look(s, c, 0f, 26f, 3);
                        l.step(s, 12, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.16f, 1.4f);
                        });
                        l.step(s, 32, () -> l.sound(c.at(), Sound.ITEM_BOOK_PAGE_TURN, 0.28f, 1.1f));
                        l.lookBack(s, 46);
                        return 52;
                    }),
                    beat("map", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 32f, 3);
                        l.step(s, 8, () -> l.sound(c.at(), Sound.ITEM_BOOK_PAGE_TURN, 0.22f, 0.9f));
                        l.lookBack(s, 46);
                        return 50;
                    }),
                    beat("nod", 0.8, (l, s, c) -> {
                        l.look(s, c, 0f, 14f, 2);
                        l.lookBack(s, 8);
                        return 16;
                    }));

            case "foreman" -> new Persona(140, 270, null,
                    beat("tap", 3, stroke(null, 22f, Sound.BLOCK_STONE_HIT, 0.32f, 1.1f, Material.STONE, 24)),
                    beat("clipboard", 2.5, write(Material.PAPER, 48)),
                    beat("shaft", 1.5, gaze(28, 18f, 26f, 44)),
                    beat("kneel", 0.8, kneel(Material.STONE, Sound.BLOCK_STONE_STEP, 36)));

            case "ledger" -> new Persona(120, 230, null,
                    beat("write", 4, write(null, 44)),
                    beat("stamp", 1.5, (l, s, c) -> {
                        l.look(s, c, 0f, 24f, 3);
                        l.step(s, 8, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.ITEM_BOOK_PUT, 0.32f, 0.9f);
                        });
                        l.lookBack(s, 22);
                        return 28;
                    }),
                    beat("page", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 26f, 3);
                        l.step(s, 6, () -> l.sound(c.at(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.15f));
                        l.lookBack(s, 30);
                        return 34;
                    }),
                    beat("peer", 1.5, gaze(20, -5f, -2f, 36)));

            case "farmer" -> new Persona(130, 250,
                    beat("till", 1, stroke(null, 26f, Sound.ITEM_HOE_TILL, 0.3f, 1.05f, Material.FARMLAND, 28)),
                    beat("till", 3.5, stroke(null, 26f, Sound.ITEM_HOE_TILL, 0.3f, 1.05f, Material.FARMLAND, 28)),
                    beat("sow", 2, (l, s, c) -> {
                        l.hold(s, Material.WHEAT_SEEDS, 36);
                        l.look(s, c, 0f, 32f, 3);
                        l.step(s, 8, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.ITEM_CROP_PLANT, 0.3f, 1.1f);
                            l.dust(c.front(1.0, 0.1), Material.FARMLAND, 3);
                        });
                        l.lookBack(s, 30);
                        return 38;
                    }),
                    beat("inspect", 1, (l, s, c) -> {
                        if (!l.crouch(s, 34)) {
                            return 0;
                        }
                        l.look(s, c, 0f, 38f, 3);
                        l.step(s, 14, () -> {
                            l.sound(c.at(), Sound.BLOCK_GRASS_STEP, 0.22f, 1.2f);
                            c.w().spawnParticle(Particle.HAPPY_VILLAGER, c.front(0.8, 0.3), 2, 0.2, 0.1, 0.2, 0.0);
                        });
                        l.lookBack(s, 34);
                        return 42;
                    }),
                    beat("sky", 1, gaze(18, -20f, -14f, 42)));

            case "vex" -> new Persona(110, 210,
                    beat("cut", 1, vexCut(false)),
                    beat("cut", 3, vexCut(false)),
                    beat("combo", 1.1, vexCut(true)),
                    beat("blade", 1.5, (l, s, c) -> {
                        l.look(s, c, 0f, 30f, 3);
                        l.step(s, 10, () -> l.sound(c.at(), Sound.BLOCK_GRINDSTONE_USE, 0.08f, 1.8f));
                        l.lookBack(s, 40);
                        return 44;
                    }),
                    beat("line", 2, gaze(40, -2f, 2f, 30)));

            case "fisher" -> new Persona(150, 290, null,
                    beat("cast", 3, fisherCast(false)),
                    beat("catch", 0.8, fisherCast(true)),
                    beat("bait", 0.7, kneel(null, Sound.ENTITY_ITEM_PICKUP, 30)),
                    beat("horizon", 1.5, gaze(24, -6f, -2f, 52)));

            // ---------- Ambient townsfolk ----------
            case "town_crier" -> new Persona(220, 400,
                    beat("ring", 1, once(Sound.BLOCK_BELL_USE, 0.28f, 1.4f, -6f)),
                    beat("ring", 2, once(Sound.BLOCK_BELL_USE, 0.28f, 1.4f, -6f)),
                    beat("announce", 2, (l, s, c) -> {
                        l.look(s, c, -35f, -4f, 4);
                        l.step(s, 18, () -> l.lookAbs(s, s.home.getYaw() + 35f, -4f, 5));
                        l.lookBack(s, 40);
                        return 46;
                    }),
                    beat("scroll", 1.5, (l, s, c) -> {
                        l.hold(s, Material.PAPER, 40);
                        l.look(s, c, 0f, 20f, 3);
                        l.step(s, 6, () -> l.sound(c.at(), Sound.ITEM_BOOK_PAGE_TURN, 0.28f, 1.0f));
                        l.lookBack(s, 36);
                        return 42;
                    }));

            case "street_sweeper" -> new Persona(130, 250,
                    beat("sweep", 1, sweep()),
                    beat("sweep", 3, sweep()),
                    beat("lean", 1.5, gaze(28, 2f, 6f, 52)),
                    beat("pickup", 0.6, kneel(null, Sound.ENTITY_ITEM_PICKUP, 24)));

            case "lamp_lighter" -> new Persona(170, 330, null,
                    beat("light", 3, (l, s, c) -> {
                        long time = c.w().getTime();
                        if (time < 12000L || time > 23000L) {
                            return 0;
                        }
                        l.hold(s, Material.TORCH, 40);
                        l.look(s, c, 0f, -28f, 3);
                        l.step(s, 10, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.ITEM_FLINTANDSTEEL_USE, 0.2f, 1.3f);
                            c.w().spawnParticle(Particle.FLAME, c.front(0.5, 2.5), 3, 0.05, 0.08, 0.05, 0.01);
                        });
                        l.lookBack(s, 34);
                        return 42;
                    }),
                    beat("wick", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 24f, 3);
                        l.step(s, 10, () -> {
                            l.sound(c.at(), Sound.ENTITY_SHEEP_SHEAR, 0.14f, 1.6f);
                            c.w().spawnParticle(Particle.SMOKE, c.hands(), 2, 0.05, 0.05, 0.05, 0.0);
                        });
                        l.lookBack(s, 30);
                        return 36;
                    }),
                    beat("rounds", 2, gaze(32, -22f, -14f, 42)));

            // ---------- Everyone else (kept, cleaned up) ----------
            case "craftsman" -> new Persona(130, 250, null,
                    beat("strike", 3, (l, s, c) -> {
                        l.hold(s, Material.IRON_PICKAXE, 38);
                        l.look(s, c, 0f, 22f, 3);
                        for (int i = 0; i < 2; i++) {
                            final int k = i;
                            l.step(s, 6 + k * 14, () -> {
                                l.swingNow(s);
                                l.sound(c.at(), Sound.BLOCK_ANVIL_USE, 0.1f, 1.5f + k * 0.1f);
                                c.w().spawnParticle(Particle.CRIT, c.front(0.7, 1.0), 3, 0.12, 0.05, 0.12, 0.05);
                            });
                        }
                        l.lookBack(s, 34);
                        return 40;
                    }),
                    beat("inspect", 1.5, (l, s, c) -> {
                        l.look(s, c, 0f, 30f, 3);
                        l.step(s, 10, () -> c.w().spawnParticle(Particle.WAX_OFF, c.hands(), 2, 0.1, 0.05, 0.1, 0.0));
                        l.lookBack(s, 36);
                        return 40;
                    }),
                    beat("glance", 1, gaze(30, -2f, 4f, 36)));

            case "booster_tutor" -> new Persona(130, 250, null,
                    beat("spark", 2, (l, s, c) -> {
                        l.hold(s, Material.FLINT_AND_STEEL, 30);
                        l.look(s, c, 0f, 18f, 2);
                        l.step(s, 4, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.BLOCK_ANVIL_LAND, 0.08f, 1.8f);
                            c.w().spawnParticle(Particle.ELECTRIC_SPARK, c.hands(), 6, 0.2, 0.1, 0.2, 0.05);
                        });
                        l.lookBack(s, 22);
                        return 28;
                    }),
                    beat("flame", 1, (l, s, c) -> {
                        c.w().spawnParticle(Particle.SMALL_FLAME, c.hands(), 4, 0.12, 0.05, 0.12, 0.01);
                        l.step(s, 10, () -> l.sound(c.at(), Sound.BLOCK_FIRE_EXTINGUISH, 0.16f, 1.6f));
                        return 20;
                    }),
                    beat("study", 1, gaze(0, 26f, 26f, 36)));

            case "lark" -> new Persona(140, 270, null,
                    beat("birds", 2, (l, s, c) -> {
                        l.look(s, c, (float) ThreadLocalRandom.current().nextDouble(-35, 35), -14f, 4);
                        l.step(s, 8, () -> l.sound(c.at(), Sound.ENTITY_PARROT_AMBIENT, 0.24f, 1.3f));
                        l.lookBack(s, 40);
                        return 46;
                    }),
                    beat("wave", 1, once(null, 0f, 1f, -2f)));

            case "fishmonger" -> new Persona(130, 250,
                    beat("slap", 1, stroke(null, 24f, Sound.ENTITY_COD_FLOP, 0.3f, 1.0f, null, 26)),
                    beat("slap", 2, stroke(null, 24f, Sound.ENTITY_COD_FLOP, 0.3f, 1.0f, null, 26)),
                    beat("show", 2, (l, s, c) -> {
                        Material[] fish = {Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};
                        l.hold(s, fish[ThreadLocalRandom.current().nextInt(fish.length)], 50);
                        l.look(s, c, 0f, 6f, 2);
                        l.step(s, 8, () -> l.sound(c.at(), Sound.ENTITY_SALMON_FLOP, 0.22f, 1.0f));
                        l.lookBack(s, 44);
                        return 52;
                    }),
                    beat("ice", 1, gaze(0, 30f, 30f, 34)));

            case "rite_keeper" -> new Persona(140, 270, null,
                    beat("incense", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 12f, 3);
                        l.step(s, 4, () -> l.use(s, 30));
                        c.w().spawnParticle(Particle.SOUL, c.hands(), 3, 0.2, 0.2, 0.2, 0.01);
                        l.lookBack(s, 34);
                        return 38;
                    }),
                    beat("kneel", 1, kneel(null, Sound.BLOCK_SOUL_SAND_STEP, 36)),
                    beat("gaze", 1, gaze(15, -22f, -14f, 44)));

            case "arena_proctor" -> new Persona(130, 250,
                    beat("tally", 1, write(Material.WRITABLE_BOOK, 44)),
                    beat("tally", 3, write(Material.WRITABLE_BOOK, 44)),
                    beat("watch", 2, gaze(35, -2f, 3f, 36)));

            case "farm_isle_guide" -> new Persona(140, 270, null,
                    beat("point", 2, (l, s, c) -> {
                        l.look(s, c, (float) ThreadLocalRandom.current().nextDouble(-35, 35), -2f, 4);
                        l.step(s, 10, () -> l.swingNow(s));
                        l.lookBack(s, 32);
                        return 38;
                    }),
                    beat("soil", 1, kneel(Material.DIRT, Sound.BLOCK_GRAVEL_STEP, 36)),
                    beat("sky", 1, gaze(18, -18f, -12f, 40)));

            case "surveyor" -> new Persona(140, 270, null,
                    beat("spyglass", 2, spyglass(62)),
                    beat("compass", 2, (l, s, c) -> {
                        l.hold(s, Material.COMPASS, 50);
                        l.look(s, c, 0f, 30f, 3);
                        l.step(s, 20, () -> l.lookAbs(s, s.home.getYaw() + 25f, 4f, 4));
                        l.lookBack(s, 44);
                        return 52;
                    }));

            case "vince" -> new Persona(140, 270, null,
                    beat("flip", 2, (l, s, c) -> {
                        l.hold(s, Material.GOLD_NUGGET, 34);
                        l.look(s, c, 0f, -20f, 2);
                        l.step(s, 3, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.16f, 0.6f);
                        });
                        l.step(s, 12, () -> l.lookAbs(s, s.home.getYaw(), 24f, 2));
                        l.lookBack(s, 26);
                        return 32;
                    }),
                    beat("count", 1, (l, s, c) -> {
                        l.look(s, c, 0f, 30f, 3);
                        l.step(s, 10, () -> l.sound(c.at(), Sound.BLOCK_CHAIN_STEP, 0.16f, 1.6f));
                        l.lookBack(s, 32);
                        return 36;
                    }));

            case "bar_whisper" -> new Persona(160, 300, null,
                    beat("enchant", 1, (l, s, c) -> {
                        l.step(s, 2, () -> l.swingNow(s));
                        c.w().spawnParticle(Particle.ENCHANT, c.hands().clone().add(0, 0.5, 0), 10, 0.3, 0.4, 0.3, 0.5);
                        return 20;
                    }),
                    beat("lean", 1, gaze(22, 4f, 8f, 50)));

            case "eldervale_welcome" -> new Persona(150, 290, null,
                    beat("wave", 2, once(null, 0f, 1f, -4f)),
                    beat("look", 1, gaze(35, -4f, 4f, 40)));

            case "eldervale_upgrade" -> new Persona(140, 270, null,
                    beat("strike", 2, (l, s, c) -> {
                        l.hold(s, Material.IRON_PICKAXE, 38);
                        l.look(s, c, 0f, 22f, 3);
                        for (int i = 0; i < 2; i++) {
                            final int k = i;
                            l.step(s, 6 + k * 14, () -> {
                                l.swingNow(s);
                                l.sound(c.at(), Sound.BLOCK_ANVIL_USE, 0.12f, 0.8f + k * 0.1f);
                                c.w().spawnParticle(Particle.LAVA, c.front(0.7, 1.0), 1, 0.1, 0.05, 0.1, 0.0);
                            });
                        }
                        l.lookBack(s, 34);
                        return 40;
                    }),
                    beat("inspect", 1, gaze(0, 30f, 30f, 36)));

            case "merchant" -> new Persona(130, 250,
                    beat("crate", 1, merchantCrate()),
                    beat("haggle", 1.5, once(Sound.ENTITY_VILLAGER_TRADE, 0.16f, 1.3f, 0f)),
                    beat("crate", 1.5, merchantCrate()),
                    beat("count", 1, (l, s, c) -> {
                        l.look(s, c, 0f, 28f, 3);
                        l.step(s, 10, () -> l.sound(c.at(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.12f, 0.7f));
                        l.lookBack(s, 30);
                        return 34;
                    }));

            case "isle_clerk" -> new Persona(130, 250, null,
                    beat("file", 2, write(Material.PAPER, 44)),
                    beat("peer", 1, gaze(22, -4f, 0f, 36)));

            case "forage_pad_guide" -> new Persona(170, 330, null,
                    beat("bounce", 2, (l, s, c) -> {
                        l.pose(s, 8);
                        l.step(s, 10, () -> {
                            l.sound(c.at(), Sound.BLOCK_SLIME_BLOCK_STEP, 0.35f, 1.2f);
                            c.w().spawnParticle(Particle.ITEM_SLIME, c.at().clone().add(0, 0.1, 0), 5, 0.3, 0.05, 0.3, 0.0);
                        });
                        return 24;
                    }),
                    beat("look", 1, gaze(35, -8f, 2f, 40)));

            case "liquidator" -> new Persona(150, 290, null,
                    beat("chime", 1.5, (l, s, c) -> {
                        l.step(s, 2, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.26f,
                                    1.0f + (float) ThreadLocalRandom.current().nextDouble(0.3));
                        });
                        return 20;
                    }),
                    beat("appraise", 1, gaze(0, 28f, 28f, 36)));

            case "canopy_clerk" -> new Persona(140, 270, null,
                    beat("log", 2, (l, s, c) -> {
                        Material[] logs = {Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG};
                        l.hold(s, logs[ThreadLocalRandom.current().nextInt(logs.length)], 50);
                        l.look(s, c, 0f, 24f, 3);
                        l.step(s, 12, () -> l.sound(c.at(), Sound.BLOCK_WOOD_STEP, 0.2f, 1.1f));
                        l.lookBack(s, 44);
                        return 52;
                    }),
                    beat("canopy", 1, gaze(25, -24f, -16f, 44)));

            case "root_cellar" -> new Persona(140, 270, null,
                    beat("grind", 2, (l, s, c) -> {
                        l.look(s, c, 0f, 24f, 2);
                        l.step(s, 4, () -> {
                            l.swingNow(s);
                            l.sound(c.at(), Sound.BLOCK_GRINDSTONE_USE, 0.1f, 1.4f);
                            c.w().spawnParticle(Particle.WHITE_ASH, c.hands(), 6, 0.3, 0.1, 0.3, 0.0);
                        });
                        l.lookBack(s, 24);
                        return 30;
                    }),
                    beat("stock", 1, (l, s, c) -> {
                        l.hold(s, Material.BREAD, 40);
                        l.look(s, c, 0f, 20f, 3);
                        l.lookBack(s, 36);
                        return 42;
                    }));

            default -> null;
        };
    }

    private static BeatAction vexCut(boolean combo) {
        return (l, s, c) -> {
            l.look(s, c, 0f, 4f, 2);
            int strikes = combo ? 2 : 1;
            for (int i = 0; i < strikes; i++) {
                final int k = i;
                l.step(s, 4 + k * 12, () -> {
                    l.swingNow(s);
                    l.sound(c.at(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.2f, 1.15f + k * 0.1f);
                    c.w().spawnParticle(Particle.SWEEP_ATTACK, c.front(1.0, 1.0), 1, 0.0, 0.0, 0.0, 0.0);
                });
            }
            l.lookBack(s, combo ? 26 : 16);
            return combo ? 32 : 22;
        };
    }

    /** Cast → wait → splash → reel in. Optionally a fish comes up and is shown for a moment. */
    private static BeatAction fisherCast(boolean catchFish) {
        return (l, s, c) -> {
            int wait = 44 + ThreadLocalRandom.current().nextInt(30);
            l.look(s, c, 0f, 10f, 3);
            l.step(s, 4, () -> {
                l.swingNow(s);
                l.sound(c.at(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.28f, 0.9f);
            });
            l.step(s, wait, () -> l.sound(c.at(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.2f, 1.15f));
            l.step(s, wait + 12, () -> {
                l.swingNow(s);
                l.sound(c.at(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.28f, 1.0f);
                if (catchFish) {
                    l.hold(s, Material.COD, 34);
                    l.lookAbs(s, s.home.getYaw(), 2f, 2);
                }
            });
            if (catchFish) {
                l.step(s, wait + 22, () -> l.sound(c.at(), Sound.ENTITY_COD_FLOP, 0.26f, 1.0f));
            }
            int end = wait + (catchFish ? 44 : 20);
            l.lookBack(s, end);
            return end + 6;
        };
    }

    private static BeatAction sweep() {
        return (l, s, c) -> {
            l.look(s, c, 0f, 30f, 3);
            for (int i = 0; i < 2; i++) {
                final int k = i;
                l.step(s, 5 + k * 14, () -> {
                    l.swingNow(s);
                    l.sound(c.at(), Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.28f, 1.0f - k * 0.08f);
                    l.dust(c.front(0.9, 0.1), Material.GRAVEL, 4);
                });
            }
            l.lookBack(s, 34);
            return 38;
        };
    }

    private static BeatAction merchantCrate() {
        return (l, s, c) -> {
            l.hold(s, Material.CHEST, 50);
            l.look(s, c, 0f, 20f, 3);
            l.step(s, 10, () -> l.sound(c.at(), Sound.BLOCK_CHEST_LOCKED, 0.14f, 1.4f));
            l.lookBack(s, 44);
            return 52;
        };
    }

    /* =====================================================================
     * Head turns (eased, 2-tick steps)
     * ===================================================================== */

    /** Beat look: yaw offset from the NPC's current front, absolute pitch. */
    private void look(State s, Ctx c, float yawOffset, float pitch, int steps) {
        LivingNpcService living = living();
        if (living != null) {
            startTurn(living, s, c.at(), c.baseYaw() + yawOffset, pitch, steps);
        }
    }

    /** Look used from delayed beat steps (fresh location). */
    private void lookAbs(State s, float yaw, float pitch, int steps) {
        LivingNpcService living = living();
        if (living == null) {
            return;
        }
        Location at = living.locationOf(s.id);
        if (at != null) {
            startTurn(living, s, at, yaw, pitch, steps);
        }
    }

    private void lookBack(State s, int afterTicks) {
        s.lookBackAt = now + afterTicks;
    }

    private void startTurn(LivingNpcService living, State s, Location at, float yaw, float pitch, int steps) {
        s.turnFromYaw = at.getYaw();
        s.turnFromPitch = at.getPitch();
        s.turnToYaw = yaw;
        s.turnToPitch = Math.max(-40f, Math.min(45f, pitch));
        s.turnStep = 0;
        s.turnSteps = Math.max(1, steps);
        stepTurn(living, s, at);
    }

    private void stepTurn(LivingNpcService living, State s, Location at) {
        s.turnStep++;
        double t = ease(Math.min(1.0, s.turnStep / (double) s.turnSteps));
        float dYaw = wrap(s.turnToYaw - s.turnFromYaw);
        Location look = at.clone();
        look.setYaw(s.turnFromYaw + (float) (dYaw * t));
        look.setPitch(s.turnFromPitch + (float) ((s.turnToPitch - s.turnFromPitch) * t));
        living.moveTo(s.id, look);
        if (s.turnStep >= s.turnSteps) {
            s.turnSteps = 0;
            s.headOff = Math.abs(wrap(s.turnToYaw - s.home.getYaw())) > 1f
                    || Math.abs(s.turnToPitch - s.home.getPitch()) > 1f;
        } else {
            s.headOff = true;
        }
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }

    /* =====================================================================
     * Patrols — only for NPCs with a hand-picked, short route
     * ===================================================================== */

    /** Route offsets (dx, dz) from home. Kept tiny on purpose. */
    private static List<double[]> routeFor(String id) {
        return switch (id) {
            case "egon" -> List.of(new double[] {1.6, 0.0}, new double[] {-1.6, 0.0});
            case "vex" -> List.of(new double[] {2.5, 0.0}, new double[] {-2.5, 0.0});
            case "farmer" -> List.of(new double[] {2.0, 1.0}, new double[] {-1.0, -1.5});
            case "fishmonger" -> List.of(new double[] {0.0, 1.4});
            case "merchant" -> List.of(new double[] {1.2, 0.0});
            case "arena_proctor" -> List.of(new double[] {1.5, 1.0});
            case "street_sweeper" -> List.of(new double[] {2.5, 0.0}, new double[] {0.0, 2.5}, new double[] {-2.0, -1.0});
            case "town_crier" -> List.of(new double[] {1.5, 0.0}, new double[] {-1.5, 0.0});
            default -> null;
        };
    }

    private void beginPatrol(LivingNpcService living, State s, Location at) {
        if (!loaded(s.home)) {
            return;
        }
        double[] off = s.route.get(ThreadLocalRandom.current().nextInt(s.route.size()));
        Location target = s.home.clone().add(off[0], 0, off[1]);
        Location ground = LivingNpcService.plantFeet(target, s.home.getY());
        if (!safeGround(ground, s.home)) {
            s.patrolBroken = true;
            plugin.getLogger().fine("Patrol disabled for " + s.id + " (unsafe ground)");
            return;
        }
        living.setMovementLocked(s.id, true);
        s.leg = 0;
        s.walkFrom = at.clone();
        s.walkTo = ground;
        s.walkU = 0;
        s.hurry = false;
        s.needTurn = true;
        s.patrolPauseUntil = 0;
        s.lookBackAt = -1;
    }

    private void patrolTick(LivingNpcService living, State s, Location at) {
        if (busy(s.id)) {
            // Talk is sacred: be exactly where the conversation expects us.
            finishPatrol(living, s, true);
            return;
        }
        if (s.walkTo == null || !loaded(s.walkTo) || !loaded(s.home)) {
            // Players left and the chunk went to sleep: no block reads, straight home.
            finishPatrol(living, s, true);
            return;
        }
        if (!s.hurry && anyone(at, 3.0)) {
            // Someone wants a word: stop the job, walk (not teleport) the last steps home.
            s.headOff = false; // the walk step sets the heading; no snap to the counter first
            calm(living, s);
            s.calm = false;
            s.hurry = true;
            s.leg = 1;
            s.walkFrom = at.clone();
            s.walkTo = s.home.clone();
            s.walkU = 0;
            s.needTurn = false;
            s.patrolPauseUntil = 0;
        }
        if (s.turnSteps > 0) {
            stepTurn(living, s, at);
            return;
        }
        if (now < s.patrolPauseUntil) {
            return;
        }
        if (s.needTurn) {
            s.needTurn = false;
            startTurn(living, s, at, yaw(s.walkFrom, s.walkTo), 8f, 3);
            s.patrolPauseUntil = now + 4;
            return;
        }
        double dist = s.walkFrom.distance(s.walkTo);
        if (dist < 0.05) {
            arrive(living, s);
            return;
        }
        double speed = s.hurry ? HURRY : WALK;
        s.walkU = Math.min(1.0, s.walkU + (speed * PERIOD) / dist);
        double t = s.hurry ? s.walkU : ease(s.walkU);
        Location next = s.walkFrom.clone().add(s.walkTo.clone().subtract(s.walkFrom).toVector().multiply(t));
        Location feet = LivingNpcService.plantFeet(next, s.walkFrom.getY());
        if (!safeGround(feet, s.home)) {
            finishPatrol(living, s, true);
            s.patrolBroken = true;
            return;
        }
        feet.setYaw(yaw(s.walkFrom, s.walkTo));
        feet.setPitch(s.hurry ? 0f : 8f);
        living.moveTo(s.id, feet);
        living.followName(s.id, feet);
        s.headOff = true;
        if ("street_sweeper".equals(s.id) && !s.hurry && s.leg == 0 && now - s.lastWalkFx >= 16
                && s.walkU > 0.15 && s.walkU < 0.85) {
            // Sweeps as he goes: brush stroke + a little grit.
            s.lastWalkFx = now;
            swingNow(s);
            sound(feet, Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.22f, 1.05f);
            dust(feet.clone().add(0, 0.1, 0), Material.GRAVEL, 3);
        }
        if (s.walkU >= 1.0) {
            arrive(living, s);
        }
    }

    private void arrive(LivingNpcService living, State s) {
        if (s.leg == 0 && !s.hurry) {
            // Far point: one job beat, a breath, turn around, stroll home.
            s.leg = 1;
            int dur = 0;
            Location at = living.locationOf(s.id);
            Persona p = s.persona;
            if (at != null && p != null && p.patrolBeat() != null) {
                dur = p.patrolBeat().act().run(this, s, ctx(s, at));
                s.lastBeat = p.patrolBeat().key();
            }
            s.patrolPauseUntil = now + Math.max(24, dur + 10) + ThreadLocalRandom.current().nextInt(30);
            s.walkFrom = s.walkTo.clone();
            s.walkTo = s.home.clone();
            s.walkU = 0;
            s.needTurn = true;
            return;
        }
        finishPatrol(living, s, false);
    }

    private void finishPatrol(LivingNpcService living, State s, boolean snapHome) {
        s.gen++;
        Location home = s.home.clone();
        Location cur = living.locationOf(s.id);
        boolean turnAfter = !snapHome && cur != null;
        if (turnAfter) {
            // Arrive facing the way we walked, then turn to the counter — no snap.
            home.setYaw(cur.getYaw());
            home.setPitch(cur.getPitch());
        }
        living.moveTo(s.id, home);
        living.followName(s.id, home);
        s.leg = -1;
        s.walkFrom = null;
        s.walkTo = null;
        s.hurry = false;
        s.needTurn = false;
        s.lookBackAt = -1;
        s.turnSteps = 0;
        if (s.restoreHandAt >= 0) {
            s.restoreHandAt = -1;
            restoreHand(s.id);
        }
        if (s.standAt >= 0) {
            s.standAt = -1;
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0) {
            s.stopUseAt = -1;
            attribute(s.id, "use_item", "none");
        }
        living.setMovementLocked(s.id, false);
        if (turnAfter) {
            startTurn(living, s, home, s.home.getYaw(), s.home.getPitch(), 4);
        } else {
            s.headOff = false;
        }
        s.busyUntil = now + 10;
        s.nextBeat = Math.max(s.nextBeat, now + 60 + ThreadLocalRandom.current().nextInt(60));
        AetherionQuests p = AetherionQuests.getInstance();
        if (p != null && p.getMarkerManager() != null) {
            p.getMarkerManager().refreshNpc(s.id);
        }
    }

    private static boolean loaded(Location at) {
        return at != null && at.getWorld() != null
                && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    private static boolean safeGround(Location feet, Location home) {
        if (feet == null || feet.getWorld() == null || home == null) {
            return false;
        }
        if (Math.abs(feet.getY() - home.getY()) > 1.05) {
            return false;
        }
        Block below = feet.clone().add(0, -0.5, 0).getBlock();
        Block at = feet.getBlock();
        Block head = feet.clone().add(0, 1, 0).getBlock();
        return below.getType().isSolid()
                && !at.isLiquid() && !at.getType().isSolid()
                && !head.getType().isSolid()
                && below.getType() != Material.MAGMA_BLOCK && below.getType() != Material.CAMPFIRE;
    }

    private static float yaw(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /* =====================================================================
     * Talk hooks (per player)
     * ===================================================================== */

    /**
     * Body language for a spoken line. The NPC is put at rest first; an exclamation usually
     * gets one arm gesture, anything else only rarely — and never more than one per ~2.5 s.
     */
    public void gesture(Player player, String npcId, String line) {
        if (player == null || npcId == null) {
            return;
        }
        calmForTalk(npcId);
        State s = states.get(npcId.toLowerCase(Locale.ROOT));
        int tick = Bukkit.getCurrentTick();
        if (s != null && tick - s.lastGesture < GESTURE_GAP) {
            return;
        }
        boolean exclaim = line != null && line.indexOf('!') >= 0;
        if (ThreadLocalRandom.current().nextDouble() < (exclaim ? 0.75 : 0.12)) {
            swingFor(player, npcId);
            if (s != null) {
                s.lastGesture = tick;
            }
        }
    }

    /** Small symbol that floats up from the NPC's head (only this player sees it). */
    public void emote(Player player, String npcId, String symbol) {
        if (player == null || npcId == null || symbol == null) {
            return;
        }
        calmForTalk(npcId);
        Location at = NpcPresence.locate(npcId);
        if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
            return;
        }
        Location start = at.clone().add(0.35, 2.05, 0);
        TextDisplay display;
        try {
            display = at.getWorld().spawn(start, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.addScoreboardTag(TalkUx.TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(20);
                d.text(de.aetherion.quests.talk.TalkGlyphs.emote(symbol));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            });
        } catch (RuntimeException ex) {
            return;
        }
        player.showEntity(plugin, display);
        later(2, () -> {
            if (display.isValid()) {
                display.teleport(start.clone().add(0, 0.4, 0));
            }
        });
        later(26, () -> {
            if (display.isValid()) {
                display.remove();
            }
        });
    }

    /** Quest handed in: heart + sparkle burst for this player. */
    public void celebrate(Player player, String npcId) {
        emote(player, npcId, "♥");
        Location at = NpcPresence.locate(npcId);
        if (at == null || player == null || !at.getWorld().equals(player.getWorld())) {
            return;
        }
        player.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0), 14, 0.45, 0.6, 0.45, 0.0);
        swingFor(player, npcId);
    }

    /* =====================================================================
     * FancyNpcs glue (reflection via Core facade)
     * ===================================================================== */

    /**
     * Arm swing seen by everyone watching the NPC (external callers: greetings, banter).
     * Skipped mid-conversation and rate-limited so overlapping systems can't flail the arm.
     */
    public void swing(String npcId) {
        if (npcId == null || busy(npcId)) {
            return;
        }
        State s = states.get(npcId.toLowerCase(Locale.ROOT));
        int tick = Bukkit.getCurrentTick();
        if (s != null) {
            if (tick - s.lastSwing < EXTERNAL_SWING_GAP) {
                return;
            }
            s.lastSwing = tick;
        }
        Object npc = living().fancy(npcId);
        if (npc != null) {
            FancyNpcFacade.updateForAll(npc, true);
        }
    }

    /** Beat swing: limited to one per {@link #SWING_GAP} ticks per NPC. */
    private void swingNow(State s) {
        int tick = Bukkit.getCurrentTick();
        if (tick - s.lastSwing < SWING_GAP) {
            return;
        }
        s.lastSwing = tick;
        Object npc = living().fancy(s.id);
        if (npc != null) {
            FancyNpcFacade.updateForAll(npc, true);
        }
    }

    /** Arm swing only this player sees (conversation gestures). */
    public void swingFor(Player player, String npcId) {
        Object npc = living().fancy(npcId);
        if (npc == null || player == null) {
            return;
        }
        try {
            npc.getClass().getMethod("update", Player.class, boolean.class).invoke(npc, player, true);
        } catch (ReflectiveOperationException ex) {
            FancyNpcFacade.updateForAll(npc, true);
        }
    }

    /** Run a later step of a beat — dropped if the NPC went calm / was re-spawned meanwhile. */
    private void step(State s, int delay, Runnable r) {
        final int gen = s.gen;
        Runnable guarded = () -> {
            if (s.gen == gen && !s.calm && states.get(s.id) == s && !busy(s.id)) {
                r.run();
            }
        };
        if (delay <= 0) {
            guarded.run();
        } else {
            later(delay, guarded);
        }
    }

    private void hold(State s, Material item, int ticks) {
        living().setMainHand(s.id, new ItemStack(item));
        s.restoreHandAt = now + ticks;
    }

    private void restoreHand(String npcId) {
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        living().setMainHand(npcId, profile == null ? null : profile.handItem());
    }

    /** Crouch is seasoning: long per-NPC cooldown, short hold. */
    private boolean crouch(State s, int ticks) {
        if (now < s.crouchReadyAt) {
            return false;
        }
        if (pose(s, Math.min(ticks, 40))) {
            s.crouchReadyAt = now + CROUCH_COOLDOWN + ThreadLocalRandom.current().nextInt(600);
            return true;
        }
        return false;
    }

    /** Raw crouch (no cooldown) — only for beats where the crouch IS the job (pad bounce). */
    private boolean pose(State s, int ticks) {
        if (attribute(s.id, "pose", "crouching")) {
            s.standAt = now + ticks;
            return true;
        }
        return false;
    }

    private boolean snack(State s) {
        if (now < s.snackReadyAt) {
            return false;
        }
        s.snackReadyAt = now + SNACK_COOLDOWN + ThreadLocalRandom.current().nextInt(1200);
        return true;
    }

    private void use(State s, int ticks) {
        if (attribute(s.id, "use_item", "main_hand")) {
            s.stopUseAt = now + ticks;
        }
    }

    private void dust(Location at, Material block, int count) {
        if (at != null && at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.BLOCK, at, count, 0.25, 0.05, 0.25, 0.0, block.createBlockData());
        }
    }

    private void restoreAll(State s) {
        s.gen++;
        if (s.restoreHandAt >= 0) {
            restoreHand(s.id);
        }
        if (s.standAt >= 0) {
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0) {
            attribute(s.id, "use_item", "none");
        }
        LivingNpcService living = living();
        if (living == null) {
            return;
        }
        if (s.leg >= 0) {
            living.moveTo(s.id, s.home);
            living.setMovementLocked(s.id, false);
        } else if (s.headOff) {
            Location at = living.locationOf(s.id);
            if (at != null) {
                at.setYaw(s.home.getYaw());
                at.setPitch(s.home.getPitch());
                living.moveTo(s.id, at);
            }
        }
    }

    /** Set a FancyNpcs attribute (pose / use_item) and push it to viewers. */
    private boolean attribute(String npcId, String name, String value) {
        Object npc = living().fancy(npcId);
        if (npc == null) {
            return false;
        }
        try {
            Object api = FancyNpcFacade.plugin();
            Object attributes = api.getClass().getMethod("getAttributeManager").invoke(api);
            Object attr = attributes.getClass()
                    .getMethod("getAttributeByName", EntityType.class, String.class)
                    .invoke(attributes, EntityType.PLAYER, name);
            if (attr == null) {
                return false;
            }
            Object data = FancyNpcFacade.data(npc);
            Class<?> attrClass = Class.forName("de.oliver.fancynpcs.api.NpcAttribute");
            data.getClass().getMethod("addAttribute", attrClass, String.class).invoke(data, attr, value);
            attr.getClass().getMethod("apply", FancyNpcFacade.npcClass(), String.class).invoke(attr, npc, value);
            FancyNpcFacade.updateForAll(npc, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private void sound(Location at, Sound sound, float volume, float pitch) {
        if (at != null && at.getWorld() != null) {
            at.getWorld().playSound(at, sound, SoundCategory.NEUTRAL, volume, pitch);
        }
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(1L, ticks));
    }

    private static List<Player> viewers(Location at, double radius) {
        List<Player> out = new ArrayList<>(2);
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean anyone(Location at, double radius) {
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= r2) {
                return true;
            }
        }
        return false;
    }
}
