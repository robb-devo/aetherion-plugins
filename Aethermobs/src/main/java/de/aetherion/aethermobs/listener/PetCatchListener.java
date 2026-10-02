package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.aethermobs.pet.CatchSphere;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PlayerPetCollection;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;
import de.aetherion.aethermobs.event.PetCaughtEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The catch ritual. A sphere hit holds the pet; the player gets one timing window;
 * then the sphere settles, shakes, and either clicks shut or cracks open.
 *
 * <p>Beats are carried by sound and a held pause, not by particles. The catch
 * odds, timing window, flee rule and rewards are unchanged from the original flow.
 */
public class PetCatchListener implements Listener {

    private static final double CATCH_RADIUS = 2.0;

    private static final int FLEE_ANIMATION_TICKS = 14;
    private static final int FLEE_AFTER_ATTEMPTS = 3;
    private static final double FLEE_CHANCE = 50.0;

    /** Silence between the timing window and the first shake. */
    private static final int SETTLE_TICKS = 7;
    /** Ticks between shakes. */
    private static final int SHAKE_GAP = 9;
    private static final int SHAKES_TO_CATCH = 3;
    /** Yaw wiggle per tick of a shake; empty tail keeps the pet still between beats. */
    private static final float[] SHAKE_YAW = {16.0f, -16.0f, 9.0f, 0.0f};

    private final AetherMobs plugin;
    private final Map<UUID, Ritual> rituals = new ConcurrentHashMap<>();

    public PetCatchListener(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
    }

    /** A sphere is already holding a pet for this player — no second throw. */
    public boolean isInRitual(Player player) {
        return player != null && rituals.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onProjectileLaunch(
            ProjectileLaunchEvent event
    ) {

        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Snowball snowball)) {
            return;
        }

