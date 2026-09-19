package de.aetherion.items.progress;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.skill.SkillService;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionService {

    public static final int ISLAND_LEVEL = 20;
    public static final int GUILD_LEVEL = 75;

    public enum Flag {
        TRADER,
        WORKBENCH,
        ANVIL,
        SPAWN_UNLOCKER,
        SKILLS,
        PETS
    }

    private final AetherionItems plugin;
    private final File file;
    private final Map<UUID, EnumSet<Flag>> flags = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public ProgressionService(AetherionItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progress.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public boolean unlock(Player player, Flag flag) {
        if (player == null || flag == null) {
            return false;
        }
        EnumSet<Flag> set = flags.computeIfAbsent(player.getUniqueId(), ignored -> EnumSet.noneOf(Flag.class));
        if (!set.add(flag)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Unlock + toast. Returns true only on first unlock. */
    public boolean unlockWithToast(Player player, Flag flag, String title, String subtitle) {
        if (!unlock(player, flag)) {
            return false;
        }
        UnlockToast.show(player, title, subtitle);
        return true;
    }

    public boolean has(Player player, Flag flag) {
        if (player == null || flag == null) {
            return false;
        }
        EnumSet<Flag> set = flags.get(player.getUniqueId());
        return set != null && set.contains(flag);
    }

    public boolean skills(Player player) {
        return has(player, Flag.SKILLS);
    }

    public boolean storage(Player player) {
        return true;
    }

    public boolean pets(Player player) {
        return has(player, Flag.PETS);
    }

    public boolean stats(Player player) {
        return true;
    }

    public boolean loadouts(Player player) {
        return true;
    }

    public boolean shop(Player player) {
        return true;
    }

    public boolean bazaar(Player player) {
        return has(player, Flag.TRADER);
    }

    public boolean auction(Player player) {
        return has(player, Flag.TRADER);
    }

    public boolean collection(Player player) {
        CodexService codex = plugin.getCodex();
        return codex != null && codex.hasAnyBlock(player);
    }

    public boolean bestiary(Player player) {
        CodexService codex = plugin.getCodex();
        return codex != null && codex.hasAnyKill(player);
    }

    public boolean journal(Player player) {
        CodexService codex = plugin.getCodex();
        return codex != null && codex.hasAnyBossKill(player);
    }

    public boolean recipeBook(Player player) {
        return has(player, Flag.WORKBENCH);
    }

    public boolean craftingTable(Player player) {
        return has(player, Flag.WORKBENCH);
    }

    public boolean anvil(Player player) {
        return has(player, Flag.ANVIL);
    }

    public boolean island(Player player) {
        SkillService skills = plugin.getSkills();
        return skills != null && skills.accountLevel(player) >= ISLAND_LEVEL;
    }

    public boolean guild(Player player) {
        SkillService skills = plugin.getSkills();
        return skills != null && skills.accountLevel(player) >= GUILD_LEVEL;
    }

    public boolean spawns(Player player) {
        return has(player, Flag.SPAWN_UNLOCKER) || hubHasBonusSpawn(player);
    }

    public String hint(Flag flag) {
        return switch (flag) {
            case TRADER -> "§7Visit the trader once. He has a face for a reason.";
            case WORKBENCH -> "§7Market stall · Craftsman unlocks Manager → Crafting / Recipes (Mining Pickaxe next).";
            case ANVIL -> "§7Talk to Temper (Booster Tutor). Anvil opens in the Manager after his lesson.";
            case SPAWN_UNLOCKER -> "§7Get your first spawn unlocker. The map then has opinions.";
            case SKILLS -> "§7Miss Ledger teaches Skills — Manager unlocks when you accept.";
            case PETS -> "§7Lark hands you spheres. Pets unlock when that lesson starts.";
        };
    }

    public String collectionHint() {
        return "§7Break your first block. Paperwork follows.";
    }

    public String bestiaryHint() {
        return "§7Make your first kill. The mobs are keeping score.";
    }

    public String journalHint() {
        return "§7Slay a dungeon boss. Then the receipts.";
    }

    public String islandHint() {
        return "§7Reach Aetherion Level §f" + ISLAND_LEVEL + "§7 to claim your island.";
    }

    public String guildHint() {
        return "§7Reach Aetherion Level §f" + GUILD_LEVEL + "§7. Guilds are mid–late game.";
    }

    private boolean hubHasBonusSpawn(Player player) {
        if (player == null) {
            return false;
        }
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        return hub != null && hub.hasBonusSpawn(player.getUniqueId());
    }

    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        flags.forEach((id, set) -> {
            if (set == null || set.isEmpty()) {
                return;
            }
            config.set("players." + id, set.stream().map(flag -> flag.name().toLowerCase(Locale.ROOT)).toList());
        });
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            config.save(file);
            dirty = false;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save progress.yml: " + exception.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void reloadFromDisk() {
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
                for (String name : root.getStringList(key)) {
                    try {
                        set.add(Flag.valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!set.isEmpty()) {
                    flags.put(id, set);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
