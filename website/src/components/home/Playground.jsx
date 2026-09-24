import { useAuth } from '../../auth.jsx'
import { useLang } from '../../i18n.jsx'
import { Link } from '../../router.jsx'
import ServerCard from '../app/ServerCard.jsx'
import { Reveal, Section, SectionHeading } from '../ui.jsx'

export default function Playground() {
  const { copy } = useLang()
  const { status } = useAuth()
  const signedIn = status === 'signed-in'
  const running = {
    id: 'preview-a',
    name: copy.playground.previewName,
    status: 'online',
    ramMb: 1024,
    tier: 'rare',
    software: 'paper',
    version: '26.3',
    address: 'play.donnernet.de:25601',
    players: { online: 2, max: 8, names: [] },
  }
  const stopped = {
    id: 'preview-b',
    name: copy.playground.previewNameB,
    status: 'offline',
    ramMb: 2048,
    tier: 'legendary',
    software: 'fabric',
    version: '1.21.1',
    address: 'play.donnernet.de:25602',
    players: null,
  }

  return (
    <Section id="playground">
      <div className="grid items-center gap-10 lg:grid-cols-[1.1fr_1fr]">
        <Reveal>
          <SectionHeading kicker={copy.playground.kicker} title={copy.playground.title} sub={copy.playground.body} />
          <ul className="mt-6 space-y-2.5">
            {copy.playground.points.map((point) => (
              <li key={point} className="flex items-center gap-3 text-sm text-mist/85">
                <span className="h-1.5 w-1.5 shrink-0 bg-amethyst" aria-hidden="true" />
                {point}
              </li>
            ))}
          </ul>
          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link to={signedIn ? '/servers' : '/register'} className="btn btn-primary btn-lg notch">
              {signedIn ? copy.playground.ctaAuthed : copy.playground.cta}
            </Link>
            {signedIn ? null : <span className="text-xs text-ash">{copy.playground.note}</span>}
          </div>
        </Reveal>
        <Reveal delay={100}>
          <div className="relative">
            <div
              className="absolute -inset-6 -z-10 bg-[radial-gradient(circle_at_60%_40%,rgba(124,77,255,0.18),transparent_65%)]"
              aria-hidden="true"
            />
            <div className="pointer-events-none flex select-none flex-col gap-3" aria-hidden="true">
              <ServerCard server={running} preview />
              <div className="opacity-70 sm:ml-10">
                <ServerCard server={stopped} slotHolder={running} preview />
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </Section>
  )
}
