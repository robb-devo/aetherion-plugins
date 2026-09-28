package de.aetherion.fishing.isle;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.QuestBars;
import de.aetherion.fishing.FishingSkills;
import de.aetherion.items.util.InventoryDrops;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The lake-wide events. Both ring the island's bells first, run on one shared boss bar, and end
 * with a personal tally.
 * <ul>
 *   <li><b>Silver Run</b> — two minutes of herring. Lake bites come three times as fast, the run
 *   forgives one miss per angler, every catch pays extra XP and coins.</li>
 *   <li><b>The Eldermaw</b> — something vast circles one of the big lakes. Every catch anyone lands
 *   on the isle is a heave on its line (a perfect reel heaves twice). Haul it up before it dives
 *   and everyone who pulled gets paid; the strongest arm keeps a scale.</li>
 * </ul>
 */
public final class LakeEvents implements Listener {

    static final String LEASE = "lake-event";
    private static final int TELEGRAPH_TICKS = 20 * 10;
    private static final int RUN_TICKS = 20 * 120;
    private static final int MAW_TICKS = 20 * 180;
    public static final double RUN_WAIT = 0.35d;
    private static final int RUN_XP = 2;
    private static final long RUN_COINS = 8L;

    public enum Kind {
        SILVER_RUN("Silver Run", BossBar.Color.WHITE),
        ELDERMAW("The Eldermaw", BossBar.Color.PURPLE);

        final String display;
        final BossBar.Color color;

        Kind(String display, BossBar.Color color) {
            this.display = display;
            this.color = color;
        }

        public String display() {
            return display;
        }
    }

    private enum Phase {
        IDLE,
        TELEGRAPH,
        ACTIVE
    }

    private record Circuit(Location center, double radius) {
    }

    private final FishIsle isle;
    private final NamespacedKey mawKey;
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> tally = new HashMap<>();
    private final Set<UUID> forgiven = new HashSet<>();
    private BukkitTask timer;
    private BukkitTask ticker;
    private Phase phase = Phase.IDLE;
    private Kind kind;
    private Kind lastKind;
    private int phaseTicks;
    private int activeTicks;
    private long nextAtMs;
    private BossBar bar;
    // Eldermaw
    private UUID maw;
    private Circuit circuit;
    private String mawWater;
    private String mawWaterId;
    private double angle;
    private int hp;
    private int hpMax;
    private int lakeTally;
    private int breachTicks = -1;

    LakeEvents(FishIsle isle) {
        this.isle = isle;
        this.mawKey = new NamespacedKey(isle.plugin(), "eldermaw");
    }

