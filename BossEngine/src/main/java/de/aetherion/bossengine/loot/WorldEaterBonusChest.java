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
import java.util.concurrent.ThreadLocalRandom;

/**
 * THE SEED VAULT: the Bonus Chest of a world that just generated again, and Nihil's loot.
 *
 * <p>Vanilla puts a Bonus Chest next to spawn in a new world. Here the new world is the one Nihil
 * ate, so its first gift is built from the world itself. Over a pool of void, inside an F3+G chunk
 * border, a miniature chunk of the Last Seed generates the way the game builds one: bedrock,
 * deepslate, stone with its ores, dirt, and a lid of grass split in four. Four end rods, the
 * torches of the End, are placed around it one by one. Then it blooms: the grass petals fold open,
 * a beam of starlight goes up into the night Nihil left behind, the eye it could not eat rises out
 * of the sculk heart, and a sapling grows on the open soil. The last seed.
 *
 * <p><b>Claim.</b> Same contract as the reliquary and the music box: {@link LootService#grantToChest}
 * pays XP and the recap at death and hands the item bundles here. Each player right-clicks the vault
 * (an {@link Interaction}) for their own share: the heart pulses and a stream of stars carries the
 * share over. Shares still unclaimed at expiry are delivered: online players get them in their
 * inventory, offline players' shares drop at the vault. Nothing is lost, including on plugin
 * disable ({@link #clearAll()}).
 *
 * <p><b>Unload.</b> The rods go out, the petals close, and the chunk de-generates top-down into the
 * void pool, which closes behind it.
 *
 * <p>Displays and an Interaction only: no blocks are placed (the rods' light is client-side).
 */
public final class WorldEaterBonusChest {

    private static final List<WorldEaterBonusChest> LIVE = new ArrayList<>();

    /* Timeline (ticks from the start of generation). */
    private static final int LAYERS_AT = 8;
    private static final int LAYER_STEP = 6;
    private static final int RODS_AT = 44;
    private static final int BLOOM = 80;
    private static final int READY = BLOOM + 22;
    private static final int CLAIM_TICKS = 20 * 180;
    private static final int UNLOAD = 70;

    /* The vault, in blocks. Origin: floor center. */
    private static final float S = 1.5f;
    private static final float HOVER = 0.55f;
    private static final float[] LAYER_H = {0.22f, 0.3f, 0.34f, 0.28f};
    private static final Material[] LAYER_M = {Material.BEDROCK, Material.DEEPSLATE, Material.STONE, Material.DIRT};
    private static final float LID_H = 0.26f;
    private static final float ROD_R = 2.0f;

    private static final Color VOID = Color.fromRGB(150, 60, 255);
    private static final Color STAR = Color.fromRGB(220, 235, 255);
    private static final Color CHUNK_YELLOW = Color.fromRGB(255, 235, 60);
    private static final Color CHUNK_CYAN = Color.fromRGB(70, 200, 255);
    private static final Color SEED_GREEN = Color.fromRGB(120, 230, 90);

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
    private final BlockDisplay[] layers = new BlockDisplay[4];
    private final float[] layerGrow = new float[4];
    private final BlockDisplay[] petals = new BlockDisplay[4];
    private final BlockDisplay[] ores = new BlockDisplay[3];
    private final BlockDisplay[] rods = new BlockDisplay[4];
    private final float[] rodGrow = new float[4];
    private final List<BlockDisplay> pool = new ArrayList<>();
    private BlockDisplay heart;
    private ItemDisplay eye;
    private BlockDisplay sapling;
    private BlockDisplay beamCore;
    private BlockDisplay beamShell;
    private Interaction hitbox;
    private TextDisplay label;
    private BukkitTask task;

    private Phase phase = Phase.WAITING;
    private int delay;
    private int tick;
    private int readyTick;
    private int unloadTick;
    private int unloadAt = -1;
    private int clock;

    /* Pose. */
    private float poolSize;
    private float lift;
    private float petalOpen;
    private float eyeRise;
    private float saplingGrow;
    private float rodSpin;
    private float pulse;

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
     * Registers the vault now (the bundles are safe from this moment) and starts generating it
     * after {@code delayTicks}. {@code onFinish} runs once it has unloaded.
     */
    public static WorldEaterBonusChest place(JavaPlugin plugin, Location anchor, String bossName,
                                             Map<UUID, List<ItemStack>> bundles, int delayTicks, Runnable onFinish) {
        WorldEaterBonusChest chest = new WorldEaterBonusChest(plugin, anchor, bossName, bundles, delayTicks, onFinish);
        LIVE.add(chest);
        chest.task = Bukkit.getScheduler().runTaskTimer(plugin, chest::tick, 1L, 1L);
        return chest;
    }

