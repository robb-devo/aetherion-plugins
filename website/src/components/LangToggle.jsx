import { useLang } from '../i18n.jsx'

const OPTIONS = [
  { id: 'en', label: 'EN' },
  { id: 'de', label: 'DE' },
]

export default function LangToggle({ className = '' }) {
  const { lang, setLang, copy } = useLang()

  return (
    <div
      className={`notch inline-flex bg-white/[0.06] p-0.5 ${className}`}
      role="group"
      aria-label={copy.ui.langToggle}
    >
      {OPTIONS.map((option) => {
        const active = lang === option.id
        return (
          <button
            key={option.id}
            type="button"
            className={`notch min-w-9 cursor-pointer px-2.5 py-1 text-[0.7rem] font-extrabold tracking-wide transition-colors ${
              active ? 'bg-white/12 text-white' : 'text-ash hover:text-white'
            }`}
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
