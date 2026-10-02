package de.aetherion.items.skill;

import de.aetherion.items.codex.CodexChrome;
import de.aetherion.items.codex.CodexRewards;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.codex.CodexText;
import de.aetherion.items.codex.CodexView;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Every skill at a glance — the page the loadout locker never had room for.
 *
 * <pre>
 * row 0    Codex tabs · header (skill average, totals, rarities)
 * rows 1–4 36 skills per page, sortable, filterable
 * row 5    Back to loadout · Prev · Sort · Close · Filter · Next · Claim seals
 * </pre>
 * Click: claim a ready seal, else jump to the skill's page in the loadout. Right-click: equip or
 * unequip straight from here.
 */
public final class SkillOverviewGUI {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int CLAIM = 53;

    private enum Sort {
        LEVEL("Highest level"),
        CATEGORY("By category"),
        CLOSEST("Closest to next rarity"),
        NAME("A–Z");

        private final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private enum Show {
        ALL("Every skill"),
        EQUIPPED("Equipped"),
        SEALS("Seal ready"),
        TRAINED("Trained (Lv. 2+)"),
        UNTRAINED("Untouched");

        private final String label;

        Show(String label) {
            this.label = label;
        }
    }

    private SkillOverviewGUI() {
    }

    public static void open(Player player) {
        new Holder().open(player);
    }

    public static final class Holder extends CodexView {

        private int page = 1;
        private Sort sort = Sort.LEVEL;
        private Show show = Show.ALL;
        private List<AetherSkill> order = List.of();

        @Override
        protected String title() {
            return "§8Skills » Overview";
        }

        @Override
        public void render(Player player) {
            SkillService skills = CodexRewards.skills();
            CodexService codex = CodexRewards.service();
            Inventory inv = inventory;
            CodexChrome.frame(inv, player, CodexChrome.Tab.SKILLS);
            if (skills == null) {
                return;
            }
            inv.setItem(CodexChrome.HEADER, header(player, skills, codex));
            inv.setItem(CodexChrome.TOOL_A, session(player, skills));

            List<AetherSkill> list = visible(player, skills, codex);
            order = list;
            int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
            page = Math.min(Math.max(1, page), pages);
            int start = (page - 1) * PER_PAGE;
            for (int i = 0; i < PER_PAGE; i++) {
                int index = start + i;
                inv.setItem(FIRST + i, index < list.size()
                        ? skillIcon(player, skills, codex, list.get(index))
                        : CodexChrome.pane(Material.GRAY_STAINED_GLASS_PANE));
            }
            if (list.isEmpty()) {
                inv.setItem(22, GuiItems.named(Material.STRUCTURE_VOID, "§7Nothing to show",
                        "§8No skills match §f" + show.label + "§8.", "", "§eClick the spyglass §7to change it."));
            }
            inv.setItem(CodexChrome.BACK, CodexChrome.back("your loadout"));
            if (page > 1) {
                inv.setItem(CodexChrome.PREV, CodexChrome.prev(page, pages));
            }
            if (page < pages) {
                inv.setItem(CodexChrome.NEXT, CodexChrome.next(page, pages));
            }
            inv.setItem(CodexChrome.SORT, cycler(Material.HOPPER, "§eSort: §f" + sort.label, Sort.values(), sort));
            inv.setItem(CodexChrome.FILTER, cycler(Material.SPYGLASS, "§eShow: §f" + show.label, Show.values(), show));
            int ready = codex == null ? 0 : SkillSeals.readyTiers(skills, codex, player);
            inv.setItem(CLAIM, ready > 0
                    ? CodexChrome.glint(GuiItems.named(Material.CHEST_MINECART, "§6§lClaim " + ready + " seal" + (ready == 1 ? "" : "s"),
                    "§7Rarity seals: coins and Shards,", "§7once per skill per rarity.", "", "§eClick to claim all"))
                    : GuiItems.named(Material.MINECART, "§7No seals ready", "§8Reach a new rarity to earn one."));
        }

