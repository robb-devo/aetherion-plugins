package de.aetherion.dungeons.instance;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonSession {

    private UUID ownerId;
    private final String floorId;
    private final String worldName;
    private final Location returnTo;
    private final Set<UUID> party = new HashSet<>();
    private final Set<UUID> ready = new HashSet<>();
    private final java.util.Map<UUID, Location> returns = new java.util.HashMap<>();
    private World world;
    private DungeonLayout layout;
    private boolean started;
    private boolean bossReleased;
    private boolean bossDead;
    private int scalingPlayers = 1;
    private final Set<Integer> unlockedRooms = new HashSet<>();
    private final Set<Integer> clearedRooms = new HashSet<>();
    private final java.util.Map<Integer, java.util.Set<UUID>> chestClaims = new java.util.HashMap<>();
    private final java.util.Set<Integer> chestBusy = new java.util.HashSet<>();
    /** Vestige piece ids already paid out to this player this run (anti-dupe weighting). */
    private final java.util.Map<UUID, java.util.Set<String>> vestigeSlotsThisRun = new java.util.HashMap<>();
    private org.bukkit.map.MapView mapView;
    private int closeSecondsLeft;
    private org.bukkit.scheduler.BukkitTask closeTask;

    public DungeonSession(UUID ownerId, String floorId, String worldName, Location returnTo) {
        this.ownerId = ownerId;
        this.floorId = floorId;
        this.worldName = worldName;
        this.returnTo = returnTo.clone();
        this.party.add(ownerId);
        this.returns.put(ownerId, this.returnTo.clone());
    }

    public UUID ownerId() {
        return ownerId;
    }

    /** Hand leadership to another party member without tearing down the instance. */
    public void transferOwner(UUID nextOwnerId) {
        if (nextOwnerId == null || nextOwnerId.equals(ownerId)) {
            return;
        }
        ownerId = nextOwnerId;
        party.add(nextOwnerId);
    }

    public String floorId() {
        return floorId;
    }

    public int floorNumber() {
        if (floorId != null && (EndlessSchemBuilder.FLOOR_ID.equals(floorId) || floorId.contains("endless"))) {
            return 2;
        }
        if (floorId != null && (floorId.contains("ice") || floorId.equals(LerfingTestBuilder.ICE_FLOOR_ID))) {
            return 2;
        }
        if (floorId != null && (floorId.contains("test") || floorId.equals(LerfingTestBuilder.FLOOR_ID))) {
            return 1;
        }
        if (floorId != null && (floorId.endsWith("_3") || floorId.contains("floor_3")
                || floorId.contains("prototype_3") || floorId.contains("ashes")
                || AshesEncounter.FLOOR_ID.equals(floorId))) {
            return 3;
        }
        if (floorId != null && (floorId.endsWith("_2") || floorId.contains("floor_2") || floorId.contains("prototype_2"))) {
            return 2;
        }
        return 1;
    }

    public String worldName() {
        return worldName;
    }

    public Location returnTo() {
        return returnTo.clone();
    }

    public void rememberReturn(Player player) {
        if (player != null && player.getLocation() != null) {
            returns.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    public Location returnFor(Player player) {
        if (player != null && returns.containsKey(player.getUniqueId())) {
            return returns.get(player.getUniqueId()).clone();
        }
        return returnTo();
    }

    public int scalingPlayers() {
        return Math.max(1, scalingPlayers);
    }

    public void setScalingPlayers(int scalingPlayers) {
        this.scalingPlayers = Math.max(1, scalingPlayers);
    }

    public World world() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public DungeonLayout layout() {
        return layout;
    }

    public void setLayout(DungeonLayout layout) {
        this.layout = layout;
    }

    public Set<UUID> party() {
        return party;
    }

    public Set<UUID> ready() {
        return ready;
    }

    public boolean started() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean bossReleased() {
        return bossReleased;
    }

    public void setBossReleased(boolean bossReleased) {
        this.bossReleased = bossReleased;
    }

    public boolean bossDead() {
        return bossDead;
    }

    public void setBossDead(boolean bossDead) {
        this.bossDead = bossDead;
    }

    public int unlockedRoom() {
        int max = -1;
        for (int index : unlockedRooms) {
            max = Math.max(max, index);
        }
        return max;
    }

    public void setUnlockedRoom(int unlockedRoom) {
        if (unlockedRoom >= 0) {
            unlockedRooms.add(unlockedRoom);
        }
    }

    public boolean isUnlocked(int index) {
        return unlockedRooms.contains(index);
    }

    public void unlock(int index) {
        unlockedRooms.add(index);
    }

    public Set<Integer> unlockedRooms() {
        return unlockedRooms;
    }

    public Set<Integer> clearedRooms() {
        return clearedRooms;
    }

    public org.bukkit.map.MapView mapView() {
        return mapView;
    }

    public void setMapView(org.bukkit.map.MapView mapView) {
        this.mapView = mapView;
    }

    public boolean isRoomCleared(int index) {
        return clearedRooms.contains(index);
    }

    public void markRoomCleared(int index) {
        clearedRooms.add(index);
        unlockedRooms.add(index);
    }

    public int clearedCount() {
        return clearedRooms.size();
    }

    public boolean allCombatCleared() {
        return layout != null && clearedRooms.size() >= layout.combatCount();
    }

    public boolean hasClaimedChest(int chestId, UUID playerId) {
        java.util.Set<UUID> claimed = chestClaims.get(chestId);
        return playerId != null && claimed != null && claimed.contains(playerId);
    }

    public void claimChest(int chestId, UUID playerId) {
        if (playerId == null) {
            return;
        }
        chestClaims.computeIfAbsent(chestId, ignored -> new HashSet<>()).add(playerId);
    }

    public boolean isChestBusy(int chestId) {
        return chestBusy.contains(chestId);
    }

    public void setChestBusy(int chestId, boolean busy) {
        if (busy) {
            chestBusy.add(chestId);
        } else {
            chestBusy.remove(chestId);
        }
    }

    public java.util.Set<String> vestigeSlotsThisRun(UUID playerId) {
        if (playerId == null) {
            return java.util.Set.of();
        }
        return vestigeSlotsThisRun.computeIfAbsent(playerId, ignored -> new HashSet<>());
    }

    public void rememberVestigeSlot(UUID playerId, String pieceId) {
        if (playerId == null || pieceId == null || pieceId.isBlank()) {
            return;
        }
        vestigeSlotsThisRun(playerId).add(pieceId);
    }

    public int closeSecondsLeft() {
        return closeSecondsLeft;
    }

    public void setCloseSecondsLeft(int closeSecondsLeft) {
        this.closeSecondsLeft = closeSecondsLeft;
    }

    public org.bukkit.scheduler.BukkitTask closeTask() {
        return closeTask;
    }

    public void setCloseTask(org.bukkit.scheduler.BukkitTask closeTask) {
        this.closeTask = closeTask;
    }

    public void cancelClose() {
        if (closeTask != null) {
            closeTask.cancel();
            closeTask = null;
        }
        closeSecondsLeft = 0;
    }

    public boolean isOwner(Player player) {
        return player != null && ownerId.equals(player.getUniqueId());
    }

    public boolean allReady() {
        if (party.isEmpty()) {
            return false;
        }
        for (UUID id : party) {
            if (!ready.contains(id)) {
                return false;
            }
        }
        return true;
    }
}
