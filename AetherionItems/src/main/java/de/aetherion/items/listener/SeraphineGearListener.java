package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.combat.ScriptedHits;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seraphine, the Hanging Saint: her gear. Every beat here is borrowed from her fight.
 *
 * <p><b>Severance, the Saint's Needle</b> ({@code seraphine_needle}), the needle she cut her
 * own strings with.
 * <ul>
 *   <li><b>Five Strings</b> (right-click, 14s): a cloud opens over your aim and five ivory
 *       fingertips of the Hand Above slide out of it, one wearing a gold thimble. Each drops a
 *       golden thread onto a foe (a single foe is strung the way she was: wrists, head, knees)
 *       while the first bar of her lullaby plays. The threads snap taut and hoist, then jerk the
 *       foes three times in stop-motion on the next three notes. Then the threads are cut, the
 *       fingers recoil as if stung, and the foes drop and shatter like porcelain.</li>
 *   <li><b>Kintsugi Stitch</b> (passive): every fifth melee hit on the same foe sews a gold seam
 *       through it, pulls it tight for bonus damage and plays the next note of the lullaby.</li>
 * </ul>
 *
 * <p><b>The Mended Saint</b> (veil, bodice, bell skirt, pointe slippers), full set bonus
 * <b>Strung</b>: a golden thread from the flies catches every fall.
 */
public final class SeraphineGearListener implements Listener {

    private static final String NEEDLE = "seraphine_needle";
    private static final String[] SET = {
            "seraphine_veil", "seraphine_bodice", "seraphine_bell_skirt", "seraphine_pointe_slippers"
    };

    private static final long COOLDOWN_TICKS = 280L; // 14s
    private static final int STITCH_HITS = 5;

    /** Her lullaby, note for note (BossEngine instance.saint.MusicBox.THEME). 0 = F#, -1 = rest. */
    private static final int[] LULLABY = {
            22, 18, 15, 17, 14, 17,
            18, 15, 10, 12, -1, -1,
            22, 18, 15, 20, 17, 14,
            15, 13, 10, 10, -1, -1,
    };

    private static final Color GOLD = Color.fromRGB(255, 214, 110);
    private static final Color WHITE = Color.fromRGB(255, 250, 240);

    /* Five Strings timeline, in ticks. */
    private static final float SKY = 11f;
    private static final int HOOK = 10;
    private static final int HOIST = 26;
    private static final int[] JERKS = {33, 39, 45};
    private static final int CUT = 53;
    private static final int LAND = 60;
    private static final int END = 84;
    private static final int SEGMENTS = 6;

    /**
     * The Hand Above, palm down, seen from the caster: index, middle, ring, pinky, thumb.
     * {side offset, forward offset, finger length}. Finger i threads the limb it owns in the
     * fight: right wrist, head, left wrist, left knee, right knee.
     */
    private static final float[][] FINGERS = {
            {-0.95f, 0.35f, 1.9f},
            {-0.3f, 0.5f, 2.15f},
            {0.35f, 0.4f, 1.95f},
            {0.95f, 0.15f, 1.55f},
            {-1.8f, -0.7f, 1.3f},
    };

    /** Every display this listener has alive, so a disable mid-cast leaves nothing behind. */
    private static final Set<Display> LIVE = ConcurrentHashMap.newKeySet();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> stitchTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> stitchCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> melody = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastCatch = new ConcurrentHashMap<>();

