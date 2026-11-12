# donnernet.de

The website of Aetherion: one day over the game's world. The camera is a single continuous take through real builds
running in the browser, from dawn to the next dawn. Scrolling moves the camera and the clock; nothing swaps, so nothing can pop.

It is a page for the server, not a game of its own. The home page is a hero, one statement, six frames with one line and a few
sentences each, and how to join. Anything deeper belongs in the wiki. Keep it that way: a new feature gets a wiki page, not a module.

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
| `src/components/home/` | `Hero`, `Beats` (the six frames), `Join`, `Server` (the server-list row) |
| `src/copy-home.js`, `src/copy.js`, `src/copy-wiki.js`, `src/copy-game.js` | All text, EN and DE. `copy-game.js` holds names the game itself uses (tool tiers, boss bars) |
| `src/data/` | Generated from the plugin sources by `forge/extract_site_data.py`: pets, items, districts. Do not edit by hand |
| `forge/` | The offline bake: schematics, structures and region files to mesh (`build_scenes.py`, `anvil.py`, `bake.c`), the sea chart (`chart/chart.py`) |
| `public/world3d/` | The baked meshes. Only derived block colours ship, no Mojang textures |
| `docs/PUBLIC_LORE_FOR_WEBSITE.md` | What may never appear on the public site. Read it before writing copy |

## How the page is layered

No element above the stage may create a stacking context (no transform, filter, isolation or z-index on ancestors of the title), because the page's `<h1>` sits between the sky and the islands:

```
sky canvas, stars, sun, moon   z -30
the <h1>                       z -20
world canvas (transparent)     z -10
cloud veil, posters            z  -9
page content                   z   0
```

Fog is alpha, so the world dissolves into whatever sky is behind it. Weak devices lose pixels first, then the 3D (posters), never the page. Reduced motion, data saver and no WebGL2 get posters straight away.

## The camera and its sets

`src/world/route.js` is the whole take. `ROUTE` pins a camera key (position, target, lens, time of day) to a fraction of a section;
`stage.js` interpolates between keys with a monotone cubic over scroll distance, so the camera settles on a frame and never overshoots it.
It banks a little into turns, and a box of motes travels with it.

A key also names its set (`z`). `SETS` are the meshes, loaded in page order: the first one is what the page opens on.
Between two keys of different sets, the old set dissolves into the air in the first half and the new one arrives in the second, so two
places are never on screen together. The islands are one mesh of four parts, each placed on its own as often as the sky needs islands.

To compose a frame, run the dev server with `?shot`: `window.__stage.peek({ eye, at, fov, t, z })` paints any view, `snap(y)` any scroll position.

## Rebaking the world

```bash
export MC_ASSETS=<folder with minecraft-assets and minecraft-data from npm>
python3 website/forge/build_scenes.py <repo root> <exil_gesamt.schem> website/public/world3d     # SCENES=isles,prison,city picks some
```

After changing the route or a scene, refresh the stills for devices without 3D and the link preview, with the dev server running:
`node forge/capture_posters.cjs <dev url> public`.

### The main island

The page opens on the starter islands only until the main island is in. It comes straight from the server's world folder:

```bash
python3 website/forge/anvil.py <world>/region                    # what is in there: chunks, bounds, heights
SCENES=island ISLAND=<world>/region ISLAND_BOX=x0,z0,x1,z1 \
  ISLAND_FINE="x,z,r;x,z,r" python3 website/forge/build_scenes.py <repo root> x website/public/world3d
```

`ISLAND_BOX` is the island's footprint in world blocks (about -552..476 by -640..374, nine region files `r.-2.-2` to `r.0.0`).
`ISLAND_FINE` lists circles around the places the camera comes close to (harbour, capital); outside them the grid is redrawn in
2-block cells, which nobody can tell from a flying camera and which keeps the download small. Leave it out to keep every block.
Seas bake as one surface. The result is `island.wasm`, and `scenes.json` gets its `offset`: add the set to `SETS` in `route.js`
with that origin, put it first, give it a zone, and move the keys from the islands to the harbour. Then recapture the stills.

The island is a purchased map (OriginBuilds). Its mesh is downloaded by every visitor and this repository is public, so check the
licence before the file is committed. What ships is softened geometry and averaged colours, never the world files or textures.

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
