package de.aetherion.items.menu;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The Aether Journal's sounds: vanilla foley layered and tuned, so they work without the pack. Only the reader hears
 * them. One family: leather and paper for the book itself, amethyst and enchanting ink for what the journal does on
 * its own, a dull thump for what is still shut.
 */
final class JournalSounds {

    private JournalSounds() {
    }

    /** Taken from the hotbar: the cover, the first page, and from the Bound cover on a hint of what is inside. */
    static void open(Player player, int stage) {
        Location at = player.getLocation();
        player.playSound(at, Sound.ITEM_BOOK_PUT, 0.55f, 0.85f);
        player.playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.95f);
        if (stage >= 1) {
            player.playSound(at, Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP_ENCHANTED, 0.35f, 1.3f);
        }
        if (stage >= 2) {
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 1.6f);
        }
    }

    /** A ribbon: one page turn, each chapter a shade higher than the one before, so the book has a little scale. */
    static void turn(Player player, int chapter) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 0.9f + 0.035f * chapter);
    }

    /** A chapter opened for the first time: the page turns, the ink writes itself, two notes answer. */
    static void reveal(Player player) {
        Location at = player.getLocation();
        player.playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
        player.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.55f, 1.35f);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        later(player, 4L, () -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.19f));
        later(player, 7L, () -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.59f));
    }

    /** A line that is not open yet: a muffled clasp. */
    static void locked(Player player) {
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_CHISELED_BOOKSHELF_INSERT, 0.7f, 0.7f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.3f, 0.6f);
    }

    /** The journal points the way: a bright chime and a quick cast as the light leaves. */
    static void way(Player player) {
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
        player.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.25f, 1.8f);
        player.playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.4f, 1.3f);
    }

    /** The light arrived: a chime from where the target stands, so the ear finds it too. */
    static void arrive(Player player, Location target) {
        player.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 1.2f);
    }

    /** Shut by the player (Escape or the close corner). */
    static void close(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 0.45f, 1.1f);
    }

    private static void later(Player player, long ticks, Runnable sound) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sound.run();
            }
        }, ticks);
    }
}
