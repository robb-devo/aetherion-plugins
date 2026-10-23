package de.aetherion.items.storage;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * mmo-r and mmo-d share one storage folder. Every hop, quit and shutdown runs {@link StorageInventory#saveAll()};
 * it must not write the cached storage of a player who has left this backend over the file another backend
 * wrote since (2026-10-23 22:56:40 and 22:58:56).
 */
class StorageInventorySaveAllTest {

    private static final String STALE = "state-when-he-left-here";
    private static final String ON_DISK = "state-written-by-the-other-backend";
    private static final String ONLINE_DEPOSIT = "deposit";

    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();
    private static final Logger LOGGER = Logger.getLogger("StorageInventorySaveAllTest");
    private static Server server;

    @TempDir
    Path dataFolder;

    private StorageInventory storage;

    @BeforeAll
    static void fakeServer() throws Exception {
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.createPluginLifecycleEventManager(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> mock(
                        UnsafeValues.class.getMethod(
                                        "createPluginLifecycleEventManager",
                                        JavaPlugin.class,
                                        java.util.function.BooleanSupplier.class)
                                .getReturnType()));
        server = proxy(Server.class, (method, args) -> switch (method.getName()) {
            case "getLogger" -> LOGGER;
            case "getUnsafe" -> unsafe;
            case "getPlayer" -> args.length == 1 && args[0] instanceof UUID id && ONLINE.contains(id) ? player(id) : null;
            case "getName", "getVersion", "getBukkitVersion" -> "test";
            default -> null;
        });
        Field field = org.bukkit.Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    @BeforeEach
    void freshBackend() throws Exception {
        ONLINE.clear();
        storage = new StorageInventory(new TestPlugin(dataFolder.toFile()));
    }

    @Test
    void saveAllLeavesTheFileOfAPlayerWhoLeftThisBackend() throws Exception {
        UUID left = UUID.randomUUID();
        seedCache(left, mockItem(STALE));
        writeSlotBytes(left, ON_DISK);

        storage.saveAll();

        assertEquals(ON_DISK, readSlotLabel(left));
    }

    @Test
    void saveAllStillWritesPlayersOnlineHere() throws Exception {
        UUID here = UUID.randomUUID();
        ONLINE.add(here);
        seedCache(here, mockItem(ONLINE_DEPOSIT));
        Files.deleteIfExists(storageFile(here));

        storage.saveAll();

        assertEquals(ONLINE_DEPOSIT, readSlotLabel(here));
    }

    // ------------------------------------------------------------------ cache + files (no Paper registry)

    private void seedCache(UUID uuid, ItemStack item) throws Exception {
        ItemStack[][] pages = new ItemStack[StorageInventory.MAX_PAGES][StorageInventory.STORAGE_SIZE];
        pages[0][0] = item;
        storageContents().put(uuid, pages);
        unlockedPages().put(uuid, 1);
        hubAccess().put(uuid, false);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ItemStack[][]> storageContents() throws Exception {
        Field field = StorageInventory.class.getDeclaredField("storageContents");
        field.setAccessible(true);
        return (Map<UUID, ItemStack[][]>) field.get(storage);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Integer> unlockedPages() throws Exception {
        Field field = StorageInventory.class.getDeclaredField("unlockedPages");
        field.setAccessible(true);
        return (Map<UUID, Integer>) field.get(storage);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Boolean> hubAccess() throws Exception {
        Field field = StorageInventory.class.getDeclaredField("hubAccess");
        field.setAccessible(true);
        return (Map<UUID, Boolean>) field.get(storage);
    }

    private static ItemStack mockItem(String label) {
        byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
        Material type = mock(Material.class);
        when(type.isAir()).thenReturn(false);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(bytes);
        when(stack.hasItemMeta()).thenReturn(false);
        return stack;
    }

    // ------------------------------------------------------------------ fake backend

    /** Paper resolves one {@link PluginLoader} service for every JavaPlugin; a real server ships its own. */
    public static final class NoPluginLoader implements PluginLoader {
        @Override
        public Plugin loadPlugin(File file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PluginDescriptionFile getPluginDescription(File file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Pattern[] getPluginFileFilters() {
            return new Pattern[0];
        }

        @Override
        public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) {
            return Map.of();
        }

        @Override
        public void enablePlugin(Plugin plugin) {
        }

        @Override
        public void disablePlugin(Plugin plugin) {
        }
    }

    private static final class TestPlugin extends JavaPlugin {
        @SuppressWarnings({"deprecation", "removal"})
        TestPlugin(File dataFolder) {
            super(new JavaPluginLoader(server),
                    new PluginDescriptionFile("AetherionItems", "test", TestPlugin.class.getName()),
                    dataFolder, new File(dataFolder, "AetherionItems.jar"));
        }
    }

    private static Player player(UUID id) {
        return proxy(Player.class, (method, args) -> "getUniqueId".equals(method.getName()) ? id : null);
    }

    private interface Handler {
        Object handle(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(StorageInventorySaveAllTest.class.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> "fake " + type.getSimpleName();
                    default -> {
                        Object value = handler.handle(method, args == null ? new Object[0] : args);
                        yield value != null ? value : primitiveDefault(method.getReturnType());
                    }
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
    }

    // ------------------------------------------------------------------ storage files

    private Path storageFile(UUID id) {
        return dataFolder.resolve("storage").resolve(id + ".yml");
    }

    private void writeSlotBytes(UUID id, String label) throws Exception {
        String bytes = Base64.getEncoder().encodeToString(label.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(storageFile(id).getParent());
        Files.writeString(storageFile(id), "unlocked-pages: 1\nhub-access: false\npages:\n  '1':\n    0-bytes: " + bytes + "\n");
    }

    private String readSlotLabel(UUID id) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile(id).toFile());
        String raw = yaml.getString("pages.1.0-bytes");
        if (raw == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
    }
}
