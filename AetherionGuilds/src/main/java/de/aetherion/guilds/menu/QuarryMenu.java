package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class QuarryMenu {

    public static final int COLLECT_SLOT = 11;
    public static final int INFO_SLOT = 13;
    public static final int UPGRADE_SLOT = 15;
    public static final int MILL_SLOT = 20;
    public static final int PICKUP_SLOT = 21;
    public static final int CLOSE_SLOT = 22;

    private final MinionService minions;
    private final GuildService guilds;
    private final PersonalIslandService personal;

    public QuarryMenu(GuildService guilds, PersonalIslandService personal, MinionService minions) {
        this.guilds = guilds;
        this.personal = personal;
        this.minions = minions;
    }

    public void open(Player player, Guild guild, QuarryMinion minion) {
        open(player, new Holder(guild.id(), minion.id(), false), minion);
    }

    public void open(Player player, PersonalIsland island, QuarryMinion minion) {
        open(player, new Holder(island.ownerId(), minion.id(), true), minion);
    }

    public void open(Player player, MinionService.QuarryRef ref) {
        if (ref == null) {
            return;
        }
        if (ref.isPersonal()) {
            open(player, ref.island(), ref.minion());
        } else {
            open(player, ref.guild(), ref.minion());
        }
    }

    private void open(Player player, Holder holder, QuarryMinion minion) {
        minions.catchUp(minion);
        QuarryType type = minion.quarryType();
        QuarryMinion.StorageView view = minion.view();
        String mill = switch (minion.processor()) {
            case COMPRESSED -> "§aMill: crafts Compressed";
            case COMPACTED -> "§bForge: Compacted, leftover Compressed";
            default -> "§8No mill installed";
        };
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8" + type.display());
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        inventory.setItem(INFO_SLOT, named(
                type.icon(),
                "§6" + type.display() + " §8Lv." + minion.level() + "/" + QuarryType.MAX_LEVEL,
                "§7Raw: §e" + view.raw() + " " + type.productName(),
                view.compressed() > 0 ? "§7Compressed: §a" + view.compressed() : "§8No compressed stored",
                view.compacted() > 0 ? "§7Compacted: §b" + view.compacted() : "§8No compacted stored",
                "§7Rate: §f" + minion.perTick() + " §7/ tick",
                "§7Cap: §f" + minion.cap() + " raw-eq",
                mill,
                type.blurb(),
                "§8Crafts while the server is online."
        ));
        inventory.setItem(COLLECT_SLOT, named(
                type.product(),
                "§aCollect",
                "§7Take stored items.",
                minion.processor() == QuarryMinion.Processor.NONE
                        ? "§eClick"
                        : "§eClick §7for crafted packs first."
        ));
        if (minion.canUpgrade()) {
            inventory.setItem(UPGRADE_SLOT, named(
                    Material.EXPERIENCE_BOTTLE,
                    "§eUpgrade to Lv." + (minion.level() + 1),
                    upgradeLore(type, minion.nextUpgradeCost(), minion.level() + 1)
            ));
        } else {
            inventory.setItem(UPGRADE_SLOT, named(
                    Material.BEDROCK,
                    "§cMax Level",
                    "§7This quarry is fully upgraded."
            ));
        }
        if (minion.processor() == QuarryMinion.Processor.COMPACTED) {
            inventory.setItem(MILL_SLOT, named(
                    Material.BLAST_FURNACE,
                    mill,
                    "§7Crafts Compacted while it runs.",
                    "§8Leftover under 16,384 becomes Compressed."
            ));
        } else {
            inventory.setItem(MILL_SLOT, named(
                    Material.PISTON,
                    minion.processor() == QuarryMinion.Processor.COMPRESSED ? mill : "§eInstall Mill / Forge",
                    minion.processor() == QuarryMinion.Processor.COMPRESSED
                            ? "§7Crafts Compressed while it runs."
                            : "§7Hold a Quarry Mill or Forge",
                    minion.processor() == QuarryMinion.Processor.COMPRESSED
                            ? "§8Leftover stays raw until 128."
                            : "§7and click here."
            ));
        }
        inventory.setItem(PICKUP_SLOT, named(
                Material.CHEST,
                "§ePick Up",
                "§7Move this quarry.",
                "§7Keeps level, mill and storage.",
                "§eClick"
        ));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public void handle(Player player, Holder holder, int slot) {
        MinionService.QuarryRef ref = find(holder);
        if (ref == null) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == COLLECT_SLOT) {
            minions.collect(player, ref);
            open(player, ref);
            return;
        }
        if (slot == UPGRADE_SLOT) {
            minions.upgrade(player, ref.minion());
            open(player, ref);
            return;
        }
        if (slot == MILL_SLOT) {
            minions.installProcessor(player, ref.minion(), player.getInventory().getItemInMainHand());
            open(player, ref);
            return;
        }
        if (slot == PICKUP_SLOT) {
            if (minions.pickup(player, ref)) {
                player.closeInventory();
            }
        }
    }

    private MinionService.QuarryRef find(Holder holder) {
        if (holder.personal()) {
            PersonalIsland island = personal == null ? null : personal.byOwner(holder.ownerId());
            if (island == null) {
                return null;
            }
            for (QuarryMinion minion : island.minions()) {
                if (minion.id().equals(holder.minionId())) {
                    return new MinionService.QuarryRef(null, island, minion);
                }
            }
            return null;
        }
        Guild guild = guilds.byId(holder.ownerId());
        if (guild == null) {
            return null;
        }
        for (QuarryMinion minion : guild.minions()) {
            if (minion.id().equals(holder.minionId())) {
                return new MinionService.QuarryRef(guild, null, minion);
            }
        }
        return null;
    }

    private String[] upgradeLore(QuarryType type, QuarryType.UpgradeCost cost, int nextLevel) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Cost:");
        if (cost.compressed() > 0) {
            lore.add("§f" + cost.compressed() + " Compressed " + type.productName());
        }
        if (cost.compacted() > 0) {
            lore.add("§f" + cost.compacted() + " Compacted " + type.productName());
        }
        if (cost.cores() > 0) {
            lore.add("§d" + cost.cores() + " Quarry Core");
        }
        lore.add("§7More output per tick.");
        lore.add(QuarryType.upgradeLine(nextLevel));
        lore.add("§8Island grind. Last tiers jump hard.");
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
        private final UUID ownerId;
        private final UUID minionId;
        private final boolean personal;

        public Holder(UUID ownerId, UUID minionId, boolean personal) {
            this.ownerId = ownerId;
            this.minionId = minionId;
            this.personal = personal;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public UUID guildId() {
            return ownerId;
        }

        public UUID minionId() {
            return minionId;
        }

        public boolean personal() {
            return personal;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
