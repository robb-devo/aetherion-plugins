package de.aetherion.hub.origin;

import com.destroystokyo.paper.ParticleBuilder;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient density: the small life that makes the 1000² feel lived in, placed on surveyed map features
 * ({@code origin-explore.yml → life}):
 * <ul>
 *   <li><b>smoke</b> — cold chimney campfires breathe again (supper smoke at dusk, thin at night);</li>
 *   <li><b>hives</b> — bees orbit every hive cluster by day, honey drips, the hive hums when you're close;</li>
 *   <li><b>fireflies</b> — glades by water and flowers light up after dusk;</li>
 *   <li><b>mist</b> — dawn and night haze on lakes and the rivers;</li>
 *   <li><b>spores</b> — giant mushroom caps shed spores (and glow at night);</li>
 *   <li><b>leaves</b> — the deep woods drop leaves by day;</li>
 *   <li><b>drips</b> — mine and pit caves drip;</li>
 *   <li><b>devils</b> — dust devils wander the Borderlands flats by day;</li>
 *   <li><b>moths</b> — moths circle the street lanterns at night;</li>
 *   <li><b>falls</b> — spray where water falls.</li>
 * </ul>
 * Budgeted per player: sources are bucketed in 32-block cells, only those near you are considered, the nearest get
 * the rich treatment (smooth motion every 4 ticks), the far ring gets a cheap pulse once a second, and a hard
 * particle budget per player per second caps everything. Players with particles off get none of it.
 */
public final class OriginLife {

    public enum Kind { SMOKE, HIVE, FIREFLIES, MIST, SPORES, LEAVES, DRIPS, DEVIL, MOTHS, FALLS }

    public record Source(Kind kind, double x, double y, double z, double r, int seed) {
    }

