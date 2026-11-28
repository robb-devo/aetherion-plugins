package de.aetherion.tutorial.isle;

import de.aetherion.core.api.BarChrome;

/**
 * The isle, one step at a time. The order is the lesson plan: every step needs what the one before it gave, each
 * system is met when it is first needed, and the boss comes last.
 *
 * <p>{@code mark} is the named spot the arrow points at ({@code isle.json}), {@code teacher} the cast member who
 * owns the step (the one who reminds, and reacts when it is done).
 */
enum Step {
    /** The first minute: before dawn on the bell hill. */
    ARRIVE(null, Cast.TOLLY, BarChrome.Icon.COMPASS),
    RING_BELL("bell", Cast.TOLLY, BarChrome.Icon.STAR),
    /** The Journal: where everything is written down. */
    TALK_ODA("oda", Cast.ODA, BarChrome.Icon.QUEST),
    OPEN_JOURNAL(null, Cast.ODA, BarChrome.Icon.QUEST),
    /** Felling, and the first skill level. */
    TALK_BURR("burr", Cast.BURR, BarChrome.Icon.COMPASS),
    FELL_TREE("lesson_tree_1", Cast.BURR, BarChrome.Icon.AXE),
    TRADE_BURR("burr", Cast.BURR, BarChrome.Icon.QUEST_READY),
    /** The workbench: the first tool made by hand. */
    CRAFT_PICKAXE("workbench", Cast.BURR, BarChrome.Icon.PICK),
    /** Mining, and what Mining Power is. */
    TALK_GRETT("grett", Cast.GRETT, BarChrome.Icon.COMPASS),
    MINE("coal_seam", Cast.GRETT, BarChrome.Icon.PICK),
    TRY_IRON("iron_vein", Cast.GRETT, BarChrome.Icon.PICK),
    /** Armour from what was mined; then the anvil and boosters. */
    TALK_ANSEL("ansel", Cast.ANSEL, BarChrome.Icon.COMPASS),
    CRAFT_ARMOR("forge_bench", Cast.ANSEL, BarChrome.Icon.ANVIL),
    WEAR_ARMOR(null, Cast.ANSEL, BarChrome.Icon.ANVIL),
    TALK_ANSEL_AGAIN("ansel", Cast.ANSEL, BarChrome.Icon.QUEST_READY),
    SOCKET("anvil", Cast.ANSEL, BarChrome.Icon.ANVIL),
    /** Talents, then how a fight works here. */
    TALK_HILDE("hilde", Cast.HILDE, BarChrome.Icon.COMPASS),
    TALENT(null, Cast.HILDE, BarChrome.Icon.STAR),
    DRILL_PARRY("drill_center", Cast.HILDE, BarChrome.Icon.FLAG),
    DRILL_ROLL("drill_center", Cast.HILDE, BarChrome.Icon.FLAG),
    DRILL_FINISHER("drill_center", Cast.HILDE, BarChrome.Icon.FLAG),
    /** Everything at once: Old Clanger. */
    GO_FLAT("arena_gate_north", Cast.HILDE, BarChrome.Icon.COMPASS),
    BOSS("clanger", null, BarChrome.Icon.WARN),
    /** The ferry. */
    TALK_WIM("captain", Cast.WIM, BarChrome.Icon.COMPASS),
    DEPART(null, Cast.WIM, BarChrome.Icon.COMPASS),
    DONE(null, null, BarChrome.Icon.STAR);

    final String mark;
    final String teacher;
    final BarChrome.Icon icon;

    Step(String mark, String teacher, BarChrome.Icon icon) {
        this.mark = mark;
        this.teacher = teacher;
        this.icon = icon;
    }

    Step next() {
        Step[] all = values();
        return this == DONE ? DONE : all[ordinal() + 1];
    }

    /** The step is "go and talk to its teacher". */
    boolean talk() {
        return this == TALK_ODA || this == TALK_BURR || this == TRADE_BURR || this == TALK_GRETT || this == TALK_ANSEL
                || this == TALK_ANSEL_AGAIN || this == TALK_HILDE || this == TALK_WIM;
    }

    static Step parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
