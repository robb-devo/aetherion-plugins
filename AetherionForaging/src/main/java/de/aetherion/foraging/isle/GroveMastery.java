package de.aetherion.foraging.isle;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Grove Mastery — the long game. Every wood keeps a lifetime count of trees felled on the isle; tiers
 * I–VII pay coins and Foraging XP the moment you cross them, and leave permanent perks that only touch
 * <i>that</i> wood:
 * <ul>
 *   <li>+1 wood cap per tier (a tree of it pays up to +7 more logs at VII),</li>
 *   <li>+0.25% heartwood chance per tier,</li>
 *   <li>IV: the CHOP window is one cell wider on that wood,</li>
 *   <li>VII: Crown Finds ×1.5 on that wood, and the title "Warden of …".</li>
 * </ul>
 * A Titan counts twice. Grove Born sometimes counts a fell twice.
 */
public final class GroveMastery {

    public static final int MAX_TIER = 7;
    private static final int[] BASE = {10, 40, 120, 300, 700, 1500, 3000};
    private static final long[] COINS = {150L, 400L, 900L, 1800L, 3500L, 6500L, 12000L};

    private final ForageIsle isle;

    GroveMastery(ForageIsle isle) {
        this.isle = isle;
    }

    public static int threshold(Wood wood, int tier) {
        if (tier <= 0) {
            return 0;
        }
        int t = Math.min(MAX_TIER, tier);
        return Math.max(1, (int) Math.round(BASE[t - 1] * wood.scale()));
    }

    public static int tier(ForageProfile profile, Wood wood) {
        int fells = profile.fells(wood);
        int tier = 0;
        while (tier < MAX_TIER && fells >= threshold(wood, tier + 1)) {
            tier++;
        }
        return tier;
    }

    public static long coins(int tier) {
        return tier <= 0 ? 0L : COINS[Math.min(MAX_TIER, tier) - 1];
    }

    /** "Frostpine II ▮▮▮▯▯ 34/120" style progress for the tally / menus. */
    public static String progress(ForageProfile profile, Wood wood) {
        int tier = tier(profile, wood);
        int fells = profile.fells(wood);
        if (tier >= MAX_TIER) {
            return wood.colored() + " " + ForageText.roman(tier) + " §6✦ mastered";
        }
        int from = threshold(wood, tier);
        int to = threshold(wood, tier + 1);
        double fill = to <= from ? 1.0d : (fells - from) / (double) (to - from);
        return wood.colored() + " " + ForageText.roman(tier) + " " + ForageText.cells(fill, "§a") + " §7" + fells + "§8/§7" + to;
    }

    public static int woodCapBonus(ForageProfile profile, Wood wood) {
        return wood == null ? 0 : tier(profile, wood);
    }

    public static double heartwoodBonus(ForageProfile profile, Wood wood) {
        return wood == null ? 0.0d : tier(profile, wood) * 0.0025d;
    }

    public static boolean widerWindow(ForageProfile profile, Wood wood) {
        return wood != null && tier(profile, wood) >= 4;
    }

    public static boolean findBoost(ForageProfile profile, Wood wood) {
        return wood != null && tier(profile, wood) >= MAX_TIER;
    }

    /** Count a fell; pays every tier crossed. */
    void count(Player player, ForageProfile profile, Wood wood, boolean titan) {
        if (wood == null) {
            return;
        }
        int add = titan ? 2 : 1;
        double born = ForageBridge.scale(player, ForageBridge.GROVE_BORN);
        if (born > 0 && ThreadLocalRandom.current().nextDouble() < Math.min(0.6d, 0.25d * born)) {
            add++;
        }
        profile.fells.merge(wood.key(), add, Integer::sum);
        profile.dirty = true;
        int tier = tier(profile, wood);
        int paid = profile.masteryPaid.getOrDefault(wood.key(), 0);
        while (paid < tier) {
            paid++;
            long coins = coins(paid);
            ForageBridge.coins(player, coins);
            ForageBridge.bonus(player, paid * 40);
            String perk = switch (paid) {
                case 4 -> " §8· §aCHOP window +1 on " + wood.display();
                case 7 -> " §8· §6Crown Finds ×1.5 on " + wood.display();
                default -> " §8· §a+1 wood cap on " + wood.display();
            };
            player.sendMessage("§2✦ Grove Mastery §8· " + wood.colored() + " " + ForageText.roman(paid)
                    + " §8· §6+" + ForageText.coins(coins) + " coins" + perk);
            player.playSound(player.getLocation(), paid >= MAX_TIER ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP,
                    0.7f, paid >= MAX_TIER ? 1.0f : 1.4f);
            if (paid >= MAX_TIER) {
                ForageText.card(player, "§6Warden of " + wood.display(), "§7Grove Mastery VII", 50);
                isle.standing().add(player, profile, 3, "mastery VII");
            } else if (paid == 4) {
                isle.standing().add(player, profile, 1, "mastery IV");
            }
        }
        profile.masteryPaid.put(wood.key(), paid);
    }
}
