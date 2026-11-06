import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { COPY } from './copy.js'
import { useRouter } from './router.jsx'

const STORAGE_KEY = 'aetherion-lang'
const LangContext = createContext(null)
const PREVIEW = import.meta.env.VITE_PREVIEW === '1'

function readStored() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    if (value === 'en' || value === 'de') return value
  } catch {
    // private mode / blocked storage
  }
  return (window.navigator?.language || '').toLowerCase().startsWith('de') ? 'de' : 'en'
}

/** The language comes from the URL (/de/…). In a preview build, where the URL is fixed, from storage. */
export function LanguageProvider({ children }) {
  const { lang: routeLang, path, search, hash, navigate } = useRouter()
  const [previewLang, setPreviewLang] = useState(() => (PREVIEW && typeof window !== 'undefined' ? readStored() : 'en'))
  const lang = PREVIEW ? previewLang : routeLang || 'en'

  function setLang(next) {
    if (next !== 'en' && next !== 'de') return
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // ignore
    }
    if (PREVIEW) setPreviewLang(next)
    else navigate(`${path}${search || ''}${hash || ''}`, { lang: next, replace: true })
  }

  useEffect(() => {
    document.documentElement.lang = lang
    const meta = COPY[lang].meta
    if (!document.title || Object.values(COPY).some((entry) => entry.meta.title === document.title)) document.title = meta.title
    const set = (sel, value) => document.querySelector(sel)?.setAttribute('content', value)
    set('meta[name="description"]', meta.description)
    set('meta[property="og:title"]', meta.title)
    set('meta[property="og:description"]', meta.description)
    set('meta[property="og:locale"]', lang === 'de' ? 'de_DE' : 'en_US')
  }, [lang])

  const value = useMemo(() => ({ lang, setLang, copy: COPY[lang] }), [lang]) // eslint-disable-line
  return <LangContext.Provider value={value}>{children}</LangContext.Provider>
}

export function useLang() {
  const ctx = useContext(LangContext)
  if (!ctx) throw new Error('useLang must be used inside LanguageProvider')
  return ctx
}
