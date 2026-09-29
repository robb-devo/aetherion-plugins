package de.aetherion.stressbots;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.PetAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.role.BotPlaystyle;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Overnight restock so trade/catch/farm bots keep listing, throwing, and harvesting for 2–3h.
 */
public final class BotUpkeep implements Runnable {

    private final AetherionStressBots plugin;

    public BotUpkeep(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
            if (handler == null) {
                continue;
            }
            BotRole role = handler.role();
            if (role == BotRole.TRADE) {
                restockTrade(player);
                topUpTradeCoins(player);
            } else if (role == BotRole.CATCH) {
                restockSpheres(player);
            } else if (role == BotRole.FARM) {
                ensureHoe(player);
            }
        }
    }

    private void restockTrade(Player player) {
        PlayerInventory inv = player.getInventory();
        ensureStack(inv, Material.COAL, 16);
        ensureStack(inv, Material.COBBLESTONE, 16);
        ensureStack(inv, Material.OAK_LOG, 8);
        ensureStack(inv, Material.WHEAT, 8);
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin != null && itemsPlugin.getCustomItem() != null && !hasSpareTool(inv)) {
            CustomItem items = itemsPlugin.getCustomItem();
            inv.addItem(items.createMiningPickaxe());
            plugin.getActivity().markAction(player, "upkeep spare pick");
        }
    }

    private void topUpTradeCoins(Player player) {
        CoinAccess coins = AetherServices.coins();
        if (coins == null) {
            return;
        }
        long want = plugin.getConfig().getLong("testbots.playstyle.starter-coins", 2500L);
        long have = coins.get(player);
        if (have >= 400L) {
            return;
        }
        long add = Math.max(400L, want / 4);
        coins.add(player, add);
        plugin.getActivity().economy().grantStarter(player, add);
        plugin.getActivity().markAction(player, "upkeep coins +" + add);
    }

    private void restockSpheres(Player player) {
        PetAccess pets = AetherServices.pets();
        if (pets == null) {
            return;
        }
        if (hasSphere(player.getInventory())) {
            return;
        }
        ItemStack sphere = pets.catchSphere("common");
        if (sphere == null) {
            return;
        }
        sphere.setAmount(16);
        player.getInventory().addItem(sphere);
        plugin.getActivity().markAction(player, "upkeep spheres");
    }

    private void ensureHoe(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType().name().contains("HOE")) {
            return;
        }
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.getCustomItem() == null) {
            return;
        }
        player.getInventory().setItemInMainHand(
                itemsPlugin.getCustomItem().farming().hoe(BotPlaystyle.gearTier(player)));
        plugin.getActivity().markAction(player, "upkeep hoe");
    }

    private static void ensureStack(PlayerInventory inv, Material material, int min) {
        int have = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == material) {
                have += stack.getAmount();
            }
        }
        if (have >= min) {
            return;
        }
        inv.addItem(new ItemStack(material, min - have));
    }

    private static boolean hasSpareTool(PlayerInventory inv) {
        int tools = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null) {
                continue;
            }
            String name = stack.getType().name();
            if (name.contains("SWORD") || name.contains("PICKAXE") || name.contains("AXE")) {
                tools++;
            }
        }
        return tools >= 2;
    }

    private static boolean hasSphere(PlayerInventory inv) {
        for (ItemStack stack : inv.getContents()) {
            if (stack == null) {
                continue;
            }
            String name = stack.getType().name();
            if (name.contains("SNOWBALL") || name.contains("ENDER_PEARL")
                    || name.contains("HEART_OF_THE_SEA") || name.contains("NETHER_STAR")
                    || name.contains("NUGGET")) {
                return true;
            }
        }
        return false;
    }
}
