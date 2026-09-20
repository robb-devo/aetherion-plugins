package de.aetherion.core.npc;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * FancyNpcs {@code setSkin(username)} goes through UUIDFetcher
 * ({@code api.minecraftservices.com/users/profiles/minecraft/...}). Invalid names
 * such as {@code MHF_Oak} 404 forever on a ~2s retry and hitch the client even
 * when server TPS is 20.
 *
 * <p>Always prefer a texture URL, UUID, hash, or local PNG path. Never feed
 * Mojang usernames (especially {@code MHF_*}) into {@code setSkin}.
 */
public final class FancyNpcSkins {

    /**
     * Same texture URL Hub already uses for the Aetherion NPC — known-good,
     * no UUIDFetcher.
     */
    public static final String DEFAULT_TEXTURE_URL =
            "https://textures.minecraft.net/texture/f163d98f20f7b8dec6ecc7a314f82e33f4d4105919dfd287080627979a957933";

    public static final String DEFAULT_FILE_NAME = "mystic.png";

    private static final Pattern UUID_32 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern UUID_DASHED = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HASH_64 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private FancyNpcSkins() {
    }

    /**
     * Turn a config value into something FancyNpcs will not send to UUIDFetcher.
     *
     * @param localPng optional extracted PNG; used when {@code requested} is a
     *                 bare {@code *.png} name or when the username is blocked
     */
    public static String resolve(String requested, File localPng) {
        String trimmed = requested == null ? "" : requested.trim();
        if (isBarePngName(trimmed)) {
            if (localPng != null && localPng.isFile()) {
                return localPng.getAbsolutePath();
            }
            return DEFAULT_TEXTURE_URL;
        }
        if (isSafeIdentifier(trimmed) && !isBlocked(trimmed)) {
            if (HASH_64.matcher(trimmed).matches()) {
                return "https://textures.minecraft.net/texture/" + trimmed.toLowerCase(Locale.ROOT);
            }
            return trimmed;
        }
        if (localPng != null && localPng.isFile()) {
            return localPng.getAbsolutePath();
        }
        return DEFAULT_TEXTURE_URL;
    }

    /** Live YAML still on {@code MHF_Oak} / a Mojang username should be rewritten. */
    public static boolean needsRewrite(String requested) {
        String trimmed = requested == null ? "" : requested.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (isBarePngName(trimmed)) {
            return false;
        }
        return !isSafeIdentifier(trimmed) || isBlocked(trimmed);
    }

    public static boolean isBlocked(String raw) {
        String key = normalize(raw);
        if (key.isEmpty()) {
            return false;
        }
        if (key.startsWith("mhf_")) {
            return true;
        }
        return FAILED.contains(key);
    }

    public static void rememberFailure(String raw) {
        String key = normalize(raw);
        if (key.isEmpty() || isSafeIdentifier(raw == null ? "" : raw.trim())) {
            return;
        }
        FAILED.add(key);
    }

    /**
     * {@code true} when FancyNpcs would treat this as a Mojang username and hit
     * UUIDFetcher.
     */
    public static boolean isUsernameLookup(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || isSafeIdentifier(trimmed) || isBarePngName(trimmed)) {
            return false;
        }
        return true;
    }

    public static boolean isSafeIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return true;
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0) {
            return true;
        }
        if (UUID_DASHED.matcher(trimmed).matches()) {
            return true;
        }
        String compact = trimmed.replace("-", "");
        return UUID_32.matcher(compact).matches() || HASH_64.matcher(compact).matches();
    }

    public static boolean isBarePngName(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    public static String identifier(Object npcData) {
        if (npcData == null) {
            return "";
        }
        try {
            Object skin = npcData.getClass().getMethod("getSkinData").invoke(npcData);
            if (skin == null) {
                return "";
            }
            Object id = skin.getClass().getMethod("getIdentifier").invoke(skin);
            return id == null ? "" : String.valueOf(id);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static boolean same(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isEmpty() && b.isEmpty()) {
            return true;
        }
        return a.equalsIgnoreCase(b);
    }

    /**
     * Apply a non-username skin and turn off {@code mirrorSkin}. Returns
     * {@code true} when the stored identifier changed (caller should persist).
     */
    public static boolean apply(Object npcData, String identifier) {
        if (npcData == null) {
            return false;
        }
        FancyNpcFacade.invokeQuiet(npcData, "setMirrorSkin", boolean.class, false);
        String id = identifier == null || identifier.isBlank() ? DEFAULT_TEXTURE_URL : identifier.trim();
        if (isUsernameLookup(id) || isBlocked(id)) {
            rememberFailure(id);
            id = DEFAULT_TEXTURE_URL;
        }
        String before = identifier(npcData);
        // Swap the identifier immediately so a stuck MHF_* name cannot keep UUIDFetcher
        // retrying while the URL/file texture loads.
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object variant = Enum.valueOf((Class) variantClass.asSubclass(Enum.class), "AUTO");
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skinData = skinDataClass
                    .getConstructor(String.class, variantClass)
                    .newInstance(id, variant);
            npcData.getClass().getMethod("setSkinData", skinDataClass).invoke(npcData, skinData);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            npcData.getClass().getMethod("setSkin", String.class).invoke(npcData, id);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return !same(before, identifier(npcData));
        }
        String after = identifier(npcData);
        if (isUsernameLookup(after) || isBlocked(after)) {
            rememberFailure(after);
            try {
                npcData.getClass().getMethod("setSkin", String.class).invoke(npcData, DEFAULT_TEXTURE_URL);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            return true;
        }
        return !same(before, id);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
