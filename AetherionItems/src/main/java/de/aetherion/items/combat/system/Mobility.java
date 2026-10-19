package de.aetherion.items.combat.system;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Dash (sprinting, tap Sneak) and roll (hold Sneak, press Jump).
 * <p>
 * Both cost stamina. Dash is a ground burst on a short cooldown with a strike window after it; roll gives
 * i-frames and a recovery, and chaining rolls inside {@code roll.chain-window-ticks} is a tired roll
 * (fewer i-frames, higher cost). Both shorten themselves before a ledge, a wall or lava instead of throwing
 * the player off a cliff. Airborne sneaks stay with the boots that use them (Nullstep, Hollow Sun).
 * <p>
 * Ledge checks scan the distance the impulse really carries on a vanilla client (air drag 0.91, ground
 * friction 0.546), not the launch speed: about 5.0 blocks per unit of dash speed, and up to 6.5 per unit
 * of roll speed, because the roll impulse lands while the client is already mid-jump.
 */
final class Mobility {

    private static final double DASH_TRAVEL = 5.0;
    private static final double ROLL_TRAVEL = 6.5;
    /** Half the player hitbox plus a little: the scan must clear the body, not just the feet. */
    private static final double MARGIN = 0.3;
    /** Jump events this soon after a dash are the dash's own lift, not a jump. */
    private static final int DASH_LIFT_TICKS = 8;
    /** Jump events this soon after being hurt are knockback, not a jump. */
    private static final int KNOCKBACK_TICKS = 6;

    private final CombatSystem system;

    Mobility(CombatSystem system) {
        this.system = system;
    }

    /** Per player per tick: pending roll impulse, sprint memory and the horizontal step, for roll direction. */
    void tick(Player player, CombatState state, long tick) {
        if (state.pendingRoll != null) {
            Vector roll = state.pendingRoll;
            state.pendingRoll = null;
            if (!player.isDead() && !player.isInsideVehicle()) {
                player.setVelocity(roll);
                player.setFallDistance(0.0f);
            }
        }
        if (player.isSprinting()) {
            state.lastSprintTick = tick;
        }
        Location at = player.getLocation();
        if (!Double.isNaN(state.lastX)) {
            double dx = at.getX() - state.lastX;
            double dz = at.getZ() - state.lastZ;
            boolean teleported = dx * dx + dz * dz > 4.0;
            state.moveX = teleported ? 0.0 : dx;
            state.moveZ = teleported ? 0.0 : dz;
        }
        state.lastX = at.getX();
        state.lastZ = at.getZ();
    }

    /** Sprint just stopped: the sneak that stopped it may arrive a tick later. */
    void sprintStopped(Player player) {
        if (system.config().dash && system.eligible(player)) {
            system.state(player).lastSprintTick = Bukkit.getCurrentTick();
        }
    }

    /* =========================================================
     * DASH
     * ========================================================= */

    @SuppressWarnings("deprecation") // client ground flag: the same one vanilla movement trusts
    void sneakPressed(Player player) {
        CombatConfig cfg = system.config();
        if (!cfg.dash || !player.isOnGround()) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        CombatState peek = system.peek(player);
        boolean sprinting = player.isSprinting() || (peek != null && tick - peek.lastSprintTick <= cfg.sprintGrace);
        if (!sprinting || !system.eligible(player)) {
            return;
        }
        CombatState state = system.state(player);
        if (state.parryOpen || state.inRoll(tick) || tick < state.dashReadyTick) {
            system.deny(player, state, tick);
            return;
        }
        Vector dir = CombatSystem.facing(player);
        double room = safeReach(player, dir, cfg.dashSpeed * DASH_TRAVEL + MARGIN) - MARGIN;
        if (room < 1.0) {
            system.deny(player, state, tick);
            return;
        }
        if (!system.spend(player, state, cfg.dashCost, tick)) {
            return;
        }
        double speed = Math.min(cfg.dashSpeed, room / DASH_TRAVEL);
        player.setVelocity(dir.multiply(speed).setY(cfg.dashLift));
        state.lastDashTick = tick;
        state.dashReadyTick = tick + cfg.dashCooldown;
        state.dashStrikeUntil = tick + cfg.strikeWindow;
        state.lastSprintTick = Long.MIN_VALUE / 4;
        system.feedback().dash(player);
    }

