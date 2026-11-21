/**
 * Login names. Every role has its own little crew of German puns, so a name in chat already says what the bot is
 * up to (Kumpel_Kalle digs, Fischers_Fritz fishes, Kerker_Kurt runs dungeons). The players who tour the whole
 * network are the Testbot_ family. Names are stable per role slot: the same slot comes back with the same name,
 * coins, skills and island, so a test never litters the server with fresh accounts.
 *
 * Minecraft names: 3–16 characters, letters, digits and _ only (no umlauts: ue, ae, oe, ss).
 * `names.style: "qa"` in the runner config brings back QaMine01-style names.
 */

export const NAME_POOLS = {
  mine: ['Kumpel_Kalle', 'Stollen_Steffi', 'Hacke_Heinz', 'Erzbert', 'Glueckauf_Gabi', 'Schacht_Schorsch', 'Kohlen_Konny', 'Pickel_Paule'],
  forage: ['Axel_Schweiss', 'Holzhacker_Hans', 'Borken_Bernd', 'Ast_Anni', 'Specht_Sepp', 'Rinden_Rita', 'Kiefern_Kurt', 'Wurzel_Waltraud'],
  catch: ['Kugel_Kai', 'Netz_Norbert', 'Fang_Franzi', 'Schnapp_Sabine', 'Kescher_Kuno', 'Fangmich_Fanny', 'Pfoten_Peter', 'Fix_Fiete'],
  roam: ['Wander_Willi', 'Bummel_Berta', 'Latsch_Lutz', 'Trampel_Tanja', 'Spazier_Siggi', 'Schlender_Uwe', 'Umweg_Udo', 'Rumtreib_Rolf'],
  combat: ['Haudrauf_Harald', 'Kloppe_Klaus', 'Raufbold_Ralf', 'Keulen_Kerstin', 'Schwert_Svenja', 'Pruegel_Paul', 'Wumms_Wolfgang', 'Kampf_Kuddel'],
  fish: ['Fischers_Fritz', 'Hering_Horst', 'Kutter_Kalle', 'Forellen_Frieda', 'Wurm_Walter', 'Brassen_Britta', 'Aal_Achim', 'Karpfen_Karl'],
  trade: ['Feilsch_Fred', 'Taler_Theo', 'Rabatt_Rudi', 'Pfennig_Paula', 'Schnaeppchen_Jo', 'Kassen_Kai', 'Wucher_Willi', 'Basar_Bea'],
  quest: ['Quassel_Quentin', 'Plauder_Paula', 'Neugier_Nina', 'Frag_Franz', 'Sabbel_Sascha', 'Laber_Lothar', 'Tratsch_Trudi', 'Schnack_Sven'],
  pad: ['Hupf_Hubert', 'Flummi_Flo', 'Spring_Sprotte', 'Hops_Hanne', 'Katapult_Kurt', 'Sprungfeder_Sue', 'Hopsasa_Hilde', 'Trampolin_Tom'],
  general: [
    'Testbot_Toni', 'Testbot_Tina', 'Testbot_Tobi', 'Testbot_Trixi', 'Testbot_Torsten', 'Testbot_Tanja',
    'Testbot_Timo', 'Testbot_Thea', 'Testbot_Till', 'Testbot_Tamara', 'Testbot_Theo', 'Testbot_Tilda'
  ],
  farm: ['Bauer_Bernd', 'Ruebe_Rudi', 'Kartoffel_Klaus', 'Weizen_Wally', 'Moehren_Moni', 'Kuerbis_Kuno'],
  menus: ['Klick_Klaus', 'Menue_Manni', 'Knopf_Kathi', 'Blaetter_Bodo'],
  casino: ['Zocker_Zoe', 'Jackpot_Jens', 'Gluecks_Gustav', 'Einarm_Erna'],
  social: ['Kumpel_Kurti', 'Freundin_Frida', 'Gesellig_Gerd', 'Party_Paulchen', 'Kontakt_Karla', 'Rudel_Rudi'],
  pit: ['Pit_Bulli', 'Knock_Knut', 'Faust_Fiona', 'Rempel_Rainer', 'Ringel_Ronny', 'Boxer_Bodo', 'Watsch_Walter', 'Ellbogen_Elke'],
  dungeon: ['Kerker_Kurt', 'Gruft_Gerda', 'Verlies_Vera', 'Fackel_Fritzi', 'Kellerkind_Kai', 'Gitter_Gitte', 'Moder_Manfred', 'Spinnweb_Sina'],
  exile: ['Verbannt_Bernd', 'Exil_Erwin', 'Faehre_Frieda', 'Nebel_Norbert', 'Heimweh_Heike', 'Ausreiss_Armin'],
  island: ['Insel_Ingo', 'Kokos_Klaus', 'Strand_Susi', 'Palmen_Paul']
}

