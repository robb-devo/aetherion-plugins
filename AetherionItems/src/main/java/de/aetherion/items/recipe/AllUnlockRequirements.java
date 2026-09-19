package de.aetherion.items.recipe;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AllUnlockRequirements implements UnlockRequirement {

    private final UnlockRequirement[] parts;

    public AllUnlockRequirements(UnlockRequirement... parts) {
        this.parts = parts == null ? new UnlockRequirement[0] : parts;
    }

    @Override
    public boolean isUnlocked(Player player) {
        for (UnlockRequirement part : parts) {
            if (part != null && !part.isUnlocked(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getDisplayText() {
        List<String> texts = new ArrayList<>();
        for (UnlockRequirement part : parts) {
            if (part == null) {
                continue;
            }
            String text = part.getDisplayText();
            if (text != null && !text.isBlank()) {
                texts.add(text);
            }
        }
        return String.join(" §8· §7", texts);
    }
}
