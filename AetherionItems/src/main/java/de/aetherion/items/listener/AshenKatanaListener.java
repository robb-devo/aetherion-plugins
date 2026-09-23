package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ashen Katana — a short velocity dash, a player-scale cherry-leaf hover, then a slam.
 * Hostiles only. The dash is movement, not a teleport.
 */
public final class AshenKatanaListener implements Listener {

    private static final int COOLDOWN_TICKS = 160;
    private static final int DASH_TICKS = 5;
    private static final int RISE_TICKS = 4;
    private static final int HOVER_END = 22;
    private static final int FALL_LIMIT = 40;
    private static final int WAVE_TICKS = 12;
    private static final int LEAF_COUNT = 8;

    /** Reapplied each tick so the client interpolates. Sum is about six blocks. */
    private static final double[] DASH_SPEED = {1.65, 1.42, 1.18, 0.88, 0.58};
    private static final double[] RISE_Y = {0.44, 0.28, 0.15, 0.05};

    private static final Color PETAL = Color.fromRGB(255, 170, 200);
    private static final Color ASH = Color.fromRGB(90, 70, 95);
    private static final Color STEEL = Color.fromRGB(220, 225, 235);

    private static final LegacyComponentSerializer TEXT = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private static final List<List<BlockDisplay>> LIVE = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Boolean> SUSPENDED_GRAVITY = new ConcurrentHashMap<>();

    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    /** Plugin disable: hovering players get gravity back, and every leaf display is removed. */
    public static void shutdown() {
        for (Map.Entry<UUID, Boolean> entry : SUSPENDED_GRAVITY.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setGravity(Boolean.TRUE.equals(entry.getValue()));
                player.setFallDistance(0f);
            }
        }
        SUSPENDED_GRAVITY.clear();
        for (List<BlockDisplay> leaves : LIVE) {
            clear(leaves);
        }
        LIVE.clear();
    }

    /**
     * Dash chip. The melee swing is the weapon's damage stat; this is the lighter hit along the glide.
     */
    static double dashDamage(double weaponDamage) {
        return Math.min(55.0, Math.max(16.0, weaponDamage * 0.45));
    }

    /**
     * Slam equals the weapon damage stat, then the melee crit rule
     * {@code hit * (1 + critDamage / 100)}. 140 crit damage is a +140% bonus.
     */
    static double slamDamage(double weaponDamage, double critChance, double critDamage, double roll01) {
        double hit = Math.max(1.0, weaponDamage);
        if (critChance > 0.0 && roll01 * 100.0 < critChance) {
            hit *= 1.0 + Math.max(0.0, critDamage) / 100.0;
        }
        return Math.min(320.0, hit);
    }

    static boolean critRoll(double critChance, double roll01) {
        return critChance > 0.0 && roll01 * 100.0 < critChance;
    }

    public AshenKatanaListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!"ashen_katana".equalsIgnoreCase(itemManager.getItemId(item))) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        if (!busy.add(player.getUniqueId())) {
            player.sendActionBar(TEXT.deserialize("§dAshen Katana §7is already drawn…"));
            return;
        }

        long tick = Bukkit.getCurrentTick();
        int cooldown = ProgressionEffects.cooldownTicks(player, itemManager, COOLDOWN_TICKS);
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            busy.remove(player.getUniqueId());
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(TEXT.deserialize("§dAshen Katana §7recharging… §f" + left + "s"));
            return;
        }
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
        player.setCooldown(item.getType(), cooldown);

        double weapon = Math.max(24.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
        double dashHit = dashDamage(weapon);
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        double roll = Math.random();
        boolean crit = critRoll(critChance, roll);
        double slam = slamDamage(weapon, critChance, critDamage, roll);

        Vector flat = player.getLocation().getDirection().clone().setY(0);
        if (flat.lengthSquared() < 0.01) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        Vector dash = flat.clone();

        player.showTitle(Title.title(
                TEXT.deserialize("§dASHEN DRAW"),
                TEXT.deserialize("§7The grove answers."),
                Title.Times.times(Duration.ofMillis(40), Duration.ofMillis(420), Duration.ofMillis(160))
        ));
        World startWorld = player.getWorld();
        startWorld.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 0.72f);
        startWorld.playSound(player.getLocation(), Sound.BLOCK_CHERRY_WOOD_STEP, 0.55f, 1.15f);
        startWorld.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 0.85f);

        List<BlockDisplay> leaves = new ArrayList<>();
        LIVE.add(leaves);
        boolean gravityWas = player.hasGravity();
        new BukkitRunnable() {
            int tickCount;
            int slamAt = -1;
            final Set<UUID> dashed = new HashSet<>();
            boolean slammed;
            boolean hovering;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finish();
                    return;
                }
                tickCount++;
                player.setFallDistance(0);
                World world = player.getWorld();

                if (tickCount <= DASH_TICKS) {
                    boolean open = glide(player, dash, tickCount - 1);
                    dashTrail(world, player.getLocation(), dash);
                    strikeNear(player, dashHit, 2.05, dashed);
                    if (tickCount % 2 == 0) {
                        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.28f, 1.55f);
                    }
                    if (!open) {
                        tickCount = DASH_TICKS;
                    }
                    return;
                }
                if (tickCount <= DASH_TICKS + RISE_TICKS) {
                    int step = tickCount - DASH_TICKS - 1;
                    double lift = RISE_Y[Math.max(0, Math.min(step, RISE_Y.length - 1))];
                    player.setVelocity(new Vector(dash.getX() * 0.26, lift, dash.getZ() * 0.26));
                    world.spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 0.6, 0), 2, 0.12, 0.08, 0.12, 0.01);
                    if (tickCount == DASH_TICKS + RISE_TICKS) {
                        world.playSound(player.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.45f, 1.25f);
                    }
                    return;
                }
                if (tickCount <= HOVER_END) {
                    if (!hovering) {
                        hovering = true;
                        SUSPENDED_GRAVITY.put(player.getUniqueId(), gravityWas);
                        player.setGravity(false);
                    }
                    player.setVelocity(new Vector(dash.getX() * 0.04, 0.015, dash.getZ() * 0.04));
                    if (leaves.isEmpty()) {
                        spawnLeaves(world, player.getLocation(), leaves, LEAF_COUNT);
                    }
                    spin(player.getLocation(), leaves, tickCount, 1.25, 0.22);
                    if (tickCount % 5 == 0) {
                        world.playSound(player.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.32f, 0.85f);
                        world.spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1.0, 0), 3, 0.25, 0.2, 0.25, 0.01);
                        player.sendActionBar(TEXT.deserialize("§dFalling Blossoms"));
                    }
                    return;
                }
                if (!slammed) {
                    if (hovering) {
                        hovering = false;
                        player.setGravity(gravityWas);
                        SUSPENDED_GRAVITY.remove(player.getUniqueId());
                    }
                    player.setVelocity(new Vector(dash.getX() * 0.04, -1.15, dash.getZ() * 0.04));
                    spin(player.getLocation(), leaves, tickCount, 0.9, 0.3);
                    if ((player.isOnGround() && tickCount > HOVER_END + 3) || tickCount >= FALL_LIMIT) {
                        slammed = true;
                        slamAt = tickCount;
                        impact(player, slam, crit);
                    }
                    return;
                }
                int wave = tickCount - slamAt;
                expandShockwave(player.getLocation(), leaves, wave);
                if (wave >= WAVE_TICKS) {
                    finish();
                }
            }

            private void finish() {
                if (player.isOnline()) {
                    player.setGravity(gravityWas);
                    player.setFallDistance(0);
                }
                SUSPENDED_GRAVITY.remove(player.getUniqueId());
                clear(leaves);
                LIVE.remove(leaves);
                busy.remove(player.getUniqueId());
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player player, double damage, boolean crit) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.95f, 0.55f);
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.45f, 1.35f);
        world.playSound(at, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.85f, 0.55f);
        world.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.35, 0), 14, 0.7, 0.2, 0.7, 0.04);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 10, 0.45, 0.12, 0.45, 0, new Particle.DustOptions(PETAL, 1.15f));
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.15, 0), 6, 0.3, 0.05, 0.3, 0, new Particle.DustOptions(ASH, 0.9f));
        Set<UUID> hit = new HashSet<>();
        strikeNear(player, damage, 4.2, hit);
        for (Entity entity : world.getNearbyEntities(at, 4.2, 2.8, 4.2)) {
            if (!(entity instanceof LivingEntity living) || !hit.contains(living.getUniqueId())) {
                continue;
            }
            Vector away = living.getLocation().toVector().subtract(at.toVector());
            if (away.lengthSquared() < 0.04) {
                away = new Vector(0.2, 0.35, 0.2);
            } else {
                away.normalize().multiply(0.62).setY(0.36);
            }
            living.setVelocity(away);
        }
        player.sendActionBar(TEXT.deserialize(crit ? "§dShockwave §f✦" : "§dShockwave"));
    }

    private void strikeNear(Player player, double damage, double radius, Set<UUID> already) {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || already.contains(entity.getUniqueId())) {
                continue;
            }
            already.add(entity.getUniqueId());
            LivingEntity living = (LivingEntity) entity;
            ScriptedHits.run(() -> living.damage(damage, player));
            living.getWorld().spawnParticle(Particle.SWEEP_ATTACK, living.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            living.getWorld().spawnParticle(Particle.CHERRY_LEAVES, living.getLocation().add(0, 1, 0), 2, 0.1, 0.15, 0.1, 0.01);
        }
    }

    /**
     * @return false when a wall ends the glide early so the hover can still play
     */
    private static boolean glide(Player player, Vector dash, int step) {
        double speed = DASH_SPEED[Math.max(0, Math.min(step, DASH_SPEED.length - 1))];
        Location feet = player.getLocation();
        Vector probe = dash.clone().multiply(0.85);
        boolean lowBlocked = blocked(feet, probe);
        boolean stepBlocked = blocked(feet.clone().add(0, 1.0, 0), probe);
        if (lowBlocked && stepBlocked) {
            player.setVelocity(new Vector(0, 0.05, 0));
            return false;
        }
        double lift = lowBlocked ? 0.42 : (step == 0 ? 0.12 : 0.04);
        double forward = lowBlocked ? speed * 0.45 : speed;
        player.setVelocity(new Vector(dash.getX() * forward, lift, dash.getZ() * forward));
        player.setFallDistance(0f);
        return true;
    }

    private static boolean blocked(Location feet, Vector probe) {
        Location next = feet.clone().add(probe);
        return !next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private static void dashTrail(World world, Location at, Vector dir) {
        Location origin = at.clone().add(0, 0.95, 0).add(dir.clone().multiply(-0.25));
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Particle.DustOptions petal = new Particle.DustOptions(PETAL, 0.95f);
        Particle.DustOptions steel = new Particle.DustOptions(STEEL, 0.65f);
        for (int i = 0; i < 4; i++) {
            double sway = (i % 2 == 0 ? 0.16 : -0.16);
            Location point = origin.clone()
                    .add(dir.clone().multiply(i * 0.26))
                    .add(side.clone().multiply(sway));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, i == 0 ? steel : petal);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.03, 0.04, 0.03, 0.005);
            }
        }
    }

    private static void spawnLeaves(World world, Location at, List<BlockDisplay> leaves, int count) {
        float size = 0.30f;
        for (int i = 0; i < count; i++) {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(cherryLeaves());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-size / 2f, -size / 2f, -size / 2f),
                        new AxisAngle4f(),
                        new Vector3f(size, size, size),
                        new AxisAngle4f()
                ));
                spawned.setBrightness(new Display.Brightness(12, 12));
                spawned.setTeleportDuration(3);
                spawned.setInterpolationDuration(3);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(PETAL);
                spawned.setPersistent(false);
                spawned.setGravity(false);
            });
            leaves.add(display);
        }
    }

    private static void spin(Location focus, List<BlockDisplay> leaves, int tick, double height, double spin) {
        int count = leaves.size();
        double column = Math.max(0.8, height);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double along = i / (double) Math.max(1, count);
            double y = (along * column + tick * 0.05) % column;
            double yaw = tick * spin + (i % 4) * (Math.PI / 2.0) + y * 0.7;
            double radius = 0.62 + y * 0.28;
            leaf.teleport(focus.clone().add(Math.cos(yaw) * radius, 0.2 + y, Math.sin(yaw) * radius));
        }
    }

    private static void expandShockwave(Location focus, List<BlockDisplay> leaves, int wave) {
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        double radius = 0.85 + wave * 0.26;
        int count = leaves.size();
        Particle.DustOptions petal = new Particle.DustOptions(PETAL, 1.05f);
        Particle.DustOptions steel = new Particle.DustOptions(STEEL, 0.8f);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double ang = Math.PI * 2 * i / Math.max(1, count) + wave * 0.16;
            leaf.teleport(focus.clone().add(Math.cos(ang) * radius, 0.22, Math.sin(ang) * radius));
        }
        if (wave % 2 == 0) {
            int points = 8;
            for (int i = 0; i < points; i++) {
                double ang = Math.PI * 2 * i / points;
                Location point = focus.clone().add(Math.cos(ang) * radius, 0.18, Math.sin(ang) * radius);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, i % 2 == 0 ? petal : steel);
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.03, 0.04, 0.03, 0.008);
                }
            }
        }
    }

    private static void clear(List<BlockDisplay> leaves) {
        for (BlockDisplay leaf : leaves) {
            if (leaf != null && leaf.isValid()) {
                leaf.remove();
            }
        }
        leaves.clear();
    }

    /**
     * Real cherry leaves. Default leaf data is distance 7 and not persistent, so clients skip the model.
     */
    private static org.bukkit.block.data.BlockData cherryLeaves() {
        Leaves leaves = (Leaves) Material.CHERRY_LEAVES.createBlockData();
        leaves.setPersistent(true);
        leaves.setDistance(1);
        return leaves;
    }
}
