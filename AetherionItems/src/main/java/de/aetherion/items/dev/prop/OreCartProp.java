package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Mining — loaded minecart on a buffered rail stub. */
public final class OreCartProp extends AmbientProp {

    public OreCartProp(AetherionItems plugin) {
        super(plugin, "ore_cart");
    }

    @Override
    public String id() {
        return "ore_cart";
    }

    @Override
    protected String title() {
        return "§7Ore Cart";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Mining",
                "§7Loaded minecart on a",
                "§7short buffered rail."
        );
    }

    @Override
    protected Material icon() {
        return Material.MINECART;
    }

    @Override
    protected float hitWidth() {
        return 1.1f;
    }

    @Override
    protected float hitHeight() {
        return 1.05f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Track
        cube(world, piece(origin, front, 0, 0.035, 0.62), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.035, 0), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.035, -0.62), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0.3, 0.095, 0), yaw, Material.IRON_BLOCK, 0.05f, 0.05f, 1.7f);
        cube(world, piece(origin, front, -0.3, 0.095, 0), yaw, Material.IRON_BLOCK, 0.05f, 0.05f, 1.7f);
        cube(world, piece(origin, front, 0, 0.1, -0.88), yaw, Material.STRIPPED_DARK_OAK_WOOD, 0.82f, 0.2f, 0.1f);
        // Axles
        cube(world, piece(origin, front, 0, 0.2, 0.26), yaw, Material.POLISHED_BLACKSTONE, 0.74f, 0.14f, 0.14f);
        cube(world, piece(origin, front, 0, 0.2, -0.26), yaw, Material.POLISHED_BLACKSTONE, 0.74f, 0.14f, 0.14f);
        // Cart
        cube(world, piece(origin, front, 0, 0.46, 0), yaw, Material.POLISHED_DEEPSLATE, 0.7f, 0.44f, 0.92f);
        cube(world, piece(origin, front, 0, 0.7, 0), yaw, Material.IRON_BLOCK, 0.76f, 0.05f, 0.98f);
        // Load
        cube(world, piece(origin, front, -0.12, 0.82, -0.1), yaw, Material.COAL_ORE, 0.34f, 0.2f, 0.36f);
        cube(world, piece(origin, front, 0.14, 0.8, 0.18), yaw, Material.RAW_IRON_BLOCK, 0.3f, 0.16f, 0.3f);
        cube(world, piece(origin, front, 0.02, 0.97, 0.04), yaw, Material.GOLD_ORE, 0.2f, 0.14f, 0.2f);
    }
}
