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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public synchronized boolean isEditorQuest(String id) {
        return get(id) != null;
    }

    public synchronized void persist(Quest quest, QuestManager manager) {
        if (quest == null || quest.getId() == null || quest.getId().isBlank()) {
            return;
        }
        quests.put(quest.getId().toLowerCase(Locale.ROOT), quest);
        save();
        if (manager != null) {
            manager.registerQuest(quest);
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("""
                Quests created from the NPC Editor (Link Quest → Create).
                """);
        for (Quest quest : quests.values()) {
            write(yaml, quest);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save editor-quests.yml", ex);
        }
    }

    static void write(YamlConfiguration yaml, Quest quest) {
        String path = "quests." + quest.getId();
        yaml.set(path + ".title", quest.getTitle());
        yaml.set(path + ".description", quest.getDescription());
        yaml.set(path + ".service", quest.getServiceId());
        if (quest.getRequiredAccountLevel() > 0) {
            yaml.set(path + ".requireAccountLevel", quest.getRequiredAccountLevel());
        }
        if (quest.hasPriorQuestRequirement()) {
            yaml.set(path + ".requirePriorQuest", quest.getRequiredPriorQuestId());
        }
        if (quest.hasItemRequirement()) {
            yaml.set(path + ".requireItem", quest.getRequiredItemId());
            yaml.set(path + ".requireItemAmount", quest.getRequiredItemAmount());
        }
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Reward reward : quest.getRewards()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", reward.getName());
            row.put("amount", reward.getAmount());
            rewards.add(row);
        }
        yaml.set(path + ".rewards", rewards);
        List<Map<String, Object>> objectives = new ArrayList<>();
        for (Objective objective : quest.getObjectives()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", objective.getType().name());
            row.put("target", objective.getTarget());
            row.put("amount", objective.getAmount());
            objectives.add(row);
        }
        yaml.set(path + ".objectives", objectives);
        if (!quest.getObjectives().isEmpty()) {
            Objective first = quest.getObjectives().get(0);
            yaml.set(path + ".objective", first.getType().name());
            yaml.set(path + ".target", first.getTarget());
            yaml.set(path + ".amount", first.getAmount());
        }
    }

    static Quest read(String id, ConfigurationSection section) {
        String title = section.getString("title", id);
        String description = section.getString("description", "Talk to this NPC. Created in the NPC Editor.");
        Quest quest = new Quest(id.toLowerCase(Locale.ROOT), title, description);
        quest.setServiceId(section.getString("service", "editor"));
        quest.requireAccountLevel(section.getInt("requireAccountLevel", 0));
        quest.requirePriorQuest(section.getString("requirePriorQuest"));
        String requireItem = section.getString("requireItem");
        if (requireItem != null && !requireItem.isBlank()) {
            quest.requireItem(requireItem, section.getInt("requireItemAmount", 1));
        }
        List<Map<?, ?>> rewardRows = section.getMapList("rewards");
        if (rewardRows.isEmpty()) {
            EditorQuestFactory.applyDefaultRewards(quest);
        } else {
            for (Map<?, ?> raw : rewardRows) {
                if (raw == null) {
                    continue;
                }
                String name = stringOf(raw.get("name"), "Coins");
                int amount = intOf(raw.get("amount"), 1);
                if (amount > 0) {
                    quest.addReward(new Reward(name, amount));
                }
            }
            if (quest.getRewards().isEmpty()) {
                EditorQuestFactory.applyDefaultRewards(quest);
            }
        }
        List<Map<?, ?>> objectiveRows = section.getMapList("objectives");
        if (!objectiveRows.isEmpty()) {
            for (Map<?, ?> raw : objectiveRows) {
                if (raw == null) {
                    continue;
                }
                quest.addObjective(new Objective(
                        parseType(stringOf(raw.get("type"), "TALK")),
                        stringOf(raw.get("target"), id),
                        intOf(raw.get("amount"), 1)
                ));
            }
        } else {
            String npcId = section.getString("target", id);
            quest.addObjective(new Objective(
                    parseType(section.getString("objective", "TALK")),
                    npcId,
                    section.getInt("amount", 1)
            ));
        }
        return quest;
    }

    private static ObjectiveType parseType(String typeName) {
        try {
            return ObjectiveType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ObjectiveType.TALK;
        }
    }

    private static String stringOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
