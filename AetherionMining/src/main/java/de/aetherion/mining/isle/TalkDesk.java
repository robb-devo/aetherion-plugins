package de.aetherion.mining.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An isle NPC as a Ledger-style desk on the Quests talk bubble (Core {@link TalkAccess} guest
 * speaker): one opening line, topics as reply chips, the NPC's chest board behind "Board…", and
 * "Bye." once a slot is free. A topic answers in a line or two, then the desk comes back without
 * it. While the bubble is up, the NPC's hologram steps aside for that player (it sits where the
 * bubble does). No bubble (Quests off, classic chat, too far): {@link #click} returns false and
 * the caller opens the board like before.
 */
final class TalkDesk {

    /** One question. {@code after} runs once the answer is queued (e.g. point the isle compass). */
    record Topic(String label, NamedTextColor color, String echo, List<String> lines, Consumer<Player> after) {
        static Topic of(String label, NamedTextColor color, String echo, String... lines) {
            return new Topic(label, color, echo, List.of(lines), null);
        }

        Topic then(Consumer<Player> next) {
            return new Topic(label, color, echo, lines, next);
        }
    }

    private static final int MAX_CHIPS = 5;
    /** A scripted beat holds the NPC this long against repeat clicks while its lines type out. */
    private static final long SCRIPT_HOLD_MS = 5000L;

    private final JavaPlugin plugin;
    private final TalkAccess.Speaker speaker;
    private final Supplier<Entity> hologram;
    private final Function<Player, String> opener;
    private final List<Topic> topics;
    private final String boardLabel;
    private final Consumer<Player> board;
    private final String farewell;
    private final Map<UUID, Set<String>> asked = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> scripted = new ConcurrentHashMap<>();

    TalkDesk(JavaPlugin plugin, String id, String displayName, String nameColor, String voice,
             Supplier<Location> anchor, Supplier<Entity> hologram, Function<Player, String> opener,
             List<Topic> topics, String boardLabel, Consumer<Player> board, String farewell) {
        this.plugin = plugin;
        this.speaker = new TalkAccess.Speaker(id, displayName, nameColor, voice, anchor);
        this.hologram = hologram;
        this.opener = opener;
        this.topics = List.copyOf(topics);
        this.boardLabel = boardLabel;
        this.board = board;
        this.farewell = farewell;
    }

    /** The NPC was clicked. True when the bubble handled it. */
    boolean click(Player player) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null || player == null || !talk.bubbles(player)) {
            return false;
        }
        talk.registerSpeaker(speaker);
        if (talk.recentlyPicked(player)) {
            return true;
        }
        if (talk.hasReplies(player, speaker.id())) {
            talk.pulse(player);
            return true;
        }
        if (talk.talkingWith(player, speaker.id())) {
            // Mid-answer or just finished: bring the desk back instead of starting over.
            offer(player);
            return true;
        }
        if (!talk.say(player, speaker.id(), opener.apply(player))) {
            return false;
        }
        asked.put(player.getUniqueId(), ConcurrentHashMap.newKeySet());
        hideHologram(player);
        offer(player);
        return true;
    }

    /**
     * Before a scripted beat: true when this click is already handled — the echo of a chip pick, or chips
     * still waiting (they get a nudge).
     */
    boolean busy(Player player) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null || player == null || !talk.bubbles(player)) {
            return false;
        }
        talk.registerSpeaker(speaker);
        if (talk.recentlyPicked(player)) {
            return true;
        }
        if (talk.hasReplies(player, speaker.id())) {
            talk.pulse(player);
            return true;
        }
        // Lines still typing out: don't start the same beat twice.
        Long last = scripted.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < SCRIPT_HOLD_MS
                && talk.talkingWith(player, speaker.id());
    }

    /**
     * One scripted beat in this NPC's bubble (an isle lesson): the lines, then the chips. The desk's topics
     * stay out of it. False when the bubble can't open; the caller then says it in chat.
     */
    boolean script(Player player, List<String> lines, List<TalkAccess.Reply> replies) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null || player == null || !talk.bubbles(player) || lines.isEmpty()) {
            return false;
        }
        talk.registerSpeaker(speaker);
        if (!talk.say(player, speaker.id(), lines.get(0))) {
            return false;
        }
        for (int i = 1; i < lines.size(); i++) {
            talk.say(player, speaker.id(), lines.get(i));
        }
        scripted.put(player.getUniqueId(), System.currentTimeMillis());
        hideHologram(player);
        if (!replies.isEmpty()) {
            talk.replies(player, speaker.id(), replies);
        }
        return true;
    }

    private void offer(Player player) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null || !player.isOnline()) {
            return;
        }
        Set<String> done = asked.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        List<TalkAccess.Reply> replies = new ArrayList<>();
        int room = MAX_CHIPS - (board != null ? 1 : 0);
        for (Topic topic : topics) {
            if (replies.size() >= room) {
                break;
            }
            if (!done.contains(topic.label())) {
                replies.add(new TalkAccess.Reply(topic.label(), topic.color(), () -> answer(player, topic), topic.echo()));
            }
        }
        if (board != null) {
            replies.add(new TalkAccess.Reply(boardLabel, NamedTextColor.GRAY, () -> board.accept(player), null));
        }
        if (replies.size() < MAX_CHIPS && farewell != null) {
            replies.add(new TalkAccess.Reply("Bye.", NamedTextColor.DARK_GRAY,
                    () -> {
                        TalkAccess now = AetherServices.talk();
                        if (now != null) {
                            now.say(player, speaker.id(), farewell);
                        }
                    }, "Bye."));
        }
        // The bubble lays the chips out once queued lines have typed out.
        talk.replies(player, speaker.id(), replies);
    }

    private void answer(Player player, Topic topic) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null) {
            return;
        }
        asked.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(topic.label());
        for (String line : topic.lines()) {
            talk.say(player, speaker.id(), line);
        }
        if (topic.after() != null) {
            topic.after().accept(player);
        }
        offer(player);
    }

    /** From the cast tick: the hologram comes back once the bubble has gone. */
    void tick() {
        if (hidden.isEmpty()) {
            return;
        }
        TalkAccess talk = AetherServices.talk();
        for (UUID id : List.copyOf(hidden)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                hidden.remove(id);
                asked.remove(id);
                scripted.remove(id);
                continue;
            }
            if (talk == null || !talk.talkingWith(player, speaker.id())) {
                showHologram(player);
                hidden.remove(id);
                asked.remove(id);
                scripted.remove(id);
            }
        }
    }

    void shutdown() {
        for (UUID id : List.copyOf(hidden)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                showHologram(player);
            }
        }
        hidden.clear();
        asked.clear();
        scripted.clear();
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.unregisterSpeaker(speaker.id());
        }
    }

    private void hideHologram(Player player) {
        Entity holo = hologram == null ? null : hologram.get();
        if (holo != null && holo.isValid()) {
            player.hideEntity(plugin, holo);
            hidden.add(player.getUniqueId());
        }
    }

    private void showHologram(Player player) {
        Entity holo = hologram == null ? null : hologram.get();
        if (holo != null && holo.isValid()) {
            player.showEntity(plugin, holo);
        }
    }
}
