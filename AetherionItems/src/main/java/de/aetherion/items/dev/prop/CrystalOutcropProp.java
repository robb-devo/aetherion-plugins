package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Aetherion — amethyst spires with glassy tips breaking through basalt. */
public final class CrystalOutcropProp extends AmbientProp {

    public CrystalOutcropProp(AetherionItems plugin) {
        super(plugin, "crystal_outcrop");
    }

    @Override
    public String id() {
        return "crystal_outcrop";
    }

    @Override
    protected String title() {
        return "§dCrystal Outcrop";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Aetherion",
                "§7Aether-touched amethyst",
                "§7breaking through the rock."
        );
    }

    @Override
    protected Material icon() {
        return Material.AMETHYST_CLUSTER;
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
        // Rock
        cube(world, piece(origin, front, 0, 0.07, 0), yaw, Material.SMOOTH_BASALT, 0.78f, 0.14f, 0.6f);
        cube(world, piece(origin, front, 0.14, 0.18, 0.06), yaw, Material.CALCITE, 0.44f, 0.08f, 0.38f);
        cube(world, piece(origin, front, -0.24, 0.2, -0.08), yaw, Material.BUDDING_AMETHYST, 0.3f, 0.16f, 0.28f);
        // Spires
        cube(world, piece(origin, front, -0.02, 0.56, -0.04), yaw, Material.AMETHYST_BLOCK, 0.2f, 0.76f, 0.2f);
        cube(world, piece(origin, front, -0.02, 1.035, -0.04), yaw, Material.PURPLE_STAINED_GLASS, 0.12f, 0.19f, 0.12f);
        cube(world, piece(origin, front, 0.22, 0.43, 0.06), yaw, Material.AMETHYST_BLOCK, 0.14f, 0.46f, 0.14f);
        cube(world, piece(origin, front, 0.22, 0.72, 0.06), yaw, Material.PURPLE_STAINED_GLASS, 0.08f, 0.12f, 0.08f);
        cube(world, piece(origin, front, -0.3, 0.28, 0.16), yaw, Material.AMETHYST_BLOCK, 0.11f, 0.28f, 0.11f);
        // Glow shard + loose chip
        cube(world, piece(origin, front, 0.14, 0.27, -0.2), yaw, Material.PEARLESCENT_FROGLIGHT, 0.1f, 0.26f, 0.1f);
        cube(world, piece(origin, front, 0.34, 0.035, 0.36), yaw, Material.AMETHYST_BLOCK, 0.07f, 0.07f, 0.07f);
    }
}
