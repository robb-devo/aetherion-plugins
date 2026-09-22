package de.aetherion.core.network;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Same-host inventory/XP sync between Velocity backends.
 * Uses Paper {@link ItemStack#serializeAsBytes()} so custom NBT/components survive.
 */
public final class TransferSnapshotStore {

    private final Plugin plugin;
    private File dir;
    private final NetworkPlayerDataSync networkData;
    private final Set<UUID> appliedThisSession = ConcurrentHashMap.newKeySet();

    public TransferSnapshotStore(Plugin plugin) {
        this.plugin = plugin;
        this.networkData = new NetworkPlayerDataSync(plugin);
        this.dir = ServerNames.snapshotDir(plugin);
        ensureDir();
    }

    /** Point both backends at the same Crafty shared folder. */
    public void relocate(File newDir) {
        if (newDir == null) {
            return;
        }
        this.dir = newDir;
        ensureDir();
    }

    public boolean hasSnapshot(java.util.UUID id) {
        if (id == null || dir == null) {
            return false;
        }
        return new File(dir, id.toString() + ".yml").isFile()
                || new File(dir, id.toString() + ".claimed.yml").isFile();
    }

    private void ensureDir() {
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create transfer snapshot dir: " + dir.getAbsolutePath());
        } else {
            plugin.getLogger().info("Transfer snapshots: " + dir.getAbsolutePath());
        }
    }

    public File directory() {
        return dir;
    }

    public void save(Player player) {
        save(player, 0, false, null);
    }

    /**
     * @param pendingFloor 1–3 (or 6 endless) to auto-enter on mmo-d after sync; 0 = hub only
     * @return snapshot timestamp, or {@code -1} if nothing was written
     */
    public long save(Player player, int pendingFloor, boolean bossOnly) {
        return save(player, pendingFloor, bossOnly, null, null);
    }

    /**
     * One snapshot of the live player: inventory, vanilla/Aetherion level bar, ender chest,
     * and flushed skills/coins/progress. Inventory is never cleared — a failed proxy
     * connect must leave the player holding their gear.
     *
     * @param pendingWarp hub spawn id applied on the main world (e.g. {@code capital}); null = default arrival
     * @param toServer Velocity name that may apply this snapshot; null = any backend (legacy)
     * @return snapshot timestamp, or {@code -1} if nothing was written
     */
    public long save(Player player, int pendingFloor, boolean bossOnly, String pendingWarp) {
        return save(player, pendingFloor, bossOnly, pendingWarp, null);
    }

    public long save(Player player, int pendingFloor, boolean bossOnly, String pendingWarp, String toServer) {
        if (player == null || !player.isOnline()) {
            return -1L;
        }
        UUID id = player.getUniqueId();
        File file = fileFor(id);
        YamlConfiguration yaml = new YamlConfiguration();
        long savedAt = System.currentTimeMillis();
        yaml.set("version", 5);
        yaml.set("uuid", id.toString());
        yaml.set("name", player.getName());
        yaml.set("saved-at", savedAt);
        yaml.set("from-server", currentServerName());
        if (toServer != null && !toServer.isBlank()) {
            yaml.set("to-server", toServer.trim().toLowerCase(java.util.Locale.ROOT));
        }
        yaml.set("pending-floor", Math.max(0, pendingFloor));
        yaml.set("pending-boss-only", bossOnly);
        if (pendingWarp != null && !pendingWarp.isBlank()) {
            yaml.set("pending-warp", pendingWarp.trim().toLowerCase(java.util.Locale.ROOT));
        }
        yaml.set("network-data", networkData.exportAll(player));
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("aetherion-level", player.getLevel());
        yaml.set("aetherion-exp", (double) player.getExp());
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
        yaml.set("fire-ticks", player.getFireTicks());
        yaml.set("held-slot", player.getInventory().getHeldItemSlot());

        PlayerInventory inv = player.getInventory();
        List<String> inventory = encodeItems(inv.getContents());
        List<String> armor = encodeItems(inv.getArmorContents());
        List<String> extra = encodeItems(inv.getExtraContents());
        List<String> ender = encodeItems(player.getEnderChest().getContents());
        String cursor = encodeItem(player.getItemOnCursor());
        if (inventory == null || armor == null || extra == null || ender == null || cursor == null) {
            plugin.getLogger().warning("Refusing transfer snapshot for " + player.getName()
                    + " — an item failed to serialize. Inventory was not touched.");
            return -1L;
        }
        yaml.set("inventory-b64", inventory);
        yaml.set("armor-b64", armor);
        yaml.set("extra-b64", extra);
        yaml.set("enderchest-b64", ender);
        yaml.set("cursor-b64", cursor);

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

        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
            plugin.getLogger().info("Saved transfer snapshot v5 for " + player.getName()
                    + " level=" + player.getLevel()
                    + " floor=" + pendingFloor
                    + " warp=" + (pendingWarp == null ? "-" : pendingWarp)
                    + " (" + file.length() + " bytes)");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save transfer snapshot for " + player.getName(), ex);
            return -1L;
        }
        return savedAt;
    }

    /** Drop a snapshot that never left this server (connect failed or the player stayed online). */
    public void discardIfUnclaimed(UUID id, long savedAt) {
        if (id == null || savedAt < 0L) {
            return;
        }
        File live = fileFor(id);
        if (!live.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(live);
        if (yaml.getLong("saved-at", -1L) != savedAt) {
            return;
        }
        if (!live.delete()) {
            plugin.getLogger().warning("Could not discard unused transfer snapshot: " + live.getAbsolutePath());
        } else {
            plugin.getLogger().info("Discarded unused transfer snapshot for " + id);
        }
    }

    public record ApplyResult(boolean applied, int pendingFloor, boolean bossOnly, String pendingWarp) {
        public static ApplyResult none() {
            return new ApplyResult(false, 0, false, null);
        }
    }

    public boolean applyIfPresent(Player player) {
        return applyDetailed(player).applied();
    }

    /** True when a snapshot on disk is meant for this backend (or is legacy and unaddressed). */
    public boolean isAddressedHere(UUID id) {
        File peek = peekFile(id);
        if (peek == null) {
            return false;
        }
        String to = YamlConfiguration.loadConfiguration(peek).getString("to-server");
        String here = currentServerName();
        if (to == null || to.isBlank()
                || here == null || here.isBlank()
                || "unknown".equalsIgnoreCase(here)) {
            return true;
        }
        return to.equalsIgnoreCase(here);
    }

    public ApplyResult applyDetailed(Player player) {
        if (player == null || !player.isOnline()) {
            return ApplyResult.none();
        }
        UUID id = player.getUniqueId();
        File live = fileFor(id);
        File claimed = claimedFileFor(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(live, plugin.getLogger());
        File peek = live.isFile() ? live : (claimed.isFile() ? claimed : null);
        if (peek != null && peek.isFile()) {
            YamlConfiguration preview = YamlConfiguration.loadConfiguration(peek);
            TransferIntent.Action action = TransferIntent.decide(
                    preview.getString("to-server"),
                    preview.getString("from-server"),
                    currentServerName()
            );
            if (action == TransferIntent.Action.LEAVE) {
                return ApplyResult.none();
            }
            if (action == TransferIntent.Action.DISCARD) {
                discardFile(live);
                discardFile(claimed);
                plugin.getLogger().info("Discarded transfer snapshot for " + player.getName()
                        + " — still on " + currentServerName() + ", inventory left in place.");
                return ApplyResult.none();
            }
        }
        File source = claimSnapshot(live, claimed, id);
        if (source == null || !source.isFile()) {
            return ApplyResult.none();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        int pendingFloor = yaml.getInt("pending-floor", 0);
        boolean bossOnly = yaml.getBoolean("pending-boss-only", false);
        String pendingWarp = yaml.getString("pending-warp");
        SlotDecode inventory = decodeSlots(yaml.getStringList("inventory-b64"));
        SlotDecode armor = decodeSlots(yaml.getStringList("armor-b64"));
        SlotDecode extra = decodeSlots(yaml.getStringList("extra-b64"));
        SlotDecode ender = decodeSlots(yaml.getStringList("enderchest-b64"));
        String cursorRaw = yaml.getString("cursor-b64");
        ItemStack cursor = null;
        boolean cursorIntact = true;
        if (cursorRaw != null && !cursorRaw.isBlank()) {
            cursor = decodeItem(cursorRaw);
            cursorIntact = cursor != null;
        }
        boolean applyInventory = TransferProgressGuard.applyItemSection(
                inventory.occupied(), occupied(player.getInventory().getContents()), inventory.intact());
        boolean applyArmor = TransferProgressGuard.applyItemSection(
                armor.occupied(), occupied(player.getInventory().getArmorContents()), armor.intact());
        boolean applyExtra = TransferProgressGuard.applyItemSection(
                extra.occupied(), occupied(player.getInventory().getExtraContents()), extra.intact());
        boolean applyEnder = TransferProgressGuard.applyItemSection(
                ender.occupied(), occupied(player.getEnderChest().getContents()), ender.intact());
        if (!applyInventory || !applyArmor || !applyExtra || !applyEnder || !cursorIntact) {
            plugin.getLogger().warning("Kept live gear for " + player.getName()
                    + " — snapshot inventory was empty or only partly readable"
                    + " (inv=" + applyInventory + " armor=" + applyArmor
                    + " extra=" + applyExtra + " ender=" + applyEnder
                    + " cursor=" + cursorIntact + ").");
        }
        ItemStack[] invBackup = cloneItems(player.getInventory().getContents());
        ItemStack[] armorBackup = cloneItems(player.getInventory().getArmorContents());
        ItemStack[] extraBackup = cloneItems(player.getInventory().getExtraContents());
        ItemStack[] enderBackup = cloneItems(player.getEnderChest().getContents());
        ItemStack cursorBackup = player.getItemOnCursor() == null ? null : player.getItemOnCursor().clone();
        try {
            player.closeInventory();
            if (applyInventory) {
                player.getInventory().setContents(inventory.items());
            }
            if (applyArmor) {
                player.getInventory().setArmorContents(armor.items());
            }
            if (applyExtra) {
                player.getInventory().setExtraContents(extra.items());
            }
            if (applyEnder) {
                player.getEnderChest().setContents(ender.items());
            }
            if (cursorIntact) {
                player.setItemOnCursor(cursor);
            }

            int held = yaml.getInt("held-slot", player.getInventory().getHeldItemSlot());
            if (held >= 0 && held <= 8) {
                player.getInventory().setHeldItemSlot(held);
            }

            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }

            int snapshotLevel = yaml.getInt("aetherion-level", yaml.getInt("level", player.getLevel()));
            int liveLevel = player.getLevel();
            float exp = (float) yaml.getDouble("aetherion-exp", yaml.getDouble("exp", player.getExp()));
            java.util.Map<String, Object> networkPreview = NetworkPlayerDataSync.toPlainMap(yaml.get("network-data"));
            boolean skillsInSnapshot = NetworkDataKeys.lookupYaml(networkPreview, NetworkDataKeys.SKILLS_FILE) != null;
            boolean sharedRich = sharedSkillsRich(id);
            boolean writeSnapshotLevel = TransferProgressGuard.applySnapshotLevel(
                    snapshotLevel, liveLevel, skillsInSnapshot, sharedRich);
            if (!writeSnapshotLevel) {
                plugin.getLogger().warning("Kept live level " + liveLevel + " for " + player.getName()
                        + " — snapshot level " + snapshotLevel
                        + " skillsInSnapshot=" + skillsInSnapshot
                        + " sharedRich=" + sharedRich);
            } else {
                player.setLevel(snapshotLevel);
                player.setExp(exp);
                player.setTotalExperience(yaml.getInt("total-exp", player.getTotalExperience()));
            }
            player.setFoodLevel(yaml.getInt("food", player.getFoodLevel()));
            player.setSaturation((float) yaml.getDouble("saturation", player.getSaturation()));
            player.setExhaustion((float) yaml.getDouble("exhaustion", player.getExhaustion()));
            player.setFireTicks(yaml.getInt("fire-ticks", 0));

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
            if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
            }
            double health = yaml.getDouble("health", player.getHealth());
            player.setHealth(Math.max(1.0, Math.min(health, player.getMaxHealth())));

            Collection<String> effectLines = yaml.getStringList("effects");
            for (String line : effectLines) {
                applyEffectLine(player, line);
            }

            player.updateInventory();
            java.util.Map<String, Object> networkMap = networkPreview;
            boolean skillsImported = skillsInSnapshot;
            if (!networkMap.isEmpty()) {
                networkData.importAll(player, networkMap);
            } else {
                plugin.getLogger().warning("Transfer snapshot for " + player.getName()
                        + " had no usable network-data (pets/skills/level may stay local).");
            }
            boolean syncFromSkills = skillsImported || sharedRich || !writeSnapshotLevel;
            assertLevel(player, writeSnapshotLevel ? snapshotLevel : liveLevel, exp, syncFromSkills);
            networkData.resetLoadoutRuntime(player);
            if (!source.delete() && source.exists()) {
                plugin.getLogger().warning("Could not delete used snapshot: " + source.getAbsolutePath());
            }
            live.delete();
            appliedThisSession.add(id);
            final ItemStack[] invCopy = applyInventory && inventory.items() != null ? inventory.items().clone() : null;
            final ItemStack[] armorCopy = applyArmor && armor.items() != null ? armor.items().clone() : null;
            final ItemStack[] extraCopy = applyExtra && extra.items() != null ? extra.items().clone() : null;
            final boolean skills = syncFromSkills;
            final int levelCopy = writeSnapshotLevel ? snapshotLevel : liveLevel;
            final float expCopy = exp;
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
                    player.getInventory().setExtraContents(extraCopy);
                }
                assertLevel(player, levelCopy, expCopy, skills);
                player.updateInventory();
            }, 30L);
            plugin.getLogger().info("Applied transfer snapshot v5 for " + player.getName()
                    + " level=" + (writeSnapshotLevel ? snapshotLevel : liveLevel)
                    + " snapshotLevel=" + snapshotLevel
                    + " skills=" + skillsImported
                    + " keptLiveLevel=" + !writeSnapshotLevel
                    + " keptLiveInv=" + !applyInventory
                    + " pendingFloor=" + pendingFloor
                    + " warp=" + (pendingWarp == null ? "-" : pendingWarp));
            return new ApplyResult(true, pendingFloor, bossOnly, pendingWarp);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply transfer snapshot for " + player.getName()
                    + " — restoring the inventory they joined with.", ex);
            restore(player, invBackup, armorBackup, extraBackup, enderBackup);
            if (player.isOnline()) {
                if (cursorBackup == null || cursorBackup.getType().isAir()) {
                    player.setItemOnCursor(null);
                } else {
                    player.setItemOnCursor(cursorBackup);
                }
            }
            return ApplyResult.none();
        }
    }

    /**
     * Skills import owns the Aetherion level bar. If the snapshot had no skills
     * section, keep the level that was on the player when the snapshot was written.
     */
    private static void assertLevel(Player player, int level, float exp, boolean skillsImported) {
        if (skillsImported) {
            de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
            if (progress != null) {
                progress.syncAccountLevel(player);
                return;
            }
        }
        player.setLevel(Math.max(0, level));
        player.setExp(Math.max(0f, Math.min(1f, exp)));
    }

    private static void restore(Player player, ItemStack[] inventory, ItemStack[] armor, ItemStack[] extra, ItemStack[] ender) {
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
        player.updateInventory();
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    private static String currentServerName() {
        de.aetherion.core.AetherionCore core = de.aetherion.core.AetherionCore.get();
        if (core != null && core.link() != null) {
            return core.link().serverName();
        }
        return "unknown";
    }

    private void discardFile(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not discard transfer snapshot: " + file.getAbsolutePath());
        }
    }

    private File peekFile(UUID id) {
        if (id == null || dir == null) {
            return null;
        }
        File live = fileFor(id);
        if (live.isFile()) {
            return live;
        }
        File claimed = claimedFileFor(id);
        return claimed.isFile() ? claimed : null;
    }

    private File fileFor(UUID id) {
        return new File(dir, id.toString() + ".yml");
    }

    private File claimedFileFor(UUID id) {
        return new File(dir, id.toString() + ".claimed.yml");
    }

    /**
     * Atomically claim {@code uuid.yml} → {@code uuid.claimed.yml} so a second join
     * cannot apply the same snapshot. A leftover claimed file is retried once
     * (inventory/coin overlay is set-not-add).
     */
    private File claimSnapshot(File live, File claimed, UUID id) {
        if (live != null && live.isFile()) {
            appliedThisSession.remove(id);
            try {
                Files.move(
                        live.toPath(),
                        claimed.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
                return claimed;
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not claim transfer snapshot for " + id + ": " + ex.getMessage());
                return live;
            }
        }
        if (claimed != null && claimed.isFile()) {
            if (appliedThisSession.contains(id)) {
                if (!claimed.delete()) {
                    plugin.getLogger().warning("Could not delete already-applied snapshot: " + claimed.getAbsolutePath());
                }
                return null;
            }
            return claimed;
        }
        return null;
    }

    private boolean sharedSkillsRich(UUID id) {
        if (id == null) {
            return false;
        }
        org.bukkit.plugin.Plugin items = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null) {
            return false;
        }
        File file = new File(items.getDataFolder(), "skills.yml");
        if (!file.isFile()) {
            return false;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        Object section = yaml.get("players." + id);
        if (section == null) {
            return false;
        }
        return TransferProgressGuard.richness(NetworkPlayerDataSync.toPlainMap(section)) > 0L;
    }

    private record SlotDecode(ItemStack[] items, boolean intact, int occupied) {
        static SlotDecode failed() {
            return new SlotDecode(null, false, 0);
        }
    }

    /** Empty list is an intact empty inventory. A non-blank slot that will not decode is not intact. */
    private static SlotDecode decodeSlots(java.util.List<String> encoded) {
        if (encoded == null) {
            return SlotDecode.failed();
        }
        ItemStack[] items = new ItemStack[encoded.size()];
        int occupied = 0;
        for (int i = 0; i < encoded.size(); i++) {
            String raw = encoded.get(i);
            if (raw == null || raw.isBlank()) {
                items[i] = null;
                continue;
            }
            ItemStack item = decodeItem(raw);
            if (item == null) {
                return SlotDecode.failed();
            }
            items[i] = item;
            if (!item.getType().isAir()) {
                occupied++;
            }
        }
        return new SlotDecode(items, true, occupied);
    }

    private static int occupied(ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    /** {@code null} when any slot fails to serialize — callers must abort the snapshot. */
    private static List<String> encodeItems(ItemStack[] items) {
        List<String> out = new ArrayList<>(items == null ? 0 : items.length);
        if (items == null) {
            return out;
        }
        for (ItemStack item : items) {
            String encoded = encodeItem(item);
            if (encoded == null) {
                return null;
            }
            out.add(encoded);
        }
        return out;
    }

    /** Empty string is an empty slot. {@code null} means serialize failed. */
    private static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception ex) {
            return null;
        }
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
            PotionEffectType type = PotionEffectType.getByKey(
                    org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase())
            );
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
}
