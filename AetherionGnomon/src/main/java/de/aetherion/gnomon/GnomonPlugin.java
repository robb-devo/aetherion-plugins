package de.aetherion.gnomon;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.api.BossEngineAPI;
import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossScripts;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.encounter.GnomonScript;
import de.aetherion.gnomon.encounter.Party;
import de.aetherion.gnomon.render.Parts;
import de.aetherion.gnomon.world.TempBlocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GNOMON - dedicated encounter plugin. BossEngine keeps HP / phases / loot through the {@code BossScript} contract;
 * this plugin owns the body, the dial, the mind and every cleanup path.
 */
public final class GnomonPlugin extends JavaPlugin {

    public static final String TEMPLATE_ID = "gnomon";
    private static final Pattern VERSION = Pattern.compile("gnomon-version:\\s*(\\d+)");

    private GnomonConfig cfg;
    private final Map<UUID, Encounter> encounters = new LinkedHashMap<>();
    private final Set<UUID> debuggers = new HashSet<>();
    private Location pendingArena;
    private long pendingAt;

    /* ================================================================== lifecycle */

    @Override
    public void onLoad() {
        // onLoad runs for every plugin before any onEnable: BossEngine's template scan will already see the file.
        syncTemplate();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGnomonConfig();

        int restored = TempBlocks.recover(new File(getDataFolder(), "journal.yml"), getLogger());
        if (restored > 0) {
            getLogger().warning("[Gnomon] Restored " + restored + " temporary block(s) left by a crash.");
        }
        int swept = 0;
        for (World w : Bukkit.getWorlds()) {
            swept += Parts.sweep(w);
        }
        if (swept > 0) {
            getLogger().warning("[Gnomon] Removed " + swept + " orphaned display(s).");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (Party.marked(p)) {
                Party.restore(p);
            }
        }

        BossScripts.register(TEMPLATE_ID, instance -> new GnomonScript(this, instance));
        ensureTemplateLoaded();

        getServer().getPluginManager().registerEvents(new GuardListener(this), this);
        GnomonCommand command = new GnomonCommand(this);
        PluginCommand cmd = getCommand("gnomon");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        getLogger().info("[Gnomon] Ready. Arena: " + (cfg.arenaCenter() == null ? "not set (/gnomon setarena)" : describe(cfg.arenaCenter())));
    }

