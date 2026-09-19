package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class IslandMenu {

    public static final String TITLE = "§8Island";
    public static final int INFO_SLOT = 4;
    public static final int HOME_SLOT = 20;
    public static final int COLLECT_SLOT = 22;
    public static final int UPGRADE_SLOT = 24;
    public static final int FRIENDS_SLOT = 30;
    public static final int STATUS_SLOT = 32;
    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private final PersonalIslandService islands;
    private final MinionService minions;
    private final FriendMenu friends;
    private final BiomeSelectMenu biomes;

    public IslandMenu(
            PersonalIslandService islands,
            MinionService minions,
            FriendMenu friends,
            BiomeSelectMenu biomes
    ) {
        this.islands = islands;
        this.minions = minions;
        this.friends = friends;
        this.biomes = biomes;
    }

    public void open(Player player) {
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return;
        }
        PersonalIsland island = islands.byOwner(player.getUniqueId());
        if (island == null) {
            biomes.open(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(INFO_SLOT, named(
                island.biome().icon(),
                "§aYour Island",
                "§7Biome: §f" + island.biome().display(),
                "§7Level: §f" + island.islandLevel(),
                "§7Quarries: §f" + island.minions().size(),
                "§7Build radius: §f" + islands.buildRadius(island),
                island.biome().look()
        ));
        inventory.setItem(HOME_SLOT, named(
                Material.OAK_DOOR,
                "§aGo Home",
                "§7Teleport to your island."
        ));
        inventory.setItem(COLLECT_SLOT, named(
                Material.HOPPER,
                "§eCollect All",
                "§7Empty every quarry at once."
        ));
        if (island.canUpgradeIsland()) {
            IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(island.islandLevel());
            List<String> lore = new ArrayList<>();
            lore.add("§7Upgrade to §fLv." + (island.islandLevel() + 1));
            lore.add("§7Bigger island and a better look.");
            lore.add("§7Radius → §f" + islands.buildRadius(island) + " §8+8");
            lore.add("");
            if (cost.compactedCobble() > 0) {
                lore.add("§f" + cost.compactedCobble() + " Compacted Cobble");
            }
            if (cost.cores() > 0) {
                lore.add("§d" + cost.cores() + " Quarry Core");
            }
            if (cost.coins() > 0) {
                lore.add("§6" + GuildFormat.compact(cost.coins()) + " Coins");
            }
            inventory.setItem(UPGRADE_SLOT, named(Material.BEACON, "§eUpgrade Island", lore.toArray(String[]::new)));
        } else {
            inventory.setItem(UPGRADE_SLOT, named(
                    Material.BEDROCK,
                    "§cMax Island",
                    "§7Lv." + island.islandLevel() + " is fully upgraded."
            ));
        }
        inventory.setItem(FRIENDS_SLOT, named(
                Material.PLAYER_HEAD,
                "§bFriends",
                "§7Friends can visit your island.",
                "§eClick to open friends."
        ));
        inventory.setItem(STATUS_SLOT, named(
                Material.CLOCK,
                "§eQuarry Status",
                statusLines(island)
        ));
        inventory.setItem(BACK_SLOT, named(
                Material.ARROW,
                "§eBack",
                "§7Return to Aetherion Manager."
        ));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public void handle(Player player, int slot) {
        PersonalIsland island = islands.byOwner(player.getUniqueId());
        if (island == null) {
            player.closeInventory();
            biomes.open(player);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            player.closeInventory();
            player.performCommand("aethermanager");
            return;
        }
        if (slot == HOME_SLOT) {
            player.closeInventory();
            islands.goHome(player);
            return;
        }
        if (slot == COLLECT_SLOT) {
            minions.collectAll(player, island);
            open(player);
            return;
        }
        if (slot == UPGRADE_SLOT) {
            islands.upgradeIsland(player);
            open(player);
            return;
        }
        if (slot == FRIENDS_SLOT) {
            friends.open(player);
            return;
        }
        if (slot == STATUS_SLOT) {
            if (island.minions().isEmpty()) {
                player.sendMessage("§7No quarries yet. Craft one: §f8 Compressed §7around a §dQuarry Core§7.");
                return;
            }
            for (QuarryMinion minion : island.minions()) {
                minions.catchUp(minion);
                QuarryMinion.StorageView view = minion.view();
                player.sendMessage("§7" + minion.quarryType().display() + " §8Lv." + minion.level()
                        + " §8- " + GuildFormat.nametag(minion.quarryType(), minion.level(), view));
            }
            islands.save();
        }
    }

    private String[] statusLines(PersonalIsland island) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Runs while the server is online.");
        if (island.minions().isEmpty()) {
            lore.add("§8No quarries placed.");
            return lore.toArray(String[]::new);
        }
        for (QuarryMinion minion : island.minions()) {
            QuarryMinion.StorageView view = minions.preview(minion);
            lore.add("§7" + minion.quarryType().display() + " §8Lv." + minion.level()
                    + " §7· §e" + view.rawEquivalent() + "§8/" + minion.cap());
        }
        return lore.toArray(String[]::new);
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
