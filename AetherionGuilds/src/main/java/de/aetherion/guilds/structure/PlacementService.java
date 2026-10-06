package de.aetherion.guilds.structure;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.Belt;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.Template;
import de.aetherion.guilds.util.GuildFormat;

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
 * <b>The Build Tool</b>: one item in your hand, Satisfactory-style. It holds one picked part at a time and the
 * hotbar icon shows which.
 * <ul>
 *   <li><b>Sneak + scroll</b> steps through the parts you have unlocked; <b>F</b> opens the picker with all of
 *   them (locked ones show where to unlock them).</li>
 *   <li><b>Right-click</b> builds what's picked where the hologram stands, <b>left-click</b> turns it. You keep
 *   building after each click; every copy is paid from your island's storage.</li>
 *   <li><b>Belt</b>: right-click a start, right-click an end (straight or one bend); it keeps chaining.</li>
 *   <li><b>Take down</b>: look at a belt or a building (it turns red), right-click. Buildings ask for a second
 *   click; half their cost goes back to storage.</li>
 *   <li>Right-click one of your buildings with a part picked: it opens, like without the tool.</li>
 * </ul>
 * Clicks are only captured while the tool is in the main hand: scroll to another slot and you play normally,
 * scroll back and the tool still holds your pick. <b>Q</b> puts it away. The holograms are display entities only
 * the builder sees.
 */
public final class PlacementService {

    public enum Kind {
        STRUCTURE,
        PROJECT_SITE,
        BELTS,
        /** Splitter / Overflow Gate / Sorter: one cell, straight onto a belt or a free spot. */
        PIECE,
        /** Take belts and buildings down. */
        DISMANTLE
    }

    /** What the Build Tool can hold, in the order sneak+scroll walks them. */
    public enum Part {
        BELT(null, "Belt", Material.IRON_TRAPDOOR, "Carries goods from a quarry to a hut or machine."),
        STORAGE_HUT(StructureType.STORAGE_HUT, "Storage Hut", Material.BARREL, "Holds what your belts bring in."),
        HUB(StructureType.WORKSHOP, "Hub", Material.LECTERN, "Your factory's heart: unlocks and upgrades."),
        DEPOT(StructureType.DEPOT, "Depot", Material.CHEST, "More storage. Belts can end here or pass through."),
        MILL(StructureType.MILL, "Mill", Material.GRINDSTONE, "Presses 9 goods into 1 compressed."),
        SPLITTER(StructureType.SPLITTER, "Splitter", Material.WAXED_COPPER_GRATE, "Splits one belt into equal shares."),
        FORGE(StructureType.FORGE, "Forge", Material.BLAST_FURNACE, "Presses compressed goods once more."),
        OVERFLOW(StructureType.OVERFLOW, "Overflow Gate", Material.WAXED_WEATHERED_COPPER_GRATE, "Front first, sides take the spill."),
        SORTER(StructureType.SORTER, "Sorter", Material.WAXED_OXIDIZED_COPPER_GRATE, "One good out the front, the rest out the sides."),
        DISMANTLE(null, "Take down", Material.BARRIER, "Removes belts and buildings. Half the cost comes back.");

        private final StructureType type;
        private final String label;
        private final Material icon;
        private final String line;

        Part(StructureType type, String label, Material icon, String line) {
            this.type = type;
            this.label = label;
            this.icon = icon;
            this.line = line;
        }

        public StructureType type() {
            return type;
        }

        public String label() {
            return label;
        }

        public Material icon() {
            return icon;
        }

        public String line() {
            return line;
        }

        public static Part of(StructureType type) {
            for (Part part : values()) {
                if (part.type == type && type != null) {
                    return part;
                }
            }
            return null;
        }
    }

    public interface SiteConfirm {
        void confirm(Player player, int ax, int ay, int az, int rot);
    }

    private static final long IDLE_MS = 600_000L;
    private static final long CONFIRM_MS = 4_000L;

    private static final class Session {
        final IslandHost host;
        final SiteConfirm onConfirm;
        final GhostView ghost;
        Part part;
        Kind kind;
        StructureType type;
        Template template;
        String label;
        int rot;
        boolean autoRot = true;
        long lastAction = System.currentTimeMillis();
        long lastBuild;
        int[] beltStart;
        int clock;
        boolean holding;
        long quietUntil;
        UUID armed;
        long armedUntil;

