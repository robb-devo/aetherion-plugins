# donnernet.de

The website of Aetherion, written as one day over the game's world. The camera is a single continuous take through a real
3D build running in the browser, from dawn to the next dawn. Scrolling moves the camera and the clock; nothing swaps, so nothing can pop.

Vite, React 19, Tailwind 4, raw WebGL2. No animation library, no 3D library.

## Run it

```bash
cd website
npm i
npm run dev              # AETHERION_API=<url> proxies /api to a Control API; without it the account pages show their offline state
npm run build            # dist/: client build, then every public page baked to real HTML in EN and DE
npm run build:preview    # dist-preview/: one relative page for a hosted preview (routes live in the hash)
```

## Where things are

| Path | What |
|---|---|
| `src/world/` | The engine (`engine.js`, `shaders.js`), the day (`day.js`), the camera's route (`route.js`), the runtime that ties scroll to all of it (`stage.js`) |
| `src/components/home/` | One file per chapter or module: `Talk`, `Skills`, `Chart`, `Isles`, `Pets`, `Loot`, `Prison`, `Gnomon`, `Bosses`, `Night`, `Join` |
| `src/copy-home.js`, `src/copy.js`, `src/copy-wiki.js` | All text, EN and DE |
| `src/data/` | Generated from the plugin sources by `forge/extract_site_data.py`: what NPCs say, pets, items, districts, bosses. Do not edit by hand |
| `forge/` | The offline bake: schematic to mesh (`build_scenes.py`, `bake.c`), the sea chart (`chart/chart.py`) |
| `public/world3d/` | The baked meshes. Only derived block colours ship, no Mojang textures |
| `docs/PUBLIC_LORE_FOR_WEBSITE.md` | What may never appear on the public site. Read it before writing copy |

## How the page is layered

No element above the stage may create a stacking context (no transform, filter, isolation or z-index on ancestors of the title), because the page's `<h1>` sits between the sky and the towers:

```
sky canvas, stars, sun, moon   z -30
the <h1>                       z -20
world canvas (transparent)     z -10
cloud veil, posters            z  -9
page content                   z   0
```

Fog is alpha, so the world dissolves into whatever sky is behind it. Weak devices lose pixels first, then the 3D (posters), never the page. Reduced motion, data saver and no WebGL2 get posters straight away.

## Rebaking the world

```bash
export MC_ASSETS=<folder with minecraft-assets and minecraft-data from npm>
cd website/forge && python3 build_scenes.py <repo root> <exil_gesamt.schem> ../public/world3d
```

Only own builds may be meshed: the geometry is downloaded by every visitor, so purchased maps stay out until their licence is checked.

## Deploy

Build from a clean `main`, then copy `dist/` into the web root. Keep `launcher/` and the launcher `.exe` that live there: `dist/` does not contain them, so copy, do not mirror-delete.

nginx needs `try_files $uri $uri/ /index.html;` so baked pages win and the account pages fall back to the app, and `.wasm` served as a file.
`/de/...` is the German site; `index.html` sends German browsers there before the first paint.

Three values stay out of this repo and go in `website/.env.production.local` at build time. The real values are in `aetherion-ops/docs/`:

| Variable | What |
|---|---|
| `VITE_VANILLA_JOIN` | The vanilla server's address. Without it the status API can send `join`; without either, the vanilla row stays hidden |
| `VITE_CLOUD_NODE` | The node id for cloud servers on the create page. Without it, the API's node list decides |
| `VITE_HOME_NODE` | The node id for the home server. Without it, the API's node list decides |
