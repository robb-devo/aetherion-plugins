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
 * Ashen Katana — dash, rise, a real cherry-leaf hover, then a slam shockwave.
 * Hostiles only. One commitment, not a spam button.
 */
public final class AshenKatanaListener implements Listener {

    private static final int COOLDOWN_TICKS = 160;
    private static final LegacyComponentSerializer TEXT = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private static final List<List<BlockDisplay>> LIVE = new CopyOnWriteArrayList<>();

    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    /** Plugin disable: every hovering leaf display goes with it. */
    public static void shutdown() {
        for (List<BlockDisplay> leaves : LIVE) {
            clear(leaves);
        }
        LIVE.clear();
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
        double dashDamage = Math.min(48.0, weapon * 0.45);
        double slam = Math.min(110.0, weapon * 1.15);
        double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
        double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
        if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
            slam = Math.min(130.0, slam * (1.0 + Math.min(0.75, Math.max(0.0, critDamage) / 100.0)));
        }
        double slamDamage = slam;

        Vector flat = player.getLocation().getDirection().clone().setY(0);
        if (flat.lengthSquared() < 0.01) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        Vector dash = flat.clone();

        player.showTitle(Title.title(
                TEXT.deserialize("§dASHEN DRAW"),
                TEXT.deserialize("§7The grove answers."),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(700), Duration.ofMillis(220))
        ));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.9f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 0.7f);

        List<BlockDisplay> leaves = new ArrayList<>();
        LIVE.add(leaves);
        new BukkitRunnable() {
            int tickCount;
            int slamAt = -1;
            final Set<UUID> dashed = new HashSet<>();
            boolean slammed;
            boolean gravityWas = player.hasGravity();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    finish();
                    return;
                }
                tickCount++;
                player.setFallDistance(0);
                World world = player.getWorld();

                if (tickCount <= 8) {
                    stepDash(player, dash);
                    slash(world, player.getLocation().add(0, 1.0, 0), dash);
                    strikeNear(player, dashDamage, 2.1, dashed);
                    if (tickCount % 2 == 0) {
                        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.45f);
                    }
                    return;
                }
                if (tickCount <= 14) {
                    player.setVelocity(new Vector(dash.getX() * 0.12, 0.62, dash.getZ() * 0.12));
                    world.spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 0.4, 0), 4, 0.25, 0.2, 0.25, 0.02);
                    if (tickCount == 14) {
                        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 0.55f, 1.4f);
                    }
                    return;
                }
                if (tickCount <= 32) {
                    player.setGravity(false);
                    player.setVelocity(new Vector(0, 0.02, 0));
                    if (leaves.isEmpty()) {
                        spawnLeaves(world, player.getLocation(), leaves, 14);
                    }
                    spin(player.getLocation(), leaves, tickCount, 2.3, 0.28);
                    if (tickCount % 6 == 0) {
                        world.playSound(player.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.55f, 0.7f);
                        player.sendActionBar(TEXT.deserialize("§dFalling Blossoms"));
                    }
                    return;
                }
                if (!slammed) {
                    player.setGravity(true);
                    player.setVelocity(new Vector(dash.getX() * 0.05, -1.7, dash.getZ() * 0.05));
                    spin(player.getLocation(), leaves, tickCount, 1.5, 0.34);
                    if ((player.isOnGround() && tickCount > 36) || tickCount >= 50) {
                        slammed = true;
                        slamAt = tickCount;
                        impact(player, slamDamage);
                    }
                    return;
                }
                int wave = tickCount - slamAt;
                expandShockwave(player.getLocation(), leaves, wave);
                if (wave >= 14) {
                    finish();
                }
            }

            private void finish() {
                if (player.isOnline()) {
                    player.setGravity(gravityWas);
                    player.setFallDistance(0);
                }
                clear(leaves);
                LIVE.remove(leaves);
                busy.remove(player.getUniqueId());
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player player, double damage) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.15f, 0.45f);
        world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.55f);
        world.playSound(at, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.5f);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.4, 0), 28, 1.1, 0.3, 1.1, 0.05);
        Set<UUID> hit = new HashSet<>();
        strikeNear(player, damage, 4.8, hit);
        for (Entity entity : world.getNearbyEntities(at, 4.8, 3.2, 4.8)) {
            if (!(entity instanceof LivingEntity living) || !hit.contains(living.getUniqueId())) {
                continue;
            }
            Vector away = living.getLocation().toVector().subtract(at.toVector());
            if (away.lengthSquared() < 0.04) {
                away = new Vector(0.2, 0.4, 0.2);
            } else {
                away.normalize().multiply(0.75).setY(0.42);
            }
            living.setVelocity(away);
        }
        player.sendActionBar(TEXT.deserialize("§dShockwave"));
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
        }
    }

    private static void stepDash(Player player, Vector dash) {
        Location next = player.getLocation().clone().add(dash.clone().multiply(0.9));
        next.setYaw(player.getLocation().getYaw());
        next.setPitch(8f);
        if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) {
            return;
        }
        player.teleport(next);
        player.setFallDistance(0);
    }

    private static void slash(World world, Location at, Vector dir) {
        Vector step = dir.clone().multiply(0.35);
        Location cursor = at.clone();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 170, 200), 1.25f);
        for (int i = 0; i < 6; i++) {
            cursor.add(step);
            world.spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.CHERRY_LEAVES, cursor, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    private static void spawnLeaves(World world, Location at, List<BlockDisplay> leaves, int count) {
        float size = 0.42f;
        for (int i = 0; i < count; i++) {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.CHERRY_LEAVES.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-size / 2f, -size / 2f, -size / 2f),
                        new AxisAngle4f(),
                        new Vector3f(size, size, size),
                        new AxisAngle4f()
                ));
                spawned.setBrightness(new Display.Brightness(12, 12));
                spawned.setTeleportDuration(2);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(Color.fromRGB(255, 176, 204));
                spawned.setPersistent(false);
                spawned.setGravity(false);
            });
            leaves.add(display);
        }
    }

    private static void spin(Location focus, List<BlockDisplay> leaves, int tick, double height, double spin) {
        int count = leaves.size();
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double along = i / (double) Math.max(1, count);
            double y = (along * height + tick * 0.06) % height;
            double yaw = tick * spin + (i % 4) * (Math.PI / 2.0) + y * 0.8;
            double radius = 0.85 + y * 0.45;
            leaf.teleport(focus.clone().add(Math.cos(yaw) * radius, 0.15 + y, Math.sin(yaw) * radius));
        }
    }

    private static void expandShockwave(Location focus, List<BlockDisplay> leaves, int wave) {
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        double radius = 1.1 + wave * 0.38;
        int count = leaves.size();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(220, 225, 235), 1.35f);
        for (int i = 0; i < count; i++) {
            BlockDisplay leaf = leaves.get(i);
            if (leaf == null || !leaf.isValid()) {
                continue;
            }
            double ang = Math.PI * 2 * i / Math.max(1, count) + wave * 0.18;
            leaf.teleport(focus.clone().add(Math.cos(ang) * radius, 0.25, Math.sin(ang) * radius));
        }
        if (wave % 2 == 0) {
            for (int i = 0; i < 10; i++) {
                double ang = Math.PI * 2 * i / 10.0;
                Location point = focus.clone().add(Math.cos(ang) * radius, 0.2, Math.sin(ang) * radius);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.04, 0.05, 0.04, 0.01);
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
}
