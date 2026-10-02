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
