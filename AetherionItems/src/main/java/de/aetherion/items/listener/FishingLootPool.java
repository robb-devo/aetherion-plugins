package de.aetherion.items.listener;

import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillService;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Aetherion fishing loot — no vanilla junk/treasure.
 * Weights lean on fishing skill + Fish Catch; stays mildly OP on purpose.
 */
public final class FishingLootPool {

    /** Guaranteed extra fish every this much Fish Catch. */
    public static final double CATCH_PER_EXTRA = 40.0d;
    public static final int EXTRA_CAP = 6;
    /** Catch-stat bonus toward compressed/compacted procs. */
    public static final double CATCH_COMPACT_DIV = 1200.0d;
    public static final double CATCH_COMPACT_CAP = 0.12d;

    private FishingLootPool() {
    }

    public static ItemStack rollPrimary(Player player, SkillService skills, double catchStat) {
        int fishingLevel = skills == null ? 1 : skills.fishingLevel(player);
        double scale = SkillProgression.effectMultiplier(fishingLevel);
        double catchBoost = 1.0d + Math.min(0.55d, Math.max(0.0d, catchStat) / 220.0d);
        WeatherBias weather = WeatherBias.of(player);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double roll = rng.nextDouble() * totalWeight(fishingLevel, scale, catchBoost, weather);
        double cursor = 0.0d;

        cursor += weightCod(fishingLevel, scale);
        if (roll < cursor) {
            return stack(Material.COD);
        }
        cursor += weightSalmon(fishingLevel, scale, catchBoost) * weather.salmonMul;
        if (roll < cursor) {
            return stack(Material.SALMON);
        }
        cursor += weightTropical(fishingLevel, scale);
        if (roll < cursor) {
            return stack(Material.TROPICAL_FISH);
        }
        cursor += weightPuffer(fishingLevel, scale, catchBoost);
        if (roll < cursor) {
            return stack(Material.PUFFERFISH);
        }
        cursor += weightKelp(fishingLevel, scale) * weather.kelpMul;
        if (roll < cursor) {
            return stack(Material.KELP);
        }
        cursor += weightPrismarine(fishingLevel, scale, catchBoost) * weather.prismarineMul;
        if (roll < cursor) {
            return stack(Material.PRISMARINE_SHARD);
        }
        return stack(Material.COD);
    }

    public static ItemStack maybeUpgrade(Player player, ItemManager items, ItemStack raw, double catchStat) {
        if (raw == null || raw.getType().isAir()) {
            return raw;
        }
        ItemStack compressed = ProgressionEffects.maybeCompress(player, items, raw);
        if (compressed != null) {
            compressed.setAmount(Math.max(1, raw.getAmount()));
            return compressed;
        }
        double catchBonus = Math.min(CATCH_COMPACT_CAP, Math.max(0.0d, catchStat) / CATCH_COMPACT_DIV);
        if (catchBonus <= 0.0d || ThreadLocalRandom.current().nextDouble() >= catchBonus) {
            return raw;
        }
        CompressedResource resource = CompressedResource.fromDrop(raw.getType());
        if (resource == null || !resource.isFishingDrop()) {
            return raw;
        }
        SkillService skills = AetherionItemsSkills.skills();
        double upgrade = skills == null ? 0.0d : skills.compactedUpgradeChance(player, false, false, false, true);
        if (upgrade > 0.0d && ThreadLocalRandom.current().nextDouble() < upgrade) {
            ItemStack compacted = resource.compacted();
            if (compacted != null) {
                compacted.setAmount(1);
                return compacted;
            }
        }
        ItemStack compressedOnly = resource.compressed();
        if (compressedOnly != null) {
            compressedOnly.setAmount(1);
            return compressedOnly;
        }
        return raw;
    }

    public static int extraCatches(double catchStat, int fishingLevel) {
        if (catchStat <= 0.0d) {
            return 0;
        }
        double effective = catchStat * (0.92d + 0.08d * SkillProgression.effectMultiplier(fishingLevel));
        int guaranteed = (int) Math.floor(effective / CATCH_PER_EXTRA);
        double remainder = effective - guaranteed * CATCH_PER_EXTRA;
        int extra = guaranteed;
        if (remainder > 0.0d && ThreadLocalRandom.current().nextDouble() * CATCH_PER_EXTRA < remainder) {
            extra++;
        }
        return Math.min(EXTRA_CAP, Math.max(0, extra));
    }

