package de.aetherion.beta.npc;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Milestone;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.beta.data.BetaStore;
import de.aetherion.beta.item.BetaBook;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.beta.menu.LanguageMenu;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;
import de.aetherion.core.npc.CastTroupe;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Patch Courier waits at one shared anchor. He is a real person (a body on a Core {@link CastTroupe}), but a
 * private one: only players who have not picked up their journal yet see him, so the square is not crowded by a
 * courier nobody else needs. There is one body per language, so the name over his head reads right for whoever
 * sees it.
 */
public final class BetaNpcService {

    private static final String SPEAKER_ID = "beta_courier";
    private static final String SKIN = "beta_courier";
    private static final Color COAT = Color.fromRGB(0x8a5fd0);

    private final AetherionBeta plugin;
    private final BetaStore store;
    private final BetaBook book;
    private final CastTroupe troupe;
    /** Who sees the courier right now, and which of the two bodies. */
    private final Map<UUID, String> shown = new ConcurrentHashMap<>();

    public BetaNpcService(AetherionBeta plugin, BetaStore store, BetaBook book) {
        this.plugin = plugin;
        this.store = store;
        this.book = book;
        this.troupe = openTroupe(plugin);
    }

    /** The journal and the checklist work without him: a courier that cannot stand must not stop the plugin. */
    private CastTroupe openTroupe(AetherionBeta host) {
        try {
            return CastTroupe.open(host, "beta", (player, key) -> interact(player));
        } catch (RuntimeException | LinkageError failure) {
            host.getLogger().warning("Patch Courier is off: " + failure);
            return null;
        }
    }

    public boolean hasAnchor() {
        return plugin.getConfig().getBoolean("npc.enabled", false)
                && plugin.getConfig().getString("npc.world") != null;
    }

