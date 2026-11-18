import { createCombatLoop } from './combat.js'
import { createMiningLoop, createForageLoop } from './gather.js'
import { createCatchLoop } from './catch.js'
import { createRoamLoop } from './roam.js'
import { createFishLoop } from './fish.js'
import { createTradeLoop } from './trade.js'
import { createQuestLoop } from './quest.js'
import { createPadLoop } from './pad.js'
import { createFarmLoop } from './farm.js'
import { createMenusLoop } from './menus.js'
import { createCasinoLoop } from './casino.js'
import { createSocialLoop } from './party.js'
import { createPitLoop } from './pit.js'
import { createDungeonLoop } from './dungeon.js'
import { createExileLoop } from './exile.js'
import { createIslandLoop } from './island.js'

/** One loop per thing a player does. Each factory returns start(); start() returns stop(). */
export const FACTORIES = {
  mine: createMiningLoop,
  mining: createMiningLoop,
  forage: createForageLoop,
  catch: createCatchLoop,
  roam: createRoamLoop,
  combat: createCombatLoop,
  fish: createFishLoop,
  trade: createTradeLoop,
  quest: createQuestLoop,
  pad: createPadLoop,
  farm: createFarmLoop,
  menus: createMenusLoop,
  casino: createCasinoLoop,
  social: createSocialLoop,
  pit: createPitLoop,
  dungeon: createDungeonLoop,
  exile: createExileLoop,
  island: createIslandLoop
}

/** The runner config block for an activity ({@code mine} falls back to the old {@code mining} block). */
export function activityConfig(config, id) {
  if (id === 'mining') return config.mining || {}
  if (id === 'mine') return config.mine || config.mining || {}
  return config[id] || {}
}

/**
 * The bot's loop for {@code id}, built once per connection and kept, so a touring bot can stop one job and pick
 * it up again later without stacking chat listeners. {@code overrides} only count the first time.
 */
export function loopFor(bot, id, config, log, overrides = {}) {
  bot.qaLoops = bot.qaLoops || new Map()
  if (!bot.qaLoops.has(id)) {
    const factory = FACTORIES[id]
    if (!factory) throw new Error(`no loop for ${id}`)
    bot.qaLoops.set(id, factory(bot, { ...activityConfig(config, id), ...overrides }, log))
  }
  return bot.qaLoops.get(id)
}
