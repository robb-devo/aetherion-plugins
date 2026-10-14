package de.aetherion.quests.lang;

import de.aetherion.core.api.LangAccess;

import org.bukkit.entity.Player;

/** Bridge for other plugins: reads the stored language preference ({@link PlayerLang}). */
public final class PlayerLangAccess implements LangAccess {

    @Override
    public boolean german(Player player) {
        return PlayerLang.of(player).german();
    }

    @Override
    public String code(Player player) {
        return PlayerLang.of(player).id();
    }
}
