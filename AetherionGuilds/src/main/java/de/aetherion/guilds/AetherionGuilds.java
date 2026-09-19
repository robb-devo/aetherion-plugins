package de.aetherion.guilds;

import de.aetherion.guilds.command.FriendCommand;
import de.aetherion.guilds.command.GuildCommand;
import de.aetherion.guilds.command.IslandCommand;
import de.aetherion.guilds.listener.GuildListener;
import de.aetherion.guilds.menu.BankMenu;
import de.aetherion.guilds.menu.BiomeSelectMenu;
import de.aetherion.guilds.menu.FriendMenu;
import de.aetherion.guilds.menu.GuildMenu;
import de.aetherion.guilds.menu.IslandMenu;
import de.aetherion.guilds.menu.QuarryMenu;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.placeholder.GuildPlaceholderExpansion;
import de.aetherion.guilds.service.FriendService;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.IslandService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public final class AetherionGuilds extends JavaPlugin {

    private static AetherionGuilds instance;

    private GuildService guilds;
    private IslandService islands;
    private PersonalIslandService personal;
    private MinionService minions;
    private FriendService friends;
    private GuildMenu menu;
    private IslandMenu islandMenu;
    private FriendMenu friendMenu;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        islands = new IslandService(this);
        islands.enable();
        personal = new PersonalIslandService(this);
        personal.enable();
        guilds = new GuildService(this, islands);
        minions = new MinionService(this, guilds, islands);
        minions.attachPersonal(personal);
        guilds.attachMinions(minions);
        personal.attachMinions(minions);
        friends = new FriendService(this, personal);
        friendMenu = new FriendMenu(friends, guilds);
        BiomeSelectMenu biomeMenu = new BiomeSelectMenu(personal);
        islandMenu = new IslandMenu(personal, minions, friendMenu, biomeMenu);
        menu = new GuildMenu(guilds, minions, islands, friendMenu);
        BankMenu bankMenu = new BankMenu(guilds, menu);
        menu.setBank(bankMenu);
        QuarryMenu quarryMenu = new QuarryMenu(guilds, personal, minions);

        getServer().getPluginManager().registerEvents(
                new GuildListener(guilds, islands, personal, minions, menu, islandMenu, biomeMenu, quarryMenu, friendMenu, friends, bankMenu),
                this
        );

        GuildCommand command = new GuildCommand(guilds, minions, menu);
        var guild = getCommand("guild");
        if (guild != null) {
            guild.setExecutor(command);
            guild.setTabCompleter(command);
        }
        IslandCommand islandCommand = new IslandCommand(personal, islandMenu, biomeMenu);
        var island = getCommand("island");
        if (island != null) {
            island.setExecutor(islandCommand);
            island.setTabCompleter(islandCommand);
        }
        FriendCommand friendCommand = new FriendCommand(friends, friendMenu);
        var friend = getCommand("friend");
        if (friend != null) {
            friend.setExecutor(friendCommand);
            friend.setTabCompleter(friendCommand);
        }

        getServer().getScheduler().runTaskLater(this, () -> {
            if (getConfig().getBoolean("reset-islands")) {
                guilds.wipeAll();
                personal.wipeAll();
                getConfig().set("reset-islands", false);
                saveConfig();
            }
            minions.purgeUnownedVisuals();
            minions.catchUpAll();
            minions.respawnVisuals();
        }, 40L);
        getServer().getScheduler().runTaskTimer(this, minions::tickVisuals, 20L, 4L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            minions.catchUpAll();
            guilds.save();
            personal.save();
            friends.save();
        }, 20L * 60, 20L * 60);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GuildPlaceholderExpansion(this).register();
        }

        getLogger().info("AetherionGuilds enabled. Private + guild island worlds, quarries tick while the server runs.");
    }

    @Override
    public void onDisable() {
        if (minions != null) {
            minions.catchUpAll();
        }
        if (guilds != null) {
            guilds.save();
        }
        if (personal != null) {
            personal.save();
        }
        if (friends != null) {
            friends.save();
        }
        instance = null;
    }

    public static AetherionGuilds getInstance() {
        return instance;
    }

    public GuildService getGuilds() {
        return guilds;
    }

    public IslandService getIslands() {
        return islands;
    }

    public PersonalIslandService getPersonalIslands() {
        return personal;
    }

    public MinionService getMinions() {
        return minions;
    }

    public FriendService getFriends() {
        return friends;
    }

    public Guild guildAt(Location location) {
        if (guilds == null || islands == null || location == null) {
            return null;
        }
        if (!islands.isGuildWorld(location.getWorld())) {
            return null;
        }
        return guilds.byPlot(islands.plotAt(location));
    }

    public PersonalIsland personalAt(Location location) {
        if (personal == null || location == null || !personal.isPersonalWorld(location.getWorld())) {
            return null;
        }
        return personal.byPlot(personal.plotAt(location));
    }

    public String islandTitle(Location location) {
        PersonalIsland personalIsland = personalAt(location);
        if (personalIsland != null) {
            org.bukkit.OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(personalIsland.ownerId());
            String name = owner.getName() == null ? "Island" : owner.getName();
            return name + "'s Island";
        }
        Guild guild = guildAt(location);
        return guild == null ? "Guild Island" : GuildFormat.islandTitle(guild.name());
    }

    public void openMenu(org.bukkit.entity.Player player) {
        if (menu != null && player != null) {
            menu.open(player);
        }
    }

    public void openIslandMenu(org.bukkit.entity.Player player) {
        if (islandMenu != null && player != null) {
            islandMenu.open(player);
        }
    }
}
