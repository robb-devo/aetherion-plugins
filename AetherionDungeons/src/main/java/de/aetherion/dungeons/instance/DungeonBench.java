package de.aetherion.dungeons.instance;

import de.aetherion.core.AetherKeys;
import de.aetherion.dungeons.bridge.BossEngineBridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays one dungeon run with a test player, so {@link DungeonRunMetrics} gets numbers without a group
 * (beta list A-06). Console or admins only: {@code /dungeon bench <player> <1|2|3> [seconds per stop]}.
 *
 * <p>The run is the normal code path; only the fighting is skipped. Floor 1: the bench readies the party,
 * puts the player into each open chamber, waits there, then kills that chamber's mobs so the next one opens.
 * Floors 2 and 3: it puts the player beside one pack after another and kills what is near until the gate
 * opens. Then it waits beside the boss, kills it and leaves. The player is kept alive throughout.
 */
public final class DungeonBench {

    private static final Set<String> BOSS_IDS = Set.of("dungeon_sentinel", "dungeon_frostbound", "dungeon_aetherion");
    private static final long MAX_RUN_MS = 15L * 60L * 1000L;
    private static final double PACK_RADIUS_SQ = 24.0 * 24.0;

    private final JavaPlugin plugin;
    private final InstanceManager instances;
    private final Map<UUID, Bench> running = new ConcurrentHashMap<>();

    public DungeonBench(JavaPlugin plugin, InstanceManager instances) {
        this.plugin = plugin;
        this.instances = instances;
    }

    public void start(CommandSender sender, Player player, int floor, int seconds) {
        if (running.containsKey(player.getUniqueId())) {
            sender.sendMessage("§cA bench is already running for " + player.getName() + ".");
            return;
        }
        if (instances.hasActive(player) || instances.isDungeonWorld(player.getWorld())) {
            sender.sendMessage("§c" + player.getName() + " is already in a dungeon.");
            return;
        }
        int stop = Math.max(5, Math.min(120, seconds));
        Bench bench = new Bench(sender, player, floor, stop);
        running.put(player.getUniqueId(), bench);
        plugin.getLogger().info("[DungeonBench] F" + floor + " with " + player.getName() + ", " + stop + " s per stop");
        sender.sendMessage("§5Bench §7Floor " + floor + " with §f" + player.getName() + "§7, " + stop + " s per stop.");
        instances.enterPrototype(player, false, floor);
        bench.task = Bukkit.getScheduler().runTaskTimer(plugin, bench, 20L, 20L);
    }

    /** Stops the bench for one player (or all when {@code player} is null) and leaves the run. */
    public int stop(Player player, String why) {
        int stopped = 0;
        for (Bench bench : List.copyOf(running.values())) {
            if (player == null || bench.id.equals(player.getUniqueId())) {
                bench.finish(why, true);
                stopped++;
            }
        }
        return stopped;
    }

    public boolean isBenchPlayer(Player player) {
        return player != null && running.containsKey(player.getUniqueId());
    }

    private final class Bench implements Runnable {
        final CommandSender sender;
        final Player player;
        final UUID id;
        final int floor;
        final int stopSeconds;
        final long startedAt = System.currentTimeMillis();
        BukkitTask task;
        boolean done;
        int stops;
        int waited;
        int idle;
        int room = -1;
        boolean roomSwept;
        boolean atPack;
        boolean atBoss;
        int bossTries;

        Bench(CommandSender sender, Player player, int floor, int stopSeconds) {
            this.sender = sender;
            this.player = player;
            this.id = player.getUniqueId();
            this.floor = floor;
            this.stopSeconds = stopSeconds;
        }

        @Override
        public void run() {
            if (done) {
                return;
            }
            if (!player.isOnline()) {
                finish("the player left", false);
                return;
            }
            if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) {
                finish("timed out after 15 minutes", true);
                return;
            }
            DungeonSession session = instances.sessionOf(player);
            if (session == null || session.world() == null) {
                if (++idle > 20) {
                    finish(stops == 0 ? "no run started (see the log)" : "the run closed", false);
                }
                return;
            }
            World world = session.world();
            keepAlive();
            if (session.bossDead()) {
                if (++waited >= 3) {
                    instances.leave(player, true);
                    finish("done", false);
                }
                return;
            }
            if (session.bossReleased()) {
                boss(world);
                return;
            }
            if (!session.started()) {
                instances.markReady(player);
                return;
            }
            if (EndlessEncounter.isEndless(session) || AshesEncounter.isAshes(session)) {
                packs(world);
            } else {
                chambers(session, world);
            }
        }

