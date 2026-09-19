package de.aetherion.quests.editor;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Locale;

/**
 * Equipment / vibe presets for moderator FancyNPCs (same leather look as living hosts).
 */
public enum AppearancePreset {
    WORKER("Worker", Material.IRON_PICKAXE, Color.fromRGB(110, 78, 42), true, true, true, false, "Grian"),
    SAILOR("Sailor", Material.FISHING_ROD, Color.fromRGB(40, 70, 110), true, false, true, false, "MumboJumbo"),
    FARMER("Farmer", Material.IRON_HOE, Color.fromRGB(120, 140, 60), true, false, true, false, "GoodTimesWithScar"),
    GUARD("Guard", Material.IRON_SWORD, Color.fromRGB(36, 58, 92), true, true, true, false, "impulseSV"),
    SCHOLAR("Scholar", Material.BOOK, Color.fromRGB(200, 200, 210), true, false, true, false, "GeminiTay"),
    MYSTIC("Mystic", Material.ENDER_PEARL, Color.fromRGB(40, 20, 55), true, true, true, true, "PearlescentMoon"),
    SCOUT("Scout", Material.BOW, Color.fromRGB(78, 58, 40), true, false, true, false, "EthosLab"),
    ROGUE("Rogue", Material.GOLD_NUGGET, Color.fromRGB(90, 45, 35), true, false, true, false, "VintageBeef"),
    MINER("Miner", Material.IRON_PICKAXE, Color.fromRGB(70, 70, 75), true, true, true, false, "Docm77"),
    ALEX("Alex", Material.STICK, Color.fromRGB(92, 48, 64), true, false, true, true, "Alex");

    private final String label;
    private final Material hand;
    private final Color leather;
    private final boolean chest;
    private final boolean legs;
    private final boolean boots;
    private final boolean slim;
    private final String skinUsername;

    AppearancePreset(
            String label,
            Material hand,
            Color leather,
            boolean chest,
            boolean legs,
            boolean boots,
            boolean slim,
            String skinUsername
    ) {
        this.label = label;
        this.hand = hand;
        this.leather = leather;
        this.chest = chest;
        this.legs = legs;
        this.boots = boots;
        this.slim = slim;
        this.skinUsername = skinUsername;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public boolean slim() {
        return slim;
    }

    public String skinUsername() {
        return skinUsername;
    }

    public Material icon() {
        return hand;
    }

    public ItemStack handItem() {
        return new ItemStack(hand);
    }

    public ItemStack chestItem() {
        return chest ? dyed(Material.LEATHER_CHESTPLATE) : null;
    }

    public ItemStack legsItem() {
        return legs ? dyed(Material.LEATHER_LEGGINGS) : null;
    }

    public ItemStack bootsItem() {
        return boots ? dyed(Material.LEATHER_BOOTS) : null;
    }

    private ItemStack dyed(Material piece) {
        ItemStack stack = new ItemStack(piece);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(leather);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static AppearancePreset parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return WORKER;
        }
        try {
            return AppearancePreset.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WORKER;
        }
    }
}
