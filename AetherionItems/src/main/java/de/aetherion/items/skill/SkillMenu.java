package de.aetherion.items.skill;

import de.aetherion.items.item.ItemLore;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.util.GuiIcons;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /skills — the loadout locker.
 *
 * <pre>
 * row 0  [Codex][Overview][Readout][Presets][Profile][Seals][Next slot][Session][Guide]
 * row 1  [ ][ 1 ][ 2 ][ 3 ][ 4 ][ 5 ][ 6 ][ 7 ][ ]      loadout
 * row 2  [ ][▬▬▬▬▬▬▬ rarity strip under each slot ▬▬▬▬▬▬▬][ ]
 * row 3  [|][Cmb][Min][For][Frm][Fsh][Utl][Dun][|]       categories (never move)
 * row 4  [|][ pool ............................ ][|]
 * row 5  [<][ pool ............................ ][|]
 * </pre>
 * Clicks re-render in place (no reopen flicker). Slot maps are static arrays — the pool
 * index always follows {@link AetherSkill} enum order for the selected category.
 */
public final class SkillMenu implements Listener {

    public static final String TITLE = "§8Aetherion Skills";
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;
    private static final int READOUT_SLOT = 2;
    private static final int INFO_SLOT = 4;
    private static final int NEXT_SLOT_SLOT = 6;
    private static final int GUIDE_SLOT = 8;
    private static final int CODEX_SLOT = 0;
    private static final int OVERVIEW_SLOT = 1;
    private static final int PRESETS_SLOT = 3;
    private static final int SEALS_SLOT = 5;
    private static final int SESSION_SLOT = 7;
    private static final int FIRST_LOADOUT = 10;
    private static final int FIRST_STRIP = 19;
    /** Categories stay here permanently — never swapped for skills. */
    private static final int[] CATEGORY_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    /** Side panes tinted in the selected category's color — frames the pool. */
    /**
     * Skill pool rows — left-aligned, last column stays glass (44 / 53).
     * Back stays at 45.
     */
    private static final int[] POOL_SLOTS = {
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };
    private static final AetherSkill.Category[] CATEGORIES = AetherSkill.Category.values();
    private static final String FLOOR_ONE_BOSS = "dungeon_sentinel";

    private final SkillService skills;
    /** Opened from the Codex skill overview: Back goes there, not to the Manager. */
    private final java.util.Set<java.util.UUID> fromOverview = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SkillMenu(SkillService skills) {
        this.skills = skills;
    }

    public void open(Player player) {
        open(player, null);
    }

    public void openFromOverview(Player player, AetherSkill.Category category) {
        open(player, category, true);
    }

    public void open(Player player, AetherSkill.Category category) {
        open(player, category, false);
    }

    private void open(Player player, AetherSkill.Category category, boolean overview) {
        if (player == null) {
            return;
        }
        AetherSkill.Category shown = category != null && categoryUnlocked(player, category)
                ? category
                : defaultCategory(player);
        if (overview) {
            fromOverview.add(player.getUniqueId());
        } else {
            fromOverview.remove(player.getUniqueId());
        }
        Holder holder = new Holder(shown);
        Inventory inventory = Bukkit.createInventory(holder, 54, de.aetherion.items.util.ManagerChrome.title(de.aetherion.items.util.ManagerChrome.Page.SKILLS));
        holder.inventory = inventory;
        render(inventory, player, shown);
        player.openInventory(inventory);
    }

