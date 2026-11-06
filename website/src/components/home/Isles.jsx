import { useEffect, useState } from 'react'
import { useLang } from '../../i18n.jsx'
import { ISLE_SPOTS } from '../../world/route.js'
import { stage } from '../../world/stage.js'
import Chapter, { Bank } from './Chapter.jsx'

/** Your own island: picking a start moves the real camera to the real island. */
export default function Isles() {
  const { copy } = useLang()
  const t = copy.home.isle
  const [pick, setPick] = useState(t.options[0].id)
  const current = t.options.find((o) => o.id === pick)

  useEffect(() => {
    stage.setFocus(ISLE_SPOTS[pick], pick)
  }, [pick])

  return (
    <Chapter id="isle" tone="day" className="ch-isle">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div className="isle-track">
        <div className="isle-panel">
          <div className="isle-tabs" role="tablist" aria-label={t.label}>
            {t.options.map((o) => (
              <button type="button" role="tab" key={o.id} aria-selected={pick === o.id} className={pick === o.id ? 'isle-tab isle-tab-on' : 'isle-tab'} onClick={() => setPick(o.id)}>
                {o.name}
              </button>
            ))}
          </div>
          <div className="isle-text" key={pick} role="tabpanel">
            <p className="isle-line">{current.line}</p>
            <p className="isle-best">
              <span>{t.best}</span> {current.best}
            </p>
          </div>
        </div>
      </div>
    </Chapter>
  )
}
