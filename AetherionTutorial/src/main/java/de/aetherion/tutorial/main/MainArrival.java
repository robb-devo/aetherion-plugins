package de.aetherion.tutorial.main;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.ProgressAccess;
import de.aetherion.tutorial.TutorialPlugin;
import de.aetherion.tutorial.TutorialStore;
import de.aetherion.tutorial.Txt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main world's side of the ferry.
 * <ul>
 *     <li>Whoever steps off it (store says {@code graduating}) is welcomed to Anker Harbour: a purse, the first
 *     teleports, and one clear next step. The harbour's own orientation counts as done (the isle filed it before
 *     casting off), so Quests points them straight at Miss Ledger's stamp.</li>
 *     <li>Whoever turns up here while still in the middle of the island is taken back to it.</li>
 *     <li>{@code /tutorial}: a player who already has a character visits the island. The character is not taken
 *     along and not touched.</li>
 * </ul>
 */
public final class MainArrival implements Listener {

    private final TutorialPlugin plugin;
    private final Map<UUID, Long> asked = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sentBack = new ConcurrentHashMap<>();

    public MainArrival(TutorialPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.store().load(player.getUniqueId()).state == TutorialStore.State.GRADUATING) {
            // before the Hub's own first-join hint ("FIND EGON") goes out: Egon's welcome is behind this player
            quietHarbourHint(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> look(player, 0), 30L);
    }