    @Override
    public void onDisable() {
        for (Encounter e : new ArrayList<>(encounters.values())) {
            abort(e, "plugin disable");
        }
        encounters.clear();
        BossScripts.unregister(TEMPLATE_ID);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (Party.marked(p)) {
                Party.restore(p);
            }
        }
    }

    public GnomonConfig cfg() {
        return cfg;
    }

    public void reloadGnomonConfig() {
        reloadConfig();
        cfg = new GnomonConfig(getConfig(), getLogger());
    }

    /* ================================================================== BossEngine template */

    /**
     * Copies the bundled template into BossEngine's folder when missing or older (by {@code gnomon-version}). The old
     * file is kept as {@code gnomon.yml.v<old>.bak} so live loot / HP edits are never lost.
     */
    private void syncTemplate() {
        File bossDir = new File(getDataFolder().getParentFile(), "BossEngine/bosses");
        File target = new File(bossDir, TEMPLATE_ID + ".yml");
        int bundled;
        try (InputStream in = getResource("gnomon-template.yml")) {
            bundled = versionOf(in);
        } catch (IOException e) {
            getLogger().warning("[Gnomon] Missing bundled template: " + e.getMessage());
            return;
        }
        int live = -1;
        if (target.isFile()) {
            try (InputStream in = new FileInputStream(target)) {
                live = versionOf(in);
            } catch (IOException e) {
                live = 0;
            }
        }
        if (live >= bundled) {
            return;
        }
        try {
            if (!bossDir.isDirectory() && !bossDir.mkdirs()) {
                getLogger().warning("[Gnomon] Could not create " + bossDir);
                return;
            }
            if (target.isFile()) {
                File backup = new File(bossDir, target.getName() + ".v" + live + ".bak");
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream in = getResource("gnomon-template.yml")) {
                if (in == null) {
                    return;
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            getLogger().info("[Gnomon] Installed BossEngine template " + target.getPath() + " (v" + bundled + ").");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "[Gnomon] Could not install BossEngine template", e);
        }
    }

    private static int versionOf(InputStream in) throws IOException {
        if (in == null) {
            return 0;
        }
        String head = new String(in.readNBytes(512), StandardCharsets.UTF_8);
        Matcher m = VERSION.matcher(head);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Hot-plug safety (e.g. plugin enabled after BossEngine already scanned): reload BossEngine's template list. */
    private void ensureTemplateLoaded() {
        BossEngine be = BossEngine.getInstance();
        if (be == null || be.getBossManager() == null) {
            return;
        }
        if (be.getBossManager().getTemplates().get(TEMPLATE_ID).isEmpty()) {
            syncTemplate();
            be.getBossManager().getTemplates().reload();
            getLogger().info("[Gnomon] BossEngine templates reloaded to pick up '" + TEMPLATE_ID + "'.");
        }
    }

    /* ================================================================== encounters */

    public Collection<Encounter> encounters() {
        return encounters.values();
    }

    /**
     * Starts a fight: BossEngine spawns the heart (hitbox), the script binds and asks {@link #open} for the arena.
     *
     * @return null on success, else a reason
     */
    public String start(Player initiator, Location center) {
        if (!encounters.isEmpty()) {
            return "A Gnomon fight is already running (/gnomon stop).";
        }
        BossEngineAPI api = BossEngine.getAPI();
        if (api == null) {
            return "BossEngine is not ready.";
        }
        if (!api.isKnownBoss(TEMPLATE_ID)) {
            ensureTemplateLoaded();
            if (!api.isKnownBoss(TEMPLATE_ID)) {
                return "BossEngine has no '" + TEMPLATE_ID + "' template (check plugins/BossEngine/bosses/gnomon.yml).";
            }
        }
        reloadGnomonConfig();
        pendingArena = center.clone();
        pendingAt = System.currentTimeMillis();
        Location spawn = center.clone().add(0, 3, 0);
        if (api.spawn(TEMPLATE_ID, spawn, SpawnCause.COMMAND, initiator).isEmpty()) {
            pendingArena = null;
            return "BossEngine refused the spawn (instance cap, WorldGuard deny-spawn or DeluxeHub mob block?).";
        }
        return null;
    }

    /** Called by {@link GnomonScript#onBind()}. */
    public Encounter open(BossInstance instance, GnomonScript script) {
        Location center = pendingArena != null && System.currentTimeMillis() - pendingAt < 10_000
                ? pendingArena
                : instance.getSpawnLocation();
        pendingArena = null;
        if (center == null || center.getWorld() == null) {
            return null;
        }
        try {
            Encounter e = new Encounter(this, cfg, instance, center);
            encounters.put(instance.getInstanceId(), e);
            getLogger().info("[Gnomon] Encounter opened at " + describe(center) + ".");
            return e;
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "[Gnomon] Could not open the encounter", ex);
            return null;
        }
    }

    /** BossEngine ended the instance (death paid, despawn, reload): clean up. */
    public void closed(Encounter e, String reason) {
        e.close(reason);
        encounters.values().removeIf(x -> x == e);
    }

    /** We end it: clean up and remove the BossEngine instance too. */
    public void abort(Encounter e, String reason) {
        e.close(reason);
        encounters.values().removeIf(x -> x == e);
        BossEngineAPI api = BossEngine.getAPI();
        BossInstance instance = e.instance();
        if (api != null && instance != null && instance.isEncounterActive()) {
            try {
                api.despawn(instance.getInstanceId());
            } catch (RuntimeException ex) {
                getLogger().log(Level.WARNING, "[Gnomon] BossEngine despawn failed", ex);
            }
        }
    }

    /* ================================================================== debug */

    public boolean toggleDebug(Player p) {
        if (!debuggers.remove(p.getUniqueId())) {
            debuggers.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public void debugHud(Encounter e) {
        if (debuggers.isEmpty()) {
            return;
        }
        String line = e.hud();
        for (UUID id : debuggers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(line));
            }
        }
    }

    public static String describe(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    public List<String> encounterIds() {
        List<String> out = new ArrayList<>();
        for (UUID id : encounters.keySet()) {
            out.add(id.toString());
        }
        return out;
    }
}
