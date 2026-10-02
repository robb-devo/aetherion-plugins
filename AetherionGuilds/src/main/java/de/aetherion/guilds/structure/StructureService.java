package de.aetherion.guilds.structure;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.StateRotator;
import de.aetherion.guilds.template.Template;
import de.aetherion.guilds.template.TemplateLibrary;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Placed structures per island: persistence ({@code island_structures.yml}), the spatial index used by
 * protection and belts, placement validation, paste, deconstruct and quarry-housing migration.
 *
 * <p>Protection is exact: a block is protected only while it still is the block the structure's template
 * put there. Anything a player adds inside the box, or anything a non-destructive housing paste skipped,
 * stays theirs.
 */
public final class StructureService {

    public record Validation(boolean ok, String reason, List<int[]> blocked) {
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final TemplateLibrary templates;
    private final PasteService paste;
    private final File file;
    private final Map<String, Map<UUID, PlacedStructure>> byHost = new HashMap<>();
    private final Map<UUID, PlacedStructure> byId = new HashMap<>();
    private final Map<UUID, PlacedStructure> byMinion = new HashMap<>();
    private final Map<String, Map<Long, List<PlacedStructure>>> spatial = new HashMap<>();
    private Consumer<IslandHost> changeListener = host -> {
    };
    private Consumer<QuarryMinion> housingListener = minion -> {
    };
    private BeltProbe beltProbe;
    private java.util.function.BiConsumer<Player, PlacedStructure> hubFounded = (player, hub) -> {
    };

    /** The first Workshop on an island just stood up: the Hub is founded (ritual, guide, board). */
    public void onHubFounded(java.util.function.BiConsumer<Player, PlacedStructure> listener) {
        this.hubFounded = listener == null ? (player, hub) -> {
        } : listener;
    }
    private boolean dirty;

    public StructureService(JavaPlugin plugin, HostService hosts, TemplateLibrary templates, PasteService paste) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.templates = templates;
        this.paste = paste;
        this.file = new File(plugin.getDataFolder(), "island_structures.yml");
    }

    public void onChange(Consumer<IslandHost> listener) {
        this.changeListener = listener == null ? host -> {
        } : listener;
    }

    /** Called when a quarry's housing changes (the quarry's look follows: housed quarries drop the tool). */
    public void onHousing(Consumer<QuarryMinion> listener) {
        this.housingListener = listener == null ? minion -> {
        } : listener;
    }

    /** Lets housing placement see belts (to face an existing belt). */
    public void attachBelts(BeltProbe probe) {
        this.beltProbe = probe;
    }

    public TemplateLibrary templates() {
        return templates;
    }

    public PasteService paster() {
        return paste;
    }

    // ------------------------------------------------------------------------------------------------
    // lookups
    // ------------------------------------------------------------------------------------------------

    public Collection<PlacedStructure> of(IslandHost host) {
        if (host == null) {
            return List.of();
        }
        Map<UUID, PlacedStructure> map = byHost.get(host.key());
        return map == null ? List.of() : List.copyOf(map.values());
    }

    public PlacedStructure get(UUID id) {
        return id == null ? null : byId.get(id);
    }

    public int count(IslandHost host, StructureType type) {
        int n = 0;
        for (PlacedStructure structure : of(host)) {
            if (structure.type() == type) {
                n++;
            }
        }
        return n;
    }

    public boolean hasWorkshop(IslandHost host) {
        return count(host, StructureType.WORKSHOP) > 0;
    }

    // ------------------------------------------------------------------------------------------------
    // the Hub (the Workshop): after it stands, the factory is run from its lectern
    // ------------------------------------------------------------------------------------------------

    public static final int HUB_REACH = 16;

    public PlacedStructure hub(IslandHost host) {
        for (PlacedStructure structure : of(host)) {
            if (structure.type() == StructureType.WORKSHOP) {
                return structure;
            }
        }
        return null;
    }

    /** 0 = no Hub yet, 1 Workshop, 2 Foreman's Hall, 3 Grand Hub. */
    public int hubLevel(IslandHost host) {
        PlacedStructure hub = hub(host);
        return hub == null ? 0 : hub.level();
    }

    /** Is the player at the Hub (within {@value #HUB_REACH} blocks of the Workshop)? True while there is none. */
    public boolean atHub(Player player, IslandHost host) {
        if (player == null || hosts.isAdminBypass(player)) {
            return true;
        }
        PlacedStructure hub = hub(host);
        if (hub == null) {
            return true;
        }
        Location at = player.getLocation();
        if (at.getWorld() == null || !at.getWorld().getName().equals(hub.world())) {
            return false;
        }
        double dx = at.getX() - (hub.x() + 0.5);
        double dz = at.getZ() - (hub.z() + 0.5);
        return dx * dx + dz * dz <= HUB_REACH * HUB_REACH;
    }

    private final Map<UUID, Long> deskAt = new HashMap<>();

