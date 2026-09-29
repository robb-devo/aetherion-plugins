package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Fishing — dock drying rack with kelp bundles and a salmon. */
public final class KelpRackProp extends AmbientProp {

    public KelpRackProp(AetherionItems plugin) {
        super(plugin, "kelp_rack");
    }

    @Override
    public String id() {
        return "kelp_rack";
    }

    @Override
    protected String title() {
        return "§3Kelp Drying Rack";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Fishing",
                "§7Dock rack hung with dried",
                "§7kelp and the day's catch."
        );
    }

    @Override
    protected Material icon() {
        return Material.DRIED_KELP_BLOCK;
    }

    @Override
    protected float hitWidth() {
        return 1.45f;
    }

    @Override
    protected float hitHeight() {
        return 1.35f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Frame
        cube(world, piece(origin, front, 0.62, 0.04, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.08f, 0.5f);
        cube(world, piece(origin, front, -0.62, 0.04, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.08f, 0.5f);
        cube(world, piece(origin, front, 0.62, 0.66, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.1f, 1.32f, 0.1f);
        cube(world, piece(origin, front, -0.62, 0.66, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.1f, 1.32f, 0.1f);
        cube(world, piece(origin, front, 0, 1.27, 0), yaw, Material.SPRUCE_WOOD, 1.4f, 0.08f, 0.08f);
        // Kelp bundles
        cube(world, piece(origin, front, -0.38, 0.98, 0), yaw, Material.DRIED_KELP_BLOCK, 0.16f, 0.5f, 0.12f);
        cube(world, piece(origin, front, -0.12, 1.03, 0), yaw, Material.DRIED_KELP_BLOCK, 0.16f, 0.4f, 0.12f);
        cube(world, piece(origin, front, 0.4, 1.0, 0), yaw, Material.DRIED_KELP_BLOCK, 0.16f, 0.46f, 0.12f);
        // Salmon
        cube(world, piece(origin, front, 0.14, 1.09, 0), yaw, Material.PINK_TERRACOTTA, 0.12f, 0.28f, 0.06f);
        cube(world, piece(origin, front, 0.14, 0.92, 0), yaw, Material.RED_TERRACOTTA, 0.18f, 0.06f, 0.05f);
    }
}
