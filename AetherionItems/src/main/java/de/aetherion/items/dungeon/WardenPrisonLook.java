package de.aetherion.items.dungeon;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floor I identity: the Warden's Prison.
 * <p>
 * Floor-I (T1) dungeon gear reads as gear pried out of the prison: every calling has a prison
 * set (Turnkey, Escapist, Riot Warden, Chaplain, Sculkbound), every piece has its own name and
 * a line of story, the armor wears prison trims, and one drop in eight comes out
 * <b>Warden-Marked</b> (Silence trim, glint, its own line). Item ids, stats and the
 * identify/attune loop are unchanged; T2/T3 boss gear keeps its look.
 */
public final class WardenPrisonLook {

    public static final double MARK_CHANCE = 0.12;
    public static final String MARK_LINE = "§3✦ Warden-Marked §8· it heard you take it";
    public static final String MARK_PREFIX = "§3Warden-Marked ";

    private WardenPrisonLook() {
    }

    public static NamespacedKey markKey() {
        return ItemKeys.key("warden_mark");
    }

    // ------------------------------------------------------------------ armor names

    public static String setName(DungeonCalling calling) {
        return switch (calling) {
            case TANK -> "Turnkey";
            case ASSASSIN -> "Escapist";
            case SOLDIER -> "Riot Warden";
            case HEALER -> "Chaplain";
            case SHAMAN -> "Sculkbound";
        };
    }

    public static String pieceName(DungeonCalling calling, DungeonPiece piece) {
        String name = switch (calling) {
            case TANK -> switch (piece) {
                case HELMET -> "Turnkey's Iron Visor";
                case CHESTPLATE -> "Turnkey's Gaol Plate";
                case LEGGINGS -> "Turnkey's Cellblock Greaves";
                case BOOTS -> "Turnkey's Shackle Sabatons";
            };
            case ASSASSIN -> switch (piece) {
                case HELMET -> "Escapist's Hood";
                case CHESTPLATE -> "Escapist's Ragged Vest";
                case LEGGINGS -> "Escapist's Tunnel Wraps";
                case BOOTS -> "Escapist's Silent Soles";
            };
            case SOLDIER -> switch (piece) {
                case HELMET -> "Riot Warden's Helm";
                case CHESTPLATE -> "Riot Warden's Hauberk";
                case LEGGINGS -> "Riot Warden's Tassets";
                case BOOTS -> "Riot Warden's Treads";
            };
            case HEALER -> switch (piece) {
                case HELMET -> "Chaplain's Cowl";
                case CHESTPLATE -> "Chaplain's Vestment";
                case LEGGINGS -> "Chaplain's Penitent Robes";
                case BOOTS -> "Chaplain's Pilgrim Sandals";
            };
            case SHAMAN -> switch (piece) {
                case HELMET -> "Sculkbound Crown";
                case CHESTPLATE -> "Sculkbound Mantle";
                case LEGGINGS -> "Sculkbound Wraps";
                case BOOTS -> "Sculkbound Steps";
            };
        };
        return calling.color() + name;
    }

    /** One line of story per piece. */
    public static String pieceLine(DungeonCalling calling, DungeonPiece piece) {
        return switch (calling) {
            case TANK -> switch (piece) {
                case HELMET -> "Every door on the block answered to this face.";
                case CHESTPLATE -> "Dented where the riot pushed. It did not move.";
                case LEGGINGS -> "Walked the same corridor ten thousand times.";
                case BOOTS -> "The chains are still attached. Somebody kept the key.";
            };
            case ASSASSIN -> switch (piece) {
                case HELMET -> "Stitched from a cell blanket. Smells like freedom.";
                case CHESTPLATE -> "Nineteen pockets. Eighteen of them hold a spoon.";
                case LEGGINGS -> "Worn crawling through a tunnel nobody finished.";
                case BOOTS -> "Never squeaked once. The guards hated that.";
            };
            case SOLDIER -> switch (piece) {
                case HELMET -> "Visor down, opinions off.";
                case CHESTPLATE -> "Chain links from the old cell doors, reforged.";
                case LEGGINGS -> "Built for standing in a doorway and meaning it.";
                case BOOTS -> "Tread pattern: the floor of D Block.";
            };
            case HEALER -> switch (piece) {
                case HELMET -> "He heard every confession. He told the Warden none.";
                case CHESTPLATE -> "Candle wax, iron thread, and a stubborn prayer.";
                case LEGGINGS -> "Knees worn through on the chapel stone.";
                case BOOTS -> "Walked the infirmary rows every night.";
            };
            case SHAMAN -> switch (piece) {
                case HELMET -> "It hums when the Warden is near. It is always humming.";
                case CHESTPLATE -> "The sculk grew into the cloth. It has opinions.";
                case LEGGINGS -> "Quiet steps. Something below listens anyway.";
                case BOOTS -> "Leaves no prints. Leaves a little sculk.";
            };
        };
    }

