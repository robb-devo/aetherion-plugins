package de.aetherion.items.skill;

import de.aetherion.items.codex.CodexChrome;
import de.aetherion.items.codex.CodexRewards;
import de.aetherion.items.codex.CodexView;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Loadout presets — a mining kit, a boss kit, a fishing kit, one click apart.
 *
 * <pre>
 * row 0  header
 * row 1  [Preset 1]  [Preset 2]  [Preset 3]   [Current loadout]
 * row 2  Back · help
 * </pre>
 * Click loads, Shift-click saves the current loadout, Shift-right-click clears. Levels never move.
 * Also from chat: {@code /skills preset <1-3>}.
 */
public final class SkillPresetsGUI {

    private static final int[] PRESET_SLOTS = {10, 12, 14};
    private static final int CURRENT = 16;
    private static final int BACK = de.aetherion.items.util.ManagerNav.SLOT_27;
    private static final int HELP = 22;

    private SkillPresetsGUI() {
    }

    public static void open(Player player) {
        new Holder().open(player);
    }

    /** Loads a preset and says how it went. Shared by the page and {@code /skills preset}. */
    public static void load(Player player, SkillService skills, int index) {
        SkillService.PresetResult result = skills.loadPreset(player, index);
        switch (result) {
            case LOCKED -> {
                player.sendMessage("§cPreset " + (index + 1) + " opens at Aetherion Lv. "
                        + SkillService.PRESET_UNLOCK[Math.max(0, Math.min(index, SkillService.PRESET_COUNT - 1))] + ".");
                CodexChrome.deny(player);
            }
            case EMPTY -> {
                player.sendMessage("§7Preset " + (index + 1) + " is empty. §8Shift-click it in /skills presets to save.");
                CodexChrome.deny(player);
            }
            case PARTIAL -> {
                player.sendMessage("§e⇄ Preset " + (index + 1) + " loaded §8· §7not every skill fit your open slots.");
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.0f);
            }
            case LOADED -> {
                player.sendMessage("§a⇄ Preset " + (index + 1) + " loaded §8· §f" + skills.equipped(player).size() + " §7skills equipped.");
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.1f);
            }
        }
    }

    public static final class Holder extends CodexView {

        @Override
        protected String title() {
            return "§8Skills » Presets";
        }

        @Override
        protected int size() {
            return 27;
        }

        @Override
        public void render(Player player) {
            SkillService skills = CodexRewards.skills();
            Inventory inv = inventory;
            ItemStack black = CodexChrome.pane(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack gray = CodexChrome.pane(Material.GRAY_STAINED_GLASS_PANE);
            for (int slot = 0; slot < inv.getSize(); slot++) {
                inv.setItem(slot, (slot < 9 || slot >= 18 ? black : gray).clone());
            }
            if (skills == null) {
                return;
            }
            inv.setItem(4, GuiItems.named(Material.ARMOR_STAND, "§b§lLoadout Presets",
                    "§8Save a loadout, swap it back in one click.",
                    "§8Levels stay with the skill, always."));
            for (int i = 0; i < SkillService.PRESET_COUNT; i++) {
                inv.setItem(PRESET_SLOTS[i], presetIcon(player, skills, i));
            }
            inv.setItem(CURRENT, current(player, skills));
            inv.setItem(BACK, CodexChrome.back("your loadout"));
            inv.setItem(HELP, GuiItems.named(Material.BOOK, "§eHow presets work",
                    "§eClick §7a preset to load it.",
                    "§eShift-click §7to save your current loadout.",
                    "§eShift-right-click §7to clear it.",
                    "",
                    "§7From chat: §f/skills preset <1-3>",
                    "",
                    "§8Presets 2 and 3 open at Aetherion",
                    "§8Lv. " + SkillService.PRESET_UNLOCK[1] + " and " + SkillService.PRESET_UNLOCK[2] + "."));
        }

        private ItemStack presetIcon(Player player, SkillService skills, int index) {
            if (!skills.presetUnlocked(player, index)) {
                return GuiItems.named(Material.GRAY_DYE, "§8Preset " + (index + 1) + " §7· Locked",
                        "§7Opens at Aetherion Lv. §f" + SkillService.PRESET_UNLOCK[index] + "§7.");
            }
            List<AetherSkill> saved = skills.preset(player, index);
            if (saved.isEmpty()) {
                return GuiItems.named(Material.BOOK, "§7Preset " + (index + 1) + " §8· empty",
                        "§8Nothing saved yet.", "", "§eShift-click §7to save your current loadout");
            }
            boolean active = saved.equals(skills.equipped(player));
            List<String> lore = new ArrayList<>();
            lore.add("§8" + label(saved));
            lore.add("");
            int unlocked = skills.unlockedSlots(player);
            for (int i = 0; i < saved.size(); i++) {
                AetherSkill skill = saved.get(i);
                String line = "§7" + (i + 1) + ". " + skills.coloredName(player, skill) + " §f" + skills.level(player, skill);
                lore.add(i < unlocked ? line : "§8" + (i + 1) + ". " + skill.displayName() + " §8(slot locked)");
            }
            lore.add("");
            lore.add(active ? "§a✔ Equipped right now" : "§eClick §7to load");
            lore.add("§eShift-click §7to overwrite · §eShift-right §7to clear");
            ItemStack item = GuiItems.named(active ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK,
                    (active ? "§a" : "§b") + "Preset " + (index + 1), lore);
            return active ? CodexChrome.glint(item) : item;
        }

        private ItemStack current(Player player, SkillService skills) {
            List<AetherSkill> equipped = skills.equipped(player);
            List<String> lore = new ArrayList<>();
            if (equipped.isEmpty()) {
                lore.add("§8Nothing equipped.");
            } else {
                lore.add("§8" + label(equipped));
                lore.add("");
                for (AetherSkill skill : equipped) {
                    lore.add("§7• " + skills.coloredName(player, skill) + " §f" + skills.level(player, skill));
                }
            }
            lore.add("");
            lore.add("§7Slots open §f" + skills.unlockedSlots(player) + "§8/§7" + SkillService.SLOT_COUNT);
            return GuiItems.named(Material.ARMOR_STAND, "§eCurrent loadout", lore);
        }

        /** "Mining kit", "Combat + Fishing", … from the categories in a loadout. */
        private static String label(List<AetherSkill> skills) {
            java.util.Map<AetherSkill.Category, Integer> counts = new java.util.EnumMap<>(AetherSkill.Category.class);
            for (AetherSkill skill : skills) {
                counts.merge(skill.category(), 1, Integer::sum);
            }
            List<AetherSkill.Category> top = new ArrayList<>(counts.keySet());
            top.sort((a, b) -> counts.get(b) - counts.get(a));
            if (top.isEmpty()) {
                return "Empty";
            }
            String first = de.aetherion.items.codex.CodexText.strip(top.get(0).title());
            if (top.size() == 1) {
                return first + " kit";
            }
            return first + " + " + de.aetherion.items.codex.CodexText.strip(top.get(1).title());
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            SkillService skills = CodexRewards.skills();
            if (skills == null) {
                return;
            }
            if (slot == BACK) {
                CodexChrome.click(player);
                var plugin = de.aetherion.items.AetherionItems.getInstance();
                if (plugin != null && plugin.getSkillMenu() != null) {
                    plugin.getSkillMenu().open(player);
                }
                return;
            }
            for (int i = 0; i < PRESET_SLOTS.length; i++) {
                if (PRESET_SLOTS[i] != slot) {
                    continue;
                }
                if (click == ClickType.SHIFT_RIGHT) {
                    skills.clearPreset(player, i);
                    player.sendMessage("§7Preset " + (i + 1) + " cleared.");
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.8f);
                } else if (click.isShiftClick()) {
                    if (skills.savePreset(player, i)) {
                        player.sendMessage("§a✎ Saved your loadout to Preset " + (i + 1) + ".");
                        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.3f);
                    } else {
                        player.sendMessage(skills.presetUnlocked(player, i)
                                ? "§7Equip something first — an empty loadout isn't worth saving."
                                : "§cPreset " + (i + 1) + " opens at Aetherion Lv. " + SkillService.PRESET_UNLOCK[i] + ".");
                        CodexChrome.deny(player);
                    }
                } else {
                    load(player, skills, i);
                }
                render(player);
                return;
            }
        }
    }
}
