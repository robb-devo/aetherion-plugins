package de.aetherion.stressbots;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.QuestProgressAccess;
import de.aetherion.core.network.NetworkRouter;
import de.aetherion.core.network.RouteBook;
import de.aetherion.stressbots.role.BotLocations;
import de.aetherion.stressbots.role.BotPlaystyle;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;
import de.aetherion.stressbots.role.BotRoleRegistry;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;

import java.util.List;
import java.util.Locale;

/**
 * {@code /testbot …}: the test bots' side door into the server. Only bots can run it (offline UUID or an old
 * {@code QaMine01}-style name; the permission is attached on join, so people never see the command). Every answer
 * is one plain chat line starting with {@code [tb]} that the runner reads; nothing is broadcast.
 *
 * <ul>
 *   <li>{@code hello <role> <index> [setup] [session]}: who I am on this backend; {@code setup} kits me and
 *       sends me to my role's spot. Answer: {@code [tb] hello server=… world=… role=… setup=yes|no x y z}.</li>
 *   <li>{@code where}: server, world and position.</li>
 *   <li>{@code stage <spot>}: teleport to a live Hub spawn ({@code farm}, {@code capital} …) or a role's spot.</li>
 *   <li>{@code near <hostile|npc|villager|animal|chest|item|name:text> [radius]}: the closest one, server side
 *       (a dungeon's next mob, a loot cache, an NPC by name).</li>
 *   <li>{@code quest}: where the bot's open quest points.</li>
 *   <li>{@code goto <server>}: the fallback road when a player road failed (Core's travel pipeline).</li>
 * </ul>
 */
