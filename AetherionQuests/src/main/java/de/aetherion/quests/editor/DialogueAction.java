package de.aetherion.quests.editor;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * What a dialogue reply does when the player clicks it.
 * <p>
 * Constant names are persisted in {@code editor-npcs.yml} ({@code choices[].action}) — never rename them.
 * Quest actions only link an <em>existing</em> quest id; blank target = the NPC's main quest.
 */
public enum DialogueAction {
    CLOSE(Kind.TALK, Material.OAK_DOOR, "End conversation",
            List.of("Closes the chat. Nothing else happens.")),
    PAGE(Kind.TALK, Material.ARROW, "Continue talking",
            List.of("Opens another page of this", "conversation.")),
    RUN_CONSOLE(Kind.COMMAND, Material.COMMAND_BLOCK, "Run command (server)",
            List.of("The server runs a command with", "full rights. Dangerous ones are blocked.")),
    RUN_PLAYER(Kind.COMMAND, Material.CHAIN_COMMAND_BLOCK, "Run command (player)",
            List.of("The player runs a command with", "their own permissions.")),
    OFFER_QUEST(Kind.QUEST, Material.MAP, "Offer a quest",
            List.of("Shows the quest with Accept / Decline.", "The player decides.")),
    START_QUEST(Kind.QUEST, Material.FILLED_MAP, "Start a quest",
            List.of("Starts the quest immediately.", "Players only hold one quest at a time —",
                    "their current one gets cancelled.")),
    TURN_IN_QUEST(Kind.QUEST, Material.EMERALD, "Turn in a quest",
            List.of("Completes the quest and pays the", "rewards — if the player finished it."));

    /** Groups actions in the editor and decides which target a reply needs. */
    public enum Kind {
        TALK,
        QUEST,
        COMMAND
    }

    private final Kind kind;
    private final Material icon;
    private final String title;
    private final List<String> description;

    DialogueAction(Kind kind, Material icon, String title, List<String> description) {
        this.kind = kind;
        this.icon = icon;
        this.title = title;
        this.description = description;
    }

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

    public Kind kind() {
        return kind;
    }

    public Material icon() {
        return icon;
    }

    /** Human title shown in the editor ("Offer a quest"). */
    public String title() {
        return title;
    }

    /** Short gray explanation lines for tooltips. */
    public List<String> description() {
        return description;
    }

    /** Kept for older call sites — same as {@link #title()}. */
    public String label() {
        return title;
    }

    public boolean isQuest() {
        return kind == Kind.QUEST;
    }

    public boolean isCommand() {
        return kind == Kind.COMMAND;
    }

    /** Whether switching between two actions can keep the reply's target (same kind of target). */
    public boolean sharesTargetWith(DialogueAction other) {
        if (other == this) {
            return true;
        }
        return other != null && other.kind == kind && kind != Kind.TALK;
    }

    /** Subtle hint players see under a reply button — never exposes internals. */
    public String playerHint() {
        return switch (this) {
            case CLOSE -> "Ends the conversation";
            case OFFER_QUEST -> "About a quest";
            case START_QUEST -> "Starts a quest";
            case TURN_IN_QUEST -> "Hand in your quest";
            default -> null;
        };
    }
}
