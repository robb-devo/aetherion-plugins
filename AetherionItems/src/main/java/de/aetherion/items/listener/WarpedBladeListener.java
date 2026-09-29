package de.aetherion.items.listener;

import de.aetherion.items.combat.AbilityCooldownHud;
import de.aetherion.items.item.DungeonCore;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
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
 * Warped Blade — Warp Step: forward blink, carried through the warped forest's mycelium.
 * The ground under the caster blooms into a nylium patch and warped roots close around the feet; the caster
 * sinks, and a glowing hyphae strand races along the floor to the landing, twigging off side threads and
 * pushing sprouts up as the front passes beneath them. At the far end a warped fungus erupts out of the ground
 * behind the caster — stalk shoots up, the wart cap bursts open overhead with shroomlights under its rim,
 * twisting vines unfurl — sheds a slow fall of spores, then droops, withers and pulls back into the floor.
 * Range, landing search, cooldown and safety checks match the old step.
 */
public class WarpedBladeListener implements Listener {

    /** Roots grip for two ticks before the caster is pulled under; the blink itself is unchanged. */
    private static final int GRIP_TICKS = 2;
    private static final int GROW_TICKS = 3;
    private static final int CAP_OPEN = 3;
    private static final int WITHER_AT = 13;
    private static final int END_AT = 21;
    private static final double SEG = 0.6;
    /** Past this many live displays (all casters), strands skip twigs and sprouts. */
    private static final int MAX_LIVE = 240;

    private static final Color TEAL = Color.fromRGB(20, 210, 190);
    private static final Color DEEP = Color.fromRGB(12, 80, 90);
    private static final Color GLOW = Color.fromRGB(150, 255, 230);
    private static final Color EMBER = Color.fromRGB(255, 170, 70);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Display.Brightness DIM = new Display.Brightness(9, 9);
    private static final BlockData NYLIUM = Material.WARPED_NYLIUM.createBlockData();
    private static final BlockData WART = Material.WARPED_WART_BLOCK.createBlockData();

    private static final List<BlockDisplay> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final Map<UUID, Long> nextUseTick = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> stepping = ConcurrentHashMap.newKeySet();

    public static void shutdown() {
        for (BlockDisplay display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    public WarpedBladeListener(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
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
        if (!isWarpedBlade(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        if (!stepping.add(player.getUniqueId())) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§3Warped Blade §7is mid-step…"
            ));
            return;
        }

        int tier = DungeonCore.tier(item);
        int cooldownTicks = de.aetherion.items.listener.ProgressionEffects.cooldownTicks(
                player,
                itemManager,
                DungeonCore.warpedCooldownTicks(tier)
        );
        double range = DungeonCore.warpedRange(tier);

        long tick = Bukkit.getCurrentTick();
        Long next = nextUseTick.get(player.getUniqueId());
        if (next != null && tick < next) {
            stepping.remove(player.getUniqueId());
            long left = Math.max(1, (next - tick + 19) / 20);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§3Warped Blade §7recharging… §f" + left + "s"
            ));
            return;
        }

        Location dest = findLanding(player, range);
        if (dest == null) {
            stepping.remove(player.getUniqueId());
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7Something warped in the way."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.65f);
            sputter(player);
            return;
        }

        nextUseTick.put(player.getUniqueId(), tick + cooldownTicks);
        player.setCooldown(item.getType(), cooldownTicks);
        AbilityCooldownHud.arm(player, "warped_blade", "Warped Blade", tick + cooldownTicks, item);

