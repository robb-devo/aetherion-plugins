package de.aetherion.items.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Aetherion Manager GUI chrome — inventory titles that draw a full-panel overlay from the
 * resource pack font ({@code assets/minecraft/font/default.json}).
 *
 * <p>Each title is {@code \uE005} (space, advance -48) followed by one 256x256 bitmap glyph.
 * Glyphs use {@code ascent 23, height 256}. Colour must stay WHITE.
 *
 * <p>Titles are display-only. Route by {@code InventoryHolder}, not title text.
 * See GUI_NOTES.md in the resource pack for the glyph map.
 */
public final class ManagerChrome {

    private ManagerChrome() {
    }

    private static Component overlay(char glyph) {
        return Component.text("\uE005" + glyph, NamedTextColor.WHITE);
    }

    // 54-slot unless noted
    public static final Component MANAGER = overlay('\uE004');
    public static final Component SHOP = overlay('\uE010');            // 45
    public static final Component SHOP_BOOSTS = overlay('\uE011');     // 45
    public static final Component SHOP_MATERIALS = overlay('\uE012');  // 45
    public static final Component SHOP_EXCLUSIVE = overlay('\uE013');  // 45
    public static final Component BAZAAR = overlay('\uE014');
    public static final Component AUCTION = overlay('\uE015');
    public static final Component MARKET_LIST = overlay('\uE016');     // 9
    public static final Component MARKET_CONFIRM = overlay('\uE017');  // 27
    public static final Component SKILLS = overlay('\uE018');
    public static final Component SKILLS_OVERVIEW = overlay('\uE019');
    public static final Component SKILLS_PRESETS = overlay('\uE01A');  // 27
    public static final Component RECIPES = overlay('\uE01B');
    public static final Component BESTIARY = overlay('\uE01C');
    public static final Component COLLECTION = overlay('\uE01D');
    public static final Component CODEX = overlay('\uE01E');
    public static final Component MILESTONES = overlay('\uE01F');
    public static final Component JOURNAL = overlay('\uE020');
    public static final Component STATS = overlay('\uE021');
    public static final Component STORAGE = overlay('\uE022');
    public static final Component LOADOUTS = overlay('\uE023');
    public static final Component SPAWNS = overlay('\uE024');
    public static final Component PETS = overlay('\uE025');
}
