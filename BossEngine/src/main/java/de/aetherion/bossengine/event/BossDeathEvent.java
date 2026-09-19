package de.aetherion.bossengine.event;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fired when a boss dies. Quest / Lexikon plugins can read kill data,
 * the damager map, and mutate loot before it is granted.
 */
public class BossDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BossInstance instance;
    private final Player killer;
    private final Map<UUID, Double> damageMap;
    private final Map<UUID, Double> shareMap;
    private final Map<UUID, List<ItemStack>> lootByPlayer;
    private boolean dropLoot = true;

    public BossDeathEvent(
            BossInstance instance,
            Player killer,
            Map<UUID, Double> damageMap,
            Map<UUID, Double> shareMap,
            Map<UUID, List<ItemStack>> lootByPlayer
    ) {
        this.instance = instance;
        this.killer = killer;
        this.damageMap = Collections.unmodifiableMap(damageMap);
        this.shareMap = Collections.unmodifiableMap(shareMap);
        this.lootByPlayer = new LinkedHashMap<>();
        lootByPlayer.forEach((playerId, items) ->
                this.lootByPlayer.put(playerId, new ArrayList<>(items))
        );
    }

    public BossInstance getInstance() {
        return instance;
    }

    public Player getKiller() {
        return killer;
    }

    public Map<UUID, Double> getDamageMap() {
        return damageMap;
    }

    public Map<UUID, Double> getShareMap() {
        return shareMap;
    }

    public Map<UUID, List<ItemStack>> getLootByPlayer() {
        return lootByPlayer;
    }

    public List<ItemStack> getLoot() {
        List<ItemStack> all = new ArrayList<>();
        lootByPlayer.values().forEach(all::addAll);
        return all;
    }

    public boolean isDropLoot() {
        return dropLoot;
    }

    public void setDropLoot(boolean dropLoot) {
        this.dropLoot = dropLoot;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
