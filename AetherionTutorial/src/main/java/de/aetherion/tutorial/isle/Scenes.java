package de.aetherion.tutorial.isle;

import de.aetherion.core.exile.CineCam;
import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The three moments the isle takes the wheel: the flight in before sunrise, the sunrise the first bell brings, and
 * the ferry casting off. Everything else on Morningbell is the player's own to walk.
 *
 * <p>A flight is a handful of keys (where the lens is, what it looks at, when); the lens glides through them on a
 * spline and eases in and out. Sunrise is no flight at all: the player rang the bell, so the player watches it
 * happen from where they stand.
 */
final class Scenes {

    private static final Particle.DustOptions BELL_GOLD = new Particle.DustOptions(Color.fromRGB(255, 214, 120), 1.4f);
    private static final Particle.DustOptions BELL_PALE = new Particle.DustOptions(Color.fromRGB(255, 240, 200), 1.0f);

    /** One key of a flight: at tick {@code at} the lens is at {@code pos} and looks at {@code look}. */
    private record Key(int at, Vector pos, Vector look) {
    }

    private final class Flight {
        final Player player;
        final Session session;
        final List<Key> keys = new ArrayList<>();
        final Map<Integer, Runnable> cues = new HashMap<>();
        /** Where the player stands when the flight is over (null = the caller moves them on). */
        Location feet;
        Runnable done;
        CineCam cam;
        int tick = -1;
        int waited;
        boolean white;
        boolean keepLens;

        Flight(Player player, Session session) {
            this.player = player;
            this.session = session;
        }

        void key(int at, double x, double y, double z, double lx, double ly, double lz) {
            keys.add(new Key(at, new Vector(x, y, z), new Vector(lx, ly, lz)));
        }

        void cue(int at, Runnable what) {
            cues.merge(at, what, (a, b) -> () -> {
                a.run();
                b.run();
            });
        }

        int length() {
            return keys.get(keys.size() - 1).at();
        }
    }

    private static final class Sunrise {
        final Session session;
        final Runnable done;
        int tick;

        Sunrise(Session session, Runnable done) {
            this.session = session;
            this.done = done;
        }
    }

    private final TutorialPlugin plugin;
    private final Isle isle;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Sunrise> sunrises = new HashMap<>();
    /** Players the ferry is carrying off: tick the hop was asked for (to notice a crossing that never happened). */
    private final Map<UUID, Long> sailing = new HashMap<>();

    Scenes(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
    }

    // ------------------------------------------------------------------ the flight in

