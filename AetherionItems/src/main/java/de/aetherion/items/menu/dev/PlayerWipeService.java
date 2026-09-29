package de.aetherion.items.menu.dev;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.rank.RankBadgeService;
import de.aetherion.items.storage.LoadoutManager;
import de.aetherion.items.world.ColosseumGateService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Full account wipe from DEV Menu: progress, economy, pads/loadouts, inventory,
 * quests, pets, hub unlocks. Prefer kick + live RAM/disk clear — no restart.
 */
public final class PlayerWipeService {

    public record Result(String name, UUID id, boolean kicked, List<String> cleared, String note) {
    }

    private PlayerWipeService() {
    }

    public static Result wipe(AetherionItems plugin, UUID targetId, Player operator) {
        if (plugin == null || targetId == null) {
            return new Result("?", targetId, false, List.of(), "missing target");
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() != null ? offline.getName() : targetId.toString();
        List<String> cleared = new ArrayList<>();
        Player online = Bukkit.getPlayer(targetId);
        boolean wasOnline = online != null && online.isOnline();

        // 1) Live player body — inventory / XP / effects while still here
        if (wasOnline) {
            clearBody(online);
            cleared.add("inventory+ender+xp");
        }

        // 2) Skills / loadout pads (skill slots)
        if (plugin.getSkills() != null) {
            plugin.getSkills().wipePlayer(targetId);
            cleared.add("skills");
        }

        // 3) Economy
        if (plugin.getCoins() != null) {
            plugin.getCoins().wipePlayer(targetId);
            cleared.add("coins");
        }
        if (plugin.getShards() != null) {
            plugin.getShards().wipePlayer(targetId);
            cleared.add("shards");
        }

        // 4) Progression flags / codex / recipes / blueprints / boosts
        if (plugin.progress() != null) {
            plugin.progress().wipePlayer(targetId);
            cleared.add("progress-flags");
        }
        if (plugin.getCodex() != null) {
            plugin.getCodex().wipePlayer(targetId);
            cleared.add("codex");
        }
        if (plugin.recipeUnlocks() != null) {
            plugin.recipeUnlocks().wipePlayer(targetId);
            cleared.add("recipes");
        }
        if (plugin.blueprintUnlocks() != null) {
            plugin.blueprintUnlocks().wipePlayer(targetId);
            cleared.add("blueprints");
        }
        if (plugin.xpBoost() != null) {
            plugin.xpBoost().wipePlayer(targetId);
            cleared.add("xp-boost");
        }
        ColosseumGateService gate = ColosseumGateService.get();
        if (gate != null) {
            gate.wipePlayer(targetId);
            cleared.add("colosseum");
        }

        // 5) Storage pages + armor pads (loadouts)
        if (plugin.getStorageInventory() != null && online != null && online.isOnline()) {
            plugin.getStorageInventory().clearStorage(online);
            cleared.add("storage");
        }
        wipeLoadouts(plugin, targetId);
        cleared.add("loadouts");

        // 6) Rank → Adventurer (never strip special ranks / Admin)
        wipeRank(plugin, targetId, cleared);

        // 7) Cross-plugin (reflection — no compile deps)
        if (wipeQuests(targetId, online)) {
            cleared.add("quests");
        }
        if (wipeHub(targetId)) {
            cleared.add("hub-teleports");
        }
        if (wipePets(targetId)) {
            cleared.add("pets");
        }

        // 8) Kick so quit-handlers cannot re-save stale RAM, and so shared YAML overlays cleanly
        boolean kicked = false;
        if (wasOnline) {
            Player still = Bukkit.getPlayer(targetId);
            if (still != null && still.isOnline()) {
                still.kickPlayer("§cAccount wiped§7 — progress reset to zero.\n§7Rejoin to start fresh.");
                kicked = true;
            }
        }

        String note = "No server restart needed — disk + RAM cleared live.";
        plugin.getLogger().info("DEV wipe " + name + " (" + targetId + ") by "
                + (operator == null ? "console" : operator.getName())
                + ": " + String.join(", ", cleared));
        if (operator != null) {
            operator.sendMessage("§c§lWIPE §7→ §f" + name);
            operator.sendMessage("§7Cleared: §f" + String.join("§8, §f", cleared));
            if (kicked) {
                operator.sendMessage("§ePlayer kicked so RAM could not re-save stale data.");
            }
            operator.sendMessage("§a" + note);
        }
        return new Result(name, targetId, kicked, List.copyOf(cleared), note);
    }

    private static void clearBody(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setExtraContents(null);
        player.getEnderChest().clear();
        player.setItemOnCursor(null);
        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        var maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(player.getHealth(), maxHealth.getValue()));
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        // Drop worn loadout runtime so armor pads don't snap back on quit
        try {
            if (AetherionItems.getInstance() != null
                    && AetherionItems.getInstance().getLoadoutListener() != null) {
                de.aetherion.core.api.ProgressAccess access = de.aetherion.core.api.AetherServices.progress();
                if (access != null) {
                    access.resetLoadoutRuntime(player);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void wipeLoadouts(AetherionItems plugin, UUID targetId) {
        new LoadoutManager(plugin).wipePlayer(targetId);
    }

    private static void wipeRank(AetherionItems plugin, UUID targetId, List<String> cleared) {
        RankBadgeService ranks = plugin.ranks();
        if (ranks == null) {
            return;
        }
        // Golden: never strip special ranks via wipe. Level rank follows XP wipe separately.
        RankBadgeService.Rank extra = ranks.extraFor(targetId);
        if (extra != null) {
            cleared.add("rank-kept-special:" + extra.group());
            return;
        }
        ranks.setRank(targetId, "adventurer");
        cleared.add("rank→adventurer");
    }

    private static boolean wipeQuests(UUID targetId, Player online) {
        try {
            Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
            if (quests == null || !quests.isEnabled()) {
                return false;
            }
            Object manager = quests.getClass().getMethod("getQuestManager").invoke(quests);
            if (online != null && online.isOnline()) {
                manager.getClass().getMethod("resetAllQuests", Player.class).invoke(manager, online);
            } else {
                manager.getClass().getMethod("wipePlayer", UUID.class).invoke(manager, targetId);
            }
            return true;
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "Quest wipe failed for " + targetId, ex);
            return false;
        }
    }

    private static boolean wipeHub(UUID targetId) {
        try {
            Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
            if (hub == null || !hub.isEnabled()) {
                // Still try deleting shared path if Hub jar isn't on this JVM but folder is sibling
                File sibling = new File(Bukkit.getPluginsFolder(), "AetherionHub/players/" + targetId + ".yml");
                if (sibling.isFile()) {
                    return sibling.delete();
                }
                return false;
            }
            Object hubService = hub.getClass().getMethod("getHub").invoke(hub);
            Method wipe = hubService.getClass().getMethod("wipePlayer", UUID.class);
            wipe.invoke(hubService, targetId);
            return true;
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "Hub wipe failed for " + targetId, ex);
            File sibling = new File(Bukkit.getPluginsFolder(), "AetherionHub/players/" + targetId + ".yml");
            return sibling.isFile() && sibling.delete();
        }
    }

    private static boolean wipePets(UUID targetId) {
        try {
            Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (mobs == null || !mobs.isEnabled()) {
                File pets = new File(Bukkit.getPluginsFolder(), "AetherMobs/pets/" + targetId + ".yml");
                return pets.isFile() && pets.delete();
            }
            try {
                Object active = mobs.getClass().getMethod("getActivePetManager").invoke(mobs);
                if (active != null) {
                    Player online = Bukkit.getPlayer(targetId);
                    if (online != null && online.isOnline()) {
                        try {
                            active.getClass().getMethod("unequip", Player.class).invoke(active, online);
                        } catch (NoSuchMethodException ignored) {
                            active.getClass().getMethod("removeAll").invoke(active);
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
            Object collection = mobs.getClass().getMethod("getPetCollection", UUID.class).invoke(mobs, targetId);
            if (collection != null) {
                collection.getClass().getMethod("clear").invoke(collection);
                Object dataManager = mobs.getClass().getMethod("getPetDataManager").invoke(mobs);
                if (dataManager != null) {
                    for (Method method : dataManager.getClass().getMethods()) {
                        if ("save".equals(method.getName()) && method.getParameterCount() == 1) {
                            method.invoke(dataManager, collection);
                            break;
                        }
                    }
                }
            }
            File pets = new File(mobs.getDataFolder(), "pets/" + targetId + ".yml");
            if (pets.isFile()) {
                pets.delete();
            }
            return true;
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "Pet wipe failed for " + targetId, ex);
            File pets = new File(Bukkit.getPluginsFolder(), "AetherMobs/pets/" + targetId + ".yml");
            return pets.isFile() && pets.delete();
        }
    }
}
