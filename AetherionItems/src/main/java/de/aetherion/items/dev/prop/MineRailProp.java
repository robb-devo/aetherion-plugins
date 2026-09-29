package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Mining — straight two-block track section matching the ore cart rails. */
public final class MineRailProp extends AmbientProp {

    public MineRailProp(AetherionItems plugin) {
        super(plugin, "mine_rail");
    }

    @Override
    public String id() {
        return "mine_rail";
    }

    @Override
    protected String title() {
        return "§7Mine Rail";
    }

    @Override
    protected List<String> loreLines() {
        return List.of(
                "§8Mining",
                "§7Straight track section.",
                "§7Place every 2 blocks to path."
        );
    }

    @Override
    protected Material icon() {
        return Material.RAIL;
    }

    @Override
    protected float hitWidth() {
        return 0.9f;
    }

    @Override
    protected float hitHeight() {
        return 0.2f;
    }

    @Override
    protected void buildMesh(World world, Location origin, BlockFace front, float yaw) {
        // Sleepers — 0.5 pitch so sections placed 2 blocks apart tile seamlessly
        cube(world, piece(origin, front, 0, 0.035, 0.75), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.035, 0.25), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.035, -0.25), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        cube(world, piece(origin, front, 0, 0.035, -0.75), yaw, Material.SPRUCE_PLANKS, 0.84f, 0.07f, 0.14f);
        // Rails
        cube(world, piece(origin, front, 0.3, 0.095, 0), yaw, Material.IRON_BLOCK, 0.05f, 0.05f, 2.0f);
        cube(world, piece(origin, front, -0.3, 0.095, 0), yaw, Material.IRON_BLOCK, 0.05f, 0.05f, 2.0f);
    }
}
