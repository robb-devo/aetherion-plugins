package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Village — hand-cranked grindstone on a sled with a quench tub. */
public final class GrindstoneBenchProp extends AmbientProp {

    public GrindstoneBenchProp(AetherionItems plugin) {
        super(plugin, "grindstone_bench");
    }

    @Override
    public String id() {
        return "grindstone_bench";
    }

    @Override
    protected String title() {
        return "§7Grindstone Bench";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Village",
                "§7Hand-cranked grindstone",
                "§7and a quench tub."
        );
    }

    @Override
    protected Material icon() {
        return Material.GRINDSTONE;
    }

    @Override
    protected float hitWidth() {
        return 1.0f;
    }

    @Override
    protected float hitHeight() {
        return 0.75f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Sled
        cube(world, piece(origin, front, 0, 0.04, 0), yaw, Material.SPRUCE_PLANKS, 0.94f, 0.08f, 0.5f);
        // Frame + axle
        cube(world, piece(origin, front, -0.04, 0.29, 0), yaw, Material.DARK_OAK_PLANKS, 0.06f, 0.42f, 0.1f);
        cube(world, piece(origin, front, 0.32, 0.29, 0), yaw, Material.DARK_OAK_PLANKS, 0.06f, 0.42f, 0.1f);
        cube(world, piece(origin, front, 0.14, 0.44, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.44f, 0.06f, 0.06f);
        // Wheel
        cube(world, piece(origin, front, 0.14, 0.44, 0), yaw, Material.SMOOTH_STONE, 0.12f, 0.5f, 0.3f);
        cube(world, piece(origin, front, 0.14, 0.44, 0), yaw, Material.SMOOTH_STONE, 0.11f, 0.3f, 0.5f);
        // Crank
        cube(world, piece(origin, front, 0.375, 0.38, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.03f, 0.15f, 0.03f);
        cube(world, piece(origin, front, 0.42, 0.315, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.1f, 0.035f, 0.035f);
        // Quench tub
        cube(world, piece(origin, front, -0.28, 0.19, 0.02), yaw, Material.SPRUCE_PLANKS, 0.3f, 0.22f, 0.3f);
        cube(world, piece(origin, front, -0.28, 0.2, 0.02), yaw, Material.POLISHED_BLACKSTONE, 0.31f, 0.03f, 0.31f);
        cube(world, piece(origin, front, -0.28, 0.303, 0.02), yaw, Material.LIGHT_BLUE_STAINED_GLASS, 0.24f, 0.01f, 0.24f);
    }
}
