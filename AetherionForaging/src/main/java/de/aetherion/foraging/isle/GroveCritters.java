package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.weather.DayNightPhase;
import de.aetherion.foraging.weather.WeatherKind;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Things that live in the forests. Built from display entities, not mobs, so they never trip spawn
 * guards, can't be farmed with a spawner and cost next to nothing. Every one has a job:
 * <ul>
 *   <li><b>Canopy Squirrel</b> (Elderwood Vale, Canopy Crown; day): sometimes bolts out of a felled
 *       crown with something in its cheeks. It runs; one hit and it drops the find.</li>
 *   <li><b>Frost Moth</b> (Frostpine Ridge, at night or in snow): flutters near foragers. Catch it for
 *       <i>Frostlit</i> — the CHOP window is one cell wider for a minute.</li>
 *   <li><b>Glowcap Wisp</b> (Gloamwood Hollow, in fog or at night): drifts off through the trunks. Follow
 *       it — where it fades, it leaves a Gloamcap behind.</li>
 *   <li><b>Bark Beetle</b> (Bark Blight): crawls at your ankles and nips. Two hits.</li>
 * </ul>
 */
public final class GroveCritters implements Listener {

    public enum Kind {
        SQUIRREL("§6Canopy Squirrel", Material.BROWN_WOOL, 1, 180),
        FROST_MOTH("§bFrost Moth", Material.WHITE_DYE, 1, 400),
        WISP("§3Glowcap Wisp", Material.GLOW_BERRIES, 1, 500),
        BEETLE("§cBark Beetle", Material.COCOA_BEANS, 2, 400);

        public final String display;
        final Material look;
        final int hp;
        final int lifeTicks;

        Kind(String display, Material look, int hp, int lifeTicks) {
            this.display = display;
            this.look = look;
            this.hp = hp;
            this.lifeTicks = lifeTicks;
        }
    }

    private static final String TAG = "ae_grove_critter";
    private static final int STEP = 2;

    private final ForageIsle isle;
    private final Map<UUID, Critter> all = new HashMap<>();
    private final Map<UUID, Critter> byPart = new HashMap<>();
    private final Map<UUID, Long> nextAmbient = new HashMap<>();

    private static final class Critter {
        final UUID id = UUID.randomUUID();
        Kind kind;
        UUID owner;
        Location at;
        Vector heading = new Vector();
        ItemDisplay body;
        Interaction hitbox;
        int hp;
        int age;
        int nextBite;
        double phase;
        Find carrying;
        boolean dead;
    }

    GroveCritters(ForageIsle isle) {
        this.isle = isle;
    }

    public int count() {
        return all.size();
    }

    private boolean enabled() {
        return isle.config().yaml().getBoolean("critters.enabled", true);
    }

    private int cap() {
        return Math.max(2, isle.config().yaml().getInt("critters.global-cap", 14));
    }

