package de.aetherion.quests.editor;

import java.util.Locale;

/**
 * Simple actions a dialogue choice can fire.
 * Quest authoring stays out of this PR — {@link #OFFER_QUEST} / {@link #START_QUEST}
 * / {@link #TURN_IN_QUEST} only link an existing quest id.
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
            case CLOSE -> "Close";
            case PAGE -> "Open page";
            case RUN_CONSOLE -> "Console command";
            case RUN_PLAYER -> "Player command";
            case OFFER_QUEST -> "Offer quest";
            case START_QUEST -> "Start quest";
            case TURN_IN_QUEST -> "Turn in quest";
        };
    }
}
