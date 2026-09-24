import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useMemo, useState } from 'react'
import { SITE, SHARD_PACKS, formatEuro, formatShards, paypalUrl } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { easeOut } from '../../motion.js'
import { DiscordIcon } from '../icons.jsx'
import { CopyButton, Reveal, Section, SectionHeading } from '../ui.jsx'

export default function Support() {
  const [custom, setCustom] = useState('5')
  const [selected, setSelected] = useState('patron')
  const [bankOpen, setBankOpen] = useState(false)
  const reduced = useReducedMotion()
  const { copy, lang } = useLang()
  const amount = Number(custom)
  const customValid = Number.isFinite(amount) && amount >= 1 && amount <= 999

  const customHref = useMemo(() => {
    if (!customValid) return undefined
    const rounded = Math.round(amount * 100) / 100
    return paypalUrl({ amount: rounded, itemName: `Aetherion Support (custom ${rounded} EUR)`, lang })
  }, [amount, customValid, lang])

  return (
    <Section id="support" className="border-t hairline">
      <Reveal>
        <SectionHeading kicker={copy.support.kicker} title={copy.support.title} sub={copy.support.sub} />
        <div className="mt-8 flex gap-4 rounded-md border border-gold/35 bg-gold/[0.06] px-5 py-4">
          <span className="mt-1 h-2.5 w-2.5 shrink-0 bg-gold" aria-hidden="true" />
          <div>
            <p className="text-xs font-extrabold tracking-[0.18em] text-gold uppercase">{copy.support.bannerKicker}</p>
            <p className="mt-1.5 text-sm font-semibold text-white">{copy.support.bannerTitle}</p>
            <p className="mt-1 text-sm text-mist/70">{copy.support.bannerBody}</p>
          </div>
        </div>
      </Reveal>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        {SHARD_PACKS.map((pack, i) => {
          const href = paypalUrl({ amount: pack.euros, itemName: `Aetherion Shards — ${pack.shards}`, lang })
          const isSelected = selected === pack.id
          const labels = copy.support.packs[pack.id]
          return (
            <Reveal key={pack.id} delay={i * 60}>
              <article
                role="button"
                tabIndex={0}
                aria-pressed={isSelected}
                onClick={() => setSelected(pack.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    setSelected(pack.id)
                  }
                }}
                className={`panel relative flex h-full cursor-pointer flex-col p-6 transition-colors ${
                  isSelected ? '!border-gold/50' : 'hover:!border-white/14'
                }`}
              >
                {pack.featured ? (
                  <span className="notch absolute top-4 right-4 bg-gold px-2 py-0.5 text-[0.62rem] font-black tracking-wide text-black uppercase">
                    {labels.hint}
                  </span>
                ) : null}
                <p className="text-xs font-extrabold tracking-[0.18em] text-ash uppercase">{labels.label}</p>
                <p className="font-display mt-3 text-4xl font-bold text-white">{formatEuro(pack.euros, lang)}</p>
                <p className="mt-2 text-xl font-extrabold text-legendary">
                  {formatShards(pack.shards, lang)} {copy.support.shards}
                </p>
                <p className="mt-1 text-sm text-mist/60">{labels.hint}</p>
                <a
                  href={href}
                  className="btn btn-gold notch mt-6 w-full"
                  target="_blank"
                  rel="noreferrer"
                  onClick={(event) => event.stopPropagation()}
                >
                  PayPal
                </a>
              </article>
            </Reveal>
          )
        })}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Reveal>
          <article className="panel h-full p-6">
            <h3 className="text-lg font-bold text-white">{copy.support.customTitle}</h3>
            <p className="mt-1 text-sm text-mist/65">{copy.support.customBody}</p>
            <form
              className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
              onSubmit={(event) => {
                event.preventDefault()
                if (customHref) window.open(customHref, '_blank', 'noopener,noreferrer')
              }}
            >
              <label className="block flex-1">
                <span className="label">{copy.support.amountLabel}</span>
                <input
                  type="number"
                  min="1"
                  max="999"
                  step="0.5"
                  value={custom}
                  onChange={(event) => setCustom(event.target.value)}
                  className="field"
                  aria-invalid={!customValid}
                />
              </label>
              <button type="submit" className="btn btn-gold notch" disabled={!customValid}>
                {copy.support.openPaypal}
              </button>
            </form>
            <AnimatePresence>
              {!customValid ? (
                <motion.p
                  key={`invalid-${lang}`}
                  initial={reduced ? { opacity: 0 } : { opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={reduced ? { opacity: 0 } : { opacity: 0, height: 0 }}
                  transition={{ duration: 0.25, ease: easeOut }}
                  className="mt-2 overflow-hidden text-xs text-gold"
                >
                  {copy.support.customInvalid}
                </motion.p>
              ) : null}
            </AnimatePresence>
          </article>
        </Reveal>

        <Reveal delay={60}>
          <article className="panel h-full p-6">
            <h3 className="text-lg font-bold text-white">{copy.support.fulfillTitle}</h3>
            <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-mist/75">
              {copy.support.fulfill.map((line) => (
                <li key={line}>{interpolate(line, SITE_VARS)}</li>
              ))}
            </ol>
            <a href={SITE.discord} className="btn btn-secondary notch mt-5" target="_blank" rel="noreferrer">
              <DiscordIcon className="h-4 w-4" />
              {copy.support.toDiscord}
            </a>
          </article>
        </Reveal>
      </div>

      <Reveal className="mt-4">
        <div className="panel">
          <button
            type="button"
            className="flex w-full cursor-pointer items-center justify-between gap-3 px-6 py-4 text-left text-sm font-semibold text-white"
            aria-expanded={bankOpen}
            onClick={() => setBankOpen((value) => !value)}
          >
            {copy.support.bankTitle}
            <span className="text-xs font-extrabold tracking-wide text-ash uppercase">
              {bankOpen ? copy.support.bankClose : copy.support.bankOpen}
            </span>
          </button>
          <AnimatePresence initial={false}>
            {bankOpen ? (
              <motion.div
                key="bank"
                initial={reduced ? { opacity: 0 } : { height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={reduced ? { opacity: 0 } : { height: 0, opacity: 0 }}
                transition={{ duration: 0.3, ease: easeOut }}
                className="overflow-hidden"
              >
                <div className="border-t hairline px-6 py-4">
                  <p className="text-sm text-mist/70">{copy.support.bankBody}</p>
                  <p className="mt-3 font-mono text-sm tracking-wide text-white sm:text-base">{SITE.iban}</p>
                  <CopyButton
                    value={SITE.iban.replaceAll(' ', '')}
                    className="btn btn-secondary btn-sm notch mt-3"
                    copiedLabel={copy.support.ibanCopied}
                    toast={copy.support.ibanCopied}
                  >
                    {copy.support.copyIban}
                  </CopyButton>
                </div>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>
      </Reveal>
    </Section>
  )
}
