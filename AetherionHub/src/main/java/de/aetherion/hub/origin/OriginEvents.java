package de.aetherion.hub.origin;

import com.destroystokyo.paper.ParticleBuilder;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Island-wide moments. Every {@code events.interval-minutes} (while anyone is on Origin) the island does something
 * that fits the hour: a <b>Lantern Festival</b> over Fountain Square at dusk, the <b>Aurora</b> or a <b>Starfall</b>
 * at night (sneak and look up to make a wish), a <b>Petal Storm</b> or <b>Harbour Fog</b> in the morning, and a
 * <b>Rainbow</b> whenever the rain stops by day. Cosmetic only: particles, sounds, non-persistent displays and
 * harmless tagged fireworks. The per-player particle setting ({@code /origin settings}) turns them off.
 */
public final class OriginEvents implements Listener {

    public enum Kind {
        LANTERN_FESTIVAL("§6§lLantern Festival", "§7Lanterns rise over Fountain Square", 20 * 90),
        AURORA("§a§lAurora", "§7Look north — the sky is moving", 20 * 120),
        STARFALL("§d§lStarfall", "§7Sneak and look up to make a wish", 20 * 90),
        PETAL_STORM("§d§lPetal Storm", "§7The blossoms let go all at once", 20 * 60),
        HARBOUR_FOG("§7§lHarbour Fog", "§7The bay disappears for a while", 20 * 120),
        RAINBOW("§e§lRainbow", "§7After the rain", 20 * 60);

        private final String title;
        private final String line;
        private final int ticks;

        Kind(String title, String line, int ticks) {
            this.title = title;
            this.line = line;
            this.ticks = ticks;
        }

        public String title() {
            return title;
        }

        public String line() {
            return line;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Kind byId(String raw) {
            if (raw == null) {
                return null;
            }
            String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (Kind kind : values()) {
                if (kind.name().equals(key) || kind.name().replace("_", "").equals(key.replace("_", ""))) {
                    return kind;
                }
            }
            return null;
        }
    }

    private static final int MAX_LANTERNS = 36;
    private static final Color[] RAINBOW = {
            Color.fromRGB(0xE8453C), Color.fromRGB(0xF39C3D), Color.fromRGB(0xF7E15A),
            Color.fromRGB(0x5BD66B), Color.fromRGB(0x4D8CF0), Color.fromRGB(0x9C63E8)
    };

    private final OriginIsle isle;
    private final NamespacedKey key;
    private final List<UUID> lanterns = new ArrayList<>();
    private final List<long[]> lanternDeaths = new ArrayList<>();
    private Kind active;
    private long eventId;
    private int left;
    private int step;
    private long nextAt = -1L;
    private long lastTick;

    OriginEvents(OriginIsle isle) {
        this.isle = isle;
        this.key = new NamespacedKey(isle.plugin(), "origin_event");
        Bukkit.getScheduler().runTaskLater(isle.plugin(), this::purgeStrays, 20L);
    }

    // ------------------------------------------------------------------ schedule

    private boolean enabled() {
        return isle.config().raw().getBoolean("events.enabled", true);
    }

    private long intervalTicks() {
        return Math.max(2L, isle.config().raw().getLong("events.interval-minutes", 22L)) * 60L * 20L;
    }

    /** Every 2 ticks from the Origin loop. */
    void tick(long tick) {
        lastTick = tick;
        reapLanterns(tick);
        if (active != null) {
            List<Player> audience = audience();
            render(active, audience, tick);
            left -= 2;
            step++;
            if (left <= 0) {
                finish();
            }
            return;
        }
        if (!enabled()) {
            return;
        }
        if (nextAt < 0L) {
            nextAt = tick + intervalTicks() / 2L;
            return;
        }
        if (tick < nextAt) {
            return;
        }
        nextAt = tick + intervalTicks();
        if (isle.onIsle().isEmpty()) {
            return;
        }
        Kind kind = pick();
        if (kind != null) {
            begin(kind);
        }
    }

    private Kind pick() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return switch (isle.phase()) {
            case DAWN -> rng.nextBoolean() ? Kind.HARBOUR_FOG : Kind.PETAL_STORM;
            case DAY -> rng.nextInt(4) == 0 ? Kind.HARBOUR_FOG : Kind.PETAL_STORM;
            case DUSK -> Kind.LANTERN_FESTIVAL;
            case NIGHT -> switch (rng.nextInt(3)) {
                case 0 -> Kind.AURORA;
                case 1 -> Kind.STARFALL;
                default -> Kind.LANTERN_FESTIVAL;
            };
        };
    }

