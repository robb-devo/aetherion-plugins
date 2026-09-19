package de.aetherion.bossengine;

import de.aetherion.bossengine.api.BossEngineAPI;
import de.aetherion.bossengine.api.BossEngineAPIImpl;
import de.aetherion.bossengine.api.BossSpawnAccessImpl;
import de.aetherion.bossengine.command.BossCommand;
import de.aetherion.bossengine.config.YamlTemplateLoader;
import de.aetherion.bossengine.integration.AetherMobsBridge;
import de.aetherion.bossengine.integration.AetherionItemBridge;
import de.aetherion.bossengine.item.BossSpawnItemService;
import de.aetherion.bossengine.listener.BossCombatListener;
import de.aetherion.bossengine.listener.BossItemUseListener;
import de.aetherion.bossengine.listener.BossProjectileListener;
import de.aetherion.bossengine.listener.BossSpawnProtectListener;
import de.aetherion.bossengine.loot.LootFactory;
import de.aetherion.bossengine.loot.LootService;
import de.aetherion.bossengine.manager.BossManager;
import de.aetherion.bossengine.manager.SkillManager;
import de.aetherion.bossengine.manager.SpawnerManager;
import de.aetherion.bossengine.manager.TemplateManager;
import de.aetherion.bossengine.skill.SkillRegistry;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BossEngine extends JavaPlugin {

    private static BossEngine instance;

    private BossKeys keys;
    private SkillRegistry skillRegistry;
    private SkillManager skillManager;
    private TemplateManager templateManager;
    private BossManager bossManager;
    private SpawnerManager spawnerManager;
    private BossSpawnItemService spawnItemService;
    private AetherionItemBridge itemBridge;
    private AetherMobsBridge mobsBridge;
    private LootService lootService;
    private BossEngineAPI api;
    private BossSpawnAccessImpl spawnAccess;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (getConfig().getInt("tick-interval-ticks", 1) >= 10) {
            getConfig().set("tick-interval-ticks", 1);
            saveConfig();
        }
        saveResourceIfMissing("spawners.yml");
        saveResourceIfMissing("items.yml");
        // Never force-overwrite live boss YAMLs — that wiped arena/leash tweaks every restart.
        saveResourceIfMissing("bosses/aether_colossus.yml");
        saveResourceIfMissing("bosses/hollow_lurker.yml");
        saveResourceIfMissing("bosses/skuldugery.yml");
        saveResourceIfMissing("bosses/mcnugget.yml");
        saveResourceIfMissing("bosses/bridge_troll.yml");
        saveResourceIfMissing("bosses/squidward.yml");
        saveResourceIfMissing("bosses/aetherion.yml");
        saveResourceIfMissing("bosses/dungeon_sentinel.yml");
        saveResourceIfMissing("bosses/dungeon_frostbound.yml");
        saveResourceIfMissing("bosses/dungeon_aetherion.yml");
        saveResourceIfMissing("bosses/sir_balthazar.yml");
        saveResourceIfMissing("bosses/lobby_cleaner.yml");
        saveResourceIfMissing("bosses/sparky.yml");
        saveResourceIfMissing("bosses/baron_von_wurm.yml");
        saveResourceIfMissing("bosses/insolvent_wither.yml");
        saveResourceIfMissing("bosses/pathwarden.yml");

        keys = new BossKeys(this);
        skillRegistry = new SkillRegistry(getLogger());
        skillManager = new SkillManager(skillRegistry);
        templateManager = new TemplateManager(this, new YamlTemplateLoader(skillManager, getLogger()));
        itemBridge = new AetherionItemBridge(getLogger());
        mobsBridge = new AetherMobsBridge();
        lootService = new LootService(new LootFactory(itemBridge, mobsBridge));
        spawnItemService = new BossSpawnItemService(this, keys);

        templateManager.reload();
        spawnItemService.reload();

        bossManager = new BossManager(this, templateManager, skillManager, keys, lootService);
        spawnerManager = new SpawnerManager(this, bossManager);
        spawnerManager.reload();
        spawnerManager.stop();
        bossManager.start();
        getServer().getScheduler().runTask(this, () -> {
            bossManager.reclaimOrphans(spawnerManager);
            spawnerManager.start();
        });

        api = new BossEngineAPIImpl(bossManager, keys, spawnerManager);

        getServer().getPluginManager().registerEvents(
                new BossCombatListener(this, bossManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BossProjectileListener(keys),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BossItemUseListener(bossManager, spawnerManager, spawnItemService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BossSpawnProtectListener(keys),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.bossengine.listener.T2MechanicListener(bossManager, keys),
                this
        );
        getServer().getPluginManager().registerEvents(
                new de.aetherion.bossengine.listener.BossHudListener(bossManager.getHud()),
                this
        );
        getServer().getScheduler().runTask(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                de.aetherion.bossengine.skill.t2.T2Mechanics.clearInvert(player);
            }
        });
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
                if (bossManager != null) {
                    bossManager.absorbChunk(event.getChunk().getEntities(), spawnerManager);
                }
            }
        }, this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                if ("AetherionItems".equals(event.getPlugin().getName())) {
                    itemBridge.hook();
                }
            }
        }, this);

        hookWorldGuard();

        BossCommand command = new BossCommand(this);
        PluginCommand pluginCommand = getCommand("boss");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("BossEngine enabled. Templates: " + templateManager.getAll().size());
        spawnAccess = new BossSpawnAccessImpl(this);
        de.aetherion.core.api.AetherServices.registerBosses(spawnAccess);
        getServer().getScheduler().runTask(this, itemBridge::hook);
        if (itemBridge.isAvailable()) {
            getLogger().info("AetherionItems loot bridge ready.");
        }
        if (mobsBridge.isAvailable()) {
            getLogger().info("AetherMobs detected – pet entities will not be treated as bosses.");
        }
    }

    @Override
    public void onDisable() {
        if (spawnAccess != null) {
            de.aetherion.core.api.AetherServices.clearBosses(spawnAccess);
            spawnAccess = null;
        }
        if (spawnerManager != null) {
            spawnerManager.stop();
        }
        if (bossManager != null) {
            bossManager.stop();
        }
        getLogger().info("BossEngine disabled.");
    }

    public void reloadEngine() {
        reloadConfig();
        if (bossManager != null) {
            bossManager.stop();
        }
        if (spawnerManager != null) {
            spawnerManager.stop();
        }
        itemBridge.hook();
        templateManager.reload();
        spawnItemService.reload();
        spawnerManager.reload();
        spawnerManager.stop();
        bossManager.start();
        getServer().getScheduler().runTask(this, () -> {
            bossManager.reclaimOrphans(spawnerManager);
            spawnerManager.start();
        });
    }

    public static BossEngine getInstance() {
        return instance;
    }

    public static BossEngineAPI getAPI() {
        return instance == null ? null : instance.api;
    }

    public BossKeys getKeys() {
        return keys;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public BossSpawnItemService getSpawnItemService() {
        return spawnItemService;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public AetherionItemBridge getItemBridge() {
        return itemBridge;
    }

    public AetherMobsBridge getMobsBridge() {
        return mobsBridge;
    }

    public BossEngineAPI api() {
        return api;
    }

    private void hookWorldGuard() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().info("WorldGuard not found – spawn bypass hook skipped.");
            return;
        }
        try {
            Class<?> hook = Class.forName("de.aetherion.bossengine.integration.worldguard.WorldGuardHook");
            hook.getMethod("register", BossEngine.class).invoke(null, this);
        } catch (Exception exception) {
            getLogger().warning("Could not hook WorldGuard: " + exception.getMessage());
        }
    }

    private void saveResourceIfMissing(String path) {
        if (!new java.io.File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }
}