    private void render(Inventory inventory, Player player, AetherSkill.Category category) {
        fill(inventory, category);
        int unlocked = skills.unlockedSlots(player);

        inventory.setItem(READOUT_SLOT, readoutIcon(player));
        inventory.setItem(INFO_SLOT, profileIcon(player));
        inventory.setItem(NEXT_SLOT_SLOT, nextSlotIcon(player, unlocked));
        inventory.setItem(GUIDE_SLOT, guideIcon(player));
        inventory.setItem(CODEX_SLOT, codexIcon(player));
        inventory.setItem(OVERVIEW_SLOT, overviewIcon(player));
        inventory.setItem(PRESETS_SLOT, presetsIcon(player));
        inventory.setItem(SEALS_SLOT, sealsIcon(player));
        inventory.setItem(SESSION_SLOT, sessionIcon(player));

        for (int i = 0; i < SkillService.SLOT_COUNT; i++) {
            inventory.setItem(FIRST_LOADOUT + i, slotIcon(player, i, unlocked));
            inventory.setItem(FIRST_STRIP + i, stripPane(player, i, unlocked));
        }

        // Categories always stay put — only the bottom pool swaps.
        for (int i = 0; i < CATEGORIES.length && i < CATEGORY_SLOTS.length; i++) {
            inventory.setItem(CATEGORY_SLOTS[i], categoryIcon(player, CATEGORIES[i], category));
        }

        if (category != null && categoryUnlocked(player, category)) {
            List<AetherSkill> pool = skillsOf(category);
            boolean full = firstFreeSlot(player, unlocked) < 0;
            for (int i = 0; i < pool.size() && i < POOL_SLOTS.length; i++) {
                inventory.setItem(POOL_SLOTS[i], poolIcon(player, pool.get(i), full));
            }
        } else {
            inventory.setItem(POOL_SLOTS[0], named(
                    Material.BOOK,
                    "§7Pick a category",
                    "§8Skills show here. Categories stay above."
            ));
        }

        inventory.setItem(BACK_SLOT, fromOverview.contains(player.getUniqueId())
                ? de.aetherion.items.codex.CodexChrome.back("the skill overview")
                : de.aetherion.items.util.ManagerNav.button());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            click(player, Sound.UI_BUTTON_CLICK, 0.9f);
            if (fromOverview.contains(player.getUniqueId())) {
                SkillOverviewGUI.open(player);
            } else {
                de.aetherion.items.util.ManagerNav.openManager(player);
            }
            return;
        }
        if (slot == CODEX_SLOT) {
            click(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
            de.aetherion.items.codex.CodexMenus.openHub(player);
            return;
        }
        if (slot == OVERVIEW_SLOT || slot == SEALS_SLOT) {
            if (slot == SEALS_SLOT && event.isShiftClick()) {
                claimAllSeals(player);
                render(top, player, holder.category);
                return;
            }
            click(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
            SkillOverviewGUI.open(player);
            return;
        }
        if (slot == PRESETS_SLOT) {
            click(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
            SkillPresetsGUI.open(player);
            return;
        }
        if (slot >= FIRST_LOADOUT && slot < FIRST_LOADOUT + SkillService.SLOT_COUNT) {
            int index = slot - FIRST_LOADOUT;
            if (index >= skills.unlockedSlots(player)) {
                bar(player, skills.unlockHint(player, index));
                click(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f);
                return;
            }
            AetherSkill leaving = skills.inSlot(player, index);
            if (leaving != null && skills.unequipSlot(player, index)) {
                announceUnequip(player, leaving, index);
                holder.category = leaving.category();
            }
            render(top, player, holder.category);
            return;
        }
        int categoryIndex = categoryIndex(slot);
        if (categoryIndex >= 0) {
            AetherSkill.Category category = CATEGORIES[categoryIndex];
            if (!categoryUnlocked(player, category)) {
                bar(player, t(player, "§cDungeon opens after you clear Floor 1.", "§cDungeon öffnet, wenn du Ebene 1 geschafft hast."));
                click(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f);
                return;
            }
            if (category != holder.category) {
                click(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0f);
            }
            holder.category = category;
            render(top, player, category);
            return;
        }
        if (holder.category == null) {
            return;
        }
        int poolIndex = poolIndex(slot);
        List<AetherSkill> pool = skillsOf(holder.category);
        if (poolIndex < 0 || poolIndex >= pool.size()) {
            return;
        }
        AetherSkill skill = pool.get(poolIndex);
        if (event.isShiftClick() && skill.category().leveled()
                && SkillSeals.ready(skills, codex(), player, skill.category()) > 0) {
            SkillSeals.claim(player, skill.category(), true);
            render(top, player, holder.category);
            return;
        }
        if (!skills.talentUnlocked(player, skill)) {
            bar(player, lockedLine(player, skill));
            click(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f);
            return;
        }
        int equippedSlot = skills.slotOf(player, skill);
        if (equippedSlot >= 0) {
            if (skills.unequip(player, skill)) {
                announceUnequip(player, skill, equippedSlot);
            }
            render(top, player, holder.category);
            return;
        }
        if (skills.equip(player, skill)) {
            announceEquip(player, skill);
        } else {
            bar(player, t(player, "§cNo free slot. §7Click a talent up top to take it out.",
                    "§cKein Platz frei. §7Klick oben auf ein Talent, um es rauszunehmen."));
            click(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f);
        }
        render(top, player, holder.category);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ moments

    private void announceEquip(Player player, AetherSkill skill) {
        int slot = skills.slotOf(player, skill);
        int level = skills.level(player, skill);
        int tier = SkillProgression.rarityTier(level);
        bar(player, "§a✚ " + t(player, "Slot ", "Platz ") + (slot + 1) + " §8· " + skills.coloredName(player, skill)
                + " §8· §7" + SkillService.effectLine(skill));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 0.9f + tier * 0.1f);
        if (tier >= 4) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.1f + (tier - 4) * 0.2f);
        }
    }

