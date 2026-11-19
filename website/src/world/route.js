/**
 * The camera's one take. Every key is pinned to a place in the page: chapter `ch`, fraction `f`
 * of that chapter's height. Scrolling moves the camera from key to key, and the clock runs with
 * it: `t` is the time of day from day.js.
 *
 *   eye, at, fov   where it stands, what it looks at, how wide
 *   sx, sy         lens shift as a fraction of the view (image moves right / up)
 *   z              which sets stand here; the others are dissolved into the air. A list names
 *                  several, the first one is the place (ZONES)
 *   solid          1 where the page title stands behind the world: nothing may shine through
 *   p              portrait overrides: a phone gets its own framing, subject above the text
 *
 * Grid coordinates: 1 unit is 1 block, the main island's own world coordinates.
 */

/**
 * The sets, in the order the page needs them. The first one is what the page opens on.
 * The starter islands are one mesh of four starts (Guilds templates), each a part that stands on
 * its own: `place` puts a part's centre at a point in the sky, as often as the sky needs islands.
 */
export const SETS = {
  // The main island, straight from the server's world (forge/anvil.py). Anker Harbour opens the page.
  // `rise`: the intro lifts it out of the fog from the harbour outward (x, z, reach in blocks).
  island: { url: 'world3d/island.wasm', origin: [-527, -64, -617], rise: [330, -450, 640] },
  isles: {
    url: 'world3d/isles.wasm',
    parts: {
      grove: { x: [0, 128], c: [64, 23.5, 67.5] },
      tide: { x: [128, 256], c: [192, 23.5, 68] },
      quarry: { x: [256, 384], c: [319.5, 23.5, 68] },
      guild: { x: [384, 512], c: [448, 23.5, 68] },
    },
    // players' own islands float off the west coast, higher than the main island
    place: [
      // the four the camera visits
      ['grove', -700, 300, -230],
      ['quarry', -790, 288, -350],
      ['tide', -830, 296, -90],
      ['guild', -930, 312, -230],
      // other people's islands, out to the horizon
      ['tide', -640, 250, -560],
      ['guild', -1000, 336, -470],
      ['grove', -1090, 276, -60],
      ['quarry', -760, 352, 130],
      ['tide', -1180, 310, -300],
      ['grove', -640, 226, 260],
      ['guild', -1300, 290, 120],
      ['quarry', -1400, 330, -560],
      ['tide', -900, 260, 360],
      ['grove', -1500, 270, -200],
      ['quarry', -1050, 240, 420],
    ],
  },
  // floor 1 of the prison hangs under the starter islands, below the cloud sea
  prison: { url: 'world3d/prison.wasm', origin: [-1060, -350, -300] },
  // the city past the fog: late game, so it only stands at night, near the end of the page
  city: { url: 'world3d/city.wasm', origin: [250, 0, 137] },
}

/**
 * Per place: where the birds circle (x, z, height, scale, or 'follow', height above the subject, scale),
 * where the mist pools, what drifts in the air.
 */
export const ZONES = {
  island: { flock: ['follow', 58, 0.42], mistY: 66 },
  isles: { flock: [-800, -200, 336, 0.45], mistY: 230 },
  prison: { motes: [1, 0.46, 0.18], wind: [0.08, 0.55, 0.05] },
  city: { flock: [430, 327, 226, 1], mistY: 92 },
}

/** Cloud layers. `thick` and `veil`: how the screen whites out while the camera passes through one. */
export const CLOUDS = [
  { y: -70, r: 6000, cover: 0.46, alpha: 1.0, scale: 0.00042, thick: 46, veil: 1 },
  { y: 18, r: 3600, cover: 0.68, alpha: 0.55, scale: 0.00085, thick: 20, veil: 0.45 },
]

