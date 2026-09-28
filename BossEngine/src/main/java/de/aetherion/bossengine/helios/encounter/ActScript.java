package de.aetherion.bossengine.helios.encounter;

import de.aetherion.bossengine.helios.HeliosModule;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.BossScript;
import de.aetherion.bossengine.instance.BossState;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Shared spine of both acts: finds its instance, owns the invisible hitbox, runs the attack
 * scheduler with pacing (moves land on the beat, quiet bars between flurries, one hazard family at a
 * time) and routes phase changes and death into the concrete act.
 */
public abstract class ActScript implements BossScript {

    public enum Mode { NONE, INTRO, FIGHT, INTERLUDE, DYING, DONE }

    /** A move the scheduler may pick. */
    public record Choice(String id, Supplier<Attack> factory, int weight, int cooldownBeats, Attack.Family family) {
    }

    protected final HeliosModule module;
    protected final BossInstance instance;
    protected HeliosEncounter enc;
    protected Mode mode = Mode.NONE;
    protected int modeTick;
    protected int clock;

    protected final List<Attack> running = new ArrayList<>();
    private final java.util.Map<String, Integer> readyAt = new java.util.HashMap<>();
    private int nextPickAt;
    private int flurry;
    private String lastPick = "";
    private String phaseId = "";
    private boolean orphan;

    /** Hitbox position in stage coordinates (feet of the invisible body). */
    protected final Vector3f hitbox = new Vector3f();

    protected ActScript(HeliosModule module, BossInstance instance) {
        this.module = module;
        this.instance = instance;
    }

    public HeliosEncounter encounter() {
        return enc;
    }

    public BossInstance instance() {
        return instance;
    }

    public Mode mode() {
        return mode;
    }

    /* ================================================================== engine hooks */

    @Override
    public void onBind() {
        LivingEntity body = instance.getEntity();
        if (body == null) {
            return;
        }
        prepareBody(body);
        if (enc != null) {
            return;
        }
        enc = module.encounterAt(instance.getSpawnLocation());
        if (enc == null) {
            // Spawned outside an instance (/boss spawn): there is no arena to fight in.
            orphan = true;
            return;
        }
        enc.attach(this);
        BossPhase p = instance.getCurrentPhase();
        phaseId = p == null ? "" : p.getId();
        begin();
    }

    @Override
    public boolean tick() {
        if (orphan) {
            if (instance.getState() == BossState.ALIVE) {
                instance.setState(BossState.DESPAWNING);
            }
            return false;
        }
        if (enc == null || enc.closed()) {
            return false;
        }
        clock++;
        modeTick++;
        LivingEntity body = instance.getEntity();
        if (body != null && body.isValid()) {
            maintainBody(body);
        }
        if (mode == Mode.FIGHT) {
            checkPhase();
        }
        boolean finished = false;
        switch (mode) {
            case INTRO -> tickIntro();
            case FIGHT -> {
                tickFight();
                schedule();
                stepAttacks();
            }
            case INTERLUDE -> {
                stepAttacks();
                tickInterlude();
            }
            case DYING -> finished = tickDeath();
            default -> {
            }
        }
        tickBody();
        placeHitbox();
        if (finished) {
            mode = Mode.DONE;
        }
        return finished;
    }

    @Override
    public boolean beginDeath() {
        if (orphan || enc == null) {
            return false;
        }
        if (mode == Mode.DYING || mode == Mode.DONE) {
            return true;
        }
        endAttacks();
        mode = Mode.DYING;
        modeTick = 0;
        LivingEntity body = instance.getEntity();
        if (body != null) {
            body.setInvulnerable(true);
        }
        onDeathStart();
        return true;
    }

    @Override
    public boolean isDying() {
        return mode == Mode.DYING;
    }

    @Override
    public boolean blocksDamage() {
        return mode != Mode.FIGHT || shielded();
    }

    @Override
    public void abort() {
        endAttacks();
        clearBody();
        mode = Mode.DONE;
    }

    @Override
    public boolean ownsBossBar() {
        return true;
    }

    @Override
    public double incoming(Player player, double amount) {
        if (enc == null || !enc.party().contains(player)) {
            return -1;
        }
        return reshape(player, amount);
    }

    /* ================================================================== act API */

    /** First bind inside an instance: start the intro. */
    protected abstract void begin();

    protected abstract void tickIntro();

    protected abstract void tickFight();

    protected void tickInterlude() {
    }

    /** Called every tick in every mode after the mode logic: animate the body. */
    protected abstract void tickBody();

    protected abstract void onDeathStart();

    /** @return true when the death cinematic finished */
    protected abstract boolean tickDeath();

    protected abstract void onPhase(String from, String to);

    /** Moves the scheduler may pick right now. */
    protected abstract List<Choice> choices();

    /** Remove the body's displays (abort / end). */
    protected abstract void clearBody();

    /** Extra damage block (e.g. while a shield or a clone game is up). */
    protected boolean shielded() {
        return false;
    }

    protected double reshape(Player player, double amount) {
        return amount;
    }

    /** How many attacks may run at once (the boss body is always at most one of them). */
    protected int maxConcurrent() {
        return 1;
    }

    /** Beats of rest after a flurry of {@link #flurryLength()} moves. */
    protected int restBeats() {
        return 4;
    }

    protected int flurryLength() {
        return 3;
    }

    /* ================================================================== scheduler */

    protected void enterFight() {
        mode = Mode.FIGHT;
        modeTick = 0;
        nextPickAt = clock + enc.tempo().ticksUntil(1);
        enc.cinematic(false);
    }

