package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Fishing — coastal lobster trap. */
public final class LobsterTrapProp extends AmbientProp {

    public LobsterTrapProp(AetherionItems plugin) {
        super(plugin, "lobster_trap");
    }

    @Override
    public String id() {
        return "lobster_trap";
    }

    @Override
    protected String title() {
        return "§3Lobster Trap";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Fishing",
                "§7Woven cage for docks",
                "§7and the fishing isle shore."
        );
    }

    @Override
    protected Material icon() {
        return Material.DARK_OAK_TRAPDOOR;
    }

    @Override
    protected float hitWidth() {
        return 1.2f;
    }

    @Override
    protected float hitHeight() {
        return 0.9f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Base board + shadowed net core
        cube(world, piece(origin, front, 0, 0.04, 0), yaw, Material.SPRUCE_PLANKS, 1.0f, 0.08f, 0.7f);
        cube(world, piece(origin, front, 0, 0.32, 0), yaw, Material.DARK_OAK_PLANKS, 0.9f, 0.48f, 0.6f);
        // End boards
        cube(world, piece(origin, front, 0.47, 0.34, 0), yaw, Material.SPRUCE_PLANKS, 0.06f, 0.52f, 0.7f);
        cube(world, piece(origin, front, -0.47, 0.34, 0), yaw, Material.SPRUCE_PLANKS, 0.06f, 0.52f, 0.7f);
        // Slats
        cube(world, piece(origin, front, 0, 0.18, 0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        cube(world, piece(origin, front, 0, 0.32, 0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        cube(world, piece(origin, front, 0, 0.46, 0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        cube(world, piece(origin, front, 0, 0.18, -0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        cube(world, piece(origin, front, 0, 0.32, -0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        cube(world, piece(origin, front, 0, 0.46, -0.32), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.88f, 0.06f, 0.04f);
        // Lattice lid (trapdoor model fills the bottom 3/16 of its box)
        cube(world, piece(origin, front, 0, 0.76, 0), yaw, Material.SPRUCE_TRAPDOOR, 1.0f, 0.32f, 0.7f);
        // Buoy
        cube(world, piece(origin, front, 0.64, 0.11, 0.18), yaw, Material.RED_CONCRETE, 0.2f, 0.22f, 0.2f);
        cube(world, piece(origin, front, 0.64, 0.11, 0.18), yaw, Material.WHITE_CONCRETE, 0.21f, 0.06f, 0.21f);
    }
}
