import { DISTRICTS, LANDMARKS } from '../../data/island.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'

/** Every district and every landmark, as written in the game's own files. */
export default function IslandData() {
  const { lang } = useLang()
  return (
    <>
      <img
        className="wiki-chart"
        src={asset('world/chart-1000.webp')}
        srcSet={`${asset('world/chart-1000.webp')} 1000w, ${asset('world/chart.webp')} 1600w`}
        sizes="(min-width: 760px) 38rem, 100vw"
        alt=""
        width="1600"
        height="1600"
        loading="lazy"
        decoding="async"
      />
      <div className="wiki-districts">
        {DISTRICTS.map((d) => (
          <section key={d.id} id={d.id} className="wiki-district">
            <h2>{d.name}</h2>
            <p className="wiki-district-tag">{d.tag[lang]}</p>
            <dl>
              {LANDMARKS.filter((m) => m.d === d.id).map((m) => (
                <div key={m.id}>
                  <dt>{m.name}</dt>
                  <dd>{m.text[lang]}</dd>
                </div>
              ))}
            </dl>
          </section>
        ))}
      </div>
    </>
  )
}
