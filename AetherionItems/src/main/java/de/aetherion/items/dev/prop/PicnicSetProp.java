package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Hub — checked blanket with a wicker basket, pie and apples. */
public final class PicnicSetProp extends AmbientProp {

    public PicnicSetProp(AetherionItems plugin) {
        super(plugin, "picnic_set");
    }

    @Override
    public String id() {
        return "picnic_set";
    }

    @Override
    protected String title() {
        return "§dPicnic Set";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Hub",
                "§7Checked blanket, basket,",
                "§7and something to share."
        );
    }

    @Override
    protected Material icon() {
        return Material.CAKE;
    }

    @Override
    protected float hitWidth() {
        return 1.3f;
    }

    @Override
    protected float hitHeight() {
        return 0.4f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Checked blanket
        cube(world, piece(origin, front, 0, 0.01, 0), yaw, Material.RED_WOOL, 1.3f, 0.02f, 1.1f);
        cube(world, piece(origin, front, 0.3, 0.012, 0), yaw, Material.WHITE_WOOL, 0.12f, 0.024f, 1.12f);
        cube(world, piece(origin, front, -0.3, 0.012, 0), yaw, Material.WHITE_WOOL, 0.12f, 0.024f, 1.12f);
        cube(world, piece(origin, front, 0, 0.013, 0.25), yaw, Material.WHITE_WOOL, 1.32f, 0.026f, 0.12f);
        cube(world, piece(origin, front, 0, 0.013, -0.25), yaw, Material.WHITE_WOOL, 1.32f, 0.026f, 0.12f);
        // Wicker basket
        cube(world, piece(origin, front, 0.32, 0.126, -0.2), yaw, Material.HAY_BLOCK, 0.36f, 0.2f, 0.26f);
        cube(world, piece(origin, front, 0.17, 0.29, -0.2), yaw, Material.STRIPPED_OAK_WOOD, 0.03f, 0.14f, 0.03f);
        cube(world, piece(origin, front, 0.47, 0.29, -0.2), yaw, Material.STRIPPED_OAK_WOOD, 0.03f, 0.14f, 0.03f);
        cube(world, piece(origin, front, 0.32, 0.36, -0.2), yaw, Material.STRIPPED_OAK_WOOD, 0.34f, 0.03f, 0.03f);
        // Treats
        cube(world, piece(origin, front, -0.18, 0.051, 0.12), yaw, Material.ORANGE_TERRACOTTA, 0.24f, 0.05f, 0.24f);
        cube(world, piece(origin, front, 0.06, 0.066, 0.26), yaw, Material.RED_CONCRETE, 0.08f, 0.08f, 0.08f);
        cube(world, piece(origin, front, 0.15, 0.061, 0.18), yaw, Material.RED_CONCRETE, 0.07f, 0.07f, 0.07f);
        cube(world, piece(origin, front, -0.42, 0.126, -0.22), yaw, Material.GREEN_STAINED_GLASS, 0.08f, 0.2f, 0.08f);
    }
}
