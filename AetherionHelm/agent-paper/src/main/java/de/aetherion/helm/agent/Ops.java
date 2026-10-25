package de.aetherion.helm.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Requests from the app, answered with {t:res}. Anything touching the world runs on the main thread. */
final class Ops {
    private final HelmAgent agent;

    Ops(HelmAgent agent) {
        this.agent = agent;
    }

    void handle(JsonObject req) {
        long id = req.get("id").getAsLong();
        String op = req.get("op").getAsString();
        JsonObject args = req.has("args") ? req.getAsJsonObject("args") : new JsonObject();
        agent.async(() -> {
            JsonObject res = new JsonObject();
            res.addProperty("t", "res");
            res.addProperty("id", id);
            try {
                JsonElement data = switch (op) {
                    case "inventory" -> sync(() -> inventory(args));
                    case "command" -> sync(() -> command(args));
                    case "complete" -> sync(() -> complete(args));
                    case "kick" -> sync(() -> kick(args));
                    case "message" -> sync(() -> message(args));
                    case "broadcast" -> sync(() -> broadcast(args));
                    case "entities" -> sync(this::entities);
                    default -> throw new IllegalArgumentException("unknown op " + op);
                };
                res.addProperty("ok", true);
                res.add("data", data == null ? new JsonObject() : data);
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                res.addProperty("ok", false);
                res.addProperty("error", c.getMessage() == null ? c.toString() : c.getMessage());
            }
            agent.link().send(res);
        });
    }