    protected void enterInterlude() {
        endAttacks();
        mode = Mode.INTERLUDE;
        modeTick = 0;
    }

    /** Delay the next pick (a scripted beat, a stagger). */
    protected void holdScheduler(int ticks) {
        nextPickAt = Math.max(nextPickAt, clock + ticks);
    }

    private void schedule() {
        if (clock < nextPickAt || enc.cinematic() || paused()) {
            return;
        }
        int cap = maxConcurrent();
        if (running.size() >= cap) {
            return;
        }
        boolean bodyBusy = false;
        Set<Attack.Family> busy = EnumSet.noneOf(Attack.Family.class);
        for (Attack a : running) {
            busy.add(a.family());
            bodyBusy |= a.usesBody();
        }
        List<Choice> pool = new ArrayList<>();
        int total = 0;
        for (Choice c : choices()) {
            if (busy.contains(c.family()) || (bodyBusy && c.family() == Attack.Family.BODY)) {
                continue;
            }
            if (clock < readyAt.getOrDefault(c.id(), 0)) {
                continue;
            }
            if (c.id().equals(lastPick) && choices().size() > 2) {
                continue;
            }
            pool.add(c);
            total += Math.max(1, c.weight());
        }
        if (pool.isEmpty()) {
            nextPickAt = clock + 5;
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        Choice pick = pool.get(pool.size() - 1);
        for (Choice c : pool) {
            roll -= Math.max(1, c.weight());
            if (roll < 0) {
                pick = c;
                break;
            }
        }
        start(pick.factory().get(), pick.id(), pick.cooldownBeats());
    }

    /** True while a set piece owns the stage (the scheduler waits). */
    protected boolean paused() {
        return false;
    }

    /** Runs an extra hazard alongside everything else, outside the scheduler's bookkeeping. */
    public void spawnHazard(Attack a) {
        if (a != null) {
            a.start();
            running.add(a);
        }
    }

    /** Starts an attack right now (scheduler pick, admin test, scripted beat). */
    public void start(Attack a, String id, int cooldownBeats) {
        if (a == null) {
            return;
        }
        a.start();
        running.add(a);
        lastPick = id;
        readyAt.put(id, clock + enc.tempo().ticks(Math.max(1, cooldownBeats)));
        flurry++;
        if (flurry >= flurryLength()) {
            flurry = 0;
            nextPickAt = clock + enc.tempo().ticks(restBeats());
        } else {
            // The next move counts in on the beat after a short breath.
            nextPickAt = clock + enc.tempo().ticksUntil(1);
        }
    }

    private void stepAttacks() {
        for (Iterator<Attack> it = running.iterator(); it.hasNext(); ) {
            Attack a = it.next();
            boolean done;
            try {
                done = a.step();
            } catch (RuntimeException ex) {
                module.plugin().getLogger().warning("[Helios] attack " + a.id() + " failed: " + ex);
                done = true;
            }
            if (done) {
                a.end();
                it.remove();
                if (a.usesBody()) {
                    nextPickAt = Math.max(nextPickAt, clock + a.recovery());
                }
            }
        }
    }

    protected void endAttacks() {
        for (Attack a : running) {
            a.end();
        }
        running.clear();
    }

    public List<Attack> running() {
        return running;
    }

    public boolean bodyBusy() {
        for (Attack a : running) {
            if (a.usesBody()) {
                return true;
            }
        }
        return false;
    }

    /** Admin: the ids this act knows (for /helios attack). */
    public List<String> attackIds() {
        List<String> ids = new ArrayList<>();
        for (Choice c : allChoices()) {
            ids.add(c.id());
        }
        return ids;
    }

    /** Every move of the act regardless of phase (admin testing). */
    protected abstract List<Choice> allChoices();

    public boolean forceAttack(String id) {
        if (mode != Mode.FIGHT) {
            return false;
        }
        for (Choice c : allChoices()) {
            if (c.id().equalsIgnoreCase(id)) {
                if (c.family() == Attack.Family.BODY) {
                    for (Attack a : new ArrayList<>(running)) {
                        if (a.usesBody()) {
                            a.end();
                            running.remove(a);
                        }
                    }
                }
                start(c.factory().get(), c.id(), c.cooldownBeats());
                return true;
            }
        }
        return false;
    }

    private void checkPhase() {
        BossPhase p = instance.getCurrentPhase();
        String now = p == null ? "" : p.getId();
        if (!now.equals(phaseId)) {
            String from = phaseId;
            phaseId = now;
            onPhase(from, now);
        }
    }

    protected String phaseId() {
        return phaseId;
    }

    /* ================================================================== body */

    private void prepareBody(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
        }
        entity.setGravity(false);
        entity.setInvisible(true);
        entity.setSilent(true);
        entity.setFireTicks(0);
        entity.setVelocity(new Vector());
        entity.setCollidable(false);
        if (entity.getEquipment() != null) {
            entity.getEquipment().clear();
        }
    }

    private void maintainBody(LivingEntity entity) {
        if (entity instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
        }
        entity.setFireTicks(0);
        entity.setFallDistance(0f);
        boolean invulnerable = blocksDamage();
        if (entity.isInvulnerable() != invulnerable) {
            entity.setInvulnerable(invulnerable);
        }
    }

    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || enc == null) {
            return;
        }
        HeliosStage stage = enc.stage();
        Location at = stage.at(hitbox);
        at.setYaw(0f);
        at.setPitch(0f);
        Location current = entity.getLocation();
        if (current.getWorld() != at.getWorld() || current.distanceSquared(at) > 0.0025) {
            instance.runInternalTeleport(() -> entity.teleport(at));
        }
        entity.setVelocity(new Vector());
    }
}
