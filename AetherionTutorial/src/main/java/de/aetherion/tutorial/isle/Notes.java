package de.aetherion.tutorial.isle;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.QuestNpcInfo;
import de.aetherion.core.api.QuestProgressAccess;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sits in front of the Quests plugin's progress service and listens.
 *
 * <p>Every gameplay plugin already tells Quests what a player just did ("opened the Journal", "crafted a Simple
 * Pickaxe", "broke stone", "parried"). The lessons need exactly those reports, so the isle wraps the registered
 * service instead of adding hooks to five plugins: each call is passed on unchanged and also shown to the
 * {@link Director}. Four answers are the isle's own while a player is in a lesson:
 * <ul>
 *     <li>{@code canChopTrees}: true from the felling lesson on (the harbour's Forager is not here to allow it);</li>
 *     <li>{@code isQuestActive("lesson_combat")}: true during the drill, because the combat kit only reports clean
 *     moves while that quest runs;</li>
 *     <li>the Journal's goal list and the HUD radar point at the current lesson;</li>
 *     <li>{@code isQuestCompleted}: the harbour quests the isle stands in for count as done as far as it has taught
 *     them, so the Journal's path does not point at people who are not here.</li>
 * </ul>
 */
final class Notes implements QuestProgressAccess {

    static final String DRILL_QUEST = "lesson_combat";

    private final QuestProgressAccess inner;
    private final Director director;

    private Notes(QuestProgressAccess inner, Director director) {
        this.inner = inner;
        this.director = director;
    }

    /** Wraps whatever is registered now. Returns the wrapper (to unwrap on disable), or null without Quests. */
    static Notes install(Director director) {
        QuestProgressAccess current = AetherServices.quests();
        if (current == null || current instanceof Notes) {
            return current instanceof Notes notes ? notes : null;
        }
        Notes notes = new Notes(current, director);
        AetherServices.registerQuests(notes);
        return notes;
    }

    void uninstall() {
        if (AetherServices.quests() == this) {
            AetherServices.registerQuests(inner);
        }
    }

    // ------------------------------------------------------------------ what the lessons listen to

    @Override
    public void noteBroken(Player player, Material material, int amount) {
        inner.noteBroken(player, material, amount);
        director.onBroken(player, material, amount);
    }

    @Override
    public void noteCollected(Player player, Material material, int amount) {
        inner.noteCollected(player, material, amount);
    }

    @Override
    public void noteInventoryGain(Player player) {
        inner.noteInventoryGain(player);
    }

    @Override
    public void noteUsed(Player player, String target) {
        inner.noteUsed(player, target);
        director.onUsed(player, target);
    }

    @Override
    public void noteCrafted(Player player, String target, int amount) {
        inner.noteCrafted(player, target, amount);
        director.onCrafted(player, target, amount);
    }

    // ------------------------------------------------------------------ answers the isle gives itself

    @Override
    public boolean isQuestActive(Player player, String questId) {
        if (DRILL_QUEST.equalsIgnoreCase(questId) && director.drilling(player)) {
            return true;
        }
        return inner.isQuestActive(player, questId);
    }

    @Override
    public boolean canChopTrees(Player player) {
        return director.mayChop(player) || inner.canChopTrees(player);
    }

    @Override
    public List<JournalQuest> journalQuests(Player player) {
        JournalQuest lesson = director.journalEntry(player);
        if (lesson == null) {
            return inner.journalQuests(player);
        }
        List<JournalQuest> out = new ArrayList<>();
        out.add(lesson);
        return out;
    }

    @Override
    public Location questTarget(Player player) {
        Location target = director.target(player);
        return target != null ? target : inner.questTarget(player);
    }

    @Override
    public boolean isQuestCompleted(Player player, String questId) {
        return director.stoodIn(player, questId) || inner.isQuestCompleted(player, questId);
    }

    // ------------------------------------------------------------------ passed straight on

    @Override
    public boolean shouldGuidePetsEquip(Player player) {
        return inner.shouldGuidePetsEquip(player);
    }

    @Override
    public void unlockForagerChop(Player player) {
        inner.unlockForagerChop(player);
    }

    @Override
    public void npcLine(Player player, String npcId, String displayName, String line) {
        inner.npcLine(player, npcId, displayName, line);
    }

    @Override
    public void suppress(Player player) {
        inner.suppress(player);
    }

    @Override
    public void unsuppress(Player player) {
        inner.unsuppress(player);
    }

    @Override
    public void unsuppress(UUID playerId) {
        inner.unsuppress(playerId);
    }

    @Override
    public void flushPlayer(Player player) {
        inner.flushPlayer(player);
    }

    @Override
    public List<QuestNpcInfo> npcs() {
        return inner.npcs();
    }

    @Override
    public ItemStack npcAnchor(String npcId) {
        return inner.npcAnchor(npcId);
    }

    @Override
    public ItemStack merchantChest() {
        return inner.merchantChest();
    }

    @Override
    public ItemStack exploreChest(String kind) {
        return inner.exploreChest(kind);
    }

    @Override
    public String despawnNpc(Entity entity) {
        return inner.despawnNpc(entity);
    }

    @Override
    public List<Location> npcsNear(Player player, double radius) {
        return inner.npcsNear(player, radius);
    }
}
