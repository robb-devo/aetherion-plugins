import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements } from './safety.js'
import { closeWindow } from './gui.js'
import { isBotPlayer, nearestEntity } from './npc.js'
import { equipMatching, jitter, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const WEAPON = (item) => /_(sword|axe)$/.test(item?.name || '')

/**
 * The hub's Pit: `/pit enter` puts the bot at the red line facing out, a few steps on PvP is live and the Pit
 * hands out a wooden sword and leather if the bot came empty-handed. Pit bots only ever fight other bots
 * (never a person), strafe and jump-crit a little, and walk back in after a death.
 */
export function createPitLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const searchRadius = cfg.searchRadius ?? 26
  const attackMs = cfg.attackIntervalMs ?? 520
  const isFleetBot = (name) => Boolean(bot.qaFleet?.isFleetBot?.(name))
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let enteredAt = 0
  let lastAttack = 0
  let target = null

  bot.on('death', () => {
    enteredAt = 0
    if (running) telemetry.count('pit deaths')
  })
  bot.on('entityDead', (entity) => {
    if (running && target && entity === target) {
      stats.kills = (stats.kills || 0) + 1
      telemetry.count('pit kills')
      note(bot, `pit kill ${entity.username}`, 'pit')
      target = null
    }
  })

  async function enter() {
    enteredAt = Date.now()
    bot.chat('/pit enter')
    await sleep(1800)
    const me = bot.entity?.position
    if (!me) return
    const yaw = bot.entity.yaw
    const steps = 8 + Math.random() * 6
    const x = me.x - Math.sin(yaw) * steps
    const z = me.z - Math.cos(yaw) * steps
    bot.pathfinder.setGoal(new goals.GoalNearXZ(x, z, 2))
    note(bot, 'into the Pit', 'pit')
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (bot.qaServer && bot.qaServer !== 'hub') return
    if (bot.currentWindow) {
      closeWindow(bot)
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 4 }))
    }
    if (!enteredAt || Date.now() - enteredAt > (cfg.reenterMs ?? 120_000)) {
      await enter()
      return
    }
    if (!target || !target.isValid || !bot.entities[target.id]) {
      target = nearestEntity(bot, (e) => isBotPlayer(e, isFleetBot), searchRadius)
    }
    if (!target) {
      if (!bot.pathfinder.isMoving()) {
        const me = bot.entity.position
        const angle = Math.random() * Math.PI * 2
        bot.pathfinder.setGoal(new goals.GoalNearXZ(me.x + Math.cos(angle) * 8, me.z + Math.sin(angle) * 8, 2))
      }
      note(bot, 'looking for a fight', 'pit')
      return
    }
    await equipMatching(bot, WEAPON)
    const dist = bot.entity.position.distanceTo(target.position)
    bot.lookAt(target.position.offset(0, 1.5, 0), true).catch(() => {})
    if (dist > 3) {
      bot.pathfinder.setGoal(new goals.GoalFollow(target, 1.8), true)
      note(bot, `chasing ${target.username}`, 'pit')
      return
    }
    bot.pathfinder.setGoal(null)
    const now = Date.now()
    if (now - lastAttack < jitter(attackMs, 0.25)) return
    lastAttack = now
    if (Math.random() < 0.3) {
      // A jump crit now and then, like everyone in a Pit.
      bot.setControlState('jump', true)
      setTimeout(() => bot.setControlState('jump', false), 250)
      await sleep(320)
    }
    try {
      bot.attack(target)
      stats.hits = (stats.hits || 0) + 1
    } catch {
      /* target left */
    }
    if (Math.random() < 0.4) {
      const side = Math.random() < 0.5 ? 'left' : 'right'
      bot.setControlState(side, true)
      setTimeout(() => bot.setControlState(side, false), 300)
    }
    note(bot, `fighting ${target.username}`, 'pit')
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    enteredAt = 0
    log(bot.stressName, 'pit loop start (bots only)')
    note(bot, 'pit loop start', 'pit')
    const halt = runEvery(bot, 200, tick, (err) => log(bot.stressName, `pit tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      target = null
    }
    return stop
  }
}
