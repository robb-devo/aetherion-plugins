package de.aetherion.aethermobs.listener;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetSpawnType;
import de.aetherion.items.model.Rarity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fishing pet hooks: rare Water Dragon on normal rods,
 * Tide Latch can reel any aquatic (incl. deep-sea) pet.
 */
public final class FishingDragonListener implements Listener {

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("aetherion", "item");
    private static final String TIDE_LATCH = "tide_latch";
    /** ~12% chance to hook an aquatic pet with Tide Latch. */
    private static final double TIDE_AQUATIC_CHANCE = 0.12d;

    private final AetherMobs plugin;

    public FishingDragonListener(AetherMobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        String rodId = itemId(player.getInventory().getItemInMainHand());
        if (TIDE_LATCH.equalsIgnoreCase(rodId)) {
            tryTideAquatic(player);
            return;
        }
        double chance = dragonChanceFor(rodId);
        if (chance <= 0.0d || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        if (!plugin.getPetSpawnManager().spawnFishedPet(player, "water_dragon")) {
            return;
        }
        announceDragon(player);
    }

    private void tryTideAquatic(Player player) {
        if (ThreadLocalRandom.current().nextDouble() >= TIDE_AQUATIC_CHANCE) {
            return;
        }
        String petId = rollAquaticPetId();
        if (petId == null || !plugin.getPetSpawnManager().spawnFishedPet(player, petId)) {
            return;
        }
        PetDefinition def = plugin.getPetRegistry().get(petId);
        String name = def != null ? def.getDisplayName() : petId;
        boolean deep = def != null && def.isDeepAquatic();
        TextColor color = Rarity.RARE.textColor();
        player.sendMessage("§3✦ §bTide Latch §7reeled an aquatic pet: §f" + name
                + (deep ? " §8(deep sea)" : ""));
        player.showTitle(Title.title(
                Component.text(name, color),
                Component.text(deep ? "Deep-sea catch — yours to claim." : "Aquatic catch — yours to claim.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(60), Duration.ofMillis(2200), Duration.ofMillis(350))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.9f, 0.85f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.4f);
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1.1, 0), 22, 0.45, 0.35, 0.45, 0.06);
        if ("water_dragon".equals(petId)) {
            announceDragon(player);
        }
    }

    private String rollAquaticPetId() {
        if (plugin.getPetRegistry() == null) {
            return null;
        }
        List<PetDefinition> pool = new ArrayList<>();
        double total = 0.0d;
        for (PetDefinition def : plugin.getPetRegistry().getAll()) {
            if (def == null || def.getSpawnType() != PetSpawnType.AQUATIC) {
                continue;
            }
            double weight = Math.max(0.05d, def.getSpawnWeight());
            // Keep water dragon rare inside the Tide Latch pool.
            if ("water_dragon".equalsIgnoreCase(def.getId())) {
                weight *= 0.05d;
            }
            pool.add(def);
            total += weight;
        }
        if (pool.isEmpty() || total <= 0.0d) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cursor = 0.0d;
        for (PetDefinition def : pool) {
            double weight = Math.max(0.05d, def.getSpawnWeight());
            if ("water_dragon".equalsIgnoreCase(def.getId())) {
                weight *= 0.05d;
            }
            cursor += weight;
            if (roll <= cursor) {
                return def.getId();
            }
        }
        return pool.get(pool.size() - 1).getId();
    }

    private void announceDragon(Player player) {
        TextColor mythic = Rarity.MYTHIC.textColor();
        player.sendMessage("§5✦ §5The line went heavy. A §5Mythic Water Dragon§5.");
        player.sendMessage("§8Only you can catch it — and the catch is guaranteed.");
        player.showTitle(Title.title(
                Component.text("Mythic Water Dragon", mythic),
                Component.text("Yours alone. Any sphere works.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(2800), Duration.ofMillis(400))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.35f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.85f);
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1.2, 0), 28, 0.5, 0.4, 0.5, 0.08);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1.4, 0), 18, 0.45, 0.5, 0.45, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendActionBar(Component.text("Mythic Water Dragon — guaranteed catch.", mythic));
        }, 12L);
    }

    private static double dragonChanceFor(String id) {
        if (id == null) {
            return 0.0d;
        }
        return switch (id.toLowerCase()) {
            case "fishing_rod" -> 0.00002d;
            case "fishing_rod_2" -> 0.00015d;
            case "fishing_rod_3" -> 0.0008d;
            default -> 0.0d;
        };
    }

    private static String itemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
    }
}
