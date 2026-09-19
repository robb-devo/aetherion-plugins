package de.aetherion.quests.lang;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.PlayerQuestStorage;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Player language preference (quests / hints only for now).
 */
public final class PlayerLang {

    private PlayerLang() {
    }

    public static LangCode of(Player player) {
        if (player == null) {
            return LangCode.EN;
        }
        return of(player.getUniqueId());
    }

    public static LangCode of(UUID uuid) {
        PlayerQuestStorage storage = storage();
        if (storage == null || uuid == null) {
            return LangCode.EN;
        }
        return storage.getLanguage(uuid);
    }

    public static boolean hasChosen(Player player) {
        if (player == null) {
            return true;
        }
        PlayerQuestStorage storage = storage();
        return storage == null || storage.hasLanguageChoice(player.getUniqueId());
    }

    public static void set(Player player, LangCode code) {
        if (player == null || code == null) {
            return;
        }
        PlayerQuestStorage storage = storage();
        if (storage == null) {
            return;
        }
        storage.setLanguage(player.getUniqueId(), code);
    }

    private static PlayerQuestStorage storage() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getPlayerQuestStorage();
    }
}
