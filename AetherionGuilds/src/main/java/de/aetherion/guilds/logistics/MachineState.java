package de.aetherion.guilds.logistics;

/**
 * One word for how a production piece is doing, the same word everywhere: the floating tag over the machine,
 * its menu, the Production page and the island bar. Each state carries the one thing to do about it.
 */
public enum MachineState {

    RUNNING("§a", "●", "Running", "All good."),
    RECEIVING("§a", "●", "Filling up", "Belts are bringing goods in."),
    NEEDS_BELT("§6", "○", "Needs belt out", "Lay a belt from its orange chute to a green arrow."),
    NEEDS_BELT_IN("§6", "○", "Needs belt in", "Run a belt onto one of its green arrows."),
    NEEDS_BELT_OUT("§6", "○", "Needs belt out", "Lay a belt away from its orange chute."),
    DEAD_END("§c", "✖", "Belt leads nowhere", "Run that belt into a hut, depot or machine."),
    FULL("§c", "■", "Full", "Take goods out, upgrade it, or belt it to more storage."),
    BACKED_UP("§e", "■", "Backed up", "Whatever it feeds is full. Add storage or a Splitter."),
    WAITING("§7", "○", "Starved", "Nothing is arriving: check what feeds it."),
    BATCHING("§e", "◔", "Gathering a batch", "It works in packs of 128."),
    PICK_FILTER("§6", "?", "Pick a good", "Open it and choose what goes out the front."),
    BUILDING("§e", "…", "Building", "Going up, a moment."),
    LECTERN("§e", "✎", "Right-click the lectern", "Your Hub: build, belts, upgrades, Coin Shortcuts."),
    IDLE("§7", "○", "Idle", "");

    private final String color;
    private final String dot;
    private final String word;
    private final String hint;

    MachineState(String color, String dot, String word, String hint) {
        this.color = color;
        this.dot = dot;
        this.word = word;
        this.hint = hint;
    }

    public String color() {
        return color;
    }

    public String word() {
        return word;
    }

    /** What to do about it (empty when nothing). */
    public String hint() {
        return hint;
    }

    /** "§a● Running" */
    public String tag() {
        return color + dot + " " + word;
    }

    /** Something the player should fix (shown as a warning on the island bar). */
    public boolean problem() {
        return this == NEEDS_BELT || this == NEEDS_BELT_IN || this == NEEDS_BELT_OUT || this == DEAD_END
                || this == FULL || this == BACKED_UP || this == PICK_FILTER;
    }

    public boolean active() {
        return this == RUNNING || this == RECEIVING;
    }
}
