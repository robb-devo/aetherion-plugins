import { useLang } from '../i18n.jsx'

const OPTIONS = [
  { id: 'en', label: 'EN' },
  { id: 'de', label: 'DE' },
]

export default function LangToggle({ className = '' }) {
  const { lang, setLang, copy } = useLang()

  return (
    <div
      className={`notch lang ${className}`}
      role="group"
      aria-label={copy.ui.langToggle}
    >
      {OPTIONS.map((option) => {
        const active = lang === option.id
        return (
          <button
            key={option.id}
            type="button"
            className={`notch lang-opt ${active ? 'lang-opt-on' : ''}`}
            aria-pressed={active}
            aria-label={option.id === 'en' ? copy.ui.langEn : copy.ui.langDe}
            onClick={() => setLang(option.id)}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
