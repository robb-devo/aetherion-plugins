package de.aetherion.mining.isle;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * The Assay Ledger — where Mining's collections live.
 *
 * <ul>
 *   <li><b>Collections</b> read the real Codex counts (every ore you have ever broken, anywhere)
 *   and pay one-time milestone rewards at the Assayer. Nothing here keeps a second tally — if
 *   the Codex says 4,000 iron, the ledger says 4,000 iron.</li>
 *   <li><b>Collector rank</b>: every seven claimed milestones raise it one step, for a little more
 *   Crystal Find luck and a little ore Fortune on every family.</li>
 *   <li><b>Specimen Cabinet</b>: one slot per family × grade. A full row (Rough, Flawless, Perfect)
 *   adds permanent Fortune for that ore; a Heartstone crowns it with ore Mining Power; a full
 *   column pays out big.</li>
 * </ul>
 */
public final class AssayLedger {

    /** Codex counts for milestone 1..7 before the family's scale. */
    private static final long[] MILESTONES = {0L, 50L, 250L, 1_000L, 4_000L, 15_000L, 50_000L, 150_000L};
    public static final int MAX_MILESTONE = MILESTONES.length - 1;
    private static final long[] MILESTONE_COINS = {0L, 150L, 500L, 1_500L, 4_000L, 10_000L, 25_000L, 60_000L};
    private static final int STARS_PER_RANK = 7;
    private static final double ROW_FORTUNE = 5.0d;
    private static final double CROWN_POWER = 3.0d;

    private final MineIsle isle;

    AssayLedger(MineIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ collections

    public static long milestone(IsleOre ore, int index) {
        if (index <= 0) {
            return 0L;
        }
        int clamped = Math.min(MAX_MILESTONE, index);
        return Math.max(index, Math.round(MILESTONES[clamped] * ore.scale()));
    }

    public static long milestoneCoins(IsleOre ore, int index) {
        long base = index >= 0 && index < MILESTONE_COINS.length ? MILESTONE_COINS[index] : 0L;
        return Math.round(base * Math.max(0.6d, Math.min(2.5d, Math.sqrt(ore.unitValue()))));
    }

    public static int milestoneXp(int index) {
        return 30 * index * index;
    }

    /** Lifetime count from the Codex (the truth). */
    public long collected(Player player, IsleOre ore) {
        return MineSkills.codexBlocks(player, ore.codexId());
    }

    /** Highest milestone the Codex count has reached. */
    public int reached(Player player, IsleOre ore) {
        long count = collected(player, ore);
        int index = 0;
        for (int i = 1; i <= MAX_MILESTONE; i++) {
            if (count >= milestone(ore, i)) {
                index = i;
            }
        }
        return index;
    }

    public int claimable(Player player, IsleOre ore) {
        return Math.max(0, reached(player, ore) - isle.profiles().of(player).claimed(ore));
    }

    public int claimableTotal(Player player) {
        int total = 0;
        for (IsleOre ore : IsleOre.values()) {
            total += claimable(player, ore);
        }
        return total;
    }

    /** Claims every reached milestone of {@code ore}. Returns how many were paid. */
    public int claim(Player player, IsleOre ore) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        int from = profile.claimed(ore);
        int to = reached(player, ore);
        if (to <= from) {
            return 0;
        }
        int rankBefore = rank(player);
        long coins = 0L;
        int xp = 0;
        for (int i = from + 1; i <= to; i++) {
            coins += milestoneCoins(ore, i);
            xp += milestoneXp(i);
        }
        profile.claimed.put(ore, to);
        isle.profiles().markDirty();
        MineSkills.coins(player, coins);
        MineSkills.bonus(player, xp);
        player.sendMessage("§d✦ " + ore.colored() + " Collection " + MineText.roman(to) + " §8· §6+"
                + MineText.coins(coins) + " coins §8· §a+" + xp + " Mining XP");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.4f);
        int rankAfter = rank(player);
        if (rankAfter > rankBefore) {
            player.showTitle(Title.title(
                    MineText.legacy("§dCollector Rank " + MineText.roman(Math.min(10, rankAfter))),
                    MineText.legacy("§7+" + (int) Math.round((crystalMultiplier(player) - 1.0d) * 100) + "% Crystal Finds §8· §7+"
                            + (int) collectorFortune(player) + " ore Fortune"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2400), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        return to - from;
    }

    public int claimAll(Player player) {
        int total = 0;
        for (IsleOre ore : IsleOre.values()) {
            total += claim(player, ore);
        }
        return total;
    }

    /** Stars = claimed milestones across every family. */
    public int stars(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        int total = 0;
        for (IsleOre ore : IsleOre.values()) {
            total += profile.claimed(ore);
        }
        return total;
    }

    public static int maxStars() {
        return IsleOre.values().length * MAX_MILESTONE;
    }

    public int rank(Player player) {
        return stars(player) / STARS_PER_RANK;
    }

    /** +2% Crystal Find chance per Collector rank. */
    public double crystalMultiplier(Player player) {
        return player == null ? 1.0d : 1.0d + 0.02d * rank(player);
    }

    /** +1 ore Fortune per Collector rank (every family). */
    public double collectorFortune(Player player) {
        return player == null ? 0.0d : rank(player);
    }

    // ------------------------------------------------------------------ cabinet

    public int cabinetFilled(Player player) {
        return isle.profiles().of(player).cabinet.size();
    }

    public static int cabinetSize() {
        return IsleOre.crystals().length * Grade.values().length;
    }

    public boolean rowComplete(Player player, IsleOre ore) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        return profile.hasSpecimen(ore, Grade.ROUGH) && profile.hasSpecimen(ore, Grade.FLAWLESS)
                && profile.hasSpecimen(ore, Grade.PERFECT);
    }

    public boolean crowned(Player player, IsleOre ore) {
        return isle.profiles().of(player).hasSpecimen(ore, Grade.HEARTSTONE);
    }

    public boolean columnComplete(Player player, Grade grade) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        for (IsleOre ore : IsleOre.crystals()) {
            if (!profile.hasSpecimen(ore, grade)) {
                return false;
            }
        }
        return true;
    }

