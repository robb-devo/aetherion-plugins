package de.aetherion.hub.origin;

import de.aetherion.hub.model.HubSpawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Walking Origin. Entering a district shows its card; the first time pays a discovery reward. Landmarks pay once.
 * Every district makes you a <b>Wayfarer</b>, every landmark the <b>Origin Cartographer</b>.
 *
 * <p>{@code /origin go <place>} points an action-bar arrow with a height hint (the isle runs from y 54 on the pier
 * to y 236 on Skyreach). The optional tour walks you through the Origin cast and the Skyreach loop.
 */
public final class OriginCompass {

    private record Target(String name, Location at) {
    }

    /** Tour steps. kind: talk | visit | ride | attune. */
    public record Step(String kind, String id, String line) {
    }

    public static final List<Step> TOUR = List.of(
            new Step("talk", "guide", "§7Say hello to §6Orla Vane §7in Ledger's Court — she keeps the Origin Journal."),
            new Step("visit", "fountain_square", "§7Walk north to §fFountain Square§7. Coins in, wishes out."),
            new Step("talk", "keeper", "§7Up the Grand Stair: §bCobb Kettleby §7minds the skyways at the Mountain Gate."),
            new Step("ride", "updraft:skyreach", "§7Step into the §fSkyreach Updraft §7behind the gate. Hold on."),
            new Step("talk", "stargazer", "§7Find §dStellan Voss §7on the summit. He knows the night sky."),
            new Step("ride", "flight:summit_glide", "§7Take the §fSummit Glide §7back down — the ring at the cliff edge."),
            new Step("talk", "bellkeeper", "§7West to Scholars' Hall: §eSister Aurel §7counts the Seven Bells."),
            new Step("attune", "hearthcap", "§7Last stop: the §9Hearthcap §7glowcap on the west terrace. Touch it.")
    );

    private final OriginIsle isle;
    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, String> currentLandmark = new HashMap<>();
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, Long> cardCooldown = new HashMap<>();
    private final Map<UUID, Boolean> greeted = new HashMap<>();

    OriginCompass(OriginIsle isle) {
        this.isle = isle;
    }

    /** Every 10 ticks. */
    void tick(List<Player> onIsle) {
        OriginConfig config = isle.config();
        for (Player player : onIsle) {
            UUID id = player.getUniqueId();
            Location at = player.getLocation();
            OriginProfile profile = isle.profiles().get(player);
            boolean quiet = isle.quiet(player);
            if (!quiet && greeted.putIfAbsent(id, Boolean.TRUE) == null && profile.mark("greeted")) {
                player.sendMessage("§6✦ Origin §8· §7the island is yours to walk. §f/origin §7opens the journal.");
            }
            OriginConfig.District district = config.districtAt(at);
            String districtId = district == null ? null : district.id();
            String before = districtId == null ? current.remove(id) : current.put(id, districtId);
            if (district != null && !district.id().equals(before)) {
                enterDistrict(player, profile, district, quiet);
            }
            OriginConfig.Landmark landmark = nearestLandmark(at);
            String landmarkId = landmark == null ? null : landmark.id();
            String prev = landmarkId == null ? currentLandmark.remove(id) : currentLandmark.put(id, landmarkId);
            if (landmark != null && !landmark.id().equals(prev)) {
                enterLandmark(player, profile, landmark, quiet);
            }
            wayfind(player);
        }
    }

    private OriginConfig.Landmark nearestLandmark(Location at) {
        OriginConfig.Landmark best = null;
        double bestD = Double.MAX_VALUE;
        for (OriginConfig.Landmark landmark : isle.config().landmarks().values()) {
            double dx = at.getX() - landmark.at()[0];
            double dy = at.getY() - landmark.at()[1];
            double dz = at.getZ() - landmark.at()[2];
            double r = landmark.radius();
            if (Math.abs(dy) > Math.max(10.0d, r) + 2.0d) {
                continue;
            }
            double d = dx * dx + dz * dz;
            if (d <= r * r && d < bestD) {
                best = landmark;
                bestD = d;
            }
        }
        return best;
    }

