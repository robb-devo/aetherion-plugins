/**
 * The camera's one take. Every key is pinned to a place in the page: chapter `ch`, fraction `f`
 * of that chapter's height. Scrolling moves the camera from key to key, and the clock runs with
 * it: `t` is the time of day from day.js.
 *
 *   eye, at, fov   where it stands, what it looks at, how wide
 *   sx, sy         lens shift as a fraction of the view (image moves right / up)
 *   z              which set stands here; the others are dissolved into the air
 *   solid          1 where the page title stands behind the world: nothing may shine through
 *   p              portrait overrides: a phone gets its own framing, subject above the text
 *
 * Grid coordinates: 1 unit is 1 block.
 */

/**
 * The sets, in the order the page needs them. The first one is what the page opens on.
 * The islands are one mesh of four starts (Guilds templates), each a part that stands on its own:
 * `place` puts a part's centre at a point in the sky, as often as the sky needs islands.
 */
export const SETS = {
  isles: {
    url: 'world3d/isles.wasm',
    parts: {
      grove: { x: [0, 128], c: [64, 23.5, 67.5] },
      tide: { x: [128, 256], c: [192, 23.5, 68] },
      quarry: { x: [256, 384], c: [319.5, 23.5, 68] },
      guild: { x: [384, 512], c: [448, 23.5, 68] },
    },
    place: [
      // the four the camera visits
      ['guild', 806, 190, -262],
      ['quarry', 655, 196, -238],
      ['tide', 743, 204, -123],
      ['grove', 512, 204, -281],
      // other people's islands, out to the horizon
      ['tide', 380, 150, -420],
      ['quarry', 300, 236, -150],
      ['grove', 150, 180, -340],
      ['guild', 40, 120, -560],
      ['grove', 420, 250, -640],
      ['tide', -120, 210, -200],
      ['quarry', -260, 160, -480],
      ['guild', 430, 144, -60],
      ['grove', 980, 230, -520],
      ['quarry', 1090, 170, -330],
      ['tide', 1200, 200, -90],
    ],
  },
  // floor 1 of the prison hangs under the islands, below the cloud sea
  prison: { url: 'world3d/prison.wasm', origin: [540, -350, -330] },
  // the city past the fog: late game, so it only stands at night, near the end of the page
  city: { url: 'world3d/city.wasm', origin: [250, 0, 137] },
}

/** Per place: where the birds circle (x, z, height, scale), where the mist pools, what drifts in the air. */
export const ZONES = {
  isles: { flock: [640, -262, 230, 0.45], mistY: 130 },
  prison: { motes: [1, 0.46, 0.18], wind: [0.08, 0.55, 0.05] },
  city: { flock: [430, 327, 226, 1], mistY: 92 },
}

/** Cloud layers. `thick` and `veil`: how the screen whites out while the camera passes through one. */
export const CLOUDS = [
  { y: -70, r: 6000, cover: 0.46, alpha: 1.0, scale: 0.00042, thick: 46, veil: 1 },
  { y: 18, r: 3600, cover: 0.68, alpha: 0.55, scale: 0.00085, thick: 20, veil: 0.45 },
]

