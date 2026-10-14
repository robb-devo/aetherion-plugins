# Opus prompt — Aetherion custom shader (Iris)

Copy everything below the line into a new Opus chat.
Recommended effort: **Extra or Max**.
Workspace for assets/docs: `C:\Users\Robbi\IdeaProjects\aetherion-story-p1` (and a ship folder under `IdeaProjects\_aetherion_shader_ship` if `.claude` writes are blocked).

---

```text
# AETHERION SHADER — go completely insane (but stay FPS-friendly)

You are building a **custom Iris shader pack** (Java 1.21.1 / Paper clients) tailored exclusively for the Aetherion Minecraft network. Not a generic Complementary fork with a new logo. Not a kitchen-sink Ultra pack. A **bespoke look** that makes every Aetherion place feel authored — Harbour fog, Glowcap night, Helios sun-tears, World Eater void, Forage weather kinds, stained-glass combat light — while surviving Origin Capital density, forage foliage, boss BlockDisplay storms, and personal-island factories.

Robb's bar: **light should look brutally sick**. Any stained glass / tinted glass / froglight / sea lantern / shroomlight / colored block should be able to **become light** (emissive + colored bloom). Weather, fog, haze, veil — first-class. You may go wild creatively. You must stay resource-friendly by design (profiles, LODs, district gates), not by gutting the look.

Deliver as a shippable Iris shader pack (+ clear install: Iris + Sodium/Iris stack for 1.21.1). Optional Complementary-Reimagined / Photon / Bliss DNA is fine as a base IF you reshape it hard for Aetherion. Prefer a **named profile set** (Performance / Balanced / Cinematic / Boss) rather than one Ultra preset that melts GPUs.

---

## HARD CONSTRAINTS

1. **Iris-first.** Target Iris shaders on Fabric/Quilt (and document Fabric+Iris client side). No OptiFine-only path as primary.
2. **Vanilla-safe fallback.** Server does NOT require the pack (docs already say no Iris dependency for shipping). Pack is the premium look.
3. **Resource-friendly by architecture:**
   - Volumetric fog / rain shafts / godrays = high quality but **distance-capped**, **toggleable**, and **lighter on forage/islands**.
   - BlockDisplay-heavy boss arenas must not die (Helios DisplayBudget already degrades props — don't double-kill with insane SSAO everywhere).
   - Provide presets: `Aetherion_Perf` (GTX 1660 / laptop), `Aetherion_Balanced` (default), `Aetherion_Cinematic` (showcase), `Aetherion_Boss` (fights: less volumetric, keep bloom + specular).
4. **Do not break readability** of combat telegraphs, TalkUx speech bubbles, TAB, or chalk/boss ground tells. Bloom must not wash out red commit shadows / gold openings / Gnomon iris tells.
5. **No server Java rewrites** unless a tiny optional datapack/resource-pack hook is required for biome fog IDs. Prefer pure shader + maybe a tiny resource pack of custom color maps / lightmap LUTs.
6. Additive ship: put work in `IdeaProjects\_aetherion_shader_ship\` if you cannot write into the story-p1/.claude worktree. README + install + preset list.

---

## WHAT AETHERION ACTUALLY HAS (deep scan — implement FOR these)

### A. District / world atmospheres (must feel distinct)

| Place | Visual intent the shader must sell |
|---|---|
| **Anker Harbour / Opening** | Salt air, soft harbour fog, water reflections, foghorn mood; morning light after orbit (time ~1000). Replace CLOUD carpets with real volumetric harbour fog when possible. |
| **Capital** | Clean daylight, cherry-petal season, warm stone; dusk lanterns. Dense builds — don't crush FPS with full volumetrics at 1km scale. |
| **Origin phases** | DAWN / DAY / DUSK / NIGHT (OriginIsle). Fireflies dusk→night, lighthouse END_ROD beams, mast lights, glowcap spores. Dawn/dusk should tint the whole isle, not just skybox. |
| **Glowcap Crags** | Bioluminescent night: cyan/teal emission from glowcaps (SEA_LANTERN nodes + LIGHT_BLUE glass caps + Brightness 15 displays). Spores in air. |
| **Whisperwood / Southwood** | Soft green canopy, fireflies, cooler fog pockets. |
| **Borderlands / wastes** | Dry haze, bone/dust, magenta→cyan rite beams; less pretty, more hostile. Softlight system **excludes** borderlands — shader haze can fill that gap. |
| **Ore Ridge / Mining** | Cave darkness that still reads; Emberseam **heat bloom** (lava/magma/orange glass braziers); forge smoke mood; Amethyst Mines locked night (~18000) with purple crystal glow. |
| **Farm (Clucksworth / Eldervale)** | Soft pastoral, pollen/petals, gentle bloom — never harsh sci-fi. |
| **Forage groves** | Weather kinds exist in code but **visuals are OFF** (`isle-weather.visuals: false`) because rain+foliage kills FPS. Shader must bring them back **cheaply**: CLEAR, FOG, RAIN, DRIZZLE, SNOW, WINDY — grove-flavored (Frostpine snow, Gloamwood fog/wisps, Brinefall wet mangrove, Blossom wind/petals, Sunscar hot clear, Elderwood drizzle, Canopy jungle rain). Prefer screen-space / light volumetrics over forcing vanilla storms. |
| **Fishing Eldervale** | Water is a star: SSR/reflections, wetness when `world.hasStorm()`, bubble columns, night+rain species mood (Pale Ghost). Harbour pier wet decks. |
| **Exile (`aetherion_exile` / mmo-c)** | Ferry "through the Veil": thicker cool fog, muted sun, arrival time ~12400; fog that *breaks* into clearer shore light. |
| **Personal / guild islands (mmo-i)** | Clean void sky around plots; factory BlockDisplays; keep Perf preset default here. |
| **Dungeons (mmo-d)** | Controlled corridor LIGHT floods + lantern warmth; don't turn every LIGHT block into a nuke. |

### B. Boss atmospheres (shader must go hard)

| Boss | Need |
|---|---|
| **Helios** | Per-player dusk → starless tear → supernova noon. True **sky color** beyond setPlayerTime (docs call this out). Godrays from ochre froglight "sun", heat haze, DOWNFALL that dims stars without ugly vanilla rain. Fog + screen darken already via bossbar — complement, don't fight it. |
| **Seraphine** | Theatre night (~13000), follow-spot pools, warm gold vs cold stage; soft dust in beams. |
| **World Eater** | Void: black sky + real stars, purple/black tear (TINTED_GLASS / stained glass VFX), sculk mood, star-eating fade. Colored void bloom on purple glass. |
| **Frostbound** | Blizzard volumes vs hearth warmth (dual climate in one arena). |
| **Gnomon** | Forced night + CLEAR seal; chalk-white reads on dark floor; iris tells white/amber/violet/red must stay readable; subtle surveyor haze, not soup. |
| **Ashen Sovereign** | Last Sky: darkened 18000 + kindled sun — dramatic contrast. |
| **Deepsong Leviathan** (Items spectacle) | Mist/spray, sky race dusk→held night→dawn — water + volumetric mist showcase. |

### C. LIGHT IS THE IDENTITY (non-negotiable)

Make emission and colored light the hero feature:

1. **labPBR / custom specular+emission maps** (or Iris equivalent) so these become real colored lights:
   - All 16 **stained glass** + panes (combat geometry, meteors, portals, glowcap caps, braziers)
   - **TINTED_GLASS** (void haze — soft absorption + faint emission edge)
   - **OCHRE / PEARLESCENT / VERDANT FROGLIGHT**
   - **SEA_LANTERN, SHROOMLIGHT, GLOWSTONE, GLOW_LICHEN, GLOW_BERRIES**
   - **Amethyst** block/cluster/budding
   - **Beacon, END_ROD, LANTERN, SOUL_LANTERN, CAMPFIRE, MAGMA_BLOCK**
   - **LIGHT** block (invisible flood — soft, not nuclear)
2. **Any dyed / colored block can contribute tinted light** when emissive or next to emitters — glass especially. Magenta glass in Borderlands rites, cyan glowcaps, orange forge glass, purple WE rifts, red/orange Helios meteors.
3. **BlockDisplay brightness 15** props (Gnomon parts, Helios rigs, WE rigs, Origin waystones) must bloom like intentional practical lights, not washed cubes.
4. **Contact hardening / colored bounce** in interiors (Capital halls, mine forges) — subtle, cheap GI approximation OK.
5. **Do not** make the entire overworld neon. Daylight Capital stays natural; night Glowcaps / bosses / forge / void get the sickness.

### D. Weather & fog system (shader-side)

Implement an **Aetherion Atmosphere controller** (uniforms / option toggles / dimension+biome rules):

- Harbour fog (dense near water, soft falloff inland)
- Borderlands dry haze
- Gloamwood / Veil fog (cool, slightly green-gray)
- Exile ferry fog (story)
- Forage FOG / DRIZZLE / RAIN / SNOW / WINDY as **optional overlays** that don't require server `setPlayerWeather`
- Boss fog hooks: Helios CREATE_WORLD_FOG feel, WE void clarity (almost no scatter), Frostbound white-out bursts
- Rain wetness on stone/wood/metal; snow accumulation light on Frostpine/Frostbound only
- Underwater: clear harbour teal vs deep Eldervale lake vs brine mangrove — different absorption

Performance: volumetrics OFF or LOW in Perf; Balances medium distance; Cinematic full; Boss = short-range godrays + bloom, low volumetric.

### E. Sky & time (per-player realities)

Server already forces private skies (Helios SkyControl, Seraphine theatre night, WE void, Gnomon night, Ashen Last Sky, Opening morning, Exile 12400, Amethyst Mines 18000). Shader must:

- Respect time-of-day driven lighting even when weird
- Offer **sky profiles**: Natural Origin, Void WE, Helios Act, Theatre Seraphine, Sealed Gnomon, Mines Night
- Stars that die for Helios supernova / WE star-eating; aurora optional for Origin `aurora` event
- Moon/sun disks that can be replaced/styled for Helios without breaking vanilla fights for non-shader clients

### F. Materials & special blocks to author looks for

Stained glass (all), tinted glass, froglights, sea lantern, shroomlight, amethyst family, sculk family, copper (Terminus / mining), magma/lava/soul fire, black concrete / crying obsidian (void body), cherry leaves (petal districts), water, ice/packed ice (Frostbound/Frostpine), sand/red sand (Sunscar/Borderlands).

### G. Performance hotspots (design around these)

- Origin Capital + softlight pass + emitters + NPCs + TalkUx
- Forage dense foliage (why rain packets are disabled)
- Boss arenas with hundreds of BlockDisplays (Helios DisplayBudget)
- Guild island quarry / belts / factories on mmo-i
- Amethyst Mines + Emberseam particle carpets
- Colosseum / Borderlands altar (historically particle-heavy)

Rule of thumb: **particles stay server-owned**; shader adds atmosphere that *replaces* the need for CLOUD/ASH carpets where possible.

---

## CREATIVE FREEDOM (please go hard)

- Custom color grading LUTs per district (Harbour cool, Capital warm, Borderlands sickly, Void crushed blacks + purple lift, Helios gold crush).
- Signature **Aetherion bloom**: soft cinematic, not Mobile Legends neon — except Glowcaps/void/boss peaks.
- Optional subtle film grain / vignette only in Cinematic preset.
- Custom water that makes Fishing Eldervale a trailer shot.
- Glass that refracts AND emits — stained glass windows in Capital should throw colored light into halls.
- Night that makes Glowcap Crags and Beacon Pier feel like a festival.
- Boss "event looks" that switch with time/sky (document how: biome, dimension, or manual preset hotkey).

If a technique needs a tiny companion resource pack (custom lightmaps, PBR textures for key blocks, font-safe UI), ship it beside the shader. Do **not** require changing locked combat systems.

---

## DELIVERABLES

1. **Iris shader pack** (zip) with presets: Perf / Balanced / Cinematic / Boss.
2. **README**: install (Iris + Sodium versions for MC 1.21.1), preset guide, known conflicts, how to hot-switch Boss preset.
3. **AETHERION_LOOK.md**: short art bible — district table, boss table, emission materials list, what each preset sacrifices.
4. **Optional** tiny resource pack for PBR/emissive on key blocks (stained glass, froglight, glowcap kit, amethyst).
5. Ship folder `_aetherion_shader_ship\` with apply notes if needed. **No live deploy.**
6. Screenshot/frame checklist for Robb: Harbour fog dawn, Capital dusk, Glowcap night, Forage Gloamwood fog, Mining forge, Helios arena, WE void, Gnomon seal, Fishing rain night, Exile ferry.

## SUCCESS

Someone joins Aetherion with the Balanced preset and immediately feels "this server has its own light." Glass and colored blocks *are* light. Fog/weather sell Harbour, Veil, Forage, Exile without melting a mid-range GPU. Bosses get trailer-grade skies without hiding telegraphs. Perf preset stays playable on forage + capital + islands.

Go make it disgusting in the best way.
```
