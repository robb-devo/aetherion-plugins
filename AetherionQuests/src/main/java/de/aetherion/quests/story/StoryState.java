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
 * 8  lullaby_ferry done      (crossed)       9  lullaby_captain done      (Caedmon said no)
 * 10 lullaby_heart done      (Maelis)        11 lullaby_doll done         (the puppet was there)
 * 12 lullaby_seed done       (nothing brings them back)
 * </pre>
 * Per-player flags and Chronicle pages live in the player's quest file (starter-kit keys
 * {@code story:flag:<key>} / {@code story:page:<id>}), so wipes and transfers treat them like
 * quest progress. Server-wide flags live in {@code story-flags.yml}.
 */
public final class StoryState implements StoryAccess {

    public static final String VEIL_QUAY = "veil_quay_unlocked";
    /** Heard Orrin out in the Observatory (Core's star ring opens for this). */
    public static final String ORRIN_WARNED = "orrin_warned";
    public static final String CROSSED = "exile_crossed";
    /** Aetherion beaten in the Ashen Void behind the Great Doors: the finale no longer opens without a vial. */
    public static final String FINALE_WON = "exile_finale_won";
    /** The Pathwarden fell to this player once, any time, any way (A-52: with Level 75 that is the whole ferry ticket). */
    public static final String PATHWARDEN_DOWN = "pathwarden_down";
    /** Aetherion's arena behind the Great Doors (Core ExileDoors); the dungeon copy of the boss elsewhere never counts. */
    private static final String FINALE_WORLD = "ashen_void";

