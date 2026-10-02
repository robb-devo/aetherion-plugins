import { attachBrain } from './brain.js'
import { personalityOf } from './personality.js'
import { note } from './util.js'

export function createGeneralLoop(bot, cfg, log, worldCfg) {
  return function start() {
    bot.qaPersonality = personalityOf(bot.stressName, 'general')
    note(bot, `general ${bot.qaPersonality.id}`, 'idle')
    log(bot.stressName, `general player (${bot.qaPersonality.id})`)
    attachBrain(bot, { config: worldCfg || cfg, log, switchable: true })
  }
}
