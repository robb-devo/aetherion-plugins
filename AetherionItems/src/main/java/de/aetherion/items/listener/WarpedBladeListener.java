package de.aetherion.items.listener;

import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WarpedBladeListener implements Listener {

    private final ItemManager itemManager;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public WarpedBladeListener(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isWarpedBlade(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        int tier = DungeonCore.tier(item);
        int cooldownTicks = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                DungeonCore.warpedCooldownTicks(tier)
        );
        double range = DungeonCore.warpedRange(tier);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§5Warped Blade §7recharging… §f" + left + "s"
            ));
            return;
        }

        Location dest = findLanding(player, range);
        if (dest == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7Something warped in the way."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.65f);
            return;
        }

        nextUseTick.put(player.getUniqueId(), tick + cooldownTicks);
        player.setCooldown(item.getType(), cooldownTicks);

        Location from = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 22, 0.25, 0.55, 0.25, 0.35);
        player.getWorld().spawnParticle(Particle.WARPED_SPORE, from.clone().add(0, 1, 0), 10, 0.2, 0.4, 0.2, 0.01);
        player.teleport(dest);
        player.setFallDistance(0);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.45f);
        player.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 18, 0.25, 0.45, 0.25, 0.28);
        player.getWorld().spawnParticle(Particle.WARPED_SPORE, dest.clone().add(0, 1, 0), 8, 0.2, 0.35, 0.2, 0.01);
    }

    private Location findLanding(Player player, double range) {
        Location start = player.getLocation();
        Vector look = start.getDirection();
        Vector dir = new Vector(look.getX(), 0, look.getZ());
        if (dir.lengthSquared() < 1.0e-4) {
            dir = look.clone();
        }
        dir.normalize();

        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                start.clone().add(0, 0.2, 0),
                dir,
                range,
                FluidCollisionMode.NEVER,
                true
        );
        double max = range;
        if (hit != null && hit.getHitPosition() != null) {
            max = Math.max(0.6, hit.getHitPosition().distance(start.toVector()) - 0.55);
        }

        Location best = null;
        for (double step = 1.0; step <= max + 0.05; step += 1.0) {
            Location candidate = start.clone().add(dir.clone().multiply(Math.min(step, max)));
            candidate.setYaw(start.getYaw());
            candidate.setPitch(start.getPitch());
            Location safe = snapToSpace(candidate);
            if (safe != null) {
                best = safe;
            }
        }
        if (best == null) {
            return null;
        }
        if (best.distanceSquared(start) < 0.55) {
            return null;
        }
        return best;
    }

    private Location snapToSpace(Location location) {
        Location feet = location.clone();
        for (int up = 0; up <= 2; up++) {
            Location test = feet.clone().add(0, up, 0);
            if (isSpaceFree(test) && !isHazard(test)) {
                return test;
            }
        }
        return null;
    }

    private boolean isSpaceFree(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    private boolean isHazard(Location location) {
        Material ground = location.clone().add(0, -0.05, 0).getBlock().getType();
        Material feet = location.getBlock().getType();
        return feet == Material.LAVA
                || feet == Material.FIRE
                || feet == Material.SOUL_FIRE
                || ground == Material.LAVA
                || ground == Material.MAGMA_BLOCK;
    }

    private boolean isWarpedBlade(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return "warped_blade".equalsIgnoreCase(itemManager.getItemId(item));
    }
}
