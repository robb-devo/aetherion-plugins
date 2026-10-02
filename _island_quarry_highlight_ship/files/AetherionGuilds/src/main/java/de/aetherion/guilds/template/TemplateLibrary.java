package de.aetherion.guilds.template;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island starters, structures and guild-project stages as Sponge schematics.
 *
 * <p>Bundled copies live in the jar under {@code templates/}. On enable they are extracted once to
 * {@code plugins/AetherionGuilds/templates/} (existing files are never overwritten), and that folder wins on
 * load. So a template can be re-authored in-game with WorldEdit ({@code //schem save}) and dropped in there;
 * {@code /island admin reload-templates} picks it up. Anchor rule: y 0 = ground layer, front = south.
 */
public final class TemplateLibrary {

    public static final String[] BUNDLED = {
            "starter_grove", "starter_quarry", "starter_tide", "starter_guild",
            "st_storage_hut", "st_workshop", "st_depot", "st_mill", "st_forge", "st_quarry_housing",
            "gp_site_hall", "gp_site_beacon",
            "gp_hall_1", "gp_hall_2", "gp_hall_3",
            "gp_beacon_1", "gp_beacon_2", "gp_beacon_3"
    };

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
        for (String id : BUNDLED) {
            File target = new File(folder, id + ".schem");
            if (target.exists()) {
                continue;
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
            plugin.getLogger().info("Extracted " + written + " island templates to " + folder.getName() + "/.");
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
