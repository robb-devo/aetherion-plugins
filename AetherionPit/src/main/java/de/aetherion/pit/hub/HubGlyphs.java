package de.aetherion.pit.hub;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Font aetherion:hub (AetherionItems/tools/gen_hub_glyphs.py). The proxy sends the pack before anyone lands here. */
public final class HubGlyphs {

    public static final Key FONT = Key.key("aetherion", "hub");

    /** Launcher emblem, 32 px, hangs four lines deep. */
    public static final String EMBLEM_BIG = "";
    public static final String EMBLEM = "";
    public static final String ISLAND = "";
    public static final String SWORD = "";
    public static final String CRAFT = "";
    public static final String DISCORD = "";
    public static final String SHOP = "";

    private HubGlyphs() {
    }

    public static Component glyph(String ch) {
        return Component.text(ch, NamedTextColor.WHITE).font(FONT);
    }
}
