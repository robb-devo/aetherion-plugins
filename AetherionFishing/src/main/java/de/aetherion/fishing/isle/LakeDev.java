package de.aetherion.fishing.isle;

import de.aetherion.fishing.AetherionFishing;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEV backend for "Fishing Island" (Items renders the pages, this runs the buttons).
 * Actions are {@code group:verb[:arg]}; every reply is a chat line for the clicker.
 */
public final class LakeDev {

    private final FishIsle isle;
    private BukkitTask outlineTask;

    LakeDev(FishIsle isle) {
        this.isle = isle;
    }

    private AetherionFishing plugin() {
        return isle.plugin();
    }

    public String action(Player player, String raw) {
        if (player == null || raw == null || raw.isBlank()) {
            return "§cNo action.";
        }
        String[] parts = raw.split(":", 3);
        String group = parts[0].toLowerCase(Locale.ROOT);
        String verb = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2] : "";
        return switch (group) {
            case "tp" -> teleport(player, raw.substring(3));
            case "npc" -> npc(player, verb, arg);
            case "open" -> open(player, verb);
            case "event" -> switch (verb) {
                case "run" -> isle.events().begin(LakeEvents.Kind.SILVER_RUN);
                case "maw" -> isle.events().begin(LakeEvents.Kind.ELDERMAW);
                case "stop" -> isle.events().stop(false);
                default -> "§cUnknown event: " + verb;
            };
            case "shoal" -> switch (verb) {
                case "here" -> isle.shoals().raiseNear(player.getLocation());
                case "next" -> isle.shoals().raise(null);
                case "clear" -> isle.shoals().clear();
                default -> "§cUnknown shoal verb: " + verb;
            };
            case "line" -> line(player, verb, arg);
            case "log" -> log(player, verb, arg);
            case "waters" -> waters(player, verb);
            case "bait" -> bait(player, verb);
            case "profile" -> {
                isle.profiles().reset(player.getUniqueId());
                isle.line().forget(player.getUniqueId());
                yield "§eYour Fishing Eldervale profile was wiped §7(Log, waters, trophies, rank, bait).";
            }
            case "landing" -> setLanding(player);
            case "config" -> isle.reload();
            default -> "§cUnknown Fishing Island action: " + raw;
        };
    }

    // ------------------------------------------------------------------ groups

    private String teleport(Player player, String key) {
        Location target = spots(player.getWorld(), false).get(key);
        Waters.Water water = null;
        for (Waters.Water candidate : isle.waters().all()) {
            if (candidate.name().equals(key)) {
                water = candidate;
            }
        }
        if (water != null && target != null) {
            target = water.center(target.getWorld());
        }
        if (target == null) {
            return "§cUnknown spot: " + key;
        }
        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
        return "§aTeleported §7→ §f" + key;
    }

    private String npc(Player player, String verb, String arg) {
        if ("preset-all".equals(verb)) {
            StringBuilder out = new StringBuilder("§aCast placed at presets:");
            for (LakeRole role : LakeRole.values()) {
                out.append("\n ").append(isle.cast().placePreset(role));
            }
            return out.toString();
        }
        if ("remove-all".equals(verb)) {
            for (LakeRole role : LakeRole.values()) {
                isle.cast().remove(role);
            }
            return "§eWhole Fishing Eldervale cast removed.";
        }
        LakeRole role = LakeRole.byId(arg);
        if (role == null) {
            return "§cUnknown NPC: " + arg;
        }
        return switch (verb) {
            case "preset" -> isle.cast().placePreset(role);
            case "here" -> {
                Location at = player.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                at.setYaw(player.getLocation().getYaw());
                yield isle.cast().place(role, at);
            }
            case "remove" -> isle.cast().remove(role);
            case "goto" -> {
                Location at = isle.cast().whereabouts(role);
                if (at == null) {
                    yield "§c" + role.display() + " has no spot yet.";
                }
                player.teleport(at.clone().add(at.getDirection().multiply(2.0)).setDirection(at.getDirection().multiply(-1)));
                yield "§aTeleported to §f" + role.display();
            }
            default -> "§cUnknown NPC verb: " + verb;
        };
    }

    private String open(Player player, String roleId) {
        LakeRole role = LakeRole.byId(roleId);
        if (role == null) {
            return "§cNo board for " + roleId;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> isle.menus().open(player, role));
        return null;
    }

    private String line(Player player, String verb, String arg) {
        switch (verb) {
            case "heat" -> {
                int streak;
                try {
                    streak = Integer.parseInt(arg);
                } catch (NumberFormatException ignored) {
                    return "§cHeat needs a streak number.";
                }
                isle.setStreak(player, streak);
                int heat = TheLine.heat(streak);
                return "§aStreak set to §e✦" + streak + (heat == 0 ? "" : " §8· " + TheLine.heatName(heat)) + "§a.";
            }
            case "force" -> {
                Species species = Species.byId(arg);
                if (species == null) {
                    species = switch (arg.toLowerCase(Locale.ROOT)) {
                        case "rare" -> Species.MIRRORBACK_STURGEON;
                        case "legendary" -> Species.GOLDSCALE_EMPEROR;
                        default -> null;
                    };
                }
                if (species == null) {
                    return "§cUnknown species: " + arg;
                }
                isle.line().force(player, species);
                return "§aYour next bite on the isle is a " + species.colored() + "§a.";
            }
            default -> {
                return "§cUnknown line verb: " + verb;
            }
        }
    }

    private String log(Player player, String verb, String arg) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        switch (verb) {
            case "all" -> {
                for (Species species : Species.values()) {
                    AnglerProfiles.Entry entry = profile.log.computeIfAbsent(species, ignored -> new AnglerProfiles.Entry());
                    entry.count = Math.max(1L, entry.count);
                    entry.bestKg = Math.max(entry.bestKg, species.minKg() + (species.maxKg() - species.minKg()) * 0.6d);
                }
                isle.profiles().markDirty();
                return "§aEvery species logged at silver weight §8(rank rewards paid on your next catch).";
            }
            case "reset" -> {
                profile.log.clear();
                profile.rankPaid = 0;
                profile.trophies = 0;
                isle.profiles().markDirty();
                return "§eAngler's Log wiped.";
            }
            case "rank" -> {
                int want;
                try {
                    want = Math.max(0, Math.min(AnglerLog.MAX_RANK, Integer.parseInt(arg)));
                } catch (NumberFormatException ignored) {
                    return "§cRank must be 0–" + AnglerLog.MAX_RANK;
                }
                // Fill the Log in rarity order until the points reach the wanted rank.
                profile.log.clear();
                int target = AnglerLog.RANK_POINTS[want];
                outer:
                for (int tier = 0; tier <= 3; tier++) {
                    for (Species species : Species.values()) {
                        if (isle.log().points(profile) >= target) {
                            break outer;
                        }
                        AnglerProfiles.Entry entry = profile.log.computeIfAbsent(species, ignored -> new AnglerProfiles.Entry());
                        entry.count = Math.max(1L, entry.count);
                        double share = tier == 0 ? 0.1d : tier == 1 ? 0.55d : tier == 2 ? 0.8d : 0.95d;
                        entry.bestKg = species.minKg() + (species.maxKg() - species.minKg()) * share;
                    }
                }
                profile.rankPaid = AnglerLog.rankFor(isle.log().points(profile));
                isle.profiles().markDirty();
                int now = AnglerLog.rankFor(isle.log().points(profile));
                return "§aAngler Rank set to §f" + LakeText.roman(now) + " " + AnglerLog.coloredTitle(now) + " §8(no rewards paid).";
            }
            default -> {
                return "§cUnknown log verb: " + verb;
            }
        }
    }

    private String waters(Player player, String verb) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        return switch (verb) {
            case "all" -> {
                for (Waters.Water water : isle.waters().all()) {
                    profile.waters.add(water.id());
                }
                isle.profiles().markDirty();
                yield "§aAll " + isle.waters().size() + " waters marked discovered §8(Waterfinder not paid).";
            }
            case "reset" -> {
                profile.waters.clear();
                profile.waterfinder = false;
                isle.profiles().markDirty();
                yield "§eWater discovery reset — walk them again.";
            }
            case "where" -> {
                Waters.Water water = isle.waters().at(player.getLocation());
                boolean onIsle = LakeWorld.onIsle(player);
                yield water == null
                        ? (onIsle ? "§7On the isle, not at a named water §8(y " + player.getLocation().getBlockY() + ")."
                        : "§7Not on the Fishing Eldervale footprint.")
                        : "§7You are at " + water.colored() + " §8(" + water.kind().label() + ", id " + water.id() + ")";
            }
            case "show" -> showOutlines(player);
            default -> "§cUnknown waters verb: " + verb;
        };
    }

    private String bait(Player player, String verb) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if ("clear".equals(verb)) {
            profile.bait.clear();
            profile.activeBait = null;
            isle.profiles().markDirty();
            return "§eBait tin emptied.";
        }
        for (Bait bait : Bait.values()) {
            profile.bait.merge(bait, 16, (a, b) -> Math.min(BaitShack.MAX_CHARGES, a + b));
        }
        isle.profiles().markDirty();
        return "§a+16 of every bait §7(pick one at the Bait Shack board).";
    }

    private String setLanding(Player player) {
        Location at = player.getLocation();
        String path = "fish-isle.landing";
        plugin().getConfig().set(path + ".world", at.getWorld() == null ? "world" : at.getWorld().getName());
        plugin().getConfig().set(path + ".x", Math.round(at.getX() * 10.0d) / 10.0d);
        plugin().getConfig().set(path + ".y", Math.round(at.getY() * 10.0d) / 10.0d);
        plugin().getConfig().set(path + ".z", Math.round(at.getZ() * 10.0d) / 10.0d);
        plugin().getConfig().set(path + ".yaw", Math.round(at.getYaw()));
        plugin().saveConfig();
        return "§aLanding set here §8(" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + ") §7— point the hub pad / §f/hubadmin set §7spawn at it.";
    }

    /** 20 seconds of particle rings for every water circle near the player; the shoal gets a ring too. */
    private String showOutlines(Player player) {
        if (outlineTask != null) {
            outlineTask.cancel();
        }
        World world = player.getWorld();
        int[] ticks = {0};
        outlineTask = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            ticks[0] += 10;
            if (ticks[0] > 400 || !player.isOnline()) {
                outlineTask.cancel();
                outlineTask = null;
                return;
            }
            double y = player.getLocation().getY() + 0.5;
            for (Waters.Water water : isle.waters().all()) {
                Particle particle = switch (water.kind()) {
                    case FOUNTAIN -> Particle.WAX_ON;
                    case TARN -> Particle.HAPPY_VILLAGER;
                    case LAKE -> Particle.SPLASH;
                };
                for (Waters.Circle circle : water.circles()) {
                    double dx = circle.x() - player.getX();
                    double dz = circle.z() - player.getZ();
                    if (dx * dx + dz * dz > (circle.radius() + 60) * (circle.radius() + 60)) {
                        continue;
                    }
                    int points = (int) Math.max(24, circle.radius() * 3);
                    for (int i = 0; i < points; i++) {
                        double angle = Math.PI * 2 * i / points;
                        player.spawnParticle(particle, new Location(world, circle.x() + Math.cos(angle) * circle.radius(), y,
                                circle.z() + Math.sin(angle) * circle.radius()), 1, 0, 0, 0, 0);
                    }
                }
            }
            for (Shoals.Spot spot : isle.shoals().spots()) {
                player.spawnParticle(Particle.FLAME, spot.at().clone().add(0, 1.0, 0), 3, 0.1, 0.4, 0.1, 0.0);
            }
        }, 0L, 10L);
        return "§aShowing water outlines for 20s §8(splash lake · green tarn · wax fountain · flame shoal spot).";
    }

    // ------------------------------------------------------------------ lists

    public Map<String, ItemStack> items(String group) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
            case "npcs" -> {
                for (LakeRole role : LakeRole.values()) {
                    out.put(role.id(), isle.cast().anchor(role));
                }
            }
            case "trophies" -> {
                for (Species species : Species.values()) {
                    out.put(species.id(), isle.trophies().item(species, species.minKg() + (species.maxKg() - species.minKg()) * 0.93d));
                }
                out.put("eldermaw_scale", isle.trophies().scale());
            }
            default -> {
            }
        }
        return out;
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        String footprint = LakeWorld.describe();
        lines.add(footprint == null ? "§cFootprint: off / world missing" : "§7Footprint: §f" + footprint);
        lines.add("§7Anglers on the isle: §f" + LakeWorld.anglers().size() + " §8(visitors " + LakeWorld.visitors().size() + ")");
        lines.add("§7Waters: §f" + isle.waters().size() + " §8· §7NPCs placed: §f" + isle.cast().placedCount()
                + "§7/§f" + LakeRole.values().length + " §8· §7Species: §f" + Species.values().length);
        lines.add(isle.shoals().statusLine());
        lines.add(isle.events().statusLine());
        return lines;
    }

    /** Landing, every NPC (placed or preset), every water centre and the live shoal. */
    public Map<String, Location> spots(World fallback, boolean resolveY) {
        Map<String, Location> out = new LinkedHashMap<>();
        Location landing = LakeWorld.landing(plugin());
        if (landing != null) {
            out.put("Landing", landing);
        }
        for (LakeRole role : LakeRole.values()) {
            Location at = isle.cast().whereabouts(role);
            if (at != null) {
                out.put(role.display(), at.clone().add(at.getDirection().multiply(2.0)).setDirection(at.getDirection().multiply(-1)));
            }
        }
        World world = LakeWorld.world();
        if (world == null) {
            world = fallback;
        }
        for (Waters.Water water : isle.waters().forBoards()) {
            if (world == null || water.circles().isEmpty()) {
                continue;
            }
            Waters.Circle main = water.circles().get(0);
            out.put(water.name(), resolveY ? water.center(world) : new Location(world, main.x(), 0, main.z()));
        }
        Shoals.Spot shoal = isle.shoals().current();
        if (shoal != null) {
            out.put("Shoal", shoal.at().clone().add(0, 3, 0));
        }
        return out;
    }
}
