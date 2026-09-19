package de.aetherion.items.skill;

import de.aetherion.items.item.ItemLore;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.List;
import java.util.Locale;

public final class SkillMenu implements Listener {

    public static final String TITLE = "§8Aetherion Skills";
    public static final int BACK_SLOT = de.aetherion.items.util.ManagerNav.SLOT;
    private static final int INFO_SLOT = 4;
    private static final int FIRST_LOADOUT = 10;
    /** Categories stay here permanently — never swapped for skills. */
    private static final int[] CATEGORY_SLOTS = {28, 29, 30, 31, 32, 33, 34};
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
        Inventory inventory = Bukkit.createInventory(new Holder(category), 54, TITLE);
        fill(inventory);

        int unlocked = skills.unlockedSlots(player);
        int account = skills.accountLevel(player);
        List<String> info = new ArrayList<>(List.of(
                "§7Aetherion Level " + AetherionLevel.coloredLevel(account) + "§8/§f" + AetherionLevel.MAX_LEVEL,
                AetherionLevel.bar(skills.accountXp(player)),
                "§7Rank: " + displayRank(player, account)
        ));
        int levelBonus = AetherionLevel.milestoneStatBonus(account);
        if (levelBonus > 0) {
            info.add("§7Level bonus: §a+" + levelBonus + " Damage §8· §c+" + levelBonus + " Health");
        }
        var boost = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().xpBoost();
        if (boost != null && boost.active(player)) {
            info.add("§5Blood Phial: " + de.aetherion.items.shop.XpBoosterService.buffSummary()
                    + " §8· §f" + boost.formatted(player));
        }
        info.addAll(List.of(
                "",
                "§7Seven slots. You start with one.",
                "§7Legacy coins unlock the rest.",
                "",
                "§7Unlocked: §f" + unlocked + "§7/7",
                "§7Skills level while equipped. Swap freely.",
                "§7Max §f" + SkillProgression.MAX_LEVEL + "§7. New rarity every §f"
                        + SkillProgression.RARITY_EVERY + " §7levels.",
                "§7Aetherion Level: §f100 XP §7= §f1 level§7. Soft cap §f"
                        + AetherionLevel.MAX_LEVEL + "§7.",
                "§7Every §f" + AetherionLevel.STAT_EVERY + " §7levels: §a+1 Damage §7& §c+1 Health§7.",
                "§7Auto pickup at Aetherion §f" + de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL + "§7.",
                "§7Ranks: §f25§7–§f500§7 early, then §f750§7, §f1250§7, §f2500§7, §f5000§7.",
                "",
                category == null
                        ? "§8Click a category. Skills appear below."
                        : "§8" + strip(category.title()) + " — pick from the bottom rows."
        ));
        inventory.setItem(INFO_SLOT, named(
                Material.NETHER_STAR,
                "§6Skill Loadout  " + AetherionLevel.coloredLevel(account),
                info.toArray(String[]::new)
        ));

        for (int i = 0; i < SkillService.SLOT_COUNT; i++) {
            inventory.setItem(FIRST_LOADOUT + i, slotIcon(player, i, unlocked));
        }

        // Categories always stay put — only the bottom pool swaps.
        for (int i = 0; i < CATEGORIES.length && i < CATEGORY_SLOTS.length; i++) {
            inventory.setItem(CATEGORY_SLOTS[i], categoryIcon(player, CATEGORIES[i], category));
        }

        if (category != null && categoryUnlocked(player, category)) {
            List<AetherSkill> pool = skillsOf(category);
            for (int i = 0; i < pool.size() && i < POOL_SLOTS.length; i++) {
                inventory.setItem(POOL_SLOTS[i], poolIcon(player, pool.get(i)));
            }
        } else {
            inventory.setItem(POOL_SLOTS[0], named(
                    Material.BOOK,
                    "§7Pick a category",
                    "§8Skills show here. Categories stay above."
            ));
        }

