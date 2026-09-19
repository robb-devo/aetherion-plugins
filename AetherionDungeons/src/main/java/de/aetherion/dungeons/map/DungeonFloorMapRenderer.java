package de.aetherion.dungeons.map;

import de.aetherion.dungeons.instance.DungeonLayout;
import de.aetherion.dungeons.instance.DungeonSession;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

public final class DungeonFloorMapRenderer extends MapRenderer {

    private static final byte VOID = MapPalette.matchColor(new Color(12, 12, 16));
    private static final byte CORRIDOR = MapPalette.matchColor(new Color(72, 72, 78));
    private static final byte LOBBY = MapPalette.matchColor(new Color(96, 96, 102));
    private static final byte FIGHT = MapPalette.matchColor(new Color(158, 48, 48));
    private static final byte OPEN = MapPalette.matchColor(new Color(176, 96, 40));
    private static final byte CLEARED = MapPalette.matchColor(new Color(52, 148, 72));
    private static final byte LOOT_FIGHT = MapPalette.matchColor(new Color(196, 156, 48));
    private static final byte LOOT_CLEAR = MapPalette.matchColor(new Color(80, 176, 96));
    private static final byte BOSS_LOCK = MapPalette.matchColor(new Color(56, 32, 72));
    private static final byte BOSS_OPEN = MapPalette.matchColor(new Color(132, 56, 176));
    private static final byte BOSS_DEAD = MapPalette.matchColor(new Color(88, 88, 102));
    private static final byte EXIT = MapPalette.matchColor(new Color(48, 32, 64));
    private static final byte WALL = MapPalette.matchColor(new Color(28, 28, 32));

    private final DungeonSession session;

    public DungeonFloorMapRenderer(DungeonSession session) {
        super(true);
        this.session = session;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        DungeonLayout layout = session.layout();
        if (layout == null) {
            return;
        }
        DungeonLayout.Room world = layout.bounds();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                canvas.setPixel(x, z, VOID);
            }
        }
        for (DungeonLayout.Link link : layout.links()) {
            fillRoom(canvas, world, link.corridorBounds(), CORRIDOR, false);
        }
        fillRoom(canvas, world, layout.lobby(), LOBBY, true);
        for (DungeonLayout.CombatRoom room : layout.combatRooms()) {
            boolean cleared = session.isRoomCleared(room.index());
            boolean unlocked = session.isUnlocked(room.index()) && session.started();
            byte fill;
            if (cleared) {
                fill = room.lootRoom() ? LOOT_CLEAR : CLEARED;
            } else if (unlocked) {
                fill = room.lootRoom() ? LOOT_FIGHT : OPEN;
            } else {
                fill = room.lootRoom() ? LOOT_FIGHT : FIGHT;
            }
            fillRoom(canvas, world, room.bounds(), fill, true);
        }
        byte boss = session.bossDead() ? BOSS_DEAD : (session.bossReleased() ? BOSS_OPEN : BOSS_LOCK);
        fillRoom(canvas, world, layout.boss(), boss, true);
        fillRoom(canvas, world, layout.exit(), EXIT, true);

        int px = worldToPixel(player.getLocation().getX(), world.minX(), world.maxX());
        int pz = worldToPixel(player.getLocation().getZ(), world.minZ(), world.maxZ());
        byte dir = yawToDir(player.getLocation().getYaw());
        MapCursorCollection cursors = new MapCursorCollection();
        cursors.addCursor(new MapCursor(pixelToCursor(px), pixelToCursor(pz), dir, MapCursor.Type.PLAYER, true));
        canvas.setCursors(cursors);
    }

    private static void fillRoom(MapCanvas canvas, DungeonLayout.Room world, DungeonLayout.Room room, byte color, boolean outline) {
        int x1 = worldToPixel(room.minX(), world.minX(), world.maxX());
        int x2 = worldToPixel(room.maxX(), world.minX(), world.maxX());
        int z1 = worldToPixel(room.minZ(), world.minZ(), world.maxZ());
        int z2 = worldToPixel(room.maxZ(), world.minZ(), world.maxZ());
        int minX = Math.max(0, Math.min(x1, x2));
        int maxX = Math.min(127, Math.max(x1, x2));
        int minZ = Math.max(0, Math.min(z1, z2));
        int maxZ = Math.min(127, Math.max(z1, z2));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = outline && (x == minX || x == maxX || z == minZ || z == maxZ);
                canvas.setPixel(x, z, edge ? WALL : color);
            }
        }
    }

    private static int worldToPixel(double value, int min, int max) {
        double span = Math.max(1, max - min);
        return (int) Math.round((value - min) / span * 127.0);
    }

    private static byte pixelToCursor(int pixel) {
        int cursor = pixel * 2 - 128;
        return (byte) Math.max(-128, Math.min(127, cursor));
    }

    private static byte yawToDir(float yaw) {
        int dir = (int) Math.round(yaw / 22.5f) & 15;
        return (byte) dir;
    }
}
