package de.aetherion.core.api;

import java.util.ArrayList;
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
     * Wave 2 role ids: {@code combat}, {@code fish}, {@code trade}, {@code quest}, {@code pad}.
     * Default empty so older implementations stay valid.
     */
    default List<String> wave2Roles() {
        return List.of();
    }

    /**
     * Dev-menu / {@code /stressbots start} role ids (Wave 1 + later waves).
     * Default: {@link #wave1Roles()} plus {@link #wave2Roles()}.
     */
    default List<String> startableRoles() {
        return qaRoles();
    }

    /** Wave 1 + Wave 2 startable QA roles. */
    default List<String> qaRoles() {
        List<String> out = new ArrayList<>(wave1Roles());
        for (String id : wave2Roles()) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
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
