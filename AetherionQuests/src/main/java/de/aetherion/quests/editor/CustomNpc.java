package de.aetherion.quests.editor;

import de.aetherion.quests.model.QuestState;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moderator-created FancyNPC. Stored in {@code editor-npcs.yml}, never in story {@code npcs.yml}.
 */
public final class CustomNpc {

    public static final String START_PAGE = "greeting";
    /** Editor caps — the runtime plays whatever is stored. */
    public static final int MAX_LINES = 14;
    public static final int MAX_CHOICES = 7;
    public static final int MAX_PAGES = 26;
    /** Quest stages that can open on their own page (not started = the normal first page). */
    public static final List<QuestState> QUEST_STAGES = List.of(QuestState.ACTIVE, QuestState.READY, QuestState.COMPLETED);

    private String id;
    private String name;
    private String subtitle;
    private String skinUsername;
    private boolean slim;
    private AppearancePreset preset;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String linkedQuestId;
    private String startPage = START_PAGE;
    private final Map<String, DialoguePage> pages = new LinkedHashMap<>();
    private final Map<QuestState, String> questPages = new EnumMap<>(QuestState.class);
    private String createdBy;
    private String editedBy;
    private long editedAt;

    public CustomNpc(String id, String name) {
        this.id = id;
        this.name = name;
        this.subtitle = "Guide";
        this.preset = AppearancePreset.WORKER;
        this.skinUsername = preset.skinUsername();
        this.slim = preset.slim();
        pages.put(START_PAGE, DialoguePage.starter());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Raw subtitle; blank means "no subtitle line". */
    public String getSubtitle() {
        return subtitle == null ? "" : subtitle;
    }

    public boolean hasSubtitle() {
        return subtitle != null && !subtitle.isBlank();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    public String getSkinUsername() {
        return skinUsername == null || skinUsername.isBlank() ? getPreset().skinUsername() : skinUsername;
    }

    public void setSkinUsername(String skinUsername) {
        this.skinUsername = skinUsername;
    }

    /** True when the skin is just the outfit's default (so changing outfit may change it too). */
    public boolean usesOutfitSkin() {
        return skinUsername == null
                || skinUsername.isBlank()
                || AppearancePreset.isDefaultSkin(skinUsername);
    }

    public boolean isSlim() {
        return slim;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }

    public AppearancePreset getPreset() {
        return preset == null ? AppearancePreset.WORKER : preset;
    }

    public void setPreset(AppearancePreset preset) {
        this.preset = preset == null ? AppearancePreset.WORKER : preset;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        this.world = location.getWorld().getName();
        setLocationFromParts(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void setLocationFromParts(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location location() {
        if (world == null || world.isBlank()) {
            return null;
        }
        World resolved = Bukkit.getWorld(world);
        if (resolved == null) {
            return null;
        }
        return new Location(resolved, x, y, z, yaw, pitch);
    }

    public String getLinkedQuestId() {
        return linkedQuestId;
    }

    public void setLinkedQuestId(String linkedQuestId) {
        this.linkedQuestId = linkedQuestId == null || linkedQuestId.isBlank() ? null : linkedQuestId;
    }

    public boolean hasLinkedQuest() {
        return linkedQuestId != null && !linkedQuestId.isBlank();
    }

    public String getStartPage() {
        if (startPage != null && pages.containsKey(startPage)) {
            return startPage;
        }
        if (pages.containsKey(START_PAGE)) {
            return START_PAGE;
        }
        return pages.isEmpty() ? START_PAGE : pages.keySet().iterator().next();
    }

    public void setStartPage(String startPage) {
        this.startPage = startPage == null ? null : startPage.toLowerCase(Locale.ROOT);
    }

    public boolean isStartPage(String pageId) {
        return pageId != null && pageId.equalsIgnoreCase(getStartPage());
    }

    // ------------------------------------------------------------------ quest stage pages

    /** Page shown first while the main quest is in {@code stage}; null = normal first page. */
    public String questPage(QuestState stage) {
        if (stage == null || stage == QuestState.AVAILABLE) {
            return null;
        }
        String pageId = questPages.get(stage);
        return pageId != null && pages.containsKey(pageId) ? pageId : null;
    }

    /** Raw mapping, including ids of pages that no longer exist (for health checks). */
    public String rawQuestPage(QuestState stage) {
        return stage == null ? null : questPages.get(stage);
    }

    public void setQuestPage(QuestState stage, String pageId) {
        if (stage == null || stage == QuestState.AVAILABLE) {
            return;
        }
        if (pageId == null || pageId.isBlank()) {
            questPages.remove(stage);
        } else {
            questPages.put(stage, pageId.toLowerCase(Locale.ROOT));
        }
    }

    public Map<QuestState, String> questPages() {
        return Collections.unmodifiableMap(questPages);
    }

    /** Where the conversation opens for a player whose main quest is in {@code stage}. */
    public String startPageFor(QuestState stage) {
        String staged = hasLinkedQuest() ? questPage(stage) : null;
        return staged != null ? staged : getStartPage();
    }

    /** Stage whose opening page is {@code pageId}, or null. */
    public QuestState stageOpening(String pageId) {
        if (pageId == null) {
            return null;
        }
        for (Map.Entry<QuestState, String> entry : questPages.entrySet()) {
            if (pageId.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ authoring metadata

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy == null || createdBy.isBlank() ? null : createdBy;
    }

    public String getEditedBy() {
        return editedBy;
    }

    public long getEditedAt() {
        return editedAt;
    }

    public void setEdited(String editedBy, long editedAt) {
        this.editedBy = editedBy == null || editedBy.isBlank() ? null : editedBy;
        this.editedAt = Math.max(0L, editedAt);
    }

    public void touch(String editor) {
        setEdited(editor, System.currentTimeMillis());
        if (createdBy == null) {
            createdBy = editor;
        }
    }

    // ------------------------------------------------------------------ pages

    public Map<String, DialoguePage> pages() {
        return pages;
    }

    public DialoguePage page(String pageId) {
        if (pageId == null) {
            return null;
        }
        return pages.get(pageId.toLowerCase(Locale.ROOT));
    }

    public DialoguePage ensurePage(String pageId) {
        String key = sanitizePageId(pageId);
        return pages.computeIfAbsent(key, DialoguePage::empty);
    }

    /** Pages in display order: first page, then the rest as stored. */
    public List<DialoguePage> orderedPages() {
        List<DialoguePage> out = new ArrayList<>(pages.size());
        DialoguePage first = page(getStartPage());
        if (first != null) {
            out.add(first);
        }
        for (DialoguePage page : pages.values()) {
            if (page != first) {
                out.add(page);
            }
        }
        return out;
    }

    /** Creates a new empty page from a human title; returns it (id derived + made unique). */
    public DialoguePage createPage(String title) {
        String key = uniquePageId(title, null);
        DialoguePage page = new DialoguePage(key);
        pages.put(key, page);
        return page;
    }

    /**
     * Renames a page (its id) and rewires every reply, the first page and stage pages that point at it.
     *
     * @return the new id, or null when nothing changed
     */
    public String renamePage(String pageId, String newTitle) {
        DialoguePage old = page(pageId);
        if (old == null) {
            return null;
        }
        String newId = uniquePageId(newTitle, old.id());
        if (newId.equals(old.id())) {
            return null;
        }
        DialoguePage renamed = new DialoguePage(newId);
        renamed.lines().addAll(old.lines());
        for (DialogueChoice choice : old.choices()) {
            renamed.choices().add(choice.copy());
        }
        Map<String, DialoguePage> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, DialoguePage> entry : pages.entrySet()) {
            if (entry.getKey().equals(old.id())) {
                rebuilt.put(newId, renamed);
            } else {
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        pages.clear();
        pages.putAll(rebuilt);
        for (DialoguePage page : pages.values()) {
            for (DialogueChoice choice : page.choices()) {
                if (choice.action() == DialogueAction.PAGE && old.id().equalsIgnoreCase(choice.target())) {
                    choice.setTarget(newId);
                }
            }
        }
        if (old.id().equalsIgnoreCase(startPage)) {
            startPage = newId;
        }
        for (Map.Entry<QuestState, String> entry : questPages.entrySet()) {
            if (old.id().equalsIgnoreCase(entry.getValue())) {
                entry.setValue(newId);
            }
        }
        return newId;
    }

    /**
     * Deletes a page. Replies that opened it now end the conversation; stage pages fall back to the first page.
     *
     * @return how many replies had to be rewired, or -1 if the page can't be deleted (last page / missing)
     */
    public int deletePage(String pageId) {
        DialoguePage target = page(pageId);
        if (target == null || pages.size() <= 1) {
            return -1;
        }
        boolean wasStart = isStartPage(target.id());
        pages.remove(target.id());
        int rewired = 0;
        for (DialoguePage page : pages.values()) {
            for (DialogueChoice choice : page.choices()) {
                if (choice.action() == DialogueAction.PAGE && target.id().equalsIgnoreCase(choice.target())) {
                    choice.setAction(DialogueAction.CLOSE);
                    choice.setTarget("");
                    rewired++;
                }
            }
        }
        questPages.values().removeIf(value -> target.id().equalsIgnoreCase(value));
        if (wasStart) {
            startPage = pages.containsKey(START_PAGE) ? START_PAGE : pages.keySet().iterator().next();
        }
        return rewired;
    }

    /** Every reply (on any page) that opens {@code pageId}. */
    public List<Link> linksTo(String pageId) {
        List<Link> out = new ArrayList<>();
        if (pageId == null) {
            return out;
        }
        for (DialoguePage page : pages.values()) {
            for (int i = 0; i < page.choices().size(); i++) {
                DialogueChoice choice = page.choices().get(i);
                if (choice.action() == DialogueAction.PAGE && pageId.equalsIgnoreCase(choice.target())) {
                    out.add(new Link(page.id(), i, choice));
                }
            }
        }
        return out;
    }

    /** Every reply (on any page) that uses a quest action. */
    public List<Link> questReplies() {
        List<Link> out = new ArrayList<>();
        for (DialoguePage page : orderedPages()) {
            for (int i = 0; i < page.choices().size(); i++) {
                DialogueChoice choice = page.choices().get(i);
                if (choice.action().isQuest()) {
                    out.add(new Link(page.id(), i, choice));
                }
            }
        }
        return out;
    }

    /** Quest a reply acts on: its own target, else the NPC's main quest. */
    public String questFor(DialogueChoice choice) {
        if (choice != null && !choice.target().isBlank()) {
            return choice.target();
        }
        return linkedQuestId;
    }

    public int totalLines() {
        int total = 0;
        for (DialoguePage page : pages.values()) {
            total += page.lines().size();
        }
        return total;
    }

    public int totalChoices() {
        int total = 0;
        for (DialoguePage page : pages.values()) {
            total += page.choices().size();
        }
        return total;
    }

    /** First spoken line of the first page (for previews), or null. */
    public String firstLine() {
        DialoguePage first = page(getStartPage());
        if (first == null) {
            return null;
        }
        for (String line : first.lines()) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private String uniquePageId(String title, String keep) {
        String base = slug(title);
        if (base == null) {
            base = "page";
        }
        if (base.equals(keep) || !pages.containsKey(base)) {
            return base;
        }
        for (int i = 2; i < 100; i++) {
            String suffix = "_" + i;
            String trimmed = base.length() + suffix.length() > 24 ? base.substring(0, 24 - suffix.length()) : base;
            String candidate = trimmed + suffix;
            if (candidate.equals(keep) || !pages.containsKey(candidate)) {
                return candidate;
            }
        }
        return base + "_" + Long.toHexString(System.nanoTime() & 0xFFFF);
    }

    // ------------------------------------------------------------------ copies

    /** Full deep copy with the same id — used for undo. */
    public CustomNpc snapshot() {
        CustomNpc copy = copy(id, name);
        copy.createdBy = this.createdBy;
        copy.editedBy = this.editedBy;
        copy.editedAt = this.editedAt;
        return copy;
    }

    public CustomNpc copy(String newId, String newName) {
        CustomNpc copy = new CustomNpc(newId, newName);
        copy.subtitle = this.subtitle;
        copy.skinUsername = this.skinUsername;
        copy.slim = this.slim;
        copy.preset = this.preset;
        copy.world = this.world;
        copy.x = this.x;
        copy.y = this.y;
        copy.z = this.z;
        copy.yaw = this.yaw;
        copy.pitch = this.pitch;
        copy.linkedQuestId = this.linkedQuestId;
        copy.startPage = this.startPage;
        copy.pages.clear();
        for (Map.Entry<String, DialoguePage> entry : pages.entrySet()) {
            copy.pages.put(entry.getKey(), entry.getValue().copy());
        }
        copy.questPages.clear();
        copy.questPages.putAll(this.questPages);
        return copy;
    }

    // ------------------------------------------------------------------ ids & titles

    public static String sanitizePageId(String raw) {
        String slug = slug(raw);
        return slug == null ? START_PAGE : slug;
    }

    private static String slug(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String slug = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            return null;
        }
        return slug.length() > 24 ? slug.substring(0, 24) : slug;
    }

    /** "in_progress" → "In progress". Page ids are the only page names, so this is their display title. */
    public static String pageTitle(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return "Page";
        }
        String spaced = pageId.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return "Page";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** A reply somewhere in the conversation. */
    public record Link(String pageId, int index, DialogueChoice choice) {
    }

    public static final class DialoguePage {
        private final String id;
        private final List<String> lines = new ArrayList<>();
        private final List<DialogueChoice> choices = new ArrayList<>();

        public DialoguePage(String id) {
            this.id = id;
        }

        public static DialoguePage starter() {
            DialoguePage page = new DialoguePage(START_PAGE);
            page.lines.add("Hello there.");
            page.choices.add(new DialogueChoice("Goodbye", DialogueAction.CLOSE, ""));
            return page;
        }

        public static DialoguePage empty(String id) {
            return new DialoguePage(id);
        }

        public String id() {
            return id;
        }

        public String title() {
            return pageTitle(id);
        }

        public List<String> lines() {
            return lines;
        }

        public List<DialogueChoice> choices() {
            return choices;
        }

        public boolean isEmpty() {
            return spokenLines().isEmpty() && choices.isEmpty();
        }

        /** Non-blank lines, in order — what the runtime actually says. */
        public List<String> spokenLines() {
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    out.add(line);
                }
            }
            return out;
        }

        public DialoguePage copy() {
            DialoguePage copy = new DialoguePage(id);
            copy.lines.addAll(lines);
            for (DialogueChoice choice : choices) {
                copy.choices.add(choice.copy());
            }
            return copy;
        }

        /** Swaps an entry with its neighbour ({@code delta} = -1 earlier, +1 later). */
        public static <T> boolean move(List<T> list, int index, int delta) {
            int target = index + delta;
            if (index < 0 || index >= list.size() || target < 0 || target >= list.size()) {
                return false;
            }
            Collections.swap(list, index, target);
            return true;
        }
    }

    public static final class DialogueChoice {
        private String text;
        private DialogueAction action;
        private String target;

        public DialogueChoice(String text, DialogueAction action, String target) {
            this.text = text == null || text.isBlank() ? "…" : text;
            this.action = action == null ? DialogueAction.CLOSE : action;
            this.target = target == null ? "" : target;
        }

        public String text() {
            return text;
        }

        public void setText(String text) {
            this.text = text == null || text.isBlank() ? "…" : text;
        }

        public DialogueAction action() {
            return action == null ? DialogueAction.CLOSE : action;
        }

        public void setAction(DialogueAction action) {
            this.action = action == null ? DialogueAction.CLOSE : action;
        }

        public String target() {
            return target == null ? "" : target;
        }

        public void setTarget(String target) {
            this.target = target == null ? "" : target;
        }

        public DialogueChoice copy() {
            return new DialogueChoice(text, action, target);
        }
    }
}
