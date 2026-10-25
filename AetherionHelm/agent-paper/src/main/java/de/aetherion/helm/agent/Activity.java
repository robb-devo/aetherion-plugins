package de.aetherion.helm.agent;

import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

/** Chat, commands, deaths and joins for the activity feed; mute and ban enforcement on this backend. */
final class Activity implements Listener {
    private static final List<String> REDACT = List.of("login", "register", "l", "reg", "changepassword", "changepw");
    private final HelmAgent agent;

    Activity(HelmAgent agent) {
        this.agent = agent;
    }

    static Component legacy(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s);
    }

    static Component banScreen(ModState.Entry e) {
        return legacy("§c§lDu bist vom Aetherion-Netzwerk gebannt\n\n§7Grund: §f" + (e.reason() == null ? "Kein Grund angegeben" : e.reason())
                + "\n§7Dauer: §f" + ModState.until(e) + "\n\n§8Einspruch? Melde dich im Discord.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        ModState.Entry b = agent.mod().ban(e.getUniqueId());
        if (b != null) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(b));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatMute(AsyncChatEvent e) {
        ModState.Entry m = agent.mod().mute(e.getPlayer().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(muteNotice(m));
    }

    static Component muteNotice(ModState.Entry m) {
        return Component.text("Du bist gemutet ", NamedTextColor.RED)
                .append(Component.text("(" + ModState.until(m) + ")", NamedTextColor.GRAY))
                .append(m.reason() == null ? Component.empty() : Component.text(" · " + m.reason(), NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (agent.mod().mute(e.getPlayer().getUniqueId()) != null) return;
        JsonObject o = base("chat", e.getPlayer());
        o.addProperty("text", PlainTextComponentSerializer.plainText().serialize(e.message()));
        if (e.isCancelled()) o.addProperty("hidden", true);
        agent.event(o);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandMute(PlayerCommandPreprocessEvent e) {
        String label = label(e.getMessage());
        ModState.Entry m = agent.mod().mute(e.getPlayer().getUniqueId());
        if (m != null && agent.muteBlocked().contains(label)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(muteNotice(m));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String label = label(e.getMessage());
        String text = REDACT.contains(label) ? "/" + label + " ••••" : e.getMessage();
        JsonObject o = base("command", e.getPlayer());
        o.addProperty("text", text.length() > 300 ? text.substring(0, 300) + "…" : text);
        if (e.isCancelled()) o.addProperty("hidden", true);
        agent.event(o);
    }

    private static String label(String msg) {
        String l = msg.startsWith("/") ? msg.substring(1) : msg;
        l = l.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int c = l.indexOf(':');
        return c >= 0 ? l.substring(c + 1) : l;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Component msg = e.deathMessage();
        JsonObject o = base("death", e.getPlayer());
        o.addProperty("text", msg == null ? e.getPlayer().getName() + " ist gestorben" : PlainTextComponentSerializer.plainText().serialize(msg));
        agent.event(o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        JsonObject o = base("join", e.getPlayer());
        o.addProperty("text", e.getPlayer().getName() + " ist beigetreten");
        agent.event(o);
        agent.pushPlayersSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        JsonObject o = base("quit", e.getPlayer());
        o.addProperty("text", e.getPlayer().getName() + " hat den Server verlassen");
        agent.event(o);
        agent.pushPlayersSoon();
    }

    static JsonObject base(String kind, Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("uuid", p.getUniqueId().toString());
        o.addProperty("name", p.getName());
        return o;
    }
}
