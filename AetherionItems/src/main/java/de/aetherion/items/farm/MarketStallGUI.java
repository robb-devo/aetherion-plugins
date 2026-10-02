package de.aetherion.items.farm;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.item.FarmingHoeProgress;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Seed stall on the Farm Isle — trades Compacted crops for planting stock.
 * Flavor service only; refining stays with the Mill Keeper / Root Cellar.
 */
public final class MarketStallGUI implements InventoryHolder {

    public static final String TITLE = "§8Seed Stall";
    public static final int SIZE = 54;
    /** Stacks handed out per Compacted crop. */
    public static final int STACKS_PER_TRADE = 8;
    /** Modest hoe XP for one Compacted farm crop (expensive sink). */
    public static final int HOE_XP_PER_TRADE = 80;
    public static final String HOE_XP_ACTION = "hoe-xp";

    private static final int[] OFFER_SLOTS = {20, 21, 22, 23, 24};
    private static final int HOE_XP_SLOT = 31;
    private static final CompressedResource[] HOE_XP_COSTS = {
            CompressedResource.WHEAT,
            CompressedResource.CARROT,
            CompressedResource.POTATO,
            CompressedResource.BEETROOT,
            CompressedResource.SUGAR_CANE
    };

    /** Seed stock, in display order. */
    public enum Offer {
        WHEAT("wheat", CompressedResource.WHEAT, Material.WHEAT_SEEDS, "§eWheat Seeds"),
        CARROT("carrot", CompressedResource.CARROT, Material.CARROT, "§6Carrots"),
        POTATO("potato", CompressedResource.POTATO, Material.POTATO, "§ePotatoes"),
        BEETROOT("beetroot", CompressedResource.BEETROOT, Material.BEETROOT_SEEDS, "§cBeetroot Seeds"),
        SUGAR_CANE("sugar_cane", CompressedResource.SUGAR_CANE, Material.SUGAR_CANE, "§aSugar Cane");

        private final String id;
        private final CompressedResource cost;
        private final Material seed;
        private final String title;

        Offer(String id, CompressedResource cost, Material seed, String title) {
            this.id = id;
            this.cost = cost;
            this.seed = seed;
            this.title = title;
        }

        public String id() {
            return id;
        }

        public CompressedResource cost() {
            return cost;
        }

        public Material seed() {
            return seed;
        }

        public String title() {
            return title;
        }

        public static Offer byId(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String id = raw.toLowerCase(Locale.ROOT);
            for (Offer offer : values()) {
                if (offer.id.equals(id)) {
                    return offer;
                }
            }
            return null;
        }
    }

    private final Inventory inventory;

    public MarketStallGUI(Player viewer) {
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
        paint(viewer);
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        player.openInventory(new MarketStallGUI(player).getInventory());
    }

