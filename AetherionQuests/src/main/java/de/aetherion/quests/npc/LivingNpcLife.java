package de.aetherion.quests.npc;

import de.aetherion.core.npc.FancyNpcFacade;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.talk.TalkUx;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPC life for living FancyNPC hosts — opt-in per NPC id:
 * <ul>
 *   <li>work beats: arm swings with a job sound, hand swaps (quill, spyglass, snack),
 *       crouch / use-item poses — only while someone is around to see it</li>
 *   <li>idle glances when nobody is close enough for turn-to-player</li>
 *   <li>tiny patrols (≤ 3 blocks, ground-checked, never over air/liquid)</li>
 *   <li>talk hooks: per-player gestures, floating emotes, a small celebration</li>
 * </ul>
 * Everything pauses while the NPC is mid-conversation or on a scripted escort.
 * Config: {@code npc-life.enabled}, {@code npc-life.patrols}.
 */
public final class LivingNpcLife {

    private static LivingNpcLife instance;

    private static final double VIEW = 24.0;
    private static final double CLOSE = 5.5;
    private static final long PERIOD = 5L;

    private final AetherionQuests plugin;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long now;
    private boolean patrols;

    private static final class State {
        final String id;
        Location home;
        long nextBeat;
        long nextGlance;
        long glanceBackAt = -1;
        long restoreHandAt = -1;
        long standAt = -1;
        long stopUseAt = -1;
        // patrol
        List<double[]> route;
        int leg = -1;
        Location walkFrom;
        Location walkTo;
        double walkT;
        long patrolPauseUntil;
        long nextPatrol;
        boolean patrolBroken;

        State(String id, Location home) {
            this.id = id;
            this.home = home.clone();
        }
    }

    private LivingNpcLife(AetherionQuests plugin) {
        this.plugin = plugin;
        this.patrols = plugin.getConfig().getBoolean("npc-life.patrols", true);
    }

    public static LivingNpcLife start(AetherionQuests plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new LivingNpcLife(plugin);
        if (plugin.getConfig().getBoolean("npc-life.enabled", true)) {
            instance.task = plugin.getServer().getScheduler().runTaskTimer(plugin, instance::tick, 40L, PERIOD);
        }
        return instance;
    }

    public static LivingNpcLife get() {
        return instance;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (State s : states.values()) {
            restoreAll(s);
        }
        states.clear();
        if (instance == this) {
            instance = null;
        }
    }

