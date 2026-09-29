package de.aetherion.mining.isle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crystal Finds — the rare find of Mining. Now and then a strike cracks into a hollow and a
 * glowing geode shoulders out of the rock. It isn't free: <b>you have to mine it open</b>, a few
 * pickaxe strikes while it spins and splinters. The finder gets first dibs; after a few seconds
 * anyone may take a swing — whoever lands the last crack keeps the specimen.
 *
 * <p>Specimens carry a family, a grade (Rough → Flawless → Perfect, and the Veins-only
 * Heartstone) and a carat weight. They sell at the Assayer, fill specimen contracts, feed the
 * Hearth's Geode Broth, fill the Specimen Cabinet, and the heaviest of each family is an isle
 * record on the Assay Hall board.
 */
public final class CrystalFinds implements Listener {

    private static final int EMERGE_TICKS = 12;
    private static final int FINDER_TICKS = 20 * 5;
    private static final int LIFE_TICKS = 20 * 16;
    private static final long COOLDOWN_MS = 25_000L;
    private static final int STRIKE_GAP_TICKS = 4;

    private final MineIsle isle;
    private final NamespacedKey specimenKey;
    private final NamespacedKey gradeKey;
    private final NamespacedKey caratKey;
    private final NamespacedKey partKey;
    private final Map<UUID, Geode> byPart = new ConcurrentHashMap<>();
    private final Map<UUID, Geode> byFinder = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFind = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastStrike = new ConcurrentHashMap<>();

    private final class Geode {
        final UUID finder;
        final IsleOre ore;
        final Grade grade;
        final double carats;
        final Location at;
        final boolean veins;
        final List<Entity> parts = new ArrayList<>();
        final int bornTick;
        final int cracksNeeded;
        int cracks;
        ItemDisplay stone;
        TextDisplay label;
        BukkitTask task;
        boolean done;
        boolean announced;

        Geode(UUID finder, IsleOre ore, Grade grade, double carats, Location at, boolean veins, int cracksNeeded) {
            this.finder = finder;
            this.ore = ore;
            this.grade = grade;
            this.carats = carats;
            this.at = at;
            this.veins = veins;
            this.cracksNeeded = cracksNeeded;
            this.bornTick = Bukkit.getCurrentTick();
        }
    }

    CrystalFinds(MineIsle isle) {
        this.isle = isle;
        this.specimenKey = new NamespacedKey(isle.plugin(), "mine_specimen");
        this.gradeKey = new NamespacedKey(isle.plugin(), "mine_specimen_grade");
        this.caratKey = new NamespacedKey(isle.plugin(), "mine_specimen_ct");
        this.partKey = new NamespacedKey(isle.plugin(), "mine_geode_part");
    }

    /** Quit: drop per-player bookkeeping (the find cooldown only while it has run out). */
    void forget(UUID id) {
        lastStrike.remove(id);
        Long last = lastFind.get(id);
        if (last != null && System.currentTimeMillis() - last >= COOLDOWN_MS) {
            lastFind.remove(id);
        }
    }

    void shutdown() {
        for (Geode geode : List.copyOf(byFinder.values())) {
            remove(geode);
        }
    }

