package de.aetherion.core.npc;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * FancyNpcs {@code setSkin(username)} goes through UUIDFetcher
 * ({@code api.minecraftservices.com/users/profiles/minecraft/...}). Invalid names
 * 404 forever on a ~2s retry and hitch the client even when server TPS is 20.
 *
 * <p>Never feed Mojang usernames into {@code setSkin}. Apply a complete
 * {@code SkinData} (texture URL identifier + value + signature) so FancyNpcs
 * persists a filled skin block instead of an empty one that keeps retrying.
 */
public final class FancyNpcSkins {

    /**
     * Texture URL used as the SkinData identifier (not a player name).
     * Matches {@link #LOCAL_TEXTURE_VALUE}.
     */
    public static final String DEFAULT_TEXTURE_URL =
            "https://textures.minecraft.net/texture/21165f4c9a43b57747d56efa815eaa2a41315977951e03656518904766a6ef218";

    public static final String DEFAULT_FILE_NAME = "mystic.png";

    /** Signed textures payload — fills FancyNpcs yaml so UUIDFetcher is never involved. */
    private static final String LOCAL_TEXTURE_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc4OTkwODI1Mjk5MCwKICAicHJvZmlsZUlkIiA6ICI3NWM4NjNhZWJiOTI0ODZkOTExYzUzMDMwYzU1MmJlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJQZWFybGVzY2VudE1vb24iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjExNjVmNGM5YTQzYjU3NzQ3ZDU2ZWZhODE1ZWFhMmE0MTM1OTc3OTUxZTAzNjU2NTE4OTA0NzY2YTZlZjIxOCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIzNDBjMGUwM2RkMjRhMTFiMTVhOGIzM2MyYTdlOWUzMmFiYjIwNTFiMjQ4MWQwYmE3ZGVmZDYzNWNhN2E5MzMiCiAgICB9CiAgfQp9";

    private static final String LOCAL_TEXTURE_SIGNATURE =
            "xWPY1gibhcMAb5K2yOajqUnRn6gx5F2BVCBz9zztGKtuiD0guU71BeyEwll1NYKlZ5psJ8r+Rajmqw2qclJXraThN915JxfooJecHdn9qG+KBnfscZ2SIn9deMkFxzPP42et8hp/octHKJMrnYWivU2tY9Pi3DpBCuvRAhKLclDv4xk0P5x+C4sAjl+19/RH+EZrhXVGp7UwTPKkcg9abFb1fS0aYvS9mvs1vMHfgYeIHPLmxEcRwsyrgfob4PQ4CSkDJnFOBGWNqPrfQOI+Ov/lMcL/sm9ABEnKs6qvk6Ty8B4wzwp0x0G/TYJCY4KAKBLdinPbpAsmkFsnwAxgTfk/BmD7GJ1YQKuG+d/DM/6wJR9A/NanRJQ3e+u+5BJIPjfmuEOSz+Zc5llHbGMAIFKnEbNf4XJmvlyj/ppGp/mQXcnQdnDOsjNVykZ6mUSVWu+luc63O084s1FGKs4tYRLzFG3DbW5bABYSsyUlnDrufTozI18ysLTR8Kl6/6rzQ80edQzjMYeXYMzcyy/esv0IHBBAgktAJuD3x0xdlkrPpaJ96Mu2+2HeSd3G2vCYtsZ9B/kBAA4GleT9jMG7Zrqq76QHTFugrr9buxaes0PCWXc579yfO3+M0BpTYe13Z6fqsRxUGyjTjT070FPv8mWx2XjWv/HQQie0pSFmIWw=";

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

    /** Live YAML still on a Mojang username should be rewritten to the texture URL. */
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

    public static boolean hasTexture(Object npcData) {
        if (npcData == null) {
            return false;
        }
        try {
            Object skin = npcData.getClass().getMethod("getSkinData").invoke(npcData);
            if (skin == null) {
                return false;
            }
            Object has = skin.getClass().getMethod("hasTexture").invoke(skin);
            return Boolean.TRUE.equals(has);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * Complete non-username skin already on the NPC — do not touch it (and do
     * not call {@code setSkin}, which queues UUIDFetcher / empties the yaml).
     */
    public static boolean hasCompleteLocalTextures(Object npcData) {
        if (!hasTexture(npcData)) {
            return false;
        }
        String have = identifier(npcData);
        return !isUsernameLookup(have) && !isBlocked(have);
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
     * Apply baked signed textures and turn off {@code mirrorSkin}.
     * Never calls {@code setSkin(String)} — that path is UUIDFetcher.
     * Returns {@code true} when the caller should persist NPCs.
     */
    public static boolean apply(Object npcData, String ignored) {
        return applyLocal(npcData);
    }

    public static boolean applyLocal(Object npcData) {
        if (npcData == null) {
            return false;
        }
        FancyNpcFacade.invokeQuiet(npcData, "setMirrorSkin", boolean.class, false);
        if (hasCompleteLocalTextures(npcData)) {
            return false;
        }
        try {
            Class<?> variantClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object variant;
            try {
                variant = Enum.valueOf((Class) variantClass.asSubclass(Enum.class), "SLIM");
            } catch (IllegalArgumentException missing) {
                variant = Enum.valueOf((Class) variantClass.asSubclass(Enum.class), "AUTO");
            }
            Class<?> skinDataClass = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skinData = skinDataClass
                    .getConstructor(String.class, variantClass, String.class, String.class)
                    .newInstance(
                            DEFAULT_TEXTURE_URL,
                            variant,
                            LOCAL_TEXTURE_VALUE,
                            LOCAL_TEXTURE_SIGNATURE
                    );
            npcData.getClass().getMethod("setSkinData", skinDataClass).invoke(npcData, skinData);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
