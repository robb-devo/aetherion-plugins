package de.aetherion.quests.ui;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.talk.TalkChrome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Overhead UI for quest NPCs: one shared nameplate per NPC, one quest mark per player.
 * <p>
 * Look comes from {@link TalkChrome} (same family as the talk bubble): calm badge marks —
 * gold "!" (job on offer), green "?" (hand it in), muted "?" (in progress) — that breathe
 * instead of blinking. The slot above the plate belongs to the NPC's voice: while a player
 * talks with the NPC, or the NPC barks at them, their mark lifts away and later drops back in.
 */
public class QuestMarkerManager implements Listener {


    private enum MarkerKind {
        HIDDEN,
        AVAILABLE,
        READY,
        PENDING
    }


    private static final float VIEW_RANGE = 28.0f / 64.0f;
    private static final double MAX_VIEW_DISTANCE = 28.0;
    private static final double MAX_VIEW_DISTANCE_SQUARED = MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE;
    /** Marks over non-living (editor) NPCs — their plate is one line at 2.15. */
    private static final double EDITOR_MARK_Y = 2.65;
    private static final double EDITOR_PLATE_Y = 2.15;


    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final NamespacedKey markerKey;
    private final NamespacedKey nameKey;

    private final Map<UUID, Map<String, TextDisplay>> markers = new HashMap<>();
    private final Map<UUID, Map<String, MarkerKind>> markerKinds = new HashMap<>();
    private final Map<String, TextDisplay> nameDisplays = new HashMap<>();
    /** Player → NPC they're talking to: their mark steps aside; the nameplate stays as the speaker label. */
    private final Map<UUID, String> talkingWith = new HashMap<>();
    /** Player → NPC → server tick until which the mark steps aside for an ambient line. */
    private final Map<UUID, Map<String, Integer>> quietUntil = new HashMap<>();
    /** Marks currently tucked away per player — they drop back in instead of snapping. */
    private final Map<UUID, Set<String>> tucked = new HashMap<>();

    private int pulse;
    private BukkitTask task;


