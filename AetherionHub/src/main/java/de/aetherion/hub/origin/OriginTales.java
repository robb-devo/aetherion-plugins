package de.aetherion.hub.origin;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Storytelling chests: dressed chests and barrels at surveyed spots, each holding a note (a short story of the
 * island, opened as a book) and a little flavour loot. Everyone finds each one once: the first open pays a few
 * coins and hands over the loot; afterwards only the note is left to re-read.
 *
 * <p>Two sorts: {@code place: chest|barrel} puts a real block on an empty, checked spot (done once on first start,
 * recorded in {@code origin-tales.yml} so {@code /origin dev tales undo} takes exactly those away again), and
 * {@code place: existing} uses a container that was already in the map. Opening one never touches its real
 * contents: the view is a per-player Hub inventory.
 */
public final class OriginTales implements Listener {

    public record Loot(Material material, int amount, String name, List<String> lore) {
    }

    public record Tale(String id, String name, String district, int[] at, String place, BlockFace facing, String author,
                       List<String> pages, List<Loot> loot, long coins, Material icon, String clue) {
    }

    /** Our per-player chest view. */
    public static final class Holder implements InventoryHolder {
        private final String taleId;
        private final Map<Integer, ItemStack> loot = new HashMap<>();
        private Inventory inventory;

