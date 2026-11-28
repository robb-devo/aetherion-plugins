package de.aetherion.tutorial.isle;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.combat.BossTells;
import de.aetherion.bossengine.combat.Parries;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Old Clanger: the hermit crab who wears the harbour bell. The last lesson, and the first boss.
 *
 * <p>He fights in the language Hilde's golem taught a minute ago: a yellow "!" is a claw jab (parry it and he lands
 * on his back), a red "!!" is the bell coming down (roll out of it and he sits there dizzy). Every hit on him rings.
 * Nobody loses to him: knock a player down and he waits, bell shut, until they are on their feet again; each time
 * he waits he also slows down a little. Beaten, he gives the bell back and walks it home.
 *
 * <p>The fight is one player's own. His body is shown to that player only, other players are hidden from them
 * while it lasts, and his hits go through {@link BossHits}, so parry and roll work exactly as on any boss.
 */
final class Boss implements Listener {

    private static final double MAX_HP = 200.0;
    /** While he is up, the bell takes almost all of a blow: only on his back or dizzy is he really open. */
    private static final double GUARDED = 0.10;
    private static final double WAKE_RANGE = 8.5;
    private static final double JAB_REACH = 4.5;
    private static final double SLAM_RADIUS = 4.7;
    private static final double ROAM = 7.0;
    private static final TextColor YELLOW = TextColor.color(0xFFD24A);
    private static final TextColor RED = TextColor.color(0xFF5A4A);
    private static final Particle.DustOptions SLAM_RED = new Particle.DustOptions(Color.fromRGB(0xFF5A4A), 1.5f);
    private static final Particle.DustOptions SLAM_WHITE = new Particle.DustOptions(Color.fromRGB(0xFFFFFF), 1.5f);
    private static final Particle.DustOptions SPARK = new Particle.DustOptions(Color.fromRGB(0xFFD86A), 1.1f);
    private static final Key COMBAT_FONT = Key.key("aetherion", "combat");

    private enum Mode { ASLEEP, WAKING, IDLE, JAB_WINDUP, JAB_RECOVER, SLAM_UP, SLAM_DOWN, DIZZY, REELING, BREATHER, YIELD, GONE }

    private enum Move { JAB, SLAM }

    private static final Move[][] PATTERN = {
            {Move.JAB, Move.JAB, Move.SLAM},
            {Move.JAB, Move.SLAM, Move.JAB, Move.JAB, Move.SLAM},
            {Move.SLAM, Move.JAB, Move.SLAM, Move.SLAM, Move.JAB}
    };

    private final class Fight {
        final Player player;
        final Session session;
        final ClangerRig rig;
        final ClangerRig.Pose pose = new ClangerRig.Pose();
        final Set<UUID> hidden = new HashSet<>();
        IronGolem body;
        BossBar bar;
        TextDisplay tell;
        boolean tellRoll;
        boolean tellPop;
        float tellSize;
        Mode mode = Mode.ASLEEP;
        int t;
        int length;
        int rest;
        int moves;
        int kite;
        int mercy;
        int age;
        double hp;
        double slamX;
        double slamZ;
        long downedTick = -1L;
        boolean taughtParry;
        boolean taughtRoll;
        int blunted;

        Fight(Player player, Session session, Location anchor) {
            this.player = player;
            this.session = session;
            this.rig = new ClangerRig(plugin, player, anchor);
        }

        int phase() {
            double share = hp / MAX_HP;
            return share > 0.62 ? 0 : share > 0.28 ? 1 : 2;
        }

        void mode(Mode next, int ticks) {
            mode = next;
            t = 0;
            length = ticks;
        }
    }

    private final TutorialPlugin plugin;
    private final Isle isle;
    private final Map<UUID, Fight> fights = new HashMap<>();
    private final char perilGlyph;
    private final char finisherGlyph;

    Boss(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
        this.perilGlyph = glyph("PERIL");
        this.finisherGlyph = glyph("FINISHER");
        sweep();
    }

