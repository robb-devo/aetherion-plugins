package net.aetherion.samuraiboss.render;

import net.aetherion.samuraiboss.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Erzeugt Display-Entities mit den Standard-Einstellungen des Plugins:
 * <ul>
 *   <li><b>nicht persistent</b> – werden nie in die Welt gespeichert. Selbst nach einem Server-Crash
 *       bleibt kein einziges Effekt-Display liegen.</li>
 *   <li>markiert (PersistentData), damit sie jederzeit gefunden und entfernt werden können</li>
 *   <li>unsichtbar gestartet (Skalierung 0), bis der Aufrufer die echte Transformation setzt</li>
 * </ul>
 */
public final class DisplayFactory {

    public static final Transformation HIDDEN = new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0f, 0f, 0f), new Quaternionf());

    private DisplayFactory() {
    }

    private static void base(Display d, String category, String arenaId) {
        d.setPersistent(false);
        Keys.mark(d, category, arenaId);
        d.setTeleportDuration(0);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setTransformation(HIDDEN);
        d.setBillboard(Display.Billboard.FIXED);
        d.setRotation(0f, 0f);
    }

    public static BlockDisplay block(Location loc, BlockData data, String category, String arenaId) {
        Location l = loc.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l.getWorld().spawn(l, BlockDisplay.class, d -> {
            base(d, category, arenaId);
            d.setBlock(data);
        });
    }

    public static ItemDisplay item(Location loc, ItemStack stack, ItemDisplay.ItemDisplayTransform mode,
                                   String category, String arenaId) {
        Location l = loc.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            base(d, category, arenaId);
            d.setItemStack(stack);
            d.setItemDisplayTransform(mode);
        });
    }

    public static TextDisplay text(Location loc, String category, String arenaId) {
        Location l = loc.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l.getWorld().spawn(l, TextDisplay.class, d -> {
            base(d, category, arenaId);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setLineWidth(400);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
        });
    }

    /**
     * Unsichtbarer Anker (ItemDisplay ohne Item). Modell-Teile reiten als Passagiere darauf,
     * so dass eine Bewegung des ganzen Modells nur EIN Paket kostet.
     */
    public static ItemDisplay anchor(Location loc, String category, String arenaId, int teleportDuration) {
        Location l = loc.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            base(d, category, arenaId);
            d.setTeleportDuration(teleportDuration);
        });
    }

    /** Setzt eine Transformation sofort (ohne Interpolation). */
    public static void snap(Display d, Transformation t) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
        d.setTransformation(t);
    }

    /** Setzt eine Transformation, die der Client über "ticks" Ticks weich interpoliert. */
    public static void lerp(Display d, Transformation t, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, ticks));
        d.setTransformation(t);
    }
}