        if (!(snowball.getShooter() instanceof Player player)) {
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

        startTrackingSnowball(snowball, player, sphere);
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
        Ritual ritual = rituals.get(event.getPlayer().getUniqueId());
        if (ritual == null || ritual.phase != Phase.WINDOW || ritual.session.locked) {
            return;
        }
        event.setCancelled(true);
        if (!ritual.session.tryLock()) {
            return;
        }
        Player player = event.getPlayer();
        if (ritual.session.perfect) {
            player.sendActionBar(legacy("§a✦ Perfect timing"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.85f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.7f);
        } else {
            player.sendActionBar(legacy("§7Timing locked"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Ritual ritual = rituals.remove(event.getPlayer().getUniqueId());
        if (ritual != null) {
            ritual.abort();
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

                if (!snowball.isValid() || snowball.isDead()) {
                    cancel();
                    return;
                }

                for (PetEntity pet : plugin.getPetSpawnManager().getActivePets()) {

                    if (!pet.isSpawned() || pet.isCatching()) {
                        continue;
                    }

                    if (!pet.getEntity().getWorld().equals(snowball.getWorld())) {
                        continue;
                    }

                    double distanceSquared = pet.getEntity()
                            .getLocation()
                            .distanceSquared(snowball.getLocation());

                    if (distanceSquared > CATCH_RADIUS * CATCH_RADIUS) {
                        continue;
                    }

                    if (!pet.canBeCaughtBy(player)) {
                        NamespacedKey denied = new NamespacedKey(plugin, "catch_denied");
                        if (snowball.getPersistentDataContainer().get(denied, PersistentDataType.BYTE) == null) {
                            snowball.getPersistentDataContainer().set(denied, PersistentDataType.BYTE, (byte) 1);
                            player.sendActionBar(legacy("§7This catch isn't yours."));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.55f);
                        }
                        continue;
                    }

                    snowball.remove();
                    startCatch(player, pet, sphere);
                    cancel();
                    return;
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    /*
     * =========================================================
     * THE RITUAL
     * =========================================================
     */

    private enum Phase {
        WINDOW,
        SETTLE,
        SHAKE
    }

    private void startCatch(
            Player player,
            PetEntity pet,
            CatchSphere sphere
    ) {

        Ritual prior = rituals.remove(player.getUniqueId());
        if (prior != null) {
            prior.abort();
        }

        pet.startCatching();

        PetInstance petInstance = pet.getPetInstance();

        if (petInstance.getDefinition() != null) {
            plugin.markPetSighted(player, petInstance.getDefinition().getId());
        }

        Ritual ritual = new Ritual(player, pet, sphere);
        rituals.put(player.getUniqueId(), ritual);
        ritual.announceHit();
        ritual.runTaskTimer(plugin, 0L, 1L);
    }

    private final class Ritual extends BukkitRunnable {

        private final Player player;
        private final UUID playerId;
        private final PetEntity pet;
        private final CatchSphere sphere;
        private final PetInstance instance;
        private final CatchTimingSession session;

        private Phase phase = Phase.WINDOW;
        private int phaseTick;
        private double baseY = Double.NaN;
        private float baseYaw;

        private boolean caught;
        private boolean firstSphere;
        private double finalChance;
        private int shakesPlanned;
        private int shakesDone;

        private Ritual(Player player, PetEntity pet, CatchSphere sphere) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.pet = pet;
            this.sphere = sphere;
            this.instance = pet.getPetInstance();
            this.session = new CatchTimingSession(
                    player,
                    pet,
                    getCatchChance(player, sphere, instance.getRarity())
            );
        }

        /** The sphere lands: one crystalline hit, one line, the bar appears. */
        private void announceHit() {
            Location at = pet.getEntity().getLocation().add(0.0, 0.55, 0.0);
            World world = at.getWorld();
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 1.3f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            world.spawnParticle(Particle.DUST, at, 10, 0.2, 0.2, 0.2, 0.0,
                    new Particle.DustOptions(rarityDust(instance.getRarity()), 0.9f));

            String color = getRarityColor(instance.getRarity());
            String name = color + instance.getDefinition().getDisplayName();
            if (pet.isGuaranteedCatch()) {
                player.sendMessage("§5✦ §fSphere hit §8· " + name + " §8· §5this one is yours");
            } else {
                player.sendMessage("§d✦ §fSphere hit §8· " + name
                        + " §8· " + color + formatRarity(instance.getRarity())
                        + " §8· §d" + formatChance(session.baseChance)
                        + " §8· §7click in the §agreen");
            }
        }

        @Override
        public void run() {

            if (rituals.get(playerId) != this) {
                cancel();
                return;
            }

            if (!player.isOnline()) {
                rituals.remove(playerId, this);
                abort();
                return;
            }

            if (!pet.isSpawned() || pet.getEntity().isDead()) {
                rituals.remove(playerId, this);
                session.close();
                cancel();
                return;
            }

            if (Double.isNaN(baseY)) {
                baseY = pet.getEntity().getLocation().getY();
            }

            switch (phase) {
                case WINDOW -> tickWindow();
                case SETTLE -> tickSettle();
                case SHAKE -> tickShake();
            }
        }

        private void tickWindow() {
            session.pulse();

            if (session.justEnteredZone()) {
                // The green is heard, not only seen.
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 1.75f);
            }

            double progress = session.ticks / (double) CatchTimingSession.DURATION_TICKS;
            double struggle = Math.sin(progress * Math.PI * 5.0) * (0.10 + progress * 0.06);

            Location location = pet.getEntity().getLocation();
            location.setY(baseY + struggle);
            location.setYaw(location.getYaw() + 6.0f + (float) (progress * 8.0));
            pet.getEntity().teleport(location);

            if (session.ticks % 6 == 0) {
                location.getWorld().spawnParticle(
                        Particle.END_ROD,
                        location.clone().add(0, 0.55, 0),
                        1, 0.15, 0.15, 0.15, 0.005
                );
            }

            if (session.done()) {
                session.close();
                resolve();
                Location still = pet.getEntity().getLocation();
                still.setY(baseY);
                pet.getEntity().teleport(still);
                baseYaw = still.getYaw();
                phase = Phase.SETTLE;
                phaseTick = 0;
            }
        }

        /** Held breath: nothing moves, nothing sounds. */
        private void tickSettle() {
            phaseTick++;
            if (phaseTick >= SETTLE_TICKS) {
                phase = Phase.SHAKE;
                phaseTick = 0;
            }
        }

        private void tickShake() {
            int beat = phaseTick % SHAKE_GAP;

            if (beat == 0) {
                if (shakesDone >= shakesPlanned) {
                    rituals.remove(playerId, this);
                    cancel();
                    if (caught) {
                        finishCaught(this);
                    } else {
                        finishEscape(this);
                    }
                    return;
                }
                shake(shakesDone);
                shakesDone++;
            }

            if (beat < SHAKE_YAW.length) {
                Location location = pet.getEntity().getLocation();
                location.setYaw(baseYaw + SHAKE_YAW[beat]);
                location.setY(baseY - (beat == 0 ? 0.04 : 0.0));
                pet.getEntity().teleport(location);
            }

            phaseTick++;
        }

        private void shake(int index) {
            Location at = pet.getEntity().getLocation().add(0.0, 0.55, 0.0);
            at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 0.85f + index * 0.14f);
            at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.12, 0.12, 0.12, 0.0,
                    new Particle.DustOptions(rarityDust(instance.getRarity()), 0.8f));

            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < SHAKES_TO_CATCH; i++) {
                dots.append(i <= index ? "§d● " : "§8○ ");
            }
            player.sendActionBar(legacy(dots.toString().trim()));
        }

        /** Roll once, when the window closes — exactly as before. */
        private void resolve() {
            finalChance = Math.max(0.0d, Math.min(100.0d,
                    getCatchChance(player, sphere, instance.getRarity()) * session.multiplier()));
            firstSphere = consumeFirstSphereGuarantee(player);
            caught = firstSphere
                    || pet.isGuaranteedCatch()
                    || Math.random() * 100.0 < finalChance;
            // A failed catch still teases: one or two shakes before it breaks.
            shakesPlanned = caught
                    ? SHAKES_TO_CATCH
                    : 1 + ThreadLocalRandom.current().nextInt(2);
        }

        private void abort() {
            cancel();
            session.close();
            if (pet.isSpawned()) {
                pet.stopCatching();
            }
        }
    }

    /*
     * =========================================================
     * OUTCOMES
     * =========================================================
     */

    private void finishCaught(Ritual ritual) {

        Player player = ritual.player;
        PetEntity pet = ritual.pet;
        PetInstance petInstance = ritual.instance;
        boolean perfect = ritual.session.multiplier() > 1.0d;

        Location at = pet.getEntity().getLocation().add(0.0, 0.55, 0.0);
        World world = at.getWorld();

        // The click: sphere seals, then a warm lift for the owner.
        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.2f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 1.45f);

        sealRing(at, petInstance);
        pet.vanish(6);

        String color = getRarityColor(petInstance.getRarity());
        String petName = petInstance.getDefinition().getDisplayName();
        boolean shiny = petInstance.getVariant() == PetVariant.SHINY;

        player.showTitle(Title.title(
                legacy("§a✦ Caught"),
                legacy(color + petName + " §8· §7Lv. " + petInstance.getLevel()
                        + (shiny ? " §8· §e✨ Shiny" : "")),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(600))
        ));

