package de.aetherion.mining.isle;

import net.kyori.adventure.title.Title;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Ore Mastery — the long game. Every ore family keeps a lifetime count; tiers I–VII pay coins,
 * Mining XP and a permanent Fortune bonus that only applies to that ore. Tiers IV and VII add
 * <b>ore-specific Mining Power</b> — it only ever helps the ore you've already mastered, so it
 * speeds you up without opening a gate early.
 *
 * <p>Mining Eldervale and The Veins both count double, so the island and its deep annex are
 * where mastery actually moves. Rarer families need far fewer breaks per tier.
 */
public final class OreMastery {

    /** Breaks needed for tier 1..7 before the family's scale (index = tier). */
    private static final long[] BASE = {0L, 150L, 600L, 2_000L, 6_000L, 15_000L, 40_000L, 100_000L};
    public static final int MAX_TIER = BASE.length - 1;
    private static final long[] COINS = {0L, 300L, 900L, 2_500L, 6_000L, 14_000L, 32_000L, 75_000L};
    private static final double FORTUNE_PER_TIER = 5.0d;

    private final MineIsle isle;

    OreMastery(MineIsle isle) {
        this.isle = isle;
    }

    public static long threshold(IsleOre ore, int tier) {
        if (tier <= 0) {
            return 0L;
        }
        int clamped = Math.min(MAX_TIER, tier);
        return Math.max(tier, Math.round(BASE[clamped] * ore.scale()));
    }

    public int tier(Player player, IsleOre ore) {
        return player == null || ore == null ? 0 : isle.profiles().of(player).tier(ore);
    }

    public long count(Player player, IsleOre ore) {
        return player == null || ore == null ? 0L : isle.profiles().of(player).mined(ore);
    }

    /** Ore-only Fortune from mastery of the ore being mined. */
    public double oreFortune(Player player, IsleOre ore) {
        return ore == null || ore == IsleOre.STONE ? 0.0d : fortuneAt(tier(player, ore));
    }

    /** Ore-specific Mining Power: +3 at IV, +8 at VII (that ore only). */
    public double orePower(Player player, IsleOre ore) {
        return ore == null || ore == IsleOre.STONE ? 0.0d : powerAt(tier(player, ore));
    }

    public static double fortuneAt(int tier) {
        return tier * FORTUNE_PER_TIER;
    }

    public static double powerAt(int tier) {
        return tier >= 7 ? 8.0d : tier >= 4 ? 3.0d : 0.0d;
    }

    public static long coinsAt(IsleOre ore, int tier) {
        long base = tier >= 0 && tier < COINS.length ? COINS[tier] : 0L;
        double valueScale = Math.max(0.6d, Math.min(2.5d, Math.sqrt(ore.unitValue())));
        return Math.round(base * valueScale);
    }

    public static int xpAt(int tier) {
        return 40 * tier * tier;
    }

    public static int tierFor(IsleOre ore, long mined) {
        int tier = 0;
        for (int i = 1; i <= MAX_TIER; i++) {
            if (mined >= threshold(ore, i)) {
                tier = i;
            }
        }
        return tier;
    }

    /** Fill toward the next tier, 0..1 (1 at max). */
    public static double fill(IsleOre ore, long mined) {
        int tier = tierFor(ore, mined);
        if (tier >= MAX_TIER) {
            return 1.0d;
        }
        long from = threshold(ore, tier);
        long to = threshold(ore, tier + 1);
        return (mined - from) / (double) Math.max(1L, to - from);
    }

    /** One break of {@code ore} worth {@code weight} ledger marks. */
    void onMined(Player player, IsleOre ore, int weight) {
        if (ore == null || weight <= 0) {
            return;
        }
        MineProfiles.Profile profile = isle.profiles().of(player);
        long total = profile.mined(ore) + weight;
        profile.mined.put(ore, total);
        isle.profiles().markDirty();
        int reached = tierFor(ore, total);
        int had = profile.tier(ore);
        while (had < reached) {
            had++;
            profile.tiers.put(ore, had);
            celebrate(player, ore, had);
        }
    }

    /** DEV: force every family to {@code tier} (counts set to the threshold, rewards not paid). */
    void setAll(Player player, int tier) {
        int clamped = Math.max(0, Math.min(MAX_TIER, tier));
        MineProfiles.Profile profile = isle.profiles().of(player);
        for (IsleOre ore : IsleOre.values()) {
            profile.mined.put(ore, threshold(ore, clamped));
            profile.tiers.put(ore, clamped);
        }
        isle.profiles().markDirty();
    }

    private void celebrate(Player player, IsleOre ore, int tier) {
        long coins = coinsAt(ore, tier);
        int xp = xpAt(tier);
        MineSkills.coins(player, coins);
        MineSkills.bonus(player, xp);
        String perk = ore == IsleOre.STONE
                ? "Stonecutter's pride"
                : "+" + (int) fortuneAt(tier) + " " + ore.display() + " Fortune";
        if (powerAt(tier) > powerAt(tier - 1)) {
            perk += " · +" + (int) powerAt(tier) + " " + ore.display() + " Mining Power";
        }
        player.showTitle(Title.title(
                MineText.legacy(ore.color() + ore.display() + " Mastery " + MineText.roman(tier)),
                MineText.legacy("§e" + perk + " §8· §6+" + MineText.coins(coins) + " coins"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))
        ));
        player.sendMessage("§6✦ " + ore.colored() + " Mastery " + MineText.roman(tier) + " §8· §7" + perk
                + " §8(permanent, that ore only) §8· §6+" + MineText.coins(coins) + " coins §8· §a+" + xp + " Mining XP");
        if (tier < MAX_TIER) {
            player.sendMessage("§8Next tier at " + MineText.coins(threshold(ore, tier + 1)) + " "
                    + ore.display().toLowerCase() + ". Eldervale and The Veins count double.");
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.1f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.3f, 1.5f);
        player.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1.0, 0), 24, 0.6, 0.6, 0.6, 0.05);
        player.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.4, 0), 20, 0.5, 0.5, 0.5, 0.0,
                new Particle.DustOptions(ore.glow(), 1.4f));
    }
}
