import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, wanderOnIsland, withinLeash } from './safety.js'
import { fidget, jitter, note } from './util.js'
import { nearestHostile } from './sense.js'

const { goals, Movements, pathfinder } = pathfinderPkg

export function createCombatLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const baseInterval = cfg.attackIntervalMs ?? 450
  const searchRadius = cfg.searchRadius ?? 28
  const wanderRadius = cfg.wanderRadius ?? 10
  const leash = cfg.leashRadius ?? bot.qaLeash ?? 22

  let running = false
  let lastAttack = 0
  let lastFidget = 0
  let burstLeft = 0
  let burstUntil = 0
  let strafeLeft = false
  let nextStrafe = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function style() {
    return bot.qaPersona?.combatStyle || 'balanced'
  }

  function stopMoveKeys() {
    try {
      bot.setControlState('left', false)
      bot.setControlState('right', false)
      bot.setControlState('back', false)
    } catch {
      /* ignore */
    }
  }

  function retreatHp() {
    const persona = bot.qaPersona
    if (!persona) return 6
    if (persona.combatStyle === 'aggressive') return 4
    if (persona.combatStyle === 'cautious') return 11
    return Math.round((persona.retreatAt ?? 0.35) * 20)
  }

  async function tick() {
    if (!bot.entity || bot.entity.isValid === false || bot.qaSuspended) return
    if (bot.qaEating) {
      stopMoveKeys()
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 3
      }))
    }

    const pad = home()
    const mode = style()
    const radius = mode === 'cautious' ? searchRadius * 0.65 : searchRadius
    const target = nearestHostile(bot, radius, {
      home: pad,
      leash: leash + (mode === 'aggressive' ? 6 : 4),
      within: withinLeash
    })
    const hp = bot.health ?? 20
    if (target && hp <= retreatHp()) {
      stopMoveKeys()
      bot.qaTargetName = target.name || 'mob'
      bot.qaGoalLabel = `backing off ${bot.qaTargetName}`
      note(bot, `retreat ${bot.qaTargetName}`, 'retreating')
      const away = pad || bot.entity.position
      bot.pathfinder.setGoal(new goals.GoalNear(away.x, away.y, away.z, 2))
      return
    }
    if (target) {
      const dist = bot.entity.position.distanceTo(target.position)
      bot.qaTargetName = target.name || 'mob'
      bot.qaGoalLabel = `${mode} vs ${bot.qaTargetName}`
      note(bot, `combat ${bot.qaTargetName}`, 'combat')
      const aim = (bot.qaPersona?.clumsiness ?? 0) * 0.35
      const ox = (Math.random() - 0.5) * aim
      const oz = (Math.random() - 0.5) * aim
      bot.lookAt(target.position.offset(ox, target.height * 0.85, oz), true).catch(() => {})
      const reach = mode === 'cautious' ? 3.4 : mode === 'aggressive' ? 2.2 : 2.6
      const follow = mode === 'cautious' ? 2.8 : mode === 'aggressive' ? 1.15 : 1.6
      if (dist > reach) {
        stopMoveKeys()
        if (mode === 'cautious' && dist > 9 && Math.random() < 0.35) {
          note(bot, 'letting it come', 'combat')
          bot.pathfinder.setGoal(null)
          return
        }
        bot.pathfinder.setGoal(new goals.GoalFollow(target, follow), true)
      } else {
        bot.pathfinder.setGoal(null)
        strafe(mode)
        const now = Date.now()
        const interval = jitter(baseInterval * (bot.qaPersona?.attackMul ?? 1), 0.35)
        if (mode === 'burst') {
          if (now < burstUntil) return
          if (burstLeft <= 0) {
            burstLeft = 2 + Math.floor(Math.random() * 3)
            burstUntil = 0
          }
        }
        if (now - lastAttack >= interval) {
          lastAttack = now
          const miss = Math.random() < (bot.qaPersona?.clumsiness ?? 0) * 0.18
          try {
            if (miss) bot.swingArm('right', true)
            else bot.attack(target)
          } catch {
            // entity may despawn mid-swing
          }
          if (mode === 'burst') {
            burstLeft -= 1
            if (burstLeft <= 0) burstUntil = now + 700 + Math.floor(Math.random() * 1100)
          }
        }
      }
      return
    }

    stopMoveKeys()
    bot.qaTargetName = ''
    bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
    bot.qaGoalLabel = bot.qaActivity === 'pathing' ? 'looking for a fight' : 'waiting on the waste'
    if (!bot.pathfinder.isMoving()) {
      const mul = bot.qaPersona?.wanderMul ?? 1
      wanderOnIsland(bot, pad, Math.min(12, wanderRadius * mul), goals)
    }
    if (Date.now() - lastFidget > 5000 + Math.random() * 4000) {
      lastFidget = Date.now()
      fidget(bot, bot.qaActivity)
    }
  }

  function strafe(mode) {
    if (mode !== 'strafe') {
      stopMoveKeys()
      return
    }
    const now = Date.now()
    if (now < nextStrafe) return
    strafeLeft = !strafeLeft
    nextStrafe = now + 380 + Math.floor(Math.random() * 520)
    try {
      bot.setControlState('left', strafeLeft)
      bot.setControlState('right', !strafeLeft)
    } catch {
      /* ignore */
    }
  }

  return function start() {
    if (running) return
    running = true
    if (bot.entity?.position && !bot.qaHome) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    const mode = bot.qaPersona?.combatStyle || 'balanced'
    log(bot.stressName, `combat loop start (${mode})`)
    note(bot, `combat loop start ${mode}`, 'fighting')
    const handle = setInterval(() => {
      tick().catch((err) => log(bot.stressName, `combat tick: ${err.message}`))
    }, jitter(250, 0.15))
    bot.once('end', () => {
      clearInterval(handle)
      try {
        bot.setControlState('left', false)
        bot.setControlState('right', false)
        bot.setControlState('back', false)
      } catch { /* ignore */ }
    })
  }
}
