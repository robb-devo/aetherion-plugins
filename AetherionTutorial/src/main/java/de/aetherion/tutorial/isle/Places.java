package de.aetherion.tutorial.isle;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * What the corner of the screen calls the spot a player stands on. Items names areas for the HUD and the TAB list
 * and knows only the main island's; here the isle answers for its own ground, from the same named spots the
 * lessons use. Place names stay as they are in every language (as on the mainland).
 */
final class Places {

    private record Place(String name, double x, double z, double radius) {
    }

    private static final String ISLE = "Morningbell";

    private final Isle isle;
    private final List<Place> places = new ArrayList<>();

    Places(Isle isle) {
        this.isle = isle;
        add("The Landing", "arrival", 24.0);
        add("Burr's Camp", "burr", 24.0);
        add("The Overlook", "overlook", 8.0);
        add("The Quarry", "quarry_floor", 22.0);
        add("The Forge", "ansel", 15.0);
        add("The Village", "village_square", 24.0);
        add("The Old Watch", "drill_center", 18.0);
        add("The Flat", "clanger", 19.0);
        add("The Harbour", "harbour", 18.0);
        add("The Harbour", "pier_head", 14.0);
        add("The Lighthouse", "lighthouse_door", 14.0);
        add("Downs Farm", "farm_yard", 28.0);
        add("The Windmill", "mill_door", 9.0);
    }

    private void add(String name, String mark, double radius) {
        Location at = isle.marks().at(isle.world(), mark);
        if (at != null) {
            places.add(new Place(name, at.getX(), at.getZ(), radius));
        }
    }

    /** The name of the place at {@code at}; the island's own name between places; null in any other world. */
    String nameAt(Location at) {
        if (at == null || at.getWorld() != isle.world()) {
            return null;
        }
        Place best = null;
        double bestShare = 1.0;
        for (Place place : places) {
            double share = Math.hypot(at.getX() - place.x(), at.getZ() - place.z()) / place.radius();
            if (share < bestShare) {
                bestShare = share;
                best = place;
            }
        }
        return best == null ? ISLE : best.name();
    }

    void register() {
        try {
            de.aetherion.items.world.AreaService.placeNamer(this::nameAt);
        } catch (LinkageError | RuntimeException ex) {
            // an Items build without the hook: the corner says "Wilderness" until Items is updated
            isle.plugin().getLogger().info("Items has no place-name hook yet: the HUD keeps its own area names.");
        }
    }

    void unregister() {
        try {
            de.aetherion.items.world.AreaService.placeNamer(null);
        } catch (LinkageError | RuntimeException ignored) {
            // nothing was registered
        }
    }
}
