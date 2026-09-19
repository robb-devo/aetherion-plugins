package de.aetherion.bossengine.hud;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.core.api.QuestBars;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One yellow bar per player, only while they are at the fight.
 * Leaves on world-change, death, or 15s without hits once they walk off.
 */
public class BossBarHud {

    private static final double VIEW_RADIUS = 32.0;
    private static final long COMBAT_MEMORY_MS = 15_000L;

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> shownForPlayer = new ConcurrentHashMap<>();

    public void tick(Collection<BossInstance> active) {
        Set<UUID> living = new HashSet<>();
        Map<UUID, BossInstance> byId = new HashMap<>();
        for (BossInstance instance : active) {
            if (instance.getEntity() == null || !instance.isEncounterActive()) {
                continue;
            }
            living.add(instance.getInstanceId());
            byId.put(instance.getInstanceId(), instance);
            paint(instance);
            dropStaleAggro(instance);
        }
        bars.entrySet().removeIf(entry -> {
            if (living.contains(entry.getKey())) {
                return false;
            }
            hide(entry.getKey(), entry.getValue());
            return true;
        });

        Map<UUID, BossInstance> pick = new HashMap<>();
        for (BossInstance instance : byId.values()) {
            LivingEntity entity = instance.getEntity();
            if (entity == null || entity.getWorld() == null) {
                continue;
            }
            for (Player player : entity.getWorld().getPlayers()) {
                if (!eligible(player, instance)) {
                    continue;
                }
                BossInstance current = pick.get(player.getUniqueId());
                if (current == null || better(player, instance, current)) {
                    pick.put(player.getUniqueId(), instance);
                }
            }
        }

        Set<UUID> claimed = new HashSet<>(pick.keySet());
        shownForPlayer.entrySet().removeIf(entry -> {
            if (claimed.contains(entry.getKey())) {
                return false;
            }
            detach(entry.getKey(), entry.getValue());
            return true;
        });

        for (Map.Entry<UUID, BossInstance> entry : pick.entrySet()) {
            showOnly(entry.getKey(), entry.getValue());
        }
    }

    public void refresh(BossInstance instance) {
        if (instance == null) {
            return;
        }
        paint(instance);
    }

    public void hidePlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID instanceId = shownForPlayer.remove(playerId);
        if (instanceId != null) {
            detach(playerId, instanceId);
        }
        for (BossBar bar : bars.values()) {
            bar.removePlayer(player);
        }
        QuestBars.unsuppress(player);
    }

    public void hideAll() {
        bars.forEach(this::hide);
        bars.clear();
        viewers.clear();
        shownForPlayer.clear();
    }

    public void hide(BossInstance instance) {
        if (instance == null) {
            return;
        }
        BossBar bar = bars.remove(instance.getInstanceId());
        if (bar != null) {
            hide(instance.getInstanceId(), bar);
        }
    }

    private void paint(BossInstance instance) {
        if (instance.getEntity() == null) {
            return;
        }
        BossBar bar = bars.computeIfAbsent(instance.getInstanceId(), id ->
                Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_20)
        );
        float progress = (float) Math.max(0, Math.min(1, instance.healthPercent() / 100.0));
        bar.setProgress(progress);
        bar.setTitle(TextUtil.color(
                instance.replacePlaceholders(
                        instance.getTemplate().getDisplayName()
                                + " &8• &f{health}&8/&f{max-health}"
                                + (instance.isTransitioning() ? " &e✦" : "")
                )
        ));
        if (instance.isTransitioning() || instance.isCinematicDying()) {
            bar.setColor(BarColor.WHITE);
        } else if (progress <= 0.5f) {
            bar.setColor(BarColor.RED);
        } else {
            bar.setColor(BarColor.YELLOW);
        }
        bar.setVisible(true);
    }

    private void showOnly(UUID playerId, BossInstance instance) {
        UUID previous = shownForPlayer.put(playerId, instance.getInstanceId());
        if (previous != null && !previous.equals(instance.getInstanceId())) {
            detach(playerId, previous);
        }
        BossBar bar = bars.get(instance.getInstanceId());
        Player player = Bukkit.getPlayer(playerId);
        if (bar == null || player == null) {
            return;
        }
        Set<UUID> current = viewers.computeIfAbsent(instance.getInstanceId(), id -> ConcurrentHashMap.newKeySet());
        if (current.add(playerId)) {
            QuestBars.suppress(player);
            bar.addPlayer(player);
        }
    }

    private void detach(UUID playerId, UUID instanceId) {
        Set<UUID> current = viewers.get(instanceId);
        if (current != null) {
            current.remove(playerId);
        }
        BossBar bar = bars.get(instanceId);
        Player player = Bukkit.getPlayer(playerId);
        if (bar != null && player != null) {
            bar.removePlayer(player);
        }
        QuestBars.unsuppress(playerId);
    }

    private boolean eligible(Player player, BossInstance instance) {
        LivingEntity entity = instance.getEntity();
        if (player == null || entity == null || !player.isValid() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (!player.getWorld().equals(entity.getWorld())) {
            return false;
        }
        double distSq = player.getLocation().distanceSquared(entity.getLocation());
        boolean nearby = distSq <= VIEW_RADIUS * VIEW_RADIUS;
        boolean recent = instance.getDamageTracker().hitRecently(player.getUniqueId(), COMBAT_MEMORY_MS);
        return nearby || recent;
    }

    private boolean better(Player player, BossInstance candidate, BossInstance current) {
        long hitA = candidate.getDamageTracker().lastHitMillis(player.getUniqueId());
        long hitB = current.getDamageTracker().lastHitMillis(player.getUniqueId());
        if (hitA != hitB) {
            return hitA > hitB;
        }
        LivingEntity a = candidate.getEntity();
        LivingEntity b = current.getEntity();
        if (a == null || b == null) {
            return false;
        }
        return player.getLocation().distanceSquared(a.getLocation())
                < player.getLocation().distanceSquared(b.getLocation());
    }

    private void dropStaleAggro(BossInstance instance) {
        if (!(instance.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(mob.getTarget() instanceof Player player)) {
            return;
        }
        if (eligible(player, instance)) {
            return;
        }
        mob.setTarget(null);
    }

    private void hide(UUID instanceId, BossBar bar) {
        shownForPlayer.entrySet().removeIf(entry -> instanceId.equals(entry.getValue()));
        Set<UUID> ids = viewers.remove(instanceId);
        if (bar != null) {
            for (Player player : new HashSet<>(bar.getPlayers())) {
                bar.removePlayer(player);
                QuestBars.unsuppress(player);
            }
            bar.removeAll();
            bar.setVisible(false);
        }
        if (ids == null) {
            return;
        }
        for (UUID playerId : ids) {
            QuestBars.unsuppress(playerId);
        }
    }
}
