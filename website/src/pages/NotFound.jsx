import { useEffect } from 'react'
import { useLang } from '../i18n.jsx'
import { Link } from '../router.jsx'

export default function NotFound() {
  const { copy } = useLang()
  const t = copy.notFound
  useEffect(() => {
    document.title = `${t.code} · Aetherion`
  }, [t.code])
  return (
    <main className="inner inner-center">
      <div className="void">
        <p className="void-code font-pixel" aria-hidden="true">
          {t.code}
        </p>
        <h1 className="inner-title">{t.title}</h1>
        <p className="inner-lead">{t.body}</p>
        <div className="inner-actions inner-actions-c">
          <Link to="/" className="btn btn-primary notch">
            {t.home}
          </Link>
          <Link to="/wiki" className="btn btn-secondary notch">
            {copy.nav.wiki}
          </Link>
        </div>
      </div>
    </main>
  )
}
