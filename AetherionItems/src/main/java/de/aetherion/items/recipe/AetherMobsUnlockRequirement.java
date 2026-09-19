package de.aetherion.items.recipe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AetherMobsUnlockRequirement implements UnlockRequirement {

    @Override
    public boolean isUnlocked(Player player) {
        return Bukkit.getPluginManager().isPluginEnabled("AetherMobs");
    }

    @Override
    public String getDisplayText() {
        return "Requires AetherMobs";
    }
}