    public SeraphineGearListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    /** Plugin disable: pull every thread and finger out of the world. */
    public static void clearAll() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    /* ================================================================== Five Strings */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!isNeedle(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        if (busy.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("§6Five Strings §7are still held…"));
            return;
        }
        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(Component.text("§6Severance §7rethreading… §f" + left + "s"));
            return;
        }

        double damage = Math.max(40.0, equipmentStats.getStat(player, ItemCapability.DAMAGE));
        if (!castFiveStrings(player, damage)) {
            return;
        }
        int cooldown = ProgressionEffects.cooldownTicks(player, itemManager, (int) COOLDOWN_TICKS);
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
    }

    private boolean castFiveStrings(Player player, double damage) {
        Location focus = focusAhead(player, 8.0);
        World world = focus.getWorld();
        if (world == null) {
            return false;
        }
        List<LivingEntity> marks = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(focus, 9.0, 7.0, 9.0)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || !entity.isValid() || entity.isDead()) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) > 20.0 * 20.0) {
                continue;
            }
            marks.add((LivingEntity) entity);
        }
        if (marks.isEmpty()) {
            player.sendActionBar(Component.text("§6Five Strings §7— nothing on the stage to string."));
            return false;
        }
        marks.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(focus)));
        if (marks.size() > 5) {
            marks = new ArrayList<>(marks.subList(0, 5));
        }
        busy.add(player.getUniqueId());
        new FiveStrings(player, marks, damage).runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private final class FiveStrings extends BukkitRunnable {

        private final Player caster;
        private final double damage;
        private final World world;
        private final Location anchor;
        private final Vector3f side;
        private final LivingEntity[] held = new LivingEntity[5];
        private final Set<LivingEntity> strung = new LinkedHashSet<>();
        private final Vector3f[] knuckle = new Vector3f[5];
        private final Vector3f[] tip = new Vector3f[5];
        private final Vector3f[] lastEnd = new Vector3f[5];
        private final float[] wobble = new float[5];
        private final BlockDisplay[] finger = new BlockDisplay[5];
        private final BlockDisplay[] band = new BlockDisplay[5];
        private final BlockDisplay[][] thread = new BlockDisplay[5][SEGMENTS];
        private final List<BlockDisplay> owned = new ArrayList<>();
        private final Map<UUID, Location> aloft = new HashMap<>();
        private final BlockDisplay thimble;
        private float slack = 0.9f;
        private int tick;
        private boolean finished;

        FiveStrings(Player caster, List<LivingEntity> marks, double damage) {
            this.caster = caster;
            this.damage = damage;
            this.strung.addAll(marks);

            // The Hand hovers over the middle of the marks, measured from the lowest pair of feet.
            double x = 0;
            double z = 0;
            double y = Double.MAX_VALUE;
            for (LivingEntity mark : marks) {
                Location l = mark.getLocation();
                x += l.getX();
                z += l.getZ();
                y = Math.min(y, l.getY());
            }
            this.world = marks.getFirst().getWorld();
            this.anchor = new Location(world, x / marks.size(), y, z / marks.size());

            Vector toward = anchor.toVector().subtract(caster.getLocation().toVector()).setY(0);
            if (toward.lengthSquared() < 1e-4) {
                toward = caster.getLocation().getDirection().setY(0);
            }
            if (toward.lengthSquared() < 1e-4) {
                toward = new Vector(0, 0, 1);
            }
            toward.normalize();
            Vector3f forward = new Vector3f((float) toward.getX(), 0f, (float) toward.getZ());
            this.side = new Vector3f(-forward.z, 0f, forward.x);

            for (int f = 0; f < 5; f++) {
                float[] layout = FINGERS[f];
                knuckle[f] = new Vector3f(side).mul(layout[0])
                        .add(new Vector3f(forward).mul(layout[1]))
                        .add(0f, SKY + 2.2f, 0f);
                tip[f] = new Vector3f(knuckle[f]).sub(0f, layout[2], 0f);
                held[f] = marks.get(f % marks.size());
                finger[f] = spawn(Material.SMOOTH_QUARTZ, null);
                band[f] = spawn(Material.GOLD_BLOCK, GOLD);
                for (int i = 0; i < SEGMENTS; i++) {
                    thread[f][i] = spawn(Material.GOLD_BLOCK, GOLD);
                }
            }
            thimble = spawn(Material.GOLD_BLOCK, GOLD);
        }

        @Override
        public void run() {
            tick++;
            if (!caster.isOnline() || caster.getWorld() != world) {
                finish(false);
                return;
            }

            if (tick == 1) {
                openClouds();
                hideFingersInCloud();
            }
            if (tick == 3) {
                // Parked in the cloud last frame: now they slide down out of it.
                placeFingers(0f, 10);
            }
            if (tick <= CUT + 14 && tick % 2 == 0) {
                clouds();
            }
            // First bar of the lullaby while the threads come down.
            if (tick <= 13 && (tick - 1) % 3 == 0) {
                note(anchorAt(0f, SKY, 0f), LULLABY[(tick - 1) / 3], 1.1f);
            }
            for (int f = 0; f < 5; f++) {
                if (tick == HOOK + f * 2 + 4) {
                    hooked(f);
                }
            }
            if (tick == HOIST) {
                hoist();
            }
            if (tick > HOIST && tick < CUT) {
                holdAloft();
            }
            boolean jerked = false;
            for (int k = 0; k < JERKS.length; k++) {
                if (tick == JERKS[k]) {
                    jerk(k);
                    jerked = true;
                }
            }
            if (tick < CUT) {
                // Taut threads move in stop-motion: frame holds, no interpolation.
                boolean stopMotion = tick >= HOIST;
                if (tick >= HOOK && (!stopMotion || jerked || tick % 3 == 0)) {
                    for (int f = 0; f < 5; f++) {
                        renderThread(f, stopMotion ? 0 : 1);
                    }
                }
            }
            if (tick == CUT) {
                cut();
            }
            if (tick == CUT + 6) {
                withdrawFingers();
            }
            if (tick == LAND) {
                land();
            }
            if (tick >= END) {
                finish(true);
            }
        }

        /* ------------------------------------------------------------ beats */

        private void openClouds() {
            Location sky = anchorAt(0f, SKY + 2.4f, 0f);
            world.spawnParticle(Particle.CLOUD, sky, 60, 2.2, 0.35, 2.2, 0.02);
            world.spawnParticle(Particle.WHITE_ASH, sky, 30, 2.5, 0.6, 2.5, 0.01);
            world.playSound(sky, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.2f, 1.6f);
            world.playSound(sky, Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.PLAYERS, 0.7f, semitone(3));
        }

        private void clouds() {
            Location sky = anchorAt(0f, SKY + 2.5f, 0f);
            double angle = tick * 0.45;
            for (int i = 0; i < 6; i++) {
                double a = angle + i * Math.PI / 3.0;
                Location puff = sky.clone().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
                world.spawnParticle(Particle.CLOUD, puff, 2, 0.4, 0.15, 0.4, 0.0);
            }
        }

        private void hooked(int f) {
            Vector3f end = attach(f);
            if (end == null) {
                return;
            }
            Location at = anchorAt(end.x, end.y, end.z);
            world.playSound(at, Sound.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 1.1f, 1.45f + f * 0.08f);
            world.spawnParticle(Particle.DUST, at, 8, 0.12, 0.12, 0.12, 0, new Particle.DustOptions(GOLD, 1.0f));
        }

        /** The tell from the fight: every thread snaps straight at once, then they lift. */
        private void hoist() {
            slack = 0f;
            for (int f = 0; f < 5; f++) {
                wobble[f] = 0.25f;
            }
            Location mid = anchorAt(0f, SKY * 0.5f, 0f);
            world.playSound(mid, Sound.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1.4f, 0.5f);
            world.playSound(mid, Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1.2f, 0.6f);
            world.playSound(mid, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.PLAYERS, 1.2f, 0.5f);
            for (LivingEntity mark : strung) {
                if (!alive(mark) || !movable(mark)) {
                    continue;
                }
                aloft.put(mark.getUniqueId(), mark.getLocation().add(0, 3.6, 0));
                mark.setVelocity(new Vector(0, 0.75, 0));
            }
        }

        private void holdAloft() {
            for (LivingEntity mark : strung) {
                Location goal = aloft.get(mark.getUniqueId());
                if (goal == null || !alive(mark)) {
                    continue;
                }
                Vector pull = goal.toVector().subtract(mark.getLocation().toVector()).multiply(0.3);
                if (pull.lengthSquared() > 0.81) {
                    pull.normalize().multiply(0.9);
                }
                mark.setVelocity(pull.setY(pull.getY() + 0.04));
                mark.setFallDistance(0f);
            }
        }

        /** Stop-motion: a jerk on the strings, one note of the lullaby, porcelain tinks. */
        private void jerk(int k) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            note(anchorAt(0f, SKY, 0f), LULLABY[5 + k], 1.2f);
            float flick = k % 2 == 0 ? 0.3f : -0.3f;
            for (int f = 0; f < 5; f++) {
                wobble[f] = 0.45f;
                tip[f].y += flick * (f % 2 == 0 ? 1f : -1f);
            }
            placeFingers(0f, 0);
            for (LivingEntity mark : strung) {
                if (!alive(mark)) {
                    continue;
                }
                if (aloft.containsKey(mark.getUniqueId())) {
                    double sway = (random.nextBoolean() ? 1 : -1) * random.nextDouble(0.4, 0.7);
                    mark.setVelocity(new Vector(side.x * sway, 0.35, side.z * sway));
                }
                Location chest = mark.getLocation().add(0, mark.getHeight() * 0.6, 0);
                world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.2f,
                        1.2f + random.nextFloat() * 0.4f);
                world.playSound(chest, Sound.BLOCK_GLASS_HIT, SoundCategory.PLAYERS, 0.9f, 1.6f);
                world.spawnParticle(Particle.DUST, chest, 6, 0.25, 0.3, 0.25, 0, new Particle.DustOptions(WHITE, 0.9f));
                world.spawnParticle(Particle.DUST, chest, 4, 0.2, 0.25, 0.2, 0, new Particle.DustOptions(GOLD, 0.8f));
                hurt(mark, damage * 0.45);
            }
        }

        /** Severance: the upper halves recoil into the sky, the lower halves fall away. */
        private void cut() {
            for (int f = 0; f < 5; f++) {
                Vector3f top = threadTop(f);
                Vector3f end = attach(f);
                if (end == null) {
                    end = lastEnd[f] != null ? lastEnd[f] : new Vector3f(top.x, 0f, top.z);
                }
                for (int i = 0; i < SEGMENTS; i++) {
                    float t0 = i / (float) SEGMENTS;
                    float t1 = (i + 1) / (float) SEGMENTS;
                    Vector3f p0 = point(f, top, end, t0);
                    Vector3f p1 = point(f, top, end, t1);
                    if (t1 <= 0.5f) {
                        p0.y += 9f;
                        p1.y += 9f;
                        push(thread[f][i], beam(p0, p1, 0.02f), 14);
                    } else {
                        Vector3f q0 = new Vector3f(p0.x, 0.05f, p0.z);
                        Vector3f q1 = new Vector3f(p1.x + (p1.y - p0.y) * 0.3f, 0.05f, p1.z);
                        push(thread[f][i], beam(q0, q1, 0.06f), 8);
                    }
                }
                // Each finger recoils as if stung.
                knuckle[f].y += 1.2f;
                tip[f].y += 1.2f;
            }
            placeFingers(0f, 3);
            Location mid = anchorAt(0f, SKY * 0.5f, 0f);
            world.playSound(mid, Sound.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.3f, 0.75f);
            world.playSound(mid, Sound.BLOCK_TRIPWIRE_DETACH, SoundCategory.PLAYERS, 1.4f, 0.5f);
            note(mid, LULLABY[8], 1.0f);
            for (LivingEntity mark : strung) {
                if (alive(mark) && aloft.containsKey(mark.getUniqueId())) {
                    mark.setVelocity(new Vector(0, -2.3, 0));
                }
            }
            aloft.clear();
        }

        /** Dropped porcelain. */
        private void land() {
            for (LivingEntity mark : strung) {
                if (!alive(mark)) {
                    continue;
                }
                Location at = mark.getLocation().add(0, 0.4, 0);
                world.spawnParticle(Particle.BLOCK, at, 36, 0.5, 0.4, 0.5, 0,
                        Material.WHITE_GLAZED_TERRACOTTA.createBlockData());
                world.spawnParticle(Particle.BLOCK, at, 20, 0.5, 0.4, 0.5, 0, Material.SMOOTH_QUARTZ.createBlockData());
                world.spawnParticle(Particle.DUST, at, 24, 0.7, 0.5, 0.7, 0, new Particle.DustOptions(GOLD, 1.5f));
                world.spawnParticle(Particle.DUST, at, 18, 0.7, 0.5, 0.7, 0, new Particle.DustOptions(WHITE, 1.2f));
                world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
                world.playSound(at, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
                world.playSound(at, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
                world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
                hurt(mark, damage * 2.4 + 40.0);
            }
        }

        private void withdrawFingers() {
            for (int f = 0; f < 5; f++) {
                knuckle[f].y += 8f;
                tip[f].y += 8f;
            }
            placeFingers(1f, 18);
            world.spawnParticle(Particle.CLOUD, anchorAt(0f, SKY + 2.4f, 0f), 40, 2.0, 0.3, 2.0, 0.01);
        }

        /* ------------------------------------------------------------ drawing */

        private void hideFingersInCloud() {
            for (int f = 0; f < 5; f++) {
                Vector3f cloud = new Vector3f(knuckle[f]).add(0f, 1.2f, 0f);
                push(finger[f], gone(cloud), 0);
                push(band[f], gone(cloud), 0);
            }
            push(thimble, gone(new Vector3f(knuckle[1]).add(0f, 1.2f, 0f)), 0);
        }

        /** @param shrink 0 = full size, 1 = gone (pulled back into the cloud) */
        private void placeFingers(float shrink, int interp) {
            float keep = Math.max(0.01f, 1f - shrink);
            for (int f = 0; f < 5; f++) {
                float width = (f == 4 ? 0.5f : 0.42f) * keep;
                Vector3f from = new Vector3f(knuckle[f]).add(0f, 0.6f, 0f);
                push(finger[f], beam(from, tip[f], width), interp);
                Vector3f ring = new Vector3f(knuckle[f]).lerp(tip[f], 0.45f);
                push(band[f], box(ring, new Vector3f(width + 0.06f * keep, 0.1f * keep, width + 0.06f * keep)), interp);
            }
            // A seamstress's gold thimble on the middle finger.
            Vector3f cap = new Vector3f(tip[1]).add(0f, 0.14f, 0f);
            float capWidth = 0.47f * keep;
            push(thimble, box(cap, new Vector3f(capWidth, 0.34f * keep, capWidth)), interp);
        }

        private void renderThread(int f, int interp) {
            Vector3f top = threadTop(f);
            Vector3f end = attach(f);
            if (end == null) {
                end = lastEnd[f] != null ? lastEnd[f] : new Vector3f(top.x, 0f, top.z);
            }
            lastEnd[f] = end;
            float reach = clamp01((tick - (HOOK + f * 2)) / 4f);
            wobble[f] *= 0.82f;
            for (int i = 0; i < SEGMENTS; i++) {
                float t0 = i / (float) SEGMENTS;
                float t1 = (i + 1) / (float) SEGMENTS;
                BlockDisplay segment = thread[f][i];
                if (t0 >= reach) {
                    push(segment, gone(top), interp);
                    continue;
                }
                push(segment, beam(point(f, top, end, t0), point(f, top, end, Math.min(t1, reach)), 0.06f), interp);
            }
        }

        private Vector3f threadTop(int f) {
            return new Vector3f(tip[f]).sub(0f, 0.05f, 0f);
        }

        /** Slack threads bow sideways; plucked ones hum. */
        private Vector3f point(int f, Vector3f a, Vector3f b, float t) {
            Vector3f p = new Vector3f(a).lerp(b, t);
            float sign = f % 2 == 0 ? 1f : -1f;
            float bow = sign * slack * 4f * t * (1f - t)
                    + wobble[f] * (float) Math.sin(tick * 1.7f + t * 9f) * (float) Math.sin(Math.PI * t);
            return p.fma(bow, side);
        }

        /** Where finger f's thread ties on: the limb it owns, as on her body. */
        private Vector3f attach(int f) {
            LivingEntity mark = held[f];
            if (!alive(mark)) {
                return null;
            }
            Location l = mark.getLocation();
            float h = (float) mark.getHeight();
            float w = (float) mark.getWidth() * 0.5f;
            Vector3f base = new Vector3f(
                    (float) (l.getX() - anchor.getX()),
                    (float) (l.getY() - anchor.getY()),
                    (float) (l.getZ() - anchor.getZ()));
            return switch (f) {
                case 0 -> base.add(0f, h * 0.62f, 0f).fma(-w, side);
                case 1 -> base.add(0f, h, 0f);
                case 2 -> base.add(0f, h * 0.62f, 0f).fma(w, side);
                case 3 -> base.add(0f, h * 0.3f, 0f).fma(w * 0.5f, side);
                default -> base.add(0f, h * 0.3f, 0f).fma(-w * 0.5f, side);
            };
        }

        /* ------------------------------------------------------------ helpers */

        private BlockDisplay spawn(Material material, Color glow) {
            BlockDisplay display = spawnDisplay(world, anchor, material, glow);
            owned.add(display);
            return display;
        }

        private Location anchorAt(float x, float y, float z) {
            return anchor.clone().add(x, y, z);
        }

        private void hurt(LivingEntity mark, double amount) {
            if (alive(mark)) {
                ScriptedHits.run(() -> mark.damage(amount, caster));
            }
        }

        private void finish(boolean curtain) {
            if (finished) {
                return;
            }
            finished = true;
            for (BlockDisplay display : owned) {
                kill(display);
            }
            owned.clear();
            for (LivingEntity mark : strung) {
                if (alive(mark)) {
                    mark.setFallDistance(0f);
                }
            }
            if (curtain) {
                note(anchorAt(0f, 1.5f, 0f), LULLABY[21], 0.8f);
            }
            busy.remove(caster.getUniqueId());
            cancel();
        }

        private void note(Location at, int note, float volume) {
            chime(world, at, note, volume);
        }
    }

    /* ================================================================== Kintsugi Stitch */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStitch(EntityDamageByEntityEvent event) {
        if (ScriptedHits.isActive() || !(event.getDamager() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!isNeedle(player.getInventory().getItemInMainHand())) {
            return;
        }
        Entity victim = event.getEntity();
        if (!TestPrototypeAbilities.isCombatTarget(victim)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int count = victim.getUniqueId().equals(stitchTarget.get(playerId))
                ? stitchCount.getOrDefault(playerId, 0) + 1
                : 1;
        stitchTarget.put(playerId, victim.getUniqueId());
        LivingEntity living = (LivingEntity) victim;
        if (count < STITCH_HITS) {
            stitchCount.put(playerId, count);
            player.sendActionBar(Component.text("§6✦ §fStitch §6" + "●".repeat(count) + "§8" + "○".repeat(STITCH_HITS - count)));
            victim.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, living.getHeight() * 0.6, 0),
                    3, 0.15, 0.2, 0.15, 0, new Particle.DustOptions(GOLD, 0.7f));
            return;
        }
        stitchCount.put(playerId, 0);
        player.sendActionBar(Component.text("§6✦ §fStitch §6●●●●● §e— pulled tight"));
        double bonus = Math.max(20.0, equipmentStats.getStat(player, ItemCapability.DAMAGE) * 0.6);
        Bukkit.getScheduler().runTask(plugin, () -> sewSeam(player, living, bonus));
    }

    /** Six stitches, shoulder to opposite hip, in and out of the target like a needle through cloth. */
    private void sewSeam(Player player, LivingEntity victim, double bonus) {
        if (!player.isOnline() || !victim.isValid() || victim.isDead()) {
            return;
        }
        World world = victim.getWorld();
        Location base = victim.getLocation();
        double height = victim.getHeight();
        double width = Math.max(0.35, victim.getWidth() * 0.6);
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        Vector across = new Vector(-look.getZ(), 0, look.getX());

        List<Location> points = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            double t = i / 6.0;
            double depth = (i % 2 == 0 ? -1 : 1) * width * 0.55;
            points.add(base.clone()
                    .add(across.clone().multiply((t - 0.5) * 2 * width))
                    .add(look.clone().multiply(depth))
                    .add(0, height * (0.85 - 0.55 * t), 0));
        }
        Particle.DustOptions gold = new Particle.DustOptions(GOLD, 0.8f);
        for (int i = 0; i + 1 < points.size(); i++) {
            Location a = points.get(i);
            Vector step = points.get(i + 1).toVector().subtract(a.toVector());
            int n = Math.max(3, (int) (step.length() * 8));
            for (int k = 0; k <= n; k++) {
                world.spawnParticle(Particle.DUST, a.clone().add(step.clone().multiply(k / (double) n)), 1, 0, 0, 0, 0, gold);
            }
        }
        world.spawnParticle(Particle.END_ROD, points.getFirst(), 2, 0.02, 0.02, 0.02, 0.01);
        world.spawnParticle(Particle.END_ROD, points.getLast(), 2, 0.02, 0.02, 0.02, 0.01);
        chime(world, base.clone().add(0, height * 0.6, 0), nextNote(player), 1.0f);

        // A beat later the thread is pulled tight: the seam flashes and bites.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isValid() || victim.isDead()) {
                return;
            }
            Particle.DustTransition flash = new Particle.DustTransition(GOLD, WHITE, 1.1f);
            for (Location p : points) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 3, 0.05, 0.05, 0.05, 0, flash);
            }
            Location at = victim.getLocation();
            world.playSound(at, Sound.BLOCK_TRIPWIRE_DETACH, SoundCategory.PLAYERS, 0.8f, 1.8f);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0f, 1.5f);
            if (player.isOnline()) {
                ScriptedHits.run(() -> victim.damage(bonus, player));
            }
        }, 3L);
    }

    /** Each finished stitch plays the next note of her lullaby, so a long fight plays the whole tune. */
    private int nextNote(Player player) {
        int index = melody.getOrDefault(player.getUniqueId(), 0);
        int note = -1;
        for (int guard = 0; guard < LULLABY.length && note < 0; guard++) {
            note = LULLABY[index];
            index = (index + 1) % LULLABY.length;
        }
        melody.put(player.getUniqueId(), index);
        return note;
    }

    /* ================================================================== Strung (full set) */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!wearingFullSet(player)) {
            return;
        }
        event.setCancelled(true);
        int now = Bukkit.getCurrentTick();
        Integer last = lastCatch.get(player.getUniqueId());
        if (last != null && now - last < 10) {
            return;
        }
        lastCatch.put(player.getUniqueId(), now);
        catchFall(player);
    }

    /** A golden thread drops from the flies, takes your weight, and is reeled back up. */
    private void catchFall(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Location anchor = new Location(world, at.getX(), at.getY(), at.getZ());
        BlockDisplay line = spawnDisplay(world, anchor, Material.GOLD_BLOCK, GOLD);
        line.setTransformation(beam(new Vector3f(0f, 16f, 0f), new Vector3f(0f, 1.5f, 0f), 0.05f));
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> push(line, beam(new Vector3f(0f, 16f, 0f), new Vector3f(0f, 15.9f, 0f), 0.02f), 12), 4L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> kill(line), 18L);
        world.playSound(at, Sound.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 0.9f, 1.4f);
        world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 0.6f, 1.6f);
        chime(world, at, LULLABY[0], 0.7f);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 1.4, 0), 10, 0.2, 0.3, 0.2, 0, new Particle.DustOptions(GOLD, 0.9f));
    }

    private boolean wearingFullSet(Player player) {
        PlayerInventory inventory = player.getInventory();
        return isPiece(inventory.getHelmet(), SET[0])
                && isPiece(inventory.getChestplate(), SET[1])
                && isPiece(inventory.getLeggings(), SET[2])
                && isPiece(inventory.getBoots(), SET[3]);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stitchTarget.remove(id);
        stitchCount.remove(id);
        lastCatch.remove(id);
    }

    /* ================================================================== shared */

    private boolean isNeedle(ItemStack item) {
        return isPiece(item, NEEDLE);
    }

    private boolean isPiece(ItemStack item, String id) {
        return item != null && id.equalsIgnoreCase(itemManager.getItemId(item));
    }

    private static boolean alive(LivingEntity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private static boolean movable(LivingEntity entity) {
        if (entity.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)) {
            return false;
        }
        AttributeInstance knockback = entity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        return knockback == null || knockback.getValue() < 0.99;
    }

    private static Location focusAhead(Player player, double distance) {
        Location eye = player.getEyeLocation();
        Location aim = eye.clone().add(eye.getDirection().normalize().multiply(distance));
        World world = aim.getWorld();
        if (world != null) {
            int y = world.getHighestBlockYAt(aim);
            if (Math.abs(y - aim.getY()) < 8) {
                aim.setY(y + 1.0);
            }
        }
        return aim;
    }

    private static void chime(World world, Location at, int note, float volume) {
        if (note < 0) {
            return;
        }
        float pitch = semitone(note);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, volume, pitch);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, volume * 0.2f, pitch);
    }

    private static float semitone(int n) {
        return Math.max(0.5f, Math.min(2.0f, (float) Math.pow(2.0, (n - 12) / 12.0)));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static BlockDisplay spawnDisplay(World world, Location anchor, Material material, Color glow) {
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(3f);
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
            d.setTransformation(gone(new Vector3f()));
            if (glow != null) {
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
            }
        });
        LIVE.add(display);
        return display;
    }

    private static void push(Display display, Transformation transformation, int interp) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, interp));
        display.setTransformation(transformation);
    }

    private static void kill(Display display) {
        LIVE.remove(display);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    /**
     * Unit block stretched from a to b. Built as translation + left rotation + scale directly (no
     * matrix decomposition), so client interpolation never spins a thin thread between frames.
     */
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

    /** Axis-aligned unit block centered on {@code center}. */
    private static Transformation box(Vector3f center, Vector3f size) {
        return new Transformation(new Vector3f(center).sub(new Vector3f(size).mul(0.5f)),
                new Quaternionf(), new Vector3f(size), new Quaternionf());
    }

    private static Transformation gone(Vector3f at) {
        return new Transformation(new Vector3f(at), new Quaternionf(), new Vector3f(0.001f), new Quaternionf());
    }
}
