import { useEffect } from 'react'
import { useAuth } from '../auth.jsx'
import ServerCard from '../components/app/ServerCard.jsx'
import { useLang } from '../i18n.jsx'
import { Link } from '../router.jsx'

/** Free sandbox servers. Used to sit on the home page; it deserves its own room. */
export default function Playground() {
  const { copy } = useLang()
  const t = copy.playground
  const { status } = useAuth()
  const signedIn = status === 'signed-in'
  useEffect(() => {
    document.title = `${copy.nav.playground} · Aetherion`
  }, [copy.nav.playground])

  const running = {
    id: 'preview-a',
    name: t.previewName,
    status: 'online',
    ramMb: 1024,
    tier: 'rare',
    software: 'paper',
    version: '26.3',
    address: 'play.donnernet.de',
    players: { online: 2, max: 8, names: [] },
  }
  const stopped = {
    id: 'preview-b',
    name: t.previewNameB,
    status: 'offline',
    ramMb: 2048,
    tier: 'legendary',
    software: 'fabric',
    version: '1.21.1',
    address: 'play.donnernet.de',
    players: null,
  }

  return (
    <main className="inner">
      <div className="inner-wrap playground">
        <div>
          <h1 className="inner-title">{t.title}</h1>
          <p className="inner-lead">{t.body}</p>
          <ul className="inner-points">
            {t.points.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
          <div className="inner-actions">
            <Link to={signedIn ? '/servers/new' : '/register'} className="btn btn-primary btn-lg notch">
              {signedIn ? t.ctaAuthed : t.cta}
            </Link>
            {signedIn ? (
              <Link to="/servers" className="btn btn-secondary btn-lg notch">
                {t.ctaManage}
              </Link>
            ) : (
              <span className="inner-note">{t.note}</span>
            )}
          </div>
        </div>
        <div className="playground-cards" aria-hidden="true">
          <ServerCard server={running} preview />
          <ServerCard server={stopped} slotHolder={running} preview />
        </div>
      </div>
    </main>
  )
}
