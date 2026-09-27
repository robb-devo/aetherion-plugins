package de.aetherion.quests.reward;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.NpcPresence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starter kit handoff as a small scene instead of a silent inventory drop.
 * <p>
 * Each piece leaves the giver's hands (Egon for the kit, the Forager for the axe),
 * settles into a little equipment layout in front of the player — armour stacked like
 * a mannequin, tools fanned to the sides — then flies into the player one by one.
 * The item is granted <em>when it lands</em>; armour goes straight onto an empty slot
 * ("Equipper" — Egon suits you up).
 * <p>
 * Safety: only the listener sees the displays (non-persistent). Quit, world change,
 * walking off, or plugin disable flushes every un-landed piece straight into the
 * inventory, so the kit can never be lost to the show.
 * <p>
 * Items themselves still come from {@link StarterGearReward} → {@code CustomItem}
 * (StarterSetBalance untouched). This class only decides <em>when</em> they arrive.
 */
public final class StarterKitCeremony implements Listener {

    public static final String TAG = "ae_kit_ceremony";

    /** Finale copy. */
    enum Finale {
        KIT,
        AXE
    }

    private static final long SPAWN_GAP = 4L;
    private static final int GLIDE = 10;
    private static final long HOLD = 12L;
    private static final long COLLECT_GAP = 4L;
    private static final int FLY = 6;
    /** Giver must be this close for the pieces to start in their hands. */
    private static final double GIVER_RANGE = 14.0;
    /** Walk further than this from the layout and the kit just lands in your bag. */
    private static final double LEASH = 24.0;

    private static final float ICON_SCALE = 0.46f;

    /** Rising G-major pentatonic on bell — one note per piece (note-block pitches). */
    private static final float[] MOTIF = {0.5297f, 0.5946f, 0.6674f, 0.7937f, 0.8909f, 1.0595f, 1.1892f, 1.3348f};

    private static volatile StarterKitCeremony instance;

    private final AetherionQuests plugin;
    private final Map<UUID, Run> running = new ConcurrentHashMap<>();

    private StarterKitCeremony(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    public static void init(AetherionQuests plugin) {
        if (plugin == null || instance != null) {
            return;
        }
        StarterKitCeremony created = new StarterKitCeremony(plugin);
        plugin.getServer().getPluginManager().registerEvents(created, plugin);
        instance = created;
    }

    /** Plugin disable: every running ceremony lands instantly. */
    public static void shutdown() {
        StarterKitCeremony current = instance;
        instance = null;
        if (current == null) {
            return;
        }
        for (UUID id : new ArrayList<>(current.running.keySet())) {
            current.flush(id);
        }
    }

    /**
     * Start a handoff. When this returns {@code true} the ceremony owns granting every
     * stack in {@code kit}; when {@code false} the caller must grant them itself.
     */
    static boolean present(Player player, String giverNpcId, List<ItemStack> kit, Finale finale) {
        StarterKitCeremony current = instance;
        if (current == null
                || player == null
                || !player.isOnline()
                || player.isDead()
                || kit == null
                || kit.isEmpty()
                || !current.plugin.isEnabled()) {
            return false;
        }
        return current.start(player, giverNpcId, kit, finale);
    }

    /* =========================================================
     * RUN
     * ========================================================= */

    private boolean start(Player player, String giverNpcId, List<ItemStack> kit, Finale finale) {
        // Two handoffs can't overlap — land the old one first.
        flush(player.getUniqueId());

        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return false;
        }

        Location giver = NpcPresence.locate(giverNpcId);
        boolean giverNear = giver != null
                && giver.getWorld() != null
                && giver.getWorld().equals(world)
                && giver.distanceSquared(player.getLocation()) <= GIVER_RANGE * GIVER_RANGE;

        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-4 && giverNear) {
            forward = giver.toVector().subtract(eye.toVector()).setY(0);
        }
        if (forward.lengthSquared() < 1.0e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());

        double reach = 2.2;
        if (giverNear) {
            double toGiver = Math.sqrt(giver.distanceSquared(player.getLocation()));
            reach = Math.max(1.6, Math.min(2.4, toGiver * 0.6));
        }
        Location center = eye.clone().add(forward.clone().multiply(reach)).add(0.0, -0.35, 0.0);

        Location origin;
        if (giverNear) {
            // Out of the giver's hands, nudged toward the player.
            Vector towardPlayer = eye.toVector().subtract(giver.toVector()).setY(0);
            if (towardPlayer.lengthSquared() > 1.0e-4) {
                towardPlayer.normalize().multiply(0.45);
            }
            origin = giver.clone().add(towardPlayer).add(0.0, 1.25, 0.0);
        } else {
            origin = center.clone().add(0.0, -0.9, 0.0);
        }

