package de.aetherion.farming.isle;

import de.aetherion.farming.FarmingSkills;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walking Eldervale: names the area you step into, pays a small discovery reward the first time,
 * pays the Cartographer bonus when every area is found — and, on request, points an action-bar
 * arrow at an NPC or plot until you arrive.
 */
public final class IsleCompass {

    private static final int DISCOVERY_XP = 25;
    private static final long CARTOGRAPHER_COINS = 2_500L;
    private static final int CARTOGRAPHER_XP = 400;
    private static final long GUIDE_MS = 120_000L;
    private static final double ARRIVE_DISTANCE = 6.0d;

    private record Guide(String label, Location target, long until) {
    }

    private final FarmIsle isle;
    private final Map<UUID, String> lastPlot = new ConcurrentHashMap<>();
    private final Map<UUID, Guide> guides = new ConcurrentHashMap<>();
    private BukkitTask task;

    IsleCompass(FarmIsle isle) {
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
        lastPlot.clear();
        guides.clear();
    }

    void forget(UUID id) {
        lastPlot.remove(id);
        guides.remove(id);
    }

    /** Point {@code player} at {@code target} for two minutes (or until they arrive). */
    public void guide(Player player, String label, Location target) {
        if (target == null || target.getWorld() == null) {
            IsleText.bar(player, "§cThat spot is not set up yet.");
            return;
        }
        guides.put(player.getUniqueId(), new Guide(label, target.clone(), System.currentTimeMillis() + GUIDE_MS));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    public void stopGuide(Player player) {
        guides.remove(player.getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Guide guide = guides.get(id);
            if (guide != null) {
                tickGuide(player, guide, now);
            }
            if (Bukkit.getCurrentTick() % 20 >= 10) {
                continue;
            }
            IslePlots.Plot plot = isle.plots().at(player.getLocation());
            String plotId = plot == null ? null : plot.id();
            String before = lastPlot.get(id);
            if (plotId == null) {
                lastPlot.remove(id);
                continue;
            }
            if (plotId.equals(before)) {
                continue;
            }
            lastPlot.put(id, plotId);
            enter(player, plot, guide == null);
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
            IsleText.bar(player, "§aYou've reached §f" + guide.label() + "§a.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.7f, 1.5f);
            return;
        }
        IsleText.bar(player, "§e" + IsleText.arrow(player, guide.target()) + " §f" + guide.label()
                + " §8· §7" + (int) Math.round(distance) + "m");
        if (Bukkit.getCurrentTick() % 40 < 10) {
            Location from = player.getLocation().add(0, 1.2, 0);
            var step = guide.target().toVector().subtract(from.toVector()).normalize().multiply(2.2);
            player.spawnParticle(Particle.WAX_ON, from.add(step), 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void enter(Player player, IslePlots.Plot plot, boolean announce) {
        IsleProfiles.Profile profile = isle.profiles().of(player);
        if (profile.plots.add(plot.id())) {
            isle.profiles().markDirty();
            int found = countKnown(profile);
            int total = isle.plots().size();
            player.showTitle(Title.title(
                    IsleText.legacy(plot.colored()),
                    IsleText.legacy("§7Discovered §8· §f" + found + "§7/§f" + total
                            + (plot.blurb().isBlank() ? "" : " §8· §7" + plot.blurb())),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 1.1f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.4f);
            FarmingSkills.bonus(player, DISCOVERY_XP);
            if (found >= total && !profile.cartographer && player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                profile.cartographer = true;
                FarmingSkills.coins(player, CARTOGRAPHER_COINS);
                FarmingSkills.bonus(player, CARTOGRAPHER_XP);
                player.sendMessage("§6✦ Isle Cartographer §8· §7You've walked every corner of Eldervale. §6+"
                        + IsleText.coins(CARTOGRAPHER_COINS) + " coins §8· §a+" + CARTOGRAPHER_XP + " Farming XP");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            return;
        }
        if (announce) {
            IsleText.bar(player, plot.colored() + (plot.blurb().isBlank() ? "" : " §8· §7" + plot.blurb()));
        }
    }

    /** Discovered plots that still exist in config. */
    public int countKnown(IsleProfiles.Profile profile) {
        int count = 0;
        for (IslePlots.Plot plot : isle.plots().all()) {
            if (profile.plots.contains(plot.id())) {
                count++;
            }
        }
        return count;
    }
}