    /** The player just opened the Hub lectern: the factory desk is theirs while they stay at the Hub. */
    public void markDesk(Player player) {
        if (player != null) {
            deskAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Is the player working at the Hub desk? Before the Hub stands: yes (the starter pieces are built from
     * /island). After: only when they opened the Workshop lectern in the last five minutes and are still at
     * the Hub. /island never counts as the desk.
     */
    public boolean atDesk(Player player, IslandHost host) {
        if (player == null || hosts.isAdminBypass(player) || hub(host) == null) {
            return true;
        }
        Long opened = deskAt.get(player.getUniqueId());
        return opened != null && System.currentTimeMillis() - opened < 300_000L && atHub(player, host);
    }

    /** Where to stand at the Hub: in front of the Workshop. */
    public Location hubSpot(IslandHost host) {
        PlacedStructure hub = hub(host);
        World world = hub == null ? null : Bukkit.getWorld(hub.world());
        if (world == null) {
            return null;
        }
        int[] front = StateRotator.rotateXZ(0, hub.maxZ() - hub.z() + 2, hub.rot());
        int[] f = StateRotator.rotateXZ(0, 1, hub.rot());
        int x = hub.x() + front[0];
        int z = hub.z() + front[1];
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location spot = new Location(world, x + 0.5, Math.max(y, hub.y() + 1), z + 0.5);
        spot.setDirection(new org.bukkit.util.Vector(-f[0], 0, -f[1]));
        return spot;
    }

    /** A machine's limit: island tier, plus two of each at the Grand Hub. */
    public int limit(IslandHost host, StructureType type) {
        int base = type.maxCount(hosts.tier(host));
        return base == Integer.MAX_VALUE || type == StructureType.WORKSHOP ? base : base + (hubLevel(host) >= 3 ? 2 : 0);
    }

    public PlacedStructure housingOf(IslandHost host, UUID minionId) {
        if (minionId == null) {
            return null;
        }
        for (PlacedStructure structure : of(host)) {
            if (structure.type() == StructureType.QUARRY_HOUSING && minionId.equals(structure.minionId())) {
                return structure;
            }
        }
        return null;
    }

    public PlacedStructure housingOfMinion(UUID minionId) {
        return minionId == null ? null : byMinion.get(minionId);
    }

    public PlacedStructure at(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        Map<Long, List<PlacedStructure>> chunks = spatial.get(world.getName());
        if (chunks == null) {
            return null;
        }
        List<PlacedStructure> list = chunks.get(chunkKey(x >> 4, z >> 4));
        if (list == null) {
            return null;
        }
        for (PlacedStructure structure : list) {
            if (structure.contains(x, y, z)) {
                return structure;
            }
        }
        return null;
    }

    public PlacedStructure atColumn(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        Map<Long, List<PlacedStructure>> chunks = spatial.get(world.getName());
        if (chunks == null) {
            return null;
        }
        List<PlacedStructure> list = chunks.get(chunkKey(x >> 4, z >> 4));
        if (list == null) {
            return null;
        }
        for (PlacedStructure structure : list) {
            if (structure.containsColumn(x, z)) {
                return structure;
            }
        }
        return null;
    }

    public Template template(PlacedStructure structure) {
        return structure == null || structure.frameless() ? null : templates.get(structure.templateId());
    }

    /** Is this block still the block some structure's template placed there? (Boxes may overlap.) */
    public boolean isStructureBlock(Block block) {
        return owner(block) != null;
    }

    /** The structure that placed this block, or null. */
    public PlacedStructure owner(Block block) {
        Map<Long, List<PlacedStructure>> chunks = spatial.get(block.getWorld().getName());
        if (chunks == null) {
            return null;
        }
        List<PlacedStructure> list = chunks.get(chunkKey(block.getX() >> 4, block.getZ() >> 4));
        if (list == null) {
            return null;
        }
        for (PlacedStructure structure : list) {
            if (structure.frameless() || !structure.contains(block.getX(), block.getY(), block.getZ())) {
                continue;
            }
            if (structure.type().router()) {
                if (block.getX() == structure.x() && block.getY() == structure.y() + 1 && block.getZ() == structure.z()
                        && block.getType() == structure.type().routerBlock()) {
                    return structure;
                }
                continue;
            }
            if (structure.building()) {
                return structure;
            }
            Material expected = templateMaterial(structure, block.getX(), block.getY(), block.getZ());
            if (expected != Material.AIR && block.getType() == expected) {
                return structure;
            }
        }
        return null;
    }

    public Material templateMaterial(PlacedStructure structure, int wx, int wy, int wz) {
        Template template = template(structure);
        if (template == null) {
            return Material.AIR;
        }
        int[] local = StateRotator.unrotateXZ(wx - structure.x(), wz - structure.z(), structure.rot());
        return template.materialAtLocal(local[0], wy - structure.y(), local[1]);
    }

    /** Local port (belt start) of a template: the cell in front of its chute, or null if it has no chute. */
    public static int[] portLocal(Template template) {
        int[] chute = template == null ? null : template.chuteLocal();
        return chute == null ? null : new int[]{chute[0], chute[1], chute[2] + 1};
    }

    /**
     * World position of the output port (belt start). Null for non-producers and for producers without a chute
     * (frameless quarries, quarry rigs): their belt may start on any cell next to them.
     */
    public int[] outputPort(PlacedStructure structure) {
        if (structure == null || !structure.type().hasOutput() || structure.frameless()) {
            return null;
        }
        int[] port = portLocal(template(structure));
        if (port == null) {
            return null;
        }
        int[] r = StateRotator.rotateXZ(port[0], port[2], structure.rot());
        return new int[]{structure.x() + r[0], structure.y() + port[1], structure.z() + r[1]};
    }

    /** World position of the chute block itself (inside the footprint), or null. */
    public int[] chuteCell(PlacedStructure structure) {
        if (structure == null || !structure.type().hasOutput() || structure.frameless()) {
            return null;
        }
        Template template = template(structure);
        int[] chute = template == null ? null : template.chuteLocal();
        if (chute == null) {
            return null;
        }
        int[] r = StateRotator.rotateXZ(chute[0], chute[2], structure.rot());
        return new int[]{structure.x() + r[0], structure.y() + chute[1], structure.z() + r[1]};
    }

    /** Where cargo leaves a producer (for the visuals): its chute, or the quarry itself. */
    public Location chute(PlacedStructure structure) {
        World world = Bukkit.getWorld(structure.world());
        if (world == null) {
            return null;
        }
        int[] cell = chuteCell(structure);
        if (cell == null) {
            return new Location(world, structure.x() + 0.5, structure.y() + 1.2, structure.z() + 0.5);
        }
        return new Location(world, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5);
    }

    /** A quarry with only the slim rig (or nothing) over it; a full housing can still be raised. */
    public boolean slimHousing(PlacedStructure housing) {
        return housing == null || housing.frameless() || StructureType.QUARRY_RIG.equals(housing.templateId());
    }

    /** Would a belt that moves into (x,y,z) feed this structure? */
    public boolean acceptsAt(PlacedStructure structure, int x, int y, int z) {
        if (!structure.type().acceptsInput() || structure.building()) {
            return false;
        }
        return structure.containsColumn(x, z) && y >= structure.y() && y <= structure.y() + 3;
    }

    // ------------------------------------------------------------------------------------------------
    // registration + index
    // ------------------------------------------------------------------------------------------------

    private void register(PlacedStructure structure) {
        computeBounds(structure);
        byHost.computeIfAbsent(structure.host().key(), k -> new LinkedHashMap<>()).put(structure.id(), structure);
        byId.put(structure.id(), structure);
        if (structure.minionId() != null) {
            byMinion.put(structure.minionId(), structure);
        }
        Map<Long, List<PlacedStructure>> chunks = spatial.computeIfAbsent(structure.world(), k -> new HashMap<>());
        for (int cx = structure.minX >> 4; cx <= structure.maxX >> 4; cx++) {
            for (int cz = structure.minZ >> 4; cz <= structure.maxZ >> 4; cz++) {
                chunks.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(structure);
            }
        }
        dirty = true;
    }

    private void unregister(PlacedStructure structure) {
        Map<UUID, PlacedStructure> map = byHost.get(structure.host().key());
        if (map != null) {
            map.remove(structure.id());
        }
        byId.remove(structure.id());
        if (structure.minionId() != null && byMinion.get(structure.minionId()) == structure) {
            byMinion.remove(structure.minionId());
        }
        Map<Long, List<PlacedStructure>> chunks = spatial.get(structure.world());
        if (chunks != null) {
            for (List<PlacedStructure> list : chunks.values()) {
                list.remove(structure);
            }
        }
        dirty = true;
    }

    /** Re-index after a template swap (project stage) so protection and bounds follow the new shape. */
    public void reindex(PlacedStructure structure) {
        unregister(structure);
        register(structure);
    }

    private void computeBounds(PlacedStructure structure) {
        Template template = template(structure);
        if (template == null) {
            structure.minX = structure.maxX = structure.x();
            structure.minZ = structure.maxZ = structure.z();
            structure.minY = structure.y();
            structure.maxY = structure.y() + 2;
            return;
        }
        int[] fp = template.rotatedFootprint(structure.rot());
        structure.minX = structure.x() + fp[0];
        structure.minZ = structure.z() + fp[1];
        structure.maxX = structure.x() + fp[2];
        structure.maxZ = structure.z() + fp[3];
        structure.minY = structure.y() + template.minY();
        structure.maxY = structure.y() + template.maxY();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------------------------------------
    // validation
    // ------------------------------------------------------------------------------------------------

    public Validation validate(IslandHost host, Template template, World world, int ax, int ay, int az, int rot,
                               BeltProbe belts) {
        List<int[]> blocked = new ArrayList<>();
        if (template == null) {
            return new Validation(false, "Template missing", blocked);
        }
        if (host == null || world == null) {
            return new Validation(false, "Not on your island", blocked);
        }
        String reason = null;
        // 1) the whole footprint (incl. overhangs) must be your land and free of other structures
        int[] fp = template.rotatedFootprint(rot);
        for (int rx = fp[0]; rx <= fp[2]; rx++) {
            for (int rz = fp[1]; rz <= fp[3]; rz++) {
                int wx = ax + rx;
                int wz = az + rz;
                if (!hosts.inBuildZone(host, wx, wz)) {
                    reason = "Part of it is outside your land";
                    blocked.add(new int[]{wx, ay + 1, wz});
                    continue;
                }
                PlacedStructure other = atColumn(world, wx, wz);
                if (other != null && !(other.type() == StructureType.QUARRY_HOUSING && other.frameless())) {
                    reason = "Overlaps your " + other.label();
                    blocked.add(new int[]{wx, ay + 1, wz});
                }
            }
        }
        // 2) every block the template places above ground needs free space; its floor needs ground
        int floor = 0;
        int solid = 0;
        for (int[] cell : template.solidCells()) {
            int[] r = StateRotator.rotateXZ(cell[0], cell[2], rot);
            int wx = ax + r[0];
            int wy = ay + cell[1];
            int wz = az + r[1];
            if (cell[1] <= 0) {
                if (cell[1] == 0) {
                    floor++;
                    Block ground = world.getBlockAt(wx, wy, wz);
                    if (ground.getType().isSolid()) {
                        solid++;
                    } else if (ground.isLiquid()) {
                        reason = "Can't build on water";
                        blocked.add(new int[]{wx, wy, wz});
                    }
                }
                continue;
            }
            if (belts != null && belts.isBelt(world, wx, wy, wz)) {
                reason = "A belt is in the way";
                blocked.add(new int[]{wx, wy, wz});
                continue;
            }
            if (!PasteService.replaceable(world.getBlockAt(wx, wy, wz))) {
                if (reason == null) {
                    reason = "Blocks are in the way";
                }
                blocked.add(new int[]{wx, wy, wz});
            }
        }
        if (reason == null && floor > 0 && solid < floor * 0.6) {
            reason = "Needs flat, solid ground under it";
        }
        if (reason == null && Props.isProp(template.id())) {
            // props have no floor of their own: the ground under their footprint carries them
            int ground = 0;
            int columns = 0;
            for (int rx = fp[0]; rx <= fp[2]; rx++) {
                for (int rz = fp[1]; rz <= fp[3]; rz++) {
                    columns++;
                    Block below = world.getBlockAt(ax + rx, ay, az + rz);
                    if (below.getType().isSolid()) {
                        ground++;
                    } else if (below.isLiquid()) {
                        reason = "Can't build on water";
                        blocked.add(new int[]{ax + rx, ay, az + rz});
                    }
                }
            }
            if (reason == null && ground < columns * 0.75) {
                reason = "Needs flat, solid ground under it";
            }
        }
        return new Validation(reason == null, reason == null ? "Valid pad" : reason, blocked);
    }

    /** Small probe so validation can see belts without depending on the logistics class. */
    public interface BeltProbe {
        boolean isBelt(World world, int x, int y, int z);
    }

    // ------------------------------------------------------------------------------------------------
    // placing
    // ------------------------------------------------------------------------------------------------

    public long priceFor(IslandHost host, StructureType type) {
        if (type == StructureType.STORAGE_HUT && count(host, StructureType.STORAGE_HUT) == 0) {
            return 0L;
        }
        return type.coins();
    }

    /** Checks limits, workshop and item requirements. Returns null when fine, else the reason. */
    public String blockedReason(Player player, IslandHost host, StructureType type) {
        if (!type.buildable()) {
            return "This isn't built from the Build menu.";
        }
        if (!hosts.canPlace(player, host)) {
            return host.isGuild() ? "Soldiers and above can build structures." : "Only the island owner can build here.";
        }
        int tier = hosts.tier(host);
        if (count(host, type) >= limit(host, type)) {
            return "Limit reached (" + limit(host, type) + " at Island Tier " + tier + ").";
        }
        if (type.needsWorkshop() && !hasWorkshop(host)) {
            return "Found your Hub first (build the Workshop).";
        }
        if ((type == StructureType.FORGE || type == StructureType.OVERFLOW || type == StructureType.SORTER)
                && hubLevel(host) < 2) {
            return "Needs the Foreman's Hall (upgrade your Hub).";
        }
        if ((type == StructureType.OVERFLOW || type == StructureType.SORTER) && count(host, StructureType.SPLITTER) == 0) {
            return "Unlocks after your first Splitter.";
        }
        if (type.itemId() != null && AetherionItemsAccess.available()
                && AetherionItemsAccess.count(player, type.itemId()) < 1) {
            return "Needs 1 " + itemName(type.itemId()) + " in your inventory.";
        }
        return null;
    }

    public static String itemName(String itemId) {
        return switch (itemId) {
            case "quarry_compressor" -> "Quarry Mill";
            case "quarry_compactor" -> "Quarry Forge";
            default -> itemId;
        };
    }

    public PlacedStructure place(Player player, IslandHost host, StructureType type, int ax, int ay, int az, int rot,
                                 BeltProbe belts) {
        String blocked = blockedReason(player, host, type);
        if (blocked != null) {
            player.sendMessage("§c" + blocked);
            return null;
        }
        World world = hosts.world(host);
        Template template = templates.get(type.templateId());
        Validation validation = validate(host, template, world, ax, ay, az, rot, belts);
        if (!validation.ok()) {
            player.sendMessage("§c" + validation.reason() + ".");
            return null;
        }
        long price = priceFor(host, type);
        if (!hosts.charge(player, host, price)) {
            return null;
        }
        if (type.itemId() != null && AetherionItemsAccess.available()) {
            AetherionItemsAccess.take(player, type.itemId(), 1);
        }
        PlacedStructure structure = new PlacedStructure(UUID.randomUUID(), type, host, world.getName(), ax, ay, az, rot,
                System.currentTimeMillis());
        structure.setBuilding(true);
        register(structure);
        changeListener.accept(host);
        world.playSound(new Location(world, ax + 0.5, ay + 1, az + 0.5), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        paste.paste(template, world, ax, ay, az, rot, PasteService.Mode.SKIP_AIR, !Props.isProp(template.id()), job -> {
            structure.setBuilding(false);
            dirty = true;
            changeListener.accept(host);
            Location center = new Location(world, ax + 0.5, ay + 2, az + 0.5);
            world.spawnParticle(Particle.CLOUD, center, 24, 1.6, 0.8, 1.6, 0.02);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 14, 1.2, 0.9, 1.2, 0.03);
            world.playSound(center, Sound.BLOCK_SMITHING_TABLE_USE, 0.9f, 1.1f);
            world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
            if (type == StructureType.WORKSHOP && count(host, StructureType.WORKSHOP) == 1) {
                hubFounded.accept(player, structure);
                return;
            }
            if (player.isOnline()) {
                player.sendTitle("§6✦ " + type.display() + " §6✦", "§7built on " + hosts.title(host), 5, 40, 15);
                player.sendMessage("§a" + type.display() + " built" + (price > 0 ? " §8(§6" + GuildFormat.compact(price)
                        + " " + hosts.fundsLabel(host) + "§8)" : " §8(free)") + "§a. " + nextHint(type));
            }
        });
        return structure;
    }

    private static String nextHint(StructureType type) {
        return switch (type) {
            case STORAGE_HUT -> "§7Run a belt into any wall and it fills itself.";
            case WORKSHOP -> "§7Your Hub stands. Its lectern is the factory desk now.";
            case MILL, FORGE -> "§7Belt in on any side, belt out from the chute at the front.";
            case DEPOT -> "§7End any belt here.";
            case SPLITTER, OVERFLOW, SORTER -> "§7Belts leading away from it are its exits.";
            default -> "";
        };
    }

    /**
     * Record a quarry's housing. {@code templateId} null = frameless (no blocks, belts start next to it). With
     * paste, the housing is raised non-destructively (only into empty space) around the quarry.
     */
    public PlacedStructure registerHousing(IslandHost host, QuarryMinion minion, int rot, String templateId,
                                           boolean pasteNow) {
        World world = hosts.world(host);
        if (world == null || minion == null) {
            return null;
        }
        PlacedStructure existing = housingOf(host, minion.id());
        if (existing != null) {
            unregister(existing);
        }
        PlacedStructure housing = new PlacedStructure(existing != null ? existing.id() : UUID.randomUUID(),
                StructureType.QUARRY_HOUSING, host, world.getName(), minion.x(), minion.y() - 1, minion.z(), rot,
                existing != null ? existing.placedAt() : System.currentTimeMillis());
        housing.setMinionId(minion.id());
        housing.setFrameless(templateId == null);
        if (templateId != null) {
            housing.setTemplateId(templateId);
        }
        register(housing);
        changeListener.accept(host);
        if (templateId != null && pasteNow) {
            Template template = templates.get(templateId);
            paste.paste(template, world, housing.x(), housing.y(), housing.z(), rot, PasteService.Mode.ONLY_REPLACEABLE,
                    true, job -> {
                        Location top = new Location(world, housing.x() + 0.5, housing.y() + 4.2, housing.z() + 0.5);
                        world.spawnParticle(Particle.CLOUD, top, 10, 0.8, 0.4, 0.8, 0.02);
                        world.playSound(top, Sound.BLOCK_PISTON_EXTEND, 0.6f, 0.8f);
                        world.playSound(top, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.8f);
                    });
        }
        housingListener.accept(minion);
        return housing;
    }

    public enum HousingResult {
        FULL,
        RIG,
        NONE
    }

    /**
     * Raise real housing round a quarry: the 3x3 housing when it fits (all on your land, no other structure in
     * the way, room for most of it), else the slim drill rig over the quarry, else it stays frameless. Only ever
     * fills empty space. {@code preferredRot} &lt; 0 = pick a side (an existing belt first, then toward the island).
     */
    public HousingResult raiseHousing(IslandHost host, QuarryMinion minion, int preferredRot) {
        World world = hosts.world(host);
        if (world == null || minion == null || !world.isChunkLoaded(minion.x() >> 4, minion.z() >> 4)) {
            return HousingResult.NONE;
        }
        Template full = templates.get(StructureType.QUARRY_HOUSING.templateId());
        Template rig = templates.get(StructureType.QUARRY_RIG);
        PlacedStructure before = housingOf(host, minion.id());
        boolean wasRig = before != null && !before.frameless() && StructureType.QUARRY_RIG.equals(before.templateId());
        int rot = pickHousingRot(host, world, minion, preferredRot);
        if (full != null && housingFits(host, world, full, minion, rot)) {
            if (wasRig && rig != null) {
                clearRigLeftovers(world, rig, full, minion, rot);
            }
            registerHousing(host, minion, rot, full.id(), true);
            return HousingResult.FULL;
        }
        if (wasRig) {
            return HousingResult.RIG;
        }
        if (rig != null && roomFor(world, rig, minion.x(), minion.y() - 1, minion.z(), 0) >= 1.0) {
            registerHousing(host, minion, 0, rig.id(), true);
            return HousingResult.RIG;
        }
        if (housingOf(host, minion.id()) == null) {
            registerHousing(host, minion, 0, null, false);
        }
        return HousingResult.NONE;
    }

    /**
     * Quarries that are still frameless (from before housings existed) get real housing. Returns {raised,
     * waiting}: waiting = quarries whose chunk isn't loaded yet (try again later).
     */
    public int[] upgradeFrameless(IslandHost host) {
        int raised = 0;
        int waiting = 0;
        World world = hosts.world(host);
        for (PlacedStructure structure : of(host)) {
            if (structure.type() != StructureType.QUARRY_HOUSING || !structure.frameless()) {
                continue;
            }
            QuarryMinion minion = hosts.minion(host, structure.minionId());
            if (minion == null || world == null) {
                continue;
            }
            if (!world.isChunkLoaded(minion.x() >> 4, minion.z() >> 4)) {
                waiting++;
                continue;
            }
            if (raiseHousing(host, minion, -1) != HousingResult.NONE) {
                raised++;
            }
        }
        return new int[]{raised, waiting};
    }

    private int pickHousingRot(IslandHost host, World world, QuarryMinion minion, int preferred) {
        BeltProbe belts = beltProbe;
        if (belts != null) {
            for (int dist = 2; dist >= 1; dist--) {
                for (int k = 0; k < 4; k++) {
                    int[] r = StateRotator.rotateXZ(0, dist, k);
                    if (belts.isBelt(world, minion.x() + r[0], minion.y(), minion.z() + r[1])) {
                        return k;
                    }
                }
            }
        }
        if (preferred >= 0) {
            return preferred & 3;
        }
        int dx = hosts.originX(host) - minion.x();
        int dz = hosts.originZ(host) - minion.z();
        int best = 0;
        int bestScore = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            int[] front = StateRotator.rotateXZ(0, 1, k);
            int[] port = StateRotator.rotateXZ(0, 2, k);
            Block cell = world.getBlockAt(minion.x() + port[0], minion.y(), minion.z() + port[1]);
            boolean open = PasteService.replaceable(cell) && cell.getRelative(0, -1, 0).getType().isSolid();
            int score = (open ? 1000 : 0) + front[0] * dx + front[1] * dz;
            if (score > bestScore) {
                bestScore = score;
                best = k;
            }
        }
        return best;
    }

    private boolean housingFits(IslandHost host, World world, Template template, QuarryMinion minion, int rot) {
        int[] fp = template.rotatedFootprint(rot);
        for (int x = minion.x() + fp[0]; x <= minion.x() + fp[2]; x++) {
            for (int z = minion.z() + fp[1]; z <= minion.z() + fp[3]; z++) {
                if (!hosts.inBuildZone(host, x, z)) {
                    return false;
                }
                PlacedStructure other = atColumn(world, x, z);
                if (other != null && !minion.id().equals(other.minionId())
                        && !(other.type() == StructureType.QUARRY_HOUSING && other.frameless())) {
                    return false;
                }
            }
        }
        return roomFor(world, template, minion.x(), minion.y() - 1, minion.z(), rot) >= 0.7;
    }

    /** Rig blocks the full housing doesn't have in the same spot (its cap) go when the housing replaces it. */
    private static void clearRigLeftovers(World world, Template rig, Template full, QuarryMinion minion, int rot) {
        int ay = minion.y() - 1;
        for (int[] cell : rig.solidCells()) {
            int[] local = StateRotator.unrotateXZ(cell[0], cell[2], rot);
            if (full.materialAtLocal(local[0], cell[1], local[1]) == rig.materialAtLocal(cell[0], cell[1], cell[2])) {
                continue;
            }
            Block block = world.getBlockAt(minion.x() + cell[0], ay + cell[1], minion.z() + cell[2]);
            if (block.getType() == rig.materialAtLocal(cell[0], cell[1], cell[2])) {
                block.setType(Material.AIR, false);
            }
        }
    }

    /** Share of the template's above-ground blocks that would find empty space. */
    private static double roomFor(World world, Template template, int ax, int ay, int az, int rot) {
        if (template.nonAirBlocks() == 0) {
            return 1.0; // a prop with no shell (the slim quarry rig) always fits
        }
        int total = 0;
        int free = 0;
        for (int[] cell : template.solidCells()) {
            if (cell[1] < 1) {
                continue;
            }
            total++;
            int[] r = StateRotator.rotateXZ(cell[0], cell[2], rot);
            if (PasteService.replaceable(world.getBlockAt(ax + r[0], ay + cell[1], az + r[1]))) {
                free++;
            }
        }
        return total == 0 ? 0.0 : free / (double) total;
    }

    /**
     * A belt piece (Splitter / Overflow Gate / Sorter): anchored on the ground block, the copper box stands on the
     * belt cell above it. Validation and paying happen in the logistics service (it knows the belts).
     */
    public PlacedStructure registerRouter(IslandHost host, StructureType type, int x, int groundY, int z, int rot) {
        World world = hosts.world(host);
        if (world == null || !type.router()) {
            return null;
        }
        PlacedStructure structure = new PlacedStructure(UUID.randomUUID(), type, host, world.getName(), x, groundY, z, rot,
                System.currentTimeMillis());
        structure.setTemplateId(null);
        register(structure);
        world.getBlockAt(x, groundY + 1, z).setType(type.routerBlock(), false);
        changeListener.accept(host);
        return structure;
    }

    /** A guild project site / building. Protection follows its current stage template. */
    public PlacedStructure registerProject(IslandHost host, String projectId, String templateId, int ax, int ay, int az,
                                           int rot) {
        World world = hosts.world(host);
        if (world == null) {
            return null;
        }
        PlacedStructure structure = new PlacedStructure(UUID.randomUUID(), StructureType.PROJECT, host, world.getName(),
                ax, ay, az, rot, System.currentTimeMillis());
        structure.setProjectId(projectId);
        structure.setTemplateId(templateId);
        register(structure);
        changeListener.accept(host);
        return structure;
    }

    /** Does any structure (other than frameless housings) stand in this rotated footprint? */
    public PlacedStructure overlapping(World world, Template template, int ax, int az, int rot) {
        if (template == null) {
            return null;
        }
        int[] fp = template.rotatedFootprint(rot);
        for (int x = ax + fp[0]; x <= ax + fp[2]; x++) {
            for (int z = az + fp[1]; z <= az + fp[3]; z++) {
                PlacedStructure other = atColumn(world, x, z);
                if (other != null && !(other.type() == StructureType.QUARRY_HOUSING && other.frameless())) {
                    return other;
                }
            }
        }
        return null;
    }

    public void removeHousing(IslandHost host, UUID minionId, boolean clearBlocks) {
        PlacedStructure housing = housingOf(host, minionId);
        if (housing == null) {
            return;
        }
        remove(housing, clearBlocks);
    }

    public void remove(PlacedStructure structure, boolean clearBlocks) {
        if (structure == null) {
            return;
        }
        Template template = template(structure);
        unregister(structure);
        changeListener.accept(structure.host());
        if (clearBlocks && structure.type().router()) {
            World world = Bukkit.getWorld(structure.world());
            if (world != null) {
                Block block = world.getBlockAt(structure.x(), structure.y() + 1, structure.z());
                if (block.getType() == structure.type().routerBlock()) {
                    block.setType(Material.AIR, false);
                }
            }
            return;
        }
        if (clearBlocks && template != null) {
            World world = Bukkit.getWorld(structure.world());
            paste.clear(template, world, structure.x(), structure.y(), structure.z(), structure.rot(), null);
        }
    }

    public boolean deconstruct(Player player, PlacedStructure structure) {
        if (structure == null) {
            return false;
        }
        IslandHost host = structure.host();
        if (!hosts.canPlace(player, host)) {
            player.sendMessage(hosts.rankHint(host, "take structures down"));
            return false;
        }
        switch (structure.type()) {
            case QUARRY_HOUSING -> {
                player.sendMessage("§7Pick the quarry up instead; the housing comes down with it.");
                return false;
            }
            case PROJECT -> {
                player.sendMessage("§7Guild projects stay. They are your guild's history now.");
                return false;
            }
            default -> {
            }
        }
        if (PlacedStructure.total(structure.store()) > 0L || PlacedStructure.total(structure.input()) > 0L
                || PlacedStructure.total(structure.output()) > 0L) {
            player.sendMessage("§cEmpty it first (take everything out), so nothing is lost.");
            return false;
        }
        if (structure.type() == StructureType.WORKSHOP) {
            for (PlacedStructure other : of(host)) {
                if (other.type().needsWorkshop()) {
                    player.sendMessage("§cMills, forges, depots and belt pieces need the Workshop. Take those down first.");
                    return false;
                }
            }
        }
        long refund = priceFor(host, structure.type()) / 2L;
        if (structure.type() == StructureType.STORAGE_HUT && count(host, StructureType.STORAGE_HUT) <= 1) {
            refund = 0L;
        }
        hosts.refund(player, host, refund);
        if (structure.type().itemId() != null && AetherionItemsAccess.available()) {
            de.aetherion.core.api.ItemFactoryAccess items = de.aetherion.core.api.AetherServices.items();
            ItemStack item = items == null ? null : items.create(structure.type().itemId());
            if (item != null) {
                player.getInventory().addItem(item).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
        remove(structure, true);
        player.sendMessage("§7" + structure.type().display() + " taken down"
                + (refund > 0 ? " §8(§6+" + GuildFormat.compact(refund) + "§8)" : "") + "§7.");
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.8f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // upgrades (StructureUpgrades): same footprint, the building grows up
    // ------------------------------------------------------------------------------------------------

    /** One cost of the next tier with what the player (+ the island's storage) has toward it. */
    public record CostLine(StructureUpgrades.Cost cost, long have) {
        public boolean enough() {
            return have >= cost.amount();
        }
    }

    public StructureUpgrades.Tier nextTier(PlacedStructure structure) {
        return structure == null ? null : StructureUpgrades.next(structure.type(), structure.level());
    }

    public List<CostLine> costLines(Player player, PlacedStructure structure, StructureUpgrades.Tier tier) {
        List<CostLine> out = new ArrayList<>();
        if (tier == null) {
            return out;
        }
        IslandHost host = structure.host();
        for (StructureUpgrades.Cost cost : tier.costs()) {
            if (cost.coins()) {
                out.add(new CostLine(cost, hosts.funds(player, host)));
                continue;
            }
            String id = cost.itemId();
            if (id == null || !AetherionItemsAccess.available()) {
                continue; // without AetherionItems nobody can hold these: free
            }
            out.add(new CostLine(cost, AetherionItemsAccess.count(player, id) + inStorage(host, cost.key())));
        }
        return out;
    }

    /** Units of an {@code item:} key lying in the island's Storage Huts and Depots. */
    public long inStorage(IslandHost host, String key) {
        long have = 0L;
        for (PlacedStructure sink : of(host)) {
            if (sink.type().role() != StructureType.Role.SINK) {
                continue;
            }
            for (Map.Entry<Res, Long> entry : sink.store().entrySet()) {
                if (entry.getKey().requirementKey().equals(key)) {
                    have += entry.getValue();
                }
            }
        }
        return have;
    }

    /** Coin price of one unit of an upgrade material when bought instead of made (Coin Shortcuts). */
    public long unitPrice(String itemId) {
        long fallback = switch (itemId) {
            case "compressed_cobblestone" -> 187_500L;
            case "compacted_cobblestone" -> 40_000_000L;
            case "quarry_core" -> 15_000_000L;
            default -> 1_000_000L;
        };
        return Math.max(1L, plugin.getConfig().getLong("market.unit." + itemId, fallback));
    }

    /** Coins it takes to buy every material the next tier still lacks (0 = nothing missing). */
    public long missingCoins(Player player, PlacedStructure structure) {
        StructureUpgrades.Tier tier = nextTier(structure);
        long sum = 0L;
        for (CostLine line : costLines(player, structure, tier)) {
            if (!line.cost().coins() && !line.enough()) {
                sum += (line.cost().amount() - line.have()) * unitPrice(line.cost().itemId());
            }
        }
        return sum;
    }

    /** Why this structure can't be upgraded right now, or null. */
    public String upgradeBlocked(Player player, PlacedStructure structure) {
        return upgradeBlocked(player, structure, false);
    }

    /**
     * With {@code buyMissing}, materials you lack are paid in coins at Coin Shortcut prices (brutal on purpose)
     * instead of blocking.
     */
    public String upgradeBlocked(Player player, PlacedStructure structure, boolean buyMissing) {
        StructureUpgrades.Tier tier = nextTier(structure);
        if (tier == null) {
            return "Fully upgraded.";
        }
        if (structure.building()) {
            return "Still building…";
        }
        IslandHost host = structure.host();
        if (!hosts.canPlace(player, host)) {
            return host.isGuild() ? "Soldiers and above can upgrade buildings." : "Only the island owner can upgrade.";
        }
        if (!atDesk(player, host)) {
            return "Upgrades are done at your Hub: right-click the Workshop lectern.";
        }
        if (hosts.tier(host) < tier.minIslandTier()) {
            return "Needs Island Tier " + tier.minIslandTier() + ".";
        }
        long coinsNeeded = 0L;
        long coinsHave = 0L;
        for (CostLine line : costLines(player, structure, tier)) {
            if (line.cost().coins()) {
                coinsNeeded += line.cost().amount();
                coinsHave = line.have();
                if (!buyMissing && !line.enough()) {
                    return "Needs " + GuildFormat.compact(line.cost().amount() - line.have()) + " more coins.";
                }
                continue;
            }
            if (!line.enough()) {
                if (!buyMissing) {
                    return "Needs " + GuildFormat.compact(line.cost().amount() - line.have()) + " more "
                            + line.cost().label() + ".";
                }
                coinsNeeded += (line.cost().amount() - line.have()) * unitPrice(line.cost().itemId());
            }
        }
        if (buyMissing && host.isGuild()) {
            return "Buying materials is for your own island.";
        }
        if (buyMissing && coinsHave < coinsNeeded && de.aetherion.core.api.AetherServices.coins() != null) {
            return "Needs " + GuildFormat.compact(coinsNeeded - coinsHave) + " more coins.";
        }
        return spaceProblem(structure, tier.level());
    }

    private static boolean legacy(PlacedStructure structure) {
        return structure.templateId() != null && structure.templateId().startsWith("st_");
    }

    /** The new tier only grows into air (or the building's own blocks). */
    private String spaceProblem(PlacedStructure structure, int toLevel) {
        if (legacy(structure)) {
            return null;
        }
        Template from = template(structure);
        Template to = templates.get(StructureUpgrades.templateFor(structure.type(), toLevel));
        World world = Bukkit.getWorld(structure.world());
        if (to == null || world == null) {
            return null;
        }
        for (int[] cell : to.solidCells()) {
            if (cell[1] < 1) {
                continue;
            }
            if (from != null && from.materialAtLocal(cell[0], cell[1], cell[2]) != Material.AIR) {
                continue;
            }
            int[] r = StateRotator.rotateXZ(cell[0], cell[2], structure.rot());
            Block block = world.getBlockAt(structure.x() + r[0], structure.y() + cell[1], structure.z() + r[1]);
            if (!PasteService.replaceable(block)) {
                return "Clear the space above it first (" + block.getX() + " " + block.getY() + " " + block.getZ() + ").";
            }
        }
        return null;
    }

    public boolean upgrade(Player player, PlacedStructure structure) {
        return upgrade(player, structure, false);
    }

    public boolean upgrade(Player player, PlacedStructure structure, boolean buyMissing) {
        String blocked = upgradeBlocked(player, structure, buyMissing);
        if (blocked != null) {
            player.sendMessage("§c" + blocked);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        StructureUpgrades.Tier tier = nextTier(structure);
        IslandHost host = structure.host();
        long coins = 0L;
        for (StructureUpgrades.Cost cost : tier.costs()) {
            if (cost.coins()) {
                coins += cost.amount();
            }
        }
        long bought = buyMissing ? missingCoins(player, structure) : 0L;
        if (!hosts.charge(player, host, coins + bought)) {
            return false;
        }
        List<String> paid = new ArrayList<>();
        if (coins + bought > 0L) {
            paid.add("§6" + GuildFormat.compact(coins + bought) + " " + hosts.fundsLabel(host));
        }
        for (StructureUpgrades.Cost cost : tier.costs()) {
            String id = cost.itemId();
            if (id == null || !AetherionItemsAccess.available()) {
                continue;
            }
            long left = cost.amount();
            long fromStorage = takeFromStorage(host, cost.key(), left);
            left -= fromStorage;
            long inPack = Math.min(left, AetherionItemsAccess.count(player, id));
            if (inPack > 0L) {
                AetherionItemsAccess.take(player, id, (int) inPack);
            }
            long boughtUnits = left - inPack;
            paid.add("§f" + cost.amount() + " " + cost.label() + (fromStorage > 0L ? " §8(" + fromStorage + " from storage)" : "")
                    + (boughtUnits > 0L ? " §8(" + boughtUnits + " bought)" : ""));
        }
        String fromName = structure.label();
        World world = Bukkit.getWorld(structure.world());
        Template from = template(structure);
        Template to = legacy(structure) ? null : templates.get(StructureUpgrades.templateFor(structure.type(), tier.level()));
        player.sendMessage("§a✦ Upgrading your " + fromName + " §7→ §f" + tier.name() + " §8(" + String.join("§8, ", paid) + "§8)");
        if (to == null || world == null) {
            structure.setLevel(tier.level());
            dirty = true;
            changeListener.accept(host);
            celebrateUpgrade(player, structure, tier, world);
            return true;
        }
        structure.setBuilding(true);
        changeListener.accept(host);
        world.playSound(new Location(world, structure.x() + 0.5, structure.y() + 2, structure.z() + 0.5),
                Sound.BLOCK_SCAFFOLDING_PLACE, 1f, 0.8f);
        paste.transition(from, to, world, structure.x(), structure.y(), structure.z(), structure.rot(), true, job -> {
            structure.setTemplateId(to.id());
            structure.setLevel(tier.level());
            structure.setBuilding(false);
            reindex(structure);
            changeListener.accept(host);
            dirty = true;
            celebrateUpgrade(player, structure, tier, world);
        });
        return true;
    }

    private long takeFromStorage(IslandHost host, String key, long need) {
        long taken = 0L;
        for (PlacedStructure sink : of(host)) {
            if (sink.type().role() != StructureType.Role.SINK || taken >= need) {
                continue;
            }
            for (Res res : new ArrayList<>(sink.store().keySet())) {
                if (res.requirementKey().equals(key) && taken < need) {
                    taken += PlacedStructure.take(sink.store(), res, need - taken);
                }
            }
        }
        if (taken > 0L) {
            dirty = true;
        }
        return taken;
    }

    private void celebrateUpgrade(Player player, PlacedStructure structure, StructureUpgrades.Tier tier, World world) {
        if (world != null) {
            Location top = new Location(world, structure.x() + 0.5, structure.maxY() + 1.0, structure.z() + 0.5);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, top, 40, 1.2, 1.0, 1.2, 0.25);
            world.spawnParticle(Particle.CLOUD, top.clone().add(0, -1.5, 0), 20, 1.5, 0.8, 1.5, 0.02);
            world.playSound(top, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.9f);
            world.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        }
        if (player != null && player.isOnline()) {
            String perk = structure.type() == StructureType.WORKSHOP
                    ? (structure.level() >= 3 ? "§7every Mill and Forge runs §f2× §7as fast" : "§7Forge, Overflow Gate and Sorter unlocked")
                    : structure.type().role() == StructureType.Role.PROCESSOR
                    ? "§f" + GuildFormat.compact((long) structure.ratePerSecond()) + " §7goods / s"
                    : "§7holds §f" + GuildFormat.compact(structure.capacity()) + " §7goods";
            player.sendTitle("§6✦ " + tier.name() + " §6✦", perk, 5, 50, 15);
            player.sendMessage("§a" + tier.name() + " stands. " + perk + "§7.");
        }
    }

    /**
     * Old pasted production looks (3x3 housing / mill / forge / depot houses) become props: their blocks come
     * down (the floor stays, it is the ground now), the prop's shell and chute go up in the same footprint, so
     * every belt that fed them still does. Returns how many were swapped (chunks not loaded wait for later).
     */
    public int migrateToProps(IslandHost host) {
        World world = hosts.world(host);
        if (world == null) {
            return 0;
        }
        int swapped = 0;
        for (PlacedStructure structure : of(host)) {
            String old = structure.templateId();
            if (old == null || structure.building() || structure.frameless() || !Props.OLD_PRODUCTION.contains(old)
                    || !world.isChunkLoaded(structure.x() >> 4, structure.z() >> 4)) {
                continue;
            }
            String target = switch (structure.type()) {
                case QUARRY_HOUSING -> "sm_quarry_rig".equals(old) ? Props.QUARRY_SLIM : Props.QUARRY;
                case MILL -> Props.MILL;
                case FORGE -> Props.FORGE;
                case DEPOT -> Props.DEPOT;
                default -> null;
            };
            Template from = templates.get(old);
            Template to = target == null ? null : templates.get(target);
            if (to == null) {
                continue;
            }
            if (from != null) {
                java.util.Map<String, String> floor = new java.util.HashMap<>();
                for (int[] cell : from.solidCells()) {
                    if (cell[1] <= 0) {
                        floor.put(cell[0] + "," + cell[1] + "," + cell[2], "minecraft:stone");
                    }
                }
                Template keep = Template.synthetic("floor_of_" + old, from.minX(), from.minY(), from.minZ(), from.maxX(),
                        from.maxY(), from.maxZ(), floor);
                paste.clearExcept(from, keep, world, structure.x(), structure.y(), structure.z(), structure.rot(), null);
            }
            structure.setTemplateId(to.id());
            reindex(structure);
            paste.paste(to, world, structure.x(), structure.y(), structure.z(), structure.rot(),
                    PasteService.Mode.ONLY_REPLACEABLE, false, null);
            changeListener.accept(host);
            swapped++;
        }
        if (swapped > 0) {
            dirty = true;
        }
        return swapped;
    }

    /** Frameless (migrated) housings: give each existing quarry a node so belts can pick up from it. */
    public int migrateLegacyMinions() {
        int created = 0;
        for (IslandHost host : hosts.all()) {
            for (QuarryMinion minion : hosts.minions(host)) {
                if (housingOf(host, minion.id()) == null) {
                    registerHousing(host, minion, 0, null, false);
                    created++;
                }
            }
            // housings whose quarry was picked up while the plugin was off
            for (PlacedStructure structure : of(host)) {
                if (structure.type() == StructureType.QUARRY_HOUSING
                        && hosts.minion(host, structure.minionId()) == null) {
                    unregister(structure);
                }
            }
        }
        if (created > 0) {
            plugin.getLogger().info("Island highlight: linked " + created + " existing quarries; they get real housing"
                    + " the first time someone visits their island.");
        }
        return created;
    }

    public void dropHost(IslandHost host) {
        for (PlacedStructure structure : of(host)) {
            unregister(structure);
        }
        byHost.remove(host.key());
        dirty = true;
    }

    // ------------------------------------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------------------------------------

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, PlacedStructure>> entry : byHost.entrySet()) {
            for (PlacedStructure s : entry.getValue().values()) {
                String path = "hosts." + entry.getKey() + "." + s.id();
                config.set(path + ".type", s.type().id());
                config.set(path + ".world", s.world());
                config.set(path + ".x", s.x());
                config.set(path + ".y", s.y());
                config.set(path + ".z", s.z());
                config.set(path + ".rot", s.rot());
                config.set(path + ".placed", s.placedAt());
                if (s.frameless()) {
                    config.set(path + ".frameless", true);
                }
                if (s.minionId() != null) {
                    config.set(path + ".minion", s.minionId().toString());
                }
                if (!s.frameless() && s.templateId() != null) {
                    config.set(path + ".template", s.templateId());
                }
                if (s.type() == StructureType.PROJECT) {
                    config.set(path + ".project", s.projectId());
                }
                if (s.level() > 1) {
                    config.set(path + ".level", s.level());
                }
                if (s.filter() != null) {
                    config.set(path + ".filter", s.filter());
                }
                writeMap(config, path + ".store", s.store());
                writeMap(config, path + ".in", s.input());
                writeMap(config, path + ".out", s.output());
            }
        }
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save island_structures.yml: " + exception.getMessage());
        }
    }

    private static void writeMap(YamlConfiguration config, String path, Map<Res, Long> map) {
        for (Map.Entry<Res, Long> entry : map.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                config.set(path + "." + entry.getKey().key(), entry.getValue());
            }
        }
    }

    public void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("hosts");
        if (root == null) {
            return;
        }
        int loaded = 0;
        for (String hostKey : root.getKeys(false)) {
            IslandHost host = IslandHost.parse(hostKey);
            ConfigurationSection hostSection = root.getConfigurationSection(hostKey);
            if (host == null || hostSection == null) {
                continue;
            }
            for (String idKey : hostSection.getKeys(false)) {
                ConfigurationSection s = hostSection.getConfigurationSection(idKey);
                StructureType type = s == null ? null : StructureType.byId(s.getString("type"));
                if (type == null) {
                    continue;
                }
                try {
                    PlacedStructure structure = new PlacedStructure(UUID.fromString(idKey), type, host,
                            s.getString("world", ""), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getInt("rot"),
                            s.getLong("placed"));
                    structure.setFrameless(s.getBoolean("frameless", false));
                    String minion = s.getString("minion");
                    if (minion != null) {
                        structure.setMinionId(UUID.fromString(minion));
                    }
                    // saved before the compact pass = no template id: keep the template it was built from
                    structure.setTemplateId(s.getString("template", type.legacyTemplateId()));
                    if (type == StructureType.PROJECT) {
                        structure.setProjectId(s.getString("project"));
                    }
                    structure.setLevel(s.getInt("level", 1));
                    structure.setFilter(s.getString("filter"));
                    readMap(s.getConfigurationSection("store"), structure.store());
                    readMap(s.getConfigurationSection("in"), structure.input());
                    readMap(s.getConfigurationSection("out"), structure.output());
                    register(structure);
                    loaded++;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        dirty = false;
        plugin.getLogger().info("Island highlight: loaded " + loaded + " structures.");
    }

    private static void readMap(ConfigurationSection section, Map<Res, Long> map) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Res res = Res.parse(key);
            long amount = section.getLong(key);
            if (res != null && amount > 0L) {
                map.put(res, amount);
            }
        }
    }
}
