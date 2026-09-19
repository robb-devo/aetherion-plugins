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
    public static final long[] COIN_UNLOCK = {0L, 5_000L, 25_000L, 80_000L, 200_000L, 500_000L, 1_250_000L};

    private final AetherionItems plugin;
    private final CoinService coins;
    private final File file;
    private final ConcurrentHashMap<UUID, PlayerSkills> data = new ConcurrentHashMap<>();
    /** Nested depth while quest reward lines are printing — defer Aetherion level chat. */
    private final ConcurrentHashMap<UUID, Integer> rewardBatchDepth = new ConcurrentHashMap<>();
    /** Earliest pre-batch account level to announce when the batch closes. */
    private final ConcurrentHashMap<UUID, Integer> deferredAccountFrom = new ConcurrentHashMap<>();
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
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
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
        PlayerSkills skills = of(player);
        int unlocked = 1;
        long lifetime = coins == null ? 0L : coins.lifetime(player);
        for (int i = 1; i < SLOT_COUNT; i++) {
            if (lifetime >= COIN_UNLOCK[i]) {
                unlocked = i + 1;
            }
        }
        return Math.min(SLOT_COUNT, unlocked + skills.bonusSlots);
    }

    public String unlockHint(int slotIndex) {
        if (slotIndex <= 0) {
            return "§7Always unlocked. Try not to waste it.";
        }
        if (slotIndex >= SLOT_COUNT) {
            return "§7That's all seven. Don't get greedy.";
        }
        return "§7Unlock with §f" + String.format(Locale.US, "%,d", COIN_UNLOCK[slotIndex])
                + " §7lifetime coins.";
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
        List<AetherSkill> extras = new ArrayList<>();
        Set<AetherSkill> equipped = new HashSet<>(equipped(player));
        for (AetherSkill skill : AetherSkill.values()) {
            if (equipped.contains(skill)) {
                continue;
            }
            if (level(player, skill) <= 1 && xp(player, skill) <= 0) {
                continue;
            }
            extras.add(skill);
        }
        extras.sort(Comparator
                .comparingInt((AetherSkill skill) -> level(player, skill))
                .reversed()
                .thenComparing(AetherSkill::displayName, String.CASE_INSENSITIVE_ORDER));
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

    private String extraName(Player player, AetherSkill skill) {
        return "§7" + skill.displayName() + " §f" + level(player, skill);
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

    public int level(Player player, AetherSkill skill) {
        return of(player).level(skill);
    }

    public int accountLevel(Player player) {
        return AetherionLevel.of(accountXp(player));
    }

    public String accountTag(Player player) {
        return AetherionLevel.tag(accountLevel(player));
    }

    public long accountXp(Player player) {
        PlayerSkills data = of(player);
        long skillRaw = 0L;
        for (AetherSkill skill : AetherSkill.values()) {
            skillRaw += SkillProgression.spentXp(data.level(skill));
            skillRaw += data.xp(skill);
        }
        // Skill grind XP is compressed; bonus XP (pets, …) is 1:1 toward the flat 100/level pool.
        return data.bonusXp + AetherionLevel.fromSkillXp(skillRaw);
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
        return of(player).xp(skill);
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
        if (slotOf(player, skill) >= 0) {
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
        PlayerSkills skills = of(player);
        for (int i = 0; i < skills.slots.length; i++) {
            skills.slots[i] = "";
        }
        skills.bonusSlots = 0;
        skills.levels.clear();
        skills.xp.clear();
        dirty = true;
        refresh(player);
    }

    public void setLevel(Player player, AetherSkill skill, int level) {
        if (player == null || skill == null) {
            return;
        }
        int before = accountLevel(player);
        PlayerSkills data = of(player);
        data.levels.put(skill, SkillProgression.clampLevel(level));
        data.xp.put(skill, 0);
        dirty = true;
        refresh(player);
        announceAccountLevel(player, before);
    }

    public int highestLevel(Player player, AetherSkill.Category category) {
        if (player == null || category == null) {
            return 1;
        }
        int best = 1;
        for (AetherSkill skill : AetherSkill.values()) {
            if (skill.category() != category) {
                continue;
            }
            best = Math.max(best, level(player, skill));
        }
        return best;
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
        int before = accountLevel(player);
        PlayerSkills data = of(player);
        int clamped = SkillProgression.clampLevel(level);
        for (AetherSkill skill : AetherSkill.values()) {
            data.levels.put(skill, clamped);
            data.xp.put(skill, 0);
        }
        dirty = true;
        refresh(player);
        announceAccountLevel(player, before);
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
    }

    public void grantFromFish(Player player, int amount) {
        if (!canEarnXp(player) || amount <= 0) {
            return;
        }
        sharePetGather(player, amount);
        grant(player, AetherSkill.Category.FISHING, Math.max(1, amount));
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
        if (hasFlag(player, AetherSkill.Flag.BOSS_GRUDGE)) {
            bonus += 0.10d * multiplier(player, AetherSkill.Flag.BOSS_GRUDGE);
        }
        if (de.aetherion.items.dungeon.DungeonArmor.inDungeon(player)
                && hasFlag(player, AetherSkill.Flag.FLOOR_GRUDGE)) {
            bonus += 0.12d * multiplier(player, AetherSkill.Flag.FLOOR_GRUDGE);
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
            double scale = multiplier(player, skill);
            total += skill.bonus(capability) * scale;
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
            for (AetherSkill skill : AetherSkill.values()) {
                int level = skills.level(skill);
                int xp = skills.xp(skill);
                if (level <= 1 && xp <= 0) {
                    continue;
                }
                config.set(path + ".progress." + skill.id() + ".level", level);
                config.set(path + ".progress." + skill.id() + ".xp", xp);
            }
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

    private void grant(Player player, AetherSkill.Category category, int amount) {
        if (player == null || category == null || amount <= 0) {
            return;
        }
        for (AetherSkill skill : equipped(player)) {
            int grant = amount;
            if (skill.category() != category) {
                if (skill.category() != AetherSkill.Category.UTILITY) {
                    continue;
                }
                grant = Math.max(1, amount / 2);
            }
            addXp(player, skill, grant);
        }
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

    private void addXp(Player player, AetherSkill skill, int amount) {
        amount = scaleXp(player, amount);
        PlayerSkills data = of(player);
        int level = data.level(skill);
        if (SkillProgression.isMax(level)) {
            return;
        }
        int accountBefore = accountLevel(player);
        int xp = data.xp(skill) + amount;
        int needed = SkillProgression.xpToNext(level);
        boolean leveled = false;
        while (xp >= needed && needed > 0 && level < SkillProgression.MAX_LEVEL) {
            xp -= needed;
            int previous = level;
            level++;
            leveled = true;
            announceSkillLevel(player, skill, previous, level);
            needed = SkillProgression.xpToNext(level);
        }
        if (SkillProgression.isMax(level)) {
            xp = 0;
        }
        data.levels.put(skill, level);
        data.xp.put(skill, xp);
        dirty = true;
        announceAccountLevel(player, accountBefore);
        if (leveled) {
            refresh(player);
        }
    }

    private void announceSkillLevel(Player player, AetherSkill skill, int previous, int level) {
        Rarity before = SkillProgression.rarity(previous);
        Rarity after = SkillProgression.rarity(level);
        String color = after.getChatColor().toString();
        player.sendMessage("§d" + skill.displayName() + " §7reached §fLv. " + level
                + " §8· " + color + after.name());
        if (level % 10 == 9 || level % 10 == 0) {
            player.sendMessage("§7" + SkillFlavor.tagline(skill, level));
        }
        if (before != after) {
            player.sendMessage("§6New rarity. The skill has thoughts now. +"
                    + SkillProgression.rarityBonusPercent(level) + "% effect.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.6f);
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

    private void announceAccountLevelNow(Player player, int previous) {
        int current = accountLevel(player);
        if (current <= previous) {
            return;
        }
        player.sendMessage("§6Aetherion Level §f" + previous + " §7→ " + AetherionLevel.coloredLevel(current));
        if (AetherionLevel.titleTier(current) > AetherionLevel.titleTier(previous)) {
            player.sendMessage("§eNew rank. " + AetherionLevel.coloredTitle(current) + "§e.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.05f);
        } else if (current % 10 == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        if (plugin.ranks() != null) {
            plugin.ranks().syncProgression(player, current);
        }
        int statBefore = AetherionLevel.milestoneStatBonus(previous);
        int statAfter = AetherionLevel.milestoneStatBonus(current);
        if (statAfter > statBefore) {
            player.sendMessage("§a+" + (statAfter - statBefore) + " Damage §8· §c+"
                    + (statAfter - statBefore) + " Health §8· §7every " + AetherionLevel.STAT_EVERY + " levels");
            refresh(player);
        }
        // World pace (silent move/jump) kicks in at Lv.3 — refresh attributes, no announcement.
        if (previous < 3 && current >= 3) {
            refresh(player);
        }
        if (previous < de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL
                && current >= de.aetherion.items.listener.AutoPickupListener.UNLOCK_LEVEL) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Auto pickup unlocked · the ground clocks out",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA
            ));
            player.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text(
                            "AUTO PICKUP",
                            net.kyori.adventure.text.format.NamedTextColor.AQUA
                    ),
                    net.kyori.adventure.text.Component.text(
                            "Drops find you now",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY
                    ),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(80),
                            java.time.Duration.ofMillis(1800),
                            java.time.Duration.ofMillis(350)
                    )
            ));
        }
        claimLevelShards(player);
        // Match reward-block breathing room (blank before deferred flush in Quests).
        player.sendMessage("");
        AetherionItems items = AetherionItems.getInstance();
        if (items != null && items.xpBarSync() != null) {
            items.xpBarSync().sync(player);
        }
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
        saveIfDirty();
    }

    public void claimLevelShards(Player player) {
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
        if (milestones == 1) {
            player.sendMessage("§b+" + totalShards + " Aether Shards §8· §7level " + lastMilestone);
            player.sendMessage("§7Every §f25 §7levels. Once. The shop noticed.");
        } else {
            player.sendMessage("§6Aetherion Level " + AetherionLevel.coloredLevel(current)
                    + " §7— stored XP carries you further.");
            player.sendMessage("§b+" + totalShards + " Aether Shards §8· §7" + milestones
                    + " milestones §8(§7through " + lastMilestone + "§8)");
            player.sendMessage("§7Every §f" + AetherionLevel.STAT_EVERY
                    + " §7levels still grants §a+1 Damage §7& §c+1 Health§7.");
        }
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
            if (hasFlag(player, AetherSkill.Flag.NIGHT_OWL) || hasFlag(player, AetherSkill.Flag.CAVE_SENSE)) {
                refresh(player);
            }
        }
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
            data.put(id, skills);
        } catch (IllegalArgumentException ignored) {
        }
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
                for (int i = 0; i < skills.slots.length; i++) {
                    skills.slots[i] = "";
                }
                skills.bonusSlots = 0;
                skills.levels.clear();
                skills.xp.clear();
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
        private final EnumMap<AetherSkill, Integer> levels = new EnumMap<>(AetherSkill.class);
        private final EnumMap<AetherSkill, Integer> xp = new EnumMap<>(AetherSkill.class);
        private int bonusSlots;
        private long bonusXp;
        private int claimedShardLevel;

        private PlayerSkills() {
            java.util.Arrays.fill(slots, "");
        }

        int level(AetherSkill skill) {
            return SkillProgression.clampLevel(levels.getOrDefault(skill, 1));
        }

        int xp(AetherSkill skill) {
            return Math.max(0, xp.getOrDefault(skill, 0));
        }
    }
}