    public static String setLine(DungeonCalling calling) {
        return calling.color() + setName(calling) + " Set §8· " + calling.display() + " · Warden's Prison";
    }

    /** The T1 display name this piece had before the Warden's Prison pass. */
    public static String legacyName(DungeonCalling calling, DungeonPiece piece) {
        return calling.color() + calling.setName() + " " + piece.display();
    }

    // ------------------------------------------------------------------ weapons

    public static String weaponName(DungeonWeaponKind kind) {
        return switch (kind) {
            case SWORD -> "§cRiot Warden's Breaker";
            case BOW -> "§5Escapist's Shortbow";
            case WAND -> "§3Sculkbound Rod";
            case MACE -> "§bTurnkey's Cell Maul";
            case STAFF -> "§eChaplain's Censer";
        };
    }

    public static String weaponLine(DungeonWeaponKind kind) {
        return switch (kind) {
            case SWORD -> "Broke up the last riot. Started the next one.";
            case BOW -> "Strung with cell-door wire. Fires before you decide.";
            case WAND -> "A guard's lantern pole the sculk grew into.";
            case MACE -> "The key ring melted into a fist.";
            case STAFF -> "Still smokes. The chapel incense never ran out.";
        };
    }

    public static String legacyWeaponName(DungeonWeaponKind kind) {
        String color = switch (kind) {
            case SWORD -> "§c";
            case BOW -> "§5";
            case WAND -> "§3";
            case MACE -> "§b";
            case STAFF -> "§e";
        };
        String set = switch (kind) {
            case SWORD -> "Vanguard";
            case BOW -> "Shade";
            case WAND -> "Spiritbind";
            case MACE -> "Bulwark";
            case STAFF -> "Sanctuary";
        };
        return color + set + " " + kind.display() + " " + DungeonGearTier.T1.roman();
    }

    // ------------------------------------------------------------------ vestige / schematic

    public static String vestigeName(DungeonPiece piece) {
        return "§7Shackled Vestige §8· §7" + piece.display();
    }

    public static String legacyVestigeName(DungeonPiece piece) {
        return "§7Dungeon Vestige " + piece.display();
    }

    public static final String SCHEMATIC_NAME = "§5Armory Requisition §8· §dFloor I";
    public static final String LEGACY_SCHEMATIC_NAME = "§5Dungeon Weapon Schematic";

    // ------------------------------------------------------------------ trims

