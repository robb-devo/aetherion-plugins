import { FEATURES } from '../content.js'
import { FeatureIcon } from './icons.jsx'
import { Kicker, MotionCard, Reveal, Section, SectionTitle } from './ui.jsx'

export default function Features() {
  return (
    <Section id="features" className="pt-8 sm:pt-10">
      <Reveal>
        <Kicker>Welt</Kicker>
        <SectionTitle en="Skills, islands, market, pads, dungeons.">
          Was dich erwartet
        </SectionTitle>
      </Reveal>

      <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-6">
        {FEATURES.map((feature, i) => (
          <Reveal key={feature.id} className={feature.span} delay={i * 55}>
            <MotionCard className="rounded-2xl p-5 sm:p-6">
              <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-white/5 text-amethyst">
                <FeatureIcon id={feature.id} />
              </div>
              <h3 className="text-xl font-bold text-white">{feature.title}</h3>
              <p className="mt-1 text-xs font-semibold tracking-wide text-cyan/80 uppercase">
                {feature.en}
              </p>
              <p className="mt-3 text-sm leading-relaxed text-mist/75">{feature.body}</p>
            </MotionCard>
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
