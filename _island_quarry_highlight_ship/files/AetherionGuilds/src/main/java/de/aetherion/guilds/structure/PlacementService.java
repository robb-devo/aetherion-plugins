package de.aetherion.guilds.structure;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.Belt;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.Template;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Build modes. No tool item: while a mode is active, clicks are captured.
 * <ul>
 *   <li><b>Blueprint</b> (structures, project sites): a particle ghost follows your crosshair, green on a valid
 *   pad, red with the blocking cells marked. Right-click builds, left-click rotates, sneak-click cancels.</li>
 *   <li><b>Rail Layer</b> (belts): right-click a start, right-click an end (straight or L), it keeps chaining from
 *   the end. Left-click a belt to take it up. Existing belts show their flow arrows, chutes glow orange.</li>
 * </ul>
 */
public final class PlacementService {

    public enum Kind {
        STRUCTURE,
        PROJECT_SITE,
        BELTS
    }

    public interface SiteConfirm {
        void confirm(Player player, int ax, int ay, int az, int rot);
    }

    private static final class Session {
        final Kind kind;
        final IslandHost host;
        final StructureType type;
        final Template template;
        final String label;
        final SiteConfirm onConfirm;
        int rot;
        boolean autoRot = true;
        long lastAction = System.currentTimeMillis();
        int[] beltStart;
        int clock;

        Session(Kind kind, IslandHost host, StructureType type, Template template, String label, SiteConfirm onConfirm) {
            this.kind = kind;
            this.host = host;
            this.type = type;
            this.template = template;
            this.label = label;
            this.onConfirm = onConfirm;
        }
    }

    private static final Color OK = Color.fromRGB(90, 225, 120);
    private static final Color BAD = Color.fromRGB(235, 70, 70);
    private static final Color FRONT = Color.fromRGB(255, 220, 80);
    private static final Color PORT = Color.fromRGB(255, 150, 40);
    private static final Color FLOW = Color.fromRGB(235, 235, 235);

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlacementService(HostService hosts, StructureService structures, LogisticsService logistics) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public boolean has(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public Kind kind(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? null : session.kind;
    }

    private boolean onIsland(Player player, IslandHost host) {
        IslandHost here = hosts.at(player.getLocation());
        if (here == null || !here.equals(host)) {
            player.sendMessage("§cStand on the island you want to build on.");
            return false;
        }
        return true;
    }

    public void startStructure(Player player, IslandHost host, StructureType type) {
        if (!onIsland(player, host)) {
            return;
        }
        String blocked = structures.blockedReason(player, host, type);
        if (blocked != null) {
            player.sendMessage("§c" + blocked);
            return;
        }
        Template template = structures.templates().get(type.templateId());
        if (template == null) {
            player.sendMessage("§cThe " + type.display() + " blueprint is missing on the server.");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(Kind.STRUCTURE, host, type, template, type.display(), null));
        player.closeInventory();
        player.sendMessage("§6✎ Blueprint: §f" + type.display());
        player.sendMessage("§7Look at the ground. §fRight-click §7builds, §fleft-click §7rotates, §fsneak + click §7cancels.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
    }

    public void startProjectSite(Player player, IslandHost host, Template template, String label, SiteConfirm confirm) {
        if (!onIsland(player, host)) {
            return;
        }
        if (template == null) {
            player.sendMessage("§cThat project's blueprint is missing on the server.");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(Kind.PROJECT_SITE, host, StructureType.PROJECT, template, label,
                confirm));
        player.closeInventory();
        player.sendMessage("§6✎ Project site: §f" + label);
        player.sendMessage("§7Pick flat ground for the finished build. §fRight-click §7claims it, §fleft-click §7rotates.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
    }

    public void startBelts(Player player, IslandHost host) {
        if (!onIsland(player, host)) {
            return;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "lay belts"));
            return;
        }
        if (!structures.hasWorkshop(host)) {
            player.sendMessage("§cBuild a Workshop first; belts come from its blueprints.");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(Kind.BELTS, host, null, null, "Rail Layer", null));
        player.closeInventory();
        player.sendMessage("§6⛏ Rail Layer on. §7Right-click a start, then an end (straight or L-shaped).");
        player.sendMessage("§7Start on the §6orange§7 chute cell of a quarry or machine, end into a hut, depot, mill or forge.");
        player.sendMessage("§7Left-click a belt to take it up. §fSneak + click §7to finish.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
    }

    public void cancel(Player player, String message) {
        if (sessions.remove(player.getUniqueId()) != null && message != null) {
            player.sendMessage(message);
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(""));
        }
    }

    public void cancelAll() {
        sessions.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------------------------------------

    /** Returns true when the click belonged to a build mode (caller cancels the event). */
    public boolean onInteract(Player player, boolean rightClick, Block clicked, boolean sneaking) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.lastAction = System.currentTimeMillis();
        if (sneaking) {
            cancel(player, session.kind == Kind.BELTS ? "§7Rail Layer off." : "§7Blueprint put away.");
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 0.8f, 1f);
            return true;
        }
        if (session.kind == Kind.BELTS) {
            handleBelts(player, session, rightClick, clicked);
            return true;
        }
        if (!rightClick) {
            session.autoRot = false;
            session.rot = (session.rot + 1) & 3;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            return true;
        }
        Block ground = groundOf(player);
        if (ground == null) {
            player.sendMessage("§cLook at the ground where it should stand.");
            return true;
        }
        int rot = session.autoRot ? StateRotator.frontTowardViewer(player.getLocation().getYaw()) : session.rot;
        IslandHost here = hosts.at(ground.getLocation());
        if (here == null || !here.equals(session.host)) {
            player.sendMessage("§cThat's not on this island.");
            return true;
        }
        if (session.kind == Kind.STRUCTURE) {
            PlacedStructure placed = structures.place(player, session.host, session.type, ground.getX(), ground.getY(),
                    ground.getZ(), rot, logistics::isBelt);
            if (placed != null) {
                sessions.remove(player.getUniqueId());
            }
            return true;
        }
        StructureService.Validation validation = structures.validate(session.host, session.template, ground.getWorld(),
                ground.getX(), ground.getY(), ground.getZ(), rot, logistics::isBelt);
        if (!validation.ok()) {
            player.sendMessage("§c" + validation.reason() + ".");
            return true;
        }
        sessions.remove(player.getUniqueId());
        session.onConfirm.confirm(player, ground.getX(), ground.getY(), ground.getZ(), rot);
        return true;
    }

