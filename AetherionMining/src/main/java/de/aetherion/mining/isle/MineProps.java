package de.aetherion.mining.isle;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Display-entity set pieces on Mining Eldervale. Nothing here is a block, so a paste, a reset or a
 * WorldGuard rule can never eat them. They are rebuilt when their chunk loads.
 * <ul>
 *   <li><b>The Deep Forge</b> at the bottom of the North Pit: a molten crucible that breathes, a
 *       leather bellows that pumps, a six-block chimney that smokes, a quench trough, a rack of
 *       picks from iron to netherite, and a banner. It roars when anyone forges a Mark here or
 *       upgrades a blueprint at the Forgehand upstairs.</li>
 *   <li><b>The Forgehand's anvil</b>: four braziers and a name plate around the Items forge frame.
 *       They flare for the whole length of a blueprint ritual.</li>
 *   <li><b>The Record Board</b> at the Assay Office: the heaviest specimen per ore family, live.</li>
 * </ul>
 */
public final class MineProps implements Listener {

    enum Piece {
        DEEP_FORGE("forge-prop.deep", "39.5 38 478.5"),
        FORGEHAND("forge-prop.forgehand", null),
        RECORDS("props.records-board", "12.5 124.3 661.7 180");

        final String path;
        final String fallback;

        Piece(String path, String fallback) {
            this.path = path;
            this.fallback = fallback;
        }
    }

    private final MineIsle isle;
    private final NamespacedKey propKey;
    private final Map<Piece, List<UUID>> live = new EnumMap<>(Piece.class);
    private BlockDisplay bellows;
    private BlockDisplay molten;
    private TextDisplay records;
    private final List<BlockDisplay> braziers = new ArrayList<>();
    private BukkitTask task;
    private boolean breathIn;
    private int roarUntil;
    private int ritualUntil;
    private Location ritualAt;

    MineProps(MineIsle isle) {
        this.isle = isle;
        this.propKey = new NamespacedKey(isle.plugin(), "mine_prop");
    }

