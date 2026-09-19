package de.aetherion.items.util;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.ValueLore;
import de.aetherion.items.listener.AutoPickupListener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class InventoryDrops {

    /** Optional block/break origin for ground drops (ThreadLocal for nested give calls). */
    private static final ThreadLocal<Location> DROP_AT = new ThreadLocal<>();

    private InventoryDrops() {
    }

    /** Pin ground drops to a block location until {@link #clearDropAt()}. */
    public static void setDropAt(Location location) {
        if (location == null) {
            DROP_AT.remove();
            return;
        }
        DROP_AT.set(location.clone());
    }

    public static void clearDropAt() {
        DROP_AT.remove();
    }

    public static void give(Player player, ItemStack stack) {
        give(player, stack, null);
    }

    /** Prefer explicit break location; falls back to {@link #setDropAt} / player feet. */
    public static void give(Player player, ItemStack stack, Location at) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getItemValues() != null) {
            ValueLore.apply(stack, plugin.getItemValues());
        }
        if (player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            de.aetherion.items.world.NetherRealm.markMined(stack);
        }

        // Direct-to-inventory is the same privilege as Auto-Pickup (Aetherion Level 5+).
        // Below that, drops go at the broken block — not under the player's feet.
        if (player.getGameMode() != GameMode.CREATIVE && !AutoPickupListener.unlocked(player)) {
            dropOwned(player, stack, at);
            if (plugin != null && plugin.recipeUnlocks() != null) {
                plugin.recipeUnlocks().noteObtained(player, stack);
            }
            return;
        }

        Material type = stack.getType();
        int amount = Math.max(1, stack.getAmount());
        var leftover = player.getInventory().addItem(stack);
        int kept = amount;
        for (ItemStack left : leftover.values()) {
            if (left != null && !left.getType().isAir()) {
                kept -= left.getAmount();
                dropOwned(player, left, at);
            }
        }
        if (kept > 0) {
            QuestProgressHook.noteCollected(player, type, kept);
            if (plugin != null && plugin.recipeUnlocks() != null) {
                plugin.recipeUnlocks().noteObtained(player, type);
            }
        } else {
            try {
                Class.forName("de.aetherion.quests.bridge.QuestProgressBridge")
                        .getMethod("noteInventoryGain", Player.class)
                        .invoke(null, player);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dropOwned(Player player, ItemStack stack, Location explicit) {
        Location pinned = explicit != null ? explicit : DROP_AT.get();
        Location at;
        if (pinned != null && pinned.getWorld() != null) {
            at = pinned.clone();
            // Block corner → center of the broken block.
            if (Math.abs(at.getX() - Math.floor(at.getX())) < 1.0e-6
                    && Math.abs(at.getY() - Math.floor(at.getY())) < 1.0e-6
                    && Math.abs(at.getZ() - Math.floor(at.getZ())) < 1.0e-6) {
                at.add(0.5, 0.25, 0.5);
            }
        } else {
            // Last resort only — should be rare once break sites are pinned.
            at = player.getLocation().add(0, 0.2, 0);
        }
        if (at.getWorld() == null) {
            return;
        }
        Item dropped = at.getWorld().dropItem(at, stack);
        dropped.teleport(at);
        dropped.setVelocity(new Vector(0, 0.08, 0));
        dropped.setPickupDelay(20);
        dropped.setOwner(player.getUniqueId());
        dropped.setThrower(player.getUniqueId());
    }
}
