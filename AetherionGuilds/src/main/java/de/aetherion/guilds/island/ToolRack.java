package de.aetherion.guilds.island;

import de.aetherion.core.exile.Say;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.template.StateRotator;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the Build Tool lives: a small rack in front of every Hub, beside its lectern, with the tool hanging on it
 * and its name over it. Whoever may build on the island and has no Build Tool in their hotbar sees it and takes
 * the tool with a click; with the tool out, the rack is empty air for them (nothing in the way of a hologram or a
 * click). So "where do I get it" has one answer the player can see: at the Hub.
 *
 * <p>Display entities and one click box per island, never saved, there while someone stands on the island. The
 * name is shown in the reader's language.
 */
public final class ToolRack implements Listener {

    private static final String TAG = "aeg_toolrack";
    /** Where it may stand, as (side, toward the Hub) steps from the spot in front of the lectern; first free wins. */
    private static final int[][] SPOTS = {{-2, -1}, {-3, -1}, {-2, 0}, {-3, 0}, {3, -1}, {-4, -1}};

    private static final class Rack {
        final IslandHost host;
        final List<Entity> parts = new ArrayList<>();
        ItemDisplay tool;
        TextDisplay english;
        TextDisplay german;
        int x;
        int y;
        int z;
        float spin;
        long checked;

        Rack(IslandHost host) {
            this.host = host;
        }
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final PlacementService placement;
    private final Map<String, Rack> racks = new HashMap<>();
    private long ticks;

    public ToolRack(JavaPlugin plugin, HostService hosts, StructureService structures, LogisticsService logistics,
                    PlacementService placement) {
        this.plugin = plugin;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        this.placement = placement;
    }

