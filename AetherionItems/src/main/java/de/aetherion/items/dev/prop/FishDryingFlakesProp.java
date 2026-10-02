package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Harbour — low slatted flake with split cod laid flat to cure. */
public final class FishDryingFlakesProp extends AmbientProp {

    public FishDryingFlakesProp(AetherionItems plugin) {
        super(plugin, "fish_drying_flakes");
    }

    @Override
    public String id() {
        return "fish_drying_flakes";
    }

    @Override
    protected String title() {
        return "§bFish Drying Flakes";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Harbour",
                "§7Split cod salted and laid",
                "§7flat on slatted flakes."
        );
    }

    @Override
    protected Material icon() {
        return Material.COD;
    }

    @Override
    protected float hitWidth() {
        return 1.2f;
    }

    @Override
    protected float hitHeight() {
        return 0.65f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Legs
        cube(world, piece(origin, front, 0.5, 0.26, 0.26), yaw, Material.STRIPPED_SPRUCE_LOG, 0.07f, 0.52f, 0.07f);
        cube(world, piece(origin, front, -0.5, 0.26, 0.26), yaw, Material.STRIPPED_SPRUCE_LOG, 0.07f, 0.52f, 0.07f);
        cube(world, piece(origin, front, 0.5, 0.26, -0.26), yaw, Material.STRIPPED_SPRUCE_LOG, 0.07f, 0.52f, 0.07f);
        cube(world, piece(origin, front, -0.5, 0.26, -0.26), yaw, Material.STRIPPED_SPRUCE_LOG, 0.07f, 0.52f, 0.07f);
        // Slats
        cube(world, piece(origin, front, 0, 0.535, -0.24), yaw, Material.SPRUCE_PLANKS, 1.14f, 0.03f, 0.12f);
        cube(world, piece(origin, front, 0, 0.535, 0), yaw, Material.SPRUCE_PLANKS, 1.14f, 0.03f, 0.12f);
        cube(world, piece(origin, front, 0, 0.535, 0.24), yaw, Material.SPRUCE_PLANKS, 1.14f, 0.03f, 0.12f);
        // Split cod (body + tail)
        cube(world, piece(origin, front, -0.32, 0.565, -0.04), yaw, Material.WHITE_TERRACOTTA, 0.15f, 0.03f, 0.36f);
        cube(world, piece(origin, front, -0.32, 0.5625, 0.17), yaw, Material.WHITE_TERRACOTTA, 0.22f, 0.025f, 0.06f);
        cube(world, piece(origin, front, 0, 0.565, 0.04), yaw, Material.LIGHT_GRAY_TERRACOTTA, 0.15f, 0.03f, 0.36f);
        cube(world, piece(origin, front, 0, 0.5625, -0.17), yaw, Material.LIGHT_GRAY_TERRACOTTA, 0.22f, 0.025f, 0.06f);
        cube(world, piece(origin, front, 0.32, 0.565, -0.02), yaw, Material.WHITE_TERRACOTTA, 0.15f, 0.03f, 0.36f);
        cube(world, piece(origin, front, 0.32, 0.5625, 0.19), yaw, Material.WHITE_TERRACOTTA, 0.22f, 0.025f, 0.06f);
    }
}
