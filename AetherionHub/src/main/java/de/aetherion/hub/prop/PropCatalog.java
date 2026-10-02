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
 * The 40 Aetherion prop schematics: the first 18, then the 22 of the second pass (each group accents →
 * landmarks). Front of every piece faces south.
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
            prop("ae_mine_headframe", "Mine Headframe", "mining", Material.COAL_BLOCK, "Dig 3×3 under collar for a real shaft."),
            // Second pass (_more_props_ship): these anchor on the block you look at, front points the way you look.
            prop("ae_prop_fallen_log", "Fallen Giant", "foraging", Material.OAK_LOG, "Lies across your view; walk-through hollow."),
            prop("ae_prop_mossy_boulders", "Split Boulder", "anywhere", Material.MOSSY_COBBLESTONE, "Grips a block into the ground."),
            prop("ae_prop_lobster_pots", "Lobster Pots", "fishing", Material.SCAFFOLDING, "Beach or quay; tall side at the back."),
            prop("ae_prop_ore_carts", "Ore Carts", "mining", Material.MINECART, "Rails run across your view to the buffer."),
            prop("ae_prop_signpost", "Signpost", "anywhere", Material.DARK_OAK_HANGING_SIGN, "Rotate so the arms match the roads."),
            prop("ae_prop_flower_cart", "Flower Cart", "anywhere", Material.RED_TULIP, "Awning side points the way you look."),
            prop("ae_prop_overlook_bench", "Overlook Bench", "edge", Material.FLOWERING_AZALEA, "Seat looks the way you look."),
            prop("ae_prop_timber_stack", "Timber Stack", "mining", Material.STRIPPED_SPRUCE_LOG, "Log ends point the way you look."),
            prop("ae_prop_quay_capstan", "Quay Capstan", "harbour", Material.LEAD, "Aim 1 block in from the quay edge."),
            prop("ae_prop_channel_marker", "Channel Marker", "fishing", Material.RED_BANNER, "Aim at the sea floor, 2–8 deep."),
            prop("ae_prop_anchor_monument", "Anchor Monument", "harbour", Material.ANVIL, "Plaque points the way you look."),
            prop("ae_fishing_pier", "Fishing Pier", "fishing", Material.FISHING_ROD, "Aim at the quay edge; runs out ahead."),
            prop("ae_forest_standing_stones", "Standing Stones", "foraging", Material.AMETHYST_CLUSTER, "Aim at the glade centre; 15 across."),
            prop("ae_harbour_lighthouse", "Lighthouse", "harbour", Material.SHROOMLIGHT, "Headland ground; door points ahead."),
            prop("ae_harbour_warehouse", "Harbour Warehouse", "harbour", Material.CHEST, "Cargo arch points the way you look."),
            prop("ae_moored_sloop", "Moored Sloop", "harbour", Material.DARK_OAK_BOAT, "Aim at the quay edge, 1 above water."),
            prop("ae_fishing_net_loft", "Net Loft", "fishing", Material.COBWEB, "Aim at the shore edge, 1 above water."),
            prop("ae_mine_tipple", "Ore Tipple", "mining", Material.COAL, "Rails run across your view under the bin."),
            prop("ae_mine_smelter", "Smelter", "mining", Material.BLAST_FURNACE, "Hearth points the way you look."),
            prop("ae_forest_treehouse", "Treehouse", "foraging", Material.OAK_SAPLING, "Hut door points the way you look."),
            prop("ae_edge_windmill", "Windmill", "edge", Material.WHEAT, "Sails point the way you look."),
            prop("ae_edge_broken_bridge", "Broken Bridge", "edge", Material.CRACKED_STONE_BRICKS, "Aim at the last block before the drop.")
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
