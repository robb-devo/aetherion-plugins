package de.aetherion.items.combat.system;

import de.aetherion.core.AetherEntities;
import de.aetherion.core.api.AetherServices;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.util.QuestProgressHook;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared player combat kit: stamina, dash, roll, parry / guard, melee cadence, hit feedback.
 * <p>
 * Binding map (vanilla client, no mod; see combat-system.yml):
 * <ul>
 *   <li>F (swap hands) with a weapon = parry / guard. Sneak + F swaps hands as usual.</li>
 *   <li>Sprinting, tap Sneak = dash.</li>
 *   <li>Hold Sneak + Jump = roll (i-frames; no movement = backstep).</li>
 *   <li>Full-charge swing = heavy; jump-crit = aerial heavy; three timed hits on one target = finisher;
 *       hit right after a parry = riposte.</li>
 * </ul>
 * Composes with {@code DamageListener}: the Aetherion DAMAGE stamp stays, cadence only scales it afterwards.
 * Bosses keep their own AI; the kit never pauses or knocks back a boss entity.
 */
public final class CombatSystem {

    /** How long after the last hit dealt or taken a player still counts as fighting. */
    static final int COMBAT_WINDOW = 200;

    private static volatile CombatSystem instance;

    private final AetherionItems plugin;
    private final ItemManager items;
    private final ActiveEquipmentStats stats;
    private final Map<UUID, CombatState> states = new ConcurrentHashMap<>();
    private final NamespacedKey samuraiOwned = new NamespacedKey("samuraiboss", "owned");

    private volatile CombatConfig config;
    private final HitFeedback feedback;
    private final Mobility mobility;
    private final Guard guard;
    private final MeleeCadence cadence;
    private final WeaponTempo tempo;
    private final CombatTutorial tutorial;
    private final TrainingDummy dummies;
    private BukkitTask task;

    private CombatSystem(AetherionItems plugin) {
        this.plugin = plugin;
        this.items = plugin.getItemManager();
        this.stats = new ActiveEquipmentStats(items);
        this.config = CombatConfig.load(plugin);
        this.feedback = new HitFeedback(this);
        this.mobility = new Mobility(this);
        this.guard = new Guard(this);
        this.cadence = new MeleeCadence(this);
        this.tempo = new WeaponTempo(this);
        this.tutorial = new CombatTutorial(this);
        this.dummies = new TrainingDummy(this);
    }

