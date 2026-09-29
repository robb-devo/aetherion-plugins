package de.aetherion.quests.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;

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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Anonymous cast skins for living NPCs.
 * <p>
 * Every skin is an original PNG painted for Aetherion ({@code docs/npc/skins/paint_skins.py}),
 * bundled at {@code skins/cast/<id>.png}. No player accounts, no creator skins.
 * <p>
 * Signing: Minecraft only renders Mojang-signed textures, so the PNG goes through FancyNpcs'
 * own MineSkin upload ({@code SkinManager#getByFile}) on the live server. The signed result
 * is cached in {@code skins-signed.yml} so a restart never re-uploads. Until a skin is signed
 * the NPC keeps its dyed-leather outfit on a default body — never somebody else's face.
 */
public final class LivingNpcSkins {

    /** Sub-folder under {@code plugins/FancyNpcs/skins/}. */
    private static final String FOLDER = "aetherion";

    /** Cast skins painted with slim (3px) arms. */
    private static final Set<String> SLIM = Set.of("ledger", "preset_mystic", "preset_townsfolk");

    /** Every bundled cast skin (placed NPCs + editor presets). */
    private static final Set<String> CAST = Set.of(
            "egon", "lumberjack", "quartermaster", "craftsman", "foreman", "booster_tutor", "ledger",
            "farmer", "lark", "fisher", "fishmonger", "vex", "rite_keeper", "arena_proctor",
            "farm_isle_guide", "surveyor", "vince", "bar_whisper", "eldervale_welcome", "eldervale_upgrade",
            "merchant", "isle_clerk", "forage_pad_guide", "liquidator", "canopy_clerk", "root_cellar",
            "town_crier", "street_sweeper", "lamp_lighter",
            "preset_worker", "preset_sailor", "preset_farmer", "preset_guard", "preset_scholar",
            "preset_mystic", "preset_scout", "preset_rogue", "preset_miner", "preset_townsfolk"
    );

    /**
     * Old editor preset defaults were well-known creators' accounts. Saved editor NPCs that
     * still carry one of those names are routed to the matching anonymous preset instead.
     */
    private static final Map<String, String> LEGACY_PRESET_SKINS = Map.of(
            "grian", "preset_worker",
            "mumbojumbo", "preset_sailor",
            "goodtimeswithscar", "preset_farmer",
            "impulsesv", "preset_guard",
            "geminitay", "preset_scholar",
            "pearlescentmoon", "preset_mystic",
            "ethoslab", "preset_scout",
            "vintagebeef", "preset_rogue",
            "docm77", "preset_miner",
            "alex", "preset_townsfolk"
    );

    public static final String TOKEN_PREFIX = "aetherion:";

    private static LivingNpcSkins instance;

    private final AetherionQuests plugin;
    private final File cacheFile;
    private final YamlConfiguration cache;
    private Consumer<String> onSigned = id -> { };

    private LivingNpcSkins(AetherionQuests plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "skins-signed.yml");
        this.cache = cacheFile.exists() ? YamlConfiguration.loadConfiguration(cacheFile) : new YamlConfiguration();
    }

    public static LivingNpcSkins start(AetherionQuests plugin) {
        instance = new LivingNpcSkins(plugin);
        instance.extract();
        instance.listen();
        return instance;
    }

    public static LivingNpcSkins get() {
        return instance;
    }

    /** Called with the cast id whenever a skin finishes signing (placeholder armour comes off). */
    public void onSigned(Consumer<String> callback) {
        this.onSigned = callback == null ? id -> { } : callback;
    }

    public static boolean hasCastSkin(String castId) {
        return castId != null && CAST.contains(castId.toLowerCase(Locale.ROOT));
    }

    /** Editor preset token / legacy creator name → cast id, else null (explicit username). */
    public static String castIdForEditorSkin(String skin) {
        if (skin == null || skin.isBlank()) {
            return null;
        }
        String s = skin.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith(TOKEN_PREFIX)) {
            String id = s.substring(TOKEN_PREFIX.length()).toLowerCase(Locale.ROOT);
            return hasCastSkin(id) ? id : null;
        }
        return LEGACY_PRESET_SKINS.get(s.toLowerCase(Locale.ROOT));
    }

    public static boolean slim(String castId) {
        return castId != null && SLIM.contains(castId.toLowerCase(Locale.ROOT));
    }

    private static String identifier(String castId) {
        return FOLDER + "/" + castId.toLowerCase(Locale.ROOT) + ".png";
    }

    public boolean isSigned(String castId) {
        return castId != null && cache.isString("skins." + castId.toLowerCase(Locale.ROOT) + ".value");
    }

    /**
     * Put the cast skin on {@code npcData} (before create/spawn, or live — caller respawns).
     *
     * @return true when a signed texture is on the data right now
     */
    public boolean apply(Object npcData, String castId) {
        if (npcData == null || !hasCastSkin(castId) || !fancyReady()) {
            return false;
        }
        String id = castId.toLowerCase(Locale.ROOT);
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            Object variant = variant(variantClass, id);
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
                // Returns cached texture straight away, or queues a MineSkin upload and
                // returns an identifier-only SkinData that FancyNpcs fills on SkinGeneratedEvent.
                skin = manager.getClass().getMethod("getByFile", String.class, variantClass)
                        .invoke(manager, identifier(id), variant);
                if (skin == null) {
                    return false;
                }
                remember(id, skin);
            }
            npcData.getClass().getMethod("setSkinData", skinDataClass).invoke(npcData, skin);
            Object has = skin.getClass().getMethod("hasTexture").invoke(skin);
            return Boolean.TRUE.equals(has);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            plugin.getLogger().fine("Cast skin " + id + " not applied: " + root.getMessage());
            return false;
        }
    }

    private Object variant(Class<?> variantClass, String castId) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object v = Enum.valueOf((Class) variantClass.asSubclass(Enum.class), slim(castId) ? "SLIM" : "AUTO");
        return v;
    }

    private void remember(String castId, Object skin) {
        try {
            Object value = skin.getClass().getMethod("getTextureValue").invoke(skin);
            Object signature = skin.getClass().getMethod("getTextureSignature").invoke(skin);
            if (value instanceof String v && signature instanceof String s && !v.isBlank() && !s.isBlank()) {
                cache.set("skins." + castId + ".value", v);
                cache.set("skins." + castId + ".signature", s);
                cache.set("skins." + castId + ".source", "painted for Aetherion (docs/npc/skins/paint_skins.py) via MineSkin");
                save();
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void save() {
        String data = cache.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(cacheFile.toPath(), data);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save skins-signed.yml: " + ex.getMessage());
            }
        });
    }

    /** "12/36 signed" for admin status. */
    public String status() {
        long signed = CAST.stream().filter(this::isSigned).count();
        return signed + "/" + CAST.size() + " cast skins signed";
    }

    public Set<String> castIds() {
        return CAST;
    }

    /* ------------------------------------------------------------------ plumbing */

    private boolean fancyReady() {
        return FancyNpcFacade.isAvailable();
    }

    /** Copy bundled PNGs to plugins/FancyNpcs/skins/aetherion/ (FancyNpcs reads files there). */
    private void extract() {
        Plugin fancy = Bukkit.getPluginManager().getPlugin(FancyNpcFacade.PLUGIN_NAME);
        File base = fancy != null ? new File(fancy.getDataFolder(), "skins") : new File("plugins/FancyNpcs/skins");
        File dir = new File(base, FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create " + dir.getPath());
            return;
        }
        int written = 0;
        for (String id : CAST) {
            File out = new File(dir, id + ".png");
            try (InputStream in = plugin.getResource("skins/cast/" + id + ".png")) {
                if (in == null) {
                    continue;
                }
                byte[] bytes = in.readAllBytes();
                if (out.exists() && Arrays.equals(Files.readAllBytes(out.toPath()), bytes)) {
                    continue;
                }
                Files.write(out.toPath(), bytes);
                written++;
                // Repainted file: the old signature belongs to the old pixels.
                cache.set("skins." + id, null);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not extract cast skin " + id + ": " + ex.getMessage());
            }
        }
        if (written > 0) {
            save();
            plugin.getLogger().info("Cast skins: wrote " + written + " PNG(s) to " + dir.getPath());
        }
    }

    /** Listen for FancyNpcs' SkinGeneratedEvent (reflection — no compile link to FancyNpcs). */
    private void listen() {
        if (!fancyReady()) {
            return;
        }
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
                    Object id = event.getClass().getMethod("getId").invoke(event);
                    Object skin = event.getClass().getMethod("getSkin").invoke(event);
                    if (!(id instanceof String ident) || skin == null) {
                        return;
                    }
                    String norm = ident.replace('\\', '/').toLowerCase(Locale.ROOT);
                    int slash = norm.lastIndexOf(FOLDER + "/");
                    if (slash < 0 || !norm.endsWith(".png")) {
                        return;
                    }
                    String castId = norm.substring(slash + FOLDER.length() + 1, norm.length() - 4);
                    if (!hasCastSkin(castId)) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        remember(castId, skin);
                        plugin.getLogger().info("Cast skin signed: " + castId + " (" + status() + ")");
                        onSigned.accept(castId);
                    });
                } catch (ReflectiveOperationException ignored) {
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, marker, EventPriority.MONITOR, executor, plugin, true);
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyNpcs SkinGeneratedEvent missing — cast skins sign on next restart only.");
        }
    }
}
