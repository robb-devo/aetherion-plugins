package de.aetherion.quests.story;

import de.aetherion.core.api.StoryAccess;
import de.aetherion.items.lore.StoryLore;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.PlayerQuestStorage;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.npc.CastBook;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Story stage + flags for "Das Schlaflied" — the Quests side of {@link StoryAccess}.
 * <p>
 * Stage (per player, computed from {@code lullaby_*} quest states, never stored):
 * <pre>
 * 0  tutorial not done / nothing heard      4  lullaby_letters done      (family known)
 * 1  lullaby_old_tale done   (legend)        5  lullaby_third_anchor done (worlds fell)
 * 2  lullaby_short_toll done (short toll)    6  lullaby_forgotten done    (the Fading is personal)
 * 3  lullaby_old_seal done   (Eldervale)     7  lullaby_silent_road done  (road open)
 * </pre>
 * Per-player flags and Chronicle pages live in the player's quest file (starter-kit keys
 * {@code story:flag:<key>} / {@code story:page:<id>}), so wipes and transfers treat them like
 * quest progress. Server-wide flags live in {@code story-flags.yml}.
 */
public final class StoryState implements StoryAccess {

    public static final String VEIL_QUAY = "veil_quay_unlocked";

    private static final String[][] STAGES = {
            {LullabyQuests.OLD_TALE, "1"},
            {LullabyQuests.SHORT_TOLL, "2"},
            {LullabyQuests.OLD_SEAL, "3"},
            {LullabyQuests.LETTERS, "4"},
            {LullabyQuests.THIRD_ANCHOR, "5"},
            {LullabyQuests.FORGOTTEN, "6"},
            {LullabyQuests.SILENT_ROAD, "7"}
    };

    private static StoryState instance;

    private final AetherionQuests plugin;
    private final File flagsFile;
    private final YamlConfiguration flags;

    public StoryState(AetherionQuests plugin) {
        this.plugin = plugin;
        this.flagsFile = new File(plugin.getDataFolder(), "story-flags.yml");
        this.flags = flagsFile.exists() ? YamlConfiguration.loadConfiguration(flagsFile) : new YamlConfiguration();
        instance = this;
    }

    public static StoryState get() {
        return instance;
    }

    public static void clear(StoryState state) {
        if (instance == state) {
            instance = null;
        }
    }

    // ------------------------------------------------------------------ stage

    /** Stage for {@code player}; 0 when Quests is not up. */
    public static int stageOf(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager qm = plugin == null ? null : plugin.getQuestManager();
        if (qm == null || player == null || !QuestStoryGate.tutorialDone(player, qm)) {
            return 0;
        }
        int stage = 0;
        for (String[] entry : STAGES) {
            if (completed(qm, player, entry[0])) {
                stage = Integer.parseInt(entry[1]);
            }
        }
        return stage;
    }

    static boolean completed(QuestManager qm, Player player, String questId) {
        Quest quest = qm.getQuest(questId);
        return quest != null && qm.getQuestState(player, quest) == QuestState.COMPLETED;
    }

    @Override
    public int stage(Player player) {
        return stageOf(player);
    }

    // ------------------------------------------------------------------ server flags

    @Override
    public boolean flag(String key) {
        return key != null && flags.getBoolean("flags." + key.toLowerCase(Locale.ROOT), false);
    }

    @Override
    public void setFlag(String key, boolean value) {
        if (key == null || key.isBlank()) {
            return;
        }
        flags.set("flags." + key.toLowerCase(Locale.ROOT), value ? Boolean.TRUE : null);
        try {
            flags.save(flagsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save story-flags.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ player flags + Chronicle

    private static PlayerQuestStorage storage() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getPlayerQuestStorage();
    }

    public static boolean hasPlayerFlag(Player player, String key) {
        PlayerQuestStorage storage = storage();
        return storage != null && player != null && key != null
                && storage.hasStarterKit(player.getUniqueId(), "story:flag:" + key.toLowerCase(Locale.ROOT));
    }

    public static void setPlayerFlag(Player player, String key) {
        PlayerQuestStorage storage = storage();
        if (storage != null && player != null && key != null) {
            storage.markStarterKit(player.getUniqueId(), "story:flag:" + key.toLowerCase(Locale.ROOT));
        }
    }

    public static boolean hasPage(Player player, String pageId) {
        PlayerQuestStorage storage = storage();
        return storage != null && player != null && pageId != null
                && storage.hasStarterKit(player.getUniqueId(), "story:page:" + pageId.toLowerCase(Locale.ROOT));
    }

    /** @return true when the page was new. */
    public static boolean unlockPage(Player player, String pageId) {
        if (hasPage(player, pageId)) {
            return false;
        }
        PlayerQuestStorage storage = storage();
        if (storage == null || player == null || pageId == null) {
            return false;
        }
        storage.markStarterKit(player.getUniqueId(), "story:page:" + pageId.toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public boolean playerFlag(Player player, String key) {
        return hasPlayerFlag(player, key);
    }

    @Override
    public Set<String> chroniclePages(Player player) {
        Set<String> out = new LinkedHashSet<>();
        for (StoryLore lore : StoryLore.values()) {
            if (hasPage(player, lore.findId())) {
                out.add(lore.findId());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ other plugins' NPCs

    @Override
    public String bark(Player player, String speakerId) {
        return CastBook.storyBark(speakerId, player);
    }

    @Override
    public List<String> talk(Player player, String speakerId) {
        return StoryFlow.talkFromElsewhere(player, speakerId);
    }

    // ------------------------------------------------------------------ quest completion hook

    /**
     * Called by QuestManager right after rewards are paid. Story side effects that must happen
     * however the quest was finished (NPC, admin command, …): Chronicle pages, the storybook,
     * the Veil Quay flag.
     */
    public static void onQuestCompleted(Player player, Quest quest) {
        if (player == null || quest == null || !LullabyQuests.isLullaby(quest.getId())) {
            return;
        }
        switch (quest.getId().toLowerCase(Locale.ROOT)) {
            case LullabyQuests.OLD_TALE -> LoreFinds.give(player, StoryLore.BOOK_MAD_KING, false);
            case LullabyQuests.GEAR -> unlockPage(player, StoryLore.MUSIC_BOX_GEAR.findId());
            case LullabyQuests.INSIGNIA -> unlockPage(player, StoryLore.SUN_GUARD_INSIGNIA.findId());
            case LullabyQuests.LETTERS -> {
                unlockPage(player, StoryLore.MAELIS_LETTER_1.findId());
                unlockPage(player, StoryLore.MAELIS_LETTER_2.findId());
                unlockPage(player, StoryLore.MAELIS_LETTER_3.findId());
            }
            case LullabyQuests.SILENT_ROAD -> {
                unlockPage(player, StoryLore.SEALED_WRIT.findId());
                setPlayerFlag(player, VEIL_QUAY);
            }
            default -> {
            }
        }
    }
}
