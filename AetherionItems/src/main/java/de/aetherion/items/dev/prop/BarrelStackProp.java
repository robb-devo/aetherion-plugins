package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** World — staggered barrel stack. */
public final class BarrelStackProp extends AmbientProp {

    public BarrelStackProp(AetherionItems plugin) {
        super(plugin, "barrel_stack");
    }

    @Override
    public String id() {
        return "barrel_stack";
    }

    @Override
    protected String title() {
        return "§6Barrel Stack";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8World",
                "§7Harbour cargo — works",
                "§7anywhere goods pile up."
        );
    }

    @Override
    protected Material icon() {
        return Material.BARREL;
    }

    @Override
    protected float hitWidth() {
        return 1.5f;
    }

    @Override
    protected float hitHeight() {
        return 1.6f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Pyramid
        cube(world, piece(origin, front, -0.42, 0.4, 0.06), yaw, Material.BARREL, 0.8f, 0.8f, 0.8f);
        cube(world, piece(origin, front, 0.42, 0.4, -0.04), yaw, Material.BARREL, 0.8f, 0.8f, 0.8f);
        cube(world, piece(origin, front, 0, 1.185, 0.01), yaw, Material.BARREL, 0.76f, 0.76f, 0.76f);
        // Small crate
        cube(world, piece(origin, front, -0.56, 0.17, 0.68), yaw, Material.SPRUCE_PLANKS, 0.34f, 0.34f, 0.34f);
        cube(world, piece(origin, front, -0.56, 0.355, 0.68), yaw, Material.DARK_OAK_PLANKS, 0.38f, 0.03f, 0.38f);
        // Tied sack
        cube(world, piece(origin, front, 0.5, 0.15, 0.62), yaw, Material.WHITE_TERRACOTTA, 0.32f, 0.3f, 0.28f);
        cube(world, piece(origin, front, 0.5, 0.34, 0.62), yaw, Material.WHITE_TERRACOTTA, 0.14f, 0.08f, 0.14f);
        cube(world, piece(origin, front, 0.5, 0.315, 0.62), yaw, Material.BROWN_TERRACOTTA, 0.16f, 0.03f, 0.16f);
    }
}