    /** Ore Fortune from the cabinet row of {@code ore} plus the Collector rank. */
    public double oreFortune(Player player, IsleOre ore) {
        if (player == null || ore == null || ore == IsleOre.STONE) {
            return 0.0d;
        }
        return collectorFortune(player) + (rowComplete(player, ore) ? ROW_FORTUNE : 0.0d);
    }

    /** Ore Mining Power from a Heartstone crown (that ore only). */
    public double orePower(Player player, IsleOre ore) {
        return player != null && ore != null && ore.hasCrystal() && crowned(player, ore) ? CROWN_POWER : 0.0d;
    }

    public static long columnCoins(Grade grade) {
        return switch (grade) {
            case ROUGH -> 2_500L;
            case FLAWLESS -> 10_000L;
            case PERFECT -> 40_000L;
            case HEARTSTONE -> 150_000L;
        };
    }

    /** A specimen was just claimed — register it and pay any row / crown / column it completes. */
    void onSpecimen(Player player, IsleOre ore, Grade grade) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (!profile.cabinet.add(ore.id() + ":" + grade.id())) {
            return;
        }
        isle.profiles().markDirty();
        int filled = profile.cabinet.size();
        player.sendMessage("§d✦ New for your Specimen Cabinet: " + grade.colored() + " " + ore.color() + ore.crystalName()
                + " §8(" + filled + "/" + cabinetSize() + ")");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.2f);
        if (grade != Grade.HEARTSTONE && rowComplete(player, ore) && profile.cabinetRewards.add("row:" + ore.id())) {
            isle.profiles().markDirty();
            player.showTitle(Title.title(
                    MineText.legacy(ore.color() + ore.display() + " Row Complete"),
                    MineText.legacy("§a+" + (int) ROW_FORTUNE + " " + ore.display() + " Fortune §8· §7permanent"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        if (grade == Grade.HEARTSTONE && profile.cabinetRewards.add("crown:" + ore.id())) {
            isle.profiles().markDirty();
            player.sendMessage("§6✦ " + ore.display() + " is crowned §8· §7+" + (int) CROWN_POWER + " "
                    + ore.display() + " Mining Power, for good.");
        }
        if (columnComplete(player, grade) && profile.cabinetRewards.add("col:" + grade.id())) {
            isle.profiles().markDirty();
            long coins = columnCoins(grade);
            MineSkills.coins(player, coins);
            MineSkills.bonus(player, 400);
            player.sendMessage("§d✦ Every " + grade.colored() + " §dspecimen on the isle is in your cabinet! §6+"
                    + MineText.coins(coins) + " coins §8· §a+400 Mining XP");
            player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 40, 0.6, 0.6, 0.6, 0.1);
            if (grade == Grade.PERFECT || grade == Grade.HEARTSTONE) {
                var line = MineText.legacy("§d✦ " + player.getName() + " §7completed the §f" + grade.display()
                        + " §7column of the Eldervale Specimen Cabinet.");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(line);
                }
            }
        }
    }

    /** DEV: fill or clear the cabinet (rewards not paid). */
    void devCabinet(Player player, boolean fill) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.cabinet.clear();
        profile.cabinetRewards.clear();
        if (fill) {
            for (IsleOre ore : IsleOre.crystals()) {
                for (Grade grade : Grade.values()) {
                    profile.cabinet.add(ore.id() + ":" + grade.id());
                }
                profile.cabinetRewards.add("row:" + ore.id());
                profile.cabinetRewards.add("crown:" + ore.id());
            }
            for (Grade grade : Grade.values()) {
                profile.cabinetRewards.add("col:" + grade.id());
            }
        }
        isle.profiles().markDirty();
    }

    /** DEV: forget every claimed milestone so they can be claimed again. */
    void devResetClaims(Player player) {
        isle.profiles().of(player).claimed.clear();
        isle.profiles().markDirty();
    }
}
