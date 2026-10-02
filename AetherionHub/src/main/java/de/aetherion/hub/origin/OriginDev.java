package de.aetherion.hub.origin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * DEV actions for Origin (from the DEV GUI, {@code /origin dev <action>} or the Items DevMenu tile).
 * Every action returns one chat line (or null when it already spoke).
 */
public final class OriginDev {

    public static final List<String> ACTIONS = List.of(
            "status", "reload", "event", "toll", "anchor", "cast", "seed", "softlight", "tp", "updraft", "fly",
            "vista", "profile");

    private final OriginIsle isle;

    OriginDev(OriginIsle isle) {
        this.isle = isle;
    }

    public String run(Player player, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            isle.menus().openDev(player);
            return null;
        }
        String[] parts = line.split("\\s+");
        String action = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        try {
            return switch (action) {
                case "status" -> status();
                case "reload" -> "§aOrigin reloaded §8· §7" + isle.reload();
                case "event" -> arg.equals("stop") || arg.isEmpty() ? stopEvent() : isle.events().force(arg);
                case "toll" -> {
                    isle.bells().toll("§e♪ §7The bells of Origin ring §8(DEV)§7.", 3);
                    yield "§eTolled.";
                }
                case "anchor" -> anchor(player, arg);
                case "cast" -> switch (arg) {
                    case "presets" -> isle.cast().placeAllPresets();
                    case "remove" -> isle.cast().removeAll();
                    default -> {
                        OriginRole role = OriginRole.byId(arg);
                        yield role == null ? "§7/origin dev cast <presets|remove|role>" : isle.cast().placePreset(role);
                    }
                };
                case "seed" -> "§aSeeded §f" + isle.seedSpawns() + "§a camp location(s) §8(only unset ones)§a.";
                case "softlight" -> softlight(player, arg, parts);
                case "tp" -> tp(player, parts.length > 1 ? parts[1] : "");
                case "updraft" -> {
                    OriginConfig.Updraft draft = isle.config().updrafts().get(arg);
                    if (draft == null) {
                        yield "§7Updrafts: " + String.join(", ", isle.config().updrafts().keySet());
                    }
                    Location floor = isle.config().location(draft.floor());
                    if (floor == null) {
                        yield "§cWorld not loaded.";
                    }
                    player.teleport(floor);
                    isle.traversal().begin(player, draft);
                    yield "§bRiding §f" + draft.name() + "§b.";
                }
                case "fly" -> {
                    OriginConfig.Flight flight = isle.config().flights().get(arg);
                    if (flight == null) {
                        yield "§7Flights: " + String.join(", ", isle.config().flights().keySet());
                    }
                    Location start = isle.config().location(flight.start());
                    if (start == null) {
                        yield "§cWorld not loaded.";
                    }
                    start.setYaw(player.getLocation().getYaw());
                    player.teleport(start);
                    yield isle.flight().begin(player, flight) ? "§bFlying §f" + flight.name() + "§b." : "§cCouldn't start that flight.";
                }
                case "vista" -> {
                    OriginConfig.Vista vista = isle.config().vistas().get(arg);
                    if (vista == null) {
                        isle.vistas().labels(player);
                        yield "§7Labels shown from here.";
                    }
                    isle.vistas().reveal(player, vista);
                    yield null;
                }
                case "profile" -> profile(player, arg);
                default -> "§7DEV actions: " + String.join(", ", ACTIONS);
            };
        } catch (Throwable throwable) {
            isle.plugin().getLogger().warning("Origin DEV '" + line + "': " + throwable);
            return "§cOrigin DEV '" + line + "' failed: §7" + throwable.getMessage();
        }
    }

    private String status() {
        OriginConfig c = isle.config();
        OriginEvents.Kind moment = isle.events().active();
        return "§6Origin §8· " + (isle.running() ? "§arunning" : "§cstopped") + " §8· §7" + c.districts().size() + " districts, "
                + c.landmarks().size() + " landmarks, " + c.waystones().size() + " glowcaps, " + c.vistas().size() + " vistas, "
                + c.bells().size() + " bells, " + c.updrafts().size() + " updrafts, " + c.flights().size() + " flights §8· §7cast "
                + isle.cast().placedCount() + "/" + OriginRole.values().length + " §8· §7on isle " + isle.onIsle().size()
                + " §8· §7flying " + isle.flight().flyers() + ", riding " + isle.traversal().riders()
                + " §8· §7moment " + (moment == null ? "none" : moment.id() + " (" + isle.events().secondsLeft() + "s)")
                + " §8· §7softlight on record " + isle.softlight().recorded();
    }

    private String stopEvent() {
        isle.events().stopAll();
        return "§eMoments stopped (lanterns cleared).";
    }

    private String anchor(Player player, String arg) {
        OriginRole role = OriginRole.byId(arg);
        if (role == null) {
            StringBuilder ids = new StringBuilder();
            for (OriginRole r : OriginRole.values()) {
                ids.append(ids.isEmpty() ? "" : ", ").append(r.id());
            }
            return "§7Anchors: " + ids;
        }
        ItemStack anchor = isle.cast().anchor(role);
        player.getInventory().addItem(anchor).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        return "§aAnchor for " + role.color() + role.display() + " §7— right-click a block to place, sneak-right-click to remove.";
    }

    private String softlight(Player player, String arg, String[] parts) {
        return switch (arg) {
            case "undo" -> isle.softlight().undo(player);
            case "cancel" -> isle.softlight().cancelCommand();
            case "" -> "§7/origin dev softlight <district|all|here [radius]|undo|cancel> §8· §7" + isle.softlight().recorded() + " on record";
            default -> {
                int radius = 48;
                if (arg.equals("here") && parts.length > 2) {
                    try {
                        radius = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                Location at = player.getLocation();
                if (arg.equals("here") && !isle.onIsle(player)) {
                    yield "§cStand on Origin for 'here' (softlight only works in " + isle.config().worldName() + ").";
                }
                yield isle.softlight().run(player, arg, new int[]{at.getBlockX(), at.getBlockZ(), radius});
            }
        };
    }

    private String tp(Player player, String id) {
        Object[] hit = isle.compass().resolve(id);
        if (hit == null || hit[1] == null) {
            return "§7Unknown place. Districts, landmarks, glowcaps, vistas, bells, updraft:x, flight:x, cast:x, camp:x.";
        }
        Location to = ((Location) hit[1]).clone();
        if (to.getYaw() == 0.0f) {
            to.setYaw(player.getLocation().getYaw());
        }
        isle.traversal().grace(player, 5_000L);
        player.teleport(to);
        return "§aTP §8· §f" + hit[0];
    }

    private String profile(Player player, String arg) {
        switch (arg) {
            case "reset" -> {
                isle.wipe(player.getUniqueId());
                return "§eYour Origin profile was wiped. Walk in again.";
            }
            case "complete" -> {
                OriginProfile p = isle.profiles().get(player);
                OriginConfig c = isle.config();
                p.districts.addAll(c.districts().keySet());
                p.landmarks.addAll(c.landmarks().keySet());
                p.waystones.addAll(c.waystones().keySet());
                p.bells.addAll(c.bells().keySet());
                p.vistas.addAll(c.vistas().keySet());
                p.rides.addAll(isle.pads().allRides());
                for (OriginRole role : OriginRole.values()) {
                    p.met.add(role.id());
                }
                p.dirty = true;
                return "§aEverything on Origin marked found (no coins, no titles).";
            }
            default -> {
                OriginProfile p = isle.profiles().get(player);
                return "§7Profile §8· §7districts " + p.districts.size() + ", landmarks " + p.landmarks.size() + ", glowcaps "
                        + p.waystones.size() + ", bells " + p.bells.size() + ", vistas " + p.vistas.size() + ", rides " + p.rides.size()
                        + ", met " + p.met.size() + ", tour " + p.tourStep + ", wishes " + p.wishes + ", coins " + p.coinsEarned
                        + " §8· §7flags " + p.flags;
            }
        }
    }
}
