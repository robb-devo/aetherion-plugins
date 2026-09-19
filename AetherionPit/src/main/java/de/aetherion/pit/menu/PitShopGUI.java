package de.aetherion.pit.menu;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class PitShopGUI implements Listener {

    public static final String TITLE_AMP = "&6Pit Shop";

    private final AetherionPit plugin;

    public PitShopGUI(AetherionPit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        AetherionPit plugin = AetherionPit.getInstance();
        PitDataStore.Stats stats = plugin.data().of(player);
        Inventory inv = Bukkit.createInventory(new Holder(), 27, de.aetherion.pit.util.Msg.amp(TITLE_AMP));
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane.clone());
        }
        inv.setItem(4, named(Material.GOLD_INGOT,
                "&6Your purse",
                "&7Level &f" + stats.level(),
                "&6Gold &f" + stats.gold() + "g"));
        inv.setItem(10, offer(Material.IRON_SWORD, "&fIron Sword", 25, false));
        inv.setItem(11, offer(Material.IRON_CHESTPLATE, "&fIron Chest", 40, false));
        inv.setItem(12, offer(Material.BOW, "&fBow", 30, false));
        inv.setItem(13, offer(Material.ARROW, "&fArrows x16", 8, false));
        inv.setItem(14, offer(Material.GOLDEN_APPLE, "&6Golden Apple", 35, false));
        inv.setItem(15, offer(Material.DIAMOND_SWORD, "&bSharp I Diamond", 90, true));
        inv.setItem(16, offer(Material.DIAMOND_CHESTPLATE, "&bProt I Diamond", 110, true));
        inv.setItem(22, named(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        Offer offer = offerFor(slot);
        if (offer == null) {
            return;
        }
        PitDataStore.Stats stats = plugin.data().of(player);
        if (offer.enchanted && stats.level() < 5) {
            de.aetherion.pit.util.Msg.send(player, "&cNeed Pit Level &f5&c for enchanted gear.");
            return;
        }
        if (!plugin.levels().spendGold(player, offer.cost)) {
            de.aetherion.pit.util.Msg.send(player, "&cNot enough gold. You have &6" + stats.gold() + "g&c.");
            return;
        }
        ItemStack item = new ItemStack(offer.material, offer.amount);
        if (offer.enchanted) {
            if (offer.material == Material.DIAMOND_SWORD) {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
            } else if (offer.material.name().contains("CHESTPLATE")
                    || offer.material.name().contains("LEGGINGS")
                    || offer.material.name().contains("HELMET")
                    || offer.material.name().contains("BOOTS")) {
                item.addUnsafeEnchantment(Enchantment.PROTECTION, 1);
            }
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        de.aetherion.pit.util.Msg.send(player, "&aBought &f" + offer.label + " &7for &6" + offer.cost + "g&7.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        open(player);
    }

    private static Offer offerFor(int slot) {
        return switch (slot) {
            case 10 -> new Offer(Material.IRON_SWORD, 1, 25, false, "Iron Sword");
            case 11 -> new Offer(Material.IRON_CHESTPLATE, 1, 40, false, "Iron Chest");
            case 12 -> new Offer(Material.BOW, 1, 30, false, "Bow");
            case 13 -> new Offer(Material.ARROW, 16, 8, false, "Arrows");
            case 14 -> new Offer(Material.GOLDEN_APPLE, 1, 35, false, "Golden Apple");
            case 15 -> new Offer(Material.DIAMOND_SWORD, 1, 90, true, "Sharp I Diamond");
            case 16 -> new Offer(Material.DIAMOND_CHESTPLATE, 1, 110, true, "Prot I Diamond");
            default -> null;
        };
    }

    private static ItemStack offer(Material material, String name, int cost, boolean gated) {
        return named(material, name,
                gated ? "&8Requires Pit Level &f5" : "&7Vanilla pit gear",
                "&6Cost &f" + cost + "g",
                "&eClick to buy");
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(de.aetherion.pit.util.Msg.amp(name));
            if (lore != null && lore.length > 0) {
                meta.lore(java.util.Arrays.stream(lore).map(de.aetherion.pit.util.Msg::amp).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private record Offer(Material material, int amount, int cost, boolean enchanted, String label) {
    }

    public record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