    private <T> T sync(Callable<T> c) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(agent, c).get(15, TimeUnit.SECONDS);
    }

    private static Player player(JsonObject args) {
        Player p = Bukkit.getPlayer(UUID.fromString(args.get("uuid").getAsString()));
        if (p == null) throw new IllegalStateException("Spieler ist nicht (mehr) auf diesem Server");
        return p;
    }

    private JsonElement inventory(JsonObject args) {
        Player p = player(args);
        JsonObject o = new JsonObject();
        o.addProperty("uuid", p.getUniqueId().toString());
        o.addProperty("name", p.getName());
        PlayerInventory inv = p.getInventory();
        o.add("inventory", Items.list(inv.getStorageContents(), 0));
        JsonArray armor = new JsonArray();
        org.bukkit.inventory.ItemStack[] a = inv.getArmorContents();   // boots, legs, chest, helmet
        for (int i = 3; i >= 0; i--) {
            JsonObject j = Items.item(a[i], 3 - i, 0);
            armor.add(j == null ? com.google.gson.JsonNull.INSTANCE : j);
        }
        o.add("armor", armor);
        JsonObject off = Items.item(inv.getItemInOffHand(), 0, 0);
        if (off != null) o.add("offhand", off);
        o.add("enderchest", Items.list(p.getEnderChest().getContents(), 0));
        o.addProperty("selected", inv.getHeldItemSlot());
        o.addProperty("health", Math.round(p.getHealth() * 10) / 10.0);
        var mh = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        o.addProperty("maxHealth", mh == null ? 20 : mh.getValue());
        o.addProperty("food", p.getFoodLevel());
        o.addProperty("saturation", Math.round(p.getSaturation() * 10) / 10.0);
        o.addProperty("level", p.getLevel());
        o.addProperty("exp", Math.round(p.getExp() * 100) / 100.0);
        o.addProperty("gamemode", p.getGameMode().name().toLowerCase());
        Location l = p.getLocation();
        o.addProperty("world", l.getWorld() == null ? "?" : l.getWorld().getName());
        o.addProperty("x", Math.round(l.getX()));
        o.addProperty("y", Math.round(l.getY()));
        o.addProperty("z", Math.round(l.getZ()));
        JsonArray fx = new JsonArray();
        for (PotionEffect e : p.getActivePotionEffects()) {
            JsonObject j = new JsonObject();
            j.addProperty("type", e.getType().getKey().getKey());
            j.addProperty("amp", e.getAmplifier());
            j.addProperty("dur", e.getDuration());
            fx.add(j);
        }
        o.add("effects", fx);
        return o;
    }

    private JsonElement command(JsonObject args) {
        String cmd = args.get("command").getAsString();
        String by = args.has("by") ? args.get("by").getAsString() : "Helm";
        agent.getLogger().info(by + " (Helm) ran: " + cmd);
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        JsonObject o = new JsonObject();
        o.addProperty("dispatched", ok);
        return o;
    }

    private JsonElement complete(JsonObject args) {
        String line = args.has("line") ? args.get("line").getAsString() : "";
        JsonArray a = new JsonArray();
        List<String> r = Bukkit.getCommandMap().tabComplete(Bukkit.getConsoleSender(), line);
        if (r != null) r.stream().limit(40).forEach(a::add);
        return a;
    }

    private JsonElement kick(JsonObject args) {
        player(args).kick(Activity.legacy(args.get("reason").getAsString()));
        return null;
    }

    private JsonElement message(JsonObject args) {
        Player p = player(args);
        if (args.has("from")) {
            p.sendMessage(Component.text()
                    .append(Component.text("✉ ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("Team · " + args.get("from").getAsString(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(args.get("text").getAsString(), NamedTextColor.WHITE)));
        } else {
            p.sendMessage(Activity.legacy(args.get("text").getAsString()));
        }
        return null;
    }

    private JsonElement broadcast(JsonObject args) {
        Bukkit.broadcast(Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Team", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(args.get("from").getAsString(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(args.get("text").getAsString(), NamedTextColor.WHITE))
                .build());
        return null;
    }

    /** The lag radar: entity mix per world and the busiest chunks. */
    private JsonElement entities() {
        JsonObject o = new JsonObject();
        JsonArray worlds = new JsonArray();
        Map<String, Integer> total = new HashMap<>();
        List<JsonObject> hot = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Map<String, Integer> byType = new HashMap<>();
            for (Entity e : w.getEntities()) byType.merge(e.getType().getKey().getKey(), 1, Integer::sum);
            for (Chunk c : w.getLoadedChunks()) {
                int ents = c.getEntities().length, tiles = c.getTileEntities().length;
                if (ents + tiles < 25) continue;
                JsonObject h = new JsonObject();
                h.addProperty("world", w.getName());
                h.addProperty("x", c.getX());
                h.addProperty("z", c.getZ());
                h.addProperty("entities", ents);
                h.addProperty("tiles", tiles);
                Map<String, Integer> mix = new HashMap<>();
                for (Entity e : c.getEntities()) mix.merge(e.getType().getKey().getKey(), 1, Integer::sum);
                mix.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(t -> h.addProperty("top", t.getKey() + " ×" + t.getValue()));
                hot.add(h);
            }
            JsonObject wo = new JsonObject();
            wo.addProperty("name", w.getName());
            wo.addProperty("entities", w.getEntityCount());
            wo.addProperty("chunks", w.getChunkCount());
            wo.addProperty("tiles", w.getTileEntityCount());
            wo.add("types", top(byType, 12));
            worlds.add(wo);
            byType.forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        hot.sort((a, b) -> Integer.compare(b.get("entities").getAsInt() + b.get("tiles").getAsInt(), a.get("entities").getAsInt() + a.get("tiles").getAsInt()));
        JsonArray hotArr = new JsonArray();
        hot.stream().limit(12).forEach(hotArr::add);
        o.add("worlds", worlds);
        o.add("types", top(total, 16));
        o.add("hot", hotArr);
        return o;
    }

    private static JsonArray top(Map<String, Integer> m, int n) {
        JsonArray a = new JsonArray();
        m.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(n).forEach(e -> {
            JsonObject j = new JsonObject();
            j.addProperty("type", e.getKey());
            j.addProperty("count", e.getValue());
            a.add(j);
        });
        return a;
    }
}