/** QaMine01-style prefixes (names.style "qa", and the plugin's legacy prefix match). */
export const QA_PREFIXES = {
  mine: 'QaMine',
  forage: 'QaForage',
  catch: 'QaCatch',
  roam: 'QaRoam',
  combat: 'QaCombat',
  fish: 'QaFish',
  trade: 'QaTrade',
  quest: 'QaQuest',
  pad: 'QaPad',
  general: 'QaGeneral',
  farm: 'QaFarm',
  menus: 'QaMenus',
  casino: 'QaCasino',
  social: 'QaSocial',
  pit: 'QaPit',
  dungeon: 'QaDungeon',
  exile: 'QaExile',
  island: 'QaIsland',
  mining: 'StressM'
}

const VALID = /^[A-Za-z0-9_]{3,16}$/

export function validName(name) {
  return typeof name === 'string' && VALID.test(name)
}

/**
 * Login name for slot {@code index} (from 1) of a role. Past the end of the pool the names get a number
 * (Kumpel_Kalle2 …), trimmed to 16 characters.
 */
export function botName(role, index, { style = 'german', prefixes = {} } = {}) {
  const n = Math.max(1, Math.floor(Number(index) || 1))
  const pool = NAME_POOLS[role]
  if (style !== 'qa' && role !== 'mining' && pool && pool.length) {
    const base = pool[(n - 1) % pool.length]
    const round = Math.floor((n - 1) / pool.length)
    if (round === 0) return base
    const suffix = String(round + 1)
    return base.slice(0, 16 - suffix.length) + suffix
  }
  const prefix = prefixes[role] || QA_PREFIXES[role] || 'QaBot'
  return `${prefix}${String(n).padStart(2, '0')}`.slice(0, 16)
}

/** Which slot a name holds, given its role (reverse of botName); 0 when it is not one of that role's names. */
export function slotOf(role, name, opts = {}) {
  for (let i = 1; i <= 200; i++) {
    if (botName(role, i, opts) === name) return i
  }
  return 0
}

const POOL_NAMES = new Set(Object.values(NAME_POOLS).flat().map((n) => n.toLowerCase()))

/**
 * True for any name this runner ever hands out: the QaMine01 / StressM01 families and every pool name, also the
 * numbered ones past the end of a pool (Kumpel_Kalle2). Used where a bot must only deal with other bots: buying
 * listings, Pit fights, party and friend invites.
 */
export function isBotName(name) {
  if (typeof name !== 'string' || !name) return false
  if (/^(qa|stress)/i.test(name)) return true
  const lower = name.toLowerCase()
  if (POOL_NAMES.has(lower)) return true
  const base = lower.replace(/\d+$/, '')
  if (base === lower || lower.length - base.length > 3) return false
  if (POOL_NAMES.has(base)) return true
  // Past the end of a pool a long name is cut to make room for its number (16 characters in all).
  if (lower.length !== 16) return false
  for (const pooled of POOL_NAMES) {
    if (pooled.length > base.length && pooled.startsWith(base)) return true
  }
  return false
}
