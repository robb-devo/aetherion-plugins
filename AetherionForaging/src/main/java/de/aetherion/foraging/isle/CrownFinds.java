package de.aetherion.foraging.isle;

import de.aetherion.foraging.isle.ForageItems.Find;
import de.aetherion.foraging.isle.ForageItems.FindData;
import de.aetherion.foraging.isle.ForageItems.Grade;

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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crown Finds — the rare find of Foraging. When a tree comes down, now and then its crown lets go of
 * something: a glowing find that drifts down towards the stump. Step under it and <b>catch it in the
 * air</b> for a heavier specimen, or pick it up where it lands before it fades (8 s). The finder has
 * it to themselves for four seconds; after that anyone nearby may grab it.
 *
 * <p>One find per district ({@link Find}), four grades. They sell at the Archivist, fill Board orders,
 * pay for the Woodwright's top tiers and fill the Find Cabinet. The heaviest of each kind is an isle
 * record. Cost on the server: one ItemDisplay per falling find and a few particles.
 */
public final class CrownFinds implements Runnable {

    private static final double CATCH_RADIUS_SQ = 1.9d * 1.9d;
    private static final int LANDED_TICKS = 160;
    private static final int PRIVATE_TICKS = 80;
    private static final String TAG = "ae_grove_find";

    private final ForageIsle isle;
    private final List<Drop> drops = new ArrayList<>();

    private static final class Drop {
        UUID finder;
        FindData data;
        ItemDisplay body;
        Location at;
        double groundY;
        double fallPerTick;
        double driftX;
        double driftZ;
        int age;
        int landedAge = -1;
        float spin;
    }

    CrownFinds(ForageIsle isle) {
        this.isle = isle;
    }

    public int active() {
        return drops.size();
    }

    /**
     * Chance a fell drops a find. Everything that tunes it lives here so the journal can show the same
     * number the roll uses.
     */
    public double chance(Player player, ForageProfile profile, FellContext ctx, Grove grove) {
        double chance = isle.config().tuning("crown-find-chance", 0.07d);
        if (ctx.perfect()) {
            chance += 0.03d;
        }
        if (ctx.titan()) {
            chance += 0.10d;
        }
        chance += profile.mark(Woodwright.CANOPY_EYE) * 0.012d;
        chance *= 1.0d + 0.5d * ForageBridge.scale(player, ForageBridge.SAP_SENSE);
        if (GroveMastery.findBoost(profile, ctx.wood())) {
            chance *= 1.5d;
        }
        chance *= isle.events().findMultiplier(player);
        chance *= isle.weatherFindFactor(player, grove);
        if (isle.lured(ctx.anchor())) {
            chance *= 3.0d;
        }
        return Math.min(0.9d, chance);
    }

