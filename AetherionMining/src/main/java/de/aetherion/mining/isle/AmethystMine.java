package de.aetherion.mining.isle;

import de.aetherion.core.api.QuestBars;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Amethyst Mine: {@code /amethyst}, the daily-reset dig world known as The Veins. This is
 * where the mountain keeps its best stones, so it's the prestige layer under Eldervale.
 * <ul>
 *   <li><b>Geodes</b> grow in the rock (more of them and bigger the deeper you go): calcite shell,
 *       amethyst walls, budding crystal, clusters in the hollow.</li>
 *   <li><b>Resonance</b> builds as you break crystal and fades when you stop. Humming (30),
 *       Singing Walls (60) and Shatterpoint (90) each pay more ore Fortune and Crystal Find luck.
 *       At 100 the geode shatters: shards fly (it hurts), Shardlings pour out, and a third of the
 *       time a <b>Geode Heart</b> is left in the rubble. That's a guaranteed Crystal Find with
 *       Heartstone odds. Mining plain stone or ore in between lets the ringing settle.</li>
 *   <li><b>The Violet Deep</b> (below Y 0) hums at anyone under Mining 20 without a Canary. It's a
 *       soft gate you can learn your way past, not a wall.</li>
 * </ul>
 * Heartstones, the Veins-only grade, crown the Specimen Cabinet and pay the top Assay Plate.
 */
public final class AmethystMine implements Listener {

    private static final String LEASE = "amethyst-resonance";
    private static final double MAX = 100.0d;
    private static final Set<Material> CRYSTAL = EnumSet.of(Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            Material.AMETHYST_CLUSTER, Material.LARGE_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD);

    public enum Stage {
        QUIET(0, "Quiet", 0.0d, 1.0d, BossBar.Color.WHITE, "§7"),
        HUMMING(30, "Humming", 10.0d, 1.25d, BossBar.Color.PURPLE, "§d"),
        SINGING(60, "Singing Walls", 20.0d, 1.6d, BossBar.Color.PINK, "§5"),
        SHATTERPOINT(90, "Shatterpoint", 30.0d, 2.0d, BossBar.Color.RED, "§c");

        final int from;
        final String label;
        final double fortune;
        final double crystal;
        final BossBar.Color color;
        final String chat;

        Stage(int from, String label, double fortune, double crystal, BossBar.Color color, String chat) {
            this.from = from;
            this.label = label;
            this.fortune = fortune;
            this.crystal = crystal;
            this.color = color;
            this.chat = chat;
        }

        public String colored() {
            return chat + label;
        }

        static Stage of(double value) {
            Stage best = QUIET;
            for (Stage stage : values()) {
                if (value >= stage.from) {
                    best = stage;
                }
            }
            return best;
        }
    }

    private static final class State {
        double value;
        int lastCrystalTick;
        Stage stage = Stage.QUIET;
        BossBar bar;
        int nextDeepHum;
    }

    private final MineIsle isle;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> deepHum = new ConcurrentHashMap<>();
    private BukkitTask task;

