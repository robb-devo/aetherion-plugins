import { createCombatLoop } from './combat.js'
import { createForageLoop, createMiningLoop } from './gather.js'
import { createCatchLoop } from './catch.js'
import { createRoamLoop } from './roam.js'
import { createFishLoop } from './fish.js'
import { createTradeLoop } from './trade.js'
import { createQuestLoop } from './quest.js'
import { createPadLoop } from './pad.js'
import { personalityOf, pickActivity, startActivity } from './personality.js'
import { equipForActivity } from './react.js'
import { jitter, note, sleep, stopBoundLoops } from './util.js'

const LOOP = {
  mine: createMiningLoop,
  mining: createMiningLoop,
  forage: createForageLoop,
  catch: createCatchLoop,
  roam: createRoamLoop,
  combat: createCombatLoop,
  fish: createFishLoop,
  trade: createTradeLoop,
  quest: createQuestLoop,
  pad: createPadLoop
}

export function attachBrain(bot, { config, log, switchable = false }) {
  bot.qaPersonality = bot.qaPersonality || personalityOf(bot.stressName, bot.role)
  bot.qaState = 'idle'
  bot.qaFailCount = 0
  bot.qaFocus = switchable ? startActivity(bot.stressName, bot.role) : bot.role
  bot.qaActivityStarted = Date.now()

  const handle = setInterval(() => {
    tick().catch((err) => log?.(bot.stressName, `brain: ${err.message}`))
  }, jitter(2400, bot.qaPersonality.tickJitter || 0.2))
  bot.qaBrainStop = () => clearInterval(handle)
  bot.once('end', () => {
    try { bot.qaBrainStop() } catch { /* ignore */ }
  })

  if (switchable) {
    setTimeout(() => {
      beginActivity(bot.qaFocus, false).catch((err) => log?.(bot.stressName, `brain start: ${err.message}`))
    }, jitter(1800, 0.3))
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaSwitching) return
    if (!switchable) {
      bot.qaState = bot.qaActivity || 'work'
      if (bot.qaNeedRetarget) {
        bot.qaFailCount = (bot.qaFailCount || 0) + 1
        bot.qaNeedRetarget = false
        note(bot, 'brain recover retarget', 'idle')
      }
      return
    }

    const personality = bot.qaPersonality
    const now = Date.now()
    const dwell = personality.dwellMs * (0.7 + Math.random() * 0.6)
    const failed = (bot.qaFailCount || 0) >= 3
    const idleTooLong = (bot.qaActivity === 'idle' || bot.qaActivity === 'stuck')
      && now - (bot.qaActivityStarted || now) > 12_000
    const timeUp = now - (bot.qaActivityStarted || 0) > dwell

    if (Math.random() < personality.idleChance && bot.qaActivity !== 'idle' && !timeUp) {
      bot.qaState = 'idle'
      note(bot, 'brain idle beat', 'idle')
      await sleep(jitter(1600, 0.4))
      return
    }

    if (failed || idleTooLong || timeUp) {
      const next = pickActivity(personality, bot.qaFocus, failed ? bot.qaFocus : '')
      bot.qaFailCount = 0
      await beginActivity(next, true)
    }
  }

  async function beginActivity(activity, warp) {
    if (!activity || bot.qaSwitching) return
    bot.qaSwitching = true
    bot.qaState = 'decide'
    try {
      stopBoundLoops(bot)
      bot.qaFocus = activity
      equipForActivity(bot, activity)
      if (warp) {
        bot.qaState = 'travel'
        note(bot, `focus ${activity}`, 'pathing')
        try { bot.chat('/botfocus ' + activity) } catch { /* ignore */ }
        await sleep(jitter(2200, 0.25))
        if (bot.entity?.position) {
          bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
        }
      }
      const factory = LOOP[activity]
      if (!factory) {
        note(bot, `unknown activity ${activity}`, 'idle')
        return
      }
      bot.qaActivityStarted = Date.now()
      bot.qaState = 'work'
      note(bot, `start ${activity}`, activity === 'combat' ? 'fighting' : activity)
      const cfg = roleConfig(config, activity)
      factory(bot, cfg, log)()
      log?.(bot.stressName, `brain → ${activity} (${bot.qaPersonality.id})`)
    } catch (err) {
      bot.qaFailCount = (bot.qaFailCount || 0) + 1
      note(bot, `brain fail ${err.message}`, 'recovering')
    } finally {
      bot.qaSwitching = false
    }
  }
}

function roleConfig(config, role) {
  if (!config) return {}
  if (role === 'mine') return config.mine || config.mining || {}
  return config[role] || {}
}
