package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.function.Function;

/**
 * Ore-troll / Surveyor blueprints — one special tool per skill.
 * These tools do not level; abilities are the power fantasy.
 */
public enum BlueprintKind {

    VEIN_SIPHON(
            "vein_siphon",
            "blueprint_vein_siphon",
            "Vein Siphon",
            Material.IRON_PICKAXE,
            CustomItem::createVeinSiphon,
            CustomItem::createBlueprintVeinSiphon
    ),
    CANOPY_CLEAVER(
            "canopy_cleaver",
            "blueprint_canopy_cleaver",
            "Canopy Cleaver",
            Material.IRON_AXE,
            CustomItem::createCanopyCleaver,
            CustomItem::createBlueprintCanopyCleaver
    ),
    BOUNTY_HOE(
            "bounty_hoe",
            "blueprint_bounty_hoe",
            "Bounty Hoe",
            Material.IRON_HOE,
            CustomItem::createBountyHoe,
            CustomItem::createBlueprintBountyHoe
    ),
    WILD_SIGHT(
            "wild_sight",
            "blueprint_wild_sight",
            "Wild Sight",
            Material.SPYGLASS,
            CustomItem::createWildSight,
            CustomItem::createBlueprintWildSight
    ),
    TIDE_LATCH(
            "tide_latch",
            "blueprint_tide_latch",
            "Tide Latch",
            Material.FISHING_ROD,
            CustomItem::createTideLatch,
            CustomItem::createBlueprintTideLatch
    ),
    RESONANCE_SCYTHE(
            "resonance_scythe",
            "blueprint_resonance_scythe",
            "Resonance Scythe",
            Material.NETHERITE_HOE,
            ci -> ci.createResonanceScythe(false),
            CustomItem::createBlueprintResonanceScythe
    );

    private final String id;
    private final String itemId;
    private final String display;
    private final Material icon;
    private final Function<CustomItem, ItemStack> tool;
    private final Function<CustomItem, ItemStack> blueprint;

    BlueprintKind(
            String id,
            String itemId,
            String display,
            Material icon,
            Function<CustomItem, ItemStack> tool,
            Function<CustomItem, ItemStack> blueprint
    ) {
        this.id = id;
        this.itemId = itemId;
        this.display = display;
        this.icon = icon;
        this.tool = tool;
        this.blueprint = blueprint;
    }

    public String id() {
        return id;
    }

    public String itemId() {
        return itemId;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public ItemStack createTool(CustomItem customItem) {
        return tool.apply(customItem);
    }

    public ItemStack createBlueprint(CustomItem customItem) {
        return blueprint.apply(customItem);
    }

    public static BlueprintKind fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT);
        for (BlueprintKind kind : values()) {
            if (kind.id.equals(key) || kind.itemId.equals(key)) {
                return kind;
            }
        }
        return null;
    }

    public static BlueprintKind fromItemId(String itemId) {
        return fromId(itemId);
    }

    /** Random blueprint kind for ore-troll drops. */
    public static BlueprintKind random() {
        BlueprintKind[] all = values();
        return all[java.util.concurrent.ThreadLocalRandom.current().nextInt(all.length)];
    }

    public static CustomItem customItem() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getCustomItem();
    }
}