    /** The screen glyphs of the combat kit (the red peril mark, the finisher slash) as BossEngine draws them. */
    private static char glyph(String name) {
        try {
            java.lang.reflect.Field field = BossTells.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getChar(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return 0;
        }
    }

    /** Leftovers of a crash or a reload: nothing of him may stand around ownerless. */
    private void sweep() {
        for (Entity entity : isle.world().getEntities()) {
            if (ClangerRig.ours(entity)) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------ in and out

    boolean fighting(Player player) {
        Fight fight = player == null ? null : fights.get(player.getUniqueId());
        return fight != null && fight.mode != Mode.ASLEEP && fight.mode != Mode.GONE && fight.mode != Mode.YIELD;
    }

    /** The player stepped onto the Flat: the bell lies there, asleep, until they come close. */
    void begin(Player player, Session session) {
        if (fights.containsKey(player.getUniqueId())) {
            return;
        }
        Location centre = isle.marks().at(isle.world(), "clanger");
        if (centre == null) {
            plugin.getLogger().warning("The island has no spot 'clanger': the boss is skipped.");
            isle.director().bossDone(player, session);
            return;
        }
        Fight fight;
        try {
            fight = new Fight(player, session, centre);
            fight.pose.x = centre.getX();
            fight.pose.y = centre.getY();
            fight.pose.z = centre.getZ();
            fight.pose.yaw = yawTo(centre, player.getLocation());
            fight.pose.tuck = 1.0;
            fight.hp = session.bossHp > 0.0 ? session.bossHp : MAX_HP;
            fight.body = hitbox(fight, centre);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Old Clanger could not be put up (" + ex + "): the boss is skipped.");
            isle.director().bossDone(player, session);
            return;
        }
        if (fight.body == null) {
            fight.rig.remove();
            plugin.getLogger().warning("Old Clanger's body was refused by another plugin: the boss is skipped.");
            isle.director().bossDone(player, session);
            return;
        }
        fights.put(player.getUniqueId(), fight);
        fight.rig.pose(fight.pose, 0);
    }

    /**
     * What a sword actually hits: an unseen, unmoving iron golem scaled to the bell's width. A golem and not a
     * monster on purpose: Items clears every hostile mob that stands around outside a combat zone, and a server set
     * to peaceful removes them too.
     */
    private IronGolem hitbox(Fight fight, Location at) {
        World world = at.getWorld();
        IronGolem golem = world.spawn(at, IronGolem.class, g -> {
            g.setAI(false);
            g.setSilent(true);
            g.setInvisible(true);
            g.setPersistent(false);
            g.setRemoveWhenFarAway(false);
            g.setCollidable(false);
            g.setGravity(false);
            g.setPlayerCreated(true);
            g.setVisibleByDefault(false);
            g.setCanPickupItems(false);
            g.addScoreboardTag(ClangerRig.TAG);
            // Items dresses every wild mob (makes it visible, hangs a health label over it); "NPC" opts out
            g.addScoreboardTag("NPC");
            AttributeInstance scale = g.getAttribute(Attribute.GENERIC_SCALE);
            if (scale != null) {
                scale.setBaseValue(1.75);
            }
            AttributeInstance health = g.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(1000.0);
            }
            AttributeInstance knock = g.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (knock != null) {
                knock.setBaseValue(1.0);
            }
        });
        if (!golem.isValid()) {
            golem.remove();
            return null;
        }
        golem.setHealth(1000.0);
        fight.player.showEntity(plugin, golem);
        return golem;
    }

    /** Staff and test rigs: he gives up now (to look at the ending without fighting for it). */
    boolean concede(Player player) {
        Fight fight = fights.get(player.getUniqueId());
        if (fight == null || fight.mode == Mode.YIELD || fight.mode == Mode.GONE) {
            return false;
        }
        if (fight.mode == Mode.ASLEEP) {
            fight.pose.tuck = 0.0;
        }
        fight.hp = 0.0;
        giveUp(fight);
        return true;
    }

    /** The player is leaving (quit) or was moved on by staff: everything of the fight goes, his health is kept. */
    void abort(Player player) {
        Fight fight = fights.remove(player.getUniqueId());
        if (fight == null) {
            return;
        }
        if (fight.mode != Mode.YIELD && fight.mode != Mode.GONE && fight.mode != Mode.ASLEEP) {
            fight.session.bossHp = fight.hp;
        }
        end(fight);
    }

    private void end(Fight fight) {
        clearTell(fight);
        fight.rig.remove();
        if (fight.body != null && fight.body.isValid()) {
            fight.body.remove();
        }
        if (fight.bar != null) {
            fight.player.hideBossBar(fight.bar);
            fight.bar = null;
        }
        for (UUID id : fight.hidden) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && fight.player.isOnline()) {
                fight.player.showPlayer(plugin, other);
            }
        }
        fight.hidden.clear();
    }

    void shutdown() {
        for (Fight fight : new ArrayList<>(fights.values())) {
            end(fight);
        }
        fights.clear();
    }

