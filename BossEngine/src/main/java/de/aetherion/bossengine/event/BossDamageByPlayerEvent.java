package de.aetherion.bossengine.event;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player contributes damage to a boss. Carries the running share map.
 */
public class BossDamageByPlayerEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BossInstance instance;
    private final Player damager;
    private final double damage;
    private final double totalDamage;
    private final double sharePercent;
    private boolean cancelled;

    public BossDamageByPlayerEvent(
            BossInstance instance,
            Player damager,
            double damage,
            double totalDamage,
            double sharePercent
    ) {
        this.instance = instance;
        this.damager = damager;
        this.damage = damage;
        this.totalDamage = totalDamage;
        this.sharePercent = sharePercent;
    }

    public BossInstance getInstance() {
        return instance;
    }

    public Player getDamager() {
        return damager;
    }

    public double getDamage() {
        return damage;
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public double getSharePercent() {
        return sharePercent;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
