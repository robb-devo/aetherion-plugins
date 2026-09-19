package de.aetherion.items.codex;

import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

public final class CodexListener implements Listener {

    private final CodexService codex;

    public CodexListener(CodexService codex) {
        this.codex = codex;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity.getType().name().equals("ARMOR_STAND")) {
            return;
        }
        if (entity.hasMetadata("NPC")) {
            return;
        }
        if (isPet(entity)) {
            return;
        }

        Player killer = resolveKiller(entity);
        if (killer == null || !countsFor(killer)) {
            return;
        }

        String bossId = entity.getPersistentDataContainer().get(
                AetherKeys.BOSS_ID,
                PersistentDataType.STRING
        );
        if (bossId != null && !bossId.isBlank()) {
            return;
        }

        String dungeonId = dungeonMob(entity);
        if (dungeonId != null) {
            noteKill(killer, "dungeon:" + dungeonId);
            return;
        }

        noteKill(killer, entity.getType().name());
    }

    private void noteKill(Player killer, String id) {
        boolean first = !codex.hasAnyKill(killer);
        codex.addKill(killer, id);
        if (first && killer != null) {
            de.aetherion.items.progress.UnlockToast.show(
                    killer,
                    "Bestiary",
                    "The mobs are keeping score now"
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!countsFor(player)) {
            return;
        }
        if (player.getWorld() != null && player.getWorld().getName().startsWith("aedun_")) {
            return;
        }
        Material type = event.getBlock().getType();
        String id = CodexCatalog.resolveBlock(type);
        if (id != null) {
            boolean first = !codex.hasAnyBlock(player);
            codex.addBlock(player, id);
            if (first) {
                de.aetherion.items.progress.UnlockToast.show(
                        player,
                        "Collection",
                        "Every block files a report"
                );
            }
        }
    }

    private boolean countsFor(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private Player resolveKiller(LivingEntity entity) {
        Player killer = entity.getKiller();
        if (killer != null) {
            return killer;
        }
        org.bukkit.event.entity.EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause != null) {
            Entity causing = cause.getDamageSource().getCausingEntity();
            if (causing instanceof Player player) {
                return player;
            }
            Player owner = helperOwner(causing);
            if (owner != null) {
                return owner;
            }
        }
        if (!(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) {
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
        return helperOwner(damager);
    }

    private Player helperOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        String owner = entity.getPersistentDataContainer().get(
                AetherKeys.SET_MINION_OWNER,
                PersistentDataType.STRING
        );
        if (owner == null || owner.isBlank()) {
            return null;
        }
        try {
            return Bukkit.getPlayer(java.util.UUID.fromString(owner));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isPet(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(
                AetherKeys.PET_ENTITY,
                PersistentDataType.BYTE
        ) || entity.getPersistentDataContainer().has(
                AetherKeys.PET_ENTITY,
                PersistentDataType.STRING
        );
    }

    private String dungeonMob(LivingEntity entity) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (plugin == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(
                AetherKeys.DUNGEON_MOB,
                PersistentDataType.STRING
        );
    }
}
