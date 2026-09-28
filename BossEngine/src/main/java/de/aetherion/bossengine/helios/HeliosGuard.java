package de.aetherion.bossengine.helios;

import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.encounter.Participants;
import de.aetherion.bossengine.helios.world.HeliosWorld;
import de.aetherion.bossengine.util.TextUtil;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps an instance honest and keeps people safe:
 * no pearls, chorus, elytra or flight, no block edits, cutscene invulnerability, void rescue hooks,
 * echoes on death, a clean exit on quit or any foreign teleport, and a guaranteed trip home on the
 * next login after a crash. Staff with {@code helios.bypass} are exempt from the movement rules.
 */
public final class HeliosGuard implements Listener {

    public static final String BYPASS = "helios.bypass";

    private final HeliosModule module;

    public HeliosGuard(HeliosModule module) {
        this.module = module;
    }

    private boolean inWorld(Player p) {
        return module.world() != null && p.getWorld() == module.world();
    }

    private boolean exempt(Player p) {
        return p.hasPermission(BYPASS) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    /* ------------------------------------------------------------------ movement exploits */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (module.internalTeleport(p)) {
            return;
        }
        HeliosEncounter e = module.encounterOf(p);
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        // CHORUS_FRUIT (1.21.1) was renamed CONSUMABLE_EFFECT (1.21.2+): match by name for both.
        if (inWorld(p) && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || "CHORUS_FRUIT".equals(cause.name()) || "CONSUMABLE_EFFECT".equals(cause.name()))) {
            if (!exempt(p)) {
                event.setCancelled(true);
                p.sendActionBar(TextUtil.component("&8The star holds you in place."));
            }
            return;
        }
        if (e == null) {
            return;
        }
        Location to = event.getTo();
        if (cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            // Echoes may not ride other people's cameras out of the arena.
            if (to == null || !e.contains(to)) {
                event.setCancelled(true);
            }
            return;
        }
        if (to == null || !e.contains(to)) {
            // Left through some other plugin (/spawn, /home, /hub…): that is leaving the fight.
            module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> e.leave(p, false));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!inWorld(p) || exempt(p)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Material m = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (m == Material.ENDER_PEARL || m == Material.CHORUS_FRUIT || m == Material.WIND_CHARGE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player p && event.isGliding() && inWorld(p) && !exempt(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (event.isFlying() && inWorld(p) && !exempt(p)) {
            event.setCancelled(true);
            p.setAllowFlight(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inWorld(event.getPlayer()) && !event.getPlayer().hasPermission(BYPASS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inWorld(event.getPlayer()) && !event.getPlayer().hasPermission(BYPASS)) {
            event.setCancelled(true);
        }
    }

    /** No natural mobs in the instance world: only plugin / command / egg spawns. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!heliosWorld(module, event.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        event.setCancelled(true);
    }

    /* ------------------------------------------------------------------ damage */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !inWorld(p)) {
            return;
        }
        HeliosEncounter e = module.encounterOf(p);
        if (e == null) {
            return;
        }
        if (e.cinematic()) {
            // Cutscenes are never lethal.
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.FALL && p.getLocation().getY() < e.center().getY() - 3) {
            // The void rescue handles falling; never double-punish.
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------------------ death, respawn, echoes */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        HeliosEncounter e = module.encounterOf(p);
        if (e != null) {
            e.died(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        HeliosEncounter e = module.encounterOf(p);
        if (e == null) {
            return;
        }
        Location to = e.respawn(p);
        if (to != null) {
            event.setRespawnLocation(to);
        }
    }

    /* ------------------------------------------------------------------ quit / join */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        HeliosEncounter e = module.encounterOf(p);
        if (e != null) {
            // Home now, while the player object is still valid: the next login starts clean.
            e.leave(p, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> recoverPlayer(module, p), 2L);
        module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> {
            if (p.isOnline() && de.aetherion.bossengine.helios.reward.PendingRewards.deliver(p) > 0) {
                p.sendMessage(TextUtil.component("&6✦ &7Your starseed from Helios has been delivered."));
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        HeliosEncounter e = module.encounterOf(p);
        if (e != null && p.getWorld() != module.world()) {
            e.leave(p, false);
        }
    }

    /** Anyone still marked as "in Helios" without a live instance is sent home. */
    public static void recoverPlayer(HeliosModule module, Player p) {
        if (p == null || !p.isOnline() || module.encounterOf(p) != null) {
            return;
        }
        boolean marked = Participants.hasRecord(p);
        boolean stranded = module.world() != null && p.getWorld() == module.world() && !p.hasPermission(BYPASS);
        if (!marked && !stranded) {
            return;
        }
        module.teleport(p, fallback(module, p));
        Participants.sendHome(p, null, module.fallbackReturn(), true);
        p.sendMessage(TextUtil.component("&6✦ &7The star brought you back."));
    }

    private static Location fallback(HeliosModule module, Player p) {
        Location l = module.fallbackReturn();
        return l == null ? p.getLocation() : l;
    }

    /* ------------------------------------------------------------------ props (mirror clones, reliquary) */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackProp(PrePlayerAttackEntityEvent event) {
        Player p = event.getPlayer();
        if (!inWorld(p)) {
            return;
        }
        HeliosEncounter e = module.encounterOf(p);
        if (e != null && e.currentScript() instanceof PropAware aware && aware.onPropHit(p, event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseProp(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (!inWorld(p)) {
            return;
        }
        HeliosEncounter e = module.encounterOf(p);
        if (e != null && e.claim(p, event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /** Scripts that react to players hitting non-living props (Interaction hitboxes). */
    public interface PropAware {
        boolean onPropHit(Player player, org.bukkit.entity.Entity prop);
    }

    static boolean heliosWorld(HeliosModule module, org.bukkit.World w) {
        return HeliosWorld.isHelios(w, module.config());
    }
}
