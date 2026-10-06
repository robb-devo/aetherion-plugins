package de.aetherion.beta.menu;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaJournal;
import de.aetherion.beta.BetaJournal.Reward;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Beta Journal (formerly the checklist). Live: milestone ticks re-render an open
 * journal in place, rewards light up as they become claimable.
 *
 * <pre>
 *  row 0  ▬▬▬▬ [header] ▬▬▬▬          purple beta bar
 *  row 1  ▮ m1 m2 m3 m4 m5 m6 m7 ▮     milestones
 *  row 2  ▮ ── progress track ── ▮     lime = tracked, yellow = self-ticked
 *  row 3  ▮                       ▮
 *  row 4  ▮ r1    r2    r3    r4  ▮     rewards
 *  row 5  lang · book · rate · close
 * </pre>
 */
public final class ChecklistMenu {

    private static final int[] MILESTONE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] TRACK_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] REWARD_SLOTS = {37, 39, 41, 43};
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_LANG = 45;
    private static final int SLOT_BOOK = 47;
    private static final int SLOT_RATE = 49;
    private static final int SLOT_CLOSE = 53;
    private static final Map<Integer, Milestone> SLOT_MAP = new HashMap<>();
    private static final Map<Integer, Reward> REWARD_MAP = new HashMap<>();

    static {
        Milestone[] values = Milestone.values();
        for (int i = 0; i < values.length && i < MILESTONE_SLOTS.length; i++) {
            SLOT_MAP.put(MILESTONE_SLOTS[i], values[i]);
        }
        Reward[] rewards = Reward.values();
        for (int i = 0; i < rewards.length && i < REWARD_SLOTS.length; i++) {
            REWARD_MAP.put(REWARD_SLOTS[i], rewards[i]);
        }
    }

    private ChecklistMenu() {
    }

    public static void open(AetherionBeta plugin, Player player) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, Texts.checklistTitle(lang));
        render(plugin, player, inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
    }

    /** Re-render in place if the player is looking at their journal right now. */
    public static void refresh(AetherionBeta plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Holder) {
            render(plugin, player, top);
        }
    }

    private static void render(AetherionBeta plugin, Player player, Inventory inventory) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        GuiUtil.frame(inventory);

        inventory.setItem(SLOT_HEADER, header(data, lang));

        Milestone[] values = Milestone.values();
        for (int i = 0; i < values.length && i < MILESTONE_SLOTS.length; i++) {
            Milestone milestone = values[i];
            boolean done = data.isDone(milestone);
            boolean tracked = done && data.isAuto(milestone);
            String name = (tracked ? "§a" : done ? "§e" : "§f") + Texts.milestoneName(lang, milestone);
            ItemStack icon = GuiUtil.named(icon(milestone), name, Texts.milestoneLore(lang, milestone, done, tracked));
            inventory.setItem(MILESTONE_SLOTS[i], done ? GuiUtil.glint(icon) : icon);
            inventory.setItem(TRACK_SLOTS[i], GuiUtil.named(
                    tracked ? Material.LIME_STAINED_GLASS_PANE
                            : done ? Material.YELLOW_STAINED_GLASS_PANE
                            : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    tracked ? "§a✔" : done ? "§e✔" : "§8○"));
        }

        for (Map.Entry<Integer, Reward> entry : REWARD_MAP.entrySet()) {
            inventory.setItem(entry.getKey(), rewardIcon(plugin, data, lang, entry.getValue()));
        }

        inventory.setItem(SLOT_LANG, GuiUtil.named(Material.COMPASS, Texts.langButton(lang),
                "§7EN / DE", "", Texts.t(lang, "§e▸ Click to switch", "§e▸ Klick zum Wechseln")));
        boolean hasBook = plugin.npcs().hasBook(player);
        inventory.setItem(SLOT_BOOK, hasBook
                ? GuiUtil.named(Material.WRITTEN_BOOK, Texts.t(lang, "§7Journal in your bag", "§7Journal im Inventar"),
                        Texts.t(lang, "§8Right-click it to open this page.", "§8Rechtsklick darauf öffnet diese Seite."))
                : GuiUtil.named(Material.WRITABLE_BOOK, Texts.bookButton(lang),
                        Texts.t(lang, "§7Lost the book? Take a fresh copy.", "§7Buch verloren? Hol dir eine neue Kopie."),
                        "", Texts.t(lang, "§e▸ Click to take", "§e▸ Klick zum Nehmen")));
        ItemStack rate = GuiUtil.named(
                data.submitted() ? Material.AMETHYST_CLUSTER : Material.AMETHYST_SHARD,
                Texts.rateButton(lang),
                Texts.rateButtonLore(lang),
                data.submitted()
                        ? Texts.t(lang, "§a✔ Sent §8· click to change answers", "§a✔ Gesendet §8· Klick zum Ändern")
                        : Texts.t(lang, "§8Unlocks the Honest Feedback reward.", "§8Schaltet die Feedback-Belohnung frei."),
                "",
                Texts.t(lang, "§e▸ Click to open", "§e▸ Klick zum Öffnen"));
        inventory.setItem(SLOT_RATE, data.submitted() ? rate : GuiUtil.glint(rate));
        inventory.setItem(SLOT_CLOSE, GuiUtil.named(Material.BARRIER, Texts.close(lang)));
    }

    private static ItemStack header(BetaPlayerData data, BetaLang lang) {
        StringBuilder bar = new StringBuilder();
        for (Milestone milestone : Milestone.values()) {
            boolean done = data.isDone(milestone);
            bar.append(done ? (data.isAuto(milestone) ? "§a" : "§e") : "§8").append('▮');
        }
        List<String> lore = new ArrayList<>();
        lore.add(bar.toString());
        lore.add(Texts.tracked(lang, data.doneCount(), data.totalMilestones()));
        lore.add(Texts.played(lang, duration(data.playedMs())));
        lore.add(Texts.feedbackState(lang, data.submitted()));
        lore.add("");
        lore.add(Texts.t(lang, "§8Steps tick themselves off as you play.", "§8Schritte haken sich beim Spielen selbst ab."));
        lore.add(Texts.t(lang, "§8Thanks for testing Aetherion.", "§8Danke, dass du Aetherion testest."));
        ItemStack head = GuiUtil.named(Material.WRITTEN_BOOK, Texts.progressHeader(lang), lore);
        return data.doneCount() >= data.totalMilestones() ? GuiUtil.glint(head) : head;
    }

    private static ItemStack rewardIcon(AetherionBeta plugin, BetaPlayerData data, BetaLang lang, Reward reward) {
        BetaJournal.State state = BetaJournal.state(plugin, data, reward);
        int coins = BetaJournal.coins(plugin, reward);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + reward.requirement(lang));
        lore.add("");
        lore.add("§6+" + String.format(Locale.US, "%,d", coins) + " coins");
        if (reward == Reward.FULL_CLEAR) {
            lore.add(Texts.t(lang, "§d+ Courier's Seal §8(cosmetic keepsake)", "§d+ Kurier-Siegel §8(kosmetisches Andenken)"));
        }
        lore.add("");
        return switch (state) {
            case CLAIMED -> {
                lore.add(Texts.t(lang, "§a✔ Claimed", "§a✔ Abgeholt"));
                yield GuiUtil.named(Material.LIME_DYE, "§8" + reward.title(lang), lore);
            }
            case READY -> {
                lore.add(Texts.t(lang, "§e▸ Click to claim", "§e▸ Klick zum Abholen"));
                yield GuiUtil.glint(GuiUtil.named(reward.icon, "§e" + reward.title(lang), lore));
            }
            case LOCKED -> {
                if (reward.need > 0) {
                    lore.add("§8" + Math.min(data.trackedCount(), reward.need) + "/" + reward.need
                            + Texts.t(lang, " tracked", " erkannt"));
                } else {
                    lore.add(Texts.t(lang, "§8Not sent yet", "§8Noch nicht gesendet"));
                }
                yield GuiUtil.named(Material.GRAY_DYE, "§7" + reward.title(lang), lore);
            }
        };
    }

    public static void handle(AetherionBeta plugin, Player player, int slot) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);

        if (slot == SLOT_LANG) {
            LanguageMenu.open(plugin, player);
            return;
        }
        if (slot == SLOT_RATE) {
            RatingMenu.open(plugin, player);
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BOOK) {
            if (!plugin.npcs().hasBook(player)) {
                plugin.npcs().giveBook(player, data, lang);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 0.8f, 1.2f);
                render(plugin, player, player.getOpenInventory().getTopInventory());
            }
            return;
        }

        Reward reward = REWARD_MAP.get(slot);
        if (reward != null) {
            if (BetaJournal.claim(plugin, player, reward)) {
                render(plugin, player, player.getOpenInventory().getTopInventory());
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 0.8f);
            }
            return;
        }

        Milestone milestone = SLOT_MAP.get(slot);
        if (milestone == null) {
            return;
        }
        if (data.isDone(milestone) && data.isAuto(milestone)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.2f);
            return;
        }
        // Self-report (or undo a self-report). Tracked steps can't be un-ticked.
        data.toggleManual(milestone);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, data.isDone(milestone) ? 1.6f : 0.8f);
        render(plugin, player, player.getOpenInventory().getTopInventory());
    }

    private static Material icon(Milestone milestone) {
        return switch (milestone) {
            case MEET_GUIDE -> Material.WRITABLE_BOOK;
            case QUESTS -> Material.FILLED_MAP;
            case GATHER -> Material.IRON_PICKAXE;
            case PET -> Material.LEAD;
            case BOSS -> Material.WITHER_SKELETON_SKULL;
            case DUNGEON -> Material.IRON_BARS;
            case ISLAND -> Material.OAK_BOAT;
        };
    }

    private static String duration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60) {
            return minutes + "m";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