    private static final Particle.DustOptions BEE_GOLD = new Particle.DustOptions(Color.fromRGB(255, 196, 40), 0.55f);
    private static final Particle.DustOptions BEE_DARK = new Particle.DustOptions(Color.fromRGB(40, 32, 20), 0.45f);
    private static final Particle.DustOptions FLY_ON = new Particle.DustOptions(Color.fromRGB(215, 255, 120), 0.75f);
    private static final Particle.DustOptions FLY_DIM = new Particle.DustOptions(Color.fromRGB(150, 210, 90), 0.5f);
    private static final Particle.DustOptions MOTH = new Particle.DustOptions(Color.fromRGB(236, 226, 200), 0.45f);
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(150, 118, 86), 1.3f);
    private static final BlockData LEAF = Material.OAK_LEAVES.createBlockData();

    private final OriginIsle isle;
    private final Map<Long, List<Source>> grid = new HashMap<>();
    private final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
    private boolean enabled = true;
    private double near = 20.0d;
    private double far = 44.0d;
    private double smokeRange = 72.0d;
    private int budget = 70;
    private long emittedLastSecond;
    private long emittedThisSecond;

    OriginLife(OriginIsle isle) {
        this.isle = isle;
    }

    int load() {
        grid.clear();
        counts.clear();
        ConfigurationSection life = isle.config().explore().getConfigurationSection("life");
        if (life == null) {
            enabled = false;
            return 0;
        }
        enabled = life.getBoolean("enabled", true);
        near = life.getDouble("near", 20.0d);
        far = Math.max(near, life.getDouble("far", 44.0d));
        smokeRange = life.getDouble("smoke-range", 72.0d);
        budget = Math.max(10, life.getInt("budget", 70));
        int total = 0;
        for (Kind kind : Kind.values()) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            if (kind == Kind.DEVIL) {
                key = "devils";
            }
            int n = 0;
            for (String raw : life.getStringList(key)) {
                double[] p = OriginConfig.nums(raw);
                if (p == null || p.length < 3) {
                    continue;
                }
                double r = p.length >= 4 ? p[3] : defaultRadius(kind);
                int seed = (int) (Math.floor(p[0]) * 31 + Math.floor(p[2]) * 17 + Math.floor(p[1]));
                Source source = new Source(kind, p[0], p[1], p[2], r, seed);
                grid.computeIfAbsent(cell(((int) Math.floor(p[0])) >> 5, ((int) Math.floor(p[2])) >> 5), k -> new ArrayList<>()).add(source);
                n++;
            }
            counts.put(kind, n);
            total += n;
        }
        return total;
    }

    private static double defaultRadius(Kind kind) {
        return switch (kind) {
            case FIREFLIES -> 6.0d;
            case MIST -> 7.0d;
            case LEAVES -> 6.0d;
            case DEVIL -> 10.0d;
            case SPORES -> 3.5d;
            default -> 1.5d;
        };
    }

    public Map<Kind, Integer> counts() {
        return counts;
    }

    public String status() {
        StringBuilder out = new StringBuilder("§6Life §8· " + (enabled ? "§aon" : "§coff") + " §8· §7budget " + budget + "/s/player, near "
                + (int) near + ", far " + (int) far + " §8· §7emitted last second §f" + emittedLastSecond + "\n§7");
        for (Map.Entry<Kind, Integer> e : counts.entrySet()) {
            out.append(e.getKey().name().toLowerCase(Locale.ROOT)).append(' ').append(e.getValue()).append("  ");
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ loops

    /** Every 4 ticks: smooth motion for the nearest living things (bees, fireflies, moths, dust devils). */
    void fast(List<Player> onIsle, long tick) {
        if (!enabled || onIsle.isEmpty()) {
            return;
        }
        OriginIsle.Phase phase = isle.phase();
        boolean night = phase == OriginIsle.Phase.NIGHT || phase == OriginIsle.Phase.DUSK;
        int perRun = Math.max(4, (int) (budget * 0.6d / 5.0d));
        for (Player player : onIsle) {
            if (!isle.profiles().get(player).particles || isle.flight().flying(player)) {
                continue;
            }
            Location at = player.getLocation();
            List<Object[]> list = gather(at, far);
            int spent = 0;
            boolean devilShown = false;
            for (Object[] e : list) {
                if (spent >= perRun) {
                    break;
                }
                Source s = (Source) e[0];
                double d = (double) e[1];
                switch (s.kind()) {
                    case HIVE -> {
                        if (!night && d <= near * near * 0.64d) {
                            spent += bees(player, s, tick, 3);
                        }
                    }
                    case FIREFLIES -> {
                        if (night && d <= near * near * 1.4d) {
                            spent += fireflies(player, s, tick, 4);
                        }
                    }
                    case MOTHS -> {
                        if (night && d <= 14.0d * 14.0d) {
                            spent += moths(player, s, tick);
                        }
                    }
                    case DEVIL -> {
                        if (!night && !devilShown && d <= far * far) {
                            spent += devil(player, s, tick);
                            devilShown = true;
                        }
                    }
                    default -> {
                    }
                }
            }
            emittedThisSecond += spent;
        }
    }

    /** Every 20 ticks: slow pulses (smoke, mist, spores, leaves, drips, falls) and the cheap far ring. */
    void slow(List<Player> onIsle, long tick) {
        emittedLastSecond = emittedThisSecond;
        emittedThisSecond = 0L;
        if (!enabled || onIsle.isEmpty()) {
            return;
        }
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        OriginIsle.Phase phase = isle.phase();
        boolean night = phase == OriginIsle.Phase.NIGHT || phase == OriginIsle.Phase.DUSK;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int perRun = Math.max(6, (int) (budget * 0.4d));
        for (Player player : onIsle) {
            OriginProfile profile = isle.profiles().get(player);
            if (isle.flight().flying(player)) {
                continue;
            }
            Location at = player.getLocation();
            List<Object[]> list = gather(at, Math.max(far, smokeRange));
            int spent = 0;
            for (Object[] e : list) {
                if (spent >= perRun) {
                    break;
                }
                Source s = (Source) e[0];
                double d = (double) e[1];
                Location p = new Location(world, s.x(), s.y(), s.z());
                if (profile.ambience) {
                    sounds(player, s, d, night, rng);
                }
                if (!profile.particles) {
                    continue;
                }
                boolean inFar = d <= far * far;
                switch (s.kind()) {
                    case SMOKE -> {
                        if (d <= smokeRange * smokeRange) {
                            int n = phase == OriginIsle.Phase.DUSK || phase == OriginIsle.Phase.DAWN ? 3 : (night ? 1 : 2);
                            new ParticleBuilder(Particle.CAMPFIRE_COSY_SMOKE).location(p.clone().add(rng.nextDouble() * 0.4 - 0.2, 0.2, rng.nextDouble() * 0.4 - 0.2))
                                    .count(0).offset(0.0, 0.07, 0.0).extra(1.0).receivers(player).force(true).spawn();
                            if (n > 1) {
                                new ParticleBuilder(Particle.CAMPFIRE_COSY_SMOKE).location(p.clone().add(rng.nextDouble() * 0.5 - 0.25, 0.4, rng.nextDouble() * 0.5 - 0.25))
                                        .count(0).offset(0.004, 0.06, 0.0).extra(1.0).receivers(player).force(true).spawn();
                            }
                            spent += Math.min(2, n);
                        }
                    }
                    case HIVE -> {
                        if (!night && inFar && d > near * near * 0.64d) {
                            spent += bees(player, s, tick, 1);
                        }
                        if (!night && d <= 16.0d * 16.0d && rng.nextDouble() < 0.15d) {
                            player.spawnParticle(Particle.FALLING_HONEY, p.clone().add(rng.nextDouble() - 0.5, -0.4, rng.nextDouble() - 0.5), 1, 0, 0, 0, 0);
                            spent++;
                        }
                    }
                    case FIREFLIES -> {
                        if (night && inFar && d > near * near * 1.4d) {
                            new ParticleBuilder(Particle.GLOW).location(p).count(2).offset(s.r() * 0.6, 0.8, s.r() * 0.6).extra(0.0)
                                    .receivers(player).force(true).spawn();
                            spent += 2;
                        }
                    }
                    case MIST -> {
                        if ((phase == OriginIsle.Phase.DAWN || night) && inFar) {
                            for (int i = 0; i < 3; i++) {
                                double a = rng.nextDouble() * Math.PI * 2.0d;
                                double r = rng.nextDouble() * s.r();
                                player.spawnParticle(Particle.CLOUD, p.clone().add(Math.cos(a) * r, 0.25 + rng.nextDouble() * 0.4, Math.sin(a) * r),
                                        0, 0.03, 0.002, 0.01, 1.0);
                            }
                            spent += 3;
                        }
                    }
                    case SPORES -> {
                        if (inFar) {
                            player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, p, 4, s.r(), 1.0, s.r(), 0.0);
                            spent += 4;
                            if (night) {
                                new ParticleBuilder(Particle.GLOW).location(p).count(2).offset(s.r(), 0.6, s.r()).extra(0.0).receivers(player).spawn();
                                spent += 2;
                            }
                        }
                    }
                    case LEAVES -> {
                        if (!night && inFar) {
                            player.spawnParticle(Particle.FALLING_DUST, p, 2, s.r(), 0.5, s.r(), 0.0, LEAF);
                            spent += 2;
                        }
                    }
                    case DRIPS -> {
                        if (d <= 24.0d * 24.0d) {
                            player.spawnParticle(Particle.DRIPPING_WATER, p, 2, s.r(), 0.1, s.r(), 0.0);
                            player.spawnParticle(Particle.WHITE_ASH, p.clone().add(0, -1.5, 0), 3, 2.0, 1.0, 2.0, 0.0);
                            spent += 5;
                        }
                    }
                    case FALLS -> {
                        if (d <= 40.0d * 40.0d) {
                            player.spawnParticle(Particle.SPLASH, p, 8, 0.8, 0.2, 0.8, 0.1);
                            player.spawnParticle(Particle.CLOUD, p.clone().add(0, 0.4, 0), 0, 0.0, 0.03, 0.0, 1.0);
                            spent += 9;
                        }
                    }
                    default -> {
                    }
                }
            }
            emittedThisSecond += spent;
        }
    }

    private void sounds(Player player, Source s, double d, boolean night, ThreadLocalRandom rng) {
        Location p = new Location(player.getWorld(), s.x(), s.y(), s.z());
        switch (s.kind()) {
            case HIVE -> {
                if (!night && d <= 10.0d * 10.0d && rng.nextDouble() < 0.18d) {
                    player.playSound(p, Sound.BLOCK_BEEHIVE_WORK, SoundCategory.AMBIENT, 0.35f, 0.9f + rng.nextFloat() * 0.3f);
                }
            }
            case DRIPS -> {
                if (d <= 16.0d * 16.0d && rng.nextDouble() < 0.3d) {
                    player.playSound(p, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.AMBIENT, 0.5f, 0.8f + rng.nextFloat() * 0.4f);
                }
            }
            case FALLS -> {
                if (d <= 24.0d * 24.0d && rng.nextDouble() < 0.5d) {
                    player.playSound(p, Sound.BLOCK_WATER_AMBIENT, SoundCategory.AMBIENT, 0.6f, 1.2f);
                }
            }
            case SMOKE -> {
                if (d <= 8.0d * 8.0d && rng.nextDouble() < 0.2d) {
                    player.playSound(p, Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.AMBIENT, 0.35f, 0.9f);
                }
            }
            case DEVIL -> {
                if (!night && d <= 18.0d * 18.0d && rng.nextDouble() < 0.25d) {
                    player.playSound(p, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.08f, 1.6f);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ shapes

    private static int bees(Player player, Source s, long tick, int n) {
        for (int i = 0; i < n; i++) {
            double a = tick * 0.21d + i * 2.09d + s.seed();
            double r = 1.1d + 0.55d * Math.sin(tick * 0.05d + i + s.seed());
            double y = 0.4d + 0.35d * Math.sin(tick * 0.17d + i * 1.3d);
            Location at = new Location(player.getWorld(), s.x() + Math.cos(a) * r, s.y() + y, s.z() + Math.sin(a * 1.07d) * r);
            player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, (i + tick / 4) % 3 == 0 ? BEE_DARK : BEE_GOLD);
        }
        return n;
    }

    private static int fireflies(Player player, Source s, long tick, int n) {
        int spent = 0;
        for (int i = 0; i < n; i++) {
            int k = (int) ((tick / 4 + i * 5L) % 12L);
            double phase = (s.seed() * 0.37d + i * 1.618d) % (Math.PI * 2.0d);
            double drift = tick * 0.012d;
            double x = s.x() + Math.cos(phase + drift) * s.r() * (0.3d + 0.7d * ((i * 37 + s.seed()) % 10) / 10.0d);
            double z = s.z() + Math.sin(phase * 1.3d + drift) * s.r() * (0.3d + 0.7d * ((i * 53 + s.seed()) % 10) / 10.0d);
            double y = s.y() + 0.8d + 1.4d * (0.5d + 0.5d * Math.sin(drift * 2.0d + i));
            if (k < 5) {
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, k < 3 ? FLY_ON : FLY_DIM);
                spent++;
            }
        }
        return spent;
    }

    private static int moths(Player player, Source s, long tick) {
        double a = tick * 0.35d + s.seed();
        player.spawnParticle(Particle.DUST, s.x() + Math.cos(a) * 0.55d, s.y() + 0.2d * Math.sin(a * 1.7d), s.z() + Math.sin(a) * 0.55d,
                1, 0, 0, 0, 0, MOTH);
        return 1;
    }

    private static int devil(Player player, Source s, long tick) {
        double cx = s.x() + Math.cos(tick / 220.0d + s.seed()) * s.r();
        double cz = s.z() + Math.sin(tick / 260.0d + s.seed()) * s.r();
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = tick * 0.45d + i * 0.75d;
            double r = 0.35d + i * 0.14d;
            new ParticleBuilder(Particle.DUST).location(new Location(player.getWorld(), cx + Math.cos(a) * r, s.y() + 0.2d + i * 0.45d, cz + Math.sin(a) * r))
                    .count(1).offset(0.05, 0.05, 0.05).data(DUST).receivers(player).force(true).spawn();
        }
        return n;
    }

    // ------------------------------------------------------------------ spatial

    private List<Object[]> gather(Location at, double reach) {
        List<Object[]> out = new ArrayList<>();
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int r = ((int) reach >> 5) + 1;
        double r2 = reach * reach;
        for (int cx = (bx >> 5) - r; cx <= (bx >> 5) + r; cx++) {
            for (int cz = (bz >> 5) - r; cz <= (bz >> 5) + r; cz++) {
                List<Source> list = grid.get(cell(cx, cz));
                if (list == null) {
                    continue;
                }
                for (Source s : list) {
                    double dx = at.getX() - s.x();
                    double dy = at.getY() - s.y();
                    double dz = at.getZ() - s.z();
                    double d = dx * dx + dz * dz + dy * dy * 0.5d;
                    if (d <= r2) {
                        out.add(new Object[]{s, d});
                    }
                }
            }
        }
        out.sort((a, b) -> Double.compare((double) a[1], (double) b[1]));
        return out;
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
