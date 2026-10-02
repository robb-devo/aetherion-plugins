# Opus — neue Props & Schematics (ausrasten)

**Hygiene:** `docs/OPUS_PROMPT_HYGIENE.md` — code/assets only. No ship folder, no README, no Manifest, no Apply-Script.

Mach **neue Prop-Wand-Pieces** für Aetherion Hub: direkt `PropCatalog` + neue `.schem`.

**Auftrag:** Absolut ausrasten. Deco und/oder Landmarks — so groß/wild du willst. Erste 18 waren stark; diesmal Gas geben.

**Einzige harte Bedingung:** Muss zur Aetherion-Welt passen (Fantasy / Hafen / Mine / Foraging / Fishing / Hub). Kein Sci-Fi, keine Raumschiffe.

**Nicht anfassen:** NPCs, Quests, Dialog, TalkUx, Origin, Ranks, Weapons, Skill-Gameplay.

**Technik:**
- Additiv — bestehende 18 bleiben
- ids: `ae_prop_*` (deco) oder `ae_*` (größer)
- Eintrag in `AetherionHub/.../prop/PropCatalog.java`
- Datei `AetherionHub/src/main/resources/props/<id>.schem` (Sponge, wie bestehende)
- Süd = Front
- Direkt im Source editieren. Fertig = Catalog + Schems. **Kein Deploy.** Compile nur wenn Robbi sagt / wenn’s ohne m2-Betteln geht.
