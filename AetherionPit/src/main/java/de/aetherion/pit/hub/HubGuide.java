package de.aetherion.pit.hub;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;
import de.aetherion.pit.npc.HubNpcService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * What a new player reads on the hub: the welcome board over the plaza, a plate over each station, the Pit board
 * at the red line with the top fighters, the join title and chat card, and the modpack links.
 */
public final class HubGuide {

    private static final TextColor PURPLE = TextColor.color(0xB06CFF);
    private static final TextColor PINK = TextColor.color(0xF08CFF);
    private static final TextColor GREEN = TextColor.color(0x7EE07E);
    private static final TextColor RED = TextColor.color(0xFF5A5A);
    private static final TextColor BLUE = TextColor.color(0x8C9BFF);
    private static final TextColor GOLD = TextColor.color(0xFFC857);
    private static final TextColor SOFT = TextColor.color(0xC9C2D6);
    private static final int PLATE_BG = 0x50100A1C;
    private static final int BOARD_BG = 0x60100A1C;

    private final AetherionPit plugin;
    private final HubHolograms holograms;
    private List<PitDataStore.Ranked> top = List.of();

    public HubGuide(AetherionPit plugin) {
        this.plugin = plugin;
        this.holograms = new HubHolograms(plugin);
    }

    public void start() {
        Location board = location("welcome-board");
        if (board != null) {
            holograms.put("welcome", board, 1.15f, 280, BOARD_BG, welcomeBoard(false), welcomeBoard(true));
        }
        top = plugin.data().top(5);
        Location pitBoard = location("pit-board");
        if (pitBoard != null) {
            holograms.put("pit_board", pitBoard, 1.0f, 240, BOARD_BG, pitBoard(false), pitBoard(true));
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTop, 1200L, 1200L);
    }

    public void stop() {
        holograms.removeAll();
    }

    // ------------------------------------------------------------------ signs

    private Component welcomeBoard(boolean de) {
        return Component.text()
                .append(HubGlyphs.glyph(HubGlyphs.EMBLEM_BIG)).append(Component.text("\n\n\n\n"))
                .append(gradient("AETHERION", PURPLE, PINK).decorate(TextDecoration.BOLD)).append(nl())
                .append(Component.text(de ? "Der Hub - das Tor zum Netzwerk" : "The Hub - gate to the network",
                        NamedTextColor.WHITE)).append(nl())
                .append(Component.text("- - - - - - -", NamedTextColor.DARK_GRAY)).append(nl())
                .append(HubGlyphs.glyph(HubGlyphs.ISLAND)).append(Component.text(" Aetherion", PURPLE))
                .append(Component.text(de ? ": das MMO, lila NPC geradeaus" : ": the MMO, purple NPC ahead", SOFT))
                .append(nl())
                .append(HubGlyphs.glyph(HubGlyphs.SWORD)).append(Component.text(" The Pit", RED))
                .append(Component.text(de ? ": PvP hinter der roten Linie" : ": PvP past the red line", SOFT))
                .append(nl())
                .append(HubGlyphs.glyph(HubGlyphs.CRAFT)).append(Component.text(" Modpack & Shader", GREEN))
                .append(Component.text("  ", SOFT))
                .append(HubGlyphs.glyph(HubGlyphs.DISCORD)).append(Component.text(" Discord", BLUE))
                .append(Component.text("  ", SOFT))
                .append(HubGlyphs.glyph(HubGlyphs.SHOP)).append(Component.text(de ? " Pit-Shop" : " Pit Shop", GOLD))
                .build();
    }

    /** One plate per station, just above the body (FancyNpcs shows no name tag). */
    public void placePlates() {
        for (HubNpcService.Station station : HubNpcService.STATIONS) {
            Location at = plugin.hubNpcs().locationOf(station.key());
            if (at == null) {
                continue;
            }
            at = at.clone().add(0, 2.2, 0);
            holograms.put("plate_" + station.key(), at, 0.85f, 220, PLATE_BG,
                    plate(station.key(), false), plate(station.key(), true));
        }
    }

    public void refreshAetherionPlate() {
        holograms.text("plate_aetherion", plate("aetherion", false), plate("aetherion", true));
    }

