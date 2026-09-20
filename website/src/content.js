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
  proxy: 'Velocity',
  discord: 'https://discord.gg/7BWHJaZChb',
  paypalEmail: 'mr.minecraft0604@gmail.com',
  iban: 'DE23 1001 0178 9795 5314 74',
}

export const NAV_LINKS = [
  { href: '#vision', key: 'vision' },
  { href: '#features', key: 'features' },
  { href: '#karten', key: 'maps' },
  { href: '#support', key: 'support' },
]

export const FEATURES = [
  { id: 'skills', span: 'md:col-span-3' },
  { id: 'islands', span: 'md:col-span-3' },
  { id: 'amethyst', span: 'md:col-span-2' },
  { id: 'quests', span: 'md:col-span-2' },
  { id: 'market', span: 'md:col-span-2' },
  { id: 'pads', span: 'md:col-span-3' },
  { id: 'dungeons', span: 'md:col-span-3' },
]

export const MAPS = [
  { id: 'eldervale', title: 'Eldervale', image: '/maps/eldervale.svg', placeholder: true },
  { id: 'amethyst-mines', title: 'Amethyst Mines', image: '/maps/amethyst-mines.svg', placeholder: true },
  { id: 'harbour', title: 'Harbour', image: '/maps/harbour.svg', placeholder: true },
]

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
