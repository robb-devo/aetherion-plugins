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
  paypalEmail: 'mr.minecraft0604@gmail.com',
  iban: 'DE23 1001 0178 9795 5314 74',
}

export const NAV_LINKS = [
  { href: '/#features', key: 'features' },
  { href: '/#world', key: 'world' },
  { href: '/#playground', key: 'playground' },
  { href: '/#support', key: 'support' },
]

/** Icons are real textures from the Aetherion resource pack (public/items). */
export const FEATURES = [
  { id: 'skills', item: 'mining_pickaxe_5' },
  { id: 'islands', item: 'fishing_rod_5' },
  { id: 'amethyst', item: 'compacted_diamond_pickaxe' },
  { id: 'quests', item: 'quest_book' },
  { id: 'dungeons', item: 'ashen_katana' },
  { id: 'market', item: 'charm_shiny' },
]

/**
 * Images live in public/world/. Places without one show a "screenshot coming
 * soon" slot — drop a 16:9 JPG in and set `image`.
 */
export const WORLD = [
  { id: 'harbour', image: '/world/harbour-spawn.jpg', item: 'fishing_rod_5' },
  { id: 'eldervale', image: '/world/eldervale.jpg', item: 'farming_hoe_5' },
  { id: 'amethyst', image: '/world/amethyst-mines.jpg', item: 'charm_mining_3' },
  { id: 'throne', image: '/world/throne-of-ashes.jpg', item: 'ashen_katana' },
]

export const HERO_IMAGE = '/world/harbour-spawn.jpg'

export const SHARD_PACKS = [
  { id: 'starter', euros: 1, shards: 1000, featured: false },
  { id: 'freund', euros: 2.5, shards: 3000, featured: false },
  { id: 'patron', euros: 5, shards: 7500, featured: true },
]

export function paypalUrl({ amount, itemName, lang = 'en' }) {
  const params = new URLSearchParams({
    cmd: '_donations',
    business: SITE.paypalEmail,
    currency_code: 'EUR',
    amount: String(amount),
    item_name: itemName,
    no_shipping: '1',
    lc: lang === 'de' ? 'de_DE' : 'en_US',
  })
  return `https://www.paypal.com/cgi-bin/webscr?${params.toString()}`
}

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
