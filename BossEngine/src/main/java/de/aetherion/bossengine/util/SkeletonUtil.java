package de.aetherion.bossengine.util;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;

import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

public final class SkeletonUtil {

    private SkeletonUtil() {
    }

    public static void prepareBoss(LivingEntity entity) {
        quietLivestock(entity);
        if (!(entity instanceof AbstractSkeleton skeleton)) {
            return;
        }
        skeleton.setShouldBurnInDay(false);
        disableVanillaRanged(skeleton);
    }

    public static void prepareMinion(LivingEntity entity) {
        quietLivestock(entity);
        if (entity instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
    }

    public static void quietLivestock(LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.Ageable ageable) {
            ageable.setAdult();
            ageable.setAgeLock(true);
            ageable.setBreed(false);
        }
        if (entity instanceof org.bukkit.entity.Chicken chicken) {
            chicken.setEggLayTime(Integer.MAX_VALUE);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void disableVanillaRanged(Mob mob) {
        if (mob == null) {
            return;
        }
        try {
            var goals = Bukkit.getMobGoals();
            goals.removeGoal(mob, (GoalKey) VanillaGoal.RANGED_BOW_ATTACK);
            goals.removeGoal(mob, (GoalKey) VanillaGoal.RANGED_CROSSBOW_ATTACK);
            goals.removeGoal(mob, (GoalKey) VanillaGoal.RANGED_ATTACK);
        } catch (RuntimeException ignored) {
        }
    }
}
