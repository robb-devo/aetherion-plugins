package de.aetherion.quests.story;

import de.aetherion.items.lore.StoryLore;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.PlayerItems;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands out story finds: quest rewards, world-chest letters, boss finds. Every grant also
 * unlocks the matching Chronicle page. Finds never stack up: a player who already carries one
 * does not get a second.
 */
public final class LoreFinds {

    /** Chance per opened world chest while "Letters Signed M" is active. */
    private static final double LETTER_CHANCE = 0.5;

    private static final List<StoryLore> LETTERS = List.of(
            StoryLore.MAELIS_LETTER_1, StoryLore.MAELIS_LETTER_2, StoryLore.MAELIS_LETTER_3);

    private LoreFinds() {
    }

    public static boolean carries(Player player, StoryLore lore) {
        return player != null && lore != null && PlayerItems.count(player, lore.itemId()) > 0;
    }

    /** Builds the stack, marks the page and announces it. Caller decides where the stack goes. */
    public static ItemStack announce(Player player, StoryLore lore) {
        ItemStack stack = lore.create();
        StoryState.unlockPage(player, lore.findId());
        player.sendMessage(LangPack.msg(player, "story.find", "§6✦ Story find §8» §f{0} §8· §7Chronicle page added.")
                .replace("{0}", lore.displayName()));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 0.9f);
        return stack;
    }

    /** Straight into the inventory (overflow drops at the player's feet). */
    public static void give(Player player, StoryLore lore, boolean onlyIfMissing) {
        if (player == null || lore == null || (onlyIfMissing && carries(player, lore))) {
            return;
        }
        ItemStack stack = announce(player, lore);
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** World chest opened: while the letters quest runs, half the chests hold the next letter. */
    public static void onExploreChest(Player player) {
        QuestManager qm = questManager();
        if (qm == null || player == null || !isActive(qm, player, LullabyQuests.LETTERS)) {
            return;
        }
        for (StoryLore letter : LETTERS) {
            if (carries(player, letter)) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() >= LETTER_CHANCE) {
                return;
            }
            give(player, letter, true);
            player.sendMessage(LangPack.msg(player, "story.letter_hint",
                    "§7§oTucked under the lining: a letter, sealed in wax, signed 'M'."));
            qm.syncDeliverProgress(player);
            return;
        }
    }

    static QuestManager questManager() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getQuestManager();
    }

    static boolean isActive(QuestManager qm, Player player, String questId) {
        Quest quest = qm.getQuest(questId);
        if (quest == null) {
            return false;
        }
        QuestState state = qm.getQuestState(player, quest);
        return state == QuestState.ACTIVE || state == QuestState.READY;
    }

    /** True while any Lullaby quest is in progress for this player. */
    static boolean anyLullabyActive(QuestManager qm, Player player) {
        for (LullabyQuests.Step step : LullabyQuests.STEPS) {
            if (isActive(qm, player, step.id())) {
                return true;
            }
        }
        return false;
    }
}
