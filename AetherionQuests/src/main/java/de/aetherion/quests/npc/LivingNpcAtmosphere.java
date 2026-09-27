package de.aetherion.quests.npc;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Lark: bind a real AetherMobs parrot companion (same follow/bob as equipped pets).
 * Liquidator: enchant-table rune motes around the desk.
 * Fisher: no atmosphere — rod in hand only.
 * <p>
 * First-hour spine (Egon, Forager, Foreman, Miss Ledger): one shared slow loop.
 * Particles are small and constant; sounds are rare, quiet, and only for players
 * standing nearby — the NPC is busy with their own job when you walk up.
 * <p>
 * Past the pier the same loop carries the rest of the early cast, each at their own
 * trade: the Quartermaster's crates, the Craftsman's hammer (sparks only when it lands),
 * Temper's heated metal, the Farmer's chaff and hens, Sergeant Vex drilling at the
 * Borderlands gate in blown ash, the Rite Warden's spores and soul-sighs, the Surveyor's
 * spyglass. Same budget: tiny particles only when someone is watching, sounds rare.
 */
public final class LivingNpcAtmosphere {

    private static final Map<String, Object> petHandles = new ConcurrentHashMap<>();
    private static final Map<String, Location> fallbackHost = new ConcurrentHashMap<>();
    private static volatile BukkitTask keepAlive;
    private static volatile BukkitTask liquidatorFx;
    private static volatile Location liquidatorAt;

