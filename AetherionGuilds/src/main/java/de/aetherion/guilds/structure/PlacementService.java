package de.aetherion.guilds.structure;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.Belt;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.Template;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Build modes, each tied to a tool item in the hotbar. Clicks are only captured (and block breaking/placing
 * only paused) while that tool is in the main hand: scroll to another slot and you build normally, scroll back
 * and the mode is still there. Dropping the tool (Q) or sneak-clicking with it ends the mode.
 * <ul>
 *   <li><b>Blueprint</b> (structures, project sites): a hologram of the build follows your crosshair, green
 *   outline on a valid pad, red with the blocking cells marked. Right-click builds, left-click rotates.</li>
 *   <li><b>Belt Layer</b>: right-click a start, right-click an end (straight or L); it keeps chaining from the
 *   end. Left-click a belt to take it up. Chutes nearby are marked orange.</li>
 * </ul>
 * The hologram is made of display entities only the builder can see; no particles.
 */
public final class PlacementService {

    public enum Kind {
        STRUCTURE,
        PROJECT_SITE,
        BELTS,
        /** Splitter / Overflow Gate / Sorter: one cell, straight onto a belt or a free spot. */
        PIECE
    }

    public interface SiteConfirm {
        void confirm(Player player, int ax, int ay, int az, int rot);
    }

    private static final long IDLE_MS = 300_000L;

    private static final class Session {
        final Kind kind;
        final IslandHost host;
        final StructureType type;
        final Template template;
        final String label;
        final SiteConfirm onConfirm;
        final GhostView ghost;
        int rot;
        boolean autoRot = true;
        long lastAction = System.currentTimeMillis();
        int[] beltStart;
        int clock;
        boolean holding;
        long quietUntil;

        Session(Kind kind, IslandHost host, StructureType type, Template template, String label, SiteConfirm onConfirm,
                GhostView ghost) {
            this.kind = kind;
            this.host = host;
            this.type = type;
            this.template = template;
            this.label = label;
            this.onConfirm = onConfirm;
            this.ghost = ghost;
        }
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final NamespacedKey toolKey;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlacementService(JavaPlugin plugin, HostService hosts, StructureService structures,
                            LogisticsService logistics) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.toolKey = new NamespacedKey(plugin, "build_tool");
    }

