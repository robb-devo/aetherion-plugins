package de.aetherion.quests.editor;

import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;

import java.util.List;

/**
 * Starting points for new NPCs, plus the one-click "quest giver" conversation builder.
 * Templates only write ordinary pages / replies — nothing the runtime doesn't already understand.
 */
public final class NpcTemplates {

    public static final String PAGE_ACTIVE = "in_progress";
    public static final String PAGE_READY = "ready";
    public static final String PAGE_DONE = "completed";

    public enum Template {
        TALKER("Talker", Material.PAPER, "Resident",
                List.of("Says a few lines, then goodbye.", "Great for lore, hints and ambience.")),
        QUEST_GIVER("Quest giver", Material.MAP, "Quest Giver",
                List.of("Offers a quest, checks on progress", "and takes the turn-in — with its own",
                        "greeting for every quest stage.")),
        GUIDE("Guide", Material.COMPASS, "Guide",
                List.of("Answers questions on a few topics.", "Shows how pages link together.")),
        SERVICE("Service", Material.COMMAND_BLOCK, "Service",
                List.of("Runs a command when the player agrees", "— teleport, open a shop, give an item.")),
        BLANK("Blank", Material.WHITE_BANNER, "Guide",
                List.of("One page, one \"Goodbye\".", "Build everything yourself."));

        private final String title;
        private final Material icon;
        private final String subtitle;
        private final List<String> description;

        Template(String title, Material icon, String subtitle, List<String> description) {
            this.title = title;
            this.icon = icon;
            this.subtitle = subtitle;
            this.description = description;
        }

        public String title() {
            return title;
        }

        public Material icon() {
            return icon;
        }

        public List<String> description() {
            return description;
        }
    }

    private NpcTemplates() {
    }

    /** Replaces the NPC's conversation with the template (used on brand-new NPCs only). */
    public static void apply(CustomNpc npc, Template template) {
        npc.pages().clear();
        npc.setStartPage(CustomNpc.START_PAGE);
        npc.setSubtitle(template.subtitle);
        CustomNpc.DialoguePage greeting = new CustomNpc.DialoguePage(CustomNpc.START_PAGE);
        npc.pages().put(greeting.id(), greeting);
        switch (template) {
            case TALKER -> {
                greeting.lines().add("Oh, hello {player}!");
                greeting.lines().add("Lovely weather today, isn't it?");
                reply(greeting, "Goodbye!", DialogueAction.CLOSE, "");
            }
            case QUEST_GIVER -> {
                greeting.lines().add("Ah, {player}! Just who I was looking for.");
                greeting.lines().add("I could really use a hand with something.");
                reply(greeting, "What do you need?", DialogueAction.OFFER_QUEST, "");
                reply(greeting, "Maybe later.", DialogueAction.CLOSE, "");
                buildQuestGiver(npc);
            }
            case GUIDE -> {
                greeting.lines().add("Welcome, traveller! Ask me anything.");
                reply(greeting, "What is this place?", DialogueAction.PAGE, "about");
                reply(greeting, "Where should I go?", DialogueAction.PAGE, "directions");
                reply(greeting, "Thanks, bye!", DialogueAction.CLOSE, "");
                CustomNpc.DialoguePage about = page(npc, "about");
                about.lines().add("A place of old stories and new beginnings.");
                about.lines().add("Every island here hides something worth finding.");
                reply(about, "Tell me something else.", DialogueAction.PAGE, CustomNpc.START_PAGE);
                reply(about, "Thanks, bye!", DialogueAction.CLOSE, "");
                CustomNpc.DialoguePage directions = page(npc, "directions");
                directions.lines().add("Follow the path and keep your eyes open.");
                directions.lines().add("Adventure is never far away around here.");
                reply(directions, "Tell me something else.", DialogueAction.PAGE, CustomNpc.START_PAGE);
                reply(directions, "Thanks, bye!", DialogueAction.CLOSE, "");
            }
            case SERVICE -> {
                greeting.lines().add("Need a hand, {player}?");
                reply(greeting, "Yes, please!", DialogueAction.RUN_PLAYER, "");
                reply(greeting, "No thanks.", DialogueAction.CLOSE, "");
            }
            case BLANK -> {
                greeting.lines().add("Hello there.");
                reply(greeting, "Goodbye", DialogueAction.CLOSE, "");
            }
        }
    }

    /** What {@link #buildQuestGiver} changed, for the confirmation message. */
    public record Setup(int pagesAdded, int repliesAdded, int stagesLinked) {
        public boolean changed() {
            return pagesAdded + repliesAdded + stagesLinked > 0;
        }
    }

    /**
     * Turns any NPC into a working quest giver without touching existing lines:
     * an "Offer" reply on the first page, plus pages for In progress / Ready / Completed wired to those stages.
     * Safe to run twice — existing pieces are reused.
     */
    public static Setup buildQuestGiver(CustomNpc npc) {
        int pages = 0;
        int replies = 0;
        int stages = 0;
        CustomNpc.DialoguePage first = npc.page(npc.getStartPage());
        if (first != null && first.choices().stream().noneMatch(choice -> choice.action() == DialogueAction.OFFER_QUEST)
                && first.choices().size() < CustomNpc.MAX_CHOICES) {
            first.choices().add(0, new CustomNpc.DialogueChoice("Got any work for me?", DialogueAction.OFFER_QUEST, ""));
            replies++;
        }
        if (npc.questPage(QuestState.ACTIVE) == null) {
            if (npc.page(PAGE_ACTIVE) == null) {
                CustomNpc.DialoguePage active = page(npc, PAGE_ACTIVE);
                active.lines().add("How's it going, {player}?");
                active.lines().add("Come back once you're done.");
                reply(active, "On it!", DialogueAction.CLOSE, "");
                pages++;
            }
            npc.setQuestPage(QuestState.ACTIVE, PAGE_ACTIVE);
            stages++;
        }
        if (npc.questPage(QuestState.READY) == null) {
            if (npc.page(PAGE_READY) == null) {
                CustomNpc.DialoguePage ready = page(npc, PAGE_READY);
                ready.lines().add("You're back! Did you get it done?");
                reply(ready, "Here you go.", DialogueAction.TURN_IN_QUEST, "");
                reply(ready, "Not yet.", DialogueAction.CLOSE, "");
                pages++;
            }
            npc.setQuestPage(QuestState.READY, PAGE_READY);
            stages++;
        }
        if (npc.questPage(QuestState.COMPLETED) == null) {
            if (npc.page(PAGE_DONE) == null) {
                CustomNpc.DialoguePage done = page(npc, PAGE_DONE);
                done.lines().add("Thanks again, {player}. You're a lifesaver.");
                reply(done, "Anytime!", DialogueAction.CLOSE, "");
                pages++;
            }
            npc.setQuestPage(QuestState.COMPLETED, PAGE_DONE);
            stages++;
        }
        return new Setup(pages, replies, stages);
    }

    private static CustomNpc.DialoguePage page(CustomNpc npc, String id) {
        CustomNpc.DialoguePage page = new CustomNpc.DialoguePage(id);
        npc.pages().put(id, page);
        return page;
    }

    private static void reply(CustomNpc.DialoguePage page, String text, DialogueAction action, String target) {
        page.choices().add(new CustomNpc.DialogueChoice(text, action, target));
    }
}