    /** Leftover geode parts from a crash (entities are not persistent, but chunks can be saved mid-find). */
    void purgeStrays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(partKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ odds

    private double baseChance() {
        return Math.max(0.0d, isle.plugin().getConfig().getDouble("crystal-finds.chance", 1.0d / 550.0d));
    }

    /** Chance for one hand strike of {@code ore}; Emerald Spread neighbours and vacuum never roll. */
    public double chance(Player player, IsleOre ore, Location at, boolean veins) {
        if (ore == null || !ore.hasCrystal()) {
            return 0.0d;
        }
        double chance = baseChance();
        chance *= switch (ore) {
            case DIAMOND, EMERALD, DEBRIS -> 3.0d;
            case AMETHYST, GOLD -> 2.0d;
            default -> 1.0d;
        };
        chance *= 1.0d + 0.5d * MineSkills.scale(player, MineSkills.GEODE_NOSE);
        chance *= isle.hearth().crystalMultiplier(player);
        chance *= 1.0d + 0.05d * isle.mastery().tier(player, ore);
        chance *= isle.ledger().crystalMultiplier(player);
        if (veins) {
            chance *= isle.plugin().getConfig().getDouble("crystal-finds.veins-multiplier", 1.25d);
            chance *= isle.amethyst().crystalMultiplier(player);
            chance *= isle.forge().crystalMultiplier(player);
        } else {
            chance *= isle.events().crystalMultiplier();
            chance *= isle.depthCrystalMultiplier(player, at);
        }
        return Math.min(0.25d, chance);
    }

    void roll(Player player, IsleOre ore, Location block, boolean veins) {
        if (ore == null || !ore.hasCrystal() || byFinder.containsKey(player.getUniqueId())) {
            return;
        }
        Long last = lastFind.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < COOLDOWN_MS) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance(player, ore, block, veins)) {
            return;
        }
        lastFind.put(player.getUniqueId(), System.currentTimeMillis());
        Grade grade = rollGrade(player, veins);
        spawn(player, ore, grade, rollCarats(player, ore, grade), block, veins);
    }

    private Grade rollGrade(Player player, boolean veins) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (veins) {
            double heart = isle.plugin().getConfig().getDouble("crystal-finds.heartstone-chance", 0.04d)
                    * (1.0d + 0.5d * MineSkills.scale(player, MineSkills.GEODE_NOSE));
            if (rng.nextDouble() < heart) {
                return Grade.HEARTSTONE;
            }
        }
        Grade first = pick(rng.nextDouble());
        if (MineSkills.has(player, MineSkills.GEODE_NOSE)) {
            Grade second = pick(rng.nextDouble());
            return second.ordinal() > first.ordinal() ? second : first;
        }
        return first;
    }

    private static Grade pick(double roll) {
        if (roll < Grade.PERFECT.weight()) {
            return Grade.PERFECT;
        }
        if (roll < Grade.PERFECT.weight() + Grade.FLAWLESS.weight()) {
            return Grade.FLAWLESS;
        }
        return Grade.ROUGH;
    }

    private double rollCarats(Player player, IsleOre ore, Grade grade) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double roll = rng.nextDouble();
        if (MineSkills.has(player, MineSkills.GEODE_NOSE)) {
            roll = Math.max(roll, rng.nextDouble());
        }
        double shaped = Math.pow(roll, 1.8d);
        double carats = ore.minCarat() + (ore.maxCarat() - ore.minCarat()) * shaped;
        double gradeMult = switch (grade) {
            case ROUGH -> 1.0d;
            case FLAWLESS -> 1.35d;
            case PERFECT -> 1.8d;
            case HEARTSTONE -> 2.6d;
        };
        return Math.round(carats * gradeMult * 100.0d) / 100.0d;
    }

    // ------------------------------------------------------------------ the geode

    /** Pushes a geode out of {@code block} for {@code finder}. Public for the DEV menu. */
    public void spawn(Player finder, IsleOre ore, Grade grade, double carats, Location block, boolean veins) {
        World world = block.getWorld();
        if (world == null || ore == null || !ore.hasCrystal()) {
            return;
        }
        Geode existing = byFinder.get(finder.getUniqueId());
        if (existing != null) {
            remove(existing);
        }
        Location at = block.getBlock().getLocation().add(0.5, 0.35, 0.5);
        int cracks = Math.max(2, grade.cracks() - (MineSkills.has(finder, MineSkills.GEODE_NOSE) ? 1 : 0));
        Geode geode = new Geode(finder.getUniqueId(), ore, grade, carats, at, veins, cracks);

        geode.stone = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(ore.showcase()));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(grade == Grade.HEARTSTONE ? Color.fromRGB(255, 170, 30) : ore.glow());
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(scaled(0.1f, -0.4f, 0.0f));
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        geode.label = world.spawn(at.clone().add(0, 1.55, 0), TextDisplay.class, spawned -> {
            spawned.text(label(geode));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(110, 12, 10, 18));
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        Interaction hitbox = world.spawn(at.clone().add(0, -0.45, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.3f);
            spawned.setInteractionHeight(1.5f);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        geode.parts.add(geode.stone);
        geode.parts.add(geode.label);
        geode.parts.add(hitbox);
        byFinder.put(geode.finder, geode);
        for (Entity part : geode.parts) {
            byPart.put(part.getUniqueId(), geode);
        }

        // Emerge: grows out of the rock face with a shake.
        Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
            if (!geode.stone.isValid()) {
                return;
            }
            geode.stone.setInterpolationDelay(0);
            geode.stone.setInterpolationDuration(EMERGE_TICKS);
            geode.stone.setTransformation(scaled(1.15f, 0.25f, 0.0f));
        }, 2L);

        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(at, Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
        world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.9f);
        world.spawnParticle(Particle.BLOCK, at, 40, 0.4, 0.3, 0.4, 0.1, Material.DEEPSLATE.createBlockData());
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.4, 0), 24, 0.45, 0.45, 0.45, 0.0,
                new Particle.DustOptions(ore.glow(), 1.6f));
        String where = veins ? " §8(The Veins)" : "";
        finder.sendMessage("§d✦ Crystal Find! §fA " + grade.colored() + " " + ore.color() + ore.crystalName()
                + " §7(" + MineText.carats(carats) + ")" + where + " §eStrike it open §7— " + cracks + " cracks.");
        MineText.bar(finder, "§d§l✦ " + ore.crystalName() + " §f" + MineText.carats(carats) + " §7— mine it open!");
        if (grade == Grade.HEARTSTONE) {
            world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 0.6f);
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.6, 0), 30, 0.3, 0.8, 0.3, 0.05);
        }
        geode.task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), () -> tick(geode), 1L, 2L);
    }

    private Component label(Geode geode) {
        int left = Math.max(0, geode.cracksNeeded - geode.cracks);
        String cracks = "§f" + "▮".repeat(geode.cracks) + "§8" + "▯".repeat(left);
        return Component.text(geode.ore.crystalName().toUpperCase(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(MineText.legacy(geode.grade.colored() + " §8· §f" + MineText.carats(geode.carats)))
                .append(Component.newline())
                .append(MineText.legacy(cracks + " §7strike it!"));
    }

    private void tick(Geode geode) {
        if (geode.done) {
            return;
        }
        int age = Bukkit.getCurrentTick() - geode.bornTick;
        if (geode.stone != null && geode.stone.isValid() && age > EMERGE_TICKS + 2) {
            float spin = (age * 0.06f) % ((float) Math.PI * 2.0f);
            float bob = 0.25f + (float) Math.sin(age * 0.18d) * 0.05f;
            float size = 1.15f - 0.12f * geode.cracks;
            geode.stone.setInterpolationDelay(0);
            geode.stone.setInterpolationDuration(2);
            geode.stone.setTransformation(scaled(Math.max(0.5f, size), bob, spin));
        }
        if (age % 12 < 2 && geode.at.getWorld() != null) {
            geode.at.getWorld().spawnParticle(Particle.DUST, geode.at.clone().add(0, 0.5, 0), 3, 0.4, 0.4, 0.4, 0.0,
                    new Particle.DustOptions(geode.ore.glow(), 1.0f));
        }
        if (age >= FINDER_TICKS && !geode.announced) {
            geode.announced = true;
            Player finder = Bukkit.getPlayer(geode.finder);
            if (finder != null) {
                MineText.bar(finder, "§e" + geode.ore.crystalName() + " §7is fair game now — anyone can swing!");
            }
        }
        if (age >= LIFE_TICKS) {
            sink(geode);
        }
    }

    private void sink(Geode geode) {
        if (geode.done) {
            return;
        }
        World world = geode.at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.BLOCK, geode.at, 25, 0.35, 0.3, 0.35, 0.1, Material.DEEPSLATE.createBlockData());
            world.playSound(geode.at, Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 0.9f, 0.5f);
        }
        Player finder = Bukkit.getPlayer(geode.finder);
        if (finder != null) {
            MineText.bar(finder, "§7The " + geode.ore.crystalName() + " sank back into the rock. §8Swing faster next time.");
        }
        remove(geode);
    }

    private void remove(Geode geode) {
        geode.done = true;
        if (geode.task != null) {
            geode.task.cancel();
        }
        for (Entity part : geode.parts) {
            byPart.remove(part.getUniqueId());
            if (part.isValid()) {
                part.remove();
            }
        }
        byFinder.remove(geode.finder, geode);
    }

    // ------------------------------------------------------------------ striking

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPunch(EntityDamageByEntityEvent event) {
        Geode geode = byPart.get(event.getEntity().getUniqueId());
        if (geode == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            strike(player, geode);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Geode geode = byPart.get(event.getRightClicked().getUniqueId());
        if (geode == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            strike(event.getPlayer(), geode);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (byPart.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void strike(Player player, Geode geode) {
        if (geode.done || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        Integer last = lastStrike.get(player.getUniqueId());
        if (last != null && now - last < STRIKE_GAP_TICKS) {
            return;
        }
        lastStrike.put(player.getUniqueId(), now);
        int age = now - geode.bornTick;
        boolean finder = player.getUniqueId().equals(geode.finder);
        if (!finder && age < FINDER_TICKS) {
            MineText.bar(player, "§7Finder's dibs — §e" + ((FINDER_TICKS - age) / 20 + 1) + "s§7.");
            return;
        }
        if (!holdingPickaxe(player)) {
            MineText.bar(player, "§cCrystals crack for pickaxes only.");
            return;
        }
        geode.cracks++;
        World world = geode.at.getWorld();
        float pitch = 0.7f + 0.15f * geode.cracks;
        if (world != null) {
            world.playSound(geode.at, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0f, pitch);
            world.playSound(geode.at, Sound.BLOCK_STONE_HIT, SoundCategory.PLAYERS, 0.8f, pitch);
            world.spawnParticle(Particle.BLOCK, geode.at.clone().add(0, 0.4, 0), 14, 0.3, 0.3, 0.3, 0.1,
                    geode.ore.showcase().createBlockData());
        }
        if (geode.cracks < geode.cracksNeeded) {
            if (geode.label != null && geode.label.isValid()) {
                geode.label.text(label(geode));
            }
            if (geode.stone != null && geode.stone.isValid()) {
                // A jolt on every crack.
                geode.stone.setInterpolationDelay(0);
                geode.stone.setInterpolationDuration(1);
                geode.stone.setTransformation(scaled(1.25f - 0.12f * geode.cracks, 0.35f, 0.4f * geode.cracks));
            }
            return;
        }
        claim(player, geode, finder);
    }

    private static boolean holdingPickaxe(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        return hand.getType().name().endsWith("_PICKAXE") || org.bukkit.Tag.ITEMS_PICKAXES.isTagged(hand.getType());
    }

    private void claim(Player player, Geode geode, boolean finder) {
        remove(geode);
        ItemStack specimen = item(geode.ore, geode.grade, geode.carats);
        MineSkills.give(player, specimen);
        int xp = switch (geode.grade) {
            case ROUGH -> 40;
            case FLAWLESS -> 70;
            case PERFECT -> 120;
            case HEARTSTONE -> 300;
        };
        MineSkills.bonus(player, xp);
        MineProfiles.Profile profile = isle.profiles().of(player);
        profile.crystals++;
        if (geode.carats > profile.bestCarat) {
            profile.bestCarat = geode.carats;
            profile.bestOre = geode.ore;
            profile.bestGrade = geode.grade;
        }
        isle.profiles().markDirty();

        World world = geode.at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FIREWORK, geode.at.clone().add(0, 0.6, 0), 30, 0.4, 0.4, 0.4, 0.08);
            world.spawnParticle(Particle.DUST, geode.at.clone().add(0, 0.6, 0), 30, 0.5, 0.5, 0.5, 0.0,
                    new Particle.DustOptions(geode.ore.glow(), 1.8f));
            world.playSound(geode.at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
            world.playSound(geode.at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        String steal = finder ? "" : " §8(claim-jumped!)";
        player.sendMessage("§d✦ " + geode.grade.colored() + " " + geode.ore.color() + geode.ore.crystalName()
                + " §7(" + MineText.carats(geode.carats) + ")" + steal + " §8· §7worth ~§6"
                + MineText.coins(value(geode.ore, geode.grade, geode.carats)) + " coins §7at the Assayer. §a+" + xp + " Mining XP");
        if (!finder) {
            Player original = Bukkit.getPlayer(geode.finder);
            if (original != null) {
                original.sendMessage("§7" + player.getName() + " cracked your " + geode.ore.crystalName()
                        + " open first. §8Mining is a contact sport.");
            }
        }
        isle.ledger().onSpecimen(player, geode.ore, geode.grade);
        if (isle.profiles().offerRecord(geode.ore, player, geode.carats, geode.grade)) {
            String line = "§d✦ Isle record! §f" + player.getName() + " §7cracked a §d" + MineText.carats(geode.carats)
                    + " " + geode.ore.crystalName() + "§7 — the heaviest on Eldervale.";
            for (Player visitor : MineWorld.visitors(isle.plugin())) {
                visitor.sendMessage(line);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 0.9f);
            isle.props().refresh();
        }
        if (geode.grade == Grade.HEARTSTONE) {
            Component shout = MineText.legacy("§6✦ " + player.getName() + " §7pulled a §6Heartstone §7"
                    + geode.ore.crystalName() + " out of The Veins. §8The mountain is going to want that back.");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(shout);
            }
        }
    }

    // ------------------------------------------------------------------ items

    public ItemStack item(IsleOre ore, Grade grade, double carats) {
        ItemStack item = new ItemStack(ore.showcase());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        double rounded = Math.round(carats * 100.0d) / 100.0d;
        meta.setDisplayName(grade.color() + "✦ " + ore.color() + ore.crystalName() + " §8(" + grade.colored() + "§8)");
        List<String> lore = new ArrayList<>();
        lore.add("§7A " + ore.display().toLowerCase() + " specimen from Eldervale's rock.");
        lore.add("§7Weight: §f" + MineText.carats(rounded) + " §8(" + band(ore, grade, rounded) + "§8)");
        lore.add("");
        lore.add("§7Sells for §6" + MineText.coins(value(ore, grade, rounded)) + " coins §7at the §dAssayer§7.");
        lore.add("§7Fills specimen Contracts · brews Geode Broth.");
        lore.add("§7Registers in your §dSpecimen Cabinet§7.");
        lore.add("");
        lore.add(grade == Grade.HEARTSTONE ? "§6Heartstone §8· only The Veins grow these" : "§8Crystal Find · " + grade.display());
        lore.add("§8Can't be placed, crafted or burned.");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(specimenKey, PersistentDataType.STRING, ore.id());
        pdc.set(gradeKey, PersistentDataType.STRING, grade.id());
        pdc.set(caratKey, PersistentDataType.DOUBLE, rounded);
        item.setItemMeta(meta);
        return item;
    }

    public IsleOre oreOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return IsleOre.byId(item.getItemMeta().getPersistentDataContainer().get(specimenKey, PersistentDataType.STRING));
    }

    public Grade gradeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Grade.byId(item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING));
    }

    public double caratsOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0d;
        }
        Double carats = item.getItemMeta().getPersistentDataContainer().get(caratKey, PersistentDataType.DOUBLE);
        return carats == null ? 0.0d : carats;
    }

    public boolean isSpecimen(ItemStack item) {
        return oreOf(item) != null;
    }

    /** Family base × grade × how close the weight sits to the family's top. */
    public static long value(IsleOre ore, Grade grade, double carats) {
        double span = Math.max(0.01d, ore.maxCarat() - ore.minCarat());
        double share = Math.max(0.0d, Math.min(1.0d, (carats / gradeScale(grade) - ore.minCarat()) / span));
        long base = switch (ore) {
            case COAL, COPPER -> 160L;
            case IRON, REDSTONE -> 200L;
            case LAPIS -> 220L;
            case GOLD, QUARTZ -> 260L;
            case AMETHYST -> 300L;
            case DIAMOND -> 420L;
            case EMERALD -> 480L;
            case DEBRIS -> 600L;
            default -> 120L;
        };
        return Math.round(base * grade.valueMult() * (0.6d + share));
    }

    private static double gradeScale(Grade grade) {
        return switch (grade) {
            case ROUGH -> 1.0d;
            case FLAWLESS -> 1.35d;
            case PERFECT -> 1.8d;
            case HEARTSTONE -> 2.6d;
        };
    }

    public long valueOf(ItemStack item) {
        IsleOre ore = oreOf(item);
        Grade grade = gradeOf(item);
        return ore == null || grade == null ? 0L : value(ore, grade, caratsOf(item)) * item.getAmount();
    }

    private static String band(IsleOre ore, Grade grade, double carats) {
        double share = (carats / gradeScale(grade) - ore.minCarat()) / Math.max(0.01d, ore.maxCarat() - ore.minCarat());
        if (share >= 0.9d) {
            return "§6museum-grade";
        }
        if (share >= 0.6d) {
            return "§ehefty";
        }
        if (share >= 0.3d) {
            return "§fsolid";
        }
        return "§7modest";
    }

    private static Transformation scaled(float scale, float lift, float spin) {
        return new Transformation(
                new Vector3f(0.0f, lift, 0.0f),
                new AxisAngle4f(spin, 0.0f, 1.0f, 0.0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
        );
    }

    // ------------------------------------------------------------------ specimen safety

    /** Specimens are block-shaped trophies — never real blocks, never crafting input, never fuel. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isSpecimen(event.getItemInHand()) || isle.hearth().rationOf(event.getItemInHand()) != null) {
            event.setCancelled(true);
            MineText.bar(event.getPlayer(), "§7Specimens stay in one piece. §8Sell or register them at the Assayer.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isSpecimen(stack) || isle.hearth().rationOf(stack) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (isSpecimen(event.getFuel())) {
            event.setCancelled(true);
        }
    }
}
