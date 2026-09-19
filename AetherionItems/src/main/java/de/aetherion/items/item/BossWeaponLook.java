package de.aetherion.items.item;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;

public final class BossWeaponLook {

    private static final Set<String> GLOWING_IDS = Set.of(
            "aetherblade",
            "bridged_axe",
            "skuldugery_shortbow",
            "aetherion_void_stick",
            "warped_blade",
            "gravwell_cleaver",
            "staff_of_technical_difficulties",
            "void_vacuum_charm",
            "thermal_core",
            "pickaxe_core_of_the_burrower",
            "insolvent_ledger"
    );

    private BossWeaponLook() {
    }

    public static void apply(ItemMeta meta, String itemId) {
        if (meta == null || itemId == null || itemId.isBlank()) {
            return;
        }
        if (!GLOWING_IDS.contains(itemId.toLowerCase())) {
            return;
        }
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
}
