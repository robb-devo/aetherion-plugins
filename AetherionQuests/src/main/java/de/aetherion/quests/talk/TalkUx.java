package de.aetherion.quests.talk;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.npc.CastBook;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.LivingNpcLife;
import de.aetherion.quests.npc.NpcMemory;
import de.aetherion.quests.npc.NpcPresence;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.reward.Reward;
import de.aetherion.quests.ui.QuestMarkerManager;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-world talk UX for living NPCs.
 * <p>
 * Speech bubble (TextDisplay) types out above the NPC's head with a per-NPC voice;
 * reply chips (TextDisplay + Interaction hitbox) float beside the NPC. Look at a chip and
 * click, scroll the hotbar to pick one, or use the mirrored chat buttons. Everything is
 * visible only to the player in the conversation and never saved to disk.
 * <p>
 * Presentation follows {@link TalkChrome}: one ink, the NPC's name colour as the only accent
 * (thin rim around bubble / card / focused reply), the nameplate below the bubble doubles as
 * the speaker label, and every piece blooms out of — and tucks back into — the NPC.
 * <p>
 * This layer mirrors the existing chat pipeline — {@link de.aetherion.quests.dialog.DialogManager}
 * still owns pacing, gates and quest state. When the in-world path can't run (FancyNpcs
 * missing, NPC not found, player too far, player picked "classic"), callers fall back to
 * the chest {@link de.aetherion.quests.ui.QuestAcceptGUI}.
 */
public final class TalkUx implements Listener {

    /** Same tag Core janitors honour via {@link de.aetherion.core.entity.DisplayEntities#TALK_UI_TAG}. */
    public static final String TAG = de.aetherion.core.entity.DisplayEntities.TALK_UI_TAG;
    private static final String CMD = "/aetalk";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** The bubble leans this far toward its listener. */
    private static final double LEAN = 0.12;
    /** Re-lay the composition once the listener has walked ~25° around the NPC. */
    private static final double ORBIT_DOT = 0.9;
    /** Reply hitbox height — a little under the reply pitch so neighbours never overlap. */
    private static final float CHIP_HIT_HEIGHT = 0.17f;

    private static TalkUx instance;

    private final AetherionQuests plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, Bark> barks = new HashMap<>();
    private BukkitTask ticker;
    private long tick;
    private int tokenSeq = ThreadLocalRandom.current().nextInt(1000, 9000);

    // Config (read with code defaults; the jar never overwrites a live config.yml)
    private boolean enabled;
    private boolean voiceBlips;
    private boolean chatReplies;
    private boolean previousLine;
    private boolean frame;
    private double maxDistance;
    private int chipTimeoutTicks;
    private int lingerTicks;
    private float bubbleScale;
    /** QA / stress bots click the chest Quest Offer — keep them on the classic path. */
    private List<String> classicPrefixes = List.of();

    /**
     * One reply chip. {@code chatCommand}: durable chat button for this reply (e.g. the
     * {@code /aetherionquest} handlers that keep working after the bubble is gone);
     * null = a session-bound {@code /aetalk pick} button.
     */
    public record Choice(String label, NamedTextColor color, Runnable action, boolean confirm, String echo,
                         String chatCommand) {
        public Choice(String label, NamedTextColor color, Runnable action, boolean confirm, String echo) {
            this(label, color, action, confirm, echo, null);
        }

        public static Choice of(String label, NamedTextColor color, Runnable action) {
            return new Choice(label, color, action, false, label, null);
        }
    }

    private static final class ChipView {
        Choice choice;
        int index;
        TalkPanel panel;
        Interaction hitbox;
        boolean armed;
    }

    private record ChipLabel(Component text, int width) {
    }

    private static final class Session {
        final UUID playerId;
        final String npcId;
        final int token;
        String npcName;
        TextColor accent = TalkChrome.QUEST;
        /** Our nameplate sits under the bubble and names the speaker — no header in the bubble. */
        boolean plate;
        /** Bubble + tail have bloomed (they wait for the first line — no empty box). */
        boolean bloomed;
        Vector facing = new Vector(0, 0, 1);
        TalkPanel bubble;
        TextDisplay tail;
        TalkPanel card;
        int cardWidthPx;
        int cardLines;
        String current = "";
        String previous;
        int shown;
        int total;
        double cps;
        double acc;
        long typedAt = -1;
        long lastLineAt;
        final List<ChipView> chips = new ArrayList<>();
        int chipWidthPx;
        int hover = -1;
        boolean hoverByScroll;
        long chipsAt;
        boolean questOffer;
        Runnable onTimeout;
        Location npcAt;
        long lastPick = -1000;
        long lastRefresh;
        long lastBlip;
        long timeoutTicks;