    public static double divingChance(double base, double catchStat, int fishingLevel) {
        return Math.min(0.055d, base * (1.0d + catchStat / 180.0d)
                * (0.85d + 0.15d * SkillProgression.effectMultiplier(fishingLevel)));
    }

    public static double compactedCrateChance(double base, double catchStat, int fishingLevel) {
        return Math.min(0.09d, base * (1.0d + catchStat / 160.0d)
                * (0.9d + 0.12d * SkillProgression.effectMultiplier(fishingLevel)));
    }

    public static double boosterChance(double base, double catchStat) {
        return Math.min(0.045d, base * (1.0d + catchStat / 280.0d));
    }

    /** True when the vanilla catch should be replaced by our pool. */
    public static boolean shouldReplace(ItemStack stack, ItemManager items) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        return items == null || !items.isAetherionItem(stack);
    }

    private static double totalWeight(int fishingLevel, double scale, double catchBoost, WeatherBias weather) {
        return weightCod(fishingLevel, scale)
                + weightSalmon(fishingLevel, scale, catchBoost) * weather.salmonMul
                + weightTropical(fishingLevel, scale)
                + weightPuffer(fishingLevel, scale, catchBoost)
                + weightKelp(fishingLevel, scale) * weather.kelpMul
                + weightPrismarine(fishingLevel, scale, catchBoost) * weather.prismarineMul;
    }

    private static double weightCod(int level, double scale) {
        // Softly fades as better fish unlock — still the reliable bite.
        return Math.max(28.0d, 72.0d - level * 0.28d) / Math.max(1.0d, 0.92d + 0.08d * scale);
    }

    private static double weightSalmon(int level, double scale, double catchBoost) {
        return (14.0d + level * 0.22d) * catchBoost * (0.9d + 0.1d * scale);
    }

    private static double weightTropical(int level, double scale) {
        return (6.0d + level * 0.08d) * (0.95d + 0.05d * scale);
    }

    private static double weightPuffer(int level, double scale, double catchBoost) {
        if (level < 8) {
            return 2.0d + level * 0.15d;
        }
        return (5.0d + level * 0.14d) * catchBoost * (0.9d + 0.1d * scale);
    }

    private static double weightKelp(int level, double scale) {
        return (8.0d + level * 0.05d) * (0.95d + 0.05d * scale);
    }

    private static double weightPrismarine(int level, double scale, double catchBoost) {
        if (level < 15) {
            return 1.5d + level * 0.08d;
        }
        return (4.0d + (level - 15) * 0.18d) * catchBoost * (0.85d + 0.15d * scale);
    }

    private static ItemStack stack(Material material) {
        return new ItemStack(material, 1);
    }

    /**
     * Soft read of forage-isle weather via {@code FishingWeatherHook} (no hard compile dep cycle).
     * Wet → more salmon/prismarine; fog → more kelp. No-op off-isle / if Foraging offline.
     */
    private static final class WeatherBias {
        final double salmonMul;
        final double kelpMul;
        final double prismarineMul;

        private WeatherBias(double salmonMul, double kelpMul, double prismarineMul) {
            this.salmonMul = salmonMul;
            this.kelpMul = kelpMul;
            this.prismarineMul = prismarineMul;
        }

        static WeatherBias of(Player player) {
            if (player == null || org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionForaging") == null) {
                return NONE;
            }
            try {
                Class<?> hook = Class.forName("de.aetherion.foraging.weather.FishingWeatherHook");
                boolean wet = Boolean.TRUE.equals(hook.getMethod("preferWetLoot", Player.class).invoke(null, player));
                boolean fog = Boolean.TRUE.equals(hook.getMethod("preferFogLoot", Player.class).invoke(null, player));
                if (!wet && !fog) {
                    return NONE;
                }
                return new WeatherBias(
                        wet ? 1.35d : 1.0d,
                        fog ? 1.45d : 1.0d,
                        wet ? 1.25d : 1.0d
                );
            } catch (Throwable ignored) {
                return NONE;
            }
        }

        private static final WeatherBias NONE = new WeatherBias(1.0d, 1.0d, 1.0d);
    }

    /** Tiny bridge so FishingLootPool does not hard-import the plugin class at the top for tests. */
    private static final class AetherionItemsSkills {
        private static SkillService skills() {
            de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
            return plugin == null ? null : plugin.getSkills();
        }
    }
}
