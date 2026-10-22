package de.aetherion.bossengine.fx;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.rite.RiteKit;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import de.aetherion.core.api.Vfx;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat spectacle per boss. Sparky keeps magma/fireball identity.
 * Nobody else borrows his crater / suck kit. The four rite bosses are drawn by their own
 * {@link RiteKit}s and only routed through here.
 */
public final class CombatTheatrics {

    private CombatTheatrics() {
    }

    public static String id(BossInstance instance) {
        if (instance == null || instance.getTemplate() == null || instance.getTemplate().getId() == null) {
            return "";
        }
        return instance.getTemplate().getId().toLowerCase(Locale.ROOT);
    }

    public static void slam(BossInstance instance, Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        RiteKit kit = RiteKit.of(instance);
        if (kit != null) {
            kit.slam(at, 3.0);
            return;
        }
        switch (id(instance)) {
            case "sparky" -> slamSparky(at);
            case "aether_colossus" -> slamColossus(at);
            case "squidward" -> slamSquid(at);
            case "sir_balthazar" -> slamBalthazar(at);
            case "baron_von_wurm" -> slamBaron(at);
            case "insolvent_wither" -> slamWither(at);
            case "lobby_cleaner" -> slamCleaner(at);
            case "dungeon_sentinel" -> slamSentinel(at);
            case "dungeon_frostbound" -> slamFrost(at);
            case "aetherion", "dungeon_aetherion" -> slamDragon(at);
            default -> slamDefault(at);
        }
    }

