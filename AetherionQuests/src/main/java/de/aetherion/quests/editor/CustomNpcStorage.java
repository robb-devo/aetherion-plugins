package de.aetherion.quests.editor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persistent store for moderator FancyNPCs. Separate from story {@code npcs.yml}.
 * Reloads from disk before each write so we never clobber another session.
 */
public final class CustomNpcStorage {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, CustomNpc> npcs = new LinkedHashMap<>();

    public CustomNpcStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder.getAbsolutePath());
        }
        this.file = new File(folder, "editor-npcs.yml");
        if (!file.exists()) {
            try {
                YamlConfiguration empty = new YamlConfiguration();
                empty.options().header("""
                        Moderator-created FancyNPCs (Aetherion NPC editor).
                        Story cast lives in npcs.yml — do not mix the two.
                        The plugin never overwrites this file from the jar.
                        Backup this file before editing by hand.
                        """);
                empty.set("npcs", new LinkedHashMap<String, Object>());
                empty.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not create editor-npcs.yml", ex);
            }
        }
        reload();
    }

    public synchronized void reload() {
        npcs.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            CustomNpc npc = read(id, section);
            if (npc != null) {
                npcs.put(npc.getId().toLowerCase(Locale.ROOT), npc);
            }
        }
    }

    public synchronized Collection<CustomNpc> all() {
        return List.copyOf(npcs.values());
    }

    public synchronized CustomNpc get(String id) {
        if (id == null) {
            return null;
        }
        return npcs.get(id.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean exists(String id) {
        return get(id) != null;
    }

    public synchronized void save(CustomNpc npc) {
        if (npc == null || npc.getId() == null || npc.getId().isBlank()) {
            return;
        }
        reload();
        npcs.put(npc.getId().toLowerCase(Locale.ROOT), npc);
        writeAll();
    }

    public synchronized void delete(String id) {
        if (id == null) {
            return;
        }
        reload();
        npcs.remove(id.toLowerCase(Locale.ROOT));
        writeAll();
    }

    private void writeAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                Moderator-created FancyNPCs (Aetherion NPC editor).
                Story cast lives in npcs.yml — do not mix the two.
                """);
        for (CustomNpc npc : npcs.values()) {
            write(yaml, npc);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save editor-npcs.yml", ex);
        }
    }

    private static void write(YamlConfiguration yaml, CustomNpc npc) {
        String path = "npcs." + npc.getId();
        yaml.set(path + ".name", npc.getName());
        yaml.set(path + ".subtitle", npc.getSubtitle());
        yaml.set(path + ".skin", npc.getSkinUsername());
        yaml.set(path + ".slim", npc.isSlim());
        yaml.set(path + ".preset", npc.getPreset().id());
        yaml.set(path + ".world", npc.getWorld());
        yaml.set(path + ".x", npc.getX());
        yaml.set(path + ".y", npc.getY());
        yaml.set(path + ".z", npc.getZ());
        yaml.set(path + ".yaw", npc.getYaw());
        yaml.set(path + ".pitch", npc.getPitch());
        yaml.set(path + ".quest", npc.getLinkedQuestId());
        yaml.set(path + ".mode", npc.getMode().id());
        yaml.set(path + ".autoQuest", npc.isAutoQuest());
        yaml.set(path + ".start", npc.getStartPage());
        for (CustomNpc.DialoguePage page : npc.pages().values()) {
            String pagePath = path + ".pages." + page.id();
            yaml.set(pagePath + ".lines", new ArrayList<>(page.lines()));
            List<Map<String, String>> choices = new ArrayList<>();
            for (CustomNpc.DialogueChoice choice : page.choices()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("text", choice.text());
                row.put("action", choice.action().name());
                row.put("target", choice.target());
                choices.add(row);
            }
            yaml.set(pagePath + ".choices", choices);
        }
    }

    private static CustomNpc read(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        CustomNpc npc = new CustomNpc(id, name);
        npc.setSubtitle(section.getString("subtitle", "Guide"));
        npc.setPreset(AppearancePreset.parse(section.getString("preset", "worker")));
        npc.setSkinUsername(section.getString("skin", npc.getPreset().skinUsername()));
        npc.setSlim(section.getBoolean("slim", npc.getPreset().slim()));
        npc.setWorld(section.getString("world"));
        npc.setLocationFromParts(
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
        npc.setLinkedQuestId(section.getString("quest"));
        npc.setMode(NpcMode.parse(section.getString("mode"), npc.hasLinkedQuest()));
        npc.setAutoQuest(section.getBoolean("autoQuest", true));
        npc.setStartPage(section.getString("start", CustomNpc.START_PAGE));
        ConfigurationSection pages = section.getConfigurationSection("pages");
        if (pages != null) {
            npc.pages().clear();
            for (String pageId : pages.getKeys(false)) {
                ConfigurationSection pageSection = pages.getConfigurationSection(pageId);
                if (pageSection == null) {
                    continue;
                }
                CustomNpc.DialoguePage page = new CustomNpc.DialoguePage(CustomNpc.sanitizePageId(pageId));
                page.lines().clear();
                page.lines().addAll(pageSection.getStringList("lines"));
                if (page.lines().isEmpty()) {
                    page.lines().add("…");
                }
                List<Map<?, ?>> rawChoices = pageSection.getMapList("choices");
                for (Map<?, ?> raw : rawChoices) {
                    if (raw == null) {
                        continue;
                    }
                    String text = stringOf(raw.get("text"), "…");
                    DialogueAction action = DialogueAction.parse(stringOf(raw.get("action"), "CLOSE"));
                    String target = stringOf(raw.get("target"), "");
                    if ("null".equals(target)) {
                        target = "";
                    }
                    page.choices().add(new CustomNpc.DialogueChoice(text, action, target));
                }
                npc.pages().put(page.id(), page);
            }
        }
        if (npc.pages().isEmpty()) {
            npc.pages().put(CustomNpc.START_PAGE, CustomNpc.DialoguePage.starter());
        }
        return npc;
    }

    private static String stringOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
