package de.aetherion.dungeons.instance;

import de.aetherion.dungeons.instance.DungeonLootFx.ChestTier;
import de.aetherion.dungeons.instance.DungeonLootFx.Kind;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Reliquary family: display-built dungeon chests wrapped around the vanilla claim block.
 *
 * Delver's Reliquary (combat rooms): iron-bound dark oak, an amber seam, a lid that pops on arrival.
 * Warden's Reliquary (boss victory): a blackstone-and-gold vault that grinds up out of the floor,
 * gold shards orbiting the loot, a crimson loot beam.
 * Sovereign's Reliquary (final boss): a sky beam slams down, runes draw, the reliquary assembles
 * piece by piece on an obsidian dais, a gold gyroscope halo holds the loot, a star ignites above it.
 *
 * All three share one language: a glowing seam under the lid, a hinged lid that cracks open on
 * claim and flings open on the reveal, and a "spent" state (dark seams, no beam) once everyone has
 * taken a share. The CHEST block + its PDC stay the claim contract; everything here is presentation.
 */
public final class DungeonChestProps {

    public static final String PROP_TAG = "aether_loot_prop";
    public static final String HITBOX_TAG = "aether_loot_hitbox";

    private static final NamespacedKey PROP_BLOCK = new NamespacedKey("aetheriondungeons", "loot_prop_block");
    private static final Map<String, Prop> PROPS = new HashMap<>();
    private static BukkitTask ticker;

    private static final Color AMBER = Color.fromRGB(255, 176, 64);
    private static final Color CRIMSON = Color.fromRGB(220, 36, 48);
    private static final Color GOLD = Color.fromRGB(255, 208, 90);
    private static final Color ROYAL = Color.fromRGB(186, 110, 255);
    private static final Color PEARL = Color.fromRGB(255, 236, 250);

    private DungeonChestProps() {
    }

    private enum Role { BODY, LID, GLOW, BEAM, ORBIT, HALO, STAR }

    // ------------------------------------------------------------------ tier geometry used by the claim

    static double spinnerHeight(ChestTier tier) {
        return switch (tier) {
            case STANDARD -> 1.5;
            case BOSS -> 1.75;
            case LEGENDARY -> 2.3;
        };
    }

    static double labelHeight(ChestTier tier) {
        return switch (tier) {
            case STANDARD -> 2.05;
            case BOSS -> 2.4;
            case LEGENDARY -> 3.6;
        };
    }

    static float spinnerScale(ChestTier tier) {
        return switch (tier) {
            case STANDARD -> 1.0f;
            case BOSS -> 1.12f;
            case LEGENDARY -> 1.3f;
        };
    }

    /** Front faces the nearest player, snapped to the block grid. */
    static float facingYaw(World world, int x, int y, int z) {
        Location at = new Location(world, x + 0.5, y, z + 0.5);
        Player best = null;
        double bestDist = 64.0 * 64.0;
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        if (best == null) {
            return 0f;
        }
        double dx = best.getLocation().getX() - at.getX();
        double dz = best.getLocation().getZ() - at.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return 0f;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return wrap(Math.round(yaw / 90f) * 90f);
    }

    // ------------------------------------------------------------------ build

    /**
     * Builds the tier's prop around the block and runs {@code claim} (block + PDC + spinner + label)
     * once the arrival has finished. Without a plugin the prop is shown instantly and claimed at once.
     */
    static void build(Plugin plugin, World world, int x, int y, int z, ChestTier tier, boolean vestige, float yaw, Runnable claim) {
        clearAt(world, x, y, z);
        Prop prop = new Prop(world, x, y, z, tier, vestige, yaw, claim);
        switch (tier) {
            case STANDARD -> buildDelver(prop);
            case BOSS -> buildWarden(prop);
            case LEGENDARY -> buildSovereign(prop);
        }
        boolean live = plugin != null && plugin.isEnabled();
        prop.spawnAll(world, !live);
        if (!live) {
            prop.claimed = true;
            claim.run();
            prop.spawnHitbox(world);
            return;
        }
        PROPS.put(prop.key, prop);
        ensureTicker(plugin);
    }

