package de.aetherion.quests.chest;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;

public enum ExploreChestKind {

    RARE("Rare Chest", NamedTextColor.AQUA, "§b", Material.CHEST),
    EPIC("Epic Chest", NamedTextColor.DARK_PURPLE, "§5", Material.TRAPPED_CHEST),
    LEGENDARY("Legendary Chest", NamedTextColor.GOLD, "§6", Material.ENDER_CHEST),
    MYTHIC("Mythic Chest", NamedTextColor.LIGHT_PURPLE, "§d", Material.PURPLE_SHULKER_BOX);

    private final String display;
    private final NamedTextColor color;
    private final String chat;
    private final Material block;

    ExploreChestKind(String display, NamedTextColor color, String chat, Material block) {
        this.display = display;
        this.color = color;
        this.chat = chat;
        this.block = block;
    }

    public String display() {
        return display;
    }

    public NamedTextColor color() {
        return color;
    }

    public String chat() {
        return chat;
    }

    public Material block() {
        return block;
    }

    public String id() {
        return name().toLowerCase();
    }

    public static ExploreChestKind fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
