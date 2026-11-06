/**
 * The camera's one take. Every key is pinned to a place in the page: chapter `ch`, fraction `f`
 * of that chapter's height. Scrolling walks the camera along a spline through the keys, and the
 * clock runs with it: `t` is the time of day from day.js.
 *
 *   eye, at, fov   where it stands, what it looks at, how wide
 *   sx, sy         lens shift as a fraction of the view (image moves right / up)
 *   p              portrait overrides: a phone gets its own framing, subject above the text
 *
 * Grid coordinates: 1 unit is 1 block. The city's centre is near (180, 130, 193).
 * Isles sit around (820, 190, -260), the prison around (140, -300, 60), far below the clouds.
 */
export const ISLE_ORIGIN = [618, 167, -328]
export const PRISON_ORIGIN = [-60, -360, -40]

export const ROUTE = [
  // Dawn, the fog breaks. Far out, the whole city against the light, title behind its towers.
  { ch: 'hero', f: 0, t: 0.0, solid: 1, eye: [560, 235, 560], at: [168, 178, 190], fov: 36, sx: 0.15, sy: -0.13,
    p: { eye: [640, 270, 640], at: [176, 190, 190], fov: 46, sy: 0.03, ff: 0.62 } },
  { ch: 'hero', f: 0.85, t: 0.03, solid: 1, eye: [556, 205, 500], at: [190, 165, 190], fov: 38, sx: 0.1, sy: -0.06,
    p: { eye: [630, 240, 590], at: [176, 170, 190], fov: 46, sy: 0.05, ff: 0.7 } },

  // Down to the chain that holds the coast. Morning.
  { ch: 'what', f: 0.12, t: 0.07, eye: [540, 118, 300], at: [300, 122, 195], fov: 40, sx: 0.14,
    p: { eye: [590, 140, 320], at: [300, 130, 195], fov: 50, sy: 0.2 } },
  { ch: 'what', f: 0.9, t: 0.105, eye: [505, 112, 262], at: [300, 120, 200], fov: 42, sx: 0.14,
    p: { eye: [560, 130, 280], at: [300, 126, 200], fov: 50, sy: 0.2 } },

  // Along the quay wall, close enough for the stone to matter. People live here.
  { ch: 'talk', f: 0.1, t: 0.13, eye: [452, 128, 280], at: [300, 136, 192], fov: 44, sx: 0.12,
    p: { eye: [500, 150, 300], at: [300, 136, 192], fov: 50, sy: 0.22 } },
  { ch: 'talk', f: 0.9, t: 0.17, eye: [438, 150, 208], at: [280, 142, 186], fov: 46, sx: 0.12,
    p: { eye: [480, 176, 216], at: [280, 140, 186], fov: 52, sy: 0.22 } },

  // Up and over the old town: every job in it.
  { ch: 'skills', f: 0.1, t: 0.2, eye: [392, 196, 252], at: [230, 150, 176], fov: 48, sx: -0.1,
    p: { eye: [430, 236, 276], at: [230, 146, 176], fov: 54, sy: 0.2 } },
  { ch: 'skills', f: 0.9, t: 0.245, eye: [372, 226, 128], at: [205, 150, 170], fov: 48, sx: -0.1,
    p: { eye: [410, 270, 116], at: [205, 146, 170], fov: 54, sy: 0.2 } },

  // Noon. High above the castle, nothing hides: the sky is the chart table.
  { ch: 'chart', f: 0.12, t: 0.27, eye: [250, 282, 42], at: [165, 170, 182], fov: 52,
    p: { eye: [262, 320, 22], at: [165, 170, 182], fov: 62, sy: 0.14 } },
  { ch: 'chart', f: 0.9, t: 0.32, eye: [356, 300, -40], at: [190, 170, 170], fov: 50,
    p: { eye: [386, 340, -60], at: [190, 170, 170], fov: 62, sy: 0.14 } },

  // Across open air to the little islands. Afternoon; a place of your own.
  { ch: 'isle', f: 0.1, t: 0.355, eye: [640, 252, -118], at: [820, 192, -260], fov: 44, focus: true,
    p: { eye: [650, 268, -100], at: [820, 190, -260], fov: 52, sy: 0.22 } },
  { ch: 'isle', f: 0.9, t: 0.41, eye: [770, 236, -130], at: [840, 190, -260], fov: 44, focus: true,
    p: { eye: [780, 260, -110], at: [840, 190, -260], fov: 52, sy: 0.22 } },

  // Golden hour, back at the far end of the city.
  { ch: 'pets', f: 0.12, t: 0.455, eye: [190, 222, -96], at: [52, 192, 28], fov: 44, sx: -0.12,
    p: { eye: [206, 250, -120], at: [52, 188, 28], fov: 54, sy: 0.22 } },
  { ch: 'pets', f: 0.9, t: 0.49, eye: [150, 214, -62], at: [50, 190, 28], fov: 44, sx: -0.12,
    p: { eye: [164, 240, -86], at: [50, 186, 28], fov: 54, sy: 0.22 } },
  { ch: 'loot', f: 0.1, t: 0.51, eye: [10, 262, 110], at: [85, 300, 190], fov: 58, sx: 0.1,
    p: { eye: [-6, 250, 96], at: [85, 300, 190], fov: 66, sy: 0.2 } },
  { ch: 'loot', f: 0.9, t: 0.545, eye: [-40, 190, 120], at: [120, 200, 190], fov: 52, sx: 0.1,
    p: { eye: [-60, 200, 96], at: [120, 200, 190], fov: 62, sy: 0.2 } },

  // Dusk. Under the keel, through the cloud sea, into what is below it.
  { ch: 'dive', f: 0.0, t: 0.565, eye: [-60, 100, 300], at: [150, 70, 200], fov: 56,
    p: { eye: [-70, 110, 320], at: [150, 70, 200], fov: 62 } },
  { ch: 'dive', f: 0.5, t: 0.6, eye: [130, -36, 300], at: [180, 90, 190], fov: 62,
    p: { eye: [130, -36, 300], at: [180, 90, 190], fov: 68 } },
  { ch: 'dive', f: 1, t: 0.635, eye: [360, -210, 330], at: [130, -300, 60], fov: 52,
    p: { eye: [380, -190, 350], at: [130, -300, 60], fov: 62, sy: 0.16 } },

  // The prison: rooms hanging in the dark, walls off, like a doll's house nobody should own.
  { ch: 'prison', f: 0.15, t: 0.645, eye: [300, -190, 240], at: [110, -305, 50], fov: 46, sx: 0.1,
    p: { eye: [330, -160, 270], at: [110, -305, 50], fov: 56, sy: 0.22 } },
  { ch: 'prison', f: 0.9, t: 0.675, eye: [210, -200, 210], at: [80, -312, 40], fov: 48, sx: 0.1,
    p: { eye: [240, -170, 250], at: [80, -312, 40], fov: 56, sy: 0.22 } },
  { ch: 'gnomon', f: 0.2, t: 0.69, eye: [130, -200, 190], at: [50, -315, 30], fov: 50,
    p: { eye: [160, -176, 220], at: [50, -315, 30], fov: 58, sy: 0.2 } },
  { ch: 'gnomon', f: 0.9, t: 0.705, eye: [20, -210, 190], at: [70, -315, 40], fov: 50,
    p: { eye: [30, -190, 220], at: [70, -315, 40], fov: 58, sy: 0.2 } },

  // Up through the clouds again, and it is night, and the windows are lit.
  { ch: 'rise', f: 0.0, t: 0.725, eye: [-140, -150, 330], at: [150, 40, 190], fov: 58,
    p: { eye: [-160, -150, 350], at: [150, 40, 190], fov: 62 } },
  { ch: 'rise', f: 0.6, t: 0.765, eye: [-300, 60, 470], at: [160, 150, 190], fov: 46,
    p: { eye: [-340, 80, 500], at: [160, 150, 190], fov: 52 } },
  { ch: 'rise', f: 1, t: 0.79, eye: [-320, 210, 460], at: [160, 170, 190], fov: 38,
    p: { eye: [-380, 250, 520], at: [160, 170, 190], fov: 46, sy: 0.06 } },
  { ch: 'night', f: 0.3, t: 0.82, eye: [-240, 300, 200], at: [170, 165, 190], fov: 38, sx: 0.1,
    p: { eye: [-300, 340, 200], at: [170, 160, 190], fov: 46, sy: 0.2 } },
  { ch: 'night', f: 0.95, t: 0.875, eye: [-180, 430, -190], at: [170, 160, 190], fov: 38, sx: 0.1,
    p: { eye: [-220, 480, -220], at: [170, 160, 190], fov: 46, sy: 0.2 } },

  // Before morning, all the way round to where we began.
  { ch: 'join', f: 0.15, t: 0.9, eye: [250, 420, -360], at: [180, 170, 190], fov: 36,
    p: { eye: [270, 470, -400], at: [180, 170, 190], fov: 44, sy: 0.3 } },
  { ch: 'join', f: 0.9, t: 0.95, eye: [720, 320, 120], at: [180, 170, 190], fov: 34,
    p: { eye: [800, 360, 140], at: [180, 170, 190], fov: 42, sy: 0.3 } },
  { ch: 'finale', f: 0.1, t: 0.985, solid: 1, eye: [590, 250, 500], at: [172, 178, 190], fov: 36, sy: -0.13,
    p: { eye: [660, 280, 610], at: [176, 188, 190], fov: 46, sy: -0.06, ff: 0.62 } },
  { ch: 'finale', f: 1, t: 1, solid: 1, eye: [560, 235, 560], at: [168, 178, 190], fov: 36, sy: -0.13,
    p: { eye: [640, 270, 640], at: [176, 190, 190], fov: 46, sy: -0.06, ff: 0.62 } },
]

/** Clock index, in page order. `at` is what the clock shows (0 = 06:00, one day = 1). */
export const CHAPTERS = [
  { id: 'hero', at: 0 },
  { id: 'what', at: 0.08 },
  { id: 'talk', at: 0.14 },
  { id: 'skills', at: 0.21 },
  { id: 'chart', at: 0.28 },
  { id: 'isle', at: 0.36 },
  { id: 'pets', at: 0.46 },
  { id: 'loot', at: 0.52 },
  { id: 'prison', at: 0.65 },
  { id: 'gnomon', at: 0.69 },
  { id: 'night', at: 0.82 },
  { id: 'join', at: 0.9 },
  { id: 'finale', at: 0.99 },
]

/** The four starts, where they float. Layout comes from the bake (public/world3d/scenes.json). */
export const ISLE_SPOTS = {
  starter_grove: [684, 188, -260],
  starter_tide: [772, 186, -260],
  starter_quarry: [858, 188, -260],
  starter_guild: [951, 188, -260],
}
