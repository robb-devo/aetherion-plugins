package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * One open Codex page. Pages keep their own state (category, page, sort, entry) and re-render in
 * place on click — no close/reopen flicker, and the cursor stays where it was.
 */
public abstract class CodexView implements InventoryHolder {

    protected Inventory inventory;

    /** Draws the page into {@link #inventory}. */
    public abstract void render(Player player);

    /** A click on the top inventory. {@code slot} is the raw slot. */
    public abstract void click(Player player, int slot, ClickType click);

    /** Inventory title for this page. */
    protected abstract String title();

    protected int size() {
        return 54;
    }

    /** Opens this page for {@code player} (creates the inventory on first open). */
    public void open(Player player) {
        if (player == null) {
            return;
        }
        inventory = Bukkit.createInventory(this, size(), title());
        render(player);
        player.openInventory(inventory);
    }

    /** Re-draws in place when this page is still the one open, else opens it. */
    public void refresh(Player player) {
        if (player != null && inventory != null
                && player.getOpenInventory().getTopInventory().getHolder() == this) {
            render(player);
            return;
        }
        open(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
