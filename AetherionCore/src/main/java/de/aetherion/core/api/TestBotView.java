package de.aetherion.core.api;

import java.util.List;

/**
 * One connected (or recently seen) test bot.
 */
public record TestBotView(
        String name,
        String role,
        String world,
        double x,
        double y,
        double z,
        String activity,
        String heldItem,
        int deaths,
        String lastAction,
        String lastError,
        List<String> recentActions
) {
    public TestBotView {
        name = name == null ? "" : name;
        role = role == null ? "" : role;
        world = world == null ? "?" : world;
        activity = activity == null ? "idle" : activity;
        heldItem = heldItem == null ? "-" : heldItem;
        lastAction = lastAction == null ? "" : lastAction;
        lastError = lastError == null ? "" : lastError;
        recentActions = recentActions == null ? List.of() : List.copyOf(recentActions);
    }
}
