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
 *   <li><b>Belt</b>: right-click a chute (or the quarry), then the hut or machine it should feed: the run picks
 *   the nearest free side and turns its last tile in. An end in the open keeps chaining; left-click clears it.</li>
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
        MILL(StructureType.MILL, "Mill", Material.GRINDSTONE, "Grinds 128 raw into 1 Compressed."),
        SPLITTER(StructureType.SPLITTER, "Splitter", Material.WAXED_COPPER_GRATE, "Splits one belt into equal shares."),
        FORGE(StructureType.FORGE, "Forge", Material.BLAST_FURNACE, "Presses 128 Compressed into 1 Compacted."),
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
        if (!structures.hasWorkshop(host)) {
            for (LogisticsService.Route route : logistics.routes(host)) {
                if (route.to() != null && route.from().type() == StructureType.QUARRY_HOUSING) {
                    return Part.HUB; // first chain runs: the Hub is next
                }
            }
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
                        lore.add("§fRight-click §7orange chute, then the hut (snaps in)");
                        lore.add("§fLeft-click §7cancel start");
                        lore.add("§fQ §7put away · Take down for remove");
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
            case BELT -> "§7Belt: §fright-click §7the §6orange chute§7, then §fright-click §7the hut or machine §8(§agreen pads§8)§7 — it snaps in."
                    + " Ending in the open keeps chaining. §fLeft-click §7cancels the start.";
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
                actionBar(player, "§7Belt taken up §8(" + logistics.lastRemoveLine() + ")");
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
            bar(player, session, "§c✖ Belt §8· §fright-click §7takes it up §8(refunded)");
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
        // Left-click always cancels the start (Satisfactory-style). Never removes belts here —
        // that is Take down / /island done. One cancel rule, taught once.
        if (!rightClick) {
            if (session.beltStart != null) {
                session.beltStart = null;
                session.ghost.clearRun();
                actionBar(player, "§7Start cleared §8· §fright-click §7a chute to begin again");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            } else {
                actionBar(player, "§7No start set §8· §fQ §7puts the Build Tool away");
            }
            return;
        }
        if (clicked == null) {
            actionBar(player, "§7Look at the §6orange chute §7or the ground");
            return;
        }
        if (session.beltStart == null) {
            int[] cell = startCell(player, session.host, world, clicked);
            IslandHost here = hosts.at(new org.bukkit.Location(world, cell[0] + 0.5, cell[1], cell[2] + 0.5));
            if (here == null || !here.equals(session.host)) {
                player.sendMessage("§cThat's not on this island.");
                return;
            }
            boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
            String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
            if (problem != null) {
                player.sendMessage("§cCan't start there: " + problem + ".");
                actionBar(player, "§c✖ " + problem);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                return;
            }
            session.beltStart = cell;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.4f);
            actionBar(player, "§aStart set §7· now aim at the §fhut §7or machine, §fright-click §8· §fleft-click §7cancel");
            session.quietUntil = System.currentTimeMillis() + 1200L;
            return;
        }
        BlockFace fallback = StateRotator.facingOfYaw(player.getLocation().getYaw());
        EndAim aim = endAim(player, session, world, clicked, fallback);
        IslandHost here = hosts.at(new org.bukkit.Location(world, aim.cell[0] + 0.5, aim.cell[1], aim.cell[2] + 0.5));
        if (here == null || !here.equals(session.host)) {
            player.sendMessage("§cThat's not on this island.");
            return;
        }
        if (aim.problem != null) {
            player.sendMessage("§c" + aim.problem + ".");
            actionBar(player, "§c✖ " + aim.problem);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        int maxRun = maxRun();
        if (aim.plan.size() > maxRun) {
            String why = "Too long (" + aim.plan.size() + "). Max " + maxRun + " tiles — click a closer end first, then keep going.";
            player.sendMessage("§c" + why);
            actionBar(player, "§c✖ " + why);
            return;
        }
        if (logistics.lay(player, session.host, session.beltStart, aim.cell, fallback, aim.xFirst, aim.into)) {
            String cost = logistics.lastLayCostLine();
            if (aim.feeds != null) {
                // plugged into a hut / machine: this belt is done; the next click starts a new one
                session.beltStart = null;
                session.ghost.clearRun();
                session.quietUntil = System.currentTimeMillis() + 2600L;
                if (!logistics.lastLayTalked()) {
                    actionBar(player, "§a✔ Into your §f" + aim.feeds.label() + " §8· " + cost
                            + " §8· §7right-click another chute for a new belt");
                }
                return;
            }
            session.beltStart = aim.cell;
            if (!logistics.lastLayTalked()) {
                actionBar(player, "§a✔ Laid §8· " + cost + " §8· §7keep clicking to extend §8· §fleft-click §7cancel");
                session.quietUntil = System.currentTimeMillis() + 1800L;
            } else {
                session.quietUntil = System.currentTimeMillis() + 2500L; // let the "Connected" line stay up
            }
        }
    }

    private int maxRun() {
        return Math.max(8, plugin.getConfig().getInt("logistics.max-run", 64));
    }

    /** One of the island's buildings at this block (its own blocks, or anything inside its footprint). */
    private PlacedStructure structureAt(IslandHost host, World world, Block block) {
        PlacedStructure structure = structures.owner(block);
        if (structure == null) {
            structure = structures.atColumn(world, block.getX(), block.getZ());
            if (structure != null && (block.getY() < structure.minY() - 1 || block.getY() > structure.maxY() + 1)) {
                structure = null;
            }
        }
        return structure != null && structure.host().equals(host) ? structure : null;
    }

    /**
     * Where a belt starts when the player clicks here: aiming anywhere at a quarry or machine starts on its
     * orange chute (frameless quarries: the side facing you); otherwise the cell itself, snapped to a chute
     * within a step and a half.
     */
    private int[] startCell(Player player, IslandHost host, World world, Block target) {
        if (logistics.belt(world, target.getX(), target.getY(), target.getZ()) == null
                && !logistics.isBeltSupport(world, target.getX(), target.getY(), target.getZ())) {
            PlacedStructure structure = structureAt(host, world, target);
            if (structure != null && structure.type().hasOutput() && !structure.type().router()) {
                int[] port = structures.outputPort(structure);
                if (port != null) {
                    return port.clone();
                }
                if (structure.frameless() || structure.type() == StructureType.QUARRY_HOUSING) {
                    return besideToward(structure, player.getLocation().getX(), player.getLocation().getZ());
                }
            }
        }
        return snapBeltCell(host, world, cellOf(world, target));
    }

    /**
     * Soft snap toward a nearby machine chute (same Y, within ~1.5 blocks). Already on a belt → keep the cell.
     * Opus Session-1 zip dropped this helper while still calling it — restored so belts compile.
     */
    private int[] snapBeltCell(IslandHost host, World world, int[] cell) {
        if (logistics.belt(world, cell[0], cell[1], cell[2]) != null) {
            return cell;
        }
        int[] best = null;
        double bestDist = 2.25; // 1.5²
        for (PlacedStructure structure : structures.of(host)) {
            int[] port = structures.outputPort(structure);
            if (port == null || port[1] != cell[1]) {
                continue;
            }
            double dx = port[0] - cell[0];
            double dz = port[2] - cell[2];
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = port;
            }
        }
        return best != null ? best.clone() : cell;
    }

    /** The cell right beside a structure on the side facing (px, pz), one above its ground. */
    private static int[] besideToward(PlacedStructure structure, double px, double pz) {
        double cx = (structure.minX() + structure.maxX() + 1) / 2.0;
        double cz = (structure.minZ() + structure.maxZ() + 1) / 2.0;
        int y = structure.y() + 1;
        if (Math.abs(px - cx) >= Math.abs(pz - cz)) {
            int x = px >= cx ? structure.maxX() + 1 : structure.minX() - 1;
            return new int[]{x, y, clamp((int) Math.floor(pz), structure.minZ(), structure.maxZ())};
        }
        int z = pz >= cz ? structure.maxZ() + 1 : structure.minZ() - 1;
        return new int[]{clamp((int) Math.floor(px), structure.minX(), structure.maxX()), y, z};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A belt run as the tool will lay it: the end cell, the forced bend / last turn, and what it feeds. */
    private static final class EndAim {
        int[] cell;
        Boolean xFirst;
        BlockFace into;
        List<Object[]> plan;
        PlacedStructure feeds;
        String problem;
    }

    /** The structure that owns this chute cell (so a belt never snaps back into where it came from). */
    private PlacedStructure ownerOfPort(IslandHost host, int[] cell) {
        for (PlacedStructure structure : structures.of(host)) {
            int[] port = structures.outputPort(structure);
            if (port != null && port[0] == cell[0] && port[1] == cell[1] && port[2] == cell[2]) {
                return structure;
            }
        }
        return null;
    }

    /** The first problem on a planned run (null when every tile can go down). */
    private String runProblem(IslandHost host, World world, List<Object[]> plan) {
        for (Object[] step : plan) {
            int[] p = (int[]) step[0];
            boolean continuing = logistics.belt(world, p[0], p[1], p[2]) != null;
            String problem = logistics.cellProblem(host, world, p[0], p[1], p[2], continuing);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** What the last tile of this run points into (a hut, depot, machine or belt piece), or null. */
    private PlacedStructure feedsInto(World world, List<Object[]> plan, PlacedStructure from) {
        if (plan == null || plan.isEmpty()) {
            return null;
        }
        Object[] last = plan.get(plan.size() - 1);
        int[] p = (int[]) last[0];
        BlockFace dir = (BlockFace) last[1];
        int nx = p[0] + dir.getModX();
        int nz = p[2] + dir.getModZ();
        PlacedStructure hit = structures.atColumn(world, nx, nz);
        return hit != null && hit != from && structures.acceptsAt(hit, nx, p[1], nz) ? hit : null;
    }

    /**
     * Satisfactory snap: aim the end at a hut or machine (or at the ground right beside one) and the run picks
     * the nearest free cell on the side facing you and turns its last tile into it. Anything else runs flat at
     * the start's height to where you aim. The preview and the click both use this, so the ghost never lies.
     */
    private EndAim endAim(Player player, Session session, World world, Block target, BlockFace fallback) {
        IslandHost host = session.host;
        int[] start = session.beltStart;
        PlacedStructure from = ownerOfPort(host, start);
        EndAim aim = new EndAim();
        boolean onBelt = logistics.belt(world, target.getX(), target.getY(), target.getZ()) != null
                || logistics.isBeltSupport(world, target.getX(), target.getY(), target.getZ());
        PlacedStructure aimed = onBelt ? null : structureAt(host, world, target);
        if (aimed != null && aimed != from && aimed.type().acceptsInput() && !aimed.building()) {
            if (snapInto(host, world, aimed, start, fallback, aim)) {
                return aim;
            }
        }
        int[] raw = cellOf(world, target);
        int[] cell = onBelt ? raw : snapBeltCell(host, world, raw);
        if (cell[1] != start[1]) {
            cell = new int[]{cell[0], start[1], cell[2]}; // belts run flat: follow the aim at the start's height
        }
        aim.cell = cell;
        aim.plan = LogisticsService.plan(start, cell, fallback);
        aim.feeds = feedsInto(world, aim.plan, from);
        if (aim.feeds == null && aim.plan != null && !onBelt) {
            // ended right beside a hut / machine but running past it: turn the last tile in
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                int nx = cell[0] + face.getModX();
                int nz = cell[2] + face.getModZ();
                PlacedStructure next = structures.atColumn(world, nx, nz);
                if (next == null || next == from || !structures.acceptsAt(next, nx, cell[1], nz)
                        || structures.atColumn(world, cell[0], cell[2]) != null) {
                    continue;
                }
                boolean alongX = face == BlockFace.EAST || face == BlockFace.WEST;
                List<Object[]> turned = LogisticsService.plan(start, cell, fallback, !alongX, face);
                if (turned != null) {
                    aim.xFirst = !alongX;
                    aim.into = face;
                    aim.plan = turned;
                    aim.feeds = next;
                }
                break;
            }
        }
        if (aim.plan == null) {
            aim.problem = "Belts run flat — aim at ground level with the start";
            aim.plan = List.of();
            return aim;
        }
        String problem = runProblem(host, world, aim.plan);
        if (problem != null) {
            aim.problem = problem.equals("a block is in the way") || problem.equals("belts need solid ground")
                    ? problem + " (belts run flat at the start's height)" : problem;
        }
        return aim;
    }

    /** Fill {@code aim} with the best run from start into one side of {@code target}. False if no side fits. */
    private boolean snapInto(IslandHost host, World world, PlacedStructure target, int[] start, BlockFace fallback,
                             EndAim aim) {
        int y = start[1];
        if (y < target.y() || y > target.y() + 3) {
            aim.cell = start.clone();
            aim.plan = List.of();
            aim.problem = "Your " + target.label() + " sits at another height — start a belt on its level";
            return true;
        }
        List<Object[]> options = new ArrayList<>(); // {cell, xFirst, into}
        int minX = target.minX();
        int maxX = target.maxX();
        int minZ = target.minZ();
        int maxZ = target.maxZ();
        if (start[0] < minX) {
            addSide(options, minX - 1, true, minZ, maxZ, start[2], y, false, BlockFace.EAST);
        }
        if (start[0] > maxX) {
            addSide(options, maxX + 1, true, minZ, maxZ, start[2], y, false, BlockFace.WEST);
        }
        if (start[2] < minZ) {
            addSide(options, minZ - 1, false, minX, maxX, start[0], y, true, BlockFace.SOUTH);
        }
        if (start[2] > maxZ) {
            addSide(options, maxZ + 1, false, minX, maxX, start[0], y, true, BlockFace.NORTH);
        }
        if (options.isEmpty()) {
            return false;
        }
        options.sort(java.util.Comparator.comparingInt(o -> {
            int[] c = (int[]) o[0];
            return Math.abs(c[0] - start[0]) + Math.abs(c[2] - start[2]);
        }));
        EndAim firstTry = null;
        int tried = 0;
        for (Object[] option : options) {
            if (tried++ >= 8) {
                break;
            }
            int[] cell = (int[]) option[0];
            Boolean xFirst = (Boolean) option[1];
            BlockFace into = (BlockFace) option[2];
            List<Object[]> plan = LogisticsService.plan(start, cell, fallback, xFirst, into);
            if (plan == null) {
                continue;
            }
            String problem = runProblem(host, world, plan);
            if (problem == null) {
                aim.cell = cell;
                aim.xFirst = xFirst;
                aim.into = into;
                aim.plan = plan;
                aim.feeds = target;
                aim.problem = null;
                return true;
            }
            if (firstTry == null) {
                firstTry = new EndAim();
                firstTry.cell = cell;
                firstTry.xFirst = xFirst;
                firstTry.into = into;
                firstTry.plan = plan;
                firstTry.feeds = target;
                firstTry.problem = problem + " (on the way into your " + target.label() + ")";
            }
        }
        if (firstTry == null) {
            return false;
        }
        aim.cell = firstTry.cell;
        aim.xFirst = firstTry.xFirst;
        aim.into = firstTry.into;
        aim.plan = firstTry.plan;
        aim.feeds = firstTry.feeds;
        aim.problem = firstTry.problem;
        return true;
    }

    /** Cells along one side of a footprint, nearest the start first (up to three per side). */
    private static void addSide(List<Object[]> out, int fixed, boolean fixedIsX, int lo, int hi, int toward, int y,
                                boolean xFirst, BlockFace into) {
        List<Integer> along = new ArrayList<>();
        for (int v = lo; v <= hi; v++) {
            along.add(v);
        }
        along.sort(java.util.Comparator.comparingInt(v -> Math.abs(v - toward)));
        for (int i = 0; i < along.size() && i < 3; i++) {
            int v = along.get(i);
            int[] cell = fixedIsX ? new int[]{fixed, y, v} : new int[]{v, y, fixed};
            out.add(new Object[]{cell, xFirst, into});
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
            List<int[]> pads = new ArrayList<>();
            int px = player.getLocation().getBlockX();
            int pz = player.getLocation().getBlockZ();
            for (PlacedStructure structure : structures.of(session.host)) {
                if (Math.abs(structure.x() - px) > 32 || Math.abs(structure.z() - pz) > 32) {
                    continue;
                }
                int[] p = structures.outputPort(structure);
                if (p != null && ports.size() < 16) {
                    ports.add(p);
                }
                if (structure.type().acceptsInput() && !structure.type().router() && !structure.building()
                        && pads.size() < 24) {
                    int y = structure.y() + 1;
                    int midX = (structure.minX() + structure.maxX()) / 2;
                    int midZ = (structure.minZ() + structure.maxZ()) / 2;
                    int[][] sides = {{structure.minX() - 1, y, midZ}, {structure.maxX() + 1, y, midZ},
                            {midX, y, structure.minZ() - 1}, {midX, y, structure.maxZ() + 1}};
                    for (int[] side : sides) {
                        if (p != null && p[0] == side[0] && p[2] == side[2]) {
                            continue; // its own chute side stays orange
                        }
                        pads.add(side);
                    }
                }
            }
            session.ghost.chutes(world, ports);
            session.ghost.inputs(world, pads);
        }
        Block target = player.getTargetBlockExact(24);
        if (session.beltStart == null) {
            if (target == null) {
                session.ghost.clearRun();
                bar(player, session, "§6⚒ §eBelt §8· §7aim at an §6orange chute §7(or the quarry) · §fright-click §7start");
                return;
            }
            Belt looked = logistics.belt(world, target.getX(), target.getY(), target.getZ());
            int[] cell = startCell(player, session.host, world, target);
            boolean continuing = logistics.belt(world, cell[0], cell[1], cell[2]) != null;
            String problem = logistics.cellProblem(session.host, world, cell[0], cell[1], cell[2], continuing);
            session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
            if (looked != null) {
                bar(player, session, "§a✔ Extend this belt §8· §fright-click §7· §fleft-click §7cancel §8· §fQ §7away");
                return;
            }
            PlacedStructure source = ownerOfPort(session.host, cell);
            bar(player, session, problem == null
                    ? (source != null ? "§a✔ Start at your §f" + source.label() + "§a's chute" : "§a✔ Start here")
                    + " §8· §fright-click §7· belts §f" + logistics.count(session.host) + "§8/"
                    + logistics.cap(session.host) + " §8· §fQ §7away"
                    : "§c✖ " + problem);
            return;
        }
        if (target == null) {
            session.ghost.clearRun();
            bar(player, session, "§6⚒ §7Aim at a §fhut §7or machine §8(§agreen pads§8) §8· §fleft-click §7cancel start");
            return;
        }
        EndAim aim = endAim(player, session, world, target, StateRotator.facingOfYaw(player.getLocation().getYaw()));
        if (aim.plan.isEmpty()) {
            session.ghost.cursor(world, aim.cell[0], aim.cell[1], aim.cell[2], false);
            bar(player, session, "§c✖ " + aim.problem);
            return;
        }
        List<int[]> points = new ArrayList<>(aim.plan.size());
        int fresh = 0;
        for (Object[] step : aim.plan) {
            int[] p = (int[]) step[0];
            points.add(p);
            if (logistics.belt(world, p[0], p[1], p[2]) == null) {
                fresh++;
            }
        }
        int maxRun = maxRun();
        if (aim.plan.size() > maxRun) {
            session.ghost.run(world, points, false);
            bar(player, session, "§c✖ Too long (" + aim.plan.size() + "). Max " + maxRun + " — click a closer end first");
            return;
        }
        if (logistics.count(session.host) + fresh > logistics.cap(session.host)) {
            session.ghost.run(world, points, false);
            bar(player, session, "§c✖ Belt limit " + logistics.cap(session.host) + " — raise Island Tier");
            return;
        }
        int free = Math.max(0, Math.min(fresh, logistics.freeTiles(session.host)));
        List<BuildCosts.Line> bill = fresh - free <= 0 ? List.of()
                : List.of(new BuildCosts.Line(BuildCosts.COBBLE, (fresh - free) * BuildCosts.BELT_COBBLE));
        String missing = structures.missing(player, session.host, bill);
        session.ghost.run(world, points, aim.problem == null && missing == null);
        String cost = bill.isEmpty()
                ? (free > 0 ? "§a" + logistics.freeLabel(session.host) : "§afree")
                : "§e−" + GuildFormat.compact((fresh - free) * BuildCosts.BELT_COBBLE) + " Cobble"
                + (free > 0 ? " §8(§e" + free + " free§8)" : "");
        String where = aim.feeds != null ? " §8→ §f" + aim.feeds.label() : " §8· §eends in the open";
        bar(player, session, aim.problem != null ? "§c✖ " + aim.problem
                : missing != null ? "§e✖ " + aim.plan.size() + " tiles §8· §c" + missing + " §8· §7a quarry belt fills it"
                : "§a✔ " + aim.plan.size() + " tiles" + where + " §8· " + cost + " §8· §fright-click §7lay");
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
