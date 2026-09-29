package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Paths / general — lantern on a dark oak post. */
public final class LanternPostProp extends AmbientProp {

    public LanternPostProp(AetherionItems plugin) {
        super(plugin, "lantern_post");
    }

    @Override
    public String id() {
        return "lantern_post";
    }

    @Override
    protected String title() {
        return "§eLantern Post";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8World",
                "§7Path light for harbours,",
                "§7mines, and village edges."
        );
    }

    @Override
    protected Material icon() {
        return Material.LANTERN;
    }

    @Override
    protected float hitWidth() {
        return 0.7f;
    }

    @Override
    protected float hitHeight() {
        return 2.4f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Plinth + post
        cube(world, piece(origin, front, 0, 0.09, 0), yaw, Material.STONE_BRICKS, 0.4f, 0.18f, 0.4f);
        cube(world, piece(origin, front, 0, 1.12, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.16f, 1.9f, 0.16f);
        cube(world, piece(origin, front, 0, 2.1, 0), yaw, Material.DARK_OAK_PLANKS, 0.22f, 0.08f, 0.22f);
        // Arm + brace
        cube(world, piece(origin, front, 0, 2.0, 0.32), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.1f, 0.1f, 0.52f);
        cube(world, piece(origin, front, 0, 1.87, 0.13), yaw, Material.DARK_OAK_PLANKS, 0.08f, 0.16f, 0.1f);
        // Hanging lantern
        cube(world, piece(origin, front, 0, 1.895, 0.48), yaw, Material.POLISHED_BLACKSTONE, 0.03f, 0.11f, 0.03f);
        cube(world, piece(origin, front, 0, 1.855, 0.48), yaw, Material.POLISHED_BLACKSTONE, 0.1f, 0.03f, 0.1f);
        cube(world, piece(origin, front, 0, 1.82, 0.48), yaw, Material.POLISHED_BLACKSTONE, 0.2f, 0.04f, 0.2f);
        cube(world, piece(origin, front, 0, 1.67, 0.48), yaw, Material.SHROOMLIGHT, 0.15f, 0.26f, 0.15f);
        cube(world, piece(origin, front, 0, 1.52, 0.48), yaw, Material.POLISHED_BLACKSTONE, 0.2f, 0.04f, 0.2f);
    }
}
