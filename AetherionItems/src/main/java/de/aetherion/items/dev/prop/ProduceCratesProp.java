package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Farm — market crates of melons and pumpkins with a price tag. */
public final class ProduceCratesProp extends AmbientProp {

    public ProduceCratesProp(AetherionItems plugin) {
        super(plugin, "produce_crates");
    }

    @Override
    public String id() {
        return "produce_crates";
    }

    @Override
    protected String title() {
        return "§aProduce Crates";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Farm",
                "§7Market crates of melons",
                "§7and pumpkins."
        );
    }

    @Override
    protected Material icon() {
        return Material.MELON;
    }

    @Override
    protected float hitWidth() {
        return 1.4f;
    }

    @Override
    protected float hitHeight() {
        return 0.8f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Crates
        cube(world, piece(origin, front, -0.36, 0.2, 0.04), yaw, Material.SPRUCE_PLANKS, 0.62f, 0.4f, 0.58f);
        cube(world, piece(origin, front, -0.36, 0.2, 0.04), yaw, Material.DARK_OAK_PLANKS, 0.64f, 0.06f, 0.6f);
        cube(world, piece(origin, front, 0.36, 0.2, -0.04), yaw, Material.SPRUCE_PLANKS, 0.62f, 0.4f, 0.58f);
        cube(world, piece(origin, front, 0.36, 0.2, -0.04), yaw, Material.DARK_OAK_PLANKS, 0.64f, 0.06f, 0.6f);
        // Melons
        cube(world, piece(origin, front, -0.5, 0.5, 0.12), yaw, Material.MELON, 0.24f, 0.22f, 0.24f);
        cube(world, piece(origin, front, -0.24, 0.49, -0.06), yaw, Material.MELON, 0.26f, 0.2f, 0.26f);
        // Pumpkins
        cube(world, piece(origin, front, 0.26, 0.51, 0.02), yaw, Material.PUMPKIN, 0.26f, 0.24f, 0.26f);
        cube(world, piece(origin, front, 0.5, 0.49, -0.14), yaw, Material.PUMPKIN, 0.22f, 0.2f, 0.22f);
        // Price tag
        cube(world, piece(origin, front, -0.62, 0.56, 0.3), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.04f, 0.32f, 0.04f);
        cube(world, piece(origin, front, -0.62, 0.7, 0.33), yaw, Material.BIRCH_PLANKS, 0.22f, 0.12f, 0.02f);
    }
}
