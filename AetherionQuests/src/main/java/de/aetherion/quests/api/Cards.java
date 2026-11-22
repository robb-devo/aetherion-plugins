package de.aetherion.quests.api;

import de.aetherion.quests.talk.HudCards;
import de.aetherion.quests.talk.TalkChrome;
import de.aetherion.quests.talk.TalkUx;

import net.kyori.adventure.text.format.TextColor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen cards and paged talk for other plugins (Guilds' island). Plain types only, so a plugin can call this
 * from a soft hook and fall back to its old chat lines when Quests (or this class) isn't there. Every method
 * returns false when cards / paged talk are off for that player: then say it the old way.
 */
public final class Cards {

    private Cards() {
    }

    /** Screen cards are on for this player. */
    public static boolean enabled(Player player) {
        HudCards cards = HudCards.get();
        return cards != null && cards.enabledFor(player);
    }

    /** Paged talk is on for this player: bubble lines wait for a click, so a speaker may hand over every line at once. */
    public static boolean paged(Player player) {
        TalkUx talk = TalkUx.get();
        return talk != null && talk.pagedFor(player);
    }

    /**
     * The player clicked {@code speakerId} (a guest NPC): in a paged talk with it, the click goes on to the next
     * line. True = used, don't restart the conversation.
     */
    public static boolean advance(Player player, String speakerId) {
        TalkUx talk = TalkUx.get();
        return talk != null && player != null && speakerId != null && talk.advance(player, speakerId);
    }

    /** Small card at the top: "✔ {title}", then {@code detail} (legacy colours) and an optional muted {@code next}. */
    public static boolean toast(Player player, String title, String detail, String next) {
        HudCards cards = HudCards.get();
        if (cards == null || title == null) {
            return false;
        }
        List<HudCards.Row> rows = new ArrayList<>();
        if (detail != null && !detail.isBlank()) {
            rows.add(HudCards.Row.of(detail));
        }
        return cards.show(player, new HudCards.Card(HudCards.Kind.TOAST, null, title, TalkChrome.READY, rows,
                next == null || next.isBlank() ? null : next, null));
    }

    /**
     * Small card at the top with its own mark instead of the ✔ ({@code glyph}, e.g. "✦" for something new), a few
     * detail rows (legacy colours) and an optional muted {@code next}.
     */
    public static boolean note(Player player, String glyph, String title, List<String> lines, String next) {
        HudCards cards = HudCards.get();
        if (cards == null || title == null) {
            return false;
        }
        List<HudCards.Row> rows = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    rows.add(HudCards.Row.of(line));
                }
            }
        }
        return cards.show(player, new HudCards.Card(HudCards.Kind.TOAST, glyph, title, TalkChrome.QUEST, rows,
                next == null || next.isBlank() ? null : next, null));
    }

    /**
     * Big card just above the middle of the screen. {@code lines}: body rows (legacy colours, "+250 Coins" counts
     * up); {@code icons}: an item per row (same index, null = none, list may be shorter). {@code accent}: legacy
     * colour code of the frame ("§6", "§b"; null = gold). {@code summary}: the one chat line it leaves (legacy,
     * null = none).
     */
    public static boolean big(Player player, String header, String title, List<String> lines, List<ItemStack> icons,
                              String next, String accent, String summary) {
        HudCards cards = HudCards.get();
        if (cards == null || title == null) {
            return false;
        }
        List<HudCards.Row> rows = new ArrayList<>();
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                ItemStack icon = icons != null && i < icons.size() ? icons.get(i) : null;
                rows.add(new HudCards.Row(line, icon));
            }
        }
        TextColor frame = accent == null ? TalkChrome.QUEST : TalkChrome.accent(TalkChrome.legacyColor(accent));
        return cards.show(player, new HudCards.Card(HudCards.Kind.BIG, header, title, frame, rows,
                next == null || next.isBlank() ? null : next, summary));
    }
}
