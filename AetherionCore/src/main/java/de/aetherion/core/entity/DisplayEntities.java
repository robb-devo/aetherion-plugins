package de.aetherion.core.entity;

import de.aetherion.core.AetherEntities;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/**
 * Shared display-entity lifecycle helpers used by Foraging holograms/nametags
 * (and other plugins that spawn {@link TextDisplay}/{@link ItemDisplay}).
 *
 * <p>Armor stands used for jump pads / forge / area markers are never discarded
 * here. Hub island-pad labels and FancyNPC-owned Aetherion holograms are
 * treated as protected so forage janitors cannot wipe them.
 */
public final class DisplayEntities {

    public static final String JUMP_PAD_TAG = "aetherion_jump_pad";

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

    /** Jump-pad labels, pets, armor stands, and other Aetherion holograms must survive janitors. */
    public static boolean isProtected(Entity entity) {
        if (entity == null || entity instanceof ArmorStand) {
            return true;
        }
        if (AetherEntities.isPet(entity) || AetherEntities.isSystemOwned(entity)) {
            return true;
        }
        if (isJumpPadLabel(entity)) {
            return true;
        }
        return hasForeignAetherionData(entity);
    }

    public static boolean isJumpPadLabel(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return false;
        }
        for (String tag : display.getScoreboardTags()) {
            if (tag != null && tag.startsWith(JUMP_PAD_TAG)) {
                return true;
            }
        }
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(display.text())
                .toLowerCase(Locale.ROOT)
                .replace('\n', ' ')
                .trim();
        if (plain.isEmpty()) {
            return false;
        }
        boolean arrow = plain.contains("→") || plain.contains("➜") || plain.contains("->");
        return plain.contains("jump pad")
                || plain.contains("junp pad")
                || plain.contains("forage isle")
                || (arrow && (plain.contains("harbour") || plain.contains("harbor")
                || plain.contains("eldervale") || plain.contains("forage") || plain.contains("shabby")))
                || plain.contains("forage island jump")
                || plain.contains("eldervale jump")
                || plain.contains("shabby mine jump")
                || plain.contains("harbour jump")
                || plain.contains("harbor jump");
    }

    public static boolean isHologram(Entity entity) {
        return entity instanceof TextDisplay || entity instanceof ItemDisplay;
    }

    /**
     * Return one living tagged hologram at {@code at}, discarding extra copies of the
     * same tag. Protected displays (jump pads, etc.) are never discarded.
     */
    public static TextDisplay findTagged(Location at, String tag, double radius) {
        if (at == null || at.getWorld() == null || tag == null || tag.isBlank()) {
            return null;
        }
        TextDisplay found = null;
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            if (!display.isValid() || display.isDead()) {
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

    /** Quest/hub/item holograms carry plugin PDC; forage-owned labels typically do not. */
    private static boolean hasForeignAetherionData(Entity entity) {
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            String namespace = key.getNamespace();
            if (namespace == null) {
                continue;
            }
            if (namespace.startsWith("aetherion") || namespace.equals("aethermobs")
                    || namespace.equals("bossengine")) {
                return true;
            }
        }
        return false;
    }
}
