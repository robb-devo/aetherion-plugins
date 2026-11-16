package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.util.TextUtil;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps a belfry honest and keeps people safe: no pearls, chorus, elytra or flight, no block edits,
 * no hits during cutscenes, a clean exit on quit or any foreign teleport, and a guaranteed trip home
 * on the next login after a crash. It also turns clicks on the lamps and bells into the fight's verbs,
 * and clicks on a configured bell in the normal worlds into the way in.
 * Staff with {@code hush.bypass} are exempt from the movement rules.
 */
final class HushGuard implements Listener {

    static final String BYPASS = "hush.bypass";

    private final HushModule module;

    HushGuard(HushModule module) {
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
        HushEncounter e = module.encounterOf(p);
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        // CHORUS_FRUIT (1.21.1) was renamed CONSUMABLE_EFFECT (1.21.2+): match by name for both.
        if (inWorld(p) && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || "CHORUS_FRUIT".equals(cause.name()) || "CONSUMABLE_EFFECT".equals(cause.name()))) {
            if (!exempt(p)) {
                event.setCancelled(true);
            }
            return;
        }
        if (e == null) {
            return;
        }
        Location to = event.getTo();
        if (cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
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

    /* ------------------------------------------------------------------ lamps, bells, the way in */

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player p = event.getPlayer();
        Block block = event.getClickedBlock();
        if (inWorld(p)) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                Material m = event.getItem() == null ? Material.AIR : event.getItem().getType();
                if (!exempt(p) && (m == Material.ENDER_PEARL || m == Material.CHORUS_FRUIT || m == Material.WIND_CHARGE)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (block == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)) {
                return;
            }
            HushEncounter e = module.encounterOf(p);
            if (e != null) {
                e.use(p, block);
            }
            if (!p.hasPermission(BYPASS)) {
                // Items still work (potions, abilities); the belfry's blocks only answer through the fight.
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.BELL && module.isEntryBell(block)) {
            HushCommand.tell(p, module.enter(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackProp(PrePlayerAttackEntityEvent event) {
        Player p = event.getPlayer();
        if (!inWorld(p)) {
            return;
        }
        HushEncounter e = module.encounterOf(p);
        if (e != null && e.script() != null && e.script().propHit(p, event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------------------ damage */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !inWorld(p)) {
            return;
        }
        HushEncounter e = module.encounterOf(p);
        if (e == null) {
            return;
        }
        if (e.cinematic()) {
            // Cutscenes are never lethal.
            event.setCancelled(true);
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.FALL && e.arena().localY(p.getLocation()) < -3) {
            // The rescue handles falling; never double-punish.
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------------------ death, respawn */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        HushEncounter e = module.encounterOf(p);
        if (e != null) {
            e.died(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        HushEncounter e = module.encounterOf(p);
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
        HushEncounter e = module.encounterOf(p);
        if (e != null) {
            // Home now, while the player object is still valid: the next login starts clean.
            e.leave(p, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> recoverPlayer(module, p), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        HushEncounter e = module.encounterOf(p);
        if (e != null && p.getWorld() != module.world()) {
            e.leave(p, false);
        }
    }

    /** Anyone still marked as "in the belfry" without a live instance is sent home. */
    static void recoverPlayer(HushModule module, Player p) {
        if (p == null || !p.isOnline() || module.encounterOf(p) != null) {
            return;
        }
        boolean marked = HushParty.hasRecord(p);
        boolean stranded = module.world() != null && p.getWorld() == module.world() && !p.hasPermission(BYPASS);
        if (!marked && !stranded) {
            return;
        }
        HushParty.sendHome(p, null, module.fallbackReturn(), true);
        p.sendMessage(TextUtil.component(HushText.BROUGHT_BACK.of(p)));
    }
}
