package de.aetherion.mining.isle;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEV backend for "Mining Island". AetherionItems renders the pages and this class runs the
 * buttons. The same actions are available from {@code /mineisle dev <action>}. Actions look like
 * {@code group:verb[:arg]}, and every reply is a chat line for whoever clicked.
 */
public final class MineDev {

    private final MineIsle isle;

    MineDev(MineIsle isle) {
        this.isle = isle;
    }

    private AetherionMining plugin() {
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
            case "event" -> event(verb);
            case "rhythm" -> {
                isle.rhythm().max(player);
                yield "§6Strike Rhythm → Anvil Chorus.";
            }
            case "find" -> find(player, verb, arg);
            case "mastery" -> {
                int tier = parse(verb, 0);
                isle.mastery().setAll(player, tier);
                yield "§6Every ore family → Mastery " + MineText.roman(tier) + " §8(no rewards paid)";
            }
            case "cabinet" -> {
                isle.ledger().devCabinet(player, "fill".equals(verb));
                yield "fill".equals(verb) ? "§dSpecimen Cabinet filled." : "§7Specimen Cabinet cleared.";
            }
            case "claims" -> {
                isle.ledger().devResetClaims(player);
                yield "§7Collection claims reset (claim again at Ilse).";
            }
            case "contracts" -> {
                if ("fill".equals(verb)) {
                    isle.contracts().fillShifts(player);
                    yield "§6Every shift on your board is done.";
                }
                isle.contracts().resetBoard(player);
                yield "§6Fresh contract board, cooldowns cleared.";
            }
            case "forge" -> forge(player, verb, arg);
            case "critter" -> critter(player, verb);
            case "hazard" -> {
                isle.hazards().devCollapse(player);
                yield "§cCave-in telegraphed on you. Move!";
            }
            case "depth" -> {
                MineProfiles.Profile profile = isle.profiles().of(player);
                for (MineDistricts.Band band : MineDistricts.Band.values()) {
                    if (band != MineDistricts.Band.SURFACE) {
                        profile.bands.add(band.name());
                    }
                }
                isle.profiles().markDirty();
                yield "§3Every depth band marked as reached.";
            }
            case "streak" -> {
                MineProfiles.Profile profile = isle.profiles().of(player);
                profile.streak = Math.max(0, profile.streak + parse(verb, 1));
                profile.streakDay = java.time.LocalDate.now().toEpochDay();
                isle.profiles().markDirty();
                yield "§6Shift streak → " + profile.streak + " §8(today logged)";
            }
            case "districts" -> districts(player, verb);
            case "profile" -> {
                isle.profiles().reset(player.getUniqueId());
                isle.rhythm().clear(player.getUniqueId());
                yield "§eYour Mining Eldervale profile was wiped §7(districts, mastery, cabinet, contracts, marks, rep, depth).";
            }
            case "props" -> {
                isle.props().rebuild();
                yield "§aProps rebuilt.";
            }
            case "prop" -> isle.props().placeHere(verb, player);
            case "landing" -> setLanding(player);
            case "config" -> isle.reload();
            case "where" -> where(player);
            case "give" -> {
                for (ItemStack item : items(verb).values()) {
                    MineSkills.give(player, item.clone());
                }
                yield "§aGave the " + verb + " set.";
            }
            default -> "§cUnknown Mining Island action: " + raw;
        };
    }

    // ------------------------------------------------------------------ groups

    private String teleport(Player player, String key) {
        Location target = spots(true).get(key);
        if (target == null) {
            return "§cUnknown spot: " + key;
        }
        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
        return "§aTeleported §7→ §f" + key;
    }

    private String npc(Player player, String verb, String arg) {
        if ("preset-all".equals(verb)) {
            return isle.cast().placeAllPresets();
        }
        if ("remove-all".equals(verb)) {
            return isle.cast().removeAll();
        }
        MineRole role = MineRole.byId(arg);
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
            case "anchor" -> {
                MineSkills.give(player, isle.cast().anchor(role));
                yield "§aAnchor for " + role.display() + ".";
            }
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

    private String open(Player player, String id) {
        if ("journal".equals(id)) {
            isle.menus().openJournal(player);
            return "";
        }
        MineRole role = MineRole.byId(id);
        if (role == null) {
            return "§cNo board for " + id;
        }
        isle.menus().open(player, role);
        return "";
    }

    private String event(String verb) {
        return switch (verb) {
            case "rich" -> isle.events().begin(MineEvents.Kind.RICH_VEIN, null);
            case "ember" -> isle.events().begin(MineEvents.Kind.EMBER_HOUR, null);
            case "tremor" -> isle.events().begin(MineEvents.Kind.TREMOR, null);
            case "troll" -> isle.events().begin(MineEvents.Kind.TROLL_RUN, null);
            case "stop" -> isle.events().stop(false);
            default -> "§cUnknown event: " + verb;
        };
    }

    private String find(Player player, String oreId, String gradeId) {
        IsleOre ore = IsleOre.byId(oreId);
        if (ore == null || !ore.hasCrystal()) {
            ore = IsleOre.DIAMOND;
        }
        Grade grade = Grade.byId(gradeId);
        if (grade == null) {
            grade = Grade.PERFECT;
        }
        Location front = player.getEyeLocation().add(flatFacing(player).multiply(2.0));
        double carats = Math.round((ore.minCarat() + ore.maxCarat()) / 2.0d * 100.0d) / 100.0d;
        isle.crystals().spawn(player, ore, grade, carats, front.getBlock().getLocation(),
                grade == Grade.HEARTSTONE || (plugin().getVeins() != null && plugin().getVeins().isVeins(player.getWorld())));
        return "§dCrystal Find spawned: " + grade.colored() + " " + ore.color() + ore.crystalName();
    }

    private String forge(Player player, String verb, String arg) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        switch (verb) {
            case "rep" -> {
                long amount = parse(arg, 1_000);
                isle.forge().addRep(player, amount, "DEV");
                return "§6+" + amount + " Forge Reputation → " + isle.forge().rank(player).colored();
            }
            case "max" -> {
                for (ForgeWorks.Mark mark : ForgeWorks.Mark.values()) {
                    profile.marks.put(mark.id(), mark.max());
                }
                profile.forgeRep = Math.max(profile.forgeRep, ForgeWorks.Rank.FORGELORD.from());
                isle.profiles().markDirty();
                return "§6Every Forge Mark maxed, rank Forgelord.";
            }
            case "reset" -> {
                profile.marks.clear();
                profile.forgeRep = 0L;
                profile.forged = 0;
                isle.profiles().markDirty();
                return "§7Forge Marks and Reputation cleared.";
            }
            case "tool" -> {
                ForgeWorks.Tool tool = ForgeWorks.Tool.byId(arg);
                if (tool == null) {
                    return "§cUnknown tool: " + arg;
                }
                MineSkills.give(player, isle.forge().tool(tool, 8));
                return "§a8× " + tool.colored();
            }
            case "burst" -> {
                isle.props().forgeBurst(true);
                return "§6The Deep Forge roars.";
            }
            default -> {
                return "§cUnknown forge verb: " + verb;
            }
        }
    }

    private String critter(Player player, String kindId) {
        MineCritters.Kind kind = MineCritters.Kind.byId(kindId);
        if (kind == null) {
            return "§cUnknown critter: " + kindId;
        }
        Location front = player.getLocation().add(flatFacing(player).multiply(3.0));
        Location spot = kind.flies ? front.add(0, 1.2, 0) : MineCritters.floorNear(front, 3);
        if (spot == null) {
            return "§cNo room for a " + kind.display + " here.";
        }
        return isle.critters().spawn(kind, spot, player) != null ? "§a" + kind.colored() + " §7spawned." : "§cCould not spawn.";
    }

    private String districts(Player player, String verb) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if ("all".equals(verb)) {
            for (MineDistricts.District district : isle.districts().all()) {
                profile.districts.add(district.id());
            }
            isle.profiles().markDirty();
            return "§eEvery district marked discovered §8(Cartographer not paid).";
        }
        profile.districts.clear();
        profile.surveyor = false;
        isle.profiles().markDirty();
        return "§7District discovery reset.";
    }

    private String setLanding(Player player) {
        Location at = player.getLocation();
        String path = "mine-isle.landing";
        plugin().getConfig().set(path + ".world", at.getWorld().getName());
        plugin().getConfig().set(path + ".x", Math.round(at.getX() * 10.0) / 10.0);
        plugin().getConfig().set(path + ".y", Math.round(at.getY() * 10.0) / 10.0);
        plugin().getConfig().set(path + ".z", Math.round(at.getZ() * 10.0) / 10.0);
        plugin().getConfig().set(path + ".yaw", Math.round(at.getYaw()));
        plugin().saveConfig();
        return "§aMining Eldervale landing set here §7(mine-isle.landing).";
    }

    private String where(Player player) {
        Location at = player.getLocation();
        MineDistricts.District district = isle.districts().at(at);
        MineDistricts.Band band = isle.districts().band(at);
        boolean onIsle = MineWorld.onIsle(plugin(), at);
        return "§7On isle: " + (onIsle ? "§ayes" : "§cno") + " §8· §7District: "
                + (district == null ? "§8none" : district.colored()) + " §8· §7Band: " + band.colored()
                + " §8· §7Y " + at.getBlockY() + "\n" + MineWorld.describe(plugin());
    }

    // ------------------------------------------------------------------ data for Items

    public Map<String, ItemStack> items(String group) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
            case "npcs" -> {
                for (MineRole role : MineRole.values()) {
                    out.put(role.id(), isle.cast().anchor(role));
                }
            }
            case "rations" -> {
                for (Hearth.Ration ration : Hearth.Ration.values()) {
                    out.put(ration.id(), isle.hearth().item(ration));
                }
            }
            case "tools" -> {
                for (ForgeWorks.Tool tool : ForgeWorks.Tool.values()) {
                    out.put(tool.id(), isle.forge().tool(tool, 8));
                }
                out.put("stonejaw_jaw", isle.critters().jaw());
            }
            case "specimens" -> {
                for (IsleOre ore : IsleOre.crystals()) {
                    Grade grade = ore.rare() ? Grade.FLAWLESS : Grade.ROUGH;
                    out.put(ore.id(), isle.crystals().item(ore, grade, (ore.minCarat() + ore.maxCarat()) / 2.0d));
                }
                out.put("heartstone", isle.crystals().item(IsleOre.AMETHYST, Grade.HEARTSTONE, 30.0d));
            }
            default -> {
            }
        }
        return out;
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add(MineWorld.describe(plugin()));
        lines.add("§7Districts: §f" + isle.districts().size() + " §8· §7Cast placed: §f" + isle.cast().placedCount() + "§7/6");
        lines.add(isle.events().statusLine());
        lines.add("§7Critters alive: §f" + isle.critters().count());
        var veins = plugin().getVeins();
        if (veins != null) {
            lines.add("§dAmethyst Mine §8· §7generation §f" + veins.generation() + " §8· §7reset in §f"
                    + MineText.clock(Math.max(0L, (veins.nextResetAt() - System.currentTimeMillis()) / 1000L)));
        }
        return lines;
    }

    /** Label → location: landing, cast, every district. {@code resolveY} keeps real Ys (districts use their centre). */
    public Map<String, Location> spots(boolean resolveY) {
        Map<String, Location> out = new LinkedHashMap<>();
        Location landing = MineWorld.landing(plugin());
        if (landing != null) {
            out.put("Landing", landing);
        }
        for (MineRole role : MineRole.values()) {
            Location at = isle.cast().whereabouts(role);
            if (at != null) {
                out.put(role.display(), at);
            }
        }
        World world = isle.world();
        for (MineDistricts.District district : isle.districts().all()) {
            Location center = district.center(world);
            if (center != null) {
                out.put(district.name(), center);
            }
        }
        return out;
    }

    /** Horizontal facing (never NaN, even looking straight up or down). */
    private static org.bukkit.util.Vector flatFacing(Player player) {
        Location flat = player.getLocation().clone();
        flat.setPitch(0.0f);
        return flat.getDirection();
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }
}