    private static final String[][] STAGES = {
            {LullabyQuests.OLD_TALE, "1"},
            {LullabyQuests.SHORT_TOLL, "2"},
            {LullabyQuests.OLD_SEAL, "3"},
            {LullabyQuests.LETTERS, "4"},
            {LullabyQuests.THIRD_ANCHOR, "5"},
            {LullabyQuests.FORGOTTEN, "6"},
            {LullabyQuests.SILENT_ROAD, "7"},
            {LullabyQuests.FERRY, "8"},
            {LullabyQuests.CAPTAIN, "9"},
            {LullabyQuests.HEART, "10"},
            {LullabyQuests.DOLL, "11"},
            {LullabyQuests.SEED, "12"}
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
            de.aetherion.core.persist.AtomicYaml.save(flags, flagsFile);
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
        if ("orientation_stamped".equalsIgnoreCase(key)) {
            // Miss Ledger's stamp: the Hub opens every island pad on it.
            de.aetherion.quests.AetherionQuests plugin = de.aetherion.quests.AetherionQuests.getInstance();
            return plugin != null && de.aetherion.quests.util.QuestStoryGate.graduated(player, plugin.getQuestManager());
        }
        if ("mine_road_open".equalsIgnoreCase(key)) {
            // The Mining pad: Foreman, Surveyor and a first blueprint tool come first (SkillRoads.mineOpen).
            return de.aetherion.quests.ui.SkillRoads.mineOpen(player);
        }
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

    /**
     * Exile boss sites wake without a vial while the story step that sends the player there is running (also after
     * a lost fight), and the Great Doors open once after "What the Seed Ate" until Aetherion falls. Vials stay the
     * way back in afterwards.
     */
    @Override
    public boolean wakesWithoutVial(Player player, String boss) {
        AetherionQuests quests = AetherionQuests.getInstance();
        QuestManager qm = quests == null ? null : quests.getQuestManager();
        if (player == null || boss == null || qm == null) {
            return false;
        }
        String id = boss.toLowerCase(Locale.ROOT);
        if ("aetherion".equals(id)) {
            return stateOf(qm, player, LullabyQuests.SEED) == QuestState.COMPLETED && !hasPlayerFlag(player, FINALE_WON);
        }
        String step = switch (id) {
            case "hollow_sun" -> LullabyQuests.CAPTAIN;
            case "helios" -> LullabyQuests.HEART;
            case "hanging_saint" -> LullabyQuests.DOLL;
            case "world_eater" -> LullabyQuests.SEED;
            default -> null;
        };
        return step != null && stateOf(qm, player, step) == QuestState.ACTIVE;
    }

    private static QuestState stateOf(QuestManager qm, Player player, String questId) {
        Quest quest = qm.getQuest(questId);
        return quest == null ? null : qm.getQuestState(player, quest);
    }

    /**
     * BossEngine kill credit (BossKillCredit): the Pathwarden once is half the ferry ticket; Aetherion falling in the
     * Ashen Void closes the free finale.
     */
    public static void onBossKill(Player player, String bossId) {
        if (player == null || bossId == null) {
            return;
        }
        if ("pathwarden".equalsIgnoreCase(bossId)) {
            if (!hasPlayerFlag(player, PATHWARDEN_DOWN)) {
                setPlayerFlag(player, PATHWARDEN_DOWN);
                player.sendMessage(de.aetherion.quests.lang.LangPack.ui(player, "tip.ferry.warden_down",
                        "§8Ferry §8» §7The §cPathwarden§7 is down. The Ferryman at the Veil Quay takes you across at Aetherion Level §e75§7."));
            }
            return;
        }
        if ("dungeon_aetherion".equalsIgnoreCase(bossId)
                && player.getWorld().getName().equalsIgnoreCase(FINALE_WORLD)) {
            setPlayerFlag(player, FINALE_WON);
        }
    }

    /**
     * Beaten the Pathwarden once: the flag, the story road (Silent Road done, crossed), or a kill the Dungeon Journal
     * logged before the flag existed.
     */
    public static boolean pathwardenDown(Player player) {
        if (player == null) {
            return false;
        }
        if (hasPlayerFlag(player, PATHWARDEN_DOWN) || hasPlayerFlag(player, VEIL_QUAY) || hasPlayerFlag(player, CROSSED)) {
            return true;
        }
        if (journalKnowsPathwarden(player)) {
            setPlayerFlag(player, PATHWARDEN_DOWN);
            return true;
        }
        return false;
    }

    private static boolean journalKnowsPathwarden(Player player) {
        try {
            de.aetherion.items.codex.CodexService codex = de.aetherion.items.codex.CodexRewards.service();
            return codex != null && codex.bossKills(player, "pathwarden") > 0L;
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /** The Ferryman's answer before he casts off (null = aboard). */
    @Override
    public String ferryBlock(Player player) {
        return crossingBlock(player);
    }

    /** Same as {@link #ferryBlock(Player)}, for the Ferryman's own desk. */
    public static String crossingBlock(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager qm = plugin == null ? null : plugin.getQuestManager();
        if (player == null || qm == null) {
            return null;
        }
        if (hasPlayerFlag(player, CROSSED)) {
            return null;
        }
        if (!QuestStoryGate.tutorialDone(player, qm)) {
            return de.aetherion.quests.lang.LangPack.ui(player, "tip.ferry.harbour",
                    "No passengers who haven't finished their harbour business. Then the fog.");
        }
        // A-52: Level 75 and the Pathwarden beaten once. The story still runs, it just isn't the lock.
        boolean warden = pathwardenDown(player);
        boolean level = StoryFlow.meetsLevel(player, 75);
        if (!warden && !level) {
            return de.aetherion.quests.lang.LangPack.ui(player, "tip.ferry.both",
                    "Two things first: Aetherion Level §e75§7, and the §cPathwarden§7 beaten once.");
        }
        if (!warden) {
            return de.aetherion.quests.lang.LangPack.ui(player, "tip.ferry.warden",
                    "Beat the §cPathwarden§7 once first. A Crypt vial, then the Proctor at the Colosseum.");
        }
        if (!level) {
            return de.aetherion.quests.lang.LangPack.ui(player, "tip.ferry.level",
                    "The fog isn't ready for you. Come back at Aetherion Level §e75§7.");
        }
        return null;
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
            case LullabyQuests.FERRY -> setPlayerFlag(player, CROSSED);
            case LullabyQuests.HEART -> setPlayerFlag(player, ORRIN_WARNED);
            default -> {
            }
        }
    }
}
