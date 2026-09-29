package de.aetherion.mining.isle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The rock pushes back, but only down deep, and only fairly.
 * <ul>
 *   <li><b>Strain → cave-in.</b> Hammer one small patch below the Galleries line and the ceiling
 *       starts to complain: dust trickles, then a creak. Stay put and it lands on you. Step out
 *       of the dust and the ceiling pays you instead, with ore that fell loose. A Canary warns
 *       you early.</li>
 *   <li><b>Heat</b> in the Emberseam. It builds while you stay and cools when you leave or stand
 *       in water. Heat Ward slows it down.</li>
 *   <li><b>The dark</b> in the Undercroft. Without a light in either hand (or Cave Sense, or a
 *       Canary) the dark closes in every few seconds.</li>
 * </ul>
 */
public final class MineHazards implements Listener {

    private static final int STRAIN_WINDOW_TICKS = 20 * 25;
    private static final int STRAIN_WARN = 22;
    private static final int STRAIN_FALL = 30;
    private static final int COLLAPSE_TELEGRAPH_TICKS = 40;
    private static final double COLLAPSE_RADIUS = 2.6d;
    private static final Set<Material> LIGHTS = EnumSet.of(Material.TORCH, Material.SOUL_TORCH, Material.LANTERN,
            Material.SOUL_LANTERN, Material.GLOWSTONE, Material.SHROOMLIGHT, Material.SEA_LANTERN, Material.GLOW_BERRIES,
            Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.JACK_O_LANTERN,
            Material.REDSTONE_TORCH, Material.GLOW_INK_SAC, Material.LIGHT);

    private static final class State {
        final Deque<long[]> strikes = new ArrayDeque<>();
        boolean warned;
        int collapseAt;
        Location collapseSpot;
        double heat;
        int nextDark;
        boolean darkTold;
        int nextHeatHurt;
    }

    private final MineIsle isle;
    private final NamespacedKey debrisKey;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    MineHazards(MineIsle isle) {
        this.isle = isle;
        this.debrisKey = new NamespacedKey(isle.plugin(), "mine_debris");
    }

    void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(debrisKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 20L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        states.clear();
    }

    void forget(UUID id) {
        states.remove(id);
    }

    private boolean enabled() {
        return isle.plugin().getConfig().getBoolean("hazards.enabled", true);
    }

    public double heat(Player player) {
        State state = states.get(player.getUniqueId());
        return state == null ? 0.0d : state.heat;
    }

    // ------------------------------------------------------------------ strain

    void onMined(Player player, IsleOre ore, Location at, MineDistricts.Band band) {
        if (!enabled() || band == MineDistricts.Band.SURFACE) {
            return;
        }
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        int now = Bukkit.getCurrentTick();
        long cell = cell(at);
        state.strikes.addLast(new long[] {now, cell});
        while (!state.strikes.isEmpty() && now - state.strikes.peekFirst()[0] > STRAIN_WINDOW_TICKS) {
            state.strikes.removeFirst();
        }
        if (state.collapseAt > 0) {
            return;
        }
        int local = 0;
        for (long[] strike : state.strikes) {
            if (strike[1] == cell) {
                local++;
            }
        }
        int bandBias = band == MineDistricts.Band.UNDERCROFT ? 6 : band == MineDistricts.Band.DEEP_WORKS ? 3 : 0;
        local += bandBias;
        boolean canary = isle.forge().canary(player);
        if (local < STRAIN_WARN - (canary ? 6 : 0)) {
            state.warned = false;
        }
        if (local >= STRAIN_WARN - (canary ? 6 : 0) && !state.warned) {
            state.warned = true;
            player.playSound(at, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.BLOCKS, 1.0f, 0.6f);
            player.spawnParticle(Particle.FALLING_DUST, at.clone().add(0.5, 2.8, 0.5), 10, 1.0, 0.1, 1.0, 0.0,
                    Material.GRAVEL.createBlockData());
            MineText.bar(player, canary ? "§e♪ The canary goes quiet. §7The ceiling's straining. §8Spread your digging out."
                    : "§7Dust trickles from the ceiling. §8Something up there is straining.");
        }
        if (local >= STRAIN_FALL && ThreadLocalRandom.current().nextDouble() < 0.18d) {
            telegraph(player, state, at);
        }
    }

