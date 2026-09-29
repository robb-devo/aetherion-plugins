package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.blueprint.BlueprintUpgrade;
import de.aetherion.items.combat.AbilityCooldownHud;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.mining.HarvestRules;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vein Siphon — Ore Vacuum with ghost suction FX. Scales with blueprint tier.
 * A scan ring sweeps out to the real radius and lights every ore it passes with a glowing ghost in the ore's
 * colour; a beat later each ore is ripped from the wall (nearest first, so the pull follows the scan) and its
 * ghost is sucked along a tightening spiral into the intake at the pick, each catch pitching the pickup up.
 * Harvest happens at the rip, only while the siphon is still in hand; a dropped line leaves the ore in place.
 */
public final class VeinSiphonListener implements Listener {

    public static final String ITEM_ID = "vein_siphon";
    private static final int SCAN_TICKS = 10;
    private static final int MARK_HOLD = 5;
    private static final int MAX_TICKS = 90;
    private static final Color INTAKE = Color.fromRGB(80, 225, 215);
    private static final Color PALE = Color.fromRGB(225, 255, 250);
    private static final Color DEEP = Color.fromRGB(20, 90, 100);

    private static final List<BlockDisplay> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();

    public VeinSiphonListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    /** Plugin disable: removes every ore ghost still marked or in flight. */
    public static void shutdown() {
        for (BlockDisplay display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = itemManager.getItemId(hand);
        if (id == null || !ITEM_ID.equalsIgnoreCase(id)) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        int tier = BlueprintUpgrade.tier(hand);
        long cooldownTicks = BlueprintUpgrade.siphonCooldownTicks(tier);
        int radius = BlueprintUpgrade.siphonRadius(tier);
        int maxOres = BlueprintUpgrade.siphonMaxOres(tier);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            long remain = (next - tick + 19) / 20;
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Ore Vacuum · " + remain + "s",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
            return;
        }

        List<Block> ores = findOres(player, radius, maxOres);
        if (ores.isEmpty()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "No mineable ore in range",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
            sputter(player);
            return;
        }

        nextUseTick.put(player.getUniqueId(), tick + cooldownTicks);
        AbilityCooldownHud.arm(player, ITEM_ID, "Ore Vacuum", tick + cooldownTicks, hand);
        new Vacuum(player, ores, radius, tier).runTaskTimer(plugin, 0L, 1L);
    }

