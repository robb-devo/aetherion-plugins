package de.aetherion.dungeons.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class DungeonMenu {

    public static final String TITLE = "§5Aetherion Dungeons";
    public static final int ENTER_SLOT = 20;
    public static final int LOCKED_SLOT = 22;
    public static final int FLOOR3_SLOT = 24;
    public static final int CLOSE_SLOT = 49;

    private DungeonMenu() {
    }

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }

        inventory.setItem(4, named(
                Material.END_PORTAL_FRAME,
                "§5Dungeon Keeper",
                "§7Temporary instances. The world is",
                "§7deleted when you leave or finish."
        ));

        inventory.setItem(LOCKED_SLOT, named(
                Material.PACKED_ICE,
                "§bFloor 2 · Frostbound",
                "§7Schematic dungeon. Clearance bar.",
                "§7Kill trash → red gate → snowman.",
                "§7Caches can drop a Dungeon Core II.",
                "",
                remoteHint(),
                "§cOnly 1 dungeon run at a time.",
                "§eParty leader starts. Max 4.",
                "§eClick to enter"
        ));

        inventory.setItem(ENTER_SLOT, named(
                Material.DEEPSLATE_BRICKS,
                "§dFloor 1 · Prison",
                "§7Talk to the Gate Warden.",
                "§7Safe lobby, then branching halls.",
                "§7Glass gates. Prison pieces.",
                "§7One loot cache. Sentinel at the end.",
                "§7Caches can drop a Dungeon Core I.",
                "§7Slot 9 is a live dungeon map.",
                "§860s close after the boss dies.",
                "",
                remoteHint(),
                "§cOnly 1 dungeon run at a time.",
                "§eParty leader starts. Max 4.",
                "§8Extra players scale the mobs.",
                "§eClick to enter"
        ));

        inventory.setItem(FLOOR3_SLOT, named(
                Material.END_STONE_BRICKS,
                "§5Floor 3 · Aetherion",
                "§7End-themed. Big floor. Fat arena.",
                "§7Aetherion is the boss. Bring friends.",
                "§7Core III. Fat loot. Tiny chance at",
                "§5the actual Aetherion set.",
                "§8Not a souvenir. Not easy.",
                "",
                remoteHint(),
                "§cOnly 1 dungeon run at a time.",
                "§eParty leader starts. Max 4.",
                "§eClick to enter"
        ));

        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose", "§7Close this menu."));
        player.openInventory(inventory);
    }

    private static String remoteHint() {
        try {
            var plugin = de.aetherion.dungeons.AetherionDungeons.getInstance();
            if (plugin != null && plugin.getRemote() != null && plugin.getRemote().remoteDungeonsEnabled()) {
                return "§dTransfers to mmo-d (dungeon hub).";
            }
        } catch (Throwable ignored) {
        }
        return "§8Runs on this server.";
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
