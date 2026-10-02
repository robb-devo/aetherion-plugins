package de.aetherion.hub.origin;

import de.aetherion.hub.model.HubSpawn;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The only doorway from the existing Hub classes (HubService, IslandLaunchPads, SpawnGotoCommand) into Origin.
 * Every call is a no-op when Origin isn't running and swallows its own errors, so the old paths behave exactly
 * as before whatever happens here.
 */
public final class OriginHooks {

    private static volatile OriginIsle isle;

    private OriginHooks() {
    }

    static void bind(OriginIsle origin) {
        isle = origin;
    }

    static void unbind(OriginIsle origin) {
        if (isle == origin) {
            isle = null;
        }
    }

    public static OriginIsle isle() {
        return isle;
    }

    /** After a successful /spawn, /harbour, menu teleport … */
    public static void arrived(Player player, HubSpawn spawn) {
        OriginIsle origin = isle;
        if (origin == null || !origin.running() || player == null || spawn == null) {
            return;
        }
        try {
            origin.compass().arrived(player, spawn);
        } catch (Throwable ignored) {
        }
    }

    public static void padLaunched(Player player, String padId) {
        OriginIsle origin = isle;
        if (origin == null || !origin.running() || player == null) {
            return;
        }
        try {
            origin.pads().launched(player, padId);
        } catch (Throwable ignored) {
        }
    }

    public static void padLanded(Player player, String padId) {
        OriginIsle origin = isle;
        if (origin == null || !origin.running() || player == null) {
            return;
        }
        try {
            origin.pads().landed(player, padId);
        } catch (Throwable ignored) {
        }
    }

    public static void padSealed(Player player, String padId) {
        OriginIsle origin = isle;
        if (origin == null || !origin.running() || player == null) {
            return;
        }
        try {
            origin.pads().sealed(player, padId);
        } catch (Throwable ignored) {
        }
    }

    /** HubService#wipePlayer (Items Full Player Wipe, DEV). */
    public static void wipe(UUID id) {
        OriginIsle origin = isle;
        if (origin == null || id == null) {
            return;
        }
        try {
            origin.wipe(id);
        } catch (Throwable ignored) {
        }
    }

    /** A line for "/mines" etc. when the camp is still locked: where it is and how to point the compass. */
    public static String lockedHint(Player player, String spawnId) {
        OriginIsle origin = isle;
        if (origin == null || !origin.running() || player == null || spawnId == null) {
            return null;
        }
        try {
            return origin.compass().lockedHint(player, spawnId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
