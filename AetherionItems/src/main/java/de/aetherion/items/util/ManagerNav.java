package de.aetherion.items.util;

import de.aetherion.items.AetherionItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shared bottom-left back control for menus opened from the Aetherion Manager.
 */
public final class ManagerNav {

    /** Bottom-left on a 54-slot (double chest) inventory. */
    public static final int SLOT = 45;

    /** Bottom-left on a 45-slot inventory. */
    public static final int SLOT_45 = 36;

    /** Bottom-left on a 27-slot inventory. */
    public static final int SLOT_27 = 18;

    private ManagerNav() {
    }

    public static ItemStack button() {
        return GuiItems.named(
                Material.ARROW,
                "§eBack",
                "§7Return to the Aetherion Manager."
        );
    }

    public static void openManager(Player player) {
        if (player == null) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getManager() != null) {
            plugin.getManager().open(player);
            return;
        }
        player.closeInventory();
    }

    /** Soft open for plugins that may not hard-depend on Items at compile time. */
    public static void openManagerReflect(Player player) {
        if (player == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.items.AetherionItems");
            Object plugin = type.getMethod("getInstance").invoke(null);
            if (plugin == null) {
                player.closeInventory();
                return;
            }
            Object manager = plugin.getClass().getMethod("getManager").invoke(plugin);
            if (manager == null) {
                player.closeInventory();
                return;
            }
            manager.getClass().getMethod("open", Player.class).invoke(manager, player);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            player.closeInventory();
        }
    }
}
