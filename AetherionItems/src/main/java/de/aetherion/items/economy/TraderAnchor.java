package de.aetherion.items.economy;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the villager traders (Gear Trader, Trader, Silas, Liquidator: everything tagged {@link ItemKeys#traderNpc()})
 * on the spot they were placed. Robb saw the Gear Trader leave his stall again and again (playtest 3, R-59); what
 * moves him is not visible in the code, so the trader simply goes back.
 *
 * <p>The spot is kept on the entity ({@code aetherion:trader_home}): written the first time the trader is seen, so a
 * trader placed with his anchor item keeps the placed spot. One that already stands in the wrong place is placed again
 * with the anchor (sneak-click removes, click places).
 */
public final class TraderAnchor implements Listener {

    private static final double SLACK_SQ = 0.25 * 0.25;

    private final NamespacedKey homeKey;
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();

    public TraderAnchor(JavaPlugin plugin) {
        this.homeKey = new NamespacedKey(plugin, "trader_home");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // traders already in loaded chunks (the add event fired before this listener existed)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    if (isTrader(villager)) {
                        tracked.add(villager.getUniqueId());
                    }
                }
            }
        });
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::hold, 100L, 100L);
    }

    private static boolean isTrader(Entity entity) {
        return entity instanceof Villager
                && entity.getPersistentDataContainer().has(ItemKeys.traderNpc(), PersistentDataType.STRING);
    }

    /** Only remembers the id: the add event is no place to move or edit an entity. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (isTrader(event.getEntity())) {
            tracked.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        tracked.remove(event.getEntity().getUniqueId());
    }

    private void hold() {
        for (UUID id : List.copyOf(tracked)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid()) {
                tracked.remove(id);
                continue;
            }
            if (!entity.getPersistentDataContainer().has(homeKey, PersistentDataType.STRING)) {
                entity.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, write(entity.getLocation()));
                continue;
            }
            Location home = home(entity);
            if (home == null) {
                continue;
            }
            Location now = entity.getLocation();
            if (!now.getWorld().equals(home.getWorld()) || now.distanceSquared(home) > SLACK_SQ) {
                entity.teleport(home);
                entity.setVelocity(new org.bukkit.util.Vector());
            }
        }
    }

    private Location home(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(homeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        String[] p = raw.split(";");
        if (p.length < 5) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), 0f);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String write(Location at) {
        return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.1f",
                at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw());
    }
}
