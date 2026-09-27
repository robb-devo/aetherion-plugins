package de.aetherion.quests.editor;

import de.aetherion.quests.model.QuestState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Health check for one editor NPC — catches broken wiring before players find it.
 * Every issue knows where it lives so the studio can jump straight to the fix.
 */
public final class NpcCheck {

    public enum Level {
        /** Players will hit an error or a dead button. */
        PROBLEM,
        /** Works, but probably not what you meant. */
        WARNING
    }

    /** Where an issue can be fixed. */
    public enum Where {
        OVERVIEW,
        QUEST,
        PAGE,
        REPLY
    }

    public record Issue(Level level, String text, Where where, String pageId, int index) {
        public boolean problem() {
            return level == Level.PROBLEM;
        }
    }

    private NpcCheck() {
    }

    public static List<Issue> run(CustomNpc npc, QuestCatalog quests) {
        List<Issue> issues = new ArrayList<>();
        if (npc == null) {
            return issues;
        }
        if (npc.location() == null) {
            issues.add(new Issue(Level.WARNING,
                    npc.getWorld() == null ? "Not placed yet — use Move here." : "Its world '" + npc.getWorld() + "' isn't loaded.",
                    Where.OVERVIEW, null, -1));
        }
        boolean questsReady = quests != null && quests.available();
        if (npc.hasLinkedQuest() && questsReady && !quests.exists(npc.getLinkedQuestId())) {
            issues.add(new Issue(Level.PROBLEM, "Main quest '" + npc.getLinkedQuestId() + "' doesn't exist anymore.",
                    Where.QUEST, null, -1));
        } else if (npc.hasLinkedQuest() && questsReady && quests.isTutorial(npc.getLinkedQuestId())) {
            issues.add(new Issue(Level.WARNING, "'" + quests.title(npc.getLinkedQuestId()) + "' is a tutorial quest — "
                    + "handing it out here can skip Harbour steps.", Where.QUEST, null, -1));
        }
        for (QuestState stage : CustomNpc.QUEST_STAGES) {
            String mapped = npc.rawQuestPage(stage);
            if (mapped != null && npc.page(mapped) == null) {
                issues.add(new Issue(Level.WARNING, stageName(stage) + " page '" + mapped + "' is gone — "
                        + "the first page is used instead.", Where.QUEST, null, -1));
            } else if (mapped != null && !npc.hasLinkedQuest()) {
                issues.add(new Issue(Level.WARNING, "Quest stage pages are set, but no main quest is picked.",
                        Where.QUEST, null, -1));
                break;
            }
        }

        Set<String> reachable = reachable(npc);
        for (CustomNpc.DialoguePage page : npc.orderedPages()) {
            String title = page.title();
            if (page.isEmpty()) {
                issues.add(new Issue(Level.WARNING, "Page '" + title + "' is empty — the NPC says nothing and the chat ends.",
                        Where.PAGE, page.id(), -1));
            }
            if (page.lines().stream().anyMatch(line -> "…".equals(line == null ? null : line.trim()))) {
                issues.add(new Issue(Level.WARNING, "Page '" + title + "' still has placeholder text (…).",
                        Where.PAGE, page.id(), -1));
            }
            if (!reachable.contains(page.id())) {
                issues.add(new Issue(Level.WARNING, "Nothing leads to page '" + title + "' — link it from a reply.",
                        Where.PAGE, page.id(), -1));
            }
            for (int i = 0; i < page.choices().size(); i++) {
                String problem = replyProblem(npc, page.choices().get(i), quests);
                if (problem != null) {
                    issues.add(new Issue(Level.PROBLEM, problem, Where.REPLY, page.id(), i));
                }
            }
        }
        issues.sort((a, b) -> a.level().compareTo(b.level()));
        return issues;
    }

    /** What's wrong with one reply, in plain words — or null when it's fine. */
    public static String replyProblem(CustomNpc npc, CustomNpc.DialogueChoice choice, QuestCatalog quests) {
        String label = "Reply '" + short24(choice.text()) + "'";
        if (choice.text().isBlank() || "…".equals(choice.text().trim())) {
            return "A reply has no text yet.";
        }
        switch (choice.action()) {
            case PAGE -> {
                if (choice.target().isBlank()) {
                    return label + " doesn't say which page to open.";
                }
                if (npc.page(choice.target()) == null) {
                    return label + " opens page '" + choice.target() + "', which doesn't exist.";
                }
            }
            case OFFER_QUEST, START_QUEST, TURN_IN_QUEST -> {
                String questId = npc.questFor(choice);
                if (questId == null || questId.isBlank()) {
                    return label + " needs a quest — pick one or set a main quest.";
                }
                if (quests != null && quests.available() && !quests.exists(questId)) {
                    return label + " uses quest '" + questId + "', which doesn't exist.";
                }
            }
            case RUN_CONSOLE, RUN_PLAYER -> {
                String problem = DialogueRuntime.commandProblem(choice.target());
                if (problem != null) {
                    return label + ": " + problem;
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** Pages a player can actually reach: first page, quest stage pages and everything linked from them. */
    public static Set<String> reachable(CustomNpc npc) {
        Set<String> seen = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(npc.getStartPage());
        for (QuestState stage : CustomNpc.QUEST_STAGES) {
            String staged = npc.questPage(stage);
            if (staged != null) {
                queue.add(staged);
            }
        }
        while (!queue.isEmpty()) {
            String id = queue.remove(queue.size() - 1);
            CustomNpc.DialoguePage page = npc.page(id);
            if (page == null || !seen.add(page.id())) {
                continue;
            }
            for (CustomNpc.DialogueChoice choice : page.choices()) {
                if (choice.action() == DialogueAction.PAGE && !choice.target().isBlank()) {
                    queue.add(choice.target());
                }
            }
        }
        return seen;
    }

    public static List<Issue> forPage(List<Issue> issues, String pageId) {
        List<Issue> out = new ArrayList<>();
        for (Issue issue : issues) {
            if (pageId != null && pageId.equalsIgnoreCase(issue.pageId())) {
                out.add(issue);
            }
        }
        return out;
    }

    public static long problems(List<Issue> issues) {
        return issues.stream().filter(Issue::problem).count();
    }

    public static String stageName(QuestState stage) {
        if (stage == null) {
            return "Not started";
        }
        return switch (stage) {
            case AVAILABLE -> "Not started";
            case ACTIVE -> "In progress";
            case READY -> "Ready to turn in";
            case COMPLETED -> "Completed";
        };
    }

    private static String short24(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 24 ? text.substring(0, 23) + "…" : text;
    }
}