    /** Trim for Floor-I dungeon armor (and vestiges); null for anything else (T2/T3 keep theirs). */
    public static ArmorTrim trimFor(String itemId, ItemMeta meta) {
        if (itemId == null) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (DungeonArmor.isVestige(id)) {
            return new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.RIB);
        }
        DungeonCalling calling = DungeonCalling.fromItemId(id);
        if (calling == null || DungeonGearTier.fromItemId(id) != DungeonGearTier.T1) {
            return null;
        }
        if (isMarked(meta)) {
            return new ArmorTrim(switch (calling) {
                case TANK -> TrimMaterial.IRON;
                case ASSASSIN -> TrimMaterial.AMETHYST;
                case SOLDIER -> TrimMaterial.REDSTONE;
                case HEALER -> TrimMaterial.GOLD;
                case SHAMAN -> TrimMaterial.DIAMOND;
            }, TrimPattern.SILENCE);
        }
        return switch (calling) {
            case TANK -> new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.WARD);
            case ASSASSIN -> new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.EYE);
            case SOLDIER -> new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.BOLT);
            case HEALER -> new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.FLOW);
            case SHAMAN -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.WAYFINDER);
        };
    }

    // ------------------------------------------------------------------ marking + refresh

    public static boolean isMarked(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(markKey(), PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public static boolean isMarked(ItemStack item) {
        return item != null && item.hasItemMeta() && isMarked(item.getItemMeta());
    }

    /** Floor-I native gear that can carry the Warden's mark (attuned armor, Floor-I weapons). */
    public static boolean isFloorOneGear(String itemId) {
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (DungeonGearTier.fromItemId(id) != DungeonGearTier.T1 || DungeonRelic.isUnidentified(id)) {
            return false;
        }
        return DungeonCalling.fromItemId(id) != null || DungeonWeaponKind.fromItemId(id) != null;
    }

    /**
     * Names, mark and glint for Floor-I gear. {@code fresh} = the item is being created right now
     * (the only time the Warden's mark is rolled). Safe to call on every lore refresh.
     */
    public static void apply(ItemStack item, ItemManager items, boolean fresh) {
        if (item == null || items == null || !item.hasItemMeta()) {
            return;
        }
        String id = items.getItemId(item);
        if (id == null || !id.toLowerCase(Locale.ROOT).startsWith("dungeon_")) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean changed = false;
        String lower = id.toLowerCase(Locale.ROOT);
        if (DungeonArmor.isVestige(lower)) {
            DungeonPiece piece = DungeonPiece.fromItemId(lower);
            if (piece != null && legacyVestigeName(piece).equals(meta.getDisplayName())) {
                meta.setDisplayName(vestigeName(piece));
                changed = true;
            }
        } else if (DungeonWeaponKind.isSchematic(lower)) {
            if (LEGACY_SCHEMATIC_NAME.equals(meta.getDisplayName()) && DungeonWeaponKind.schematicTier(item) == DungeonGearTier.T1) {
                meta.setDisplayName(SCHEMATIC_NAME);
                changed = true;
            }
        } else if (isFloorOneGear(lower)) {
            if (fresh && !meta.getPersistentDataContainer().has(markKey(), PersistentDataType.BYTE)) {
                boolean marked = ThreadLocalRandom.current().nextDouble() < MARK_CHANCE;
                meta.getPersistentDataContainer().set(markKey(), PersistentDataType.BYTE, (byte) (marked ? 1 : 0));
                changed = true;
            }
            String base = baseName(lower);
            String legacy = legacyBaseName(lower);
            String current = meta.hasDisplayName() ? meta.getDisplayName() : "";
            boolean marked = isMarked(meta);
            String wanted = marked ? MARK_PREFIX + base : base;
            if (base != null && (current.equals(legacy) || current.equals(base) || current.equals(MARK_PREFIX + base)) && !current.equals(wanted)) {
                meta.setDisplayName(wanted);
                changed = true;
            }
            if (marked) {
                if (!meta.hasEnchantmentGlintOverride() || !Boolean.TRUE.equals(meta.getEnchantmentGlintOverride())) {
                    meta.setEnchantmentGlintOverride(true);
                    changed = true;
                }
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                if (!lore.contains(MARK_LINE)) {
                    lore.add(Math.min(1, lore.size()), MARK_LINE);
                    meta.setLore(lore);
                    changed = true;
                }
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
    }

    private static String baseName(String id) {
        DungeonCalling calling = DungeonCalling.fromItemId(id);
        DungeonPiece piece = DungeonPiece.fromItemId(id);
        if (calling != null && piece != null) {
            return pieceName(calling, piece);
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(id);
        return kind == null ? null : weaponName(kind);
    }

    private static String legacyBaseName(String id) {
        DungeonCalling calling = DungeonCalling.fromItemId(id);
        DungeonPiece piece = DungeonPiece.fromItemId(id);
        if (calling != null && piece != null) {
            return legacyName(calling, piece);
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(id);
        return kind == null ? null : legacyWeaponName(kind);
    }
}