    /**
     * Before sunrise: in over the dark water, past the harbour lanterns and the sleeping village, up to the bell
     * hill, and into the player's own eyes on the Landing.
     */
    void arrive(Player player, Session session, Runnable done) {
        Location landing = isle.marks().at(isle.world(), "arrival");
        Location bell = isle.marks().at(isle.world(), "bell");
        if (landing == null) {
            done.run();
            return;
        }
        if (bell != null) {
            landing.setYaw(yaw(landing, bell));
            landing.setPitch(-8f);
        }
        if (!plugin.getConfig().getBoolean("isle.flights", true) || player.getGameMode() == GameMode.CREATIVE) {
            player.teleport(landing);
            done.run();
            return;
        }
        Location harbour = at("ferry_deck", 31, 66, 94);
        Location village = at("village_square", 36, 67, 51);
        Location forge = at("wheel", 10, 69, 11);
        Location belfry = at("bell_big", -62, 112, -51);
        Flight f = new Flight(player, session);
        f.feet = landing;
        f.done = done;
        f.white = true;
        double hx = harbour.getX();
        double hz = harbour.getZ();
        // over the sea, low, the harbour lights ahead
        f.key(0, hx + 44, harbour.getY() + 7, hz + 66, hx - 2, harbour.getY() + 3, hz - 4);
        f.key(64, hx + 14, harbour.getY() + 9, hz + 26, hx - 6, harbour.getY() + 4, hz - 18);
        // over the quay and the village roofs
        f.key(118, village.getX() - 2, village.getY() + 19, village.getZ() + 16, forge.getX(), forge.getY() + 8, forge.getZ());
        // up the valley, the hill ahead
        f.key(170, forge.getX() - 6, forge.getY() + 30, forge.getZ() - 14, belfry.getX(), belfry.getY() - 6, belfry.getZ());
        f.key(214, landing.getX() + 13, landing.getY() + 8.5, landing.getZ() + 7, belfry.getX(), belfry.getY() - 5, belfry.getZ());
        // and down into the newcomer's own eyes
        Vector ahead = landing.getDirection().multiply(6.0);
        f.key(246, landing.getX(), landing.getY() + 1.62, landing.getZ(),
                landing.getX() + ahead.getX(), landing.getY() + 1.62 + ahead.getY(), landing.getZ() + ahead.getZ());
        f.cue(24, () -> caption(player, Txt.t(player, "Nobody sails to the mainland unprepared.",
                "Niemand segelt unvorbereitet zum Festland."), 70));
        f.cue(112, () -> caption(player, Txt.t(player, "So every newcomer wakes here first.",
                "Darum wacht jeder Neue zuerst hier auf."), 60));
        f.cue(186, () -> caption(player, Txt.t(player, "It is the hour before sunrise.",
                "Es ist die Stunde vor Sonnenaufgang."), 50));
        f.cue(2, () -> player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_EXIT, SoundCategory.AMBIENT, 0.35f, 0.6f));
        f.cue(40, () -> player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 0.25f, 0.7f));
        start(f);
    }

    // ------------------------------------------------------------------ sunrise

    /** The first bell: three tolls, and the sun comes up over the island while the player looks on. */
    void sunrise(Player player, Session session, Runnable done) {
        if (sunrises.containsKey(player.getUniqueId())) {
            return;
        }
        session.scene = true;
        Guide.hide(player, session);
        sunrises.put(player.getUniqueId(), new Sunrise(session, done));
    }

    private boolean tickSunrise(Player player, Sunrise sun) {
        int t = sun.tick++;
        long dawn = plugin.getConfig().getLong("isle.dawn-time", 22900L);
        long morning = plugin.getConfig().getLong("isle.morning-time", 450L);
        long span = ((morning - dawn) % 24000L + 24000L) % 24000L;
        int rise = 150;
        double u = Math.min(1.0, t / (double) rise);
        double eased = u * u * (3.0 - 2.0 * u);
        player.setPlayerTime((dawn + Math.round(span * eased)) % 24000L, false);
        Location bell = isle.marks().at(isle.world(), "bell");
        Location big = isle.marks().at(isle.world(), "bell_big");
        Location from = bell != null ? bell : player.getLocation();
        if (t == 0 || t == 44 || t == 88) {
            float pitch = t == 0 ? 0.9f : t == 44 ? 0.8f : 0.7f;
            player.playSound(from, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.2f, pitch);
            player.playSound(from, Sound.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 1.0f, pitch);
            if (big != null) {
                // the great bell in the tower answers
                player.playSound(big, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 4.0f, 0.5f);
            }
        }
        // a ring of light runs out from the bell with every toll
        int since = t < 44 ? t : t < 88 ? t - 44 : t - 88;
        if (since < 34 && t < 122) {
            double radius = 1.2 + since * 0.95;
            int points = (int) Math.max(14, radius * 5.0);
            double y = from.getY() - 1.6 + since * 0.05;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0 * i) / points + since * 0.07;
                player.spawnParticle(Particle.DUST, from.getX() + Math.cos(a) * radius, y, from.getZ() + Math.sin(a) * radius,
                        1, 0.0, 0.05, 0.0, 0.0, i % 3 == 0 ? BELL_PALE : BELL_GOLD);
            }
        }
        if (t == 50) {
            Guide.place(player, "MORNINGBELL", Txt.t(player, "§7the island where everyone starts",
                    "§7die Insel, auf der alle anfangen"));
        }
        if (t == 96) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.3f, 1.3f);
        }
        if (t < rise + 10) {
            return true;
        }
        sun.session.morning = true;
        sun.session.scene = false;
        isle.rules().applyTime(player, sun.session);
        sun.done.run();
        return false;
    }

    // ------------------------------------------------------------------ the ferry

    /** Cast off: the island falls behind in the morning light, the mist takes the view, the mainland is next. */
    void depart(Player player, Session session) {
        Location deck = at("ferry_deck", 31, 65, 94);
        Location pier = at("pier_head", 24, 65, 97);
        Location village = at("village_square", 36, 67, 51);
        Location post = isle.marks().at(isle.world(), "bell_post");
        Location seaward = deck.clone();
        seaward.setYaw(0f);
        seaward.setPitch(-4f);
        Flight f = new Flight(player, session);
        f.feet = seaward;
        f.keepLens = true;
        double dx = deck.getX();
        double dz = deck.getZ();
        f.key(0, pier.getX() - 7, pier.getY() + 4.5, pier.getZ() + 6, dx + 1, deck.getY() + 4, dz - 2);
        f.key(60, pier.getX() - 10, pier.getY() + 8, pier.getZ() + 24, dx, deck.getY() + 5, dz - 6);
        f.key(132, pier.getX() - 12, pier.getY() + 17, pier.getZ() + 58, village.getX(), village.getY() + 14, village.getZ());
        Location toll = post != null ? post : pier;
        for (int at : new int[] {4, 34, 64}) {
            f.cue(at, () -> {
                player.playSound(toll, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 4.0f, 0.75f);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 0.5f, 0.75f);
            });
        }
        f.cue(8, () -> player.sendMessage(Txt.t(player, "§3Captain Wim§8: §fCast off! Next stop: Anker Harbour.",
                "§3Kapitän Wim§8: §fLeinen los! Nächster Halt: Anker Harbour.")));
        f.cue(12, () -> player.playSound(player.getLocation(), Sound.ENTITY_BOAT_PADDLE_WATER, SoundCategory.AMBIENT, 0.8f, 0.7f));
        f.cue(30, () -> caption(player, Txt.t(player, "Nobody sails to the mainland unprepared.",
                "Niemand segelt unvorbereitet zum Festland."), 50));
        f.cue(84, () -> caption(player, Txt.t(player, "§eYou are prepared, {player}.", "§eDu bist vorbereitet, {player}."), 50));
        f.cue(56, () -> player.sendMessage(Txt.t(player, "§bTolly§8: §fSee you on the mainland, {player}!",
                "§bTolly§8: §fWir sehen uns auf dem Festland, {player}!")));
        // the mist closes in
        for (int i = 0; i < 5; i++) {
            int alpha = 70 + i * 22;
            f.cue(92 + i * 8, () -> {
                if (f.cam != null) {
                    f.cam.addWhite(1, alpha);
                }
            });
        }
        f.done = () -> {
            isle.director().graduate(player, session);
            sailing.put(player.getUniqueId(), (long) org.bukkit.Bukkit.getCurrentTick());
            if (!plugin.toMain(player, "")) {
                // nothing left this server: the pier it is
                sailing.put(player.getUniqueId(), (long) org.bukkit.Bukkit.getCurrentTick() - 1000L);
            }
        };
        start(f);
    }

    /** The crossing did not happen (the main world is closed): back on the pier, nothing lost. */
    private void ashore(Player player, Session session) {
        Flight f = flights.remove(player.getUniqueId());
        Location pier = at("pier_head", 24, 65, 97);
        if (f != null && f.cam != null) {
            f.cam.end(pier, GameMode.SURVIVAL);
        } else if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(pier);
        }
        session.scene = false;
        isle.director().stranded(player, session);
    }

    // ------------------------------------------------------------------ running flights

    private void start(Flight f) {
        Flight old = flights.put(f.player.getUniqueId(), f);
        if (old != null && old.cam != null) {
            old.cam.remove();
        }
        f.session.scene = true;
        Guide.hide(f.player, f.session);
        f.player.closeInventory();
        Key first = f.keys.get(0);
        f.cam = CineCam.start(plugin, f.player, pose(first.pos(), first.look()), 3);
        if (f.white) {
            f.cam.addWhite(4, 235);
        }
    }

    void tick() {
        if (!sunrises.isEmpty()) {
            Iterator<Map.Entry<UUID, Sunrise>> it = sunrises.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Sunrise> e = it.next();
                Player player = org.bukkit.Bukkit.getPlayer(e.getKey());
                if (player == null || !player.isOnline() || !tickSunrise(player, e.getValue())) {
                    it.remove();
                }
            }
        }
        if (!sailing.isEmpty()) {
            long now = org.bukkit.Bukkit.getCurrentTick();
            Iterator<Map.Entry<UUID, Long>> it = sailing.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                Player player = org.bukkit.Bukkit.getPlayer(e.getKey());
                if (player == null || !player.isOnline()) {
                    it.remove();
                    continue;
                }
                if (now - e.getValue() > 240L) {
                    it.remove();
                    Session session = isle.director().of(player);
                    if (session != null) {
                        ashore(player, session);
                    }
                }
            }
        }
        if (flights.isEmpty()) {
            return;
        }
        for (Flight f : new ArrayList<>(flights.values())) {
            step(f);
        }
    }

    private void step(Flight f) {
        Player player = f.player;
        if (!player.isOnline() || f.cam == null || !f.cam.valid()) {
            flights.remove(player.getUniqueId());
            if (f.cam != null) {
                f.cam.remove();
            }
            return;
        }
        f.cam.hold();
        if (!f.cam.attached() && f.waited < 100) {
            // the lens's ground is still on its way to the client: the film waits inside the mist
            f.waited++;
            return;
        }
        if (f.tick < 0 && f.white) {
            f.cam.clearWhite(36);
        }
        f.tick++;
        Runnable cue = f.cues.get(f.tick);
        if (cue != null) {
            cue.run();
        }
        int length = f.length();
        if (f.tick >= length) {
            flights.remove(player.getUniqueId());
            if (f.keepLens) {
                // the lens stays on (inside the mist) until the server lets go; the caller owns what comes next
                flights.put(player.getUniqueId(), hold(f));
            } else {
                f.cam.end(f.feet, GameMode.SURVIVAL);
                f.session.scene = false;
            }
            if (f.done != null) {
                f.done.run();
            }
            return;
        }
        f.cam.frame(poseAt(f, f.tick));
    }

    /** A flight that only keeps the lens where it stopped. */
    private Flight hold(Flight from) {
        Flight still = new Flight(from.player, from.session);
        Key last = from.keys.get(from.keys.size() - 1);
        still.keys.add(new Key(0, last.pos(), last.look()));
        still.keys.add(new Key(Integer.MAX_VALUE, last.pos(), last.look()));
        still.cam = from.cam;
        still.tick = 0;
        still.feet = from.feet;
        return still;
    }

    private Location poseAt(Flight f, int tick) {
        List<Key> keys = f.keys;
        int length = f.length();
        // ease the whole flight in and out, half linear so the middle never crawls
        double u = Math.max(0.0, Math.min(1.0, tick / (double) length));
        double eased = 0.45 * u + 0.55 * (u * u * (3.0 - 2.0 * u));
        double t = eased * length;
        int i = 0;
        while (i < keys.size() - 2 && keys.get(i + 1).at() <= t) {
            i++;
        }
        Key a = keys.get(i);
        Key b = keys.get(i + 1);
        double s = b.at() == a.at() ? 0.0 : (t - a.at()) / (double) (b.at() - a.at());
        Key before = keys.get(Math.max(0, i - 1));
        Key after = keys.get(Math.min(keys.size() - 1, i + 2));
        Vector pos = spline(before.pos(), a.pos(), b.pos(), after.pos(), s);
        Vector look = spline(before.look(), a.look(), b.look(), after.look(), s);
        return pose(pos, look);
    }

    /** Catmull-Rom through {@code p1} and {@code p2}. */
    private static Vector spline(Vector p0, Vector p1, Vector p2, Vector p3, double s) {
        double s2 = s * s;
        double s3 = s2 * s;
        double x = 0.5 * ((2 * p1.getX()) + (-p0.getX() + p2.getX()) * s
                + (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * s2
                + (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * s3);
        double y = 0.5 * ((2 * p1.getY()) + (-p0.getY() + p2.getY()) * s
                + (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * s2
                + (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * s3);
        double z = 0.5 * ((2 * p1.getZ()) + (-p0.getZ() + p2.getZ()) * s
                + (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * s2
                + (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * s3);
        return new Vector(x, y, z);
    }

    private Location pose(Vector pos, Vector look) {
        Location at = new Location(isle.world(), pos.getX(), pos.getY(), pos.getZ());
        Vector dir = look.clone().subtract(pos);
        if (dir.lengthSquared() > 1.0e-6) {
            at.setDirection(dir);
        }
        return at;
    }

    private static float yaw(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private Location at(String mark, double x, double y, double z) {
        Location spot = isle.marks().at(isle.world(), mark);
        return spot != null ? spot : new Location(isle.world(), x, y, z);
    }

    /** A quiet line low on the screen (the subtitle row, no title above it). */
    private static void caption(Player player, String text, int stayTicks) {
        player.showTitle(Title.title(Component.empty(), Txt.legacy("§f" + text),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(600))));
    }

    // ------------------------------------------------------------------ leaving early

    /** The player is going (quit): the lens goes, the body is theirs again, on solid ground. */
    void abort(Player player, Session session) {
        UUID id = player.getUniqueId();
        sunrises.remove(id);
        boolean wasSailing = sailing.remove(id) != null;
        Flight f = flights.remove(id);
        if (f != null && f.cam != null) {
            Location feet = f.feet != null ? f.feet : isle.marks().at(isle.world(), "arrival");
            if (wasSailing) {
                f.cam.remove();
            } else {
                f.cam.end(feet, GameMode.SURVIVAL);
            }
        }
        session.scene = false;
    }

    void shutdown() {
        for (Flight f : new ArrayList<>(flights.values())) {
            if (f.cam != null) {
                f.cam.end(f.feet, GameMode.SURVIVAL);
            }
            f.session.scene = false;
        }
        flights.clear();
        sunrises.clear();
        sailing.clear();
    }
}
