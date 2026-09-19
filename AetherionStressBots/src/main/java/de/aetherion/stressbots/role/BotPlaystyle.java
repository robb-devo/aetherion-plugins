package de.aetherion.stressbots.role;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.ProgressAccess;
import de.aetherion.stressbots.AetherionStressBots;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/**
 * Shared first-join playstyle for QA bots: English locale, trader unlock, starter coins.
 * Does not change live YAML or wipe player data for humans.
 */
public final class BotPlaystyle {

    public static final String FLAG_TRADER = "TRADER";
    public static final long DEFAULT_STARTER_COINS = 2_500L;

    private BotPlaystyle() {
    }

    public static void apply(AetherionStressBots plugin, Player player, BotRole role) {
        if (plugin == null || player == null || role == null || !role.qa()) {
            return;
        }
        forceEnglish(plugin, player);
        unlockTrader(plugin, player, role);
        topUpCoins(plugin, player, role);
        unlockStarterSpawns(plugin, player, role);
    }

    public static void forceEnglish(AetherionStressBots plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean set = setQuestLanguageEnglish(player);
        try {
            player.performCommand("language en");
        } catch (RuntimeException ignored) {
            // command may be missing when Quests is offline
        }
        if (isLanguageWindow(player.getOpenInventory())) {
            player.closeInventory();
            if (plugin != null) {
                plugin.getActivity().markAction(player, "language dismissed (en)");
            }
        } else if (set && plugin != null) {
            plugin.getActivity().markAction(player, "language en");
        }
    }

    public static boolean isLanguageTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String plain = title.replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT);
        return plain.contains("language") || plain.contains("sprache");
    }

    public static boolean isLanguageWindow(InventoryView view) {
        if (view == null) {
            return false;
        }
        try {
            String title = PlainTextComponentSerializer.plainText().serialize(view.title());
            return isLanguageTitle(title);
        } catch (RuntimeException ignored) {
            return isLanguageTitle(view.getTitle());
        }
    }

    private static boolean setQuestLanguageEnglish(Player player) {
        try {
            Class<?> playerLang = Class.forName("de.aetherion.quests.lang.PlayerLang");
            Class<?> langCode = Class.forName("de.aetherion.quests.lang.LangCode");
            Object english = java.lang.Enum.valueOf(langCode.asSubclass(Enum.class), "EN");
            playerLang.getMethod("set", Player.class, langCode).invoke(null, player, english);
            return true;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return false;
        }
    }

    private static void unlockTrader(AetherionStressBots plugin, Player player, BotRole role) {
        boolean want = role == BotRole.TRADE || plugin.getConfig().getBoolean("testbots.playstyle.unlock-trader", true);
        if (!want) {
            return;
        }
        ProgressAccess progress = AetherServices.progress();
        if (progress == null) {
            return;
        }
        if (progress.unlockSilent(player, FLAG_TRADER)) {
            plugin.getActivity().markAction(player, "unlock TRADER");
        }
    }

    private static void topUpCoins(AetherionStressBots plugin, Player player, BotRole role) {
        long want = plugin.getConfig().getLong("testbots.playstyle.starter-coins", DEFAULT_STARTER_COINS);
        if (role != BotRole.TRADE) {
            want = plugin.getConfig().getLong("testbots.playstyle.pocket-coins", Math.min(250L, want));
        }
        if (want <= 0L) {
            return;
        }
        CoinAccess coins = AetherServices.coins();
        if (coins == null) {
            return;
        }
        long have = coins.get(player);
        if (have >= want) {
            return;
        }
        coins.add(player, want - have);
        plugin.getActivity().markAction(player, "starter coins +" + (want - have));
    }

    private static void unlockStarterSpawns(AetherionStressBots plugin, Player player, BotRole role) {
        if (!plugin.getConfig().getBoolean("testbots.playstyle.unlock-spawns", true)) {
            return;
        }
        if (role != BotRole.TRADE && role != BotRole.QUEST && role != BotRole.PAD && role != BotRole.ROAM) {
            return;
        }
        HubAccess hub = AetherServices.hub();
        if (hub == null) {
            return;
        }
        for (String id : new String[] {"harbour", "eldervale", "forage_isle", "capital"}) {
            if (hub.unlockNew(player.getUniqueId(), id)) {
                plugin.getActivity().markAction(player, "spawn unlock " + id);
            }
        }
    }
}
