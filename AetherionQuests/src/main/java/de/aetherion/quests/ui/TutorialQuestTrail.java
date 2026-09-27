package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.NpcPresence;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guide path for Egon's first wood loop:
 * Egon → (300,63,-379) → (328,63,-379) → Forager (outbound),
 * reverse when delivering logs back.
 * <p>
 * Near the player: a few painted dock chevrons on the planks (per-player BlockDisplays),
 * a slow pulse rolling forward through them. Beyond them: the old gold dust, so the
 * far end of the route still reads. Same corners, same quests — just better paint.
 * <p>
 * Past the pier (Quartermaster → Foreman → Temper → Miss Ledger → Fields, and the open
 * road Ledger pins after the stamp) there are no authored corners, so the same paint
 * works as a compass: a short run of chevrons from where you stand toward whoever the
 * yellow arrow points at, only on walkable ground with headroom (it stops rather than
 * climb a wall), and a thin beacon in that NPC's colour over their head once they're
 * within sight. The target is the one {@link QuestHint} already chose — or the turn-in
 * NPC once a quest is READY — never a second navigation system.
 */
public final class TutorialQuestTrail {

    private static final double ARRIVED = 4.0;
    private static final double SPACING = 1.15;
    private static final double MAX_AHEAD = 56.0;

    /** Painted chevrons: count, spacing, first one this far ahead of you. */
    private static final int CHEVRONS = 6;
    private static final double CHEVRON_GAP = 2.4;
    private static final double CHEVRON_LEAD = 1.4;
    /** Wander further than this off the route and only the dust stays. */
    private static final double CHEVRON_OFF_PATH = 10.0;
    private static final double CHEVRON_END_MARGIN = 1.6;
    public static final String CHEVRON_TAG = "ae_trail_chevron";

    /** Chevron arm: width, thickness, length, and spread either side of straight back. */
    private static final float ARM_W = 0.09f;
    private static final float ARM_H = 0.02f;
    private static final float ARM_L = 0.42f;
    private static final float ARM_SPREAD = (float) Math.toRadians(40.0);
    /** Chevron centre sits halfway along its depth. */
    private static final float TIP_Z = (float) (ARM_L * Math.cos(Math.toRadians(40.0)) * 0.5);
    private static final float PULSE_SCALE = 1.3f;
    private static final int PULSE_TICKS = 6;

    private static final BlockData PAINT = Material.YELLOW_CONCRETE.createBlockData();

    /** Compass mode: NPCs the painted trail may lead to past the pier (Egon/Forager have the authored route). */
    private static final Set<String> COMPASS_NPCS = Set.of(
            "quartermaster", "foreman", "craftsman", "booster_tutor", "ledger",
            "farmer", "lark", "vex", "rite_keeper", "surveyor"
    );
    /** Beacon over the target NPC reads from this far. */
    private static final double BEACON_RANGE = 64.0;
    /** Compass chevrons only for targets within this range (same world). */
    private static final double COMPASS_RANGE = 420.0;
    /** Drift further than this off the compass line and it re-anchors where you stand. */
    private static final double COMPASS_REANCHOR = 6.0;
    /** Walkable-ground limits between neighbouring compass chevrons. */
    private static final double STEP_UP = 1.3;
    private static final double STEP_DOWN = 2.6;

    /** Harbour path corners (same world as Egon / Forager). */
    private static final double[][] CORNERS = {
            {300.5, 63.15, -379.5},
            {328.5, 63.15, -379.5},
            {337.5, 66.15, -368.5}
    };

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Particle.DustOptions dust =
            new Particle.DustOptions(Color.fromRGB(255, 214, 90), 1.05f);
    private final Map<UUID, Crumbs> crumbs = new HashMap<>();
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private long anchorSerial;

    private BukkitTask task;
    private int phase;
    private int pulse;

