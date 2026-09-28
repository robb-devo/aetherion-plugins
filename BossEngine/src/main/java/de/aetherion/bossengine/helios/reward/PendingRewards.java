package de.aetherion.bossengine.helios.reward;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shares that could not be handed over (the player logged out before the capsule opened). The instance
 * world is wiped after every fight, so dropping them there would destroy them: they wait in
 * {@code helios/pending.yml} and are delivered on the owner's next login.
 */
public final class PendingRewards {

    private static File file;
    private static Logger log;

    private PendingRewards() {
    }

    public static void init(File dataFolder, Logger logger) {
        file = new File(dataFolder, "helios/pending.yml");
        log = logger;
        AtomicYaml.recoverTemp(file, logger);
    }

    public static synchronized void store(UUID owner, List<ItemStack> items) {
        if (file == null || owner == null || items == null || items.isEmpty()) {
            return;
        }
        YamlConfiguration yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        List<ItemStack> list = read(yaml, owner);
        for (ItemStack i : items) {
            if (i != null && !i.getType().isAir()) {
                list.add(i.clone());
            }
        }
        yaml.set(owner.toString(), list);
        save(yaml);
    }

    /** Delivers everything waiting for {@code p}. @return how many stacks were delivered */
    public static synchronized int deliver(Player p) {
        if (file == null || p == null || !file.isFile()) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> list = read(yaml, p.getUniqueId());
        if (list.isEmpty()) {
            return 0;
        }
        yaml.set(p.getUniqueId().toString(), null);
        save(yaml);
        for (ItemStack i : list) {
            Delivery.give(p, i);
        }
        return list.size();
    }

    private static List<ItemStack> read(YamlConfiguration yaml, UUID owner) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = yaml.getList(owner.toString());
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof ItemStack i) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private static void save(YamlConfiguration yaml) {
        try {
            AtomicYaml.save(yaml, file, log);
        } catch (IOException e) {
            if (log != null) {
                log.warning("[Helios] Could not save pending rewards: " + e.getMessage());
            }
        }
    }

    /** Hands an item to a player the way the rest of Aetherion does (booster storage first). */
    public static final class Delivery {
        private Delivery() {
        }

        public static void give(Player p, ItemStack item) {
            if (p == null || item == null || item.getType().isAir()) {
                return;
            }
            try {
                Class<?> delivery = Class.forName("de.aetherion.items.storage.BoosterDelivery");
                delivery.getMethod("giveReward", Player.class, ItemStack.class).invoke(null, p, item.clone());
                return;
            } catch (ReflectiveOperationException | NoClassDefFoundError | RuntimeException ignored) {
                // AetherionItems absent or older: plain inventory below
            }
            p.getInventory().addItem(item.clone()).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }
}
