package de.aetherion.items.menu;

/**
 * Hotbar slot 8 is reserved for the manager star and the dungeon map.
 * A real stack already in that slot has to move; a manager-marked stack is replaced in place.
 */
final class ManagerSlotGuard {

    private ManagerSlotGuard() {
    }

    static boolean displace(boolean empty, boolean managerMarked) {
        return !empty && !managerMarked;
    }

    /**
     * The parked copy is the same stack still sitting in the slot
     * (a transfer rewrite put it back). Keeping both would duplicate it.
     */
    static boolean parkedAlreadyInSlot(boolean empty, boolean managerMarked, boolean similar, boolean sameAmount) {
        return !empty && !managerMarked && similar && sameAmount;
    }
}
