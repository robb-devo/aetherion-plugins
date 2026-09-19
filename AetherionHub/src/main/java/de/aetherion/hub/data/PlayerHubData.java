package de.aetherion.hub.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerHubData {

    private final UUID uuid;
    private String selectedId;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private boolean hintShown;

    public PlayerHubData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String selectedId() {
        return selectedId;
    }

    public void setSelectedId(String selectedId) {
        this.selectedId = selectedId;
    }

    public Set<String> unlocked() {
        return unlocked;
    }

    public boolean isUnlocked(String spawnId) {
        return unlocked.contains(spawnId);
    }

    public boolean hintShown() {
        return hintShown;
    }

    public void setHintShown(boolean hintShown) {
        this.hintShown = hintShown;
    }
}
