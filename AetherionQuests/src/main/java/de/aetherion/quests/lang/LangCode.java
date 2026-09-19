package de.aetherion.quests.lang;

import java.util.Locale;

/**
 * Pre-pre-beta language codes. English is complete; German is experimental.
 */
public enum LangCode {

    EN("en", "English", "Fully supported"),
    DE("de", "Deutsch", "Experimental / incomplete / WIP");

    private final String id;
    private final String display;
    private final String status;

    LangCode(String id, String display, String status) {
        this.id = id;
        this.display = display;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String status() {
        return status;
    }

    public boolean german() {
        return this == DE;
    }

    public static LangCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EN;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "de", "deutsch", "german", "ger", "deu" -> DE;
            default -> EN;
        };
    }
}
