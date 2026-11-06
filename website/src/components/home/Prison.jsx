import { useCallback, useState } from 'react'
import { useLang } from '../../i18n.jsx'
import Chapter, { Bank } from './Chapter.jsx'

const FIRST = 0 // The Intake Gate
const LAST = 22 // The Warden's Throne

/** Six rooms from the real pool. A run never lays them out the same way twice, and neither does this. */
function deal() {
  const pool = []
  for (let i = 1; i < LAST; i += 1) pool.push(i)
  for (let i = pool.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[pool[i], pool[j]] = [pool[j], pool[i]]
  }
  return [FIRST, ...pool.slice(0, 6), LAST]
}

export default function Prison() {
  const { copy } = useLang()
  const t = copy.home.prison
  const [hand, setHand] = useState(() => [FIRST, 12, 5, 16, 20, 1, 21, LAST])
  const [round, setRound] = useState(0)
  const again = useCallback(() => {
    setHand(deal())
    setRound((r) => r + 1)
  }, [])
  return (
    <Chapter id="prison" tone="dark" className="ch-prison">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div className="deck">
        <ol className="deck-hand" key={round}>
          {hand.map((room, i) => (
            <li key={room} className={`deck-card ${i === 0 ? 'deck-first' : ''} ${i === hand.length - 1 ? 'deck-last' : ''}`} style={{ '--i': i }}>
              <span className="deck-kind">{i === 0 ? t.entrance : i === hand.length - 1 ? t.boss : i}</span>
              <span className="deck-name">{t.rooms[room]}</span>
            </li>
          ))}
        </ol>
        <div className="deck-foot">
          <button type="button" className="btn btn-primary notch" onClick={again}>
            {t.deal}
          </button>
          <ul className="deck-floors">
            {t.floorNames.map((f) => (
              <li key={f}>{f}</li>
            ))}
          </ul>
        </div>
      </div>
    </Chapter>
  )
}
