package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Benches you can sit on (2026-11-27): right-click a wooden stair on the hub island with an empty hand and you sit
 * down, facing the way the bench faces. Sneak to stand up.
 */
public final class HubSeats implements Listener {

    public static final String TAG = "ae_hub_seat";

    private final AetherionPit plugin;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();

    public HubSeats(AetherionPit plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block b = event.getClickedBlock();
        if (!b.getType().name().endsWith("_STAIRS") || !b.getType().name().contains("SPRUCE")
                || !(b.getBlockData() instanceof Stairs stairs) || stairs.getHalf() != Stairs.Half.BOTTOM
                || player.isSneaking() || player.isInsideVehicle() || player.getGameMode() == GameMode.SPECTATOR
                || player.getInventory().getItemInMainHand().getType() != Material.AIR
                || !plugin.safeZone().isPitWorld(b.getWorld()) || !plugin.safeZone().isSafe(b.getLocation())
                || !b.getRelative(0, 1, 0).getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        for (ArmorStand s : seats.values()) {
            if (s.isValid() && s.getLocation().getBlock().equals(b)) {
                return;
            }
        }
        // the tall back of a stair is on its facing side: you sit looking the other way
        float yaw = switch (stairs.getFacing()) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case EAST -> 90f;
            default -> -90f;
        };
        Location at = b.getLocation().add(0.5, 0.3, 0.5);
        at.setYaw(yaw);
        ArmorStand seat = b.getWorld().spawn(at, ArmorStand.class, s -> {
            s.setPersistent(false);
            s.addScoreboardTag(TAG);
            s.setInvisible(true);
            s.setMarker(true);
            s.setSmall(true);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSilent(true);
        });
        seat.addPassenger(player);
        seats.put(player.getUniqueId(), seat);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof ArmorStand s && s.getScoreboardTags().contains(TAG)) {
            if (event.getEntity() instanceof Player p) {
                seats.remove(p.getUniqueId());
                Location up = s.getLocation().add(0, 0.7, 0);
                up.setYaw(p.getLocation().getYaw());
                up.setPitch(p.getLocation().getPitch());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        p.teleport(up);
                    }
                });
            }
            Bukkit.getScheduler().runTask(plugin, s::remove);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ArmorStand s = seats.remove(event.getPlayer().getUniqueId());
        if (s != null && s.isValid()) {
            s.remove();
        }
    }

    public void removeAll() {
        seats.values().forEach(s -> {
            if (s.isValid()) {
                s.remove();
            }
        });
        seats.clear();
    }
}
