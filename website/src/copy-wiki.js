/**
 * Wiki pages, EN + DE. Spread into copy.js → wiki.pages, so components still
 * read everything through `copy`.
 *
 * Blocks (rendered by components/wiki/Blocks.jsx):
 *   'text'                      paragraph; supports **bold**, `code`, [label](/path or {discord})
 *   { h3 }                      sub-heading
 *   { list: [...] }             bullet list
 *   { steps: [...] }            numbered list (only for real sequences)
 *   { qa: [{ q, a: [...] }] }   question + answer blocks
 *   { note }                    bronze side note
 *   { tale: { who, text } }     a rumour, in the chronicle voice
 *   { join: true }              server address + launcher + Discord
 *   { drops: true }             boss weapons row (content.js → BOSS_DROPS)
 *   { image, alt }              a screenshot from public/world
 *   { items: [...] }            a row of item icons from public/items
 *
 * Section `item` / `image` are optional. Placeholders: {ip} {version} {discord}.
 */

const en = {
  faq: {
    title: 'FAQ',
    lead: 'The questions everyone asks in their first ten minutes. Answered once, here.',
    sections: [
      {
        id: 'join',
        title: 'Getting in',
        blocks: [
          { join: true },
          {
            qa: [
              {
                q: 'How do I join?',
                a: [
                  { steps: ['Open Minecraft **Java Edition {version}**.', 'Multiplayer, then Add Server.', 'Enter `{ip}` and join.'] },
                  'You arrive at Anker Harbour. Egon is waiting on the pier. Talk to him and the first hour takes care of itself.',
                ],
              },
              {
                q: 'Which Minecraft version do I need?',
                a: ['Java Edition **{version}**. Aetherion is a Java server, so Bedrock (consoles, phones, the Windows Bedrock app) cannot join.'],
              },
              {
                q: 'Do I need the launcher?',
                a: [
                  'No, but it is the easiest way in. The launcher is for Windows and installs the Aetherion pack for you, so items, menus and sounds look the way they should.',
                  'Prefer your own client? Any clean Java {version} install works. If the server offers its resource pack, accept it: a lot of Aetherion lives in there.',
                ],
              },
              {
                q: 'I am on Mac or Linux.',
                a: ['The launcher is Windows-only for now. Use the official Minecraft launcher with Java {version} and add the server by hand. Same world, same progress.'],
              },
              {
                q: 'Does it cost anything?',
                a: ['No. Aetherion is free to play. Support exists, it is optional, and it does not make you stronger. See Shards below.'],
              },
            ],
          },
        ],
      },
      {
        id: 'first-steps',
        title: 'First steps',
        blocks: [
          {
            qa: [
              {
                q: 'I joined. Now what?',
                a: [
                  'Follow Egon. He sends you to chop, mine and fetch, then on to Miss Ledger, who unlocks your skills. After she stamps you, she offers you the roads: Forage Isle, Farm Isle, fishing with Tackle, or combat with Sergeant Vex.',
                  'There is no wrong road. You can walk any of them later.',
                ],
              },
              {
                q: 'I lost track of what to do next.',
                a: ['Type `/guide`. It shows exactly one next step. Miss Ledger’s desk also has a **Where next?** option that reopens the roads.'],
              },
              {
                q: 'Can I play in German?',
                a: ['Yes. Type `/sprache de` in game. This website has its own EN / DE switch at the top.'],
              },
              {
                q: 'What does pre-beta mean for me?',
                a: ['The world is live and the systems are real, but they are still being tuned. Things move, numbers change, new places open. If this wiki and the server disagree, the server is right, and we would like to hear about it in Discord.'],
              },
            ],
          },
        ],
      },
      {
        id: 'shards',
        title: 'Shards and support',
        blocks: [
          {
            qa: [
              {
                q: 'What are Shards?',
                a: [
                  'A thank-you currency. You earn some in game, for example from skill seals. Paid support is paused while payment details are refreshed.',
                  'When support returns, instructions will be posted in [Discord]({discord}). Nothing that makes you stronger is for sale.',
                ],
              },
              {
                q: 'Is Aetherion pay-to-win?',
                a: ['No. Shards do not buy power, dungeon wins or skill levels. Boss weapons drop from bosses and are never sold. If you never pay a cent, you play the same server as everyone else.'],
              },
              {
                q: 'Where do I buy them?',
                a: ['On the [support section](/#support) of the homepage. Packs start at 1 euro.'],
              },
            ],
          },
        ],
      },
      {
        id: 'community',
        title: 'Discord and help',
        blocks: [
          {
            qa: [
              {
                q: 'Where do I ask for help?',
                a: ['[Discord]({discord}). Questions, bug reports, Shard credits and appeals all go there.'],
              },
              {
                q: 'I found a bug or a dupe.',
                a: ['Report it in Discord and do not use it. Reporting helps everyone; abusing it breaks the [rules](/wiki/rules#bugs).'],
              },
            ],
          },
        ],
      },
      {
        id: 'sandbox',
        title: 'Your own sandbox server',
        blocks: [
          {
            qa: [
              {
                q: 'What is the Playground?',
                a: [
                  'A free Minecraft server of your own, separate from Aetherion. Test a build, try a version, play with two friends.',
                  {
                    list: [
                      'Account: a username and a password. No email.',
                      'Memory from 128 MB to 2 GB. Paper, Vanilla, Purpur or Fabric.',
                      'Make as many worlds as you like. One runs at a time.',
                      'It stops by itself when nobody has been online for a while.',
                      'Plugins and mods install from Modrinth in the dashboard.',
                    ],
                  },
                  '[Create one](/register) or [manage yours](/servers).',
                ],
              },
              {
                q: 'Does my sandbox share anything with Aetherion?',
                a: ['No. It has its own address and its own world. Nothing you do there touches your Aetherion progress.'],
              },
            ],
          },
        ],
      },
    ],
  },

  skills: {
    title: 'Skills',
    lead: 'Skills here are things you equip, not just a number that goes up. Pick them, slot them, and they grow while you work.',
    sections: [
      {
        id: 'how',
        title: 'How skills work',
        blocks: [
          'Aetherion has dozens of skills in seven families: **Combat, Mining, Farming, Fishing, Foraging**, plus **Utility** and **Dungeon**. Miss Ledger unlocks them during the tutorial, and you manage them in the Aetherion Manager.',
          { h3: 'Slots' },
          'You equip skills into slots. The first slot is free, and more open as your lifetime coin earnings grow. An equipped skill earns XP whenever you do its kind of work. Utility skills earn half from everything. Unequipped skills earn nothing, so choose what you slot.',
          { h3: 'Levels and rarity' },
          'Every skill goes from level 1 to 100: Apprentice, then Journeyman at 50, Master at 75, Mastered at 100. Every 20 levels its rarity climbs, from Common through Uncommon, Rare, Epic and Legendary to Mythic, and you can claim a seal reward for it.',
          { h3: 'Your Aetherion level' },
          'All skill XP also feeds your account level. It brings titles and small permanent stat gains, and it opens bigger things: a personal island at level 20 and guilds at level 75.',
        ],
      },
      {
        id: 'gear',
        title: 'Tools, armour and charms',
        blocks: [
          { items: ['combat_sword_5', 'mining_pickaxe_5', 'farming_hoe_5', 'fishing_rod_5', 'foraging_axe_5'] },
          'Each path has its own tool and armour ladder, tier I to V. You craft the next tier from the one before it plus materials, so nothing is skipped.',
          'Hoes, axes and rods level up as you use them, and higher tiers can climb further. Each path also has a charm line: Token, then Charm, then Relic.',
          'Boosters socket into gear for flat stat gains. Temper teaches them on the road to the Capital.',
        ],
      },
      {
        id: 'combat',
        title: 'Combat',
        item: 'combat_sword_5',
        blocks: [
          'Combat XP comes from kills. The road starts with **Sergeant Vex** at the Borderlands gate, who checks your gear before he sends you anywhere. Armour on, blade in hand, food in the bag.',
          'After Vex come the Borderlands rites, then boss hunts. Bosses drop their own weapons, which is the only way to get them.',
          { note: 'Skills to look for: Heavy Hands, Boss Grudge, Last Word.' },
        ],
      },
      {
        id: 'mining',
        title: 'Mining',
        item: 'mining_pickaxe_5',
        blocks: [
          'Mining Eldervale is the flagship gathering isle. Hand-mining builds a work song, the Strike Rhythm, that can chain into extra ore. Geodes push out of the rock for you to crack open. Foreman contracts pay for deep shifts and hand-ins, and the Deep Forge turns ore into permanent Forge Marks.',
          '**Ore Mastery** tracks every ore family from tier I to VII and adds permanent ore Fortune as you climb. Under the isle lie the Amethyst Mines; their Violet Deep opens at Mining 20.',
          { note: 'Skills to look for: Rock Whisper, Cave Sense, Depth Gauge.' },
        ],
      },
      {
        id: 'farming',
        title: 'Farming',
        item: 'farming_hoe_5',
        blocks: [
          'Farm Isle belongs to **Harrow**. Its portal opens at Farming 10. Harvest in a steady rhythm for bonus Fortune, watch for glowing Prize Crops, and take Harvest Orders for better pay than the trader.',
          'The Oven House bakes your crops into short farming buffs. **Crop Mastery** runs tier I to VI per crop, and isle harvests count double.',
          { note: 'Skills to look for: Crop Gossip, Soil Sense, Market Day.' },
        ],
      },
      {
        id: 'fishing',
        title: 'Fishing',
        item: 'fishing_rod_5',
        blocks: [
          '**Tackle** fishes off the end of the harbour pier and teaches the basics. The fishing isle is where it gets serious: a dozen named waters, species that only bite at night or in the rain, and a boiling shoal that moves around the lake.',
          'Reeling is a timing game called The Line. Clean catches build heat, and heat brings rarer fish. Your **Angler’s Log** records every species and your heaviest catch, and its ranks add permanent catch and speed on the isle.',
          { note: 'Skills to look for: Bite Me, Lake Sense, Tide Reader.' },
        ],
      },
      {
        id: 'foraging',
        title: 'Foraging',
        item: 'foraging_axe_5',
        blocks: [
          'Twig’s slime pad, just past the Forager at the harbour, throws you to Forage Isle: seven forests stacked up a very tall island, linked by updrafts. Felling is a timing bar. Hit it clean and keep the streak.',
          '**Grove Mastery** climbs tier I to VII per wood. Crown Finds drop out of the canopy as a tree falls, so catch them. Pell’s Lumber Board takes orders for coins.',
          { note: 'Skills to look for: Woodwise, Grove Born, Heart Hunter.' },
        ],
      },
    ],
  },

  world: {
    title: 'World',
    lead: 'Four places, in the order you will find them. The screenshots do most of the talking.',
    sections: [
      {
        id: 'harbour',
        title: 'Anker Harbour',
        image: '/world/harbour-spawn.jpg',
        blocks: [
          'Everyone arrives here. Egon waits on the pier, the Forager is chopping nearby, and the Quartermaster wants coal. The road climbs from the docks towards the Capital, where Miss Ledger keeps her desk and Temper works on the way up.',
          'Slime pads at the harbour throw you out to the isles. The Borderlands lie at the other edge, past Sergeant Vex. Ships come and go. The bells are loudest on the water.',
        ],
      },
      {
        id: 'eldervale',
        title: 'Eldervale',
        image: '/world/eldervale.jpg',
        blocks: [
          'The outer isles, one for each gathering skill. Each has its own people, events and records.',
          {
            list: [
              '**Forage Isle**: seven forests on one tall island, from the Elderwood Vale at the landing up to the Canopy Crown. Updrafts carry you between them.',
              '**Farm Isle**: Harrow’s fields, the Oven House, and Bee Bloom and Harvest Moon events that roll through every so often.',
              '**The fishing isle**: lakes, highland tarns and a wishing fountain. When the bells toll over the water, a lake event is about to start.',
              '**The mining isle**: shafts, districts and four depth bands, with the Deep Forge at its heart.',
            ],
          },
        ],
      },
      {
        id: 'amethyst',
        title: 'Amethyst Mines',
        image: '/world/amethyst-mines.jpg',
        blocks: [
          'The Veins: a dig world under the islands that resets every day. Geodes grow more common and bigger the deeper you go. Strike them to fill their Resonance, shatter them, and keep what is inside.',
          'Shardlings skitter through the tunnels. The Violet Deep opens at Mining 20. Bring a good pickaxe, and something to light the way.',
        ],
      },
      {
        id: 'throne',
        title: 'Throne of Ashes',
        image: '/world/throne-of-ashes.jpg',
        blocks: [
          'The third dungeon floor: lava halls, ash in the air, and elites that step in partway through the run. Nobody walks in here by accident.',
          'You do not start here. See [Dungeons](/wiki/dungeons) for the floors that come first.',
        ],
      },
    ],
  },

  rules: {
    title: 'Rules',
    lead: 'Short on purpose. If you are unsure whether something is okay, ask in Discord before you do it.',
    sections: [
      {
        id: 'people',
        title: 'People',
        blocks: [
          {
            steps: [
              '**Be decent.** No harassment, slurs or hate. Banter is fine; bullying is not.',
              '**Keep chat readable.** No spam, and no advertising other servers.',
            ],
          },
        ],
      },
      {
        id: 'play',
        title: 'Play',
        blocks: [
          {
            steps: [
              '**Do not grief.** Leave other players’ builds, islands and belongings alone. No stealing and no trapping.',
              '**Play the game yourself.** No hacked clients, no x-ray packs and no exploits.',
            ],
          },
        ],
      },
      {
        id: 'bugs',
        title: 'Bugs and dupes',
        blocks: [
          {
            steps: ['**Report it, do not use it.** Using a dupe or exploit counts as cheating, even if you found it by accident. Reporting it in Discord is appreciated.'],
          },
        ],
      },
      {
        id: 'trade',
        title: 'Trading',
        blocks: [
          {
            steps: ['**Trade honestly.** Deals on the Auction House, on the Bazaar and between players stand as agreed. Scamming in a trade is treated like theft.'],
          },
        ],
      },
      {
        id: 'appeals',
        title: 'Staff and appeals',
        blocks: [
          'Staff decisions can be questioned. Do it calmly, in [Discord]({discord}), with your Minecraft name and what happened. Arguing in public chat does not speed anything up.',
          { note: 'Aetherion is in pre-beta, so these rules may be sharpened as the server grows.' },
        ],
      },
    ],
  },

  dungeons: {
    title: 'Dungeons',
    lead: 'Instanced floors for you and your party. The fights scale with your group, and so does the noise.',
    sections: [
      {
        id: 'floors',
        title: 'The floors',
        blocks: [
          {
            steps: [
              '**Warden’s Prison.** The entry floor, guarded by the Sentinel. Learn the rooms here.',
              '**Frostbound.** A real step up. Come with dungeon gear from the first floor.',
              '**Throne of Ashes.** Lava halls, elites mid-run, and something waiting at the end.',
            ],
          },
          'Every run is your own instance. Bring friends; fights scale up with each extra player.',
        ],
      },
      {
        id: 'loot',
        title: 'What you bring home',
        blocks: [
          'Dungeon rewards come from chests: combat caches along the way and a victory chest at the end. Expect dungeon cores, vestige armour and a weapon schematic from the first floor, plus relics and materials.',
          'Dungeon gear is tuned for dungeons. It is strong inside and noticeably weaker in the open world. Dungeon-family skills only work inside a run as well.',
        ],
      },
      {
        id: 'bosses',
        title: 'Bosses',
        blocks: [
          'Outside the dungeons, bosses are hunted through quests on the combat road. Each hunt opens a spawn: the Hollow Lurker, the Bridge Troll, the Ashen Sheath and others. Some come back with their weapon; most come back with a story.',
          { drops: true },
        ],
      },
    ],
  },

  lore: {
    title: 'Lore',
    lead: 'Rumours, not records. Everything below is what people say. Some of it is wrong. Nobody agrees which part.',
    sections: [
      {
        id: 'name',
        title: 'The name',
        blocks: [
          { tale: { who: 'A harbour clerk', text: 'The old charts say Eldervale. Everyone says Aetherion now. Ask which is right and the answer depends on how old the person is, and how much they have had to drink.' } },
          { tale: { who: 'An old sailor', text: 'Something broke. That is when the name changed. I will not say what broke. Buy me another and I still will not.' } },
        ],
      },
      {
        id: 'chains',
        title: 'The anchors',
        blocks: [
          { tale: { who: 'A net mender on the pier', text: 'There were chains once, anchor chains, running from this harbour to places no ship can reach. Nobody has seen one. Everybody knows somebody who has.' } },
        ],
      },
      {
        id: 'bells',
        title: 'The bells',
        blocks: [
          'Every morning and every evening, bells ring across the islands. Ask ten people who rings them and you get eleven answers.',
          { tale: { who: 'A forager at Bell Lodge', text: 'That is the only bell on this island, and it is not the one you hear.' } },
          { tale: { who: 'A fisher on the lake', text: 'When they ring over the water, reel in. Something big is coming up to listen.' } },
          { tale: { who: 'A child at the market', text: 'It is a ghost who forgot to stop.' } },
        ],
      },
      {
        id: 'king',
        title: 'The bedtime story',
        blocks: [
          { tale: { who: 'Every parent in the harbour', text: 'Once there was a king who went mad. Go to sleep.' } },
          'Children here get one scary story, about a mad king. Nobody tells it the same way twice, and nobody tells it to the end.',
          { note: 'The archive collects rumours, not answers. The world remembers more than this page does.' },
        ],
      },
    ],
  },
}