        Session(UUID playerId, String npcId, int token) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.token = token;
        }
    }

    private static final class Bark {
        TalkPanel panel;
        TextDisplay tail;
        boolean plate;
        long until;
        final Set<UUID> viewers = new HashSet<>();
    }

    private TalkUx(AetherionQuests plugin) {
        this.plugin = plugin;
        reload();
    }

    public static TalkUx start(AetherionQuests plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new TalkUx(plugin);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.purgeLeftovers();
        instance.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, instance::tickAll, 1L, 1L);
        return instance;
    }

    public static TalkUx get() {
        return instance;
    }

    public void reload() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("talk-ux.enabled", true);
        voiceBlips = cfg.getBoolean("talk-ux.voice-blips", true);
        chatReplies = cfg.getBoolean("talk-ux.chat-replies", true);
        previousLine = cfg.getBoolean("talk-ux.show-previous-line", true);
        frame = cfg.getBoolean("talk-ux.bubble-frame", true);
        maxDistance = Math.max(4.0, cfg.getDouble("talk-ux.max-distance", 10.0));
        chipTimeoutTicks = Math.max(200, cfg.getInt("talk-ux.reply-timeout-seconds", 40) * 20);
        lingerTicks = Math.max(20, cfg.getInt("talk-ux.linger-ticks", 90));
        bubbleScale = (float) Math.max(0.3, Math.min(1.2, cfg.getDouble("talk-ux.bubble-scale", 0.56)));
        de.aetherion.quests.dialog.DialogPace.configure(
                cfg.getDouble("talk-ux.chars-per-tick", 1.5),
                cfg.getLong("talk-ux.read-gap-ticks", 36L));
        List<String> prefixes = cfg.getStringList("talk-ux.classic-name-prefixes");
        if (!cfg.isSet("talk-ux.classic-name-prefixes")) {
            prefixes = List.of("QaQuest", "QaCombat", "QaMine", "QaForage", "QaCatch", "QaRoam",
                    "QaFish", "QaTrade", "QaPad", "StressM");
        }
        classicPrefixes = List.copyOf(prefixes);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Session s : List.copyOf(sessions.values())) {
            destroy(s, false);
        }
        sessions.clear();
        for (Bark b : barks.values()) {
            if (b.panel != null) {
                b.panel.kill();
            }
            kill(b.tail);
        }
        barks.clear();
        purgeLeftovers();
        if (instance == this) {
            instance = null;
        }
    }

    /* =====================================================================
     * Public API
     * ===================================================================== */

    /** In-world talk on for this player (global switch + personal "classic" opt-out). */
    public boolean enabledFor(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        String name = player.getName();
        for (String prefix : classicPrefixes) {
            if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                return false;
            }
        }
        NpcMemory memory = NpcMemory.get();
        return memory == null || !memory.prefersClassic(player.getUniqueId());
    }

    /** How close a listener must stand for the bubble to open and stay (blocks). */
    public double reach() {
        return maxDistance;
    }

    public boolean isTalkingWith(Player player, String npcId) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        return s != null && npcId != null && s.npcId.equalsIgnoreCase(npcId);
    }

    public boolean hasChips(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        return s != null && !s.chips.isEmpty();
    }

    public boolean hasChipsWith(Player player, String npcId) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        return s != null && !s.chips.isEmpty() && npcId != null && s.npcId.equalsIgnoreCase(npcId);
    }

    /** A reply was just picked — swallow the NPC click that rode in with the same mouse press. */
    public boolean recentlyPicked(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        return s != null && tick - s.lastPick < 12;
    }

    /** Someone is mid-conversation with this NPC (life routines pause). */
    public boolean isBusy(String npcId) {
        if (npcId == null) {
            return false;
        }
        for (Session s : sessions.values()) {
            if (s.npcId.equalsIgnoreCase(npcId)) {
                return true;
            }
        }
        return false;
    }

    /** A quest offer (not small talk) is waiting on this NPC. */
    public boolean hasOfferWith(Player player, String npcId) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        return s != null && s.questOffer && !s.chips.isEmpty()
                && npcId != null && s.npcId.equalsIgnoreCase(npcId);
    }

    /** Nudge the chips when the player clicks the NPC instead of a reply. */
    public void pulseChips(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        if (s == null || s.chips.isEmpty()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.35f, 1.8f);
        player.sendActionBar(Component.text(ui(player, "pulse", "Pick a reply — look at it and click · or scroll + click"), TalkChrome.HINT));
        for (ChipView chip : s.chips) {
            if (chip.panel != null) {
                chip.panel.nudge(1.12f);
            }
        }
    }

    /**
     * One spoken line reached {@code player}. Starts / continues the bubble when the NPC is
     * a living host within reach; otherwise chat stays the only channel.
     */
    public void line(Player player, String npcId, String displayName, String legacyLine) {
        if (!enabledFor(player) || npcId == null || legacyLine == null || legacyLine.isBlank()) {
            return;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        if (!LivingNpcService.isLiving(id) && !GuestSpeakers.has(id)) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.npcId.equals(id)) {
            Location at = NpcPresence.locate(id);
            if (!near(player, at, maxDistance + 2.0)) {
                return;
            }
            if (s != null) {
                destroy(s, true);
            }
            s = open(player, id, displayName, at);
            if (s == null) {
                return;
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            s.npcName = displayName;
        }
        clearChips(s, true);
        String filled = TalkText.fill(player, legacyLine);
        if (s.current != null && !s.current.isBlank() && s.typedAt >= 0) {
            s.previous = s.current;
        }
        s.current = TalkText.wrap("§f" + filled, de.aetherion.quests.dialog.DialogPace.WRAP_PX);
        s.total = TalkText.visibleLength(s.current);
        s.shown = 0;
        s.acc = 0;
        // One reading speed for every line and every NPC; DialogPace books the read gap after it.
        s.cps = de.aetherion.quests.dialog.DialogPace.charsPerTick();
        s.typedAt = -1;
        s.lastLineAt = tick;
        renderBubble(s, true);
        LivingNpcLife life = LivingNpcLife.get();
        if (life != null) {
            life.gesture(player, id, filled);
        }
    }

    /**
     * Quest offer as reply chips + a quest card. Returns false when the in-world path
     * can't run — the caller then opens the chest {@code QuestAcceptGUI}.
     */
    public boolean offerQuest(
            Player player,
            QuestNPC npc,
            Quest quest,
            Quest conflicting,
            String gateFail,
            Runnable accept,
            Runnable decline,
            Runnable details
    ) {
        if (!enabledFor(player) || npc == null || quest == null) {
            return false;
        }
        String id = npc.getId().toLowerCase(Locale.ROOT);
        if (!LivingNpcService.isLiving(id)) {
            return false;
        }
        Session s = sessions.get(player.getUniqueId());
        Location at = NpcPresence.locate(id);
        if (!near(player, at, maxDistance)) {
            return false;
        }
        if (s == null || !s.npcId.equals(id)) {
            if (s != null) {
                destroy(s, true);
            }
            s = open(player, id, npc.getName(), at);
            if (s == null) {
                return false;
            }
        }
        List<Choice> choices = new ArrayList<>();
        String title = de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle());
        String acceptCmd = "/aetherionquest accept " + quest.getId();
        String declineCmd = "/aetherionquest decline " + quest.getId();
        String detailsCmd = "/aetherionquest details " + quest.getId();
        if (gateFail != null) {
            String hint = de.aetherion.quests.util.QuestSkillGate.requirementHint(quest);
            line(player, id, npc.getName(), gateFail + (hint != null ? " " + hint : ""));
            String gotIt = ui(player, "got_it", "Got it.");
            choices.add(new Choice(gotIt, NamedTextColor.GRAY, decline, false, gotIt, declineCmd));
            choices.add(new Choice(ui(player, "numbers", "Show me the numbers"), NamedTextColor.DARK_GRAY, details, false, null, detailsCmd));
        } else if (conflicting != null) {
            String current = de.aetherion.quests.lang.LangPack.questTitle(player, conflicting.getId(), conflicting.getTitle());
            // Two-step in-world; the chat path has its own CONFIRM ABORT step.
            choices.add(new Choice(ui(player, "conflict_take", "Drop \"{0}\" & take this").replace("{0}", current), NamedTextColor.GOLD, accept, true,
                    ui(player, "conflict_take_echo", "Fine. I'll drop the other job."), acceptCmd));
            choices.add(new Choice(ui(player, "conflict_keep", "Keep my current job"), NamedTextColor.RED, decline, false,
                    ui(player, "conflict_keep_echo", "I'll finish what I started."), declineCmd));
            choices.add(new Choice(ui(player, "details", "Details…"), NamedTextColor.GRAY, details, false, null, detailsCmd));
        } else {
            choices.add(new Choice(CastBook.acceptLabel(id, quest.getId(), player), NamedTextColor.GREEN, accept, false,
                    CastBook.acceptLabel(id, quest.getId(), player), acceptCmd));
            choices.add(new Choice(CastBook.declineLabel(id, player), NamedTextColor.RED, decline, false,
                    CastBook.declineLabel(id, player), declineCmd));
            choices.add(new Choice(ui(player, "details", "Details…"), NamedTextColor.GRAY, details, false, null, detailsCmd));
        }
        s.questOffer = true;
        s.onTimeout = gateFail != null ? null : () -> player.sendMessage(Component.text("  » ", NamedTextColor.DARK_GRAY)
                .append(Component.text(ui(player, "offer_stands", "{0}'s offer stands. ").replace("{0}", npc.getName()), NamedTextColor.GRAY))
                .append(chatButton(ui(player, "accept_button", "[Accept]"), NamedTextColor.GREEN, "/aetherionquest accept " + quest.getId(),
                        ui(player, "accept_hover", "Take {0}").replace("{0}", title)))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(chatButton(ui(player, "decline_button", "[Decline]"), NamedTextColor.RED, "/aetherionquest decline " + quest.getId(),
                        ui(player, "decline_hover", "Maybe another time"))));
        showCard(player, s, quest, conflicting);
        s.timeoutTicks = chipTimeoutTicks;
        showChips(player, s, choices);
        LivingNpcLife life = LivingNpcLife.get();
        if (life != null) {
            life.emote(player, id, "?");
        }
        return true;
    }

    /** Generic reply chips (topics, help desk, "bye"). */
    public boolean choices(Player player, String npcId, String displayName, List<Choice> choices) {
        if (!enabledFor(player) || npcId == null || choices == null || choices.isEmpty()) {
            return false;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        if (!LivingNpcService.isLiving(id) && !GuestSpeakers.has(id)) {
            return false;
        }
        Location at = NpcPresence.locate(id);
        if (!near(player, at, maxDistance)) {
            return false;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.npcId.equals(id)) {
            if (s != null) {
                destroy(s, true);
            }
            s = open(player, id, displayName, at);
            if (s == null) {
                return false;
            }
        }
        s.questOffer = false;
        s.onTimeout = null;
        // Small talk is optional: short-lived, and it never grabs the hotbar wheel.
        s.timeoutTicks = Math.min(chipTimeoutTicks, 20L * 18L);
        showChips(player, s, choices);
        return true;
    }

    /** Drop reply chips (e.g. chat button used) but keep the bubble for the reaction line. */
    public void clearChips(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        if (s != null) {
            clearChips(s, true);
            collapseCard(s);
        }
    }

    public void end(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        if (s != null) {
            destroy(s, true);
        }
    }

    /**
     * Ambient one-liner above an NPC (greetings, banter), visible only to {@code viewers}.
     * Living NPCs speak from the same slot as the talk bubble — right above their nameplate,
     * with a tail — and their quest mark steps aside for the length of the line. One bark
     * per NPC at a time.
     */
    public void bark(String npcId, String displayName, String legacyLine, Collection<? extends Player> viewers, int ticks) {
        if (!enabled || npcId == null || legacyLine == null || viewers == null || viewers.isEmpty()) {
            return;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        Location at = NpcPresence.locate(id);
        if (at == null || at.getWorld() == null) {
            return;
        }
        List<Player> audience = new ArrayList<>();
        for (Player p : viewers) {
            if (p != null && p.isOnline() && enabledFor(p) && !isTalkingWith(p, id)
                    && p.getWorld().equals(at.getWorld())) {
                audience.add(p);
            }
        }
        if (audience.isEmpty()) {
            return;
        }
        Bark old = barks.remove(id);
        if (old != null) {
            if (old.panel != null) {
                old.panel.kill();
            }
            kill(old.tail);
        }
        QuestMarkerManager markers = plugin.getMarkerManager();
        boolean plate = LivingNpcProfile.of(id) != null && markers != null && markers.hasNameplate(id);
        TextColor accent = TalkChrome.accentOf(id);
        String name = TalkText.strip(displayName == null ? id : displayName);
        String said = audience.size() > 1
                ? legacyLine.replace("{player}", "you")
                : TalkText.fill(audience.get(0), legacyLine);
        String body = TalkText.wrap("§f" + said, 150);

        Component text = Component.empty();
        int width = 0;
        int lines = 0;
        if (!plate) {
            text = text.append(Component.text(" " + name + " ", accent, TextDecoration.BOLD)).append(Component.newline());
            width = TalkChrome.width(" " + name + " ", true);
            lines++;
        }
        text = text.append(Component.text(" ")).append(LEGACY.deserialize(body.replace("\n", " \n "))).append(Component.text(" "));
        width = Math.max(width, 8 + TalkChrome.legacyBlockWidth(body));
        lines += body.split("\n", -1).length;

        Location pos = barkPos(plate, at);
        TalkPanel panel = TalkPanel.open(plugin, this::spawnText, pos, TalkChrome.BARK_SCALE,
                d -> stylePanel(d, TextDisplay.TextAlignment.CENTER, TalkChrome.BARK_ALPHA), null);
        if (panel == null) {
            return;
        }
        panel.content(text, width, lines);
        TextDisplay tail = plate ? spawnTail(tailPos(pos, TalkChrome.BARK_SCALE), accent) : null;
        Bark bark = new Bark();
        bark.panel = panel;
        bark.tail = tail;
        bark.plate = plate;
        int life = Math.max(40, ticks);
        bark.until = tick + life;
        for (Player p : audience) {
            panel.show(p);
            if (tail != null) {
                p.showEntity(plugin, tail);
            }
            bark.viewers.add(p.getUniqueId());
            NpcPresence.blip(p, id, 0.16f);
            if (plate && markers != null) {
                markers.quiet(p, id, life + 3);
            }
        }
        panel.popIn(1, true);
        popIn(tail, TalkChrome.BARK_SCALE * 0.9f, 1);
        barks.put(id, bark);
    }

    /* =====================================================================
     * Session lifecycle
     * ===================================================================== */

    private Session open(Player player, String npcId, String displayName, Location npcAt) {
        if (npcAt == null || npcAt.getWorld() == null) {
            return null;
        }
        Session s = new Session(player.getUniqueId(), npcId, ++tokenSeq);
        s.npcName = displayName == null || displayName.isBlank() ? npcId : displayName;
        QuestMarkerManager markers = plugin.getMarkerManager();
        s.accent = TalkChrome.accentOf(npcId);
        s.plate = LivingNpcProfile.of(npcId) != null && markers != null && markers.hasNameplate(npcId);
        s.npcAt = npcAt.clone();
        s.facing = horizontal(player.getLocation().toVector().subtract(npcAt.toVector()));
        Location pos = bubblePos(s, npcAt);
        s.bubble = TalkPanel.open(plugin, this::spawnText, pos, bubbleScale,
                d -> {
                    stylePanel(d, TextDisplay.TextAlignment.CENTER, TalkChrome.BUBBLE_ALPHA);
                    d.setViewRange(0.5f);
                },
                frame ? TalkChrome.rim(s.accent) : null);
        if (s.bubble == null) {
            return null;
        }
        s.tail = spawnTail(tailPos(pos, bubbleScale), s.accent);
        // An ambient line from this NPC would sit in the same slot — the conversation wins.
        Bark bark = barks.get(npcId);
        if (bark != null && bark.viewers.remove(player.getUniqueId())) {
            bark.panel.hide(player);
            if (bark.tail != null && bark.tail.isValid()) {
                player.hideEntity(plugin, bark.tail);
            }
        }
        s.bubble.show(player);
        if (s.tail != null) {
            player.showEntity(plugin, s.tail);
        }
        sessions.put(player.getUniqueId(), s);
        if (markers != null) {
            markers.setTalking(player, npcId);
        }
        NpcMemory memory = NpcMemory.get();
        if (memory != null) {
            memory.noteTalk(player.getUniqueId(), npcId);
        }
        return s;
    }

    private void destroy(Session s, boolean animate) {
        sessions.remove(s.playerId, s);
        clearChips(s, animate);
        if (animate) {
            if (s.bubble != null) {
                s.bubble.collapse(null);
            }
            popOut(s.tail);
            collapseCard(s);
        } else {
            if (s.bubble != null) {
                s.bubble.kill();
            }
            kill(s.tail);
            if (s.card != null) {
                s.card.kill();
            }
        }
        s.card = null;
        Player player = Bukkit.getPlayer(s.playerId);
        QuestMarkerManager markers = plugin.getMarkerManager();
        if (player != null && markers != null && !sessions.containsKey(s.playerId)) {
            // The mark waits for the bubble to fold away, then pops back in.
            markers.clearTalking(player);
        }
    }

    /* =====================================================================
     * Rendering
     * ===================================================================== */

    private void renderBubble(Session s, boolean fresh) {
        if (s.bubble == null || !s.bubble.valid()) {
            return;
        }
        if (fresh) {
            // The rim is sized once per line on the fully typed text — the box never changes
            // while the line types out (the untyped rest is drawn in the bubble's own ink).
            int[] box = bubbleBox(s);
            s.bubble.content(bubbleText(s, s.total), box[0], box[1]);
        }
        s.bubble.body(bubbleText(s, s.shown));
        if (fresh && !s.bloomed) {
            s.bloomed = true;
            s.bubble.popIn(1, true);
            popIn(s.tail, bubbleScale * 0.9f, 1);
        }
    }

    private Component bubbleText(Session s, int shown) {
        Component out = Component.empty();
        boolean first = true;
        if (!s.plate) {
            out = out.append(Component.text(" " + TalkText.strip(s.npcName) + " ", s.accent, TextDecoration.BOLD));
            first = false;
        }
        if (previousLine && s.previous != null && !s.previous.isBlank()) {
            for (String line : TalkText.strip(s.previous).split("\n")) {
                if (!first) {
                    out = out.append(Component.newline());
                }
                out = out.append(Component.text(" " + line + " ", TalkChrome.FAINT));
                first = false;
            }
        }
        if (!first) {
            out = out.append(Component.newline());
        }
        String typed = TalkText.truncate(s.current, shown);
        Component current = Component.text(" ").append(LEGACY.deserialize(typed.replace("\n", " \n ")));
        if (shown < s.total) {
            // Untyped remainder in the bubble's own ink: the box keeps its final size from
            // the first frame, so nothing jumps while the line types out.
            String rest = TalkText.rest(s.current, shown);
            current = current.append(Component.text(rest.replace("\n", " \n "), TalkChrome.inkText(s.accent)));
        }
        return out.append(current.append(Component.text(" ")));
    }

    /** {widest line px, line count} of the fully typed bubble. */
    private int[] bubbleBox(Session s) {
        int width = 0;
        int lines = 0;
        if (!s.plate) {
            width = TalkChrome.width(" " + TalkText.strip(s.npcName) + " ", true);
            lines++;
        }
        if (previousLine && s.previous != null && !s.previous.isBlank()) {
            for (String line : TalkText.strip(s.previous).split("\n")) {
                width = Math.max(width, 8 + TalkChrome.width(line, false));
                lines++;
            }
        }
        for (String line : s.current.split("\n", -1)) {
            width = Math.max(width, 8 + TalkChrome.legacyWidth(line));
            lines++;
        }
        return new int[] {width, Math.max(1, lines)};
    }

    private void showChips(Player player, Session s, List<Choice> choices) {
        clearChips(s, true);
        Location npcAt = s.npcAt != null ? s.npcAt : NpcPresence.locate(s.npcId);
        if (npcAt == null) {
            return;
        }
        s.facing = horizontal(player.getLocation().toVector().subtract(npcAt.toVector()));
        int n = Math.min(choices.size(), 5);
        // One width for the whole column (incl. the bold focus / "Sure?" states): the replies
        // read as a menu, and focusing one never changes its size.
        int widest = 0;
        String armedLabel = ui(player, "armed", "Sure? Click again");
        for (int i = 0; i < n; i++) {
            Choice c = choices.get(i);
            String key = String.valueOf(i + 1);
            widest = Math.max(widest, chipContentWidth(key, c.label(), true));
            if (c.confirm()) {
                widest = Math.max(widest, chipContentWidth(key, armedLabel, true));
            }
        }
        s.chipWidthPx = widest + 6;
        Vector back = right(s.facing).multiply(-TalkChrome.DEAL);
        float hitWidth = (float) Math.max(0.5, s.chipWidthPx * TalkChrome.PX * TalkChrome.CHIP_SCALE);
        for (int i = 0; i < n; i++) {
            ChipView chip = new ChipView();
            chip.choice = choices.get(i);
            chip.index = i;
            Location home = chipHome(s, npcAt, i);
            chip.panel = TalkPanel.open(plugin, this::spawnText, home.clone().add(back),
                    TalkChrome.CHIP_SCALE,
                    d -> stylePanel(d, TextDisplay.TextAlignment.LEFT, TalkChrome.CHIP_ALPHA),
                    frame ? TalkChrome.none() : null);
            if (chip.panel == null) {
                continue;
            }
            ChipLabel label = chipLabel(player, s, chip, false, false);
            chip.panel.content(label.text(), label.width(), 1);
            chip.hitbox = spawn(home.clone().add(0, -0.025, 0), Interaction.class, it -> {
                it.setInteractionWidth(hitWidth);
                it.setInteractionHeight(CHIP_HIT_HEIGHT);
                it.setResponsive(false);
            });
            chip.panel.show(player);
            if (chip.hitbox != null) {
                player.showEntity(plugin, chip.hitbox);
            }
            final Session session = s;
            // Deal out of the NPC, one after another.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sessions.get(session.playerId) != session || !session.chips.contains(chip) || session.npcAt == null) {
                    return;
                }
                chip.panel.teleport(chipHome(session, session.npcAt, chip.index));
                chip.panel.popIn(0, false);
            }, 1L + i * 2L);
            s.chips.add(chip);
        }
        s.hover = -1;
        s.chipsAt = tick;
        if (chatReplies) {
            sendChatReplies(player, s);
        }
        if (s.questOffer) {
            player.sendActionBar(TalkGlyphs.leftClick()
                    .append(Component.text(ui(player, "hint_offer", "Look at a reply + click  ·  "), TalkChrome.HINT))
                    .append(TalkGlyphs.scroll())
                    .append(Component.text(ui(player, "hint_offer_scroll", "or scroll + click"), TalkChrome.HINT)));
        } else {
            player.sendActionBar(TalkGlyphs.leftClick()
                    .append(Component.text(ui(player, "hint_topics", "Look at a reply + click — or just walk on"), TalkChrome.MUTED)));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.25f, 1.6f);
    }

    private void sendChatReplies(Player player, Session s) {
        Component line = Component.text("  » ", s.accent);
        for (int i = 0; i < s.chips.size(); i++) {
            ChipView chip = s.chips.get(i);
            String command = chip.choice.chatCommand() != null
                    ? chip.choice.chatCommand()
                    : CMD + " pick " + s.token + " " + i;
            if (i > 0) {
                line = line.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(chatButton("[" + chip.choice.label() + "]", TalkChrome.soft(chip.choice.color()), command,
                    chip.choice.label()));
        }
        player.sendMessage(line);
    }

    private static Component chatButton(String label, TextColor color, String command, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    /**
     * " 1  Label     " — key column, label, padded to the column width. Rest: faint key,
     * label in its (softened) meaning colour. Focus: key in the NPC's accent, label lifted
     * and bold. Armed: amber "Sure? Click again".
     */
    private ChipLabel chipLabel(Player player, Session s, ChipView chip, boolean focus, boolean armed) {
        String label = armed ? ui(player, "armed", "Sure? Click again") : chip.choice.label();
        String key = String.valueOf(chip.index + 1);
        boolean strong = focus || armed;
        TextColor keyColor = armed ? TalkChrome.AMBER : focus ? s.accent : TalkChrome.FAINT;
        TextColor labelColor = armed ? TalkChrome.AMBER
                : focus ? TalkChrome.bright(chip.choice.color()) : TalkChrome.soft(chip.choice.color());
        int used = chipContentWidth(key, label, strong);
        int spaces = Math.max(1, Math.round((s.chipWidthPx - used) / 4f));
        Component text = Component.text(" ")
                .append(Component.text(key, keyColor).decoration(TextDecoration.BOLD, strong))
                .append(Component.text("  "))
                .append(Component.text(label, labelColor).decoration(TextDecoration.BOLD, strong))
                .append(Component.text(" ".repeat(spaces)));
        return new ChipLabel(text, used + spaces * 4);
    }

    private static int chipContentWidth(String key, String label, boolean strong) {
        return 4 + TalkChrome.width(key, strong) + 8 + TalkChrome.width(TalkText.strip(label), strong);
    }

    private void focusChip(Player player, Session s, ChipView chip, boolean focus) {
        if (chip.panel == null || !chip.panel.valid()) {
            return;
        }
        boolean armed = focus && chip.armed;
        ChipLabel label = chipLabel(player, s, chip, focus, armed);
        chip.panel.content(label.text(), label.width(), 1);
        TextColor tone = armed ? TalkChrome.AMBER : s.accent;
        chip.panel.background(focus
                ? TalkChrome.inkTint(tone, 0.2, TalkChrome.CHIP_FOCUS_ALPHA)
                : TalkChrome.ink(TalkChrome.CHIP_ALPHA));
        chip.panel.rimColor(focus ? TalkChrome.rim(tone) : TalkChrome.none(), 2);
        chip.panel.scaleTo(focus ? TalkChrome.CHIP_FOCUS_SCALE : TalkChrome.CHIP_SCALE, 2);
    }

    /**
     * The quest as a small document beside the NPC: who (small caps, accent), what (gold
     * title, muted brief), then goals and pay. Left of the NPC, top-aligned with the replies.
     */
    private void showCard(Player player, Session s, Quest quest, Quest conflicting) {
        if (s.card != null) {
            s.card.kill();
            s.card = null;
        }
        Location npcAt = s.npcAt != null ? s.npcAt : NpcPresence.locate(s.npcId);
        if (npcAt == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        int[] width = {0};
        java.util.function.BiConsumer<Component, Integer> add = (c, w) -> {
            lines.add(c);
            width[0] = Math.max(width[0], w);
        };
        s.facing = horizontal(player.getLocation().toVector().subtract(npcAt.toVector()));
        String who = " " + TalkChrome.smallCaps(TalkText.strip(s.npcName)) + " ";
        add.accept(Component.text(who, s.accent), TalkChrome.width(who, false));
        String title = " " + TalkText.strip(de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle())) + " ";
        add.accept(Component.text(title, TalkChrome.QUEST, TextDecoration.BOLD), TalkChrome.width(title, true));
        String desc = de.aetherion.quests.lang.LangPack.questDescription(player, quest.getId(),
                quest.getDescription() == null ? "" : quest.getDescription());
        if (desc != null && !desc.isBlank()) {
            for (String l : TalkText.wrap(TalkText.strip(desc), 150).split("\n")) {
                String line = " " + l + " ";
                add.accept(Component.text(line, TalkChrome.MUTED), TalkChrome.width(line, false));
            }
        }
        if (!quest.getObjectives().isEmpty()) {
            add.accept(Component.text(" "), 4);
            String label = " " + TalkChrome.smallCaps(ui(player, "goals", "Goals")) + " ";
            add.accept(Component.text(label, TalkChrome.mix(TalkChrome.QUEST, TalkChrome.MUTED, 0.35)), TalkChrome.width(label, false));
            for (Objective objective : quest.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                String name = TalkText.strip(objective.getDisplayName() != null ? objective.getDisplayName() : pretty(objective.getTarget()));
                String amount = " ×" + Math.max(1, objective.getAmount());
                add.accept(Component.text(" • ", TalkChrome.FAINT)
                                .append(Component.text(name, TalkChrome.TEXT))
                                .append(Component.text(amount + " ", TalkChrome.FAINT)),
                        TalkChrome.width(" • " + name + amount + " ", false));
            }
        }
        if (!quest.getRewards().isEmpty()) {
            if (quest.getObjectives().isEmpty()) {
                add.accept(Component.text(" "), 4);
            }
            String label = " " + TalkChrome.smallCaps(ui(player, "pays", "Pays")) + " ";
            add.accept(Component.text(label, TalkChrome.mix(TalkChrome.READY, TalkChrome.MUTED, 0.35)), TalkChrome.width(label, false));
            for (Reward reward : quest.getRewards()) {
                if (reward == null) {
                    continue;
                }
                String amount = String.valueOf(reward.getAmount());
                String name = TalkText.strip(String.valueOf(reward.getName()));
                add.accept(Component.text(" • ", TalkChrome.FAINT)
                                .append(Component.text(amount, TalkChrome.READY))
                                .append(Component.text(" " + name + " ", TalkChrome.TEXT)),
                        TalkChrome.width(" • " + amount + " " + name + " ", false));
            }
        }
        if (conflicting != null) {
            String warn = " ⚠ " + ui(player, "replaces", "Replaces your current job") + " ";
            add.accept(Component.text(warn, TalkChrome.WARN), TalkChrome.width(warn, false));
        }
        Component body = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                body = body.append(Component.newline());
            }
            body = body.append(lines.get(i));
        }
        s.cardWidthPx = width[0];
        s.cardLines = lines.size();
        Location home = cardHome(s, npcAt);
        Location from = home.clone().add(right(s.facing).multiply(TalkChrome.DEAL));
        s.card = TalkPanel.open(plugin, this::spawnText, from, TalkChrome.CARD_SCALE,
                d -> stylePanel(d, TextDisplay.TextAlignment.LEFT, TalkChrome.CARD_ALPHA),
                frame ? TalkChrome.rim(s.accent) : null);
        if (s.card == null) {
            return;
        }
        s.card.content(body, s.cardWidthPx, s.cardLines);
        s.card.show(player);
        final TalkPanel card = s.card;
        final Session session = s;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.card != card || session.npcAt == null) {
                return;
            }
            card.teleport(cardHome(session, session.npcAt));
            card.popIn(0, false);
        }, 2L);
    }

    /** German overlay for fixed talk-UI strings ({@code ui.talk_ux.<key>}); English passes through as written. */
    private static String ui(Player player, String key, String english) {
        return de.aetherion.quests.lang.LangPack.ui(player, "talk_ux." + key, english);
    }

    private static String pretty(String raw) {
        if (raw == null) {
            return "?";
        }
        String t = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Replies fold back toward the NPC (or vanish at once when {@code animate} is false). */
    private void clearChips(Session s, boolean animate) {
        Vector tuck = right(s.facing).multiply(-TalkChrome.DEAL);
        for (ChipView chip : s.chips) {
            if (chip.panel != null) {
                if (animate) {
                    Location at = chip.panel.location();
                    chip.panel.collapse(at == null ? null : at.add(tuck));
                } else {
                    chip.panel.kill();
                }
            }
            kill(chip.hitbox);
        }
        s.chips.clear();
        s.hover = -1;
    }

    private void collapseCard(Session s) {
        if (s.card == null) {
            return;
        }
        Location at = s.card.location();
        s.card.collapse(at == null ? null : at.add(right(s.facing).multiply(TalkChrome.DEAL)));
        s.card = null;
    }

    /* =====================================================================
     * Tick
     * ===================================================================== */

    private void tickAll() {
        tick++;
        if (!sessions.isEmpty()) {
            for (Session s : List.copyOf(sessions.values())) {
                try {
                    tickSession(s);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Talk session error (" + s.npcId + "): " + ex.getMessage());
                    destroy(s, false);
                }
            }
        }
        if (!barks.isEmpty() && tick % 5 == 0) {
            Iterator<Map.Entry<String, Bark>> it = barks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Bark> e = it.next();
                Bark b = e.getValue();
                if (tick >= b.until || b.panel == null || !b.panel.valid() || b.viewers.isEmpty()) {
                    if (b.panel != null) {
                        b.panel.collapse(null);
                    }
                    popOut(b.tail);
                    it.remove();
                    continue;
                }
                Location at = NpcPresence.locate(e.getKey());
                Location now = b.panel.location();
                if (at != null && now != null && at.getWorld() == now.getWorld()) {
                    Location target = barkPos(b.plate, at);
                    if (now.distanceSquared(target) > 0.01) {
                        b.panel.teleport(target);
                        teleportSmooth(b.tail, tailPos(target, TalkChrome.BARK_SCALE));
                    }
                }
            }
        }
    }

    private void tickSession(Session s) {
        Player player = Bukkit.getPlayer(s.playerId);
        if (player == null || !player.isOnline()) {
            destroy(s, false);
            return;
        }
        if (tick - s.lastRefresh >= 5) {
            s.lastRefresh = tick;
            Location at = NpcPresence.locate(s.npcId);
            if (!near(player, at, maxDistance)) {
                if (s.questOffer && s.onTimeout != null && !s.chips.isEmpty()) {
                    s.onTimeout.run();
                }
                destroy(s, true);
                return;
            }
            Vector facing = horizontal(player.getLocation().toVector().subtract(at.toVector()));
            boolean moved = s.npcAt == null || s.npcAt.distanceSquared(at) > 0.0025;
            boolean orbited = s.facing.dot(facing) < ORBIT_DOT;
            if (moved || orbited) {
                // NPC shifted or the listener walked around: the whole composition follows,
                // so bubble, replies and card always face the one who's talking.
                s.npcAt = at.clone();
                if (orbited) {
                    s.facing = facing;
                }
                layout(s);
            }
        }
        // Typewriter
        if (s.shown < s.total) {
            s.acc += s.cps;
            int before = s.shown;
            while (s.acc >= 1.0 && s.shown < s.total) {
                s.shown++;
                s.acc -= 1.0;
            }
            if (s.shown != before) {
                if (voiceBlips && tick - s.lastBlip >= 2) {
                    for (int i = before; i < s.shown; i++) {
                        if (Character.isLetterOrDigit(TalkText.visibleCharAt(s.current, i))) {
                            NpcPresence.blip(player, s.npcId, 0.13f);
                            s.lastBlip = tick;
                            break;
                        }
                    }
                }
                if (s.shown >= s.total) {
                    s.typedAt = tick;
                }
                renderBubble(s, false);
            }
        } else if (s.typedAt < 0) {
            s.typedAt = tick;
            renderBubble(s, false);
        }
        // Chips: hover + timeout
        if (!s.chips.isEmpty()) {
            if (tick % 2 == 0) {
                updateHover(player, s);
            }
            if (tick - s.chipsAt > (s.timeoutTicks > 0 ? s.timeoutTicks : chipTimeoutTicks)) {
                if (s.questOffer && s.onTimeout != null) {
                    s.onTimeout.run();
                }
                destroy(s, true);
            }
            return;
        }
        // No chips: fade after the line has been on screen long enough
        if (s.typedAt >= 0 && tick - s.typedAt > lingerTicks && tick - s.lastLineAt > lingerTicks) {
            destroy(s, true);
        }
    }

    /** Put every piece of the conversation where it belongs for the current NPC spot / facing. */
    private void layout(Session s) {
        if (s.npcAt == null) {
            return;
        }
        Location pos = bubblePos(s, s.npcAt);
        if (s.bubble != null) {
            s.bubble.teleport(pos);
        }
        teleportSmooth(s.tail, tailPos(pos, bubbleScale));
        for (ChipView chip : s.chips) {
            Location home = chipHome(s, s.npcAt, chip.index);
            if (chip.panel != null) {
                chip.panel.teleport(home);
            }
            teleportSmooth(chip.hitbox, home.clone().add(0, -0.025, 0));
        }
        if (s.card != null && s.card.valid()) {
            s.card.teleport(cardHome(s, s.npcAt));
        }
    }

    private void updateHover(Player player, Session s) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector dir = eye.getDirection();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < s.chips.size(); i++) {
            ChipView chip = s.chips.get(i);
            if (chip.hitbox == null || !chip.hitbox.isValid()) {
                continue;
            }
            BoundingBox box = chip.hitbox.getBoundingBox().clone().expand(0.04, 0.005, 0.04);
            RayTraceResult hit = box.rayTrace(origin, dir, 8.0);
            if (hit != null) {
                double d = hit.getHitPosition().distanceSquared(origin);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
        }
        if (best >= 0 && best != s.hover) {
            s.hoverByScroll = false;
            setHover(player, s, best);
        } else if (best < 0 && s.hover >= 0 && !s.hoverByScroll) {
            // Looked away: no silent "armed" reply waiting for a stray click.
            int old = s.hover;
            s.hover = -1;
            ChipView chip = s.chips.get(old);
            chip.armed = false;
            focusChip(player, s, chip, false);
        }
    }

    private void setHover(Player player, Session s, int index) {
        int old = s.hover;
        s.hover = index;
        for (int i = 0; i < s.chips.size(); i++) {
            ChipView chip = s.chips.get(i);
            boolean on = i == index;
            if (!on) {
                chip.armed = false;
            }
            if (i == index || i == old) {
                focusChip(player, s, chip, on);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.MASTER, 0.22f, 1.9f);
    }

    private void pick(Player player, Session s, int index) {
        if (index < 0 || index >= s.chips.size()) {
            return;
        }
        if (tick - s.lastPick < 6) {
            return;
        }
        s.lastPick = tick;
        ChipView chip = s.chips.get(index);
        if (chip.choice.confirm() && !chip.armed) {
            chip.armed = true;
            if (s.hover != index) {
                setHover(player, s, index);
            } else {
                focusChip(player, s, chip, true);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.5f, 0.9f);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.35f, 1.25f);
        if (chip.choice.echo() != null) {
            player.sendMessage(Component.text("  » ", s.accent)
                    .append(Component.text(ui(player, "you", "You: "), NamedTextColor.GRAY))
                    .append(Component.text(chip.choice.echo(), NamedTextColor.WHITE, TextDecoration.ITALIC)));
        }
        clearChips(s, true);
        collapseCard(s);
        s.questOffer = false;
        s.onTimeout = null;
        s.lastLineAt = tick;
        s.typedAt = tick;
        // They answered, so they've read it: the reaction doesn't sit out the read gap.
        de.aetherion.quests.dialog.DialogPace.release(player);
        Runnable action = chip.choice.action();
        if (action != null) {
            try {
                action.run();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Talk reply failed: " + ex.getMessage());
            }
        }
    }

    /* =====================================================================
     * Input
     * ===================================================================== */

    private Session sessionFor(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    private int chipIndexOf(Session s, Entity entity) {
        if (s == null || entity == null) {
            return -1;
        }
        for (int i = 0; i < s.chips.size(); i++) {
            ChipView chip = s.chips.get(i);
            if (chip.hitbox != null && chip.hitbox.getUniqueId().equals(entity.getUniqueId())) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChipRightClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) {
            return;
        }
        Session s = sessionFor(event.getPlayer());
        int index = chipIndexOf(s, event.getRightClicked());
        if (index < 0) {
            if (event.getRightClicked().getScoreboardTags().contains(TAG)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND && !(event instanceof PlayerInteractAtEntityEvent)) {
            pick(event.getPlayer(), s, index);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChipRightClickAt(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) {
            return;
        }
        Session s = sessionFor(event.getPlayer());
        int index = chipIndexOf(s, event.getRightClicked());
        if (index >= 0 || event.getRightClicked().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
        if (index >= 0 && event.getHand() == EquipmentSlot.HAND) {
            pick(event.getPlayer(), s, index);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChipLeftClick(PrePlayerAttackEntityEvent event) {
        Session s = sessionFor(event.getPlayer());
        int index = chipIndexOf(s, event.getAttacked());
        if (index >= 0 || event.getAttacked().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
        if (index >= 0) {
            pick(event.getPlayer(), s, index);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Session s = sessionFor(event.getPlayer());
        if (s == null || s.chips.isEmpty() || s.hover < 0) {
            return;
        }
        pick(event.getPlayer(), s, s.hover);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Session s = sessionFor(event.getPlayer());
        if (s == null || s.chips.isEmpty() || s.hover < 0) {
            return;
        }
        event.setCancelled(true);
        pick(event.getPlayer(), s, s.hover);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        Session s = sessionFor(event.getPlayer());
        if (s == null || s.chips.isEmpty() || !s.questOffer) {
            return;
        }
        event.setCancelled(true);
        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        int n = s.chips.size();
        int target;
        if (next == (prev + 1) % 9) {
            target = s.hover < 0 ? 0 : (s.hover + 1) % n;
        } else if (next == (prev + 8) % 9) {
            target = s.hover < 0 ? n - 1 : (s.hover + n - 1) % n;
        } else {
            target = Math.min(next, n - 1);
        }
        s.hoverByScroll = true;
        setHover(event.getPlayer(), s, target);
        event.getPlayer().sendActionBar(Component.text(ui(event.getPlayer(), "scroll", "Click to pick · scroll to change"), TalkChrome.HINT));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null || !msg.toLowerCase(Locale.ROOT).startsWith(CMD + " ")) {
            return;
        }
        event.setCancelled(true);
        String[] parts = msg.substring(1).trim().split("\\s+");
        if (parts.length < 4 || !"pick".equalsIgnoreCase(parts[1])) {
            return;
        }
        Player player = event.getPlayer();
        Session s = sessionFor(player);
        int token;
        int index;
        try {
            token = Integer.parseInt(parts[2]);
            index = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return;
        }
        if (s == null || s.token != token || s.chips.isEmpty()) {
            player.sendMessage(Component.text(ui(player, "moved_on", "That conversation moved on. Right-click them again."), NamedTextColor.GRAY));
            return;
        }
        pick(player, s, index);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            destroy(s, false);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        end(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) > 64.0) {
            Session s = sessions.get(event.getPlayer().getUniqueId());
            if (s != null) {
                destroy(s, false);
            }
        }
    }

    /* =====================================================================
     * Layout (see TalkChrome for the rhythm)
     * ===================================================================== */

    private Location bubblePos(Session s, Location npcFeet) {
        // Right above the living nameplate, which then reads as the speaker label under the tail.
        double y = s.plate ? TalkChrome.VOICE_Y : TalkChrome.VOICE_Y_FREE;
        return npcFeet.clone().add(0, y, 0).add(s.facing.clone().multiply(LEAN));
    }

    private static Location barkPos(boolean plate, Location npcFeet) {
        return npcFeet.clone().add(0, plate ? TalkChrome.VOICE_Y : 3.02, 0);
    }

    /** The tail hangs from the bubble's bottom edge (tail glyph box ≈ one line at 0.9× scale). */
    private static Location tailPos(Location bubble, float scale) {
        return bubble.clone().add(0, -0.125 * (scale / 0.56), 0);
    }

    private static Location chipHome(Session s, Location npcAt, int index) {
        double half = s.chipWidthPx * TalkChrome.PX * TalkChrome.CHIP_SCALE / 2.0;
        double y = TalkChrome.SIDE_TOP - TalkChrome.LINE_PX * TalkChrome.PX * TalkChrome.CHIP_SCALE - index * TalkChrome.CHIP_STEP;
        return npcAt.clone()
                .add(s.facing.clone().multiply(TalkChrome.SIDE_FORWARD))
                .add(right(s.facing).multiply(TalkChrome.SIDE_GAP + half))
                .add(0, y, 0);
    }

    private static Location cardHome(Session s, Location npcAt) {
        double half = s.cardWidthPx * TalkChrome.PX * TalkChrome.CARD_SCALE / 2.0;
        double height = s.cardLines * TalkChrome.LINE_PX * TalkChrome.PX * TalkChrome.CARD_SCALE;
        return npcAt.clone()
                .add(s.facing.clone().multiply(TalkChrome.SIDE_FORWARD))
                .add(right(s.facing).multiply(-(TalkChrome.SIDE_GAP + half)))
                .add(0, TalkChrome.SIDE_TOP - height, 0);
    }

    /** The listener's right-hand side, seen while facing the NPC. */
    private static Vector right(Vector toward) {
        return new Vector(toward.getZ(), 0, -toward.getX());
    }

    private static Vector horizontal(Vector v) {
        Vector h = new Vector(v.getX(), 0, v.getZ());
        if (h.lengthSquared() < 1.0E-4) {
            return new Vector(0, 0, 1);
        }
        return h.normalize();
    }

    private static boolean near(Player player, Location at, double max) {
        return player != null && at != null && at.getWorld() != null
                && at.getWorld().equals(player.getWorld())
                && at.distanceSquared(player.getLocation()) <= max * max;
    }

    /* =====================================================================
     * Entity helpers
     * ===================================================================== */

    private static void stylePanel(TextDisplay d, TextDisplay.TextAlignment alignment, int alpha) {
        TalkChrome.base(d);
        d.setAlignment(alignment);
        d.setLineWidth(400);
        d.setBackgroundColor(TalkChrome.ink(alpha));
    }

    private TextDisplay spawnTail(Location at, TextColor accent) {
        return spawnText(at, d -> {
            TalkChrome.base(d);
            d.setShadowed(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setLineWidth(40);
            d.text(TalkGlyphs.tail(TalkChrome.inkText(accent)));
            d.setTransformation(TalkChrome.scaled(0.01f));
        });
    }

    private TextDisplay spawnText(Location at, java.util.function.Consumer<TextDisplay> setup) {
        return spawn(at, TextDisplay.class, setup);
    }

    private <T extends Entity> T spawn(Location at, Class<T> type, java.util.function.Consumer<T> setup) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            return world.spawn(at, type, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.addScoreboardTag(TAG);
                e.setSilent(true);
                if (e instanceof Display display) {
                    display.setTeleportDuration(3);
                }
                setup.accept(e);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Talk UI spawn failed: " + ex.getMessage());
            return null;
        }
    }

    private void popIn(Display display, float target, long delay) {
        if (display == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> TalkChrome.animate(display, TalkChrome.scaled(target), 4), Math.max(1L, delay));
    }

    private void popOut(Display display) {
        if (display == null || !display.isValid()) {
            return;
        }
        TalkChrome.animate(display, TalkChrome.scaled(0.01f), 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> kill(display), 4L);
    }

    private static void teleportSmooth(Entity entity, Location to) {
        if (entity != null && entity.isValid() && to != null && to.getWorld() == entity.getWorld()) {
            entity.teleport(to);
        }
    }

    private static void kill(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /** Remove UI entities left in loaded chunks by a crash / hot reload. */
    private void purgeLeftovers() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClasses(TextDisplay.class, Interaction.class)) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Talk UI: removed " + removed + " leftover display(s).");
        }
    }
}
