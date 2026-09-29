package de.aetherion.foraging.isle;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Walking Foraging Eldervale. Stepping into a forest shows its card; the first time pays a discovery
 * reward. Named places (the Pagoda, the Hollow, the Bell Lodge…) pay once too. All seven forests make you
 * a <b>Grove Walker</b>; every place makes you the <b>Cartographer</b>.
 *
 * <p>On request ({@code /grove go}) it points an arrow — with a height hint, because on this island
 * "up" matters more than "north" — at a forest, a place, an updraft or a cast member until you arrive.
 * First-time visitors are walked through the cast: Miss Canopy, Pell, Tamsin, then the climb to Juniper.
 */
public final class GroveCompass {

    /** Tour steps: who's next. The last one is up on the shelf, so the tour ends with an updraft ride. */
    private static final String[] TOUR = {"guide", ForageCast.BOARD_CLERK, ForageCast.WOODWRIGHT, ForageCast.ARCHIVIST};
    private static final String[] TOUR_LINES = {
            "§7Say hello to §aMiss Canopy §7at the Landing — she'll brief you.",
            "§7Pell keeps the §2Lumber Board §7at the Landing stall — orders pay well.",
            "§7Tamsin the §6Woodwright §7works the bench at the Bell Lodge, down the vale.",
            "§7Juniper keeps the §dForest Ledger §7in the Blossom Pagoda — ride the §fLanding Updraft §7up."
    };

    private record Target(String name, Location at) {
    }

    private final ForageIsle isle;
    private final Map<UUID, Grove> current = new HashMap<>();
    private final Map<UUID, String> currentPlace = new HashMap<>();
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, Long> cardCooldown = new HashMap<>();
    private final Map<UUID, Boolean> greeted = new HashMap<>();

    GroveCompass(ForageIsle isle) {
        this.isle = isle;
    }

    /** Every 10 ticks. */
    void tick(List<Player> onIsle) {
        for (Player player : onIsle) {
            Location at = player.getLocation();
            UUID id = player.getUniqueId();
            ForageProfile profile = isle.profiles().get(player);
            if (greeted.putIfAbsent(id, Boolean.TRUE) == null && profile.tourStep == 0 && profile.groves.isEmpty()) {
                player.sendMessage("§2❦ Foraging Eldervale §8· §7seven forests, one island. §fNew here? §7Follow the arrow, or §f/grove tour§7.");
                startTour(player, profile);
            }
            Grove grove = isle.grove(at);
            Grove before = current.put(id, grove);
            if (grove != null && grove != before) {
                enterGrove(player, profile, grove);
            }
            Landmark place = isle.landmark(at);
            String placeId = place == null ? null : place.id();
            String prevPlace = placeId == null ? currentPlace.remove(id) : currentPlace.put(id, placeId);
            if (place != null && !place.id().equals(prevPlace)) {
                enterPlace(player, profile, place);
            }
            wayfind(player);
        }
    }

