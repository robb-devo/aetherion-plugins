package de.aetherion.guilds.structure;

import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.QuarryType;

import java.util.List;

/**
 * What things cost, Satisfactory-style: you <b>unlock</b> a part once at the Hub (goods + coins), then every
 * copy you <b>build</b> costs goods from your island's storage — what your quarries and machines made. No
 * crafting of in-between items by hand: machines make the goods, the goods build more machines.
 *
 * <p>Numbers are deliberately small early (a level-1 cobble quarry makes 6 a minute) and climb with what the
 * part does. Coins only show up on unlocks and Hub tiers, as a sink against the main economy.
 */
public final class BuildCosts {

    /** One line of a bill: some of a good, or coins when {@code res} is null. */
    public record Line(Res res, long amount) {
        public boolean coins() {
            return res == null;
        }

        public String label() {
            return res == null ? "coins" : res.display();
        }
    }

    /** An unlock: which Hub tier it needs and its one-time price. */
    public record Unlock(StructureType type, int hubLevel, List<Line> price) {
    }

    public static final Res COBBLE = Res.of(QuarryType.COBBLESTONE, Res.Form.RAW);
    public static final Res COMPRESSED = Res.of(QuarryType.COBBLESTONE, Res.Form.COMPRESSED);

    private BuildCosts() {
    }

    /** Goods per belt tile (from storage). */
    public static final long BELT_COBBLE = 1L;

    /** What building one costs (goods from the island's storage). The first Storage Hut is free. */
    public static List<Line> build(StructureType type, boolean firstHut) {
        return switch (type) {
            case STORAGE_HUT -> firstHut ? List.of() : List.of(new Line(COBBLE, 128L));
            case WORKSHOP -> List.of(new Line(COBBLE, 24L), new Line(null, 1_500L));
            case DEPOT -> List.of(new Line(COBBLE, 32L));
            case MILL -> List.of(new Line(COBBLE, 96L));
            case SPLITTER -> List.of(new Line(COBBLE, 32L));
            case FORGE -> List.of(new Line(COMPRESSED, 4L), new Line(COBBLE, 128L));
            case OVERFLOW, SORTER -> List.of(new Line(COMPRESSED, 2L), new Line(COBBLE, 64L));
            default -> List.of();
        };
    }

    /** Parts you unlock at the Hub, in the order the Hub shows them. Everything else is open from the start. */
    public static final List<Unlock> UNLOCKS = List.of(
            new Unlock(StructureType.DEPOT, 1, List.of(new Line(COBBLE, 48L))),
            new Unlock(StructureType.MILL, 1, List.of(new Line(COBBLE, 128L), new Line(null, 2_000L))),
            new Unlock(StructureType.SPLITTER, 1, List.of(new Line(COBBLE, 96L), new Line(null, 1_000L))),
            new Unlock(StructureType.FORGE, 2, List.of(new Line(COMPRESSED, 12L), new Line(null, 10_000L))),
            new Unlock(StructureType.OVERFLOW, 2, List.of(new Line(COMPRESSED, 4L), new Line(null, 4_000L))),
            new Unlock(StructureType.SORTER, 2, List.of(new Line(COMPRESSED, 6L), new Line(null, 6_000L))));

    public static Unlock unlock(StructureType type) {
        for (Unlock unlock : UNLOCKS) {
            if (unlock.type() == type) {
                return unlock;
            }
        }
        return null;
    }

    /** "96 Cobblestone, 2k coins" */
    public static String describe(List<Line> lines) {
        if (lines.isEmpty()) {
            return "free";
        }
        StringBuilder out = new StringBuilder();
        for (Line line : lines) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(de.aetherion.guilds.util.GuildFormat.compact(line.amount())).append(' ').append(line.label());
        }
        return out.toString();
    }
}
