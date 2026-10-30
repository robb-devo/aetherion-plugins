package de.aetherion.stressbots.role;

import de.aetherion.stressbots.AetherionStressBots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Player-like display names. Login names stay {@code QaMine01} for Velocity and
 * provisioning; chat, tab list, death messages, Dev menu, and Collection use these.
 * The pool is ordinary Minecraft-style names, offset per role so bots do not share a label.
 */
public final class BotNicknames {

    private static final List<String> PLAYER_NAMES = List.of(
            "MapleReed", "IronWade", "CinderFox", "NovaKite", "RowanVale",
            "CaseyBrook", "QuinnAsh", "AveryLane", "ParkerHolt", "ReeseWild",
            "SkylerNorth", "HaydenCole", "ElliotMarsh", "FinleyCrowe", "HarperGlen",
            "LoganPike", "BlakeYarrow", "CameronDusk", "DrewHollow", "EllisWard",
            "FrankieMoss", "GreerLane", "IndigoVale", "JulesHart", "KaiMercer",
            "LaneBishop", "MicahSol", "NoelArcher", "OakleyFinn", "PeytonRue",
            "RemyShaw", "SloaneVale", "TatumReed", "WinterHayes", "AugustKerr",
            "BlairMoss", "CedarQuinn", "WynnHollis", "SagePorter", "TheoMarlow",
            "NiaCalder", "OwenBriar", "LilaFen", "MiloCrest", "JunoHale",
            "ArloVoss", "EsmeCalder", "BodhiLane", "FreyaMoss", "NicoVale"
    );

    private final AetherionStressBots plugin;

    public BotNicknames(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("testbots.nicknames.enabled", true);
    }

    public String colored(Player player, BotRole role) {
        if (player == null || role == null || !enabled()) {
            return player == null ? "" : player.getName();
        }
        List<String> pool = pool(role);
        if (pool.isEmpty()) {
            return player.getName();
        }
        int index = Math.floorMod(slotIndex(player.getName()) + role.ordinal() * 7, pool.size());
        String nick = pool.get(index);
        return nick == null || nick.isBlank() ? player.getName() : nick;
    }

    public String plain(Player player, BotRole role) {
        return strip(colored(player, role));
    }

    public void apply(Player player, BotRole role) {
        if (player == null || !player.isOnline() || !enabled()) {
            return;
        }
        String colored = colored(player, role);
        Component component = LegacyComponentSerializer.legacySection().deserialize(colored);
        player.displayName(component);
        player.playerListName(component);
        player.customName(component);
        player.setCustomNameVisible(true);
    }

    public void applyLater(Player player, BotRole role) {
        apply(player, role);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apply(player, role);
            }
        }, 30L);
    }

    private List<String> pool(BotRole role) {
        List<String> configured = plugin.getConfig().getStringList("testbots.nicknames." + role.id());
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return PLAYER_NAMES;
    }

    static int slotIndex(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        int digits = 0;
        boolean any = false;
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if (c >= '0' && c <= '9') {
                any = true;
                digits = digits * 10 + (c - '0');
            }
        }
        if (any && digits > 0) {
            return digits - 1;
        }
        return Math.abs(username.toLowerCase(Locale.ROOT).hashCode());
    }

    public static String strip(String colored) {
        if (colored == null || colored.isBlank()) {
            return "";
        }
        return colored.replaceAll("§.", "");
    }
}
