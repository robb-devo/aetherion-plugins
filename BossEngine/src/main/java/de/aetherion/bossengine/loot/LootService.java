package de.aetherion.bossengine.loot;

import de.aetherion.bossengine.event.BossDeathEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.DamageTracker;
import de.aetherion.bossengine.model.BossLootTable;
import de.aetherion.bossengine.model.LootEntry;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LootService {

    private final LootFactory factory;

    public LootService(LootFactory factory) {
        this.factory = factory;
    }

    public BossDeathEvent buildDeathEvent(BossInstance instance, Player killer) {
        DamageTracker tracker = instance.getDamageTracker();
        Map<UUID, List<ItemStack>> bundles = distribute(instance, killer);

        BossDeathEvent event = new BossDeathEvent(
                instance,
                killer,
                tracker.snapshot(),
                tracker.shareSnapshot(),
                bundles
        );
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public void grant(BossDeathEvent event) {
        if (event == null) {
            return;
        }

        if (event.isDropLoot()) {
            event.getLootByPlayer().forEach((playerId, items) -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    dropAtBoss(event, items);
                    return;
                }
                for (ItemStack item : items) {
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    giveLootItem(player, item);
                    String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                            ? item.getItemMeta().getDisplayName()
                            : item.getType().name();
                    player.sendMessage(TextUtil.component("&5Loot &8» &f" + name));
                }
            });
        }

        Map<UUID, Integer> xp = grantExperience(event);
        sendRecap(event, xp);
    }

    public Map<UUID, List<ItemStack>> distribute(BossInstance instance, Player killer) {
        Map<UUID, List<ItemStack>> bundles = new HashMap<>();
        BossLootTable table = instance.getTemplate().getLootTable();
        DamageTracker tracker = instance.getDamageTracker();
        double threshold = table.getParticipationThresholdPercent();

        UUID firstPlace = tracker.topDamager()
                .map(Player::getUniqueId)
                .orElse(killer == null ? null : killer.getUniqueId());

        List<UUID> ranking = new ArrayList<>(tracker.snapshot().keySet());
        if (ranking.isEmpty() && firstPlace != null) {
            ranking.add(firstPlace);
        }
        if (ranking.isEmpty()) {
            Player nearest = nearestFighter(instance);
            if (nearest != null) {
                tracker.add(nearest, 1.0);
                ranking.add(nearest.getUniqueId());
                firstPlace = nearest.getUniqueId();
            }
        }

        final UUID winner = firstPlace;
        if (winner != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            for (LootEntry entry : table.getKillerBonus()) {
                factory.create(entry, 1.0, winnerPlayer).ifPresent(item -> add(bundles, winner, item));
            }
        }
        for (int rank = 1; rank <= 3; rank++) {
            if (rank > ranking.size()) {
                break;
            }
            UUID playerId = ranking.get(rank - 1);
            Player ranked = Bukkit.getPlayer(playerId);
            for (LootEntry entry : table.getRankLoot(rank)) {
                factory.create(entry, 1.0, ranked).ifPresent(item -> add(bundles, playerId, item));
            }
        }

        List<UUID> participants = new ArrayList<>();
        tracker.snapshot().forEach((playerId, ignored) -> {
            if (!table.isDamageBased() || tracker.participated(playerId, threshold)) {
                participants.add(playerId);
            }
        });

        if (participants.isEmpty() && winner != null) {
            participants.add(winner);
        }

        for (LootEntry entry : table.getShared()) {
            factory.create(entry, 1.0, winner != null ? Bukkit.getPlayer(winner) : null).ifPresent(item -> {
                if (participants.isEmpty()) {
                    return;
                }
                UUID receiver = winner != null ? winner : participants.getFirst();
                add(bundles, receiver, item);
            });
        }

        int topCount = Math.min(table.getTopDamagerCount(), ranking.size());
        if (topCount == 0 && winner != null && !table.getTopDamagerLoot().isEmpty()) {
            ranking.add(winner);
            topCount = 1;
        }
        for (int i = 0; i < topCount; i++) {
            UUID playerId = ranking.get(i);
            Player top = Bukkit.getPlayer(playerId);
            for (LootEntry entry : table.getTopDamagerLoot()) {
                factory.create(entry, 1.0, top).ifPresent(item -> add(bundles, playerId, item));
            }
        }

        for (UUID playerId : participants) {
            double share = table.isDamageBased() ? tracker.getShare(playerId) : 1.0 / participants.size();
            Player participant = Bukkit.getPlayer(playerId);
            for (LootEntry entry : table.getPerDamager()) {
                factory.create(entry, share, participant).ifPresent(item -> add(bundles, playerId, item));
            }
        }

        return bundles;
    }

    private Map<UUID, Integer> grantExperience(BossDeathEvent event) {
        Map<UUID, Integer> awarded = new LinkedHashMap<>();
        int total = event.getInstance().getTemplate().getLootTable().getExperience();
        if (total <= 0) {
            return awarded;
        }
        Map<UUID, Double> shares = event.getShareMap();
        if (shares.isEmpty() && event.getKiller() != null) {
            event.getKiller().giveExp(total);
            awarded.put(event.getKiller().getUniqueId(), total);
            sharePetExperience(event.getKiller(), total);
            return awarded;
        }
        int given = 0;
        int index = 0;
        for (Map.Entry<UUID, Double> entry : shares.entrySet()) {
            index++;
            int amount = index == shares.size()
                    ? Math.max(0, total - given)
                    : (int) Math.round(total * entry.getValue());
            given += amount;
            if (amount <= 0) {
                continue;
            }
            awarded.put(entry.getKey(), amount);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.giveExp(amount);
                sharePetExperience(player, amount);
            }
        }
        return awarded;
    }

    private void sharePetExperience(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        org.bukkit.plugin.Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs == null || !mobs.isEnabled()) {
            return;
        }

        try {
            mobs.getClass()
                    .getMethod("sharePetExperience", Player.class, int.class)
                    .invoke(mobs, player, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void sendRecap(BossDeathEvent event, Map<UUID, Integer> xp) {
        Map<UUID, Double> damage = event.getDamageMap();
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(damage.entrySet());
        ranking.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());

        List<UUID> audience = new ArrayList<>(damage.keySet());
        if (event.getKiller() != null && !audience.contains(event.getKiller().getUniqueId())) {
            audience.add(event.getKiller().getUniqueId());
        }
        Location origin = event.getInstance().getSpawnLocation();
        if (origin != null && origin.getWorld() != null) {
            for (Player nearby : origin.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(origin) <= 64 * 64
                        && !audience.contains(nearby.getUniqueId())) {
                    audience.add(nearby.getUniqueId());
                }
            }
        }

        String bossName = event.getInstance().getTemplate().getDisplayName();

        for (UUID playerId : audience) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean de = prefersGerman(player);
            player.sendMessage(TextUtil.component("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            player.sendMessage(TextUtil.component(bossName + (de
                    ? " &7wurde besiegt"
                    : " &7has been defeated")));
            if (ranking.isEmpty()) {
                player.sendMessage(TextUtil.component(de
                        ? "&7Kein Schadens-Ranking."
                        : "&7No damage ranking."));
            } else {
                for (int i = 0; i < ranking.size(); i++) {
                    Map.Entry<UUID, Double> entry = ranking.get(i);
                    String medal = i == 0 ? "&6#" : (i == 1 ? "&7#" : (i == 2 ? "&8#" : "&8#"));
                    player.sendMessage(TextUtil.component(
                            medal + (i + 1) + " &f" + playerName(entry.getKey())
                                    + " &8» &c" + formatDamage(entry.getValue())));
                }
            }
            player.sendMessage(TextUtil.component("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

            double mine = damage.getOrDefault(playerId, 0.0);
            int myXp = xp.getOrDefault(playerId, 0);
            player.sendMessage(TextUtil.component(
                    (de ? "&7Dein Schaden: &f" : "&7Your damage: &f")
                            + formatDamage(mine)
                            + (myXp > 0 ? "  &8|  &a+" + formatDamage(myXp) + " XP" : "")
            ));
            if (!ranking.isEmpty() && playerId.equals(ranking.getFirst().getKey())) {
                player.sendMessage(TextUtil.component(de
                        ? "&7#1-Bonus liegt in deinem Inventar."
                        : "&7#1 bonus is in your inventory."));
            }
        }
    }

    /** Soft-read AetherionQuests language preference; English is the default. */
    private static boolean prefersGerman(Player player) {
        try {
            Object code = Class.forName("de.aetherion.quests.lang.PlayerLang")
                    .getMethod("of", Player.class)
                    .invoke(null, player);
            if (code == null) {
                return false;
            }
            Object german = code.getClass().getMethod("german").invoke(code);
            return german instanceof Boolean b && b;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static Player nearestFighter(BossInstance instance) {
        Location origin = instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            origin = instance.getEntity() == null ? null : instance.getEntity().getLocation();
        }
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = 48 * 48;
        for (Player player : origin.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static String playerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? "Unknown" : name;
    }

    private static String formatDamage(double value) {
        return String.format(Locale.GERMANY, "%,.0f", value);
    }

    private void add(Map<UUID, List<ItemStack>> bundles, UUID playerId, ItemStack item) {
        bundles.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(item);
    }

    private static void giveLootItem(Player player, ItemStack item) {
        try {
            Class<?> delivery = Class.forName("de.aetherion.items.storage.BoosterDelivery");
            delivery.getMethod("giveReward", Player.class, ItemStack.class).invoke(null, player, item);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void dropAtBoss(BossDeathEvent event, List<ItemStack> items) {
        if (event.getInstance().getEntity() == null || items == null) {
            return;
        }
        Location location = event.getInstance().getEntity().getLocation();
        items.forEach(item -> location.getWorld().dropItemNaturally(location, item));
    }
}
