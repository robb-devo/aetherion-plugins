package de.aetherion.mining.isle;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walking Mining Eldervale. It names the hall, quarry or shaft you step into and pays a discovery
 * reward the first time. Find every place and you get Cartographer. Crossing into a new depth band
 * shows a depth card. On request it points an arrow (with a height hint) at a district or an NPC
 * until you arrive. First-time visitors get walked through the cast: Old Wick, then Otto, Nan,
 * Ilse, Brann and finally Hollis at the Last Lamp.
 */
public final class MineCompass {

    private static final int DISCOVERY_XP = 30;
    private static final long CARTOGRAPHER_COINS = 4_000L;
    private static final int CARTOGRAPHER_XP = 600;
    private static final long GUIDE_MS = 180_000L;
    private static final double ARRIVE_DISTANCE = 5.0d;
    private static final List<MineRole> TOUR = List.of(
            MineRole.LAMPWARDEN, MineRole.CLERK, MineRole.COOK, MineRole.ASSAYER, MineRole.FORGEMASTER, MineRole.HERMIT);

    private record Guide(String label, Location target, long until) {
    }

    private final MineIsle isle;
    private final Map<UUID, String> lastDistrict = new ConcurrentHashMap<>();
    private final Map<UUID, MineDistricts.Band> lastBand = new ConcurrentHashMap<>();
    private final Map<UUID, Guide> guides = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> welcomed = new ConcurrentHashMap<>();
    private BukkitTask task;

    MineCompass(MineIsle isle) {
        this.isle = isle;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 20L, 10L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastDistrict.clear();
        lastBand.clear();
        guides.clear();
    }

    void forget(UUID id) {
        lastDistrict.remove(id);
        lastBand.remove(id);
        guides.remove(id);
        welcomed.remove(id);
    }

    // ------------------------------------------------------------------ guiding

    public void guide(Player player, String label, Location target) {
        if (target == null || target.getWorld() == null) {
            MineText.bar(player, "§cThat spot isn't set up yet.");
            return;
        }
        guides.put(player.getUniqueId(), new Guide(label, target.clone(), System.currentTimeMillis() + GUIDE_MS));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        MineText.bar(player, "§e⚑ Compass set §8· §f" + label);
    }

    public void guide(Player player, MineRole role) {
        guide(player, role.color() + role.display() + " §7(" + role.title() + ")", isle.cast().whereabouts(role));
    }

    public void guide(Player player, MineDistricts.District district) {
        if (district == null) {
            return;
        }
        guide(player, district.colored(), district.center(isle.world()));
    }

    public void stop(Player player) {
        guides.remove(player.getUniqueId());
        MineText.bar(player, "§7Compass cleared.");
    }

