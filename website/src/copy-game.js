/**
 * Names the game itself uses: tool tiers per trade and the boss bars. The wiki's data pages and the
 * home page quote them. Keep them verbatim when the game changes them (AetherionSkills, AetherionBosses).
 */
const en = {
  trades: {
    forage: { name: 'Foraging', tools: ['Kindling Hatchet', 'Windfall Axe', 'The Felling', 'Canopy Cleaver', 'The Worldroot'] },
    farm: { name: 'Farming', tools: ['Furrow Ledger', 'Barnstorm Rake', 'The Haymaker', 'Threshlord Scythe', 'The Verdant'] },
    fish: { name: 'Fishing', tools: ['Nibble Rod', 'Ripple Rod', 'The Keelhaul', 'Abyssal Rod', 'The Leviathan'] },
    mine: { name: 'Mining', tools: ['Mining Pickaxe', 'Iron Mining Pickaxe', 'Diamond Mining Pickaxe', 'Netherite Mining Pickaxe', 'Mythic Mining Pickaxe'] },
    combat: { name: 'Combat', tools: ['Combat Sword', 'Iron Combat Sword', 'Diamond Combat Sword', 'Netherite Combat Sword', 'Mythic Combat Sword'] },
  },
  bosses: [
    { name: 'McNugget', color: 'yellow', line: 'A small chick, once.', phases: ['Cluck', 'Nugget Panic'] },
    { name: 'Hollow Lurker', color: 'purple', line: 'Waits in the dark until it stops waiting.', phases: ['Lurking', 'Frenzy'] },
    { name: 'Bridge Troll', color: 'green', line: 'Wants a toll. Keep the receipt.', phases: ['Stomp', 'Storm'] },
    { name: 'Skuldugery', color: 'white', line: 'Volleys first. Inferno next.', phases: ['Volley', 'Inferno'] },
    { name: 'The Ashen Sheath', color: 'pink', line: 'The sheath remembers every cut.', phases: ['Quiet Steel', 'Falling Blossoms', 'Ashen Moon'] },
    { name: 'Pathwarden', color: 'red', line: 'A toll booth that accepts souls.', phases: ['The Gate'] },
    { name: 'Sir Balthazar', color: 'blue', line: 'Lectures. Hotfixes. Rolls back your progress in person.', phases: ['Lecture', 'Hotfix', 'Rollback'] },
    { name: 'The Eggquelizer', color: 'yellow', line: 'A soundcheck you will feel in your teeth.', phases: ['Soundcheck', 'Headliner', 'Distortion'] },
    { name: 'The Insolvent Wither', color: 'purple', line: 'Audits you. Forecloses. Files for Chapter Eleven.', phases: ['Audit', 'Foreclosure', 'Chapter Eleven'] },
    { name: 'Baron von Wurm', color: 'green', line: 'Nibbles, then owns the quarry.', phases: ['Nibble', 'Quarry', 'Feeding'] },
    { name: 'The Frostbound', color: 'blue', line: 'Boss of the second floor. Bring a coat.', phases: ['Packed', 'Blizzard', 'Hearth'] },
  ],
  dropFrom: 'Dropped by {who}',
}

const de = {
  trades: {
    forage: { name: 'Holzsammeln', tools: ['Kindling Hatchet', 'Windfall Axe', 'The Felling', 'Kronenspalter', 'The Worldroot'] },
    farm: { name: 'Landwirtschaft', tools: ['Furrow Ledger', 'Barnstorm Rake', 'The Haymaker', 'Threshlord Scythe', 'The Verdant'] },
    fish: { name: 'Angeln', tools: ['Nibble Rod', 'Ripple Rod', 'The Keelhaul', 'Abyssal Rod', 'The Leviathan'] },
    mine: { name: 'Bergbau', tools: ['Bergbau-Spitzhacke', 'Eisen-Bergbau-Spitzhacke', 'Diamant-Bergbau-Spitzhacke', 'Netherit-Bergbau-Spitzhacke', 'Mythische Bergbau-Spitzhacke'] },
    combat: { name: 'Kampf', tools: ['Kampf-Schwert', 'Eisen-Kampf-Schwert', 'Diamant-Kampf-Schwert', 'Netherit-Kampf-Schwert', 'Mythisches Kampf-Schwert'] },
  },
  bosses: [
    { name: 'McNugget', color: 'yellow', line: 'Einmal war er ein kleines Küken.', phases: ['Gackern', 'Nugget-Panik'] },
    { name: 'Hollow Lurker', color: 'purple', line: 'Wartet im Dunkeln, bis er nicht mehr wartet.', phases: ['Lauern', 'Raserei'] },
    { name: 'Brückentroll', color: 'green', line: 'Will Maut. Heb die Quittung auf.', phases: ['Stampfen', 'Sturm'] },
    { name: 'Skuldugery', color: 'white', line: 'Erst die Salve. Dann das Inferno.', phases: ['Salve', 'Inferno'] },
    { name: 'The Ashen Sheath', color: 'pink', line: 'Die Scheide erinnert sich an jeden Schnitt.', phases: ['Stiller Stahl', 'Fallende Blüten', 'Aschemond'] },
    { name: 'Pathwarden', color: 'red', line: 'Eine Mautstelle, die Seelen annimmt.', phases: ['Das Tor'] },
    { name: 'Sir Balthazar', color: 'blue', line: 'Doziert. Hotfixt. Setzt deinen Fortschritt persönlich zurück.', phases: ['Vorlesung', 'Hotfix', 'Rollback'] },
    { name: 'The Eggquelizer', color: 'yellow', line: 'Ein Soundcheck, den du in den Zähnen spürst.', phases: ['Soundcheck', 'Headliner', 'Verzerrung'] },
    { name: 'The Insolvent Wither', color: 'purple', line: 'Prüft dich. Pfändet. Meldet Insolvenz an.', phases: ['Prüfung', 'Pfändung', 'Insolvenz'] },
    { name: 'Baron von Wurm', color: 'green', line: 'Knabbert, dann gehört ihm der Steinbruch.', phases: ['Knabbern', 'Steinbruch', 'Fütterung'] },
    { name: 'The Frostbound', color: 'blue', line: 'Boss der zweiten Etage. Nimm eine Jacke mit.', phases: ['Eingepackt', 'Schneesturm', 'Herdfeuer'] },
  ],
  dropFrom: 'Gedroppt von {who}',
}

export const GAME = { en, de }
