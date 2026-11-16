package de.aetherion.core.npc;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Painted skins for every cast that stands on a {@link CastTroupe} body: the isle crews, the traders, the
 * townsfolk. Each skin is an original PNG painted for Aetherion ({@code docs/npc/skins/paint_skins.py},
 * group WORLD), bundled in this jar under {@code skins/cast/} and listed in {@code skins/cast/index.txt}.
 * No player accounts, nobody else's face.
 *
 * <p>Minecraft only shows textures Mojang has signed, so a PNG goes through FancyNpcs' own MineSkin upload once.
 * The signed result is kept in {@code <shared>/world/cast-skins-<port>.yml} together with the PNG's checksum;
 * every backend reads the others' files too, so a skin is signed once for the whole network and a repainted
 * PNG is signed again by itself. The quest cast keeps its own wardrobe in AetherionQuests; the folders differ
 * ({@code aetherion-cast} here, {@code aetherion} there), so the two never answer each other's signatures.
 */
public final class CastSkins {

    private static final String FOLDER = "aetherion-cast";
    private static final String INDEX = "skins/cast/index.txt";

    private final JavaPlugin plugin;
    private final File cacheDir;
    private final File cacheFile;
    private final Map<String, Boolean> slim = new LinkedHashMap<>();
    private final Map<String, String> checksum = new LinkedHashMap<>();
    private final List<Consumer<String>> signedListeners = new CopyOnWriteArrayList<>();
    private YamlConfiguration cache = new YamlConfiguration();
    private boolean ready;

    public CastSkins(JavaPlugin plugin, File cacheDir) {
        this.plugin = plugin;
        this.cacheDir = cacheDir;
        this.cacheFile = new File(cacheDir, "cast-skins-" + Bukkit.getPort() + ".yml");
        readIndex();
    }

    /** A skin of this id is bundled. */
    public boolean has(String id) {
        return id != null && slim.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** Hear the id of every skin that finishes signing (the placeholder outfit can come off). */
    public void onSigned(Consumer<String> listener) {
        if (listener != null) {
            signedListeners.add(listener);
        }
    }

    public void removeListener(Consumer<String> listener) {
        signedListeners.remove(listener);
    }

    public String status() {
        long signed = slim.keySet().stream().filter(this::signed).count();
        return signed + "/" + slim.size() + " cast skins signed";
    }

    private boolean signed(String id) {
        String at = "skins." + id;
        return cache.isString(at + ".value") && cache.isString(at + ".signature")
                && checksum.getOrDefault(id, "").equals(cache.getString(at + ".sha1", ""));
    }

    /**
     * Put the skin on FancyNpcs {@code NpcData}, before create or live (the caller re-sends the NPC).
     *
     * @return true when a signed texture is on the data right now; false while it is still being signed, when
     *         there is no such skin or when FancyNpcs is missing
     */
    public boolean apply(Object npcData, String skinId) {
        if (npcData == null || !has(skinId) || !FancyNpcFacade.isAvailable()) {
            return false;
        }
        String id = skinId.toLowerCase(Locale.ROOT);
        prepare();
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object variant = Enum.valueOf((Class) variantClass.asSubclass(Enum.class),
                    Boolean.TRUE.equals(slim.get(id)) ? "SLIM" : "AUTO");
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skin;
            if (signed(id)) {
                skin = skinDataClass.getConstructor(String.class, variantClass, String.class, String.class)
                        .newInstance(identifier(id), variant, cache.getString("skins." + id + ".value"),
                                cache.getString("skins." + id + ".signature"));
            } else {
                Object api = FancyNpcFacade.plugin();
                Object manager = api.getClass().getMethod("getSkinManager").invoke(api);
                // Answers from FancyNpcs' own cache at once, or queues the MineSkin upload and fills the
                // texture in later (SkinGeneratedEvent).
                skin = manager.getClass().getMethod("getByFile", String.class, variantClass)
                        .invoke(manager, identifier(id), variant);
                if (skin == null) {
                    return false;
                }
                remember(id, skin);
            }
            npcData.getClass().getMethod("setSkinData", skinDataClass).invoke(npcData, skin);
            return Boolean.TRUE.equals(skin.getClass().getMethod("hasTexture").invoke(skin));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().fine("Cast skin " + id + " not applied: " + rootMessage(exception));
            return false;
        }
    }

    // ------------------------------------------------------------------ plumbing

    private static String identifier(String id) {
        return FOLDER + "/" + id + ".png";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private void readIndex() {
        try (InputStream in = plugin.getResource(INDEX)) {
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] words = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
                if (words.length == 0 || words[0].isEmpty() || words[0].startsWith("#")) {
                    continue;
                }
                slim.put(words[0], words.length > 1 && words[1].equals("slim"));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Cast skins: index not readable (" + exception.getMessage() + ")");
        }
    }

    /** First use, once FancyNpcs is up: PNGs out to its skins folder, signatures in, listener on. */
    private void prepare() {
        if (ready) {
            return;
        }
        ready = true;
        extract();
        loadCache();
        listen();
    }

