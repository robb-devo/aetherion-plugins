package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.NpcPresence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Miss Ledger's stamp — the moment orientation ends, as a small physical beat.
 * <p>
 * An orientation slip appears on her side of the desk, her stamp comes down on it
 * (the milestone fanfare fires on the hit, not on the click), a wine-ink seal is left
 * behind, the slip lifts and is filed into your pocket, and somewhere down at the
 * harbour two bells answer. About two and a half seconds; the listener is the only one
 * who sees it.
 * <p>
 * Safety: displays are per-player and non-persistent. Quit, world change or plugin
 * disable fires the milestone callback immediately (if it hasn't fired yet) and removes
 * every piece — the graduation reward can never be lost to the show.
 */
public final class GraduationStamp implements Listener {

    public static final String TAG = "ae_graduation_stamp";

    private static final double LEDGER_RANGE = 8.0;
    private static final long T_SLIP = 1;
    private static final long T_SLIP_GROW = 3;
    private static final long T_STAMP = 5;
    private static final long T_STAMP_IN = 7;
    private static final long T_RAISE = 11;
    private static final long T_PRESS = 17;
    private static final long T_HIT = 19;
    private static final long T_LIFT = 25;
    private static final long T_STAMP_GONE = 33;
    private static final long T_SHOW = 36;
    private static final long T_FILE = 48;
    private static final long T_FILED = 54;
    private static final long T_BELL_1 = 60;
    private static final long T_BELL_2 = 72;
    private static final long T_END = 74;

    private static final BlockData SLIP = Material.WHITE_CONCRETE.createBlockData();
    private static final BlockData INK = Material.PURPLE_CONCRETE.createBlockData();
    private static final BlockData RUBBER = Material.RED_TERRACOTTA.createBlockData();
    private static final BlockData BASE = Material.POLISHED_BLACKSTONE.createBlockData();
    private static final BlockData HANDLE = Material.STRIPPED_DARK_OAK_LOG.createBlockData();
    private static final Particle.DustOptions INK_MOTE =
            new Particle.DustOptions(Color.fromRGB(92, 48, 64), 0.7f);

    private static volatile GraduationStamp instance;

    private final AetherionQuests plugin;
    private final Map<UUID, Run> running = new ConcurrentHashMap<>();

    private GraduationStamp(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    public static void init(AetherionQuests plugin) {
        if (plugin == null || instance != null) {
            return;
        }
        GraduationStamp created = new GraduationStamp(plugin);
        plugin.getServer().getPluginManager().registerEvents(created, plugin);
        instance = created;
    }

    public static void shutdown() {
        GraduationStamp current = instance;
        instance = null;
        if (current == null) {
            return;
        }
        for (UUID id : new ArrayList<>(current.running.keySet())) {
            current.flush(id);
        }
    }

    /**
     * Play the stamp. {@code onStamp} runs exactly once — on the hit, or immediately if
     * the scene is cut short. Returns false if nothing was started (caller runs it).
     */
    public static boolean play(Player player, Runnable onStamp) {
        GraduationStamp current = instance;
        if (current == null || player == null || !player.isOnline() || player.isDead()
                || !current.plugin.isEnabled()) {
            return false;
        }
        return current.start(player, onStamp);
    }

    private boolean start(Player player, Runnable onStamp) {
        flush(player.getUniqueId());
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return false;
        }
        Location ledger = NpcPresence.locate("ledger");
        boolean ledgerNear = ledger != null
                && world.equals(ledger.getWorld())
                && ledger.distanceSquared(player.getLocation()) <= LEDGER_RANGE * LEDGER_RANGE;

        Vector toPlayer;
        Location desk;
        if (ledgerNear) {
            toPlayer = player.getLocation().toVector().subtract(ledger.toVector()).setY(0);
            if (toPlayer.lengthSquared() < 1.0e-4) {
                toPlayer = eye.getDirection().multiply(-1).setY(0);
            }
            toPlayer.normalize();
            double gap = Math.sqrt(ledger.distanceSquared(player.getLocation()));
            double reach = Math.max(0.6, Math.min(1.1, gap * 0.45));
            desk = ledger.clone().add(toPlayer.clone().multiply(reach)).add(0.0, 1.0, 0.0);
        } else {
            Vector forward = eye.getDirection().setY(0);
            if (forward.lengthSquared() < 1.0e-4) {
                forward = new Vector(0, 0, 1);
            }
            forward.normalize();
            toPlayer = forward.clone().multiply(-1);
            desk = eye.clone().add(forward.multiply(1.5)).add(0.0, -0.6, 0.0);
        }
        desk.setYaw(0f);
        desk.setPitch(0f);

        Run run = new Run(player.getUniqueId(), desk, (float) Math.atan2(toPlayer.getX(), toPlayer.getZ()), onStamp);
        running.put(player.getUniqueId(), run);
        run.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(run), 1L, 1L);
        return true;
    }

    private void tick(Run run) {
        Player player = plugin.getServer().getPlayer(run.playerId);
        if (player == null || !player.isOnline()) {
            flush(run.playerId);
            return;
        }
        try {
            if (player.getWorld() != run.desk.getWorld()) {
                flush(run.playerId);
                return;
            }
            step(player, run);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Graduation stamp hiccup (" + exception.getMessage() + ") — filing directly.");
            flush(run.playerId);
        }
    }

    private void step(Player player, Run run) {
        long t = ++run.tick;
        if (t == T_SLIP) {
            run.slip.add(spawn(player, run.desk, SLIP, slipFrame(run, 0.0f, 0.02f, 0f).mul(slipLocal())));
            run.slip.add(spawn(player, run.desk, INK, slipFrame(run, 0.0f, 0.02f, 0f).mul(hidden(sealDiamond()))));
            run.slip.add(spawn(player, run.desk, INK, slipFrame(run, 0.0f, 0.02f, 0f).mul(hidden(sealSquare()))));
            player.playSound(run.desk, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.7f, 1.05f);
        } else if (t == T_SLIP_GROW) {
            // Interpolation needs a tick between spawn and the first change.
            animateSlip(run, 0.0f, 1.0f, 0f, 5, false);
        } else if (t == T_STAMP) {
            run.stamp.add(spawn(player, run.desk, RUBBER, stampFrame(1.1f, 0.02f).mul(rubber())));
            run.stamp.add(spawn(player, run.desk, BASE, stampFrame(1.1f, 0.02f).mul(base())));
            run.stamp.add(spawn(player, run.desk, HANDLE, stampFrame(1.1f, 0.02f).mul(handle())));
            run.stamp.add(spawn(player, run.desk, BASE, stampFrame(1.1f, 0.02f).mul(knob())));
        } else if (t == T_STAMP_IN) {
            animateStamp(run, 0.85f, 1.0f, 4);
        } else if (t == T_RAISE) {
            // A breath: the stamp lifts a hair before it commits.
            animateStamp(run, 0.95f, 1.0f, 6);
        } else if (t == T_PRESS) {
            animateStamp(run, 0.012f, 1.0f, 2);
        } else if (t == T_HIT) {
            hit(player, run);
        } else if (t == T_LIFT) {
            animateStamp(run, 0.9f, 0.02f, 8);
        } else if (t == T_STAMP_GONE) {
            remove(run.stamp);
        } else if (t == T_SHOW) {
            // Slip stands up and turns to you — read it before it's filed.
            animateSlip(run, 0.32f, 1.1f, (float) Math.toRadians(70.0), 8, true);
            player.playSound(run.desk, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.5f, 1.3f);
        } else if (t == T_FILE) {
            Location pocket = player.getLocation().add(0.0, 1.0, 0.0);
            pocket.setYaw(0f);
            pocket.setPitch(0f);
            for (BlockDisplay piece : run.slip) {
                if (piece != null && piece.isValid()) {
                    piece.setTeleportDuration(6);
                    piece.teleport(pocket);
                }
            }
            animateSlip(run, 0.0f, 0.08f, (float) Math.toRadians(70.0), 6, true);
        } else if (t == T_FILED) {
            remove(run.slip);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, SoundCategory.PLAYERS, 0.8f, 1.1f);
            player.sendActionBar(Component.text(
                    LangPack.ui(player, "graduation_filed", "✔ Orientation filed · the map is yours"),
                    NamedTextColor.LIGHT_PURPLE
            ));
        } else if (t == T_BELL_1) {
            harbourBell(player, 0.71f);
        } else if (t == T_BELL_2) {
            harbourBell(player, 0.94f);
        } else if (t >= T_END) {
            finish(run);
        }
    }

    private void hit(Player player, Run run) {
        Location at = run.desk.clone().add(0.0, 0.03, 0.0);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.PLAYERS, 0.9f, 0.55f);
        player.playSound(at, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.55f, 0.72f);
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2.0 / 10.0);
            player.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 0.16, 0.0, Math.sin(a) * 0.16),
                    1, 0.0, 0.0, 0.0, 0.0, INK_MOTE);
        }
        // Ink shows once the stamp comes up — seal grows in under it.
        animateSlip(run, 0.0f, 1.0f, 0f, 1, true);
        fire(run);
    }

    /** Two bells from "down at the harbour" — quiet, low, a beat apart. */
    private static void harbourBell(Player player, float pitch) {
        Location far = player.getLocation().add(player.getLocation().getDirection().setY(0).multiply(-6.0));
        player.playSound(far, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 0.32f, pitch);
    }

    /* =========================================================
     * FRAMES — matrix only (setTransformationMatrix)
     * ========================================================= */

    /** Slip frame: lift above the desk, turn to the player, tilt up, scale. */
    private static Matrix4f slipFrame(Run run, float lift, float scale, float tilt) {
        return new Matrix4f()
                .translate(0.0f, lift, 0.0f)
                .rotateY(run.yaw)
                .rotateX(tilt)
                .scale(scale);
    }

    private static Matrix4f slipLocal() {
        return new Matrix4f().translate(-0.2f, 0.0f, -0.14f).scale(0.4f, 0.008f, 0.28f);
    }

    private static Matrix4f sealDiamond() {
        return new Matrix4f()
                .translate(0.0f, 0.009f, 0.02f)
                .rotateY((float) Math.toRadians(45.0))
                .translate(-0.05f, 0.0f, -0.05f)
                .scale(0.1f, 0.003f, 0.1f);
    }

    private static Matrix4f sealSquare() {
        return new Matrix4f().translate(-0.045f, 0.0095f, -0.025f).scale(0.09f, 0.003f, 0.09f);
    }

    /** Near-zero, never zero — a singular matrix does not decompose cleanly. */
    private static Matrix4f hidden(Matrix4f local) {
        return new Matrix4f(local).scale(0.001f);
    }

    private static Matrix4f stampFrame(float height, float scale) {
        return new Matrix4f().translate(0.0f, height, 0.0f).scale(scale);
    }

    private static Matrix4f rubber() {
        return new Matrix4f().translate(-0.075f, 0.0f, -0.075f).scale(0.15f, 0.02f, 0.15f);
    }

    private static Matrix4f base() {
        return new Matrix4f().translate(-0.09f, 0.02f, -0.09f).scale(0.18f, 0.05f, 0.18f);
    }

    private static Matrix4f handle() {
        return new Matrix4f().translate(-0.03f, 0.07f, -0.03f).scale(0.06f, 0.18f, 0.06f);
    }

    private static Matrix4f knob() {
        return new Matrix4f().translate(-0.055f, 0.25f, -0.055f).scale(0.11f, 0.07f, 0.11f);
    }

    /** Slip + seal share one frame. Seal stays hidden (scale 0) until the stamp has hit. */
    private void animateSlip(Run run, float lift, float scale, float tilt, int ticks, boolean inked) {
        Matrix4f frame = slipFrame(run, lift, scale, tilt);
        Matrix4f[] locals = {slipLocal(), sealDiamond(), sealSquare()};
        for (int i = 0; i < run.slip.size() && i < locals.length; i++) {
            BlockDisplay piece = run.slip.get(i);
            if (piece == null || !piece.isValid()) {
                continue;
            }
            Matrix4f local = (i == 0 || inked) ? locals[i] : hidden(locals[i]);
            piece.setInterpolationDelay(0);
            piece.setInterpolationDuration(ticks);
            piece.setTransformationMatrix(new Matrix4f(frame).mul(local));
        }
    }

    private void animateStamp(Run run, float height, float scale, int ticks) {
        Matrix4f frame = stampFrame(height, scale);
        Matrix4f[] locals = {rubber(), base(), handle(), knob()};
        for (int i = 0; i < run.stamp.size() && i < locals.length; i++) {
            BlockDisplay piece = run.stamp.get(i);
            if (piece == null || !piece.isValid()) {
                continue;
            }
            piece.setInterpolationDelay(0);
            piece.setInterpolationDuration(ticks);
            piece.setTransformationMatrix(new Matrix4f(frame).mul(locals[i]));
        }
    }

    private BlockDisplay spawn(Player viewer, Location at, BlockData block, Matrix4f matrix) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setBlock(block);
            spawned.setBrightness(new Display.Brightness(13, 15));
            spawned.setShadowRadius(0f);
            spawned.setTransformationMatrix(matrix);
            spawned.addScoreboardTag(TAG);
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    /* =========================================================
     * LIFECYCLE
     * ========================================================= */

    private void fire(Run run) {
        if (run.fired) {
            return;
        }
        run.fired = true;
        if (run.onStamp != null) {
            try {
                run.onStamp.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Graduation callback failed: " + exception.getMessage());
            }
        }
    }

    private void finish(Run run) {
        if (run.task != null) {
            run.task.cancel();
        }
        remove(run.stamp);
        remove(run.slip);
        running.remove(run.playerId, run);
    }

    private void flush(UUID playerId) {
        Run run = running.remove(playerId);
        if (run == null) {
            return;
        }
        if (run.task != null) {
            run.task.cancel();
        }
        remove(run.stamp);
        remove(run.slip);
        fire(run);
    }

    private static void remove(List<BlockDisplay> pieces) {
        for (BlockDisplay piece : pieces) {
            if (piece != null && piece.isValid()) {
                piece.remove();
            }
        }
        pieces.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    private static final class Run {
        private final UUID playerId;
        private final Location desk;
        private final float yaw;
        private final Runnable onStamp;
        private final List<BlockDisplay> slip = new ArrayList<>(3);
        private final List<BlockDisplay> stamp = new ArrayList<>(4);
        private BukkitTask task;
        private long tick;
        private boolean fired;

        private Run(UUID playerId, Location desk, float yaw, Runnable onStamp) {
            this.playerId = playerId;
            this.desk = desk;
            this.yaw = yaw;
            this.onStamp = onStamp;
        }
    }
}