    public static void ringAnnounce(BossInstance instance, Location at) {
        World world = at == null ? null : at.getWorld();
        if (world == null || RiteKit.owns(instance)) {
            return;
        }
        switch (id(instance)) {
            case "sparky" -> world.playSound(at, Sound.ENTITY_GHAST_SCREAM, 0.7f, 0.55f);
            case "aether_colossus" -> world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
            case "squidward" -> world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
            case "sir_balthazar" -> world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.1f, 0.8f);
            case "baron_von_wurm" -> world.playSound(at, Sound.ENTITY_SILVERFISH_AMBIENT, 1.2f, 0.4f);
            case "insolvent_wither" -> world.playSound(at, Sound.ENTITY_WITHER_AMBIENT, 0.95f, 0.7f);
            case "lobby_cleaner" -> world.playSound(at, Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.45f);
            case "dungeon_sentinel" -> world.playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 0.55f);
            case "dungeon_frostbound" -> world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.1f, 0.45f);
            default -> world.playSound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 0.6f);
        }
    }

    public static void ringClimax(BossInstance instance, Location at, double radius) {
        if (at == null || at.getWorld() == null || RiteKit.owns(instance)) {
            return;
        }
        switch (id(instance)) {
            case "sparky" -> ringSparky(at, radius);
            case "aether_colossus" -> ringColossus(at, radius);
            case "squidward" -> ringSquid(at, radius);
            case "sir_balthazar" -> ringBalthazar(at, radius);
            case "baron_von_wurm" -> ringBaron(at, radius);
            case "insolvent_wither" -> ringWither(at, radius);
            case "lobby_cleaner" -> ringCleaner(at, radius);
            case "dungeon_sentinel" -> ringSentinel(at, radius);
            case "dungeon_frostbound" -> ringFrost(at, radius);
            default -> ringDefault(at, radius);
        }
    }

    public static void meteor(BossInstance instance, Location ground) {
        if (ground == null || ground.getWorld() == null) {
            return;
        }
        RiteKit kit = RiteKit.of(instance);
        if (kit != null) {
            kit.meteor(ground);
            return;
        }
        switch (id(instance)) {
            case "sparky" -> meteorSparky(ground);
            case "aether_colossus" -> meteorColossus(ground);
            case "squidward" -> meteorSquid(ground);
            case "sir_balthazar" -> meteorBalthazar(ground);
            case "baron_von_wurm" -> meteorBaron(ground);
            case "insolvent_wither" -> meteorWither(ground);
            case "lobby_cleaner" -> meteorCleaner(ground);
            case "dungeon_sentinel" -> meteorSentinel(ground);
            case "dungeon_frostbound" -> meteorFrost(ground);
            default -> meteorDefault(ground);
        }
    }

    /** Phase-change opener for the rite bosses; false = caller keeps its default sting. */
    public static boolean transitionStart(BossInstance instance, Location at) {
        RiteKit kit = RiteKit.of(instance);
        if (kit == null || at == null || at.getWorld() == null) {
            return false;
        }
        kit.transitionStart(at);
        return true;
    }

    public static void transitionPulse(BossInstance instance, Location at, int tick, int duration) {
        RiteKit kit = RiteKit.of(instance);
        if (kit != null && at != null && at.getWorld() != null) {
            kit.transitionTick(at, tick, duration);
        }
    }

    public static void transitionEnd(BossInstance instance, Location at) {
        RiteKit kit = RiteKit.of(instance);
        if (kit != null && at != null && at.getWorld() != null) {
            kit.transitionEnd(at);
        }
    }

    public static boolean meteorBurns(BossInstance instance) {
        return switch (id(instance)) {
            case "sparky", "skuldugery" -> true;
            default -> false;
        };
    }

    private static void slamSparky(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 0.7f);
        world.playSound(at, Sound.ENTITY_GHAST_SHOOT, 0.7f, 0.55f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.3, 0), Particle.EXPLOSION, 2, Math.max(0.35, 0.35), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.FLAME, 28, Math.max(0.9, 0.9), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.LAVA, 10, Math.max(0.55, 0.55), null);
        FakeDestruction.blockBurst(world, at, Material.MAGMA_BLOCK, 14);
    }

    private static void slamColossus(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_RAVAGER_ATTACK, 1.2f, 0.55f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.5f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.4, 0), Particle.PORTAL, 40, Math.max(0.9, 0.9), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.ENCHANT, 18, Math.max(0.8, 0.8), null);
        FakeDestruction.blockBurst(world, at, Material.AMETHYST_BLOCK, 16);
        pillars(world, at, Particle.REVERSE_PORTAL, 4, 3.2, 4.5);
    }

    private static void slamSquid(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SQUID_SQUIRT, 1.2f, 0.55f);
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_FLOP, 0.6f, 0.7f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.4, 0), Particle.SQUID_INK, 30, Math.max(0.7, 0.7), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.BUBBLE, 20, Math.max(0.8, 0.8), null);
        ring(world, at, 3.0, Particle.BUBBLE_COLUMN_UP, 0.1);
    }

    private static void slamBalthazar(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1.1f, 0.75f);
        world.playSound(at, Sound.ITEM_TOTEM_USE, 0.35f, 1.4f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.WITCH, 22, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.6, 0), Particle.ENCHANT, 24, Math.max(0.7, 0.7), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.TOTEM_OF_UNDYING, 10, Math.max(0.4, 0.4), null);
    }

    private static void slamBaron(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.5f);
        world.playSound(at, Sound.ENTITY_SILVERFISH_HURT, 0.9f, 0.45f);
        FakeDestruction.blockBurst(world, at, Material.DIRT, 22);
        FakeDestruction.blockBurst(world, at, Material.COARSE_DIRT, 10);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.MYCELIUM, 16, Math.max(0.8, 0.8), null);
        geyser(world, at, Material.DIRT, 5.5);
    }

    private static void slamWither(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.95f, 0.7f);
        world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.55f, 0.5f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.SMOKE, 22, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.SOUL, 10, Math.max(0.5, 0.5), null);
        itemBurst(world, at, Material.GOLD_NUGGET, 10);
        dust(world, at, Color.fromRGB(40, 40, 40), 14);
    }

    private static void slamCleaner(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.55f);
        world.playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 0.7f);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.PORTAL, 36, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.5, 0), Particle.REVERSE_PORTAL, 14, Math.max(0.4, 0.4), null);
        beams(world, at, 6.0, Particle.END_ROD);
    }

    private static void slamSentinel(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.7f);
        world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 0.9f, 0.6f);
        if (Vfx.wave(at, Color.fromRGB(150, 205, 255), 3.0, 11)) {
            arcs(at, Color.fromRGB(150, 205, 255), 3, 2.6);
        } else {
            de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.ELECTRIC_SPARK, 28, Math.max(0.9, 0.9), null);
            de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.CRIT, 16, Math.max(0.7, 0.7), null);
        }
        FakeDestruction.blockBurst(world, at, Material.IRON_BLOCK, 10);
    }

    private static void slamFrost(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.25f, 0.55f);
        world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 0.7f);
        if (!Vfx.wave(at, Color.fromRGB(190, 236, 255), 3.0, 12)) {
            de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.SNOWFLAKE, 28, Math.max(0.9, 0.9), null);
        }
        FakeDestruction.blockBurst(world, at, Material.PACKED_ICE, 16);
        FakeDestruction.blockBurst(world, at, Material.SNOW_BLOCK, 8);
        spikes(world, at, 6, 2.8, 3.6);
    }

    private static void slamDragon(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.8f);
        if (Vfx.wave(at, Color.fromRGB(176, 92, 255), 3.0, 12)) {
            Vfx.orb(at.clone().add(0, 0.6, 0), null, Color.fromRGB(176, 92, 255), 1.6, 10);
            Vfx.motes(at.clone().add(0, 0.3, 0), Color.fromRGB(176, 92, 255), 5, 0.8, new Vector(0, 1.8, 0), 24);
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.5, 0), Particle.DRAGON_BREATH, 24, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.PORTAL, 20, Math.max(0.7, 0.7), null);
    }

    private static void slamDefault(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
        if (Vfx.wave(at, Color.fromRGB(236, 232, 222), 2.6, 11)) {
            Vfx.burst(at.clone().add(0, 0.5, 0), Color.fromRGB(236, 232, 222), 1.4, 8);
            Vfx.chips(at, at.clone().add(0, -1, 0).getBlock().getBlockData(), 6, 1.0);
            return;
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.CLOUD, 18, Math.max(0.8, 0.8), null);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.CRIT, 12, Math.max(0.6, 0.6), null);
    }

    private static void ringSparky(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.65f);
        ring(world, at, radius, Particle.FLAME, 0.25);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.LAVA, 12, Math.max(0.6, 0.6), null);
    }

    private static void ringColossus(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.6f);
        ring(world, at, radius, Particle.PORTAL, 0.4);
        pillars(world, at, Particle.ENCHANT, 6, radius * 0.55, 5.0);
        FakeDestruction.blockBurst(world, at, Material.CRYING_OBSIDIAN, 8);
    }

    private static void ringSquid(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SQUID_SQUIRT, 0.9f, 0.5f);
        ring(world, at, radius, Particle.SQUID_INK, 0.45);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.2, 0), Particle.BUBBLE_COLUMN_UP, 18, Math.max(0.4, 0.4), null);
    }

    private static void ringBalthazar(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.85f, 0.9f);
        ring(world, at, radius, Particle.WITCH, 0.3);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 1.0, 0), Particle.ENCHANT, 28, Math.max(radius * 0.25, radius * 0.25), null);
    }

    private static void ringBaron(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.45f);
        ring(world, at, radius, Particle.CRIT, 0.15);
        FakeDestruction.blockBurst(world, at, Material.GRAVEL, 14);
        geyser(world, at, Material.COARSE_DIRT, 4.2);
    }

    private static void ringWither(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.7f, 0.55f);
        ring(world, at, radius, Particle.SMOKE, 0.35);
        itemBurst(world, at, Material.GOLD_NUGGET, 12);
        de.aetherion.bossengine.fx.ShapeFx.puff(at, Particle.SOUL, 10, Math.max(0.5, 0.5), null);
    }

    private static void ringCleaner(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_PORTAL_TRIGGER, 0.45f, 1.4f);
        ring(world, at, radius, Particle.REVERSE_PORTAL, 0.4);
        beams(world, at, radius, Particle.PORTAL);
    }

    private static void ringSentinel(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.2f);
        if (Vfx.wave(at, Color.fromRGB(150, 205, 255), radius, 14)) {
            arcs(at, Color.fromRGB(150, 205, 255), 4, radius);
            return;
        }
        ring(world, at, radius, Particle.ELECTRIC_SPARK, 0.25);
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.6, 0), Particle.CRIT, 16, Math.max(0.5, 0.5), null);
    }

    private static void ringFrost(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.05f, 0.7f);
        if (!Vfx.wave(at, Color.fromRGB(190, 236, 255), radius, 14)) {
            ring(world, at, radius, Particle.SNOWFLAKE, 0.3);
        }
        FakeDestruction.blockBurst(world, at, Material.ICE, 12);
        spikes(world, at, 8, radius * 0.45, 3.2);
    }

    private static void ringDefault(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 0.85f);
        if (!Vfx.wave(at, Color.fromRGB(236, 232, 222), radius, 14)) {
            ring(world, at, radius, Particle.CLOUD, 0.2);
        }
    }

    private static void meteorSparky(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.FLAME, Particle.LAVA, 14);
        de.aetherion.bossengine.fx.ShapeFx.puff(ground.clone().add(0, 0.4, 0), Particle.EXPLOSION, 2, Math.max(0.2, 0.2), null);
        world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 0.75f);
        world.playSound(ground, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.55f);
        FakeDestruction.blockBurst(world, ground, Material.MAGMA_BLOCK, 10);
    }

    private static void meteorColossus(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.PORTAL, Particle.ENCHANT, 16);
        world.playSound(ground, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.6f);
        world.playSound(ground, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 0.5f);
        FakeDestruction.blockBurst(world, ground, Material.AMETHYST_BLOCK, 12);
        dust(world, ground, Color.fromRGB(120, 40, 180), 10);
    }

    private static void meteorSquid(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.SQUID_INK, Particle.BUBBLE, 14);
        world.playSound(ground, Sound.ENTITY_SQUID_SQUIRT, 1.0f, 0.6f);
        de.aetherion.bossengine.fx.ShapeFx.puff(ground, Particle.BUBBLE_COLUMN_UP, 16, Math.max(0.3, 0.3), null);
    }

    private static void meteorBalthazar(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.WITCH, Particle.ENCHANT, 16);
        world.playSound(ground, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 0.8f);
        de.aetherion.bossengine.fx.ShapeFx.puff(ground.clone().add(0, 0.4, 0), Particle.TOTEM_OF_UNDYING, 12, Math.max(0.25, 0.25), null);
    }

    private static void meteorBaron(Location ground) {
        World world = ground.getWorld();
        geyser(world, ground, Material.DIRT, 12.0);
        world.playSound(ground, Sound.BLOCK_GRAVEL_BREAK, 1.15f, 0.4f);
        FakeDestruction.blockBurst(world, ground, Material.DIRT, 18);
        FakeDestruction.blockBurst(world, ground, Material.GRAVEL, 8);
    }

    private static void meteorWither(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.SMOKE, Particle.SOUL, 16);
        world.playSound(ground, Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.55f);
        itemBurst(world, ground, Material.GOLD_NUGGET, 10);
        dust(world, ground, Color.fromRGB(20, 20, 20), 12);
    }

    private static void meteorCleaner(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.PORTAL, Particle.REVERSE_PORTAL, 16);
        world.playSound(ground, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.6f);
        de.aetherion.bossengine.fx.ShapeFx.puff(ground.clone().add(0, 0.4, 0), Particle.END_ROD, 10, Math.max(0.25, 0.25), null);
    }

    private static void meteorSentinel(Location ground) {
        if (fall(ground, Color.fromRGB(150, 205, 255))) {
            arcs(ground, Color.fromRGB(150, 205, 255), 2, 2.0);
        }
        World world = ground.getWorld();
        if (!Vfx.active()) column(world, ground, Particle.ELECTRIC_SPARK, Particle.CRIT, 14);
        world.playSound(ground, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.7f);
        FakeDestruction.blockBurst(world, ground, Material.IRON_BLOCK, 8);
    }

    private static void meteorFrost(Location ground) {
        if (fall(ground, Color.fromRGB(190, 236, 255))) {
            Vfx.spark(ground.clone().add(0, 0.6, 0), Color.fromRGB(190, 236, 255), 1.2);
        }
        World world = ground.getWorld();
        if (!Vfx.active()) column(world, ground, Particle.SNOWFLAKE, Particle.CLOUD, 14);
        world.playSound(ground, Sound.BLOCK_GLASS_BREAK, 1.1f, 0.5f);
        FakeDestruction.blockBurst(world, ground, Material.PACKED_ICE, 12);
        spikes(world, ground, 5, 1.8, 4.0);
    }

    private static void meteorDefault(Location ground) {
        if (fall(ground, Color.fromRGB(236, 232, 222))) {
            Vfx.chips(ground, ground.clone().add(0, -1, 0).getBlock().getBlockData(), 6, 1.0);
        }
        World world = ground.getWorld();
        if (!Vfx.active()) column(world, ground, Particle.CLOUD, Particle.CRIT, 12);
        world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.9f);
    }

    /** Short lightning arcs from the centre out to the rim. */
    private static void arcs(Location at, Color color, int count, double radius) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location from = at.clone().add(0, 0.4, 0);
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble(Math.PI * 2);
            Vfx.bolt(from, at.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), color, 6);
        }
    }

    /**
     * The meteor itself: a comet that falls out of the sky onto {@code ground} and lands when the burst does
     * ({@link de.aetherion.bossengine.skill.t2.T2Mechanics#BURST_TELEGRAPH_TICKS}), over a ground warning.
     */
    private static boolean fall(Location ground, Color color) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int ticks = de.aetherion.bossengine.skill.t2.T2Mechanics.BURST_TELEGRAPH_TICKS;
        Location sky = ground.clone().add(rng.nextDouble(-6.0, 6.0), 18.0 + rng.nextDouble(4.0), rng.nextDouble(-6.0, 6.0));
        if (!Vfx.streak(sky, ground.clone().add(0, 0.3, 0), color, 1.1, ticks)) {
            return false;
        }
        Vfx.telegraph(ground, 3.2, ticks, color);
        return true;
    }

    private static void ring(World world, Location at, double radius, Particle particle, double y) {
        if (ShapeFx.ring(at.clone().add(0, y, 0), particle, radius)) {
            return;
        }
        int points = Math.max(18, (int) (radius * 7));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Location rim = at.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            de.aetherion.bossengine.fx.ShapeFx.puff(rim, particle, 1, Math.max(0, 0), null);
        }
    }

    private static void column(World world, Location ground, Particle primary, Particle extra, int height) {
        for (int y = height; y >= 0; y -= 2) {
            Location at = ground.clone().add(0, y, 0);
            de.aetherion.bossengine.fx.ShapeFx.puff(at, primary, 8, Math.max(0.12, 0.12), null);
            de.aetherion.bossengine.fx.ShapeFx.puff(at, extra, 2, Math.max(0.08, 0.08), null);
        }
    }

    private static void pillars(World world, Location at, Particle particle, int count, double radius, double height) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            for (double y = 0; y < height; y += 0.45) {
                de.aetherion.bossengine.fx.ShapeFx.puff(base.clone().add(0, y, 0), particle, 2, Math.max(0.05, 0.05), null);
            }
        }
    }

    private static void spikes(World world, Location at, int count, double radius, double height) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            for (double y = 0; y < height; y += 0.35) {
                de.aetherion.bossengine.fx.ShapeFx.puff(base.clone().add(0, y, 0), Particle.BLOCK, 3, Math.max(0.04, 0.04), Material.PACKED_ICE.createBlockData());
                de.aetherion.bossengine.fx.ShapeFx.puff(base.clone().add(0, y, 0), Particle.SNOWFLAKE, 1, Math.max(0, 0), null);
            }
        }
    }

    private static void geyser(World world, Location at, Material material, double height) {
        for (double y = 0; y < height; y += 0.4) {
            de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, y, 0), Particle.BLOCK, 6, Math.max(0.12, 0.12), material.createBlockData());
        }
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.2, 0), Particle.CLOUD, 8, Math.max(0.25, 0.25), null);
    }

    private static void beams(World world, Location at, double length, Particle particle) {
        Vector[] dirs = {
                new Vector(1, 0, 0),
                new Vector(-1, 0, 0),
                new Vector(0, 0, 1),
                new Vector(0, 0, -1)
        };
        for (Vector dir : dirs) {
            for (double d = 0.4; d <= length; d += 0.45) {
                de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(dir.clone().multiply(d)).add(0, 1.1, 0), particle, 1, Math.max(0, 0), null);
            }
        }
    }

    private static void itemBurst(World world, Location at, Material material, int count) {
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.4, 0), Particle.ITEM, count, Math.max(0.45, 0.45), new ItemStack(material));
    }

    private static void dust(World world, Location at, Color color, int count) {
        de.aetherion.bossengine.fx.ShapeFx.puff(at.clone().add(0, 0.35, 0), Particle.DUST, count, Math.max(0.5, 0.5), new Particle.DustOptions(color, 1.35f));
    }
}
