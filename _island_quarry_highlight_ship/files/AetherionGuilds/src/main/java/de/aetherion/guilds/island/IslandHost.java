package de.aetherion.guilds.island;

import java.util.UUID;

/** A personal island (owner UUID) or a guild island (guild UUID). Key form: "p:uuid" / "g:uuid". */
public record IslandHost(Kind kind, UUID id) {

    public enum Kind {
        PERSONAL,
        GUILD
    }

    public static IslandHost personal(UUID owner) {
        return new IslandHost(Kind.PERSONAL, owner);
    }

    public static IslandHost guild(UUID guildId) {
        return new IslandHost(Kind.GUILD, guildId);
    }

    public boolean isGuild() {
        return kind == Kind.GUILD;
    }

    public String key() {
        return (kind == Kind.GUILD ? "g:" : "p:") + id;
    }

    public static IslandHost parse(String key) {
        if (key == null || key.length() < 3 || key.charAt(1) != ':') {
            return null;
        }
        try {
            UUID id = UUID.fromString(key.substring(2));
            return switch (key.charAt(0)) {
                case 'g' -> guild(id);
                case 'p' -> personal(id);
                default -> null;
            };
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
