package de.aetherion.mining.isle;

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
 * Foreman Contracts at the Contract Office — three rotating jobs per miner, paid well above
 * trader value plus Mining XP. Two kinds are hand-ins (raw ore, compressed ore, a specimen);
 * two are <b>shifts</b> that fill while you work: a Deep shift (mine below the Deep Works line on
 * the isle) and a Veins shift (mine in The Veins — the annex pays the island's bills).
 * A finished slot restocks after a short break; the board rerolls free every half hour.
 * Union Card raises the coin payout.
 */
public final class ForemanContracts {

    public enum Form {
        RAW,
        COMPRESSED,
        SPECIMEN,
        DEEP,
        VEINS;

        boolean shift() {
            return this == DEEP || this == VEINS;
        }
    }

    public record Contract(IsleOre ore, Form form, int amount, long coins, int xp, String client) {

        public String want() {
            return switch (form) {
                case RAW -> amount + " " + ore.display();
                case COMPRESSED -> amount + " Compressed " + ore.display();
                case SPECIMEN -> "1 " + ore.crystalName() + " §8(any grade)";
                case DEEP -> "Mine " + amount + " " + ore.display() + " below the Deep Works line";
                case VEINS -> "Mine " + amount + " ore in The Veins";
            };
        }

        Material icon() {
            return switch (form) {
                case SPECIMEN -> ore.showcase();
                case DEEP -> Material.LANTERN;
                case VEINS -> Material.CHAIN;
                default -> ore.resource();
            };
        }

        void write(ConfigurationSection section) {
            section.set("ore", ore.id());
            section.set("form", form.name());
            section.set("amount", amount);
            section.set("coins", coins);
            section.set("xp", xp);
            section.set("client", client);
        }

        static Contract read(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            IsleOre ore = IsleOre.byId(section.getString("ore"));
            Form form;
            try {
                form = Form.valueOf(section.getString("form", "RAW").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            if (ore == null) {
                return null;
            }
            return new Contract(ore, form, Math.max(1, section.getInt("amount", 1)), Math.max(0L, section.getLong("coins")),
                    Math.max(0, section.getInt("xp")), section.getString("client", "The Foreman"));
        }
    }

    private static final long RESTOCK_MS = 3L * 60_000L;
    private static final long FREE_REROLL_MS = 30L * 60_000L;
    private static final long REROLL_COST = 400L;
    private static final String[] CLIENTS = {
            "The Smelter",
            "Harbour Forge",
            "The Assay Office",
            "The Lamplighters",
            "A hub blacksmith",
            "The Veins Foreman",
            "Cart Crew",
            "The Eldervale Mint",
            "Old Wick's lantern works",
            "A nervous jeweller",
            "The Hearth",
            "Somebody's uncle (paid in advance)"
    };

    private final MineIsle isle;

    ForemanContracts(MineIsle isle) {
        this.isle = isle;
    }

    /** Fills empty, restocked slots. Call before showing the board. */
    public void refresh(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            if (profile.contracts[slot] == null && profile.slotReadyAt[slot] <= now) {
                profile.contracts[slot] = generate(player);
                profile.slotReadyAt[slot] = 0L;
                profile.progress[slot] = 0;
                changed = true;
            }
        }
        if (changed) {
            isle.profiles().markDirty();
        }
    }

    public Contract contract(Player player, int slot) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        return slot < 0 || slot >= MineProfiles.CONTRACT_SLOTS ? null : profile.contracts[slot];
    }

    public long restockSeconds(Player player, int slot) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        return Math.max(0L, (profile.slotReadyAt[slot] - System.currentTimeMillis()) / 1000L);
    }

    public long freeRerollSeconds(Player player) {
        return Math.max(0L, (isle.profiles().of(player).freeRerollAt - System.currentTimeMillis()) / 1000L);
    }

    public int readyCount(Player player) {
        int ready = 0;
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            Contract contract = contract(player, slot);
            if (contract != null && have(player, slot) >= contract.amount()) {
                ready++;
            }
        }
        return ready;
    }

    /** Goods carried (hand-ins) or blocks mined so far (shifts). */
    public int have(Player player, int slot) {
        Contract contract = contract(player, slot);
        if (contract == null) {
            return 0;
        }
        if (contract.form().shift()) {
            return isle.profiles().of(player).progress[slot];
        }
        PlayerInventory inventory = player.getInventory();
        return switch (contract.form()) {
            case RAW -> MineItems.countPlain(inventory, contract.ore().resource());
            case COMPRESSED -> {
                String id = compressedId(contract.ore());
                yield id == null ? 0 : MineItems.countById(inventory, id);
            }
            case SPECIMEN -> MineItems.countSpecimens(inventory, isle.crystals(), contract.ore());
            default -> 0;
        };
    }

    public long payout(Player player, Contract contract) {
        double union = Math.min(0.6d, 0.15d * MineSkills.scale(player, MineSkills.UNION_CARD));
        return Math.round(contract.coins() * (1.0d + union));
    }

    /** A block was mined somewhere — shifts count it. */
    void onMined(Player player, IsleOre ore, boolean veins, MineDistricts.Band band) {
        if (ore == null || ore == IsleOre.STONE) {
            return;
        }
        MineProfiles.Profile profile = isle.profiles().of(player);
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            Contract contract = profile.contracts[slot];
            if (contract == null || !contract.form().shift() || profile.progress[slot] >= contract.amount()) {
                continue;
            }
            boolean counts = contract.form() == Form.VEINS
                    ? veins
                    : !veins && band.deep() && contract.ore() == ore;
            if (!counts) {
                continue;
            }
            profile.progress[slot]++;
            isle.profiles().markDirty();
            if (profile.progress[slot] >= contract.amount()) {
                MineText.bar(player, "§6✔ Shift done: §f" + contract.want() + " §7— hand it in with Otto.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.6f);
            } else if (profile.progress[slot] % 25 == 0) {
                MineText.bar(player, "§7Shift §f" + profile.progress[slot] + "§7/§f" + contract.amount() + " §8· " + contract.want());
            }
        }
    }

    public boolean deliver(Player player, int slot) {
        Contract contract = contract(player, slot);
        if (contract == null) {
            return false;
        }
        int have = have(player, slot);
        if (have < contract.amount()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
            MineText.bar(player, "§cNot yet §8· §7" + have + "/" + contract.amount() + " §8· " + contract.want());
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        switch (contract.form()) {
            case RAW -> MineItems.takePlain(inventory, contract.ore().resource(), contract.amount());
            case COMPRESSED -> MineItems.takeById(inventory, compressedId(contract.ore()), contract.amount());
            case SPECIMEN -> MineItems.takeSpecimen(inventory, isle.crystals(), contract.ore());
            default -> {
            }
        }
        long coins = payout(player, contract);
        MineSkills.coins(player, coins);
        MineSkills.bonus(player, contract.xp());
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.contracts[slot] = null;
        profile.progress[slot] = 0;
        profile.slotReadyAt[slot] = System.currentTimeMillis() + RESTOCK_MS;
        profile.contractsDone++;
        isle.profiles().markDirty();
        isle.forge().addRep(player, contract.form().shift() ? 12L : 8L, "Contract for " + contract.client());
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        String bonus = coins > contract.coins() ? " §8(Union Card +" + MineText.coins(coins - contract.coins()) + ")" : "";
        isle.cast().say(player, MineRole.CLERK, "Stamped and filed for " + contract.client() + ". §6+"
                + MineText.coins(coins) + " coins" + bonus + " §8· §a+" + contract.xp() + " Mining XP");
        return true;
    }

    /** Free every 30 min, otherwise 400 coins. */
    public boolean reroll(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        long now = System.currentTimeMillis();
        boolean free = profile.freeRerollAt <= now;
        if (!free && !MineSkills.takeCoins(player, REROLL_COST)) {
            MineText.bar(player, "§cA fresh board costs §6" + REROLL_COST + " coins §cuntil the free reroll.");
            return false;
        }
        if (free) {
            profile.freeRerollAt = now + FREE_REROLL_MS;
        }
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            profile.contracts[slot] = generate(player);
            profile.slotReadyAt[slot] = 0L;
            profile.progress[slot] = 0;
        }
        isle.profiles().markDirty();
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.0f);
        MineText.bar(player, free ? "§aFresh contracts pinned up." : "§aFresh contracts pinned up §8(-" + REROLL_COST + " coins)");
        return true;
    }

    /** DEV: clear cooldowns and roll a new board. */
    void resetBoard(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.freeRerollAt = 0L;
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            profile.contracts[slot] = null;
            profile.slotReadyAt[slot] = 0L;
            profile.progress[slot] = 0;
        }
        refresh(player);
    }

    /** DEV: complete every shift on the board. */
    void fillShifts(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        for (int slot = 0; slot < MineProfiles.CONTRACT_SLOTS; slot++) {
            Contract contract = profile.contracts[slot];
            if (contract != null && contract.form().shift()) {
                profile.progress[slot] = contract.amount();
            }
        }
        isle.profiles().markDirty();
    }

    /** Sells every specimen in the inventory at the Assayer's price. */
    public long sellSpecimens(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        long total = 0L;
        int sold = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            long value = isle.crystals().valueOf(contents[slot]);
            if (value <= 0L) {
                continue;
            }
            total += value;
            sold += contents[slot].getAmount();
            contents[slot] = null;
        }
        if (sold == 0) {
            MineText.bar(player, "§7No specimens to sell.");
            return 0L;
        }
        inventory.setStorageContents(contents);
        MineSkills.coins(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        isle.cast().say(player, MineRole.ASSAYER, "Weighed, logged, paid: " + sold + " specimen" + (sold == 1 ? "" : "s")
                + " for §6" + MineText.coins(total) + " coins§f. The cabinet keeps its record either way.");
        return total;
    }

    // ------------------------------------------------------------------ generation

    Contract generate(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int level = MineSkills.level(player);
        List<IsleOre> pool = new ArrayList<>(List.of(IsleOre.COAL, IsleOre.COPPER, IsleOre.IRON));
        if (level >= 15) {
            pool.addAll(List.of(IsleOre.REDSTONE, IsleOre.LAPIS));
        }
        if (level >= 25) {
            pool.add(IsleOre.GOLD);
        }
        if (level >= 40) {
            pool.addAll(List.of(IsleOre.DIAMOND, IsleOre.AMETHYST));
        }
        if (level >= 60) {
            pool.add(IsleOre.EMERALD);
        }
        IsleOre ore = pool.get(rng.nextInt(pool.size()));
        double scale = 1.0d + Math.min(2.0d, level / 50.0d);
        double roll = rng.nextDouble();
        Form form;
        if (roll < 0.10d && level >= 15) {
            form = Form.SPECIMEN;
        } else if (roll < 0.25d && level >= 30) {
            form = Form.VEINS;
        } else if (roll < 0.43d) {
            form = Form.DEEP;
        } else if (roll < 0.63d && compressedId(ore) != null) {
            form = Form.COMPRESSED;
        } else {
            form = Form.RAW;
        }
        String client = CLIENTS[rng.nextInt(CLIENTS.length)];
        long unit = Math.max(1L, ore.unitValue());
        return switch (form) {
            case RAW -> {
                int base = ore.rare() ? 8 + rng.nextInt(25) : 64 + rng.nextInt(193);
                int amount = Math.max(ore.rare() ? 8 : 32, (int) Math.round(base * scale / 8.0d) * 8);
                long coins = Math.round(amount * unit * (2.0d + rng.nextDouble() * 0.6d));
                yield new Contract(ore, form, amount, coins, Math.max(12, amount / (ore.rare() ? 1 : 3)), client);
            }
            case COMPRESSED -> {
                int amount = Math.max(1, (int) Math.round((1 + rng.nextInt(3)) * scale));
                long coins = Math.round(amount * unit * 179L * (1.8d + rng.nextDouble() * 0.4d));
                yield new Contract(ore, form, amount, coins, 60 * amount, client);
            }
            case SPECIMEN -> {
                IsleOre crystal = ore.hasCrystal() ? ore : IsleOre.IRON;
                long coins = CrystalFinds.value(crystal, Grade.ROUGH, (crystal.minCarat() + crystal.maxCarat()) / 2.0d) * 2L
                        + 400L + rng.nextInt(301);
                yield new Contract(crystal, form, 1, coins, 180, client);
            }
            case DEEP -> {
                int base = ore.rare() ? 10 + rng.nextInt(20) : 60 + rng.nextInt(90);
                int amount = Math.max(ore.rare() ? 8 : 40, (int) Math.round(base * scale / 5.0d) * 5);
                long coins = Math.round(amount * Math.max(2L, unit) * (2.8d + rng.nextDouble() * 0.6d));
                yield new Contract(ore, form, amount, coins, amount * 2, client);
            }
            case VEINS -> {
                int amount = Math.max(80, (int) Math.round((100 + rng.nextInt(151)) * scale / 10.0d) * 10);
                long coins = Math.round(amount * (6.0d + rng.nextDouble() * 2.0d));
                yield new Contract(ore, form, amount, coins, amount * 2, "The Veins Foreman");
            }
        };
    }

    static String compressedId(IsleOre ore) {
        if (ore == null || ore.compressedKey() == null) {
            return null;
        }
        try {
            return CompressedResource.valueOf(ore.compressedKey().toUpperCase(Locale.ROOT)).compressedId();
        } catch (IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }
}
