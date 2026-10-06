package de.aetherion.beta.npc;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.beta.data.BetaStore;
import de.aetherion.beta.item.BetaBook;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.beta.menu.LanguageMenu;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.TalkAccess;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One shared anchor; each online player gets a personal villager only they can see.
 */
public final class BetaNpcService {

    private final AetherionBeta plugin;
    private final BetaStore store;
    private final BetaBook book;
    private final Map<UUID, UUID> npcByPlayer = new ConcurrentHashMap<>();

    public BetaNpcService(AetherionBeta plugin, BetaStore store, BetaBook book) {
        this.plugin = plugin;
        this.store = store;
        this.book = book;
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
        Location location = new Location(
                world,
                config.getDouble("npc.x"),
                config.getDouble("npc.y"),
                config.getDouble("npc.z"),
                (float) config.getDouble("npc.yaw"),
                (float) config.getDouble("npc.pitch")
        );
        return location;
    }

    public void placeAnchor(Location location) {
        placeAnchor(location, null);
    }

    /**
     * Set the shared anchor. When {@code placer} is set, they always get a personal
     * courier here (even if they already finished MEET_GUIDE) so admins can see it.
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

    /** Spawn (or re-teleport) the personal courier ignoring book / milestone gates. */
    public void forceEnsureFor(Player player) {
        if (player == null || !hasAnchor()) {
            return;
        }
        Location anchor = anchor();
        if (anchor == null) {
            return;
        }
        despawnFor(player);
        spawnPersonal(player, anchor);
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
        if (data.bookGiven() || data.isDone(de.aetherion.beta.Milestone.MEET_GUIDE)) {
            despawnFor(player);
            return;
        }
        Location anchor = anchor();
        if (anchor == null) {
            return;
        }
        UUID existing = npcByPlayer.get(player.getUniqueId());
        if (existing != null) {
            Entity entity = Bukkit.getEntity(existing);
            if (entity != null && !entity.isDead()) {
                if (entity.getWorld().equals(anchor.getWorld())
                        && entity.getLocation().distanceSquared(anchor) > 0.01) {
                    entity.teleport(anchor);
                }
                return;
            }
            npcByPlayer.remove(player.getUniqueId());
        }
        spawnPersonal(player, anchor);
    }

    public void despawnFor(Player player) {
        if (player == null) {
            return;
        }
        UUID entityId = npcByPlayer.remove(player.getUniqueId());
        if (entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
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
        for (UUID playerId : npcByPlayer.keySet()) {
            UUID entityId = npcByPlayer.remove(playerId);
            if (entityId != null) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        npcByPlayer.clear();
        Location anchor = anchor();
        if (anchor != null && anchor.getWorld() != null) {
            for (Entity entity : anchor.getWorld().getEntities()) {
                if (isBetaNpc(entity)) {
                    entity.remove();
                }
            }
        }
    }

    public boolean isBetaNpc(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        return entity.getPersistentDataContainer().has(plugin.npcKey(), PersistentDataType.STRING);
    }

    public UUID ownerOf(Entity entity) {
        if (!isBetaNpc(entity)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(plugin.npcKey(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final String SPEAKER_ID = "beta_courier";

    public void interact(Player player, Entity entity) {
        UUID owner = ownerOf(entity);
        if (owner == null || !owner.equals(player.getUniqueId())) {
            return;
        }
        BetaPlayerData data = store.get(player.getUniqueId());
        data.setName(player.getName());
        BetaLang lang = data.langOr(BetaLang.EN);

        TalkAccess talk = AetherServices.talk();
        if (talk != null && talk.bubbles(player)) {
            String display = lang == BetaLang.DE ? "Patch-Kurier" : "Patch Courier";
            talk.registerSpeaker(new TalkAccess.Speaker(
                    SPEAKER_ID, display, "§d", "isle_hand", entity::getLocation));
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
            player.playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.55f, 1.2f);
            if (!talk.say(player, SPEAKER_ID, Texts.bubbleThanks(lang, player.getName()))) {
                chatFallback(player, entity, data, lang);
                return;
            }
            talk.say(player, SPEAKER_ID, Texts.bubbleBook(lang));
            giveBook(player, data, lang, false);
            de.aetherion.beta.BetaJournal.progress(plugin, player, de.aetherion.beta.Milestone.MEET_GUIDE);
            offerDoneChip(player, lang, data);
            return;
        }

        chatFallback(player, entity, data, lang);
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

    private void chatFallback(Player player, Entity entity, BetaPlayerData data, BetaLang lang) {
        player.sendMessage(Texts.npcGreeting(lang, player.getName()));
        player.playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.7f, 1.2f);
        giveBook(player, data, lang, true);
        de.aetherion.beta.BetaJournal.progress(plugin, player, de.aetherion.beta.Milestone.MEET_GUIDE);
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

    private void spawnPersonal(Player owner, Location anchor) {
        BetaPlayerData data = store.get(owner.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        String display = lang == BetaLang.DE
                ? plugin.getConfig().getString("npc.name-de", "§d✦ Patch-Kurier")
                : plugin.getConfig().getString("npc.name-en", "§d✦ Patch Courier");

        Villager villager = anchor.getWorld().spawn(anchor, Villager.class, spawned -> {
            spawned.setCustomName(display + " §8· §f" + owner.getName());
            spawned.setCustomNameVisible(true);
            spawned.setAI(false);
            spawned.setAware(false);
            spawned.setGravity(true);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCanPickupItems(false);
            spawned.setAdult();
            spawned.setAgeLock(true);
            spawned.setRecipes(java.util.List.of());
            spawned.setProfession(Villager.Profession.LIBRARIAN);
            spawned.setVillagerType(Villager.Type.PLAINS);
            spawned.setVillagerLevel(5);
            spawned.getPersistentDataContainer().set(
                    plugin.npcKey(),
                    PersistentDataType.STRING,
                    owner.getUniqueId().toString()
            );
            EntityEquipment gear = spawned.getEquipment();
            if (gear != null) {
                gear.clear();
                gear.setItemInMainHand(new ItemStack(Material.WRITTEN_BOOK));
                gear.setItemInMainHandDropChance(0.0f);
            }
        });

        npcByPlayer.put(owner.getUniqueId(), villager.getUniqueId());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner.getUniqueId())) {
                other.hideEntity(plugin, villager);
            }
        }
        owner.showEntity(plugin, villager);
    }

    public void hideNewNpcFromOthers(Player joiner) {
        for (Map.Entry<UUID, UUID> entry : npcByPlayer.entrySet()) {
            if (entry.getKey().equals(joiner.getUniqueId())) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entry.getValue());
            if (entity != null) {
                joiner.hideEntity(plugin, entity);
            }
        }
    }
}
