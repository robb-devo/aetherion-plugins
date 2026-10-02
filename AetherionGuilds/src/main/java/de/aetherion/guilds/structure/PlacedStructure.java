package de.aetherion.guilds.structure;

import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.Res;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One placed structure. Anchor = ground-layer centre (template y 0), rot = clockwise quarter turns. */
public final class PlacedStructure {

    private final UUID id;
    private final StructureType type;
    private final IslandHost host;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int rot;
    private final long placedAt;
    private boolean frameless;
    private UUID minionId;
    private String templateId;
    private String projectId;
    private boolean building;
    private long lastWork;
    /** Sorter: what goes out the front ("type:COAL", "form:COMPRESSED"), null = nothing picked yet. */
    private String filter;
    /** Splitter: whose turn the next odd unit is (keeps small trickles fair). */
    private int turn;
    /** Upgrade tier (1 = as built). See {@link StructureUpgrades}. */
    private int level = 1;
    private final Map<Res, Long> store = new LinkedHashMap<>();
    private final Map<Res, Long> input = new LinkedHashMap<>();
    private final Map<Res, Long> output = new LinkedHashMap<>();

    int minX;
    int minY;
    int minZ;
    int maxX;
    int maxY;
    int maxZ;

    public PlacedStructure(UUID id, StructureType type, IslandHost host, String world, int x, int y, int z, int rot,
                           long placedAt) {
        this.id = id;
        this.type = type;
        this.host = host;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rot = Math.floorMod(rot, 4);
        this.placedAt = placedAt;
        this.templateId = type.templateId();
    }

    public UUID id() {
        return id;
    }

    public StructureType type() {
        return type;
    }

    public IslandHost host() {
        return host;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int rot() {
        return rot;
    }

    public long placedAt() {
        return placedAt;
    }

    public boolean frameless() {
        return frameless;
    }

    public void setFrameless(boolean frameless) {
        this.frameless = frameless;
    }

    public UUID minionId() {
        return minionId;
    }

    public void setMinionId(UUID minionId) {
        this.minionId = minionId;
    }

    public String templateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String projectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public boolean building() {
        return building;
    }

    public void setBuilding(boolean building) {
        this.building = building;
    }

    public long lastWork() {
        return lastWork;
    }

    public void setLastWork(long lastWork) {
        this.lastWork = lastWork;
    }

    public String filter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter == null || filter.isBlank() ? null : filter;
    }

    public int turn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    /** Does this sorter's filter pick that resource? */
    public boolean filterMatches(Res res) {
        if (filter == null || res == null) {
            return false;
        }
        if (filter.startsWith("type:")) {
            return res.type().name().equals(filter.substring(5));
        }
        if (filter.startsWith("form:")) {
            return res.form().name().equals(filter.substring(5));
        }
        return res.key().equals(filter);
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(StructureUpgrades.maxLevel(type), level));
    }

    /** Storage / buffer at this tier (raw-equivalent). */
    public long capacity() {
        return StructureUpgrades.capacity(type, level);
    }

    /** Processing speed at this tier (raw-equivalent per second). */
    public int ratePerSecond() {
        return StructureUpgrades.ratePerSecond(type, level);
    }

    public Map<Res, Long> store() {
        return store;
    }

    public Map<Res, Long> input() {
        return input;
    }

    public Map<Res, Long> output() {
        return output;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean contains(int bx, int by, int bz) {
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    public boolean containsColumn(int bx, int bz) {
        return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }

    public static long total(Map<Res, Long> map) {
        long sum = 0L;
        for (Map.Entry<Res, Long> entry : map.entrySet()) {
            sum += entry.getKey().rawEquivalent(entry.getValue());
        }
        return sum;
    }

    public static void add(Map<Res, Long> map, Res res, long amount) {
        if (amount <= 0L) {
            return;
        }
        map.merge(res, amount, Long::sum);
    }

    public static long take(Map<Res, Long> map, Res res, long amount) {
        Long have = map.get(res);
        if (have == null || have <= 0L || amount <= 0L) {
            return 0L;
        }
        long taken = Math.min(have, amount);
        if (have - taken <= 0L) {
            map.remove(res);
        } else {
            map.put(res, have - taken);
        }
        return taken;
    }

    public String label() {
        if (type == StructureType.PROJECT && projectId != null) {
            return "Guild Project";
        }
        return StructureUpgrades.tierName(type, level);
    }
}
