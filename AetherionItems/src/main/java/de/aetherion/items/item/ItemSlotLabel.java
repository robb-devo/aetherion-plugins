package de.aetherion.items.item;

import de.aetherion.items.model.ItemProfile;

import org.bukkit.ChatColor;

import java.util.Locale;

/**
 * Short gray subtitle under the item name (category / slot).
 */
public final class ItemSlotLabel {

    private ItemSlotLabel() {
    }

    /** Gray lore line, or null if unknown. */
    public static String loreLine(String itemId) {
        String label = plain(itemId);
        if (label == null || label.isBlank()) {
            return null;
        }
        return "§8" + label;
    }

    /** Uppercase type fragment for rarity footer, e.g. {@code MINING PICKAXE}. */
    public static String footerType(String itemId) {
        String label = plain(itemId);
        if (label == null || label.isBlank()) {
            return "ITEM";
        }
        return label.toUpperCase(Locale.ROOT);
    }

    public static String fromBannerLine(String line) {
        if (line == null || !LoreLayout.isCategoryBanner(line)) {
            return null;
        }
        String plain = ChatColor.stripColor(line).trim()
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isEmpty()) {
            return null;
        }
        // Title-case words
        String[] parts = plain.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (part.length() <= 3 && part.equals(part.toUpperCase(Locale.ROOT))) {
                out.append(part); // II, III, IV
            } else {
                out.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    out.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return out.toString();
    }

    public static String plain(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        ItemProfile profile = ItemProfile.fromItemId(id);

        if (id.startsWith("god_")) {
            return "God Kit";
        }
        if (id.contains("charm") || id.contains("accessory") || id.contains("talisman")) {
            return "Aetherion Charm";
        }
        if (id.contains("dungeon")) {
            if (id.contains("helmet") || id.contains("helm")) {
                return "Dungeon Helmet";
            }
            if (id.contains("chest") || id.contains("vest")) {
                return "Dungeon Chestplate";
            }
            if (id.contains("leg")) {
                return "Dungeon Leggings";
            }
            if (id.contains("boot")) {
                return "Dungeon Boots";
            }
            if (id.contains("sword") || id.contains("blade") || id.contains("weapon")) {
                return "Dungeon Weapon";
            }
            return "Dungeon Gear";
        }

        String slot = slotWord(id);
        String skill = skillWord(id, profile);
        if (skill != null && slot != null) {
            return skill + " " + slot;
        }
        if (slot != null) {
            return slot;
        }
        if (skill != null) {
            return skill + " Gear";
        }
        if (id.startsWith("simple_")) {
            return "Basic Aetherion Tool";
        }
        if (id.startsWith("beginner_") || id.contains("starter")) {
            return "Starter Tool";
        }
        return null;
    }

    private static String slotWord(String id) {
        if (id.contains("pickaxe") || id.contains("pick") || id.contains("siphon")) {
            return "Pickaxe";
        }
        if (id.contains("cleaver")) {
            return "Axe";
        }
        if (id.contains("longbow")) {
            return "Longbow";
        }
        if (id.contains("shortbow") || id.contains("bow")) {
            return "Bow";
        }
        if (id.contains("sword") || id.contains("blade") || id.contains("dagger") || id.contains("aetherblade")
                || id.contains("maul") || id.contains("scythe") || id.contains("staff")
                || (id.contains("rod") && id.contains("combat"))) {
            return "Weapon";
        }
        if (id.contains("axe") && !id.contains("pickaxe")) {
            return "Axe";
        }
        if (id.contains("hoe")) {
            return "Hoe";
        }
        if (id.contains("rod") || id.contains("fishing")) {
            return "Rod";
        }
        if (id.contains("gaff") || id.contains("catcher")) {
            return "Gaff";
        }
        if (id.contains("helmet") || id.contains("helm")) {
            return "Helmet";
        }
        if (id.contains("chest") || id.contains("plate") || id.contains("vest")) {
            return "Chestplate";
        }
        if (id.contains("legging") || id.contains("pants") || id.contains("legs")) {
            return "Leggings";
        }
        if (id.contains("boot")) {
            return "Boots";
        }
        return null;
    }

    private static String skillWord(String id, ItemProfile profile) {
        if (id.contains("combat") || id.contains("warrior") || id.contains("assassin")
                || id.contains("soldier") || id.contains("tank") || id.contains("healer")
                || id.contains("shaman")) {
            return "Combat";
        }
        if (id.contains("mining") || id.contains("miner") || id.contains("ore") || id.contains("siphon")
                || id.contains("vein")) {
            return "Mining";
        }
        if (id.contains("farm") || id.contains("hoe") || id.contains("harvest")
                || (id.contains("scythe") && id.contains("resonance"))) {
            return "Farming";
        }
        if (id.contains("forage") || id.contains("lumber") || id.contains("timber") || id.contains("canopy")
                || id.contains("cleaver")) {
            return "Foraging";
        }
        if (id.contains("fish") || id.contains("dive") || id.contains("ocean")) {
            return "Fishing";
        }
        if (id.contains("catch") || id.contains("pet") || id.contains("gaff")) {
            return "Catcher";
        }
        if (profile == null) {
            return null;
        }
        if (profile.hasCapability(de.aetherion.items.model.ItemCapability.MINING_POWER)) {
            return "Mining";
        }
        if (profile.hasCapability(de.aetherion.items.model.ItemCapability.DAMAGE)
                && !profile.hasCapability(de.aetherion.items.model.ItemCapability.MINING_POWER)) {
            return "Combat";
        }
        if (profile.hasCapability(de.aetherion.items.model.ItemCapability.HARVEST_SPREAD)) {
            return "Farming";
        }
        if (profile.hasCapability(de.aetherion.items.model.ItemCapability.FISHING_SPEED)
                || profile.hasCapability(de.aetherion.items.model.ItemCapability.FISHING_CATCH)) {
            return "Fishing";
        }
        if (profile.hasCapability(de.aetherion.items.model.ItemCapability.PET_CATCH_RATE)) {
            return "Catcher";
        }
        return null;
    }
}
