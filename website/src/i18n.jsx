import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { COPY } from './copy.js'

const STORAGE_KEY = 'aetherion-lang'
const LangContext = createContext(null)

function readStoredLang() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    if (value === 'en' || value === 'de') return value
  } catch {
    // private mode / blocked storage
  }
  return 'en'
}

export function LanguageProvider({ children }) {
  const [lang, setLangState] = useState(() => (typeof window === 'undefined' ? 'en' : readStoredLang()))

  function setLang(next) {
    if (next !== 'en' && next !== 'de') return
    setLangState(next)
  }

  useEffect(() => {
    document.documentElement.lang = lang
    const meta = COPY[lang].meta
    // Pages set their own titles; only swap the generic site title.
    if (!document.title || Object.values(COPY).some((entry) => entry.meta.title === document.title)) {
      document.title = meta.title
    }
    const description = document.querySelector('meta[name="description"]')
    if (description) description.setAttribute('content', meta.description)
    const ogTitle = document.querySelector('meta[property="og:title"]')
    if (ogTitle) ogTitle.setAttribute('content', meta.title)
    const og = document.querySelector('meta[property="og:description"]')
    if (og) og.setAttribute('content', meta.description)
    const ogLocale = document.querySelector('meta[property="og:locale"]')
    if (ogLocale) ogLocale.setAttribute('content', lang === 'de' ? 'de_DE' : 'en_US')
    try {
      window.localStorage.setItem(STORAGE_KEY, lang)
    } catch {
      // ignore
    }
  }, [lang])

  const value = useMemo(
    () => ({
      lang,
      setLang,
      copy: COPY[lang],
    }),
    [lang],
  )

  return <LangContext.Provider value={value}>{children}</LangContext.Provider>
}

export function useLang() {
  const ctx = useContext(LangContext)
  if (!ctx) throw new Error('useLang must be used inside LanguageProvider')
  return ctx
}
