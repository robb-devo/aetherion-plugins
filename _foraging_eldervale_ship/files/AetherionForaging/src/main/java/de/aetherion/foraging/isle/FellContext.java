package de.aetherion.foraging.isle;

import org.bukkit.Location;

/**
 * One felled tree, as the chop listener saw it: where it stood ({@code anchor} = the stump), the top of
 * its crown (where Crown Finds let go), how big it was, which wood it paid, and how it came down.
 */
public record FellContext(
        Location anchor,
        Location crown,
        int logs,
        Wood wood,
        boolean perfect,
        int streak,
        boolean cleaver,
        boolean titan
) {
}
