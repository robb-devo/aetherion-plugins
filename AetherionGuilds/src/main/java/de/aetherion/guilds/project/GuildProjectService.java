package de.aetherion.guilds.project;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.StarterLayout;
import de.aetherion.guilds.island.UnlockService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.Guild;
import de.aetherion.guilds.model.GuildRank;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.template.Template;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thin guild projects v1. Mayor+ starts one (one at a time), Footman+ contribute (inventory, the guild's
 * Storage Huts / Depots, coins, guild bank). When a stage is fully paid the building on site transitions to the
 * next stage template, with a guild-wide beat. Persisted in {@code guild_projects.yml}.
 */
public final class GuildProjectService {

    public static final class Active {
        private final GuildProjectType type;
        private int stage;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final int rot;
        private final UUID structureId;
        private final Map<String, Long> paid = new LinkedHashMap<>();

        Active(GuildProjectType type, int stage, String world, int x, int y, int z, int rot, UUID structureId) {
            this.type = type;
            this.stage = stage;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rot = rot;
            this.structureId = structureId;
        }

        public GuildProjectType type() {
            return type;
        }

        /** Completed stages so far. */
        public int stage() {
            return stage;
        }

        public GuildProjectType.Stage nextStage() {
            return stage < type.stages().size() ? type.stages().get(stage) : null;
        }

        public long paid(String key) {
            return paid.getOrDefault(key, 0L);
        }

        public long remaining(GuildProjectType.Req req) {
            return Math.max(0L, req.amount() - paid(req.key()));
        }

