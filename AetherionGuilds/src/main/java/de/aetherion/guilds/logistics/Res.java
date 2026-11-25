package de.aetherion.guilds.logistics;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resource moving through island logistics: a quarry product in one of three forms. Raw becomes a vanilla
 * stack; Compressed / Compacted become the Aetherion items (same 128:1 chain as the quarry's own mill).
 * Serialised as "COBBLESTONE:RAW".
 */
public record Res(QuarryType type, Form form) {

    public enum Form {
        RAW,
        COMPRESSED,
        COMPACTED;

        public Form next() {
            return switch (this) {
                case RAW -> COMPRESSED;
                case COMPRESSED -> COMPACTED;
                case COMPACTED -> null;
            };
        }
    }

    public static final int UNIT = 128;

    private static final Map<QuarryType, Map<Form, Res>> CACHE = new EnumMap<>(QuarryType.class);
    private static final Map<String, ItemStack> TEMPLATES = new ConcurrentHashMap<>();

    static {
        for (QuarryType type : QuarryType.values()) {
            Map<Form, Res> forms = new EnumMap<>(Form.class);
            for (Form form : Form.values()) {
                forms.put(form, new Res(type, form));
            }
            CACHE.put(type, forms);
        }
    }

    public static Res of(QuarryType type, Form form) {
        return CACHE.get(type == null ? QuarryType.COBBLESTONE : type).get(form == null ? Form.RAW : form);
    }

    public static Res parse(String raw) {
        if (raw == null) {
            return null;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            QuarryType type = QuarryType.valueOf(raw.substring(0, colon).trim().toUpperCase(Locale.ROOT));
            Form form = Form.valueOf(raw.substring(colon + 1).trim().toUpperCase(Locale.ROOT));
            return of(type, form);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String key() {
        return type.name() + ":" + form.name();
    }

    /** Units of raw this stands for. */
    public long rawEquivalent(long amount) {
        return switch (form) {
            case RAW -> amount;
            case COMPRESSED -> amount * UNIT;
            case COMPACTED -> amount * UNIT * UNIT;
        };
    }

    public String display() {
        return switch (form) {
            case RAW -> type.productName();
            case COMPRESSED -> "Compressed " + type.productName();
            case COMPACTED -> "Compacted " + type.productName();
        };
    }

    public String color() {
        return switch (form) {
            case RAW -> "§e";
            case COMPRESSED -> "§a";
            case COMPACTED -> "§b";
        };
    }

    /** Requirement key used by guild projects ("mat:COBBLESTONE" / "item:compressed_cobblestone"). */
    public String requirementKey() {
        return switch (form) {
            case RAW -> "mat:" + type.product().name();
            case COMPRESSED -> "item:" + type.compressedId();
            case COMPACTED -> "item:" + type.compactedId();
        };
    }

    /** Can this form be turned into the next one (needs AetherionItems and an item for it)? */
    public boolean canUpgrade() {
        Form next = form.next();
        return next != null && of(type, next).stack(1) != null;
    }

    /** A stack of this resource, or null when it can't be materialised (no AetherionItems). */
    public ItemStack stack(int amount) {
        if (amount <= 0) {
            return null;
        }
        if (form == Form.RAW) {
            return new ItemStack(type.product(), Math.min(amount, type.product().getMaxStackSize()));
        }
        if (!AetherionItemsAccess.available()) {
            return null;
        }
        ItemStack template = TEMPLATES.computeIfAbsent(key(), k -> {
            ItemFactoryAccess items = AetherServices.items();
            if (items == null) {
                return null;
            }
            ItemStack made = form == Form.COMPACTED ? items.compacted(type.name()) : items.compressed(type.name());
            return made == null ? new ItemStack(Material.AIR) : made;
        });
        if (template == null || template.getType().isAir()) {
            return null;
        }
        ItemStack out = template.clone();
        out.setAmount(Math.max(1, Math.min(amount, out.getMaxStackSize())));
        return out;
    }

    /**
     * The good this item is (a plain vanilla stack of a quarry's product, or a Compressed / Compacted Aetherion
     * item), or null for anything else (renamed stacks and other items never count).
     */
    public static Res fromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String id = AetherionItemsAccess.itemId(item);
        if (id != null) {
            for (QuarryType type : QuarryType.values()) {
                if (id.equals(type.compressedId())) {
                    return of(type, Form.COMPRESSED);
                }
                if (id.equals(type.compactedId())) {
                    return of(type, Form.COMPACTED);
                }
            }
            return null;
        }
        for (QuarryType type : QuarryType.values()) {
            if (item.getType() == type.product()) {
                return new ItemStack(type.product()).isSimilar(item) ? of(type, Form.RAW) : null;
            }
        }
        return null;
    }

    /** How many of this good the player carries. */
    public long carried(org.bukkit.entity.Player player) {
        if (player == null) {
            return 0L;
        }
        long have = 0L;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (this.equals(fromItem(item))) {
                have += item.getAmount();
            }
        }
        return have;
    }

    /** Takes up to {@code amount} of this good out of the player's inventory; returns how many it took. */
    public long takeCarried(org.bukkit.entity.Player player, long amount) {
        if (player == null || amount <= 0L) {
            return 0L;
        }
        long left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0L; slot++) {
            ItemStack item = contents[slot];
            if (!this.equals(fromItem(item))) {
                continue;
            }
            int take = (int) Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            player.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
            left -= take;
        }
        return amount - left;
    }

    /** Icon for menus and belt cargo (always something visible). */
    public ItemStack icon() {
        ItemStack stack = stack(1);
        if (stack != null) {
            return stack;
        }
        return new ItemStack(form == Form.RAW ? type.product() : type.icon());
    }

    public static void clearCache() {
        TEMPLATES.clear();
    }
}
