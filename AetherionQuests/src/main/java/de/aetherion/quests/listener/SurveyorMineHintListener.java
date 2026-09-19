package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.ui.QuestHint;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Soft tip toward the Surveyor the first time you enter the Shabby Mine
 * after tutorial + mining intro — not on unlock / quest accept.
 * Also backup-unlocks crafting if the player skipped Craftsman.
 */
public final class SurveyorMineHintListener implements Listener, Runnable {

    private static final String HINT_KEY = "surveyor_mine_hint";
    private static final String CRAFTSMAN_GIFT = "craftsman_mining_pick_gift";
    private static final String CRAFTSMAN_SPOKEN = "craftsman_spoken";
    private static final String MINE_CRAFT_BACKUP = "mine_craft_backup";

    private final AetherionQuests plugin;

    public SurveyorMineHintListener(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 60L, 40L);
    }

    public static void tryHint(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getPlayerQuestStorage() == null) {
            return;
        }
        if (!insideShabbyMine(player)) {
            return;
        }
        tryCraftBackup(player);
        if (hasStampedAny(player)) {
            QuestHint.clearPending(player);
            return;
        }
        if (plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), HINT_KEY)) {
            return;
        }
        // Don't point at Surveyor mid-tutorial — he says so himself.
        if (plugin.getQuestManager() == null
                || !de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, plugin.getQuestManager())) {
            return;
        }
        if (!eligible(player)) {
            return;
        }
        plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), HINT_KEY);
        QuestHint.clearPending(player);
        QuestHint.show(player, "surveyor", "Surveyor · blueprints");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "→ Surveyor deeper in the mine · blueprints",
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
        player.sendMessage("§b✦ §7End of the tunnel: §eSurveyor§7 — blueprints unlock crafts.");
    }

    /**
     * If the player never spoke to Craftsman, unlock Recipe Book + Crafting on first mine entry.
     */
    private static void tryCraftBackup(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getPlayerQuestStorage() == null) {
            return;
        }
        if (plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), MINE_CRAFT_BACKUP)
                || plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), CRAFTSMAN_GIFT)
                || plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), CRAFTSMAN_SPOKEN)) {
            return;
        }
        if (hasWorkbench(player)) {
            return;
        }
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress == null) {
            return;
        }
        boolean unlocked = progress.unlock(player, "WORKBENCH", "Crafting + Recipes", "Manager → green Recipe Book");
        plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), MINE_CRAFT_BACKUP);
        if (!unlocked) {
            return;
        }
        player.sendMessage("§6✦ §eCrafting unlocked. §7You skipped the Craftsman — Recipe Book is in the Manager.");
        player.sendMessage("§7Nether Star (slot 9) → §agreen book§7. Craft a Mining Pickaxe when you're ready.");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Crafting unlocked · Manager → Recipe Book",
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }

    private static boolean hasWorkbench(Player player) {
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        return progress != null && progress.hasFlag(player, "WORKBENCH");
    }

    private static boolean eligible(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getPlayerQuestStorage() != null) {
            if (plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), CRAFTSMAN_GIFT)
                    || plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), CRAFTSMAN_SPOKEN)
                    || plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), MINE_CRAFT_BACKUP)) {
                return true;
            }
        }
        try {
            return de.aetherion.quests.dialog.DialogManager.playerHasItemId(player, "mining_pickaxe")
                    || de.aetherion.quests.dialog.DialogManager.playerHasItemId(player, "mining_pickaxe_2")
                    || de.aetherion.quests.dialog.DialogManager.playerHasItemId(player, "vein_siphon")
                    || hasWorkbench(player);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean insideShabbyMine(Player player) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getAreas() == null) {
                return false;
            }
            return items.getAreas().isType(
                    player.getLocation(),
                    de.aetherion.items.world.AreaType.SHABBY_MINE
            );
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean hasStampedAny(Player player) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.blueprintUnlocks() == null) {
                return false;
            }
            return items.blueprintUnlocks().hasStampedAny(player);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tryHint(player);
        }
    }
}
