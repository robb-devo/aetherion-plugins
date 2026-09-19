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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Same-host inventory/XP sync between Velocity backends.
 * Uses Paper {@link ItemStack#serializeAsBytes()} so custom NBT/components survive.
 */
public final class TransferSnapshotStore {

    private final Plugin plugin;
    private final File dir;
    private final NetworkPlayerDataSync networkData;

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
        save(player, 0, false);
    }

    /**
     * @param pendingFloor 1–3 (or 6 endless) to auto-enter on mmo-d after sync; 0 = hub only
     */
    public void save(Player player, int pendingFloor, boolean bossOnly) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        File file = fileFor(id);
        networkData.flushPlayer(player);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 4);
        yaml.set("uuid", id.toString());
        yaml.set("name", player.getName());
        yaml.set("saved-at", System.currentTimeMillis());
        yaml.set("from-server", plugin.getConfig().getString("role", "unknown"));
        yaml.set("pending-floor", Math.max(0, pendingFloor));
        yaml.set("pending-boss-only", bossOnly);
        yaml.set("network-data", networkData.exportAll(player));
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
            yaml.save(file);
            plugin.getLogger().info("Saved transfer snapshot v4 for " + player.getName()
                    + " floor=" + pendingFloor + " (" + file.length() + " bytes)");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save transfer snapshot for " + player.getName(), ex);
            return;
        }

        // So this backend's player.dat does not keep a stale full inventory after Velocity move.
        clearLivingInventory(player);
    }

    public record ApplyResult(boolean applied, int pendingFloor, boolean bossOnly) {
        public static ApplyResult none() {
            return new ApplyResult(false, 0, false);
        }
    }

    public boolean applyIfPresent(Player player) {
        return applyDetailed(player).applied();
    }

    public ApplyResult applyDetailed(Player player) {
        if (player == null || !player.isOnline()) {
            return ApplyResult.none();
        }
        File file = fileFor(player.getUniqueId());
        if (!file.isFile()) {
            return ApplyResult.none();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int pendingFloor = yaml.getInt("pending-floor", 0);
        boolean bossOnly = yaml.getBoolean("pending-boss-only", false);
        try {
            player.closeInventory();
            player.setItemOnCursor(null);
            player.getInventory().clear();
            player.getEnderChest().clear();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            ItemStack[] inventory = decodeItems(yaml.getStringList("inventory-b64"));
            ItemStack[] armor = decodeItems(yaml.getStringList("armor-b64"));
            ItemStack[] extra = decodeItems(yaml.getStringList("extra-b64"));
            ItemStack[] ender = decodeItems(yaml.getStringList("enderchest-b64"));
            ItemStack cursor = decodeItem(yaml.getString("cursor-b64"));

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

            player.setLevel(yaml.getInt("level", player.getLevel()));
            player.setExp((float) yaml.getDouble("exp", player.getExp()));
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
            if (!networkMap.isEmpty()) {
                networkData.importAll(player, networkMap);
            } else {
                plugin.getLogger().warning("Transfer snapshot for " + player.getName()
                        + " had no usable network-data (pets/skills/level may stay local).");
            }
            networkData.resetLoadoutRuntime(player);
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete used snapshot: " + file.getAbsolutePath());
            }
            // Re-assert inventory after loadout/join hooks (tick 25).
            final ItemStack[] invCopy = inventory == null ? null : inventory.clone();
            final ItemStack[] armorCopy = armor == null ? null : armor.clone();
            final ItemStack[] extraCopy = extra == null ? null : extra.clone();
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
                player.updateInventory();
            }, 25L);
            plugin.getLogger().info("Applied transfer snapshot v4 for " + player.getName()
                    + " pendingFloor=" + pendingFloor);
            return new ApplyResult(true, pendingFloor, bossOnly);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply transfer snapshot for " + player.getName(), ex);
            return ApplyResult.none();
        }
    }

    private static void clearLivingInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setExtraContents(null);
        player.updateInventory();
    }

    private File fileFor(UUID id) {
        return new File(dir, id.toString() + ".yml");
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