    private int ownedBy(UUID owner) {
        int n = 0;
        for (Critter c : all.values()) {
            if (owner.equals(c.owner)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ spawning

    /** A felled crown in the Vale / Crown sometimes lets a squirrel bolt with a find. */
    void onFell(Player player, FellContext ctx, Grove grove) {
        if (!enabled() || (grove != Grove.ELDERWOOD && grove != Grove.CANOPY_CROWN)) {
            return;
        }
        DayNightPhase phase = DayNightPhase.ofWorld(player.getWorld());
        if (phase == DayNightPhase.NIGHT || ThreadLocalRandom.current().nextDouble() >= 0.09d) {
            return;
        }
        Location at = GroveEvents.groundNear(ctx.anchor(), 1, 3);
        if (at == null) {
            return;
        }
        Critter c = spawnCritter(Kind.SQUIRREL, at, player);
        if (c != null) {
            c.carrying = Find.of(grove);
            ForageText.bar(player, "§6A Canopy Squirrel §7bolts from the crown with something in its cheeks!");
        }
    }

    /** Called every second by the router: ambient moths and wisps near foragers. */
    void ambient(List<Player> onIsle) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : onIsle) {
            Long next = nextAmbient.get(player.getUniqueId());
            if (next != null && now < next) {
                continue;
            }
            nextAmbient.put(player.getUniqueId(), now + 30_000L + ThreadLocalRandom.current().nextInt(30_000));
            if (all.size() >= cap() || ownedBy(player.getUniqueId()) >= 2) {
                continue;
            }
            Grove grove = isle.grove(player.getLocation());
            WeatherKind weather = isle.weatherKind(player);
            DayNightPhase phase = DayNightPhase.ofWorld(player.getWorld());
            boolean night = phase == DayNightPhase.NIGHT || phase == DayNightPhase.EVENING;
            Kind kind = null;
            if (grove == Grove.FROSTPINE && (night || weather == WeatherKind.SNOW)) {
                kind = Kind.FROST_MOTH;
            } else if (grove == Grove.GLOAMWOOD && (night || weather == WeatherKind.FOG)) {
                kind = Kind.WISP;
            }
            if (kind == null || ThreadLocalRandom.current().nextInt(3) == 0) {
                continue;
            }
            Location at = GroveEvents.groundNear(player.getLocation(), 4, 8);
            if (at != null) {
                spawn(kind, at.add(0, kind == Kind.WISP ? 1.4 : 1.8, 0), player);
            }
        }
    }

    void spawnBeetle(Player player) {
        if (!enabled() || all.size() >= cap() + 8 || ownedBy(player.getUniqueId()) >= 3) {
            return;
        }
        Location at = GroveEvents.groundNear(player.getLocation(), 3, 7);
        if (at != null) {
            spawn(Kind.BEETLE, at, player);
        }
    }

    /** DEV / events: spawn one critter owned by {@code owner}. */
    public boolean spawn(Kind kind, Location at, Player owner) {
        return spawnCritter(kind, at, owner) != null;
    }

    private Critter spawnCritter(Kind kind, Location at, Player owner) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        Critter c = new Critter();
        c.kind = kind;
        c.owner = owner == null ? null : owner.getUniqueId();
        c.at = at.clone();
        c.hp = kind.hp;
        c.phase = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        ItemStack look = new ItemStack(kind.look);
        float size = switch (kind) {
            case SQUIRREL -> 0.45f;
            case FROST_MOTH -> 0.5f;
            case WISP -> 0.55f;
            case BEETLE -> 0.4f;
        };
        Color glow = switch (kind) {
            case SQUIRREL -> null;
            case FROST_MOTH -> Color.fromRGB(200, 240, 255);
            case WISP -> Color.fromRGB(90, 255, 220);
            case BEETLE -> Color.fromRGB(255, 80, 40);
        };
        c.body = world.spawn(c.at, ItemDisplay.class, d -> {
            d.setItemStack(look);
            d.setBillboard(kind == Kind.SQUIRREL || kind == Kind.BEETLE ? Display.Billboard.FIXED : Display.Billboard.CENTER);
            if (glow != null) {
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
            }
            d.setBrightness(new Display.Brightness(12, 12));
            d.setTransformation(new Transformation(new Vector3f(0, size / 2, 0), new Quaternionf(), new Vector3f(size, size, size),
                    new Quaternionf()));
            d.setTeleportDuration(STEP);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        c.hitbox = world.spawn(c.at, Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(0.8f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(TAG);
        });
        all.put(c.id, c);
        byPart.put(c.body.getUniqueId(), c);
        byPart.put(c.hitbox.getUniqueId(), c);
        switch (kind) {
            case SQUIRREL -> world.playSound(c.at, Sound.ENTITY_FOX_AMBIENT, SoundCategory.NEUTRAL, 0.7f, 1.8f);
            case FROST_MOTH -> world.playSound(c.at, Sound.BLOCK_POWDER_SNOW_STEP, SoundCategory.NEUTRAL, 0.6f, 1.6f);
            case WISP -> world.playSound(c.at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.6f, 1.9f);
            case BEETLE -> world.playSound(c.at, Sound.ENTITY_SILVERFISH_AMBIENT, SoundCategory.HOSTILE, 0.7f, 1.4f);
        }
        return c;
    }

    // ------------------------------------------------------------------ moving

    /** Every {@value #STEP} ticks while any critter is alive. */
    void tick() {
        if (all.isEmpty()) {
            return;
        }
        for (Critter c : new ArrayList<>(all.values())) {
            if (c.dead || c.body == null || !c.body.isValid() || c.at.getWorld() == null) {
                remove(c);
                continue;
            }
            c.age += STEP;
            if (c.age >= c.kind.lifeTicks) {
                expire(c);
                continue;
            }
            Player owner = c.owner == null ? null : Bukkit.getPlayer(c.owner);
            if (owner == null || !owner.isOnline() || owner.getWorld() != c.at.getWorld()
                    || owner.getLocation().distanceSquared(c.at) > 40 * 40) {
                remove(c);
                continue;
            }
            move(c, owner);
        }
    }

    private void move(Critter c, Player owner) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location from = c.at.clone();
        Vector toOwner = owner.getLocation().toVector().subtract(from.toVector()).setY(0);
        double dist = toOwner.length();
        c.phase += 0.35d;
        Vector want;
        double speed;
        switch (c.kind) {
            case SQUIRREL -> {
                // Bolt away from whoever is close, zig-zag.
                want = dist < 0.01 ? new Vector(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1)) : toOwner.clone().multiply(-1).normalize();
                want.rotateAroundY(Math.sin(c.phase * 1.7) * 0.9);
                speed = dist < 6 ? 0.34d : 0.18d;
            }
            case FROST_MOTH -> {
                want = new Vector(Math.cos(c.phase), 0, Math.sin(c.phase * 1.3));
                if (dist > 7) {
                    want.add(toOwner.clone().normalize().multiply(0.8));
                }
                speed = 0.16d;
            }
            case WISP -> {
                // Leads away: drifts off in a slowly turning direction, waits if you fall behind.
                if (c.heading.lengthSquared() < 1.0E-4) {
                    c.heading = new Vector(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1)).normalize();
                }
                c.heading.rotateAroundY(Math.sin(c.phase * 0.3) * 0.08);
                want = c.heading.clone();
                speed = dist > 9 ? 0.0d : 0.14d;
            }
            case BEETLE -> {
                want = dist < 0.01 ? new Vector() : toOwner.clone().normalize();
                speed = dist > 1.2 ? 0.16d : 0.0d;
                if (dist <= 1.4 && c.age >= c.nextBite) {
                    c.nextBite = c.age + 30;
                    double allowed = Math.max(0.0d, owner.getHealth() - 2.0d);
                    if (allowed > 0) {
                        owner.damage(Math.min(1.0d, allowed));
                    }
                    owner.playSound(owner.getLocation(), Sound.ENTITY_SILVERFISH_HURT, SoundCategory.HOSTILE, 0.8f, 1.3f);
                }
            }
            default -> {
                want = new Vector();
                speed = 0.0d;
            }
        }
        Location next = from.clone();
        if (want.lengthSquared() > 1.0E-4 && speed > 0) {
            next.add(want.normalize().multiply(speed));
        }
        boolean flies = c.kind == Kind.FROST_MOTH || c.kind == Kind.WISP;
        if (flies) {
            next.setY(next.getY() + Math.sin(c.phase) * 0.05d);
            if (!next.getBlock().isPassable()) {
                next = from.clone().add(0, 0.25, 0);
                if (!next.getBlock().isPassable()) {
                    next = from.clone();
                    c.heading.multiply(-1);
                }
            }
        } else {
            Location floor = floorNear(next);
            next = floor != null ? floor : from.clone();
        }
        if (want.lengthSquared() > 1.0E-4) {
            next.setYaw((float) Math.toDegrees(Math.atan2(-want.getX(), want.getZ())));
        }
        c.at = next;
        c.body.teleport(next);
        c.hitbox.teleport(next);
        if (flies && c.age % 6 == 0) {
            next.getWorld().spawnParticle(c.kind == Kind.WISP ? Particle.GLOW : Particle.SNOWFLAKE, next, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private static Location floorNear(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        for (int dy = 1; dy >= -2; dy--) {
            int y = at.getBlockY() + dy;
            if (world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                Location out = at.clone();
                out.setY(y);
                return out;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ catching

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(EntityDamageByEntityEvent event) {
        Critter c = byPart.get(event.getEntity().getUniqueId());
        if (c == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            hit(player, c);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        Critter c = byPart.get(event.getRightClicked().getUniqueId());
        if (c != null) {
            event.setCancelled(true);
            hit(event.getPlayer(), c);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (byPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void hit(Player player, Critter c) {
        if (c.dead) {
            return;
        }
        c.hp--;
        c.at.getWorld().spawnParticle(Particle.CRIT, c.at.clone().add(0, 0.3, 0), 5, 0.15, 0.15, 0.15, 0.05);
        if (c.hp > 0) {
            c.at.getWorld().playSound(c.at, Sound.ENTITY_SILVERFISH_HURT, SoundCategory.NEUTRAL, 0.7f, 1.5f);
            return;
        }
        c.dead = true;
        caught(player, c);
        remove(c);
    }

    private void caught(Player player, Critter c) {
        ForageProfile profile = isle.profiles().get(player);
        profile.critters++;
        profile.dirty = true;
        isle.board().noteCritter(player, profile);
        Location at = c.at.clone();
        World world = at.getWorld();
        switch (c.kind) {
            case SQUIRREL -> {
                world.playSound(at, Sound.ENTITY_FOX_HURT, SoundCategory.NEUTRAL, 0.7f, 1.8f);
                Find find = c.carrying == null ? Find.ELDER_ACORN : c.carrying;
                ForageItems.FindData data = ForageItems.roll(find, ThreadLocalRandom.current().nextInt(4) == 0
                        ? ForageItems.Grade.FINE : ForageItems.Grade.ROUGH);
                isle.finds().drop(player, data, at.clone().add(0, 1.4, 0), at);
                ForageText.bar(player, "§6Got it! §7The squirrel spat out its prize.");
            }
            case FROST_MOTH -> {
                profile.frostlitUntil = System.currentTimeMillis() + 60_000L;
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.8f, 1.6f);
                world.spawnParticle(Particle.SNOWFLAKE, at, 20, 0.3, 0.3, 0.3, 0.02);
                ForageText.bar(player, "§b❄ Frostlit §8· §7CHOP window +1 for §f1:00");
                ForageBridge.bonus(player, 12);
            }
            case WISP -> {
                world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.NEUTRAL, 0.8f, 1.7f);
                ForageItems.FindData data = ForageItems.roll(Find.GLOAMCAP, CrownFinds.rollGrade(1.5d));
                isle.finds().drop(player, data, at.clone().add(0, 0.6, 0), at);
                ForageText.bar(player, "§3The wisp flickers out §7and something falls where it was.");
            }
            case BEETLE -> {
                world.playSound(at, Sound.ENTITY_SILVERFISH_DEATH, SoundCategory.HOSTILE, 0.8f, 1.4f);
                ForageBridge.coins(player, 15L);
                isle.events().countBeetle(player);
            }
        }
    }

    private void expire(Critter c) {
        if (c.kind == Kind.WISP && c.at.getWorld() != null) {
            // A wisp that got away still leaves its gift where it faded — that's the point of following.
            Player owner = c.owner == null ? null : Bukkit.getPlayer(c.owner);
            if (owner != null && owner.getLocation().distanceSquared(c.at) < 20 * 20) {
                Location floor = floorNear(c.at.clone().subtract(0, 1.4, 0));
                Location land = floor == null ? c.at.clone() : floor;
                isle.finds().drop(owner, ForageItems.roll(Find.GLOAMCAP, ForageItems.Grade.ROUGH), c.at, land);
            }
        }
        remove(c);
    }

    private void remove(Critter c) {
        all.remove(c.id);
        if (c.body != null) {
            byPart.remove(c.body.getUniqueId());
            c.body.remove();
        }
        if (c.hitbox != null) {
            byPart.remove(c.hitbox.getUniqueId());
            c.hitbox.remove();
        }
    }

    void clear() {
        for (Critter c : new ArrayList<>(all.values())) {
            remove(c);
        }
        nextAmbient.clear();
    }

    void forget(UUID player) {
        nextAmbient.remove(player);
    }

    static boolean isOurs(Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }
}
