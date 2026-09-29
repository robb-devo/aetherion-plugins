package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Forage — mossy fallen log with shelf fungi and toadstools. */
public final class MushroomShelfProp extends AmbientProp {

    public MushroomShelfProp(AetherionItems plugin) {
        super(plugin, "mushroom_shelf");
    }

    @Override
    public String id() {
        return "mushroom_shelf";
    }

    @Override
    protected String title() {
        return "§6Mushroom Shelf Log";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Forage",
                "§7Mossy log sprouting shelf",
                "§7fungi and toadstools."
        );
    }

    @Override
    protected Material icon() {
        return Material.RED_MUSHROOM;
    }

    @Override
    protected float hitWidth() {
        return 1.1f;
    }

    @Override
    protected float hitHeight() {
        return 0.6f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Log
        cube(world, piece(origin, front, 0, 0.19, 0), yaw, Material.SPRUCE_WOOD, 1.1f, 0.38f, 0.38f);
        cube(world, piece(origin, front, 0.56, 0.19, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.02f, 0.32f, 0.32f);
        cube(world, piece(origin, front, -0.1, 0.395, -0.02), yaw, Material.MOSS_BLOCK, 0.7f, 0.03f, 0.26f);
        // Shelf fungi
        cube(world, piece(origin, front, 0.18, 0.24, 0.26), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.26f, 0.04f, 0.14f);
        cube(world, piece(origin, front, 0.3, 0.31, 0.245), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.18f, 0.035f, 0.11f);
        cube(world, piece(origin, front, -0.3, 0.17, 0.25), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.2f, 0.04f, 0.12f);
        // Toadstools
        cube(world, piece(origin, front, 0.3, 0.43, 0.06), yaw, Material.MUSHROOM_STEM, 0.05f, 0.1f, 0.05f);
        cube(world, piece(origin, front, 0.3, 0.5, 0.06), yaw, Material.RED_MUSHROOM_BLOCK, 0.15f, 0.06f, 0.15f);
        cube(world, piece(origin, front, 0.14, 0.42, -0.07), yaw, Material.MUSHROOM_STEM, 0.04f, 0.08f, 0.04f);
        cube(world, piece(origin, front, 0.14, 0.475, -0.07), yaw, Material.RED_MUSHROOM_BLOCK, 0.1f, 0.045f, 0.1f);
        cube(world, piece(origin, front, 0.02, 0.04, 0.33), yaw, Material.MUSHROOM_STEM, 0.05f, 0.08f, 0.05f);
        cube(world, piece(origin, front, 0.02, 0.09, 0.33), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.13f, 0.04f, 0.13f);
    }
}
