package de.aetherion.bossengine.combat;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.rite.RiteKit;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * The combat kit for every boss without a director of its own (Hollow Sun, the Sovereign and Nihil keep theirs):
 * a body hit is no longer instant. The boss winds up under a yellow "!" ({@link BossTells}), the hit lands half a
 * second later on whoever is still in reach, and F on the white beat parries it ({@link BossHits#hurt},
 * {@link Parries}). Three parries break the guard: the boss reels for 5.5 s, takes x1.3 and is open for charged
 * hits ({@link Openings}). A guard left alone mends one parry every 25 s. Area moves that cannot be parried get a
 * red "!!" through {@link #rollTell} and land as {@link BossHits#peril}.
 */
public final class BossGuard {

    private static final int POSTURE = 3;
    private static final int BROKEN_TICKS = 110;
    private static final double BROKEN_TAKEN = 1.3;
    private static final int WINDUP = 10;
    private static final int WINDUP_LATE = 8;
    private static final int MEND_TICKS = 500;

    private final BossInstance instance;
    private BossTells tells;
    private BossTells.Tell pending;
    private Player target;
    private double power;
    private long landAt = -1;
    private int posture;
    private long postureAt;
    private long brokenUntil = -1;
    private boolean frozeAi;

    public BossGuard(BossInstance instance) {
        this.instance = instance;
    }

    /** Bosses whose own director already speaks this language. */
    public boolean active() {
        return !instance.ownsCombatKit();
    }

    /**
     * A body hit reached {@code player}: wind it up instead of landing it now. A boss winds up one hit at a time
     * and none while its guard is broken, so the vanilla attack is just dropped then.
     */
    public void melee(Player player, double hitPower) {
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid() || player == null || landAt >= 0 || broken()) {
            return;
        }
        int windup = late() ? WINDUP_LATE : WINDUP;
        target = player;
        power = hitPower;
        landAt = Bukkit.getCurrentTick() + windup;
        pending = tells().parry(overHead(boss), windup, size(boss));
        boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.9f, 0.7f);
    }

    /** A red "!!" over the boss for an area move that lands in {@code ticks} (null when this boss has no guard). */
    public BossTells.Tell rollTell(int ticks) {
        return tell(BossTells.Kind.ROLL, ticks);
    }

    /** A yellow "!" for a signature swing that can be parried; count the parry with {@link #parriedBy}. */
    public BossTells.Tell parryTell(int ticks) {
        return tell(BossTells.Kind.PARRY, ticks);
    }

    private BossTells.Tell tell(BossTells.Kind kind, int ticks) {
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid() || !active()) {
            return null;
        }
        int t = Math.max(1, ticks);
        return kind == BossTells.Kind.PARRY ? tells().parry(overHead(boss), t, size(boss))
                : tells().roll(overHead(boss), t, size(boss));
    }

    /** A signature strike of this boss was just parried by {@code player} (call right after {@link BossHits#hurt}). */
    public void parriedBy(Player player) {
        LivingEntity boss = instance.getEntity();
        if (boss != null && boss.isValid() && active() && !broken() && Parries.parried(player)) {
            parried(boss, player);
        }
    }

    public boolean broken() {
        return brokenUntil >= 0;
    }

    public double scaleIncoming(double amount) {
        return broken() ? amount * BROKEN_TAKEN : amount;
    }

    public void tick() {
        if (tells == null && landAt < 0 && !broken() && posture == 0) {
            return;
        }
        LivingEntity boss = instance.getEntity();
        if (boss == null || !boss.isValid() || !instance.isAlive()) {
            clear();
            return;
        }
        long now = Bukkit.getCurrentTick();
        if (pending != null) {
            pending.move(overHead(boss));
        }
        if (tells != null) {
            tells.tick();
        }
        if (posture > 0 && now - postureAt > MEND_TICKS) {
            posture--;
            postureAt = now;
        }
        if (broken() && (now >= brokenUntil || instance.isTransitioning())) {
            endBroken(boss);
        }
        if (!broken() && frozeAi && !instance.isTransitioning()) {
            // Handed back only outside a phase transition, which runs the body itself.
            if (boss instanceof Mob mob) {
                mob.setAI(true);
            }
            frozeAi = false;
        }
        if (landAt >= 0 && now >= landAt) {
            land(boss);
        }
    }

    public void clear() {
        if (tells != null) {
            tells.clear();
        }
        LivingEntity boss = instance.getEntity();
        if (frozeAi && boss instanceof Mob mob && boss.isValid()) {
            mob.setAI(true);
        }
        frozeAi = false;
        brokenUntil = -1;
        pending = null;
        target = null;
        landAt = -1;
        posture = 0;
    }

    private void land(LivingEntity boss) {
        Player hit = target;
        landAt = -1;
        pending = null;
        target = null;
        if (hit == null || !hit.isValid() || hit.isDead() || broken() || instance.isTransitioning()
                || hit.getWorld() != boss.getWorld()) {
            return;
        }
        Location feet = boss.getLocation();
        Location at = hit.getLocation();
        double dx = at.getX() - feet.getX();
        double dz = at.getZ() - feet.getZ();
        double reach = Math.max(2.9, boss.getWidth() * 0.5 + 2.4);
        boss.swingMainHand();
        boss.getWorld().playSound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        if (dx * dx + dz * dz > reach * reach || Math.abs(at.getY() - feet.getY()) > 2.6) {
            // Stepped out of it: a whiff.
            return;
        }
        BossHits.hurt(hit, boss, power);
        if (Parries.parried(hit)) {
            parried(boss, hit);
        }
    }

    private void parried(LivingEntity boss, Player by) {
        posture++;
        postureAt = Bukkit.getCurrentTick();
        World world = boss.getWorld();
        Location chest = boss.getLocation().add(0, boss.getHeight() * 0.6, 0);
        world.playSound(chest, Sound.BLOCK_ANVIL_LAND, 1.1f, 0.8f);
        world.playSound(chest, Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.7f);
        Vector away = boss.getLocation().toVector().subtract(by.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 0.01 && !(boss instanceof Mob mob && !mob.hasAI())) {
            boss.setVelocity(away.normalize().multiply(0.35).setY(0.1));
        }
        if (posture >= POSTURE) {
            breakGuard(boss);
            return;
        }
        bar(by, "&eParried &7" + posture + "/" + POSTURE + " · three in a row break the guard",
                "&ePariert &7" + posture + "/" + POSTURE + " · drei hintereinander brechen die Deckung");
    }

    private void breakGuard(LivingEntity boss) {
        posture = 0;
        brokenUntil = Bukkit.getCurrentTick() + BROKEN_TICKS;
        RiteKit kit = RiteKit.of(instance);
        if (kit != null) {
            kit.stagger(BROKEN_TICKS);
        }
        if (boss instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            frozeAi = true;
        }
        boss.setVelocity(new Vector());
        Openings.open(boss, BROKEN_TICKS);
        Location chest = boss.getLocation().add(0, boss.getHeight() * 0.6, 0);
        tells().finisher(chest);
        World world = boss.getWorld();
        world.playSound(chest, Sound.ITEM_SHIELD_BREAK, 1.5f, 0.5f);
        world.playSound(chest, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= 30 * 30) {
                bar(p, "&6&lGUARD BROKEN &7· it reels: &fhit it now",
                        "&6&lDECKUNG GEBROCHEN &7· er taumelt: &fjetzt draufhauen");
            }
        }
    }

    private void endBroken(LivingEntity boss) {
        brokenUntil = -1;
        boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.7f);
    }

    private BossTells tells() {
        if (tells == null) {
            tells = new BossTells(instance.getKeys(), instance.getInstanceId(), 32.0);
        }
        return tells;
    }

    private boolean late() {
        var phase = instance.getCurrentPhase();
        return phase != null && phase.getHealthPercent() < 100;
    }

    private static Location overHead(LivingEntity boss) {
        return boss.getLocation().add(0, boss.getHeight() + 0.6, 0);
    }

    private static float size(LivingEntity boss) {
        return (float) Math.max(1.4, Math.min(2.6, boss.getHeight() * 0.7));
    }

    private static void bar(Player player, String en, String de) {
        Locale locale = player.locale();
        boolean german = locale != null && "de".equalsIgnoreCase(locale.getLanguage());
        player.sendActionBar(TextUtil.component(german ? de : en));
    }
}
