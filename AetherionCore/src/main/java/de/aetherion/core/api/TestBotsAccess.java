package de.aetherion.core.api;

import java.util.List;

/**
 * QA test-bot controls for the Dev menu and {@code /botreport}.
 * Implemented by AetherionStressBots (Mineflayer runner + in-game provisioner).
 */
public interface TestBotsAccess {

    boolean enabled();

    /** Wave 1 role ids: {@code mine}, {@code forage}, {@code catch}, {@code roam}. */
    List<String> wave1Roles();

    /**
     * Dev-menu / {@code /stressbots start} role ids (Wave 1 + later waves).
     * Default: {@link #wave1Roles()}.
     */
    default List<String> startableRoles() {
        return wave1Roles();
    }

    /**
     * Ask the runner to bring this role to {@code count} online bots.
     * @return chat-ready status line (success or error)
     */
    String start(String role, int count);

    String stop(String role);

    String stopAll();

    /** Change stored desired count without spawning. Clamped to caps. */
    int adjustDesired(String role, int delta);

    int desired(String role);

    TestBotReport report();

    /** Multi-line plain text for chat/book/AI QA. */
    String reportText();

    TestBotView bot(String name);
}
