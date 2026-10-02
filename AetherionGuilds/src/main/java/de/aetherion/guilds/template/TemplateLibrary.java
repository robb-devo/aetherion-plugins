package de.aetherion.guilds.template;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island starters, structures and guild-project stages as Sponge schematics.
 *
 * <p>Bundled copies live in the jar under {@code templates/}. On enable they are extracted once to
 * {@code plugins/AetherionGuilds/templates/} (existing files are never overwritten), and that folder wins on
 * load. So a template can be re-authored in-game with WorldEdit ({@code //schem save}) and dropped in there;
 * {@code /island admin reload-templates} picks it up. Anchor rule: y 0 = ground layer, front = south.
 *
 * <p>When a bundled template is revised, an extracted copy that is still byte-for-byte an older bundled version
 * (its SHA-256 is listed in {@link #RETIRED}) is replaced by the new one; a copy someone re-authored is kept.
 */
public final class TemplateLibrary {

    public static final String[] BUNDLED = {
            "starter_grove", "starter_quarry", "starter_tide", "starter_guild",
            "sm_storage_hut", "sm_workshop", "sm_depot", "sm_mill", "sm_forge", "sm_quarry_housing", "sm_quarry_rig",
            "sm_storage_hut_2", "sm_storage_hut_3", "sm_depot_2", "sm_mill_2", "sm_forge_2",
            "st_storage_hut", "st_workshop", "st_depot", "st_mill", "st_forge", "st_quarry_housing",
            "gp_site_hall", "gp_site_beacon",
            "gp_hall_1", "gp_hall_2", "gp_hall_3",
            "gp_beacon_1", "gp_beacon_2", "gp_beacon_3"
    };

    /** SHA-256 of earlier bundled versions (the 31-wide starters of the first highlight build + signed yard posts). */
    private static final Map<String, Set<String>> RETIRED = Map.of(
            "starter_grove", Set.of(
                    "2dbc5a3217d19af433073c239d1b998d46df2ca24b9b94d433f7e8cdc81cc2a6",
                    "1b78f8a20a7423ab4ccc7d6035570765564975961939b2c69a4d0cd6f7ac2f22"),
            "starter_quarry", Set.of(
                    "6d2ea0fbb5757a7ea8f410cc4007b8791864fb569f04e1ba3997a5accd799738",
                    "2f7182681eb34b700293e0145d07dd24976734c2184024ab48fae29582da7e33"),
            "starter_tide", Set.of(
                    "67ed6bbcdd18970e0e7a0acb52900449ac61d63b7eaca522fed4eb64e5947717",
                    "e982f40bcc7ac7a1ad3e2bfe6d96c973218ed0efacef72f4baf12b7ec76ba733"),
            "starter_guild", Set.of("0ef80f7fb5e2d6bd44366a256943df0421b7efc76005e04d38e738d74779a14f")
    );

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<String, Optional<Template>> cache = new ConcurrentHashMap<>();

    public TemplateLibrary(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "templates");
    }

    public void extractDefaults() {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
            return;
        }
        int written = 0;
        int updated = 0;
        for (String id : BUNDLED) {
            File target = new File(folder, id + ".schem");
            if (target.exists()) {
                if (!RETIRED.getOrDefault(id, Set.of()).contains(sha256(target))) {
                    continue;
                }
                updated++;
            }
            try (InputStream in = plugin.getResource("templates/" + id + ".schem")) {
                if (in == null) {
                    plugin.getLogger().warning("Bundled template missing from jar: " + id);
                    continue;
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                written++;
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not extract template " + id + ": " + exception.getMessage());
            }
        }
        if (written > 0) {
            plugin.getLogger().info("Extracted " + written + " island templates to " + folder.getName() + "/"
                    + (updated > 0 ? " (" + updated + " untouched older versions updated)." : "."));
        }
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "";
        }
    }

    /** The template, or null if it can't be read (callers fall back gracefully). */
    public Template get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(id, this::load).orElse(null);
    }

    public void reload() {
        cache.clear();
    }

    public int loadedCount() {
        int count = 0;
        for (Optional<Template> template : cache.values()) {
            if (template.isPresent()) {
                count++;
            }
        }
        return count;
    }

    private Optional<Template> load(String id) {
        File file = new File(folder, id + ".schem");
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                return Optional.of(Template.read(id, in));
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().warning("Template " + file.getName() + " is unreadable ("
                        + exception.getMessage() + "); using the bundled copy.");
            }
        }
        try (InputStream in = plugin.getResource("templates/" + id + ".schem")) {
            if (in == null) {
                plugin.getLogger().warning("No template named " + id + ".");
                return Optional.empty();
            }
            return Optional.of(Template.read(id, in));
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Bundled template " + id + " is unreadable: " + exception.getMessage());
            return Optional.empty();
        }
    }
}
