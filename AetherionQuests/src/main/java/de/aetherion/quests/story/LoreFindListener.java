package de.aetherion.quests.story;

import de.aetherion.bossengine.event.BossDeathEvent;
import de.aetherion.items.lore.StoryLore;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Story finds from boss kills. No boss YAML is touched: the find is added to the death event's
 * per-player loot only for players whose matching {@code lullaby_*} quest is running. The two
 * side finds (McNugget, Bridge Troll) drop once per player while any Lullaby quest is running.
 * <p>
 * Registered only when BossEngine is loaded (soft dependency).
 */
public final class LoreFindListener implements Listener {

    /** Original pitches of the Seven Bells hymn (OriginBells.HYMN); the gear plays it wrong. */
    private static final float[] WRONG_HYMN = {0.749f, 0.841f, 0.944f, 1.06f};

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(BossDeathEvent event) {
        if (event.getInstance() == null || event.getInstance().getTemplate() == null) {
            return;
        }
        String bossId = event.getInstance().getTemplate().getId();
        if (bossId == null) {
            return;
        }
        QuestManager qm = LoreFinds.questManager();
        if (qm == null) {
            return;
        }
        switch (bossId.toLowerCase(Locale.ROOT)) {
            case "hollow_lurker" -> drop(event, qm, StoryLore.MUSIC_BOX_GEAR, LullabyQuests.GEAR, null);
            case "skuldugery" -> drop(event, qm, StoryLore.SUN_GUARD_INSIGNIA, LullabyQuests.INSIGNIA,
                    "boss.skuldugery");
            case "pathwarden" -> drop(event, qm, StoryLore.SEALED_WRIT, LullabyQuests.SILENT_ROAD,
                    "boss.pathwarden");
            case "mcnugget" -> drop(event, qm, StoryLore.TINY_PAPER_CROWN, null, null);
            case "bridge_troll" -> drop(event, qm, StoryLore.ANCHOR_CHAIN_LINK, null, "boss.bridge_troll");
            default -> {
            }
        }
    }

    private void drop(BossDeathEvent event, QuestManager qm, StoryLore lore, String questId, String bossLine) {
        Set<UUID> ids = new LinkedHashSet<>(event.getLootByPlayer().keySet());
        ids.addAll(event.getDamageMap().keySet());
        if (event.getKiller() != null) {
            ids.add(event.getKiller().getUniqueId());
        }
        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || LoreFinds.carries(player, lore)) {
                continue;
            }
            if (questId != null) {
                if (!LoreFinds.isActive(qm, player, questId)) {
                    continue;
                }
            } else if (!LoreFinds.anyLullabyActive(qm, player) || StoryState.hasPage(player, lore.findId())) {
                continue;
            }
            ItemStack stack = LoreFinds.announce(player, lore);
            List<ItemStack> bundle = event.isDropLoot() ? event.getLootByPlayer().get(id) : null;
            if (bundle != null) {
                bundle.add(stack);
            } else if (event.isDropLoot() && event.getLootByPlayer() != null && !event.getLootByPlayer().isEmpty()) {
                event.getLootByPlayer().put(id, new ArrayList<>(List.of(stack)));
            } else {
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            if (bossLine != null) {
                player.sendMessage(LangPack.msg(player, bossLine, defaultLine(bossLine)));
            }
            if (lore == StoryLore.MUSIC_BOX_GEAR) {
                playWrongHymn(player);
            }
        }
    }

    private static String defaultLine(String key) {
        return switch (key) {
            case "boss.skuldugery" -> "§cSkuldugery §8» §f§oI ran. The Captain didn't.";
            case "boss.pathwarden" -> "§7Pathwarden §8» §f§oOrder… fulfilled?";
            case "boss.bridge_troll" -> "§6Bridge Troll §8» §f§oChain old. Chain heavy. Chain MINE.";
            default -> "";
        };
    }

    /** Four slow notes of the bell hymn, the last one wrong. Only the finder hears it. */
    private static void playWrongHymn(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        for (int i = 0; i < WRONG_HYMN.length; i++) {
            float pitch = WRONG_HYMN[i];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 0.6f, pitch);
                }
            }, 30L + i * 16L);
        }
    }
}
