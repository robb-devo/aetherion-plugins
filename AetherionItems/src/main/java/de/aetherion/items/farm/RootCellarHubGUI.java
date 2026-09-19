package de.aetherion.items.farm;

import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Root Cellar hub — two categories, roomy double-chest. */
public final class RootCellarHubGUI implements InventoryHolder {

    public static final String TITLE = "§8Root Cellar";
    public static final int SIZE = 54;
    /** Spaced across the middle row. */
    public static final int CELLAR_SLOT = 20;
    public static final int PANTRY_SLOT = 24;

    private final Inventory inventory;

    public RootCellarHubGUI() {
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
        paint();
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        player.openInventory(new RootCellarHubGUI().getInventory());
    }

    private void paint() {
        ItemStack glass = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass.clone());
        }
        inventory.setItem(4, GuiItems.named(
                Material.BARREL,
                "§fRoot Cellar",
                "§7Millstone Pantry.",
                "§7Pick a shelf."
        ));
        inventory.setItem(CELLAR_SLOT, GuiItems.named(
                Material.HAY_BLOCK,
                "§eCellar Crafts",
                "§7Refine Compacted crops",
                "§7into Refined pantry goods.",
                "",
                "§eClick to open"
        ));
        inventory.setItem(PANTRY_SLOT, GuiItems.named(
                Material.EXPERIENCE_BOTTLE,
                "§ePantry Crafts",
                "§7Catch spheres & pet treats",
                "§7from pantry goods.",
                "",
                "§eClick to open"
        ));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