    void start() {
        purgeStrays();
        for (Piece piece : Piece.values()) {
            ensure(piece);
        }
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 40L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Piece piece : Piece.values()) {
            despawn(piece);
        }
    }

    /** A record changed: rewrite the board text (cheap). */
    public void refresh() {
        if (records != null && records.isValid()) {
            records.text(MineText.legacy(recordText()));
            return;
        }
        ensure(Piece.RECORDS);
    }

    /** Rebuild every piece (config reload, DEV). */
    public void rebuild() {
        if (task == null) {
            return;
        }
        for (Piece piece : Piece.values()) {
            despawn(piece);
            ensure(piece);
        }
    }

    Location anchor(Piece piece) {
        String raw = isle.plugin().getConfig().getString(piece.path, piece.fallback);
        return raw == null ? null : MineWorld.point(isle.plugin(), raw);
    }

    /** DEV: move a piece to where the player stands. */
    public String placeHere(String id, Player player) {
        Piece piece;
        try {
            piece = Piece.valueOf(id.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return "§cUnknown prop " + id + ".";
        }
        Location at = player.getLocation();
        String raw = String.format(java.util.Locale.US, "%.1f %.1f %.1f %.0f", at.getX(), at.getY(), at.getZ(), at.getYaw());
        isle.plugin().getConfig().set(piece.path, raw);
        isle.plugin().saveConfig();
        despawn(piece);
        ensure(piece);
        return "§a" + piece.name().toLowerCase(java.util.Locale.ROOT) + " §7moved to §f" + raw + "§7.";
    }

    // ------------------------------------------------------------------ building

    private void ensure(Piece piece) {
        if (live.containsKey(piece)) {
            return;
        }
        Location at = anchor(piece);
        if (at == null || at.getWorld() == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        switch (piece) {
            case DEEP_FORGE -> buildDeepForge(at, ids);
            case FORGEHAND -> buildForgehand(at, ids);
            case RECORDS -> buildRecords(at, ids);
        }
        live.put(piece, ids);
    }

    private void despawn(Piece piece) {
        List<UUID> ids = live.remove(piece);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        if (piece == Piece.DEEP_FORGE) {
            bellows = null;
            molten = null;
        } else if (piece == Piece.FORGEHAND) {
            braziers.clear();
        } else if (piece == Piece.RECORDS) {
            records = null;
        }
    }

    private void buildDeepForge(Location base, List<UUID> ids) {
        World world = base.getWorld();
        // Crucible: a squat cauldron with a molten top that pulses.
        block(ids, world, base.clone().add(2.2, 1.0, -3.8), Material.CAULDRON.createBlockData(), 1.25f, 1.1f, 1.25f, false, null);
        molten = block(ids, world, base.clone().add(2.32, 1.85, -3.68), Material.MAGMA_BLOCK.createBlockData(), 1.0f, 0.12f, 1.0f,
                true, Color.fromRGB(255, 120, 20));
        // Bellows: plates and a leather body that breathes.
        block(ids, world, base.clone().add(3.2, 1.05, -1.6), Material.DARK_OAK_PLANKS.createBlockData(), 0.9f, 0.1f, 1.4f, false, null);
        bellows = block(ids, world, base.clone().add(3.25, 1.15, -1.55), Material.BROWN_WOOL.createBlockData(), 0.8f, 0.45f, 1.3f, false, null);
        block(ids, world, base.clone().add(3.2, 1.62, -1.6), Material.DARK_OAK_PLANKS.createBlockData(), 0.9f, 0.1f, 1.4f, false, null);
        block(ids, world, base.clone().add(2.65, 1.3, -1.0), Material.IRON_BARS.createBlockData(), 0.6f, 0.2f, 0.2f, false, null);
        // Chimney: six blocks of dark stone rising out of the hut roof.
        for (int i = 0; i < 6; i++) {
            block(ids, world, base.clone().add(-0.4, 4.0 + i, -0.4), Material.POLISHED_DEEPSLATE.createBlockData(), 0.8f, 1.0f, 0.8f, false, null);
        }
        block(ids, world, base.clone().add(-0.55, 10.0, -0.55), Material.DEEPSLATE_TILE_SLAB.createBlockData(), 1.1f, 0.5f, 1.1f, false, null);
        // Quench trough.
        block(ids, world, base.clone().add(-0.5, 1.0, -4.4), Material.SPRUCE_PLANKS.createBlockData(), 1.8f, 0.55f, 0.8f, false, null);
        block(ids, world, base.clone().add(-0.4, 1.35, -4.3), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 1.6f, 0.15f, 0.6f,
                false, null);
        // Pick rack on the hut wall: iron → netherite.
        Material[] picks = {Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE};
        for (int i = 0; i < picks.length; i++) {
            Location spot = base.clone().add(-3.35, 2.3, -1.4 + i * 0.75);
            ItemStack stack = new ItemStack(picks[i]);
            ItemDisplay display = world.spawn(spot, ItemDisplay.class, spawned -> {
                spawned.setItemStack(stack);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(90)),
                        new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
                tag(spawned);
            });
            ids.add(display.getUniqueId());
        }
        // Ingot stack on a barrel.
        Material[] ingots = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
        for (int i = 0; i < ingots.length; i++) {
            block(ids, world, base.clone().add(-5.3 + 0.05 * i, 3.0 + 0.16 * i, -1.8 + 0.08 * i), ingots[i].createBlockData(),
                    0.45f, 0.14f, 0.22f, false, null);
        }
        // Banner plate.
        TextDisplay plate = world.spawn(base.clone().add(0.0, 5.0, -3.2), TextDisplay.class, text -> {
            text.text(MineText.legacy("§6§l⚒ THE DEEP FORGE ⚒\n§7Brann Emberlock §8· §eForge Works\n§8Marks · Reputation · Flares"));
            text.setBillboard(Display.Billboard.VERTICAL);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(120, 20, 10, 5));
            text.setViewRange(0.6f);
            tag(text);
        });
        ids.add(plate.getUniqueId());
    }

    private void buildForgehand(Location anvil, List<UUID> ids) {
        World world = anvil.getWorld();
        braziers.clear();
        double[][] corners = {{1.6, 1.6}, {-1.6, 1.6}, {1.6, -1.6}, {-1.6, -1.6}};
        for (double[] corner : corners) {
            Location post = anvil.clone().add(corner[0] - 0.1, -1.0, corner[1] - 0.1);
            block(ids, world, post, Material.CHAIN.createBlockData(), 0.2f, 2.4f, 0.2f, false, null);
            BlockDisplay lamp = block(ids, world, post.clone().add(-0.15, 2.4, -0.15), Material.LANTERN.createBlockData(),
                    0.5f, 0.5f, 0.5f, false, null);
            braziers.add(lamp);
        }
        TextDisplay plate = world.spawn(anvil.clone().add(0, 2.6, 0), TextDisplay.class, text -> {
            text.text(MineText.legacy("§6⚒ The Eldervale Forge\n§7Blueprint upgrades §8· §fUpgrade Stones II–IV"));
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setViewRange(0.4f);
            tag(text);
        });
        ids.add(plate.getUniqueId());
    }

    private void buildRecords(Location at, List<UUID> ids) {
        records = at.getWorld().spawn(at, TextDisplay.class, text -> {
            text.text(MineText.legacy(recordText()));
            text.setBillboard(Display.Billboard.VERTICAL);
            text.setAlignment(TextDisplay.TextAlignment.LEFT);
            text.setShadowed(true);
            text.setLineWidth(260);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(150, 18, 10, 26));
            text.setViewRange(0.5f);
            text.setRotation(at.getYaw(), 0.0f);
            tag(text);
        });
        ids.add(records.getUniqueId());
    }

    private String recordText() {
        StringBuilder out = new StringBuilder("§d§l✦ Eldervale Specimen Records ✦\n§8heaviest ever cracked, per family\n");
        Map<IsleOre, MineProfiles.SpecimenRecord> all = isle.profiles().records();
        int shown = 0;
        for (IsleOre ore : IsleOre.crystals()) {
            MineProfiles.SpecimenRecord record = all.get(ore);
            if (record == null) {
                out.append("\n").append(ore.color()).append(ore.crystalName()).append(" §8· §7unclaimed");
                continue;
            }
            shown++;
            out.append("\n").append(ore.color()).append(ore.crystalName()).append(" §f").append(MineText.carats(record.carats()))
                    .append(" §8· §f").append(record.name())
                    .append(record.grade() == null ? "" : " §8(" + record.grade().colored() + "§8)");
        }
        if (shown == 0) {
            out.append("\n\n§7Every line is up for grabs. §8Ilse is waiting.");
        }
        return out.toString();
    }

    private BlockDisplay block(List<UUID> ids, World world, Location at, BlockData data, float sx, float sy, float sz,
                               boolean glow, Color glowColor) {
        BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f()));
            if (glow) {
                spawned.setGlowing(true);
                if (glowColor != null) {
                    spawned.setGlowColorOverride(glowColor);
                }
                spawned.setBrightness(new Display.Brightness(15, 15));
            }
            tag(spawned);
        });
        ids.add(display.getUniqueId());
        return display;
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(propKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(propKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
        live.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Piece piece : Piece.values()) {
            if (live.containsKey(piece)) {
                continue;
            }
            Location at = anchor(piece);
            if (at != null && at.getWorld() != null && at.getWorld().equals(chunk.getWorld())
                    && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                Bukkit.getScheduler().runTask(isle.plugin(), () -> ensure(piece));
            }
        }
    }

    // ------------------------------------------------------------------ life

    private void tick() {
        if (Bukkit.getCurrentTick() % 200 < 10) {
            for (Piece piece : Piece.values()) {
                if (live.containsKey(piece) && !alive(piece)) {
                    despawn(piece);
                }
                ensure(piece);
            }
        }
        int now = Bukkit.getCurrentTick();
        Location forge = anchor(Piece.DEEP_FORGE);
        boolean roaring = now < roarUntil;
        if (forge != null && forge.getWorld() != null && watched(forge)) {
            World world = forge.getWorld();
            breathIn = !breathIn;
            if (bellows != null && bellows.isValid()) {
                bellows.setInterpolationDelay(0);
                bellows.setInterpolationDuration(roaring ? 5 : 10);
                bellows.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.8f, breathIn ? 0.45f : 0.2f, 1.3f), new AxisAngle4f()));
                if (!breathIn) {
                    world.spawnParticle(Particle.SMOKE, forge.clone().add(2.7, 1.35, -0.9), 3, 0.05, 0.05, 0.05, 0.02);
                }
            }
            if (molten != null && molten.isValid()) {
                molten.setGlowColorOverride(breathIn ? Color.fromRGB(255, 150, 30) : Color.fromRGB(230, 70, 10));
            }
            Location crucible = forge.clone().add(2.8, 2.0, -3.2);
            world.spawnParticle(Particle.SMALL_FLAME, crucible, roaring ? 8 : 1, 0.25, 0.05, 0.25, 0.005);
            if (ThreadLocalRandom.current().nextInt(roaring ? 2 : 6) == 0) {
                world.spawnParticle(Particle.LAVA, crucible, 1, 0.2, 0.05, 0.2, 0.0);
            }
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, forge.clone().add(0.0, 10.7, 0.0), roaring ? 4 : 1, 0.12, 0.2, 0.12, 0.02);
            world.spawnParticle(Particle.DRIPPING_WATER, forge.clone().add(0.4, 1.6, -4.0), 1, 0.4, 0.0, 0.1, 0.0);
            if (roaring && now % 20 < 10) {
                world.playSound(crucible, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 0.7f);
            }
        }
        if (ritualAt != null && now < ritualUntil && ritualAt.getWorld() != null) {
            World world = ritualAt.getWorld();
            for (BlockDisplay lamp : braziers) {
                if (lamp.isValid()) {
                    Location flame = lamp.getLocation().add(0.25, 0.6, 0.25);
                    world.spawnParticle(Particle.FLAME, flame, 3, 0.08, 0.1, 0.08, 0.01);
                    world.spawnParticle(Particle.SMALL_FLAME, flame, 2, 0.1, 0.2, 0.1, 0.02);
                }
            }
            double angle = (now % 40) / 40.0d * Math.PI * 2.0d;
            for (int i = 0; i < 3; i++) {
                double a = angle + i * Math.PI * 2.0d / 3.0d;
                world.spawnParticle(Particle.ELECTRIC_SPARK, ritualAt.clone().add(Math.cos(a) * 1.3, 0.3, Math.sin(a) * 1.3),
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private boolean alive(Piece piece) {
        List<UUID> ids = live.get(piece);
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Entity first = Bukkit.getEntity(ids.get(0));
        return first != null && first.isValid();
    }

    private static boolean watched(Location at) {
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) < 48.0d * 48.0d) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ moments

    /** A Mark was forged: sparks, lava and (for the big ones) a bell. */
    void forgeBurst(boolean big) {
        Location forge = anchor(Piece.DEEP_FORGE);
        roarUntil = Bukkit.getCurrentTick() + (big ? 20 * 10 : 20 * 5);
        if (forge == null || forge.getWorld() == null) {
            return;
        }
        World world = forge.getWorld();
        Location crucible = forge.clone().add(2.8, 2.1, -3.2);
        world.spawnParticle(Particle.LAVA, crucible, big ? 30 : 12, 0.4, 0.3, 0.4, 0.0);
        world.spawnParticle(Particle.FLAME, crucible, big ? 60 : 25, 0.5, 0.6, 0.5, 0.05);
        world.spawnParticle(Particle.ELECTRIC_SPARK, crucible, 30, 0.6, 0.6, 0.6, 0.2);
        world.playSound(crucible, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0f, 0.7f);
        world.playSound(crucible, Sound.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1.0f, 0.6f);
        if (big) {
            world.playSound(crucible, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.2f, 0.5f);
            world.spawnParticle(Particle.FLASH, crucible, 1);
        }
    }

    /** Items blueprint ritual started: adopt the anvil as the Forgehand anchor, light the braziers. */
    void forgeStart(Player player, int tier, Location anvil) {
        if (anvil != null && anvil.getWorld() != null) {
            if (isle.plugin().getConfig().getString(Piece.FORGEHAND.path) == null) {
                isle.plugin().getConfig().set(Piece.FORGEHAND.path, String.format(java.util.Locale.US, "%.1f %.1f %.1f",
                        anvil.getX(), anvil.getY(), anvil.getZ()));
                isle.plugin().saveConfig();
                despawn(Piece.FORGEHAND);
                ensure(Piece.FORGEHAND);
            }
            ritualAt = anvil.clone();
        }
        int seconds = switch (Math.max(1, Math.min(4, tier))) {
            case 3 -> 20;
            case 4 -> 30;
            default -> 10;
        };
        ritualUntil = Bukkit.getCurrentTick() + seconds * 20 + 10;
        roarUntil = ritualUntil;
    }

    /** Items blueprint ritual finished: eruption at both forges. */
    void forgeDone(Player player, int tier, Location anvil) {
        ritualUntil = 0;
        Location at = anvil != null ? anvil : ritualAt;
        if (at != null && at.getWorld() != null) {
            World world = at.getWorld();
            world.spawnParticle(Particle.LAVA, at.clone().add(0, 0.6, 0), 16 + 6 * tier, 0.4, 0.2, 0.4, 0.0);
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 0.8, 0), 30 + 10 * tier, 0.6, 0.6, 0.6, 0.06);
            if (tier >= 4) {
                world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.0, 0), 1);
                world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 1.2f, 0.6f);
            }
        }
        forgeBurst(tier >= 3);
    }
}
