package de.aetherion.tutorial.isle;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.tutorial.TutorialPlugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The cast's own faces. Every skin is an original PNG painted for Morningbell ({@code docs/npc/skins}), bundled
 * under {@code skins/<id>.png}. Minecraft only shows Mojang-signed textures, so a PNG goes through FancyNpcs' own
 * MineSkin upload once; the signed result is kept in {@code skins-signed.yml} (a signed set ships with the jar, so a
 * fresh server uploads nothing). Until a skin is signed the body wears the default face.
 */
final class Skins {

    private static final String FOLDER = "morningbell";
    /** Painted with slim arms. */
    private static final Set<String> SLIM = Set.of(Cast.ODA, Cast.GRETT, Cast.HILDE);

    private final TutorialPlugin plugin;
    private final File cacheFile;
    private final YamlConfiguration cache;
    private Consumer<String> onSigned = id -> {
    };

    Skins(TutorialPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "skins-signed.yml");
        YamlConfiguration loaded = cacheFile.isFile() ? YamlConfiguration.loadConfiguration(cacheFile) : new YamlConfiguration();
        // what the jar brings (signed on the staging server) fills every gap
        try (InputStream in = plugin.getResource("skins-signed.yml")) {
            if (in != null) {
                YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String id : Cast.ALL.keySet()) {
                    String hash = shipped.getString("skins." + id + ".png");
                    if (shipped.isString("skins." + id + ".value") && !loaded.isString("skins." + id + ".value")) {
                        loaded.set("skins." + id + ".value", shipped.getString("skins." + id + ".value"));
                        loaded.set("skins." + id + ".signature", shipped.getString("skins." + id + ".signature"));
                        loaded.set("skins." + id + ".png", hash);
                    }
                }
            }
        } catch (IOException ignored) {
            // no shipped set: everything is signed on this server
        }
        this.cache = loaded;
        extract();
        listen();
    }

    void onSigned(Consumer<String> callback) {
        this.onSigned = callback == null ? id -> {
        } : callback;
    }

    private static String identifier(String id) {
        return FOLDER + "/" + id + ".png";
    }

    /** Puts the cast skin on {@code npcData}. True when a signed texture is on it now. */
    boolean apply(Object npcData, String id) {
        if (npcData == null || id == null || !FancyNpcFacade.isAvailable() || plugin.getResource("skins/" + id + ".png") == null) {
            return false;
        }
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object variant = Enum.valueOf((Class) variantClass.asSubclass(Enum.class), SLIM.contains(id) ? "SLIM" : "AUTO");
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skin;
            String value = cache.getString("skins." + id + ".value");
            String signature = cache.getString("skins." + id + ".signature");
            if (value != null && signature != null) {
                skin = skinDataClass.getConstructor(String.class, variantClass, String.class, String.class)
                        .newInstance(identifier(id), variant, value, signature);
            } else {
                Object api = FancyNpcFacade.plugin();
                Object manager = api.getClass().getMethod("getSkinManager").invoke(api);
                // the cached texture at once, or an upload is queued and the skin arrives with SkinGeneratedEvent
                skin = manager.getClass().getMethod("getByFile", String.class, variantClass)
                        .invoke(manager, identifier(id), variant);
                if (skin == null) {
                    return false;
                }
                remember(id, skin);
            }
            npcData.getClass().getMethod("setSkinData", skinDataClass).invoke(npcData, skin);
            return Boolean.TRUE.equals(skin.getClass().getMethod("hasTexture").invoke(skin));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("Skin of " + id + " not applied: " + ex);
            return false;
        }
    }

    private void remember(String id, Object skin) {
        try {
            Object value = skin.getClass().getMethod("getTextureValue").invoke(skin);
            Object signature = skin.getClass().getMethod("getTextureSignature").invoke(skin);
            if (value instanceof String v && signature instanceof String s && !v.isBlank() && !s.isBlank()) {
                cache.set("skins." + id + ".value", v);
                cache.set("skins." + id + ".signature", s);
                save();
            }
        } catch (ReflectiveOperationException ignored) {
            // not signed yet
        }
    }

    private void save() {
        String data = cache.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(cacheFile.toPath(), data, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save skins-signed.yml: " + ex.getMessage());
            }
        });
    }

    /** Copies the bundled PNGs to plugins/FancyNpcs/skins/morningbell/ (FancyNpcs reads files from there). */
    private void extract() {
        Plugin fancy = Bukkit.getPluginManager().getPlugin(FancyNpcFacade.PLUGIN_NAME);
        if (fancy == null) {
            return;
        }
        File dir = new File(new File(fancy.getDataFolder(), "skins"), FOLDER);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create " + dir.getPath());
            return;
        }
        boolean changed = false;
        for (String id : Cast.ALL.keySet()) {
            try (InputStream in = plugin.getResource("skins/" + id + ".png")) {
                if (in == null) {
                    continue;
                }
                byte[] bytes = in.readAllBytes();
                String hash = Integer.toHexString(Arrays.hashCode(bytes));
                File out = new File(dir, id + ".png");
                if (!out.isFile() || !Arrays.equals(Files.readAllBytes(out.toPath()), bytes)) {
                    Files.write(out.toPath(), bytes);
                }
                String signedFor = cache.getString("skins." + id + ".png");
                if (cache.isString("skins." + id + ".value") && signedFor != null && !signedFor.equals(hash)) {
                    // repainted: the old signature belongs to the old pixels
                    cache.set("skins." + id + ".value", null);
                    cache.set("skins." + id + ".signature", null);
                    changed = true;
                }
                if (!hash.equals(signedFor)) {
                    cache.set("skins." + id + ".png", hash);
                    changed = true;
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not write the skin of " + id + ": " + ex.getMessage());
            }
        }
        if (changed) {
            save();
        }
    }

    /** FancyNpcs tells when an upload came back signed (reflection: no compile link to FancyNpcs). */
    private void listen() {
        if (!FancyNpcFacade.isAvailable()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName("de.oliver.fancynpcs.api.skins.SkinGeneratedEvent");
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Object ident = event.getClass().getMethod("getId").invoke(event);
                    Object skin = event.getClass().getMethod("getSkin").invoke(event);
                    if (!(ident instanceof String name) || skin == null) {
                        return;
                    }
                    String norm = name.replace('\\', '/').toLowerCase(Locale.ROOT);
                    int slash = norm.lastIndexOf(FOLDER + "/");
                    if (slash < 0 || !norm.endsWith(".png")) {
                        return;
                    }
                    String id = norm.substring(slash + FOLDER.length() + 1, norm.length() - 4);
                    if (!Cast.ALL.containsKey(id)) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        remember(id, skin);
                        plugin.getLogger().info("Cast skin signed: " + id);
                        onSigned.accept(id);
                    });
                } catch (ReflectiveOperationException ignored) {
                    // a FancyNpcs build with another event shape: the skin shows after the next restart
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.MONITOR, executor, plugin, true);
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().info("FancyNpcs has no SkinGeneratedEvent: cast skins show after the next restart.");
        }
    }
}
