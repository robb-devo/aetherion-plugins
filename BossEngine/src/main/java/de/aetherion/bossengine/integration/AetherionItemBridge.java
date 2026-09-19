package de.aetherion.bossengine.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Soft-bridge. The concrete AetherionItems classes are only loaded when that plugin is present.
 */
public class AetherionItemBridge {

    private final Logger logger;
    private ItemFactory factory;

    public AetherionItemBridge(Logger logger) {
        this.logger = logger;
        hook();
    }

    public boolean isAvailable() {
        return factory != null && Bukkit.getPluginManager().isPluginEnabled("AetherionItems");
    }

    public void hook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (plugin == null || !plugin.isEnabled()) {
            factory = null;
            return;
        }
        try {
            factory = (ItemFactory) Class.forName(
                    "de.aetherion.bossengine.integration.AetherionItemHook"
            ).getConstructor().newInstance();
            logger.info("Hooked AetherionItems loot bridge.");
        } catch (Exception exception) {
            factory = null;
            logger.warning("Could not hook AetherionItems: " + exception.getMessage());
        }
    }

    public Optional<ItemStack> create(String itemId, int amount) {
        if (factory == null) {
            hook();
        }
        if (!isAvailable()) {
            logger.warning("Aetherion loot skipped '" + itemId + "' – items bridge not hooked.");
            return Optional.empty();
        }
        Optional<ItemStack> created = factory.create(itemId, amount);
        if (created.isEmpty()) {
            logger.warning("Aetherion loot id '" + itemId + "' produced no item.");
        }
        return created;
    }

    public interface ItemFactory {
        Optional<ItemStack> create(String itemId, int amount);
    }
}
