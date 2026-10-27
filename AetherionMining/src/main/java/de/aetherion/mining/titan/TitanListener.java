package de.aetherion.mining.titan;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;

/**
 * House rules inside Ossarath: mine anything but the heart, place only torches, ladders and scaffolding, wake up in
 * Jawcamp, and ride the breath columns (updrafts) up into the lungs.
 */
public final class TitanListener implements Listener {

    private static final Set<Material> PLACEABLE = EnumSet.of(Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH,
            Material.SOUL_WALL_TORCH, Material.LADDER, Material.SCAFFOLDING, Material.LANTERN, Material.SOUL_LANTERN);

    private final AetherionMining plugin;
    private final TitanWorld titan;
    private final Jawcamp camp;
    private final java.util.Map<java.util.UUID, String> lastIsle = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> warmed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long nextBeat;
    private long pulseUntil;

    public TitanListener(AetherionMining plugin, TitanWorld titan, Jawcamp camp) {
        this.plugin = plugin;
        this.titan = titan;
        this.camp = camp;
        Bukkit.getScheduler().runTaskTimer(plugin, this::breathe, 40L, 4L);
        nextBeat = System.currentTimeMillis() + beatMillis();
        Bukkit.getScheduler().runTaskTimer(plugin, this::heart, 100L, 20L);
    }

