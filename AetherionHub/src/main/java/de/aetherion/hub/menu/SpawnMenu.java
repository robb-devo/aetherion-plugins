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

public final class SpawnMenu {

    public static final String TITLE = "§8Aetherion Spawns";
    /** Same slot as every Manager page's Back (ManagerNav.SLOT). */
    public static final int BACK_SLOT = 45;
    public static final int INFO_SLOT = 49;
    private static final int MENU_SIZE = 54;

    /**
     * Book page tiles (gen_gui_chrome.py BOOK_TILES['spawns']): short names four to a row on top, the long ones three
     * to a row below, so every name fits under its seal in both languages.
     */
    private static int displaySlot(HubSpawn spawn) {
        return switch (spawn.id()) {
            case "harbour" -> 1;
            case "ore_ridge" -> 3;
            case "mines" -> 5;
            case "farm" -> 7;
            case "capital" -> 19;
            case "forage_isle" -> 22;
            case "farm_isle" -> 25;
            case "colosseum" -> 37;
            case "eldervale" -> 40;
            case "borderlands" -> 43;
            default -> -1;
        };
    }

    private static String caption(HubSpawn spawn, boolean german) {
        return switch (spawn.id()) {
            case "harbour" -> german ? "Hafen" : "Harbor";
            case "ore_ridge" -> german ? "Erzgrat" : "Ridge";
            case "mines" -> german ? "Minen" : "Mines";
            case "farm" -> "Farm";
            case "capital" -> german ? "Hauptstadt" : "Capital";
            case "forage_isle" -> german ? "Waldinsel" : "Forage";
            case "farm_isle" -> german ? "Farminsel" : "Farm Isle";
            case "colosseum" -> german ? "Kolosseum" : "Colosseum";
            case "eldervale" -> "Eldervale";
            case "borderlands" -> german ? "Grenzland" : "Borderlands";
            default -> spawn.displayName();
        };
    }

    private final HubService hub;

    public SpawnMenu(HubService hub) {
        this.hub = hub;
    }

    public void open(Player player) {
        boolean german = de.aetherion.core.api.AetherServices.german(player);
        Inventory inventory = Bukkit.createInventory(new Holder(), MENU_SIZE);
        inventory.setItem(INFO_SLOT, item(
                Material.COMPASS,
                german ? "§6Deine Spawns" : "§6Your Spawns",
                german
                        ? List.of("§7Lager schaltest du beim Erkunden frei.", "§8Blass = gesperrt, Gold = dein /spawn.", "",
                                "§eLinksklick §7→ als /spawn setzen", "§eRechtsklick §7→ sofort hin")
                        : List.of("§7Camps unlock as you explore.", "§8Faded = locked, gold = your /spawn.", "",
                                "§eLeft-click §7→ set /spawn", "§eRight-click §7→ teleport now")
        ));
        List<de.aetherion.core.api.BookPage.Caption> captions = new ArrayList<>();
        HubSpawn selected = hub.selected(player);

        for (HubSpawn spawn : hub.spawns()) {
            if (!HubService.isOriginSpawn(spawn.id())) {
                continue;
            }
            int slot = displaySlot(spawn);
            if (slot < 0 || slot >= inventory.getSize() || slot == INFO_SLOT || slot == BACK_SLOT) {
                continue;
            }
            inventory.setItem(slot, spawnItem(player, spawn, selected));
            de.aetherion.core.api.BookPage.State state = !hub.isUnlocked(player, spawn.id())
                    ? de.aetherion.core.api.BookPage.State.LOCKED
                    : selected != null && selected.id().equals(spawn.id())
                    ? de.aetherion.core.api.BookPage.State.NEXT
                    : de.aetherion.core.api.BookPage.State.OPEN;
            captions.add(new de.aetherion.core.api.BookPage.Caption(slot, caption(spawn, german), state));
        }
        // The round back hook on its seal, like every other Manager page (pack icon, gen_gui_icons.py).
        inventory.setItem(BACK_SLOT, de.aetherion.core.api.ChromePage.icon(item(Material.ARROW,
                german ? "§eZurück" : "§eBack",
                List.of(german ? "§7Zum Aetherion Manager." : "§7Return to the Aetherion Manager.")),
                de.aetherion.core.api.ChromePage.BACK));
        Inventory page = Bukkit.createInventory(new Holder(), MENU_SIZE,
                de.aetherion.core.api.BookPage.title('', captions));
        page.setContents(inventory.getContents());
        player.openInventory(page);
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
                    || "borderlands".equalsIgnoreCase(spawn.id()) || "ore_ridge".equalsIgnoreCase(spawn.id())) {
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