        StringBuilder header = new StringBuilder("§a✦ Caught ")
                .append(color).append("§l").append(petName).append("§r ")
                .append(color).append(formatRarity(petInstance.getRarity()));
        if (shiny) {
            header.append(" §e✨ Shiny");
        } else if (petInstance.getVariant() != null && petInstance.getVariant() != PetVariant.NORMAL) {
            header.append(" §7").append(formatVariant(petInstance.getVariant()));
        }
        header.append(" §8· §7Lv. ").append(petInstance.getLevel());
        if (perfect && !ritual.firstSphere && !pet.isGuaranteedCatch()) {
            header.append(" §8· §a✦ perfect");
        }
        player.sendMessage(header.toString());
        player.sendMessage(statLine(petInstance));

        String petId = petInstance.getDefinition() == null
                ? ""
                : petInstance.getDefinition().getId();
        PlayerPetCollection collection = plugin.getPetCollection(player);
        collection.addPet(petInstance);
        boolean newEntry = collection.claimFirstCatchXp(petId);
        long xp = newEntry ? grantAetherionCatchXp(player) : 0L;
        grantGaffCatchXp(player, petInstance.getRarity());

        if (newEntry) {
            player.sendMessage("§b✦ New Aetherlex entry"
                    + (xp > 0 ? " §8· §b+" + xp + " Aetherion XP" : ""));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.25f);
                }
            }, 10L);
        }

        if (collection.getSize() == 1) {
            player.sendMessage("§8  Open §d/pets §8to equip it.");
        }

        // Catches were only saved on quit before — a crash lost them.
        if (plugin.getPetDataManager() != null) {
            plugin.getPetDataManager().save(collection);
        }

        Bukkit.getPluginManager().callEvent(new PetCaughtEvent(player, petId));
    }

    private void finishEscape(Ritual ritual) {

        Player player = ritual.player;
        PetEntity pet = ritual.pet;
        PetInstance petInstance = ritual.instance;

        Location at = pet.getEntity().getLocation().add(0.0, 0.55, 0.0);
        World world = at.getWorld();

        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.15f);
        world.spawnParticle(Particle.DUST, at, 8, 0.25, 0.25, 0.25, 0.0,
                new Particle.DustOptions(rarityDust(petInstance.getRarity()), 1.0f));
        world.spawnParticle(Particle.POOF, at, 3, 0.15, 0.15, 0.15, 0.02);

        pet.registerCatchAttempt();
        pet.stopCatching();

        boolean flees = pet.getCatchAttempts() >= FLEE_AFTER_ATTEMPTS
                && Math.random() * 100.0 < FLEE_CHANCE;

        String name = getRarityColor(petInstance.getRarity()) + petInstance.getDefinition().getDisplayName();
        player.sendActionBar(legacy("§c✦ Broke free"));

        if (flees) {
            player.sendMessage("§c✦ " + name + " §7broke free §8(" + formatChance(ritual.finalChance) + ")");
            startFleeAnimation(player, pet);
            return;
        }

        String warning = pet.getCatchAttempts() >= FLEE_AFTER_ATTEMPTS - 1
                ? " §8· §7it looks restless"
                : "";
        player.sendMessage("§c✦ " + name + " §7broke free §8(" + formatChance(ritual.finalChance) + ")" + warning);
    }

    /** One ring in the pet's rarity colour, a few motes rising — then it's gone. */
    private void sealRing(Location at, PetInstance petInstance) {
        World world = at.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(rarityDust(petInstance.getRarity()), 1.0f);
        int points = 14;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            world.spawnParticle(
                    Particle.DUST,
                    at.clone().add(Math.cos(angle) * 0.6, 0.0, Math.sin(angle) * 0.6),
                    1, 0.0, 0.0, 0.0, 0.0, dust
            );
        }
        world.spawnParticle(Particle.END_ROD, at, 5, 0.12, 0.2, 0.12, 0.02);
    }

    private void startFleeAnimation(
            Player player,
            PetEntity pet
    ) {

        if (!pet.isSpawned()) {
            return;
        }

        pet.startCatching();

        Vector away = pet.getEntity()
                .getLocation()
                .toVector()
                .subtract(player.getLocation().toVector());
        away.setY(0);

        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }

        Vector fleeDirection = away.normalize().multiply(0.45);
        fleeDirection.setY(0.12);

        player.playSound(pet.getEntity().getLocation(), Sound.ENTITY_FOX_AMBIENT, 1.0f, 1.6f);

        new BukkitRunnable() {

            private int ticks = 0;

            @Override
            public void run() {

                if (!pet.isSpawned() || pet.getEntity().isDead()) {
                    cancel();
                    return;
                }

                Location location = pet.getEntity().getLocation().add(fleeDirection);
                pet.getEntity().teleport(location);

                if (ticks % 2 == 0) {
                    pet.getEntity().getWorld().spawnParticle(
                            Particle.CLOUD,
                            location.clone().add(0, 0.3, 0),
                            1, 0.15, 0.1, 0.15, 0.01
                    );
                }

                ticks++;

                if (ticks < FLEE_ANIMATION_TICKS) {
                    return;
                }

                cancel();

                Location last = pet.getEntity().getLocation().add(0, 0.4, 0);
                last.getWorld().spawnParticle(Particle.POOF, last, 6, 0.25, 0.25, 0.25, 0.03);
                last.getWorld().playSound(last, Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.4f);

                String petName = pet.getPetInstance().getDefinition().getDisplayName();
                player.sendMessage("§e✦ " + getRarityColor(pet.getPetInstance().getRarity())
                        + petName + " §7ran away.");

                pet.vanish(8);
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    /*
     * =========================================================
     * ODDS + REWARDS (unchanged)
     * =========================================================
     */

    private double getCatchChance(Player player, CatchSphere sphere, Rarity petRarity) {
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
    private long grantAetherionCatchXp(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getSkills() == null) {
            return 0L;
        }
        long amount = 90L;
        items.getSkills().addBonusXp(player, amount);
        return amount;
    }

    /** Off-hand Catcher Gaff XP — rarer pets feed the tool harder. */
    private void grantGaffCatchXp(Player player, Rarity rarity) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.getItemManager() == null) {
            return;
        }
        int amount = de.aetherion.items.item.CatcherGaffProgress.xpForCatch(rarity);
        de.aetherion.items.item.CatcherGaffProgress.grantEquipped(player, items.getItemManager(), amount);
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

    /*
     * =========================================================
     * FORMAT
     * =========================================================
     */

    private String statLine(PetInstance pet) {
        StringBuilder line = new StringBuilder("§8  ▸ ");
        if (pet.getDefinition() != null && pet.getDefinition().isPercentBonus()) {
            line.append("§7Dungeon aura §f+")
                    .append(formatValue(pet.getStats().getScaledCoreValue(pet.getLevel())))
                    .append('%');
        } else {
            line.append("§7").append(formatCapability(pet.getStats().getCoreStat()))
                    .append(" §f").append(formatValue(pet.getStats().getCoreValue()));
        }
        List<String> bonus = new ArrayList<>();
        for (var entry : pet.getStats().getBonusStats().entrySet()) {
            bonus.add("§7" + formatCapability(entry.getKey()) + " §f" + formatValue(entry.getValue()));
        }
        if (!bonus.isEmpty()) {
            line.append(" §8· ").append(String.join(" §8· ", bonus));
        }
        return line.toString();
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static Color rarityDust(Rarity rarity) {
        if (rarity == null) {
            return Color.fromRGB(235, 235, 235);
        }
        return switch (rarity) {
            case COMMON -> Color.fromRGB(235, 235, 235);
            case UNCOMMON -> Color.fromRGB(96, 230, 96);
            case RARE -> Color.fromRGB(96, 140, 255);
            case EPIC -> Color.fromRGB(176, 84, 232);
            case LEGENDARY -> Color.fromRGB(255, 176, 32);
            case MYTHIC -> Color.fromRGB(255, 110, 230);
            case AETHERED -> Color.fromRGB(176, 20, 40);
        };
    }

    private String formatRarity(Object rarity) {
        String text = rarity.toString().toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String formatVariant(PetVariant variant) {
        String text = variant.toString().toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String formatCapability(ItemCapability capability) {
        String text = capability.toString().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(' ');
        }
        return result.toString().trim();
    }

    private String formatValue(double value) {
        return String.format("%.2f", value);
    }

    private String formatChance(double chance) {
        if (chance == Math.floor(chance)) {
            return String.format("%.0f%%", chance);
        }
        return String.format("%.2f%%", chance);
    }

    private String getRarityColor(Object rarity) {
        return switch (rarity.toString()) {
            case "COMMON" -> ChatColor.WHITE.toString();
            case "UNCOMMON" -> ChatColor.GREEN.toString();
            case "RARE" -> ChatColor.BLUE.toString();
            case "EPIC" -> ChatColor.DARK_PURPLE.toString();
            case "LEGENDARY" -> ChatColor.GOLD.toString();
            case "MYTHIC" -> ChatColor.LIGHT_PURPLE.toString();
            case "AETHERED" -> ChatColor.DARK_RED.toString();
            default -> ChatColor.WHITE.toString();
        };
    }
}
