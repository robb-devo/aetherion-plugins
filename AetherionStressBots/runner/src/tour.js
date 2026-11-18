import pathfinderPkg from 'mineflayer-pathfinder'
import { ask } from './helper.js'
import { applyIslandMovements, cancelPath } from './safety.js'
import { closeWindow } from './gui.js'
import { INSTANCE_PREFIX } from './dungeon.js'
import { HUB, MAIN, reconnectToHub, travel } from './travel.js'
import { jitter, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

const { Movements, pathfinder } = pathfinderPkg

/**
 * What a touring player does. {@code server}: where the step is played (the far steps travel there on their own),
 * {@code stage}: the `/testbot stage` spot that puts the bot on the right isle, {@code leash}/{@code floor}: the
 * runner's own safety while there (the plugin only guards the void for touring bots), {@code minutes}: how long.
 */
export const TOUR_STEPS = {
  mine: { weight: 9, server: MAIN, stage: 'mine', leash: 16, floor: -40, maxDrop: 2, minutes: [3, 6] },
  forage: { weight: 7, server: MAIN, stage: 'forage', leash: 20, floor: 40, maxDrop: 2, minutes: [3, 6] },
  fish: { weight: 6, server: MAIN, stage: 'fish', leash: 14, floor: 40, maxDrop: 1, minutes: [3, 5] },
  catch: { weight: 5, server: MAIN, stage: 'catch', leash: 12, floor: 40, maxDrop: 1, minutes: [2, 4] },
  farm: { weight: 6, server: MAIN, leash: Infinity, floor: -64, maxDrop: 3, minutes: [3, 6] },
  quest: { weight: 6, server: MAIN, stage: 'quest', leash: 14, floor: 40, maxDrop: 2, minutes: [3, 5] },
  trade: { weight: 5, server: MAIN, stage: 'trade', leash: 14, floor: 40, maxDrop: 2, minutes: [2, 4] },
  combat: { weight: 7, server: MAIN, stage: 'combat', leash: 22, floor: 40, maxDrop: 3, minutes: [3, 6] },
  pad: { weight: 3, server: MAIN, stage: 'pad', leash: 72, floor: 40, maxDrop: 3, minutes: [2, 4] },
  roam: { weight: 3, server: MAIN, stage: 'roam', leash: 14, floor: 40, maxDrop: 2, minutes: [2, 3] },
  menus: { weight: 5, server: MAIN, leash: Infinity, floor: -64, maxDrop: 2, minutes: [2, 4] },
  casino: { weight: 2, server: MAIN, leash: Infinity, floor: -64, maxDrop: 2, minutes: [1, 3] },
  dungeon: { weight: 8, server: 'mmo-d', leash: Infinity, floor: -512, maxDrop: 4, minutes: [6, 12] },
  exile: { weight: 7, server: 'mmo-c', leash: Infinity, floor: -512, maxDrop: 3, minutes: [5, 9] },
  island: { weight: 6, server: 'mmo-i', leash: Infinity, floor: -512, maxDrop: 2, minutes: [4, 7] },
  pit: { weight: 4, server: HUB, leash: Infinity, floor: -512, maxDrop: 4, minutes: [2, 4] }
}

/** The far steps run their loops with these settings, so they come home after one go and the tour moves on. */
export const TOUR_OVERRIDES = {
  dungeon: { runsBeforeHome: 1, homeBreakMs: 0, partySize: 3 },
  exile: { stayMs: 5 * 60_000, breakMs: 0 },
  island: { stayMs: 4 * 60_000, breakMs: 0 }
}

/**
 * Weighted pick of the next step: never the same twice in a row, weight 0 (config `general.weights`) turns a step
 * off. {@code server} matters only on the hub: a bot that just logged in there plays the Pit first now and then.
 */
export function pickStep({ last = null, server = MAIN, weights = {}, rng = Math.random, startWith = null } = {}) {
  if (startWith && TOUR_STEPS[startWith]) return startWith
  if (server === HUB && rng() < 0.35 && (weights.pit ?? TOUR_STEPS.pit.weight) > 0) return 'pit'
  const pool = Object.entries(TOUR_STEPS)
    .map(([id, def]) => [id, Math.max(0, Number(weights[id] ?? def.weight) || 0)])
    .filter(([id, weight]) => weight > 0 && id !== last)
  const total = pool.reduce((sum, [, weight]) => sum + weight, 0)
  if (!total) return null
  let roll = rng() * total
  for (const [id, weight] of pool) {
    roll -= weight
    if (roll < 0) return id
  }
  return pool[pool.length - 1][0]
}

/**
 * The general role: a bot that plays the whole network like a person would. It picks a step, gets there by the
 * player roads (or the Pit by logging out and in again, the only road to the lobby), plays it for a few minutes
 * with that step's loop, cleans up (paths, windows, dungeon instance) and picks the next one.
 * {@code loopFor(id, overrides)} hands out the bot's cached loop for a step.
 */
export function createTourLoop(bot, cfg, log, { loopFor }) {
  bot.loadPlugin(pathfinder)
  const weights = cfg.weights || {}
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let step = null
  let last = bot.qaMeta?.lastStep || null
  let restUntil = 0
  let first = true

  function knobs(def) {
    bot.qaLeash = def.leash
    bot.qaVoidFloorY = def.floor
    bot.qaHome = null
    try {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: def.maxDrop }))
    } catch {
      /* no world yet */
    }
  }

  function cleanup() {
    cancelPath(bot)
    if (bot.currentWindow) closeWindow(bot)
    bot.qaOwnWindows = false
    bot.qaDigging = false
    bot.qaGathering = false
    try {
      if (bot.targetDigBlock) bot.stopDigging()
    } catch {
      /* ignore */
    }
  }

  function fail(id, why) {
    telemetry.problem(bot.stressName, `tour ${id}: ${why}`)
    note(bot, `tour ${id} skipped: ${why}`, 'idle')
    bot.qaStep = null
    last = id
    restUntil = Date.now() + 30_000
  }

  async function begin(id) {
    const def = TOUR_STEPS[id]
    bot.qaStep = id
    if (bot.qaMeta) bot.qaMeta.lastStep = id
    note(bot, `tour → ${id}`, 'travel')
    telemetry.count(`tour ${id}`)

    if (def.server === HUB) {
      if (bot.qaServer !== HUB) {
        // Nothing leads back to the lobby: log out, and the next login (through the proxy, on the hub) is the Pit.
        if (bot.qaMeta) bot.qaMeta.startWith = 'pit'
        await reconnectToHub(bot)
        return
      }
    } else {
      if (bot.qaServer === HUB || (def.server === MAIN && bot.qaServer !== MAIN)) {
        if (!(await travel(bot, MAIN, { log }))) {
          fail(id, `could not reach ${MAIN} from ${bot.qaServer || '?'}`)
          return
        }
      }
      if (bot.qaServer === MAIN) await bot.qaEnsureKit?.()
    }

    knobs(def)
    if (def.stage) {
      const staged = await ask(bot, `stage ${def.stage}`, { timeoutMs: 5000 })
      if (!staged?.words.includes('ok')) {
        fail(id, `no "${def.stage}" spot on ${bot.qaServer}`)
        return
      }
      await sleep(jitter(1800, 0.3))
    }

    const [lo, hi] = cfg.minutes?.[id] || def.minutes
    const minutes = lo + Math.random() * (hi - lo)
    const start = loopFor(id, TOUR_OVERRIDES[id] || {})
    step = {
      id,
      def,
      startedAt: Date.now(),
      until: Date.now() + minutes * 60_000,
      reached: bot.qaServer === def.server,
      stop: start()
    }
    stats.tourSteps = (stats.tourSteps || 0) + 1
    log(bot.stressName, `tour: ${id} for ${minutes.toFixed(1)} min`)
  }

  async function finish(reason) {
    const done = step
    step = null
    try {
      done.stop()
    } catch {
      /* already stopped */
    }
    cleanup()
    last = done.id
    note(bot, `tour: ${done.id} done (${reason})`, 'travel')
    if (bot.qaServer === HUB) {
      bot.chat('/pit spawn')
      await sleep(1500)
    } else if (bot.qaServer === 'mmo-d') {
      const where = await ask(bot, 'where', { timeoutMs: 4000 })
      if (String(where?.kv?.world || '').startsWith(INSTANCE_PREFIX)) {
        bot.chat('/dungeon leave')
        await sleep(4000)
      }
    }
    if (bot.qaServer && bot.qaServer !== MAIN) {
      // Back to the crossroads; the next step starts from there.
      await travel(bot, MAIN, { log })
    }
    bot.qaStep = null
    restUntil = Date.now() + jitter(12_000, 0.6)
  }

  async function keep(server) {
    const now = Date.now()
    if (server === step.def.server) step.reached = true
    if (now > step.until) return finish('time')
    if (step.def.server === MAIN) {
      if (server !== MAIN) return finish(`knocked off to ${server}`)
      return
    }
    if (step.def.server === HUB) {
      if (server !== HUB) return finish(`left the hub for ${server}`)
      return
    }
    // Far steps: their loops travel there and come home on their own.
    if (step.reached && server !== step.def.server) return finish('came home')
    if (!step.reached && now - step.startedAt > (cfg.reachMs ?? 150_000)) return finish(`never reached ${step.def.server}`)
  }

  async function tick() {
    if (!bot.entity || bot.qaTravelling) return
    const server = bot.qaServer
    if (!server) return
    if (step) {
      await keep(server)
      return
    }
    if (Date.now() < restUntil) return
    const startWith = first ? bot.qaMeta?.startWith : null
    if (bot.qaMeta) bot.qaMeta.startWith = null
    const id = pickStep({ last, server: first ? server : MAIN, weights, startWith })
    first = false
    if (!id) {
      fail('-', 'every step has weight 0')
      return
    }
    await begin(id)
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'tour start (the whole network)')
    note(bot, 'tour start', 'travel')
    const halt = runEvery(bot, 2000, tick, (err) => log(bot.stressName, `tour tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      if (step) {
        try {
          step.stop()
        } catch {
          /* ignore */
        }
        step = null
      }
      cleanup()
    }
    return stop
  }
}