    /** Loot-chest block behind a Reliquary hitbox, or null. */
    public static Block blockOf(Entity entity) {
        if (!(entity instanceof Interaction) || !entity.getScoreboardTags().contains(HITBOX_TAG)) {
            return null;
        }
        String coords = entity.getPersistentDataContainer().get(PROP_BLOCK, PersistentDataType.STRING);
        if (coords == null) {
            return null;
        }
        String[] split = coords.split(",");
        if (split.length != 3) {
            return null;
        }
        try {
            return entity.getWorld().getBlockAt(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Tear down Reliquary displays at a block (survives restarts when PROPS is empty). */
    public static void clearPropAt(World world, int x, int y, int z) {
        clearAt(world, x, y, z);
    }

    private static void clearAt(World world, int x, int y, int z) {
        Prop old = PROPS.remove(key(world, x, y, z));
        if (old != null) {
            old.removeAll();
        }
        String coords = coords(x, y, z);
        Location at = new Location(world, x + 0.5, y + 1.5, z + 0.5);
        for (Entity entity : world.getNearbyEntities(at, 3.0, 4.0, 3.0)) {
            if (!entity.getScoreboardTags().contains(PROP_TAG) && !entity.getScoreboardTags().contains(HITBOX_TAG)) {
                continue;
            }
            if (coords.equals(entity.getPersistentDataContainer().get(PROP_BLOCK, PersistentDataType.STRING))) {
                entity.remove();
            }
        }
    }

    private static void ensureTicker(Plugin plugin) {
        if (ticker != null && !ticker.isCancelled()) {
            return;
        }
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (PROPS.isEmpty()) {
                    cancel();
                    ticker = null;
                    return;
                }
                Iterator<Prop> it = PROPS.values().iterator();
                while (it.hasNext()) {
                    Prop prop = it.next();
                    boolean keep;
                    try {
                        keep = prop.tick();
                    } catch (RuntimeException exception) {
                        keep = false;
                    }
                    if (!keep) {
                        prop.removeAll();
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ claim hooks (from DungeonLootFx)

    static void onOpenStart(Block block) {
        Prop prop = find(block);
        if (prop == null) {
            return;
        }
        prop.busy = true;
        prop.setLid(-0.42, 0.1);
        World world = block.getWorld();
        Location seam = prop.at(0, prop.seamY(), 0);
        if (prop.tier == ChestTier.STANDARD) {
            world.playSound(seam, Sound.BLOCK_CHEST_OPEN, 0.6f, 1.15f);
        } else {
            world.playSound(seam, Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.9f, prop.tier == ChestTier.LEGENDARY ? 0.8f : 1.0f);
        }
        world.spawnParticle(Particle.DUST, seam, 10, prop.halfWidth() * 0.6, 0.05, prop.halfWidth() * 0.5, 0, new Particle.DustOptions(prop.accent(), 0.9f));
    }

    static void onSpinStep(Block block, int step) {
        Prop prop = find(block);
        if (prop == null) {
            return;
        }
        prop.spinBoost = 1.0;
        if (prop.tier == ChestTier.LEGENDARY && step % 4 == 0) {
            Location core = prop.at(0, spinnerHeight(prop.tier), 0);
            block.getWorld().spawnParticle(Particle.ENCHANT, core, 8, 0.6, 0.6, 0.6, 0.6);
        } else if (prop.tier == ChestTier.BOSS && step % 5 == 0) {
            Location core = prop.at(0, spinnerHeight(prop.tier), 0);
            block.getWorld().spawnParticle(Particle.DUST, core, 4, 0.45, 0.3, 0.45, 0, new Particle.DustOptions(GOLD, 0.8f));
        }
    }

    static void onReveal(Block block, Kind kind) {
        Prop prop = find(block);
        if (prop == null) {
            return;
        }
        prop.setLid(-1.95, 0.38);
        World world = block.getWorld();
        Location core = prop.at(0, spinnerHeight(prop.tier), 0);
        Location seam = prop.at(0, prop.seamY() + 0.1, 0);
        boolean big = kind == Kind.MYTH || kind == Kind.RELIC || kind == Kind.COMPACTED;
        world.playSound(core, Sound.BLOCK_VAULT_EJECT_ITEM, 0.9f, prop.tier == ChestTier.STANDARD ? 1.3f : 1.0f);
        switch (prop.tier) {
            case STANDARD -> {
                world.spawnParticle(Particle.DUST, seam, 18, 0.35, 0.2, 0.3, 0, new Particle.DustOptions(AMBER, 1.0f));
                world.spawnParticle(Particle.CRIT, seam, 8, 0.3, 0.2, 0.3, 0.15);
            }
            case BOSS -> {
                world.spawnParticle(Particle.DUST, seam, 26, 0.5, 0.25, 0.4, 0, new Particle.DustOptions(GOLD, 1.1f));
                world.spawnParticle(Particle.DUST, core, 14, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(CRIMSON, 1.0f));
                world.spawnParticle(Particle.LAVA, seam, 3, 0.3, 0.1, 0.3, 0);
            }
            case LEGENDARY -> {
                ringParticles(world, prop.at(0, 0.18, 0), 1.6, ROYAL, 1.2f, 1.0);
                world.spawnParticle(Particle.END_ROD, core, 26, 0.2, 0.2, 0.2, 0.12);
                world.spawnParticle(Particle.REVERSE_PORTAL, core, 30, 0.5, 0.5, 0.5, 0.05);
                world.playSound(core, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
            }
        }
        if (big) {
            prop.flare(world, kind == Kind.MYTH);
        }
    }

    static void onClosed(Block block, boolean empty) {
        Prop prop = find(block);
        if (prop == null) {
            return;
        }
        prop.busy = false;
        prop.setLid(0.0, 0.3);
        prop.slamPending = true;
        if (empty && !prop.spent) {
            prop.spentAt = prop.age + 14;
        }
    }

    private static Prop find(Block block) {
        if (block == null) {
            return null;
        }
        return PROPS.get(key(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }

    // ------------------------------------------------------------------ T1 Delver's Reliquary

    private static void buildDelver(Prop p) {
        Material seam = p.vestige ? Material.AMETHYST_BLOCK : Material.OCHRE_FROGLIGHT;
        p.hinge = new Vector3f(0f, 0.9f, -0.465f);
        p.seamY = 0.88;
        p.halfWidth = 0.47;
        p.accent = p.vestige ? ROYAL : AMBER;

        List<Part> body = new ArrayList<>();
        body.add(p.box(Role.BODY, Material.POLISHED_DEEPSLATE, -0.52, 0, -0.52, 0.52, 0.06, 0.52));
        body.add(p.box(Role.BODY, Material.DARK_OAK_PLANKS, -0.46, 0.06, -0.45, 0.46, 0.9, 0.45));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                body.add(p.box(Role.BODY, Material.NETHERITE_BLOCK,
                        Math.min(sx * 0.43, sx * 0.49), 0.06, Math.min(sz * 0.42, sz * 0.48),
                        Math.max(sx * 0.43, sx * 0.49), 0.9, Math.max(sz * 0.42, sz * 0.48)));
            }
        }
        body.add(p.box(Role.BODY, Material.NETHERITE_BLOCK, -0.475, 0.3, -0.465, 0.475, 0.36, 0.465));
        Part glow = p.box(Role.GLOW, seam, -0.43, 0.84, -0.42, 0.43, 0.905, 0.42);
        Part band = p.box(Role.GLOW, seam, -0.472, 0.855, -0.462, 0.472, 0.895, 0.462);

        List<Part> lid = new ArrayList<>();
        lid.add(p.box(Role.LID, Material.STRIPPED_DARK_OAK_WOOD, -0.475, 0.9, -0.465, 0.475, 1.14, 0.465));
        for (int s = -1; s <= 1; s += 2) {
            lid.add(p.box(Role.LID, Material.NETHERITE_BLOCK, s * 0.25 - 0.045, 0.895, -0.48, s * 0.25 + 0.045, 1.155, 0.48));
        }
        lid.add(p.box(Role.LID, Material.IRON_BLOCK, -0.085, 0.72, 0.46, 0.085, 0.98, 0.5));
        lid.add(p.box(Role.LID, Material.BLACK_CONCRETE, -0.022, 0.78, 0.5, 0.022, 0.88, 0.506));

        for (Part part : body) {
            part.appear(1, 5, 0, -0.2, 0, 0.35);
        }
        for (Part part : lid) {
            part.appear(2, 5, 0, 0.25, 0, 0.35);
        }
        glow.appear(3, 4, 0, 0, 0, 0.2);
        band.appear(3, 4, 0, 0, 0, 0.2);
        p.claimAt = 4;
    }

    // ------------------------------------------------------------------ T2 Warden's Reliquary

    private static void buildWarden(Prop p) {
        p.hinge = new Vector3f(0f, 0.95f, -0.52f);
        p.seamY = 0.93;
        p.halfWidth = 0.68;
        p.accent = CRIMSON;
        int rise = 2;
        int riseTicks = 28;

        List<Part> body = new ArrayList<>();
        body.add(p.box(Role.BODY, Material.POLISHED_BLACKSTONE, -0.78, 0, -0.66, 0.78, 0.16, 0.66));
        body.add(p.box(Role.BODY, Material.GILDED_BLACKSTONE, -0.7, 0.16, -0.58, 0.7, 0.22, 0.58));
        body.add(p.box(Role.BODY, Material.POLISHED_BLACKSTONE_BRICKS, -0.62, 0.22, -0.5, 0.62, 0.95, 0.5));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                body.add(p.box(Role.BODY, Material.GOLD_BLOCK,
                        Math.min(sx * 0.57, sx * 0.67), 0.22, Math.min(sz * 0.45, sz * 0.55),
                        Math.max(sx * 0.57, sx * 0.67), 0.99, Math.max(sz * 0.45, sz * 0.55)));
            }
        }
        body.add(p.box(Role.BODY, Material.GOLD_BLOCK, -0.635, 0.5, -0.515, 0.635, 0.56, 0.515));
        body.add(p.diamond(Role.BODY, Material.CHISELED_POLISHED_BLACKSTONE, 0, 0.62, 0.505, 0.22, 0.03));
        for (int s = -1; s <= 1; s += 2) {
            body.add(p.box(Role.BODY, Material.LANTERN, s * 0.72 - 0.16, 0.16, 0.44, s * 0.72 + 0.16, 0.56, 0.76));
        }
        Part glow = p.box(Role.GLOW, Material.REDSTONE_BLOCK, -0.58, 0.9, -0.46, 0.58, 0.955, 0.46);
        Part band = p.box(Role.GLOW, Material.REDSTONE_BLOCK, -0.632, 0.9, -0.512, 0.632, 0.945, 0.512);

        List<Part> lid = new ArrayList<>();
        lid.add(p.box(Role.LID, Material.POLISHED_BLACKSTONE, -0.64, 0.95, -0.52, 0.64, 1.1, 0.52));
        lid.add(p.box(Role.LID, Material.CHISELED_POLISHED_BLACKSTONE, -0.5, 1.1, -0.4, 0.5, 1.22, 0.4));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.655, 1.08, -0.535, 0.655, 1.12, 0.535));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.52, 1.22, -0.06, 0.52, 1.28, 0.06));
        lid.add(p.diamond(Role.LID, Material.GOLD_BLOCK, 0, 1.02, 0.53, 0.24, 0.04));
        lid.add(p.diamond(Role.LID, Material.REDSTONE_BLOCK, 0, 1.02, 0.55, 0.11, 0.03));

        for (Part part : body) {
            part.appear(rise, riseTicks, 0, -1.45, 0, 1.0);
        }
        for (Part part : lid) {
            part.appear(rise, riseTicks, 0, -1.45, 0, 1.0);
        }
        glow.appear(31, 3, 0, 0, 0, 0.2);
        band.appear(31, 3, 0, 0, 0, 0.2);

        double h = spinnerHeight(ChestTier.BOSS);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            Part shard = p.shard(Role.ORBIT, Material.GOLD_BLOCK, Math.cos(a) * 0.62, 0, Math.sin(a) * 0.62, 0.13, h);
            shard.phase = i * 1.6f;
            shard.flyIn(32 + i * 2, 10, 2.4, 1.2);
        }

        Part outer = p.beam(Material.RED_STAINED_GLASS, 0.2, h + 0.5, 8.0);
        Part inner = p.beam(Material.WHITE_STAINED_GLASS, 0.08, h + 0.5, 8.0);
        outer.growUp(38, 6);
        inner.growUp(39, 6);
        p.claimAt = 42;
        p.hitboxWidth = 1.6f;
        p.hitboxHeight = 1.35f;
    }

