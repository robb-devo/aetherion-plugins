package de.aetherion.items.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.ChatColor;
import org.bukkit.Color;

public enum Rarity {

    COMMON(ChatColor.WHITE, Color.WHITE, NamedTextColor.WHITE),
    UNCOMMON(ChatColor.GREEN, Color.fromRGB(85, 255, 85), NamedTextColor.GREEN),
    RARE(ChatColor.AQUA, Color.fromRGB(85, 170, 255), NamedTextColor.AQUA),
    EPIC(ChatColor.DARK_PURPLE, Color.fromRGB(85, 0, 85), NamedTextColor.DARK_PURPLE),
    LEGENDARY(ChatColor.GOLD, Color.fromRGB(255, 170, 0), NamedTextColor.GOLD),
    MYTHIC(ChatColor.LIGHT_PURPLE, Color.fromRGB(170, 0, 170), NamedTextColor.LIGHT_PURPLE),
    /** Dragon-only endgame rarity (ascension). Deep crimson — unused by other rarities. */
    AETHERED(ChatColor.DARK_RED, Color.fromRGB(176, 20, 40), NamedTextColor.DARK_RED);

    private final ChatColor chatColor;
    private final Color armorColor;
    private final TextColor textColor;

    Rarity(ChatColor chatColor, Color armorColor, TextColor textColor) {
        this.chatColor = chatColor;
        this.armorColor = armorColor;
        this.textColor = textColor;
    }

    public Rarity next() {
        int index = ordinal() + 1;
        Rarity[] all = values();
        return index >= all.length ? this : all[index];
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public Color getArmorColor() {
        return armorColor;
    }

    public TextColor textColor() {
        return textColor;
    }
}