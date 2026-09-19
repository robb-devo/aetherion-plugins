package de.aetherion.quests.editor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moderator-created FancyNPC. Stored in {@code editor-npcs.yml}, never in story {@code npcs.yml}.
 */
public final class CustomNpc {

    public static final String START_PAGE = "greeting";

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

    public String getSubtitle() {
        return subtitle == null || subtitle.isBlank() ? "Guide" : subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getSkinUsername() {
        return skinUsername == null || skinUsername.isBlank() ? preset.skinUsername() : skinUsername;
    }

    public void setSkinUsername(String skinUsername) {
        this.skinUsername = skinUsername;
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
        this.startPage = startPage;
    }

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

    public void removePage(String pageId) {
        if (pageId == null || START_PAGE.equalsIgnoreCase(pageId) && pages.size() <= 1) {
            return;
        }
        pages.remove(pageId.toLowerCase(Locale.ROOT));
        if (pageId.equalsIgnoreCase(startPage)) {
            startPage = getStartPage();
        }
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
        return copy;
    }

    public static String sanitizePageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return START_PAGE;
        }
        String slug = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_|_$", "");
        if (slug.isBlank()) {
            return START_PAGE;
        }
        return slug.length() > 24 ? slug.substring(0, 24) : slug;
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
            DialoguePage page = new DialoguePage(id);
            page.lines.add("…");
            return page;
        }

        public String id() {
            return id;
        }

        public List<String> lines() {
            return lines;
        }

        public List<DialogueChoice> choices() {
            return choices;
        }

        public DialoguePage copy() {
            DialoguePage copy = new DialoguePage(id);
            copy.lines.addAll(lines);
            for (DialogueChoice choice : choices) {
                copy.choices.add(choice.copy());
            }
            return copy;
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
