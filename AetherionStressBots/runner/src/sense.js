const HOSTILE = [
  'zombie', 'husk', 'skeleton', 'stray', 'creeper', 'spider', 'cave_spider',
  'drowned', 'witch', 'pillager', 'phantom', 'enderman', 'slime', 'magma_cube',
  'blaze', 'wither_skeleton', 'hoglin', 'piglin_brute', 'vindicator', 'ravager'
]

const FOOD = new Set([
  'bread', 'cooked_beef', 'cooked_porkchop', 'cooked_chicken', 'cooked_mutton',
  'cooked_rabbit', 'baked_potato', 'golden_apple', 'apple', 'carrot', 'cooked_cod',
  'cooked_salmon', 'sweet_berries', 'cookie', 'pumpkin_pie', 'mushroom_stew',
  'beetroot_soup', 'melon_slice', 'dried_kelp', 'cooked_cod', 'tropical_fish'
])

export function isHostileName(name) {
  const n = String(name || '').toLowerCase()
  if (!n) return false
  if (n.includes('villager') || n.includes('iron_golem') || n.includes('armor_stand')) return false
  if (n.includes('item') || n.includes('experience')) return false
  return HOSTILE.some((key) => n.includes(key))
}

export function entityName(entity) {
  return entity?.username || entity?.name || entity?.displayName || ''
}

export function nearbyPlayers(bot, radius = 8) {
  if (!bot?.entity) return []
  const found = []
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity || entity === bot.entity || entity.type !== 'player') continue
    if (entity.username && entity.username === bot.username) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist <= radius) found.push(entity)
  }
  return found
}

export function nearestHostile(bot, radius, { home = null, leash = null, within = null } = {}) {
  if (!bot?.entity) return null
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity || entity === bot.entity || entity.type === 'player') continue
    const name = String(entityName(entity)).toLowerCase()
    const typed = entity.type === 'mob' || entity.type === 'hostile'
    if (!typed && !isHostileName(name)) continue
    if (name.includes('villager') || name.includes('iron_golem') || name.includes('armor_stand')) continue
    if (name.includes('item') || name.includes('experience')) continue
    if (home && leash != null && within && !within(entity.position, home, leash)) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

export function threatened(bot, radius = 6) {
  return nearestHostile(bot, radius) != null
}

export function isFood(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  if (FOOD.has(n)) return true
  return n.startsWith('cooked_') || n.includes('stew') || n.includes('soup')
}

export function findFood(bot) {
  const items = bot?.inventory?.items?.() || []
  return items.find(isFood) || null
}

export function isGearItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n.includes('sword') || n.includes('pickaxe') || n.includes('axe') || n.includes('shovel')
    || n.includes('hoe') || n.includes('_helmet') || n.includes('chestplate')
    || n.includes('leggings') || n.includes('boots') || n.includes('fishing_rod')
}

export function isWeapon(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n.includes('sword') || n.includes('axe') || n.includes('pickaxe') || n.includes('trident')
}

export function isWornSlot(item) {
  return item && item.slot >= 5 && item.slot <= 8
}

export function isResourceItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  if (isGearItem(item) || isFood(item)) return false
  return n.includes('ore') || n.includes('ingot') || n.includes('coal') || n.includes('cobble')
    || n.includes('log') || n.includes('wood') || n.includes('planks') || n.includes('dirt')
    || n.includes('stone') || n.includes('deepslate') || n.includes('raw_') || n.includes('gem')
    || n.includes('dust') || n.includes('crop') || n.includes('wheat') || n.includes('string')
    || n.includes('bone') || n.includes('leather') || n.includes('rotten') || n.includes('gunpowder')
    || n.includes('spider_eye') || n.includes('arrow')
}

export function countMatching(bot, predicate) {
  let total = 0
  for (const item of bot?.inventory?.items?.() || []) {
    if (predicate(item)) total += item.count || 1
  }
  return total
}

/** Extra gear that is safe to list. Keeps the last weapon. */
export function surplusGearCount(bot) {
  const gear = (bot?.inventory?.items?.() || []).filter((item) => isGearItem(item) && !isWornSlot(item))
  const weapons = gear.filter(isWeapon)
  const extraWeapons = Math.max(0, weapons.length - 1)
  return extraWeapons + (gear.length - weapons.length)
}

export function summarizeInventory(bot, limit = 6) {
  const counts = new Map()
  for (const item of bot?.inventory?.items?.() || []) {
    if (!item?.name) continue
    counts.set(item.name, (counts.get(item.name) || 0) + (item.count || 1))
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([name, count]) => ({ name, count }))
}

export function resourceStack(bot) {
  const items = (bot?.inventory?.items?.() || []).filter(isResourceItem)
  items.sort((a, b) => (b.count || 1) - (a.count || 1))
  return items[0] || null
}

export function surplusGearItem(bot) {
  const gear = (bot?.inventory?.items?.() || []).filter((item) => isGearItem(item) && !isWornSlot(item))
  const weapons = gear.filter(isWeapon)
  if (weapons.length > 1) return weapons[weapons.length - 1]
  const other = gear.find((item) => !isWeapon(item))
  return other || null
}
