package de.aetherion.core.api;

/**
 * Aggregated per-role line for the Dev-menu Testbots page.
 */
public record TestBotRoleView(
        String id,
        String display,
        int online,
        int desired,
        int cap,
        String activityHint,
        String lastError
) {
    public TestBotRoleView {
        id = id == null ? "" : id;
        display = display == null ? id : display;
        activityHint = activityHint == null ? "idle" : activityHint;
        lastError = lastError == null ? "" : lastError;
    }
}
