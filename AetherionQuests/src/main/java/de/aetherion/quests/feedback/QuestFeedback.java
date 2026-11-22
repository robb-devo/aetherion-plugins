package de.aetherion.quests.feedback;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.talk.HudCards;
import de.aetherion.quests.talk.TalkChrome;
import de.aetherion.quests.talk.TalkText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


/**
 * Sounds, titles and chat lines for quest moments. With screen cards on ({@code hud-cards.enabled}) a finished
 * objective and "ready to turn in" become a small toast at the top, a finished quest becomes the big card with its
 * rewards, and the chat keeps a single summary line; counts stay in the action bar.
 */
public class QuestFeedback implements Listener {


    public static final String FIREWORK_KEY_NAME = "reward_firework";

    private final JavaPlugin plugin;
    private final NamespacedKey fireworkKey;


    public QuestFeedback(JavaPlugin plugin) {

        this.plugin = plugin;
        this.fireworkKey = new NamespacedKey(plugin, FIREWORK_KEY_NAME);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }


    public void playAccept(Player player, Quest quest) {

        if (player == null) {
            return;
        }

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                0.55f,
                1.8f
        );

        player.sendActionBar(Component.text(
                quest != null
                        ? de.aetherion.quests.lang.LangPack.format(
                        player,
                        "ui.quest_accepted",
                        "Quest accepted: {0}",
                        de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle())
                )
                        : de.aetherion.quests.lang.LangPack.ui(player, "quest_accepted_plain", "Quest accepted"),
                NamedTextColor.GREEN
        ));

    }


    public void playAbandon(Player player, Quest quest) {

        if (player == null) {
            return;
        }

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS,
                0.8f,
                0.7f
        );

    }


    public void playProgress(
            Player player,
            Quest quest,
            Objective objective,
            int current,
            int maximum,
            boolean objectiveComplete
    ) {

        if (player == null) {
            return;
        }

        String label = objective != null
                ? objective.getDisplayName()
                : (quest != null ? quest.getTitle() : "Quest");

        String progressText = maximum > 0
                ? label + " " + current + "/" + maximum
                : label;

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.55f,
                objectiveComplete ? 1.6f : 1.25f
        );

        player.sendActionBar(Component.text(
                progressText,
                objectiveComplete ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        if (objectiveComplete) {

            if (cards(player)) {
                String next = nextObjective(player, quest, objective);
                HudCards.get().show(player, new HudCards.Card(HudCards.Kind.TOAST, null, progressText, TalkChrome.READY,
                        List.of(HudCards.Row.of("§7" + questTitle(player, quest))),
                        next == null ? null : "§8" + LangPack.ui(player, "card.next", "Next:") + " §7" + next, null));
                return;
            }

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    0.4f,
                    1.6f
            );

            player.sendMessage(
                    "§a✔ §f" + progressText + " §aobjective complete!"
            );

        }

    }


    public void playReady(Player player, Quest quest) {

        if (player == null) {
            return;
        }

        String npcName = turnInName(quest);

        player.playSound(
                player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                0.7f,
                1.2f
        );

        player.sendActionBar(Component.text(
                de.aetherion.quests.lang.LangPack.format(
                        player,
                        "ui.quest_ready",
                        "Quest ready! Return to {0}",
                        npcName
                ),
                NamedTextColor.GREEN
        ));

        if (cards(player)) {
            HudCards.get().show(player, new HudCards.Card(HudCards.Kind.TOAST, null,
                    LangPack.ui(player, "card.objectives_done", "Objectives complete"), TalkChrome.READY,
                    List.of(HudCards.Row.of("§7" + questTitle(player, quest))),
                    "§8" + LangPack.ui(player, "card.next", "Next:") + " §7"
                            + LangPack.format(player, "progress.return_to", "Return to {0}", npcName), null));
            return;
        }

        player.sendMessage("");
        player.sendMessage(de.aetherion.quests.lang.LangPack.ui(player, "objectives_complete", "§a§lOBJECTIVES COMPLETE"));
        player.sendMessage("§7"
                + de.aetherion.quests.lang.LangPack.format(player, "progress.return_to", "Return to {0}", npcName)
                + " §7— §f"
                + de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle()));
        player.sendMessage("");

    }


    public void playComplete(Player player, Quest quest) {

        if (player == null || quest == null) {
            return;
        }

        playComplete(
                player,
                de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle())
        );

    }


    /** Same QUEST COMPLETE fanfare as a quest turn-in, without needing a Quest object. */
    public void playComplete(Player player, String localizedTitle) {

        if (player == null) {
            return;
        }

        String title = localizedTitle == null || localizedTitle.isBlank()
                ? de.aetherion.quests.lang.LangPack.ui(player, "tutorial_name", "Tutorial")
                : localizedTitle;

        player.playSound(
                player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f,
                1.0f
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                0.35f,
                1.35f
        );

        player.showTitle(Title.title(
                Component.text(
                        de.aetherion.quests.lang.LangPack.ui(player, "quest_complete_title", "QUEST COMPLETE"),
                        NamedTextColor.GOLD,
                        TextDecoration.BOLD
                ),
                Component.text(title, NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(600)
                )
        ));

        player.sendMessage("");
        player.sendMessage(de.aetherion.quests.lang.LangPack.ui(player, "quest_complete_banner", "§6§l✦ QUEST COMPLETE ✦"));
        player.sendMessage("");
        player.sendMessage("§e" + title);
        player.sendMessage("");
        // Blank line before rewards / next category.

        spawnRewardFirework(player);

    }


    /** Screen cards are on for this player (else the chat lines above). */
    public boolean cards(Player player) {
        HudCards cards = HudCards.get();
        return cards != null && cards.enabledFor(player);
    }

    /**
     * The big card for a finished quest: title, the rewards as rows (icons, "+250" counts up), one summary line in
     * chat once it has gone. False = it could not show: the caller says it the old way.
     */
    public boolean playCompleteCard(Player player, Quest quest, List<HudCards.Row> rewards) {
        if (player == null || quest == null || !cards(player)) {
            return false;
        }
        String title = questTitle(player, quest);
        List<HudCards.Row> rows = rewards == null ? List.of() : List.copyOf(rewards);
        StringBuilder summary = new StringBuilder("§6✦ §e")
                .append(LangPack.ui(player, "card.quest_done_summary", "Quest complete:"))
                .append(" §f").append(TalkText.strip(title));
        List<String> paid = new ArrayList<>();
        for (HudCards.Row row : rows) {
            if (row != null && row.text() != null && paid.size() < 4) {
                paid.add(TalkText.strip(row.text()));
            }
        }
        if (!paid.isEmpty()) {
            summary.append(" §8· §7").append(String.join(", ", paid));
        }
        boolean shown = HudCards.get().show(player, new HudCards.Card(HudCards.Kind.BIG,
                LangPack.ui(player, "card.quest_complete", "Quest complete"), title, TalkChrome.QUEST, rows, null,
                summary.toString()));
        if (shown) {
            spawnRewardFirework(player);
        }
        return shown;
    }

    private static String questTitle(Player player, Quest quest) {
        if (quest == null) {
            return "Quest";
        }
        return LangPack.questTitle(player, quest.getId(), quest.getTitle());
    }

    /** The first objective still open after {@code done}, as "Label 0/5" (null = none left). */
    private static String nextObjective(Player player, Quest quest, Objective done) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager quests = plugin == null ? null : plugin.getQuestManager();
        if (quests == null || quest == null || quest.getObjectives() == null) {
            return null;
        }
        for (Objective objective : quest.getObjectives()) {
            if (objective == null || objective == done || objective.getAmount() <= 0) {
                continue;
            }
            int have = quests.getProgress(player, quest.getId(), objective.getTarget());
            if (have < objective.getAmount()) {
                String label = objective.getDisplayName() != null ? objective.getDisplayName() : objective.getTarget();
                return TalkText.strip(label) + " " + have + "/" + objective.getAmount();
            }
        }
        return null;
    }

    public void spawnRewardFirework(Player player) {

        if (player == null || player.getWorld() == null) {
            return;
        }

        Location location = player.getLocation().add(0.0, 3.4, 0.0);

        Firework firework = player.getWorld().spawn(location, Firework.class, spawned -> {

            spawned.getPersistentDataContainer().set(
                    fireworkKey,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            spawned.setPersistent(false);
            spawned.setShotAtAngle(false);

            FireworkMeta meta = spawned.getFireworkMeta();
            meta.addEffect(
                    FireworkEffect.builder()
                            .withColor(Color.YELLOW, Color.ORANGE)
                            .withFade(Color.WHITE)
                            .with(FireworkEffect.Type.BURST)
                            .trail(false)
                            .flicker(false)
                            .build()
            );
            meta.setPower(0);
            spawned.setFireworkMeta(meta);

        });

        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                firework::detonate,
                8L
        );

    }


    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Firework firework)) {
            return;
        }

        if (firework.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }

    }


    private static String turnInName(Quest quest) {

        if (quest != null && quest.hasTurnInNpc()) {
            QuestNPC turnIn = QuestNPCRegistry.getNPC(quest.getTurnInNpcId());
            if (turnIn != null && turnIn.getName() != null && !turnIn.getName().isBlank()) {
                return turnIn.getName();
            }
        }

        QuestNPC npc = QuestNPCRegistry.findForQuest(quest);

        if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
            return npc.getName();
        }

        return "the quest NPC";

    }

}