    public TutorialQuestTrail(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 30L, 8L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Crumbs set : crumbs.values()) {
            set.removeAll();
        }
        crumbs.clear();
        anchors.clear();
    }

    private void tick() {
        phase = (phase + 1) % 6;
        pulse++;
        Set<UUID> painted = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<Location> nodes = pathFor(player);
            if (nodes == null || nodes.size() < 2) {
                if (compass(player)) {
                    painted.add(player.getUniqueId());
                }
                continue;
            }
            anchors.remove(player.getUniqueId());
            Path path = new Path(nodes);
            Location feet = player.getLocation();
            Path.Hit here = path.project(feet);
            Location end = nodes.get(nodes.size() - 1);
            boolean arrived = feet.distanceSquared(end) <= ARRIVED * ARRIVED;

            double dustFrom = here.s();
            if (!arrived && here.distance() <= CHEVRON_OFF_PATH) {
                String key = trackedId(player);
                double lastChevron = paintChevrons(player, path, here.s(), key, false);
                if (lastChevron > 0.0) {
                    painted.add(player.getUniqueId());
                    dustFrom = lastChevron + CHEVRON_GAP * 0.5;
                }
            }
            drawDust(player, path, dustFrom, here.s() + MAX_AHEAD);
            if (phase % 3 == 0 && !arrived) {
                player.spawnParticle(Particle.END_ROD, end.clone().add(0, 0.35, 0), 1, 0.1, 0.15, 0.1, 0.0);
            }
        }
        anchors.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        // Off route, quest moved on, or logged out → chevrons go.
        Iterator<Map.Entry<UUID, Crumbs>> it = crumbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Crumbs> entry = it.next();
            if (!painted.contains(entry.getKey())) {
                entry.getValue().removeAll();
                it.remove();
            }
        }
    }

    private String trackedId(Player player) {
        Quest tracked = questManager == null ? null : questManager.getTrackedQuest(player);
        return tracked == null ? "" : tracked.getId();
    }

    private List<Location> pathFor(Player player) {
        if (questManager == null) {
            return null;
        }
        Quest tracked = questManager.getTrackedQuest(player);
        if (tracked == null) {
            return null;
        }
        String id = tracked.getId();
        World world = player.getWorld();
        if (world == null) {
            return null;
        }
        Location egon = npcLocation("egon");
        Location forager = npcLocation("lumberjack");
        if (egon == null || forager == null) {
            return null;
        }
        if (!world.equals(egon.getWorld()) || !world.equals(forager.getWorld())) {
            return null;
        }

        List<Location> path = new ArrayList<>(4);
        if ("welcome_aboard".equalsIgnoreCase(id)) {
            // Egon → corners → Forager
            path.add(ground(egon));
            for (double[] c : CORNERS) {
                path.add(new Location(world, c[0], c[1], c[2]));
            }
            path.add(ground(forager));
            return path;
        }
        if ("gather_wood".equalsIgnoreCase(id)) {
            // Forager → corners (reverse) → Egon
            path.add(ground(forager));
            for (int i = CORNERS.length - 1; i >= 0; i--) {
                double[] c = CORNERS[i];
                path.add(new Location(world, c[0], c[1], c[2]));
            }
            path.add(ground(egon));
            return path;
        }
        return null;
    }

    /* =========================================================
     * DUST (far guidance)
     * ========================================================= */

    private void drawDust(Player player, Path path, double from, double to) {
        double limit = Math.min(to, path.length());
        // Snap to the spacing grid so dots stay put and the phase makes them march.
        long k = (long) Math.ceil(Math.max(0.0, from) / SPACING);
        for (double s = k * SPACING; s <= limit; s += SPACING, k++) {
            if ((k + phase) % 2 == 0) {
                player.spawnParticle(Particle.DUST, path.pointAt(s), 1, 0.03, 0.02, 0.03, 0.0, dust);
            }
        }
    }

    /* =========================================================
     * CHEVRONS (near guidance)
     * ========================================================= */

    /**
     * Keep this player's chevrons on the arc-length grid just ahead of them.
     *
     * @return arc length of the furthest chevron placed, or 0 if none
     */
    private double paintChevrons(Player player, Path path, double s0, String key, boolean strictGround) {
        long firstSlot = (long) Math.ceil((s0 + CHEVRON_LEAD) / CHEVRON_GAP);
        double maxS = path.length() - CHEVRON_END_MARGIN;
        Crumbs set = crumbs.computeIfAbsent(player.getUniqueId(), id -> new Crumbs());

        if (!set.placedFor(key, firstSlot)) {
            set.key = key;
            set.firstSlot = firstSlot;
            World world = player.getWorld();
            double previousTop = player.getLocation().getY();
            boolean blocked = false;
            for (int i = 0; i < CHEVRONS; i++) {
                double s = (firstSlot + i) * CHEVRON_GAP;
                if (s > maxS || blocked) {
                    set.clear(i);
                    continue;
                }
                Location point = path.pointAt(s);
                if (strictGround) {
                    // Compass line ignores terrain: follow the ground you'd actually walk on.
                    point.setY(previousTop + 0.5);
                }
                double groundTop = groundTop(world, point);
                if (strictGround) {
                    if (Double.isNaN(groundTop)
                            || groundTop - previousTop > STEP_UP
                            || previousTop - groundTop > STEP_DOWN
                            || !headroom(world, point, groundTop)) {
                        // A wall, a drop, a roof: stop here rather than paint over it.
                        blocked = true;
                        set.clear(i);
                        continue;
                    }
                    previousTop = groundTop;
                }
                point.setY(Double.isNaN(groundTop) ? point.getY() - 0.12 : groundTop + 0.02);
                point.setYaw(0f);
                point.setPitch(0f);
                set.place(plugin, player, i, point, path.headingAt(s));
            }
        }

        // One chevron swells at a time, rolling away from you (with a short rest between waves).
        int active = pulse % (CHEVRONS + 2);
        int previous = (pulse - 1) % (CHEVRONS + 2);
        set.pulse(previous, 1.0f);
        set.pulse(active, PULSE_SCALE);

        double last = 0.0;
        for (int i = CHEVRONS - 1; i >= 0; i--) {
            if (set.has(i)) {
                last = (firstSlot + i) * CHEVRON_GAP;
                break;
            }
        }
        return last;
    }

    /**
     * Top of the walkable surface under a path point (planks, slabs, carpet),
     * or NaN if nothing sensible is nearby. Climbs out if the straight line
     * between corners dipped into a slope.
     */
    private static double groundTop(World world, Location at) {
        if (world == null) {
            return Double.NaN;
        }
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int y = at.getBlockY();
        int climb = 0;
        while (climb < 3 && !world.getBlockAt(bx, y, bz).isPassable()) {
            y++;
            climb++;
        }
        for (int d = 0; d < 6; d++) {
            Block block = world.getBlockAt(bx, y - d, bz);
            if (block.isLiquid()) {
                return block.getY() + 0.9;
            }
            if (!block.isPassable()) {
                double top = block.getBoundingBox().getMaxY();
                return top > block.getY() ? top : block.getY() + 1.0;
            }
        }
        return Double.NaN;
    }

    /** Two passable blocks above a painted spot — nobody walks through a ceiling. */
    private static boolean headroom(World world, Location at, double groundTop) {
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int y = (int) Math.ceil(groundTop - 0.01);
        return world.getBlockAt(bx, y, bz).isPassable() && world.getBlockAt(bx, y + 1, bz).isPassable();
    }

    /* =========================================================
     * COMPASS (past the pier)
     * ========================================================= */

    /**
     * Chevrons + beacon toward the NPC the yellow arrow points at.
     *
     * @return true if chevrons are standing for this player
     */
    private boolean compass(Player player) {
        String npcId = compassTarget(player);
        if (npcId == null) {
            anchors.remove(player.getUniqueId());
            return false;
        }
        World world = player.getWorld();
        Location target = NpcPresence.locate(npcId);
        if (world == null || target == null || target.getWorld() == null || !world.equals(target.getWorld())
                || isFarmIsland(world)) {
            anchors.remove(player.getUniqueId());
            return false;
        }
        Location feet = player.getLocation();
        double dx = target.getX() - feet.getX();
        double dz = target.getZ() - feet.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat <= ARRIVED + 1.0 || flat > COMPASS_RANGE) {
            anchors.remove(player.getUniqueId());
            return false;
        }
        beacon(player, npcId, target, flat);

        Location end = ground(target);
        Anchor anchor = anchors.get(player.getUniqueId());
        if (anchor == null || !anchor.npcId.equals(npcId) || anchor.from.getWorld() != world) {
            anchor = newAnchor(npcId, feet);
            anchors.put(player.getUniqueId(), anchor);
        }
        Path path = new Path(List.of(anchor.from, end));
        Path.Hit here = path.project(feet);
        if (here.distance() > COMPASS_REANCHOR) {
            anchor = newAnchor(npcId, feet);
            anchors.put(player.getUniqueId(), anchor);
            path = new Path(List.of(anchor.from, end));
            here = path.project(feet);
        }
        return paintChevrons(player, path, here.s(), "npc:" + npcId + "#" + anchor.serial, true) > 0.0;
    }

    private Anchor newAnchor(String npcId, Location feet) {
        Location from = feet.clone();
        from.setYaw(0f);
        from.setPitch(0f);
        return new Anchor(npcId, from, ++anchorSerial);
    }

    /**
     * Who the paint should lead to: the turn-in NPC while a quest is READY, else the soft
     * hint (never while objectives are still being worked — the quest bar owns that).
     */
    private String compassTarget(Player player) {
        if (questManager == null) {
            return null;
        }
        Quest tracked = questManager.getTrackedQuest(player);
        Quest active = tracked != null ? tracked : questManager.findActiveOrReadyQuest(player);
        if (active != null) {
            if (questManager.getQuestState(player, active) != QuestState.READY) {
                return null;
            }
            String turnIn = null;
            if (active.hasTurnInNpc()) {
                turnIn = active.getTurnInNpcId();
            } else {
                QuestNPC npc = QuestNPCRegistry.findForQuest(active);
                turnIn = npc == null ? null : npc.getId();
            }
            return guided(turnIn);
        }
        return guided(QuestHint.targetNpcId(player));
    }

    private static String guided(String npcId) {
        if (npcId == null) {
            return null;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        return COMPASS_NPCS.contains(id) ? id : null;
    }

    /** Thin rising column in the NPC's name colour, player-only, once they're within sight. */
    private void beacon(Player player, String npcId, Location target, double flat) {
        if (flat > BEACON_RANGE || phase % 2 != 0) {
            return;
        }
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        NamedTextColor tone = profile != null ? profile.nameColor() : NamedTextColor.YELLOW;
        Particle.DustOptions mote = new Particle.DustOptions(Color.fromRGB(tone.red(), tone.green(), tone.blue()), 0.85f);
        double rise = (pulse % 4) * 0.14;
        Location head = target.clone().add(0.0, 2.45 + rise, 0.0);
        for (int i = 0; i < 7; i++) {
            player.spawnParticle(Particle.DUST, head.clone().add(0.0, i * 0.55, 0.0), 1, 0.015, 0.04, 0.015, 0.0, mote);
        }
        if (phase == 0) {
            player.spawnParticle(Particle.END_ROD, head.clone().add(0.0, 4.1, 0.0), 1, 0.05, 0.1, 0.05, 0.0);
        }
    }

    private static boolean isFarmIsland(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_farm_island") || name.startsWith("aether_farm_");
    }

    private record Anchor(String npcId, Location from, long serial) {
    }

    /**
     * One arm of a flat "^" chevron pointing along {@code heading}.
     * Local frame: +Z forward, tip at +TIP_Z, arm swept back by ±ARM_SPREAD.
     * Matrix only ({@code setTransformationMatrix}) — no TRS decompose.
     */
    private static Matrix4f armMatrix(float heading, int side, float scale) {
        float sweep = (float) Math.PI + side * ARM_SPREAD;
        return new Matrix4f()
                .rotateY(heading)
                .scale(scale, 1.0f, scale)
                .translate(0.0f, 0.0f, TIP_Z)
                .rotateY(sweep)
                .translate(-ARM_W * 0.5f, 0.0f, 0.0f)
                .scale(ARM_W, ARM_H, ARM_L);
    }

    /** Per-player chevron pool: {@code CHEVRONS} pairs of arm displays. */
    private static final class Crumbs {
        private final BlockDisplay[][] arms = new BlockDisplay[CHEVRONS][];
        private final float[] headings = new float[CHEVRONS];
        private String key;
        private long firstSlot = Long.MIN_VALUE;

        /** Same window, same quest, and nothing got culled (chunk unload drops non-persistent displays). */
        boolean placedFor(String questKey, long slot) {
            if (slot != firstSlot || questKey == null || !questKey.equals(key)) {
                return false;
            }
            for (BlockDisplay[] pair : arms) {
                if (pair != null && (pair[0] == null || !pair[0].isValid() || pair[1] == null || !pair[1].isValid())) {
                    return false;
                }
            }
            return true;
        }

        boolean has(int i) {
            BlockDisplay[] pair = arms[i];
            return pair != null && pair[0] != null && pair[0].isValid();
        }

        void place(AetherionQuests plugin, Player viewer, int i, Location at, float heading) {
            headings[i] = heading;
            BlockDisplay[] pair = arms[i];
            if (pair == null || pair[0] == null || !pair[0].isValid() || pair[1] == null || !pair[1].isValid()
                    || pair[0].getWorld() != at.getWorld()) {
                clear(i);
                pair = new BlockDisplay[] {
                        spawnArm(plugin, viewer, at, heading, -1),
                        spawnArm(plugin, viewer, at, heading, 1)
                };
                arms[i] = pair;
                return;
            }
            for (int side = 0; side < 2; side++) {
                BlockDisplay arm = pair[side];
                arm.teleport(at);
                arm.setInterpolationDuration(0);
                arm.setTransformationMatrix(armMatrix(heading, side == 0 ? -1 : 1, 1.0f));
            }
        }

        void pulse(int i, float scale) {
            if (i < 0 || i >= CHEVRONS || !has(i)) {
                return;
            }
            BlockDisplay[] pair = arms[i];
            for (int side = 0; side < 2; side++) {
                BlockDisplay arm = pair[side];
                if (arm == null || !arm.isValid()) {
                    continue;
                }
                arm.setInterpolationDelay(0);
                arm.setInterpolationDuration(PULSE_TICKS);
                arm.setTransformationMatrix(armMatrix(headings[i], side == 0 ? -1 : 1, scale));
            }
        }

        void clear(int i) {
            BlockDisplay[] pair = arms[i];
            if (pair != null) {
                for (BlockDisplay arm : pair) {
                    if (arm != null && arm.isValid()) {
                        arm.remove();
                    }
                }
            }
            arms[i] = null;
        }

        void removeAll() {
            for (int i = 0; i < CHEVRONS; i++) {
                clear(i);
            }
        }

        private static BlockDisplay spawnArm(AetherionQuests plugin, Player viewer, Location at, float heading, int side) {
            World world = at.getWorld();
            if (world == null) {
                return null;
            }
            BlockDisplay arm = world.spawn(at, BlockDisplay.class, display -> {
                display.setPersistent(false);
                display.setVisibleByDefault(false);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBlock(PAINT);
                display.setBrightness(new Display.Brightness(12, 15));
                display.setShadowRadius(0f);
                display.setTransformationMatrix(armMatrix(heading, side, 1.0f));
                display.addScoreboardTag(CHEVRON_TAG);
            });
            viewer.showEntity(plugin, arm);
            return arm;
        }
    }

    /* =========================================================
     * PATH
     * ========================================================= */

    /** Polyline with arc length — projection, points and headings along it. */
    private static final class Path {

        record Hit(double s, double distance) {
        }

        private final List<Location> nodes;
        private final double[] cumulative;
        private final double length;

        Path(List<Location> nodes) {
            this.nodes = nodes;
            this.cumulative = new double[nodes.size()];
            double total = 0.0;
            for (int i = 1; i < nodes.size(); i++) {
                total += nodes.get(i - 1).distance(nodes.get(i));
                cumulative[i] = total;
            }
            this.length = total;
        }

        double length() {
            return length;
        }

        Hit project(Location p) {
            double bestDist = Double.MAX_VALUE;
            double bestS = 0.0;
            Vector pv = p.toVector();
            for (int i = 0; i < nodes.size() - 1; i++) {
                Vector a = nodes.get(i).toVector();
                Vector ab = nodes.get(i + 1).toVector().subtract(a);
                double len2 = ab.lengthSquared();
                double t = len2 < 1.0e-6 ? 0.0 : pv.clone().subtract(a).dot(ab) / len2;
                t = Math.max(0.0, Math.min(1.0, t));
                Vector q = a.clone().add(ab.clone().multiply(t));
                double d = q.distanceSquared(pv);
                if (d < bestDist) {
                    bestDist = d;
                    bestS = cumulative[i] + t * Math.sqrt(len2);
                }
            }
            return new Hit(bestS, Math.sqrt(bestDist));
        }

        Location pointAt(double s) {
            int i = segmentAt(s);
            Location a = nodes.get(i);
            Location b = nodes.get(i + 1);
            double seg = cumulative[i + 1] - cumulative[i];
            double t = seg < 1.0e-6 ? 0.0 : (s - cumulative[i]) / seg;
            t = Math.max(0.0, Math.min(1.0, t));
            return new Location(
                    a.getWorld(),
                    a.getX() + (b.getX() - a.getX()) * t,
                    a.getY() + (b.getY() - a.getY()) * t,
                    a.getZ() + (b.getZ() - a.getZ()) * t
            );
        }

        /** Radians for {@code Matrix4f.rotateY}: local +Z turned onto the walking direction. */
        float headingAt(double s) {
            int i = segmentAt(s);
            for (int j = i; j < nodes.size() - 1; j++) {
                double dx = nodes.get(j + 1).getX() - nodes.get(j).getX();
                double dz = nodes.get(j + 1).getZ() - nodes.get(j).getZ();
                if (dx * dx + dz * dz > 1.0e-4) {
                    return (float) Math.atan2(dx, dz);
                }
            }
            return 0f;
        }

        private int segmentAt(double s) {
            for (int i = 0; i < nodes.size() - 2; i++) {
                if (s <= cumulative[i + 1]) {
                    return i;
                }
            }
            return nodes.size() - 2;
        }
    }

    private static Location ground(Location npc) {
        return new Location(npc.getWorld(), npc.getX(), npc.getY() + 0.15, npc.getZ());
    }

    private Location npcLocation(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null && npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        if (plugin == null || plugin.getNpcDataStorage() == null) {
            return null;
        }
        NPCDataStorage storage = plugin.getNpcDataStorage();
        return storage.getSavedLocation(npcId);
    }
}