    /**
     * It would have been the end of the player. On the Flat it is a pause: he shuts himself in the bell and waits
     * until they are on their feet again, and he is a little slower afterwards.
     */
    void breather(Player player) {
        Fight fight = fights.get(player.getUniqueId());
        IsleRules.heal(player);
        player.setFireTicks(0);
        if (fight == null) {
            return;
        }
        fight.downedTick = Bukkit.getCurrentTick();
        fight.mercy = Math.min(3, fight.mercy + 1);
        clearTell(fight);
        fight.mode(Mode.BREATHER, 90);
        de.aetherion.tutorial.TutorialStore.Entry entry = plugin.store().get(player.getUniqueId());
        entry.deaths++;
        plugin.store().save(player.getUniqueId(), entry);
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.8f, 0.6f);
        Guide.toast(player, Txt.t(player, "Catch your breath", "Atme durch"),
                Txt.t(player, "§7He waits in his bell. Nobody loses on Morningbell.", "§7Er wartet in seiner Glocke. Auf Morningbell verliert niemand."),
                Txt.t(player, "Yellow ! = F   ·   Red !! = Sneak + Jump", "Gelbes ! = F   ·   Rotes !! = Schleichen + Springen"));
    }

    // ------------------------------------------------------------------ every tick

    void tick() {
        if (fights.isEmpty()) {
            return;
        }
        for (Fight fight : new ArrayList<>(fights.values())) {
            try {
                tick(fight);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Old Clanger tripped; the fight ends", ex);
                fights.remove(fight.player.getUniqueId());
                end(fight);
                if (fight.player.isOnline()) {
                    isle.director().bossDone(fight.player, fight.session);
                }
            }
        }
    }

    private void tick(Fight f) {
        Player p = f.player;
        Location centre = isle.marks().at(isle.world(), "clanger");
        if (!p.isOnline() || p.getWorld() != isle.world() || centre == null || !f.rig.valid()) {
            abort(p);
            return;
        }
        double arena = isle.marks().radius("arena", 13.0);
        Location at = p.getLocation();
        double fromCentre = Math.hypot(at.getX() - centre.getX(), at.getZ() - centre.getZ());
        if (fromCentre > arena + 10.0 && f.mode != Mode.YIELD && f.mode != Mode.GONE) {
            // walked off the Flat: he goes back to sleep where he stands, his bruises stay
            abort(p);
            isle.director().retreat(p, f.session);
            return;
        }
        f.age++;
        f.t++;
        if (f.age % 20 == 1) {
            hideOthers(f, centre, arena);
        }
        ClangerRig.Pose pose = f.pose;
        pose.ring = Math.max(0.0, pose.ring - 0.25);
        double dx = at.getX() - pose.x;
        double dz = at.getZ() - pose.z;
        double dist = Math.hypot(dx, dz);
        float toPlayer = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pose.glance = Math.max(-1.0, Math.min(1.0, -angle(toPlayer - pose.yaw) / 50.0));

        switch (f.mode) {
            case ASLEEP -> asleep(f, dist);
            case WAKING -> waking(f);
            case IDLE -> idle(f, centre, dist, toPlayer);
            case JAB_WINDUP -> jabWindup(f, dist, toPlayer);
            case JAB_RECOVER -> jabRecover(f);
            case SLAM_UP -> slamUp(f, centre);
            case SLAM_DOWN -> slamDown(f);
            case DIZZY -> dizzy(f);
            case REELING -> reeling(f);
            case BREATHER -> resting(f);
            case YIELD -> walkHome(f, centre);
            default -> {
            }
        }
        if (f.mode == Mode.GONE) {
            return;
        }
        if (f.body == null || !f.body.isValid()) {
            // something took his body away (a cleanup of another plugin): he gets a new one, the fight goes on
            f.body = hitbox(f, new Location(isle.world(), pose.x, pose.y + pose.lift, pose.z));
            if (f.body == null && f.age % 100 == 0) {
                plugin.getLogger().warning("Old Clanger has no body to hit for " + p.getName() + " (spawn refused).");
            }
        }
        if (f.body != null && f.body.isValid()) {
            Location hit = new Location(isle.world(), pose.x, pose.y + pose.lift, pose.z);
            if (f.body.getLocation().distanceSquared(hit) > 0.0004) {
                f.body.teleport(hit);
            }
            // asleep he can be knocked on (that wakes him); in the air, shut in or giving up nothing reaches him
            boolean open = f.mode == Mode.ASLEEP || f.mode == Mode.IDLE || f.mode == Mode.JAB_WINDUP
                    || f.mode == Mode.JAB_RECOVER || f.mode == Mode.DIZZY || f.mode == Mode.REELING;
            if (f.body.isInvulnerable() == open) {
                f.body.setInvulnerable(!open);
            }
            if (f.body.getHealth() < 900.0) {
                f.body.setHealth(1000.0);
            }
        }
        if (f.tell != null && f.tell.isValid()) {
            TellSpot spot = tellSpot(f, f.tellRoll);
            if (f.tell.getLocation().distanceSquared(spot.at()) > 0.01) {
                f.tell.teleport(spot.at());
            }
            float size = spot.scale() * (f.tellPop ? 1.24f : 1.0f);
            if (Math.abs(size - f.tellSize) > 0.04f) {
                f.tellSize = size;
                f.tell.setTransformation(scale(size));
            }
        }
        f.rig.pose(pose, 2);
        if (f.bar != null) {
            f.bar.progress((float) Math.max(0.0, Math.min(1.0, f.hp / MAX_HP)));
        }
    }

    private void hideOthers(Fight f, Location centre, double arena) {
        double reach = (arena + 14.0) * (arena + 14.0);
        for (Player other : isle.world().getPlayers()) {
            if (other == f.player || f.hidden.contains(other.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(centre) <= reach) {
                f.player.hidePlayer(plugin, other);
                f.hidden.add(other.getUniqueId());
            }
        }
    }

    // ------------------------------------------------------------------ the bell on the sand

    private void asleep(Fight f, double dist) {
        ClangerRig.Pose pose = f.pose;
        pose.tuck = 1.0;
        pose.lift = 0.0;
        // a bell does not snore. This one does.
        pose.wobble = Math.sin(f.age / 14.0) * 1.2;
        if (f.age % 60 == 30) {
            f.player.playSound(spot(f), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.25f, 1.6f);
        }
        if (dist < WAKE_RANGE) {
            wake(f);
        }
    }

    private void wake(Fight f) {
        f.mode(Mode.WAKING, 46);
        f.player.playSound(spot(f), Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.4f, 0.7f);
        f.player.playSound(spot(f), Sound.ENTITY_TURTLE_EGG_CRACK, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }

    private void waking(Fight f) {
        ClangerRig.Pose pose = f.pose;
        Player p = f.player;
        int t = f.t;
        pose.wobble = t < 14 ? Math.sin(t * 1.9) * 7.0 : 0.0;
        pose.tuck = t < 14 ? 1.0 : Math.max(0.0, 1.0 - (t - 14) / 12.0);
        pose.eyes = t < 26 ? 0.1 : t < 36 ? 1.7 : 1.0;
        pose.yaw = turn(pose.yaw, yawTo(spot(f), p.getLocation()), 12f);
        if (t == 14 || t == 18 || t == 22) {
            p.playSound(spot(f), Sound.BLOCK_BONE_BLOCK_HIT, SoundCategory.HOSTILE, 1.0f, 0.8f + (t - 14) * 0.05f);
        }
        if (t == 27) {
            p.playSound(spot(f), Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 2.0f, 0.55f);
            p.playSound(spot(f), Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 0.5f, 1.7f);
            pose.ring = 1.0;
            p.showTitle(Title.title(Component.text("OLD CLANGER", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Txt.legacy(Txt.t(p, "§7wears the harbour bell", "§7trägt die Hafenglocke")),
                    Title.Times.times(Ticks.duration(6), Ticks.duration(34), Ticks.duration(10))));
        }
        if (t >= f.length) {
            f.bar = BossBar.bossBar(Txt.legacy(BarChrome.legacy(BarChrome.Family.BOSS, BarChrome.Icon.CROWN, "§6Old Clanger")),
                    (float) (f.hp / MAX_HP), BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);
            p.showBossBar(f.bar);
            f.rest = 26;
            f.mode(Mode.IDLE, 0);
            Txt.actionBar(p, Txt.t(p, "§eYellow !§7 = parry with §fF§8   ·   §cRed !!§7 = roll with §fSneak + Jump",
                    "§eGelbes !§7 = parieren mit §fF§8   ·   §cRotes !!§7 = rollen mit §fSchleichen + Springen"));
        }
    }

    // ------------------------------------------------------------------ circling

    private void idle(Fight f, Location centre, double dist, float toPlayer) {
        ClangerRig.Pose pose = f.pose;
        int phase = f.phase();
        pose.tuck = Math.max(0.0, pose.tuck - 0.12);
        pose.tilt = Math.max(0.0, pose.tilt - 4.0);
        pose.wobble *= 0.6;
        pose.rear = Math.max(0.0, pose.rear - 0.15);
        pose.thrust = Math.max(0.0, pose.thrust - 0.15);
        pose.eyes = 1.0;
        pose.dizzy = false;
        pose.yaw = turn(pose.yaw, toPlayer, 8f);
        double speed = phase == 0 ? 0.10 : phase == 1 ? 0.125 : 0.145;
        boolean walking = dist > 3.1;
        if (walking) {
            double nx = pose.x - Math.sin(Math.toRadians(pose.yaw)) * speed;
            double nz = pose.z + Math.cos(Math.toRadians(pose.yaw)) * speed;
            double out = Math.hypot(nx - centre.getX(), nz - centre.getZ());
            if (out <= ROAM) {
                pose.x = nx;
                pose.z = nz;
            } else {
                walking = false;
            }
        }
        pose.walk = pose.walk + ((walking ? 1.0 : 0.0) - pose.walk) * 0.3;
        pose.stride += 0.42 * (0.35 + pose.walk);
        if (walking && f.age % 6 == 0) {
            f.player.playSound(spot(f), Sound.ENTITY_SPIDER_STEP, SoundCategory.HOSTILE, 0.5f, 0.6f);
        }
        if (f.rest > 0) {
            f.rest--;
            return;
        }
        Move[] pattern = PATTERN[phase];
        Move next = pattern[f.moves % pattern.length];
        if (next == Move.JAB && dist > 3.9) {
            // kept at arm's length: after a while he stops chasing and jumps
            if (++f.kite < 70) {
                return;
            }
            next = Move.SLAM;
        }
        f.kite = 0;
        f.moves++;
        if (next == Move.JAB) {
            startJab(f);
        } else {
            startSlam(f);
        }
    }

    // ------------------------------------------------------------------ the claw

    private void startJab(Fight f) {
        int phase = f.phase();
        int windup = (phase == 0 ? 26 : phase == 1 ? 22 : 19) + f.mercy * 4;
        f.mode(Mode.JAB_WINDUP, windup);
        showTell(f, false);
        Player p = f.player;
        p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.HOSTILE, 1.4f, 1.6f);
        p.playSound(spot(f), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.HOSTILE, 1.2f, 1.2f);
        if (!f.taughtParry) {
            f.taughtParry = true;
            Txt.actionBar(p, Txt.t(p, "§eYellow !§7: press §fF §7when it turns white", "§eGelbes !§7: drück §fF§7, wenn es weiß wird"));
        }
    }

    private void jabWindup(Fight f, double dist, float toPlayer) {
        ClangerRig.Pose pose = f.pose;
        Player p = f.player;
        int left = f.length - f.t;
        pose.walk *= 0.6;
        pose.rear = Math.min(1.0, f.t / Math.max(1.0, f.length * 0.55));
        pose.eyes = left <= 5 ? 1.6 : 0.6;
        pose.yaw = turn(pose.yaw, toPlayer, left > 6 ? 3.5f : 0.8f);
        if (left > 5 && f.t % 4 == 0) {
            p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.HOSTILE, 0.8f, 1.0f + (1.0f - left / (float) f.length) * 0.9f);
        }
        if (left == 5) {
            whiten(f);
            p.playSound(spot(f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.6f, 1.9f);
            p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.HOSTILE, 1.3f, 2.0f);
        }
        if (left <= 6 && left >= 0 && left % 2 == 0) {
            f.tellPop = left % 4 < 2;
        }
        if (left > 0) {
            return;
        }
        // the claw lands
        clearTell(f);
        pose.rear = 0.0;
        pose.thrust = 1.0;
        pose.reach = dist - 0.7;
        p.playSound(spot(f), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 0.6f);
        boolean inReach = dist <= JAB_REACH && Math.abs(angle(toPlayer - pose.yaw)) <= 70.0
                && Math.abs(p.getLocation().getY() - pose.y) < 2.6 && alive(p);
        int recover = 24;
        if (inReach) {
            f.downedTick = -1L;
            BossHits.hurt(p, f.body, 5.0);
            boolean downed = f.downedTick == Bukkit.getCurrentTick();
            if (!downed && Parries.parried(p)) {
                parried(f);
                return;
            }
            if (downed) {
                return;
            }
            recover = Parries.avoided(p) ? 22 : 30;
        }
        f.mode(Mode.JAB_RECOVER, recover);
    }

    private void jabRecover(Fight f) {
        ClangerRig.Pose pose = f.pose;
        pose.thrust = Math.max(0.0, 1.0 - f.t / 10.0);
        pose.eyes = 1.0;
        if (f.t >= f.length) {
            int phase = f.phase();
            f.rest = (phase == 0 ? 30 : phase == 1 ? 22 : 15) + f.mercy * 6;
            f.mode(Mode.IDLE, 0);
        }
    }

    /** The claw met a blade: he goes over backwards and lies there kicking. */
    private void parried(Fight f) {
        Player p = f.player;
        int ticks = 76 + f.mercy * 12;
        f.mode(Mode.REELING, ticks);
        p.playSound(spot(f), Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.0f, 0.8f);
        p.playSound(spot(f), Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 2.0f, 1.3f);
        p.playSound(spot(f), Sound.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 0.9f, 0.7f);
        f.pose.ring = 1.0;
        screenGlyph(p, finisherGlyph, 0, 6, 8);
        Txt.actionBar(p, Txt.t(p, "§6§lPARRIED §7· he is on his back: §fhit him now", "§6§lPARIERT §7· er liegt auf dem Rücken: §fjetzt draufhauen"));
    }

    private void reeling(Fight f) {
        ClangerRig.Pose pose = f.pose;
        int left = f.length - f.t;
        pose.thrust = Math.max(0.0, pose.thrust - 0.2);
        pose.tilt = left > 8 ? Math.min(32.0, pose.tilt + 9.0) : Math.max(0.0, pose.tilt - 4.5);
        pose.dizzy = true;
        pose.dizzyAngle += 0.45;
        pose.eyes = 1.3;
        if (f.t % 16 == 8) {
            f.player.playSound(spot(f), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.5f, 1.4f);
        }
        if (left <= 0) {
            pose.dizzy = false;
            f.rest = 12;
            f.mode(Mode.IDLE, 0);
        }
    }

    // ------------------------------------------------------------------ the bell

    private void startSlam(Fight f) {
        int phase = f.phase();
        int windup = (phase == 0 ? 38 : phase == 1 ? 32 : 27) + f.mercy * 5;
        f.mode(Mode.SLAM_UP, windup);
        f.slamX = f.pose.x;
        f.slamZ = f.pose.z;
        showTell(f, true);
        Player p = f.player;
        p.playSound(spot(f), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.4f, 0.7f);
        p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.HOSTILE, 1.8f, 0.5f);
        screenGlyph(p, perilGlyph, 0, 10, 4);
        if (!f.taughtRoll) {
            f.taughtRoll = true;
            Txt.actionBar(p, Txt.t(p, "§cRed !!§7: no parry. Hold §fSneak §7and §fJump§7: roll out of the ring",
                    "§cRotes !!§7: kein Parieren. §fSchleichen §7halten und §fspringen§7: Roll aus dem Ring"));
        }
    }

    private void slamUp(Fight f, Location centre) {
        ClangerRig.Pose pose = f.pose;
        Player p = f.player;
        int left = f.length - f.t;
        pose.walk *= 0.5;
        pose.tuck = Math.min(1.0, f.t / 8.0);
        double rise = Math.min(1.0, f.t / 15.0);
        pose.lift = 3.3 * (1.0 - (1.0 - rise) * (1.0 - rise));
        pose.spin += 10.0 + f.t * 0.9;
        pose.eyes = 0.1;
        if (left > 10) {
            // he hangs over whoever he is after, until the last half second
            Location at = p.getLocation();
            double dx = at.getX() - pose.x;
            double dz = at.getZ() - pose.z;
            double d = Math.hypot(dx, dz);
            double step = Math.min(d, 0.19);
            if (d > 0.05) {
                double nx = pose.x + dx / d * step;
                double nz = pose.z + dz / d * step;
                if (Math.hypot(nx - centre.getX(), nz - centre.getZ()) <= ROAM + 1.5) {
                    pose.x = nx;
                    pose.z = nz;
                }
            }
            f.slamX = pose.x;
            f.slamZ = pose.z;
        }
        boolean beat = left <= 8;
        if (left == 8) {
            whiten(f);
            p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.HOSTILE, 1.5f, 0.7f);
            p.playSound(spot(f), Sound.ITEM_TRIDENT_RETURN, SoundCategory.HOSTILE, 1.3f, 1.4f);
        }
        if (f.t % 2 == 0) {
            ring(p, f.slamX, pose.y + 0.12, f.slamZ, SLAM_RADIUS, 40, beat ? SLAM_WHITE : SLAM_RED);
            double fill = SLAM_RADIUS * Math.min(1.0, f.t / (double) f.length);
            ring(p, f.slamX, pose.y + 0.12, f.slamZ, fill, 18, SLAM_RED);
        }
        int every = left > 14 ? 6 : left > 7 ? 4 : 2;
        if (f.t % every == 0) {
            p.playSound(spot(f), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.HOSTILE, 1.0f, 0.5f + (1.0f - left / (float) f.length) * 0.5f);
        }
        if (left <= 6 && left >= 0 && left % 2 == 0) {
            f.tellPop = left % 4 < 2;
        }
        if (left <= 0) {
            clearTell(f);
            f.mode(Mode.SLAM_DOWN, 4);
        }
    }

    private void slamDown(Fight f) {
        ClangerRig.Pose pose = f.pose;
        Player p = f.player;
        pose.lift = Math.max(0.0, 3.3 * (1.0 - f.t / 4.0));
        if (f.t < f.length) {
            return;
        }
        pose.lift = 0.0;
        pose.spin = 0.0;
        pose.ring = 1.0;
        Location ground = new Location(isle.world(), f.slamX, pose.y, f.slamZ);
        p.playSound(ground, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        p.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.7f, 0.7f);
        p.playSound(ground, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.4f, 0.6f);
        for (int i = 0; i < 3; i++) {
            ring(p, f.slamX, pose.y + 0.15 + i * 0.1, f.slamZ, SLAM_RADIUS - i * 1.2, 36, SLAM_WHITE);
        }
        p.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 60, 1.6, 0.2, 1.6, 0.0, Material.SAND.createBlockData());
        Location at = p.getLocation();
        boolean under = Math.hypot(at.getX() - f.slamX, at.getZ() - f.slamZ) <= SLAM_RADIUS
                && at.getY() - pose.y < 2.4 && alive(p);
        boolean downed = false;
        if (under) {
            f.downedTick = -1L;
            BossHits.peril(p, f.body, 5.0);
            downed = f.downedTick == Bukkit.getCurrentTick();
        }
        if (downed) {
            return;
        }
        int phase = f.phase();
        boolean rolled = under && Parries.avoided(p);
        int ticks = (phase == 0 ? 66 : phase == 1 ? 54 : 46) + f.mercy * 10 + (rolled ? 8 : 0);
        f.mode(Mode.DIZZY, ticks);
        if (rolled || !under) {
            Txt.actionBar(p, Txt.t(p, "§6§lDIZZY §7· the bell is still ringing in his head: §fhit him now",
                    "§6§lBENOMMEN §7· ihm dröhnt die eigene Glocke: §fjetzt draufhauen"));
        }
    }

    private void dizzy(Fight f) {
        ClangerRig.Pose pose = f.pose;
        int left = f.length - f.t;
        pose.tuck = Math.max(0.0, pose.tuck - 0.2);
        pose.wobble = Math.sin(f.t * 0.55) * (left > 10 ? 7.0 : 2.0);
        pose.dizzy = true;
        pose.dizzyAngle += 0.5;
        pose.eyes = 1.25;
        if (f.t % 14 == 6) {
            f.player.playSound(spot(f), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.5f, 1.2f);
        }
        if (left <= 0) {
            pose.dizzy = false;
            pose.wobble = 0.0;
            f.rest = 14;
            f.mode(Mode.IDLE, 0);
        }
    }

    private void resting(Fight f) {
        ClangerRig.Pose pose = f.pose;
        pose.tuck = Math.min(1.0, pose.tuck + 0.15);
        pose.lift = Math.max(0.0, pose.lift - 0.4);
        pose.tilt = Math.max(0.0, pose.tilt - 5.0);
        pose.rear = 0.0;
        pose.thrust = 0.0;
        pose.spin = 0.0;
        pose.wobble = 0.0;
        pose.dizzy = false;
        if (f.t >= f.length) {
            f.rest = 24;
            f.mode(Mode.IDLE, 0);
            f.player.playSound(spot(f), Sound.BLOCK_BONE_BLOCK_HIT, SoundCategory.HOSTILE, 1.0f, 0.9f);
        }
    }

    // ------------------------------------------------------------------ hits on him

    @EventHandler(priority = EventPriority.LOW)
    public void onStruck(EntityDamageEvent event) {
        if (!ClangerRig.ours(event.getEntity())) {
            return;
        }
        Fight fight = owner(event.getEntity());
        Player by = event instanceof EntityDamageByEntityEvent e && e.getDamager() instanceof Player player ? player : null;
        if (fight == null || by == null || by != fight.player) {
            event.setCancelled(true);
            return;
        }
        if (fight.mode == Mode.ASLEEP) {
            // knocking on the bell is one way to say hello
            event.setCancelled(true);
            wake(fight);
        }
    }

    /** After Items has worked out the blow: the bell takes most of it unless he is on his back or dizzy. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuard(EntityDamageByEntityEvent event) {
        if (!ClangerRig.ours(event.getEntity()) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        Fight f = fights.get(player.getUniqueId());
        if (f == null || f.body == null || !event.getEntity().getUniqueId().equals(f.body.getUniqueId())) {
            return;
        }
        double factor = open(f) ? 1.0 : GUARDED;
        if (f.mercy >= 2) {
            factor *= 1.3;
        }
        if (f.age > 20 * 240) {
            // four minutes in: nobody should be stuck here
            factor *= 2.0;
        }
        event.setDamage(event.getDamage() * factor);
    }

    private static boolean open(Fight f) {
        return f.mode == Mode.DIZZY || f.mode == Mode.REELING;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!ClangerRig.ours(event.getEntity()) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        Fight f = fights.get(player.getUniqueId());
        if (f == null || f.body == null || !event.getEntity().getUniqueId().equals(f.body.getUniqueId())) {
            return;
        }
        double dealt = event.getFinalDamage();
        if (dealt <= 0.0) {
            return;
        }
        f.hp = Math.max(0.0, f.hp - dealt);
        f.pose.ring = 1.0;
        Location at = spot(f).add(0, 1.6, 0);
        if (open(f)) {
            float pitch = 0.95f + ThreadLocalRandom.current().nextFloat() * 0.45f;
            player.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.4f, pitch);
            player.spawnParticle(Particle.DUST, at, 12, 0.9, 0.7, 0.9, 0.0, SPARK);
            player.spawnParticle(Particle.CRIT, at, 8, 0.8, 0.6, 0.8, 0.1);
        } else {
            // a dull clonk: the bell took it
            player.playSound(at, Sound.BLOCK_ANVIL_PLACE, SoundCategory.HOSTILE, 0.5f, 0.6f);
            player.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 0.5f, 0.5f);
            if (f.blunted++ % 4 == 0) {
                Txt.actionBar(player, Txt.t(player, "§7The bell takes the blow. §eParry §7his claw or §croll §7out of the slam: §fthen §7he is open",
                        "§7Die Glocke fängt den Schlag ab. §ePariere §7die Schere oder §croll §7aus dem Hieb: §fdann §7ist er offen"));
            }
        }
        if (f.hp <= 0.0 && f.mode != Mode.YIELD) {
            giveUp(f);
        } else if (f.bar != null && f.hp / MAX_HP <= 0.28) {
            f.bar.color(BossBar.Color.RED);
        }
    }

    private Fight owner(Entity entity) {
        for (Fight fight : fights.values()) {
            if (fight.body != null && fight.body.getUniqueId().equals(entity.getUniqueId())) {
                return fight;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ he gives up

    private void giveUp(Fight f) {
        Player p = f.player;
        clearTell(f);
        f.mode(Mode.YIELD, 190);
        f.session.bossHp = -1.0;
        if (f.bar != null) {
            p.hideBossBar(f.bar);
            f.bar = null;
        }
        p.playSound(spot(f), Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        p.playSound(spot(f), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.5f, 1.1f);
        p.sendMessage(Txt.t(p, "§6Old Clanger §7throws up his claws. The bell was never his, and he knows it.",
                "§6Old Clanger §7reißt die Scheren hoch. Die Glocke war nie seine, und er weiß es."));
    }

    private void walkHome(Fight f, Location centre) {
        ClangerRig.Pose pose = f.pose;
        Player p = f.player;
        int t = f.t;
        pose.tuck = Math.max(0.0, pose.tuck - 0.2);
        pose.lift = Math.max(0.0, pose.lift - 0.5);
        pose.spin = 0.0;
        pose.rear = 0.0;
        pose.thrust = 0.0;
        pose.dizzy = false;
        if (t < 60) {
            // claws up, eyes wide, a little bow
            pose.tilt = Math.max(0.0, pose.tilt - 4.0);
            pose.surrender = Math.min(1.0, pose.surrender + 0.12);
            pose.eyes = 1.6;
            pose.wobble = Math.sin(t * 0.8) * 2.5;
            pose.yaw = turn(pose.yaw, yawTo(spot(f), p.getLocation()), 10f);
            pose.walk *= 0.7;
        } else {
            // off he goes, bell and all, towards the harbour
            pose.surrender = Math.max(0.0, pose.surrender - 0.1);
            pose.eyes = 1.0;
            pose.wobble = 0.0;
            Location gate = isle.marks().at(isle.world(), "arena_gate_west");
            Location to = gate != null ? gate : centre.clone().add(-14, 0, -6);
            pose.yaw = turn(pose.yaw, yawTo(spot(f), to), 9f);
            pose.x -= Math.sin(Math.toRadians(pose.yaw)) * 0.16;
            pose.z += Math.cos(Math.toRadians(pose.yaw)) * 0.16;
            pose.walk = Math.min(1.0, pose.walk + 0.2);
            pose.stride += 0.55;
            if (t % 5 == 0) {
                p.playSound(spot(f), Sound.ENTITY_SPIDER_STEP, SoundCategory.HOSTILE, 0.6f, 0.7f);
            }
            if (t % 40 == 0) {
                p.playSound(spot(f), Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.2f, 1.0f);
            }
            if (t > f.length - 24) {
                pose.scale = Math.max(0.0, (f.length - t) / 24.0);
            }
        }
        if (t == 62) {
            p.sendMessage(Txt.t(p, "§7He hoists it onto his back one last time and carries it home, to the frame at the harbour.",
                    "§7Er wuchtet sie ein letztes Mal auf den Rücken und trägt sie heim, zum Gestell am Hafen."));
        }
        if (t < f.length) {
            return;
        }
        Location last = spot(f).add(0, 1.0, 0);
        p.spawnParticle(Particle.CLOUD, last, 24, 0.8, 0.6, 0.8, 0.02);
        f.mode = Mode.GONE;
        fights.remove(p.getUniqueId());
        end(f);
        reward(p);
        isle.director().bossDone(p, f.session);
    }

    private void reward(Player p) {
        int coins = Math.max(0, plugin.getConfig().getInt("isle.boss-coins", 100));
        CoinAccess purse = AetherServices.coins();
        List<String> lines = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        lines.add(Txt.t(p, "§7The bell hangs in its frame at the harbour again.", "§7Die Glocke hängt wieder in ihrem Gestell am Hafen."));
        icons.add(new ItemStack(Material.BELL));
        lines.add(Txt.t(p, "§eParry§7, §croll§7, §ffull swings§7: every fight here is made of these.",
                "§eParieren§7, §crollen§7, §fvolle Schläge§7: Daraus besteht hier jeder Kampf."));
        icons.add(new ItemStack(Material.IRON_SWORD));
        if (purse != null && coins > 0) {
            purse.add(p, coins);
            lines.add("§6+" + coins + " Coins");
            icons.add(new ItemStack(Material.GOLD_NUGGET));
        }
        Guide.card(p, Txt.t(p, "YOUR FIRST BOSS", "DEIN ERSTER BOSS"), Txt.t(p, "Old Clanger gives up", "Old Clanger gibt auf"),
                lines, icons, Txt.t(p, "Next: Captain Wim at the pier", "Als Nächstes: Kapitän Wim am Steg"), "§6");
    }

    // ------------------------------------------------------------------ the mark over the attack

    private void showTell(Fight f, boolean roll) {
        clearTell(f);
        TellSpot spot = tellSpot(f, roll);
        f.tellRoll = roll;
        f.tellPop = false;
        f.tellSize = spot.scale();
        f.tell = isle.world().spawn(spot.at(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(ClangerRig.TAG);
            d.text(Component.text(roll ? "!!" : "!", roll ? RED : YELLOW).decorate(TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            // not "see through": that text is drawn before his block displays and they would paint over it
            d.setSeeThrough(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTeleportDuration(2);
            d.setTransformation(scale(spot.scale()));
        });
        f.player.showEntity(plugin, f.tell);
    }

    private void whiten(Fight f) {
        if (f.tell != null && f.tell.isValid()) {
            f.tell.text(f.tell.text().color(NamedTextColor.WHITE));
        }
    }

    private void clearTell(Fight f) {
        if (f.tell != null) {
            if (f.tell.isValid()) {
                f.tell.remove();
            }
            f.tell = null;
        }
    }

    /** Where the mark hangs and how big it is drawn. */
    private record TellSpot(Location at, float scale) {
    }

    /**
     * The mark hangs between him and the player, in front of his eyes and claws, at the height the player is
     * looking at. He is four blocks tall and a new player stands close and looks wherever: a mark over his head
     * (where the big bosses of the main world carry theirs) would be off the screen exactly when it matters. The
     * closer it hangs, the smaller it is drawn, so on the screen it is always about the same size.
     */
    private TellSpot tellSpot(Fight f, boolean roll) {
        ClangerRig.Pose pose = f.pose;
        Location eye = f.player.getEyeLocation();
        double dx = pose.x - eye.getX();
        double dz = pose.z - eye.getZ();
        double d = Math.hypot(dx, dz);
        // the way from the player to him; right under the flying bell: the way the player looks
        double tx;
        double tz;
        if (d >= 0.6) {
            tx = dx / d;
            tz = dz / d;
        } else {
            double yaw = Math.toRadians(eye.getYaw());
            tx = -Math.sin(yaw);
            tz = Math.cos(yaw);
        }
        // his eyes stand 1.9 blocks out, the bell's wall 1.4: the mark hangs in front of both. It keeps a distance
        // from the player's face too (the jab's mark may come close: whoever hugs the bell still has to see it)
        double out = Math.max(1.45, Math.min(2.1, d - 0.9));
        double back = Math.max(roll ? 0.9 : 0.3, d - out);
        double size = back * (roll ? 1.45 : 1.3);
        size = Math.max(roll ? 1.2 : 0.4, Math.min(roll ? 2.8 : 2.5, size));
        // a little above the crosshair; never under his chin, never over his eyes
        double pitch = Math.toRadians(Math.max(-50.0, Math.min(50.0, eye.getPitch())));
        double aim = eye.getY() - Math.tan(pitch) * back + 0.10 * back;
        double middle = Math.max(pose.y + 0.8, Math.min(pose.y + 2.6, aim));
        // the text grows upwards from the display's own spot: its middle sits 0.15 blocks (times the size) above
        double y = middle - 0.15 * size;
        return new TellSpot(new Location(isle.world(), eye.getX() + tx * back, y, eye.getZ() + tz * back), (float) size);
    }

    private static Transformation scale(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    private void screenGlyph(Player player, char glyph, int in, int stay, int out) {
        if (glyph == 0) {
            return;
        }
        player.showTitle(Title.title(Component.text(String.valueOf(glyph)).font(COMBAT_FONT), Component.empty(),
                Title.Times.times(Ticks.duration(in), Ticks.duration(stay), Ticks.duration(out))));
    }

    // ------------------------------------------------------------------ small things

    private Location spot(Fight f) {
        return new Location(isle.world(), f.pose.x, f.pose.y, f.pose.z);
    }

    private static boolean alive(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private static void ring(Player p, double x, double y, double z, double radius, int points, Particle.DustOptions dust) {
        if (radius < 0.2) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            p.spawnParticle(Particle.DUST, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private static float yawTo(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    /** Signed difference of two yaws, -180..180. */
    private static double angle(double degrees) {
        double a = degrees % 360.0;
        if (a > 180.0) {
            a -= 360.0;
        }
        if (a < -180.0) {
            a += 360.0;
        }
        return a;
    }

    private static float turn(float from, float to, float max) {
        double delta = angle(to - from);
        if (Math.abs(delta) <= max) {
            return to;
        }
        return from + (float) Math.copySign(max, delta);
    }
}
