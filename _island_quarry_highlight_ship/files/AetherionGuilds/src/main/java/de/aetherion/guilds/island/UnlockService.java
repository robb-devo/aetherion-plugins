package de.aetherion.guilds.island;

import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The unlock beats. Gates stay where Items puts them (Island Lv20, Guild Lv75); this is the presentation:
 * <ul>
 *   <li><b>Foreshadow</b> a few levels early (via PlaceholderAPI's %aetherion_level%): a gull's note / a harbour
 *   clerk, and {@code /island} shows the three starters as a locked preview.</li>
 *   <li><b>Unlock</b>: title, chime, totem burst, a clickable "Choose your starter".</li>
 *   <li><b>Claim</b>: "The island rises…" while the starter pastes, then you drift down onto it (slow falling),
 *   fanfare, and a three-line "first steps" card.</li>
 *   <li><b>Guild</b>: founding raises the Guild Harbour and drops the founder onto it ("We have a place."); every
 *   member's first arrival gets its own beat.</li>
 * </ul>
 * Who has seen what is kept in {@code personal_islands.yml} under {@code unlock.*}.
 */
public final class UnlockService {

    private final JavaPlugin plugin;
    private final PersonalIslandService personal;
    private final GuildService guilds;
    private final IslandService islands;
    private final Set<UUID> islandAnnounced = ConcurrentHashMap.newKeySet();
    private final Set<UUID> guildAnnounced = ConcurrentHashMap.newKeySet();
    private final Set<UUID> islandHinted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> guildHinted = ConcurrentHashMap.newKeySet();

    public UnlockService(JavaPlugin plugin, PersonalIslandService personal, GuildService guilds, IslandService islands) {
        this.plugin = plugin;
        this.personal = personal;
        this.guilds = guilds;
        this.islands = islands;
        load();
        personal.setExtraSave(this::write);
    }

    // ------------------------------------------------------------------------------------------------
    // persistence (inside personal_islands.yml)
    // ------------------------------------------------------------------------------------------------

    private void load() {
        File file = new File(plugin.getDataFolder(), "personal_islands.yml");
        if (!file.exists()) {
            return;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("unlock");
        if (section == null) {
            return;
        }
        read(section.getStringList("island-announced"), islandAnnounced);
        read(section.getStringList("guild-announced"), guildAnnounced);
        read(section.getStringList("island-hinted"), islandHinted);
        read(section.getStringList("guild-hinted"), guildHinted);
    }

    private static void read(List<String> raw, Set<UUID> into) {
        for (String value : raw) {
            try {
                into.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void write(YamlConfiguration config) {
        config.set("unlock.island-announced", strings(islandAnnounced));
        config.set("unlock.guild-announced", strings(guildAnnounced));
        config.set("unlock.island-hinted", strings(islandHinted));
        config.set("unlock.guild-hinted", strings(guildHinted));
    }

    private static List<String> strings(Set<UUID> ids) {
        List<String> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            out.add(id.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // checks
    // ------------------------------------------------------------------------------------------------

    public void tick() {
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed |= check(player);
        }
        if (changed) {
            personal.save();
        }
    }

    public void onJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && check(player)) {
                personal.save();
            }
        }, 80L);
    }

    /** Returns true when something was recorded (caller saves). */
    private boolean check(Player player) {
        UUID id = player.getUniqueId();
        boolean changed = false;
        boolean islandOpen = AetherionItemsAccess.islandUnlocked(player);
        if (personal.byOwner(id) != null) {
            changed |= islandAnnounced.add(id);
        } else if (islandOpen) {
            if (islandAnnounced.add(id)) {
                announceIsland(player);
                changed = true;
            }
        } else if (!islandHinted.contains(id)) {
            int level = level(player);
            if (level >= plugin.getConfig().getInt("unlock.island-foreshadow-level", 15)) {
                islandHinted.add(id);
                foreshadowIsland(player);
                changed = true;
            }
        }
        boolean guildOpen = AetherionItemsAccess.guildUnlocked(player);
        if (guilds.byPlayer(id) != null) {
            changed |= guildAnnounced.add(id);
        } else if (guildOpen) {
            if (islandAnnounced.contains(id) && guildAnnounced.add(id)) {
                announceGuild(player);
                changed = true;
            }
        } else if (!guildHinted.contains(id)) {
            int level = level(player);
            if (level >= plugin.getConfig().getInt("unlock.guild-foreshadow-level", 65)) {
                guildHinted.add(id);
                foreshadowGuild(player);
                changed = true;
            }
        }
        return changed;
    }

    private static int level(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return -1;
        }
        try {
            String raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%aetherion_level%");
            String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // beats
    // ------------------------------------------------------------------------------------------------

    public void foreshadowIsland(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 0.9f, 1.5f);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        player.sendMessage("");
        player.sendMessage("§7A gull drops a salt-stained note at your feet:");
        player.sendMessage("§f“Past the reef there's a plot with no name on it yet.");
        player.sendMessage("§f Reach §eAetherion Level 20§f and it's yours.”");
        player.sendMessage(button("§b[Peek at the starters]", "/island", "§7Grove Camp, Quarry Outpost, Tide Dock"));
        player.sendMessage("");
        actionBar(player, "§6✦ §eYour own island · Aetherion Level 20 §6✦");
    }

    public void foreshadowGuild(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.6f, 0.8f);
        player.sendMessage("");
        player.sendMessage("§7Banners snap above the harbour. A clerk looks up from his ledger:");
        player.sendMessage("§f“Guild charters open at §eLevel 75§f. Find your people first.”");
        player.sendMessage("");
        actionBar(player, "§6⚑ §eGuilds · Aetherion Level 75");
    }

    public void announceIsland(Player player) {
        player.sendTitle("§6✦ Island Unlocked ✦", "§eA plot beyond the reef is yours.", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        later(10L, () -> player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.2f));
        later(22L, () -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f));
        burst(player.getLocation());
        spiral(player);
        player.sendMessage("");
        player.sendMessage("§6§l  ✦ YOUR ISLAND AWAITS ✦");
        player.sendMessage("§7  Three starters wait past the reef. Pick one,");
        player.sendMessage("§7  then grow it: land, huts, quarries, belts.");
        player.sendMessage(legacy("  ")
                .append(buttonComponent("§a§l[▶ Choose your starter]", "/island create", "§7Opens the starter picker"))
                .append(legacy("   "))
                .append(buttonComponent("§8[later: /island]", "/island", "§7Open it any time")));
        player.sendMessage("");
    }

    public void announceGuild(Player player) {
        player.sendTitle("§6⚑ Guilds Unlocked", "§eFound a guild. Raise a harbour.", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.2f);
        later(16L, () -> player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f));
        burst(player.getLocation());
        player.sendMessage("");
        player.sendMessage("§6§l  ⚑ GUILDS UNLOCKED");
        player.sendMessage("§7  Found one and your guild gets its own harbour isle,");
        player.sendMessage("§7  a project board, and a place to build together.");
        player.sendMessage(legacy("  ")
                .append(suggestComponent("§a§l[▶ Found a guild]", "/guild create ", "§7/guild create <name>"))
                .append(legacy("   "))
                .append(buttonComponent("§8[guild menu]", "/guild", "§7Invites, friends, how-to")));
        player.sendMessage("");
    }

    /** Starter picked: paste, then drift down onto it. */
    public void claim(Player player, PersonalIsland island, StarterLayout starter) {
        player.closeInventory();
        player.sendTitle("§7The island rises…", "§f" + starter.display(), 5, 80, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.7f);
        personal.whenBuilt(island, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location spawn = personal.spawn(island);
            descend(player, spawn, "§a✦ " + starter.display() + " §a✦", "§fYour island. Grow it.",
                    () -> firstSteps(player, starter));
        });
    }

    public void guildFounded(Player leader, Guild guild) {
        leader.sendTitle("§7Raising the harbour…", "§6" + guild.name(), 5, 80, 10);
        leader.playSound(leader.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.8f);
        guildAnnounced.add(leader.getUniqueId());
        islands.whenBuilt(guild, () -> {
            if (!leader.isOnline()) {
                return;
            }
            Location spawn = islands.spawn(guild);
            descend(leader, spawn, "§6⚑ " + guild.name(), "§fWe have a place.", () -> guildSteps(leader));
        });
    }

    public void guildArrival(Player player, Guild guild) {
        player.sendTitle("§6⚑ " + guild.name(), "§fOur place.", 10, 50, 15);
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1f);
        later(8L, () -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0);
    }

    private void descend(Player player, Location spawn, String title, String subtitle, Runnable after) {
        if (spawn == null) {
            return;
        }
        Location drop = spawn.clone().add(0, 14, 0);
        player.teleport(drop);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 8, 0, false, false, true));
        player.playSound(drop, Sound.ITEM_ELYTRA_FLYING, 0.3f, 1.4f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 5;
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.3, 0), 2, 0.3, 0.1, 0.3, 0.01);
                if (player.isOnGround() || ticks >= 160) {
                    cancel();
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    fanfare(player, title, subtitle);
                    if (after != null) {
                        later(50L, after);
                    }
                    personal.refreshFlight(player);
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    private void fanfare(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, 5, 60, 20);
        Location at = player.getLocation();
        player.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        later(6L, () -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f));
        later(14L, () -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f));
        burst(at);
        World world = at.getWorld();
        if (world != null) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                world.spawnParticle(Particle.FIREWORK, at.getX() + Math.cos(a) * 2.2, at.getY() + 0.3,
                        at.getZ() + Math.sin(a) * 2.2, 1, 0, 0.15, 0, 0.02);
            }
        }
    }

    private void firstSteps(Player player, StarterLayout starter) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§6§l  First steps on your " + starter.display());
        player.sendMessage(legacy("  §f1. §7Build your §fStorage Hut §7(free) on the marked site. ")
                .append(buttonComponent("§a[Build]", "/island build", "§7Opens blueprints")));
        player.sendMessage("  §f2. §7Place a quarry, lay a belt from its chute into the hut.");
        player.sendMessage(legacy("  §f3. §7Buy land to grow outward. ")
                .append(buttonComponent("§a[Expand]", "/island land", "§7Opens the land map")));
        player.sendMessage("");
    }

    private void guildSteps(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§6§l  Your Guild Harbour");
        player.sendMessage("  §7The §fproject board §7(lectern on the plaza) starts guild builds.");
        player.sendMessage(legacy("  §7Start the §fGuild Hall§7, everyone chips in: ")
                .append(buttonComponent("§a[Projects]", "/guild project", "§7Opens guild projects")));
        player.sendMessage(legacy("  §7Invite people: ")
                .append(suggestComponent("§a[/guild invite]", "/guild invite ", "§7/guild invite <player>")));
        player.sendMessage("");
    }

    // ------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------

    private void burst(Location at) {
        World world = at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 70, 0.6, 1.0, 0.6, 0.35);
        }
    }

    private void spiral(Player player) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || step++ > 30) {
                    cancel();
                    return;
                }
                Location at = player.getLocation();
                double a = step * 0.6;
                double y = step * 0.08;
                player.getWorld().spawnParticle(Particle.END_ROD, at.getX() + Math.cos(a) * 1.1, at.getY() + y,
                        at.getZ() + Math.sin(a) * 1.1, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    private static void actionBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private static Component button(String label, String command, String hover) {
        return buttonComponent(label, command, hover);
    }

    static Component buttonComponent(String label, String command, String hover) {
        return LegacyComponentSerializer.legacySection().deserialize(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)));
    }

    static Component suggestComponent(String label, String command, String hover) {
        return LegacyComponentSerializer.legacySection().deserialize(label)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)));
    }

    static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
