package de.aetherion.items.command;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.world.BorderlandsRiteService;
import de.aetherion.items.world.WildlifeLooks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cheap ops count of display entities by known Aetherion scoreboard tags.
 * Console-safe. Does not spawn or remove anything.
 */
public final class DisplayAuditCommand implements CommandExecutor {

    /**
     * Tags that have shown up on block_display / text_display in Aetherion plugins.
     * Zero counts are still printed for the two tags that leaked in production.
     */
    private static final List<String> TAGS = List.of(
            BorderlandsRiteService.OUTLINE_TAG,
            WildlifeLooks.WILDLIFE_LABEL_TAG,
            "aetherion_world_map",
            "aetherion_building_banner",
            "aetherion_crypt_holo",
            "aetherion_jump_pad",
            "aether_gate_glass",
            "aether_loot_spin",
            "aether_loot_label",
            "aether_explore_spin",
            "aether_explore_label",
            "aether_merchant_spin",
            "aether_merchant_label",
            "ae_editor_holo",
            "ae_miss_canopy_holo"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Map<String, Integer> blockByTag = zeroTags();
        Map<String, Integer> textByTag = zeroTags();
        int blockTotal = 0;
        int textTotal = 0;
        int blockKnown = 0;
        int textKnown = 0;
        int wildlifePdc = 0;

        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                blockTotal++;
                if (tally(display, blockByTag)) {
                    blockKnown++;
                }
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                textTotal++;
                if (tally(display, textByTag)) {
                    textKnown++;
                }
                if (display.getPersistentDataContainer().has(ItemKeys.wildlifeLabel(), PersistentDataType.STRING)) {
                    wildlifePdc++;
                }
            }
        }

        sender.sendMessage("§6Display audit");
        sender.sendMessage("§7block_display §f" + blockTotal
                + " §8(tagged §f" + blockKnown + "§8, other §f" + (blockTotal - blockKnown) + "§8)");
        sendAlways(sender, "block", BorderlandsRiteService.OUTLINE_TAG, blockByTag);
        sendPositive(sender, "block", blockByTag, BorderlandsRiteService.OUTLINE_TAG);
        sender.sendMessage("§7text_display §f" + textTotal
                + " §8(tagged §f" + textKnown + "§8, other §f" + (textTotal - textKnown) + "§8)");
        sendAlways(sender, "text", WildlifeLooks.WILDLIFE_LABEL_TAG, textByTag);
        sender.sendMessage("§7  wildlife_label pdc §f" + wildlifePdc);
        sendPositive(sender, "text", textByTag, WildlifeLooks.WILDLIFE_LABEL_TAG);
        sender.sendMessage("§8Altar outlines: §f0 §8with no vial session, else §f1§8. Wildlife labels: about one per live mob.");
        return true;
    }

    private static Map<String, Integer> zeroTags() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String tag : TAGS) {
            counts.put(tag, 0);
        }
        return counts;
    }

    private static boolean tally(Entity entity, Map<String, Integer> counts) {
        boolean known = false;
        for (String tag : TAGS) {
            if (entity.getScoreboardTags().contains(tag)) {
                counts.merge(tag, 1, Integer::sum);
                known = true;
            }
        }
        return known;
    }

    private static void sendAlways(CommandSender sender, String kind, String tag, Map<String, Integer> counts) {
        sender.sendMessage("§7  " + kind + " §f" + tag + " §f" + counts.getOrDefault(tag, 0));
    }

    private static void sendPositive(CommandSender sender, String kind, Map<String, Integer> counts, String skip) {
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getKey().equals(skip) || entry.getValue() <= 0) {
                continue;
            }
            sender.sendMessage("§7  " + kind + " §f" + entry.getKey() + " §f" + entry.getValue());
        }
    }
}
