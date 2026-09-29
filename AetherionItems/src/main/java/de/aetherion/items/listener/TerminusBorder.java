package de.aetherion.items.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * The edge of the world, lent to {@link TerminusEdge} for a few seconds.
 *
 * <p>One virtual {@link WorldBorder} shown to every onlooker. The client draws it as its own forcefield: a wall
 * the full height of the world, additive, texture scrolling, frame-smooth while it lerps ΓÇö and it colours it by
 * motion: blue at rest, red while it shrinks, green while it grows. Anyone near it or outside it gets the screen
 * edges tinted red; the warning distance is the dial for that tint. The server never ticks a virtual border, so
 * nothing here can hurt, push or block anyone server-side.
 *
 * <p>The real border is never touched. Every viewer gets their world's border back in {@link #restore()}, and
 * every exit path of the session calls it.
 */
final class TerminusBorder {

    private final World world;
    private final double cx;
    private final double cz;
    private final List<Viewer> viewers = new ArrayList<>();
    private WorldBorder edge;
    private int warning = -1;

    TerminusBorder(World world, double cx, double cz) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
    }

    boolean isShown() {
        return edge != null;
    }

    /** Hands the edge to every candidate who is not already looking at some other virtual border. */
    void show(Collection<Player> candidates, double size) {
        if (edge != null) {
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(cx, cz);
        border.setSize(clamp(border, size));
        border.setWarningTime(0);
        border.setWarningDistance(0);
        warning = 0;
        edge = border;
        for (Player player : candidates) {
            if (!player.isOnline() || player.getWorld() != world || player.getWorldBorder() != null) {
                continue;
            }
            try {
                player.setWorldBorder(border);
                viewers.add(new Viewer(player, signed(player.getLocation()) < 0));
            } catch (Throwable ignored) {
            }
        }
    }

    boolean sees(Player player) {
        for (Viewer viewer : viewers) {
            if (viewer.player.equals(player)) {
                return true;
            }
        }
        return false;
    }

    /** Client-interpolated move to {@code size} over {@code millis}; the colour follows the direction. */
    void lerp(double size, long millis) {
        if (edge == null) {
            return;
        }
        double target = clamp(edge, size);
        if (millis <= 0) {
            edge.setSize(target);
        } else {
            edge.setSize(target, TimeUnit.MILLISECONDS, millis);
        }
    }

    double size() {
        return edge == null ? 0.0 : edge.getSize();
    }

    /** Blocks between {@code at} and the edge; negative once {@code at} is outside the world. */
    double signed(Location at) {
        double half = size() / 2.0;
        return half - Math.max(Math.abs(at.getX() - cx), Math.abs(at.getZ() - cz));
    }

    /** Red screen edges for everyone inside and closer than {@code blocks}; outside is always fully red. */
    void warn(int blocks) {
        if (edge == null) {
            return;
        }
        int value = Math.max(0, blocks);
        if (value != warning) {
            warning = value;
            edge.setWarningDistance(value);
        }
    }

    /** Reports every viewer the edge has just passed; {@code out} is true when they are now outside the world. */
    void sweep(BiConsumer<Player, Boolean> crossed) {
        Iterator<Viewer> it = viewers.iterator();
        while (it.hasNext()) {
            Viewer viewer = it.next();
            Player player = viewer.player;
            if (!player.isOnline()) {
                it.remove();
                continue;
            }
            if (player.getWorld() != world) {
                release(player);
                it.remove();
                continue;
            }
            boolean out = signed(player.getLocation()) < 0;
            if (out != viewer.outside) {
                viewer.outside = out;
                crossed.accept(player, out);
            }
        }
    }

    void restore() {
        for (Viewer viewer : viewers) {
            release(viewer.player);
        }
        viewers.clear();
        edge = null;
    }

    private void release(Player player) {
        try {
            if (player.isOnline() && edge != null && player.getWorldBorder() == edge) {
                player.setWorldBorder(null);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Distance to the world's own border, the one that lives thirty million blocks out. */
    static double realDistance(Player player) {
        WorldBorder real = player.getWorld().getWorldBorder();
        Location center = real.getCenter();
        Location at = player.getLocation();
        double half = real.getSize() / 2.0;
        return half - Math.max(Math.abs(at.getX() - center.getX()), Math.abs(at.getZ() - center.getZ()));
    }

    private static double clamp(WorldBorder border, double size) {
        return Math.max(1.0, Math.min(border.getMaxSize(), size));
    }

    private static final class Viewer {
        final Player player;
        boolean outside;

        Viewer(Player player, boolean outside) {
            this.player = player;
            this.outside = outside;
        }
    }
}
