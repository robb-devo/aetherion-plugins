export const SITE = {
  name: 'Aetherion',
  /** Public website. www is an alias; canonical is the apex. */
  origin: 'https://donnernet.de',
  canonical: 'https://donnernet.de/',
  www: 'https://www.donnernet.de',
  /** Minecraft Java join address — not the website. Do not reuse for web DNS. */
  ip: 'play.donnernet.de',
  port: '25565',
  edition: 'Java',
  minecraft: '1.21.1',
  proxy: 'Velocity',
  discord: 'https://discord.gg/7BWHJaZChb',
  launcherRepo: 'robb-devo/launcheraetherion',
}

/** Standalone official vanilla — not on Velocity. Status via /api/web/vanilla. */
export const VANILLA_SERVER = {
  name: 'Vanilla',
  edition: 'Java',
  version: '26.3',
  host: '135.181.18.162',
  port: 25571,
  join: '135.181.18.162:25571',
}

export const NAV_LINKS = [
  { href: '/#world', key: 'world' },
  { href: '/#game', key: 'game' },
  { href: '/wiki', key: 'wiki' },
  { href: '/#support', key: 'support' },
  { href: '/#playground', key: 'playground' },
]

/** Floating island in the clouds — the strongest shot we have. */
export const HERO_IMAGE = '/world/eldervale.jpg'
export const HARBOUR_IMAGE = '/world/harbour-spawn.jpg'

/**
 * The voyage on the home page, in the order a new player finds these places.
 * Images live in public/world/ (16:9 JPG). `tone` colours the zone title.
 */
export const WORLD = [
  // harbour-spawn is only 1024×530: `soft` lays fog over it so the softness reads as weather.
  { id: 'harbour', image: '/world/harbour-spawn.jpg', tone: 'text-aether', position: '50% 58%', soft: true },
  { id: 'eldervale', image: '/world/eldervale.jpg', tone: 'text-bell-hi', position: '50% 42%' },
  { id: 'amethyst', image: '/world/amethyst-mines.jpg', tone: 'text-arcane-hi', position: '42% 50%' },
  { id: 'throne', image: '/world/throne-of-ashes.jpg', tone: 'text-ember', position: '50% 62%' },
]

/**
 * Past the fog: stills from beyond the charted isles. Rumour captions only —
 * see docs/PUBLIC_LORE_FOR_WEBSITE.md before adding any.
 */
export const FOG = [
  { id: 'ferry', image: '/world/veil-ferry.jpg', position: '55% 50%' },
  { id: 'fogbreak', image: '/world/exile-fogbreak.jpg', position: '50% 40%' },
  { id: 'market', image: '/world/exile-hofstadt.jpg', position: '50% 60%' },
  { id: 'observatory', image: '/world/exile-observatory.jpg', position: '45% 40%' },
  { id: 'theatre', image: '/world/exile-puppet-theatre.jpg', position: '50% 45%' },
]

export const BELL_IMAGE = '/world/exile-belltower.jpg'

/**
 * The hotbar on the home page. Icons are real textures from the Aetherion
 * resource pack (public/items); `scene` is the place behind the HUD when the
 * slot is held; `rarity` only tints the tooltip title.
 */
export const FEATURES = [
  { id: 'skills', item: 'mining_pickaxe_5', rarity: 'rare', scene: '/world/eldervale.jpg', position: '50% 78%', zoom: 1.35 },
  { id: 'islands', item: 'fishing_rod_5', rarity: 'uncommon', scene: '/world/eldervale.jpg', position: '50% 40%', zoom: 1 },
  { id: 'amethyst', item: 'compacted_diamond_pickaxe', rarity: 'epic', scene: '/world/amethyst-mines.jpg', position: '40% 50%', zoom: 1.05 },
  { id: 'quests', item: 'quest_book', rarity: 'common', scene: '/world/harbour-spawn.jpg', position: '62% 60%', zoom: 1.2 },
  { id: 'dungeons', item: 'ashen_katana', rarity: 'legendary', scene: '/world/throne-of-ashes.jpg', position: '50% 62%', zoom: 1.05 },
  { id: 'market', item: 'charm_shiny', rarity: 'mythic', scene: '/world/harbour-spawn.jpg', position: '18% 40%', zoom: 1.45 },
]

/** Weapons that only drop from bosses. Names are the in-game item names. */
export const BOSS_DROPS = ['ashen_katana', 'gravwell_cleaver', 'resonance_scythe', 'stormcaller_maul', 'aetherblade']

export const BOSS_DROP_NAMES = {
  ashen_katana: 'Ashen Katana',
  gravwell_cleaver: 'Gravwell Cleaver',
  resonance_scythe: 'Resonance Scythe',
  stormcaller_maul: 'Stormcaller Maul',
  aetherblade: 'Aetherblade',
}

/**
 * Wiki pages, in reading order (index rows, previous / next). Each lives at
 * /wiki/<id>; its copy is in copy-wiki.js. `image` is the header art.
 */
export const WIKI_SECTIONS = [
  { id: 'faq', item: 'diamond_booster', image: '/world/harbour-spawn.jpg', position: '50% 55%' },
  { id: 'skills', item: 'mining_pickaxe_5', image: '/world/eldervale.jpg', position: '50% 40%' },
  { id: 'world', item: 'fishing_rod_5', image: '/world/amethyst-mines.jpg', position: '45% 50%' },
  { id: 'dungeons', item: 'ashen_katana', image: '/world/throne-of-ashes.jpg', position: '50% 60%' },
  { id: 'rules', item: 'aetherion_helmet', image: '/world/exile-hofstadt.jpg', position: '50% 50%' },
  { id: 'lore', item: 'quest_book', image: '/world/exile-belltower.jpg', position: '50% 30%' },
].map((section) => ({ ...section, to: `/wiki/${section.id}` }))

/** Shard packs. `crystals` is how many shards the drawn pile shows (not an in-game item). */
export const SHARD_PACKS = [
  { id: 'starter', euros: 1, shards: 1000, featured: false, crystals: 1 },
  { id: 'freund', euros: 2.5, shards: 3000, featured: false, crystals: 2 },
  { id: 'patron', euros: 5, shards: 7500, featured: true, crystals: 3 },
]

export function formatEuro(n, lang = 'en') {
  return new Intl.NumberFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: n % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(n)
}

export function formatShards(n, lang = 'en') {
  return new Intl.NumberFormat(lang === 'de' ? 'de-DE' : 'en-US').format(n)
}
