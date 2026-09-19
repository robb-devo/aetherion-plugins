package de.aetherion.items.blueprint;

import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Eldervale forge — put blueprint tool + upgrade stone, click apply.
 */
public final class BlueprintForgeGUI implements InventoryHolder {

    public static final int TOOL_SLOT = 11;
    public static final int STONE_SLOT = 15;
    public static final int APPLY_SLOT = 13;
    public static final int SIZE = 27;

    private final Inventory inventory;
    private final ItemManager itemManager;
    private boolean skipReturn;

    public BlueprintForgeGUI(ItemManager itemManager) {
        this.itemManager = itemManager;
        this.inventory = Bukkit.createInventory(this, SIZE, "§bEldervale Blueprint Forge");
        paint();
    }

    public static void open(Player player, ItemManager itemManager) {
        if (player == null || itemManager == null) {
            return;
        }
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin != null && plugin.getBlueprintForgeRitual() != null) {
            if (plugin.getBlueprintForgeRitual().isBusy()
                    || plugin.getBlueprintForgeRitual().isForging(player.getUniqueId())) {
                player.sendMessage("§cForge is busy — wait for the current strike.");
                return;
            }
        }
        player.openInventory(new BlueprintForgeGUI(itemManager).getInventory());
    }

    private void paint() {
        ItemStack glass = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass);
        }
        inventory.setItem(TOOL_SLOT, null);
        inventory.setItem(STONE_SLOT, null);
        inventory.setItem(APPLY_SLOT, button(
                Material.ANVIL,
                "§aApply Upgrade",
                "§7Tool left · Stone right.",
                "§7Stone II/III/IV → Tier 2/3/4.",
                "§8Forgehand strikes the frame."
        ));
        inventory.setItem(4, button(
                Material.DEEPSLATE_DIAMOND_ORE,
                "§bBlueprint Tiers",
                "§7Start at Tier I. Three upgrades to IV.",
                "§7T4 is strong — stones are expensive.",
                "§7Forge times: §f10s / 20s / 30s"
        ));
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemManager itemManager() {
        return itemManager;
    }

    public void markConsumed() {
        skipReturn = true;
    }

    public boolean skipReturn() {
        return skipReturn;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