    private Component plate(String key, boolean de) {
        return switch (key) {
            case "aetherion" -> {
                int online = plugin.hubNpcs().aetherionOnline();
                String sub = online >= 0
                        ? (de ? "Das MMO - " : "The MMO - ") + online + " online"
                        : (de ? "Das MMO - Inseln, Quests, Bosse" : "The MMO - isles, quests, bosses");
                yield plate(HubGlyphs.ISLAND, gradient("AETHERION", PURPLE, PINK), sub,
                        de ? "Klicken zum Reisen" : "Click to travel", PURPLE);
            }
            case "modpack" -> plate(HubGlyphs.CRAFT, Component.text("Modpack & Shader", GREEN),
                    de ? "Launcher, Mods & AetherionLight" : "Launcher, mods & AetherionLight",
                    de ? "Klicken für die Links" : "Click for the links", GREEN);
            case "pit" -> {
                String sub = top.isEmpty()
                        ? (de ? "PvP-Arena - Vanilla-Gear" : "PvP arena - vanilla gear")
                        : "#1 " + top.getFirst().name() + " - Lv " + top.getFirst().level();
                yield plate(HubGlyphs.SWORD, Component.text("THE PIT", RED), sub,
                        de ? "Klicken zum Betreten" : "Click to enter", RED);
            }
            case "discord" -> plate(HubGlyphs.DISCORD, Component.text("Discord", BLUE),
                    de ? "News, Hilfe & Bug-Reports" : "News, help & bug reports",
                    de ? "Klicken für die Einladung" : "Click for the invite", BLUE);
            default -> plate(HubGlyphs.SHOP, Component.text(de ? "Pit-Shop" : "Pit Shop", GOLD),
                    de ? "Ausrüstung für Kill-Gold" : "Gear for kill gold",
                    de ? "Klicken zum Handeln" : "Click to trade", GOLD);
        };
    }

    private static Component plate(String glyph, Component title, String sub, String action, TextColor accent) {
        return Component.text()
                .append(HubGlyphs.glyph(glyph)).append(Component.space())
                .append(title.decorate(TextDecoration.BOLD)).append(nl())
                .append(Component.text(sub, SOFT)).append(nl())
                .append(Component.text("> ", NamedTextColor.DARK_GRAY)).append(Component.text(action, accent))
                .build();
    }

    private void refreshTop() {
        top = plugin.data().top(5);
        holograms.text("plate_pit", plate("pit", false), plate("pit", true));
        holograms.text("pit_board", pitBoard(false), pitBoard(true));
    }

    private Component pitBoard(boolean de) {
        int killXp = plugin.getConfig().getInt("levels.kill-xp", 12);
        int killGold = plugin.getConfig().getInt("economy.kill-gold", 8);
        int deathXp = plugin.getConfig().getInt("levels.death-xp-loss", 4);
        TextComponent.Builder b = Component.text()
                .append(HubGlyphs.glyph(HubGlyphs.SWORD)).append(Component.space())
                .append(Component.text("THE PIT", RED, TextDecoration.BOLD)).append(nl())
                .append(Component.text(de ? "PvP hinter der roten Linie - Vanilla-Gear"
                        : "PvP past the red line - vanilla gear", NamedTextColor.WHITE)).append(nl())
                .append(Component.text(de
                        ? "Kill: +" + killXp + " XP, +" + killGold + " Gold - Tod: -" + deathXp + " XP"
                        : "Kill: +" + killXp + " XP, +" + killGold + " gold - Death: -" + deathXp + " XP", SOFT))
                .append(nl())
                .append(Component.text(de ? "Ohne Rüstung? Du bekommst ein Starter-Kit."
                        : "No gear? You get a starter kit.", SOFT)).append(nl())
                .append(Component.text(de ? "- Beste Kämpfer -" : "- Top fighters -", GOLD));
        if (top.isEmpty()) {
            b.append(nl()).append(Component.text(de ? "Noch frei. Sei der Erste!" : "Still empty. Be the first!",
                    NamedTextColor.GRAY));
        }
        for (int i = 0; i < top.size(); i++) {
            PitDataStore.Ranked r = top.get(i);
            b.append(nl())
                    .append(Component.text((i + 1) + ". ", i == 0 ? GOLD : NamedTextColor.GRAY))
                    .append(Component.text(r.name(), NamedTextColor.WHITE))
                    .append(Component.text("  Lv " + r.level(), RED));
        }
        return b.build();
    }

    // ------------------------------------------------------------------ join + chat