        private void chambers(DungeonSession session, World world) {
            DungeonLayout layout = session.layout();
            if (layout == null) {
                return;
            }
            if (room >= 0 && session.isRoomCleared(room)) {
                room = -1;
            }
            if (room < 0) {
                int next = -1;
                for (int i = 0; i < layout.combatCount(); i++) {
                    if (session.isUnlocked(i) && !session.isRoomCleared(i)) {
                        next = i;
                        break;
                    }
                }
                if (next < 0) {
                    return; // the next chamber is opening
                }
                List<LivingEntity> mobs = roomMobs(world, next);
                if (mobs.isEmpty()) {
                    return; // spawn is on its way, or the clear scan closes it next second
                }
                moveTo(mobs.get(0).getLocation());
                room = next;
                roomSwept = false;
                waited = 0;
                stops++;
                return;
            }
            if (++waited < (roomSwept ? 3 : stopSeconds)) {
                return;
            }
            for (LivingEntity mob : roomMobs(world, room)) {
                kill(mob);
            }
            roomSwept = true;
            waited = 0;
        }

        private void packs(World world) {
            int packStop = Math.max(5, stopSeconds / 2);
            if (!atPack) {
                List<LivingEntity> trash = trash(world);
                if (trash.isEmpty()) {
                    if (++idle > 60) {
                        finish("no mobs left but the gate stayed shut", true);
                    }
                    return;
                }
                idle = 0;
                moveTo(nearest(trash, player.getLocation()).getLocation());
                atPack = true;
                waited = 0;
                stops++;
                return;
            }
            if (++waited < packStop) {
                return;
            }
            Location here = player.getLocation();
            for (LivingEntity mob : trash(world)) {
                if (mob.getWorld().equals(here.getWorld()) && mob.getLocation().distanceSquared(here) <= PACK_RADIUS_SQ) {
                    kill(mob);
                }
            }
            atPack = false;
        }

        private void boss(World world) {
            LivingEntity boss = findBoss(world);
            if (boss == null) {
                if (++idle > 40) {
                    finish("the boss never showed up", true);
                }
                return;
            }
            idle = 0;
            if (!atBoss) {
                moveTo(boss.getLocation());
                atBoss = true;
                waited = 0;
                stops++;
                return;
            }
            if (++waited < stopSeconds) {
                return;
            }
            if (bossTries++ > 15) {
                finish("the boss would not die", true);
                return;
            }
            kill(boss);
        }

        private void moveTo(Location at) {
            Location target = at.clone();
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(0f);
            player.teleport(target);
        }

        private void keepAlive() {
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null && player.getHealth() > 0.0) {
                player.setHealth(max.getValue());
            }
            player.setFoodLevel(20);
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, true, false));
        }

        void finish(String why, boolean leave) {
            if (done) {
                return;
            }
            done = true;
            if (task != null) {
                task.cancel();
            }
            running.remove(id);
            if (leave && player.isOnline() && instances.hasActive(player)) {
                instances.leave(player, false);
            }
            long seconds = (System.currentTimeMillis() - startedAt) / 1000L;
            String head = "Bench F" + floor + " " + why + " after " + seconds + " s, " + stops + " stop(s).";
            plugin.getLogger().info("[DungeonBench] " + head);
            sender.sendMessage("§5" + head);
            List<String> report = instances.metrics().report();
            if (!report.isEmpty()) {
                sender.sendMessage("§7" + report.get(0));
            }
        }
    }

    private List<LivingEntity> roomMobs(World world, int roomIndex) {
        List<LivingEntity> out = new ArrayList<>();
        var key = PrototypeDungeonBuilder.roomKey(plugin);
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.isDead() || !PrototypeDungeonBuilder.isCombatRoomMob(plugin, entity)) {
                continue;
            }
            Integer room = entity.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (room != null && room == roomIndex) {
                out.add(entity);
            }
        }
        return out;
    }

    private static List<LivingEntity> trash(World world) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity.isDead() || BossEngineBridge.isEngineBoss(entity)) {
                continue;
            }
            String id = entity.getPersistentDataContainer().get(AetherKeys.DUNGEON_MOB, PersistentDataType.STRING);
            if ((id != null && !BOSS_IDS.contains(id)) || BossEngineBridge.isAshesElite(entity)) {
                out.add(entity);
            }
        }
        return out;
    }

    private static LivingEntity findBoss(World world) {
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.isDead()) {
                continue;
            }
            if (BossEngineBridge.isEngineBoss(entity)) {
                return entity;
            }
            String id = entity.getPersistentDataContainer().get(AetherKeys.DUNGEON_MOB, PersistentDataType.STRING);
            if (id != null && BOSS_IDS.contains(id)) {
                return entity;
            }
        }
        return null;
    }

    private static LivingEntity nearest(List<LivingEntity> mobs, Location from) {
        LivingEntity best = mobs.get(0);
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity mob : mobs) {
            if (!mob.getWorld().equals(from.getWorld())) {
                continue;
            }
            double sq = mob.getLocation().distanceSquared(from);
            if (sq < bestSq) {
                bestSq = sq;
                best = mob;
            }
        }
        return best;
    }

    private static void kill(LivingEntity mob) {
        if (mob.isValid() && !mob.isDead()) {
            mob.setHealth(0.0);
        }
    }
}
