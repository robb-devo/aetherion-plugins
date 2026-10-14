package de.aetherion.core.exile;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A moving copy of real blocks: every block of a box becomes a {@link BlockDisplay} riding one invisible root.
 * Moving the root moves the whole build in one packet, smoothed on the client by {@code teleport_duration}.
 *
 * <p>Rigs are per viewer (hidden from everyone else) and never persistent. While a rig stands in for the
 * real blocks it was copied from, those blocks are hidden for the viewer only, so the copy can sail off and
 * later sink back into its own berth without a seam.
 */
public final class BlockRig {

    public static final String TAG = "aetherion_exile_rig";

    private final Plugin plugin;
    private final NamespacedKey key;
    private final ItemDisplay root;
    private final List<Display> parts = new ArrayList<>();
    private final List<Vector3f> restTranslation = new ArrayList<>();
    private final List<Vector3f> restScale = new ArrayList<>();
    private final List<Quaternionf> restRotation = new ArrayList<>();
    private final List<Location> stand = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final int teleportTicks;

    private BlockRig(Plugin plugin, ItemDisplay root, int teleportTicks) {
        this.plugin = plugin;
        this.key = key(plugin);
        this.root = root;
        this.teleportTicks = teleportTicks;
    }

    static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "exile_rig");
    }

    /** An empty rig whose origin (all translations are relative to it) sits at {@code at}. */
    public static BlockRig spawn(Plugin plugin, Location at, int teleportTicks, Collection<? extends Player> viewers) {
        NamespacedKey key = key(plugin);
        ItemDisplay root = at.getWorld().spawn(at.clone(), ItemDisplay.class, d -> {
            mark(d, key);
            d.setTeleportDuration(teleportTicks);
            d.setViewRange(4.0f);
        });
        BlockRig rig = new BlockRig(plugin, root, teleportTicks);
        for (Player viewer : viewers) {
            rig.show(viewer);
        }
        return rig;
    }

    private static void mark(Entity e, NamespacedKey key) {
        e.setPersistent(false);
        e.setVisibleByDefault(false);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        e.setRotation(0f, 0f);
    }

    public void show(Player viewer) {
        if (viewer == null) {
            return;
        }
        viewers.add(viewer.getUniqueId());
        viewer.showEntity(plugin, root);
        for (Display d : parts) {
            viewer.showEntity(plugin, d);
        }
    }

    public ItemDisplay root() {
        return root;
    }

    public Location location() {
        return root.getLocation();
    }

    public boolean valid() {
        return root.isValid();
    }

    /**
     * Copies every block in the inclusive box that {@code keep} accepts. Translations are block corner minus
     * the root position, so the copy lines up with the original while the root sits where it was spawned.
     *
     * @param standIn true: hide these real blocks for the viewers while the rig exists
     */
    public int copy(World world, int x1, int y1, int z1, int x2, int y2, int z2, Predicate<BlockData> keep, boolean standIn) {
        return copy(world, x1, y1, z1, x2, y2, z2, keep, standIn, root.getLocation());
    }

    /** As {@link #copy}, with translations measured from {@code origin} (where the rig will line up). */
    public int copy(World world, int x1, int y1, int z1, int x2, int y2, int z2, Predicate<BlockData> keep, boolean standIn,
                    Location origin) {
        Location o = origin;
        int n = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (keep != null && !keep.test(data)) {
                        continue;
                    }
                    addBlock(data, new Vector3f((float) (x - o.getX()), (float) (y - o.getY()), (float) (z - o.getZ())),
                            new Vector3f(1f, 1f, 1f), new Quaternionf());
                    if (standIn) {
                        stand.add(block.getLocation());
                    }
                    n++;
                }
            }
        }
        return n;
    }

    public BlockDisplay addBlock(BlockData data, Vector3f translation, Vector3f scale, Quaternionf rotation) {
        BlockDisplay d = root.getWorld().spawn(root.getLocation(), BlockDisplay.class, b -> {
            mark(b, key);
            b.setBlock(data);
            b.setViewRange(4.0f);
            b.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(scale), new Quaternionf()));
        });
        attach(d, translation, scale, rotation);
        return d;
    }

    public ItemDisplay addItem(ItemStack item, Vector3f translation, Vector3f scale, Quaternionf rotation) {
        ItemDisplay d = root.getWorld().spawn(root.getLocation(), ItemDisplay.class, b -> {
            mark(b, key);
            b.setItemStack(item);
            b.setViewRange(4.0f);
            b.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(scale), new Quaternionf()));
        });
        attach(d, translation, scale, rotation);
        return d;
    }

    private void attach(Display d, Vector3f translation, Vector3f scale, Quaternionf rotation) {
        root.addPassenger(d);
        parts.add(d);
        restTranslation.add(new Vector3f(translation));
        restScale.add(new Vector3f(scale));
        restRotation.add(new Quaternionf(rotation));
        for (UUID id : viewers) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.showEntity(plugin, d);
            }
        }
    }

    /** Moves the whole rig; the client glides there over {@code teleportTicks}. */
    public void moveTo(Location at) {
        if (!root.isValid()) {
            return;
        }
        Location to = at.clone();
        to.setYaw(0f);
        to.setPitch(0f);
        root.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /**
     * Rolls (about the travel axis, x) and pitches (about z) every part around the rig origin, interpolated on the
     * client over {@code ticks}. Call every {@code ticks} with the next target for a continuous sway.
     */
    public void sway(float rollDeg, float pitchDeg, int ticks) {
        Quaternionf q = new Quaternionf().rotateX((float) Math.toRadians(rollDeg)).rotateZ((float) Math.toRadians(pitchDeg));
        for (int i = 0; i < parts.size(); i++) {
            Display d = parts.get(i);
            if (!d.isValid()) {
                continue;
            }
            Vector3f t = new Vector3f(restTranslation.get(i));
            q.transform(t);
            Quaternionf r = new Quaternionf(q).mul(restRotation.get(i));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(t, r, new Vector3f(restScale.get(i)), new Quaternionf()));
        }
    }

    /** Shrinks the given parts to nothing over {@code ticks} (the Ferryman fades; he is one of the Faded). */
    public void fade(Collection<? extends Display> which, int ticks) {
        for (Display d : which) {
            if (d == null || !d.isValid()) {
                continue;
            }
            Transformation now = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(new Vector3f(now.getTranslation()).add(0f, 0.4f, 0f),
                    now.getLeftRotation(), new Vector3f(0.001f, 0.001f, 0.001f), now.getRightRotation()));
        }
    }

    /** Re-applies a part's rest pose with an offset (small animations such as the Ferryman's pole). */
    public void pose(Display d, Vector3f translation, Quaternionf rotation, int ticks) {
        int i = parts.indexOf(d);
        if (i < 0 || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(restScale.get(i)), new Quaternionf()));
    }

    /** Hide the real blocks this rig copies, for {@code viewer} only. Safe to call again (chunk resends). */
    public void hideStatic(Player viewer) {
        if (viewer == null || stand.isEmpty()) {
            return;
        }
        BlockData air = Material.AIR.createBlockData();
        for (Location at : stand) {
            viewer.sendBlockChange(at, air);
        }
    }

    /** Show the real blocks again for {@code viewer}. */
    public void restoreStatic(Player viewer) {
        if (viewer == null) {
            return;
        }
        for (Location at : stand) {
            viewer.sendBlockChange(at, at.getBlock().getBlockData());
        }
    }

    public List<Display> parts() {
        return parts;
    }

    public void remove() {
        for (Display d : parts) {
            try {
                d.remove();
            } catch (Throwable ignored) {
            }
        }
        parts.clear();
        try {
            root.remove();
        } catch (Throwable ignored) {
        }
    }

    /** Removes rigs a crash or reload left behind in {@code world} (loaded chunks). */
    public static int purge(Plugin plugin, World world) {
        if (world == null) {
            return 0;
        }
        NamespacedKey key = key(plugin);
        int n = 0;
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(TAG) || e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.remove();
                n++;
            }
        }
        return n;
    }
}
