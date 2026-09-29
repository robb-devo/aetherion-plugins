package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Forage — straw bee skep on a plank stand. */
public final class BeeSkepProp extends AmbientProp {

    public BeeSkepProp(AetherionItems plugin) {
        super(plugin, "bee_skep");
    }

    @Override
    public String id() {
        return "bee_skep";
    }

    @Override
    protected String title() {
        return "§eBee Skep";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Forage",
                "§7Straw hive on a plank",
                "§7stand with fresh honey."
        );
    }

    @Override
    protected Material icon() {
        return Material.HONEYCOMB;
    }

    @Override
    protected float hitWidth() {
        return 0.9f;
    }

    @Override
    protected float hitHeight() {
        return 1.15f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Stand
        cube(world, piece(origin, front, 0, 0.52, 0), yaw, Material.SPRUCE_PLANKS, 0.8f, 0.08f, 0.8f);
        cube(world, piece(origin, front, 0.32, 0.24, 0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.08f, 0.48f, 0.08f);
        cube(world, piece(origin, front, -0.32, 0.24, 0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.08f, 0.48f, 0.08f);
        cube(world, piece(origin, front, 0.32, 0.24, -0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.08f, 0.48f, 0.08f);
        cube(world, piece(origin, front, -0.32, 0.24, -0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.08f, 0.48f, 0.08f);
        // Skep tiers
        cube(world, piece(origin, front, 0, 0.66, 0), yaw, Material.HAY_BLOCK, 0.54f, 0.2f, 0.54f);
        cube(world, piece(origin, front, 0, 0.84, 0), yaw, Material.HAY_BLOCK, 0.44f, 0.16f, 0.44f);
        cube(world, piece(origin, front, 0, 0.99, 0), yaw, Material.HAY_BLOCK, 0.32f, 0.14f, 0.32f);
        cube(world, piece(origin, front, 0, 1.1, 0), yaw, Material.HAY_BLOCK, 0.16f, 0.08f, 0.16f);
        cube(world, piece(origin, front, 0, 0.61, 0.275), yaw, Material.BLACK_TERRACOTTA, 0.12f, 0.08f, 0.02f);
        // Honey
        cube(world, piece(origin, front, 0.32, 0.58, 0.32), yaw, Material.HONEY_BLOCK, 0.1f, 0.04f, 0.1f);
        cube(world, piece(origin, front, -0.32, 0.61, -0.32), yaw, Material.HONEYCOMB_BLOCK, 0.1f, 0.1f, 0.1f);
        // Bee
        cube(world, piece(origin, front, 0.32, 1.02, 0.2), yaw, Material.YELLOW_WOOL, 0.07f, 0.06f, 0.09f);
        cube(world, piece(origin, front, 0.32, 1.02, 0.2), yaw, Material.BLACK_WOOL, 0.075f, 0.065f, 0.025f);
    }
}
