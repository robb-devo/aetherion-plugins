package de.aetherion.items.codex;

import de.aetherion.items.skill.SkillSeals;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Milestone roads. Each road is a window of seven steps around where you stand: done steps green,
 * the next one yellow with its bar, the rest gray. The last column sums what you hold.
 *
 * <pre>
 * row 2  [Collection] · · · road · · · [perks held]
 * row 3  [Bestiary]   · · · road · · · [perks held]
 * row 4  [Seals]      U  R  E  L  M     [claim seals]
 * </pre>
 */
public final class CodexMilestonesGUI {

    private static final int COLLECTION_ROW = 2;
    private static final int BESTIARY_ROW = 3;
    private static final int SEAL_ROW = 4;
    private static final int SEAL_CLAIM = SEAL_ROW * 9 + 8;

    private CodexMilestonesGUI() {
    }

    public static final class Holder extends CodexView {

        @Override
        protected String title() {
            return "§8Codex » Milestones";
        }

        @Override
        public void render(Player player) {
            CodexService service = CodexRewards.service();
            SkillService skills = CodexRewards.skills();
            Inventory inv = inventory;
            CodexChrome.frame(inv, player, CodexChrome.Tab.MILESTONES);
            if (service == null) {
                return;
            }
            CodexChrome.row(inv, 1, Material.YELLOW_STAINED_GLASS_PANE);
            inv.setItem(CodexChrome.HEADER, GuiItems.named(Material.BEACON, "§e§lMilestones",
                    "§8Every 10 ledger levels is a milestone.",
                    "§8Milestone perks are permanent and",
                    "§8apply everywhere you go.",
                    "",
                    "§aCollection §7→ " + CodexPerks.perkLine(CodexBook.Ledger.COLLECTION, 1) + " §7each",
                    "§6Bestiary §7→ " + CodexPerks.perkLine(CodexBook.Ledger.BESTIARY, 1) + " §7each",
                    "§dSkills §7→ a rarity seal per skill per rarity",
                    "",
                    "§7Codex score §d" + CodexHubGUI.score(player)));
            road(inv, COLLECTION_ROW, CodexBook.summary(service, player, CodexBook.Ledger.COLLECTION),
                    CodexBook.Ledger.COLLECTION);
            road(inv, BESTIARY_ROW, CodexBook.summary(service, player, CodexBook.Ledger.BESTIARY),
                    CodexBook.Ledger.BESTIARY);
            if (skills != null) {
                seals(inv, player, service, skills);
            }
            inv.setItem(CodexChrome.BACK, CodexChrome.back("the Codex"));
        }

        private void road(Inventory inv, int row, CodexBook.Summary summary, CodexBook.Ledger ledger) {
            int base = row * 9;
            int milestone = summary.milestone();
            inv.setItem(base, GuiItems.named(
                    ledger == CodexBook.Ledger.COLLECTION ? Material.IRON_PICKAXE : Material.BONE,
                    ledger.color() + "§l" + ledger.title() + " road",
                    "§7Level §f" + summary.level() + " §8· milestone §f" + milestone,
                    "§8" + CodexTiers.MILESTONE_EVERY + " levels per step.",
                    "",
                    "§eClick §7to open the " + ledger.title()));
            // Seven-step window: two behind, the next one, four ahead.
            int first = Math.max(1, milestone - 1);
            for (int i = 0; i < 7; i++) {
                int step = first + i;
                inv.setItem(base + 1 + i, step(summary, ledger, step));
            }
            List<String> lore = new ArrayList<>();
            lore.add("§7Milestones held §f" + milestone);
            lore.add(" " + (milestone > 0 ? CodexPerks.perkLine(ledger, milestone) : "§8nothing yet"));
            if (!CodexPerks.enabled()) {
                lore.add("");
                lore.add("§8Perks are switched off on this server.");
            }
            inv.setItem(base + 8, GuiItems.named(Material.NETHER_STAR, "§aPerks held", lore));
        }

