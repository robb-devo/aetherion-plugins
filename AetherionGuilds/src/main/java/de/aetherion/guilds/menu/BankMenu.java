package de.aetherion.guilds.menu;

import de.aetherion.guilds.model.BankTiers;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BankMenu {

    public static final String TITLE = "§8Guild Bank";
    public static final int COIN_INFO = 45;
    public static final int DEPOSIT_1K = 46;
    public static final int DEPOSIT_10K = 47;
    public static final int DEPOSIT_ALL = 48;
    public static final int UPGRADE_SLOT = 49;
    public static final int WITHDRAW_1K = 50;
    public static final int WITHDRAW_10K = 51;
    public static final int WITHDRAW_ALL = 52;
    public static final int BACK_SLOT = 53;

    private final GuildService guilds;
    private final GuildMenu guildMenu;

    public BankMenu(GuildService guilds, GuildMenu guildMenu) {
        this.guilds = guilds;
        this.guildMenu = guildMenu;
    }

    public void open(Player player) {
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cCreate or join a guild first.");
            return;
        }
        Holder holder = new Holder(guild.id());
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE);
        holder.bind(inventory);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        int slots = guild.bankSlots();
        for (int slot = 0; slot < 45; slot++) {
            if (slot < slots) {
                ItemStack stored = guild.bank()[slot];
                inventory.setItem(slot, stored == null ? null : stored.clone());
            } else {
                inventory.setItem(slot, pane.clone());
            }
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, pane.clone());
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        inventory.setItem(COIN_INFO, named(
                Material.GOLD_INGOT,
                "§6Guild Coins",
                "§f" + GuildFormat.compact(guild.bankCoins()) + " §8/ §7" + GuildFormat.compact(guild.coinCap()),
                "§7Bank level: §f" + guild.bankLevel() + "§8/§7" + BankTiers.MAX_LEVEL,
                "§7Item slots: §f" + slots,
                rank != null && rank.canBankWithdraw() ? "§eSoldiers+ can withdraw." : "§7Deposit only."
        ));
        inventory.setItem(DEPOSIT_1K, named(Material.CHEST, "§aDeposit 1,000", "§7Your coins: §f" + GuildFormat.compact(AetherionItemsAccess.coins(player))));
        inventory.setItem(DEPOSIT_10K, named(Material.CHEST, "§aDeposit 10,000"));
        inventory.setItem(DEPOSIT_ALL, named(Material.ENDER_CHEST, "§aDeposit all"));
        inventory.setItem(UPGRADE_SLOT, upgradeIcon(guild));
        inventory.setItem(WITHDRAW_1K, named(Material.HOPPER, "§eWithdraw 1,000"));
        inventory.setItem(WITHDRAW_10K, named(Material.HOPPER, "§eWithdraw 10,000"));
        inventory.setItem(WITHDRAW_ALL, named(Material.DROPPER, "§eWithdraw all"));
        inventory.setItem(BACK_SLOT, named(Material.ARROW, "§eBack"));
        player.openInventory(inventory);
    }

    public void handle(Player player, Holder holder, InventoryClickEvent event) {
        Guild guild = guilds.byId(holder.guildId());
        if (guild == null) {
            player.closeInventory();
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.UNKNOWN) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            event.setCancelled(true);
            save(guild, event.getView().getTopInventory());
            player.closeInventory();
            if (guildMenu != null) {
                guildMenu.open(player);
            }
            return;
        }
        if (slot >= 45 && slot < 54) {
            event.setCancelled(true);
            save(guild, event.getView().getTopInventory());
            if (slot == DEPOSIT_1K) {
                guilds.depositCoins(player, 1_000L);
            } else if (slot == DEPOSIT_10K) {
                guilds.depositCoins(player, 10_000L);
            } else if (slot == DEPOSIT_ALL) {
                guilds.depositCoins(player, Math.max(1L, AetherionItemsAccess.coins(player)));
            } else if (slot == WITHDRAW_1K) {
                guilds.withdrawCoins(player, 1_000L);
            } else if (slot == WITHDRAW_10K) {
                guilds.withdrawCoins(player, 10_000L);
            } else if (slot == WITHDRAW_ALL) {
                guilds.withdrawCoins(player, Math.max(1L, guild.bankCoins()));
            } else if (slot == UPGRADE_SLOT) {
                guilds.upgradeBank(player);
            }
            open(player);
            return;
        }
        int slots = guild.bankSlots();
        if (slot >= 0 && slot < 45 && slot >= slots) {
            event.setCancelled(true);
            return;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        boolean canDeposit = rank != null && rank.canBankDeposit();
        boolean canWithdraw = rank != null && rank.canBankWithdraw();

        if (slot >= 54) {
            if (!event.isShiftClick()) {
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return;
            }
            event.setCancelled(true);
            if (!canDeposit) {
                player.sendMessage("§cYou cannot deposit items here.");
                return;
            }
            ItemStack moving = current.clone();
            int before = moving.getAmount();
            stow(guild, event.getView().getTopInventory(), moving);
            if (moving.getAmount() < before) {
                current.setAmount(moving.getAmount());
                if (current.getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                guilds.save();
            } else {
                player.sendMessage("§cThe guild chest is full.");
            }
            return;
        }

        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClick() == ClickType.NUMBER_KEY) {
            swapHotbar(player, guild, top, slot, event.getHotbarButton(), canDeposit, canWithdraw);
            return;
        }
        if (event.isShiftClick()) {
            ItemStack sitting = top.getItem(slot);
            if (sitting == null || sitting.getType().isAir()) {
                return;
            }
            if (!canWithdraw) {
                player.sendMessage("§cSoldiers and above can take items from the bank.");
                return;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(sitting.clone());
            if (leftover.isEmpty()) {
                top.setItem(slot, null);
                guild.bank()[slot] = null;
            } else {
                ItemStack remain = leftover.values().iterator().next();
                top.setItem(slot, remain);
                guild.bank()[slot] = remain.clone();
            }
            guilds.save();
            return;
        }

        ItemStack cursor = player.getItemOnCursor();
        ItemStack sitting = top.getItem(slot);
        boolean cursorHas = cursor != null && !cursor.getType().isAir();
        boolean sittingHas = sitting != null && !sitting.getType().isAir();
        if (cursorHas && sittingHas) {
            if (!canDeposit || !canWithdraw) {
                player.sendMessage("§cYou cannot swap items here.");
                return;
            }
            player.setItemOnCursor(sitting.clone());
            setSlot(guild, top, slot, cursor);
            guilds.save();
            return;
        }
        if (cursorHas) {
            if (!canDeposit) {
                player.sendMessage("§cYou cannot deposit items here.");
                return;
            }
            setSlot(guild, top, slot, cursor);
            player.setItemOnCursor(null);
            guilds.save();
            return;
        }
        if (sittingHas) {
            if (!canWithdraw) {
                player.sendMessage("§cSoldiers and above can take items from the bank.");
                return;
            }
            player.setItemOnCursor(sitting.clone());
            setSlot(guild, top, slot, null);
            guilds.save();
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (event.getRawSlots().stream().anyMatch(slot -> slot < 54)) {
            event.setCancelled(true);
        }
    }

    public void save(Guild guild, Inventory inventory) {
        if (guild == null || inventory == null) {
            return;
        }
        int slots = guild.bankSlots();
        for (int slot = 0; slot < Guild.BANK_MAX_SLOTS; slot++) {
            if (slot >= slots) {
                guild.bank()[slot] = null;
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            guild.bank()[slot] = item == null || item.getType().isAir() ? null : item.clone();
        }
        guilds.save();
    }

    private boolean stow(Guild guild, Inventory top, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return false;
        }
        int slots = guild.bankSlots();
        for (int i = 0; i < slots && stack.getAmount() > 0; i++) {
            ItemStack have = top.getItem(i);
            if (have == null || have.getType().isAir() || !have.isSimilar(stack) || have.getAmount() >= have.getMaxStackSize()) {
                continue;
            }
            int move = Math.min(have.getMaxStackSize() - have.getAmount(), stack.getAmount());
            have.setAmount(have.getAmount() + move);
            stack.setAmount(stack.getAmount() - move);
            setSlot(guild, top, i, have);
        }
        for (int i = 0; i < slots && stack.getAmount() > 0; i++) {
            ItemStack have = top.getItem(i);
            if (have != null && !have.getType().isAir()) {
                continue;
            }
            setSlot(guild, top, i, stack.clone());
            stack.setAmount(0);
            return true;
        }
        return stack.getAmount() <= 0;
    }

    private void swapHotbar(
            Player player,
            Guild guild,
            Inventory top,
            int slot,
            int hotbar,
            boolean canDeposit,
            boolean canWithdraw
    ) {
        if (hotbar < 0 || hotbar > 8) {
            return;
        }
        ItemStack hot = player.getInventory().getItem(hotbar);
        ItemStack sitting = top.getItem(slot);
        boolean hotHas = hot != null && !hot.getType().isAir();
        boolean sittingHas = sitting != null && !sitting.getType().isAir();
        if (hotHas && !canDeposit) {
            player.sendMessage("§cYou cannot deposit items here.");
            return;
        }
        if (sittingHas && !canWithdraw) {
            player.sendMessage("§cSoldiers and above can take items from the bank.");
            return;
        }
        player.getInventory().setItem(hotbar, sitting == null ? null : sitting.clone());
        setSlot(guild, top, slot, hot);
        guilds.save();
    }

    private void setSlot(Guild guild, Inventory top, int slot, ItemStack item) {
        ItemStack stored = item == null || item.getType().isAir() ? null : item.clone();
        top.setItem(slot, stored);
        guild.bank()[slot] = stored == null ? null : stored.clone();
    }

    private ItemStack upgradeIcon(Guild guild) {
        if (!guild.canUpgradeBank()) {
            return named(Material.BEDROCK, "§cMax Bank", "§7Coin cap: §6" + GuildFormat.compact(guild.coinCap()));
        }
        BankTiers.UpgradeCost cost = BankTiers.upgradeCost(guild.bankLevel());
        List<String> lore = new ArrayList<>();
        lore.add("§7Next cap: §6" + GuildFormat.compact(BankTiers.coinCap(guild.bankLevel() + 1)));
        lore.add("§7Next slots: §f" + BankTiers.itemSlots(guild.bankLevel() + 1));
        lore.add("§7Cost: §f" + cost.label());
        lore.add("§eClick §7(Mayor+)");
        return named(Material.EXPERIENCE_BOTTLE, "§eUpgrade Bank", lore.toArray(String[]::new));
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
        private final UUID guildId;
        private Inventory inventory;

        public Holder(UUID guildId) {
            this.guildId = guildId;
        }

        public UUID guildId() {
            return guildId;
        }

        public void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
