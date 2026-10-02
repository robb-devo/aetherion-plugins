package de.aetherion.stressbots.role;

import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-bot working activity. Dedicated roles stay on themselves.
 * General bots switch islands through {@code /botfocus} so they actually
 * use the real mine/forage/combat/… pads instead of walking the void.
 */
public final class BotFocusService {

    private static final String[] GENERAL_PROFILES = {
            "explorer", "farmer", "miner", "fighter", "fisher", "general"
    };

    private static final BotRole[] GENERAL_START = {
            BotRole.MINE, BotRole.FORAGE, BotRole.COMBAT, BotRole.FISH,
            BotRole.TRADE, BotRole.QUEST, BotRole.ROAM, BotRole.CATCH
    };

    private final AetherionStressBots plugin;
    private final Map<UUID, BotRole> focus = new ConcurrentHashMap<>();

    public BotFocusService(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public BotRole identity(Player player) {
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        return handler == null ? null : handler.role();
    }

    public BotRole focusOf(Player player) {
        BotRole identity = identity(player);
        if (identity == null) {
            return null;
        }
        if (identity != BotRole.GENERAL) {
            return identity;
        }
        return focus.getOrDefault(player.getUniqueId(), seedFocus(player));
    }

    public BotRole ensure(Player player) {
        BotRole identity = identity(player);
        if (identity == null) {
            return null;
        }
        if (identity != BotRole.GENERAL) {
            return identity;
        }
        return focus.computeIfAbsent(player.getUniqueId(), id -> seedFocus(player));
    }

    public boolean setFocus(Player player, BotRole activity) {
        if (player == null || activity == null || !activity.startable() || activity == BotRole.GENERAL) {
            return false;
        }
        focus.put(player.getUniqueId(), activity);
        plugin.getActivity().markProfile(player, profileOf(player), activity.id());
        Location dest = destination(player);
        if (dest != null) {
            player.teleport(dest);
            plugin.getActivity().markAction(player, "focus " + activity.id() + " @ " + BotLocations.format(dest));
            plugin.getActivity().markActivity(player, activity.id(), "arrive " + activity.id());
        }
        return dest != null;
    }

    public Location destination(Player player) {
        BotRole working = ensure(player);
        if (working == null) {
            return null;
        }
        ConfigurationSection section = sectionOf(working);
        if (section == null) {
            BotRoleHandler handler = plugin.getRegistry().handler(working);
            return handler == null ? null : handler.destination(player);
        }
        return BotLocations.pickAnchor(player, section);
    }

    public Location recoverPad(Player player, boolean otherPad) {
        BotRole working = ensure(player);
        ConfigurationSection section = sectionOf(working);
        if (otherPad) {
            Location other = BotLocations.otherAnchor(player, section, player.getLocation());
            if (other != null) {
                return other;
            }
        }
        Location assigned = BotLocations.assignedAnchor(player, section);
        if (assigned != null) {
            return assigned;
        }
        return destination(player);
    }

    public ConfigurationSection sectionOf(BotRole role) {
        if (role == null) {
            return null;
        }
        ConfigurationSection qa = BotRoleRegistry.roleSection(plugin, role);
        if (qa != null) {
            return qa;
        }
        return plugin.getConfig().getConfigurationSection(role.id());
    }

    public ConfigurationSection workingSection(Player player) {
        return sectionOf(ensure(player));
    }

    public String profileOf(Player player) {
        BotRole identity = identity(player);
        if (identity == null) {
            return "";
        }
        if (identity == BotRole.GENERAL) {
            return GENERAL_PROFILES[Math.floorMod(hash(player.getName()), GENERAL_PROFILES.length)];
        }
        return switch (identity) {
            case MINE, MINING -> "miner";
            case FORAGE -> "farmer";
            case CATCH -> "catcher";
            case COMBAT -> "fighter";
            case FISH -> "fisher";
            case TRADE -> "trader";
            case QUEST -> "explorer";
            case ROAM, PAD -> "wanderer";
            default -> identity.id();
        };
    }

    public void clear(Player player) {
        if (player != null) {
            focus.remove(player.getUniqueId());
        }
    }

    private BotRole seedFocus(Player player) {
        String profile = profileOf(player);
        BotRole[] pool = switch (profile) {
            case "explorer" -> new BotRole[] {BotRole.ROAM, BotRole.QUEST, BotRole.FORAGE, BotRole.PAD, BotRole.FISH};
            case "farmer" -> new BotRole[] {BotRole.FORAGE, BotRole.FISH, BotRole.CATCH, BotRole.MINE};
            case "miner" -> new BotRole[] {BotRole.MINE, BotRole.FORAGE, BotRole.TRADE};
            case "fighter" -> new BotRole[] {BotRole.COMBAT, BotRole.ROAM, BotRole.CATCH};
            case "fisher" -> new BotRole[] {BotRole.FISH, BotRole.ROAM, BotRole.FORAGE};
            default -> GENERAL_START;
        };
        return pool[Math.floorMod(hash(player.getName()) / 7, pool.length)];
    }

    static int hash(String name) {
        return name == null ? 0 : name.hashCode();
    }

    public static BotRole parseActivity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        BotRole role = BotRole.fromId(raw.toLowerCase(Locale.ROOT).trim());
        if (role == null || role == BotRole.GENERAL || !role.startable()) {
            return null;
        }
        return role;
    }
}
