package de.aetherion.mining;

import de.aetherion.mining.veins.VeinsCommand;
import de.aetherion.mining.veins.VeinsListener;
import de.aetherion.mining.veins.VeinsNpcs;
import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

public class AetherionMining extends JavaPlugin {

    private static AetherionMining instance;
    private WorldGuardPlugin worldGuard;
    private VeinsWorld veins;
    private de.aetherion.core.api.MiningAccess miningAccess;
    private de.aetherion.mining.isle.MineIsle isle;
    private de.aetherion.mining.titan.TitanWorld titan;
    private de.aetherion.mining.titan.Jawcamp camp;

    @Override
    public void onEnable() {
        instance = this;
        worldGuard = WorldGuardPlugin.inst();
        saveDefaultConfig();
        // Additive: merge new default keys (Mining Eldervale, Amethyst Mine) into an existing
        // config.yml without touching values that are already set.
        getConfig().options().copyDefaults(true);
        saveConfig();

        veins = new VeinsWorld(this);
        de.aetherion.mining.titan.TitanItems.init(this);
        getServer().getPluginManager().registerEvents(new de.aetherion.mining.titan.TitanRunes(this), this);
        VeinsNpcs npcs = new VeinsNpcs(this);
        veins.bind(npcs);
        // The mine server (mmo-m) runs the Amethyst Mine with mmo-r's config copy; the Eldervale isle stays on mmo-r.
        if (veins.mineServer()) {
            de.aetherion.mining.isle.MineWorld.isleAway(this, true);
            new de.aetherion.mining.veins.MinesHost(this, veins);
            if (getConfig().getBoolean("titan.enabled", true)) {
                // The Swallowed Isles: Jawcamp in the main world, Ossarath's body regrown every morning.
                titan = new de.aetherion.mining.titan.TitanWorld(this);
                de.aetherion.mining.titan.Heartforge forge = new de.aetherion.mining.titan.Heartforge();
                getServer().getPluginManager().registerEvents(forge, this);
                camp = new de.aetherion.mining.titan.Jawcamp(this, titan, forge);
                titan.bind(camp);
                de.aetherion.mining.titan.Jawcamp hub = camp;
                veins.bindCamp(() -> hub.ready() ? hub.spawn() : null);
                getServer().getPluginManager().registerEvents(camp, this);
                getServer().getPluginManager().registerEvents(new de.aetherion.mining.titan.TitanRelics(this, titan), this);
                getServer().getPluginManager().registerEvents(
                        new de.aetherion.mining.titan.TitanListener(this, titan, camp), this);
            }
        }

        // Mining Eldervale first: its break router must see the strike before MiningListener seals it.
        if (getConfig().getBoolean("mine-isle.enabled", true)) {
            try {
                isle = new de.aetherion.mining.isle.MineIsle(this);
                isle.start();
                // Mastery, ledger, forge rep and mine tallies travel with the character. Left registered on disable
                // so Core can still pack a player who is online while the server stops.
                de.aetherion.core.api.AetherServices.registerPlayerData(isle.profiles().part());
                PluginCommand mineisle = getCommand("mineisle");
                if (mineisle != null) {
                    de.aetherion.mining.isle.MineCommand isleCommand = new de.aetherion.mining.isle.MineCommand(isle);
                    mineisle.setExecutor(isleCommand);
                    mineisle.setTabCompleter(isleCommand);
                }
            } catch (LinkageError error) {
                isle = null;
                getLogger().warning("Mining Eldervale off: AetherionItems missing or too old (" + error.getClass().getSimpleName() + ").");
            }
        }

        getServer().getPluginManager().registerEvents(new MiningListener(), this);
        getServer().getPluginManager().registerEvents(new SharedWorldGuard(), this);
        getServer().getPluginManager().registerEvents(new VeinsListener(this, veins, npcs), this);

        VeinsCommand command = new VeinsCommand(this, veins, npcs);
        PluginCommand amethyst = getCommand("amethyst");
        if (amethyst != null) {
            amethyst.setExecutor(command);
            amethyst.setTabCompleter(command);
        }
        de.aetherion.mining.titan.TitanCommand titanCommand = new de.aetherion.mining.titan.TitanCommand(this, veins);
        for (String name : new String[]{"jawcamp", "titan"}) {
            PluginCommand titanCmd = getCommand(name);
            if (titanCmd != null) {
                titanCmd.setExecutor(titanCommand);
                titanCmd.setTabCompleter(titanCommand);
            }
        }
        PluginCommand deepmines = getCommand("deepmines");
        if (deepmines != null) {
            deepmines.setExecutor(command);
            deepmines.setTabCompleter(command);
        }

        getServer().getScheduler().runTask(this, () -> {
            veins.ensureLoaded();
            npcs.loadEntrance();
            if (titan != null) {
                camp.start();
                titan.start();
            }
            if (getConfig().getBoolean("eldervale.auto-paste-once", false)) {
                getConfig().set("eldervale.auto-paste-once", false);
                saveConfig();
                getLogger().warning("eldervale.auto-paste-once triggered — pasting Mining Eldervale Island…");
                de.aetherion.mining.island.EldervalePaste.paste(this, getServer().getConsoleSender());
            }
        });
        getServer().getScheduler().runTaskTimer(this, veins::tickReset, 20L * 60L, 20L * 60L * 5L);
        getLogger().info(veins.hostedHere()
                ? "Amethyst Area dig world ready. Players: /amethyst. Admin: /deepmines."
                : "The Amethyst Mine runs on the mine server: /amethyst and the Foreman send players there.");
        miningAccess = new de.aetherion.mining.api.MiningAccessImpl();
        de.aetherion.core.api.AetherServices.registerMining(miningAccess);
    }

    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        String name = getConfig().getString("veins.world", "aether_veins");
        if (worldName != null && worldName.equalsIgnoreCase(name)) {
            return new de.aetherion.mining.veins.VeinsChunkGenerator(
                    Math.max(16, getConfig().getInt("veins.radius", 250)),
                    getConfig().getInt("veins.hub-y", 220)
            );
        }
        return null;
    }

    @Override
    public void onDisable() {
        if (titan != null) {
            titan.shutdown();
            camp.shutdown();
        }
        if (isle != null) {
            isle.shutdown();
            isle = null;
        }
        if (veins != null) {
            veins.saveData();
        }
        if (miningAccess != null) {
            de.aetherion.core.api.AetherServices.clearMining(miningAccess);
            miningAccess = null;
        }
        getLogger().info("AetherionMining beendet!");
        instance = null;
    }

    public static AetherionMining getInstance() {
        return instance;
    }

    public WorldGuardPlugin getWorldGuard() {
        return worldGuard;
    }

    public VeinsWorld getVeins() {
        return veins;
    }

    /** The Swallowed Isles, or {@code null} off the mine server. */
    public de.aetherion.mining.titan.TitanWorld getTitan() {
        return titan;
    }

    /** Mining Eldervale router, or {@code null} when disabled. */
    public de.aetherion.mining.isle.MineIsle getIsle() {
        return isle;
    }
}
