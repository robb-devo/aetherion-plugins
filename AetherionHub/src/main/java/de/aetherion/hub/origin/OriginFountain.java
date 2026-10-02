package de.aetherion.hub.origin;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The wishing fountain in Fountain Square. Sneak and right-click its water with an empty hand to toss a coin
 * ({@code fountain.cost}, default 10). You get a fortune, a splash and — rarely — the Fountain's Favour
 * ({@code rewards.fountain-favour}). One toss a minute. Coins come from the Core wallet; with Items offline the
 * fountain just listens.
 */
public final class OriginFountain implements Listener {

    private static final long COOLDOWN_MS = 60_000L;
    private static final List<String> FORTUNES = List.of(
            "The water says: the next ore you break is the one you wanted.",
            "The water says: go and ring a bell you haven't rung.",
            "The water says: someone on Skyreach is looking your way.",
            "The water says: patience. Then a lot of fish.",
            "The water says: the Borderlands owe you nothing. Go anyway.",
            "The water says: tonight, look up.",
            "The water says: the glowcaps remember you.",
            "The water says: Vince's tables are not your friends.",
            "The water says: bring a friend to the Colosseum.",
            "The water says: that was a nice coin. Very shiny.",
            "The water says: the wind behind the Mountain Gate is going your way.",
            "The water says: something small and lucky is about to happen. Don't blink."
    );

    private final OriginIsle isle;
    private final Map<UUID, Long> cool = new HashMap<>();

    OriginFountain(OriginIsle isle) {
        this.isle = isle;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToss(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isle.running()) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        double[] center = isle.config().point("fountain.center");
        World world = isle.config().world();
        if (center == null || world == null || !world.equals(player.getWorld())) {
            return;
        }
        double radius = isle.config().raw().getDouble("fountain.radius", 12.0d);
        Location at = player.getLocation();
        double dx = at.getX() - center[0];
        double dz = at.getZ() - center[2];
        if (dx * dx + dz * dz > (radius + 6.0d) * (radius + 6.0d)) {
            return;
        }
        Block water = player.getTargetBlockExact(7, FluidCollisionMode.ALWAYS);
        if (water == null || water.getType() != Material.WATER) {
            return;
        }
        double wx = water.getX() + 0.5d - center[0];
        double wz = water.getZ() + 0.5d - center[2];
        if (wx * wx + wz * wz > radius * radius) {
            return;
        }
        event.setCancelled(true);
        toss(player, water.getLocation().add(0.5, 0.9, 0.5));
    }

    private void toss(Player player, Location splash) {
        long now = System.currentTimeMillis();
        Long until = cool.get(player.getUniqueId());
        if (until != null && now < until) {
            OriginText.bar(player, "§b≈ §7The water is still settling… §8(" + ((until - now) / 1000L + 1) + "s)");
            return;
        }
        long cost = Math.max(0L, isle.config().raw().getLong("fountain.cost", 10L));
        if (!isle.takeCoins(player, cost)) {
            OriginText.bar(player, "§b≈ §7You need §6" + cost + " coins §7to toss.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.5f, 1.2f);
            return;
        }
        cool.put(player.getUniqueId(), now + COOLDOWN_MS);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = splash.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SPLASH, splash, 30, 0.35, 0.1, 0.35, 0.1);
            world.spawnParticle(Particle.BUBBLE_POP, splash, 8, 0.3, 0.1, 0.3, 0.02);
            world.playSound(splash, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.5f, 1.6f);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.6f, 2.0f);
        OriginProfile profile = isle.profiles().get(player);
        boolean first = profile.mark("fountain_coin");
        double chance = isle.config().raw().getDouble("fountain.favour-chance", 0.03d);
        if (rng.nextDouble() < chance) {
            long paid = isle.pay(player, "fountain-favour", 250L);
            OriginText.card(player, "§b§lThe Fountain's Favour", "§7It gave something back.", 50);
            player.sendMessage("§b≈ §fThe Fountain's Favour" + (paid > 0 ? " §8· §6+" + OriginText.coins(paid) + " coins" : "")
                    + " §8· §7the water glitters for a moment.");
            if (world != null) {
                world.spawnParticle(Particle.END_ROD, splash.clone().add(0, 0.6, 0), 24, 0.4, 0.8, 0.4, 0.05);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
            profile.mark("fountain_favour");
            return;
        }
        String line = FORTUNES.get(rng.nextInt(FORTUNES.size()));
        player.sendMessage("§b≈ §7You toss a coin" + (cost > 0 ? " §8(§6-" + cost + "§8)" : "") + "§7. §f" + line);
        if (first) {
            player.sendMessage("§8One toss a minute. Once in a while the fountain gives back.");
        }
    }

    /** Quit: the cooldown deliberately survives a relog; only expired entries are dropped. */
    void forget(UUID id) {
        long now = System.currentTimeMillis();
        cool.values().removeIf(until -> until < now);
    }
}