    /** Title and a short card of clickable destinations. Replaces the hub join title and the old hint lines. */
    public void welcome(Player player, boolean firstJoin) {
        boolean de = HubHolograms.german(player);
        player.showTitle(Title.title(
                gradient("AETHERION", PURPLE, PINK).decorate(TextDecoration.BOLD),
                Component.text(de ? "Willkommen im Hub" : "Welcome to the Hub", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2600), Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.1f);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(" ").append(HubGlyphs.glyph(HubGlyphs.EMBLEM)).append(Component.space())
                .append(gradient("Aetherion Hub", PURPLE, PINK).decorate(TextDecoration.BOLD))
                .append(Component.text(de ? "  das Tor zum Netzwerk" : "  gate to the network", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" ")
                .append(button(de ? "Aetherion spielen" : "Play Aetherion", PURPLE, "/aetherion",
                        de ? "Zum MMO: Inseln, Quests, Bosse" : "To the MMO: isles, quests, bosses"))
                .append(Component.space())
                .append(button("The Pit", RED, "/pit enter",
                        de ? "PvP-Arena hinter der roten Linie" : "PvP arena past the red line"))
                .append(Component.space())
                .append(button("Modpack & Shader", GREEN, "/modpack",
                        de ? "Launcher, Mods und AetherionLight" : "Launcher, mods and AetherionLight"))
                .append(Component.space())
                .append(button("Discord", BLUE, "/pit discord",
                        de ? "News, Hilfe und Bug-Reports" : "News, help and bug reports")));
        if (firstJoin) {
            player.sendMessage(Component.text(de
                    ? " Neu hier? Geh zum lila NPC geradeaus, dort beginnt dein Abenteuer."
                    : " New here? Walk to the purple NPC straight ahead, your adventure starts there.", SOFT));
        }
        player.sendMessage(Component.empty());
    }

    public void modpackCard(Player player) {
        boolean de = HubHolograms.german(player);
        ConfigurationSection links = plugin.getConfig().getConfigurationSection("modpack");
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(" ").append(HubGlyphs.glyph(HubGlyphs.CRAFT)).append(Component.space())
                .append(Component.text("Modpack & Shader", GREEN, TextDecoration.BOLD)));
        player.sendMessage(Component.text(de
                ? " Spiel mit normalem Minecraft 1.21.1 oder nimm unseren Launcher mit Mods und Shader."
                : " Play on plain Minecraft 1.21.1, or take our launcher with mods and shader.", SOFT));
        player.sendMessage(Component.text(" ")
                .append(link("GitHub Launcher", links, "github", de)).append(Component.space())
                .append(link("Modrinth", links, "modrinth", de)).append(Component.space())
                .append(link("CurseForge", links, "curseforge", de)));
        player.sendMessage(Component.text(" Shader ", NamedTextColor.GRAY)
                .append(Component.text("AetherionLight ", GOLD))
                .append(link("Download", links, "shader", de)));
        player.sendMessage(Component.empty());
    }

    /** The Pit NPC and {@code /pit enter}: stand at the red line, facing out. */
    public void enterPit(Player player) {
        Location at = location("pit-entrance");
        if (at == null) {
            return;
        }
        boolean de = HubHolograms.german(player);
        player.teleport(at);
        player.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.8f, 0.8f);
        player.showTitle(Title.title(
                Component.text("THE PIT", RED, TextDecoration.BOLD),
                Component.text(de ? "Hinter der roten Linie ist PvP an" : "PvP is live past the red line", SOFT),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(600))));
    }

    public void topCard(Player player) {
        boolean de = HubHolograms.german(player);
        player.sendMessage(Component.text(de ? "Beste Kämpfer in The Pit" : "Top fighters in The Pit", GOLD,
                TextDecoration.BOLD));
        if (top.isEmpty()) {
            player.sendMessage(Component.text(de ? " Noch niemand." : " Nobody yet.", NamedTextColor.GRAY));
        }
        for (int i = 0; i < top.size(); i++) {
            PitDataStore.Ranked r = top.get(i);
            player.sendMessage(Component.text(" " + (i + 1) + ". ", NamedTextColor.GRAY)
                    .append(Component.text(r.name(), NamedTextColor.WHITE))
                    .append(Component.text("  Lv " + r.level(), RED)));
        }
    }

    // ------------------------------------------------------------------ helpers

    private Component link(String label, ConfigurationSection links, String key, boolean de) {
        String url = links == null ? "" : links.getString(key, "").trim();
        if (url.isEmpty() || !url.startsWith("http")) {
            return Component.text("[" + label + (de ? ": bald]" : ": soon]"), NamedTextColor.DARK_GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text(de ? "Kommt bald" : "Coming soon",
                            NamedTextColor.GRAY)));
        }
        return Component.text("[" + label + "]", GREEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)));
    }

    private static Component button(String label, TextColor color, String command, String hover) {
        return Component.text("[" + label + "]", color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private static Component nl() {
        return Component.newline();
    }

    private static Component gradient(String text, TextColor from, TextColor to) {
        TextComponent.Builder b = Component.text();
        int n = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            b.append(Component.text(text.charAt(i), TextColor.lerp(i / (float) n, from, to)));
        }
        return b.build();
    }

    private Location location(String path) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) {
            return null;
        }
        World world = Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw", 0), (float) sec.getDouble("pitch", 0));
    }
}
