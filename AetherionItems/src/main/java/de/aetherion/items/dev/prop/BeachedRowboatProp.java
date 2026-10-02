package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Coast — weathered rowboat hauled up the beach, oar across the gunwales. */
public final class BeachedRowboatProp extends AmbientProp {

    public BeachedRowboatProp(AetherionItems plugin) {
        super(plugin, "beached_rowboat");
    }

    @Override
    public String id() {
        return "beached_rowboat";
    }

    @Override
    protected String title() {
        return "§bBeached Rowboat";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Coast",
                "§7Weathered rowboat hauled",
                "§7above the tide line."
        );
    }

    @Override
    protected Material icon() {
        return Material.OAK_BOAT;
    }

    @Override
    protected float hitWidth() {
        return 1.2f;
    }

    @Override
    protected float hitHeight() {
        return 0.45f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Hull
        cube(world, piece(origin, front, 0, 0.04, -0.16), yaw, Material.SPRUCE_PLANKS, 0.62f, 0.08f, 1.3f);
        cube(world, piece(origin, front, 0.34, 0.19, -0.16), yaw, Material.SPRUCE_PLANKS, 0.07f, 0.3f, 1.3f);
        cube(world, piece(origin, front, -0.34, 0.19, -0.16), yaw, Material.SPRUCE_PLANKS, 0.07f, 0.3f, 1.3f);
        cube(world, piece(origin, front, 0.34, 0.36, -0.16), yaw, Material.DARK_OAK_PLANKS, 0.1f, 0.04f, 1.32f);
        cube(world, piece(origin, front, -0.34, 0.36, -0.16), yaw, Material.DARK_OAK_PLANKS, 0.1f, 0.04f, 1.32f);
        cube(world, piece(origin, front, 0, 0.19, -0.83), yaw, Material.DARK_OAK_PLANKS, 0.72f, 0.3f, 0.07f);
        // Bow
        cube(world, piece(origin, front, 0, 0.19, 0.57), yaw, Material.SPRUCE_PLANKS, 0.74f, 0.3f, 0.18f);
        cube(world, piece(origin, front, 0, 0.19, 0.72), yaw, Material.SPRUCE_PLANKS, 0.46f, 0.3f, 0.14f);
        cube(world, piece(origin, front, 0, 0.21, 0.83), yaw, Material.DARK_OAK_PLANKS, 0.18f, 0.38f, 0.1f);
        // Thwart + oar
        cube(world, piece(origin, front, 0, 0.25, -0.26), yaw, Material.STRIPPED_OAK_WOOD, 0.62f, 0.05f, 0.16f);
        cube(world, piece(origin, front, 0.14, 0.403, -0.06), yaw, Material.STRIPPED_SPRUCE_WOOD, 1.0f, 0.045f, 0.045f);
        cube(world, piece(origin, front, 0.72, 0.403, -0.06), yaw, Material.SPRUCE_PLANKS, 0.26f, 0.03f, 0.13f);
    }
}
