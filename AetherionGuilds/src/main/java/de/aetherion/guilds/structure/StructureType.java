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
 * <p>Live templates are the compact {@code sm_*} ones (machines 3x3, hut/workshop 5x5). Structures placed before
 * that were saved without a template id and keep their original {@link #legacyTemplateId()}.
 */
public enum StructureType {

    STORAGE_HUT("storage_hut", "Storage Hut", Material.BARREL, "sm_storage_hut", Role.SINK,
            2_000L, null, 1, false, true, 5_000_000L, 0,
            List.of("§7Where your belts end. Holds",
                    "§7everything the quarries make,",
                    "§7raw or milled. Your first is free.")),
    WORKSHOP("workshop", "Workshop", Material.SMITHING_TABLE, "sm_workshop", Role.UTILITY,
            1_500L, null, 1, false, true, 0L, 0,
            List.of("§7The blueprint desk. Unlocks",
                    "§7belts, mills, forges and depots.",
                    "§7Use its lectern to build.")),
    DEPOT("depot", "Depot", Material.CHEST, "sm_depot", Role.SINK,
            500L, null, 2, true, true, 250_000L, 0,
            List.of("§7A small crate drop at the end",
                    "§7of any belt. Cheap, anywhere.")),
    MILL("mill", "Mill", Material.GRINDSTONE, "sm_mill", Role.PROCESSOR,
            3_000L, "quarry_compressor", 1, true, true, 200_000L, 4_096,
            List.of("§7Belt in, belt out.",
                    "§7Grinds §eRaw §7→ §aCompressed §8(128:1)§7.",
                    "§7Everything else passes through.")),
    FORGE("forge", "Forge", Material.BLAST_FURNACE, "sm_forge", Role.PROCESSOR,
            8_000L, "quarry_compactor", 1, true, true, 200_000L, 4_096,
            List.of("§7Belt in, belt out.",
                    "§7Presses §aCompressed §7→ §bCompacted§7,",
                    "§7and grinds §eRaw §7on the way.")),
    QUARRY_HOUSING("quarry_housing", "Quarry Housing", Material.IRON_PICKAXE, "sm_quarry_housing", Role.SOURCE,
            0L, null, 0, false, false, 0L, 0,
            List.of("§7Raised around every quarry.",
                    "§7Its chute feeds the belt in front.")),
    PROJECT("project", "Guild Project", Material.BELL, null, Role.DECOR,
            0L, null, 0, false, false, 0L, 0,
            List.of("§7A guild build, raised stage by stage."));

    public enum Role {
        SOURCE,
        PROCESSOR,
        SINK,
        UTILITY,
        DECOR
    }

    /** Slim drill rig over a quarry, used when a full housing has no room (belts start next to it). */
    public static final String QUARRY_RIG = "sm_quarry_rig";

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

    public boolean hasOutput() {
        return role == Role.PROCESSOR || role == Role.SOURCE;
    }

    public boolean acceptsInput() {
        return role == Role.SINK || role == Role.PROCESSOR;
    }

    public int maxCount(int tier) {
        if (!buildable) {
            return Integer.MAX_VALUE;
        }
        if (this == WORKSHOP) {
            return 1;
        }
        return baseMax + IslandTiers.structureBonus(tier);
    }

    public static StructureType byId(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (StructureType type : values()) {
            if (type.id.equals(key) || type.name().toLowerCase(Locale.ROOT).equals(key)) {
                return type;
            }
        }
        return null;
    }
}
