package de.aetherion.aethermobs.pet;

import de.aetherion.core.AetherKeys;
import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.items.model.Rarity;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.HeightMap;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PetEntity {

    public static final NamespacedKey PET_ENTITY_KEY = AetherKeys.PET_ENTITY;

    /** Ties a temporary nameplate to its ItemDisplay so a respawn can drop the old one. */
    private static final NamespacedKey NAMEPLATE_OWNER =
            AetherKeys.namespaced("aethermobs", "pet_nameplate_owner");

    public static final PersistentDataType<Byte, Byte>
            PET_ENTITY_KEY_TYPE =
            PersistentDataType.BYTE;

    public static final double HOVER_HEIGHT = 1.2;

    /** Low corridors / swimming: sit mid-body beside the owner. */
    private static final double HOVER_HEIGHT_TIGHT = 0.45;

    private static final double HOVER_RANGE = 0.12;

    private static final double FOLLOW_DISTANCE = 1.85;

    private static final double FOLLOW_SIDE = 1.15;

    private static final double TELEPORT_DISTANCE = 14.0;

    private static final double FOLLOW_SPEED = 0.20;

    /** Equipped glance: message + soft sound + hearts together, very rare. */
    private static final long HAPPY_GLANCE_COOLDOWN_MS = 3_600_000L;

    private static final double HAPPY_GLANCE_CHANCE = 0.0015;

    private static final java.util.Map<UUID, Long> LAST_HAPPY_GLANCE_BY_OWNER =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final double WILD_SPEED = 0.10;

    private static final double WILD_FLEE_SPEED = 0.16;

    private static final double WILD_FLEE_DISTANCE = 6.0;

    private static final double SURFACE_Y_STEP = 0.16;

    private static final double FOLLOW_Y_STEP = 0.18;

    /** Vanilla-like: one block up is fine, walls are not. */
    private static final int MAX_SURFACE_STEP_UP = 1;

    /** Allow a short drop so hills/stairs still feel natural. */
    private static final int MAX_SURFACE_STEP_DOWN = 4;

    private static final float LOOK_TURN_STEP = 10.0F;

    private static final int WILD_DIRECTION_MIN_TICKS = 40;

    private static final int WILD_DIRECTION_MAX_TICKS = 100;

    private static final int SHINY_PARTICLE_INTERVAL = 4;

    private static final int AURA_PARTICLE_INTERVAL = 6;

    private static final double SHINY_PARTICLE_HEIGHT = 0.35;

    private static final Particle.DustOptions AETHERION_AURA =
            new Particle.DustOptions(
                    Color.fromRGB(168, 92, 255),
                    0.7F
            );

    private static final Particle.DustOptions AETHERED_AURA =
            new Particle.DustOptions(
                    Color.fromRGB(176, 20, 40),
                    0.65F
            );

    private final PetInstance petInstance;

    private final Player owner;

    /** NPC / world companion: follows a location the same way equipped pets follow players. */
    private java.util.function.Supplier<Location> companionHost;

    private ItemDisplay entity;
    private TextDisplay nameplate;

    private BukkitRunnable movementTask;

    private double hoverTime;

    private int shinyParticleStep;

    private int auraParticleStep;

    private Vector wildDirection;

    private Vector followAlong;

    private Location lastFollowFrom;

    private int wildDirectionStep;

    private boolean catching;
    private int catchAttempts;

    private double wildBaseY;

    private double lastHoverBob;

    private float lookYaw;

    private int lookTicks;

    private double smoothedHoverY;

    private boolean ignoreHabitatRules;

    private UUID reservedCatcher;
    private boolean guaranteedCatch;

    private double orbitAngle;


    public PetEntity(
            PetInstance petInstance
    ) {

        this(
                petInstance,
                null
        );
    }


    public PetEntity(
            PetInstance petInstance,
            Player owner
    ) {

        this.petInstance = petInstance;

        this.owner = owner;

        this.hoverTime =
                ThreadLocalRandom.current()
                        .nextDouble(
                                0.0,
                                Math.PI * 2
                        );

        this.shinyParticleStep = 0;

        this.auraParticleStep = 0;

        this.wildDirection =
                createRandomWildDirection(
                        getSpawnType()
                );

        this.wildDirectionStep =
                ThreadLocalRandom.current()
                        .nextInt(
                                WILD_DIRECTION_MIN_TICKS,
                                WILD_DIRECTION_MAX_TICKS
                        );

        this.catching = false;
        this.catchAttempts = 0;
        this.orbitAngle =
                ThreadLocalRandom.current()
                        .nextDouble(
                                0.0,
                                Math.PI * 2
                        );

        this.wildBaseY = Double.NaN;

        this.lookYaw =
                ThreadLocalRandom.current()
                        .nextFloat()
                        * 360.0F;

        this.lookTicks =
                ThreadLocalRandom.current()
                        .nextInt(
                                12,
                                40
                        );

        this.smoothedHoverY = Double.NaN;
    }


    private PetSpawnType getSpawnType() {

        if (petInstance == null
                || petInstance.getDefinition() == null) {

            return PetSpawnType.SURFACE;
        }

        // Factory config only — never force bee/pigeon/etc. to SKY by id.
        return petInstance.getDefinition().getSpawnType();
    }


    private boolean isEquipped() {

        return owner != null;
    }


    public void spawn(
            Location location
    ) {

        spawn(
                location,
                false
        );
    }


    public void spawn(
            Location location,
            boolean force
    ) {

        if (location == null
                || location.getWorld() == null) {

            return;
        }

        // Never place wild pets in the Test Arena (equipped pets still allowed).
        if (!isEquipped()
                && !force
                && DungeonWorlds.isTestArena(location.getWorld())) {
            return;
        }

        if (isEquipped()) {

            location =
                    getEquippedSpawnLocation(
                            owner
                    );

            if (equippedBlockedAt(location)) {

                Location side =
                        followAnchor(
                                owner
                        );

                if (!equippedBlockedAt(side)) {

                    location =
                            side;

                } else {

                    location =
                            owner.getLocation()
                                    .clone()
                                    .add(
                                            0.55,
                                            HOVER_HEIGHT_TIGHT,
                                            0.0
                                    );

                    location.setY(
                            equippedHoverY(
                                    owner,
                                    location.getX(),
                                    location.getZ()
                            )
                    );
                }
            }

        } else if (force) {

            ignoreHabitatRules =
                    true;

            location =
                    openAirBeside(
                            location
                    );

            wildBaseY =
                    location.getY();

        } else {

            location =
                    getWildSpawnLocation(
                            location
                    );


            if (getSpawnType() != PetSpawnType.SURFACE) {

                wildBaseY =
                        location.getY();
            }


            /*
             * The spawn manager should already have validated
             * the location. We validate again here so that a pet
             * can never accidentally spawn in the wrong environment.
             */

            PetSpawnType spawnType =
                    getSpawnType();


            if (spawnType == PetSpawnType.SURFACE
                    && !isValidSurfacePosition(location)) {

                return;
            }


            if ((spawnType == PetSpawnType.CAVE
                    || spawnType == PetSpawnType.DUNGEON)
                    && !isValidCavePosition(location)) {

                return;
            }


            if (spawnType == PetSpawnType.NETHER) {

                boolean realNether =
                        location.getWorld().getEnvironment()
                                == World.Environment.NETHER;

                if (realNether
                        && !isValidCavePosition(location)) {

                    return;
                }

                if (!realNether
                        && !isValidSurfacePosition(location)) {

                    return;
                }
            }


            if (spawnType == PetSpawnType.AQUATIC
                    && !isValidAquaticPosition(location)) {

                return;
            }


            if (spawnType == PetSpawnType.SKY
                    && !isValidSkyPosition(location)) {

                return;
            }
        }


        entity =
                location.getWorld()
                        .spawn(
                                location,
                                ItemDisplay.class
                        );


        entity.setItemStack(
                PetHead.create(
                        petInstance
                )
        );


        entity.setBillboard(
                ItemDisplay.Billboard.FIXED
        );

        entity.setItemDisplayTransform(
                ItemDisplay.ItemDisplayTransform.FIXED
        );

        entity.setTransformation(
                new Transformation(
                        new Vector3f(
                                0f,
                                0.42f,
                                0f
                        ),
                        new Quaternionf(),
                        new Vector3f(
                                0.82f,
                                0.82f,
                                0.82f
                        ),
                        new Quaternionf()
                )
        );


        entity.setGravity(
                false
        );


        entity.setInterpolationDuration(
                2
        );


        entity.setTeleportDuration(
                3
        );


        entity.setPersistent(
                false
        );


        entity.getPersistentDataContainer().set(
                PET_ENTITY_KEY,
                PET_ENTITY_KEY_TYPE,
                (byte) 1
        );


        spawnNameplate();
        updateDisplayName();

        smoothedHoverY =
                location.getY();

        lookYaw =
                location.getYaw();


        if (isEquipped()) {

            startFollowMovement();

        } else if (companionHost != null) {

            startCompanionMovement();

        } else {

            startWildMovement();
        }
    }

    /**
     * Make this pet follow a location source like an equipped pet (bob, look, side hover).
     * Call before {@link #spawn(Location, boolean)} or after to rebind.
     */
    public void setCompanionHost(
            java.util.function.Supplier<Location> host
    ) {
        this.companionHost = host;
    }

    /** Switch an already-spawned wild pet onto companion follow. */
    public void bindCompanion(
            java.util.function.Supplier<Location> host
    ) {
        this.companionHost = host;
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (isSpawned() && host != null) {
            startCompanionMovement();
        }
    }


    /*
     * =========================================================
     * CATCH STATE
     * =========================================================
     */

    public void startCatching() {

        catching = true;
    }


    public void stopCatching() {

        catching = false;
    }


    public boolean isCatching() {

        return catching;
    }


    public void registerCatchAttempt() {

        catchAttempts++;
    }


    public int getCatchAttempts() {

        return catchAttempts;
    }

    public void reserveCatcher(UUID playerId) {
        this.reservedCatcher = playerId;
    }

    public void setGuaranteedCatch(boolean guaranteedCatch) {
        this.guaranteedCatch = guaranteedCatch;
    }

    public boolean isGuaranteedCatch() {
        return guaranteedCatch;
    }

    public boolean canBeCaughtBy(Player player) {
        return reservedCatcher == null
                || player != null && reservedCatcher.equals(player.getUniqueId());
    }


    /*
     * =========================================================
     * EQUIPPED PET
     * =========================================================
     */

    private void startFollowMovement() {

        movementTask =
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        if (!isSpawned()) {

                            remove();

                            return;
                        }


                        if (owner == null
                                || !owner.isOnline()) {

                            remove();

                            cancel();

                            return;
                        }


                        if (!catching) {

                            if (!entity.getWorld()
                                    .equals(
                                            owner.getWorld()
                                    )) {

                                movePet(owner.getLocation());
                                return;
                            }

                            followOwner(
                                    owner
                            );
                        }


                        updateEquippedHover();

                        updateShinyEffect();

                        updateAuraEffect();
                    }
                };


        AetherMobs plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        movementTask.runTaskTimer(
                plugin,
                0L,
                2L
        );
    }


    /*
     * =========================================================
     * COMPANION PET (NPC / location follow)
     * =========================================================
     */

    private void startCompanionMovement() {

        movementTask =
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        if (!isSpawned()) {

                            remove();

                            return;
                        }

                        Location host =
                                companionHost == null
                                        ? null
                                        : companionHost.get();

                        if (host == null
                                || host.getWorld() == null) {

                            return;
                        }

                        if (!entity.getWorld()
                                .equals(
                                        host.getWorld()
                                )) {

                            movePet(
                                    companionAnchor(
                                            host
                                    )
                            );

                            return;
                        }

                        followCompanion(
                                host
                        );

                        updateCompanionHover();

                        updateShinyEffect();

                        updateAuraEffect();
                    }
                };


        AetherMobs plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        movementTask.runTaskTimer(
                plugin,
                0L,
                2L
        );
    }


    private void followCompanion(
            Location host
    ) {

        Location current =
                entity.getLocation();

        Location target =
                companionAnchor(
                        host
                );

        double desiredY =
                host.getY()
                        + HOVER_HEIGHT;

        double currentY =
                current.getY()
                        - lastHoverBob;

        double nextY;

        if (Math.abs(desiredY - currentY) > 3.5) {

            nextY =
                    desiredY;

            lastHoverBob = 0;

        } else {

            nextY =
                    currentY
                            + (
                            desiredY
                                    - currentY
                    )
                            * 0.14;

            if (Math.abs(nextY - currentY) > FOLLOW_Y_STEP) {

                nextY =
                        currentY
                                + Math.copySign(
                                FOLLOW_Y_STEP,
                                nextY - currentY
                        );
            }
        }

        smoothedHoverY =
                nextY;

        target.setY(
                nextY
                        + lastHoverBob
        );

        double distanceSquared =
                current.distanceSquared(
                        target
                );

        if (distanceSquared
                > TELEPORT_DISTANCE
                * TELEPORT_DISTANCE) {

            lastHoverBob = 0;
            target.setY(
                    nextY
            );

            movePet(
                    target
            );

            return;
        }

        double dx =
                target.getX()
                        - current.getX();

        double dz =
                target.getZ()
                        - current.getZ();

        double horizontal =
                Math.sqrt(
                        dx * dx
                                + dz * dz
                );

        Location next =
                current.clone();

        next.setY(
                nextY
                        + lastHoverBob
        );

        if (horizontal > 0.35) {

            double step =
                    Math.min(
                            horizontal,
                            FOLLOW_SPEED
                                    + horizontal * 0.16
                    );

            next.setX(
                    current.getX()
                            + dx / horizontal * step
            );

            next.setZ(
                    current.getZ()
                            + dz / horizontal * step
            );
        }

        applyCompanionLook(
                next,
                host
        );

        movePet(
                next
        );
    }


    private Location companionAnchor(
            Location host
    ) {

        Location base =
                host.clone();

        Vector along =
                companionTravel(
                        host
                );

        Vector side =
                new Vector(
                        -along.getZ(),
                        0.0,
                        along.getX()
                );

        if (side.lengthSquared() < 0.0001) {

            side =
                    new Vector(
                            1.0,
                            0.0,
                            0.0
                    );

        } else {

            side.normalize();
        }

        base.add(
                along.multiply(
                        -FOLLOW_DISTANCE * 0.55
                )
        );

        base.add(
                side.multiply(
                        FOLLOW_SIDE * 0.9
                )
        );

        // Smooth vertical bob only — no side-to-side orbit while following.
        orbitAngle += 0.08;
        double bob = Math.sin(orbitAngle) * 0.22;

        base.setY(
                host.getY()
                        + HOVER_HEIGHT
                        + bob
        );

        return base;
    }


    private Vector companionTravel(
            Location host
    ) {

        if (lastFollowFrom != null
                && lastFollowFrom.getWorld() != null
                && lastFollowFrom.getWorld()
                .equals(
                        host.getWorld()
                )) {

            double dx =
                    host.getX()
                            - lastFollowFrom.getX();

            double dz =
                    host.getZ()
                            - lastFollowFrom.getZ();

            if (dx * dx + dz * dz > 0.04) {

                followAlong =
                        new Vector(
                                dx,
                                0.0,
                                dz
                        )
                                .normalize();
            }
        }

        lastFollowFrom =
                host.clone();

        if (followAlong == null
                || followAlong.lengthSquared() < 0.0001) {

            Vector facing =
                    host.getDirection()
                            .clone()
                            .setY(
                                    0.0
                            );

            if (facing.lengthSquared() < 0.0001) {

                facing =
                        new Vector(
                                1.0,
                                0.0,
                                0.0
                        );

            } else {

                facing.normalize();
            }

            followAlong =
                    facing;
        }

        return followAlong.clone();
    }


    private void updateCompanionHover() {

        if (!isSpawned()) {

            return;
        }

        hoverTime += 0.18;

        Location origin =
                entity.getLocation();

        double bob =
                Math.sin(
                        hoverTime
                )
                        * HOVER_RANGE;

        double baseY =
                Double.isNaN(smoothedHoverY)
                        ? origin.getY() - lastHoverBob
                        : smoothedHoverY;

        Location bobbed =
                origin.clone();

        bobbed.setY(
                baseY
                        + bob
        );

        lastHoverBob =
                bob;

        movePet(
                bobbed
        );
    }


    private void applyCompanionLook(
            Location location,
            Location host
    ) {

        if (location == null) {
            return;
        }

        lookTicks -=
                2;

        if (lookTicks <= 0) {

            lookTicks =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    80,
                                    180
                            );

            if (host != null
                    && ThreadLocalRandom.current()
                    .nextDouble()
                    < 0.35) {

                Location glanceAt =
                        host.clone()
                                .add(
                                        0.0,
                                        1.62,
                                        0.0
                                );

                lookYaw =
                        yawToward(
                                location,
                                glanceAt
                        );

            } else {

                lookYaw =
                        ThreadLocalRandom.current()
                                .nextFloat()
                                * 360.0F;
            }
        }

        location.setYaw(
                lerpYaw(
                        location.getYaw(),
                        lookYaw,
                        LOOK_TURN_STEP
                )
        );

        location.setPitch(
                0.0F
        );
    }


    private void followOwner(
            Player player
    ) {

        Location current =
                entity.getLocation();

        Location target =
                followAnchor(
                        player
                );

        double desiredY =
                equippedHoverY(
                        player,
                        target.getX(),
                        target.getZ()
                );

        double currentY =
                current.getY()
                        - lastHoverBob;

        double nextY;

        if (Math.abs(desiredY - currentY) > 3.5) {

            nextY =
                    desiredY;

            lastHoverBob = 0;

        } else {

            nextY =
                    currentY
                            + (
                            desiredY
                                    - currentY
                    )
                            * 0.14;

            if (Math.abs(nextY - currentY) > FOLLOW_Y_STEP) {

                nextY =
                        currentY
                                + Math.copySign(
                                FOLLOW_Y_STEP,
                                nextY - currentY
                        );
            }
        }

        smoothedHoverY =
                nextY;

        target.setY(
                nextY
                        + lastHoverBob
        );

        double distanceSquared =
                current.distanceSquared(
                        target
                );

        if (distanceSquared
                > TELEPORT_DISTANCE
                * TELEPORT_DISTANCE) {

            lastHoverBob = 0;
            target.setY(
                    nextY
            );

            movePet(
                    keepBesideOwner(
                            current,
                            target
                    )
            );

            return;
        }

        double dx =
                target.getX()
                        - current.getX();

        double dz =
                target.getZ()
                        - current.getZ();

        double horizontal =
                Math.sqrt(
                        dx * dx
                                + dz * dz
                );

        Location next =
                current.clone();

        /*
         * Keep the current bob on the entity while following.
         * Stripping it here and re-adding in updateEquippedHover
         * made lastHoverBob desync and caused idle hitching.
         */
        next.setY(
                nextY
                        + lastHoverBob
        );

        if (horizontal > 0.35) {

            double step =
                    Math.min(
                            horizontal,
                            FOLLOW_SPEED
                                    + horizontal * 0.16
                    );

            next.setX(
                    current.getX()
                            + dx / horizontal * step
            );

            next.setZ(
                    current.getZ()
                            + dz / horizontal * step
            );
        }

        applyLook(
                next,
                player
        );

        movePet(
                keepBesideOwner(
                        current,
                        next
                )
        );
    }


    /*
     * Stay beside the owner's travel direction, not their camera.
     * Looking around does not orbit the pet behind the new facing.
     */
    private Location followAnchor(
            Player player
    ) {

        Location playerLoc =
                player.getLocation()
                        .clone();

        Vector along =
                followTravel(
                        player
                );

        Vector side =
                new Vector(
                        -along.getZ(),
                        0.0,
                        along.getX()
                );

        if (side.lengthSquared() < 0.0001) {

            side =
                    new Vector(
                            1.0,
                            0.0,
                            0.0
                    );

        } else {

            side.normalize();
        }

        playerLoc.add(
                along.multiply(
                        -FOLLOW_DISTANCE
                )
        );

        playerLoc.add(
                side.multiply(
                        FOLLOW_SIDE
                )
        );

        playerLoc.setY(
                equippedHoverY(
                        player,
                        playerLoc.getX(),
                        playerLoc.getZ()
                )
        );

        return playerLoc;
    }


    private Vector followTravel(
            Player player
    ) {

        Location now =
                player.getLocation();

        if (lastFollowFrom != null
                && lastFollowFrom.getWorld() != null
                && lastFollowFrom.getWorld()
                .equals(
                        now.getWorld()
                )) {

            double dx =
                    now.getX()
                            - lastFollowFrom.getX();

            double dz =
                    now.getZ()
                            - lastFollowFrom.getZ();

            if (dx * dx + dz * dz > 0.04) {

                followAlong =
                        new Vector(
                                dx,
                                0.0,
                                dz
                        )
                                .normalize();
            }
        }

        lastFollowFrom =
                now.clone();

        if (followAlong == null
                || followAlong.lengthSquared() < 0.0001) {

            followAlong =
                    yawVector(
                            bodyYaw(
                                    player
                            )
                    );
        }

        return followAlong.clone();
    }


    private static float bodyYaw(
            Player player
    ) {

        try {

            return player.getBodyYaw();

        } catch (Throwable ignored) {

            return player.getLocation()
                    .getYaw();
        }
    }


    private static Vector yawVector(
            float yaw
    ) {

        double rad =
                Math.toRadians(
                        yaw
                );

        return new Vector(
                -Math.sin(rad),
                0.0,
                Math.cos(rad)
        );
    }


    private Location getEquippedSpawnLocation(
            Player player
    ) {

        Location location =
                followAnchor(
                        player
                );

        location.setYaw(
                yawToward(
                        location,
                        player.getEyeLocation()
                )
        );

        location.setPitch(
                0.0F
        );

        return location;
    }


    private void updateEquippedHover() {

        if (!isSpawned()
                || owner == null) {

            return;
        }

        hoverTime += 0.18;

        Location origin =
                entity.getLocation();

        double bob =
                Math.sin(
                        hoverTime
                )
                        * HOVER_RANGE;

        /*
         * Same model as wild surface hover: one smoothed base Y
         * plus a pure sine bob. Never zero lastHoverBob on clamp —
         * that snapped the pet to full amplitude and looked like hitching.
         */
        double baseY =
                Double.isNaN(smoothedHoverY)
                        ? origin.getY() - lastHoverBob
                        : smoothedHoverY;

        Location bobbed =
                origin.clone();

        bobbed.setY(
                baseY
                        + bob
        );

        Location dest =
                keepBesideOwner(
                        origin,
                        bobbed
                );

        if (Math.abs(dest.getY() - (baseY + bob)) > 0.001) {

            smoothedHoverY =
                    dest.getY()
                            - bob;
        }

        lastHoverBob =
                bob;

        movePet(
                dest
        );
    }


    /*
     * =========================================================
     * WILD PET
     * =========================================================
     */

    private void startWildMovement() {

        movementTask =
                new BukkitRunnable() {

                    @Override
                    public void run() {

                        if (!isSpawned()) {

                            remove();

                            return;
                        }


                        if (!catching) {

                            updateWildMovement();
                        }


                        updateWildHover();

                        updateShinyEffect();

                        updateAuraEffect();
                    }
                };


        AetherMobs plugin = getPlugin();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        movementTask.runTaskTimer(
                plugin,
                0L,
                2L
        );
    }


    private void updateWildMovement() {

        if (!isSpawned()) {
            return;
        }

        if (orbitReservedCatcher()) {
            return;
        }

        /*
         * =====================================================
         * FLEE FROM NEARBY PLAYERS
         * =====================================================
         */

        Player nearestPlayer =
                getNearestPlayer(
                        WILD_FLEE_DISTANCE
                );


        double speed =
                WILD_SPEED;


        if (nearestPlayer != null) {

            Vector away =
                    entity.getLocation()
                            .toVector()
                            .subtract(
                                    nearestPlayer.getLocation()
                                            .toVector()
                            );


            away.setY(0);


            if (away.lengthSquared() > 0.01) {

                wildDirection =
                        away.normalize();


                speed =
                        WILD_FLEE_SPEED;
            }

        } else {

            /*
             * =================================================
             * SOFT AVOID QUEST NPCs
             * =================================================
             *
             * Prefer not to hang around NPCs (hub / spawn stays quieter).
             * Does not change habitat rules — only steering.
             */

            Location nearestNpc =
                    PetSpawnZones.nearestWithin(
                            entity.getLocation(),
                            PetSpawnZones.npcAvoidRadius()
                    );

            if (nearestNpc != null) {

                Vector away =
                        entity.getLocation()
                                .toVector()
                                .subtract(
                                        nearestNpc.toVector()
                                );

                away.setY(0);

                if (away.lengthSquared() > 0.01) {
                    wildDirection = away.normalize();
                }

            } else {

                /*
                 * =================================================
                 * RANDOM WANDERING
                 * =================================================
                 */

                wildDirectionStep--;


                if (wildDirectionStep <= 0) {

                    wildDirection =
                            createRandomWildDirection(
                                    movementSpawnType()
                            );


                    wildDirectionStep =
                            ThreadLocalRandom.current()
                                    .nextInt(
                                            WILD_DIRECTION_MIN_TICKS,
                                            WILD_DIRECTION_MAX_TICKS
                                    );
                }
            }
        }


        if (wildDirection == null
                || wildDirection.lengthSquared() < 0.01) {

            wildDirection =
                    createRandomWildDirection(
                            movementSpawnType()
                    );
        }


        Vector movement =
                wildDirection.clone()
                        .normalize()
                        .multiply(
                                speed
                        );


        Location current =
                entity.getLocation();


        Location next =
                current.clone()
                        .add(
                                movement
                        );


        PetSpawnType spawnType =
                movementSpawnType();


        /*
         * =====================================================
         * SURFACE
         * =====================================================
         *
         * Surface pets:
         * - stay above the terrain
         * - cannot enter water
         * - cannot enter lava
         * - cannot enter caves
         */

        if (spawnType == PetSpawnType.SURFACE) {

            // Already submerged? Get out before wandering further.
            if (!isValidSurfacePosition(current)) {
                Location escape = rescueDrySurface(current);
                if (escape != null) {
                    smoothedHoverY = escape.getY();
                    applyLook(escape, null);
                    movePet(escape);
                }
                wildDirection = createRandomWildDirection(spawnType);
                return;
            }

            if (!isValidSurfacePosition(next)) {

                Location recovered =
                        recoverSurfaceStep(
                                current,
                                speed
                        );

                if (recovered == null) {

                    wildDirection =
                            createRandomWildDirection(
                                    spawnType
                            );

                    Location escape = rescueDrySurface(current);
                    if (escape != null) {
                        smoothedHoverY = escape.getY();
                        applyLook(escape, null);
                        movePet(escape);
                    }
                    return;
                }

                next =
                        recovered;
            }


            int nearY = current.getBlockY();

            int fromGround =
                    PetWalkSurface.walkGroundY(
                            current.getWorld(),
                            current.getBlockX(),
                            current.getBlockZ(),
                            nearY
                    );

            int groundY =
                    PetWalkSurface.walkGroundY(
                            next.getWorld(),
                            next.getBlockX(),
                            next.getBlockZ(),
                            nearY
                    );

            if (!isGentleSurfaceStep(fromGround, groundY)) {

                Location recovered =
                        recoverSurfaceStep(
                                current,
                                speed
                        );

                if (recovered == null) {
                    wildDirection =
                            createRandomWildDirection(
                                    spawnType
                            );
                    return;
                }

                next = recovered;
                groundY =
                        PetWalkSurface.walkGroundY(
                                next.getWorld(),
                                next.getBlockX(),
                                next.getBlockZ(),
                                nearY
                        );

                if (!isGentleSurfaceStep(fromGround, groundY)) {
                    wildDirection =
                            createRandomWildDirection(
                                    spawnType
                            );
                    return;
                }
            }

            // Never follow seafloor under a water column.
            if (!PetWalkSurface.isDryWalkColumn(
                    next.getWorld(),
                    next.getBlockX(),
                    next.getBlockZ(),
                    nearY
            )) {
                Location escape = rescueDrySurface(current);
                if (escape != null) {
                    smoothedHoverY = escape.getY();
                    applyLook(escape, null);
                    movePet(escape);
                }
                wildDirection = createRandomWildDirection(spawnType);
                return;
            }

            double desiredY =
                    groundY
                            + HOVER_HEIGHT;

            if (Double.isNaN(smoothedHoverY)) {

                smoothedHoverY =
                        next.getY();
            }

            smoothedHoverY =
                    approachY(
                            smoothedHoverY,
                            desiredY,
                            SURFACE_Y_STEP
                    );

            next.setY(
                    smoothedHoverY
            );
        }


        /*
         * =====================================================
         * CAVE
         * =====================================================
         *
         * Cave pets stay underground.
         *
         * We deliberately DO NOT use
         * getHighestBlockYAt() here.
         */

        else if (spawnType == PetSpawnType.CAVE
                || spawnType == PetSpawnType.DUNGEON
                || spawnType == PetSpawnType.NETHER) {

            if (!isValidCavePosition(next)) {

                wildDirection =
                        createRandomWildDirection(
                                spawnType
                        );

                return;
            }
        }


        /*
         * =====================================================
         * AQUATIC
         * =====================================================
         *
         * Surface aquatic pets stay near the water line.
         * Deep-sea pets swim inside the water column.
         */

        else if (spawnType == PetSpawnType.AQUATIC) {

            boolean surfaceSwim = prefersSurfaceSwim(current);

            // Already out of water? Pull back before wandering further.
            if (!isValidAquaticPosition(current)) {
                Location escape = rescueAquatic(current);
                if (escape != null) {
                    smoothedHoverY = escape.getY();
                    applyLook(escape, null);
                    movePet(escape);
                }
                wildDirection = createRandomWildDirection(spawnType, surfaceSwim);
                return;
            }

            // Shallow / surface swimmers stay horizontal — Y jitter pops them
            // out of 1-deep water and makes movement stutter.
            if (surfaceSwim) {
                next.setY(waterSurfaceY(next) + 0.35);
            }

            if (!isValidAquaticPosition(next)) {

                Location recovered = recoverAquaticStep(current, speed);
                if (recovered == null) {
                    wildDirection =
                            createRandomWildDirection(
                                    spawnType,
                                    surfaceSwim
                            );
                    Location escape = rescueAquatic(current);
                    if (escape != null) {
                        smoothedHoverY = escape.getY();
                        applyLook(escape, null);
                        movePet(escape);
                    }
                    return;
                }
                next = recovered;
            }

            if (surfaceSwim) {

                next.setY(
                        waterSurfaceY(
                                next
                        )
                                + 0.35
                );
                // Never finish on dry land after the surface snap.
                if (!isValidAquaticPosition(next)) {
                    wildDirection = createRandomWildDirection(spawnType, true);
                    return;
                }
            }
        }


        /*
         * =====================================================
         * SKY
         * =====================================================
         *
         * Sky pets stay in open air above the terrain.
         */

        else if (spawnType == PetSpawnType.SKY) {

            if (!isValidSkyPosition(next)) {

                wildDirection =
                        createRandomWildDirection(
                                spawnType
                        );

                return;
            }

            int groundY =
                    next.getWorld()
                            .getHighestBlockYAt(
                                    next.getBlockX(),
                                    next.getBlockZ(),
                                    HeightMap.MOTION_BLOCKING_NO_LEAVES
                            );

            double minY =
                    groundY + 8.0;

            double maxY =
                    groundY + 18.0;

            if (next.getY() < minY) {

                next.setY(
                        minY
                );
            }

            if (next.getY() > maxY) {

                next.setY(
                        maxY
                );
            }
        }


        applyLook(
                next,
                null
        );

        if (spawnType == PetSpawnType.SURFACE) {

            movePet(
                    avoidTerrainClip(
                            current,
                            next,
                            SURFACE_Y_STEP
                    )
            );

            return;
        }

        movePet(
                next
        );
    }

    private boolean orbitReservedCatcher() {
        if (reservedCatcher == null || !isSpawned()) {
            return false;
        }
        Player fisher = Bukkit.getPlayer(reservedCatcher);
        if (fisher == null || !fisher.isOnline() || !fisher.getWorld().equals(entity.getWorld())) {
            return false;
        }
        orbitAngle += 0.12d;
        Location dest = fisher.getLocation().clone().add(
                Math.cos(orbitAngle) * 2.85d,
                2.15d + Math.sin(orbitAngle * 0.85d) * 0.45d,
                Math.sin(orbitAngle) * 2.85d
        );
        Location current = entity.getLocation();
        if (current.getWorld() == null || dest.getWorld() == null) {
            return false;
        }
        if (current.distanceSquared(dest) > 18.0d * 18.0d) {
            movePet(dest);
            return true;
        }
        Vector step = dest.toVector().subtract(current.toVector());
        double dist = step.length();
        if (dist > 0.04d) {
            movePet(current.add(step.normalize().multiply(Math.min(dist, 0.48d))));
        }
        return true;
    }

    private PetSpawnType movementSpawnType() {

        if (ignoreHabitatRules) {
            return PetSpawnType.SURFACE;
        }

        PetSpawnType type = getSpawnType();

        // Borderlands Nether pets live on the overworld surface —
        // cave pathfinding rejects every step and they freeze.
        if (type == PetSpawnType.NETHER && !isInRealNether()) {
            return PetSpawnType.SURFACE;
        }

        return type;
    }

    private boolean isInRealNether() {
        return entity != null
                && entity.getWorld() != null
                && entity.getWorld().getEnvironment()
                == World.Environment.NETHER;
    }

    private static Location openAirBeside(
            Location origin
    ) {

        Location loc =
                origin.clone();

        if (loc.getWorld() == null) {
            return loc;
        }

        for (
                int i = 0;
                i < 8;
                i++
        ) {

            if (loc.getBlock().isPassable()
                    && loc.clone()
                    .add(0, 1, 0)
                    .getBlock()
                    .isPassable()) {

                return loc;
            }

            loc.add(0, 1, 0);
        }

        return origin.clone();
    }


    private Vector createRandomWildDirection(
            PetSpawnType spawnType
    ) {
        return createRandomWildDirection(spawnType, false);
    }

    private Vector createRandomWildDirection(
            PetSpawnType spawnType,
            boolean surfaceSwim
    ) {

        double angle =
                ThreadLocalRandom.current()
                        .nextDouble(
                                0.0,
                                Math.PI * 2
                        );

        double y =
                0.0;

        if (!surfaceSwim
                && (spawnType == PetSpawnType.SKY
                || spawnType == PetSpawnType.AQUATIC
                || spawnType == PetSpawnType.CAVE
                || spawnType == PetSpawnType.NETHER)) {

            y =
                    ThreadLocalRandom.current()
                            .nextDouble(
                                    -0.4,
                                    0.4
                            );
        }

        return new Vector(
                Math.cos(angle),
                y,
                Math.sin(angle)
        ).normalize();
    }


    private boolean isDeepAquatic() {

        return petInstance != null
                && petInstance.getDefinition() != null
                && petInstance.getDefinition().isDeepAquatic();
    }

    /**
     * Surface aquatics always; deep aquatics only while the local column
     * is too shallow for vertical swimming (1–2 blocks).
     */
    private boolean prefersSurfaceSwim(Location location) {
        if (!isDeepAquatic()) {
            return true;
        }
        return waterColumnDepth(location) <= 2;
    }

    private int waterColumnDepth(Location location) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int surface = (int) Math.floor(waterSurfaceY(location));
        int depth = 0;
        for (int y = surface; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!PetWalkSurface.isFullWaterBlock(block)
                    || PetWalkSurface.isPartialWaterObstacle(
                    world.getBlockAt(x, y + 1, z)
            )) {
                break;
            }
            depth++;
        }
        return depth;
    }


    private double waterSurfaceY(
            Location location
    ) {

        World world =
                location.getWorld();

        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();

        int y =
                location.getBlockY();

        // If we are above / beside the column, drop into water first.
        if (!PetWalkSurface.isFullWaterBlock(world.getBlockAt(x, y, z))) {
            for (int dy = 0; dy <= 3; dy++) {
                if (PetWalkSurface.isFullWaterBlock(world.getBlockAt(x, y - dy, z))) {
                    y = y - dy;
                    break;
                }
            }
        }

        while (y < world.getMaxHeight() - 1
                && PetWalkSurface.isFullWaterBlock(
                world.getBlockAt(
                        x,
                        y + 1,
                        z
                )
        )) {

            y++;
        }

        // Still not in water — climb from below toward a surface cell.
        if (!PetWalkSurface.isFullWaterBlock(world.getBlockAt(x, y, z))) {
            int minY = world.getMinHeight() + 1;
            for (int probe = Math.min(world.getMaxHeight() - 2, y + 8); probe >= minY; probe--) {
                Block at = world.getBlockAt(x, probe, z);
                Block above = world.getBlockAt(x, probe + 1, z);
                if (PetWalkSurface.isFullWaterBlock(at)
                        && !PetWalkSurface.isFullWaterBlock(above)
                        && !PetWalkSurface.isPartialWaterObstacle(above)) {
                    return probe;
                }
            }
        }

        return y;
    }


    private Player nearestPlayerCache;
    private long nearestPlayerCacheTick = -1L;

    private Player getNearestPlayer(
            double maxDistance
    ) {

        if (!isSpawned()) {
            return null;
        }

        World world = entity.getWorld();
        if (world == null) {
            return null;
        }

        long now = Bukkit.getCurrentTick();
        double maxSq = maxDistance * maxDistance;
        if (nearestPlayerCacheTick >= 0
                && now - nearestPlayerCacheTick <= 4
                && nearestPlayerCache != null
                && nearestPlayerCache.isOnline()
                && nearestPlayerCache.getWorld() == world) {
            double cachedDist = nearestPlayerCache.getLocation()
                    .distanceSquared(entity.getLocation());
            if (cachedDist <= maxSq) {
                return nearestPlayerCache;
            }
        }

        Player nearest = null;
        double nearestDistanceSquared = maxSq;

        for (Player player : world.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            double distanceSquared = player.getLocation()
                    .distanceSquared(entity.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }

        nearestPlayerCache = nearest;
        nearestPlayerCacheTick = now;
        return nearest;
    }


    /*
     * =========================================================
     * WILD SPAWN LOCATION
     * =========================================================
     */

    private Location getWildSpawnLocation(
            Location location
    ) {

        Location result =
                location.clone();


        PetSpawnType spawnType =
                getSpawnType();


        /*
         * SURFACE:
         *
         * The spawn manager already selected and validated the
         * exact surface position. Keep that position.
         *
         * Do not recalculate Y here with another heightmap.
         */

        if (spawnType == PetSpawnType.SURFACE) {

            result.setYaw(
                    ThreadLocalRandom.current()
                            .nextFloat()
                            * 360.0F
            );


            result.setPitch(
                    0.0F
            );


            return result;
        }


        /*
         * CAVE:
         *
         * PetSpawnManager already selected an underground
         * location. Keep that Y coordinate.
         */

        if (spawnType == PetSpawnType.CAVE
                || spawnType == PetSpawnType.DUNGEON
                || spawnType == PetSpawnType.NETHER) {

            result.setYaw(
                    ThreadLocalRandom.current()
                            .nextFloat()
                            * 360.0F
            );


            result.setPitch(
                    0.0F
            );


            return result;
        }


        /*
         * AQUATIC:
         *
         * PetSpawnManager already selected a water-surface
         * location. Keep that Y coordinate.
         */

        if (spawnType == PetSpawnType.AQUATIC) {

            result.setYaw(
                    ThreadLocalRandom.current()
                            .nextFloat()
                            * 360.0F
            );


            result.setPitch(
                    0.0F
            );


            return result;
        }


        /*
         * SKY:
         *
         * PetSpawnManager already selected an open-air
         * location. Keep that Y coordinate.
         */

        if (spawnType == PetSpawnType.SKY) {

            result.setYaw(
                    ThreadLocalRandom.current()
                            .nextFloat()
                            * 360.0F
            );


            result.setPitch(
                    0.0F
            );


            return result;
        }


        return result;
    }


    /*
     * =========================================================
     * SURFACE VALIDATION
     * =========================================================
     */

    private boolean isValidSurfacePosition(
            Location location
    ) {
        if (
                location == null
                        || location.getWorld() == null
        ) {
            return false;
        }

        World world =
                location.getWorld();

        int x =
                location.getBlockX();

        int z =
                location.getBlockZ();

        int nearY = location.getBlockY();
        int groundY =
                PetWalkSurface.walkGroundY(
                        world,
                        x,
                        z,
                        nearY
                );

        Block ground =
                world.getBlockAt(
                        x,
                        groundY,
                        z
                );

        if (!ground.getType().isSolid()
                || PetWalkSurface.isWatery(ground)
                || PetWalkSurface.isCanopy(ground.getType())) {
            return false;
        }

        Block feet =
                world.getBlockAt(
                        x,
                        groundY + 1,
                        z
                );

        Block head =
                world.getBlockAt(
                        x,
                        groundY + 2,
                        z
                );

        return ground.getType().isSolid()
                && !PetWalkSurface.isWatery(ground)
                && PetWalkSurface.isDryWalkColumn(world, x, z, nearY)
                && isOpenForPet(
                feet
        )
                && isOpenForPet(
                head
        );
    }

    private boolean isOpenForPet(
            Block block
    ) {
        if (block == null) {
            return false;
        }
        Material type =
                block.getType();
        String name =
                type.name();
        if (block.isLiquid()) {
            return false;
        }
        if (name.contains("LEAVES")
                || name.contains("CARPET")
                || name.contains("VINE")
                || type == Material.SNOW
                || type == Material.POWDER_SNOW
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.COBWEB
                || type == Material.BAMBOO
                || type == Material.MOSS_CARPET) {
            return true;
        }
        return block.isPassable();
    }

    /**
     * Equipped pets stay in the owner's air pocket.
     * Never collapse onto the owner's head — prefer a side offset,
     * lower hover in tight ceilings, and treat water as open space.
     */
    private Location keepBesideOwner(
            Location from,
            Location to
    ) {

        if (owner == null
                || to == null
                || to.getWorld() == null) {

            return from;
        }

        Location ownerLoc =
                owner.getLocation();

        if (ownerLoc.getWorld() == null
                || !ownerLoc.getWorld()
                .equals(
                        to.getWorld()
                )) {

            return from;
        }

        World world =
                to.getWorld();

        Location dest =
                to.clone();

        double hoverY =
                equippedHoverY(
                        owner,
                        dest.getX(),
                        dest.getZ()
                );

        double floorTop =
                equippedFloorTop(
                        world,
                        dest.getX(),
                        ownerLoc.getY(),
                        dest.getZ()
                );

        double ceilingBottom =
                equippedCeilingBottom(
                        world,
                        dest.getX(),
                        ownerLoc.getY(),
                        dest.getZ()
                );

        double minY =
                Math.max(
                        floorTop + 0.35,
                        ownerLoc.getY() + 0.12
                );

        double maxY =
                Math.min(
                        ceilingBottom - 0.35,
                        ownerLoc.getY() + 1.55
                );

        double bobBase =
                Double.isNaN(smoothedHoverY)
                        ? hoverY
                        : smoothedHoverY;

        if (maxY < minY) {

            dest.setY(
                    (floorTop + ceilingBottom) * 0.5
            );

        } else if (Math.abs(dest.getY() - bobBase)
                <= HOVER_RANGE + 0.08
                || Math.abs(dest.getY() - hoverY)
                <= HOVER_RANGE + 0.35) {

            /*
             * Preserve soft bob / follow lerp. Snapping to hoverY
             * while the sine wave peaks caused idle stutter.
             */
            dest.setY(
                    Math.max(
                            minY,
                            Math.min(
                                    maxY,
                                    dest.getY()
                            )
                    )
            );

        } else if (Math.abs(dest.getY() - hoverY) < 1.25) {

            /* Still approaching — keep current Y, only clamp. */
            dest.setY(
                    Math.max(
                            minY,
                            Math.min(
                                    maxY,
                                    dest.getY()
                            )
                    )
            );

        } else {

            dest.setY(
                    Math.max(
                            minY,
                            Math.min(
                                    maxY,
                                    hoverY
                            )
                    )
            );
        }

        if (!equippedBlockedAt(dest)) {

            return dest;
        }

        /*
         * Preferred follow spot is blocked (wall / ceiling).
         * Try other side offsets around the owner — never snap
         * into the player's head.
         */
        Vector along =
                followAlong != null
                        && followAlong.lengthSquared() > 0.0001
                        ? followAlong.clone().normalize()
                        : yawVector(bodyYaw(owner));

        Vector side =
                new Vector(
                        -along.getZ(),
                        0.0,
                        along.getX()
                );

        if (side.lengthSquared() < 0.0001) {

            side =
                    new Vector(
                            1.0,
                            0.0,
                            0.0
                    );

        } else {

            side.normalize();
        }

        double[][] offsets = {
                { -FOLLOW_DISTANCE, FOLLOW_SIDE },
                { -FOLLOW_DISTANCE, -FOLLOW_SIDE },
                { -FOLLOW_DISTANCE * 0.55, FOLLOW_SIDE * 0.7 },
                { -FOLLOW_DISTANCE * 0.55, -FOLLOW_SIDE * 0.7 },
                { -0.35, FOLLOW_SIDE * 0.85 },
                { -0.35, -FOLLOW_SIDE * 0.85 },
                { 0.0, FOLLOW_SIDE * 0.55 },
                { 0.0, -FOLLOW_SIDE * 0.55 },
                { -FOLLOW_DISTANCE * 0.35, 0.35 },
                { -0.55, 0.0 }
        };

        Location best =
                null;

        double bestDist =
                Double.MAX_VALUE;

        for (double[] offset : offsets) {

            Location candidate =
                    ownerLoc.clone()
                            .add(
                                    along.clone()
                                            .multiply(
                                                    offset[0]
                                            )
                            )
                            .add(
                                    side.clone()
                                            .multiply(
                                                    offset[1]
                                            )
                            );

            candidate.setY(
                    equippedHoverY(
                            owner,
                            candidate.getX(),
                            candidate.getZ()
                    )
            );

            candidate.setYaw(
                    dest.getYaw()
            );

            candidate.setPitch(
                    0.0F
            );

            if (equippedBlockedAt(candidate)) {
                continue;
            }

            double dist =
                    from == null
                            ? 0.0
                            : candidate.distanceSquared(
                                    from
                            );

            if (dist < bestDist) {

                bestDist =
                        dist;

                best =
                        candidate;
            }
        }

        if (best != null) {
            return best;
        }

        /*
         * Absolute last resort: stand slightly beside the owner
         * at a Y that fits the owner's own column — still not
         * inside the skull if we can help it.
         */
        Location fallback =
                ownerLoc.clone()
                        .add(
                                side.clone()
                                        .multiply(
                                                0.55
                                        )
                        );

        fallback.setY(
                equippedHoverY(
                        owner,
                        fallback.getX(),
                        fallback.getZ()
                )
        );

        fallback.setYaw(
                dest.getYaw()
        );

        fallback.setPitch(
                0.0F
        );

        if (!equippedBlockedAt(fallback)) {
            return fallback;
        }

        fallback.setX(
                ownerLoc.getX()
                        + side.getX() * 0.35
        );

        fallback.setZ(
                ownerLoc.getZ()
                        + side.getZ() * 0.35
        );

        fallback.setY(
                equippedHoverY(
                        owner,
                        fallback.getX(),
                        fallback.getZ()
                )
        );

        return fallback;
    }


    /**
     * Hover height that fits the local ceiling — low corridors and
     * swimming keep the pet mid-body beside the player, not in the head.
     */
    private double equippedHoverY(
            Player player,
            double x,
            double z
    ) {

        Location ownerLoc =
                player.getLocation();

        World world =
                ownerLoc.getWorld();

        if (world == null) {
            return ownerLoc.getY() + HOVER_HEIGHT_TIGHT;
        }

        double floorTop =
                equippedFloorTop(
                        world,
                        x,
                        ownerLoc.getY(),
                        z
                );

        double ceilingBottom =
                equippedCeilingBottom(
                        world,
                        x,
                        ownerLoc.getY(),
                        z
                );

        double clearance =
                ceilingBottom - floorTop;

        double prefer =
                clearance < 2.35
                        || player.isInWater()
                        || player.isSwimming()
                        ? HOVER_HEIGHT_TIGHT
                        : HOVER_HEIGHT;

        double desired =
                ownerLoc.getY()
                        + prefer;

        /*
         * On stairs / slabs the owner's Y is already the stand height.
         * Prefer that over a block-scan that can jump under the stair.
         */
        double ownerFloor =
                equippedFloorTop(
                        world,
                        ownerLoc.getX(),
                        ownerLoc.getY(),
                        ownerLoc.getZ()
                );

        if (Math.abs(ownerLoc.getY() - ownerFloor) < 1.15) {

            desired =
                    ownerLoc.getY()
                            + prefer;
        }

        double minY =
                Math.max(
                        floorTop + 0.35,
                        ownerLoc.getY() + 0.15
                );

        double maxY =
                Math.min(
                        ceilingBottom - 0.35,
                        ownerLoc.getY() + 1.55
                );

        if (maxY < minY) {

            return (floorTop + ceilingBottom) * 0.5;
        }

        return Math.max(
                minY,
                Math.min(
                        maxY,
                        desired
                )
        );
    }


    /**
     * Top face of the solid under a column. Stairs / slabs count as
     * solid so pets do not drop through and flip on half-blocks.
     */
    private static double equippedFloorTop(
            World world,
            double x,
            double fromY,
            double z
    ) {

        int bx =
                Location.locToBlock(
                        x
                );

        int bz =
                Location.locToBlock(
                        z
                );

        int min =
                Math.max(
                        world.getMinHeight() + 1,
                        (int) Math.floor(fromY) - 24
                );

        int start =
                Math.min(
                        Math.max(
                                min,
                                (int) Math.floor(fromY)
                        ),
                        world.getMaxHeight() - 2
                );

        for (
                int y = start;
                y >= min;
                y--
        ) {

            Block block =
                    world.getBlockAt(
                            bx,
                            y,
                            bz
                    );

            if (isEquippedFloorBlock(block)) {

                return y + equippedBlockTop(block);
            }
        }

        return fromY - 1.0;
    }


    private static double equippedCeilingBottom(
            World world,
            double x,
            double fromY,
            double z
    ) {

        int bx =
                Location.locToBlock(
                        x
                );

        int bz =
                Location.locToBlock(
                        z
                );

        int max =
                Math.min(
                        world.getMaxHeight() - 2,
                        (int) Math.floor(fromY) + 12
                );

        int start =
                Math.max(
                        world.getMinHeight() + 1,
                        Math.min(
                                max,
                                (int) Math.floor(fromY) + 1
                        )
                );

        for (
                int y = start;
                y <= max;
                y++
        ) {

            Block block =
                    world.getBlockAt(
                            bx,
                            y,
                            bz
                    );

            if (isEquippedCeilingBlock(block)) {

                return y;
            }
        }

        return fromY + 8.0;
    }


    private static boolean isEquippedFloorBlock(
            Block block
    ) {

        if (block == null) {
            return false;
        }

        Material type =
                block.getType();

        if (type.isAir()
                || type == Material.LIGHT
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR) {

            return false;
        }

        if (block.isLiquid()) {
            return false;
        }

        String name =
                type.name();

        if (name.contains("CARPET")
                || name.contains("VINE")
                || type == Material.SNOW
                || type == Material.POWDER_SNOW
                || type == Material.MOSS_CARPET
                || type == Material.COBWEB
                || type == Material.BAMBOO) {

            return false;
        }

        return type.isSolid()
                || !block.isPassable();
    }


    private static boolean isEquippedCeilingBlock(
            Block block
    ) {

        if (block == null) {
            return false;
        }

        if (block.isLiquid()) {
            return false;
        }

        Material type =
                block.getType();

        if (type.isAir()
                || type == Material.LIGHT
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR) {

            return false;
        }

        return type.isSolid()
                || !block.isPassable();
    }


    /**
     * Approximate top of stairs / slabs so hover doesn't sit inside them.
     */
    private static double equippedBlockTop(
            Block block
    ) {

        Material type =
                block.getType();

        String name =
                type.name();

        if (name.contains("SLAB")) {

            try {

                org.bukkit.block.data.type.Slab slab =
                        (org.bukkit.block.data.type.Slab)
                                block.getBlockData();

                if (slab.getType()
                        == org.bukkit.block.data.type.Slab.Type.TOP) {

                    return 1.0;
                }

                if (slab.getType()
                        == org.bukkit.block.data.type.Slab.Type.DOUBLE) {

                    return 1.0;
                }

                return 0.5;

            } catch (Throwable ignored) {

                return 0.5;
            }
        }

        if (name.contains("STAIR")) {
            return 1.0;
        }

        return 1.0;
    }


    /**
     * ItemDisplays are tiny — one open cell is enough.
     * Water / lava count as open so swimming never forces a head-snap.
     */
    private boolean equippedBlockedAt(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return true;
        }

        Block block =
                location.getBlock();

        Material type =
                block.getType();

        if (type.isAir()
                || type == Material.LIGHT
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || block.isLiquid()) {

            return false;
        }

        String name =
                type.name();

        if (name.contains("CARPET")
                || name.contains("VINE")
                || name.contains("LEAVES")
                || type == Material.SNOW
                || type == Material.POWDER_SNOW
                || type == Material.MOSS_CARPET
                || type == Material.COBWEB
                || type == Material.BAMBOO
                || type == Material.SWEET_BERRY_BUSH) {

            return false;
        }

        return type.isSolid()
                || !block.isPassable();
    }


    /**
     * Climb first, then slide. Linear interpolation through stairs
     * is what ate pets on Y changes.
     */
    private Location avoidTerrainClip(
            Location from,
            Location to,
            double yStep
    ) {

        if (from == null
                || to == null
                || to.getWorld() == null) {

            return from;
        }

        World world =
                to.getWorld();

        int nearY = from.getBlockY();

        int toGround =
                PetWalkSurface.walkGroundY(
                        world,
                        to.getBlockX(),
                        to.getBlockZ(),
                        nearY
                );

        int fromGround =
                PetWalkSurface.walkGroundY(
                        world,
                        from.getBlockX(),
                        from.getBlockZ(),
                        nearY
                );

        // Don't climb walls / building faces — step up at most one block.
        if (toGround > fromGround + MAX_SURFACE_STEP_UP) {

            Location slide =
                    to.clone();

            slide.setY(
                    from.getY()
            );

            if (!solidAt(slide)
                    && isGentleSurfaceStep(
                    fromGround,
                    PetWalkSurface.walkGroundY(
                            world,
                            slide.getBlockX(),
                            slide.getBlockZ(),
                            nearY
                    )
            )) {

                return slide;
            }

            return from.clone();
        }

        double minClearY =
                toGround + 0.72;

        Location dest =
                to.clone();

        if (dest.getY() < minClearY - 0.03) {

            Location climb =
                    from.clone();

            climb.setY(
                    approachY(
                            from.getY(),
                            toGround + HOVER_HEIGHT,
                            yStep
                    )
            );

            if (!solidAt(climb)) {
                return climb;
            }
        }

        dest.setY(
                clampOpenY(
                        world,
                        dest.getX(),
                        dest.getY(),
                        dest.getZ()
                )
        );

        if (!solidAt(dest)) {
            return dest;
        }

        Location rise =
                from.clone();

        rise.setY(
                approachY(
                        from.getY(),
                        toGround + HOVER_HEIGHT,
                        yStep
                )
        );

        if (!solidAt(rise)) {
            return rise;
        }

        Location slide =
                dest.clone();

        slide.setY(
                from.getY()
        );

        if (!solidAt(slide)
                && slide.getY() >= minClearY) {

            return slide;
        }

        return from.clone();
    }

    private double clampOpenY(
            World world,
            double x,
            double y,
            double z
    ) {

        int nearY = Location.locToBlock(y);
        int ground =
                PetWalkSurface.walkGroundY(
                        world,
                        Location.locToBlock(x),
                        Location.locToBlock(z),
                        nearY
                );

        double hover =
                ground + HOVER_HEIGHT;

        Location probe =
                new Location(
                        world,
                        x,
                        y,
                        z
                );

        if (!solidAt(probe)
                && y >= ground + 0.55) {

            return y;
        }

        return hover;
    }

    private boolean solidAt(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return true;
        }

        World world =
                location.getWorld();

        int x =
                location.getBlockX();

        int y =
                location.getBlockY();

        int z =
                location.getBlockZ();

        return !isOpenForPet(
                world.getBlockAt(
                        x,
                        y,
                        z
                )
        )
                || !isOpenForPet(
                world.getBlockAt(
                        x,
                        y + 1,
                        z
                )
        );
    }

    private Location recoverSurfaceStep(
            Location current,
            double speed
    ) {
        PetSpawnType spawnType =
                PetSpawnType.SURFACE;

        int nearY = current.getBlockY();
        int fromGround =
                PetWalkSurface.walkGroundY(
                        current.getWorld(),
                        current.getBlockX(),
                        current.getBlockZ(),
                        nearY
                );

        for (
                int attempt = 0;
                attempt < 16;
                attempt++
        ) {

            Vector direction =
                    createRandomWildDirection(
                            spawnType
                    );

            Location candidate =
                    current.clone()
                            .add(
                                    direction.clone()
                                            .multiply(
                                                    speed
                                            )
                            );

            if (!isValidSurfacePosition(candidate)) {
                continue;
            }

            int toGround =
                    PetWalkSurface.walkGroundY(
                            candidate.getWorld(),
                            candidate.getBlockX(),
                            candidate.getBlockZ(),
                            nearY
                    );

            if (!isGentleSurfaceStep(fromGround, toGround)) {
                continue;
            }

            wildDirection =
                    direction;

            return candidate;
        }

        return rescueDrySurface(current);
    }

    /**
     * Scan nearby dry columns when a surface pet is stuck or submerged.
     */
    private Location rescueDrySurface(Location from) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        World world = from.getWorld();
        int ox = from.getBlockX();
        int oz = from.getBlockZ();
        int nearY = from.getBlockY();
        int fromGround = PetWalkSurface.walkGroundY(world, ox, oz, nearY);
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = ox + dx;
                    int z = oz + dz;
                    if (!PetWalkSurface.isDryWalkColumn(world, x, z, nearY)) {
                        continue;
                    }
                    int groundY = PetWalkSurface.walkGroundY(world, x, z, nearY);
                    if (!isGentleSurfaceStep(fromGround, groundY)) {
                        continue;
                    }
                    Location candidate = new Location(world, x + 0.5, groundY + HOVER_HEIGHT, z + 0.5);
                    if (!isValidSurfacePosition(candidate)) {
                        continue;
                    }
                    double dist = candidate.distanceSquared(from);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private boolean isGentleSurfaceStep(
            int fromGround,
            int toGround
    ) {

        int delta =
                toGround - fromGround;

        return delta <= MAX_SURFACE_STEP_UP
                && delta >= -MAX_SURFACE_STEP_DOWN;
    }

    private Location recoverAquaticStep(Location current, double speed) {
        boolean surfaceSwim = prefersSurfaceSwim(current);
        for (int attempt = 0; attempt < 16; attempt++) {
            Vector direction = createRandomWildDirection(PetSpawnType.AQUATIC, surfaceSwim);
            Location candidate = current.clone().add(direction.clone().multiply(speed));
            if (surfaceSwim) {
                candidate.setY(waterSurfaceY(candidate) + 0.35);
            }
            if (isValidAquaticPosition(candidate)) {
                return candidate;
            }
        }
        return rescueAquatic(current);
    }

    /**
     * Pull aquatic pets back into a water column when they hop onto shore blocks.
     */
    private Location rescueAquatic(Location from) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        World world = from.getWorld();
        int ox = from.getBlockX();
        int oz = from.getBlockZ();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = ox + dx;
                    int z = oz + dz;
                    Location candidate = aquaticCandidateAt(world, x, z, from.getY());
                    if (candidate == null || !isValidAquaticPosition(candidate)) {
                        continue;
                    }
                    double dist = candidate.distanceSquared(from);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private Location aquaticCandidateAt(World world, int x, int z, double preferY) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int start = Math.max(minY, Math.min(maxY, (int) Math.floor(preferY)));
        boolean deep = isDeepAquatic();
        for (int y = start; y >= minY; y--) {
            Block at = world.getBlockAt(x, y, z);
            if (!PetWalkSurface.isFullWaterBlock(at)) {
                continue;
            }
            Location candidate = new Location(world, x + 0.5, y + 0.35, z + 0.5);
            if (!deep || waterColumnDepth(candidate) <= 2) {
                candidate.setY(waterSurfaceY(candidate) + 0.35);
            }
            if (isValidAquaticPosition(candidate)) {
                return candidate;
            }
        }
        for (int y = start + 1; y <= maxY; y++) {
            Block at = world.getBlockAt(x, y, z);
            if (!PetWalkSurface.isFullWaterBlock(at)) {
                continue;
            }
            Location candidate = new Location(world, x + 0.5, y + 0.35, z + 0.5);
            if (!deep || waterColumnDepth(candidate) <= 2) {
                candidate.setY(waterSurfaceY(candidate) + 0.35);
            }
            if (isValidAquaticPosition(candidate)) {
                return candidate;
            }
        }
        return null;
    }


    /*
     * =========================================================
     * CAVE VALIDATION
     * =========================================================
     */

    private boolean isValidCavePosition(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return false;
        }


        int x =
                location.getBlockX();


        int y =
                location.getBlockY();


        int z =
                location.getBlockZ();


        /*
         * Keep the pet inside normal world height.
         */

        if (y <= location.getWorld().getMinHeight() + 1
                || y >= location.getWorld().getMaxHeight() - 2) {

            return false;
        }


        Material feet =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y,
                                z
                        )
                        .getType();


        Material head =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y + 1,
                                z
                        )
                        .getType();


        /*
         * Pet needs two blocks of free space.
         */

        if (!isPassable(feet)
                || !isPassable(head)) {

            return false;
        }


        /*
         * Caves are not water/lava spaces.
         */

        if (isLiquid(feet)
                || isLiquid(head)) {

            return false;
        }


        /*
         * Make sure we are actually underground.
         *
         * There must be at least two blocks of terrain
         * between the pet and the surface.
         */

        /*
         * Use the same heightmap type as
         * PetSpawnManager.findCaveLocation() so both methods
         * agree on what "the surface" means at this column.
         */

        int surfaceY =
                location.getWorld()
                        .getHighestBlockYAt(
                                x,
                                z,
                                HeightMap.MOTION_BLOCKING_NO_LEAVES
                        );


        if (!(y + 2 < surfaceY)) {
            return false;
        }


        if (!isTerrainStable(
                location.getWorld(),
                x,
                z,
                surfaceY,
                2,
                2
        )) {

            return false;
        }


        return true;
    }


    /*
     * =========================================================
     * AQUATIC VALIDATION
     * =========================================================
     */

    private boolean isValidAquaticPosition(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return false;
        }


        int x =
                location.getBlockX();


        int y =
                location.getBlockY();


        int z =
                location.getBlockZ();


        Material feet =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y,
                                z
                        )
                        .getType();


        Material head =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y + 1,
                                z
                        )
                        .getType();

        Block feetBlock =
                location.getWorld().getBlockAt(x, y, z);
        Block headBlock =
                location.getWorld().getBlockAt(x, y + 1, z);

        // Full source-water only — skip waterlogged slabs / half-cells.
        if (!PetWalkSurface.isFullWaterBlock(feetBlock)
                || PetWalkSurface.isPartialWaterObstacle(feetBlock)
                || PetWalkSurface.isPartialWaterObstacle(headBlock)) {
            return false;
        }

        // 1-deep water on solid is fine (ponds / streams). Only reject a
        // completely isolated single-cell puddle with no water next to it.
        Material below =
                location.getWorld()
                        .getBlockAt(x, y - 1, z)
                        .getType();
        if (!isWater(below) && below.isSolid()) {
            int waterNeighbors = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    Block neighbor =
                            location.getWorld().getBlockAt(x + dx, y, z + dz);
                    if (PetWalkSurface.isFullWaterBlock(neighbor)
                            && !PetWalkSurface.isPartialWaterObstacle(
                            location.getWorld().getBlockAt(x + dx, y + 1, z + dz)
                    )) {
                        waterNeighbors++;
                    }
                }
            }
            if (waterNeighbors < 1) {
                return false;
            }
        }

        // Deep vertical swim only when the column is actually deep.
        if (isDeepAquatic() && waterColumnDepth(location) > 2) {

            return PetWalkSurface.isFullWaterBlock(headBlock)
                    || PetWalkSurface.isFullWaterBlock(feetBlock);
        }

        if (isLiquid(head)
                || isWater(head)) {

            return false;
        }

        return headBlock.isPassable();
    }


    /*
     * =========================================================
     * SKY VALIDATION
     * =========================================================
     */

    private boolean isValidSkyPosition(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return false;
        }

        int x =
                location.getBlockX();

        int y =
                location.getBlockY();

        int z =
                location.getBlockZ();

        Block feet =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y,
                                z
                        );

        Block head =
                location.getWorld()
                        .getBlockAt(
                                x,
                                y + 1,
                                z
                        );

        if (!feet.isPassable()
                || !head.isPassable()
                || isLiquid(feet.getType())
                || isLiquid(head.getType())) {

            return false;
        }

        int groundY =
                location.getWorld()
                        .getHighestBlockYAt(
                                x,
                                z,
                                HeightMap.MOTION_BLOCKING_NO_LEAVES
                        );

        if (y < groundY + 6) {
            return false;
        }

        if (y > groundY + 28) {
            return false;
        }

        return true;
    }


    private boolean isPassable(
            Material material
    ) {

        return material != null
                && (material.isAir()
                || material == Material.LIGHT
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR);
    }


    private boolean isLiquid(
            Material material
    ) {

        return material == Material.WATER
                || material == Material.LAVA;
    }


    private boolean isWater(
            Material material
    ) {

        return material == Material.WATER;
    }


    /*
     * =========================================================
     * TERRAIN STABILITY CHECK
     * =========================================================
     *
     * Mirrors PetSpawnManager.isTerrainStable().
     *
     * Keeps this re-validation in sync with the spawn manager's
     * search, so a position that was rejected there cannot
     * silently pass here (or the other way around).
     */

    private boolean isTerrainStable(
            World world,
            int x,
            int z,
            int centerHeight,
            int radius,
            int maxDeviation
    ) {

        int[] offsets = { -radius, 0, radius };

        for (int dx : offsets) {

            for (int dz : offsets) {

                if (dx == 0 && dz == 0) {
                    continue;
                }

                int neighborHeight =
                        world.getHighestBlockYAt(
                                x + dx,
                                z + dz,
                                HeightMap.MOTION_BLOCKING_NO_LEAVES
                        );

                if (
                        Math.abs(
                                neighborHeight - centerHeight
                        )
                                > maxDeviation
                ) {

                    return false;
                }
            }
        }


        return true;
    }


    /*
     * =========================================================
     * WILD HOVER
     * =========================================================
     */

    private void updateWildHover() {

        if (!isSpawned()) {
            return;
        }


        hoverTime += 0.18;


        Location location =
                entity.getLocation();

        PetSpawnType spawnType =
                movementSpawnType();


        double bob =
                Math.sin(
                        hoverTime
                )
                        * HOVER_RANGE;


        if (catching) {

            location.setY(
                    location.getY()
                            - lastHoverBob
                            + bob
            );

            lastHoverBob =
                    bob;

        } else if (spawnType == PetSpawnType.SURFACE) {

            if (Double.isNaN(smoothedHoverY)) {

                smoothedHoverY =
                        location.getY();
            }

            location.setY(
                    smoothedHoverY
                            + bob
            );

            lastHoverBob =
                    bob;

        } else {

            location.setY(
                    location.getY()
                            - lastHoverBob
                            + bob
            );

            lastHoverBob =
                    bob;
        }


        movePet(
                location
        );
    }


    /*
     * =========================================================
     * LOOK
     * =========================================================
     *
     * Facing is independent from movement so pets do not always
     * show their backs while fleeing or following.
     */

    private void applyLook(
            Location location,
            Player glance
    ) {

        if (location == null) {
            return;
        }

        lookTicks -=
                2;

        if (lookTicks <= 0) {

            lookTicks =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    80,
                                    180
                            );

            if (glance != null
                    && glance.isOnline()
                    && ThreadLocalRandom.current()
                    .nextDouble()
                    < 0.22) {

                lookYaw =
                        yawToward(
                                location,
                                glance.getEyeLocation()
                        );
                maybeHappyGlance(
                        glance,
                        location
                );

            } else {

                lookYaw =
                        ThreadLocalRandom.current()
                                .nextFloat()
                                * 360.0F;
            }
        }

        location.setYaw(
                lerpYaw(
                        location.getYaw(),
                        lookYaw,
                        LOOK_TURN_STEP
                )
        );

        location.setPitch(
                0.0F
        );
    }

    private void maybeHappyGlance(
            Player player,
            Location facing
    ) {

        if (player == null
                || facing == null
                || entity == null
                || !entity.isValid()) {

            return;
        }

        long now =
                System.currentTimeMillis();

        UUID ownerId =
                player.getUniqueId();

        Long last =
                LAST_HAPPY_GLANCE_BY_OWNER.get(
                        ownerId
                );

        if (last != null
                && now - last < HAPPY_GLANCE_COOLDOWN_MS) {

            return;
        }

        if (ThreadLocalRandom.current()
                .nextDouble()
                >= HAPPY_GLANCE_CHANCE) {

            return;
        }

        LAST_HAPPY_GLANCE_BY_OWNER.put(
                ownerId,
                now
        );

        Location at =
                entity.getLocation()
                        .add(
                                0.0,
                                0.45,
                                0.0
                        );

        World world =
                at.getWorld();

        /*
         * Hearts only with the rare chat line — never as ambient fluff.
         */
        if (world != null) {

            world.spawnParticle(
                    Particle.HEART,
                    at,
                    2,
                    0.18,
                    0.14,
                    0.18,
                    0.0
            );
        }

        player.playSound(
                at,
                Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                0.35f,
                1.35f
        );

        String petName =
                petInstance.getDefinition()
                        .getDisplayName();

        player.sendMessage(
                PetFlavor.glance(
                        petInstance.getDefinition()
                                .getId(),
                        petName
                )
        );
    }

    private static float yawToward(
            Location from,
            Location to
    ) {

        if (from == null
                || to == null) {

            return 0.0F;
        }

        double dx =
                to.getX()
                        - from.getX();

        double dz =
                to.getZ()
                        - from.getZ();

        return (float) Math.toDegrees(
                Math.atan2(
                        -dx,
                        dz
                )
        );
    }

    private static float lerpYaw(
            float from,
            float to,
            float maxStep
    ) {

        float delta =
                wrapDegrees(
                        to - from
                );

        if (Math.abs(delta) <= maxStep) {
            return to;
        }

        return from
                + Math.copySign(
                maxStep,
                delta
        );
    }

    private static float wrapDegrees(
            float yaw
    ) {

        yaw %=
                360.0F;

        if (yaw >= 180.0F) {
            yaw -=
                    360.0F;
        }

        if (yaw < -180.0F) {
            yaw +=
                    360.0F;
        }

        return yaw;
    }

    private static double approachY(
            double current,
            double desired,
            double maxStep
    ) {

        double delta =
                desired
                        - current;

        if (Math.abs(delta) <= maxStep) {
            return desired;
        }

        return current
                + Math.copySign(
                maxStep,
                delta
        );
    }


    /*
     * =========================================================
     * SHINY EFFECT
     * =========================================================
     */

    private void updateShinyEffect() {

        if (!isSpawned()) {
            return;
        }


        if (petInstance.getVariant()
                != PetVariant.SHINY) {

            return;
        }


        shinyParticleStep++;


        if (shinyParticleStep
                % SHINY_PARTICLE_INTERVAL
                != 0) {

            return;
        }


        Location location =
                entity.getLocation()
                        .clone();


        location.add(
                0,
                SHINY_PARTICLE_HEIGHT,
                0
        );


        Color color =
                getShinyParticleColor();


        Particle.DustOptions dust =
                new Particle.DustOptions(
                        color,
                        0.95F
                );


        entity.getWorld()
                .spawnParticle(
                        Particle.DUST,
                        location,
                        3,
                        0.16,
                        0.16,
                        0.16,
                        0.0,
                        dust
                );

        entity.getWorld()
                .spawnParticle(
                        Particle.END_ROD,
                        location,
                        1,
                        0.08,
                        0.12,
                        0.08,
                        0.0
                );
    }


    private Color getShinyParticleColor() {

        Color[] colors = {

                Color.RED,
                Color.ORANGE,
                Color.YELLOW,
                Color.LIME,
                Color.AQUA,
                Color.BLUE,
                Color.FUCHSIA
        };


        return colors[
                (shinyParticleStep
                        / SHINY_PARTICLE_INTERVAL)
                        % colors.length
                ];
    }


    private void updateAuraEffect() {

        if (!isSpawned()) {
            return;
        }

        String id =
                petInstance.getDefinition() == null
                        ? ""
                        : petInstance.getDefinition()
                                .getId()
                                .toLowerCase();

        boolean dragon = id.endsWith("_dragon") || id.equals("aetherion");
        if (!dragon
                && !id.equals("blaze")
                && !id.equals("ghast")) {
            return;
        }

        auraParticleStep++;

        if (auraParticleStep % AURA_PARTICLE_INTERVAL != 0) {
            return;
        }

        Location location =
                entity.getLocation()
                        .clone()
                        .add(0.0, 0.28, 0.0);

        World world =
                entity.getWorld();

        if (id.equals("ice_dragon")) {
            world.spawnParticle(
                    Particle.SNOWFLAKE,
                    location,
                    2,
                    0.18,
                    0.22,
                    0.18,
                    0.01
            );
        } else if (id.equals("fire_dragon")
                || id.equals("blaze")) {
            world.spawnParticle(
                    Particle.FLAME,
                    location,
                    1,
                    0.12,
                    0.16,
                    0.12,
                    0.005
            );
        } else if (id.equals("water_dragon")) {
            world.spawnParticle(
                    Particle.BUBBLE,
                    location,
                    2,
                    0.16,
                    0.20,
                    0.16,
                    0.01
            );
        } else if (id.equals("nature_dragon")) {
            world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    location,
                    2,
                    0.16,
                    0.20,
                    0.16,
                    0.0
            );
        } else if (id.equals("mining_dragon") || id.equals("poison_dragon")) {
            world.spawnParticle(
                    Particle.CRIT,
                    location,
                    2,
                    0.14,
                    0.18,
                    0.14,
                    0.01
            );
        } else if (id.equals("forest_dragon")) {
            world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    location,
                    1,
                    0.14,
                    0.18,
                    0.14,
                    0.0
            );
            world.spawnParticle(
                    Particle.BLOCK,
                    location,
                    1,
                    0.1,
                    0.12,
                    0.1,
                    0.0,
                    Material.OAK_LEAVES.createBlockData()
            );
        } else if (id.equals("lightning_dragon")) {
            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    location,
                    1,
                    0.16,
                    0.20,
                    0.16,
                    0.0
            );
        } else if (id.equals("ghast")) {
            world.spawnParticle(
                    Particle.CLOUD,
                    location,
                    1,
                    0.14,
                    0.16,
                    0.14,
                    0.0
            );
        } else if (id.equals("aetherion")) {
            world.spawnParticle(
                    Particle.DUST,
                    location,
                    2,
                    0.16,
                    0.20,
                    0.16,
                    0.0,
                    AETHERION_AURA
            );
        }

        // Soft crimson veil on Aethered dragons — on top of their normal aura.
        if (dragon && petInstance.getRarity() == Rarity.AETHERED) {
            world.spawnParticle(
                    Particle.DUST,
                    location.clone().add(0.0, 0.12, 0.0),
                    1,
                    0.10,
                    0.14,
                    0.10,
                    0.0,
                    AETHERED_AURA
            );
            if (auraParticleStep % (AURA_PARTICLE_INTERVAL * 3) == 0) {
                world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        location,
                        1,
                        0.08,
                        0.10,
                        0.08,
                        0.0
                );
            }
        }
    }


    /*
     * =========================================================
     * DISPLAY NAME
     * =========================================================
     */

    public void updateDisplayName() {

        if (!isSpawned()) {
            return;
        }


        entity.setCustomName(
                getDisplayName()
        );


        entity.setCustomNameVisible(
                false
        );

        if (nameplate == null
                || !nameplate.isValid()) {

            spawnNameplate();
        }

        if (nameplate != null) {

            nameplate.text(
                    LegacyComponentSerializer
                            .legacySection()
                            .deserialize(
                                    getDisplayName()
                            )
            );
        }
    }


    /** Nameplates are temporary. A stale !isValid() ref must not leave the old display up. */
    private void purgeOrphanNameplates() {
        if (entity == null || !entity.isValid() || entity.getWorld() == null) {
            return;
        }
        String id = entity.getUniqueId().toString();
        for (org.bukkit.entity.Entity nearby : entity.getWorld().getNearbyEntities(entity.getLocation(), 4, 4, 4)) {
            if (!(nearby instanceof TextDisplay display) || !display.isValid()) {
                continue;
            }
            if (nameplate != null && display.getUniqueId().equals(nameplate.getUniqueId())) {
                continue;
            }
            String owner = display.getPersistentDataContainer().get(NAMEPLATE_OWNER, PersistentDataType.STRING);
            if (id.equals(owner)) {
                display.remove();
            }
        }
    }

    private void spawnNameplate() {

        if (!isSpawned()
                || entity.getWorld() == null) {

            return;
        }

        if (nameplate != null
                && nameplate.isValid()) {

            return;
        }

        purgeOrphanNameplates();

        nameplate =
                entity.getWorld()
                        .spawn(
                                entity.getLocation(),
                                TextDisplay.class,
                                display -> {

                                    display.setBillboard(
                                            Display.Billboard.CENTER
                                    );

                                    display.setSeeThrough(
                                            false
                                    );

                                    display.setShadowed(
                                            true
                                    );

                                    display.setAlignment(
                                            TextDisplay.TextAlignment.CENTER
                                    );

                                    display.setBackgroundColor(
                                            Color.fromARGB(
                                                    0,
                                                    0,
                                                    0,
                                                    0
                                            )
                                    );

                                    display.setPersistent(
                                            false
                                    );

                                    display.setGravity(
                                            false
                                    );

                                    display.setTeleportDuration(
                                            3
                                    );

                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(
                                                            0f,
                                                            0.82f,
                                                            0f
                                                    ),
                                                    new Quaternionf(),
                                                    new Vector3f(
                                                            0.75f,
                                                            0.75f,
                                                            0.75f
                                                    ),
                                                    new Quaternionf()
                                            )
                                    );

                                    display.getPersistentDataContainer()
                                            .set(
                                                    PET_ENTITY_KEY,
                                                    PET_ENTITY_KEY_TYPE,
                                                    (byte) 1
                                            );
                                    display.getPersistentDataContainer()
                                            .set(
                                                    NAMEPLATE_OWNER,
                                                    PersistentDataType.STRING,
                                                    entity.getUniqueId().toString()
                                            );
                                }
                        );
    }


    private void movePet(
            Location location
    ) {

        if (!isSpawned()
                || location == null
                || location.getWorld() == null) {

            return;
        }

        Location dest =
                location.clone();

        entity.teleport(
                dest
        );

        if (nameplate == null
                || !nameplate.isValid()) {

            spawnNameplate();
        }

        if (nameplate != null
                && nameplate.isValid()) {

            nameplate.teleport(
                    dest
            );
        }
    }


    private String getDisplayName() {

        String color =
                getRarityColor(
                        petInstance.getRarity()
                );


        String petName =
                petInstance
                        .getDefinition()
                        .getDisplayName();


        String rarityName =
                prettyRarity(
                        petInstance.getRarity()
                );

        if (petInstance.getVariant()
                == PetVariant.SHINY) {

            return rainbowShinyName(
                    petName
            )
                    + "\n"
                    + color
                    + rarityName;
        }


        return color
                + petName
                + "\n"
                + color
                + rarityName
                + " §7Lv. "
                + petInstance.getLevel();
    }

    private String prettyRarity(
            Rarity rarity
    ) {

        if (rarity == null) {
            return "Common";
        }
        String raw =
                rarity.name()
                        .toLowerCase();
        return Character.toUpperCase(
                raw.charAt(
                        0
                )
        )
                + raw.substring(
                        1
                );
    }


    private String rainbowShinyName(
            String petName
    ) {

        StringBuilder result =
                new StringBuilder();


        result.append(
                "§l✨ "
        );


        result.append(
                "§l"
        );


        result.append(
                rainbowText(
                        "SHINY"
                )
        );


        result.append(
                "§r §l"
        );


        result.append(
                petName
        );


        result.append(
                " §7Lv. "
        );


        result.append(
                petInstance.getLevel()
        );


        return result.toString();
    }


    private String rainbowText(
            String text
    ) {

        String[] colors = {

                "§c",
                "§6",
                "§e",
                "§a",
                "§b",
                "§9",
                "§d"
        };


        StringBuilder result =
                new StringBuilder();


        for (
                int i = 0;
                i < text.length();
                i++
        ) {

            result.append(
                    colors[
                            i % colors.length
                            ]
            );


            result.append(
                    text.charAt(i)
            );
        }


        return result.toString();
    }


    private String getRarityColor(
            Rarity rarity
    ) {

        return switch (rarity) {

            case COMMON ->

                    ChatColor.WHITE.toString();


            case UNCOMMON ->

                    ChatColor.GREEN.toString();


            case RARE ->

                    ChatColor.BLUE.toString();


            case EPIC ->

                    ChatColor.DARK_PURPLE.toString();


            case LEGENDARY ->

                    ChatColor.GOLD.toString();


            case MYTHIC ->

                    ChatColor.LIGHT_PURPLE.toString();

            case AETHERED ->

                    ChatColor.DARK_RED.toString();
        };
    }


    /*
     * =========================================================
     * GENERAL
     * =========================================================
     */

    private AetherMobs getPlugin() {

        return (AetherMobs)
                Bukkit.getPluginManager()
                        .getPlugin(
                                "AetherMobs"
                        );
    }


    public void teleport(
            Location location
    ) {

        if (!isSpawned()
                || location == null) {

            return;
        }

        smoothedHoverY =
                location.getY();

        lookYaw =
                location.getYaw();

        followAlong = null;
        lastFollowFrom = null;

        movePet(
                location
        );
    }


    /*
     * =========================================================
     * LIFECYCLE
     * =========================================================
     */

    public void remove() {

        if (movementTask != null) {

            movementTask.cancel();

            movementTask = null;
        }


        if (nameplate != null) {

            nameplate.remove();

            nameplate = null;
        }


        if (entity != null) {

            entity.remove();

            entity = null;
        }
    }


    public boolean isWild() {

        return owner == null
                && companionHost == null;
    }


    public void setCatchBeacon(
            boolean enabled
    ) {

        if (!isSpawned()
                || isEquipped()) {

            return;
        }

        entity.setGlowing(
                enabled
        );

        if (nameplate != null
                && nameplate.isValid()) {

            nameplate.setSeeThrough(
                    enabled
            );
        }
    }


    public PetInstance getPetInstance() {

        return petInstance;
    }


    public ItemDisplay getEntity() {

        return entity;
    }


    public Player getOwner() {

        return owner;
    }


    public boolean isSpawned() {

        return entity != null
                && entity.isValid()
                && !entity.isDead();
    }
}