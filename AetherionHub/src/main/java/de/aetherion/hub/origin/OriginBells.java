package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BellRingEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Seven Bells of Origin: the real bell blocks of the map (Scholars' Hall, the farm smithy pair, Bell Wharf and
 * its two-bell tower, the wayside bell by the south pad). Ring each once for its note of the old hymn; ring all
 * seven to become the <b>Bellwright</b>. The high tower bell is out of reach — use an arrow.
 * At dawn and dusk the bells toll on their own for everyone on the island.
 */
public final class OriginBells implements Listener {

    /** The hymn: one note per bell, in the order of the config. Notes as pitch multipliers (a small pentatonic). */
    private static final float[] HYMN = {0.749f, 0.841f, 0.944f, 1.122f, 1.26f, 1.498f, 1.682f};

    private final OriginIsle isle;
    private final Map<UUID, Long> ringCool = new HashMap<>();
    private OriginIsle.Phase lastPhase;

    OriginBells(OriginIsle isle) {
        this.isle = isle;
        Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::watchClock, 200L, 100L);
    }

    public OriginConfig.Bell bellAt(Block block) {
        if (block == null || !block.getWorld().getName().equalsIgnoreCase(isle.config().worldName())) {
            return null;
        }
        for (OriginConfig.Bell bell : isle.config().bells().values()) {
            int[] b = bell.at();
            if (Math.abs(b[0] - block.getX()) <= 1 && Math.abs(b[1] - block.getY()) <= 1 && Math.abs(b[2] - block.getZ()) <= 1) {
                return bell;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRing(BellRingEvent event) {
        if (!isle.running()) {
            return;
        }
        OriginConfig.Bell bell = bellAt(event.getBlock());
        if (bell == null) {
            return;
        }
        Player player = ringer(event.getEntity());
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cool = ringCool.get(player.getUniqueId());
        if (cool != null && now < cool) {
            return;
        }
        ringCool.put(player.getUniqueId(), now + 1_500L);
        rung(player, bell, event.getBlock().getLocation().add(0.5, 0.5, 0.5));
    }

    private static Player ringer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void rung(Player player, OriginConfig.Bell bell, Location at) {
        int index = indexOf(bell.id());
        float pitch = HYMN[Math.max(0, index) % HYMN.length];
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 1.2f, pitch);
            world.spawnParticle(Particle.NOTE, at.clone().add(0, 1.0, 0), 1, 0, 0, 0, (index % 12) / 24.0d);
        }
        OriginProfile profile = isle.profiles().get(player);
        if (!profile.bells.add(bell.id())) {
            OriginText.bar(player, "§e♪ " + bell.name() + " §8· §7" + count(profile) + "/" + isle.config().bells().size());
            return;
        }
        profile.dirty = true;
        long paid = isle.pay(player, "bell", 60L);
        int found = count(profile);
        int total = isle.config().bells().size();
        player.sendMessage("§e♪ Rang §8· §f" + bell.name() + (paid > 0 ? " §8· §6+" + paid : "") + " §8· §7" + found + "/" + total + " bells");
        OriginText.bar(player, "§e♪ " + bell.name() + " §8· §7" + found + "/" + total + " of the Seven Bells");
        if (found >= total && profile.mark("bellwright")) {
            OriginText.card(player, "§e§lBellwright", "§7All seven bells of Origin", 60);
            long bonus = isle.pay(player, "bellwright", 1500L);
            player.sendMessage("§e✦ Bellwright §8· §6+" + OriginText.coins(bonus) + " coins §8· §7listen…");
            hymn(player);
        }
    }

    /** The whole hymn, for the Bellwright (and Sister Aurel's board). */
    public void hymn(Player player) {
        for (int i = 0; i < HYMN.length; i++) {
            float pitch = HYMN[i];
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 0.9f, pitch);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.4f, pitch);
                }
            }, 6L * i);
        }
    }

    private int indexOf(String id) {
        int i = 0;
        for (String key : isle.config().bells().keySet()) {
            if (key.equals(id)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    void forget(UUID id) {
        ringCool.remove(id);
    }

    public int count(OriginProfile profile) {
        int n = 0;
        for (String id : isle.config().bells().keySet()) {
            if (profile.bells.contains(id)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ dawn & dusk tolls

    private void watchClock() {
        if (!isle.running()) {
            return;
        }
        OriginIsle.Phase phase = isle.phase();
        OriginIsle.Phase before = lastPhase;
        lastPhase = phase;
        if (before == null || before == phase) {
            return;
        }
        if (phase == OriginIsle.Phase.DAWN) {
            toll("§e♪ §7The bells of Origin ring in the morning.", 3);
        } else if (phase == OriginIsle.Phase.DUSK) {
            toll("§e♪ §7The bells of Origin toll for dusk. §8The lanterns are lit.", 5);
        }
    }

    public void toll(String line, int strokes) {
        List<Player> onIsle = isle.onIsle();
        if (onIsle.isEmpty()) {
            return;
        }
        for (Player player : onIsle) {
            if (isle.profiles().get(player).ambience) {
                player.sendMessage(line);
            }
        }
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        for (int s = 0; s < strokes; s++) {
            int stroke = s;
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                for (OriginConfig.Bell bell : isle.config().bells().values()) {
                    Location at = new Location(world, bell.at()[0] + 0.5, bell.at()[1] + 0.5, bell.at()[2] + 0.5);
                    for (Player player : world.getPlayers()) {
                        double d2 = player.getLocation().distanceSquared(at);
                        if (d2 < 110 * 110 && isle.profiles().get(player).ambience) {
                            float volume = (float) Math.max(0.25d, 2.2d - Math.sqrt(d2) / 60.0d);
                            player.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, volume, stroke % 2 == 0 ? 0.9f : 0.8f);
                            if (stroke == 0) {
                                player.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, volume * 0.6f, 1.0f);
                            }
                        }
                    }
                }
            }, 30L * s);
        }
    }
}
