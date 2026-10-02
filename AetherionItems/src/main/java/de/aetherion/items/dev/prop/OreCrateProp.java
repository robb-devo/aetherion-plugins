package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Mining — open crate with ore spilling out. */
public final class OreCrateProp extends AmbientProp {

    public OreCrateProp(AetherionItems plugin) {
        super(plugin, "ore_crate");
    }

    @Override
    public String id() {
        return "ore_crate";
    }

    @Override
    protected String title() {
        return "§6Ore Crate";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Mining",
                "§7Packed crate of raw ore",
                "§7for mine yards and ridges."
        );
    }

    @Override
    protected Material icon() {
        return Material.RAW_IRON_BLOCK;
    }

    @Override
    protected float hitWidth() {
        return 1.25f;
    }

    @Override
    protected float hitHeight() {
        return 1.1f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Crate body
        cube(world, piece(origin, front, 0, 0.33, 0), yaw, Material.SPRUCE_PLANKS, 1.0f, 0.66f, 0.8f);
        // Corner posts
        cube(world, piece(origin, front, 0.48, 0.34, 0.38), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.68f, 0.1f);
        cube(world, piece(origin, front, -0.48, 0.34, 0.38), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.68f, 0.1f);
        cube(world, piece(origin, front, 0.48, 0.34, -0.38), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.68f, 0.1f);
        cube(world, piece(origin, front, -0.48, 0.34, -0.38), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.68f, 0.1f);
        // Mid bands
        cube(world, piece(origin, front, 0, 0.33, 0.41), yaw, Material.DARK_OAK_PLANKS, 0.86f, 0.08f, 0.03f);
        cube(world, piece(origin, front, 0, 0.33, -0.41), yaw, Material.DARK_OAK_PLANKS, 0.86f, 0.08f, 0.03f);
        // Ore heap
        cube(world, piece(origin, front, -0.18, 0.75, -0.06), yaw, Material.RAW_IRON_BLOCK, 0.4f, 0.22f, 0.4f);
        cube(world, piece(origin, front, 0.2, 0.73, 0.1), yaw, Material.RAW_COPPER_BLOCK, 0.34f, 0.18f, 0.34f);
        cube(world, piece(origin, front, -0.08, 0.92, -0.02), yaw, Material.RAW_GOLD_BLOCK, 0.24f, 0.16f, 0.24f);
        // Spill
        cube(world, piece(origin, front, 0.3, 0.07, 0.6), yaw, Material.RAW_IRON_BLOCK, 0.14f, 0.14f, 0.14f);
        cube(world, piece(origin, front, 0.1, 0.05, 0.66), yaw, Material.COBBLED_DEEPSLATE, 0.1f, 0.1f, 0.1f);
    }
}
