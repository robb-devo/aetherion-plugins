package de.aetherion.bossengine.loot;

import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE BONUS CHEST: Nihil's loot.
 *
 * <p>When a new Minecraft world is generated with "Bonus Chest" on, a chest is waiting next to
 * spawn with four torches around it. After the World Eater has eaten itself and the Last Seed has
 * generated again, the same thing happens at the heart of the island: a structure outline draws
 * itself on the podzol, the chest loads into it, and four torches are placed around it one after
 * another. It is a world's first gift, not a trophy.
 *
 * <p><b>Claim.</b> Same contract as the reliquary and the music box: {@link LootService#grantToChest}
 * pays XP and the recap at death and hands the item bundles here. Each player right-clicks the chest
 * (an {@link Interaction}) for their own share; the lid sound plays and the share flies out to them.
 * Shares still unclaimed at expiry are delivered: online players get them in their inventory,
 * offline players' shares drop at the chest. Nothing is lost, including on plugin disable
 * ({@link #clearAll()}).
 *
 * <p><b>Unload.</b> Once every share is taken (or on expiry) the torches go out one by one, the
 * outline returns, and the chest unloads out of the world like a chunk leaving render distance.
 *
 * <p>Displays and an Interaction only: no blocks are placed.
 */
public final class WorldEaterBonusChest {

    private static final List<WorldEaterBonusChest> LIVE = new ArrayList<>();

    private static final int OUTLINE = 12;
    private static final int LOAD = OUTLINE + 30;
    private static final int TORCHES = LOAD + 6;
    private static final int READY = TORCHES + 30;
    private static final int CLAIM_TICKS = 20 * 180;
    private static final int UNLOAD = 70;
    private static final float CHEST = 1.7f;
    private static final Color OUTLINE_COLOR = Color.fromRGB(240, 240, 255);

    private enum Phase { WAITING, GENERATING, READY, UNLOADING, DONE }

    private final JavaPlugin plugin;
    private final Location anchor;
    private final World world;
    private final String bossName;
    private final Runnable onFinish;
    private final Map<UUID, List<ItemStack>> unclaimed = new LinkedHashMap<>();
    private final Set<UUID> claimed = new HashSet<>();
    private final int shares;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<BlockDisplay> outline = new ArrayList<>();
    private final BlockDisplay[] torches = new BlockDisplay[4];
    private ItemDisplay chest;
    private Interaction hitbox;
    private TextDisplay label;
    private BukkitTask task;

    private Phase phase = Phase.WAITING;
    private int delay;
    private int tick;
    private int readyTick;
    private int unloadTick;
    private int unloadAt = -1;
    private float chestScale;
    private float chestSpin;

    private WorldEaterBonusChest(JavaPlugin plugin, Location anchor, String bossName,
                                 Map<UUID, List<ItemStack>> bundles, int delayTicks, Runnable onFinish) {
        this.plugin = plugin;
        this.world = anchor.getWorld();
        this.anchor = new Location(world, anchor.getX(), anchor.getY(), anchor.getZ());
        this.bossName = bossName == null ? "" : bossName;
        this.onFinish = onFinish;
        bundles.forEach((playerId, items) -> {
            if (items != null && !items.isEmpty()) {
                unclaimed.put(playerId, new ArrayList<>(items));
            }
        });
        this.shares = unclaimed.size();
        this.delay = Math.max(0, delayTicks);
    }

    /* ================================================================== API */

    /**
     * Registers the chest now (the bundles are safe from this moment) and starts generating it
     * after {@code delayTicks}. {@code onFinish} runs once it has unloaded.
     */
    public static WorldEaterBonusChest place(JavaPlugin plugin, Location anchor, String bossName,
                                             Map<UUID, List<ItemStack>> bundles, int delayTicks, Runnable onFinish) {
        WorldEaterBonusChest chest = new WorldEaterBonusChest(plugin, anchor, bossName, bundles, delayTicks, onFinish);
        LIVE.add(chest);
        chest.task = Bukkit.getScheduler().runTaskTimer(plugin, chest::tick, 1L, 1L);
        return chest;
    }

    /** @return true when {@code clicked} is a Bonus Chest (the click is handled either way) */
    public static boolean click(Player player, Entity clicked) {
        if (player == null || clicked == null) {
            return false;
        }
        for (WorldEaterBonusChest chest : new ArrayList<>(LIVE)) {
            if (chest.hitbox != null && chest.hitbox.getUniqueId().equals(clicked.getUniqueId())) {
                chest.claim(player);
                return true;
            }
        }
        return false;
    }

    /** Plugin disable: every share still in a chest is delivered, then the chests vanish. */
    public static void clearAll() {
        for (WorldEaterBonusChest chest : new ArrayList<>(LIVE)) {
            chest.deliverRemaining(false);
            chest.finish();
        }
        LIVE.clear();
    }

    /* ================================================================== timeline */

    private void tick() {
        if (world == null) {
            deliverRemaining(false);
            finish();
            return;
        }
        switch (phase) {
            case WAITING -> {
                if (delay-- <= 0) {
                    phase = Phase.GENERATING;
                    tick = 0;
                }
            }
            case GENERATING -> tickGenerating();
            case READY -> tickReady();
            case UNLOADING -> tickUnloading();
            default -> {
            }
        }
    }

    private void tickGenerating() {
        int t = tick++;
        if (t == 0) {
            buildOutline();
            play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            play(Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
            actionBar("&7Generating structure: &fBonus Chest");
        }
        if (t < OUTLINE) {
            float f = smooth(t / (float) OUTLINE);
            pushOutline(f, 2);
            return;
        }
        if (t == OUTLINE) {
            chest = world.spawn(anchor, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.CHEST));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                prepare(d);
            });
            spawned.add(chest);
            play(Sound.BLOCK_CHEST_LOCKED, 0.8f, 0.6f);
        }
        if (t >= OUTLINE && t < LOAD) {
            // It loads in from the ground up, spinning into place.
            float f = smooth((t - OUTLINE) / (float) (LOAD - OUTLINE));
            chestScale = CHEST * f;
            chestSpin = (1f - f) * 6f;
            pushChest(2);
            if (t % 3 == 0) {
                dust(at(0f, 0.2f + f * CHEST * 0.8f, 0f), OUTLINE_COLOR, 12, 0.7);
            }
            return;
        }
        if (t == LOAD) {
            chestScale = CHEST;
            chestSpin = 0f;
            pushChest(3);
            play(Sound.BLOCK_WOOD_PLACE, 1.2f, 0.6f);
            play(Sound.BLOCK_CHEST_CLOSE, 1f, 0.8f);
            world.spawnParticle(Particle.BLOCK, at(0f, 0.2f, 0f), 30, 0.8, 0.1, 0.8, 0,
                    Material.PODZOL.createBlockData(), true);
        }
        for (int i = 0; i < 4; i++) {
            if (t == TORCHES + i * 7) {
                placeTorch(i);
            }
        }
        if (t == TORCHES + 30) {
            pushOutline(0f, 12);
        }
        if (t >= READY) {
            for (BlockDisplay d : outline) {
                kill(d);
            }
            outline.clear();
            ready();
        }
    }

    private void placeTorch(int i) {
        float a = i * (float) Math.PI * 0.5f;
        Vector3f at = new Vector3f((float) Math.sin(a) * 1.9f, 0f, (float) Math.cos(a) * 1.9f);
        BlockDisplay torch = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setBlock(Material.TORCH.createBlockData());
            prepare(d);
        });
        spawned.add(torch);
        torches[i] = torch;
        torch.setTransformation(new Transformation(new Vector3f(at).add(-0.5f, 0f, -0.5f), new Quaternionf(), new Vector3f(1f), new Quaternionf()));
        Location l = at(at.x, 0.6f, at.z);
        world.playSound(l, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        world.playSound(l, Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        world.spawnParticle(Particle.FLAME, l.clone().add(0, 0.2, 0), 6, 0.05, 0.1, 0.05, 0.01);
        world.spawnParticle(Particle.SMOKE, l.clone().add(0, 0.3, 0), 4, 0.05, 0.1, 0.05, 0.01);
        placeLight(at.x, at.z, 14);
    }

    /** Torches really light the podzol at night: light blocks only in air, only while the chest stands. */
    private final List<Location> lights = new ArrayList<>();

    private void placeLight(float x, float z, int level) {
        Location l = at(x, 0.5f, z);
        if (!l.getBlock().getType().isAir()) {
            return;
        }
        BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof Light light) {
            light.setLevel(level);
        }
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(l) < 96 * 96) {
                p.sendBlockChange(l, data);
            }
        }
        lights.add(l);
    }

    private void ready() {
        phase = Phase.READY;
        readyTick = 0;
        hitbox = world.spawn(anchor, Interaction.class, interaction -> {
            interaction.setInteractionWidth(2.2f);
            interaction.setInteractionHeight(2.0f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
        });
        spawned.add(hitbox);
        label = world.spawn(at(0f, 2.9f, 0f), TextDisplay.class, text -> {
            text.setPersistent(false);
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(true);
            text.setLineWidth(220);
            text.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            text.setViewRange(1.0f);
        });
        spawned.add(label);
        updateLabel();
        play(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.7f);
        for (UUID owner : unclaimed.keySet()) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.component("&aBonus Chest &8» &fYour share is inside. &7Right-click the chest."));
            }
        }
        if (unclaimed.isEmpty()) {
            unloadAt = 100;
        }
    }

    private void tickReady() {
        readyTick++;
        if (readyTick % 20 == 0) {
            updateLabel();
        }
        if (readyTick % 8 == 0) {
            for (BlockDisplay torch : torches) {
                if (torch != null && torch.isValid()) {
                    Location flame = torch.getLocation().clone().add(torch.getTransformation().getTranslation().x + 0.5,
                            0.75, torch.getTransformation().getTranslation().z + 0.5);
                    world.spawnParticle(Particle.SMOKE, flame, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }
        if (readyTick % 30 == 0) {
            dust(at(0f, CHEST * 0.9f, 0f), Color.fromRGB(255, 220, 120), 4, 0.5);
        }
        if (readyTick % 600 == 0 && readyTick < CLAIM_TICKS) {
            remind((CLAIM_TICKS - readyTick) / 20);
        }
        if (readyTick >= CLAIM_TICKS) {
            deliverRemaining(true);
            beginUnload();
            return;
        }
        if (unloadAt >= 0 && readyTick >= unloadAt) {
            beginUnload();
        }
    }

    private void beginUnload() {
        phase = Phase.UNLOADING;
        unloadTick = 0;
        kill(hitbox);
        hitbox = null;
        kill(label);
        label = null;
    }

    private void tickUnloading() {
        int t = unloadTick++;
        for (int i = 0; i < 4; i++) {
            if (t == i * 8) {
                BlockDisplay torch = torches[i];
                if (torch != null && torch.isValid()) {
                    Location l = torch.getLocation().clone().add(torch.getTransformation().getTranslation().x + 0.5,
                            0.6, torch.getTransformation().getTranslation().z + 0.5);
                    world.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.4f);
                    world.spawnParticle(Particle.SMOKE, l, 8, 0.05, 0.1, 0.05, 0.01);
                    kill(torch);
                }
                torches[i] = null;
            }
        }
        if (t == 34) {
            buildOutline();
            pushOutline(1f, 6);
            play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        }
        if (t > 40 && t <= 60) {
            float f = 1f - smooth((t - 40) / 20f);
            chestScale = CHEST * f;
            chestSpin = (1f - f) * -6f;
            pushChest(2);
            pushOutline(f, 2);
        }
        if (t >= UNLOAD) {
            play(Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.4f);
            finish();
        }
    }

    /* ================================================================== claim */

    private void claim(Player player) {
        if (phase != Phase.READY) {
            player.sendActionBar(TextUtil.component(phase == Phase.UNLOADING
                    ? "&7The chest is unloading."
                    : "&7The chest is still generating…"));
            return;
        }
        List<ItemStack> share = unclaimed.remove(player.getUniqueId());
        if (share == null) {
            player.sendActionBar(TextUtil.component(claimed.contains(player.getUniqueId())
                    ? "&7You already took your share."
                    : "&7The Bonus Chest holds nothing for you."));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.6f, 1f);
            return;
        }
        claimed.add(player.getUniqueId());
        play(Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        give(player, share);
        flyTo(player, share);
        Bukkit.getScheduler().runTaskLater(plugin, () -> play(Sound.BLOCK_CHEST_CLOSE, 0.9f, 1f), 16L);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        if (unclaimed.isEmpty()) {
            unloadAt = readyTick + 60;
        }
        updateLabel();
    }

    private void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            LootService.giveLootItem(player, item);
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();
            player.sendMessage(TextUtil.component("&aBonus Chest &8» &f" + name
                    + (item.getAmount() > 1 ? " &7x" + item.getAmount() : "")));
        }
    }

    /** The share hops out of the chest and flies to its owner (cosmetic copies). */
    private void flyTo(Player player, List<ItemStack> share) {
        int n = 0;
        for (ItemStack item : share) {
            if (item == null || item.getType().isAir() || n >= 4) {
                continue;
            }
            int delayTicks = n * 3;
            n++;
            ItemStack icon = new ItemStack(item.getType());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world == null) {
                    return;
                }
                ItemDisplay d = world.spawn(anchor, ItemDisplay.class, x -> {
                    x.setItemStack(icon);
                    x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    prepare(x);
                });
                spawned.add(d);
                d.setTransformation(itemAt(new Vector3f(0f, CHEST * 0.7f, 0f), 0.6f));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(6);
                        d.setTransformation(itemAt(new Vector3f(0f, CHEST * 1.5f, 0f), 0.8f));
                    }
                }, 1L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (d.isValid() && player.isOnline() && player.getWorld() == world) {
                        Location c = player.getLocation().add(0, 1.1, 0);
                        Vector3f to = new Vector3f((float) (c.getX() - anchor.getX()), (float) (c.getY() - anchor.getY()), (float) (c.getZ() - anchor.getZ()));
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(8);
                        d.setTransformation(itemAt(to, 0.3f));
                    }
                }, 8L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> kill(d), 17L);
            }, delayTicks);
        }
    }

    private static Transformation itemAt(Vector3f at, float s) {
        return new Transformation(new Vector3f(at), new Quaternionf(), new Vector3f(s), new Quaternionf());
    }

    /** Expiry / disable: online owners receive their share, offline owners' share drops at the chest. */
    private void deliverRemaining(boolean announce) {
        for (Map.Entry<UUID, List<ItemStack>> entry : unclaimed.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner != null && owner.isOnline()) {
                if (announce) {
                    owner.sendMessage(TextUtil.component("&aBonus Chest &8» &7Your share was sent after you."));
                }
                give(owner, entry.getValue());
            } else if (world != null) {
                Location drop = at(0f, 1.2f, 0f);
                for (ItemStack item : entry.getValue()) {
                    if (item != null && !item.getType().isAir()) {
                        world.dropItemNaturally(drop, item);
                    }
                }
            }
            claimed.add(entry.getKey());
        }
        unclaimed.clear();
    }

    private void remind(int seconds) {
        for (UUID owner : unclaimed.keySet()) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.component("&aBonus Chest &8» &7Your share is still waiting… &f" + seconds + "s"));
            }
        }
    }

    private void updateLabel() {
        if (label == null || !label.isValid()) {
            return;
        }
        int left = Math.max(0, (CLAIM_TICKS - readyTick) / 20);
        label.text(TextUtil.component(bossName
                + "\n&a&lBonus Chest"
                + "\n&7Right-click to take your share"
                + "\n&f" + unclaimed.size() + "&7/&f" + shares + " &7waiting &8· &f"
                + String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60)));
    }

    /* ================================================================== build */

    /** A structure-block style bounding box around the chest: twelve thin white edges. */
    private void buildOutline() {
        for (BlockDisplay d : outline) {
            kill(d);
        }
        outline.clear();
        for (int i = 0; i < 12; i++) {
            BlockDisplay d = world.spawn(anchor, BlockDisplay.class, x -> {
                x.setBlock(Material.WHITE_CONCRETE.createBlockData());
                prepare(x);
                x.setGlowing(true);
                x.setGlowColorOverride(OUTLINE_COLOR);
            });
            spawned.add(d);
            outline.add(d);
        }
        pushOutline(0f, 0);
    }

    private void pushOutline(float f, int interp) {
        if (outline.isEmpty()) {
            return;
        }
        float h = 1.4f;
        float w = 1.3f;
        float y0 = 0.02f;
        float y1 = y0 + h * Math.max(0.001f, f);
        float[][] corners = {{-w, -w}, {w, -w}, {w, w}, {-w, w}};
        int k = 0;
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            push(outline.get(k++), edge(new Vector3f(a[0], y0, a[1]), new Vector3f(b[0], y0, b[1]), f), interp);
            push(outline.get(k++), edge(new Vector3f(a[0], y1, a[1]), new Vector3f(b[0], y1, b[1]), f), interp);
            push(outline.get(k++), edge(new Vector3f(a[0], y0, a[1]), new Vector3f(a[0], y1, a[1]), f), interp);
        }
    }

    private static Transformation edge(Vector3f a, Vector3f b, float f) {
        float t = 0.05f * Math.max(0.01f, Math.min(1f, f * 3f));
        Vector3f min = new Vector3f(Math.min(a.x, b.x) - t * 0.5f, Math.min(a.y, b.y) - t * 0.5f, Math.min(a.z, b.z) - t * 0.5f);
        Vector3f size = new Vector3f(Math.abs(b.x - a.x) + t, Math.abs(b.y - a.y) + t, Math.abs(b.z - a.z) + t);
        return new Transformation(min, new Quaternionf(), size, new Quaternionf());
    }

    private void pushChest(int interp) {
        if (chest == null || !chest.isValid()) {
            return;
        }
        float s = Math.max(0.001f, chestScale);
        push(chest, new Transformation(new Vector3f(0f, s * 0.5f, 0f), new Quaternionf().rotateY(chestSpin),
                new Vector3f(s), new Quaternionf()), interp);
    }

    /* ================================================================== helpers */

    private void prepare(Display d) {
        d.setPersistent(false);
        d.setGravity(false);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(2.0f);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f), new Quaternionf()));
    }

    private static void push(Display d, Transformation t, int interp) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, interp));
        d.setTransformation(t);
    }

    private Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    private void play(Sound sound, float volume, float pitch) {
        world.playSound(at(0f, 0.8f, 0f), sound, SoundCategory.BLOCKS, volume, pitch);
    }

    private void dust(Location at, Color color, int count, double spread) {
        world.spawnParticle(Particle.DUST, at, count, spread, spread * 0.5, spread, 0, new Particle.DustOptions(color, 1.0f), true);
    }

    private void actionBar(String text) {
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) < 80 * 80) {
                p.sendActionBar(TextUtil.component(text));
            }
        }
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static void kill(Entity e) {
        if (e != null && e.isValid()) {
            e.remove();
        }
    }

    private void finish() {
        if (phase == Phase.DONE) {
            return;
        }
        phase = Phase.DONE;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Entity e : spawned) {
            kill(e);
        }
        spawned.clear();
        outline.clear();
        if (world != null) {
            for (Location l : lights) {
                BlockData real = l.getBlock().getBlockData();
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(l) < 160 * 160) {
                        p.sendBlockChange(l, real);
                    }
                }
            }
        }
        lights.clear();
        LIVE.remove(this);
        if (onFinish != null) {
            try {
                onFinish.run();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Bonus Chest finish hook failed: " + ex.getMessage());
            }
        }
    }
}
