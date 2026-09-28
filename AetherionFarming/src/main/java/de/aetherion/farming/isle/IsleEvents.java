package de.aetherion.farming.isle;

import de.aetherion.core.api.QuestBars;
import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Isle-wide events on a timer while farmers are on Eldervale.
 * <ul>
 *   <li><b>Bee Bloom</b> — the hives swarm one field. Harvests there pay extra crops and XP.</li>
 *   <li><b>Harvest Moon</b> — the whole isle glows; Prize Crops turn up four times as often.</li>
 * </ul>
 * Both telegraph first, run on one shared boss bar, and end with a personal tally.
 */
public final class IsleEvents {

    static final String LEASE = "isle-event";
    private static final int TELEGRAPH_TICKS = 20 * 8;
    private static final int BLOOM_TICKS = 20 * 90;
    private static final int MOON_TICKS = 20 * 120;
    private static final double BLOOM_EXTRA_CHANCE = 0.5d;
    private static final int BLOOM_XP = 2;
    private static final double MOON_PRIZE_MULT = 4.0d;

    public enum Kind {
        BEE_BLOOM("Bee Bloom", BossBar.Color.YELLOW),
        HARVEST_MOON("Harvest Moon", BossBar.Color.BLUE);

        final String display;
        final BossBar.Color color;

        Kind(String display, BossBar.Color color) {
            this.display = display;
            this.color = color;
        }

        public String display() {
            return display;
        }
    }

    private enum Phase {
        IDLE,
        TELEGRAPH,
        ACTIVE
    }

    private final FarmIsle isle;
    private final NamespacedKey beeKey;
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> tally = new HashMap<>();
    private final List<UUID> bees = new ArrayList<>();
    private BukkitTask timer;
    private BukkitTask ticker;
    private Phase phase = Phase.IDLE;
    private Kind kind;
    private IslePlots.Plot plot;
    private int phaseTicks;
    private int activeTicks;
    private long nextAtMs;
    private BossBar bar;

    IsleEvents(FarmIsle isle) {
        this.isle = isle;
        this.beeKey = new NamespacedKey(isle.plugin(), "isle_bloom_bee");
    }

