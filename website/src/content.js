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

/**
 * The standalone vanilla server. Its address is not kept in this repo: the status API can send it as
 * `join`, or a deploy can set VITE_VANILLA_JOIN at build time. Without either, its row stays hidden.
 */
export const VANILLA_SERVER = {
  name: 'Vanilla',
  edition: 'Java',
  version: '26.3',
  join: import.meta.env.VITE_VANILLA_JOIN || '',
}

/** Weapons that only drop from bosses (wiki). Ids are the in-game item ids. */
export const BOSS_DROPS = ['ashen_katana', 'gravwell_cleaver', 'bridged_axe', 'skuldugery_shortbow', 'warped_blade']

/**
 * Wiki pages, in reading order. Each lives at /wiki/<id>; its copy is in copy-wiki.js.
 * `image` is the header art, a still from the live world.
 */
export const WIKI_SECTIONS = [
  { id: 'faq', item: 'diamond_booster', image: 'world/harbour-spawn.jpg', position: '50% 55%' },
  { id: 'skills', item: 'mining_pickaxe_5', image: 'world/eldervale.jpg', position: '50% 40%' },
  { id: 'world', item: 'fishing_rod_5', image: 'world/amethyst-mines.jpg', position: '45% 50%' },
  { id: 'dungeons', item: 'ashen_katana', image: 'world/throne-of-ashes.jpg', position: '50% 60%' },
  { id: 'island', item: 'quest_book', image: 'world/chart.webp', position: '50% 50%' },
  { id: 'bosses', item: 'gravwell_cleaver', image: 'world/throne-of-ashes.jpg', position: '50% 60%' },
  { id: 'pets', item: 'catch_sphere_legendary', image: 'world/eldervale.jpg', position: '50% 40%' },
  { id: 'gear', item: 'mining_pickaxe_4', image: 'world/amethyst-mines.jpg', position: '45% 50%' },
  { id: 'rules', item: 'aetherion_helmet', image: 'world/exile-hofstadt.jpg', position: '50% 50%' },
  { id: 'lore', item: 'recipe_book', image: 'world/exile-belltower.jpg', position: '50% 30%' },
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
