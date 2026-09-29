import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, horizontalDistance, nearestAnchor, standingIsSafe } from './safety.js'
import { assignGait, idleFidget, setNearGoal, tunePathfinder } from './motion.js'
import { pickNextPad, readPads } from './pads.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES } from './playstyle.js'

const { goals, Movements, pathfinder } = pathfinderPkg

export { pickNextPad, readPads } from './pads.js'

export function isAirborne(bot) {
  const vel = bot.entity?.velocity
  const pos = bot.entity?.position
  if (!pos || !vel) return false
  return Math.abs(vel.y) > 0.35 || (!bot.entity.onGround && pos.y > (bot.qaHome?.y ?? pos.y) + 1.4)
}

export function createPadLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  assignGait(bot)

  const pads = readPads(cfg)
  const hopTimeoutMs = cfg.hopTimeoutMs ?? 9000
  const stuckMs = cfg.stuckHopMs ?? 5000
  let running = false
  let lastId = null
  let hopStarted = 0
  let lastLand = 0
  let lastFidget = 0

  function home() {
    return bot.qaHome || nearestAnchor(bot.entity?.position, pads) || bot.entity?.position
  }

  function slimeNear(radius = 8) {
    if (!bot.findBlock) return null
    try {
      return bot.findBlock({
        matching: (block) => block && (block.name === 'slime_block' || block.name === 'honey_block'),
        maxDistance: radius
      })
    } catch {
      return null
    }
  }

  async function walkOnto(dest) {
    if (!dest || !bot.pathfinder) return false
    if (!standingIsSafe(bot, dest.x, dest.y, dest.z) && dest.y < (bot.qaVoidFloorY ?? 40) + 4) {
      note(bot, `skip void pad ${dest.id || ''}`, 'recovering')
      return false
    }
    note(bot, `pad walk ${dest.id || dest.x.toFixed(0)}`, ACTIVITIES.padHop)
    setNearGoal(bot, goals, dest, 1.1)
    hopStarted = Date.now()
    const start = bot.entity.position.clone()
    const deadline = Date.now() + 7000
    while (Date.now() < deadline) {
      if (bot.qaSuspended) return false
      if (isAirborne(bot) || bot.entity.position.y > start.y + 2.2) {
        cancelPath(bot)
        note(bot, 'pad launch', ACTIVITIES.padHop)
        return true
      }
      const here = bot.entity.position
      if (horizontalDistance(here, dest) < 1.4 && Math.abs(here.y - dest.y) < 1.6) {
        bot.setControlState('jump', true)
        setTimeout(() => bot.setControlState('jump', false), 200)
      }
      await sleep(180)
    }
    cancelPath(bot)
    return false
  }

  async function waitLand() {
    const startY = bot.entity.position.y
    const start = Date.now()
    let lastY = startY
    let progressAt = Date.now()
    while (Date.now() - start < hopTimeoutMs) {
      if (bot.qaSuspended) return false
      const pos = bot.entity.position
      if (Math.abs(pos.y - lastY) > 0.6) {
        progressAt = Date.now()
        lastY = pos.y
      }
      if (bot.entity.onGround && !isAirborne(bot) && Date.now() - start > 400) {
        bot.qaHome = { x: pos.x, y: pos.y, z: pos.z }
        lastLand = Date.now()
        note(bot, `pad land @ ${pos.x.toFixed(0)} ${pos.y.toFixed(0)} ${pos.z.toFixed(0)}`, ACTIVITIES.padHop)
        return true
      }
      if (Date.now() - progressAt > stuckMs && isAirborne(bot)) {
        cancelPath(bot)
        note(bot, 'stuck mid-hop — hold', 'stuck')
        bot.qaSuspended = false
        return false
      }
      await sleep(120)
    }
    note(bot, 'hop timeout', 'stuck')
    return false
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(tunePathfinder(bot, applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 3 }), { maxDrop: 3 }))
    }

    if (isAirborne(bot)) {
      bot.qaActivity = ACTIVITIES.padHop
      await waitLand()
      return
    }

    if (Date.now() - lastLand < 900) {
      if (Date.now() - lastFidget > 2000) {
        lastFidget = Date.now()
        idleFidget(bot, ACTIVITIES.padHop)
      }
      return
    }

    const slime = slimeNear(6)
    const dest = pickNextPad(bot.entity.position, pads, lastId, { minHop: 4, maxHop: cfg.maxHop ?? 14 })
    let target = dest
    if (slime && dest && horizontalDistance(slime.position, dest) < 4) {
      target = { id: dest.id, x: slime.position.x + 0.5, y: slime.position.y + 1, z: slime.position.z + 0.5 }
    } else if (!target && slime) {
      target = { id: 'slime', x: slime.position.x + 0.5, y: slime.position.y + 1, z: slime.position.z + 0.5 }
    }
    if (!target) {
      note(bot, 'no pad nearby', 'idle')
      idleFidget(bot, ACTIVITIES.padHop)
      return
    }
    lastId = dest?.id || lastId
    const launched = await walkOnto(target)
    if (launched) {
      await waitLand()
    } else if (Date.now() - hopStarted > hopTimeoutMs) {
      note(bot, 'pad retry other', ACTIVITIES.padHop)
      lastId = target.id
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `pad loop start (${pads.length} pads)`)
    note(bot, 'pad loop start', ACTIVITIES.padHop)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `pad tick: ${err.message}`)
      })
    }, 420)
    bot.once('end', () => clearInterval(handle))
  }
}
