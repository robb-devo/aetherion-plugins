package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Farm — wooden watering trough with a still water plane. */
public final class WaterTroughProp extends AmbientProp {

    public WaterTroughProp(AetherionItems plugin) {
        super(plugin, "water_trough");
    }

    @Override
    public String id() {
        return "water_trough";
    }

    @Override
    protected String title() {
        return "§6Water Trough";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Farm",
                "§7Stock trough for field edges",
                "§7and the Mill Yard."
        );
    }

    @Override
    protected Material icon() {
        return Material.OAK_SLAB;
    }

    @Override
    protected float hitWidth() {
        return 1.6f;
    }

    @Override
    protected float hitHeight() {
        return 0.95f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Skids
        cube(world, piece(origin, front, 0.6, 0.06, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.16f, 0.12f, 0.92f);
        cube(world, piece(origin, front, -0.6, 0.06, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.16f, 0.12f, 0.92f);
        // Hollow tub
        cube(world, piece(origin, front, 0, 0.175, 0), yaw, Material.SPRUCE_PLANKS, 1.4f, 0.09f, 0.7f);
        cube(world, piece(origin, front, 0, 0.395, 0.36), yaw, Material.SPRUCE_PLANKS, 1.46f, 0.55f, 0.08f);
        cube(world, piece(origin, front, 0, 0.395, -0.36), yaw, Material.SPRUCE_PLANKS, 1.46f, 0.55f, 0.08f);
        // End posts
        cube(world, piece(origin, front, 0.72, 0.42, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.6f, 0.84f);
        cube(world, piece(origin, front, -0.72, 0.42, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.6f, 0.84f);
        // Rim caps
        cube(world, piece(origin, front, 0, 0.685, 0.36), yaw, Material.STRIPPED_SPRUCE_WOOD, 1.34f, 0.03f, 0.12f);
        cube(world, piece(origin, front, 0, 0.685, -0.36), yaw, Material.STRIPPED_SPRUCE_WOOD, 1.34f, 0.03f, 0.12f);
        // Water
        cube(world, piece(origin, front, 0, 0.6, 0), yaw, Material.LIGHT_BLUE_STAINED_GLASS, 1.3f, 0.04f, 0.6f);
    }
}
