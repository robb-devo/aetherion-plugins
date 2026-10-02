package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Village / harbour — roofed plank board with pinned notices. */
public final class NoticeBoardProp extends AmbientProp {

    public NoticeBoardProp(AetherionItems plugin) {
        super(plugin, "notice_board");
    }

    @Override
    public String id() {
        return "notice_board";
    }

    @Override
    protected String title() {
        return "§6Notice Board";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Village / Harbour",
                "§7Pinned notices under a",
                "§7little plank roof."
        );
    }

    @Override
    protected Material icon() {
        return Material.SPRUCE_SIGN;
    }

    @Override
    protected float hitWidth() {
        return 1.3f;
    }

    @Override
    protected float hitHeight() {
        return 2.0f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Posts
        cube(world, piece(origin, front, 0.55, 0.95, 0), yaw, Material.SPRUCE_LOG, 0.12f, 1.9f, 0.12f);
        cube(world, piece(origin, front, -0.55, 0.95, 0), yaw, Material.SPRUCE_LOG, 0.12f, 1.9f, 0.12f);
        // Board + trim
        cube(world, piece(origin, front, 0, 1.28, 0), yaw, Material.SPRUCE_PLANKS, 1.1f, 0.78f, 0.06f);
        cube(world, piece(origin, front, 0, 0.86, 0.01), yaw, Material.DARK_OAK_PLANKS, 1.16f, 0.07f, 0.09f);
        cube(world, piece(origin, front, 0, 1.7, 0.01), yaw, Material.DARK_OAK_PLANKS, 1.16f, 0.07f, 0.09f);
        // Roof
        cube(world, piece(origin, front, 0, 1.9, 0.02), yaw, Material.DARK_OAK_PLANKS, 1.4f, 0.06f, 0.34f);
        cube(world, piece(origin, front, 0, 1.955, 0.02), yaw, Material.STRIPPED_DARK_OAK_WOOD, 1.44f, 0.05f, 0.12f);
        // Notices
        cube(world, piece(origin, front, -0.22, 1.32, 0.042), yaw, Material.SMOOTH_SANDSTONE, 0.3f, 0.38f, 0.02f);
        cube(world, piece(origin, front, 0.2, 1.42, 0.042), yaw, Material.WHITE_TERRACOTTA, 0.28f, 0.22f, 0.02f);
        cube(world, piece(origin, front, 0.24, 1.1, 0.042), yaw, Material.WHITE_WOOL, 0.24f, 0.18f, 0.02f);
        cube(world, piece(origin, front, -0.22, 1.47, 0.058), yaw, Material.RED_CONCRETE, 0.04f, 0.04f, 0.02f);
    }
}
