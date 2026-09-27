package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player studio state: pending chat input, list positions, picker filters and the "back to editor" spot.
 */
public final class EditorSessions {

    public static final class Session {
        private TextInput input;
        private int homePage;
        private int questPage;
        private String questSearch;
        private boolean showTutorialQuests;
        private Runnable returnTo;

        public TextInput input() {
            return input;
        }

        public boolean prompting() {
            return input != null;
        }

        void setInput(TextInput input) {
            this.input = input;
        }

        public void clearInput() {
            this.input = null;
        }

        public int homePage() {
            return homePage;
        }

        public void setHomePage(int homePage) {
            this.homePage = Math.max(0, homePage);
        }

        public int questPage() {
            return questPage;
        }

        public void setQuestPage(int questPage) {
            this.questPage = Math.max(0, questPage);
        }

        public String questSearch() {
            return questSearch;
        }

        public void setQuestSearch(String questSearch) {
            this.questSearch = questSearch == null || questSearch.isBlank() ? null : questSearch.trim();
        }

        public boolean showTutorialQuests() {
            return showTutorialQuests;
        }

        public void setShowTutorialQuests(boolean showTutorialQuests) {
            this.showTutorialQuests = showTutorialQuests;
        }

        /** Screen to reopen from chat links ("Back to editor") after a preview or input. */
        public Runnable returnTo() {
            return returnTo;
        }

        public void setReturnTo(Runnable returnTo) {
            this.returnTo = returnTo;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session of(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
    }

    public Session peek(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public Map<UUID, Session> all() {
        return sessions;
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            sessions.remove(playerId);
        }
    }
}
