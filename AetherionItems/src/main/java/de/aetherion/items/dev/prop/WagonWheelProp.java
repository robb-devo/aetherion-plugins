package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Paths — spare wagon wheel leaning back against a stake. */
public final class WagonWheelProp extends AmbientProp {

    public WagonWheelProp(AetherionItems plugin) {
        super(plugin, "wagon_wheel");
    }

    @Override
    public String id() {
        return "wagon_wheel";
    }

    @Override
    protected String title() {
        return "§6Wagon Wheel";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Paths",
                "§7Spare wheel leaning",
                "§7against a stake."
        );
    }

    @Override
    protected Material icon() {
        return Material.DARK_OAK_TRAPDOOR;
    }

    @Override
    protected float hitWidth() {
        return 1.0f;
    }

    @Override
    protected float hitHeight() {
        return 0.95f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Stake + chock
        cube(world, piece(origin, front, -0.28, 0.5, -0.19), yaw, Material.SPRUCE_LOG, 0.12f, 1.0f, 0.12f);
        cube(world, piece(origin, front, 0.12, 0.04, 0.1), yaw, Material.COBBLESTONE, 0.18f, 0.08f, 0.12f);
        // Rim — each row steps back to fake the lean
        cube(world, piece(origin, front, 0, 0.06, 0), yaw, Material.DARK_OAK_PLANKS, 0.46f, 0.08f, 0.09f);
        cube(world, piece(origin, front, 0.295, 0.165, -0.013), yaw, Material.DARK_OAK_PLANKS, 0.15f, 0.15f, 0.09f);
        cube(world, piece(origin, front, -0.295, 0.165, -0.013), yaw, Material.DARK_OAK_PLANKS, 0.15f, 0.15f, 0.09f);
        cube(world, piece(origin, front, 0.4, 0.46, -0.038), yaw, Material.DARK_OAK_PLANKS, 0.08f, 0.46f, 0.09f);
        cube(world, piece(origin, front, -0.4, 0.46, -0.038), yaw, Material.DARK_OAK_PLANKS, 0.08f, 0.46f, 0.09f);
        cube(world, piece(origin, front, 0.295, 0.755, -0.062), yaw, Material.DARK_OAK_PLANKS, 0.15f, 0.15f, 0.09f);
        cube(world, piece(origin, front, -0.295, 0.755, -0.062), yaw, Material.DARK_OAK_PLANKS, 0.15f, 0.15f, 0.09f);
        cube(world, piece(origin, front, 0, 0.86, -0.072), yaw, Material.DARK_OAK_PLANKS, 0.46f, 0.08f, 0.09f);
        // Spokes + hub
        cube(world, piece(origin, front, 0, 0.46, -0.038), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.05f, 0.74f, 0.05f);
        cube(world, piece(origin, front, 0, 0.46, -0.038), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.74f, 0.05f, 0.05f);
        cube(world, piece(origin, front, 0, 0.46, -0.038), yaw, Material.POLISHED_BLACKSTONE, 0.14f, 0.14f, 0.16f);
    }
}
