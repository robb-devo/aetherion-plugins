/**
 * The home page's words: one line and a few sentences per frame of the camera, nothing more.
 * Depth lives in the wiki. Numbers here are the game's own (docs/PROJECT_STATE.md).
 */
const en = {
  hero: {
    pitch: 'A whole MMO, built inside Minecraft.',
    loading: 'Generating terrain',
    versionLine: 'Java Edition {version}',
  },
  about: {
    title: 'Nothing here is off the shelf.',
    body: 'You still chop, mine, fish and fight with your own two hands. Around that stands a world written from nothing: skills, quests, dungeons, bosses, a player economy and a story that takes its time.',
    runIn: 'Sixty skills. Fourteen districts. Forty quests. More than seventy creatures to catch. And nothing that makes you stronger is for sale.',
  },
  jobs: {
    title: 'Every job is a small game.',
    body: 'A tree has a rhythm here. So does a wheat field, a fishing line and a vein of ore. You get good, your tools get better, and the rare stuff starts to show up.',
    more: 'All sixty skills',
  },
  world: {
    title: 'One island. You will know it by heart.',
    body: 'Fourteen districts, fifty-four landmarks, seven bells you can ring and eight giant mushrooms that double as fast travel. Some evenings the island does something on its own: a Lantern Festival, an Aurora, a Starfall you can wish on.',
    plate: 'The main island, as charted',
    more: 'Open the chart',
  },
  isle: {
    title: 'At level 20, you get your own island.',
    body: 'Pick one of four starts. Then it is yours: quarries with minions, mills, forges, workshops. At level 75, a guild can raise a hall and a beacon of its own.',
  },
  pets: {
    title: 'More than seventy things that will follow you home.',
    body: 'Throw a sphere. They level up, fight next to you and feed your stats, from a wolf to a sack of potatoes. One catch in 4,096 is shiny.',
    // one entry of the in-game Aetherlex, word for word (src/data/pets.js)
    card: { name: 'Sack of Potatoes', note: 'A burlap conspiracy. Contains potatoes, denial, and a union pamphlet.', rarity: 'mythic' },
    more: 'The whole Aetherlex',
  },
  dungeons: {
    title: 'Under the clouds, the Warden\'s Prison.',
    body: 'Instanced floors, you and your friends, nobody else. Every run deals its rooms from a deck of set pieces, so you never quite know the way. Boss weapons drop from bosses, and only from bosses.',
    more: 'How dungeons work',
  },
  exile: {
    title: 'Past the fog, there is a city.',
    body: 'You start at the harbour, on the other side of the fog. Most people will not see this city for a long time. The windows are lit. Nobody will tell you why.',
    more: 'Read the rumours',
  },
  join: {
    title: 'Three steps to the pier.',
    steps: [
      { title: 'Get the launcher', body: 'For Windows. It installs the Aetherion pack for you. Your own Java {version} client works too.' },
      { title: 'Add the server', body: 'Multiplayer, Add Server, then paste the address.' },
      { title: 'Find Egon on the pier', body: 'He has your first quest. Everything after that is up to you.' },
    ],
    more: 'Also here',
    vanilla: 'A plain vanilla server, for when you want one.',
    playground: 'Your own free server, in a minute.',
    wiki: 'The wiki: questions, skills, rumours.',
    discord: 'Discord, where the bugs and the jokes go.',
    support: 'How support works, and why it buys nothing.',
  },
  finale: {
    title: 'See you at the harbour.',
  },
}

