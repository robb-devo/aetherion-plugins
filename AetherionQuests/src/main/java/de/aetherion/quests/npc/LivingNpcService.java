package de.aetherion.quests.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet NPCs via FancyNpcs — player model, turn-to-player (short range), no glow.
 */
public final class LivingNpcService {

    private static final String FANCY_PREFIX = "ae_living_";
    private static final int LOOK_RADIUS = 5;
    /** Packet visibility — high enough that hub pop-in mostly disappears. */
    private static final int VISIBILITY_DISTANCE = 96;

    private final Map<String, UUID> sentinels = new ConcurrentHashMap<>();
    private final Map<String, String> fancyNames = new ConcurrentHashMap<>();
    /** Living NPCs currently mid-escort / scripted walk — do not snap home. */
    private final Set<String> movementLocked = ConcurrentHashMap.newKeySet();
    private final AetherionQuests plugin;

    public LivingNpcService(AetherionQuests plugin) {
        this.plugin = plugin;
        extractBundledSkins();
    }

    public static boolean isLiving(String npcId) {
        return LivingNpcProfile.isLiving(npcId);
    }

    public boolean available() {
        return FancyNpcFacade.isAvailable();
    }

    public boolean isSpawned(String npcId) {
        return findFancy(npcId) != null;
    }

    /** While locked, guards must not teleport this NPC back to npcs.yml. */
    public void setMovementLocked(String npcId, boolean locked) {
        if (npcId == null || npcId.isBlank()) {
            return;
        }
        String key = npcId.toLowerCase(Locale.ROOT);
        if (locked) {
            movementLocked.add(key);
        } else {
            movementLocked.remove(key);
        }
    }

    public boolean isMovementLocked(String npcId) {
        return npcId != null && movementLocked.contains(npcId.toLowerCase(Locale.ROOT));
    }

    public Location locationOf(String npcId) {
        if (!isLiving(npcId) || !available()) {
            return null;
        }
        Object npc = findFancy(npcId);
        if (npc == null) {
            return null;
        }
        try {
            return FancyNpcFacade.locationOf(npc);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC location failed: " + ex.getMessage());
            return null;
        }
    }