        Run run = new Run(player.getUniqueId(), finale, center);
        List<Slot> slots = layout(kit, center, forward, right);
        for (int i = 0; i < kit.size(); i++) {
            ItemStack stack = kit.get(i);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            run.pieces.add(new Piece(stack.clone(), slots.get(i)));
        }
        if (run.pieces.isEmpty()) {
            return false;
        }
        run.origin = origin;
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
            if (player.getWorld() != run.center.getWorld()
                    || player.getLocation().distanceSquared(run.center) > LEASH * LEASH) {
                flush(run.playerId);
                return;
            }
            step(player, run);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Kit ceremony hiccup (" + exception.getMessage() + ") — landing kit directly.");
            flush(run.playerId);
        }
    }

    private void step(Player player, Run run) {
        long t = ++run.tick;
        int n = run.pieces.size();
        long lastSpawn = SPAWN_GAP * n;
        // Last piece starts gliding two ticks after it spawns.
        long holdStart = lastSpawn + 2 + GLIDE;
        long collectStart = holdStart + HOLD;
        long lastLand = collectStart + COLLECT_GAP * (n - 1) + FLY;

        for (int i = 0; i < n; i++) {
            Piece piece = run.pieces.get(i);
            long spawnAt = SPAWN_GAP * (i + 1);
            long moveAt = spawnAt + 2;
            long flyAt = collectStart + COLLECT_GAP * i;
            long landAt = flyAt + FLY;

            if (t == spawnAt) {
                piece.display = spawnPiece(player, run.origin, piece.stack);
            } else if (t == moveAt) {
                layOut(player, piece, i);
            } else if (t == flyAt) {
                fly(player, piece);
            } else if (t == landAt) {
                land(player, run, piece, i);
            }
        }

        if (t == holdStart) {
            presentAll(player, run);
        }
        if (t == lastLand + 4) {
            finale(player, run);
        }
        if (t >= lastLand + 6) {
            finish(run);
        }
    }

    private ItemDisplay spawnPiece(Player player, Location origin, ItemStack stack) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        Location at = origin.clone();
        at.setYaw(0f);
        at.setPitch(0f);
        ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setItemStack(stack.clone());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setShadowRadius(0f);
            spawned.setTeleportDuration(GLIDE);
            spawned.setTransformationMatrix(pose(0.02f, 0f));
            spawned.addScoreboardTag(TAG);
        });
        player.showEntity(plugin, display);
        return display;
    }

    private void layOut(Player player, Piece piece, int index) {
        ItemDisplay display = piece.display;
        if (display == null || !display.isValid()) {
            return;
        }
        display.teleport(piece.slot.at);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(GLIDE);
        display.setTransformationMatrix(pose(ICON_SCALE, piece.slot.roll));

        Location at = piece.slot.at;
        float note = MOTIF[Math.min(index, MOTIF.length - 1)];
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.22f, note);
        if (isArmour(piece.stack)) {
            player.playSound(at, Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.45f, 1.0f + index * 0.04f);
        } else {
            player.playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.12f, 1.9f);
        }
    }

    /** Everything is laid out — one breath before it comes to you. */
    private void presentAll(Player player, Run run) {
        for (Piece piece : run.pieces) {
            ItemDisplay display = piece.display;
            if (display == null || !display.isValid()) {
                continue;
            }
            // Flights are quicker than the glide out; set a tick before the first flight.
            display.setTeleportDuration(FLY);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) HOLD);
            display.setTransformationMatrix(pose(ICON_SCALE * 1.08f, piece.slot.roll * 0.5f));
            player.spawnParticle(Particle.WAX_OFF, piece.slot.at, 1, 0.08, 0.08, 0.08, 0.0);
        }
        player.playSound(run.center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35f, 1.2f);
    }

    private void fly(Player player, Piece piece) {
        ItemDisplay display = piece.display;
        if (display == null || !display.isValid()) {
            return;
        }
        Location chest = player.getLocation().add(0.0, 1.05, 0.0);
        chest.setYaw(0f);
        chest.setPitch(0f);
        display.teleport(chest);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(FLY);
        display.setTransformationMatrix(pose(0.08f, 0f));
    }

    private void land(Player player, Run run, Piece piece, int index) {
        removeDisplay(piece);
        if (piece.granted) {
            return;
        }
        piece.granted = true;
        boolean worn = grant(player, piece.stack);
        run.wornAny |= worn;
        float pitch = 1.0f + Math.min(index, 7) * 0.08f;
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, pitch);
        if (worn) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.35f, 1.1f);
        }
    }

    private void finale(Player player, Run run) {
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.35f, 1.0595f);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.28f, 1.3348f);
        player.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.3f, 1.35f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0.0, 1.0, 0.0), 8, 0.4, 0.5, 0.4, 0.0);

        String line;
        if (run.finale == Finale.AXE) {
            line = LangPack.ui(player, "kit_axe_landed", "✔ Simple Axe · yours to keep");
        } else if (run.wornAny) {
            line = LangPack.ui(player, "kit_landed_worn", "✔ Kit issued · armour's on, tools are in your bag");
        } else {
            line = LangPack.ui(player, "kit_landed", "✔ Kit issued · check your inventory");
        }
        player.sendActionBar(Component.text(line, NamedTextColor.GREEN));
    }

    private void finish(Run run) {
        if (run.task != null) {
            run.task.cancel();
            run.task = null;
        }
        for (Piece piece : run.pieces) {
            removeDisplay(piece);
        }
        running.remove(run.playerId, run);
    }

    /** Land every piece now — no show. Safe to call any time. */
    private void flush(UUID playerId) {
        Run run = running.remove(playerId);
        if (run == null) {
            return;
        }
        if (run.task != null) {
            run.task.cancel();
            run.task = null;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        for (Piece piece : run.pieces) {
            removeDisplay(piece);
            if (!piece.granted && player != null) {
                piece.granted = true;
                grant(player, piece.stack);
            }
        }
        if (player == null && run.pieces.stream().anyMatch(piece -> !piece.granted)) {
            plugin.getLogger().warning("Kit ceremony: player " + playerId + " vanished before the kit landed.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    /* =========================================================
     * GRANT
     * ========================================================= */

    /** @return true if it went straight onto an empty armour slot. */
    private static boolean grant(Player player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        String name = stack.getType().name();
        if (name.endsWith("_HELMET") && isEmpty(inventory.getHelmet())) {
            inventory.setHelmet(stack);
            return true;
        }
        if (name.endsWith("_CHESTPLATE") && isEmpty(inventory.getChestplate())) {
            inventory.setChestplate(stack);
            return true;
        }
        if (name.endsWith("_LEGGINGS") && isEmpty(inventory.getLeggings())) {
            inventory.setLeggings(stack);
            return true;
        }
        if (name.endsWith("_BOOTS") && isEmpty(inventory.getBoots())) {
            inventory.setBoots(stack);
            return true;
        }
        StarterGearReward.give(player, stack);
        return false;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    /* =========================================================
     * LAYOUT
     * ========================================================= */

    /**
     * Armour stacked like a mannequin in the middle; tools fanned left/right.
     * Returned list is parallel to {@code kit}.
     */
    private static List<Slot> layout(List<ItemStack> kit, Location center, Vector forward, Vector right) {
        List<Slot> slots = new ArrayList<>(kit.size());
        int tools = 0;
        for (ItemStack stack : kit) {
            if (stack == null) {
                slots.add(new Slot(center.clone(), 0f));
                continue;
            }
            int armourRow = armourRow(stack);
            if (kit.size() == 1) {
                slots.add(new Slot(center.clone().add(0.0, 0.1, 0.0), 0f));
                continue;
            }
            if (armourRow >= 0) {
                double y = 0.52 - armourRow * 0.44;
                slots.add(new Slot(center.clone().add(0.0, y, 0.0), 0f));
                continue;
            }
            int side = tools % 2 == 0 ? -1 : 1;
            int row = tools / 2;
            tools++;
            double y = 0.25 - row * 0.62;
            Location at = center.clone()
                    .add(right.clone().multiply(0.95 * side))
                    .add(forward.clone().multiply(-0.18))
                    .add(0.0, y, 0.0);
            slots.add(new Slot(at, side < 0 ? 18f : -18f));
        }
        for (Slot slot : slots) {
            slot.at.setYaw(0f);
            slot.at.setPitch(0f);
        }
        return slots;
    }

    private static int armourRow(ItemStack stack) {
        String name = stack.getType().name();
        if (name.endsWith("_HELMET")) {
            return 0;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return 1;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 2;
        }
        if (name.endsWith("_BOOTS")) {
            return 3;
        }
        return -1;
    }

    private static boolean isArmour(ItemStack stack) {
        return stack != null && armourRow(stack) >= 0;
    }

    /** Billboarded icon: uniform scale + a little roll. Matrix only — no TRS decompose. */
    private static Matrix4f pose(float scale, float rollDeg) {
        return new Matrix4f()
                .rotateZ((float) Math.toRadians(rollDeg))
                .scale(scale);
    }

    private static void removeDisplay(Piece piece) {
        if (piece.display != null) {
            if (piece.display.isValid()) {
                piece.display.remove();
            }
            piece.display = null;
        }
    }

    /* =========================================================
     * STATE
     * ========================================================= */

    private static final class Run {
        final UUID playerId;
        final Finale finale;
        final Location center;
        final List<Piece> pieces = new ArrayList<>();
        Location origin;
        BukkitTask task;
        long tick;
        boolean wornAny;

        Run(UUID playerId, Finale finale, Location center) {
            this.playerId = playerId;
            this.finale = finale;
            this.center = center;
        }
    }

    private static final class Piece {
        final ItemStack stack;
        final Slot slot;
        ItemDisplay display;
        boolean granted;

        Piece(ItemStack stack, Slot slot) {
            this.stack = stack;
            this.slot = slot;
        }
    }

    private record Slot(Location at, float roll) {
    }
}