    public static void onSpawned(String npcId, Location at) {
        LivingNpcLife life = instance;
        if (life == null || npcId == null || at == null) {
            return;
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        State s = life.states.get(id);
        if (s != null && s.leg >= 0) {
            // Re-placed mid-walk: drop the patrol, adopt the new home.
            life.living().setMovementLocked(id, false);
        }
        State fresh = new State(id, at);
        fresh.nextBeat = life.now + 40 + ThreadLocalRandom.current().nextInt(120);
        fresh.nextGlance = life.now + 100 + ThreadLocalRandom.current().nextInt(200);
        fresh.nextPatrol = life.now + 200 + ThreadLocalRandom.current().nextInt(400);
        fresh.route = routeFor(id);
        life.states.put(id, fresh);
    }

    public static void onRemoved(String npcId) {
        LivingNpcLife life = instance;
        if (life == null || npcId == null) {
            return;
        }
        State s = life.states.remove(npcId.toLowerCase(Locale.ROOT));
        if (s != null && s.leg >= 0) {
            life.living().setMovementLocked(s.id, false);
        }
    }

    private LivingNpcService living() {
        return plugin.getLivingNpcService();
    }

    /* =====================================================================
     * Tick
     * ===================================================================== */

    private void tick() {
        now += PERIOD;
        LivingNpcService living = living();
        if (living == null || !living.available()) {
            return;
        }
        for (State s : states.values()) {
            try {
                tickOne(living, s);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("NPC life tick failed for " + s.id + ": " + ex.getMessage());
            }
        }
    }

    private void tickOne(LivingNpcService living, State s) {
        // Timed restores always run, even if nobody is watching any more.
        if (s.restoreHandAt >= 0 && now >= s.restoreHandAt) {
            s.restoreHandAt = -1;
            restoreHand(s.id);
        }
        if (s.standAt >= 0 && now >= s.standAt) {
            s.standAt = -1;
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0 && now >= s.stopUseAt) {
            s.stopUseAt = -1;
            attribute(s.id, "use_item", "none");
        }

        Location at = living.locationOf(s.id);
        if (at == null || at.getWorld() == null) {
            return;
        }
        boolean talking = TalkUx.get() != null && TalkUx.get().isBusy(s.id);
        boolean escort = living.isMovementLocked(s.id) && s.leg < 0;

        // Patrol in progress owns the NPC.
        if (s.leg >= 0) {
            if (talking || anyone(at, 3.0)) {
                // Someone wants a word: stop where we are, walk home later.
                finishPatrol(living, s, true);
                return;
            }
            stepPatrol(living, s);
            return;
        }
        if (escort) {
            return;
        }

        List<Player> viewers = viewers(at, VIEW);
        if (viewers.isEmpty()) {
            if (s.glanceBackAt >= 0) {
                glance(living, s, 0f);
                s.glanceBackAt = -1;
            }
            return;
        }
        if (talking) {
            return;
        }
        boolean someoneClose = anyone(at, CLOSE);

        if (s.glanceBackAt >= 0 && (now >= s.glanceBackAt || someoneClose)) {
            glance(living, s, 0f);
            s.glanceBackAt = -1;
        } else if (!someoneClose && s.glanceBackAt < 0 && now >= s.nextGlance) {
            float offset = (float) ThreadLocalRandom.current().nextDouble(25.0, 65.0)
                    * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            glance(living, s, offset);
            s.glanceBackAt = now + 40 + ThreadLocalRandom.current().nextInt(60);
            s.nextGlance = now + 160 + ThreadLocalRandom.current().nextInt(260);
        }

        if (now >= s.nextBeat) {
            s.nextBeat = now + 70 + ThreadLocalRandom.current().nextInt(110);
            workBeat(s, at);
        }

        if (patrols && s.route != null && !s.patrolBroken && !someoneClose
                && now >= s.nextPatrol && s.glanceBackAt < 0) {
            s.nextPatrol = now + 600 + ThreadLocalRandom.current().nextInt(900);
            beginPatrol(living, s, at);
        }
    }

    /* =====================================================================
     * Work beats
     * ===================================================================== */

    private void workBeat(State s, Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double roll = r.nextDouble();
        Location hands = at.clone().add(0, 1.2, 0);
        World w = at.getWorld();
        switch (s.id) {
            case "egon" -> {
                if (roll < 0.35) {
                    swing(s.id);
                } else if (roll < 0.6) {
                    hold(s, Material.SHEARS, 70);
                    later(10, () -> swing(s.id));
                    later(35, () -> swing(s.id));
                } else {
                    crouch(s, 45);
                }
            }
            case "lumberjack" -> {
                if (roll < 0.7) {
                    swing(s.id);
                    later(9, () -> swing(s.id));
                    w.spawnParticle(Particle.BLOCK, hands, 6, 0.25, 0.1, 0.25, 0.0, Material.OAK_LOG.createBlockData());
                } else {
                    hold(s, Material.APPLE, 40);
                    use(s, 32);
                    sound(at, Sound.ENTITY_GENERIC_EAT, 0.35f, 1.0f);
                }
            }
            case "quartermaster" -> {
                if (roll < 0.35) {
                    hold(s, Material.SPYGLASS, 70);
                    use(s, 60);
                } else if (roll < 0.7) {
                    hold(s, Material.WRITABLE_BOOK, 60);
                    later(8, () -> swing(s.id));
                    sound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.35f, 1.1f);
                } else {
                    swing(s.id);
                    sound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.9f);
                }
            }
            case "craftsman" -> {
                if (roll < 0.75) {
                    for (int i = 0; i < 3; i++) {
                        final int k = i;
                        later(k * 8, () -> {
                            swing(s.id);
                            sound(at, Sound.BLOCK_ANVIL_USE, 0.12f, 1.5f + k * 0.08f);
                            at.getWorld().spawnParticle(Particle.CRIT, hands, 3, 0.15, 0.05, 0.15, 0.05);
                        });
                    }
                } else {
                    hold(s, Material.IRON_PICKAXE, 60);
                }
            }
            case "foreman" -> {
                if (roll < 0.6) {
                    swing(s.id);
                } else {
                    crouch(s, 50);
                }
            }
            case "booster_tutor" -> {
                if (roll < 0.8) {
                    swing(s.id);
                    sound(at, Sound.BLOCK_ANVIL_LAND, 0.1f, 1.8f);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, hands, 8, 0.2, 0.1, 0.2, 0.05);
                } else {
                    swing(s.id);
                    w.spawnParticle(Particle.SMALL_FLAME, hands, 5, 0.15, 0.05, 0.15, 0.01);
                    later(10, () -> sound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.6f));
                }
            }
            case "ledger" -> {
                if (roll < 0.5) {
                    hold(s, Material.FEATHER, 50);
                    later(6, () -> swing(s.id));
                } else {
                    crouch(s, 20);
                }
            }
            case "farmer" -> {
                if (roll < 0.65) {
                    swing(s.id);
                    sound(at, Sound.ITEM_HOE_TILL, 0.25f, 1.1f);
                    w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), 6, 0.4, 0.05, 0.4, 0.0,
                            Material.FARMLAND.createBlockData());
                } else {
                    crouch(s, 50);
                }
            }
            case "lark" -> {
                if (roll < 0.5) {
                    swing(s.id);
                    sound(at, Sound.ENTITY_PARROT_AMBIENT, 0.3f, 1.3f);
                } else {
                    crouch(s, 30);
                }
            }
            case "fisher" -> {
                if (roll < 0.55) {
                    swing(s.id);
                    sound(at, Sound.ENTITY_FISHING_BOBBER_THROW, 0.3f, 0.9f);
                    later(40, () -> sound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.2f, 1.2f));
                } else {
                    crouch(s, 40);
                }
            }
            case "fishmonger" -> {
                if (roll < 0.5) {
                    swing(s.id);
                    sound(at, Sound.ENTITY_COD_FLOP, 0.35f, 1.0f);
                } else {
                    Material[] fish = {Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};
                    hold(s, fish[r.nextInt(fish.length)], 60);
                    later(6, () -> swing(s.id));
                }
            }
            case "vex" -> {
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    later(k * 7, () -> {
                        swing(s.id);
                        sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.18f, 1.2f + k * 0.1f);
                    });
                }
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, hands.clone().add(0, -0.2, 0), 1, 0.3, 0.0, 0.3, 0.0);
            }
            case "rite_keeper" -> {
                if (roll < 0.5) {
                    use(s, 30);
                    w.spawnParticle(Particle.SOUL, hands, 3, 0.2, 0.2, 0.2, 0.01);
                } else {
                    crouch(s, 40);
                    w.spawnParticle(Particle.SMOKE, at.clone().add(0, 0.2, 0), 8, 0.4, 0.05, 0.4, 0.01);
                }
            }
            case "arena_proctor" -> {
                if (roll < 0.6) {
                    hold(s, Material.WRITABLE_BOOK, 60);
                    later(8, () -> swing(s.id));
                    sound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.2f);
                } else {
                    crouch(s, 25);
                }
            }
            case "farm_isle_guide" -> {
                if (roll < 0.5) {
                    crouch(s, 45);
                    w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), 5, 0.3, 0.05, 0.3, 0.0,
                            Material.DIRT.createBlockData());
                } else {
                    swing(s.id);
                }
            }
            case "surveyor" -> {
                if (roll < 0.5) {
                    hold(s, Material.SPYGLASS, 70);
                    use(s, 60);
                } else {
                    hold(s, Material.COMPASS, 60);
                    later(6, () -> swing(s.id));
                }
            }
            case "vince" -> {
                hold(s, Material.GOLD_NUGGET, 40);
                later(4, () -> {
                    swing(s.id);
                    sound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 0.6f);
                });
            }
            case "bar_whisper" -> {
                swing(s.id);
                w.spawnParticle(Particle.ENCHANT, hands.clone().add(0, 0.5, 0), 12, 0.3, 0.4, 0.3, 0.5);
            }
            case "eldervale_welcome" -> {
                if (roll < 0.5) {
                    crouch(s, 40);
                } else {
                    swing(s.id);
                }
            }
            case "eldervale_upgrade" -> {
                for (int i = 0; i < 2; i++) {
                    final int k = i;
                    later(k * 10, () -> {
                        swing(s.id);
                        sound(at, Sound.BLOCK_ANVIL_USE, 0.14f, 0.8f + k * 0.1f);
                        at.getWorld().spawnParticle(Particle.LAVA, hands, 1, 0.1, 0.05, 0.1, 0.0);
                    });
                }
            }
            case "merchant" -> {
                if (roll < 0.5) {
                    swing(s.id);
                    sound(at, Sound.ENTITY_VILLAGER_TRADE, 0.2f, 1.3f);
                } else {
                    hold(s, Material.CHEST, 60);
                }
            }
            case "isle_clerk" -> {
                hold(s, Material.PAPER, 50);
                later(8, () -> swing(s.id));
            }
            case "forage_pad_guide" -> {
                crouch(s, 8);
                later(10, () -> {
                    sound(at, Sound.BLOCK_SLIME_BLOCK_STEP, 0.4f, 1.2f);
                    at.getWorld().spawnParticle(Particle.ITEM_SLIME, at.clone().add(0, 0.1, 0), 6, 0.3, 0.05, 0.3, 0.0);
                });
                later(22, () -> crouch(s, 8));
            }
            case "liquidator" -> {
                swing(s.id);
                sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.0f + (float) r.nextDouble(0.3));
            }
            case "canopy_clerk" -> {
                Material[] logs = {Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG};
                hold(s, logs[r.nextInt(logs.length)], 60);
                if (roll < 0.5) {
                    crouch(s, 30);
                }
            }
            case "root_cellar" -> {
                if (roll < 0.6) {
                    swing(s.id);
                    sound(at, Sound.BLOCK_GRINDSTONE_USE, 0.12f, 1.4f);
                    w.spawnParticle(Particle.WHITE_ASH, hands, 8, 0.3, 0.1, 0.3, 0.0);
                } else {
                    hold(s, Material.BREAD, 50);
                }
            }
            case "town_crier" -> {
                swing(s.id);
                sound(at, Sound.BLOCK_BELL_USE, 0.35f, 1.4f);
            }
            case "street_sweeper" -> {
                swing(s.id);
                later(8, () -> swing(s.id));
                sound(at, Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.35f, 1.0f);
                w.spawnParticle(Particle.BLOCK, at.clone().add(0.4, 0.1, 0), 8, 0.4, 0.05, 0.4, 0.0,
                        Material.GRAVEL.createBlockData());
            }
            case "lamp_lighter" -> {
                long time = w.getTime();
                if (time > 12000L && time < 23000L) {
                    hold(s, Material.TORCH, 60);
                    later(8, () -> swing(s.id));
                    sound(at, Sound.ITEM_FIRECHARGE_USE, 0.15f, 1.6f);
                    w.spawnParticle(Particle.FLAME, hands.clone().add(0, 0.6, 0), 3, 0.05, 0.1, 0.05, 0.01);
                } else {
                    crouch(s, 30);
                }
            }
            default -> {
            }
        }
    }

    /* =====================================================================
     * Patrols — only for NPCs with a hand-picked, short route
     * ===================================================================== */

    /** Route offsets (dx, dz) from home. Kept tiny on purpose. */
    private static List<double[]> routeFor(String id) {
        return switch (id) {
            case "egon" -> List.of(new double[] {1.6, 0.0}, new double[] {-1.6, 0.0});
            case "vex" -> List.of(new double[] {2.5, 0.0}, new double[] {-2.5, 0.0});
            case "farmer" -> List.of(new double[] {2.0, 1.0}, new double[] {-1.0, -1.5});
            case "fishmonger" -> List.of(new double[] {0.0, 1.4});
            case "merchant" -> List.of(new double[] {1.2, 0.0});
            case "arena_proctor" -> List.of(new double[] {1.5, 1.0});
            case "street_sweeper" -> List.of(new double[] {2.5, 0.0}, new double[] {0.0, 2.5}, new double[] {-2.0, -1.0});
            case "town_crier" -> List.of(new double[] {1.5, 0.0}, new double[] {-1.5, 0.0});
            default -> null;
        };
    }

    private void beginPatrol(LivingNpcService living, State s, Location at) {
        if (!loaded(s.home)) {
            return;
        }
        double[] off = s.route.get(ThreadLocalRandom.current().nextInt(s.route.size()));
        Location target = s.home.clone().add(off[0], 0, off[1]);
        Location ground = LivingNpcService.plantFeet(target, s.home.getY());
        if (!safeGround(ground, s.home)) {
            s.patrolBroken = true;
            plugin.getLogger().fine("Patrol disabled for " + s.id + " (unsafe ground)");
            return;
        }
        living.setMovementLocked(s.id, true);
        s.leg = 0;
        s.walkFrom = at.clone();
        s.walkTo = ground;
        s.walkT = 0;
    }

    private void stepPatrol(LivingNpcService living, State s) {
        if (s.walkTo == null || !loaded(s.walkTo) || !loaded(s.home)) {
            // Players left and the chunk went to sleep: no block reads, straight home.
            finishPatrol(living, s, true);
            return;
        }
        if (now < s.patrolPauseUntil) {
            return;
        }
        double dist = s.walkFrom.distance(s.walkTo);
        if (dist < 0.05) {
            arrive(living, s);
            return;
        }
        s.walkT = Math.min(1.0, s.walkT + (0.09 * PERIOD) / dist);
        Location next = s.walkFrom.clone().add(s.walkTo.clone().subtract(s.walkFrom).toVector().multiply(s.walkT));
        Location feet = LivingNpcService.plantFeet(next, s.walkFrom.getY());
        if (!safeGround(feet, s.home)) {
            finishPatrol(living, s, true);
            s.patrolBroken = true;
            return;
        }
        feet.setYaw(yaw(s.walkFrom, s.walkTo));
        living.moveTo(s.id, feet);
        living.followName(s.id, feet);
        if (s.walkT >= 1.0) {
            arrive(living, s);
        }
    }

    private void arrive(LivingNpcService living, State s) {
        if (s.leg == 0) {
            // at the far point: a beat of "work", then back home
            s.leg = 1;
            s.patrolPauseUntil = now + 40 + ThreadLocalRandom.current().nextInt(60);
            Location at = living.locationOf(s.id);
            if (at != null) {
                workBeat(s, at);
            }
            s.walkFrom = s.walkTo.clone();
            s.walkTo = s.home.clone();
            s.walkT = 0;
            return;
        }
        finishPatrol(living, s, false);
    }

    private void finishPatrol(LivingNpcService living, State s, boolean snapHome) {
        Location home = s.home.clone();
        if (snapHome) {
            living.moveTo(s.id, home);
            living.followName(s.id, home);
        } else {
            living.moveTo(s.id, home);
        }
        s.leg = -1;
        s.walkFrom = null;
        s.walkTo = null;
        living.setMovementLocked(s.id, false);
        AetherionQuests p = AetherionQuests.getInstance();
        if (p != null && p.getMarkerManager() != null) {
            p.getMarkerManager().refreshNpc(s.id);
        }
    }

    private static boolean loaded(Location at) {
        return at != null && at.getWorld() != null
                && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    private static boolean safeGround(Location feet, Location home) {
        if (feet == null || feet.getWorld() == null || home == null) {
            return false;
        }
        if (Math.abs(feet.getY() - home.getY()) > 1.05) {
            return false;
        }
        Block below = feet.clone().add(0, -0.5, 0).getBlock();
        Block at = feet.getBlock();
        Block head = feet.clone().add(0, 1, 0).getBlock();
        return below.getType().isSolid()
                && !at.isLiquid() && !at.getType().isSolid()
                && !head.getType().isSolid()
                && below.getType() != Material.MAGMA_BLOCK && below.getType() != Material.CAMPFIRE;
    }

    private static float yaw(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /* =====================================================================
     * Talk hooks (per player)
     * ===================================================================== */

    /** Body language for a spoken line: first line and exclamations get an arm gesture. */
    public void gesture(Player player, String npcId, String line) {
        if (player == null || npcId == null) {
            return;
        }
        boolean loud = line != null && (line.contains("!") || line.contains("?"));
        if (loud || ThreadLocalRandom.current().nextDouble() < 0.3) {
            swingFor(player, npcId);
        }
    }

    /** Small symbol that floats up from the NPC's head (only this player sees it). */
    public void emote(Player player, String npcId, String symbol) {
        if (player == null || npcId == null || symbol == null) {
            return;
        }
        Location at = NpcPresence.locate(npcId);
        if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) {
            return;
        }
        Location start = at.clone().add(0.35, 2.05, 0);
        TextDisplay display;
        try {
            display = at.getWorld().spawn(start, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.addScoreboardTag(TalkUx.TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(20);
                d.text(de.aetherion.quests.talk.TalkGlyphs.emote(symbol));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            });
        } catch (RuntimeException ex) {
            return;
        }
        player.showEntity(plugin, display);
        later(2, () -> {
            if (display.isValid()) {
                display.teleport(start.clone().add(0, 0.4, 0));
            }
        });
        later(26, () -> {
            if (display.isValid()) {
                display.remove();
            }
        });
    }

    /** Quest handed in: heart + sparkle burst for this player. */
    public void celebrate(Player player, String npcId) {
        emote(player, npcId, "♥");
        Location at = NpcPresence.locate(npcId);
        if (at == null || player == null || !at.getWorld().equals(player.getWorld())) {
            return;
        }
        player.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0), 14, 0.45, 0.6, 0.45, 0.0);
        swingFor(player, npcId);
    }

    /* =====================================================================
     * FancyNpcs glue (reflection via Core facade)
     * ===================================================================== */

    /** Arm swing seen by everyone watching the NPC. */
    public void swing(String npcId) {
        Object npc = living().fancy(npcId);
        if (npc != null) {
            FancyNpcFacade.updateForAll(npc, true);
        }
    }

    /** Arm swing only this player sees (conversation gestures). */
    public void swingFor(Player player, String npcId) {
        Object npc = living().fancy(npcId);
        if (npc == null || player == null) {
            return;
        }
        try {
            npc.getClass().getMethod("update", Player.class, boolean.class).invoke(npc, player, true);
        } catch (ReflectiveOperationException ex) {
            FancyNpcFacade.updateForAll(npc, true);
        }
    }

    private void hold(State s, Material item, int ticks) {
        living().setMainHand(s.id, new ItemStack(item));
        s.restoreHandAt = now + ticks;
    }

    private void restoreHand(String npcId) {
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        living().setMainHand(npcId, profile == null ? null : profile.handItem());
    }

    private void crouch(State s, int ticks) {
        if (attribute(s.id, "pose", "crouching")) {
            s.standAt = now + ticks;
        }
    }

    private void use(State s, int ticks) {
        if (attribute(s.id, "use_item", "main_hand")) {
            s.stopUseAt = now + ticks;
        }
    }

    private void glance(LivingNpcService living, State s, float yawOffset) {
        Location at = living.locationOf(s.id);
        if (at == null) {
            return;
        }
        Location look = at.clone();
        look.setYaw(s.home.getYaw() + yawOffset);
        look.setPitch(yawOffset == 0f ? s.home.getPitch() : (float) ThreadLocalRandom.current().nextDouble(-8, 12));
        living.moveTo(s.id, look);
    }

    private void restoreAll(State s) {
        if (s.restoreHandAt >= 0) {
            restoreHand(s.id);
        }
        if (s.standAt >= 0) {
            attribute(s.id, "pose", "standing");
        }
        if (s.stopUseAt >= 0) {
            attribute(s.id, "use_item", "none");
        }
        if (s.leg >= 0) {
            LivingNpcService living = living();
            if (living != null) {
                living.moveTo(s.id, s.home);
                living.setMovementLocked(s.id, false);
            }
        }
    }

    /** Set a FancyNpcs attribute (pose / use_item) and push it to viewers. */
    private boolean attribute(String npcId, String name, String value) {
        Object npc = living().fancy(npcId);
        if (npc == null) {
            return false;
        }
        try {
            Object api = FancyNpcFacade.plugin();
            Object attributes = api.getClass().getMethod("getAttributeManager").invoke(api);
            Object attr = attributes.getClass()
                    .getMethod("getAttributeByName", EntityType.class, String.class)
                    .invoke(attributes, EntityType.PLAYER, name);
            if (attr == null) {
                return false;
            }
            Object data = FancyNpcFacade.data(npc);
            Class<?> attrClass = Class.forName("de.oliver.fancynpcs.api.NpcAttribute");
            data.getClass().getMethod("addAttribute", attrClass, String.class).invoke(data, attr, value);
            attr.getClass().getMethod("apply", FancyNpcFacade.npcClass(), String.class).invoke(attr, npc, value);
            FancyNpcFacade.updateForAll(npc, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private void sound(Location at, Sound sound, float volume, float pitch) {
        if (at != null && at.getWorld() != null) {
            at.getWorld().playSound(at, sound, SoundCategory.NEUTRAL, volume, pitch);
        }
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(1L, ticks));
    }

    private static List<Player> viewers(Location at, double radius) {
        List<Player> out = new ArrayList<>(2);
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean anyone(Location at, double radius) {
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= r2) {
                return true;
            }
        }
        return false;
    }
}
