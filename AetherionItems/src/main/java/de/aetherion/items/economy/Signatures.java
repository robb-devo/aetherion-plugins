package de.aetherion.items.economy;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Progression Signatures (design §2 / §4.1): the four hand-earned isle finds that the T4/T5
 * ladders take as a cross-domain ingredient.
 *
 * <ul>
 *   <li>{@code isle_heartwood_<wood>} — Foraging ({@link IsleHeartwood}, already Items ids)</li>
 *   <li>{@code crystal_find} — Mining Crystal Finds (any grade except Heartstone)</li>
 *   <li>{@code prize_crop} — Farming Prize Crops (any crop / weight)</li>
 *   <li>{@code fish_trophy} — Fishing trophy heads (any species)</li>
 * </ul>
 *
 * The gather plugins stamp the id through {@code ItemFactoryAccess#stampSignature} when the find is
 * built and keep their own PDC (grade / carat / kg) for value and lore. Finds made before the stamp
 * existed are recognised here by their plugin keys and stamped on the fly ({@link #stampLegacy}).
 */
public final class Signatures {

    public static final String CRYSTAL_FIND = "crystal_find";
    public static final String PRIZE_CROP = "prize_crop";
    public static final String FISH_TROPHY = "fish_trophy";
    public static final String HEARTWOOD_PREFIX = "isle_heartwood_";

    /** Codex collection entry ids (one card per Signature family). */
    public static final String CODEX_CRYSTAL = "sig_crystal_find";
    public static final String CODEX_PRIZE = "sig_prize_crop";
    public static final String CODEX_TROPHY = "sig_fish_trophy";
    public static final String CODEX_HEARTWOOD = "sig_heartwood";

    /** Stampable ids (heartwood already carries its own Items id). */
    private static final Set<String> STAMPED = Set.of(CRYSTAL_FIND, PRIZE_CROP, FISH_TROPHY);

    // Gather-plugin PDC keys (namespace = plugin name, lower case). Read-only here.
    private static final NamespacedKey MINE_SPECIMEN = new NamespacedKey("aetherionmining", "mine_specimen");
    private static final NamespacedKey MINE_GRADE = new NamespacedKey("aetherionmining", "mine_specimen_grade");
    private static final NamespacedKey FARM_PRIZE = new NamespacedKey("aetherionfarming", "farm_prize");
    private static final NamespacedKey TROPHY_SPECIES = new NamespacedKey("aetherionfishing", "trophy_species");

    private Signatures() {
    }

    /** True for an id gather plugins may stamp. */
    public static boolean isStampable(String itemId) {
        return itemId != null && STAMPED.contains(itemId.toLowerCase(Locale.ROOT));
    }

    /** True for any Signature id, heartwood included. */
    public static boolean isSignature(String itemId) {
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return STAMPED.contains(id) || IsleHeartwood.fromId(id) != null;
    }

    /** Codex collection id for a Signature id, or null. */
    public static String codexId(String itemId) {
        if (itemId == null) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case CRYSTAL_FIND -> CODEX_CRYSTAL;
            case PRIZE_CROP -> CODEX_PRIZE;
            case FISH_TROPHY -> CODEX_TROPHY;
            default -> IsleHeartwood.fromId(id) != null ? CODEX_HEARTWOOD : null;
        };
    }

    /**
     * The stack a recipe shows for a Signature slot (and the DevMenu test sample). Recipes match by
     * id only, so any real find of that family fits the slot. Heartwood ids return the real heartwood.
     * Null for anything that is not a Signature.
     */
    public static ItemStack display(String itemId) {
        if (itemId == null) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.startsWith(HEARTWOOD_PREFIX)) {
            return IsleHeartwood.fromItemId(id);
        }
        return switch (id) {
            case CRYSTAL_FIND -> sample(Material.AMETHYST_CLUSTER, "§d✦ Crystal Find §8(any)", id,
                    "§7Any grade but Heartstone.",
                    "§7Cracked by hand on §dMining Eldervale§7.");
            case PRIZE_CROP -> sample(Material.WHEAT, "§6✦ Prize Crop §8(any)", id,
                    "§7Any crop, any weight.",
                    "§7Hand harvests on the §eFarm Isle§7.");
            case FISH_TROPHY -> sample(Material.TROPICAL_FISH, "§6✦ Trophy Fish §8(any)", id,
                    "§7Any species, any weight.",
                    "§7Landed on §bFishing Eldervale§7.");
            default -> null;
        };
    }

    /**
     * Every material a real find of this family can have, so the Bukkit ShapedRecipe behind a T4/T5
     * recipe matches whichever crystal / crop / head sits in the slot (vanilla then consumes the grid
     * normally). Mirrors Mining {@code IsleOre#showcase}, Farming {@code IsleCrop#yield} and Fishing
     * {@code LureHead} (player heads); keep in sync when a new ore or crop is added there.
     * Empty for ids whose material never varies (heartwood).
     */
    public static List<Material> choiceMaterials(String itemId) {
        if (itemId == null) {
            return List.of();
        }
        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case CRYSTAL_FIND -> List.of(
                    Material.COBBLESTONE, Material.COAL_BLOCK, Material.OXIDIZED_COPPER, Material.RAW_IRON_BLOCK,
                    Material.REDSTONE_BLOCK, Material.LAPIS_BLOCK, Material.RAW_GOLD_BLOCK, Material.QUARTZ_PILLAR,
                    Material.AMETHYST_CLUSTER, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.ANCIENT_DEBRIS);
            case PRIZE_CROP -> List.of(
                    Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SUGAR_CANE,
                    Material.NETHER_WART, Material.MELON_SLICE, Material.PUMPKIN, Material.COCOA_BEANS);
            case FISH_TROPHY -> List.of(Material.PLAYER_HEAD, Material.TROPICAL_FISH);
            default -> List.of();
        };
    }

    private static ItemStack sample(Material icon, String name, String id, String first, String second) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        meta.setLore(List.of(
                first,
                second,
                "",
                "§5Signature §8· needs a find from Eldervale",
                "§8Quarries can't make it. Trade or find one."
        ));
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** Writes the Signature id onto the stack. Returns the same stack (null-safe). */
    public static ItemStack stamp(ItemStack item, String signatureId) {
        if (item == null || !isStampable(signatureId)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING,
                signatureId.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Signature id a stack counts as for recipes: its Items id when it has one, else the id implied
     * by a gather plugin's own keys (finds from before the stamp). Heartstones never count.
     */
    public static String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(ItemKeys.item(), PersistentDataType.STRING);
        if (id != null) {
            return id;
        }
        return legacyId(pdc);
    }

    /** Stamps a pre-stamp find in place. True when the stack changed. */
    public static boolean stampLegacy(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ItemKeys.item(), PersistentDataType.STRING)) {
            return false;
        }
        String id = legacyId(pdc);
        if (id == null) {
            return false;
        }
        stamp(item, id);
        return true;
    }

    /** Stamps every pre-stamp find in an inventory. Returns how many stacks changed. */
    public static int stampLegacy(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int changed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stampLegacy(stack)) {
                inventory.setItem(slot, stack);
                changed++;
            }
        }
        return changed;
    }

    private static String legacyId(PersistentDataContainer pdc) {
        if (pdc.has(MINE_SPECIMEN, PersistentDataType.STRING)) {
            String grade = pdc.get(MINE_GRADE, PersistentDataType.STRING);
            // Heartstones stay collector items (design §4.1).
            return "heartstone".equalsIgnoreCase(grade) ? null : CRYSTAL_FIND;
        }
        if (pdc.has(FARM_PRIZE, PersistentDataType.STRING)) {
            return PRIZE_CROP;
        }
        if (pdc.has(TROPHY_SPECIES, PersistentDataType.STRING)) {
            return FISH_TROPHY;
        }
        return null;
    }
}
