package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * A studio screen: items + one click action per slot.
 * <p>
 * Clicks run on the next tick (safe to open/close inventories). Re-opening a screen with the same size and
 * title updates the open inventory in place, so the cursor doesn't jump back to the centre on every edit.
 */
public final class Menu {

    @FunctionalInterface
    public interface Action {
        void run(Click click);
    }

    public record Click(Player player, ClickType type) {
        public boolean left() {
            return type == ClickType.LEFT;
        }

        public boolean right() {
            return type == ClickType.RIGHT;
        }

        public boolean shift() {
            return type.isShiftClick();
        }

        public boolean shiftLeft() {
            return type == ClickType.SHIFT_LEFT;
        }

        public boolean shiftRight() {
            return type == ClickType.SHIFT_RIGHT;
        }

        public boolean drop() {
            return type == ClickType.DROP || type == ClickType.CONTROL_DROP;
        }
    }

    private static boolean registered;

    private final int size;
    private final String title;
    private final ItemStack[] items;
    private final Action[] actions;

    private Menu(int rows, String title) {
        this.size = Math.max(1, Math.min(6, rows)) * 9;
        this.title = title == null ? "" : title;
        this.items = new ItemStack[size];
        this.actions = new Action[size];
    }

    public static Menu of(int rows, String title) {
        return new Menu(rows, title);
    }

    public int size() {
        return size;
    }

    public String title() {
        return title;
    }

    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    public Menu set(int slot, ItemStack item, Action action) {
        if (slot < 0 || slot >= size) {
            return this;
        }
        items[slot] = item;
        actions[slot] = action;
        return this;
    }

    /** Fills every still-empty slot. */
    public Menu fill(ItemStack item) {
        for (int i = 0; i < size; i++) {
            if (items[i] == null) {
                items[i] = item.clone();
            }
        }
        return this;
    }

    Action action(int slot) {
        return slot < 0 || slot >= size ? null : actions[slot];
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        NpcEditor editor = NpcEditor.get();
        if (editor != null && editor.prompting(player)) {
            // Opening any screen abandons a pending chat answer, so the next chat message isn't eaten.
            editor.cancelInput(player, false);
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Holder holder && top.getSize() == size && title.equals(holder.title)) {
            holder.menu = this;
            top.setContents(items.clone());
            return;
        }
        Holder holder = new Holder(this, title);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        inventory.setContents(items.clone());
        player.openInventory(inventory);
    }

    public static void register(Plugin plugin) {
        if (registered) {
            return;
        }
        registered = true;
        plugin.getServer().getPluginManager().registerEvents(new ClickListener(plugin), plugin);
    }

    public static final class Holder implements InventoryHolder {
        private final String title;
        private Menu menu;
        private Inventory inventory;

        private Holder(Menu menu, String title) {
            this.menu = menu;
            this.title = title;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ClickListener implements Listener {
        private final Plugin plugin;

        private ClickListener(Plugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof Holder holder)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ClickType type = event.getClick();
            if (type == ClickType.DOUBLE_CLICK || type == ClickType.NUMBER_KEY || type == ClickType.SWAP_OFFHAND
                    || type == ClickType.MIDDLE || type == ClickType.UNKNOWN || type == ClickType.CREATIVE) {
                return;
            }
            Menu menu = holder.menu;
            Action action = menu.action(event.getRawSlot());
            if (action == null) {
                return;
            }
            if (!NpcEditor.allowed(player)) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            }
            Click click = new Click(player, type);
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Skip if the screen changed or closed in between (also swallows same-tick double clicks).
                if (!player.isOnline()
                        || player.getOpenInventory().getTopInventory().getHolder() != holder
                        || holder.menu != menu) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.25f);
                try {
                    action.run(click);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "NPC Studio action failed", ex);
                    player.sendMessage("§c✖ Something went wrong — details are in the server console.");
                }
            });
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof Holder) {
                event.setCancelled(true);
            }
        }
    }
}
