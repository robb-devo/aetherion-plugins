package de.aetherion.items.dungeon;

import de.aetherion.items.item.CustomItem;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public enum SetWeaponKind {

    COMBAT("combat", "Combat", Material.STONE_SWORD, "The Combat set's argument, in sword form."),
    MINING("mining", "Mining", Material.STONE_PICKAXE, "A pick that matches the Mining set."),
    FARMING("farming", "Farming", Material.WOODEN_HOE, "A hoe that matches the Furrow set."),
    FORAGING("foraging", "Foraging", Material.WOODEN_AXE, "An axe that matches the Kindling set."),
    FISHING("fishing", "Fishing", Material.FISHING_ROD, "A rod that matches the Nibble set."),
    ROTTEN("rotten", "Rotten", Material.IRON_AXE, "Flesh learned to hold an edge."),
    BONE("bone", "Bone", Material.STONE_SWORD, "Carved from something that didn't stay buried."),
    WEBWEAVE("webweave", "Webweave", Material.STONE_SWORD, "A fang the web decided to keep."),
    IRONHIDE("ironhide", "Ironhide", Material.NETHERITE_HOE, "The maul that matches the Ironhide set."),
    HEALER("healer", "Healer", Material.BLAZE_ROD, "The staff that matches the Healer set."),
    CATCHER("catcher", "Catcher", Material.IRON_HOE, "A gaff for pets that refuse the appointment.");

    public static final String SCHEMATIC_ID = "weapon_schematic";

    private final String id;
    private final String setName;
    private final Material icon;
    private final String blurb;

    SetWeaponKind(String id, String setName, Material icon, String blurb) {
        this.id = id;
        this.setName = setName;
        this.icon = icon;
        this.blurb = blurb;
    }

    public String id() {
        return id;
    }

    public String setName() {
        return setName;
    }

    public Material icon() {
        return icon;
    }

    public String blurb() {
        return blurb;
    }

    public ItemStack create(CustomItem items) {
        if (items == null) {
            return null;
        }
        return switch (this) {
            case COMBAT -> items.createCombatSword();
            case MINING -> items.createMiningPickaxe();
            case FARMING -> items.farming().hoe(1);
            case FORAGING -> items.foraging().axe(1);
            case FISHING -> items.fishing().rod(1);
            case ROTTEN -> items.createRottenCleaver();
            case BONE -> items.createBoneKnife();
            case WEBWEAVE -> items.createWebweaveFang();
            case IRONHIDE -> items.createObsidianMaul();
            case HEALER -> items.createMenderStaff();
            case CATCHER -> items.createCatcherGaff();
        };
    }

    public static boolean isSchematic(String itemId) {
        return itemId != null && SCHEMATIC_ID.equalsIgnoreCase(itemId);
    }

    public static SetWeaponKind fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT);
        for (SetWeaponKind kind : values()) {
            if (kind.id.equals(key) || kind.name().equalsIgnoreCase(key)) {
                return kind;
            }
        }
        return null;
    }
}
