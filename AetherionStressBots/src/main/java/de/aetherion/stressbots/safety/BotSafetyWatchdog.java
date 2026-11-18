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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps QA bots on their Skyblock pads: void cancel, leash pull, idle re-anchor, pad hops.
 * The leash is measured from where the plugin last put each bot, so the 5-tick loop never scans blocks
 * for a home; the floor is per role ({@code void-floor-y}), because quarry floors sit below island pads.
 */
public final class BotSafetyWatchdog implements Listener, Runnable {

    private final AetherionStressBots plugin;
    private final Map<UUID, Long> recoverAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHopAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> idleSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> padFlightUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> homes = new ConcurrentHashMap<>();
    /** Free roles: the last ground they stood on, where the void sends them back. */
    private final Map<UUID, Location> lastGround = new ConcurrentHashMap<>();

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
        if (cause == DamageCause.VOID) {
            event.setCancelled(true);
            if (handler.role().free()) {
                recoverFree(player, "void");
            } else {
                recover(player, handler, "void", false);
            }
            return;
        }
        if (cause == DamageCause.FALL) {
            // Landing is fine (a quarry drop, a jump-pad arc). The tick below handles void and leash.
            event.setCancelled(true);
            return;
        }
        if (handler.role() == BotRole.ROAM && isMob(cause)) {
            event.setCancelled(true);
            recover(player, handler, "hostile", true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        homes.remove(id);
        recoverAt.remove(id);
        lastHopAt.remove(id);
        idleSince.remove(id);
        padFlightUntil.remove(id);
        lastGround.remove(id);
    }

    /** Where the plugin last put this bot (setup, respawn, recover). The leash is measured from here. */
    public void rememberHome(Player player, Location home) {
        if (player != null && home != null && home.getWorld() != null) {
            homes.put(player.getUniqueId(), home.clone());
        }
    }

    public Location homeOf(Player player) {
        Location home = player == null ? null : homes.get(player.getUniqueId());
        return home == null ? null : home.clone();
    }

    public double floorOf(BotRoleHandler handler) {
        return BotLocations.voidFloor(plugin.getConfig(), handler == null ? null : roleSection(handler));
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler == null) {
                continue;
            }
            Location loc = player.getLocation();
            if (handler.role().free()) {
                watchFree(player, loc);
                continue;
            }
            ConfigurationSection section = roleSection(handler);
            double floor = BotLocations.voidFloor(plugin.getConfig(), section);
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
            Location home = homes.get(player.getUniqueId());
            if (home == null && player.isOnGround() && BotLocations.isSolidStand(loc)) {
                // Plugin reloaded while the bot was out: adopt where it stands.
                rememberHome(player, loc);
                home = loc;
            }
            if (home != null && BotLocations.horizontalDistance(loc, home) > leash) {
                // Landed on another configured pad (jump pads, pad hops): that pad becomes home.
                Location near = BotLocations.nearestAnchor(loc, section);
                if (near != null && BotLocations.horizontalDistance(loc, near) <= leash) {
                    rememberHome(player, near);
                } else {
                    recover(player, handler, "leash", false);
                    continue;
                }
            }
            if (loc.getY() < floor + 15 && player.getVelocity().getY() < -0.35 && !BotLocations.isSolidStand(loc)) {
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

    /**
     * Travelling roles walk wherever the server lets players walk: no leash, no idle re-anchor, no pad hops, and
     * the floor is the world's real bottom (dungeons and islands sit at other heights than the main-world pads).
     */
    private void watchFree(Player player, Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        if (loc.getY() < loc.getWorld().getMinHeight() - 8) {
            recoverFree(player, "void-floor");
            return;
        }
        if (player.isOnGround() && BotLocations.isSolidStand(loc)) {
            lastGround.put(player.getUniqueId(), loc.clone());
        }
    }

    public boolean recoverFree(Player player, String reason) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("testbots.safety.recover-cooldown-ms", 1500);
        Long previous = recoverAt.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            return false;
        }
        Location dest = lastGround.get(player.getUniqueId());
        if (dest == null || dest.getWorld() == null || !dest.getWorld().equals(player.getWorld())) {
            dest = player.getWorld().getSpawnLocation();
        }
        recoverAt.put(player.getUniqueId(), now);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(dest);
        plugin.getActivity().markRecovering(player, reason + " → " + BotLocations.format(dest));
        plugin.getLogger().info("Recovered " + player.getName() + " (" + reason + ") → " + BotLocations.format(dest));
        return true;
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
        Location dest;
        if (otherPad) {
            dest = BotLocations.otherAnchor(player, section, player.getLocation());
        } else {
            Location home = homes.get(player.getUniqueId());
            dest = home != null && BotLocations.isSolidStand(home) ? home.clone() : BotLocations.assignedAnchor(player, section);
        }
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
        rememberHome(player, dest);
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
