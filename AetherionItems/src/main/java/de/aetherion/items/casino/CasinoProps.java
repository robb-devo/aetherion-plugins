package de.aetherion.items.casino;

import de.aetherion.items.core.ItemKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared bits for the casino furniture props (slot machine + roulette table).
 * Everything is display entities; the only real block is one invisible light block.
 */
final class CasinoProps {

    /** Same id on every piece of one machine, so neighbours never touch each other. */
    static final NamespacedKey MACHINE_ID = ItemKeys.key("casino_prop_id");
    /** What a piece is (lever, reel_0, bulb_3, ...). Only pieces that animate need one. */
    static final NamespacedKey PART = ItemKeys.key("casino_prop_part");

    static final Display.Brightness FULL = new Display.Brightness(15, 15);
    static final Display.Brightness DIM = new Display.Brightness(3, 3);

    private CasinoProps() {
    }

    // ── tags ────────────────────────────────────────────────────────────────

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static String id(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(MACHINE_ID, PersistentDataType.STRING);
    }

    static String part(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(PART, PersistentDataType.STRING);
    }

    static void mark(Entity entity, String id, String part) {
        if (id != null) {
            entity.getPersistentDataContainer().set(MACHINE_ID, PersistentDataType.STRING, id);
        }
        if (part != null) {
            entity.getPersistentDataContainer().set(PART, PersistentDataType.STRING, part);
        }
    }

    // ── geometry ────────────────────────────────────────────────────────────

    static Location piece(Location origin, BlockFace front, double right, double y, double alongFront) {
        Vector r = right(front).getDirection().multiply(right);
        Vector f = front.getDirection().multiply(alongFront);
        return origin.clone().add(0.5, y, 0.5).add(r).add(f);
    }

    static float yawOf(BlockFace front) {
        return switch (front) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    static BlockFace right(BlockFace front) {
        return switch (front) {
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    static BlockFace look(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360f + 360f) % 360f;
        if (yaw >= 315f || yaw < 45f) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135f) {
            return BlockFace.WEST;
        }
        if (yaw < 225f) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    static BlockFace face(String name) {
        if (name == null) {
            return BlockFace.SOUTH;
        }
        try {
            return BlockFace.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return BlockFace.SOUTH;
        }
    }

    static boolean fits(Block origin) {
        return replaceable(origin.getType()) && replaceable(origin.getRelative(BlockFace.UP).getType());
    }

    static boolean replaceable(Material material) {
        return material.isAir()
                || material == Material.LIGHT
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.SNOW
                || material == Material.VINE
                || material == Material.FERN
                || material == Material.DEAD_BUSH;
    }

    // ── transformations (local space: +z = front of the prop) ───────────────

    /** Box centred on the entity. */
    static Transformation centered(float w, float h, float d) {
        return new Transformation(
                new Vector3f(-w / 2f, -h / 2f, -d / 2f),
                new Quaternionf(),
                new Vector3f(w, h, d),
                new Quaternionf()
        );
    }

    /** Box standing on the entity (base centred on it), tipped forward around local X. */
    static Transformation tilted(float w, float h, float d, float baseY, float radians) {
        Quaternionf rot = new Quaternionf().rotationX(radians);
        Vector3f offset = new Vector3f(-w / 2f, baseY, -d / 2f);
        rot.transform(offset);
        return new Transformation(offset, rot, new Vector3f(w, h, d), new Quaternionf());
    }

    /** Box sitting on a circle of {@code radius} around the entity, turned {@code radians} around local Y. */
    static Transformation orbit(float w, float h, float d, float radius, float y, float radians) {
        Quaternionf rot = new Quaternionf().rotationY(radians);
        Vector3f offset = new Vector3f(radius - w / 2f, y - h / 2f, -d / 2f);
        rot.transform(offset);
        return new Transformation(offset, rot, new Vector3f(w, h, d), new Quaternionf());
    }

    /** Uniform scale + spin around local Y, for item / text displays. */
    static Transformation spun(float scale, float radians) {
        return new Transformation(
                new Vector3f(),
                new Quaternionf().rotationY(radians),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
    }

    static void animate(Display display, Transformation to, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(to);
    }

    // ── spawners ────────────────────────────────────────────────────────────

    static BlockDisplay box(
            World world,
            Location at,
            float yaw,
            Material material,
            Transformation shape,
            Consumer<Entity> tag
    ) {
        return world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            settle(spawned, yaw);
            spawned.setTransformation(shape);
            tag.accept(spawned);
        });
    }

    static ItemDisplay item(World world, Location at, float yaw, ItemStack stack, float scale, Consumer<Entity> tag) {
        return world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            settle(spawned, yaw);
            spawned.setTransformation(spun(scale, 0f));
            tag.accept(spawned);
        });
    }

    static TextDisplay label(World world, Location at, Component text, float scale, Consumer<Entity> tag) {
        return world.spawn(at, TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setBrightness(FULL);
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setViewRange(48f);
            spawned.setTransformation(spun(scale, 0f));
            tag.accept(spawned);
        });
    }

    /** Flat LED panel glued to the front of a prop. */
    static TextDisplay screen(World world, Location at, float yaw, Component text, float scale, Color background, Consumer<Entity> tag) {
        return world.spawn(at, TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(false);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(background);
            spawned.setLineWidth(200);
            spawned.setBrightness(FULL);
            spawned.setPersistent(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setViewRange(32f);
            spawned.setRotation(yaw, 0f);
            spawned.setTransformation(spun(scale, 0f));
            tag.accept(spawned);
        });
    }

    private static void settle(Display display, float yaw) {
        display.setBrightness(FULL);
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
        display.setPersistent(true);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setViewRange(48f);
        display.setRotation(yaw, 0f);
    }

    static ItemStack shiny(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ── real light (one invisible block, never anything else) ───────────────

    static void light(Block block, int level) {
        if (block == null) {
            return;
        }
        Material type = block.getType();
        if (!type.isAir() && type != Material.LIGHT) {
            return;
        }
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(Math.max(0, Math.min(15, level)));
        if (type == Material.LIGHT && block.getBlockData() instanceof Light current && current.getLevel() == data.getLevel()) {
            return;
        }
        block.setBlockData(data, false);
    }

    static void unlight(Block block) {
        if (block != null && block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    // ── text ────────────────────────────────────────────────────────────────

    /** A bright glint that runs across the word, letter by letter. */
    static Component shimmer(String word, int phase, TextColor base, TextColor near, TextColor peak) {
        TextComponent.Builder out = Component.text();
        int span = word.length() + 6;
        int head = Math.floorMod(phase, span) - 3;
        for (int i = 0; i < word.length(); i++) {
            int distance = Math.abs(i - head);
            TextColor color = distance == 0 ? peak : distance == 1 ? near : base;
            out.append(Component.text(String.valueOf(word.charAt(i)), color, TextDecoration.BOLD));
        }
        return out.build();
    }

    // ── misc ────────────────────────────────────────────────────────────────

    static List<Player> viewers(Location at, double radius) {
        List<Player> out = new ArrayList<>();
        World world = at.getWorld();
        if (world == null) {
            return out;
        }
        double max = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= max) {
                out.add(player);
            }
        }
        return out;
    }

    static boolean anyoneNear(Location at, double radius) {
        World world = at.getWorld();
        if (world == null) {
            return false;
        }
        double max = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= max) {
                return true;
            }
        }
        return false;
    }

    static Entity entity(UUID id) {
        return id == null ? null : Bukkit.getEntity(id);
    }
}
