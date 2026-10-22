package de.aetherion.quests.isle;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * An old boss left a sword standing by the Crystal Liquidator in the Capital (beta list). Every NPC is a FancyNpcs
 * packet figure, so a real display, armor stand or never-despawning item holding a sword there is a leftover.
 * They are removed when their chunk loads; after the first sweep that removes one, {@code stray-sword-swept.yml}
 * switches this off for good.
 */
public final class StraySwordSweep implements Listener {

    private static final String ANCHOR_NPC = "liquidator";
    private static final double RADIUS = 24.0d;

    private final AetherionQuests plugin;
    private final File done;

    public StraySwordSweep(AetherionQuests plugin) {
        this.plugin = plugin;
        this.done = new File(plugin.getDataFolder(), "stray-sword-swept.yml");
    }

    /** Registers the listener and sweeps what is loaded now; does nothing once a sweep has removed something. */
    public void start() {
        if (done.exists()) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Location anchor = anchor();
        if (anchor != null) {
            sweep(anchor.getWorld().getNearbyEntities(anchor, RADIUS, RADIUS, RADIUS));
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        Location anchor = anchor();
        if (anchor == null || !event.getWorld().equals(anchor.getWorld())) {
            return;
        }
        int cx = event.getChunk().getX() << 4;
        int cz = event.getChunk().getZ() << 4;
        if (Math.abs(cx + 8 - anchor.getX()) > RADIUS + 12 || Math.abs(cz + 8 - anchor.getZ()) > RADIUS + 12) {
            return;
        }
        sweep(event.getEntities());
    }

    private Location anchor() {
        if (plugin.getNpcDataStorage() == null) {
            return null;
        }
        Location at = plugin.getNpcDataStorage().getSavedLocation(ANCHOR_NPC);
        return at == null || at.getWorld() == null ? null : at;
    }

    private void sweep(java.util.Collection<Entity> entities) {
        Location anchor = anchor();
        if (anchor == null) {
            return;
        }
        World world = anchor.getWorld();
        int removed = 0;
        for (Entity entity : List.copyOf(entities)) {
            if (!entity.isValid() || !entity.getWorld().equals(world)
                    || entity.getLocation().distanceSquared(anchor) > RADIUS * RADIUS || !holdsSword(entity)) {
                continue;
            }
            Location at = entity.getLocation();
            plugin.getLogger().info("Stray sword removed: " + entity.getType() + " at " + at.getBlockX() + " "
                    + at.getBlockY() + " " + at.getBlockZ() + " (by the Crystal Liquidator)");
            entity.remove();
            removed++;
        }
        if (removed > 0) {
            try {
                done.getParentFile().mkdirs();
                java.nio.file.Files.writeString(done.toPath(), "swept: " + System.currentTimeMillis() + "\n");
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write " + done.getName() + ": " + exception.getMessage());
            }
            HandlerList.unregisterAll(this);
        }
    }

    private static boolean holdsSword(Entity entity) {
        if (entity instanceof ItemDisplay display) {
            return sword(display.getItemStack());
        }
        if (entity instanceof ArmorStand stand) {
            EntityEquipment gear = stand.getEquipment();
            return gear != null && (sword(gear.getItemInMainHand()) || sword(gear.getItemInOffHand()));
        }
        if (entity instanceof org.bukkit.entity.Mob mob && (mob.isInvisible() || !mob.hasAI())) {
            // A frozen or invisible model mob (old boss props were giants / zombies with a blade).
            EntityEquipment gear = mob.getEquipment();
            return gear != null && (sword(gear.getItemInMainHand()) || sword(gear.getItemInOffHand()));
        }
        if (entity instanceof Item item) {
            // Only display-like items (never picked up, never despawn): a sword a player just dropped stays.
            return (item.isUnlimitedLifetime() || item.getPickupDelay() > 1000) && sword(item.getItemStack());
        }
        return false;
    }

    private static boolean sword(ItemStack stack) {
        return stack != null && stack.getType().name().endsWith("_SWORD");
    }
}