    void start() {
        if (!isle.plugin().getConfig().getBoolean("isle-events.enabled", true)) {
            return;
        }
        long interval = intervalTicks();
        long first = Math.max(20L * 60L, isle.plugin().getConfig().getLong("isle-events.first-delay-ticks", 20L * 60L * 5L));
        nextAtMs = System.currentTimeMillis() + first * 50L;
        timer = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::maybeStart, first, interval);
        ticker = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 5L, 5L);
    }

    void shutdown() {
        stop(true);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private long intervalTicks() {
        return Math.max(20L * 120L, isle.plugin().getConfig().getLong("isle-events.interval-ticks", 20L * 60L * 20L));
    }

    // ------------------------------------------------------------------ queries

    public boolean active() {
        return phase == Phase.ACTIVE;
    }

    public Kind kind() {
        return phase == Phase.IDLE ? null : kind;
    }

    public IslePlots.Plot bloomPlot() {
        return phase == Phase.ACTIVE && kind == Kind.BEE_BLOOM ? plot : null;
    }

    public double prizeMultiplier() {
        return phase == Phase.ACTIVE && kind == Kind.HARVEST_MOON ? MOON_PRIZE_MULT : 1.0d;
    }

    /** Seconds until the next scheduled event roll (0 while one runs). */
    public long secondsUntilNext() {
        if (phase != Phase.IDLE) {
            return 0L;
        }
        return Math.max(0L, (nextAtMs - System.currentTimeMillis()) / 1000L);
    }

    public String statusLine() {
        if (phase == Phase.IDLE) {
            return "§7Next isle event in §f" + IsleText.clock(secondsUntilNext());
        }
        String where = kind == Kind.BEE_BLOOM && plot != null ? " §8· " + plot.colored() : "";
        String left = phase == Phase.TELEGRAPH ? "§7starting" : "§f" + IsleText.clock((totalTicks() - activeTicks) / 20L) + " §7left";
        return "§e" + kind.display + where + " §8· " + left;
    }

    /** Extra whole crops for a harvest at {@code at} (Bee Bloom). */
    int bloomExtra(Player player, Location at) {
        IslePlots.Plot bloom = bloomPlot();
        if (bloom == null || at == null || !bloom.contains(at)) {
            return 0;
        }
        return ThreadLocalRandom.current().nextDouble() < BLOOM_EXTRA_CHANCE ? 1 : 0;
    }

    /** Harvest hook: Bee Bloom XP + tally, Harvest Moon tally. */
    void onHarvest(Player player, Location at) {
        if (phase != Phase.ACTIVE) {
            return;
        }
        if (kind == Kind.BEE_BLOOM) {
            if (plot == null || !plot.contains(at)) {
                return;
            }
            FarmingSkills.bonus(player, BLOOM_XP);
            int count = tally.merge(player.getUniqueId(), 1, Integer::sum);
            if (count % 12 == 0) {
                player.spawnParticle(Particle.FALLING_NECTAR, at.clone().add(0.5, 1.2, 0.5), 6, 0.4, 0.2, 0.4, 0.0);
                player.playSound(at, Sound.BLOCK_BEEHIVE_WORK, SoundCategory.PLAYERS, 0.5f, 1.3f);
            }
            return;
        }
        tally.merge(player.getUniqueId(), 1, Integer::sum);
    }

    // ------------------------------------------------------------------ lifecycle

    private void maybeStart() {
        nextAtMs = System.currentTimeMillis() + intervalTicks() * 50L;
        if (phase != Phase.IDLE) {
            return;
        }
        List<Player> farmers = IsleWorld.farmers(isle.plugin());
        if (farmers.isEmpty()) {
            return;
        }
        IslePlots.Plot field = fieldWithFarmers(farmers);
        if (field != null && ThreadLocalRandom.current().nextDouble() < 0.6d) {
            begin(Kind.BEE_BLOOM, field);
        } else {
            begin(Kind.HARVEST_MOON, null);
        }
    }

    private IslePlots.Plot fieldWithFarmers(List<Player> farmers) {
        List<IslePlots.Plot> candidates = new ArrayList<>();
        for (IslePlots.Plot field : isle.plots().fields()) {
            for (Player player : farmers) {
                if (field.contains(player.getLocation())) {
                    candidates.add(field);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            List<IslePlots.Plot> fields = isle.plots().fields();
            return fields.isEmpty() ? null : fields.get(ThreadLocalRandom.current().nextInt(fields.size()));
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** DEV / timer entry. {@code where} picks the bloom field; null = random field. */
    public String begin(Kind next, IslePlots.Plot where) {
        if (phase != Phase.IDLE) {
            stop(true);
        }
        if (next == Kind.BEE_BLOOM) {
            plot = where != null ? where : fieldWithFarmers(IsleWorld.farmers(isle.plugin()));
            if (plot == null) {
                return "§cNo fields configured under farm-isle-plots.";
            }
        } else {
            plot = null;
        }
        kind = next;
        phase = Phase.TELEGRAPH;
        phaseTicks = 0;
        activeTicks = 0;
        tally.clear();
        String tease = next == Kind.BEE_BLOOM
                ? "§eThe hives are stirring over the " + plot.colored() + "§e…"
                : "§bThe sky over Eldervale is turning silver…";
        for (Player visitor : IsleWorld.visitors(isle.plugin())) {
            visitor.sendMessage(tease);
            visitor.playSound(visitor.getLocation(), next == Kind.BEE_BLOOM ? Sound.BLOCK_BEEHIVE_WORK : Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                    SoundCategory.AMBIENT, 0.8f, 0.8f);
        }
        return "§a" + next.display + " §7starting" + (plot != null ? " over " + plot.colored() : "") + "§7.";
    }

    public String stop(boolean quiet) {
        if (phase == Phase.IDLE) {
            return "§7No isle event running.";
        }
        Kind was = kind;
        boolean ran = phase == Phase.ACTIVE;
        phase = Phase.IDLE;
        removeBees();
        if (bar != null) {
            for (UUID id : Set.copyOf(viewers)) {
                hide(id);
            }
        }
        viewers.clear();
        bar = null;
        if (!quiet && ran) {
            for (Player visitor : IsleWorld.visitors(isle.plugin())) {
                Integer count = tally.get(visitor.getUniqueId());
                String line = was == Kind.BEE_BLOOM
                        ? "§eThe bloom fades." + (count == null ? "" : " §7You worked §f" + count + " §7pollinated crops.")
                        : "§bThe Harvest Moon sets." + (count == null ? "" : " §7You harvested §f" + count + " §7crops under it.");
                visitor.sendMessage(line);
            }
        }
        tally.clear();
        plot = null;
        return "§eIsle event stopped.";
    }

    private int totalTicks() {
        return kind == Kind.BEE_BLOOM ? BLOOM_TICKS : MOON_TICKS;
    }

    private void tick() {
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.TELEGRAPH) {
            phaseTicks += 5;
            if (kind == Kind.BEE_BLOOM && plot != null) {
                swirl(plot, 2);
            }
            if (phaseTicks >= TELEGRAPH_TICKS) {
                activate();
            }
            return;
        }
        activeTicks += 5;
        if (activeTicks >= totalTicks()) {
            stop(false);
            return;
        }
        List<Player> visitors = IsleWorld.visitors(isle.plugin());
        syncViewers(visitors);
        if (bar != null) {
            bar.progress((float) Math.max(0.0d, 1.0d - activeTicks / (double) totalTicks()));
            bar.name(IsleText.legacy(barTitle()));
        }
        if (kind == Kind.BEE_BLOOM && plot != null) {
            swirl(plot, 5);
            if (activeTicks % 40 == 0) {
                topUpBees();
            }
        } else if (kind == Kind.HARVEST_MOON) {
            for (Player visitor : visitors) {
                visitor.spawnParticle(Particle.END_ROD, visitor.getLocation().add(0, 6, 0), 3, 8.0, 2.0, 8.0, 0.0);
                if (activeTicks % 100 == 0) {
                    visitor.playSound(visitor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.35f, 0.6f);
                }
            }
        }
    }

    private void activate() {
        phase = Phase.ACTIVE;
        activeTicks = 0;
        bar = BossBar.bossBar(IsleText.legacy(barTitle()), 1.0f, kind.color, BossBar.Overlay.PROGRESS);
        String sub = kind == Kind.BEE_BLOOM
                ? "§6" + plot.name() + " §7· +1 crop (50%) and +" + BLOOM_XP + " XP per harvest"
                : "§7Prize Crops ×" + (int) MOON_PRIZE_MULT + " for two minutes";
        for (Player visitor : IsleWorld.visitors(isle.plugin())) {
            visitor.showTitle(Title.title(
                    IsleText.legacy((kind == Kind.BEE_BLOOM ? "§e" : "§b") + kind.display),
                    IsleText.legacy(sub),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(500))
            ));
            visitor.playSound(visitor.getLocation(), kind == Kind.BEE_BLOOM ? Sound.ENTITY_BEE_LOOP_AGGRESSIVE : Sound.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.AMBIENT, 0.7f, kind == Kind.BEE_BLOOM ? 1.4f : 0.7f);
        }
        if (kind == Kind.BEE_BLOOM) {
            topUpBees();
        }
        syncViewers(IsleWorld.visitors(isle.plugin()));
    }

    private String barTitle() {
        long left = Math.max(0L, (totalTicks() - activeTicks) / 20L);
        return kind == Kind.BEE_BLOOM
                ? "§eBee Bloom §8· §6" + (plot == null ? "?" : plot.name()) + " §8· §f+1 crop · +" + BLOOM_XP + " XP §8· §7" + IsleText.clock(left)
                : "§bHarvest Moon §8· §fPrize Crops ×" + (int) MOON_PRIZE_MULT + " §8· §7" + IsleText.clock(left);
    }

    private void syncViewers(List<Player> visitors) {
        if (bar == null) {
            return;
        }
        Set<UUID> now = new HashSet<>();
        for (Player visitor : visitors) {
            now.add(visitor.getUniqueId());
            if (viewers.add(visitor.getUniqueId())) {
                QuestBars.suppress(visitor, LEASE);
                visitor.showBossBar(bar);
            }
        }
        for (UUID id : Set.copyOf(viewers)) {
            if (!now.contains(id)) {
                hide(id);
            }
        }
    }

    private void hide(UUID id) {
        viewers.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null && bar != null) {
            player.hideBossBar(bar);
        }
        QuestBars.release(id, LEASE);
    }

    void onQuit(UUID id) {
        if (viewers.contains(id)) {
            hide(id);
        }
    }

    // ------------------------------------------------------------------ bloom FX

    private void swirl(IslePlots.Plot field, int puffs) {
        for (Player visitor : IsleWorld.visitors(isle.plugin())) {
            Location at = visitor.getLocation();
            if (!field.contains(at)) {
                continue;
            }
            visitor.spawnParticle(Particle.FALLING_NECTAR, at.clone().add(0, 3, 0), puffs, 5.0, 1.5, 5.0, 0.0);
            visitor.spawnParticle(Particle.WAX_ON, at.clone().add(0, 1.5, 0), Math.max(1, puffs / 2), 4.0, 1.0, 4.0, 0.0);
        }
    }

    private void topUpBees() {
        World world = IsleWorld.world(isle.plugin());
        if (world == null || plot == null) {
            return;
        }
        bees.removeIf(id -> {
            Entity bee = Bukkit.getEntity(id);
            return bee == null || !bee.isValid();
        });
        int want = Math.max(0, isle.plugin().getConfig().getInt("isle-events.bloom-bees", 8));
        for (Player visitor : IsleWorld.visitors(isle.plugin())) {
            if (bees.size() >= want) {
                break;
            }
            if (!plot.contains(visitor.getLocation())) {
                continue;
            }
            for (int i = 0; i < 3 && bees.size() < want; i++) {
                Location at = visitor.getLocation().clone().add(
                        ThreadLocalRandom.current().nextDouble(-6, 6), 2.0, ThreadLocalRandom.current().nextDouble(-6, 6));
                Bee bee = world.spawn(at, Bee.class, spawned -> {
                    spawned.setPersistent(false);
                    spawned.setRemoveWhenFarAway(true);
                    spawned.setInvulnerable(true);
                    spawned.setSilent(false);
                    spawned.setAnger(0);
                    spawned.setHasNectar(true);
                    spawned.setCannotEnterHiveTicks(20 * 300);
                    spawned.getPersistentDataContainer().set(beeKey, PersistentDataType.BYTE, (byte) 1);
                });
                bees.add(bee.getUniqueId());
            }
        }
    }

    private void removeBees() {
        for (UUID id : List.copyOf(bees)) {
            Entity bee = Bukkit.getEntity(id);
            if (bee != null) {
                if (bee.getWorld() != null) {
                    bee.getWorld().spawnParticle(Particle.WAX_OFF, bee.getLocation(), 3, 0.2, 0.2, 0.2, 0.0);
                }
                bee.remove();
            }
        }
        bees.clear();
    }
}
