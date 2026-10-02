package de.aetherion.mining.isle;

import de.aetherion.core.api.QuestBars;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
 * Isle-wide events on a timer while miners are on Mining Eldervale. All four telegraph first,
 * share one boss bar, and end with a personal tally.
 * <ul>
 *   <li><b>Rich Vein</b> — one quarry or shaft runs rich: ores there pay an extra drop half the
 *   time and bonus XP. (The Farm Isle's Bee Bloom, underground.)</li>
 *   <li><b>Ember Hour</b> — the forges roar and the rock runs hot: Crystal Finds ×4 isle-wide.</li>
 *   <li><b>Tremor</b> — the mountain shakes loose. Glowing rubble pops out of the walls near every
 *   miner; strike it for ore. The whole isle works towards one shared goal, and if the crew makes
 *   it, everyone who helped gets paid and Crystal Finds double for a minute.</li>
 *   <li><b>Troll Run</b> — something in the deep is restless: Ore Trolls crawl out of the veins
 *   far more often (blueprint drops as usual) and every troll felled pays a bounty.</li>
 * </ul>
 */
public final class MineEvents implements Listener {

    static final String LEASE = "mine-event";
    private static final int TELEGRAPH_TICKS = 20 * 8;
    private static final double RICH_EXTRA_CHANCE = 0.5d;
    private static final int RICH_XP = 3;
    private static final double EMBER_MULT = 4.0d;
    private static final double SHORED_MULT = 2.0d;
    private static final long SHORED_MS = 60_000L;
    private static final double TROLL_CHANCE = 0.08d;
    private static final int TROLL_BOUNTY_XP = 25;
    private static final long TROLL_BOUNTY_COINS = 120L;

    public enum Kind {
        RICH_VEIN("Rich Vein", BossBar.Color.YELLOW, 90, "§e"),
        EMBER_HOUR("Ember Hour", BossBar.Color.RED, 120, "§c"),
        TREMOR("Tremor", BossBar.Color.WHITE, 75, "§f"),
        TROLL_RUN("Troll Run", BossBar.Color.GREEN, 90, "§a");

        final String display;
        final BossBar.Color color;
        final int seconds;
        final String chat;

        Kind(String display, BossBar.Color color, int seconds, String chat) {
            this.display = display;
            this.color = color;
            this.seconds = seconds;
            this.chat = chat;
        }

        public String display() {
            return display;
        }

        public String colored() {
            return chat + display;
        }
    }

    private enum Phase {
        IDLE,
        TELEGRAPH,
        ACTIVE
    }

    private record Rubble(UUID display, UUID hitbox, IsleOre ore, Location at, UUID owner, int bornTick) {
    }

    private final MineIsle isle;
    private final NamespacedKey rubbleKey;
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> tally = new HashMap<>();
    private final Map<UUID, Rubble> rubbleByPart = new HashMap<>();
    private final Map<UUID, Integer> rubbleHits = new HashMap<>();
    private final Map<UUID, List<UUID>> trollsLive = new HashMap<>();
    private BukkitTask timer;
    private BukkitTask ticker;
    private Phase phase = Phase.IDLE;
    private Kind kind;
    private MineDistricts.District district;
    private int phaseTicks;
    private int activeTicks;
    private long nextAtMs;
    private BossBar bar;
    private int goal;
    private int progress;
    private boolean goalMet;
    private long shoredUntil;

    MineEvents(MineIsle isle) {
        this.isle = isle;
        this.rubbleKey = new NamespacedKey(isle.plugin(), "mine_rubble");
    }

    void start() {
        purgeStrays();
        if (!isle.plugin().getConfig().getBoolean("mine-events.enabled", true)) {
            return;
        }
        long interval = intervalTicks();
        long first = Math.max(20L * 60L, isle.plugin().getConfig().getLong("mine-events.first-delay-ticks", 20L * 60L * 6L));
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
        return Math.max(20L * 120L, isle.plugin().getConfig().getLong("mine-events.interval-ticks", 20L * 60L * 20L));
    }

    // ------------------------------------------------------------------ queries

    public boolean active() {
        return phase == Phase.ACTIVE;
    }

    public Kind kind() {
        return phase == Phase.IDLE ? null : kind;
    }

    public MineDistricts.District richDistrict() {
        return phase == Phase.ACTIVE && kind == Kind.RICH_VEIN ? district : null;
    }

    public double crystalMultiplier() {
        double mult = 1.0d;
        if (phase == Phase.ACTIVE && kind == Kind.EMBER_HOUR) {
            mult *= EMBER_MULT;
        }
        if (shoredUntil > System.currentTimeMillis()) {
            mult *= SHORED_MULT;
        }
        return mult;
    }

    public long secondsUntilNext() {
        if (phase != Phase.IDLE) {
            return 0L;
        }
        return Math.max(0L, (nextAtMs - System.currentTimeMillis()) / 1000L);
    }

    public String statusLine() {
        if (phase == Phase.IDLE) {
            String shored = shoredUntil > System.currentTimeMillis()
                    ? " §8· §dShored up: Crystal Finds ×2 (" + MineText.clock((shoredUntil - System.currentTimeMillis()) / 1000L) + ")"
                    : "";
            return "§7Next mine event in §f" + MineText.clock(secondsUntilNext()) + shored;
        }
        String where = kind == Kind.RICH_VEIN && district != null ? " §8· " + district.colored() : "";
        String left = phase == Phase.TELEGRAPH ? "§7starting" : "§f" + MineText.clock((kind.seconds * 20L - activeTicks) / 20L) + " §7left";
        return kind.colored() + where + " §8· " + left;
    }

    /** Extra whole drops for an ore break at {@code at} (Rich Vein). */
    int richExtra(Player player, Location at) {
        MineDistricts.District rich = richDistrict();
        if (rich == null || at == null || !rich.contains(at)) {
            return 0;
        }
        return ThreadLocalRandom.current().nextDouble() < RICH_EXTRA_CHANCE ? 1 : 0;
    }

    /** Break hook: Rich Vein XP + tally, Troll Run spawns, general tally. */
    void onMined(Player player, IsleOre ore, Location at) {
        if (phase != Phase.ACTIVE || ore == null) {
            return;
        }
        switch (kind) {
            case RICH_VEIN -> {
                if (district == null || !district.contains(at) || ore == IsleOre.STONE) {
                    return;
                }
                MineSkills.bonus(player, RICH_XP);
                int count = tally.merge(player.getUniqueId(), 1, Integer::sum);
                if (count % 10 == 0) {
                    player.spawnParticle(Particle.DUST, at.clone().add(0.5, 0.6, 0.5), 8, 0.4, 0.3, 0.4, 0.0,
                            new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.4f));
                    player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.6f);
                }
            }
            case TROLL_RUN -> {
                if (ore == IsleOre.STONE) {
                    return;
                }
                List<UUID> mine = trollsLive.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
                mine.removeIf(id -> {
                    org.bukkit.entity.Entity troll = Bukkit.getEntity(id);
                    return troll == null || !troll.isValid() || troll.isDead();
                });
                if (mine.size() >= 2 || ThreadLocalRandom.current().nextDouble() >= TROLL_CHANCE) {
                    return;
                }
                org.bukkit.entity.Entity troll = MineSkills.spawnTrollEntity(player, at.clone().add(0.5, 0.0, 0.5));
                if (troll != null) {
                    mine.add(troll.getUniqueId());
                    MineText.bar(player, "§a⚠ An Ore Troll crawls out of the seam!");
                }
            }
            case TREMOR -> {
                // Tremor pays for rubble cleared (strikeRubble), not for every block mined meanwhile.
            }
            default -> tally.merge(player.getUniqueId(), 1, Integer::sum);
        }
    }

    /** Seam Bursts during a Rich Vein pay a little extra. */
    void onSeamBurst(Player player) {
        if (phase == Phase.ACTIVE && kind == Kind.RICH_VEIN && district != null && district.contains(player.getLocation())) {
            MineSkills.bonus(player, 10);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    private void maybeStart() {
        nextAtMs = System.currentTimeMillis() + intervalTicks() * 50L;
        if (phase != Phase.IDLE) {
            return;
        }
        List<Player> miners = MineWorld.miners(isle.plugin());
        if (miners.isEmpty()) {
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble();
        MineDistricts.District rich = districtWithMiners(miners);
        if (roll < 0.35d && rich != null) {
            begin(Kind.RICH_VEIN, rich);
        } else if (roll < 0.60d) {
            begin(Kind.EMBER_HOUR, null);
        } else if (roll < 0.85d) {
            begin(Kind.TREMOR, null);
        } else {
            begin(Kind.TROLL_RUN, null);
        }
    }

    private MineDistricts.District districtWithMiners(List<Player> miners) {
        List<MineDistricts.District> candidates = new ArrayList<>();
        for (MineDistricts.District candidate : isle.districts().mineable()) {
            for (Player player : miners) {
                if (candidate.contains(player.getLocation())) {
                    candidates.add(candidate);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            List<MineDistricts.District> all = isle.districts().mineable();
            return all.isEmpty() ? null : all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** DEV / timer entry. {@code where} picks the Rich Vein district; null = one with miners. */
    public String begin(Kind next, MineDistricts.District where) {
        if (phase != Phase.IDLE) {
            stop(true);
        }
        if (next == Kind.RICH_VEIN) {
            district = where != null ? where : districtWithMiners(MineWorld.miners(isle.plugin()));
            if (district == null) {
                return "§cNo quarries / shafts configured under mine-isle-districts.";
            }
        } else {
            district = null;
        }
        kind = next;
        phase = Phase.TELEGRAPH;
        phaseTicks = 0;
        activeTicks = 0;
        tally.clear();
        trollsLive.clear();
        progress = 0;
        goalMet = false;
        String tease = switch (next) {
            case RICH_VEIN -> "§eA seam is waking up under the " + district.colored() + "§e…";
            case EMBER_HOUR -> "§cThe forges below are roaring. The rock is running hot…";
            case TREMOR -> "§fThe mountain groans. Hold onto something…";
            case TROLL_RUN -> "§aSomething is scratching behind the walls…";
        };
        Sound sound = switch (next) {
            case RICH_VEIN -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
            case EMBER_HOUR -> Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE;
            case TREMOR -> Sound.ENTITY_WARDEN_HEARTBEAT;
            case TROLL_RUN -> Sound.ENTITY_SILVERFISH_AMBIENT;
        };
        for (Player visitor : MineWorld.visitors(isle.plugin())) {
            visitor.sendMessage(tease);
            visitor.playSound(visitor.getLocation(), sound, SoundCategory.AMBIENT, 0.9f, 0.7f);
        }
        return "§a" + next.display + " §7starting" + (district != null ? " in " + district.colored() : "") + "§7.";
    }

    public String stop(boolean quiet) {
        if (phase == Phase.IDLE) {
            return "§7No mine event running.";
        }
        Kind was = kind;
        boolean ran = phase == Phase.ACTIVE;
        phase = Phase.IDLE;
        clearRubble();
        if (bar != null) {
            for (UUID id : Set.copyOf(viewers)) {
                hide(id);
            }
        }
        viewers.clear();
        bar = null;
        if (!quiet && ran) {
            if (was == Kind.TREMOR) {
                finishTremor();
            }
            for (Player visitor : MineWorld.visitors(isle.plugin())) {
                Integer count = tally.get(visitor.getUniqueId());
                String line = switch (was) {
                    case RICH_VEIN -> "§eThe seam settles." + (count == null ? "" : " §7You worked §f" + count + " §7rich ore.");
                    case EMBER_HOUR -> "§cThe forges bank down." + (count == null ? "" : " §7You struck §f" + count + " §7blocks in the heat.");
                    case TREMOR -> "§fThe mountain goes quiet." + (count == null ? "" : " §7You cleared §f" + count + " §7rubble.");
                    case TROLL_RUN -> "§aThe scratching stops." + (count == null ? "" : " §7You felled §f" + count + " §7troll" + (count == 1 ? "" : "s") + ".");
                };
                visitor.sendMessage(line);
            }
        }
        tally.clear();
        trollsLive.clear();
        district = null;
        return "§eMine event stopped.";
    }

    private int totalTicks() {
        return kind.seconds * 20;
    }

    private void tick() {
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.TELEGRAPH) {
            phaseTicks += 5;
            if (kind == Kind.TREMOR && phaseTicks % 20 == 0) {
                for (Player visitor : MineWorld.visitors(isle.plugin())) {
                    visitor.playSound(visitor.getLocation(), Sound.BLOCK_BASALT_BREAK, SoundCategory.AMBIENT, 0.7f, 0.5f);
                    visitor.spawnParticle(Particle.FALLING_DUST, visitor.getLocation().add(0, 3, 0), 6, 3.0, 0.5, 3.0, 0.0,
                            Material.GRAVEL.createBlockData());
                }
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
        List<Player> visitors = MineWorld.visitors(isle.plugin());
        syncViewers(visitors);
        if (bar != null) {
            bar.progress((float) Math.max(0.0d, 1.0d - activeTicks / (double) totalTicks()));
            bar.name(MineText.legacy(barTitle()));
        }
        switch (kind) {
            case RICH_VEIN -> {
                if (activeTicks % 20 == 0 && district != null) {
                    for (Player visitor : visitors) {
                        if (district.contains(visitor.getLocation())) {
                            visitor.spawnParticle(Particle.DUST, visitor.getLocation().add(0, 1.5, 0), 4, 4.0, 1.5, 4.0, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.2f));
                        }
                    }
                }
            }
            case EMBER_HOUR -> {
                if (activeTicks % 20 == 0) {
                    for (Player visitor : visitors) {
                        visitor.spawnParticle(Particle.LAVA, visitor.getLocation().add(0, 2.5, 0), 1, 5.0, 1.0, 5.0, 0.0);
                        if (activeTicks % 100 == 0) {
                            visitor.playSound(visitor.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.AMBIENT, 0.12f, 0.6f);
                        }
                    }
                }
            }
            case TREMOR -> {
                if (activeTicks % 160 == 0) {
                    for (Player miner : MineWorld.miners(isle.plugin())) {
                        spawnRubble(miner);
                    }
                }
                if (activeTicks % 60 == 0) {
                    for (Player visitor : visitors) {
                        visitor.playSound(visitor.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.AMBIENT, 0.4f, 0.5f);
                    }
                }
                expireRubble();
            }
            case TROLL_RUN -> {
                if (activeTicks % 100 == 0) {
                    for (Player visitor : visitors) {
                        visitor.playSound(visitor.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.AMBIENT, 0.25f, 0.5f);
                    }
                }
            }
        }
    }

    private void activate() {
        phase = Phase.ACTIVE;
        activeTicks = 0;
        if (kind == Kind.TREMOR) {
            goal = Math.max(15, 10 * MineWorld.miners(isle.plugin()).size());
        }
        bar = BossBar.bossBar(MineText.legacy(barTitle()), 1.0f, kind.color, BossBar.Overlay.PROGRESS);
        String sub = switch (kind) {
            case RICH_VEIN -> "§6" + district.name() + " §7· +1 ore (50%) and +" + RICH_XP + " XP per ore";
            case EMBER_HOUR -> "§7Crystal Finds ×" + (int) EMBER_MULT + " for two minutes";
            case TREMOR -> "§7Strike the glowing rubble · crew goal §f" + goal;
            case TROLL_RUN -> "§7Ore Trolls everywhere · §6" + TROLL_BOUNTY_COINS + " coins §7a head";
        };
        for (Player visitor : MineWorld.visitors(isle.plugin())) {
            visitor.showTitle(Title.title(
                    MineText.legacy(kind.chat + kind.display),
                    MineText.legacy(sub),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(500))
            ));
            Sound sound = switch (kind) {
                case RICH_VEIN -> Sound.BLOCK_AMETHYST_CLUSTER_BREAK;
                case EMBER_HOUR -> Sound.ITEM_FIRECHARGE_USE;
                case TREMOR -> Sound.ENTITY_WARDEN_EMERGE;
                case TROLL_RUN -> Sound.ENTITY_RAVAGER_ROAR;
            };
            visitor.playSound(visitor.getLocation(), sound, SoundCategory.AMBIENT, kind == Kind.TREMOR ? 0.4f : 0.7f, 0.8f);
        }
        if (kind == Kind.TREMOR) {
            for (Player miner : MineWorld.miners(isle.plugin())) {
                spawnRubble(miner);
            }
        }
        syncViewers(MineWorld.visitors(isle.plugin()));
    }

    private String barTitle() {
        long left = Math.max(0L, (totalTicks() - activeTicks) / 20L);
        return switch (kind) {
            case RICH_VEIN -> "§eRich Vein §8· §6" + (district == null ? "?" : district.name())
                    + " §8· §f+1 ore · +" + RICH_XP + " XP §8· §7" + MineText.clock(left);
            case EMBER_HOUR -> "§cEmber Hour §8· §fCrystal Finds ×" + (int) EMBER_MULT + " §8· §7" + MineText.clock(left);
            case TREMOR -> "§fTremor §8· §7Crew §f" + progress + "§7/§f" + goal
                    + (goalMet ? " §a✔ shored up" : "") + " §8· §7" + MineText.clock(left);
            case TROLL_RUN -> "§aTroll Run §8· §fbounty " + TROLL_BOUNTY_COINS + " coins §8· §7" + MineText.clock(left);
        };
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

    // ------------------------------------------------------------------ tremor rubble

    private void spawnRubble(Player miner) {
        int mine = 0;
        for (Rubble rubble : new HashSet<>(rubbleByPart.values())) {
            if (rubble.owner().equals(miner.getUniqueId())) {
                mine++;
            }
        }
        if (mine >= 2) {
            return;
        }
        Location spot = findSpot(miner.getLocation());
        if (spot == null) {
            return;
        }
        IsleOre ore = rubbleOre(isle.districts().band(spot));
        World world = spot.getWorld();
        BlockDisplay display = world.spawn(spot, BlockDisplay.class, spawned -> {
            spawned.setBlock(ore.showcase().createBlockData());
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(ore.glow());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.35f, 0.0f, -0.35f),
                    new AxisAngle4f(0.35f, 0.3f, 1.0f, 0.2f),
                    new Vector3f(0.7f, 0.7f, 0.7f),
                    new AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
            ));
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(rubbleKey, PersistentDataType.BYTE, (byte) 1);
        });
        Interaction hitbox = world.spawn(spot, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.0f);
            spawned.setInteractionHeight(1.0f);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(rubbleKey, PersistentDataType.BYTE, (byte) 1);
        });
        Rubble rubble = new Rubble(display.getUniqueId(), hitbox.getUniqueId(), ore, spot, miner.getUniqueId(),
                Bukkit.getCurrentTick());
        rubbleByPart.put(display.getUniqueId(), rubble);
        rubbleByPart.put(hitbox.getUniqueId(), rubble);
        world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.4, 0), 20, 0.3, 0.3, 0.3, 0.1, Material.DEEPSLATE.createBlockData());
        world.playSound(spot, Sound.BLOCK_POINTED_DRIPSTONE_FALL, SoundCategory.BLOCKS, 1.0f, 0.7f);
        MineText.bar(miner, "§f⛏ Loose seam! §7" + ore.colored() + " §7rubble shook out nearby — strike it.");
    }

    /** A free, floored air block 2–6 blocks from {@code near} at roughly the same height. */
    private Location findSpot(Location near) {
        World world = near.getWorld();
        if (world == null) {
            return null;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 14; attempt++) {
            int dx = rng.nextInt(-6, 7);
            int dz = rng.nextInt(-6, 7);
            if (Math.abs(dx) + Math.abs(dz) < 2) {
                continue;
            }
            for (int dy = -1; dy <= 1; dy++) {
                Block air = world.getBlockAt(near.getBlockX() + dx, near.getBlockY() + dy, near.getBlockZ() + dz);
                if (air.getType().isAir() && air.getRelative(0, 1, 0).getType().isAir()
                        && air.getRelative(0, -1, 0).getType().isSolid()
                        && MineWorld.onIsle(isle.plugin(), air.getLocation())) {
                    return air.getLocation().add(0.5, 0.0, 0.5);
                }
            }
        }
        return null;
    }

    private static IsleOre rubbleOre(MineDistricts.Band band) {
        double roll = ThreadLocalRandom.current().nextDouble();
        return switch (band) {
            case UNDERCROFT -> roll < 0.25 ? IsleOre.DIAMOND : roll < 0.40 ? IsleOre.EMERALD : roll < 0.65 ? IsleOre.GOLD : IsleOre.LAPIS;
            case DEEP_WORKS -> roll < 0.10 ? IsleOre.DIAMOND : roll < 0.40 ? IsleOre.GOLD : roll < 0.70 ? IsleOre.REDSTONE : IsleOre.LAPIS;
            case GALLERIES -> roll < 0.35 ? IsleOre.IRON : roll < 0.65 ? IsleOre.COPPER : roll < 0.85 ? IsleOre.REDSTONE : IsleOre.GOLD;
            case SURFACE -> roll < 0.45 ? IsleOre.COAL : roll < 0.80 ? IsleOre.COPPER : IsleOre.IRON;
        };
    }

    private void expireRubble() {
        int now = Bukkit.getCurrentTick();
        for (Rubble rubble : new HashSet<>(rubbleByPart.values())) {
            if (now - rubble.bornTick() > 20 * 30) {
                removeRubble(rubble, true);
            }
        }
    }

    private void removeRubble(Rubble rubble, boolean crumble) {
        rubbleByPart.remove(rubble.display());
        rubbleByPart.remove(rubble.hitbox());
        rubbleHits.remove(rubble.hitbox());
        for (UUID id : List.of(rubble.display(), rubble.hitbox())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        if (crumble && rubble.at().getWorld() != null) {
            rubble.at().getWorld().spawnParticle(Particle.BLOCK, rubble.at().clone().add(0, 0.3, 0), 12, 0.25, 0.2, 0.25, 0.05,
                    Material.GRAVEL.createBlockData());
        }
    }

    private void clearRubble() {
        for (Rubble rubble : new HashSet<>(rubbleByPart.values())) {
            removeRubble(rubble, false);
        }
        rubbleByPart.clear();
        rubbleHits.clear();
    }

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(rubbleKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRubblePunch(EntityDamageByEntityEvent event) {
        Rubble rubble = rubbleByPart.get(event.getEntity().getUniqueId());
        if (rubble == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            strikeRubble(player, rubble);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRubbleClick(PlayerInteractEntityEvent event) {
        Rubble rubble = rubbleByPart.get(event.getRightClicked().getUniqueId());
        if (rubble == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            strikeRubble(event.getPlayer(), rubble);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRubbleClickAt(PlayerInteractAtEntityEvent event) {
        if (rubbleByPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void strikeRubble(Player player, Rubble rubble) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_PICKAXE")) {
            MineText.bar(player, "§cRubble wants a pickaxe.");
            return;
        }
        int hits = rubbleHits.merge(rubble.hitbox(), 1, Integer::sum);
        World world = rubble.at().getWorld();
        if (world != null) {
            world.playSound(rubble.at(), Sound.BLOCK_DEEPSLATE_HIT, SoundCategory.PLAYERS, 1.0f, 0.8f + hits * 0.2f);
            world.spawnParticle(Particle.BLOCK, rubble.at().clone().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.05,
                    rubble.ore().showcase().createBlockData());
        }
        if (hits < 2) {
            return;
        }
        removeRubble(rubble, true);
        int amount = rubble.ore().rare() ? 1 + ThreadLocalRandom.current().nextInt(2) : 2 + ThreadLocalRandom.current().nextInt(5);
        MineSkills.give(player, new ItemStack(rubble.ore().resource(), amount));
        MineSkills.bonus(player, 8);
        tally.merge(player.getUniqueId(), 1, Integer::sum);
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.rubbleCleared++;
        isle.profiles().markDirty();
        progress++;
        if (world != null) {
            world.playSound(rubble.at(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 1.1f);
        }
        MineText.bar(player, "§f+" + amount + " " + rubble.ore().colored() + " §8· §7crew §f" + progress + "§7/§f" + goal);
        if (!goalMet && progress >= goal) {
            goalMet = true;
            shoredUntil = System.currentTimeMillis() + SHORED_MS;
            for (Player visitor : MineWorld.visitors(isle.plugin())) {
                visitor.showTitle(Title.title(
                        MineText.legacy("§aShored Up!"),
                        MineText.legacy("§7The crew held the mountain · §dCrystal Finds ×2 §7for a minute"),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))
                ));
                visitor.playSound(visitor.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
        }
    }

    /** Tremor payout: everyone who cleared rubble gets paid; more if the crew hit the goal. */
    private void finishTremor() {
        for (Map.Entry<UUID, Integer> entry : tally.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            long coins = entry.getValue() * 60L + (goalMet ? 500L : 0L);
            int xp = entry.getValue() * 6 + (goalMet ? 120 : 0);
            MineSkills.coins(player, coins);
            MineSkills.bonus(player, xp);
            player.sendMessage("§fTremor pay §8· §6+" + MineText.coins(coins) + " coins §8· §a+" + xp + " Mining XP"
                    + (goalMet ? " §8(crew bonus)" : ""));
        }
    }

    // ------------------------------------------------------------------ troll run

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTrollDeath(EntityDeathEvent event) {
        if (!MineSkills.isTroll(event.getEntity())) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        List<UUID> live = trollsLive.get(killer.getUniqueId());
        if (live != null) {
            live.remove(event.getEntity().getUniqueId());
        }
        MineProfiles.Profile profile = isle.profiles().of(killer);
        profile.trollsFelled++;
        isle.profiles().markDirty();
        if (phase != Phase.ACTIVE || kind != Kind.TROLL_RUN || !MineWorld.onIsle(isle.plugin(), killer)) {
            return;
        }
        tally.merge(killer.getUniqueId(), 1, Integer::sum);
        MineSkills.coins(killer, TROLL_BOUNTY_COINS);
        MineSkills.bonus(killer, TROLL_BOUNTY_XP);
        MineText.bar(killer, "§aTroll bounty §8· §6+" + TROLL_BOUNTY_COINS + " coins §8· §a+" + TROLL_BOUNTY_XP + " Mining XP");
    }
}
