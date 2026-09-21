package de.aetherion.quests.editor;

import java.util.Locale;

/**
 * Simple actions a dialogue choice can fire. Quest ids resolve to the live quest
 * manager (story or editor-created).
 */
public enum DialogueAction {
    CLOSE,
    PAGE,
    RUN_CONSOLE,
    RUN_PLAYER,
    OFFER_QUEST,
    START_QUEST,
    TURN_IN_QUEST;

    public static DialogueAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLOSE;
        }
        try {
            return DialogueAction.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return CLOSE;
        }
    }

    public String label() {
        return switch (this) {
            case CLOSE -> "Close the chat";
            case PAGE -> "Open another page";
            case RUN_CONSOLE -> "Run a console command";
            case RUN_PLAYER -> "Run a player command";
            case OFFER_QUEST -> "Offer the quest";
            case START_QUEST -> "Start the quest";
            case TURN_IN_QUEST -> "Turn in the quest";
        };
    }

    public String hint() {
        return switch (this) {
            case CLOSE -> "Ends the conversation.";
            case PAGE -> "Jumps to another dialogue page.";
            case RUN_CONSOLE -> "Server runs a safe command.";
            case RUN_PLAYER -> "The player runs a safe command.";
            case OFFER_QUEST -> "Shows Accept / Decline.";
            case START_QUEST -> "Starts the quest immediately.";
            case TURN_IN_QUEST -> "Completes the quest if ready.";
        };
    }

    public boolean needsTarget() {
        return this != CLOSE;
    }

    public boolean isAdvanced() {
        return this == RUN_CONSOLE || this == RUN_PLAYER;
    }
}