    /** Called once per isle fell. */
    void onFell(Player player, ForageProfile profile, FellContext ctx, Grove grove) {
        boolean shaker = profile.charge(Woodwright.CHARGE_SHAKER) > 0;
        double chance = chance(player, profile, ctx, grove);
        if (!shaker && ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        if (shaker) {
            profile.addCharge(Woodwright.CHARGE_SHAKER, -1);
            ForageText.bar(player, "§e✦ Crown Shaker §7— something's loose up there…");
        }
        double luck = ForageBridge.scale(player, ForageBridge.SAP_SENSE) + profile.mark(Woodwright.CANOPY_EYE) * 0.3d
                + (ctx.titan() ? 1.0d : 0.0d) + (ctx.perfect() ? 0.5d : 0.0d) + isle.events().gradeLuck();
        Find find = Find.of(grove == null ? ctx.wood() == null ? Grove.ELDERWOOD : ctx.wood().grove() : grove);
        drop(player, ForageItems.roll(find, rollGrade(luck)), ctx.crown(), ctx.anchor());
    }

    public static Grade rollGrade(double luck) {
        double l = Math.max(0.0d, luck);
        double heartsong = 0.5d + 0.35d * l;
        double pristine = 6.5d + 2.5d * l;
        double fine = 23.0d + 5.0d * l;
        double rough = Math.max(20.0d, 70.0d - 7.85d * l);
        double roll = ThreadLocalRandom.current().nextDouble() * (heartsong + pristine + fine + rough);
        if ((roll -= heartsong) < 0) {
            return Grade.HEARTSONG;
        }
        if ((roll -= pristine) < 0) {
            return Grade.PRISTINE;
        }
        if ((roll -= fine) < 0) {
            return Grade.FINE;
        }
        return Grade.ROUGH;
    }

    /** Spawns a drifting find from {@code crown} towards the stump at {@code base}. */
    public void drop(Player finder, FindData data, Location crown, Location base) {
        if (crown == null || crown.getWorld() == null || base == null) {
            return;
        }
        World world = crown.getWorld();
        Location start = crown.clone();
        double groundY = groundBelow(world, base.getBlockX(), base.getBlockZ(), start.getBlockY(), base.getBlockY() - 4);
        if (start.getY() < groundY + 2.0d) {
            start.setY(groundY + 2.0d);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double landX = base.getBlockX() + 0.5d + r.nextDouble(-1.5d, 1.5d);
        double landZ = base.getBlockZ() + 0.5d + r.nextDouble(-1.5d, 1.5d);
        double height = start.getY() - groundY;
        int flightTicks = (int) Math.max(50, Math.min(110, height * 4.0d));
        Drop drop = new Drop();
        drop.finder = finder == null ? null : finder.getUniqueId();
        drop.data = data;
        drop.at = start;
        drop.groundY = groundY + 0.35d;
        drop.fallPerTick = (start.getY() - drop.groundY) / flightTicks;
        drop.driftX = (landX - start.getX()) / flightTicks;
        drop.driftZ = (landZ - start.getZ()) / flightTicks;
        ItemStack icon = new ItemStack(data.find().icon);
        Color glow = glow(data.grade());
        drop.body = world.spawn(start, ItemDisplay.class, spawned -> {
            spawned.setItemStack(icon);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(glow);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(scale(0.7f, 0.0f));
            spawned.setTeleportDuration(2);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(TAG);
        });
        drops.add(drop);
        world.playSound(start, Sound.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 1.0f, 0.7f);
        if (finder != null) {
            finder.playSound(finder.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9f,
                    data.grade().atLeast(Grade.PRISTINE) ? 0.6f : 1.4f);
            ForageText.bar(finder, "§e✦ Crown Find §7falling — " + data.grade().colored() + " " + data.find().colored()
                    + " §8· §7look up!");
        }
    }

    @Override
    public void run() {
        if (drops.isEmpty()) {
            return;
        }
        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop drop = it.next();
            if (drop.body == null || !drop.body.isValid() || drop.at.getWorld() == null) {
                it.remove();
                continue;
            }
            drop.age += 2;
            boolean landed = drop.landedAge >= 0;
            if (!landed) {
                drop.at.add(drop.driftX * 2.0d, -drop.fallPerTick * 2.0d, drop.driftZ * 2.0d);
                if (drop.at.getY() <= drop.groundY) {
                    drop.at.setY(drop.groundY);
                    drop.landedAge = 0;
                    drop.at.getWorld().playSound(drop.at, Sound.BLOCK_MOSS_CARPET_PLACE, SoundCategory.BLOCKS, 0.8f, 1.2f);
                }
            } else {
                drop.landedAge += 2;
            }
            drop.spin += 0.35f;
            drop.body.teleport(drop.at);
            drop.body.setTransformation(scale(landed ? 0.55f : 0.7f, drop.spin));
            if (drop.age % 6 == 0) {
                drop.at.getWorld().spawnParticle(landed ? Particle.GLOW : Particle.END_ROD, drop.at.clone().add(0, 0.2, 0),
                        1, 0.08, 0.08, 0.08, 0.0);
            }
            Player catcher = catcher(drop);
            if (catcher != null) {
                catchIt(catcher, drop, !landed);
                it.remove();
                continue;
            }
            if (landed && drop.landedAge > LANDED_TICKS) {
                drop.at.getWorld().spawnParticle(Particle.SMOKE, drop.at, 6, 0.15, 0.1, 0.15, 0.01);
                Player finder = drop.finder == null ? null : Bukkit.getPlayer(drop.finder);
                if (finder != null) {
                    ForageText.bar(finder, "§7The " + drop.data.find().display + " §7wilted before anyone picked it up.");
                }
                drop.body.remove();
                it.remove();
            }
        }
    }

    private Player catcher(Drop drop) {
        Player best = null;
        double bestDist = CATCH_RADIUS_SQ;
        boolean open = drop.age >= PRIVATE_TICKS || drop.finder == null;
        for (Player player : drop.at.getWorld().getPlayers()) {
            if (!open && !player.getUniqueId().equals(drop.finder)) {
                continue;
            }
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            Location body = player.getLocation().add(0, 1.0, 0);
            double d = body.distanceSquared(drop.at);
            if (d <= bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private void catchIt(Player player, Drop drop, boolean midAir) {
        FindData data = drop.data;
        if (midAir) {
            int grams = (int) Math.round(data.grams() * 1.10d);
            data = new FindData(data.find(), data.grade(), grams);
        }
        drop.body.remove();
        ForageBridge.give(player, ForageItems.find(data), player.getLocation());
        ForageProfile profile = isle.profiles().get(player);
        profile.finds++;
        profile.dirty = true;
        isle.ledger().noteFind(player, profile, data);
        isle.board().noteFind(player, profile, data);
        boolean record = isle.profiles().offerRecord("find." + data.find().id(), player, data.grams(), data.grade().display);
        String head = midAir ? "§e✦ Caught mid-air! §8(+10% weight) §8· " : "§e✦ Crown Find §8· ";
        player.sendMessage(head + data.grade().colored() + " " + data.find().colored() + " §8· §f" + ForageText.grams(data.grams())
                + (record ? " §6§l★ isle record" : ""));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, midAir ? 1.6f : 1.1f);
        if (data.grade().atLeast(Grade.PRISTINE)) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
        if (data.grade() == Grade.HEARTSONG) {
            for (Player other : isle.playersOnIsle()) {
                other.sendMessage("§6§l✦ HEARTSONG §8· §f" + player.getName() + " §7caught a " + data.find().colored()
                        + " §7(" + ForageText.grams(data.grams()) + ")");
            }
        }
        ForageBridge.bonus(player, 6 * data.grade().valueMult);
    }

    /** Removes every falling find (disable / reload). */
    void clear() {
        for (Drop drop : drops) {
            if (drop.body != null && drop.body.isValid()) {
                drop.body.remove();
            }
        }
        drops.clear();
    }

    static boolean isOurs(org.bukkit.entity.Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }

    private static double groundBelow(World world, int x, int z, int fromY, int toY) {
        for (int y = fromY; y >= toY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            String name = type.name();
            if (name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")) {
                continue;
            }
            return y + 1;
        }
        return toY + 4;
    }

    private static Color glow(Grade grade) {
        return switch (grade) {
            case ROUGH -> Color.fromRGB(200, 230, 190);
            case FINE -> Color.fromRGB(110, 255, 120);
            case PRISTINE -> Color.fromRGB(90, 220, 255);
            case HEARTSONG -> Color.fromRGB(255, 190, 60);
        };
    }

    private static Transformation scale(float s, float spin) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateY(spin), new Vector3f(s, s, s), new Quaternionf());
    }
}