        Holder(String taleId) {
            this.taleId = taleId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int NOTE_SLOT = 11;
    private static final int[] LOOT_SLOTS = {13, 14, 15, 16, 22, 23};
    private static final String STATE_FILE = "origin-tales.yml";

    private final OriginIsle isle;
    private final Map<String, Tale> tales = new LinkedHashMap<>();
    private final Map<Long, Tale> byBlock = new HashMap<>();
    private final File stateFile;
    private YamlConfiguration state = new YamlConfiguration();
    private boolean autoPlace = true;
    private double glintRadius = 8.0d;

    OriginTales(OriginIsle isle) {
        this.isle = isle;
        this.stateFile = new File(isle.plugin().getDataFolder(), STATE_FILE);
    }

    // ------------------------------------------------------------------ config

    int load() {
        tales.clear();
        byBlock.clear();
        state = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        ConfigurationSection settings = isle.config().explore().getConfigurationSection("tale-settings");
        if (settings != null) {
            autoPlace = settings.getBoolean("auto-place", true);
            glintRadius = settings.getDouble("glint-radius", 8.0d);
        }
        ConfigurationSection ts = isle.config().explore().getConfigurationSection("tales");
        if (ts == null) {
            return 0;
        }
        for (String id : ts.getKeys(false)) {
            ConfigurationSection s = ts.getConfigurationSection(id);
            int[] at = s == null ? null : OriginConfig.ints(s.getString("at"));
            if (at == null) {
                continue;
            }
            BlockFace facing;
            try {
                facing = BlockFace.valueOf(s.getString("facing", "south").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                facing = BlockFace.SOUTH;
            }
            List<Loot> loot = new ArrayList<>();
            for (String raw : s.getStringList("loot")) {
                Loot l = parseLoot(raw);
                if (l != null) {
                    loot.add(l);
                }
            }
            List<String> pages = new ArrayList<>(s.getStringList("note"));
            if (pages.isEmpty()) {
                pages.add("§8(The ink has run. Whatever this said, the rain has it now.)");
            }
            String place = s.getString("place", "chest").toLowerCase(Locale.ROOT);
            Tale tale = new Tale(id.toLowerCase(Locale.ROOT), s.getString("name", OriginText.pretty(id)), s.getString("district", ""), at,
                    place, facing, s.getString("author", "Anonymous"), pages, loot, s.getLong("coins", -1L),
                    OriginConfig.material(s.getString("icon"), place.equals("barrel") ? Material.BARREL : Material.CHEST), s.getString("clue", ""));
            tales.put(tale.id(), tale);
            byBlock.put(key(at[0], at[1], at[2]), tale);
        }
        return tales.size();
    }

    /** {@code "MATERIAL amount Name with spaces | lore line | lore line"}. */
    private static Loot parseLoot(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|");
        String[] head = parts[0].trim().split("\\s+", 3);
        Material material = Material.matchMaterial(head[0]);
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = 1;
        String name = null;
        if (head.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(head[1])));
                name = head.length >= 3 ? head[2] : null;
            } catch (NumberFormatException exception) {
                name = head.length >= 3 ? head[1] + " " + head[2] : head[1];
            }
        }
        List<String> lore = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            lore.add(parts[i].trim());
        }
        return new Loot(material, amount, name, lore);
    }

    public Map<String, Tale> all() {
        return tales;
    }

    public Tale get(String id) {
        return id == null ? null : tales.get(id.toLowerCase(Locale.ROOT));
    }

    public int found(OriginProfile profile) {
        int n = 0;
        for (String id : tales.keySet()) {
            if (profile.tales.contains(id)) {
                n++;
            }
        }
        return n;
    }

    /** A tale counts once its container stands in the world (placed by us, or one the map already had). */
    public boolean present(Tale tale) {
        if (tale.place().equals("existing")) {
            return true;
        }
        return state.isString("placed." + tale.id());
    }

    public int placedCount() {
        int n = 0;
        for (Tale tale : tales.values()) {
            if (present(tale)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ placing (once) and undo

    /** First start: put the chests down once. A later undo is remembered and never re-placed automatically. */
    void autoPlace() {
        if (!autoPlace || state.getBoolean("seeded", false)) {
            return;
        }
        String result = place();
        state.set("seeded", true);
        save();
        isle.plugin().getLogger().info("Origin " + result.replaceAll("§.", ""));
    }

    String place() {
        World world = isle.config().world();
        if (world == null) {
            return "§cWorld not loaded.";
        }
        int placed = 0;
        int already = 0;
        List<String> skipped = new ArrayList<>();
        for (Tale tale : tales.values()) {
            if (tale.place().equals("existing")) {
                continue;
            }
            if (present(tale)) {
                already++;
                continue;
            }
            Block block = world.getBlockAt(tale.at()[0], tale.at()[1], tale.at()[2]);
            Block below = block.getRelative(BlockFace.DOWN);
            if (!replaceable(block.getType()) || !below.getType().isSolid()) {
                skipped.add(tale.id() + " (" + block.getType().name().toLowerCase(Locale.ROOT) + ")");
                continue;
            }
            String previous = block.getBlockData().getAsString();
            Material type = tale.place().equals("barrel") ? Material.BARREL : Material.CHEST;
            BlockData data = Bukkit.createBlockData(type);
            if (data instanceof Directional directional) {
                BlockFace face = tale.facing();
                if (type == Material.BARREL || directional.getFaces().contains(face)) {
                    directional.setFacing(face);
                }
            }
            block.setBlockData(data, false);
            state.set("placed." + tale.id(), previous);
            placed++;
        }
        save();
        return "§aTales §8· §7placed §f" + placed + "§7, already there §f" + already
                + (skipped.isEmpty() ? "" : "§7, skipped §c" + skipped.size() + " §8" + String.join(", ", skipped));
    }

    String undo() {
        World world = isle.config().world();
        if (world == null) {
            return "§cWorld not loaded.";
        }
        ConfigurationSection placed = state.getConfigurationSection("placed");
        if (placed == null) {
            return "§7No placed tales on record.";
        }
        int restored = 0;
        for (String id : new ArrayList<>(placed.getKeys(false))) {
            Tale tale = tales.get(id);
            String previous = placed.getString(id);
            if (tale != null && previous != null) {
                Block block = world.getBlockAt(tale.at()[0], tale.at()[1], tale.at()[2]);
                if (block.getType() == Material.CHEST || block.getType() == Material.BARREL) {
                    try {
                        block.setBlockData(Bukkit.createBlockData(previous), false);
                        restored++;
                    } catch (IllegalArgumentException exception) {
                        block.setType(Material.AIR, false);
                        restored++;
                    }
                }
            }
            state.set("placed." + id, null);
        }
        save();
        return "§eTales §8· §7removed §f" + restored + " §7placed chest(s). §8/origin dev tales place puts them back.";
    }

    private static boolean replaceable(Material type) {
        return type.isAir() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS || type == Material.FERN
                || type == Material.SNOW || type == Material.DEAD_BUSH || type == Material.MOSS_CARPET || type == Material.PINK_PETALS
                || org.bukkit.Tag.SMALL_FLOWERS.isTagged(type);
    }

    private void save() {
        try {
            state.save(stateFile);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Origin: could not save " + STATE_FILE + ": " + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------ opening

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || byBlock.isEmpty()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Tale tale = byBlock.get(key(block.getX(), block.getY(), block.getZ()));
        Player player = event.getPlayer();
        if (tale == null || !isle.running() || !isle.onIsle(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.BARREL && type != Material.TRAPPED_CHEST) {
            return;
        }
        if (player.isSneaking() && OriginCommand.dev(player) && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return; // DEV with a block in hand: let vanilla building happen
        }
        event.setCancelled(true);
        open(player, tale, block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Tale tale = byBlock.get(key(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
        if (tale != null && isle.running() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    void open(Player player, Tale tale, Block block) {
        OriginProfile profile = isle.profiles().get(player);
        boolean first = profile.tales.add(tale.id());
        Holder holder = new Holder(tale.id());
        holder.inventory = Bukkit.createInventory(holder, 27, OriginText.legacy("§8" + tale.name()));
        holder.inventory.setItem(NOTE_SLOT, noteItem(tale, first));
        if (first) {
            profile.dirty = true;
            int i = 0;
            for (Loot l : tale.loot()) {
                if (i >= LOOT_SLOTS.length) {
                    break;
                }
                ItemStack stack = lootItem(l);
                holder.inventory.setItem(LOOT_SLOTS[i], stack);
                holder.loot.put(LOOT_SLOTS[i], stack);
                i++;
            }
            long paid = tale.coins() == 0 ? 0L : isle.pay(player, "tale", tale.coins() > 0 ? tale.coins() : 50L);
            int found = found(profile);
            OriginConfig.District d = isle.config().district(tale.district());
            player.sendMessage("§6✉ Tale §8· " + (d == null ? "§f" : d.color()) + tale.name() + (paid > 0 ? " §8· §6+" + paid : "")
                    + " §8· §7" + found + "/" + tales.size() + " §8· §7click the note to read it");
            if (found >= tales.size() && profile.mark("taleteller")) {
                long bonus = isle.pay(player, "taleteller", 1500L);
                if (!isle.quiet(player)) {
                    OriginText.card(player, "§6§lTaleteller", "§7Every storytelling chest on Origin", 70);
                }
                player.sendMessage("§6✦ Taleteller §8· §7every storytelling chest on Origin" + (bonus > 0 ? " §8· §6+" + OriginText.coins(bonus) + " coins" : ""));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.15f);
            }
        } else {
            holder.inventory.setItem(LOOT_SLOTS[1], OriginMenus.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8Nothing else here",
                    List.of("§7You took what there was."), false));
        }
        player.openInventory(holder.inventory);
        lid(block, true);
        Sound sound = block.getType() == Material.BARREL ? Sound.BLOCK_BARREL_OPEN : Sound.BLOCK_CHEST_OPEN;
        player.playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 0.6f, 1.0f);
        if (first && isle.profiles().get(player).particles) {
            player.spawnParticle(Particle.WAX_ON, block.getLocation().add(0.5, 1.0, 0.5), 8, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private static void lid(Block block, boolean open) {
        try {
            BlockState state = block.getState();
            if (state instanceof Lidded lidded) {
                if (open) {
                    lidded.open();
                } else {
                    lidded.close();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private ItemStack noteItem(Tale tale, boolean first) {
        List<String> lore = new ArrayList<>();
        lore.add("§7by §f" + tale.author());
        String preview = tale.pages().get(0).replaceAll("§.", "");
        lore.addAll(OriginMenus.wrap("§8" + (preview.length() > 90 ? preview.substring(0, 88) + "…" : preview), 34));
        lore.add("");
        lore.add("§e▸ Click §7to read");
        return OriginMenus.item(Material.WRITTEN_BOOK, (first ? "§6" : "§e") + tale.name(), lore, first);
    }

    private static ItemStack lootItem(Loot l) {
        ItemStack stack = new ItemStack(l.material(), l.amount());
        if (l.name() != null || !l.lore().isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (l.name() != null) {
                    meta.displayName(OriginText.legacy(l.name()).decoration(TextDecoration.ITALIC, false));
                }
                List<Component> lines = new ArrayList<>();
                for (String line : l.lore()) {
                    lines.add(OriginText.legacy(line).decoration(TextDecoration.ITALIC, false));
                }
                lines.add(OriginText.legacy("§8Found on Origin").decoration(TextDecoration.ITALIC, false));
                meta.lore(lines);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    public void read(Player player, Tale tale) {
        List<Component> pages = new ArrayList<>();
        for (String page : tale.pages()) {
            pages.add(OriginText.legacy(page.replace("\\n", "\n")));
        }
        player.openBook(Book.book(OriginText.legacy(tale.name()), OriginText.legacy(tale.author()), pages));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        Tale tale = tales.get(holder.taleId);
        if (tale == null) {
            return;
        }
        if (slot == NOTE_SLOT) {
            Bukkit.getScheduler().runTask(isle.plugin(), () -> {
                player.closeInventory();
                read(player, tale);
            });
            return;
        }
        ItemStack stack = holder.loot.remove(slot);
        if (stack != null) {
            give(player, stack);
            holder.inventory.setItem(slot, null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.3f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!holder.loot.isEmpty()) {
            for (ItemStack stack : holder.loot.values()) {
                give(player, stack);
            }
            holder.loot.clear();
            player.sendMessage("§8   You pocket the rest.");
        }
        Tale tale = tales.get(holder.taleId);
        World world = isle.config().world();
        if (tale != null && world != null) {
            Block block = world.getBlockAt(tale.at()[0], tale.at()[1], tale.at()[2]);
            Bukkit.getScheduler().runTaskLater(isle.plugin(), () -> {
                lid(block, false);
                Sound sound = block.getType() == Material.BARREL ? Sound.BLOCK_BARREL_CLOSE : Sound.BLOCK_CHEST_CLOSE;
                player.playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 0.5f, 1.0f);
            }, 2L);
        }
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack.clone()).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    // ------------------------------------------------------------------ glints

    void glint(Player player, OriginProfile profile, Location feet) {
        for (Tale tale : tales.values()) {
            if (profile.tales.contains(tale.id()) || !present(tale)) {
                continue;
            }
            double dx = feet.getX() - (tale.at()[0] + 0.5d);
            double dy = feet.getY() - tale.at()[1];
            double dz = feet.getZ() - (tale.at()[2] + 0.5d);
            if (dx * dx + dz * dz + dy * dy > glintRadius * glintRadius) {
                continue;
            }
            player.spawnParticle(Particle.WAX_ON, new Location(player.getWorld(), tale.at()[0] + 0.5d, tale.at()[1] + 1.05d, tale.at()[2] + 0.5d),
                    2, 0.25, 0.08, 0.25, 0.0);
            return;
        }
    }

    // ------------------------------------------------------------------ DEV

    String dev(Player player, String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "place" -> {
                return place();
            }
            case "undo" -> {
                return undo();
            }
            case "tp" -> {
                Tale tale = get(arg);
                if (tale == null) {
                    return "§7/origin dev tales tp <id>";
                }
                Location at = new Location(isle.config().world(), tale.at()[0] + 0.5d, tale.at()[1], tale.at()[2] + 0.5d)
                        .add(tale.facing().getModX() * 1.6d, 0, tale.facing().getModZ() * 1.6d);
                at.setDirection(new org.bukkit.util.Vector(-tale.facing().getModX(), -0.3, -tale.facing().getModZ()));
                isle.traversal().grace(player, 5_000L);
                player.teleport(at);
                return "§6TP §8· §f" + tale.name() + " §8(" + (present(tale) ? "§apresent" : "§cnot placed") + "§8)";
            }
            case "reset" -> {
                OriginProfile profile = isle.profiles().get(player);
                profile.tales.clear();
                profile.flags.remove("taleteller");
                profile.dirty = true;
                return "§eYour tales were reset.";
            }
            default -> {
                return "§7/origin dev tales <place|undo|tp <id>|reset> §8· §7" + placedCount() + "/" + tales.size() + " in the world";
            }
        }
    }

    void forget(UUID id) {
        // views close with the player; nothing cached per player
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }
}