    /** Called from {@code AetherionItems#onEnable} after DamageListener and HealthListener are registered. */
    public static void enable(AetherionItems plugin) {
        if (instance != null) {
            disable();
        }
        CombatSystem system = new CombatSystem(plugin);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(system), plugin);
        CombatCommand.register(plugin, system);
        system.guard.repairLoaded();
        system.task = plugin.getServer().getScheduler().runTaskTimer(plugin, system::tick, 1L, 1L);
        instance = system;
    }

    public static void disable() {
        CombatSystem system = instance;
        instance = null;
        if (system == null) {
            return;
        }
        if (system.task != null) {
            system.task.cancel();
        }
        CombatCommand.unregister(system.plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            system.tempo.clear(player);
        }
        system.dummies.removeAll();
        system.guard.releaseAll();
        system.feedback.releaseAll();
        system.states.clear();
    }

    public static CombatSystem get() {
        return instance;
    }

    /* =========================================================
     * HUD (read by combat.StaminaHud through PlayerHud)
     * ========================================================= */

    /** Gauge snapshot for the HUD, or null when the gauge is hidden (kit off, full and idle). */
    public static Gauge gauge(Player player) {
        CombatSystem system = instance;
        if (system == null || player == null) {
            return null;
        }
        CombatConfig cfg = system.config;
        if (!system.active(player)) {
            return null;
        }
        CombatState state = system.states.get(player.getUniqueId());
        if (state == null) {
            return null;
        }
        long tick = Bukkit.getCurrentTick();
        boolean full = state.stamina >= cfg.staminaMax - 0.01;
        boolean fighting = state.inCombat(tick, COMBAT_WINDOW);
        if (full && !fighting && tick - state.fullSinceTick > cfg.hideAfterFull && tick >= state.denyUntil) {
            return null;
        }
        return new Gauge(state.stamina / cfg.staminaMax, tick < state.denyUntil, state.exhausted(tick),
                state.guarding(tick), state.perfectParry(tick), tick - state.lastSpendTick < 4, (tick / 4) % 2 == 0);
    }

    /** pulse: a move was paid for in the last few ticks (the bar flashes). blink: phase for the winded flash. */
    public record Gauge(double fraction, boolean denied, boolean exhausted, boolean guarding, boolean perfect,
                        boolean pulse, boolean blink) {
    }

    /* =========================================================
     * ONBOARDING DRILL (Quests lesson_combat, Sergeant Vex)
     * ========================================================= */

    /** Vex's drill quest; a clean move is reported only while it runs. */
    static final String DRILL_QUEST = "lesson_combat";
    /** USE targets the drill counts (mirrored in Quests {@code tutorial.CombatTeach}). */
    static final String DRILL_PARRY = "COMBAT_PARRY";
    static final String DRILL_DODGE = "COMBAT_DODGE";
    static final String DRILL_FINISHER = "COMBAT_FINISHER";

    /** True while the kit runs ({@code enabled} in combat-system.yml). Quests falls back to its gear check otherwise. */
    public static boolean running() {
        CombatSystem system = instance;
        return system != null && system.config.enabled;
    }

    /**
     * Puts this player's training golem up beside them for the drill, replacing an old one. False when the kit
     * is off, the golem cap is reached, there is no open ground, or a protection plugin refused the spawn.
     */
    public static boolean drillGolem(Player player) {
        CombatSystem system = instance;
        return system != null && player != null && player.isOnline() && system.dummies.spawnNear(player);
    }

    /** True while this player's training golem stands within {@code range} blocks of them. */
    public static boolean hasGolem(Player player, double range) {
        CombatSystem system = instance;
        return system != null && player != null && system.dummies.hasNear(player, range);
    }

    /** Removes this player's training golem (drill passed). */
    public static void clearGolem(Player player) {
        CombatSystem system = instance;
        if (system != null && player != null) {
            system.dummies.clear(player);
        }
    }

    /** A clean move for Vex's drill: one quest lookup per clean move, a USE note only while the quest runs. */
    void drill(Player player, String target) {
        if (player != null && QuestProgressHook.isQuestActive(player, DRILL_QUEST)) {
            QuestProgressHook.noteUsed(player, target);
        }
    }

    /* =========================================================
     * SHARED STATE
     * ========================================================= */

    CombatConfig config() {
        return config;
    }

    AetherionItems plugin() {
        return plugin;
    }

    ItemManager items() {
        return items;
    }

    ActiveEquipmentStats stats() {
        return stats;
    }

    HitFeedback feedback() {
        return feedback;
    }

    Mobility mobility() {
        return mobility;
    }

    Guard guard() {
        return guard;
    }

    MeleeCadence cadence() {
        return cadence;
    }

    WeaponTempo tempo() {
        return tempo;
    }

    CombatTutorial tutorial() {
        return tutorial;
    }

    TrainingDummy dummies() {
        return dummies;
    }

    CombatState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> {
            CombatState fresh = new CombatState(id, config.staminaMax);
            fresh.fullSinceTick = Long.MIN_VALUE / 4;
            return fresh;
        });
    }

    CombatState peek(Player player) {
        return player == null ? null : states.get(player.getUniqueId());
    }

    void forget(UUID playerId) {
        states.remove(playerId);
        guard.forget(playerId);
    }

    /** Re-reads combat-system.yml. Running windows keep their old numbers until they end. */
    void reload() {
        config = CombatConfig.load(plugin);
        for (CombatState state : states.values()) {
            state.stamina = Math.min(state.stamina, config.staminaMax);
        }
    }

    /* =========================================================
     * RULES
     * ========================================================= */

    /** Kit is on for this player at all: enabled, survival / adventure, world not disabled. */
    boolean active(Player player) {
        CombatConfig cfg = config;
        if (!cfg.enabled || player == null) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !cfg.worldDisabled(player.getWorld().getName());
    }

    /** Whether the kit may act for this player right now (mode, world, mount, flight, weapon in hand). */
    boolean eligible(Player player) {
        CombatConfig cfg = config;
        if (!active(player) || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.isInsideVehicle() || player.isGliding() || player.isFlying() || player.isSwimming()
                || player.isRiptiding() || player.isSleeping()) {
            return false;
        }
        // Scripted movement (Blossom Blade's dash, boss cinematics) turns gravity off or roots with heavy
        // slowness; the kit stays out of the way until it ends.
        if (!player.hasGravity()) {
            return false;
        }
        PotionEffect slow = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow != null && slow.getAmplifier() >= 2) {
            return false;
        }
        return !cfg.requireWeapon || isWeapon(player.getInventory().getItemInMainHand());
    }

    boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material type = stack.getType();
        String name = type.name();
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || type == Material.FISHING_ROD) {
            return false;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || type == Material.MACE || type == Material.TRIDENT
                || type == Material.BOW || type == Material.CROSSBOW) {
            return true;
        }
        if (!items.isAetherionItem(stack)) {
            return false;
        }
        return items.isShortbow(stack) || items.isLongbow(stack) || items.isWand(stack) || items.isCustomCrossbow(stack)
                || items.getStat(stack, ItemCapability.DAMAGE) > 0.0;
    }

    /** Ranged and wand items keep their own fire logic; melee cadence leaves them alone. */
    boolean isRangedOrWand(ItemStack stack) {
        return stack != null && (items.isShortbow(stack) || items.isLongbow(stack) || items.isCustomCrossbow(stack)
                || items.isWand(stack));
    }

    String heldItemId(Player player) {
        return items.getItemId(player.getInventory().getItemInMainHand());
    }

    /** True when DamageListener stamped the swing (the player carries Aetherion DAMAGE). */
    boolean stamped(Player player) {
        return stats.getStat(player, ItemCapability.DAMAGE) > 0.0;
    }

    double maxHealth(Player player) {
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? 20.0 : Math.max(1.0, attribute.getValue());
    }

    /** Bosses, boss parts and system entities: the kit never stuns, pauses, reflects at or knocks them back. */
    boolean protectedEntity(Entity entity) {
        if (entity == null) {
            return true;
        }
        if (AetherEntities.isBoss(entity) || AetherEntities.isBossMinion(entity) || samuraiHitbox(entity)) {
            return true;
        }
        Entity root = AetherEntities.root(entity);
        return AetherEntities.isPet(root) || AetherEntities.isSetMinion(root) || AetherEntities.isQuestNpc(root)
                || AetherEntities.isDungeonNpc(root) || AetherEntities.isFishingEncounter(root)
                || root.hasMetadata("NPC") || root.isInvulnerable();
    }

    /** Boss-tier target for bonus scaling and parry rules (boss, boss minion, Samurai hitbox). */
    boolean bossTier(Entity entity) {
        return entity != null && (AetherEntities.isBoss(entity) || AetherEntities.isBossMinion(entity) || samuraiHitbox(entity));
    }

    /** BossEngine marks a boss open (metadata, tick until) after its big moves; see opening-mult. */
    boolean bossOpening(Entity entity) {
        Entity root = AetherEntities.root(entity);
        for (org.bukkit.metadata.MetadataValue value : root.getMetadata("aetherion_opening")) {
            if (value.asLong() > Bukkit.getCurrentTick()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tooltip's tempo line ({@code ⚡ Tempo: Fast (1.6/s)}) for a melee item the weapon tempo drives, or null
     * when the system is off or the item keeps its own swing speed.
     */
    public static String tempoLine(ItemStack item, String itemId) {
        CombatSystem system = instance;
        if (system == null || item == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        double speed = system.tempo.speedOf(item, itemId);
        if (Double.isNaN(speed)) {
            return null;
        }
        String word = speed >= 1.9 ? "Very fast" : speed >= 1.5 ? "Fast" : speed >= 1.1 ? "Medium"
                : speed >= 0.85 ? "Slow" : "Very slow";
        return "§7⚡ Tempo: §f" + word + " §8(" + String.format(Locale.US, "%.1f", speed) + "/s)";
    }

    boolean samuraiHitbox(Entity entity) {
        if (entity == null) {
            return false;
        }
        String owned = entity.getPersistentDataContainer().get(samuraiOwned, PersistentDataType.STRING);
        return owned != null;
    }

    /** Horizontal unit vector the player faces, from yaw (safe when looking straight up or down). */
    static Vector facing(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    static String t(Player player, String en, String de) {
        return german(player) ? de : en;
    }

    static boolean german(Player player) {
        return player != null && AetherServices.german(player);
    }

    static String lower(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }

    /* =========================================================
     * TICK
     * ========================================================= */

    private void tick() {
        long tick = Bukkit.getCurrentTick();
        CombatConfig cfg = config;
        Iterator<CombatState> it = states.values().iterator();
        while (it.hasNext()) {
            CombatState state = it.next();
            Player player = Bukkit.getPlayer(state.id);
            if (player == null || !player.isOnline()) {
                it.remove();
                guard.forget(state.id);
                continue;
            }
            regen(state, cfg, tick);
            guard.tick(player, state, tick);
            mobility.tick(player, state, tick);
        }
        if (tick % 2 == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tempo.refresh(player);
            }
        }
        guard.tickStaggers(tick);
        feedback.tick(tick);
        dummies.tick(tick);
    }

    private static void regen(CombatState state, CombatConfig cfg, long tick) {
        if (state.stamina >= cfg.staminaMax) {
            return;
        }
        if (state.exhausted(tick) || state.guarding(tick) || tick - state.lastSpendTick < cfg.regenDelay) {
            return;
        }
        state.stamina = Math.min(cfg.staminaMax, state.stamina + cfg.regenPerTick);
        if (state.stamina >= cfg.staminaMax) {
            state.fullSinceTick = tick;
        }
    }

    /**
     * Pays for a move. Over-committing is allowed once: any stamina left buys the move, landing at 0 = winded.
     * Returns false (and pulses the gauge) when winded or empty.
     */
    boolean spend(Player player, CombatState state, double cost, long tick) {
        CombatConfig cfg = config;
        if (state.exhausted(tick) || state.stamina <= 0.5) {
            deny(player, state, tick);
            return false;
        }
        state.stamina = Math.max(0.0, state.stamina - cost);
        state.lastSpendTick = tick;
        if (state.stamina <= 0.0) {
            state.exhaustedUntil = tick + cfg.exhaustedTicks;
            feedback.winded(player, cfg.exhaustedTicks);
        }
        return true;
    }

    void refund(CombatState state, double amount) {
        if (amount > 0) {
            state.stamina = Math.min(config.staminaMax, state.stamina + amount);
        }
    }

    /** An entity hurt this player (MONITOR): combat flag, and the knockback guard for rolls. */
    void hurt(Player player, long tick) {
        if (!active(player)) {
            return;
        }
        markCombat(player, tick);
        state(player).lastHurtTick = tick;
    }

    void deny(Player player, CombatState state, long tick) {
        boolean fresh = tick >= state.denyUntil;
        state.denyUntil = tick + 10;
        if (fresh) {
            feedback.deny(player);
        }
    }

    void markCombat(Player player, long tick) {
        if (!active(player)) {
            return;
        }
        CombatState state = state(player);
        boolean first = !state.inCombat(tick, COMBAT_WINDOW);
        state.lastCombatTick = tick;
        if (first) {
            tutorial.maybeShow(player);
        }
    }
}
