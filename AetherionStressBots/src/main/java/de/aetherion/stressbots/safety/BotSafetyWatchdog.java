package de.aetherion.stressbots.safety;

import de.aetherion.stressbots.AetherionStressBots;
import de.aetherion.stressbots.role.BotLocations;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;
import de.aetherion.stressbots.role.BotRoleRegistry;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps QA bots on their Skyblock pads: void/fall cancel, leash pull, idle re-anchor, pad hops.
 */
public final class BotSafetyWatchdog implements Listener, Runnable {

    private final AetherionStressBots plugin;
    private final Map<UUID, Long> recoverAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHopAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> idleSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> padFlightUntil = new ConcurrentHashMap<>();

    public BotSafetyWatchdog(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler == null) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause == DamageCause.VOID || cause == DamageCause.FALL) {
            event.setCancelled(true);
            recover(player, handler, cause == DamageCause.VOID ? "void" : "fall", false);
            return;
        }
        if (handler.role() == BotRole.ROAM && isMob(cause)) {
            event.setCancelled(true);
            recover(player, handler, "hostile", true);
        }
        if (handler.role() == BotRole.PAD && cause == DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @Override
    public void run() {
        double floor = plugin.getConfig().getDouble("testbots.safety.void-floor-y", 40);
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler == null) {
                continue;
            }
            Location loc = player.getLocation();
            ConfigurationSection section = roleSection(handler);
            Location home = BotLocations.assignedAnchor(player, section);
            double leash = leashOf(section);

            trackPadFlight(player, handler, loc, now);

            if (loc.getY() < floor) {
                recover(player, handler, "void-floor", false);
                continue;
            }
            boolean padFlight = handler.role() == BotRole.PAD && now < padFlightUntil.getOrDefault(player.getUniqueId(), 0L);
            if (padFlight) {
                plugin.getActivity().markActivity(player, "pad_hop", "mid-hop");
                continue;
            }
            if (home != null && BotLocations.horizontalDistance(loc, home) > leash) {
                Location nearest = BotLocations.nearestAnchor(loc, section);
                if (nearest == null || BotLocations.horizontalDistance(loc, nearest) > leash) {
                    recover(player, handler, "leash", false);
                    continue;
                }
            }
            if (!BotLocations.isSolidStand(loc) && loc.getY() < floor + 15 && player.getVelocity().getY() < -0.35) {
                recover(player, handler, "falling", false);
                continue;
            }

            String activity = plugin.getActivity().snapshot(player).activity();
            boolean idle = "idle".equals(activity) || "stuck".equals(activity);
            if (idle) {
                idleSince.putIfAbsent(player.getUniqueId(), now);
            } else {
                idleSince.remove(player.getUniqueId());
            }
            long idleMs = now - idleSince.getOrDefault(player.getUniqueId(), now);
            int reanchorTicks = plugin.getConfig().getInt("testbots.safety.idle-reanchor-ticks", 240);
            if (idle && idleMs > reanchorTicks * 50L) {
                recover(player, handler, "idle-reanchor", true);
                idleSince.remove(player.getUniqueId());
                continue;
            }

            int hopTicks = plugin.getConfig().getInt("testbots.safety.pad-hop-ticks", 0);
            if ((handler.role() == BotRole.ROAM || handler.role() == BotRole.PAD) && hopTicks > 0) {
                long last = lastHopAt.getOrDefault(player.getUniqueId(), 0L);
                long interval = handler.role() == BotRole.PAD ? Math.max(400L, hopTicks * 20L) : hopTicks * 50L;
                if (now - last > interval && handler.role() == BotRole.ROAM) {
                    recover(player, handler, "pad-hop", true);
                }
            }
        }
    }

    public boolean recover(Player player, BotRoleHandler handler, String reason, boolean otherPad) {
        if (player == null || handler == null || !player.isOnline()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("testbots.safety.recover-cooldown-ms", 1500);
        Long previous = recoverAt.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            return false;
        }
        ConfigurationSection section = roleSection(handler);
        Location dest = otherPad
                ? BotLocations.otherAnchor(player, section, player.getLocation())
                : BotLocations.assignedAnchor(player, section);
        if (dest == null) {
            dest = handler.destination(player);
        }
        if (dest == null) {
            plugin.getActivity().markError(player, "recover failed: no pad");
            return false;
        }
        recoverAt.put(player.getUniqueId(), now);
        lastHopAt.put(player.getUniqueId(), now);
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(dest);
        plugin.getActivity().markRecovering(player, reason + " → " + BotLocations.format(dest));
        plugin.getLogger().info("Recovered " + player.getName() + " (" + reason + ") → " + BotLocations.format(dest));
        return true;
    }

    private void trackPadFlight(Player player, BotRoleHandler handler, Location loc, long now) {
        if (handler.role() != BotRole.PAD) {
            return;
        }
        Vector velocity = player.getVelocity();
        boolean launched = velocity.getY() > 0.55 || (!player.isOnGround() && loc.getY() > 60 && velocity.lengthSquared() > 0.35);
        if (launched) {
            long grace = plugin.getConfig().getLong("testbots.safety.pad-flight-ms", 8000L);
            padFlightUntil.put(player.getUniqueId(), now + grace);
            lastHopAt.put(player.getUniqueId(), now);
        } else if (player.isOnGround() && BotLocations.isSolidStand(loc) && now > padFlightUntil.getOrDefault(player.getUniqueId(), 0L)) {
            padFlightUntil.remove(player.getUniqueId());
        }
    }

    private ConfigurationSection roleSection(BotRoleHandler handler) {
        ConfigurationSection qa = BotRoleRegistry.roleSection(plugin, handler.role());
        if (qa != null) {
            return qa;
        }
        return plugin.getConfig().getConfigurationSection(handler.role().id());
    }

    private double leashOf(ConfigurationSection section) {
        double fallback = plugin.getConfig().getDouble("testbots.safety.leash-radius", 18);
        if (section == null) {
            return fallback;
        }
        return section.getDouble("leash-radius", fallback);
    }

    private static boolean isMob(DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK
                || cause == DamageCause.PROJECTILE
                || cause == DamageCause.ENTITY_SWEEP_ATTACK
                || cause == DamageCause.ENTITY_EXPLOSION;
    }
}
