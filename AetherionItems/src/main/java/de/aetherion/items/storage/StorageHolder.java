package de.aetherion.items.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes Storage by holder (page lives here). Title is display-only chrome.
 */
public final class StorageHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    public StorageHolder(int page) {
        this.page = Math.max(1, page);
    }

    public int getPage() {
        return page;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
