package de.aetherion.items.farm;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.util.GuiItems;
import de.aetherion.items.util.ManagerNav;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Category shelf — list offers, show costs, click to craft. */
public final class RootCellarCraftGUI implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int BACK_SLOT = ManagerNav.SLOT;
    /** Roomy inner grid — gaps between offers, free rim. */
    public static final int[] OFFER_SLOTS = {
            20, 22, 24,
            30, 32
    };

    private final Inventory inventory;
    private final RootCellarCrafts.Category category;

    public RootCellarCraftGUI(Player viewer, RootCellarCrafts.Category category) {
        this.category = category;
        String title = category == RootCellarCrafts.Category.CELLAR
                ? "§8Cellar Crafts"
                : "§8Pantry Crafts";
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        paint(viewer);
    }

    public static void open(Player player, RootCellarCrafts.Category category) {
        if (player == null || category == null) {
            return;
        }
        player.openInventory(new RootCellarCraftGUI(player, category).getInventory());
    }

    public RootCellarCrafts.Category category() {
        return category;
    }

    private void paint(Player viewer) {
        ItemStack glass = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass.clone());
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ItemManager manager = plugin == null ? null : plugin.getItemManager();

        if (category == RootCellarCrafts.Category.CELLAR) {
            inventory.setItem(4, GuiItems.named(
                    Material.HAY_BLOCK,
                    "§eCellar Crafts",
                    "§7Click a refined crop to mill it.",
                    "§7Needs Compacted of the same crop."
            ));
        } else {
            inventory.setItem(4, GuiItems.named(
                    Material.EXPERIENCE_BOTTLE,
                    "§ePantry Crafts",
                    "§7Click an item to craft.",
                    "§7Costs come from your inventory."
            ));
        }

        List<RootCellarCrafts.Offer> offers = RootCellarCrafts.of(category);
        for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
            inventory.setItem(OFFER_SLOTS[i], offerButton(offers.get(i), viewer, manager));
        }
        inventory.setItem(BACK_SLOT, GuiItems.named(
                Material.ARROW,
                "§eBack",
                "§7Root Cellar."
        ));
    }

    private static ItemStack offerButton(RootCellarCrafts.Offer offer, Player viewer, ItemManager manager) {
        ItemStack icon = offer.result().get();
        if (icon == null) {
            icon = new ItemStack(Material.BARRIER);
        } else {
            icon = icon.clone();
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(offer.title());
            List<String> lore = new ArrayList<>(offer.description());
            lore.add("");
            lore.add("§7Needs:");
            lore.addAll(RootCellarCrafts.missingLines(viewer, offer, manager));
            lore.add("");
            if (RootCellarCrafts.canAfford(viewer, offer, manager)) {
                lore.add("§aClick to craft");
            } else {
                lore.add("§cMissing materials");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    ItemKeys.devAction(),
                    PersistentDataType.STRING,
                    "rootcraft:" + offer.id()
            );
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
