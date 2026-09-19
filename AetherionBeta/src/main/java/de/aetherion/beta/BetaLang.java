package de.aetherion.beta;

public enum BetaLang {
    EN,
    DE;

    public static BetaLang parse(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "DE", "GER", "GERMAN", "DEUTSCH" -> DE;
            case "EN", "ENG", "ENGLISH" -> EN;
            default -> null;
        };
    }
}
