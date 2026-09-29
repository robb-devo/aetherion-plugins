package de.aetherion.foraging.isle;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Isle-wide events on a timer while foragers are on Foraging Eldervale. All four telegraph first,
 * share one boss bar, and end with a personal tally.
 * <ul>
 *   <li><b>Golden Sap</b> (the isle's hotspot, {@code hotspots.enabled}) — one district runs rich:
 *   wood cap ×1.5, heartwood ×2 and bonus XP there. The bar names the district; {@code /grove go} points.</li>
 *   <li><b>Windfall</b> — a gale knocks limbs down around every forager. Strike a windfall log three
 *   times for its wood (outside any cap). The crew works towards one shared tally; make it and
 *   everyone who helped gets paid and a Tailwind (Haste, Crown Finds ×2 for a minute).</li>
 *   <li><b>Blossom Storm</b> — petals on the wind: Crown Finds ×3 and better grades, isle-wide.</li>
 *   <li><b>Bark Blight</b> — bark beetles swarm the trunks near foragers. Squash them; clear the
 *   blight together for pay, Standing and a Tailwind.</li>
 * </ul>
 */
public final class GroveEvents implements Listener {

    public enum Kind {
        GOLDEN_SAP("§6✦ Golden Sap", 150, BossBar.Color.YELLOW),
        WINDFALL("§f≋ Windfall", 120, BossBar.Color.WHITE),
        BLOSSOM_STORM("§d❀ Blossom Storm", 120, BossBar.Color.PINK),
        BARK_BLIGHT("§c✸ Bark Blight", 150, BossBar.Color.RED);

        public final String title;
        public final int seconds;
        final BossBar.Color color;

        Kind(String title, int seconds, BossBar.Color color) {
            this.title = title;
            this.seconds = seconds;
            this.color = color;
        }
    }

    private static final String LOG_TAG = "ae_grove_windfall";
    private static final long TAILWIND_MS = 60_000L;

    private final ForageIsle isle;
    private final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1.0f, BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> contributed = new HashMap<>();
    private final List<WindLog> logs = new ArrayList<>();
    private final Map<UUID, WindLog> logByPart = new HashMap<>();

    private Kind active;
    private Kind warming;
    private Grove hotspot;
    private long startsAt;
    private long endsAt;
    private long nextAt;
    private int goal;
    private int tally;
    private int tick;

    private static final class WindLog {
        BlockDisplay body;
        Interaction hitbox;
        Material wood;
        int hits;
        long expiresAt;
    }

    GroveEvents(ForageIsle isle) {
        this.isle = isle;
        scheduleNext();
    }

    // ------------------------------------------------------------------ queries used by the loops

    public Kind active() {
        return active;
    }

    public Grove hotspot() {
        return active == Kind.GOLDEN_SAP ? hotspot : null;
    }

    public double woodCapFactor(Grove grove) {
        return active == Kind.GOLDEN_SAP && grove != null && grove == hotspot ? 1.5d : 1.0d;
    }

    public double heartwoodFactor(Grove grove) {
        return active == Kind.GOLDEN_SAP && grove != null && grove == hotspot ? 2.0d : 1.0d;
    }

    public double findMultiplier(Player player) {
        double mult = active == Kind.BLOSSOM_STORM ? 3.0d : 1.0d;
        ForageProfile profile = isle.profiles().peek(player.getUniqueId());
        if (profile != null && profile.tailwindUntil > System.currentTimeMillis()) {
            mult *= 2.0d;
        }
        return mult;
    }

    public double gradeLuck() {
        return active == Kind.BLOSSOM_STORM ? 1.0d : 0.0d;
    }

    public int bonusXp(Grove grove) {
        return active == Kind.GOLDEN_SAP && grove != null && grove == hotspot ? 8 : 0;
    }

    // ------------------------------------------------------------------ lifecycle

    private void scheduleNext() {
        var yaml = isle.config().yaml();
        int min = Math.max(60, yaml.getInt("events.interval-min-seconds", 420));
        int max = Math.max(min + 1, yaml.getInt("events.interval-max-seconds", 660));
        nextAt = System.currentTimeMillis() + 1000L * (min + ThreadLocalRandom.current().nextInt(max - min));
    }

    /** Every second. */
    void tick() {
        tick++;
        long now = System.currentTimeMillis();
        List<Player> onIsle = isle.playersOnIsle();
        syncViewers(onIsle);
        if (active == null && warming == null) {
            if (!isle.config().yaml().getBoolean("events.enabled", true) || onIsle.isEmpty()) {
                if (onIsle.isEmpty()) {
                    nextAt = Math.max(nextAt, now + 60_000L);
                }
                return;
            }
            if (now >= nextAt) {
                warm(pick(onIsle), onIsle);
            }
            return;
        }
        if (warming != null) {
            long left = startsAt - now;
            bar.name(ForageText.legacy(warming.title + " §7in §f" + Math.max(1, (left + 999) / 1000) + "s"
                    + (warming == Kind.GOLDEN_SAP && hotspot != null ? " §8· " + hotspot.colored() : "")));
            bar.progress(1.0f - (float) Math.max(0, Math.min(1.0, left / (1000.0d * warmupSeconds()))));
            if (left <= 0) {
                begin(onIsle);
            }
            return;
        }
        long left = endsAt - now;
        if (left <= 0) {
            end(false);
            return;
        }
        bar.progress((float) Math.max(0.0d, Math.min(1.0d, left / (active.seconds * 1000.0d))));
        bar.name(ForageText.legacy(barName(left)));
        switch (active) {
            case WINDFALL -> tickWindfall(onIsle, now);
            case BLOSSOM_STORM -> {
                for (Player player : onIsle) {
                    player.spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 3, 0), 6, 4, 1.5, 4, 0.0);
                }
            }
            case BARK_BLIGHT -> {
                if (tick % 6 == 0) {
                    for (Player player : onIsle) {
                        isle.critters().spawnBeetle(player);
                    }
                }
            }
            default -> {
            }
        }
    }

    private int warmupSeconds() {
        return Math.max(5, isle.config().yaml().getInt("events.warmup-seconds", 20));
    }

    private String barName(long leftMs) {
        String clock = ForageText.clock((leftMs + 999) / 1000);
        return switch (active) {
            case GOLDEN_SAP -> active.title + " §8· " + (hotspot == null ? "" : hotspot.colored()) + " §7· wood ×1.5 · heartwood ×2 §8· §f" + clock;
            case WINDFALL -> active.title + " §8· §7crew §f" + tally + "§8/§f" + goal + " §8· §f" + clock;
            case BLOSSOM_STORM -> active.title + " §8· §7Crown Finds ×3 §8· §f" + clock;
            case BARK_BLIGHT -> active.title + " §8· §7beetles §f" + tally + "§8/§f" + goal + " §8· §f" + clock;
        };
    }

    private Kind pick(List<Player> onIsle) {
        List<Kind> pool = new ArrayList<>(List.of(Kind.WINDFALL, Kind.BLOSSOM_STORM, Kind.BARK_BLIGHT));
        if (isle.plugin().getConfig().getBoolean("hotspots.enabled", true)) {
            pool.add(Kind.GOLDEN_SAP);
            pool.add(Kind.GOLDEN_SAP);
        }
        if (!isle.config().yaml().getBoolean("critters.enabled", true)) {
            pool.remove(Kind.BARK_BLIGHT);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** DEV / command: start now (skips most of the warm-up). */
    public boolean start(Kind kind) {
        if (active != null || warming != null) {
            end(true);
        }
        List<Player> onIsle = isle.playersOnIsle();
        warm(kind, onIsle);
        startsAt = System.currentTimeMillis() + 3000L;
        return true;
    }

    private void warm(Kind kind, List<Player> onIsle) {
        warming = kind;
        startsAt = System.currentTimeMillis() + warmupSeconds() * 1000L;
        hotspot = null;
        if (kind == Kind.GOLDEN_SAP) {
            // Prefer a district someone is actually in, so the event lands where people are.
            List<Grove> candidates = new ArrayList<>();
            for (Player player : onIsle) {
                Grove grove = isle.grove(player.getLocation());
                if (grove != null) {
                    candidates.add(grove);
                }
            }
            Grove[] all = Grove.values();
            hotspot = !candidates.isEmpty() && ThreadLocalRandom.current().nextInt(3) > 0
                    ? candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()))
                    : all[ThreadLocalRandom.current().nextInt(all.length)];
        }
        bar.color(kind.color);
        for (Player player : onIsle) {
            player.sendMessage(kind.title + " §7is coming" + (hotspot != null ? " §8· " + hotspot.colored() : "") + "§7…");
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 0.6f, 1.2f);
        }
    }

    private void begin(List<Player> onIsle) {
        active = warming;
        warming = null;
        endsAt = System.currentTimeMillis() + active.seconds * 1000L;
        tally = 0;
        contributed.clear();
        goal = switch (active) {
            case WINDFALL -> Math.max(12, 10 * onIsle.size());
            case BARK_BLIGHT -> Math.max(10, 8 * onIsle.size());
            default -> 0;
        };
        for (Player player : onIsle) {
            ForageText.card(player, active.title, switch (active) {
                case GOLDEN_SAP -> "§7" + (hotspot == null ? "" : hotspot.display()) + " runs rich";
                case WINDFALL -> "§7Strike the fallen limbs · crew goal §f" + goal;
                case BLOSSOM_STORM -> "§7Crown Finds ×3 across the isle";
                case BARK_BLIGHT -> "§7Squash the beetles · crew goal §f" + goal;
            }, 40);
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 0.8f, 0.9f);
        }
    }

    /** Ends the event (early when {@code silent}); pays crew goals. */
    public void end(boolean silent) {
        Kind was = active;
        active = null;
        warming = null;
        clearLogs();
        if (was != null && !silent) {
            boolean crew = was == Kind.WINDFALL || was == Kind.BARK_BLIGHT;
            boolean made = crew && tally >= goal;
            for (Player player : isle.playersOnIsle()) {
                int mine = contributed.getOrDefault(player.getUniqueId(), 0);
                if (!crew) {
                    player.sendMessage(was.title + " §7has passed.");
                    continue;
                }
                if (made && mine > 0) {
                    ForageProfile profile = isle.profiles().get(player);
                    long coins = was == Kind.WINDFALL ? 400L : 500L;
                    ForageBridge.coins(player, coins + 20L * mine);
                    ForageBridge.bonus(player, 40 + 4 * mine);
                    isle.standing().add(player, profile, 1, "event");
                    profile.tailwindUntil = System.currentTimeMillis() + TAILWIND_MS;
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 60, 0, false, false, true));
                    player.sendMessage(was.title + " §acleared! §8· §7you: §f" + mine + " §8· §6+" + ForageText.coins(coins + 20L * mine)
                            + " coins §8· §fTailwind §7(Crown Finds ×2, 1:00)");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
                } else if (made) {
                    player.sendMessage(was.title + " §acleared §7by the crew. §8(Join in next time for a share.)");
                } else {
                    player.sendMessage(was.title + " §7is over §8· §7crew §f" + tally + "§8/§f" + goal + " §8— §7not this time.");
                }
            }
        }
        contributed.clear();
        hotspot = null;
        scheduleNext();
        for (UUID id : new HashSet<>(viewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
        }
        viewers.clear();
    }

    private void syncViewers(List<Player> onIsle) {
        boolean show = active != null || warming != null;
        Set<UUID> want = new HashSet<>();
        if (show) {
            for (Player player : onIsle) {
                want.add(player.getUniqueId());
            }
        }
        for (UUID id : new HashSet<>(viewers)) {
            if (!want.contains(id)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.hideBossBar(bar);
                }
                viewers.remove(id);
            }
        }
        for (Player player : onIsle) {
            if (want.contains(player.getUniqueId()) && viewers.add(player.getUniqueId())) {
                player.showBossBar(bar);
            }
        }
    }

    /** Beetle squashed during Bark Blight. */
    void countBeetle(Player player) {
        if (active == Kind.BARK_BLIGHT) {
            tally++;
            contributed.merge(player.getUniqueId(), 1, Integer::sum);
        }
    }

    // ------------------------------------------------------------------ windfall logs

    private void tickWindfall(List<Player> onIsle, long now) {
        Iterator<WindLog> it = logs.iterator();
        while (it.hasNext()) {
            WindLog log = it.next();
            if (now >= log.expiresAt || !log.body.isValid()) {
                removeLog(log);
                it.remove();
            }
        }
        if (tick % 5 != 0 || logs.size() > 24) {
            return;
        }
        int served = 0;
        for (Player player : onIsle) {
            if (served++ >= 8) {
                break;
            }
            Location spot = groundNear(player.getLocation(), 4, 9);
            if (spot == null) {
                continue;
            }
            Grove grove = isle.grove(spot);
            Material wood = grove == null ? Material.OAK_LOG : grove.mainWood();
            spawnLog(spot, wood, now);
        }
    }

    private void spawnLog(Location spot, Material wood, long now) {
        World world = spot.getWorld();
        BlockData data = wood.createBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(org.bukkit.Axis.X);
        }
        float yaw = ThreadLocalRandom.current().nextFloat() * 6.28f;
        WindLog log = new WindLog();
        log.wood = wood;
        log.expiresAt = now + 25_000L;
        Location body = spot.clone().add(0, 3.5, 0);
        log.body = world.spawn(body, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(new Transformation(new Vector3f(-1.2f, 0f, -0.35f), new Quaternionf().rotateY(yaw),
                    new Vector3f(2.4f, 0.7f, 0.7f), new Quaternionf()));
            d.setTeleportDuration(6);
            d.setPersistent(false);
            d.addScoreboardTag(LOG_TAG);
        });
        log.hitbox = world.spawn(spot, Interaction.class, i -> {
            i.setInteractionWidth(1.6f);
            i.setInteractionHeight(0.9f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(LOG_TAG);
        });
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (log.body.isValid()) {
                log.body.teleport(spot);
                world.playSound(spot, Sound.BLOCK_WOOD_FALL, SoundCategory.BLOCKS, 1.0f, 0.6f);
                world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.3, 0), 12, 0.8, 0.1, 0.8, 0.05, data);
            }
        }, 1L);
        logs.add(log);
        logByPart.put(log.body.getUniqueId(), log);
        logByPart.put(log.hitbox.getUniqueId(), log);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStrike(EntityDamageByEntityEvent event) {
        WindLog log = logByPart.get(event.getEntity().getUniqueId());
        if (log == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        log.hits++;
        Location at = log.hitbox.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1.0f, 0.8f + log.hits * 0.15f);
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.4, 0), 8, 0.4, 0.2, 0.4, 0.05, log.wood.createBlockData());
        if (log.hits < 3) {
            return;
        }
        int amount = 3 + ThreadLocalRandom.current().nextInt(3);
        ForageBridge.give(player, new ItemStack(log.wood, amount), at);
        ForageBridge.bonus(player, 5);
        tally++;
        contributed.merge(player.getUniqueId(), 1, Integer::sum);
        ForageText.bar(player, "§f≋ Windfall §8· §f+" + amount + " logs §8· §7crew §f" + tally + "§8/§f" + goal);
        removeLog(log);
        logs.remove(log);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        if (logByPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void removeLog(WindLog log) {
        if (log.body != null) {
            logByPart.remove(log.body.getUniqueId());
            log.body.remove();
        }
        if (log.hitbox != null) {
            logByPart.remove(log.hitbox.getUniqueId());
            log.hitbox.remove();
        }
    }

    private void clearLogs() {
        for (WindLog log : logs) {
            removeLog(log);
        }
        logs.clear();
        logByPart.clear();
    }

    static boolean isOurs(Entity entity) {
        return entity.getScoreboardTags().contains(LOG_TAG);
    }

    /** A standable spot {@code min}–{@code max} blocks from {@code near}, or null. */
    static Location groundNear(Location near, int min, int max) {
        World world = near.getWorld();
        if (world == null) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2.0d;
            double dist = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(near.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(near.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int baseY = near.getBlockY();
            for (int dy = 3; dy >= -4; dy--) {
                Block feet = world.getBlockAt(x, baseY + dy, z);
                Block below = feet.getRelative(0, -1, 0);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && below.getType().isSolid()
                        && !below.getType().name().endsWith("_LEAVES") && !feet.isLiquid()) {
                    return feet.getLocation().add(0.5, 0.0, 0.5);
                }
            }
        }
        return null;
    }

    void shutdown() {
        clearLogs();
        for (UUID id : viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
        }
        viewers.clear();
    }

    public String status() {
        long now = System.currentTimeMillis();
        if (active != null) {
            return active.title + " §7· §f" + ForageText.clock((endsAt - now) / 1000L) + " left";
        }
        if (warming != null) {
            return warming.title + " §7warming up";
        }
        return "§7next in ~§f" + ForageText.clock(Math.max(0L, nextAt - now) / 1000L);
    }
}
