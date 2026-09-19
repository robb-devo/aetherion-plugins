package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.service.FriendService;
import de.aetherion.guilds.service.GuildService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FriendMenu {

    public static final String TITLE = "§8Friends";
    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private final FriendService friends;
    private final GuildService guilds;

    public FriendMenu(FriendService friends, GuildService guilds) {
        this.friends = friends;
        this.guilds = guilds;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        List<UUID> incoming = friends.incomingOf(player.getUniqueId());
        List<UUID> list = friends.friendsOf(player.getUniqueId());
        inventory.setItem(4, named(
                Material.PLAYER_HEAD,
                "§bFriends",
                "§7Click a friend to visit their island.",
                "§7Offline friends with an island work too.",
                "§7Shift-click to guild-invite.",
                "§7Pending: §f" + incoming.size(),
                "§7Friends: §f" + list.size(),
                "",
                "§8/friend add <name>",
                "§8/friend visit <name>"
        ));
        int slot = 9;
        for (UUID id : incoming) {
            if (slot >= 18) {
                break;
            }
            inventory.setItem(slot++, head(id, true));
        }
        slot = 18;
        for (UUID id : list) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, head(id, false));
        }
        inventory.setItem(BACK_SLOT, named(Material.ARROW, "§eBack"));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public void handle(Player player, int slot, ItemStack clicked, boolean shift) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            player.closeInventory();
            player.performCommand("guild");
            return;
        }
        UUID targetId = skullId(clicked);
        if (targetId == null) {
            return;
        }
        if (friends.incomingOf(player.getUniqueId()).contains(targetId)) {
            OfflinePlayer other = Bukkit.getOfflinePlayer(targetId);
            friends.accept(player, other.getName() == null ? targetId.toString() : other.getName());
            open(player);
            return;
        }
        Player online = Bukkit.getPlayer(targetId);
        if (online == null || !online.isOnline()) {
            OfflinePlayer other = Bukkit.getOfflinePlayer(targetId);
            String name = other.getName() == null ? targetId.toString() : other.getName();
            friends.visit(player, name);
            player.closeInventory();
            return;
        }
        if (shift) {
            Guild guild = guilds.byPlayer(player.getUniqueId());
            GuildRank rank = guild == null ? null : guild.rank(player.getUniqueId());
            if (guild == null || rank == null || !rank.canInvite()) {
                player.sendMessage("§cYour guild rank cannot invite.");
                return;
            }
            guilds.invite(player, online);
            return;
        }
        friends.visit(player, online.getName());
        player.closeInventory();
    }

    private ItemStack head(UUID id, boolean pending) {
        OfflinePlayer other = Bukkit.getOfflinePlayer(id);
        Player online = Bukkit.getPlayer(id);
        boolean isOnline = online != null && online.isOnline();
        String name = other.getName() == null ? id.toString().substring(0, 8) : other.getName();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(other);
            meta = skull;
        }
        if (meta != null) {
            meta.setDisplayName((isOnline ? "§a" : "§7") + name);
            List<String> lore = new ArrayList<>();
            lore.add(pending ? "§ePending request" : (isOnline ? "§aOnline" : "§8Offline"));
            if (pending) {
                lore.add("§eClick to accept");
            } else if (isOnline) {
                lore.add("§eClick to visit island");
                lore.add("§eShift-click to guild-invite");
            } else {
                lore.add("§eClick to visit their island");
                lore.add("§8if they have one.");
            }
            lore.add("§8/friend remove " + name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return tagged(item, id);
    }

    private ItemStack tagged(ItemStack item, UUID id) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("aetherionguilds", "friend"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    id.toString()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private UUID skullId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("aetherionguilds", "friend"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
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

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
