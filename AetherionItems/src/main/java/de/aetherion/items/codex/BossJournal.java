package de.aetherion.items.codex;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Reads BossEngine templates through reflection so Items stays a soft depend.
 */
public final class BossJournal {

    public record Drop(String role, String name, double chance, int minAmount, int maxAmount) {
        public String line() {
            String amount = minAmount == maxAmount ? "x" + minAmount : "x" + minAmount + "-" + maxAmount;
            return "§7" + name + " §8" + amount + " §e" + percent(chance) + "%";
        }
    }

    public record Entry(String id, String displayName, Material icon, List<Drop> drops, int experience) {
    }

    private BossJournal() {
    }

    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BossEngine");
        if (plugin == null || !plugin.isEnabled()) {
            return entries;
        }
        try {
            Object api = plugin.getClass().getMethod("getAPI").invoke(null);
            if (api == null) {
                return entries;
            }
            Object templates = api.getClass().getMethod("getTemplates").invoke(api);
            if (!(templates instanceof Collection<?> collection)) {
                return entries;
            }
            for (Object template : collection) {
                String id = String.valueOf(invoke(template, "getId"));
                String name = String.valueOf(invoke(template, "getDisplayName"));
                EntityType type = (EntityType) invoke(template, "getEntityType");
                Object loot = invoke(template, "getLootTable");
                int experience = loot == null ? 0 : ((Number) invoke(loot, "getExperience")).intValue();
                List<Drop> lootDrops = drops(loot);
                if (!id.toLowerCase(Locale.ROOT).startsWith("dungeon")) {
                    lootDrops.add(new Drop("World", "Quarry Core Shard", 0.20, 1, 1));
                }
                entries.add(new Entry(
                        id,
                        strip(name),
                        icon(type),
                        lootDrops,
                        experience
                ));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        entries.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return entries;
    }

    private static List<Drop> drops(Object loot) throws ReflectiveOperationException {
        List<Drop> drops = new ArrayList<>();
        if (loot == null) {
            return drops;
        }
        addDrops(drops, "Killer", invoke(loot, "getKillerBonus"));
        addDrops(drops, "Shared", invoke(loot, "getShared"));
        addDrops(drops, "Damage", invoke(loot, "getPerDamager"));
        addDrops(drops, "Top 3", invoke(loot, "getTopDamagerLoot"));
        Object rankLoot = invoke(loot, "getRankLoot", int.class, 1);
        if (rankLoot instanceof Collection<?>) {
            addDrops(drops, "1st", rankLoot);
        }
        return drops;
    }

    @SuppressWarnings("unchecked")
    private static void addDrops(List<Drop> target, String role, Object list) throws ReflectiveOperationException {
        if (!(list instanceof Collection<?> collection)) {
            return;
        }
        for (Object entry : collection) {
            String itemId = String.valueOf(invoke(entry, "getItemId"));
            Object material = invoke(entry, "getMaterial");
            String name = pretty(itemId);
            if (name.isBlank() && material instanceof Material mat && mat != Material.AIR) {
                name = pretty(mat.name());
            }
            if (name.isBlank()) {
                name = "Unknown";
            }
            double chance = ((Number) invoke(entry, "getChance")).doubleValue();
            int min = ((Number) invoke(entry, "getMinAmount")).intValue();
            int max = ((Number) invoke(entry, "getMaxAmount")).intValue();
            target.add(new Drop(role, name, chance, min, max));
        }
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Object invoke(Object target, String method, Class<?> type, Object argument)
            throws ReflectiveOperationException {
        Method found = target.getClass().getMethod(method, type);
        return found.invoke(target, argument);
    }

    private static Material icon(EntityType type) {
        if (type == null) {
            return Material.WITHER_SKELETON_SKULL;
        }
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ignored) {
            return Material.WITHER_SKELETON_SKULL;
        }
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return "";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String strip(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        return name.replace('&', '§');
    }

    private static String percent(double chance) {
        double value = chance * 100.0;
        if (value >= 10 || value == Math.rint(value)) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
