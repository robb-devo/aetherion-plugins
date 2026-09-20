package de.aetherion.hub.menu;

import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SpawnMenu {

    public static final String TITLE = "§8Aetherion Spawns";
    public static final int BACK_SLOT = 49;
    public static final int INFO_SLOT = 4;
    private static final int MENU_SIZE = 54;

    /** Left + right edge columns stay glass. Content lives in columns 1–7. */
    private static final Set<Integer> EDGE_SLOTS = Set.of(
            0, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 53
    );

    private static int displaySlot(HubSpawn spawn) {
        return switch (spawn.id()) {
            case "harbour" -> 10;
            case "ore_ridge" -> 11;
            case "mines" -> 12;
            case "capital" -> 13;
            case "forage_isle" -> 14;
            case "farm" -> 15;
            case "farm_isle" -> 16;
            case "colosseum" -> 19;
            case "eldervale" -> 20;
            case "borderlands" -> 21;
            case "fishing" -> 22;
            default -> spawn.slot();
        };
    }

    private final HubService hub;

    public SpawnMenu(HubService hub) {
        this.hub = hub;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), MENU_SIZE, TITLE);
        fillBorder(inventory);

        inventory.setItem(INFO_SLOT, item(
                Material.COMPASS,
                "§6Your Spawns",
                List.of(
                        "§7Camps unlock as you explore.",
                        "§cRed §7= locked · walk there or finish the quest.",
                        "",
                        "§7Harbour → Ore Ridge → Mines → Capital",
                        "§7Forage · Farm · Farm Isle · Fishing Isle · Borderlands",
                        "",
                        "§eLeft-click §7→ set /spawn",
                        "§eRight-click §7→ teleport now"
                )
        ));

        HubSpawn selected = hub.selected(player);

        for (HubSpawn spawn : hub.spawns()) {
            if (!HubService.isOriginSpawn(spawn.id())) {
                continue;
            }
            int slot = displaySlot(spawn);
            if (slot < 0 || slot >= inventory.getSize() || slot == INFO_SLOT || slot == BACK_SLOT) {
                continue;
            }
            if (EDGE_SLOTS.contains(slot)) {
                continue;
            }
            inventory.setItem(slot, spawnItem(player, spawn, selected));
        }

        inventory.setItem(BACK_SLOT, item(Material.ARROW, "§eBack", List.of("§7Return to the Aetherion Manager.")));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    public void handleClick(Player player, int slot, boolean rightClick) {
        if (slot == BACK_SLOT) {
            openManager(player);
            return;
        }

        HubSpawn clicked = null;
        for (HubSpawn spawn : hub.spawns()) {
            if (!HubService.isOriginSpawn(spawn.id())) {
                continue;
            }
            if (displaySlot(spawn) == slot) {
                clicked = spawn;
                break;
            }
        }

        if (clicked == null) {
            return;
        }

        if (!hub.isUnlocked(player, clicked.id())) {
            player.sendMessage(hub.format("messages.locked", clicked));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (rightClick) {
            player.closeInventory();
            hub.teleport(player, clicked);
            return;
        }

        if (hub.select(player, clicked.id())) {
            player.sendMessage(hub.format("messages.selected", clicked));
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 1.4f);
            open(player);
        }
    }

    private ItemStack spawnItem(Player player, HubSpawn spawn, HubSpawn selected) {
        boolean unlocked = hub.isUnlocked(player, spawn.id());
        boolean isSelected = selected != null && selected.id().equals(spawn.id());

        List<String> lore = new ArrayList<>();
        if (spawn.description() != null && !spawn.description().isBlank()) {
            lore.add("§7" + spawn.description());
            lore.add("");
        }

        if (!unlocked) {
            lore.add("§cLocked");
            if ("farm".equalsIgnoreCase(spawn.id()) || "capital".equalsIgnoreCase(spawn.id())
                    || "borderlands".equalsIgnoreCase(spawn.id()) || "ore_ridge".equalsIgnoreCase(spawn.id())
                    || "eldervale".equalsIgnoreCase(spawn.id()) || "fishing".equalsIgnoreCase(spawn.id())) {
                lore.add("§7Walk there to unlock.");
            } else {
                lore.add("§7Unlock through the story,");
                lore.add("§7or discover it in the world.");
            }
            if (!spawn.hasLocation()) {
                lore.add("§8No teleport planted yet.");
            }
            return item(spawn.icon(), "§c" + spawn.displayName(), lore);
        }

        if (isSelected) {
            lore.add("§aCurrent spawn");
            lore.add("§eRight-click to teleport");
        } else {
            lore.add("§aUnlocked");
            if (!spawn.hasLocation()) {
                lore.add("§cNo teleport planted yet.");
            }
            lore.add("§eLeft-click to select");
            lore.add("§eRight-click to teleport");
        }

        ItemStack item = item(spawn.icon(), (isSelected ? "§a" : "§f") + spawn.displayName(), lore);
        if (isSelected) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private void fillBorder(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        // Top + bottom rows
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, pane.clone());
            inventory.setItem(45 + slot, pane.clone());
        }
        // Left + right columns
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, pane.clone());
            inventory.setItem(row * 9 + 8, pane.clone());
        }
    }

    private static void openManager(Player player) {
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress == null) {
            player.closeInventory();
            return;
        }
        progress.openManager(player);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