const de = {
  hero: {
    pitch: 'Ein ganzes MMO, gebaut in Minecraft.',
    loading: 'Terrain wird generiert',
    versionLine: 'Java Edition {version}',
  },
  about: {
    title: 'Nichts hier ist von der Stange.',
    body: 'Du hackst, gräbst, angelst und kämpfst weiter mit deinen eigenen zwei Händen. Drumherum steht eine Welt, die von null an geschrieben wurde: Skills, Quests, Dungeons, Bosse, eine Spielerwirtschaft und eine Geschichte, die sich Zeit lässt.',
    runIn: 'Sechzig Skills. Vierzehn Bezirke. Vierzig Quests. Mehr als siebzig Kreaturen zum Fangen. Und nichts, was dich stärker macht, ist käuflich.',
  },
  jobs: {
    title: 'Jeder Job ist ein kleines Spiel.',
    body: 'Ein Baum hat hier einen Rhythmus. Ein Weizenfeld auch, eine Angelschnur und eine Erzader ebenso. Du wirst besser, dein Werkzeug wird besser, und dann tauchen die seltenen Sachen auf.',
    more: 'Alle sechzig Skills',
  },
  world: {
    title: 'Eine Insel. Du wirst sie auswendig kennen.',
    body: 'Vierzehn Bezirke, vierundfünfzig Wahrzeichen, sieben Glocken zum Läuten und acht Riesenpilze, die zugleich Schnellreise sind. Manche Abende macht die Insel etwas von selbst: ein Laternenfest, eine Aurora, ein Sternenfall, bei dem du dir etwas wünschen kannst.',
    plate: 'Die Hauptinsel, wie sie vermessen wurde',
    more: 'Die Karte öffnen',
  },
  isle: {
    title: 'Ab Level 20 gehört dir eine eigene Insel.',
    body: 'Such dir einen von vier Anfängen aus. Dann gehört sie dir: Steinbrüche mit Minions, Mühlen, Schmieden, Werkstätten. Ab Level 75 kann eine Gilde ihre eigene Halle und ihr eigenes Leuchtfeuer errichten.',
  },
  pets: {
    title: 'Mehr als siebzig Dinge, die dir nach Hause folgen.',
    body: 'Wirf eine Sphäre. Sie leveln, kämpfen neben dir und füttern deine Werte, vom Wolf bis zum Kartoffelsack. Einer von 4.096 Fängen ist shiny.',
    card: { name: 'Kartoffelsack', note: 'Eine Verschwörung aus Jute. Enthält Kartoffeln, Verdrängung und ein Gewerkschaftsflugblatt.', rarity: 'mythic' },
    more: 'Der ganze Aetherlex',
  },
  dungeons: {
    title: 'Unter den Wolken liegt das Gefängnis des Wärters.',
    body: 'Instanzierte Etagen, du und deine Freunde, sonst niemand. Jeder Run teilt seine Räume aus einem Deck von Versatzstücken aus, also kennst du den Weg nie ganz. Boss-Waffen droppen von Bossen, und nur von Bossen.',
    more: 'So funktionieren Dungeons',
  },
  exile: {
    title: 'Hinter dem Nebel liegt eine Stadt.',
    body: 'Du fängst am Hafen an, auf der anderen Seite des Nebels. Die meisten werden diese Stadt lange nicht zu sehen bekommen. In den Fenstern brennt Licht. Niemand wird dir sagen, warum.',
    more: 'Die Gerüchte lesen',
  },
  join: {
    title: 'Drei Schritte bis zum Pier.',
    steps: [
      { title: 'Launcher holen', body: 'Für Windows. Er installiert das Aetherion-Paket für dich. Dein eigener Java-{version}-Client geht auch.' },
      { title: 'Server hinzufügen', body: 'Multiplayer, Server hinzufügen, dann die Adresse einfügen.' },
      { title: 'Egon auf dem Pier finden', body: 'Er hat deine erste Quest. Alles danach liegt an dir.' },
    ],
    more: 'Außerdem hier',
    vanilla: 'Ein normaler Vanilla-Server, wenn dir danach ist.',
    playground: 'Dein eigener Gratis-Server, in einer Minute.',
    wiki: 'Das Wiki: Fragen, Skills, Gerüchte.',
    discord: 'Discord, da landen die Bugs und die Witze.',
    support: 'Wie Support funktioniert und warum er nichts kauft.',
  },
  finale: {
    title: 'Wir sehen uns am Hafen.',
  },
}

export const HOME = { en, de }
