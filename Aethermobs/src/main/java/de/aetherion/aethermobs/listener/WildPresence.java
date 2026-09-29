package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PlayerPetCollection;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How wild pets are noticed before they are caught.
 *
 * <ul>
 *     <li><b>Sighting</b> — actually seeing a species up close fills its Aetherlex page
 *     (the menu always promised "find it in the world"; only a sphere hit counted).</li>
 *     <li><b>Rare stir</b> — an Epic+ or shiny pet in the area is heard first: one
 *     positional chime from where it stands, so the player turns and looks.</li>
 * </ul>
 * One cue per player per second at most; no particles.
 */
public final class WildPresence implements Listener, Runnable {

    private static final double SIGHT_RADIUS_SQ = 14.0 * 14.0;
    private static final double STIR_RADIUS_SQ = 30.0 * 30.0;
    private static final long STIR_COOLDOWN_MS = 90_000L;
    private static final int STIR_MEMORY = 48;

    private final AetherMobs plugin;
    private final Map<UUID, Set<UUID>> stirred = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStir = new ConcurrentHashMap<>();
    private BukkitTask task;

    public WildPresence(AetherMobs plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 50L, 20L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        if (plugin.getPetSpawnManager() == null) {
            return;
        }
        List<PetEntity> wild = new ArrayList<>();
        for (PetEntity pet : plugin.getPetSpawnManager().getActivePets()) {
            if (pet != null
                    && pet.isSpawned()
                    && pet.isWild()
                    && !pet.isCatching()
                    && pet.getPetInstance() != null
                    && pet.getPetInstance().getDefinition() != null) {
                wild.add(pet);
            }
        }
        if (wild.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            notice(player, wild);
        }
    }

    private void notice(Player player, List<PetEntity> wild) {
        PlayerPetCollection collection = plugin.getPetCollection(player);
        Location eye = player.getEyeLocation();
        PetEntity stirCandidate = null;

        for (PetEntity pet : wild) {
            Location at = pet.getEntity().getLocation();
            if (at.getWorld() != eye.getWorld()) {
                continue;
            }
            double d2 = at.distanceSquared(eye);
            PetDefinition definition = pet.getPetInstance().getDefinition();

            if (d2 <= SIGHT_RADIUS_SQ
                    && collection != null
                    && !collection.hasRevealed(definition.getId())
                    && player.hasLineOfSight(pet.getEntity())) {
                sight(player, collection, definition);
                return;
            }

            if (stirCandidate == null
                    && d2 <= STIR_RADIUS_SQ
                    && d2 > SIGHT_RADIUS_SQ
                    && isRare(pet.getPetInstance())
                    && !alreadyStirred(player, pet)) {
                stirCandidate = pet;
            }
        }

        if (stirCandidate != null) {
            stir(player, stirCandidate);
        }
    }

    private void sight(Player player, PlayerPetCollection collection, PetDefinition definition) {
        if (!collection.markSighted(definition.getId())) {
            return;
        }
        plugin.markPetsDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.6f);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§e✦ New sighting §8· §f" + definition.getDisplayName() + " §8· §7Aetherlex updated"
        ));
    }

    /** A chime from the pet's own position — the direction is the hint. */
    private void stir(Player player, PetEntity pet) {
        long now = System.currentTimeMillis();
        Long last = lastStir.get(player.getUniqueId());
        if (last != null && now - last < STIR_COOLDOWN_MS) {
            return;
        }
        lastStir.put(player.getUniqueId(), now);

        Set<UUID> seen = stirred.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashSet<>());
        seen.add(pet.getEntity().getUniqueId());
        if (seen.size() > STIR_MEMORY) {
            seen.remove(seen.iterator().next());
        }

        PetInstance instance = pet.getPetInstance();
        Location at = pet.getEntity().getLocation();
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.25f);

        boolean shiny = instance.getVariant() == PetVariant.SHINY;
        if (shiny || instance.getRarity().ordinal() >= Rarity.LEGENDARY.ordinal()) {
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.9f);
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    shiny ? "§e✨ §7Something glimmers nearby…" : "§6✦ §7Something rare stirs nearby…"
            ));
        }
    }

    private boolean alreadyStirred(Player player, PetEntity pet) {
        Set<UUID> seen = stirred.get(player.getUniqueId());
        return seen != null && seen.contains(pet.getEntity().getUniqueId());
    }

    private static boolean isRare(PetInstance instance) {
        return instance.getVariant() == PetVariant.SHINY
                || instance.getRarity() != null
                && instance.getRarity().ordinal() >= Rarity.EPIC.ordinal();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stirred.remove(event.getPlayer().getUniqueId());
        lastStir.remove(event.getPlayer().getUniqueId());
    }
}
