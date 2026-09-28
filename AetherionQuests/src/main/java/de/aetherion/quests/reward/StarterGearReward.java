package de.aetherion.quests.reward;


import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.quests.lang.LangPack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class StarterGearReward {


    /*
     * =========================================================
     * STARTER GEAR VERGEBEN
     * =========================================================
     *
     * Gibt das komplette Simple Gear aus AetherionItems.
     *
     * Harbour onboarding splits axe (Forager) from the rest (Egon).
     *
     * Items are always built here via CustomItem (StarterSetBalance
     * revision included). StarterKitCeremony only stages the handoff
     * and grants each piece as it lands — or instantly if it can't run.
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
        giveSimpleAxe(player, null);
    }


    /**
     * @param giverNpcId NPC whose hands the axe comes from (handoff scene), or null for a plain grant
     */
    public static void giveSimpleAxe(Player player, String giverNpcId) {
        if (player == null) {
            return;
        }
        CustomItem customItem = customItem();
        if (customItem == null) {
            player.sendMessage("§cAetherionItems not found.");
            return;
        }
        ItemStack axe = customItem.createSimpleAxe();
        if (axe == null) {
            return;
        }
        player.sendMessage("§aReceived: §fSimple Axe");
        deliver(player, giverNpcId, List.of(axe), StarterKitCeremony.Finale.AXE);
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

        List<ItemStack> kit = new ArrayList<>(8);
        kit.add(customItem.createSimplePickaxe());
        if (includeAxe) {
            kit.add(customItem.createSimpleAxe());
        }
        kit.add(customItem.createSimpleSword());
        kit.add(customItem.createSimpleHoe());
        kit.add(customItem.createSimpleHelmet());
        kit.add(customItem.createSimpleChestplate());
        kit.add(customItem.createSimpleLeggings());
        kit.add(customItem.createSimpleBoots());
        kit.removeIf(stack -> stack == null);

        // Same line style as the other quest rewards in the turn-in block.
        player.sendMessage(LangPack.ui(
                player,
                "kit_reward_line",
                "§b✦ §3Reward: §fEgon's Starter Kit §7— pickaxe, sword, hoe, armour"
        ));
        deliver(player, "egon", kit, StarterKitCeremony.Finale.KIT);
    }


    private static void deliver(
            Player player,
            String giverNpcId,
            List<ItemStack> kit,
            StarterKitCeremony.Finale finale
    ) {
        if (giverNpcId != null && StarterKitCeremony.present(player, giverNpcId, kit, finale)) {
            return;
        }
        for (ItemStack stack : kit) {
            give(player, stack);
        }
    }


    private static CustomItem customItem() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            return null;
        }
        return new CustomItem(items.getItemManager());
    }


    /** Into the bag; a full bag drops the rest at the player's feet instead of eating it. */
    static void give(Player player, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            if (rest != null && player.getWorld() != null) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
    }

}
