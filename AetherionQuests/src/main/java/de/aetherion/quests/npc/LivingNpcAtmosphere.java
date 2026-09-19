package de.aetherion.quests.npc;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lark: bind a real AetherMobs parrot companion (same follow/bob as equipped pets).
 * Liquidator: enchant-table rune motes around the desk.
 * Fisher: no atmosphere — rod in hand only.
 */
public final class LivingNpcAtmosphere {

    private static final Map<String, Object> petHandles = new ConcurrentHashMap<>();
    private static final Map<String, Location> fallbackHost = new ConcurrentHashMap<>();
    private static volatile BukkitTask keepAlive;
    private static volatile BukkitTask liquidatorFx;
    private static volatile Location liquidatorAt;

    private LivingNpcAtmosphere() {
    }

    public static void onSpawned(String npcId, Location at) {
        if (npcId == null || at == null || at.getWorld() == null) {
            return;
        }
        if ("liquidator".equalsIgnoreCase(npcId)) {
            liquidatorAt = at.clone().add(0.0, 1.1, 0.0);
            ensureLiquidatorFx();
            return;
        }
        if (!"lark".equalsIgnoreCase(npcId)) {
            return;
        }
        fallbackHost.put("lark", at.clone());
        ensureKeepAlive();
        ensureLarkPet();
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        // FancyNPC location / AetherMobs can lag a few ticks after boot.
        plugin.getServer().getScheduler().runTaskLater(plugin, LivingNpcAtmosphere::ensureLarkPet, 5L);
        plugin.getServer().getScheduler().runTaskLater(plugin, LivingNpcAtmosphere::ensureLarkPet, 40L);
        plugin.getServer().getScheduler().runTaskLater(plugin, LivingNpcAtmosphere::ensureLarkPet, 100L);
        plugin.getServer().getScheduler().runTaskLater(plugin, LivingNpcAtmosphere::ensureLarkPet, 200L);
    }

    public static void onRemoved(String npcId) {
        if (npcId == null) {
            return;
        }
        String id = npcId.toLowerCase();
        if ("liquidator".equals(id)) {
            liquidatorAt = null;
            stopLiquidatorFx();
            return;
        }
        Object pet = petHandles.remove(id);
        fallbackHost.remove(id);
        if (pet != null) {
            try {
                pet.getClass().getMethod("remove").invoke(pet);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        // Drop leftover companions from older builds (bobbers / frozen heads).
        if ("fisher".equals(id) || "fisherman".equals(id) || "lark".equals(id)) {
            clearLegacyTagged(id);
        }
        if ("lark".equals(id) && fallbackHost.isEmpty()) {
            stopKeepAlive();
        }
    }

    private static void ensureLiquidatorFx() {
        if (liquidatorFx != null) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        liquidatorFx = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                LivingNpcAtmosphere::tickLiquidatorRunes,
                10L,
                12L
        );
    }

    private static void stopLiquidatorFx() {
        if (liquidatorFx != null) {
            liquidatorFx.cancel();
            liquidatorFx = null;
        }
    }

    private static void tickLiquidatorRunes() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        LivingNpcService living = plugin.getLivingNpcService();
        Location at = null;
        if (living != null && living.isSpawned("liquidator")) {
            Location live = living.locationOf("liquidator");
            if (live != null && live.getWorld() != null) {
                at = live.clone().add(0.0, 1.1, 0.0);
                liquidatorAt = at.clone();
            }
        }
        if (at == null) {
            at = liquidatorAt;
        }
        if (at == null || at.getWorld() == null) {
            return;
        }
        World world = at.getWorld();
        // Enchant-table glyph motes drifting upward around him.
        world.spawnParticle(Particle.ENCHANT, at, 18, 0.45, 0.55, 0.45, 0.55);
        // Soft amethyst shimmer — mysterious without being loud.
        world.spawnParticle(Particle.WITCH, at.clone().add(0.0, 0.35, 0.0), 2, 0.25, 0.35, 0.25, 0.0);
    }