    private void announceUnequip(Player player, AetherSkill skill, int slot) {
        bar(player, "§7− " + t(player, "Slot ", "Platz ") + (slot + 1) + " §8· " + skills.coloredName(player, skill));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 0.8f);
    }

    private static void click(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 0.6f, pitch);
    }

    /** Menu feedback goes to the action bar, not the chat (chat rule, 2026-11-27). */
    private static void bar(Player player, String legacy) {
        if (player != null && legacy != null && !legacy.isBlank()) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(legacy));
        }
    }

    private static String t(Player player, String en, String german) {
        return de.aetherion.core.exile.Say.t(player, en, german);
    }

    /** "Opens at Mining 20" for a talent still closed. */
    private String lockedLine(Player player, AetherSkill skill) {
        String which = skill.category().leveled()
                ? skill.category().plain()
                : t(player, "your best skill", "deinem besten Skill");
        return t(player, "§8✕ §7Opens at §f" + which + " " + TalentUnlocks.level(skill),
                "§8✕ §7Öffnet bei §f" + which + " " + TalentUnlocks.level(skill));
    }

    // ------------------------------------------------------------------ header row

    private ItemStack profileIcon(Player player) {
        int account = skills.accountLevel(player);
        List<String> lore = new ArrayList<>();
        lore.add("§7Aetherion Level " + AetherionLevel.coloredLevel(account)
                + "§8/§7" + AetherionLevel.MAX_LEVEL);
        lore.add(AetherionLevel.bar(skills.accountXp(player)));
        lore.add("§7Rank: " + displayRank(player, account));
        int nextMark = AetherionLevel.nextMilestone(account);
        if (nextMark > account && nextMark <= AetherionLevel.MAX_LEVEL
                && AetherionLevel.titleTier(nextMark) > AetherionLevel.titleTier(account)) {
            lore.add("§8Next rank: " + AetherionLevel.coloredTitle(nextMark) + " §8at Lv. §7" + nextMark);
        }
        int levelBonus = AetherionLevel.milestoneStatBonus(account);
        if (levelBonus > 0) {
            lore.add("§7Level bonus: §a+" + levelBonus + " Damage §8· §c+" + levelBonus + " Health");
        }
        var boost = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().xpBoost();
        if (boost != null && boost.active(player)) {
            lore.add("§5Blood Phial: " + de.aetherion.items.shop.XpBoosterService.buffSummary()
                    + " §8· §f" + boost.formatted(player));
        }
        lore.add("");
        lore.add(t(player, "§8Everything you do fills this bar:", "§8Alles, was du tust, füllt diesen Balken:"));
        lore.add(t(player, "§8skills, quests, Codex, pets.", "§8Skills, Quests, Codex, Pets."));
        return named(Material.NETHER_STAR, "§6Your Standing  " + AetherionLevel.coloredLevel(account), lore);
    }

    private ItemStack readoutIcon(Player player) {
        List<AetherSkill> equipped = skills.equipped(player);
        List<String> lore = new ArrayList<>();
        lore.add("§8What the loadout adds right now.");
        lore.add("");
        if (equipped.isEmpty()) {
            lore.add("§7Nothing equipped. §8Bold strategy.");
            return named(Material.SPYGLASS, "§eLoadout Readout", lore);
        }
        boolean dungeon = de.aetherion.items.dungeon.DungeonArmor.inDungeon(player);
        Map<ItemCapability, Double> totals = new EnumMap<>(ItemCapability.class);
        List<String> effects = new ArrayList<>();
        boolean sleeping = false;
        for (AetherSkill skill : equipped) {
            int level = skills.level(player, skill);
            if (skill.category() == AetherSkill.Category.DUNGEON && !dungeon) {
                sleeping = true;
                continue;
            }
            if (skill != AetherSkill.LIFE_ABSORB) {
                for (var entry : skill.bonuses().entrySet()) {
                    totals.merge(entry.getKey(),
                            entry.getValue() * SkillProgression.bonusMultiplier(level, entry.getKey()), Double::sum);
                }
            }
            for (Effect effect : flagEffects(skill, level)) {
                if (!effect.note()) {
                    effects.add("§7" + effect.label() + ": §f" + effect.format(effect.value()));
                }
            }
        }
        for (var entry : totals.entrySet()) {
            ItemCapability capability = entry.getKey();
            lore.add("§7" + label(capability) + ": §f+" + ItemLore.formatStat(entry.getValue())
                    + (percent(capability) ? "%" : ""));
        }
        lore.addAll(effects);
        if (sleeping) {
            lore.add("");
            lore.add("§5Dungeon skills sleep outside floors.");
        }
        return named(Material.SPYGLASS, "§eLoadout Readout §8· §f" + equipped.size() + "§8/§7"
                + skills.unlockedSlots(player), lore);
    }

    private ItemStack nextSlotIcon(Player player, int unlocked) {
        int at = skills.nextSlotLevel(player);
        if (at < 0) {
            return named(
                    Material.ENDER_CHEST,
                    t(player, "§aAll seven slots open", "§aAlle sieben Plätze offen"),
                    t(player, "§7Every talent slot is yours.", "§7Jeder Talent-Platz gehört dir.")
            );
        }
        int level = skills.accountLevel(player);
        double fill = Math.min(1.0d, level / (double) at);
        return named(
                Material.TRIPWIRE_HOOK,
                t(player, "§eNext: Slot ", "§eAls Nächstes: Platz ") + (unlocked + 1),
                t(player, "§7Opens at §fAetherion Level " + at + "§7.", "§7Öffnet bei §fAetherion-Level " + at + "§7."),
                SkillProgression.miniBar(fill, "§e") + " §7" + level + "§8/§7" + at,
                "",
                t(player, "§8Every skill, quest and find counts.", "§8Jeder Skill, jede Quest, jeder Fund zählt.")
        );
    }

    private static ItemStack guideIcon(Player player) {
        StringBuilder slots = new StringBuilder();
        for (int i = 1; i < SkillService.SLOT_LEVELS.length; i++) {
            slots.append(i == 1 ? "§f " : " §8· §f").append(SkillService.SLOT_LEVELS[i]);
        }
        return named(
                Material.BOOK,
                t(player, "§eHow skills work", "§eSo funktionieren Skills"),
                t(player, "§7Six skills: §fCombat, Mining, Foraging,", "§7Sechs Skills: §fCombat, Mining, Foraging,"),
                t(player, "§fFarming, Fishing, Dungeon§7. They level", "§fFarming, Fishing, Dungeon§7. Sie steigen,"),
                t(player, "§7whenever you do the thing. Always.", "§7sobald du das Passende tust. Immer."),
                "",
                t(player, "§7Talents are the cards below. Put up to", "§7Talente sind die Karten unten. Bis zu"),
                t(player, "§7seven in your slots; each is as strong", "§7sieben passen in deine Plätze; jedes ist so"),
                t(player, "§7as your level in its skill.", "§7stark wie dein Level im passenden Skill."),
                t(player, "§7New talents open as a skill levels up.", "§7Neue Talente öffnen sich mit dem Skill-Level."),
                t(player, "§7Utility talents follow your best skill.", "§7Utility-Talente folgen deinem besten Skill."),
                "",
                t(player, "§7Slots open at Aetherion Level:", "§7Plätze öffnen bei Aetherion-Level:"),
                slots.toString(),
                "",
                t(player, "§7New rarity every §f" + SkillProgression.RARITY_EVERY + " §7skill levels:",
                        "§7Neue Seltenheit alle §f" + SkillProgression.RARITY_EVERY + " §7Skill-Level:"),
                "§f Common §aUncommon §bRare §5Epic §6Legendary §dMythic",
                "",
                t(player, "§7Every §f" + AetherionLevel.STAT_EVERY + " §7Aetherion Levels: §a+1 Damage §7& §c+1 Health§7.",
                        "§7Alle §f" + AetherionLevel.STAT_EVERY + " §7Aetherion-Level: §a+1 Schaden §7& §c+1 Leben§7."),
                t(player, "§7Auto pickup at Aetherion Level §f", "§7Auto-Pickup ab Aetherion-Level §f")
                        + de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL + "§7."
        );
    }

    private static de.aetherion.items.codex.CodexService codex() {
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        return plugin == null ? null : plugin.getCodex();
    }

    private ItemStack codexIcon(Player player) {
        int ready = de.aetherion.items.codex.CodexRewards.claimableTotal(player);
        List<String> lore = new ArrayList<>();
        lore.add("§8Collection, Bestiary, Journal and Milestones.");
        lore.add("§7Codex score §d" + de.aetherion.items.codex.CodexHubGUI.score(player));
        if (ready > 0) {
            lore.add("§e✦ " + ready + " reward" + (ready == 1 ? "" : "s") + " to claim");
        }
        lore.add("");
        lore.add("§eClick to open");
        ItemStack item = named(Material.ENCHANTED_BOOK, "§dAetherion Codex", lore);
        return ready > 0 ? glint(item) : item;
    }

    private ItemStack overviewIcon(Player player) {
        int total = 0;
        int mastered = 0;
        int count = AetherSkill.Category.LEVELED.length;
        List<String> lore = new ArrayList<>();
        lore.add(t(player, "§8Your six skills and every talent.", "§8Deine sechs Skills und alle Talente."));
        lore.add("");
        for (AetherSkill.Category skill : AetherSkill.Category.LEVELED) {
            int level = skills.skillLevel(player, skill);
            total += level;
            if (SkillProgression.isMax(level)) {
                mastered++;
            }
            lore.add("§7" + skill.plain() + " " + SkillProgression.rarity(level).getChatColor() + level);
        }
        lore.add("");
        lore.add(t(player, "§7Average §f", "§7Durchschnitt §f") + String.format(Locale.US, "%.1f", total / (double) count)
                + t(player, " §8· §7Mastered §d", " §8· §7Gemeistert §d") + mastered + "§8/§7" + count);
        lore.add("");
        lore.add(t(player, "§eClick to open", "§eKlick zum Öffnen"));
        return named(Material.KNOWLEDGE_BOOK, t(player, "§bSkill Overview", "§bSkill-Übersicht"), lore);
    }

    private ItemStack presetsIcon(Player player) {
        int saved = 0;
        int open = 0;
        for (int i = 0; i < SkillService.PRESET_COUNT; i++) {
            if (skills.presetUnlocked(player, i)) {
                open++;
                if (!skills.preset(player, i).isEmpty()) {
                    saved++;
                }
            }
        }
        return named(Material.ARMOR_STAND, "§bLoadout Presets",
                "§8Swap whole loadouts in one click.",
                "",
                "§7Saved §f" + saved + "§8/§7" + open + " §8open (" + SkillService.PRESET_COUNT + " total)",
                "§7Chat: §f/skills preset <1-3>",
                "",
                "§eClick to open");
    }

    private ItemStack sealsIcon(Player player) {
        de.aetherion.items.codex.CodexService codex = codex();
        int ready = codex == null ? 0 : SkillSeals.readyTiers(skills, codex, player);
        int[] reached = SkillSeals.reachedByRarity(skills, player);
        List<String> lore = new ArrayList<>();
        lore.add("§8A one-off reward each time a skill");
        lore.add("§8reaches a new rarity.");
        lore.add("");
        lore.add("§7Reached §aU" + reached[1] + " §bR" + reached[2] + " §5E" + reached[3]
                + " §6L" + reached[4] + " §dM" + reached[5]);
        lore.add("");
        if (ready > 0) {
            lore.add("§e§l✦ " + ready + " seal" + (ready == 1 ? "" : "s") + " ready");
            lore.add("§eShift-click §7to claim all · §eClick §7for the overview");
        } else {
            lore.add("§eClick §7for the overview");
        }
        ItemStack item = named(ready > 0 ? Material.CHEST_MINECART : Material.AMETHYST_SHARD,
                "§dRarity Seals" + (ready > 0 ? " §8(§e" + ready + "§8)" : ""), lore);
        return ready > 0 ? glint(item) : item;
    }

    private ItemStack sessionIcon(Player player) {
        SkillSession.Snapshot snap = SkillSession.of(player);
        List<String> lore = new ArrayList<>();
        lore.add("§8Since you logged in (" + snap.minutes() + " min).");
        lore.add("");
        lore.add("§7Skill XP §f" + String.format(Locale.US, "%,d", snap.xp()));
        lore.add("§7Level-ups §f" + snap.levels());
        if (snap.perHour() > 0L) {
            lore.add("§7Pace §f" + String.format(Locale.US, "%,d", snap.perHour()) + " XP/h");
        }
        if (snap.top() != null) {
            lore.add("§7Top §f" + snap.top().plain() + " §8+"
                    + String.format(Locale.US, "%,d", snap.topXp()));
        }
        return named(Material.CLOCK, "§eThis Session", lore);
    }

    private void claimAllSeals(Player player) {
        de.aetherion.items.codex.CodexService codex = codex();
        if (codex == null) {
            return;
        }
        int paid = 0;
        boolean batch = skills.beginRewardBatch(player);
        try {
            for (AetherSkill.Category skill : SkillSeals.ready(skills, codex, player)) {
                paid += SkillSeals.claim(player, skill, true);
            }
        } finally {
            if (batch) {
                skills.endRewardBatch(player);
            }
        }
        if (paid <= 0) {
            bar(player, t(player, "§7No seals ready. §8Level a skill to its next rarity.",
                    "§7Keine Siegel bereit. §8Bring einen Skill zur nächsten Seltenheit."));
            click(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f);
        }
    }

    /** Dungeon page gate (Floor 1 cleared) — shared with the overview's quick equip. */
    public static boolean dungeonUnlocked(Player player) {
        return categoryUnlocked(player, AetherSkill.Category.DUNGEON);
    }

    // ------------------------------------------------------------------ loadout row

    private ItemStack slotIcon(Player player, int index, int unlocked) {
        if (index >= unlocked) {
            int at = SkillService.SLOT_LEVELS[Math.min(index, SkillService.SLOT_LEVELS.length - 1)];
            if (index == unlocked) {
                int level = skills.accountLevel(player);
                double fill = Math.min(1.0d, level / (double) at);
                return named(
                        Material.IRON_TRAPDOOR,
                        t(player, "§eSlot ", "§ePlatz ") + (index + 1) + t(player, " §8· §eNext", " §8· §eAls Nächstes"),
                        t(player, "§7Opens at §fAetherion Level " + at + "§7.", "§7Öffnet bei §fAetherion-Level " + at + "§7."),
                        SkillProgression.miniBar(fill, "§e") + " §7" + level + "§8/§7" + at
                );
            }
            return named(
                    Material.GRAY_DYE,
                    t(player, "§8Slot ", "§8Platz ") + (index + 1) + t(player, " §7· Locked", " §7· Zu"),
                    t(player, "§7Opens at §fAetherion Level " + at + "§7.", "§7Öffnet bei §fAetherion-Level " + at + "§7.")
            );
        }
        AetherSkill skill = skills.inSlot(player, index);
        if (skill == null) {
            return named(
                    Material.ITEM_FRAME,
                    "§aSlot " + (index + 1) + " §8· §7Empty",
                    t(player, "§7Pick a skill below, then a talent.", "§7Wähl unten einen Skill, dann ein Talent."),
                    "",
                    "§8It is judging you already."
            );
        }
        int level = skills.level(player, skill);
        Rarity rarity = SkillProgression.rarity(level);
        List<String> lore = new ArrayList<>();
        lore.add(skill.category().title() + " §8· " + rarity.getChatColor() + SkillProgression.rarityName(rarity)
                + " §8· " + SkillProgression.stage(level).colored());
        lore.add(levelLine(player, skill, level));
        List<Effect> effects = effects(skill, level);
        if (!effects.isEmpty()) {
            lore.add("");
            for (Effect effect : effects) {
                lore.add(effect.render(null));
            }
        }
        if (skill.category() == AetherSkill.Category.DUNGEON
                && !de.aetherion.items.dungeon.DungeonArmor.inDungeon(player)) {
            lore.add("§5Asleep — wakes inside dungeon floors.");
        }
        lore.add("");
        lore.add(t(player, "§eClick §7to take it out", "§eKlick §7zum Rausnehmen"));
        ItemStack item = named(
                skill.icon(),
                "§7Slot " + (index + 1) + " §8· " + rarity.getChatColor() + skill.displayName(),
                lore
        );
        return glint(item);
    }

    private ItemStack stripPane(Player player, int index, int unlocked) {
        if (index >= unlocked) {
            return GuiIcons.spacer();
        }
        AetherSkill skill = skills.inSlot(player, index);
        if (skill == null) {
            return GuiIcons.icon(named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8Empty slot"), GuiIcons.SPACER);
        }
        int level = skills.level(player, skill);
        Rarity rarity = SkillProgression.rarity(level);
        List<String> lore = new ArrayList<>();
        lore.add("§7Slot " + (index + 1) + " §8· " + rarity.getChatColor() + skill.displayName()
                + " §7Lv. " + level);
        lore.add(nextRarityLine(player, skill, level));
        return named(
                SkillProgression.rarityPane(rarity),
                rarity.getChatColor() + SkillProgression.rarityName(rarity),
                lore
        );
    }

    // ------------------------------------------------------------------ categories

    private ItemStack categoryIcon(Player player, AetherSkill.Category category, AetherSkill.Category selected) {
        if (!categoryUnlocked(player, category)) {
            return named(
                    Material.BARRIER,
                    "§8Dungeon §7· Locked",
                    "§7Clear Floor 1 first.",
                    "§8Beat the Sentinel. Then this page opens.",
                    "",
                    "§cLocked"
            );
        }
        List<AetherSkill> pool = skillsOf(category);
        int equipped = 0;
        int open = 0;
        for (AetherSkill skill : pool) {
            if (skills.slotOf(player, skill) >= 0) {
                equipped++;
            }
            if (skills.talentUnlocked(player, skill)) {
                open++;
            }
        }
        boolean active = category == selected;
        int level = skills.skillLevel(player, category);
        List<String> lore = new ArrayList<>();
        if (category.leveled()) {
            if (SkillProgression.isMax(level)) {
                lore.add("§7Lv. §d" + SkillProgression.MAX_LEVEL + " §8· §dMAX");
            } else {
                lore.add("§7Lv. " + SkillProgression.rarity(level).getChatColor() + level + "§8/§7" + SkillProgression.MAX_LEVEL
                        + "  " + SkillProgression.miniBar(SkillProgression.levelFill(level, skills.skillXp(player, category)), "§a"));
            }
            lore.add(t(player, "§8Levels from " + levelsFrom(category), "§8Steigt durch " + levelsFrom(category)));
        } else {
            lore.add(t(player, "§7Follows your best skill: §f", "§7Folgt deinem besten Skill: §f") + level);
        }
        lore.add(t(player, "§7Talents open: §f", "§7Talente offen: §f") + open + "§8/§7" + pool.size());
        AetherSkill nextTalent = TalentUnlocks.next(category, level);
        if (nextTalent != null) {
            lore.add(t(player, "§8Next at " + TalentUnlocks.level(nextTalent) + ": §7", "§8Nächstes bei " + TalentUnlocks.level(nextTalent) + ": §7")
                    + nextTalent.displayName());
        }
        lore.add(equipped > 0 ? t(player, "§aIn your slots: §f", "§aIn deinen Plätzen: §f") + equipped
                : t(player, "§8None in your slots.", "§8Keins in deinen Plätzen."));
        String loop = loopHint(category);
        if (loop != null) {
            lore.add("");
            lore.add("§3In the loop:");
            for (String line : loop.split("\n")) {
                lore.add("§7" + line);
            }
        }
        lore.add("");
        lore.add(active ? t(player, "§a▶ Showing below", "§a▶ Unten zu sehen") : t(player, "§eClick to browse", "§eKlick zum Ansehen"));
        ItemStack item = named(
                category.icon(),
                (active ? "§a▶ " : "") + category.title(),
                lore
        );
        return active ? glint(item) : item;
    }

    // ------------------------------------------------------------------ pool

    private ItemStack poolIcon(Player player, AetherSkill skill, boolean loadoutFull) {
        int equippedSlot = skills.slotOf(player, skill);
        int level = skills.level(player, skill);
        Rarity rarity = SkillProgression.rarity(level);
        SkillProgression.Stage stage = SkillProgression.stage(level);
        List<String> lore = new ArrayList<>();
        lore.add(skill.category().title() + " §8· " + rarity.getChatColor() + SkillProgression.rarityName(rarity)
                + " §8· " + stage.colored());
        boolean open = skills.talentUnlocked(player, skill);
        lore.add(equippedSlot >= 0
                ? t(player, "§a✔ In slot ", "§a✔ In Platz ") + (equippedSlot + 1)
                : open ? t(player, "§8○ Ready to use", "§8○ Bereit") : lockedLine(player, skill));
        lore.add("");
        lore.add(levelLine(player, skill, level));
        lore.add(nextRarityLine(player, skill, level));
        lore.add("");
        lore.add("§7“" + SkillFlavor.tagline(skill, level) + "”");

        int nextRarity = SkillProgression.nextRarityLevel(level);
        List<Effect> now = effects(skill, level);
        List<Effect> later = nextRarity > 0 ? effects(skill, nextRarity) : List.of();
        if (!now.isEmpty()) {
            lore.add("");
            for (int i = 0; i < now.size(); i++) {
                Effect preview = i < later.size() ? later.get(i) : null;
                lore.add(now.get(i).render(preview));
            }
            if (!later.isEmpty()) {
                lore.add("§8→ values at " + SkillProgression.rarity(nextRarity).getChatColor()
                        + SkillProgression.rarityName(SkillProgression.rarity(nextRarity)) + " §8(Lv. " + nextRarity + ")");
            }
        } else {
            lore.add("");
            lore.add("§7" + skill.details());
        }
        String inLoop = skillLoopLine(skill);
        if (inLoop != null) {
            lore.add("§3" + inLoop);
        }
        de.aetherion.items.codex.CodexService codex = codex();
        if (codex != null && skill.category().leveled()) {
            lore.add(SkillSeals.pips(skills, codex, player, skill.category()));
            if (SkillSeals.ready(skills, codex, player, skill.category()) > 0) {
                lore.add(t(player, "§e§l✦ Rarity seal ready §8— §eShift-click §7to claim",
                        "§e§l✦ Siegel bereit §8— §eShift-Klick §7zum Abholen"));
            }
        }
        if (skill.category() == AetherSkill.Category.DUNGEON) {
            lore.add("§5Dungeon only — asleep in the overworld.");
        }
        lore.add("");
        if (equippedSlot >= 0) {
            lore.add(t(player, "§eClick §7to take it out", "§eKlick §7zum Rausnehmen"));
        } else if (!open) {
            lore.add(lockedLine(player, skill));
        } else if (loadoutFull) {
            lore.add(t(player, "§cNo free slot §8— click a talent up top.", "§cKein Platz frei §8— klick oben auf ein Talent."));
        } else {
            lore.add(t(player, "§eClick §7to put it in §8→ Slot ", "§eKlick §7zum Einsetzen §8→ Platz ")
                    + (firstFreeSlot(player, skills.unlockedSlots(player)) + 1));
        }
        ItemStack item = named(
                open ? skill.icon() : Material.GRAY_DYE,
                (open ? rarity.getChatColor() : "§8") + skill.displayName(),
                lore
        );
        return equippedSlot >= 0 ? glint(item) : item;
    }

    private String levelLine(Player player, AetherSkill skill, int level) {
        if (SkillProgression.isMax(level)) {
            return "§7Lv. §d" + SkillProgression.MAX_LEVEL + " §8· §dMAX. It has nothing left to prove.";
        }
        int current = skills.xp(player, skill);
        int needed = SkillProgression.xpToNext(level);
        double fill = SkillProgression.levelFill(level, current);
        return "§7Lv. §f" + level + "§8/§7" + SkillProgression.MAX_LEVEL + "  "
                + SkillProgression.miniBar(fill, "§a") + " §7" + current + "§8/§7" + needed;
    }

    private String nextRarityLine(Player player, AetherSkill skill, int level) {
        int next = SkillProgression.nextRarityLevel(level);
        if (next < 0) {
            return "§dMythic reached. §8Top shelf.";
        }
        Rarity rarity = SkillProgression.rarity(next);
        int levels = next - level;
        long xp = SkillProgression.xpUntil(level, skills.xp(player, skill), next);
        return "§8Next: " + rarity.getChatColor() + SkillProgression.rarityName(rarity)
                + " §8at Lv. §7" + next + " §8(" + levels + (levels == 1 ? " level" : " levels")
                + " · " + String.format(Locale.US, "%,d", xp) + " XP)";
    }

    // ------------------------------------------------------------------ effects

    /** One lore line of an effect; {@code note} lines are plain text with no number. */
    private record Effect(String label, double value, String prefix, String suffix, boolean note) {

        static Effect stat(String label, double value, String prefix, String suffix) {
            return new Effect(label, value, prefix, suffix, false);
        }

        static Effect hint(String text) {
            return new Effect(text, 0.0d, "", "", true);
        }

        String format(double amount) {
            return prefix + ItemLore.formatStat(amount) + suffix;
        }

        String render(Effect preview) {
            if (note) {
                return "§8" + label;
            }
            String line = "§7" + label + ": §f" + format(value);
            if (preview != null && !preview.note && label.equals(preview.label)
                    && Math.abs(preview.value - value) > 0.0001d) {
                line += " §8→ §a" + preview.format(preview.value);
            }
            return line;
        }
    }

    private static List<Effect> effects(AetherSkill skill, int level) {
        List<Effect> lines = new ArrayList<>();
        for (var entry : skill.bonuses().entrySet()) {
            ItemCapability capability = entry.getKey();
            lines.add(Effect.stat(label(capability), entry.getValue() * SkillProgression.bonusMultiplier(level, capability),
                    "+", percent(capability) ? "%" : ""));
        }
        lines.addAll(flagEffects(skill, level));
        return lines;
    }

    private static List<Effect> flagEffects(AetherSkill skill, int level) {
        List<Effect> lines = new ArrayList<>();
        double scale = SkillProgression.effectMultiplier(level);
        int tier = SkillProgression.rarityTier(level);
        switch (skill.flag()) {
            case PACK_RAT -> compactLines(lines, "Mining Compact", "Compacted Upgrade", level, tier, "drops");
            case TIMBER_TAX -> compactLines(lines, "Oak Compact", "Compacted Oak", level, tier, "oak");
            case SEED_LEDGER -> compactLines(lines, "Crop Compact", "Compacted Crops", level, tier, "crops");
            case SOIL_SENSE -> lines.add(Effect.hint("Stats apply on the Farming Isle only."));
            case ROW_RHYTHM -> {
                lines.add(Effect.stat("Glint streak per catch", 1.0 + 0.5 * scale, "+", ""));
                lines.add(Effect.stat("Shine time", 40.0 * scale, "+", "%"));
            }
            case BLUE_RIBBON -> lines.add(Effect.stat("Prize Crop chance", 50.0 * scale, "+", "%"));
            case BIRD_LAW -> {
                lines.add(Effect.hint("Bold crows leave on the first shoo."));
                lines.add(Effect.stat("Golden Hour", Math.round(8.0 * scale), "+", "s"));
            }
            case MARKET_DAY -> lines.add(Effect.stat("Order coins", Math.min(60.0, 15.0 * scale), "+", "%"));
            case FISH_LEDGER -> compactLines(lines, "Cod Compact", "Compacted Cod", level, tier, "cod");
            case LAKE_SENSE -> lines.add(Effect.hint("Stats apply on the Fishing Isle only."));
            case STEADY_LINE -> {
                lines.add(Effect.hint("Gold cell two wide on the Fishing Isle."));
                lines.add(Effect.stat("Boiling streak save", 25.0 + 25.0 * Math.min(1.0, scale / 3.0), "", "%"));
            }
            case TALL_TALES -> {
                lines.add(Effect.stat("Rare & legendary bites", 50.0 * Math.min(1.0, scale / 3.0), "+", "%"));
                lines.add(Effect.hint(scale >= 1.5 ? "Fish weigh in heavier (extra weight roll)."
                        : "Extra weight roll from about Lv. 40."));
            }
            case TIDE_READER -> {
                lines.add(Effect.stat("Shoal wait", 24.0 * Math.min(1.0, scale / 3.0), "-", "%"));
                lines.add(Effect.hint("On top of the shoal's own half wait."));
                lines.add(Effect.hint("Wake arrow to the shoal while you hold a rod."));
            }
            case QUICK_HANDS -> lines.add(Effect.stat("Ability Cooldown",
                    100.0 - (Math.max(0.70, 1.0 - 0.10 * scale) * 100.0), "-", "%"));
            case BOSS_GRUDGE -> lines.add(Effect.stat("Boss Damage", 10.0 * scale, "+", "%"));
            case FLOOR_GRUDGE -> lines.add(Effect.stat("Dungeon Boss Damage", 12.0 * scale, "+", "%"));
            case RELIC_APPETITE -> lines.add(Effect.stat("Dungeon Gear XP", 20.0 * scale, "+", "%"));
            case BLOOD_TAX -> lines.add(Effect.stat("Kill Coins",
                    SkillService.bloodTaxCurve(level) * 100.0, "+", "% of HP"));
            case LIFE_ABSORB -> lines.add(Effect.stat("Lifesteal", 5.0 * scale, "+", "% healed"));
            case GOLDEN_HOUR -> lines.add(Effect.stat("Coin Payouts", 25.0 * scale, "+", "%"));
            case NIGHT_OWL -> {
                // Speed is gear/pets/Lv.3 pace only — Night Owl no longer grants move speed.
            }
            case CAVE_SENSE -> lines.add(Effect.stat("Mining Power in the dark", 10.0 * scale, "+", ""));
            case BEDROCK_BORN -> lines.add(Effect.hint("Stats apply on the Mining Isle only."));
            case WORK_SONG -> {
                lines.add(Effect.stat("Rhythm per strike", 1.0 + 0.5 * scale, "+", ""));
                lines.add(Effect.stat("Rhythm hold", 40.0 * scale, "+", "%"));
            }
            case GEODE_NOSE -> {
                lines.add(Effect.stat("Crystal Find chance", 50.0 * scale, "+", "%"));
                lines.add(Effect.hint("Rolls weight twice · one less crack."));
            }
            case UNION_CARD -> lines.add(Effect.stat("Contract coins", Math.min(60.0, 15.0 * scale), "+", "%"));
            case SEAM_READER -> {
                lines.add(Effect.stat("Seam Chain burst at", Math.max(4.0, 8.0 - Math.floor(scale * 1.5)), "", " strikes"));
                lines.add(Effect.hint("Nearby ore shimmers every ~30s."));
            }
            case DEPTH_GAUGE -> lines.add(Effect.stat("Depth bonuses", 50.0 * scale, "+", "%"));
            case GROVE_BORN -> {
                lines.add(Effect.stat("Wood cap per tree", Math.round(Math.min(4.0, 2.0 * scale)), "+", ""));
                lines.add(Effect.stat("Extra mastery count", Math.min(60.0, 25.0 * scale), "", "% chance"));
                lines.add(Effect.hint("Applies on the Foraging Isle only."));
            }
            case SAP_SENSE -> {
                lines.add(Effect.stat("Crown Find chance", 50.0 * scale, "+", "%"));
                lines.add(Effect.hint("Better grades — Heartsong included."));
            }
            case STEADY_HANDS -> {
                lines.add(Effect.hint("The glowing spot on a trunk stays lit longer."));
                lines.add(Effect.stat("Miss cooldown", 25.0 * Math.min(2.0, scale), "-", "%"));
            }
            case DEADFALL_DANCER -> {
                lines.add(Effect.hint("Widowmaker warning lasts half a second longer."));
                lines.add(Effect.hint("Limbs hurt half · deadfall wood ×2."));
            }
            case BOARD_RATES -> lines.add(Effect.stat("Board order coins", Math.min(60.0, 15.0 * scale), "+", "%"));
            case HEART_HUNTER -> lines.add(Effect.stat("Heartwood chance", 50.0 * scale, "+", "%"));
            case PINCH_PENNY -> lines.add(Effect.stat("Midas Cost",
                    Math.max(2L, 5L - tier), "", " coins"));
            case DIAMOND_SPINE -> lines.add(Effect.stat("Melee Reflect", 8.0 * scale, "+", "%"));
            default -> {
            }
        }
        return lines;
    }

    private static void compactLines(List<Effect> lines, String label, String upgradeLabel,
                                     int level, int tier, String noun) {
        lines.add(Effect.stat(label, SkillProgression.compactChancePercent(level), "+", "%"));
        if (tier >= 4) {
            lines.add(Effect.stat(upgradeLabel, SkillService.compactedChancePercent(tier), "", "% of procs"));
        } else {
            lines.add(Effect.hint("Compacted " + noun + " from Legendary onward."));
        }
    }

    /** What the category's XP actually comes from — no wiki needed. */
    private static String levelsFrom(AetherSkill.Category category) {
        return switch (category) {
            case COMBAT -> "kills";
            case MINING -> "ore & stone";
            case FORAGING -> "logs & fells";
            case FARMING -> "crops & field events";
            case FISHING -> "catches & sea mobs";
            case UTILITY -> "everything (½)";
            case DUNGEON -> "kills";
        };
    }

    /** How the category's stats touch its gathering loop. {@code null} for combat pages. */
    private static String loopHint(AetherSkill.Category category) {
        return switch (category) {
            case MINING -> "Mining Power opens harder ore.\nFortune multiplies what breaks.\nOn Mining Isle: Rhythm, Crystal Finds, Contracts.";
            case FORAGING -> "Hit a trunk until it falls; better axes need fewer hits.\nThe glowing spot counts twice; a clean cut pays bonus XP.\nLv. 50 keeps the spot lit longer.\nFortune raises each tree's wood cap.\nOn the Foraging Isle: Mastery, Crown Finds, the Board.";
            case FARMING -> "Harvest breaks extra crops per swing.\nClearing birds boosts the whole field.\nOn the Farming Isle: Ripe Glint, Prize Crops, Orders.";
            case FISHING -> "Fish Catch widens the green zone.\nFish Speed shortens the wait.\nPerfect reels and streaks pay bonus XP.";
            default -> null;
        };
    }

    /** One line tying a gathering skill's main stat to the minigame. */
    private static String skillLoopLine(AetherSkill skill) {
        if (skill.bonus(ItemCapability.FISHING_CATCH) > 0) {
            return "Wider strike zone · more extra catches.";
        }
        if (skill.bonus(ItemCapability.FISHING_SPEED) > 0) {
            return "Shorter wait before the bite.";
        }
        if (skill.bonus(ItemCapability.HARVEST_SPREAD) > 0) {
            return "Breaks extra mature crops each swing.";
        }
        if (skill.category() == AetherSkill.Category.FORAGING && skill.bonus(ItemCapability.FORTUNE) > 0) {
            return "+1 wood cap per 25 Fortune on every fell.";
        }
        if (skill.bonus(ItemCapability.MINING_POWER) > 0) {
            return "Opens tougher ore tiers sooner.";
        }
        if (skill.bonus(ItemCapability.SPREAD) > 0) {
            return "Extra blocks break with each swing.";
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private int firstFreeSlot(Player player, int unlocked) {
        for (int i = 0; i < unlocked; i++) {
            if (skills.inSlot(player, i) == null) {
                return i;
            }
        }
        return -1;
    }

    private AetherSkill.Category defaultCategory(Player player) {
        for (AetherSkill skill : skills.equipped(player)) {
            if (categoryUnlocked(player, skill.category())) {
                return skill.category();
            }
        }
        return AetherSkill.Category.COMBAT;
    }

    private static boolean categoryUnlocked(Player player, AetherSkill.Category category) {
        if (category != AetherSkill.Category.DUNGEON) {
            return true;
        }
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin == null || plugin.getCodex() == null) {
            return false;
        }
        return plugin.getCodex().bossKills(player, FLOOR_ONE_BOSS) > 0;
    }

    private static boolean percent(ItemCapability capability) {
        return capability == ItemCapability.SPEED
                || capability == ItemCapability.CRIT_CHANCE
                || capability == ItemCapability.CRIT_DAMAGE
                || capability == ItemCapability.PET_CATCH_RATE;
    }

    private static String label(ItemCapability capability) {
        return switch (capability) {
            case MINING_POWER -> "Mining Power";
            case FORTUNE -> "Fortune";
            case DAMAGE -> "Damage";
            case DEFENSE -> "Defense";
            case HEALTH -> "Health";
            case SPREAD -> "Spread";
            case HARVEST_SPREAD -> "Harvest";
            case FISHING_SPEED -> "Fish Speed";
            case FISHING_CATCH -> "Fish Catch";
            case ATTACK_SPREAD -> "Attack Spread";
            case SPEED -> "Speed";
            case PET_CATCH_RATE -> "Catch Rate";
            case CRIT_CHANCE -> "✧ Crit Chance";
            case CRIT_DAMAGE -> "✧ Crit Damage";
            default -> capability.name().toLowerCase(Locale.ROOT);
        };
    }

    private String displayRank(Player player, int account) {
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin != null && plugin.ranks() != null) {
            return plugin.ranks().displayTitle(player);
        }
        return AetherionLevel.coloredTitle(account);
    }

    private static List<AetherSkill> skillsOf(AetherSkill.Category category) {
        List<AetherSkill> pool = new ArrayList<>();
        if (category == null) {
            return pool;
        }
        for (AetherSkill skill : AetherSkill.values()) {
            if (skill.category() == category) {
                pool.add(skill);
            }
        }
        return pool;
    }

    private static int categoryIndex(int slot) {
        for (int i = 0; i < CATEGORY_SLOTS.length && i < CATEGORIES.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static int poolIndex(int slot) {
        for (int i = 0; i < POOL_SLOTS.length; i++) {
            if (POOL_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static String coins(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    /** Every slot an invisible spacer: the book page (tools/gen_gui_chrome.py PAGES['skills']) draws the rest. */
    private static void fill(Inventory inventory, AetherSkill.Category category) {
        ItemStack spacer = GuiIcons.spacer();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, spacer.clone());
        }
    }

    private static ItemStack glint(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            GuiItems.hideVanilla(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        return GuiItems.named(material, name, lore);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        return GuiItems.named(material, name, lore);
    }

    public static final class Holder implements InventoryHolder {
        private AetherSkill.Category category;
        private Inventory inventory;

        public Holder() {
            this(null);
        }

        public Holder(AetherSkill.Category category) {
            this.category = category;
        }

        public AetherSkill.Category category() {
            return category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
