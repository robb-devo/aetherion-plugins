package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything Foraging Eldervale hands out: Crown Finds (one per district, four grades, a weight),
 * the Woodwright's consumables, and plain-log counting for orders and the bench. Finds and
 * consumables carry the Items registry id (rarity-framed tooltips) plus our own tags.
 */
public final class ForageItems {

    /** One find per district — what a felled crown sometimes lets go of. */
    public enum Find {
        FROSTCONE(Grove.FROSTPINE, Material.PITCHER_POD, "Frostcone", "§b", 60, 30, 180,
                "A spruce cone rimed white. It never thaws."),
        PETAL_SILK(Grove.BLOSSOM, Material.PINK_PETALS, "Petal Silk", "§d", 70, 5, 40,
                "Cherry petals woven by the wind itself."),
        SUNRESIN(Grove.SUNSCAR, Material.HONEYCOMB, "Sunresin", "§6", 65, 40, 260,
                "Acacia sap baked to glass on the mesa."),
        ELDER_ACORN(Grove.ELDERWOOD, Material.COCOA_BEANS, "Elder Acorn", "§a", 50, 8, 60,
                "An acorn the squirrels argued over."),
        GLOAMCAP(Grove.GLOAMWOOD, Material.WARPED_FUNGUS, "Gloamcap", "§3", 75, 20, 140,
                "A cap that glows under the shelf. Warm to the touch."),
        BRINE_PEARL(Grove.BRINEFALL, Material.NAUTILUS_SHELL, "Brine Pearl", "§3", 80, 4, 30,
                "Grown in a mangrove root where the falls land."),
        CANOPY_AMBER(Grove.CANOPY_CROWN, Material.GOLD_NUGGET, "Canopy Amber", "§e", 90, 10, 120,
                "Old jungle sap. Something small is still inside.");

        public final Grove grove;
        public final Material icon;
        public final String display;
        public final String color;
        public final int baseValue;
        public final int minGrams;
        public final int maxGrams;
        public final String flavor;

        Find(Grove grove, Material icon, String display, String color, int baseValue, int minGrams, int maxGrams, String flavor) {
            this.grove = grove;
            this.icon = icon;
            this.display = display;
            this.color = color;
            this.baseValue = baseValue;
            this.minGrams = minGrams;
            this.maxGrams = maxGrams;
            this.flavor = flavor;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String colored() {
            return color + display;
        }

        public static Find of(Grove grove) {
            for (Find find : values()) {
                if (find.grove == grove) {
                    return find;
                }
            }
            return ELDER_ACORN;
        }

        public static Find byId(String raw) {
            if (raw == null) {
                return null;
            }
            for (Find find : values()) {
                if (find.id().equalsIgnoreCase(raw) || find.name().equalsIgnoreCase(raw)) {
                    return find;
                }
            }
            return null;
        }
    }

    public enum Grade {
        ROUGH("Rough", "§7", "UNCOMMON", 1, 0.00d, 0.50d),
        FINE("Fine", "§a", "RARE", 3, 0.30d, 0.75d),
        PRISTINE("Pristine", "§b", "EPIC", 10, 0.55d, 0.95d),
        HEARTSONG("Heartsong", "§6", "LEGENDARY", 40, 0.85d, 1.30d);

        public final String display;
        public final String color;
        public final String rarity;
        public final int valueMult;
        final double lo;
        final double hi;

        Grade(String display, String color, String rarity, int valueMult, double lo, double hi) {
            this.display = display;
            this.color = color;
            this.rarity = rarity;
            this.valueMult = valueMult;
            this.lo = lo;
            this.hi = hi;
        }

        public String colored() {
            return color + display;
        }

        public boolean atLeast(Grade other) {
            return ordinal() >= other.ordinal();
        }

        public static Grade byName(String raw) {
            if (raw == null) {
                return null;
            }
            for (Grade grade : values()) {
                if (grade.name().equalsIgnoreCase(raw) || grade.display.equalsIgnoreCase(raw)) {
                    return grade;
                }
            }
            return null;
        }
    }

    /** Woodwright consumables. */
    public enum Tonic {
        SAP_LURE("sap_lure", Material.HONEY_BOTTLE, "§6Sap Lure", "RARE",
                "§7Right-click a living tree on the isle.",
                "§7For 3 minutes, fells within 6 blocks roll",
                "§7Crown Finds §f×3 §7and district extras §f×2§7."),
        CROWN_SHAKER("crown_shaker", Material.STICK, "§eCrown Shaker", "RARE",
                "§7Right-click to load a charge.",
                "§7Your next fell on the isle §fguarantees",
                "§7a Crown Find."),
        HEARTWOOD_INCENSE("heartwood_incense", Material.BLAZE_POWDER, "§dHeartwood Incense", "EPIC",
                "§7Right-click to light it.",
                "§7For 10 minutes on the isle, heartwood",
                "§7turns up §ftwice §7as often.");

        public final String id;
        public final Material icon;
        public final String display;
        public final String rarity;
        public final List<String> lore;

        Tonic(String id, Material icon, String display, String rarity, String... lore) {
            this.id = id;
            this.icon = icon;
            this.display = display;
            this.rarity = rarity;
            this.lore = List.of(lore);
        }

        public static Tonic byId(String raw) {
            for (Tonic tonic : values()) {
                if (tonic.id.equalsIgnoreCase(raw) || tonic.name().equalsIgnoreCase(raw)) {
                    return tonic;
                }
            }
            return null;
        }
    }