        Location from = player.getLocation().clone();
        double distance = dest.toVector().subtract(from.toVector()).length();
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§3✦ Warp Step §7— §f" + String.format(java.util.Locale.US, "%.0f", distance) + "§7m"
        ));

        new Transit(player, from, dest).runTaskTimer(plugin, 0L, 1L);
    }

    private static void sputter(Player player) {
        Location at = player.getLocation().add(0, 0.1, 0);
        World world = player.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 8, 0.25, 0.02, 0.25, 0.05, NYLIUM);
        world.spawnParticle(Particle.WARPED_SPORE, at.clone().add(0, 0.8, 0), 6, 0.15, 0.2, 0.15, 0.01);
        world.playSound(at, Sound.BLOCK_ROOTS_BREAK, 0.5f, 0.6f);
    }

    /** One Warp Step, from the root grip to the last withered sprout. */
    private final class Transit extends BukkitRunnable {
        final Player player;
        final World world;
        final Location from;
        final Location dest;
        final Location originFloor;
        final Location destFloor;
        final Vector travel;
        final List<BlockDisplay> patch = new ArrayList<>();
        final List<float[]> patchSpec = new ArrayList<>();
        final List<BlockDisplay> grip = new ArrayList<>();
        final List<Strand> strand = new ArrayList<>();
        final List<Sprout> sprouts = new ArrayList<>();
        final List<BlockDisplay> vines = new ArrayList<>();
        final List<BlockDisplay> lights = new ArrayList<>();
        List<Vector> path = List.of();
        Location fungusBase;
        BlockDisplay stalk;
        BlockDisplay cap;
        BlockDisplay dome;
        float capYaw;
        int t;
        boolean moved;

        Transit(Player player, Location from, Location dest) {
            this.player = player;
            this.world = from.getWorld();
            this.from = from;
            this.dest = dest;
            this.originFloor = floorAt(from);
            this.destFloor = floorAt(dest);
            Vector flat = dest.toVector().subtract(from.toVector()).setY(0);
            this.travel = flat.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : flat.normalize();
        }

        @Override
        public void run() {
            if (world == null) {
                stepping.remove(player.getUniqueId());
                cancel();
            return;
        }
            if (!moved && (!player.isOnline() || player.isDead() || player.getWorld() != world)) {
                stepping.remove(player.getUniqueId());
                clear();
                cancel();
                return;
            }

            if (t == 0) {
                bloomPatch();
            }
            if (t < GRIP_TICKS) {
                gripTick(t);
            } else if (t == GRIP_TICKS) {
                commit();
            }

            if (moved) {
                int since = t - GRIP_TICKS;
                growStrand(since);
                fungus(since);
                tickSprouts(since);
                if (since == 6) {
                    coolStrand();
                }
                if (since >= 3 && since < WITHER_AT && since % 2 == 0) {
                    sporeFall();
                }
                if (since >= WITHER_AT) {
                    wither(since);
                }
                if (since >= END_AT) {
                    finish();
                    clear();
                    cancel();
                    return;
                }
            }
            t++;
        }

        // ---------------------------------------------------------------- origin

        /** A patch of nylium spreads over the floor at the caster's feet. */
        private void bloomPatch() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble(Math.PI * 2);
                double r = i == 0 ? 0.0 : random.nextDouble(0.35, 0.8);
                float size = i == 0 ? 1.0f : (float) random.nextDouble(0.45, 0.75);
                float[] spec = {(float) (Math.cos(a) * r), (float) (Math.sin(a) * r), size,
                        (float) random.nextDouble(Math.PI * 2)};
                BlockDisplay tile = spawn(originFloor, Material.WARPED_NYLIUM, null, DIM);
                if (tile != null) {
                    patch.add(tile);
                    patchSpec.add(spec);
                    pose(tile, flatTile(spec, 0.3f, 0f), 1);
                }
            }
            for (int i = 0; i < 4; i++) {
                BlockDisplay root = spawn(originFloor, i % 2 == 0 ? Material.WARPED_ROOTS : Material.NETHER_SPROUTS, null, LIT);
                if (root != null) {
                    grip.add(root);
                }
            }
            world.spawnParticle(Particle.BLOCK, originFloor.clone().add(0, 0.1, 0), 14, 0.45, 0.02, 0.45, 0.05, NYLIUM);
            world.playSound(originFloor, Sound.BLOCK_NYLIUM_BREAK, 0.8f, 0.6f);
            world.playSound(originFloor, Sound.BLOCK_SCULK_SPREAD, 0.6f, 1.3f);
            world.playSound(originFloor, Sound.BLOCK_ROOTS_PLACE, 0.7f, 0.75f);
        }

        /** Roots rise and lean in around the feet: the forest takes hold before the pull. */
        private void gripTick(int step) {
            float k = (step + 1) / (float) GRIP_TICKS;
            for (int i = 0; i < patch.size(); i++) {
                pose(patch.get(i), flatTile(patchSpec.get(i), 0.3f + 0.7f * k, 0f), 1);
            }
            for (int i = 0; i < grip.size(); i++) {
                double a = Math.PI * 2 * i / grip.size() + 0.4;
                pose(grip.get(i), plant(new Vector3f((float) (Math.cos(a) * 0.55), 0f, (float) (Math.sin(a) * 0.55)),
                        0.8f * k, 0.85f * k, (float) a, (float) Math.toRadians(22 * k), true), 1);
            }
            world.spawnParticle(Particle.WARPED_SPORE, originFloor.clone().add(0, 0.6, 0), 4, 0.3, 0.3, 0.3, 0.01);
            if (step == GRIP_TICKS - 1) {
                world.playSound(originFloor, Sound.BLOCK_ROOTS_BREAK, 0.6f, 1.2f);
            }
        }

        // ---------------------------------------------------------------- the step

        private void commit() {
            Location leave = from.clone().add(0, 0.9, 0);
            world.spawnParticle(Particle.BLOCK, originFloor.clone().add(0, 0.15, 0), 26, 0.35, 0.05, 0.35, 0.12, NYLIUM);
            world.spawnParticle(Particle.WARPED_SPORE, leave, 16, 0.25, 0.45, 0.25, 0.02);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, leave, 10, 0.25, 0.4, 0.25, 0,
                    new Particle.DustTransition(TEAL, DEEP, 1.0f));
            world.playSound(leave, Sound.ENTITY_ENDERMAN_TELEPORT, 0.45f, 0.6f);
            world.playSound(leave, Sound.BLOCK_ROOTS_BREAK, 0.8f, 0.5f);
            world.playSound(leave, Sound.BLOCK_NYLIUM_BREAK, 0.7f, 0.45f);
            for (BlockDisplay root : grip) {
                pose(root, plant(new Vector3f(), 0.001f, 0.001f, 0f, 0f, true), 2);
            }

            player.teleport(dest);
            player.setFallDistance(0);
            moved = true;
            stepping.remove(player.getUniqueId());

            layStrand();
            erupt();
        }

        /** Hyphae follow the floor from the origin to the landing, wandering a little like real mycelium. */
        private void layStrand() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector a = originFloor.toVector();
            Vector b = destFloor.toVector();
            Vector span = b.clone().subtract(a).setY(0);
            double len = span.length();
            if (len < 0.4) {
                return;
            }
            Vector side = new Vector(-travel.getZ(), 0, travel.getX());
            int n = Math.max(2, (int) Math.ceil(len / SEG));
            double phase = random.nextDouble(Math.PI * 2);
            List<Vector> points = new ArrayList<>(n + 1);
            for (int i = 0; i <= n; i++) {
                double f = i / (double) n;
                double taper = Math.sin(Math.PI * f);
                Vector at = a.clone().add(span.clone().multiply(f))
                        .add(side.clone().multiply(taper * (0.16 * Math.sin(f * 7.0 + phase) + random.nextDouble(-0.05, 0.05))));
                at.setY(groundY(at, a.getY() + (b.getY() - a.getY()) * f));
                points.add(at);
            }
            path = points;
            boolean crowded = LIVE.size() > MAX_LIVE;
            for (int i = 0; i < n; i++) {
                Vector p = points.get(i);
                Vector q = points.get(i + 1);
                double start = i / (double) n;
                Strand main = strand(p, q, 0.1f, 0.06f, Material.WARPED_HYPHAE, TEAL, start);
                if (main != null) {
                    strand.add(main);
                }
                if (crowded) {
                        continue;
                    }
                if (i % 2 == 1 && i < n - 1) {
                    double sign = random.nextBoolean() ? 1.0 : -1.0;
                    Vector dir = q.clone().subtract(p).setY(0);
                    if (dir.lengthSquared() > 1.0E-4) {
                        dir.normalize();
                        double ang = sign * random.nextDouble(0.7, 1.1);
                        Vector twigDir = dir.clone().multiply(Math.cos(ang)).add(side.clone().multiply(Math.sin(ang)));
                        Vector tip = q.clone().add(twigDir.multiply(random.nextDouble(0.3, 0.5)));
                        tip.setY(groundY(tip, q.getY()));
                        Strand twig = strand(q, tip, 0.05f, 0.04f, Material.STRIPPED_WARPED_HYPHAE, null, start + 0.08);
                        if (twig != null) {
                            strand.add(twig);
                        }
                    }
                }
                if (i % 3 == 1) {
                    Location at = q.toLocation(world);
                    at.setYaw(0);
                    at.setPitch(0);
                    Material kind = i % 2 == 0 ? Material.WARPED_ROOTS : Material.NETHER_SPROUTS;
                    BlockDisplay sprout = spawn(at, kind, null, LIT);
                    if (sprout != null) {
                        sprouts.add(new Sprout(sprout, (i + 1) / (double) n, (float) random.nextDouble(Math.PI * 2)));
                    }
                }
            }
        }

        private Strand strand(Vector p, Vector q, float width, float height, Material material, Color glow, double start) {
            Location at = p.toLocation(world);
            at.setYaw(0);
            at.setPitch(0);
            BlockDisplay display = spawn(at, material, glow, LIT);
            if (display == null) {
                return null;
            }
            Vector3f d = new Vector3f((float) (q.getX() - p.getX()), (float) (q.getY() - p.getY()), (float) (q.getZ() - p.getZ()));
            return new Strand(display, d, width, height, start);
        }

        /** The glow front runs origin → landing in {@link #GROW_TICKS}; each piece grows out from its start. */
        private void growStrand(int since) {
            if (since > GROW_TICKS + 1) {
                return;
            }
            double front = Math.min(1.0, (since + 1) / (double) GROW_TICKS);
            for (Strand piece : strand) {
                if (piece.grown) {
                    continue;
                }
                double k = Math.max(0.0, Math.min(1.0, (front - piece.start) * 3.0));
                if (k <= 0.0) {
                    continue;
                }
                pose(piece.display, piece.shape((float) k, 1f), 1);
                piece.grown = k >= 1.0;
            }
            if (!path.isEmpty() && since <= GROW_TICKS) {
                int idx = (int) Math.min(path.size() - 1, Math.round(front * (path.size() - 1)));
                Location tip = path.get(idx).toLocation(world).add(0, 0.1, 0);
                world.spawnParticle(Particle.BLOCK, tip, 6, 0.2, 0.02, 0.2, 0.05, NYLIUM);
                world.spawnParticle(Particle.DUST, tip, 3, 0.1, 0.05, 0.1, 0, new Particle.DustOptions(GLOW, 0.9f));
                world.playSound(tip, Sound.BLOCK_ROOTS_PLACE, 0.55f, 0.8f + since * 0.2f);
            }
        }

        /** The run is done: the glow drains out of the hyphae. */
        private void coolStrand() {
            for (Strand piece : strand) {
                BlockDisplay display = piece.display;
                if (display != null && display.isValid()) {
                    display.setGlowing(false);
                    display.setBrightness(DIM);
                }
            }
        }

        private void tickSprouts(int since) {
            double front = Math.min(1.0, (since + 1) / (double) GROW_TICKS);
            for (Sprout sprout : sprouts) {
                if (!sprout.up && front >= sprout.at) {
                    sprout.up = true;
                    sprout.upAt = since;
                    pose(sprout.display, plant(new Vector3f(), 0.7f, 0.75f, sprout.yaw, 0f, true), 2);
                    Location at = sprout.display.getLocation();
                    world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.1, 0), 4, 0.12, 0.02, 0.12, 0.03, NYLIUM);
                } else if (sprout.up && !sprout.down && since - sprout.upAt >= 7) {
                    sprout.down = true;
                    pose(sprout.display, plant(new Vector3f(), 0.001f, 0.001f, sprout.yaw, 0f, true), 4);
                }
            }
        }

        // ---------------------------------------------------------------- the fungus

        /** Out of the ground behind the caster: stalk, cap, shroomlights, vines. */
        private void erupt() {
            Location base = destFloor.clone().subtract(travel.clone().multiply(0.6));
            base.setY(groundY(base.toVector(), destFloor.getY()));
            base.setYaw(0);
            base.setPitch(0);
            fungusBase = base;
            capYaw = (float) ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            stalk = spawn(base, Material.WARPED_STEM, null, LIT);
            cap = spawn(base, Material.WARPED_WART_BLOCK, null, LIT);
            dome = spawn(base, Material.WARPED_WART_BLOCK, null, LIT);
            for (int i = 0; i < 3; i++) {
                BlockDisplay light = spawn(base, Material.SHROOMLIGHT, null, LIT);
                if (light != null) {
                    lights.add(light);
                }
            }
            Location arrive = destFloor.clone();
            arrive.setYaw(0);
            arrive.setPitch(0);
            for (int i = 0; i < 3; i++) {
                BlockDisplay vine = spawn(arrive, Material.TWISTING_VINES, null, LIT);
                if (vine != null) {
                    vines.add(vine);
                }
            }

            Location up = destFloor.clone().add(0, 0.2, 0);
            world.spawnParticle(Particle.BLOCK, up, 30, 0.55, 0.05, 0.55, 0.18, NYLIUM);
            world.spawnParticle(Particle.BLOCK, fungusBase.clone().add(0, 0.2, 0), 16, 0.3, 0.05, 0.3, 0.2, WART);
            world.spawnParticle(Particle.WARPED_SPORE, up.clone().add(0, 0.8, 0), 18, 0.4, 0.5, 0.4, 0.03);
            world.spawnParticle(Particle.SCULK_CHARGE_POP, up.clone().add(0, 0.5, 0), 10, 0.45, 0.4, 0.45, 0.02);
            world.playSound(up, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.45f);
            world.playSound(up, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.9f, 1.2f);
            world.playSound(up, Sound.BLOCK_STEM_PLACE, 0.9f, 0.55f);
            world.playSound(up, Sound.BLOCK_FUNGUS_PLACE, 0.8f, 0.7f);
        }

        private void fungus(int since) {
            if (fungusBase == null || since >= WITHER_AT) {
                return;
            }
            float height;
            if (since == 0) {
                height = 1.2f;
            } else {
                height = 2.45f;
            }
            if (since <= 1) {
                pose(stalk, column(0.34f, height), since == 0 ? 1 : 2);
            }

            float capW;
            float capH;
            if (since < 1) {
                capW = 0.001f;
                capH = 0.001f;
            } else if (since < CAP_OPEN) {
                capW = 0.5f + since * 0.5f;
                capH = 0.55f;
            } else if (since == CAP_OPEN) {
                capW = 2.9f;
                capH = 0.3f;
            } else {
                capW = 2.55f;
                capH = 0.45f;
            }
            float yaw = capYaw + since * 0.02f;
            pose(cap, capBox(height, capW, capH, capW, yaw, 0f), 1);
            pose(dome, capBox(height + capH * 0.9f, capW * 0.6f, capH * 0.8f, capW * 0.6f, yaw + 0.4f, 0f), 1);

            if (since == CAP_OPEN) {
                Location top = fungusBase.clone().add(0, height + 0.2, 0);
                world.spawnParticle(Particle.BLOCK, top, 24, 1.1, 0.1, 1.1, 0.1, WART);
                world.spawnParticle(Particle.WARPED_SPORE, top, 24, 1.2, 0.2, 1.2, 0.02);
                world.playSound(top, Sound.BLOCK_WART_BLOCK_PLACE, 0.9f, 0.7f);
                world.playSound(top, Sound.BLOCK_FUNGUS_PLACE, 0.8f, 1.1f);
                world.playSound(top, Sound.BLOCK_SHROOMLIGHT_PLACE, 0.6f, 1.2f);
            }
            if (since >= CAP_OPEN) {
                for (int i = 0; i < lights.size(); i++) {
                    double a = yaw + Math.PI * 2 * i / lights.size() + 0.5;
                    float r = capW * 0.36f;
                    Vector3f at = new Vector3f((float) (Math.cos(a) * r), height - 0.22f, (float) (Math.sin(a) * r));
                    pose(lights.get(i), cube(at, 0.24f), 1);
                }
            }

            float k = Math.min(1f, since / 3f);
            for (int i = 0; i < vines.size(); i++) {
                double a = Math.atan2(travel.getZ(), travel.getX()) + Math.PI * 0.35 + i * Math.PI * 0.65;
                Vector3f at = new Vector3f((float) (Math.cos(a) * 0.85), 0f, (float) (Math.sin(a) * 0.85));
                pose(vines.get(i), plant(at, 0.55f, 1.7f * k, (float) a, (float) Math.toRadians(12), false), 1);
            }
        }

        /** Spores fall out from under the cap; a few shroomlight embers drift with them. */
        private void sporeFall() {
            if (fungusBase == null) {
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location under = fungusBase.clone().add(0, 2.3, 0);
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(0.3, 1.25);
                world.spawnParticle(Particle.FALLING_DUST, under.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r),
                        1, 0, 0, 0, 0, WART);
            }
            world.spawnParticle(Particle.WARPED_SPORE, under, 3, 1.0, 0.2, 1.0, 0.005);
            if (random.nextInt(3) == 0) {
                world.spawnParticle(Particle.DUST, under.clone().add(random.nextDouble(-0.8, 0.8), -0.1,
                        random.nextDouble(-0.8, 0.8)), 1, 0, 0, 0, 0, new Particle.DustOptions(EMBER, 0.7f));
            }
        }

        /** The fungus droops, the cap folds in, and everything pulls back into the floor. */
        private void wither(int since) {
            float k = Math.min(1f, (since - WITHER_AT) / (float) (END_AT - WITHER_AT));
            if (since == WITHER_AT) {
                Location top = fungusBase == null ? destFloor : fungusBase.clone().add(0, 2.4, 0);
                world.playSound(top, Sound.BLOCK_FUNGUS_BREAK, 0.8f, 0.6f);
                world.playSound(top, Sound.BLOCK_WEEPING_VINES_BREAK, 0.6f, 0.8f);
                for (BlockDisplay vine : vines) {
                    pose(vine, plant(new Vector3f(), 0.001f, 0.001f, 0f, 0f, false), 5);
                }
                for (BlockDisplay light : lights) {
                    if (light != null && light.isValid()) {
                        light.setBrightness(DIM);
                    }
                }
                for (Strand piece : strand) {
                    pose(piece.display, piece.shape(1f, 0.001f), END_AT - WITHER_AT);
                    if (piece.display != null && piece.display.isValid()) {
                        piece.display.setBlock(Material.STRIPPED_WARPED_HYPHAE.createBlockData());
                    }
                }
                for (int i = 0; i < patch.size(); i++) {
                    pose(patch.get(i), flatTile(patchSpec.get(i), 0.001f, -0.05f), END_AT - WITHER_AT);
                }
            }
            if (fungusBase != null) {
                float droop = (float) Math.toRadians(28.0 * k);
                float shrink = 1f - k;
                float height = Math.max(0.001f, 2.45f * shrink);
                pose(stalk, column(0.34f * Math.max(0.3f, shrink), height), 1);
                float capW = Math.max(0.001f, 2.55f * shrink);
                float capH = Math.max(0.001f, 0.45f * (1f - 0.5f * k));
                float yaw = capYaw + (WITHER_AT + since) * 0.02f;
                pose(cap, capBox(height, capW, capH, capW, yaw, droop), 1);
                pose(dome, capBox(height + capH * 0.9f, capW * 0.6f, capH * 0.8f, capW * 0.6f, yaw + 0.4f, droop), 1);
                for (int i = 0; i < lights.size(); i++) {
                    double a = yaw + Math.PI * 2 * i / lights.size() + 0.5;
                    float r = capW * 0.36f;
                    Vector3f at = new Vector3f((float) (Math.cos(a) * r), Math.max(0f, height - 0.22f - k * 0.6f),
                            (float) (Math.sin(a) * r));
                    pose(lights.get(i), cube(at, 0.24f * shrink), 1);
                }
                if (since % 2 == 0) {
                    world.spawnParticle(Particle.BLOCK, fungusBase.clone().add(0, height, 0), 4, 0.6 * shrink, 0.1,
                            0.6 * shrink, 0.02, WART);
                }
            }
        }

        private void finish() {
            Location at = fungusBase != null ? fungusBase : destFloor;
            world.spawnParticle(Particle.WARPED_SPORE, at.clone().add(0, 0.4, 0), 14, 0.4, 0.2, 0.4, 0.02);
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.1, 0), 10, 0.3, 0.02, 0.3, 0.03, NYLIUM);
            world.playSound(at, Sound.BLOCK_NYLIUM_STEP, 0.7f, 0.6f);
        }

        private void clear() {
            removeAll(patch);
            removeAll(grip);
            for (Strand piece : strand) {
                discard(piece.display);
            }
            strand.clear();
            for (Sprout sprout : sprouts) {
                discard(sprout.display);
            }
            sprouts.clear();
            removeAll(vines);
            removeAll(lights);
            discard(stalk);
            discard(cap);
            discard(dome);
        }

        /** Floor height under {@code at}, searched from a little above {@code near}; {@code near} over a drop. */
        private double groundY(Vector at, double near) {
            RayTraceResult hit = world.rayTraceBlocks(new Location(world, at.getX(), near + 1.2, at.getZ()),
                    new Vector(0, -1, 0), 3.5, FluidCollisionMode.NEVER, true);
            return hit != null && hit.getHitPosition() != null ? hit.getHitPosition().getY() : near;
        }

        // ---------------------------------------------------------------- shapes

        private Transformation flatTile(float[] spec, float scale, float sink) {
            float s = Math.max(0.001f, spec[2] * scale);
            Quaternionf rot = new Quaternionf().rotateY(spec[3]);
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(s / 2f, 0f, s / 2f));
            Vector3f at = new Vector3f(spec[0], 0.012f + sink, spec[1]).sub(half);
            return new Transformation(at, rot, new Vector3f(s, 0.05f, s), new Quaternionf());
        }

        private Transformation column(float width, float height) {
            float w = Math.max(0.001f, width);
            return new Transformation(new Vector3f(-w / 2f, -0.1f, -w / 2f), new Quaternionf(),
                    new Vector3f(w, Math.max(0.001f, height + 0.1f), w), new Quaternionf());
        }

        /** A box whose bottom centre sits on the stalk top at {@code height}, spun by {@code yaw}, tipped by {@code droop}. */
        private Transformation capBox(float height, float sx, float sy, float sz, float yaw, float droop) {
            Vector side = new Vector(-travel.getZ(), 0, travel.getX());
            Quaternionf rot = new Quaternionf()
                    .rotateAxis(droop, (float) side.getX(), 0f, (float) side.getZ())
                    .rotateY(yaw);
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(sx / 2f, 0f, sz / 2f));
            Vector3f at = new Vector3f(0f, height, 0f).sub(half);
            return new Transformation(at, rot, new Vector3f(Math.max(0.001f, sx), Math.max(0.001f, sy),
                    Math.max(0.001f, sz)), new Quaternionf());
        }

        private Transformation cube(Vector3f center, float size) {
            float s = Math.max(0.001f, size);
            return new Transformation(new Vector3f(center).sub(s / 2f, s / 2f, s / 2f), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf());
        }

        /**
         * A plant model standing on {@code at}: {@code lean} tips it toward the centre when {@code inward},
         * otherwise away from it, so roots grip the feet and vines unfurl outward.
         */
        private Transformation plant(Vector3f at, float width, float height, float angle, float lean, boolean inward) {
            float w = Math.max(0.001f, width);
            float h = Math.max(0.001f, height);
            float tilt = inward ? lean : -lean;
            Vector3f axis = new Vector3f((float) -Math.sin(angle), 0f, (float) Math.cos(angle));
            Quaternionf rot = new Quaternionf().rotateAxis(tilt, axis);
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, w / 2f));
            return new Transformation(new Vector3f(at).sub(half), rot, new Vector3f(w, h, w), new Quaternionf());
        }
    }

    /** One hyphae piece lying along the floor from its anchor, oriented so its thin side faces up. */
    private static final class Strand {
        final BlockDisplay display;
        final Vector3f d;
        final float width;
        final float height;
        final double start;
        final Quaternionf rot;
        boolean grown;

        Strand(BlockDisplay display, Vector3f d, float width, float height, double start) {
            this.display = display;
            this.d = d;
            this.width = width;
            this.height = height;
            this.start = start;
            Vector3f z = d.lengthSquared() < 1.0E-6f ? new Vector3f(0f, 0f, 1f) : new Vector3f(d).normalize();
            Vector3f x = new Vector3f(0f, 1f, 0f).cross(z);
            if (x.lengthSquared() < 1.0E-6f) {
                x.set(1f, 0f, 0f);
            }
            x.normalize();
            Vector3f y = new Vector3f(z).cross(x);
            this.rot = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
        }

        /** {@code grow} extends it along its run; {@code thin} shrinks its cross-section. */
        Transformation shape(float grow, float thin) {
            float len = Math.max(0.001f, d.length() * grow + width * 0.5f);
            float w = Math.max(0.001f, width * thin);
            float h = Math.max(0.001f, height * thin);
            Vector3f back = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, width * 0.25f));
            Vector3f at = new Vector3f(0f, 0.015f, 0f).sub(back);
            return new Transformation(at, new Quaternionf(rot), new Vector3f(w, h, len), new Quaternionf());
        }
    }

    private static final class Sprout {
        final BlockDisplay display;
        final double at;
        final float yaw;
        boolean up;
        boolean down;
        int upAt;

        Sprout(BlockDisplay display, double at, float yaw) {
            this.display = display;
            this.at = at;
            this.yaw = yaw;
        }
    }

    // -------------------------------------------------------------------- helpers

    private static Location floorAt(Location at) {
        World world = at.getWorld();
        Location floor = at.clone();
        floor.setYaw(0);
        floor.setPitch(0);
        if (world == null) {
            return floor;
        }
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 0.5, 0), new Vector(0, -1, 0), 3.0,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            floor.setY(hit.getHitPosition().getY());
        }
        return floor;
    }

    private static void pose(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, Material material, Color glow, Display.Brightness light) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setBrightness(light);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTeleportDuration(1);
                spawned.setInterpolationDuration(1);
                spawned.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void removeAll(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            discard(display);
        }
        displays.clear();
    }

    private static void discard(BlockDisplay display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
                display.remove();
        }
    }

    private Location findLanding(Player player, double range) {
        Location start = player.getLocation();
        Vector look = start.getDirection();
        Vector dir = new Vector(look.getX(), 0, look.getZ());
        if (dir.lengthSquared() < 1.0e-4) {
            dir = look.clone();
        }
        dir.normalize();

        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                start.clone().add(0, 0.2, 0),
                dir,
                range,
                FluidCollisionMode.NEVER,
                true
        );
        double max = range;
        if (hit != null && hit.getHitPosition() != null) {
            max = Math.max(0.6, hit.getHitPosition().distance(start.toVector()) - 0.55);
        }

        Location best = null;
        double stepSize = range > 12.0 ? 0.75 : 1.0;
        for (double step = stepSize; step <= max + 0.05; step += stepSize) {
            Location candidate = start.clone().add(dir.clone().multiply(Math.min(step, max)));
            candidate.setYaw(start.getYaw());
            candidate.setPitch(start.getPitch());
            Location safe = snapToSpace(candidate);
            if (safe != null) {
                best = safe;
            }
        }
        if (best == null) {
            return null;
        }
        if (best.distanceSquared(start) < 0.55) {
            return null;
        }
        return best;
    }

    private Location snapToSpace(Location location) {
        Location feet = location.clone();
        for (int up = 0; up <= 2; up++) {
            Location test = feet.clone().add(0, up, 0);
            if (isSpaceFree(test) && !isHazard(test)) {
                return test;
            }
        }
        return null;
    }

    private boolean isSpaceFree(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    private boolean isHazard(Location location) {
        Material ground = location.clone().add(0, -0.05, 0).getBlock().getType();
        Material feet = location.getBlock().getType();
        return feet == Material.LAVA
                || feet == Material.FIRE
                || feet == Material.SOUL_FIRE
                || ground == Material.LAVA
                || ground == Material.MAGMA_BLOCK;
    }

    private boolean isWarpedBlade(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return "warped_blade".equalsIgnoreCase(itemManager.getItemId(item));
    }
}
