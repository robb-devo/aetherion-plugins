import { HERO_IMAGE, SITE } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { useLauncherDownload } from '../../lib/hooks.js'
import { CopyButton, Reveal } from '../ui.jsx'

export default function Finale() {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  return (
    <section className="relative isolate overflow-hidden px-4 py-24 sm:px-6">
      <img src={HERO_IMAGE} alt="" loading="lazy" className="absolute inset-0 -z-20 h-full w-full object-cover object-[30%_70%] opacity-35" />
      <div className="absolute inset-0 -z-10 bg-gradient-to-b from-void via-void/75 to-void" aria-hidden="true" />
      <Reveal className="mx-auto max-w-2xl text-center">
        <h2 className="font-display text-3xl font-bold tracking-wide text-white sm:text-5xl">{copy.finale.title}</h2>
        <p className="mt-4 text-base text-mist/75">{copy.finale.body}</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <CopyButton
            value={SITE.ip}
            className="btn btn-primary btn-lg notch font-mono"
            copiedLabel={copy.hero.copied}
            toast={copy.hero.ipCopied}
          >
            {SITE.ip}
          </CopyButton>
          <a href={launcher} className="btn btn-secondary btn-lg notch">
            {copy.hero.launcher}
          </a>
        </div>
      </Reveal>
    </section>
  )
}