    private static boolean staff(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!titan.isTitan(block.getWorld()) || staff(event.getPlayer())) {
            return;
        }
        TitanShape shape = titan.shape();
        if (shape != null && shape.guarded(block.getX(), block.getY(), block.getZ()) || block.getType() == Material.BEDROCK) {
            event.setCancelled(true);
            return;
        }
        if (de.aetherion.mining.SharedWorldGuard.isSoftTerrain(block.getType())) {
            return;
        }
        event.setCancelled(false);
    }

    private long beatMillis() {
        return Math.max(5, plugin.getConfig().getInt("titan.heartbeat-minutes", 30)) * 60_000L;
    }

    /** What the body gives: Titan Bone from bone, Titan Marrow from the Marrowdeep, Heart Embers during a pulse. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!titan.isTitan(block.getWorld()) || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Material type = block.getType();
        if (type == Material.BONE_BLOCK) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(at, TitanItems.create(TitanItems.Kind.BONE, 1));
            return;
        }
        if ((type == Material.SHROOMLIGHT || type == Material.NETHER_WART_BLOCK) && block.getY() <= 36) {
            if (Math.random() < 0.6) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(at, TitanItems.create(TitanItems.Kind.MARROW, 1));
            }
            return;
        }
        if (System.currentTimeMillis() < pulseUntil && de.aetherion.mining.isle.IsleOre.fromBlock(type) != null
                && type != Material.STONE && Math.random() < 0.04) {
            block.getWorld().dropItemNaturally(at, TitanItems.create(TitanItems.Kind.EMBER, 1));
            player.playSound(at, org.bukkit.Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);
        }
    }

    /** Heartbeat: the stone heart beats every half hour; for a minute the veins shed embers, the heart gives one. */
    private void heart() {
        World world = titan.world();
        TitanShape shape = titan.shape();
        long now = System.currentTimeMillis();
        if (world == null || shape == null || !titan.open()) {
            return;
        }
        if (now >= nextBeat) {
            nextBeat = now + beatMillis();
            pulseUntil = now + 60_000L;
            warmed.clear();
            for (Player player : world.getPlayers()) {
                player.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§4Heartbeat"),
                        net.kyori.adventure.text.Component.text("§7the veins shed embers · the heart gives one"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                                java.time.Duration.ofMillis(2000), java.time.Duration.ofMillis(600))));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f);
            }
        }
        if (now >= pulseUntil) {
            return;
        }
        world.spawnParticle(Particle.DUST, shape.heartX, shape.heartY, shape.heartZ, 40, 6, 7, 6, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 30, 40), 2.0f));
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (shape.zoneAt(at.getX(), at.getY(), at.getZ()) == TitanShape.Zone.HEART && warmed.add(player.getUniqueId())) {
                TitanItems.give(player, TitanItems.Kind.EMBER, 1);
                player.sendMessage("§4✦ §7The heart beats beside you. §cA Heart Ember §7falls into your hands.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (!titan.isTitan(event.getBlock().getWorld()) || staff(event.getPlayer())) {
            return;
        }
        event.setCancelled(!PLACEABLE.contains(event.getBlock().getType()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (titan.isTitan(event.getEntity().getWorld()) || camp.isCamp(event.getEntity().getWorld())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (titan.isTitan(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** The lodestone at the gullet landing: touch it and you are coughed up into Jawcamp. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClimb(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !titan.isTitan(event.getClickedBlock().getWorld())) {
            return;
        }
        Block block = event.getClickedBlock();
        TitanShape shape = titan.shape();
        int[] stone = shape == null ? null : shape.climbStone();
        if (stone == null || block.getX() != stone[0] || block.getY() != stone[1] || block.getZ() != stone[2]) {
            return;
        }
        event.setCancelled(true);
        titan.coughUp(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn() || !titan.isTitan(event.getPlayer().getWorld())) {
            return;
        }
        Location spawn = titan.campSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (titan.isTitan(player.getWorld()) && titan.fallSafe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // A closed body, or a spot that is rock in today's body (the world was regrown): back to Jawcamp.
            if (player.isOnline() && TitanWorld.isTitanWorld(player.getWorld())
                    && (!titan.open() || !player.getLocation().getBlock().isPassable()
                    || !player.getEyeLocation().getBlock().isPassable())) {
                titan.coughUp(player, false);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        titan.forget(event.getPlayer().getUniqueId());
        lastIsle.remove(event.getPlayer().getUniqueId());
    }

    /** First visit to each part of the titan: its name, a line on what it gives, Mining XP. The Marrowdeep burns below 50. */
    private void discover(Player player, TitanShape shape, Location at) {
        TitanShape.Zone zone = shape.zoneAt(at.getX(), at.getY(), at.getZ());
        if (zone == TitanShape.Zone.MARROW && !MineGate.reaches(player, 50) && player.getGameMode() == GameMode.SURVIVAL) {
            player.damage(1.0);
            player.setFireTicks(Math.max(player.getFireTicks(), 20));
            player.sendActionBar(net.kyori.adventure.text.Component.text("§cThe marrow burns. §7Mining 50 to bear it."));
        }
        de.aetherion.mining.isle.MineIsle isle = plugin.getIsle();
        if (zone == TitanShape.Zone.ROCK || isle == null) {
            return;
        }
        var profile = isle.profiles().of(player);
        if (!profile.claimMark("titan_zone_" + zone.name().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        isle.profiles().markDirty();
        String[] text = switch (zone) {
            case GULLET -> new String[]{"The Gullet", "the throat · the lodestone takes you back up", "100"};
            case RIBVAULT -> new String[]{"The Ribvault", "ribs of Titan Bone · iron and coal in the walls", "150"};
            case HEART -> new String[]{"The Heartwell", "stand here when the heart beats", "250"};
            case LUNGS -> new String[]{"The Breathing Caves", "ride the breath · diamonds and lapis in the ice", "250"};
            case SPINE -> new String[]{"The Spine Road", "the long way from head to belly · gold and redstone", "200"};
            case SEA -> new String[]{"The Churning Sea", "three swallowed isles drift here today", "250"};
            case MARROW -> new String[]{"The Marrowdeep", "Titan Marrow grows here · ancient debris below", "400"};
            default -> new String[]{"The Tail Coil", "sealed. For now.", "100"};
        };
        int xp = Integer.parseInt(text[2]);
        de.aetherion.mining.isle.MineSkills.bonus(player, xp);
        player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("§6" + text[0]),
                net.kyori.adventure.text.Component.text("§7" + text[1] + " §8· §a+" + xp + " Mining XP"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2800), java.time.Duration.ofMillis(700))));
        player.playSound(at, org.bukkit.Sound.BLOCK_BELL_RESONATE, 0.6f, 0.6f);
    }

    /** The first step onto a swallowed isle shows its name, once per isle per day. */
    private void announceIsle(Player player, TitanShape shape, Location at) {
        TitanShape.Isle isle = shape.isleAt(at.getX(), at.getY(), at.getZ());
        if (isle == null) {
            return;
        }
        String mark = titan.generation() + ":" + isle.theme();
        if (mark.equals(lastIsle.put(player.getUniqueId(), mark))) {
            return;
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("§6" + isle.name()),
                net.kyori.adventure.text.Component.text("§7swallowed long ago · find its Relic Chest"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(700))));
        player.playSound(at, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
    }

    /** Updrafts: rise inside a breath column, sneak to sink through it without a fall. */
    private void breathe() {
        World world = titan.world();
        TitanShape shape = titan.shape();
        if (world == null || shape == null || world.getPlayers().isEmpty()) {
            return;
        }
        boolean puff = (Bukkit.getCurrentTick() & 7) == 0;
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (puff) {
                announceIsle(player, shape, at);
                discover(player, shape, at);
            }
            for (TitanShape.Updraft draft : shape.updrafts) {
                double dx = at.getX() - draft.x(), dz = at.getZ() - draft.z();
                if (puff && dx * dx + dz * dz < 40 * 40) {
                    double y = Math.max(draft.bottom(), Math.min(draft.top(), at.getY()));
                    world.spawnParticle(Particle.SNOWFLAKE, draft.x(), y, draft.z(), 6, 0.9, 3.0, 0.9, 0.02);
                    world.spawnParticle(Particle.CLOUD, draft.x(), y - 2, draft.z(), 2, 0.7, 1.5, 0.7, 0.01);
                }
                if (!draft.contains(at.getX(), at.getY(), at.getZ())) {
                    continue;
                }
                titan.markFallSafe(player, 8000L);
                if (player.isSneaking()) {
                    player.removePotionEffect(PotionEffectType.LEVITATION);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, false));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 10, 7, false, false, false));
                }
                break;
            }
        }
    }
}
