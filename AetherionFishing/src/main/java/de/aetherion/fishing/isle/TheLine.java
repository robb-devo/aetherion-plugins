package de.aetherion.fishing.isle;

import de.aetherion.fishing.CastHooks;
import de.aetherion.fishing.CastSession;
import de.aetherion.fishing.FishingSkills;
import de.aetherion.items.util.InventoryDrops;

import net.kyori.adventure.title.Title;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The cast on Fishing Eldervale — the same strike bar as the harbour, with stakes:
 * <ul>
 *   <li><b>What's on the line</b> is decided at the bite. Commons show their name; rares arrive as
 *   "something heavy" with a narrower window; a legendary goes quiet and the marker runs fast.</li>
 *   <li><b>Heat</b>: the clean-catch streak warms the water — Warm 3, Hot 5, Boiling 10,
 *   Whirlpool 20 — and each step pulls rarer fish. One miss and it's gone.</li>
 *   <li><b>Perfect chains</b>: back-to-back gold reels climb a scale and weigh the fish again —
 *   perfect fishing lands heavier fish.</li>
 *   <li><b>Second chance</b>: lose a rare and it keeps circling that water for 40 seconds.</li>
 * </ul>
 */
public final class TheLine {

    public static final int WARM = 3;
    public static final int HOT = 5;
    public static final int BOILING = 10;
    public static final int WHIRLPOOL = 20;
    private static final long SECOND_CHANCE_MS = 40_000L;
    private static final int[] PENTATONIC = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};
    /** Isle catches before the bite card stops explaining the bar. */
    private static final long TUTORIAL_CATCHES = 15L;

    /** What took the bait, parked on the cast. */
    record Bite(Species species, String water, boolean shoal, Bait bait, int heat, boolean returning) {
    }

    private record Chance(Species species, String water, long until) {
    }

    private final FishIsle isle;
    private final Map<UUID, Integer> chains = new ConcurrentHashMap<>();
    private final Map<UUID, Chance> chances = new ConcurrentHashMap<>();
    private final Map<UUID, Species> forced = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fountainCoins = new ConcurrentHashMap<>();
    /** Last catch / miss line per player — ambient action-bar hints wait until it has been read. */
    private final Map<UUID, Long> lastBeat = new ConcurrentHashMap<>();

    TheLine(FishIsle isle) {
        this.isle = isle;
    }

    void forget(UUID id) {
        chains.remove(id);
        chances.remove(id);
        forced.remove(id);
        fountainCoins.remove(id);
        lastBeat.remove(id);
    }

    /** True when no catch / miss line went up in the last few seconds. */
    boolean quiet(UUID id) {
        Long last = lastBeat.get(id);
        return last == null || System.currentTimeMillis() - last > 3_500L;
    }

    // ------------------------------------------------------------------ heat

    public static int heat(int streak) {
        if (streak >= WHIRLPOOL) {
            return 4;
        }
        if (streak >= BOILING) {
            return 3;
        }
        if (streak >= HOT) {
            return 2;
        }
        return streak >= WARM ? 1 : 0;
    }

    public static String heatName(int heat) {
        return switch (heat) {
            case 1 -> "§eWarm";
            case 2 -> "§6Hot Water";
            case 3 -> "§cBoiling";
            case 4 -> "§dWhirlpool";
            default -> "";
        };
    }

    int chain(UUID id) {
        return chains.getOrDefault(id, 0);
    }

    /** DEV: the next bite on the isle is this species. */
    public void force(Player player, Species species) {
        forced.put(player.getUniqueId(), species);
    }

    // ------------------------------------------------------------------ hooks

    double settle(Player player, Location hook) {
        double factor = 1.0d;
        if (isle.shoals().inShoal(hook)) {
            factor *= isle.shoals().waitFactor(player);
        }
        if (isle.events().runActive() && LakeWorld.lakeLevel(hook.getY())) {
            factor *= LakeEvents.RUN_WAIT;
        }
        Bait bait = isle.profiles().of(player).activeBait();
        if (bait == Bait.BREADCRUMB) {
            factor *= 0.8d;
        }
        return Math.max(0.2d, factor);
    }

    int extraLures(Location hook) {
        return isle.shoals().inShoal(hook) ? 1 : 0;
    }

    String waitTag(Player player, Location hook, int streak) {
        StringBuilder tag = new StringBuilder();
        Waters.Water water = isle.waters().at(hook);
        tag.append(water == null ? "§3open water" : water.colored());
        int heat = heat(streak);
        if (heat > 0) {
            tag.append(" §8· ").append(heatName(heat));
        }
        if (isle.shoals().inShoal(hook)) {
            tag.append(" §8· §b≋ Shoal");
        }
        Chance chance = chances.get(player.getUniqueId());
        if (chance != null && water != null && water.id().equals(chance.water())) {
            long left = (chance.until() - System.currentTimeMillis()) / 1000L;
            if (left > 0L) {
                tag.append(" §8· §5circling ").append(left).append("s");
            }
        }
        Bait bait = isle.profiles().of(player).activeBait();
        if (bait != null) {
            tag.append(" §8· §7").append(bait.display());
        }
        return tag.toString();
    }

    CastHooks.BiteCue bite(Player player, CastSession session, Location hook, int streak) {
        UUID id = player.getUniqueId();
        Waters.Water water = isle.waters().at(hook);
        String waterId = water == null ? null : water.id();
        boolean shoal = isle.shoals().inShoal(hook);
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        Bait bait = profile.activeBait();
        int heat = heat(streak);

        Species species = forced.remove(id);
        boolean returning = false;
        Chance chance = chances.get(id);
        if (species == null && chance != null) {
            if (chance.until() < System.currentTimeMillis()) {
                chances.remove(id);
            } else if (waterId != null && waterId.equals(chance.water())) {
                chances.remove(id);
                double odds = chance.species().rarity() == Species.Rarity.LEGENDARY ? 0.5d : 0.6d;
                if (ThreadLocalRandom.current().nextDouble() < odds) {
                    species = chance.species();
                    returning = true;
                }
            }
        }
        if (species == null) {
            species = roll(player, water, hook, shoal, bait, heat);
        }
        if (bait != null) {
            spendBait(player, profile, bait);
        }
        session.tag(new Bite(species, waterId, shoal, bait, heat, returning));

        Species.Rarity rarity = species.rarity();
        if (rarity == Species.Rarity.RARE) {
            session.resizeZone(-1);
        } else if (rarity == Species.Rarity.LEGENDARY) {
            session.resizeZone(-1);
            session.fastMarker(true);
        }
        if (FishingSkills.has(player, FishingSkills.STEADY_LINE)) {
            session.perfectWidth(2);
        }

        String hint = profile.catches() < TUTORIAL_CATCHES ? "§7reel on green §8· §6gold is perfect" : null;
        if (returning) {
            return new CastHooks.BiteCue("§5It's back!", "§7The " + species.colored() + " §7took it again",
                    Sound.BLOCK_BELL_RESONATE, 1.2f);
        }
        return switch (rarity) {
            case LEGENDARY -> {
                if (player.getWorld() != null) {
                    player.getWorld().playSound(hook, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
                }
                yield new CastHooks.BiteCue("§6§l! ! !", "§eThe water goes still… §8· §ffast line", Sound.BLOCK_BEACON_POWER_SELECT, 0.6f);
            }
            case RARE -> new CastHooks.BiteCue("§9Something heavy!", "§7It's pulling hard §8· §fnarrow window",
                    Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f);
            case RUN -> new CastHooks.BiteCue("§fSilver!", hint != null ? hint : "§7Herring on the line",
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 1.8f);
            default -> new CastHooks.BiteCue("§bBite!", hint != null ? hint : species.colored() + " §8· §7on the line",
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 1.6f);
        };
    }

    String landed(Player player, CastSession session, Location hook, boolean perfect, int streak) {
        if (!(session.tag() instanceof Bite bite)) {
            return null;
        }
        UUID id = player.getUniqueId();
        lastBeat.put(id, System.currentTimeMillis());
        chances.remove(id);
        int chain = perfect ? chains.merge(id, 1, Integer::sum) : 0;
        if (!perfect) {
            chains.remove(id);
        } else {
            int step = PENTATONIC[Math.min(PENTATONIC.length - 1, chain - 1)];
            float pitch = (float) Math.pow(2.0d, (step - 12) / 12.0d);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.5f, pitch);
        }
        Species species = bite.species();
        double kg = weigh(player, species, bite.bait(), perfect ? Math.min(3, chain) : 0);
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        profile.catches++;
        profile.bestStreak = Math.max(profile.bestStreak, streak);
        AnglerProfiles.Entry entry = profile.log.computeIfAbsent(species, ignored -> new AnglerProfiles.Entry());
        boolean first = entry.count == 0;
        int tierBefore = first ? -1 : Trophies.tier(species, entry.bestKg);
        boolean pb = !first && kg > entry.bestKg;
        entry.count++;
        if (kg > entry.bestKg) {
            entry.bestKg = kg;
        }
        isle.profiles().markDirty();

        boolean trophy = Trophies.isTrophy(species, kg);
        if (trophy) {
            profile.trophies++;
            InventoryDrops.give(player, isle.trophies().item(species, kg));
        }
        if (first) {
            newSpecies(player, species, kg, profile);
        } else if (trophy) {
            trophyBeat(player, species, kg);
        }
        if (pb && Trophies.tier(species, kg) > tierBefore && Trophies.tier(species, kg) >= 1) {
            player.sendMessage("§a▲ New best " + species.colored() + " §f" + LakeText.kg(kg) + " §8— "
                    + weightWord(Trophies.tier(species, kg)) + " §8(+Log points)");
        }
        // Only brag about records worth hearing: every rare / legendary, heavy uncommons, very heavy commons.
        boolean notable = switch (species.rarity()) {
            case RARE, LEGENDARY -> true;
            case UNCOMMON -> species.share(kg) >= 0.6d;
            default -> species.share(kg) >= 0.75d;
        };
        if (isle.profiles().offerRecord(species, player, kg) && notable) {
            String line = "§6✦ Isle record! §f" + player.getName() + " §7landed a §f" + LakeText.kg(kg) + " "
                    + species.colored() + "§7 — the heaviest on Eldervale.";
            for (Player visitor : LakeWorld.visitors()) {
                visitor.sendMessage(line);
            }
        }
        if (species.rarity() == Species.Rarity.LEGENDARY) {
            legendary(player, species, kg);
        }
        if (bite.shoal()) {
            isle.shoals().onCatch(player);
        }
        String event = isle.events().onCatch(player, hook, perfect, streak, bite.water());
        String fountain = "wishing_fountain".equals(bite.water()) ? fountainCoin(player) : null;
        heatBeat(player, streak);
        isle.log().settleRank(player);

        StringBuilder out = new StringBuilder(species.colored()).append(" §f").append(LakeText.kg(kg));
        if (first) {
            out.append(" §e★ new");
        } else if (pb) {
            out.append(" §a▲ PB");
        }
        if (trophy) {
            out.append(" §6✦");
        }
        if (chain >= 2) {
            out.append(" §6✧").append(chain);
        }
        if (event != null) {
            out.append(" §8· ").append(event);
        }
        if (fountain != null) {
            out.append(" §8· ").append(fountain);
        }
        return out.toString();
    }

    boolean missed(Player player, CastSession session, Location hook, boolean timeout, int streak) {
        UUID id = player.getUniqueId();
        lastBeat.put(id, System.currentTimeMillis());
        chains.remove(id);
        if (session.tag() instanceof Bite bite) {
            Species species = bite.species();
            if (species.rarity() == Species.Rarity.RARE || species.rarity() == Species.Rarity.LEGENDARY) {
                if (bite.water() != null) {
                    chances.put(id, new Chance(species, bite.water(), System.currentTimeMillis() + SECOND_CHANCE_MS));
                }
                String what = species.rarity() == Species.Rarity.LEGENDARY ? "§6something enormous" : "§9something heavy";
                player.sendMessage("§7You lost " + what + "§7. " + (bite.water() == null ? "§8Gone."
                        : "§5It's still circling — §fcast here again within 40s§5."));
                player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, SoundCategory.PLAYERS, 1.0f, 0.5f);
            }
        }
        if (streak <= 0) {
            return false;
        }
        if (isle.events().forgive(player)) {
            LakeText.bar(player, "§fThe run forgives one. §7Streak kept.");
            return true;
        }
        if (streak >= BOILING && FishingSkills.has(player, FishingSkills.STEADY_LINE)) {
            double keep = 0.25d + 0.25d * FishingSkills.power(player, FishingSkills.STEADY_LINE);
            if (ThreadLocalRandom.current().nextDouble() < keep) {
                player.sendMessage("§6Steady Line. §7The streak holds.");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rolls

    private Species roll(Player player, Waters.Water water, Location hook, boolean shoal, Bait bait, int heat) {
        World world = hook.getWorld();
        long time = world == null ? 6000L : world.getTime();
        boolean night = time >= 13_000L && time <= 23_000L;
        boolean rain = world != null && world.hasStorm();
        boolean lakeLevel = LakeWorld.lakeLevel(hook.getY());
        if (water == null && !lakeLevel) {
            return Species.BROOK_TROUT;
        }
        double[] uMul = {1.0d, 1.1d, 1.2d, 1.25d, 1.3d};
        double[] rMul = {1.0d, 1.0d, 1.25d, 1.5d, 2.0d};
        double[] lMul = {1.0d, 1.0d, 1.0d, 1.5d, 2.5d};
        double u = uMul[heat];
        double r = rMul[heat];
        double l = lMul[heat];
        double catchStat = isle.stats().catchBonus(player);
        r *= 1.0d + Math.min(0.6d, catchStat / 300.0d);
        l *= 1.0d + Math.min(0.5d, catchStat / 400.0d);
        if (shoal) {
            u *= 1.3d;
            r *= 1.6d;
            l *= 2.0d;
        }
        double tales = FishingSkills.power(player, FishingSkills.TALL_TALES);
        r *= 1.0d + 0.5d * tales;
        l *= 1.0d + 0.5d * tales;
        if (bait == Bait.SILVER_SPINNER) {
            r *= 1.5d;
        } else if (bait == Bait.EMPERORS_FEAST) {
            r *= 1.3d;
            l *= 3.0d;
        } else if (bait == Bait.GLOW_GRUBS && night) {
            r *= 1.3d;
        }
        boolean emperorGate = heat >= 3 || shoal || bait == Bait.EMPERORS_FEAST;

        Species[] all = Species.values();
        double[] weights = new double[all.length];
        double total = 0.0d;
        for (int i = 0; i < all.length; i++) {
            Species species = all[i];
            if (species == Species.SILVERRUN_HERRING) {
                continue;
            }
            double home = species.home(water);
            if (home <= 0.0d) {
                continue;
            }
            double w = species.rarity().weight() * home * species.when(night, rain);
            if (w <= 0.0d) {
                continue;
            }
            switch (species.rarity()) {
                case UNCOMMON -> w *= u;
                case RARE -> w *= r;
                case LEGENDARY -> {
                    if (species == Species.GOLDSCALE_EMPEROR && !emperorGate) {
                        w = 0.0d;
                    }
                    w *= l;
                }
                default -> {
                }
            }
            if (bait == Bait.GLOW_GRUBS && species.when(true, false) >= 2.0d) {
                w *= 2.0d;
            }
            if (bait == Bait.HEAVY_SINKER && (species == Species.DEEPWATER_GAR
                    || species == Species.MIRRORBACK_STURGEON || species == Species.OLD_WHISKERS)) {
                w *= 1.6d;
            }
            weights[i] = w;
            total += w;
        }
        if (isle.events().runActive() && lakeLevel && (water == null || water.kind() == Waters.Kind.LAKE)) {
            double run = total * 0.8d;
            weights[Species.SILVERRUN_HERRING.ordinal()] = run;
            total += run;
        }
        if (total <= 0.0d) {
            return water != null && water.kind() == Waters.Kind.TARN ? Species.BROOK_TROUT : Species.SILVER_PERCH;
        }
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < all.length; i++) {
            pick -= weights[i];
            if (pick < 0.0d && weights[i] > 0.0d) {
                return all[i];
            }
        }
        return Species.SILVER_PERCH;
    }

    /** Skewed light; every extra roll keeps the heaviest. */
    private double weigh(Player player, Species species, Bait bait, int chainRolls) {
        int rolls = 1 + chainRolls;
        if (bait == Bait.HEAVY_SINKER) {
            rolls++;
        }
        if (FishingSkills.power(player, FishingSkills.TALL_TALES) >= 0.5d) {
            rolls++;
        }
        double best = 0.0d;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < rolls; i++) {
            best = Math.max(best, rng.nextDouble());
        }
        double kg = species.minKg() + (species.maxKg() - species.minKg()) * Math.pow(best, 2.2d);
        return Math.round(kg * 100.0d) / 100.0d;
    }

    private void spendBait(Player player, AnglerProfiles.Profile profile, Bait bait) {
        int left = Math.max(0, profile.charges(bait) - 1);
        profile.bait.put(bait, left);
        isle.profiles().markDirty();
        if (left == 0) {
            profile.activeBait = null;
            player.sendMessage("§7That was your last §f" + bait.display() + "§7. §8Tilly at the Bait Shack makes more.");
        }
    }

    // ------------------------------------------------------------------ beats

    private void newSpecies(Player player, Species species, double kg, AnglerProfiles.Profile profile) {
        int xp = species.rarity().firstXp();
        FishingSkills.bonus(player, xp);
        int known = profile.speciesCount();
        player.showTitle(Title.title(
                LakeText.legacy("§bNew species!"),
                LakeText.legacy(species.colored() + " §8· §f" + LakeText.kg(kg) + " §8· §7Log " + known + "/" + Species.values().length),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(500))
        ));
        player.sendMessage("§b✦ Angler's Log §8· " + species.colored() + " §8(" + species.rarity().label() + ") §7— \""
                + species.line() + "\" §a+" + xp + " Fishing XP");
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4f, 1.6f);
        if (Trophies.isTrophy(species, kg)) {
            trophyBeat(player, species, kg);
        }
    }

    private void trophyBeat(Player player, Species species, double kg) {
        player.sendMessage("§6✦ Trophy §8· " + species.colored() + " §f" + LakeText.kg(kg) + " §8· §7worth ~§6"
                + LakeText.coins(Trophies.value(species, kg)) + " coins §7at the §dTrophy House§7.");
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.6f, 1.3f);
        Location at = player.getLocation().add(0, 1.2, 0);
        player.getWorld().spawnParticle(Particle.WAX_ON, at, 10, 0.4, 0.4, 0.4, 0.0);
    }

    private void legendary(Player player, Species species, double kg) {
        String line = "§6§l✦ LEGENDARY §8· §f" + player.getName() + " §7landed " + species.colored() + " §f" + LakeText.kg(kg) + "§7!";
        for (Player visitor : LakeWorld.visitors()) {
            visitor.sendMessage(line);
            visitor.playSound(visitor.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 0.7f);
        }
        player.showTitle(Title.title(
                LakeText.legacy("§6§l" + species.display().toUpperCase(java.util.Locale.ROOT)),
                LakeText.legacy("§f" + LakeText.kg(kg) + " §8· §7the lake will talk about this"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(3500), Duration.ofMillis(900))
        ));
        World world = player.getWorld();
        world.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 60, 0.6, 0.8, 0.6, 0.12);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.0, 0), 30, 0.5, 1.2, 0.5, 0.04);
    }

    /** Heat steps get a card; a Whirlpool is news for the whole isle. */
    private void heatBeat(Player player, int streak) {
        if (streak == BOILING) {
            player.showTitle(Title.title(LakeText.legacy("§cBoiling"),
                    LakeText.legacy("§7Ten clean · rare fish are paying attention"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(400))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.8f, 1.5f);
        } else if (streak == WHIRLPOOL) {
            player.showTitle(Title.title(LakeText.legacy("§d§lWhirlpool"),
                    LakeText.legacy("§7Twenty clean · the lake's royalty is listening"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            String line = "§d≋ §f" + player.getName() + " §7is in a §dWhirlpool §7streak §8(20 clean)";
            for (Player visitor : LakeWorld.visitors()) {
                if (!visitor.equals(player)) {
                    visitor.sendMessage(line);
                }
            }
        }
    }

    /** The Wishing Fountain gives back a coin now and then. */
    private String fountainCoin(Player player) {
        long now = System.currentTimeMillis();
        Long last = fountainCoins.get(player.getUniqueId());
        if (last != null && now - last < 20_000L) {
            return null;
        }
        fountainCoins.put(player.getUniqueId(), now);
        long coins = 3L + ThreadLocalRandom.current().nextInt(10);
        FishingSkills.coins(player, coins);
        return "§eA wished-on coin §6+" + coins;
    }

    static String weightWord(int tier) {
        return switch (tier) {
            case 3 -> "§6record class";
            case 2 -> "§egold weight";
            case 1 -> "§fsilver weight";
            default -> "§7bronze weight";
        };
    }
}