public final class TestBotCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "aetherion.testbot.self";
    private static final List<String> SUBS = List.of("hello", "where", "stage", "near", "quest", "goto");

    private final AetherionStressBots plugin;

    public TestBotCommand(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !plugin.getProvisioner().isStressBot(player)) {
            sender.sendMessage("Unknown command. Type \"/help\" for help.");
            return true;
        }
        String sub = args.length == 0 ? "where" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "hello" -> hello(player, args);
            case "where" -> reply(player, "where " + here(player));
            case "stage" -> stage(player, args.length < 2 ? "" : args[1]);
            case "near" -> near(player, args.length < 2 ? "hostile" : args[1], args.length < 3 ? 48 : parse(args[2], 48));
            case "quest" -> quest(player);
            case "goto" -> travel(player, args.length < 2 ? "main" : args[1]);
            default -> reply(player, "error unknown " + sub);
        }
        return true;
    }

    private void hello(Player player, String[] args) {
        BotRole role = args.length < 2 ? null : BotRole.fromId(args[1]);
        if (role == null) {
            reply(player, "hello error role");
            return;
        }
        int index = args.length < 3 ? 1 : parse(args[2], 1);
        boolean setup = false;
        String session = "";
        for (int i = 3; i < args.length; i++) {
            if ("setup".equalsIgnoreCase(args[i])) {
                setup = true;
            } else {
                session = args[i];
            }
        }
        plugin.getIdentity().hello(player, role, index, session);
        plugin.getActivity().markAction(player, "hello " + role.id() + " #" + index + (setup ? " setup" : ""));
        BotPlaystyle.forceEnglish(plugin, player);
        BotRoleHandler handler = plugin.getRegistry().handler(role);
        if (setup && handler != null) {
            plugin.getProvisioner().setup(player, handler);
        }
        reply(player, "hello " + here(player) + " role=" + role.id() + " setup=" + (setup && handler != null ? "yes" : "no"));
    }

    private void stage(Player player, String spot) {
        String id = spot == null ? "" : spot.toLowerCase(Locale.ROOT);
        Location at = null;
        HubAccess hub = AetherServices.hub();
        if (hub != null && !id.isBlank()) {
            at = hub.location(id);
        }
        if (at == null) {
            BotRole role = BotRole.fromId(id);
            ConfigurationSection section = role == null ? null : BotRoleRegistry.roleSection(plugin, role);
            at = section == null ? null : BotLocations.pickAnchor(player, section);
        }
        if (at == null && "spawn".equals(id)) {
            at = player.getWorld().getSpawnLocation();
        }
        if (at == null || at.getWorld() == null) {
            reply(player, "stage " + id + " none");
            return;
        }
        player.setFallDistance(0f);
        player.teleport(at);
        plugin.getActivity().markAction(player, "stage " + id + " " + BotLocations.format(at));
        reply(player, "stage " + id + " ok " + coords(at));
    }

    private void near(Player player, String kind, double radius) {
        String what = kind == null ? "hostile" : kind.toLowerCase(Locale.ROOT);
        double r = Math.max(4, Math.min(160, radius));
        Location here = player.getLocation();
        Location best = null;
        String bestName = "";
        double bestDist = Double.MAX_VALUE;
        if ("chest".equals(what)) {
            World world = here.getWorld();
            int chunks = (int) Math.ceil(r / 16.0);
            int cx = here.getBlockX() >> 4;
            int cz = here.getBlockZ() >> 4;
            for (int dx = -chunks; dx <= chunks; dx++) {
                for (int dz = -chunks; dz <= chunks; dz++) {
                    if (world == null || !world.isChunkLoaded(cx + dx, cz + dz)) {
                        continue;
                    }
                    Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                    for (BlockState state : chunk.getTileEntities()) {
                        if (!(state instanceof Container)) {
                            continue;
                        }
                        String type = state.getType().name();
                        if (!type.contains("CHEST") && !type.equals("BARREL")) {
                            continue;
                        }
                        Location at = state.getLocation().add(0.5, 0, 0.5);
                        double dist = at.distanceSquared(here);
                        if (dist < bestDist && dist <= r * r) {
                            best = at;
                            bestDist = dist;
                            bestName = type.toLowerCase(Locale.ROOT);
                        }
                    }
                }
            }
        } else {
            String needle = what.startsWith("name:") ? what.substring(5) : null;
            for (Entity entity : player.getNearbyEntities(r, Math.max(12, r / 2), r)) {
                if (entity == player || !matches(entity, what, needle)) {
                    continue;
                }
                double dist = entity.getLocation().distanceSquared(here);
                if (dist < bestDist) {
                    best = entity.getLocation();
                    bestDist = dist;
                    bestName = label(entity);
                }
            }
        }
        if (best == null) {
            reply(player, "near " + what + " none");
            return;
        }
        reply(player, "near " + what + " " + coords(best) + " " + bestName.replace(' ', '_'));
    }

    private boolean matches(Entity entity, String what, String needle) {
        if (!entity.isValid()) {
            return false;
        }
        if (needle != null) {
            return !needle.isBlank() && label(entity).toLowerCase(Locale.ROOT).contains(needle);
        }
        return switch (what) {
            case "hostile" -> entity instanceof Enemy && entity instanceof LivingEntity living && !living.isDead();
            case "villager" -> entity instanceof Villager || entity instanceof WanderingTrader;
            case "animal" -> entity instanceof Animals;
            case "item" -> entity instanceof Item;
            case "npc" -> entity instanceof Villager || entity instanceof WanderingTrader
                    || (entity instanceof Player other && other.hasMetadata("NPC"))
                    || (entity instanceof LivingEntity && !(entity instanceof Enemy) && !(entity instanceof Player)
                    && entity.customName() != null);
            default -> false;
        };
    }

    private static String label(Entity entity) {
        if (entity.customName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(entity.customName());
        }
        return entity.getType().name().toLowerCase(Locale.ROOT);
    }

    private void quest(Player player) {
        QuestProgressAccess quests = AetherServices.quests();
        Location target = quests == null ? null : quests.questTarget(player);
        if (target == null || target.getWorld() == null) {
            reply(player, "quest none");
            return;
        }
        boolean sameWorld = target.getWorld().equals(player.getWorld());
        reply(player, "quest " + coords(target) + " world=" + target.getWorld().getName() + (sameWorld ? "" : " far"));
    }

    private void travel(Player player, String target) {
        AetherionCore core = AetherionCore.get();
        NetworkRouter router = core == null ? null : core.router();
        if (router == null || !router.canTravel()) {
            reply(player, "goto " + target + " refused no-transport");
            return;
        }
        String server = router.serverFor(target);
        if (server.equalsIgnoreCase(router.here())) {
            reply(player, "goto " + server + " here");
            return;
        }
        boolean sent = router.travel(player, server, RouteBook.Kind.NONE, "", null, "testbot");
        plugin.getActivity().markAction(player, "goto " + server + (sent ? "" : " refused"));
        reply(player, "goto " + server + (sent ? " ok" : " refused"));
    }

    private static String here(Player player) {
        AetherionCore core = AetherionCore.get();
        NetworkRouter router = core == null ? null : core.router();
        String server = router == null ? "?" : router.here();
        Location at = player.getLocation();
        return "server=" + (server == null || server.isBlank() ? "?" : server)
                + " world=" + (at.getWorld() == null ? "?" : at.getWorld().getName()) + " " + coords(at);
    }

    private static String coords(Location at) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", at.getX(), at.getY(), at.getZ());
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void reply(Player player, String line) {
        player.sendMessage("[tb] " + line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
