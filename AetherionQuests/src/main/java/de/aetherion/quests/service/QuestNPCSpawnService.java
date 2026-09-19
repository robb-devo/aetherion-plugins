package de.aetherion.quests.service;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.npc.LivingNpcService;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.npc.QuestNPCSpawner;
import de.aetherion.quests.npc.QuestNpcAppearance;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class QuestNPCSpawnService {

    public QuestNPC spawnNPC(String npcId, Location location) {
        if (npcId == null || npcId.isEmpty() || location == null || location.getWorld() == null) {
            return null;
        }
        if (QuestNPCRegistry.getNPC(npcId) == null) {
            return null;
        }
        if (LivingNpcService.isLiving(npcId)) {
            return spawnLiving(npcId, location);
        }
        Entity keeper = ensureUnique(npcId, location);
        if (keeper == null) {
            return null;
        }
        persist(QuestNPCRegistry.getNPC(npcId), location);
        refreshMarkers(npcId);
        return QuestNPCRegistry.getNPC(npcId);
    }

    private QuestNPC spawnLiving(String npcId, Location location) {
        return spawnLiving(npcId, location, true);
    }

    private QuestNPC spawnLiving(String npcId, Location location, boolean force) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        LivingNpcService living = plugin == null ? null : plugin.getLivingNpcService();
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (living == null || npc == null) {
            return null;
        }
        if (!living.available()) {
            if (plugin != null) {
                plugin.getLogger().warning("Cannot spawn living NPC without FancyNpcs: " + npcId);
            }
            return null;
        }
        // Living hosts are Fancy packet NPCs — always strip leftover villager copies.
        removeAllTagged(npcId);
        QuestNPC spawned = living.spawn(npc, location, force);
        if (spawned == null) {
            return null;
        }
        persist(spawned, location);
        refreshMarkers(npcId);
        return spawned;
    }

    /**
     * Remove the live entity only. Saved position in {@code npcs.yml} stays for reboot
     * restore. Mid-session, {@link #ensureAllUnique()} will not bring them back —
     * only {@link #restoreAllNPCs()} / place / death-guard does.
     */
    public boolean despawn(String npcId) {
        return unload(npcId);
    }

    /** Remove entity and wipe the saved position (forget this NPC placement). */
    public boolean forget(String npcId) {
        boolean removed = unload(npcId);
        AetherionQuests plugin = AetherionQuests.getInstance();
        boolean deleted = false;
        if (plugin != null && plugin.getNpcDataStorage() != null && npcId != null) {
            deleted = plugin.getNpcDataStorage().hasSavedNPC(npcId);
            plugin.getNpcDataStorage().deleteNPC(npcId);
        }
        return removed || deleted;
    }

    public boolean unload(String npcId) {
        if (npcId == null || npcId.isEmpty()) {
            return false;
        }
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        boolean removed = false;
        if (LivingNpcService.isLiving(npcId)) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            LivingNpcService living = plugin == null ? null : plugin.getLivingNpcService();
            if (living != null) {
                removed = living.remove(npcId);
            }
        }
        removed = removeAllTagged(npcId) || removed;
        if (npc != null) {
            npc.setEntityId(null);
        }
        refreshMarkers(npcId);
        return npc != null || removed;
    }

    public boolean despawnEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        String npcId = QuestNpcAppearance.npcId(entity);
        if (npcId == null || npcId.isEmpty()) {
            QuestNPC npc = QuestNPCRegistry.getNPCByEntityId(entity.getUniqueId().toString());
            if (npc != null) {
                npcId = npc.getId();
            }
        }
        if (npcId == null || npcId.isEmpty()) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin != null && plugin.getMarkerManager() != null) {
                npcId = plugin.getMarkerManager().getNpcId(entity);
            }
        }
        if (npcId == null || npcId.isEmpty()) {
            return false;
        }
        return unload(npcId);
    }

    public QuestNPC restoreNPC(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (npc == null || plugin == null || plugin.getNpcDataStorage() == null) {
            return null;
        }
        Location location = plugin.getNpcDataStorage().getSavedLocation(npcId);
        if (location == null || location.getWorld() == null) {
            return null;
        }
        loadChunksAround(location);
        if (LivingNpcService.isLiving(npcId)) {
            return spawnLiving(npcId, location, false);
        }
        Entity keeper = ensureUnique(npcId, location);
        if (keeper == null) {
            return null;
        }
        persist(npc, location);
        refreshMarkers(npcId);
        return npc;
    }

    public void restoreAllNPCs() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getNpcDataStorage() == null) {
            return;
        }
        restoreMissingHubPositions(plugin.getNpcDataStorage());
        stripLeftoverVisuals();

        NPCDataStorage storage = plugin.getNpcDataStorage();
        for (QuestNPC npc : List.copyOf(QuestNPCRegistry.getAll().values())) {
            if (npc == null || npc.getId() == null || npc.getId().isEmpty()) {
                continue;
            }
            if (!storage.hasSavedNPC(npc.getId())) {
                continue;
            }
            // Living FancyNpcs: never re-spawn if already visible (entityId sentinel is not enough).
            if (LivingNpcService.isLiving(npc.getId())) {
                LivingNpcService living = plugin.getLivingNpcService();
                if (living != null && living.isSpawned(npc.getId())) {
                    continue;
                }
            } else if (npc.getEntityId() != null) {
                Entity existing = Bukkit.getEntity(npc.getEntityId());
                if (existing != null && existing.isValid()) {
                    continue;
                }
            }
            npc.setEntityId(null);
            QuestNPC restored = restoreNPC(npc.getId());
            if (restored == null) {
                plugin.getLogger().warning("Could not restore quest NPC: " + npc.getId());
            } else {
                plugin.getLogger().info("Restored quest NPC: " + npc.getId());
            }
        }
    }

    public Entity ensureUnique(String npcId, Location target) {
        if (LivingNpcService.isLiving(npcId)) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            LivingNpcService living = plugin == null ? null : plugin.getLivingNpcService();
            if (living != null && living.isMovementLocked(npcId)) {
                return null;
            }
            // Already visible — never recreate at npcs.yml home (that killed escorts).
            if (living != null && living.isSpawned(npcId)) {
                return null;
            }
            Location dest = target;
            if (dest == null || dest.getWorld() == null) {
                if (plugin != null && plugin.getNpcDataStorage() != null) {
                    dest = plugin.getNpcDataStorage().getSavedLocation(npcId);
                }
            }
            spawnLiving(npcId, dest, false);
            return null;
        }
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc == null) {
            return null;
        }

        Location dest = target;
        if (dest == null || dest.getWorld() == null) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin != null && plugin.getNpcDataStorage() != null) {
                dest = plugin.getNpcDataStorage().getSavedLocation(npc.getId());
            }
        }
        if (dest != null && dest.getWorld() != null) {
            loadChunksAround(dest);
        }

        List<Villager> hosts = new ArrayList<>();
        List<Entity> extras = new ArrayList<>();
        collectTagged(npc.getId(), hosts, extras);

        Villager keep = pickHost(hosts, npc, dest);
        Location spawnAt = dest;
        if (spawnAt == null && keep != null) {
            spawnAt = keep.getLocation();
        }
        if (spawnAt == null && !extras.isEmpty()) {
            spawnAt = extras.get(0).getLocation();
        }

        if (keep == null) {
            if (spawnAt == null || spawnAt.getWorld() == null) {
                npc.setEntityId(null);
                return null;
            }
            QuestNPC spawned = QuestNPCSpawner.spawnNPC(npc, spawnAt);
            if (spawned == null || spawned.getEntityId() == null) {
                return null;
            }
            Entity spawnedEntity = Bukkit.getEntity(spawned.getEntityId());
            keep = spawnedEntity instanceof Villager villager ? villager : null;
            if (keep == null) {
                return null;
            }
        }

        UUID keepId = keep.getUniqueId();
        for (Villager extra : hosts) {
            if (extra.getUniqueId().equals(keepId)) {
                continue;
            }
            QuestNpcAppearance.removeTree(extra);
        }
        for (Entity extra : extras) {
            if (extra.getUniqueId().equals(keepId)) {
                continue;
            }
            QuestNpcAppearance.removeTree(extra);
        }

        if (dest != null && dest.getWorld() != null) {
            if (keep.getWorld() != dest.getWorld()
                    || keep.getLocation().distanceSquared(dest) > 0.25) {
                keep.teleport(dest);
            }
        }

        QuestNpcAppearance.apply(keep, npc);
        cleanupNametaggedCopies(npc, keep.getLocation());

        npc.setEntityId(keep.getUniqueId());
        QuestNPCRegistry.registerNPC(npc);
        refreshMarkers(npc.getId());
        return keep;
    }

    /**
     * Deduplicate live entities only. Does <b>not</b> spawn missing NPCs from
     * {@code npcs.yml} — that is {@link #restoreAllNPCs()} on boot / explicit restore.
     * Otherwise {@code /questnpc remove} and mid-session unload would instantly come back.
     */
    public int ensureAllUnique() {
        Set<String> ids = new HashSet<>();
        AetherionQuests plugin = AetherionQuests.getInstance();
        LivingNpcService living = plugin == null ? null : plugin.getLivingNpcService();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                String id = QuestNpcAppearance.npcId(entity);
                if (id != null && QuestNPCRegistry.getNPC(id) != null) {
                    ids.add(QuestNPCRegistry.getNPC(id).getId());
                }
            }
        }
        if (living != null && living.available()) {
            for (QuestNPC npc : List.copyOf(QuestNPCRegistry.getAll().values())) {
                if (npc == null || npc.getId() == null || !LivingNpcService.isLiving(npc.getId())) {
                    continue;
                }
                if (living.isSpawned(npc.getId())) {
                    ids.add(npc.getId());
                }
            }
        }
        int kept = 0;
        for (String id : ids) {
            if (LivingNpcService.isLiving(id)) {
                ensureUnique(id, null);
                if (living != null && living.isSpawned(id)) {
                    kept++;
                }
                continue;
            }
            if (ensureUnique(id, null) != null) {
                kept++;
            }
        }
        return kept;
    }

    private void restoreMissingHubPositions(NPCDataStorage storage) {
        // Origin map: do not invent megamap coordinates. Place NPCs via /aquest npc …
        // npcs.yml is the source of truth after migrate.
    }

    private void saveIfMissing(NPCDataStorage storage, World world, String id, double x, double y, double z, float yaw) {
        QuestNPC npc = QuestNPCRegistry.getNPC(id);
        if (npc == null || storage.hasSavedNPC(id)) {
            return;
        }
        storage.saveNPC(npc, new Location(world, x, y, z, yaw, 0f));
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null) {
            plugin.getLogger().info("Seeded spawn position for quest NPC: " + id);
        }
    }

    private void stripLeftoverVisuals() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (entity instanceof ArmorStand && QuestNpcAppearance.npcId(entity) != null) {
                    QuestNpcAppearance.removeTree(entity);
                }
            }
        }
    }

    private void collectTagged(String npcId, List<Villager> hosts, List<Entity> extras) {
        String id = npcId.toLowerCase(Locale.ROOT);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (entity == null || entity.isDead() || entity instanceof Player) {
                    continue;
                }
                String stored = QuestNpcAppearance.npcId(entity);
                if (stored == null || !id.equalsIgnoreCase(stored)) {
                    continue;
                }
                if (entity instanceof Villager villager && !QuestNpcAppearance.isVisual(villager)) {
                    hosts.add(villager);
                } else {
                    extras.add(entity);
                }
            }
        }
    }

    private Villager pickHost(List<Villager> hosts, QuestNPC npc, Location dest) {
        if (hosts.isEmpty()) {
            return null;
        }
        if (npc.getEntityId() != null) {
            for (Villager host : hosts) {
                if (host.getUniqueId().equals(npc.getEntityId())) {
                    return host;
                }
            }
        }
        if (dest != null && dest.getWorld() != null) {
            Villager closest = null;
            double best = Double.MAX_VALUE;
            for (Villager host : hosts) {
                if (host.getWorld() != dest.getWorld()) {
                    continue;
                }
                double distance = host.getLocation().distanceSquared(dest);
                if (distance < best) {
                    best = distance;
                    closest = host;
                }
            }
            if (closest != null) {
                return closest;
            }
        }
        return hosts.get(0);
    }

    private boolean removeAllTagged(String npcId) {
        if (npcId == null || npcId.isEmpty()) {
            return false;
        }
        boolean removed = false;
        String id = npcId.toLowerCase(Locale.ROOT);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (entity == null || entity.isDead()) {
                    continue;
                }
                String stored = QuestNpcAppearance.npcId(entity);
                if (stored != null && id.equalsIgnoreCase(stored)) {
                    QuestNpcAppearance.removeTree(entity);
                    removed = true;
                }
            }
        }
        return removed;
    }

    private void cleanupNametaggedCopies(QuestNPC npc, Location location) {
        if (npc == null || location == null || location.getWorld() == null) {
            return;
        }
        String expected = ChatColor.stripColor(npc.getName());
        if (expected == null || expected.isBlank()) {
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, 8, 8, 8)) {
            if (entity == null || entity.isDead() || entity instanceof Player) {
                continue;
            }
            if (QuestNpcAppearance.npcId(entity) != null) {
                continue;
            }
            if (entity instanceof TextDisplay) {
                continue;
            }
            if (!(entity instanceof Villager) && !(entity instanceof ArmorStand)) {
                continue;
            }
            String custom = entity.getCustomName();
            if (custom == null) {
                continue;
            }
            String name = ChatColor.stripColor(custom);
            if (expected.equalsIgnoreCase(name)) {
                QuestNpcAppearance.removeTree(entity);
            }
        }
    }

    private void loadChunksAround(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getChunkAt(chunkX + dx, chunkZ + dz).load(true);
            }
        }
    }

    private void persist(QuestNPC npc, Location location) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getNpcDataStorage() == null || npc == null || location == null) {
            return;
        }
        plugin.getNpcDataStorage().saveNPC(npc, location);
    }

    private void refreshMarkers(String npcId) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getMarkerManager() != null) {
            plugin.getMarkerManager().refreshNpc(npcId);
        }
    }
}
