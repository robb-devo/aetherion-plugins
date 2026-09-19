package de.aetherion.quests.data;


import de.aetherion.quests.model.QuestState;

import java.util.HashMap;
import java.util.Map;


public class PlayerQuestData {


    private final Map<String, QuestState> quests = new HashMap<>();

    private final Map<String, Map<String, Integer>> progress = new HashMap<>();


    public void setQuestState(
            String questId,
            QuestState state
    ) {

        quests.put(
                questId,
                state
        );

    }


    public QuestState getQuestState(
            String questId
    ) {

        return quests.getOrDefault(
                questId,
                QuestState.AVAILABLE
        );

    }


    public boolean hasQuest(
            String questId
    ) {

        return quests.containsKey(
                questId
        );

    }


    public void addProgress(
            String questId,
            String objective,
            int amount
    ) {


        progress
                .computeIfAbsent(
                        questId,
                        k -> new HashMap<>()
                )
                .merge(
                        objective,
                        amount,
                        Integer::sum
                );

    }


    public void setProgress(
            String questId,
            String objective,
            int amount
    ) {

        progress
                .computeIfAbsent(
                        questId,
                        k -> new HashMap<>()
                )
                .put(
                        objective,
                        amount
                );

    }


    public int getProgress(
            String questId,
            String objective
    ) {


        return progress
                .getOrDefault(
                        questId,
                        new HashMap<>()
                )
                .getOrDefault(
                        objective,
                        0
                );

    }


    public Map<String, Map<String, Integer>> getAllProgress() {

        return progress;

    }


    public void clearQuest(String questId) {

        quests.remove(questId);
        progress.remove(questId);

    }


}