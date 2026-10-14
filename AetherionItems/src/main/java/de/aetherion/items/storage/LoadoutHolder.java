package de.aetherion.items.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Routes Loadouts by holder. Title is display-only chrome. */
public final class LoadoutHolder implements InventoryHolder {

    private Inventory inventory;

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