    public boolean guiding(Player player) {
        return guides.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ the tour

    /** First right-click on an NPC: small reward and the next stop on the tour. */
    void met(Player player, MineRole role) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        if (!profile.met.add(role.id())) {
            return;
        }
        isle.profiles().markDirty();
        MineSkills.bonus(player, 20);
        Guide current = guides.get(player.getUniqueId());
        if (current != null && current.target().getWorld() != null
                && current.target().distanceSquared(player.getLocation()) < 144.0d) {
            guides.remove(player.getUniqueId());
        }
        MineRole next = null;
        for (MineRole stop : TOUR) {
            if (!profile.met.contains(stop.id()) && isle.cast().isPlaced(stop)) {
                next = stop;
                break;
            }
        }
        if (next == null) {
            if (profile.met.size() >= TOUR.size()) {
                player.sendMessage("§6⚒ You've met the whole Eldervale crew. §7Now go make them rich.");
                MineSkills.bonus(player, 150);
            }
            return;
        }
        MineRole target = next;
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (player.isOnline()) {
                player.sendMessage("§8Tour · §7Next: " + target.color() + target.display() + " §7(" + target.title()
                        + ") §8— §eclick for the compass arrow §8(/mineisle tour)");
            }
        }, 60L);
    }

    /** Point at the next NPC on the tour. */
    public void tour(Player player) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        for (MineRole stop : TOUR) {
            if (!profile.met.contains(stop.id()) && isle.cast().isPlaced(stop)) {
                guide(player, stop);
                return;
            }
        }
        MineText.bar(player, "§7You've met everyone. §8Try /mineisle to plan a dig.");
    }

    // ------------------------------------------------------------------ ticking

    private void tick() {
        long now = System.currentTimeMillis();
        boolean slow = Bukkit.getCurrentTick() % 20 < 10;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Guide guide = guides.get(id);
            if (guide != null) {
                tickGuide(player, guide, now);
            }
            if (!slow) {
                continue;
            }
            if (!MineWorld.onIsle(isle.plugin(), player)) {
                lastDistrict.remove(id);
                lastBand.remove(id);
                continue;
            }
            if (!welcomed.containsKey(id)) {
                welcomed.put(id, Boolean.TRUE);
                MineProfiles.Profile profile = isle.profiles().of(player);
                if (profile.met.isEmpty() && profile.districts.isEmpty() && !profile.compassOff()) {
                    welcome(player);
                }
            }
            MineDistricts.Band band = isle.districts().band(player.getLocation());
            MineDistricts.Band beforeBand = lastBand.put(id, band);
            if (beforeBand != null && beforeBand != band && guide == null && !isle.profiles().of(player).compassOff()) {
                depthCard(player, band);
            }
            MineDistricts.District district = isle.districts().at(player.getLocation());
            String districtId = district == null ? null : district.id();
            String before = lastDistrict.get(id);
            if (districtId == null) {
                lastDistrict.remove(id);
                continue;
            }
            if (districtId.equals(before)) {
                continue;
            }
            lastDistrict.put(id, districtId);
            enter(player, district, guide == null);
        }
    }

    private void tickGuide(Player player, Guide guide, long now) {
        if (now > guide.until() || !player.getWorld().equals(guide.target().getWorld())) {
            guides.remove(player.getUniqueId());
            return;
        }
        double distance = player.getLocation().distance(guide.target());
        if (distance <= ARRIVE_DISTANCE) {
            guides.remove(player.getUniqueId());
            MineText.bar(player, "§aYou've reached §f" + guide.label() + "§a.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.7f, 1.5f);
            return;
        }
        MineText.bar(player, "§e" + MineText.arrow(player, guide.target()) + " §f" + guide.label()
                + " §8· §7" + (int) Math.round(distance) + "m" + MineText.vertical(player, guide.target()));
        if (Bukkit.getCurrentTick() % 40 < 10) {
            Location from = player.getLocation().add(0, 1.2, 0);
            var step = guide.target().toVector().subtract(from.toVector()).normalize().multiply(2.2);
            player.spawnParticle(Particle.SMALL_FLAME, from.add(step), 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void enter(Player player, MineDistricts.District district, boolean announce) {
        MineProfiles.Profile profile = isle.profiles().of(player);
        boolean fresh = profile.districts.add(district.id());
        if (!fresh) {
            cartographer(player, profile);
        }
        if (fresh) {
            isle.profiles().markDirty();
            int found = countKnown(profile);
            int total = isle.districts().size();
            player.showTitle(Title.title(
                    MineText.legacy(district.colored()),
                    MineText.legacy("§7Discovered §8· §f" + found + "§7/§f" + total
                            + (district.blurb().isBlank() ? "" : " §8· §7" + district.blurb())),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 0.9f);
            player.playSound(player.getLocation(), district.kind() == MineDistricts.Kind.SHAFT
                    ? Sound.AMBIENT_CAVE : Sound.BLOCK_LANTERN_PLACE, SoundCategory.PLAYERS, 0.7f, 1.0f);
            MineSkills.bonus(player, DISCOVERY_XP * (district.kind() == MineDistricts.Kind.SHAFT ? 2 : 1));
            cartographer(player, profile);
            return;
        }
        if (announce) {
            MineText.bar(player, district.colored() + (district.blurb().isBlank() ? "" : " §8· §7" + district.blurb()));
        }
    }

    private void depthCard(Player player, MineDistricts.Band band) {
        double fortune = band.fortune() * (1.0d + 0.5d * MineSkills.scale(player, MineSkills.DEPTH_GAUGE));
        String extra = band == MineDistricts.Band.SURFACE
                ? "§7Back in daylight."
                : "§e+" + MineText.num(fortune) + " ore Fortune §8· §dCrystal Finds ×" + MineText.num(band.crystalMult())
                + (band.bonusXp() > 0 ? " §8· §a+" + band.bonusXp() + " XP/ore" : "");
        MineText.bar(player, band.colored() + " §8· §7Y " + player.getLocation().getBlockY() + " §8· " + extra);
        if (band.deep()) {
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.5f, 0.7f);
        }
    }

    /** First step on the isle: say hello and walk them to Old Wick. */
    private void welcome(Player player) {
        player.sendMessage("§6⚒ Welcome to Mining Eldervale. §7Seven quarries, four depths, one very old forge.");
        player.sendMessage("§7Every strike here feeds something: rhythm, mastery, contracts, and sometimes a crystal the size of your fist.");
        if (isle.cast().isPlaced(MineRole.LAMPWARDEN)) {
            player.sendMessage("§7Old Wick keeps the lamps. §eFollow the arrow§7, he'll give you the lay of the rock.");
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    guide(player, MineRole.LAMPWARDEN);
                }
            }, 50L);
        }
    }

    /** Pays Cartographer once every place is known (checked on every entry, survival only). */
    private void cartographer(Player player, MineProfiles.Profile profile) {
        if (profile.surveyor || player.getGameMode() != GameMode.SURVIVAL
                || countKnown(profile) < isle.districts().size() || isle.districts().size() == 0) {
            return;
        }
        profile.surveyor = true;
        isle.profiles().markDirty();
        MineSkills.coins(player, CARTOGRAPHER_COINS);
        MineSkills.bonus(player, CARTOGRAPHER_XP);
        player.sendMessage("§6✦ Cartographer §8· §7You've walked every hall, quarry and shaft on Mining Eldervale."
                + " §6+" + MineText.coins(CARTOGRAPHER_COINS) + " coins §8· §a+" + CARTOGRAPHER_XP + " Mining XP");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    public int countKnown(MineProfiles.Profile profile) {
        int count = 0;
        for (MineDistricts.District district : isle.districts().all()) {
            if (profile.districts.contains(district.id())) {
                count++;
            }
        }
        return count;
    }
}
