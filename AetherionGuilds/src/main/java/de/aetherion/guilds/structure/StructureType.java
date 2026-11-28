package de.aetherion.guilds.structure;

import de.aetherion.guilds.model.IslandTiers;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Curated island structures. Front = south in the template; the output chute is the front-most hopper at local
 * x 0, y 1 and the belt starts on the cell in front of it (see {@code StructureService.portLocal}). Sinks and
 * processors accept a belt that runs into their footprint from any side.
 *
 * <p>New buildings use the compact display props ({@code prop_v2_*}: machines, hut and quarries 2x2, Hub 4x2, Dock 3x2).
 * Every placed structure keeps the template it was saved with (older 3x3 / 5x5 props stay as they are);
 * structures saved without a template id keep their original {@link #legacyTemplateId()}.
 */
public enum StructureType {

    STORAGE_HUT("storage_hut", "Storage Hut", Material.BARREL, Props.HUT_V2, Role.SINK,
            2_000L, null, 1, false, true, 5_000_000L, 0,
            List.of("§7Where your belts end. Holds",
                    "§7everything the quarries make,",
                    "§7raw or milled. Your first is free.")),
    WORKSHOP("workshop", "Hub", Material.LECTERN, Props.HUB_V2, Role.UTILITY,
            1_500L, null, 1, false, true, 0L, 0,
            List.of("§7Your factory's heart. At its",
                    "§7lectern: unlocks, upgrades,",
                    "§7Coin Shortcuts.")),
    DEPOT("depot", "Depot", Material.CHEST, Props.DEPOT_V2, Role.SINK,
            500L, null, 2, true, true, 250_000L, 0,
            List.of("§7A crate yard. Ends any belt, and",
                    "§7passes its stock on if you lead",
                    "§7a belt away from its chute.")),
    MILL("mill", "Mill", Material.GRINDSTONE, Props.MILL_V2, Role.PROCESSOR,
            3_000L, "quarry_compressor", 1, true, true, 200_000L, 4_096,
            List.of("§7Belt in, belt out.",
                    "§7Grinds §eRaw §7→ §aCompressed §8(128:1)§7.",
                    "§7Everything else passes through.")),
    FORGE("forge", "Forge", Material.BLAST_FURNACE, Props.FORGE_V2, Role.PROCESSOR,
            8_000L, "quarry_compactor", 1, true, true, 200_000L, 4_096,
            List.of("§7Belt in, belt out.",
                    "§7Presses §aCompressed §7→ §bCompacted§7,",
                    "§7and grinds §eRaw §7on the way.")),
    QUARRY_HOUSING("quarry_housing", "Quarry Housing", Material.IRON_PICKAXE, Props.QUARRY_V2, Role.SOURCE,
            0L, null, 0, false, false, 0L, 0,
            List.of("§7Raised around every quarry.",
                    "§7Its chute feeds the belt in front.")),
    PROJECT("project", "Guild Project", Material.BELL, null, Role.DECOR,
            0L, null, 0, false, false, 0L, 0,
            List.of("§7A guild build, raised stage by stage.")),
    /*
     * Belt pieces: a 1-block copper box that sits on the belt line. Belts run into it from any side; every belt
     * that starts on a cell next to it and points away from it is one of its exits. "Front" = the way it faces
     * (the way the belt under it ran, or the way you looked when you set it down).
     */
    SPLITTER("splitter", "Splitter", Material.WAXED_COPPER_GRATE, null, Role.ROUTER,
            2_500L, null, 4, true, true, 262_144L, 0,
            List.of("§7Set it on a belt. Everything",
                    "§7that comes in is shared evenly",
                    "§7over every belt leading out.")),
    /**
     * Gone from the game (the Splitter is enough). The constant stays so nothing that still names it breaks;
     * a saved Overflow Gate loads as a Splitter ({@link #byId}), so none is ever placed or kept.
     */
    OVERFLOW("overflow_gate_retired", "Overflow Gate", Material.WAXED_WEATHERED_COPPER_GRATE, null, Role.ROUTER,
            4_000L, null, 0, true, false, 262_144L, 0,
            List.of("§7Retired: the Splitter does this job.")),
    SORTER("sorter", "Sorter", Material.WAXED_OXIDIZED_COPPER_GRATE, null, Role.ROUTER,
            6_000L, null, 4, true, true, 262_144L, 0,
            List.of("§7Pick one good. That good leaves",
                    "§7by the §ffront §7belt, everything",
                    "§7else by the §fsides§7.")),
    /*
     * The Dock (personal islands): where contracts are filled and the airship comes in. Belts end here; what a
     * belt brings goes straight into the open contracts (never into storage), so a full Dock backs its line up.
     */
    DOCK("dock", "Dock", Material.SPRUCE_BOAT, Props.DOCK_V2, Role.EXPORT,
            0L, null, 1, true, true, 0L, 0,
            List.of("§7Contracts come in here. Belt",
                    "§7goods in or load them from",
                    "§7storage; the airship collects."));

    public enum Role {
        SOURCE,
        PROCESSOR,
        SINK,
        UTILITY,
        DECOR,
        /** Splitter / Overflow Gate / Sorter: no recipe, only decides which exit goods take. */
        ROUTER,
        /** The Dock: belts end here and fill its contracts (goods leave the island by airship). */
        EXPORT
    }

    /** Slim drill rig over a quarry, used when a full housing has no room (belts start next to it). */
    public static final String QUARRY_RIG = Props.QUARRY_SLIM;

    private final String id;
    private final String display;
    private final Material icon;
    private final String templateId;
    private final Role role;
    private final long coins;
    private final String itemId;
    private final int baseMax;
    private final boolean needsWorkshop;
    private final boolean buildable;
    private final long capacity;
    private final int ratePerSecond;
    private final List<String> blurb;

    StructureType(String id, String display, Material icon, String templateId, Role role, long coins, String itemId,
                  int baseMax, boolean needsWorkshop, boolean buildable, long capacity, int ratePerSecond,
                  List<String> blurb) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.templateId = templateId;
        this.role = role;
        this.coins = coins;
        this.itemId = itemId;
        this.baseMax = baseMax;
        this.needsWorkshop = needsWorkshop;
        this.buildable = buildable;
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
        this.blurb = blurb;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public String templateId() {
        return templateId;
    }

    /** Template of a structure placed before the compact pass (those were saved without a template id). */
    public String legacyTemplateId() {
        return switch (this) {
            case STORAGE_HUT -> "st_storage_hut";
            case WORKSHOP -> "st_workshop";
            case DEPOT -> "st_depot";
            case MILL -> "st_mill";
            case FORGE -> "st_forge";
            case QUARRY_HOUSING -> "st_quarry_housing";
            default -> templateId;
        };
    }

    public Role role() {
        return role;
    }

    public long coins() {
        return coins;
    }

    /** Aetherion item consumed on build (Quarry Mill / Quarry Forge), or null. */
    public String itemId() {
        return itemId;
    }

    public boolean needsWorkshop() {
        return needsWorkshop;
    }

    public boolean buildable() {
        return buildable;
    }

    /** Sink storage / processor buffer, in raw-equivalent units. */
    public long capacity() {
        return capacity;
    }

    /** Processor speed in raw-equivalent units per second. */
    public int ratePerSecond() {
        return ratePerSecond;
    }

    public List<String> blurb() {
        return blurb;
    }

    /** Depots too: a belt led away from a depot's chute takes its stock on (a buffer in the line). */
    public boolean hasOutput() {
        return role == Role.PROCESSOR || role == Role.SOURCE || role == Role.ROUTER || this == DEPOT;
    }

    public boolean acceptsInput() {
        return role == Role.SINK || role == Role.PROCESSOR || role == Role.ROUTER || role == Role.EXPORT;
    }

    public boolean router() {
        return role == Role.ROUTER;
    }

    /** The real block a belt piece stands as (a copper grate you can see the goods through). */
    public Material routerBlock() {
        return router() ? icon : Material.AIR;
    }

    public int maxCount(int tier) {
        if (!buildable) {
            return Integer.MAX_VALUE;
        }
        if (this == WORKSHOP || this == DOCK) {
            return 1;
        }
        return baseMax + IslandTiers.structureBonus(tier);
    }

    public static StructureType byId(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.equals("overflow")) {
            return SPLITTER; // the Overflow Gate is gone: what stood as one shares evenly now
        }
        for (StructureType type : values()) {
            if (type.id.equals(key) || type.name().toLowerCase(Locale.ROOT).equals(key)) {
                return type;
            }
        }
        return null;
    }
}
