package de.aetherion.core.network;

import java.util.Map;

/**
 * Snapshot map keys that survive Bukkit YAML.
 * <p>
 * A key such as {@code yaml:skills.yml} contains a colon. SnakeYAML often
 * re-reads that as a nested {@code yaml -> skills.yml} node, so apply sees
 * {@code skills=false} and then writes a default level over a real profile.
 * New snapshots use {@code yaml_skills_yml}. Lookup still accepts the legacy
 * colon key and the split-nested shape from snapshots already on disk.
 */
public final class NetworkDataKeys {

    public static final String SKILLS_FILE = "skills.yml";
    public static final String STORAGE_FILE = "storage";

    private NetworkDataKeys() {
    }

    public static String yamlFile(String fileName) {
        return "yaml_" + fileName.replace('.', '_');
    }

    public static String blob(String name) {
        return "blob_" + name.replace('.', '_');
    }

    public static String skills() {
        return yamlFile(SKILLS_FILE);
    }

    public static String storage() {
        return blob(STORAGE_FILE);
    }

    public static Object lookupYaml(Map<String, Object> plain, String fileName) {
        if (plain == null || fileName == null) {
            return null;
        }
        Object safe = plain.get(yamlFile(fileName));
        if (safe != null) {
            return safe;
        }
        Object legacy = plain.get("yaml:" + fileName);
        if (legacy != null) {
            return legacy;
        }
        // Bukkit treats '.' as a path separator, so yaml:skills.yml was stored
        // as key "yaml:skills" with a child named "yml".
        String stem = fileName.endsWith(".yml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        Object splitParent = plain.get("yaml:" + stem);
        if (splitParent instanceof Map<?, ?> parent && parent.get("yml") != null) {
            return parent.get("yml");
        }
        Object split = nested(plain.get("yaml"), fileName);
        if (split != null) {
            return split;
        }
        return null;
    }

    public static Object lookupBlob(Map<String, Object> plain, String name) {
        if (plain == null || name == null) {
            return null;
        }
        Object safe = plain.get(blob(name));
        if (safe != null) {
            return safe;
        }
        Object legacy = plain.get("file:" + name);
        if (legacy != null) {
            return legacy;
        }
        Object split = nested(plain.get("file"), name);
        if (split != null) {
            return split;
        }
        return null;
    }

    private static Object nested(Object node, String child) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(child);
    }
}
