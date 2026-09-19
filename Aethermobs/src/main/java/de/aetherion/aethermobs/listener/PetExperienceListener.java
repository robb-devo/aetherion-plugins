package de.aetherion.aethermobs.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.ActivePetManager;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PlayerPetCollection;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetExperienceListener implements Listener {

    private static final double SHARE = 0.35d;
    private static final NamespacedKey PET_KEY = AetherKeys.PET_ENTITY;
    private static final NamespacedKey MINION_KEY = AetherKeys.SET_MINION;
    private static final NamespacedKey BOSS_KEY = AetherKeys.BOSS_ID;
    private static final NamespacedKey DUNGEON_MOB = AetherKeys.DUNGEON_MOB;

    private final AetherMobs plugin;
    private final ActivePetManager activePetManager;
    private final Map<UUID, Long> lastShareTick = new HashMap<>();
    private final Map<UUID, Integer> lastShareAmount = new HashMap<>();

    public PetExperienceListener(AetherMobs plugin) {
        this.plugin = plugin;
        this.activePetManager = plugin.getActivePetManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExperience(PlayerExpChangeEvent event) {
        shareExperience(event.getPlayer(), event.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!countsAsCombat(entity)) {
            return;
        }
        Player killer = resolveKiller(entity);
        if (killer == null) {
            return;
        }
        grantCombatExperience(killer, combatExperience(entity));
    }

    public void shareExperience(Player player, int experience) {
        if (player == null || experience <= 0) {
            return;
        }

        long tick = plugin.getServer().getCurrentTick();
        UUID playerId = player.getUniqueId();

        if (lastShareTick.getOrDefault(playerId, -1L) == tick
                && lastShareAmount.getOrDefault(playerId, 0) == experience) {
            return;
        }

        lastShareTick.put(playerId, tick);
        lastShareAmount.put(playerId, experience);

        applyExperience(player, Math.max(1, (int) Math.round(experience * SHARE)));
    }

    private void grantCombatExperience(Player player, int experience) {
        applyExperience(player, experience);
    }

    public void shareGatherExperience(Player player, int experience) {
        if (player == null || experience <= 0) {
            return;
        }
        applyExperience(player, Math.max(1, Math.min(12, (experience + 1) / 2)));
    }

    private void applyExperience(Player player, int granted) {
        if (player == null || granted <= 0) {
            return;
        }

        PetInstance pet = equippedPet(player);
        if (pet == null) {
            return;
        }

        int previousLevel = pet.getLevel();
        pet.addExperience(granted);
        int currentLevel = pet.getLevel();

        PlayerPetCollection collection = plugin.getPetCollection(player);
        if (currentLevel > previousLevel) {
            PetEntity active = activePetManager.getActivePet(player);
            if (active != null) {
                active.updateDisplayName();
            }
            if (collection != null) {
                plugin.getPetDataManager().save(collection);
            }
            player.sendMessage(
                    "§d✦ §f"
                            + pet.getDefinition().getDisplayName()
                            + " §7reached §eLevel "
                            + currentLevel
                            + "§7!"
            );
            return;
        }

        plugin.markPetsDirty(player.getUniqueId());
    }

    private PetInstance equippedPet(Player player) {
        PetEntity active = activePetManager.getActivePet(player);
        if (active != null && active.getPetInstance() != null) {
            return active.getPetInstance();
        }
        PlayerPetCollection collection = plugin.getPetCollection(player);
        return collection == null ? null : collection.getEquippedPet();
    }

    private static boolean countsAsCombat(LivingEntity entity) {
        if (entity == null || entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        if (entity.hasMetadata("NPC")) {
            return false;
        }
        var data = entity.getPersistentDataContainer();
        return !data.has(PET_KEY, PersistentDataType.BYTE)
                && !data.has(MINION_KEY, PersistentDataType.BYTE);
    }

    private static int combatExperience(LivingEntity entity) {
        int xp = 6 + (int) Math.min(24.0, Math.max(0.0, entity.getMaxHealth()) / 50.0);
        String dungeonId = entity.getPersistentDataContainer().get(DUNGEON_MOB, PersistentDataType.STRING);
        if (dungeonId != null) {
            xp += 6;
            if ("dungeon_sentinel".equals(dungeonId)
                    || "dungeon_frostbound".equals(dungeonId)
                    || "dungeon_aetherion".equals(dungeonId)
                    || "dungeon_warden".equals(dungeonId)) {
                xp += 18;
            }
        }
        if (entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.STRING)) {
            xp += 18;
        }
        return xp;
    }

    private static Player resolveKiller(LivingEntity entity) {
        Player killer = entity.getKiller();
        if (killer != null) {
            return killer;
        }
        EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause != null) {
            Entity causing = cause.getDamageSource().getCausingEntity();
            if (causing instanceof Player player) {
                return player;
            }
        }
        if (!(cause instanceof EntityDamageByEntityEvent damage)) {
            return null;
        }
        Entity damager = damage.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
