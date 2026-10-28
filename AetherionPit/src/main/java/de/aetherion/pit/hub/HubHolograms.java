package de.aetherion.pit.hub;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hub signs as text displays, one per language: German clients see the German one, everyone else English.
 * Never saved with the chunk; respawned whenever their chunk is loaded and they are gone.
 */
public final class HubHolograms implements Listener {

    public static final String TAG = "ae_hub_holo";

    private final Plugin plugin;
    private final Map<String, Holo> holos = new LinkedHashMap<>();

    private static final class Holo {
        final Location at;
        final float scale;
        final int lineWidth;
        final int background;
        Component en;
        Component de;
        TextDisplay enEntity;
        TextDisplay deEntity;

        Holo(Location at, float scale, int lineWidth, int background, Component en, Component de) {
            this.at = at;
            this.scale = scale;
            this.lineWidth = lineWidth;
            this.background = background;
            this.en = en;
            this.de = de;
        }

        void remove() {
            if (enEntity != null) {
                enEntity.remove();
            }
            if (deEntity != null) {
                deEntity.remove();
            }
            enEntity = null;
            deEntity = null;
        }
    }

    public HubHolograms(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> holos.values().forEach(this::ensure), 40L, 40L);
    }

    public static boolean german(Player player) {
        Locale locale = player.locale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    /** Define or move a sign. {@code background} is ARGB (0 = none). */
    public void put(String id, Location at, float scale, int lineWidth, int background, Component en, Component de) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        Holo old = holos.remove(id);
        if (old != null) {
            old.remove();
        }
        Holo holo = new Holo(at.clone(), scale, lineWidth, background, en, de);
        holos.put(id, holo);
        ensure(holo);
    }

    public void text(String id, Component en, Component de) {
        Holo holo = holos.get(id);
        if (holo == null || (holo.en.equals(en) && holo.de.equals(de))) {
            return;
        }
        holo.en = en;
        holo.de = de;
        if (alive(holo.enEntity)) {
            holo.enEntity.text(en);
        }
        if (alive(holo.deEntity)) {
            holo.deEntity.text(de);
        }
    }

    public void removeAll() {
        holos.values().forEach(Holo::remove);
        holos.clear();
    }

    private void ensure(Holo holo) {
        World world = holo.at.getWorld();
        if (world == null || !world.isChunkLoaded(holo.at.getBlockX() >> 4, holo.at.getBlockZ() >> 4)) {
            return;
        }
        if (!alive(holo.enEntity)) {
            holo.enEntity = spawn(holo, holo.en, false);
        }
        if (!alive(holo.deEntity)) {
            holo.deEntity = spawn(holo, holo.de, true);
        }
    }

    private TextDisplay spawn(Holo holo, Component text, boolean germanCopy) {
        TextDisplay display = holo.at.getWorld().spawn(holo.at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(TAG);
            d.text(text);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(holo.lineWidth);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(holo.background));
            d.setViewRange(1.2f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(holo.scale, holo.scale, holo.scale), new AxisAngle4f()));
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (german(player) == germanCopy) {
                player.showEntity(plugin, display);
            }
        }
        return display;
    }

    private void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        boolean de = german(player);
        for (Holo holo : holos.values()) {
            show(player, holo.enEntity, !de);
            show(player, holo.deEntity, de);
        }
    }

    private void show(Player player, TextDisplay display, boolean visible) {
        if (!alive(display)) {
            return;
        }
        if (visible) {
            player.showEntity(plugin, display);
        } else {
            player.hideEntity(plugin, display);
        }
    }

    private static boolean alive(Entity entity) {
        return entity != null && entity.isValid();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(player), 5L);
    }

    /** The client sends its language a moment after joining. */
    @EventHandler
    public void onLocale(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refresh(player));
    }
}
