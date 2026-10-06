package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shared skill-sack framework: one {@link Skill} (which resources) x one {@link Tier}
 * (which forms + how many). Every skill sack is built from this — no per-skill classes.
 */
public final class SkillSack {

    /** Item PDC value for {@link ItemKeys#sackType()} — keeps legacy {@link SackType} untouched. */
    public static final String TYPE_TAG = "SKILL";

    /** Shared empty bag — craft with 8 leather, then wrap with skill resources for T1. */
    public static final String LEATHER_BAG_ID = "leather_bag";

    private SkillSack() {
    }

    public static ItemStack createLeatherBag() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName("§6Leather Bag");
        meta.setLore(List.of(
                "§7Empty pouch for a skill sack.",
                "§7Surround with 8 of a skill's raw drops",
                "§7to make that skill's Sack I.",
                "",
                "§8Recipe Book · Resources"
        ));
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, LEATHER_BAG_ID);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        ItemManager manager = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getItemManager();
        if (manager != null) {
            manager.applyItemData(meta, Rarity.COMMON, new ItemStats());
        }
        de.aetherion.items.item.ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isLeatherBag(ItemStack item) {
        return LEATHER_BAG_ID.equalsIgnoreCase(rawItemId(item));
    }

    private static String rawItemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }

    /** Representative raw drop used in the T1 ring / T2–T3 upgrade crafts. */
    public static CompressedResource craftResource(Skill skill) {
        return switch (skill) {
            case MINING -> CompressedResource.COBBLESTONE;
            case FARMING -> CompressedResource.WHEAT;
            case FORAGING -> CompressedResource.OAK_LOG;
            case FISHING -> CompressedResource.COD;
        };
    }

    /** What a sack slot holds for one resource. */
    public enum Form {
        RAW("raw"),
        COMPRESSED("compressed"),
        COMPACTED("compacted");

        private final String key;

        Form(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public enum Tier {
        T1("I", 2_000, Rarity.UNCOMMON, Form.RAW),
        T2("II", 20_000, Rarity.RARE, Form.RAW, Form.COMPRESSED),
        T3("III", 200_000, Rarity.EPIC, Form.RAW, Form.COMPRESSED, Form.COMPACTED);

        private final String roman;
        private final int capacity;
        private final Rarity rarity;
        private final List<Form> forms;

        Tier(String roman, int capacity, Rarity rarity, Form... forms) {
            this.roman = roman;
            this.capacity = capacity;
            this.rarity = rarity;
            this.forms = List.of(forms);
        }

        public String roman() {
            return roman;
        }

        /** Max count per slot (one resource in one form). */
        public int capacity() {
            return capacity;
        }

        public Rarity rarity() {
            return rarity;
        }

        public List<Form> forms() {
            return forms;
        }

        public boolean holds(Form form) {
            return forms.contains(form);
        }
    }

    public enum Skill {
        MINING("Mining", "§b"),
        FARMING("Farming", "§e"),
        FORAGING("Foraging", "§2"),
        FISHING("Fishing", "§3");

        private final String label;
        private final String color;
        private List<CompressedResource> resources;

        Skill(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public String color() {
            return color;
        }

        /** Accepted resources, in the shared display order. */
        public List<CompressedResource> resources() {
            if (resources == null) {
                List<CompressedResource> out = new ArrayList<>();
                for (CompressedResource resource : CompressedResource.contentOrdered()) {
                    if (owns(resource)) {
                        out.add(resource);
                    }
                }
                resources = List.copyOf(out);
            }
            return resources;
        }

        private boolean owns(CompressedResource resource) {
            return switch (this) {
                case MINING -> resource.isMiningDrop();
                case FARMING -> resource.isFarmingDrop();
                case FORAGING -> resource.isForagingDrop();
                case FISHING -> resource.isFishingDrop();
            };
        }
    }

    /** One stored slot: resource + form. */
    public record Slot(CompressedResource resource, Form form) {
        public String key() {
            return form.key() + "." + resource.key();
        }

        public ItemStack sample() {
            return switch (form) {
                case RAW -> new ItemStack(resource.input());
                case COMPRESSED -> resource.compressed();
                case COMPACTED -> resource.compacted();
            };
        }

        public String displayName() {
            return switch (form) {
                case RAW -> "§f" + resource.prettyName();
                case COMPRESSED -> "§aCompressed " + resource.prettyName();
                case COMPACTED -> "§bCompacted " + resource.prettyName();
            };
        }
    }

    /**
     * Which slot this stack would go into for the given sack, or null if the sack refuses it.
     * Raw = plain vanilla drop (no Aetherion id, no rename, not Nether-marked).
     */
    public static Slot slotFor(Skill skill, Tier tier, ItemStack stack, ItemManager items) {
        if (skill == null || tier == null || stack == null || stack.getType().isAir()) {
            return null;
        }
        String id = items == null ? null : items.getItemId(stack);
        if (id != null) {
            CompressedResource resource = CompressedResource.byItemId(id);
            if (resource == null || !skill.resources().contains(resource)) {
                return null;
            }
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.equals(resource.compressedId()) && tier.holds(Form.COMPRESSED)) {
                return new Slot(resource, Form.COMPRESSED);
            }
            if (lower.equals(resource.compactedId()) && tier.holds(Form.COMPACTED)) {
                return new Slot(resource, Form.COMPACTED);
            }
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return null;
        }
        if (de.aetherion.items.world.NetherRealm.isMarked(stack)) {
            return null;
        }
        for (CompressedResource resource : skill.resources()) {
            if (resource.input() == stack.getType()) {
                return new Slot(resource, Form.RAW);
            }
        }
        return null;
    }

    /** Every slot this sack shows, column = resource, row = form. */
    public static List<Slot> slots(Skill skill, Tier tier) {
        List<Slot> out = new ArrayList<>();
        for (Form form : tier.forms()) {
            for (CompressedResource resource : skill.resources()) {
                out.add(new Slot(resource, form));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ items

    public static String itemId(Skill skill, Tier tier) {
        return skill.name().toLowerCase(Locale.ROOT) + "_sack_" + tier.name().toLowerCase(Locale.ROOT);
    }

    public static String displayName(Skill skill, Tier tier) {
        return skill.color() + skill.label() + " Sack " + tier.roman();
    }

    public static ItemStack create(Skill skill, Tier tier) {
        return create(skill, tier, UUID.randomUUID());
    }

    public static ItemStack create(Skill skill, Tier tier, UUID sackId) {
        if (skill == null || tier == null) {
            return null;
        }
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(displayName(skill, tier));
        meta.setLore(lore(skill, tier));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ItemKeys.item(), PersistentDataType.STRING, itemId(skill, tier));
        pdc.set(ItemKeys.sackId(), PersistentDataType.STRING, sackId.toString());
        pdc.set(ItemKeys.sackType(), PersistentDataType.STRING, TYPE_TAG);
        if (tier != Tier.T1) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        ItemManager manager = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getItemManager();
        if (manager != null) {
            manager.applyItemData(meta, tier.rarity(), new ItemStats());
        }
        de.aetherion.items.item.ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> lore(Skill skill, Tier tier) {
        List<String> lines = new ArrayList<>();
        lines.add("§7Collects " + skill.label() + " drops automatically.");
        lines.add("");
        lines.add("§7Holds: " + formsLine(tier));
        lines.add("§7Capacity: §f" + String.format(Locale.US, "%,d", tier.capacity()) + " §7per slot");
        lines.add("§7Resources: §f" + skill.resources().size());
        lines.add("");
        lines.add("§8Works from your inventory or");
        lines.add("§8from inside Aetherion Storage.");
        lines.add("§eRight-click to open.");
        return lines;
    }

    private static String formsLine(Tier tier) {
        StringBuilder out = new StringBuilder();
        for (Form form : tier.forms()) {
            if (out.length() > 0) {
                out.append("§8 · ");
            }
            out.append(switch (form) {
                case RAW -> "§fRaw";
                case COMPRESSED -> "§aCompressed";
                case COMPACTED -> "§bCompacted";
            });
        }
        return out.toString();
    }

    public static boolean is(ItemStack item) {
        return skillOf(item) != null && tierOf(item) != null;
    }

    public static Skill skillOf(ItemStack item) {
        String id = rawId(item);
        if (id == null) {
            return null;
        }
        for (Skill skill : Skill.values()) {
            if (id.startsWith(skill.name().toLowerCase(Locale.ROOT) + "_sack_")) {
                return skill;
            }
        }
        return null;
    }

    public static Tier tierOf(ItemStack item) {
        String id = rawId(item);
        if (id == null) {
            return null;
        }
        for (Tier tier : Tier.values()) {
            if (id.endsWith("_sack_" + tier.name().toLowerCase(Locale.ROOT))) {
                return tier;
            }
        }
        return null;
    }

    /** Dev / admin give by item id (e.g. {@code mining_sack_t2}). */
    public static ItemStack fromId(String id) {
        if (id == null) {
            return null;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (Skill skill : Skill.values()) {
            for (Tier tier : Tier.values()) {
                if (itemId(skill, tier).equals(lower)) {
                    return create(skill, tier);
                }
            }
        }
        return null;
    }

    /** All 12 sacks, skill-major — dev shelves. */
    public static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            for (Tier tier : Tier.values()) {
                out.add(create(skill, tier));
            }
        }
        return out;
    }

    private static String rawId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String type = meta.getPersistentDataContainer().get(ItemKeys.sackType(), PersistentDataType.STRING);
        if (!TYPE_TAG.equals(type)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }
}