    public Location anchor() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("npc.enabled", false)) {
            return null;
        }
        World world = Bukkit.getWorld(config.getString("npc.world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble("npc.x"),
                config.getDouble("npc.y"),
                config.getDouble("npc.z"),
                (float) config.getDouble("npc.yaw"),
                (float) config.getDouble("npc.pitch")
        );
    }

    public void placeAnchor(Location location) {
        placeAnchor(location, null);
    }

    /**
     * Set the shared anchor. When {@code placer} is set, they always see the courier
     * here (even if they already finished MEET_GUIDE) so admins can check the spot.
     */
    public void placeAnchor(Location location, Player placer) {
        FileConfiguration config = plugin.getConfig();
        config.set("npc.enabled", true);
        config.set("npc.world", location.getWorld().getName());
        config.set("npc.x", location.getX());
        config.set("npc.y", location.getY());
        config.set("npc.z", location.getZ());
        config.set("npc.yaw", location.getYaw());
        config.set("npc.pitch", location.getPitch());
        plugin.saveConfig();
        respawnAll();
        if (placer != null && placer.isOnline()) {
            forceEnsureFor(placer);
        }
    }

    /** Shows the courier to {@code player} ignoring the book / milestone gates. */
    public void forceEnsureFor(Player player) {
        if (player == null || !hasAnchor()) {
            return;
        }
        reveal(player);
    }

    public void clearAnchor() {
        plugin.getConfig().set("npc.enabled", false);
        plugin.saveConfig();
        despawnAll();
    }

    public void ensureFor(Player player) {
        if (player == null || !hasAnchor()) {
            return;
        }
        BetaPlayerData data = store.get(player.getUniqueId());
        if (data.bookGiven() || data.isDone(Milestone.MEET_GUIDE)) {
            despawnFor(player);
            return;
        }
        reveal(player);
    }

    private void reveal(Player player) {
        Location anchor = anchor();
        if (troupe == null || anchor == null) {
            return;
        }
        BetaLang lang = store.get(player.getUniqueId()).langOr(BetaLang.EN);
        String key = lang == BetaLang.DE ? "de" : "en";
        String before = shown.put(player.getUniqueId(), key);
        if (before != null && !before.equals(key)) {
            troupe.hide(before, player);
        }
        if (!troupe.keys().contains(key)) {
            troupe.spawn(key, anchor, look(lang));
            troupe.habit(key, CastTroupe.Habit.watch(9, 18));
        }
        troupe.show(key, player);
    }

    private CastTroupe.Look look(BetaLang lang) {
        String plate = lang == BetaLang.DE
                ? plugin.getConfig().getString("npc.name-de", "§d✦ Patch-Kurier")
                : plugin.getConfig().getString("npc.name-en", "§d✦ Patch Courier");
        return CastTroupe.Look
                .of(lang == BetaLang.DE ? "Patch-Kurier" : "Patch Courier", SKIN, Material.WRITTEN_BOOK, COAT)
                .stand(Villager.Profession.LIBRARIAN, Villager.Type.PLAINS)
                .labelled(plate)
                .hidden();
    }

    /** The courier walks out of {@code player}'s sight (he stays for everybody still waiting for a journal). */
    public void despawnFor(Player player) {
        if (player == null) {
            return;
        }
        String key = shown.remove(player.getUniqueId());
        if (key != null && troupe != null) {
            troupe.hide(key, player);
        }
    }

    public void respawnAll() {
        despawnAll();
        if (!hasAnchor()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureFor(player);
        }
    }

    public void despawnAll() {
        shown.clear();
        if (troupe != null) {
            troupe.clear();
        }
    }

    /** Plugin shutdown: the courier leaves and his troupe stops listening. */
    public void shutdown() {
        shown.clear();
        if (troupe != null) {
            troupe.close();
        }
    }

    /** Where he stands (his feet), or null while he has no body. */
    private Location spot() {
        if (troupe == null) {
            return null;
        }
        Location at = troupe.location("en");
        return at != null ? at : troupe.location("de");
    }

    private void interact(Player player) {
        if (!shown.containsKey(player.getUniqueId())) {
            return;
        }
        Location spot = spot();
        if (spot == null) {
            return;
        }
        BetaPlayerData data = store.get(player.getUniqueId());
        data.setName(player.getName());
        BetaLang lang = data.langOr(BetaLang.EN);

        TalkAccess talk = AetherServices.talk();
        if (talk != null && talk.bubbles(player)) {
            String display = lang == BetaLang.DE ? "Patch-Kurier" : "Patch Courier";
            talk.registerSpeaker(new TalkAccess.Speaker(SPEAKER_ID, display, "§d", "isle_hand", this::spot));
            if (talk.recentlyPicked(player)) {
                return;
            }
            if (talk.hasReplies(player, SPEAKER_ID)) {
                talk.pulse(player);
                return;
            }
            if (talk.talkingWith(player, SPEAKER_ID)) {
                offerDoneChip(player, lang, data);
                return;
            }
            player.playSound(spot, Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
            if (!talk.say(player, SPEAKER_ID, Texts.bubbleThanks(lang, player.getName()))) {
                chatFallback(player, spot, data, lang);
                return;
            }
            talk.say(player, SPEAKER_ID, Texts.bubbleBook(lang));
            giveBook(player, data, lang, false);
            de.aetherion.beta.BetaJournal.progress(plugin, player, Milestone.MEET_GUIDE);
            offerDoneChip(player, lang, data);
            return;
        }

        chatFallback(player, spot, data, lang);
    }

    private void offerDoneChip(Player player, BetaLang lang, BetaPlayerData data) {
        TalkAccess talk = AetherServices.talk();
        if (talk == null) {
            return;
        }
        String chip = Texts.bubbleChip(lang);
        talk.replies(player, SPEAKER_ID, List.of(
                new TalkAccess.Reply(chip, NamedTextColor.GREEN, () -> finishTalk(player, data), chip)
        ));
    }

    private void finishTalk(Player player, BetaPlayerData data) {
        TalkAccess talk = AetherServices.talk();
        if (talk != null) {
            talk.end(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                despawnFor(player);
            }
        }, 8L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (data.lang() == null) {
                LanguageMenu.open(plugin, player);
            } else {
                ChecklistMenu.open(plugin, player);
            }
        }, 12L);
    }

    private void chatFallback(Player player, Location spot, BetaPlayerData data, BetaLang lang) {
        player.sendMessage(Texts.npcGreeting(lang, player.getName()));
        player.playSound(spot, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.1f);
        giveBook(player, data, lang, true);
        de.aetherion.beta.BetaJournal.progress(plugin, player, Milestone.MEET_GUIDE);
        player.sendMessage(Texts.npcFarewell(lang));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                despawnFor(player);
            }
        }, 35L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (data.lang() == null) {
                LanguageMenu.open(plugin, player);
            } else {
                ChecklistMenu.open(plugin, player);
            }
        }, 55L);
    }

    public void giveBook(Player player, BetaPlayerData data, BetaLang lang) {
        giveBook(player, data, lang, true);
    }

    public void giveBook(Player player, BetaPlayerData data, BetaLang lang, boolean chat) {
        if (hasBook(player)) {
            if (!data.bookGiven()) {
                data.setBookGiven(true);
            }
            if (chat) {
                player.sendMessage(Texts.alreadyHaveBook(lang));
            }
            return;
        }
        ItemStack created = book.create(lang);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(created);
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), created);
        }
        data.setBookGiven(true);
        if (chat) {
            player.sendMessage(Texts.bookGiven(lang));
        }
    }

    public boolean hasBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (book.isBook(item)) {
                return true;
            }
        }
        return book.isBook(player.getInventory().getItemInOffHand());
    }
}
