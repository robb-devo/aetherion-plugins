package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player undo: a snapshot of the NPC taken right before each change.
 * Lives in memory only (cleared on restart); restoring overwrites the NPC with the snapshot.
 */
public final class EditHistory {

    private static final int DEPTH = 40;

    /** {@code before} is the NPC as it was; {@code deleted} marks an undo that brings a deleted NPC back. */
    public record Entry(String npcId, String npcName, String label, CustomNpc before, boolean deleted, long at) {
    }

    private final Map<UUID, Deque<Entry>> stacks = new ConcurrentHashMap<>();

    public void record(Player player, CustomNpc before, String label, boolean deleted) {
        if (player == null || before == null) {
            return;
        }
        Deque<Entry> stack = stacks.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        synchronized (stack) {
            stack.push(new Entry(before.getId(), before.getName(), label, before, deleted, System.currentTimeMillis()));
            while (stack.size() > DEPTH) {
                stack.removeLast();
            }
        }
    }

    /** Latest change by this player, on any NPC. */
    public Entry peek(Player player) {
        return find(player, null, false);
    }

    public Entry pop(Player player) {
        return find(player, null, true);
    }

    /** Latest change by this player on one NPC — what the Undo button in that NPC's screens reverts. */
    public Entry peek(Player player, String npcId) {
        return npcId == null ? null : find(player, npcId, false);
    }

    public Entry pop(Player player, String npcId) {
        return npcId == null ? null : find(player, npcId, true);
    }

    private Entry find(Player player, String npcId, boolean remove) {
        Deque<Entry> stack = player == null ? null : stacks.get(player.getUniqueId());
        if (stack == null) {
            return null;
        }
        synchronized (stack) {
            Iterator<Entry> it = stack.iterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                if (npcId == null || npcId.equalsIgnoreCase(entry.npcId())) {
                    if (remove) {
                        it.remove();
                    }
                    return entry;
                }
            }
        }
        return null;
    }
}
