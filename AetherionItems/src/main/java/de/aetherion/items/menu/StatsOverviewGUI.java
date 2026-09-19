package de.aetherion.items.menu;

import de.aetherion.items.item.ItemLore;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class StatsOverviewGUI {

    public static final String TITLE = "§8Aetherion Stats";
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;

    private final ActiveEquipmentStats equipmentStats;

    public StatsOverviewGUI(ItemManager itemManager) {
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }

        inventory.setItem(4, named(
                Material.EXPERIENCE_BOTTLE,
                "§bYour Active Stats",
                "§7Includes equipment, pets",
                "§7and equipped skills."
        ));

        inventory.setItem(19, stat(player, Material.DIAMOND_SWORD, "§cDamage", ItemCapability.DAMAGE, false));
        inventory.setItem(20, stat(player, Material.BIRCH_LOG, "§fCrit Chance", ItemCapability.CRIT_CHANCE, true));
        inventory.setItem(21, stat(player, Material.OAK_LOG, "§6Crit Damage", ItemCapability.CRIT_DAMAGE, true));
        inventory.setItem(22, stat(player, Material.SHIELD, "§bDefense", ItemCapability.DEFENSE, false));
        inventory.setItem(23, stat(player, Material.GOLDEN_APPLE, "§cHealth", ItemCapability.HEALTH, false));
        inventory.setItem(24, stat(player, Material.GLOWSTONE, "§eSpeed", ItemCapability.SPEED, true));
        inventory.setItem(25, stat(player, Material.LEAD, "§6Catch Rate", ItemCapability.PET_CATCH_RATE, true));

        inventory.setItem(29, stat(player, Material.IRON_PICKAXE, "§7Mining Power", ItemCapability.MINING_POWER, false));
        inventory.setItem(30, stat(player, Material.GOLD_INGOT, "§eFortune", ItemCapability.FORTUNE, false));
        inventory.setItem(31, stat(player, Material.EMERALD, "§aSpread", ItemCapability.SPREAD, false));
        inventory.setItem(32, stat(player, Material.REDSTONE, "§cAttack Spread", ItemCapability.ATTACK_SPREAD, false));
        inventory.setItem(33, stat(player, Material.GOLDEN_HOE, "§6Harvest", ItemCapability.HARVEST_SPREAD, false));

        inventory.setItem(38, stat(player, Material.FISHING_ROD, "§bFish Speed", ItemCapability.FISHING_SPEED, false));
        inventory.setItem(39, stat(player, Material.COD, "§3Fish Catch", ItemCapability.FISHING_CATCH, false));

        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());

        player.openInventory(inventory);
    }

    private ItemStack stat(
            Player player,
            Material material,
            String name,
            ItemCapability capability,
            boolean percent
    ) {
        double value = equipmentStats.getStat(player, capability);
        String amount = "§f+" + ItemLore.formatStat(value) + (percent ? "%" : "");
        return named(material, name, "", "§7Current: " + amount);
    }

    private ItemStack named(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
