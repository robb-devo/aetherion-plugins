package de.aetherion.bossengine.item;

import org.bukkit.Material;

import java.util.List;

public class SpawnItemDefinition {

    private final String id;
    private final String bossId;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final boolean glowing;
    private final boolean consume;
    private final int cooldownSeconds;
    private final boolean requireAltar;
    private final SpawnItemMode mode;

    public SpawnItemDefinition(
            String id,
            String bossId,
            Material material,
            String displayName,
            List<String> lore,
            boolean glowing,
            boolean consume,
            int cooldownSeconds,
            boolean requireAltar,
            SpawnItemMode mode
    ) {
        this.id = id;
        this.bossId = bossId;
        this.material = material;
        this.displayName = displayName;
        this.lore = List.copyOf(lore == null ? List.of() : lore);
        this.glowing = glowing;
        this.consume = consume;
        this.cooldownSeconds = cooldownSeconds;
        this.requireAltar = requireAltar;
        this.mode = mode == null ? SpawnItemMode.SUMMON : mode;
    }

    public String getId() {
        return id;
    }

    public String getBossId() {
        return bossId;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public boolean isConsume() {
        return consume;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isRequireAltar() {
        return requireAltar;
    }

    public SpawnItemMode getMode() {
        return mode;
    }
}
