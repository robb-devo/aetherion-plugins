package de.aetherion.dungeons.bridge;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Character snapshots between the Velocity backends that run AetherionDungeons (mmo-r ↔ mmo-d).
 *
 * <h2>Transfer v6 — one character, one owner</h2>
 * <ul>
 *     <li><b>{@code <uuid>.char.yml}</b> is the character in flight. It is written by exactly one
 *     place per hop: the send pipeline (portal, /dungeon transfer, pending floor, /dhub, return
 *     portal) or the quit/shutdown hook. Items are encoded with Paper {@link ItemStack#serializeAsBytes()},
 *     so custom PDC survives. If a single item cannot be encoded the snapshot is refused and the
 *     inventory is not touched.</li>
 *     <li>Every snapshot has an id. The arriving backend claims the file with an atomic rename,
 *     applies it once, records the id in the ledger and moves the file to {@code applied/}.
 *     Reconnecting with an already applied id is a no-op.</li>
 *     <li>A backup copy goes to {@code outbox/} before Connect, so a missing main file can be
 *     recovered automatically ({@link #recoverFromOutbox}).</li>
 *     <li>A non-empty live inventory is never overwritten silently: it is copied to
 *     {@code quarantine/} first and logged.</li>
 * </ul>
 * Legacy files ({@code <uuid>.yml}, v4 from older Dungeons jars and v5 from Core's hub handoff)
 * are still honoured on join: v4 applies as before, hub snapshots only route (no gear).
 *
 * <p>Policy (documented choice): <b>full restore</b> — inventory, armor, offhand, ender chest,
 * cursor, level/exp, food/saturation/exhaustion, effects, gamemode/flight. Health is restored but
 * clamped to [1, max]; fire ticks are never carried.</p>
 */
public final class TransferSnapshotStore {

    public static final int CHAR_VERSION = 6;
    private static final NamespacedKey MANAGER_KEY = new NamespacedKey("aetherionitems", "manager");

    private final Plugin plugin;
    private final File dir;
    private final File outbox;
    private final File appliedDir;
    private final File quarantine;
    private final File rollback;
    private final NetworkPlayerDataSync networkData;
    private final TransferLedger ledger;
    private final Set<UUID> legacyAppliedThisSession = ConcurrentHashMap.newKeySet();
    private final String role;
    private final String here;

    public TransferSnapshotStore(Plugin plugin) {
        this.plugin = plugin;
        this.networkData = new NetworkPlayerDataSync(plugin);
        String configured = plugin.getConfig().getString("remote-transfer.shared-dir", "");
        if (configured == null || configured.isBlank()) {
            this.dir = new File(plugin.getDataFolder(), "transfer-snapshots");
        } else {
            File candidate = new File(configured);
            this.dir = candidate.isAbsolute()
                    ? candidate
                    : new File(plugin.getDataFolder(), configured);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create transfer snapshot dir: " + dir.getAbsolutePath());
        } else {
            plugin.getLogger().info("Transfer snapshots: " + dir.getAbsolutePath());
        }
        this.outbox = subdir("outbox");
        this.appliedDir = subdir("applied");
        this.quarantine = subdir("quarantine");
        this.rollback = subdir("rollback");
        this.ledger = new TransferLedger(plugin, new File(dir, "ledger"));
        this.role = plugin.getConfig().getString("role", "hub");
        boolean dungeonRole = "dungeon".equalsIgnoreCase(role);
        String configuredName = plugin.getConfig().getString("remote-transfer.this-server", "");
        String fallback = dungeonRole
                ? plugin.getConfig().getString("remote-transfer.target-server", "mmo-d")
                : plugin.getConfig().getString("remote-transfer.return-server", "mmo-r");
        String resolved = configuredName == null || configuredName.isBlank() ? fallback : configuredName;
        this.here = resolved == null || resolved.isBlank() ? role : resolved.trim().toLowerCase(Locale.ROOT);
    }

    private File subdir(String name) {
        File sub = new File(dir, name);
        if (!sub.exists() && !sub.mkdirs()) {
            plugin.getLogger().warning("[Transfer] Could not create " + sub.getAbsolutePath());
        }
        return sub;
    }

    public File directory() {
        return dir;
    }

    public TransferLedger ledger() {
        return ledger;
    }

    /** Velocity name of this backend (mmo-r / mmo-d), used for routing and the ledger. */
    public String serverName() {
        return here;
    }

    public NetworkPlayerDataSync networkData() {
        return networkData;
    }

    // ------------------------------------------------------------------ save

    public record SaveResult(boolean ok, String snapshotId, String error) {
        static SaveResult failed(String error) {
            return new SaveResult(false, "", error);
        }
    }

    /** @deprecated legacy entry point — use the send pipeline in {@link RemoteServerBridge}. */
    @Deprecated
    public void save(Player player) {
        save(player, 0, false);
    }

    /** @deprecated legacy entry point — use the send pipeline in {@link RemoteServerBridge}. */
    @Deprecated
    public void save(Player player, int pendingFloor, boolean bossOnly) {
        SaveResult result = saveCharacter(player, "legacy", "", pendingFloor, bossOnly, 0);
        if (result.ok()) {
            clearLocalCharacter(player);
        }
    }

    /**
     * Pack the whole character into {@code <uuid>.char.yml} (+ outbox backup) and mark the ledger.
     * Does NOT clear the live inventory — the caller decides when the handoff is final.
     */
    public SaveResult saveCharacter(Player player, String reason, String toServer,
                                    int pendingFloor, boolean bossOnly, int dcFloor) {
        if (player == null) {
            return SaveResult.failed("no player");
        }
        UUID id = player.getUniqueId();
        String snapshotId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        // Close GUIs + flush progression FIRST: closing an AH/trade GUI can hand items back
        // into the inventory, and those must be inside the snapshot.
        player.closeInventory();
        Map<String, Object> network = networkData.exportAll(player);
        PlayerInventory inv = player.getInventory();
        List<String> inventory;
        List<String> armor;
        List<String> extra;
        List<String> ender;
        String cursor;
        try {
            inventory = encodeStrict(inv.getContents(), "inventory");
            armor = encodeStrict(inv.getArmorContents(), "armor");
            extra = encodeStrict(inv.getExtraContents(), "offhand");
            ender = encodeStrict(player.getEnderChest().getContents(), "ender");
            cursor = encodeStrict(new ItemStack[]{player.getItemOnCursor()}, "cursor").get(0);
        } catch (EncodeFailure failure) {
            plugin.getLogger().severe("[Transfer] REFUSED save for " + player.getName()
                    + " reason=" + reason + " — " + failure.getMessage() + ". Inventory was not touched.");
            return SaveResult.failed(failure.getMessage());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", CHAR_VERSION);
        yaml.set("snapshot-id", snapshotId);
        yaml.set("uuid", id.toString());
        yaml.set("name", player.getName());
        yaml.set("saved-at", System.currentTimeMillis());
        yaml.set("from-server", role);
        yaml.set("from-name", here);
        yaml.set("to-server", toServer == null ? "" : toServer.toLowerCase(Locale.ROOT));
        yaml.set("reason", reason == null ? "" : reason);
        yaml.set("pending-floor", Math.max(0, pendingFloor));
        yaml.set("pending-boss-only", bossOnly);
        yaml.set("dc-floor", Math.max(0, dcFloor));
        Location at = player.getLocation();
        if (at.getWorld() != null) {
            yaml.set("location.world", at.getWorld().getName());
            yaml.set("location.x", at.getX());
            yaml.set("location.y", at.getY());
            yaml.set("location.z", at.getZ());
            yaml.set("location.yaw", (double) at.getYaw());
            yaml.set("location.pitch", (double) at.getPitch());
        }
        // Dot-safe: Bukkit YAML would split "yaml:coins.yml" / "players.<uuid>" on load.
        yaml.set("network-yaml", encodeNetwork(network));
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("health", player.getHealth());
        double maxHealth = 20.0;
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        yaml.set("max-health", maxHealth);
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", (double) player.getSaturation());
        yaml.set("exhaustion", (double) player.getExhaustion());
        yaml.set("gamemode", player.getGameMode().name());
        yaml.set("allow-flight", player.getAllowFlight());
        yaml.set("flying", player.isFlying());
        yaml.set("held-slot", inv.getHeldItemSlot());
        yaml.set("inventory-b64", inventory);
        yaml.set("armor-b64", armor);
        yaml.set("extra-b64", extra);
        yaml.set("enderchest-b64", ender);
        yaml.set("cursor-b64", cursor);
        yaml.set("count.inventory", countFilled(inventory));
        yaml.set("count.ender", countFilled(ender));
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(effect.getType().getKey().getKey()
                    + ";" + effect.getDuration()
                    + ";" + effect.getAmplifier()
                    + ";" + effect.isAmbient()
                    + ";" + effect.hasParticles()
                    + ";" + effect.hasIcon());
        }
        yaml.set("effects", effects);

        File file = charFile(id);
        parkUnappliedCharacter(id, "superseded-by-" + snapshotId);
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "[Transfer] REFUSED save for " + player.getName()
                    + " — write failed. Inventory was not touched.", ex);
            return SaveResult.failed("write failed");
        }
        try {
            Files.copy(file.toPath(), outboxFile(id, snapshotId).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Transfer] Outbox backup failed for " + snapshotId + ": " + ex.getMessage());
        }
        ledger.markSent(id, snapshotId, toServer, reason, here);
        plugin.getLogger().info("[Transfer] SAVE id=" + snapshotId
                + " player=" + player.getName()
                + " reason=" + reason
                + " from=" + here
                + " to=" + (toServer == null || toServer.isBlank() ? "any" : toServer)
                + " floor=" + pendingFloor
                + (dcFloor > 0 ? " dcFloor=" + dcFloor : "")
                + " inv=" + countFilled(inventory)
                + " ender=" + countFilled(ender)
                + " lvl=" + player.getLevel()
                + " (" + file.length() + " bytes)");
        return new SaveResult(true, snapshotId, "");
    }

    /** Empty the local copy after a successful handoff so this backend's playerdata is not a second truth. */
    public void clearLocalCharacter(Player player) {
        if (player == null) {
            return;
        }
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setExtraContents(null);
        player.getEnderChest().clear();
        player.updateInventory();
    }

    /**
     * Take back a snapshot nobody claimed yet (Connect failed / timed out).
     * @return true when the file was ours and is now parked in {@code rollback/}
     */
    public boolean reclaimUnclaimed(UUID id, String snapshotId) {
        File file = charFile(id);
        if (!file.isFile()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!snapshotId.equals(yaml.getString("snapshot-id", ""))) {
            return false;
        }
        File target = new File(rollback, id + "-" + snapshotId + ".yml");
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException gone) {
            return false;
        } catch (IOException ex) {
            plugin.getLogger().warning("[Transfer] Could not reclaim " + snapshotId + ": " + ex.getMessage());
            return false;
        }
        deleteQuietly(outboxFile(id, snapshotId));
        ledger.markHolder(id, here);
        plugin.getLogger().info("[Transfer] ROLLBACK id=" + snapshotId + " uuid=" + id + " — nobody claimed it, character stays on " + here);
        return true;
    }

    // ------------------------------------------------------------------ apply

    public record ApplyResult(
            boolean applied,
            int pendingFloor,
            boolean bossOnly,
            String fromServer,
            boolean inventoryOmitted,
            String snapshotId,
            String reason,
            int dcFloor,
            String fromName
    ) {
        public static ApplyResult none() {
            return new ApplyResult(false, 0, false, "", false, "", "", 0, "");
        }

        public boolean returnFromDungeon() {
            if (!applied || fromServer == null) {
                return false;
            }
            String from = fromServer.trim();
            return "dungeon".equalsIgnoreCase(from) || "mmo-d".equalsIgnoreCase(from);
        }

        public boolean arrivalFromHub() {
            if (!applied || fromServer == null) {
                return false;
            }
            String from = fromServer.trim();
            return "hub".equalsIgnoreCase(from) || "mmo-r".equalsIgnoreCase(from);
        }

        /** Quit or shutdown snapshot re-applied on the backend that wrote it. */
        public boolean sameServerRelog(String here) {
            return applied && fromName != null && fromName.equalsIgnoreCase(here)
                    && ("quit".equals(reason) || "shutdown".equals(reason) || "reload".equals(reason));
        }
    }

    public boolean hasPendingCharacter(UUID id) {
        return charFile(id).isFile() || claimedCharFile(id).isFile();
    }

    /** @deprecated use {@link #applyOnJoin(Player)} */
    @Deprecated
    public boolean applyIfPresent(Player player) {
        return applyOnJoin(player).applied();
    }

    /** @deprecated use {@link #applyOnJoin(Player)} */
    @Deprecated
    public ApplyResult applyDetailed(Player player) {
        return applyOnJoin(player);
    }

    /**
     * Join hook. Legacy {@code <uuid>.yml} first (routing / old full snapshots), then the v6 character.
     */
    public ApplyResult applyOnJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return ApplyResult.none();
        }
        UUID id = player.getUniqueId();
        ApplyResult legacy = processLegacy(player);
        File claimed = claimCharacter(id);
        if (claimed == null) {
            return legacy;
        }
        ApplyResult result = applyCharacterFile(player, claimed, false);
        if (!result.applied() && legacy.applied()) {
            return legacy;
        }
        if (result.applied() && legacy.applied() && legacy.pendingFloor() > 0 && result.pendingFloor() <= 0) {
            return new ApplyResult(true, legacy.pendingFloor(), legacy.bossOnly(), result.fromServer(),
                    result.inventoryOmitted(), result.snapshotId(), result.reason(), result.dcFloor(), result.fromName());
        }
        return result;
    }

    /** Missing main file but the ledger names a snapshot id: apply the outbox backup. */
    public ApplyResult recoverFromOutbox(Player player, String snapshotId) {
        if (player == null || snapshotId == null || snapshotId.isBlank()) {
            return ApplyResult.none();
        }
        UUID id = player.getUniqueId();
        if (ledger.wasApplied(id, snapshotId)) {
            return ApplyResult.none();
        }
        File backup = outboxFile(id, snapshotId);
        if (!backup.isFile()) {
            return ApplyResult.none();
        }
        File claimed = claimedCharFile(id);
        try {
            Files.copy(backup.toPath(), claimed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "[Transfer] Outbox recovery copy failed for " + snapshotId, ex);
            return ApplyResult.none();
        }
        plugin.getLogger().warning("[Transfer] RECOVER id=" + snapshotId + " player=" + player.getName()
                + " — main snapshot file was missing, applying the outbox backup.");
        return applyCharacterFile(player, claimed, false);
    }

    private File claimCharacter(UUID id) {
        File live = charFile(id);
        File claimed = claimedCharFile(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(live, plugin.getLogger());
        if (live.isFile()) {
            try {
                Files.move(live.toPath(), claimed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return claimed;
            } catch (NoSuchFileException raced) {
                // Another backend claimed it in the same instant.
                return claimed.isFile() ? claimed : null;
            } catch (IOException ex) {
                plugin.getLogger().warning("[Transfer] Could not claim snapshot for " + id + ": " + ex.getMessage());
                return live;
            }
        }
        // A claim that never finished (crash mid-apply) is retried; the ledger stops double applies.
        return claimed.isFile() ? claimed : null;
    }

    private ApplyResult applyCharacterFile(Player player, File source, boolean legacyFile) {
        UUID id = player.getUniqueId();
        YamlConfiguration yaml;
        try {
            yaml = new YamlConfiguration();
            yaml.load(source);
        } catch (Exception corrupt) {
            File parked = new File(quarantine, id + "-" + System.currentTimeMillis() + "-corrupt.yml");
            moveQuietly(source, parked);
            plugin.getLogger().log(Level.SEVERE, "[Transfer] CORRUPT snapshot for " + player.getName()
                    + " — parked at " + parked.getAbsolutePath() + ". Inventory left as joined.", corrupt);
            player.sendMessage("§cYour transfer data could not be read. Staff can restore it — nothing was deleted.");
            return ApplyResult.none();
        }
        String snapshotId = yaml.getString("snapshot-id", "");
        if (snapshotId.isBlank()) {
            snapshotId = "legacy-" + yaml.getLong("saved-at", System.currentTimeMillis());
        }
        if (ledger.wasApplied(id, snapshotId)) {
            moveQuietly(source, new File(appliedDir, id + "-" + snapshotId + ".dup.yml"));
            plugin.getLogger().info("[Transfer] SKIP id=" + snapshotId + " player=" + player.getName() + " — already applied once.");
            return ApplyResult.none();
        }
        String fromServer = yaml.getString("from-server", "");
        String fromName = yaml.getString("from-name", fromServer);
        String reason = yaml.getString("reason", legacyFile ? "legacy" : "");
        int pendingFloor = yaml.getInt("pending-floor", 0);
        boolean bossOnly = yaml.getBoolean("pending-boss-only", false);
        int dcFloor = yaml.getInt("dc-floor", 0);
        boolean omitInventory = legacyFile && shouldOmitInventory(yaml);
        boolean sameServer = fromName != null && fromName.equalsIgnoreCase(here) && !legacyFile;
        int decodeFailures = 0;
        try {
            ItemStack[] inventory = null;
            ItemStack[] armor = null;
            ItemStack[] extra = null;
            List<ItemStack> leftovers = List.of();
            if (!omitInventory) {
                if (!ledger.isAdopted(id, here)) {
                    // First v6 contact with this backend: whatever its playerdata still holds is
                    // residue of the old per-server model (never in any snapshot) — keep it.
                    leftovers = liveItems(player);
                } else {
                    backupLiveIfNotEmpty(player, snapshotId);
                }
                player.closeInventory();
                player.setItemOnCursor(null);
                player.getInventory().clear();
                player.getEnderChest().clear();
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                List<String> invRaw = yaml.getStringList("inventory-b64");
                List<String> armorRaw = yaml.getStringList("armor-b64");
                List<String> extraRaw = yaml.getStringList("extra-b64");
                List<String> enderRaw = yaml.getStringList("enderchest-b64");
                inventory = decodeItems(invRaw);
                armor = decodeItems(armorRaw);
                extra = decodeItems(extraRaw);
                ItemStack[] ender = decodeItems(enderRaw);
                ItemStack cursor = decodeItem(yaml.getString("cursor-b64"));
                decodeFailures = failures(invRaw, inventory) + failures(armorRaw, armor)
                        + failures(extraRaw, extra) + failures(enderRaw, ender);
                if (inventory != null && inventory.length > 0) {
                    player.getInventory().setContents(fit(inventory, player.getInventory().getSize()));
                }
                if (armor != null && armor.length > 0) {
                    player.getInventory().setArmorContents(fit(armor, 4));
                }
                if (extra != null && extra.length > 0) {
                    player.getInventory().setExtraContents(fit(extra, player.getInventory().getExtraContents().length));
                }
                if (ender != null && ender.length > 0) {
                    player.getEnderChest().setContents(fit(ender, player.getEnderChest().getSize()));
                }
                if (cursor != null && !cursor.getType().isAir()) {
                    player.getInventory().addItem(cursor).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
                int held = yaml.getInt("held-slot", player.getInventory().getHeldItemSlot());
                if (held >= 0 && held <= 8) {
                    player.getInventory().setHeldItemSlot(held);
                }
                player.setLevel(yaml.getInt("level", player.getLevel()));
                player.setExp((float) Math.max(0.0, Math.min(0.9999, yaml.getDouble("exp", player.getExp()))));
                player.setTotalExperience(yaml.getInt("total-exp", player.getTotalExperience()));
                player.setFoodLevel(yaml.getInt("food", player.getFoodLevel()));
                player.setSaturation((float) yaml.getDouble("saturation", player.getSaturation()));
                player.setExhaustion((float) yaml.getDouble("exhaustion", player.getExhaustion()));
                player.setFireTicks(0);
                String mode = yaml.getString("gamemode");
                if (mode != null) {
                    try {
                        player.setGameMode(GameMode.valueOf(mode));
                    } catch (IllegalArgumentException ignored) {
                        // keep current
                    }
                }
                player.setAllowFlight(yaml.getBoolean("allow-flight", false));
                if (yaml.getBoolean("flying", false) && player.getAllowFlight()) {
                    player.setFlying(true);
                }
                double maxHealth = yaml.getDouble("max-health", 20.0);
                if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null && maxHealth > 0) {
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
                }
                double health = yaml.getDouble("health", player.getHealth());
                player.setHealth(Math.max(1.0, Math.min(health, player.getMaxHealth())));
                for (String line : yaml.getStringList("effects")) {
                    applyEffectLine(player, line);
                }
                if (!leftovers.isEmpty()) {
                    // The old model never emptied ender chests on a hop, so both backends hold stale
                    // copies. Only what the incoming character does NOT already carry is residue.
                    List<ItemStack> incoming = new ArrayList<>();
                    collect(incoming, inventory);
                    collect(incoming, ender);
                    collect(incoming, new ItemStack[]{cursor});
                    List<ItemStack> surplus = surplusOver(leftovers, incoming);
                    if (totalAmount(surplus) != totalAmount(leftovers)) {
                        plugin.getLogger().info("[Transfer] ADOPT dedupe for " + player.getName() + ": "
                                + leftovers.size() + " live stack(s) on " + here + ", "
                                + surplus.size() + " not already in snapshot " + snapshotId);
                    }
                    if (!surplus.isEmpty()) {
                        adoptLeftovers(player, surplus, snapshotId);
                    }
                }
                player.updateInventory();
            } else {
                plugin.getLogger().info("[Transfer] Leaving inventory in place for " + player.getName()
                        + " — snapshot from " + fromServer + " does not carry hub gear onto MMO.");
            }

            // Progression files: only when the snapshot came from ANOTHER backend. On a same-backend
            // relog the local files are newer (offline payouts, pet ticks) and must win.
            if (!omitInventory && !sameServer) {
                Map<String, Object> networkMap = decodeNetwork(yaml);
                if (!networkMap.isEmpty()) {
                    networkData.importAll(player, networkMap);
                } else {
                    plugin.getLogger().warning("[Transfer] Snapshot " + snapshotId + " for " + player.getName()
                            + " had no network-data (pets/skills/level may stay local).");
                }
            }
            networkData.resetLoadoutRuntime(player);

            ledger.markApplied(id, snapshotId, here);
            moveQuietly(source, new File(appliedDir, id + "-" + snapshotId + ".yml"));
            deleteQuietly(outboxFile(id, snapshotId));
            if (legacyFile) {
                legacyAppliedThisSession.add(id);
            }

            if (!omitInventory) {
                // Copies of what the player holds NOW (snapshot + adopted leftovers).
                final ItemStack[] invCopy = cloneAll(player.getInventory().getContents());
                final ItemStack[] armorCopy = cloneAll(player.getInventory().getArmorContents());
                final ItemStack[] extraCopy = cloneAll(player.getInventory().getExtraContents());
                // Re-assert after loadout / join hooks (tick 25). The player is frozen until then.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    networkData.resetLoadoutRuntime(player);
                    if (invCopy != null) {
                        player.getInventory().setContents(invCopy);
                    }
                    if (armorCopy != null) {
                        player.getInventory().setArmorContents(armorCopy);
                    }
                    if (extraCopy != null) {
                        player.getInventory().setExtraContents(fit(extraCopy, player.getInventory().getExtraContents().length));
                    }
                    player.updateInventory();
                    de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
                    if (progress != null) {
                        progress.refreshManager(player);
                    }
                }, 25L);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        networkData.resetLoadoutRuntime(player);
                    }
                }, 25L);
            }

            plugin.getLogger().info("[Transfer] APPLY id=" + snapshotId
                    + " player=" + player.getName()
                    + " from=" + (fromName == null || fromName.isBlank() ? "?" : fromName)
                    + " here=" + here
                    + " reason=" + reason
                    + " floor=" + pendingFloor
                    + (dcFloor > 0 ? " dcFloor=" + dcFloor : "")
                    + (omitInventory ? " inv=left-in-place" : " inv=" + yaml.getInt("count.inventory", -1))
                    + (sameServer ? " progress=local" : " progress=imported")
                    + (legacyFile ? " legacy=v" + yaml.getInt("version", 4) : ""));
            if (decodeFailures > 0) {
                plugin.getLogger().severe("[Transfer] " + decodeFailures + " item(s) in snapshot " + snapshotId
                        + " for " + player.getName() + " could not be decoded. Original kept at "
                        + new File(appliedDir, id + "-" + snapshotId + ".yml").getAbsolutePath());
                player.sendMessage("§c" + decodeFailures + " item(s) could not be restored. Staff have the original — nothing was deleted.");
            }
            return new ApplyResult(true, pendingFloor, bossOnly, fromServer == null ? "" : fromServer,
                    omitInventory, snapshotId, reason, dcFloor, fromName == null ? "" : fromName);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "[Transfer] FAILED to apply " + snapshotId + " for " + player.getName()
                    + " — claimed file kept for the next join: " + source.getAbsolutePath(), ex);
            return ApplyResult.none();
        }
    }

    // ------------------------------------------------------------------ legacy (<uuid>.yml)

    private ApplyResult processLegacy(Player player) {
        UUID id = player.getUniqueId();
        File live = legacyFile(id);
        File claimed = legacyClaimedFile(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(live, plugin.getLogger());
        File source = null;
        if (live.isFile()) {
            legacyAppliedThisSession.remove(id);
            try {
                Files.move(live.toPath(), claimed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                source = claimed;
            } catch (NoSuchFileException raced) {
                source = null;
            } catch (IOException ex) {
                plugin.getLogger().warning("[Transfer] Could not claim legacy snapshot for " + id + ": " + ex.getMessage());
                source = live;
            }
        } else if (claimed.isFile()) {
            if (legacyAppliedThisSession.contains(id)) {
                deleteQuietly(claimed);
                return ApplyResult.none();
            }
            source = claimed;
        }
        if (source == null || !source.isFile()) {
            return ApplyResult.none();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        boolean routingOnly = shouldOmitInventory(yaml);
        if (!routingOnly && hasPendingCharacter(id)) {
            // A v6 character is also waiting — it is newer by construction (new jars write only v6).
            File parked = new File(quarantine, id + "-" + System.currentTimeMillis() + "-legacy-superseded.yml");
            moveQuietly(source, parked);
            plugin.getLogger().warning("[Transfer] Legacy v" + yaml.getInt("version", 4) + " snapshot for " + player.getName()
                    + " parked (a v6 character is pending): " + parked.getName());
            return ApplyResult.none();
        }
        return applyCharacterFile(player, source, true);
    }

    private static boolean shouldOmitInventory(YamlConfiguration yaml) {
        if (yaml.getBoolean("inventory-omitted", false)) {
            return true;
        }
        String from = yaml.getString("from-server", "");
        if (from == null || !"hub".equalsIgnoreCase(from.trim())) {
            return false;
        }
        int version = yaml.getInt("version", 4);
        if (version == CHAR_VERSION) {
            return false;
        }
        if (version >= 5) {
            return true;
        }
        String to = yaml.getString("to-server", "");
        String warp = yaml.getString("pending-warp", "");
        if (to != null && "mmo-r".equalsIgnoreCase(to.trim())) {
            return true;
        }
        return warp != null && !warp.isBlank();
    }

    // ------------------------------------------------------------------ pre-login staging

    /**
     * Async pre-login: write per-player files that Paper / Quests load before the join event
     * (stats, advancements, quest YAML) from a pending v6 character that came from another backend.
     * Does not claim the snapshot; the join hook applies it as usual.
     */
    public void stageBeforeLogin(UUID id, File mainWorldFolder) {
        if (id == null) {
            return;
        }
        File source = charFile(id).isFile() ? charFile(id) : claimedCharFile(id);
        if (!source.isFile()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
            String fromName = yaml.getString("from-name", "");
            if (fromName != null && fromName.equalsIgnoreCase(here)) {
                return;
            }
            if (ledger.wasApplied(id, yaml.getString("snapshot-id", ""))) {
                return;
            }
            Map<String, Object> network = decodeNetwork(yaml);
            networkData.stageEarlyFiles(id, network, mainWorldFolder);
        } catch (Exception ex) {
            plugin.getLogger().warning("[Transfer] Pre-login staging skipped for " + id + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ housekeeping

    /** Drop applied/outbox/rollback copies older than {@code days}; quarantine keeps 4x longer. */
    public void prune(int days) {
        long cutoff = System.currentTimeMillis() - Math.max(1, days) * 86_400_000L;
        pruneDir(appliedDir, cutoff);
        pruneDir(outbox, cutoff);
        pruneDir(rollback, cutoff);
        pruneDir(quarantine, System.currentTimeMillis() - Math.max(1, days) * 4L * 86_400_000L);
    }

    private void pruneDir(File folder, long cutoff) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                deleteQuietly(file);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * An unapplied character that is about to be replaced by a newer save goes to quarantine,
     * never into the void. Already-applied leftovers are just removed.
     */
    private void parkUnappliedCharacter(UUID id, String why) {
        for (File file : new File[]{charFile(id), claimedCharFile(id)}) {
            if (!file.isFile()) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String oldId = yaml.getString("snapshot-id", "");
            if (!oldId.isBlank() && ledger.wasApplied(id, oldId)) {
                deleteQuietly(file);
                continue;
            }
            File parked = new File(quarantine, id + "-" + (oldId.isBlank() ? System.currentTimeMillis() : oldId) + "-" + why + ".yml");
            moveQuietly(file, parked);
            plugin.getLogger().severe("[Transfer] Unapplied snapshot " + oldId + " for " + id
                    + " was still pending while a new one was written — parked at " + parked.getAbsolutePath());
        }
    }

    private void backupLiveIfNotEmpty(Player player, String incomingId) {
        List<String> inv = encodeLenient(player.getInventory().getContents());
        List<String> ender = encodeLenient(player.getEnderChest().getContents());
        int filled = countFilled(inv) + countFilled(ender);
        if (filled == 0) {
            return;
        }
        YamlConfiguration backup = new YamlConfiguration();
        backup.set("uuid", player.getUniqueId().toString());
        backup.set("name", player.getName());
        backup.set("saved-at", System.currentTimeMillis());
        backup.set("server", here);
        backup.set("replaced-by", incomingId);
        backup.set("inventory-b64", inv);
        backup.set("enderchest-b64", ender);
        backup.set("level", player.getLevel());
        File file = new File(quarantine, player.getUniqueId() + "-" + System.currentTimeMillis() + "-preapply.yml");
        try {
            de.aetherion.core.persist.AtomicYaml.save(backup, file, plugin.getLogger());
            plugin.getLogger().warning("[Transfer] " + player.getName() + " joined " + here + " with " + filled
                    + " live item(s) before snapshot " + incomingId + " — backed up to " + file.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Transfer] Pre-apply backup failed for " + player.getName(), ex);
        }
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? null : items[i].clone();
        }
        return out;
    }

    private static List<ItemStack> liveItems(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack[] area : new ItemStack[][]{
                player.getInventory().getContents(),
                player.getEnderChest().getContents(),
                new ItemStack[]{player.getItemOnCursor()}}) {
            if (area == null) {
                continue;
            }
            for (ItemStack item : area) {
                if (item != null && !item.getType().isAir() && !isManagerItem(item)) {
                    out.add(item.clone());
                }
            }
        }
        return out;
    }

    private static void collect(List<ItemStack> into, ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                into.add(item);
            }
        }
    }

    /**
     * Multiset difference by {@link ItemStack#isSimilar}: what {@code live} holds beyond
     * {@code incoming}, amount-aware (32 in the snapshot, 40 live → 8 surplus).
     */
    static List<ItemStack> surplusOver(List<ItemStack> live, List<ItemStack> incoming) {
        List<ItemStack> keys = new ArrayList<>();
        List<Integer> budget = new ArrayList<>();
        for (ItemStack item : incoming) {
            int at = indexOfSimilar(keys, item);
            if (at < 0) {
                keys.add(item);
                budget.add(item.getAmount());
            } else {
                budget.set(at, budget.get(at) + item.getAmount());
            }
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : live) {
            int at = indexOfSimilar(keys, item);
            int left = item.getAmount();
            if (at >= 0) {
                int take = Math.min(left, budget.get(at));
                budget.set(at, budget.get(at) - take);
                left -= take;
            }
            if (left <= 0) {
                continue;
            }
            ItemStack rest = item.clone();
            rest.setAmount(left);
            out.add(rest);
        }
        return out;
    }

    private static int totalAmount(List<ItemStack> items) {
        int sum = 0;
        for (ItemStack item : items) {
            sum += item.getAmount();
        }
        return sum;
    }

    private static int indexOfSimilar(List<ItemStack> keys, ItemStack item) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    /** Legacy leftovers go into the bag, then the ender chest; anything beyond waits in quarantine. */
    private void adoptLeftovers(Player player, List<ItemStack> leftovers, String snapshotId) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : leftovers) {
            overflow.addAll(player.getInventory().addItem(item).values());
        }
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : overflow) {
            rest.addAll(player.getEnderChest().addItem(item).values());
        }
        plugin.getLogger().warning("[Transfer] ADOPT " + leftovers.size() + " legacy item stack(s) from " + here
                + " playerdata for " + player.getName() + " (snapshot " + snapshotId + ")"
                + (rest.isEmpty() ? "" : ", " + rest.size() + " did not fit"));
        if (rest.isEmpty()) {
            player.sendMessage("§5Dungeon Gate§7: Items you left on §f" + here + "§7 were merged back into your bag.");
            return;
        }
        YamlConfiguration parked = new YamlConfiguration();
        parked.set("uuid", player.getUniqueId().toString());
        parked.set("name", player.getName());
        parked.set("saved-at", System.currentTimeMillis());
        parked.set("server", here);
        parked.set("reason", "adopt-overflow");
        parked.set("inventory-b64", encodeLenient(rest.toArray(new ItemStack[0])));
        File file = new File(quarantine, player.getUniqueId() + "-" + System.currentTimeMillis() + "-adopt-overflow.yml");
        try {
            de.aetherion.core.persist.AtomicYaml.save(parked, file, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "[Transfer] Could not park adopt overflow for " + player.getName(), ex);
        }
        player.sendMessage("§5Dungeon Gate§7: " + rest.size() + " old item stack(s) did not fit — staff can hand them back (nothing was deleted).");
    }

    private static final char NETWORK_SEPARATOR = '\u001F';

    static String encodeNetwork(Map<String, Object> network) {
        YamlConfiguration box = new YamlConfiguration();
        box.options().pathSeparator(NETWORK_SEPARATOR);
        box.set("network-data", network);
        return box.saveToString();
    }

    /**
     * v6: {@code network-yaml} (separator-safe). Older snapshots: {@code network-data}, whose dotted
     * keys Bukkit split into nested sections — repaired back to {@code yaml:<file>.yml -> players.<uuid>}.
     */
    Map<String, Object> decodeNetwork(YamlConfiguration yaml) {
        String raw = yaml.getString("network-yaml", "");
        if (raw != null && !raw.isBlank()) {
            YamlConfiguration box = new YamlConfiguration();
            box.options().pathSeparator(NETWORK_SEPARATOR);
            try {
                box.loadFromString(raw);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Transfer] network-yaml unreadable: " + ex.getMessage());
                return new java.util.LinkedHashMap<>();
            }
            return NetworkPlayerDataSync.toPlainMap(box.get("network-data"));
        }
        return NetworkPlayerDataSync.repairLegacyKeys(NetworkPlayerDataSync.toPlainMap(yaml.get("network-data")));
    }

    private static final class EncodeFailure extends Exception {
        EncodeFailure(String message) {
            super(message);
        }
    }

    private static List<String> encodeStrict(ItemStack[] items, String area) throws EncodeFailure {
        List<String> out = new ArrayList<>(items == null ? 0 : items.length);
        if (items == null) {
            return out;
        }
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (item == null || item.getType().isAir()) {
                out.add("");
                continue;
            }
            try {
                out.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            } catch (Exception ex) {
                throw new EncodeFailure("item " + item.getType() + " in " + area + " slot " + slot
                        + " failed to serialize (" + ex.getClass().getSimpleName() + ")");
            }
        }
        return out;
    }

    private static List<String> encodeLenient(ItemStack[] items) {
        List<String> out = new ArrayList<>(items == null ? 0 : items.length);
        if (items == null) {
            return out;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || isManagerItem(item)) {
                out.add("");
                continue;
            }
            try {
                out.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            } catch (Exception ex) {
                out.add("");
            }
        }
        return out;
    }

    private static boolean isManagerItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MANAGER_KEY, PersistentDataType.BYTE);
    }

    private static int countFilled(List<String> encoded) {
        int count = 0;
        if (encoded == null) {
            return 0;
        }
        for (String value : encoded) {
            if (value != null && !value.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static int failures(List<String> raw, ItemStack[] decoded) {
        if (raw == null || decoded == null) {
            return 0;
        }
        int failed = 0;
        for (int i = 0; i < raw.size() && i < decoded.length; i++) {
            String value = raw.get(i);
            if (value != null && !value.isBlank() && decoded[i] == null) {
                failed++;
            }
        }
        return failed;
    }

    private static ItemStack[] fit(ItemStack[] items, int size) {
        ItemStack[] out = new ItemStack[Math.max(0, size)];
        if (items == null) {
            return out;
        }
        System.arraycopy(items, 0, out, 0, Math.min(items.length, out.length));
        return out;
    }

    private static ItemStack[] decodeItems(List<String> encoded) {
        if (encoded == null) {
            return null;
        }
        ItemStack[] items = new ItemStack[encoded.size()];
        for (int i = 0; i < encoded.size(); i++) {
            items[i] = decodeItem(encoded.get(i));
        }
        return items;
    }

    private static ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Exception ex) {
            return null;
        }
    }

    private static void applyEffectLine(Player player, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String[] parts = line.split(";");
        if (parts.length < 3) {
            return;
        }
        try {
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) {
                type = PotionEffectType.getByName(parts[0]);
            }
            if (type == null) {
                return;
            }
            int duration = Integer.parseInt(parts[1]);
            int amplifier = Integer.parseInt(parts[2]);
            boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3]);
            boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4]);
            boolean icon = parts.length <= 5 || Boolean.parseBoolean(parts[5]);
            player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        } catch (Exception ignored) {
            // skip malformed effect
        }
    }

    private void moveQuietly(File from, File to) {
        if (from == null || !from.exists()) {
            return;
        }
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Transfer] Could not move " + from.getName() + " → " + to.getName() + ": " + ex.getMessage());
            if (!from.delete() && from.exists()) {
                plugin.getLogger().warning("[Transfer] Could not delete " + from.getAbsolutePath());
            }
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            plugin.getLogger().warning("[Transfer] Could not delete " + file.getAbsolutePath());
        }
    }

    private File charFile(UUID id) {
        return new File(dir, id + ".char.yml");
    }

    private File claimedCharFile(UUID id) {
        return new File(dir, id + ".char.claimed.yml");
    }

    private File outboxFile(UUID id, String snapshotId) {
        return new File(outbox, id + "-" + snapshotId + ".yml");
    }

    private File legacyFile(UUID id) {
        return new File(dir, id + ".yml");
    }

    private File legacyClaimedFile(UUID id) {
        return new File(dir, id + ".claimed.yml");
    }

    /** For admin/debug output. */
    public String describe(UUID id) {
        StringBuilder out = new StringBuilder();
        out.append("holder=").append(ledger.holder(id));
        out.append(" lastSent=").append(ledger.lastSentId(id)).append("→").append(ledger.lastSentTo(id));
        out.append(" pending=").append(hasPendingCharacter(id));
        out.append(" legacyPending=").append(legacyFile(id).isFile() || legacyClaimedFile(id).isFile());
        return out.toString();
    }
}