    /** @return true when {@code clicked} is a Seed Vault (the click is handled either way) */
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

    /** Plugin disable: every share still in a vault is delivered, then the vaults vanish. */
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
        clock++;
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
        if (phase != Phase.DONE && phase != Phase.WAITING && clock % 2 == 0) {
            pose(3);
        }
    }

    private void tickGenerating() {
        int t = tick++;
        if (t == 0) {
            build();
            play(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.6f);
            play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            actionBar("&7Generating structure: &a&lBonus Chest");
        }
        // The void pool opens and the chunk border draws itself.
        poolSize = smooth(t / 12f);
        pushOutline(smooth(t / 14f), 2);
        // The chunk generates from bedrock up, like the world did.
        for (int i = 0; i < 4; i++) {
            int at = LAYERS_AT + i * LAYER_STEP;
            if (t == at) {
                Material m = LAYER_M[i];
                play(m.createBlockData().getSoundGroup().getPlaceSound(), 1.2f, 0.7f + i * 0.08f);
                play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.6f + i * 0.15f);
                dust(at(0f, HOVER + layerTop(i), 0f), CHUNK_CYAN, 10, 0.8);
            }
            layerGrow[i] = smooth((t - at) / 5f);
        }
        int lidAt = LAYERS_AT + 4 * LAYER_STEP;
        if (t == lidAt) {
            play(Sound.BLOCK_GRASS_PLACE, 1.4f, 0.8f);
            play(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at(0f, HOVER + layerTop(4), 0f), 14, 0.6, 0.1, 0.6, 0);
        }
        if (t >= lidAt) {
            lift = smooth((t - lidAt) / 10f);
        }
        // The torches of the End, placed one by one.
        for (int i = 0; i < 4; i++) {
            int at = RODS_AT + i * 6;
            if (t == at) {
                Location l = rodTip(i);
                world.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.BLOCKS, 1f, 0.8f + i * 0.15f);
                world.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.4f, 1.6f + i * 0.1f);
                world.spawnParticle(Particle.END_ROD, l, 10, 0.05, 0.2, 0.05, 0.03);
                placeLight(i, 13);
            }
            rodGrow[i] = smooth((t - at) / 6f);
        }
        if (t == RODS_AT + 30) {
            pushOutline(0f, 14);
        }
        if (t == BLOOM) {
            bloom();
        }
        if (t >= BLOOM) {
            petalOpen = smooth((t - BLOOM) / 14f);
            eyeRise = smooth((t - BLOOM - 4) / 16f);
            saplingGrow = smooth((t - BLOOM - 8) / 12f);
            float beam = t < BLOOM + 40 ? smooth((t - BLOOM) / 6f) : 0f;
            pushBeam(beam, t < BLOOM + 40 ? 3 : 20);
            if (t < BLOOM + 30 && t % 2 == 0) {
                Location heartAt = at(0f, HOVER + layerTop(3) + 0.3f, 0f);
                world.spawnParticle(Particle.END_ROD, heartAt, 6, 0.3, 0.2, 0.3, 0.12);
            }
        }
        if (t >= READY) {
            for (BlockDisplay d : outline) {
                kill(d);
            }
            outline.clear();
            ready();
        }
    }

    /** The reward beat: the petals open, starlight goes up, the eye rises, the seed sprouts. */
    private void bloom() {
        play(Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.8f);
        play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
        play(Sound.BLOCK_END_PORTAL_SPAWN, 0.35f, 1.6f);
        play(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        Location top = at(0f, HOVER + layerTop(4), 0f);
        world.spawnParticle(Particle.END_ROD, top, 60, 0.2, 0.4, 0.2, 0.25);
        world.spawnParticle(Particle.HAPPY_VILLAGER, top, 30, 0.9, 0.3, 0.9, 0);
        world.spawnParticle(Particle.DUST, top, 40, 1.2, 0.6, 1.2, 0, new Particle.DustOptions(SEED_GREEN, 1.4f), true);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) < 64 * 64) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.RECORDS, 0.7f, 0.6f);
            }
        }
    }

    /** The rods light the podzol at night: light blocks only in air, client-side, only while the vault stands. */
    private final List<Location> lights = new ArrayList<>();

    private void placeLight(int i, int level) {
        Location l = rodTip(i).add(0, -0.8, 0);
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
            interaction.setInteractionHeight(2.6f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
        });
        spawned.add(hitbox);
        label = world.spawn(at(0f, 3.6f, 0f), TextDisplay.class, text -> {
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
        for (UUID owner : unclaimed.keySet()) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.component("&aBonus Chest &8» &fThe last seed kept your share. &7Right-click the vault."));
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
        if (readyTick % 5 == 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Location heartAt = at(0f, HOVER + layerTop(3) + 0.35f, 0f);
            world.spawnParticle(Particle.END_ROD, heartAt.clone().add(r.nextDouble(-0.4, 0.4), r.nextDouble(0, 1.2), r.nextDouble(-0.4, 0.4)),
                    1, 0, 0.02, 0, 0.01);
            world.spawnParticle(Particle.SCULK_SOUL, heartAt, 1, 0.3, 0.1, 0.3, 0.01);
        }
        if (readyTick % 40 == 0) {
            world.playSound(at(0f, 1.5f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f,
                    0.5f + ThreadLocalRandom.current().nextFloat() * 0.8f);
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
            if (t == i * 6) {
                Location l = rodTip(i);
                world.playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.4f, 1.8f);
                world.spawnParticle(Particle.SMOKE, l, 8, 0.05, 0.1, 0.05, 0.01);
            }
            rodGrow[i] = Math.min(rodGrow[i], 1f - smooth((t - i * 6) / 5f));
        }
        if (t >= 20) {
            petalOpen = 1f - smooth((t - 20) / 10f);
            eyeRise = 1f - smooth((t - 20) / 10f);
            saplingGrow = 1f - smooth((t - 20) / 8f);
        }
        if (t == 30) {
            play(Sound.BLOCK_GRASS_BREAK, 1f, 0.7f);
        }
        // The chunk de-generates top-down into the pool.
        for (int i = 3; i >= 0; i--) {
            int at = 34 + (3 - i) * 5;
            layerGrow[i] = Math.min(layerGrow[i], 1f - smooth((t - at) / 5f));
            if (t == at) {
                play(LAYER_M[i].createBlockData().getSoundGroup().getBreakSound(), 0.8f, 0.7f);
            }
        }
        if (t >= 30) {
            lift = Math.min(lift, 1f - smooth((t - 30) / 20f));
        }
        if (t >= 56) {
            poolSize = 1f - smooth((t - 56) / 12f);
        }
        if (t >= UNLOAD) {
            play(Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 1.4f);
            finish();
        }
    }

    /* ================================================================== claim */

    private void claim(Player player) {
        if (phase != Phase.READY) {
            player.sendActionBar(TextUtil.component(phase == Phase.UNLOADING
                    ? "&7The vault is closing."
                    : "&7The vault is still generating…"));
            return;
        }
        List<ItemStack> share = unclaimed.remove(player.getUniqueId());
        if (share == null) {
            player.sendActionBar(TextUtil.component(claimed.contains(player.getUniqueId())
                    ? "&7You already took your share."
                    : "&7The vault holds nothing for you."));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.BLOCKS, 0.8f, 0.5f);
            return;
        }
        claimed.add(player.getUniqueId());
        // The heart pulses and a stream of stars carries the share over.
        pulse = 1f;
        play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
        play(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.4f);
        play(Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 0.8f);
        starStream(player);
        give(player, share);
        flyTo(player, share);
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
                d.setTransformation(itemAt(new Vector3f(0f, HOVER + layerTop(3) + 0.4f, 0f), 0.6f));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(6);
                        d.setTransformation(itemAt(new Vector3f(0f, HOVER + layerTop(4) + 1.6f, 0f), 0.8f));
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

    /** A ribbon of end-rod stars from the heart to the claimer, over a few ticks. */
    private void starStream(Player player) {
        for (int k = 0; k < 6; k++) {
            int delayTicks = k * 2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world == null || !player.isOnline() || player.getWorld() != world) {
                    return;
                }
                Location from = at(0f, HOVER + layerTop(3) + 0.8f, 0f);
                Location to = player.getLocation().add(0, 1.1, 0);
                org.bukkit.util.Vector d = to.toVector().subtract(from.toVector());
                for (int i = 0; i <= 10; i++) {
                    double u = i / 10.0;
                    Location p = from.clone().add(d.clone().multiply(u)).add(0, Math.sin(u * Math.PI) * 0.8, 0);
                    world.spawnParticle(Particle.END_ROD, p, 1, 0.03, 0.03, 0.03, 0.0);
                }
                world.spawnParticle(Particle.DUST, to, 6, 0.25, 0.35, 0.25, 0, new Particle.DustOptions(SEED_GREEN, 1.0f), true);
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

    private void build() {
        // The void pool: an octagon of night with a violet rim.
        for (int i = 0; i < 2; i++) {
            pool.add(block(Material.BLACK_CONCRETE, null, 15));
        }
        for (int i = 0; i < 2; i++) {
            pool.add(block(Material.PURPLE_STAINED_GLASS, VOID, 15));
        }
        for (int i = 0; i < 4; i++) {
            layers[i] = block(LAYER_M[i], null, 15);
        }
        // Ores showing on the stone layer's faces: this is a real chunk.
        Material[] oreM = {Material.DIAMOND_ORE, Material.IRON_ORE, Material.EMERALD_ORE};
        for (int i = 0; i < 3; i++) {
            ores[i] = block(oreM[i], null, 15);
        }
        heart = block(Material.SCULK_CATALYST, CHUNK_CYAN, 15);
        for (int i = 0; i < 4; i++) {
            petals[i] = block(Material.GRASS_BLOCK, null, 15);
        }
        sapling = block(Material.OAK_SAPLING, null, 15);
        eye = world.spawn(anchor, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.ENDER_EYE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            prepare(d);
            d.setGlowing(true);
            d.setGlowColorOverride(VOID);
        });
        spawned.add(eye);
        for (int i = 0; i < 4; i++) {
            rods[i] = block(Material.END_ROD, STAR, 15);
        }
        beamCore = block(Material.WHITE_STAINED_GLASS, STAR, 15);
        beamShell = block(Material.LIGHT_BLUE_STAINED_GLASS, CHUNK_CYAN, 15);
        // The F3+G chunk border it generates inside: yellow posts, cyan rails.
        for (int i = 0; i < 12; i++) {
            outline.add(block(Material.WHITE_CONCRETE, i % 3 == 2 ? CHUNK_YELLOW : CHUNK_CYAN, 15));
        }
        pushOutline(0f, 0);
        pose(0);
    }

    private BlockDisplay block(Material m, Color glow, int brightness) {
        BlockDisplay d = world.spawn(anchor, BlockDisplay.class, x -> {
            x.setBlock(m.createBlockData());
            prepare(x);
            x.setBrightness(new Display.Brightness(brightness, brightness));
            if (glow != null) {
                x.setGlowing(true);
                x.setGlowColorOverride(glow);
            }
        });
        spawned.add(d);
        return d;
    }

    /** Height of the top of layer {@code i} above the vault's bottom (4 = top of the lid). */
    private static float layerTop(int i) {
        float y = 0f;
        for (int k = 0; k < Math.min(i + 1, 4); k++) {
            y += LAYER_H[k];
        }
        return i >= 4 ? y + LID_H : y;
    }

    private float bob() {
        return phase == Phase.READY ? 0.08f * (float) Math.sin(clock * 0.06f) : 0f;
    }

    private Location rodTip(int i) {
        float a = rodSpin + i * (float) Math.PI * 0.5f + (float) Math.PI * 0.25f;
        return at((float) Math.sin(a) * ROD_R, 1.9f, (float) Math.cos(a) * ROD_R);
    }

    /** Every piece, from the pose fields. */
    private void pose(int interp) {
        float base = HOVER * lift + bob();
        float pulseS = 1f + 0.12f * pulse;
        pulse *= 0.8f;
        // Pool.
        float pr = 3.1f * Math.max(0.001f, poolSize);
        for (int i = 0; i < 4; i++) {
            boolean rim = i >= 2;
            float s = rim ? pr + 0.35f : pr;
            float yaw = (i % 2) * (float) Math.PI * 0.25f;
            push(pool.get(i), box(new Vector3f(0f, rim ? 0.015f : 0.03f, 0f), new Vector3f(s * 0.83f, rim ? 0.02f : 0.025f, s * 0.83f),
                    new Quaternionf().rotateY(yaw)), interp);
        }
        // Chunk layers.
        float y = base;
        for (int i = 0; i < 4; i++) {
            float g = Math.max(0.001f, layerGrow[i]);
            float h = LAYER_H[i] * g;
            push(layers[i], box(new Vector3f(0f, y + h * 0.5f, 0f), new Vector3f(S * g, h, S * g), new Quaternionf()), interp);
            if (i == 2) {
                float[][] faces = {{S * 0.5f, -0.3f}, {-0.35f, S * 0.5f}, {0.2f, -S * 0.5f}};
                for (int k = 0; k < 3; k++) {
                    boolean xFace = k == 0;
                    Vector3f c = xFace ? new Vector3f(faces[k][0] * g, y + h * 0.45f, faces[k][1] * g)
                            : new Vector3f(faces[k][0] * g, y + h * 0.5f, faces[k][1] * g);
                    push(ores[k], box(c, new Vector3f(0.28f * g, 0.2f * g, 0.28f * g), new Quaternionf()), interp);
                }
            }
            y += LAYER_H[i] * layerGrow[i];
        }
        float soil = y;
        // Heart of sculk and night on the soil, visible once the petals open.
        float hs = 0.5f * Math.max(0.001f, Math.min(layerGrow[3], 1f)) * pulseS;
        push(heart, box(new Vector3f(0f, soil + hs * 0.5f - 0.02f, 0f), new Vector3f(hs), new Quaternionf().rotateY(0.785f)), interp);
        // Grass petals: four quarters of the lid, hinged on the outer corners.
        float lidGrow = smooth((tick - (LAYERS_AT + 4 * LAYER_STEP)) / 5f);
        if (phase != Phase.GENERATING) {
            lidGrow = Math.min(1f, layerGrow[3] * 1.5f);
        }
        float q = S * 0.5f;
        for (int i = 0; i < 4; i++) {
            float sx = (i == 0 || i == 3) ? 1f : -1f;
            float sz = (i == 0 || i == 1) ? 1f : -1f;
            float g = Math.max(0.001f, lidGrow);
            Vector3f center = new Vector3f(sx * q * 0.5f * g, soil + LID_H * 0.5f * g, sz * q * 0.5f * g);
            Vector3f size = new Vector3f(q * g, LID_H * g, q * g);
            Vector3f pivot = new Vector3f(sx * q * g, soil, sz * q * g);
            Vector3f axis = new Vector3f(-sz, 0f, sx).normalize();
            Quaternionf rot = new Quaternionf().rotateAxis(-petalOpen * 2.1f, axis.x, axis.y, axis.z);
            Vector3f c = rot.transform(new Vector3f(center).sub(pivot)).add(pivot);
            push(petals[i], box(c, size, rot), interp);
        }
        // The seed.
        float sg = Math.max(0.001f, saplingGrow) * 0.9f;
        push(sapling, box(new Vector3f(0.22f, soil + hs + sg * 0.5f - 0.05f, 0.22f), new Vector3f(sg), new Quaternionf()), interp);
        // The eye it could not eat.
        float er = Math.max(0.001f, eyeRise);
        Vector3f eyeAt = new Vector3f(0f, soil + 0.4f + er * 1.3f + bob(), 0f);
        push(eye, new Transformation(eyeAt, new Quaternionf().rotateY(clock * 0.08f), new Vector3f(1.5f * er * pulseS), new Quaternionf()), interp);
        // Torches of the End, slowly circling.
        if (phase == Phase.READY) {
            rodSpin += 0.01f;
        }
        for (int i = 0; i < 4; i++) {
            float a = rodSpin + i * (float) Math.PI * 0.5f + (float) Math.PI * 0.25f;
            float g = Math.max(0.001f, rodGrow[i]);
            float bobR = 0.1f * (float) Math.sin(clock * 0.08f + i * 1.6f);
            Vector3f c = new Vector3f((float) Math.sin(a) * ROD_R, 1.4f + bobR, (float) Math.cos(a) * ROD_R);
            push(rods[i], box(c, new Vector3f(0.9f * g, 1.1f * g, 0.9f * g), new Quaternionf().rotateY(a)), interp);
        }
    }

    /** A vertical beam of starlight out of the open vault. */
    private void pushBeam(float f, int interp) {
        float top = HOVER + layerTop(4);
        float h = 40f * Math.max(0.001f, f);
        float w = 0.35f * Math.max(0.001f, f);
        push(beamCore, box(new Vector3f(0f, top + h * 0.5f, 0f), new Vector3f(w, h, w), new Quaternionf()), interp);
        push(beamShell, box(new Vector3f(0f, top + h * 0.5f, 0f), new Vector3f(w * 2.4f, h, w * 2.4f), new Quaternionf().rotateY(0.785f)), interp);
    }

    /** The chunk border it generates inside: four posts and the rails top and bottom. */
    private void pushOutline(float f, int interp) {
        if (outline.isEmpty()) {
            return;
        }
        float h = 3.0f;
        float w = 1.6f;
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

    /** A block display box of {@code size} centered on {@code center}, rotated about its center. */
    private static Transformation box(Vector3f center, Vector3f size, Quaternionf rot) {
        Vector3f corner = rot.transform(new Vector3f(size).mul(-0.5f)).add(center);
        return new Transformation(corner, new Quaternionf(rot), new Vector3f(size), new Quaternionf());
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
