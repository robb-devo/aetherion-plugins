package de.aetherion.hub.origin;

import com.destroystokyo.paper.ParticleBuilder;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hub life you can hear and see. Two layers, both per-player (nobody receives effects for places they aren't near):
 * <ul>
 *   <li><b>Soundscapes</b> — each district has a day and a night palette (gulls and rigging at the harbour, chatter
 *   and the fountain in the Capital, wind and chimes on Skyreach, frogs and owls in the wilds, bones creaking in the
 *   Borderlands…). Every few seconds one quiet sample plays somewhere around you. Fireflies at night, drifting petals
 *   and motes by day.</li>
 *   <li><b>Emitters</b> — measured spots of the map: forges and kitchens, the alchemist's counter, the beacon pier's
 *   sweeping lighthouse beam and the ships' mast lights at night, the fountain, glowcap spores and humming starbells
 *   after dark, the shrine braziers, library pages, Vince's coins.</li>
 * </ul>
 */
public final class OriginAmbience {

    private record Sample(Sound sound, float volume, float pitchMin, float pitchMax) {
    }

    private record Palette(List<Sample> day, List<Sample> night, boolean fireflies, boolean petals, boolean motes) {
    }

    private static final Map<String, Palette> PALETTES = new HashMap<>();

    static {
        PALETTES.put("harbour", new Palette(
                List.of(new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.35f, 1.6f, 2.0f),
                        new Sample(Sound.ENTITY_BOAT_PADDLE_WATER, 0.4f, 0.8f, 1.0f),
                        new Sample(Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.25f, 0.6f, 0.8f),
                        new Sample(Sound.ENTITY_VILLAGER_AMBIENT, 0.25f, 0.9f, 1.1f),
                        new Sample(Sound.BLOCK_CHAIN_PLACE, 0.2f, 0.5f, 0.7f)),
                List.of(new Sample(Sound.BLOCK_WATER_AMBIENT, 0.45f, 0.7f, 0.9f),
                        new Sample(Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.2f, 0.5f, 0.6f),
                        new Sample(Sound.BLOCK_BELL_RESONATE, 0.15f, 1.4f, 1.6f)),
                false, false, false));
        PALETTES.put("capital", new Palette(
                List.of(new Sample(Sound.ENTITY_VILLAGER_AMBIENT, 0.3f, 0.85f, 1.15f),
                        new Sample(Sound.ENTITY_VILLAGER_TRADE, 0.25f, 0.9f, 1.1f),
                        new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.2f, 1.7f, 2.0f),
                        new Sample(Sound.BLOCK_WOOD_STEP, 0.3f, 0.8f, 1.0f),
                        new Sample(Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.9f, 1.1f)),
                List.of(new Sample(Sound.BLOCK_CAMPFIRE_CRACKLE, 0.3f, 0.8f, 1.0f),
                        new Sample(Sound.ENTITY_CAT_AMBIENT, 0.2f, 0.9f, 1.2f),
                        new Sample(Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.2f, 0.8f, 1.0f)),
                false, true, false));
        PALETTES.put("mountain", new Palette(
                List.of(new Sample(Sound.ITEM_ELYTRA_FLYING, 0.12f, 1.4f, 1.8f),
                        new Sample(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.9f, 1.4f),
                        new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.2f, 1.8f, 2.0f)),
                List.of(new Sample(Sound.ITEM_ELYTRA_FLYING, 0.12f, 1.2f, 1.5f),
                        new Sample(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.7f, 1.1f)),
                false, false, true));
        PALETTES.put("ridge", new Palette(
                List.of(new Sample(Sound.BLOCK_STONE_HIT, 0.25f, 0.6f, 0.8f),
                        new Sample(Sound.ITEM_ELYTRA_FLYING, 0.1f, 1.5f, 1.8f)),
                List.of(new Sample(Sound.ENTITY_BAT_AMBIENT, 0.2f, 0.9f, 1.1f)),
                false, false, false));
        PALETTES.put("wilds", new Palette(
                List.of(new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.3f, 1.4f, 1.9f),
                        new Sample(Sound.BLOCK_AZALEA_LEAVES_STEP, 0.3f, 0.8f, 1.1f),
                        new Sample(Sound.ENTITY_FROG_AMBIENT, 0.3f, 0.9f, 1.2f),
                        new Sample(Sound.BLOCK_WATER_AMBIENT, 0.3f, 0.9f, 1.1f)),
                List.of(new Sample(Sound.ENTITY_FROG_AMBIENT, 0.4f, 0.8f, 1.1f),
                        new Sample(Sound.ENTITY_FOX_AMBIENT, 0.2f, 0.6f, 0.8f),
                        new Sample(Sound.ENTITY_BAT_AMBIENT, 0.15f, 0.8f, 1.0f)),
                true, false, false));
        PALETTES.put("meadow", new Palette(
                List.of(new Sample(Sound.ENTITY_BEE_LOOP, 0.2f, 1.0f, 1.2f),
                        new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.25f, 1.6f, 2.0f),
                        new Sample(Sound.BLOCK_AZALEA_LEAVES_STEP, 0.25f, 0.9f, 1.2f)),
                List.of(new Sample(Sound.ENTITY_FROG_AMBIENT, 0.3f, 1.0f, 1.2f)),
                true, true, false));
        PALETTES.put("arena", new Palette(
                List.of(new Sample(Sound.ENTITY_VILLAGER_CELEBRATE, 0.2f, 0.8f, 1.0f),
                        new Sample(Sound.ITEM_ARMOR_EQUIP_IRON, 0.3f, 0.8f, 1.0f),
                        new Sample(Sound.BLOCK_ANVIL_LAND, 0.1f, 1.4f, 1.6f)),
                List.of(new Sample(Sound.BLOCK_CAMPFIRE_CRACKLE, 0.3f, 0.8f, 1.0f)),
                false, false, false));
        PALETTES.put("crags", new Palette(
                List.of(new Sample(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.0f, 1.5f),
                        new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.2f, 1.5f, 1.9f),
                        new Sample(Sound.ITEM_ELYTRA_FLYING, 0.08f, 1.5f, 1.8f)),
                List.of(new Sample(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.6f, 1.0f),
                        new Sample(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.15f, 0.7f, 0.9f)),
                true, false, true));
        PALETTES.put("farm", new Palette(
                List.of(new Sample(Sound.ENTITY_CHICKEN_AMBIENT, 0.35f, 0.9f, 1.2f),
                        new Sample(Sound.ENTITY_COW_AMBIENT, 0.25f, 0.9f, 1.1f),
                        new Sample(Sound.ENTITY_SHEEP_AMBIENT, 0.25f, 0.9f, 1.1f),
                        new Sample(Sound.BLOCK_COMPOSTER_FILL, 0.3f, 0.8f, 1.0f),
                        new Sample(Sound.ITEM_HOE_TILL, 0.3f, 0.9f, 1.1f)),
                List.of(new Sample(Sound.ENTITY_FROG_AMBIENT, 0.3f, 0.9f, 1.1f),
                        new Sample(Sound.ENTITY_CAT_AMBIENT, 0.2f, 0.9f, 1.1f)),
                true, true, false));
        PALETTES.put("forest", new Palette(
                List.of(new Sample(Sound.ENTITY_PARROT_AMBIENT, 0.3f, 1.4f, 1.9f),
                        new Sample(Sound.BLOCK_WOOD_HIT, 0.3f, 0.6f, 0.8f),
                        new Sample(Sound.BLOCK_AZALEA_LEAVES_STEP, 0.3f, 0.8f, 1.0f)),
                List.of(new Sample(Sound.ENTITY_FOX_AMBIENT, 0.2f, 0.7f, 0.9f),
                        new Sample(Sound.ENTITY_BAT_AMBIENT, 0.15f, 0.8f, 1.0f)),
                true, false, false));
        PALETTES.put("wastes", new Palette(
                List.of(new Sample(Sound.ITEM_ELYTRA_FLYING, 0.1f, 0.6f, 0.8f),
                        new Sample(Sound.ENTITY_SKELETON_AMBIENT, 0.15f, 0.5f, 0.7f),
                        new Sample(Sound.BLOCK_MUD_STEP, 0.3f, 0.7f, 0.9f)),
                List.of(new Sample(Sound.ENTITY_WOLF_HOWL, 0.12f, 0.6f, 0.8f),
                        new Sample(Sound.ENTITY_SKELETON_AMBIENT, 0.2f, 0.5f, 0.6f),
                        new Sample(Sound.AMBIENT_CAVE, 0.15f, 0.8f, 1.0f)),
                false, false, false));
    }

    private final OriginIsle isle;
    private final Map<UUID, Long> lastSample = new HashMap<>();

    OriginAmbience(OriginIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ soundscape (every 60 ticks)

    void soundscape(List<Player> onIsle) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean night = isle.night();
        OriginIsle.Phase phase = isle.phase();
        for (Player player : onIsle) {
            OriginProfile profile = isle.profiles().get(player);
            if (!profile.ambience || isle.flight().flying(player)) {
                continue;
            }
            OriginConfig.District district = isle.config().districtAt(player.getLocation());
            if (district == null) {
                continue;
            }
            Palette palette = PALETTES.get(district.soundscape());
            if (palette == null) {
                continue;
            }
            List<Sample> pool = night ? palette.night() : palette.day();
            if (!pool.isEmpty() && rng.nextDouble() < 0.55d) {
                Sample s = pool.get(rng.nextInt(pool.size()));
                Location at = around(player.getLocation(), 5.0d, 12.0d, rng);
                player.playSound(at, s.sound(), SoundCategory.AMBIENT, s.volume(),
                        s.pitchMin() + rng.nextFloat() * Math.max(0.0f, s.pitchMax() - s.pitchMin()));
            }
            if (!profile.particles) {
                continue;
            }
            Location feet = player.getLocation();
            if ((night || phase == OriginIsle.Phase.DUSK) && palette.fireflies()) {
                for (int i = 0; i < 6; i++) {
                    Location f = around(feet, 2.0d, 9.0d, rng).add(0, 0.6 + rng.nextDouble() * 2.2, 0);
                    player.spawnParticle(Particle.DUST, f, 1, 0.1, 0.1, 0.1, 0.0,
                            new Particle.DustOptions(Color.fromRGB(215, 255, 120), 0.7f));
                }
                player.spawnParticle(Particle.GLOW, around(feet, 2.0d, 7.0d, rng).add(0, 1.2, 0), 2, 0.8, 0.5, 0.8, 0.005);
            }
            if (!night && palette.petals()) {
                player.spawnParticle(Particle.CHERRY_LEAVES, feet.clone().add(0, 6, 0), 5, 6.0, 1.5, 6.0, 0.0);
            }
            if (palette.motes()) {
                player.spawnParticle(Particle.WHITE_ASH, feet.clone().add(0, 2, 0), 14, 7.0, 3.0, 7.0, 0.0);
            }
        }
    }

    private static Location around(Location at, double min, double max, ThreadLocalRandom rng) {
        double a = rng.nextDouble() * Math.PI * 2.0d;
        double r = min + rng.nextDouble() * (max - min);
        return at.clone().add(Math.cos(a) * r, rng.nextDouble() * 3.0d - 0.5d, Math.sin(a) * r);
    }

    // ------------------------------------------------------------------ emitters (every 20 ticks)

    void emitters(List<Player> onIsle, long tick) {
        if (onIsle.isEmpty()) {
            return;
        }
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        OriginIsle.Phase phase = isle.phase();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (OriginConfig.Emitter e : isle.config().emitters()) {
            if (!active(e.when(), phase) || "lighthouse".equals(e.kind())) {
                continue;
            }
            List<Player> near = near(onIsle, e);
            if (near.isEmpty()) {
                continue;
            }
            Location at = new Location(world, e.at()[0], e.at()[1], e.at()[2]);
            for (Player player : near) {
                OriginProfile profile = isle.profiles().get(player);
                emit(player, profile, e, at, rng, tick);
            }
        }
    }

    /** Every 4 ticks: the few emitters that must move smoothly (the lighthouse beam). */
    void fastEmitters(List<Player> onIsle, long tick) {
        if (onIsle.isEmpty() || !isle.night()) {
            return;
        }
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        for (OriginConfig.Emitter e : isle.config().emitters()) {
            if (!"lighthouse".equals(e.kind())) {
                continue;
            }
            List<Player> near = new ArrayList<>();
            for (Player player : near(onIsle, e)) {
                if (isle.profiles().get(player).particles) {
                    near.add(player);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            Location lamp = new Location(world, e.at()[0], e.at()[1], e.at()[2]);
            double angle = Math.toRadians((tick * 3L) % 360L);
            for (int ray = 0; ray < 2; ray++) {
                double a = angle + ray * Math.PI;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                for (int k = 1; k <= 12; k++) {
                    double d = k * 3.0d;
                    new ParticleBuilder(Particle.END_ROD).location(lamp.clone().add(cos * d, -k * 0.12d, sin * d)).count(1)
                            .offset(0.05, 0.05, 0.05).extra(0.0).receivers(near).force(true).spawn();
                }
            }
            new ParticleBuilder(Particle.DUST).location(lamp).count(3).offset(0.25, 0.25, 0.25)
                    .data(new Particle.DustOptions(Color.fromRGB(255, 244, 200), 1.6f)).receivers(near).force(true).spawn();
        }
    }

    private static boolean active(String when, OriginIsle.Phase phase) {
        return switch (when == null ? "any" : when.toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> phase == OriginIsle.Phase.DAY || phase == OriginIsle.Phase.DAWN;
            case "night" -> phase == OriginIsle.Phase.NIGHT || phase == OriginIsle.Phase.DUSK;
            case "dusk" -> phase == OriginIsle.Phase.DUSK;
            case "dawn" -> phase == OriginIsle.Phase.DAWN;
            default -> true;
        };
    }

    private static List<Player> near(List<Player> onIsle, OriginConfig.Emitter e) {
        List<Player> out = new ArrayList<>();
        double r2 = e.radius() * e.radius();
        for (Player player : onIsle) {
            Location at = player.getLocation();
            double dx = at.getX() - e.at()[0];
            double dy = at.getY() - e.at()[1];
            double dz = at.getZ() - e.at()[2];
            if (dx * dx + dy * dy * 0.5d + dz * dz <= r2) {
                out.add(player);
            }
        }
        return out;
    }

    private void emit(Player player, OriginProfile profile, OriginConfig.Emitter e, Location at, ThreadLocalRandom rng, long tick) {
        boolean sound = profile.ambience;
        boolean fx = profile.particles;
        switch (e.kind()) {
            case "forge" -> {
                if (fx) {
                    player.spawnParticle(Particle.LAVA, at, 1, 0.2, 0.1, 0.2, 0.0);
                    player.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 0.8, 0), 2, 0.2, 0.3, 0.2, 0.01);
                }
                if (sound && rng.nextDouble() < 0.4d) {
                    player.playSound(at, Sound.BLOCK_ANVIL_USE, SoundCategory.AMBIENT, 0.25f, 0.9f + rng.nextFloat() * 0.3f);
                }
                if (sound && rng.nextDouble() < 0.3d) {
                    player.playSound(at, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.AMBIENT, 0.6f, 1.0f);
                }
            }
            case "kitchen" -> {
                if (fx) {
                    player.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.01);
                }
                if (sound && rng.nextDouble() < 0.35d) {
                    player.playSound(at, Sound.BLOCK_SMOKER_SMOKE, SoundCategory.AMBIENT, 0.5f, 1.0f);
                }
            }
            case "alchemy" -> {
                if (fx) {
                    player.spawnParticle(Particle.WITCH, at.clone().add(0, 0.6, 0), 3, 0.15, 0.2, 0.15, 0.0);
                    player.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.8, 0), 2, 0.1, 0.1, 0.1, 0.01);
                }
                if (sound && rng.nextDouble() < 0.25d) {
                    player.playSound(at, Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.AMBIENT, 0.35f, 1.0f + rng.nextFloat() * 0.3f);
                }
            }
            case "mastlight" -> {
                if (fx) {
                    new ParticleBuilder(Particle.DUST).location(at).count(2).offset(0.1, 0.1, 0.1)
                            .data(new Particle.DustOptions(Color.fromRGB(255, 214, 120), 1.4f)).receivers(player).force(true).spawn();
                }
            }
            case "fountain" -> {
                if (fx) {
                    player.spawnParticle(Particle.SPLASH, at.clone().add(0, 1.0, 0), 14, 1.2, 0.3, 1.2, 0.1);
                    player.spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 2.2, 0), 6, 0.6, 0.2, 0.6, 0.0);
                }
                if (sound && rng.nextDouble() < 0.6d) {
                    player.playSound(at, Sound.BLOCK_WATER_AMBIENT, SoundCategory.AMBIENT, 0.55f, 1.1f);
                }
                if (fx && rng.nextDouble() < 0.12d) {
                    player.spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.4, 0), 3, 1.0, 0.1, 1.0, 0.0);
                }
            }
            case "gulls" -> {
                if (sound && rng.nextDouble() < 0.35d) {
                    Location gull = around(at, 4.0d, 18.0d, rng);
                    player.playSound(gull, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.AMBIENT, 0.5f, 1.8f + rng.nextFloat() * 0.2f);
                }
                if (fx && rng.nextDouble() < 0.5d) {
                    double a = (tick % 360) * 0.05d + rng.nextDouble();
                    Location bird = at.clone().add(Math.cos(a) * 14.0d, 4 + rng.nextDouble() * 4, Math.sin(a) * 14.0d);
                    new ParticleBuilder(Particle.DUST).location(bird).count(2).offset(0.3, 0.05, 0.3)
                            .data(new Particle.DustOptions(Color.fromRGB(245, 245, 245), 1.2f)).receivers(player).force(true).spawn();
                }
            }
            case "surf" -> {
                if (fx) {
                    player.spawnParticle(Particle.SPLASH, around(at, 0.0d, 6.0d, rng), 10, 1.5, 0.1, 1.5, 0.05);
                }
                if (sound && rng.nextDouble() < 0.4d) {
                    player.playSound(at, Sound.BLOCK_WATER_AMBIENT, SoundCategory.AMBIENT, 0.6f, 0.7f);
                }
            }
            case "starbell" -> {
                if (fx) {
                    player.spawnParticle(Particle.END_ROD, at, 3, 2.5, 2.0, 2.5, 0.01);
                    player.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0, 2, 0), 4, 2.0, 1.5, 2.0, 0.0,
                            new Particle.DustTransition(Color.fromRGB(190, 110, 255), Color.fromRGB(110, 255, 220), 1.1f));
                }
                if (sound && rng.nextDouble() < 0.3d) {
                    player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.6f, 0.6f + rng.nextFloat() * 0.8f);
                }
            }
            case "glowcap" -> {
                if (fx) {
                    new ParticleBuilder(Particle.GLOW).location(at).count(4).offset(2.5, 1.0, 2.5).extra(0.01)
                            .receivers(player).force(true).spawn();
                    new ParticleBuilder(Particle.DUST).location(at.clone().add(0, -2, 0)).count(3).offset(3.0, 2.0, 3.0)
                            .data(new Particle.DustOptions(Color.fromRGB(90, 200, 255), 0.9f)).receivers(player).force(true).spawn();
                }
            }
            case "market" -> {
                if (sound && rng.nextDouble() < 0.35d) {
                    Sound pick = rng.nextBoolean() ? Sound.ENTITY_VILLAGER_TRADE : Sound.ENTITY_VILLAGER_AMBIENT;
                    player.playSound(around(at, 1.0d, 7.0d, rng), pick, SoundCategory.AMBIENT, 0.35f, 0.9f + rng.nextFloat() * 0.3f);
                }
                if (sound && rng.nextDouble() < 0.15d) {
                    player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 0.15f, 0.6f);
                }
            }
            case "mill" -> {
                if (fx) {
                    player.spawnParticle(Particle.CLOUD, at, 2, 3.0, 2.0, 3.0, 0.02);
                }
                if (sound && rng.nextDouble() < 0.3d) {
                    player.playSound(at, Sound.BLOCK_WOOD_STEP, SoundCategory.AMBIENT, 0.5f, 0.5f);
                }
            }
            case "chimes" -> {
                if (sound && rng.nextDouble() < 0.45d) {
                    player.playSound(around(at, 1.0d, 6.0d, rng), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT,
                            0.6f, 0.8f + rng.nextFloat() * 0.9f);
                }
                if (fx) {
                    player.spawnParticle(Particle.WHITE_ASH, at, 10, 6.0, 3.0, 6.0, 0.0);
                }
            }
            case "bones" -> {
                if (sound && rng.nextDouble() < 0.25d) {
                    player.playSound(around(at, 2.0d, 10.0d, rng), Sound.BLOCK_BONE_BLOCK_STEP, SoundCategory.AMBIENT, 0.6f, 0.5f);
                }
                if (fx) {
                    player.spawnParticle(Particle.ASH, at, 12, 8.0, 3.0, 8.0, 0.0);
                }
            }
            case "farm" -> {
                if (sound && rng.nextDouble() < 0.4d) {
                    Sound pick = switch (rng.nextInt(3)) {
                        case 0 -> Sound.ENTITY_CHICKEN_AMBIENT;
                        case 1 -> Sound.ENTITY_COW_AMBIENT;
                        default -> Sound.ENTITY_SHEEP_AMBIENT;
                    };
                    player.playSound(around(at, 2.0d, 10.0d, rng), pick, SoundCategory.AMBIENT, 0.4f, 0.9f + rng.nextFloat() * 0.2f);
                }
            }
            case "brazier" -> {
                if (fx) {
                    new ParticleBuilder(Particle.FLAME).location(at).count(4).offset(1.2, 0.3, 1.2).extra(0.01)
                            .receivers(player).force(true).spawn();
                    new ParticleBuilder(Particle.SMALL_FLAME).location(at.clone().add(0, 0.5, 0)).count(3).offset(1.0, 0.3, 1.0).extra(0.02)
                            .receivers(player).force(true).spawn();
                }
            }
            case "pages" -> {
                if (fx) {
                    player.spawnParticle(Particle.ENCHANT, at.clone().add(0, 1.5, 0), 10, 1.5, 1.0, 1.5, 0.4);
                }
                if (sound && rng.nextDouble() < 0.3d) {
                    player.playSound(at, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.AMBIENT, 0.5f, 0.9f + rng.nextFloat() * 0.2f);
                }
            }
            case "coins" -> {
                if (sound && rng.nextDouble() < 0.35d) {
                    player.playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.AMBIENT, 0.3f, 1.8f);
                }
                if (fx && rng.nextDouble() < 0.4d) {
                    player.spawnParticle(Particle.WAX_ON, at.clone().add(0, 1, 0), 3, 0.8, 0.4, 0.8, 0.0);
                }
            }
            default -> {
            }
        }
    }

    void forget(UUID id) {
        lastSample.remove(id);
    }
}
