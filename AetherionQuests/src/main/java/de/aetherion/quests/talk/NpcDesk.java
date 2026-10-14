package de.aetherion.quests.talk;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.CastBook;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.QuestNPC;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Miss Ledger's desk, for anyone: one opening line in the bubble, then at most five reply
 * chips. A <b>topic</b> answers in a line or two and the desk comes back (minus what was
 * asked); an <b>action</b> does something (set a waypoint, open a board) and ends the round;
 * "More…" opens the chest board as details; "Bye." (when a slot is free) closes with the
 * NPC's farewell.
 * <p>
 * Everything rides the normal TalkUx bubble and the {@link DialogPace} clock. Build a fresh
 * desk per click. {@link #open} returns false when the bubble can't run for this player
 * (classic chat, too far) — the caller then does its chat / chest fallback.
 */
public final class NpcDesk {

    private static final int MAX_CHIPS = 5;

    private record Item(String label, NamedTextColor color, String echo, Supplier<String[]> lines,
                        Runnable action, boolean topic) {
    }

    private final QuestNPC npc;
    private final List<Item> items = new ArrayList<>();
    private final Set<String> asked = new HashSet<>();
    private String moreLabel;
    private Runnable more;

    private NpcDesk(QuestNPC npc) {
        this.npc = npc;
    }

    public static NpcDesk of(QuestNPC npc) {
        return new NpcDesk(npc);
    }

    /** A question: the NPC answers with {@code lines}, then the desk returns. */
    public NpcDesk topic(String label, NamedTextColor color, String echo, String... lines) {
        String[] fixed = lines == null ? new String[0] : lines.clone();
        return topic(label, color, echo, () -> fixed);
    }

    /** A question whose answer depends on the player (read at pick time). */
    public NpcDesk topic(String label, NamedTextColor color, String echo, Supplier<String[]> lines) {
        items.add(new Item(label, color, echo, lines, null, true));
        return this;
    }

    /** A doer: runs {@code action} (it may speak) and ends the round. */
    public NpcDesk action(String label, NamedTextColor color, String echo, Runnable action) {
        items.add(new Item(label, color, echo, null, action, false));
        return this;
    }

    /** "More…" — the old chest board, kept as details. */
    public NpcDesk more(String label, Runnable openBoard) {
        this.moreLabel = label;
        this.more = openBoard;
        return this;
    }

    /** This NPC is still talking to the player (opener / answer typing or being read): ignore re-clicks. */
    public static boolean busy(Player player, String npcId) {
        TalkUx talk = TalkUx.get();
        return talk != null && player != null && npcId != null && talk.isTalkingWith(player, npcId)
                && !talk.hasChips(player) && DialogPace.untilFree(player) > 0L;
    }

    /**
     * Say the opener (one or two short lines) and lay out the chips once it has typed out.
     * Returns false when the in-world talk can't run for this player.
     */
    public boolean open(Player player, String... opener) {
        TalkUx talk = TalkUx.get();
        if (talk == null || player == null || npc == null || !talk.enabledFor(player)) {
            return false;
        }
        if (opener != null) {
            for (String line : opener) {
                if (line != null && !line.isBlank()) {
                    LivingNpcProfile.say(player, npc, line);
                }
            }
        }
        later(player, DialogPace.untilReplies(player), () -> offer(player));
        return true;
    }

    private void offer(Player player) {
        TalkUx talk = TalkUx.get();
        if (talk == null || !player.isOnline()) {
            return;
        }
        List<TalkUx.Choice> choices = new ArrayList<>();
        // Topics first; "More…" keeps its slot; "Bye." only when a slot is free (walking off works too).
        int room = MAX_CHIPS - (more != null ? 1 : 0);
        for (Item item : items) {
            if (choices.size() >= room) {
                break;
            }
            if (item.topic() && asked.contains(item.label())) {
                continue;
            }
            choices.add(new TalkUx.Choice(item.label(), item.color(), () -> pick(player, item), false, item.echo()));
        }
        if (choices.isEmpty() && more == null) {
            return;
        }
        if (more != null) {
            choices.add(new TalkUx.Choice(moreLabel, NamedTextColor.GRAY, more, false, null));
        }
        if (choices.size() < MAX_CHIPS) {
            String bye = LangPack.ui(player, "talk_ux.bye", "Bye.");
            choices.add(new TalkUx.Choice(bye, NamedTextColor.DARK_GRAY, () -> {
                String farewell = CastBook.farewell(npc.getId(), player);
                if (farewell != null && !farewell.isBlank()) {
                    LivingNpcProfile.say(player, npc, farewell);
                }
            }, false, bye));
        }
        talk.choices(player, npc.getId(), npc.getName(), choices);
    }

    private void pick(Player player, Item item) {
        if (!item.topic()) {
            if (item.action() != null) {
                item.action().run();
            }
            return;
        }
        asked.add(item.label());
        String[] lines = item.lines() == null ? null : item.lines().get();
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    LivingNpcProfile.say(player, npc, line);
                }
            }
        }
        later(player, DialogPace.untilReplies(player), () -> {
            TalkUx talk = TalkUx.get();
            if (talk != null && talk.isTalkingWith(player, npc.getId()) && !talk.hasChips(player)) {
                offer(player);
            }
        });
    }

    private static void later(Player player, long ticks, Runnable task) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, Math.max(1L, ticks));
    }
}