    public record FindData(Find find, Grade grade, int grams) {
        public int value() {
            double weightFactor = find.maxGrams <= find.minGrams ? 1.0d
                    : 0.8d + 0.4d * (grams - find.minGrams) / (double) (find.maxGrams - find.minGrams);
            return (int) Math.max(1, Math.round(find.baseValue * grade.valueMult * weightFactor));
        }
    }

    private static NamespacedKey kFind;
    private static NamespacedKey kGrade;
    private static NamespacedKey kGrams;
    private static NamespacedKey kTonic;

    private ForageItems() {
    }

    static void init(AetherionForaging plugin) {
        kFind = new NamespacedKey(plugin, "grove_find");
        kGrade = new NamespacedKey(plugin, "grove_grade");
        kGrams = new NamespacedKey(plugin, "grove_grams");
        kTonic = new NamespacedKey(plugin, "grove_tonic");
    }

    // ------------------------------------------------------------------ finds

    public static FindData roll(Find find, Grade grade) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double t = grade.lo + r.nextDouble() * (grade.hi - grade.lo);
        int grams = (int) Math.max(1, Math.round(find.minGrams + t * (find.maxGrams - find.minGrams)));
        return new FindData(find, grade, grams);
    }

    public static ItemStack find(FindData data) {
        ItemStack item = new ItemStack(data.find().icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(data.grade().color + data.grade().display + " " + data.find().color + "§l" + data.find().display);
        List<String> lore = new ArrayList<>();
        lore.add("§8Crown Find · " + data.find().grove.colored());
        lore.add("");
        lore.add("§7" + data.find().flavor);
        lore.add("");
        lore.add("§7Weight §f" + ForageText.grams(data.grams()));
        lore.add("§7Archivist pays §6" + ForageText.coins(data.value()) + " coins");
        lore.add("");
        lore.add("§8File it in the Cabinet · Board orders · Woodwright");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (data.grade().atLeast(Grade.PRISTINE)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kFind, PersistentDataType.STRING, data.find().id());
        pdc.set(kGrade, PersistentDataType.STRING, data.grade().name());
        pdc.set(kGrams, PersistentDataType.INTEGER, data.grams());
        ForageBridge.polish(meta, "forage_find_" + data.find().id(), data.grade().rarity);
        item.setItemMeta(meta);
        return item;
    }

    public static FindData readFind(ItemStack item) {
        if (item == null || !item.hasItemMeta() || kFind == null) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Find find = Find.byId(pdc.get(kFind, PersistentDataType.STRING));
        Grade grade = Grade.byName(pdc.get(kGrade, PersistentDataType.STRING));
        Integer grams = pdc.get(kGrams, PersistentDataType.INTEGER);
        if (find == null || grade == null) {
            return null;
        }
        return new FindData(find, grade, grams == null ? find.minGrams : grams);
    }

    // ------------------------------------------------------------------ consumables

    public static ItemStack tonic(Tonic tonic, int amount) {
        ItemStack item = new ItemStack(tonic.icon, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(tonic.display);
        List<String> lore = new ArrayList<>(tonic.lore);
        lore.add("");
        lore.add("§8Woodwright · Foraging Eldervale");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(kTonic, PersistentDataType.STRING, tonic.id);
        ForageBridge.polish(meta, "forage_" + tonic.id, tonic.rarity);
        item.setItemMeta(meta);
        return item;
    }

    public static Tonic readTonic(ItemStack item) {
        if (item == null || !item.hasItemMeta() || kTonic == null) {
            return null;
        }
        return Tonic.byId(item.getItemMeta().getPersistentDataContainer().get(kTonic, PersistentDataType.STRING));
    }

    /** Ours at all (find or tonic)? Such stacks are never placed, eaten or thrown. */
    public static boolean isOurs(ItemStack item) {
        if (item == null || !item.hasItemMeta() || kFind == null) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(kFind, PersistentDataType.STRING) || pdc.has(kTonic, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ plain logs

    /** Vanilla stack of exactly {@code material} — a heartwood or quest log is never eaten. */
    public static boolean isPlain(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasCustomModelData() && meta.getPersistentDataContainer().isEmpty();
    }

    public static int countPlain(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static void takePlain(PlayerInventory inventory, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isPlain(stack, material)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    public static int countHeartwood(PlayerInventory inventory, String woodKeyOrNull) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            String id = ForageBridge.itemId(stack);
            if (id != null && id.startsWith("isle_heartwood_")
                    && (woodKeyOrNull == null || id.equals("isle_heartwood_" + woodKeyOrNull))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static void takeHeartwood(PlayerInventory inventory, String woodKeyOrNull, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            String id = ForageBridge.itemId(stack);
            if (id == null || !id.startsWith("isle_heartwood_")
                    || (woodKeyOrNull != null && !id.equals("isle_heartwood_" + woodKeyOrNull))) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    /** Count finds at or above {@code min} (optionally of one kind). */
    public static int countFinds(PlayerInventory inventory, Find kind, Grade min) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            FindData data = readFind(stack);
            if (data != null && (kind == null || data.find() == kind) && data.grade().atLeast(min)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Takes the lowest-grade matching finds first. */
    public static void takeFinds(PlayerInventory inventory, Find kind, Grade min, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (Grade pass : Grade.values()) {
            if (!pass.atLeast(min)) {
                continue;
            }
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                FindData data = readFind(contents[slot]);
                if (data == null || data.grade() != pass || (kind != null && data.find() != kind)) {
                    continue;
                }
                int take = Math.min(left, contents[slot].getAmount());
                left -= take;
                contents[slot].setAmount(contents[slot].getAmount() - take);
                if (contents[slot].getAmount() <= 0) {
                    contents[slot] = null;
                }
            }
        }
        inventory.setStorageContents(contents);
    }
}
