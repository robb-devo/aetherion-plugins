package de.aetherion.bossengine.loot;

import de.aetherion.bossengine.instance.saint.MusicBox;
import de.aetherion.bossengine.instance.saint.SaintStage;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SERAPHINE'S MUSIC BOX: the Hanging Saint's loot chest.
 *
 * <p><b>Arrival.</b> A moment after the curtain call, the music box that played her lullaby all
 * fight is lowered from the flies on four golden threads tied to a marionette control lost in the
 * clouds. It sways as it comes down, the lullaby growing louder, and settles on the gold compass at
 * stage center. The threads let go and are reeled back up. The winding key turns three clicks, the
 * lid swings open on crimson velvet and a little mirror, and a porcelain ballerina springs up on a
 * gold spindle: Seraphine in miniature, halo and kintsugi seam included, arms in fifth, pirouetting
 * to her own tune with no strings at all.
 *
 * <p><b>Claim.</b> Same contract as a reliquary: {@link LootService#grantToChest} pays XP and the
 * recap at death and hands the item bundles here. Each player right-clicks the box (an
 * {@link Interaction}) for their own share only. A gold ribbon carries it over, the next note of
 * the lullaby plays, and the dancer turns to them and dips. Shares still unclaimed at expiry are
 * delivered: online players get them in their inventory, offline players' shares drop at the box.
 * Nothing is lost, including on plugin disable ({@link #clearAll()}).
 *
 * <p><b>Dismantle.</b> Once every share is taken (or on expiry) the spring runs down, each note
 * later and flatter, the dancer slows, turns to the house and curtsies. One golden thread comes
 * down for her and lifts her into the clouds. The lid slams, the kintsugi seams flare, and the box
 * comes apart into porcelain shards that rise like ash, the way she did.
 *
 * <p>Displays and an Interaction only: no blocks are placed. While a box is up it holds the stage
 * ({@link SaintStage#hold}), so a temporary set is not struck from under it.
 */
public final class SeraphineMusicBox {

    private static final List<SeraphineMusicBox> LIVE = new ArrayList<>();

    private static final float PI = (float) Math.PI;
    private static final Color THREAD_GOLD = Color.fromRGB(255, 214, 110);
    private static final Color DUST_GOLD = Color.fromRGB(255, 205, 90);
    private static final Color DUST_WHITE = Color.fromRGB(255, 250, 240);

    /* The box, in blocks. Origin: stage floor center. +Z faces the house. */
    private static final float W = 2.4f;
    private static final float D = 1.7f;
    private static final float PLINTH = 0.18f;
    private static final float BODY_MID = PLINTH + 0.55f;
    private static final float BODY_TOP = PLINTH + 1.1f;
    private static final float KEY_Y = BODY_MID;
    private static final float LID_OPEN = 1.9f;
    /* The dancer, in figure units (about 1.1 tall), scaled by FIG. */
    private static final float FIG = 1.5f;
    private static final float HIP = 0.55f;
    private static final float RISE = 0.28f;

    /* Timeline, in ticks from the start of the descent. */
    private static final float DROP = 30f;
    private static final float FLIES = 36f;
    private static final int DESCEND = 110;
    private static final int RELEASE = DESCEND + 8;
    private static final int WIND = DESCEND + 14;
    private static final int OPEN = WIND + 22;
    private static final int READY = OPEN + 16;
    private static final int CLAIM_TICKS = 20 * 180;
    private static final int FINALE = 206;

    private enum Phase { WAITING, ARRIVING, PLAYING, FINALE, DONE }

    private enum Group { BODY, SEAM, LID, KEY, SPINDLE, LOWER, UPPER }

    private static final Set<Group> CASE = EnumSet.of(Group.BODY, Group.SEAM, Group.LID, Group.KEY, Group.SPINDLE);
    private static final Set<Group> DANCER = EnumSet.of(Group.SPINDLE, Group.LOWER, Group.UPPER);

    private static final class Part {
        final BlockDisplay display;
        final Group group;
        final Matrix4f local;
        final Matrix4f freeMatrix = new Matrix4f();
        boolean free;

        Part(BlockDisplay display, Group group, Matrix4f local) {
            this.display = display;
            this.group = group;
            this.local = local;
        }
    }

    private final JavaPlugin plugin;
    private final Location anchor;
    private final World world;
    private final String bossName;
    private final Runnable onFinish;
    private final Map<UUID, List<ItemStack>> unclaimed = new LinkedHashMap<>();
    private final Set<UUID> claimed = new HashSet<>();
    private final int shares;
    private final List<Part> parts = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final BlockDisplay[] lines = new BlockDisplay[4];
    private final Vector3f[] lineTop = new Vector3f[4];
    private final Vector3f[] lineCorner = new Vector3f[4];
    private final List<BlockDisplay> flyBar = new ArrayList<>();
    private final Tune tune = new Tune();
    private BlockDisplay liftLine;
    private Interaction hitbox;
    private TextDisplay label;
    private BukkitTask task;

    private Phase phase = Phase.WAITING;
    private int delay;
    private int tick;
    private int playTick;
    private int finaleTick;
    private int finaleAt = -1;

    /* Pose. */
    private float lift = DROP;
    private float swayX;
    private float swayZ;
    private float lidAngle;
    private float keyAngle;
    private float rise;
    private float figScale = 0.001f;
    private float figLift;
    private float spin;
    private float spinSpeed = 0.1f;
    private float bow;
    private UUID facing;
    private int facingUntil;
    private int noteCursor;

    private SeraphineMusicBox(
            JavaPlugin plugin,
            Location anchor,
            String bossName,
            Map<UUID, List<ItemStack>> bundles,
            int delayTicks,
            Runnable onFinish
    ) {
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
        SaintStage.hold(this.anchor);
    }

    /* ================================================================== API */

    public static SeraphineMusicBox place(
            JavaPlugin plugin,
            Location anchor,
            String bossName,
            Map<UUID, List<ItemStack>> bundles
    ) {
        return place(plugin, anchor, bossName, bundles, 0, null);
    }

    /**
     * Registers the box now (the bundles are safe from this moment and the stage is held) and
     * starts lowering it after {@code delayTicks}. {@code onFinish} runs once it is gone.
     */
    public static SeraphineMusicBox place(
            JavaPlugin plugin,
            Location anchor,
            String bossName,
            Map<UUID, List<ItemStack>> bundles,
            int delayTicks,
            Runnable onFinish
    ) {
        SeraphineMusicBox box = new SeraphineMusicBox(plugin, anchor, bossName, bundles, delayTicks, onFinish);
        LIVE.add(box);
        box.task = Bukkit.getScheduler().runTaskTimer(plugin, box::tick, 1L, 1L);
        return box;
    }

    /** @return true when {@code clicked} is a music box (the click is handled either way) */
    public static boolean click(Player player, Entity clicked) {
        if (player == null || clicked == null) {
            return false;
        }
        for (SeraphineMusicBox box : new ArrayList<>(LIVE)) {
            if (box.hitbox != null && box.hitbox.getUniqueId().equals(clicked.getUniqueId())) {
                box.claim(player);
                return true;
            }
        }
        return false;
    }

    /** Plugin disable: every share still in a box is delivered, then the boxes vanish. */
    public static void clearAll() {
        for (SeraphineMusicBox box : new ArrayList<>(LIVE)) {
            box.deliverRemaining(false);
            box.finish();
        }
        LIVE.clear();
    }

    /* ================================================================== timeline */

    private void tick() {
        if (world == null) {
            finish();
            return;
        }
        switch (phase) {
            case WAITING -> {
                if (delay-- <= 0) {
                    begin();
                }
            }
            case ARRIVING -> tickArrival();
            case PLAYING -> tickPlaying();
            case FINALE -> tickFinale();
            default -> {
            }
        }
        tune.tick();
    }

    private void begin() {
        phase = Phase.ARRIVING;
        tick = 0;
        buildCase();
        buildDancer();
        buildLines();
        tune.play(10f, 1f, 0.4f, true);
        actionBar("&6✦ &fSomething is lowered from the flies…");
        world.playSound(at(0f, FLIES, 0f), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.2f, 0.7f);
    }

    private void tickArrival() {
        int t = tick++;
        if (t < DESCEND) {
            float p = t / (float) DESCEND;
            lift = DROP * (1f - smooth(p));
            float amp = 1f - p;
            swayZ = 0.09f * (float) Math.sin(t * 0.11f) * amp;
            swayX = 0.06f * (float) Math.sin(t * 0.083f + 1.3f) * amp;
            tune.volume = 0.4f + 0.9f * p;
            if (t <= 1) {
                // Spawned last tick: snap into place up in the flies, no interpolation.
                pushParts(CASE, 0);
                renderLines(0);
            } else if (t % 2 == 0) {
                pushParts(CASE, 2);
                renderLines(2);
            }
            if (t % 14 == 0) {
                world.playSound(boxTop(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.BLOCKS, 0.6f, 0.6f);
            }
            if (t % 3 == 0) {
                clouds(FLIES + 1f, 3.2);
            }
            return;
        }
        if (t == DESCEND) {
            land();
        }
        if (t == RELEASE) {
            releaseLines();
        }
        if (t == RELEASE + 40) {
            for (BlockDisplay line : lines) {
                kill(line);
            }
            for (BlockDisplay bar : flyBar) {
                kill(bar);
            }
            flyBar.clear();
        }
        if (t == WIND || t == WIND + 6 || t == WIND + 12) {
            int click = (t - WIND) / 6;
            keyAngle += 2f * PI / 3f;
            pushParts(EnumSet.of(Group.KEY), 4);
            world.playSound(at(W / 2f + 0.3f, KEY_Y, 0f), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.9f, 1.3f + click * 0.2f);
            world.playSound(at(W / 2f + 0.3f, KEY_Y, 0f), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.BLOCKS, 0.5f, 1.8f);
        }
        if (t == OPEN) {
            open();
        }
        if (t == OPEN + 3) {
            // The dancer springs up past her mark...
            figScale = FIG * 1.1f;
            rise = RISE + 0.08f;
            pushParts(DANCER, 8);
        }
        if (t == OPEN + 11) {
            // ...and settles on it.
            figScale = FIG;
            rise = RISE;
            pushParts(DANCER, 5);
        }
        if (t >= READY) {
            ready();
        }
    }

    private void land() {
        lift = 0f;
        swayX = 0f;
        swayZ = 0f;
        pushParts(CASE, 2);
        renderLines(2);
        tune.stop();
        Location floor = at(0f, 0.1f, 0f);
        world.playSound(floor, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.2f, 0.6f);
        world.playSound(floor, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(floor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.2f);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            world.spawnParticle(Particle.DUST, floor.clone().add(Math.cos(a) * 1.9, 0, Math.sin(a) * 1.5),
                    2, 0.05, 0.02, 0.05, 0, new Particle.DustOptions(DUST_GOLD, 1.1f));
        }
        world.spawnParticle(Particle.WHITE_ASH, floor, 30, 1.4, 0.1, 1.1, 0.02);
    }

    private void open() {
        lidAngle = LID_OPEN;
        pushParts(EnumSet.of(Group.LID), 14);
        Location top = boxTop();
        world.playSound(top, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.9f, 1.3f);
        world.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.8f, 1.7f);
        world.spawnParticle(Particle.END_ROD, top, 24, 0.7, 0.3, 0.5, 0.03);
        world.spawnParticle(Particle.DUST, top, 20, 0.8, 0.3, 0.6, 0, new Particle.DustOptions(DUST_GOLD, 1.0f));
        tune.play(8f, 1f, 1.3f, true);
    }

    private void ready() {
        phase = Phase.PLAYING;
        playTick = 0;
        hitbox = world.spawn(anchor, Interaction.class, interaction -> {
            interaction.setInteractionWidth(3.0f);
            interaction.setInteractionHeight(3.3f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
        });
        spawned.add(hitbox);
        label = world.spawn(at(0f, 3.75f, 0f), TextDisplay.class, text -> {
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
                player.sendMessage(TextUtil.component("&6Music Box &8» &fYour share waits inside. &7Right-click the music box."));
            }
        }
    }

    private void tickPlaying() {
        playTick++;
        Player face = facing == null ? null : Bukkit.getPlayer(facing);
        if (face != null && playTick < facingUntil && face.getWorld() == world) {
            float dx = (float) (face.getLocation().getX() - anchor.getX());
            float dz = (float) (face.getLocation().getZ() - anchor.getZ());
            spin = approachAngle(spin, (float) Math.atan2(dx, dz), 0.3f);
            bow += (0.32f - bow) * 0.25f;
        } else {
            facing = null;
            spin += spinSpeed;
            bow += (0f - bow) * 0.2f;
        }
        if (playTick % 2 == 0) {
            pushParts(EnumSet.of(Group.LOWER, Group.UPPER), 2);
        }
        keyAngle -= 0.05f;
        if (playTick % 4 == 0) {
            pushParts(EnumSet.of(Group.KEY), 4);
        }
        if (playTick % 16 == 0) {
            Location note = at(0f, BODY_TOP + 2.2f, 0f);
            world.spawnParticle(Particle.NOTE, note.add(rand(-0.6, 0.6), rand(0, 0.4), rand(-0.4, 0.4)),
                    0, ThreadLocalRandom.current().nextInt(25) / 24.0, 0, 0, 1);
        }
        if (playTick % 10 == 0) {
            world.spawnParticle(Particle.DUST, at(0f, BODY_TOP + 1.0f, 0f), 2, 0.5, 0.6, 0.5, 0,
                    new Particle.DustOptions(DUST_GOLD, 0.7f));
        }
        if (playTick % 20 == 0) {
            updateLabel();
        }
        int left = CLAIM_TICKS - playTick;
        if (left == 20 * 60 || left == 20 * 15) {
            remind(left / 20);
        }
        if (playTick >= CLAIM_TICKS) {
            deliverRemaining(true);
            beginFinale();
            return;
        }
        if (finaleAt >= 0 && playTick >= finaleAt) {
            beginFinale();
        }
    }

    private void beginFinale() {
        phase = Phase.FINALE;
        finaleTick = 0;
        tune.windDown();
        removeEntity(hitbox);
        hitbox = null;
        removeEntity(label);
        label = null;
        actionBar("&7&oThe music box winds down…");
    }

    private void tickFinale() {
        int t = finaleTick++;
        if (t < 56) {
            // The spring runs out: every turn slower than the last.
            spinSpeed *= 0.955f;
            spin += spinSpeed;
        }
        if (t >= 44 && t < 60) {
            spin = approachAngle(spin, 0f, 0.14f);
            bow += (0f - bow) * 0.3f;
        }
        if (t < 60 && t % 2 == 0) {
            pushParts(EnumSet.of(Group.LOWER, Group.UPPER), 2);
        }
        if (t == 60) {
            // She curtsies: the one gesture no one strung her for.
            bow = 0.55f;
            pushParts(EnumSet.of(Group.UPPER), 14);
            world.playSound(boxTop(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 0.8f);
        }
        if (t == 74) {
            liftLine = display(Material.GOLD_BLOCK, 15);
            liftLine.setGlowing(true);
            liftLine.setGlowColorOverride(THREAD_GOLD);
            world.playSound(at(0f, FLIES, 0f), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
        if (t >= 74) {
            clouds(FLIES + 1f, 2.4);
        }
        if (t >= 75 && t <= 82) {
            // One thread comes down for her.
            Vector3f top = new Vector3f(0f, FLIES + 2f, 0f);
            Vector3f head = headPoint();
            float reach = smooth((t - 75) / 7f);
            push(liftLine, beam(top, new Vector3f(top).lerp(head, reach), 0.05f), t == 75 ? 0 : 1);
        }
        if (t == 82) {
            world.playSound(boxTop(), Sound.BLOCK_TRIPWIRE_ATTACH, SoundCategory.BLOCKS, 1.2f, 1.2f);
            bow = 0f;
        }
        if (t >= 84 && t < 150) {
            // Lifted into the clouds, free.
            figLift = 34f * smooth((t - 84) / 60f);
            if (t >= 138) {
                figScale = FIG * Math.max(0.001f, 1f - (t - 138) / 12f);
            }
            if (t % 2 == 0) {
                pushParts(EnumSet.of(Group.LOWER, Group.UPPER), 2);
                push(liftLine, beam(new Vector3f(0f, FLIES + 2f, 0f), headPoint(), 0.05f), 2);
            }
        }
        if (t == 150) {
            kill(liftLine);
            liftLine = null;
        }
        if (t == 92) {
            lidAngle = 0f;
            pushParts(EnumSet.of(Group.LID), 3);
            Location top = boxTop();
            world.playSound(top, Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 0.8f);
            world.playSound(top, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
        if (t == 100) {
            // The kintsugi seams flare.
            for (Part part : parts) {
                if (part.group == Group.SEAM && part.display.isValid()) {
                    part.display.setGlowing(true);
                    part.display.setGlowColorOverride(DUST_GOLD);
                }
            }
            world.playSound(boxTop(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.0f, 0.6f);
            world.spawnParticle(Particle.DUST, at(0f, BODY_MID, 0f), 30, 1.1, 0.5, 0.8, 0, new Particle.DustOptions(DUST_GOLD, 1.3f));
        }
        if (t == 112) {
            shatter();
        }
        if (t == 124) {
            ascend();
        }
        if (t == 180) {
            // The last note of the fight, once more.
            tune.single(21, 0.5f, 1f);
        }
        if (t >= FINALE) {
            finish();
        }
    }

    /* ================================================================== claim */

    private void claim(Player player) {
        if (phase != Phase.PLAYING) {
            player.sendActionBar(TextUtil.component(phase == Phase.FINALE
                    ? "&7The music box is closing."
                    : "&7Wait for the dancer…"));
            return;
        }
        List<ItemStack> share = unclaimed.remove(player.getUniqueId());
        if (share == null) {
            player.sendActionBar(TextUtil.component(claimed.contains(player.getUniqueId())
                    ? "&7You already took your share."
                    : "&7The music box holds nothing for you."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.5f, 0.5f);
            return;
        }
        claimed.add(player.getUniqueId());
        give(player, share);
        ribbon(player);
        tune.single(nextNote(), 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        facing = player.getUniqueId();
        facingUntil = playTick + 24;
        if (unclaimed.isEmpty()) {
            finaleAt = playTick + 36;
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
            player.sendMessage(TextUtil.component("&6Music Box &8» &f" + name
                    + (item.getAmount() > 1 ? " &7x" + item.getAmount() : "")));
        }
    }

    /** Expiry / disable: online owners receive their share, offline owners' share drops at the box. */
    private void deliverRemaining(boolean announce) {
        for (Map.Entry<UUID, List<ItemStack>> entry : unclaimed.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner != null && owner.isOnline()) {
                if (announce) {
                    owner.sendMessage(TextUtil.component("&6Music Box &8» &7The music box sent your share after you."));
                }
                give(owner, entry.getValue());
            } else if (world != null) {
                Location drop = at(0f, BODY_TOP + 0.3f, 0f);
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

    /** A gold ribbon from the open box to the claimer's chest, then reeled into them. */
    private void ribbon(Player player) {
        Vector3f from = new Vector3f(0f, BODY_TOP + 0.9f, 0f);
        BlockDisplay line = display(Material.GOLD_BLOCK, 15);
        line.setGlowing(true);
        line.setGlowColorOverride(THREAD_GOLD);
        push(line, beam(from, new Vector3f(from).add(0f, 0.01f, 0f), 0.05f), 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector3f chest = chestOf(player);
            push(line, beam(from, chest, 0.05f), 4);
            Location mid = at((chest.x + from.x) * 0.5f, (chest.y + from.y) * 0.5f, (chest.z + from.z) * 0.5f);
            world.spawnParticle(Particle.DUST, mid, 12, 0.6, 0.4, 0.6, 0, new Particle.DustOptions(DUST_GOLD, 0.8f));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector3f chest = chestOf(player);
            push(line, beam(new Vector3f(chest).sub(0f, 0.02f, 0f), chest, 0.02f), 6);
            if (player.isOnline() && player.getWorld() == world) {
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1.2, 0), 10, 0.25, 0.35, 0.25, 0,
                        new Particle.DustOptions(DUST_GOLD, 0.9f));
            }
        }, 9L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> kill(line), 17L);
    }

    private void remind(int seconds) {
        for (UUID owner : unclaimed.keySet()) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.component("&6Music Box &8» &7Still playing for you… &f" + seconds + "s"));
            }
        }
    }

    private void updateLabel() {
        if (label == null || !label.isValid()) {
            return;
        }
        int left = Math.max(0, (CLAIM_TICKS - playTick) / 20);
        label.text(TextUtil.component(bossName
                + "\n&6✦ &f&oMusic Box &6✦"
                + "\n&7Right-click to take your share"
                + "\n&f" + unclaimed.size() + "&7/&f" + shares + " &7waiting &8· &f"
                + String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60)));
    }

    /* ================================================================== build */

    private void buildCase() {
        Material porcelain = Material.SMOOTH_QUARTZ;
        Material glaze = Material.WHITE_GLAZED_TERRACOTTA;
        Material gold = Material.GOLD_BLOCK;

        // Plinth in the stage's own blackstone, gold line on top.
        box(Group.BODY, Material.POLISHED_BLACKSTONE, 0f, PLINTH / 2f, 0f, W + 0.3f, PLINTH, D + 0.3f);
        box(Group.BODY, gold, 0f, PLINTH + 0.015f, 0f, W + 0.34f, 0.03f, D + 0.34f);
        // Porcelain case, glazed panels on all four sides.
        box(Group.BODY, porcelain, 0f, BODY_MID, 0f, W, 1.1f, D);
        box(Group.BODY, glaze, 0f, BODY_MID, D / 2f + 0.01f, W - 0.5f, 0.72f, 0.02f);
        box(Group.BODY, glaze, 0f, BODY_MID, -D / 2f - 0.01f, W - 0.5f, 0.72f, 0.02f);
        box(Group.BODY, glaze, W / 2f + 0.01f, BODY_MID, 0f, 0.02f, 0.72f, D - 0.5f);
        box(Group.BODY, glaze, -W / 2f - 0.01f, BODY_MID, 0f, 0.02f, 0.72f, D - 0.5f);
        // Gold rails and corner posts.
        box(Group.BODY, gold, 0f, BODY_TOP - 0.04f, 0f, W + 0.05f, 0.07f, D + 0.05f);
        box(Group.BODY, gold, 0f, PLINTH + 0.07f, 0f, W + 0.05f, 0.07f, D + 0.05f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(Group.BODY, gold, sx * W / 2f, BODY_MID, sz * D / 2f, 0.13f, 1.14f, 0.13f);
            }
        }
        // Keyhole escutcheon.
        box(Group.BODY, gold, 0f, BODY_MID - 0.12f, D / 2f + 0.03f, 0.16f, 0.24f, 0.02f);
        box(Group.BODY, Material.BLACK_CONCRETE, 0f, BODY_MID - 0.1f, D / 2f + 0.042f, 0.04f, 0.1f, 0.004f);
        // Crimson velvet inside, seen when the lid is up.
        box(Group.BODY, Material.RED_WOOL, 0f, BODY_TOP + 0.005f, 0f, W - 0.16f, 0.01f, D - 0.16f);

        // Kintsugi: hairline gold seams across the glaze.
        seamFront(0.35f, BODY_MID + 0.1f, D / 2f + 0.025f, 0.5f, 0.6f);
        seamFront(-0.5f, BODY_MID - 0.15f, D / 2f + 0.025f, 0.4f, -0.9f);
        seamFront(-0.12f, BODY_MID + 0.24f, D / 2f + 0.025f, 0.3f, 1.3f);
        seamFront(0.1f, BODY_MID - 0.2f, -D / 2f - 0.025f, 0.45f, 0.4f);
        seamFront(-0.4f, BODY_MID + 0.15f, -D / 2f - 0.025f, 0.35f, -0.5f);
        seamSide(W / 2f + 0.025f, BODY_MID, 0.2f, 0.4f, 0.7f);
        seamSide(-W / 2f - 0.025f, BODY_MID + 0.1f, -0.25f, 0.35f, -0.6f);

        // Lid (modelled closed; hinged at the back top edge).
        float lidMid = BODY_TOP + 0.07f;
        box(Group.LID, porcelain, 0f, lidMid, 0f, W + 0.06f, 0.14f, D + 0.06f);
        box(Group.LID, gold, 0f, lidMid, 0f, W + 0.1f, 0.05f, D + 0.1f);
        box(Group.LID, glaze, 0f, BODY_TOP + 0.145f, 0f, W - 0.6f, 0.01f, D - 0.6f);
        box(Group.LID, Material.RED_WOOL, 0f, BODY_TOP - 0.004f, 0f, W - 0.16f, 0.008f, D - 0.16f);
        box(Group.LID, Material.LIGHT_BLUE_STAINED_GLASS, 0f, BODY_TOP - 0.012f, 0.1f, 1.2f, 0.006f, 0.8f);
        box(Group.LID, gold, 0f, BODY_TOP + 0.21f, 0f, 0.2f, 0.12f, 0.2f, new Quaternionf().rotateY(PI / 4f));
        box(Group.LID, gold, 0f, BODY_TOP + 0.3f, 0f, 0.08f, 0.08f, 0.08f, diamond());

        // Winding key on the right side: a gold butterfly on a shaft.
        box(Group.KEY, gold, W / 2f + 0.17f, KEY_Y, 0f, 0.3f, 0.06f, 0.06f);
        box(Group.KEY, gold, W / 2f + 0.33f, KEY_Y + 0.11f, 0f, 0.04f, 0.2f, 0.13f);
        box(Group.KEY, gold, W / 2f + 0.33f, KEY_Y - 0.11f, 0f, 0.04f, 0.2f, 0.13f);

        // Gold spindle the dancer stands on (unit height, scaled by the rise).
        add(Group.SPINDLE, gold, 15, new Matrix4f().scale(0.05f, 1f, 0.05f).translate(-0.5f, 0f, -0.5f));
    }

    /** Seraphine in miniature, en pointe in passé, arms in fifth. Facing +Z. */
    private void buildDancer() {
        Material porcelain = Material.SMOOTH_QUARTZ;
        Material gold = Material.GOLD_BLOCK;

        // Pedestal and standing leg on pointe.
        box(Group.LOWER, gold, 0f, -0.015f, 0f, 0.3f, 0.03f, 0.3f, new Quaternionf().rotateY(PI / 4f));
        limb(Group.LOWER, porcelain, v(-0.035f, 0.55f, 0f), v(-0.035f, 0.07f, 0f), 0.07f);
        box(Group.LOWER, gold, -0.035f, 0.035f, 0.01f, 0.06f, 0.07f, 0.08f);
        // Passé: thigh out to the side, toe resting at the standing knee.
        limb(Group.LOWER, porcelain, v(0.035f, 0.55f, 0f), v(0.19f, 0.42f, 0.04f), 0.065f);
        limb(Group.LOWER, porcelain, v(0.19f, 0.42f, 0.04f), v(-0.01f, 0.33f, 0.02f), 0.055f);
        box(Group.LOWER, gold, -0.01f, 0.33f, 0.02f, 0.05f, 0.05f, 0.06f);
        // Tutu: two squares turned 45 degrees and a softer tier above.
        box(Group.LOWER, Material.WHITE_CONCRETE, 0f, 0.555f, 0f, 0.46f, 0.025f, 0.46f);
        box(Group.LOWER, Material.WHITE_CONCRETE, 0f, 0.555f, 0f, 0.46f, 0.025f, 0.46f, new Quaternionf().rotateY(PI / 4f));
        box(Group.LOWER, Material.WHITE_WOOL, 0f, 0.58f, 0f, 0.34f, 0.03f, 0.34f, new Quaternionf().rotateY(PI / 8f));

        // Waist ribbon, bodice with its one gold seam.
        box(Group.UPPER, gold, 0f, 0.605f, 0f, 0.14f, 0.03f, 0.1f);
        box(Group.UPPER, porcelain, 0f, 0.71f, 0f, 0.13f, 0.19f, 0.09f);
        box(Group.UPPER, gold, 0.025f, 0.72f, 0.047f, 0.008f, 0.12f, 0.004f, new Quaternionf().rotateZ(0.4f));
        // Neck and head: white face, closed gold eyes, red mouth, a bun.
        limb(Group.UPPER, porcelain, v(0f, 0.8f, 0f), v(0f, 0.87f, 0f), 0.035f);
        box(Group.UPPER, porcelain, 0f, 0.925f, 0f, 0.11f, 0.12f, 0.11f);
        box(Group.UPPER, Material.WHITE_CONCRETE, 0f, 0.92f, 0.056f, 0.09f, 0.08f, 0.004f);
        box(Group.UPPER, gold, -0.022f, 0.93f, 0.059f, 0.026f, 0.006f, 0.003f);
        box(Group.UPPER, gold, 0.022f, 0.93f, 0.059f, 0.026f, 0.006f, 0.003f);
        box(Group.UPPER, Material.RED_CONCRETE, 0f, 0.895f, 0.059f, 0.018f, 0.006f, 0.003f);
        box(Group.UPPER, porcelain, 0f, 0.995f, -0.035f, 0.06f, 0.05f, 0.06f);
        // Her sunburst halo, as on the full-size saint.
        for (int k = 0; k < 8; k++) {
            float b = k * PI / 4f;
            box(Group.UPPER, gold, (float) Math.sin(b) * 0.075f, 1.05f, (float) Math.cos(b) * 0.075f - 0.03f,
                    0.055f, 0.01f, 0.01f, new Quaternionf().rotateY(b + PI / 2f));
        }
        // Arms en haut: elbows out, hands meeting over the head. Gold ball joints at the shoulders.
        limb(Group.UPPER, porcelain, v(-0.075f, 0.79f, 0f), v(-0.13f, 0.95f, 0.01f), 0.032f);
        limb(Group.UPPER, porcelain, v(-0.13f, 0.95f, 0.01f), v(-0.025f, 1.09f, 0.02f), 0.028f);
        limb(Group.UPPER, porcelain, v(0.075f, 0.79f, 0f), v(0.13f, 0.95f, 0.01f), 0.032f);
        limb(Group.UPPER, porcelain, v(0.13f, 0.95f, 0.01f), v(0.025f, 1.09f, 0.02f), 0.028f);
        box(Group.UPPER, gold, -0.075f, 0.79f, 0f, 0.04f, 0.04f, 0.04f, diamond());
        box(Group.UPPER, gold, 0.075f, 0.79f, 0f, 0.04f, 0.04f, 0.04f, diamond());
    }

    /** Four golden threads from a marionette control in the clouds to the lid's corners. */
    private void buildLines() {
        float[][] corners = {{1, 1}, {-1, -1}, {-1, 1}, {1, -1}};
        float[][] tops = {{0.9f, 0f}, {-0.9f, 0f}, {0f, 0.65f}, {0f, -0.65f}};
        for (int i = 0; i < 4; i++) {
            lines[i] = display(Material.GOLD_BLOCK, 15);
            lines[i].setGlowing(true);
            lines[i].setGlowColorOverride(THREAD_GOLD);
            lineCorner[i] = new Vector3f(corners[i][0] * (W / 2f - 0.06f), BODY_TOP + 0.14f, corners[i][1] * (D / 2f - 0.06f));
            lineTop[i] = new Vector3f(tops[i][0], FLIES, tops[i][1]);
        }
        BlockDisplay across = display(Material.GOLD_BLOCK, 15);
        BlockDisplay along = display(Material.GOLD_BLOCK, 15);
        push(across, box(new Vector3f(0f, FLIES + 0.05f, 0f), new Vector3f(1.9f, 0.1f, 0.1f)), 0);
        push(along, box(new Vector3f(0f, FLIES + 0.05f, 0f), new Vector3f(0.1f, 0.1f, 1.4f)), 0);
        flyBar.add(across);
        flyBar.add(along);
    }

    private void renderLines(int interp) {
        Matrix4f root = boxRoot();
        for (int i = 0; i < 4; i++) {
            Vector3f corner = root.transformPosition(new Vector3f(lineCorner[i]));
            push(lines[i], beam(lineTop[i], corner, 0.045f), interp);
        }
    }

    /** The box is delivered: the threads let go and are reeled up with the control. */
    private void releaseLines() {
        for (int i = 0; i < 4; i++) {
            Vector3f top = new Vector3f(lineTop[i]).add(0f, 12f, 0f);
            push(lines[i], beam(top, new Vector3f(top).sub(0f, 0.02f, 0f), 0.01f), 30);
        }
        for (BlockDisplay bar : flyBar) {
            push(bar, gone(new Vector3f(0f, FLIES + 14f, 0f)), 34);
        }
        world.playSound(boxTop(), Sound.BLOCK_TRIPWIRE_DETACH, SoundCategory.BLOCKS, 1.2f, 0.7f);
        world.playSound(boxTop(), Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.BLOCKS, 0.8f, 1.4f);
    }

    /* ================================================================== dismantle */

    /** The case comes apart into porcelain shards. */
    private void shatter() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3f core = new Vector3f(0f, BODY_MID, 0f);
        Map<Group, Matrix4f> cache = new EnumMap<>(Group.class);
        for (Part part : parts) {
            if (!CASE.contains(part.group)) {
                continue;
            }
            Matrix4f m = new Matrix4f(cache.computeIfAbsent(part.group, this::groupMatrix)).mul(part.local);
            Vector3f c = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            Vector3f s = m.getScale(new Vector3f());
            Vector3f out = new Vector3f(c).sub(core);
            out.y = 0f;
            if (out.lengthSquared() < 0.01f) {
                out.set((float) r.nextGaussian(), 0f, (float) r.nextGaussian());
            }
            out.normalize((float) r.nextDouble(0.9, 2.2)).add(0f, (float) r.nextDouble(0.4, 1.4), 0f);
            part.free = true;
            part.freeMatrix.set(new Matrix4f().translation(new Vector3f(c).add(out))
                    .rotateXYZ((float) r.nextDouble(-1.4, 1.4), (float) r.nextDouble(2 * PI), (float) r.nextDouble(-1.4, 1.4))
                    .scale(s).translate(-0.5f, -0.5f, -0.5f));
            push(part.display, transformation(part.freeMatrix), 10);
        }
        Location mid = at(0f, BODY_MID, 0f);
        world.playSound(mid, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.2f, 0.5f);
        world.playSound(mid, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.2f, 0.75f);
        world.playSound(mid, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.2f, 1.0f);
        world.playSound(mid, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.spawnParticle(Particle.BLOCK, mid, 50, 1.0, 0.5, 0.8, 0, Material.WHITE_GLAZED_TERRACOTTA.createBlockData());
        world.spawnParticle(Particle.DUST, mid, 50, 1.2, 0.6, 0.9, 0, new Particle.DustOptions(DUST_GOLD, 1.8f));
        world.spawnParticle(Particle.DUST, mid, 30, 1.2, 0.6, 0.9, 0, new Particle.DustOptions(DUST_WHITE, 1.4f));
    }

    /** The shards rise like ash. */
    private void ascend() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int i = 0;
        for (Part part : parts) {
            if (!part.free) {
                continue;
            }
            Vector3f c = part.freeMatrix.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            Vector3f up = new Vector3f(c).add((float) r.nextGaussian() * 1.5f, (float) r.nextDouble(12, 22), (float) r.nextGaussian() * 1.5f);
            part.freeMatrix.set(new Matrix4f().translation(up).rotateY((float) r.nextDouble(2 * PI)).scale(0.001f));
            push(part.display, transformation(part.freeMatrix), 40 + (i % 20) * 2);
            i++;
        }
        world.spawnParticle(Particle.WHITE_ASH, at(0f, 2f, 0f), 40, 1.5, 1.0, 1.5, 0.02);
        world.spawnParticle(Particle.END_ROD, at(0f, 2.5f, 0f), 12, 1.2, 1.0, 1.2, 0.02);
        world.playSound(at(0f, 2f, 0f), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.0f, 0.5f);
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
        for (Part part : parts) {
            removeEntity(part.display);
        }
        parts.clear();
        for (Entity entity : spawned) {
            removeEntity(entity);
        }
        spawned.clear();
        hitbox = null;
        label = null;
        SaintStage.release(anchor);
        LIVE.remove(this);
        if (onFinish != null) {
            onFinish.run();
        }
    }

    /* ================================================================== frames */

    private Matrix4f boxRoot() {
        return new Matrix4f().translation(0f, lift, 0f).rotateZ(swayZ).rotateX(swayX);
    }

    private Matrix4f figureRoot() {
        return boxRoot().translate(0f, BODY_TOP + rise + figLift, 0f).rotateY(spin).scale(figScale);
    }

    private Matrix4f groupMatrix(Group group) {
        return switch (group) {
            case LID -> boxRoot().translate(0f, BODY_TOP, -D / 2f).rotateX(-lidAngle).translate(0f, -BODY_TOP, D / 2f);
            case KEY -> boxRoot().translate(0f, KEY_Y, 0f).rotateX(keyAngle).translate(0f, -KEY_Y, 0f);
            case SPINDLE -> boxRoot().translate(0f, BODY_TOP, 0f).scale(1f, Math.max(0.001f, rise + 0.02f), 1f);
            case LOWER -> figureRoot();
            case UPPER -> figureRoot().translate(0f, HIP, 0f).rotateX(bow).translate(0f, -HIP, 0f);
            default -> boxRoot();
        };
    }

    private void pushParts(Set<Group> groups, int interp) {
        Map<Group, Matrix4f> cache = new EnumMap<>(Group.class);
        for (Part part : parts) {
            if (part.free || !groups.contains(part.group)) {
                continue;
            }
            Matrix4f group = cache.computeIfAbsent(part.group, this::groupMatrix);
            push(part.display, transformation(new Matrix4f(group).mul(part.local)), interp);
        }
    }

    private Vector3f headPoint() {
        return figureRoot().transformPosition(new Vector3f(0f, 1.1f, 0f));
    }

    private Vector3f chestOf(Player player) {
        if (!player.isOnline() || player.getWorld() != world) {
            return new Vector3f(0f, BODY_TOP + 1f, 0f);
        }
        Location l = player.getLocation();
        return new Vector3f(
                (float) (l.getX() - anchor.getX()),
                (float) (l.getY() + 1.2 - anchor.getY()),
                (float) (l.getZ() - anchor.getZ()));
    }

    private Location boxTop() {
        return at(0f, lift + BODY_TOP + 0.2f, 0f);
    }

    private Location at(float x, float y, float z) {
        return new Location(world, anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
    }

    private void clouds(float y, double radius) {
        Location sky = at(0f, y, 0f);
        double angle = tick * 0.4 + finaleTick * 0.4;
        for (int i = 0; i < 5; i++) {
            double a = angle + i * Math.PI * 0.4;
            world.spawnParticle(Particle.CLOUD, sky.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    2, 0.5, 0.15, 0.5, 0.0);
        }
    }

    private void actionBar(String text) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= 64 * 64) {
                player.sendActionBar(TextUtil.component(text));
            }
        }
    }

    private int nextNote() {
        for (int guard = 0; guard < MusicBox.THEME.length; guard++) {
            int index = noteCursor;
            noteCursor = (noteCursor + 1) % MusicBox.THEME.length;
            if (MusicBox.THEME[index] >= 0) {
                return index;
            }
        }
        return 0;
    }

    /* ================================================================== displays */

    /** A loose display (threads, ribbons, the fly bar), removed on finish. */
    private BlockDisplay display(Material material, int brightness) {
        BlockDisplay d = spawnBlock(material, brightness);
        spawned.add(d);
        return d;
    }

    private void add(Group group, Material material, int brightness, Matrix4f local) {
        parts.add(new Part(spawnBlock(material, brightness), group, local));
    }

    /** Spawned invisible on the anchor; all motion afterwards is transformation interpolation. */
    private BlockDisplay spawnBlock(Material material, int brightness) {
        return world.spawn(anchor, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.setViewRange(3.5f);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setBrightness(new Display.Brightness(brightness, brightness));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTeleportDuration(0);
            display.setTransformation(gone(new Vector3f()));
        });
    }

    private void box(Group group, Material material, float x, float y, float z, float sx, float sy, float sz) {
        box(group, material, x, y, z, sx, sy, sz, new Quaternionf());
    }

    private void box(Group group, Material material, float x, float y, float z, float sx, float sy, float sz, Quaternionf rot) {
        int brightness = material == Material.GOLD_BLOCK ? 15 : 14;
        add(group, material, brightness, new Matrix4f().translation(x, y, z).rotate(rot).scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f));
    }

    private void limb(Group group, Material material, Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float length = dir.length();
        Quaternionf rot = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / length, dir.y / length, dir.z / length);
        add(group, material, 14, new Matrix4f().translation(a).rotate(rot).scale(width, length, width).translate(-0.5f, 0f, -0.5f));
    }

    private void seamFront(float x, float y, float z, float length, float roll) {
        box(Group.SEAM, Material.GOLD_BLOCK, x, y, z, 0.02f, length, 0.012f, new Quaternionf().rotateZ(roll));
    }

    private void seamSide(float x, float y, float z, float length, float roll) {
        box(Group.SEAM, Material.GOLD_BLOCK, x, y, z, 0.012f, length, 0.02f, new Quaternionf().rotateX(roll));
    }

    private static Quaternionf diamond() {
        return new Quaternionf().rotateY(PI / 4f).rotateX(PI / 4f);
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static void push(Display display, Transformation transformation, int interp) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, interp));
        display.setTransformation(transformation);
    }

    private void kill(Display display) {
        if (display != null) {
            spawned.remove(display);
            removeEntity(display);
        }
    }

    private static void removeEntity(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /**
     * Every matrix here is translate * rotate * axis-aligned scale, so it is split directly into
     * translation, left rotation and scale: letting the server SVD-decompose it can flip rotations
     * between frames and the client then spins thin parts while interpolating. The columns carry
     * the scale, hence the unnormalized rotation extraction (the normalized one assumes unit
     * columns and returns a skewed, non-unit quaternion for scaled parts).
     */
    private static Transformation transformation(Matrix4f m) {
        Vector3f translation = m.getTranslation(new Vector3f());
        Vector3f scale = m.getScale(new Vector3f());
        Quaternionf rotation = scale.x < 1e-6f || scale.y < 1e-6f || scale.z < 1e-6f
                ? new Quaternionf()
                : m.getUnnormalizedRotation(new Quaternionf());
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    private static Transformation beam(Vector3f a, Vector3f b, float width) {
        Vector3f dir = new Vector3f(b).sub(a);
        float length = dir.length();
        if (length < 1e-4f) {
            return gone(a);
        }
        Quaternionf rotation = new Quaternionf().rotationTo(0f, 1f, 0f, dir.x / length, dir.y / length, dir.z / length);
        Vector3f corner = rotation.transform(new Vector3f(-width * 0.5f, 0f, -width * 0.5f));
        return new Transformation(new Vector3f(a).add(corner), rotation, new Vector3f(width, length, width), new Quaternionf());
    }

    private static Transformation box(Vector3f center, Vector3f size) {
        return new Transformation(new Vector3f(center).sub(new Vector3f(size).mul(0.5f)),
                new Quaternionf(), new Vector3f(size), new Quaternionf());
    }

    private static Transformation gone(Vector3f at) {
        return new Transformation(new Vector3f(at), new Quaternionf(), new Vector3f(0.001f), new Quaternionf());
    }

    /* ================================================================== math */

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float approachAngle(float from, float to, float maxStep) {
        float delta = to - from;
        while (delta > PI) {
            delta -= 2f * PI;
        }
        while (delta < -PI) {
            delta += 2f * PI;
        }
        if (Math.abs(delta) <= maxStep) {
            return from + delta;
        }
        return from + Math.signum(delta) * maxStep;
    }

    private static double rand(double lo, double hi) {
        return ThreadLocalRandom.current().nextDouble(lo, hi);
    }

    private static float semitone(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }

    /* ================================================================== the lullaby */

    /** Her lullaby from the box itself: positional, so it sounds from where the box is. */
    private final class Tune {
        private boolean playing;
        private int beat;
        private float clock;
        private float ticksPerBeat = 8f;
        private float pitchMul = 1f;
        private float volume = 1f;
        private boolean loop;
        private boolean windingDown;

        void play(float ticksPerBeat, float pitchMul, float volume, boolean loop) {
            this.playing = true;
            this.beat = 0;
            this.clock = 0f;
            this.ticksPerBeat = ticksPerBeat;
            this.pitchMul = pitchMul;
            this.volume = volume;
            this.loop = loop;
            this.windingDown = false;
        }

        /** Every beat longer and flatter until the spring runs out (as at her death). */
        void windDown() {
            windingDown = true;
            loop = false;
        }

        void stop() {
            playing = false;
        }

        void tick() {
            if (!playing) {
                return;
            }
            clock -= 1f;
            if (clock > 0f) {
                return;
            }
            if (beat >= MusicBox.THEME.length) {
                if (!loop) {
                    playing = false;
                    return;
                }
                beat = 0;
            }
            note(beat);
            beat++;
            clock += ticksPerBeat;
            if (windingDown) {
                ticksPerBeat *= 1.16f;
                pitchMul *= 0.985f;
                volume *= 0.95f;
                if (ticksPerBeat > 60f) {
                    playing = false;
                }
            }
        }

        void single(int index, float pitchMul, float volume) {
            int n = MusicBox.THEME[Math.floorMod(index, MusicBox.THEME.length)];
            if (n < 0) {
                return;
            }
            float pitch = clampPitch(semitone(n) * pitchMul);
            Location at = boxTop();
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, volume, pitch);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, volume * 0.2f, pitch);
        }

        private void note(int index) {
            Location at = boxTop();
            if (index % 3 == 0) {
                int root = MusicBox.ROOTS[(index / 6) % MusicBox.ROOTS.length];
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.RECORDS, volume * 0.45f,
                        clampPitch(semitone(root) * pitchMul));
            }
            int n = MusicBox.THEME[index];
            if (n < 0) {
                return;
            }
            float pitch = clampPitch(semitone(n) * pitchMul);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, volume, pitch);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, volume * 0.18f, pitch);
        }
    }
}
