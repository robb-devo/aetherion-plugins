package de.aetherion.core.entity;

import de.aetherion.core.AetherEntities;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/**
 * Display-entity lifecycle helpers. Armor stands used for jump pads / forge /
 * area markers are never discarded here.
 */
public final class DisplayEntities {

    public static final String JUMP_PAD_TAG = "aetherion_jump_pad";
    /**
     * Quests TalkUx bubble / chips / card / bark panels. Isle janitors and
     * {@link #discard} must leave these alone — they are short-lived, per-viewer UI.
     */
    public static final String TALK_UI_TAG = "ae_talk_ui";

    private DisplayEntities() {
    }

    /**
     * Remove a leaked hologram / FX display. Never touches {@link ArmorStand}.
     */
    public static void discard(Entity entity) {
        if (entity == null || entity instanceof ArmorStand || isProtected(entity)) {
            return;
        }
        try {
            entity.remove();
        } catch (Throwable ignored) {
        }
    }

    /** Jump-pad labels, talk UI, pets, and armor stands must survive janitors. */
    public static boolean isProtected(Entity entity) {
        if (entity == null || entity instanceof ArmorStand) {
            return true;
        }
        if (AetherEntities.isPet(entity)) {
            return true;
        }
        return isJumpPadLabel(entity) || isTalkUi(entity);
    }

    public static boolean isJumpPadLabel(Entity entity) {
        if (!(entity instanceof TextDisplay)) {
            return false;
        }
        for (String tag : entity.getScoreboardTags()) {
            if (tag != null && tag.startsWith(JUMP_PAD_TAG)) {
                return true;
            }
        }
        return false;
    }

    /** In-world TalkUx panels ({@link #TALK_UI_TAG}). */
    public static boolean isTalkUi(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TALK_UI_TAG);
    }

    public static boolean isHologram(Entity entity) {
        return entity instanceof TextDisplay || entity instanceof ItemDisplay;
    }

    public static TextDisplay findTagged(Location at, String tag, double radius) {
        if (at == null || at.getWorld() == null || tag == null || tag.isBlank()) {
            return null;
        }
        TextDisplay found = null;
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(tag)) {
                continue;
            }
            if (found == null) {
                found = display;
            } else {
                discard(display);
            }
        }
        return found;
    }
}
