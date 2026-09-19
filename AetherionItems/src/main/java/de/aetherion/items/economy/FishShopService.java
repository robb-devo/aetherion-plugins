package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.CustomItem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Dock stall after Dock Pass — T1 fishing rod + armor. No raw fish (infinite buy was broken).
 */
public final class FishShopService implements Listener {

    public static final String TITLE = "§8Fishmonger";
    private static final int[] BUY_SLOTS = {10, 11, 12, 14, 15};
    private static final int CLOSE_SLOT = 22;
    private static final int INFO_SLOT = 4;

    private final CoinService coins;
    private final CustomItem items;

    public FishShopService(CoinService coins, CustomItem items) {
        this.coins = coins;
        this.items = items;
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, TITLE);
        fill(inventory);
        stock(inventory);
        inventory.setItem(INFO_SLOT, button(Material.LEATHER_CHESTPLATE, "§bDock Stock",
                "§7T1 fishing kit — rod and armor.",
                "§7No fish. Catch your own.",
                "§7Balance: §e" + format(coins.get(player)) + " coins"));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.55f, 1.15f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= top.getSize()) {
            return;
        }
        event.setCancelled(true);
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        Offer offer = offerAt(raw);
        if (offer == null) {
            return;
        }
        if (!coins.take(player, offer.price)) {
            player.sendMessage("§cNeed §f" + format(offer.price) + " coins§c.");
            return;
        }
        ItemStack bought = offer.create.get();
        player.getInventory().addItem(bought).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        player.sendMessage("§bFishmonger §8» §7" + offer.label + " §8· §c-" + format(offer.price) + " coins");
        stock(top);
        top.setItem(INFO_SLOT, button(Material.LEATHER_CHESTPLATE, "§bDock Stock",
                "§7T1 fishing kit — rod and armor.",
                "§7No fish. Catch your own.",
                "§7Balance: §e" + format(coins.get(player)) + " coins"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void stock(Inventory inventory) {
        Offer[] offers = offers();
        for (int i = 0; i < BUY_SLOTS.length; i++) {
            if (i >= offers.length) {
                break;
            }
            inventory.setItem(BUY_SLOTS[i], shopIcon(offers[i]));
        }
    }

    private Offer[] offers() {
        return new Offer[]{
                new Offer("fishing_rod", "Nibble Rod", 24L, () -> items.fishing().rod(1)),
                new Offer("fishing_helmet", "Nibble Cap", 16L, () -> items.fishing().helmet(1)),
                new Offer("fishing_chestplate", "Nibble Smock", 28L, () -> items.fishing().chestplate(1)),
                new Offer("fishing_leggings", "Nibble Trousers", 22L, () -> items.fishing().leggings(1)),
                new Offer("fishing_boots", "Nibble Clogs", 14L, () -> items.fishing().boots(1))
        };
    }

    private Offer offerAt(int slot) {
        Offer[] offers = offers();
        for (int i = 0; i < BUY_SLOTS.length && i < offers.length; i++) {
            if (BUY_SLOTS[i] == slot) {
                return offers[i];
            }
        }
        return null;
    }

    private ItemStack shopIcon(Offer offer) {
        ItemStack item = offer.create.get();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§eBuy §8· §6" + format(offer.price) + " coins");
            lore.add("§8Dock stall · T1 fishing kit.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ItemKeys.fishShop(), PersistentDataType.STRING, offer.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String format(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private record Offer(String id, String label, long price, Supplier<ItemStack> create) {
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /** Reflection-friendly entry for Quests. */
    public static void openFor(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getFishShop() == null) {
            player.sendMessage("§cFish shop is offline.");
            return;
        }
        plugin.getFishShop().open(player);
    }
}
