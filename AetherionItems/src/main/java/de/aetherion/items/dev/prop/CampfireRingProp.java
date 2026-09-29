package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Forage — stone fire ring with a pot on a spit. */
public final class CampfireRingProp extends AmbientProp {

    public CampfireRingProp(AetherionItems plugin) {
        super(plugin, "campfire_ring");
    }

    @Override
    public String id() {
        return "campfire_ring";
    }

    @Override
    protected String title() {
        return "§6Campfire Ring";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Forage",
                "§7Stone fire ring with a",
                "§7cooking pot over the coals."
        );
    }

    @Override
    protected Material icon() {
        return Material.CAMPFIRE;
    }

    @Override
    protected float hitWidth() {
        return 1.3f;
    }

    @Override
    protected float hitHeight() {
        return 0.85f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        cube(world, piece(origin, front, 0, 0.3, 0), yaw, Material.CAMPFIRE, 0.6f, 0.6f, 0.6f);
        // Stone ring
        cube(world, piece(origin, front, 0.5, 0.07, 0), yaw, Material.COBBLESTONE, 0.22f, 0.14f, 0.24f);
        cube(world, piece(origin, front, 0.25, 0.06, 0.43), yaw, Material.ANDESITE, 0.24f, 0.12f, 0.2f);
        cube(world, piece(origin, front, -0.25, 0.08, 0.43), yaw, Material.STONE, 0.22f, 0.16f, 0.22f);
        cube(world, piece(origin, front, -0.5, 0.07, 0), yaw, Material.MOSSY_COBBLESTONE, 0.22f, 0.14f, 0.24f);
        cube(world, piece(origin, front, -0.25, 0.06, -0.43), yaw, Material.COBBLESTONE, 0.24f, 0.12f, 0.2f);
        cube(world, piece(origin, front, 0.25, 0.07, -0.43), yaw, Material.ANDESITE, 0.22f, 0.14f, 0.22f);
        // Spit
        cube(world, piece(origin, front, 0.5, 0.47, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.05f, 0.66f, 0.05f);
        cube(world, piece(origin, front, -0.5, 0.47, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 0.05f, 0.66f, 0.05f);
        cube(world, piece(origin, front, 0, 0.76, 0), yaw, Material.STRIPPED_SPRUCE_WOOD, 1.06f, 0.04f, 0.04f);
        // Hanging pot
        cube(world, piece(origin, front, 0, 0.705, 0), yaw, Material.POLISHED_BLACKSTONE, 0.02f, 0.07f, 0.02f);
        cube(world, piece(origin, front, 0, 0.555, 0), yaw, Material.CAULDRON, 0.24f, 0.24f, 0.24f);
    }
}