export const ROUTE = [
  // Dawn over Anker Harbour: the bay, the ships, the town climbing to Skyreach. The name stands behind it.
  { ch: 'hero', f: 0, t: 0.0, z: 'island', solid: 1, eye: [460, 112, -690], at: [325, 82, -440], fov: 40, sx: 0.1, sy: -0.3,
    p: { eye: [560, 104, -880], at: [322, 70, -462], fov: 50, sy: 0.06 } },
  { ch: 'hero', f: 0.85, t: 0.03, z: 'island', solid: 1, eye: [452, 110, -686], at: [322, 82, -440], fov: 40, sx: 0.1, sy: -0.28,
    p: { eye: [552, 104, -876], at: [321, 70, -462], fov: 50, sy: 0.05 } },

  // Morning over the Capital: the avenue, the Grand Stair, the gate under the mountain.
  { ch: 'about', f: 0.25, t: 0.085, z: 'island', eye: [60, 210, 300], at: [0, 70, -60], fov: 40, sx: 0.17,
    p: { eye: [70, 240, 340], at: [0, 70, -70], fov: 56, sy: 0.18 } },
  { ch: 'about', f: 0.75, t: 0.115, z: 'island', eye: [48, 196, 262], at: [0, 72, -72], fov: 40, sx: 0.17,
    p: { eye: [58, 226, 300], at: [0, 72, -80], fov: 56, sy: 0.18 } },

  // Late morning along the south: the fields of Clucksworth, then Southwood and the road to the mine.
  { ch: 'jobs', f: 0.25, t: 0.165, z: 'island', eye: [-130, 215, 370], at: [-280, 62, 150], fov: 40, sx: -0.17,
    p: { eye: [-120, 250, 420], at: [-280, 62, 150], fov: 56, sy: 0.18 } },
  { ch: 'jobs', f: 0.75, t: 0.205, z: 'island', eye: [90, 210, 420], at: [-20, 80, 220], fov: 40, sx: -0.17,
    p: { eye: [100, 245, 470], at: [-20, 80, 220], fov: 56, sy: 0.18 } },

  // Noon, high up: the whole island at once.
  { ch: 'world', f: 0.25, t: 0.265, z: 'island', eye: [420, 620, 640], at: [-40, 30, -120], fov: 46,
    p: { eye: [560, 860, 900], at: [-40, 40, -130], fov: 60, sy: 0.24 } },
  { ch: 'world', f: 0.75, t: 0.31, z: 'island', eye: [300, 650, 700], at: [-60, 30, -130], fov: 46,
    p: { eye: [420, 890, 960], at: [-60, 40, -140], fov: 60, sy: 0.24 } },

  // Afternoon off the west coast: an island of your own, the main island below.
  { ch: 'isle', f: 0.25, t: 0.365, z: ['isles', 'island'], eye: [-760, 314, -262], at: [-700, 306, -230], fov: 42, sx: -0.17,
    p: { eye: [-786, 320, -276], at: [-700, 307, -230], fov: 56, sy: 0.18 } },
  { ch: 'isle', f: 0.75, t: 0.41, z: ['isles', 'island'], eye: [-750, 312, -280], at: [-700, 306, -230], fov: 42, sx: -0.17,
    p: { eye: [-772, 318, -300], at: [-700, 307, -230], fov: 56, sy: 0.18 } },

  // Golden hour at the lagoon, lanterns coming on.
  { ch: 'pets', f: 0.25, t: 0.465, z: ['isles', 'island'], eye: [-890, 310, -110], at: [-830, 298, -90], fov: 42, sx: 0.17,
    p: { eye: [-912, 316, -130], at: [-830, 299, -90], fov: 56, sy: 0.18 } },
  { ch: 'pets', f: 0.75, t: 0.5, z: ['isles', 'island'], eye: [-884, 308, -72], at: [-830, 298, -90], fov: 42, sx: 0.17,
    p: { eye: [-906, 314, -60], at: [-830, 299, -90], fov: 56, sy: 0.18 } },

  // Dusk. Over the edge, through the cloud sea, to what hangs below it.
  { ch: 'dungeons', f: 0.0, t: 0.57, z: 'isles', eye: [-880, 70, -40], at: [-895, -120, -190], fov: 54 },
  { ch: 'dungeons', f: 0.42, t: 0.645, z: 'prison', eye: [-700, -180, -20], at: [-890, -295, -210], fov: 46, sx: -0.14,
    p: { eye: [-670, -150, 10], at: [-890, -295, -210], fov: 56, sy: 0.2 } },
  { ch: 'dungeons', f: 0.82, t: 0.69, z: 'prison', eye: [-790, -190, -50], at: [-920, -302, -220], fov: 48, sx: -0.14,
    p: { eye: [-760, -160, -10], at: [-920, -302, -220], fov: 56, sy: 0.2 } },

  // Up through the clouds again, and it is night, and there is a city, and its windows are lit.
  { ch: 'exile', f: 0.25, t: 0.8, z: 'city', eye: [860, 225, 200], at: [420, 165, 330], fov: 38, sx: 0.12,
    p: { eye: [990, 280, 160], at: [420, 175, 330], fov: 50, sy: 0.2 } },
  { ch: 'exile', f: 0.75, t: 0.86, z: 'city', eye: [880, 220, 370], at: [420, 165, 330], fov: 38, sx: 0.12,
    p: { eye: [1020, 270, 380], at: [420, 175, 330], fov: 50, sy: 0.2 } },

  // Before morning, home again: the harbour with its lamps still on, then first light.
  { ch: 'join', f: 0.2, t: 0.93, z: 'island', eye: [700, 330, -160], at: [250, 70, -400], fov: 44,
    p: { eye: [740, 370, -140], at: [250, 70, -400], fov: 58, sy: 0.2 } },
  { ch: 'join', f: 0.88, t: 0.975, z: 'island', eye: [500, 130, -700], at: [330, 82, -440], fov: 40, sy: -0.2,
    p: { eye: [580, 122, -900], at: [322, 70, -462], fov: 50, sy: 0.05 } },
  { ch: 'finale', f: 1, t: 1, z: 'island', eye: [460, 112, -690], at: [325, 82, -440], fov: 40, sx: 0.06, sy: -0.3,
    p: { eye: [560, 104, -880], at: [322, 70, -462], fov: 50, sy: 0.06 } },
]
