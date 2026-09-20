export const SITE = {
  name: 'Aetherion',
  ip: 'play.donnernet.de',
  port: '25565',
  edition: 'Java',
  proxy: 'Velocity',
  discord: 'https://discord.gg/7BWHJaZChb',
  paypalEmail: 'mr.minecraft0604@gmail.com',
  owner: 'Peter',
  iban: 'DE23 1001 0178 9795 5314 74',
}

export const NAV = [
  { href: '#vision', label: 'Vision' },
  { href: '#features', label: 'Features' },
  { href: '#karten', label: 'Karten' },
  { href: '#support', label: 'Support' },
]

export const FEATURES = [
  {
    id: 'skills',
    title: 'Skills',
    en: 'Progress that actually sticks',
    span: 'md:col-span-3',
    body: 'Combat, Mining, Farming, Fishing, Foraging — Level, Loadouts und Manager statt leerer XP-Leisten. Wer grindet, wird stärker. Wer zahlt, nicht automatisch.',
  },
  {
    id: 'islands',
    title: 'Inseln',
    en: 'Farming · Fishing · Foraging · Mining',
    span: 'md:col-span-3',
    body: 'Eldervale und die Satelliten-Inseln: Felder, Angelgründe, Forage Isle, Mining-Insel. Jede Insel hat ihren Loop — und einen Grund, wiederzukommen.',
  },
  {
    id: 'amethyst',
    title: 'Amethyst Mines',
    en: 'Crystal Hollows · The Veins',
    span: 'md:col-span-2',
    body: 'Tiefe Adern, Amethyst-Cluster, The Veins. Mining ist nicht nur Stein klopfen — es ist die Kristallader des Servers.',
  },
  {
    id: 'quests',
    title: 'Quests',
    en: 'NPCs, Kompass, Story-Pads',
    span: 'md:col-span-2',
    body: 'Egon am Hafen, Ledger, Vex, Vorarbeiter. Quests führen durch die Welt — mit Markern, Dialog und Belohnungen, die sich nach Spiel anfühlen.',
  },
  {
    id: 'market',
    title: 'AH & Bazaar',
    en: 'Player economy, not a cash shop',
    span: 'md:col-span-2',
    body: 'Auction House und Bazaar: Spieler handeln untereinander. Coins und Shards sind Server-Währung — kein Shop, der dir den Raid abnimmt.',
  },
  {
    id: 'pads',
    title: 'Jump-Pads',
    en: 'Slime arcs between islands',
    span: 'md:col-span-3',
    body: 'Von Origin nach Eldervale, Forage Isle, Harbour. Pads sind das Rückgrat der Insel-Navigation — kurz, laut, Skyblock.',
  },
  {
    id: 'dungeons',
    title: 'Dungeons',
    en: 'Instances, bosses, the bruise',
    span: 'md:col-span-3',
    body: 'Eigene Instanzen, Bossphasen, Sets, die man sich verdient. Floor-Raids statt Lobby-PvP. Der Dungeon kauft sich nicht — er wird gelaufen.',
  },
]

export const MAPS = [
  {
    id: 'eldervale',
    title: 'Eldervale',
    image: '/maps/eldervale.svg',
    caption:
      'Farming-, Fishing-, Foraging- und Mining-Inseln hinter den Jump-Pads. Das Herz der Skyblock-Schleife.',
    en: 'The island cluster',
    placeholder: true,
  },
  {
    id: 'crystal-hollows',
    title: 'Crystal Hollows',
    image: '/maps/crystal-hollows.svg',
    caption:
      'Amethyst-Minen & The Veins. Kristallhallen, tiefe Adern, Spitzhacke an.',
    en: 'Amethyst mines',
    placeholder: true,
  },
  {
    id: 'harbour',
    title: 'Harbour',
    image: '/maps/harbour.svg',
    caption:
      'Anker Harbour — Egon, Markt und Docks. Hier startet jeder. Hier liegt der Hafen.',
    en: 'Spawn & market docks',
    placeholder: true,
  },
]

export const SHARD_PACKS = [
  {
    id: 'starter',
    euros: 1,
    shards: 1000,
    label: 'Starter',
    hint: 'Kleines Danke',
    featured: false,
  },
  {
    id: 'freund',
    euros: 2.5,
    shards: 3000,
    label: 'Freund',
    hint: 'Besserer Kurs',
    featured: false,
  },
  {
    id: 'patron',
    euros: 5,
    shards: 7500,
    label: 'Patron',
    hint: 'Bester Kurs',
    featured: true,
  },
]

export function paypalUrl({ amount, itemName }) {
  const params = new URLSearchParams({
    cmd: '_donations',
    business: SITE.paypalEmail,
    currency_code: 'EUR',
    amount: String(amount),
    item_name: itemName,
    no_shipping: '1',
    lc: 'de_DE',
  })
  return `https://www.paypal.com/cgi-bin/webscr?${params.toString()}`
}

export function formatEuro(n) {
  return new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: n % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(n)
}

export function formatShards(n) {
  return new Intl.NumberFormat('de-DE').format(n)
}
