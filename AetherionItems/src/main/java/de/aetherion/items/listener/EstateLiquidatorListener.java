package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class EstateLiquidatorListener implements Listener {

    private static final Map<Material, Integer> PAYOUT = new EnumMap<>(Material.class);

    static {
        pay(1, Material.ROTTEN_FLESH, Material.EGG, Material.TURTLE_EGG);
        pay(2, Material.BONE, Material.STRING, Material.FEATHER, Material.INK_SAC, Material.LEATHER,
                Material.RABBIT_HIDE, Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON,
                Material.RABBIT, Material.COD, Material.SALMON);
        pay(3, Material.SPIDER_EYE, Material.SLIME_BALL, Material.GLOW_INK_SAC, Material.COOKED_BEEF,
                Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_RABBIT,
                Material.COOKED_COD, Material.COOKED_SALMON, Material.WHITE_WOOL);
        pay(4, Material.GUNPOWDER, Material.BONE_MEAL);
        pay(3, Material.MAGMA_CREAM);
        pay(6, Material.PHANTOM_MEMBRANE, Material.GHAST_TEAR, Material.BLAZE_ROD);
        pay(8, Material.RABBIT_FOOT, Material.ENDER_PEARL, Material.SADDLE);
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.endsWith("_WOOL") && !PAYOUT.containsKey(material)) {
                PAYOUT.put(material, 2);
            }
        }
    }

    private final ItemManager items;
    private final CoinService coins;

    public EstateLiquidatorListener(AetherionItems plugin, ItemManager items, CoinService coins) {
        this.items = items;
        this.coins = coins;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::stopSpyglass, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpyglass(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        ItemStack offhand = inventory.getItemInOffHand();
        boolean estateUse = isEstate(event.getItem());
        boolean spyglassEstateOffhand = isEstate(offhand) && offhand.getType() == Material.SPYGLASS;
        if (!estateUse && !spyglassEstateOffhand) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        retireSpyglass(event.getItem());
        retireSpyglass(offhand);
    }

    private void stopSpyglass() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack main = inventory.getItemInMainHand();
            ItemStack off = inventory.getItemInOffHand();
            retireSpyglass(main);
            retireSpyglass(off);
            if (!isEstate(main) && !isEstate(off)) {
                continue;
            }
            ItemStack using = player.getActiveItem();
            if (using != null && (using.getType() == Material.SPYGLASS || isEstate(using))) {
                player.clearActiveItem();
            }
        }
    }

    private void retireSpyglass(ItemStack item) {
        if (isEstate(item) && item.getType() == Material.SPYGLASS) {
            item.setType(Material.CLOCK);
        }
    }

    private boolean isEstate(ItemStack item) {
        return item != null && AccessoryItems.ESTATE_ID.equalsIgnoreCase(items.getItemId(item));
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            if (!AccessoryItems.holdingOffhand(player, AccessoryItems.ESTATE_ID)) {
                continue;
            }
            liquidate(player);
        }
    }

    private void liquidate(Player player) {
        PlayerInventory inventory = player.getInventory();
        int sold = 0;
        long coinsGained = 0L;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (items.getItemId(stack) != null) {
                continue;
            }
            Integer price = PAYOUT.get(stack.getType());
            if (price == null || price <= 0) {
                continue;
            }
            int amount = stack.getAmount();
            coinsGained += (long) price * amount;
            sold += amount;
            inventory.setItem(slot, null);
        }
        if (sold <= 0 || coins == null) {
            return;
        }
        coins.add(player, coinsGained);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6Estate Liquidator §8· §e+" + String.format(Locale.US, "%,d", coinsGained)
                        + " coins §7· " + sold + " peasant souvenirs"
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.4f, 1.6f);
    }

    private static void pay(int coins, Material... materials) {
        for (Material material : materials) {
            PAYOUT.put(material, coins);
        }
    }
}
