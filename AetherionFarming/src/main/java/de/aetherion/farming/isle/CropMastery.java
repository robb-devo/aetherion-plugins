package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.title.Title;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Crop Mastery — the long game. Every crop keeps a lifetime harvest count; tiers I–VI pay coins,
 * Farming XP and a permanent Fortune bonus that only applies to that crop. Harvests on Eldervale
 * count double, so the isle is where mastery actually moves.
 */
public final class CropMastery {

    /** Harvests needed for tier 1..6 (index = tier). */
    public static final long[] THRESHOLDS = {0L, 250L, 1_000L, 4_000L, 12_000L, 35_000L, 100_000L};
    public static final int MAX_TIER = THRESHOLDS.length - 1;
    private static final long[] COINS = {0L, 250L, 750L, 2_000L, 5_000L, 12_000L, 30_000L};
    private static final double FORTUNE_PER_TIER = 6.0d;

    private final FarmIsle isle;

    CropMastery(FarmIsle isle) {
        this.isle = isle;
    }

    public int tier(Player player, IsleCrop crop) {
        return player == null || crop == null ? 0 : isle.profiles().of(player).tier(crop);
    }

    public long count(Player player, IsleCrop crop) {
        return player == null || crop == null ? 0L : isle.profiles().of(player).harvested(crop);
    }

    /** Crop-only Fortune from mastery of the crop being harvested. */
    public double cropFortune(Player player, IsleCrop crop) {
        return tier(player, crop) * FORTUNE_PER_TIER;
    }

    public static double fortuneAt(int tier) {
        return tier * FORTUNE_PER_TIER;
    }

    public static long coinsAt(int tier) {
        return tier >= 0 && tier < COINS.length ? COINS[tier] : 0L;
    }

    public static int xpAt(int tier) {
        return 50 * tier * tier;
    }

    public static int tierFor(long harvested) {
        int tier = 0;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (harvested >= THRESHOLDS[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /** Fill toward the next tier, 0..1 (1 at max). */
    public static double fill(long harvested) {
        int tier = tierFor(harvested);
        if (tier >= MAX_TIER) {
            return 1.0d;
        }
        long from = THRESHOLDS[tier];
        long to = THRESHOLDS[tier + 1];
        return (harvested - from) / (double) Math.max(1L, to - from);
    }

    void onHarvest(Player player, IsleCrop crop, boolean onIsle) {
        if (crop == null) {
            return;
        }
        int weight = onIsle ? Math.max(1, isle.plugin().getConfig().getInt("crop-mastery.isle-weight", 2)) : 1;
        IsleProfiles.Profile profile = isle.profiles().of(player);
        long total = profile.harvested(crop) + weight;
        profile.harvested.put(crop, total);
        isle.profiles().markDirty();
        int reached = tierFor(total);
        int had = profile.tier(crop);
        while (had < reached) {
            had++;
            profile.tiers.put(crop, had);
            celebrate(player, crop, had);
        }
    }

    /** DEV: force every crop to {@code tier} (counts set to the threshold, rewards not paid). */
    void setAll(Player player, int tier) {
        int clamped = Math.max(0, Math.min(MAX_TIER, tier));
        IsleProfiles.Profile profile = isle.profiles().of(player);
        for (IsleCrop crop : IsleCrop.values()) {
            profile.harvested.put(crop, THRESHOLDS[clamped]);
            profile.tiers.put(crop, clamped);
        }
        isle.profiles().markDirty();
    }

    private void celebrate(Player player, IsleCrop crop, int tier) {
        long coins = coinsAt(tier);
        int xp = xpAt(tier);
        FarmingSkills.coins(player, coins);
        FarmingSkills.bonus(player, xp);
        String perk = "+" + (int) fortuneAt(tier) + " " + crop.display() + " Fortune";
        player.showTitle(Title.title(
                IsleText.legacy("§6" + crop.display() + " Mastery " + IsleText.roman(tier)),
                IsleText.legacy("§e" + perk + " §8· §6+" + IsleText.coins(coins) + " coins"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))
        ));
        player.sendMessage("§6✦ " + crop.display() + " Mastery " + IsleText.roman(tier) + " §8· §7" + perk
                + " (crop-only, permanent) §8· §6+" + IsleText.coins(coins) + " coins §8· §a+" + xp + " Farming XP");
        if (tier < MAX_TIER) {
            player.sendMessage("§8Next tier at " + IsleText.coins(THRESHOLDS[tier + 1]) + " "
                    + crop.display().toLowerCase() + ". Eldervale harvests count double.");
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 24, 0.6, 0.6, 0.6, 0.02);
        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.4, 0), 16, 0.4, 0.4, 0.4, 0.05);
    }
}