const de = {
  faq: {
    title: 'FAQ',
    lead: 'Die Fragen, die alle in den ersten zehn Minuten stellen. Hier einmal beantwortet.',
    sections: [
      {
        id: 'join',
        title: 'Reinkommen',
        blocks: [
          { join: true },
          {
            qa: [
              {
                q: 'Wie trete ich bei?',
                a: [
                  { steps: ['Minecraft **Java Edition {version}** öffnen.', 'Mehrspieler, dann Server hinzufügen.', '`{ip}` eintragen und beitreten.'] },
                  'Du landest in Anker Harbour. Egon wartet am Pier. Sprich ihn an, dann ergibt sich die erste Stunde von selbst.',
                ],
              },
              {
                q: 'Welche Minecraft-Version brauche ich?',
                a: ['Java Edition **{version}**. Aetherion ist ein Java-Server, Bedrock (Konsolen, Handys, die Bedrock-App für Windows) kann nicht beitreten.'],
              },
              {
                q: 'Brauche ich den Launcher?',
                a: [
                  'Nein, aber er ist der einfachste Weg. Der Launcher ist für Windows und installiert das Aetherion-Pack für dich, damit Items, Menüs und Sounds so aussehen, wie sie sollen.',
                  'Lieber dein eigener Client? Jede saubere Java-{version}-Installation geht. Wenn der Server sein Ressourcenpaket anbietet, nimm es an: Ein großer Teil von Aetherion steckt darin.',
                ],
              },
              {
                q: 'Ich bin auf Mac oder Linux.',
                a: ['Den Launcher gibt es vorerst nur für Windows. Nimm den offiziellen Minecraft-Launcher mit Java {version} und füg den Server von Hand hinzu. Gleiche Welt, gleicher Fortschritt.'],
              },
              {
                q: 'Kostet das etwas?',
                a: ['Nein. Aetherion ist kostenlos. Support gibt es, er ist freiwillig und macht dich nicht stärker. Siehe Shards weiter unten.'],
              },
            ],
          },
        ],
      },
      {
        id: 'first-steps',
        title: 'Erste Schritte',
        blocks: [
          {
            qa: [
              {
                q: 'Ich bin drin. Und jetzt?',
                a: [
                  'Folge Egon. Er schickt dich Holz hacken, Erz holen und Besorgungen machen, dann weiter zu Miss Ledger, die deine Skills freischaltet. Nach ihrem Stempel zeigt sie dir die Wege: Forage Isle, Farm Isle, Angeln bei Tackle oder Kampf bei Sergeant Vex.',
                  'Es gibt keinen falschen Weg. Die anderen kannst du später immer noch gehen.',
                ],
              },
              {
                q: 'Ich weiß nicht mehr, was als Nächstes kommt.',
                a: ['Tipp `/guide`. Es zeigt genau einen nächsten Schritt. Am Schreibtisch von Miss Ledger gibt es außerdem **Wohin jetzt?**, das die Wege wieder öffnet.'],
              },
              {
                q: 'Kann ich auf Deutsch spielen?',
                a: ['Ja. Tipp `/sprache de` im Spiel. Diese Website hat oben ihren eigenen EN-/DE-Schalter.'],
              },
              {
                q: 'Was heißt Pre-Beta für mich?',
                a: ['Die Welt läuft und die Systeme sind echt, werden aber noch abgestimmt. Dinge verschieben sich, Zahlen ändern sich, neue Orte kommen dazu. Wenn dieses Wiki und der Server sich widersprechen, hat der Server recht, und wir freuen uns über einen Hinweis im Discord.'],
              },
            ],
          },
        ],
      },
      {
        id: 'shards',
        title: 'Shards und Support',
        blocks: [
          {
            qa: [
              {
                q: 'Was sind Shards?',
                a: [
                  'Eine Danke-Währung. Ein paar verdienst du im Spiel, zum Beispiel über Skill-Siegel. Bezahlter Support pausiert, während Zahlungsdaten erneuert werden.',
                  'Wenn Support zurückkommt, steht der Weg im [Discord]({discord}). Nichts, was dich stärker macht, ist käuflich.',
                ],
              },
              {
                q: 'Ist Aetherion Pay-to-Win?',
                a: ['Nein. Shards kaufen keine Power, keine Dungeon-Siege und keine Skill-Level. Boss-Waffen droppen von Bossen und werden nie verkauft. Wer nie einen Cent zahlt, spielt denselben Server wie alle anderen.'],
              },
              {
                q: 'Wo bekomme ich sie?',
                a: ['Im [Support-Bereich](/#support) der Startseite. Pakete gibt es ab 1 Euro.'],
              },
            ],
          },
        ],
      },
      {
        id: 'community',
        title: 'Discord und Hilfe',
        blocks: [
          {
            qa: [
              {
                q: 'Wo frage ich nach Hilfe?',
                a: ['Im [Discord]({discord}). Fragen, Bug-Reports, Shard-Gutschriften und Einsprüche gehören alle dorthin.'],
              },
              {
                q: 'Ich habe einen Bug oder einen Dupe gefunden.',
                a: ['Melde ihn im Discord und nutze ihn nicht. Melden hilft allen; ausnutzen verstößt gegen die [Regeln](/wiki/rules#bugs).'],
              },
            ],
          },
        ],
      },
      {
        id: 'sandbox',
        title: 'Dein eigener Sandbox-Server',
        blocks: [
          {
            qa: [
              {
                q: 'Was ist der Playground?',
                a: [
                  'Ein kostenloser eigener Minecraft-Server, getrennt von Aetherion. Build testen, Version ausprobieren, mit zwei Freunden spielen.',
                  {
                    list: [
                      'Konto: Benutzername und Passwort. Keine E-Mail.',
                      'RAM von 128 MB bis 2 GB. Paper, Vanilla, Purpur oder Fabric.',
                      'So viele Welten, wie du willst. Eine läuft gleichzeitig.',
                      'Er stoppt von selbst, wenn eine Weile niemand online war.',
                      'Plugins und Mods installierst du im Dashboard über Modrinth.',
                    ],
                  },
                  '[Einen erstellen](/register) oder [deine verwalten](/servers).',
                ],
              },
              {
                q: 'Teilt meine Sandbox etwas mit Aetherion?',
                a: ['Nein. Sie hat ihre eigene Adresse und ihre eigene Welt. Nichts davon berührt deinen Aetherion-Fortschritt.'],
              },
            ],
          },
        ],
      },
    ],
  },

  skills: {
    title: 'Skills',
    lead: 'Skills sind hier etwas, das du ausrüstest, nicht nur eine Zahl, die steigt. Aussuchen, einsetzen, und sie wachsen, während du arbeitest.',
    sections: [
      {
        id: 'how',
        title: 'So funktionieren Skills',
        blocks: [
          'Aetherion hat Dutzende Skills in sieben Familien: **Combat, Mining, Farming, Fishing, Foraging**, dazu **Utility** und **Dungeon**. Miss Ledger schaltet sie im Tutorial frei, verwaltet werden sie im Aetherion Manager.',
          { h3: 'Slots' },
          'Skills rüstest du in Slots aus. Der erste Slot ist frei, weitere öffnen sich mit deinen insgesamt verdienten Coins. Ein ausgerüsteter Skill bekommt XP, sobald du seine Art Arbeit machst. Utility-Skills bekommen von allem die Hälfte. Nicht ausgerüstete Skills bekommen nichts, also wähl gut, was du einsetzt.',
          { h3: 'Level und Seltenheit' },
          'Jeder Skill geht von Level 1 bis 100: Apprentice, dann Journeyman ab 50, Master ab 75, Mastered bei 100. Alle 20 Level steigt seine Seltenheit, von Common über Uncommon, Rare, Epic und Legendary bis Mythic, und du kannst dafür ein Siegel als Belohnung abholen.',
          { h3: 'Dein Aetherion-Level' },
          'Alle Skill-XP fließen auch in dein Account-Level. Das bringt Titel und kleine dauerhafte Werte und öffnet größere Dinge: eine eigene Insel ab Level 20 und Gilden ab Level 75.',
        ],
      },
      {
        id: 'gear',
        title: 'Werkzeuge, Rüstung und Charms',
        blocks: [
          { items: ['combat_sword_5', 'mining_pickaxe_5', 'farming_hoe_5', 'fishing_rod_5', 'foraging_axe_5'] },
          'Jeder Pfad hat seine eigene Werkzeug- und Rüstungsleiter, Stufe I bis V. Die nächste Stufe craftest du aus der vorherigen plus Material, übersprungen wird nichts.',
          'Hacken, Äxte und Angeln steigen im Level, während du sie benutzt, und höhere Stufen kommen weiter. Jeder Pfad hat außerdem eine Charm-Reihe: Token, dann Charm, dann Relic.',
          'Booster setzt du in Ausrüstung ein, für feste Werte obendrauf. Temper zeigt dir das auf dem Weg zur Capital.',
        ],
      },
      {
        id: 'combat',
        title: 'Combat',
        item: 'combat_sword_5',
        blocks: [
          'Combat-XP gibt es für Kills. Der Weg beginnt bei **Sergeant Vex** am Tor zu den Borderlands, der deine Ausrüstung prüft, bevor er dich irgendwohin schickt. Rüstung an, Klinge in der Hand, Essen in der Tasche.',
          'Nach Vex kommen die Riten der Borderlands, dann Boss-Jagden. Bosse droppen ihre eigenen Waffen, und nur so bekommt man sie.',
          { note: 'Skills, nach denen du suchen solltest: Heavy Hands, Boss Grudge, Last Word.' },
        ],
      },
      {
        id: 'mining',
        title: 'Mining',
        item: 'mining_pickaxe_5',
        blocks: [
          'Mining Eldervale ist die wichtigste Sammel-Insel. Von Hand abbauen baut einen Arbeitstakt auf, den Strike Rhythm, der sich zu Extra-Erz verketten kann. Geoden drücken sich aus dem Stein, du knackst sie selbst auf. Foreman-Aufträge zahlen für tiefe Schichten und Abgaben, und die Deep Forge macht aus Erz dauerhafte Forge Marks.',
          '**Ore Mastery** zählt jede Erzfamilie von Stufe I bis VII und gibt dauerhaft Erz-Fortune, je höher du kommst. Unter der Insel liegen die Amethyst-Minen; ihr Violet Deep öffnet sich ab Mining 20.',
          { note: 'Skills, nach denen du suchen solltest: Rock Whisper, Cave Sense, Depth Gauge.' },
        ],
      },
      {
        id: 'farming',
        title: 'Farming',
        item: 'farming_hoe_5',
        blocks: [
          'Farm Isle gehört **Harrow**. Das Portal öffnet sich ab Farming 10. Ernte im gleichmäßigen Takt für Extra-Fortune, halt Ausschau nach leuchtenden Prize Crops und nimm Harvest Orders an, die besser zahlen als der Händler.',
          'Das Oven House backt aus deiner Ernte kurze Farming-Buffs. **Crop Mastery** geht pro Pflanze von Stufe I bis VI, und Ernten auf der Insel zählen doppelt.',
          { note: 'Skills, nach denen du suchen solltest: Crop Gossip, Soil Sense, Market Day.' },
        ],
      },
      {
        id: 'fishing',
        title: 'Fishing',
        item: 'fishing_rod_5',
        blocks: [
          '**Tackle** angelt am Ende des Hafenpiers und bringt dir die Grundlagen bei. Ernst wird es auf der Angel-Insel: ein Dutzend benannte Gewässer, Arten, die nur nachts oder bei Regen beißen, und ein brodelnder Schwarm, der über den See wandert.',
          'Einholen ist ein Timing-Spiel namens The Line. Saubere Fänge bauen Hitze auf, und Hitze bringt seltenere Fische. Dein **Angler’s Log** hält jede Art und deinen schwersten Fang fest, und seine Ränge geben auf der Insel dauerhaft mehr Fang und Tempo.',
          { note: 'Skills, nach denen du suchen solltest: Bite Me, Lake Sense, Tide Reader.' },
        ],
      },
      {
        id: 'foraging',
        title: 'Foraging',
        item: 'foraging_axe_5',
        blocks: [
          'Twigs Schleimpad, gleich hinter dem Forager am Hafen, wirft dich nach Forage Isle: sieben Wälder übereinander auf einer sehr hohen Insel, verbunden durch Aufwinde. Fällen ist eine Timing-Leiste. Triff sie sauber und halt die Serie.',
          '**Grove Mastery** steigt pro Holzart von Stufe I bis VII. Crown Finds fallen aus der Krone, wenn ein Baum umkippt, also fang sie. Pells Lumber Board nimmt Aufträge gegen Coins an.',
          { note: 'Skills, nach denen du suchen solltest: Woodwise, Grove Born, Heart Hunter.' },
        ],
      },
    ],
  },

  world: {
    title: 'Welt',
    lead: 'Vier Orte, in der Reihenfolge, in der du sie findest. Die Screenshots erzählen das meiste.',
    sections: [
      {
        id: 'harbour',
        title: 'Anker Harbour',
        image: '/world/harbour-spawn.jpg',
        blocks: [
          'Hier kommen alle an. Egon wartet am Pier, der Forager hackt in der Nähe Holz, und der Quartermaster will Kohle. Die Straße steigt von den Docks zur Capital hinauf, wo Miss Ledger ihren Schreibtisch hat und Temper auf halbem Weg arbeitet.',
          'Schleimpads am Hafen werfen dich zu den Inseln hinaus. Am anderen Rand liegen die Borderlands, hinter Sergeant Vex. Schiffe kommen und gehen. Auf dem Wasser sind die Glocken am lautesten.',
        ],
      },
      {
        id: 'eldervale',
        title: 'Eldervale',
        image: '/world/eldervale.jpg',
        blocks: [
          'Die äußeren Inseln, eine für jeden Sammel-Skill. Jede hat ihre eigenen Leute, Events und Rekorde.',
          {
            list: [
              '**Forage Isle**: sieben Wälder auf einer hohen Insel, vom Elderwood Vale an der Landung bis hinauf zur Canopy Crown. Aufwinde tragen dich dazwischen.',
              '**Farm Isle**: Harrows Felder, das Oven House und die Events Bee Bloom und Harvest Moon, die immer wieder durchziehen.',
              '**Die Angel-Insel**: Seen, Hochlandtümpel und ein Wunschbrunnen. Wenn die Glocken über dem Wasser läuten, beginnt gleich ein See-Event.',
              '**Die Mining-Insel**: Schächte, Viertel und vier Tiefenzonen, mit der Deep Forge in der Mitte.',
            ],
          },
        ],
      },
      {
        id: 'amethyst',
        title: 'Amethyst-Minen',
        image: '/world/amethyst-mines.jpg',
        blocks: [
          'The Veins: eine Grabwelt unter den Inseln, die sich jeden Tag zurücksetzt. Je tiefer du kommst, desto häufiger und größer werden die Geoden. Schlag sie an, bis ihre Resonanz voll ist, zerbrich sie und behalte, was drin ist.',
          'Shardlings huschen durch die Gänge. Der Violet Deep öffnet sich ab Mining 20. Nimm eine gute Spitzhacke mit, und etwas, das Licht macht.',
        ],
      },
      {
        id: 'throne',
        title: 'Throne of Ashes',
        image: '/world/throne-of-ashes.jpg',
        blocks: [
          'Der dritte Dungeon-Floor: Lavahallen, Asche in der Luft und Elites, die mitten im Lauf dazukommen. Hier landet niemand aus Versehen.',
          'Hier fängst du nicht an. Unter [Dungeons](/wiki/dungeons) stehen die Floors, die vorher kommen.',
        ],
      },
    ],
  },

  rules: {
    title: 'Regeln',
    lead: 'Mit Absicht kurz. Wenn du unsicher bist, ob etwas okay ist, frag vorher im Discord.',
    sections: [
      {
        id: 'people',
        title: 'Menschen',
        blocks: [
          {
            steps: [
              '**Sei anständig.** Keine Belästigung, keine Beleidigungen, kein Hass. Sprüche sind okay, Mobbing nicht.',
              '**Halt den Chat lesbar.** Kein Spam und keine Werbung für andere Server.',
            ],
          },
        ],
      },
      {
        id: 'play',
        title: 'Spielen',
        blocks: [
          {
            steps: [
              '**Kein Griefing.** Lass die Builds, Inseln und Sachen anderer Spieler in Ruhe. Kein Klauen, keine Fallen.',
              '**Spiel selbst.** Keine Hack-Clients, keine X-Ray-Packs und keine Exploits.',
            ],
          },
        ],
      },
      {
        id: 'bugs',
        title: 'Bugs und Dupes',
        blocks: [
          {
            steps: ['**Melden, nicht nutzen.** Einen Dupe oder Exploit zu nutzen zählt als Cheaten, auch wenn du ihn zufällig gefunden hast. Eine Meldung im Discord freut uns.'],
          },
        ],
      },
      {
        id: 'trade',
        title: 'Handel',
        blocks: [
          {
            steps: ['**Handel ehrlich.** Deals im Auction House, im Bazaar und zwischen Spielern gelten so, wie sie abgemacht sind. Betrug beim Handel zählt wie Diebstahl.'],
          },
        ],
      },
      {
        id: 'appeals',
        title: 'Team und Einsprüche',
        blocks: [
          'Entscheidungen des Teams darf man hinterfragen. Ruhig, im [Discord]({discord}), mit deinem Minecraft-Namen und dem, was passiert ist. Streit im öffentlichen Chat beschleunigt nichts.',
          { note: 'Aetherion ist in der Pre-Beta, die Regeln werden also noch geschärft, während der Server wächst.' },
        ],
      },
    ],
  },

  dungeons: {
    title: 'Dungeons',
    lead: 'Instanzierte Floors für dich und deine Gruppe. Die Kämpfe wachsen mit der Gruppe, der Lärm auch.',
    sections: [
      {
        id: 'floors',
        title: 'Die Floors',
        blocks: [
          {
            steps: [
              '**Warden’s Prison.** Der Einstiegs-Floor, bewacht vom Sentinel. Hier lernst du die Räume.',
              '**Frostbound.** Eine echte Stufe höher. Komm mit Dungeon-Ausrüstung aus dem ersten Floor.',
              '**Throne of Ashes.** Lavahallen, Elites mitten im Lauf und etwas, das am Ende wartet.',
            ],
          },
          'Jeder Lauf ist deine eigene Instanz. Bring Freunde mit; die Kämpfe werden mit jedem weiteren Spieler stärker.',
        ],
      },
      {
        id: 'loot',
        title: 'Was du mit nach Hause nimmst',
        blocks: [
          'Dungeon-Belohnungen kommen aus Truhen: Kampf-Caches unterwegs und eine Siegestruhe am Ende. Rechne mit Dungeon-Cores, Vestige-Rüstung und einer Waffen-Schematic aus dem ersten Floor, dazu Relikte und Material.',
          'Dungeon-Ausrüstung ist für Dungeons gemacht. Drinnen ist sie stark, draußen in der offenen Welt spürbar schwächer. Skills der Dungeon-Familie wirken ebenfalls nur während eines Laufs.',
        ],
      },
      {
        id: 'bosses',
        title: 'Bosse',
        blocks: [
          'Außerhalb der Dungeons werden Bosse über Quests auf dem Kampf-Weg gejagt. Jede Jagd öffnet einen Spawn: den Hollow Lurker, den Bridge Troll, den Ashen Sheath und andere. Manche kommen mit ihrer Waffe zurück, die meisten mit einer Geschichte.',
          { drops: true },
        ],
      },
    ],
  },

  lore: {
    title: 'Lore',
    lead: 'Gerüchte, keine Akten. Alles hier ist, was die Leute erzählen. Manches davon ist falsch. Niemand ist sich einig, welcher Teil.',
    sections: [
      {
        id: 'name',
        title: 'Der Name',
        blocks: [
          { tale: { who: 'Ein Hafenschreiber', text: 'Auf den alten Karten steht Eldervale. Heute sagen alle Aetherion. Frag, was stimmt, und die Antwort hängt davon ab, wie alt jemand ist und wie viel er getrunken hat.' } },
          { tale: { who: 'Ein alter Seemann', text: 'Etwas ist zerbrochen. Danach hat sich der Name geändert. Was zerbrochen ist, sag ich nicht. Gib mir noch einen aus, ich sag’s trotzdem nicht.' } },
        ],
      },
      {
        id: 'chains',
        title: 'Die Anker',
        blocks: [
          { tale: { who: 'Eine Netzflickerin am Pier', text: 'Früher gab es Ketten, Ankerketten, von diesem Hafen bis zu Orten, die kein Schiff erreicht. Gesehen hat keiner eine. Jeder kennt jemanden, der eine gesehen hat.' } },
        ],
      },
      {
        id: 'bells',
        title: 'Die Glocken',
        blocks: [
          'Jeden Morgen und jeden Abend läuten Glocken über den Inseln. Frag zehn Leute, wer sie läutet, und du bekommst elf Antworten.',
          { tale: { who: 'Ein Forager in der Bell Lodge', text: 'Das ist die einzige Glocke auf dieser Insel, und es ist nicht die, die du hörst.' } },
          { tale: { who: 'Eine Fischerin am See', text: 'Wenn sie über dem Wasser läuten, hol die Leine ein. Da kommt etwas Großes hoch und hört zu.' } },
          { tale: { who: 'Ein Kind am Markt', text: 'Das ist ein Geist, der vergessen hat aufzuhören.' } },
        ],
      },
      {
        id: 'king',
        title: 'Die Gutenachtgeschichte',
        blocks: [
          { tale: { who: 'Alle Eltern im Hafen', text: 'Es war einmal ein König, der wurde verrückt. Schlaf jetzt.' } },
          'Kinder bekommen hier genau eine gruselige Geschichte, über einen verrückten König. Niemand erzählt sie zweimal gleich, und niemand erzählt sie zu Ende.',
          { note: 'Das Archiv sammelt Gerüchte, keine Antworten. Die Welt erinnert sich an mehr als diese Seite.' },
        ],
      },
    ],
  },
}

export const WIKI_PAGES = { en, de }