    /** Every 10 ticks. */
    public void tick() {
        ticks++;
        Set<String> present = new HashSet<>();
        Map<String, List<Player>> people = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hosts.isIslandWorld(player.getWorld())) {
                continue;
            }
            IslandHost host = hosts.at(player.getLocation());
            if (host == null || !hosts.exists(host)) {
                continue;
            }
            present.add(host.key());
            people.computeIfAbsent(host.key(), k -> new ArrayList<>()).add(player);
        }
        for (String key : new ArrayList<>(racks.keySet())) {
            if (!present.contains(key)) {
                remove(racks.remove(key));
            }
        }
        for (String key : present) {
            IslandHost host = IslandHost.parse(key);
            PlacedStructure hub = host == null ? null : structures.hub(host);
            Rack rack = racks.get(key);
            if (hub == null || hub.building()) {
                if (rack != null) {
                    remove(racks.remove(key));
                }
                continue;
            }
            if (rack == null || !alive(rack) || ticks - rack.checked >= 10) {
                rack = place(host, hub, rack);
                if (rack == null) {
                    racks.remove(key);
                    continue;
                }
                racks.put(key, rack);
            }
            // the tool turns slowly on its hook
            rack.spin += (float) (Math.PI / 8);
            if (rack.tool != null && rack.tool.isValid()) {
                rack.tool.setInterpolationDelay(0);
                rack.tool.setInterpolationDuration(10);
                rack.tool.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f),
                        new Quaternionf().rotateY(rack.spin), new Vector3f(0.62f, 0.62f, 0.62f), new Quaternionf()));
            }
            for (Player player : people.getOrDefault(key, List.of())) {
                boolean show = hosts.canPlace(player, host) && !placement.has(player)
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
                boolean de = Say.german(player);
                for (Entity part : rack.parts) {
                    boolean want = show && (part != rack.english || !de) && (part != rack.german || de);
                    boolean seen = player.canSee(part);
                    if (want && !seen) {
                        player.showEntity(plugin, part);
                    } else if (!want && seen) {
                        player.hideEntity(plugin, part);
                    }
                }
            }
        }
    }

    private static boolean alive(Rack rack) {
        if (rack.parts.isEmpty()) {
            return false;
        }
        for (Entity part : rack.parts) {
            if (!part.isValid()) {
                return false;
            }
        }
        return true;
    }

    /** Stands the rack (or moves it when its cell got built over). Null when there is no room in front of the Hub. */
    private Rack place(IslandHost host, PlacedStructure hub, Rack old) {
        World world = hosts.world(host);
        Location front = structures.hubSpot(host);
        if (world == null || front == null) {
            remove(old);
            return null;
        }
        int[] spot = null;
        for (int[] option : SPOTS) {
            int[] off = StateRotator.rotateXZ(option[0], option[1], hub.rot());
            int x = front.getBlockX() + off[0];
            int z = front.getBlockZ() + off[1];
            int y = floor(world, x, hub.y() + 1, z);
            if (y == Integer.MIN_VALUE || structures.atColumn(world, x, z) != null || logistics.isBelt(world, x, y, z)
                    || !hosts.inBuildZone(host, x, z)) {
                continue;
            }
            spot = new int[]{x, y, z};
            break;
        }
        if (spot == null) {
            remove(old);
            return null;
        }
        if (old != null && alive(old) && old.x == spot[0] && old.y == spot[1] && old.z == spot[2]) {
            old.checked = ticks;
            return old;
        }
        remove(old);
        Rack rack = new Rack(host);
        rack.x = spot[0];
        rack.y = spot[1];
        rack.z = spot[2];
        rack.checked = ticks;
        build(world, rack, hub.rot());
        return rack;
    }

    /** The cell a rack can stand in at (x, z): air over solid ground, near {@code around}; MIN_VALUE when none. */
    private static int floor(World world, int x, int around, int z) {
        for (int y : new int[]{around, around + 1, around - 1, around + 2, around - 2}) {
            if (world.getBlockAt(x, y - 1, z).getType().isSolid() && world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void build(World world, Rack rack, int rot) {
        double cx = rack.x + 0.5;
        double cz = rack.z + 0.5;
        double y = rack.y;
        // a stone foot, a post with a cross arm, the tool hanging from it
        box(world, rack, Material.SMOOTH_STONE.createBlockData(), cx, y, cz, 0.62f, 0.14f, 0.62f);
        box(world, rack, Material.STRIPPED_SPRUCE_LOG.createBlockData(), cx, y + 0.14, cz, 0.16f, 1.5f, 0.16f);
        boolean alongX = Math.floorMod(rot, 2) == 0;
        box(world, rack, Material.STRIPPED_SPRUCE_LOG.createBlockData(), cx, y + 1.5, cz, alongX ? 0.86f : 0.14f, 0.12f,
                alongX ? 0.14f : 0.86f);
        box(world, rack, Material.COPPER_BLOCK.createBlockData(), cx, y + 1.62, cz, 0.22f, 0.07f, 0.22f);
        ItemStack tool = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            tool.setItemMeta(meta);
        }
        rack.tool = world.spawn(new Location(world, cx, y + 1.02, cz), ItemDisplay.class, d -> {
            d.setItemStack(tool);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.62f, 0.62f, 0.62f),
                    new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            common(d);
        });
        rack.parts.add(rack.tool);
        rack.english = label(world, cx, y + 1.86, cz, "§6⚒ §e§lBuild Tool\n§7click to take it");
        rack.german = label(world, cx, y + 1.86, cz, "§6⚒ §e§lBauwerkzeug\n§7anklicken und nehmen");
        rack.parts.add(rack.english);
        rack.parts.add(rack.german);
        Interaction hit = world.spawn(new Location(world, cx, y, cz), Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(1.8f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.setVisibleByDefault(false);
            i.addScoreboardTag(TAG);
        });
        rack.parts.add(hit);
    }

    private void box(World world, Rack rack, BlockData block, double cx, double y, double cz, float sx, float sy, float sz) {
        BlockDisplay display = world.spawn(new Location(world, cx, y, cz), BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setTransformation(new Transformation(new Vector3f(-sx / 2f, 0f, -sz / 2f), new AxisAngle4f(),
                    new Vector3f(sx, sy, sz), new AxisAngle4f()));
            common(d);
        });
        rack.parts.add(display);
    }

    private TextDisplay label(World world, double x, double y, double z, String text) {
        return world.spawn(new Location(world, x, y, z), TextDisplay.class, d -> {
            d.text(LegacyComponentSerializer.legacySection().deserialize(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(150, 16, 13, 22));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
            common(d);
        });
    }

    private static void common(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setShadowRadius(0f);
        display.setViewRange(0.6f);
        display.addScoreboardTag(TAG);
    }

    private static void remove(Rack rack) {
        if (rack == null) {
            return;
        }
        for (Entity part : rack.parts) {
            part.remove();
        }
        rack.parts.clear();
    }

    /** Shutdown: no rack stays behind. */
    public void clear() {
        for (Rack rack : racks.values()) {
            remove(rack);
        }
        racks.clear();
    }

    private Rack rackOf(Entity entity) {
        if (entity == null || !entity.getScoreboardTags().contains(TAG)) {
            return null;
        }
        for (Rack rack : racks.values()) {
            if (rack.parts.contains(entity)) {
                return rack;
            }
        }
        return null;
    }

    private void take(Player player, Rack rack) {
        if (rack == null || !hosts.canPlace(player, rack.host) || placement.has(player)) {
            return;
        }
        placement.openTool(player, rack.host, null);
        if (placement.has(player)) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEntityEvent event) {
        Rack rack = rackOf(event.getRightClicked());
        if (rack == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            take(event.getPlayer(), rack);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHit(PrePlayerAttackEntityEvent event) {
        Rack rack = rackOf(event.getAttacked());
        if (rack == null) {
            return;
        }
        event.setCancelled(true);
        take(event.getPlayer(), rack);
    }
}
