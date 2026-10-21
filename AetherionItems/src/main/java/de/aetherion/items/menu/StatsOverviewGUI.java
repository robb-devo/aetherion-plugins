package de.aetherion.items.menu;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BookPage;
import de.aetherion.items.item.ItemLore;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import de.aetherion.items.util.GuiIcons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The stats page in the Manager's book language: one sealed tile per stat with its value written under it, so the
 * whole build reads at a glance. Survival and damage on top, speed and gathering in the middle, pets and fishing
 * below. Zero values are written faded.
 */
public class StatsOverviewGUI {

    public static final String TITLE = "§8Aetherion Stats";
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;

    private record Stat(int slot, Material icon, String colour, String en, String de, ItemCapability capability,
                        boolean percent) {
    }

    /** Book tiles: rows 0, 2 and 4, every other column (gen_gui_chrome.py BOOK_TILES['stats']). */
    private static final List<Stat> STATS = List.of(
            new Stat(0, Material.GLISTERING_MELON_SLICE, "§c", "Health", "Leben", ItemCapability.HEALTH, false),
            new Stat(2, Material.SHIELD, "§b", "Defense", "Abwehr", ItemCapability.DEFENSE, false),
            new Stat(4, Material.DIAMOND_SWORD, "§c", "Damage", "Schaden", ItemCapability.DAMAGE, false),
            new Stat(6, Material.GHAST_TEAR, "§f", "Crit Chance", "Krit-Chance", ItemCapability.CRIT_CHANCE, true),
            new Stat(8, Material.BLAZE_POWDER, "§6", "Crit Damage", "Krit-Schaden", ItemCapability.CRIT_DAMAGE, true),
            new Stat(18, Material.SUGAR, "§e", "Speed", "Tempo", ItemCapability.SPEED, true),
            new Stat(20, Material.REDSTONE, "§c", "Attack Spread", "Angriffsstreuung", ItemCapability.ATTACK_SPREAD, false),
            new Stat(22, Material.IRON_PICKAXE, "§7", "Mining Power", "Abbaukraft", ItemCapability.MINING_POWER, false),
            new Stat(24, Material.RABBIT_FOOT, "§e", "Fortune", "Glück", ItemCapability.FORTUNE, false),
            new Stat(26, Material.EMERALD, "§a", "Spread", "Streuung", ItemCapability.SPREAD, false),
            new Stat(36, Material.GOLDEN_HOE, "§6", "Harvest", "Ernte", ItemCapability.HARVEST_SPREAD, false),
            new Stat(38, Material.LEAD, "§6", "Catch Rate", "Fangrate", ItemCapability.PET_CATCH_RATE, true),
            new Stat(40, Material.FISHING_ROD, "§b", "Fish Speed", "Angeltempo", ItemCapability.FISHING_SPEED, false),
            new Stat(42, Material.COD, "§3", "Fish Catch", "Fang", ItemCapability.FISHING_CATCH, false)
    );

    private final ActiveEquipmentStats equipmentStats;

    public StatsOverviewGUI(ItemManager itemManager) {
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    public void open(Player player) {
        boolean german = AetherServices.german(player);
        List<BookPage.Caption> captions = new ArrayList<>();
        ItemStack[] contents = new ItemStack[54];
        ItemStack spacer = GuiIcons.spacer();
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = spacer.clone();
        }
        for (Stat stat : STATS) {
            double value = equipmentStats.getStat(player, stat.capability());
            String amount = ItemLore.formatStat(value) + (stat.percent() ? "%" : "");
            contents[stat.slot()] = de.aetherion.items.util.GuiItems.named(stat.icon(),
                    stat.colour() + (german ? stat.de() : stat.en()) + " §f" + amount,
                    german ? "§7Ausrüstung, Pet und Skills zusammen." : "§7Gear, pet and skills, added up.");
            captions.add(new BookPage.Caption(stat.slot(), amount,
                    Math.abs(value) < 0.005 ? BookPage.State.LOCKED : BookPage.State.OPEN));
        }
        contents[BACK_SLOT] = de.aetherion.items.util.ManagerNav.button();

        Inventory inventory = Bukkit.createInventory(new Holder(), 54, BookPage.title('', captions));
        inventory.setContents(contents);
        player.openInventory(inventory);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
