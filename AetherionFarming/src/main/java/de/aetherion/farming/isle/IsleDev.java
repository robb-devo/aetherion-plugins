package de.aetherion.farming.isle;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.FeaturedCropService;
import de.aetherion.farming.dev.FarmDistrictMarker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEV menu backend for "Farming Island" (Items renders the pages, this runs the buttons).
 * Actions are {@code group:verb[:arg]}; every reply is a chat line for the clicker.
 */
public final class IsleDev {

    private final FarmIsle isle;
    private BukkitTask outlineTask;

    IsleDev(FarmIsle isle) {
        this.isle = isle;
    }

    private AetherionFarming plugin() {
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
            case "event" -> event(player, verb);
            case "prize" -> prize(player, verb);
            case "rhythm" -> {
                isle.rhythm().max(player);
                yield "§dRhythm maxed §7— harvest within the hold window to keep it.";
            }
            case "mastery" -> mastery(player, verb);
            case "plots" -> plots(player, verb);
            case "orders" -> {
                isle.orders().resetBoard(player);
                yield "§aOrder board reset §7(cooldowns cleared, fresh orders).";
            }
            case "food" -> {
                IsleProfiles.Profile profile = isle.profiles().of(player);
                profile.food = null;
                profile.foodUntil = 0L;
                isle.profiles().markDirty();
                yield "§eFood buff cleared.";
            }
            case "profile" -> {
                isle.rhythm().clear(player.getUniqueId());
                isle.profiles().reset(player.getUniqueId());
                yield "§eYour Eldervale profile was wiped §7(plots, mastery, prizes, orders, food).";
            }
            case "config" -> isle.reload();
            case "seed" -> plugin().seeder() == null ? "§cSeeder offline." : plugin().seeder().start("force".equals(verb));
            default -> "§cUnknown Farming Island action: " + raw;
        };
    }

    // ------------------------------------------------------------------ groups

    private String teleport(Player player, String key) {
        Location target = spots(player.getWorld(), false).get(key);
        IslePlots.Plot plot = null;
        for (IslePlots.Plot candidate : isle.plots().all()) {
            if (candidate.name().equals(key)) {
                plot = candidate;
            }
        }
        if (plot != null && target != null) {
            // Only the chosen plot pays for a surface lookup.
            target = plot.center(target.getWorld());
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
            for (IsleRole role : IsleRole.values()) {
                out.append("\n ").append(isle.cast().placePreset(role));
            }
            return out.toString();
        }
        if ("remove-all".equals(verb)) {
            for (IsleRole role : IsleRole.values()) {
                isle.cast().remove(role);
            }
            return "§eWhole Eldervale cast removed.";
        }
        IsleRole role = IsleRole.byId(arg);
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
        IsleRole role = IsleRole.byId(roleId);
        if (role == null || role.flavorOnly()) {
            return "§cNo board for " + roleId;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> isle.menus().open(player, role));
        return null;
    }

    private String event(Player player, String verb) {
        return switch (verb) {
            case "bloom" -> {
                IslePlots.Plot here = isle.plots().at(player.getLocation());
                IslePlots.Plot field = here != null && here.kind() == IslePlots.Kind.FIELD ? here : null;
                yield isle.events().begin(IsleEvents.Kind.BEE_BLOOM, field);
            }
            case "moon" -> isle.events().begin(IsleEvents.Kind.HARVEST_MOON, null);
            case "stop" -> isle.events().stop(false);
            case "birds" -> plugin().birdScare() == null ? "§cBird scare offline (AetherionItems missing?)."
                    : plugin().birdScare().devStart(player);
            case "scarecrow" -> plugin().scarecrowEvent() == null ? "§cScarecrow minigame offline."
                    : plugin().scarecrowEvent().devStart(player);
            case "featured" -> {
                FeaturedCropService featured = plugin().featuredCrop();
                yield featured == null ? "§cFeatured crop is disabled." : featured.reroll();
            }
            default -> "§cUnknown event: " + verb;
        };
    }

    private String prize(Player player, String verb) {
        IsleCrop crop = IsleCrop.byId(verb);
        Block target = player.getTargetBlockExact(6);
        Location at = target != null ? target.getLocation() : player.getLocation().add(player.getLocation().getDirection().multiply(2));
        if (crop == null) {
            IsleCrop under = target == null ? null : IsleCrop.fromBlock(target.getType());
            crop = under != null ? under : IsleCrop.CARROT;
        }
        double kg = crop.minKg() + (crop.maxKg() - crop.minKg()) * Math.random();
        isle.prizes().spawn(player, crop, kg, at);
        return "§6Prize " + crop.display() + " §7popped §8(" + IsleText.kg(kg) + ")";
    }

    private String mastery(Player player, String verb) {
        if ("reset".equals(verb)) {
            isle.mastery().setAll(player, 0);
            return "§eCrop mastery reset to 0.";
        }
        try {
            int tier = Integer.parseInt(verb);
            isle.mastery().setAll(player, tier);
            return "§aAll crops set to Mastery " + IsleText.roman(Math.max(0, Math.min(CropMastery.MAX_TIER, tier)))
                    + " §8(no rewards paid).";
        } catch (NumberFormatException ignored) {
            return "§cMastery tier must be 0–" + CropMastery.MAX_TIER;
        }
    }

    private String plots(Player player, String verb) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        return switch (verb) {
            case "all" -> {
                for (IslePlots.Plot plot : isle.plots().all()) {
                    profile.plots.add(plot.id());
                }
                isle.profiles().markDirty();
                yield "§aAll " + isle.plots().size() + " plots marked discovered §8(Cartographer not paid).";
            }
            case "reset" -> {
                profile.plots.clear();
                profile.cartographer = false;
                isle.profiles().markDirty();
                yield "§ePlot discovery reset — walk them again.";
            }
            case "where" -> {
                IslePlots.Plot plot = isle.plots().at(player.getLocation());
                boolean onIsle = IsleWorld.onIsle(plugin(), player);
                yield plot == null
                        ? (onIsle ? "§7On the isle, outside every named plot." : "§7Not on the Farm Isle footprint.")
                        : "§7You are in " + plot.colored() + " §8(" + plot.kind().name().toLowerCase(Locale.ROOT) + ", id " + plot.id() + ")";
            }
            case "show" -> showOutlines(player);
            default -> "§cUnknown plots verb: " + verb;
        };
    }

    /** 20 seconds of particle rings for every plot circle near the player. */
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
            for (IslePlots.Plot plot : isle.plots().all()) {
                Particle particle = switch (plot.kind()) {
                    case LANDMARK -> Particle.FLAME;
                    case WATER -> Particle.SPLASH;
                    case FIELD -> Particle.HAPPY_VILLAGER;
                };
                for (IslePlots.Circle circle : plot.circles()) {
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
        }, 0L, 10L);
        return "§aShowing plot outlines for 20s §8(flame = landmark, splash = water, green = field).";
    }

    // ------------------------------------------------------------------ lists

    public Map<String, ItemStack> items(String group) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
            case "npcs" -> {
                for (IsleRole role : IsleRole.values()) {
                    out.put(role.id(), isle.cast().anchor(role));
                }
            }
            case "foods" -> {
                for (Bakehouse.Food food : Bakehouse.Food.values()) {
                    out.put(food.id(), isle.bakehouse().item(food));
                }
            }
            case "prizes" -> {
                for (IsleCrop crop : IsleCrop.values()) {
                    out.put(crop.id(), isle.prizes().item(crop, crop.minKg() + (crop.maxKg() - crop.minKg()) * 0.7d));
                }
            }
            case "props" -> {
                AetherionFarming plugin = plugin();
                putIfPresent(out, "scarecrow", plugin.scarecrow() == null ? null : plugin.scarecrow().create());
                putIfPresent(out, "hay_wagon", plugin.hayWagon() == null ? null : plugin.hayWagon().create());
                putIfPresent(out, "cane_patch", plugin.canePatchTool() == null ? null : plugin.canePatchTool().create());
                if (plugin.districtMarker() != null) {
                    for (FarmDistrictMarker.District district : FarmDistrictMarker.District.values()) {
                        putIfPresent(out, "district_" + district.name().toLowerCase(Locale.ROOT),
                                plugin.districtMarker().create(district));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static void putIfPresent(Map<String, ItemStack> out, String id, ItemStack item) {
        if (item != null) {
            out.put(id, item);
        }
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        var footprint = de.aetherion.farming.island.FarmIsleZones.footprint(plugin());
        lines.add(footprint == null ? "§cFootprint: off / world missing"
                : "§7Footprint: §f" + footprint.world().getName() + " §8x " + footprint.minX() + "…" + footprint.maxX()
                + " z " + footprint.minZ() + "…" + footprint.maxZ());
        lines.add("§7Farmers on the isle: §f" + IsleWorld.farmers(plugin()).size()
                + " §8(visitors " + IsleWorld.visitors(plugin()).size() + ")");
        lines.add("§7Plots: §f" + isle.plots().size() + " §8· §7NPCs placed: §f" + isle.cast().placedCount()
                + "§7/§f" + IsleRole.values().length);
        FeaturedCropService featured = plugin().featuredCrop();
        if (featured != null) {
            lines.add("§7Featured: §e" + featured.prettyName() + " §8(" + IsleText.clock(featured.secondsLeft()) + ")");
        }
        lines.add(isle.events().statusLine());
        return lines;
    }

    /**
     * Landing, every NPC (placed or preset) and every plot centre. With {@code resolveY} false the
     * plot centres keep y = 0 so listing the spots never loads chunks.
     */
    public Map<String, Location> spots(World fallback, boolean resolveY) {
        Map<String, Location> out = new LinkedHashMap<>();
        Location landing = IsleWorld.landing(plugin());
        if (landing != null) {
            out.put("Landing", landing);
        }
        for (IsleRole role : IsleRole.values()) {
            Location at = isle.cast().whereabouts(role);
            if (at != null) {
                out.put(role.display(), at.clone().add(at.getDirection().multiply(2.0)).setDirection(at.getDirection().multiply(-1)));
            }
        }
        World world = IsleWorld.world(plugin());
        if (world == null) {
            world = fallback;
        }
        for (IslePlots.Plot plot : isle.plots().all()) {
            if (world == null || plot.circles().isEmpty()) {
                continue;
            }
            IslePlots.Circle main = plot.circles().get(0);
            out.put(plot.name(), resolveY ? plot.center(world) : new Location(world, main.x(), 0, main.z()));
        }
        return out;
    }
}
