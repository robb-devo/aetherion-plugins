package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Aetherion — amethyst-banded egg in a crystal cradle on an End plinth. */
public final class AetherionEggProp extends AmbientProp {

    public AetherionEggProp(AetherionItems plugin) {
        super(plugin, "aetherion_egg");
    }

    @Override
    public String id() {
        return "aetherion_egg";
    }

    @Override
    protected String title() {
        return "§5Aetherion Egg";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Aetherion",
                "§7Something stirs inside",
                "§7the amethyst-veined shell."
        );
    }

    @Override
    protected Material icon() {
        return Material.DRAGON_EGG;
    }

    @Override
    protected float hitWidth() {
        return 0.8f;
    }

    @Override
    protected float hitHeight() {
        return 1.0f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Plinth
        cube(world, piece(origin, front, 0, 0.05, 0), yaw, Material.END_STONE_BRICKS, 0.66f, 0.1f, 0.66f);
        cube(world, piece(origin, front, 0, 0.12, 0), yaw, Material.PURPUR_BLOCK, 0.5f, 0.04f, 0.5f);
        // Crystal cradle
        cube(world, piece(origin, front, 0.22, 0.27, 0.2), yaw, Material.AMETHYST_BLOCK, 0.07f, 0.26f, 0.07f);
        cube(world, piece(origin, front, -0.23, 0.24, 0.16), yaw, Material.AMETHYST_BLOCK, 0.06f, 0.2f, 0.06f);
        cube(world, piece(origin, front, 0.03, 0.25, -0.23), yaw, Material.AMETHYST_BLOCK, 0.065f, 0.22f, 0.065f);
        // Egg
        cube(world, piece(origin, front, 0, 0.19, 0), yaw, Material.OBSIDIAN, 0.3f, 0.1f, 0.3f);
        cube(world, piece(origin, front, 0, 0.3, 0), yaw, Material.CRYING_OBSIDIAN, 0.4f, 0.12f, 0.4f);
        cube(world, piece(origin, front, 0, 0.46, 0), yaw, Material.CRYING_OBSIDIAN, 0.44f, 0.2f, 0.44f);
        cube(world, piece(origin, front, 0, 0.63, 0), yaw, Material.CRYING_OBSIDIAN, 0.38f, 0.14f, 0.38f);
        cube(world, piece(origin, front, 0, 0.75, 0), yaw, Material.OBSIDIAN, 0.28f, 0.1f, 0.28f);
        cube(world, piece(origin, front, 0, 0.835, 0), yaw, Material.AMETHYST_BLOCK, 0.14f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.43, 0), yaw, Material.AMETHYST_BLOCK, 0.452f, 0.035f, 0.452f);
    }
}
