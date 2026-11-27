package de.aetherion.items.skill;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CoinService;
import de.aetherion.items.listener.HealthListener;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.mining.HarvestRules;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;
import de.aetherion.items.util.WorldLight;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkillService implements StatProvider, Listener {

    public static final int SLOT_COUNT = 7;
    /** Old slot curve (lifetime coins). Only used once per player to keep the slots they already had. */
    public static final long[] COIN_UNLOCK = {0L, 5_000L, 25_000L, 80_000L, 200_000L, 500_000L, 1_250_000L};
    /** Level-system v2: talent slot N opens at this Aetherion Level. */
    public static final int[] SLOT_LEVELS = {1, 5, 10, 20, 35, 50, 75};
    /** Loadout presets per player; preset N opens at {@link #PRESET_UNLOCK}[N] Aetherion levels. */
    public static final int PRESET_COUNT = 3;
    public static final int[] PRESET_UNLOCK = {1, 10, 25};

    private final AetherionItems plugin;
    private final CoinService coins;
    private final File file;
    private final ConcurrentHashMap<UUID, PlayerSkills> data = new ConcurrentHashMap<>();
    /** Nested depth while quest reward lines are printing — defer Aetherion level chat. */
    private final ConcurrentHashMap<UUID, Integer> rewardBatchDepth = new ConcurrentHashMap<>();
    /** Earliest pre-batch account level to announce when the batch closes. */
    private final ConcurrentHashMap<UUID, Integer> deferredAccountFrom = new ConcurrentHashMap<>();
    /** Skill level-ups collected this tick — flushed as one line so seven skills don't spam seven. */
    private final ConcurrentHashMap<UUID, List<LevelBeat>> pendingBeats = new ConcurrentHashMap<>();
    private final Set<UUID> beatFlushQueued = ConcurrentHashMap.newKeySet();
    /** Per-player cue throttles ({@code uuid|key} → until millis). Cleared on quit. */
    private final ConcurrentHashMap<String, Long> cueUntil = new ConcurrentHashMap<>();
    /** Last boss Boss Grudge announced for — one cue per fight, not per hit. */
    private final ConcurrentHashMap<UUID, UUID> grudgeTarget = new ConcurrentHashMap<>();
    /** Cave Sense currently lit (dark) — cue only on the dark transition. */
    private final Set<UUID> caveSenseLit = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    private HealthListener health;

    public SkillService(AetherionItems plugin, CoinService coins) {
        this.plugin = plugin;
        this.coins = coins;
        this.file = new File(plugin.getDataFolder(), "skills.yml");
        load();
        applyPendingWipes();
        ActiveEquipmentStats.registerProvider(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty,
                de.aetherion.items.core.SaveSlot.SKILLS.firstRunTicks(), de.aetherion.items.core.SaveSlot.PERIOD_TICKS);
    }

    public void setHealthListener(HealthListener health) {
        this.health = health;
    }

    public PlayerSkills of(Player player) {
        if (player == null) {
            return new PlayerSkills();
        }
        return data.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerSkills());
    }

    public int unlockedSlots(Player player) {
        return Math.min(SLOT_COUNT, baseSlots(player) + of(player).bonusSlots);
    }

    /** Slots from the Aetherion Level, never fewer than the player had before slots moved to levels. */
    private int baseSlots(Player player) {
        PlayerSkills skills = of(player);
        if (skills.slotFloor < 0 && player != null) {
            skills.slotFloor = coinSlots(player);
            dirty = true;
        }
        return Math.min(SLOT_COUNT, Math.max(levelSlots(accountLevel(player)), Math.max(1, skills.slotFloor)));
    }

    /** Talent slots the Aetherion Level alone opens. */
    public static int levelSlots(int accountLevel) {
        int open = 0;
        for (int at : SLOT_LEVELS) {
            if (accountLevel >= at) {
                open++;
            }
        }
        return Math.max(1, open);
    }

    /** Aetherion Level that opens the next talent slot, or {@code -1} once all seven are open. */
    public int nextSlotLevel(Player player) {
        int base = baseSlots(player);
        if (base >= SLOT_COUNT || unlockedSlots(player) >= SLOT_COUNT) {
            return -1;
        }
        return SLOT_LEVELS[base];
    }

    /** Slots opened by lifetime Legacy coins alone (old curve; kept for the one-time carry-over). */
    public int coinSlots(Player player) {
        int unlocked = 1;
        long lifetime = lifetimeCoins(player);
        for (int i = 1; i < SLOT_COUNT; i++) {
            if (lifetime >= COIN_UNLOCK[i]) {
                unlocked = i + 1;
            }
        }
        return unlocked;
    }

    public long lifetimeCoins(Player player) {
        return coins == null || player == null ? 0L : coins.lifetime(player);
    }

    public String unlockHint(Player player, int slotIndex) {
        if (slotIndex <= 0) {
            return de.aetherion.core.exile.Say.t(player, "§7Always open.", "§7Immer offen.");
        }
        if (slotIndex >= SLOT_COUNT) {
            return "";
        }
        return de.aetherion.core.exile.Say.t(player,
                "§7Opens at §fAetherion Level " + SLOT_LEVELS[slotIndex] + "§7.",
                "§7Öffnet bei §fAetherion-Level " + SLOT_LEVELS[slotIndex] + "§7.");
    }

    public String tabSlotLine(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return "";
        }
        if (slotIndex >= unlockedSlots(player)) {
            return "§8Locked";
        }
        AetherSkill skill = inSlot(player, slotIndex);
        if (skill == null) {
            return "§8Empty";
        }
        int skillLevel = level(player, skill);
        return SkillProgression.rarity(skillLevel).getChatColor()
                + skill.displayName()
                + " §8· §f" + skillLevel;
    }

    public String tabExtraLine(Player player, int lineIndex) {
        if (lineIndex < 0) {
            return "";
        }
        // Level-system v2: the tab shows the six skills (best first), two per line.
        List<AetherSkill.Category> extras = new ArrayList<>();
        for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
            if (skillLevel(player, category) <= 1 && skillXp(player, category) <= 0) {
                continue;
            }
            extras.add(category);
        }
        extras.sort(Comparator
                .comparingInt((AetherSkill.Category category) -> skillLevel(player, category))
                .reversed()
                .thenComparing(AetherSkill.Category::ordinal));
        int start = lineIndex * 2;
        if (start >= extras.size()) {
            return "";
        }
        String first = extraName(player, extras.get(start));
        if (start + 1 >= extras.size()) {
            return first;
        }
        return first + " §8· " + extraName(player, extras.get(start + 1));
    }

    private String extraName(Player player, AetherSkill.Category category) {
        return "§7" + category.plain() + " §f" + skillLevel(player, category);
    }

    public List<AetherSkill> equipped(Player player) {
        List<AetherSkill> equipped = new ArrayList<>();
        PlayerSkills skills = of(player);
        for (String id : skills.slots) {
            AetherSkill skill = AetherSkill.byId(id);
            if (skill != null) {
                equipped.add(skill);
            }
        }
        return equipped;
    }

    public AetherSkill inSlot(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return null;
        }
        return AetherSkill.byId(of(player).slots[slotIndex]);
    }

    public int slotOf(Player player, AetherSkill skill) {
        if (skill == null) {
            return -1;
        }
        String[] slots = of(player).slots;
        for (int i = 0; i < slots.length; i++) {
            if (skill.id().equals(slots[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Level-system v2: a talent is as strong as the level of its skill (Utility: the best skill). Every caller that
     * asks a talent's level gets that, so effects, rarity and gates follow the skill automatically.
     */
    public int level(Player player, AetherSkill skill) {
        return skill == null ? 1 : of(player).skillLevel(skill.category());
    }

    /** Level of a skill (Mining, Farming …), 1–150. Utility = the best skill. */
    public int skillLevel(Player player, AetherSkill.Category category) {
        return of(player).skillLevel(category);
    }

    /** XP into the current level of a skill. */
    public int skillXp(Player player, AetherSkill.Category category) {
        return of(player).skillXp(category);
    }

    /** The talent is open: its skill is high enough, the player had it before v2, or it sits in a slot. */
    public boolean talentUnlocked(Player player, AetherSkill talent) {
        if (talent == null) {
            return false;
        }
        PlayerSkills data = of(player);
        return data.keptTalents.contains(talent.id())
                || data.skillLevel(talent.category()) >= TalentUnlocks.level(talent)
                || slotOf(player, talent) >= 0;
    }

    public int accountLevel(Player player) {
        return AetherionLevel.of(accountXp(player));
    }

    public String accountTag(Player player) {
        return AetherionLevel.tag(accountLevel(player));
    }

    /**
     * Level-system v2: every skill XP a player earns feeds the Aetherion Level once ({@code pool}), whatever is
     * equipped. {@code legacyPool} is everything the old talent levels were worth on the day of the switch, so no
     * level is lost. Bonus XP (pets, quests, Codex …) stays 1:1.
     */
    public long accountXp(Player player) {
        PlayerSkills data = of(player);
        return data.bonusXp + AetherionLevel.fromSkillXp(Math.max(0L, data.legacyPool) + data.pool);
    }

    public void addBonusXp(Player player, long amount) {
        if (player == null || amount <= 0L) {
            return;
        }
        int before = accountLevel(player);
        of(player).bonusXp += scaleXp(player, (int) Math.min(Integer.MAX_VALUE, amount));
        dirty = true;
        announceAccountLevel(player, before);
    }

    /**
     * Open a reward print batch. Level-up chat is deferred until {@link #endRewardBatch}.
     * @return true when this call opened the outermost batch (caller should end it)
     */
    public boolean beginRewardBatch(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        int depth = rewardBatchDepth.merge(id, 1, Integer::sum);
        return depth == 1;
    }

    /** Close one reward-batch level; outermost close flushes deferred Aetherion level messages. */
    public void endRewardBatch(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        Integer depth = rewardBatchDepth.get(id);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            rewardBatchDepth.remove(id);
            Integer from = deferredAccountFrom.remove(id);
            if (from != null && player.isOnline()) {
                announceAccountLevelNow(player, from);
            }
        } else {
            rewardBatchDepth.put(id, depth - 1);
        }
    }

    private boolean inRewardBatch(Player player) {
        return player != null && rewardBatchDepth.getOrDefault(player.getUniqueId(), 0) > 0;
    }

    public int xp(Player player, AetherSkill skill) {
        return skill == null ? 0 : of(player).skillXp(skill.category());
    }

    public double multiplier(Player player, AetherSkill skill) {
        return SkillProgression.effectMultiplier(level(player, skill));
    }

    public double multiplier(Player player, AetherSkill.Flag flag) {
        AetherSkill skill = equippedWithFlag(player, flag);
        return skill == null ? 1.0d : multiplier(player, skill);
    }

    public int rarityTier(Player player, AetherSkill.Flag flag) {
        AetherSkill skill = equippedWithFlag(player, flag);
        return skill == null ? 0 : SkillProgression.rarityTier(level(player, skill));
    }

    public boolean hasFlag(Player player, AetherSkill.Flag flag) {
        return equippedWithFlag(player, flag) != null;
    }

    public boolean equip(Player player, AetherSkill skill) {
        if (player == null || skill == null) {
            return false;
        }
        if (slotOf(player, skill) >= 0 || !talentUnlocked(player, skill)) {
            return false;
        }
        int unlocked = unlockedSlots(player);
        String[] slots = of(player).slots;
        for (int i = 0; i < unlocked; i++) {
            if (slots[i] == null || slots[i].isBlank()) {
                slots[i] = skill.id();
                dirty = true;
                refresh(player);
                de.aetherion.items.util.QuestProgressHook.noteUsed(player, "AETHER_SKILL");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ presets

    public enum PresetResult { LOADED, PARTIAL, EMPTY, LOCKED }

    public boolean presetUnlocked(Player player, int index) {
        return index >= 0 && index < PRESET_COUNT && accountLevel(player) >= PRESET_UNLOCK[index];
    }

    /** Skills saved in a preset, in slot order (unknown ids dropped). */
    public List<AetherSkill> preset(Player player, int index) {
        List<AetherSkill> out = new ArrayList<>();
        if (index < 0 || index >= PRESET_COUNT) {
            return out;
        }
        String raw = of(player).presets[index];
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String id : raw.split(",")) {
            AetherSkill skill = AetherSkill.byId(id.trim());
            if (skill != null && !out.contains(skill)) {
                out.add(skill);
            }
        }
        return out;
    }

    /** Saves the current loadout into a preset. False when the loadout is empty or the preset is locked. */
    public boolean savePreset(Player player, int index) {
        if (player == null || !presetUnlocked(player, index)) {
            return false;
        }
        List<String> ids = new ArrayList<>();
        for (AetherSkill skill : equipped(player)) {
            ids.add(skill.id());
        }
        if (ids.isEmpty()) {
            return false;
        }
        of(player).presets[index] = String.join(",", ids);
        dirty = true;
        return true;
    }

    public void clearPreset(Player player, int index) {
        if (player != null && index >= 0 && index < PRESET_COUNT) {
            of(player).presets[index] = "";
            dirty = true;
        }
    }

    /**
     * Swaps the loadout for a preset. Levels never move — only which skills sit in the slots.
     * Skills beyond the unlocked slot count stay out ({@link PresetResult#PARTIAL}).
     */
    public PresetResult loadPreset(Player player, int index) {
        if (player == null || !presetUnlocked(player, index)) {
            return PresetResult.LOCKED;
        }
        List<AetherSkill> wanted = preset(player, index);
        if (wanted.isEmpty()) {
            return PresetResult.EMPTY;
        }
        String[] slots = of(player).slots;
        java.util.Arrays.fill(slots, "");
        int unlocked = unlockedSlots(player);
        int placed = 0;
        for (AetherSkill skill : wanted) {
            if (placed >= unlocked) {
                break;
            }
            if (!talentUnlocked(player, skill)) {
                continue;
            }
            slots[placed++] = skill.id();
        }
        dirty = true;
        refresh(player);
        return placed < wanted.size() ? PresetResult.PARTIAL : PresetResult.LOADED;
    }

    public boolean unequipSlot(Player player, int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return false;
        }
        String[] slots = of(player).slots;
        if (slots[slotIndex] == null || slots[slotIndex].isBlank()) {
            return false;
        }
        slots[slotIndex] = "";
        dirty = true;
        refresh(player);
        return true;
    }

    public boolean unequip(Player player, AetherSkill skill) {
        int slot = slotOf(player, skill);
        return slot >= 0 && unequipSlot(player, slot);
    }

    public void grantAllSlots(Player player) {
        of(player).bonusSlots = SLOT_COUNT;
        dirty = true;
    }

    /**
     * Extra unlocked slots on top of the lifetime-coin curve.
     * Used by StressBots so a few skills can be equipped without {@code /skills setlevel}.
     */
    public void grantBonusSlots(Player player, int extra) {
        if (player == null) {
            return;
        }
        PlayerSkills skills = of(player);
        skills.bonusSlots = Math.max(skills.bonusSlots, Math.min(SLOT_COUNT, Math.max(0, extra)));
        dirty = true;
    }

    public void resetLoadout(Player player) {
        PlayerSkills skills = of(player);
        for (int i = 0; i < skills.slots.length; i++) {
            skills.slots[i] = "";
        }
        skills.bonusSlots = 0;
        dirty = true;
        refresh(player);
    }

    public void reset(Player player) {
        resetLoadout(player);
    }

    /** Clears every skill level/xp back to 1/0 and unequips the loadout. */
    public void wipeProgress(Player player) {
        if (player == null) {
            return;
        }
        of(player).wipe();
        dirty = true;
        refresh(player);
    }

    /** DEV: sets the skill a talent belongs to (Utility: the best skill). Account XP follows the change. */
    public void setLevel(Player player, AetherSkill skill, int level) {
        if (player == null || skill == null) {
            return;
        }
        setSkillLevel(player, of(player).leveledFor(skill.category()), level);
    }

    /** DEV: sets one skill to a level; the Aetherion pool moves by what that is worth. */
    public void setSkillLevel(Player player, AetherSkill.Category category, int level) {
        if (player == null || category == null || !category.leveled()) {
            return;
        }
        int before = accountLevel(player);
        PlayerSkills data = of(player);
        int from = data.skillLevel(category);
        int to = SkillProgression.clampLevel(level);
        long delta = SkillProgression.spentXp(to) - (SkillProgression.spentXp(from) + data.skillXp(category));
        data.pool = Math.max(0L, data.pool + delta);
        data.skillLevels.put(category, to);
        data.skillXp.put(category, 0);
        dirty = true;
        refresh(player);
        announceAccountLevel(player, before);
    }

    /**
     * Admin/test grant straight into the skill of a talent. Runs the normal level-up path,
     * so every moment (card, rarity, new talents, Mastered) fires exactly as in play.
     */
    public void grantXpDirect(Player player, AetherSkill skill, int amount) {
        if (player == null || skill == null || amount <= 0) {
            return;
        }
        addSkillXp(player, of(player).leveledFor(skill.category()), scaleXp(player, amount), true);
    }

    /** Level of a skill. Kept under its old name: Mining, Farming, Foraging, Fishing and Quests call it. */
    public int highestLevel(Player player, AetherSkill.Category category) {
        if (player == null || category == null) {
            return 1;
        }
        return skillLevel(player, category);
    }

    public int miningLevel(Player player) {
        return highestLevel(player, AetherSkill.Category.MINING);
    }

    public int fishingLevel(Player player) {
        return highestLevel(player, AetherSkill.Category.FISHING);
    }

    public void setLevelAll(Player player, int level) {
        if (player == null) {
            return;
        }
        for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
            setSkillLevel(player, category, level);
        }
    }

    public void grantFromBlock(Player player, Material material) {
        if (!canEarnXp(player) || material == null) {
            return;
        }
        if (isCrop(material)) {
            grant(player, AetherSkill.Category.FARMING, 4);
            return;
        }
        if (isWood(material)) {
            sharePetGather(player, 4);
            grant(player, AetherSkill.Category.FORAGING, 4);
            return;
        }
        int amount = miningXp(material);
        if (amount > 0) {
            sharePetGather(player, amount);
            grant(player, AetherSkill.Category.MINING, amount);
        }
    }

    public void grantFromFarm(Player player, int amount) {
        if (!canEarnXp(player) || amount <= 0) {
            return;
        }
        sharePetGather(player, amount);
        grant(player, AetherSkill.Category.FARMING, amount);
    }

    public void grantFromKill(LivingEntity victim, boolean boss) {
        grantFromKill(resolveKiller(victim), victim, boss);
    }

    public void grantFromKill(Player player, LivingEntity victim, boolean boss) {
        if (!canEarnXp(player) || victim == null) {
            return;
        }
        int amount = 6 + (int) Math.max(0, victim.getMaxHealth() / 8.0);
        if (de.aetherion.items.world.WildlifeLooks.isSturdy(victim)) {
            amount = Math.max(amount + 4, (int) Math.round(amount * 1.4));
        }
        if (boss) {
            amount += 25;
        }
        grant(player, AetherSkill.Category.COMBAT, amount);
        // Dungeon skill: kills on a floor teach it too (it never had an XP source before v2). The Aetherion
        // pool already counted this kill once through Combat.
        if (de.aetherion.items.dungeon.DungeonArmor.inDungeon(player)) {
            addSkillXp(player, AetherSkill.Category.DUNGEON, scaleXp(player, amount), false);
        }
    }

    public void grantFromFish(Player player, int amount) {
        if (!canEarnXp(player) || amount <= 0) {
            return;
        }
        sharePetGather(player, amount);
        grant(player, AetherSkill.Category.FISHING, Math.max(1, amount));
    }

    /**
     * Minigame bonus XP (perfect timing, streaks, cleared events). Goes to equipped skills of
     * {@code category} (Utility at half, like every grant). No pet share — the base grant
     * already fed pets once for this action.
     */
    public void grantGatherBonus(Player player, AetherSkill.Category category, int amount) {
        if (!canEarnXp(player) || category == null || amount <= 0) {
            return;
        }
        grant(player, category, amount);
    }

    /** Highest-level equipped skill of a category — the one a gathering loop credits. */
    public AetherSkill focusSkill(Player player, AetherSkill.Category category) {
        if (player == null || category == null) {
            return null;
        }
        AetherSkill best = null;
        int bestLevel = 0;
        for (AetherSkill skill : equipped(player)) {
            if (skill.category() != category) {
                continue;
            }
            int skillLevel = level(player, skill);
            if (best == null || skillLevel > bestLevel) {
                best = skill;
                bestLevel = skillLevel;
            }
        }
        return best;
    }

    /** Skill name in its current rarity color. */
    public String coloredName(Player player, AetherSkill skill) {
        if (skill == null) {
            return "";
        }
        return SkillProgression.rarity(level(player, skill)).getChatColor() + skill.displayName();
    }

    /**
     * Compact progress read for action bars: {@code Bite Me 34 ▮▮▮▮▯▯▯▯▯▯}.
     * Gathering loops append this to their success beat so growth is visible per catch/fell.
     */
    public String progressLine(Player player, AetherSkill skill) {
        if (player == null || skill == null) {
            return "";
        }
        int skillLevel = level(player, skill);
        String name = coloredName(player, skill);
        if (SkillProgression.isMax(skillLevel)) {
            return name + " §dMAX";
        }
        return name + " §f" + skillLevel + " "
                + SkillProgression.miniBar(SkillProgression.levelFill(skillLevel, xp(player, skill)), "§a");
    }

    /**
     * Action-bar read of a skill: {@code Foraging 34 ▮▮▮▮▯▯▯▯▯▯}. Gathering loops append it to their success beat.
     * Level-system v2: always there (the skill levels whatever is equipped), never {@code null} for a skill.
     */
    public String loopCredit(Player player, AetherSkill.Category category) {
        if (player == null || category == null) {
            return null;
        }
        AetherSkill.Category skill = of(player).leveledFor(category);
        int level = skillLevel(player, skill);
        String name = SkillProgression.rarity(level).getChatColor() + skill.plain();
        if (SkillProgression.isMax(level)) {
            return name + " §dMAX";
        }
        return name + " §f" + level + " "
                + SkillProgression.miniBar(SkillProgression.levelFill(level, skillXp(player, skill)), "§a");
    }

    /**
     * Domain-scoped compact chance from player skills.
     * Mining / foraging / farming / fishing each only boost their own drops.
     */
    public double compactBonus(Player player, boolean mining, boolean oak, boolean wheat, boolean fish) {
        double bonus = 0.0;
        if (mining && hasFlag(player, AetherSkill.Flag.PACK_RAT)) {
            bonus += compactChanceForFlag(player, AetherSkill.Flag.PACK_RAT);
        }
        if (oak && hasFlag(player, AetherSkill.Flag.TIMBER_TAX)) {
            bonus += compactChanceForFlag(player, AetherSkill.Flag.TIMBER_TAX);
        }
        if (wheat && hasFlag(player, AetherSkill.Flag.SEED_LEDGER)) {
            bonus += compactChanceForFlag(player, AetherSkill.Flag.SEED_LEDGER);
        }
        if (fish && hasFlag(player, AetherSkill.Flag.FISH_LEDGER)) {
            bonus += compactChanceForFlag(player, AetherSkill.Flag.FISH_LEDGER);
        }
        return bonus;
    }

    public double compactedUpgradeChance(Player player, boolean mining, boolean oak, boolean wheat, boolean fish) {
        double chance = 0.0d;
        if (mining && hasFlag(player, AetherSkill.Flag.PACK_RAT)) {
            chance = Math.max(chance, compactedChanceForTier(rarityTier(player, AetherSkill.Flag.PACK_RAT)));
        }
        if (oak && hasFlag(player, AetherSkill.Flag.TIMBER_TAX)) {
            chance = Math.max(chance, compactedChanceForTier(rarityTier(player, AetherSkill.Flag.TIMBER_TAX)));
        }
        if (wheat && hasFlag(player, AetherSkill.Flag.SEED_LEDGER)) {
            chance = Math.max(chance, compactedChanceForTier(rarityTier(player, AetherSkill.Flag.SEED_LEDGER)));
        }
        if (fish && hasFlag(player, AetherSkill.Flag.FISH_LEDGER)) {
            chance = Math.max(chance, compactedChanceForTier(rarityTier(player, AetherSkill.Flag.FISH_LEDGER)));
        }
        return chance;
    }

    private double compactChanceForFlag(Player player, AetherSkill.Flag flag) {
        AetherSkill skill = equippedWithFlag(player, flag);
        int level = skill == null ? 1 : level(player, skill);
        return SkillProgression.compactChance(level);
    }

    private static double compactedChanceForTier(int rarityTier) {
        if (rarityTier >= 5) {
            return 0.05d;
        }
        if (rarityTier >= 4) {
            return 0.03d;
        }
        return 0.0d;
    }

    public static int compactedChancePercent(int rarityTier) {
        return (int) Math.round(compactedChanceForTier(rarityTier) * 100.0d);
    }

    /**
     * A compact proc the ledger skill earned (caller attributes by chance share).
     * Always a soft click; the named chat line at most once a minute per skill.
     */
    public void noteCompactProc(Player player, boolean mining, boolean oak, boolean wheat, boolean fish) {
        AetherSkill.Flag flag;
        String line;
        if (mining) {
            flag = AetherSkill.Flag.PACK_RAT;
            line = "compacted that one. The rocks didn't object.";
        } else if (oak) {
            flag = AetherSkill.Flag.TIMBER_TAX;
            line = "taxed that log into a bundle.";
        } else if (wheat) {
            flag = AetherSkill.Flag.SEED_LEDGER;
            line = "filed that harvest as a bale.";
        } else if (fish) {
            flag = AetherSkill.Flag.FISH_LEDGER;
            line = "itemized that catch. Compacted.";
        } else {
            return;
        }
        AetherSkill skill = equippedWithFlag(player, flag);
        if (skill == null || !player.isOnline()) {
            return;
        }
        int tier = SkillProgression.rarityTier(level(player, skill));
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.55f, 1.05f + tier * 0.08f);
        if (cueReady(player, "compact_" + flag.name(), 60_000L)) {
            player.sendMessage("§8⌁ " + coloredName(player, skill) + " §7" + line);
        }
    }

    /**
     * First hit on a new boss with Boss Grudge equipped: one line, one thud. Not per hit.
     * Quiet inside dungeons — floors run their own chat.
     */
    public void noteBossHit(Player attacker, LivingEntity boss) {
        if (attacker == null || boss == null || !hasFlag(attacker, AetherSkill.Flag.BOSS_GRUDGE)) {
            return;
        }
        if (de.aetherion.items.dungeon.DungeonArmor.inDungeon(attacker)) {
            return;
        }
        UUID previous = grudgeTarget.put(attacker.getUniqueId(), boss.getUniqueId());
        if (boss.getUniqueId().equals(previous)) {
            return;
        }
        AetherSkill skill = equippedWithFlag(attacker, AetherSkill.Flag.BOSS_GRUDGE);
        double bonus = 100.0d * SkillProgression.bossGrudge(level(attacker, skill));
        String name = boss.getName();
        attacker.sendMessage("§8⌁ " + coloredName(attacker, skill) + " §8· §f" + name
                + " §7goes on the list. §8+" + de.aetherion.items.item.ItemLore.formatStat(bonus) + "% damage");
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 0.6f);
    }

    /** Short chat tag for coin lines Blood Tax paid into, e.g. {@code  · Blood Tax}. */
    public String bloodTaxTag(Player player) {
        AetherSkill skill = equippedWithFlag(player, AetherSkill.Flag.BLOOD_TAX);
        return skill == null ? "" : " §8· " + coloredName(player, skill);
    }

    /** True at most once per {@code cooldownMs} per player + key. */
    private boolean cueReady(Player player, String key, long cooldownMs) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String id = player.getUniqueId() + "|" + key;
        Long until = cueUntil.get(id);
        if (until != null && until > now) {
            return false;
        }
        cueUntil.put(id, now + cooldownMs);
        return true;
    }

    public double coinMultiplier(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.GOLDEN_HOUR)) {
            return 1.0d;
        }
        return 1.0d + (0.25d * multiplier(player, AetherSkill.Flag.GOLDEN_HOUR));
    }

    public long midasCost(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.PINCH_PENNY)) {
            return 10L;
        }
        return Math.max(2L, 5L - rarityTier(player, AetherSkill.Flag.PINCH_PENNY));
    }

    public double reflectPercent(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.DIAMOND_SPINE)) {
            return 0.0d;
        }
        return 0.08d * multiplier(player, AetherSkill.Flag.DIAMOND_SPINE);
    }

    public double bossBonus(Player player) {
        double bonus = 1.0d;
        // P4 / L3: linear 10% -> 30% (Boss) and 12% -> 36% (Floor) instead of x effectMultiplier.
        AetherSkill boss = equippedWithFlag(player, AetherSkill.Flag.BOSS_GRUDGE);
        if (boss != null) {
            bonus += SkillProgression.bossGrudge(level(player, boss));
        }
        if (de.aetherion.items.dungeon.DungeonArmor.inDungeon(player)) {
            AetherSkill floor = equippedWithFlag(player, AetherSkill.Flag.FLOOR_GRUDGE);
            if (floor != null) {
                bonus += SkillProgression.floorGrudge(level(player, floor));
            }
        }
        return bonus;
    }

    /** Relic Appetite: dungeon gear XP multiplier (1.0 = none). */
    public double dungeonGearXpFactor(Player player) {
        if (!de.aetherion.items.dungeon.DungeonArmor.inDungeon(player)
                || !hasFlag(player, AetherSkill.Flag.RELIC_APPETITE)) {
            return 1.0d;
        }
        return 1.0d + (0.20d * multiplier(player, AetherSkill.Flag.RELIC_APPETITE));
    }

    public double bloodTaxFactor(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.BLOOD_TAX)) {
            return 0.0d;
        }
        AetherSkill skill = equippedWithFlag(player, AetherSkill.Flag.BLOOD_TAX);
        int level = skill == null ? 1 : level(player, skill);
        return bloodTaxCurve(level);
    }

    /**
     * Coins-per-kill as a fraction of victim max HP.
     * Wave 2: late levels still pay, but 185% @100 was a coin printer.
     * ~18% @1 · ~28% @25 · ~38% @50 · ~48% @75 · ~60% @100.
     */
    public static double bloodTaxCurve(int level) {
        int clamped = SkillProgression.clampLevel(level);
        double t = (clamped - 1) / (double) (SkillProgression.MAX_LEVEL - 1);
        return 0.18d + (0.20d * t) + (0.22d * t * t);
    }

    public double lifeAbsorbFactor(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.LIFE_ABSORB)) {
            return 0.0d;
        }
        return 0.05d * multiplier(player, AetherSkill.Flag.LIFE_ABSORB);
    }

    public double quickHandsFactor(Player player) {
        if (!hasFlag(player, AetherSkill.Flag.QUICK_HANDS)) {
            return 1.0d;
        }
        return Math.max(0.70d, 1.0d - (0.10d * multiplier(player, AetherSkill.Flag.QUICK_HANDS)));
    }

    public void refresh(Player player) {
        if (player != null && health != null) {
            health.refreshHealth(player);
        }
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 0.0;
        }
        // Movement speed is gear / pets / boots boosters / Lv.3 pace only — never skills.
        if (capability == ItemCapability.SPEED) {
            return 0.0;
        }
        double total = 0.0;
        if (capability == ItemCapability.DAMAGE || capability == ItemCapability.HEALTH) {
            total += AetherionLevel.milestoneStatBonus(accountLevel(player));
        }
        boolean dark = WorldLight.isDark(player);
        boolean dungeon = de.aetherion.items.dungeon.DungeonArmor.inDungeon(player);
        for (AetherSkill skill : equipped(player)) {
            // Life Absorb is heal-on-hit only — never flat stats.
            if (skill == AetherSkill.LIFE_ABSORB) {
                continue;
            }
            if (skill.category() == AetherSkill.Category.DUNGEON && !dungeon) {
                continue;
            }
            if (skill.flag() == AetherSkill.Flag.SOIL_SENSE
                    && !de.aetherion.items.farming.FarmIsleHook.onIsle(player)) {
                continue;
            }
            if (skill.flag() == AetherSkill.Flag.LAKE_SENSE
                    && !de.aetherion.items.fishing.FishIsleHook.onIsle(player)) {
                continue;
            }
            if (skill.flag() == AetherSkill.Flag.BEDROCK_BORN
                    && !de.aetherion.items.mining.MineIsleHook.onIsle(player)) {
                continue;
            }
            double scale = multiplier(player, skill);
            total += skill.bonus(capability) * SkillProgression.bonusMultiplier(level(player, skill), capability);
            if (dark && capability == ItemCapability.MINING_POWER && skill.flag() == AetherSkill.Flag.CAVE_SENSE) {
                total += 10.0 * scale;
            }
        }
        return total;
    }

    public void save() {
        // Merge into disk so shared network YAMLs keep offline players from the other JVM.
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        data.forEach((id, skills) -> {
            String path = "players." + id;
            config.set(path, null);
            List<String> slots = new ArrayList<>(SLOT_COUNT);
            for (int i = 0; i < SLOT_COUNT; i++) {
                String slot = skills.slots[i];
                slots.add(slot == null ? "" : slot);
            }
            config.set(path + ".slots", slots);
            config.set(path + ".bonusSlots", skills.bonusSlots);
            config.set(path + ".bonusXp", skills.bonusXp);
            config.set(path + ".claimedShardLevel", skills.claimedShardLevel);
            config.set(path + ".seenSlots", skills.seenSlots);
            List<String> presets = new ArrayList<>(PRESET_COUNT);
            boolean anyPreset = false;
            for (int i = 0; i < PRESET_COUNT; i++) {
                String preset = skills.presets[i];
                presets.add(preset == null ? "" : preset);
                anyPreset |= preset != null && !preset.isBlank();
            }
            if (anyPreset) {
                config.set(path + ".presets", presets);
            }
            // Old per-talent progress: frozen since level-system v2, still written so an older jar reads it.
            for (AetherSkill skill : AetherSkill.values()) {
                int level = skills.legacyLevel(skill);
                int xp = skills.legacyXp(skill);
                if (level <= 1 && xp <= 0) {
                    continue;
                }
                config.set(path + ".progress." + skill.id() + ".level", level);
                config.set(path + ".progress." + skill.id() + ".xp", xp);
            }
            String v2 = path + ".v2";
            for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
                config.set(v2 + ".skills." + category.key() + ".level", skills.skillLevel(category));
                config.set(v2 + ".skills." + category.key() + ".xp", skills.skillXp(category));
            }
            config.set(v2 + ".pool", skills.pool);
            config.set(v2 + ".legacyPool", Math.max(0L, skills.legacyPool));
            config.set(v2 + ".utilityFloor", skills.utilityFloor);
            config.set(v2 + ".slotFloor", skills.slotFloor);
            List<String> kept = new ArrayList<>(skills.keptTalents);
            java.util.Collections.sort(kept);
            config.set(v2 + ".kept", kept);
        });
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
            dirty = false;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save skills.yml: " + exception.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /**
     * Level-system v2: the skill of the activity always gets the XP, equipped talents or not, and the Aetherion
     * Level gets it once. Which talents sit in the slots only decides which bonuses are active.
     */
    private void grant(Player player, AetherSkill.Category category, int amount) {
        if (player == null || category == null || amount <= 0 || !category.leveled()) {
            return;
        }
        addSkillXp(player, category, scaleXp(player, amount), true);
    }

    private boolean canEarnXp(Player player) {
        if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return true;
    }

    private void sharePetGather(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        org.bukkit.plugin.Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs == null || !mobs.isEnabled()) {
            return;
        }
        try {
            mobs.getClass()
                    .getMethod("shareGatherExperience", Player.class, int.class)
                    .invoke(mobs, player, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Player resolveKiller(LivingEntity victim) {
        if (victim == null) {
            return null;
        }
        Player killer = victim.getKiller();
        if (killer != null) {
            return killer;
        }
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent damage)) {
            return null;
        }
        Entity damager = damage.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private int scaleXp(Player player, int amount) {
        if (plugin.xpBoost() == null) {
            return amount;
        }
        return plugin.xpBoost().scale(player, amount);
    }

    /**
     * One skill gains XP: levels, the level-up beat and (when {@code feedPool}) the Aetherion pool. XP past the top
     * level still feeds the Aetherion Level, so every action keeps counting.
     */
    private void addSkillXp(Player player, AetherSkill.Category category, int amount, boolean feedPool) {
        if (player == null || category == null || !category.leveled() || amount <= 0) {
            return;
        }
        PlayerSkills data = of(player);
        int accountBefore = accountLevel(player);
        int utilityBefore = data.skillLevel(AetherSkill.Category.UTILITY);
        if (feedPool) {
            data.pool += amount;
        }
        int level = data.skillLevel(category);
        int startLevel = level;
        if (!SkillProgression.isMax(level)) {
            int xp = data.skillXp(category) + amount;
            int needed = SkillProgression.xpToNext(level);
            while (xp >= needed && needed > 0 && level < SkillProgression.MAX_LEVEL) {
                xp -= needed;
                level++;
                needed = SkillProgression.xpToNext(level);
            }
            if (SkillProgression.isMax(level)) {
                xp = 0;
            }
            data.skillLevels.put(category, level);
            data.skillXp.put(category, xp);
        }
        SkillSession.note(player, category, amount, level - startLevel);
        dirty = true;
        if (level > startLevel) {
            queueLevelBeat(player, category, startLevel, level, utilityBefore, data.skillLevel(AetherSkill.Category.UTILITY));
        }
        announceAccountLevel(player, accountBefore);
        if (level > startLevel) {
            refresh(player);
        }
    }

    /**
     * Level-ups land next tick as one beat per skill: a small card for a plain level, a big card for a new talent,
     * a new rarity or Mastered. Several skills in one tick share the beat instead of stacking cards.
     */
    private void queueLevelBeat(Player player, AetherSkill.Category skill, int from, int to, int utilityFrom, int utilityTo) {
        if (player == null || skill == null || to <= from) {
            return;
        }
        UUID id = player.getUniqueId();
        List<LevelBeat> beats = pendingBeats.computeIfAbsent(id, ignored -> new ArrayList<>());
        LevelBeat existing = null;
        for (LevelBeat beat : beats) {
            if (beat.skill == skill) {
                existing = beat;
                break;
            }
        }
        if (existing != null) {
            existing.from = Math.min(existing.from, from);
            existing.to = Math.max(existing.to, to);
            existing.utilityFrom = Math.min(existing.utilityFrom, utilityFrom);
            existing.utilityTo = Math.max(existing.utilityTo, utilityTo);
        } else {
            beats.add(new LevelBeat(skill, from, to, utilityFrom, utilityTo));
        }
        if (beatFlushQueued.add(id)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> flushLevelBeats(id));
        }
    }

    private void flushLevelBeats(UUID id) {
        beatFlushQueued.remove(id);
        List<LevelBeat> beats = pendingBeats.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (beats == null || beats.isEmpty() || player == null || !player.isOnline()) {
            return;
        }
        // The loudest beat leads; plain level-ups of other skills ride along as rows.
        beats.sort(Comparator.comparingInt(LevelBeat::rank).reversed());
        LevelBeat lead = beats.get(0);
        List<String> extra = new ArrayList<>();
        for (int i = 1; i < beats.size(); i++) {
            LevelBeat other = beats.get(i);
            extra.add(levelColor(other.to) + other.skill.plain() + " §f" + other.to);
        }
        playBeat(player, lead, extra);
    }

    private static String levelColor(int level) {
        return SkillProgression.rarity(level).getChatColor().toString();
    }

    private void playBeat(Player player, LevelBeat beat, List<String> extra) {
        AetherSkill.Category skill = beat.skill;
        Rarity rarity = SkillProgression.rarity(beat.to);
        String name = rarity.getChatColor() + skill.plain();
        List<AetherSkill> opened = new ArrayList<>(TalentUnlocks.openedBetween(skill, beat.from, beat.to));
        opened.addAll(TalentUnlocks.openedBetween(AetherSkill.Category.UTILITY, beat.utilityFrom, beat.utilityTo));
        int rank = beat.rank();
        List<String> rows = new ArrayList<>();
        String header;
        String title;
        String accent;
        switch (rank) {
            case LevelBeat.RANK_MASTERED -> {
                header = t(player, "Skill mastered", "Skill gemeistert");
                title = name + " " + SkillProgression.MAX_LEVEL;
                accent = "§d";
                rows.add("§7“" + SkillFlavor.tagline(talentFor(skill), beat.to) + "”");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.2f);
                rarityRing(player, rarity);
            }
            case LevelBeat.RANK_RARITY -> {
                header = t(player, "New rarity", "Neue Seltenheit");
                title = name + " " + beat.to + " §8· " + rarity.getChatColor() + SkillProgression.rarityName(rarity);
                accent = rarity.getChatColor().toString();
                rows.add(t(player, "§7All " + skill.plain() + " talents: §a+" + SkillProgression.rarityBonusPercent(beat.to) + "% §7effect",
                        "§7Alle " + skill.plain() + "-Talente: §a+" + SkillProgression.rarityBonusPercent(beat.to) + "% §7Wirkung"));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.65f, 1.3f);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
                rarityRing(player, rarity);
            }
            default -> {
                header = opened.isEmpty() ? null : t(player, "New talent", "Neues Talent");
                title = name + " §f" + beat.to;
                accent = "§a";
                player.playSound(player.getLocation(), opened.isEmpty() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.UI_TOAST_CHALLENGE_COMPLETE,
                        opened.isEmpty() ? 0.35f : 0.6f, opened.isEmpty() ? 1.6f : 1.25f);
            }
        }
        for (AetherSkill talent : opened) {
            rows.add(t(player, "§a✚ New talent: §f", "§a✚ Neues Talent: §f") + talent.displayName()
                    + " §8· §7" + effectLine(talent));
        }
        // Rarity seals pay out right away (coins + Shards); no claim line in chat.
        int sealsPaid = rank == LevelBeat.RANK_RARITY || rank == LevelBeat.RANK_MASTERED
                ? SkillSeals.claimQuiet(player, skill, rows)
                : 0;
        rows.addAll(extra);
        String next = opened.isEmpty() && sealsPaid == 0 && rank == LevelBeat.RANK_NONE
                ? nextTalentLine(player, skill, beat.to)
                : t(player, "§8Journal › Character › Skills", "§8Journal › Charakter › Skills");
        boolean big = rank != LevelBeat.RANK_NONE || !opened.isEmpty();
        boolean shown = big
                ? de.aetherion.items.util.CardsBridge.big(player, header, title, rows, null, next, accent, null)
                : de.aetherion.items.util.CardsBridge.note(player, "▲", title, rows, next);
        if (!shown) {
            // No cards for this player: one short action-bar line, the rows only in chat when they matter.
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§d▲ " + title));
            if (big) {
                for (String row : rows) {
                    player.sendMessage(row);
                }
            }
        }
    }

    /** "Next talent at Lv. 20: Name" for the small level card, or a plain hint once all are open. */
    private String nextTalentLine(Player player, AetherSkill.Category skill, int level) {
        AetherSkill next = TalentUnlocks.next(skill, level);
        if (next == null) {
            return null;
        }
        return t(player, "§8Next talent at " + TalentUnlocks.level(next) + ": §7" + next.displayName(),
                "§8Nächstes Talent bei " + TalentUnlocks.level(next) + ": §7" + next.displayName());
    }

    /** First sentence of a talent's details: what it does, without the joke. */
    static String effectLine(AetherSkill talent) {
        String details = talent == null ? "" : talent.details();
        int dot = details.indexOf(". ");
        return dot > 0 ? details.substring(0, dot) : details;
    }

    /** A talent of the skill, for the old per-talent flavour lines. */
    private static AetherSkill talentFor(AetherSkill.Category skill) {
        List<AetherSkill> talents = TalentUnlocks.of(skill);
        return talents.isEmpty() ? AetherSkill.values()[0] : talents.get(0);
    }

    private static String t(Player player, String en, String german) {
        return de.aetherion.core.exile.Say.t(player, en, german);
    }

    private static void showTitle(Player player, String title, String subtitle) {
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        player.showTitle(net.kyori.adventure.title.Title.title(
                legacy.deserialize(title),
                legacy.deserialize(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(120),
                        java.time.Duration.ofMillis(1700),
                        java.time.Duration.ofMillis(400)
                )
        ));
    }

    /** Personal ring of rarity-colored dust — only the player sees it. */
    private static void rarityRing(Player player, Rarity rarity) {
        org.bukkit.Location base = player.getLocation().add(0, 0.15, 0);
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(rarity.getArmorColor(), 1.1f);
        for (int i = 0; i < 18; i++) {
            double angle = (Math.PI * 2.0d * i) / 18.0d;
            player.spawnParticle(
                    org.bukkit.Particle.DUST,
                    base.clone().add(Math.cos(angle) * 0.9d, 0.0d, Math.sin(angle) * 0.9d),
                    1, 0.0d, 0.02d, 0.0d, 0.0d, dust
            );
        }
    }

    private static final class LevelBeat {
        static final int RANK_NONE = 0;
        static final int RANK_RARITY = 2;
        static final int RANK_MASTERED = 3;

        final AetherSkill.Category skill;
        int from;
        int to;
        int utilityFrom;
        int utilityTo;

        LevelBeat(AetherSkill.Category skill, int from, int to, int utilityFrom, int utilityTo) {
            this.skill = skill;
            this.from = from;
            this.to = to;
            this.utilityFrom = utilityFrom;
            this.utilityTo = utilityTo;
        }

        int rank() {
            if (SkillProgression.isMax(to) && !SkillProgression.isMax(from)) {
                return RANK_MASTERED;
            }
            if (SkillProgression.rarityTier(to) > SkillProgression.rarityTier(from)) {
                return RANK_RARITY;
            }
            return RANK_NONE;
        }
    }

    private void announceAccountLevel(Player player, int previous) {
        int current = accountLevel(player);
        if (current <= previous) {
            return;
        }
        // Keep reward lines contiguous — flush level-up after the reward block.
        if (inRewardBatch(player)) {
            deferredAccountFrom.merge(player.getUniqueId(), previous, Math::min);
            return;
        }
        announceAccountLevelNow(player, previous);
    }

    /**
     * Aetherion Level up. Level-system v2: a small card for a plain level, a big card when a gate opens (auto pickup,
     * a talent slot, a talent set, the island, the Exile) or a new rank. Nothing in chat when cards are on.
     */
    private void announceAccountLevelNow(Player player, int previous) {
        int current = accountLevel(player);
        if (current <= previous) {
            return;
        }
        List<String> rows = new ArrayList<>();
        boolean big = false;
        if (AetherionLevel.titleTier(current) > AetherionLevel.titleTier(previous)) {
            rows.add(t(player, "§eNew rank: ", "§eNeuer Rang: ") + AetherionLevel.coloredTitle(current));
            big = true;
        }
        for (String gate : gatesBetween(player, previous, current)) {
            rows.add(gate);
            big = true;
        }
        if (plugin.ranks() != null) {
            plugin.ranks().syncProgression(player, current);
        }
        int statBefore = AetherionLevel.milestoneStatBonus(previous);
        int statAfter = AetherionLevel.milestoneStatBonus(current);
        if (statAfter > statBefore) {
            int gain = statAfter - statBefore;
            rows.add(t(player, "§a+" + gain + " Damage §8· §c+" + gain + " Health",
                    "§a+" + gain + " Schaden §8· §c+" + gain + " Leben"));
            refresh(player);
        }
        // World pace (silent move/jump) kicks in at Lv.3 — refresh attributes, no announcement.
        if (previous < 3 && current >= 3) {
            refresh(player);
        }
        claimLevelShards(player, rows);
        String title = "§6Aetherion Level " + AetherionLevel.coloredLevel(current);
        String next = nextGateLine(player, current);
        boolean shown = big
                ? de.aetherion.items.util.CardsBridge.big(player, t(player, "Level up", "Level-Up"), title, rows, null, next, "§6", null)
                : de.aetherion.items.util.CardsBridge.note(player, "✦", title, rows, next);
        if (big) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.05f);
        } else if (current % 10 == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        if (!shown) {
            player.sendMessage("§6Aetherion Level §f" + previous + " §7→ " + AetherionLevel.coloredLevel(current));
            for (String row : rows) {
                player.sendMessage("§8   " + row);
            }
            int autoPickup = de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL;
            if (previous < autoPickup && current >= autoPickup) {
                showTitle(player, "§bAUTO PICKUP", t(player, "§7Drops find you now", "§7Drops fliegen jetzt zu dir"));
            }
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items != null && items.xpBarSync() != null) {
            items.xpBarSync().sync(player);
        }
    }

    /** What opens between two Aetherion Levels, one card row each. Only gates that exist in code. */
    private List<String> gatesBetween(Player player, int from, int to) {
        List<String> rows = new ArrayList<>();
        int autoPickup = de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL;
        if (from < autoPickup && to >= autoPickup) {
            rows.add(t(player, "§b✚ Auto pickup: drops fly to you", "§b✚ Auto-Pickup: Drops fliegen zu dir"));
        }
        for (int i = 1; i < SLOT_LEVELS.length; i++) {
            if (from < SLOT_LEVELS[i] && to >= SLOT_LEVELS[i]) {
                rows.add(t(player, "§a✚ Talent slot " + (i + 1) + " is open", "§a✚ Talent-Platz " + (i + 1) + " ist offen"));
            }
        }
        for (int i = 1; i < PRESET_COUNT; i++) {
            if (from < PRESET_UNLOCK[i] && to >= PRESET_UNLOCK[i]) {
                rows.add(t(player, "§a✚ Talent set " + (i + 1) + " to save a build", "§a✚ Talent-Set " + (i + 1) + " für einen Build"));
            }
        }
        int island = de.aetherion.items.progress.ProgressionService.ISLAND_LEVEL;
        if (from < island && to >= island) {
            rows.add(t(player, "§6✚ Your own island, guilds and Compacting", "§6✚ Eigene Insel, Gilden und Verdichten"));
        }
        int guild = de.aetherion.items.progress.ProgressionService.GUILD_LEVEL;
        if (from < guild && to >= guild) {
            rows.add(t(player, "§5✚ The Exile (after the Pathwarden) and founding a guild",
                    "§5✚ Das Exil (nach dem Pathwarden) und eine Gilde gründen"));
        }
        return rows;
    }

    /** The next gate above {@code level}, as the muted last line of a level card. */
    private String nextGateLine(Player player, int level) {
        int next = Integer.MAX_VALUE;
        String what = null;
        int autoPickup = de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL;
        if (autoPickup > level) {
            next = autoPickup;
            what = t(player, "auto pickup", "Auto-Pickup");
        }
        for (int i = 1; i < SLOT_LEVELS.length; i++) {
            if (SLOT_LEVELS[i] > level && SLOT_LEVELS[i] < next) {
                next = SLOT_LEVELS[i];
                what = t(player, "talent slot " + (i + 1), "Talent-Platz " + (i + 1));
            }
        }
        int island = de.aetherion.items.progress.ProgressionService.ISLAND_LEVEL;
        if (island > level && island <= next) {
            next = island;
            what = t(player, "your own island", "eigene Insel");
        }
        int guild = de.aetherion.items.progress.ProgressionService.GUILD_LEVEL;
        if (guild > level && guild <= next) {
            next = guild;
            what = t(player, "the Exile", "das Exil");
        }
        if (what == null) {
            return null;
        }
        return t(player, "§8Next at " + next + ": §7" + what, "§8Als Nächstes bei " + next + ": §7" + what);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        claimLevelShards(player);
        refresh(player);
        if (plugin.ranks() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.ranks().syncProgression(player, accountLevel(player));
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingBeats.remove(id);
        beatFlushQueued.remove(id);
        grudgeTarget.remove(id);
        caveSenseLit.remove(id);
        SkillSession.forget(id);
        String prefix = id + "|";
        cueUntil.keySet().removeIf(key -> key.startsWith(prefix));
        saveIfDirty();
    }

    public void claimLevelShards(Player player) {
        claimLevelShards(player, null);
    }

    /**
     * Pays the Aether Shards for every 25th Aetherion Level not yet paid. With {@code rows} the lines go into the
     * level card; without (login) they go to chat once.
     */
    public void claimLevelShards(Player player, List<String> rows) {
        if (player == null || plugin.getShards() == null) {
            return;
        }
        PlayerSkills skills = of(player);
        int current = accountLevel(player);
        int claimed = skills.claimedShardLevel;
        int milestones = 0;
        long totalShards = 0L;
        int lastMilestone = claimed;
        for (int milestone = AetherionLevel.TITLE_EVERY; milestone <= current; milestone += AetherionLevel.TITLE_EVERY) {
            if (milestone <= claimed) {
                continue;
            }
            totalShards += AetherionLevel.SHARD_MILESTONE;
            milestones++;
            lastMilestone = milestone;
        }
        if (milestones <= 0) {
            return;
        }
        plugin.getShards().add(player, totalShards);
        skills.claimedShardLevel = lastMilestone;
        dirty = true;
        String line = t(player, "§b+" + totalShards + " Aether Shards §8· §7every 25 levels",
                "§b+" + totalShards + " Aether-Splitter §8· §7alle 25 Level");
        if (rows != null) {
            rows.add(line);
            return;
        }
        player.sendMessage(line);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.7f);
    }

    private AetherSkill equippedWithFlag(Player player, AetherSkill.Flag flag) {
        if (flag == null || flag == AetherSkill.Flag.NONE) {
            return null;
        }
        for (AetherSkill skill : equipped(player)) {
            if (skill.flag() == flag) {
                return skill;
            }
        }
        return null;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean caveSense = hasFlag(player, AetherSkill.Flag.CAVE_SENSE);
            if (caveSense || hasFlag(player, AetherSkill.Flag.NIGHT_OWL)) {
                refresh(player);
            }
            tickCaveSense(player, caveSense);
            checkSlotUnlocks(player);
        }
    }

    /** Cave Sense speaks up once when the dark starts paying — then stays quiet a while. */
    private void tickCaveSense(Player player, boolean equipped) {
        UUID id = player.getUniqueId();
        if (!equipped || player.getGameMode() == GameMode.SPECTATOR) {
            caveSenseLit.remove(id);
            return;
        }
        if (!WorldLight.isDark(player)) {
            caveSenseLit.remove(id);
            return;
        }
        if (!caveSenseLit.add(id) || !cueReady(player, "cave_sense", 120_000L)) {
            return;
        }
        AetherSkill skill = equippedWithFlag(player, AetherSkill.Flag.CAVE_SENSE);
        double power = 10.0d * multiplier(player, AetherSkill.Flag.CAVE_SENSE);
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        player.sendActionBar(legacy.deserialize(coloredName(player, skill)
                + " §8· §7the dark is paying §8(+"
                + de.aetherion.items.item.ItemLore.formatStat(power) + " Mining Power)"));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 0.8f);
    }

    /**
     * Slots open with the Aetherion Level now, and the level card already says so. This only keeps
     * {@code seenSlots} current and plays a short click with an action-bar line when a slot opens.
     * {@code seenSlots < 0} means "never measured": record quietly instead of replaying old unlocks.
     */
    private void checkSlotUnlocks(Player player) {
        PlayerSkills skills = of(player);
        int now = unlockedSlots(player);
        if (skills.seenSlots < 0 || now < skills.seenSlots) {
            skills.seenSlots = now;
            dirty = true;
            return;
        }
        if (now == skills.seenSlots) {
            return;
        }
        skills.seenSlots = now;
        dirty = true;
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        player.sendActionBar(legacy.deserialize(t(player, "§a✚ Talent slot " + now + " is open §8· §7Journal › Skills",
                "§a✚ Talent-Platz " + now + " ist offen §8· §7Journal › Skills")));
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, 1.1f);
    }

    public void reloadFromDisk() {
        load();
    }

    public void overlayPlayerFromDisk(UUID playerId) {
        if (playerId == null || !file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null || !section.contains(playerId.toString())) {
            return;
        }
        loadPlayer(section, playerId.toString());
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            loadPlayer(section, key);
        }
    }

    private void loadPlayer(ConfigurationSection section, String key) {
        try {
            UUID id = UUID.fromString(key);
            PlayerSkills skills = new PlayerSkills();
            List<String> slots = section.getStringList(key + ".slots");
            for (int i = 0; i < SLOT_COUNT && i < slots.size(); i++) {
                skills.slots[i] = slots.get(i) == null ? "" : slots.get(i);
            }
            skills.bonusSlots = section.getInt(key + ".bonusSlots");
            skills.bonusXp = section.getLong(key + ".bonusXp");
            skills.claimedShardLevel = section.getInt(key + ".claimedShardLevel");
            skills.seenSlots = section.getInt(key + ".seenSlots", -1);
            List<String> presets = section.getStringList(key + ".presets");
            for (int i = 0; i < PRESET_COUNT && i < presets.size(); i++) {
                skills.presets[i] = presets.get(i) == null ? "" : presets.get(i);
            }
            ConfigurationSection progress = section.getConfigurationSection(key + ".progress");
            if (progress != null) {
                for (String skillId : progress.getKeys(false)) {
                    AetherSkill skill = AetherSkill.byId(skillId);
                    if (skill == null) {
                        continue;
                    }
                    skills.levels.put(skill, SkillProgression.clampLevel(progress.getInt(skillId + ".level", 1)));
                    skills.xp.put(skill, Math.max(0, progress.getInt(skillId + ".xp", 0)));
                }
            }
            ConfigurationSection v2 = section.getConfigurationSection(key + ".v2");
            if (v2 != null) {
                for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
                    skills.skillLevels.put(category,
                            SkillProgression.clampLevel(v2.getInt("skills." + category.key() + ".level", 1)));
                    skills.skillXp.put(category, Math.max(0, v2.getInt("skills." + category.key() + ".xp", 0)));
                }
                skills.pool = Math.max(0L, v2.getLong("pool"));
                skills.legacyPool = Math.max(0L, v2.getLong("legacyPool"));
                skills.utilityFloor = Math.max(1, v2.getInt("utilityFloor", 1));
                skills.slotFloor = v2.getInt("slotFloor", -1);
                skills.keptTalents.addAll(v2.getStringList("kept"));
            } else {
                migrateLegacy(skills);
            }
            data.put(id, skills);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Level-system v2 carry-over, once per player (the first time a v2 jar reads them). Nobody loses anything:
     * each skill starts at the best talent level the player had in it, the Aetherion pool keeps everything the old
     * talent levels were worth, talents the player ever used stay open, and slots are measured on the next login
     * (lifetime coins need the player) and never drop below that.
     */
    static void migrateLegacy(PlayerSkills skills) {
        long raw = 0L;
        for (AetherSkill talent : AetherSkill.values()) {
            raw += SkillProgression.spentXp(skills.legacyLevel(talent));
            raw += skills.legacyXp(talent);
        }
        skills.legacyPool = raw;
        skills.pool = 0L;
        for (AetherSkill.Category category : AetherSkill.Category.LEVELED) {
            int bestLevel = 1;
            int bestXp = 0;
            for (AetherSkill talent : TalentUnlocks.of(category)) {
                int level = skills.legacyLevel(talent);
                int xp = skills.legacyXp(talent);
                if (level > bestLevel || (level == bestLevel && xp > bestXp)) {
                    bestLevel = level;
                    bestXp = xp;
                }
            }
            skills.skillLevels.put(category, bestLevel);
            skills.skillXp.put(category, bestXp);
        }
        int utility = 1;
        for (AetherSkill talent : TalentUnlocks.of(AetherSkill.Category.UTILITY)) {
            utility = Math.max(utility, skills.legacyLevel(talent));
        }
        skills.utilityFloor = utility;
        for (AetherSkill talent : AetherSkill.values()) {
            if (skills.legacyLevel(talent) > 1 || skills.legacyXp(talent) > 0) {
                skills.keptTalents.add(talent.id());
            }
        }
        for (String slot : skills.slots) {
            AetherSkill talent = AetherSkill.byId(slot);
            if (talent != null) {
                skills.keptTalents.add(talent.id());
            }
        }
        for (String preset : skills.presets) {
            if (preset == null || preset.isBlank()) {
                continue;
            }
            for (String id : preset.split(",")) {
                AetherSkill talent = AetherSkill.byId(id.trim());
                if (talent != null) {
                    skills.keptTalents.add(talent.id());
                }
            }
        }
        skills.slotFloor = -1;
    }

    /** Test hook: a player with old per-talent levels and slots, carried over to v2. */
    static PlayerSkills carriedOver(java.util.Map<AetherSkill, Integer> legacyLevels, String... slotIds) {
        PlayerSkills skills = new PlayerSkills();
        legacyLevels.forEach((talent, level) -> skills.levels.put(talent, SkillProgression.clampLevel(level)));
        for (int i = 0; i < slotIds.length && i < SLOT_COUNT; i++) {
            skills.slots[i] = slotIds[i];
        }
        migrateLegacy(skills);
        return skills;
    }

    /**
     * One-shot offline wipe: drop UUIDs into {@code wipe-skills-on-boot.yml} under {@code players:},
     * then restart. Survives an in-memory {@code onDisable} save that would otherwise restore levels.
     */
    private void applyPendingWipes() {
        File pending = new File(plugin.getDataFolder(), "wipe-skills-on-boot.yml");
        if (!pending.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pending);
        List<String> raw = config.getStringList("players");
        if (raw.isEmpty()) {
            // allow bare UUID lines under root key "uuids" too
            raw = config.getStringList("uuids");
        }
        int wiped = 0;
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                UUID id = UUID.fromString(entry.trim());
                PlayerSkills skills = data.computeIfAbsent(id, ignored -> new PlayerSkills());
                skills.wipe();
                wiped++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (wiped > 0) {
            dirty = true;
            save();
            plugin.getLogger().info("Wiped skill progress for " + wiped + " player(s) from wipe-skills-on-boot.yml");
        }
        if (!pending.delete()) {
            plugin.getLogger().warning("Could not delete wipe-skills-on-boot.yml — remove it manually.");
        }
    }

    private static boolean isWood(Material material) {
        return Tag.LOGS.isTagged(material) || Tag.PLANKS.isTagged(material);
    }

    private static boolean isCrop(Material material) {
        return de.aetherion.items.farming.Crops.isCrop(material);
    }

    private static int miningXp(Material material) {
        String name = material.name();
        if (material == Material.ANCIENT_DEBRIS
                || material == Material.DIAMOND_ORE
                || material == Material.DEEPSLATE_DIAMOND_ORE
                || material == Material.EMERALD_ORE
                || material == Material.DEEPSLATE_EMERALD_ORE
                || material == Material.DIAMOND_BLOCK
                || material == Material.EMERALD_BLOCK
                || material == Material.NETHERITE_BLOCK) {
            return 18;
        }
        if (material == Material.AMETHYST_CLUSTER) {
            return 12;
        }
        if (HarvestRules.fullMineralBlock(material)) {
            return 14;
        }
        if (name.contains("_ORE") || material == Material.NETHER_QUARTZ_ORE) {
            return 8;
        }
        if (material == Material.COBBLESTONE
                || material == Material.STONE
                || material == Material.DEEPSLATE
                || material == Material.COBBLED_DEEPSLATE
                || material == Material.GRANITE
                || material == Material.DIORITE
                || material == Material.ANDESITE
                || material == Material.TUFF
                || material == Material.NETHERRACK
                || material == Material.END_STONE
                || material == Material.BLACKSTONE
                || material == Material.BASALT
                || material == Material.SMOOTH_BASALT
                || material == Material.GRAVEL
                || material == Material.SANDSTONE
                || material == Material.OBSIDIAN
                || material == Material.CALCITE
                || material == Material.DRIPSTONE_BLOCK) {
            return 3;
        }
        return 0;
    }

    public static final class PlayerSkills {
        private final String[] slots = new String[SLOT_COUNT];
        /** Old per-talent progress. Frozen since level-system v2; read once for the carry-over, written back as is. */
        private final EnumMap<AetherSkill, Integer> levels = new EnumMap<>(AetherSkill.class);
        private final EnumMap<AetherSkill, Integer> xp = new EnumMap<>(AetherSkill.class);
        /** Level-system v2: one level per skill. */
        private final EnumMap<AetherSkill.Category, Integer> skillLevels = new EnumMap<>(AetherSkill.Category.class);
        private final EnumMap<AetherSkill.Category, Integer> skillXp = new EnumMap<>(AetherSkill.Category.class);
        /** Skill XP earned since v2, raw; feeds the Aetherion Level. */
        private long pool;
        /** Raw talent XP before v2 (carried over once). New players start at 0. */
        private long legacyPool;
        /** Best old Utility talent level, so Utility never drops below it. */
        private int utilityFloor = 1;
        /** Talent slots the player had before slots moved to levels; -1 = measure at the next look. */
        private int slotFloor = 1;
        /** Talents the player used before v2: they stay open whatever the skill level. */
        private final Set<String> keptTalents = new HashSet<>();
        private int bonusSlots;
        private long bonusXp;
        private int claimedShardLevel;
        /** Slot count the player has already been told about; -1 = not yet measured. */
        private int seenSlots = -1;
        /** Saved loadouts: comma-joined skill ids per preset ({@code ""} = empty). */
        private final String[] presets = new String[PRESET_COUNT];

        private PlayerSkills() {
            java.util.Arrays.fill(slots, "");
            java.util.Arrays.fill(presets, "");
        }

        int legacyLevel(AetherSkill skill) {
            return SkillProgression.clampLevel(levels.getOrDefault(skill, 1));
        }

        int legacyXp(AetherSkill skill) {
            return Math.max(0, xp.getOrDefault(skill, 0));
        }

        /** Utility follows the best skill (and never drops below the old best Utility talent). */
        AetherSkill.Category leveledFor(AetherSkill.Category category) {
            if (category != null && category.leveled()) {
                return category;
            }
            AetherSkill.Category best = AetherSkill.Category.COMBAT;
            int bestLevel = 0;
            for (AetherSkill.Category skill : AetherSkill.Category.LEVELED) {
                int level = SkillProgression.clampLevel(skillLevels.getOrDefault(skill, 1));
                if (level > bestLevel) {
                    best = skill;
                    bestLevel = level;
                }
            }
            return best;
        }

        int skillLevel(AetherSkill.Category category) {
            if (category == null) {
                return 1;
            }
            if (!category.leveled()) {
                return Math.max(utilityFloor, skillLevel(leveledFor(category)));
            }
            return SkillProgression.clampLevel(skillLevels.getOrDefault(category, 1));
        }

        int skillXp(AetherSkill.Category category) {
            if (category == null) {
                return 0;
            }
            if (!category.leveled()) {
                AetherSkill.Category best = leveledFor(category);
                return utilityFloor > skillLevel(best) ? 0 : skillXp(best);
            }
            return Math.max(0, skillXp.getOrDefault(category, 0));
        }

        boolean keeps(String talentId) {
            return keptTalents.contains(talentId);
        }

        long carried() {
            return legacyPool;
        }

        /** Back to a fresh start: levels, pool, kept talents, slots. */
        void wipe() {
            java.util.Arrays.fill(slots, "");
            bonusSlots = 0;
            levels.clear();
            xp.clear();
            skillLevels.clear();
            skillXp.clear();
            pool = 0L;
            legacyPool = 0L;
            utilityFloor = 1;
            slotFloor = 1;
            keptTalents.clear();
        }
    }

    /** Full skill wipe for online or offline — RAM + skills.yml key removed. */
    public void wipePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            wipeProgress(online);
            data.remove(playerId);
        } else {
            data.remove(playerId);
        }
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        config.set("players." + playerId, null);
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
            dirty = false;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not wipe skills for " + playerId + ": " + exception.getMessage());
            dirty = true;
        }
    }

}
