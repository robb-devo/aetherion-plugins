package de.aetherion.dungeons.bridge;

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
    private final File dir;
    private final NetworkPlayerDataSync networkData;
    private final Set<UUID> appliedThisSession = ConcurrentHashMap.newKeySet();

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
        return save(player, pendingFloor, bossOnly, null);
    }

    /**
     * One snapshot of the live player: inventory, vanilla/Aetherion level bar, ender chest,
     * and flushed skills/coins/progress. Inventory is never cleared — a failed proxy
     * connect must leave the player holding their gear.
     *
     * @param pendingWarp hub spawn id applied on the main world (e.g. {@code capital}); null = default arrival
     * @return snapshot timestamp, or {@code -1} if nothing was written
     */
    public long save(Player player, int pendingFloor, boolean bossOnly, String pendingWarp) {
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
        yaml.set("from-server", plugin.getConfig().getString("role", "unknown"));
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
        yaml.set("inventory-b64", encodeItems(inv.getContents()));
        yaml.set("armor-b64", encodeItems(inv.getArmorContents()));
        yaml.set("extra-b64", encodeItems(inv.getExtraContents()));
        yaml.set("enderchest-b64", encodeItems(player.getEnderChest().getContents()));
        yaml.set("cursor-b64", encodeItem(player.getItemOnCursor()));

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

    public ApplyResult applyDetailed(Player player) {
        if (player == null || !player.isOnline()) {
            return ApplyResult.none();
        }
        UUID id = player.getUniqueId();
        File live = fileFor(id);
        File claimed = claimedFileFor(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(live, plugin.getLogger());
        File source = claimSnapshot(live, claimed, id);
        if (source == null || !source.isFile()) {
            return ApplyResult.none();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        int pendingFloor = yaml.getInt("pending-floor", 0);
        boolean bossOnly = yaml.getBoolean("pending-boss-only", false);
        String pendingWarp = yaml.getString("pending-warp");
        ItemStack[] inventory = decodeItems(yaml.getStringList("inventory-b64"));
        ItemStack[] armor = decodeItems(yaml.getStringList("armor-b64"));
        ItemStack[] extra = decodeItems(yaml.getStringList("extra-b64"));
        ItemStack[] ender = decodeItems(yaml.getStringList("enderchest-b64"));
        ItemStack cursor = decodeItem(yaml.getString("cursor-b64"));
        ItemStack[] invBackup = cloneItems(player.getInventory().getContents());
        ItemStack[] armorBackup = cloneItems(player.getInventory().getArmorContents());
        ItemStack[] extraBackup = cloneItems(player.getInventory().getExtraContents());
        ItemStack[] enderBackup = cloneItems(player.getEnderChest().getContents());
        try {
            player.closeInventory();
            player.setItemOnCursor(null);
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
            if (cursor != null && !cursor.getType().isAir()) {
                player.setItemOnCursor(cursor);
            }

            int held = yaml.getInt("held-slot", player.getInventory().getHeldItemSlot());
            if (held >= 0 && held <= 8) {
                player.getInventory().setHeldItemSlot(held);
            }

            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }

            int level = yaml.getInt("aetherion-level", yaml.getInt("level", player.getLevel()));
            float exp = (float) yaml.getDouble("aetherion-exp", yaml.getDouble("exp", player.getExp()));
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(yaml.getInt("total-exp", player.getTotalExperience()));
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
            java.util.Map<String, Object> networkMap = NetworkPlayerDataSync.toPlainMap(yaml.get("network-data"));
            boolean skillsImported = networkMap.containsKey("yaml:skills.yml");
            if (!networkMap.isEmpty()) {
                networkData.importAll(player, networkMap);
            } else {
                plugin.getLogger().warning("Transfer snapshot for " + player.getName()
                        + " had no usable network-data (pets/skills/level may stay local).");
            }
            assertLevel(player, level, exp, skillsImported);
            networkData.resetLoadoutRuntime(player);
            if (!source.delete() && source.exists()) {
                plugin.getLogger().warning("Could not delete used snapshot: " + source.getAbsolutePath());
            }
            live.delete();
            appliedThisSession.add(id);
            final ItemStack[] invCopy = inventory == null ? null : inventory.clone();
            final ItemStack[] armorCopy = armor == null ? null : armor.clone();
            final ItemStack[] extraCopy = extra == null ? null : extra.clone();
            final boolean skills = skillsImported;
            final int levelCopy = level;
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
                    + " level=" + level
                    + " skills=" + skillsImported
                    + " pendingFloor=" + pendingFloor
                    + " warp=" + (pendingWarp == null ? "-" : pendingWarp));
            return new ApplyResult(true, pendingFloor, bossOnly, pendingWarp);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply transfer snapshot for " + player.getName()
                    + " — restoring the inventory they joined with.", ex);
            restore(player, invBackup, armorBackup, extraBackup, enderBackup);
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

    private static List<String> encodeItems(ItemStack[] items) {
        List<String> out = new ArrayList<>(items == null ? 0 : items.length);
        if (items == null) {
            return out;
        }
        for (ItemStack item : items) {
            out.add(encodeItem(item));
        }
        return out;
    }

    private static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception ex) {
            return "";
        }
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
