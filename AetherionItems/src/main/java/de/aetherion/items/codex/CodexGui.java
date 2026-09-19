package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class CodexGui {

    public static final int[] CATEGORY_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    public static final int[] ENTRY_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final int PREV_SLOT = 48;
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;
    public static final int NEXT_SLOT = 53;

    private CodexGui() {
    }

    public static void fill(Inventory inventory) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    public static ItemStack named(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static List<String> leaderboardLore(long yours, List<CodexService.Rank> ranks, String verb) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Your " + verb + ": §f" + yours);
        lore.add("");
        lore.add("§6Top 3");
        if (ranks.isEmpty()) {
            lore.add("§8No records yet.");
        } else {
            for (CodexService.Rank rank : ranks) {
                String medal = switch (rank.place()) {
                    case 1 -> "§6#1";
                    case 2 -> "§7#2";
                    default -> "§c#3";
                };
                lore.add(medal + " §f" + rank.name() + " §8- §e" + rank.amount());
            }
        }
        return lore;
    }

    public static void openPaged(
            Player player,
            InventoryHolder holder,
            String title,
            Material headerIcon,
            String headerName,
            String headerLore,
            List<String> categories,
            String selected,
            List<CodexCatalog.Entry> entries,
            int page,
            CodexService service,
            boolean kills
    ) {
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        fill(inventory);

        for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
            String category = categories.get(i);
            boolean active = category.equals(selected);
            inventory.setItem(CATEGORY_SLOTS[i], categoryItem(
                    CodexCatalog.categoryIcon(category),
                    (active ? "§a" : "§e") + CodexCatalog.categoryTitle(category),
                    active ? "§7Currently viewing." : "§eClick to open"
            ));
        }

        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) ENTRY_SLOTS.length));
        int safePage = Math.min(Math.max(page, 1), pages);
        int start = (safePage - 1) * ENTRY_SLOTS.length;
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                inventory.setItem(ENTRY_SLOTS[i], named(Material.BLACK_STAINED_GLASS_PANE, " "));
                continue;
            }
            CodexCatalog.Entry entry = entries.get(index);
            long yours = kills ? service.kills(player, entry.id()) : service.blocks(player, entry.id());
            List<CodexService.Rank> ranks = kills ? service.topKills(entry.id()) : service.topBlocks(entry.id());
            List<String> lore = leaderboardLore(yours, ranks, kills ? "kills" : "mined");
            inventory.setItem(ENTRY_SLOTS[i], named(entry.icon(), "§f" + entry.name(), lore));
        }

        if (safePage > 1) {
            inventory.setItem(PREV_SLOT, named(Material.ARROW, "§ePrevious", "§7Page " + (safePage - 1)));
        }
        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        if (safePage < pages) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, "§eNext", "§7Page " + (safePage + 1)));
        }

        player.openInventory(inventory);
    }

    private static ItemStack categoryItem(Material material, String name, String... lore) {
        ItemStack item = named(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            de.aetherion.items.util.GuiItems.hideVanilla(meta);
            item.setItemMeta(meta);
        }
        return item;
    }
}