        Session(IslandHost host, SiteConfirm onConfirm, GhostView ghost) {
            this.host = host;
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
    private final Map<UUID, Part> lastPart = new HashMap<>();
    private java.util.function.BiConsumer<Player, IslandHost> picker = (player, host) -> {
    };

    public PlacementService(JavaPlugin plugin, HostService hosts, StructureService structures,
                            LogisticsService logistics) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.toolKey = new NamespacedKey(plugin, "build_tool");
    }

    /** The picker screen (F with the tool in hand). */
    public void attachPicker(java.util.function.BiConsumer<Player, IslandHost> picker) {
        this.picker = picker == null ? (player, host) -> {
        } : picker;
    }

    public boolean has(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public Kind kind(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? null : session.kind;
    }

    /** The part the player's tool holds right now (null without a tool or on a project site). */
    public Part part(Player player) {
        Session session = player == null ? null : sessions.get(player.getUniqueId());
        return session == null ? null : session.part;
    }

    // ------------------------------------------------------------------------------------------------
    // what can be picked
    // ------------------------------------------------------------------------------------------------

    /** Why this part can't be picked on this island yet (null when it can). Costs don't count here. */
    public String lockReason(IslandHost host, Part part) {
        StructureType type = part.type();
        if (type == null) {
            return null;
        }
        if (part == Part.HUB) {
            return structures.hasWorkshop(host) ? "Your Hub already stands." : null;
        }
        if (type.needsWorkshop() && !structures.hasWorkshop(host)) {
            return "Build your Hub first.";
        }
        if (!structures.unlocked(host, type)) {
            de.aetherion.guilds.structure.BuildCosts.Unlock unlock = BuildCosts.unlock(type);
            return unlock != null && structures.hubLevel(host) < unlock.hubLevel()
                    ? "Unlock it at your Hub (needs Hub tier " + unlock.hubLevel() + ")."
                    : "Unlock it at your Hub first.";
        }
        return null;
    }

    /** The parts sneak+scroll walks through on this island, in order. */
    public List<Part> pickable(IslandHost host) {
        List<Part> out = new ArrayList<>();
        for (Part part : Part.values()) {
            if (lockReason(host, part) == null) {
                out.add(part);
            }
        }
        return out;
    }

    /** What a new pick defaults to: the next thing a fresh island needs, else the last pick. */
    private Part suggested(Player player, IslandHost host) {
        if (structures.count(host, StructureType.STORAGE_HUT) == 0) {
            return Part.STORAGE_HUT;
        }
        if (logistics.count(host) == 0) {
            return Part.BELT;
        }
        Part last = lastPart.get(player.getUniqueId());
        if (last != null && lockReason(host, last) == null) {
            return last;
        }
        return structures.hasWorkshop(host) ? Part.BELT : Part.HUB;
    }

    /** "96 Cobblestone (230)" with green/red per line: what this costs against what the island has. */
    public String costLine(Player player, IslandHost host, List<BuildCosts.Line> bill) {
        if (bill.isEmpty()) {
            return "§afree";
        }
        StringBuilder out = new StringBuilder();
        for (BuildCosts.Line line : bill) {
            long have = line.coins() ? hosts.funds(player, host) : structures.stock(host, line.res());
            if (out.length() > 0) {
                out.append("§8, ");
            }
            out.append(have >= line.amount() ? "§a" : "§c").append(GuildFormat.compact(line.amount())).append(' ')
                    .append(line.label()).append(" §8(").append(GuildFormat.compact(have)).append(')');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // the tool item
    // ------------------------------------------------------------------------------------------------

    public boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.STRING);
    }

    /** Is the player holding the Build Tool right now? Only then are clicks and block edits captured. */
    public boolean holdingTool(Player player) {
        return player != null && isTool(player.getInventory().getItemInMainHand());
    }

    private ItemStack toolFor(Player player, Session session) {
        Part part = session.part;
        ItemStack item = new ItemStack(part == null ? Material.PAPER : part.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (part == null) {
                meta.setDisplayName("§6✎ Project site: §f" + session.label);
                lore.add("§fRight-click §7claim this spot");
                lore.add("§fLeft-click §7turn");
                lore.add("§fQ §7put away");
            } else {
                meta.setDisplayName("§6⚒ Build Tool §8» §e" + part.label());
                lore.add("§7" + part.line());
                if (part.type() != null) {
                    lore.add("§7Cost: " + costLine(player, session.host, structures.buildCost(session.host, part.type())));
                } else if (part == Part.BELT) {
                    lore.add("§7Cost: §f" + BuildCosts.BELT_COBBLE + " Cobblestone §7a tile");
                }
                lore.add("");
                switch (part) {
                    case BELT -> {
                        lore.add("§fRight-click §7start, then end");
                        lore.add("§fLeft-click §7a belt: take it up");
                    }
                    case DISMANTLE -> lore.add("§fRight-click §7take down what's red");
                    default -> {
                        lore.add("§fRight-click §7build");
                        lore.add("§fLeft-click §7turn");
                    }
                }
                lore.add("§fSneak + scroll §7next part");
                lore.add("§fF §7all parts");
                lore.add("§fQ §7put away");
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, part == null ? "SITE" : part.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Puts the tool into the main hand (the held item moves to a free slot). False if the inventory is full. */
    private boolean giveTool(Player player, Session session) {
        stripTools(player);
        PlayerInventory inventory = player.getInventory();
        ItemStack tool = toolFor(player, session);
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
            player.sendMessage("§cFree one inventory slot for the Build Tool.");
            return false;
        }
        inventory.setItem(free, current);
        inventory.setItem(held, tool);
        return true;
    }

    /** Swaps the tool item in place (new icon / name / cost) without moving it. */
    private void refreshTool(Player player, Session session) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isTool(inventory.getItem(slot))) {
                inventory.setItem(slot, toolFor(player, session));
                return;
            }
        }
        giveTool(player, session);
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
    // starting / picking / ending
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

    /** Sets what the tool holds (fields only; the caller refreshes the item). False if the part can't be held. */
    private boolean apply(Player player, Session session, Part part) {
        StructureType type = part.type();
        Template template = null;
        if (type != null && !type.router()) {
            template = structures.templates().get(type.templateId());
            if (template == null) {
                player.sendMessage("§cThe " + part.label() + " shape is missing on the server. Tell staff.");
                return false;
            }
        }
        session.part = part;
        session.type = type;
        session.template = template;
        session.label = part.label();
        session.kind = switch (part) {
            case BELT -> Kind.BELTS;
            case DISMANTLE -> Kind.DISMANTLE;
            default -> type.router() ? Kind.PIECE : Kind.STRUCTURE;
        };
        session.beltStart = null;
        session.autoRot = true;
        session.armed = null;
        session.quietUntil = 0L;
        session.ghost.clear();
        lastPart.put(player.getUniqueId(), part);
        return true;
    }

    /**
     * Hands the player the Build Tool holding {@code wanted} (null: the next thing this island needs). Used by
     * the picker, /island → Build, the Hub, Hollis and every "build X" button.
     */
    public void openTool(Player player, IslandHost host, Part wanted) {
        if (!onIsland(player, host)) {
            return;
        }
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "build"));
            return;
        }
        Part part = wanted != null ? wanted : suggested(player, host);
        String locked = lockReason(host, part);
        if (locked != null) {
            player.sendMessage("§c" + part.label() + ": " + locked);
            if (wanted != null && has(player)) {
                return;
            }
            part = suggested(player, host);
            if (lockReason(host, part) != null) {
                part = Part.BELT;
            }
        }
        Session session = sessions.get(player.getUniqueId());
        boolean fresh = session == null || !session.host.equals(host) || session.kind == Kind.PROJECT_SITE;
        if (fresh) {
            session = new Session(host, null, new GhostView(plugin, player));
        }
        if (!apply(player, session, part)) {
            return;
        }
        if (fresh) {
            if (!begin(player, session)) {
                return;
            }
        } else if (!giveTool(player, session)) {
            return;
        } else {
            player.closeInventory();
        }
        announce(player, session, fresh);
    }

    private void announce(Player player, Session session, boolean fresh) {
        Part part = session.part;
        player.sendTitle("", "§6⚒ §e" + part.label(), 0, 22, 8);
        if (fresh) {
            player.sendMessage("§6⚒ Build Tool §7in your hand. §fSneak + scroll §7picks a part, §fF §7shows all,"
                    + " §fQ §7puts it away.");
        }
        player.sendMessage(switch (part) {
            case BELT -> "§7Belt: §fright-click §7a start (an §6orange §7mark), then the end (a hut or machine).";
            case DISMANTLE -> "§7Take down: look at a belt or building, it turns §cred§7, then §fright-click§7.";
            case SPLITTER, OVERFLOW, SORTER -> "§7" + part.label() + ": §fright-click §7a belt to cut it in. "
                    + part.line();
            default -> "§7" + part.label() + ": look at the ground, §fright-click §7builds, §fleft-click §7turns.";
        });
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8f, 1.4f);
    }

    /** Sneak + scroll: the next / previous part this island can build. */
    public void cycle(Player player, int direction) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.part == null) {
            return;
        }
        List<Part> parts = pickable(session.host);
        if (parts.isEmpty()) {
            return;
        }
        int index = parts.indexOf(session.part);
        int n = parts.size();
        Part next = parts.get(((index < 0 ? 0 : index + direction) % n + n) % n);
        if (!apply(player, session, next)) {
            return;
        }
        session.lastAction = System.currentTimeMillis();
        refreshTool(player, session);
        int at = parts.indexOf(next);
        String before = parts.get(((at - 1) % n + n) % n).label();
        String after = parts.get((at + 1) % n).label();
        player.sendTitle("", "§8" + before + "  §6« §e§l" + next.label() + " §6»  §8" + after, 0, 18, 6);
        session.quietUntil = System.currentTimeMillis() + 900L;
        actionBar(player, "§7" + next.line());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f + 0.08f * at);
    }

    /** F with the tool in hand: the picker. */
    public void openPicker(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            picker.accept(player, session.host);
        }
    }

    public void startStructure(Player player, IslandHost host, StructureType type) {
        Part part = Part.of(type);
        if (part == null) {
            player.sendMessage("§cThat isn't built with the Build Tool.");
            return;
        }
        openTool(player, host, part);
    }

    /** Splitter / Overflow Gate / Sorter: the tool targets one cell; on a belt it cuts into the line. */
    public void startPiece(Player player, IslandHost host, StructureType type) {
        startStructure(player, host, type);
    }

    public void startBelts(Player player, IslandHost host) {
        openTool(player, host, Part.BELT);
    }

    public void startProjectSite(Player player, IslandHost host, Template template, String label, SiteConfirm confirm) {
        if (!onIsland(player, host)) {
            return;
        }
        if (template == null) {
            player.sendMessage("§cThat project's shape is missing on the server.");
            return;
        }
        Session session = new Session(host, confirm, new GhostView(plugin, player));
        session.kind = Kind.PROJECT_SITE;
        session.type = StructureType.PROJECT;
        session.template = template;
        session.label = label;
        if (!begin(player, session)) {
            return;
        }
        player.sendMessage("§6✎ Project site: §f" + label + " §7(in your hand)");
        player.sendMessage("§7Pick flat ground for the finished build. §fRight-click §7claims it, §fleft-click §7turns.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
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

    /** Does a right-click on one of the island's buildings open it (instead of building)? */
    public boolean opensBuildings(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && (session.kind == Kind.STRUCTURE || session.kind == Kind.PIECE);
    }

    /** A click with the build tool. The caller always cancels the event. */
    public void onInteract(Player player, boolean rightClick, Block clicked, boolean sneaking) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            stripTools(player);
            player.sendMessage("§7That build tool had expired; put away.");
            return;
        }
        session.lastAction = System.currentTimeMillis();
        switch (session.kind) {
            case BELTS -> {
                handleBelts(player, session, rightClick, clicked);
                return;
            }
            case PIECE -> {
                handlePiece(player, session, rightClick, clicked);
                return;
            }
            case DISMANTLE -> {
                handleDismantle(player, session, rightClick, clicked);
                return;
            }
            default -> {
            }
        }
        if (!rightClick) {
            session.autoRot = false;
            session.rot = (session.rot + 1) & 3;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastBuild < 250L) {
            return; // one click can arrive twice (block + air); never build two
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
                session.lastBuild = now;
                afterBuild(player, session);
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

    /** After a build the tool stays; if that part can't be built again here (the Hub), it moves on to belts. */
    private void afterBuild(Player player, Session session) {
        if (session.part != null && lockReason(session.host, session.part) != null) {
            if (apply(player, session, Part.BELT)) {
                refreshTool(player, session);
            }
            return;
        }
        refreshTool(player, session); // cost line vs. the new stock
    }

    /** Tool dropped (Q): the mode ends quietly. */
    public void onDropTool(Player player) {
        Session session = sessions.get(player.getUniqueId());
        cancel(player, session == null ? null : "§7Build Tool put away. §8(/island → Build hands it back)");
    }

    private void end(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.ghost.clear();
        }
        stripTools(player);
    }

    // ------------------------------------------------------------------------------------------------
    // take down
    // ------------------------------------------------------------------------------------------------

    private Belt beltAt(World world, Block block) {
        Belt belt = logistics.belt(world, block.getX(), block.getY(), block.getZ());
        if (belt == null && logistics.isBeltSupport(world, block.getX(), block.getY(), block.getZ())) {
            belt = logistics.belt(world, block.getX(), block.getY() + 1, block.getZ());
        }
        return belt;
    }

    private PlacedStructure buildingAt(IslandHost host, Block block) {
        PlacedStructure structure = structures.owner(block);
        if (structure == null) {
            structure = structures.at(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
        return structure != null && structure.host().equals(host) ? structure : null;
    }

    /** Why this can't be taken down with the tool (null when it can). */
    private static String keepReason(PlacedStructure structure) {
        if (structure.building()) {
            return "Still going up";
        }
        return switch (structure.type()) {
            case QUARRY_HOUSING -> "Quarries are picked up from their own menu";
            case PROJECT -> "Guild projects stay";
            default -> PlacedStructure.total(structure.store()) > 0L || PlacedStructure.total(structure.input()) > 0L
                    || PlacedStructure.total(structure.output()) > 0L ? "Empty it first, so nothing is lost" : null;
        };
    }

    private void handleDismantle(Player player, Session session, boolean rightClick, Block clicked) {
        if (!rightClick) {
            session.armed = null;
            return;
        }
        long clickAt = System.currentTimeMillis();
        if (clickAt - session.lastBuild < 250L) {
            return; // one click can arrive twice (block + air): never confirm with one click
        }
        session.lastBuild = clickAt;
        Block target = clicked != null ? clicked : player.getTargetBlockExact(24);
        if (target == null) {
            return;
        }
        World world = target.getWorld();
        Belt belt = beltAt(world, target);
        if (belt != null) {
            if (logistics.remove(player, session.host, belt.x(), belt.y(), belt.z())) {
                actionBar(player, "§7Belt taken up §8(+" + BuildCosts.BELT_COBBLE + " Cobblestone back)");
                session.quietUntil = System.currentTimeMillis() + 800L;
            }
            return;
        }
        PlacedStructure structure = buildingAt(session.host, target);
        if (structure == null) {
            return;
        }
        String keep = keepReason(structure);
        if (keep != null) {
            player.sendMessage("§7" + keep + ".");
            return;
        }
        long now = System.currentTimeMillis();
        if (!structure.id().equals(session.armed) || now > session.armedUntil) {
            session.armed = structure.id();
            session.armedUntil = now + CONFIRM_MS;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            actionBar(player, "§c§lRight-click again §7to take down your §f" + structure.label()
                    + " §8(half its cost comes back)");
            session.quietUntil = now + 1500L;
            return;
        }
        session.armed = null;
        if (structures.deconstruct(player, structure)) {
            session.ghost.clearMark();
            refreshTool(player, session);
        }
    }

    private void renderDismantle(Player player, Session session) {
        Block target = player.getTargetBlockExact(24);
        if (target == null) {
            session.ghost.clearMark();
            bar(player, session, "§7Look at a belt or a building to take it down");
            return;
        }
        World world = target.getWorld();
        Belt belt = beltAt(world, target);
        if (belt != null) {
            session.ghost.mark(world, belt.x(), belt.y(), belt.z(), belt.x(), belt.y(), belt.z());
            bar(player, session, "§c✖ Belt §8· §fright-click §7takes it up §8(+" + BuildCosts.BELT_COBBLE
                    + " Cobblestone back)");
            return;
        }
        PlacedStructure structure = buildingAt(session.host, target);
        if (structure == null) {
            session.ghost.clearMark();
            bar(player, session, "§7Nothing of yours to take down here");
            return;
        }
        int y1 = Math.max(structure.minY(), structure.y() + 1);
        session.ghost.mark(world, structure.minX(), y1, structure.minZ(), structure.maxX(),
                Math.max(y1, structure.maxY()), structure.maxZ());
        String keep = keepReason(structure);
        boolean armed = structure.id().equals(session.armed) && System.currentTimeMillis() <= session.armedUntil;
        bar(player, session, keep != null ? "§e" + structure.label() + " §8· §7" + keep
                : armed ? "§c§lRight-click again §7to take down your §f" + structure.label()
                : "§c✖ " + structure.label() + " §8· §fright-click §7take down §8(half its cost comes back)");
    }

    private void handleBelts(Player player, Session session, boolean rightClick, Block clicked) {
        long clickAt = System.currentTimeMillis();
        if (clickAt - session.lastBuild < 200L) {
            return; // one click can arrive twice (block + air)
        }
        session.lastBuild = clickAt;
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
                actionBar(player, "§aLaid §8· §7keep clicking to extend");
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
        long now = System.currentTimeMillis();
        if (now - session.lastBuild < 250L) {
            return;
        }
        PlacedStructure placed = logistics.placePiece(player, session.host, session.type, cell[0], cell[1], cell[2], facing);
        if (placed != null) {
            session.lastBuild = now;
            afterBuild(player, session);
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
            bar(player, session, "§6⚒ §7Look at a belt to cut a " + session.label + " into it");
            return;
        }
        int[] cell = pieceCell(world, target);
        String problem = logistics.pieceProblem(session.host, world, cell[0], cell[1], cell[2]);
        session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
        Belt belt = logistics.belt(world, cell[0], cell[1], cell[2]);
        BlockFace front = belt != null ? belt.dir()
                : session.autoRot ? StateRotator.facingOfYaw(player.getLocation().getYaw()) : faceOfRot(session.rot);
        if (problem == null) {
            problem = structures.blockedReason(player, session.host, session.type);
        }
        if (problem != null) {
            bar(player, session, "§c✖ " + problem);
            return;
        }
        List<BuildCosts.Line> bill = structures.buildCost(session.host, session.type);
        String missing = structures.missing(player, session.host, bill);
        bar(player, session, (missing != null ? "§e✖ " + session.label + " §8· §c" + missing
                : (belt != null ? "§a✔ Cut into this belt" : "§a✔ Set down here") + " §8· §7front → §f" + pretty(front))
                + " §8· " + costLine(player, session.host, bill)
                + (missing == null && belt == null ? " §8· §fleft-click §7turn" : ""));
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
                cancel(player, "§7Build Tool put away (unused for a while).");
                continue;
            }
            IslandHost here = hosts.at(player.getLocation());
            if (here == null || !here.equals(session.host)) {
                cancel(player, "§7You left the island; Build Tool put away.");
                continue;
            }
            if (isTool(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(null); // the tool lives in the hotbar only
                refreshTool(player, session);
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
            switch (session.kind) {
                case BELTS -> renderBelts(player, session);
                case PIECE -> renderPiece(player, session);
                case DISMANTLE -> renderDismantle(player, session);
                default -> renderGhost(player, session);
            }
        }
    }

    private void renderGhost(Player player, Session session) {
        Block looked = player.getTargetBlockExact(24);
        if (looked != null && session.kind == Kind.STRUCTURE) {
            PlacedStructure mine = structures.owner(looked);
            if (mine != null && mine.host().equals(session.host)) {
                session.ghost.clearStructure();
                bar(player, session, "§7Your §f" + mine.label() + " §8· §fright-click §7opens it");
                return;
            }
        }
        Block ground = groundOf(player);
        if (ground == null) {
            session.ghost.clearStructure();
            bar(player, session, "§6⚒ §e" + session.label + " §8· §7look at the ground §8· §fsneak + scroll §7other part");
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
        boolean onIsland = here != null && here.equals(session.host);
        String blocked = session.kind == Kind.STRUCTURE ? structures.blockedReason(player, session.host, session.type) : null;
        List<BuildCosts.Line> bill = session.kind == Kind.STRUCTURE ? structures.buildCost(session.host, session.type)
                : List.of();
        String missing = blocked == null ? structures.missing(player, session.host, bill) : null;
        boolean fits = validation.ok() && onIsland;
        boolean ok = fits && blocked == null && missing == null;
        int[] port = session.type != null && session.type.hasOutput() ? StructureService.portLocal(session.template) : null;
        session.ghost.structure(session.template, world, ax, ay, az, rot, ok, port);
        if (!fits && session.clock % 2 == 0) {
            int shown = 0;
            Particle.DustOptions bad = new Particle.DustOptions(GhostView.BAD, 1.2f);
            for (int[] cell : validation.blocked()) {
                if (shown++ >= 10) {
                    break;
                }
                player.spawnParticle(Particle.DUST, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5, 1, 0.15, 0.15, 0.15, 0, bad);
            }
        }
        String cost = bill.isEmpty() ? "" : " §8· " + costLine(player, session.host, bill);
        String text;
        if (blocked != null) {
            text = "§c✖ " + session.label + " §8· §c" + blocked;
        } else if (!fits) {
            text = "§c✖ " + (onIsland ? validation.reason() : "Not on this island") + cost + " §8· §fleft-click §7turn";
        } else if (missing != null) {
            text = "§e✖ " + session.label + " §8· §c" + missing + cost;
        } else {
            text = "§a✔ " + session.label + cost + " §8· §fright-click §7build §8· §fleft-click §7turn";
        }
        bar(player, session, text);
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
                bar(player, session, "§6⚒ §eBelt §8· §7look at the ground to start §8· §fsneak + scroll §7other part");
                return;
            }
            Belt looked = logistics.belt(world, target.getX(), target.getY(), target.getZ());
            int[] cell = cellOf(world, target);
            boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
            String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
            session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
            if (looked != null) {
                bar(player, session, "§6⚒ §7Belt → §f" + pretty(looked.dir()) + " §8· §fright-click §7extends from here §8· §fleft-click §7takes it up");
                return;
            }
            bar(player, session, problem == null
                    ? "§6⚒ §eBelt §8· §fright-click §7sets the start §8· §7belts §f" + logistics.count(session.host) + "§8/" + logistics.cap(session.host)
                    : "§c✖ " + problem);
            return;
        }
        if (target == null) {
            session.ghost.clearRun();
            bar(player, session, "§6⚒ §7Look where the belt should end §8· §fleft-click §7clears the start");
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
        int fresh = 0;
        List<int[]> points = new ArrayList<>(plan.size());
        for (Object[] step : plan) {
            int[] p = (int[]) step[0];
            points.add(p);
            boolean continuing = logistics.belt(world, p[0], p[1], p[2]) != null;
            if (!continuing) {
                fresh++;
            }
            if (first == null) {
                first = logistics.cellProblem(session.host, world, p[0], p[1], p[2], continuing);
            }
        }
        LogisticsService.BeltPass pass = logistics.beltPass();
        int free = pass == null ? 0 : Math.max(0, Math.min(fresh, pass.freeTiles(session.host)));
        List<BuildCosts.Line> bill = fresh - free <= 0 ? List.of()
                : List.of(new BuildCosts.Line(BuildCosts.COBBLE, (fresh - free) * BuildCosts.BELT_COBBLE));
        String missing = structures.missing(player, session.host, bill);
        session.ghost.run(world, points, first == null && missing == null);
        String cost = (bill.isEmpty() ? "§afree" : costLine(player, session.host, bill))
                + (free > 0 ? " §8(§e" + free + " free§8)" : "");
        bar(player, session, first != null ? "§c✖ " + first
                : missing != null ? "§e✖ " + plan.size() + " tiles §8· §c" + missing
                : "§a✔ " + plan.size() + " tiles §8· " + cost + " §8· §fright-click §7lay");
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
