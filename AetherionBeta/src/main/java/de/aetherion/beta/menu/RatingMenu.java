package de.aetherion.beta.menu;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class RatingMenu {

    public static final String Q_OVERALL = "overall";
    public static final String Q_FAVORITE = "favorite";
    public static final String Q_CONFUSING = "confusing";
    public static final String Q_RETURN = "return";

    private static final Map<Integer, Choice> CHOICES = new HashMap<>();

    static {
        // Row 1 — overall
        CHOICES.put(10, new Choice(Q_OVERALL, "great", Material.LIME_STAINED_GLASS));
        CHOICES.put(11, new Choice(Q_OVERALL, "okay", Material.YELLOW_STAINED_GLASS));
        CHOICES.put(12, new Choice(Q_OVERALL, "rough", Material.RED_STAINED_GLASS));
        // Row 2 — favorite
        CHOICES.put(19, new Choice(Q_FAVORITE, "quests", Material.WRITABLE_BOOK));
        CHOICES.put(20, new Choice(Q_FAVORITE, "gather", Material.IRON_PICKAXE));
        CHOICES.put(21, new Choice(Q_FAVORITE, "combat", Material.DIAMOND_SWORD));
        CHOICES.put(22, new Choice(Q_FAVORITE, "pets", Material.BONE));
        CHOICES.put(23, new Choice(Q_FAVORITE, "dungeons", Material.ENDER_EYE));
        // Row 3 — confusing
        CHOICES.put(28, new Choice(Q_CONFUSING, "quests", Material.BOOK));
        CHOICES.put(29, new Choice(Q_CONFUSING, "gather", Material.WHEAT));
        CHOICES.put(30, new Choice(Q_CONFUSING, "combat", Material.IRON_SWORD));
        CHOICES.put(31, new Choice(Q_CONFUSING, "pets", Material.LEAD));
        CHOICES.put(32, new Choice(Q_CONFUSING, "dungeons", Material.OBSIDIAN));
        CHOICES.put(33, new Choice(Q_CONFUSING, "nothing", Material.LIME_DYE));
        // Row 4 — return
        CHOICES.put(39, new Choice(Q_RETURN, "yes", Material.LIME_STAINED_GLASS_PANE));
        CHOICES.put(40, new Choice(Q_RETURN, "maybe", Material.YELLOW_STAINED_GLASS_PANE));
        CHOICES.put(41, new Choice(Q_RETURN, "no", Material.RED_STAINED_GLASS_PANE));
    }

    private static final int SLOT_BACK = 45;
    private static final int SLOT_SUBMIT = 53;

    private RatingMenu() {
    }

    public static void open(AetherionBeta plugin, Player player) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, Texts.ratingTitle(lang));
        GuiUtil.frame(inventory);

        inventory.setItem(4, GuiUtil.named(
                Material.AMETHYST_CLUSTER,
                "§d✦ " + Texts.t(lang, "Quick feedback", "Kurzes Feedback"),
                Texts.t(lang, "§7Tap glass / icons — multiple choice.", "§7Glas / Icons tippen — Multiple Choice."),
                Texts.t(lang, "§8Selected options glow.", "§8Auswahl leuchtet.")
        ));

        inventory.setItem(9, GuiUtil.named(Material.PAPER, Texts.qOverall(lang)));
        inventory.setItem(18, GuiUtil.named(Material.PAPER, Texts.qFavorite(lang)));
        inventory.setItem(27, GuiUtil.named(Material.PAPER, Texts.qConfusing(lang)));
        inventory.setItem(36, GuiUtil.named(Material.PAPER, Texts.qReturn(lang)));

        for (Map.Entry<Integer, Choice> entry : CHOICES.entrySet()) {
            Choice choice = entry.getValue();
            boolean selected = choice.value().equals(data.answers().get(choice.question()));
            Material mat = selected ? glow(choice.material()) : choice.material();
            String prefix = selected ? "§a▶ " : "§7";
            inventory.setItem(entry.getKey(), GuiUtil.named(
                    mat,
                    prefix + Texts.choice(lang, choice.value()),
                    selected
                            ? Texts.t(lang, "§aSelected", "§aAusgewählt")
                            : Texts.t(lang, "§8Click to choose", "§8Klicken zum Wählen")
            ));
        }

        inventory.setItem(SLOT_BACK, GuiUtil.named(Material.ARROW, Texts.back(lang)));
        inventory.setItem(SLOT_SUBMIT, GuiUtil.named(
                Material.NETHER_STAR,
                Texts.submit(lang),
                Texts.t(lang, "§7Saves to the beta board.", "§7Speichert auf dem Beta-Board.")
        ));

        player.openInventory(inventory);
    }

    public static void handle(AetherionBeta plugin, Player player, int slot) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);

        if (slot == SLOT_BACK) {
            ChecklistMenu.open(plugin, player);
            return;
        }
        if (slot == SLOT_SUBMIT) {
            if (!ready(data)) {
                player.sendMessage(Texts.needAnswers(lang));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                return;
            }
            data.setSubmitted(true);
            plugin.store().save(data);
            player.closeInventory();
            player.sendMessage(Texts.submitted(lang));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            plugin.getLogger().info("Beta feedback from " + player.getName()
                    + " overall=" + data.answers().get(Q_OVERALL)
                    + " favorite=" + data.answers().get(Q_FAVORITE)
                    + " confusing=" + data.answers().get(Q_CONFUSING)
                    + " return=" + data.answers().get(Q_RETURN));
            return;
        }

        Choice choice = CHOICES.get(slot);
        if (choice == null) {
            return;
        }
        data.answer(choice.question(), choice.value());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.5f);
        open(plugin, player);
    }

    private static boolean ready(BetaPlayerData data) {
        return data.answers().containsKey(Q_OVERALL)
                && data.answers().containsKey(Q_FAVORITE)
                && data.answers().containsKey(Q_CONFUSING)
                && data.answers().containsKey(Q_RETURN);
    }

    private static Material glow(Material material) {
        return switch (material) {
            case LIME_STAINED_GLASS, LIME_STAINED_GLASS_PANE -> Material.LIME_CONCRETE;
            case YELLOW_STAINED_GLASS, YELLOW_STAINED_GLASS_PANE -> Material.YELLOW_CONCRETE;
            case RED_STAINED_GLASS, RED_STAINED_GLASS_PANE -> Material.RED_CONCRETE;
            default -> material;
        };
    }

    private record Choice(String question, String value, Material material) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
