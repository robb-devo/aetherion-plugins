package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft exploration hint (not a quest) — bossbar + compass arrow only.
 */
public final class QuestHint {

    private static final double ARRIVED = 5.0;
    private static final String[] ARROWS = {
            "↑", "↗", "→", "↘", "↓", "↙", "←", "↖"
    };

    private static final Map<UUID, Hint> HINTS = new ConcurrentHashMap<>();
    /** Soft trail remembered across side-quests; restored when idle. */
    private static final Map<UUID, Hint> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> BARS = new ConcurrentHashMap<>();

    private QuestHint() {
    }

    public static void show(Player player, String npcId, String label) {
        show(player, npcId, label, null);
    }

    /** Optional note appears only on the bossbar (e.g. {@code also /mines}). */
    public static void show(Player player, String npcId, String label, String note) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return;
        }
        String name = label == null || label.isBlank() ? pretty(npcId) : label;
        Hint hint = new Hint(npcId.toLowerCase(Locale.ROOT), name, note);
        HINTS.put(player.getUniqueId(), hint);
        PENDING.put(player.getUniqueId(), hint);
        tickPlayer(player);
    }

    /** Remember a soft next-step without forcing the bossbar if a quest is already active. */
    public static void remember(Player player, String npcId, String label) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return;
        }
        String name = label == null || label.isBlank() ? pretty(npcId) : label;
        PENDING.put(player.getUniqueId(), new Hint(npcId.toLowerCase(Locale.ROOT), name, null));
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        HINTS.remove(player.getUniqueId());
        BossBar bar = BARS.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    /** Drop both visible hint and remembered trail (quest accepted / done). */
    public static void clearPending(Player player) {
        clear(player);
        if (player != null) {
            PENDING.remove(player.getUniqueId());
        }
    }

    public static boolean has(Player player) {
        return player != null && HINTS.containsKey(player.getUniqueId());
    }

    /** Soft-hint / trail target (NPC), or null. */
    public static Location targetLocation(Player player) {
        if (player == null) {
            return null;
        }
        Hint hint = HINTS.get(player.getUniqueId());
        if (hint == null) {
            hint = PENDING.get(player.getUniqueId());
        }
        if (hint == null) {
            return null;
        }
        return npcLocation(hint.npcId());
    }

    public static void tickAll(QuestManager questManager) {
        if (questManager == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isFarmIsland(player.getWorld())) {
                BossBar bar = BARS.get(player.getUniqueId());
                if (bar != null) {
                    bar.setVisible(false);
                }
                continue;
            }
            boolean busy = questManager.getTrackedQuest(player) != null
                    || questManager.findActiveOrReadyQuest(player) != null;
            if (busy) {
                if (HINTS.containsKey(player.getUniqueId())) {
                    clear(player);
                }
                continue;
            }
            Hint pending = PENDING.get(player.getUniqueId());
            if (pending != null && shouldKeepPending(questManager, player, pending)) {
                if (!HINTS.containsKey(player.getUniqueId())) {
                    HINTS.put(player.getUniqueId(), pending);
                }
                tickPlayer(player);
            } else if (pending != null) {
                PENDING.remove(player.getUniqueId());
                clear(player);
            } else if (HINTS.containsKey(player.getUniqueId())) {
                tickPlayer(player);
            }
        }
    }

    private static boolean shouldKeepPending(QuestManager questManager, Player player, Hint pending) {
        if (pending == null || pending.npcId() == null) {
            return false;
        }
        String npc = pending.npcId().toLowerCase(Locale.ROOT);
        // Orientation trail drops after stamp — except Miss Ledger (graduation visit).
        if (de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager)
                && isTutorialTrailNpc(npc)
                && !"ledger".equals(npc)) {
            return false;
        }
        return switch (npc) {
            case "egon", "lumberjack" -> stillNeeds(questManager, player, "gather_wood");
            case "quartermaster" -> stillNeeds(questManager, player, "forge_coal");
            case "foreman", "craftsman" -> stillNeeds(questManager, player, "first_shift");
            case "booster_tutor" -> stillNeeds(questManager, player, "lesson_boost");
            case "ledger" -> stillNeeds(questManager, player, "lesson_manager")
                    || de.aetherion.quests.util.QuestStoryGate.tutorialDone(player, questManager);
            case "farmer" -> stillNeeds(questManager, player, "farm_hand");
            case "lark" -> stillNeeds(questManager, player, "pocket_zoo");
            default -> true;
        };
    }

    /** Quest not yet finished — drop pending once ACTIVE/READY/COMPLETED (quest bar owns the pin). */
    private static boolean stillNeeds(QuestManager questManager, Player player, String questId) {
        var quest = questManager.getQuest(questId);
        if (quest == null) {
            return true;
        }
        var state = questManager.getQuestState(player, quest);
        return state != de.aetherion.quests.model.QuestState.ACTIVE
                && state != de.aetherion.quests.model.QuestState.READY
                && state != de.aetherion.quests.model.QuestState.COMPLETED;
    }

    private static boolean isTutorialTrailNpc(String npcId) {
        if (npcId == null) {
            return false;
        }
        return switch (npcId.toLowerCase(Locale.ROOT)) {
            case "egon", "lumberjack", "quartermaster", "craftsman", "foreman",
                 "booster_tutor", "ledger", "farmer", "lark" -> true;
            default -> false;
        };
    }

    private static void tickPlayer(Player player) {
        Hint hint = HINTS.get(player.getUniqueId());
        if (hint == null) {
            return;
        }
        if (isFarmIsland(player.getWorld())) {
            BossBar bar = BARS.get(player.getUniqueId());
            if (bar != null) {
                bar.setVisible(false);
            }
            return;
        }
        Location target = npcLocation(hint.npcId());
        BossBar bar = BARS.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
            created.setProgress(1.0);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
        bar.setColor(BarColor.YELLOW);

        String note = hint.note() == null || hint.note().isBlank() ? "" : " §8· §7" + hint.note();

        if (target == null || target.getWorld() == null
                || player.getWorld() == null
                || !player.getWorld().equals(target.getWorld())) {
            bar.setTitle("§7Hint §8• §e" + hint.label() + note + " §8• §7◆");
            return;
        }

        player.setCompassTarget(target);

        double dx = target.getX() - player.getLocation().getX();
        double dz = target.getZ() - player.getLocation().getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= ARRIVED) {
            bar.setTitle("§7" + de.aetherion.quests.lang.LangPack.ui(player, "hint", "Hint")
                + " §8• §a" + hint.label() + note + " §8• §a●");
            bar.setColor(BarColor.GREEN);
            return;
        }

        String arrow = arrowToward(player.getLocation().getYaw(), dx, dz);
        String distText = dist >= 1000
                ? String.format(Locale.US, "%.1fkm", dist / 1000.0)
                : Math.round(dist) + "m";
        bar.setTitle("§7" + de.aetherion.quests.lang.LangPack.ui(player, "hint", "Hint")
                + " §8• §e" + hint.label() + note + " §8• §e" + arrow + " §f" + distText);
    }

    private static String arrowToward(float yaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = targetYaw - yaw;
        while (diff < -180.0) {
            diff += 360.0;
        }
        while (diff > 180.0) {
            diff -= 360.0;
        }
        int index = (int) Math.round(diff / 45.0);
        if (index < 0) {
            index += 8;
        }
        if (index >= 8) {
            index = 0;
        }
        return ARROWS[index];
    }

    private static Location npcLocation(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc == null) {
            return null;
        }
        if (npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getNpcDataStorage() == null) {
            return null;
        }
        NPCDataStorage storage = plugin.getNpcDataStorage();
        return storage.getSavedLocation(npcId);
    }

    private static String pretty(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
            return npc.getName();
        }
        String[] parts = npcId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static boolean isFarmIsland(org.bukkit.World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_farm_island") || name.startsWith("aether_farm_");
    }

    private record Hint(String npcId, String label, String note) {
    }
}
