package de.aetherion.tutorial;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.network.CharacterHost;
import de.aetherion.core.network.ServerNames;
import de.aetherion.tutorial.isle.Isle;
import de.aetherion.tutorial.main.MainArrival;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;

/**
 * Morningbell, the tutorial isle. One jar, two roles:
 * <ul>
 *     <li><b>isle</b> on the backend that carries the world {@code morningbell}: the lessons, the cast, the boss,
 *     the ferry;</li>
 *     <li><b>main</b> on the main world: welcomes whoever steps off that ferry, sends back whoever left the island
 *     half-way, and lets players who already have a character visit the island ({@code /tutorial}).</li>
 * </ul>
 * Where a player stands with the tutorial is kept in {@link TutorialStore}, a folder every backend shares.
 */
public final class TutorialPlugin extends JavaPlugin {

    public static final String ADMIN = "aetherion.tutorial.admin";

    private TutorialStore store;
    private Isle isle;
    private MainArrival arrival;
    private Sandbox sandbox;
    private String role = "off";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new TutorialStore(this, storeDir());
        String here = here();
        String configured = getConfig().getString("role", "auto");
        configured = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        World world = Bukkit.getWorld(getConfig().getString("world", "morningbell"));
        if (configured.equals("isle") || (configured.equals("auto") && world != null)) {
            if (world == null) {
                getLogger().severe("role: isle, but the world '" + getConfig().getString("world", "morningbell")
                        + "' is not loaded. Nothing runs.");
            } else {
                isle = Isle.open(this, world);
                if (isle != null) {
                    role = "isle";
                    sandbox = new Sandbox(this);
                    Bukkit.getPluginManager().registerEvents(sandbox, this);
                    armGuestPass();
                }
            }
        } else if (configured.equals("main") || (configured.equals("auto") && here.equalsIgnoreCase(mainServer()))) {
            arrival = new MainArrival(this);
            Bukkit.getPluginManager().registerEvents(arrival, this);
            role = "main";
        }
        TutorialCommand command = new TutorialCommand(this);
        for (String name : new String[] {"morningbell", "tutorial"}) {
            PluginCommand registered = getCommand(name);
            if (registered != null) {
                registered.setExecutor(command);
                registered.setTabCompleter(command);
            }
        }
        getLogger().info("Morningbell tutorial: role " + role + " on '" + here + "', store " + store.directory().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (isle != null) {
            isle.close();
            isle = null;
        }
    }

    public TutorialStore store() {
        return store;
    }

    /** The isle role, or null on any other backend. */
    public Isle isle() {
        return isle;
    }

    public MainArrival arrival() {
        return arrival;
    }

    Sandbox sandbox() {
        return sandbox;
    }

    public String role() {
        return role;
    }

    public String mainServer() {
        String name = getConfig().getString("main-server", ServerNames.MAIN);
        return name == null || name.isBlank() ? ServerNames.MAIN : name.trim().toLowerCase(Locale.ROOT);
    }

    public String isleServer() {
        String name = getConfig().getString("isle-server", ServerNames.TUTORIAL);
        return name == null || name.isBlank() ? ServerNames.TUTORIAL : name.trim().toLowerCase(Locale.ROOT);
    }

    /** This backend's Velocity name as Core knows it. */
    public String here() {
        try {
            AetherionCore core = AetherionCore.get();
            return core == null || core.router() == null ? "unknown" : core.router().here();
        } catch (LinkageError | RuntimeException ex) {
            return "unknown";
        }
    }

    private File storeDir() {
        String configured = getConfig().getString("store-dir", "");
        if (configured != null && !configured.isBlank()) {
            return new File(configured.trim());
        }
        File transfer = ServerNames.snapshotDir(AetherionCore.get());
        File shared = transfer.getParentFile();
        return new File(shared != null ? shared : getDataFolder(), "tutorial");
    }

    // ------------------------------------------------------------------ crossings

    private CharacterTransport transport() {
        AetherionCore core = AetherionCore.get();
        return core == null || core.router() == null ? null : core.router().transport();
    }

    /** The character hop to the main world. False when nothing left this server. */
    public boolean toMain(Player player, String departLine) {
        return hop(player, mainServer(), departLine);
    }

    /** The character hop to the isle (a newcomer who slipped onto the main world half-way). */
    public boolean toIsle(Player player, String departLine) {
        return hop(player, isleServer(), departLine);
    }

    private boolean hop(Player player, String server, String departLine) {
        CharacterTransport transport = transport();
        if (transport == null || player == null || !player.isOnline()) {
            return false;
        }
        try {
            return transport.send(player, server, "tutorial", departLine);
        } catch (RuntimeException ex) {
            getLogger().warning("Crossing to " + server + " failed for " + player.getName() + ": " + ex);
            return false;
        }
    }

    /**
     * Tells Core who comes to the isle as a visitor: such a player is let in without their character, which stays
     * untouched wherever it is (see {@link CharacterHost#guestPass}). Only while their sandbox here is really this
     * server's own: on a backend that shares its player files, a visitor's lesson would write into the real ones.
     */
    private void armGuestPass() {
        AetherionCore core = AetherionCore.get();
        CharacterHost host = core == null ? null : core.characterHost();
        if (host == null) {
            getLogger().warning("Core's character host is not armed here: visits (/tutorial) are off, newcomers still work"
                    + " only if network.character.host is on.");
            return;
        }
        if (!sandbox.isolated()) {
            getLogger().severe("This backend shares player files with other servers: visits (/tutorial) are OFF here.");
            return;
        }
        host.guestPass(id -> store.visiting(id));
        getLogger().info("Visits are on: players with a character walk the isle without it.");
    }
}
