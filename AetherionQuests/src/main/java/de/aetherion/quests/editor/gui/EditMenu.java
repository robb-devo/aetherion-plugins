package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.EditorSessions;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.NpcMode;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EditMenu implements Listener {

    private static final int HEAD = 4;
    private static final int SAVED = 8;
    private static final int NAME = 10;
    private static final int SUBTITLE = 11;
    private static final int APPEAR = 12;
    private static final int MODE = 19;
    private static final int DIALOGUE = 20;
    private static final int QUEST = 21;
    private static final int REWARDS = 22;
    private static final int REQUIRE = 23;
    private static final int OBJECTIVE = 24;
    private static final int MOVE = 37;
    private static final int LOOK = 38;
    private static final int DUPLICATE = 45;
    private static final int DELETE = 47;
    private static final int BACK = 49;

    private final NpcEditor editor;

    public EditMenu(NpcEditor editor) {
        this.editor = editor;
        editor.plugin().getServer().getPluginManager().registerEvents(this, editor.plugin());
    }

    public static void open(Player player, CustomNpc npc) {
        NpcEditor editor = playerEditor();
        EditorSessions.Session session = editor == null ? null : editor.sessions().of(player);
        boolean dirty = session != null && (session.dirty() || session.prompting());
        Inventory inventory = Bukkit.createInventory(
                new Holder(npc.getId()),
                54,
                EditorItems.title(player, "npc_edit", "§8Edit NPC")
        );
        EditorItems.chrome(inventory);
        inventory.setItem(HEAD, EditorItems.head(
                npc.getSkinUsername(),
                "§b" + npc.getName(),
                "§7id §f" + npc.getId(),
                "§7" + npc.getSubtitle(),
                modePreview(npc),
                npc.hasLinkedQuest() ? "§aQuest §f" + npc.getLinkedQuestId() : "§8No quest linked"
        ));
        inventory.setItem(SAVED, EditorItems.saved(dirty));
        inventory.setItem(9, EditorItems.section("Identity", "§7Name, line, look."));
        inventory.setItem(NAME, EditorItems.button(
                Material.NAME_TAG,
                "§eRename",
                "§7Currently §f" + npc.getName(),
                "§8Click · type in chat"
        ));
        inventory.setItem(SUBTITLE, EditorItems.button(
                Material.PAPER,
                "§eSubtitle",
                "§7Currently §f" + npc.getSubtitle(),
                "§8Small line under the name"
        ));
        inventory.setItem(APPEAR, EditorItems.button(
                Material.LEATHER_CHESTPLATE,
                "§6Appearance",
                "§7Preset §f" + npc.getPreset().label(),
                "§7Skin §f" + npc.getSkinUsername(),
                "§8Presets + slim arms"
        ));
        inventory.setItem(18, EditorItems.section("Mode", "§7Dialog talks. Quest can give a job."));
        inventory.setItem(MODE, modeButton(npc));
        inventory.setItem(DIALOGUE, EditorItems.button(
                Material.WRITABLE_BOOK,
                "§dDialogue",
                "§7" + npc.pages().size() + " page(s)",
                "§7Start §f" + npc.getStartPage(),
                "§8Lines + player choices"
        ));
        boolean questMode = npc.isQuestNpc();
        inventory.setItem(QUEST, EditorItems.button(
                questMode ? Material.MAP : Material.GRAY_DYE,
                questMode ? "§aQuest link" : "§8Quest link",
                questMode
                        ? (npc.hasLinkedQuest() ? "§f" + npc.getLinkedQuestId() : "§7None — create or pick")
                        : "§7Switch to Quest NPC first.",
                "§8Create new path or pick existing"
        ));
        inventory.setItem(REWARDS, questTool(
                questMode,
                Material.GOLD_INGOT,
                "§6Rewards",
                rewardLore(editor, npc)
        ));
        inventory.setItem(REQUIRE, questTool(
                questMode,
                Material.IRON_BARS,
                "§eRequirements",
                requireLore(editor, npc)
        ));
        inventory.setItem(OBJECTIVE, questTool(
                questMode,
                Material.CHEST,
                "§bObjective",
                objectiveLore(editor, npc)
        ));
        inventory.setItem(36, EditorItems.section("Place", "§7Move without breaking the NPC."));
        inventory.setItem(MOVE, EditorItems.button(Material.ENDER_PEARL, "§bMove here", "§7Teleport the NPC to your feet."));
        inventory.setItem(LOOK, EditorItems.button(Material.ENDER_EYE, "§bLook at me", "§7Face your current position."));
        inventory.setItem(DUPLICATE, EditorItems.button(Material.PAPER, "§eDuplicate", "§7Clone beside you."));
        inventory.setItem(DELETE, EditorItems.button(Material.BARRIER, "§cDelete", "§7Asks first."));
        inventory.setItem(BACK, EditorItems.button(Material.ARROW, "§7Back"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f);
    }

    private static org.bukkit.inventory.ItemStack modeButton(CustomNpc npc) {
        boolean quest = npc.isQuestNpc();
        return EditorItems.button(
                quest ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE,
                quest ? "§aMode · Quest NPC" : "§bMode · Dialog-only",
                quest ? "§7Linked quest, rewards, gather." : "§7Just talks. No job.",
                "§eClick to switch.",
                "§8Saved on the NPC."
        );
    }

    private static org.bukkit.inventory.ItemStack questTool(boolean questMode, Material material, String name, String... lore) {
        if (!questMode) {
            return EditorItems.button(Material.GRAY_DYE, "§8" + strip(name), "§7Quest NPC only.");
        }
        return EditorItems.button(material, name, lore);
    }

    private static String strip(String name) {
        return name.replace("§6", "").replace("§e", "").replace("§b", "").replace("§a", "");
    }

    private static String modePreview(CustomNpc npc) {
        return npc.isQuestNpc()
                ? "§aQuest NPC"
                : "§bDialog-only";
    }

    private static String[] rewardLore(NpcEditor editor, CustomNpc npc) {
        Quest quest = linkedEditor(editor, npc);
        if (quest == null) {
            return new String[]{
                    npc.hasLinkedQuest() ? "§7Story quest — read-only" : "§7Create a quest path first",
                    "§8Coins / XP / items"
            };
        }
        int n = quest.getRewards().size();
        return new String[]{
                "§7" + n + " reward(s)",
                n > 0 ? "§f" + quest.getRewards().get(0).getAmount() + " " + quest.getRewards().get(0).getName() : "§8empty",
                "§8Click to browse / edit"
        };
    }

    private static String[] requireLore(NpcEditor editor, CustomNpc npc) {
        Quest quest = linkedEditor(editor, npc);
        if (quest == null) {
            return new String[]{"§7Optional. Editor quests only.", "§8Prior quest / level / item"};
        }
        if (!quest.hasRequirement()) {
            return new String[]{"§7None set", "§8Prior quest, Aetherion level, item"};
        }
        return new String[]{
                quest.hasPriorQuestRequirement() ? "§7Prior §f" + quest.getRequiredPriorQuestId() : "§8No prior quest",
                quest.getRequiredAccountLevel() > 0 ? "§7Level §f" + quest.getRequiredAccountLevel() : "§8No level gate",
                quest.hasItemRequirement()
                        ? "§7Item §f" + quest.getRequiredItemAmount() + "× " + quest.getRequiredItemId()
                        : "§8No item gate"
        };
    }

    private static String[] objectiveLore(NpcEditor editor, CustomNpc npc) {
        Quest quest = linkedEditor(editor, npc);
        if (quest == null) {
            return new String[]{"§7Talk or gather.", "§8Click an item — no typing IDs"};
        }
        if (quest.getObjectives().isEmpty()) {
            return new String[]{"§7None", "§8Add talk or gather"};
        }
        Objective objective = quest.getObjectives().get(0);
        return new String[]{
                "§7" + objective.getType().name() + " §f" + objective.getAmount() + "×",
                "§f" + objective.getTarget(),
                "§8Picker for gather items"
        };
    }

    private static Quest linkedEditor(NpcEditor editor, CustomNpc npc) {
        if (editor == null || npc == null || !npc.hasLinkedQuest()) {
            return null;
        }
        return editor.editorQuests().get(npc.getLinkedQuestId());
    }

    private static NpcEditor playerEditor() {
        var plugin = de.aetherion.quests.AetherionQuests.getInstance();
        return plugin == null ? null : plugin.getNpcEditor();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !NpcEditor.allowed(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        CustomNpc npc = editor.storage().get(holder.npcId());
        if (npc == null) {
            player.closeInventory();
            player.sendMessage("§cThat NPC is gone.");
            return;
        }
        editor.sessions().of(player).setNpcId(npc.getId());
        switch (event.getRawSlot()) {
            case NAME -> editor.prompt(player, EditorSessions.Prompt.RENAME, "Type the new display name");
            case SUBTITLE -> editor.prompt(player, EditorSessions.Prompt.SUBTITLE, "Type a short subtitle");
            case APPEAR -> AppearanceMenu.open(player, npc);
            case MODE -> toggleMode(player, npc);
            case DIALOGUE -> DialogueMenu.openTree(player, npc);
            case QUEST -> {
                if (!npc.isQuestNpc()) {
                    player.sendMessage("§eSwitch to Quest NPC first.");
                    return;
                }
                QuestLinkMenu.open(player, npc, 0);
            }
            case REWARDS -> openQuestTool(player, npc, RewardsMenu::open);
            case REQUIRE -> openQuestTool(player, npc, RequirementsMenu::open);
            case OBJECTIVE -> openQuestTool(player, npc, ObjectiveMenu::open);
            case MOVE -> {
                editor.moveHere(player, npc);
                editor.sessions().of(player).setDirty(false);
                open(player, npc);
            }
            case LOOK -> {
                editor.lookAt(player, npc);
                editor.sessions().of(player).setDirty(false);
                open(player, npc);
            }
            case DUPLICATE -> editor.duplicate(player, npc);
            case DELETE -> ConfirmMenu.open(player, npc);
            case BACK -> MainMenu.open(player);
            default -> {
            }
        }
    }

    private void toggleMode(Player player, CustomNpc npc) {
        if (npc.isQuestNpc()) {
            npc.setMode(NpcMode.DIALOG);
            editor.persistQuiet(npc);
            editor.sessions().of(player).setDirty(false);
            player.sendMessage("§bDialog-only. §7Talks, no quest offer.");
            open(player, npc);
            return;
        }
        npc.setMode(NpcMode.QUEST);
        editor.persistQuiet(npc);
        editor.sessions().of(player).setDirty(false);
        player.sendMessage("§aQuest NPC. §7Link or create a quest path.");
        if (!npc.hasLinkedQuest()) {
            QuestLinkMenu.open(player, npc, 0);
            return;
        }
        open(player, npc);
    }

    private void openQuestTool(Player player, CustomNpc npc, java.util.function.BiConsumer<Player, CustomNpc> open) {
        if (!npc.isQuestNpc()) {
            player.sendMessage("§eSwitch to Quest NPC first.");
            return;
        }
        if (!npc.hasLinkedQuest()) {
            player.sendMessage("§eCreate or link a quest path first.");
            QuestLinkMenu.open(player, npc, 0);
            return;
        }
        if (editor.editorQuests().get(npc.getLinkedQuestId()) == null) {
            player.sendMessage("§eStory quests are read-only. Create a new quest path to edit rewards.");
            QuestLinkMenu.open(player, npc, 0);
            return;
        }
        open.accept(player, npc);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public record Holder(String npcId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
