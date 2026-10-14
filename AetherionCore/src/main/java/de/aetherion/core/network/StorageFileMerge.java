package de.aetherion.core.network;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Personal storage is one YAML file per player ({@code pages.N.slot-bytes}).
 * A transfer rewrite must not drop a page that the shared file already has.
 * Incoming slots still win when both sides stored that slot, so a real deposit
 * is kept. Slots only the disk file has are copied back.
 */
public final class StorageFileMerge {

    private StorageFileMerge() {
    }

    public static String merge(String existingRaw, String incomingRaw) {
        if (incomingRaw == null || incomingRaw.isBlank()) {
            return existingRaw == null ? "" : existingRaw;
        }
        if (existingRaw == null || existingRaw.isBlank()) {
            return incomingRaw;
        }
        YamlConfiguration existing = load(existingRaw);
        YamlConfiguration incoming = load(incomingRaw);
        if (existing == null || incoming == null) {
            return existingRaw;
        }
        int existingSlots = countSlots(existing);
        int incomingSlots = countSlots(incoming);
        int existingPages = Math.max(1, existing.getInt("unlocked-pages", 1));
        int incomingPages = Math.max(1, incoming.getInt("unlocked-pages", 1));
        if (incomingSlots >= existingSlots && incomingPages >= existingPages) {
            return incomingRaw;
        }
        YamlConfiguration merged = load(incomingRaw);
        if (merged == null) {
            return existingRaw;
        }
        merged.set("unlocked-pages", Math.max(existingPages, incomingPages));
        if (existing.getBoolean("hub-access", false)) {
            merged.set("hub-access", true);
        }
        copyMissingSlots(existing, merged);
        return merged.saveToString();
    }

    public static int countSlots(String raw) {
        YamlConfiguration yaml = load(raw);
        return yaml == null ? 0 : countSlots(yaml);
    }

    public static int unlockedPages(String raw) {
        YamlConfiguration yaml = load(raw);
        if (yaml == null) {
            return 1;
        }
        return Math.max(1, yaml.getInt("unlocked-pages", 1));
    }

    private static void copyMissingSlots(YamlConfiguration from, YamlConfiguration into) {
        for (String key : from.getKeys(true)) {
            if (!key.startsWith("pages.")) {
                continue;
            }
            if (!key.endsWith("-bytes") && !key.endsWith("-id")) {
                continue;
            }
            if (into.contains(key)) {
                continue;
            }
            String raw = from.getString(key);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            into.set(key, raw);
        }
    }

    static int countSlots(YamlConfiguration yaml) {
        if (yaml == null) {
            return 0;
        }
        int slots = 0;
        for (String key : yaml.getKeys(true)) {
            if (key.startsWith("pages.") && key.endsWith("-bytes")) {
                String raw = yaml.getString(key);
                if (raw != null && !raw.isBlank()) {
                    slots++;
                }
            }
        }
        return slots;
    }

    private static YamlConfiguration load(String raw) {
        if (raw == null || raw.isBlank()) {
            return new YamlConfiguration();
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(raw);
            return yaml;
        } catch (Exception ex) {
            return null;
        }
    }
}