    /**
     * AetherionHub greets whoever joins the main world for the first time with "FIND EGON". A graduate of the isle
     * has that lesson filed already; the hint is marked as shown (reflection: Hub is not a dependency of this
     * plugin, and without it nothing is lost).
     */
    private void quietHarbourHint(Player player) {
        try {
            org.bukkit.plugin.Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
            if (hub == null || !hub.isEnabled()) {
                return;
            }
            Object service = hub.getClass().getMethod("getHub").invoke(hub);
            if (service != null) {
                service.getClass().getMethod("markHintShown", UUID.class).invoke(service, player.getUniqueId());
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("Could not quiet the harbour hint: " + ex);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        asked.remove(event.getPlayer().getUniqueId());
    }

    private void look(Player player, int tries) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        CharacterTransport transport = transport();
        if (transport != null && (transport.isArriving(id) || transport.isHeld(id)) && tries < 40) {
            // the character is still on its way in (or already on its way out): wait for it
            Bukkit.getScheduler().runTaskLater(plugin, () -> look(player, tries + 1), 10L);
            return;
        }
        TutorialStore.Entry entry = plugin.store().load(id);
        switch (entry.state) {
            case GRADUATING -> welcome(player, entry);
            case IN_PROGRESS -> {
                if (entry.guest) {
                    // a visit that was dropped half-way: it simply ends
                    entry.state = TutorialStore.State.DONE;
                    entry.guest = false;
                    plugin.store().save(id, entry);
                } else if (sentBack.merge(id, 1, Integer::sum) <= 2) {
                    player.sendMessage(Txt.t(player, "§e✦ §7Your first morning on §fMorningbell §7is not over yet. Taking you back to the island…",
                            "§e✦ §7Dein erster Morgen auf §fMorningbell §7ist noch nicht vorbei. Es geht zurück auf die Insel…"));
                    plugin.toIsle(player, "");
                    // still here a while later: the island is closed. They may look around; the lobby takes
                    // them back to it another day
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && plugin.store().load(id).state == TutorialStore.State.IN_PROGRESS) {
                            player.sendMessage(Txt.t(player, "§e✦ §7The island is closed for the moment. Look around here if you like: §fPlay §7in the lobby takes you back to finish your first morning once it is open again.",
                                    "§e✦ §7Die Insel ist gerade geschlossen. Schau dich hier ruhig um: §fSpielen §7in der Lobby bringt dich zurück zu deinem ersten Morgen, sobald sie wieder offen ist."));
                            plugin.store().forget(id);
                        }
                    }, 320L);
                }
            }
            default -> {
            }
        }
        plugin.store().forget(id);
    }

    private static CharacterTransport transport() {
        AetherionCore core = AetherionCore.get();
        return core == null || core.router() == null ? null : core.router().transport();
    }

    // ------------------------------------------------------------------ off the ferry

    private void welcome(Player player, TutorialStore.Entry entry) {
        UUID id = player.getUniqueId();
        // first the paperwork, so a hiccup below can never pay twice
        entry.state = TutorialStore.State.DONE;
        entry.completedOnce = true;
        entry.guest = false;
        if (entry.finished <= 0L) {
            entry.finished = System.currentTimeMillis();
        }
        plugin.store().save(id, entry);
        sentBack.remove(id);

        List<String> opened = new ArrayList<>();
        HubAccess hub = AetherServices.hub();
        if (hub != null) {
            for (String spawn : plugin.getConfig().getStringList("main.spawns")) {
                if (spawn == null || spawn.isBlank()) {
                    continue;
                }
                try {
                    if (hub.unlock(player, spawn.trim().toLowerCase(java.util.Locale.ROOT))) {
                        opened.add(pretty(spawn));
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Could not open the teleport '" + spawn + "' for " + player.getName() + ": " + ex);
                }
            }
        }
        ProgressAccess progress = AetherServices.progress();
        if (progress != null) {
            if (!opened.isEmpty()) {
                progress.unlockSilent(player, "SPAWN_UNLOCKER");
            }
            progress.refreshManager(player);
        }
        int purse = Math.max(0, plugin.getConfig().getInt("main.purse", 300));
        CoinAccess coins = AetherServices.coins();
        if (coins != null && purse > 0) {
            coins.add(player, purse);
        }

        List<String> lines = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        if (purse > 0 && coins != null) {
            lines.add("§6+" + purse + " Coins");
            icons.add(new ItemStack(Material.GOLD_NUGGET));
        }
        if (!opened.isEmpty()) {
            lines.add(Txt.t(player, "§dTeleports: §f", "§dTeleports: §f") + String.join("§7, §f", opened));
            icons.add(new ItemStack(Material.ENDER_PEARL));
        }
        lines.add(Txt.t(player, "§7Everything you learnt on the island works here too.",
                "§7Alles, was du auf der Insel gelernt hast, gilt auch hier."));
        icons.add(new ItemStack(Material.WRITABLE_BOOK));
        String next = Txt.t(player, "Next: Miss Ledger in the Capital stamps your papers",
                "Als Nächstes: Miss Ledger in der Hauptstadt stempelt deine Papiere");
        String summary = "§6✦ §fAnker Harbour§8: §7"
                + (purse > 0 && coins != null ? "+" + purse + " Coins" : "")
                + (opened.isEmpty() ? "" : Txt.t(player, ", teleports: ", ", Teleports: ") + String.join(", ", opened));
        boolean shown = false;
        try {
            shown = de.aetherion.quests.api.Cards.big(player, Txt.t(player, "YOU MADE IT ACROSS", "DU BIST DRÜBEN"),
                    "Anker Harbour", lines, icons, next, "§6", summary);
        } catch (LinkageError | RuntimeException ignored) {
            // Quests is not here: the title below says it
        }
        if (!shown) {
            player.showTitle(Title.title(Component.text("ANKER HARBOUR", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Txt.legacy(Txt.t(player, "§7the mainland", "§7das Festland")),
                    Title.Times.times(Duration.ofMillis(600), Duration.ofMillis(2600), Duration.ofMillis(700))));
            player.sendMessage(summary);
            player.sendMessage("§7" + next);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.6f, 1.1f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.35f, 1.2f);
        plugin.getLogger().info("[Morningbell] " + player.getName() + " arrived from the island (" + (entry.seconds / 60L)
                + " min, " + (entry.skipped ? "short way" : "all lessons") + ").");
    }

    private static String pretty(String id) {
        String text = id.trim().replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ------------------------------------------------------------------ a visit to the island

    /** {@code /tutorial} on the main world: walk the island again, without the character. */
    public void visit(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = asked.get(id);
        if (last != null && now - last < 8000L) {
            return;
        }
        asked.put(id, now);
        CharacterTransport transport = transport();
        if (transport != null && (transport.isHeld(id) || transport.isArriving(id))) {
            return;
        }
        TutorialStore.Entry entry = plugin.store().load(id);
        if (entry.state == TutorialStore.State.IN_PROGRESS && !entry.guest) {
            plugin.toIsle(player, "");
            return;
        }
        entry.state = TutorialStore.State.IN_PROGRESS;
        entry.guest = true;
        entry.step = "";
        entry.name = player.getName();
        entry.started = now;
        entry.seconds = 0L;
        entry.flags.clear();
        plugin.store().save(id, entry);
        player.sendMessage(Txt.t(player, "§e✦ §7A visit to §fMorningbell§7. Your character stays here, untouched: on the island you start with empty pockets.",
                "§e✦ §7Ein Besuch auf §fMorningbell§7. Dein Charakter bleibt unangetastet hier: Auf der Insel startest du mit leeren Taschen."));
        if (!connect(player, plugin.isleServer())) {
            revoke(player, id);
            return;
        }
        // still here after a while: the island is closed, the ticket is void
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                revoke(player, id);
            }
        }, 200L);
    }

    private void revoke(Player player, UUID id) {
        TutorialStore.Entry entry = plugin.store().load(id);
        if (entry.state == TutorialStore.State.IN_PROGRESS && entry.guest) {
            entry.state = TutorialStore.State.DONE;
            entry.guest = false;
            plugin.store().save(id, entry);
            player.sendMessage(Txt.t(player, "§7The ferry to Morningbell is not sailing right now. Try again later.",
                    "§7Die Fähre nach Morningbell fährt gerade nicht. Versuch es später noch mal."));
        }
        plugin.store().forget(id);
    }

    /** A plain proxy connect: the character is packed by the normal quit and waits here. */
    private boolean connect(Player player, String server) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            return true;
        } catch (java.io.IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not walk " + player.getName() + " to " + server + ": " + ex);
            return false;
        }
    }
}