        private List<AetherSkill> visible(Player player, SkillService skills, CodexService codex) {
            List<AetherSkill> list = new ArrayList<>();
            for (AetherSkill skill : AetherSkill.values()) {
                int level = skills.level(player, skill);
                boolean keep = switch (show) {
                    case ALL -> true;
                    case EQUIPPED -> skills.slotOf(player, skill) >= 0;
                    case SEALS -> SkillSeals.ready(skills, codex, player, skill) > 0;
                    case TRAINED -> level > 1 || skills.xp(player, skill) > 0;
                    case UNTRAINED -> level <= 1 && skills.xp(player, skill) <= 0;
                };
                if (keep) {
                    list.add(skill);
                }
            }
            Comparator<AetherSkill> byLevel = Comparator.comparingInt((AetherSkill skill) -> skills.level(player, skill))
                    .reversed()
                    .thenComparing(Comparator.comparingInt((AetherSkill skill) -> skills.xp(player, skill)).reversed());
            switch (sort) {
                case LEVEL -> list.sort(byLevel.thenComparing(AetherSkill::displayName));
                case CATEGORY -> list.sort(Comparator.comparing(AetherSkill::category).thenComparing(byLevel));
                case CLOSEST -> list.sort(Comparator.comparingLong((AetherSkill skill) -> {
                    int level = skills.level(player, skill);
                    int next = SkillProgression.nextRarityLevel(level);
                    return next < 0 ? Long.MAX_VALUE : SkillProgression.xpUntil(level, skills.xp(player, skill), next);
                }));
                case NAME -> list.sort(Comparator.comparing(AetherSkill::displayName));
            }
            return list;
        }

        private ItemStack header(Player player, SkillService skills, CodexService codex) {
            int total = 0;
            int mastered = 0;
            int trained = 0;
            for (AetherSkill skill : AetherSkill.values()) {
                int level = skills.level(player, skill);
                total += level;
                if (SkillProgression.isMax(level)) {
                    mastered++;
                }
                if (level > 1 || skills.xp(player, skill) > 0) {
                    trained++;
                }
            }
            int count = AetherSkill.values().length;
            int[] reached = SkillSeals.reachedByRarity(skills, player);
            List<String> lore = new ArrayList<>();
            lore.add("§8Every skill you own, equipped or not.");
            lore.add("");
            lore.add("§7Skill average §f" + String.format(Locale.US, "%.1f", total / (double) count));
            lore.add(CodexText.bar(total / (double) (count * SkillProgression.MAX_LEVEL), "§b")
                    + " §7" + CodexText.number(total) + "§8/§7" + CodexText.number((long) count * SkillProgression.MAX_LEVEL));
            lore.add("§7Trained §f" + trained + "§8/§7" + count + "  §7Mastered §d" + mastered);
            lore.add("§7Reached §aU" + reached[1] + " §bR" + reached[2] + " §5E" + reached[3]
                    + " §6L" + reached[4] + " §dM" + reached[5]);
            lore.add("");
            lore.add("§8Skills only level while equipped.");
            lore.add("§8Swap freely — levels stay with the skill.");
            return GuiItems.named(Material.EXPERIENCE_BOTTLE, "§b§lSkill Overview", lore);
        }

        private ItemStack session(Player player, SkillService skills) {
            SkillSession.Snapshot snap = SkillSession.of(player);
            List<String> lore = new ArrayList<>();
            lore.add("§8Since you logged in (" + snap.minutes() + " min).");
            lore.add("");
            lore.add("§7Skill XP §f" + CodexText.number(snap.xp()));
            lore.add("§7Level-ups §f" + snap.levels());
            if (snap.perHour() > 0L) {
                lore.add("§7Pace §f" + CodexText.compact(snap.perHour()) + " XP/h");
            }
            if (snap.top() != null) {
                lore.add("§7Top §f" + skills.coloredName(player, snap.top()) + " §8+" + CodexText.compact(snap.topXp()));
            }
            return GuiItems.named(Material.CLOCK, "§eThis session", lore);
        }

        private ItemStack skillIcon(Player player, SkillService skills, CodexService codex, AetherSkill skill) {
            int level = skills.level(player, skill);
            Rarity rarity = SkillProgression.rarity(level);
            int slot = skills.slotOf(player, skill);
            int ready = SkillSeals.ready(skills, codex, player, skill);
            List<String> lore = new ArrayList<>();
            lore.add(skill.category().title() + " §8· " + rarity.getChatColor() + SkillProgression.rarityName(rarity)
                    + " §8· " + SkillProgression.stage(level).colored());
            if (SkillProgression.isMax(level)) {
                lore.add("§7Lv. §d" + SkillProgression.MAX_LEVEL + " §8· §dMastered");
            } else {
                lore.add("§7Lv. §f" + level + "  " + SkillProgression.miniBar(SkillProgression.levelFill(level, skills.xp(player, skill)), "§a")
                        + " §7" + skills.xp(player, skill) + "§8/§7" + SkillProgression.xpToNext(level));
                int next = SkillProgression.nextRarityLevel(level);
                if (next > 0) {
                    lore.add("§8Next " + SkillProgression.rarity(next).getChatColor()
                            + SkillProgression.rarityName(SkillProgression.rarity(next)) + " §8at Lv. " + next + " ("
                            + CodexText.compact(SkillProgression.xpUntil(level, skills.xp(player, skill), next)) + " XP)");
                }
            }
            lore.add(SkillSeals.pips(skills, codex, player, skill));
            lore.add("");
            lore.add("§7“" + SkillFlavor.tagline(skill, level) + "”");
            lore.add("");
            lore.add(slot >= 0 ? "§a✔ Equipped §8· Slot " + (slot + 1) : "§8○ In the pool");
            if (ready > 0) {
                lore.add("§e§l✦ " + ready + " seal" + (ready == 1 ? "" : "s") + " ready §8» "
                        + SkillSeals.pending(skills, codex, player, skill).line());
                lore.add("§eClick §7to claim");
            } else {
                lore.add("§eClick §7to open its page");
            }
            lore.add("§eRight-click §7to " + (slot >= 0 ? "unequip" : "equip"));
            ItemStack item = GuiItems.named(skill.icon(), rarity.getChatColor() + skill.displayName() + " §7" + level, lore);
            return slot >= 0 || ready > 0 ? CodexChrome.glint(item) : item;
        }

