package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Mining — spruce timber set framing a stollen entrance. */
public final class MineTimberProp extends AmbientProp {

    public MineTimberProp(AetherionItems plugin) {
        super(plugin, "mine_timber");
    }

    @Override
    public String id() {
        return "mine_timber";
    }

    @Override
    protected String title() {
        return "§7Mine Timbering";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Mining",
                "§7Timber set and lagging",
                "§7for a stollen entrance."
        );
    }

    @Override
    protected Material icon() {
        return Material.SPRUCE_LOG;
    }

    @Override
    protected float hitWidth() {
        return 1.7f;
    }

    @Override
    protected float hitHeight() {
        return 2.4f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Footings
        cube(world, piece(origin, front, 0.72, 0.06, 0), yaw, Material.COBBLED_DEEPSLATE, 0.34f, 0.12f, 0.34f);
        cube(world, piece(origin, front, -0.72, 0.06, 0), yaw, Material.COBBLED_DEEPSLATE, 0.34f, 0.12f, 0.34f);
        // Posts + cap
        cube(world, piece(origin, front, 0.72, 1.17, 0), yaw, Material.SPRUCE_LOG, 0.22f, 2.1f, 0.22f);
        cube(world, piece(origin, front, -0.72, 1.17, 0), yaw, Material.SPRUCE_LOG, 0.22f, 2.1f, 0.22f);
        cube(world, piece(origin, front, 0, 2.2, 0), yaw, Material.SPRUCE_WOOD, 1.78f, 0.24f, 0.26f);
        cube(world, piece(origin, front, 0.55, 2.01, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.14f, 0.18f);
        cube(world, piece(origin, front, -0.55, 2.01, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.14f, 0.18f);
        // Lagging
        cube(world, piece(origin, front, -0.52, 2.345, 0), yaw, Material.SPRUCE_PLANKS, 0.26f, 0.05f, 0.62f);
        cube(world, piece(origin, front, 0, 2.345, 0), yaw, Material.SPRUCE_PLANKS, 0.26f, 0.05f, 0.62f);
        cube(world, piece(origin, front, 0.52, 2.345, 0), yaw, Material.SPRUCE_PLANKS, 0.26f, 0.05f, 0.62f);
        // Hanging lamp
        cube(world, piece(origin, front, 0.34, 2.05, 0), yaw, Material.POLISHED_BLACKSTONE, 0.025f, 0.06f, 0.025f);
        cube(world, piece(origin, front, 0.34, 2.0, 0), yaw, Material.POLISHED_BLACKSTONE, 0.14f, 0.04f, 0.14f);
        cube(world, piece(origin, front, 0.34, 1.905, 0), yaw, Material.SHROOMLIGHT, 0.1f, 0.15f, 0.1f);
    }
}
