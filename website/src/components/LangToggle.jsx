import { AnimatePresence, LayoutGroup, motion, useReducedMotion } from 'framer-motion'
import { useLang } from '../i18n.jsx'
import { easeOut, springSoft } from '../motion.js'

const OPTIONS = [
  { id: 'en', label: 'EN' },
  { id: 'de', label: 'DE' },
]

export default function LangToggle({ className = '' }) {
  const { lang, setLang, copy } = useLang()
  const reduced = useReducedMotion()

  return (
    <LayoutGroup id="lang-toggle">
      <div
        className={`relative inline-flex rounded-full border border-white/15 bg-white/5 p-0.5 ${className}`}
        role="group"
        aria-label={copy.ui.langToggle}
      >
        {OPTIONS.map((option) => {
          const active = lang === option.id
          return (
            <button
              key={option.id}
              type="button"
              className={`relative z-10 min-w-9 cursor-pointer rounded-full px-2.5 py-1 text-[0.7rem] font-bold tracking-wide ${
                active ? 'text-white' : 'text-mist/70 hover:text-white'
              }`}
              aria-pressed={active}
              aria-label={option.id === 'en' ? copy.ui.langEn : copy.ui.langDe}
              onClick={() => setLang(option.id)}
            >
              {active ? (
                <motion.span
                  layoutId="lang-pill"
                  className="absolute inset-0 rounded-full bg-gradient-to-r from-amethyst/40 to-cyan/30"
                  transition={reduced ? { duration: 0 } : springSoft}
                />
              ) : null}
              <span className="relative">{option.label}</span>
            </button>
          )
        })}
      </div>
    </LayoutGroup>
  )
}

export function FadeLang({ children, className = '', as: Comp = 'div' }) {
  const { lang } = useLang()
  const reduced = useReducedMotion()
  const MotionComp = Comp === 'span' ? motion.span : motion.div

  return (
    <AnimatePresence mode="wait" initial={false}>
      <MotionComp
        key={lang}
        className={className}
        initial={reduced ? { opacity: 0 } : { opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={reduced ? { opacity: 0 } : { opacity: 0, y: -6 }}
        transition={{ duration: reduced ? 0.16 : 0.28, ease: easeOut }}
      >
        {children}
      </MotionComp>
    </AnimatePresence>
  )
}
