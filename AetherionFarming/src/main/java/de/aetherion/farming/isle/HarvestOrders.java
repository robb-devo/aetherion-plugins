package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;
import de.aetherion.items.economy.CompressedResource;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Harvest Orders at the Market Barn — three rotating jobs per farmer from people on (and off)
 * the isle. Raw crops, compressed crops or a prize crop, paid well above trader value plus
 * Farming XP. A finished slot restocks after a short break; the board rerolls free every
 * half hour. Market Day raises the coin payout.
 */
public final class HarvestOrders {

    public enum Form {
        RAW,
        COMPRESSED,
        PRIZE
    }

    public record Order(IsleCrop crop, Form form, int amount, long coins, int xp, String client) {

        public String want() {
            return switch (form) {
                case RAW -> amount + " " + crop.display();
                case COMPRESSED -> amount + " Compressed " + crop.display();
                case PRIZE -> amount + " " + crop.prizeName();
            };
        }

        Material icon() {
            return form == Form.PRIZE ? Material.GOLDEN_CARROT : crop.yield();
        }

        void write(ConfigurationSection section) {
            section.set("crop", crop.id());
            section.set("form", form.name());
            section.set("amount", amount);
            section.set("coins", coins);
            section.set("xp", xp);
            section.set("client", client);
        }

        static Order read(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            IsleCrop crop = IsleCrop.byId(section.getString("crop"));
            Form form;
            try {
                form = Form.valueOf(section.getString("form", "RAW").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            if (crop == null) {
                return null;
            }
            return new Order(crop, form, Math.max(1, section.getInt("amount", 1)), Math.max(0L, section.getLong("coins")),
                    Math.max(0, section.getInt("xp")), section.getString("client", "A neighbour"));
        }
    }

    private static final long RESTOCK_MS = 3L * 60_000L;
    private static final long FREE_REROLL_MS = 30L * 60_000L;
    private static final long REROLL_COST = 250L;
    private static final String[] CLIENTS = {
            "The Oven House",
            "Heron Lake anglers",
            "Southfield Hamlet",
            "The Hive Lodge",
            "A hub merchant",
            "The Old Granary",
            "Harbour kitchens",
            "The Field Warden",
            "A travelling cook",
            "The Mill Keeper"
    };

    private final FarmIsle isle;

    HarvestOrders(FarmIsle isle) {
        this.isle = isle;
    }

    /** Fills empty, restocked slots. Call before showing the board. */
    public void refresh(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int slot = 0; slot < IsleProfiles.ORDER_SLOTS; slot++) {
            if (profile.orders[slot] == null && profile.slotReadyAt[slot] <= now) {
                profile.orders[slot] = generate(player);
                profile.slotReadyAt[slot] = 0L;
                changed = true;
            }
        }
        if (changed) {
            isle.profiles().markDirty();
        }
    }

    public Order order(Player player, int slot) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        return slot < 0 || slot >= IsleProfiles.ORDER_SLOTS ? null : profile.orders[slot];
    }

