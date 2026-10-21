package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.fx.ShapeFx;
import de.aetherion.bossengine.skill.t2.T2Mechanics;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.Vfx;

import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Borderlands rite bosses as encounters, not stat blocks: an intro card when the fight opens and one
 * signature move each, drawn in shapes. Every move marks the ground in the boss colour and flashes white just
 * before it lands, the same "white means roll" the golem teaches. Phase two casts it faster and wider.
 *
 * <ul>
 *   <li>Skuldugery, Ash Rain: fire arrows drop on marked circles.</li>
 *   <li>McNugget, Golden Egg: a lobbed egg bursts where it lands, three at once when he panics.</li>
 *   <li>Bridge Troll, Toll Sweep: a wide axe swing in front of him; in the storm a full spin.</li>
 *   <li>Hollow Lurker, Soul Grasp: soul tethers mark players, then the dark reaches up under them.</li>
 * </ul>
 */
final class RiteDirector {

    private static final Color WHITE = Color.fromRGB(0xFFFFFF);
    private static final Color TOLL = Color.fromRGB(0xE8B04A);
    private static final int FLASH = 8;
    private static final double REACH = 28.0;

    private enum Kind {
        SKULL("&6&lSkuldugery", "The Ash Archer", "Der Aschebogen", ShapeFx.FIRE, 300, 220),
        NUGGET("&e&lMcNugget", "Terror of the Coop", "Schrecken des Hühnerstalls", ShapeFx.GOLD, 280, 200),
        TROLL("&6&lBridge Troll", "Keeper of the Toll", "Hüter des Zolls", TOLL, 260, 200),
        LURKER("&8&lHollow Lurker", "What Waits Below", "Was unten wartet", ShapeFx.SOUL, 260, 190),
        WARDEN("&3&lPathwarden", "Warden of the Path", "Wächter des Pfads", ShapeFx.SOUL, 0, 0),
        NONE("", "", "", WHITE, 0, 0);

        final String name;
        final String epithetEn;
        final String epithetDe;
        final Color color;
        final int cooldown;
        final int cooldownLate;

        Kind(String name, String epithetEn, String epithetDe, Color color, int cooldown, int cooldownLate) {
            this.name = name;
            this.epithetEn = epithetEn;
            this.epithetDe = epithetDe;
            this.color = color;
            this.cooldown = cooldown;
            this.cooldownLate = cooldownLate;
        }

        static Kind of(String id) {
            return switch (id) {
                case "skuldugery" -> SKULL;
                case "mcnugget" -> NUGGET;
                case "bridge_troll" -> TROLL;
                case "hollow_lurker" -> LURKER;
                case "pathwarden" -> WARDEN;
                default -> NONE;
            };
        }
    }

    private final BossInstance instance;
    private final List<Location> marks = new ArrayList<>();
    private final List<Player> marked = new ArrayList<>();
    private Kind kind;
    private boolean introduced;
    private int cooldown = 140;
    private int windup = -1;
    private int length;
    private int followUp = -1;
    private float sweepYaw;

    RiteDirector(BossInstance instance) {
        this.instance = instance;
    }

