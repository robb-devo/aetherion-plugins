package de.aetherion.quests.ui;

import de.aetherion.quests.dialog.DialogManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.util.QuestSkillGate;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat-free Accept / Decline after NPC dialog.
 */
public final class QuestAcceptGUI implements Listener {

    public static final String TITLE = "§8Quest Offer";

    private static String titleFor(Player player) {
        return de.aetherion.quests.lang.LangPack.ui(player, "quest_offer", TITLE);
    }

    private static final int SLOT_INFO = 4;
    private static final int SLOT_ACCEPT = 11;
    private static final int SLOT_DECLINE = 15;
    private static final int SLOT_WARN = 13;

    private final DialogManager dialogManager;

    public QuestAcceptGUI(JavaPlugin plugin, DialogManager dialogManager) {
        this.dialogManager = dialogManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, QuestNPC npc, Quest quest, Quest conflicting, String gateFail) {
        if (player == null || npc == null || quest == null) {
            return;
        }

        Holder holder = new Holder(npc.getId(), quest.getId(), conflicting != null ? conflicting.getId() : null);
        Inventory inventory = Bukkit.createInventory(holder, 27, titleFor(player));
        fill(inventory);

        inventory.setItem(SLOT_INFO, infoItem(player, npc, quest, conflicting, gateFail));

        if (gateFail != null) {
            inventory.setItem(SLOT_ACCEPT, button(
                    Material.BARRIER,
                    "§cCannot accept yet",
                    gateFail,
                    QuestSkillGate.requirementHint(quest) != null
                            ? QuestSkillGate.requirementHint(quest)
                            : "§7Come back when the numbers agree."
            ));
            inventory.setItem(SLOT_DECLINE, button(
                    Material.RED_CONCRETE,
                    "§cClose",
                    "§7Walk away. No shame. Much shame."
            ));
        } else if (conflicting != null) {
            inventory.setItem(SLOT_WARN, button(
                    Material.ORANGE_CONCRETE,
                    "§6Abort current quest?",
                    "§cAccepting will abort:",
                    "§f" + conflicting.getTitle(),
                    "",
                    "§7Only one tracked quest at a time."
            ));
            inventory.setItem(SLOT_ACCEPT, button(
                    Material.LIME_CONCRETE,
                    "§6§lAccept anyway",
                    "§cAborts §f" + conflicting.getTitle(),
                    "§aStarts §f" + quest.getTitle()
            ));
            inventory.setItem(SLOT_DECLINE, button(
                    Material.RED_CONCRETE,
                    "§c§lCancel",
                    "§7Keep §f" + conflicting.getTitle()
            ));
        } else {
            inventory.setItem(SLOT_ACCEPT, button(
                    Material.LIME_CONCRETE,
                    de.aetherion.quests.lang.LangPack.ui(player, "accept", "§a§lAccept"),
                    "§7Start §f" + de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle())
            ));
            inventory.setItem(SLOT_DECLINE, button(
                    Material.RED_CONCRETE,
                    de.aetherion.quests.lang.LangPack.ui(player, "decline", "§c§lDecline"),
                    "§7Maybe another time."
            ));
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.15f);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.quests.lang.LangPack.ui(player, "quest_offer_hint", "Quest offer — pick Accept or Decline"),
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_DECLINE) {
            player.closeInventory();
            dialogManager.handleGuiDecline(player, holder.questId());
            return;
        }
        if (slot != SLOT_ACCEPT) {
            return;
        }

        // Locked by gate — only close.
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            dialogManager.handleGuiDecline(player, holder.questId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }

        player.closeInventory();
        dialogManager.handleGuiAccept(player, holder.questId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack infoItem(Player player, QuestNPC npc, Quest quest, Quest conflicting, String gateFail) {
        List<String> lore = new ArrayList<>();
        lore.add("§7From §f" + npc.getName());
        lore.add("");
        String description = de.aetherion.quests.lang.LangPack.questDescription(
                player, quest.getId(), quest.getDescription() != null ? quest.getDescription() : "");
        if (description != null && !description.isBlank()) {
            for (String line : wrap(description, 42)) {
                lore.add("§f" + line);
            }
            lore.add("");
        }
        if (!quest.getObjectives().isEmpty()) {
            lore.add("§eObjectives");
            for (Objective objective : quest.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                String name = objective.getDisplayName() != null
                        ? objective.getDisplayName()
                        : objective.getTarget();
                int amount = Math.max(1, objective.getAmount());
                lore.add("§8• §7" + name + " §8x" + amount);
            }
            lore.add("");
        }
        if (gateFail != null) {
            lore.add("§c" + gateFail);
        } else if (conflicting != null) {
            lore.add("§cWill abort: §f" + de.aetherion.quests.lang.LangPack.questTitle(
                    player, conflicting.getId(), conflicting.getTitle()));
        } else {
            lore.add("§aReady when you are.");
        }
        return button(
                Material.WRITABLE_BOOK,
                "§6" + de.aetherion.quests.lang.LangPack.questTitle(player, quest.getId(), quest.getTitle()),
                lore.toArray(String[]::new)
        );
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public record Holder(String npcId, String questId, String conflictingQuestId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