    private List<Block> findOres(Player player, int radius, int maxOres) {
        List<Block> found = new ArrayList<>();
        Location center = player.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double power = 0;
        if (AetherionItems.getInstance() != null && AetherionItems.getInstance().getHarvestListener() != null) {
            power = new de.aetherion.items.manager.ActiveEquipmentStats(itemManager)
                    .getStat(player, de.aetherion.items.model.ItemCapability.MINING_POWER);
        }
        boolean open = HarvestRules.openMine(center.getWorld());
        int r2 = radius * radius;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz) > r2) {
                        continue;
                    }
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!HarvestRules.ore(type)) {
                        continue;
                    }
                    if (!open && !HarvestRules.canHarvest(type, power)) {
                        continue;
                    }
                    found.add(block);
                }
            }
        }
        found.sort((a, b) -> {
            double da = a.getLocation().distanceSquared(center);
            double db = b.getLocation().distanceSquared(center);
            return Double.compare(da, db);
        });
        if (found.size() > maxOres) {
            return new ArrayList<>(found.subList(0, maxOres));
        }
        return found;
    }

    private boolean holdingSiphon(Player player) {
        return ITEM_ID.equalsIgnoreCase(itemManager.getItemId(player.getInventory().getItemInMainHand()));
    }

    /** Where ghosts are swallowed: just ahead of the pick in the right hand. */
    private static Location intake(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = look.getCrossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        Location at = eye.add(look.multiply(0.75)).add(right.normalize().multiply(0.32)).add(0, -0.38, 0);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private static void sputter(Player player) {
        Location at = intake(player);
        player.getWorld().spawnParticle(Particle.SMOKE, at, 6, 0.08, 0.08, 0.08, 0.02);
        player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4, 0.1, 0.1, 0.1, 0,
                new Particle.DustTransition(INTAKE, DEEP, 0.6f));
        player.playSound(at, Sound.ENTITY_BREEZE_INHALE, 0.35f, 0.6f);
    }

    static Color oreColor(Material type) {
        String name = type.name();
        if (name.contains("DIAMOND")) {
            return Color.fromRGB(90, 235, 230);
        }
        if (name.contains("EMERALD")) {
            return Color.fromRGB(60, 220, 110);
        }
        if (name.contains("GOLD")) {
            return Color.fromRGB(255, 210, 60);
        }
        if (name.contains("REDSTONE")) {
            return Color.fromRGB(240, 45, 40);
        }
        if (name.contains("LAPIS")) {
            return Color.fromRGB(50, 90, 230);
        }
        if (name.contains("COPPER")) {
            return Color.fromRGB(230, 125, 75);
        }
        if (name.contains("IRON")) {
            return Color.fromRGB(225, 180, 145);
        }
        if (name.contains("COAL")) {
            return Color.fromRGB(120, 120, 130);
        }
        if (name.contains("QUARTZ")) {
            return Color.fromRGB(240, 235, 225);
        }
        if (name.contains("ANCIENT_DEBRIS") || name.contains("NETHERITE")) {
            return Color.fromRGB(160, 100, 75);
        }
        if (name.contains("AMETHYST")) {
            return Color.fromRGB(185, 125, 240);
        }
        return INTAKE;
    }

    private enum Stage { PENDING, MARKED, FLYING, DONE }

    private static final class Pull {
        final Block block;
        final Material type;
        final BlockData data;
        final Location from;
        final Color color;
        final double reach;
        final int markAt;
        final int ripAt;
        final int flightTicks;
        final Vector tumble;
        final double swirlPhase;
        Stage stage = Stage.PENDING;
        BlockDisplay ghost;
        Location last;
        int flightAge;

        Pull(Block block, Location center, int radius) {
            this.block = block;
            this.type = block.getType();
            this.data = block.getBlockData().clone();
            this.from = block.getLocation().add(0.5, 0.5, 0.5);
            this.color = oreColor(type);
            this.reach = Math.min(1.0, from.distance(center) / Math.max(1.0, radius));
            this.markAt = 1 + (int) Math.round(reach * SCAN_TICKS);
            this.ripAt = markAt + MARK_HOLD;
            this.flightTicks = 7 + (int) Math.round(reach * 9);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector axis = new Vector(random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1));
            this.tumble = axis.lengthSquared() < 1.0E-3 ? new Vector(0, 1, 0) : axis.normalize();
            this.swirlPhase = random.nextDouble(Math.PI * 2);
        }
    }

    /** One activation: scan, mark, rip, suction, finale. */
    private final class Vacuum extends BukkitRunnable {
        private final Player player;
        private final World world;
        private final Location center;
        private final int radius;
        private final int tier;
        private final List<Pull> pulls = new ArrayList<>();
        private final HarvestListener harvest;
        private int t;
        private int pulled;
        private int caught;
        private int dropped;
        private int sounds;

        Vacuum(Player player, List<Block> ores, int radius, int tier) {
            this.player = player;
            this.world = player.getWorld();
            this.center = player.getLocation().clone();
            this.radius = radius;
            this.tier = tier;
            this.harvest = AetherionItems.getInstance() == null
                    ? null
                    : AetherionItems.getInstance().getHarvestListener();
            for (Block block : ores) {
                pulls.add(new Pull(block, center, radius));
            }
            Location at = intake(player);
            world.playSound(at, Sound.ENTITY_BREEZE_INHALE, 0.8f, 1.3f);
            world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.7f);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 8, 0.15, 0.15, 0.15, 0,
                    new Particle.DustTransition(PALE, INTAKE, 0.8f));
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                abort();
                return;
            }
            t++;
            sounds = 0;
            if (t <= SCAN_TICKS) {
                scan();
            }
            boolean active = false;
            for (Pull pull : pulls) {
                switch (pull.stage) {
                    case PENDING -> {
                        if (t >= pull.markAt) {
                            mark(pull);
                        }
                        active = true;
                    }
                    case MARKED -> {
                        if (t >= pull.ripAt) {
                            rip(pull);
                        }
                        active = true;
                    }
                    case FLYING -> {
                        fly(pull);
                        active = true;
                    }
                    default -> {
                    }
                }
            }
            if (active) {
                intakeSwirl();
            }
            if ((!active && t > SCAN_TICKS) || t > MAX_TICKS) {
                finale();
            }
        }

        /** The reach, drawn: a ring at hip height expanding to the true vacuum radius. */
        private void scan() {
            double k = t / (double) SCAN_TICKS;
            double r = radius * (1.0 - Math.pow(1.0 - k, 2));
            int points = (int) Math.min(72, 12 + r * 4);
            Particle.DustTransition edge = new Particle.DustTransition(PALE, INTAKE, 1.1f);
            Location mid = center.clone().add(0, 0.9, 0);
            double turn = t * 0.08;
            for (int i = 0; i < points; i++) {
                double a = turn + Math.PI * 2 * i / points;
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        mid.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, edge);
            }
        }

        /** The ore lights up in place: a full-bright ghost copy with a glowing outline in its colour. */
        private void mark(Pull pull) {
            pull.stage = Stage.MARKED;
            pull.ghost = ghost(pull);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, pull.from, 4, 0.35, 0.35, 0.35, 0,
                    new Particle.DustTransition(pull.color, PALE, 0.8f));
            if (sounds++ < 2) {
                world.playSound(pull.from, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, (float) (1.2 + 0.6 * pull.reach));
            }
        }

        /** Harvest at the rip, then tear the ghost out of the wall. */
        private void rip(Pull pull) {
            if (!holdingSiphon(player) || pull.block.getType() != pull.type) {
                drop(pull);
                return;
            }
            if (harvest != null) {
                harvest.vacuumHarvest(player, pull.block);
            } else {
                pull.block.setType(Material.BEDROCK, false);
            }
            if (pull.block.getType() == pull.type) {
                drop(pull);
                return;
            }
            pulled++;
            pull.stage = Stage.FLYING;
            pull.last = pull.from.clone();
            world.spawnParticle(Particle.BLOCK, pull.from, 14, 0.3, 0.3, 0.3, 0.1, pull.data);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, pull.from, 6, 0.3, 0.3, 0.3, 0,
                    new Particle.DustTransition(pull.color, PALE, 1.0f));
            if (sounds++ < 3) {
                Sound crack;
                try {
                    crack = pull.data.getSoundGroup().getBreakSound();
                } catch (Throwable ignored) {
                    crack = Sound.BLOCK_STONE_BREAK;
                }
                world.playSound(pull.from, crack, 0.55f, 0.9f);
                world.playSound(pull.from, Sound.ENTITY_BREEZE_INHALE, 0.2f, 1.8f);
            }
        }

        /** Accelerating spiral that tightens onto the intake; the ghost shrinks and tumbles as it goes. */
        private void fly(Pull pull) {
            BlockDisplay ghost = pull.ghost;
            if (ghost == null || !ghost.isValid()) {
                pull.stage = Stage.DONE;
                caught++;
                return;
            }
            pull.flightAge++;
            double p = Math.min(1.0, pull.flightAge / (double) pull.flightTicks);
            Location mouth = intake(player);
            Vector path = mouth.toVector().subtract(pull.from.toVector());
            double dist = path.length();
            double ease = p * p;
            Location at = pull.from.clone().add(path.clone().multiply(ease));
            if (dist > 0.5) {
                Vector axis = path.clone().multiply(1.0 / dist);
                Vector b1 = axis.getCrossProduct(Math.abs(axis.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0)).normalize();
                Vector b2 = axis.getCrossProduct(b1).normalize();
                double swirl = Math.min(1.4, dist * 0.18) * Math.sin(Math.PI * p) * (1.0 - 0.5 * p);
                double a = pull.swirlPhase + p * Math.PI * 3.0;
                at.add(b1.multiply(Math.cos(a) * swirl)).add(b2.multiply(Math.sin(a) * swirl));
            }
            at.setYaw(0);
            at.setPitch(0);
            ghost.teleport(at);
            posture(ghost, pull, (float) (0.85 - 0.5 * p), 1);

            Vector seg = at.toVector().subtract(pull.last.toVector());
            Particle.DustTransition wisp = new Particle.DustTransition(pull.color, PALE, 0.75f);
            for (int i = 1; i <= 2; i++) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        pull.last.clone().add(seg.clone().multiply(i / 2.0)), 1, 0.02, 0.02, 0.02, 0, wisp);
            }
            pull.last = at;

            if (p >= 1.0) {
                discard(ghost);
                pull.ghost = null;
                pull.stage = Stage.DONE;
                caught++;
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, mouth, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustTransition(pull.color, PALE, 0.7f));
                if (sounds++ < 4) {
                    world.playSound(mouth, Sound.ENTITY_ITEM_PICKUP, 0.3f, (float) Math.min(2.0, 0.9 + caught * 0.05));
                }
            }
        }

        /** The mouth of the siphon: a ring perpendicular to the view, contracting over and over. */
        private void intakeSwirl() {
            Location mouth = intake(player);
            Vector look = player.getEyeLocation().getDirection().normalize();
            Vector a = look.getCrossProduct(new Vector(0, 1, 0));
            if (a.lengthSquared() < 1.0E-4) {
                a = new Vector(1, 0, 0);
            }
            a.normalize();
            Vector b = a.getCrossProduct(look).normalize();
            double r = 0.45 - 0.33 * ((t % 6) / 6.0);
            Particle.DustTransition dust = new Particle.DustTransition(INTAKE, PALE, 0.55f);
            for (int i = 0; i < 8; i++) {
                double ang = t * 0.5 + Math.PI * 2 * i / 8;
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        mouth.clone().add(a.clone().multiply(Math.cos(ang) * r)).add(b.clone().multiply(Math.sin(ang) * r)),
                        1, 0, 0, 0, 0, dust);
            }
            if (t % 7 == 0) {
                world.playSound(mouth, Sound.ENTITY_BREEZE_INHALE, 0.35f, 1.6f);
            }
        }

        private void finale() {
            clearGhosts();
            Location mouth = intake(player);
            Color tone = pulls.isEmpty() ? INTAKE : pulls.get(pulls.size() - 1).color;
            if (pulled > 0) {
                Particle.DustTransition burst = new Particle.DustTransition(tone, PALE, 0.9f);
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, mouth, 0, Math.cos(a), 0.4, Math.sin(a), 0.08, burst);
                }
                world.spawnParticle(Particle.WAX_OFF, mouth, 8, 0.25, 0.25, 0.25, 0.4);
                world.playSound(mouth, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f);
                world.playSound(mouth, Sound.BLOCK_BEACON_DEACTIVATE, 0.3f, 2.0f);
            } else {
                sputter(player);
            }
            String tail = dropped > 0 ? " · " + dropped + " let go" : "";
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Ore Vacuum T" + tier + " · " + pulled + " pulled" + tail,
                    pulled > 0
                            ? net.kyori.adventure.text.format.NamedTextColor.AQUA
                            : net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
            cancel();
        }

        private void abort() {
            clearGhosts();
            cancel();
        }

        /** The line lets go: the lit ghost fades back into the wall and the ore stays where it was. */
        private void drop(Pull pull) {
            dropped++;
            pull.stage = Stage.DONE;
            if (pull.ghost != null) {
                world.spawnParticle(Particle.SMOKE, pull.from, 3, 0.25, 0.25, 0.25, 0.01);
                discard(pull.ghost);
                pull.ghost = null;
            }
        }

        private void clearGhosts() {
            for (Pull pull : pulls) {
                if (pull.ghost != null) {
                    discard(pull.ghost);
                    pull.ghost = null;
                }
            }
        }

        private BlockDisplay ghost(Pull pull) {
            try {
                Location at = pull.from.clone();
                at.setYaw(0);
                at.setPitch(0);
                BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(pull.data);
                    spawned.setPersistent(false);
                    spawned.setInvulnerable(true);
                    spawned.setGravity(false);
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(pull.color);
                    spawned.setTeleportDuration(1);
                    spawned.setTransformation(centered(new Quaternionf(), 1.01f));
                });
                LIVE.add(display);
                return display;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void posture(BlockDisplay ghost, Pull pull, float scale, int ticks) {
            Quaternionf rot = new Quaternionf().rotateAxis(pull.flightAge * 0.35f,
                    (float) pull.tumble.getX(), (float) pull.tumble.getY(), (float) pull.tumble.getZ());
            ghost.setInterpolationDelay(0);
            ghost.setInterpolationDuration(ticks);
            ghost.setTransformation(centered(rot, scale));
        }
    }

    private static Transformation centered(Quaternionf rot, float scale) {
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(scale / 2f, scale / 2f, scale / 2f));
        return new Transformation(half.negate(), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static void discard(BlockDisplay display) {
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }
}
