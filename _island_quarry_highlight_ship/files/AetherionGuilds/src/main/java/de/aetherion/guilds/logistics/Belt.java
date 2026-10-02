package de.aetherion.guilds.logistics;

import org.bukkit.block.BlockFace;

/** One conveyor tile: a rail at (x, y, z) that moves cargo toward {@code dir}. */
public final class Belt {

    private final int x;
    private final int y;
    private final int z;
    private BlockFace dir;

    public Belt(int x, int y, int z, BlockFace dir) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dir = dir;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockFace dir() {
        return dir;
    }

    public void setDir(BlockFace dir) {
        this.dir = dir;
    }

    public int nextX() {
        return x + dir.getModX();
    }

    public int nextZ() {
        return z + dir.getModZ();
    }

    public long key() {
        return LogisticsService.pos(x, y, z);
    }

    public long nextKey() {
        return LogisticsService.pos(nextX(), y, nextZ());
    }

    public String serialize() {
        return x + "," + y + "," + z + "," + dir.name();
    }

    public static Belt parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            BlockFace face = BlockFace.valueOf(parts[3].trim());
            if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST) {
                return null;
            }
            return new Belt(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), face);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