    private static long cell(Location at) {
        long x = Math.floorDiv(at.getBlockX(), 6);
        long y = Math.floorDiv(at.getBlockY(), 6);
        long z = Math.floorDiv(at.getBlockZ(), 6);
        return (x & 0x1FFFFFL) << 42 | (y & 0x1FFFFFL) << 21 | (z & 0x1FFFFFL);
    }

    private void telegraph(Player player, State state, Location at) {
        state.collapseAt = Bukkit.getCurrentTick() + COLLAPSE_TELEGRAPH_TICKS;
        state.collapseSpot = player.getLocation().clone();
        World world = at.getWorld();
        world.playSound(state.collapseSpot, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.2f, 0.4f);
        world.playSound(state.collapseSpot, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 0.8f, 0.6f);
        MineText.bar(player, "§c§l⚠ CAVE-IN! §7Get out from under the dust!");
        player.showTitle(net.kyori.adventure.title.Title.title(MineText.legacy("§c⚠"), MineText.legacy("§7The ceiling is coming down"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1200),
                        java.time.Duration.ofMillis(300))));
    }

    private void collapse(Player player, State state) {
        Location spot = state.collapseSpot;
        state.collapseAt = 0;
        state.warned = false;
        state.strikes.clear();
        if (spot == null || spot.getWorld() == null) {
            return;
        }
        if (!player.getWorld().equals(spot.getWorld()) || !MineWorld.onIsle(isle.plugin(), player)) {
            return;
        }
        World world = spot.getWorld();
        world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 0.5f);
        world.playSound(spot, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.4f, 0.5f);
        world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 1.5, 0), 90, 1.4, 1.0, 1.4, 0.1, Material.GRAVEL.createBlockData());
        dropRubble(spot);
        boolean caught = player.getWorld().equals(world) && player.getLocation().distanceSquared(spot) <= COLLAPSE_RADIUS * COLLAPSE_RADIUS;
        if (caught) {
            double amount = player.getMaxHealth() * 0.2d * isle.forge().hazardFactor(player);
            player.damage(Math.max(0.5d, amount));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
            MineText.bar(player, "§c✖ Caught in the cave-in. §8The mountain doesn't do warnings twice.");
            return;
        }
        IsleOre loose = switch (isle.districts().band(spot)) {
            case UNDERCROFT -> ThreadLocalRandom.current().nextBoolean() ? IsleOre.DIAMOND : IsleOre.EMERALD;
            case DEEP_WORKS -> ThreadLocalRandom.current().nextBoolean() ? IsleOre.GOLD : IsleOre.LAPIS;
            default -> IsleOre.IRON;
        };
        int amount = 2 + ThreadLocalRandom.current().nextInt(4);
        MineSkills.give(player, new ItemStack(loose.resource(), amount));
        MineSkills.bonus(player, 20);
        MineText.bar(player, "§a✔ Clear! §7The ceiling came down behind you and shook loose §f" + amount + " "
                + loose.colored() + "§7. §a+20 Mining XP");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    private void dropRubble(Location spot) {
        World world = spot.getWorld();
        List<BlockDisplay> pieces = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Location from = spot.clone().add(rng.nextDouble(-1.6, 1.6), 3.2, rng.nextDouble(-1.6, 1.6));
            Material material = rng.nextBoolean() ? Material.GRAVEL : Material.COBBLED_DEEPSLATE;
            float size = (float) rng.nextDouble(0.4, 0.8);
            BlockDisplay piece = world.spawn(from, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size, size, size),
                        new AxisAngle4f()));
                spawned.setPersistent(false);
                spawned.getPersistentDataContainer().set(debrisKey, PersistentDataType.BYTE, (byte) 1);
            });
            pieces.add(piece);
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (piece.isValid()) {
                    piece.setInterpolationDelay(0);
                    piece.setInterpolationDuration(6);
                    piece.setTransformation(new Transformation(new Vector3f(0, -3.0f, 0),
                            new AxisAngle4f((float) rng.nextDouble(0, 3), 0, 1, 0), new Vector3f(size, size, size), new AxisAngle4f()));
                }
            }, 2L);
        }
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> pieces.forEach(piece -> {
            if (piece.isValid()) {
                piece.remove();
            }
        }), 60L);
    }

    // ------------------------------------------------------------------ heat and dark

    private void tick() {
        if (!enabled()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state != null && state.collapseAt > 0) {
                if (now >= state.collapseAt) {
                    collapse(player, state);
                } else if (state.collapseSpot != null && state.collapseSpot.getWorld() != null) {
                    state.collapseSpot.getWorld().spawnParticle(Particle.FALLING_DUST, state.collapseSpot.clone().add(0, 3.0, 0),
                            14, COLLAPSE_RADIUS * 0.6, 0.1, COLLAPSE_RADIUS * 0.6, 0.0, Material.GRAVEL.createBlockData());
                }
            }
            if (!MineWorld.onIsle(isle.plugin(), player) || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                if (state != null) {
                    state.heat = Math.max(0.0d, state.heat - 5.0d);
                }
                continue;
            }
            MineDistricts.District district = isle.districts().at(player.getLocation());
            boolean hot = district != null && district.id().startsWith("emberseam");
            if (hot || (state != null && state.heat > 0)) {
                if (state == null) {
                    state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
                }
                heat(player, state, hot, now);
            }
            if (isle.districts().band(player.getLocation()) == MineDistricts.Band.UNDERCROFT) {
                if (state == null) {
                    state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
                }
                dark(player, state, now);
            }
        }
    }

    private void heat(Player player, State state, boolean hot, int now) {
        boolean wet = player.isInWater() || player.getLocation().getBlock().getType() == Material.WATER_CAULDRON;
        if (wet) {
            if (state.heat > 20.0d) {
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.2f);
                player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.02);
            }
            state.heat = 0.0d;
            return;
        }
        if (hot) {
            double ward = isle.forge().hazardFactor(player);
            state.heat = Math.min(100.0d, state.heat + 1.4d * ward);
        } else {
            state.heat = Math.max(0.0d, state.heat - 4.0d);
        }
        if (state.heat >= 30.0d) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, false, false, false));
        }
        if (state.heat >= 65.0d && now >= state.nextHeatHurt) {
            state.nextHeatHurt = now + 40;
            player.damage(Math.max(0.5d, player.getMaxHealth() * 0.03d * isle.forge().hazardFactor(player)));
            player.setFireTicks(Math.max(player.getFireTicks(), 20));
        }
        if (now % 40 < 10 && state.heat > 0.0d && !isle.compass().guiding(player)) {
            String color = state.heat >= 65 ? "§c" : state.heat >= 30 ? "§6" : "§e";
            MineText.bar(player, color + "♨ Heat " + MineText.cells(state.heat / 100.0d, color) + " §8· §7"
                    + (hot ? "the Emberseam bakes you §8(water or Heat Ward helps)" : "cooling off"));
        }
    }

    private void dark(Player player, State state, int now) {
        if (now < state.nextDark) {
            return;
        }
        state.nextDark = now + 20 * 10;
        if (lit(player) || isle.forge().canary(player) || MineSkills.has(player, MineSkills.CAVE_SENSE)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 4, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.6f, 0.6f);
        if (!state.darkTold) {
            state.darkTold = true;
            player.sendMessage("§3The Undercroft swallows light. §7Hold a lantern or torch in either hand, carry a Canary, or learn"
                    + " §fCave Sense§7. §8Old Wick said this. You didn't listen.");
        }
    }

    private static boolean lit(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return (main != null && LIGHTS.contains(main.getType())) || (off != null && LIGHTS.contains(off.getType()));
    }

    /** DEV: force a cave-in on {@code player}. */
    void devCollapse(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        telegraph(player, state, player.getLocation());
    }

    static boolean solidAbove(Block block) {
        for (int i = 1; i <= 4; i++) {
            if (block.getRelative(0, i, 0).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
