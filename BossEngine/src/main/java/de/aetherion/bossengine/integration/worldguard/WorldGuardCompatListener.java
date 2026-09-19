package de.aetherion.bossengine.integration.worldguard;

import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.DestroyEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.bukkit.event.inventory.UseItemEvent;

import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.event.Event.Result.ALLOW;

/**
 * Same pattern as AetherionMining / AetherionForaging:
 * allow only BossEngine-owned entities and spawn items, leave the rest protected.
 */
public class WorldGuardCompatListener implements Listener {

    private final BossKeys keys;

    public WorldGuardCompatListener(BossKeys keys) {
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnLowest(SpawnEntityEvent event) {
        allowSpawn(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnHighest(SpawnEntityEvent event) {
        allowSpawn(event);
    }

    private void allowSpawn(SpawnEntityEvent event) {
        if (WorldGuardSpawnGuard.isBypassing() || isOwned(event.getEntity())) {
            event.setResult(ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageLowest(DamageEntityEvent event) {
        allowOwned(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageHighest(DamageEntityEvent event) {
        allowOwned(event);
    }

    private void allowOwned(DamageEntityEvent event) {
        if (isOwned(event.getEntity())) {
            event.setResult(ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDestroyLowest(DestroyEntityEvent event) {
        allowOwned(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDestroyHighest(DestroyEntityEvent event) {
        allowOwned(event);
    }

    private void allowOwned(DestroyEntityEvent event) {
        if (isOwned(event.getEntity())) {
            event.setResult(ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUseItem(UseItemEvent event) {
        ItemStack item = event.getItemStack();
        if (item != null && keys.spawnItemId(item).isPresent()) {
            event.setResult(ALLOW);
        }
    }

    private boolean isOwned(Entity entity) {
        return keys.isBoss(entity) || keys.isMinion(entity);
    }
}
