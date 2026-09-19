package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.aethermobs.pet.CatchSphere;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.aethermobs.event.PetCaughtEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetCatchListener implements Listener {

    private static final double CATCH_RADIUS = 2.0;

    private static final int FLEE_ANIMATION_TICKS = 14;
    private static final int FLEE_AFTER_ATTEMPTS = 3;
    private static final double FLEE_CHANCE = 50.0;

    private final AetherMobs plugin;
    private final Map<UUID, CatchTimingSession> timingSessions = new ConcurrentHashMap<>();

    public PetCatchListener(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileLaunch(
            ProjectileLaunchEvent event
    ) {

        Projectile projectile =
                event.getEntity();

        if (!(projectile instanceof Snowball snowball)) {
            return;
        }

        if (!(snowball.getShooter()
                instanceof Player player)) {

            return;
        }

        CatchSphere sphere = plugin.getBetaSphereManager().getThrownSphere(snowball);

        if (sphere == null) {
            sphere = plugin.getBetaSphereManager().getSphere(
                    player.getInventory().getItemInMainHand()
            );
        }

        if (sphere == null) {
            sphere = plugin.getBetaSphereManager().getSphere(
                    player.getInventory().getItemInOffHand()
            );
        }

        if (sphere == null) {
            return;
        }

        startTrackingSnowball(
                snowball,
                player,
                sphere
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTimingClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        CatchTimingSession session = timingSessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.locked) {
            return;
        }
        event.setCancelled(true);
        if (!session.tryLock()) {
            return;
        }
        Player player = event.getPlayer();
        if (session.perfect) {
            player.sendActionBar("§a✦ Perfect timing");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.85f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.7f);
        } else {
            player.sendActionBar("§7Timing locked");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CatchTimingSession session = timingSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.close();
        }
    }

    private void startTrackingSnowball(
            Snowball snowball,
            Player player,
            CatchSphere sphere
    ) {

        new BukkitRunnable() {

            @Override
            public void run() {

                if (!snowball.isValid()
                        || snowball.isDead()) {

                    cancel();
                    return;
                }

                for (PetEntity pet :
                        plugin.getPetSpawnManager()
                                .getActivePets()) {

                    if (!pet.isSpawned()) {
                        continue;
                    }

                    if (pet.isCatching()) {
                        continue;
                    }

                    if (!pet.getEntity()
                            .getWorld()
                            .equals(
                                    snowball.getWorld()
                            )) {

                        continue;
                    }

                    double distanceSquared =
                            pet.getEntity()
                                    .getLocation()
                                    .distanceSquared(
                                            snowball.getLocation()
                                    );

                    if (distanceSquared
                            > CATCH_RADIUS
                            * CATCH_RADIUS) {

                        continue;
                    }

                    if (!pet.canBeCaughtBy(player)) {
                        NamespacedKey denied = new NamespacedKey(plugin, "catch_denied");
                        if (snowball.getPersistentDataContainer().get(denied, PersistentDataType.BYTE) == null) {
                            snowball.getPersistentDataContainer().set(denied, PersistentDataType.BYTE, (byte) 1);
                            player.sendMessage("§7This catch isn't yours.");
                            player.playSound(
                                    player.getLocation(),
                                    Sound.BLOCK_NOTE_BLOCK_BASS,
                                    0.45f,
                                    0.55f
                            );
                        }
                        continue;
                    }

                    snowball.remove();

                    startCatchAnimation(
                            player,
                            pet,
                            sphere
                    );

                    cancel();
                    return;
                }
            }

        }.runTaskTimer(
                plugin,
                0L,
                1L
        );
    }

    private void startCatchAnimation(
            Player player,
            PetEntity pet,
            CatchSphere sphere
    ) {

        pet.startCatching();

        PetInstance petInstance =
                pet.getPetInstance();

        if (petInstance.getDefinition() != null) {

            plugin.markPetSighted(
                    player,
                    petInstance.getDefinition()
                            .getId()
            );
        }

        String petName =
                petInstance.getDefinition()
                        .getDisplayName();

        String rarity =
                formatRarity(
                        petInstance.getRarity()
                );

        double catchChance =
                getCatchChance(player, sphere, petInstance.getRarity());

        CatchTimingSession prior = timingSessions.remove(player.getUniqueId());
        if (prior != null) {
            prior.close();
        }
        CatchTimingSession session = new CatchTimingSession(player, pet, catchChance);
        timingSessions.put(player.getUniqueId(), session);

        player.sendMessage("");
        player.sendMessage(
                "§d§l✦ PET HIT ✦"
        );
        player.sendMessage("");

        player.sendMessage(
                "§7Pet: "
                        + getRarityColor(
                        petInstance.getRarity()
                )
                        + petName
        );

        player.sendMessage(
                "§7Rarity: "
                        + getRarityColor(
                        petInstance.getRarity()
                )
                        + rarity
        );

        player.sendMessage(
                "§7Catch Chance: §d"
                        + formatChance(
                        catchChance
                )
                        + " §8(click the green window for a boost)"
        );

        player.sendMessage("");

        player.sendMessage(
                "§d✦ §fThe pet is trying to escape..."
        );

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                1.0f,
                1.4f
        );

        new BukkitRunnable() {

            private double baseY = pet.isSpawned()
                    ? pet.getEntity().getLocation().getY()
                    : 0.0;
            private boolean anchored;

            @Override
            public void run() {

                CatchTimingSession active = timingSessions.get(player.getUniqueId());
                if (active == null || active != session) {
                    cancel();
                    return;
                }

                if (!pet.isSpawned()
                        || pet.getEntity().isDead()) {
                    timingSessions.remove(player.getUniqueId(), session);
                    session.close();
                    cancel();
                    return;
                }

                if (!anchored) {
                    baseY = pet.getEntity().getLocation().getY();
                    anchored = true;
                }

                session.pulse();

                double progress =
                        session.ticks
                                / (double)
                                CatchTimingSession.DURATION_TICKS;

                // Soft hover + slight spin-up feel toward the end.
                double bounce =
                        Math.sin(
                                progress
                                        * Math.PI
                                        * 5.0
                        )
                                * (0.12 + progress * 0.08);

                var location =
                        pet.getEntity()
                                .getLocation();

                location.setY(baseY + bounce);
                location.setYaw(location.getYaw() + 8.0f + (float) (progress * 10.0));

                pet.getEntity()
                        .teleport(location);

                var world = pet.getEntity().getWorld();
                world.spawnParticle(
                        Particle.END_ROD,
                        location.clone().add(0, 0.55, 0),
                        session.perfect ? 4 : 2,
                        0.18,
                        0.18,
                        0.18,
                        0.01
                );

                if (session.ticks % 4 == 0) {
                    world.spawnParticle(
                            Particle.ENCHANT,
                            location.clone().add(0, 0.7, 0),
                            6,
                            0.25,
                            0.25,
                            0.25,
                            0.4
                    );
                }

                if (session.perfect && session.ticks % 3 == 0) {
                    world.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            location.clone().add(0, 0.8, 0),
                            2,
                            0.2,
                            0.2,
                            0.2,
                            0.0
                    );
                }

                if (session.done()) {
                    timingSessions.remove(player.getUniqueId(), session);
                    session.close();
                    cancel();

                    finishCatch(
                            player,
                            pet,
                            sphere,
                            session.multiplier()
                    );
                }
            }

        }.runTaskTimer(
                plugin,
                0L,
                1L
        );
    }

    private void finishCatch(
            Player player,
            PetEntity pet,
            CatchSphere sphere,
            double timingMult
    ) {

        if (!pet.isSpawned()) {
            return;
        }

        PetInstance petInstance =
                pet.getPetInstance();

        double catchChance =
                Math.max(
                        0.0d,
                        Math.min(
                                100.0d,
                                getCatchChance(player, sphere, petInstance.getRarity())
                                        * timingMult
                        )
                );

        String petName =
                petInstance.getDefinition()
                        .getDisplayName();

        String rarity =
                formatRarity(
                        petInstance.getRarity()
                );

        boolean firstSphere = consumeFirstSphereGuarantee(player);
        boolean caught = firstSphere
                || pet.isGuaranteedCatch()
                || Math.random() * 100.0 < catchChance;

        if (pet.isGuaranteedCatch()) {
            player.sendMessage("§5✦ Guaranteed catch §7· §5this one is yours.");
        } else if (!firstSphere && timingMult > 1.0d) {
            player.sendMessage(
                    "§a✦ Perfect timing §7boosted catch chance to §d"
                            + formatChance(catchChance)
            );
        }

        if (caught) {

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.2f
            );
            if (timingMult > 1.0d) {
                player.playSound(
                        player.getLocation(),
                        Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                        0.55f,
                        1.4f
                );
            }

            var catchLoc = pet.getEntity().getLocation().add(0, 0.7, 0);
            pet.getEntity()
                    .getWorld()
                    .spawnParticle(
                            Particle.TOTEM_OF_UNDYING,
                            catchLoc,
                            timingMult > 1.0d ? 36 : 25,
                            0.35,
                            0.35,
                            0.35,
                            0.1
                    );
            pet.getEntity()
                    .getWorld()
                    .spawnParticle(
                            Particle.END_ROD,
                            catchLoc,
                            18,
                            0.4,
                            0.4,
                            0.4,
                            0.05
                    );

            player.sendTitle(
                    "§a✦ Caught!",
                    "§7" + getRarityColor(petInstance.getRarity()) + petName,
                    5,
                    28,
                    12
            );

            player.sendMessage("");
            player.sendMessage(
                    "§a§l✦ PET CAUGHT! ✦"
            );
            player.sendMessage("");

            player.sendMessage(
                    "§7Pet: "
                            + getRarityColor(
                            petInstance.getRarity()
                    )
                            + petName
            );

            player.sendMessage(
                    "§7Rarity: "
                            + getRarityColor(
                            petInstance.getRarity()
                    )
                            + rarity
            );

            player.sendMessage(
                    "§7Variant: §f"
                            + formatVariant(
                            petInstance.getVariant()
                    )
            );

            player.sendMessage(
                    "§7Level: §f"
                            + petInstance.getLevel()
            );

            player.sendMessage("");

            player.sendMessage(
                    "§6Core Stat:"
            );

            player.sendMessage(
                    "§7"
                            + formatCapability(
                            petInstance
                                    .getStats()
                                    .getCoreStat()
                    )
                            + ": §f"
                            + formatValue(
                            petInstance
                                    .getStats()
                                    .getCoreValue()
                    )
            );

            player.sendMessage("");

            player.sendMessage(
                    "§6Bonus Stats:"
            );

            if (petInstance
                    .getStats()
                    .getBonusStats()
                    .isEmpty()) {

                player.sendMessage(
                        "§7None"
                );

            } else {

                for (
                        var entry :
                        petInstance
                                .getStats()
                                .getBonusStats()
                                .entrySet()
                ) {

                    player.sendMessage(
                            "§7"
                                    + formatCapability(
                                    entry.getKey()
                            )
                                    + ": §f"
                                    + formatValue(
                                    entry.getValue()
                            )
                    );
                }
            }

            player.sendMessage("");

            String petId = petInstance.getDefinition() == null
                    ? ""
                    : petInstance.getDefinition().getId();
            var collection = plugin.getPetCollection(player);
            collection.addPet(petInstance);
            if (collection.claimFirstCatchXp(petId)) {
                grantAetherionCatchXp(player, true);
            }
            grantGaffCatchXp(player, petInstance.getRarity());

            Bukkit.getPluginManager().callEvent(new PetCaughtEvent(player, petId));

            player.sendMessage(
                    "§a✦ Added to your collection!"
            );

            player.sendMessage("");

            pet.remove();

        } else {

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ITEM_BREAK,
                    1.0f,
                    1.1f
            );

            pet.getEntity()
                    .getWorld()
                    .spawnParticle(
                            Particle.SMOKE,
                            pet.getEntity()
                                    .getLocation()
                                    .add(
                                            0,
                                            0.5,
                                            0
                                    ),
                            12,
                            0.25,
                            0.25,
                            0.25,
                            0.02
                    );

            player.sendMessage("");
            player.sendMessage(
                    "§c§l✦ PET ESCAPED! ✦"
            );
            player.sendMessage("");

            player.sendMessage(
                    "§7The "
                            + getRarityColor(
                            petInstance.getRarity()
                    )
                            + petName
                            + " §7escaped the sphere!"
            );

            player.sendMessage("");

            pet.registerCatchAttempt();

            pet.stopCatching();

            if (pet.getCatchAttempts()
                    >= FLEE_AFTER_ATTEMPTS
                    && Math.random() * 100.0
                    < FLEE_CHANCE) {

                startFleeAnimation(
                        player,
                        pet
                );
            }
        }
    }

    private void startFleeAnimation(
            Player player,
            PetEntity pet
    ) {

        if (!pet.isSpawned()) {
            return;
        }

        pet.startCatching();

        Vector away =
                pet.getEntity()
                        .getLocation()
                        .toVector()
                        .subtract(
                                player.getLocation()
                                        .toVector()
                        );

        away.setY(
                0
        );

        if (away.lengthSquared()
                < 0.01) {

            away =
                    new Vector(
                            1,
                            0,
                            0
                    );
        }

        Vector fleeDirection =
                away.normalize()
                        .multiply(
                                0.45
                        );

        fleeDirection.setY(
                0.12
        );

        player.playSound(
                pet.getEntity()
                        .getLocation(),
                Sound.ENTITY_FOX_AMBIENT,
                1.0f,
                1.6f
        );

        new BukkitRunnable() {

            private int ticks = 0;

            @Override
            public void run() {

                if (!pet.isSpawned()
                        || pet.getEntity()
                        .isDead()) {

                    cancel();
                    return;
                }

                var location =
                        pet.getEntity()
                                .getLocation()
                                .add(
                                        fleeDirection
                                );

                pet.getEntity()
                        .teleport(
                                location
                        );

                pet.getEntity()
                        .getWorld()
                        .spawnParticle(
                                Particle.CLOUD,
                                location.clone()
                                        .add(
                                                0,
                                                0.3,
                                                0
                                        ),
                                4,
                                0.2,
                                0.15,
                                0.2,
                                0.01
                        );

                ticks++;

                if (ticks
                        < FLEE_ANIMATION_TICKS) {

                    return;
                }

                cancel();

                pet.getEntity()
                        .getWorld()
                        .spawnParticle(
                                Particle.POOF,
                                pet.getEntity()
                                        .getLocation()
                                        .add(
                                                0,
                                                0.4,
                                                0
                                        ),
                                18,
                                0.3,
                                0.3,
                                0.3,
                                0.04
                        );

                pet.getEntity()
                        .getWorld()
                        .playSound(
                                pet.getEntity()
                                        .getLocation(),
                                Sound.ENTITY_GENERIC_EXTINGUISH_FIRE,
                                0.9f,
                                1.4f
                        );

                String petName =
                        pet.getPetInstance()
                                .getDefinition()
                                .getDisplayName();

                player.sendMessage(
                        "§e✦ "
                                + getRarityColor(
                                pet.getPetInstance()
                                        .getRarity()
                        )
                                + petName
                                + " §7ran away!"
                );

                player.sendMessage("");

                pet.remove();
            }

        }.runTaskTimer(
                plugin,
                0L,
                1L
        );
    }

    private double getCatchChance(Player player, CatchSphere sphere, de.aetherion.items.model.Rarity petRarity) {
        double chance = sphere != null ? sphere.getCatchChance(petRarity) : 0.0;

        AetherionItems items = AetherionItems.getInstance();

        if (items != null && items.getItemManager() != null) {
            double gear = new ActiveEquipmentStats(items.getItemManager())
                    .getStat(player, ItemCapability.PET_CATCH_RATE);
            // Gear scales the sphere rate (Skyblock-ish), never a flat +61% Mythic.
            double scale = 1.0d + Math.min(0.80d, Math.max(0.0d, gear) / 100.0d);
            chance *= scale;
        }

        return Math.max(0.0, Math.min(100.0, chance));
    }

    /** One-time Aetherion XP the first time this pet species is caught. */
    private void grantAetherionCatchXp(Player player, boolean firstOfKind) {
        if (!firstOfKind) {
            return;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getSkills() == null) {
            return;
        }
        long amount = 90L;
        items.getSkills().addBonusXp(player, amount);
        player.sendMessage("§b+" + amount + " Aetherion XP §8· §7first catch");
    }

    /** Off-hand Catcher Gaff XP — rarer pets feed the tool harder. */
    private void grantGaffCatchXp(Player player, de.aetherion.items.model.Rarity rarity) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getItemManager() == null) {
            return;
        }
        int amount = de.aetherion.items.item.CatcherGaffProgress.xpForCatch(rarity);
        de.aetherion.items.item.CatcherGaffProgress.grantEquipped(player, items.getItemManager(), amount);
    }

    private String formatRarity(
            Object rarity
    ) {

        String text =
                rarity.toString()
                        .toLowerCase();

        return Character.toUpperCase(
                text.charAt(0)
        )
                + text.substring(1);
    }

    private String formatVariant(
            PetVariant variant
    ) {

        String text =
                variant.toString()
                        .toLowerCase();

        return Character.toUpperCase(
                text.charAt(0)
        )
                + text.substring(1);
    }

    private String formatCapability(
            ItemCapability capability
    ) {

        String text =
                capability.toString()
                        .toLowerCase()
                        .replace(
                                "_",
                                " "
                        );

        StringBuilder result =
                new StringBuilder();

        for (String word :
                text.split(" ")) {

            if (word.isEmpty()) {
                continue;
            }

            result.append(
                    Character.toUpperCase(
                            word.charAt(0)
                    )
            );

            result.append(
                    word.substring(1)
            );

            result.append(" ");
        }

        return result.toString().trim();
    }

    private String formatValue(
            double value
    ) {

        return String.format(
                "%.2f",
                value
        );
    }

    private String formatChance(
            double chance
    ) {

        if (chance == Math.floor(chance)) {

            return String.format(
                    "%.0f%%",
                    chance
            );
        }

        return String.format(
                "%.2f%%",
                chance
        );
    }

    private String getRarityColor(
            Object rarity
    ) {

        return switch (
                rarity.toString()
                ) {

            case "COMMON" ->
                    ChatColor.WHITE.toString();

            case "UNCOMMON" ->
                    ChatColor.GREEN.toString();

            case "RARE" ->
                    ChatColor.BLUE.toString();

            case "EPIC" ->
                    ChatColor.DARK_PURPLE.toString();

            case "LEGENDARY" ->
                    ChatColor.GOLD.toString();

            case "MYTHIC" ->
                    ChatColor.LIGHT_PURPLE.toString();

            case "AETHERED" ->
                    ChatColor.DARK_RED.toString();

            default ->
                    ChatColor.WHITE.toString();
        };
    }

    /**
     * Silent first-sphere guarantee: first catch attempt ever for this player always succeeds.
     * Not announced — soft tutorial safety so Lark's starter spheres aren't wasted.
     */
    private boolean consumeFirstSphereGuarantee(Player player) {
        if (player == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, "first_sphere_done");
        if (player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return false;
        }
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        return true;
    }
}