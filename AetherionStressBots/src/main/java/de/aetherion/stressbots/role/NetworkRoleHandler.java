package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Wave 3: the roles that play the rest of the network. They are kitted once per session on the main world
 * (the character carries the kit to mmo-d, mmo-c and mmo-i) and travel by the roads players use; the runner
 * drives them. Destinations only matter for the roles that start on the main world.
 */
public final class NetworkRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;
    private final BotRole role;

    public NetworkRoleHandler(AetherionStressBots plugin, BotRole role) {
        this.plugin = plugin;
        this.role = role;
    }

    @Override
    public BotRole role() {
        return role;
    }

    @Override
    public String prefix() {
        String fallback = switch (role) {
            case GENERAL -> "QaGeneral";
            case FARM -> "QaFarm";
            case MENUS -> "QaMenus";
            case CASINO -> "QaCasino";
            case SOCIAL -> "QaSocial";
            case PIT -> "QaPit";
            case DUNGEON -> "QaDungeon";
            case EXILE -> "QaExile";
            case ISLAND -> "QaIsland";
            default -> "Qa" + role.id();
        };
        return BotRoleRegistry.prefixOf(plugin, role, fallback);
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, role);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        int tier = BotPlaystyle.gearTier(player);
        switch (role) {
            case GENERAL -> BotPlaystyle.kitAllRounder(inv, items, tier);
            case FARM -> BotPlaystyle.kitFarming(inv, items, tier);
            case DUNGEON, EXILE -> BotPlaystyle.kitCombat(inv, items, Math.max(2, tier), true);
            case ISLAND -> {
                BotPlaystyle.kitAllRounder(inv, items, tier);
                inv.addItem(new ItemStack(Material.OAK_PLANKS, 64));
                inv.addItem(new ItemStack(Material.COBBLESTONE, 64));
                inv.addItem(new ItemStack(Material.TORCH, 16));
            }
            case CASINO, MENUS, SOCIAL -> BotPlaystyle.kitCombat(inv, items, tier, false);
            default -> {
            }
        }
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, role));
    }

    @Override
    public String description() {
        return switch (role) {
            case GENERAL -> "Tours the whole network like a player: Pit, isles, market, quests, dungeons, exile, islands.";
            case FARM -> "Harvests ripe crops on the farm fields with a farming hoe.";
            case MENUS -> "Opens every player menu, pages through it and times how long each one takes to open.";
            case CASINO -> "Spins Lucky Vince's slots with pocket coins.";
            case SOCIAL -> "Builds parties and friend lists with other bots.";
            case PIT -> "Fights other bots in the hub's Pit (never people).";
            case DUNGEON -> "Takes the dungeon road to mmo-d, enters Floor 1, fights through and leaves.";
            case EXILE -> "Takes the ferry road to the exile (mmo-c), explores, fights and talks to people there.";
            case ISLAND -> "Crosses to mmo-i, claims a starter island, builds on it and visits other bots.";
            default -> role.id();
        };
    }
}
