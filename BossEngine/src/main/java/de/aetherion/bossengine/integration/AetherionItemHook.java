package de.aetherion.bossengine.integration;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loaded only via {@link Class#forName} when AetherionItems is installed.
 */
public class AetherionItemHook implements AetherionItemBridge.ItemFactory {

    private final CustomItem customItem;
    private final Logger logger;

    public AetherionItemHook() {
        this.customItem = new CustomItem(AetherionItems.getInstance().getItemManager());
        this.logger = Bukkit.getLogger();
    }

    @Override
    public Optional<ItemStack> create(String itemId, int amount) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }

        try {
            ItemStack item = createById(itemId);
            if (item == null) {
                logger.warning("[BossEngine] Aetherion loot id '" + itemId + "' produced no item.");
                return Optional.empty();
            }
            item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
            return Optional.of(item);
        } catch (ReflectiveOperationException exception) {
            logger.warning("[BossEngine] Aetherion loot '" + itemId + "' failed: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private ItemStack createById(String itemId) throws ReflectiveOperationException {
        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case "beginner_pickaxe" -> customItem.create();
            case "random_aetherion_armor", "aetherion_armor" -> customItem.createRandomAetherionArmor();
            case "aetherion_helmet" -> customItem.createAetherionHelmet();
            case "aetherion_chestplate" -> customItem.createAetherionChestplate();
            case "aetherion_leggings" -> customItem.createAetherionLeggings();
            case "aetherion_boots" -> customItem.createAetherionBoots();
            case "aetherion_void_stick", "void_stick" -> customItem.createAetherionVoidStick();
            case "random_dungeon_relic_t2", "dungeon_relic_t2" -> customItem.createRandomDungeonRelicT2();
            case "random_dungeon_relic_t3", "dungeon_relic_t3" -> customItem.createRandomDungeonRelicT3();
            case "weapon_schematic" -> customItem.createWeaponSchematic();
            default -> {
                ItemStack dungeon = customItem.createDungeonFromId(itemId);
                yield dungeon != null ? dungeon : invokeCreate(itemId);
            }
        };
    }

    private ItemStack invokeCreate(String itemId) throws ReflectiveOperationException {
        Method method = CustomItem.class.getMethod(toCreateMethod(itemId));
        Object result = method.invoke(customItem);
        return result instanceof ItemStack stack ? stack : null;
    }

    private String toCreateMethod(String itemId) {
        StringBuilder builder = new StringBuilder("create");
        for (String part : itemId.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.chars().allMatch(Character::isDigit)) {
                builder.append(part);
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
