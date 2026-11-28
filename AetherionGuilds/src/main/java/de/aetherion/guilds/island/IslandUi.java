package de.aetherion.guilds.island;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Screen cards and paged talk from AetherionQuests ({@code de.aetherion.quests.api.Cards}), through a soft hook.
 * Every call returns false when Quests is missing, too old, or has cards / paged talk off for that player; the
 * caller then says it the old way in chat. Nothing here ever throws.
 */
public final class IslandUi {

    /** Quests (or its Cards API) isn't on this server: stop trying. */
    private static volatile boolean missing;

    private IslandUi() {
    }

    /** Screen cards are on for this player. */
    public static boolean cards(Player player) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.enabled(player);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** Paged talk is on: hand the bubble every line at once, the player clicks from one to the next. */
    public static boolean paged(Player player) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.paged(player);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** A click on this guest NPC during a paged talk with it: on to the next line (true = used). */
    public static boolean advance(Player player, String speakerId) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.advance(player, speakerId);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** A big card is on the player's screen (or about to be): glowing things in the world step aside for it. */
    public static boolean cardUp(Player player) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.talk.HudCards.blocking(player);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** Sends the big card on the player's screen away (what a sneak does). True = there was one. */
    public static boolean dismissCard(Player player) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.talk.HudCards.close(player);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** Small card at the top: "✔ title", a detail line and a muted "next" line (both may be null). */
    public static boolean toast(Player player, String title, String detail, String next) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.toast(player, title, detail, next);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /** Small card at the top with its own mark ("✦" = something new) and a few rows. */
    public static boolean note(Player player, String glyph, String title, List<String> lines, String next) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.note(player, glyph, title, lines, next);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }

    /**
     * Big card above the middle of the screen. {@code icons} pairs with {@code lines} by index (null = no icon);
     * {@code accent} is a legacy colour code for the frame; {@code summary} is the one chat line it leaves.
     */
    public static boolean big(Player player, String header, String title, List<String> lines, List<ItemStack> icons,
                              String next, String accent, String summary) {
        return big(player, header, title, lines, icons, next, accent, summary, null);
    }

    /** The same, with the item that turns in the card's crest ({@code emblem}; null = the card picks one). */
    public static boolean big(Player player, String header, String title, List<String> lines, List<ItemStack> icons,
                              String next, String accent, String summary, ItemStack emblem) {
        if (missing || player == null) {
            return false;
        }
        try {
            return de.aetherion.quests.api.Cards.big(player, header, title, lines, icons, next, accent, summary, emblem);
        } catch (LinkageError ex) {
            missing = true;
            return false;
        }
    }
}
