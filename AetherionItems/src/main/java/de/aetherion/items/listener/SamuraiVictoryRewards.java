package de.aetherion.items.listener;

import de.aetherion.core.api.AetherServices;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.progress.UnlockToast;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A-58: beating Sakuragi Jinsai (SamuraiBoss) pays. Every fighter with at least {@link #MIN_SHARE} of the damage gets
 * {@link #COINS} coins and the kill in the Dungeon Journal ({@code samurai}), and rolls {@link #BLADE_CHANCE} for the
 * Blossom Blade ({@code ashen_katana}; Robb's call on T-06, the blade is LOCKED and its stats stay untouched).
 *
 * <p>SamuraiBoss loads after Items (it soft-depends on us), so the event class is looked up one tick after start,
 * reflectively, without a compile link. No SamuraiBoss on this server: nothing happens.
 */
public final class SamuraiVictoryRewards {

    public static final String BOSS_ID = "samurai";
    static final double MIN_SHARE = 0.05;
    static final long COINS = 5_000L;
    static final double BLADE_CHANCE = 0.04;

    private SamuraiVictoryRewards() {
    }

    public static void register(AetherionItems plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Class<? extends Event> eventClass = Class
                        .forName("net.aetherion.samuraiboss.api.SamuraiBossDefeatEvent")
                        .asSubclass(Event.class);
                EventExecutor executor = (listener, event) -> handle(plugin, event);
                Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
                }, EventPriority.MONITOR, executor, plugin, true);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // no SamuraiBoss on this backend
            }
        });
    }

    private static void handle(AetherionItems plugin, Event event) {
        Map<?, ?> damage;
        try {
            Object raw = event.getClass().getMethod("getDamage").invoke(event);
            if (!(raw instanceof Map<?, ?> map)) {
                return;
            }
            damage = map;
        } catch (ReflectiveOperationException ex) {
            return;
        }
        double total = 0.0;
        for (Object value : damage.values()) {
            if (value instanceof Number number && number.doubleValue() > 0.0) {
                total += number.doubleValue();
            }
        }
        if (total <= 0.0) {
            return;
        }
        for (Map.Entry<?, ?> entry : damage.entrySet()) {
            if (!(entry.getKey() instanceof UUID id) || !(entry.getValue() instanceof Number dealt)) {
                continue;
            }
            if (dealt.doubleValue() / total < MIN_SHARE) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                reward(plugin, player);
            }
        }
    }

    private static void reward(AetherionItems plugin, Player player) {
        boolean german = AetherServices.german(player);
        var coins = plugin.getCoins();
        if (coins != null) {
            coins.add(player, COINS);
        }
        CodexService codex = plugin.getCodex();
        if (codex != null) {
            boolean first = !codex.hasAnyBossKill(player);
            codex.addBossKill(player, BOSS_ID);
            if (first) {
                UnlockToast.show(player, "Dungeon Journal", "The bosses keep receipts");
            }
        }
        player.sendMessage(german
                ? "§d❀ Sakuragi Jinsai §7ist besiegt. §6+" + String.format("%,d", COINS).replace(',', '.') + " Coins§7, steht jetzt im Dungeon-Journal."
                : "§d❀ Sakuragi Jinsai §7is beaten. §6+" + String.format("%,d", COINS) + " coins§7, noted in the Dungeon Journal.");
        if (ThreadLocalRandom.current().nextDouble() >= BLADE_CHANCE || plugin.getCustomItem() == null) {
            return;
        }
        ItemStack blade = plugin.getCustomItem().createAshenKatana();
        for (ItemStack rest : player.getInventory().addItem(blade).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        player.sendMessage(german
                ? "§d❀ §fEr legt dir seine Klinge hin: §dBlossom Blade§f."
                : "§d❀ §fHe leaves you his blade: §dBlossom Blade§f.");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            online.sendMessage(AetherServices.german(online)
                    ? "§d❀ §f" + player.getName() + " §7hat Sakuragi Jinsai die §dBlossom Blade §7abgerungen."
                    : "§d❀ §f" + player.getName() + " §7took the §dBlossom Blade §7from Sakuragi Jinsai.");
        }
    }
}
