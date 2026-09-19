package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GuildMenu {

    public static final String TITLE = "§8Guild";
    public static final int CREATE_SLOT = 13;
    public static final int INFO_SLOT = 10;
    public static final int FRIENDS_SLOT = 11;
    public static final int MEMBERS_SLOT = 12;
    public static final int BANK_SLOT = 13;
    public static final int ISLAND_SLOT = 14;
    public static final int COLLECT_SLOT = 15;
    public static final int UPGRADE_SLOT = 16;
    public static final int MINIONS_SLOT = 21;
    public static final int BACK_SLOT = 18;

    private final GuildService guilds;
    private final MinionService minions;
    private final IslandService islands;
    private final FriendMenu friends;
    private BankMenu bank;

    public GuildMenu(GuildService guilds, MinionService minions, IslandService islands, FriendMenu friends) {
        this.guilds = guilds;
        this.minions = minions;
        this.islands = islands;
        this.friends = friends;
    }

    public void setBank(BankMenu bank) {
        this.bank = bank;
    }

    public void open(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }

        if (guild == null) {
            inventory.setItem(4, named(
                    Material.WHITE_BANNER,
                    "§eNo Guild",
                    "§7Create one with",
                    "§f/guild create <name>",
                    "§7or accept an invite."
            ));
            inventory.setItem(CREATE_SLOT, named(
                    Material.EMERALD,
                    "§aHow to start",
                    "§7/guild create <name>",
                    "§7/guild accept",
                    "§7/guild deny"
            ));
            inventory.setItem(FRIENDS_SLOT, named(
                    Material.PLAYER_HEAD,
                    "§bFriends",
                    "§7See who's online and invite",
                    "§7them once you have a guild.",
                    "§eClick"
            ));
            inventory.setItem(BACK_SLOT, named(Material.ARROW, "§eBack", "§7Return to the Aetherion Manager."));
            player.openInventory(inventory);
            return;
        }

        GuildRank rank = guild.rank(player.getUniqueId());
        inventory.setItem(4, named(
                Material.YELLOW_BANNER,
                "§6" + guild.name(),
                "§7Your rank: " + (rank == null ? "§8-" : rank.display()),
                "§7Members: §f" + guild.onlineMembers() + "§8/§7" + guild.members().size() + " online",
                "§7Island: §fLv." + guild.islandLevel(),
                "§7Quarries: §f" + guild.minions().size(),
                "§7Bank: §6" + GuildFormat.compact(guild.bankCoins()) + " coins"
        ));
        inventory.setItem(INFO_SLOT, named(
                Material.BOOK,
                "§eGuild Info",
                "§6Leader §8• §5Vice President",
                "§bMayor §8• §cSoldier §8• §7Footman",
                "§7Build radius: §f" + islands.buildRadius(guild)
        ));
        inventory.setItem(FRIENDS_SLOT, named(
                Material.PLAYER_HEAD,
                "§bFriends",
                "§7Online status and one-click",
                "§7guild invites.",
                "§eClick"
        ));
        inventory.setItem(MEMBERS_SLOT, named(
                Material.PLAYER_HEAD,
                "§bMembers",
                "§7Click to list the roster."
        ));
        inventory.setItem(BANK_SLOT, named(
                Material.ENDER_CHEST,
                "§6Guild Bank",
                "§7Shared chest and coin pool.",
                "§7Bank Lv." + guild.bankLevel() + " §8• §f" + guild.bankSlots() + " slots",
                "§6" + GuildFormat.compact(guild.bankCoins()) + " §8/ §7" + GuildFormat.compact(guild.coinCap()),
                "§eClick"
        ));
        inventory.setItem(ISLAND_SLOT, named(
                Material.GRASS_BLOCK,
                "§aIsland",
                "§7Teleport to your guild island."
        ));
        inventory.setItem(COLLECT_SLOT, named(
                Material.HOPPER,
                "§aCollect All",
                "§7Take stored items from",
                "§7every quarry at once.",
                "§eClick"
        ));
        if (guild.canUpgradeIsland()) {
            IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(guild.islandLevel());
            List<String> lore = new ArrayList<>();
            lore.add("§7Upgrade to §fLv." + (guild.islandLevel() + 1));
            lore.add("§7Bigger island and a better look.");
            lore.add("§7Radius → §f" + islands.buildRadius(guild) + " §8+8");
            lore.add("§7Cost:");
            if (cost.compactedCobble() > 0) {
                lore.add("§f" + cost.compactedCobble() + " Compacted Cobblestone");
            }
            if (cost.cores() > 0) {
                lore.add("§d" + cost.cores() + " Quarry Core");
            }
            if (cost.coins() > 0) {
                lore.add("§6" + GuildFormat.compact(cost.coins()) + " Coins");
            }
            lore.add("§eClick §7(Mayor+)");
            inventory.setItem(UPGRADE_SLOT, named(Material.BEACON, "§eUpgrade Island", lore.toArray(String[]::new)));
        } else {
            inventory.setItem(UPGRADE_SLOT, named(
                    Material.BEDROCK,
                    "§cMax Island",
                    "§7Lv." + guild.islandLevel() + " is fully upgraded."
            ));
        }
        inventory.setItem(MINIONS_SLOT, named(
                Material.FURNACE,
                "§6Quarries",
                "§7Craft with 8 Compressed items",
                "§7around a Quarry Core.",
                "§7Place, collect, or pick up",
                "§7to move them."
        ));
        inventory.setItem(BACK_SLOT, named(Material.ARROW, "§eBack", "§7Return to the Aetherion Manager."));
        player.openInventory(inventory);
    }

    public void handle(Player player, int slot) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (slot == BACK_SLOT) {
            openManager(player);
            return;
        }
        if (slot == FRIENDS_SLOT) {
            player.closeInventory();
            if (friends != null) {
                friends.open(player);
            }
            return;
        }
        if (guild == null) {
            return;
        }
        switch (slot) {
            case MEMBERS_SLOT -> {
                player.closeInventory();
                player.sendMessage("§6=== " + guild.name() + " ===");
                for (Map.Entry<UUID, GuildRank> entry : guild.members().entrySet()) {
                    OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = member.getName() == null ? entry.getKey().toString() : member.getName();
                    String online = member.isOnline() ? "§a●" : "§8○";
                    player.sendMessage(online + " " + entry.getValue().display() + " §f" + name);
                }
            }
            case BANK_SLOT -> {
                player.closeInventory();
                if (bank != null) {
                    bank.open(player);
                }
            }
            case ISLAND_SLOT -> {
                player.closeInventory();
                guilds.goHome(player);
            }
            case COLLECT_SLOT -> {
                minions.collectAll(player, guild);
                open(player);
            }
            case UPGRADE_SLOT -> {
                guilds.upgradeIsland(player);
                open(player);
            }
            case MINIONS_SLOT -> {
                player.closeInventory();
                if (guild.minions().isEmpty()) {
                    player.sendMessage("§7No quarries yet. Craft one: §f8 Compressed §7around a §dQuarry Core§7.");
                    return;
                }
                player.sendMessage("§6=== Quarries ===");
                for (QuarryMinion minion : guild.minions()) {
                    minions.catchUp(minion);
                    QuarryMinion.StorageView view = minion.view();
                    player.sendMessage("§7" + minion.quarryType().display() + " §8Lv." + minion.level()
                            + " @ " + minion.x() + " " + minion.y() + " " + minion.z()
                            + " §8- " + GuildFormat.nametag(minion.quarryType(), minion.level(), view));
                }
                guilds.save();
            }
            default -> {
            }
        }
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

    private static void openManager(Player player) {
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress == null) {
            player.closeInventory();
            return;
        }
        progress.openManager(player);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