    // ------------------------------------------------------------------ T3 Sovereign's Reliquary

    private static void buildSovereign(Prop p) {
        p.hinge = new Vector3f(0f, 1.0f, -0.57f);
        p.seamY = 0.98;
        p.halfWidth = 0.75;
        p.accent = ROYAL;

        // Dais.
        List<Part> dais = new ArrayList<>();
        dais.add(p.box(Role.BODY, Material.OBSIDIAN, -1.12, 0, -1.12, 1.12, 0.14, 1.12));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.14, 0.1, -1.14, 1.14, 0.15, -1.06));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.14, 0.1, 1.06, 1.14, 0.15, 1.14));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, -1.14, 0.1, -1.06, -1.06, 0.15, 1.06));
        dais.add(p.box(Role.BODY, Material.GOLD_BLOCK, 1.06, 0.1, -1.06, 1.14, 0.15, 1.06));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                dais.add(p.box(Role.BODY, Material.CRYING_OBSIDIAN,
                        sx * 1.02 - 0.13, 0.0, sz * 1.02 - 0.13, sx * 1.02 + 0.13, 0.2, sz * 1.02 + 0.13));
            }
        }
        List<Part> step = new ArrayList<>();
        step.add(p.box(Role.BODY, Material.GILDED_BLACKSTONE, -0.86, 0.14, -0.74, 0.86, 0.26, 0.74));

        // Body.
        List<Part> body = new ArrayList<>();
        body.add(p.box(Role.BODY, Material.OBSIDIAN, -0.7, 0.26, -0.55, 0.7, 1.0, 0.55));
        List<Part> frame = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                frame.add(p.box(Role.BODY, Material.GOLD_BLOCK,
                        Math.min(sx * 0.65, sx * 0.74), 0.26, Math.min(sz * 0.5, sz * 0.59),
                        Math.max(sx * 0.65, sx * 0.74), 1.03, Math.max(sz * 0.5, sz * 0.59)));
            }
        }
        frame.add(p.box(Role.BODY, Material.GOLD_BLOCK, -0.715, 0.26, -0.565, 0.715, 0.32, 0.565));
        List<Part> panels = new ArrayList<>();
        panels.add(p.box(Role.BODY, Material.AMETHYST_BLOCK, -0.46, 0.4, 0.55, 0.46, 0.88, 0.575));
        panels.add(p.box(Role.BODY, Material.AMETHYST_BLOCK, -0.46, 0.4, -0.575, 0.46, 0.88, -0.55));
        panels.add(p.box(Role.BODY, Material.AMETHYST_BLOCK, 0.7, 0.4, -0.36, 0.725, 0.88, 0.36));
        panels.add(p.box(Role.BODY, Material.AMETHYST_BLOCK, -0.725, 0.4, -0.36, -0.7, 0.88, 0.36));
        panels.add(p.diamond(Role.BODY, Material.GOLD_BLOCK, 0, 0.64, 0.58, 0.2, 0.025));

        List<Part> glow = new ArrayList<>();
        glow.add(p.box(Role.GLOW, Material.PEARLESCENT_FROGLIGHT, -0.66, 0.95, -0.51, 0.66, 1.005, 0.51));
        glow.add(p.box(Role.GLOW, Material.PEARLESCENT_FROGLIGHT, -0.712, 0.955, -0.562, 0.712, 0.995, 0.562));

        List<Part> lid = new ArrayList<>();
        lid.add(p.box(Role.LID, Material.OBSIDIAN, -0.72, 1.0, -0.57, 0.72, 1.16, 0.57));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.735, 1.13, -0.585, 0.735, 1.17, 0.585));
        lid.add(p.box(Role.LID, Material.CRYING_OBSIDIAN, -0.58, 1.16, -0.45, 0.58, 1.28, 0.45));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.42, 1.28, -0.3, 0.42, 1.36, 0.3));
        lid.add(p.box(Role.LID, Material.GOLD_BLOCK, -0.44, 1.36, -0.05, 0.44, 1.42, 0.05));
        lid.add(p.diamond(Role.LID, Material.GOLD_BLOCK, 0, 1.06, 0.58, 0.3, 0.04));
        lid.add(p.diamond(Role.LID, Material.AMETHYST_BLOCK, 0, 1.06, 0.6, 0.15, 0.03));

        List<Part> pillars = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double cx = sx * 1.02;
                double cz = sz * 1.02;
                pillars.add(p.box(Role.GLOW, Material.END_ROD, cx - 0.5, 0.2, cz - 0.5, cx + 0.5, 1.9, cz + 0.5));
            }
        }

        stage(dais, 20, 8, 0, -0.3, 0, 0.6);
        stage(step, 26, 7, 0, -0.25, 0, 0.5);
        stage(body, 32, 7, 0, -0.4, 0, 0.3);
        stage(frame, 36, 6, 0, 0.3, 0, 0.2);
        stage(panels, 40, 6, 0, 0, 0, 0.1);
        stage(glow, 44, 5, 0, 0, 0, 0.1);
        stage(lid, 50, 8, 0, 0.9, 0, 0.5);
        for (Part pillar : pillars) {
            pillar.growUp(56, 8);
        }

        double h = spinnerHeight(ChestTier.LEGENDARY);
        int ring = 16;
        float radius = 0.64f;
        for (int i = 0; i < ring; i++) {
            double a = i * Math.PI * 2 / ring;
            Part seg = p.segmentVertical(Material.GOLD_BLOCK, a, radius, 0.06f, h, 2 * (float) Math.PI * radius / ring * 1.05f);
            seg.appear(64 + i / 4, 6, 0, 0, 0, 0.05);
        }
        int flat = 12;
        for (int i = 0; i < flat; i++) {
            double a = i * Math.PI * 2 / flat;
            Part seg = p.segmentFlat(Material.AMETHYST_BLOCK, a, 0.82f, 0.05f, h, 2 * (float) Math.PI * 0.82f / flat * 1.05f);
            seg.appear(66 + i / 4, 6, 0, 0, 0, 0.05);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Part shard = p.shard(Role.ORBIT, Material.AMETHYST_CLUSTER, Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3, 0.42, 1.55);
            shard.phase = i * 1.05f;
            shard.flyIn(68 + i, 9, 3.2, 1.8);
        }
        Part star = p.star(new ItemStack(Material.NETHER_STAR), h + 0.85, 0.55f);
        star.appear(80, 6, 0, 0, 0, 0.05);

        Part outer = p.beam(Material.PURPLE_STAINED_GLASS, 0.26, h + 1.2, 12.0);
        Part inner = p.beam(Material.WHITE_STAINED_GLASS, 0.1, h + 1.2, 12.0);
        outer.growUp(84, 6);
        inner.growUp(85, 6);
        p.claimAt = 90;
        p.hitboxWidth = 2.3f;
        p.hitboxHeight = 1.5f;
    }

    private static void stage(List<Part> parts, int at, int ticks, double dx, double dy, double dz, double scale) {
        for (Part part : parts) {
            part.appear(at, ticks, dx, dy, dz, scale);
        }
    }

    // ------------------------------------------------------------------ prop

    private static final class Prop {
        private final String key;
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;
        private final ChestTier tier;
        private final boolean vestige;
        private final float yaw;
        private final Runnable claim;
        private final List<Part> parts = new ArrayList<>();
        private final List<Temp> temps = new ArrayList<>();
        private UUID hitbox;
        private Vector3f hinge = new Vector3f();
        private double seamY = 0.9;
        private double halfWidth = 0.5;
        private Color accent = AMBER;
        private float hitboxWidth;
        private float hitboxHeight;
        private int claimAt;
        private boolean claimed;
        private int age;
        private int missing;
        private double lid;
        private double lidTarget;
        private double lidSpeed = 0.2;
        private boolean slamPending;
        private boolean busy;
        private boolean spent;
        private int spentAt = -1;
        private double spinBoost;
        private float orbit;
        private float halo;

        private Prop(World world, int x, int y, int z, ChestTier tier, boolean vestige, float yaw, Runnable claim) {
            this.key = key(world, x, y, z);
            this.worldId = world.getUID();
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.vestige = vestige;
            this.yaw = yaw;
            this.claim = claim;
        }

        private double seamY() {
            return seamY;
        }

        private double halfWidth() {
            return halfWidth;
        }

        private Color accent() {
            return accent;
        }

        private boolean playersNear(World world, double radius) {
            double r2 = radius * radius;
            for (Player player : world.getPlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - (x + 0.5);
                double dy = at.getY() - y;
                double dz = at.getZ() - (z + 0.5);
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    return true;
                }
            }
            return false;
        }

        private BlockData floorData(World world) {
            Block below = world.getBlockAt(x, y - 1, z);
            return below.getType().isSolid() ? below.getBlockData() : Material.DEEPSLATE_TILES.createBlockData();
        }

        private Location at(double lx, double ly, double lz) {
            World world = Bukkit.getWorld(worldId);
            Vector3f rotated = new Quaternionf().rotateY((float) Math.toRadians(-yaw)).transform(new Vector3f((float) lx, 0f, (float) lz));
            return new Location(world, x + 0.5 + rotated.x, y + ly, z + 0.5 + rotated.z);
        }

        // --- part factories (local space: origin = block bottom center, front = +Z)

        private Part box(Role role, Material material, double x0, double y0, double z0, double x1, double y1, double z1) {
            Transformation home = new Transformation(
                    new Vector3f((float) x0, (float) y0, (float) z0),
                    new Quaternionf(),
                    new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0)),
                    new Quaternionf());
            return add(new Part(role, material, null, home, 0.0));
        }

        /** A square turned 45 degrees on the front face, centered at (cx, cy, cz). */
        private Part diamond(Role role, Material material, double cx, double cy, double cz, double size, double depth) {
            Quaternionf q = new Quaternionf().rotateZ((float) (Math.PI / 4));
            Vector3f scale = new Vector3f((float) size, (float) size, (float) depth);
            Vector3f half = q.transform(new Vector3f(scale).mul(0.5f));
            Vector3f t = new Vector3f((float) cx, (float) cy, (float) cz).sub(half);
            return add(new Part(role, material, null, new Transformation(t, q, scale, new Quaternionf()), 0.0));
        }

        /** Tilted cube around an orbit center {@code lift} above the block. */
        private Part shard(Role role, Material material, double cx, double cy, double cz, double size, double lift) {
            Quaternionf q = new Quaternionf().rotateY((float) Math.atan2(cx, cz)).rotateX(0.6f).rotateZ(0.6f);
            Vector3f scale = new Vector3f((float) size, (float) size, (float) size);
            Vector3f half = q.transform(new Vector3f(scale).mul(0.5f));
            Vector3f t = new Vector3f((float) cx, (float) cy, (float) cz).sub(half);
            return add(new Part(role, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        /** Tangent segment of a ring standing upright in the local XY plane (faces the front). */
        private Part segmentVertical(Material material, double a, float r, float thick, double lift, float len) {
            Quaternionf q = new Quaternionf().rotateZ((float) (a + Math.PI / 2));
            Vector3f scale = new Vector3f(len, thick, thick);
            Vector3f center = new Vector3f((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0f);
            Vector3f t = center.sub(q.transform(new Vector3f(scale).mul(0.5f)));
            return add(new Part(Role.HALO, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        /** Tangent segment of a flat ring in the local XZ plane. */
        private Part segmentFlat(Material material, double a, float r, float thick, double lift, float len) {
            Quaternionf q = new Quaternionf().rotateY((float) -(a + Math.PI / 2));
            Vector3f scale = new Vector3f(len, thick, thick);
            Vector3f center = new Vector3f((float) (Math.cos(a) * r), 0f, (float) (Math.sin(a) * r));
            Vector3f t = center.sub(q.transform(new Vector3f(scale).mul(0.5f)));
            return add(new Part(Role.BODY, material, null, new Transformation(t, q, scale, new Quaternionf()), lift));
        }

        private Part beam(Material material, double width, double from, double height) {
            Transformation home = new Transformation(
                    new Vector3f((float) -width / 2f, (float) from, (float) -width / 2f),
                    new Quaternionf(),
                    new Vector3f((float) width, (float) height, (float) width),
                    new Quaternionf());
            Part part = add(new Part(Role.BEAM, material, null, home, 0.0));
            part.glow = true;
            return part;
        }

        private Part star(ItemStack item, double lift, float size) {
            Transformation home = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf());
            Part part = add(new Part(Role.STAR, null, item, home, lift));
            part.glow = true;
            return part;
        }

        private Part add(Part part) {
            if (part.role == Role.GLOW || part.role == Role.BEAM || part.role == Role.ORBIT || part.role == Role.HALO) {
                part.glow = true;
            }
            parts.add(part);
            return part;
        }

        // --- lifecycle

        private void spawnAll(World world, boolean instant) {
            String coords = coords(x, y, z);
            for (Part part : parts) {
                Location at = new Location(world, x + 0.5, y + part.lift, z + 0.5, yaw, 0f);
                Transformation start = instant ? pose(part) : part.hidden;
                Display display;
                if (part.item != null) {
                    display = world.spawn(at, ItemDisplay.class, spawned -> {
                        spawned.setItemStack(part.item);
                        spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        prepare(spawned, part, start, coords, true);
                    });
                } else {
                    BlockData data = part.material.createBlockData();
                    display = world.spawn(at, BlockDisplay.class, spawned -> {
                        spawned.setBlock(data);
                        prepare(spawned, part, start, coords, true);
                    });
                }
                part.id = display.getUniqueId();
                part.cached = display;
                part.shown = instant;
            }
        }

        private void prepare(Display display, Part part, Transformation start, String coords, boolean persistent) {
            display.setTransformation(start);
            display.setInterpolationDuration(0);
            display.setTeleportDuration(part.role == Role.ORBIT || part.role == Role.HALO || part.role == Role.STAR ? 2 : 0);
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0f);
            display.setViewRange(tier == ChestTier.LEGENDARY ? 2.0f : tier == ChestTier.BOSS ? 1.5f : 1.0f);
            if (part.glow) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.addScoreboardTag(PROP_TAG);
            display.getPersistentDataContainer().set(PROP_BLOCK, PersistentDataType.STRING, coords);
        }

        private void spawnHitbox(World world) {
            if (hitboxWidth <= 0f) {
                return;
            }
            String coords = coords(x, y, z);
            Interaction box = world.spawn(new Location(world, x + 0.5, y, z + 0.5), Interaction.class, spawned -> {
                spawned.setInteractionWidth(hitboxWidth);
                spawned.setInteractionHeight(hitboxHeight);
                spawned.setResponsive(true);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(HITBOX_TAG);
                spawned.getPersistentDataContainer().set(PROP_BLOCK, PersistentDataType.STRING, coords);
            });
            hitbox = box.getUniqueId();
        }

        private void removeAll() {
            for (Part part : parts) {
                Display display = part.resolve();
                if (display != null) {
                    display.remove();
                }
            }
            parts.clear();
            for (Temp temp : temps) {
                if (temp.display != null && temp.display.isValid()) {
                    temp.display.remove();
                }
            }
            temps.clear();
            if (hitbox != null) {
                Entity entity = Bukkit.getEntity(hitbox);
                if (entity != null) {
                    entity.remove();
                }
                hitbox = null;
            }
        }

        /** @return false when the prop is gone for good and should be disposed */
        private boolean tick() {
            World world = Bukkit.getWorld(worldId);
            if (world == null || parts.isEmpty()) {
                return false;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return true;
            }
            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isEntitiesLoaded()) {
                return true;
            }
            if (parts.get(0).resolve() == null) {
                return ++missing < 40;
            }
            if (claimed && world.getBlockAt(x, y, z).getType() != Material.CHEST) {
                return ++missing < 40;
            }
            missing = 0;
            age++;

            for (Part part : parts) {
                if (!part.shown && age >= part.appearAt) {
                    part.shown = true;
                    ease(part.resolve(), pose(part), part.appearTicks);
                }
            }
            script(world);
            if (!claimed && age >= claimAt) {
                claimed = true;
                claim.run();
                spawnHitbox(world);
            }
            tickLid(world);
            if (spentAt >= 0 && age >= spentAt && !spent) {
                becomeSpent(world);
            }
            tickTemps();
            spinBoost *= 0.9;
            if (claimed && !spent && playersNear(world, 48.0)) {
                idle(world);
            }
            return true;
        }

        private void setLid(double target, double speed) {
            lidTarget = target;
            lidSpeed = speed;
        }

        private void tickLid(World world) {
            if (Math.abs(lid - lidTarget) < 1.0E-4) {
                return;
            }
            double before = lid;
            lid = lid < lidTarget ? Math.min(lidTarget, lid + lidSpeed) : Math.max(lidTarget, lid - lidSpeed);
            for (Part part : parts) {
                if (part.role == Role.LID && part.shown && age >= part.appearAt + part.appearTicks) {
                    ease(part.resolve(), pose(part), 1);
                }
            }
            if (slamPending && before < 0 && lid >= 0) {
                slamPending = false;
                Location seam = at(0, seamY, 0);
                if (tier == ChestTier.STANDARD) {
                    world.playSound(seam, Sound.BLOCK_CHEST_CLOSE, 0.6f, 0.9f);
                } else {
                    world.playSound(seam, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 0.9f, tier == ChestTier.LEGENDARY ? 0.7f : 0.9f);
                }
                world.spawnParticle(Particle.SMOKE, seam, 6, halfWidth * 0.6, 0.03, halfWidth * 0.5, 0.01);
            }
        }

        private Transformation pose(Part part) {
            if (part.role == Role.LID && Math.abs(lid) > 1.0E-4) {
                return hinged(part.home, hinge, lid);
            }
            if (spent && part.role == Role.ORBIT) {
                Vector3f t = new Vector3f(part.home.getTranslation()).add(0f, (float) (0.22 - part.lift), 0f);
                return new Transformation(t, part.home.getLeftRotation(), part.home.getScale(), part.home.getRightRotation());
            }
            return part.home;
        }

        // --- scripted arrivals

        private void script(World world) {
            Location base = at(0, 0.1, 0);
            switch (tier) {
                case STANDARD -> {
                    if (age == 1) {
                        world.playSound(base, Sound.BLOCK_VAULT_ACTIVATE, 0.55f, 1.35f);
                        world.spawnParticle(Particle.BLOCK, base, 18, 0.45, 0.05, 0.45, 0, floorData(world));
                        world.spawnParticle(Particle.DUST, at(0, 0.6, 0), 10, 0.35, 0.3, 0.35, 0, new Particle.DustOptions(accent, 0.9f));
                    }
                    if (age == 8 && !busy) {
                        setLid(-0.6, 0.22);
                        world.playSound(at(0, seamY, 0), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
                        world.spawnParticle(Particle.DUST, at(0, seamY + 0.15, 0), 12, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(accent, 1.0f));
                    }
                    if (age == 15 && !busy) {
                        setLid(0.0, 0.34);
                        slamPending = true;
                    }
                }
                case BOSS -> {
                    if (age == 1) {
                        world.playSound(base, Sound.BLOCK_VAULT_ACTIVATE, 1.0f, 0.7f);
                        world.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 0.6f);
                    }
                    if (age >= 2 && age <= 30) {
                        if (age % 3 == 0) {
                            ringParticlesBlock(world, at(0, 0.1, 0), 0.95, floorData(world), 10);
                        }
                        if (age % 8 == 2) {
                            world.playSound(base, Sound.BLOCK_GRINDSTONE_USE, 0.55f, 0.5f);
                        }
                    }
                    if (age == 30) {
                        world.playSound(base, Sound.ITEM_MACE_SMASH_GROUND, 0.7f, 0.7f);
                        world.spawnParticle(Particle.BLOCK, base, 30, 0.8, 0.05, 0.7, 0, floorData(world));
                        world.spawnParticle(Particle.DUST, at(0, seamY, 0), 16, 0.5, 0.05, 0.4, 0, new Particle.DustOptions(CRIMSON, 1.0f));
                    }
                    if (age == 32) {
                        world.playSound(base, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
                    }
                    if (age == 38) {
                        world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);
                    }
                    if (age == 42) {
                        world.playSound(base, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
                    }
                    if (age == 44 && !busy) {
                        setLid(-0.55, 0.18);
                        world.playSound(at(0, seamY, 0), Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.8f, 1.1f);
                        world.spawnParticle(Particle.DUST, at(0, seamY + 0.2, 0), 16, 0.4, 0.1, 0.35, 0, new Particle.DustOptions(GOLD, 1.0f));
                    }
                    if (age == 54 && !busy) {
                        setLid(0.0, 0.3);
                        slamPending = true;
                    }
                }
                case LEGENDARY -> scriptSovereign(world, base);
            }
        }

        /*
         * 1-6 a beam slams down from the sky · 8-30 two rune circles draw themselves ·
         * 20-66 the reliquary assembles bottom-up, one chime per layer · 68-76 the shards fly in ·
         * 80 the star ignites (title) · 84 the loot beam · 90 the claim · 94 the lid breathes.
         */
        private void scriptSovereign(World world, Location base) {
            if (age == 1) {
                world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
                world.playSound(base, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.6f);
                world.playSound(base, Sound.BLOCK_END_PORTAL_SPAWN, 0.55f, 1.5f);
                Temp outer = temp(world, Material.PURPLE_STAINED_GLASS, skyColumn(1.3f, 0.001f), 46);
                Temp inner = temp(world, Material.WHITE_STAINED_GLASS, skyColumn(0.55f, 0.001f), 46);
                outer.then(skyColumn(1.3f, 30f), 5);
                inner.then(skyColumn(0.55f, 30f), 5);
            }
            if (age == 6) {
                Location impact = at(0, 0.3, 0);
                world.spawnParticle(Particle.FLASH, impact, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.END_ROD, impact, 40, 0.3, 0.1, 0.3, 0.25);
                world.spawnParticle(Particle.BLOCK, impact, 40, 1.2, 0.05, 1.2, 0, floorData(world));
                world.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.8f, 1.3f);
                world.playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.7f);
            }
            if (age == 30) {
                for (Temp temp : temps) {
                    if (temp.sky) {
                        temp.then(skyColumn(0.001f, 30f), 10);
                    }
                }
            }
            if (age == 8) {
                world.playSound(base, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.7f);
            }
            if (age >= 8 && age <= 90 && age % 2 == 0) {
                double fill = Math.min(1.0, (age - 8) / 22.0);
                ringParticles(world, at(0, 0.17, 0), 1.55, ROYAL, 1.0f, fill);
                ringParticles(world, at(0, 0.17, 0), 2.1, GOLD, 0.8f, fill);
                if (age <= 30) {
                    world.spawnParticle(Particle.ENCHANT, at(0, 0.6, 0), 12, 1.4, 0.3, 1.4, 0.4);
                }
            }
            int[] layers = {20, 26, 32, 36, 40, 44, 50, 56, 64, 68};
            for (int i = 0; i < layers.length; i++) {
                if (age == layers[i]) {
                    float pitch = 0.6f + i * 0.13f;
                    world.playSound(base, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, pitch);
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.9f, pitch);
                    world.spawnParticle(Particle.END_ROD, at(0, 0.3 + i * 0.12, 0), 6, 0.5, 0.1, 0.5, 0.03);
                }
            }
            if (age == 80) {
                Location star = at(0, spinnerHeight(tier) + 0.85, 0);
                world.spawnParticle(Particle.FLASH, star, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, star, 40, 0.3, 0.3, 0.3, 0.4);
                world.spawnParticle(Particle.END_ROD, star, 20, 0.1, 0.1, 0.1, 0.15);
                world.playSound(star, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                world.playSound(star, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.2f);
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(star) <= 48 * 48) {
                        player.sendTitle("§d✦ Sovereign's Reliquary ✦", "§7The hoard answers.", 6, 44, 14);
                    }
                }
            }
            if (age == 84) {
                world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }
            if (age == 94 && !busy) {
                setLid(-0.5, 0.12);
                world.playSound(at(0, seamY, 0), Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.9f, 0.8f);
                world.spawnParticle(Particle.END_ROD, at(0, seamY + 0.2, 0), 14, 0.4, 0.1, 0.35, 0.04);
            }
            if (age == 106 && !busy) {
                setLid(0.0, 0.25);
                slamPending = true;
            }
        }

        // --- idle

        private void idle(World world) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float boost = (float) (1.0 + 3.0 * spinBoost);
            switch (tier) {
                case STANDARD -> {
                    if (age % 12 == 0) {
                        Location mote = at(random.nextDouble(-0.4, 0.4), seamY + random.nextDouble(0.0, 0.25), random.nextDouble(-0.4, 0.4));
                        world.spawnParticle(Particle.DUST, mote, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(accent, 0.7f));
                    }
                }
                case BOSS -> {
                    if (age % 2 == 0) {
                        orbit = wrap(orbit + 3.0f * boost);
                        animateOrbit(Role.ORBIT, orbit, 0.05);
                    }
                    if (age % 5 == 0) {
                        double h = spinnerHeight(tier) + 0.5 + random.nextDouble(7.0);
                        world.spawnParticle(Particle.DUST, at(0, h, 0), 1, 0.08, 0.2, 0.08, 0, new Particle.DustOptions(CRIMSON, 0.9f));
                    }
                    if (age % 9 == 0) {
                        world.spawnParticle(Particle.SMALL_FLAME, at(random.nextBoolean() ? 0.72 : -0.72, 0.4, 0.6), 1, 0.02, 0.02, 0.02, 0.003);
                    }
                    if (age % 100 == 0) {
                        world.playSound(at(0, 1, 0), Sound.BLOCK_VAULT_AMBIENT, 0.5f, 0.8f);
                    }
                }
                case LEGENDARY -> {
                    if (age % 2 == 0) {
                        halo = wrap(halo + 5.0f * boost);
                        orbit = wrap(orbit - 3.0f * boost);
                        animateHalo(halo);
                        animateOrbit(Role.ORBIT, orbit, 0.09);
                        animateStar();
                    }
                    if (age % 6 == 0) {
                        int sx = random.nextBoolean() ? 1 : -1;
                        int sz = random.nextBoolean() ? 1 : -1;
                        world.spawnParticle(Particle.END_ROD, at(sx * 1.02, 1.95, sz * 1.02), 1, 0.02, 0.05, 0.02, 0.01);
                    }
                    if (age % 8 == 0) {
                        world.spawnParticle(Particle.REVERSE_PORTAL, at(0, 0.25, 0), 4, 1.0, 0.05, 1.0, 0.01);
                        double h = spinnerHeight(tier) + 1.2 + random.nextDouble(11.0);
                        world.spawnParticle(Particle.DUST, at(0, h, 0), 1, 0.1, 0.2, 0.1, 0, new Particle.DustOptions(PEARL, 1.0f));
                    }
                    if (age % 10 == 0) {
                        ringParticles(world, at(0, 0.17, 0), 1.55, ROYAL, 0.7f, 1.0);
                    }
                    if (age % 120 == 0) {
                        world.playSound(at(0, 1, 0), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.9f);
                    }
                }
            }
        }

        private void animateOrbit(Role role, float angle, double bob) {
            for (Part part : parts) {
                if (part.role != role || !part.shown || age < part.appearAt + part.appearTicks) {
                    continue;
                }
                Display display = part.resolve();
                if (display == null) {
                    continue;
                }
                display.setRotation(wrap(yaw + angle), 0f);
                Vector3f t = new Vector3f(part.home.getTranslation()).add(0f, (float) (Math.sin(age * 0.12 + part.phase) * bob), 0f);
                ease(display, new Transformation(t, part.home.getLeftRotation(), part.home.getScale(), part.home.getRightRotation()), 2);
            }
        }

        private void animateHalo(float angle) {
            for (Part part : parts) {
                if (part.role != Role.HALO || !part.shown || age < part.appearAt + part.appearTicks) {
                    continue;
                }
                Display display = part.resolve();
                if (display != null) {
                    display.setRotation(wrap(yaw + angle), 0f);
                }
            }
        }

        private void animateStar() {
            for (Part part : parts) {
                if (part.role != Role.STAR || !part.shown || age < part.appearAt + part.appearTicks) {
                    continue;
                }
                Display display = part.resolve();
                if (display == null) {
                    continue;
                }
                display.setRotation(wrap(yaw - halo * 1.6f), 0f);
                Vector3f t = new Vector3f(0f, (float) (Math.sin(age * 0.08) * 0.06), 0f);
                ease(display, new Transformation(t, part.home.getLeftRotation(), part.home.getScale(), part.home.getRightRotation()), 2);
            }
        }

        // --- spent

        private void becomeSpent(World world) {
            spent = true;
            Location seam = at(0, seamY, 0);
            world.playSound(seam, Sound.BLOCK_VAULT_DEACTIVATE, 0.9f, tier == ChestTier.LEGENDARY ? 0.7f : 1.0f);
            world.spawnParticle(Particle.SMOKE, seam, 12, halfWidth * 0.6, 0.05, halfWidth * 0.5, 0.01);
            Material dark = tier == ChestTier.LEGENDARY ? Material.GRAY_CONCRETE : Material.COAL_BLOCK;
            Iterator<Part> it = parts.iterator();
            while (it.hasNext()) {
                Part part = it.next();
                Display display = part.resolve();
                switch (part.role) {
                    case GLOW -> {
                        if (display instanceof BlockDisplay block && part.material != Material.END_ROD) {
                            block.setBlock(dark.createBlockData());
                            block.setBrightness(null);
                        }
                    }
                    case BEAM, STAR -> {
                        if (display != null) {
                            display.remove();
                        }
                        it.remove();
                    }
                    case ORBIT -> {
                        if (display != null) {
                            ease(display, pose(part), 12);
                            display.setBrightness(null);
                        }
                    }
                    default -> {
                    }
                }
            }
            if (tier != ChestTier.STANDARD) {
                world.spawnParticle(Particle.SMOKE, at(0, spinnerHeight(tier) + 1.0, 0), 10, 0.1, 0.8, 0.1, 0.01);
            }
        }

        // --- temporary reveal fx (never persistent)

        private void flare(World world, boolean myth) {
            Location core = at(0, spinnerHeight(tier), 0);
            world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, core, myth ? 60 : 24, 0.3, 0.4, 0.3, myth ? 0.5 : 0.3);
            world.playSound(core, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, myth ? 0.8f : 1.3f);
            if (!myth) {
                return;
            }
            world.playSound(core, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);
            world.playSound(core, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            float from = (float) spinnerHeight(tier) + 0.4f;
            Temp gold = temp(world, Material.YELLOW_STAINED_GLASS, column(0.7f, 0.001f, from), 40);
            gold.sky = false;
            gold.then(column(0.7f, 26f, from), 5);
        }

        private Temp temp(World world, Material material, Transformation start, int ttl) {
            Location at = new Location(world, x + 0.5, y, z + 0.5);
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setTransformation(start);
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setShadowRadius(0f);
                spawned.setViewRange(3.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.addScoreboardTag(PROP_TAG);
            });
            Temp temp = new Temp(display, ttl);
            temps.add(temp);
            return temp;
        }

        private void tickTemps() {
            Iterator<Temp> it = temps.iterator();
            while (it.hasNext()) {
                Temp temp = it.next();
                temp.ttl--;
                if (temp.pending != null) {
                    // Applied a tick later so the client has seen the start pose before interpolating.
                    ease(temp.display, temp.pending, temp.pendingTicks);
                    temp.pending = null;
                }
                if (temp.ttl <= 0 || temp.display == null || !temp.display.isValid()) {
                    if (temp.display != null && temp.display.isValid()) {
                        temp.display.remove();
                    }
                    it.remove();
                }
            }
        }
    }

    private static Transformation column(float width, float height, float from) {
        return new Transformation(new Vector3f(-width / 2f, from, -width / 2f), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf());
    }

    /** A column whose top stays 30 blocks up, so growing its height reads as a beam slamming down. */
    private static Transformation skyColumn(float width, float height) {
        return column(width, height, 30f - height);
    }

    // ------------------------------------------------------------------ part

    private static final class Part {
        private final Role role;
        private final Material material;
        private final ItemStack item;
        private final Transformation home;
        private final double lift;
        private UUID id;
        private Display cached;
        private Transformation hidden;
        private int appearAt;
        private int appearTicks = 6;
        private boolean shown;
        private boolean glow;
        private float phase;

        private Part(Role role, Material material, ItemStack item, Transformation home, double lift) {
            this.role = role;
            this.material = material;
            this.item = item;
            this.home = home;
            this.lift = lift;
            this.hidden = shrink(home, 0.001, 0, 0, 0);
        }

        private Display resolve() {
            if (cached != null && cached.isValid()) {
                return cached;
            }
            if (id == null) {
                return null;
            }
            Entity entity = Bukkit.getEntity(id);
            cached = entity instanceof Display display ? display : null;
            return cached;
        }

        /** Hidden pose = home shrunk around its center by {@code scale} and moved by (dx, dy, dz). */
        private void appear(int at, int ticks, double dx, double dy, double dz, double scale) {
            appearAt = at;
            appearTicks = Math.max(1, ticks);
            hidden = shrink(home, Math.max(0.001, scale), dx, dy, dz);
        }

        /** Starts far out along its own radial direction, then flies to its orbit slot. */
        private void flyIn(int at, int ticks, double distance, double up) {
            Vector3f t = home.getTranslation();
            double len = Math.sqrt(t.x * t.x + t.z * t.z);
            double ox = len < 1.0E-3 ? 0 : t.x / len * distance;
            double oz = len < 1.0E-3 ? 0 : t.z / len * distance;
            appear(at, ticks, ox, up, oz, 0.3);
        }

        /** Grows from its base upward. */
        private void growUp(int at, int ticks) {
            appearAt = at;
            appearTicks = Math.max(1, ticks);
            Vector3f s = home.getScale();
            hidden = new Transformation(new Vector3f(home.getTranslation()), home.getLeftRotation(),
                    new Vector3f(s.x, 0.001f, s.z), home.getRightRotation());
        }
    }

    private static final class Temp {
        private final BlockDisplay display;
        private int ttl;
        private boolean sky = true;
        private Transformation pending;
        private int pendingTicks;

        private Temp(BlockDisplay display, int ttl) {
            this.display = display;
            this.ttl = ttl;
        }

        private void then(Transformation next, int ticks) {
            pending = next;
            pendingTicks = Math.max(1, ticks);
        }
    }

    // ------------------------------------------------------------------ math & fx helpers

    private static Transformation shrink(Transformation home, double factor, double dx, double dy, double dz) {
        Quaternionf left = home.getLeftRotation();
        Vector3f scale = home.getScale();
        Vector3f center = new Vector3f(home.getTranslation()).add(new Quaternionf(left).transform(new Vector3f(scale).mul(0.5f)));
        Vector3f small = new Vector3f(scale).mul((float) factor);
        Vector3f t = center.sub(new Quaternionf(left).transform(new Vector3f(small).mul(0.5f)))
                .add((float) dx, (float) dy, (float) dz);
        return new Transformation(t, new Quaternionf(left), small, new Quaternionf(home.getRightRotation()));
    }

    /** Rotates a part about the lid hinge (local X axis). Negative angles open the lid backwards. */
    private static Transformation hinged(Transformation home, Vector3f hinge, double angle) {
        Quaternionf r = new Quaternionf().rotateX((float) angle);
        Vector3f rel = new Vector3f(home.getTranslation()).sub(hinge);
        Vector3f t = r.transform(rel).add(hinge);
        Quaternionf left = new Quaternionf(r).mul(home.getLeftRotation());
        return new Transformation(t, left, new Vector3f(home.getScale()), new Quaternionf(home.getRightRotation()));
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static void ringParticles(World world, Location center, double radius, Color color, float size, double fill) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(12, (int) Math.ceil(Math.PI * 2 * radius / 0.3));
        int drawn = (int) Math.ceil(points * Math.max(0.0, Math.min(1.0, fill)));
        for (int i = 0; i < drawn; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust);
        }
    }

    private static void ringParticlesBlock(World world, Location center, double radius, BlockData data, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points + ThreadLocalRandom.current().nextDouble(0.3);
            world.spawnParticle(Particle.BLOCK, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 2, 0.1, 0.02, 0.1, 0, data);
        }
    }

    private static String key(World world, int x, int y, int z) {
        return world.getUID() + ":" + coords(x, y, z);
    }

    private static String coords(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
