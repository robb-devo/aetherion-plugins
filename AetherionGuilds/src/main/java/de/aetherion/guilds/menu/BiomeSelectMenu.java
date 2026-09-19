package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.IslandBiome;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class BiomeSelectMenu {

    public static final String TITLE = "§8Choose Island Biome";
    public static final int PLAINS_SLOT = 11;
    public static final int FOREST_SLOT = 13;
    public static final int DESERT_SLOT = 15;
    public static final int CLOSE_SLOT = 22;

    private final PersonalIslandService islands;

    public BiomeSelectMenu(PersonalIslandService islands) {
        this.islands = islands;
    }

    public void open(Player player) {
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return;
        }
        if (islands.byOwner(player.getUniqueId()) != null) {
            player.sendMessage("§cBiome is locked after your island is created.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(4, named(
                Material.GRASS_BLOCK,
                "§aPersonal Island",
                "§7Pick a look. Quarries work the same.",
                "§7Friends can visit once it exists.",
                "§8Biome cannot be changed later."
        ));
        putBiome(inventory, PLAINS_SLOT, IslandBiome.PLAINS);
        putBiome(inventory, FOREST_SLOT, IslandBiome.FOREST);
        putBiome(inventory, DESERT_SLOT, IslandBiome.DESERT);
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public void handle(Player player, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        IslandBiome biome = switch (slot) {
            case PLAINS_SLOT -> IslandBiome.PLAINS;
            case FOREST_SLOT -> IslandBiome.FOREST;
            case DESERT_SLOT -> IslandBiome.DESERT;
            default -> null;
        };
        if (biome == null) {
            return;
        }
        player.closeInventory();
        islands.create(player, biome);
    }

    private void putBiome(Inventory inventory, int slot, IslandBiome biome) {
        inventory.setItem(slot, named(
                biome.icon(),
                "§e" + biome.display(),
                biome.look(),
                biome.blurb(),
                "",
                "§eClick to claim"
        ));
    }

    private ItemStack named(Material material, String name, String... lore) {
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
