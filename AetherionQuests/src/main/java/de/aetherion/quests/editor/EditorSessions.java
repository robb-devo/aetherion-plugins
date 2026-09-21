package de.aetherion.quests.editor;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
        LINE_EDIT,
        CHOICE_TEXT,
        SKIN,
        COMMAND,
        PAGE_ID,
        QUEST_ID,
        QUEST_TITLE,
        REWARD_NAME,
        ITEM_SEARCH
    }

    public static final class Session {
        private Prompt prompt = Prompt.NONE;
        private String npcId;
        private String pageId;
        private int choiceIndex = -1;
        private int listPage;
        private boolean confirmDelete;
        private EditorScreen returnTo = EditorScreen.EDIT;
        private String promptPreview;
        private String promptHint;
        private BukkitTask promptWatch;
        private long promptUntilMs;
        private String gatherPurpose;
        private String gatherKind;
        private String itemFilter;

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

        public EditorScreen returnTo() {
            return returnTo == null ? EditorScreen.EDIT : returnTo;
        }

        public void setReturnTo(EditorScreen returnTo) {
            this.returnTo = returnTo == null ? EditorScreen.EDIT : returnTo;
        }

        public String promptPreview() {
            return promptPreview;
        }

        public void setPromptPreview(String promptPreview) {
            this.promptPreview = promptPreview;
        }

        public String promptHint() {
            return promptHint;
        }

        public void setPromptHint(String promptHint) {
            this.promptHint = promptHint;
        }

        public BukkitTask promptWatch() {
            return promptWatch;
        }

        public void setPromptWatch(BukkitTask promptWatch) {
            this.promptWatch = promptWatch;
        }

        public long promptUntilMs() {
            return promptUntilMs;
        }

        public void setPromptUntilMs(long promptUntilMs) {
            this.promptUntilMs = promptUntilMs;
        }

        public String gatherPurpose() {
            return gatherPurpose;
        }

        public void setGatherPurpose(String gatherPurpose) {
            this.gatherPurpose = gatherPurpose;
        }

        public String gatherKind() {
            return gatherKind;
        }

        public void setGatherKind(String gatherKind) {
            this.gatherKind = gatherKind;
        }

        public String itemFilter() {
            return itemFilter;
        }

        public void setItemFilter(String itemFilter) {
            this.itemFilter = itemFilter;
        }

        public void clearPrompt() {
            this.prompt = Prompt.NONE;
            this.promptPreview = null;
            this.promptHint = null;
            this.promptUntilMs = 0L;
            if (promptWatch != null) {
                promptWatch.cancel();
                promptWatch = null;
            }
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
            Session session = sessions.remove(playerId);
            if (session != null) {
                session.clearPrompt();
            }
        }
    }
}