    public QuestMarkerManager(AetherionQuests plugin, QuestManager questManager) {

        this.plugin = plugin;
        this.questManager = questManager;
        this.markerKey = new NamespacedKey(plugin, "quest_marker_npc");
        this.nameKey = new NamespacedKey(plugin, "quest_npc_name");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        cleanupWorldMarkers();

        this.task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                10L,
                10L
        );

    }


    public String getNpcId(Entity entity) {

        if (entity == null) {
            return null;
        }

        String markerNpcId = entity.getPersistentDataContainer().get(
                markerKey,
                PersistentDataType.STRING
        );

        if (markerNpcId != null && !markerNpcId.isBlank()) {
            return markerNpcId;
        }

        return entity.getPersistentDataContainer().get(
                nameKey,
                PersistentDataType.STRING
        );

    }


    public void refresh(Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

        for (QuestNPC npc : QuestNPCRegistry.getAll().values()) {
            updateMarker(player, npc, 1L);
        }

    }


    public void refreshNpc(String npcId) {

        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);

        if (npc == null) {
            return;
        }

        updateNameDisplay(npc);

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateMarker(player, npc, 1L);
        }

    }

    /** This NPC has a live nameplate of ours (the talk bubble can sit right on top of it). */
    public boolean hasNameplate(String npcId) {
        TextDisplay plate = plateOf(npcId);
        return plate != null && plate.isValid() && !plate.isDead();
    }

    /** Talk bubble opened: this NPC's quest mark lifts away for {@code player}. */
    public void setTalking(Player player, String npcId) {
        if (player == null || npcId == null) {
            return;
        }
        String id = key(npcId);
        String previous = talkingWith.put(player.getUniqueId(), id);
        if (previous != null && !previous.equals(id)) {
            showFor(player, previous, 1L);
        }
        tuck(player, id);
    }

    /** Conversation over: the mark drops back in once the bubble has folded away. */
    public void clearTalking(Player player) {
        if (player == null) {
            return;
        }
        String id = talkingWith.remove(player.getUniqueId());
        if (id != null) {
            showFor(player, id, 3L);
        }
    }

    /**
     * The NPC says something ambient to {@code player} from the voice slot: the mark steps
     * aside for {@code ticks}, then comes back on its own.
     */
    public void quiet(Player player, String npcId, int ticks) {
        if (player == null || npcId == null || ticks <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String id = key(npcId);
        quietUntil.computeIfAbsent(uuid, k -> new HashMap<>())
                .merge(id, Bukkit.getCurrentTick() + ticks, Math::max);
        tuck(player, id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isQuiet(uuid, id)) {
                Map<String, Integer> q = quietUntil.get(uuid);
                if (q != null) {
                    q.remove(id);
                    if (q.isEmpty()) {
                        quietUntil.remove(uuid);
                    }
                }
                showFor(player, id, 1L);
            }
        }, ticks + 1L);
    }

    private boolean isQuiet(UUID uuid, String npcId) {
        String id = key(npcId);
        if (id.equals(talkingWith.get(uuid))) {
            return true;
        }
        Map<String, Integer> q = quietUntil.get(uuid);
        Integer until = q == null ? null : q.get(id);
        return until != null && until > Bukkit.getCurrentTick();
    }

    /** Mark lifts and shrinks away, then hides (per player — nobody else's mark moves). */
    private void tuck(Player player, String npcId) {
        UUID uuid = player.getUniqueId();
        String id = key(npcId);
        if (!tucked.computeIfAbsent(uuid, k -> new HashSet<>()).add(id)) {
            return;
        }
        TextDisplay marker = markOf(uuid, id);
        if (marker == null || !marker.isValid() || !player.canSee(marker)) {
            return;
        }
        TalkChrome.animate(marker, TalkChrome.scaled(0.05f, 0.14f), 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (marker.isValid() && player.isOnline() && isQuiet(uuid, id)) {
                player.hideEntity(plugin, marker);
            }
        }, 4L);
    }

    private void showFor(Player player, String npcId, long popDelay) {
        TextDisplay name = plateOf(npcId);
        if (name != null && name.isValid()) {
            player.showEntity(plugin, name);
        }
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null) {
            updateMarker(player, npc, popDelay);
        }
    }

    /** Smooth nametag (and quest mark) follow while a living FancyNPC walks. */
    public void followLivingName(String npcId, Location feet) {
        if (npcId == null || feet == null || feet.getWorld() == null) {
            return;
        }
        TextDisplay display = plateOf(npcId);
        if (display == null || !display.isValid() || display.isDead()) {
            QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
            if (npc != null) {
                updateNameDisplay(npc);
            }
            return;
        }
        Location at = feet.clone().add(0.0, TalkChrome.PLATE_Y, 0.0);
        if (display.getWorld() != at.getWorld()
                || display.getLocation().distanceSquared(at) > 0.0001) {
            display.teleport(at);
        }
        // Marks used to lag a refresh (~2 s) behind a walking NPC — keep them on the plate.
        Location mark = feet.clone().add(0.0, TalkChrome.MARK_Y, 0.0);
        for (Map.Entry<UUID, Map<String, TextDisplay>> entry : markers.entrySet()) {
            TextDisplay marker = markOf(entry.getKey(), npcId);
            if (marker != null && marker.isValid() && marker.getWorld() == mark.getWorld()
                    && marker.getLocation().distanceSquared(mark) > 0.0001) {
                marker.teleport(mark);
            }
        }
    }


    public void refreshAll() {

        for (QuestNPC npc : QuestNPCRegistry.getAll().values()) {
            updateNameDisplay(npc);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }

    }


    public void shutdown() {

        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Map<String, TextDisplay> playerMarkers : markers.values()) {
            for (TextDisplay display : playerMarkers.values()) {
                removeDisplay(display);
            }
        }

        markers.clear();
        markerKinds.clear();
        tucked.clear();
        quietUntil.clear();

        for (TextDisplay display : nameDisplays.values()) {
            removeDisplay(display);
        }

        nameDisplays.clear();
        cleanupWorldMarkers();

    }


    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        UUID uuid = event.getPlayer().getUniqueId();
        talkingWith.remove(uuid);
        quietUntil.remove(uuid);
        tucked.remove(uuid);
        removePlayer(event.getPlayer());

    }


    private void tick() {

        pulse++;
        // Full kind/visibility refresh every ~2s; marks breathe every second.
        if (pulse % 4 == 0) {
            refreshAll();
        }
        if (pulse % 2 == 0) {
            breathe();
        }

    }

    /**
     * Offers and hand-ins drift up and down a hair (interpolated over a full second each way)
     * instead of colour-blinking. Neighbouring NPCs breathe out of phase.
     */
    private void breathe() {
        boolean up = (pulse / 2) % 2 == 0;
        for (Map.Entry<UUID, Map<String, TextDisplay>> entry : markers.entrySet()) {
            Map<String, MarkerKind> kindsForPlayer = markerKinds.get(entry.getKey());
            if (kindsForPlayer == null || kindsForPlayer.isEmpty()) {
                continue;
            }
            Set<String> away = tucked.get(entry.getKey());
            for (Map.Entry<String, TextDisplay> marker : entry.getValue().entrySet()) {
                TextDisplay display = marker.getValue();
                if (display == null || !display.isValid() || display.isDead()) {
                    continue;
                }
                MarkerKind kind = kindsForPlayer.get(marker.getKey());
                if (kind == null || kind == MarkerKind.HIDDEN || !TalkChrome.markBobs(chrome(kind))) {
                    continue;
                }
                if (away != null && away.contains(key(marker.getKey()))) {
                    continue;
                }
                boolean high = up ^ ((marker.getKey().hashCode() & 1) == 1);
                TalkChrome.animate(display,
                        TalkChrome.scaled(TalkChrome.markScale(chrome(kind)), high ? TalkChrome.MARK_BOB : 0f), 20);
            }
        }
    }


    private void removePlayer(Player player) {

        if (player == null) {
            return;
        }

        markerKinds.remove(player.getUniqueId());
        Map<String, TextDisplay> playerMarkers = markers.remove(player.getUniqueId());

        if (playerMarkers == null) {
            return;
        }

        for (TextDisplay display : playerMarkers.values()) {
            removeDisplay(display);
        }

    }


    private void updateMarker(Player player, QuestNPC npc, long popDelay) {

        if (player == null || npc == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        MarkerKind kind = kindFor(player, npc);
        Location location = LivingNpcService.isLiving(npc.getId())
                ? markerLocation(npc, TalkChrome.MARK_Y)
                : markerLocation(npc, EDITOR_MARK_Y);

        Map<String, TextDisplay> playerMarkers = markers.computeIfAbsent(
                uuid,
                k -> new HashMap<>()
        );
        Map<String, MarkerKind> kindsForPlayer = markerKinds.computeIfAbsent(
                uuid,
                k -> new HashMap<>()
        );

        TextDisplay display = playerMarkers.get(npc.getId());

        if (kind == MarkerKind.HIDDEN
                || location == null
                || !isInViewRange(player, location)) {
            if (display != null) {
                removeDisplay(display);
                playerMarkers.remove(npc.getId());
            }
            kindsForPlayer.remove(npc.getId());
            return;
        }

        MarkerKind before = kindsForPlayer.put(npc.getId(), kind);
        boolean quiet = isQuiet(uuid, npc.getId());
        String id = key(npc.getId());

        if (display == null || !display.isValid() || display.isDead()) {
            display = spawn(npc, location, kind);
            playerMarkers.put(npc.getId(), display);
            if (quiet) {
                tucked.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
            } else {
                Set<String> away = tucked.get(uuid);
                if (away != null) {
                    away.remove(id);
                }
                popIn(player, display, kind, 1L);
            }
            return;
        }

        if (display.getWorld() != location.getWorld()
                || display.getLocation().distanceSquared(location) > 0.08) {
            display.teleport(location);
        }

        display.text(markerText(kind));
        applyMarkerStyle(display, kind);

        if (quiet) {
            tucked.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
            player.hideEntity(plugin, display);
            return;
        }

        Set<String> away = tucked.get(uuid);
        if (away != null && away.remove(id)) {
            popIn(player, display, kind, popDelay);
            return;
        }

        if (before != null && before != kind) {
            // Offer → hand-in (or back): one soft bump so the change is noticed, not shouted.
            bump(display, kind);
        }
        player.showEntity(plugin, display);

    }

    /** Start small and a touch high, then drop into place with a little spring. */
    private void popIn(Player player, TextDisplay display, MarkerKind kind, long delay) {
        float scale = TalkChrome.markScale(chrome(kind));
        TalkChrome.animate(display, TalkChrome.scaled(0.05f, 0.12f), 0);
        player.showEntity(plugin, display);
        UUID uuid = player.getUniqueId();
        String id = getNpcId(display);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid() || (id != null && isQuiet(uuid, id))) {
                return;
            }
            TalkChrome.animate(display, TalkChrome.scaled(scale * 1.15f, 0f), 3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (display.isValid() && (id == null || !isQuiet(uuid, id))) {
                    TalkChrome.animate(display, TalkChrome.scaled(scale, 0f), 3);
                }
            }, 3L);
        }, Math.max(1L, delay));
    }

    private void bump(TextDisplay display, MarkerKind kind) {
        float scale = TalkChrome.markScale(chrome(kind));
        float lift = display.getTransformation().getTranslation().y();
        TalkChrome.animate(display, TalkChrome.scaled(scale * 1.3f, lift), 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                TalkChrome.animate(display, TalkChrome.scaled(scale, lift), 4);
            }
        }, 3L);
    }


    private MarkerKind kindFor(Player player, QuestNPC npc) {

        Quest quest = questManager.getQuestForNpc(player, npc);

        if (quest == null) {
            return MarkerKind.HIDDEN;
        }

        QuestState state = questManager.getQuestState(player, quest);

        if (state == QuestState.READY) {
            return MarkerKind.READY;
        }

        if (state == QuestState.AVAILABLE) {
            // Orientation: one "!" at a time — only on the spine's next stop.
            if (!de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)
                    && !npc.getId().equalsIgnoreCase(de.aetherion.quests.util.QuestStoryGate.spineNpc(player, questManager))) {
                return MarkerKind.HIDDEN;
            }
            return MarkerKind.AVAILABLE;
        }

        // In progress: keep NPCs like Tackle marked — just quieter than an open offer.
        if (state == QuestState.ACTIVE) {
            return MarkerKind.PENDING;
        }

        return MarkerKind.HIDDEN;

    }


    private Quest resolveQuest(QuestNPC npc) {

        if (npc.getQuestId() != null && !npc.getQuestId().isBlank()) {
            Quest quest = questManager.getQuest(npc.getQuestId());
            if (quest != null) {
                return quest;
            }
        }

        if (npc.getServiceId() == null || npc.getServiceId().isBlank()) {
            return null;
        }

        for (Quest quest : questManager.getQuests()) {
            if (quest != null && quest.hasService(npc.getServiceId())) {
                return quest;
            }
        }

        return null;

    }


    private Location markerLocation(QuestNPC npc, double yOffset) {

        if (npc == null) {
            return null;
        }

        if (LivingNpcService.isLiving(npc.getId())) {
            AetherionQuests plugin = this.plugin;
            LivingNpcService living = plugin.getLivingNpcService();
            Location livingLoc = living == null ? null : living.locationOf(npc.getId());
            if (livingLoc == null && plugin.getNpcDataStorage() != null) {
                livingLoc = plugin.getNpcDataStorage().getSavedLocation(npc.getId());
            }
            if (livingLoc == null || livingLoc.getWorld() == null) {
                return null;
            }
            return livingLoc.clone().add(0.0, yOffset, 0.0);
        }

        if (npc.getEntityId() == null) {
            return null;
        }

        Entity entity = Bukkit.getEntity(npc.getEntityId());

        if (entity == null || entity.isDead() || !entity.isValid()) {
            return null;
        }

        hideVanillaNametag(entity);

        return entity.getLocation().clone().add(0.0, yOffset, 0.0);

    }


    private boolean isInViewRange(Player player, Location location) {

        if (player.getWorld() == null || location.getWorld() == null) {
            return false;
        }

        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }

        return player.getLocation().distanceSquared(location) <= MAX_VIEW_DISTANCE_SQUARED;

    }


    private void hideVanillaNametag(Entity entity) {

        if (entity instanceof LivingEntity living) {
            living.setCustomNameVisible(false);
        }

    }


    private void updateNameDisplay(QuestNPC npc) {

        Location location = LivingNpcService.isLiving(npc.getId())
                ? markerLocation(npc, TalkChrome.PLATE_Y)
                : markerLocation(npc, EDITOR_PLATE_Y);

        // Always scrub orphan nametags (stacked = opaque black background).
        purgeNameDisplays(npc.getId());
        TextDisplay kept = nameDisplays.get(npc.getId());
        if (kept != null && (!kept.isValid() || kept.isDead())) {
            nameDisplays.remove(npc.getId());
            kept = null;
        }

        if (location == null) {
            if (kept != null) {
                removeDisplay(kept);
                nameDisplays.remove(npc.getId());
            }
            return;
        }

        TextDisplay display = kept;
        if (display == null) {
            display = spawnName(npc, location);
            nameDisplays.put(npc.getId(), display);
            return;
        }

        if (display.getWorld() != location.getWorld()
                || display.getLocation().distanceSquared(location) > 0.08) {
            display.teleport(location);
        }

        display.text(nameComponent(npc));
        applyNameStyle(display);
    }


    /** Spawned hidden — the caller pops it in (or keeps it tucked while the NPC speaks). */
    private TextDisplay spawn(
            QuestNPC npc,
            Location location,
            MarkerKind kind
    ) {

        return location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {

            textDisplay.setPersistent(false);
            textDisplay.setVisibleByDefault(false);
            textDisplay.setGravity(false);
            textDisplay.setInvulnerable(true);
            textDisplay.text(markerText(kind));
            textDisplay.getPersistentDataContainer().set(
                    markerKey,
                    PersistentDataType.STRING,
                    npc.getId()
            );
            applyMarkerStyle(textDisplay, kind);

        });

    }


    private Component markerText(MarkerKind kind) {

        if (kind == MarkerKind.HIDDEN) {
            return Component.empty();
        }

        return TalkChrome.markText(chrome(kind));

    }

    private static TalkChrome.Mark chrome(MarkerKind kind) {
        return switch (kind) {
            case READY -> TalkChrome.Mark.READY;
            case PENDING -> TalkChrome.Mark.PENDING;
            default -> TalkChrome.Mark.AVAILABLE;
        };
    }


    private void purgeNameDisplays(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return;
        }
        TextDisplay kept = nameDisplays.get(npcId);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (!(entity instanceof TextDisplay display)) {
                    continue;
                }
                if (kept != null && display.equals(kept)) {
                    continue;
                }
                String stored = display.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
                if (npcId.equalsIgnoreCase(stored)) {
                    removeDisplay(display);
                }
            }
        }
    }

    private TextDisplay spawnName(QuestNPC npc, Location location) {

        return location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {

            textDisplay.setPersistent(false);
            textDisplay.setVisibleByDefault(true);
            textDisplay.setGravity(false);
            textDisplay.setInvulnerable(true);
            textDisplay.text(nameComponent(npc));
            textDisplay.getPersistentDataContainer().set(
                    nameKey,
                    PersistentDataType.STRING,
                    npc.getId()
            );
            applyNameStyle(textDisplay);

        });

    }

    private static Component nameComponent(QuestNPC npc) {
        LivingNpcProfile profile = LivingNpcProfile.of(npc.getId());
        if (profile != null) {
            return profile.nametag(npc.getName());
        }
        return TalkChrome.nameplate(npc.getName(), TalkChrome.accent(NamedTextColor.AQUA), null);
    }


    private void applyMarkerStyle(TextDisplay display, MarkerKind kind) {
        TalkChrome.styleMark(display, chrome(kind));
        applyHologramStyle(display, TalkChrome.markScale(chrome(kind)));
    }


    private void applyNameStyle(TextDisplay display) {
        TalkChrome.stylePlate(display);
        applyHologramStyle(display, TalkChrome.PLATE_SCALE);
    }


    private void applyHologramStyle(TextDisplay display, float scale) {

        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setViewRange(VIEW_RANGE);

        Transformation transformation = display.getTransformation();
        display.setTransformation(new Transformation(
                transformation.getTranslation(),
                transformation.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                transformation.getRightRotation()
        ));

    }


    private TextDisplay plateOf(String npcId) {
        if (npcId == null) {
            return null;
        }
        TextDisplay direct = nameDisplays.get(npcId);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, TextDisplay> entry : nameDisplays.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(npcId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private TextDisplay markOf(UUID uuid, String npcId) {
        Map<String, TextDisplay> playerMarkers = markers.get(uuid);
        if (playerMarkers == null || npcId == null) {
            return null;
        }
        TextDisplay direct = playerMarkers.get(npcId);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, TextDisplay> entry : playerMarkers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(npcId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String key(String npcId) {
        return npcId == null ? "" : npcId.toLowerCase(Locale.ROOT);
    }


    private void removeDisplay(TextDisplay display) {

        if (display != null && display.isValid()) {
            display.remove();
        }

    }


    private void cleanupWorldMarkers() {

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)
                        || entity.getPersistentDataContainer().has(nameKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }

    }

}
