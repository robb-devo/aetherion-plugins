package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Forage / alchemy — tall frame with tied herb bundles hung to dry. */
public final class HerbDryingRackProp extends AmbientProp {

    public HerbDryingRackProp(AetherionItems plugin) {
        super(plugin, "herb_drying_rack");
    }

    @Override
    public String id() {
        return "herb_drying_rack";
    }

    @Override
    protected String title() {
        return "§aHerb Drying Rack";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Forage / Alchemy",
                "§7Tied herb bundles hung",
                "§7upside down to dry."
        );
    }

    @Override
    protected Material icon() {
        return Material.FLOWERING_AZALEA;
    }

    @Override
    protected float hitWidth() {
        return 1.1f;
    }

    @Override
    protected float hitHeight() {
        return 1.8f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Feet + posts
        cube(world, piece(origin, front, 0.46, 0.03, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.06f, 0.46f);
        cube(world, piece(origin, front, -0.46, 0.03, 0), yaw, Material.SPRUCE_PLANKS, 0.14f, 0.06f, 0.46f);
        cube(world, piece(origin, front, 0.46, 0.88, 0), yaw, Material.STRIPPED_SPRUCE_LOG, 0.1f, 1.7f, 0.1f);
        cube(world, piece(origin, front, -0.46, 0.88, 0), yaw, Material.STRIPPED_SPRUCE_LOG, 0.1f, 1.7f, 0.1f);
        // Beam + rung
        cube(world, piece(origin, front, 0, 1.64, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 1.08f, 0.08f, 0.08f);
        cube(world, piece(origin, front, 0, 1.06, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.92f, 0.06f, 0.06f);
        // Tied bundles
        cube(world, piece(origin, front, -0.28, 1.55, 0), yaw, Material.STRIPPED_OAK_WOOD, 0.06f, 0.1f, 0.06f);
        cube(world, piece(origin, front, -0.28, 1.39, 0), yaw, Material.AZALEA_LEAVES, 0.15f, 0.24f, 0.15f);
        cube(world, piece(origin, front, 0, 1.55, 0), yaw, Material.STRIPPED_OAK_WOOD, 0.06f, 0.1f, 0.06f);
        cube(world, piece(origin, front, 0, 1.4, 0), yaw, Material.HAY_BLOCK, 0.13f, 0.22f, 0.13f);
        cube(world, piece(origin, front, 0.28, 1.55, 0), yaw, Material.STRIPPED_OAK_WOOD, 0.06f, 0.1f, 0.06f);
        cube(world, piece(origin, front, 0.28, 1.39, 0), yaw, Material.FLOWERING_AZALEA_LEAVES, 0.15f, 0.24f, 0.15f);
        // Rung bundles
        cube(world, piece(origin, front, -0.16, 0.92, 0), yaw, Material.DRIED_KELP_BLOCK, 0.12f, 0.22f, 0.12f);
        cube(world, piece(origin, front, 0.18, 0.93, 0), yaw, Material.MOSS_BLOCK, 0.12f, 0.2f, 0.12f);
    }
}
