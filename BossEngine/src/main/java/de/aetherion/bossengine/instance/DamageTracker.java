package de.aetherion.bossengine.instance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageTracker {

    private final Map<UUID, Double> damageByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitAt = new ConcurrentHashMap<>();
    private double totalDamage;

    public void add(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        damageByPlayer.merge(player.getUniqueId(), amount, Double::sum);
        lastHitAt.put(player.getUniqueId(), System.currentTimeMillis());
        totalDamage += amount;
    }

    public boolean hitRecently(UUID playerId, long windowMs) {
        if (playerId == null || windowMs <= 0) {
            return false;
        }
        Long at = lastHitAt.get(playerId);
        return at != null && System.currentTimeMillis() - at <= windowMs;
    }

    public long lastHitMillis(UUID playerId) {
        return lastHitAt.getOrDefault(playerId, 0L);
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public double getDamage(UUID playerId) {
        return damageByPlayer.getOrDefault(playerId, 0.0);
    }

    public double getShare(UUID playerId) {
        if (totalDamage <= 0) {
            return 0;
        }
        return getDamage(playerId) / totalDamage;
    }

    public Map<UUID, Double> snapshot() {
        Map<UUID, Double> copy = new LinkedHashMap<>();
        damageByPlayer.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(copy);
    }

    public Map<UUID, Double> shareSnapshot() {
        Map<UUID, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> entry : snapshot().entrySet()) {
            copy.put(entry.getKey(), totalDamage <= 0 ? 0 : entry.getValue() / totalDamage);
        }
        return Collections.unmodifiableMap(copy);
    }

    public Optional<Player> topDamager() {
        return snapshot().keySet().stream()
                .findFirst()
                .map(Bukkit::getPlayer);
    }

    public boolean participated(UUID playerId, double thresholdPercent) {
        return getShare(playerId) * 100.0 >= thresholdPercent;
    }
}
