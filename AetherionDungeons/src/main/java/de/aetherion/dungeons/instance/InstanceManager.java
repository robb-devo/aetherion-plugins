package de.aetherion.dungeons.instance;

import de.aetherion.dungeons.bridge.BossEngineBridge;
import de.aetherion.core.world.VoidChunkGenerator;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InstanceManager {

    public static final String WORLD_PREFIX = "aedun_";

    private final JavaPlugin plugin;
    private final DungeonWarmPool warmPool;
    private final Map<UUID, DungeonSession> byOwner = new ConcurrentHashMap<>();
    private final Map<String, DungeonSession> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonSession> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> portalCooldown = new ConcurrentHashMap<>();

    public InstanceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.warmPool = new DungeonWarmPool(plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanActiveRooms, 20L, 20L);
        // Orphan XL worlds leak RAM hard — sweep regularly.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::purgeOrphanSessionsAndWorlds, 20L * 30, 20L * 60);
    }

    /** Called after leftover purge on enable — pre-bakes void + Endless XL. */
    public void startWarmPool() {
        warmPool.start();
    }

    public DungeonSession sessionOf(Player player) {
        return player == null ? null : byPlayer.getOrDefault(player.getUniqueId(), byOwner.get(player.getUniqueId()));
    }

    public DungeonSession sessionOf(World world) {
        return world == null ? null : byWorld.get(world.getName());
    }

    public void tryLootChest(Player player, org.bukkit.block.Block block) {
        DungeonSession session = sessionOf(player);
        if (session == null || player == null || !isDungeonWorld(player.getWorld())) {
            return;
        }
        DungeonLootFx.tryLoot(plugin, session, player, block);
    }

    public boolean isDungeonWorld(World world) {
        return world != null && world.getName().startsWith(WORLD_PREFIX);
    }

    public boolean hasActive(Player player) {
        return sessionOf(player) != null;
    }

    public void enterPrototype(Player player) {
        enterPrototype(player, false, 1);
    }

    public void enterPrototype(Player player, boolean bossOnly) {
        enterPrototype(player, bossOnly, 1);
    }

    public void enterPrototype(Player player, boolean bossOnly, int floor) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // Provisional: only one dungeon instance on the server at a time.
        if (!byOwner.isEmpty() || !byWorld.isEmpty()) {
            String who = "another party";
            for (DungeonSession live : byOwner.values()) {
                Player owner = Bukkit.getPlayer(live.ownerId());
                if (owner != null && owner.isOnline()) {
                    who = owner.getName();
                    break;
                }
            }
            player.sendMessage("§cA dungeon is already running §7(" + who + "§7).");
            player.sendMessage("§8Only one dungeon run at a time for now — try again when it finishes.");
            return;
        }
        int enterCode = Math.max(1, floor);
        // Floor 2 is the Endless XL schematic (new Frostbound floor).
        boolean endless = enterCode == EndlessSchemBuilder.ENTER_CODE || enterCode == 2;
        // Ice/test enter codes: ice → Floor 2 XL, prison-test → Floor 1 template.
        if (!endless && (enterCode == LerfingTestBuilder.ICE_TEST_FLOOR || enterCode == LerfingTestBuilder.FLOOR_NUMBER)) {
            if (enterCode == LerfingTestBuilder.ICE_TEST_FLOOR) {
                enterCode = 2;
                endless = true;
            } else {
                enterCode = 1;
            }
        }
        boolean template = !endless && enterCode == 1;
        LerfingTestBuilder.TemplateStyle templateStyle = LerfingTestBuilder.TemplateStyle.PRISON;
        int floorNumber = endless ? 2 : Math.max(1, Math.min(3, enterCode));
        boolean ashes = !endless && !template && floorNumber >= 3;
        if (!bossOnly && de.aetherion.dungeons.bridge.PartyBridge.inParty(player)
                && !de.aetherion.dungeons.bridge.PartyBridge.isLeader(player)) {
            player.sendMessage("§cOnly the party leader can start a dungeon.");
            return;
        }

        List<Player> party = new ArrayList<>();
        if (de.aetherion.dungeons.bridge.PartyBridge.isLeader(player)
                || !de.aetherion.dungeons.bridge.PartyBridge.inParty(player)) {
            party.addAll(de.aetherion.dungeons.bridge.PartyBridge.onlineMembers(player));
        } else {
            party.add(player);
            player.sendMessage("§7Starting solo. You are not the party leader.");
        }
        if (party.isEmpty()) {
            party.add(player);
        }

        for (Player member : party) {
            if (hasActive(member) || isDungeonWorld(member.getWorld())) {
                player.sendMessage("§c" + member.getName() + " is already in a dungeon.");
                return;
            }
        }

        if (endless && !EndlessSchemBuilder.worldEditPresent()) {
            player.sendMessage("§cEndless test needs WorldEdit on this server.");
            return;
        }

        String worldName;
        World world = null;
        DungeonWarmPool.WarmEndless warmEndless = null;
        DungeonWarmPool.WarmAshes warmAshes = null;
        if (endless) {
            warmEndless = warmPool.takeEndless();
            if (warmEndless != null) {
                world = warmEndless.world();
                worldName = world.getName();
            } else {
                worldName = WORLD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
        } else if (ashes) {
            warmAshes = warmPool.takeAshes();
            if (warmAshes != null) {
                world = warmAshes.world();
                worldName = world.getName();
            } else {
                worldName = DungeonWarmPool.ASHES_BASE;
            }
        } else {
            world = warmPool.takeVoid();
            worldName = world != null
                    ? world.getName()
                    : WORLD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        Location returnTo = player.getLocation().clone();

        String sessionFloorId = endless
                ? EndlessSchemBuilder.FLOOR_ID
                : (ashes ? AshesEncounter.FLOOR_ID : floorId(floorNumber));

        DungeonSession session = new DungeonSession(
                player.getUniqueId(),
                sessionFloorId,
                worldName,
                returnTo
        );
        byOwner.put(player.getUniqueId(), session);
        byWorld.put(worldName, session);
        for (Player member : party) {
            session.rememberReturn(member);
            session.party().add(member.getUniqueId());
            byPlayer.put(member.getUniqueId(), session);
        }
        session.setScalingPlayers(party.size());

        player.sendMessage(endless
                ? (warmEndless != null
                    ? "§bEntering Floor 2 · Frostbound..."
                    : "§bOpening Floor 2 · Frostbound...")
                : ashes
                    ? (warmAshes != null
                        ? "§5Entering Floor 3 · Throne of Ashes..."
                        : "§5Opening Floor 3 · Throne of Ashes...")
                    : "§5Opening a temporary dungeon instance...");
        player.closeInventory();

        if (world == null) {
            if (ashes) {
                File folder = new File(Bukkit.getWorldContainer(), DungeonWarmPool.ASHES_BASE);
                if (!folder.isDirectory()) {
                    byOwner.remove(player.getUniqueId());
                    byWorld.remove(worldName);
                    for (Player member : party) {
                        byPlayer.remove(member.getUniqueId());
                    }
                    player.sendMessage("§cFloor 3 map is not installed on this server yet.");
                    warmPool.ensureAll();
                    return;
                }
                WorldCreator creator = new WorldCreator(DungeonWarmPool.ASHES_BASE);
                creator.generateStructures(false);
                world = creator.createWorld();
            } else {
                world = createSlimVoidWorld(worldName);
            }
        }
        if (world == null) {
            byOwner.remove(player.getUniqueId());
            byWorld.remove(worldName);
            for (Player member : party) {
                byPlayer.remove(member.getUniqueId());
            }
            player.sendMessage("§cCould not create the dungeon world.");
            warmPool.ensureAll();
            return;
        }

        session.setWorld(world);
        DungeonLayout layout;
        Location spawn;
        try {
            if (endless) {
                if (warmEndless != null) {
                    EndlessSchemBuilder.PasteResult pasted = warmEndless.paste();
                    layout = pasted.layout();
                    spawn = EndlessEncounter.begin(
                            plugin,
                            world,
                            session,
                            pasted.minX(),
                            pasted.maxX(),
                            pasted.minY(),
                            pasted.maxY(),
                            pasted.minZ(),
                            pasted.maxZ(),
                            warmEndless.prep()
                    );
                } else {
                    EndlessSchemBuilder.BuildResult built = EndlessSchemBuilder.build(plugin, world, session);
                    layout = built.layout();
                    spawn = built.spawn();
                }
            } else if (ashes) {
                AshesEncounter.Prep prep = warmAshes != null
                        ? warmAshes.prep()
                        : AshesEncounter.prepare(plugin, world);
                if (warmAshes == null) {
                    warmPool.claimColdAshes(world, prep);
                }
                layout = AshesEncounter.layoutShell();
                spawn = AshesEncounter.begin(plugin, world, session, prep);
            } else {
                layout = template
                        ? DungeonLayout.lerfingTest(worldName.hashCode() ^ System.nanoTime())
                        : DungeonLayout.generate(worldName.hashCode() ^ System.nanoTime(), floorNumber);
                spawn = template
                        ? LerfingTestBuilder.build(plugin, world, layout, templateStyle)
                        : PrototypeDungeonBuilder.build(plugin, world, layout, floorNumber);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Dungeon build failed: " + exception.getMessage());
            byOwner.remove(player.getUniqueId());
            byWorld.remove(worldName);
            for (Player member : party) {
                byPlayer.remove(member.getUniqueId());
            }
            deleteWorld(worldName, world);
            player.sendMessage("§cCould not build the dungeon.");
            warmPool.ensureAll();
            return;
        }
        session.setLayout(layout);
        final World instanceWorld = world;
        final DungeonLayout instanceLayout = layout;
        world.setSpawnLocation(spawn);
        Location dest = spawn;
        if (bossOnly && ashes) {
            dest = new Location(world, AshesEncounter.BOSS_X + 0.5, AshesEncounter.BOSS_Y, AshesEncounter.BOSS_Z + 0.5, 180f, 0f);
        } else if (bossOnly && !endless) {
            dest = new Location(
                    world,
                    layout.boss().centerX() + 0.5,
                    PrototypeDungeonBuilder.FLOOR_Y + 1,
                    layout.boss().centerZ() + 0.5,
                    180f,
                    0f
            );
        }
        int extras = Math.max(0, party.size() - 1);
        for (Player member : party) {
            member.closeInventory();
            member.teleport(dest);
            QuestHudHook.suppress(member);
            if (!bossOnly && !endless && !ashes) {
                DungeonProgressHud.show(member, session);
            }
            if (!member.getUniqueId().equals(player.getUniqueId())) {
                member.sendMessage("§5" + player.getName() + " §7opened a dungeon. You came along.");
            }
        }
        if (endless) {
            session.setStarted(true);
            for (Player member : party) {
                member.sendMessage("§bFloor 2 · Frostbound §7· fill the §cred clearance bar §7(~§f75%§7 kills).");
                member.sendMessage("§7Red gate drops when the bar is full — §bFrostbound §7waits beyond.");
                member.sendMessage("§e/dungeon leave §7exits. §e/dungeon return §7goes to the main world. Gear stays.");
            }
            return;
        }
        if (ashes) {
            session.setStarted(true);
            if (bossOnly) {
                session.setBossReleased(true);
                Player initiator = party.get(0);
                Location at = new Location(world, AshesEncounter.BOSS_X + 0.5, AshesEncounter.BOSS_Y, AshesEncounter.BOSS_Z + 0.5);
                BossEngineBridge.spawn(at, initiator, BossEngineBridge.AETHERION, false);
                for (Player member : party) {
                    member.sendMessage("§5Boss test. Aetherion is already in the ash arena.");
                    member.sendMessage("§7Tip: §e/dungeon leave §7exits. Gear stays.");
                }
                return;
            }
            for (Player member : party) {
                member.sendMessage("§5Floor 3 · Throne of Ashes §7· fill the §cred clearance bar §7(~§f75%§7 kills).");
                member.sendMessage("§7Barrier drops when the bar is full — §5Aetherion §7waits in the arena.");
                member.sendMessage("§e/dungeon leave §7exits. §e/dungeon return §7goes to the main world. Gear stays.");
            }
            return;
        }
        if (bossOnly) {
            session.setStarted(true);
            session.setBossReleased(true);
            withScale(session, () -> PrototypeDungeonBuilder.unlockBoss(plugin, instanceWorld, instanceLayout, player));
            for (Player member : party) {
                member.sendMessage("§5Boss test. " + bossName(floorNumber) + " is already in the room.");
                if (floorNumber >= 3) {
                    member.sendMessage("§5Floor 3. Aetherion. The set is not a souvenir.");
                } else if (floorNumber >= 2) {
                    member.sendMessage("§bFloor 2. Snow, stairs, and a snowman with opinions.");
                }
                if (extras > 0) {
                    member.sendMessage("§8Scaled for §f" + party.size() + " §8players.");
                }
            }
            return;
        }
        if (floorNumber >= 3) {
            player.sendMessage("§5Floor 3 · End §7— talk to the §eGate Warden §7to ready up.");
            player.sendMessage("§7Big floor. Fat arena. §5Aetherion §7waits. The set is not easy.");
        } else if (floorNumber >= 2) {
            player.sendMessage("§bFloor 2 · Frost §7— talk to the §eGate Warden §7to ready up.");
            player.sendMessage("§7A little larger. Snow. Stairs. A snowman that unionized.");
        } else {
            player.sendMessage("§5Floor 1 · Prison §7— talk to the §eGate Warden §7to ready up.");
            player.sendMessage("§7Safe lobby, then branching halls. Glass gates. Sentinel at the end.");
        }
        player.sendMessage("§8Slot 9 is a live dungeon map. Green rooms are cleared.");
        if (extras > 0) {
            for (Player member : party) {
                member.sendMessage("§8Party of §f" + party.size() + "§8. Mobs scale up.");
            }
        }
    }

    public void leave(Player player, boolean completed) {
        if (player == null) {
            return;
        }
        DungeonSession session = sessionOf(player);
        if (session == null) {
            DungeonProgressHud.leaveDungeon(player);
            if (player.getWorld() != null && isDungeonWorld(player.getWorld())) {
                teleportHome(player, player.getWorld().getSpawnLocation());
            }
            return;
        }
        leaveSession(player.getUniqueId(), session, completed);
    }

    public void leaveSession(UUID playerId, DungeonSession session, boolean completed) {
        if (playerId == null || session == null) {
            return;
        }
        if (byWorld.get(session.worldName()) != session && byOwner.get(session.ownerId()) != session) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        World world = session.world();
        boolean othersRemain = world != null && world.getPlayers().stream()
                .anyMatch(occupant -> occupant.isOnline() && !occupant.getUniqueId().equals(playerId));
        if (!othersRemain) {
            destroy(session, completed
                    ? "§aYou left through the portal. The instance was deleted."
                    : "§7You left the dungeon. The instance was deleted.");
            return;
        }
        if (session.ownerId().equals(playerId)) {
            UUID nextOwner = null;
            for (Player occupant : world.getPlayers()) {
                if (!occupant.isOnline() || occupant.getUniqueId().equals(playerId)) {
                    continue;
                }
                if (session.party().contains(occupant.getUniqueId())) {
                    nextOwner = occupant.getUniqueId();
                    break;
                }
                if (nextOwner == null) {
                    nextOwner = occupant.getUniqueId();
                }
            }
            if (nextOwner == null) {
                destroy(session, completed
                        ? "§aYou left through the portal. The instance was deleted."
                        : "§7You left the dungeon. The instance was deleted.");
                return;
            }
            byOwner.remove(session.ownerId());
            session.transferOwner(nextOwner);
            byOwner.put(nextOwner, session);
            byPlayer.put(nextOwner, session);
            Player promoted = Bukkit.getPlayer(nextOwner);
            if (promoted != null && promoted.isOnline()) {
                promoted.sendMessage("§eYou are now the dungeon leader.");
            }
        }
        session.party().remove(playerId);
        session.ready().remove(playerId);
        byPlayer.remove(playerId);
        if (player != null && player.isOnline()) {
            teleportHome(player, session.returnFor(player));
            DungeonProgressHud.leaveDungeon(player);
            player.sendMessage("§7You left the dungeon.");
        }
        if (world != null) {
            String name = player != null ? player.getName() : "A player";
            for (Player occupant : world.getPlayers()) {
                occupant.sendMessage("§e" + name + " §7left the dungeon.");
            }
        }
    }

    public void tryPortalExit(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = portalCooldown.put(player.getUniqueId(), now);
        if (last != null && now - last < 1500L) {
            return;
        }
        DungeonSession session = sessionOf(player);
        leave(player, session != null && session.bossDead());
    }

    public void destroy(DungeonSession session, String message) {
        if (session == null) {
            return;
        }
        session.cancelClose();
        byOwner.remove(session.ownerId());
        byWorld.remove(session.worldName());
        for (UUID id : session.party()) {
            byPlayer.remove(id);
            EndlessEncounter.clearHistory(id);
        }
        byPlayer.remove(session.ownerId());
        EndlessEncounter.clearHistory(session.ownerId());
        EndlessEncounter.clear(session.world() != null ? session.world() : Bukkit.getWorld(session.worldName()));
        AshesEncounter.clear(session.world() != null ? session.world() : Bukkit.getWorld(session.worldName()));

        World world = session.world();
        if (world == null) {
            world = Bukkit.getWorld(session.worldName());
        }

        List<Player> evacuate = new ArrayList<>();
        if (world != null) {
            evacuate.addAll(world.getPlayers());
        }
        Player owner = Bukkit.getPlayer(session.ownerId());
        if (owner != null && owner.isOnline() && isDungeonWorld(owner.getWorld()) && !evacuate.contains(owner)) {
            evacuate.add(owner);
        }
        for (Player occupant : evacuate) {
            teleportHome(occupant, session.returnFor(occupant));
            DungeonProgressHud.leaveDungeon(occupant);
            if (message != null && !message.isBlank()) {
                occupant.sendMessage(message);
            }
        }
        if (owner != null && owner.isOnline() && !evacuate.contains(owner) && message != null && !message.isBlank()) {
            owner.sendMessage(message);
        }

        session.setWorld(null);
        if (world != null && warmPool.recycleEndless(world)) {
            // Reusable XL base — kept on disk, no delete/paste next time.
        } else if (world != null && warmPool.recycleAshes(world)) {
            // Reusable Throne of Ashes Floor 3 base.
        } else if (world != null && DungeonWarmPool.ASHES_BASE.equals(world.getName())) {
            Bukkit.unloadWorld(world, true);
        } else {
            deleteWorld(session.worldName(), world);
        }
        warmPool.ensureAll();
    }

    public void markReady(Player player) {
        DungeonSession session = sessionOf(player);
        if (session == null || session.world() == null) {
            return;
        }
        if (session.started()) {
            player.sendMessage("§7The gate is already open.");
            return;
        }
        if (!session.party().contains(player.getUniqueId())) {
            player.sendMessage("§cYou are not in this dungeon party.");
            return;
        }
        if (session.ready().add(player.getUniqueId())) {
            int have = session.ready().size();
            int need = session.party().size();
            for (Player occupant : session.world().getPlayers()) {
                occupant.sendMessage("§eGate Warden§7: " + player.getName() + " is ready. §f" + have + "/" + need);
            }
        } else {
            player.sendMessage("§7You are already ready. Waiting for the rest of the party.");
        }
        if (session.allReady()) {
            start(session);
        }
    }

    public void onCombatMobDeath(World world, int roomIndex) {
        plugin.getServer().getScheduler().runTask(plugin, () -> tryClearRoom(sessionOf(world), roomIndex));
    }

    public void scanCombatRooms(World world) {
        plugin.getServer().getScheduler().runTask(plugin, () -> scanSession(sessionOf(world)));
    }

    private void scanActiveRooms() {
        for (DungeonSession session : List.copyOf(byWorld.values())) {
            if (EndlessEncounter.isEndless(session)) {
                EndlessEncounter.tickSafeHistory(session);
                continue;
            }
            if (AshesEncounter.isAshes(session)) {
                continue;
            }
            scanSession(session);
        }
    }

    private void scanSession(DungeonSession session) {
        if (session == null || !session.started() || session.bossReleased() || session.world() == null || session.layout() == null) {
            return;
        }
        for (int i = 0; i < session.layout().combatCount(); i++) {
            tryClearRoom(session, i);
        }
    }

    private void tryClearRoom(DungeonSession session, int roomIndex) {
        if (session == null || !session.started() || session.bossReleased() || session.world() == null || session.layout() == null) {
            return;
        }
        if (byWorld.get(session.worldName()) != session) {
            return;
        }
        if (roomIndex < 0 || roomIndex >= session.layout().combatCount()) {
            return;
        }
        if (!session.isUnlocked(roomIndex) || session.isRoomCleared(roomIndex)) {
            return;
        }
        if (PrototypeDungeonBuilder.countRoomMobs(plugin, session.world(), roomIndex) > 0) {
            return;
        }
        if (session.isRoomCleared(roomIndex)) {
            return;
        }
        session.markRoomCleared(roomIndex);
        DungeonLayout.CombatRoom room = session.layout().combat(roomIndex);
        for (Player occupant : session.world().getPlayers()) {
            occupant.sendMessage("§a" + room.title() + " §7cleared. §f" + session.clearedCount() + "/" + session.layout().combatCount());
        }
        PrototypeDungeonBuilder.placeLootChest(session.world(), room, session.floorNumber());
        for (Player occupant : session.world().getPlayers()) {
            occupant.sendMessage("§6A cache unseals in " + room.title() + "§7. Right-click it — one item each.");
        }
        if (room.miniBoss() && session.floorNumber() <= 1
                && java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) {
            DungeonLootFx.placeGuaranteedVestige(
                    plugin,
                    session.world(),
                    room.bounds().maxX() - 2,
                    room.bounds().minZ() + 2,
                    1000 + roomIndex,
                    session.floorNumber()
            );
            for (Player occupant : session.world().getPlayers()) {
                occupant.sendMessage("§6The warden left a vestige cache.");
            }
        }
        boolean opened = false;
        for (int next : session.layout().neighbors(roomIndex)) {
            if (session.isUnlocked(next) || session.isRoomCleared(next)) {
                continue;
            }
            session.unlock(next);
            withScale(session, () -> PrototypeDungeonBuilder.unlockRoom(plugin, session.world(), session.layout(), next));
            opened = true;
            for (Player occupant : session.world().getPlayers()) {
                occupant.sendMessage("§8A seal turns green. §7" + session.layout().combat(next).title());
                occupant.playSound(occupant.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.55f);
            }
        }
        if (!session.allCombatCleared()) {
            if (!opened) {
                for (Player occupant : session.world().getPlayers()) {
                    occupant.sendMessage("§7Another chamber is still open. Check the map.");
                }
            }
            return;
        }
        session.setBossReleased(true);
        Player initiator = session.world().getPlayers().isEmpty() ? null : session.world().getPlayers().get(0);
        withScale(session, () -> PrototypeDungeonBuilder.unlockBoss(plugin, session.world(), session.layout(), initiator));
        for (Player occupant : session.world().getPlayers()) {
            DungeonProgressHud.hide(occupant);
            occupant.sendTitle(bossStirTitle(session.floorNumber()), "§7All chambers are clear", 10, 40, 15);
            occupant.playSound(occupant.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
            occupant.sendMessage("§5Every chamber is clear. " + bossName(session.floorNumber()) + " §7waits ahead.");
        }
    }

    public void onBossKilled(World world) {
        DungeonSession session = sessionOf(world);
        if (session == null || session.bossDead() || session.world() == null) {
            return;
        }
        session.setBossDead(true);
        if (EndlessEncounter.isEndless(session)) {
            EndlessEncounter.placeVictoryChest(plugin, session.world());
        } else if (AshesEncounter.isAshes(session)) {
            AshesEncounter.placeVictoryChest(plugin, session.world());
        } else if (session.layout() != null) {
            PrototypeDungeonBuilder.placeVictoryChest(session.world(), session.layout(), session.floorNumber());
        }
        for (Player occupant : session.world().getPlayers()) {
            occupant.sendTitle(bossFallTitle(session.floorNumber()), "§760s until the instance closes", 10, 50, 15);
            occupant.sendMessage(bossFallTitle(session.floorNumber()) + "§7. Right-click the reward chest — one item each — then walk into the §5exit portal§7.");
            occupant.sendMessage("§7Or type §e/dungeon leave §7to exit. Your gear stays.");
            occupant.sendMessage("§8The instance closes automatically in §f60 seconds§8.");
            occupant.playSound(occupant.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.8f);
        }
        startCloseTimer(session);
    }

    private void startCloseTimer(DungeonSession session) {
        session.cancelClose();
        session.setCloseSecondsLeft(60);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (session.world() == null) {
                session.cancelClose();
                return;
            }
            int left = session.closeSecondsLeft() - 1;
            session.setCloseSecondsLeft(left);
            for (Player occupant : session.world().getPlayers()) {
                occupant.sendActionBar(net.kyori.adventure.text.Component.text("Instance closes in " + Math.max(left, 0) + "s"));
                if (left == 10) {
                    occupant.sendTitle("§c10 seconds", "§7Use the exit portal", 5, 30, 10);
                }
            }
            if (left <= 0) {
                destroy(session, "§7Time is up. The instance closed.");
            }
        }, 20L, 20L);
        session.setCloseTask(task);
    }

    private void start(DungeonSession session) {
        if (session.started() || session.world() == null) {
            return;
        }
        session.setStarted(true);
        session.unlock(0);
            if (session.layout() == null) {
                session.setLayout(DungeonLayout.generate(session.worldName().hashCode(), session.floorNumber()));
            }
            withScale(session, () -> PrototypeDungeonBuilder.startEncounter(plugin, session.world(), session.layout()));
            for (Player occupant : session.world().getPlayers()) {
                occupant.sendTitle("§5The gate opens", "§7Clear every chamber", 10, 45, 15);
                occupant.playSound(occupant.getLocation(), org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);
                occupant.sendMessage("§5The dungeon begins. Clear all chambers — " + bossName(session.floorNumber())
                        + " §7waits until then.");
            }
    }

    public void shutdown() {
        for (DungeonSession session : new ArrayList<>(byOwner.values())) {
            destroy(session, "§7The dungeon closed because the server restarted.");
        }
        warmPool.shutdown();
        purgeLeftoverWorlds();
    }

    public void purgeLeftoverWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles((dir, name) -> name != null && name.startsWith(WORLD_PREFIX));
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            if (DungeonWarmPool.ENDLESS_BASE.equals(folder.getName())
                    || DungeonWarmPool.ASHES_BASE.equals(folder.getName())) {
                continue; // persistent floor templates — never purge
            }
            World loaded = Bukkit.getWorld(folder.getName());
            if (loaded != null) {
                for (Player occupant : new ArrayList<>(loaded.getPlayers())) {
                    Location fallback = Bukkit.getWorlds().isEmpty() ? occupant.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
                    teleportHome(occupant, fallback);
                }
                Bukkit.unloadWorld(loaded, false);
            }
            deleteFolderLater(folder, 1L);
        }
    }

    private void withScale(DungeonSession session, Runnable run) {
        PrototypeDungeonBuilder.applyPartyScale(
                session == null ? 1 : session.scalingPlayers(),
                session == null ? 1 : session.floorNumber()
        );
        try {
            run.run();
        } finally {
            PrototypeDungeonBuilder.resetPartyScale();
        }
    }

    private World createVoidWorld(String name) {
        return createSlimVoidWorld(name);
    }

    /** Shared by live instances and the warm pool. */
    public static World createSlimVoidWorld(String name) {
        WorldCreator creator = new WorldCreator(name);
        creator.generator(VoidChunkGenerator.forDungeons());
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);
        World world = creator.createWorld();
        if (world == null) {
            return null;
        }
        slimExistingWorld(world);
        return world;
    }

    public static void slimExistingWorld(World world) {
        if (world == null) {
            return;
        }
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setSpawnFlags(false, false);
        world.setDifficulty(org.bukkit.Difficulty.HARD);
        world.setPVP(false);
        world.setTime(18000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        try {
            world.getClass().getMethod("setViewDistance", int.class).invoke(world, 4);
        } catch (Throwable ignored) {
        }
        try {
            world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, 4);
        } catch (Throwable ignored) {
        }
    }

    private void teleportHome(Player player, Location fallback) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // On the dungeon backend, always land at the dungeon hub — never world spawn / stale return.
        Location hub = null;
        if (plugin instanceof de.aetherion.dungeons.AetherionDungeons dungeons) {
            hub = dungeons.dungeonHubLocation();
        }
        if (hub == null) {
            hub = resolveConfiguredHub();
        }
        Location target = hub;
        if (target == null || target.getWorld() == null) {
            target = fallback;
        }
        if (target == null || target.getWorld() == null || isDungeonWorld(target.getWorld())) {
            target = Bukkit.getWorlds().isEmpty() ? player.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(target);
    }

    private Location resolveConfiguredHub() {
        String worldName = plugin.getConfig().getString("dungeon-hub.arrival.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                plugin.getConfig().getDouble("dungeon-hub.arrival.x", -1.0),
                plugin.getConfig().getDouble("dungeon-hub.arrival.y", 49.0),
                plugin.getConfig().getDouble("dungeon-hub.arrival.z", -15.0),
                (float) plugin.getConfig().getDouble("dungeon-hub.arrival.yaw", 180.0),
                (float) plugin.getConfig().getDouble("dungeon-hub.arrival.pitch", 0.0)
        );
    }

    private void deleteWorld(String worldName, World world) {
        if (world != null && warmPool.recycleEndless(world)) {
            return;
        }
        if (world != null && warmPool.recycleAshes(world)) {
            return;
        }
        if (DungeonWarmPool.ENDLESS_BASE.equals(worldName)
                || DungeonWarmPool.ASHES_BASE.equals(worldName)) {
            // Persistent template — unload only, never wipe the folder.
            if (world != null) {
                BossEngineBridge.despawnInWorld(world);
                for (Player occupant : new ArrayList<>(world.getPlayers())) {
                    teleportHome(occupant, Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                for (org.bukkit.entity.Entity entity : new ArrayList<>(world.getEntities())) {
                    if (!(entity instanceof Player)) {
                        entity.remove();
                    }
                }
                Bukkit.unloadWorld(world, true);
            }
            return;
        }
        File folder = world != null ? world.getWorldFolder() : new File(Bukkit.getWorldContainer(), worldName);
        if (world != null) {
            forceUnload(world, worldName);
        }
        // XL folders are huge — retry deletes until gone.
        deleteFolderLater(folder, 20L);
        deleteFolderLater(folder, 60L);
        deleteFolderLater(folder, 120L);
        deleteFolderLater(folder, 200L);
    }

    private void forceUnload(World world, String worldName) {
        if (world == null) {
            return;
        }
        BossEngineBridge.despawnInWorld(world);
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        for (Player occupant : new ArrayList<>(world.getPlayers())) {
            Location hub = Bukkit.getWorlds().isEmpty()
                    ? occupant.getLocation()
                    : Bukkit.getWorlds().get(0).getSpawnLocation();
            teleportHome(occupant, hub);
        }
        if (!world.getPlayers().isEmpty()) {
            plugin.getLogger().warning("Dungeon world still has players after evacuate: " + worldName);
            // Kick-evacuate once more to hub.
            for (Player occupant : new ArrayList<>(world.getPlayers())) {
                teleportHome(occupant, Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        for (org.bukkit.entity.Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        // Drop chunk references so Paper can unload.
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(false);
        }
        if (!world.getPlayers().isEmpty()) {
            plugin.getLogger().warning("Skipping unload, players remain: " + worldName);
            scheduleUnloadRetry(worldName, 40L);
            return;
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload dungeon world (retry): " + worldName);
            scheduleUnloadRetry(worldName, 40L);
            scheduleUnloadRetry(worldName, 100L);
        }
    }

    private void scheduleUnloadRetry(String worldName, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                deleteFolderLater(new File(Bukkit.getWorldContainer(), worldName), 1L);
                return;
            }
            if (byWorld.containsKey(worldName) || warmPool.isPoolWorld(worldName)) {
                return; // still a live session or warm pool
            }
            forceUnload(world, worldName);
            deleteFolderLater(world.getWorldFolder(), 20L);
        }, delayTicks);
    }

    /** Disk/loaded worlds with no live session — XL leaks RAM hard if left behind. */
    private void purgeOrphanSessionsAndWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles((dir, name) -> name != null && name.startsWith(WORLD_PREFIX));
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            String name = folder.getName();
            if (byWorld.containsKey(name) || warmPool.isPoolWorld(name) || DungeonWarmPool.ENDLESS_BASE.equals(name)
                    || DungeonWarmPool.ASHES_BASE.equals(name)) {
                continue;
            }
            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                if (!loaded.getPlayers().isEmpty()) {
                    continue;
                }
                plugin.getLogger().warning("Purging orphan dungeon world: " + name);
                forceUnload(loaded, name);
            }
            deleteFolderLater(folder, 20L);
            deleteFolderLater(folder, 80L);
        }
    }

    private void deleteFolderLater(File folder, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (folder == null || !folder.exists()) {
                return;
            }
            String name = folder.getName();
            if (byWorld.containsKey(name) || Bukkit.getWorld(name) != null || warmPool.isPoolWorld(name)
                    || DungeonWarmPool.ENDLESS_BASE.equals(name)
                    || DungeonWarmPool.ASHES_BASE.equals(name)) {
                return;
            }
            if (!deleteRecursively(folder)) {
                plugin.getLogger().warning("Could not fully delete dungeon world folder: " + folder.getName()
                        + " — will retry");
                deleteFolderLater(folder, 100L);
            } else {
                plugin.getLogger().info("Deleted dungeon world folder: " + folder.getName());
            }
        }, delayTicks);
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File child : children) {
                ok &= deleteRecursively(child);
            }
        }
        return file.delete() && ok;
    }

    private static String coreRoman(int floor) {
        return floor >= 3 ? "III" : floor >= 2 ? "II" : "I";
    }

    private static String floorId(int floor) {
        if (floor == EndlessSchemBuilder.ENTER_CODE) {
            return EndlessSchemBuilder.FLOOR_ID;
        }
        if (floor == LerfingTestBuilder.ICE_TEST_FLOOR) {
            return LerfingTestBuilder.ICE_FLOOR_ID;
        }
        if (floor >= 3) {
            return AshesEncounter.FLOOR_ID;
        }
        if (floor >= 2) {
            return PrototypeDungeonBuilder.FLOOR_2_ID;
        }
        return PrototypeDungeonBuilder.FLOOR_ID;
    }

    private static String bossName(int floor) {
        if (floor >= 3) {
            return "§5Aetherion";
        }
        if (floor >= 2) {
            return "§bThe Frostbound";
        }
        return "§5the Sentinel";
    }

    private static String bossStirTitle(int floor) {
        if (floor >= 3) {
            return "§5The sky answers";
        }
        if (floor >= 2) {
            return "§bThe snowman stirs";
        }
        return "§5The Sentinel stirs";
    }

    private static String bossFallTitle(int floor) {
        if (floor >= 3) {
            return "§5Aetherion falls";
        }
        if (floor >= 2) {
            return "§bThe Frostbound melts";
        }
        return "§5The Sentinel falls";
    }

    public List<DungeonSession> sessions() {
        return List.copyOf(byOwner.values());
    }
}
