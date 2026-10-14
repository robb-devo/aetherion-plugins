package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.render.model.ModelBuilder;
import net.aetherion.samuraiboss.render.model.ModelDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Das Modell von Sakuragi Jinsai – komplett aus Display-Teilen gebaut.
 *
 * <p>Koordinaten in Pixeln (1/16 Block), Ruhepose: Füße auf y=0, Blick nach +Z,
 * rechte Körperseite = -X (Schwerthand), linke Seite = +X (Schwertscheide).
 * Das ganze Modell wird später mit {@link #BASE_SCALE} vergrößert (≈ 2,6 Blöcke hoch).</p>
 *
 * <p>Aufbau: Waraji/Tabi, Suneate (Schienbeinschutz), weite Hakama, vier Kusazuri
 * (Hüftplatten), Dō (Brustpanzer) mit roter Schnürung, Ō-Sode (Schulterplatten), Kote,
 * Kabuto mit Kuwagata-Hörnern und Kirschblüten-Maedate, rote Menpō-Maske, langer weißer
 * Bart und Haare, Katana „Hanafubuki" mit Saya an der linken Hüfte und ein Sashimono
 * (Rückenbanner) mit dem Zeichen 桜.</p>
 */
public final class SamuraiModel {

    /** Skalierung des Modells, damit es ca. 2,6 Blöcke hoch ist. */
    public static final float BASE_SCALE = 1.2f;

    // ---- Tags (zum Ein-/Ausblenden und für Skins) ----
    public static final String T_KATANA_HAND = "katana_hand";
    public static final String T_KATANA_SHEATHED = "katana_sheathed";
    public static final String T_BLADE = "blade";
    public static final String T_HELMET = "helmet";
    public static final String T_BANNER = "sashimono";
    public static final String T_PLATE = "armor_plate";
    public static final String T_BODY = "body";
    public static final String T_HAIR = "hair";
    public static final String T_EYES = "eyes";
    public static final String T_SAYA = "saya";
    public static final String T_CLOTH = "cloth";
    public static final String T_MASK = "mask";
    /** Füße – Yūrei (japanische Geister) haben traditionell keine Füße. */
    public static final String T_FEET = "feet";

    private static ModelDef cached;

    private SamuraiModel() {
    }

    public static synchronized ModelDef def() {
        if (cached == null) cached = build();
        return cached;
    }

    private static ModelDef build() {
        ModelBuilder b = new ModelBuilder("sakuragi_jinsai");

        // ============================================================ Skelett
        b.bone("root", null, 0, 0, 0);
        b.bone("hips", "root", 0, 15f, 0);
        b.bone("torso", "hips", 0, 16.5f, 0);
        b.bone("neck", "torso", 0, 24.6f, 0);
        b.bone("head", "neck", 0, 26.4f, 0);
        b.bone("helmet", "head", 0, 31.2f, 0);
        b.bone("horn_l", "helmet", 0.9f, 33.4f, 3.0f).restRot(-8, 0, -22);
        b.bone("horn_r", "helmet", -0.9f, 33.4f, 3.0f).restRot(-8, 0, 22);
        b.bone("beard1", "head", 0, 27.2f, 2.9f);
        b.bone("beard2", "beard1", 0, 24.2f, 3.6f);
        b.bone("beard3", "beard2", 0, 21.6f, 3.9f);
        b.bone("hair1", "head", 0, 30.4f, -2.4f);
        b.bone("hair2", "hair1", 0, 27f, -3.3f);
        b.bone("hair3", "hair2", 0, 23.2f, -3.7f);
        b.bone("arm_r", "torso", -5.0f, 24.3f, 0);
        b.bone("sode_r", "arm_r", -6.0f, 25.4f, 0).restRot(0, 0, -11);
        b.bone("forearm_r", "arm_r", -5.1f, 18.1f, 0);
        b.bone("hand_r", "forearm_r", -5.1f, 12.4f, 0);
        b.bone("katana", "hand_r", -5.1f, 10.9f, 0);
        b.bone("arm_l", "torso", 5.0f, 24.3f, 0);
        b.bone("sode_l", "arm_l", 6.0f, 25.4f, 0).restRot(0, 0, 11);
        b.bone("forearm_l", "arm_l", 5.1f, 18.1f, 0);
        b.bone("hand_l", "forearm_l", 5.1f, 12.4f, 0);
        b.bone("sashimono", "torso", 0, 18.5f, -3.5f);
        b.bone("flag", "sashimono", 0.3f, 39.4f, -3.6f);
        b.bone("kusazuri_f", "hips", 0, 16.3f, 2.9f).restRot(-9, 0, 0);
        b.bone("kusazuri_b", "hips", 0, 16.3f, -2.9f).restRot(9, 0, 0);
        b.bone("kusazuri_l", "hips", 4.6f, 16.3f, 0).restRot(0, 0, 11);
        b.bone("kusazuri_r", "hips", -4.6f, 16.3f, 0).restRot(0, 0, -11);
        b.bone("saya", "hips", 4.5f, 15.4f, 2.6f).restRot(-17, -9, 0);
        b.bone("thigh_r", "hips", -2.2f, 15f, 0);
        b.bone("shin_r", "thigh_r", -2.2f, 8f, 0);
        b.bone("foot_r", "shin_r", -2.2f, 1.5f, 0);
        b.bone("thigh_l", "hips", 2.2f, 15f, 0);
        b.bone("shin_l", "thigh_l", 2.2f, 8f, 0);
        b.bone("foot_l", "shin_l", 2.2f, 1.5f, 0);

        // ============================================================ Beine
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "_r" : "_l";
            float o = side; // Spiegel-Faktor für X
            // Tabi + Waraji
            box(b, "foot" + s, "foot" + s, "robe", o * 0.8f, 0f, -1.7f, o * 3.6f, 1.6f, 3.3f).lod(0).tag(T_BODY, T_CLOTH, T_FEET);
            // Suneate (Schienbeinschutz) – schwarzer Lack mit goldener Mittelschiene
            box(b, "shin" + s, "shin" + s, "armor", o * 0.6f, 1.3f, -1.9f, o * 3.8f, 8.3f, 2.1f).lod(0).tag(T_PLATE);
            box(b, "shin" + s, "shin_gold" + s, "gold", o * 2.0f, 2.2f, 2.0f, o * 2.45f, 7.7f, 2.35f).lod(1).tag(T_PLATE);
            // Weite Hakama
            box(b, "thigh" + s, "thigh" + s, "hakama", o * 0.0f, 7.4f, -2.7f, o * 4.6f, 15.6f, 2.7f).lod(0).tag(T_BODY, T_CLOTH);
        }
        // Becken / Hakama-Bund
        b.block("hips", "pelvis", "hakama", -4.6f, 13.6f, -2.75f, 4.6f, 17.0f, 2.75f).lod(0).tag(T_BODY, T_CLOTH);

        // ============================================================ Kusazuri (Hüftplatten)
        plate(b, "kusazuri_f", "kz_f", -3.9f, 10.0f, 2.75f, 3.9f, 16.3f, 3.55f, 'z');
        plate(b, "kusazuri_b", "kz_b", -3.9f, 10.0f, -3.55f, 3.9f, 16.3f, -2.75f, 'z');
        plate(b, "kusazuri_l", "kz_l", 4.6f, 10.4f, -2.6f, 5.4f, 16.3f, 2.6f, 'x');
        plate(b, "kusazuri_r", "kz_r", -5.4f, 10.4f, -2.6f, -4.6f, 16.3f, 2.6f, 'x');

        // ============================================================ Rumpf (Dō)
        b.block("torso", "do", "armor", -4.4f, 16.4f, -2.8f, 4.4f, 24.6f, 2.9f).lod(0).tag(T_PLATE);
        // Kimono unter dem Panzer – sichtbar, wenn die Rüstung in Phase 3 zerspringt
        b.block("torso", "kimono", "robe", -4.0f, 16.6f, -2.45f, 4.0f, 24.4f, 2.55f).lod(0).tag(T_BODY, T_CLOTH);
        b.block("torso", "do_chest", "armor", -3.9f, 19.6f, 2.8f, 3.9f, 24.3f, 3.35f).lod(1).tag(T_PLATE);
        b.block("torso", "do_lace1", "lace", -4.5f, 17.5f, -2.9f, 4.5f, 18.2f, 3.0f).lod(1).tag(T_PLATE);
        b.block("torso", "do_lace2", "lace", -4.5f, 19.0f, -2.9f, 4.5f, 19.6f, 3.0f).lod(1).tag(T_PLATE);
        b.block("torso", "do_trim", "gold", -3.95f, 23.75f, 3.3f, 3.95f, 24.25f, 3.5f).lod(2).tag(T_PLATE);
        b.block("torso", "obi", "obi", -4.7f, 15.9f, -3.0f, 4.7f, 17.1f, 3.1f).lod(0).tag(T_BODY, T_CLOTH);
        b.block("torso", "obi_knot", "obi", -1.0f, 15.3f, 3.0f, 1.0f, 17.5f, 3.6f).lod(2).tag(T_BODY, T_CLOTH);
        // Kirschblüten-Mon auf der Brust
        b.blockC("torso", "mon", "sakura", 0f, 21.8f, 3.45f, 1.7f, 1.7f, 0.3f).rotC(0, 0, 45).lod(2).tag(T_PLATE);
        b.blockC("torso", "mon_core", "gold", 0f, 21.8f, 3.6f, 0.6f, 0.6f, 0.2f).rotC(0, 0, 45).lod(2).tag(T_PLATE);
        // weißer Kragen des Untergewands
        b.block("torso", "collar", "robe", -1.9f, 24.0f, 1.2f, 1.9f, 25.4f, 3.0f).lod(1).tag(T_BODY, T_CLOTH);
        // Agemaki – roter Zierknoten auf dem Rücken
        b.block("torso", "agemaki", "lace", -1.4f, 18.0f, -3.6f, 1.4f, 20.1f, -2.75f).lod(1).tag(T_PLATE);

        // ============================================================ Hals & Kopf
        b.block("neck", "neck", "skin", -1.3f, 24.4f, -1.3f, 1.3f, 26.8f, 1.3f).lod(1).tag(T_BODY);
        b.block("head", "face", "skin", -2.7f, 26.4f, -2.6f, 2.7f, 31.8f, 2.6f).lod(0).tag(T_BODY);
        b.block("head", "menpo", "mask", -2.9f, 26.1f, 0.9f, 2.9f, 29.0f, 3.1f).lod(0).tag(T_MASK);
        b.block("head", "nose", "mask", -0.6f, 28.2f, 3.0f, 0.6f, 29.8f, 3.75f).lod(1).tag(T_MASK);
        b.block("head", "mustache", "hair", -2.0f, 27.45f, 3.05f, 2.0f, 28.1f, 3.55f).rot(0, 27.8f, 3.3f, 0, 0, 0).lod(1).tag(T_HAIR);
        b.block("head", "eye_r", "eyes", -1.8f, 29.8f, 2.55f, -0.6f, 30.4f, 2.72f).lod(1).emissive().tag(T_EYES);
        b.block("head", "eye_l", "eyes", 0.6f, 29.8f, 2.55f, 1.8f, 30.4f, 2.72f).lod(1).emissive().tag(T_EYES);
        b.block("head", "brow_r", "hair", -2.2f, 30.55f, 2.4f, -0.45f, 31.05f, 2.8f).rot(-1.3f, 30.8f, 2.6f, 0, 0, -12).lod(2).tag(T_HAIR);
        b.block("head", "brow_l", "hair", 0.45f, 30.55f, 2.4f, 2.2f, 31.05f, 2.8f).rot(1.3f, 30.8f, 2.6f, 0, 0, 12).lod(2).tag(T_HAIR);
        // Oberes Haar + Chonmage (unter dem Helm verborgen, sichtbar wenn er fällt)
        b.block("head", "hair_top", "hair", -2.85f, 31.5f, -2.75f, 2.85f, 32.5f, 2.5f).lod(1).tag(T_HAIR);
        b.block("head", "topknot", "hair", -0.65f, 32.3f, -1.8f, 0.65f, 33.4f, 1.4f).lod(1).tag(T_HAIR);

        // Bart (drei Glieder, weht im Wind)
        b.block("beard1", "beard1", "hair", -2.0f, 24.0f, 2.4f, 2.0f, 27.3f, 4.0f).lod(0).tag(T_HAIR);
        b.block("beard2", "beard2", "hair", -1.5f, 21.4f, 3.35f, 1.5f, 24.3f, 4.5f).lod(0).tag(T_HAIR);
        b.block("beard3", "beard3", "hair", -0.95f, 19.0f, 3.5f, 0.95f, 21.7f, 4.35f).lod(1).tag(T_HAIR);
        // Langes Haar (fällt hinten aus dem Helm)
        b.block("hair1", "hair1", "hair", -2.3f, 26.6f, -3.9f, 2.3f, 30.6f, -2.4f).lod(0).tag(T_HAIR);
        b.block("hair2", "hair2", "hair", -1.8f, 23.0f, -4.1f, 1.8f, 27.1f, -2.9f).lod(0).tag(T_HAIR);
        b.block("hair3", "hair3", "hair", -1.35f, 19.4f, -4.3f, 1.35f, 23.3f, -3.2f).lod(1).tag(T_HAIR);

        // ============================================================ Kabuto (Helm)
        // Hachi (Helmglocke) mit gewölbter Krone
        b.block("helmet", "hachi", "armor2", -3.15f, 31.0f, -3.05f, 3.15f, 33.9f, 3.15f).lod(0).tag(T_HELMET);
        b.block("helmet", "hachi_top", "armor2", -2.45f, 33.8f, -2.35f, 2.45f, 34.7f, 2.45f).lod(1).tag(T_HELMET);
        b.block("helmet", "tehen", "gold", -0.55f, 34.6f, -0.5f, 0.55f, 35.1f, 0.6f).lod(2).tag(T_HELMET);
        b.block("helmet", "visor", "armor2", -3.25f, 31.0f, 2.6f, 3.25f, 31.55f, 4.5f).rot(0, 31.25f, 2.7f, 14, 0, 0).lod(0).tag(T_HELMET);
        // Fukigaeshi – die nach außen umgeschlagenen Seitenklappen
        b.block("helmet", "fuki_l", "armor2", 3.1f, 28.6f, 1.0f, 3.9f, 31.6f, 3.6f).rot(3.4f, 30f, 1.0f, 0, 40, 0).lod(0).tag(T_HELMET);
        b.block("helmet", "fuki_r", "armor2", -3.9f, 28.6f, 1.0f, -3.1f, 31.6f, 3.6f).rot(-3.4f, 30f, 1.0f, 0, -40, 0).lod(0).tag(T_HELMET);
        b.block("helmet", "fuki_l_gold", "gold", 3.85f, 28.9f, 1.3f, 4.0f, 31.3f, 3.3f).rot(3.4f, 30f, 1.0f, 0, 40, 0).lod(2).tag(T_HELMET);
        b.block("helmet", "fuki_r_gold", "gold", -4.0f, 28.9f, 1.3f, -3.85f, 31.3f, 3.3f).rot(-3.4f, 30f, 1.0f, 0, -40, 0).lod(2).tag(T_HELMET);
        // Shikoro – gestufter Nackenschutz, zwei Lagen, nach außen gewölbt
        b.block("helmet", "shikoro_b", "armor2", -3.7f, 28.6f, -4.0f, 3.7f, 31.3f, -3.1f).rot(0, 31.3f, -3.1f, 28, 0, 0).lod(0).tag(T_HELMET);
        b.block("helmet", "shikoro_b2", "armor2", -4.2f, 26.4f, -4.9f, 4.2f, 29.0f, -4.0f).rot(0, 31.3f, -3.1f, 28, 0, 0).lod(1).tag(T_HELMET);
        b.block("helmet", "shikoro_lace", "lace", -4.25f, 27.6f, -4.95f, 4.25f, 28.2f, -3.95f).rot(0, 31.3f, -3.1f, 28, 0, 0).lod(1).tag(T_HELMET);
        b.block("helmet", "shikoro_l", "armor2", 3.1f, 28.4f, -3.1f, 4.0f, 31.3f, 1.2f).rot(3.1f, 31.3f, 0, 0, 0, 24).lod(0).tag(T_HELMET);
        b.block("helmet", "shikoro_r", "armor2", -4.0f, 28.4f, -3.1f, -3.1f, 31.3f, 1.2f).rot(-3.1f, 31.3f, 0, 0, 0, -24).lod(0).tag(T_HELMET);
        // Kuwagata – goldene Hörner (je ein eigener Knochen mit Vor-Drehung)
        b.block("horn_l", "horn_l", "gold", 0.65f, 33.2f, 2.85f, 1.2f, 41.0f, 3.2f).lod(0).tag(T_HELMET);
        b.block("horn_r", "horn_r", "gold", -1.2f, 33.2f, 2.85f, -0.65f, 41.0f, 3.2f).lod(0).tag(T_HELMET);
        // Maedate – goldene Kirschblüte vorne am Helm
        b.blockC("helmet", "maedate", "gold", 0f, 32.8f, 3.25f, 2.1f, 2.1f, 0.3f).rotC(0, 0, 45).lod(1).tag(T_HELMET);
        b.blockC("helmet", "maedate_core", "sakura", 0f, 32.8f, 3.43f, 1.0f, 1.0f, 0.2f).rotC(0, 0, 45).lod(2).tag(T_HELMET);

        // ============================================================ Arme
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "_r" : "_l";
            float o = side;
            // Ärmel (Kote-Stoff)
            box(b, "arm" + s, "sleeve" + s, "sleeve", o * 3.5f, 17.8f, -1.75f, o * 6.75f, 24.9f, 1.85f).lod(0).tag(T_BODY, T_CLOTH);
            // Unterarm (gepanzerte Kote)
            box(b, "forearm" + s, "kote" + s, "kote", o * 3.7f, 12.3f, -1.5f, o * 6.5f, 18.3f, 1.6f).lod(0).tag(T_BODY);
            box(b, "forearm" + s, "kote_gold" + s, "gold", o * 6.45f, 13.4f, -0.7f, o * 6.7f, 17.0f, 0.7f).lod(2).tag(T_PLATE);
            // Hand (Lederhandschuh)
            box(b, "hand" + s, "hand" + s, "glove", o * 3.9f, 9.6f, -1.3f, o * 6.3f, 12.5f, 1.4f).lod(0).tag(T_BODY);
            // Ō-Sode (große Schulterplatte) – Teile ungedreht, Knochen hat Vor-Drehung
            box(b, "sode" + s, "sode" + s, "armor", o * 6.5f, 18.5f, -2.95f, o * 7.45f, 25.7f, 2.95f).lod(0).tag(T_PLATE);
            box(b, "sode" + s, "sode_lace1" + s, "lace", o * 6.45f, 20.4f, -3.0f, o * 7.5f, 21.1f, 3.0f).lod(1).tag(T_PLATE);
            box(b, "sode" + s, "sode_lace2" + s, "lace", o * 6.45f, 22.6f, -3.0f, o * 7.5f, 23.3f, 3.0f).lod(1).tag(T_PLATE);
            box(b, "sode" + s, "sode_gold" + s, "gold", o * 6.45f, 25.15f, -3.0f, o * 7.5f, 25.75f, 3.0f).lod(2).tag(T_PLATE);
        }

        // ============================================================ Katana „Hanafubuki" (in der Hand)
        // Achse entlang +Z durch die Hand (x=-5.1, y=10.9). Schneide unten, Rücken oben.
        final float kx = -5.1f, ky = 10.9f;
        // Resource-Pack-Variante: ein einziges Item-Modell (Griffmitte = Modellmitte)
        b.item("katana", "katana_item", "katana_item", kx, ky, 0f, 1f).packOnly().lod(0).tag(T_KATANA_HAND, T_BLADE);
        // Vanilla-Variante aus Blöcken
        b.block("katana", "tsuka", "grip", kx - 0.45f, ky - 0.45f, -2.6f, kx + 0.45f, ky + 0.45f, 3.0f).vanillaOnly().lod(0).tag(T_KATANA_HAND);
        b.block("katana", "kashira", "gold", kx - 0.5f, ky - 0.5f, -3.1f, kx + 0.5f, ky + 0.5f, -2.6f).vanillaOnly().lod(1).tag(T_KATANA_HAND);
        b.block("katana", "tsuba", "guard", kx - 1.4f, ky - 1.4f, 3.0f, kx + 1.4f, ky + 1.4f, 3.4f).vanillaOnly().lod(0).tag(T_KATANA_HAND);
        b.blockC("katana", "tsuba_gold", "gold", kx, ky, 3.2f, 2.4f, 2.4f, 0.32f).rotC(0, 0, 45).vanillaOnly().lod(1).tag(T_KATANA_HAND);
        b.block("katana", "habaki", "gold", kx - 0.35f, ky - 0.5f, 3.4f, kx + 0.35f, ky + 0.45f, 4.0f).vanillaOnly().lod(1).tag(T_KATANA_HAND);
        blade(b, kx, ky, 4.0f);

        // ============================================================ Saya (Scheide) an der linken Hüfte
        // Definiert im ungedrehten Knochenraum: Mund (Koiguchi) bei z=2.6, Spitze nach hinten (-z).
        final float sx = 4.5f, sy = 15.4f, sz = 2.6f;
        b.item("saya", "saya_item", "saya_item", sx, sy, sz, 1f).packOnly().lod(0).tag(T_SAYA);
        b.block("saya", "saya", "saya", sx - 0.5f, sy - 0.65f, sz - 15.2f, sx + 0.5f, sy + 0.65f, sz).vanillaOnly().lod(0).tag(T_SAYA);
        b.block("saya", "koiguchi", "gold", sx - 0.6f, sy - 0.75f, sz - 0.6f, sx + 0.6f, sy + 0.75f, sz).vanillaOnly().lod(1).tag(T_SAYA);
        b.block("saya", "kojiri", "gold", sx - 0.6f, sy - 0.75f, sz - 15.6f, sx + 0.6f, sy + 0.75f, sz - 14.9f).vanillaOnly().lod(1).tag(T_SAYA);
        b.block("saya", "saya_deco1", "sakura", sx + 0.45f, sy - 0.35f, sz - 5.2f, sx + 0.6f, sy + 0.35f, sz - 4.4f).vanillaOnly().lod(2).tag(T_SAYA);
        b.block("saya", "saya_deco2", "sakura", sx + 0.45f, sy - 0.25f, sz - 9.6f, sx + 0.6f, sy + 0.45f, sz - 8.9f).vanillaOnly().lod(2).tag(T_SAYA);
        // Griff des eingesteckten Schwerts (sichtbar, solange es in der Scheide steckt)
        b.item("saya", "sheathed_item", "hilt_item", sx, sy, sz + 3.2f, 1f).packOnly().lod(0).tag(T_KATANA_SHEATHED);
        b.block("saya", "s_tsuba", "guard", sx - 1.4f, sy - 1.4f, sz, sx + 1.4f, sy + 1.4f, sz + 0.4f).vanillaOnly().lod(0).tag(T_KATANA_SHEATHED);
        b.blockC("saya", "s_tsuba_gold", "gold", sx, sy, sz + 0.2f, 2.4f, 2.4f, 0.32f).rotC(0, 0, 45).vanillaOnly().lod(1).tag(T_KATANA_SHEATHED);
        b.block("saya", "s_tsuka", "grip", sx - 0.45f, sy - 0.45f, sz + 0.4f, sx + 0.45f, sy + 0.45f, sz + 6.0f).vanillaOnly().lod(0).tag(T_KATANA_SHEATHED);
        b.block("saya", "s_kashira", "gold", sx - 0.5f, sy - 0.5f, sz + 6.0f, sx + 0.5f, sy + 0.5f, sz + 6.5f).vanillaOnly().lod(1).tag(T_KATANA_SHEATHED);

        // ============================================================ Sashimono (Rückenbanner)
        b.block("sashimono", "pole", "pole", -0.3f, 17.6f, -3.95f, 0.3f, 40.2f, -3.35f).lod(0).tag(T_BANNER);
        b.block("flag", "crossbar", "pole", 0.3f, 39.1f, -3.8f, 6.7f, 39.6f, -3.5f).lod(1).tag(T_BANNER);
        b.block("flag", "flag", "flag", 0.3f, 30.2f, -3.72f, 6.5f, 39.1f, -3.58f).lod(0).tag(T_BANNER, T_CLOTH);
        Component kanji = Component.text("桜").color(TextColor.color(0xC2185B));
        b.text("flag", "flag_kanji_f", kanji, 3.4f, 34.6f, -3.55f, 1.55f).lod(1).tag(T_BANNER);
        b.text("flag", "flag_kanji_b", kanji, 3.4f, 34.6f, -3.75f, 1.55f).rotC(0, 180, 0).lod(1).tag(T_BANNER);

        return b.build();
    }

    // ------------------------------------------------------------------------ Helfer

    /** Quader, der auch mit gespiegelten (negativen) X-Werten funktioniert. */
    private static ModelBuilder.CubeBuilder box(ModelBuilder b, String bone, String id, String mat,
                                                float x1, float y1, float z1, float x2, float y2, float z2) {
        return b.block(bone, id, mat, x1, y1, z1, x2, y2, z2);
    }

    /** Eine Kusazuri-Platte mit roter Schnürung. axis: 'z' = Platte liegt in XY, 'x' = in ZY. */
    private static void plate(ModelBuilder b, String bone, String id, float x1, float y1, float z1,
                              float x2, float y2, float z2, char axis) {
        b.block(bone, id, "armor", x1, y1, z1, x2, y2, z2).lod(0).tag(T_PLATE);
        float ly1 = y1 + (y2 - y1) * 0.38f, ly2 = ly1 + 0.65f;
        if (axis == 'z') {
            b.block(bone, id + "_lace", "lace", x1 - 0.05f, ly1, z1 - 0.05f, x2 + 0.05f, ly2, z2 + 0.05f).lod(1).tag(T_PLATE);
        } else {
            b.block(bone, id + "_lace", "lace", x1 - 0.05f, ly1, z1 - 0.05f, x2 + 0.05f, ly2, z2 + 0.05f).lod(1).tag(T_PLATE);
        }
    }

    /**
     * Die Klinge: drei leicht gebogene Segmente (Sori) + Spitze (Kissaki), jeweils mit
     * weißer Härtelinie (Hamon) an der Schneide.
     */
    private static void blade(ModelBuilder b, float x, float y, float z0) {
        float[] lens = {4.6f, 4.4f, 3.2f};
        float[] angles = {0f, -2.6f, -5.6f};
        float w = 0.28f, h = 0.95f;
        float cy = y, cz = z0;
        for (int i = 0; i < lens.length; i++) {
            float len = lens[i], a = angles[i];
            b.block("katana", "blade" + i, "steel", x - w / 2, cy - h / 2, cz, x + w / 2, cy + h / 2, cz + len)
                    .rot(x, cy, cz, a, 0, 0).vanillaOnly().lod(0).tag(T_KATANA_HAND, T_BLADE);
            b.block("katana", "hamon" + i, "edge", x - w / 2 - 0.02f, cy - h / 2 - 0.02f, cz, x + w / 2 + 0.02f, cy - h / 2 + 0.3f, cz + len)
                    .rot(x, cy, cz, a, 0, 0).vanillaOnly().lod(1).tag(T_KATANA_HAND, T_BLADE);
            double rad = Math.toRadians(-a);
            cy += (float) (Math.sin(rad) * len);
            cz += (float) (Math.cos(rad) * len);
        }
        // Kissaki – schräg angeschliffene Spitze
        b.block("katana", "kissaki", "steel", x - w / 2, cy - h / 2, cz - 0.2f, x + w / 2, cy + h / 2 - 0.2f, cz + 1.3f)
                .rot(x, cy, cz, -7f, 0, 0).rot(x, cy + h / 2, cz + 1.3f, 24f, 0, 0).vanillaOnly().lod(0).tag(T_KATANA_HAND, T_BLADE);
    }
}
