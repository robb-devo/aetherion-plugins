package de.aetherion.quests.ui;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
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
import java.util.Map;
import java.util.UUID;


public class QuestMarkerManager implements Listener {


    private enum MarkerKind {
        HIDDEN,
        AVAILABLE,
        READY
    }


    private static final float VIEW_RANGE = 28.0f / 64.0f;
    private static final double MAX_VIEW_DISTANCE = 28.0;
    private static final double MAX_VIEW_DISTANCE_SQUARED = MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE;
    private static final int MAX_MARKERS_PER_PLAYER = 12;


    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final NamespacedKey markerKey;
    private final NamespacedKey nameKey;

    private final Map<UUID, Map<String, TextDisplay>> markers = new HashMap<>();
    private final Map<UUID, Map<String, MarkerKind>> markerKinds = new HashMap<>();
    private final Map<String, TextDisplay> nameDisplays = new HashMap<>();

    private boolean blinkOn = true;
    private int blinkPulse;
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
            updateMarker(player, npc);
        }

    }


    public void refreshNpc(String npcId) {

        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);

        if (npc == null) {
            return;
        }

        updateNameDisplay(npc);

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateMarker(player, npc);
        }

    }

    /** Smooth nametag follow while a living FancyNPC is mid-walk. */
    public void followLivingName(String npcId, Location feet) {
        if (npcId == null || feet == null || feet.getWorld() == null) {
            return;
        }
        TextDisplay display = nameDisplays.get(npcId);
        if (display == null || !display.isValid() || display.isDead()) {
            QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
            if (npc != null) {
                updateNameDisplay(npc);
            }
            return;
        }
        Location at = feet.clone().add(0.0, 2.35, 0.0);
        if (display.getWorld() != at.getWorld()
                || display.getLocation().distanceSquared(at) > 0.0001) {
            display.teleport(at);
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

        for (TextDisplay display : nameDisplays.values()) {
            removeDisplay(display);
        }

        nameDisplays.clear();
        cleanupWorldMarkers();

    }


    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        removePlayer(event.getPlayer());

    }


    private void tick() {

        blinkOn = !blinkOn;
        blinkPulse++;
        // Full kind/visibility refresh every ~2s; blink-only the rest.
        if (blinkPulse % 4 == 0) {
            refreshAll();
            return;
        }
        blinkExisting();

    }

    private void blinkExisting() {
        for (Map.Entry<UUID, Map<String, TextDisplay>> entry : markers.entrySet()) {
            Map<String, MarkerKind> kindsForPlayer = markerKinds.get(entry.getKey());
            if (kindsForPlayer == null || kindsForPlayer.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, TextDisplay> marker : entry.getValue().entrySet()) {
                TextDisplay display = marker.getValue();
                if (display == null || !display.isValid() || display.isDead()) {
                    continue;
                }
                MarkerKind kind = kindsForPlayer.get(marker.getKey());
                if (kind == null || kind == MarkerKind.HIDDEN) {
                    continue;
                }
                display.text(markerText(kind, blinkOn));
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


    private void updateMarker(Player player, QuestNPC npc) {

        if (player == null || npc == null) {
            return;
        }

        MarkerKind kind = kindFor(player, npc);
        Location location = LivingNpcService.isLiving(npc.getId())
                ? markerLocation(npc, 2.8)
                : markerLocation(npc, 2.65);

        Map<String, TextDisplay> playerMarkers = markers.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new HashMap<>()
        );
        Map<String, MarkerKind> kindsForPlayer = markerKinds.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new HashMap<>()
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

        kindsForPlayer.put(npc.getId(), kind);

        if (playerMarkers.size() >= MAX_MARKERS_PER_PLAYER && display == null) {
            dropFarthestMarker(player, playerMarkers, kindsForPlayer, location);
        }

        if (display == null || !display.isValid() || display.isDead()) {
            if (display != null) {
                removeDisplay(display);
            }
            display = spawn(player, npc, location, kind);
            playerMarkers.put(npc.getId(), display);
            return;
        }

        if (display.getWorld() != location.getWorld()
                || display.getLocation().distanceSquared(location) > 0.08) {
            display.teleport(location);
        }

        display.text(markerText(kind, blinkOn));
        applyMarkerStyle(display);
        player.showEntity(plugin, display);

    }


    private MarkerKind kindFor(Player player, QuestNPC npc) {

        Quest quest = resolveQuest(npc);

        if (quest == null) {
            return MarkerKind.HIDDEN;
        }

        QuestState state = questManager.getQuestState(player, quest);

        if (state == QuestState.READY) {
            return MarkerKind.READY;
        }

        // Offer + in-progress: keep the hologram so NPCs like Tackle stay marked.
        if (state == QuestState.AVAILABLE || state == QuestState.ACTIVE) {
            return MarkerKind.AVAILABLE;
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


    private Location markerLocation(QuestNPC npc) {
        return markerLocation(npc, 2.65);
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
                ? markerLocation(npc, 2.35)
                : markerLocation(npc, 2.15);

        TextDisplay kept = nameDisplays.get(npc.getId());
        if (kept != null && (!kept.isValid() || kept.isDead())) {
            removeDisplay(kept);
            nameDisplays.remove(npc.getId());
            kept = null;
        }

        if (location == null || !anyPlayerNear(location)) {
            if (kept != null) {
                removeDisplay(kept);
                nameDisplays.remove(npc.getId());
            }
            return;
        }

        cullNameNear(location, npc.getId(), kept);

        TextDisplay display = kept;
        if (display == null) {
            display = findExisting(location, nameKey, npc.getId());
        }
        if (display == null) {
            display = spawnName(npc, location);
            nameDisplays.put(npc.getId(), display);
            return;
        }

        nameDisplays.put(npc.getId(), display);

        if (display.getWorld() != location.getWorld()
                || display.getLocation().distanceSquared(location) > 0.08) {
            display.teleport(location);
        }

        display.text(nameComponent(npc));
        applyNameStyle(display);
        display.setBackgroundColor(Color.fromARGB(40, 0, 0, 0));
    }


    private TextDisplay spawn(
            Player player,
            QuestNPC npc,
            Location location,
            MarkerKind kind
    ) {

        TextDisplay display = findOrphan(location, markerKey, npc.getId());
        if (display != null) {
            display.text(markerText(kind, blinkOn));
            applyMarkerStyle(display);
            player.showEntity(plugin, display);
            return display;
        }

        display = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {

            textDisplay.setPersistent(false);
            textDisplay.setVisibleByDefault(false);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setGravity(false);
            textDisplay.setInvulnerable(true);
            textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setBackgroundColor(Color.fromARGB(50, 0, 0, 0));
            textDisplay.setLineWidth(120);
            textDisplay.text(markerText(kind, blinkOn));
            textDisplay.getPersistentDataContainer().set(
                    markerKey,
                    PersistentDataType.STRING,
                    npc.getId()
            );
            applyMarkerStyle(textDisplay);

        });

        player.showEntity(plugin, display);

        return display;

    }


    private Component markerText(MarkerKind kind, boolean blink) {

        if (kind == MarkerKind.AVAILABLE) {
            NamedTextColor bang = blink ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
            return Component.text("! ", bang)
                    .append(Component.text("QUEST", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" !", bang));
        }

        if (kind == MarkerKind.READY) {
            NamedTextColor mark = blink ? NamedTextColor.GREEN : NamedTextColor.AQUA;
            return Component.text("? ", mark)
                    .append(Component.text("QUEST", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" ?", mark));
        }

        return Component.empty();

    }


    private void cullNameNear(Location location, String npcId, TextDisplay keep) {
        if (location == null || location.getWorld() == null || npcId == null) {
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 3.5, 2.5)) {
            if (!(entity instanceof TextDisplay display) || display.equals(keep)) {
                continue;
            }
            String stored = display.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
            if (npcId.equalsIgnoreCase(stored)) {
                removeDisplay(display);
            }
        }
    }

    private TextDisplay findOrphan(Location location, NamespacedKey key, String npcId) {
        TextDisplay found = findExisting(location, key, npcId);
        if (found == null) {
            return null;
        }
        for (Map<String, TextDisplay> owned : markers.values()) {
            if (owned.containsValue(found)) {
                return null;
            }
        }
        if (nameDisplays.containsValue(found)) {
            return null;
        }
        return found;
    }

    private TextDisplay findExisting(Location location, NamespacedKey key, String npcId) {
        if (location == null || location.getWorld() == null || npcId == null) {
            return null;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 3.5, 2.5)) {
            if (!(entity instanceof TextDisplay display) || !display.isValid()) {
                continue;
            }
            String stored = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (npcId.equalsIgnoreCase(stored)) {
                return display;
            }
        }
        return null;
    }

    private boolean anyPlayerNear(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (Player player : location.getWorld().getPlayers()) {
            if (isInViewRange(player, location)) {
                return true;
            }
        }
        return false;
    }

    private void dropFarthestMarker(
            Player player,
            Map<String, TextDisplay> playerMarkers,
            Map<String, MarkerKind> kindsForPlayer,
            Location incoming
    ) {
        String farthestId = null;
        double farthest = -1;
        for (Map.Entry<String, TextDisplay> entry : playerMarkers.entrySet()) {
            TextDisplay display = entry.getValue();
            if (display == null || !display.isValid()) {
                farthestId = entry.getKey();
                break;
            }
            double dist = display.getLocation().distanceSquared(incoming);
            if (dist > farthest) {
                farthest = dist;
                farthestId = entry.getKey();
            }
        }
        if (farthestId == null) {
            return;
        }
        TextDisplay dropped = playerMarkers.remove(farthestId);
        kindsForPlayer.remove(farthestId);
        removeDisplay(dropped);
    }

    private TextDisplay spawnName(QuestNPC npc, Location location) {

        TextDisplay existing = findOrphan(location, nameKey, npc.getId());
        if (existing != null) {
            existing.text(nameComponent(npc));
            applyNameStyle(existing);
            return existing;
        }

        return location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {

            textDisplay.setPersistent(false);
            textDisplay.setVisibleByDefault(true);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setGravity(false);
            textDisplay.setInvulnerable(true);
            textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setBackgroundColor(Color.fromARGB(40, 0, 0, 0));
            textDisplay.setLineWidth(120);
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
        return Component.text(npc.getName(), NamedTextColor.AQUA);
    }


    private void applyMarkerStyle(TextDisplay display) {
        applyHologramStyle(display, 0.85f);
    }


    private void applyNameStyle(TextDisplay display) {
        applyHologramStyle(display, 0.7f);
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


    private void removeDisplay(TextDisplay display) {

        if (display == null) {
            return;
        }
        try {
            display.remove();
        } catch (Throwable ignored) {
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
