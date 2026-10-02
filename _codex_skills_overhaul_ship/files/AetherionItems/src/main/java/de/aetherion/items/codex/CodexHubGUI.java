package de.aetherion.items.codex;

import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.AetherionLevel;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillSeals;
import de.aetherion.items.skill.SkillService;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /codex — the front page. Three ledgers side by side, the journal and milestones under them,
 * one Claim All, and the five entries closest to their next tier.
 *
 * <pre>
 * row 0  tabs · profile
 * row 2  [Collection]  [Bestiary]  [Skills]
 * row 3  [Journal]     [Milestones] [Claim All]
 * row 4  Next up: five nearest tier-ups
 * </pre>
 */
public final class CodexHubGUI {

    private static final int COLLECTION = 20;
    private static final int BESTIARY = 22;
    private static final int SKILLS = 24;
    private static final int JOURNAL = 29;
    private static final int MILESTONES = 31;
    private static final int CLAIM_ALL = 33;
    private static final int[] NEXT_UP = {38, 39, 40, 41, 42};

    private CodexHubGUI() {
    }

    /** Codex score: both ledger levels plus every skill seal reached. Shown on profiles. */
    public static int score(Player player) {
        CodexService service = CodexRewards.service();
        SkillService skills = CodexRewards.skills();
        if (service == null || player == null) {
            return 0;
        }
        int score = CodexBook.summary(service, player, CodexBook.Ledger.COLLECTION).level()
                + CodexBook.summary(service, player, CodexBook.Ledger.BESTIARY).level();
        if (skills != null) {
            for (AetherSkill skill : AetherSkill.values()) {
                score += SkillSeals.reached(skills, player, skill);
            }
        }
        return score;
    }

    public static final class Holder extends CodexView {

        private List<String> nextUp = List.of();

        @Override
        protected String title() {
            return "§8Aetherion Codex";
        }