    AmethystMine(MineIsle isle) {
        this.isle = isle;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 20L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : Map.copyOf(states).keySet()) {
            forget(id);
        }
    }

    void forget(UUID id) {
        deepHum.remove(id);
        dropBar(id);
    }

    private void dropBar(UUID id) {
        State state = states.remove(id);
        if (state != null && state.bar != null) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(state.bar);
            }
        }
        QuestBars.release(id, LEASE);
    }

    static boolean counts(Material material) {
        return material != null && CRYSTAL.contains(material);
    }

    /** Amethyst family for crystal blocks the ore enum doesn't list (blocks, buds). */
    static IsleOre oreOf(Material material) {
        return counts(material) ? IsleOre.AMETHYST : null;
    }

    public double resonance(Player player) {
        State state = states.get(player.getUniqueId());
        return state == null ? 0.0d : state.value;
    }

    public Stage stage(Player player) {
        State state = states.get(player.getUniqueId());
        return state == null ? Stage.QUIET : state.stage;
    }

    /** Ore Fortune in the Amethyst Mine from Resonance (every family). */
    double oreFortune(Player player, IsleOre ore) {
        return stage(player).fortune;
    }

    /** Crystal Find luck from Resonance (read by CrystalFinds for Veins strikes). */
    double crystalMultiplier(Player player) {
        return stage(player).crystal;
    }

    // ------------------------------------------------------------------ strikes

    void onMined(Player player, IsleOre ore, Location at) {
        if (!isle.plugin().getConfig().getBoolean("amethyst-mine.enabled", true)) {
            return;
        }
        Material type = at.getBlock().getType();
        boolean crystal = ore == IsleOre.AMETHYST || counts(type);
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        int now = Bukkit.getCurrentTick();
        if (crystal) {
            double gain = type == Material.BUDDING_AMETHYST ? 9.0d : type == Material.AMETHYST_CLUSTER ? 7.0d : 5.0d;
            state.value = Math.min(MAX, state.value + gain);
            state.lastCrystalTick = now;
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.5f,
                        (float) (0.6d + state.value / 100.0d));
            }
        } else {
            state.value = Math.max(0.0d, state.value - (ore == IsleOre.STONE ? 3.0d : 1.5d));
        }
        Stage before = state.stage;
        state.stage = Stage.of(state.value);
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (state.value > profile.resonanceBest) {
            profile.resonanceBest = state.value;
            isle.profiles().markDirty();
        }
        if (state.stage.ordinal() > before.ordinal()) {
            stageUp(player, state.stage);
        }
        if (crystal && state.stage.ordinal() >= Stage.SINGING.ordinal()
                && ThreadLocalRandom.current().nextDouble() < (state.stage == Stage.SHATTERPOINT ? 0.3d : 0.18d)) {
            isle.critters().spawnNear(MineCritters.Kind.SHARDLING, player, at);
        }
        if (state.value >= MAX) {
            shatter(player, state, at);
        }
        paint(player, state);
    }

    private void stageUp(Player player, Stage stage) {
        String perk = "+" + (int) stage.fortune + " ore Fortune · Crystal Finds ×" + MineText.num(stage.crystal);
        if (stage == Stage.SINGING) {
            perk += " · §5Shardlings wake";
        }
        if (stage == Stage.SHATTERPOINT) {
            perk = "§cone more push and it shatters §8· §7" + perk;
        }
        MineText.bar(player, stage.chat + "◆ " + stage.label + " §8· §7" + perk);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.5f + 0.25f * stage.ordinal());
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 12 * stage.ordinal(), 0.8, 0.8, 0.8, 0.02);
    }

    private void shatter(Player player, State state, Location at) {
        state.value = 40.0d;
        state.stage = Stage.of(state.value);
        World world = at.getWorld();
        Location center = at.clone().add(0.5, 0.5, 0.5);
        world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 1.6f, 0.5f);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.4f, 0.6f);
        world.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.BLOCKS, 0.4f, 1.8f);
        world.spawnParticle(Particle.BLOCK, center, 120, 1.5, 1.2, 1.5, 0.2, Material.AMETHYST_BLOCK.createBlockData());
        world.spawnParticle(Particle.END_ROD, center, 40, 1.2, 1.2, 1.2, 0.15);
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(center) <= 4.5d * 4.5d) {
                near.damage(Math.max(0.5d, near.getMaxHealth() * 0.12d * isle.forge().hazardFactor(near)));
                near.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
            }
        }
        for (int i = 0; i < 2; i++) {
            isle.critters().spawnNear(MineCritters.Kind.SHARDLING, player, at);
        }
        MineSkills.bonus(player, 60);
        if (ThreadLocalRandom.current().nextDouble() < isle.plugin().getConfig().getDouble("amethyst-mine.geode-heart-chance", 0.35d)) {
            MineProfiles.Profile profile = isle.profiles().of(player);
            profile.geodeHearts++;
            isle.profiles().markDirty();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double heart = isle.plugin().getConfig().getDouble("amethyst-mine.heart-heartstone-chance", 0.15d);
            Grade grade = rng.nextDouble() < heart ? Grade.HEARTSTONE : rng.nextDouble() < 0.5d ? Grade.PERFECT : Grade.FLAWLESS;
            double carats = Math.round((IsleOre.AMETHYST.minCarat()
                    + (IsleOre.AMETHYST.maxCarat() - IsleOre.AMETHYST.minCarat()) * rng.nextDouble(0.5d, 1.0d)) * 1.6d * 100.0d) / 100.0d;
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    isle.crystals().spawn(player, IsleOre.AMETHYST, grade, carats, at, true);
                }
            }, 15L);
            player.sendMessage("§5◆ Shatter! §7The walls gave way and left a §dGeode Heart §7in the rubble. §8Crack it before someone else does.");
        } else {
            player.sendMessage("§5◆ Shatter! §7The geode sings itself to pieces. §8Let the ringing settle next time. Or don't.");
        }
    }

    // ------------------------------------------------------------------ ticking

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, State> entry : Map.copyOf(states).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null || !player.isOnline() || !inMine(player)) {
                forget(entry.getKey());
                continue;
            }
            if (now - state.lastCrystalTick > 60 && state.value > 0.0d) {
                state.value = Math.max(0.0d, state.value - 2.0d);
                state.stage = Stage.of(state.value);
                paint(player, state);
            }
            if (state.value <= 0.0d && now - state.lastCrystalTick > 200) {
                dropBar(entry.getKey());
            }
        }
        if (now % 40 >= 10) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!inMine(player) || player.getLocation().getY() >= 0.0d || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                continue;
            }
            if (MineSkills.level(player) >= isle.plugin().getConfig().getInt("amethyst-mine.violet-deep-level", 20)
                    || isle.forge().canary(player)) {
                continue;
            }
            Integer next = deepHum.get(player.getUniqueId());
            if (next != null && now < next) {
                continue;
            }
            deepHum.put(player.getUniqueId(), now + 20 * 8);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 4, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 4, 0, false, false, false));
            MineText.bar(player, "§5The Violet Deep hums at you. §7Mining 20 or a §6Canary Cage §7quiets it.");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 1.0f, 0.5f);
        }
    }

    private boolean inMine(Player player) {
        return isle.plugin().getVeins() != null && isle.plugin().getVeins().isVeins(player.getWorld());
    }

    private void paint(Player player, State state) {
        if (state.value <= 0.0d) {
            if (state.bar != null) {
                player.hideBossBar(state.bar);
                state.bar = null;
                QuestBars.release(player.getUniqueId(), LEASE);
            }
            return;
        }
        float progress = (float) Math.max(0.0d, Math.min(1.0d, state.value / MAX));
        String title = state.stage.chat + "◆ Resonance §f" + (int) state.value + " §8· " + state.stage.colored()
                + (state.stage == Stage.QUIET ? "" : " §8· §7+" + (int) state.stage.fortune + " Fortune · Crystal ×"
                + MineText.num(state.stage.crystal));
        if (state.bar == null) {
            state.bar = BossBar.bossBar(MineText.legacy(title), progress, state.stage.color, BossBar.Overlay.NOTCHED_10);
            QuestBars.suppress(player, LEASE);
            player.showBossBar(state.bar);
            return;
        }
        state.bar.name(MineText.legacy(title));
        state.bar.progress(progress);
        state.bar.color(state.stage.color);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (!inMine(event.getPlayer())) {
            forget(event.getPlayer().getUniqueId());
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!player.isOnline() || !inMine(player)) {
                return;
            }
            player.sendMessage("§d◆ The Amethyst Mine. §7Geodes grow in the rock (bigger the deeper). Break crystal to build"
                    + " §dResonance§7: more Fortune, more Crystal Finds. Let it hit 100 and the geode shatters.");
            player.sendMessage("§8Heartstones grow only here. Below Y 0 is the Violet Deep.");
        }, 40L);
    }
}
