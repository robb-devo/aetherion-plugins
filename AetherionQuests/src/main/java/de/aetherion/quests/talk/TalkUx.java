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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
 * This layer mirrors the existing chat pipeline — {@link de.aetherion.quests.dialog.DialogManager}
 * still owns pacing, gates and quest state. When the in-world path can't run (FancyNpcs
 * missing, NPC not found, player too far, player picked "classic"), callers fall back to
 * the chest {@link de.aetherion.quests.ui.QuestAcceptGUI}.
 */
public final class TalkUx implements Listener {

    public static final String TAG = "ae_talk_ui";
    private static final String CMD = "/aetalk";

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
        TextDisplay display;
        Interaction hitbox;
        boolean armed;
    }

    private static final class Session {
        final UUID playerId;
        final String npcId;
        final int token;
        String npcName;
        String nameCode;
        TextDisplay bubble;
        TextDisplay tail;
        TextDisplay card;
        String current = "";
        String previous;
        int shown;
        int total;
        double cps;
        double acc;
        long typedAt = -1;
        long lastLineAt;
        final List<ChipView> chips = new ArrayList<>();
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
        TextDisplay display;
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
        maxDistance = Math.max(4.0, cfg.getDouble("talk-ux.max-distance", 10.0));
        chipTimeoutTicks = Math.max(200, cfg.getInt("talk-ux.reply-timeout-seconds", 40) * 20);
        lingerTicks = Math.max(20, cfg.getInt("talk-ux.linger-ticks", 70));
        bubbleScale = (float) Math.max(0.3, Math.min(1.2, cfg.getDouble("talk-ux.bubble-scale", 0.56)));
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
            kill(b.display);
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
        player.sendActionBar(Component.text("Pick a reply — look at it and click · or scroll + click", NamedTextColor.GOLD));
        for (ChipView chip : s.chips) {
            popScale(chip.display, 1.12f, 2);
            Bukkit.getScheduler().runTaskLater(plugin, () -> popScale(chip.display, 1.0f, 3), 3L);
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
        if (!LivingNpcService.isLiving(id)) {
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
        clearChips(s);
        String filled = TalkText.fill(player, legacyLine);
        if (s.current != null && !s.current.isBlank() && s.typedAt >= 0) {
            s.previous = s.current;
        }
        s.current = TalkText.wrap("§f" + filled, 168);
        s.total = TalkText.visibleLength(s.current);
        s.shown = 0;
        s.acc = 0;
        // Finish typing within ~22 ticks so there's reading time before the next paced line.
        s.cps = Math.max(1.4, s.total / 22.0);
        s.typedAt = -1;
        s.lastLineAt = tick;
        renderBubble(player, s);
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
            choices.add(new Choice("Got it.", NamedTextColor.GRAY, decline, false, "Got it.", declineCmd));
            choices.add(new Choice("Show me the numbers", NamedTextColor.DARK_GRAY, details, false, null, detailsCmd));
        } else if (conflicting != null) {
            String current = de.aetherion.quests.lang.LangPack.questTitle(player, conflicting.getId(), conflicting.getTitle());
            // Two-step in-world; the chat path has its own CONFIRM ABORT step.
            choices.add(new Choice("Drop \"" + current + "\" & take this", NamedTextColor.GOLD, accept, true,
                    "Fine. I'll drop the other job.", acceptCmd));
            choices.add(new Choice("Keep my current job", NamedTextColor.RED, decline, false,
                    "I'll finish what I started.", declineCmd));
            choices.add(new Choice("Details…", NamedTextColor.GRAY, details, false, null, detailsCmd));
        } else {
            choices.add(new Choice(CastBook.acceptLabel(id, quest.getId()), NamedTextColor.GREEN, accept, false,
                    CastBook.acceptLabel(id, quest.getId()), acceptCmd));
            choices.add(new Choice(CastBook.declineLabel(id), NamedTextColor.RED, decline, false,
                    CastBook.declineLabel(id), declineCmd));
            choices.add(new Choice("Details…", NamedTextColor.GRAY, details, false, null, detailsCmd));
        }
        s.questOffer = true;
        s.onTimeout = gateFail != null ? null : () -> player.sendMessage(Component.text("  » ", NamedTextColor.DARK_GRAY)
                .append(Component.text(npc.getName() + "'s offer stands. ", NamedTextColor.GRAY))
                .append(chatButton("[Accept]", NamedTextColor.GREEN, "/aetherionquest accept " + quest.getId(),
                        "Take " + title))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(chatButton("[Decline]", NamedTextColor.RED, "/aetherionquest decline " + quest.getId(),
                        "Maybe another time")));
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
        if (!LivingNpcService.isLiving(id)) {
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
            clearChips(s);
            if (s.card != null) {
                popOut(s.card);
                s.card = null;
            }
        }
    }

    public void end(Player player) {
        Session s = player == null ? null : sessions.get(player.getUniqueId());
        if (s != null) {
            destroy(s, true);
        }
    }

    /**
     * Ambient one-liner above an NPC (greetings, banter). Floats above the nametag and is
     * visible only to {@code viewers}. One bark per NPC at a time.
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
            kill(old.display);
        }
        LivingNpcProfile profile = LivingNpcProfile.of(id);
        String code = profile != null ? profile.chatPrefix() : "§6";
        String name = displayName == null ? id : displayName;
        String text = code + "§l" + name + "\n" + TalkText.wrap("§f" + TalkText.fill(audience.get(0), legacyLine), 150);
        if (audience.size() > 1) {
            text = code + "§l" + name + "\n" + TalkText.wrap("§f" + legacyLine.replace("{player}", "you"), 150);
        }
        Location pos = at.clone().add(0.0, 3.02, 0.0);
        final String body = text;
        TextDisplay display = spawnText(pos, d -> {
            styleBubble(d, 0.5f);
            d.text(LegacyComponentSerializer.legacySection().deserialize(body));
        });
        if (display == null) {
            return;
        }
        Bark bark = new Bark();
        bark.display = display;
        bark.until = tick + Math.max(40, ticks);
        for (Player p : audience) {
            p.showEntity(plugin, display);
            bark.viewers.add(p.getUniqueId());
            NpcPresence.blip(p, id, 0.16f);
        }
        popIn(display, 0.5f);
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
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        s.nameCode = profile != null ? profile.chatPrefix() : "§6";
        s.npcAt = npcAt.clone();
        Location pos = bubblePos(npcAt, player);
        s.bubble = spawnText(pos, d -> styleBubble(d, bubbleScale));
        if (s.bubble == null) {
            return null;
        }
        s.tail = spawnText(pos.clone().add(0, -0.125 * (bubbleScale / 0.56), 0), d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setLineWidth(40);
            d.setBrightness(new Display.Brightness(15, 15));
            d.text(TalkGlyphs.tail());
            d.setTransformation(scaleOf(0.01f));
        });
        player.showEntity(plugin, s.bubble);
        if (s.tail != null) {
            player.showEntity(plugin, s.tail);
            popIn(s.tail, bubbleScale * 0.9f);
        }
        popIn(s.bubble, bubbleScale);
        sessions.put(player.getUniqueId(), s);
        if (plugin.getMarkerManager() != null) {
            plugin.getMarkerManager().setTalking(player, npcId);
        }
        NpcMemory memory = NpcMemory.get();
        if (memory != null) {
            memory.noteTalk(player.getUniqueId(), npcId);
        }
        return s;
    }

    private void destroy(Session s, boolean animate) {
        sessions.remove(s.playerId);
        clearChips(s);
        if (animate) {
            popOut(s.bubble);
            popOut(s.tail);
            popOut(s.card);
        } else {
            kill(s.bubble);
            kill(s.tail);
            kill(s.card);
        }
        Player player = Bukkit.getPlayer(s.playerId);
        if (player != null && plugin.getMarkerManager() != null) {
            plugin.getMarkerManager().clearTalking(player);
        }
    }

    /* =====================================================================
     * Rendering
     * ===================================================================== */

    private void renderBubble(Player player, Session s) {
        if (s.bubble == null || !s.bubble.isValid()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(s.nameCode).append("§l").append(s.npcName);
        if (previousLine && s.previous != null && !s.previous.isBlank()) {
            text.append("\n§8").append(TalkText.strip(s.previous).replace("\n", "\n§8"));
        }
        text.append("\n").append(TalkText.truncate(s.current, s.shown));
        Component body = LegacyComponentSerializer.legacySection().deserialize(text.toString());
        if (s.shown < s.total) {
            // Untyped remainder in the bubble's own colour: the box keeps its final size
            // from the first frame, so nothing jumps while the line types out.
            body = body.append(Component.text(TalkText.rest(s.current, s.shown), TextColor.color(17, 17, 25)));
        }
        s.bubble.text(body);
    }

    private void showChips(Player player, Session s, List<Choice> choices) {
        clearChips(s);
        Location npcAt = s.npcAt != null ? s.npcAt : NpcPresence.locate(s.npcId);
        if (npcAt == null) {
            return;
        }
        Vector toward = horizontal(player.getLocation().toVector().subtract(npcAt.toVector()));
        Vector right = new Vector(-toward.getZ(), 0, toward.getX()).multiply(-1); // viewer's right
        int n = Math.min(choices.size(), 5);
        double top = 1.62 + (n > 3 ? 0.14 : 0.0);
        for (int i = 0; i < n; i++) {
            Choice choice = choices.get(i);
            ChipView chip = new ChipView();
            chip.choice = choice;
            Location pos = npcAt.clone()
                    .add(toward.clone().multiply(0.95))
                    .add(right.clone().multiply(1.05))
                    .add(0, top - i * 0.27, 0);
            final int index = i;
            chip.display = spawnText(pos, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setLineWidth(400);
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setDefaultBackground(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(scaleOf(0.01f));
                d.text(chipText(choice, index, false, false));
                d.setBackgroundColor(chipBg(false));
            });
            if (chip.display == null) {
                continue;
            }
            int px = pixelWidth("[" + (i + 1) + "] " + choice.label()) + 12;
            float width = (float) Math.max(0.6, px * 0.025 * 0.5);
            chip.hitbox = spawn(pos.clone().add(0, -0.02, 0), Interaction.class, it -> {
                it.setInteractionWidth(width);
                it.setInteractionHeight(0.22f);
                it.setResponsive(false);
            });
            player.showEntity(plugin, chip.display);
            if (chip.hitbox != null) {
                player.showEntity(plugin, chip.hitbox);
            }
            final TextDisplay disp = chip.display;
            Bukkit.getScheduler().runTaskLater(plugin, () -> popIn(disp, 0.5f), 1L + i * 2L);
            s.chips.add(chip);
        }
        s.hover = -1;
        s.chipsAt = tick;
        if (chatReplies) {
            sendChatReplies(player, s);
        }
        if (s.questOffer) {
            player.sendActionBar(TalkGlyphs.leftClick()
                    .append(Component.text("Look at a reply + click  ·  ", NamedTextColor.GOLD))
                    .append(TalkGlyphs.scroll())
                    .append(Component.text("or scroll + click", NamedTextColor.GOLD)));
        } else {
            player.sendActionBar(TalkGlyphs.leftClick()
                    .append(Component.text("Look at a reply + click — or just walk on", NamedTextColor.GRAY)));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.25f, 1.6f);
    }

    private void sendChatReplies(Player player, Session s) {
        Component line = Component.text("  » ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < s.chips.size(); i++) {
            ChipView chip = s.chips.get(i);
            String command = chip.choice.chatCommand() != null
                    ? chip.choice.chatCommand()
                    : CMD + " pick " + s.token + " " + i;
            if (i > 0) {
                line = line.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(chatButton("[" + chip.choice.label() + "]", chip.choice.color(), command,
                    chip.choice.label()));
        }
        player.sendMessage(line);
    }

    private static Component chatButton(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private Component chipText(Choice choice, int index, boolean hovered, boolean armed) {
        String label = armed ? "Sure? Click again" : choice.label();
        if (hovered) {
            return TalkGlyphs.chevron()
                    .append(Component.text(label, choice.color(), TextDecoration.BOLD))
                    .append(Component.text(" ◀", NamedTextColor.YELLOW));
        }
        return Component.text("[" + (index + 1) + "] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(label, choice.color()));
    }

    private static Color chipBg(boolean hovered) {
        return hovered ? Color.fromARGB(225, 58, 52, 34) : Color.fromARGB(190, 14, 14, 20);
    }

    private void showCard(Player player, Session s, Quest quest, Quest conflicting) {
        if (s.card != null) {
            kill(s.card);
            s.card = null;
        }
        Location npcAt = s.npcAt != null ? s.npcAt : NpcPresence.locate(s.npcId);
        if (npcAt == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("§6§l").append(de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle()));
        String desc = de.aetherion.quests.lang.LangPack.questDescription(player, quest.getId(),
                quest.getDescription() == null ? "" : quest.getDescription());
        if (desc != null && !desc.isBlank()) {
            text.append("\n§7").append(TalkText.wrap(TalkText.strip(desc), 150).replace("\n", "\n§7"));
        }
        if (!quest.getObjectives().isEmpty()) {
            text.append("\n§e▸ Goals");
            for (Objective objective : quest.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                String name = objective.getDisplayName() != null ? objective.getDisplayName() : pretty(objective.getTarget());
                text.append("\n§f• ").append(TalkText.strip(name)).append(" §8x").append(Math.max(1, objective.getAmount()));
            }
        }
        if (!quest.getRewards().isEmpty()) {
            text.append("\n§a▸ Pays");
            for (Reward reward : quest.getRewards()) {
                if (reward == null) {
                    continue;
                }
                text.append("\n§f• ").append(reward.getAmount()).append(" ").append(reward.getName());
            }
        }
        if (conflicting != null) {
            text.append("\n§c⚠ Replaces your current job");
        }
        Vector toward = horizontal(player.getLocation().toVector().subtract(npcAt.toVector()));
        Vector right = new Vector(-toward.getZ(), 0, toward.getX()).multiply(-1);
        Location pos = npcAt.clone().add(toward.clone().multiply(0.9)).add(right.clone().multiply(-1.15)).add(0, 0.95, 0);
        String body = text.toString();
        s.card = spawnText(pos, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setLineWidth(400);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(205, 32, 24, 14));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(scaleOf(0.01f));
            d.text(LegacyComponentSerializer.legacySection().deserialize(body));
        });
        if (s.card != null) {
            player.showEntity(plugin, s.card);
            popIn(s.card, 0.42f);
        }
    }

    private static String pretty(String raw) {
        if (raw == null) {
            return "?";
        }
        String t = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private void clearChips(Session s) {
        for (ChipView chip : s.chips) {
            popOut(chip.display);
            kill(chip.hitbox);
        }
        s.chips.clear();
        s.hover = -1;
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
                if (tick >= b.until || b.display == null || !b.display.isValid()) {
                    popOut(b.display);
                    it.remove();
                    continue;
                }
                Location at = NpcPresence.locate(e.getKey());
                if (at != null && at.getWorld() == b.display.getWorld()) {
                    Location target = at.clone().add(0, 3.02, 0);
                    if (b.display.getLocation().distanceSquared(target) > 0.01) {
                        b.display.teleport(target);
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
            if (s.npcAt == null || s.npcAt.distanceSquared(at) > 0.0025) {
                s.npcAt = at.clone();
                Location pos = bubblePos(at, player);
                teleportSmooth(s.bubble, pos);
                teleportSmooth(s.tail, pos.clone().add(0, -0.125 * (bubbleScale / 0.56), 0));
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
                renderBubble(player, s);
            }
        } else if (s.typedAt < 0) {
            s.typedAt = tick;
            renderBubble(player, s);
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
            BoundingBox box = chip.hitbox.getBoundingBox().clone().expand(0.04, 0.03, 0.04);
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
            if (chip.display != null && chip.display.isValid()) {
                chip.display.text(chipText(chip.choice, old, false, false));
                chip.display.setBackgroundColor(chipBg(false));
                popScale(chip.display, 0.5f, 2);
            }
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
                if (chip.display != null && chip.display.isValid()) {
                    chip.display.text(chipText(chip.choice, i, on, chip.armed));
                    chip.display.setBackgroundColor(chipBg(on));
                    popScale(chip.display, on ? 0.56f : 0.5f, 2);
                }
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
            } else if (chip.display != null) {
                chip.display.text(chipText(chip.choice, index, true, true));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.5f, 0.9f);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.35f, 1.25f);
        if (chip.choice.echo() != null) {
            player.sendMessage(Component.text("  » ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("You: ", NamedTextColor.GRAY))
                    .append(Component.text(chip.choice.echo(), NamedTextColor.WHITE, TextDecoration.ITALIC)));
        }
        clearChips(s);
        if (s.card != null) {
            popOut(s.card);
            s.card = null;
        }
        s.questOffer = false;
        s.onTimeout = null;
        s.lastLineAt = tick;
        s.typedAt = tick;
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
        event.getPlayer().sendActionBar(Component.text("Click to pick · scroll to change", NamedTextColor.GOLD));
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
            player.sendMessage(Component.text("That conversation moved on. Right-click them again.", NamedTextColor.GRAY));
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
     * Entity helpers
     * ===================================================================== */

    private Location bubblePos(Location npcFeet, Player player) {
        Vector toward = horizontal(player.getLocation().toVector().subtract(npcFeet.toVector()));
        return npcFeet.clone().add(0, 2.08, 0).add(toward.multiply(0.12));
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

    private void styleBubble(TextDisplay d, float scale) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setLineWidth(400);
        d.setShadowed(true);
        d.setSeeThrough(false);
        d.setDefaultBackground(false);
        d.setBackgroundColor(Color.fromARGB(214, 16, 16, 24));
        d.setBrightness(new Display.Brightness(15, 15));
        d.setTransformation(scaleOf(0.01f));
        d.setViewRange(0.5f);
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

    private static Transformation scaleOf(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    private void popIn(Display display, float target) {
        if (display == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> popScale(display, target, 4), 2L);
    }

    private static void popScale(Display display, float target, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(scaleOf(target));
    }

    private void popOut(Display display) {
        if (display == null || !display.isValid()) {
            return;
        }
        popScale(display, 0.01f, 3);
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

    private static int pixelWidth(String text) {
        int w = 0;
        String plain = TalkText.strip(text);
        for (int i = 0; i < plain.length(); i++) {
            w += TalkText.advance(plain.charAt(i));
        }
        return w;
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
