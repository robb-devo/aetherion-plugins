package de.aetherion.foraging.ritual;

import de.aetherion.foraging.weather.WeatherKind;
import de.aetherion.items.economy.IsleHeartwood;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dual-heartwood grove rites — small pool, costs 1–3 + 1–3.
 */
public final class GroveRiteCatalog {

    public record Cost(IsleHeartwood heart, int amount) {
        public String label() {
            return amount + "× " + heart.coloredName();
        }
    }

    public record Rite(
            String id,
            String title,
            String blurb,
            Cost costA,
            Cost costB,
            PotionEffectType primary,
            int primaryAmp,
            PotionEffectType secondary,
            WeatherKind weather,
            Material icon
    ) {
        public List<String> costLore() {
            return List.of(
                    "§7Cost:",
                    "§8· §f" + costA.label(),
                    "§8· §f" + costB.label()
            );
        }
    }

    private static final List<Rite> ALL = List.of(
            rite("elderbark", "Elderbark Rite", "Timber flows easier for a while.",
                    IsleHeartwood.OAK, 2, IsleHeartwood.BIRCH, 1,
                    PotionEffectType.HASTE, 0, PotionEffectType.LUCK, null, Material.OAK_LOG),
            rite("frostpitch", "Frostpitch Rite", "Snow settles around you.",
                    IsleHeartwood.SPRUCE, 2, IsleHeartwood.OAK, 1,
                    PotionEffectType.RESISTANCE, 0, null, WeatherKind.SNOW, Material.SPRUCE_LOG),
            rite("brine_root", "Brine Root Rite", "Mist and drizzle cling to the grove.",
                    IsleHeartwood.MANGROVE, 2, IsleHeartwood.DARK_OAK, 1,
                    PotionEffectType.WATER_BREATHING, 0, null, WeatherKind.FOG, Material.MANGROVE_LOG),
            rite("blossom", "Blossom Rite", "Skies clear. Fortune smiles briefly.",
                    IsleHeartwood.CHERRY, 2, IsleHeartwood.BIRCH, 2,
                    PotionEffectType.LUCK, 0, null, WeatherKind.CLEAR, Material.CHERRY_LOG),
            rite("jade_culm", "Jade Culm Rite", "Legs and axe move like wind in culms.",
                    IsleHeartwood.BAMBOO, 2, IsleHeartwood.JUNGLE, 1,
                    PotionEffectType.SPEED, 0, PotionEffectType.HASTE, WeatherKind.WINDY, Material.BAMBOO),
            rite("pale_heart", "Pale Heart Rite", "Light steps through the canopy.",
                    IsleHeartwood.BIRCH, 2, IsleHeartwood.CHERRY, 1,
                    PotionEffectType.SPEED, 0, null, null, Material.BIRCH_LOG),
            rite("canopy_amber", "Canopy Amber Rite", "The jungle answers with vigor.",
                    IsleHeartwood.JUNGLE, 2, IsleHeartwood.BAMBOO, 2,
                    PotionEffectType.STRENGTH, 0, null, WeatherKind.DRIZZLE, Material.JUNGLE_LOG),
            rite("savanna_thorn", "Savanna Thorn Rite", "Heat-hardened focus.",
                    IsleHeartwood.ACACIA, 2, IsleHeartwood.OAK, 1,
                    PotionEffectType.FIRE_RESISTANCE, 0, null, WeatherKind.CLEAR, Material.ACACIA_LOG),
            rite("nightbark", "Nightbark Rite", "Shadows thicken; night vision blooms.",
                    IsleHeartwood.DARK_OAK, 2, IsleHeartwood.MANGROVE, 2,
                    PotionEffectType.NIGHT_VISION, 0, null, WeatherKind.FOG, Material.DARK_OAK_LOG),
            // Stronger duals — higher cost
            rite("elderbark_ii", "Elderbark Chorus", "Deep haste — the grove sings with you.",
                    IsleHeartwood.OAK, 3, IsleHeartwood.SPRUCE, 2,
                    PotionEffectType.HASTE, 1, PotionEffectType.LUCK, null, Material.ENCHANTED_BOOK),
            rite("jade_gale", "Jade Gale", "A storm of culms at your heels.",
                    IsleHeartwood.BAMBOO, 3, IsleHeartwood.CHERRY, 2,
                    PotionEffectType.SPEED, 1, PotionEffectType.HASTE, WeatherKind.WINDY, Material.FEATHER)
    );

    private GroveRiteCatalog() {
    }

    public static List<Rite> all() {
        return ALL;
    }

    /** Three distinct random offers for one player session. */
    public static List<Rite> rollOffers(int count) {
        List<Rite> pool = new ArrayList<>(ALL);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int n = Math.min(Math.max(1, count), pool.size());
        return List.copyOf(pool.subList(0, n));
    }

    private static Rite rite(
            String id,
            String title,
            String blurb,
            IsleHeartwood a,
            int aAmt,
            IsleHeartwood b,
            int bAmt,
            PotionEffectType primary,
            int amp,
            PotionEffectType secondary,
            WeatherKind weather,
            Material icon
    ) {
        return new Rite(id, title, blurb, new Cost(a, aAmt), new Cost(b, bAmt),
                primary, amp, secondary, weather, icon);
    }
}