    private static void ensureKeepAlive() {
        if (keepAlive != null) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        keepAlive = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                LivingNpcAtmosphere::ensureLarkPet,
                80L,
                100L
        );
    }

    private static void stopKeepAlive() {
        if (keepAlive != null) {
            keepAlive.cancel();
            keepAlive = null;
        }
    }

    private static void ensureLarkPet() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        LivingNpcService living = plugin.getLivingNpcService();
        if (living == null || !living.isSpawned("lark")) {
            return;
        }

        Supplier<Location> host = () -> {
            Location live = living.locationOf("lark");
            if (live != null && live.getWorld() != null) {
                fallbackHost.put("lark", live.clone());
                return live;
            }
            Location fb = fallbackHost.get("lark");
            return fb != null ? fb.clone() : null;
        };

        Object existing = petHandles.get("lark");
        if (isAlive(existing)) {
            rebindCompanion(existing, host);
            return;
        }

        Object stale = petHandles.remove("lark");
        if (stale != null) {
            try {
                stale.getClass().getMethod("remove").invoke(stale);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        clearLegacyTagged("lark");

        Location seed = host.get();
        if (seed == null || seed.getWorld() == null) {
            return;
        }

        // null name = stock AetherMobs nameplate (pet name + rarity + level), same as player pets
        Object pet = spawnCompanion("parrot", host);
        if (pet == null) {
            plugin.getLogger().warning("Lark companion pet failed — is AetherMobs loaded with spawnCompanionPet?");
            return;
        }
        tagCompanion(pet, "lark");
        rebindCompanion(pet, host);
        petHandles.put("lark", pet);
        ensureKeepAlive();
    }

    private static void rebindCompanion(Object pet, Supplier<Location> host) {
        if (pet == null || host == null) {
            return;
        }
        try {
            pet.getClass().getMethod("bindCompanion", Supplier.class).invoke(pet, host);
        } catch (ReflectiveOperationException ignored) {
            try {
                pet.getClass().getMethod("setCompanionHost", Supplier.class).invoke(pet, host);
            } catch (ReflectiveOperationException ignored2) {
            }
        }
    }

    private static void tagCompanion(Object pet, String npcId) {
        try {
            Object display = pet.getClass().getMethod("getEntity").invoke(pet);
            if (display instanceof Entity entity) {
                entity.addScoreboardTag("ae_living_companion_" + npcId.toLowerCase());
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean isAlive(Object pet) {
        if (pet == null) {
            return false;
        }
        try {
            Object display = pet.getClass().getMethod("getEntity").invoke(pet);
            return display instanceof Entity entity && entity.isValid() && !entity.isDead();
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static Object spawnCompanion(String petId, Supplier<Location> host) {
        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs == null || !mobs.isEnabled()) {
            return null;
        }
        try {
            return mobs.getClass()
                    .getMethod("spawnCompanionPet", String.class, Supplier.class)
                    .invoke(mobs, petId, host);
        } catch (ReflectiveOperationException ex) {
            AetherionQuests plugin = AetherionQuests.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("spawnCompanionPet missing/failed: " + ex.getMessage());
            }
            return null;
        }
    }

    private static void clearLegacyTagged(String npcId) {
        String tag = "ae_living_companion_" + npcId.toLowerCase();
        java.util.Set<World> worlds = new java.util.HashSet<>();
        Location host = fallbackHost.get(npcId == null ? "" : npcId.toLowerCase());
        if (host != null && host.getWorld() != null) {
            worlds.add(host.getWorld());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != null) {
                worlds.add(player.getWorld());
            }
        }
        if (worlds.isEmpty()) {
            worlds.addAll(Bukkit.getWorlds());
        }
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity != null && entity.getScoreboardTags().contains(tag)) {
                    entity.remove();
                }
            }
        }
    }
}
