import { useEffect } from 'react'
import { AppShell } from '../components/app/AppShell.jsx'
import { ItemSlot } from '../components/ui.jsx'
import { useLang } from '../i18n.jsx'
import { Link } from '../router.jsx'

export default function NotFound() {
  const { copy } = useLang()
  useEffect(() => {
    document.title = `404 · Aetherion`
  }, [])
  return (
    <AppShell>
      <div className="mx-auto flex max-w-lg flex-col items-center py-16 text-center sm:py-24">
        <div className="grid grid-cols-3 gap-1.5" aria-hidden="true">
          <ItemSlot size="h-14 w-14" />
          <ItemSlot item="quest_book" size="h-14 w-14" />
          <ItemSlot size="h-14 w-14" />
        </div>
        <p className="font-display mt-8 text-6xl font-black tracking-[0.2em] text-white/15">{copy.notFound.code}</p>
        <h1 className="font-display mt-2 text-3xl font-bold tracking-wide text-white">{copy.notFound.title}</h1>
        <p className="mt-3 text-mist/70">{copy.notFound.body}</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link to="/" className="btn btn-primary notch">
            {copy.notFound.home}
          </Link>
          <Link to="/servers" className="btn btn-secondary notch">
            {copy.notFound.servers}
          </Link>
        </div>
      </div>
    </AppShell>
  )
}
