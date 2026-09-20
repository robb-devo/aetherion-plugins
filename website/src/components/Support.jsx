import { AnimatePresence, LayoutGroup, motion, useReducedMotion } from 'framer-motion'
import { useMemo, useState } from 'react'
import {
  SITE,
  SHARD_PACKS,
  formatEuro,
  formatShards,
  paypalUrl,
} from '../content.js'
import { interpolate, SITE_VARS } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { easeOut, springSoft } from '../motion.js'
import { DiscordIcon } from './icons.jsx'
import { FadeLang } from './LangToggle.jsx'
import { CopyButton, Kicker, MotionLink, Reveal, Section, SectionTitle } from './ui.jsx'

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
    return paypalUrl({
      amount: rounded,
      itemName: `Aetherion Support (custom ${rounded} EUR)`,
      lang,
    })
  }, [amount, customValid, lang])

  return (
    <Section id="support">
      <Reveal>
        <FadeLang>
          <div className="mb-8 rounded-2xl border-2 border-gold/55 bg-gradient-to-r from-gold/15 to-amethyst/10 px-4 py-5 sm:px-6">
            <p className="text-sm font-extrabold tracking-[0.18em] text-gold uppercase">
              {copy.support.bannerKicker}
            </p>
            <p className="mt-2 text-base font-semibold text-white">{copy.support.bannerTitle}</p>
            <p className="mt-1 text-sm text-white/80">{copy.support.bannerBody}</p>
          </div>
          <Kicker>{copy.support.kicker}</Kicker>
          <SectionTitle sub={copy.support.sub}>{copy.support.title}</SectionTitle>
        </FadeLang>
      </Reveal>

      <LayoutGroup id="shard-packs">
        <div className="mt-8 grid gap-4 md:grid-cols-3">
          {SHARD_PACKS.map((pack, i) => {
            const href = paypalUrl({
              amount: pack.euros,
              itemName: `Aetherion Shards — ${pack.shards}`,
              lang,
            })
            const isSelected = selected === pack.id
            const labels = copy.support.packs[pack.id]
            return (
              <Reveal key={pack.id} delay={i * 70}>
                <motion.article
                  role="button"
                  tabIndex={0}
                  onClick={() => setSelected(pack.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      setSelected(pack.id)
                    }
                  }}
                  aria-pressed={isSelected}
                  className="pack-card glass relative flex h-full flex-col rounded-2xl p-5 sm:p-6"
                  initial={false}
                  animate={{
                    y: isSelected ? -6 : 0,
                    borderColor: isSelected
                      ? 'rgba(34, 211, 238, 0.45)'
                      : 'rgba(233, 213, 255, 0.16)',
                  }}
                  whileHover={reduced ? undefined : { y: isSelected ? -8 : -5 }}
                  whileTap={reduced ? undefined : { scale: 0.985 }}
                  transition={springSoft}
                >
                  {isSelected ? (
                    <motion.span
                      layoutId={reduced ? undefined : 'pack-select'}
                      className="pointer-events-none absolute inset-0 rounded-2xl ring-2 ring-cyan/50 shadow-[0_0_40px_rgba(34,211,238,0.14)]"
                      transition={reduced ? { duration: 0 } : springSoft}
                    />
                  ) : null}
                  {pack.featured ? (
                    <span className="absolute top-4 right-4 rounded-full bg-cyan/15 px-2 py-0.5 text-[0.65rem] font-bold text-cyan uppercase">
                      <FadeLang as="span">{labels.hint}</FadeLang>
                    </span>
                  ) : null}
                  <FadeLang className="relative">
                    <p className="text-xs font-bold tracking-[0.18em] text-mist/70 uppercase">{labels.label}</p>
                    <p className="mt-3 font-display text-4xl font-bold text-white">
                      {formatEuro(pack.euros, lang)}
                    </p>
                    <p className="mt-2 text-2xl font-extrabold text-amethyst">
                      {formatShards(pack.shards, lang)} Shards
                    </p>
                    <p className="mt-1 text-sm text-mist/65">{labels.hint}</p>
                  </FadeLang>
                  <MotionLink
                    href={href}
                    className="btn btn-gold relative mt-6 w-full"
                    target="_blank"
                    rel="noreferrer"
                    onClick={(event) => event.stopPropagation()}
                  >
                    PayPal
                  </MotionLink>
                </motion.article>
              </Reveal>
            )
          })}
        </div>
      </LayoutGroup>

      <Reveal className="mt-4">
        <article className="glass rounded-2xl p-5 sm:p-6">
          <FadeLang>
            <h3 className="text-lg font-bold text-white">{copy.support.customTitle}</h3>
            <p className="mt-1 text-sm text-mist/70">{copy.support.customBody}</p>
          </FadeLang>
          <form
            className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              if (customHref) window.open(customHref, '_blank', 'noopener,noreferrer')
            }}
          >
            <label className="block flex-1">
              <span className="mb-1.5 block text-xs font-bold tracking-wide text-mist/70 uppercase">
                <FadeLang as="span">{copy.support.amountLabel}</FadeLang>
              </span>
              <input
                type="number"
                min="1"
                max="999"
                step="0.5"
                value={custom}
                onChange={(e) => setCustom(e.target.value)}
                className="w-full rounded-xl border border-white/12 bg-white/5 px-3 py-3 text-white outline-none transition-shadow duration-300 ring-cyan/40 focus:ring-2"
              />
            </label>
            <motion.button
              type="submit"
              className="btn btn-gold sm:mb-0.5 disabled:pointer-events-none disabled:opacity-40"
              disabled={!customValid}
              whileHover={reduced || !customValid ? undefined : { y: -2 }}
              whileTap={reduced || !customValid ? undefined : { scale: 0.98 }}
            >
              <FadeLang as="span">{copy.support.openPaypal}</FadeLang>
            </motion.button>
          </form>
          <AnimatePresence>
            {!customValid ? (
              <motion.p
                key={`invalid-${lang}`}
                initial={reduced ? { opacity: 0 } : { opacity: 0, y: -6, height: 0 }}
                animate={{ opacity: 1, y: 0, height: 'auto' }}
                exit={reduced ? { opacity: 0 } : { opacity: 0, y: -4, height: 0 }}
                transition={{ duration: 0.28, ease: easeOut }}
                className="mt-2 overflow-hidden text-xs text-gold"
              >
                {copy.support.customInvalid}
              </motion.p>
            ) : null}
          </AnimatePresence>
        </article>
      </Reveal>

      <Reveal className="mt-4">
        <article className="rounded-2xl border border-white/12 bg-white/5 p-5 sm:p-6">
          <FadeLang>
            <h3 className="text-lg font-bold text-white">{copy.support.fulfillTitle}</h3>
            <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-mist/80">
              {copy.support.fulfill.map((line) => (
                <li key={line}>{interpolate(line, SITE_VARS)}</li>
              ))}
            </ol>
          </FadeLang>
          <MotionLink href={SITE.discord} className="btn btn-ghost mt-5" target="_blank" rel="noreferrer">
            <DiscordIcon className="h-4 w-4" />
            <FadeLang as="span">{copy.support.toDiscord}</FadeLang>
          </MotionLink>
        </article>
      </Reveal>

      <Reveal className="mt-4">
        <div className="glass rounded-2xl">
          <button
            type="button"
            className="flex w-full cursor-pointer items-center justify-between gap-3 px-5 py-4 text-left text-sm font-semibold text-white sm:px-6"
            aria-expanded={bankOpen}
            onClick={() => setBankOpen((v) => !v)}
          >
            <FadeLang as="span">{copy.support.bankTitle}</FadeLang>
            <span className="text-xs font-bold tracking-wide text-mist/60 uppercase">
              <FadeLang as="span">{bankOpen ? copy.support.bankClose : copy.support.bankOpen}</FadeLang>
            </span>
          </button>
          <AnimatePresence initial={false}>
            {bankOpen ? (
              <motion.div
                key="bank"
                initial={reduced ? { opacity: 0 } : { height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={reduced ? { opacity: 0 } : { height: 0, opacity: 0 }}
                transition={{ duration: 0.38, ease: easeOut }}
                className="overflow-hidden"
              >
                <div className="border-t border-white/10 px-5 py-4 sm:px-6">
                  <FadeLang>
                    <p className="text-sm text-mist/75">{copy.support.bankBody}</p>
                  </FadeLang>
                  <p className="mt-3 font-mono text-sm tracking-wide text-white sm:text-base">{SITE.iban}</p>
                  <CopyButton
                    value={SITE.iban.replaceAll(' ', '')}
                    className="btn btn-ghost mt-3 !py-2 text-xs"
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
