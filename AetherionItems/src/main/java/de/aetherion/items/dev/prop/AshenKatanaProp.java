package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Wall decor — crimson-edged katana with twin tassels, hung flat on two pegs. */
public final class AshenKatanaProp extends AmbientProp {

    public AshenKatanaProp(AetherionItems plugin) {
        super(plugin, "ashen_katana");
    }

    @Override
    public String id() {
        return "ashen_katana";
    }

    @Override
    protected String title() {
        return "§dBlossom Blade";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Wall Decor",
                "§7Crimson-edged blade with",
                "§7twin tassels. Click a wall."
        );
    }

    @Override
    protected Material icon() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    protected float hitWidth() {
        return 1.0f;
    }

    @Override
    protected float hitHeight() {
        return 0.75f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Pegs
        wall(world, origin, front, yaw, Material.DARK_OAK_PLANKS, -0.42, 0.4, -0.44, 0.05f, 0.05f, 0.12f);
        wall(world, origin, front, yaw, Material.DARK_OAK_PLANKS, 0.27, 0.462, -0.44, 0.05f, 0.05f, 0.12f);
        // Twin tassels
        wall(world, origin, front, yaw, Material.RED_CONCRETE, -0.64, 0.35, -0.44, 0.022f, 0.14f, 0.022f);
        wall(world, origin, front, yaw, Material.RED_WOOL, -0.64, 0.225, -0.44, 0.055f, 0.12f, 0.04f);
        wall(world, origin, front, yaw, Material.RED_CONCRETE, -0.585, 0.365, -0.435, 0.022f, 0.11f, 0.022f);
        wall(world, origin, front, yaw, Material.RED_WOOL, -0.585, 0.265, -0.435, 0.05f, 0.1f, 0.036f);
        // Pommel, wrapped grip, tsuba
        wall(world, origin, front, yaw, Material.NETHERITE_BLOCK, -0.625, 0.468, -0.43, 0.05f, 0.1f, 0.075f);
        wall(world, origin, front, yaw, Material.BLACK_CONCRETE, -0.5, 0.47, -0.43, 0.2f, 0.085f, 0.05f);
        wall(world, origin, front, yaw, Material.BLACK_CONCRETE, -0.33, 0.482, -0.425, 0.16f, 0.085f, 0.05f);
        wall(world, origin, front, yaw, Material.GRAY_CONCRETE, -0.54, 0.47, -0.428, 0.028f, 0.093f, 0.058f);
        wall(world, origin, front, yaw, Material.GRAY_CONCRETE, -0.44, 0.47, -0.428, 0.028f, 0.093f, 0.058f);
        wall(world, origin, front, yaw, Material.GRAY_CONCRETE, -0.33, 0.482, -0.428, 0.028f, 0.093f, 0.058f);
        wall(world, origin, front, yaw, Material.NETHERITE_BLOCK, -0.23, 0.49, -0.44, 0.045f, 0.19f, 0.11f);
        // Blade — rises toward the tip; alternating depth keeps overlaps from z-fighting
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, -0.105, 0.5, -0.43, 0.21f, 0.066f, 0.03f);
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, 0.085, 0.515, -0.425, 0.21f, 0.065f, 0.03f);
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, 0.265, 0.54, -0.43, 0.19f, 0.062f, 0.03f);
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, 0.42, 0.575, -0.425, 0.16f, 0.058f, 0.03f);
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, 0.54, 0.618, -0.43, 0.12f, 0.054f, 0.03f);
        wall(world, origin, front, yaw, Material.MAGENTA_CONCRETE, 0.6225, 0.66, -0.425, 0.075f, 0.044f, 0.03f);
        // Crimson edge peeking below the blade
        wall(world, origin, front, yaw, Material.RED_CONCRETE, -0.105, 0.48, -0.44, 0.21f, 0.066f, 0.03f);
        wall(world, origin, front, yaw, Material.RED_CONCRETE, 0.085, 0.495, -0.444, 0.21f, 0.065f, 0.03f);
        wall(world, origin, front, yaw, Material.RED_CONCRETE, 0.265, 0.52, -0.44, 0.19f, 0.062f, 0.03f);
        wall(world, origin, front, yaw, Material.RED_CONCRETE, 0.42, 0.555, -0.444, 0.16f, 0.058f, 0.03f);
        wall(world, origin, front, yaw, Material.RED_CONCRETE, 0.54, 0.598, -0.44, 0.12f, 0.054f, 0.03f);
    }

    /** x runs to the viewer's right; piece()'s right axis is the prop's own, i.e. the viewer's left. */
    private void wall(World world, Location origin, BlockFace front, float yaw, Material material,
                      double x, double y, double depth, float width, float height, float thickness) {
        cube(world, piece(origin, front, -x, y, depth), yaw, material, width, height, thickness);
    }
}