    /** Early cast with an ambient loop (pier spine + the stops after it). */
    private static final Set<String> SPINE = Set.of(
            "egon", "lumberjack", "foreman", "ledger",
            "quartermaster", "craftsman", "booster_tutor", "farmer",
            "vex", "rite_keeper", "surveyor"
    );
    private static final double SPINE_VIEW = 20.0;
    private static final double SPINE_EAR = 14.0;
    private static final long SPINE_PERIOD = 10L;
    private static final Map<String, Location> spineAt = new ConcurrentHashMap<>();
    private static final Map<String, Long> spineNextSound = new ConcurrentHashMap<>();
    private static volatile BukkitTask spineFx;
    private static long spineCycle;
    private static final BlockData OAK_CHIPS = Material.OAK_LOG.createBlockData();
    private static final Particle.DustOptions LEDGER_INK =
            new Particle.DustOptions(Color.fromRGB(92, 48, 64), 0.55f);
    private static final BlockData CHAFF = Material.HAY_BLOCK.createBlockData();
    private static final Particle.DustOptions HOT_METAL =
            new Particle.DustOptions(Color.fromRGB(255, 142, 52), 0.5f);
    private static final Particle.DustOptions BLUEPRINT_INK =
            new Particle.DustOptions(Color.fromRGB(70, 130, 200), 0.45f);

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
        String spineId = npcId.toLowerCase(java.util.Locale.ROOT);
        if (SPINE.contains(spineId)) {
            spineAt.put(spineId, at.clone());
            ensureSpineFx();
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
        if (SPINE.contains(id)) {
            spineAt.remove(id);
            spineNextSound.remove(id);
            if (spineAt.isEmpty()) {
                stopSpineFx();
            }
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

    /* =========================================================
     * SPINE AMBIENCE — Egon / Forager / Foreman / Miss Ledger
     * ========================================================= */

    private static void ensureSpineFx() {
        if (spineFx != null) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        spineFx = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                LivingNpcAtmosphere::tickSpine,
                20L,
                SPINE_PERIOD
        );
    }

    private static void stopSpineFx() {
        if (spineFx != null) {
            spineFx.cancel();
            spineFx = null;
        }
    }

    private static void tickSpine() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        spineCycle++;
        LivingNpcService living = plugin.getLivingNpcService();
        for (String id : SPINE) {
            Location cached = spineAt.get(id);
            if (cached == null) {
                continue;
            }
            Location at = cached;
            if (living != null && living.isSpawned(id)) {
                Location live = living.locationOf(id);
                if (live != null && live.getWorld() != null) {
                    at = live.clone();
                    spineAt.put(id, at.clone());
                }
            }
            World world = at.getWorld();
            if (world == null) {
                continue;
            }
            List<Player> viewers = nearby(world, at, SPINE_VIEW);
            if (viewers.isEmpty()) {
                continue;
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            switch (id) {
                case "egon" -> egonAmbient(world, at, rng);
                case "lumberjack" -> foragerAmbient(world, at, rng);
                case "foreman" -> foremanAmbient(world, at, rng);
                case "ledger" -> ledgerAmbient(world, at, rng);
                case "booster_tutor" -> temperAmbient(world, at, rng);
                case "farmer" -> farmerAmbient(world, at, rng);
                case "vex" -> vexAmbient(world, at, rng);
                case "rite_keeper" -> riteAmbient(world, at, rng);
                case "surveyor" -> surveyorAmbient(world, at, rng);
                default -> {
                    // Quartermaster / Craftsman: sound-led, no idle particles.
                }
            }
            if (soundDue(id, rng)) {
                List<Player> ears = nearby(world, at, SPINE_EAR);
                if (!ears.isEmpty()) {
                    spineSound(plugin, id, at, ears, rng);
                }
            }
        }
    }

    /** Harbour kit man: light catches freshly oiled iron in his hands. */
    private static void egonAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.28) {
            world.spawnParticle(Particle.WAX_OFF, at.clone().add(0.0, 1.15, 0.0), 1, 0.22, 0.12, 0.22, 0.0);
        }
    }

    /** Forager: whittling — oak chips drop off the axe now and then. */
    private static void foragerAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.34) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0.0, 1.05, 0.0), 2, 0.2, 0.08, 0.2, 0.0, OAK_CHIPS);
        }
    }

    /** Shaft Foreman: pipe on, always. */
    private static void foremanAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.5) {
            world.spawnParticle(Particle.SMOKE, at.clone().add(0.0, 1.85, 0.0), 1, 0.06, 0.02, 0.06, 0.004);
        }
    }

    /** Miss Ledger: ink specks off the quill. */
    private static void ledgerAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (spineCycle % 2 == 0 && rng.nextDouble() < 0.6) {
            world.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.1, 0.0), 1, 0.18, 0.06, 0.18, 0.0, LEDGER_INK);
        }
    }

    /** Temper: metal still warm from the last fuse — a glint, a heat mote. */
    private static void temperAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.22) {
            world.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.1, 0.0), 1, 0.16, 0.06, 0.16, 0.0, HOT_METAL);
        } else if (rng.nextDouble() < 0.08) {
            world.spawnParticle(Particle.WAX_ON, at.clone().add(0.0, 1.15, 0.0), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** Farmer: chaff in the breeze around his boots. */
    private static void farmerAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.25) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0.0, 0.35, 0.0), 2, 0.55, 0.1, 0.55, 0.0, CHAFF);
        }
    }

    /** Sergeant Vex: the waste blows ash through the gate. */
    private static void vexAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.55) {
            world.spawnParticle(Particle.WHITE_ASH, at.clone().add(0.0, 1.4, 0.0), 3, 1.6, 0.8, 1.6, 0.0);
        }
    }

    /** Rite Warden: crimson spores hang in the air; now and then a soul lifts off the altar dust. */
    private static void riteAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.45) {
            world.spawnParticle(Particle.CRIMSON_SPORE, at.clone().add(0.0, 1.2, 0.0), 2, 0.9, 0.6, 0.9, 0.0);
        }
        if (rng.nextDouble() < 0.04) {
            world.spawnParticle(Particle.SOUL, at.clone().add(rng.nextDouble(-0.8, 0.8), 0.2, rng.nextDouble(-0.8, 0.8)),
                    1, 0.0, 0.0, 0.0, 0.015);
        }
    }

    /** Surveyor: blueprint ink on the fingers. */
    private static void surveyorAmbient(World world, Location at, ThreadLocalRandom rng) {
        if (spineCycle % 2 == 1 && rng.nextDouble() < 0.4) {
            world.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.1, 0.0), 1, 0.18, 0.06, 0.18, 0.0, BLUEPRINT_INK);
        }
    }

    /** Next sound in 6–14 s per NPC, so the pier never ticks like a clock. */
    private static boolean soundDue(String id, ThreadLocalRandom rng) {
        long next = spineNextSound.getOrDefault(id, 0L);
        if (next == 0L) {
            spineNextSound.put(id, spineCycle + rng.nextLong(4L, 16L));
            return false;
        }
        if (spineCycle < next) {
            return false;
        }
        spineNextSound.put(id, spineCycle + rng.nextLong(12L, 29L));
        return true;
    }

    private static void spineSound(
            AetherionQuests plugin,
            String id,
            Location at,
            List<Player> ears,
            ThreadLocalRandom rng
    ) {
        Location hands = at.clone().add(0.0, 1.1, 0.0);
        switch (id) {
            case "egon" -> {
                if (rng.nextBoolean()) {
                    play(ears, hands, Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.NEUTRAL, 0.16f, 1.15f);
                } else {
                    // A fish turns over somewhere off the pier.
                    Location water = at.clone().add(rng.nextDouble(-6.0, 6.0), -1.0, rng.nextDouble(-6.0, 6.0));
                    play(ears, water, Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.AMBIENT, 0.18f, 1.3f);
                }
            }
            case "lumberjack" -> {
                play(ears, hands, Sound.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL, 0.22f, 0.95f);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> play(ears, hands, Sound.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL, 0.18f, 1.05f), 5L);
            }
            case "foreman" -> play(ears, hands, Sound.BLOCK_STONE_HIT, SoundCategory.NEUTRAL, 0.2f, 0.8f);
            case "ledger" -> {
                // Page… stamp.
                play(ears, hands, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.28f, 1.1f);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> play(ears, hands, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.NEUTRAL, 0.22f, 0.62f), 7L);
            }
            case "quartermaster" -> {
                // A crate lid, then the tally.
                if (rng.nextBoolean()) {
                    play(ears, hands, Sound.BLOCK_BARREL_CLOSE, SoundCategory.NEUTRAL, 0.2f, 0.9f);
                } else {
                    play(ears, hands, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.22f, 0.9f);
                }
            }
            case "craftsman" -> {
                // Two hammer taps; sparks jump only when the hammer lands.
                craftsmanTap(ears, hands, 1.45f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> craftsmanTap(ears, hands, 1.6f), 6L);
            }
            case "booster_tutor" -> play(ears, hands, Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.NEUTRAL, 0.14f, 1.25f);
            case "farmer" -> {
                if (rng.nextBoolean()) {
                    play(ears, hands, Sound.ITEM_HOE_TILL, SoundCategory.NEUTRAL, 0.2f, 1.05f);
                } else {
                    Location coop = at.clone().add(rng.nextDouble(-5.0, 5.0), 0.3, rng.nextDouble(-5.0, 5.0));
                    play(ears, coop, Sound.ENTITY_CHICKEN_AMBIENT, SoundCategory.AMBIENT, 0.18f, 1.1f);
                }
            }
            case "vex" -> {
                if (rng.nextBoolean()) {
                    // Whetstone along the blade.
                    play(ears, hands, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.NEUTRAL, 0.12f, 1.45f);
                } else {
                    // Drill cadence — two low beats.
                    play(ears, hands, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.NEUTRAL, 0.24f, 0.5f);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> play(ears, hands, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.NEUTRAL, 0.24f, 0.5f), 8L);
                }
            }
            case "rite_keeper" -> {
                if (rng.nextBoolean()) {
                    play(ears, hands, Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.AMBIENT, 0.5f, 0.8f);
                } else {
                    play(ears, hands, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.AMBIENT, 0.16f, 0.7f);
                }
            }
            case "surveyor" -> play(ears, hands, Sound.ITEM_SPYGLASS_USE, SoundCategory.NEUTRAL, 0.3f, 1.0f);
            default -> {
            }
        }
    }

    private static void craftsmanTap(List<Player> ears, Location hands, float pitch) {
        play(ears, hands, Sound.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.08f, pitch);
        World world = hands.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.CRIT, hands.clone().add(0.0, -0.1, 0.0), 3, 0.08, 0.04, 0.08, 0.12);
        }
    }

    private static void play(List<Player> ears, Location at, Sound sound, SoundCategory category, float volume, float pitch) {
        for (Player player : ears) {
            if (player.isOnline() && at.getWorld() != null && at.getWorld().equals(player.getWorld())) {
                player.playSound(at, sound, category, volume, pitch);
            }
        }
    }

    private static List<Player> nearby(World world, Location at, double radius) {
        List<Player> out = new ArrayList<>(2);
        double r2 = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= r2) {
                out.add(player);
            }
        }
        return out;
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
