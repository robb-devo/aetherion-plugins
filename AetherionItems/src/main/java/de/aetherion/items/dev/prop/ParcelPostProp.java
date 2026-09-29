package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Village — painted mailbox with its flag up and a parcel waiting. */
public final class ParcelPostProp extends AmbientProp {

    public ParcelPostProp(AetherionItems plugin) {
        super(plugin, "parcel_post");
    }

    @Override
    public String id() {
        return "parcel_post";
    }

    @Override
    protected String title() {
        return "§3Parcel Post";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Village",
                "§7Mailbox with its flag up —",
                "§7something's waiting."
        );
    }

    @Override
    protected Material icon() {
        return Material.PAPER;
    }

    @Override
    protected float hitWidth() {
        return 0.6f;
    }

    @Override
    protected float hitHeight() {
        return 1.4f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Footing + post
        cube(world, piece(origin, front, 0, 0.05, 0), yaw, Material.COBBLESTONE, 0.26f, 0.1f, 0.26f);
        cube(world, piece(origin, front, 0, 0.56, 0), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.11f, 0.92f, 0.11f);
        cube(world, piece(origin, front, 0, 1.0, 0), yaw, Material.DARK_OAK_PLANKS, 0.18f, 0.05f, 0.4f);
        // Box + rounded roof
        cube(world, piece(origin, front, 0, 1.155, 0), yaw, Material.CYAN_TERRACOTTA, 0.32f, 0.26f, 0.48f);
        cube(world, piece(origin, front, 0, 1.305, 0), yaw, Material.DARK_OAK_PLANKS, 0.36f, 0.05f, 0.52f);
        cube(world, piece(origin, front, 0, 1.35, 0), yaw, Material.DARK_OAK_PLANKS, 0.24f, 0.05f, 0.5f);
        // Door + knob
        cube(world, piece(origin, front, 0, 1.15, 0.25), yaw, Material.SPRUCE_PLANKS, 0.26f, 0.2f, 0.02f);
        cube(world, piece(origin, front, -0.08, 1.15, 0.268), yaw, Material.GOLD_BLOCK, 0.035f, 0.035f, 0.025f);
        // Flag (raised)
        cube(world, piece(origin, front, 0.172, 1.2, -0.1), yaw, Material.POLISHED_BLACKSTONE, 0.025f, 0.2f, 0.025f);
        cube(world, piece(origin, front, 0.172, 1.25, -0.02), yaw, Material.RED_CONCRETE, 0.02f, 0.09f, 0.14f);
        // Parcel
        cube(world, piece(origin, front, -0.3, 0.09, 0.18), yaw, Material.STRIPPED_OAK_WOOD, 0.28f, 0.18f, 0.22f);
        cube(world, piece(origin, front, -0.3, 0.09, 0.18), yaw, Material.WHITE_WOOL, 0.29f, 0.19f, 0.035f);
    }
}
