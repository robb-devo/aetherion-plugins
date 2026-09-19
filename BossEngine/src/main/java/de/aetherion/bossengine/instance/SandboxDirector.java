package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.fx.CombatTheatrics;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Experimental / Test-Arena bosses. Unique mechanics, no live loot.
 */
public final class SandboxDirector {

    enum Kind {
        NONE,
        ECHO,
        PARITY,
        CURATOR,
        NULLSPACE,
        LOADBEARING,
        SOFTLOCK,
        HEARTBEAT,
        BROKER,
        AFTERIMAGE,
        GRAVITY,
        QUIET,
        PETJURY;

        static Kind of(String id) {
            if (id == null) {
                return NONE;
            }
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "test_echo" -> ECHO;
                case "test_parity" -> PARITY;
                case "test_curator" -> CURATOR;
                case "test_nullspace" -> NULLSPACE;
                case "test_loadbearing" -> LOADBEARING;
                case "test_softlock" -> SOFTLOCK;
                case "test_heartbeat" -> HEARTBEAT;
                case "test_broker" -> BROKER;
                case "test_afterimage" -> AFTERIMAGE;
                case "test_gravity" -> GRAVITY;
                case "test_quiet" -> QUIET;
                case "test_petjury" -> PETJURY;
                default -> NONE;
            };
        }
    }

    private final BossInstance instance;
    private Kind kind = Kind.NONE;
    private int ticks;
    private final List<Entity> props = new ArrayList<>();
    private final Map<UUID, List<Snapshot>> echoTape = new HashMap<>();
    private LivingEntity parityTwin;
    private final Map<UUID, Long> parityHits = new HashMap<>();
    private String curatorRule = "sprint";
    private int curatorUntil;
    private final List<Block> nullHoles = new ArrayList<>();
    private final List<Location> pillars = new ArrayList<>();
    private final List<PillarSite> pillarSites = new ArrayList<>();
    private int pillarsLeft;
    private int loadPhase; // 0 pillars, 1 awakening, 2 berserk
    private int loadAwakenTicks;
    private int loadAttackCd;
    private int loadPattern;
    private final Map<UUID, Integer> softSlots = new HashMap<>();
    private int beatPhase;
    private boolean brokerOpen;
    private int brokerUntil;
    private LivingEntity afterFake;
    private boolean afterReveal;
    private int gravityMode; // 0 normal, 1 pull up, 2 sideways
    private final Map<UUID, Location> quietMarks = new HashMap<>();
    private boolean petJuryArmed;
    private int showCd;
    private int showPattern;
    private final Map<Block, Material> nullRestore = new HashMap<>();
    private final List<LivingEntity> afterFakes = new ArrayList<>();
    private int curatorStrikeCd;
    private int softBurstCd;

    SandboxDirector(BossInstance instance) {
        this.instance = instance;
    }

    public boolean active() {
        return kind != Kind.NONE;
    }

    void onBind() {
        kind = Kind.of(instance.getTemplate() == null ? "" : instance.getTemplate().getId());
        if (kind == Kind.NONE) {
            return;
        }
        ticks = 0;
        clearProps();
        showCd = 0;
        showPattern = 0;
        curatorStrikeCd = 0;
        softBurstCd = 0;
        nullRestore.clear();
        afterFakes.clear();
        LivingEntity body = instance.getEntity();
        Location at = body != null ? body.getLocation() : instance.getSpawnLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.8f);
        ring(world, at.clone().add(0, 1, 0), Particle.END_ROD, 1.8, 48);
        shout("&8[Sandbox] &f" + kind.name() + " &7prototype online.");

        switch (kind) {
            case PARITY -> spawnParityTwin(at);
            case LOADBEARING -> {
                if (body instanceof Mob mob) {
                    mob.setAI(false);
                    mob.setAware(false);
                }
                if (body != null) {
                    body.setGravity(false);
                    body.setVelocity(new Vector());
                    body.setGlowing(true);
                    AttributeUtil.setBase(body, AttributeUtil.scale(), 1.85);
                }
                loadPhase = 0;
                loadAwakenTicks = 0;
                loadAttackCd = 0;
                loadPattern = 0;
                buildPillars(at);
            }
            case SOFTLOCK -> {
                if (body instanceof Mob mob) {
                    mob.setAI(false);
                }
            }
            case CURATOR -> rollCuratorRule(true);
            case HEARTBEAT -> beatPhase = 0;
            case AFTERIMAGE -> spawnAfterimage(at);
            case PETJURY -> petJuryArmed = true;
            default -> {
            }
        }
    }

    void abort() {
        restoreNullHoles();
        clearProps();
        pillarSites.clear();
        pillars.clear();
        pillarsLeft = 0;
        loadPhase = 0;
        if (parityTwin != null && parityTwin.isValid()) {
            parityTwin.remove();
        }
        parityTwin = null;
        clearAfterFakes();
        softSlots.clear();
        echoTape.clear();
        quietMarks.clear();
        nullRestore.clear();
        kind = Kind.NONE;
    }

    void tick() {
        if (!active() || !instance.isAlive()) {
            return;
        }
        ticks++;
        LivingEntity body = instance.getEntity();
        if (body == null || !body.isValid()) {
            return;
        }
        Location at = body.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }

        switch (kind) {
            case ECHO -> tickEcho(world, at);
            case PARITY -> tickParity(world, at);
            case CURATOR -> tickCurator(world, at);
            case NULLSPACE -> tickNullspace(world, at);
            case LOADBEARING -> tickLoadBearing(world, at);
            case SOFTLOCK -> tickSoftlock(world, at);
            case HEARTBEAT -> tickHeartbeat(world, at);
            case BROKER -> tickBroker(world, at);
            case AFTERIMAGE -> tickAfterimage(world, at);
            case GRAVITY -> tickGravity(world, at);
            case QUIET -> tickQuiet(world, at);
            case PETJURY -> tickPetJury(world, at);
            default -> {
            }
        }
    }

    /**
     * @return modified damage, or negative if the hit should be fully cancelled
     */
    public double modifyIncoming(Player player, double damage) {
        if (!active() || player == null) {
            return damage;
        }
        return switch (kind) {
            case ECHO -> damage;
            case PARITY -> parityDamage(player, damage);
            case CURATOR -> curatorDamage(player, damage);
            case LOADBEARING -> loadPhase < 2 ? -1 : damage;
            case HEARTBEAT -> heartbeatDamage(player, damage);
            case AFTERIMAGE -> afterimageDamage(player, damage);
            case BROKER -> brokerOpen && !hasCharm(player, "rulebreaker_charm") ? damage * 0.35 : damage;
            case PETJURY -> petJuryDamage(player, damage);
            default -> damage;
        };
    }

    public boolean blocksDamage() {
        return kind == Kind.LOADBEARING && loadPhase < 2;
    }

    // ── Echo ──────────────────────────────────────────────────

    private void tickEcho(World world, Location at) {
        LivingEntity body = instance.getEntity();
        for (Player player : nearby(at, 30)) {
            List<Snapshot> tape = echoTape.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
            tape.add(new Snapshot(player.getLocation().clone(), player.getVelocity().clone(), System.currentTimeMillis()));
            while (tape.size() > 200) {
                tape.remove(0);
            }
            while (!tape.isEmpty() && System.currentTimeMillis() - tape.get(0).time > 10000L) {
                tape.remove(0);
            }
        }
        if (ticks % 8 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1.1, 0), Color.fromRGB(180, 80, 220), 1.4, 22, 1.15f);
        }
        if (showCd > 0) {
            showCd--;
        }
        double hp = SandboxFx.hpFrac(body);
        int cadence = hp < 0.25 ? 55 : (hp < 0.5 ? 70 : 90);
        if (showCd <= 0) {
            showCd = cadence;
            showPattern = (showPattern + 1) % 4;
            switch (showPattern) {
                case 0 -> echoReplayBurst(world, at, body);
                case 1 -> echoRewindSnap(world, at, body);
                case 2 -> echoSoulTendrils(world, at, body);
                default -> echoCloneStorm(world, at, body);
            }
        }
        if (SandboxFx.enraged(body) && ticks % 35 == 0) {
            SandboxFx.expandingRings(instance, world, at, body, 3, 2.0, 14, Particle.WITCH);
        }
    }

    private void echoReplayBurst(World world, Location at, LivingEntity body) {
        shout("&d&lECHO REPLAY");
        world.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.15f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.7f);
        for (Player player : nearby(at, 30)) {
            List<Snapshot> tape = echoTape.get(player.getUniqueId());
            if (tape == null || tape.size() < 12) {
                continue;
            }
            int[] indices = {tape.size() / 4, tape.size() / 2, (tape.size() * 3) / 4};
            for (int idx : indices) {
                Snapshot old = tape.get(Math.max(0, Math.min(tape.size() - 1, idx)));
                Location ghost = old.loc.clone();
                ArmorStand stand = world.spawn(ghost, ArmorStand.class, s -> {
                    s.setInvisible(true);
                    s.setMarker(true);
                    s.setGravity(false);
                    s.setSmall(true);
                    s.setGlowing(true);
                    tagProp(s);
                });
                props.add(stand);
                world.spawnParticle(Particle.SOUL, ghost, 16, 0.35, 0.7, 0.35, 0.01);
                SandboxFx.later(instance, 14L + ThreadLocalRandom.current().nextInt(10), () -> {
                    if (!stand.isValid()) {
                        return;
                    }
                    world.spawnParticle(Particle.SONIC_BOOM, stand.getLocation(), 1, 0, 0, 0, 0);
                    world.playSound(stand.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.55f);
                    for (Player victim : nearby(stand.getLocation(), 2.6)) {
                        victim.damage(22, body);
                        Vector knock = victim.getLocation().toVector().subtract(stand.getLocation().toVector());
                        if (knock.lengthSquared() > 0.05) {
                            victim.setVelocity(knock.normalize().multiply(-0.7).setY(0.4));
                        }
                    }
                    stand.remove();
                });
            }
        }
    }

    private void echoRewindSnap(World world, Location at, LivingEntity body) {
        shout("&d&lREWIND");
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.7f);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 4, 0.5, 0.4, 0.5, 0);
        for (Player player : nearby(at, 28)) {
            List<Snapshot> tape = echoTape.get(player.getUniqueId());
            if (tape == null || tape.size() < 20) {
                continue;
            }
            Snapshot old = tape.get(Math.max(0, tape.size() - 30));
            Location dest = old.loc.clone();
            SandboxFx.dustBeam(world, player.getLocation().add(0, 1, 0), dest.clone().add(0, 1, 0),
                    Color.fromRGB(160, 60, 255), 1.4f);
            player.teleport(dest);
            player.damage(12, body);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, true));
            world.spawnParticle(Particle.PORTAL, dest, 25, 0.4, 0.6, 0.4, 0.15);
            player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
        }
    }

    private void echoSoulTendrils(World world, Location at, LivingEntity body) {
        shout("&d&lSOUL TENDRILS");
        world.playSound(at, Sound.ENTITY_GHAST_WARN, 0.55f, 1.6f);
        for (int i = 0; i < 6; i++) {
            Player target = SandboxFx.nearest(at, 26);
            if (target == null) {
                break;
            }
            Location tip = target.getLocation().add(0, 1, 0);
            SandboxFx.dustBeam(world, at.clone().add(0, 1.4, 0), tip, Color.fromRGB(100, 40, 160), 1.6f);
            target.damage(16, body);
            SandboxFx.pullIn(at, List.of(target), 0.35);
        }
    }

    private void echoCloneStorm(World world, Location at, LivingEntity body) {
        shout("&d&lCLONE STORM");
        world.playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.1f, 0.9f);
        for (int i = 0; i < 5; i++) {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            Location spawn = at.clone().add(Math.cos(a) * 5.5, 0.2, Math.sin(a) * 5.5);
            ArmorStand clone = world.spawn(spawn, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setGlowing(true);
                tagProp(s);
            });
            props.add(clone);
            for (int t = 0; t < 18; t++) {
                int step = t;
                SandboxFx.later(instance, step * 2L, () -> {
                    if (!clone.isValid()) {
                        return;
                    }
                    Player chase = SandboxFx.nearest(clone.getLocation(), 18);
                    if (chase != null) {
                        Vector to = chase.getLocation().toVector().subtract(clone.getLocation().toVector());
                        if (to.lengthSquared() > 0.2) {
                            clone.teleport(clone.getLocation().add(to.normalize().multiply(0.55)));
                        }
                        world.spawnParticle(Particle.WITCH, clone.getLocation().add(0, 1, 0), 3, 0.1, 0.2, 0.1, 0);
                        if (chase.getLocation().distanceSquared(clone.getLocation()) < 2.2) {
                            chase.damage(14, body);
                        }
                    }
                    if (step == 17) {
                        world.spawnParticle(Particle.EXPLOSION, clone.getLocation(), 1, 0.2, 0.2, 0.2, 0);
                        clone.remove();
                    }
                });
            }
        }
    }

    // ── Parity ────────────────────────────────────────────────

    private void spawnParityTwin(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Location twinAt = at.clone().add(4, 0, 0);
        parityTwin = (LivingEntity) world.spawnEntity(twinAt, EntityType.ALLAY);
        parityTwin.customName(TextUtil.component("&b&lParity Twin"));
        parityTwin.setCustomNameVisible(true);
        parityTwin.setGlowing(true);
        parityTwin.setRemoveWhenFarAway(false);
        parityTwin.setPersistent(true);
        AttributeUtil.setMaxHealth(parityTwin, 4096);
        tagProp(parityTwin);
        parityTwin.getPersistentDataContainer().set(
                AetherKeys.namespaced("bossengine", "sandbox_parity"),
                PersistentDataType.STRING,
                instance.getInstanceId().toString()
        );
        props.add(parityTwin);
    }

    private void tickParity(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (parityTwin == null || !parityTwin.isValid()) {
            spawnParityTwin(at);
            shout("&bParity Twin &7reformed.");
        } else {
            double orbit = SandboxFx.enraged(body) ? 5.8 : 4.5;
            Location want = at.clone().add(Math.sin(ticks * 0.1) * orbit, 0.35, Math.cos(ticks * 0.1) * orbit);
            parityTwin.setVelocity(want.toVector().subtract(parityTwin.getLocation().toVector()).multiply(0.22));
            world.spawnParticle(Particle.END_ROD, parityTwin.getLocation().add(0, 0.6, 0), 4, 0.15, 0.2, 0.15, 0);
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.6, 0), 4, 0.15, 0.2, 0.15, 0);
            if (ticks % 3 == 0) {
                SandboxFx.dustBeam(world, at.clone().add(0, 0.9, 0), parityTwin.getLocation().add(0, 0.9, 0),
                        Color.fromRGB(80, 220, 255), 1.2f);
                // Standing in the link hurts
                Vector step = parityTwin.getLocation().toVector().subtract(at.toVector());
                double len = step.length();
                if (len > 0.2) {
                    step.normalize().multiply(0.5);
                    Location c = at.clone().add(0, 0.9, 0);
                    for (double d = 0; d < len; d += 0.5) {
                        for (Player player : nearby(c, 1.15)) {
                            player.damage(4, body);
                        }
                        c.add(step);
                    }
                }
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 70 : 100;
            showPattern = (showPattern + 1) % 3;
            if (showPattern == 0) {
                shout("&b&lSYNC WINDOW &7— tag twin then body");
                world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f, 1.5f);
                SandboxFx.dustRing(world, at.clone().add(0, 1, 0), Color.fromRGB(100, 220, 255), 3.0, 36, 1.4f);
            } else if (showPattern == 1) {
                shout("&b&lCROSS LASER");
                world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.4f);
                for (int i = 0; i < 4; i++) {
                    double a = i * (Math.PI / 2) + ticks * 0.05;
                    Location tip = at.clone().add(Math.cos(a) * 12, 1, Math.sin(a) * 12);
                    SandboxFx.dustBeam(world, at.clone().add(0, 1, 0), tip, Color.fromRGB(40, 180, 255), 1.8f);
                    for (Player player : nearby(tip, 1.4)) {
                        player.damage(18, body);
                    }
                }
            } else {
                shout("&b&lTWIN PULSE");
                SandboxFx.expandingRings(instance, world, at, body, 4, 1.8, 12, Particle.ELECTRIC_SPARK);
                if (parityTwin != null && parityTwin.isValid()) {
                    SandboxFx.expandingRings(instance, world, parityTwin.getLocation(), body, 3, 1.6, 10, Particle.END_ROD);
                }
            }
        }
    }

    private double parityDamage(Player player, double damage) {
        long now = System.currentTimeMillis();
        Long last = parityHits.put(player.getUniqueId(), now);
        if (last != null && now - last < 1000L) {
            ring(player.getWorld(), player.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 1.4, 28);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.8f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§b§lPARITY SYNC ×1.6"));
            player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
            return damage * 1.6;
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("§7Hit the §btwin §7within §f1.0s§7…"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.55f);
        return damage * 0.12;
    }

    boolean isParityTwin(Entity entity) {
        return parityTwin != null && entity != null && parityTwin.getUniqueId().equals(entity.getUniqueId());
    }

    public void onParityTwinHit(Player player) {
        parityHits.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendActionBar(net.kyori.adventure.text.Component.text("§bTwin tagged — strike the main body!"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.55f);
        if (parityTwin != null && parityTwin.isValid()) {
            parityTwin.getWorld().spawnParticle(Particle.FLASH, parityTwin.getLocation().add(0, 0.8, 0), 2, 0.2, 0.2, 0.2, 0);
        }
    }

    // ── Curator ───────────────────────────────────────────────

    private void rollCuratorRule(boolean announce) {
        String[] rules = {"sprint", "jump", "bow", "sneak", "lookup", "still"};
        curatorRule = rules[ThreadLocalRandom.current().nextInt(rules.length)];
        curatorUntil = ticks + (SandboxFx.enraged(instance.getEntity()) ? 140 : 180);
        if (announce) {
            shout("&6&lNEW RULE: &f" + curatorRuleLabel() + " &7is forbidden.");
            LivingEntity body = instance.getEntity();
            if (body != null) {
                body.getWorld().playSound(body.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.2f, 0.65f);
                body.getWorld().playSound(body.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
                spawnRuleSign(body.getLocation());
            }
        }
    }

    private String curatorRuleLabel() {
        return switch (curatorRule) {
            case "sprint" -> "Sprint";
            case "jump" -> "Jump";
            case "bow" -> "Bows / crossbows";
            case "sneak" -> "Sneaking";
            case "lookup" -> "Looking up";
            case "still" -> "Standing still";
            default -> curatorRule;
        };
    }

    private void spawnRuleSign(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        TextDisplay display = world.spawn(at.clone().add(0, 2.8, 0), TextDisplay.class, d -> {
            d.text(net.kyori.adventure.text.Component.text("§6§lRULE §f" + curatorRuleLabel()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(true);
            tagProp(d);
        });
        props.add(display);
        Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (display.isValid()) {
                display.remove();
            }
        }, 80L);
    }

    private void tickCurator(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks >= curatorUntil) {
            rollCuratorRule(true);
        }
        SandboxFx.dustRing(world, at.clone().add(0, 1.1, 0), Color.fromRGB(220, 170, 40), 1.6, 18, 1.1f);
        if (ticks % 8 == 0) {
            for (Player player : nearby(at, 28)) {
                if (hasCharm(player, "rulebreaker_charm")) {
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 2, 0.2, 0.2, 0.2, 0);
                    continue;
                }
                boolean breakRule = switch (curatorRule) {
                    case "sprint" -> player.isSprinting();
                    case "jump" -> !player.isOnGround() && player.getVelocity().getY() > 0.08;
                    case "bow" -> {
                        ItemStack main = player.getInventory().getItemInMainHand();
                        yield main.getType() == Material.BOW || main.getType() == Material.CROSSBOW;
                    }
                    case "sneak" -> player.isSneaking();
                    case "lookup" -> player.getLocation().getPitch() < -35;
                    case "still" -> player.getVelocity().lengthSquared() < 0.003 && player.isOnGround();
                    default -> false;
                };
                if (breakRule) {
                    player.damage(9, body);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1, true, false, true));
                    world.spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1.8, 0), 6, 0.25, 0.2, 0.25, 0);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85f, 1.35f);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§cRule broken: §f" + curatorRuleLabel()));
                }
            }
        }
        if (curatorStrikeCd > 0) {
            curatorStrikeCd--;
        }
        if (curatorStrikeCd <= 0) {
            curatorStrikeCd = SandboxFx.enraged(body) ? 65 : 95;
            shout("&6&lJUDGMENT SLAM");
            world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);
            Player target = SandboxFx.nearest(at, 26);
            Location mark = target != null ? target.getLocation() : at.clone().add(4, 0, 0);
            mark.setY(at.getY());
            SandboxFx.markedSlam(instance, world, mark, body, 3.4, 32, 22L, Particle.ENCHANTED_HIT, m -> {
                SandboxFx.visualDebris(instance, props, world, m, Material.BOOKSHELF, 10);
                world.spawnParticle(Particle.FLASH, m.clone().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0);
            });
            // Safe gold ring
            SandboxFx.dustRing(world, at.clone().add(0, 0.1, 0), Color.fromRGB(255, 215, 60), 4.5, 40, 1.5f);
            for (Player player : nearby(at, 4.5)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§6Safe ring"));
            }
        }
    }

    private double curatorDamage(Player player, double damage) {
        if (hasCharm(player, "rulebreaker_charm")) {
            return damage * 1.25;
        }
        return damage;
    }

    // ── Nullspace ─────────────────────────────────────────────

    private void tickNullspace(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks % 6 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1.3, 0), Color.fromRGB(90, 20, 140), 1.8, 24, 1.2f);
            world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1.5, 0), 10, 0.5, 0.6, 0.5, 0.08);
        }
        if (ticks % 65 == 0) {
            shout("&5&lVOID WELLS");
            world.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.35f);
            int wells = SandboxFx.enraged(body) ? 7 : 5;
            for (int i = 0; i < wells; i++) {
                double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double dist = 3 + ThreadLocalRandom.current().nextDouble() * 11;
                Location hole = at.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                Block floor = world.getHighestBlockAt(hole);
                if (Math.abs(floor.getY() - at.getBlockY()) > 3) {
                    floor = world.getBlockAt(hole.getBlockX(), at.getBlockY() - 1, hole.getBlockZ());
                }
                if (floor.getType().isAir() || floor.getType() == Material.BEDROCK) {
                    continue;
                }
                Material was = floor.getType();
                final Block holeBlock = floor;
                nullRestore.put(holeBlock, was);
                holeBlock.setType(Material.AIR, false);
                nullHoles.add(holeBlock);
                Location fx = holeBlock.getLocation().add(0.5, 1, 0.5);
                world.spawnParticle(Particle.PORTAL, fx, 40, 0.4, 0.5, 0.4, 0.25);
                world.spawnParticle(Particle.SQUID_INK, fx, 12, 0.3, 0.3, 0.3, 0.02);
                // Visual void plate so it still reads as a hole without permanent mess
                org.bukkit.entity.BlockDisplay voidPlate = world.spawn(
                        holeBlock.getLocation(),
                        org.bukkit.entity.BlockDisplay.class,
                        d -> {
                            d.setBlock(Material.BLACK_CONCRETE.createBlockData());
                            d.setPersistent(false);
                            d.setTransformation(new org.bukkit.util.Transformation(
                                    new org.joml.Vector3f(0f, -0.05f, 0f),
                                    new org.joml.Quaternionf(),
                                    new org.joml.Vector3f(1f, 0.08f, 1f),
                                    new org.joml.Quaternionf()
                            ));
                            tagProp(d);
                        });
                props.add(voidPlate);
                SandboxFx.later(instance, 90L, () -> {
                    if (voidPlate.isValid()) {
                        voidPlate.remove();
                    }
                    Material restore = nullRestore.remove(holeBlock);
                    if (restore != null && holeBlock.getType().isAir()) {
                        holeBlock.setType(restore, false);
                    }
                    nullHoles.remove(holeBlock);
                });
            }
        }
        // Soft pull into open air
        for (Player player : nearby(at, 24)) {
            Block under = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (under.getType().isAir() && player.getLocation().getY() < at.getY() + 2) {
                player.setVelocity(player.getVelocity().add(new Vector(0, -0.22, 0)));
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 75 : 100;
            shout("&5&lNULL LANCE");
            world.playSound(at, Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.7f);
            Player target = SandboxFx.nearest(at, 28);
            if (target != null) {
                SandboxFx.dustBeam(world, at.clone().add(0, 1.6, 0), target.getEyeLocation(),
                        Color.fromRGB(120, 0, 180), 2.0f);
                target.damage(28, body);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false, true));
                world.spawnParticle(Particle.FLASH, target.getEyeLocation(), 2, 0.2, 0.2, 0.2, 0);
            }
            if (body instanceof org.bukkit.entity.Enderman enderman) {
                Location blink = at.clone().add(
                        ThreadLocalRandom.current().nextDouble(-6, 6),
                        0,
                        ThreadLocalRandom.current().nextDouble(-6, 6));
                blink.setY(at.getY());
                enderman.teleport(blink);
                world.playSound(blink, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }

    private void restoreNullHoles() {
        for (Map.Entry<Block, Material> entry : nullRestore.entrySet()) {
            Block block = entry.getKey();
            if (block != null && block.getType().isAir()) {
                block.setType(entry.getValue(), false);
            }
        }
        nullRestore.clear();
        nullHoles.clear();
    }

    // ── Load-bearing (click pillars — no mining) ──────────────

    private static final class PillarSite {
        final Location base;
        final List<Entity> visuals = new ArrayList<>();
        org.bukkit.entity.Interaction hitbox;
        TextDisplay label;
        int clicks;
        final int need = 3;
        boolean dead;

        PillarSite(Location base) {
            this.base = base.clone();
        }
    }

    private void buildPillars(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        pillars.clear();
        pillarSites.clear();
        clearProps();
        pillarsLeft = 4;
        loadPhase = 0;
        double[][] offsets = {{9, 9}, {9, -9}, {-9, 9}, {-9, -9}};
        Material[] skins = {
                Material.LODESTONE,
                Material.CRYING_OBSIDIAN,
                Material.RESPAWN_ANCHOR,
                Material.REINFORCED_DEEPSLATE
        };
        for (int i = 0; i < offsets.length; i++) {
            final int pillarIndex = i;
            double[] o = offsets[i];
            Location base = at.clone().add(o[0], 0, o[1]);
            base.setY(at.getY());
            PillarSite site = new PillarSite(base);
            // Visual stack via BlockDisplay — never place real blocks
            for (int y = 0; y < 5; y++) {
                Location blockAt = base.clone().add(0, y, 0);
                Material mat = y == 4 ? skins[i] : Material.POLISHED_DEEPSLATE;
                float scale = y == 4 ? 1.15f : 1.0f;
                org.bukkit.entity.BlockDisplay display = world.spawn(blockAt, org.bukkit.entity.BlockDisplay.class, d -> {
                    d.setBlock(mat.createBlockData());
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(-0.5f * scale, 0f, -0.5f * scale),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(scale, scale, scale),
                            new org.joml.Quaternionf()
                    ));
                    tagProp(d);
                });
                site.visuals.add(display);
                props.add(display);
            }
            Location mid = base.clone().add(0.5, 0.1, 0.5);
            org.bukkit.entity.Interaction hit = world.spawn(mid, org.bukkit.entity.Interaction.class, inter -> {
                inter.setInteractionWidth(1.9f);
                inter.setInteractionHeight(5.2f);
                inter.setResponsive(true);
                inter.setPersistent(false);
                tagProp(inter);
                inter.getPersistentDataContainer().set(
                        AetherKeys.namespaced("bossengine", "sandbox_pillar"),
                        PersistentDataType.BYTE,
                        (byte) 1
                );
                inter.getPersistentDataContainer().set(
                        AetherKeys.namespaced("bossengine", "sandbox_pillar_idx"),
                        PersistentDataType.INTEGER,
                        pillarIndex
                );
            });
            site.hitbox = hit;
            props.add(hit);

            TextDisplay label = world.spawn(base.clone().add(0.5, 5.35, 0.5), TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.CENTER);
                t.setSeeThrough(true);
                t.setShadowed(true);
                t.setPersistent(false);
                t.text(TextUtil.component("&e&lCLICK &7· &f3"));
                tagProp(t);
            });
            site.label = label;
            props.add(label);

            pillars.add(base.clone().add(0.5, 4.5, 0.5));
            pillarSites.add(site);
            world.spawnParticle(Particle.END_ROD, base.clone().add(0.5, 2.5, 0.5), 24, 0.35, 1.2, 0.35, 0.02);
            world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.6f + i * 0.1f);
        }
        shout("&7&lLOAD-BEARING &8— &fRight-click &7the &e4 pillars &7(&f3× &7each).");
        shout("&8Mining does nothing. The core is sealed until the load fails.");
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.55f, 0.45f);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 2, 0), 3, 0.4, 0.4, 0.4, 0);
    }

    private void tickLoadBearing(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (body != null) {
            // Anchor the core in place during pillar phase / awakening
            if (loadPhase < 2) {
                body.teleport(at);
                body.setVelocity(new Vector());
                body.setGravity(false);
                if (body instanceof Mob mob) {
                    mob.setAI(false);
                    mob.setAware(false);
                }
            }
        }

        if (loadPhase == 0) {
            tickLoadPillarPhase(world, at, body);
            return;
        }
        if (loadPhase == 1) {
            tickLoadAwaken(world, at, body);
            return;
        }
        tickLoadBerserk(world, at, body);
    }

    private void tickLoadPillarPhase(World world, Location at, LivingEntity body) {
        // Energy tethers + rotating halo
        if (ticks % 4 == 0) {
            for (PillarSite site : pillarSites) {
                if (site.dead) {
                    continue;
                }
                Location pillar = site.base.clone().add(0.5, 3.0, 0.5);
                drawBeamDust(world, pillar, at.clone().add(0, 1.6, 0),
                        site.clicks == 0 ? Particle.CRIT : Particle.FLAME);
            }
            double spin = ticks * 0.12;
            for (int i = 0; i < 18; i++) {
                double a = spin + (Math.PI * 2 * i) / 18.0;
                Location p = at.clone().add(Math.cos(a) * 3.4, 1.1, Math.sin(a) * 3.4);
                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 140, 160), 1.2f));
            }
        }
        // Soft pressure while sealed — reminds you to click pillars
        if (ticks % 55 == 0) {
            world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.7f, 0.55f);
            for (Player player : nearby(at, 18)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§7Core sealed · §eCLICK pillars §8(" + pillarsLeft + " left)"));
                Vector push = player.getLocation().toVector().subtract(at.toVector());
                if (push.lengthSquared() > 1) {
                    player.setVelocity(push.normalize().multiply(0.18).setY(0.12));
                }
            }
        }
        // Pulse from unfinished pillars
        if (ticks % 30 == 0) {
            for (PillarSite site : pillarSites) {
                if (site.dead) {
                    continue;
                }
                Location tip = site.base.clone().add(0.5, 5.0, 0.5);
                world.spawnParticle(Particle.ELECTRIC_SPARK, tip, 12, 0.35, 0.35, 0.35, 0.02);
                for (Player player : nearby(tip, 3.2)) {
                    player.damage(6, body);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                }
            }
        }
    }

    private void tickLoadAwaken(World world, Location at, LivingEntity body) {
        loadAwakenTicks++;
        double t = loadAwakenTicks / 60.0;
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1.2, 0), 1, 0.6, 0.4, 0.6, 0);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 1, 0.2, 0.2, 0.2, 0);
        ring(world, at.clone().add(0, 0.2, 0), Particle.SOUL_FIRE_FLAME, 1.5 + t * 6.0, 40);
        if (loadAwakenTicks % 8 == 0) {
            world.playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.1f, 0.5f + (float) t * 0.6f);
            world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.45f, 0.4f);
        }
        if (body != null) {
            AttributeUtil.setBase(body, AttributeUtil.scale(), 1.85 + t * 0.55);
            body.setGlowing(true);
        }
        for (Player player : nearby(at, 14)) {
            Vector away = player.getLocation().toVector().subtract(at.toVector());
            if (away.lengthSquared() > 0.2) {
                player.setVelocity(away.normalize().multiply(0.35 + t * 0.4).setY(0.25));
            }
        }
        if (loadAwakenTicks >= 60) {
            loadPhase = 2;
            loadAttackCd = 20;
            shout("&c&lSTRUCTURE FAILED &8— &fLoad-Bearing &cgoes critical.");
            world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.85f, 0.7f);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 3, 1.0, 0.5, 1.0, 0);
            if (body instanceof Mob mob) {
                mob.setAI(true);
                mob.setAware(true);
            }
            if (body != null) {
                body.setGravity(true);
            }
            CombatTheatrics.ringClimax(instance, at, 8.0);
        }
    }

    private void tickLoadBerserk(World world, Location at, LivingEntity body) {
        if (loadAttackCd > 0) {
            loadAttackCd--;
        }
        // Ambient overload
        if (ticks % 6 == 0) {
            world.spawnParticle(Particle.LAVA, at.clone().add(0, 1.2, 0), 4, 0.6, 0.5, 0.6, 0);
            world.spawnParticle(Particle.DUST, at.clone().add(0, 2.0, 0), 10, 1.2, 0.8, 1.2, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 80, 40), 1.5f));
        }
        double hpFrac = 1.0;
        if (body != null && body.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null) {
            hpFrac = body.getHealth() / body.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        int cadence = hpFrac < 0.35 ? 28 : (hpFrac < 0.65 ? 38 : 48);
        if (loadAttackCd <= 0) {
            loadAttackCd = cadence;
            loadPattern = (loadPattern + 1) % 6;
            switch (loadPattern) {
                case 0 -> loadShockwave(world, at, body);
                case 1 -> loadGhostBeams(world, at, body);
                case 2 -> loadSkyCrush(world, at, body);
                case 3 -> loadVacuumBoom(world, at, body);
                case 4 -> loadOrbitSaws(world, at, body);
                default -> loadMeteorRain(world, at, body);
            }
        }
        // Enrage aura
        if (hpFrac < 0.4 && ticks % 25 == 0) {
            ring(world, at.clone().add(0, 0.3, 0), Particle.FLAME, 4.5, 48);
            for (Player player : nearby(at, 5.0)) {
                player.damage(18, body);
                player.setFireTicks(40);
            }
        }
    }

    private void loadShockwave(World world, Location at, LivingEntity body) {
        shout("&7&lSEISMIC RING");
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.55f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        CombatTheatrics.slam(instance, at);
        for (int wave = 1; wave <= 5; wave++) {
            int w = wave;
            Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (!active()) {
                    return;
                }
                double r = w * 2.2;
                ring(world, at.clone().add(0, 0.15, 0), Particle.CLOUD, r, 48);
                ring(world, at.clone().add(0, 0.4, 0), Particle.CRIT, r, 36);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.5f + w * 0.1f);
                for (Player player : nearby(at, r + 0.8)) {
                    if (player.getLocation().distance(at) < r - 1.4) {
                        continue;
                    }
                    player.damage(16 + w * 3, body);
                    Vector away = player.getLocation().toVector().subtract(at.toVector()).normalize();
                    player.setVelocity(away.multiply(0.9).setY(0.45));
                }
            }, w * 4L);
        }
    }

    private void loadGhostBeams(World world, Location at, LivingEntity body) {
        shout("&7&lGHOST LOAD LINES");
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.55f);
        List<Location> origins = new ArrayList<>(pillars);
        if (origins.isEmpty()) {
            origins.add(at.clone().add(8, 0, 8));
            origins.add(at.clone().add(-8, 0, 8));
        }
        for (Location origin : origins) {
            Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (!active()) {
                    return;
                }
                Player target = nearestPlayer(at, 28);
                Location aim = target != null ? target.getLocation().add(0, 1, 0) : at.clone().add(0, 1, 12);
                for (int step = 0; step < 28; step++) {
                    Location c = origin.clone().add(0, 4, 0);
                    Vector dir = aim.toVector().subtract(c.toVector()).normalize();
                    Location p = c.add(dir.multiply(step * 0.55));
                    world.spawnParticle(Particle.END_ROD, p, 2, 0.05, 0.05, 0.05, 0);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 60, 40), 1.4f));
                    for (Player player : nearby(p, 1.3)) {
                        player.damage(22, body);
                        player.setVelocity(dir.clone().multiply(0.55).setY(0.2));
                    }
                }
                world.playSound(aim, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.45f, 1.4f);
            }, ThreadLocalRandom.current().nextInt(1, 8));
        }
    }

    private void loadSkyCrush(World world, Location at, LivingEntity body) {
        shout("&7&lSKY ANVIL");
        Player target = nearestPlayer(at, 26);
        Location mark = target != null ? target.getLocation().clone() : at.clone().add(4, 0, 0);
        mark.setY(at.getY());
        for (int i = 0; i < 20; i++) {
            double a = (Math.PI * 2 * i) / 20.0;
            world.spawnParticle(Particle.DUST, mark.clone().add(Math.cos(a) * 2.2, 0.1, Math.sin(a) * 2.2),
                    1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 40), 1.3f));
        }
        world.playSound(mark, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.5f);
        Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (!active()) {
                return;
            }
            world.spawnParticle(Particle.EXPLOSION_EMITTER, mark.clone().add(0, 0.5, 0), 2, 0.4, 0.2, 0.4, 0);
            world.spawnParticle(Particle.CLOUD, mark.clone().add(0, 0.5, 0), 30, 1.2, 0.4, 1.2, 0.05);
            world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.65f);
            world.playSound(mark, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.55f);
            spawnVisualDebris(world, mark, Material.POLISHED_DEEPSLATE, 14);
            for (Player player : nearby(mark, 3.8)) {
                player.damage(38, body);
                player.setVelocity(new Vector(0, 1.1, 0));
            }
        }, 28L);
    }

    private void loadVacuumBoom(World world, Location at, LivingEntity body) {
        shout("&7&lLOAD COLLAPSE");
        world.playSound(at, Sound.BLOCK_PORTAL_TRIGGER, 0.9f, 0.55f);
        for (int i = 1; i <= 18; i++) {
            int step = i;
            Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (!active()) {
                    return;
                }
                world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1.2, 0), 18, 1.5, 1.0, 1.5, 0.15);
                for (Player player : nearby(at, 16)) {
                    Vector to = at.toVector().subtract(player.getLocation().toVector());
                    if (to.lengthSquared() > 0.4) {
                        player.setVelocity(to.normalize().multiply(0.22 + step * 0.01).setY(0.08));
                    }
                }
                if (step == 18) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 4, 1.0, 0.5, 1.0, 0);
                    world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.4, 0), 5, 0.6, 0.4, 0.6, 0);
                    world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.35f, 0.45f);
                    world.playSound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 0.6f);
                    spawnVisualDebris(world, at.clone().add(0, 1, 0), Material.OBSIDIAN, 20);
                    for (Player player : nearby(at, 7.5)) {
                        player.damage(45, body);
                        Vector away = player.getLocation().toVector().subtract(at.toVector());
                        if (away.lengthSquared() < 0.1) {
                            away = new Vector(0, 1, 0);
                        }
                        player.setVelocity(away.normalize().multiply(2.0).setY(1.15));
                    }
                }
            }, i * 2L);
        }
    }

    private void loadOrbitSaws(World world, Location at, LivingEntity body) {
        shout("&7&lORBIT SAWS");
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.45f);
        for (int blade = 0; blade < 3; blade++) {
            int b = blade;
            for (int t = 0; t < 24; t++) {
                int tick = t;
                Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                    if (!active()) {
                        return;
                    }
                    double a = ticks * 0.2 + b * (Math.PI * 2 / 3.0) + tick * 0.28;
                    double r = 3.0 + (tick % 8) * 0.35;
                    Location p = at.clone().add(Math.cos(a) * r, 1.0, Math.sin(a) * r);
                    world.spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.CRIT, p, 4, 0.1, 0.1, 0.1, 0.01);
                    for (Player player : nearby(p, 1.35)) {
                        player.damage(14, body);
                    }
                }, tick);
            }
        }
    }

    private void loadMeteorRain(World world, Location at, LivingEntity body) {
        shout("&7&lDEBRIS RAIN");
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.6f);
        for (int i = 0; i < 7; i++) {
            int delay = i * 6;
            Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (!active()) {
                    return;
                }
                Player target = nearestPlayer(at, 24);
                Location mark = target != null
                        ? target.getLocation().clone()
                        : at.clone().add(ThreadLocalRandom.current().nextDouble(-8, 8), 0,
                        ThreadLocalRandom.current().nextDouble(-8, 8));
                mark.setY(at.getY());
                world.spawnParticle(Particle.FLAME, mark.clone().add(0, 8, 0), 10, 0.2, 0.2, 0.2, 0.01);
                Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                    if (!active()) {
                        return;
                    }
                    world.spawnParticle(Particle.EXPLOSION, mark.clone().add(0, 0.4, 0), 2, 0.3, 0.2, 0.3, 0);
                    world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.9f);
                    spawnVisualDebris(world, mark, Material.MAGMA_BLOCK, 8);
                    for (Player player : nearby(mark, 2.8)) {
                        player.damage(26, body);
                        player.setFireTicks(50);
                    }
                }, 12L);
            }, delay);
        }
    }

    private void spawnVisualDebris(World world, Location at, Material mat, int count) {
        for (int i = 0; i < count; i++) {
            org.bukkit.entity.FallingBlock fb = world.spawnFallingBlock(at.clone().add(0, 1.2, 0), mat.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            try {
                fb.setCancelDrop(true);
            } catch (Throwable ignored) {
            }
            fb.setPersistent(false);
            fb.getPersistentDataContainer().set(
                    AetherKeys.namespaced("bossengine", "sandbox_debris"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            fb.setVelocity(new Vector(
                    ThreadLocalRandom.current().nextDouble(-0.9, 0.9),
                    ThreadLocalRandom.current().nextDouble(0.4, 1.1),
                    ThreadLocalRandom.current().nextDouble(-0.9, 0.9)
            ));
            props.add(fb);
            Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (fb.isValid()) {
                    fb.remove();
                }
            }, 30L + ThreadLocalRandom.current().nextInt(15));
        }
    }

    private static void drawBeamDust(World world, Location from, Location to, Particle particle) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.2) {
            return;
        }
        Vector step = delta.normalize().multiply(0.55);
        Location c = from.clone();
        int n = Math.min(40, (int) (len / 0.55));
        for (int i = 0; i < n; i++) {
            c.add(step);
            world.spawnParticle(particle, c, 1, 0, 0, 0, 0);
        }
    }

    private Player nearestPlayer(Location at, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : nearby(at, range)) {
            double d = player.getLocation().distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    public boolean isPillar(Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(AetherKeys.namespaced("bossengine", "sandbox_pillar"), PersistentDataType.BYTE);
    }

    /** Right-click / punch a pillar Interaction — no block breaking. */
    public void onPillarClick(Player player, Entity clicked) {
        if (!active() || kind != Kind.LOADBEARING || loadPhase != 0 || player == null || clicked == null) {
            return;
        }
        Integer idx = clicked.getPersistentDataContainer().get(
                AetherKeys.namespaced("bossengine", "sandbox_pillar_idx"),
                PersistentDataType.INTEGER
        );
        PillarSite site = null;
        if (idx != null && idx >= 0 && idx < pillarSites.size()) {
            site = pillarSites.get(idx);
        } else {
            for (PillarSite candidate : pillarSites) {
                if (candidate.hitbox != null && candidate.hitbox.getUniqueId().equals(clicked.getUniqueId())) {
                    site = candidate;
                    break;
                }
            }
        }
        if (site == null || site.dead) {
            return;
        }
        site.clicks++;
        World world = clicked.getWorld();
        Location tip = site.base.clone().add(0.5, 4.5, 0.5);
        if (world != null) {
            world.playSound(tip, Sound.BLOCK_ANVIL_PLACE, 0.85f, 0.7f + site.clicks * 0.15f);
            world.playSound(tip, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.1f);
            world.spawnParticle(Particle.CRIT, tip, 20, 0.4, 0.5, 0.4, 0.05);
            world.spawnParticle(Particle.FLASH, tip, 1, 0, 0, 0, 0);
        }
        int left = site.need - site.clicks;
        if (site.label != null && site.label.isValid()) {
            if (left > 0) {
                site.label.text(TextUtil.component("&e&lCLICK &7· &f" + left));
            } else {
                site.label.text(TextUtil.component("&c&lBREAKING"));
            }
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7Pillar load §f" + site.clicks + "§8/§f" + site.need));
        if (site.clicks < site.need) {
            return;
        }
        collapsePillar(site);
    }

    /** @deprecated punch path still routes here */
    public void onPillarBroken(Location at) {
        // Legacy: find nearest living pillar and force-complete it
        PillarSite best = null;
        double bestDist = 16;
        for (PillarSite site : pillarSites) {
            if (site.dead) {
                continue;
            }
            double d = site.base.distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = site;
            }
        }
        if (best != null) {
            best.clicks = best.need;
            collapsePillar(best);
        }
    }

    private void collapsePillar(PillarSite site) {
        if (site.dead) {
            return;
        }
        site.dead = true;
        pillarsLeft = Math.max(0, pillarsLeft - 1);
        Location tip = site.base.clone().add(0.5, 3.0, 0.5);
        World world = tip.getWorld();
        if (world != null) {
            world.playSound(tip, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.3f, 0.55f);
            world.playSound(tip, Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 0.7f);
            world.playSound(tip, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.6f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, tip, 2, 0.4, 0.6, 0.4, 0);
            world.spawnParticle(Particle.CLOUD, tip, 40, 0.8, 1.2, 0.8, 0.04);
            spawnVisualDebris(world, tip, Material.POLISHED_DEEPSLATE, 16);
            for (Player player : nearby(tip, 5.0)) {
                player.damage(20, instance.getEntity());
                Vector away = player.getLocation().toVector().subtract(tip.toVector());
                if (away.lengthSquared() > 0.1) {
                    player.setVelocity(away.normalize().multiply(1.1).setY(0.55));
                }
            }
        }
        for (Entity visual : site.visuals) {
            if (visual != null && visual.isValid()) {
                visual.remove();
            }
        }
        site.visuals.clear();
        if (site.hitbox != null && site.hitbox.isValid()) {
            site.hitbox.remove();
        }
        if (site.label != null && site.label.isValid()) {
            site.label.remove();
        }
        pillars.removeIf(p -> p.distanceSquared(site.base.clone().add(0.5, 4.5, 0.5)) < 4);
        shout("&7Pillar collapsed · remaining &f" + pillarsLeft);
        if (pillarsLeft <= 0) {
            loadPhase = 1;
            loadAwakenTicks = 0;
            shout("&c&lALL PILLARS DOWN &8— &7the load transfers to &fYOU&7.");
            LivingEntity body = instance.getEntity();
            if (body != null) {
                body.setGlowing(true);
                World w = body.getWorld();
                w.playSound(body.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.85f);
                w.playSound(body.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1.1f, 0.6f);
            }
        }
    }

    // ── Softlock ──────────────────────────────────────────────

    private void tickSoftlock(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks % 10 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1.2, 0), Color.fromRGB(220, 40, 80), 1.5, 18, 1.2f);
            world.spawnParticle(Particle.DRAGON_BREATH, at.clone().add(0, 1.4, 0), 8, 0.5, 0.5, 0.5, 0.01);
        }
        if (ticks % 110 == 0) {
            shout("&c&lHOTBAR SEAL");
            world.playSound(at, Sound.BLOCK_CHEST_LOCKED, 1.2f, 0.55f);
            world.playSound(at, Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 0.7f);
            int seals = SandboxFx.enraged(body) ? 2 : 1;
            for (Player player : nearby(at, 26)) {
                for (int s = 0; s < seals; s++) {
                    int slot = ThreadLocalRandom.current().nextInt(9);
                    softSlots.put(player.getUniqueId(), slot);
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§c§lSEALED §fslot " + (slot + 1) + " §7(6s)"));
                    world.spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 22, 0.4, 0.5, 0.4, 0.02);
                    TextDisplay lockFx = world.spawn(player.getLocation().add(0, 2.2, 0), TextDisplay.class, d -> {
                        d.text(net.kyori.adventure.text.Component.text("§c§lSEAL " + (slot + 1)));
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setSeeThrough(true);
                        tagProp(d);
                    });
                    props.add(lockFx);
                    SandboxFx.later(instance, 25L, () -> {
                        if (lockFx.isValid()) {
                            lockFx.remove();
                        }
                    });
                }
                SandboxFx.later(instance, 120L, () -> softSlots.remove(player.getUniqueId()));
            }
        }
        for (Map.Entry<UUID, Integer> entry : softSlots.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (player.getInventory().getHeldItemSlot() == entry.getValue()) {
                if (hasCharm(player, "softlock_plate")) {
                    continue;
                }
                int next = (entry.getValue() + 1) % 9;
                player.getInventory().setHeldItemSlot(next);
                player.damage(5, body);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.85f, 0.45f);
                world.spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.05);
            }
        }
        if (softBurstCd > 0) {
            softBurstCd--;
        }
        if (softBurstCd <= 0) {
            softBurstCd = SandboxFx.enraged(body) ? 70 : 95;
            showPattern = (showPattern + 1) % 3;
            switch (showPattern) {
                case 0 -> softCageBurst(world, at, body);
                case 1 -> softBoltBarrage(world, at, body);
                default -> softLockdownSlam(world, at, body);
            }
        }
    }

    private void softCageBurst(World world, Location at, LivingEntity body) {
        shout("&c&lCAGE BURST");
        world.playSound(at, Sound.ENTITY_SHULKER_SHOOT, 1.1f, 0.7f);
        Player target = SandboxFx.nearest(at, 24);
        Location center = target != null ? target.getLocation() : at.clone().add(4, 0, 0);
        center.setY(at.getY());
        SandboxFx.dustRing(world, center.clone().add(0, 0.1, 0), Color.fromRGB(255, 60, 100), 3.2, 36, 1.5f);
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4.0);
            Location wall = center.clone().add(Math.cos(a) * 3.0, 0.1, Math.sin(a) * 3.0);
            org.bukkit.entity.BlockDisplay plate = world.spawn(wall, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
                d.setPersistent(false);
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(-0.4f, 0f, -0.1f),
                        new org.joml.Quaternionf(),
                        new org.joml.Vector3f(0.8f, 2.4f, 0.2f),
                        new org.joml.Quaternionf()
                ));
                tagProp(d);
            });
            props.add(plate);
            SandboxFx.later(instance, 45L, () -> {
                if (plate.isValid()) {
                    plate.remove();
                }
            });
        }
        SandboxFx.later(instance, 18L, () -> {
            world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 2, 0.4, 0.3, 0.4, 0);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 1.15f);
            for (Player player : nearby(center, 3.4)) {
                player.damage(24, body);
                SandboxFx.flingOut(center, List.of(player), 1.1, 0.55);
            }
            SandboxFx.visualDebris(instance, props, world, center, Material.PURPUR_BLOCK, 8);
        });
    }

    private void softBoltBarrage(World world, Location at, LivingEntity body) {
        shout("&c&lSHULKER BARRAGE");
        world.playSound(at, Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.2f);
        for (int i = 0; i < 10; i++) {
            int shot = i;
            SandboxFx.later(instance, shot * 3L, () -> {
                Player target = SandboxFx.nearest(at, 26);
                if (target == null) {
                    return;
                }
                Location tip = target.getEyeLocation();
                SandboxFx.dustBeam(world, at.clone().add(0, 1.5, 0), tip, Color.fromRGB(180, 40, 220), 1.6f);
                world.spawnParticle(Particle.END_ROD, tip, 6, 0.15, 0.15, 0.15, 0.02);
                target.damage(12, body);
                if (shot % 3 == 0) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 0, true, false, true));
                }
            });
        }
    }

    private void softLockdownSlam(World world, Location at, LivingEntity body) {
        shout("&c&lLOCKDOWN");
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.55f);
        SandboxFx.expandingRings(instance, world, at, body, 4, 2.1, 16, Particle.DRAGON_BREATH);
        for (Player player : nearby(at, 16)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, false, true));
        }
    }

    // ── Heartbeat ─────────────────────────────────────────────

    private void tickHeartbeat(World world, Location at) {
        LivingEntity body = instance.getEntity();
        double hp = SandboxFx.hpFrac(body);
        int cycle = hp < 0.25 ? 28 : (hp < 0.5 ? 36 : 50);
        int windowStart = cycle - Math.max(8, cycle / 4);
        beatPhase = (beatPhase + 1) % cycle;
        boolean window = beatPhase >= windowStart;

        if (ticks % 8 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1.1, 0), Color.fromRGB(255, 60, 90), 1.3, 16, 1.1f);
        }

        if (beatPhase == windowStart) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.4f, 0.65f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.5f);
            SandboxFx.ring(world, at.clone().add(0, 1, 0), Particle.HEART, 2.4, 32);
            for (Player player : nearby(at, 26)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("§a§lBEAT — STRIKE NOW"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.6f);
            }
            SandboxFx.expandingRings(instance, world, at, body, 2, 2.4, 10, Particle.HEART);
        } else if (beatPhase == 0) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.55f, 0.45f);
            for (Player player : nearby(at, 4.5)) {
                if (player.getVelocity().lengthSquared() < 0.01) {
                    player.damage(8, body);
                    player.sendActionBar(net.kyori.adventure.text.Component.text("§cResting on the off-beat…"));
                }
            }
        }

        if (window && ticks % 3 == 0) {
            world.spawnParticle(Particle.HEART, at.clone().add(0, 1.8, 0), 4, 0.5, 0.4, 0.5, 0);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, at.clone().add(0, 1.2, 0), 2, 0.3, 0.2, 0.3, 0);
        }

        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 80 : 110;
            showPattern = (showPattern + 1) % 3;
            if (showPattern == 0) {
                shout("&c&lARRHYTHMIA");
                world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                for (int i = 0; i < 5; i++) {
                    int delay = i * 8;
                    SandboxFx.later(instance, delay, () -> {
                        Player t = SandboxFx.nearest(at, 24);
                        Location mark = t != null ? t.getLocation() : at.clone().add(5, 0, 0);
                        mark.setY(at.getY());
                        SandboxFx.markedSlam(instance, world, mark, body, 2.8, 26, 12L, Particle.HEART, m ->
                                SandboxFx.visualDebris(instance, props, world, m, Material.REDSTONE_BLOCK, 6));
                    });
                }
            } else if (showPattern == 1) {
                shout("&c&lFIBRILLATION");
                world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.4f);
                for (Player player : nearby(at, 20)) {
                    SandboxFx.dustBeam(world, at.clone().add(0, 1.4, 0), player.getEyeLocation(),
                            Color.fromRGB(255, 40, 70), 1.5f);
                    player.damage(14, body);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false, true));
                }
            } else {
                shout("&c&lCARDIAC WAVE");
                SandboxFx.expandingRings(instance, world, at, body, 5, 1.9, 14, Particle.DAMAGE_INDICATOR);
            }
        }
    }

    private double heartbeatDamage(Player player, double damage) {
        LivingEntity body = instance.getEntity();
        double hp = SandboxFx.hpFrac(body);
        int cycle = hp < 0.25 ? 28 : (hp < 0.5 ? 36 : 50);
        int windowStart = cycle - Math.max(8, cycle / 4);
        boolean window = beatPhase >= windowStart;
        boolean metro = hasCharm(player, "metronome_bow") || hasItemName(player, "Metronome");
        if (window || metro) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.85f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 4, 0.2, 0.2, 0.2, 0);
            return damage * (window ? 1.75 : 1.2);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.45f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cOff-beat — weak hit"));
        return damage * 0.18;
    }

    // ── Broker ────────────────────────────────────────────────

    private void tickBroker(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks % 10 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1, 0), Color.fromRGB(255, 200, 40), 1.4, 16, 1.15f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0);
        }
        if (!brokerOpen && ticks > 40 && ticks % 140 == 0) {
            brokerOpen = true;
            brokerUntil = ticks + (SandboxFx.enraged(body) ? 70 : 90);
            showPattern = (showPattern + 1) % 3;
            String deal = switch (showPattern) {
                case 1 -> "&eDeal B: &7Pay §c20 HP §7→ §aSpeed II + Jump (7s). &fSneak.";
                case 2 -> "&eDeal C: &7Pay §c25% HP §7→ §aInvuln frames (3s) then §cTax Wave. &fSneak.";
                default -> "&eDeal A: &7Pay §c15% HP §7→ §a+Strength II (9s). &fSneak.";
            };
            shout(deal);
            world.playSound(at, Sound.ENTITY_VILLAGER_TRADE, 1.3f, 1.05f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
            SandboxFx.dustRing(world, at.clone().add(0, 0.2, 0), Color.fromRGB(255, 220, 60), 4.5, 40, 1.6f);
            TextDisplay offer = world.spawn(at.clone().add(0, 2.6, 0), TextDisplay.class, d -> {
                d.text(net.kyori.adventure.text.Component.text("§e§lDEAL OPEN — SNEAK"));
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                tagProp(d);
            });
            props.add(offer);
            SandboxFx.later(instance, 90L, () -> {
                if (offer.isValid()) {
                    offer.remove();
                }
            });
        }
        if (brokerOpen) {
            for (Player player : nearby(at, 12)) {
                if (player.isSneaking()) {
                    acceptDeal(player);
                }
            }
            if (ticks % 5 == 0) {
                SandboxFx.dustRing(world, at.clone().add(0, 0.15, 0), Color.fromRGB(255, 180, 20), 5.0, 28, 1.3f);
            }
            if (ticks >= brokerUntil) {
                brokerOpen = false;
                shout("&e&lDEAL EXPIRED — COLLECTION");
                world.playSound(at, Sound.ENTITY_VILLAGER_NO, 1.1f, 0.6f);
                SandboxFx.expandingRings(instance, world, at, body, 4, 2.0, 18, Particle.CRIT);
                for (Player player : nearby(at, 16)) {
                    player.damage(16, body);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 80, 1, true, false, true));
                }
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (!brokerOpen && showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 85 : 115;
            shout("&e&lINTEREST CHARGE");
            world.playSound(at, Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
            Player target = SandboxFx.nearest(at, 26);
            Location mark = target != null ? target.getLocation() : at.clone().add(5, 0, 0);
            mark.setY(at.getY());
            SandboxFx.markedSlam(instance, world, mark, body, 3.2, 28, 20L, Particle.HAPPY_VILLAGER, m -> {
                SandboxFx.visualDebris(instance, props, world, m, Material.GOLD_BLOCK, 8);
                world.spawnParticle(Particle.FLASH, m.clone().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
            });
            for (int i = 0; i < 12; i++) {
                Location coin = at.clone().add(
                        ThreadLocalRandom.current().nextDouble(-5, 5),
                        3 + ThreadLocalRandom.current().nextDouble(2),
                        ThreadLocalRandom.current().nextDouble(-5, 5));
                world.spawnParticle(Particle.BLOCK, coin, 4, 0.1, 0.1, 0.1, 0.02, Material.GOLD_BLOCK.createBlockData());
            }
        }
    }

    private void acceptDeal(Player player) {
        if (!brokerOpen) {
            return;
        }
        brokerOpen = false;
        LivingEntity body = instance.getEntity();
        double hp = player.getHealth();
        switch (showPattern % 3) {
            case 1 -> {
                player.setHealth(Math.max(1.0, hp - 20));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 140, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 140, 1, true, true, true));
                player.sendMessage("§eBroker §7Deal B accepted — mobility.");
            }
            case 2 -> {
                player.setHealth(Math.max(1.0, hp * 0.75));
                player.setNoDamageTicks(60);
                player.sendMessage("§eBroker §7Deal C accepted — brief invuln… tax incoming.");
                SandboxFx.later(instance, 65L, () -> {
                    if (body != null && body.isValid()) {
                        shout("&e&lTAX WAVE");
                        World w = body.getWorld();
                        SandboxFx.expandingRings(instance, w, body.getLocation(), body, 4, 2.2, 16, Particle.CRIT);
                    }
                });
            }
            default -> {
                player.setHealth(Math.max(1.0, hp * 0.85));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 180, 1, true, true, true));
                player.sendMessage("§eBroker §7Deal A accepted — strength.");
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.85f, 1.35f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 28, 0.45, 0.7, 0.45, 0.12);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.2, 0), 2, 0.2, 0.2, 0.2, 0);
    }

    // ── Afterimage ────────────────────────────────────────────

    private void spawnAfterimage(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        clearAfterFakes();
        LivingEntity body = instance.getEntity();
        if (body != null) {
            body.setInvisible(true);
            body.setGlowing(false);
            afterReveal = false;
        }
        int count = 3;
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count;
            Location spawn = at.clone().add(Math.cos(a) * 4.0, 1.1, Math.sin(a) * 4.0);
            LivingEntity fake = (LivingEntity) world.spawnEntity(spawn, EntityType.PHANTOM);
            fake.customName(TextUtil.component("&8Afterimage"));
            fake.setCustomNameVisible(true);
            fake.setAI(false);
            fake.setGravity(false);
            fake.setInvulnerable(true);
            fake.setSilent(true);
            tagProp(fake);
            props.add(fake);
            afterFakes.add(fake);
            if (i == 0) {
                afterFake = fake;
            }
        }
    }

    private void clearAfterFakes() {
        for (LivingEntity fake : afterFakes) {
            if (fake != null && fake.isValid()) {
                fake.remove();
            }
        }
        afterFakes.clear();
        if (afterFake != null && afterFake.isValid() && !props.contains(afterFake)) {
            afterFake.remove();
        }
        afterFake = null;
    }

    private void tickAfterimage(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (afterFakes.isEmpty() || afterFakes.stream().noneMatch(Entity::isValid)) {
            spawnAfterimage(at);
        }
        int n = Math.max(1, afterFakes.size());
        for (int i = 0; i < afterFakes.size(); i++) {
            LivingEntity fake = afterFakes.get(i);
            if (fake == null || !fake.isValid()) {
                continue;
            }
            double a = ticks * 0.11 + (Math.PI * 2 * i) / n;
            double r = SandboxFx.enraged(body) ? 5.2 : 4.0;
            Location orbit = at.clone().add(Math.cos(a) * r, 1.0 + Math.sin(ticks * 0.08 + i) * 0.4, Math.sin(a) * r);
            fake.teleport(orbit);
            world.spawnParticle(Particle.SMOKE, orbit, 3, 0.15, 0.15, 0.15, 0.01);
            world.spawnParticle(Particle.SCULK_SOUL, orbit, 1, 0.1, 0.1, 0.1, 0);
        }
        if (ticks % 8 == 0 && !afterReveal) {
            SandboxFx.dustRing(world, at.clone().add(0, 0.3, 0), Color.fromRGB(40, 40, 50), 1.2, 14, 0.9f);
        }

        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 75 : 100;
            showPattern = (showPattern + 1) % 4;
            switch (showPattern) {
                case 0 -> afterRevealWindow(world, at, body);
                case 1 -> afterSwapStorm(world, at, body);
                case 2 -> afterPhantomDive(world, at, body);
                default -> afterSmokeBomb(world, at, body);
            }
        }
    }

    private void afterRevealWindow(World world, Location at, LivingEntity body) {
        afterReveal = true;
        if (body != null) {
            body.setInvisible(false);
            body.setGlowing(true);
        }
        shout("&8&lTRUE FORM");
        world.playSound(at, Sound.ENTITY_PHANTOM_FLAP, 1.3f, 0.65f);
        world.playSound(at, Sound.BLOCK_BELL_USE, 0.6f, 1.5f);
        SandboxFx.dustRing(world, at.clone().add(0, 1, 0), Color.fromRGB(220, 220, 255), 2.2, 36, 1.5f);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.5, 0), 30, 0.6, 0.8, 0.6, 0.05);
        SandboxFx.later(instance, SandboxFx.enraged(body) ? 45L : 38L, () -> {
            afterReveal = false;
            LivingEntity b = instance.getEntity();
            if (b != null && b.isValid()) {
                b.setInvisible(true);
                b.setGlowing(false);
            }
        });
    }

    private void afterSwapStorm(World world, Location at, LivingEntity body) {
        shout("&8&lSWAP STORM");
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        for (Player player : nearby(at, 22)) {
            LivingEntity fake = afterFakes.isEmpty() ? null
                    : afterFakes.get(ThreadLocalRandom.current().nextInt(afterFakes.size()));
            if (fake == null || !fake.isValid()) {
                continue;
            }
            Location dest = fake.getLocation().clone();
            SandboxFx.dustBeam(world, player.getLocation().add(0, 1, 0), dest.clone().add(0, 1, 0),
                    Color.fromRGB(80, 80, 100), 1.3f);
            player.teleport(dest);
            player.damage(10, body);
            world.spawnParticle(Particle.PORTAL, dest, 20, 0.3, 0.5, 0.3, 0.2);
        }
        spawnAfterimage(at);
    }

    private void afterPhantomDive(World world, Location at, LivingEntity body) {
        shout("&8&lPHANTOM DIVE");
        world.playSound(at, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);
        for (LivingEntity fake : afterFakes) {
            if (fake == null || !fake.isValid()) {
                continue;
            }
            Player target = SandboxFx.nearest(fake.getLocation(), 18);
            if (target == null) {
                continue;
            }
            Location mark = target.getLocation().clone();
            mark.setY(at.getY());
            SandboxFx.markedSlam(instance, world, mark, body, 2.6, 22, 16L, Particle.SMOKE, m -> {
                world.spawnParticle(Particle.SCULK_SOUL, m, 16, 0.4, 0.3, 0.4, 0.02);
            });
        }
    }

    private void afterSmokeBomb(World world, Location at, LivingEntity body) {
        shout("&8&lSMOKE BOMB");
        world.playSound(at, Sound.ENTITY_WITCH_THROW, 0.9f, 0.6f);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, 1, 0), 60, 2.5, 1.2, 2.5, 0.02);
        for (Player player : nearby(at, 14)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 35, 0, true, false, true));
            player.damage(8, body);
        }
        if (body != null) {
            Location blink = at.clone().add(
                    ThreadLocalRandom.current().nextDouble(-7, 7),
                    0,
                    ThreadLocalRandom.current().nextDouble(-7, 7));
            blink.setY(at.getY());
            body.teleport(blink);
            spawnAfterimage(blink);
        }
    }

    private double afterimageDamage(Player player, double damage) {
        if (afterReveal) {
            player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0);
            return damage * 1.15;
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("§8Afterimage — wait for the true form"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
        return damage * 0.06;
    }

    // ── Gravity ───────────────────────────────────────────────

    private void tickGravity(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks % 8 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 1.1, 0), Color.fromRGB(60, 140, 220), 1.6, 20, 1.2f);
            world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1.5, 0), 8, 0.5, 0.6, 0.5, 0.05);
        }
        if (ticks % 90 == 0) {
            gravityMode = (gravityMode + 1) % 4;
            String label = switch (gravityMode) {
                case 1 -> "CEILING PULL";
                case 2 -> "LATERAL SHEAR";
                case 3 -> "CRUSH WELL";
                default -> "NORMAL";
            };
            shout("&3&lGRAVITY: &f" + label);
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.35f);
            world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 0.8f);
            SandboxFx.dustRing(world, at.clone().add(0, 0.2, 0), Color.fromRGB(40, 120, 255), 3.5, 40, 1.6f);
            SandboxFx.visualDebris(instance, props, world, at, Material.CRYING_OBSIDIAN, 6);
        }
        Vector force = switch (gravityMode) {
            case 1 -> new Vector(0, 0.38, 0);
            case 2 -> new Vector(Math.sin(ticks * 0.22) * 0.34, 0.04, Math.cos(ticks * 0.22) * 0.34);
            default -> null;
        };
        if (gravityMode == 3) {
            SandboxFx.pullIn(at, nearby(at, 16), 0.28);
            if (ticks % 4 == 0) {
                SandboxFx.dustRing(world, at.clone().add(0, 0.1, 0), Color.fromRGB(20, 40, 120), 4.0, 28, 1.3f);
            }
            if (ticks % 20 == 0) {
                for (Player player : nearby(at, 5)) {
                    player.damage(10, body);
                    player.setVelocity(new Vector(0, -0.6, 0));
                }
            }
        } else if (force != null) {
            for (Player player : nearby(at, 20)) {
                if (hasCharm(player, "nullstep_boots")) {
                    world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.2, 0), 2, 0.1, 0.1, 0.1, 0);
                    continue;
                }
                player.setVelocity(player.getVelocity().multiply(0.55).add(force));
                if (ticks % 4 == 0) {
                    world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 4, 0.12, 0.12, 0.12, 0);
                }
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 70 : 95;
            showPattern = (showPattern + 1) % 3;
            if (showPattern == 0) {
                shout("&3&lSINGULARITY");
                world.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, 0.55f, 1.6f);
                SandboxFx.pullIn(at, nearby(at, 22), 0.55);
                SandboxFx.later(instance, 18L, () -> {
                    world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 3, 0.5, 0.4, 0.5, 0);
                    world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
                    SandboxFx.flingOut(at, nearby(at, 14), 1.45, 0.85);
                    for (Player player : nearby(at, 10)) {
                        player.damage(26, body);
                    }
                    SandboxFx.visualDebris(instance, props, world, at, Material.OBSIDIAN, 12);
                });
            } else if (showPattern == 1) {
                shout("&3&lORBITAL SLAM");
                for (int i = 0; i < 4; i++) {
                    double a = i * (Math.PI / 2) + ticks * 0.05;
                    Location mark = at.clone().add(Math.cos(a) * 7, 0, Math.sin(a) * 7);
                    SandboxFx.markedSlam(instance, world, mark, body, 2.8, 24, 16L + i * 4L,
                            Particle.REVERSE_PORTAL, m ->
                                    SandboxFx.visualDebris(instance, props, world, m, Material.CYAN_CONCRETE, 5));
                }
            } else {
                shout("&3&lGRAVITY LANCE");
                Player target = SandboxFx.nearest(at, 28);
                if (target != null) {
                    SandboxFx.dustBeam(world, at.clone().add(0, 1.8, 0), target.getEyeLocation(),
                            Color.fromRGB(80, 180, 255), 2.0f);
                    target.damage(30, body);
                    target.setVelocity(new Vector(0, -1.1, 0));
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
                }
            }
        }
    }

    // ── Quiet Room ────────────────────────────────────────────

    private void tickQuiet(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (body != null) {
            body.setSilent(true);
        }
        if (ticks % 8 == 0) {
            world.spawnParticle(Particle.MYCELIUM, at.clone().add(0, 0.08, 0), 10, 2.0, 0.04, 2.0, 0);
            world.spawnParticle(Particle.SCULK_CHARGE_POP, at.clone().add(0, 0.2, 0), 2, 0.8, 0.1, 0.8, 0);
        }
        if (ticks % 40 == 0) {
            for (Player player : nearby(at, 18)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 35, 0, true, false, false));
                player.sendActionBar(net.kyori.adventure.text.Component.text("§8… silence …"));
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 50 : 70;
            showPattern = (showPattern + 1) % 4;
            switch (showPattern) {
                case 0 -> quietFloorSlam(world, at, body);
                case 1 -> quietFanBlades(world, at, body);
                case 2 -> quietChaseMarks(world, at, body);
                default -> quietCollapse(world, at, body);
            }
        }
    }

    private void quietFloorSlam(World world, Location at, LivingEntity body) {
        double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        Location mark = at.clone().add(Math.cos(ang) * (4 + ThreadLocalRandom.current().nextDouble() * 6), 0.05,
                Math.sin(ang) * (4 + ThreadLocalRandom.current().nextDouble() * 6));
        for (int i = 0; i < 28; i++) {
            double a = (Math.PI * 2 * i) / 28.0;
            Location p = mark.clone().add(Math.cos(a) * 2.6, 0.05, Math.sin(a) * 2.6);
            world.spawnParticle(Particle.SCULK_SOUL, p, 1, 0, 0, 0, 0);
        }
        SandboxFx.later(instance, 28L, () -> {
            world.spawnParticle(Particle.SONIC_BOOM, mark, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, mark.clone().add(0, 0.5, 0), 24, 0.8, 0.3, 0.8, 0.02);
            for (Player player : nearby(mark, 3.0)) {
                player.damage(28, body);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, true));
            }
        });
    }

    private void quietFanBlades(World world, Location at, LivingEntity body) {
        for (int blade = 0; blade < 6; blade++) {
            double base = blade * (Math.PI / 3.0) + ticks * 0.04;
            for (int r = 1; r <= 10; r++) {
                Location p = at.clone().add(Math.cos(base) * r * 0.85, 0.15, Math.sin(base) * r * 0.85);
                world.spawnParticle(Particle.SCULK_CHARGE_POP, p, 1, 0, 0, 0, 0);
            }
            Location tip = at.clone().add(Math.cos(base) * 9, 0.2, Math.sin(base) * 9);
            for (Player player : nearby(tip, 1.3)) {
                player.damage(16, body);
            }
        }
    }

    private void quietChaseMarks(World world, Location at, LivingEntity body) {
        for (Player player : nearby(at, 24)) {
            Location mark = player.getLocation().clone();
            mark.setY(at.getY());
            quietMarks.put(player.getUniqueId(), mark);
            for (int i = 0; i < 16; i++) {
                double a = (Math.PI * 2 * i) / 16.0;
                world.spawnParticle(Particle.MYCELIUM, mark.clone().add(Math.cos(a) * 1.8, 0.05, Math.sin(a) * 1.8),
                        1, 0, 0, 0, 0);
            }
            SandboxFx.later(instance, 22L, () -> {
                Location hit = quietMarks.remove(player.getUniqueId());
                if (hit == null) {
                    return;
                }
                world.spawnParticle(Particle.SONIC_BOOM, hit, 1, 0, 0, 0, 0);
                if (player.isValid() && player.getLocation().distanceSquared(hit) < 3.2 * 3.2) {
                    player.damage(24, body);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false, true));
                }
            });
        }
    }

    private void quietCollapse(World world, Location at, LivingEntity body) {
        for (int wave = 1; wave <= 4; wave++) {
            int w = wave;
            SandboxFx.later(instance, w * 6L, () -> {
                double r = w * 2.2;
                for (int i = 0; i < 36; i++) {
                    double a = (Math.PI * 2 * i) / 36.0;
                    world.spawnParticle(Particle.SCULK_SOUL, at.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r),
                            1, 0, 0, 0, 0);
                }
                for (Player player : nearby(at, r + 0.8)) {
                    if (player.getLocation().distance(at) < r - 1.4) {
                        continue;
                    }
                    player.damage(12 + w * 3, body);
                }
            });
        }
    }

    // ── Pet Jury ──────────────────────────────────────────────

    private void tickPetJury(World world, Location at) {
        LivingEntity body = instance.getEntity();
        if (ticks % 10 == 0) {
            SandboxFx.dustRing(world, at.clone().add(0, 0.9, 0), Color.fromRGB(255, 120, 200), 1.5, 18, 1.15f);
            world.spawnParticle(Particle.NOTE, at.clone().add(0, 1.4, 0), 4, 0.6, 0.4, 0.6, 0.2);
        }
        if (petJuryArmed && ticks == 30) {
            shout("&d&lPET JURY &7is in session.");
            world.playSound(at, Sound.ENTITY_WOLF_HOWL, 1.1f, 0.75f);
            spawnJuryGallery(world, at);
        }
        if (ticks % 60 == 0) {
            for (Player player : nearby(at, 24)) {
                boolean hasPet = nearbyPet(player);
                if (hasPet) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§d§lGUILTY — companion present (you deal less)"));
                    world.spawnParticle(Particle.ANGRY_VILLAGER, at.clone().add(0, 1.6, 0), 8, 0.5, 0.5, 0.5, 0);
                    player.damage(6, body);
                } else {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§a§lINNOCENT — no pet (+damage)"));
                    world.spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 4, 0.25, 0.25, 0.25, 0);
                }
            }
        }
        if (showCd > 0) {
            showCd--;
        }
        if (showCd <= 0) {
            showCd = SandboxFx.enraged(body) ? 75 : 100;
            showPattern = (showPattern + 1) % 4;
            switch (showPattern) {
                case 0 -> juryVerdictSlam(world, at, body);
                case 1 -> juryHowlWave(world, at, body);
                case 2 -> juryGavelBarrage(world, at, body);
                default -> juryContempt(world, at, body);
            }
        }
    }

    private void spawnJuryGallery(World world, Location at) {
        for (int i = 0; i < 5; i++) {
            double a = (Math.PI * 2 * i) / 5.0;
            Location seat = at.clone().add(Math.cos(a) * 7.5, 0.2, Math.sin(a) * 7.5);
            ArmorStand juror = world.spawn(seat, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setGravity(false);
                s.setMarker(true);
                s.setGlowing(true);
                s.setCustomNameVisible(true);
                s.customName(TextUtil.component("&dJuror"));
                s.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
                tagProp(s);
            });
            props.add(juror);
            TextDisplay plaque = world.spawn(seat.clone().add(0, 2.1, 0), TextDisplay.class, d -> {
                d.text(net.kyori.adventure.text.Component.text("§d§lJURY"));
                d.setBillboard(Display.Billboard.CENTER);
                tagProp(d);
            });
            props.add(plaque);
        }
    }

    private void juryVerdictSlam(World world, Location at, LivingEntity body) {
        shout("&d&lVERDICT");
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.7f);
        world.playSound(at, Sound.ENTITY_WOLF_GROWL, 0.8f, 0.6f);
        for (Player player : nearby(at, 22)) {
            Location mark = player.getLocation().clone();
            mark.setY(at.getY());
            boolean guilty = nearbyPet(player);
            SandboxFx.markedSlam(instance, world, mark, body, guilty ? 3.4 : 2.4, guilty ? 32 : 18, 18L,
                    guilty ? Particle.ANGRY_VILLAGER : Particle.HEART,
                    m -> SandboxFx.visualDebris(instance, props, world, m,
                            guilty ? Material.RED_CONCRETE : Material.PINK_CONCRETE, 6));
        }
    }

    private void juryHowlWave(World world, Location at, LivingEntity body) {
        shout("&d&lHOWL");
        world.playSound(at, Sound.ENTITY_WOLF_HOWL, 1.3f, 0.85f);
        SandboxFx.expandingRings(instance, world, at, body, 5, 2.0, 14, Particle.NOTE);
        for (Player player : nearby(at, 18)) {
            if (nearbyPet(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, true, false, true));
                player.damage(12, body);
            }
        }
    }

    private void juryGavelBarrage(World world, Location at, LivingEntity body) {
        shout("&d&lGAVEL BARRAGE");
        world.playSound(at, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.0f, 0.5f);
        for (int i = 0; i < 8; i++) {
            int shot = i;
            SandboxFx.later(instance, shot * 4L, () -> {
                Player target = SandboxFx.nearest(at, 26);
                if (target == null) {
                    return;
                }
                SandboxFx.dustBeam(world, at.clone().add(0, 1.5, 0), target.getEyeLocation(),
                        Color.fromRGB(255, 100, 180), 1.5f);
                target.damage(nearbyPet(target) ? 18 : 11, body);
                world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.05);
            });
        }
    }

    private void juryContempt(World world, Location at, LivingEntity body) {
        shout("&d&lCONTEMPT OF COURT");
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.2f);
        world.spawnParticle(Particle.FLASH, at.clone().add(0, 1.5, 0), 3, 0.4, 0.3, 0.4, 0);
        for (Player player : nearby(at, 20)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, false, true));
            if (nearbyPet(player)) {
                SandboxFx.pullIn(at, List.of(player), 0.45);
                player.damage(14, body);
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, true));
            }
        }
        SandboxFx.visualDebris(instance, props, world, at, Material.BOOKSHELF, 10);
    }

    private double petJuryDamage(Player player, double damage) {
        return nearbyPet(player) ? damage * 0.55 : damage * 1.4;
    }

    private boolean nearbyPet(Player player) {
        for (Entity entity : player.getNearbyEntities(12, 8, 12)) {
            if (entity.getPersistentDataContainer().has(AetherKeys.PET_ENTITY, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    // ── helpers ───────────────────────────────────────────────

    private List<Player> nearby(Location at, double radius) {
        List<Player> list = new ArrayList<>();
        World world = at.getWorld();
        if (world == null) {
            return list;
        }
        double r2 = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(at) <= r2) {
                list.add(player);
            }
        }
        return list;
    }

    private void ring(World world, Location center, Particle particle, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    private void shout(String message) {
        Location at = instance.getEntity() != null ? instance.getEntity().getLocation() : instance.getSpawnLocation();
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Player player : nearby(at, 40)) {
            player.sendMessage(TextUtil.component(message));
        }
    }

    private void tagProp(Entity entity) {
        entity.getPersistentDataContainer().set(
                AetherKeys.namespaced("bossengine", "sandbox_prop"),
                PersistentDataType.STRING,
                instance.getInstanceId().toString()
        );
    }

    private void clearProps() {
        for (Entity entity : props) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        props.clear();
    }

    private static boolean hasCharm(Player player, String id) {
        return hasItemId(player, id);
    }

    private static boolean hasItemId(Player player, String id) {
        if (player == null || id == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            String raw = item.getItemMeta().getPersistentDataContainer()
                    .get(AetherKeys.namespaced("aetherion", "test_gear"), PersistentDataType.STRING);
            if (id.equalsIgnoreCase(raw)) {
                return true;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.hasItemMeta()) {
            String raw = off.getItemMeta().getPersistentDataContainer()
                    .get(AetherKeys.namespaced("aetherion", "test_gear"), PersistentDataType.STRING);
            if (id.equalsIgnoreCase(raw)) {
                return true;
            }
        }
        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.hasItemMeta()) {
            String raw = chest.getItemMeta().getPersistentDataContainer()
                    .get(AetherKeys.namespaced("aetherion", "test_gear"), PersistentDataType.STRING);
            if (id.equalsIgnoreCase(raw)) {
                return true;
            }
        }
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.hasItemMeta()) {
            String raw = boots.getItemMeta().getPersistentDataContainer()
                    .get(AetherKeys.namespaced("aetherion", "test_gear"), PersistentDataType.STRING);
            return id.equalsIgnoreCase(raw);
        }
        return false;
    }

    private static boolean hasItemName(Player player, String needle) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.hasItemMeta() && main.getItemMeta().hasDisplayName()) {
            return main.getItemMeta().getDisplayName().contains(needle);
        }
        return false;
    }

    private record Snapshot(Location loc, Vector vel, long time) {
    }
}
