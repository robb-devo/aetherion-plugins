package net.aetherion.samuraiboss.listener;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.util.Keys;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;

/**
 * Verbindet Vanilla-Ereignisse mit dem Kampf:
 * <ul>
 *   <li>Treffer auf die unsichtbare Hitbox → virtueller Bossschaden (Vanilla-Schaden inkl.
 *       Verzauberungen, Krits, Bögen, Dreizack …). Die Hitbox selbst nimmt NIE Schaden.</li>
 *   <li>Logout → Spieler verlässt den Kampf; Tod → optional Inventar behalten</li>
 *   <li>Rechtsklick auf die Tempelglocke → Herausforderung (wenn aktiviert)</li>
 *   <li>Keine natürlichen Mob-Spawns im Dojo während eines Kampfes</li>
 * </ul>
 */
public final class FightListener implements Listener {

    private final SamuraiBossPlugin plugin;

    public FightListener(SamuraiBossPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Schaden

    /**
     * HIGHEST so AetherionItems (HIGH) can stamp custom weapon damage first.
     * Use max(raw, final) — Iron Golem armor would otherwise crush FinalDamage.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (!Keys.isOwned(victim)) return;
        e.setCancelled(true); // eigene Entities (Hitbox, Glocke …) nehmen nie Vanilla-Schaden
        if (!"hitbox".equals(Keys.category(victim))) return;
        Fight f = plugin.fights().byHitbox(victim);
        if (f == null) {
            // verwaiste Hitbox (z. B. nach einem Absturz) – weg damit
            victim.remove();
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent ed)) return;
        Player p = responsible(ed.getDamager());
        if (p == null) return;
        double incoming = Math.max(e.getDamage(), e.getFinalDamage());
        if (incoming <= 0) return;
        f.onBossHit(p, incoming, hitPoint(ed.getDamager(), victim));
    }

    /** Wer steckt hinter einem Treffer? Spieler direkt, Geschosse, TNT, Tränke, zahme Tiere. */
    private static Player responsible(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) return p;
        }
        if (damager instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (damager instanceof Tameable t && t.getOwner() instanceof Player p) return p;
        return null;
    }

    /** Ungefährer Trefferpunkt auf der Hitbox (für Funken/Partikel). */
    private static Location hitPoint(Entity damager, Entity victim) {
        BoundingBox bb = victim.getBoundingBox();
        Vector c = bb.getCenter();
        Vector from = damager instanceof Player p ? p.getEyeLocation().toVector() : damager.getLocation().toVector();
        Vector d = from.clone().subtract(c);
        d.setY(d.getY() * 0.5);
        if (d.lengthSquared() > 1e-6) d.normalize().multiply(Math.min(bb.getWidthX(), bb.getWidthZ()) * 0.5);
        Vector p = c.add(d);
        p.setY(Math.max(bb.getMinY() + 0.4, Math.min(bb.getMaxY() - 0.2, from.getY() - 0.3)));
        return p.toLocation(victim.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (Keys.isOwned(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (Keys.isOwned(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        // andere Mobs sollen die unsichtbare Hitbox nicht angreifen – und die Hitbox niemanden
        if (e.getTarget() != null && Keys.isOwned(e.getTarget())) e.setCancelled(true);
        if (Keys.isOwned(e.getEntity())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ Spieler

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = plugin.fights().byParticipant(e.getPlayer());
        if (f != null) f.leave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Fight f = plugin.fights().byParticipant(p);
        if (f == null || !f.settings().keepInventory) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!Keys.isOwned(clicked)) return;
        String id = Keys.arena(clicked);
        Arena a = id == null ? null : plugin.arenas().get(id);
        if (a == null || !a.deco.isBellEntity(clicked)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.settings().bellChallenge) {
            // nur der Klang – die Glocke ist auch Deko
            if (a.state == Arena.State.IDLE) {
                a.deco.strikeBell();
                new net.aetherion.samuraiboss.audio.Sfx(plugin.settings()).play(a.point("bell"), net.aetherion.samuraiboss.audio.Sfx.Id.BELL, 0.6f, 1f);
            }
            return;
        }
        if (!p.hasPermission("samuraiboss.challenge")) {
            p.sendMessage(plugin.msg("no-permission", "<red>Keine Berechtigung.</red>"));
            return;
        }
        String err = plugin.fights().start(a);
        if (err != null) p.sendMessage(plugin.prefixed("<gray>" + err + "</gray>"));
    }

    // ------------------------------------------------------------------ Welt

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.CUSTOM || r == CreatureSpawnEvent.SpawnReason.COMMAND
                || r == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        Arena a = plugin.arenas().at(e.getLocation());
        if (a != null && a.state != Arena.State.IDLE) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        for (Fight f : new ArrayList<>(plugin.fights().all())) {
            if (f.world() == e.getWorld()) f.abort(true);
        }
    }
}
