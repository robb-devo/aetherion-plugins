package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * One pending chat answer — the only place the studio asks people to type (names, lines, commands).
 * <p>
 * Single mode: the first accepted message finishes the input. Multi-line mode ("dictation"): every message
 * is accepted as its own entry until the player types {@code done}.
 */
public final class TextInput {

    /** Validates + applies one message. Return an error to show (input stays open) or null when accepted. */
    @FunctionalInterface
    public interface Handler {
        String accept(Player player, String text);
    }

    private final String title;
    private final List<String> hints;
    private final String current;
    private final int maxLength;
    private final boolean multiLine;
    private final boolean acceptsCommand;
    private final Handler handler;
    private final Consumer<Player> onDone;
    private final Consumer<Player> onCancel;
    private int accepted;
    private long lastActivity = System.currentTimeMillis();

    private TextInput(Builder builder) {
        this.title = builder.title;
        this.hints = List.copyOf(builder.hints);
        this.current = builder.current;
        this.maxLength = builder.maxLength;
        this.multiLine = builder.multiLine;
        this.acceptsCommand = builder.acceptsCommand;
        this.handler = builder.handler;
        this.onDone = builder.onDone;
        this.onCancel = builder.onCancel != null ? builder.onCancel : builder.onDone;
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public String title() {
        return title;
    }

    public List<String> hints() {
        return hints;
    }

    /** Existing value — offered as a click-to-insert button so nobody retypes a long line. */
    public String current() {
        return current;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean multiLine() {
        return multiLine;
    }

    /** True for command answers: a typed "/spawn" is captured as the answer instead of being run. */
    public boolean acceptsCommand() {
        return acceptsCommand;
    }

    public int accepted() {
        return accepted;
    }

    public long lastActivity() {
        return lastActivity;
    }

    Handler handler() {
        return handler;
    }

    void markAccepted() {
        accepted++;
        lastActivity = System.currentTimeMillis();
    }

    void touch() {
        lastActivity = System.currentTimeMillis();
    }

    void done(Player player) {
        if (onDone != null) {
            onDone.accept(player);
        }
    }

    void cancel(Player player) {
        if (onCancel != null) {
            onCancel.accept(player);
        }
    }

    public static boolean isCancel(String message) {
        String value = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return value.equals("cancel") || value.equals("abbrechen") || value.equals("abort");
    }

    public static boolean isDone(String message) {
        String value = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return value.equals("done") || value.equals("fertig") || value.equals("finish");
    }

    public static final class Builder {
        private final String title;
        private final List<String> hints = new ArrayList<>();
        private String current;
        private int maxLength = 200;
        private boolean multiLine;
        private boolean acceptsCommand;
        private Handler handler = (player, text) -> null;
        private Consumer<Player> onDone;
        private Consumer<Player> onCancel;

        private Builder(String title) {
            this.title = title == null ? "Input" : title;
        }

        public Builder hint(String line) {
            if (line != null) {
                hints.add(line);
            }
            return this;
        }

        public Builder current(String value) {
            this.current = value == null || value.isBlank() ? null : value;
            return this;
        }

        public Builder max(int maxLength) {
            this.maxLength = Math.max(1, maxLength);
            return this;
        }

        public Builder multiLine() {
            this.multiLine = true;
            return this;
        }

        public Builder acceptsCommand() {
            this.acceptsCommand = true;
            return this;
        }

        public Builder handler(Handler handler) {
            this.handler = handler == null ? (player, text) -> null : handler;
            return this;
        }

        /** Runs after the input finishes (usually: reopen the screen the player came from). */
        public Builder then(Consumer<Player> onDone) {
            this.onDone = onDone;
            return this;
        }

        /** Runs on cancel / timeout; defaults to {@link #then}. */
        public Builder onCancel(Consumer<Player> onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        public TextInput build() {
            return new TextInput(this);
        }
    }
}