    void start() {
        purgeMaw();
        ticker = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::tick, 5L, 1L);
        if (!isle.plugin().getConfig().getBoolean("lake-events.enabled", true)) {
            return;
        }
        long interval = intervalTicks();
        long first = Math.max(20L * 60L, isle.plugin().getConfig().getLong("lake-events.first-delay-ticks", 20L * 60L * 5L));
        nextAtMs = System.currentTimeMillis() + first * 50L;
        timer = Bukkit.getScheduler().runTaskTimer(isle.plugin(), this::maybeStart, first, interval);
    }

    void shutdown() {
        stop(true);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        purgeMaw();
    }

    private long intervalTicks() {
        return Math.max(20L * 120L, isle.plugin().getConfig().getLong("lake-events.interval-ticks", 20L * 60L * 18L));
    }

    // ------------------------------------------------------------------ queries

    public boolean runActive() {
        return phase == Phase.ACTIVE && kind == Kind.SILVER_RUN;
    }

    public boolean mawActive() {
        return phase == Phase.ACTIVE && kind == Kind.ELDERMAW && breachTicks < 0;
    }

    public long secondsUntilNext() {
        if (phase != Phase.IDLE) {
            return 0L;
        }
        return Math.max(0L, (nextAtMs - System.currentTimeMillis()) / 1000L);
    }

    public String statusLine() {
        if (phase == Phase.IDLE) {
            return "§7Next lake event in §f" + LakeText.clock(secondsUntilNext());
        }
        if (phase == Phase.TELEGRAPH) {
            return "§e" + kind.display + " §8· §7the bells are ringing…";
        }
        String left = LakeText.clock((total() - activeTicks) / 20L);
        return kind == Kind.SILVER_RUN
                ? "§fSilver Run §8· §b" + lakeTally + " §7caught §8· §f" + left + " §7left"
                : "§5The Eldermaw §8· §f" + mawWater + " §8· §d" + Math.max(0, hp) + "§7/§d" + hpMax + " §8· §f" + left + " §7left";
    }

    /** First miss in a Silver Run keeps the streak. Returns true when this miss is forgiven. */
    boolean forgive(Player player) {
        return runActive() && forgiven.add(player.getUniqueId());
    }

    // ------------------------------------------------------------------ catch hook

    /** Every landed isle catch: run tally / Eldermaw heave. Returns an action-bar tag or null. */
    String onCatch(Player player, Location hook, boolean perfect, int streak, String water) {
        if (phase != Phase.ACTIVE) {
            return null;
        }
        if (kind == Kind.SILVER_RUN) {
            if (!LakeWorld.lakeLevel(hook.getY())) {
                return null;
            }
            lakeTally++;
            tally.merge(player.getUniqueId(), 1, Integer::sum);
            FishingSkills.bonus(player, RUN_XP);
            FishingSkills.coins(player, RUN_COINS);
            return "§fSilver Run §7+" + RUN_COINS;
        }
        if (breachTicks >= 0) {
            return null;
        }
        int heave = (perfect ? 2 : 1) + (streak >= TheLine.BOILING ? 1 : 0) + (water != null && water.equals(mawWaterId) ? 1 : 0);
        hp -= heave;
        tally.merge(player.getUniqueId(), heave, Integer::sum);
        lakeTally += heave;
        Entity entity = maw == null ? null : Bukkit.getEntity(maw);
        if (entity != null) {
            World world = entity.getWorld();
            world.spawnParticle(Particle.SPLASH, entity.getLocation().add(0, 1.5, 0), 18, 1.2, 0.3, 1.2, 0.1);
            if (hp > 0 && (hp * 4 / Math.max(1, hpMax)) != ((hp + heave) * 4 / Math.max(1, hpMax))) {
                world.playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 2.5f, 0.6f);
                for (Player visitor : LakeWorld.visitors()) {
                    LakeText.bar(visitor, "§5The Eldermaw thrashes! §7Keep hauling.");
                }
            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 0.6f, 0.7f);
        if (hp <= 0) {
            breach();
        }
        return "§5Heave §d+" + heave;
    }

    // ------------------------------------------------------------------ lifecycle

    private void maybeStart() {
        nextAtMs = System.currentTimeMillis() + intervalTicks() * 50L;
        if (phase != Phase.IDLE || LakeWorld.anglers().isEmpty()) {
            return;
        }
        begin(lastKind == Kind.SILVER_RUN ? Kind.ELDERMAW : Kind.SILVER_RUN);
    }

    /** DEV / timer entry. */
    public String begin(Kind next) {
        if (phase != Phase.IDLE) {
            stop(true);
        }
        if (next == Kind.ELDERMAW) {
            circuit = pickCircuit();
            if (circuit == null) {
                return "§cNo Eldermaw circuit configured (lake-events.eldermaw.circuits).";
            }
            Waters.Water water = isle.waters().at(circuit.center());
            mawWater = water == null ? "the lake" : water.name();
            mawWaterId = water == null ? null : water.id();
        }
        kind = next;
        lastKind = next;
        phase = Phase.TELEGRAPH;
        phaseTicks = 0;
        activeTicks = 0;
        lakeTally = 0;
        breachTicks = -1;
        tally.clear();
        forgiven.clear();
        String tease = next == Kind.SILVER_RUN
                ? "§fThe water's going silver… §7a run is coming in."
                : "§5The bells toll. §7Something vast is moving under §f" + mawWater + "§7…";
        for (Player visitor : LakeWorld.visitors()) {
            visitor.sendMessage(tease);
        }
        return "§a" + next.display + " §7starting.";
    }

    public String stop(boolean quiet) {
        if (phase == Phase.IDLE) {
            return "§7No lake event running.";
        }
        Kind was = kind;
        boolean ran = phase == Phase.ACTIVE;
        boolean hauled = was == Kind.ELDERMAW && breachTicks >= 0;
        phase = Phase.IDLE;
        for (UUID id : Set.copyOf(viewers)) {
            hide(id);
        }
        viewers.clear();
        bar = null;
        if (was == Kind.ELDERMAW) {
            removeMaw(!hauled && ran);
        }
        if (!quiet && ran) {
            if (was == Kind.SILVER_RUN) {
                for (Player visitor : LakeWorld.visitors()) {
                    Integer count = tally.get(visitor.getUniqueId());
                    visitor.sendMessage("§fThe run has passed. §7The lake landed §b" + lakeTally + " §7fish"
                            + (count == null ? "." : " — §f" + count + " §7of them yours."));
                }
            } else if (!hauled) {
                for (Player visitor : LakeWorld.visitors()) {
                    Integer pulled = tally.get(visitor.getUniqueId());
                    visitor.sendMessage("§5The Eldermaw dives. §7It'll be back." + (pulled == null ? ""
                            : " §8(You heaved §f" + pulled + "§8.)"));
                    if (pulled != null) {
                        FishingSkills.bonus(visitor, 10);
                    }
                }
            }
        }
        tally.clear();
        forgiven.clear();
        return "§eLake event stopped.";
    }

    private int total() {
        return kind == Kind.SILVER_RUN ? RUN_TICKS : MAW_TICKS;
    }

    private void tick() {
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.TELEGRAPH) {
            phaseTicks++;
            if (phaseTicks % 40 == 1) {
                toll(phaseTicks / 40);
            }
            if (phaseTicks >= TELEGRAPH_TICKS) {
                activate();
            }
            return;
        }
        activeTicks++;
        if (kind == Kind.ELDERMAW) {
            swim();
            if (breachTicks >= 0) {
                breachTicks++;
                if (breachTicks >= 40) {
                    stop(false);
                }
                return;
            }
        }
        if (activeTicks >= total()) {
            stop(false);
            return;
        }
        if (activeTicks % 5 != 0) {
            return;
        }
        List<Player> visitors = LakeWorld.visitors();
        syncViewers(visitors);
        if (bar != null) {
            float progress = kind == Kind.SILVER_RUN
                    ? (float) Math.max(0.0d, 1.0d - activeTicks / (double) total())
                    : (float) Math.max(0.0d, Math.min(1.0d, hp / (double) Math.max(1, hpMax)));
            bar.progress(progress);
            bar.name(LakeText.legacy(barTitle()));
        }
        if (kind == Kind.SILVER_RUN && activeTicks % 10 == 0) {
            for (Player visitor : visitors) {
                visitor.spawnParticle(Particle.END_ROD, visitor.getLocation().add(0, 5, 0), 2, 7.0, 1.0, 7.0, 0.0);
            }
        }
    }

    /** The island's bells, one stroke per 2 s of telegraph. */
    private void toll(int stroke) {
        List<Location> bells = isle.bells();
        for (Player visitor : LakeWorld.visitors()) {
            Location nearest = null;
            double best = Double.MAX_VALUE;
            for (Location bell : bells) {
                if (bell.getWorld() != null && bell.getWorld().equals(visitor.getWorld())) {
                    double distance = bell.distanceSquared(visitor.getLocation());
                    if (distance < best) {
                        best = distance;
                        nearest = bell;
                    }
                }
            }
            Location at = nearest != null && best < 80.0d * 80.0d ? nearest : visitor.getLocation();
            visitor.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 2.0f, kind == Kind.ELDERMAW ? 0.6f : 1.0f);
            if (kind == Kind.ELDERMAW && stroke == 2) {
                visitor.playSound(visitor.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
        }
    }

    private void activate() {
        phase = Phase.ACTIVE;
        activeTicks = 0;
        String sub;
        if (kind == Kind.ELDERMAW) {
            int anglers = Math.max(1, LakeWorld.anglers().size());
            hpMax = Math.min(90, 10 + 8 * anglers);
            hp = hpMax;
            spawnMaw();
            sub = "§7Every catch on the isle heaves its line §8· §fperfect ×2 §8· §fin " + mawWater + " +1";
        } else {
            sub = "§7Lake bites ×3 faster §8· §fone miss forgiven §8· §7+" + RUN_COINS + " coins a fish";
        }
        bar = BossBar.bossBar(LakeText.legacy(barTitle()), 1.0f, kind.color, BossBar.Overlay.NOTCHED_10);
        for (Player visitor : LakeWorld.visitors()) {
            visitor.showTitle(Title.title(
                    LakeText.legacy((kind == Kind.ELDERMAW ? "§5§l" : "§f§l") + kind.display),
                    LakeText.legacy(sub),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(600))
            ));
            visitor.playSound(visitor.getLocation(), kind == Kind.ELDERMAW ? Sound.ENTITY_ELDER_GUARDIAN_CURSE
                    : Sound.ENTITY_DOLPHIN_PLAY, SoundCategory.AMBIENT, 0.8f, kind == Kind.ELDERMAW ? 0.7f : 1.2f);
        }
        syncViewers(LakeWorld.visitors());
    }

    private String barTitle() {
        long left = Math.max(0L, (total() - activeTicks) / 20L);
        if (kind == Kind.SILVER_RUN) {
            return "§fSilver Run §8· §b" + lakeTally + " §7caught §8· §fbites ×3 §8· §7" + LakeText.clock(left);
        }
        return "§5The Eldermaw §8· §f" + mawWater + " §8· §dhaul " + Math.max(0, hpMax - hp) + "§7/§d" + hpMax
                + " §8· §7" + LakeText.clock(left);
    }

    private void syncViewers(List<Player> visitors) {
        if (bar == null) {
            return;
        }
        Set<UUID> now = new HashSet<>();
        for (Player visitor : visitors) {
            now.add(visitor.getUniqueId());
            if (viewers.add(visitor.getUniqueId())) {
                QuestBars.suppress(visitor, LEASE);
                visitor.showBossBar(bar);
            }
        }
        for (UUID id : Set.copyOf(viewers)) {
            if (!now.contains(id)) {
                hide(id);
            }
        }
    }

    private void hide(UUID id) {
        viewers.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null && bar != null) {
            player.hideBossBar(bar);
        }
        QuestBars.release(id, LEASE);
    }

    void onQuit(UUID id) {
        if (viewers.contains(id)) {
            hide(id);
        }
    }

    // ------------------------------------------------------------------ the Eldermaw

    private Circuit pickCircuit() {
        World world = LakeWorld.world();
        if (world == null) {
            return null;
        }
        List<Circuit> circuits = new ArrayList<>();
        for (String raw : isle.plugin().getConfig().getStringList("lake-events.eldermaw.circuits")) {
            double[] v = LakeText.numbers(raw, 3);
            if (v != null) {
                circuits.add(new Circuit(new Location(world, v[0], LakeWorld.lakeY(), v[1]), Math.max(4.0d, v[2])));
            }
        }
        return circuits.isEmpty() ? null : circuits.get(ThreadLocalRandom.current().nextInt(circuits.size()));
    }

    private double swimY() {
        return isle.plugin().getConfig().getDouble("lake-events.eldermaw.swim-y", LakeWorld.lakeY() - 3.6d);
    }

    private Location mawAt(double theta) {
        Location center = circuit.center();
        Location at = center.clone().add(Math.cos(theta) * circuit.radius(), 0.0d, Math.sin(theta) * circuit.radius());
        at.setY(swimY());
        // Face along the circle: heading (−sin θ, cos θ) is yaw θ in Minecraft's convention.
        at.setYaw((float) Math.toDegrees(theta));
        return at;
    }

    private void spawnMaw() {
        World world = circuit.center().getWorld();
        Location center = circuit.center();
        if (world == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            return;
        }
        angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d);
        Location at = mawAt(angle);
        ElderGuardian guardian = world.spawn(at, ElderGuardian.class, CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setGlowing(true);
            spawned.setCollidable(false);
            spawned.customName(LakeText.legacy("§5§lThe Eldermaw"));
            spawned.setCustomNameVisible(false);
            spawned.getPersistentDataContainer().set(mawKey, PersistentDataType.BYTE, (byte) 1);
            spawned.getPersistentDataContainer().set(AetherKeys.FISHING_ENCOUNTER, PersistentDataType.BYTE, (byte) 1);
            try {
                AttributeInstance scale = spawned.getAttribute(Attribute.GENERIC_SCALE);
                if (scale != null) {
                    scale.setBaseValue(1.6d);
                }
            } catch (NoSuchFieldError | IllegalArgumentException ignored) {
            }
        });
        maw = guardian.getUniqueId();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 3, 0), 60, 2.5, 0.4, 2.5, 0.2);
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
    }

    /** One step round the circuit; every few seconds the back breaks the surface. */
    private void swim() {
        if (circuit == null) {
            return;
        }
        Entity entity = maw == null ? null : Bukkit.getEntity(maw);
        if (entity == null) {
            // Non-persistent: gone with its chunk. Bring it back once someone is near again.
            Location center = circuit.center();
            if (breachTicks < 0 && activeTicks % 40 == 0 && center.getWorld() != null
                    && center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
                spawnMaw();
            }
            return;
        }
        if (breachTicks >= 0) {
            Location up = entity.getLocation().add(0, breachTicks < 12 ? 0.45 : -0.35, 0);
            entity.teleport(up);
            if (breachTicks == 10) {
                World world = entity.getWorld();
                world.spawnParticle(Particle.SPLASH, up, 120, 3.0, 1.0, 3.0, 0.3);
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, up, 60, 2.5, 1.0, 2.5, 0.1);
                world.playSound(up, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 3.0f, 0.5f);
            }
            return;
        }
        angle += 0.018d;
        Location next = mawAt(angle);
        double bob = Math.sin(activeTicks / 30.0d);
        if (bob > 0.85d) {
            next.add(0, 1.4d * (bob - 0.85d) / 0.15d, 0);
            if (activeTicks % 8 == 0) {
                entity.getWorld().spawnParticle(Particle.SPLASH, next.clone().add(0, 2.4, 0), 20, 1.4, 0.1, 1.4, 0.05);
            }
        }
        entity.teleport(next);
        if (activeTicks % 20 == 0) {
            entity.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, next.clone().add(0, 1.0, 0), 10, 1.2, 0.4, 1.2, 0.05);
        }
    }

    /** Hauled: it breaches, then everyone who pulled gets paid. */
    private void breach() {
        breachTicks = 0;
        if (bar != null) {
            bar.progress(0.0f);
            bar.name(LakeText.legacy("§5§lThe Eldermaw breaches!"));
        }
        List<Map.Entry<UUID, Integer>> pulls = new ArrayList<>(tally.entrySet());
        pulls.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int top = pulls.isEmpty() ? 0 : pulls.get(0).getValue();
        for (Map.Entry<UUID, Integer> entry : pulls) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            int pulled = entry.getValue();
            int xp = Math.min(150, 30 + 6 * pulled);
            long coins = Math.min(800L, 100L + 25L * pulled);
            FishingSkills.bonus(player, xp);
            FishingSkills.coins(player, coins);
            AnglerProfiles.Profile profile = isle.profiles().of(player);
            profile.eldermaw++;
            isle.profiles().markDirty();
            boolean best = pulled == top;
            if (best) {
                InventoryDrops.give(player, isle.trophies().scale());
            }
            player.sendMessage("§5✦ The Eldermaw is hauled up! §7You heaved §f" + pulled + "§7 · §6+" + LakeText.coins(coins)
                    + " coins §8· §a+" + xp + " Fishing XP" + (best ? " §8· §5Eldermaw Scale §7(strongest arm)" : ""));
        }
        for (Player visitor : LakeWorld.visitors()) {
            visitor.showTitle(Title.title(
                    LakeText.legacy("§5§lHAULED"),
                    LakeText.legacy("§7The whole lake pulled the Eldermaw up"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2400), Duration.ofMillis(700))
            ));
            visitor.playSound(visitor.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.9f, 0.8f);
            if (!tally.containsKey(visitor.getUniqueId())) {
                visitor.sendMessage("§7The Eldermaw was hauled up. §8Land a fish during the next one to share the haul.");
            }
        }
    }

    private void removeMaw(boolean dive) {
        Entity entity = maw == null ? null : Bukkit.getEntity(maw);
        if (entity != null) {
            if (dive) {
                entity.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, entity.getLocation(), 40, 2.0, 1.0, 2.0, 0.1);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.4f);
            }
            entity.remove();
        }
        maw = null;
        circuit = null;
    }

    private void purgeMaw() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ElderGuardian.class)) {
                if (entity.getPersistentDataContainer().has(mawKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
        maw = null;
    }

    private boolean isMaw(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(mawKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (isMaw(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {
        if (isMaw(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
