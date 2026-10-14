package de.aetherion.core.network;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Transfer v6 for backends without the AetherionDungeons jar (mmo-c, mmo-i): a faithful port of
 * Dungeons' {@code TransferSnapshotStore} character path. Same files in the same shared folder
 * ({@code <uuid>.char.yml}, {@code outbox/}, {@code applied/}, {@code quarantine/}, {@code rollback/},
 * {@code ledger/}), so a character packed on mmo-r applies on mmo-i and the other way round.
 *
 * <p>Three deliberate differences to the Dungeons store:
 * <ul>
 *     <li><b>Travel as SURVIVAL, no flight</b> (non-staff). The exile forces ADVENTURE / SPECTATOR and the
 *     isles grant island flight; neither may follow the character to the main world.</li>
 *     <li><b>No leftover adoption.</b> The exile backend used to receive <i>copies</i> of the main inventory
 *     (v5 never emptied the source), so whatever its playerdata still holds is a stale copy, not residue.
 *     A non-empty live inventory is always backed up to {@code quarantine/} and replaced — never merged.</li>
 *     <li><b>No legacy apply.</b> {@code <uuid>.yml} (v5) stays with Core's {@link TransferSnapshotStore};
 *     when a v6 character is applied here, a legacy file addressed to this backend is parked as superseded.</li>
 * </ul>
 */
public final class CharacterStore {

    public static final int CHAR_VERSION = 6;
    private static final NamespacedKey MANAGER_KEY = new NamespacedKey("aetherionitems", "manager");
    private static final char NETWORK_SEPARATOR = '\u001F';

    private final Plugin plugin;
    private final File dir;
    private final File outbox;
    private final File appliedDir;
    private final File quarantine;
    private final File rollback;
    private final NetworkPlayerDataSync networkData;
    private final CharacterLedger ledger;
    private final String here;

