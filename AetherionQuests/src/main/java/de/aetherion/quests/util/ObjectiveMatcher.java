package de.aetherion.quests.util;


import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Set;


public final class ObjectiveMatcher {


    private static final Map<String, Set<String>> BLOCK_ALIASES = Map.ofEntries(
            Map.entry("COAL", Set.of("COAL_ORE", "DEEPSLATE_COAL_ORE")),
            Map.entry("IRON", Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE", "RAW_IRON")),
            Map.entry("IRON_ORE", Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE")),
            Map.entry("GOLD", Set.of("GOLD_ORE", "DEEPSLATE_GOLD_ORE", "RAW_GOLD")),
            Map.entry("DIAMOND", Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE")),
            Map.entry("EMERALD", Set.of("EMERALD_ORE", "DEEPSLATE_EMERALD_ORE")),
            Map.entry("REDSTONE", Set.of("REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE")),
            Map.entry("LAPIS", Set.of("LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "LAPIS_LAZULI")),
            Map.entry("COPPER", Set.of("COPPER_ORE", "DEEPSLATE_COPPER_ORE", "RAW_COPPER")),
            Map.entry("OAK_LOG", Set.of("OAK_LOG", "OAK_WOOD", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD")),
            Map.entry("WHEAT", Set.of("WHEAT"))
    );


    private ObjectiveMatcher() {
    }


    public static boolean matchesBlock(Material material, String target) {
        return matches(material, target, true);
    }


    public static boolean matchesItem(Material material, String target) {
        return matches(material, target, false);
    }


    private static boolean matches(Material material, String target, boolean block) {

        if (material == null || target == null || target.isBlank()) {
            return false;
        }

        if (target.equalsIgnoreCase("ANY")) {
            return true;
        }

        if (target.equalsIgnoreCase("ANY_ORE") || target.equalsIgnoreCase("ORE")) {
            String name = material.name();
            return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
        }

        String materialName = material.name();

        if (materialName.equalsIgnoreCase(target)) {
            return true;
        }

        Set<String> aliases = BLOCK_ALIASES.get(target.toUpperCase(Locale.ROOT));

        if (aliases != null && aliases.contains(materialName)) {
            return true;
        }

        if (block && materialName.endsWith("_ORE")) {
            String drop = materialName.replace("DEEPSLATE_", "").replace("_ORE", "");
            return drop.equalsIgnoreCase(target);
        }

        return false;

    }

}
