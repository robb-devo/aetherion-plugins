import { useMemo, useState } from 'react'
import {
  SITE,
  SHARD_PACKS,
  formatEuro,
  formatShards,
  paypalUrl,
} from '../content.js'
import { DiscordIcon } from './icons.jsx'
import { CopyButton, Kicker, Reveal, Section, SectionTitle } from './ui.jsx'

export default function Support() {
  const [custom, setCustom] = useState('5')
  const amount = Number(custom)
  const customValid = Number.isFinite(amount) && amount >= 1 && amount <= 999

  const customHref = useMemo(() => {
    if (!customValid) return undefined
    const rounded = Math.round(amount * 100) / 100
    return paypalUrl({
      amount: rounded,
      itemName: `Aetherion Support (custom ${rounded} EUR)`,
    })
  }, [amount, customValid])

  return (
    <Section id="support">
      <Reveal>
        <div className="mb-8 rounded-2xl border-2 border-gold/55 bg-gradient-to-r from-gold/15 to-amethyst/10 px-4 py-5 sm:px-6">
          <p className="text-sm font-extrabold tracking-[0.18em] text-gold uppercase">
            Optional · Kein Pay-to-Win
          </p>
          <p className="mt-2 text-base font-semibold text-white">
            Support ist ein Prototyp-Danke. Nicht nötig zum Spielen. Niemand muss zahlen, um stark zu sein.
          </p>
          <p className="mt-1 text-sm text-white/80">
            Shards kaufen keine Power, keine Dungeon-Wins, keine Skill-Level. Wer nicht spendet, spielt
            denselben Server.
          </p>
        </div>
        <Kicker>Support-Shop</Kicker>
        <SectionTitle en="PayPal first. Shards are granted by hand.">Shards für Freunde</SectionTitle>
      </Reveal>

      <div className="mt-8 grid gap-4 md:grid-cols-3">
        {SHARD_PACKS.map((pack, i) => {
          const href = paypalUrl({
            amount: pack.euros,
            itemName: `Aetherion Shards — ${pack.shards}`,
          })
          return (
            <Reveal key={pack.id} delay={i * 70}>
              <article
                className={`glass glass-hover relative flex h-full flex-col rounded-2xl p-5 sm:p-6 ${
                  pack.featured ? 'border-cyan/40 ring-1 ring-cyan/25' : ''
                }`}
              >
                {pack.featured ? (
                  <span className="absolute top-4 right-4 rounded-full bg-cyan/15 px-2 py-0.5 text-[0.65rem] font-bold text-cyan uppercase">
                    {pack.hint}
                  </span>
                ) : null}
                <p className="text-xs font-bold tracking-[0.18em] text-mist/70 uppercase">{pack.label}</p>
                <p className="mt-3 font-display text-4xl font-bold text-white">{formatEuro(pack.euros)}</p>
                <p className="mt-2 text-2xl font-extrabold text-amethyst">{formatShards(pack.shards)} Shards</p>
                <p className="mt-1 text-sm text-mist/65">{pack.hint}</p>
                <a href={href} className="btn btn-gold mt-6 w-full" target="_blank" rel="noreferrer">
                  PayPal
                </a>
              </article>
            </Reveal>
          )
        })}
      </div>

      <Reveal className="mt-4">
        <article className="glass rounded-2xl p-5 sm:p-6">
          <h3 className="text-lg font-bold text-white">Freier Betrag</h3>
          <p className="mt-1 text-sm text-mist/70">
            €1–999. Shards nach Absprache — kein Automat, kein Kurs-Versprechen außerhalb der Packs.
          </p>
          <form
            className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              if (customHref) window.open(customHref, '_blank', 'noopener,noreferrer')
            }}
          >
            <label className="block flex-1">
              <span className="mb-1.5 block text-xs font-bold tracking-wide text-mist/70 uppercase">
                Betrag in Euro
              </span>
              <input
                type="number"
                min="1"
                max="999"
                step="0.5"
                value={custom}
                onChange={(e) => setCustom(e.target.value)}
                className="w-full rounded-xl border border-white/12 bg-white/5 px-3 py-3 text-white outline-none ring-cyan/40 focus:ring-2"
              />
            </label>
            <button
              type="submit"
              className="btn btn-gold sm:mb-0.5 disabled:pointer-events-none disabled:opacity-40"
              disabled={!customValid}
            >
              PayPal öffnen
            </button>
          </form>
          {!customValid ? (
            <p className="mt-2 text-xs text-gold">Bitte einen Betrag zwischen 1 und 999 Euro wählen.</p>
          ) : null}
        </article>
      </Reveal>

      <Reveal className="mt-4">
          <article className="rounded-2xl border border-white/12 bg-white/5 p-5 sm:p-6">
          <h3 className="text-lg font-bold text-white">Gutschrift v1 — manuell nach PayPal</h3>
          <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-mist/80">
            <li>Pack oder Betrag per PayPal an {SITE.paypalEmail} senden.</li>
            <li>
              Discord öffnen und {SITE.owner} per DM schreiben: Minecraft-Name (IGN) + PayPal-Beleg.
            </li>
            <li>Peter bucht die Shards von Hand. Es gibt noch keinen Auto-Shop und keine Webhooks.</li>
          </ol>
          <a
            href={SITE.discord}
            className="btn btn-ghost mt-5"
            target="_blank"
            rel="noreferrer"
          >
            <DiscordIcon className="h-4 w-4" />
            Zum Discord
          </a>
        </article>
      </Reveal>

      <Reveal className="mt-4">
        <details className="bank-details glass rounded-2xl">
          <summary className="flex cursor-pointer items-center justify-between gap-3 px-5 py-4 text-sm font-semibold text-white sm:px-6">
            <span>Banküberweisung — nur für Freunde</span>
            <span className="text-xs font-bold tracking-wide text-mist/60 uppercase">
              <span className="bank-closed">Aufklappen</span>
              <span className="bank-open">Zuklappen</span>
            </span>
          </summary>
          <div className="border-t border-white/10 px-5 py-4 sm:px-6">
            <p className="text-sm text-mist/75">
              IBAN (SEPA). Verwendungszweck: dein IGN + „Shards“. Danach trotzdem Discord-DM mit Beleg.
            </p>
            <p className="mt-3 font-mono text-sm tracking-wide text-white sm:text-base">{SITE.iban}</p>
            <CopyButton
              value={SITE.iban.replaceAll(' ', '')}
              className="btn btn-ghost mt-3 !py-2 text-xs"
              copiedLabel="IBAN kopiert"
              toast="IBAN kopiert"
            >
              IBAN kopieren
            </CopyButton>
          </div>
        </details>
      </Reveal>
    </Section>
  )
}
