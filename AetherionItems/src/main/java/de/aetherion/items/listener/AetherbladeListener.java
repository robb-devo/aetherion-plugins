package de.aetherion.items.listener;

import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aetherblade — Rift Dance: chain-blink between nearest hostiles.
 * Identity: the blade cuts space and you step through the cuts.
 * On the draw every target on the route gets a crystal sigil, and the next one in line burns brightest.
 * Each hop tears a pointed slit in the air along the path — dark rift inside, white light leaking through
 * its seam, a cyan edge — rolled to a new angle every hop so the route reads as crossing strokes, and the blow
 * lands as a crescent in that same plane. When the dance ends the route zips shut, seam by seam, in cut order.
 */
public class AetherbladeListener implements Listener {

    private static final int HOP_DELAY_TICKS = 8;
    private static final int FINALE_DELAY = 6;
    private static final int ZIP_TICKS = 4;
    private static final int ZIP_STAGGER = 2;
    private static final float SEAM_OPEN = 0.62f;
    private static final float SEAM_REST = 0.34f;
    private static final float RIFT_THICK = 0.02f;
    private static final float CORE_THICK = 0.045f;

    private static final Color CORE = Color.fromRGB(120, 220, 255);
    private static final Color EDGE = Color.fromRGB(190, 120, 255);
    private static final Color HOT = Color.fromRGB(255, 250, 255);
    private static final float[] HOP_NOTES = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.7f, 1.85f, 2.0f};
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Quaternionf ON_VERTEX = new Quaternionf()
            .rotationTo(new Vector3f(1f, 1f, 1f).normalize(), new Vector3f(0f, 1f, 0f));
    private static final Quaternionf DIAMOND = new Quaternionf().rotateY((float) (Math.PI / 4.0));

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> dancing = ConcurrentHashMap.newKeySet();

    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    public AetherbladeListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats = new ActiveEquipmentStats(itemManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAetherblade(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        if (!dancing.add(player.getUniqueId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§dAetherblade §7is mid-dance…"
            ));
            return;
        }

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            dancing.remove(player.getUniqueId());
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§5Rift Dance §7recharging… §f" + left + "s"
            ));
            return;
        }

        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        List<LivingEntity> targets = findTargets(player, item);
        if (targets.isEmpty()) {
            dancing.remove(player.getUniqueId());
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7No enemies close enough."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            sputter(player.getLocation().add(0, 1.0, 0));
            return;
        }

        int cooldown = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                de.aetherion.items.item.DungeonCore.aetherbladeCooldownTicks(tier)
        );
        nextUseTick.put(player.getUniqueId(), tick + cooldown);
        player.setCooldown(item.getType(), cooldown);
        de.aetherion.items.combat.AbilityCooldownHud.arm(player, "aetherblade", "Aetherblade", tick + cooldown, item);

        draw(player);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§d✦ Rift Dance"));
        new Dance(player, targets).runTaskTimer(plugin, 0L, 1L);
    }

    /** The draw: a hairline of light splits beside the blade and the route locks in. */
    private static void draw(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        Vector look = base.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        Location blade = base.clone().add(right.multiply(0.45)).add(look.multiply(0.3));
        Particle.DustTransition line = new Particle.DustTransition(HOT, CORE, 0.7f);
        for (double y = 0.5; y <= 1.9; y += 0.12) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, blade.clone().add(0, y, 0), 1, 0, 0, 0, 0, line);
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, blade.clone().add(0, 1.2, 0), 6, 0.05, 0.5, 0.05, 0.03);
        world.playSound(blade, Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.9f);
        world.playSound(blade, Sound.ITEM_CROSSBOW_LOADING_END, 0.55f, 1.5f);
        world.playSound(blade, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4f, 1.85f);
    }

    private static void sputter(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, at, 5, 0.1, 0.15, 0.1, 0.01);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4, 0.12, 0.15, 0.12, 0,
                new Particle.DustTransition(EDGE, CORE, 0.6f));
    }

    /** One Rift Dance: hop timing matches the old per-hop schedule (hop i at i × 8 ticks). */
    private final class Dance extends BukkitRunnable {
        final Player player;
        final List<LivingEntity> targets;
        final List<Mark> marks = new ArrayList<>();
        final List<Seam> seams = new ArrayList<>();
        int t;
        int next;
        int finaleAt = -1;
        boolean released;

        Dance(Player player, List<LivingEntity> targets) {
            this.player = player;
            this.targets = targets;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                release();
                clear();
                cancel();
                return;
            }
            if (t >= 1 && t < targets.size()) {
                marks.add(new Mark(targets.get(t), t));
            }
            if (next < targets.size() && t == next * HOP_DELAY_TICKS) {
                hop(next);
                next++;
                if (next == targets.size()) {
                    release();
                    finaleAt = t + FINALE_DELAY;
                }
            }
            for (Mark mark : marks) {
                mark.tick(next);
            }
            for (int i = 0; i < seams.size(); i++) {
                Seam seam = seams.get(i);
                if (finaleAt >= 0 && t == finaleAt + i * ZIP_STAGGER) {
                    seam.zip(i);
                }
                seam.tick();
            }
            if (finaleAt >= 0 && t == finaleAt + Math.max(0, seams.size() - 1) * ZIP_STAGGER + ZIP_TICKS) {
                sealed();
            }
            if (finaleAt >= 0 && t > finaleAt + seams.size() * ZIP_STAGGER + ZIP_TICKS + 1) {
                clear();
                cancel();
                return;
            }
            t++;
        }

        private void release() {
            if (!released) {
                released = true;
                dancing.remove(player.getUniqueId());
            }
        }

        private Mark markFor(int order) {
            for (Mark mark : marks) {
                if (mark.order == order) {
                    return mark;
                }
            }
            return null;
        }

        private void hop(int index) {
            LivingEntity target = targets.get(index);
            Mark mark = markFor(index);
            if (target == null || !target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) {
                if (mark != null) {
                    mark.fizzle();
                }
                return;
            }

            Vector away = player.getLocation().toVector().subtract(target.getLocation().toVector());
            if (away.lengthSquared() < 0.01) {
                away = target.getLocation().getDirection().multiply(-1);
            }
            away.normalize().multiply(0.85);
            Location dest = target.getLocation().clone().add(away);
            dest.setY(target.getLocation().getY());
            Vector look = target.getLocation().toVector().subtract(dest.toVector());
            look.setY(0);
            if (look.lengthSquared() < 0.0001) {
                look = target.getLocation().getDirection();
                look.setY(0);
            }
            if (look.lengthSquared() > 0.0001) {
                dest.setDirection(look);
            }
            dest.setPitch(0f);

            Location from = player.getLocation().clone();
            double roll = (index % 2 == 0 ? 1 : -1) * Math.toRadians(28 + 14 * (index % 3));
            Seam seam = new Seam(from.clone().add(0, 1.0, 0), dest.clone().add(0, 1.0, 0), roll);
            seams.add(seam);
            World world = from.getWorld();
            if (world != null) {
                Location leave = from.clone().add(0, 1.0, 0);
                world.playSound(leave, Sound.ENTITY_SHEEP_SHEAR, 0.7f, 1.35f + index * 0.05f);
                world.playSound(leave, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.1f + index * 0.08f);
                world.spawnParticle(Particle.ELECTRIC_SPARK, leave, 6, 0.15, 0.35, 0.15, 0.03);
            }

            player.teleport(dest);
            player.setFallDistance(0);
            player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 8));

            boolean last = index == targets.size() - 1;
            Location chest = target.getLocation().add(0, target.getHeight() * 0.55, 0);
            crescent(chest, seam, Math.max(1.0, target.getWidth() * 1.2) * (last ? 1.35 : 1.0), last);
            if (mark != null) {
                mark.shatter();
            } else {
                shardBurst(target.getLocation().add(0, target.getHeight() + 0.5, 0));
            }

            World at = dest.getWorld();
            if (at != null) {
                float note = HOP_NOTES[Math.min(HOP_NOTES.length - 1, index)];
                at.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.25f + index * 0.06f);
                at.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
                at.playSound(dest, Sound.BLOCK_GLASS_BREAK, 0.4f, note);
                if (last) {
                    at.playSound(dest, Sound.ITEM_TRIDENT_THUNDER, 0.4f, 1.9f);
                }
            }

            double damage = equipmentStats.getStat(player, ItemCapability.DAMAGE);
            if (damage <= 0) {
                damage = 12.0;
            }
            double critChance = equipmentStats.getStat(player, ItemCapability.CRIT_CHANCE);
            double critDamage = equipmentStats.getStat(player, ItemCapability.CRIT_DAMAGE);
            if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
                damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
                de.aetherion.items.combat.DamageNumbers.markCrit(player);
                if (at != null) {
                    Location eye = target.getEyeLocation();
                    at.spawnParticle(Particle.CRIT, eye, 14, 0.22, 0.22, 0.22, 0.25);
                    at.spawnParticle(Particle.DUST, eye, 8, 0.18, 0.22, 0.18, 0,
                            new Particle.DustOptions(HOT, 1.05f));
                    at.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.55f, 1.35f);
                }
            }

            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player)
                    .withDirectEntity(player)
                    .build();
            target.damage(damage, source);
        }

        /** The route is closed: one quiet seal at the dancer. */
        private void sealed() {
            Location at = player.getLocation().add(0, 1.0, 0);
            World world = at.getWorld();
            if (world == null) {
                return;
            }
            world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.8f);
            world.playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.5f, 1.2f);
            world.spawnParticle(Particle.ELECTRIC_SPARK, at, 8, 0.25, 0.45, 0.25, 0.04);
        }

        private void clear() {
            for (Seam seam : seams) {
                seam.remove();
            }
            seams.clear();
            for (Mark mark : marks) {
                mark.remove();
            }
            marks.clear();
        }
    }

    /** The blow: a crescent in the plane of the seam that led here, so cut and step read as one stroke. */
    private static void crescent(Location center, Seam seam, double radius, boolean last) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Vector across = seam.widthVector();
        Vector along = seam.dirVector();
        Particle.DustOptions rim = new Particle.DustOptions(EDGE, 0.9f);
        for (int deg = -80; deg <= 80; deg += 10) {
            double a = Math.toRadians(deg);
            double c = Math.cos(a);
            float size = (float) (0.35 + 1.1 * c);
            Location inner = center.clone()
                    .add(across.clone().multiply(Math.sin(a) * radius))
                    .add(along.clone().multiply(c * radius * 0.45 - radius * 0.2));
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, inner, 1, 0, 0, 0, 0,
                    new Particle.DustTransition(last ? HOT : CORE, CORE, size));
            Location outer = center.clone()
                    .add(across.clone().multiply(Math.sin(a) * radius * 1.08))
                    .add(along.clone().multiply(c * radius * 0.5 - radius * 0.2));
            world.spawnParticle(Particle.DUST, outer, 1, 0, 0, 0, 0, rim);
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, last ? 14 : 7, 0.25, 0.3, 0.25, 0.05);
        if (last) {
            world.spawnParticle(Particle.END_ROD, center, 6, 0.1, 0.1, 0.1, 0.08);
        }
    }

    private static void shardBurst(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 10, 0.2, 0.2, 0.2, 0,
                new Particle.DustTransition(EDGE, CORE, 0.8f));
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.15, 0.15, 0.15, 0.05);
    }

    /**
     * A pointed slit torn along the hop: a stretched rhombus of rift (cyan outline) with a thicker white seam
     * through its middle that shows through both faces. Opens A→B, rests narrow, then zips shut toward B.
     */
    private final class Seam {
        final Location anchor;
        final Location end;
        final Vector3f dir;
        final Vector3f across;
        final Quaternionf basis;
        final float length;
        final BlockDisplay rift;
        final BlockDisplay core;
        /** Starts below zero so the tear opens on the tick after spawn instead of popping in already open. */
        int age = -1;
        int zipAge = -1;

        Seam(Location a, Location b, double roll) {
            anchor = a.clone();
            anchor.setYaw(0);
            anchor.setPitch(0);
            end = b.clone();
            Vector span = b.toVector().subtract(a.toVector());
            float len = (float) span.length();
            length = Math.max(0.3f, len);
            Vector d = len < 1.0E-3 ? new Vector(0, 0, 1) : span.clone().multiply(1.0 / len);
            Vector side = d.getCrossProduct(new Vector(0, 1, 0));
            if (side.lengthSquared() < 1.0E-4) {
                side = new Vector(1, 0, 0);
            }
            side.normalize();
            Vector up = side.getCrossProduct(d).normalize();
            Vector w = up.multiply(Math.cos(roll)).add(side.multiply(Math.sin(roll))).normalize();
            dir = new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ());
            across = new Vector3f((float) w.getX(), (float) w.getY(), (float) w.getZ());
            Vector3f normal = new Vector3f(dir).cross(across).normalize();
            basis = new Quaternionf().setFromNormalized(new Matrix3f(across, normal, dir));
            rift = spawn(anchor, Material.CRYING_OBSIDIAN, CORE, shape(0f, 0.02f, 0.02f, RIFT_THICK));
            core = spawn(anchor, Material.WHITE_CONCRETE, null, shape(0f, 0.02f, 0.01f, CORE_THICK));
        }

        Vector widthVector() {
            return new Vector(across.x, across.y, across.z);
        }

        Vector dirVector() {
            return new Vector(dir.x, dir.y, dir.z);
        }

        void zip(int order) {
            zipAge = 0;
            World world = anchor.getWorld();
            if (world != null) {
                Location mid = anchor.clone().add(dirVector().multiply(length * 0.5));
                world.playSound(mid, Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.6f, 1.4f + order * 0.08f);
            }
            pose(rift, shape(1f, 1f, 0.04f, RIFT_THICK), ZIP_TICKS);
            pose(core, shape(1f, 1f, 0.02f, CORE_THICK), ZIP_TICKS);
        }

        void tick() {
            age++;
            World world = anchor.getWorld();
            if (world == null) {
                return;
            }
            if (zipAge >= 0) {
                zipAge++;
                if (zipAge == ZIP_TICKS) {
                    Location tip = end.clone();
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 8, 0.12, 0.12, 0.12, 0,
                            new Particle.DustTransition(HOT, EDGE, 0.9f));
                    world.spawnParticle(Particle.ELECTRIC_SPARK, tip, 6, 0.1, 0.1, 0.1, 0.05);
                    world.spawnParticle(Particle.END_ROD, tip, 2, 0.05, 0.05, 0.05, 0.03);
                    world.playSound(tip, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.45f, 1.6f);
                    remove();
                }
                return;
            }
            if (age == 1) {
                pose(rift, shape(0f, 1f, SEAM_OPEN, RIFT_THICK), 2);
                pose(core, shape(0f, 1f, SEAM_OPEN * 0.12f, CORE_THICK), 2);
                Particle.DustTransition fray = new Particle.DustTransition(CORE, EDGE, 0.8f);
                for (int i = 0; i <= 6; i++) {
                    Location at = anchor.clone().add(dirVector().multiply(length * i / 6.0));
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.04, 0.04, 0.04, 0, fray);
                }
            } else if (age == 4) {
                pose(rift, shape(0f, 1f, SEAM_REST, RIFT_THICK), 6);
                pose(core, shape(0f, 1f, SEAM_REST * 0.14f, CORE_THICK), 6);
            } else if (age > 4 && age % 5 == 0) {
                double s = ThreadLocalRandom.current().nextDouble(0.1, 0.9);
                Location at = anchor.clone().add(dirVector().multiply(length * s));
                world.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0.02, 0.02, 0.02, 0.02);
            }
        }

        /** Rhombus spanning {@code from}..{@code to} of the seam length, {@code width} across, {@code thick} deep. */
        private Transformation shape(float from, float to, float width, float thick) {
            float l = Math.max(0.001f, length * (to - from));
            float w = Math.max(0.001f, width);
            Vector3f center = new Vector3f(dir).mul(length * (from + to) * 0.5f);
            Vector3f local = new Quaternionf(basis).transform(new Vector3f(w / 2f, thick / 2f, 0f));
            float root2 = (float) Math.sqrt(2.0);
            return new Transformation(center.sub(local), new Quaternionf(basis),
                    new Vector3f(w / root2, thick, l / root2), new Quaternionf(DIAMOND));
        }

        void remove() {
            discard(rift);
            discard(core);
        }
    }

    /** A crystal sigil riding over a target's head; the next target on the route glows and swells. */
    private final class Mark {
        final LivingEntity target;
        final int order;
        final BlockDisplay gem;
        int age;
        int shatterAge = -1;
        boolean gone;

        Mark(LivingEntity target, int order) {
            this.target = target;
            this.order = order;
            this.gem = spawn(anchor(), Material.AMETHYST_BLOCK, null, gemShape(0.001f, 0f));
            if (gem != null) {
                gem.setTeleportDuration(1);
                World world = gem.getWorld();
                world.playSound(gem.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.35f, 1.4f + order * 0.08f);
            }
        }

        private Location anchor() {
            Location at = target.getLocation().add(0, target.getHeight() + 0.55 + 0.06 * Math.sin(age * 0.3), 0);
            at.setYaw(0);
            at.setPitch(0);
            return at;
        }

        void tick(int nextOrder) {
            if (gone || gem == null || !gem.isValid()) {
                return;
            }
            age++;
            if (shatterAge >= 0) {
                shatterAge++;
                if (shatterAge >= 3) {
                    remove();
                }
                return;
            }
            if (!target.isValid() || target.isDead()) {
                fizzle();
                return;
            }
            gem.teleport(anchor());
            boolean up = order == nextOrder;
            float size = (up ? 0.3f : 0.2f) * Math.min(1f, age / 3f);
            gem.setBrightness(up ? LIT : new Display.Brightness(8, 8));
            if (up && !gem.isGlowing()) {
                gem.setGlowing(true);
                gem.setGlowColorOverride(CORE);
            }
            pose(gem, gemShape(size, age * 0.14f), 1);
        }

        void shatter() {
            if (gone || gem == null || !gem.isValid()) {
                return;
            }
            shatterAge = 0;
            pose(gem, gemShape(0.6f, age * 0.14f + 0.8f), 2);
            shardBurst(gem.getLocation());
        }

        void fizzle() {
            if (gem != null && gem.isValid()) {
                World world = gem.getWorld();
                world.spawnParticle(Particle.SMOKE, gem.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.DUST, gem.getLocation(), 3, 0.08, 0.08, 0.08, 0,
                        new Particle.DustOptions(EDGE, 0.6f));
            }
            remove();
        }

        void remove() {
            gone = true;
            discard(gem);
        }

        /** A cube stood on its corner, stretched tall, spun about the vertical. */
        private Transformation gemShape(float size, float spin) {
            float s = Math.max(0.001f, size);
            Quaternionf left = new Quaternionf().rotateY(spin);
            Vector3f scale = new Vector3f(s, s * 1.4f, s);
            Vector3f center = new Quaternionf(ON_VERTEX).transform(new Vector3f(0.5f, 0.5f, 0.5f)).mul(scale);
            left.transform(center);
            return new Transformation(center.negate(), left, scale, new Quaternionf(ON_VERTEX));
        }
    }

    private static void pose(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, Material material, Color glow, Transformation initial) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setBrightness(LIT);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setInterpolationDuration(1);
                spawned.setTransformation(initial);
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    private List<LivingEntity> findTargets(Player player, ItemStack item) {
        int tier = de.aetherion.items.item.DungeonCore.tier(item);
        double range = de.aetherion.items.item.DungeonCore.aetherbladeRange(tier);
        int max = de.aetherion.items.item.DungeonCore.aetherbladeTargets(tier);
        List<LivingEntity> found = new ArrayList<>();
        for (var entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living, player)) {
                continue;
            }
            found.add(living);
        }
        found.sort(Comparator.comparingDouble(target ->
                target.getLocation().distanceSquared(player.getLocation())
        ));
        if (found.size() > max) {
            return new ArrayList<>(found.subList(0, max));
        }
        return found;
    }

    private boolean isValidTarget(LivingEntity target, Player player) {
        if (target == null || target.equals(player) || !target.isValid() || target.isDead()) {
            return false;
        }
        if (target instanceof Player || target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        return true;
    }

    private boolean isAetherblade(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) {
            return false;
        }
        String id = itemManager.getItemId(item);
        return "aetherblade".equalsIgnoreCase(id);
    }
}
