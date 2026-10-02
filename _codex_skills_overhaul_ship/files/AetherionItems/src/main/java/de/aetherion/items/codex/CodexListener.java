package de.aetherion.items.codex;

import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds the Codex.
 *
 * <ul>
 *   <li>Kills → Bestiary: vanilla types (with Borderlands Sturdy/Brute/Crypt variants), dungeon
 *   mobs, fishing sea creatures (by encounter band), Ore Trolls. Boss kills come from
 *   {@link BossJournalListener} (everyone who dealt damage).</li>
 *   <li>Breaks → Collection: ripe crops only, and never a block a player placed this session.</li>
 *   <li>Landed catches → Collection (Catches shelf).</li>
 * </ul>
 */
public final class CodexListener implements Listener {

    /** How many recent player placements are remembered (oldest forgotten first). */
    private static final int PLACED_CAP = 200_000;

    private static final NamespacedKey ENCOUNTER_TIER = new NamespacedKey("aetherionfishing", "encounter_tier");
    private static final NamespacedKey ENCOUNTER_OWNER = new NamespacedKey("aetherionfishing", "encounter_owner");

    private final CodexService codex;
    private final Map<UUID, LinkedHashSet<Long>> placed = new ConcurrentHashMap<>();
    private int placedCount;

    public CodexListener(CodexService codex) {
        this.codex = codex;
    }

    // ------------------------------------------------------------------ Bestiary

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
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        Player killer = resolveKiller(entity);
        if (killer == null) {
            killer = encounterOwner(pdc);
        }
        if (killer == null || !countsFor(killer)) {
            return;
        }

        String bossId = pdc.get(AetherKeys.BOSS_ID, PersistentDataType.STRING);
        if (bossId != null && !bossId.isBlank()) {
            return;
        }

        if (pdc.has(AetherKeys.FISHING_ENCOUNTER, PersistentDataType.BYTE)) {
            Integer band = pdc.get(ENCOUNTER_TIER, PersistentDataType.INTEGER);
            String sea = band == null ? null : CodexCatalog.seaCreature(band);
            if (sea != null) {
                noteKill(killer, sea, null);
            }
            return;
        }

        if (isOreTroll(entity)) {
            noteKill(killer, "odd:ore_troll", null);
            return;
        }

        String dungeonId = dungeonMob(entity);
        if (dungeonId != null) {
            noteKill(killer, "dungeon:" + dungeonId, null);
            return;
        }

        noteKill(killer, entity.getType().name(), variant(entity));
    }

    private void noteKill(Player killer, String id, String variant) {
        boolean first = !codex.hasAnyKill(killer);
        codex.addKill(killer, id, variant);
        if (first && killer != null) {
            de.aetherion.items.progress.UnlockToast.show(
                    killer,
                    "Bestiary",
                    "The mobs are keeping score now"
            );
        }
    }

    private static String variant(LivingEntity entity) {
        try {
            return switch (de.aetherion.items.world.WildlifeLooks.tierOf(entity)) {
                case de.aetherion.items.world.WildlifeLooks.TIER_STURDY -> "sturdy";
                case de.aetherion.items.world.WildlifeLooks.TIER_BRUTE -> "brute";
                case de.aetherion.items.world.WildlifeLooks.TIER_CRYPT -> "crypt";
                default -> null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isOreTroll(LivingEntity entity) {
        try {
            return entity.getPersistentDataContainer().has(
                    de.aetherion.items.core.ItemKeys.key("ore_troll"), PersistentDataType.BYTE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Player encounterOwner(PersistentDataContainer pdc) {
        String owner = pdc.get(ENCOUNTER_OWNER, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(owner));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Collection

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        String id = CodexCatalog.resolveBlock(block.getType());
        if (id == null) {
            return;
        }
        // Planted crops are gated by ripeness instead — a grown crop must still count.
        if (block.getBlockData() instanceof Ageable
                && CodexCatalog.BLOCK_CROPS.equals(CodexCatalog.categoryOf(id))
                && !"sugar_cane".equals(id) && !"cactus".equals(id)) {
            return;
        }
        remember(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (forget(block)) {
            return;
        }
        if (!countsFor(player)) {
            return;
        }
        if (player.getWorld() != null && player.getWorld().getName().startsWith("aedun_")) {
            return;
        }
        String id = CodexCatalog.resolveHarvest(block);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }
        Player player = event.getPlayer();
        if (!countsFor(player)) {
            return;
        }
        String id = CodexCatalog.resolveCatch(item.getItemStack().getType());
        if (id == null) {
            return;
        }
        boolean first = !codex.hasAnyBlock(player);
        codex.addBlock(player, id);
        if (first) {
            de.aetherion.items.progress.UnlockToast.show(player, "Collection", "Even the fish get filed");
        }
    }

    private void remember(Block block) {
        LinkedHashSet<Long> set = placed.computeIfAbsent(block.getWorld().getUID(), ignored -> new LinkedHashSet<>());
        if (set.add(block.getBlockKey())) {
            placedCount++;
        }
        if (placedCount > PLACED_CAP) {
            // Trim the oldest from the busiest world.
            LinkedHashSet<Long> biggest = set;
            for (LinkedHashSet<Long> candidate : placed.values()) {
                if (candidate.size() > biggest.size()) {
                    biggest = candidate;
                }
            }
            Iterator<Long> oldest = biggest.iterator();
            for (int i = 0; i < 1_000 && oldest.hasNext(); i++) {
                oldest.next();
                oldest.remove();
                placedCount--;
            }
        }
    }

    /** True (and forgotten) when the block was placed by a player since the last restart. */
    private boolean forget(Block block) {
        LinkedHashSet<Long> set = placed.get(block.getWorld().getUID());
        if (set != null && set.remove(block.getBlockKey())) {
            placedCount--;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

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
