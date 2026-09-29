package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Capital / world — small flower cart. */
public final class FlowerCartProp extends AmbientProp {

    public FlowerCartProp(AetherionItems plugin) {
        super(plugin, "flower_cart");
    }

    @Override
    public String id() {
        return "flower_cart";
    }

    @Override
    protected String title() {
        return "§dFlower Cart";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8World",
                "§7Market cart of blooms —",
                "§7capital squares and gardens."
        );
    }

    @Override
    protected Material icon() {
        return Material.PINK_TULIP;
    }

    @Override
    protected float hitWidth() {
        return 1.5f;
    }

    @Override
    protected float hitHeight() {
        return 1.5f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Bed + rails
        cube(world, piece(origin, front, 0, 0.6, 0), yaw, Material.BIRCH_PLANKS, 1.06f, 0.1f, 1.4f);
        cube(world, piece(origin, front, 0.53, 0.8, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.3f, 1.4f);
        cube(world, piece(origin, front, -0.53, 0.8, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.3f, 1.4f);
        cube(world, piece(origin, front, 0, 0.8, 0.67), yaw, Material.DARK_OAK_PLANKS, 1.0f, 0.3f, 0.06f);
        cube(world, piece(origin, front, 0, 0.8, -0.67), yaw, Material.DARK_OAK_PLANKS, 1.0f, 0.3f, 0.06f);
        // Octagonal wheels (two crossed slabs of unequal thickness)
        cube(world, piece(origin, front, 0.62, 0.31, -0.3), yaw, Material.SPRUCE_PLANKS, 0.08f, 0.6f, 0.36f);
        cube(world, piece(origin, front, 0.62, 0.31, -0.3), yaw, Material.SPRUCE_PLANKS, 0.07f, 0.36f, 0.6f);
        cube(world, piece(origin, front, -0.62, 0.31, -0.3), yaw, Material.SPRUCE_PLANKS, 0.08f, 0.6f, 0.36f);
        cube(world, piece(origin, front, -0.62, 0.31, -0.3), yaw, Material.SPRUCE_PLANKS, 0.07f, 0.36f, 0.6f);
        // Front legs + handles
        cube(world, piece(origin, front, 0.44, 0.275, 0.58), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.55f, 0.06f);
        cube(world, piece(origin, front, -0.44, 0.275, 0.58), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.55f, 0.06f);
        cube(world, piece(origin, front, 0.53, 0.72, 0.95), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.06f, 0.5f);
        cube(world, piece(origin, front, -0.53, 0.72, 0.95), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.06f, 0.06f, 0.5f);
        // Blooms
        cube(world, piece(origin, front, -0.18, 0.86, -0.28), yaw, Material.FLOWERING_AZALEA_LEAVES, 0.52f, 0.42f, 0.62f);
        cube(world, piece(origin, front, 0.24, 0.9, -0.3), yaw, Material.ALLIUM, 0.4f, 0.5f, 0.4f);
        cube(world, piece(origin, front, 0.22, 0.88, 0.3), yaw, Material.PINK_TULIP, 0.4f, 0.46f, 0.4f);
        cube(world, piece(origin, front, -0.22, 0.88, 0.32), yaw, Material.OXEYE_DAISY, 0.4f, 0.46f, 0.4f);
    }
}
