package de.aetherion.quests.reward;


import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


public class StarterGearReward {


    /*
     * =========================================================
     * STARTER GEAR VERGEBEN
     * =========================================================
     *
     * Gibt das komplette Simple Gear aus AetherionItems.
     *
     * Harbour onboarding splits axe (Forager) from the rest (Egon).
     * =========================================================
     */

    public static void giveStarterGear(Player player) {
        giveStarterGear(player, true);
    }


    /**
     * Full simple kit without the axe — axe comes from the Forager first.
     */
    public static void giveStarterGearWithoutAxe(Player player) {
        giveStarterGear(player, false);
    }


    public static void giveSimpleAxe(Player player) {
        if (player == null) {
            return;
        }
        CustomItem customItem = customItem();
        if (customItem == null) {
            player.sendMessage("§cAetherionItems not found.");
            return;
        }
        give(player, customItem.createSimpleAxe());
        player.sendMessage("§aReceived: §fSimple Axe");
    }


    public static void giveSimplePickaxe(Player player) {
        if (player == null) {
            return;
        }
        CustomItem customItem = customItem();
        if (customItem == null) {
            player.sendMessage("§cAetherionItems not found.");
            return;
        }
        give(player, customItem.createSimplePickaxe());
        player.sendMessage("§aReceived: §fSimple Pickaxe");
    }


    private static void giveStarterGear(Player player, boolean includeAxe) {
        if (player == null) {
            return;
        }

        CustomItem customItem = customItem();
        if (customItem == null) {
            player.sendMessage("§cAetherionItems not found.");
            return;
        }

        give(player, customItem.createSimplePickaxe());
        if (includeAxe) {
            give(player, customItem.createSimpleAxe());
        }
        give(player, customItem.createSimpleSword());
        give(player, customItem.createSimpleHoe());
        give(player, customItem.createSimpleHelmet());
        give(player, customItem.createSimpleChestplate());
        give(player, customItem.createSimpleLeggings());
        give(player, customItem.createSimpleBoots());
    }


    private static CustomItem customItem() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            return null;
        }
        return new CustomItem(items.getItemManager());
    }


    private static void give(Player player, ItemStack stack) {
        if (stack != null) {
            player.getInventory().addItem(stack);
        }
    }

}
