package de.aetherion.items.world;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BarChrome;
import de.aetherion.core.api.BossSpawnAccess;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colosseum spill / Pathwarden spawn.
 * First time: Proctor walk (Quests). After that: right-click the center pad with a Crypt vial.
 */
public final class ColosseumEscortService implements Listener {

    private static final int RITUAL_SECONDS = 10;
    private static final double PAD_CLICK_RADIUS_SQ = 2.6 * 2.6;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> ritualBusyUntil = new ConcurrentHashMap<>();

    private static volatile ColosseumEscortService instance;

    public ColosseumEscortService(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static ColosseumEscortService get() {
        return instance;
    }

    /**
     * Proctor is on the pad (first demo): pause → splash → summon FX → 10s → boss.
     * Marks the player as taught so later vials use the pad directly.
     */
    public void spillAtPad(Player player, String bossId) {
        if (player == null) {
            return;
        }
        World world = Bukkit.getWorld(ColosseumArena.WORLD);
        if (world == null) {
            player.sendMessage("§cColosseum world offline.");
            return;
        }
        String id = bossId == null || bossId.isBlank() ? "pathwarden" : bossId;
        beginSpillSequence(player, id, true);
    }

    /**
     * Self-summon at the marked pad (after Proctor taught once).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPadUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isPadBlock(block)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!BorderlandsRiteService.isCryptSpirit(hand)) {
            return;
        }
        event.setCancelled(true);

        ColosseumGateService gate = ColosseumGateService.get();
        if (gate == null || !gate.isTaught(player)) {
            player.sendMessage("§6Colosseum §8» §7Let the §6Proctor §7study a Crypt vial with you once first.");
            return;
        }
        Long busy = ritualBusyUntil.get(player.getUniqueId());
        if (busy != null && busy > System.currentTimeMillis()) {
            player.sendMessage("§cRitual already running.");
            return;
        }
        String bossId = BorderlandsRiteService.spiritBossId(hand);
        if (bossId == null || bossId.isBlank()) {
            bossId = "pathwarden";
        }
        hand.setAmount(hand.getAmount() - 1);
        beginSpillSequence(player, bossId, false);
    }

    /**
     * Fallback if an old Quests jar still calls this.
     */
    public boolean startEscort(Player player, ItemStack vial) {
        if (player == null || vial == null || !BorderlandsRiteService.isCryptSpirit(vial)) {
            return false;
        }
        String bossId = BorderlandsRiteService.spiritBossId(vial);
        if (bossId == null || bossId.isBlank()) {
            bossId = "pathwarden";
        }
        vial.setAmount(vial.getAmount() - 1);
        ColosseumGateService gate = ColosseumGateService.get();
        if (gate != null) {
            gate.unlock(player);
        }
        spillAtPad(player, bossId);
        return true;
    }

    private void beginSpillSequence(Player player, String bossId, boolean fromProctorDemo) {
        World world = Bukkit.getWorld(ColosseumArena.WORLD);
        if (world == null) {
            return;
        }
        Location pad = ColosseumArena.pad(world);
        Location spawn = ColosseumArena.bossSpawn(world);
        clearTrash(spawn, ColosseumArena.RADIUS);
        ritualBusyUntil.put(
                player.getUniqueId(),
                System.currentTimeMillis() + (RITUAL_SECONDS + 8) * 1000L
        );

        if (fromProctorDemo) {
            player.sendMessage("§6Proctor §8» §fRight on the mark. Hold still…");
        } else {
            player.sendMessage("§6Colosseum §8» §fVial seats on the mark.");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (fromProctorDemo) {
                player.sendMessage("§6Proctor §8» §fOh. §cOh no.");
            }
            playSpill(pad);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                startSummonCountdown(player, bossId, spawn, fromProctorDemo);
            }, 22L);
        }, fromProctorDemo ? 18L : 8L);
    }

    private static boolean isPadBlock(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        if (!ColosseumArena.WORLD.equalsIgnoreCase(block.getWorld().getName())) {
            return false;
        }
        double dx = block.getX() + 0.5 - ColosseumArena.PAD_X;
        double dz = block.getZ() + 0.5 - ColosseumArena.PAD_Z;
        if (dx * dx + dz * dz > PAD_CLICK_RADIUS_SQ) {
            return false;
        }
        return Math.abs(block.getY() - ColosseumArena.PAD_Y) <= 2
                || Math.abs(block.getY() - ColosseumArena.SPAWN_Y) <= 2;
    }

    private void playSpill(Location pad) {
        World world = pad.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(pad, Sound.ENTITY_SPLASH_POTION_THROW, 1.0f, 0.95f);
        world.playSound(pad, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.85f);
        world.playSound(pad, Sound.BLOCK_GLASS_BREAK, 0.95f, 0.7f);
        world.playSound(pad, Sound.ITEM_BOTTLE_EMPTY, 0.8f, 0.9f);

        de.aetherion.core.api.Vfx.puff(pad.clone().add(0, 1.0, 0), Particle.SPLASH, 40, Math.max(0.35, 0.35), null);
        de.aetherion.core.api.Vfx.puff(pad.clone().add(0, 1.1, 0), Particle.EFFECT, 28, Math.max(0.4, 0.4), null);
        de.aetherion.core.api.Vfx.puff(pad.clone().add(0, 0.9, 0), Particle.CRIT, 18, Math.max(0.3, 0.3), null);
        de.aetherion.core.api.Vfx.puff(pad.clone().add(0, 0.8, 0), Particle.SOUL, 16, Math.max(0.25, 0.25), null);
    }

    private void startSummonCountdown(Player player, String bossId, Location spawnAt, boolean fromProctorDemo) {
        World world = spawnAt.getWorld();
        clearTrash(spawnAt, ColosseumArena.RADIUS);
        igniteSummon(player, spawnAt);

        String display = displayName(bossId);
        player.sendMessage("§6Colosseum §8» §fCalling §6" + display + "§f… stand ready.");

        BossBar bar = BossBar.bossBar(
                BarChrome.component(BarChrome.Family.BOSS, BarChrome.Icon.SIGIL,
                        Component.text("Summoning " + display, NamedTextColor.GOLD, TextDecoration.BOLD)),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);

        final int[] left = {RITUAL_SECONDS};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                player.hideBossBar(bar);
                task.cancel();
                return;
            }
            if (left[0] <= 0) {
                player.hideBossBar(bar);
                task.cancel();
                spawnBoss(player, bossId, spawnAt, fromProctorDemo);
                return;
            }
            float progress = left[0] / (float) RITUAL_SECONDS;
            bar.progress(Math.max(0f, Math.min(1f, progress)));
            bar.name(Component.text(
                    "Summoning " + display + " · " + left[0] + "s",
                    NamedTextColor.GOLD,
                    TextDecoration.BOLD
            ));
            if (world != null) {
                de.aetherion.core.api.Vfx.puff(spawnAt, Particle.SOUL_FIRE_FLAME, 10, Math.max(0.45, 0.45), null);
                de.aetherion.core.api.Vfx.puff(spawnAt.clone().add(0, 0.4, 0), Particle.SMOKE, 8, Math.max(0.35, 0.35), null);
                if (left[0] % 2 == 0) {
                    world.playSound(spawnAt, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.35f, 0.75f);
                }
            }
            left[0]--;
        }, 0L, 20L);
    }

    private void igniteSummon(Player player, Location spawnAt) {
        World world = spawnAt.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(spawnAt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.28f, 1.35f);
        world.playSound(spawnAt, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.22f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 0.55f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 0.8f);

        de.aetherion.core.api.Vfx.puff(spawnAt.clone().add(0, 1.2, 0), Particle.FLASH, 1, 0.3, null);
        de.aetherion.core.api.Vfx.puff(spawnAt.clone().add(0, 0.6, 0), Particle.EXPLOSION, 1, 0.3, null);
        de.aetherion.core.api.Vfx.puff(spawnAt, Particle.SOUL_FIRE_FLAME, 55, Math.max(0.7, 0.7), null);
        de.aetherion.core.api.Vfx.puff(spawnAt.clone().add(0, 1, 0), Particle.SOUL, 35, Math.max(0.5, 0.5), null);
        de.aetherion.core.api.Vfx.puff(spawnAt, Particle.LAVA, 12, Math.max(0.4, 0.4), null);
        for (int y = 0; y < 18; y++) {
            Location column = spawnAt.clone().add(0, y * 0.55, 0);
            de.aetherion.core.api.Vfx.puff(column, Particle.END_ROD, 2, Math.max(0.05, 0.05), null);
            de.aetherion.core.api.Vfx.puff(column, Particle.ELECTRIC_SPARK, 3, Math.max(0.12, 0.12), null);
        }
        player.spawnParticle(Particle.FIREWORK, spawnAt.clone().add(0, 1.5, 0), 25, 0.3, 0.6, 0.3, 0.05);
    }

    private void spawnBoss(Player player, String bossId, Location at, boolean fromProctorDemo) {
        BossSpawnAccess bosses = AetherServices.bosses();
        if (bosses == null) {
            player.sendMessage("§cBossEngine offline — the spill fizzles.");
            return;
        }
        clearTrash(at, ColosseumArena.RADIUS);
        boolean ok = bosses.spawn(bossId, at, player);
        if (!ok) {
            ok = bosses.spawnSandbox(bossId, at, player);
        }
        World world = at.getWorld();
        if (ok) {
            player.sendMessage("§6Colosseum §8» §f§6" + displayName(bossId) + " §frises in the ring.");
            if (world != null) {
                world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.32f, 1.25f);
                world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.15f);
                de.aetherion.core.api.Vfx.puff(at.clone().add(0, 1, 0), Particle.EXPLOSION_EMITTER, 1, 0.3, null);
                de.aetherion.core.api.Vfx.puff(at, Particle.SOUL_FIRE_FLAME, 60, Math.max(0.8, 0.8), null);
                de.aetherion.core.api.Vfx.puff(at.clone().add(0, 1.5, 0), Particle.FLASH, 1, 0.3, null);
            }
            ColosseumGateService gate = ColosseumGateService.get();
            if (gate != null) {
                gate.markTaught(player);
            }
            if (fromProctorDemo) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("§6Proctor §8» §fYou saw it. Next Crypt vial: §eright-click the glowing mark §fin the ring yourself. I only walk once.");
                    }
                }, 40L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> latchBossClear(at), 10L);
        } else {
            player.sendMessage("§cRite failed — boss template missing or too many active.");
        }
    }

    private void latchBossClear(Location spawn) {
        UUID bossId = null;
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        double best = 64;
        for (Entity entity : world.getNearbyEntities(spawn, 8, 8, 8)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            if (de.aetherion.core.AetherEntities.isBoss(living)
                    || de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                double d = living.getLocation().distanceSquared(spawn);
                if (d < best) {
                    best = d;
                    bossId = living.getUniqueId();
                }
            }
        }
        if (bossId == null) {
            return;
        }
        UUID watch = bossId;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            Entity boss = Bukkit.getEntity(watch);
            if (boss == null || !boss.isValid() || boss.isDead()) {
                task.cancel();
                return;
            }
            clearTrash(spawn, 20);
        }, 20L, 20L);
    }

    private void clearTrash(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double r2 = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster living)) {
                continue;
            }
            if (de.aetherion.core.AetherEntities.isBoss(living)
                    || de.aetherion.core.AetherEntities.isSystemOwned(living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(center) <= r2) {
                living.remove();
            }
        }
    }

    private static String displayName(String bossId) {
        if ("pathwarden".equalsIgnoreCase(bossId)) {
            return "Pathwarden";
        }
        if ("squidward".equalsIgnoreCase(bossId)) {
            return "Squidward";
        }
        return bossId;
    }
}