    /** Instantly move a living FancyNPC (used for interpolated walks). */
    public boolean moveTo(String npcId, Location location) {
        if (location == null || location.getWorld() == null || !isLiving(npcId) || !available()) {
            return false;
        }
        Object npc = findFancy(npcId);
        if (npc == null) {
            return false;
        }
        try {
            Object data = FancyNpcFacade.data(npc);
            if (data == null) {
                return false;
            }
            FancyNpcFacade.setLocation(data, location.clone());
            // CRITICAL: moveForAll() without args uses isSwingArmOnUpdate() → bottle spam.
            // Always pass false so the NPC walks without punching.
            FancyNpcFacade.moveForAll(npc, false);
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC moveTo failed: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Scripted ground walk for FancyNPC (direct — no cross-plugin reflection).
     * Empty hand + no arm swing. Calls {@code onArrive} at the target, then returns home
     * after {@code homeDelayTicks}.
     */
    public boolean scriptWalk(String npcId, Location target, double speed, long homeDelayTicks, Runnable onArrive) {
        if (npcId == null || target == null || target.getWorld() == null || onArrive == null) {
            return false;
        }
        if (!isLiving(npcId) || !available() || findFancy(npcId) == null) {
            plugin.getLogger().warning("scriptWalk: FancyNPC missing for " + npcId);
            return false;
        }
        Location start = locationOf(npcId);
        if (start == null) {
            start = target.clone();
        }
        setMovementLocked(npcId, true);
        setTurnToPlayer(npcId, false);
        setMainHand(npcId, null);

        final Location home = start.clone();
        final Location goal = target.clone();
        final Location[] cursor = { plantFeet(start.clone(), start.getY()) };
        moveTo(npcId, cursor[0]);
        followName(npcId, cursor[0]);

        double arrive = 1.8;
        long homeDelay = Math.max(40L, homeDelayTicks);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            Location now = cursor[0];
            double dx = goal.getX() - now.getX();
            double dz = goal.getZ() - now.getZ();
            if (dx * dx + dz * dz <= arrive * arrive) {
                Location at = plantFeet(goal, goal.getY());
                at.setYaw(yawTo(now, goal));
                at.setPitch(0f);
                moveTo(npcId, at);
                followName(npcId, at);
                task.cancel();
                try {
                    onArrive.run();
                } finally {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        moveTo(npcId, plantFeet(home, home.getY()));
                        followName(npcId, home);
                        setMainHand(npcId, new ItemStack(org.bukkit.Material.GLASS_BOTTLE));
                        setTurnToPlayer(npcId, true);
                        setMovementLocked(npcId, false);
                    }, homeDelay);
                }
                return;
            }
            double len = Math.sqrt(dx * dx + dz * dz);
            Location next = now.clone().add((dx / len) * speed, 0, (dz / len) * speed);
            next = plantFeet(next, now.getY());
            next.setYaw(yawTo(now, goal));
            next.setPitch(0f);
            cursor[0] = next;
            if (!moveTo(npcId, next)) {
                plugin.getLogger().warning("scriptWalk moveTo failed for " + npcId);
            } else {
                followName(npcId, next);
            }
        }, 1L, 1L);
        return true;
    }

    /** Keep the TextDisplay nametag glued to the FancyNPC during scripted walks. */
    private void followName(String npcId, Location feet) {
        if (feet == null) {
            return;
        }
        try {
            de.aetherion.quests.ui.QuestMarkerManager markers = plugin.getMarkerManager();
            if (markers != null) {
                markers.followLivingName(npcId, feet);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static Location plantFeet(Location at, double preferY) {
        org.bukkit.World world = at.getWorld();
        if (world == null) {
            return at.clone();
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int prefer = (int) Math.floor(preferY);
        int min = Math.max(world.getMinHeight() + 1, prefer - 8);
        int maxSolid = Math.min(world.getMaxHeight() - 3, prefer);
        Double bestFeet = null;
        double bestDelta = Double.MAX_VALUE;
        for (int solidY = maxSolid; solidY >= min; solidY--) {
            if (!world.getBlockAt(x, solidY, z).getType().isSolid()) {
                continue;
            }
            org.bukkit.Material a = world.getBlockAt(x, solidY + 1, z).getType();
            org.bukkit.Material b = world.getBlockAt(x, solidY + 2, z).getType();
            if (a.isSolid() || b.isSolid()) {
                continue;
            }
            double feet = solidY + 1.0;
            if (feet - preferY > 1.05) {
                continue;
            }
            double delta = Math.abs(feet - preferY);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestFeet = feet;
            }
        }
        Location out = at.clone();
        out.setY(bestFeet != null ? bestFeet : preferY);
        out.setPitch(0f);
        return out;
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Temporarily clear / restore mainhand (stops bottle swing during walks). */
    public void setMainHand(String npcId, ItemStack item) {
        if (!isLiving(npcId) || !available()) {
            return;
        }
        Object npc = findFancy(npcId);
        if (npc == null) {
            return;
        }
        try {
            Object data = FancyNpcFacade.data(npc);
            if (data == null) {
                return;
            }
            Class<?> slotClass = FancyNpcFacade.equipmentSlotClass();
            Object slot = FancyNpcFacade.equipmentSlot("MAINHAND");
            ItemStack use = item == null ? new ItemStack(org.bukkit.Material.AIR) : item.clone();
            data.getClass().getMethod("addEquipment", slotClass, ItemStack.class).invoke(data, slot, use);
            FancyNpcFacade.updateForAll(npc, false);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC setMainHand failed: " + ex.getMessage());
        }
    }

    public void setTurnToPlayer(String npcId, boolean enabled) {
        if (!isLiving(npcId) || !available()) {
            return;
        }
        Object npc = findFancy(npcId);
        if (npc == null) {
            return;
        }
        try {
            Object data = FancyNpcFacade.data(npc);
            if (data == null) {
                return;
            }
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, enabled);
            FancyNpcFacade.invokeQuiet(npc, "updateForAll", boolean.class, false);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC turnToPlayer failed: " + ex.getMessage());
        }
    }

    public UUID sentinelId(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String key = npcId.toLowerCase(Locale.ROOT);
        return sentinels.computeIfAbsent(
                key,
                id -> UUID.nameUUIDFromBytes(("aetherion-living:" + id).getBytes())
        );
    }

    /**
     * @param force when false, skip recreate if already present near {@code location}
     */
    public QuestNPC spawn(QuestNPC questNpc, Location location, boolean force) {
        if (questNpc == null || location == null || location.getWorld() == null) {
            return null;
        }
        if (!isLiving(questNpc.getId())) {
            return null;
        }
        if (!available()) {
            plugin.getLogger().warning("FancyNpcs missing — cannot spawn living NPC: " + questNpc.getId());
            return null;
        }

        String key = questNpc.getId().toLowerCase(Locale.ROOT);
        if (isMovementLocked(key)) {
            // Escort / scripted walk in progress — leave the FancyNPC alone.
            questNpc.setEntityId(sentinelId(key));
            QuestNPCRegistry.registerNPC(questNpc);
            return questNpc;
        }
        if (!force && isSpawned(key)) {
            Location current = locationOf(key);
            if (current != null
                    && current.getWorld() != null
                    && current.getWorld().equals(location.getWorld())
                    && current.distanceSquared(location) <= 0.25) {
                questNpc.setEntityId(sentinelId(key));
                QuestNPCRegistry.registerNPC(questNpc);
                LivingNpcAtmosphere.onSpawned(questNpc.getId(), location.clone());
                Object existing = findFancy(key);
                if (existing != null) {
                    scheduleMojangSkin(existing, key);
                }
                return questNpc;
            }
        }

        remove(questNpc.getId());

        try {
            String fancyName = fancyName(questNpc.getId());
            UUID creator = new UUID(0L, 0L);

            Object data = FancyNpcFacade.createNpcData(fancyName, creator, location.clone());

            FancyNpcFacade.invoke(data, "setDisplayName", String.class, "<empty>");
            FancyNpcFacade.invoke(data, "setType", EntityType.class, EntityType.PLAYER);
            FancyNpcFacade.invoke(data, "setShowInTab", boolean.class, false);
            FancyNpcFacade.invoke(data, "setCollidable", boolean.class, false);
            FancyNpcFacade.invoke(data, "setGlowing", boolean.class, false);
            FancyNpcFacade.invoke(data, "setTurnToPlayer", boolean.class, true);
            FancyNpcFacade.invoke(data, "setTurnToPlayerDistance", int.class, LOOK_RADIUS);
            FancyNpcFacade.invoke(data, "setVisibilityDistance", int.class, VISIBILITY_DISTANCE);
            FancyNpcFacade.invoke(data, "setInteractionCooldown", float.class, 0.5f);
            FancyNpcFacade.invoke(data, "setSpawnEntity", boolean.class, true);
            // Skins applied async — FancyNpcs getByUsername blocks main thread + hits broken API.
            applyGear(data, questNpc.getId());

            Object fancy = FancyNpcFacade.adapt(data);
            FancyNpcFacade.setSaveToFile(fancy, false);
            FancyNpcFacade.create(fancy);

            Object manager = FancyNpcFacade.manager();
            FancyNpcFacade.register(manager, fancy);
            FancyNpcFacade.spawnForAll(fancy);

            scheduleMojangSkin(fancy, questNpc.getId());

            fancyNames.put(key, fancyName);
            questNpc.setEntityId(sentinelId(questNpc.getId()));
            QuestNPCRegistry.registerNPC(questNpc);
            LivingNpcAtmosphere.onSpawned(questNpc.getId(), location.clone());
            return questNpc;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            plugin.getLogger().severe(
                    "Failed to spawn living NPC " + questNpc.getId() + ": "
                            + root.getClass().getSimpleName() + " — " + root.getMessage()
            );
            root.printStackTrace();
            return null;
        }
    }

    public boolean remove(String npcId) {
        if (npcId == null || npcId.isBlank() || !available()) {
            return false;
        }
        String key = npcId.toLowerCase(Locale.ROOT);
        Object existing = findFancy(npcId);
        boolean removed = false;
        try {
            if (existing != null) {
                FancyNpcFacade.removeForAll(existing);
                Object manager = FancyNpcFacade.manager();
                FancyNpcFacade.unregister(manager, existing);
                removed = true;
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC remove failed: " + ex.getMessage());
        }
        fancyNames.remove(key);
        LivingNpcAtmosphere.onRemoved(npcId);
        QuestNPC questNpc = QuestNPCRegistry.getNPC(npcId);
        if (questNpc != null && isLiving(npcId)) {
            questNpc.setEntityId(null);
        }
        return removed;
    }

    public String questIdFromFancyName(String fancyName) {
        if (fancyName == null || !fancyName.startsWith(FANCY_PREFIX)) {
            return null;
        }
        String id = fancyName.substring(FANCY_PREFIX.length());
        return isLiving(id) ? id : null;
    }

    private static final java.util.concurrent.atomic.AtomicInteger SKIN_SLOT =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Fetch Mojang textures ourselves (async) — FancyNpcs username path uses a dead API URL
     * and blocks the server thread during boot restore.
     */
    private void scheduleMojangSkin(Object fancy, String npcId) {
        if (fancy == null || npcId == null) {
            return;
        }
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        if (profile == null) {
            return;
        }
        String username = profile.skinUsername();
        if (username == null || username.isBlank()) {
            return;
        }
        // Stagger so Mojang isn't hammered during mass restore.
        long delay = 5L + (SKIN_SLOT.getAndIncrement() % 40) * 8L;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            MojangSkinFetcher.Textures textures = MojangSkinFetcher.fetch(username);
            if (textures == null) {
                plugin.getLogger().fine("No Mojang skin for " + npcId + " (" + username + ")");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyTextures(fancy, npcId, username, textures, profile));
        }, delay);
    }

    private void applyTextures(
            Object fancy,
            String npcId,
            String username,
            MojangSkinFetcher.Textures textures,
            LivingNpcProfile profile
    ) {
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            Object variant = Enum.valueOf(variantClass.asSubclass(Enum.class), "AUTO");
            if (profile.slim()) {
                try {
                    variant = Enum.valueOf(variantClass.asSubclass(Enum.class), "SLIM");
                } catch (IllegalArgumentException ignored) {
                }
            }
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skinData = skinDataClass
                    .getConstructor(String.class, variantClass, String.class, String.class)
                    .newInstance(username, variant, textures.value(), textures.signature());
            Object data = FancyNpcFacade.data(fancy);
            data.getClass().getMethod("setSkinData", skinDataClass).invoke(data, skinData);
            FancyNpcFacade.updateForAll(fancy);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("Skin apply failed for " + npcId + ": " + ex.getMessage());
        }
    }

    private void applyGear(Object data, String npcId) throws ReflectiveOperationException {
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        if (profile == null) {
            return;
        }
        Class<?> slotClass = FancyNpcFacade.equipmentSlotClass();
        Method add = data.getClass().getMethod("addEquipment", slotClass, ItemStack.class);

        ItemStack hand = profile.handItem();
        if (hand != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("MAINHAND"), hand);
        }
        ItemStack chest = profile.chestItem();
        if (chest != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("CHEST"), chest);
        }
        ItemStack legs = profile.legsItem();
        if (legs != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("LEGS"), legs);
        }
        ItemStack boots = profile.bootsItem();
        if (boots != null) {
            add.invoke(data, FancyNpcFacade.equipmentSlot("FEET"), boots);
        }
    }

    private void extractBundledSkins() {
        File dir = new File(plugin.getDataFolder(), "skins");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        String[] names = {
                "alex.png", "alex_slim.png", "farmer.png", "guard.png", "miner.png",
                "mystic.png", "rogue.png", "sailor.png", "scholar.png", "scout.png",
                "testskin.png", "worker.png"
        };
        for (String name : names) {
            File out = new File(dir, name);
            try (InputStream in = plugin.getResource("skins/" + name)) {
                if (in == null) {
                    continue;
                }
                // Always refresh bundled skins so bad caches (old alex.png) get replaced.
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not extract skin " + name + ": " + ex.getMessage());
            }
        }
    }

    private Object findFancy(String npcId) {
        if (!available()) {
            return null;
        }
        try {
            Object manager = FancyNpcFacade.manager();
            String expected = fancyName(npcId);
            String known = fancyNames.getOrDefault(npcId.toLowerCase(Locale.ROOT), expected);

            Object found = FancyNpcFacade.tryGetNpc(manager, known);
            if (found != null) {
                return found;
            }
            if (!known.equals(expected)) {
                found = FancyNpcFacade.tryGetNpc(manager, expected);
                if (found != null) {
                    return found;
                }
            }

            for (Object npc : FancyNpcFacade.allNpcs(manager)) {
                Object data = FancyNpcFacade.data(npc);
                if (data == null) {
                    continue;
                }
                Object name = data.getClass().getMethod("getName").invoke(data);
                Object id = data.getClass().getMethod("getId").invoke(data);
                if (expected.equalsIgnoreCase(String.valueOf(name))
                        || expected.equalsIgnoreCase(String.valueOf(id))) {
                    return npc;
                }
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Living NPC lookup failed: " + ex.getMessage());
        }
        return null;
    }

    private static String fancyName(String npcId) {
        return FANCY_PREFIX + npcId.toLowerCase(Locale.ROOT);
    }
}
