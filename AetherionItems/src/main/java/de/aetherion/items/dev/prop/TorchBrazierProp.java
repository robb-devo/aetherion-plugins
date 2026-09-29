package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** World — wide gold-banded fire bowl on a blackstone column. */
public final class TorchBrazierProp extends AmbientProp {

    public TorchBrazierProp(AetherionItems plugin) {
        super(plugin, "torch_brazier");
    }

    @Override
    public String id() {
        return "torch_brazier";
    }

    @Override
    protected String title() {
        return "§6Ceremonial Brazier";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8World",
                "§7Wide ceremonial fire bowl",
                "§7for plazas and shrines."
        );
    }

    @Override
    protected Material icon() {
        return Material.CAMPFIRE;
    }

    @Override
    protected float hitWidth() {
        return 1.0f;
    }

    @Override
    protected float hitHeight() {
        return 1.6f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Plinth + column
        cube(world, piece(origin, front, 0, 0.07, 0), yaw, Material.POLISHED_BLACKSTONE_BRICKS, 0.62f, 0.14f, 0.62f);
        cube(world, piece(origin, front, 0, 0.4, 0), yaw, Material.POLISHED_BLACKSTONE, 0.28f, 0.52f, 0.28f);
        cube(world, piece(origin, front, 0, 0.69, 0), yaw, Material.CHISELED_POLISHED_BLACKSTONE, 0.44f, 0.08f, 0.44f);
        // Bowl
        cube(world, piece(origin, front, 0, 0.83, 0), yaw, Material.POLISHED_BLACKSTONE, 0.78f, 0.2f, 0.78f);
        cube(world, piece(origin, front, 0, 0.81, 0), yaw, Material.GOLD_BLOCK, 0.82f, 0.05f, 0.82f);
        cube(world, piece(origin, front, 0, 0.955, 0), yaw, Material.POLISHED_BLACKSTONE_BRICKS, 0.96f, 0.05f, 0.96f);
        cube(world, piece(origin, front, 0, 0.995, 0), yaw, Material.MAGMA_BLOCK, 0.78f, 0.03f, 0.78f);
        // Fire
        cube(world, piece(origin, front, 0, 1.125, 0), yaw, Material.ORANGE_STAINED_GLASS, 0.62f, 0.22f, 0.62f);
        cube(world, piece(origin, front, 0, 1.205, 0), yaw, Material.SHROOMLIGHT, 0.4f, 0.38f, 0.4f);
        cube(world, piece(origin, front, 0.03, 1.44, -0.02), yaw, Material.YELLOW_STAINED_GLASS, 0.28f, 0.24f, 0.28f);
        cube(world, piece(origin, front, 0.03, 1.47, -0.02), yaw, Material.SHROOMLIGHT, 0.16f, 0.16f, 0.16f);
    }
}
