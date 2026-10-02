package de.aetherion.foraging.isle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Widowmaker — tall trees don't always come down clean. A moment after the fell, a loose limb
 * lets go right above where you stand: bark dust trickles and the trunk groans (the tell). Stay put and
 * it lands on you (it hurts, it never kills). Step out of the dust and it's a <b>Deadfall</b>: a free
 * armful of that wood, outside the tree's cap, and your streak never notices.
 *
 * <p>Tall, heavy woods (jungle, dark oak, mangrove) do it more. Deadfall Dancer gives you a longer tell,
 * a softer hit and doubles the deadfall.
 */
public final class Widowmaker {

    private static final String TAG = "ae_grove_limb";

    private final ForageIsle isle;

    Widowmaker(ForageIsle isle) {
        this.isle = isle;
    }

    void onFell(Player player, FellContext ctx) {
        if (ctx.cleaver() || ctx.logs() < isle.config().tuningInt("widowmaker-min-logs", 14)) {
            return;
        }
        double chance = isle.config().tuning("widowmaker-chance", 0.12d);
        if (ctx.wood() == Wood.JUNGLE || ctx.wood() == Wood.DARK_OAK || ctx.wood() == Wood.MANGROVE) {
            chance *= 1.6d;
        }
        if (ctx.titan()) {
            chance *= 1.5d;
        }
        if (ThreadLocalRandom.current().nextDouble() >= Math.min(0.6d, chance)) {
            return;
        }
        long delay = 14L + ThreadLocalRandom.current().nextInt(12);
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> telegraph(player, ctx), delay);
    }

    private void telegraph(Player player, FellContext ctx) {
        if (!player.isOnline() || ctx.anchor().getWorld() != player.getWorld()
                || player.getLocation().distanceSquared(ctx.anchor()) > 14 * 14) {
            return;
        }
        double dancer = ForageBridge.scale(player, ForageBridge.DEADFALL_DANCER);
        int tell = 26 + (dancer > 0 ? 10 : 0);
        Location spot = player.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        World world = spot.getWorld();
        Material wood = ctx.wood() == null ? Material.OAK_LOG : ctx.wood().log();
        BlockData dust = wood.createBlockData();
        world.playSound(spot, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.7f, 0.5f);
        world.playSound(spot, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
        ForageText.bar(player, "§c⚠ Widowmaker §7— a limb is loose above you. §fMove!");
        for (int t = 0; t < tell; t += 4) {
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () ->
                    world.spawnParticle(Particle.FALLING_DUST, spot.clone().add(0, 5.5, 0), 6, 0.6, 0.2, 0.6, 0.0, dust), t);
        }
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> drop(player, ctx, spot, wood, dancer), tell);
    }

    private void drop(Player player, FellContext ctx, Location spot, Material wood, double dancer) {
        World world = spot.getWorld();
        if (world == null) {
            return;
        }
        BlockData data = wood.createBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(org.bukkit.Axis.X);
        }
        Location top = spot.clone().add(-1.0, 6.0, -0.5);
        BlockDisplay limb = world.spawn(top, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f, 0.7f, 0.7f), new Quaternionf()));
            d.setTeleportDuration(5);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (limb.isValid()) {
                limb.teleport(spot.clone().add(-1.0, 0.05, -0.5));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> land(player, ctx, spot, wood, dancer, limb), 6L);
    }

    private void land(Player player, FellContext ctx, Location spot, Material wood, double dancer, BlockDisplay limb) {
        World world = spot.getWorld();
        world.playSound(spot, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.3f, 0.6f);
        world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.25f, 1.6f);
        world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.4, 0), 24, 0.8, 0.2, 0.4, 0.1, wood.createBlockData());
        Bukkit.getScheduler().runTaskLater(isle.plugin(), limb::remove, 30L);
        if (!player.isOnline() || player.getWorld() != world) {
            return;
        }
        Location feet = player.getLocation();
        double dx = feet.getX() - spot.getX();
        double dz = feet.getZ() - spot.getZ();
        boolean hit = dx * dx + dz * dz <= 1.4d * 1.4d && Math.abs(feet.getY() - spot.getY()) < 2.5d;
        ForageProfile profile = isle.profiles().get(player);
        if (hit) {
            double damage = 6.0d * (dancer > 0 ? 0.5d : 1.0d) * isle.woodwright().hazardFactor(profile);
            double floor = 2.0d;
            double allowed = Math.max(0.0d, player.getHealth() - floor);
            if (allowed > 0.0d) {
                player.damage(Math.min(damage, allowed));
            }
            player.setVelocity(new Vector(dx, 0.25, dz).normalize().multiply(0.45).setY(0.3));
            ForageText.bar(player, "§c✖ Widowmaker! §7The limb found you. §8(Watch for the bark dust.)");
            return;
        }
        int logs = 2 + ThreadLocalRandom.current().nextInt(3);
        if (dancer > 0) {
            logs *= 2;
        }
        ForageBridge.give(player, new ItemStack(wood, logs), spot);
        profile.deadfalls++;
        profile.dirty = true;
        isle.deadfall(player);
        ForageText.bar(player, "§a✔ Deadfall §7— the limb missed. §f+" + logs + " " + ForageText.pretty(wood.name().replace("_LOG", "").replace("_BLOCK", ""))
                + " §7outside the cap.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    static boolean isOurs(org.bukkit.entity.Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }
}
