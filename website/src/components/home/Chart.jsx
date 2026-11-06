import { useMemo, useState } from 'react'
import { interpolate } from '../../copy.js'
import { DISTRICTS, LANDMARKS } from '../../data/island.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'

/* Block coordinates to a place on the chart: forge/chart/chart.py draws with the same numbers. */
const PAD = 0.09
const toX = (x) => (PAD + ((54 + (x + 500) * 1.0322) / 1060) * (1 - PAD * 2)) * 100
const toY = (z) => (PAD + ((42 + (z + 600) * 1.045) / 1060) * (1 - PAD * 2)) * 100

/** Where each name is lettered on the chart: placed by hand, as a chartmaker would, so nothing collides. */
const LETTERING = {
  skyreach: ['Mount Skyreach', -40, -305],
  ore_ridge: ['Ore Ridge', 158, -262],
  anker_harbour: ['Anker Harbour', 262, -486],
  seawatch: ['Seawatch', 438, -345],
  capital: ['The Capital', 0, -55],
  whisperwood: ['Whisperwood', -395, -440],
  bloomfield: ['Bloomfield', -70, -530],
  colosseum: ['Colosseum', -350, -92],
  glowcap_crags: ['Glowcap Crags', -412, 75],
  clucksworth: ['Clucksworth', -225, 175],
  southwood: ['Southwood', 24, 262],
  borderlands: ['Borderlands', 285, 222],
  eastwood: ['Eastwood', 405, -190],
  meres: ['The Meres', 205, -122],
}
const ZOOM = 2.5

function PostCards({ t }) {
  return (
    <figure className="cards">
      <figcaption className="cards-title">{t.postcards}</figcaption>
      <ul className="cards-row">
        {t.cards.map((card, i) => (
          <li key={card.src} className={`card card-${i % 4}`}>
            <span className="card-photo">
              <img src={asset(card.src)} alt="" loading="lazy" decoding="async" style={{ objectPosition: card.pos }} draggable="false" />
            </span>
            <span className="card-title">{card.title}</span>
            <span className="card-line">{card.line}</span>
          </li>
        ))}
      </ul>
    </figure>
  )
}

export default function Chart() {
  const { copy, lang } = useLang()
  const t = copy.home.chart
  const [selected, setSelected] = useState(null)
  const [mark, setMark] = useState(null)
  const district = DISTRICTS.find((d) => d.id === selected)
  const marks = useMemo(() => LANDMARKS.filter((m) => m.d === selected), [selected])
  const landmark = marks.find((m) => m.id === mark)

  let view = { z: 1, tx: 0, ty: 0 }
  if (district) {
    const [, lx, lz] = LETTERING[district.id]
    // aim between the lettering and its landmarks
    const px = marks.length ? marks.reduce((a, m) => a + toX(m.x), 0) / marks.length : toX(lx)
    const py = marks.length ? marks.reduce((a, m) => a + toY(m.z), 0) / marks.length : toY(lz)
    const clamp = (v) => Math.max(100 - 100 * ZOOM, Math.min(0, v))
    view = { z: ZOOM, tx: clamp(50 - px * ZOOM), ty: clamp(50 - py * ZOOM) }
  }

  return (
    <Chapter id="chart" tone="day" className="ch-chart">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div className="chart-module">
        <div className="chart-plate">
          <div className="chart-inner" style={{ '--z': view.z, transform: `translate(${view.tx}%, ${view.ty}%) scale(${view.z})` }}>
            <img
              className="chart-map"
              src={asset('world/chart-1000.webp')}
              srcSet={`${asset('world/chart-1000.webp')} 1000w, ${asset('world/chart.webp')} 1600w`}
              sizes="(min-width: 900px) 40rem, 100vw"
              alt={t.plate}
              width="1600"
              height="1600"
              decoding="async"
              draggable="false"
            />
            {DISTRICTS.map((d) => {
              const [label, lx, lz] = LETTERING[d.id]
              return (
                <button
                  type="button"
                  key={d.id}
                  className={`chart-label ${selected === d.id ? 'chart-label-on' : ''} ${selected && selected !== d.id ? 'chart-label-off' : ''}`}
                  style={{ left: `${toX(lx)}%`, top: `${toY(lz)}%` }}
                  aria-pressed={selected === d.id}
                  onClick={() => {
                    setSelected(selected === d.id ? null : d.id)
                    setMark(null)
                  }}
                >
                  {label}
                </button>
              )
            })}
            {marks.map((m) => (
              <button
                type="button"
                key={m.id}
                className={`chart-dot ${mark === m.id ? 'chart-dot-on' : ''}`}
                style={{ left: `${toX(m.x)}%`, top: `${toY(m.z)}%` }}
                aria-label={m.name}
                onClick={() => setMark(m.id)}
              />
            ))}
          </div>
          {landmark ? (
            <p className="chart-note">
              <strong>{landmark.name}</strong> {landmark.text[lang]}
            </p>
          ) : null}
          <span className="chart-scale" aria-hidden="true">
            <i />
            {t.scale}
          </span>
          {district ? (
            <button
              type="button"
              className="chart-back"
              onClick={() => {
                setSelected(null)
                setMark(null)
              }}
            >
              {t.back}
            </button>
          ) : null}
        </div>
        <div className="chart-side" aria-live="polite">
          {district ? (
            <>
              <h3 className="chart-name">{district.name}</h3>
              <p className="chart-tag">{district.tag[lang]}</p>
              <p className="chart-of">{interpolate(t.of, { n: marks.length })}</p>
              <ul className="chart-marks">
                {marks.map((m) => (
                  <li key={m.id}>
                    <button type="button" aria-pressed={mark === m.id} onClick={() => setMark(m.id)}>
                      {m.name}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <>
              <h3 className="chart-name">{t.plate}</h3>
              <p className="chart-tag">{t.hint}</p>
              <p className="chart-tag chart-moments">{t.moments}</p>
            </>
          )}
        </div>
      </div>
      <PostCards t={t} />
    </Chapter>
  )
}
