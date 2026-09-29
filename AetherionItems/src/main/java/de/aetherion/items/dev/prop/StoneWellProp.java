package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Farm — cobblestone well with windlass, bucket, and stepped roof. */
public final class StoneWellProp extends AmbientProp {

    public StoneWellProp(AetherionItems plugin) {
        super(plugin, "stone_well");
    }

    @Override
    public String id() {
        return "stone_well";
    }

    @Override
    protected String title() {
        return "§bStone Well";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Farm",
                "§7Village well with a rope",
                "§7bucket and shingle roof."
        );
    }

    @Override
    protected Material icon() {
        return Material.BUCKET;
    }

    @Override
    protected float hitWidth() {
        return 1.4f;
    }

    @Override
    protected float hitHeight() {
        return 2.4f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Ring walls
        cube(world, piece(origin, front, 0, 0.375, 0.55), yaw, Material.COBBLESTONE, 1.3f, 0.75f, 0.2f);
        cube(world, piece(origin, front, 0, 0.375, -0.55), yaw, Material.COBBLESTONE, 1.3f, 0.75f, 0.2f);
        cube(world, piece(origin, front, 0.55, 0.375, 0), yaw, Material.COBBLESTONE, 0.2f, 0.75f, 0.9f);
        cube(world, piece(origin, front, -0.55, 0.375, 0), yaw, Material.COBBLESTONE, 0.2f, 0.75f, 0.9f);
        // Deep water
        cube(world, piece(origin, front, 0, 0.47, 0), yaw, Material.DARK_PRISMARINE, 0.88f, 0.06f, 0.88f);
        cube(world, piece(origin, front, 0, 0.62, 0), yaw, Material.LIGHT_BLUE_STAINED_GLASS, 0.88f, 0.04f, 0.88f);
        // Posts + windlass
        cube(world, piece(origin, front, 0.55, 1.45, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.12f, 1.4f, 0.12f);
        cube(world, piece(origin, front, -0.55, 1.45, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.12f, 1.4f, 0.12f);
        cube(world, piece(origin, front, 0, 1.72, 0), yaw, Material.SPRUCE_WOOD, 0.98f, 0.09f, 0.09f);
        // Rope + bucket
        cube(world, piece(origin, front, 0, 1.39, 0), yaw, Material.WHITE_TERRACOTTA, 0.03f, 0.6f, 0.03f);
        cube(world, piece(origin, front, 0, 1.0, 0), yaw, Material.SPRUCE_PLANKS, 0.2f, 0.18f, 0.2f);
        // Stepped roof
        cube(world, piece(origin, front, 0, 2.19, 0), yaw, Material.SPRUCE_PLANKS, 1.5f, 0.08f, 0.96f);
        cube(world, piece(origin, front, 0, 2.27, 0), yaw, Material.SPRUCE_PLANKS, 1.5f, 0.08f, 0.6f);
        cube(world, piece(origin, front, 0, 2.345, 0), yaw, Material.DARK_OAK_PLANKS, 1.56f, 0.07f, 0.22f);
    }
}
