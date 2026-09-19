package de.aetherion.guilds.service;

import de.aetherion.guilds.model.BankTiers;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.model.IslandTiers;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GuildService {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _-]{3,24}");

    private final JavaPlugin plugin;
    private final IslandService islands;
    private final File file;
    private final Map<UUID, Guild> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerGuild = new ConcurrentHashMap<>();
    private MinionService minions;

    public GuildService(JavaPlugin plugin, IslandService islands) {
        this.plugin = plugin;
        this.islands = islands;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
        load();
    }

    public void attachMinions(MinionService minions) {
        this.minions = minions;
    }

    public void wipeAll() {
        Location spawn = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (islands.isGuildWorld(player.getWorld())) {
                islands.teleportHome(player, spawn);
                player.sendMessage("§7Guild islands were wiped. Start a new guild when you're ready.");
            }
        }
        if (minions != null) {
            for (Guild guild : List.copyOf(guilds.values())) {
                minions.removeVisuals(guild);
            }
            minions.purgeAllVisuals();
        }
        for (Guild guild : List.copyOf(guilds.values())) {
            islands.clearPlot(guild);
        }
        guilds.clear();
        playerGuild.clear();
        save();
        plugin.getLogger().info("Wiped all guilds, islands, and quarry visuals.");
    }

    public Collection<Guild> all() {
        return guilds.values();
    }

    public Guild byId(UUID id) {
        return id == null ? null : guilds.get(id);
    }

    public Guild byPlayer(UUID playerId) {
        UUID guildId = playerGuild.get(playerId);
        return guildId == null ? null : guilds.get(guildId);
    }

    public Guild byPlot(int plot) {
        for (Guild guild : guilds.values()) {
            if (guild.plot() == plot) {
                return guild;
            }
        }
        return null;
    }

    public Guild create(Player leader, String rawName) {
        if (leader == null) {
            return null;
        }
        if (byPlayer(leader.getUniqueId()) != null) {
            leader.sendMessage("§cYou already have a guild. Leave it first.");
            return null;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (!NAME.matcher(name).matches()) {
            leader.sendMessage("§cGuild names must be 3-24 letters, numbers, spaces, - or _.");
            return null;
        }
        if (byName(name) != null) {
            leader.sendMessage("§cThat guild name is taken.");
            return null;
        }
        Guild guild = new Guild(UUID.randomUUID(), name, nextPlot());
        guild.members().put(leader.getUniqueId(), GuildRank.LEADER);
        guilds.put(guild.id(), guild);
        playerGuild.put(leader.getUniqueId(), guild.id());
        islands.ensureBuilt(guild);
        save();
        leader.sendMessage("§aCreated guild §f" + name + "§a. Open §f/guild §ato visit your island.");
        return guild;
    }

    public void disband(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild.");
            return;
        }
        if (guild.rank(player.getUniqueId()) != GuildRank.LEADER) {
            player.sendMessage("§cOnly the leader can disband the guild.");
            return;
        }
        for (UUID member : new ArrayList<>(guild.members().keySet())) {
            playerGuild.remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null && islands.isGuildWorld(online.getWorld())) {
                islands.teleportHome(online, Bukkit.getWorlds().isEmpty() ? online.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation());
                online.sendMessage("§cThe guild §f" + guild.name() + " §cwas disbanded.");
            }
        }
        if (minions != null) {
            minions.removeVisuals(guild);
        }
        islands.clearPlot(guild);
        guilds.remove(guild.id());
        save();
        player.sendMessage("§7Guild disbanded. The island was cleared and the plot can be reused.");
    }

    public void invite(Player sender, Player target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null) {
            sender.sendMessage("§cYou are not in a guild.");
            return;
        }
        GuildRank rank = guild.rank(sender.getUniqueId());
        if (rank == null || !rank.canInvite()) {
            sender.sendMessage("§cYour rank cannot invite.");
            return;
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }
        if (byPlayer(target.getUniqueId()) != null) {
            sender.sendMessage("§cThat player is already in a guild.");
            return;
        }
        int max = plugin.getConfig().getInt("max-members", 50);
        if (guild.members().size() >= max) {
            sender.sendMessage("§cThe guild is full (" + max + ").");
            return;
        }
        long expire = System.currentTimeMillis() + plugin.getConfig().getInt("invite-seconds", 300) * 1000L;
        guild.invites().put(target.getUniqueId(), expire);
        save();
        sender.sendMessage("§aInvited §f" + target.getName() + " §ato §f" + guild.name() + "§a.");
        target.sendMessage("§b" + sender.getName() + " §7invited you to §f" + guild.name() + "§7.");
        target.sendMessage("§7Use §f/guild accept " + guild.name() + " §7or §f/guild deny§7.");
    }

    public void accept(Player player, String guildName) {
        if (byPlayer(player.getUniqueId()) != null) {
            player.sendMessage("§cYou already have a guild.");
            return;
        }
        Guild guild = guildName == null || guildName.isBlank() ? invitedGuild(player.getUniqueId()) : byName(guildName);
        if (guild == null) {
            player.sendMessage("§cNo invite found.");
            return;
        }
        Long expire = guild.invites().get(player.getUniqueId());
        if (expire == null || expire < System.currentTimeMillis()) {
            guild.invites().remove(player.getUniqueId());
            player.sendMessage("§cThat invite expired.");
            return;
        }
        guild.invites().remove(player.getUniqueId());
        guild.members().put(player.getUniqueId(), GuildRank.SOLDIER);
        playerGuild.put(player.getUniqueId(), guild.id());
        save();
        player.sendMessage("§aYou joined §f" + guild.name() + "§a.");
        broadcast(guild, "§a" + player.getName() + " §7joined the guild.");
    }

    public void deny(Player player) {
        boolean any = false;
        for (Guild guild : guilds.values()) {
            if (guild.invites().remove(player.getUniqueId()) != null) {
                any = true;
            }
        }
        if (any) {
            save();
            player.sendMessage("§7Invite declined.");
        } else {
            player.sendMessage("§cYou have no invites.");
        }
    }

    public void leave(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild.");
            return;
        }
        if (guild.rank(player.getUniqueId()) == GuildRank.LEADER) {
            player.sendMessage("§cLeaders must /guild disband or transfer later. Core has no transfer yet.");
            return;
        }
        guild.members().remove(player.getUniqueId());
        playerGuild.remove(player.getUniqueId());
        save();
        if (islands.isGuildWorld(player.getWorld())) {
            islands.teleportHome(player, Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        player.sendMessage("§7You left §f" + guild.name() + "§7.");
        broadcast(guild, "§c" + player.getName() + " §7left the guild.");
    }

    public void kick(Player sender, Player target) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null || target == null) {
            sender.sendMessage("§cUnknown player or guild.");
            return;
        }
        GuildRank rank = guild.rank(sender.getUniqueId());
        GuildRank other = guild.rank(target.getUniqueId());
        if (rank == null || other == null || !rank.canKick(other)) {
            sender.sendMessage("§cYou cannot kick that member.");
            return;
        }
        guild.members().remove(target.getUniqueId());
        playerGuild.remove(target.getUniqueId());
        save();
        if (islands.isGuildWorld(target.getWorld())) {
            islands.teleportHome(target, Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        sender.sendMessage("§7Kicked §f" + target.getName() + "§7.");
        target.sendMessage("§cYou were kicked from §f" + guild.name() + "§c.");
    }

    public void setRank(Player sender, Player target, String rawRank) {
        Guild guild = byPlayer(sender.getUniqueId());
        if (guild == null || target == null) {
            sender.sendMessage("§cUnknown player or guild.");
            return;
        }
        GuildRank actor = guild.rank(sender.getUniqueId());
        GuildRank current = guild.rank(target.getUniqueId());
        if (actor == null || current == null) {
            sender.sendMessage("§cThat player is not in your guild.");
            return;
        }
        GuildRank next = GuildRank.parse(rawRank);
        if (current == GuildRank.LEADER || next == GuildRank.LEADER) {
            sender.sendMessage("§cLeadership transfer comes later.");
            return;
        }
        if (actor != GuildRank.LEADER && !actor.canSetRank(current, next)) {
            sender.sendMessage("§cYou cannot set that rank.");
            return;
        }
        guild.members().put(target.getUniqueId(), next);
        save();
        sender.sendMessage("§aSet §f" + target.getName() + " §ato " + next.display() + "§a.");
        target.sendMessage("§7Your guild rank is now " + next.display() + "§7.");
    }

    public void goHome(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cCreate or join a guild first. §f/guild create <name>");
            return;
        }
        Location spawn = islands.spawn(guild);
        if (spawn == null) {
            player.sendMessage("§cThe guild island is not ready.");
            return;
        }
        player.teleport(spawn);
        player.sendMessage("§aWelcome to §f" + guild.name() + "§a's island.");
    }

    public void upgradeIsland(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cCreate or join a guild first.");
            return;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canUpgradeIsland()) {
            player.sendMessage("§cMayors and above can upgrade the island.");
            return;
        }
        if (!guild.canUpgradeIsland()) {
            player.sendMessage("§cThe island is already max level.");
            return;
        }
        int from = guild.islandLevel();
        IslandTiers.UpgradeCost cost = IslandTiers.upgradeCost(from);
        List<String> missing = new ArrayList<>();
        if (AetherionItemsAccess.count(player, "compacted_cobblestone") < cost.compactedCobble()) {
            missing.add("§f" + cost.compactedCobble() + " Compacted Cobblestone");
        }
        if (AetherionItemsAccess.count(player, "quarry_core") < cost.cores()) {
            missing.add("§d" + cost.cores() + " Quarry Core");
        }
        if (AetherionItemsAccess.coins(player) < cost.coins()) {
            missing.add("§6" + GuildFormat.compact(cost.coins()) + " Coins");
        }
        if (!missing.isEmpty()) {
            player.sendMessage("§cNeed " + String.join(" §7and §c", missing) + " §cto upgrade.");
            return;
        }
        AetherionItemsAccess.take(player, "compacted_cobblestone", cost.compactedCobble());
        AetherionItemsAccess.take(player, "quarry_core", cost.cores());
        AetherionItemsAccess.takeCoins(player, cost.coins());
        guild.setIslandLevel(from + 1);
        islands.expand(guild, from);
        save();
        broadcast(guild, "§a" + player.getName() + " upgraded the island to §fLv." + guild.islandLevel()
                + "§a. Build radius is now §f" + islands.buildRadius(guild) + "§a.");
    }

    public void upgradeBank(Player player) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cCreate or join a guild first.");
            return;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canUpgradeIsland()) {
            player.sendMessage("§cMayor or higher can upgrade the guild bank.");
            return;
        }
        if (!guild.canUpgradeBank()) {
            player.sendMessage("§cThe guild bank is fully upgraded.");
            return;
        }
        int from = guild.bankLevel();
        BankTiers.UpgradeCost cost = BankTiers.upgradeCost(from);
        if (cost.isEmpty()) {
            return;
        }
        if (AetherionItemsAccess.count(player, cost.itemId()) < cost.amount()) {
            player.sendMessage("§cNeed §f" + cost.label() + " §cto upgrade the bank.");
            return;
        }
        AetherionItemsAccess.take(player, cost.itemId(), cost.amount());
        guild.setBankLevel(from + 1);
        save();
        broadcast(guild, "§a" + player.getName() + " upgraded the guild bank to §fLv." + guild.bankLevel()
                + "§a. Coin cap is now §6" + GuildFormat.compact(guild.coinCap())
                + "§a with §f" + guild.bankSlots() + " §aitem slots.");
    }

    public long depositCoins(Player player, long amount) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null || player == null || amount <= 0L) {
            return 0L;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canBankDeposit()) {
            player.sendMessage("§cYou cannot deposit into the guild bank.");
            return 0L;
        }
        long room = Math.max(0L, guild.coinCap() - guild.bankCoins());
        long take = Math.min(amount, Math.min(room, AetherionItemsAccess.coins(player)));
        if (take <= 0L) {
            player.sendMessage(room <= 0L ? "§cThe guild bank is full." : "§cYou don't have enough coins.");
            return 0L;
        }
        if (!AetherionItemsAccess.takeCoins(player, take)) {
            player.sendMessage("§cYou don't have enough coins.");
            return 0L;
        }
        guild.setBankCoins(guild.bankCoins() + take);
        save();
        player.sendMessage("§aDeposited §6" + GuildFormat.compact(take) + " §acoins. Bank: §f"
                + GuildFormat.compact(guild.bankCoins()) + "§8/§7" + GuildFormat.compact(guild.coinCap()));
        return take;
    }

    public long withdrawCoins(Player player, long amount) {
        Guild guild = byPlayer(player.getUniqueId());
        if (guild == null || player == null || amount <= 0L) {
            return 0L;
        }
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canBankWithdraw()) {
            player.sendMessage("§cSoldiers and above can withdraw from the bank.");
            return 0L;
        }
        long give = Math.min(amount, guild.bankCoins());
        if (give <= 0L) {
            player.sendMessage("§cThe guild bank has no coins.");
            return 0L;
        }
        guild.setBankCoins(guild.bankCoins() - give);
        AetherionItemsAccess.addCoins(player, give);
        save();
        player.sendMessage("§aWithdrew §6" + GuildFormat.compact(give) + " §acoins. Bank: §f"
                + GuildFormat.compact(guild.bankCoins()));
        return give;
    }

    public void broadcast(Guild guild, String message) {
        for (UUID id : guild.members().keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public Guild byName(String name) {
        if (name == null) {
            return null;
        }
        for (Guild guild : guilds.values()) {
            if (guild.name().equalsIgnoreCase(name.trim())) {
                return guild;
            }
        }
        return null;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Guild guild : guilds.values()) {
            String path = "guilds." + guild.id();
            config.set(path + ".name", guild.name());
            config.set(path + ".plot", guild.plot());
            config.set(path + ".island-built", guild.islandBuilt());
            config.set(path + ".island-level", guild.islandLevel());
            config.set(path + ".bank.level", guild.bankLevel());
            config.set(path + ".bank.coins", guild.bankCoins());
            for (int slot = 0; slot < Guild.BANK_MAX_SLOTS; slot++) {
                ItemStack item = guild.bank()[slot];
                if (item != null && !item.getType().isAir()) {
                    config.set(path + ".bank.items." + slot, item);
                }
            }
            for (Map.Entry<UUID, GuildRank> entry : guild.members().entrySet()) {
                config.set(path + ".members." + entry.getKey(), entry.getValue().name());
            }
            for (Map.Entry<UUID, Long> entry : guild.invites().entrySet()) {
                config.set(path + ".invites." + entry.getKey(), entry.getValue());
            }
            for (QuarryMinion minion : guild.minions()) {
                String m = path + ".minions." + minion.id();
                config.set(m + ".type", minion.type());
                config.set(m + ".x", minion.x());
                config.set(m + ".y", minion.y());
                config.set(m + ".z", minion.z());
                config.set(m + ".lastTick", minion.lastTick());
                config.set(m + ".stored", minion.stored());
                config.set(m + ".storedCompressed", minion.storedCompressed());
                config.set(m + ".storedCompacted", minion.storedCompacted());
                config.set(m + ".level", minion.level());
                config.set(m + ".processor", minion.processor().name());
            }
        }
        try {
            de.aetherion.core.persist.AtomicYaml.save(config, file, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save guilds.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("guilds");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Guild guild = new Guild(id, section.getString("name", "Guild"), section.getInt("plot"));
            guild.setIslandBuilt(section.getBoolean("island-built"));
            guild.setIslandLevel(section.getInt("island-level", 1));
            ConfigurationSection bankSection = section.getConfigurationSection("bank");
            guild.setBankLevel(bankSection == null ? section.getInt("bank.level", 0) : bankSection.getInt("level", 0));
            guild.setBankCoins(section.getLong("bank.coins"));
            ConfigurationSection bankItems = section.getConfigurationSection("bank.items");
            if (bankItems != null) {
                for (String slotKey : bankItems.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        if (slot < 0 || slot >= Guild.BANK_MAX_SLOTS) {
                            continue;
                        }
                        ItemStack item = bankItems.getItemStack(slotKey);
                        if (item != null) {
                            guild.bank()[slot] = item;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            ConfigurationSection members = section.getConfigurationSection("members");
            if (members != null) {
                for (String memberKey : members.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(memberKey);
                        GuildRank rank = GuildRank.parse(members.getString(memberKey, "FOOTMAN"));
                        guild.members().put(playerId, rank);
                        playerGuild.put(playerId, guild.id());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection invites = section.getConfigurationSection("invites");
            if (invites != null) {
                for (String inviteKey : invites.getKeys(false)) {
                    try {
                        guild.invites().put(UUID.fromString(inviteKey), invites.getLong(inviteKey));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection minions = section.getConfigurationSection("minions");
            if (minions != null) {
                for (String minionKey : minions.getKeys(false)) {
                    try {
                        ConfigurationSection minion = minions.getConfigurationSection(minionKey);
                        if (minion == null) {
                            continue;
                        }
                        guild.minions().add(new QuarryMinion(
                                UUID.fromString(minionKey),
                                minion.getString("type", QuarryMinion.COBBLE),
                                minion.getInt("x"),
                                minion.getInt("y"),
                                minion.getInt("z"),
                                minion.getLong("lastTick"),
                                minion.getInt("stored"),
                                minion.getInt("level", 1),
                                QuarryMinion.Processor.parse(minion.getString("processor")),
                                minion.getInt("storedCompressed"),
                                minion.getInt("storedCompacted")
                        ));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            guilds.put(guild.id(), guild);
        }
    }

    private Guild invitedGuild(UUID playerId) {
        Guild found = null;
        for (Guild guild : guilds.values()) {
            Long expire = guild.invites().get(playerId);
            if (expire != null && expire >= System.currentTimeMillis()) {
                if (found != null) {
                    return found;
                }
                found = guild;
            }
        }
        return found;
    }

    private int nextPlot() {
        Set<Integer> used = new HashSet<>();
        for (Guild guild : guilds.values()) {
            used.add(guild.plot());
        }
        int plot = 0;
        while (used.contains(plot)) {
            plot++;
        }
        return plot;
    }
}