        private static <T extends Enum<T>> ItemStack cycler(Material icon, String name, T[] values, T current) {
            List<String> lore = new ArrayList<>();
            for (T value : values) {
                String label = value instanceof Sort s ? s.label : value instanceof Show sh ? sh.label : value.name();
                lore.add(value == current ? "§a▶ " + label : "§8  " + label);
            }
            lore.add("");
            lore.add("§eClick §7next · §eRight-click §7previous");
            return GuiItems.named(icon, name, lore);
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (slot == CodexChrome.Tab.SKILLS.slot()) {
                // Already on Skills — the tab takes you to the loadout.
                openLoadout(player, null);
                return;
            }
            if (CodexChrome.clickTab(player, slot, CodexChrome.Tab.SKILLS)) {
                return;
            }
            SkillService skills = CodexRewards.skills();
            CodexService codex = CodexRewards.service();
            if (skills == null) {
                return;
            }
            if (slot == CodexChrome.BACK) {
                openLoadout(player, null);
                return;
            }
            if (slot == CodexChrome.PREV) {
                page = Math.max(1, page - 1);
                render(player);
                return;
            }
            if (slot == CodexChrome.NEXT) {
                page++;
                render(player);
                return;
            }
            if (slot == CodexChrome.SORT) {
                sort = Sort.values()[(sort.ordinal() + (click.isRightClick() ? Sort.values().length - 1 : 1)) % Sort.values().length];
                page = 1;
                CodexChrome.click(player);
                render(player);
                return;
            }
            if (slot == CodexChrome.FILTER) {
                show = Show.values()[(show.ordinal() + (click.isRightClick() ? Show.values().length - 1 : 1)) % Show.values().length];
                page = 1;
                CodexChrome.click(player);
                render(player);
                return;
            }
            if (slot == CLAIM) {
                int paid = 0;
                boolean batch = skills.beginRewardBatch(player);
                try {
                    for (AetherSkill skill : SkillSeals.ready(skills, codex, player)) {
                        paid += SkillSeals.claim(player, skill, true);
                    }
                } finally {
                    if (batch) {
                        skills.endRewardBatch(player);
                    }
                }
                if (paid <= 0) {
                    CodexChrome.deny(player);
                }
                render(player);
                return;
            }
            int index = (page - 1) * PER_PAGE + (slot - FIRST);
            if (slot < FIRST || slot >= FIRST + PER_PAGE || index < 0 || index >= order.size()) {
                return;
            }
            AetherSkill skill = order.get(index);
            if (click.isRightClick()) {
                toggle(player, skills, skill);
                render(player);
                return;
            }
            if (SkillSeals.ready(skills, codex, player, skill) > 0) {
                SkillSeals.claim(player, skill, true);
                render(player);
                return;
            }
            openLoadout(player, skill.category());
        }

        private static void toggle(Player player, SkillService skills, AetherSkill skill) {
            if (skills.slotOf(player, skill) >= 0) {
                skills.unequip(player, skill);
                player.sendMessage("§7− " + skills.coloredName(player, skill) + " §8(progress kept)");
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 0.8f);
                return;
            }
            if (skill.category() == AetherSkill.Category.DUNGEON
                    && !SkillMenu.dungeonUnlocked(player)) {
                player.sendMessage("§cDungeon skills unlock after clearing Floor 1.");
                CodexChrome.deny(player);
                return;
            }
            if (skills.equip(player, skill)) {
                player.sendMessage("§a✚ Slot " + (skills.slotOf(player, skill) + 1) + " §8· " + skills.coloredName(player, skill));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.0f);
            } else {
                player.sendMessage("§cNo free slot. §7Unequip something first.");
                CodexChrome.deny(player);
            }
        }

        private static void openLoadout(Player player, AetherSkill.Category category) {
            var plugin = de.aetherion.items.AetherionItems.getInstance();
            if (plugin != null && plugin.getSkillMenu() != null) {
                CodexChrome.click(player);
                plugin.getSkillMenu().open(player, category);
            }
        }
    }
}
