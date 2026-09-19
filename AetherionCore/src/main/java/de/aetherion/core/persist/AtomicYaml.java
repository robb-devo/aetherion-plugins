package de.aetherion.core.persist;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Crash-safe YAML / text replace: write {@code name.tmp}, then rename over the target.
 * On POSIX the rename is atomic so a mid-write kill cannot truncate the live file.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    public static File tempFile(File target) {
        return new File(target.getParentFile(), target.getName() + ".tmp");
    }

    /**
     * If a previous save died after writing the temp file but before rename,
     * and the live file is missing or empty, promote the temp file.
     */
    public static void recoverTemp(File target, Logger log) {
        if (target == null) {
            return;
        }
        File tmp = tempFile(target);
        if (!tmp.isFile() || tmp.length() == 0L) {
            return;
        }
        if (target.isFile() && target.length() > 0L) {
            return;
        }
        try {
            replaceAtomically(tmp, target);
            if (log != null) {
                log.info("Recovered unfinished write: " + target.getName());
            }
        } catch (IOException exception) {
            if (log != null) {
                log.warning("Could not recover " + tmp.getName() + ": " + exception.getMessage());
            }
        }
    }

    public static void save(YamlConfiguration config, File file) throws IOException {
        save(config, file, null);
    }

    public static void save(YamlConfiguration config, File file, Logger log) throws IOException {
        if (config == null || file == null) {
            throw new IOException("yaml save missing config or file");
        }
        File folder = file.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create folder: " + folder.getAbsolutePath());
        }
        File tmp = tempFile(file);
        config.save(tmp);
        replaceAtomically(tmp, file);
    }

    public static void writeString(File file, String raw) throws IOException {
        writeString(file, raw, StandardCharsets.UTF_8);
    }

    public static void writeString(File file, String raw, Charset charset) throws IOException {
        if (file == null) {
            throw new IOException("missing file");
        }
        if (raw == null) {
            raw = "";
        }
        File folder = file.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create folder: " + folder.getAbsolutePath());
        }
        File tmp = tempFile(file);
        Files.writeString(tmp.toPath(), raw, charset == null ? StandardCharsets.UTF_8 : charset);
        replaceAtomically(tmp, file);
    }

    public static void replaceAtomically(File tmp, File target) throws IOException {
        if (tmp == null || target == null) {
            throw new IOException("atomic replace missing path");
        }
        try {
            Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException unsupported) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