        public Location site() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y + 1, z + 0.5);
        }
    }

    private static final class Record {
        int renown;
        final Set<String> completed = new LinkedHashSet<>();
        final Set<UUID> visited = new LinkedHashSet<>();
        Active active;
    }

    private final JavaPlugin plugin;
    private final GuildService guilds;
    private final HostService hosts;
    private final StructureService structures;
    private final PlacementService placement;
    private final UnlockService unlock;
    private final File file;
    private final Map<UUID, Record> records = new HashMap<>();

    public GuildProjectService(JavaPlugin plugin, GuildService guilds, HostService hosts, StructureService structures,
                               PlacementService placement, UnlockService unlock) {
        this.plugin = plugin;
        this.guilds = guilds;
        this.hosts = hosts;
        this.structures = structures;
        this.placement = placement;
        this.unlock = unlock;
        this.file = new File(plugin.getDataFolder(), "guild_projects.yml");
    }

    private Record record(Guild guild) {
        return records.computeIfAbsent(guild.id(), k -> new Record());
    }

    public Active active(Guild guild) {
        Record record = records.get(guild.id());
        return record == null ? null : record.active;
    }

    public boolean completed(Guild guild, GuildProjectType type) {
        Record record = records.get(guild.id());
        return record != null && record.completed.contains(type.id());
    }

    public int renown(Guild guild) {
        Record record = records.get(guild.id());
        return record == null ? 0 : record.renown;
    }

    public List<GuildProjectType> completedList(Guild guild) {
        List<GuildProjectType> out = new ArrayList<>();
        Record record = records.get(guild.id());
        if (record != null) {
            for (String id : record.completed) {
                GuildProjectType type = GuildProjectType.byId(id);
                if (type != null) {
                    out.add(type);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // start
    // ------------------------------------------------------------------------------------------------

    public void start(Player player, Guild guild, GuildProjectType type) {
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canUpgradeIsland()) {
            player.sendMessage("§cMayors and above start guild projects.");
            return;
        }
        Record record = record(guild);
        if (record.active != null) {
            player.sendMessage("§cFinish the " + record.active.type().display() + " first.");
            return;
        }
        if (record.completed.contains(type.id())) {
            player.sendMessage("§7Your guild already built the " + type.display() + ".");
            return;
        }
        IslandHost host = IslandHost.guild(guild.id());
        World world = hosts.world(host);
        Template finalShape = structures.templates().get(type.finalTemplate());
        if (world == null || finalShape == null) {
            player.sendMessage("§cThat project's templates are missing on the server.");
            return;
        }
        if (hosts.starter(host) == StarterLayout.GUILD) {
            int ax = hosts.originX(host) + type.harbourSite()[0];
            int ay = HostService.SURFACE_Y + type.harbourSite()[1];
            int az = hosts.originZ(host) + type.harbourSite()[2];
            PlacedStructure blocking = structures.overlapping(world, finalShape, ax, az, 0);
            if (blocking == null) {
                begin(player, guild, type, world, ax, ay, az, 0, false);
                return;
            }
            player.sendMessage("§7The harbour's " + type.display() + " site is taken by your "
                    + blocking.label() + "; pick another spot.");
        }
        placement.startProjectSite(player, host, finalShape, type.display(),
                (who, ax, ay, az, rot) -> begin(who, guild, type, world, ax, ay, az, rot, true));
    }

    private void begin(Player player, Guild guild, GuildProjectType type, World world, int ax, int ay, int az, int rot,
                       boolean pasteSite) {
        Record record = record(guild);
        if (record.active != null) {
            player.sendMessage("§cAnother project started meanwhile.");
            return;
        }
        IslandHost host = IslandHost.guild(guild.id());
        PlacedStructure structure = structures.registerProject(host, type.id(), type.siteTemplate(), ax, ay, az, rot);
        if (structure == null) {
            return;
        }
        record.active = new Active(type, 0, world.getName(), ax, ay, az, rot, structure.id());
        if (pasteSite) {
            Template site = structures.templates().get(type.siteTemplate());
            structures.paster().paste(site, world, ax, ay, az, rot,
                    de.aetherion.guilds.template.PasteService.Mode.SKIP_AIR, true, null);
        }
        save();
        world.playSound(new Location(world, ax + 0.5, ay + 1, az + 0.5), Sound.BLOCK_BELL_USE, 1f, 0.8f);
        guilds.broadcast(guild, "§6⚑ " + player.getName() + " §7started the §f" + type.display()
                + "§7! Chip in at the project board or with §f/guild project§7.");
        player.sendMessage("§7Stage 1 needs: " + needs(record.active));
    }

    // ------------------------------------------------------------------------------------------------
    // contributions
    // ------------------------------------------------------------------------------------------------

    private Active contributable(Player player, Guild guild) {
        GuildRank rank = guild.rank(player.getUniqueId());
        if (rank == null || !rank.canCollectQuarry()) {
            player.sendMessage("§cOnly guild members can contribute.");
            return null;
        }
        Active active = active(guild);
        if (active == null || active.nextStage() == null) {
            player.sendMessage("§7No project is running. A Mayor can start one.");
            return null;
        }
        return active;
    }

    public void contributeInventory(Player player, Guild guild) {
        Active active = contributable(player, guild);
        if (active == null) {
            return;
        }
        List<String> given = new ArrayList<>();
        for (GuildProjectType.Req req : active.nextStage().reqs()) {
            long need = active.remaining(req);
            if (req.coins() || need <= 0L) {
                continue;
            }
            long taken = takeFromInventory(player, req.key(), need);
            if (taken > 0L) {
                active.paid.merge(req.key(), taken, Long::sum);
                given.add("§f" + GuildFormat.compact(taken) + " " + req.label());
            }
        }
        report(player, guild, given, "your pack");
        checkStage(player, guild);
    }

    public void contributeStorage(Player player, Guild guild) {
        Active active = contributable(player, guild);
        if (active == null) {
            return;
        }
        IslandHost host = IslandHost.guild(guild.id());
        List<String> given = new ArrayList<>();
        for (GuildProjectType.Req req : active.nextStage().reqs()) {
            long need = active.remaining(req);
            if (req.coins() || need <= 0L) {
                continue;
            }
            long taken = 0L;
            for (PlacedStructure sink : structures.of(host)) {
                if (sink.type().role() != StructureType.Role.SINK || need - taken <= 0L) {
                    continue;
                }
                for (Res res : new ArrayList<>(sink.store().keySet())) {
                    if (res.requirementKey().equals(req.key())) {
                        taken += PlacedStructure.take(sink.store(), res, need - taken);
                    }
                }
            }
            if (taken > 0L) {
                active.paid.merge(req.key(), taken, Long::sum);
                given.add("§f" + GuildFormat.compact(taken) + " " + req.label());
            }
        }
        if (!given.isEmpty()) {
            structures.markDirty();
        }
        report(player, guild, given, "the guild storage");
        checkStage(player, guild);
    }

    public void contributeCoins(Player player, Guild guild, long amount, boolean fromBank) {
        Active active = contributable(player, guild);
        if (active == null) {
            return;
        }
        GuildProjectType.Req coins = null;
        for (GuildProjectType.Req req : active.nextStage().reqs()) {
            if (req.coins()) {
                coins = req;
            }
        }
        if (coins == null || active.remaining(coins) <= 0L) {
            player.sendMessage("§7This stage's coins are already paid.");
            return;
        }
        long pay = Math.min(amount, active.remaining(coins));
        if (fromBank) {
            GuildRank rank = guild.rank(player.getUniqueId());
            if (rank == null || !rank.canBankWithdraw()) {
                player.sendMessage("§cSoldiers and above can pay from the guild bank.");
                return;
            }
            pay = Math.min(pay, guild.bankCoins());
            if (pay <= 0L) {
                player.sendMessage("§cThe guild bank is empty.");
                return;
            }
            guild.setBankCoins(guild.bankCoins() - pay);
            guilds.save();
        } else {
            if (de.aetherion.core.api.AetherServices.coins() != null) {
                pay = Math.min(pay, AetherionItemsAccess.coins(player));
                if (pay <= 0L || !AetherionItemsAccess.takeCoins(player, pay)) {
                    player.sendMessage("§cYou don't have coins to give.");
                    return;
                }
            }
        }
        active.paid.merge(coins.key(), pay, Long::sum);
        report(player, guild, List.of("§6" + GuildFormat.compact(pay) + " coins"), fromBank ? "the guild bank" : "your purse");
        checkStage(player, guild);
    }

    private long takeFromInventory(Player player, String key, long need) {
        if (key.startsWith("item:")) {
            String id = key.substring(5);
            int have = AetherionItemsAccess.count(player, id);
            int take = (int) Math.min(have, need);
            if (take > 0) {
                AetherionItemsAccess.take(player, id, take);
            }
            return take;
        }
        if (!key.startsWith("mat:")) {
            return 0L;
        }
        Material material = Material.matchMaterial(key.substring(4));
        if (material == null) {
            return 0L;
        }
        long left = need;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0L; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material || AetherionItemsAccess.itemId(item) != null) {
                continue;
            }
            if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasLore())) {
                continue;
            }
            int take = (int) Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            left -= take;
        }
        return need - left;
    }

    private void report(Player player, Guild guild, List<String> given, String source) {
        if (given.isEmpty()) {
            player.sendMessage("§7Nothing in " + source + " that this stage still needs.");
            return;
        }
        save();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.1f);
        guilds.broadcast(guild, "§6⚑ §f" + player.getName() + " §7gave " + String.join("§7, ", given)
                + " §7from " + source + ".");
    }

    // ------------------------------------------------------------------------------------------------
    // stages
    // ------------------------------------------------------------------------------------------------

    private void checkStage(Player trigger, Guild guild) {
        Record record = record(guild);
        Active active = record.active;
        if (active == null) {
            return;
        }
        GuildProjectType.Stage next = active.nextStage();
        if (next == null) {
            return;
        }
        for (GuildProjectType.Req req : next.reqs()) {
            if (active.remaining(req) > 0L) {
                return;
            }
        }
        PlacedStructure structure = structures.get(active.structureId);
        World world = Bukkit.getWorld(active.world);
        String fromId = active.stage == 0 ? active.type.siteTemplate()
                : active.type.stages().get(active.stage - 1).templateId();
        Template from = structures.templates().get(fromId);
        Template to = structures.templates().get(next.templateId());
        int stageNumber = active.stage + 1;
        boolean finished = stageNumber >= active.type.stages().size();
        active.stage = stageNumber;
        active.paid.clear();
        record.renown++;
        if (finished) {
            record.completed.add(active.type.id());
            record.active = null;
        }
        save();
        if (world == null || to == null) {
            return;
        }
        if (structure != null) {
            structure.setBuilding(true);
        }
        GuildProjectType type = active.type;
        structures.paster().transition(from, to, world, active.x, active.y, active.z, active.rot, true, job -> {
            if (structure != null) {
                structure.setTemplateId(next.templateId());
                structure.setBuilding(false);
                structures.reindex(structure);
            }
            celebrate(guild, type, stageNumber, next.name(), finished, job.center());
        });
        guilds.broadcast(guild, "§6⚑ §7Stage " + stageNumber + " of the §f" + type.display() + " §7is paid. Building…");
    }

    private void celebrate(Guild guild, GuildProjectType type, int stage, String stageName, boolean finished, Location site) {
        World world = site.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, site.clone().add(0, 3, 0), 120, 3, 3, 3, 0.4);
            world.spawnParticle(Particle.FIREWORK, site.clone().add(0, 5, 0), 80, 4, 2, 4, 0.05);
            world.playSound(site, Sound.EVENT_RAID_HORN, 2f, 1.1f);
            world.playSound(site, Sound.BLOCK_BELL_USE, 2f, 0.8f);
        }
        String title = "§6⚑ " + type.display();
        String subtitle = finished ? "§fFinished. Built together." : "§fStage " + stage + ": " + stageName + " done";
        for (Player member : hosts.onlineMembers(IslandHost.guild(guild.id()))) {
            member.sendTitle(title, subtitle, 10, 70, 20);
            member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, finished ? 0.9f : 1.2f);
        }
        guilds.broadcast(guild, finished
                ? "§6§l⚑ " + guild.name() + " finished the " + type.display() + "! §r§7Renown: §f" + renown(guild)
                : "§6⚑ §f" + type.display() + " §7→ §f" + stageName + " §7stands. Renown: §f" + renown(guild));
    }

    public String needs(Active active) {
        GuildProjectType.Stage stage = active == null ? null : active.nextStage();
        if (stage == null) {
            return "§7nothing";
        }
        List<String> parts = new ArrayList<>();
        for (GuildProjectType.Req req : stage.reqs()) {
            long left = active.remaining(req);
            if (left > 0L) {
                parts.add("§f" + GuildFormat.compact(left) + " " + req.label());
            }
        }
        return parts.isEmpty() ? "§anothing, it's paid" : String.join("§7, ", parts);
    }

    public static String bar(long have, long need) {
        int filled = need <= 0L ? 10 : (int) Math.min(10L, have * 10L / need);
        StringBuilder out = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                out.append("§8");
            }
            out.append('■');
        }
        long pct = need <= 0L ? 100L : Math.min(100L, have * 100L / need);
        return out.append(" §7").append(pct).append('%').toString();
    }

    // ------------------------------------------------------------------------------------------------
    // arrivals / lifecycle
    // ------------------------------------------------------------------------------------------------

    public void markVisited(Guild guild, UUID player) {
        if (record(guild).visited.add(player)) {
            save();
        }
    }

    /** First time a member stands on the guild island. */
    public void onArrive(Player player) {
        if (!hosts.guildIslands().isGuildWorld(player.getWorld())) {
            return;
        }
        Guild guild = guilds.byPlayer(player.getUniqueId());
        if (guild == null || !guild.islandBuilt()) {
            return;
        }
        IslandHost here = hosts.at(player.getLocation());
        if (here == null || !here.id().equals(guild.id())) {
            return;
        }
        if (record(guild).visited.add(player.getUniqueId())) {
            save();
            unlock.guildArrival(player, guild);
        }
    }

    public void dropGuild(UUID guildId) {
        if (records.remove(guildId) != null) {
            save();
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Record> entry : records.entrySet()) {
            Record record = entry.getValue();
            String path = "guilds." + entry.getKey();
            config.set(path + ".renown", record.renown);
            config.set(path + ".completed", new ArrayList<>(record.completed));
            List<String> visited = new ArrayList<>();
            for (UUID id : record.visited) {
                visited.add(id.toString());
            }
            config.set(path + ".visited", visited);
            Active active = record.active;
            if (active != null) {
                String a = path + ".active";
                config.set(a + ".type", active.type.id());
                config.set(a + ".stage", active.stage);
                config.set(a + ".world", active.world);
                config.set(a + ".x", active.x);
                config.set(a + ".y", active.y);
                config.set(a + ".z", active.z);
                config.set(a + ".rot", active.rot);
                config.set(a + ".structure", active.structureId.toString());
                for (Map.Entry<String, Long> paid : active.paid.entrySet()) {
                    config.set(a + ".paid." + paid.getKey().replace(':', '|'), paid.getValue());
                }
            }
        }
        try {
            AtomicYaml.save(config, file, plugin.getLogger());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save guild_projects.yml: " + exception.getMessage());
        }
    }

    public void load() {
        AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.exists()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("guilds");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID guildId = UUID.fromString(key);
                Record record = new Record();
                record.renown = s.getInt("renown");
                record.completed.addAll(s.getStringList("completed"));
                for (String raw : s.getStringList("visited")) {
                    try {
                        record.visited.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                ConfigurationSection a = s.getConfigurationSection("active");
                GuildProjectType type = a == null ? null : GuildProjectType.byId(a.getString("type"));
                if (type != null) {
                    Active active = new Active(type, a.getInt("stage"), a.getString("world", ""), a.getInt("x"),
                            a.getInt("y"), a.getInt("z"), a.getInt("rot"), UUID.fromString(a.getString("structure", "")));
                    ConfigurationSection paid = a.getConfigurationSection("paid");
                    if (paid != null) {
                        for (String k : paid.getKeys(false)) {
                            active.paid.put(k.replace('|', ':'), paid.getLong(k));
                        }
                    }
                    record.active = active;
                }
                records.put(guildId, record);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
