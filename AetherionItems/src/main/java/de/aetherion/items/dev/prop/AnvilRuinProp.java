package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Borderlands — split anvil on a cracked plinth, horn fallen in the ash. */
public final class AnvilRuinProp extends AmbientProp {

    public AnvilRuinProp(AetherionItems plugin) {
        super(plugin, "anvil_ruin");
    }

    @Override
    public String id() {
        return "anvil_ruin";
    }

    @Override
    protected String title() {
        return "§8Anvil Ruin";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Borderlands",
                "§7A smith's anvil, split",
                "§7and left to the ash."
        );
    }

    @Override
    protected Material icon() {
        return Material.DAMAGED_ANVIL;
    }

    @Override
    protected float hitWidth() {
        return 1.1f;
    }

    @Override
    protected float hitHeight() {
        return 0.7f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Cracked plinth
        cube(world, piece(origin, front, -0.05, 0.08, 0), yaw, Material.CRACKED_STONE_BRICKS, 0.66f, 0.16f, 0.5f);
        // Anvil
        cube(world, piece(origin, front, -0.05, 0.21, 0), yaw, Material.POLISHED_DEEPSLATE, 0.52f, 0.1f, 0.34f);
        cube(world, piece(origin, front, -0.05, 0.35, 0), yaw, Material.DEEPSLATE_TILES, 0.24f, 0.18f, 0.2f);
        cube(world, piece(origin, front, -0.08, 0.52, 0), yaw, Material.POLISHED_DEEPSLATE, 0.46f, 0.16f, 0.3f);
        cube(world, piece(origin, front, -0.08, 0.61, 0), yaw, Material.NETHERITE_BLOCK, 0.44f, 0.03f, 0.28f);
        cube(world, piece(origin, front, -0.35, 0.53, 0), yaw, Material.POLISHED_DEEPSLATE, 0.08f, 0.12f, 0.22f);
        cube(world, piece(origin, front, 0.21, 0.53, 0), yaw, Material.CRACKED_DEEPSLATE_TILES, 0.12f, 0.12f, 0.16f);
        // Fallen horn
        cube(world, piece(origin, front, 0.5, 0.06, 0.18), yaw, Material.POLISHED_DEEPSLATE, 0.24f, 0.12f, 0.18f);
        cube(world, piece(origin, front, 0.67, 0.045, 0.18), yaw, Material.POLISHED_DEEPSLATE, 0.12f, 0.09f, 0.11f);
        // Embers + rubble
        cube(world, piece(origin, front, 0.45, 0.012, -0.12), yaw, Material.MAGMA_BLOCK, 0.22f, 0.024f, 0.07f);
        cube(world, piece(origin, front, -0.48, 0.05, 0.34), yaw, Material.BLACKSTONE, 0.14f, 0.1f, 0.12f);
    }
}
