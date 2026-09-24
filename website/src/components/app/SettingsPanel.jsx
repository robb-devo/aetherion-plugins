import { useId, useMemo, useState } from 'react'
import { useLang } from '../../i18n.jsx'
import { api, errorMessage } from '../../lib/api.js'
import { Notice, Spinner, useToast } from '../ui.jsx'
import { RamTierPicker } from './ServerBits.jsx'

const DIFFICULTIES = ['peaceful', 'easy', 'normal', 'hard']
const GAMEMODES = ['survival', 'creative', 'adventure', 'spectator']

function fromServer(server) {
  return {
    name: server.name,
    motd: server.settings.motd,
    difficulty: server.settings.difficulty,
    gamemode: server.settings.gamemode,
    maxPlayers: String(server.settings.maxPlayers),
    ramMb: server.ramMb,
  }
}

export default function SettingsPanel({ server, tiers, onSaved }) {
  const { copy } = useLang()
  const t = copy.app.dash.settings
  const showToast = useToast()
  const ids = { name: useId(), motd: useId(), difficulty: useId(), gamemode: useId(), max: useId() }
  const [form, setForm] = useState(() => fromServer(server))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const offline = server.status === 'offline'
  const baseline = useMemo(() => fromServer(server), [server])

  const changed = Object.keys(form).filter((key) => String(form[key]) !== String(baseline[key]))
  const maxPlayers = Number(form.maxPlayers)
  const valid = form.name.trim().length >= 2 && Number.isInteger(maxPlayers) && maxPlayers >= 1 && maxPlayers <= 40

  const set = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }))

  async function save(event) {
    event.preventDefault()
    if (!changed.length || !valid || saving) return
    setSaving(true)
    setError(null)
    const patch = {}
    for (const key of changed) patch[key] = key === 'maxPlayers' ? maxPlayers : form[key]
    try {
      const next = await api.updateServer(server.id, patch)
      onSaved(next)
      setForm(fromServer(next))
      showToast(t.saved, 'success')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="space-y-6" onSubmit={save} noValidate>
      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor={ids.name}>
            {t.name}
          </label>
          <input id={ids.name} className="field" maxLength={32} value={form.name} onChange={set('name')} />
        </div>
        <div>
          <label className="label" htmlFor={ids.motd}>
            {t.motd}
          </label>
          <input id={ids.motd} className="field" maxLength={60} value={form.motd} onChange={set('motd')} />
          <p className="mt-1.5 text-xs text-ash">{t.motdHint}</p>
        </div>
        <div>
          <label className="label" htmlFor={ids.difficulty}>
            {t.difficulty}
          </label>
          <select id={ids.difficulty} className="field" value={form.difficulty} onChange={set('difficulty')}>
            {DIFFICULTIES.map((value) => (
              <option key={value} value={value}>
                {t.difficulties[value]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label" htmlFor={ids.gamemode}>
            {t.gamemode}
          </label>
          <select id={ids.gamemode} className="field" value={form.gamemode} onChange={set('gamemode')}>
            {GAMEMODES.map((value) => (
              <option key={value} value={value}>
                {t.gamemodes[value]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label" htmlFor={ids.max}>
            {t.maxPlayers}
          </label>
          <input
            id={ids.max}
            className="field"
            type="number"
            min={1}
            max={40}
            value={form.maxPlayers}
            aria-invalid={!valid}
            onChange={set('maxPlayers')}
          />
        </div>
      </div>

      <fieldset>
        <legend className="label">{t.ram}</legend>
        {tiers ? (
          <RamTierPicker
            tiers={tiers}
            value={form.ramMb}
            disabled={!offline}
            name={`ram-${server.id}`}
            onChange={(ramMb) => setForm((current) => ({ ...current, ramMb }))}
          />
        ) : null}
        {!offline ? <p className="mt-2 text-xs text-gold">{t.ramLocked}</p> : null}
      </fieldset>

      {error ? <Notice tone="error">{errorMessage(error, copy)}</Notice> : null}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t hairline pt-5">
        <p className="text-xs text-ash">{offline ? '' : t.applyNote}</p>
        <button type="submit" className="btn btn-primary notch" disabled={!changed.length || !valid || saving}>
          {saving ? <Spinner /> : null}
          {saving ? t.saving : t.save}
        </button>
      </div>
    </form>
  )
}