    private void enterDistrict(Player player, OriginProfile profile, OriginConfig.District district, boolean quiet) {
        long now = System.currentTimeMillis();
        boolean first = profile.districts.add(district.id());
        if (first) {
            profile.dirty = true;
            long paid = isle.pay(player, "district", 150L);
            int total = isle.config().districts().size();
            int found = (int) isle.config().districts().keySet().stream().filter(profile.districts::contains).count();
            if (!quiet) {
                OriginText.card(player, district.color() + "§l" + district.name(), "§7" + district.tagline(), 50);
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 1.05f);
            } else {
                OriginText.bar(player, district.color() + "✦ " + district.name() + " §8· §7" + district.tagline());
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4f, 1.3f);
            }
            player.sendMessage(district.color() + "✦ Discovered §8· " + district.colored()
                    + (paid > 0 ? " §8· §6+" + OriginText.coins(paid) + " coins" : "") + " §8· §7" + found + "/" + total + " districts");
            if (found >= total && profile.mark("wayfarer")) {
                OriginText.card(player, "§6§lWayfarer", "§7You have walked every district of Origin", 70);
                long bonus = isle.pay(player, "wayfarer", 2000L);
                player.sendMessage("§6✦ Wayfarer §8· §7every district of Origin §8· §6+" + OriginText.coins(bonus) + " coins");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.1f);
            }
            if ("capital".equals(district.id()) && !quiet) {
                offerTour(player, profile);
            }
        } else {
            Long cool = cardCooldown.get(player.getUniqueId());
            if (cool == null || now >= cool) {
                if (!OriginText.held(player)) {
                    OriginText.bar(player, district.color() + district.name() + " §8· §7" + district.tagline());
                }
            }
        }
        cardCooldown.put(player.getUniqueId(), now + 15_000L);
    }

    private void enterLandmark(Player player, OriginProfile profile, OriginConfig.Landmark landmark, boolean quiet) {
        tourVisited(player, profile, landmark.id());
        if (!profile.landmarks.add(landmark.id())) {
            return;
        }
        profile.dirty = true;
        long paid = isle.pay(player, "landmark", 60L);
        OriginConfig.District district = isle.config().district(landmark.district());
        String color = district == null ? "§e" : district.color();
        player.sendMessage("§e✧ Found §8· " + color + landmark.name() + (landmark.blurb().isBlank() ? "" : " §8· §7" + landmark.blurb())
                + (paid > 0 ? " §8· §6+" + paid : ""));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, quiet ? 0.4f : 0.7f, 1.5f);
        OriginText.bar(player, "§e✧ " + color + landmark.name());
        int total = isle.config().landmarks().size();
        long found = isle.config().landmarks().keySet().stream().filter(profile.landmarks::contains).count();
        if (found >= total && total > 0 && profile.mark("cartographer")) {
            OriginText.card(player, "§6§lOrigin Cartographer", "§7Every landmark on the island", 70);
            long bonus = isle.pay(player, "cartographer", 3000L);
            player.sendMessage("§6✦ Origin Cartographer §8· §6+" + OriginText.coins(bonus) + " coins");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ arrival (spawn teleports)

    void arrived(Player player, HubSpawn spawn) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.0, 0), 18, 0.35, 0.8, 0.35, 0.02);
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.1, 0), 12, 0.6, 0.05, 0.6, 0.01);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        OriginConfig.District district = isle.config().districtAt(at);
        if (district != null) {
            OriginText.bar(player, district.color() + "✦ " + spawn.displayName() + " §8· " + district.colored() + " §8· §7" + district.tagline());
            // Only mark "already here" for districts they know: a first arrival by teleport still discovers.
            if (isle.profiles().get(player).districts.contains(district.id())) {
                current.put(player.getUniqueId(), district.id());
                cardCooldown.put(player.getUniqueId(), System.currentTimeMillis() + 15_000L);
            }
        }
    }

    String lockedHint(Player player, String spawnId) {
        HubSpawn spawn = isle.hub().spawn(spawnId);
        if (spawn == null) {
            return null;
        }
        Location at = isle.hub().resolveLocation(spawn);
        if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
            return "§7Unlock it through the story, or discover it in the world.";
        }
        double dist = at.distance(player.getLocation());
        return "§7It's §f" + OriginText.distance(dist) + " §7" + OriginText.compass(player.getLocation(), at)
                + " of you. §e/origin go camp:" + spawn.id() + " §7points the way.";
    }

    // ------------------------------------------------------------------ wayfinder

    public void point(Player player, String name, Location at) {
        if (at == null) {
            targets.remove(player.getUniqueId());
            return;
        }
        targets.put(player.getUniqueId(), new Target(name, at.clone()));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.6f, 1.3f);
        wayfind(player);
    }

    public void stop(Player player) {
        targets.remove(player.getUniqueId());
    }

    public boolean pointing(Player player) {
        return targets.containsKey(player.getUniqueId());
    }

    public String targetName(Player player) {
        Target target = targets.get(player.getUniqueId());
        return target == null ? null : target.name();
    }

    private void wayfind(Player player) {
        Target target = targets.get(player.getUniqueId());
        if (target == null || target.at().getWorld() != player.getWorld()) {
            return;
        }
        double dist = player.getLocation().distance(target.at());
        if (dist < 5.0d) {
            targets.remove(player.getUniqueId());
            OriginText.bar(player, "§a✔ " + target.name() + " §7— you're here.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.6f);
            return;
        }
        if (OriginText.held(player)) {
            return;
        }
        player.sendActionBar(OriginText.legacy("§e" + OriginText.arrow(player, target.at()) + " §f" + target.name()
                + " §8· §7" + OriginText.distance(dist) + OriginText.vertical(player, target.at())));
    }

    /**
     * Resolves a place id: district, landmark, waystone, vista, bell, {@code updraft:x}, {@code flight:x},
     * {@code cast:role} or {@code camp:spawnId}. Returns [display name, location] or null.
     */
    public Object[] resolve(String raw) {
        if (raw == null) {
            return null;
        }
        OriginConfig config = isle.config();
        String key = raw.toLowerCase(Locale.ROOT).trim();
        if (key.startsWith("camp:")) {
            HubSpawn spawn = isle.hub().spawn(key.substring(5));
            Location at = spawn == null ? null : isle.hub().resolveLocation(spawn);
            return at == null ? null : new Object[]{spawn.displayName(), at};
        }
        if (key.startsWith("cast:")) {
            OriginRole role = OriginRole.byId(key.substring(5));
            Location at = role == null ? null : isle.cast().whereabouts(role);
            return at == null ? null : new Object[]{role.display(), at};
        }
        if (key.startsWith("updraft:")) {
            OriginConfig.Updraft draft = config.updrafts().get(key.substring(8));
            return draft == null ? null : new Object[]{draft.name(), config.location(draft.floor())};
        }
        if (key.startsWith("flight:")) {
            OriginConfig.Flight flight = config.flights().get(key.substring(7));
            return flight == null ? null : new Object[]{flight.name(), config.location(flight.marker())};
        }
        if (key.startsWith("waystone:")) {
            OriginConfig.Waystone waystone = config.waystones().get(key.substring(9));
            return waystone == null ? null : new Object[]{waystone.name(), config.location(waystone.stand())};
        }
        if (key.startsWith("vista:")) {
            OriginConfig.Vista vista = config.vistas().get(key.substring(6));
            return vista == null ? null : new Object[]{vista.name(), config.location(vista.at())};
        }
        if (key.startsWith("bell:")) {
            key = key.substring(5);
        } else if (key.startsWith("landmark:")) {
            key = key.substring(9);
        } else if (key.startsWith("district:")) {
            OriginConfig.District district = config.district(key.substring(9));
            return district == null || district.anchor() == null ? null : new Object[]{district.name(), config.location(district.anchor())};
        }
        OriginConfig.Landmark landmark = config.landmark(key);
        if (landmark != null) {
            return new Object[]{landmark.name(), config.location(landmark.at())};
        }
        OriginConfig.District district = config.district(key);
        if (district != null && district.anchor() != null) {
            return new Object[]{district.name(), config.location(district.anchor())};
        }
        OriginConfig.Waystone waystone = config.waystones().get(key);
        if (waystone != null) {
            return new Object[]{waystone.name(), config.location(waystone.stand())};
        }
        OriginConfig.Vista vista = config.vistas().get(key);
        if (vista != null) {
            return new Object[]{vista.name(), config.location(vista.at())};
        }
        OriginConfig.Bell bell = config.bells().get(key);
        if (bell != null) {
            return new Object[]{bell.name(), config.location(new double[]{bell.at()[0] + 0.5, bell.at()[1] - 1.0, bell.at()[2] + 0.5})};
        }
        return null;
    }

    public boolean go(Player player, String raw) {
        Object[] hit = resolve(raw);
        if (hit == null || hit[1] == null) {
            return false;
        }
        point(player, (String) hit[0], (Location) hit[1]);
        return true;
    }

    // ------------------------------------------------------------------ tour

    private void offerTour(Player player, OriginProfile profile) {
        if (profile.tourStep >= 0 || !profile.mark("tour_offered")) {
            return;
        }
        Component line = OriginText.legacy("§6✦ New to Origin? ")
                .append(OriginText.legacy("§e§n[Take the tour]")
                        .clickEvent(ClickEvent.runCommand("/origin tour"))
                        .hoverEvent(HoverEvent.showText(OriginText.legacy("§7Eight stops: the cast, the updraft,\n§7the summit and a glide back down."))))
                .append(OriginText.legacy(" §8· §7or §f/origin §7any time."));
        player.sendMessage(line);
    }

    public void startTour(Player player) {
        OriginProfile profile = isle.profiles().get(player);
        if (profile.tourStep < 0 || profile.tourStep >= TOUR.size()) {
            profile.tourStep = 0;
            profile.dirty = true;
        }
        pointTour(player, profile);
    }

    public void stopTour(Player player) {
        OriginProfile profile = isle.profiles().get(player);
        if (profile.tourStep >= 0 && profile.tourStep < TOUR.size()) {
            profile.tourStep = -1;
            profile.dirty = true;
        }
        targets.remove(player.getUniqueId());
    }

    public boolean touring(Player player) {
        OriginProfile profile = isle.profiles().get(player);
        return profile.tourStep >= 0 && profile.tourStep < TOUR.size();
    }

    private void pointTour(Player player, OriginProfile profile) {
        if (profile.tourStep < 0 || profile.tourStep >= TOUR.size()) {
            return;
        }
        Step step = TOUR.get(profile.tourStep);
        player.sendMessage("§6Tour §8(" + (profile.tourStep + 1) + "/" + TOUR.size() + ") §8· " + step.line());
        Object[] hit = switch (step.kind()) {
            case "talk" -> resolve("cast:" + step.id());
            case "ride" -> resolve(step.id());
            default -> resolve(step.id());
        };
        if (hit != null && hit[1] != null) {
            point(player, (String) hit[0], (Location) hit[1]);
        }
    }

    private void advance(Player player, OriginProfile profile) {
        profile.tourStep++;
        profile.dirty = true;
        if (profile.tourStep >= TOUR.size()) {
            targets.remove(player.getUniqueId());
            // The tour pays once per player; taking it again is just a walk.
            long paid = profile.mark("tour_done") ? isle.pay(player, "tour", 1000L) : 0L;
            OriginText.card(player, "§6§lTour complete", "§7Origin is yours. §f/origin §7is your journal.", 60);
            player.sendMessage("§6Tour done" + (paid > 0 ? " §8· §6+" + OriginText.coins(paid) + " coins" : "") + " §8· §7the island is yours.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            return;
        }
        pointTour(player, profile);
    }

    private boolean onStep(OriginProfile profile, String kind, String id) {
        if (profile.tourStep < 0 || profile.tourStep >= TOUR.size()) {
            return false;
        }
        Step step = TOUR.get(profile.tourStep);
        return step.kind().equals(kind) && step.id().equals(id);
    }

    public void talked(Player player, OriginRole role) {
        OriginProfile profile = isle.profiles().get(player);
        if (profile.met.add(role.id())) {
            profile.dirty = true;
        }
        if (onStep(profile, "talk", role.id())) {
            advance(player, profile);
        }
    }

    void tourVisited(Player player, OriginProfile profile, String landmarkId) {
        if (onStep(profile, "visit", landmarkId)) {
            advance(player, profile);
        }
    }

    public void rode(Player player, String rideKey) {
        OriginProfile profile = isle.profiles().get(player);
        if (onStep(profile, "ride", rideKey)) {
            advance(player, profile);
        }
    }

    public void attuned(Player player, String waystoneId) {
        OriginProfile profile = isle.profiles().get(player);
        if (onStep(profile, "attune", waystoneId)) {
            advance(player, profile);
        }
    }

    public String districtOf(Player player) {
        return current.get(player.getUniqueId());
    }

    void forget(UUID id) {
        current.remove(id);
        currentLandmark.remove(id);
        targets.remove(id);
        cardCooldown.remove(id);
        greeted.remove(id);
    }
}
