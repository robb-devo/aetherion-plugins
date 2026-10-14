package de.aetherion.gnomon.encounter;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossScript;
import de.aetherion.bossengine.instance.BossState;
import de.aetherion.gnomon.GnomonPlugin;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * The BossEngine contract (same path as Helios): BossEngine keeps the HP pool, phase gates, damage tracker, HUD bar
 * and loot; this script hands every tick, hit and death to the {@link Encounter}.
 */
public final class GnomonScript implements BossScript {

    private final GnomonPlugin plugin;
    private final BossInstance instance;
    private Encounter encounter;
    private boolean orphan;

    public GnomonScript(GnomonPlugin plugin, BossInstance instance) {
        this.plugin = plugin;
        this.instance = instance;
    }

    public Encounter encounter() {
        return encounter;
    }

    @Override
    public void onBind() {
        LivingEntity body = instance.getEntity();
        if (body != null) {
            prepare(body);
        }
        if (encounter != null || orphan) {
            return;
        }
        encounter = plugin.open(instance, this);
        if (encounter == null) {
            orphan = true;
        }
    }

    private static void prepare(LivingEntity e) {
        if (e instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
        }
        e.setGravity(false);
        e.setInvisible(true);
        e.setSilent(true);
        e.setFireTicks(0);
        e.setVelocity(new Vector());
        // Must stay collidable: bows / arrows otherwise pass through the heart (melee still hits).
        e.setCollidable(true);
        e.setRemoveWhenFarAway(false);
        if (e.getEquipment() != null) {
            e.getEquipment().clear();
        }
    }

    @Override
    public boolean tick() {
        if (orphan || encounter == null) {
            if (instance.getState() == BossState.ALIVE) {
                instance.setState(BossState.DESPAWNING);
            }
            return false;
        }
        return encounter.tick();
    }

    @Override
    public boolean beginDeath() {
        return encounter != null && encounter.beginDeath();
    }

    @Override
    public boolean isDying() {
        return encounter != null && encounter.dying();
    }

    @Override
    public boolean blocksDamage() {
        return encounter == null || encounter.blocksDamage();
    }

    @Override
    public void abort() {
        if (encounter != null) {
            plugin.closed(encounter, "engine");
        }
    }

    @Override
    public double incoming(Player player, double amount) {
        return encounter == null ? -1 : encounter.incoming(player, amount);
    }
}
