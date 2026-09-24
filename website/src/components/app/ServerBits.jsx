import { formatRam, SOFTWARE_LABEL } from '../../lib/format.js'
import { useLang } from '../../i18n.jsx'

export const RARITY_TEXT = {
  common: 'text-common',
  uncommon: 'text-uncommon',
  rare: 'text-rare',
  epic: 'text-epic',
  legendary: 'text-legendary',
  mythic: 'text-mythic',
}

export const RARITY_BORDER = {
  common: 'border-common/55',
  uncommon: 'border-uncommon/55',
  rare: 'border-rare/60',
  epic: 'border-epic/60',
  legendary: 'border-legendary/65',
  mythic: 'border-mythic/65',
}

/** A server's "icon": its initial in an inventory slot, tinted by the RAM tier's rarity. */
export function ServerGlyph({ name, tier, size = 'h-12 w-12 text-lg' }) {
  return (
    <span className={`slot shrink-0 font-display font-black ${size} ${RARITY_TEXT[tier] ?? 'text-mist'}`} aria-hidden="true">
      {String(name || '?').trim().slice(0, 1).toUpperCase()}
    </span>
  )
}

export function RamChip({ ramMb, tier }) {
  return (
    <span className={`notch inline-flex items-center bg-white/[0.06] px-2 py-0.5 text-xs font-extrabold ${RARITY_TEXT[tier] ?? 'text-mist'}`}>
      {formatRam(ramMb)}
    </span>
  )
}

export function SoftwareLabel({ software, version }) {
  return (
    <span className="text-xs font-semibold text-ash">
      {SOFTWARE_LABEL[software] ?? software} {version}
    </span>
  )
}

/** Memory tier cards, shared by the create form and server settings. */
export function RamTierPicker({ tiers, value, onChange, disabled = false, name = 'ram' }) {
  const { copy } = useLang()
  return (
    <div role="radiogroup" aria-label={copy.app.create.ram} className="grid grid-cols-2 gap-2 sm:grid-cols-5">
      {tiers.map((tier) => {
        const selected = tier.ramMb === value
        return (
          <label
            key={tier.ramMb}
            className={`relative flex cursor-pointer flex-col rounded-md border bg-black/25 p-3 transition-colors ${
              selected ? `${RARITY_BORDER[tier.rarity]} bg-white/[0.05]` : 'border-white/8 hover:border-white/16'
            } ${disabled ? 'pointer-events-none opacity-50' : ''}`}
          >
            <input
              type="radio"
              name={name}
              className="sr-only"
              checked={selected}
              disabled={disabled}
              onChange={() => onChange(tier.ramMb)}
            />
            <span className={`text-[0.62rem] font-extrabold tracking-[0.16em] uppercase ${RARITY_TEXT[tier.rarity]}`}>
              {copy.arsenal.rarity[tier.rarity]}
            </span>
            <span className="mt-1 text-xl font-extrabold text-white">{formatRam(tier.ramMb)}</span>
            <span className="mt-1 text-xs leading-snug text-mist/60">{copy.app.create.tiers[tier.ramMb]}</span>
            {tier.recommended ? (
              <span className="notch absolute -top-2 right-2 bg-amethyst px-1.5 py-0.5 text-[0.58rem] font-black tracking-wide text-black uppercase">
                {copy.app.create.recommended}
              </span>
            ) : null}
          </label>
        )
      })}
    </div>
  )
}
