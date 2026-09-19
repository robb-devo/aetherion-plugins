package de.aetherion.bossengine.fx;

import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat spectacle per boss. Sparky keeps magma/fireball identity.
 * Nobody else borrows his crater / suck kit.
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
        switch (id(instance)) {
            case "sparky" -> slamSparky(at);
            case "aether_colossus" -> slamColossus(at);
            case "hollow_lurker" -> slamLurker(at);
            case "skuldugery" -> slamSkull(at);
            case "mcnugget" -> slamNugget(at);
            case "bridge_troll" -> slamTroll(at);
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
        if (world == null) {
            return;
        }
        switch (id(instance)) {
            case "sparky" -> world.playSound(at, Sound.ENTITY_GHAST_SCREAM, 0.7f, 0.55f);
            case "aether_colossus" -> world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
            case "hollow_lurker" -> world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.7f);
            case "skuldugery" -> world.playSound(at, Sound.ENTITY_BLAZE_AMBIENT, 1.1f, 0.55f);
            case "mcnugget" -> world.playSound(at, Sound.ENTITY_CHICKEN_HURT, 1.2f, 0.45f);
            case "bridge_troll" -> world.playSound(at, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.15f, 0.55f);
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
        if (at == null || at.getWorld() == null) {
            return;
        }
        switch (id(instance)) {
            case "sparky" -> ringSparky(at, radius);
            case "aether_colossus" -> ringColossus(at, radius);
            case "hollow_lurker" -> ringLurker(at, radius);
            case "skuldugery" -> ringSkull(at, radius);
            case "mcnugget" -> ringNugget(at, radius);
            case "bridge_troll" -> ringTroll(at, radius);
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
        switch (id(instance)) {
            case "sparky" -> meteorSparky(ground);
            case "aether_colossus" -> meteorColossus(ground);
            case "hollow_lurker" -> meteorLurker(ground);
            case "skuldugery" -> meteorSkull(ground);
            case "mcnugget" -> meteorNugget(ground);
            case "bridge_troll" -> meteorTroll(ground);
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
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.3, 0), 2, 0.35, 0.1, 0.35, 0);
        world.spawnParticle(Particle.FLAME, at, 28, 0.9, 0.2, 0.9, 0.04);
        world.spawnParticle(Particle.LAVA, at, 10, 0.55, 0.1, 0.55, 0);
        FakeDestruction.blockBurst(world, at, Material.MAGMA_BLOCK, 14);
    }

    private static void slamColossus(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_RAVAGER_ATTACK, 1.2f, 0.55f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.5f);
        world.spawnParticle(Particle.PORTAL, at.clone().add(0, 0.4, 0), 40, 0.9, 0.8, 0.9, 0.55);
        world.spawnParticle(Particle.ENCHANT, at, 18, 0.8, 0.5, 0.8, 0.6);
        FakeDestruction.blockBurst(world, at, Material.AMETHYST_BLOCK, 16);
        pillars(world, at, Particle.REVERSE_PORTAL, 4, 3.2, 4.5);
    }

    private static void slamLurker(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.55f);
        world.playSound(at, Sound.BLOCK_SCULK_BREAK, 1.1f, 0.6f);
        world.spawnParticle(Particle.SOUL, at, 22, 0.8, 0.15, 0.8, 0.02);
        world.spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 0.2, 0), 14, 0.7, 0.1, 0.7, 0.01);
        FakeDestruction.blockBurst(world, at, Material.SCULK, 12);
        ring(world, at, 3.2, Particle.SOUL_FIRE_FLAME, 0.15);
    }

    private static void slamSkull(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SKELETON_DEATH, 1.0f, 0.55f);
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.6f);
        world.spawnParticle(Particle.FLAME, at, 24, 0.8, 0.25, 0.8, 0.03);
        world.spawnParticle(Particle.LAVA, at, 6, 0.4, 0.1, 0.4, 0);
        itemBurst(world, at, Material.BONE, 16);
        ring(world, at, 2.8, Particle.FLAME, 0.2);
    }

    private static void slamNugget(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_CHICKEN_HURT, 1.15f, 0.7f);
        world.playSound(at, Sound.ENTITY_CHICKEN_EGG, 0.9f, 0.8f);
        world.spawnParticle(Particle.CLOUD, at, 18, 0.8, 0.25, 0.8, 0.04);
        itemBurst(world, at, Material.FEATHER, 18);
        itemBurst(world, at, Material.EGG, 6);
    }

    private static void slamTroll(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 1.35f, 0.45f);
        world.playSound(at, Sound.ENTITY_PIGLIN_BRUTE_HURT, 0.8f, 0.6f);
        world.spawnParticle(Particle.GUST, at.clone().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 20, 0.9, 0.4, 0.9, 0.04);
        itemBurst(world, at, Material.GOLD_NUGGET, 14);
        FakeDestruction.blockBurst(world, at, Material.GOLD_BLOCK, 8);
    }

    private static void slamSquid(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SQUID_SQUIRT, 1.2f, 0.55f);
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_FLOP, 0.6f, 0.7f);
        world.spawnParticle(Particle.SQUID_INK, at.clone().add(0, 0.4, 0), 30, 0.7, 0.4, 0.7, 0.04);
        world.spawnParticle(Particle.BUBBLE, at, 20, 0.8, 0.5, 0.8, 0.3);
        ring(world, at, 3.0, Particle.BUBBLE_COLUMN_UP, 0.1);
    }

    private static void slamBalthazar(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1.1f, 0.75f);
        world.playSound(at, Sound.ITEM_TOTEM_USE, 0.35f, 1.4f);
        world.spawnParticle(Particle.WITCH, at, 22, 0.8, 0.5, 0.8, 0.02);
        world.spawnParticle(Particle.ENCHANT, at.clone().add(0, 0.6, 0), 24, 0.7, 0.6, 0.7, 0.7);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 10, 0.4, 0.5, 0.4, 0.15);
    }

    private static void slamBaron(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.5f);
        world.playSound(at, Sound.ENTITY_SILVERFISH_HURT, 0.9f, 0.45f);
        FakeDestruction.blockBurst(world, at, Material.DIRT, 22);
        FakeDestruction.blockBurst(world, at, Material.COARSE_DIRT, 10);
        world.spawnParticle(Particle.MYCELIUM, at, 16, 0.8, 0.15, 0.8, 0);
        geyser(world, at, Material.DIRT, 5.5);
    }

    private static void slamWither(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.95f, 0.7f);
        world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.55f, 0.5f);
        world.spawnParticle(Particle.SMOKE, at, 22, 0.8, 0.4, 0.8, 0.02);
        world.spawnParticle(Particle.SOUL, at, 10, 0.5, 0.3, 0.5, 0.02);
        itemBurst(world, at, Material.GOLD_NUGGET, 10);
        dust(world, at, Color.fromRGB(40, 40, 40), 14);
    }

    private static void slamCleaner(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.55f);
        world.playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 0.7f);
        world.spawnParticle(Particle.PORTAL, at, 36, 0.8, 0.7, 0.8, 0.6);
        world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 0.5, 0), 14, 0.4, 0.4, 0.4, 0.15);
        beams(world, at, 6.0, Particle.END_ROD);
    }

    private static void slamSentinel(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.7f);
        world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 0.9f, 0.6f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 28, 0.9, 0.5, 0.9, 0.05);
        world.spawnParticle(Particle.CRIT, at, 16, 0.7, 0.3, 0.7, 0.08);
        FakeDestruction.blockBurst(world, at, Material.IRON_BLOCK, 10);
    }

    private static void slamFrost(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.25f, 0.55f);
        world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 0.7f);
        world.spawnParticle(Particle.SNOWFLAKE, at, 28, 0.9, 0.4, 0.9, 0.02);
        FakeDestruction.blockBurst(world, at, Material.PACKED_ICE, 16);
        FakeDestruction.blockBurst(world, at, Material.SNOW_BLOCK, 8);
        spikes(world, at, 6, 2.8, 3.6);
    }

    private static void slamDragon(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.8f);
        world.spawnParticle(Particle.DRAGON_BREATH, at.clone().add(0, 0.5, 0), 24, 0.8, 0.4, 0.8, 0.04);
        world.spawnParticle(Particle.PORTAL, at, 20, 0.7, 0.5, 0.7, 0.4);
    }

    private static void slamDefault(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
        world.spawnParticle(Particle.CLOUD, at, 18, 0.8, 0.15, 0.8, 0.03);
        world.spawnParticle(Particle.CRIT, at, 12, 0.6, 0.2, 0.6, 0.06);
    }

    private static void ringSparky(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.65f);
        ring(world, at, radius, Particle.FLAME, 0.25);
        world.spawnParticle(Particle.LAVA, at, 12, 0.6, 0.2, 0.6, 0);
    }

    private static void ringColossus(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.6f);
        ring(world, at, radius, Particle.PORTAL, 0.4);
        pillars(world, at, Particle.ENCHANT, 6, radius * 0.55, 5.0);
        FakeDestruction.blockBurst(world, at, Material.CRYING_OBSIDIAN, 8);
    }

    private static void ringLurker(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.45f);
        ring(world, at, radius, Particle.SCULK_SOUL, 0.2);
        world.spawnParticle(Particle.SOUL, at, 16, 0.5, 0.2, 0.5, 0.02);
    }

    private static void ringSkull(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
        ring(world, at, radius, Particle.FLAME, 0.35);
        itemBurst(world, at, Material.ARROW, 10);
    }

    private static void ringNugget(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.7f);
        ring(world, at, radius, Particle.CLOUD, 0.3);
        itemBurst(world, at, Material.FEATHER, 16);
    }

    private static void ringTroll(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.9f, 0.55f);
        ring(world, at, radius, Particle.ELECTRIC_SPARK, 0.25);
        itemBurst(world, at, Material.GOLD_INGOT, 8);
    }

    private static void ringSquid(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SQUID_SQUIRT, 0.9f, 0.5f);
        ring(world, at, radius, Particle.SQUID_INK, 0.45);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, at.clone().add(0, 0.2, 0), 18, 0.4, 0.4, 0.4, 0.4);
    }

    private static void ringBalthazar(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.85f, 0.9f);
        ring(world, at, radius, Particle.WITCH, 0.3);
        world.spawnParticle(Particle.ENCHANT, at.clone().add(0, 1.0, 0), 28, radius * 0.25, 0.8, radius * 0.25, 0.8);
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
        world.spawnParticle(Particle.SOUL, at, 10, 0.5, 0.3, 0.5, 0.02);
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
        ring(world, at, radius, Particle.ELECTRIC_SPARK, 0.25);
        world.spawnParticle(Particle.CRIT, at.clone().add(0, 0.6, 0), 16, 0.5, 0.4, 0.5, 0.08);
    }

    private static void ringFrost(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.05f, 0.7f);
        ring(world, at, radius, Particle.SNOWFLAKE, 0.3);
        FakeDestruction.blockBurst(world, at, Material.ICE, 12);
        spikes(world, at, 8, radius * 0.45, 3.2);
    }

    private static void ringDefault(Location at, double radius) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 0.85f);
        ring(world, at, radius, Particle.CLOUD, 0.2);
    }

    private static void meteorSparky(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.FLAME, Particle.LAVA, 14);
        world.spawnParticle(Particle.EXPLOSION, ground.clone().add(0, 0.4, 0), 2, 0.2, 0.1, 0.2, 0);
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

    private static void meteorLurker(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.SOUL, Particle.SCULK_SOUL, 14);
        world.playSound(ground, Sound.ENTITY_WARDEN_NEARBY_CLOSE, 0.55f, 0.7f);
        FakeDestruction.blockBurst(world, ground, Material.SCULK, 10);
    }

    private static void meteorSkull(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.FLAME, Particle.LAVA, 14);
        world.playSound(ground, Sound.ENTITY_SKELETON_SHOOT, 0.9f, 0.5f);
        itemBurst(world, ground, Material.ARROW, 8);
        FakeDestruction.blockBurst(world, ground, Material.BONE_BLOCK, 8);
    }

    private static void meteorNugget(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.CLOUD, Particle.CRIT, 12);
        world.playSound(ground, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.6f);
        itemBurst(world, ground, Material.FEATHER, 14);
        itemBurst(world, ground, Material.EGG, 4);
    }

    private static void meteorTroll(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.ELECTRIC_SPARK, Particle.CRIT, 12);
        world.playSound(ground, Sound.BLOCK_ANVIL_LAND, 0.85f, 0.7f);
        itemBurst(world, ground, Material.GOLD_NUGGET, 12);
    }

    private static void meteorSquid(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.SQUID_INK, Particle.BUBBLE, 14);
        world.playSound(ground, Sound.ENTITY_SQUID_SQUIRT, 1.0f, 0.6f);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, ground, 16, 0.3, 0.8, 0.3, 0.4);
    }

    private static void meteorBalthazar(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.WITCH, Particle.ENCHANT, 16);
        world.playSound(ground, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 0.8f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, ground.clone().add(0, 0.4, 0), 12, 0.25, 0.4, 0.25, 0.2);
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
        world.spawnParticle(Particle.END_ROD, ground.clone().add(0, 0.4, 0), 10, 0.25, 0.4, 0.25, 0.02);
    }

    private static void meteorSentinel(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.ELECTRIC_SPARK, Particle.CRIT, 14);
        world.playSound(ground, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.7f);
        FakeDestruction.blockBurst(world, ground, Material.IRON_BLOCK, 8);
    }

    private static void meteorFrost(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.SNOWFLAKE, Particle.CLOUD, 14);
        world.playSound(ground, Sound.BLOCK_GLASS_BREAK, 1.1f, 0.5f);
        FakeDestruction.blockBurst(world, ground, Material.PACKED_ICE, 12);
        spikes(world, ground, 5, 1.8, 4.0);
    }

    private static void meteorDefault(Location ground) {
        World world = ground.getWorld();
        column(world, ground, Particle.CLOUD, Particle.CRIT, 12);
        world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.9f);
    }

    private static void ring(World world, Location at, double radius, Particle particle, double y) {
        int points = Math.max(18, (int) (radius * 7));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Location rim = at.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(particle, rim, 1, 0, 0, 0, 0);
        }
    }

    private static void column(World world, Location ground, Particle primary, Particle extra, int height) {
        for (int y = height; y >= 0; y -= 2) {
            Location at = ground.clone().add(0, y, 0);
            world.spawnParticle(primary, at, 8, 0.12, 0.25, 0.12, 0.02);
            world.spawnParticle(extra, at, 2, 0.08, 0.1, 0.08, 0.01);
        }
    }

    private static void pillars(World world, Location at, Particle particle, int count, double radius, double height) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            for (double y = 0; y < height; y += 0.45) {
                world.spawnParticle(particle, base.clone().add(0, y, 0), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    private static void spikes(World world, Location at, int count, double radius, double height) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location base = at.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            for (double y = 0; y < height; y += 0.35) {
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, y, 0), 3, 0.04, 0.08, 0.04, 0.02,
                        Material.PACKED_ICE.createBlockData());
                world.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, y, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void geyser(World world, Location at, Material material, double height) {
        for (double y = 0; y < height; y += 0.4) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, y, 0), 6, 0.12, 0.08, 0.12, 0.08, material.createBlockData());
        }
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 8, 0.25, 0.1, 0.25, 0.02);
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
                world.spawnParticle(particle, at.clone().add(dir.clone().multiply(d)).add(0, 1.1, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void itemBurst(World world, Location at, Material material, int count) {
        world.spawnParticle(Particle.ITEM, at.clone().add(0, 0.4, 0), count, 0.45, 0.3, 0.45, 0.08, new ItemStack(material));
    }

    private static void dust(World world, Location at, Color color, int count) {
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.35, 0), count, 0.5, 0.3, 0.5, 0,
                new Particle.DustOptions(color, 1.35f));
    }
}
