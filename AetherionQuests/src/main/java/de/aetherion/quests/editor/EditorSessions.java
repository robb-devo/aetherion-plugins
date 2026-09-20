package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player editor state: chat prompts and which NPC / page is open.
 */
public final class EditorSessions {

    public enum Prompt {
        NONE,
        NAME,
        RENAME,
        SUBTITLE,
        LINE,
        CHOICE_TEXT,
        SKIN,
        COMMAND,
        PAGE_ID,
        QUEST_ID,
        QUEST_TITLE
    }

    public static final class Session {
        private Prompt prompt = Prompt.NONE;
        private String npcId;
        private String pageId;
        private int choiceIndex = -1;
        private int listPage;
        private boolean confirmDelete;

        public Prompt prompt() {
            return prompt;
        }

        public void setPrompt(Prompt prompt) {
            this.prompt = prompt == null ? Prompt.NONE : prompt;
        }

        public boolean prompting() {
            return prompt != Prompt.NONE;
        }

        public String npcId() {
            return npcId;
        }

        public void setNpcId(String npcId) {
            this.npcId = npcId;
        }

        public String pageId() {
            return pageId;
        }

        public void setPageId(String pageId) {
            this.pageId = pageId;
        }

        public int choiceIndex() {
            return choiceIndex;
        }

        public void setChoiceIndex(int choiceIndex) {
            this.choiceIndex = choiceIndex;
        }

        public int listPage() {
            return listPage;
        }

        public void setListPage(int listPage) {
            this.listPage = Math.max(0, listPage);
        }

        public boolean confirmDelete() {
            return confirmDelete;
        }

        public void setConfirmDelete(boolean confirmDelete) {
            this.confirmDelete = confirmDelete;
        }

        public void clearPrompt() {
            this.prompt = Prompt.NONE;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session of(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
    }

    public Session peek(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            sessions.remove(playerId);
        }
    }
}