        inventory.setItem(BACK_SLOT, de.aetherion.items.util.ManagerNav.button());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }
        if (slot >= FIRST_LOADOUT && slot < FIRST_LOADOUT + SkillService.SLOT_COUNT) {
            int index = slot - FIRST_LOADOUT;
            if (index >= skills.unlockedSlots(player)) {
                player.sendMessage(skills.unlockHint(index));
                return;
            }
            if (skills.unequipSlot(player, index)) {
                player.sendMessage("§7Slot cleared. The build flinched.");
            }
            open(player, holder.category());
            return;
        }
        int categoryIndex = categoryIndex(slot);
        if (categoryIndex >= 0) {
            AetherSkill.Category category = CATEGORIES[categoryIndex];
            if (!categoryUnlocked(player, category)) {
                player.sendMessage("§cDungeon skills unlock after clearing Floor 1.");
                return;
            }
            // Same category again → keep it; just refresh. Smooth switch otherwise.
            open(player, category);
            return;
        }
        if (holder.category() == null) {
            return;
        }
        int poolIndex = poolIndex(slot);
        List<AetherSkill> pool = skillsOf(holder.category());
        if (poolIndex < 0 || poolIndex >= pool.size()) {
            return;
        }
        AetherSkill skill = pool.get(poolIndex);
        if (skills.slotOf(player, skill) >= 0) {
            skills.unequip(player, skill);
            player.sendMessage("§7Unequipped §f" + skill.displayName() + "§7.");
            open(player, holder.category());
            return;
        }
        if (skills.equip(player, skill)) {
            player.sendMessage("§aEquipped §f" + skill.displayName() + "§a.");
        } else {
            player.sendMessage("§cNo free slot. Unequip something or unlock more.");
        }
        open(player, holder.category());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private ItemStack slotIcon(Player player, int index, int unlocked) {
        if (index >= unlocked) {
            return named(
                    Material.GRAY_DYE,
                    "§8Slot " + (index + 1) + " §7· Locked",
                    "",
                    skills.unlockHint(index)
            );
        }
        AetherSkill skill = skills.inSlot(player, index);
        if (skill == null) {
            return named(
                    Material.LIME_STAINED_GLASS_PANE,
                    "§aSlot " + (index + 1) + " §7· Empty",
                    "§7Open a category and click a skill.",
                    "",
                    "§8It is judging you already."
            );
        }
        ItemStack item = poolIcon(player, skill);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int level = skills.level(player, skill);
            var rarity = de.aetherion.items.skill.SkillProgression.rarity(level);
            meta.setDisplayName("§aSlot " + (index + 1) + " §8· " + rarity.getChatColor() + skill.displayName());
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§eClick to unequip");
            lore.add("§8Progress is kept.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

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
        int totalLevel = 0;
        for (AetherSkill skill : pool) {
            totalLevel += skills.level(player, skill);
            if (skills.slotOf(player, skill) >= 0) {
                equipped++;
            }
        }
        boolean active = category == selected;
        List<String> lore = new ArrayList<>();
        lore.add("§7" + pool.size() + " skills in this page.");
        lore.add(equipped > 0 ? "§aEquipped here: §f" + equipped : "§8Nothing equipped from here.");
        lore.add("§7Combined levels: §f" + totalLevel);
        lore.add("");
        if (active) {
            lore.add("§aSelected — skills below.");
            lore.add("§8Click another category to switch.");
        } else {
            lore.add("§eClick to show skills below");
        }
        ItemStack item = named(
                category.icon(),
                (active ? "§a▶ " : "") + category.title(),
                lore.toArray(String[]::new)
        );
        if (active) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                GuiItems.hideVanilla(meta);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack poolIcon(Player player, AetherSkill skill) {
        int equippedSlot = skills.slotOf(player, skill);
        int level = skills.level(player, skill);
        var rarity = SkillProgression.rarity(level);
        List<String> lore = new ArrayList<>();
        lore.add(skill.category().title() + " §8· " + rarity.getChatColor() + rarity.name());
        lore.add("§7Level §f" + level + "§8/§7" + SkillProgression.MAX_LEVEL);
        lore.add(xpBar(player, skill, level));
        lore.add("§8After 50, bonuses climb faster.");
        int rarityBonus = SkillProgression.rarityBonusPercent(level);
        if (rarityBonus > 0) {
            lore.add("§6Rarity bonus: §f+" + rarityBonus + "%");
        } else {
            lore.add("§8Next rarity at Lv. 10");
        }
        if (skill.category() == AetherSkill.Category.DUNGEON) {
            lore.add("§5Dungeon only — dead in the overworld.");
        }
        lore.add("");
        lore.add("§7" + SkillFlavor.tagline(skill, level));
        List<String> stats = bonusLines(player, skill, level);
        if (!stats.isEmpty()) {
            lore.add("");
            lore.addAll(stats);
        }
        lore.add("");
        if (equippedSlot >= 0) {
            lore.add("§aEquipped in slot " + (equippedSlot + 1));
            lore.add("§eClick to unequip");
        } else {
            lore.add("§eClick to equip");
            lore.add("§8Levels while equipped.");
        }
        ItemStack item = named(
                skill.icon(),
                rarity.getChatColor() + skill.displayName(),
                lore.toArray(String[]::new)
        );
        if (equippedSlot >= 0) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                GuiItems.hideVanilla(meta);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private String xpBar(Player player, AetherSkill skill, int level) {
        if (SkillProgression.isMax(level)) {
            return "§6MAX. It has nothing left to prove.";
        }
        int current = skills.xp(player, skill);
        int needed = SkillProgression.xpToNext(level);
        int filled = needed <= 0 ? 10 : Math.min(10, (current * 10) / needed);
        return "§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled)
                + " §7" + current + "§8/§7" + needed;
    }

    private List<String> bonusLines(Player player, AetherSkill skill, int level) {
        List<String> lines = new ArrayList<>();
        double scale = SkillProgression.effectMultiplier(level);
        for (var entry : skill.bonuses().entrySet()) {
            ItemCapability capability = entry.getKey();
            double amount = entry.getValue() * scale;
            boolean percent = capability == ItemCapability.SPEED
                    || capability == ItemCapability.CRIT_CHANCE
                    || capability == ItemCapability.CRIT_DAMAGE
                    || capability == ItemCapability.PET_CATCH_RATE;
            lines.add("§7" + label(capability) + ": §f+" + ItemLore.formatStat(amount) + (percent ? "%" : ""));
        }
        switch (skill.flag()) {
            case PACK_RAT -> {
                lines.add("§7Mining Compact: §f+" + ItemLore.formatStat(SkillProgression.compactChancePercent(level)) + "%");
                int tier = SkillProgression.rarityTier(level);
                if (tier >= 4) {
                    lines.add("§7Compacted Upgrade: §b" + SkillService.compactedChancePercent(tier)
                            + "% §7of compact procs");
                } else {
                    lines.add("§8Compacted drops from Legendary onward.");
                }
            }
            case TIMBER_TAX -> {
                lines.add("§7Oak Compact: §f+" + ItemLore.formatStat(SkillProgression.compactChancePercent(level)) + "%");
                int tier = SkillProgression.rarityTier(level);
                if (tier >= 4) {
                    lines.add("§7Compacted Oak: §b" + SkillService.compactedChancePercent(tier)
                            + "% §7of compact procs");
                } else {
                    lines.add("§8Compacted oak from Legendary onward.");
                }
            }
            case SEED_LEDGER -> {
                lines.add("§7Crop Compact: §f+" + ItemLore.formatStat(SkillProgression.compactChancePercent(level)) + "%");
                int tier = SkillProgression.rarityTier(level);
                if (tier >= 4) {
                    lines.add("§7Compacted Crops: §b" + SkillService.compactedChancePercent(tier)
                            + "% §7of compact procs");
                } else {
                    lines.add("§8Compacted crops from Legendary onward.");
                }
            }
            case FISH_LEDGER -> {
                lines.add("§7Cod Compact: §f+" + ItemLore.formatStat(SkillProgression.compactChancePercent(level)) + "%");
                int tier = SkillProgression.rarityTier(level);
                if (tier >= 4) {
                    lines.add("§7Compacted Cod: §b" + SkillService.compactedChancePercent(tier)
                            + "% §7of compact procs");
                } else {
                    lines.add("§8Compacted cod from Legendary onward.");
                }
            }
            case QUICK_HANDS -> lines.add("§7Ability Cooldown: §f-"
                    + ItemLore.formatStat(100.0 - (Math.max(0.70, 1.0 - 0.10 * scale) * 100.0)) + "%");
            case BOSS_GRUDGE -> lines.add("§7Boss Damage: §f+" + ItemLore.formatStat(10.0 * scale) + "%");
            case FLOOR_GRUDGE -> lines.add("§7Dungeon Boss Damage: §f+" + ItemLore.formatStat(12.0 * scale) + "%");
            case RELIC_APPETITE -> lines.add("§7Dungeon Gear XP: §f+" + ItemLore.formatStat(20.0 * scale) + "%");
            case BLOOD_TAX -> lines.add("§7Kill Coins: §f+"
                    + ItemLore.formatStat(SkillService.bloodTaxCurve(level) * 100.0) + "% HP");
            case LIFE_ABSORB -> lines.add("§7Lifesteal: §f+"
                    + ItemLore.formatStat(5.0 * scale) + "% damage healed");
            case GOLDEN_HOUR -> lines.add("§7Coin Payouts: §f+" + ItemLore.formatStat(25.0 * scale) + "%");
            case NIGHT_OWL -> {
                // Speed is gear/pets/Lv.3 pace only — Night Owl no longer grants move speed.
            }
            case CAVE_SENSE -> lines.add("§7Cave Mining Power: §f+" + ItemLore.formatStat(10.0 * scale));
            case PINCH_PENNY -> {
                long cost = Math.max(2L, 5L - SkillProgression.rarityTier(level));
                lines.add("§7Midas Cost: §f" + cost + " coins");
            }
            case DIAMOND_SPINE -> lines.add("§7Melee Reflect: §f+" + ItemLore.formatStat(8.0 * scale) + "%");
            default -> {
            }
        }
        return lines;
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

    private static String strip(String colored) {
        return colored == null ? "" : colored.replaceAll("§.", "");
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack named(Material material, String name, String... lore) {
        return de.aetherion.items.util.GuiItems.named(material, name, lore);
    }

    public static final class Holder implements InventoryHolder {
        private final AetherSkill.Category category;

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
            return null;
        }
    }
}
