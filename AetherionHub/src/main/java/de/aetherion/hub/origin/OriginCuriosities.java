package de.aetherion.hub.origin;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Curiosities: the small hidden finds of Origin (turtle eggs in a fountain planter, a snow layer that never melts,
 * a gold block at the bottom of the Borderlands pit…). Each one sits on a surveyed, reachable spot and is found by
 * doing what a curious person would do there:
 * <ul>
 *   <li><b>step</b> — stand on it (rooftops, caps, cave floors, lily rafts);</li>
 *   <li><b>look</b> — hold your gaze on it for a moment (a lightning rod on a roof, lights in the pit at night);</li>
 *   <li><b>touch</b> — right-click it (a chart table, a chipped anvil, wild honeycomb);</li>
 *   <li><b>listen</b> — sneak and stay still for a few seconds (the river under the bridge, the hive wall).</li>
 * </ul>
 * Nothing is announced in advance: undiscovered ones glint faintly when you're close, and the journal only shows a
 * riddle. Finds pay a little, every district swept pays a little more, and the milestones are titles.
 */
public final class OriginCuriosities implements Listener {

    public enum Kind { STEP, LOOK, TOUCH, LISTEN }

    public record Curio(String id, String name, String district, Kind kind, double[] at, int[] target, double radius,
                        double range, String when, Material icon, String clue, String note, String requires, Sound sound) {

        /** Where the find "is" (the thing you look at / touch, or the spot you stand on). */
        public double[] focus() {
            return target != null ? new double[]{target[0] + 0.5d, target[1] + 0.5d, target[2] + 0.5d} : at;
        }
    }

    /** Milestone titles: flag, title, count (0 = every curiosity). */
    public record Milestone(String flag, String title, int count, String rewardKey, long fallback) {
    }

    private static final int CELL = 32;
    private static final long SENSE_COOLDOWN_MS = 90_000L;

    private final OriginIsle isle;
    private final Map<String, Curio> curios = new LinkedHashMap<>();
    private final Map<Long, List<Curio>> grid = new HashMap<>();
    private final Map<Long, Curio> touchIndex = new HashMap<>();
    private final List<Milestone> milestones = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> holds = new HashMap<>();
    private final Map<UUID, Map<String, Long>> sensed = new HashMap<>();
    private double glintRadius = 9.0d;
    private int lookHold = 2;
    private int listenChecks = 6;

