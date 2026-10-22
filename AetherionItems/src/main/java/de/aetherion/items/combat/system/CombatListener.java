package de.aetherion.items.combat.system;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Event wiring for the kit. Registered at the end of {@code AetherionItems#onEnable}, so at equal priority
 * it runs after DamageListener (HIGH outgoing overwrite, HIGHEST defense).
 * <pre>
 *   LOW      incoming player damage  parry / roll cancels and which hit the guard caught (before BossHits)
 *   HIGH     incoming player damage  guard cut, after WildlifeLooks / OreTroll rewrite the number
 *   HIGH     outgoing player melee   light / heavy / finisher / riposte scaling of the DAMAGE stamp
 *   HIGHEST  dummy hits              never lethal either way
 *   MONITOR  damage                  commit combo / riposte / strike cues once the hit stood,
 *                                    combat flag, heavy-hit cue, dummy readout
 * </pre>
 */
final class CombatListener implements Listener {

    private final CombatSystem system;

    CombatListener(CombatSystem system) {
        this.system = system;
    }

    /* ---------------------------------------------------------------- inputs */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        if (system.guard().press(player, Bukkit.getCurrentTick())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            system.mobility().sneakPressed(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            system.mobility().sprintStopped(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        system.mobility().jumped(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        system.cadence().snapshot(event.getPlayer(), event.getAttacked());
    }

    /* ---------------------------------------------------------------- damage */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIncoming(EntityDamageEvent event) {
        system.guard().onIncoming(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardCut(EntityDamageEvent event) {
        system.guard().applyCut(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        system.cadence().apply(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDummyClamp(EntityDamageEvent event) {
        system.dummies().clampLethal(event);
    }

    /** Sees cancelled hits too: pending markers are dropped either way, the strike commits only if it stood. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSettle(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            system.guard().settle(player, event);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player) {
            system.cadence().commit(byEntity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player player) {
            if (event instanceof EntityDamageByEntityEvent && event.getFinalDamage() > 0.0 && system.active(player)) {
                system.hurt(player, Bukkit.getCurrentTick());
                system.feedback().heavyTaken(player, event.getFinalDamage() / system.maxHealth(player));
            }
            return;
        }
        if (system.dummies().isDummy(victim)) {
            system.dummies().recordHit(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        system.dummies().preventDeath(event);
    }

    /* ---------------------------------------------------------------- lifecycle */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        system.forget(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        system.tempo().clear(event.getPlayer());
        system.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            system.guard().repair(entity);
        }
    }
}