    private void enterGrove(Player player, ForageProfile profile, Grove grove) {
        long now = System.currentTimeMillis();
        boolean first = profile.groves.add(grove.id());
        Long cool = cardCooldown.get(player.getUniqueId());
        if (first) {
            profile.dirty = true;
            ForageText.card(player, grove.colored(), "§7" + grove.tagline(), 45);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.9f, 1.1f);
            long coins = 250L;
            ForageBridge.coins(player, coins);
            ForageBridge.bonus(player, 30);
            player.sendMessage("§2❦ Discovered §8· " + grove.colored() + " §8· §6+" + coins + " coins §8· §7"
                    + profile.groves.size() + "/" + Grove.values().length + " forests");
            if (profile.groves.size() >= Grove.values().length && profile.flags.add("grove_walker")) {
                ForageText.card(player, "§2Grove Walker", "§7You have walked all seven forests", 60);
                ForageBridge.coins(player, 1500L);
                isle.standing().add(player, profile, 2, "grove walker");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            }
        } else if (cool == null || now >= cool) {
            ForageText.bar(player, grove.colored() + " §8· §7" + grove.tagline()
                    + (isle.events().hotspot() == grove ? " §8· §6✦ Golden Sap here" : ""));
        }
        cardCooldown.put(player.getUniqueId(), now + 15_000L);
    }

    private void enterPlace(Player player, ForageProfile profile, Landmark place) {
        if (!profile.places.add(place.id())) {
            return;
        }
        profile.dirty = true;
        ForageBridge.coins(player, 150L);
        ForageBridge.bonus(player, 20);
        player.sendMessage("§2✧ Found §8· " + place.colored() + (place.blurb().isBlank() ? "" : " §8· §7" + place.blurb()));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
        int total = isle.config().landmarks().size();
        long found = isle.config().landmarks().keySet().stream().filter(profile.places::contains).count();
        if (found >= total && total > 0 && profile.flags.add("cartographer")) {
            ForageText.card(player, "§6Cartographer", "§7Every place on Foraging Eldervale", 60);
            ForageBridge.coins(player, 2500L);
            isle.standing().add(player, profile, 2, "cartographer");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ wayfinder

    public void point(Player player, String name, Location at) {
        if (at == null) {
            targets.remove(player.getUniqueId());
            return;
        }
        targets.put(player.getUniqueId(), new Target(name, at.clone()));
        wayfind(player);
    }

    public void stop(Player player) {
        targets.remove(player.getUniqueId());
    }

    public boolean pointing(Player player) {
        return targets.containsKey(player.getUniqueId());
    }

    private void wayfind(Player player) {
        Target target = targets.get(player.getUniqueId());
        if (target == null || target.at().getWorld() != player.getWorld()) {
            return;
        }
        double dist = player.getLocation().distance(target.at());
        if (dist < 5.0d) {
            targets.remove(player.getUniqueId());
            ForageText.bar(player, "§a✔ " + target.name() + " §7— you're here.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
            return;
        }
        if (ForageText.held(player)) {
            return;
        }
        player.sendActionBar(ForageText.legacy("§e" + ForageText.arrow(player, target.at()) + " §f" + target.name()
                + " §8· §7" + (int) dist + "m" + ForageText.vertical(player, target.at())));
    }

    /** Resolves a forest, place, updraft or cast id to a standing spot. */
    public Location resolve(String raw, World world) {
        if (raw == null || world == null) {
            return null;
        }
        Landmark landmark = isle.config().landmark(raw);
        if (landmark != null) {
            return landmark.anchor(world);
        }
        Grove grove = Grove.byId(raw);
        if (grove != null) {
            Landmark heart = isle.config().landmark(heartOf(grove));
            return heart == null ? null : heart.anchor(world);
        }
        ForageConfig.Updraft draft = isle.config().updrafts().get(raw.toLowerCase(java.util.Locale.ROOT));
        if (draft != null) {
            return new Location(world, draft.fx(), draft.fy(), draft.fz());
        }
        double[] cast = isle.config().castAnchor(raw.toLowerCase(java.util.Locale.ROOT));
        return cast == null ? null : new Location(world, cast[0], cast[1], cast[2]);
    }

    public static String heartOf(Grove grove) {
        return switch (grove) {
            case FROSTPINE -> "frostpine_lodge";
            case BLOSSOM -> "blossom_pagoda";
            case SUNSCAR -> "sunscar_huts";
            case ELDERWOOD -> "streamside_hut";
            case GLOAMWOOD -> "emberlit_hollow";
            case BRINEFALL -> "brinefall_lakes";
            case CANOPY_CROWN -> "canopy_boardwalk";
        };
    }

    // ------------------------------------------------------------------ tour

    public void startTour(Player player, ForageProfile profile) {
        if (profile.tourStep >= TOUR.length) {
            profile.tourStep = 0;
            profile.dirty = true;
        }
        pointTour(player, profile);
    }

    private void pointTour(Player player, ForageProfile profile) {
        if (profile.tourStep >= TOUR.length) {
            return;
        }
        String who = TOUR[profile.tourStep];
        Location at = who.equals("guide") ? isle.guideLocation() : resolve(who, isle.isleWorld());
        if (at == null) {
            return;
        }
        player.sendMessage("§2Tour §8(" + (profile.tourStep + 1) + "/" + TOUR.length + ") §8· " + TOUR_LINES[profile.tourStep]);
        point(player, tourName(who), at);
    }

    private static String tourName(String who) {
        return switch (who) {
            case "guide" -> "Miss Canopy";
            case ForageCast.BOARD_CLERK -> "Pell · Lumber Board";
            case ForageCast.WOODWRIGHT -> "Tamsin · Woodwright";
            case ForageCast.ARCHIVIST -> "Juniper · Forest Ledger";
            default -> who;
        };
    }

    /** A cast member (or Miss Canopy, "guide") was talked to — advance the tour when it's their turn. */
    public void talked(Player player, String who) {
        ForageProfile profile = isle.profiles().get(player);
        if (profile.tourStep >= TOUR.length || !TOUR[profile.tourStep].equals(who)) {
            return;
        }
        profile.tourStep++;
        profile.dirty = true;
        if (profile.tourStep >= TOUR.length) {
            targets.remove(player.getUniqueId());
            ForageBridge.coins(player, 750L);
            isle.standing().add(player, profile, 1, "tour");
            player.sendMessage("§2Tour done §8· §6+750 coins §8· §7the island is yours. §f/grove §7is your journal.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
            return;
        }
        pointTour(player, profile);
    }

    void forget(UUID id) {
        current.remove(id);
        currentPlace.remove(id);
        targets.remove(id);
        cardCooldown.remove(id);
        greeted.remove(id);
    }
}
