package de.aetherion.items.skill;

import de.aetherion.items.item.ItemLore;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;
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
 * row 0  [ ][ ][Readout][ ][Profile][ ][Next slot][ ][Guide]
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
    private static final int FIRST_LOADOUT = 10;
    private static final int FIRST_STRIP = 19;
    /** Categories stay here permanently — never swapped for skills. */
    private static final int[] CATEGORY_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    /** Side panes tinted in the selected category's color — frames the pool. */
    private static final int[] FRAME_SLOTS = {27, 35, 36, 44, 53};
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

    public SkillMenu(SkillService skills) {
        this.skills = skills;
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, AetherSkill.Category category) {
        if (player == null) {
            return;
        }
        AetherSkill.Category shown = category != null && categoryUnlocked(player, category)
                ? category
                : defaultCategory(player);
        Holder holder = new Holder(shown);
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE);
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
        inventory.setItem(GUIDE_SLOT, guideIcon());

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

        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
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
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (slot >= FIRST_LOADOUT && slot < FIRST_LOADOUT + SkillService.SLOT_COUNT) {
            int index = slot - FIRST_LOADOUT;
            if (index >= skills.unlockedSlots(player)) {
                player.sendMessage(skills.unlockHint(index));
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
                player.sendMessage("§cDungeon skills unlock after clearing Floor 1.");
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
            player.sendMessage("§cNo free slot. §7Click an equipped skill up top to free one"
                    + (skills.unlockedSlots(player) < SkillService.SLOT_COUNT ? ", or earn the next slot." : "."));
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
        player.sendMessage("§a✚ Slot " + (slot + 1) + " §8· " + skills.coloredName(player, skill)
                + " §7Lv. " + level + " §8— §7" + SkillFlavor.tagline(skill, level));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 0.9f + tier * 0.1f);
        if (tier >= 4) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.1f + (tier - 4) * 0.2f);
        }
    }

    private void announceUnequip(Player player, AetherSkill skill, int slot) {
        player.sendMessage("§7− Slot " + (slot + 1) + " §8· " + skills.coloredName(player, skill)
                + " §8(progress kept)");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 0.8f);
    }

    private static void click(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 0.6f, pitch);
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
        lore.add("§8Every skill level feeds this bar.");
        lore.add("§8Pets and quests top it up.");
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
                double scale = SkillProgression.effectMultiplier(level);
                for (var entry : skill.bonuses().entrySet()) {
                    totals.merge(entry.getKey(), entry.getValue() * scale, Double::sum);
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
        long cost = skills.nextSlotCost(player);
        if (cost < 0L) {
            return named(
                    Material.ENDER_CHEST,
                    "§aAll seven slots open",
                    "§7The whole locker is yours.",
                    "§8Don't get greedy."
            );
        }
        long have = skills.lifetimeCoins(player);
        double fill = cost <= 0L ? 1.0d : Math.min(1.0d, have / (double) cost);
        return named(
                Material.TRIPWIRE_HOOK,
                "§eNext: Slot " + (unlocked + 1),
                "§7Opens at §f" + coins(cost) + " §7lifetime coins.",
                SkillProgression.miniBar(fill, "§e") + " §7" + (int) Math.floor(fill * 100.0d) + "%",
                "§7You: §f" + coins(have) + " §8(" + coins(Math.max(0L, cost - have)) + " to go)",
                "",
                "§8Lifetime counts what you earned —",
                "§8spending never sets you back."
        );
    }

    private static ItemStack guideIcon() {
        return named(
                Material.BOOK,
                "§eField Guide",
                "§7Seven slots. §fSlot 1 §7is free.",
                "§7The rest open with lifetime Legacy coins:",
                "§f 5k §8· §f25k §8· §f80k §8· §f200k §8· §f500k §8· §f1.25M",
                "",
                "§7Skills only level while equipped.",
                "§7Swap freely — progress stays with the skill.",
                "§7Utility skills learn from everything, at half rate.",
                "",
                "§7New rarity every §f" + SkillProgression.RARITY_EVERY + " §7levels:",
                "§f Common §aUncommon §bRare §5Epic §6Legendary §dMythic",
                "",
                "§7The curve:",
                " " + SkillProgression.Stage.APPRENTICE.colored() + " §8"
                        + SkillProgression.stageRange(SkillProgression.Stage.APPRENTICE) + " §7gentle",
                " " + SkillProgression.Stage.JOURNEYMAN.colored() + " §8"
                        + SkillProgression.stageRange(SkillProgression.Stage.JOURNEYMAN) + " §7climbs faster",
                " " + SkillProgression.Stage.MASTER.colored() + " §8"
                        + SkillProgression.stageRange(SkillProgression.Stage.MASTER) + " §7steepest",
                " " + SkillProgression.Stage.MASTERED.colored() + " §8"
                        + SkillProgression.stageRange(SkillProgression.Stage.MASTERED),
                "",
                "§7Aetherion Level: §f100 XP §7= §f1 level§7.",
                "§7Every §f" + AetherionLevel.STAT_EVERY + " §7levels: §a+1 Damage §7& §c+1 Health§7.",
                "§7Auto pickup at Aetherion §f"
                        + de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL + "§7."
        );
    }

    // ------------------------------------------------------------------ loadout row

    private ItemStack slotIcon(Player player, int index, int unlocked) {
        if (index >= unlocked) {
            long cost = SkillService.COIN_UNLOCK[Math.min(index, SkillService.COIN_UNLOCK.length - 1)];
            if (index == unlocked) {
                long have = skills.lifetimeCoins(player);
                double fill = cost <= 0L ? 1.0d : Math.min(1.0d, have / (double) cost);
                return named(
                        Material.IRON_TRAPDOOR,
                        "§eSlot " + (index + 1) + " §8· §eNext",
                        "§7Opens at §f" + coins(cost) + " §7lifetime coins.",
                        SkillProgression.miniBar(fill, "§e") + " §7" + (int) Math.floor(fill * 100.0d) + "%",
                        "",
                        "§8Keep working. It's listening."
                );
            }
            return named(
                    Material.GRAY_DYE,
                    "§8Slot " + (index + 1) + " §7· Locked",
                    "§7Opens at §f" + coins(cost) + " §7lifetime coins."
            );
        }
        AetherSkill skill = skills.inSlot(player, index);
        if (skill == null) {
            return named(
                    Material.ITEM_FRAME,
                    "§aSlot " + (index + 1) + " §8· §7Empty",
                    "§7Pick a category below, then a skill.",
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
        lore.add("§eClick §7to unequip §8(progress is kept)");
        ItemStack item = named(
                skill.icon(),
                "§7Slot " + (index + 1) + " §8· " + rarity.getChatColor() + skill.displayName(),
                lore
        );
        return glint(item);
    }

    private ItemStack stripPane(Player player, int index, int unlocked) {
        if (index >= unlocked) {
            return named(Material.BLACK_STAINED_GLASS_PANE, " ");
        }
        AetherSkill skill = skills.inSlot(player, index);
        if (skill == null) {
            return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8Empty slot");
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
        AetherSkill best = null;
        int bestLevel = 0;
        for (AetherSkill skill : pool) {
            int level = skills.level(player, skill);
            if (best == null || level > bestLevel) {
                best = skill;
                bestLevel = level;
            }
            if (skills.slotOf(player, skill) >= 0) {
                equipped++;
            }
        }
        boolean active = category == selected;
        List<String> lore = new ArrayList<>();
        lore.add("§8" + pool.size() + " skills §8· levels from " + levelsFrom(category));
        if (best != null && bestLevel > 1) {
            lore.add("§7Best: " + SkillProgression.rarity(bestLevel).getChatColor() + best.displayName()
                    + " §f" + bestLevel);
        }
        lore.add(equipped > 0 ? "§aEquipped here: §f" + equipped : "§8Nothing equipped from here.");
        String loop = loopHint(category);
        if (loop != null) {
            lore.add("");
            lore.add("§3In the loop:");
            for (String line : loop.split("\n")) {
                lore.add("§7" + line);
            }
        }
        lore.add("");
        lore.add(active ? "§a▶ Showing below" : "§eClick to browse");
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
        lore.add(equippedSlot >= 0
                ? "§a✔ Equipped §8· §aSlot " + (equippedSlot + 1)
                : "§8○ In the pool");
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
        if (skill.category() == AetherSkill.Category.DUNGEON) {
            lore.add("§5Dungeon only — asleep in the overworld.");
        }
        lore.add("");
        if (equippedSlot >= 0) {
            lore.add("§eClick §7to unequip §8(progress is kept)");
        } else if (loadoutFull) {
            lore.add("§cNo free slot §8— click an equipped skill up top.");
        } else {
            lore.add("§eClick §7to equip §8→ Slot " + (firstFreeSlot(player, skills.unlockedSlots(player)) + 1));
            lore.add("§8Levels while equipped.");
        }
        ItemStack item = named(
                skill.icon(),
                rarity.getChatColor() + skill.displayName(),
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
        double scale = SkillProgression.effectMultiplier(level);
        for (var entry : skill.bonuses().entrySet()) {
            ItemCapability capability = entry.getKey();
            lines.add(Effect.stat(label(capability), entry.getValue() * scale, "+", percent(capability) ? "%" : ""));
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
            case FISH_LEDGER -> compactLines(lines, "Cod Compact", "Compacted Cod", level, tier, "cod");
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
            case MINING -> "Mining Power opens harder ore.\nFortune multiplies what breaks.";
            case FORAGING -> "Clean fells pay bonus XP; Perfect pays more.\nLv. 50 widens the CHOP window.\nFortune raises each tree's wood cap.";
            case FARMING -> "Harvest breaks extra crops per swing.\nClearing birds boosts the whole field.\nHigher Farming holds the boost longer.";
            case FISHING -> "Fish Catch widens the green zone.\nFish Speed shortens the wait.\nPerfect reels pay bonus XP.";
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

    private static Material categoryPane(AetherSkill.Category category) {
        if (category == null) {
            return Material.GRAY_STAINED_GLASS_PANE;
        }
        return switch (category) {
            case COMBAT -> Material.RED_STAINED_GLASS_PANE;
            case MINING -> Material.BLUE_STAINED_GLASS_PANE;
            case FORAGING -> Material.GREEN_STAINED_GLASS_PANE;
            case FARMING -> Material.ORANGE_STAINED_GLASS_PANE;
            case FISHING -> Material.CYAN_STAINED_GLASS_PANE;
            case UTILITY -> Material.YELLOW_STAINED_GLASS_PANE;
            case DUNGEON -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    private static void fill(Inventory inventory, AetherSkill.Category category) {
        ItemStack header = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack body = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, (slot < 9 ? header : body).clone());
        }
        ItemStack frame = named(categoryPane(category), " ");
        for (int slot : FRAME_SLOTS) {
            inventory.setItem(slot, frame.clone());
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
