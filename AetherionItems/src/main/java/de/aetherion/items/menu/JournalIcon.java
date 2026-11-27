package de.aetherion.items.menu;

import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The Aether Journal's own line pictures, one per line that is not a Manager tile (those keep
 * {@link ManagerIcons}). Each is paper with a CustomModelData override: {@code 7800 + ordinal} open,
 * {@code 7850 + ordinal} greyed with a padlock. The art and the overrides come from {@code tools/gen_gui_icons.py}
 * ({@code JOURNAL}, same order: append new pictures at the end, never reorder).
 */
enum JournalIcon {
    QUEST, QUEST_READY, QUEST_NONE, CHAPTER, CLAIM, ISLAND_GOAL, ISLE_MENU, TALK_LOG, GUIDE, DUNGEONS, EXILE, CAPITAL,
    GROVE, MINE_ISLE, ORIGIN, BOSSES, MILESTONES, AETHERLEX, PARTY, FRIENDS, LANGUAGE, TALK_MODE, REPORT, CHECKLIST,
    RAID_BAN,
    /** 2026-11-27: the language picker's flags (Quests LangMenu uses paper 7825 / 7826 directly). */
    FLAG_DE, FLAG_EN;

    private static final int OPEN = 7800;
    private static final int LOCKED = 7850;

    /** A button with this picture. */
    ItemStack item(String name, List<String> lore) {
        return with(GuiItems.named(Material.PAPER, name, lore), OPEN + ordinal());
    }

    /** The same picture in stone grey with a padlock: not open yet. */
    ItemStack locked(String name, List<String> lore) {
        return with(GuiItems.named(Material.PAPER, name, lore), LOCKED + ordinal());
    }

    private static ItemStack with(ItemStack item, int cmd) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }
}
