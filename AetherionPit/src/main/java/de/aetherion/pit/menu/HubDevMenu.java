package de.aetherion.pit.menu;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Minimal hub admin GUI — /dev */
public final class HubDevMenu implements Listener {

    public static final String TITLE = "§8Hub DEV";

    private final AetherionPit plugin;

    public HubDevMenu(AetherionPit plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        AetherionPit plugin = AetherionPit.getInstance();
        PitDataStore.Stats stats = plugin.data().of(player);
        Inventory inv = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane.clone());
        }
        inv.setItem(4, named(Material.GOLD_INGOT,
                "§6You",
                "§7Pit Lvl §f" + stats.level(),
                "§6Gold §f" + stats.gold() + "g",
                "§8Safe: §7" + plugin.safeZone().describe()));
        inv.setItem(10, named(Material.END_PORTAL_FRAME,
                "§d§lPlace Aetherion NPC",
                "§7At your feet.",
                "§eClick → transfer NPC"));
        inv.setItem(12, named(Material.BLUE_DYE,
                "§9§lPlace Discord NPC",
                "§7At your feet.",
                "§eClick → discord invite NPC"));
        inv.setItem(14, named(Material.CHEST,
                "§6§lPlace Shop NPC",
                "§7At your feet.",
                "§eClick → pit shop NPC"));
        inv.setItem(16, named(Material.COMPASS,
                "§aTeleport Spawn",
                "§70 64 0"));
        inv.setItem(19, named(Material.EMERALD,
                "§a+100 Gold",
                "§7Test currency"));
        inv.setItem(21, named(Material.EXPERIENCE_BOTTLE,
                "§a+1 Pit Level",
                "§7Test level / XP bar"));
        inv.setItem(23, named(Material.ENDER_EYE,
                "§eReload Hub NPCs",
                "§7Re-ensure FancyNPCs"));
        inv.setItem(25, named(Material.BARRIER, "§cClose"));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.45f, 1.3f);
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
        if (!player.hasPermission("aetherion.pit.admin")) {
            player.closeInventory();
            return;
        }
        switch (event.getSlot()) {
            case 10 -> {
                plugin.hubNpcs().place("aetherion", player.getLocation());
                player.sendMessage("§aPlaced §dAetherion §aNPC.");
            }
            case 12 -> {
                plugin.hubNpcs().place("discord", player.getLocation());
                player.sendMessage("§aPlaced §9Discord §aNPC.");
            }
            case 14 -> {
                plugin.hubNpcs().place("shop", player.getLocation());
                player.sendMessage("§aPlaced §6Shop §aNPC.");
            }
            case 16 -> {
                var spawn = plugin.safeZone().spawn();
                if (spawn != null) {
                    player.teleport(spawn);
                    player.sendMessage("§aSpawn.");
                }
            }
            case 19 -> {
                plugin.levels().addGold(player, 100);
                player.sendMessage("§6+100g");
                open(player);
                return;
            }
            case 21 -> {
                plugin.levels().addXp(player, plugin.levels().xpForNext(plugin.data().of(player).level()));
                open(player);
                return;
            }
            case 23 -> {
                plugin.hubNpcs().ensureAll();
                player.sendMessage("§aHub NPCs reloaded.");
            }
            case 25 -> player.closeInventory();
            default -> {
                return;
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
