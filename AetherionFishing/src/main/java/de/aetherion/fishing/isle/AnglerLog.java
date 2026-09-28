package de.aetherion.fishing.isle;

import de.aetherion.fishing.FishingSkills;

import net.kyori.adventure.title.Title;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * The Angler's Log: every species you land, how many, and your heaviest. Log points come from new
 * species (by rarity) and from heavier personal bests (silver / gold / record-class weight). Points
 * climb ten Angler Ranks; each rank is permanent Fish Catch + Fish Speed on Fishing Eldervale — so
 * yesterday's cast really is weaker than today's.
 */
public final class AnglerLog {

    public static final int MAX_RANK = 10;
    static final int[] RANK_POINTS = {0, 3, 8, 15, 24, 35, 48, 63, 80, 100, 120};
    private static final String[] TITLES = {
            "Visitor", "Dock Rat", "Line Tender", "Pier Regular", "Reed Runner", "Lakehand",
            "Shoal Chaser", "Deepwater Angler", "Trophy Hunter", "Lake Legend", "Master of Eldervale"
    };
    public static final double CATCH_PER_RANK = 3.0d;
    public static final double SPEED_PER_RANK = 2.0d;

    private final FishIsle isle;

    AnglerLog(FishIsle isle) {
        this.isle = isle;
    }

    /** Points for one species line: first catch by rarity, then +1 / +1 / +2 for silver / gold / record weight. */
    static int points(Species species, AnglerProfiles.Entry entry) {
        if (entry == null || entry.count <= 0) {
            return 0;
        }
        int tier = Trophies.tier(species, entry.bestKg);
        int weight = tier >= 3 ? 4 : tier;
        return species.rarity().points() + weight;
    }

    public static int maxPoints() {
        int total = 0;
        for (Species species : Species.values()) {
            total += species.rarity().points() + 4;
        }
        return total;
    }

    public int points(AnglerProfiles.Profile profile) {
        int total = 0;
        for (Species species : Species.values()) {
            total += points(species, profile.log.get(species));
        }
        return total;
    }

    public static int rankFor(int points) {
        int rank = 0;
        for (int i = 1; i < RANK_POINTS.length; i++) {
            if (points >= RANK_POINTS[i]) {
                rank = i;
            }
        }
        return rank;
    }

    public int rank(Player player) {
        return rankFor(points(isle.profiles().of(player)));
    }

    public static String title(int rank) {
        return TITLES[Math.max(0, Math.min(MAX_RANK, rank))];
    }

    public static String coloredTitle(int rank) {
        String color = rank >= 9 ? "§6" : rank >= 7 ? "§d" : rank >= 5 ? "§9" : rank >= 3 ? "§a" : "§7";
        return color + title(rank);
    }

    /** Points still needed for the next rank (0 at max). */
    public static int toNext(int points) {
        int rank = rankFor(points);
        return rank >= MAX_RANK ? 0 : RANK_POINTS[rank + 1] - points;
    }

    /** 0–1 progress inside the current rank. */
    public static double fill(int points) {
        int rank = rankFor(points);
        if (rank >= MAX_RANK) {
            return 1.0d;
        }
        int from = RANK_POINTS[rank];
        int to = RANK_POINTS[rank + 1];
        return (points - from) / (double) Math.max(1, to - from);
    }

    /**
     * Pay any rank the player has reached but not been paid for yet (coins, XP, a title card).
     * Rank VI and up is announced to everyone on the isle.
     */
    void settleRank(Player player) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        int rank = rankFor(points(profile));
        if (rank <= profile.rankPaid) {
            return;
        }
        for (int reached = profile.rankPaid + 1; reached <= rank; reached++) {
            long coins = 250L * reached;
            int xp = 100 * reached;
            FishingSkills.coins(player, coins);
            FishingSkills.bonus(player, xp);
            player.sendMessage("§b✦ Angler Rank " + LakeText.roman(reached) + " §8· " + coloredTitle(reached)
                    + " §8· §6+" + LakeText.coins(coins) + " coins §8· §a+" + xp + " Fishing XP");
            player.sendMessage("§7On Eldervale you now fish with §a+" + (int) (CATCH_PER_RANK * reached)
                    + " Fish Catch §7and §a+" + (int) (SPEED_PER_RANK * reached) + " Fish Speed§7 — for good.");
            if (reached >= 6) {
                String line = "§b✦ §f" + player.getName() + " §7is now " + coloredTitle(reached)
                        + " §8(Angler Rank " + LakeText.roman(reached) + ")";
                for (Player visitor : LakeWorld.visitors()) {
                    if (!visitor.equals(player)) {
                        visitor.sendMessage(line);
                    }
                }
            }
        }
        profile.rankPaid = rank;
        isle.profiles().markDirty();
        player.showTitle(Title.title(
                LakeText.legacy("§bAngler Rank " + LakeText.roman(rank)),
                LakeText.legacy(coloredTitle(rank)),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(600))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.1f);
    }

    /** Isle-only stats for {@link FishIsle#getStat}. */
    double catchBonus(Player player) {
        return CATCH_PER_RANK * rank(player);
    }

    double speedBonus(Player player) {
        return SPEED_PER_RANK * rank(player);
    }
}
