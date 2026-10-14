package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Grappling Hook — pulls until arrival (distance-based), not a fixed short tick budget.
 */
public final class GrapplingHookListener implements Listener {

    private static final String ID = "grappling_hook";
    private static final double RANGE = 42.0;
    private static final long COOLDOWN_MS = 550L;
    private static final double ARRIVE_DIST = 1.25;
    private static final int MAX_PULL_TICKS = 90;
    private static final Color LINE = Color.fromRGB(90, 210, 230);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static GrapplingHookListener instance;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Pull> pulls = new ConcurrentHashMap<>();

    public GrapplingHookListener(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static void shutdown() {
        if (instance != null) {
            for (Pull pull : instance.pulls.values()) {
                pull.cancel();
            }
            instance.pulls.clear();
            instance.cooldown.clear();
            instance = null;
        }
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isHook(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (player.isSneaking()) {
            cancelPull(player, true);
            return;
        }
        fire(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (!isHook(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            cancelPull(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPull(event.getPlayer(), false);
        cooldown.remove(event.getPlayer().getUniqueId());
    }

    private void fire(Player player) {
        UUID id = player.getUniqueId();
        Long next = cooldown.get(id);
        if (next != null && System.currentTimeMillis() < next) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7Hook recharging…"));
            return;
        }
        Pull existing = pulls.get(id);
        if (existing != null) {
            existing.cancel();
            pulls.remove(id, existing);
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, RANGE, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7Nothing to latch."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.55f);
            return;
        }

        Location latch = hit.getHitPosition().toLocation(world);
        if (hit.getHitBlockFace() != null) {
            latch.add(hit.getHitBlockFace().getDirection().multiply(0.15));
        }
        double dist = latch.distance(eye);
        cooldown.put(id, System.currentTimeMillis() + COOLDOWN_MS);
        Pull pull = new Pull(player, latch, dist);
        pulls.put(id, pull);
        pull.start();
        player.sendActionBar(net.kyori.adventure.text.Component.text("§3Latch!"));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.7f, 1.35f);
        player.playSound(latch, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.55f, 1.6f);
    }

    private void cancelPull(Player player, boolean feedback) {
        Pull pull = pulls.remove(player.getUniqueId());
        if (pull == null) {
            return;
        }
        pull.cancel();
        if (feedback) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7Hook released."));
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.5f, 1.1f);
        }
    }

    private static boolean isHook(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String stamped = stack.getItemMeta().getPersistentDataContainer().get(
                AetherKeys.namespaced("aetherion", "test_gear"),
                PersistentDataType.STRING
        );
        return ID.equalsIgnoreCase(stamped);
    }

    private final class Pull {
        private final UUID playerId;
        private final Location latch;
        private final int budget;
        private final List<BlockDisplay> line = new ArrayList<>();
        private BukkitTask task;
        private int tick;

        Pull(Player player, Location latch, double distance) {
            this.playerId = player.getUniqueId();
            this.latch = latch.clone();
            /* ~1 block / tick at cruise — never starve long shots. */
            this.budget = Math.min(MAX_PULL_TICKS, Math.max(18, (int) Math.ceil(distance / 0.85) + 8));
        }

        void start() {
            spawnLine();
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }

        void tick() {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                finish(false);
                return;
            }
            if (player.isSneaking()) {
                finish(true);
                return;
            }
            tick++;
            Location from = player.getLocation().add(0, 0.9, 0);
            Vector delta = latch.toVector().subtract(from.toVector());
            double dist = delta.length();
            if (dist <= ARRIVE_DIST) {
                softLand(player, delta);
                finish(false);
                return;
            }
            if (tick >= budget) {
                /* Last push toward latch instead of dying mid-air. */
                softLand(player, delta);
                finish(false);
                return;
            }
            double progress = Math.min(1.0, tick / (double) budget);
            double speed = 0.72 + 1.05 * progress;
            Vector pull = delta.normalize().multiply(Math.min(speed, dist * 0.55));
            Vector current = player.getVelocity();
            player.setVelocity(current.multiply(0.28).add(pull));
            player.setFallDistance(0f);
            updateLine(from);
            if (tick % 3 == 0) {
                player.getWorld().spawnParticle(Particle.CRIT, from, 2, 0.05, 0.05, 0.05, 0);
            }
        }

        private void softLand(Player player, Vector delta) {
            if (delta.lengthSquared() > 0.01) {
                player.setVelocity(delta.normalize().multiply(0.28));
            } else {
                player.setVelocity(new Vector(0, 0.08, 0));
            }
            player.setFallDistance(0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.6f);
            player.playSound(latch, Sound.BLOCK_CHAIN_PLACE, 0.55f, 1.4f);
        }

        private void spawnLine() {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                return;
            }
            Location from = player.getLocation().add(0, 0.9, 0);
            World world = from.getWorld();
            if (world == null) {
                return;
            }
            int segs = 10;
            for (int i = 0; i < segs; i++) {
                double u = (i + 0.5) / segs;
                Location at = from.clone().add(latch.toVector().subtract(from.toVector()).multiply(u));
                BlockDisplay bead = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                    spawned.setPersistent(false);
                    spawned.setBrightness(LIT);
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(LINE);
                    spawned.setTeleportDuration(1);
                    spawned.setTransformation(tinyBead());
                });
                LIVE.add(bead);
                line.add(bead);
            }
        }

        private void updateLine(Location from) {
            Vector span = latch.toVector().subtract(from.toVector());
            for (int i = 0; i < line.size(); i++) {
                BlockDisplay bead = line.get(i);
                if (bead == null || !bead.isValid()) {
                    continue;
                }
                double u = (i + 0.5) / line.size();
                bead.teleport(from.clone().add(span.clone().multiply(u)));
            }
        }

        private void finish(boolean cancelled) {
            cancel();
            pulls.remove(playerId, this);
            if (cancelled) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§7Hook released."));
                }
            }
        }

        void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            for (BlockDisplay bead : line) {
                LIVE.remove(bead);
                if (bead != null && bead.isValid()) {
                    bead.remove();
                }
            }
            line.clear();
        }
    }

    private static Transformation tinyBead() {
        float s = 0.12f;
        return new Transformation(
                new Vector3f(-s / 2f, -s / 2f, -s / 2f),
                new Quaternionf(),
                new Vector3f(s, s, s),
                new Quaternionf()
        );
    }
}
