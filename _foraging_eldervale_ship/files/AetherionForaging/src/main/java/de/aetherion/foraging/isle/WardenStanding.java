package de.aetherion.foraging.isle;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Warden Standing — how much the Grove trusts you. Earned from Board orders, Grove Marks, events,
 * mastery milestones and walking the whole island. It gates the higher bench tiers and the better
 * Board orders; it is never spent. There is no second currency.
 */
public final class WardenStanding {

    private static final int[] AT = {0, 3, 8, 15, 25, 40, 60, 85, 120};
    private static final String[] TITLES = {
            "Sapling", "Seedling", "Sprout", "Bough", "Grovehand", "Woodwarden", "Elder", "Canopy Keeper", "Heartwood"
    };

    WardenStanding() {
    }

    public static int level(int points) {
        int level = 0;
        while (level + 1 < AT.length && points >= AT[level + 1]) {
            level++;
        }
        return level;
    }

    public static int maxLevel() {
        return AT.length - 1;
    }

    public static String title(int level) {
        return TITLES[Math.max(0, Math.min(TITLES.length - 1, level))];
    }

    public static int pointsFor(int level) {
        return AT[Math.max(0, Math.min(AT.length - 1, level))];
    }

    public static String line(ForageProfile profile) {
        int level = level(profile.standing);
        if (level >= maxLevel()) {
            return "§2" + title(level) + " §8(§6max§8)";
        }
        int from = AT[level];
        int to = AT[level + 1];
        return "§2" + title(level) + " §8" + ForageText.cells((profile.standing - from) / (double) (to - from), "§2")
                + " §7" + profile.standing + "§8/§7" + to;
    }

    public void add(Player player, ForageProfile profile, int points, String why) {
        if (points <= 0) {
            return;
        }
        int before = level(profile.standing);
        profile.standing += points;
        profile.dirty = true;
        int after = level(profile.standing);
        if (player != null && after > before) {
            player.sendMessage("§2❦ Warden Standing §8· §a" + title(after) + " §8(" + ForageText.roman(after) + ")"
                    + " §7— the Grove trusts you with more.");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.1f);
        }
    }
}
