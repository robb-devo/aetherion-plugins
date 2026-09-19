import pathfinderPkg from 'mineflayer-pathfinder'

const { goals, Movements, pathfinder } = pathfinderPkg

export function createMiningLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const searchRadius = cfg.searchRadius ?? 20
  const digTimeoutMs = cfg.digTimeoutMs ?? 12_000
  const oreSet = new Set((cfg.ores || []).map((s) => s.toLowerCase()))

  let running = false
  let busy = false
  let home = null

  async function tick() {
    if (!bot.entity || busy) return
    if (!home) home = bot.entity.position.clone()

    if (!bot.pathfinder.movements) {
      const movements = new Movements(bot)
      movements.allowSprinting = true
      movements.canDig = true
      bot.pathfinder.setMovements(movements)
    }

    // Dump junk so inventory never soft-locks digs
    if (inventoryAlmostFull(bot)) {
      tossJunk(bot)
    }

    const block = findOre(bot, oreSet, searchRadius)
    if (!block) {
      if (!bot.pathfinder.isMoving()) {
        const angle = Math.random() * Math.PI * 2
        const dist = 3 + Math.random() * 10
        const x = home.x + Math.cos(angle) * dist
        const z = home.z + Math.sin(angle) * dist
        bot.pathfinder.setGoal(new goals.GoalNear(x, home.y, z, 2))
      }
      return
    }

    busy = true
    try {
      const dist = bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5))
      if (dist > 3.2) {
        bot.pathfinder.setGoal(new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2))
        await waitUntil(() => bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= 3.2, 8000)
      }
      bot.pathfinder.setGoal(null)
      await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
      await Promise.race([
        bot.dig(block),
        sleep(digTimeoutMs).then(() => {
          throw new Error('dig timeout')
        })
      ])
    } catch (err) {
      // Block gone / path fail — continue
      if (!String(err.message || err).includes('dig timeout')) {
        // quiet
      }
      try { bot.stopDigging() } catch { /* ignore */ }
    } finally {
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    home = bot.entity?.position?.clone() ?? null
    log(bot.stressName, 'mining loop start')
    const handle = setInterval(() => {
      tick().catch((err) => log(bot.stressName, `mining tick: ${err.message}`))
    }, 400)
    bot.once('end', () => clearInterval(handle))
  }
}

function findOre(bot, oreSet, radius) {
  const origin = bot.entity.position
  let best = null
  let bestDist = radius
  const r = Math.ceil(radius)
  for (let dx = -r; dx <= r; dx++) {
    for (let dy = -6; dy <= 6; dy++) {
      for (let dz = -r; dz <= r; dz++) {
        const pos = origin.offset(dx, dy, dz).floored()
        const dist = origin.distanceTo(pos.offset(0.5, 0.5, 0.5))
        if (dist > radius || dist >= bestDist) continue
        const block = bot.blockAt(pos)
        if (!block || !block.name) continue
        if (!oreSet.has(block.name.toLowerCase())) continue
        // Prefer real ores over filler stone
        const prefer = block.name.includes('ore') ? -2 : 0
        const score = dist + prefer
        if (score < bestDist) {
          best = block
          bestDist = score
        }
      }
    }
  }
  return best
}

function inventoryAlmostFull(bot) {
  const empty = bot.inventory.slots.filter((s) => s == null).length
  return empty < 4
}

function tossJunk(bot) {
  for (const item of bot.inventory.items()) {
    const n = item.name
    if (n.includes('cobble') || n.includes('dirt') || n.includes('gravel') || n === 'stone' || n.includes('deepslate')) {
      bot.tossStack(item).catch(() => {})
    }
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function waitUntil(predicate, timeoutMs) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const id = setInterval(() => {
      if (predicate()) {
        clearInterval(id)
        resolve()
      } else if (Date.now() - start > timeoutMs) {
        clearInterval(id)
        reject(new Error('wait timeout'))
      }
    }, 200)
  })
}