    public String force(String raw) {
        Kind kind = Kind.byId(raw);
        if (kind == null) {
            return "§cUnknown moment. §7Try: lantern-festival, aurora, starfall, petal-storm, harbour-fog, rainbow.";
        }
        if (active != null) {
            finish();
        }
        begin(kind);
        return "§aStarted §f" + kind.id() + "§a (" + (kind.ticks / 20) + "s).";
    }

    private void begin(Kind kind) {
        active = kind;
        eventId = System.currentTimeMillis();
        left = kind.ticks;
        step = 0;
        for (Player player : isle.onIsle()) {
            OriginProfile profile = isle.profiles().get(player);
            if (!profile.ambience) {
                continue;
            }
            if (isle.quiet(player)) {
                OriginText.bar(player, kind.title() + " §8· " + kind.line());
            } else {
                player.sendMessage("§d✦ " + kind.title() + " §8· " + kind.line());
                OriginText.bar(player, kind.title() + " §8· " + kind.line());
            }
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.8f, 0.6f);
            if (kind == Kind.AURORA || kind == Kind.STARFALL) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.4f, 1.6f);
            }
        }
    }

    private void finish() {
        Kind was = active;
        active = null;
        left = 0;
        if (was == Kind.LANTERN_FESTIVAL) {
            // Lanterns already in the air finish rising on their own schedule.
            return;
        }
        if (was == Kind.HARBOUR_FOG) {
            for (Player player : audience()) {
                OriginText.bar(player, "§7The fog lifts off the bay.");
            }
        }
    }

    void stopAll() {
        active = null;
        left = 0;
        for (UUID id : lanterns) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        lanterns.clear();
        lanternDeaths.clear();
    }

    public Kind active() {
        return active;
    }

    public int secondsLeft() {
        return Math.max(0, left / 20);
    }

    public int minutesToNext() {
        if (active != null || nextAt < 0L) {
            return -1;
        }
        return (int) Math.max(0L, (nextAt - lastTick) / 1200L);
    }

    private List<Player> audience() {
        List<Player> out = new ArrayList<>();
        for (Player player : isle.onIsle()) {
            if (isle.profiles().get(player).particles) {
                out.add(player);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ rendering

    private void render(Kind kind, List<Player> audience, long tick) {
        switch (kind) {
            case LANTERN_FESTIVAL -> lanternFestival(audience, tick);
            case AURORA -> {
                if (step % 2 == 0) {
                    for (Player player : audience) {
                        aurora(player);
                    }
                }
            }
            case STARFALL -> {
                if (step % 5 == 0) {
                    for (Player player : audience) {
                        shootingStar(player, ThreadLocalRandom.current().nextInt(3) == 0);
                    }
                }
            }
            case PETAL_STORM -> {
                if (step % 2 == 0) {
                    petals(audience);
                }
            }
            case HARBOUR_FOG -> {
                if (step % 2 == 0) {
                    fog(audience);
                }
            }
            case RAINBOW -> {
                if (step % 5 == 0) {
                    for (Player player : audience) {
                        rainbow(player);
                    }
                }
            }
        }
    }

    private void lanternFestival(List<Player> audience, long tick) {
        double[] center = isle.config().point("events.lantern-festival.center");
        double radius = isle.config().raw().getDouble("events.lantern-festival.radius", 34.0d);
        World world = isle.config().world();
        if (center == null || world == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (step % 5 == 0 && lanterns.size() < MAX_LANTERNS && world.isChunkLoaded((int) center[0] >> 4, (int) center[2] >> 4)) {
            double a = rng.nextDouble(Math.PI * 2.0d);
            double r = Math.sqrt(rng.nextDouble()) * radius;
            Location at = new Location(world, center[0] + Math.cos(a) * r, center[1] + rng.nextDouble(0.0d, 3.0d), center[2] + Math.sin(a) * r);
            lantern(world, at, tick);
        }
        if (step % 30 == 0) {
            firework(world, new Location(world, center[0] + rng.nextDouble(-radius / 2, radius / 2), center[1] + 2,
                    center[2] + rng.nextDouble(-radius / 2, radius / 2)));
        }
        if (step % 20 == 0) {
            for (Player player : audience) {
                double dx = player.getLocation().getX() - center[0];
                double dz = player.getLocation().getZ() - center[2];
                if (dx * dx + dz * dz < (radius + 60) * (radius + 60)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.AMBIENT, 0.35f,
                            0.8f + rng.nextFloat() * 0.8f);
                }
            }
        }
    }

    private void lantern(World world, Location at, long tick) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Material material = rng.nextInt(4) == 0 ? Material.SOUL_LANTERN : Material.LANTERN;
        BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setPersistent(false);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setViewRange(4.0f);
            spawned.setTransformation(new Transformation(new Vector3f(-0.3f, 0f, -0.3f), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
            spawned.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        lanterns.add(display.getUniqueId());
        lanternDeaths.add(new long[]{display.getUniqueId().getMostSignificantBits(), display.getUniqueId().getLeastSignificantBits(), tick + 420L});
        float drift = (float) rng.nextDouble(-6.0d, 6.0d);
        float driftZ = (float) rng.nextDouble(-6.0d, 6.0d);
        float spin = (float) rng.nextDouble(-1.5d, 1.5d);
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(400);
                display.setTransformation(new Transformation(new Vector3f(drift - 0.3f, 42.0f, driftZ - 0.3f),
                        new AxisAngle4f(spin, 0f, 1f, 0f), new Vector3f(0.45f, 0.45f, 0.45f), new AxisAngle4f()));
            }
        }, 2L);
    }

    private void reapLanterns(long tick) {
        if (lanternDeaths.isEmpty()) {
            return;
        }
        Iterator<long[]> it = lanternDeaths.iterator();
        while (it.hasNext()) {
            long[] entry = it.next();
            if (tick < entry[2]) {
                continue;
            }
            UUID id = new UUID(entry[0], entry[1]);
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
            lanterns.remove(id);
            it.remove();
        }
    }

    private void firework(World world, Location at) {
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Color a = RAINBOW[rng.nextInt(RAINBOW.length)];
        Color b = Color.fromRGB(0xFFE7A8);
        world.spawn(at, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(rng.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(a).withFade(b).trail(true).flicker(rng.nextBoolean()).build());
            meta.setPower(1 + rng.nextInt(2));
            firework.setFireworkMeta(meta);
            firework.setPersistent(false);
            firework.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && firework.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    /** A moving curtain of green and violet, 70 blocks north and 45-60 blocks up from each viewer. */
    private void aurora(Player player) {
        Location base = player.getLocation();
        double time = step * 0.08d;
        Color green = Color.fromRGB(0x3CFF9E);
        Color teal = Color.fromRGB(0x2FD6D0);
        Color violet = Color.fromRGB(0x8A5CFF);
        for (int i = 0; i <= 44; i++) {
            double t = i / 44.0d;
            double x = base.getX() - 90.0d + 180.0d * t;
            double z = base.getZ() - 72.0d + 9.0d * Math.sin(t * 6.0d + time);
            double y = Math.min(318.0d, base.getY() + 48.0d + 5.0d * Math.sin(t * 4.0d + time * 0.7d));
            for (int k = 0; k < 3; k++) {
                Color from = k == 0 ? green : (k == 1 ? teal : green);
                Color to = k == 2 ? violet : teal;
                new ParticleBuilder(Particle.DUST_COLOR_TRANSITION)
                        .location(new Location(base.getWorld(), x, y + k * 3.2d, z))
                        .data(new Particle.DustTransition(from, to, 4.0f - k * 0.6f))
                        .count(1).offset(0.6, 0.8, 0.6).extra(0.0)
                        .receivers(player).force(true).spawn();
            }
        }
    }

    private void shootingStar(Player player, boolean big) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location base = player.getLocation();
        double sx = base.getX() + rng.nextDouble(-70.0d, 70.0d);
        double sz = base.getZ() + rng.nextDouble(-70.0d, 70.0d);
        double sy = Math.min(310.0d, base.getY() + rng.nextDouble(45.0d, 75.0d));
        double dx = rng.nextDouble(-1.0d, 1.0d);
        double dz = rng.nextDouble(-1.0d, 1.0d);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2d) {
            dx = 1.0d;
            dz = 0.3d;
            len = Math.sqrt(dx * dx + dz * dz);
        }
        dx /= len;
        dz /= len;
        int points = big ? 30 : 18;
        for (int i = 0; i < points; i++) {
            double d = i * 0.75d;
            Location at = new Location(base.getWorld(), sx + dx * d, sy - d * 0.35d, sz + dz * d);
            new ParticleBuilder(i >= points - 2 ? Particle.FIREWORK : Particle.END_ROD)
                    .location(at).count(1).offset(0.02, 0.02, 0.02).extra(0.0)
                    .receivers(player).force(true).spawn();
        }
        if (big) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.5f, 1.8f);
        }
    }

    private void petals(List<Player> audience) {
        List<double[]> centers = isle.config().points("events.petal-storm.centers");
        for (Player player : audience) {
            Location at = player.getLocation();
            boolean near = centers.isEmpty();
            for (double[] c : centers) {
                double dx = at.getX() - c[0];
                double dz = at.getZ() - c[2];
                if (dx * dx + dz * dz < 110.0d * 110.0d) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            new ParticleBuilder(Particle.CHERRY_LEAVES).location(at.clone().add(0, 7, 0)).count(14)
                    .offset(9, 4, 9).extra(0.0).receivers(player).spawn();
            if (step % 40 == 0) {
                player.playSound(at, Sound.BLOCK_CHERRY_LEAVES_PLACE, SoundCategory.AMBIENT, 0.25f, 0.6f);
                player.playSound(at, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.08f, 1.6f);
            }
        }
    }

    private void fog(List<Player> audience) {
        double[] center = isle.config().point("events.harbour-fog.center");
        double radius = isle.config().raw().getDouble("events.harbour-fog.radius", 60.0d);
        if (center == null) {
            return;
        }
        for (Player player : audience) {
            Location at = player.getLocation();
            double dx = at.getX() - center[0];
            double dz = at.getZ() - center[2];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius + 40.0d) {
                continue;
            }
            int thick = d < radius ? 10 : 4;
            new ParticleBuilder(Particle.CLOUD).location(at.clone().add(0, 1.5, 0)).count(thick)
                    .offset(9, 2, 9).extra(0.0).receivers(player).spawn();
            new ParticleBuilder(Particle.WHITE_ASH).location(at.clone().add(0, 2, 0)).count(thick * 3)
                    .offset(10, 3, 10).extra(0.0).receivers(player).spawn();
            if (step % 150 == 0) {
                Location horn = new Location(at.getWorld(), 274.5, 73, -473.5);
                player.playSound(horn, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.AMBIENT, 2.5f, 0.5f);
                Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                    if (player.isOnline()) {
                        player.playSound(horn, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.AMBIENT, 2.5f, 0.53f);
                    }
                }, 24L);
            }
        }
    }

    /** A soft arc in the northern sky, 100 blocks out. */
    private void rainbow(Player player) {
        Location base = player.getLocation();
        double cx = base.getX() + 30.0d;
        double cz = base.getZ() - 100.0d;
        double cy = base.getY() - 12.0d;
        for (int band = 0; band < RAINBOW.length; band++) {
            double r = 72.0d - band * 1.6d;
            Particle.DustOptions dust = new Particle.DustOptions(RAINBOW[band], 4.0f);
            for (int i = 0; i <= 36; i++) {
                double a = Math.PI * i / 36.0d;
                double y = cy + Math.sin(a) * r;
                if (y > 318.0d) {
                    continue;
                }
                new ParticleBuilder(Particle.DUST).location(new Location(base.getWorld(), cx + Math.cos(a) * r, y, cz))
                        .data(dust).count(1).offset(0.3, 0.3, 0.3).extra(0.0).receivers(player).force(true).spawn();
            }
        }
    }

    // ------------------------------------------------------------------ triggers

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (!isle.running() || !enabled() || event.toWeatherState()
                || !event.getWorld().getName().equalsIgnoreCase(isle.config().worldName())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            OriginIsle.Phase phase = isle.phase();
            if (active == null && (phase == OriginIsle.Phase.DAY || phase == OriginIsle.Phase.DAWN) && !isle.onIsle().isEmpty()) {
                begin(Kind.RAINBOW);
            }
        }, 100L);
    }

    /** Starfall wish: sneak while looking up (once per starfall). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (active != Kind.STARFALL || !event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getLocation().getPitch() > -40.0f || !isle.onIsle(player)) {
            return;
        }
        OriginProfile profile = isle.profiles().get(player);
        if (profile.lastWishEvent == eventId) {
            return;
        }
        profile.lastWishEvent = eventId;
        profile.wishes++;
        profile.dirty = true;
        long paid = isle.pay(player, "wish", 25L);
        shootingStar(player, true);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2.2, 0), 20, 0.3, 0.6, 0.3, 0.03);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
        player.sendMessage("§d✦ You made a wish. §8· §7" + profile.wishes + (profile.wishes == 1 ? " wish" : " wishes")
                + " on Origin" + (paid > 0 ? " §8· §6+" + paid : ""));
        OriginText.bar(player, "§d✦ A wish, sent upward.");
        if (profile.wishes >= 7 && profile.mark("wishmaker")) {
            OriginText.card(player, "§d§lWishmaker", "§7Seven wishes on falling stars", 60);
        }
    }

    // ------------------------------------------------------------------ housekeeping

    private void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if ((entity instanceof BlockDisplay || entity instanceof Firework)
                        && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    void forget(UUID id) {
        // Wishes live in the profile; nothing per-session to drop.
    }
}
