package de.aetherion.tutorial.isle;

import de.aetherion.tutorial.TutorialPlugin;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The animals of the island: sheep in Wren's fold, hens in the yard of Downs Farm, a cat by the well. They are
 * scenery. Nothing spawns on Morningbell by itself (the lessons must never be interrupted by a zombie), so these
 * few are put there, cannot be hurt, and are put back when the server has cleared them with their chunk.
 */
final class Ambient {

    static final String TAG = "ae_morningbell_life";

    private record Herd(String mark, EntityType type, int count, double spread, double roam) {
    }

    private static final List<Herd> HERDS = List.of(
            new Herd("sheep_fold", EntityType.SHEEP, 4, 1.6, 0.0),
            new Herd("farm_yard", EntityType.CHICKEN, 4, 2.5, 7.0),
            new Herd("well", EntityType.CAT, 1, 1.5, 6.0));

    private final TutorialPlugin plugin;
    private final Isle isle;

    Ambient(TutorialPlugin plugin, Isle isle) {
        this.plugin = plugin;
        this.isle = isle;
        for (Entity entity : isle.world().getEntities()) {
            if (entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }

    /** Every ten seconds: whoever is missing is put back, whoever strayed is called home. */
    void tend() {
        if (!plugin.getConfig().getBoolean("isle.animals", true)) {
            return;
        }
        for (Herd herd : HERDS) {
            Location home = isle.marks().at(isle.world(), herd.mark());
            if (home == null || !home.isChunkLoaded()) {
                continue;
            }
            int here = 0;
            for (Entity entity : isle.world().getNearbyEntities(home, 24, 12, 24)) {
                if (entity.getType() != herd.type() || !entity.getScoreboardTags().contains(TAG)) {
                    continue;
                }
                here++;
                if (herd.roam() > 0.0 && entity instanceof Mob mob
                        && entity.getLocation().distanceSquared(home) > herd.roam() * herd.roam()) {
                    try {
                        mob.getPathfinder().moveTo(home, 1.0);
                    } catch (RuntimeException ignored) {
                        // no way back on foot from where it stands: it stays where it is until its chunk unloads
                    }
                }
            }
            for (int i = here; i < herd.count(); i++) {
                spawn(herd, home);
            }
        }
    }

    private void spawn(Herd herd, Location home) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = home.clone().add(random.nextDouble(-herd.spread(), herd.spread()), 0.0,
                random.nextDouble(-herd.spread(), herd.spread()));
        at.setYaw(random.nextFloat() * 360f);
        try {
            isle.world().spawnEntity(at, herd.type(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
                entity.addScoreboardTag(TAG);
                // Items dresses every wild animal with a health label and counts it as wildlife: "NPC" opts out
                entity.addScoreboardTag("NPC");
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                if (entity instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                    living.setCanPickupItems(false);
                }
                if (entity instanceof Ageable ageable) {
                    ageable.setAdult();
                }
                if (entity instanceof org.bukkit.entity.Breedable breedable) {
                    breedable.setAgeLock(true);
                    breedable.setBreed(false);
                }
                if (entity instanceof Sheep sheep) {
                    sheep.setColor(random.nextInt(5) == 0 ? DyeColor.BLACK : DyeColor.WHITE);
                }
                if (entity instanceof Cat cat) {
                    cat.setCatType(Cat.Type.RED);
                    cat.setCollarColor(DyeColor.RED);
                }
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Could not put a " + herd.type() + " at " + herd.mark() + ": " + ex);
        }
    }

    void clear() {
        for (Entity entity : isle.world().getEntities()) {
            if (entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }
}
