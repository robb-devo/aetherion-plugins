import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, sampleSolidNear } from './safety.js'
import { clickCommands } from './chat.js'
import { closeWindow } from './gui.js'
import { fightStep } from './fight.js'
import { isTalkable, nearestEntity, talkTo, walkTo } from './npc.js'
import { chooseReply } from './quest.js'
import { travel } from './travel.js'
import { note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * The exile on mmo-c: cross with `/exile go` like a player (the story gate decides; a bot that has not earned the
 * ferry is let across by the plugin's fallback road, and /botreport says so), sit out the arrival (sneak skips
 * the cinematic when it allows it), then walk the island, fight what attacks, talk to the people there and answer
 * their dialogs, and finally `/exile return` home. Every crossing is timed in the road telemetry.
 */
export function createExileLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const stayMs = cfg.stayMs ?? 10 * 60_000
  const roam = cfg.roamRadius ?? 32
  const stats = (bot.qaStats = bot.qaStats || {})
  const fight = {}
  let running = false
  let arrivedAt = 0
  let origin = null
  let nextTripAt = 0
  let nextTalkAt = 0
  let pending = []

  bot.on('message', (message) => {
    const commands = clickCommands(message)
    if (commands.length) pending = commands
  })

  async function talk() {
    const npc = nearestEntity(bot, (e) => isTalkable(e, bot), 14)
    nextTalkAt = Date.now() + 40_000 + Math.random() * 50_000
    if (!npc || !(await walkTo(bot, npc.position, { range: 2.5, timeoutMs: 15_000 }))) return
    pending = []
    await talkTo(bot, npc)
    stats.talks = (stats.talks || 0) + 1
    telemetry.count('exile talks')
    note(bot, 'talking on the exile', 'exploring')
    for (let i = 0; i < 4; i++) {
      await sleep(1800)
      if (bot.currentWindow) closeWindow(bot)
      const reply = chooseReply(pending)
      if (!reply) break
      pending = []
      bot.chat(reply)
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 3 }))
    }
    if (bot.qaServer !== 'mmo-c') {
      if (Date.now() < nextTripAt) return
      arrivedAt = 0
      if (await travel(bot, 'mmo-c', { log })) {
        telemetry.count('exile crossings')
      } else {
        nextTripAt = Date.now() + 120_000
      }
      return
    }
    if (!arrivedAt) {
      arrivedAt = Date.now()
      origin = null
    }
    if (bot.game?.gameMode === 'spectator') {
      // The arrival cinematic: sneak is the player's "let me off".
      bot.setControlState('sneak', true)
      setTimeout(() => bot.setControlState('sneak', false), 400)
      note(bot, 'arrival cinematic', 'exploring')
      await sleep(2500)
      return
    }
    if (!origin && bot.entity.onGround) origin = bot.entity.position.clone()
    if (Date.now() - arrivedAt > stayMs) {
      note(bot, 'home from the exile', 'travel')
      if (await travel(bot, 'mmo-r', { log })) await bot.qaEnsureKit?.()
      nextTripAt = Date.now() + (cfg.breakMs ?? 180_000)
      return
    }
    if (bot.currentWindow) {
      closeWindow(bot)
      return
    }
    if (await fightStep(bot, fight, { radius: 14 })) {
      note(bot, 'fighting on the exile', 'exploring')
      return
    }
    if (Date.now() > nextTalkAt) {
      await talk()
      return
    }
    if (!bot.pathfinder.isMoving() && origin) {
      const spot = sampleSolidNear(bot, origin, roam)
      if (spot) bot.pathfinder.setGoal(new goals.GoalNear(spot.x, spot.y, spot.z, 2))
      note(bot, 'exploring the exile', 'exploring')
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'exile loop start')
    note(bot, 'exile loop start', 'exploring')
    const halt = runEvery(bot, 400, tick, (err) => log(bot.stressName, `exile tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}
