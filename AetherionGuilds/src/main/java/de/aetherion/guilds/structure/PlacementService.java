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
 *   <li><b>Pick up</b>: look at a belt, a building or a quarry (it turns red), right-click. Buildings and quarries
 *   ask for a second click; everything they cost comes back (a building keeps its tier for the next one).</li>
 *   <li>Right-click one of your buildings with a part picked: it opens, like without the tool.</li>
 *   <li><b>Quarry</b>: sets a quarry from your pack down; the hologram is the rig that rises round it.</li>
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
        DISMANTLE,
        /** A quarry from the player's pack, with its housing as the hologram. */
        QUARRY
    }

    /** Where the quarries come from (the items in a player's pack) and how one is set down. */
    public interface Quarries {
        /** The first quarry item in the player's pack, or null. */
        ItemStack find(Player player);

        /** How many quarries the player carries. */
        int count(Player player);

        /** Sets the quarry down on this ground block (it stands on top). True when it stands. */
        boolean place(Player player, Block ground, ItemStack item);

        /** Picks the quarry in this housing up into the player's inventory (keeps everything). */
        default boolean pickUp(Player player, PlacedStructure housing) {
            return false;
        }

        /** Which quarry this item is (its trade picks the hologram's yard), or null. */
        default de.aetherion.guilds.model.QuarryType type(ItemStack item) {
            return null;
        }

        /** The level this quarry item carries (its look follows it). */
        default int level(ItemStack item) {
            return 1;
        }
    }

    /** What the Build Tool can hold, in the order sneak+scroll walks them. */
    public enum Part {
        BELT(null, "Belt", Material.IRON_TRAPDOOR, "Carries goods from a quarry to a hut or machine."),
        QUARRY(null, "Quarry", Material.IRON_PICKAXE, "Sets a quarry from your pack down. It digs on its own."),
        STORAGE_HUT(StructureType.STORAGE_HUT, "Storage Hut", Material.BARREL, "Holds what your belts bring in."),
        HUB(StructureType.WORKSHOP, "Hub", Material.LECTERN, "Your factory's heart: unlocks and upgrades."),
        DEPOT(StructureType.DEPOT, "Depot", Material.CHEST, "More storage. Belts can end here or pass through."),
        MILL(StructureType.MILL, "Mill", Material.GRINDSTONE, "Grinds 128 raw into 1 Compressed."),
        SPLITTER(StructureType.SPLITTER, "Splitter", Material.WAXED_COPPER_GRATE, "Splits one belt into equal shares."),
        FORGE(StructureType.FORGE, "Forge", Material.BLAST_FURNACE, "Presses 128 Compressed into 1 Compacted."),
        OVERFLOW(StructureType.OVERFLOW, "Overflow Gate", Material.WAXED_WEATHERED_COPPER_GRATE, "Front first, sides take the spill."),
        SORTER(StructureType.SORTER, "Sorter", Material.WAXED_OXIDIZED_COPPER_GRATE, "One good out the front, the rest out the sides."),
        DOCK(StructureType.DOCK, "Dock", Material.SPRUCE_BOAT, "Contracts and the airship. Belts end here."),
        DISMANTLE(null, "Pick up", Material.CHEST, "Picks up belts, buildings and quarries. Everything it cost comes back.");

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
        /** A line from the island guide that the action bar shows instead of the tool's own (null: none). */
        String coach;
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
    private Quarries quarries;
    /** The turn a quarry is being set down with (read once by the housing as it rises). */
    private final Map<UUID, Integer> quarryRot = new HashMap<>();
    private java.util.function.BiConsumer<Player, IslandHost> picker = (player, host) -> {
    };
    /** Told whenever the player switches the tool's part themselves (sneak + scroll, the picker). */
    private java.util.function.BiConsumer<Player, Part> switched = (player, part) -> {
    };

    public PlacementService(JavaPlugin plugin, HostService hosts, StructureService structures,
                            LogisticsService logistics) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.toolKey = new NamespacedKey(plugin, "build_tool");
    }

    /** Where the Build Tool's quarries come from (the quarry service). */
    public void attachQuarries(Quarries quarries) {
        this.quarries = quarries;
    }

    /** How many quarries the player carries (0 without the quarry service). */
    public int quarriesIn(Player player) {
        return quarries == null || player == null ? 0 : quarries.count(player);
    }

    /** The turn the Build Tool set a quarry down with, else {@code fallback} (placed by hand). Read once. */
    public int takeQuarryRot(Player player, int fallback) {
        Integer rot = player == null ? null : quarryRot.remove(player.getUniqueId());
        return rot == null ? fallback : rot;
    }

    /** Who hears that a player switched parts by hand (the island guide's Build Tool lesson). */
    public void attachSwitched(java.util.function.BiConsumer<Player, Part> listener) {
        this.switched = listener == null ? (player, part) -> {
        } : listener;
    }

    /**
     * A line the action bar shows instead of the tool's own while the tool is in hand (the guide's "sneak + scroll
     * to the Quarry"); null puts the tool's own lines back. Lives as long as this tool session.
     */
    public void coach(Player player, String line) {
        Session session = player == null ? null : sessions.get(player.getUniqueId());
        if (session == null || java.util.Objects.equals(session.coach, line)) {
            return;
        }
        session.coach = line;
        if (line != null && holdingTool(player)) {
            actionBar(player, line);
        }
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
        if (!offered(host, part)) {
            return host.isGuild() ? "Docks are for your own island." : "Your Storage Huts do this job.";
        }
        if (type.needsWorkshop() && !structures.hasWorkshop(host)) {
            return "Build your Hub first.";
        }
        if (!structures.unlocked(host, type)) {
            de.aetherion.guilds.structure.BuildCosts.Unlock unlock = BuildCosts.unlock(host, type);
            if (unlock != null && !host.isGuild() && stageOf.applyAsInt(host) < unlock.stage()) {
                return "Unlock it at your Hub (opens on " + de.aetherion.guilds.island.Stages.label(unlock.stage()) + ").";
            }
            return unlock != null && host.isGuild() && structures.hubLevel(host) < unlock.hubLevel()
                    ? "Unlock it at your Hub (needs Hub tier " + unlock.hubLevel() + ")."
                    : "Unlock it at your Hub first.";
        }
        return null;
    }

    private java.util.function.ToIntFunction<IslandHost> stageOf = host -> 0;

    public void attachStage(java.util.function.ToIntFunction<IslandHost> stageOf) {
        this.stageOf = stageOf == null ? host -> 0 : stageOf;
    }

    /** Does this kind of island have the part at all (personal: no Depot; guild: no Dock)? */
    public static boolean offered(IslandHost host, Part part) {
        if (host == null) {
            return true;
        }
        return host.isGuild() ? part != Part.DOCK : part != Part.DEPOT;
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

    /**
     * What a new pick defaults to: the next thing a fresh island needs, else the last pick. Personal islands go
     * the way the stone flows, like the tour (quarry, Mill, hut); guild yards keep hut first.
     */
    private Part suggested(Player player, IslandHost host) {
        if (!structures.hasWorkshop(host)) {
            return Part.HUB; // the Hub comes first, everything else hangs off it
        }
        boolean noQuarry = structures.count(host, StructureType.QUARRY_HOUSING) == 0 && quarriesIn(player) > 0;
        if (host.isGuild()) {
            if (structures.count(host, StructureType.STORAGE_HUT) == 0) {
                return Part.STORAGE_HUT;
            }
            if (noQuarry) {
                return Part.QUARRY;
            }
        } else {
            if (noQuarry) {
                return Part.QUARRY;
            }
            if (structures.count(host, StructureType.MILL) == 0 && lockReason(host, Part.MILL) == null) {
                return Part.MILL;
            }
            if (structures.count(host, StructureType.STORAGE_HUT) == 0) {
                return Part.STORAGE_HUT;
            }
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
            long have = line.coins() ? hosts.funds(player, host) : structures.available(player, host, line.res());
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
                } else if (part == Part.QUARRY) {
                    int carried = quarriesIn(player);
                    lore.add("§7In your pack: " + (carried > 0 ? "§a" + carried : "§cnone §8(the Market at your Hub sells them)"));
                }
                lore.add("");
                switch (part) {
                    case BELT -> {
                        lore.add("§fRight-click §7orange chute, then the hut (snaps in)");
                        lore.add("§fLeft-click §7cancel start");
                        lore.add("§fQ §7put away · Pick up to remove");
                    }
                    case DISMANTLE -> lore.add("§fRight-click §7pick up what's red");
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
        StructureType type = part == Part.QUARRY ? StructureType.QUARRY_HOUSING : part.type();
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
            case QUARRY -> Kind.QUARRY;
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

    private java.util.function.BiFunction<Player, Part, String> boardHint = (player, part) -> null;

    /** The island tour's line for the board beside the hologram ("in front of the quarry's chute"), if any. */
    public void attachBoardHint(java.util.function.BiFunction<Player, Part, String> hint) {
        if (hint != null) {
            this.boardHint = hint;
        }
    }

    private static final de.aetherion.guilds.logistics.Res COBBLE =
            de.aetherion.guilds.logistics.Res.of(de.aetherion.guilds.model.QuarryType.COBBLESTONE,
                    de.aetherion.guilds.logistics.Res.Form.RAW);
    private static final de.aetherion.guilds.logistics.Res COMPRESSED =
            de.aetherion.guilds.logistics.Res.of(de.aetherion.guilds.model.QuarryType.COBBLESTONE,
                    de.aetherion.guilds.logistics.Res.Form.COMPRESSED);
    private static final de.aetherion.guilds.logistics.Res COMPACTED =
            de.aetherion.guilds.logistics.Res.of(de.aetherion.guilds.model.QuarryType.COBBLESTONE,
                    de.aetherion.guilds.logistics.Res.Form.COMPACTED);

    /**
     * What the board beside the hologram says for the part in hand: one line on what it does, then In and Out
     * with the real goods (green in, orange out, like the pads and chutes), the cost, and the tour's hint.
     */
    private PartBoard.Spec boardSpec(Player player, Session session, List<BuildCosts.Line> bill) {
        Part part = session.part;
        List<PartBoard.Row> rows = new ArrayList<>();
        String title = part.label();
        ItemStack belt = new ItemStack(Material.IRON_TRAPDOOR);
        switch (part) {
            case QUARRY -> {
                ItemStack next = quarries == null ? null : quarries.find(player);
                de.aetherion.guilds.model.QuarryType type = next == null ? null : quarries.type(next);
                int level = next == null ? 1 : quarries.level(next);
                de.aetherion.guilds.logistics.Res raw = type == null ? null
                        : de.aetherion.guilds.logistics.Res.of(type, de.aetherion.guilds.logistics.Res.Form.RAW);
                title = (type == null ? "Quarry" : GuildFormat.shortName(type) + " Quarry") + " §7Lv " + level;
                rows.add(PartBoard.Row.of("§7Digs on its own, day and night"));
                rows.add(new PartBoard.Row("§aIn  §fnothing, just flat ground", new ItemStack(Material.GRASS_BLOCK)));
                rows.add(new PartBoard.Row("§6Out §f" + (raw == null ? "raw goods" : raw.display()) + ", from its chute",
                        raw == null ? new ItemStack(Material.COBBLESTONE) : raw.icon()));
            }
            case MILL -> {
                rows.add(PartBoard.Row.of("§7Presses raw goods into blocks"));
                rows.add(new PartBoard.Row("§aIn  §f128 of one raw good", COBBLE.icon()));
                rows.add(new PartBoard.Row("§6Out §f1 Compressed block", COMPRESSED.icon()));
            }
            case FORGE -> {
                rows.add(PartBoard.Row.of("§7Presses Compressed once more"));
                rows.add(new PartBoard.Row("§aIn  §f128 Compressed blocks", COMPRESSED.icon()));
                rows.add(new PartBoard.Row("§6Out §f1 Compacted block", COMPACTED.icon()));
            }
            case STORAGE_HUT -> {
                rows.add(PartBoard.Row.of("§7Keeps what your belts bring"));
                rows.add(new PartBoard.Row("§aIn  §fany good, from any side", belt));
                rows.add(new PartBoard.Row("§6Out §fthe stock you build with", new ItemStack(Material.BARREL)));
            }
            case DEPOT -> {
                rows.add(PartBoard.Row.of("§7More storage on the line"));
                rows.add(new PartBoard.Row("§aIn  §fany good, from any side", belt));
                rows.add(new PartBoard.Row("§6Out §fbelts can run on through", new ItemStack(Material.CHEST)));
            }
            case HUB -> {
                rows.add(PartBoard.Row.of("§7Your island's heart"));
                rows.add(new PartBoard.Row("§aIn  §fgoods from your huts", new ItemStack(Material.BARREL)));
                rows.add(new PartBoard.Row("§6Out §fnew parts and upgrades", new ItemStack(Material.LECTERN)));
            }
            case DOCK -> {
                rows.add(PartBoard.Row.of("§7Ships goods off the island"));
                rows.add(new PartBoard.Row("§aIn  §fwhat a contract asks for", COMPRESSED.icon()));
                rows.add(new PartBoard.Row("§6Out §fcoins, Dock Marks, stages", new ItemStack(Material.GOLD_NUGGET)));
            }
            case SPLITTER -> {
                rows.add(PartBoard.Row.of("§7Shares one belt out evenly"));
                rows.add(new PartBoard.Row("§aIn  §fone belt, from behind", belt));
                rows.add(new PartBoard.Row("§6Out §fan equal share per exit", new ItemStack(part.icon())));
            }
            case OVERFLOW -> {
                rows.add(PartBoard.Row.of("§7Keeps the front fed first"));
                rows.add(new PartBoard.Row("§aIn  §fone belt, from behind", belt));
                rows.add(new PartBoard.Row("§6Out §ffront first, sides spill", new ItemStack(part.icon())));
            }
            case SORTER -> {
                rows.add(PartBoard.Row.of("§7Picks one good off a belt"));
                rows.add(new PartBoard.Row("§aIn  §fone belt, from behind", belt));
                rows.add(new PartBoard.Row("§6Out §fyour pick out the front", new ItemStack(part.icon())));
            }
            default -> {
                return null;
            }
        }
        if (session.kind == Kind.QUARRY) {
            rows.add(PartBoard.Row.of("§7From your pack §f" + quarriesIn(player) + " §7left"));
        } else {
            rows.add(PartBoard.Row.of("§7Cost " + (bill.isEmpty() ? "§ffree" : "§f" + BuildCosts.describe(bill))));
        }
        String hint = boardHint.apply(player, part);
        if (hint != null && !hint.isBlank()) {
            rows.add(PartBoard.Row.of("§e➜ §f" + hint));
        }
        return new PartBoard.Spec(title, rows);
    }

    /**
     * The board stands beside the hologram (never in it): on the player's right, a step toward them, turned to
     * face them. {@code (x0, z0)-(x1, z1)} is the footprint in block bounds, {@code floorY} what it stands on.
     */
    private void showBoard(Player player, Session session, World world, double floorY, double x0, double z0,
                           double x1, double z1, List<BuildCosts.Line> bill) {
        placeBoard(player, session, world, floorY, x0, z0, x1, z1, boardSpec(player, session, bill));
    }

    /** The board for laying belts: where a run starts and ends, what it costs, the tour's hint. */
    private PartBoard.Spec beltSpec(Player player, Session session, boolean started) {
        List<PartBoard.Row> rows = new ArrayList<>();
        rows.add(PartBoard.Row.of("§7Carries goods from chute to pad"));
        rows.add(new PartBoard.Row((started ? "§a✔ §7Start: " : "§6Start §f") + "an orange chute",
                new ItemStack(Material.ORANGE_CONCRETE)));
        rows.add(new PartBoard.Row((started ? "§aEnd §f" : "§7End §7") + "a green pad, machine or hut",
                new ItemStack(Material.LIME_CONCRETE)));
        int free = logistics.freeTiles(session.host);
        rows.add(PartBoard.Row.of("§7Cost §f" + (free > 0 ? free + " tiles free"
                : GuildFormat.compact(BuildCosts.BELT_COBBLE) + " Cobble a tile")));
        String hint = boardHint.apply(player, Part.BELT);
        if (hint != null && !hint.isBlank()) {
            rows.add(PartBoard.Row.of("§e➜ §f" + hint));
        }
        return new PartBoard.Spec("Belt", rows);
    }

    private void placeBoard(Player player, Session session, World world, double floorY, double x0, double z0,
                            double x1, double z1, PartBoard.Spec spec) {
        if (spec == null) {
            session.ghost.clearBoard();
            return;
        }
        org.bukkit.Location eye = player.getEyeLocation();
        double cx = (x0 + x1) / 2.0;
        double cz = (z0 + z1) / 2.0;
        double fx = eye.getX() - cx;
        double fz = eye.getZ() - cz;
        double len = Math.hypot(fx, fz);
        if (len < 0.01) {
            fx = 0.0;
            fz = 1.0;
            len = 1.0;
        }
        fx /= len;
        fz /= len;
        // the viewer's right, seen from where they stand (they look along -f)
        double rx = fz;
        double rz = -fx;
        double extent = 0.5 * (Math.abs(rx) * (x1 - x0) + Math.abs(rz) * (z1 - z0));
        double out = extent + 0.3 + session.ghost.boardHalfWidth(spec, len);
        double bx = cx + rx * out + fx * 0.6;
        double bz = cz + rz * out + fz * 0.6;
        double tx = eye.getX() - bx;
        double tz = eye.getZ() - bz;
        float yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
        session.ghost.board(world, bx, floorY, bz, yaw, Math.hypot(tx, tz), spec);
    }

    /**
     * Hands the player the Build Tool holding {@code wanted} (null: the next thing this island needs). Used by
     * the picker, /island → Build, the Hub, Hollis and every "build X" button.
     */
    public void openTool(Player player, IslandHost host, Part wanted) {
        openTool(player, host, wanted, false);
    }

    /** The picker and the guide's lesson: the part goes into the hand, and the player's own pick is reported. */
    public void pick(Player player, IslandHost host, Part part) {
        openTool(player, host, part);
        if (part != null && part(player) == part) {
            switched.accept(player, part);
        }
    }

    /** Quiet: into the hand without the title and the part's how-to line (the guide is talking already). */
    public void openToolQuiet(Player player, IslandHost host, Part wanted) {
        openTool(player, host, wanted, true);
    }

    private void openTool(Player player, IslandHost host, Part wanted, boolean quiet) {
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
        if (quiet) {
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.6f, 1.4f);
            return;
        }
        announce(player, session, fresh);
    }

    /** True while the island tour runs for this player: the tool then shows its part, but writes nothing to chat. */
    private java.util.function.Predicate<Player> calm = player -> false;

    public void attachCalm(java.util.function.Predicate<Player> calm) {
        if (calm != null) {
            this.calm = calm;
        }
    }

    private void announce(Player player, Session session, boolean fresh) {
        Part part = session.part;
        player.sendTitle("", "§6⚒ §e" + part.label(), 0, 22, 8);
        if (calm.test(player)) {
            return; // the tour's card and the board beside the hologram say how; no how-to line in chat
        }
        if (fresh) {
            player.sendMessage("§6⚒ Build Tool §7in your hand. §fSneak + scroll §7picks a part, §fF §7shows all,"
                    + " §fQ §7puts it away.");
        }
        player.sendMessage(switch (part) {
            case BELT -> "§7Belt: §fright-click §7the §6orange chute§7, then §fright-click §7the hut or machine §8(§agreen pads§8)§7 — it snaps in."
                    + " Ending in the open keeps chaining. §fLeft-click §7cancels the start.";
            case DISMANTLE -> "§7Pick up: look at a belt, building or quarry, it turns §cred§7, then §fright-click§7.";
            case QUARRY -> "§7Quarry: the hologram is its rig. Look at flat ground, §fright-click §7sets the quarry from your"
                    + " pack down, §fleft-click §7turns its chute.";
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
        switched.accept(player, next);
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
        return session != null && (session.kind == Kind.STRUCTURE || session.kind == Kind.PIECE
                || session.kind == Kind.QUARRY);
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
        int[] anchor = anchorFor(player, session.template, ground, rot);
        if (session.kind == Kind.QUARRY) {
            placeQuarry(player, session, ground.getWorld().getBlockAt(anchor[0], ground.getY(), anchor[1]), rot, now);
            return;
        }
        if (session.kind == Kind.STRUCTURE) {
            PlacedStructure placed = structures.place(player, session.host, session.type, anchor[0], ground.getY(),
                    anchor[1], rot, logistics::isBelt);
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

    /** The Build Tool's quarry: one from the pack goes down where the hologram stands, its rig turned the same way. */
    private void placeQuarry(Player player, Session session, Block ground, int rot, long now) {
        ItemStack item = quarries == null ? null : quarries.find(player);
        if (item == null) {
            player.sendMessage("§cNo quarry in your pack. §7The Market at your Hub sells them.");
            return;
        }
        StructureService.Validation validation = structures.validate(session.host, session.template, ground.getWorld(),
                ground.getX(), ground.getY(), ground.getZ(), rot, logistics::isBelt);
        if (!validation.ok()) {
            player.sendMessage("§c" + validation.reason() + ".");
            return;
        }
        quarryRot.put(player.getUniqueId(), rot);
        boolean placed;
        try {
            placed = quarries.place(player, ground, item);
        } finally {
            quarryRot.remove(player.getUniqueId());
        }
        if (!placed) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
        session.lastBuild = now;
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        if (quarriesIn(player) > 0) {
            refreshTool(player, session);
        } else if (apply(player, session, Part.BELT)) {
            // the quarry stands: what it needs next is a belt from its orange chute
            refreshTool(player, session);
            announce(player, session, false);
        }
    }

    /**
     * Where a building's anchor goes for the block the player looks at: on the compact buildings (2x2, 4x2, 3x2)
     * the footprint is centred under the crosshair instead of hanging off the looked-at block.
     */
    private static int[] anchorFor(Player player, Template template, Block ground, int rot) {
        if (template == null || !Props.compact(template.id())) {
            return new int[]{ground.getX(), ground.getZ()};
        }
        double cx = (template.minX() + template.maxX()) / 2.0;
        double cz = (template.minZ() + template.maxZ()) / 2.0;
        if (cx == 0.0 && cz == 0.0) {
            return new int[]{ground.getX(), ground.getZ()};
        }
        for (int i = 0; i < Math.floorMod(rot, 4); i++) {
            double nx = -cz;
            cz = cx;
            cx = nx;
        }
        double px = ground.getX() + 0.5;
        double pz = ground.getZ() + 0.5;
        org.bukkit.util.RayTraceResult hit = player.rayTraceBlocks(24.0);
        if (hit != null && hit.getHitPosition() != null) {
            px = Math.max(ground.getX(), Math.min(ground.getX() + 1, hit.getHitPosition().getX()));
            pz = Math.max(ground.getZ(), Math.min(ground.getZ() + 1, hit.getHitPosition().getZ()));
        }
        return new int[]{(int) Math.floor(px - cx), (int) Math.floor(pz - cz)};
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

    /** Why this can't be picked up with the tool (null when it can). What it holds never stops it. */
    private static String keepReason(PlacedStructure structure) {
        if (structure.building()) {
            return "Still going up";
        }
        return switch (structure.type()) {
            case QUARRY_HOUSING -> null; // picked up whole: level, mill and storage go with it
            case PROJECT -> "Guild projects stay";
            default -> null; // its goods go to the other huts, or come back with the next one
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
                actionBar(player, "§7Belt picked up §8(" + logistics.lastRemoveLine() + ")");
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
            actionBar(player, "§c§lRight-click again §7to pick up your §f" + structure.label()
                    + " §8(" + (structure.type() == StructureType.QUARRY_HOUSING ? "it goes into your inventory"
                    : "all of its cost comes back") + ")");
            session.quietUntil = now + 1500L;
            return;
        }
        session.armed = null;
        boolean done = structure.type() == StructureType.QUARRY_HOUSING
                ? quarries != null && quarries.pickUp(player, structure)
                : structures.deconstruct(player, structure);
        if (done) {
            session.ghost.clearMark();
            refreshTool(player, session);
        }
    }

    private void renderDismantle(Player player, Session session) {
        Block target = player.getTargetBlockExact(24);
        if (target == null) {
            session.ghost.clearMark();
            bar(player, session, "§7Look at a belt, building or quarry to pick it up");
            return;
        }
        World world = target.getWorld();
        Belt belt = beltAt(world, target);
        if (belt != null) {
            session.ghost.mark(world, belt.x(), belt.y(), belt.z(), belt.x(), belt.y(), belt.z());
            bar(player, session, "§c✖ Belt §8· §fright-click §7picks it up §8(refunded)");
            return;
        }
        PlacedStructure structure = buildingAt(session.host, target);
        if (structure == null) {
            session.ghost.clearMark();
            bar(player, session, "§7Nothing of yours to pick up here");
            return;
        }
        int y1 = Math.max(structure.minY(), structure.y() + 1);
        session.ghost.mark(world, structure.minX(), y1, structure.minZ(), structure.maxX(),
                Math.max(y1, structure.maxY()), structure.maxZ());
        String keep = keepReason(structure);
        boolean armed = structure.id().equals(session.armed) && System.currentTimeMillis() <= session.armedUntil;
        bar(player, session, keep != null ? "§e" + structure.label() + " §8· §7" + keep
                : armed ? "§c§lRight-click again §7to pick up your §f" + structure.label()
                : "§c✖ " + structure.label() + " §8· §fright-click §7pick up §8(" + (structure.type() == StructureType.QUARRY_HOUSING
                ? "into your inventory" : "all of its cost comes back") + ")");
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
        // that is Pick up / /island done. One cancel rule, taught once.
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
            if (structure != null && structure.type().router()) {
                int[] side = freeSideToward(structure, player.getLocation().getX(), player.getLocation().getZ());
                if (side != null) {
                    return side;
                }
            }
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

    /** The free side of a belt piece nearest (px, pz) where a new exit belt can start, or null. */
    private int[] freeSideToward(PlacedStructure piece, double px, double pz) {
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] side : logistics.freeSides(piece)) {
            double dx = side[0] + 0.5 - px;
            double dz = side[2] + 0.5 - pz;
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = new int[]{side[0], side[1], side[2]};
            }
        }
        return best;
    }

    /** The belt piece this cell sits right beside (the cell is one of its sides), or null. */
    private PlacedStructure pieceBeside(IslandHost host, World world, int[] cell) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            PlacedStructure next = structures.atColumn(world, cell[0] - face.getModX(), cell[2] - face.getModZ());
            if (next != null && next.type().router() && next.host().equals(host) && next.y() + 1 == cell[1]) {
                return next;
            }
        }
        return null;
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
        if (from == null && logistics.belt(world, start[0], start[1], start[2]) == null) {
            from = pieceBeside(host, world, start); // a new exit of a belt piece: never loop back into it
        }
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
            if (logistics.liveExits(placed) < 2 && apply(player, session, Part.BELT)) {
                // a piece only does something once a second belt leaves it: the tool turns to Belt and its free
                // sides glow orange, so the next click is the right one
                refreshTool(player, session);
                player.sendTitle("", "§6⚒ §eBelt §8· §7from an §6orange side §7of the " + placed.type().display(), 0, 40, 10);
                actionBar(player, "§6⚒ §eBelt §8· §fright-click §7an §6orange side §7of your " + placed.type().display()
                        + "§7, then the hut or machine");
                session.quietUntil = System.currentTimeMillis() + 3500L;
                player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8f, 1.4f);
                return;
            }
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
            session.ghost.clearBoard();
            bar(player, session, "§6⚒ §7Look at a belt to cut a " + session.label + " into it");
            return;
        }
        int[] cell = pieceCell(world, target);
        String problem = logistics.pieceProblem(session.host, world, cell[0], cell[1], cell[2]);
        session.ghost.cursor(world, cell[0], cell[1], cell[2], problem == null);
        List<BuildCosts.Line> bill = structures.buildCost(session.host, session.type);
        showBoard(player, session, world, cell[1], cell[0], cell[2], cell[0] + 1, cell[2] + 1, bill);
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
        if (looked != null && (session.kind == Kind.STRUCTURE || session.kind == Kind.QUARRY)) {
            PlacedStructure mine = structures.owner(looked);
            if (mine != null && mine.host().equals(session.host)) {
                session.ghost.clearStructure();
                session.ghost.clearBoard();
                bar(player, session, "§7Your §f" + mine.label() + " §8· §fright-click §7opens it");
                return;
            }
        }
        Block ground = groundOf(player);
        if (ground == null) {
            session.ghost.clearStructure();
            session.ghost.clearBoard();
            bar(player, session, "§6⚒ §e" + session.label + " §8· §7look at the ground §8· §fsneak + scroll §7other part");
            return;
        }
        int rot = session.autoRot ? StateRotator.frontTowardViewer(player.getLocation().getYaw()) : session.rot;
        session.rot = rot;
        World world = ground.getWorld();
        int[] anchor = anchorFor(player, session.template, ground, rot);
        int ax = anchor[0];
        int ay = ground.getY();
        int az = anchor[1];
        StructureService.Validation validation = structures.validate(session.host, session.template, world, ax, ay, az,
                rot, logistics::isBelt);
        IslandHost here = hosts.at(ground.getLocation());
        boolean onIsland = here != null && here.equals(session.host);
        String blocked = session.kind == Kind.STRUCTURE ? structures.blockedReason(player, session.host, session.type)
                : session.kind == Kind.QUARRY && quarriesIn(player) == 0 ? "No quarry in your pack (the Market at your Hub)"
                : null;
        List<BuildCosts.Line> bill = session.kind == Kind.STRUCTURE ? structures.buildCost(session.host, session.type)
                : List.of();
        String missing = blocked == null ? structures.missing(player, session.host, bill) : null;
        boolean fits = validation.ok() && onIsland;
        boolean ok = fits && blocked == null && missing == null;
        int[] port = session.type != null && session.type.hasOutput() ? StructureService.portLocal(session.template) : null;
        if (session.kind == Kind.QUARRY && quarries != null) {
            ItemStack next = quarries.find(player);
            session.ghost.quarry(next == null ? null : quarries.type(next), next == null ? 1 : quarries.level(next));
        }
        session.ghost.structure(session.template, world, ax, ay, az, rot, ok, port);
        int[] fp = session.template.rotatedFootprint(rot);
        showBoard(player, session, world, ay + 1, ax + fp[0], az + fp[1], ax + fp[2] + 1, az + fp[3] + 1, bill);
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
        } else if (session.kind == Kind.QUARRY) {
            text = "§a✔ Quarry §8(§f" + quarriesIn(player) + " §8in your pack) · §fright-click §7set it down §8· §fleft-click §7turn";
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
                if (structure.type().router() && !structure.building()) {
                    // a belt piece: every free side is where a new exit can start
                    for (int[] side : logistics.freeSides(structure)) {
                        if (ports.size() < 20) {
                            ports.add(new int[]{side[0], side[1], side[2]});
                        }
                    }
                }
                if (structure.type().acceptsInput() && !structure.type().router() && !structure.building()
                        && pads.size() < 24) {
                    int y = structure.y() + 1;
                    BlockFace chuteSide = logistics.chuteSide(structure);
                    for (int[] side : LogisticsService.inputSides(structure)) {
                        if (chuteSide != null && BlockFace.values()[side[2]].getOppositeFace() == chuteSide) {
                            continue; // its own chute side stays orange: nothing green right beside the way out
                        }
                        pads.add(new int[]{side[0], y, side[1]});
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
            PlacedStructure piece = source == null ? pieceBeside(session.host, world, cell) : null;
            placeBoard(player, session, world, cell[1], cell[0], cell[2], cell[0] + 1, cell[2] + 1,
                    beltSpec(player, session, false));
            bar(player, session, problem == null
                    ? (source != null ? "§a✔ Start at your §f" + source.label() + "§a's chute"
                    : piece != null ? "§a✔ New exit of your §f" + piece.label() : "§a✔ Start here")
                    + " §8· §fright-click §7· belts §f" + logistics.count(session.host) + "§8/"
                    + logistics.cap(session.host) + " §8· §fQ §7away"
                    : "§c✖ " + problem);
            return;
        }
        int[] start = session.beltStart; // the board waits beside the start while the run is aimed
        placeBoard(player, session, world, start[1], start[0], start[2], start[0] + 1, start[2] + 1,
                beltSpec(player, session, true));
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
            actionBar(player, session.coach != null ? session.coach : text);
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
