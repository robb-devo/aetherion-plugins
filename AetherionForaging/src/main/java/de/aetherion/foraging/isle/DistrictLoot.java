package de.aetherion.foraging.isle;

import de.aetherion.foraging.weather.WeatherKind;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * District extras: every forest lets go of something of its own when a tree comes down — saplings,
 * petals, cocoa, propagules… Vanilla goods, small stacks, one roll per fell, so they feed cooking and
 * building without flooding anyone's inventory. Weather tilts it: snow thickens the Ridge, rain the
 * Mangroves and the Crown, fog the Hollow, a clear sky the Mesa.
 */
public final class DistrictLoot {

    private record Extra(Material item, int min, int max, int weight) {
    }

    private static final Map<Grove, List<Extra>> TABLE = new EnumMap<>(Grove.class);

    static {
        TABLE.put(Grove.FROSTPINE, List.of(
                new Extra(Material.SWEET_BERRIES, 2, 4, 40), new Extra(Material.SNOWBALL, 2, 5, 30),
                new Extra(Material.SPRUCE_SAPLING, 1, 1, 20), new Extra(Material.FERN, 1, 2, 10)));
        TABLE.put(Grove.BLOSSOM, List.of(
                new Extra(Material.PINK_PETALS, 2, 5, 40), new Extra(Material.HONEYCOMB, 1, 1, 25),
                new Extra(Material.CHERRY_SAPLING, 1, 1, 20), new Extra(Material.STICK, 2, 4, 15)));
        TABLE.put(Grove.SUNSCAR, List.of(
                new Extra(Material.STICK, 3, 6, 40), new Extra(Material.ACACIA_SAPLING, 1, 1, 25),
                new Extra(Material.GOLD_NUGGET, 1, 2, 15), new Extra(Material.DEAD_BUSH, 1, 1, 20)));
        TABLE.put(Grove.ELDERWOOD, List.of(
                new Extra(Material.APPLE, 1, 2, 40), new Extra(Material.OAK_SAPLING, 1, 1, 20),
                new Extra(Material.BIRCH_SAPLING, 1, 1, 20), new Extra(Material.STICK, 2, 4, 20)));
        TABLE.put(Grove.GLOAMWOOD, List.of(
                new Extra(Material.RED_MUSHROOM, 1, 3, 30), new Extra(Material.BROWN_MUSHROOM, 1, 3, 30),
                new Extra(Material.GLOW_BERRIES, 1, 2, 25), new Extra(Material.DARK_OAK_SAPLING, 1, 1, 15)));
        TABLE.put(Grove.BRINEFALL, List.of(
                new Extra(Material.MANGROVE_PROPAGULE, 1, 2, 35), new Extra(Material.CLAY_BALL, 2, 3, 30),
                new Extra(Material.LILY_PAD, 1, 1, 20), new Extra(Material.SLIME_BALL, 1, 1, 15)));
        TABLE.put(Grove.CANOPY_CROWN, List.of(
                new Extra(Material.COCOA_BEANS, 2, 4, 35), new Extra(Material.MELON_SLICE, 2, 4, 30),
                new Extra(Material.BAMBOO, 2, 4, 20), new Extra(Material.JUNGLE_SAPLING, 1, 1, 15)));
    }

    private final ForageIsle isle;

    DistrictLoot(ForageIsle isle) {
        this.isle = isle;
    }

    void onFell(Player player, FellContext ctx, Grove grove) {
        if (grove == null) {
            return;
        }
        double chance = isle.config().tuning("extras-chance", 0.22d) * weatherFactor(isle.weatherKind(player), grove);
        if (isle.lured(ctx.anchor())) {
            chance *= 2.0d;
        }
        if (ctx.titan()) {
            chance += 0.25d;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= Math.min(0.85d, chance)) {
            return;
        }
        List<Extra> extras = TABLE.get(grove);
        int total = 0;
        for (Extra extra : extras) {
            total += extra.weight();
        }
        int pick = r.nextInt(total);
        for (Extra extra : extras) {
            pick -= extra.weight();
            if (pick < 0) {
                int amount = extra.min() + (extra.max() > extra.min() ? r.nextInt(extra.max() - extra.min() + 1) : 0);
                ForageBridge.give(player, new ItemStack(extra.item(), amount), ctx.anchor());
                return;
            }
        }
    }

    static double weatherFactor(WeatherKind kind, Grove grove) {
        if (kind == null) {
            return 1.0d;
        }
        return switch (grove) {
            case FROSTPINE -> kind == WeatherKind.SNOW ? 2.0d : 1.0d;
            case BRINEFALL, CANOPY_CROWN, ELDERWOOD -> kind == WeatherKind.RAIN || kind == WeatherKind.DRIZZLE ? 1.5d : 1.0d;
            case GLOAMWOOD -> kind == WeatherKind.FOG ? 1.5d : 1.0d;
            case SUNSCAR -> kind == WeatherKind.CLEAR ? 1.25d : 1.0d;
            case BLOSSOM -> kind == WeatherKind.WINDY ? 1.5d : 1.0d;
        };
    }
}