export const ROUTE = [
  // Dawn. The islands players call home, lit from the east, the name behind them.
  { ch: 'hero', f: 0, t: 0.0, z: 'isles', solid: 1, eye: [890, 205, -244], at: [770, 195, -262], fov: 40, sx: 0.17, sy: -0.12,
    p: { eye: [914, 207, -240], at: [806, 197, -262], fov: 56, sy: 0.1 } },
  { ch: 'hero', f: 0.85, t: 0.03, z: 'isles', solid: 1, eye: [880, 204, -252], at: [772, 195, -262], fov: 40, sx: 0.17, sy: -0.1,
    p: { eye: [908, 206, -248], at: [806, 197, -262], fov: 56, sy: 0.1 } },

  // Morning, close enough to see that somebody built this.
  { ch: 'about', f: 0.25, t: 0.085, z: 'isles', eye: [848, 203, -296], at: [806, 194, -262], fov: 42, sx: 0.17,
    p: { eye: [868, 209, -312], at: [806, 195, -262], fov: 56, sy: 0.18 } },
  { ch: 'about', f: 0.75, t: 0.115, z: 'isles', eye: [832, 201, -306], at: [806, 194, -262], fov: 42, sx: 0.17,
    p: { eye: [846, 207, -324], at: [806, 195, -262], fov: 56, sy: 0.18 } },

  // The quarry island: a mine mouth, a shed, ore in the rock.
  { ch: 'jobs', f: 0.25, t: 0.165, z: 'isles', eye: [705, 205, -210], at: [655, 197, -238], fov: 40, sx: -0.17,
    p: { eye: [731, 212, -196], at: [655, 198, -238], fov: 56, sy: 0.18 } },
  { ch: 'jobs', f: 0.75, t: 0.205, z: 'isles', eye: [711, 203, -248], at: [655, 197, -238], fov: 40, sx: -0.17,
    p: { eye: [739, 210, -252], at: [655, 198, -238], fov: 56, sy: 0.18 } },

  // Noon, high up: the sky is the chart table.
  { ch: 'world', f: 0.25, t: 0.265, z: 'isles', eye: [900, 350, -120], at: [640, 180, -260], fov: 46,
    p: { eye: [930, 380, -100], at: [640, 180, -260], fov: 60 } },
  { ch: 'world', f: 0.75, t: 0.31, z: 'isles', eye: [840, 366, -90], at: [620, 180, -260], fov: 46,
    p: { eye: [870, 396, -70], at: [620, 180, -260], fov: 60 } },

  // Afternoon, the grove: a camp under an old oak. A place of your own.
  { ch: 'isle', f: 0.25, t: 0.365, z: 'isles', eye: [460, 215, -304], at: [512, 207, -281], fov: 42, sx: -0.17,
    p: { eye: [434, 221, -316], at: [512, 208, -281], fov: 56, sy: 0.18 } },
  { ch: 'isle', f: 0.75, t: 0.41, z: 'isles', eye: [472, 213, -322], at: [512, 207, -281], fov: 42, sx: -0.17,
    p: { eye: [452, 219, -342], at: [512, 208, -281], fov: 56, sy: 0.18 } },

  // Golden hour at the lagoon, lanterns coming on.
  { ch: 'pets', f: 0.25, t: 0.465, z: 'isles', eye: [694, 211, -85], at: [743, 207, -123], fov: 42, sx: 0.17,
    p: { eye: [671, 217, -65], at: [743, 208, -123], fov: 56, sy: 0.18 } },
  { ch: 'pets', f: 0.75, t: 0.5, z: 'isles', eye: [688, 209, -115], at: [743, 207, -123], fov: 42, sx: 0.17,
    p: { eye: [661, 215, -109], at: [743, 208, -123], fov: 56, sy: 0.18 } },

  // Dusk. Over the edge, through the cloud sea, to what hangs below it.
  { ch: 'dungeons', f: 0.0, t: 0.57, z: 'isles', eye: [720, 70, -70], at: [705, -120, -220], fov: 54 },
  { ch: 'dungeons', f: 0.42, t: 0.645, z: 'prison', eye: [900, -180, -50], at: [710, -295, -240], fov: 46, sx: -0.14,
    p: { eye: [930, -150, -20], at: [710, -295, -240], fov: 56, sy: 0.2 } },
  { ch: 'dungeons', f: 0.82, t: 0.69, z: 'prison', eye: [810, -190, -80], at: [680, -302, -250], fov: 48, sx: -0.14,
    p: { eye: [840, -160, -40], at: [680, -302, -250], fov: 56, sy: 0.2 } },

  // Up through the clouds again, and it is night, and there is a city, and its windows are lit.
  { ch: 'exile', f: 0.25, t: 0.8, z: 'city', eye: [860, 225, 200], at: [420, 165, 330], fov: 38, sx: 0.12,
    p: { eye: [990, 280, 160], at: [420, 175, 330], fov: 50, sy: 0.2 } },
  { ch: 'exile', f: 0.75, t: 0.86, z: 'city', eye: [880, 220, 370], at: [420, 165, 330], fov: 38, sx: 0.12,
    p: { eye: [1020, 270, 380], at: [420, 175, 330], fov: 50, sy: 0.2 } },

  // Before morning, home again: the islands with their lamps still on, then first light.
  { ch: 'join', f: 0.2, t: 0.93, z: 'isles', eye: [930, 300, -80], at: [700, 190, -230], fov: 44,
    p: { eye: [960, 330, -60], at: [700, 190, -230], fov: 58, sy: 0.2 } },
  { ch: 'join', f: 0.88, t: 0.975, z: 'isles', eye: [912, 214, -230], at: [780, 195, -262], fov: 40, sy: -0.05,
    p: { eye: [930, 212, -236], at: [806, 197, -262], fov: 56, sy: 0.04 } },
  { ch: 'finale', f: 1, t: 1, z: 'isles', eye: [890, 205, -244], at: [770, 195, -262], fov: 40, sx: 0.06, sy: -0.12,
    p: { eye: [914, 207, -240], at: [806, 197, -262], fov: 56, sy: -0.04 } },
]