    public CharacterStore(Plugin plugin, File dir, String here) {
        this.plugin = plugin;
        this.dir = dir;
        this.here = here == null ? "unknown" : here.trim().toLowerCase(Locale.ROOT);
        this.networkData = new NetworkPlayerDataSync(plugin);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Transfer] Could not create transfer dir: " + dir.getAbsolutePath());
        }
        this.outbox = subdir("outbox");
        this.appliedDir = subdir("applied");
        this.quarantine = subdir("quarantine");
        this.rollback = subdir("rollback");
        this.ledger = new CharacterLedger(plugin, new File(dir, "ledger"));
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

    public CharacterLedger ledger() {
        return ledger;
    }

    public String serverName() {
        return here;
    }

    // ------------------------------------------------------------------ save

    public record SaveResult(boolean ok, String snapshotId, String error) {
        static SaveResult failed(String error) {
            return new SaveResult(false, "", error);
        }
    }

    /**
     * Pack the whole character into {@code <uuid>.char.yml} (+ outbox backup) and mark the ledger.
     * Does NOT clear the live inventory — the caller decides when the handoff is final.
     */
    public SaveResult saveCharacter(Player player, String reason, String toServer) {
        if (player == null) {
            return SaveResult.failed("no player");
        }
        UUID id = player.getUniqueId();
        String snapshotId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        // Close GUIs + flush progression FIRST: closing a trade/bank GUI can hand items back.
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
        yaml.set("from-server", here);
        yaml.set("from-name", here);
        yaml.set("to-server", toServer == null ? "" : toServer.toLowerCase(Locale.ROOT));
        yaml.set("reason", reason == null ? "" : reason);
        yaml.set("pending-floor", 0);
        yaml.set("pending-boss-only", false);
        yaml.set("dc-floor", 0);
        Location at = player.getLocation();
        if (at.getWorld() != null) {
            yaml.set("location.world", at.getWorld().getName());
            yaml.set("location.x", at.getX());
            yaml.set("location.y", at.getY());
            yaml.set("location.z", at.getZ());
            yaml.set("location.yaw", (double) at.getYaw());
            yaml.set("location.pitch", (double) at.getPitch());
        }
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
        // Exile forces ADVENTURE (and the ferry cutscene SPECTATOR); islands grant island flight. Neither may
        // follow a player home: non-staff always travel as SURVIVAL, feet on the ground.
        boolean staff = player.hasPermission("aetherion.core.admin");
        GameMode travelMode = staff ? player.getGameMode() : GameMode.SURVIVAL;
        yaml.set("gamemode", travelMode.name());
        yaml.set("allow-flight", staff && player.getAllowFlight());
        yaml.set("flying", staff && player.isFlying());
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
        plugin.getLogger().info("[Transfer] ROLLBACK id=" + snapshotId + " uuid=" + id
                + " — nobody claimed it, character stays on " + here);
        return true;
    }

    // ------------------------------------------------------------------ apply

    public record ApplyResult(boolean applied, String snapshotId, String reason, String fromName) {
        public static ApplyResult none() {
            return new ApplyResult(false, "", "", "");
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

    /** Join hook: claim + apply the pending v6 character, once. */
    public ApplyResult applyOnJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return ApplyResult.none();
        }
        File claimed = claimCharacter(player.getUniqueId());
        if (claimed == null) {
            return ApplyResult.none();
        }
        ApplyResult result = applyCharacterFile(player, claimed);
        if (result.applied()) {
            parkSupersededLegacy(player);
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
        return applyCharacterFile(player, claimed);
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

    private ApplyResult applyCharacterFile(Player player, File source) {
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
            player.sendMessage("§cYour travel data could not be read. Staff can restore it — nothing was deleted.");
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
        String reason = yaml.getString("reason", "");
        boolean sameServer = fromName != null && fromName.equalsIgnoreCase(here);
        ItemStack[] invBackup = cloneAll(player.getInventory().getContents());
        ItemStack[] armorBackup = cloneAll(player.getInventory().getArmorContents());
        ItemStack[] extraBackup = cloneAll(player.getInventory().getExtraContents());
        ItemStack[] enderBackup = cloneAll(player.getEnderChest().getContents());
        ItemStack cursorBackup = player.getItemOnCursor() == null ? null : player.getItemOnCursor().clone();
        boolean clearedLive = false;
        try {
            // Never merge: whatever this backend's playerdata holds is a stale copy at best.
            backupLiveIfNotEmpty(player, snapshotId);
            player.closeInventory();
            player.setItemOnCursor(null);
            player.getInventory().clear();
            player.getEnderChest().clear();
            clearedLive = true;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            List<String> invRaw = yaml.getStringList("inventory-b64");
            List<String> armorRaw = yaml.getStringList("armor-b64");
            List<String> extraRaw = yaml.getStringList("extra-b64");
            List<String> enderRaw = yaml.getStringList("enderchest-b64");
            ItemStack[] inventory = decodeItems(invRaw);
            ItemStack[] armor = decodeItems(armorRaw);
            ItemStack[] extra = decodeItems(extraRaw);
            ItemStack[] ender = decodeItems(enderRaw);
            ItemStack cursor = decodeItem(yaml.getString("cursor-b64"));
            int decodeFailures = failures(invRaw, inventory) + failures(armorRaw, armor)
                    + failures(extraRaw, extra) + failures(enderRaw, ender);
            if (decodeFailures > 0) {
                restoreLive(player, invBackup, armorBackup, extraBackup, enderBackup, cursorBackup);
                plugin.getLogger().severe("[Transfer] " + decodeFailures + " item(s) in snapshot " + snapshotId
                        + " for " + player.getName() + " could not be decoded — live inventory restored, claim kept for retry: "
                        + source.getAbsolutePath());
                player.sendMessage("§cYour travel data was damaged. Your inventory was restored — try rejoining or ask staff.");
                return ApplyResult.none();
            }
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
            player.updateInventory();

            // Progression files only when the snapshot came from ANOTHER backend. On a same-backend
            // relog the local files are newer and must win.
            if (!sameServer) {
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

            // Re-assert after loadout / join hooks (tick 25). The player is frozen until then.
            final ItemStack[] invCopy = cloneAll(player.getInventory().getContents());
            final ItemStack[] armorCopy = cloneAll(player.getInventory().getArmorContents());
            final ItemStack[] extraCopy = cloneAll(player.getInventory().getExtraContents());
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

            plugin.getLogger().info("[Transfer] APPLY id=" + snapshotId
                    + " player=" + player.getName()
                    + " from=" + (fromName == null || fromName.isBlank() ? "?" : fromName)
                    + " here=" + here
                    + " reason=" + reason
                    + " inv=" + yaml.getInt("count.inventory", -1)
                    + (sameServer ? " progress=local" : " progress=imported"));
            return new ApplyResult(true, snapshotId, reason, fromName == null ? "" : fromName);
        } catch (Exception ex) {
            if (clearedLive) {
                restoreLive(player, invBackup, armorBackup, extraBackup, enderBackup, cursorBackup);
            }
            plugin.getLogger().log(Level.SEVERE, "[Transfer] FAILED to apply " + snapshotId + " for " + player.getName()
                    + " — live inventory restored; claimed file kept for the next join: " + source.getAbsolutePath(), ex);
            if (player.isOnline()) {
                player.sendMessage("§cYour gear could not be unpacked. Your inventory was restored — try rejoining.");
            }
            return ApplyResult.none();
        }
    }

    /**
     * A v5 {@code <uuid>.yml} addressed to this backend is older than the v6 character that was just applied
     * (new jars only write v6 between character backends). Park it so Core's v5 path cannot apply it on top.
     */
    private void parkSupersededLegacy(Player player) {
        UUID id = player.getUniqueId();
        for (File legacy : new File[]{new File(dir, id + ".yml"), new File(dir, id + ".claimed.yml")}) {
            if (!legacy.isFile()) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
            TransferIntent.Action action = TransferIntent.decide(
                    yaml.getString("to-server"), yaml.getString("from-server"), here);
            if (action == TransferIntent.Action.LEAVE) {
                continue;
            }
            File parked = new File(quarantine, id + "-" + System.currentTimeMillis() + "-legacy-superseded.yml");
            moveQuietly(legacy, parked);
            plugin.getLogger().warning("[Transfer] Legacy v" + yaml.getInt("version", 4) + " snapshot for "
                    + player.getName() + " parked (v6 character applied): " + parked.getName());
        }
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
            stageEarlyFiles(id, decodeNetwork(yaml), mainWorldFolder);
        } catch (Exception ex) {
            plugin.getLogger().warning("[Transfer] Pre-login staging skipped for " + id + ": " + ex.getMessage());
        }
    }

    private void stageEarlyFiles(UUID id, Map<String, Object> plain, File mainWorldFolder) {
        if (id == null || plain == null || plain.isEmpty()) {
            return;
        }
        String key = id.toString();
        if (mainWorldFolder != null) {
            Object stats = NetworkDataKeys.lookupBlob(plain, "stats");
            if (stats instanceof String raw) {
                writeString(new File(mainWorldFolder, "stats/" + key + ".json"), raw);
            }
            Object adv = NetworkDataKeys.lookupBlob(plain, "advancements");
            if (adv instanceof String raw) {
                writeString(new File(mainWorldFolder, "advancements/" + key + ".json"), raw);
            }
        }
        Plugin quests = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionQuests");
        if (quests != null) {
            Object q1 = NetworkDataKeys.lookupBlob(plain, "quests");
            if (q1 instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "players/" + key + ".yml"), raw);
            }
            Object q2 = NetworkDataKeys.lookupBlob(plain, "quests2");
            if (q2 instanceof String raw) {
                writeString(new File(quests.getDataFolder(), "data/" + key + ".yml"), raw);
            }
        }
    }

    private void writeString(File file, String raw) {
        try {
            de.aetherion.core.persist.AtomicYaml.writeString(file, raw);
        } catch (Exception ex) {
            plugin.getLogger().warning("[Transfer] Could not stage " + file.getName() + ": " + ex.getMessage());
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

    /**
     * Guest session (the real character is held elsewhere): whatever this backend's playerdata still holds is a
     * stale copy. Park it in {@code quarantine/} and empty the live inventory, so a guest can never hand out
     * items that also exist in the real character.
     */
    public void stashLiveForGuest(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        backupLiveIfNotEmpty(player, "guest-session");
        clearLocalCharacter(player);
    }

    private void backupLiveIfNotEmpty(Player player, String incomingId) {
        List<String> inv = encodeLenient(player.getInventory().getContents());
        List<String> armor = encodeLenient(player.getInventory().getArmorContents());
        List<String> ender = encodeLenient(player.getEnderChest().getContents());
        int filled = countFilled(inv) + countFilled(armor) + countFilled(ender);
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
        backup.set("armor-b64", armor);
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

    /** After a failed apply that already cleared the player — put what they joined with back. */
    private static void restoreLive(
            Player player,
            ItemStack[] inventory,
            ItemStack[] armor,
            ItemStack[] extra,
            ItemStack[] ender,
            ItemStack cursor
    ) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (inventory != null) {
            player.getInventory().setContents(inventory);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (extra != null) {
            player.getInventory().setExtraContents(extra);
        }
        if (ender != null) {
            player.getEnderChest().setContents(ender);
        }
        if (cursor == null || cursor.getType().isAir()) {
            player.setItemOnCursor(null);
        } else {
            player.setItemOnCursor(cursor);
        }
        player.updateInventory();
    }

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
                return new LinkedHashMap<>();
            }
            return NetworkPlayerDataSync.toPlainMap(box.get("network-data"));
        }
        return repairLegacyKeys(NetworkPlayerDataSync.toPlainMap(yaml.get("network-data")));
    }

    /**
     * Snapshots before v6 stored {@code yaml:coins.yml -> players.<uuid>} inside a normal YAML
     * document, and Bukkit split the dots on load. Rebuild flat keys for pending files.
     */
    static Map<String, Object> repairLegacyKeys(Map<String, Object> plain) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (plain == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : plain.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name.startsWith("yaml:") && !name.endsWith(".yml") && value instanceof Map<?, ?> nested
                    && nested.size() == 1 && nested.get("yml") instanceof Map<?, ?> inner) {
                Map<String, Object> flat = new LinkedHashMap<>();
                for (Map.Entry<?, ?> section : inner.entrySet()) {
                    if (section.getValue() instanceof Map<?, ?> perPlayer) {
                        for (Map.Entry<?, ?> player : perPlayer.entrySet()) {
                            flat.put(section.getKey() + "." + player.getKey(), player.getValue());
                        }
                    }
                }
                out.put(name + ".yml", flat);
            } else {
                out.put(name, value);
            }
        }
        return out;
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

    /** For admin/debug output. */
    public String describe(UUID id) {
        return "holder=" + ledger.holder(id)
                + " lastSent=" + ledger.lastSentId(id) + "→" + ledger.lastSentTo(id)
                + " pending=" + hasPendingCharacter(id);
    }
}