    /** Buys one trade worth of seeds; false when the Compacted crop is missing. */
    public static boolean buy(Player player, Offer offer) {
        if (player == null || offer == null) {
            return false;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ItemManager manager = plugin == null ? null : plugin.getItemManager();
        ItemStack price = offer.cost().compacted();
        if (count(player.getInventory(), price, manager) < 1) {
            return false;
        }
        if (!consume(player.getInventory(), price, 1, manager)) {
            return false;
        }
        for (int i = 0; i < STACKS_PER_TRADE; i++) {
            ItemStack seeds = new ItemStack(offer.seed(), 64);
            player.getInventory().addItem(seeds).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        return true;
    }

    /**
     * Spends one Compacted farm crop to grant a small chunk of XP to the
     * farming hoe in the player's main hand.
     *
     * @return null on success, otherwise a short failure reason for chat
     */
    public static String buyHoeXp(Player player) {
        if (player == null) {
            return "§cNo player.";
        }
        ItemStack hoe = player.getInventory().getItemInMainHand();
        if (!FarmingHoeProgress.isHoe(hoe)) {
            return "§cHold a farming hoe in your main hand.";
        }
        int level = FarmingHoeProgress.level(hoe);
        int cap = FarmingHoeProgress.maxLevel(hoe);
        if (level >= cap) {
            return "§cThat hoe is already max level.";
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ItemManager manager = plugin == null ? null : plugin.getItemManager();
        CompressedResource paid = findAffordableCompacted(player.getInventory(), manager);
        if (paid == null) {
            return "§cNeed 1 Compacted farm crop (wheat / carrot / potato / beet / cane).";
        }
        if (!consume(player.getInventory(), paid.compacted(), 1, manager)) {
            return "§cCould not take the Compacted crop.";
        }
        FarmingHoeProgress.grant(player, hoe, manager, HOE_XP_PER_TRADE);
        return null;
    }

    private void paint(Player viewer) {
        ItemStack glass = GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, glass.clone());
        }
        inventory.setItem(4, GuiItems.named(
                Material.OAK_SIGN,
                "§fSeed Stall",
                "§7Planting stock for the shared fields.",
                "§7Paid in Compacted crops."
        ));
        AetherionItems plugin = AetherionItems.getInstance();
        ItemManager manager = plugin == null ? null : plugin.getItemManager();
        Offer[] offers = Offer.values();
        for (int i = 0; i < offers.length && i < OFFER_SLOTS.length; i++) {
            inventory.setItem(OFFER_SLOTS[i], offerButton(offers[i], viewer, manager));
        }
        inventory.setItem(HOE_XP_SLOT, hoeXpButton(viewer, manager));
    }

    private static ItemStack hoeXpButton(Player viewer, ItemManager manager) {
        ItemStack icon = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Furrow Coaching");
            List<String> lore = new ArrayList<>();
            lore.add("§7+" + HOE_XP_PER_TRADE + " Hoe XP on the tool");
            lore.add("§7held in your main hand.");
            lore.add("");
            lore.add("§7Costs §fone Compacted§7 farm crop");
            lore.add("§8(wheat / carrot / potato / beet / cane).");
            lore.add("");
            CompressedResource have = findAffordableCompacted(viewer.getInventory(), manager);
            ItemStack hoe = viewer.getInventory().getItemInMainHand();
            boolean holding = FarmingHoeProgress.isHoe(hoe);
            boolean maxed = holding && FarmingHoeProgress.level(hoe) >= FarmingHoeProgress.maxLevel(hoe);
            if (!holding) {
                lore.add("§cHold a farming hoe first.");
            } else if (maxed) {
                lore.add("§cHoe already max level.");
            } else if (have == null) {
                lore.add("§cMissing Compacted farm crop.");
            } else {
                lore.add("§aReady §8· §7paying with " + label(have.compacted()));
                lore.add("§aClick to coach");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    ItemKeys.devAction(),
                    PersistentDataType.STRING,
                    "seedstall:" + HOE_XP_ACTION
            );
            GuiItems.hideVanilla(meta);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static CompressedResource findAffordableCompacted(PlayerInventory inv, ItemManager manager) {
        for (CompressedResource resource : HOE_XP_COSTS) {
            if (count(inv, resource.compacted(), manager) >= 1) {
                return resource;
            }
        }
        return null;
    }

    private static ItemStack offerButton(Offer offer, Player viewer, ItemManager manager) {
        ItemStack icon = new ItemStack(offer.seed());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(offer.title());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + (STACKS_PER_TRADE * 64) + " × " + strip(offer.title()) + ".");
            lore.add("");
            lore.add("§7Costs:");
            ItemStack price = offer.cost().compacted();
            int have = count(viewer.getInventory(), price, manager);
            lore.add((have >= 1 ? "§a" : "§c") + label(price) + " §8· §f" + have + "§7/§f1");
            lore.add("");
            lore.add(have >= 1 ? "§aClick to buy" : "§cMissing Compacted crop");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    ItemKeys.devAction(),
                    PersistentDataType.STRING,
                    "seedstall:" + offer.id()
            );
            GuiItems.hideVanilla(meta);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static String strip(String title) {
        return title.replaceAll("§.", "");
    }

    private static String label(ItemStack sample) {
        if (sample == null) {
            return "Item";
        }
        if (sample.hasItemMeta() && sample.getItemMeta().hasDisplayName()) {
            return sample.getItemMeta().getDisplayName();
        }
        return sample.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static int count(PlayerInventory inv, ItemStack sample, ItemManager manager) {
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (matches(stack, sample, manager)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static boolean consume(PlayerInventory inv, ItemStack sample, int amount, ItemManager manager) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, sample, manager)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            left -= take;
        }
        inv.setContents(contents);
        return left <= 0;
    }

    private static boolean matches(ItemStack actual, ItemStack sample, ItemManager manager) {
        if (actual == null || sample == null || actual.getType().isAir()) {
            return false;
        }
        if (manager != null) {
            String expect = manager.getItemId(sample);
            String got = manager.getItemId(actual);
            if (expect != null) {
                return expect.equalsIgnoreCase(got);
            }
        }
        return actual.getType() == sample.getType();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
