package de.aetherion.items.social;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.ItemLore;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.skill.AetherionLevel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class PlayerCardMenu {

    public static final String TITLE = "§8Player";

    private final AetherionItems plugin;
    private final ActiveEquipmentStats stats;

    public PlayerCardMenu(AetherionItems plugin) {
        this.plugin = plugin;
        this.stats = new ActiveEquipmentStats(plugin.getItemManager());
    }

    public void open(Player viewer, Player target) {
        Inventory inventory = Bukkit.createInventory(new Holder(target.getUniqueId()), 27, "§8" + target.getName());
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            skull.setDisplayName("§f" + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7A quick look at this player.");
            if (plugin.getSkills() != null) {
                lore.add("§7Level: " + AetherionLevel.coloredLevel(plugin.getSkills().accountLevel(target)));
                String rank = plugin.ranks() == null
                        ? AetherionLevel.coloredTitle(plugin.getSkills().accountLevel(target))
                        : plugin.ranks().displayTitle(target);
                if (rank != null && !rank.isBlank()) {
                    lore.add("§7Rank: " + rank);
                }
            }
            String guild = guildName(target);
            if (guild != null) {
                lore.add("§7Guild: §f" + guild);
            }
            String area = plugin.getAreas() == null ? "Wilderness" : plugin.getAreas().nameAt(target.getLocation());
            lore.add("§7Area: §f" + area);
            skull.setLore(lore);
            head.setItemMeta(skull);
        }
        inventory.setItem(4, head);

        inventory.setItem(11, named(
                Material.IRON_SWORD,
                "§eCombat",
                "§7Damage: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.DAMAGE)),
                "§7Defense: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.DEFENSE)),
                "§7Health: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.HEALTH)),
                "§7Crit: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.CRIT_CHANCE)) + "%"
        ));
        inventory.setItem(12, named(
                Material.IRON_PICKAXE,
                "§bMining",
                "§7Power: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.MINING_POWER)),
                "§7Fortune: §f" + ItemLore.formatStat(stats.getStat(target, ItemCapability.FORTUNE))
        ));
        inventory.setItem(13, copyOrEmpty(target.getInventory().getHelmet(), "§7Helmet"));
        inventory.setItem(14, copyOrEmpty(target.getInventory().getChestplate(), "§7Chestplate"));
        inventory.setItem(15, copyOrEmpty(target.getInventory().getLeggings(), "§7Leggings"));
        inventory.setItem(16, copyOrEmpty(target.getInventory().getBoots(), "§7Boots"));
        inventory.setItem(21, copyOrEmpty(target.getInventory().getItemInMainHand(), "§7Main Hand"));
        inventory.setItem(23, copyOrEmpty(target.getInventory().getItemInOffHand(), "§7Off Hand"));
        inventory.setItem(22, named(Material.BARRIER, "§cClose"));
        viewer.openInventory(inventory);
    }

    public void handle(Player player, int slot) {
        if (slot == 22) {
            player.closeInventory();
        }
    }

    private ItemStack copyOrEmpty(ItemStack item, String emptyName) {
        if (item == null || item.getType().isAir()) {
            return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, emptyName, "§8Empty.");
        }
        return item.clone();
    }

    private String guildName(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AetherionGuilds")) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("de.aetherion.guilds.AetherionGuilds");
            Object plugin = clazz.getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return null;
            }
            Object guilds = plugin.getClass().getMethod("getGuilds").invoke(plugin);
            Object guild = guilds.getClass().getMethod("byPlayer", java.util.UUID.class).invoke(guilds, player.getUniqueId());
            if (guild == null) {
                return null;
            }
            Object name = guild.getClass().getMethod("name").invoke(guild);
            return name instanceof String text ? text : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private ItemStack named(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static final class Holder implements InventoryHolder {
        private final java.util.UUID targetId;

        public Holder(java.util.UUID targetId) {
            this.targetId = targetId;
        }

        public java.util.UUID targetId() {
            return targetId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
