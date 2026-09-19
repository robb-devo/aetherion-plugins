package de.aetherion.bossengine.skill.t2;

import de.aetherion.core.AetherKeys;
import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class T2Mechanics {

    public static final NamespacedKey PROP = new NamespacedKey("bossengine", "t2_prop");
    public static final NamespacedKey PROP_BOSS = new NamespacedKey("bossengine", "t2_prop_boss");
    public static final NamespacedKey INVERT_UNTIL = new NamespacedKey("bossengine", "t2_invert_until");
    public static final NamespacedKey CHARM_SUPPRESS = AetherKeys.CHARM_SUPPRESS;

    static final String CORE = "blast_core";
    static final String TOTEM = "charm_totem";

    private static final int MAX_CORES = 4;
    private static final int BURST_TELEGRAPH_TICKS = 14;
    private static final int RING_WINDUP_TICKS = 10;

    private static final Map<UUID, Burrow> BURROWS = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> METEOR_BOOM = new ThreadLocal<>();

    private T2Mechanics() {
    }

    public static List<Player> nearby(LivingEntity origin, double range) {
        List<Player> players = new ArrayList<>();
        if (origin == null || origin.getWorld() == null) {
            return players;
        }
        double rangeSq = range * range;
        for (Player player : origin.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin.getLocation()) <= rangeSq) {
                players.add(player);
            }
        }
        return players;
    }

    public static boolean vulnerable(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    public static void invert(LivingEntity origin, double range, int ticks) {
        if (origin == null) {
            return;
        }
        for (Player player : nearby(origin, Math.max(8.0, range))) {
            clearInvert(player);
        }
    }

    public static boolean inverted(Player player) {
        clearInvert(player);
        return false;
    }

    public static void clearInvert(Player player) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().remove(INVERT_UNTIL);
    }

    public static void charmDrain(BossInstance instance, double range, int lifetimeTicks) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(40, lifetimeTicks) * 50L;
        for (Player player : nearby(boss, range)) {
            player.getPersistentDataContainer().set(CHARM_SUPPRESS, PersistentDataType.LONG, until);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§5Off-hand charms parked. Break the totem."
            ));
        }
        Location spot = boss.getLocation().add(ThreadLocalRandom.current().nextDouble(-3, 3), 0, ThreadLocalRandom.current().nextDouble(-3, 3));
        spawnProp(instance, spot, TOTEM, Material.TOTEM_OF_UNDYING, "§eSpawn Totem", lifetimeTicks, () -> {
            for (Player player : nearby(boss, range + 8)) {
                player.getPersistentDataContainer().remove(CHARM_SUPPRESS);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§aCharms are back. Barely."));
            }
            Location boom = boss.getLocation();
            boom.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boom, 1, 0, 0, 0, 0);
            boom.getWorld().playSound(boom, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.7f);
            for (Player player : nearby(boss, 8)) {
                BossHits.hurt(player, boss, instance.scaleDamage(55));
            }
        });
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.2f, 0.8f);
    }

    public static void vacuum(BossInstance instance, double range, double aoePower, int delayTicks) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        Location pull = boss.getLocation().add(0, 0.4, 0);
        boss.getWorld().playSound(pull, Sound.ENTITY_ENDERMAN_SCREAM, 1.35f, 0.35f);
        boss.getWorld().playSound(pull, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.55f);
        boss.getWorld().spawnParticle(Particle.PORTAL, pull, 160, 2.4, 1.8, 2.4, 0.55);
        boss.getWorld().spawnParticle(Particle.SQUID_INK, pull, 40, 1.1, 0.6, 1.1, 0.02);
        for (int i = 0; i < 10; i++) {
            Location column = pull.clone().add(
                    ThreadLocalRandom.current().nextDouble(-range * 0.4, range * 0.4),
                    0,
                    ThreadLocalRandom.current().nextDouble(-range * 0.4, range * 0.4)
            );
            boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, column.clone().add(0, 2.2, 0), 18, 0.15, 1.8, 0.15, 0.02);
        }
        for (Player player : nearby(boss, range)) {
            Vector to = pull.toVector().subtract(player.getLocation().toVector());
            if (to.lengthSquared() < 0.01) {
                continue;
            }
            to.normalize().multiply(1.35).setY(0.35);
            player.setVelocity(to);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5The vacuum wants a word."));
        }
        instance.getPlugin().getServer().getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (!instance.isAlive()) {
                return;
            }
            Location boom = instance.getEntity().getLocation();
            boom.getWorld().spawnParticle(Particle.SONIC_BOOM, boom.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            boom.getWorld().playSound(boom, Sound.ENTITY_GENERIC_EXPLODE, 1.35f, 0.55f);
            double scaled = instance.scaleDamage(aoePower);
            for (Player player : nearby(instance.getEntity(), 7)) {
                BossHits.hurt(player, instance.getEntity(), scaled);
            }
        }, Math.max(8, delayTicks));
    }

    public static void teleportPrank(BossInstance instance, double range) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        List<Player> players = nearby(boss, range);
        if (players.isEmpty()) {
            return;
        }
        if (players.size() >= 2) {
            Player a = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            Player b = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            int guard = 0;
            while (a.getUniqueId().equals(b.getUniqueId()) && guard++ < 8) {
                b = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            }
            Location one = a.getLocation().clone();
            Location two = b.getLocation().clone();
            a.teleport(two);
            b.teleport(one);
            a.sendActionBar(net.kyori.adventure.text.Component.text("§5You are not where you were."));
            b.sendActionBar(net.kyori.adventure.text.Component.text("§5Seat swap. No refunds."));
        } else {
            Player tank = players.get(0);
            Location behind = boss.getLocation().clone().add(boss.getLocation().getDirection().multiply(-2.4));
            behind.setYaw(tank.getLocation().getYaw());
            behind.setPitch(tank.getLocation().getPitch());
            tank.teleport(behind);
            tank.sendActionBar(net.kyori.adventure.text.Component.text("§5Wrong side of the broom."));
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
    }

    public static void overheat(BossInstance instance, int ticks) {
        instance.armOverheat(Math.max(40, ticks));
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.4f, 0.5f);
        for (Player player : nearby(boss, 28)) {
            player.sendMessage("§6Sparky §7overheats. Arrows still land — but soft.");
        }
    }

    public static void blastCores(BossInstance instance, int amount, int fuseTicks, double explodePower) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        int living = countProps(instance, CORE);
        int room = Math.max(0, MAX_CORES - living);
        if (room <= 0) {
            return;
        }
        int spawn = Math.min(Math.max(1, amount), room);
        double power = instance.scaleDamage(explodePower);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location origin = boss.getLocation();
        for (int i = 0; i < spawn; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 5.5 + random.nextDouble() * 7.5;
            Location spot = origin.clone().add(Math.cos(angle) * dist, 0.15, Math.sin(angle) * dist);
            Location boomAt = spot.clone();
            spawnProp(instance, spot, CORE, Material.MAGMA_CREAM, "§cGrid Core", fuseTicks, () -> {
                if (!instance.isAlive() || instance.isCinematicDying()) {
                    return;
                }
                World world = boomAt.getWorld();
                if (world == null) {
                    return;
                }
                world.spawnParticle(Particle.FLAME, boomAt.clone().add(0, 0.4, 0), 36, 0.7, 0.45, 0.7, 0.06);
                world.spawnParticle(Particle.LAVA, boomAt, 8, 0.35, 0.2, 0.35, 0);
                world.playSound(boomAt, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 1.15f);
                LivingEntity source = instance.getEntity();
                for (Player player : world.getPlayers()) {
                    if (!vulnerable(player)) {
                        continue;
                    }
                    if (player.getLocation().distanceSquared(boomAt) > 4.8 * 4.8) {
                        continue;
                    }
                    BossHits.hurt(player, source, power);
                    player.setFireTicks(Math.max(player.getFireTicks(), 80));
                }
            });
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f);
        for (Player player : nearby(boss, 28)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§cLeft-click the cores. They do not wait."));
        }
    }

    public static void burrow(BossInstance instance, int hideTicks, double launchPower) {
        LivingEntity boss = instance.getEntity();
        if (boss == null || instance.isTransitioning() || BURROWS.containsKey(instance.getInstanceId())) {
            return;
        }
        List<Player> victims = nearby(boss, 18);
        Map<UUID, Location> marks = new ConcurrentHashMap<>();
        for (Player player : victims) {
            marks.put(player.getUniqueId(), player.getLocation().clone());
            player.sendActionBar(net.kyori.adventure.text.Component.text("§8The ground has opinions. Move."));
        }
        if (boss instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
        }
        boss.setInvisible(true);
        boss.setGlowing(false);
        boss.setCollidable(false);
        boss.setGravity(false);
        Location dig = boss.getLocation().clone().subtract(0, Math.min(1.1, boss.getHeight() * 0.55), 0);
        instance.runInternalTeleport(() -> boss.teleport(dig));
        boss.setVelocity(new Vector(0, 0, 0));
        World world = dig.getWorld();
        if (world != null) {
            world.playSound(dig, Sound.ENTITY_WARDEN_DIG, 1.35f, 0.75f);
            world.spawnParticle(Particle.BLOCK, dig.clone().add(0, 1.0, 0), 40, 0.7, 0.35, 0.7, 0.08, Material.DIRT.createBlockData());
            world.spawnParticle(Particle.CLOUD, dig.clone().add(0, 0.8, 0), 16, 0.5, 0.2, 0.5, 0.02);
        }
        BURROWS.put(instance.getInstanceId(), new Burrow(marks, launchPower, Math.max(24, hideTicks)));
    }

    public static boolean isBurrowing(BossInstance instance) {
        return instance != null && BURROWS.containsKey(instance.getInstanceId());
    }

    public static void tickBurrow(BossInstance instance) {
        Burrow burrow = BURROWS.get(instance.getInstanceId());
        if (burrow == null) {
            return;
        }
        LivingEntity boss = instance.getEntity();
        if (boss != null && boss.isValid()) {
            World world = boss.getWorld();
            if (world != null && burrow.remaining % 4 == 0) {
                Location at = boss.getLocation().clone().add(0, 1.0, 0);
                world.spawnParticle(Particle.BLOCK, at, 10, 0.45, 0.15, 0.45, 0.02, Material.DIRT.createBlockData());
            }
            boss.setInvisible(true);
            boss.setGlowing(false);
            boss.setVelocity(new Vector(0, 0, 0));
        }
        for (Location mark : burrow.marks.values()) {
            World world = mark.getWorld();
            if (world == null) {
                continue;
            }
            world.spawnParticle(Particle.BLOCK, mark.clone().add(0, 0.15, 0), 8, 0.35, 0.05, 0.35, 0.01, Material.DIRT.createBlockData());
            world.spawnParticle(Particle.CRIT, mark.clone().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0);
        }
        burrow.remaining--;
        if (burrow.remaining <= 0 || instance.isTransitioning() || !instance.isAlive()) {
            emerge(instance);
        }
    }

    private static void emerge(BossInstance instance) {
        Burrow burrow = BURROWS.remove(instance.getInstanceId());
        if (burrow == null) {
            return;
        }
        LivingEntity live = instance.getEntity();
        if (live != null && live.isValid()) {
            Location strike = pickBurrowStrike(instance, burrow, live);
            live.setGravity(true);
            instance.runInternalTeleport(() -> live.teleport(strike));
            live.setInvisible(false);
            live.setInvulnerable(false);
            live.setCollidable(true);
            live.setGlowing(instance.getTemplate() != null && instance.getTemplate().getOptions().isGlowing());
            live.setVelocity(new Vector(0, 0.35, 0));
            if (live instanceof Mob mob) {
                mob.setAI(true);
                mob.setAware(true);
            }
            World world = strike.getWorld();
            if (world != null) {
                world.playSound(strike, Sound.ENTITY_WARDEN_EMERGE, 1.35f, 0.85f);
                world.spawnParticle(Particle.BLOCK, strike.clone().add(0, 0.3, 0), 50, 0.8, 0.4, 0.8, 0.1, Material.DIRT.createBlockData());
                world.spawnParticle(Particle.CRIT, strike.clone().add(0, 0.5, 0), 18, 0.6, 0.3, 0.6, 0.05);
            }
        }
        if (!instance.isAlive() || live == null) {
            return;
        }
        for (Map.Entry<UUID, Location> entry : burrow.marks.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (!vulnerable(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(entry.getValue()) > 2.4 * 2.4) {
                continue;
            }
            player.setVelocity(new Vector(0, 1.35, 0));
            BossHits.hurt(player, live, instance.scaleDamage(burrow.launchPower));
            player.sendActionBar(net.kyori.adventure.text.Component.text("§8The quarry bites back."));
        }
    }

    private static Location pickBurrowStrike(BossInstance instance, Burrow burrow, LivingEntity live) {
        if (!burrow.marks.isEmpty()) {
            Location mark = burrow.marks.values().iterator().next();
            if (mark != null && mark.getWorld() != null) {
                Location at = mark.clone();
                at.setYaw(live.getLocation().getYaw());
                return at;
            }
        }
        List<Player> near = nearby(live, 20);
        if (!near.isEmpty()) {
            Player pick = near.get(ThreadLocalRandom.current().nextInt(near.size()));
            Location at = pick.getLocation().clone();
            at.setYaw(live.getLocation().getYaw());
            return at;
        }
        return live.getLocation().clone().add(0, 1.0, 0);
    }

    public static void tax(BossInstance instance, double range, double bonusPower) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        Player rich = null;
        int best = -1;
        for (Player player : nearby(boss, range)) {
            int score = wealthScore(player);
            if (score > best) {
                best = score;
                rich = player;
            }
        }
        if (rich == null) {
            return;
        }
        BossHits.hurt(rich, boss, instance.scaleDamage(bonusPower));
        rich.sendActionBar(net.kyori.adventure.text.Component.text(
                "§eThe Wither audited you. Boosters are taxable."
        ));
        boss.getWorld().spawnParticle(Particle.SMOKE, rich.getLocation().add(0, 1.2, 0), 24, 0.3, 0.5, 0.3, 0.02);
        boss.getWorld().playSound(rich.getLocation(), Sound.ENTITY_WITHER_HURT, 0.9f, 0.6f);
    }

    public static void debtHit(Player player, Entity source) {
        if (!vulnerable(player)) {
            return;
        }
        int levels = Math.max(1, Math.min(3, player.getLevel() / 12 + 1));
        if (player.getLevel() > 0) {
            player.giveExpLevels(-levels);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 0, false, true, true));
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§eInsufficient funds. Skills bounced."
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.45f, 1.4f);
        if (source != null) {
            source.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.01);
        }
    }

    public static void clearInstanceProps(BossInstance instance) {
        if (instance == null) {
            return;
        }
        BURROWS.remove(instance.getInstanceId());
        clearPropsOfKind(instance, null);
    }

    public static void clearPropsOfKind(BossInstance instance, String kind) {
        if (instance == null) {
            return;
        }
        String id = instance.getInstanceId().toString();
        java.util.Set<World> worlds = new java.util.HashSet<>();
        if (instance.getSpawnLocation() != null && instance.getSpawnLocation().getWorld() != null) {
            worlds.add(instance.getSpawnLocation().getWorld());
        }
        if (instance.getEntity() != null && instance.getEntity().getWorld() != null) {
            worlds.add(instance.getEntity().getWorld());
        }
        for (World world : worlds) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                String tagged = entity.getPersistentDataContainer().get(PROP_BOSS, PersistentDataType.STRING);
                if (!id.equals(tagged)) {
                    continue;
                }
                if (kind != null) {
                    String prop = entity.getPersistentDataContainer().get(PROP, PersistentDataType.STRING);
                    if (!kind.equals(prop)) {
                        continue;
                    }
                }
                removeProp(entity);
            }
        }
    }

    public static boolean clickProp(Player player, Entity clicked) {
        if (clicked == null || player == null) {
            return false;
        }
        String kind = clicked.getPersistentDataContainer().get(PROP, PersistentDataType.STRING);
        if (kind == null || kind.isBlank()) {
            Entity vehicle = clicked.getVehicle();
            if (vehicle != null) {
                kind = vehicle.getPersistentDataContainer().get(PROP, PersistentDataType.STRING);
                clicked = vehicle;
            }
        }
        if (kind == null) {
            return false;
        }
        removeProp(clicked);
        if (CORE.equals(kind)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§aCore defused. The grid sighs."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
        } else if (TOTEM.equals(kind)) {
            player.getPersistentDataContainer().remove(CHARM_SUPPRESS);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§aTotem smashed. Charms reboot."));
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
            for (Player other : player.getWorld().getPlayers()) {
                if (other.getLocation().distanceSquared(player.getLocation()) <= 400) {
                    other.getPersistentDataContainer().remove(CHARM_SUPPRESS);
                }
            }
        }
        return true;
    }

    public static int wealthScore(Player player) {
        if (player == null) {
            return 0;
        }
        try {
            var items = Bukkit.getPluginManager().getPlugin("AetherionItems");
            if (items == null || !items.isEnabled()) {
                return player.getInventory().getArmorContents().length;
            }
            Object manager = items.getClass().getMethod("getItemManager").invoke(items);
            int total = 0;
            org.bukkit.inventory.ItemStack[] slots = {
                    player.getInventory().getItemInMainHand(),
                    player.getInventory().getItemInOffHand(),
                    player.getInventory().getHelmet(),
                    player.getInventory().getChestplate(),
                    player.getInventory().getLeggings(),
                    player.getInventory().getBoots()
            };
            for (org.bukkit.inventory.ItemStack slot : slots) {
                if (slot == null || slot.getType().isAir()) {
                    continue;
                }
                Object stats = manager.getClass().getMethod("getItemStats", org.bukkit.inventory.ItemStack.class)
                        .invoke(manager, slot);
                if (stats == null) {
                    continue;
                }
                Number core = (Number) stats.getClass().getMethod("getTotalCoreBoosters").invoke(stats);
                Number special = (Number) stats.getClass().getMethod("getTotalSpecialBoosters").invoke(stats);
                total += core.intValue() + special.intValue();
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static boolean hasMiningSkills(Player player) {
        if (player == null) {
            return false;
        }
        try {
            var plugin = Bukkit.getPluginManager().getPlugin("AetherionItems");
            if (plugin == null || !plugin.isEnabled()) {
                return false;
            }
            Object skills = plugin.getClass().getMethod("getSkills").invoke(plugin);
            @SuppressWarnings("unchecked")
            List<Object> equipped = (List<Object>) skills.getClass().getMethod("equipped", Player.class).invoke(skills, player);
            for (Object skill : equipped) {
                Object category = skill.getClass().getMethod("category").invoke(skill);
                if (category != null && "MINING".equals(category.toString())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void tickFireTrail(BossInstance instance) {
        if (!instance.isOverheated() || instance.getEntity() == null) {
            return;
        }
        if (instance.getTicksAlive() % 4 != 0) {
            return;
        }
        Location loc = instance.getEntity().getLocation();
        Block feet = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.2, 0).getBlock();
        if (feet.getType().isAir() && below.getType().isSolid() && !below.isLiquid()) {
            feet.setType(Material.FIRE, false);
            instance.getPlugin().getServer().getScheduler().runTaskLater(instance.getPlugin(), () -> {
                if (feet.getType() == Material.FIRE) {
                    feet.setType(Material.AIR, false);
                }
            }, 160L);
        }
        for (Player player : nearby(instance.getEntity(), 1.8)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 50));
        }
    }

    public static void spectacle(
            BossInstance instance,
            String title,
            String subtitle,
            int lightning,
            Particle particle,
            int count,
            double radius,
            Sound sound
    ) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        Location origin = boss.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Particle used = particle == null ? Particle.END_ROD : particle;
        world.playSound(origin, sound == null ? Sound.ENTITY_WITHER_SPAWN : sound, 1.35f, 0.7f);
        puff(world, used, origin.clone().add(0, 1.4, 0), Math.max(24, count), radius * 0.35, 1.1, radius * 0.35, 0.08);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int bolts = Math.max(0, Math.min(16, lightning));
        for (int i = 0; i < bolts; i++) {
            Location strike = origin.clone().add(random.nextDouble(-radius, radius), 0, random.nextDouble(-radius, radius));
            world.strikeLightningEffect(strike);
            puff(world, Particle.FLASH, strike.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        }
        for (Player player : nearby(boss, Math.max(16, radius + 8))) {
            if (title != null && !title.isBlank()) {
                player.showTitle(net.kyori.adventure.title.Title.title(
                        de.aetherion.bossengine.util.TextUtil.component(title),
                        de.aetherion.bossengine.util.TextUtil.component(subtitle == null ? "" : subtitle),
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ofMillis(80),
                                java.time.Duration.ofMillis(1400),
                                java.time.Duration.ofMillis(180)
                        )
                ));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.45f, 0.7f);
        }
    }

    public static void ringBurst(
            BossInstance instance,
            int waves,
            double step,
            double damage,
            Particle particle
    ) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        JavaPlugin plugin = instance.getPlugin();
        Particle used = particle == null ? Particle.SWEEP_ATTACK : particle;
        int safeWaves = Math.max(2, Math.min(8, waves));
        double safeStep = Math.max(2.2, step);
        double scaled = instance.scaleDamage(damage);
        Location pinned = boss.getLocation().clone();
        de.aetherion.bossengine.fx.CombatTheatrics.ringAnnounce(instance, pinned);
        World announceWorld = pinned.getWorld();
        if (announceWorld != null) {
            for (int preview = 1; preview <= safeWaves; preview++) {
                double radius = preview * safeStep;
                int points = Math.max(12, (int) (radius * 4));
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2 * i) / points;
                    Location rim = pinned.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
                    puff(announceWorld, Particle.CRIT, rim, 1, 0, 0, 0, 0);
                }
            }
        }
        for (int wave = 1; wave <= safeWaves; wave++) {
            int index = wave;
            long delay = RING_WINDUP_TICKS + (index - 1L) * 6L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!instance.isAlive() || instance.getEntity() == null) {
                    return;
                }
                Location center = pinned.clone();
                World world = center.getWorld();
                if (world == null) {
                    return;
                }
                double radius = index * safeStep;
                int points = Math.max(18, (int) (radius * 8));
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2 * i) / points;
                    Location rim = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                    puff(world, used, rim, 2, 0.08, 0.12, 0.08, 0.01);
                    if (index == safeWaves) {
                        world.spawnParticle(Particle.CRIT, rim.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                    }
                }
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 0.55f + index * 0.12f);
                if (index == safeWaves) {
                    de.aetherion.bossengine.fx.CombatTheatrics.ringClimax(instance, center, radius);
                }
                for (Player player : nearby(instance.getEntity(), radius + 1.6)) {
                    double dist = player.getLocation().distance(center);
                    if (dist < radius - 1.35 || dist > radius + 1.45) {
                        continue;
                    }
                    Vector push = player.getLocation().toVector().subtract(center.toVector());
                    if (push.lengthSquared() > 0.01) {
                        push.normalize().multiply(0.85).setY(0.28);
                        player.setVelocity(push);
                    }
                    BossHits.hurt(player, instance.getEntity(), scaled);
                }
            }, delay);
        }
    }

    public static boolean meteorBoomActive() {
        return Boolean.TRUE.equals(METEOR_BOOM.get());
    }

    public static void runMeteorBoom(Runnable boom) {
        if (boom == null) {
            return;
        }
        METEOR_BOOM.set(Boolean.TRUE);
        try {
            boom.run();
        } finally {
            METEOR_BOOM.remove();
        }
    }

    public static void meteorRain(BossInstance instance, int amount, double damage, double scatter) {
        meteorRain(instance, amount, damage, scatter, "BURST");
    }

    public static void meteorRain(BossInstance instance, int amount, double damage, double scatter, String style) {
        LivingEntity boss = instance.getEntity();
        if (boss == null) {
            return;
        }
        boolean ghast = isGhastMeteors(instance, style);
        JavaPlugin plugin = instance.getPlugin();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = Math.max(3, Math.min(14, amount));
        double spread = Math.max(4.0, scatter);
        String bar = ghast ? "§6Ghast fire. The grid is pitching." : "§cThe sky just filed a complaint.";
        for (Player player : nearby(boss, 28)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(bar));
        }
        List<Player> prey = nearby(boss, 28);
        for (int i = 0; i < count; i++) {
            int delay = 4 + i * (ghast ? 7 : 5);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!instance.isAlive() || instance.getEntity() == null) {
                    return;
                }
                Location ground = pickMeteorTarget(instance.getEntity(), prey, spread, random);
                if (ghast) {
                    launchGhastMeteor(instance, ground, damage);
                    return;
                }
                burstMeteor(instance, ground, damage);
            }, delay);
        }
    }

    private static boolean isGhastMeteors(BossInstance instance, String style) {
        if (style != null) {
            String raw = style.trim().toUpperCase();
            if (raw.equals("GHAST") || raw.equals("FIREBALL") || raw.equals("LARGE_FIREBALL")) {
                return true;
            }
            if (raw.equals("BURST") || raw.equals("PARTICLE") || raw.equals("FALSE") || raw.equals("OFF")) {
                return false;
            }
        }
        return instance.getTemplate() != null && "sparky".equalsIgnoreCase(instance.getTemplate().getId());
    }

    private static Location pickMeteorTarget(
            LivingEntity boss,
            List<Player> prey,
            double spread,
            ThreadLocalRandom random
    ) {
        Location ground;
        if (prey != null && !prey.isEmpty() && random.nextDouble() < 0.55) {
            Player target = prey.get(random.nextInt(prey.size()));
            ground = target.getLocation().clone().add(
                    random.nextDouble(-2.4, 2.4),
                    0,
                    random.nextDouble(-2.4, 2.4)
            );
        } else {
            ground = boss.getLocation().clone().add(
                    random.nextDouble(-spread, spread),
                    0,
                    random.nextDouble(-spread, spread)
            );
        }
        if (ground.distanceSquared(boss.getLocation()) < 3.2 * 3.2) {
            Vector away = ground.toVector().subtract(boss.getLocation().toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            ground = boss.getLocation().clone().add(away.normalize().multiply(4.2));
        }
        ground.setY(boss.getLocation().getY());
        return ground;
    }

    private static void burstMeteor(BossInstance instance, Location ground, double damage) {
        World world = ground.getWorld();
        if (world == null) {
            return;
        }
        Location impact = ground.clone();
        double scaled = instance.scaleDamage(damage);
        de.aetherion.bossengine.fx.CombatTheatrics.meteor(instance, impact);
        int points = 22;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Location rim = impact.clone().add(Math.cos(angle) * 3.2, 0.08, Math.sin(angle) * 3.2);
            world.spawnParticle(Particle.FLAME, rim, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CRIT, rim, 1, 0, 0, 0, 0);
        }
        world.playSound(impact, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.55f);
        JavaPlugin plugin = instance.getPlugin();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!instance.isAlive() || instance.isCinematicDying()) {
                return;
            }
            World boomWorld = impact.getWorld();
            if (boomWorld == null) {
                return;
            }
            boomWorld.spawnParticle(Particle.EXPLOSION_EMITTER, impact.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
            boomWorld.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.75f);
            for (Player player : boomWorld.getPlayers()) {
                if (!vulnerable(player)) {
                    continue;
                }
                if (player.getLocation().distanceSquared(impact) <= 3.4 * 3.4) {
                    BossHits.hurt(player, instance.getEntity(), scaled);
                    if (de.aetherion.bossengine.fx.CombatTheatrics.meteorBurns(instance)) {
                        player.setFireTicks(Math.max(player.getFireTicks(), 60));
                    }
                }
            }
        }, BURST_TELEGRAPH_TICKS);
    }

    private static int countProps(BossInstance instance, String kind) {
        if (instance == null || kind == null) {
            return 0;
        }
        String id = instance.getInstanceId().toString();
        int count = 0;
        java.util.Set<World> worlds = new java.util.HashSet<>();
        if (instance.getSpawnLocation() != null && instance.getSpawnLocation().getWorld() != null) {
            worlds.add(instance.getSpawnLocation().getWorld());
        }
        if (instance.getEntity() != null && instance.getEntity().getWorld() != null) {
            worlds.add(instance.getEntity().getWorld());
        }
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                String tagged = entity.getPersistentDataContainer().get(PROP_BOSS, PersistentDataType.STRING);
                String prop = entity.getPersistentDataContainer().get(PROP, PersistentDataType.STRING);
                if (id.equals(tagged) && kind.equals(prop) && entity instanceof ArmorStand) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void launchGhastMeteor(BossInstance instance, Location ground, double damage) {
        LivingEntity boss = instance.getEntity();
        World world = ground.getWorld();
        if (boss == null || world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location sky = ground.clone().add(
                random.nextDouble(-2.8, 2.8),
                16.0 + random.nextDouble() * 5.0,
                random.nextDouble(-2.8, 2.8)
        );
        Vector velocity = ground.toVector().subtract(sky.toVector());
        if (velocity.lengthSquared() < 0.01) {
            velocity = new Vector(0, -1, 0);
        }
        velocity.normalize().multiply(1.12);
        org.bukkit.entity.LargeFireball ball = world.spawn(sky, org.bukkit.entity.LargeFireball.class, spawned -> {
            spawned.setShooter(boss);
            spawned.setYield(0f);
            spawned.setIsIncendiary(false);
            spawned.setBounce(false);
            spawned.setPersistent(false);
            instance.getKeys().tagMeteor(spawned, damage);
        });
        ball.setDirection(velocity);
        ball.setVelocity(velocity);
        world.playSound(sky, Sound.ENTITY_GHAST_SHOOT, 1.25f, 0.7f);
        world.playSound(ground, Sound.ENTITY_GHAST_WARN, 0.45f, 0.8f);
        trailMeteor(instance.getPlugin(), ball);
    }

    private static void trailMeteor(JavaPlugin plugin, org.bukkit.entity.LargeFireball ball) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ball == null || !ball.isValid() || ball.isDead()) {
                task.cancel();
                return;
            }
            Location at = ball.getLocation();
            World world = at.getWorld();
            if (world == null) {
                task.cancel();
                return;
            }
            world.spawnParticle(Particle.FLAME, at, 5, 0.12, 0.12, 0.12, 0.02);
            world.spawnParticle(Particle.LAVA, at, 1, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(Particle.SMOKE, at, 2, 0.08, 0.08, 0.08, 0.01);
        }, 1L, 1L);
    }

    private static void spawnProp(
            BossInstance instance,
            Location location,
            String kind,
            Material icon,
            String name,
            int lifetimeTicks,
            Runnable onExpire
    ) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ArmorStand stand = world.spawn(location, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(false);
            spawned.setSmall(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setCustomNameVisible(true);
            spawned.customName(de.aetherion.bossengine.util.TextUtil.component(name));
            spawned.getPersistentDataContainer().set(PROP, PersistentDataType.STRING, kind);
            spawned.getPersistentDataContainer().set(PROP_BOSS, PersistentDataType.STRING, instance.getInstanceId().toString());
            EntityEquipment equipment = spawned.getEquipment();
            if (equipment != null) {
                ItemStack helm = new ItemStack(icon);
                if (helm.getItemMeta() instanceof LeatherArmorMeta meta) {
                    meta.setColor(Color.ORANGE);
                    helm.setItemMeta(meta);
                }
                equipment.setHelmet(helm);
            }
        });
        Interaction hit = world.spawn(location.clone().add(0, 0.2, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(0.9f);
            spawned.setInteractionHeight(1.1f);
            spawned.setResponsive(true);
            spawned.getPersistentDataContainer().set(PROP, PersistentDataType.STRING, kind);
            spawned.getPersistentDataContainer().set(PROP_BOSS, PersistentDataType.STRING, instance.getInstanceId().toString());
        });
        stand.addPassenger(hit);
        JavaPlugin plugin = instance.getPlugin();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean still = stand.isValid();
            removeProp(stand);
            removeProp(hit);
            if (still && onExpire != null) {
                onExpire.run();
            }
        }, Math.max(20, lifetimeTicks));
    }

    private static void removeProp(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            passenger.remove();
        }
        Entity vehicle = entity.getVehicle();
        entity.remove();
        if (vehicle != null && vehicle.isValid()) {
            vehicle.remove();
        }
    }

    private static void puff(
            World world,
            Particle particle,
            Location location,
            int count,
            double ox,
            double oy,
            double oz,
            double extra
    ) {
        if (world == null || location == null || particle == null) {
            return;
        }
        try {
            world.spawnParticle(particle, location, count, ox, oy, oz, extra);
        } catch (Exception ignored) {
            world.spawnParticle(Particle.END_ROD, location, Math.max(6, count / 2), ox, oy, oz, extra);
        }
    }

    private static final class Burrow {
        private final Map<UUID, Location> marks;
        private final double launchPower;
        private int remaining;

        private Burrow(Map<UUID, Location> marks, double launchPower, int remaining) {
            this.marks = marks;
            this.launchPower = launchPower;
            this.remaining = Math.max(1, remaining);
        }
    }
}
