package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Forage — mossy stump with mushrooms. */
public final class MossyStumpProp extends AmbientProp {

    public MossyStumpProp(AetherionItems plugin) {
        super(plugin, "mossy_stump");
    }

    @Override
    public String id() {
        return "mossy_stump";
    }

    @Override
    protected String title() {
        return "§2Mossy Stump";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Forage",
                "§7Cut stump with moss and",
                "§7mushrooms for the undergrowth."
        );
    }

    @Override
    protected Material icon() {
        return Material.MOSS_BLOCK;
    }

    @Override
    protected float hitWidth() {
        return 1.1f;
    }

    @Override
    protected float hitHeight() {
        return 1.15f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Trunk + cut rings
        cube(world, piece(origin, front, 0, 0.4, 0), yaw, Material.SPRUCE_LOG, 0.8f, 0.8f, 0.8f);
        cube(world, piece(origin, front, 0, 0.82, 0), yaw, Material.STRIPPED_SPRUCE_LOG, 0.68f, 0.04f, 0.68f);
        // Root flares
        cube(world, piece(origin, front, 0.44, 0.11, 0.08), yaw, Material.SPRUCE_WOOD, 0.14f, 0.22f, 0.3f);
        cube(world, piece(origin, front, -0.06, 0.09, 0.44), yaw, Material.SPRUCE_WOOD, 0.3f, 0.18f, 0.14f);
        cube(world, piece(origin, front, -0.3, 0.08, -0.43), yaw, Material.SPRUCE_WOOD, 0.24f, 0.16f, 0.14f);
        // Moss cap draped over one side
        cube(world, piece(origin, front, -0.17, 0.855, -0.1), yaw, Material.MOSS_BLOCK, 0.52f, 0.03f, 0.46f);
        cube(world, piece(origin, front, -0.41, 0.63, -0.1), yaw, Material.MOSS_BLOCK, 0.04f, 0.44f, 0.46f);
        // Toadstool
        cube(world, piece(origin, front, 0.18, 0.89, 0.16), yaw, Material.MUSHROOM_STEM, 0.06f, 0.1f, 0.06f);
        cube(world, piece(origin, front, 0.18, 0.97, 0.16), yaw, Material.RED_MUSHROOM_BLOCK, 0.2f, 0.07f, 0.2f);
        // Bracket fungus
        cube(world, piece(origin, front, 0.12, 0.46, 0.44), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.26f, 0.05f, 0.1f);
        cube(world, piece(origin, front, -0.06, 0.32, 0.43), yaw, Material.BROWN_MUSHROOM_BLOCK, 0.18f, 0.04f, 0.08f);
    }
}
