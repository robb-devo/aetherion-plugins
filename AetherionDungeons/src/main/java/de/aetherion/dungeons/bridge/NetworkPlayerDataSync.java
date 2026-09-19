package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Full progression sync for network transfers (Hypixel-style on same host).
 * Per-player folders (storage/loadouts/sacks/pets) should also be junctioned;
 * this class still flushes + reloads memory caches so both directions stay hot.
 */
public final class NetworkPlayerDataSync {

    private static final List<String[]> YAML_KEYS = List.of(
            new String[]{"coins.yml", "players", "lifetime"},
            new String[]{"skills.yml", "players"},
            new String[]{"progress.yml", "players"},
            new String[]{"player-ranks.yml", "players"},
            new String[]{"shards.yml", "players"},
            new String[]{"xp-boosts.yml", "players"},
            new String[]{"recipe_unlocks.yml", "players"},
            new String[]{"unlocked_blueprints.yml", "players"},
            new String[]{"areas.yml", "players"},
            new String[]{"codex.yml", "players"},
            new String[]{"colosseum-unlock.yml", "players"}
    );

    private final Plugin plugin;

    public NetworkPlayerDataSync(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Call on the leaving server before Velocity Connect. */
    public void flushPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        player.closeInventory();

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null && items.isEnabled()) {
            saveStorageDirect(items, id);
            invoke(items, "getStorageInventory", "saveAll");
            invoke(items, "getCoins", "save");
            invoke(items, "getSkills", "save");
            invoke(items, "progress", "save");
            invoke(items, "getShards", "save");
            invoke(items, "xpBoost", "save");
            invoke(items, "recipeUnlocks", "save");
            invoke(items, "blueprintUnlocks", "save");
            invoke(items, "getAreas", "save");
            invoke(items, "getCodex", "save");
            invoke(items, "ranks", "save");
            invoke(items, "getMarket", "save");
        }

        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null && mobs.isEnabled()) {
            try {
                Object collection = mobs.getClass().getMethod("getPetCollection", UUID.class).invoke(mobs, id);
                Object mgr = mobs.getClass().getMethod("getPetDataManager").invoke(mobs);
                boolean saved = false;
                if (mgr != null && collection != null) {
                    for (Method m : mgr.getClass().getMethods()) {
                        if (!"save".equals(m.getName()) || m.getParameterCount() != 1) {
                            continue;
                        }
                        if (m.getParameterTypes()[0].isInstance(collection)) {
                            m.invoke(mgr, collection);
                            saved = true;
                            break;
                        }
                    }
                }
                if (!saved) {
                    mobs.getClass().getMethod("markPetsDirty", UUID.class).invoke(mobs, id);
                    Method saveDirty = mobs.getClass().getDeclaredMethod("saveDirtyPets");
                    saveDirty.setAccessible(true);
                    saveDirty.invoke(mobs);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Pet flush: " + ex.getMessage());
            }
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null && quests.isEnabled()) {
            invokeNoArg(quests, "savePlayer", player);
            invokeNoArg(quests, "saveAll", null);
        }
    }

    private void saveStorageDirect(Plugin items, UUID id) {
        try {
            Object storage = items.getClass().getMethod("getStorageInventory").invoke(items);
            if (storage == null) {
                return;
            }
            Method save = storage.getClass().getDeclaredMethod("saveStorage", UUID.class);
            save.setAccessible(true);
            save.invoke(storage, id);
        } catch (Exception ex) {
            plugin.getLogger().warning("Direct storage save failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> exportAll(Player player) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (player == null) {
            return out;
        }
        flushPlayer(player);
        UUID id = player.getUniqueId();
        String key = id.toString();

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null) {
            File folder = items.getDataFolder();
            for (String[] spec : YAML_KEYS) {
                Map<String, Object> found = readKeys(new File(folder, spec[0]), key, spec);
                if (!found.isEmpty()) {
                    out.put("yaml:" + spec[0], found);
                }
            }
            // Always include per-player files (even if junctions share them — keeps non-junction setups working)
            putFile(out, "file:storage", new File(folder, "storage/" + key + ".yml"));
            putFile(out, "file:loadout", new File(folder, "loadouts/" + key + ".yml"));
            putFile(out, "file:sack", new File(folder, "sacks/" + key + ".yml"));
        }

        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null) {
            putFile(out, "file:pets", new File(mobs.getDataFolder(), "pets/" + key + ".yml"));
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            putFile(out, "file:quests", new File(quests.getDataFolder(), "players/" + key + ".yml"));
            putFile(out, "file:quests2", new File(quests.getDataFolder(), "data/" + key + ".yml"));
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        putFile(out, "file:stats", new File(worldFolder, "stats/" + key + ".json"));
        putFile(out, "file:advancements", new File(worldFolder, "advancements/" + key + ".json"));
        return out;
    }

    @SuppressWarnings("unchecked")
    public void importAll(Player player, Map<?, ?> data) {
        if (player == null || data == null || data.isEmpty()) {
            return;
        }
        UUID id = player.getUniqueId();
        String key = id.toString();
        Map<String, Object> plain = toPlainMap(data);

        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null) {
            File folder = items.getDataFolder();
            for (Map.Entry<String, Object> entry : plain.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                try {
                    if (name.startsWith("yaml:") && value instanceof Map<?, ?> map) {
                        writeKeys(new File(folder, name.substring(5)), (Map<String, Object>) map);
                    } else if (name.equals("file:storage") && value instanceof String raw) {
                        writeString(new File(folder, "storage/" + key + ".yml"), raw);
                    } else if (name.equals("file:loadout") && value instanceof String raw) {
                        writeString(new File(folder, "loadouts/" + key + ".yml"), raw);
                    } else if (name.equals("file:sack") && value instanceof String raw) {
                        writeString(new File(folder, "sacks/" + key + ".yml"), raw);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Import failed for " + name, ex);
                }
            }
            applyCoinsInMemory(items, id, plain);
            reloadPrivate(items, "getCoins");
            reloadPrivate(items, "getSkills");
            reloadPrivate(items, "progress");
            reloadPrivate(items, "getShards");
            reloadPrivate(items, "xpBoost");
            reloadPrivate(items, "recipeUnlocks");
            reloadPrivate(items, "blueprintUnlocks");
            reloadPrivate(items, "getAreas");
            reloadPrivate(items, "getCodex");
            reloadPrivate(items, "ranks");
            invalidateStorageCache(items, id);
            refreshAetherionXpBar(items, player);
        }

        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null && plain.get("file:pets") instanceof String rawPets) {
            writeString(new File(mobs.getDataFolder(), "pets/" + key + ".yml"), rawPets);
            reloadPets(mobs, id);
            // Join hooks may have already spawned the local (stale) pet — re-equip from imported file.
            Bukkit.getScheduler().runTaskLater(plugin, () -> reequipPet(mobs, player), 2L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> reequipPet(mobs, player), 12L);
        }

        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            if (plain.get("file:quests") instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "players/" + key + ".yml"), raw);
            }
            if (plain.get("file:quests2") instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "data/" + key + ".yml"), raw);
            }
        }

        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();
        if (plain.get("file:stats") instanceof String raw) {
            writeString(new File(worldFolder, "stats/" + key + ".json"), raw);
        }
        if (plain.get("file:advancements") instanceof String raw) {
            writeString(new File(worldFolder, "advancements/" + key + ".json"), raw);
        }

        if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "tab scoreboard show main " + player.getName()), 20L);
        }
        plugin.getLogger().info("Network progression imported for " + player.getName()
                + " keys=" + plain.keySet());
    }

    /**
     * Bukkit YAML reloads nested maps as {@link org.bukkit.configuration.ConfigurationSection}.
     * Convert those (and nested sections) into plain maps so transfer import never skips data.
     */
    public static Map<String, Object> toPlainMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), toPlainValue(e.getValue()));
            }
            return out;
        }
        if (raw instanceof org.bukkit.configuration.ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                out.put(key, toPlainValue(section.get(key)));
            }
        }
        return out;
    }

    private static Object toPlainValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            return toPlainMap(section);
        }
        if (value instanceof Map<?, ?> map) {
            return toPlainMap(map);
        }
        if (value instanceof List<?> list) {
            java.util.ArrayList<Object> copy = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(toPlainValue(item));
            }
            return copy;
        }
        return value;
    }

    /**
     * Drop in-memory loadout/active state after a network snapshot so join hooks
     * cannot re-apply stale armor over the transferred inventory.
     */
    public void resetLoadoutRuntime(Player player) {
        if (player == null) {
            return;
        }
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return;
        }
        try {
            Object listener = items.getClass().getMethod("getLoadoutListener").invoke(items);
            if (listener == null) {
                return;
            }
            try {
                Method reset = listener.getClass().getMethod("resetRuntimeAfterNetworkSync", Player.class);
                reset.invoke(listener, player);
                return;
            } catch (NoSuchMethodException ignored) {
                // older jar — fall through to field scrub
            }
            for (Field field : listener.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())
                        && !java.util.Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                String name = field.getName().toLowerCase();
                if (!name.contains("loadout") && !name.contains("applying") && !name.contains("edit")) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(listener);
                if (value instanceof Map<?, ?> map) {
                    map.remove(player.getUniqueId());
                } else if (value instanceof java.util.Set<?> set) {
                    set.remove(player.getUniqueId());
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Loadout runtime reset failed: " + ex.getMessage());
        }
    }

    private void invalidateStorageCache(Plugin items, UUID id) {
        try {
            Object storage = items.getClass().getMethod("getStorageInventory").invoke(items);
            if (storage == null) {
                return;
            }
            Field field = storage.getClass().getDeclaredField("storageContents");
            field.setAccessible(true);
            Object map = field.get(storage);
            if (map instanceof Map<?, ?> m) {
                m.remove(id);
            }
            // force reload from disk next open
            Method load = storage.getClass().getDeclaredMethod("loadStorage", UUID.class);
            load.setAccessible(true);
            load.invoke(storage, id);
        } catch (Exception ex) {
            plugin.getLogger().warning("Storage cache invalidate failed: " + ex.getMessage());
        }
        invalidateLoadoutCache(items, id);
    }

    private void invalidateLoadoutCache(Plugin items, UUID id) {
        try {
            Object listener = items.getClass().getMethod("getLoadoutListener").invoke(items);
            if (listener == null) {
                return;
            }
            for (Field field : listener.getClass().getDeclaredFields()) {
                String typeName = field.getType().getName();
                if (!typeName.contains("LoadoutManager") && !Map.class.isAssignableFrom(field.getType())
                        && !java.util.Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(listener);
                if (value instanceof Map<?, ?> map) {
                    map.remove(id);
                    continue;
                }
                if (value instanceof java.util.Set<?> set) {
                    set.remove(id);
                    continue;
                }
                if (value != null && typeName.contains("LoadoutManager")) {
                    for (Field mf : value.getClass().getDeclaredFields()) {
                        if (!Map.class.isAssignableFrom(mf.getType())) {
                            continue;
                        }
                        mf.setAccessible(true);
                        Object map = mf.get(value);
                        if (map instanceof Map<?, ?> m) {
                            m.remove(id);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Loadout cache invalidate failed: " + ex.getMessage());
        }
    }

    private void reloadPets(Plugin mobs, UUID id) {
        try {
            // Drop cached collection so next access reloads from file
            for (Field field : mobs.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())
                        && field.getName().toLowerCase().contains("pet")) {
                    field.setAccessible(true);
                    Object map = field.get(mobs);
                    if (map instanceof Map<?, ?> m) {
                        m.remove(id);
                    }
                }
            }
            Method mark = null;
            try {
                mark = mobs.getClass().getMethod("markPetsDirty", UUID.class);
            } catch (NoSuchMethodException ignored) {
            }
            // Touch-load
            for (Method m : mobs.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class
                        && m.getName().toLowerCase().contains("pet")
                        && m.getName().toLowerCase().contains("collection")) {
                    m.invoke(mobs, id);
                    break;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Pet reload failed: " + ex.getMessage());
        }
    }

    private void reequipPet(Plugin mobs, Player player) {
        if (mobs == null || player == null || !player.isOnline()) {
            return;
        }
        try {
            Object active = mobs.getClass().getMethod("getActivePetManager").invoke(mobs);
            Object collection = mobs.getClass().getMethod("getPetCollection", UUID.class)
                    .invoke(mobs, player.getUniqueId());
            if (active == null || collection == null) {
                return;
            }
            // Unequip current entity if any, then equip from reloaded collection.
            try {
                active.getClass().getMethod("unequip", Player.class).invoke(active, player);
            } catch (NoSuchMethodException ignored) {
                try {
                    active.getClass().getMethod("removeActivePet", Player.class).invoke(active, player);
                } catch (NoSuchMethodException ignored2) {
                }
            }
            Object equipped = collection.getClass().getMethod("getEquippedPet").invoke(collection);
            if (equipped == null) {
                return;
            }
            for (Method m : active.getClass().getMethods()) {
                if (!"equip".equals(m.getName()) || m.getParameterCount() != 2) {
                    continue;
                }
                if (m.getParameterTypes()[0] == Player.class
                        && m.getParameterTypes()[1].isInstance(equipped)) {
                    m.invoke(active, player, equipped);
                    return;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Pet re-equip after transfer failed: " + ex.getMessage());
        }
    }

    private void refreshAetherionXpBar(Plugin items, Player player) {
        try {
            Object skills = items.getClass().getMethod("getSkills").invoke(items);
            if (skills == null || player == null || !player.isOnline()) {
                return;
            }
            // Find registered AetherionXpBarSync instance on the plugin and sync.
            for (Field field : items.getClass().getDeclaredFields()) {
                if (!field.getType().getName().contains("AetherionXpBarSync")
                        && !field.getType().getName().toLowerCase().contains("xpbar")) {
                    continue;
                }
                field.setAccessible(true);
                Object sync = field.get(items);
                if (sync == null) {
                    continue;
                }
                try {
                    sync.getClass().getMethod("sync", Player.class).invoke(sync, player);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
            // Fallback: set vanilla bar from accountXp directly.
            Object xpObj = skills.getClass().getMethod("accountXp", Player.class).invoke(skills, player);
            long xp = xpObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(xpObj));
            Class<?> lvl = Class.forName("de.aetherion.items.skill.AetherionLevel");
            int level = ((Number) lvl.getMethod("of", long.class).invoke(null, xp)).intValue();
            int max = ((Number) lvl.getField("MAX_LEVEL").get(null)).intValue();
            float progress = 1f;
            if (level < max) {
                long into = ((Number) lvl.getMethod("intoLevel", long.class).invoke(null, xp)).longValue();
                long need = ((Number) lvl.getMethod("xpToNext", int.class).invoke(null, level)).longValue();
                progress = need <= 0L ? 0f : Math.min(0.999f, (float) into / (float) need);
            }
            player.setLevel(level);
            player.setExp(progress);
        } catch (Exception ex) {
            plugin.getLogger().warning("Aetherion XP bar refresh failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCoinsInMemory(Plugin items, UUID id, Map<?, ?> data) {
        Object raw = data.get("yaml:coins.yml");
        Map<String, Object> map = toPlainMap(raw);
        if (map.isEmpty()) {
            return;
        }
        try {
            Object coins = items.getClass().getMethod("getCoins").invoke(items);
            if (coins == null) {
                return;
            }
            setUuidLongMap(coins, "balances", id, map.get("players." + id));
            setUuidLongMap(coins, "lifetime", id, map.get("lifetime." + id));
        } catch (Exception ex) {
            plugin.getLogger().warning("Coin memory apply failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void setUuidLongMap(Object service, String fieldName, UUID id, Object value) throws Exception {
        if (value == null) {
            return;
        }
        long amount = value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
        Field field = service.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object mapObj = field.get(service);
        if (mapObj instanceof ConcurrentHashMap<?, ?> map) {
            ((ConcurrentHashMap<UUID, Long>) map).put(id, amount);
        }
    }

    private void reloadPrivate(Plugin items, String getter) {
        try {
            Object service = items.getClass().getMethod(getter).invoke(items);
            if (service == null) {
                return;
            }
            Method load = service.getClass().getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(service);
        } catch (Exception ex) {
            plugin.getLogger().warning("Reload " + getter + " failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> readKeys(File file, String uuid, String[] spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (file == null || !file.isFile()) {
            return out;
        }
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (int i = 1; i < spec.length; i++) {
            String path = spec[i] + "." + uuid;
            if (yaml.contains(path)) {
                // Always plain maps so SnakeYAML round-trip never becomes dead ConfigurationSections.
                out.put(path, toPlainValue(yaml.get(path)));
            }
        }
        return out;
    }

    private void writeKeys(File file, Map<String, Object> values) throws Exception {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        var yaml = file.isFile()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            yaml.set(entry.getKey(), toPlainValue(entry.getValue()));
        }
        yaml.save(file);
    }

    private void putFile(Map<String, Object> out, String key, File file) {
        if (file == null || !file.isFile() || file.length() == 0) {
            return;
        }
        try {
            out.put(key, Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void writeString(File file, String raw) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.writeString(file.toPath(), raw, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not write " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void invoke(Plugin target, String getter, String method) {
        try {
            Object service = target.getClass().getMethod(getter).invoke(target);
            if (service != null) {
                service.getClass().getMethod(method).invoke(service);
            }
        } catch (Exception ignored) {
        }
    }

    private void invoke(Plugin target, String getter, String method, Object arg) {
        try {
            Object service = target.getClass().getMethod(getter).invoke(target);
            if (service == null) {
                return;
            }
            for (Method m : service.getClass().getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(arg)) {
                    m.invoke(service, arg);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void invokeNoArg(Plugin target, String method, Object arg) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(method)) {
                    continue;
                }
                if (arg == null && m.getParameterCount() == 0) {
                    m.invoke(target);
                    return;
                }
                if (arg != null && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(arg)) {
                    m.invoke(target, arg);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
