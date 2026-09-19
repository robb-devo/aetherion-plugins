package de.aetherion.stressbots.role;

import de.aetherion.stressbots.AetherionStressBots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quirky in-game nicknames. Login names stay {@code QaMine01} for Velocity;
 * chat, tab list, death messages, Dev menu, and Collection leaderboards use these.
 */
public final class BotNicknames {

    private static final Map<BotRole, List<String>> DEFAULTS = Map.of(
            BotRole.MINE, List.of(
                    "§bPickel-Ute", "§bErz-Ernie", "§bSchacht-Stefan", "§bKohle-Karla", "§bAder-Achim",
                    "§bGruben-Gabi", "§bStollen-Sören", "§bFlinz-Frieda", "§bTiefen-Theo", "§bKies-Klaus",
                    "§bGestein-Greta", "§bBohr-Bernd", "§bErzgeist", "§bSchiefer-Susi", "§bHauer-Hans",
                    "§bAetherader", "§bKrummpickel", "§bVenen-Vera", "§bDusty-Dieter", "§bAderwächter"
            ),
            BotRole.FORAGE, List.of(
                    "§aAst-Anni", "§aLaub-Lutz", "§aCanopy-Kalle", "§aStamm-Steffi", "§aMiss-Axt",
                    "§aBirken-Bert", "§aEichen-Else", "§aRinden-Rudi", "§aForst-Fritze", "§aAetherholz",
                    "§aZweig-Zora", "§aKlotz-Kai", "§aHain-Hilde", "§aWurzel-Willi", "§aSchnitzi",
                    "§aKronen-Kira", "§aMoos-Moritz", "§aHolz-Heiner", "§aIsle-Ilse", "§aBlatt-Bärbel"
            ),
            BotRole.CATCH, List.of(
                    "§dKugel-Kai", "§dSphäre-Sven", "§dPet-Petra", "§dFang-Fiete", "§dHabitat-Hansi",
                    "§dNetz-Nadja", "§dGaff-Gustav", "§dFlucht-Felix", "§dAetherfang", "§dKnautsch-Kim",
                    "§dPfote-Pia", "§dWurf-Waldi", "§dZoo-Zelda", "§dMenagerie-Max", "§dSchnapp-Sandra",
                    "§dKäfig-Kurt", "§dPlüsch-Paul", "§dTreffer-Tine", "§dMiss-Catch", "§dKugelregen"
            ),
            BotRole.ROAM, List.of(
                    "§eFlaneur-Franz", "§eCapital-Claus", "§ePad-Poldi", "§eBummel-Bärbel", "§eAether-Tourist",
                    "§eHafen-Heike", "§ePflaster-Pit", "§eUmweg-Uwe", "§eGasse-Gundula", "§eSchlender-Sepp",
                    "§eOrigin-Otto", "§eBrücken-Britta", "§eMarkt-Manni", "§eIrrläufer", "§eHub-Hugo",
                    "§eEcken-Ella", "§eTorkel-Tim", "§eStadtgeist", "§eQuatsch-Quirin", "§eBummelant"
            ),
            BotRole.COMBAT, List.of(
                    "§cBorder-Bernd", "§cWaste-Wanda", "§cVex-Victim", "§cKlingen-Kai", "§cScharmützel"
            ),
            BotRole.MINING, List.of(
                    "§7Stress-Stollen", "§7Schacht-Bot", "§7Alte-Ader", "§7Mine-Marga", "§7Staub-Stefan"
            )
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
        int index = Math.floorMod(slotIndex(player.getName()), pool.size());
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
        return DEFAULTS.getOrDefault(role, List.of());
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
