package de.aetherion.quests.editor;

import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists NPC-editor-created quests in {@code editor-quests.yml}.
 * Story quests stay in {@link de.aetherion.quests.quest.QuestRegistry}.
 */
public final class EditorQuestStorage {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public EditorQuestStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder.getAbsolutePath());
        }
        this.file = new File(folder, "editor-quests.yml");
        if (!file.exists()) {
            try {
                YamlConfiguration empty = new YamlConfiguration();
                empty.options().header("""
                        Quests created from the NPC Editor (Link Quest → Create).
                        Story quests live in QuestRegistry — do not mix the two.
                        The plugin never overwrites this file from the jar.
                        """);
                empty.set("quests", new LinkedHashMap<String, Object>());
                empty.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not create editor-quests.yml", ex);
            }
        }
        reload();
    }

    public synchronized void reload() {
        quests.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("quests");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Quest quest = read(id, section);
            if (quest != null) {
                quests.put(quest.getId().toLowerCase(Locale.ROOT), quest);
            }
        }
    }

    public synchronized void registerAll(QuestManager manager) {
        if (manager == null) {
            return;
        }
        for (Quest quest : quests.values()) {
            manager.registerQuest(quest);
        }
    }

    public synchronized Quest createAndSave(String title, String npcId, QuestManager manager) {
        java.util.Set<String> taken = new java.util.HashSet<>(quests.keySet());
        if (manager != null) {
            for (Quest existing : manager.getQuests()) {
                if (existing != null && existing.getId() != null) {
                    taken.add(existing.getId().toLowerCase(Locale.ROOT));
                }
            }
        }
        String id = EditorQuestFactory.uniqueId(title, taken);
        Quest quest = EditorQuestFactory.talkQuest(id, title, npcId);
        quests.put(id, quest);
        save();
        if (manager != null) {
            manager.registerQuest(quest);
        }
        return quest;
    }

    public synchronized Quest get(String id) {
        if (id == null) {
            return null;
        }
        return quests.get(id.toLowerCase(Locale.ROOT));
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                Quests created from the NPC Editor (Link Quest → Create).
                """);
        for (Quest quest : quests.values()) {
            String path = "quests." + quest.getId();
            yaml.set(path + ".title", quest.getTitle());
            yaml.set(path + ".description", quest.getDescription());
            yaml.set(path + ".service", quest.getServiceId());
            if (!quest.getObjectives().isEmpty()) {
                Objective objective = quest.getObjectives().get(0);
                yaml.set(path + ".objective", objective.getType().name());
                yaml.set(path + ".target", objective.getTarget());
                yaml.set(path + ".amount", objective.getAmount());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save editor-quests.yml", ex);
        }
    }

    private static Quest read(String id, ConfigurationSection section) {
        String title = section.getString("title", id);
        String description = section.getString("description", "Talk to this NPC. Created in the NPC Editor.");
        String npcId = section.getString("target", id);
        Quest quest = new Quest(id.toLowerCase(Locale.ROOT), title, description);
        quest.setServiceId(section.getString("service", "editor"));
        String typeName = section.getString("objective", "TALK");
        ObjectiveType type;
        try {
            type = ObjectiveType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = ObjectiveType.TALK;
        }
        quest.addObjective(new Objective(type, npcId, section.getInt("amount", 1)));
        quest.addReward(new Reward("XP", 25));
        quest.addReward(new Reward("Coins", 50));
        return quest;
    }
}
