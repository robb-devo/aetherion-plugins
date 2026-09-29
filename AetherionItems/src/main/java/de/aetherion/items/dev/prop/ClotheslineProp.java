package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Farm — washing line with hung laundry and a linen basket. */
public final class ClotheslineProp extends AmbientProp {

    public ClotheslineProp(AetherionItems plugin) {
        super(plugin, "clothesline");
    }

    @Override
    public String id() {
        return "clothesline";
    }

    @Override
    protected String title() {
        return "§fClothesline";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Farm",
                "§7Washing line for cottages",
                "§7and farmstead yards."
        );
    }

    @Override
    protected Material icon() {
        return Material.WHITE_WOOL;
    }

    @Override
    protected float hitWidth() {
        return 1.9f;
    }

    @Override
    protected float hitHeight() {
        return 1.65f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Posts + line
        cube(world, piece(origin, front, 0.85, 0.8, 0), yaw, Material.STRIPPED_OAK_WOOD, 0.1f, 1.6f, 0.1f);
        cube(world, piece(origin, front, -0.85, 0.8, 0), yaw, Material.STRIPPED_OAK_WOOD, 0.1f, 1.6f, 0.1f);
        cube(world, piece(origin, front, 0.85, 1.62, 0), yaw, Material.OAK_PLANKS, 0.14f, 0.04f, 0.14f);
        cube(world, piece(origin, front, -0.85, 1.62, 0), yaw, Material.OAK_PLANKS, 0.14f, 0.04f, 0.14f);
        cube(world, piece(origin, front, 0, 1.5, 0), yaw, Material.WHITE_TERRACOTTA, 1.62f, 0.02f, 0.02f);
        // Sheet
        cube(world, piece(origin, front, -0.42, 1.19, 0), yaw, Material.WHITE_WOOL, 0.56f, 0.62f, 0.03f);
        // Shirt
        cube(world, piece(origin, front, 0.22, 1.29, 0), yaw, Material.LIGHT_BLUE_WOOL, 0.26f, 0.42f, 0.03f);
        cube(world, piece(origin, front, 0.22, 1.44, 0), yaw, Material.LIGHT_BLUE_WOOL, 0.46f, 0.12f, 0.025f);
        // Striped towel
        cube(world, piece(origin, front, 0.62, 1.31, 0), yaw, Material.RED_WOOL, 0.18f, 0.38f, 0.03f);
        cube(world, piece(origin, front, 0.62, 1.24, 0), yaw, Material.WHITE_WOOL, 0.185f, 0.05f, 0.035f);
        // Linen basket
        cube(world, piece(origin, front, -0.36, 0.1, 0.38), yaw, Material.STRIPPED_BIRCH_WOOD, 0.36f, 0.2f, 0.26f);
        cube(world, piece(origin, front, -0.36, 0.215, 0.38), yaw, Material.PINK_WOOL, 0.3f, 0.03f, 0.2f);
    }
}
