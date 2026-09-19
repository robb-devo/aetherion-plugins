package de.aetherion.beta.menu;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Milestone;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ChecklistMenu {

    private static final int[] MILESTONE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_LANG = 18;
    private static final int SLOT_INFO = 22;
    private static final int SLOT_RATE = 26;
    private static final Map<Integer, Milestone> SLOT_MAP = new HashMap<>();

    static {
        Milestone[] values = Milestone.values();
        for (int i = 0; i < values.length && i < MILESTONE_SLOTS.length; i++) {
            SLOT_MAP.put(MILESTONE_SLOTS[i], values[i]);
        }
    }

    private ChecklistMenu() {
    }

    public static void open(AetherionBeta plugin, Player player) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, Texts.checklistTitle(lang));
        GuiUtil.frame(inventory);

        Milestone[] values = Milestone.values();
        for (int i = 0; i < values.length && i < MILESTONE_SLOTS.length; i++) {
            Milestone milestone = values[i];
            boolean done = data.isDone(milestone);
            boolean autoTracked = isAutoTracked(milestone);
            Material material = done ? Material.LIME_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
            inventory.setItem(
                    MILESTONE_SLOTS[i],
                    GuiUtil.named(
                            material,
                            "§f" + Texts.milestoneName(lang, milestone),
                            Texts.milestoneLore(lang, milestone, done, autoTracked)
                    )
            );
        }

        inventory.setItem(SLOT_LANG, GuiUtil.named(Material.COMPASS, Texts.langButton(lang), "§7EN / DE"));
        inventory.setItem(SLOT_INFO, GuiUtil.named(
                Material.BOOK,
                "§d✦ " + Texts.t(lang, "Progress", "Fortschritt"),
                "§f" + data.doneCount() + "§7/§f" + data.totalMilestones(),
                data.submitted()
                        ? Texts.t(lang, "§aFeedback submitted", "§aFeedback gesendet")
                        : Texts.t(lang, "§8Feedback not submitted yet", "§8Feedback noch nicht gesendet"),
                "",
                Texts.t(lang, "§7Auto-tracks what you try, then rate.", "§7Hakt automatisch ab, dann bewerten.")
        ));
        inventory.setItem(SLOT_RATE, GuiUtil.named(
                Material.AMETHYST_SHARD,
                Texts.rateButton(lang),
                Texts.rateButtonLore(lang)
        ));

        player.openInventory(inventory);
    }

    public static void handle(AetherionBeta plugin, Player player, int slot) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());

        if (slot == SLOT_LANG) {
            LanguageMenu.open(plugin, player);
            return;
        }
        if (slot == SLOT_RATE) {
            RatingMenu.open(plugin, player);
            return;
        }

        Milestone milestone = SLOT_MAP.get(slot);
        if (milestone == null) {
            return;
        }
        if (isAutoTracked(milestone) && data.isDone(milestone) && data.isAuto(milestone)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.2f);
            return;
        }
        if (isAutoTracked(milestone) && !data.isDone(milestone)) {
            data.mark(milestone, false);
        } else if (!isAutoTracked(milestone)) {
            data.toggleManual(milestone);
        } else {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, data.isDone(milestone) ? 1.6f : 0.8f);
        open(plugin, player);
    }

    private static boolean isAutoTracked(Milestone milestone) {
        return milestone == Milestone.MEET_GUIDE
                || milestone == Milestone.QUESTS
                || milestone == Milestone.GATHER
                || milestone == Milestone.PET
                || milestone == Milestone.BOSS
                || milestone == Milestone.DUNGEON
                || milestone == Milestone.ISLAND;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