    private void loadCache() {
        cache = cacheFile.isFile() ? YamlConfiguration.loadConfiguration(cacheFile) : new YamlConfiguration();
        File[] peers = cacheDir.listFiles((dir, name) -> name.startsWith("cast-skins-") && name.endsWith(".yml"));
        if (peers == null) {
            return;
        }
        for (File peer : peers) {
            if (peer.equals(cacheFile)) {
                continue;
            }
            ConfigurationSection skins = YamlConfiguration.loadConfiguration(peer).getConfigurationSection("skins");
            if (skins == null) {
                continue;
            }
            for (String id : skins.getKeys(false)) {
                // Take a peer's signature only for the PNG we carry, and only where ours is missing or stale.
                String sha = skins.getString(id + ".sha1", "");
                if (signed(id) || sha.isEmpty() || !sha.equals(checksum.get(id))
                        || !skins.isString(id + ".value") || !skins.isString(id + ".signature")) {
                    continue;
                }
                String at = "skins." + id;
                cache.set(at + ".value", skins.getString(id + ".value"));
                cache.set(at + ".signature", skins.getString(id + ".signature"));
                cache.set(at + ".sha1", sha);
            }
        }
    }

    private void extract() {
        Plugin fancy = Bukkit.getPluginManager().getPlugin(FancyNpcFacade.PLUGIN_NAME);
        File base = fancy != null ? new File(fancy.getDataFolder(), "skins") : new File("plugins/FancyNpcs/skins");
        File dir = new File(base, FOLDER);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Cast skins: could not create " + dir.getPath());
            return;
        }
        int written = 0;
        for (String id : slim.keySet()) {
            try (InputStream in = plugin.getResource("skins/cast/" + id + ".png")) {
                if (in == null) {
                    continue;
                }
                byte[] bytes = in.readAllBytes();
                checksum.put(id, sha1(bytes));
                File out = new File(dir, id + ".png");
                if (out.isFile() && Arrays.equals(Files.readAllBytes(out.toPath()), bytes)) {
                    continue;
                }
                Files.write(out.toPath(), bytes);
                written++;
            } catch (IOException exception) {
                plugin.getLogger().warning("Cast skins: could not extract " + id + " (" + exception.getMessage() + ")");
            }
        }
        if (written > 0) {
            plugin.getLogger().info("Cast skins: wrote " + written + " PNG(s) to " + dir.getPath());
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            StringBuilder out = new StringBuilder(40);
            for (byte b : MessageDigest.getInstance("SHA-1").digest(bytes)) {
                out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(Arrays.hashCode(bytes));
        }
    }

    private void remember(String id, Object skin) {
        try {
            Object value = skin.getClass().getMethod("getTextureValue").invoke(skin);
            Object signature = skin.getClass().getMethod("getTextureSignature").invoke(skin);
            if (value instanceof String v && signature instanceof String s && !v.isBlank() && !s.isBlank()) {
                cache.set("skins." + id + ".value", v);
                cache.set("skins." + id + ".signature", s);
                cache.set("skins." + id + ".sha1", checksum.getOrDefault(id, ""));
                save();
            }
        } catch (ReflectiveOperationException ignored) {
            // an API without these getters keeps signing every boot; nothing to store
        }
    }

    private void save() {
        String data = cache.saveToString();
        Runnable write = () -> {
            try {
                de.aetherion.core.persist.AtomicYaml.writeString(cacheFile, data);
            } catch (IOException exception) {
                plugin.getLogger().warning("Cast skins: could not save " + cacheFile.getName() + " (" + exception.getMessage() + ")");
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    /** FancyNpcs tells us when a queued upload came back (reflection: Core has no compile link to it). */
    private void listen() {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName("de.oliver.fancynpcs.api.skins.SkinGeneratedEvent");
            Listener marker = new Listener() {
            };
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Object ident = event.getClass().getMethod("getId").invoke(event);
                    Object skin = event.getClass().getMethod("getSkin").invoke(event);
                    if (!(ident instanceof String raw) || skin == null) {
                        return;
                    }
                    String norm = raw.replace('\\', '/').toLowerCase(Locale.ROOT);
                    int at = norm.lastIndexOf(FOLDER + "/");
                    if (at < 0 || !norm.endsWith(".png")) {
                        return;
                    }
                    String id = norm.substring(at + FOLDER.length() + 1, norm.length() - 4);
                    if (!has(id) || !plugin.isEnabled()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        remember(id, skin);
                        plugin.getLogger().info("Cast skin signed: " + id + " (" + status() + ")");
                        for (Consumer<String> listenerOfSigned : signedListeners) {
                            try {
                                listenerOfSigned.accept(id);
                            } catch (RuntimeException exception) {
                                plugin.getLogger().fine("Cast skin listener: " + exception);
                            }
                        }
                    });
                } catch (ReflectiveOperationException ignored) {
                    // unknown event shape: the skin shows after the next restart instead
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, marker, EventPriority.MONITOR, executor, plugin, true);
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().warning("FancyNpcs SkinGeneratedEvent missing: cast skins show after the next restart.");
        }
    }
}