    void tick() {
        if (kind == null) {
            kind = Kind.of(de.aetherion.bossengine.fx.CombatTheatrics.id(instance));
        }
        if (kind == Kind.NONE) {
            return;
        }
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid() || !instance.isAlive()) {
            reset();
            return;
        }
        if (!introduced) {
            introduced = intro(boss);
        }
        if (kind.cooldown == 0) {
            return;
        }
        if (instance.isTransitioning()) {
            reset();
            return;
        }
        if (followUp > 0 && --followUp == 0) {
            spin(boss);
        }
        if (windup >= 0) {
            charge(boss);
            if (windup-- == 0) {
                strike(boss);
                marks.clear();
                marked.clear();
                windup = -1;
            }
            return;
        }
        if (--cooldown <= 0) {
            cooldown = late() ? kind.cooldownLate : kind.cooldown;
            begin(boss);
        }
    }

    private void reset() {
        marks.clear();
        marked.clear();
        windup = -1;
        followUp = -1;
    }

    private boolean late() {
        de.aetherion.bossengine.model.BossPhase phase = instance.getCurrentPhase();
        return phase != null && phase.getHealthPercent() < 100;
    }

    // intro ---------------------------------------------------------------------------------------------

    private boolean intro(LivingEntity boss) {
        List<Player> near = T2Mechanics.nearby(boss, 40);
        if (near.isEmpty()) {
            return false;
        }
        Location at = boss.getLocation();
        Vfx.rune(at.clone().add(0, 0.1, 0), kind.color, 3.2, 50);
        Vfx.wave(at.clone().add(0, 0.15, 0), kind.color, 5.0, 16);
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2400), Duration.ofMillis(900));
        for (Player p : near) {
            String epithet = AetherServices.german(p) ? kind.epithetDe : kind.epithetEn;
            p.showTitle(Title.title(TextUtil.component(kind.name), TextUtil.component("&7" + epithet), times));
            p.playSound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.35f, 1.6f);
            p.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.8f, 0.6f);
        }
        return true;
    }

    // signature moves -----------------------------------------------------------------------------------

    private void begin(LivingEntity boss) {
        List<Player> near = T2Mechanics.nearby(boss, REACH);
        if (near.isEmpty() || instance.isSlamPending()) {
            cooldown = 40;
            return;
        }
        boolean late = late();
        Location at = boss.getLocation();
        switch (kind) {
            case SKULL -> {
                length = late ? 20 : 24;
                for (Player p : near) {
                    if (marks.size() < (late ? 5 : 3)) {
                        marks.add(ground(p.getLocation()));
                    }
                }
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                while (marks.size() < (late ? 5 : 3)) {
                    Location base = near.get(rng.nextInt(near.size())).getLocation();
                    marks.add(ground(base.add(rng.nextDouble(-4.5, 4.5), 0, rng.nextDouble(-4.5, 4.5))));
                }
                Location bow = boss.getEyeLocation();
                Vfx.streak(bow, bow.clone().add(0, 18, 0), ShapeFx.FIRE, 0.3, 8);
                boss.getWorld().playSound(at, Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.HOSTILE, 1.2f, 0.6f);
                boss.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.8f, 0.7f);
                cue(near, "&6Ash Rain &7· leave the circles", "&6Ascheregen &7· raus aus den Kreisen");
            }
            case NUGGET -> {
                length = 26;
                List<Player> far = new ArrayList<>(near);
                far.sort(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(at)).reversed());
                for (int i = 0; i < Math.min(late ? 3 : 1, far.size()); i++) {
                    marks.add(ground(far.get(i).getLocation()));
                }
                Location beak = at.clone().add(0, boss.getHeight() * 0.8, 0);
                for (Location land : marks) {
                    Location apex = beak.clone().add(land).multiply(0.5).add(0, 7, 0);
                    Vfx.orb(beak, apex, ShapeFx.GOLD, 0.9, length / 2);
                    Location from = apex.clone();
                    instance.getPlugin().getServer().getScheduler().runTaskLater(instance.getPlugin(),
                            () -> Vfx.orb(from, land.clone().add(0, 0.4, 0), ShapeFx.GOLD, 0.9, length / 2 + 1), length / 2);
                }
                boss.getWorld().playSound(at, Sound.ENTITY_CHICKEN_EGG, SoundCategory.HOSTILE, 1.4f, 0.5f);
                boss.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 0.8f, 1.2f);
                cue(near, "&eGolden Egg &7· leave the ring", "&eGoldenes Ei &7· raus aus dem Ring");
            }
            case TROLL -> {
                length = late ? 18 : 22;
                Player target = near.stream()
                        .min(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(at))).orElse(near.get(0));
                Vector to = target.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (to.lengthSquared() < 0.01) {
                    to = at.getDirection().setY(0);
                }
                sweepYaw = (float) Math.toDegrees(Math.atan2(to.getZ(), to.getX()));
                boss.getWorld().playSound(at, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, SoundCategory.HOSTILE, 1.3f, 0.5f);
                boss.getWorld().playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 1.0f, 0.5f);
                cue(near, "&6Toll Sweep &7· don't stand in front of him", "&6Zollhieb &7· nicht vor ihm stehen");
            }
            case LURKER -> {
                length = 22;
                List<Player> pool = new ArrayList<>(near);
                java.util.Collections.shuffle(pool);
                for (int i = 0; i < Math.min(late ? 4 : 2, pool.size()); i++) {
                    marked.add(pool.get(i));
                    marks.add(ground(pool.get(i).getLocation()));
                }
                boss.getWorld().playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 0.8f, 0.5f);
                boss.getWorld().playSound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.HOSTILE, 1.0f, 0.8f);
                cue(marked, "&3Soul Grasp &7· step out of the mark", "&3Seelengriff &7· raus aus der Markierung");
            }
            default -> {
                return;
            }
        }
        windup = length;
        telegraph(boss, length, kind.color);
    }

    /** The marks in the boss colour; called again for the white flash right before the hit. */
    private void telegraph(LivingEntity boss, int ticks, Color color) {
        switch (kind) {
            case SKULL -> marks.forEach(m -> Vfx.telegraph(m, 2.2, ticks, color));
            case NUGGET -> marks.forEach(m -> Vfx.telegraph(m, 3.0, ticks, color));
            case LURKER -> marks.forEach(m -> Vfx.telegraph(m, 1.8, ticks, color));
            case TROLL -> {
                Location c = ground(boss.getLocation());
                if (late()) {
                    Vfx.telegraph(c, 5.0, ticks, color);
                } else {
                    Vfx.telegraph(c.add(front().multiply(3.0)), 3.2, ticks, color);
                }
            }
            default -> {
            }
        }
    }

    private void charge(LivingEntity boss) {
        if (windup == FLASH) {
            telegraph(boss, FLASH, WHITE);
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.HOSTILE, 0.9f, 1.9f);
        }
        switch (kind) {
            case LURKER -> {
                Location from = boss.getLocation().add(0, boss.getHeight() * 0.6, 0);
                for (int i = 0; i < marked.size(); i++) {
                    Player p = marked.get(i);
                    if (p.isValid() && p.getWorld() == boss.getWorld()) {
                        Vfx.line(instance.getInstanceId() + ":grasp:" + i, from, p.getLocation().add(0, 1.0, 0),
                                windup <= FLASH ? WHITE : ShapeFx.SOUL, 0.08);
                    }
                }
            }
            case TROLL -> {
                if (windup % 6 == 0) {
                    Vfx.motes(boss.getLocation().add(0, boss.getHeight() * 0.7, 0), TOLL, 3, 0.6, new Vector(0, 0.04, 0), 14);
                }
            }
            case SKULL -> {
                if (windup % 5 == 0) {
                    Vfx.flame(boss.getEyeLocation().add(0, 0.6, 0), ShapeFx.FIRE, 0.5, 8);
                }
            }
            default -> {
            }
        }
    }

    private void strike(LivingEntity boss) {
        boolean late = late();
        World world = boss.getWorld();
        switch (kind) {
            case SKULL -> {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                for (Location m : marks) {
                    Location sky = m.clone().add(rng.nextDouble(-2, 2), 16, rng.nextDouble(-2, 2));
                    Vfx.streak(sky, m.clone().add(0, 0.2, 0), ShapeFx.FIRE, 0.35, 6);
                    Vfx.flame(m.clone().add(0, 0.3, 0), ShapeFx.FIRE, 1.4, 14);
                    Vfx.wave(m.clone().add(0, 0.1, 0), ShapeFx.FIRE, 2.4, 10);
                    world.playSound(m, Sound.ENTITY_ARROW_HIT, SoundCategory.HOSTILE, 1.0f, 0.6f);
                    world.playSound(m, Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 0.7f, 0.8f);
                    hitCircle(boss, m, 2.2, late ? 24 : 20, 0.0);
                }
            }
            case NUGGET -> {
                for (Location m : marks) {
                    Vfx.burst(m.clone().add(0, 0.5, 0), ShapeFx.GOLD, 2.6, 10);
                    Vfx.wave(m.clone().add(0, 0.1, 0), ShapeFx.PALE, 3.4, 12);
                    Vfx.motes(m.clone().add(0, 0.6, 0), ShapeFx.PALE, 10, 1.6, new Vector(0, 0.05, 0), 30);
                    world.playSound(m, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.6f, 1.6f);
                    world.playSound(m, Sound.ENTITY_CHICKEN_HURT, SoundCategory.HOSTILE, 1.0f, 0.6f);
                    hitCircle(boss, m, 3.0, late ? 22 : 18, 0.75);
                }
            }
            case TROLL -> {
                if (late) {
                    spin(boss);
                } else {
                    sweep(boss);
                }
            }
            case LURKER -> {
                for (Location m : marks) {
                    Vfx.pillar(m, ShapeFx.SOUL, 1.1, 3.6, 14);
                    Vfx.spiral(m, ShapeFx.SOUL, 1.4, 3.2, 16, 3);
                    Vfx.chips(m, m.getBlock().getRelative(BlockFace.DOWN).getBlockData(), 6, 0.45);
                    world.playSound(m, Sound.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.4f, 0.6f);
                    world.playSound(m, Sound.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 0.7f, 1.2f);
                    for (Player p : hitCircle(boss, m, 1.8, late ? 24 : 20, 0.0)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, true));
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Toll Sweep: one wide swing at the marked front. */
    private void sweep(LivingEntity boss) {
        Location at = boss.getLocation();
        Vector front = front();
        Location eye = at.clone().add(0, boss.getHeight() * 0.6, 0);
        eye.setDirection(front);
        Vfx.slash(eye.clone().add(front.clone().multiply(1.5)), TOLL, 5.5, 0f);
        Location hit = ground(at).add(front.clone().multiply(3.0));
        Vfx.wave(hit, TOLL, 3.4, 10);
        Vfx.chips(hit, hit.getBlock().getRelative(BlockFace.DOWN).getBlockData(), 8, 0.5);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.5f);
        at.getWorld().playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 1.0f, 0.8f);
        for (Player p : T2Mechanics.nearby(boss, 7.5)) {
            if (p.getLocation().distanceSquared(hit) <= 3.4 * 3.4) {
                toll(boss, p, 22);
            }
        }
    }

    /** Toll Spin (storm phase): the full circle, then a second swing half a beat later. */
    private void spin(LivingEntity boss) {
        Location at = boss.getLocation();
        Location eye = at.clone().add(0, boss.getHeight() * 0.55, 0);
        for (int i = 0; i < 4; i++) {
            Location side = eye.clone();
            side.setYaw(sweepYaw + i * 90f);
            side.setPitch(0f);
            Vfx.slash(side.add(side.getDirection().multiply(1.6)), TOLL, 4.5, i * 30f);
        }
        Vfx.wave(ground(at), TOLL, 5.5, 12);
        Vfx.chips(at, at.getBlock().getRelative(BlockFace.DOWN).getBlockData(), 10, 0.55);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
        at.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.2f, 0.5f);
        for (Player p : T2Mechanics.nearby(boss, 5.0)) {
            toll(boss, p, 26);
        }
        if (followUp < 0 && windup < 0) {
            followUp = 12;
            Vfx.telegraph(ground(at), 5.0, 12, WHITE);
        } else {
            followUp = -1;
        }
    }

    private void toll(LivingEntity boss, Player p, double power) {
        Vector push = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 0.01) {
            p.setVelocity(push.normalize().multiply(1.2).setY(0.45));
        }
        BossHits.hurt(p, boss, instance.scaleDamage(power));
    }

    private List<Player> hitCircle(LivingEntity boss, Location centre, double radius, double power, double lift) {
        List<Player> hit = new ArrayList<>();
        for (Player p : T2Mechanics.nearby(boss, REACH + 6)) {
            Location l = p.getLocation();
            double dx = l.getX() - centre.getX();
            double dz = l.getZ() - centre.getZ();
            if (dx * dx + dz * dz > radius * radius || Math.abs(l.getY() - centre.getY()) > 3.0) {
                continue;
            }
            if (lift > 0) {
                p.setVelocity(p.getVelocity().setY(lift));
            }
            BossHits.hurt(p, boss, instance.scaleDamage(power));
            hit.add(p);
        }
        return hit;
    }

    // helpers -------------------------------------------------------------------------------------------

    private Vector front() {
        double rad = Math.toRadians(sweepYaw);
        return new Vector(Math.cos(rad), 0, Math.sin(rad));
    }

    /** A point on the floor under {@code at} (up to four blocks down), lifted a hair for the ring to show. */
    private static Location ground(Location at) {
        Location l = at.clone();
        for (int i = 0; i < 4 && l.getBlock().isPassable() && l.getBlockY() > l.getWorld().getMinHeight(); i++) {
            l.add(0, -1, 0);
        }
        if (!l.getBlock().isPassable()) {
            l.setY(l.getBlockY() + 1.0);
        }
        return l.add(0, 0.05, 0);
    }

    private static void cue(List<Player> players, String en, String de) {
        for (Player p : players) {
            p.sendActionBar(TextUtil.component(AetherServices.german(p) ? de : en));
        }
    }
}
