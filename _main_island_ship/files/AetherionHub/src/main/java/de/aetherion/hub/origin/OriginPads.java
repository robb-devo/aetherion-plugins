package de.aetherion.hub.origin;

import com.destroystokyo.paper.ParticleBuilder;

import de.aetherion.hub.pad.IslandLaunchPads;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Skyway flair and rediscovery. Every jump pad on Origin (the island pads of {@link IslandLaunchPads} and the two
 * arch pads Origin brought back) gets a soft light column you can see from 60 blocks away, a preview line when you
 * walk up to it (where it goes, and whether it's still sealed), a card on take-off and a discovery the first time
 * you ride it. Riding every skyway, glide and updraft makes you a <b>Skyrider</b>.
 */
public final class OriginPads {

    private static final Map<String, String> DESTINATIONS = Map.of(
            "origin_to_mining", "Mining Eldervale",
            "origin_to_forage", "Forage Isle",
            "mining_to_origin", "Origin",
            "forage_to_origin", "Origin"
    );

    private record Marker(String id, String label, String to, double x, double y, double z, boolean islandPad) {
    }

    private final OriginIsle isle;
    private final Map<UUID, Long> previewCool = new HashMap<>();
    private final Map<UUID, Long> sealedCool = new HashMap<>();

    OriginPads(OriginIsle isle) {
        this.isle = isle;
    }

    private List<Marker> markers() {
        List<Marker> out = new ArrayList<>();
        IslandLaunchPads pads = isle.plugin().getLaunchPads();
        String worldName = isle.config().worldName();
        if (pads != null) {
            for (IslandLaunchPads.PadInfo pad : pads.padInfo()) {
                if (!pad.world().equalsIgnoreCase(worldName)) {
                    continue;
                }
                out.add(new Marker(pad.id(), pad.label(), destination(pad.id()),
                        (pad.minX() + pad.maxX()) / 2.0d + 0.5d, Math.max(pad.minY(), pad.maxY()) + 1.0d,
                        (pad.minZ() + pad.maxZ()) / 2.0d + 0.5d, true));
            }
        }
        for (OriginConfig.Flight flight : isle.config().flights().values()) {
            double[] m = flight.marker();
            out.add(new Marker("flight:" + flight.id(), flight.name(), flight.to(), m[0], m[1], m[2], false));
        }
        return out;
    }

    public static String destination(String padId) {
        if (padId == null) {
            return "";
        }
        String known = DESTINATIONS.get(padId.toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        String key = padId.toLowerCase(Locale.ROOT);
        int to = key.indexOf("_to_");
        return to >= 0 ? OriginText.pretty(key.substring(to + 4)) : OriginText.pretty(key);
    }

    /** Every 10 ticks: light columns (every other call) and walk-up previews. */
    void tick(List<Player> onIsle, long tick) {
        if (onIsle.isEmpty()) {
            return;
        }
        List<Marker> markers = markers();
        long now = System.currentTimeMillis();
        boolean beacons = tick % 20 == 0;
        for (Player player : onIsle) {
            OriginProfile profile = isle.profiles().get(player);
            Location at = player.getLocation();
            for (Marker m : markers) {
                double dx = at.getX() - m.x();
                double dy = at.getY() - m.y();
                double dz = at.getZ() - m.z();
                double d2 = dx * dx + dz * dz;
                if (beacons && profile.particles && d2 < 64 * 64 && Math.abs(dy) < 60) {
                    column(player, m);
                }
                if (d2 < 5.5 * 5.5 && Math.abs(dy) < 4.0d && !isle.flight().flying(player)) {
                    Long cool = previewCool.get(player.getUniqueId());
                    if (cool == null || now >= cool) {
                        previewCool.put(player.getUniqueId(), now + 20_000L);
                        preview(player, m);
                    }
                }
            }
        }
    }

    private void column(Player player, Marker m) {
        World world = player.getWorld();
        Location base = new Location(world, m.x(), m.y(), m.z());
        for (int k = 1; k <= 7; k++) {
            new ParticleBuilder(Particle.END_ROD).location(base.clone().add(0, k * 1.6d, 0)).count(1)
                    .offset(0.05, 0.25, 0.05).extra(0.0).receivers(player).force(true).spawn();
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0d;
            new ParticleBuilder(Particle.HAPPY_VILLAGER).location(base.clone().add(Math.cos(a) * 1.7d, 0.15, Math.sin(a) * 1.7d))
                    .count(1).offset(0, 0, 0).extra(0.0).receivers(player).force(true).spawn();
        }
    }

    private void preview(Player player, Marker m) {
        String to = m.to().isBlank() ? "" : " §8· §7to §f" + m.to();
        String state = "";
        if (m.islandPad()) {
            IslandLaunchPads pads = isle.plugin().getLaunchPads();
            if (pads != null && !pads.openFor(player, m.id())) {
                state = " §8· §csealed §7— stamp a Surveyor blueprint";
            }
        }
        boolean known = isle.profiles().get(player).rides.contains(rideKey(m.id()));
        OriginText.bar(player, "§b⇗ §f" + m.label() + to + state + (known ? "" : " §8· §enew"));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.35f, 1.8f);
    }

    private static String rideKey(String markerId) {
        return markerId.startsWith("flight:") ? markerId : "pad:" + markerId;
    }

    // ------------------------------------------------------------------ hooks

    void launched(Player player, String padId) {
        String to = destination(padId);
        if (padId != null && padId.toLowerCase(Locale.ROOT).startsWith("origin_to")) {
            OriginText.card(player, "§b⇗ §fSkyway", "§7to §f" + to, 30);
        }
        record(player, "pad:" + padId, OriginText.pretty(padId) + (to.isBlank() ? "" : " → " + to));
    }

    void landed(Player player, String padId) {
        if (padId == null || !padId.toLowerCase(Locale.ROOT).endsWith("_to_origin")) {
            return;
        }
        OriginConfig.District district = isle.config().districtAt(player.getLocation());
        OriginText.bar(player, "§6✦ Back on Origin" + (district == null ? "" : " §8· " + district.colored()));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    void sealed(Player player, String padId) {
        long now = System.currentTimeMillis();
        Long cool = sealedCool.get(player.getUniqueId());
        if (cool != null && now < cool) {
            return;
        }
        sealedCool.put(player.getUniqueId(), now + 30_000L);
        player.sendMessage("§7Cobb Kettleby at the §fMountain Gate §7keeps the skyway ledger — §e/origin go cast:keeper§7.");
    }

    void rodeFlight(Player player, OriginConfig.Flight flight) {
        record(player, "flight:" + flight.id(), flight.name());
        isle.compass().rode(player, "flight:" + flight.id());
    }

    void rodeUpdraft(Player player, OriginConfig.Updraft draft) {
        record(player, "updraft:" + draft.id(), draft.name());
        isle.compass().rode(player, "updraft:" + draft.id());
    }

    private void record(Player player, String key, String name) {
        OriginProfile profile = isle.profiles().get(player);
        if (!profile.rides.add(key)) {
            return;
        }
        profile.dirty = true;
        long paid = isle.pay(player, key.startsWith("updraft:") ? "updraft" : "skyway", 80L);
        player.sendMessage("§b⇗ New skyway §8· §f" + name + (paid > 0 ? " §8· §6+" + paid + " coins" : "")
                + " §8· §7" + ridden(profile) + "/" + allRides().size());
        Set<String> all = allRides();
        if (profile.rides.containsAll(all) && profile.mark("skyrider")) {
            OriginText.card(player, "§b§lSkyrider", "§7Every skyway, glide and updraft on Origin", 60);
            long bonus = isle.pay(player, "skyrider", 1000L);
            player.sendMessage("§b✦ Skyrider §8· §6+" + OriginText.coins(bonus) + " coins");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
    }

    /** Everything that counts for Skyrider: Origin's outbound island pads, the flights and the updrafts. */
    public Set<String> allRides() {
        Set<String> out = new LinkedHashSet<>();
        IslandLaunchPads pads = isle.plugin().getLaunchPads();
        if (pads != null) {
            for (IslandLaunchPads.PadInfo pad : pads.padInfo()) {
                if (pad.id().toLowerCase(Locale.ROOT).startsWith("origin_to")) {
                    out.add("pad:" + pad.id());
                }
            }
        }
        for (String id : isle.config().flights().keySet()) {
            out.add("flight:" + id);
        }
        for (String id : isle.config().updrafts().keySet()) {
            out.add("updraft:" + id);
        }
        return out;
    }

    public int ridden(OriginProfile profile) {
        int n = 0;
        for (String key : allRides()) {
            if (profile.rides.contains(key)) {
                n++;
            }
        }
        return n;
    }

    /** For menus: [key, label, to, x, y, z]. */
    public List<Object[]> catalog() {
        List<Object[]> out = new ArrayList<>();
        for (Marker m : markers()) {
            out.add(new Object[]{rideKey(m.id()), m.label(), m.to(), m.x(), m.y(), m.z()});
        }
        for (OriginConfig.Updraft draft : isle.config().updrafts().values()) {
            out.add(new Object[]{"updraft:" + draft.id(), draft.name(), "up", draft.floor()[0], draft.floor()[1], draft.floor()[2]});
        }
        return out;
    }

    void forget(UUID id) {
        previewCool.remove(id);
        sealedCool.remove(id);
    }
}