        @Override
        public void render(Player player) {
            CodexService service = CodexRewards.service();
            SkillService skills = CodexRewards.skills();
            Inventory inv = inventory;
            CodexChrome.frame(inv, player, CodexChrome.Tab.HUB);
            if (service == null) {
                return;
            }
            CodexChrome.row(inv, 1, Material.MAGENTA_STAINED_GLASS_PANE);
            inv.setItem(CodexChrome.HEADER, profile(player, skills));
            inv.setItem(COLLECTION, ledgerTile(service, player, CodexBook.Ledger.COLLECTION));
            inv.setItem(BESTIARY, ledgerTile(service, player, CodexBook.Ledger.BESTIARY));
            inv.setItem(SKILLS, skillsTile(service, player, skills));
            inv.setItem(JOURNAL, journalTile(service, player));
            inv.setItem(MILESTONES, milestonesTile(service, player));
            inv.setItem(CLAIM_ALL, claimTile(player));

            List<CodexBook.State> goals = CodexBook.nextUp(service, player, NEXT_UP.length);
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < NEXT_UP.length; i++) {
                if (i >= goals.size()) {
                    inv.setItem(NEXT_UP[i], GuiItems.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8Next up",
                            "§8Find more entries to fill this row."));
                    continue;
                }
                CodexBook.State state = goals.get(i);
                keys.add(state.card().key());
                inv.setItem(NEXT_UP[i], goal(state));
            }
            nextUp = keys;
            inv.setItem(36, GuiItems.named(Material.COMPASS, "§eNext up §8→",
                    "§7The five entries closest", "§7to their next tier."));
            inv.setItem(CodexChrome.BACK, de.aetherion.items.util.ManagerNav.button());
        }

        private ItemStack profile(Player player, SkillService skills) {
            List<String> lore = new ArrayList<>();
            if (skills != null) {
                int level = skills.accountLevel(player);
                lore.add("§7Aetherion Level " + AetherionLevel.coloredLevel(level) + " §8· " + AetherionLevel.coloredTitle(level));
                lore.add(AetherionLevel.bar(skills.accountXp(player)));
            }
            lore.add("");
            lore.add("§7Codex score §d" + score(player));
            lore.add("§8Collection + Bestiary levels + skill seals.");
            int ready = CodexRewards.claimableTotal(player);
            if (ready > 0) {
                lore.add("");
                lore.add("§e✦ " + ready + " reward" + (ready == 1 ? "" : "s") + " waiting");
            }
            ItemStack head = GuiItems.named(Material.PLAYER_HEAD, "§d" + player.getName() + "'s Codex", lore);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(player);
                head.setItemMeta(skull);
            }
            return head;
        }

        private ItemStack ledgerTile(CodexService service, Player player, CodexBook.Ledger ledger) {
            CodexBook.Summary summary = CodexBook.summary(service, player, ledger);
            String locked = CodexChrome.gate(player, ledger == CodexBook.Ledger.COLLECTION
                    ? CodexChrome.Tab.COLLECTION : CodexChrome.Tab.BESTIARY);
            if (locked != null) {
                return GuiItems.named(Material.GRAY_DYE, "§8" + ledger.title() + " §7· Locked", locked);
            }
            List<String> lore = new ArrayList<>();
            lore.add("§8" + (ledger == CodexBook.Ledger.COLLECTION
                    ? "Ores, wood, crops, catches and more."
                    : "Mobs, sea creatures, floors and bosses."));
            lore.add("");
            lore.add("§7Level §f" + summary.level() + " §8· milestone §f" + summary.milestone());
            lore.add(CodexText.bar(summary.milestoneFill(), ledger.color()) + " §7→ §f" + (summary.milestone() + 1));
            lore.add("§7Found §f" + summary.found() + "§8/§7" + summary.total() + "  §7Maxed §f" + summary.maxed());
            lore.add("§7Perks " + (summary.milestone() > 0 ? CodexPerks.perkLine(ledger, summary.milestone()) : "§8none yet"));
            if (summary.claimableTiers() > 0) {
                lore.add("");
                lore.add("§e✦ " + summary.claimableTiers() + " tier" + (summary.claimableTiers() == 1 ? "" : "s") + " to claim");
            }
            lore.add("");
            lore.add("§eClick to open");
            ItemStack item = GuiItems.named(ledger == CodexBook.Ledger.COLLECTION ? Material.IRON_PICKAXE : Material.BONE,
                    ledger.color() + "§l" + ledger.title() + " §r§8· §fLevel " + summary.level(), lore);
            return summary.claimableTiers() > 0 ? CodexChrome.glint(item) : item;
        }

        private ItemStack skillsTile(CodexService service, Player player, SkillService skills) {
            if (skills == null) {
                return GuiItems.named(Material.BARRIER, "§cSkills are not loaded");
            }
            String locked = CodexChrome.gate(player, CodexChrome.Tab.SKILLS);
            if (locked != null) {
                return GuiItems.named(Material.GRAY_DYE, "§8Skills §7· Locked", locked);
            }
            int total = 0;
            int mastered = 0;
            int best = 1;
            AetherSkill top = null;
            for (AetherSkill skill : AetherSkill.values()) {
                int level = skills.level(player, skill);
                total += level;
                if (SkillProgression.isMax(level)) {
                    mastered++;
                }
                if (top == null || level > best) {
                    top = skill;
                    best = level;
                }
            }
            int count = AetherSkill.values().length;
            int[] reached = SkillSeals.reachedByRarity(skills, player);
            int ready = SkillSeals.readyTiers(skills, service, player);
            List<String> lore = new ArrayList<>();
            lore.add("§8Your loadout and every skill level.");
            lore.add("");
            lore.add("§7Skill average §f" + String.format(java.util.Locale.US, "%.1f", total / (double) count));
            lore.add("§7Total levels §f" + CodexText.number(total) + "§8/§7" + CodexText.number((long) count * SkillProgression.MAX_LEVEL));
            if (top != null && best > 1) {
                lore.add("§7Best §f" + skills.coloredName(player, top) + " §f" + best);
            }
            lore.add("§7Mastered §f" + mastered + "§8/§7" + count);
            lore.add("§7Rarities §aU" + reached[1] + " §bR" + reached[2] + " §5E" + reached[3]
                    + " §6L" + reached[4] + " §dM" + reached[5]);
            if (ready > 0) {
                lore.add("");
                lore.add("§e✦ " + ready + " rarity seal" + (ready == 1 ? "" : "s") + " to claim");
            }
            lore.add("");
            lore.add("§eClick §7for the loadout · §eRight-click §7for the overview");
            ItemStack item = GuiItems.named(Material.EXPERIENCE_BOTTLE, "§b§lSkills §r§8· §favg "
                    + String.format(java.util.Locale.US, "%.1f", total / (double) count), lore);
            return ready > 0 ? CodexChrome.glint(item) : item;
        }

        private ItemStack journalTile(CodexService service, Player player) {
            String locked = CodexChrome.gate(player, CodexChrome.Tab.JOURNAL);
            if (locked != null) {
                return GuiItems.named(Material.GRAY_DYE, "§8Boss Journal §7· Locked", locked);
            }
            int slain = 0;
            int total = 0;
            for (CodexCatalog.Boss boss : CodexCatalog.bosses()) {
                total++;
                if (service.count(player, "b:boss:" + boss.templateId()) > 0L) {
                    slain++;
                }
            }
            return GuiItems.named(Material.WRITABLE_BOOK, "§5Boss Journal §8· §f" + slain + "§8/§7" + total,
                    "§8Bosses and what they drop.",
                    "",
                    CodexText.bar(total == 0 ? 0.0d : slain / (double) total, "§5"),
                    "",
                    "§eClick to open");
        }

        private ItemStack milestonesTile(CodexService service, Player player) {
            CodexBook.Summary collection = CodexBook.summary(service, player, CodexBook.Ledger.COLLECTION);
            CodexBook.Summary bestiary = CodexBook.summary(service, player, CodexBook.Ledger.BESTIARY);
            List<String> lore = new ArrayList<>();
            lore.add("§8Permanent perks, every 10 levels.");
            lore.add("");
            lore.add("§aCollection §7milestone §f" + collection.milestone());
            lore.add(" " + (collection.milestone() > 0 ? CodexPerks.perkLine(CodexBook.Ledger.COLLECTION, collection.milestone()) : "§8none yet"));
            lore.add("§6Bestiary §7milestone §f" + bestiary.milestone());
            lore.add(" " + (bestiary.milestone() > 0 ? CodexPerks.perkLine(CodexBook.Ledger.BESTIARY, bestiary.milestone()) : "§8none yet"));
            lore.add("");
            lore.add("§eClick to see the roads");
            return GuiItems.named(Material.BEACON, "§e§lMilestones", lore);
        }

        private ItemStack claimTile(Player player) {
            int ready = CodexRewards.claimableTotal(player);
            if (ready <= 0) {
                return GuiItems.named(Material.MINECART, "§7Nothing to claim",
                        "§8Tiers and seals land here", "§8the moment you reach them.");
            }
            List<String> lore = new ArrayList<>();
            lore.add("§7" + ready + " reward" + (ready == 1 ? "" : "s") + " across every ledger:");
            lore.addAll(CodexRewards.preview(player, 8));
            lore.add("");
            lore.add("§e§lClick to claim everything");
            return CodexChrome.glint(GuiItems.named(Material.CHEST_MINECART, "§6§lClaim All §8(§e" + ready + "§8)", lore));
        }

        private ItemStack goal(CodexBook.State state) {
            CodexBook.Card card = state.card();
            List<String> lore = new ArrayList<>();
            lore.add("§8" + card.ledger().title() + " · " + CodexCatalog.categoryTitle(card.category()));
            lore.add("");
            lore.addAll(CodexCards.progressBlock(state));
            lore.add("");
            lore.add("§6Tier " + CodexTiers.roman(state.tier() + 1) + " pays");
            lore.add(" " + CodexTiers.reward(card.scale(), state.tier() + 1).line());
            lore.add("");
            lore.add("§eClick §7for the ladder");
            return GuiItems.named(card.icon(), CodexCards.tierColor(state.tier()) + card.name()
                    + " §8→ §e" + CodexTiers.roman(state.tier() + 1), lore);
        }

        @Override
        public void click(Player player, int slot, ClickType click) {
            if (CodexChrome.clickTab(player, slot, CodexChrome.Tab.HUB)) {
                return;
            }
            switch (slot) {
                case CodexChrome.BACK -> {
                    CodexChrome.click(player);
                    de.aetherion.items.util.ManagerNav.openManager(player);
                }
                case COLLECTION -> CodexMenus.openGated(player, CodexChrome.Tab.COLLECTION);
                case BESTIARY -> CodexMenus.openGated(player, CodexChrome.Tab.BESTIARY);
                case JOURNAL -> CodexMenus.openGated(player, CodexChrome.Tab.JOURNAL);
                case MILESTONES -> CodexMenus.openMilestones(player);
                case SKILLS -> {
                    if (CodexChrome.gate(player, CodexChrome.Tab.SKILLS) != null) {
                        CodexMenus.openGated(player, CodexChrome.Tab.SKILLS);
                    } else if (click.isRightClick()) {
                        de.aetherion.items.skill.SkillOverviewGUI.open(player);
                    } else {
                        CodexMenus.openSkills(player);
                    }
                }
                case CLAIM_ALL -> {
                    if (CodexRewards.claimAll(player) > 0) {
                        render(player);
                    }
                }
                default -> {
                    for (int i = 0; i < NEXT_UP.length; i++) {
                        if (NEXT_UP[i] == slot && i < nextUp.size()) {
                            CodexChrome.click(player);
                            CodexMenus.openDetail(player, nextUp.get(i), this);
                            return;
                        }
                    }
                }
            }
        }
    }
}