    OriginCuriosities(OriginIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ config

    /** Parses {@code origin-explore.yml → curiosities} (+ {@code curiosity-settings}). Returns the count. */
    int load() {
        curios.clear();
        grid.clear();
        touchIndex.clear();
        milestones.clear();
        holds.clear();
        ConfigurationSection settings = isle.config().explore().getConfigurationSection("curiosity-settings");
        if (settings != null) {
            glintRadius = settings.getDouble("glint-radius", 9.0d);
            lookHold = Math.max(1, settings.getInt("look-hold-checks", 2));
            listenChecks = Math.max(2, settings.getInt("listen-checks", 6));
            ConfigurationSection ms = settings.getConfigurationSection("milestones");
            if (ms != null) {
                for (String flag : ms.getKeys(false)) {
                    ConfigurationSection m = ms.getConfigurationSection(flag);
                    if (m != null) {
                        milestones.add(new Milestone(flag, m.getString("title", OriginText.pretty(flag)), m.getInt("count", 0),
                                m.getString("reward", flag), m.getLong("fallback", 500L)));
                    }
                }
            }
        }
        milestones.sort((a, b) -> Integer.compare(a.count() <= 0 ? Integer.MAX_VALUE : a.count(), b.count() <= 0 ? Integer.MAX_VALUE : b.count()));
        ConfigurationSection cs = isle.config().explore().getConfigurationSection("curiosities");
        if (cs == null) {
            return 0;
        }
        for (String id : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            double[] at = OriginConfig.nums(s.getString("at"));
            int[] target = OriginConfig.ints(s.getString("target"));
            if (at == null && target != null) {
                at = new double[]{target[0] + 0.5d, target[1], target[2] + 0.5d};
            }
            if (at == null || at.length < 3) {
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(s.getString("kind", "step").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                kind = Kind.STEP;
            }
            if ((kind == Kind.LOOK || kind == Kind.TOUCH) && target == null) {
                target = new int[]{(int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2])};
            }
            Sound sound = null;
            String rawSound = s.getString("sound");
            if (rawSound != null && !rawSound.isBlank()) {
                sound = soundOf(rawSound);
            }
            Curio curio = new Curio(id.toLowerCase(Locale.ROOT), s.getString("name", OriginText.pretty(id)), s.getString("district", ""),
                    kind, at, target, s.getDouble("radius", kind == Kind.LISTEN ? 3.0d : 2.5d), s.getDouble("range", 12.0d),
                    s.getString("when", "any"), OriginConfig.material(s.getString("icon"), Material.AMETHYST_SHARD),
                    s.getString("clue", ""), s.getString("note", ""), s.getString("requires", ""), sound);
            curios.put(curio.id(), curio);
            double[] f = curio.focus();
            grid.computeIfAbsent(cell((int) Math.floor(f[0]) >> 5, (int) Math.floor(f[2]) >> 5), k -> new ArrayList<>()).add(curio);
            if (kind == Kind.TOUCH) {
                touchIndex.put(blockKey(target[0], target[1], target[2]), curio);
            }
        }
        return curios.size();
    }

    static Sound soundOf(String raw) {
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ accessors

    public Map<String, Curio> all() {
        return curios;
    }

    public Curio get(String id) {
        return id == null ? null : curios.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Milestone> milestones() {
        return milestones;
    }

    public int found(OriginProfile profile) {
        int n = 0;
        for (String id : curios.keySet()) {
            if (profile.curiosities.contains(id)) {
                n++;
            }
        }
        return n;
    }

    public long inDistrict(String district) {
        return curios.values().stream().filter(c -> c.district().equals(district)).count();
    }

    public long foundInDistrict(OriginProfile profile, String district) {
        return curios.values().stream().filter(c -> c.district().equals(district) && profile.curiosities.contains(c.id())).count();
    }

    /** Is this curiosity live right now (time of day, weather, a prop it needs)? */
    boolean active(Curio c) {
        String when = c.when() == null ? "any" : c.when().toLowerCase(Locale.ROOT);
        if (when.equals("rain")) {
            World world = isle.config().world();
            if (world == null || world.isClearWeather()) {
                return false;
            }
        } else if (!OriginAmbience.active(when, isle.phase())) {
            return false;
        }
        String req = c.requires() == null ? "" : c.requires().trim().toLowerCase(Locale.ROOT);
        if (req.startsWith("prop:")) {
            return isle.props().pasted(req.substring(5));
        }
        return true;
    }

    // ------------------------------------------------------------------ loop (every 8 ticks)

    void tick(List<Player> onIsle) {
        if (curios.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            if (player.getGameMode() == GameMode.SPECTATOR || isle.flight().flying(player)) {
                continue;
            }
            OriginProfile profile = isle.profiles().get(player);
            Location feet = player.getLocation();
            Map<String, Integer> hold = holds.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            Set<String> seenNow = new HashSet<>();
            for (Curio c : nearby(feet, 40)) {
                if (profile.curiosities.contains(c.id()) || !active(c)) {
                    continue;
                }
                switch (c.kind()) {
                    case STEP -> {
                        if (standsAt(feet, c)) {
                            find(player, profile, c);
                        } else {
                            sense(player, c, now);
                        }
                    }
                    case LISTEN -> {
                        if (standsAt(feet, c)) {
                            if (player.isSneaking()) {
                                int n = hold.merge(c.id(), 1, Integer::sum);
                                seenNow.add(c.id());
                                if (n >= listenChecks) {
                                    find(player, profile, c);
                                } else {
                                    OriginText.bar(player, "§7You hold still and listen… " + OriginText.cells((double) n / listenChecks, "§d"));
                                }
                            } else {
                                faintCue(player, c, now);
                            }
                        }
                    }
                    case LOOK -> {
                        if (aimedAt(player, c)) {
                            int n = hold.merge(c.id(), 1, Integer::sum);
                            seenNow.add(c.id());
                            if (n >= lookHold && visible(player, c)) {
                                find(player, profile, c);
                            }
                        }
                    }
                    case TOUCH -> sense(player, c, now);
                }
            }
            hold.keySet().retainAll(seenNow);
        }
    }

    private List<Curio> nearby(Location at, int reach) {
        List<Curio> out = new ArrayList<>();
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int r = (reach >> 5) + 1;
        for (int cx = (bx >> 5) - r; cx <= (bx >> 5) + r; cx++) {
            for (int cz = (bz >> 5) - r; cz <= (bz >> 5) + r; cz++) {
                List<Curio> list = grid.get(cell(cx, cz));
                if (list != null) {
                    out.addAll(list);
                }
            }
        }
        return out;
    }

    private static boolean standsAt(Location feet, Curio c) {
        double dx = feet.getX() - c.at()[0];
        double dz = feet.getZ() - c.at()[2];
        double dy = feet.getY() - c.at()[1];
        return dx * dx + dz * dz <= c.radius() * c.radius() && Math.abs(dy) <= Math.max(2.5d, c.radius());
    }

    private static boolean aimedAt(Player player, Curio c) {
        Location eye = player.getEyeLocation();
        double[] f = c.focus();
        Vector to = new Vector(f[0] - eye.getX(), f[1] - eye.getY(), f[2] - eye.getZ());
        double dist = to.length();
        if (dist > c.range() || dist < 0.5d) {
            return false;
        }
        double size = Math.max(0.6d, Math.min(2.5d, c.radius()));
        double allowed = Math.atan2(size, dist) + Math.toRadians(1.5d);
        double cos = eye.getDirection().dot(to.multiply(1.0d / dist));
        return cos >= Math.cos(allowed);
    }

    private boolean visible(Player player, Curio c) {
        Location eye = player.getEyeLocation();
        double[] f = c.focus();
        Vector to = new Vector(f[0] - eye.getX(), f[1] - eye.getY(), f[2] - eye.getZ());
        double dist = to.length();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, to.normalize(), Math.max(0.1d, dist - 0.55d),
                FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            return true;
        }
        Block block = hit.getHitBlock();
        if (c.target() == null) {
            return false;
        }
        return Math.abs(block.getX() - c.target()[0]) <= 1 && Math.abs(block.getY() - c.target()[1]) <= 1
                && Math.abs(block.getZ() - c.target()[2]) <= 1;
    }

    /** A soft "something's here" chime the first time you come close (at most every 90 s per curiosity). */
    private void sense(Player player, Curio c, long now) {
        double[] f = c.focus();
        Location at = player.getLocation();
        double dx = at.getX() - f[0];
        double dz = at.getZ() - f[2];
        if (dx * dx + dz * dz > 36.0d || Math.abs(at.getY() - f[1]) > 6.0d) {
            return;
        }
        Map<String, Long> mine = sensed.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long last = mine.get(c.id());
        if (last != null && now - last < SENSE_COOLDOWN_MS) {
            return;
        }
        mine.put(c.id(), now);
        if (isle.profiles().get(player).ambience) {
            player.playSound(new Location(player.getWorld(), f[0], f[1], f[2]), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.25f, 1.9f);
        }
    }

    /** Listen spots whisper a little before you crouch. */
    private void faintCue(Player player, Curio c, long now) {
        Map<String, Long> mine = sensed.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long last = mine.get(c.id());
        if (last != null && now - last < 12_000L) {
            return;
        }
        mine.put(c.id(), now);
        if (!isle.profiles().get(player).ambience) {
            return;
        }
        Location at = new Location(player.getWorld(), c.at()[0], c.at()[1] + 0.5d, c.at()[2]);
        player.playSound(at, c.sound() != null ? c.sound() : Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.18f, 1.0f);
        if (!OriginText.held(player)) {
            OriginText.bar(player, "§8…§7something here, if you keep still§8…");
        }
    }

    // ------------------------------------------------------------------ glints (every 20 ticks)

    void glints(List<Player> onIsle) {
        if (curios.isEmpty()) {
            return;
        }
        for (Player player : onIsle) {
            OriginProfile profile = isle.profiles().get(player);
            if (!profile.particles || isle.flight().flying(player)) {
                continue;
            }
            Location feet = player.getLocation();
            int shown = 0;
            for (Curio c : nearby(feet, 32)) {
                if (shown >= 4) {
                    break;
                }
                if (profile.curiosities.contains(c.id()) || !active(c)) {
                    continue;
                }
                double[] f = c.focus();
                double dx = feet.getX() - f[0];
                double dy = feet.getY() - f[1];
                double dz = feet.getZ() - f[2];
                double limit = c.kind() == Kind.LOOK ? Math.min(c.range(), 24.0d) : glintRadius;
                if (dx * dx + dz * dz + dy * dy * 0.6d > limit * limit) {
                    continue;
                }
                Location at = new Location(player.getWorld(), f[0], f[1] + (c.kind() == Kind.STEP || c.kind() == Kind.LISTEN ? 0.4d : 0.1d), f[2]);
                if (c.kind() == Kind.LOOK && c.when().equalsIgnoreCase("night")) {
                    player.spawnParticle(Particle.END_ROD, at, 2, 0.6, 0.4, 0.6, 0.004);
                    player.spawnParticle(Particle.GLOW, at, 3, 0.8, 0.5, 0.8, 0.0);
                } else {
                    player.spawnParticle(Particle.WAX_ON, at, 2, 0.25, 0.2, 0.25, 0.0);
                }
                shown++;
            }
            isle.tales().glint(player, profile, feet);
        }
    }

    // ------------------------------------------------------------------ touch

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTouch(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || touchIndex.isEmpty()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Curio c = touchIndex.get(blockKey(block.getX(), block.getY(), block.getZ()));
        Player player = event.getPlayer();
        if (c == null || !isle.running() || !isle.onIsle(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        OriginProfile profile = isle.profiles().get(player);
        if (!profile.curiosities.contains(c.id()) && active(c)) {
            find(player, profile, c);
        }
    }

    // ------------------------------------------------------------------ the find

    void find(Player player, OriginProfile profile, Curio c) {
        if (!profile.curiosities.add(c.id())) {
            return;
        }
        profile.dirty = true;
        Map<String, Integer> hold = holds.get(player.getUniqueId());
        if (hold != null) {
            hold.remove(c.id());
        }
        boolean quiet = isle.quiet(player);
        long paid = isle.pay(player, "curiosity", 40L);
        int total = curios.size();
        int found = found(profile);
        OriginConfig.District district = isle.config().district(c.district());
        String color = district == null ? "§d" : district.color();
        player.sendMessage("§d✧ Curiosity §8· " + color + c.name() + (paid > 0 ? " §8· §6+" + paid : "") + " §8· §7" + found + "/" + total);
        if (!c.note().isBlank()) {
            player.sendMessage("§8   “§7" + c.note() + "§8”");
        }
        OriginText.bar(player, "§d✧ " + color + c.name() + " §8· §7" + found + "/" + total);
        double[] f = c.focus();
        Location at = new Location(player.getWorld(), f[0], f[1], f[2]);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, quiet ? 0.4f : 0.7f, 1.6f);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6f, 1.2f);
        if (c.sound() != null) {
            player.playSound(at, c.sound(), SoundCategory.AMBIENT, 0.6f, 1.0f);
        }
        if (profile.particles) {
            player.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.3, 0), 10, 0.3, 0.3, 0.3, 0.03);
            player.spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.2, 0), 8, 0.4, 0.3, 0.4, 0.0);
        }
        // A whole district swept.
        if (!c.district().isBlank()) {
            long inD = inDistrict(c.district());
            if (inD > 1 && foundInDistrict(profile, c.district()) >= inD && profile.mark("curio_" + c.district())) {
                long bonus = isle.pay(player, "curio-district", 150L);
                player.sendMessage("§d✧ §7Every curiosity of " + (district == null ? OriginText.pretty(c.district()) : district.colored())
                        + " §7found" + (bonus > 0 ? " §8· §6+" + OriginText.coins(bonus) : "") + "§7.");
            }
        }
        for (Milestone m : milestones) {
            int need = m.count() <= 0 ? total : Math.min(m.count(), total);
            if (found >= need && profile.mark(m.flag())) {
                long bonus = isle.pay(player, m.rewardKey(), m.fallback());
                if (!quiet) {
                    OriginText.card(player, m.title(), "§7" + found + " curiosities of Origin", 70);
                }
                player.sendMessage("§d✦ " + m.title() + " §8· §7" + found + " curiosities" + (bonus > 0 ? " §8· §6+" + OriginText.coins(bonus) + " coins" : ""));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.25f);
                if (m.count() <= 0) {
                    for (Player other : isle.onIsle()) {
                        if (other != player) {
                            other.sendMessage("§d✦ §f" + player.getName() + " §7has found every small wonder of Origin.");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ DEV

    String dev(Player player, String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";
        OriginProfile profile = isle.profiles().get(player);
        switch (sub) {
            case "tp" -> {
                Curio c = get(arg);
                if (c == null) {
                    return "§7/origin dev curio tp <id> §8· §7" + curios.size() + " curiosities";
                }
                Location to = isle.config().location(standFor(c));
                if (to == null) {
                    return "§cWorld not loaded.";
                }
                to.setYaw(player.getLocation().getYaw());
                isle.traversal().grace(player, 5_000L);
                player.teleport(to);
                return "§dTP §8· §f" + c.name() + " §8(" + c.kind().name().toLowerCase(Locale.ROOT) + ", " + c.when() + ")";
            }
            case "find" -> {
                Curio c = get(arg);
                if (c == null) {
                    return "§7Unknown curiosity.";
                }
                find(player, profile, c);
                return null;
            }
            case "reset" -> {
                profile.curiosities.clear();
                for (Milestone m : milestones) {
                    profile.flags.remove(m.flag());
                }
                profile.flags.removeIf(flag -> flag.startsWith("curio_"));
                profile.dirty = true;
                return "§eYour curiosities were reset.";
            }
            case "next" -> {
                Curio best = null;
                double bestD = Double.MAX_VALUE;
                for (Curio c : curios.values()) {
                    if (profile.curiosities.contains(c.id())) {
                        continue;
                    }
                    double[] f = c.focus();
                    double d = player.getLocation().distanceSquared(new Location(player.getWorld(), f[0], f[1], f[2]));
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
                if (best == null) {
                    return "§aAll curiosities found.";
                }
                isle.compass().point(player, "§d" + best.name(), isle.config().location(best.focus()));
                return "§dNearest unfound §8· §f" + best.name() + " §8(" + best.id() + ")";
            }
            default -> {
                return "§7/origin dev curio <tp <id>|find <id>|next|reset> §8· §7" + found(profile) + "/" + curios.size() + " found";
            }
        }
    }

    /** A spot to stand for a curiosity: step/listen = its spot; look/touch = its configured {@code at} (a viewing spot). */
    double[] standFor(Curio c) {
        return c.at();
    }

    Collection<String> ids() {
        return curios.keySet();
    }

    void forget(UUID id) {
        holds.remove(id);
        sensed.remove(id);
    }

    // ------------------------------------------------------------------ helpers

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }
}