    /* =========================================================
     * ROLL
     * ========================================================= */

    /** PlayerJumpEvent: {@code from} / {@code to} is the client's first jump step. */
    void jumped(Player player, Location from, Location to) {
        CombatConfig cfg = system.config();
        if (!cfg.roll || !player.isSneaking()) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        CombatState peek = system.peek(player);
        // Paper fires the jump event for any lift off the ground: our own dash, or knockback.
        if (peek != null && (tick - peek.lastDashTick <= DASH_LIFT_TICKS || tick - peek.lastHurtTick <= KNOCKBACK_TICKS)) {
            return;
        }
        if (!system.eligible(player)) {
            return;
        }
        CombatState state = system.state(player);
        if (state.parryOpen || tick < state.rollRecoverUntil) {
            system.deny(player, state, tick);
            return;
        }
        boolean tired = tick - state.rollEndTick < cfg.rollChainWindow;
        double cost = cfg.rollCost * (tired ? cfg.rollFatigueCostMult : 1.0);
        if (!system.spend(player, state, cost, tick)) {
            return;
        }

        Vector dir = new Vector(state.moveX, 0.0, state.moveZ);
        if (dir.lengthSquared() < 0.0009 && from != null && to != null) {
            dir = new Vector(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ());
        }
        boolean backstep = dir.lengthSquared() < 0.0009;
        dir = backstep ? CombatSystem.facing(player).multiply(-1) : dir.normalize();
        double want = cfg.rollSpeed * (tired ? 0.75 : 1.0) * (backstep ? 0.85 : 1.0);
        double room = Math.max(0.0, safeReach(player, dir, want * ROLL_TRAVEL + MARGIN) - MARGIN);
        double speed = Math.min(want, room / ROLL_TRAVEL);

        int iframes = tired ? cfg.rollFatigueIframes : cfg.rollIframes;
        state.rollStartTick = tick;
        state.rollIframesUntil = tick + iframes;
        state.rollRecoverUntil = tick + cfg.rollRecovery;
        state.rollEndTick = state.rollRecoverUntil;
        state.rollDodged = false;

        // Applied next tick: Paper calls jumpFromGround() after this event returns, which would replace Y.
        state.pendingRoll = dir.multiply(speed).setY(cfg.rollLift);
        player.setFallDistance(0.0f);
        system.feedback().roll(player, tired);
    }

    /* =========================================================
     * TERRAIN
     * ========================================================= */

    /**
     * How far (blocks) the player can travel along {@code dir} before a wall, a drop deeper than three
     * blocks, or something that burns (lava, fire, magma, powder snow). Samples every half block, feet and head.
     */
    static double safeReach(Player player, Vector dir, double max) {
        Location from = player.getLocation();
        World world = from.getWorld();
        if (world == null) {
            return 0.0;
        }
        double safe = 0.0;
        for (double d = 0.5; d <= max + 1.0E-6; d += 0.5) {
            double x = from.getX() + dir.getX() * d;
            double z = from.getZ() + dir.getZ() * d;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int feet = (int) Math.floor(from.getY() + 0.05);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                break;
            }
            Block body = world.getBlockAt(bx, feet, bz);
            Block head = world.getBlockAt(bx, feet + 1, bz);
            if (hazard(body.getType()) || hazard(head.getType())) {
                break;
            }
            boolean step = body.isSolid() && !head.isSolid() && !world.getBlockAt(bx, feet + 2, bz).isSolid();
            if ((body.isSolid() && !step) || head.isSolid()) {
                break;
            }
            if (!step && !groundBelow(world, bx, feet, bz)) {
                break;
            }
            safe = d;
        }
        return safe;
    }

    /** The first thing below within three blocks: solid ground or water is a landing, lava or fire is not. */
    private static boolean groundBelow(World world, int x, int feet, int z) {
        for (int dy = 1; dy <= 3; dy++) {
            Block block = world.getBlockAt(x, feet - dy, z);
            Material type = block.getType();
            if (hazard(type)) {
                return false;
            }
            if (block.isSolid() || type == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    private static boolean hazard(Material type) {
        return type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK || type == Material.POWDER_SNOW
                || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE;
    }
}
