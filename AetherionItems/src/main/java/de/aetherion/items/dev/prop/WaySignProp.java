package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** World — trail waymarker with a hanging board. */
public final class WaySignProp extends AmbientProp {

    public WaySignProp(AetherionItems plugin) {
        super(plugin, "way_sign");
    }

    @Override
    public String id() {
        return "way_sign";
    }

    @Override
    protected String title() {
        return "§eWay Sign";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8World",
                "§7Directional post for paths,",
                "§7crossroads, and isle edges."
        );
    }

    @Override
    protected Material icon() {
        return Material.OAK_SIGN;
    }

    @Override
    protected float hitWidth() {
        return 0.9f;
    }

    @Override
    protected float hitHeight() {
        return 2.3f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Footing + post
        cube(world, piece(origin, front, 0, 0.09, 0), yaw, Material.COBBLESTONE, 0.34f, 0.18f, 0.34f);
        cube(world, piece(origin, front, 0, 1.12, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.14f, 1.9f, 0.14f);
        // Stepped cap
        cube(world, piece(origin, front, 0, 2.1, 0), yaw, Material.SPRUCE_PLANKS, 0.2f, 0.06f, 0.2f);
        cube(world, piece(origin, front, 0, 2.16, 0), yaw, Material.SPRUCE_PLANKS, 0.1f, 0.06f, 0.1f);
        // Upper arrow (right)
        cube(world, piece(origin, front, 0.36, 1.74, 0), yaw, Material.OAK_PLANKS, 0.72f, 0.22f, 0.06f);
        cube(world, piece(origin, front, 0.76, 1.74, 0), yaw, Material.OAK_PLANKS, 0.08f, 0.12f, 0.06f);
        cube(world, piece(origin, front, 0.34, 1.74, 0), yaw, Material.DARK_OAK_PLANKS, 0.44f, 0.04f, 0.07f);
        // Lower arrow (left)
        cube(world, piece(origin, front, -0.33, 1.4, 0), yaw, Material.BIRCH_PLANKS, 0.66f, 0.2f, 0.06f);
        cube(world, piece(origin, front, -0.7, 1.4, 0), yaw, Material.BIRCH_PLANKS, 0.08f, 0.12f, 0.06f);
        cube(world, piece(origin, front, -0.31, 1.4, 0), yaw, Material.DARK_OAK_PLANKS, 0.4f, 0.04f, 0.07f);
    }
}