        private ItemStack step(CodexBook.Summary summary, CodexBook.Ledger ledger, int step) {
            int need = step * CodexTiers.MILESTONE_EVERY;
            String perk = CodexPerks.perkLine(ledger, 1);
            if (summary.level() >= need) {
                return GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§aMilestone " + step + " §2✔",
                        "§7Reached at Level " + need, " " + perk);
            }
            if (step == summary.milestone() + 1) {
                double fill = summary.milestoneFill();
                return CodexChrome.glint(GuiItems.named(Material.YELLOW_STAINED_GLASS_PANE, "§eMilestone " + step + " §8· next",
                        CodexText.bar(fill, ledger.color()) + " §f" + CodexText.percent(fill),
                        "§7Level §f" + summary.level() + " §8/ §7" + need,
                        "",
                        "§7Unlocks " + perk,
                        "§8Any tier on any entry counts."));
            }
            return GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§8Milestone " + step,
                    "§7At Level " + need, " " + perk);
        }

        private void seals(Inventory inv, Player player, CodexService service, SkillService skills) {
            int base = SEAL_ROW * 9;
            int[] reached = SkillSeals.reachedByRarity(skills, player);
            int[] claimed = SkillSeals.claimedByRarity(skills, service, player);
            int count = de.aetherion.items.skill.AetherSkill.values().length;
            inv.setItem(base, GuiItems.named(Material.EXPERIENCE_BOTTLE, "§d§lRarity seals",
                    "§7Every skill earns a seal the first",
                    "§7time it reaches each rarity.",
                    "",
                    "§eClick §7for the skill overview"));
            String[] names = {"", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
            String[] colors = {"", "§a", "§b", "§5", "§6", "§d"};
            Material[] panes = {null, Material.LIME_DYE, Material.LIGHT_BLUE_DYE, Material.PURPLE_DYE,
                    Material.ORANGE_DYE, Material.MAGENTA_DYE};
            int[] slots = {0, 2, 3, 4, 5, 6};
            for (int tier = 1; tier <= SkillSeals.MAX_SEALS; tier++) {
                double fill = reached[tier] / (double) count;
                inv.setItem(base + slots[tier], GuiItems.named(panes[tier],
                        colors[tier] + names[tier] + " seals",
                        "§7Skills there §f" + reached[tier] + "§8/§7" + count,
                        CodexText.bar(fill, colors[tier], 15),
                        "§7Claimed §f" + claimed[tier],
                        "",
                        "§7Each pays " + de.aetherion.items.codex.CodexTiers.seal(tier).line()));
            }
            int ready = SkillSeals.readyTiers(skills, service, player);
            inv.setItem(SEAL_CLAIM, ready > 0
                    ? CodexChrome.glint(GuiItems.named(Material.CHEST_MINECART, "§6§lClaim " + ready + " seal" + (ready == 1 ? "" : "s"),
                    "§eClick to claim every ready seal"))
                    : GuiItems.named(Material.MINECART, "§7No seals ready", "§8Level a skill to its next rarity."));
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (CodexChrome.clickTab(player, slot, CodexChrome.Tab.MILESTONES)) {
                return;
            }
            if (slot == CodexChrome.BACK) {
                CodexChrome.click(player);
                CodexMenus.openHub(player);
                return;
            }
            if (slot == COLLECTION_ROW * 9) {
                CodexMenus.openGated(player, CodexChrome.Tab.COLLECTION);
                return;
            }
            if (slot == BESTIARY_ROW * 9) {
                CodexMenus.openGated(player, CodexChrome.Tab.BESTIARY);
                return;
            }
            if (slot == SEAL_ROW * 9) {
                if (CodexChrome.gate(player, CodexChrome.Tab.SKILLS) == null) {
                    de.aetherion.items.skill.SkillOverviewGUI.open(player);
                } else {
                    CodexMenus.openGated(player, CodexChrome.Tab.SKILLS);
                }
                return;
            }
            if (slot == SEAL_CLAIM) {
                SkillService skills = CodexRewards.skills();
                CodexService service = CodexRewards.service();
                if (skills == null || service == null) {
                    return;
                }
                int paid = 0;
                boolean batch = skills.beginRewardBatch(player);
                try {
                    for (var skill : SkillSeals.ready(skills, service, player)) {
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
            }
        }
    }
}
