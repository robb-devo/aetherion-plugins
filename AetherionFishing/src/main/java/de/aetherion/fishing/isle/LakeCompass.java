package de.aetherion.fishing.isle;

import de.aetherion.fishing.FishingSkills;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walking Fishing Eldervale: names the water you step up to, pays a small discovery reward the
 * first time, pays Waterfinder when every water is found — and, on request, points an action-bar
 * arrow at an NPC, a water or the shoal until you arrive.
 */
public final class LakeCompass {

    private static final int DISCOVERY_XP = 25;
    private static final long WATERFINDER_COINS = 2_500L;
    private static final int WATERFINDER_XP = 400;
    private static final long GUIDE_MS = 120_000L;
    private static final double ARRIVE_DISTANCE = 6.0d;

    private record Guide(String label, Location target, long until) {
    }

    private final FishIsle isle;
    private final Map<UUID, String> lastWater = new ConcurrentHashMap<>();
    private final Map<UUID, Guide> guides = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> welcomed = new ConcurrentHashMap<>();
    private BukkitTask task;

    LakeCompass(FishIsle isle) {
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
        lastWater.clear();
        guides.clear();
    }

    void forget(UUID id) {
        lastWater.remove(id);
        guides.remove(id);
        welcomed.remove(id);
    }

    public void guide(Player player, String label, Location target) {
        if (target == null || target.getWorld() == null) {
            LakeText.bar(player, "§cThat spot is not set up yet.");
            return;
        }
        guides.put(player.getUniqueId(), new Guide(label, target.clone(), System.currentTimeMillis() + GUIDE_MS));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Guide guide = guides.get(id);
            if (guide != null && !isle.casting(player) && isle.line().quiet(id)) {
                tickGuide(player, guide, now);
            }
            if (Bukkit.getCurrentTick() % 20 >= 10) {
                continue;
            }
            if (!LakeWorld.onIsle(player)) {
                lastWater.remove(id);
                continue;
            }
            if (!welcomed.containsKey(id)) {
                welcomed.put(id, Boolean.TRUE);
                if (isle.profiles().of(player).catches() == 0 && isle.profiles().of(player).waters().isEmpty()) {
                    welcome(player);
                }
            }
            Waters.Water water = isle.waters().at(player.getLocation());
            String waterId = water == null ? null : water.id();
            String before = lastWater.get(id);
            if (waterId == null) {
                lastWater.remove(id);
                continue;
            }
            if (waterId.equals(before)) {
                continue;
            }
            lastWater.put(id, waterId);
            enter(player, water, guide == null && !isle.casting(player));
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
            LakeText.bar(player, "§aYou've reached §f" + guide.label() + "§a.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.7f, 1.5f);
            return;
        }
        LakeText.bar(player, "§e" + LakeText.arrow(player, guide.target()) + " §f" + guide.label()
                + " §8· §7" + (int) Math.round(distance) + "m");
        if (Bukkit.getCurrentTick() % 40 < 10) {
            Location from = player.getLocation().add(0, 1.2, 0);
            var step = guide.target().toVector().subtract(from.toVector()).normalize().multiply(2.2);
            player.spawnParticle(Particle.DRIPPING_WATER, from.add(step), 3, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void enter(Player player, Waters.Water water, boolean announce) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if (profile.waters.add(water.id())) {
            isle.profiles().markDirty();
            int found = countKnown(profile);
            int total = isle.waters().size();
            player.showTitle(Title.title(
                    LakeText.legacy(water.colored()),
                    LakeText.legacy("§7Discovered §8· §f" + found + "§7/§f" + total
                            + (water.blurb().isBlank() ? "" : " §8· §7" + water.blurb())),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 1.1f);
            player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, SoundCategory.PLAYERS, 0.8f, 1.4f);
            FishingSkills.bonus(player, DISCOVERY_XP);
            if (found >= total && !profile.waterfinder && player.getGameMode() == GameMode.SURVIVAL) {
                profile.waterfinder = true;
                FishingSkills.coins(player, WATERFINDER_COINS);
                FishingSkills.bonus(player, WATERFINDER_XP);
                player.sendMessage("§6✦ Waterfinder §8· §7You've fished your eyes over every water on Eldervale. §6+"
                        + LakeText.coins(WATERFINDER_COINS) + " coins §8· §a+" + WATERFINDER_XP + " Fishing XP");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            return;
        }
        if (announce) {
            LakeText.bar(player, water.colored() + (water.blurb().isBlank() ? "" : " §8· §7" + water.blurb()));
        }
    }

    /** First step on the isle: say hello and walk them to the Harbourmaster. */
    private void welcome(Player player) {
        player.sendMessage("§b✦ Welcome to Fishing Eldervale. §7Every water here has its own fish —"
                + " and some of them only come out at night.");
        Location at = isle.cast().whereabouts(LakeRole.HARBOURMASTER);
        if (at != null && isle.cast().isPlaced(LakeRole.HARBOURMASTER)) {
            player.sendMessage("§7Maren runs the harbour — follow the arrow, she'll hand you an Angler's Log.");
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    guide(player, LakeRole.HARBOURMASTER.display() + " §7(Harbourmaster)", at);
                }
            }, 50L);
        }
    }

    public int countKnown(AnglerProfiles.Profile profile) {
        int count = 0;
        for (Waters.Water water : isle.waters().all()) {
            if (profile.waters.contains(water.id())) {
                count++;
            }
        }
        return count;
    }
}
