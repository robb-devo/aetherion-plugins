package de.aetherion.bossengine.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the combat kit did with a boss hit. AetherionItems negates a perfectly parried {@link BossHits#hurt} strike
 * and cancels whatever a roll's i-frames caught, by cancelling the damage event; it tells nobody. Watched here at
 * MONITOR: a cancelled strike right after the parry key (F, swap hands) is a parry, any other cancelled boss hit was
 * avoided. Ask right after the hit, in the same tick ({@link #parried}, {@link #avoided}).
 */
public final class Parries implements Listener {

    /** combat-system.yml parry.perfect-ticks (6) plus a little slack. */
    private static final int WINDOW = 8;

    private static final Map<UUID, Long> PRESSED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PARRIED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> AVOIDED = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPress(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) {
            // Sneak + F stays a plain hand swap in the combat kit.
            return;
        }
        PRESSED.put(event.getPlayer().getUniqueId(), (long) Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageEvent event) {
        if (!event.isCancelled() || !(event.getEntity() instanceof Player player) || !BossHits.isApplying(player)) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        AVOIDED.put(id, now);
        Long at = PRESSED.get(id);
        if (BossHits.isStrike(player) && at != null && now - at <= WINDOW) {
            PARRIED.put(id, now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PRESSED.remove(id);
        PARRIED.remove(id);
        AVOIDED.remove(id);
    }

    /** The boss strike this player just took (this tick) was parried. */
    public static boolean parried(Player player) {
        return now(PARRIED, player);
    }

    /** The boss hit this player just took (this tick) never landed: parried or rolled through. */
    public static boolean avoided(Player player) {
        return now(AVOIDED, player);
    }

    private static boolean now(Map<UUID, Long> map, Player player) {
        Long at = player == null ? null : map.get(player.getUniqueId());
        return at != null && at == Bukkit.getCurrentTick();
    }
}
