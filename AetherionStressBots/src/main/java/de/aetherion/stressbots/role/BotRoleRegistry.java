package de.aetherion.stressbots.role;

import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prefix → handler lookup for every known bot identity.
 */
public final class BotRoleRegistry {

    private final Map<BotRole, BotRoleHandler> handlers = new EnumMap<>(BotRole.class);

    public BotRoleRegistry(AetherionStressBots plugin) {
        register(new MineRoleHandler(plugin));
        register(new ForageRoleHandler(plugin));
        register(new CatchRoleHandler(plugin));
        register(new RoamRoleHandler(plugin));
        register(new CombatRoleHandler(plugin));
        register(new FishRoleHandler(plugin));
        register(new TradeRoleHandler(plugin));
        register(new QuestRoleHandler(plugin));
        register(new PadRoleHandler(plugin));
        register(new LegacyMiningRoleHandler(plugin));
    }

    private void register(BotRoleHandler handler) {
        handlers.put(handler.role(), handler);
    }

    public BotRoleHandler handler(BotRole role) {
        return role == null ? null : handlers.get(role);
    }

    public BotRoleHandler byPlayer(Player player) {
        if (player == null) {
            return null;
        }
        return byName(player.getName());
    }

    public BotRoleHandler byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        BotRoleHandler best = null;
        int bestLen = -1;
        for (BotRoleHandler handler : handlers.values()) {
            String prefix = handler.prefix();
            if (prefix != null && !prefix.isBlank() && lower.startsWith(prefix) && prefix.length() > bestLen) {
                best = handler;
                bestLen = prefix.length();
            }
            for (String extra : handler.prefixes()) {
                if (extra == null || extra.isBlank()) {
                    continue;
                }
                String alias = extra.toLowerCase(Locale.ROOT);
                if (lower.startsWith(alias) && alias.length() > bestLen) {
                    best = handler;
                    bestLen = alias.length();
                }
            }
        }
        return best;
    }

    public List<BotRoleHandler> startable() {
        List<BotRoleHandler> out = new ArrayList<>();
        for (BotRole role : BotRole.values()) {
            if (!role.startable()) {
                continue;
            }
            BotRoleHandler handler = handlers.get(role);
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    public List<BotRoleHandler> wave1() {
        List<BotRoleHandler> out = new ArrayList<>();
        for (BotRole role : BotRole.values()) {
            if (!role.wave1()) {
                continue;
            }
            BotRoleHandler handler = handlers.get(role);
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    public List<BotRoleHandler> all() {
        List<BotRoleHandler> out = new ArrayList<>();
        for (BotRole role : BotRole.values()) {
            BotRoleHandler handler = handlers.get(role);
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    static String prefixOf(AetherionStressBots plugin, BotRole role, String fallback) {
        String wave = plugin.getConfig().getString("testbots.prefixes." + role.id(), null);
        if (wave != null && !wave.isBlank()) {
            return wave.toLowerCase(Locale.ROOT);
        }
        String legacy = plugin.getConfig().getString("prefixes." + role.id(), fallback);
        return (legacy == null ? fallback : legacy).toLowerCase(Locale.ROOT);
    }

    static int capOf(AetherionStressBots plugin, BotRole role) {
        int configured = plugin.getConfig().getInt("testbots.caps." + role.id(), 20);
        int maxStart = plugin.getConfig().getInt("testbots.max-per-start", 20);
        return Math.max(0, Math.min(configured, maxStart));
    }

    public static ConfigurationSection roleSection(AetherionStressBots plugin, BotRole role) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("testbots.roles." + role.id());
        if (section != null) {
            return section;
        }
        return plugin.getConfig().getConfigurationSection(role.id());
    }

    static void giveSpare(PlayerInventory inv, ItemStack spare) {
        if (spare != null) {
            inv.addItem(spare.clone());
        }
    }
}
