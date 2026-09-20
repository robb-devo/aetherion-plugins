import { Kicker, Reveal, Section, SectionTitle } from './ui.jsx'

const PILLARS = [
  {
    title: 'Prototyp, ehrlich',
    body: 'Kein Publisher, kein Season-Pass-Zirkus. Peter baut Aetherion für Freunde — Feature für Feature, mit sichtbaren Kanten.',
  },
  {
    title: 'Spiel vor Shop',
    body: 'Skills, Inseln, Quests, Dungeons. Wer nicht spendet, spielt denselben Server. Support ist ein Danke, kein Shortcut.',
  },
  {
    title: 'Gemeinsam hosten',
    body: 'Optionaler Support hilft bei Hardware und Traffic. Er ersetzt kein Grind und kauft keine Wins.',
  },
]

export default function Vision() {
  return (
    <Section id="vision">
      <Reveal>
        <Kicker>Vision</Kicker>
        <SectionTitle en="A Hypixel-style Paper MMO, built as a friend prototype.">
          Kein Konzern. Ein Server.
        </SectionTitle>
        <p className="mt-6 max-w-2xl text-base leading-relaxed text-mist/85 sm:text-lg">
          Aetherion ist Peters Paper-MMO im Skyblock-Stil: Skills, Inseln, Auction House, Bazaar,
          Jump-Pads und Dungeons. Es wächst mit uns. Der Support-Shop ist ein optionales Danke — nicht
          der Weg, stärker zu werden.
        </p>
      </Reveal>

      <div className="mt-10 grid gap-4 md:grid-cols-3">
        {PILLARS.map((pillar, i) => (
          <Reveal key={pillar.title} delay={i * 80}>
            <article className="glass glass-hover h-full rounded-2xl p-5 sm:p-6">
              <p className="text-cyan/90">✦</p>
              <h3 className="mt-2 text-lg font-bold text-white">{pillar.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-mist/75">{pillar.body}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
