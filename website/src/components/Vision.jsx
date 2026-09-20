import { useLang } from '../i18n.jsx'
import { FadeLang } from './LangToggle.jsx'
import { Kicker, MotionCard, Reveal, Section, SectionTitle } from './ui.jsx'

export default function Vision() {
  const { copy } = useLang()

  return (
    <Section id="vision">
      <Reveal>
        <FadeLang>
          <Kicker>{copy.vision.kicker}</Kicker>
          <SectionTitle sub={copy.vision.sub}>{copy.vision.title}</SectionTitle>
          <p className="mt-6 max-w-2xl text-base leading-relaxed text-mist/85 sm:text-lg">
            {copy.vision.body}
          </p>
        </FadeLang>
      </Reveal>

      <div className="mt-10 grid gap-4 md:grid-cols-3">
        {copy.vision.pillars.map((pillar, i) => (
          <Reveal key={i} delay={i * 80}>
            <MotionCard className="rounded-2xl p-5 sm:p-6">
              <p className="text-cyan/90">✦</p>
              <FadeLang>
                <h3 className="mt-2 text-lg font-bold text-white">{pillar.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-mist/75">{pillar.body}</p>
              </FadeLang>
            </MotionCard>
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
