package de.aetherion.hub.prop;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 18 Aetherion prop schematics (accents → landmarks). Front of every piece faces south.
 */
public final class PropCatalog {

    public record Prop(String id, String display, String zone, Material icon, String tip) {
    }

    private static final List<Prop> PROPS = List.of(
            prop("ae_prop_lantern_post", "Lantern Post", "harbour", Material.LANTERN, "Arms run east–west."),
            prop("ae_prop_notice_board", "Notice Board", "anywhere", Material.OAK_SIGN, "Reads from the south."),
            prop("ae_prop_cargo_stack", "Cargo Stack", "harbour", Material.BARREL, "Tall side against a wall."),
            prop("ae_prop_fish_rack", "Fish Rack", "fishing", Material.DRIED_KELP, "Beach / shore blend."),
            prop("ae_prop_ore_heap", "Ore Heap", "mining", Material.RAW_IRON, "Rotate rail toward tracks."),
            prop("ae_prop_wayside_shrine", "Wayside Shrine", "foraging", Material.CANDLE, "Face it toward the path."),
            prop("ae_prop_well", "Well", "anywhere", Material.BUCKET, "Stand in the well centre."),
            prop("ae_prop_forest_campsite", "Forest Campsite", "foraging", Material.CAMPFIRE, "Tent opens south."),
            prop("ae_mine_mouth", "Mine Mouth", "mining", Material.RAIL, "Free-standing outcrop."),
            prop("ae_market_stall", "Market Stall", "harbour", Material.BROWN_BANNER, "Counter faces south."),
            prop("ae_dock_crane", "Dock Crane", "harbour", Material.CHAIN, "y = quay deck; stilts go down."),
            prop("ae_stone_bridge", "Stone Bridge", "anywhere", Material.MOSSY_STONE_BRICKS, "Spans east–west."),
            prop("ae_cliff_overlook", "Cliff Overlook", "edge", Material.SPYGLASS, "Last land block before the drop."),
            prop("ae_ranger_hut", "Ranger Hut", "foraging", Material.SPRUCE_DOOR, "Door faces south."),
            prop("ae_harbour_watchtower", "Harbour Watchtower", "harbour", Material.BELL, "Signal fire on top."),
            prop("ae_boathouse", "Boathouse", "fishing", Material.OAK_BOAT, "y = water surface; stilts below."),
            prop("ae_forest_ruin_gate", "Forest Ruin Gate", "foraging", Material.MOSSY_COBBLESTONE_WALL, "Path through N–S."),
            prop("ae_mine_headframe", "Mine Headframe", "mining", Material.COAL_BLOCK, "Dig 3×3 under collar for a real shaft.")
    );

    private static final Map<String, Prop> BY_ID;

    static {
        Map<String, Prop> map = new LinkedHashMap<>();
        for (Prop prop : PROPS) {
            map.put(prop.id(), prop);
        }
        BY_ID = Collections.unmodifiableMap(map);
    }

    private PropCatalog() {
    }

    private static Prop prop(String id, String display, String zone, Material icon, String tip) {
        return new Prop(id, display, zone, icon, tip);
    }

    public static List<Prop> all() {
        return PROPS;
    }

    public static Prop get(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static Prop byIndex(int index) {
        if (PROPS.isEmpty()) {
            return null;
        }
        int i = Math.floorMod(index, PROPS.size());
        return PROPS.get(i);
    }

    public static int indexOf(String id) {
        Prop prop = get(id);
        if (prop == null) {
            return 0;
        }
        return PROPS.indexOf(prop);
    }

    /** Extract jar props into {@code plugins/AetherionHub/props/} once, and mirror into FAWE schematics. */
    public static void ensureExtracted(Plugin plugin) {
        File hubProps = new File(plugin.getDataFolder(), "props");
        if (!hubProps.isDirectory() && !hubProps.mkdirs()) {
            plugin.getLogger().warning("Could not create " + hubProps.getPath());
        }
        File fawe = new File(plugin.getDataFolder().getParentFile(), "FastAsyncWorldEdit/schematics");
        if (!fawe.isDirectory()) {
            fawe.mkdirs();
        }
        for (Prop prop : PROPS) {
            String name = prop.id() + ".schem";
            File out = new File(hubProps, name);
            if (!out.isFile()) {
                try (InputStream in = plugin.getResource("props/" + name)) {
                    if (in == null) {
                        plugin.getLogger().warning("Missing jar resource props/" + name);
                        continue;
                    }
                    try (OutputStream stream = Files.newOutputStream(out.toPath())) {
                        in.transferTo(stream);
                    }
                } catch (Exception exception) {
                    plugin.getLogger().warning("Failed to extract " + name + ": " + exception.getMessage());
                    continue;
                }
            }
            File faweOut = new File(fawe, name);
            if (!faweOut.isFile()) {
                try {
                    Files.copy(out.toPath(), faweOut.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static File resolve(Plugin plugin, String id) {
        Prop prop = get(id);
        if (prop == null) {
            return null;
        }
        String name = prop.id() + ".schem";
        List<File> candidates = new ArrayList<>(4);
        candidates.add(new File(plugin.getDataFolder(), "props/" + name));
        File plugins = plugin.getDataFolder().getParentFile();
        candidates.add(new File(plugins, "FastAsyncWorldEdit/schematics/" + name));
        candidates.add(new File(plugins, "WorldEdit/schematics/" + name));
        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return candidates.get(0);
    }
}