    private void handleBelts(Player player, Session session, boolean rightClick, Block clicked) {
        World world = player.getWorld();
        if (clicked == null) {
            if (!rightClick) {
                session.beltStart = null;
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§7Start cleared."));
            }
            return;
        }
        if (!rightClick) {
            Belt belt = logistics.belt(world, clicked.getX(), clicked.getY(), clicked.getZ());
            if (belt != null) {
                if (logistics.remove(player, session.host, belt.x(), belt.y(), belt.z())) {
                    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§7Belt taken up §8(refunded)"));
                }
            } else {
                session.beltStart = null;
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§7Start cleared."));
            }
            return;
        }
        int[] cell = cellOf(world, clicked);
        IslandHost here = hosts.at(clicked.getLocation());
        if (here == null || !here.equals(session.host)) {
            player.sendMessage("§cThat's not on this island.");
            return;
        }
        if (session.beltStart == null) {
            boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
            String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
            if (problem != null) {
                player.sendMessage("§cCan't start there: " + problem + ".");
                return;
            }
            session.beltStart = cell;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.4f);
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize("§aStart set §7· right-click where the belt should end"));
            return;
        }
        BlockFace fallback = StateRotator.facingOfYaw(player.getLocation().getYaw());
        if (logistics.lay(player, session.host, session.beltStart, cell, fallback)) {
            session.beltStart = cell;
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize("§aLaid §7· keep clicking to extend, §fsneak §7to finish"));
        }
    }

    private int[] cellOf(World world, Block clicked) {
        if (logistics.isBelt(world, clicked.getX(), clicked.getY(), clicked.getZ())) {
            return new int[]{clicked.getX(), clicked.getY(), clicked.getZ()};
        }
        if (PasteService.replaceable(clicked)) {
            return new int[]{clicked.getX(), clicked.getY(), clicked.getZ()};
        }
        return new int[]{clicked.getX(), clicked.getY() + 1, clicked.getZ()};
    }

    private static Block groundOf(Player player) {
        Block target = player.getTargetBlockExact(24);
        if (target == null) {
            return null;
        }
        if (PasteService.replaceable(target)) {
            target = target.getRelative(BlockFace.DOWN);
        }
        return target.getType().isAir() ? null : target;
    }

    // ------------------------------------------------------------------------------------------------
    // ghost rendering (every 4 ticks)
    // ------------------------------------------------------------------------------------------------

    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline()) {
                sessions.remove(entry.getKey());
                continue;
            }
            if (now - session.lastAction > 180_000L) {
                cancel(player, "§7Build mode timed out.");
                continue;
            }
            IslandHost here = hosts.at(player.getLocation());
            if (here == null || !here.equals(session.host)) {
                cancel(player, "§7You left the island; build mode off.");
                continue;
            }
            session.clock++;
            if (session.kind == Kind.BELTS) {
                renderBelts(player, session);
            } else {
                renderGhost(player, session);
            }
        }
    }

    private void renderGhost(Player player, Session session) {
        Block ground = groundOf(player);
        if (ground == null) {
            actionBar(player, "§7Look at the ground · §fsneak + click §7to cancel");
            return;
        }
        int rot = session.autoRot ? StateRotator.frontTowardViewer(player.getLocation().getYaw()) : session.rot;
        session.rot = rot;
        World world = ground.getWorld();
        int ax = ground.getX();
        int ay = ground.getY();
        int az = ground.getZ();
        StructureService.Validation validation = structures.validate(session.host, session.template, world, ax, ay, az,
                rot, logistics::isBelt);
        IslandHost here = hosts.at(ground.getLocation());
        boolean ok = validation.ok() && here != null && here.equals(session.host);
        Particle.DustOptions edge = new Particle.DustOptions(ok ? OK : BAD, 1.1f);
        int[] fp = session.template.rotatedFootprint(rot);
        double x1 = ax + fp[0];
        double z1 = az + fp[1];
        double x2 = ax + fp[2] + 1.0;
        double z2 = az + fp[3] + 1.0;
        double y = ay + 1.05;
        for (double t = x1; t <= x2 + 0.01; t += 0.5) {
            player.spawnParticle(Particle.DUST, t, y, z1, 1, 0, 0, 0, 0, edge);
            player.spawnParticle(Particle.DUST, t, y, z2, 1, 0, 0, 0, 0, edge);
        }
        for (double t = z1; t <= z2 + 0.01; t += 0.5) {
            player.spawnParticle(Particle.DUST, x1, y, t, 1, 0, 0, 0, 0, edge);
            player.spawnParticle(Particle.DUST, x2, y, t, 1, 0, 0, 0, 0, edge);
        }
        int height = Math.max(2, Math.min(12, session.template.maxY()));
        if (session.clock % 2 == 0) {
            for (int h = 1; h <= height; h++) {
                double yy = ay + 1 + h;
                player.spawnParticle(Particle.DUST, x1, yy, z1, 1, 0, 0, 0, 0, edge);
                player.spawnParticle(Particle.DUST, x2, yy, z1, 1, 0, 0, 0, 0, edge);
                player.spawnParticle(Particle.DUST, x1, yy, z2, 1, 0, 0, 0, 0, edge);
                player.spawnParticle(Particle.DUST, x2, yy, z2, 1, 0, 0, 0, 0, edge);
            }
        }
        // front arrow
        Particle.DustOptions front = new Particle.DustOptions(FRONT, 1.3f);
        int frontReach = session.template.maxZ();
        for (int i = 0; i <= frontReach; i++) {
            int[] r = StateRotator.rotateXZ(0, i, rot);
            player.spawnParticle(Particle.DUST, ax + r[0] + 0.5, ay + 1.3, az + r[1] + 0.5, 1, 0, 0, 0, 0, front);
        }
        if (session.type != null && session.type.hasOutput()) {
            int[] r = StateRotator.rotateXZ(StructureType.OUTPUT_PORT[0], StructureType.OUTPUT_PORT[2], rot);
            Particle.DustOptions port = new Particle.DustOptions(PORT, 1.5f);
            for (double h = 0.2; h <= 1.4; h += 0.4) {
                player.spawnParticle(Particle.DUST, ax + r[0] + 0.5, ay + 1 + h, az + r[1] + 0.5, 1, 0, 0, 0, 0, port);
            }
        }
        int shown = 0;
        Particle.DustOptions bad = new Particle.DustOptions(BAD, 1.6f);
        for (int[] cell : validation.blocked()) {
            if (shown++ > 16) {
                break;
            }
            player.spawnParticle(Particle.DUST, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5, 2, 0.2, 0.2, 0.2, 0, bad);
        }
        String reason = here == null || !here.equals(session.host) ? "Not on this island" : validation.reason();
        actionBar(player, ok
                ? "§a✔ " + session.label + " §7· §fright-click §7build · §fleft-click §7rotate · §fsneak §7cancel"
                : "§c✖ " + reason + " §7· §fsneak + click §7cancel");
    }

    private void renderBelts(Player player, Session session) {
        World world = player.getWorld();
        // existing belts near the player: flow arrows (every other render)
        if (session.clock % 2 == 0) {
            Particle.DustOptions flow = new Particle.DustOptions(FLOW, 0.8f);
            int shown = 0;
            for (Belt belt : logistics.belts(session.host)) {
                if (Math.abs(belt.x() - player.getLocation().getBlockX()) > 20
                        || Math.abs(belt.z() - player.getLocation().getBlockZ()) > 20) {
                    continue;
                }
                if (shown++ > 96) {
                    break;
                }
                double cx = belt.x() + 0.5;
                double cz = belt.z() + 0.5;
                player.spawnParticle(Particle.DUST, cx + belt.dir().getModX() * 0.35, belt.y() + 0.25,
                        cz + belt.dir().getModZ() * 0.35, 1, 0, 0, 0, 0, flow);
            }
            Particle.DustOptions port = new Particle.DustOptions(PORT, 1.4f);
            for (PlacedStructure structure : structures.of(session.host)) {
                int[] p = structures.outputPort(structure);
                if (p != null && Math.abs(p[0] - player.getLocation().getBlockX()) <= 32) {
                    player.spawnParticle(Particle.DUST, p[0] + 0.5, p[1] + 0.3, p[2] + 0.5, 2, 0.15, 0.15, 0.15, 0, port);
                }
            }
        }
        Block target = player.getTargetBlockExact(24);
        if (session.beltStart == null) {
            if (target != null) {
                int[] cell = cellOf(world, target);
                boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
                String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
                player.spawnParticle(Particle.DUST, cell[0] + 0.5, cell[1] + 0.2, cell[2] + 0.5, 3, 0.25, 0.05, 0.25, 0,
                        new Particle.DustOptions(problem == null ? OK : BAD, 1.2f));
                actionBar(player, problem == null
                        ? "§6⛏ §7Right-click to set the §fstart §7· belts " + logistics.count(session.host) + "/" + logistics.cap(session.host)
                        : "§c✖ " + problem);
            } else {
                actionBar(player, "§6⛏ §7Look at the ground to start a belt");
            }
            return;
        }
        if (target == null) {
            actionBar(player, "§6⛏ §7Look where the belt should end");
            return;
        }
        int[] end = cellOf(world, target);
        List<Object[]> plan = LogisticsService.plan(session.beltStart, end,
                StateRotator.facingOfYaw(player.getLocation().getYaw()));
        if (plan == null) {
            actionBar(player, "§c✖ Belts run flat: pick an end at the same height");
            return;
        }
        String first = null;
        int shown = 0;
        for (Object[] step : plan) {
            int[] p = (int[]) step[0];
            BlockFace dir = (BlockFace) step[1];
            boolean continuing = logistics.belt(world, p[0], p[1], p[2]) != null;
            String problem = logistics.cellProblem(session.host, world, p[0], p[1], p[2], continuing);
            if (problem != null && first == null) {
                first = problem;
            }
            if (shown++ > 80) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(problem == null ? OK : BAD, 1.0f);
            player.spawnParticle(Particle.DUST, p[0] + 0.5, p[1] + 0.15, p[2] + 0.5, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, p[0] + 0.5 + dir.getModX() * 0.35, p[1] + 0.15,
                    p[2] + 0.5 + dir.getModZ() * 0.35, 1, 0, 0, 0, 0, dust);
        }
        long price = logistics.tileCost() * plan.size();
        actionBar(player, first == null
                ? "§a✔ " + plan.size() + " tiles §8(§6~" + price + "§8) §7· right-click to lay · §fsneak §7finish"
                : "§c✖ " + first);
    }

    private static void actionBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }
}
