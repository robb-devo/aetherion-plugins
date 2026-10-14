package net.aetherion.samuraiboss.arena;

import net.aetherion.samuraiboss.arena.dojo.BuildPlan;
import net.aetherion.samuraiboss.arena.dojo.El;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Vector3f;

import java.io.File;

/**
 * Ein gebautes Dojo in einer Welt. Alle Bauplan-Koordinaten sind relativ zum Ursprung
 * (ox, oy, oz) = Mitte des Hofes auf Laufhöhe.
 */
public final class Arena {

    public enum State {
        /** bereit für einen Kampf */
        IDLE,
        /** wird gerade gebaut */
        BUILDING,
        /** Kampf läuft */
        FIGHT,
        /** baut sich nach dem Kampf wieder auf */
        REBUILDING,
        /** wird abgerissen */
        REMOVING
    }

    public final String id;
    public final String worldName;
    public final int ox, oy, oz;
    public final BuildPlan plan;
    private final File dir;
    public State state = State.IDLE;
    public boolean built;
    public DecoManager deco;
    /** Snapshot zu Kampfbeginn (im Speicher, zusätzlich auf Platte). */
    public BlockSnapshot fightSnapshot;
    /** Aktueller Kampf (oder null). */
    public Object fight;

    public Arena(String id, String worldName, int ox, int oy, int oz, BuildPlan plan, File dir) {
        this.id = id;
        this.worldName = worldName;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.plan = plan;
        this.dir = dir;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public File dir() {
        return dir;
    }

    public File prebuildFile() {
        return new File(dir, "prebuild.snap");
    }

    public File fightFile() {
        return new File(dir, "fight.snap");
    }

    // ---------------------------------------------------------------- Koordinaten

    public int minX() {
        return ox + plan.minX;
    }

    public int minY() {
        return oy + plan.minY;
    }

    public int minZ() {
        return oz + plan.minZ;
    }

    public int maxX() {
        return ox + plan.maxX;
    }

    public int maxY() {
        return oy + plan.maxY;
    }

    public int maxZ() {
        return oz + plan.maxZ;
    }

    /** Absolute Position für relative Bauplan-Koordinaten. */
    public Location loc(double rx, double ry, double rz) {
        return new Location(world(), ox + rx, oy + ry, oz + rz);
    }

    /** Ein benannter Punkt des Bauplans (z. B. "boss", "bell", "ult"). */
    public Location point(String name) {
        Vector3f v = plan.point(name);
        return loc(v.x, v.y, v.z);
    }

    public boolean inRegion(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ();
    }

    public boolean inRegion(String world, int x, int y, int z) {
        return world.equals(worldName) && x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ();
    }

    /** Innerhalb der Mauern (Spielbereich) – Höhe großzügig. */
    public boolean insideWalls(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        double rx = l.getX() - ox, ry = l.getY() - oy, rz = l.getZ() - oz;
        return ry > -4 && ry < 40 && plan.insideWalls(rx, rz);
    }

    /** Bauteil an einer absoluten Blockposition (oder null, wenn nicht Teil des Plans). */
    public El elementAt(int x, int y, int z) {
        BuildPlan.Placement p = plan.get(x - ox, y - oy, z - oz);
        return p == null ? null : p.element();
    }

    public Location center() {
        return point("center");
    }

    @Override
    public String toString() {
        return id + " @ " + worldName + " " + ox + "," + oy + "," + oz + " (" + state + ")";
    }
}