    public boolean has(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public Kind kind(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? null : session.kind;
    }

    // ------------------------------------------------------------------------------------------------
    // tool items
    // ------------------------------------------------------------------------------------------------

    public boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.STRING);
    }

    /** Is the player holding a build tool right now? Only then are clicks and block edits captured. */
    public boolean holdingTool(Player player) {
        return player != null && isTool(player.getInventory().getItemInMainHand());
    }

    private ItemStack toolFor(Session session) {
        boolean belts = session.kind == Kind.BELTS;
        boolean piece = session.kind == Kind.PIECE;
        ItemStack item = new ItemStack(belts ? Material.BLAZE_ROD : piece ? session.type.icon() : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(belts ? "§6⛓ Belt Layer" : "§6✎ Blueprint: §f" + session.label);
            meta.setLore(belts
                    ? List.of("§7Right-click a start, then an end.",
                    "§7Left-click a belt to take it up.",
                    "§8Q or sneak-click: finish")
                    : piece
                    ? List.of("§7Right-click a belt: cut it in",
                    "§7Right-click ground: set it down",
                    "§7Left-click: turn its front",
                    "§8Q or sneak-click: put away")
                    : List.of("§7Right-click: build here",
                    "§7Left-click: rotate",
                    "§8Q or sneak-click: put away"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, session.kind.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Puts the tool into the main hand (the held item moves to a free slot). False if the inventory is full. */
    private boolean giveTool(Player player, Session session) {
        stripTools(player);
        PlayerInventory inventory = player.getInventory();
        ItemStack tool = toolFor(session);
        int held = inventory.getHeldItemSlot();
        ItemStack current = inventory.getItem(held);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(held, tool);
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                inventory.setItem(slot, tool);
                inventory.setHeldItemSlot(slot);
                return true;
            }
        }
        int free = inventory.firstEmpty();
        if (free < 0) {
            player.sendMessage("§cFree one inventory slot for the " + (session.kind == Kind.BELTS ? "Belt Layer" : "Blueprint") + ".");
            return false;
        }
        inventory.setItem(free, current);
        inventory.setItem(held, tool);
        return true;
    }

    /** Removes every build tool from the player's inventory (and cursor). */
    public void stripTools(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (isTool(storage[i])) {
                storage[i] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setStorageContents(storage);
        }
        if (isTool(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (isTool(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // starting / ending
    // ------------------------------------------------------------------------------------------------

    private boolean onIsland(Player player, IslandHost host) {
        IslandHost here = hosts.at(player.getLocation());
        if (here == null || !here.equals(host)) {
            player.sendMessage("§cStand on the island you want to build on.");
            return false;
        }
        return true;
    }

    private boolean begin(Player player, Session session) {
        Session old = sessions.remove(player.getUniqueId());
        if (old != null) {
            old.ghost.clear();
        }
        if (!giveTool(player, session)) {
            return false;
        }
        sessions.put(player.getUniqueId(), session);
        player.closeInventory();
        return true;
    }

    /** After the Hub stands, blueprints, belts and belt pieces are picked up at its lectern. */
    private boolean atHub(Player player, IslandHost host) {
        if (structures.atDesk(player, host)) {
            return true;
        }
        player.sendMessage("§6Your Hub runs the factory now: §7pick that up at the Workshop lectern §8(/island → Build"
                + " shows the way)§7.");
        return false;
    }

    public void startStructure(Player player, IslandHost host, StructureType type) {
        if (!onIsland(player, host) || !atHub(player, host)) {
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
        Session session = new Session(Kind.STRUCTURE, host, type, template, type.display(), null,
                new GhostView(plugin, player));
        if (!begin(player, session)) {
            return;
        }
        player.sendMessage("§6✎ Blueprint: §f" + type.display() + " §7(in your hand)");
        player.sendMessage("§7Look at the ground. §fRight-click §7builds, §fleft-click §7rotates, §fQ §7puts it away.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
    }

    /** Splitter / Overflow Gate / Sorter: the tool targets one cell; on a belt it cuts into the line. */
    public void startPiece(Player player, IslandHost host, StructureType type) {
        if (!onIsland(player, host) || !atHub(player, host)) {
            return;
        }
        String blocked = structures.blockedReason(player, host, type);
        if (blocked != null) {
            player.sendMessage("§c" + blocked);
            return;
        }
        Session session = new Session(Kind.PIECE, host, type, null, type.display(), null, new GhostView(plugin, player));
        if (!begin(player, session)) {
            return;
        }
        player.sendMessage("§6✎ " + type.display() + " §7(in your hand). §fRight-click a belt §7to cut it into the line,"
                + " or a free spot to set it down.");
        player.sendMessage(switch (type) {
            case SORTER -> "§7Then right-click the Sorter to pick its good: that good goes out the §ffront§7, the rest out the §fsides§7.";
            case OVERFLOW -> "§7The §ffront §7belt gets everything until it backs up; then the §fsides §7take the rest.";
            default -> "§7Every belt you lay §faway §7from it becomes an exit with an equal share.";
        });
        player.playSound(player.getLocation(), Sound.BLOCK_COPPER_GRATE_PLACE, 1f, 1.2f);
    }

    public void startProjectSite(Player player, IslandHost host, Template template, String label, SiteConfirm confirm) {
        if (!onIsland(player, host)) {
            return;
        }
        if (template == null) {
            player.sendMessage("§cThat project's blueprint is missing on the server.");
            return;
        }
        Session session = new Session(Kind.PROJECT_SITE, host, StructureType.PROJECT, template, label, confirm,
                new GhostView(plugin, player));
        if (!begin(player, session)) {
            return;
        }
        player.sendMessage("§6✎ Project site: §f" + label + " §7(in your hand)");
        player.sendMessage("§7Pick flat ground for the finished build. §fRight-click §7claims it, §fleft-click §7rotates.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
    }

    public void startBelts(Player player, IslandHost host) {
        if (!onIsland(player, host) || !atHub(player, host)) {
            return;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "lay belts"));
            return;
        }
        LogisticsService.BeltPass pass = logistics.beltPass();
        if (!structures.hasWorkshop(host) && (pass == null || !pass.noWorkshopNeeded(player, host))) {
            player.sendMessage("§cFound your Hub first (Build → Workshop): belts are laid from its lectern.");
            return;
        }
        Session session = new Session(Kind.BELTS, host, null, null, "Belt Layer", null, new GhostView(plugin, player));
        if (!begin(player, session)) {
            return;
        }
        player.sendMessage("§6⛓ Belt Layer §7(in your hand). Right-click a start, then an end (straight or L-shaped).");
        player.sendMessage("§7Start on the §6orange§7 chute mark of a quarry or machine, end into a hut, depot, mill or forge.");
        player.sendMessage("§7Left-click a belt to take it up. §fQ §7or §fsneak-click §7to finish. Scroll away to build normally.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
    }

    public void cancel(Player player, String message) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.ghost.clear();
        }
        stripTools(player);
        if (session != null && message != null) {
            player.sendMessage(message);
            actionBar(player, "");
        }
    }

    public void cancelAll() {
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            entry.getValue().ghost.clear();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                stripTools(player);
            }
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // input (only called while the tool is in the main hand)
    // ------------------------------------------------------------------------------------------------

    /** A click with a build tool. The caller always cancels the event. */
    public void onInteract(Player player, boolean rightClick, Block clicked, boolean sneaking) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            stripTools(player);
            player.sendMessage("§7That build mode had ended; tool put away.");
            return;
        }
        session.lastAction = System.currentTimeMillis();
        if (sneaking) {
            cancel(player, session.kind == Kind.BELTS ? "§7Belt Layer put away." : "§7Blueprint put away.");
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 0.8f, 1f);
            return;
        }
        if (session.kind == Kind.BELTS) {
            handleBelts(player, session, rightClick, clicked);
            return;
        }
        if (session.kind == Kind.PIECE) {
            handlePiece(player, session, rightClick, clicked);
            return;
        }
        if (!rightClick) {
            session.autoRot = false;
            session.rot = (session.rot + 1) & 3;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            return;
        }
        Block ground = groundOf(player);
        if (ground == null) {
            player.sendMessage("§cLook at the ground where it should stand.");
            return;
        }
        int rot = session.autoRot ? StateRotator.frontTowardViewer(player.getLocation().getYaw()) : session.rot;
        IslandHost here = hosts.at(ground.getLocation());
        if (here == null || !here.equals(session.host)) {
            player.sendMessage("§cThat's not on this island.");
            return;
        }
        if (session.kind == Kind.STRUCTURE) {
            PlacedStructure placed = structures.place(player, session.host, session.type, ground.getX(), ground.getY(),
                    ground.getZ(), rot, logistics::isBelt);
            if (placed != null) {
                end(player);
            }
            return;
        }
        StructureService.Validation validation = structures.validate(session.host, session.template, ground.getWorld(),
                ground.getX(), ground.getY(), ground.getZ(), rot, logistics::isBelt);
        if (!validation.ok()) {
            player.sendMessage("§c" + validation.reason() + ".");
            return;
        }
        end(player);
        session.onConfirm.confirm(player, ground.getX(), ground.getY(), ground.getZ(), rot);
    }

    /** Tool dropped (Q): the mode ends quietly. */
    public void onDropTool(Player player) {
        Session session = sessions.get(player.getUniqueId());
        cancel(player, session == null ? null
                : session.kind == Kind.BELTS ? "§7Belt Layer put away." : "§7Blueprint put away.");
    }

    private void end(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.ghost.clear();
        }
        stripTools(player);
    }

    private void handleBelts(Player player, Session session, boolean rightClick, Block clicked) {
        World world = player.getWorld();
        if (clicked == null) {
            // past arm's reach the client reports an air click; the preview reaches 24 blocks, so do the clicks
            clicked = player.getTargetBlockExact(24);
        }
        if (clicked == null) {
            if (!rightClick) {
                session.beltStart = null;
                actionBar(player, "§7Start cleared.");
            }
            return;
        }
        if (!rightClick) {
            Belt belt = logistics.belt(world, clicked.getX(), clicked.getY(), clicked.getZ());
            if (belt == null && logistics.isBeltSupport(world, clicked.getX(), clicked.getY(), clicked.getZ())) {
                belt = logistics.belt(world, clicked.getX(), clicked.getY() + 1, clicked.getZ());
            }
            if (belt != null) {
                if (logistics.remove(player, session.host, belt.x(), belt.y(), belt.z())) {
                    actionBar(player, "§7Belt taken up §8(refunded)");
                }
            } else {
                session.beltStart = null;
                actionBar(player, "§7Start cleared.");
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
            actionBar(player, "§aStart set §7· right-click where the belt should end");
            return;
        }
        BlockFace fallback = StateRotator.facingOfYaw(player.getLocation().getYaw());
        if (logistics.lay(player, session.host, session.beltStart, cell, fallback)) {
            session.beltStart = cell;
            if (!logistics.lastLayTalked()) {
                actionBar(player, "§aLaid §7· keep clicking to extend, §fQ §7to finish");
            }
            if (logistics.lastLayTalked()) {
                session.quietUntil = System.currentTimeMillis() + 2500L; // let the "Connected" line stay up
            }
        }
    }

    private void handlePiece(Player player, Session session, boolean rightClick, Block clicked) {
        if (!rightClick) {
            session.autoRot = false;
            session.rot = (session.rot + 1) & 3;
            actionBar(player, "§7Front turned → §f" + pretty(faceOfRot(session.rot)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            return;
        }
        World world = player.getWorld();
        if (clicked == null) {
            clicked = player.getTargetBlockExact(24);
        }
        if (clicked == null) {
            player.sendMessage("§cLook at a belt or the ground.");
            return;
        }
        int[] cell = pieceCell(world, clicked);
        IslandHost here = hosts.at(clicked.getLocation());
        if (here == null || !here.equals(session.host)) {
            player.sendMessage("§cThat's not on this island.");
            return;
        }
        BlockFace facing = session.autoRot ? StateRotator.facingOfYaw(player.getLocation().getYaw()) : faceOfRot(session.rot);
        PlacedStructure placed = logistics.placePiece(player, session.host, session.type, cell[0], cell[1], cell[2], facing);
        if (placed != null) {
            end(player);
        }
    }

    private static BlockFace faceOfRot(int rot) {
        return switch (rot & 3) {
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
    }

    /** The belt cell a piece lands in: the belt you clicked (or the one standing on what you clicked), else above. */
    private int[] pieceCell(World world, Block clicked) {
        if (logistics.isBelt(world, clicked.getX(), clicked.getY(), clicked.getZ())) {
            return new int[]{clicked.getX(), clicked.getY(), clicked.getZ()};
        }
        if (logistics.isBeltSupport(world, clicked.getX(), clicked.getY(), clicked.getZ())) {
            return new int[]{clicked.getX(), clicked.getY() + 1, clicked.getZ()};
        }
        return cellOf(world, clicked);
    }

    private void renderPiece(Player player, Session session) {
        World world = player.getWorld();
        Block target = player.getTargetBlockExact(24);
        if (target == null) {
            session.ghost.clearRun();
            actionBar(player, "§6✎ §7Look at a belt to cut a " + session.label + " into it");
            return;
        }
        int[] cell = pieceCell(world, target);
        String problem = logistics.pieceProblem(session.host, world, cell[0], cell[1], cell[2]);
        session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
        Belt belt = logistics.belt(world, cell[0], cell[1], cell[2]);
        BlockFace front = belt != null ? belt.dir()
                : session.autoRot ? StateRotator.facingOfYaw(player.getLocation().getYaw()) : faceOfRot(session.rot);
        if (problem != null) {
            actionBar(player, "§c✖ " + problem);
            return;
        }
        long price = structures.priceFor(session.host, session.type);
        actionBar(player, (belt != null ? "§a✔ Cut into this belt" : "§a✔ Set down here") + " §7· front → §f"
                + pretty(front) + " §8(§6" + price + "§8) §7· §fright-click §7place"
                + (belt == null ? " · §fleft-click §7turn" : ""));
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
    // preview (every 4 ticks)
    // ------------------------------------------------------------------------------------------------

    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline()) {
                session.ghost.clear();
                sessions.remove(entry.getKey());
                continue;
            }
            if (now - session.lastAction > IDLE_MS) {
                cancel(player, "§7Build mode timed out; tool put away.");
                continue;
            }
            IslandHost here = hosts.at(player.getLocation());
            if (here == null || !here.equals(session.host)) {
                cancel(player, "§7You left the island; build tool put away.");
                continue;
            }
            boolean holding = holdingTool(player);
            if (!holding) {
                if (session.holding) {
                    session.ghost.clear();
                    session.holding = false;
                }
                continue;
            }
            session.holding = true;
            session.lastAction = Math.max(session.lastAction, now - IDLE_MS + 60_000L);
            session.clock++;
            if (session.kind == Kind.BELTS) {
                renderBelts(player, session);
            } else if (session.kind == Kind.PIECE) {
                renderPiece(player, session);
            } else {
                renderGhost(player, session);
            }
        }
    }

    private void renderGhost(Player player, Session session) {
        Block ground = groundOf(player);
        if (ground == null) {
            session.ghost.clearStructure();
            actionBar(player, "§7Look at the ground · §fQ §7puts the blueprint away");
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
        int[] port = session.type != null && session.type.hasOutput() ? StructureService.portLocal(session.template) : null;
        session.ghost.structure(session.template, world, ax, ay, az, rot, ok, port);
        if (!ok && session.clock % 2 == 0) {
            int shown = 0;
            Particle.DustOptions bad = new Particle.DustOptions(GhostView.BAD, 1.2f);
            for (int[] cell : validation.blocked()) {
                if (shown++ >= 10) {
                    break;
                }
                player.spawnParticle(Particle.DUST, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5, 1, 0.15, 0.15, 0.15, 0, bad);
            }
        }
        String reason = here == null || !here.equals(session.host) ? "Not on this island" : validation.reason();
        actionBar(player, ok
                ? "§a✔ " + session.label + " §7· §fright-click §7build · §fleft-click §7rotate · §fQ §7put away"
                : "§c✖ " + reason + " §7· §fleft-click §7rotate · §fQ §7put away");
    }

    private void renderBelts(Player player, Session session) {
        World world = player.getWorld();
        if (session.clock % 5 == 1) {
            List<int[]> ports = new ArrayList<>();
            int px = player.getLocation().getBlockX();
            int pz = player.getLocation().getBlockZ();
            for (PlacedStructure structure : structures.of(session.host)) {
                int[] p = structures.outputPort(structure);
                if (p != null && Math.abs(p[0] - px) <= 32 && Math.abs(p[2] - pz) <= 32 && ports.size() < 16) {
                    ports.add(p);
                }
            }
            session.ghost.chutes(world, ports);
        }
        Block target = player.getTargetBlockExact(24);
        if (session.beltStart == null) {
            if (target == null) {
                session.ghost.clearRun();
                bar(player, session, "§6⛓ §7Look at the ground to start a belt");
                return;
            }
            Belt looked = logistics.belt(world, target.getX(), target.getY(), target.getZ());
            int[] cell = cellOf(world, target);
            boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
            String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
            session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
            if (looked != null) {
                bar(player, session, "§6⛓ §7Belt → §f" + pretty(looked.dir()) + " §7· right-click extends from here · left-click takes it up");
                return;
            }
            bar(player, session, problem == null
                    ? "§6⛓ §7Right-click to set the §fstart §7· belts " + logistics.count(session.host) + "/" + logistics.cap(session.host)
                    : "§c✖ " + problem);
            return;
        }
        if (target == null) {
            session.ghost.clearRun();
            bar(player, session, "§6⛓ §7Look where the belt should end");
            return;
        }
        int[] end = cellOf(world, target);
        List<Object[]> plan = LogisticsService.plan(session.beltStart, end,
                StateRotator.facingOfYaw(player.getLocation().getYaw()));
        if (plan == null) {
            session.ghost.cursor(world, end[0], end[1], end[2], false);
            bar(player, session, "§c✖ Belts run flat: pick an end at the same height");
            return;
        }
        String first = null;
        List<int[]> points = new ArrayList<>(plan.size());
        for (Object[] step : plan) {
            int[] p = (int[]) step[0];
            points.add(p);
            if (first == null) {
                boolean continuing = logistics.belt(world, p[0], p[1], p[2]) != null;
                first = logistics.cellProblem(session.host, world, p[0], p[1], p[2], continuing);
            }
        }
        session.ghost.run(world, points, first == null);
        long price = logistics.tileCost() * plan.size();
        bar(player, session, first == null
                ? "§a✔ " + plan.size() + " tiles §8(§6~" + price + "§8) §7· right-click to lay · §fQ §7finish"
                : "§c✖ " + first);
    }

    private static void bar(Player player, Session session, String text) {
        if (System.currentTimeMillis() >= session.quietUntil) {
            actionBar(player, text);
        }
    }

    private static String pretty(BlockFace face) {
        return switch (face) {
            case NORTH -> "north";
            case SOUTH -> "south";
            case EAST -> "east";
            default -> "west";
        };
    }

    private static void actionBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }
}
