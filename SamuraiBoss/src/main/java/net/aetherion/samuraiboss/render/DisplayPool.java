package net.aetherion.samuraiboss.render;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wiederverwendbare Display-Entities ("Pooling").
 *
 * <p>Effekte wie Blütenblätter, Schnittlinien oder Splitter leben oft nur ein paar Ticks.
 * Statt jedes Mal ein neues Entity zu spawnen (Spawn-Paket für jeden Spieler) wird ein freies,
 * unsichtbares Display umgesetzt und neu konfiguriert. Freigegebene Displays werden auf
 * Skalierung 0 gesetzt und warten im Pool.</p>
 *
 * <p>Zusätzlich gilt ein hartes Limit für aktive Displays – wird es erreicht, liefern die
 * Methoden {@code null} und der Effekt lässt das Teil einfach weg (Performance-Schutz).</p>
 */
public final class DisplayPool {

    private final String arenaId;
    private final int maxActive;
    private final int maxFree;

    private final Deque<BlockDisplay> freeBlocks = new ArrayDeque<>();
    private final Deque<ItemDisplay> freeItems = new ArrayDeque<>();
    private final Deque<TextDisplay> freeTexts = new ArrayDeque<>();
    private final Set<Display> active = new HashSet<>();
    /** Alle je gespawnten Displays (aktiv + frei) – für das finale Aufräumen. */
    private final Set<Entity> all = new HashSet<>();

    public DisplayPool(String arenaId, int maxActive, int maxFree) {
        this.arenaId = arenaId;
        this.maxActive = maxActive;
        this.maxFree = maxFree;
    }

    public boolean hasCapacity(int n) {
        return active.size() + n <= maxActive;
    }

    public int activeCount() {
        return active.size();
    }

    public int totalCount() {
        return all.size();
    }

    private static void reset(Display d) {
        d.setGlowing(false);
        d.setGlowColorOverride(null);
        d.setBrightness(null);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(1.0f);
        DisplayFactory.snap(d, DisplayFactory.HIDDEN);
    }

    private static boolean moveTo(Entity e, Location loc) {
        Location cur = e.getLocation();
        if (cur.getWorld() != loc.getWorld()) return false;
        if (cur.distanceSquared(loc) > 1e-6) {
            Location l = loc.clone();
            l.setYaw(0f);
            l.setPitch(0f);
            return e.teleport(l);
        }
        return true;
    }

    public BlockDisplay block(Location loc, BlockData data) {
        if (active.size() >= maxActive) return null;
        BlockDisplay d;
        while ((d = freeBlocks.poll()) != null) {
            if (d.isValid() && moveTo(d, loc)) {
                reset(d);
                if (!d.getBlock().equals(data)) d.setBlock(data);
                active.add(d);
                return d;
            }
            all.remove(d);
            d.remove();
        }
        d = DisplayFactory.block(loc, data, "fx", arenaId);
        all.add(d);
        active.add(d);
        return d;
    }

    public ItemDisplay item(Location loc, ItemStack stack, ItemDisplay.ItemDisplayTransform mode) {
        if (active.size() >= maxActive) return null;
        ItemDisplay d;
        while ((d = freeItems.poll()) != null) {
            if (d.isValid() && moveTo(d, loc)) {
                reset(d);
                d.setItemStack(stack);
                d.setItemDisplayTransform(mode);
                active.add(d);
                return d;
            }
            all.remove(d);
            d.remove();
        }
        d = DisplayFactory.item(loc, stack, mode, "fx", arenaId);
        all.add(d);
        active.add(d);
        return d;
    }

    public TextDisplay text(Location loc) {
        if (active.size() >= maxActive) return null;
        TextDisplay d;
        while ((d = freeTexts.poll()) != null) {
            if (d.isValid() && moveTo(d, loc)) {
                reset(d);
                d.setTextOpacity((byte) -1);
                d.setShadowed(false);
                d.setSeeThrough(false);
                active.add(d);
                return d;
            }
            all.remove(d);
            d.remove();
        }
        d = DisplayFactory.text(loc, "fx", arenaId);
        all.add(d);
        active.add(d);
        return d;
    }

    /** Gibt ein Display zurück in den Pool (wird sofort unsichtbar). */
    public void release(Display d) {
        if (d == null) return;
        if (!active.remove(d)) return;
        if (!d.isValid()) {
            all.remove(d);
            return;
        }
        DisplayFactory.snap(d, DisplayFactory.HIDDEN);
        d.setGlowing(false);
        if (d instanceof BlockDisplay b && freeBlocks.size() < maxFree) freeBlocks.add(b);
        else if (d instanceof ItemDisplay i && freeItems.size() < maxFree) freeItems.add(i);
        else if (d instanceof TextDisplay t && freeTexts.size() < maxFree) freeTexts.add(t);
        else {
            all.remove(d);
            d.remove();
        }
    }

    /** Übernimmt ein von außen gespawntes Display in die Verwaltung (wird beim Aufräumen entfernt). */
    public void adopt(Entity e) {
        all.add(e);
    }

    /** Entfernt ALLE Displays dieses Pools aus der Welt. */
    public void clear() {
        List<Entity> copy = new ArrayList<>(all);
        for (Entity e : copy) {
            if (e.isValid()) e.remove();
        }
        all.clear();
        active.clear();
        freeBlocks.clear();
        freeItems.clear();
        freeTexts.clear();
    }

    /** Begrenzt die Zahl wartender Displays (z. B. nach einem großen Effekt-Peak). */
    public void trim(int keepPerType) {
        trimDeque(freeBlocks, keepPerType);
        trimDeque(freeItems, keepPerType);
        trimDeque(freeTexts, keepPerType);
    }

    private <T extends Display> void trimDeque(Deque<T> dq, int keep) {
        while (dq.size() > keep) {
            T d = dq.pollLast();
            all.remove(d);
            if (d != null && d.isValid()) d.remove();
        }
    }
}
