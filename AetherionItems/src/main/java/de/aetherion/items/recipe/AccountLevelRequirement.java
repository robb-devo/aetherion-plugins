package de.aetherion.items.recipe;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;

import org.bukkit.entity.Player;

public final class AccountLevelRequirement implements UnlockRequirement {

    private final int level;

    public AccountLevelRequirement(int level) {
        this.level = Math.max(1, level);
    }

    @Override
    public boolean isUnlocked(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getSkills() == null) {
            return false;
        }
        return plugin.getSkills().accountLevel(player) >= level;
    }

    @Override
    public String getDisplayText() {
        if (level == ProgressionService.ISLAND_LEVEL) {
            return "Reach Aetherion Level " + level + " (Islands)";
        }
        if (level == ProgressionService.GUILD_LEVEL) {
            return "Reach Aetherion Level " + level + " (Guilds)";
        }
        return "Reach Aetherion Level " + level;
    }
}