    public long restockSeconds(Player player, int slot) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        return Math.max(0L, (profile.slotReadyAt[slot] - System.currentTimeMillis()) / 1000L);
    }

    public long freeRerollSeconds(Player player) {
        return Math.max(0L, (isle.profiles().of(player).freeRerollAt - System.currentTimeMillis()) / 1000L);
    }

    public int readyCount(Player player) {
        int ready = 0;
        for (int slot = 0; slot < IsleProfiles.ORDER_SLOTS; slot++) {
            Order order = order(player, slot);
            if (order != null && have(player, order) >= order.amount()) {
                ready++;
            }
        }
        return ready;
    }

    /** How many of the order's goods the player carries right now. */
    public int have(Player player, Order order) {
        PlayerInventory inventory = player.getInventory();
        return switch (order.form()) {
            case RAW -> OrderItems.countPlain(inventory, order.crop().yield());
            case COMPRESSED -> {
                CompressedResource resource = compressed(order.crop());
                yield resource == null ? 0 : OrderItems.countById(inventory, resource.compressedId());
            }
            case PRIZE -> OrderItems.countPrizes(inventory, isle.prizes(), order.crop());
        };
    }

    public long payout(Player player, Order order) {
        double marketDay = Math.min(0.6d, 0.15d * FarmingSkills.scale(player, FarmingSkills.MARKET_DAY));
        return Math.round(order.coins() * (1.0d + marketDay));
    }

    public boolean deliver(Player player, int slot) {
        Order order = order(player, slot);
        if (order == null) {
            return false;
        }
        if (have(player, order) < order.amount()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            IsleText.bar(player, "§cNot enough yet §8· §7" + have(player, order) + "/" + order.amount() + " " + order.crop().display());
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        switch (order.form()) {
            case RAW -> OrderItems.takePlain(inventory, order.crop().yield(), order.amount());
            case COMPRESSED -> OrderItems.takeById(inventory, compressed(order.crop()).compressedId(), order.amount());
            case PRIZE -> {
                for (int i = 0; i < order.amount(); i++) {
                    int prizeSlot = OrderItems.firstPrize(inventory, isle.prizes(), order.crop());
                    ItemStack prize = prizeSlot < 0 ? null : inventory.getItem(prizeSlot);
                    if (prize != null) {
                        prize.setAmount(prize.getAmount() - 1);
                        inventory.setItem(prizeSlot, prize.getAmount() <= 0 ? null : prize);
                    }
                }
            }
        }
        long coins = payout(player, order);
        FarmingSkills.coins(player, coins);
        FarmingSkills.bonus(player, order.xp());
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.orders[slot] = null;
        profile.slotReadyAt[slot] = System.currentTimeMillis() + RESTOCK_MS;
        profile.ordersDone++;
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.6f);
        String bonus = coins > order.coins() ? " §8(Market Day +" + IsleText.coins(coins - order.coins()) + ")" : "";
        player.sendMessage("§6Hattie §8» §fDelivered to " + order.client() + ". §6+" + IsleText.coins(coins) + " coins"
                + bonus + " §8· §a+" + order.xp() + " Farming XP");
        return true;
    }

    /** Free every 30 min, otherwise 250 coins. Only empty-able slots are swapped. */
    public boolean reroll(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        long now = System.currentTimeMillis();
        boolean free = profile.freeRerollAt <= now;
        if (!free && !FarmingSkills.takeCoins(player, REROLL_COST)) {
            IsleText.bar(player, "§cA fresh board costs §6" + REROLL_COST + " coins §cuntil the free reroll.");
            return false;
        }
        if (free) {
            profile.freeRerollAt = now + FREE_REROLL_MS;
        }
        for (int slot = 0; slot < IsleProfiles.ORDER_SLOTS; slot++) {
            profile.orders[slot] = generate(player);
            profile.slotReadyAt[slot] = 0L;
        }
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.0f);
        IsleText.bar(player, free ? "§aFresh orders pinned up." : "§aFresh orders pinned up §8(-" + REROLL_COST + " coins)");
        return true;
    }

    /** DEV: clear cooldowns and roll a new board. */
    void resetBoard(Player player) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        profile.freeRerollAt = 0L;
        for (int slot = 0; slot < IsleProfiles.ORDER_SLOTS; slot++) {
            profile.orders[slot] = null;
            profile.slotReadyAt[slot] = 0L;
        }
        refresh(player);
    }

    /** Sells every prize crop in the inventory at the Market Barn price. */
    public long sellPrizes(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        long total = 0L;
        int sold = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            long value = isle.prizes().valueOf(contents[slot]);
            if (value <= 0L) {
                continue;
            }
            total += value;
            sold += contents[slot].getAmount();
            contents[slot] = null;
        }
        if (sold == 0) {
            IsleText.bar(player, "§7No prize crops to sell.");
            return 0L;
        }
        inventory.setStorageContents(contents);
        FarmingSkills.coins(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        player.sendMessage("§6Hattie §8» §fSold " + sold + " prize crop" + (sold == 1 ? "" : "s") + " for §6"
                + IsleText.coins(total) + " coins§f. Lovely specimens.");
        return total;
    }

    // ------------------------------------------------------------------ generation

    Order generate(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<IsleCrop> pool = new ArrayList<>();
        for (IsleCrop crop : IsleCrop.values()) {
            if (crop.onIsle()) {
                pool.add(crop);
            }
        }
        IsleCrop crop = pool.get(rng.nextInt(pool.size()));
        int level = FarmingSkills.level(player);
        double scale = 1.0d + Math.min(2.0d, level / 50.0d);
        double roll = rng.nextDouble();
        Form form;
        if (roll < 0.12d && level >= 10) {
            form = Form.PRIZE;
        } else if (roll < 0.35d && compressed(crop) != null) {
            form = Form.COMPRESSED;
        } else {
            form = Form.RAW;
        }
        String client = CLIENTS[rng.nextInt(CLIENTS.length)];
        return switch (form) {
            case RAW -> {
                int amount = Math.max(32, (int) Math.round((64 + rng.nextInt(193)) * scale / 16.0d) * 16);
                long coins = Math.round(amount * (2.0d + rng.nextDouble() * 0.6d));
                yield new Order(crop, form, amount, coins, Math.max(12, amount / 3), client);
            }
            case COMPRESSED -> {
                int amount = Math.max(1, (int) Math.round((1 + rng.nextInt(3)) * scale));
                long coins = Math.round(amount * 179L * (1.8d + rng.nextDouble() * 0.4d));
                yield new Order(crop, form, amount, coins, 60 * amount, client);
            }
            case PRIZE -> new Order(crop, form, 1, 700L + rng.nextInt(301), 150, client);
        };
    }

    private static CompressedResource compressed(IsleCrop crop) {
        try {
            CompressedResource resource = CompressedResource.fromDrop(crop.yield());
            return resource != null && resource.input() == crop.yield() ? resource : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }
}
